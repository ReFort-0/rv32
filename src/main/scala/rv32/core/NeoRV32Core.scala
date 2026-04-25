package rv32.core

import chisel3._
import chisel3.util._
import rv32.configs.{CoreConfig, NeoRV32Config}
import rv32.core.util._
import rv32.core.stage._
import rv32.core.functionalunit._

// ============================================================
// NeoRV32CoreIO - IO Bundle for the core
// ============================================================

class NeoRV32CoreIO(implicit config: NeoRV32Config) extends Bundle {
  val conf = config.core
  // Core is requester:
  // - req: Output (core drives request to memory)
  // - resp: Input (core receives response from memory)
  val imem = new Bundle {
    val req = Output(new SimpleMemReq())
    val resp = Input(new SimpleMemResp())
  }
  val dmem = new Bundle {
    val req = Output(new SimpleMemReq())
    val resp = Input(new SimpleMemResp())
  }
  val dbg = new Bundle {
    val pc = Output(UInt(conf.xlen.W))
  }
  // Reset PC input for ACT4 testing (default 0x00000000)
  val resetPc = Input(UInt(conf.xlen.W))
}

// ============================================================
// NeoRV32Core - Parameterized RISC-V Core
// Supports 1, 3, or 5 stage pipelines
// ============================================================

class NeoRV32Core(config: NeoRV32Config) extends Module {
  implicit val conf: CoreConfig = config.core
  implicit val neoConf: NeoRV32Config = config

  val io = IO(new NeoRV32CoreIO)

  io := DontCare

  // ============================================================
  // Pipeline Stage Instances
  // ============================================================

  val fetch    = Module(new FetchStage())
  val decode   = Module(new DecodeStage())
  val execute   = Module(new ExecuteStage())
  val memory   = Module(new MemoryStage())
  val writeback = Module(new WritebackStage())
  val regfile  = Module(new RegisterFile())

  // Hazard detection and forwarding unit
  val hazardUnit = Module(new HazardUnit())

  // ============================================================
  // PC Select Constants
  // ============================================================

  val PC_PLUS4 = 0.U(2.W)
  val PC_BRJMP = 1.U(2.W)
  val PC_JALR  = 2.U(2.W)

  // ============================================================
  // Pipeline Registers and Connections based on stage count
  // ============================================================

  if (conf.pipelineStages == 1) {
    connect1Stage()
  } else if (conf.pipelineStages == 3) {
    connect3Stage()
  } else {
    connect5Stage()
  }

  // ============================================================
  // Memory Interface Connections
  // ============================================================

  io.imem.req.addr  := fetch.io.imem.req.addr
  io.imem.req.wdata := fetch.io.imem.req.wdata
  io.imem.req.wen   := fetch.io.imem.req.wen
  io.imem.req.mask  := fetch.io.imem.req.mask
  io.imem.req.valid := fetch.io.imem.req.valid
  fetch.io.imem.resp <> io.imem.resp

  io.dmem.req.addr  := memory.io.dmem.req.addr
  io.dmem.req.wdata := memory.io.dmem.req.wdata
  io.dmem.req.wen   := memory.io.dmem.req.wen
  io.dmem.req.mask  := memory.io.dmem.req.mask
  io.dmem.req.valid := memory.io.dmem.req.valid
  memory.io.dmem.resp <> io.dmem.resp

  // ============================================================
  // Debug Interface
  // ============================================================

  io.dbg.pc := fetch.io.pc_out

  // ============================================================
  // 1-Stage Pipeline: All stages combined (combinatorial)
  // ============================================================

  def connect1Stage(): Unit = {
    val pc = RegInit(0x00000000L.U(conf.xlen.W))

    // --- Fetch stage ---
    val jalr_target = (decode.io.rf.rs1_data + decode.io.imm) & ~1.U(conf.xlen.W)
    val br_target   = decode.io.out.bits.pc + decode.io.imm

    fetch.io.pc_in   := Mux(execute.io.pc_sel === PC_JALR, jalr_target, br_target)
    fetch.io.pc_sel  := Mux(execute.io.br_taken, execute.io.pc_sel, PC_PLUS4)
    fetch.io.br_taken := execute.io.br_taken
    fetch.io.stall   := false.B
    fetch.io.flush   := false.B
    fetch.io.reset_pc := io.resetPc

    // Build uop from fetch output
    val uop = MicroOp(fetch.io.inst_out, fetch.io.pc_out, fetch.io.valid_out)

    // --- Decode stage ---
    decode.io.in.valid := fetch.io.valid_out
    decode.io.in.bits  := uop
    decode.io.stall    := false.B
    decode.io.flush    := false.B

    // Register file read
    regfile.io.rs1_addr := decode.io.rf.rs1_addr
    regfile.io.rs2_addr := decode.io.rf.rs2_addr
    val rf_rs1_data = Wire(UInt(conf.xlen.W))
    val rf_rs2_data = Wire(UInt(conf.xlen.W))
    rf_rs1_data := regfile.io.rs1_data
    rf_rs2_data := regfile.io.rs2_data

    // Forward from writeback (1-cycle, no stall needed)
    val fwd_rs1 = !execute.io.out.bits.ctrl.rf_wen &&
                  writeback.io.rf.wen &&
                  (writeback.io.rf.addr =/= 0.U) &&
                  (writeback.io.rf.addr === decode.io.rf.rs1_addr)
    val fwd_rs2 = !execute.io.out.bits.ctrl.rf_wen &&
                  writeback.io.rf.wen &&
                  (writeback.io.rf.addr =/= 0.U) &&
                  (writeback.io.rf.addr === decode.io.rf.rs2_addr)

    decode.io.rf.rs1_data := Mux(fwd_rs1, writeback.io.rf.data, rf_rs1_data)
    decode.io.rf.rs2_data := Mux(fwd_rs2, writeback.io.rf.data, rf_rs2_data)

    // --- Execute stage ---
    execute.io.in.valid := decode.io.out.valid
    execute.io.in.bits  := decode.io.out.bits
    execute.io.rs1_data := decode.io.rf.rs1_data
    execute.io.rs2_data := decode.io.rf.rs2_data
    execute.io.imm      := decode.io.imm
    execute.io.stall    := false.B
    execute.io.flush    := false.B

    // --- Memory stage ---
    memory.io.in.valid := execute.io.out.valid
    memory.io.in.bits  := execute.io.out.bits
    memory.io.stall    := false.B
    memory.io.flush    := false.B

    // --- Writeback stage ---
    writeback.io.in.valid := memory.io.out.valid
    writeback.io.in.bits  := memory.io.out.bits
    writeback.io.stall    := false.B
    writeback.io.flush    := false.B

    // Writeback to regfile
    regfile.io.rd_addr := writeback.io.rf.addr
    regfile.io.rd_data := writeback.io.rf.data
    regfile.io.wen     := writeback.io.rf.wen

    // Update PC
    pc := Mux(execute.io.br_taken, fetch.io.pc_in, pc + 4.U)
  }

  // ============================================================
  // 3-Stage Pipeline: IF | ID+EX | MEM+WB
  // ============================================================

  def connect3Stage(): Unit = {
    // IF/ID pipeline register
    val if_id_uop = RegInit(MicroOp.bubble)

    // EX/MEM pipeline register
    val ex_mem_uop = RegInit(MicroOp.bubble)

    // Flush on branch taken
    val flush_if = execute.io.br_taken

    // --- IF/ID register update ---
    when(!hazardUnit.io.stall) {
      when(flush_if) {
        if_id_uop := MicroOp.bubble
      }.elsewhen(fetch.io.valid_out) {
        if_id_uop := MicroOp(fetch.io.inst_out, fetch.io.pc_out, true.B)
      }.otherwise {
        if_id_uop := MicroOp.bubble
      }
    }

    // --- Fetch stage ---
    val jalr_target = (if_id_uop.rs1_data.asUInt + if_id_uop.imm.asUInt) & ~1.U(conf.xlen.W)
    val next_pc = Mux(execute.io.br_taken,
      Mux(execute.io.pc_sel === PC_JALR, jalr_target, execute.io.br_target),
      fetch.io.pc_out + 4.U)

    fetch.io.pc_in    := next_pc
    fetch.io.pc_sel   := Mux(execute.io.br_taken, execute.io.pc_sel, PC_PLUS4)
    fetch.io.br_taken := execute.io.br_taken
    fetch.io.stall    := hazardUnit.io.stall
    fetch.io.flush    := flush_if
    fetch.io.reset_pc := io.resetPc

    // --- Decode stage ---
    decode.io.in.valid := if_id_uop.valid
    decode.io.in.bits  := if_id_uop
    decode.io.stall    := hazardUnit.io.stall
    decode.io.flush    := false.B

    // Register file read
    regfile.io.rs1_addr := decode.io.rf.rs1_addr
    regfile.io.rs2_addr := decode.io.rf.rs2_addr

    // ---- Hazard unit inputs from ID stage ----
    val id_use_rs1 = decode.io.rf.rs1_addr =/= 0.U
    val id_use_rs2 = decode.io.rf.rs2_addr =/= 0.U
    hazardUnit.io.id_rs1      := decode.io.rf.rs1_addr
    hazardUnit.io.id_rs2      := decode.io.rf.rs2_addr
    hazardUnit.io.id_valid     := if_id_uop.valid
    hazardUnit.io.id_use_rs1  := id_use_rs1
    hazardUnit.io.id_use_rs2  := id_use_rs2

    // Forwarding: override regfile data with hazard unit output
    val fwd_rs1_data = MuxCase(regfile.io.rs1_data, Array(
      (hazardUnit.io.fwd_rs1_sel === 1.U) -> hazardUnit.io.fwd_rs1_data_ex,
      (hazardUnit.io.fwd_rs1_sel === 2.U) -> hazardUnit.io.fwd_rs1_data_mem,
      (hazardUnit.io.fwd_rs1_sel === 3.U) -> hazardUnit.io.fwd_rs1_data_wb
    ))
    val fwd_rs2_data = MuxCase(regfile.io.rs2_data, Array(
      (hazardUnit.io.fwd_rs2_sel === 1.U) -> hazardUnit.io.fwd_rs2_data_ex,
      (hazardUnit.io.fwd_rs2_sel === 2.U) -> hazardUnit.io.fwd_rs2_data_mem,
      (hazardUnit.io.fwd_rs2_sel === 3.U) -> hazardUnit.io.fwd_rs2_data_wb
    ))
    decode.io.rf.rs1_data := fwd_rs1_data
    decode.io.rf.rs2_data := fwd_rs2_data

    // --- ID/EX register ---
    val id_ex_uop = RegInit(MicroOp.bubble)
    when(!hazardUnit.io.stall) {
      val uop = Wire(new MicroOp())
      uop := decode.io.out.bits
      uop.rs1_data := decode.io.rf.rs1_data
      uop.rs2_data := decode.io.rf.rs2_data
      uop.imm      := decode.io.imm
      id_ex_uop := uop
    }

    // Flush ID/EX on branch
    when(flush_if && !hazardUnit.io.stall) {
      id_ex_uop := MicroOp.bubble
    }

    // ---- Hazard unit inputs from EX stage ----
    hazardUnit.io.ex_rd        := id_ex_uop.rd
    hazardUnit.io.ex_rf_wen    := id_ex_uop.ctrl.rf_wen
    hazardUnit.io.ex_valid     := id_ex_uop.valid
    hazardUnit.io.ex_is_load   := (id_ex_uop.ctrl.mem_fcn === Constants.M_XRD)
    hazardUnit.io.ex_alu_result := execute.io.out.bits.alu_result
    hazardUnit.io.ex_fu_sel    := id_ex_uop.ctrl.fu_sel

    // ---- Hazard unit inputs from MEM stage (ex_mem) ----
    hazardUnit.io.mem_rd       := ex_mem_uop.rd
    hazardUnit.io.mem_rf_wen   := ex_mem_uop.ctrl.rf_wen
    hazardUnit.io.mem_valid    := ex_mem_uop.valid
    hazardUnit.io.mem_result   := ex_mem_uop.alu_result

    // ---- Hazard unit inputs from WB stage ----
    hazardUnit.io.wb_rd        := writeback.io.rf.addr
    hazardUnit.io.wb_rf_wen    := writeback.io.rf.wen
    hazardUnit.io.wb_valid     := writeback.io.in.valid
    hazardUnit.io.wb_result    := writeback.io.rf.data

    // ---- Hazard unit MulDiv busy ----
    hazardUnit.io.muldiv_busy  := execute.io.muldiv_busy.getOrElse(false.B)
    hazardUnit.io.br_taken     := execute.io.br_taken

    // --- Execute stage ---
    execute.io.in.valid  := id_ex_uop.valid
    execute.io.in.bits   := id_ex_uop
    execute.io.rs1_data  := id_ex_uop.rs1_data
    execute.io.rs2_data  := id_ex_uop.rs2_data
    execute.io.imm       := id_ex_uop.imm
    execute.io.stall     := hazardUnit.io.stall
    execute.io.flush     := false.B

    // --- EX/MEM register ---
    when(!hazardUnit.io.stall) {
      ex_mem_uop := execute.io.out.bits
    }

    // --- Memory stage ---
    memory.io.in.valid := ex_mem_uop.valid
    memory.io.in.bits  := ex_mem_uop
    memory.io.stall    := hazardUnit.io.stall
    memory.io.flush    := false.B

    // --- Writeback stage ---
    writeback.io.in.valid := memory.io.out.valid
    writeback.io.in.bits  := memory.io.out.bits
    writeback.io.stall    := hazardUnit.io.stall
    writeback.io.flush    := false.B

    // Writeback to regfile
    regfile.io.rd_addr := writeback.io.rf.addr
    regfile.io.rd_data := writeback.io.rf.data
    regfile.io.wen     := writeback.io.rf.wen && !hazardUnit.io.stall
  }

  // ============================================================
  // 5-Stage Pipeline: IF | ID | EX | MEM | WB
  // ============================================================

  def connect5Stage(): Unit = {
    // Pipeline registers
    val if_id_uop  = RegInit(MicroOp.bubble)
    val id_ex_uop  = RegInit(MicroOp.bubble)
    val ex_mem_uop = RegInit(MicroOp.bubble)
    val mem_wb_uop = RegInit(MicroOp.bubble)

    // Flush on branch taken
    val flush_if = execute.io.br_taken

    // --- IF/ID register update ---
    when(!hazardUnit.io.stall) {
      when(flush_if) {
        if_id_uop := MicroOp.bubble
      }.elsewhen(fetch.io.valid_out) {
        if_id_uop := MicroOp(fetch.io.inst_out, fetch.io.pc_out, true.B)
      }.otherwise {
        if_id_uop := MicroOp.bubble
      }
    }

    // --- Fetch stage ---
    val jalr_target = Wire(UInt(conf.xlen.W))
    jalr_target := (if_id_uop.rs1_data.asUInt + if_id_uop.imm.asUInt) & ~1.U(conf.xlen.W)

    val next_pc = Mux(execute.io.br_taken,
      Mux(execute.io.pc_sel === PC_JALR, jalr_target, execute.io.br_target),
      fetch.io.pc_out + 4.U)

    fetch.io.pc_in    := next_pc
    fetch.io.pc_sel   := Mux(execute.io.br_taken, execute.io.pc_sel, PC_PLUS4)
    fetch.io.br_taken := execute.io.br_taken
    fetch.io.stall    := hazardUnit.io.stall
    fetch.io.flush    := flush_if
    fetch.io.reset_pc := io.resetPc

    // --- Decode stage ---
    decode.io.in.valid := if_id_uop.valid
    decode.io.in.bits  := if_id_uop
    decode.io.stall    := hazardUnit.io.stall
    decode.io.flush    := false.B

    // Register file read
    regfile.io.rs1_addr := decode.io.rf.rs1_addr
    regfile.io.rs2_addr := decode.io.rf.rs2_addr

    // ---- Hazard unit inputs from ID stage ----
    hazardUnit.io.id_rs1     := decode.io.rf.rs1_addr
    hazardUnit.io.id_rs2     := decode.io.rf.rs2_addr
    hazardUnit.io.id_valid   := if_id_uop.valid
    hazardUnit.io.id_use_rs1 := decode.io.rf.rs1_addr =/= 0.U
    hazardUnit.io.id_use_rs2 := decode.io.rf.rs2_addr =/= 0.U

    // Forwarding
    val fwd_rs1_data = MuxCase(regfile.io.rs1_data, Array(
      (hazardUnit.io.fwd_rs1_sel === 1.U) -> hazardUnit.io.fwd_rs1_data_ex,
      (hazardUnit.io.fwd_rs1_sel === 2.U) -> hazardUnit.io.fwd_rs1_data_mem,
      (hazardUnit.io.fwd_rs1_sel === 3.U) -> hazardUnit.io.fwd_rs1_data_wb
    ))
    val fwd_rs2_data = MuxCase(regfile.io.rs2_data, Array(
      (hazardUnit.io.fwd_rs2_sel === 1.U) -> hazardUnit.io.fwd_rs2_data_ex,
      (hazardUnit.io.fwd_rs2_sel === 2.U) -> hazardUnit.io.fwd_rs2_data_mem,
      (hazardUnit.io.fwd_rs2_sel === 3.U) -> hazardUnit.io.fwd_rs2_data_wb
    ))
    decode.io.rf.rs1_data := fwd_rs1_data
    decode.io.rf.rs2_data := fwd_rs2_data

    // --- ID/EX register ---
    when(!hazardUnit.io.stall) {
      val uop = Wire(new MicroOp())
      uop := decode.io.out.bits
      uop.rs1_data := decode.io.rf.rs1_data
      uop.rs2_data := decode.io.rf.rs2_data
      uop.imm      := decode.io.imm
      id_ex_uop := uop
    }

    when(flush_if && !hazardUnit.io.stall) {
      id_ex_uop := MicroOp.bubble
    }

    // ---- Hazard unit inputs from EX stage ----
    hazardUnit.io.ex_rd         := id_ex_uop.rd
    hazardUnit.io.ex_rf_wen     := id_ex_uop.ctrl.rf_wen
    hazardUnit.io.ex_valid      := id_ex_uop.valid
    hazardUnit.io.ex_is_load    := (id_ex_uop.ctrl.mem_fcn === Constants.M_XRD)
    hazardUnit.io.ex_alu_result := execute.io.out.bits.alu_result
    hazardUnit.io.ex_fu_sel     := id_ex_uop.ctrl.fu_sel

    // ---- Hazard unit inputs from MEM stage ----
    hazardUnit.io.mem_rd     := ex_mem_uop.rd
    hazardUnit.io.mem_rf_wen := ex_mem_uop.ctrl.rf_wen
    hazardUnit.io.mem_valid  := ex_mem_uop.valid
    hazardUnit.io.mem_result := ex_mem_uop.alu_result

    // ---- Hazard unit inputs from WB stage ----
    hazardUnit.io.wb_rd     := writeback.io.rf.addr
    hazardUnit.io.wb_rf_wen := writeback.io.rf.wen
    hazardUnit.io.wb_valid  := mem_wb_uop.valid
    hazardUnit.io.wb_result := writeback.io.rf.data

    // ---- Hazard unit MulDiv busy and branch ----
    hazardUnit.io.muldiv_busy := execute.io.muldiv_busy.getOrElse(false.B)
    hazardUnit.io.br_taken    := execute.io.br_taken

    // --- Execute stage ---
    execute.io.in.valid  := id_ex_uop.valid
    execute.io.in.bits   := id_ex_uop
    execute.io.rs1_data := id_ex_uop.rs1_data
    execute.io.rs2_data := id_ex_uop.rs2_data
    execute.io.imm      := id_ex_uop.imm
    execute.io.stall    := hazardUnit.io.stall
    execute.io.flush    := false.B

    // --- EX/MEM register ---
    when(!hazardUnit.io.stall) {
      ex_mem_uop := execute.io.out.bits
    }

    // --- Memory stage ---
    memory.io.in.valid := ex_mem_uop.valid
    memory.io.in.bits  := ex_mem_uop
    memory.io.stall    := hazardUnit.io.stall
    memory.io.flush    := false.B

    // --- MEM/WB register ---
    when(!hazardUnit.io.stall) {
      mem_wb_uop := memory.io.out.bits
    }

    // --- Writeback stage ---
    writeback.io.in.valid := mem_wb_uop.valid
    writeback.io.in.bits  := mem_wb_uop
    writeback.io.stall    := hazardUnit.io.stall
    writeback.io.flush    := false.B

    // Writeback to regfile
    regfile.io.rd_addr := writeback.io.rf.addr
    regfile.io.rd_data := writeback.io.rf.data
    regfile.io.wen     := writeback.io.rf.wen && !hazardUnit.io.stall
  }
}
