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

  // Connect data memory
  dataRam.io.req.valid := core.io.dmem.req.valid
  dataRam.io.req.addr := core.io.dmem.req.addr
  dataRam.io.req.wdata := core.io.dmem.req.wdata
  dataRam.io.req.wen := core.io.dmem.req.wen
  dataRam.io.req.mask := core.io.dmem.req.mask
  core.io.dmem.resp.valid := dataRam.io.resp.valid
  core.io.dmem.resp.rdata := dataRam.io.resp.rdata

  // Peripheral connections (for P0, only RAM is enabled)
  // Future: Add UART, Timer, GPIO here

  // Debug outputs
  io.debug.pc := core.io.debug.pc
}
