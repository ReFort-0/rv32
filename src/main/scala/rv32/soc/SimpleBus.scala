package rv32.soc

import chisel3._
import chisel3.util._
import rv32.configs._

// ============================================================
// SimpleBus - Minimal Memory-Mapped Bus Interface
// ============================================================

class SimpleBusReq(implicit config: CoreConfig) extends Bundle {
  val addr  = UInt(32.W)
  val wdata = UInt(32.W)
  val wen   = Bool()
  val mask  = UInt(4.W)   // Byte mask for writes
  val valid = Bool()
}

class SimpleBusResp(implicit config: CoreConfig) extends Bundle {
  val rdata = UInt(32.W)
  val valid = Bool()
}

class SimpleBusMaster(implicit config: CoreConfig) extends Bundle {
  val req = Output(new SimpleBusReq)
  val resp = Input(new SimpleBusResp)
}

class SimpleBusSlave(implicit config: CoreConfig) extends Bundle {
  val req = Input(new SimpleBusReq)
  val resp = Output(new SimpleBusResp)
}

class SimpleBusSlaveIO(implicit config: CoreConfig) extends Bundle {
  val req = Input(new SimpleBusReq)
  val resp = Output(new SimpleBusResp)
}

// ============================================================
// SimpleBus Demux - Routes requests to RAM or Peripherals
// ============================================================

class SimpleBusDemuxIO(nSlaves: Int)(implicit config: CoreConfig) extends Bundle {
  val master = new SimpleBusSlave  // From CPU
  val slaves = Vec(nSlaves, new SimpleBusMaster)  // To slaves

  // Address decode configuration
  val addrRanges = Input(Vec(nSlaves, new Bundle {
    val start = UInt(32.W)
    val end = UInt(32.W)
  }))
}

class SimpleBusDemux(nSlaves: Int)(implicit config: CoreConfig) extends Module {
  val io = IO(new SimpleBusDemuxIO(nSlaves))

  // Track unmapped address access
  val unmapped_req = RegInit(false.B)
  val anyHit = io.addrRanges.map(r => io.master.req.addr >= r.start && io.master.req.addr < r.end).reduce(_ || _)

  when(io.master.req.valid && !anyHit) {
    unmapped_req := true.B
  }.elsewhen(unmapped_req) {
    unmapped_req := false.B
  }

  // Address decode
  val addrHits = io.addrRanges.map(r => io.master.req.addr >= r.start && io.master.req.addr < r.end)

  // Connect to slaves
  for (i <- 0 until nSlaves) {
    io.slaves(i).req.valid := io.master.req.valid && addrHits(i)
    io.slaves(i).req.addr := io.master.req.addr
    io.slaves(i).req.wdata := io.master.req.wdata
    io.slaves(i).req.wen := io.master.req.wen
    io.slaves(i).req.mask := io.master.req.mask
  }

  // Response mux - priority based (highest index first for easier override)
  val respValid = addrHits.map(_ && io.master.req.valid).zip(io.slaves.map(_.resp.valid)).map { case (a, v) => a && v }
  val anyRespValid = respValid.reduce(_ || _)

  // Use Mux1H with default value of 0 when no slave responds
  val respData = Mux(anyRespValid, Mux1H(respValid.zip(io.slaves.map(_.resp.rdata))), 0.U)

  io.master.resp.valid := anyRespValid || unmapped_req
  io.master.resp.rdata := Mux(unmapped_req, 0xDEADDEADL.U, respData)
}
