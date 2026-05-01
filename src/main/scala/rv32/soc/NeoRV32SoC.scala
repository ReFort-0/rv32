package rv32.soc

import chisel3._
import chisel3.util._
import rv32.configs._
import rv32.core.NeoRV32Core

// ============================================================
// NeoRV32SoC - Complete SoC with CPU, RAM, and Peripherals
// ============================================================

class NeoRV32SoC(implicit config: NeoRV32Config) extends Module {
  val io = IO(new Bundle {
    // Optional debug output
    val debug = new Bundle {
      val pc = Output(UInt(32.W))
    }

    // Conditional peripheral IO
    val uart_tx = if (config.soc.enableUART) Some(Output(Bool())) else None
    val uart_rx = if (config.soc.enableUART) Some(Input(Bool())) else None
    val timer_irq = if (config.soc.enableTimer) Some(Output(Bool())) else None
    val gpio_out = if (config.soc.enableGPIO) Some(Output(UInt(32.W))) else None
    val gpio_in = if (config.soc.enableGPIO) Some(Input(UInt(32.W))) else None
    val gpio_dir = if (config.soc.enableGPIO) Some(Output(UInt(32.W))) else None
  })

  // Create CPU core
  implicit val coreConfig: CoreConfig = config.core
  val core = Module(new NeoRV32Core)

  // Memory map:
  // 0x0000_0000 - 0x0000_3FFF: On-chip RAM (16KB max, configurable)
  // Data memory interface for loads/stores

  // On-chip RAM for data
  val dataRam = Module(new OnChipRAM(config.soc.onChipRAMSize))

  // For single-cycle P0, we use unified memory (Harvard-style separation handled by Chisel)
  // Instruction and data memories are separate in hardware

  // Create instruction RAM (same size as data RAM for simplicity)
  val instRam = Module(new OnChipRAM(config.soc.onChipRAMSize))

  // Connect instruction memory
  instRam.io.req.valid := core.io.imem.req.valid
  instRam.io.req.addr := core.io.imem.req.addr
  instRam.io.req.wdata := core.io.imem.req.wdata
  instRam.io.req.wen := core.io.imem.req.wen
  instRam.io.req.mask := core.io.imem.req.mask
  core.io.imem.resp.valid := instRam.io.resp.valid
  core.io.imem.resp.rdata := instRam.io.resp.rdata

  // Conditionally instantiate peripherals
  val uart = if (config.soc.enableUART) Some(Module(new UART)) else None
  val timer = if (config.soc.enableTimer) Some(Module(new Timer)) else None
  val gpio = if (config.soc.enableGPIO) Some(Module(new GPIO)) else None

  // Memory map
  val RAM_BASE = 0x00000000L
  val RAM_SIZE = config.soc.onChipRAMSize.toLong
  val UART_BASE = 0x10000000L
  val UART_SIZE = 0x00001000L
  val TIMER_BASE = 0x20000000L
  val TIMER_SIZE = 0x00001000L
  val GPIO_BASE = 0x30000000L
  val GPIO_SIZE = 0x00001000L

  // Build slave list and address ranges
  val slaves = Seq(Some(dataRam)) ++ Seq(uart, timer, gpio).flatten
  val addrRanges = Seq(
    Some((RAM_BASE, RAM_BASE + RAM_SIZE)),
    if (uart.isDefined) Some((UART_BASE, UART_BASE + UART_SIZE)) else None,
    if (timer.isDefined) Some((TIMER_BASE, TIMER_BASE + TIMER_SIZE)) else None,
    if (gpio.isDefined) Some((GPIO_BASE, GPIO_BASE + GPIO_SIZE)) else None
  ).flatten

  // Instantiate demux if we have peripherals, otherwise direct connection
  if (slaves.length > 1) {
    val demux = Module(new SimpleBusDemux(slaves.length))

    // Connect master (CPU data memory)
    demux.io.master.req <> core.io.dmem.req
    core.io.dmem.resp <> demux.io.master.resp

    // Configure address ranges
    for (i <- 0 until slaves.length) {
      demux.io.addrRanges(i).start := addrRanges(i)._1.U
      demux.io.addrRanges(i).end := addrRanges(i)._2.U
    }

    // Connect slaves
    dataRam.io.req <> demux.io.slaves(0).req
    demux.io.slaves(0).resp <> dataRam.io.resp

    var slaveIdx = 1
    uart.foreach { u =>
      u.io.bus.req <> demux.io.slaves(slaveIdx).req
      demux.io.slaves(slaveIdx).resp <> u.io.bus.resp
      slaveIdx += 1
    }
    timer.foreach { t =>
      t.io.bus.req <> demux.io.slaves(slaveIdx).req
      demux.io.slaves(slaveIdx).resp <> t.io.bus.resp
      slaveIdx += 1
    }
    gpio.foreach { g =>
      g.io.bus.req <> demux.io.slaves(slaveIdx).req
      demux.io.slaves(slaveIdx).resp <> g.io.bus.resp
      slaveIdx += 1
    }
  } else {
    // Direct connection (no peripherals)
    dataRam.io.req <> core.io.dmem.req
    core.io.dmem.resp <> dataRam.io.resp
  }

  // Connect peripheral IO to top-level
  uart.foreach { u =>
    io.uart_tx.get := u.io.tx
    u.io.rx := io.uart_rx.get
  }
  timer.foreach { t =>
    io.timer_irq.get := t.io.irq
  }
  gpio.foreach { g =>
    io.gpio_out.get := g.io.gpio_out
    g.io.gpio_in := io.gpio_in.get
    io.gpio_dir.get := g.io.gpio_dir
  }

  // Debug outputs
  io.debug.pc := core.io.debug.pc
}
