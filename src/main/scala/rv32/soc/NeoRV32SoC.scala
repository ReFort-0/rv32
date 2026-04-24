package rv32.soc

import chisel3._
import chisel3.util._
import rv32.configs.{NeoRV32Config, CoreConfig, SoCConfig}
import rv32.core.NeoRV32Core
import rv32.soc.bus.{SimpleBus, SimpleBusMaster, SimpleBusSlave, Demux, CoreBusIF}
import rv32.soc.peripherals.{OnChipRAM, UART, Timer, GPIO}

// ============================================================
// NeoRV32SoC - Top-level SoC integrating Core + Bus + Peripherals
// ============================================================

class NeoRV32SoC(config: NeoRV32Config) extends Module {
  implicit val socConfig: SoCConfig = config.soc
  implicit val coreConfig: CoreConfig = config.core

  val io = IO(new Bundle {
    val interrupt_uart = Output(Bool())
    val interrupt_timer = Output(Bool())
    val interrupt_gpio = Output(Bool())
    val gpio_pins = Output(UInt(32.W))
    val uart_tx = Output(Bool())
    val debug_pc = Output(UInt(32.W))  // Expose PC for verification
    // Reset PC input for ACT4 testing (default 0x80000000)
    val resetPc = Input(UInt(32.W))
  })

  // ============================================================
  // CPU Core
  // ============================================================

  val core = Module(new NeoRV32Core(config))

  // Connect reset PC (default 0x80000000 if not specified)
  core.io.resetPc := Mux(io.resetPc =/= 0.U, io.resetPc, 0x80000000L.U(32.W))

  // ============================================================
  // Bus Infrastructure
  // ============================================================

  val coreBusIF = Module(new CoreBusIF(config))
  val demux = Module(new Demux(config))

  // Connect CPU to bus interface
  // Core requests: core outputs -> CoreBusIF inputs
  coreBusIF.io.core_imem.req.addr  := core.io.imem.req.addr
  coreBusIF.io.core_imem.req.wdata := core.io.imem.req.wdata
  coreBusIF.io.core_imem.req.wen   := core.io.imem.req.wen
  coreBusIF.io.core_imem.req.ren   := core.io.imem.req.valid
  coreBusIF.io.core_imem.req.mask  := core.io.imem.req.mask

  coreBusIF.io.core_dmem.req.addr  := core.io.dmem.req.addr
  coreBusIF.io.core_dmem.req.wdata := core.io.dmem.req.wdata
  coreBusIF.io.core_dmem.req.wen   := core.io.dmem.req.wen
  coreBusIF.io.core_dmem.req.ren   := core.io.dmem.req.valid
  coreBusIF.io.core_dmem.req.mask  := core.io.dmem.req.mask

  // Core responses: CoreBusIF outputs -> core inputs
  core.io.imem.resp.rdata := coreBusIF.io.core_imem.resp.rdata
  core.io.imem.resp.valid := coreBusIF.io.core_imem.resp.valid
  core.io.imem.resp.error := coreBusIF.io.core_imem.resp.error

  core.io.dmem.resp.rdata := coreBusIF.io.core_dmem.resp.rdata
  core.io.dmem.resp.valid := coreBusIF.io.core_dmem.resp.valid
  core.io.dmem.resp.error := coreBusIF.io.core_dmem.resp.error

  // Connect bus interface to demux
  demux.io.cpu.req <> coreBusIF.io.soc.req
  demux.io.cpu.resp <> coreBusIF.io.soc.resp

  // ============================================================
  // On-Chip RAM (always present)
  // ============================================================

  val ram = Module(new OnChipRAM(config.soc.onChipRAMSize)(coreConfig))
  ram.io.bus.req <> demux.io.ram.req
  ram.io.bus.resp <> demux.io.ram.resp

  // ============================================================
  // Peripherals (conditional instantiation)
  // ============================================================

  // UART
  val uart = if (config.soc.enableUART) Some(Module(new UART()(socConfig))) else None
  uart.foreach { u =>
    u.io.bus.req <> demux.io.uart.get.req
    u.io.bus.resp <> demux.io.uart.get.resp
    io.uart_tx := u.io.tx
    io.interrupt_uart := u.io.interrupt
  }
  if (!config.soc.enableUART) {
    io.uart_tx := false.B
    io.interrupt_uart := false.B
  }

  // Timer
  val timer = if (config.soc.enableTimer) Some(Module(new Timer()(socConfig))) else None
  timer.foreach { t =>
    t.io.bus.req <> demux.io.timer.get.req
    t.io.bus.resp <> demux.io.timer.get.resp
    io.interrupt_timer := t.io.interrupt
  }
  if (!config.soc.enableTimer) {
    io.interrupt_timer := false.B
  }

  // GPIO
  val gpio = if (config.soc.enableGPIO) Some(Module(new GPIO()(socConfig))) else None
  gpio.foreach { g =>
    g.io.bus.req <> demux.io.gpio.get.req
    g.io.bus.resp <> demux.io.gpio.get.resp
    io.gpio_pins := g.io.pins_out
    io.interrupt_gpio := false.B  // GPIO doesn't have interrupt
  }
  if (!config.soc.enableGPIO) {
    io.gpio_pins := 0.U
    io.interrupt_gpio := false.B
  }

  // Debug
  io.debug_pc := core.io.dbg.pc
}
