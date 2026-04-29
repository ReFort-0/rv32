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

  // Connect stall/flush to other stages (single-cycle: disabled)
  decode.io.stall := false.B
  decode.io.flush := false.B
  execute.io.stall := false.B
  execute.io.flush := false.B
  memory.io.stall := false.B
  memory.io.flush := false.B
  writeback.io.stall := false.B

  // Pipeline stage configuration based on pipelineStages parameter
  if (config.pipelineStages == 1) {
    // Single-cycle: combinational through all stages
    // Registers already in stages for clean timing
    // No additional stalls/forwarding needed
  } else if (config.pipelineStages == 3) {
    // 3-stage: IF | ID/EX | MEM/WB
    // TODO: Implement 3-stage configuration
  } else if (config.pipelineStages == 5) {
    // 5-stage: IF | ID | EX | MEM | WB
    // TODO: Implement 5-stage configuration with hazard unit
  }

  // Debug outputs
  io.debug.pc := fetch.io.pc
  io.debug.reg_write := writeback.io.wb_reg_write
  io.debug.reg_addr := writeback.io.wb_rd_addr
  io.debug.reg_data := writeback.io.wb_rd_data
}
