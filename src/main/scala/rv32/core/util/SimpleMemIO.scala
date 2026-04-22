package rv32.core.util

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig

// ============================================================
// SimpleMemIO - Memory interface used by pipeline stages
// ============================================================

class SimpleMemIO(implicit conf: CoreConfig) extends Bundle {
  val req = new Bundle {
    val valid = Output(Bool())
    val addr  = Output(UInt(conf.xlen.W))
    val wdata = Output(UInt(conf.xlen.W))
    val wen   = Output(Bool())
    val mask  = Output(UInt(4.W))
  }
  val resp = Flipped(new Bundle {
    val valid = Input(Bool())
    val rdata = Input(UInt(conf.xlen.W))
  })
}
