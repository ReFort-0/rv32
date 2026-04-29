package rv32.core.stage

import chisel3._
import chisel3.util._
import rv32.configs._
import rv32.core.util._

class WritebackStageIO(implicit config: CoreConfig) extends Bundle {
  val memwb = Input(new MEMWBBundle)
  val stall = Input(Bool())

  val wb_rd_addr = Output(UInt(5.W))
  val wb_rd_data = Output(UInt(config.xlen.W))
  val wb_reg_write = Output(Bool())
}

class WritebackStage(implicit config: CoreConfig) extends Module {
  import Constants._
  val io = IO(new WritebackStageIO)

  val wb_data = MuxCase(io.memwb.alu_result, Seq(
    (io.memwb.wb_sel === WB_MEM) -> io.memwb.mem_rdata,
    (io.memwb.wb_sel === WB_PC4) -> (io.memwb.pc + 4.U)
  ))

  if (config.pipelineStages == 1) {
    io.wb_rd_addr := io.memwb.rd_addr
    io.wb_rd_data := wb_data
    io.wb_reg_write := io.memwb.reg_write && io.memwb.valid
  } else {
    io.wb_rd_addr := RegNext(io.memwb.rd_addr)
    io.wb_rd_data := RegNext(wb_data)
    io.wb_reg_write := RegNext(io.memwb.reg_write && io.memwb.valid && !io.stall)
  }
}
