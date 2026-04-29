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
}

class ExecuteStage(implicit config: CoreConfig) extends Module {
  import Constants._
  val io = IO(new ExecuteStageIO)

  val alu = Module(new ALU)

  val op1 = MuxCase(io.idex.rs1_data, Seq(
    (io.idex.op1_sel === OP1_PC) -> io.idex.pc,
    (io.idex.op1_sel === OP1_X0) -> 0.U
  ))

  val op2 = MuxCase(io.idex.rs2_data, Seq(
    (io.idex.op2_sel === OP2_IMM) -> io.idex.imm,
    (io.idex.op2_sel === OP2_4) -> 4.U
  ))

  alu.io.op := io.idex.alu_op
  alu.io.in1 := op1
  alu.io.in2 := op2

  val rs1_data = io.idex.rs1_data
  val rs2_data = io.idex.rs2_data

  val branch_taken = MuxCase(false.B, Seq(
    (io.idex.branch_type === BR_EQ)  -> (rs1_data === rs2_data),
    (io.idex.branch_type === BR_NE)  -> (rs1_data =/= rs2_data),
    (io.idex.branch_type === BR_LT)  -> (rs1_data.asSInt < rs2_data.asSInt),
    (io.idex.branch_type === BR_LTU) -> (rs1_data < rs2_data),
    (io.idex.branch_type === BR_GE)  -> (rs1_data.asSInt >= rs2_data.asSInt),
    (io.idex.branch_type === BR_GEU) -> (rs1_data >= rs2_data),
    (io.idex.branch_type === BR_J)   -> true.B
  ))

  val pc_plus_imm = io.idex.pc + io.idex.imm
  val jalr_target = Cat((rs1_data + io.idex.imm)(31, 1), 0.U(1.W))

  io.pc_target := Mux(io.idex.op1_sel === OP1_RS1, jalr_target, pc_plus_imm)
  io.pc_take := branch_taken

  val next = Wire(new EXMEMBundle)
  next.pc := io.idex.pc
  next.inst := io.idex.inst
  next.valid := io.idex.valid
  next.rd_addr := io.idex.rd_addr
  next.alu_result := alu.io.out
  next.rs2_data := io.idex.rs2_data
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
