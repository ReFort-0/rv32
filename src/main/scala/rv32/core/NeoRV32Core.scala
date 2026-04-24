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
  // Reset PC input for ACT4 testing (default 0x80000000)
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

  val fetch = Module(new FetchStage())
  val decode = Module(new DecodeStage())
  val execute = Module(new ExecuteStage())
  val memory = Module(new MemoryStage())
  val writeback = Module(new WritebackStage())
  val regfile = Module(new RegisterFile())

  // ============================================================
  // Pipeline Registers and Connections based on stage count
  // ============================================================

  val PC_PLUS4 = 0.U(2.W)
  val PC_BRJMP = 1.U(2.W)
  val PC_JALR = 2.U(2.W)

  val stall = Wire(Bool())
  stall := false.B

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

  // CoreIO: Flipped(SimpleMemIO) - req=Input, resp=Output (from SoC to core)
  // Stages: SimpleMemIO - req=Output, resp=Input (from core to SoC)

  // imem connections (SoC provides data to core)
  io.imem.req.addr := fetch.io.imem.req.addr
  io.imem.req.wdata := fetch.io.imem.req.wdata
  io.imem.req.wen := fetch.io.imem.req.wen
  io.imem.req.mask := fetch.io.imem.req.mask
  io.imem.req.valid := fetch.io.imem.req.valid
  fetch.io.imem.resp <> io.imem.resp

  // dmem connections
  io.dmem.req.addr := memory.io.dmem.req.addr
  io.dmem.req.wdata := memory.io.dmem.req.wdata
  io.dmem.req.wen := memory.io.dmem.req.wen
  io.dmem.req.mask := memory.io.dmem.req.mask
  io.dmem.req.valid := memory.io.dmem.req.valid
  memory.io.dmem.resp <> io.dmem.resp

  // ============================================================
  // Debug Interface
  // ============================================================

  io.dbg.pc := fetch.io.pc_out

  // ============================================================
  // Pipeline Connection Functions
  // ============================================================

  def connect1Stage(): Unit = {
    // Single cycle - all stages operate combinatorially

    val pc = RegInit(0x80000000L.U(conf.xlen.W))

    // Fetch stage
    val jalr_target = (decode.io.rf.rs1_data + decode.io.imm) & ~1.U(conf.xlen.W)
    val br_target = decode.io.out.bits.pc + decode.io.imm

    fetch.io.pc_in := Mux(execute.io.pc_sel === PC_JALR, jalr_target, br_target)
    fetch.io.pc_sel := Mux(execute.io.br_taken, execute.io.pc_sel, PC_PLUS4)
    fetch.io.br_taken := execute.io.br_taken
    fetch.io.stall := false.B
    fetch.io.flush := false.B
    fetch.io.reset_pc := io.resetPc

    // Fetch produces uop
    val uop = MicroOp(fetch.io.inst_out, fetch.io.pc_out, fetch.io.valid_out)

    // Decode
    decode.io.in.valid := fetch.io.valid_out
    decode.io.in.bits := uop
    decode.io.stall := false.B
    decode.io.flush := false.B

    // Regfile connections
    regfile.io.rs1_addr := decode.io.rf.rs1_addr
    regfile.io.rs2_addr := decode.io.rf.rs2_addr
    decode.io.rf.rs1_data := regfile.io.rs1_data
    decode.io.rf.rs2_data := regfile.io.rs2_data

    // Execute
    execute.io.in.valid := decode.io.out.valid
    execute.io.in.bits := decode.io.out.bits
    execute.io.rs1_data := decode.io.rf.rs1_data
    execute.io.rs2_data := decode.io.rf.rs2_data
    execute.io.imm := decode.io.imm
    execute.io.stall := false.B
    execute.io.flush := false.B
    if (conf.useM) { execute.io.muldiv.get.busy := false.B }
    if (conf.useM) {
      execute.io.muldiv.get.busy := false.B  // No MulDiv for now
    }

    // Memory
    memory.io.in.valid := execute.io.out.valid
    memory.io.in.bits := execute.io.out.bits
    memory.io.stall := false.B
    memory.io.flush := false.B

    // Writeback
    writeback.io.in.valid := memory.io.out.valid
    writeback.io.in.bits := memory.io.out.bits
    writeback.io.stall := false.B
    writeback.io.flush := false.B

    // Writeback to regfile
    regfile.io.rd_addr := writeback.io.rf.addr
    regfile.io.rd_data := writeback.io.rf.data
    regfile.io.wen := writeback.io.rf.wen

    // Update PC
    pc := Mux(execute.io.br_taken, fetch.io.pc_in, pc + 4.U)
  }

  def connect3Stage(): Unit = {
    // 3-Stage: IF | ID+EX | MEM+WB

    // IF/ID register
    val if_id_uop = RegInit(MicroOp.bubble)

    val flush_if = execute.io.br_taken

    when(!stall) {
      when(flush_if) {
        if_id_uop := MicroOp.bubble
      }.elsewhen(fetch.io.valid_out) {
        if_id_uop := MicroOp(fetch.io.inst_out, fetch.io.pc_out, true.B)
      }.otherwise {
        if_id_uop := MicroOp.bubble
      }
    }

    // Fetch stage
    val jalr_target = (if_id_uop.rs1_data.asUInt + if_id_uop.imm.asUInt) & ~1.U(conf.xlen.W)
    val next_pc = Mux(execute.io.br_taken,
      Mux(execute.io.pc_sel === PC_JALR, jalr_target, execute.io.br_target),
      fetch.io.pc_out + 4.U)

    fetch.io.pc_in := next_pc
    fetch.io.pc_sel := Mux(execute.io.br_taken, execute.io.pc_sel, PC_PLUS4)
    fetch.io.br_taken := execute.io.br_taken
    fetch.io.stall := stall
    fetch.io.flush := flush_if
    fetch.io.reset_pc := io.resetPc

    // Decode stage
    decode.io.in.valid := if_id_uop.valid
    decode.io.in.bits := if_id_uop
    decode.io.stall := stall
    decode.io.flush := false.B

    // Register file
    regfile.io.rs1_addr := decode.io.rf.rs1_addr
    regfile.io.rs2_addr := decode.io.rf.rs2_addr
    decode.io.rf.rs1_data := regfile.io.rs1_data
    decode.io.rf.rs2_data := regfile.io.rs2_data

    // ID/EX register
    val id_ex_uop = RegInit(MicroOp.bubble)

    when(!stall) {
      val uop = Wire(new MicroOp())
      uop := decode.io.out.bits
      uop.rs1_data := decode.io.rf.rs1_data
      uop.rs2_data := decode.io.rf.rs2_data
      uop.imm := decode.io.imm
      id_ex_uop := uop
    }

    // Flush ID/EX on branch
    when(flush_if && !stall) {
      id_ex_uop := MicroOp.bubble
    }

    // Execute stage
    execute.io.in.valid := id_ex_uop.valid
    execute.io.in.bits := id_ex_uop
    execute.io.rs1_data := id_ex_uop.rs1_data
    execute.io.rs2_data := id_ex_uop.rs2_data
    execute.io.imm := id_ex_uop.imm
    execute.io.stall := stall
    execute.io.flush := false.B
    if (conf.useM) { execute.io.muldiv.get.busy := false.B }

    // EX/MEM register
    val ex_mem_uop = RegInit(MicroOp.bubble)

    when(!stall) {
      ex_mem_uop := execute.io.out.bits
    }

    // Memory + Writeback
    memory.io.in.valid := ex_mem_uop.valid
    memory.io.in.bits := ex_mem_uop
    memory.io.stall := stall
    memory.io.flush := false.B

    writeback.io.in.valid := memory.io.out.valid
    writeback.io.in.bits := memory.io.out.bits
    writeback.io.stall := stall
    writeback.io.flush := false.B

    // Writeback to regfile
    regfile.io.rd_addr := writeback.io.rf.addr
    regfile.io.rd_data := writeback.io.rf.data
    regfile.io.wen := writeback.io.rf.wen && !stall
  }

  def connect5Stage(): Unit = {
    // 5-Stage: IF | ID | EX | MEM | WB

    // IF/ID register
    val if_id_uop = RegInit(MicroOp.bubble)
    val flush_if = execute.io.br_taken

    when(!stall) {
      when(flush_if) {
        if_id_uop := MicroOp.bubble
      }.elsewhen(fetch.io.valid_out) {
        if_id_uop := MicroOp(fetch.io.inst_out, fetch.io.pc_out, true.B)
      }.otherwise {
        if_id_uop := MicroOp.bubble
      }
    }

    // Fetch stage
    val jalr_target = Wire(UInt(conf.xlen.W))
    jalr_target := (if_id_uop.rs1_data.asUInt + if_id_uop.imm.asUInt) & ~1.U(conf.xlen.W)

    val next_pc = Mux(execute.io.br_taken,
      Mux(execute.io.pc_sel === PC_JALR, jalr_target, execute.io.br_target),
      fetch.io.pc_out + 4.U)

    fetch.io.pc_in := next_pc
    fetch.io.pc_sel := Mux(execute.io.br_taken, execute.io.pc_sel, PC_PLUS4)
    fetch.io.br_taken := execute.io.br_taken
    fetch.io.stall := stall
    fetch.io.flush := flush_if
    fetch.io.reset_pc := io.resetPc

    // Decode stage
    decode.io.in.valid := if_id_uop.valid
    decode.io.in.bits := if_id_uop
    decode.io.stall := stall
    decode.io.flush := false.B

    // Register file
    regfile.io.rs1_addr := decode.io.rf.rs1_addr
    regfile.io.rs2_addr := decode.io.rf.rs2_addr
    decode.io.rf.rs1_data := regfile.io.rs1_data
    decode.io.rf.rs2_data := regfile.io.rs2_data

    // ID/EX register
    val id_ex_uop = RegInit(MicroOp.bubble)

    when(!stall) {
      val uop = Wire(new MicroOp())
      uop := decode.io.out.bits
      uop.rs1_data := decode.io.rf.rs1_data
      uop.rs2_data := decode.io.rf.rs2_data
      uop.imm := decode.io.imm
      id_ex_uop := uop
    }

    when(flush_if && !stall) {
      id_ex_uop := MicroOp.bubble
    }

    // Execute stage
    execute.io.in.valid := id_ex_uop.valid
    execute.io.in.bits := id_ex_uop
    execute.io.rs1_data := id_ex_uop.rs1_data
    execute.io.rs2_data := id_ex_uop.rs2_data
    execute.io.imm := id_ex_uop.imm
    execute.io.stall := stall
    execute.io.flush := false.B
    if (conf.useM) { execute.io.muldiv.get.busy := false.B }

    // EX/MEM register
    val ex_mem_uop = RegInit(MicroOp.bubble)

    when(!stall) {
      ex_mem_uop := execute.io.out.bits
    }

    // Memory stage
    memory.io.in.valid := ex_mem_uop.valid
    memory.io.in.bits := ex_mem_uop
    memory.io.stall := stall
    memory.io.flush := false.B

    // MEM/WB register
    val mem_wb_uop = RegInit(MicroOp.bubble)

    when(!stall) {
      mem_wb_uop := memory.io.out.bits
    }

    // Writeback stage
    writeback.io.in.valid := mem_wb_uop.valid
    writeback.io.in.bits := mem_wb_uop
    writeback.io.stall := stall
    writeback.io.flush := false.B

    // Writeback to regfile
    regfile.io.rd_addr := writeback.io.rf.addr
    regfile.io.rd_data := writeback.io.rf.data
    regfile.io.wen := writeback.io.rf.wen && !stall
  }
}
