package rv32.core

import chisel3._
import chisel3.util._
import rv32.configs._
import rv32.core.util._
import rv32.core.stage._

// ============================================================
// NeoRV32 Core - Parameterizable RISC-V RV32 CPU
// Supports: 1, 3, 5 stage pipeline configurations
// ============================================================

class NeoRV32CoreIO(implicit config: CoreConfig) extends Bundle {
  // Instruction memory interface
  val imem = new Bundle {
    val req = Output(new SimpleMemReq)
    val resp = Input(new SimpleMemResp)
  }

  // Data memory interface
  val dmem = new Bundle {
    val req = Output(new SimpleMemReq)
    val resp = Input(new SimpleMemResp)
  }

  // Optional: debug output
  val debug = new Bundle {
    val pc = Output(UInt(32.W))
    val reg_write = Output(Bool())
    val reg_addr = Output(UInt(5.W))
    val reg_data = Output(UInt(config.xlen.W))
  }
}

class NeoRV32Core(implicit config: CoreConfig) extends Module {
  import Constants._
  val io = IO(new NeoRV32CoreIO)
  dontTouch(io.dmem.req.mask)

  // Pipeline stage instances
  val fetch = Module(new FetchStage)
  val decode = Module(new DecodeStage)
  val execute = Module(new ExecuteStage)
  val memory = Module(new MemoryStage)
  val writeback = Module(new WritebackStage)

  // PC control signals
  val pc_next = Wire(UInt(32.W))
  val pc_take = Wire(Bool())
  val pc_stall = Wire(Bool())

  // Connect memory interfaces
  io.imem.req <> fetch.io.inst_req
  fetch.io.inst_resp <> io.imem.resp
  io.dmem.req <> memory.io.data_req
  memory.io.data_resp <> io.dmem.resp

  // Connect pipeline stages
  decode.io.ifid <> fetch.io.ifid
  execute.io.idex <> decode.io.idex
  memory.io.exmem <> execute.io.exmem
  writeback.io.memwb <> memory.io.memwb

  // Writeback to Decode (register file writeback)
  decode.io.wb_rd_addr := writeback.io.wb_rd_addr
  decode.io.wb_rd_data := writeback.io.wb_rd_data
  decode.io.wb_reg_write := writeback.io.wb_reg_write

  // PC control - branch/jump from execute stage
  pc_next := Mux(execute.io.pc_take, execute.io.pc_target, fetch.io.pc + 4.U)
  pc_take := execute.io.pc_take

  // Simple stall logic (for single-cycle, mostly disabled)
  // In multi-stage, this would come from hazard unit
  pc_stall := false.B

  // Connect PC control to fetch stage
  fetch.io.pc_next := pc_next
  fetch.io.pc_we := pc_take
  fetch.io.stall := pc_stall
  fetch.io.flush := false.B

  // Default forwarding signals (no forwarding for 1-stage and 3-stage)
  execute.io.forward_a := 0.U
  execute.io.forward_b := 0.U
  execute.io.ex_fwd_data := 0.U
  execute.io.mem_fwd_data := 0.U

  // Pipeline stage configuration based on pipelineStages parameter
  if (config.pipelineStages == 1) {
    // Single-cycle: combinational through all stages
    // Registers already in stages for clean timing
    // No additional stalls/forwarding needed
    decode.io.stall := false.B
    decode.io.flush := false.B
    execute.io.stall := false.B
    execute.io.flush := false.B
    memory.io.stall := false.B
    memory.io.flush := false.B
    writeback.io.stall := false.B
  } else if (config.pipelineStages == 3) {
    // 3-stage: IF | ID/EX | MEM/WB
    // Stages are combined: Decode+Execute in one cycle, Memory+Writeback in one cycle
    // Simple load-use hazard detection

    // Detect load-use hazard: if ID/EX stage has load and next instruction needs that register
    val load_in_idex = execute.io.idex.mem_en && !execute.io.idex.mem_rw
    // Extract rs1/rs2 from current instruction being decoded (not from previous instruction)
    val rs1_addr = decode.io.ifid.inst(19, 15)
    val rs2_addr = decode.io.ifid.inst(24, 20)

    val load_use_hazard_3stage = load_in_idex &&
                                  execute.io.idex.reg_write &&
                                  (execute.io.idex.rd_addr =/= 0.U) &&
                                  decode.io.ifid.valid &&
                                  ((rs1_addr === execute.io.idex.rd_addr) ||
                                   (rs2_addr === execute.io.idex.rd_addr))

    // Detect MulDiv stall: division in progress in ID/EX stage
    val muldiv_stall = if (config.useM) {
      !execute.io.muldiv_ready &&
      execute.io.idex.valid &&
      (execute.io.idex.fu_sel === FU_MULDIV)
    } else {
      false.B
    }

    // Combine stalls
    val combined_stall_3stage = load_use_hazard_3stage || muldiv_stall

    // Stall IF and ID/EX on load-use hazard or MulDiv stall
    fetch.io.stall := combined_stall_3stage
    decode.io.stall := combined_stall_3stage
    execute.io.stall := combined_stall_3stage
    memory.io.stall := false.B
    writeback.io.stall := false.B

    // Flush on branch taken
    decode.io.flush := pc_take
    execute.io.flush := pc_take || load_use_hazard_3stage  // Don't flush on muldiv_stall
    memory.io.flush := false.B

  } else if (config.pipelineStages == 5) {
    // 5-stage: IF | ID | EX | MEM | WB
    // Full hazard unit with forwarding

    val hazard = Module(new HazardUnit)

    // Connect hazard unit inputs
    hazard.io.id_rs1_addr := decode.io.idex.rs1_addr
    hazard.io.id_rs2_addr := decode.io.idex.rs2_addr
    hazard.io.ex_rd_addr := execute.io.exmem.rd_addr
    hazard.io.ex_reg_write := execute.io.exmem.reg_write
    hazard.io.ex_mem_read := execute.io.exmem.mem_en && !execute.io.exmem.mem_rw
    hazard.io.mem_rd_addr := memory.io.memwb.rd_addr
    hazard.io.mem_reg_write := memory.io.memwb.reg_write
    hazard.io.wb_rd_addr := writeback.io.wb_rd_addr
    hazard.io.wb_reg_write := writeback.io.wb_reg_write

    // Detect MulDiv stall: division in progress in EX stage
    val muldiv_stall = if (config.useM) {
      !execute.io.muldiv_ready &&
      execute.io.exmem.valid &&
      (execute.io.exmem.fu_sel === FU_MULDIV)
    } else {
      false.B
    }

    // Apply stall signals
    fetch.io.stall := hazard.io.stall_if || muldiv_stall
    decode.io.stall := hazard.io.stall_id || muldiv_stall
    execute.io.stall := muldiv_stall
    memory.io.stall := muldiv_stall  // Prevent MEM stage from overwriting result
    writeback.io.stall := false.B

    // Apply flush signals
    decode.io.flush := pc_take
    execute.io.flush := pc_take || hazard.io.flush_ex
    memory.io.flush := false.B

    // Connect forwarding signals
    execute.io.forward_a := hazard.io.forward_a
    execute.io.forward_b := hazard.io.forward_b

    // Forwarding data sources
    // EX stage: ALU result from EX/MEM register
    execute.io.ex_fwd_data := execute.io.exmem.alu_result

    // MEM stage: Select between memory data and ALU result
    execute.io.mem_fwd_data := Mux(
      memory.io.memwb.wb_sel === WB_MEM,
      memory.io.memwb.mem_rdata,
      memory.io.memwb.alu_result
    )
  }

  // Debug outputs
  io.debug.pc := fetch.io.pc
  io.debug.reg_write := writeback.io.wb_reg_write
  io.debug.reg_addr := writeback.io.wb_rd_addr
  io.debug.reg_data := writeback.io.wb_rd_data
}
