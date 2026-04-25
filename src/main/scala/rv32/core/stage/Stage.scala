package rv32.core.stage

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig
import rv32.core.util._

// ============================================================
// Stage Type - Identifies which pipeline stage this is
// ============================================================

object StageType extends ChiselEnum {
  val FETCH, DECODE, EXECUTE, MEMORY, WRITEBACK = Value
  // For 1-stage: COMBINED
  val COMBINED = Value
  // For 3-stage: ID_EXE, MEM_WB
  val DECODE_EXECUTE = Value
  val MEMORY_WRITEBACK = Value
}

// ============================================================
// Stage IO - Common interface for all pipeline stages
// ============================================================

class StageIO(implicit conf: CoreConfig) extends Bundle {
  // Pipeline inputs
  val in = Flipped(Valid(new MicroOp()))
  // Pipeline outputs
  val out = Valid(new MicroOp())
  // Control signals
  val stall = Input(Bool())
  val flush = Input(Bool())
  // Memory interface (for stages that need it)
  val dmem = if (conf.useM) Some(new Bundle {
    val req = Output(new SimpleMemReq())
    val resp = Input(new SimpleMemResp())
  }) else None
}

// ============================================================
// Simple Memory Interface - simplified from Bus, for single-port memory
// (Defined in rv32.core.util.SimpleMemIO)
// ============================================================

// SimpleMemIO import from util package
import rv32.core.util.{SimpleMemReq, SimpleMemResp, SimpleMemIO}
import rv32.core.functionalunit.ExuBlock

// ============================================================
// Fetch Stage - Instruction Fetch
// ============================================================

class FetchStage(implicit conf: CoreConfig) extends Module {
  val io = IO(new Bundle {
    // Instruction memory interface - Stage is initiator
    val imem = new Bundle {
      val req = Output(new SimpleMemReq())
      val resp = Input(new SimpleMemResp())
    }
    val pc_out = Output(UInt(conf.xlen.W))
    val inst_out = Output(UInt(32.W))
    val valid_out = Output(Bool())
    // PC input (from branch target)
    val pc_in = Input(UInt(conf.xlen.W))
    val pc_sel = Input(UInt(2.W))  // PC_PLUS4, PC_BRJMP, PC_JALR
    val br_taken = Input(Bool())
    val stall = Input(Bool())
    val flush = Input(Bool())
    // Reset PC (configurable, default 0x80000000)
    val reset_pc = Input(UInt(conf.xlen.W))
  })

  val PC_PLUS4 = 0.U(2.W)
  val PC_BRJMP = 1.U(2.W)
  val PC_JALR = 2.U(2.W)

  // Default reset PC value
  val defaultResetPC = 0x00000000L.U(conf.xlen.W)
  val resetPC = Mux(io.reset_pc =/= 0.U, io.reset_pc, defaultResetPC)

  // PC register (initialized from reset_pc parameter)
  val pc = RegInit(resetPC)

  // Next PC calculation
  val pc_plus4 = pc + 4.U
  val next_pc = MuxCase(pc_plus4, Array(
    (io.pc_sel === PC_BRJMP && io.br_taken) -> io.pc_in,
    (io.pc_sel === PC_JALR) -> Cat(io.pc_in(conf.xlen - 1, 1), 0.U(1.W))
  ))

  // Outputs (registered)
  val pc_reg = RegInit(resetPC)
  val inst_reg = RegInit(Constants.BUBBLE)
  val valid_reg = RegInit(false.B)

  // Update PC and outputs when not stalled
  when(!io.stall) {
    pc := next_pc
    pc_reg := next_pc
    inst_reg := io.imem.resp.rdata
    valid_reg := !io.flush && io.imem.resp.valid
  }

  // Instruction memory request
  io.imem.req.valid := !io.stall || io.flush
  io.imem.req.addr := next_pc
  io.imem.req.wen := false.B
  io.imem.req.wdata := 0.U
  io.imem.req.mask := 0.U

  io.pc_out := pc_reg
  io.inst_out := inst_reg
  io.valid_out := valid_reg
}

// ============================================================
// Decode Stage - Instruction Decode
// ============================================================

class DecodeStage(implicit conf: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Valid(new MicroOp()))
    val out = Valid(new MicroOp())
    val stall = Input(Bool())
    val flush = Input(Bool())

    // Register file interface
    val rf = new Bundle {
      val rs1_addr = Output(UInt(5.W))
      val rs2_addr = Output(UInt(5.W))
      val rs1_data = Input(UInt(conf.xlen.W))
      val rs2_data = Input(UInt(conf.xlen.W))
    }

    // Immediate generator output
    val imm = Output(UInt(conf.xlen.W))
  })

  val decoder = Module(new Decoder())
  val immgen = Module(new ImmGen())

  decoder.io.inst := io.in.bits.inst
  immgen.io.inst := io.in.bits.inst
  immgen.io.sel := decoder.io.ctrl.imm_sel

  val ctrl = decoder.io.ctrl

  // Register file address outputs
  io.rf.rs1_addr := io.in.bits.rs1
  io.rf.rs2_addr := io.in.bits.rs2

  // Output uop
  val out_uop = Wire(new MicroOp())
  out_uop := io.in.bits
  out_uop.ctrl := ctrl
  out_uop.imm := immgen.io.out

  // Register outputs when not stalled
  val out_reg = RegInit(MicroOp.bubble)
  when(!io.stall) {
    when(io.flush || !io.in.valid) {
      out_reg := MicroOp.bubble
    }.otherwise {
      out_reg := out_uop
    }
  }

  io.out.bits := out_reg
  io.out.valid := out_reg.valid
  io.imm := immgen.io.out
}

// ============================================================
// Execute Stage - ALU / Functional Unit Execution
// ============================================================

class ExecuteStage(implicit conf: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Valid(new MicroOp()))
    val out = Valid(new MicroOp())
    val stall = Input(Bool())
    val flush = Input(Bool())

    // Register read data
    val rs1_data = Input(UInt(conf.xlen.W))
    val rs2_data = Input(UInt(conf.xlen.W))
    val imm = Input(UInt(conf.xlen.W))

    // Branch outputs
    val br_target = Output(UInt(conf.xlen.W))
    val br_taken = Output(Bool())
    val pc_sel = Output(UInt(2.W))

    // MulDiv busy signal output (only valid when useM=true)
    val muldiv_busy = if (conf.useM) Some(Output(Bool())) else None
  })

  // Conditionally instantiate execution unit based on M extension
  val alu = Module(new ALU())
  val exu = if (conf.useM) Some(Module(new ExuBlock())) else None

  // Operand selection
  val op1 = MuxCase(io.rs1_data, Array(
    (io.in.bits.ctrl.op1_sel === Constants.OP1_PC) -> io.in.bits.pc,
    (io.in.bits.ctrl.op1_sel === Constants.OP1_X0) -> 0.U
  ))

  val op2 = MuxCase(io.rs2_data, Array(
    (io.in.bits.ctrl.op2_sel === Constants.OP2_IMM) -> io.imm,
    (io.in.bits.ctrl.op2_sel === Constants.OP2_4) -> 4.U
  ))

  // Execute
  // When useM=true: use ExuBlock (ALU + MulDiv)
  // When useM=false: use ALU only (smaller area)
  val muldiv_busy_internal = Wire(Bool())
  val alu_result = if (conf.useM) {
    exu.get.io.alu_op := io.in.bits.ctrl.alu_op
    exu.get.io.muldiv_op := io.in.bits.ctrl.muldiv_op
    exu.get.io.in1 := op1
    exu.get.io.in2 := op2
    exu.get.io.use_muldiv := io.in.bits.ctrl.fu_sel === Constants.FU_MULDIV
    muldiv_busy_internal := exu.get.io.busy
    io.muldiv_busy.get := muldiv_busy_internal
    exu.get.io.out
  } else {
    // Only ALU ops are valid when M extension disabled
    alu.io.op := io.in.bits.ctrl.alu_op
    alu.io.in1 := op1
    alu.io.in2 := op2
    muldiv_busy_internal := false.B  // Always false when M disabled
    alu.io.out
  }

  // Branch condition check
  val rs1_eq_rs2 = io.rs1_data === io.rs2_data
  val rs1_lt_rs2 = io.rs1_data.asSInt < io.rs2_data.asSInt
  val rs1_ltu_rs2 = io.rs1_data.asUInt < io.rs2_data.asUInt

  val br_taken = io.in.bits.valid && MuxCase(false.B, Seq(
    (io.in.bits.ctrl.br_type === Constants.BR_EQ) -> rs1_eq_rs2,
    (io.in.bits.ctrl.br_type === Constants.BR_NE) -> !rs1_eq_rs2,
    (io.in.bits.ctrl.br_type === Constants.BR_LT) -> rs1_lt_rs2,
    (io.in.bits.ctrl.br_type === Constants.BR_GE) -> !rs1_lt_rs2,
    (io.in.bits.ctrl.br_type === Constants.BR_LTU) -> rs1_ltu_rs2,
    (io.in.bits.ctrl.br_type === Constants.BR_GEU) -> !rs1_ltu_rs2,
    (io.in.bits.ctrl.br_type === Constants.BR_J) -> true.B  // JAL always taken
  ))

  val br_target = io.in.bits.pc + io.imm
  val jalr_target = (io.rs1_data + io.imm) & ~1.U(conf.xlen.W)

  io.br_target := Mux(io.in.bits.ctrl.pc_sel === Constants.PC_JALR, jalr_target, br_target)
  io.br_taken := br_taken
  io.pc_sel := Mux(br_taken || (io.in.bits.ctrl.br_type === Constants.BR_J), io.in.bits.ctrl.pc_sel, Constants.PC_PLUS4)

  // Output
  val out_uop = Wire(new MicroOp())
  out_uop := io.in.bits
  out_uop.alu_result := alu_result
  out_uop.target := io.br_target
  out_uop.br_taken := br_taken
  out_uop.mem_addr := alu_result
  out_uop.mem_wdata := io.rs2_data

  // Register outputs
  val out_reg = RegInit(MicroOp.bubble)
  when(!io.stall) {
    when(io.flush || !io.in.valid) {
      out_reg := MicroOp.bubble
    }.otherwise {
      out_reg := out_uop
    }
  }

  io.out.bits := out_reg
  io.out.valid := out_reg.valid
}

// ============================================================
// Memory Stage - Data Memory Access
// ============================================================

class MemoryStage(implicit conf: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Valid(new MicroOp()))
    val out = Valid(new MicroOp())
    val stall = Input(Bool())
    val flush = Input(Bool())

    // Data memory interface - Stage is initiator
    val dmem = new Bundle {
      val req = Output(new SimpleMemReq())
      val resp = Input(new SimpleMemResp())
    }
  })

  val mem_fcn_load = io.in.bits.ctrl.mem_fcn === Constants.M_XRD
  val mem_fcn_store = io.in.bits.ctrl.mem_fcn === Constants.M_XWR
  val mem_valid = mem_fcn_load || mem_fcn_store

  // Memory request
  io.dmem.req.valid := mem_valid && io.in.valid && !io.stall
  io.dmem.req.addr := io.in.bits.mem_addr
  io.dmem.req.wen := mem_fcn_store
  io.dmem.req.wdata := io.in.bits.mem_wdata

  // Byte/half/word mask based on mem_typ
  io.dmem.req.mask := MuxCase(0xF.U, Seq(
    (io.in.bits.ctrl.mem_typ === Constants.MT_B)  -> 0x1.U,
    (io.in.bits.ctrl.mem_typ === Constants.MT_BU) -> 0x1.U,
    (io.in.bits.ctrl.mem_typ === Constants.MT_H)  -> 0x3.U,
    (io.in.bits.ctrl.mem_typ === Constants.MT_HU) -> 0x3.U,
    (io.in.bits.ctrl.mem_typ === Constants.MT_W)  -> 0xF.U
  ))

  // Output
  val out_uop = Wire(new MicroOp())
  out_uop := io.in.bits
  out_uop.mem_data := io.dmem.resp.rdata

  // Register outputs
  val out_reg = RegInit(MicroOp.bubble)
  when(!io.stall) {
    when(io.flush || !io.in.valid) {
      out_reg := MicroOp.bubble
    }.otherwise {
      out_reg := out_uop
    }
  }

  io.out.bits := out_reg
  io.out.valid := out_reg.valid
}

// ============================================================
// Writeback Stage - Register Writeback
// ============================================================

class WritebackStage(implicit conf: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Valid(new MicroOp()))
    val stall = Input(Bool())
    val flush = Input(Bool())

    // Register file write interface
    val rf = new Bundle {
      val addr = Output(UInt(5.W))
      val data = Output(UInt(conf.xlen.W))
      val wen = Output(Bool())
    }
  })

  // Writeback data selection
  val wb_data = MuxCase(io.in.bits.alu_result, Seq(
    (io.in.bits.ctrl.wb_sel === Constants.WB_MEM) -> io.in.bits.mem_data,
    (io.in.bits.ctrl.wb_sel === Constants.WB_PC4) -> (io.in.bits.pc + 4.U)
  ))

  // Load byte/half word sign/zero extension
  val addr_offset = io.in.bits.mem_addr(1, 0)
  val mem_rdata = io.in.bits.mem_data

  val load_byte = MuxCase(mem_rdata(7, 0), Seq(
    (addr_offset === 1.U) -> mem_rdata(15, 8),
    (addr_offset === 2.U) -> mem_rdata(23, 16),
    (addr_offset === 3.U) -> mem_rdata(31, 24)
  ))

  val load_half = Mux(addr_offset(1), mem_rdata(31, 16), mem_rdata(15, 0))

  val load_result = MuxCase(mem_rdata, Seq(
    (io.in.bits.ctrl.mem_typ === Constants.MT_B)  -> Cat(Fill(24, load_byte(7)), load_byte),
    (io.in.bits.ctrl.mem_typ === Constants.MT_BU) -> Cat(Fill(24, 0.U), load_byte),
    (io.in.bits.ctrl.mem_typ === Constants.MT_H)  -> Cat(Fill(16, load_half(15)), load_half),
    (io.in.bits.ctrl.mem_typ === Constants.MT_HU) -> Cat(Fill(16, 0.U), load_half)
  ))

  val final_wb_data = Mux(io.in.bits.ctrl.wb_sel === Constants.WB_MEM, load_result, wb_data)

  io.rf.addr := io.in.bits.rd
  io.rf.data := final_wb_data
  io.rf.wen := io.in.valid && io.in.bits.ctrl.rf_wen && !io.stall && !io.flush
}
