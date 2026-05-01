package rv32.soc

import chisel3._
import chisel3.util._
import rv32.configs._

class Timer(implicit config: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val bus = new SimpleBusSlaveIO
    val irq = Output(Bool())
  })

  // Memory-mapped registers
  val counterLow = RegInit(0.U(32.W))
  val counterHigh = RegInit(0.U(32.W))
  val compareLow = RegInit(0.U(32.W))
  val compareHigh = RegInit(0.U(32.W))
  val controlReg = RegInit(0.U(32.W))

  // Bus interface (must be defined before use)
  val req = io.bus.req
  val resp = io.bus.resp

  // Address decode (word-aligned)
  val addr = req.addr(4, 2)

  // Control bits
  val enable = controlReg(0)
  val irqEnable = controlReg(1)

  // 64-bit counter logic with write priority
  val counter = Cat(counterHigh, counterLow)
  val compare = Cat(compareHigh, compareLow)

  // Track if counter was written this cycle
  val counterWritten = WireDefault(false.B)

  // Write logic (has priority over auto-increment)
  when(req.valid && req.wen) {
    switch(addr) {
      is(0.U) {
        counterLow := req.wdata
        counterWritten := true.B
      }
      is(1.U) {
        counterHigh := req.wdata
        counterWritten := true.B
      }
      is(2.U) { compareLow := req.wdata }
      is(3.U) { compareHigh := req.wdata }
      is(4.U) { controlReg := req.wdata }
    }
  }

  // Auto-increment only if not written
  when(enable && !counterWritten) {
    val nextCounter = counter + 1.U
    counterLow := nextCounter(31, 0)
    counterHigh := nextCounter(63, 32)
  }

  // Interrupt generation
  io.irq := irqEnable && (counter >= compare)

  // Read logic
  val rdata = WireDefault(0.U(32.W))
  when(req.valid && !req.wen) {
    switch(addr) {
      is(0.U) { rdata := counterLow }
      is(1.U) { rdata := counterHigh }
      is(2.U) { rdata := compareLow }
      is(3.U) { rdata := compareHigh }
      is(4.U) { rdata := controlReg }
    }
  }

  // Response
  resp.rdata := RegNext(rdata)
  resp.valid := RegNext(req.valid)
}
