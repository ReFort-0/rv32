package rv32.core.stage

import chisel3._
import chisel3.util._
import rv32.configs._
import rv32.core.util._

class ExecuteStageIO(implicit config: CoreConfig) extends Bundle {
  val idex = Input(new IDEXBundle)
  val stall = Input(Bool())
  val flush = Input(Bool())

  val exmem = Output(new EXMEMBundle)

  val pc_target = Output(UInt(32.W))
  val pc_take = Output(Bool())

  // Forwarding inputs for 5-stage pipeline
  val forward_a = Input(UInt(2.W))
  val forward_b = Input(UInt(2.W))
  val ex_fwd_data = Input(UInt(32.W))
  val mem_fwd_data = Input(UInt(32.W))
}

class ExecuteStage(implicit config: CoreConfig) extends Module {
  import Constants._
  val io = IO(new ExecuteStageIO)

  val alu = Module(new ALU)

  // Conditionally instantiate MulDiv unit
  val muldiv = if (config.useM) Some(Module(new MulDivUnit)) else None

  // Forwarding muxes for rs1 and rs2
  // forward_a/b encoding: 0=no forward, 1=forward from EX, 2=forward from MEM
  val rs1_fwd = MuxCase(io.idex.rs1_data, Seq(
    (io.forward_a === 1.U) -> io.ex_fwd_data,
    (io.forward_a === 2.U) -> io.mem_fwd_data
  ))

  val rs2_fwd = MuxCase(io.idex.rs2_data, Seq(
    (io.forward_b === 1.U) -> io.ex_fwd_data,
    (io.forward_b === 2.U) -> io.mem_fwd_data
  ))

  val op1 = MuxCase(rs1_fwd, Seq(
    (io.idex.op1_sel === OP1_PC) -> io.idex.pc,
    (io.idex.op1_sel === OP1_X0) -> 0.U
  ))

  val op2 = MuxCase(rs2_fwd, Seq(
    (io.idex.op2_sel === OP2_IMM) -> io.idex.imm,
    (io.idex.op2_sel === OP2_4) -> 4.U
  ))

  alu.io.op := io.idex.alu_op
  alu.io.in1 := op1
  alu.io.in2 := op2

  // Connect MulDiv unit if enabled
  if (config.useM) {
    muldiv.get.io.op := io.idex.muldiv_op
    muldiv.get.io.in1 := rs1_fwd
    muldiv.get.io.in2 := rs2_fwd
    muldiv.get.io.valid := io.idex.valid && (io.idex.fu_sel === FU_MULDIV)
  }

  // Select result based on functional unit
  val exec_result = if (config.useM) {
    Mux(io.idex.fu_sel === FU_MULDIV, muldiv.get.io.out, alu.io.out)
  } else {
    alu.io.out
  }

  // ALU operation validity assertion
  when(io.idex.valid && io.idex.fu_sel === FU_ALU) {
    assert(io.idex.alu_op <= ALU_COPY1, "Invalid ALU operation")
  }

  // Use forwarded values for branch comparison
  val branch_taken = MuxCase(false.B, Seq(
    (io.idex.branch_type === BR_EQ)  -> (rs1_fwd === rs2_fwd),
    (io.idex.branch_type === BR_NE)  -> (rs1_fwd =/= rs2_fwd),
    (io.idex.branch_type === BR_LT)  -> (rs1_fwd.asSInt < rs2_fwd.asSInt),
    (io.idex.branch_type === BR_LTU) -> (rs1_fwd < rs2_fwd),
    (io.idex.branch_type === BR_GE)  -> (rs1_fwd.asSInt >= rs2_fwd.asSInt),
    (io.idex.branch_type === BR_GEU) -> (rs1_fwd >= rs2_fwd),
    (io.idex.branch_type === BR_J)   -> true.B
  ))

  val pc_plus_imm = io.idex.pc + io.idex.imm
  val jalr_target = Cat((rs1_fwd + io.idex.imm)(31, 1), 0.U(1.W))

  io.pc_target := Mux(io.idex.op1_sel === OP1_RS1, jalr_target, pc_plus_imm)
  io.pc_take := branch_taken

  val next = Wire(new EXMEMBundle)
  next.pc := io.idex.pc
  next.inst := io.idex.inst
  next.valid := io.idex.valid
  next.rd_addr := io.idex.rd_addr
  next.alu_result := exec_result
  next.rs2_data := rs2_fwd  // Use forwarded rs2 for store data
  next.mem_en := io.idex.mem_en
  next.mem_rw := io.idex.mem_rw
  next.mem_type := io.idex.mem_type
  next.wb_sel := io.idex.wb_sel
  next.reg_write := io.idex.reg_write

  if (config.pipelineStages == 1) {
    io.exmem := next
  } else {
    val reg = RegInit(0.U.asTypeOf(new EXMEMBundle))
    when(io.flush) { reg.valid := false.B }
    .elsewhen(!io.stall) { reg := next }
    io.exmem := reg
  }
}
