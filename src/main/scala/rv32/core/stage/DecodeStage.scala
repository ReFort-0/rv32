package rv32.core.stage

import chisel3._
import chisel3.util._
import rv32.configs._
import rv32.core.util._

class DecodeStageIO(implicit config: CoreConfig) extends Bundle {
  val ifid = Input(new IFIDBundle)
  val stall = Input(Bool())
  val flush = Input(Bool())

  val wb_rd_addr = Input(UInt(5.W))
  val wb_rd_data = Input(UInt(config.xlen.W))
  val wb_reg_write = Input(Bool())

  val idex = Output(new IDEXBundle)
}

class DecodeStage(implicit config: CoreConfig) extends Module {
  val io = IO(new DecodeStageIO)

  val decoder = Module(new Decoder)
  val immgen = Module(new ImmGen)
  val regfile = Module(new RegisterFile)

  decoder.io.inst := io.ifid.inst

  immgen.io.inst := io.ifid.inst
  immgen.io.sel := decoder.io.ctrl.imm_type

  regfile.io.rs1_addr := io.ifid.inst(19, 15)
  regfile.io.rs2_addr := io.ifid.inst(24, 20)
  regfile.io.rd_addr := io.wb_rd_addr
  regfile.io.rd_data := io.wb_rd_data
  regfile.io.wen := io.wb_reg_write

  val next = Wire(new IDEXBundle)
  next.pc := io.ifid.pc
  next.inst := io.ifid.inst
  next.valid := io.ifid.valid
  next.rs1_addr := io.ifid.inst(19, 15)
  next.rs2_addr := io.ifid.inst(24, 20)
  next.rd_addr := io.ifid.inst(11, 7)
  next.rs1_data := regfile.io.rs1_data
  next.rs2_data := regfile.io.rs2_data
  next.imm := immgen.io.out
  next.op1_sel := decoder.io.ctrl.op1_sel
  next.op2_sel := decoder.io.ctrl.op2_sel
  next.alu_op := decoder.io.ctrl.alu_op
  next.fu_sel := decoder.io.ctrl.fu_sel
  next.muldiv_op := decoder.io.ctrl.muldiv_op
  next.branch_type := decoder.io.ctrl.branch_type
  next.mem_en := decoder.io.ctrl.mem_en
  next.mem_rw := decoder.io.ctrl.mem_rw
  next.mem_type := decoder.io.ctrl.mem_type
  next.wb_sel := decoder.io.ctrl.wb_sel
  next.reg_write := decoder.io.ctrl.reg_write

  io.idex := (if (config.pipelineStages == 1) {
    next
  } else {
    PipelineConnect.withEmbeddedValid(next, io.stall, io.flush)
  })
}
