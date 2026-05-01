package rv32.soc

import chisel3._
import chisel3.util._
import rv32.configs._

class GPIO(implicit config: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val bus = new SimpleBusSlaveIO
    val gpio_out = Output(UInt(32.W))
    val gpio_in = Input(UInt(32.W))
    val gpio_dir = Output(UInt(32.W))
  })

  // Memory-mapped registers
  val dataOutReg = RegInit(0.U(32.W))
  val dataInReg = RegInit(0.U(32.W))
  val directionReg = RegInit(0.U(32.W))

  // Output assignments
  io.gpio_out := dataOutReg
  io.gpio_dir := directionReg

  // Bus interface (must be defined before use)
  val req = io.bus.req
  val resp = io.bus.resp

  // Address decode (word-aligned)
  val addr = req.addr(3, 2)

  // Sample input only when read (avoid continuous sampling)
  when(req.valid && !req.wen && (addr === 1.U)) {
    dataInReg := io.gpio_in
  }

  // Read logic
  val rdata = WireDefault(0.U(32.W))
  when(req.valid && !req.wen) {
    switch(addr) {
      is(0.U) { rdata := dataOutReg }
      is(1.U) { rdata := dataInReg }
      is(2.U) { rdata := directionReg }
    }
  }

  // Write logic
  when(req.valid && req.wen) {
    switch(addr) {
      is(0.U) { dataOutReg := req.wdata }
      is(2.U) { directionReg := req.wdata }
    }
  }

  // Response
  resp.rdata := RegNext(rdata)
  resp.valid := RegNext(req.valid)
}
