package rv32.soc.bus

import chisel3._
import chisel3.util._
import rv32.configs.{CoreConfig, SoCConfig, NeoRV32Config}

// ============================================================
// SimpleBus - Minimal memory-mapped bus interface
// ============================================================

// Bundle without directions - directions assigned at interface level
class SimpleBusReq extends Bundle {
  val addr  = UInt(32.W)
  val wdata = UInt(32.W)
  val wen   = Bool()
  val ren   = Bool()
  val mask  = UInt(4.W)
}

class SimpleBusResp extends Bundle {
  val rdata = UInt(32.W)
  val valid = Bool()
  val error = Bool()
}

class SimpleBus extends Bundle {
  val req  = new SimpleBusReq()
  val resp = new SimpleBusResp()
}

// Master interface (CPU side) - drives request, receives response
class SimpleBusMaster extends Bundle {
  val req  = Output(new SimpleBusReq())
  val resp = Input(new SimpleBusResp())
}

// Slave interface (peripheral side) - receives request, drives response
class SimpleBusSlave extends Bundle {
  val req  = Input(new SimpleBusReq())
  val resp = Output(new SimpleBusResp())
}

// ============================================================
// Demux - Address Demultiplexer for Static Memory Map
// ============================================================

class Demux(config: NeoRV32Config) extends Module {
  val io = IO(new Bundle {
    val cpu = new SimpleBusSlave()
    val ram = new SimpleBusMaster()
    val uart = if (config.soc.enableUART) Some(new SimpleBusMaster()) else None
    val timer = if (config.soc.enableTimer) Some(new SimpleBusMaster()) else None
    val gpio = if (config.soc.enableGPIO) Some(new SimpleBusMaster()) else None
  })

  // Static address map
  val ADDR_RAM   = "h00000000".U
  val ADDR_UART  = "h10000000".U
  val ADDR_TIMER = "h20000000".U
  val ADDR_GPIO  = "h30000000".U

  val RAM_MASK   = "hF0000000".U(32.W)   // 0x0-------
  val UART_MASK  = "hF0000000".U(32.W)   // 0x1-------
  val TIMER_MASK = "hF0000000".U(32.W)   // 0x2-------
  val GPIO_MASK  = "hF0000000".U(32.W)   // 0x3-------

  // Address decoding
  val addr = io.cpu.req.addr
  val sel_ram   = (addr & RAM_MASK) === ADDR_RAM
  val sel_uart  = if (config.soc.enableUART)  Some((addr & UART_MASK)  === ADDR_UART)  else None
  val sel_timer = if (config.soc.enableTimer) Some((addr & TIMER_MASK) === ADDR_TIMER) else None
  val sel_gpio  = if (config.soc.enableGPIO)  Some((addr & GPIO_MASK)  === ADDR_GPIO)  else None

  // Connect RAM
  io.ram.req.addr  := io.cpu.req.addr
  io.ram.req.wdata := io.cpu.req.wdata
  io.ram.req.wen   := io.cpu.req.wen   && sel_ram
  io.ram.req.ren   := io.cpu.req.ren   && sel_ram
  io.ram.req.mask  := io.cpu.req.mask

  // UART
  if (config.soc.enableUART) {
    io.uart.get.req.addr  := io.cpu.req.addr
    io.uart.get.req.wdata := io.cpu.req.wdata
    io.uart.get.req.wen   := io.cpu.req.wen   && sel_uart.get
    io.uart.get.req.ren   := io.cpu.req.ren   && sel_uart.get
    io.uart.get.req.mask  := io.cpu.req.mask
  }

  // Timer
  if (config.soc.enableTimer) {
    io.timer.get.req.addr  := io.cpu.req.addr
    io.timer.get.req.wdata := io.cpu.req.wdata
    io.timer.get.req.wen   := io.cpu.req.wen   && sel_timer.get
    io.timer.get.req.ren   := io.cpu.req.ren   && sel_timer.get
    io.timer.get.req.mask  := io.cpu.req.mask
  }

  // GPIO
  if (config.soc.enableGPIO) {
    io.gpio.get.req.addr  := io.cpu.req.addr
    io.gpio.get.req.wdata := io.cpu.req.wdata
    io.gpio.get.req.wen   := io.cpu.req.wen   && sel_gpio.get
    io.gpio.get.req.ren   := io.cpu.req.ren   && sel_gpio.get
    io.gpio.get.req.mask  := io.cpu.req.mask
  }

  // Response mux back to CPU
  val sel_uart_valid  = if (config.soc.enableUART)  sel_uart.get  else false.B
  val sel_timer_valid = if (config.soc.enableTimer) sel_timer.get else false.B
  val sel_gpio_valid  = if (config.soc.enableGPIO)  sel_gpio.get  else false.B

  io.cpu.resp.rdata := MuxCase(io.ram.resp.rdata, Seq(
    sel_uart_valid  -> (if (config.soc.enableUART)  io.uart.get.resp.rdata  else 0.U),
    sel_timer_valid -> (if (config.soc.enableTimer) io.timer.get.resp.rdata else 0.U),
    sel_gpio_valid  -> (if (config.soc.enableGPIO)  io.gpio.get.resp.rdata  else 0.U)
  ))

  io.cpu.resp.valid := io.ram.resp.valid ||
                       (if (config.soc.enableUART)  io.uart.get.resp.valid  else false.B) ||
                       (if (config.soc.enableTimer) io.timer.get.resp.valid else false.B) ||
                       (if (config.soc.enableGPIO)  io.gpio.get.resp.valid  else false.B)

  io.cpu.resp.error := false.B
}

// ============================================================
// CoreBusIF - CPU to SoC Bus Bridge
// ============================================================

class CoreBusIF(config: NeoRV32Config) extends Module {
  val io = IO(new Bundle {
    val core_dmem = new SimpleBusSlave()  // From CPU memory stage
    val core_imem = new SimpleBusSlave()  // From CPU fetch stage
    val soc = new SimpleBusMaster()
  })

  // Simple arbiter: instruction has priority
  val imem_active = io.core_imem.req.ren || io.core_imem.req.wen
  val dmem_active = io.core_dmem.req.ren || io.core_dmem.req.wen

  val use_imem = imem_active

  // Connect to SoC bus (imem has priority)
  io.soc.req.addr  := Mux(use_imem, io.core_imem.req.addr,  io.core_dmem.req.addr)
  io.soc.req.wdata := Mux(use_imem, io.core_imem.req.wdata, io.core_dmem.req.wdata)
  io.soc.req.wen   := Mux(use_imem, io.core_imem.req.wen,   io.core_dmem.req.wen)
  io.soc.req.ren   := Mux(use_imem, io.core_imem.req.ren,   io.core_dmem.req.ren)
  io.soc.req.mask  := Mux(use_imem, io.core_imem.req.mask,  io.core_dmem.req.mask)

  // Responses
  io.core_imem.resp.rdata := io.soc.resp.rdata
  io.core_imem.resp.valid := io.soc.resp.valid && use_imem
  io.core_imem.resp.error := io.soc.resp.error

  io.core_dmem.resp.rdata := io.soc.resp.rdata
  io.core_dmem.resp.valid := io.soc.resp.valid && !use_imem
  io.core_dmem.resp.error := io.soc.resp.error
}
