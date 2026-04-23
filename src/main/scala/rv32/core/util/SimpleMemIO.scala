package rv32.core.util

import chisel3._
import chisel3.util._
// Directionless bundle - directions assigned at instantiation

class SimpleMemReq extends Bundle {
  val valid = Bool()
  val addr  = UInt(32.W)
  val wdata = UInt(32.W)
  val wen   = Bool()
  val mask  = UInt(4.W)
}

class SimpleMemResp extends Bundle {
  val valid = Bool()
  val rdata = UInt(32.W)
  val error = Bool()
}

// Directionless bundle
class SimpleMemIO extends Bundle {
  val req = new SimpleMemReq()
  val resp = new SimpleMemResp()
}
