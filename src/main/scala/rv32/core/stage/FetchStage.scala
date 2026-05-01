package rv32.core.stage

import chisel3._
import chisel3.util._
import rv32.configs._
import rv32.core.util._

class FetchStageIO(implicit config: CoreConfig) extends Bundle {
  val stall = Input(Bool())
  val flush = Input(Bool())
  val pc_next = Input(UInt(32.W))
  val pc_we = Input(Bool())

  val inst_req = Output(new SimpleMemReq)
  val inst_resp = Input(new SimpleMemResp)

  val ifid = Output(new IFIDBundle)
  val pc = Output(UInt(32.W))
}

class FetchStage(implicit config: CoreConfig) extends Module {
  val io = IO(new FetchStageIO)

  val pc_reg = RegInit(0x80000000L.U(32.W))

  io.inst_req.addr := pc_reg
  io.inst_req.wdata := 0.U
  io.inst_req.wen := false.B
  io.inst_req.mask := 0.U
  io.inst_req.valid := !io.stall

  when(io.flush || io.pc_we) {
    pc_reg := io.pc_next
  }.elsewhen(!io.stall) {
    pc_reg := pc_reg + 4.U
  }

  io.pc := pc_reg

  io.ifid.pc := pc_reg
  io.ifid.inst := io.inst_resp.rdata
  io.ifid.valid := io.inst_resp.valid && !io.flush
}
