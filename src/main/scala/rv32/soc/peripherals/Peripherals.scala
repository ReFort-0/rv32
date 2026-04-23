package rv32.soc.peripherals

import chisel3._
import chisel3.util._
import rv32.configs.{CoreConfig, SoCConfig, NeoRV32Config}
import rv32.soc.bus.{SimpleBusSlave}

// ============================================================
// OnChipRAM - On-chip memory for program/data storage
// ============================================================

class OnChipRAM(size: Int)(implicit conf: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val bus = new SimpleBusSlave()
  })

  val memory = Mem(size / 4, UInt(32.W))

  // Simple address decoding - word aligned
  val wordAddr = io.bus.req.addr(31, 2)
  val inRange = wordAddr < (size / 4).U

  // Read
  io.bus.resp.rdata := Mux(inRange, memory.read(wordAddr), 0.U)
  io.bus.resp.valid := io.bus.req.ren || io.bus.req.wen
  io.bus.resp.error := !inRange

  // Write
  when(io.bus.req.wen && inRange) {
    memory.write(wordAddr, io.bus.req.wdata)
  }
}

// ============================================================
// UART - Simple UART with TX/RX FIFOs
// ============================================================

class UART(implicit conf: SoCConfig) extends Module {
  val io = IO(new Bundle {
    val bus = new SimpleBusSlave()
    val tx = Output(Bool())
    val interrupt = Output(Bool())
  })

  // Registers
  val tx_data = RegInit(0.U(8.W))
  val tx_valid = RegInit(false.B)

  // Address decoding (offset 0x1000_0000)
  val regAddr = io.bus.req.addr(3, 2)

  // TX register (offset 0)
  when(io.bus.req.wen && regAddr === 0.U) {
    tx_data := io.bus.req.wdata(7, 0)
    tx_valid := true.B
  }

  // TX data register readback
  io.bus.resp.rdata := Cat(0.U(24.W), tx_data)

  io.bus.resp.valid := io.bus.req.ren || io.bus.req.wen
  io.bus.resp.error := false.B

  io.tx := tx_valid
  io.interrupt := false.B

  // TX busy indicator
  when(tx_valid) {
    tx_valid := false.B
  }
}

// ============================================================
// Timer - 64-bit timer with compare interrupt
// ============================================================

class Timer(implicit conf: SoCConfig) extends Module {
  val io = IO(new Bundle {
    val bus = new SimpleBusSlave()
    val interrupt = Output(Bool())
  })

  // 64-bit counter (two 32-bit registers)
  val counter_low = RegInit(0.U(32.W))
  val counter_high = RegInit(0.U(32.W))

  // Compare registers
  val compare_low = RegInit(0.U(32.W))
  val compare_high = RegInit(0.U(32.W))

  // Control register
  val control = RegInit(0.U(32.W))

  // Address decoding (offset 0x2000_0000)
  val regAddr = io.bus.req.addr(5, 2)

  // Write registers
  when(io.bus.req.wen) {
    switch(regAddr) {
      is(0.U) { counter_low := io.bus.req.wdata }
      is(1.U) { counter_high := io.bus.req.wdata }
      is(2.U) { compare_low := io.bus.req.wdata }
      is(3.U) { compare_high := io.bus.req.wdata }
      is(4.U) { control := io.bus.req.wdata }
    }
  }

  // Read registers
  io.bus.resp.rdata := MuxCase(0.U, Seq(
    (regAddr === 0.U) -> counter_low,
    (regAddr === 1.U) -> counter_high,
    (regAddr === 2.U) -> compare_low,
    (regAddr === 3.U) -> compare_high,
    (regAddr === 4.U) -> control
  ))

  io.bus.resp.valid := io.bus.req.ren || io.bus.req.wen
  io.bus.resp.error := false.B

  // Increment counter
  counter_low := counter_low + 1.U

  // Compare interrupt
  val match_int = (counter_low === compare_low) && (counter_high === compare_high)
  io.interrupt := match_int && control(0)
}

// ============================================================
// GPIO - General Purpose Input/Output
// ============================================================

class GPIO(implicit conf: SoCConfig) extends Module {
  val io = IO(new Bundle {
    val bus = new SimpleBusSlave()
    val pins_out = Output(UInt(32.W))
  })

  // Data register
  val data_reg = RegInit(0.U(32.W))
  // Direction register (1 = output, 0 = input)
  val dir_reg = RegInit(0.U(32.W))

  // Address decoding (offset 0x3000_0000)
  val regAddr = io.bus.req.addr(3, 2)

  // Write registers
  when(io.bus.req.wen) {
    switch(regAddr) {
      is(0.U) { data_reg := io.bus.req.wdata }
      is(1.U) { dir_reg := io.bus.req.wdata }
    }
  }

  // Read registers
  io.bus.resp.rdata := MuxCase(0.U, Seq(
    (regAddr === 0.U) -> data_reg,
    (regAddr === 1.U) -> dir_reg
  ))

  io.bus.resp.valid := io.bus.req.ren || io.bus.req.wen
  io.bus.resp.error := false.B

  // Output pins - simplified (all pins output)
  io.pins_out := data_reg
}
