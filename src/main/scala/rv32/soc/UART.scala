package rv32.soc

import chisel3._
import chisel3.util._
import rv32.configs._

class UART(implicit config: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val bus = new SimpleBusSlaveIO
    val tx = Output(Bool())
    val rx = Input(Bool())
  })

  // Memory-mapped registers
  val txDataReg = RegInit(0.U(32.W))
  val rxDataReg = RegInit(0.U(32.W))
  val statusReg = RegInit(1.U(32.W))  // TX ready by default
  val controlReg = RegInit(0.U(32.W))

  // Status bits
  val txReady = statusReg(0)
  val rxValid = statusReg(1)

  // Control bits
  val txEnable = controlReg(0)
  val rxEnable = controlReg(1)

  // Bus interface (must be defined before use)
  val req = io.bus.req
  val resp = io.bus.resp

  // Address decode (word-aligned)
  val addr = req.addr(3, 2)

  // Simple TX/RX logic (minimal implementation)
  // RX sampling (simplified - just capture input)
  when(rxEnable && io.rx) {
    rxDataReg := Cat(0.U(31.W), io.rx)
    statusReg := statusReg | 2.U  // Set rxValid bit
  }

  // Clear rxValid when RX data register is read
  when(req.valid && !req.wen && (addr === 1.U)) {
    statusReg := statusReg & ~2.U  // Clear rxValid bit
  }

  // TX output
  io.tx := txDataReg(0)

  // Read logic
  val rdata = WireDefault(0.U(32.W))
  when(req.valid && !req.wen) {
    switch(addr) {
      is(0.U) { rdata := txDataReg }
      is(1.U) { rdata := rxDataReg }
      is(2.U) { rdata := statusReg }
      is(3.U) { rdata := controlReg }
    }
  }

  // Write logic
  when(req.valid && req.wen) {
    switch(addr) {
      is(0.U) { txDataReg := req.wdata }
      is(3.U) { controlReg := req.wdata }
    }
  }

  // Response
  resp.rdata := RegNext(rdata)
  resp.valid := RegNext(req.valid)
}
