package rv32.configs

import chisel3._
import chisel3.util._

// ============================================================
// Parameter Keys - Using simple case class based config
// Pattern inspired by rocket-chip but simplified for this project
// ============================================================

case class CoreConfig(
  xlen: Int = 32,
  useRV32E: Boolean = false,
  useM: Boolean = false,
  pipelineStages: Int = 3
) {
  require(pipelineStages == 1 || pipelineStages == 3 || pipelineStages == 5,
    "Pipeline stages must be 1, 3, or 5")
  require(xlen == 32, "Only RV32 is supported")

  def numRegs: Int = if (useRV32E) 16 else 32
  // def regAddrWidth: Int = if (useRV32E) 4 else 5
  //(RV32E和RV32I的GPR都是5位地址)
}

case class SoCConfig(
  enableUART: Boolean = false,
  enableTimer: Boolean = false,
  enableGPIO: Boolean = false,
  onChipRAMSize: Int = 4 * 1024
)

// Combined configuration
case class NeoRV32Config(core: CoreConfig, soc: SoCConfig)

// ============================================================
// Predefined Configurations
// ============================================================

object Configs {
  // E1T: RV32E + 1-stage + Timer
  val E1T: NeoRV32Config = NeoRV32Config(
    core = CoreConfig(useRV32E = true, pipelineStages = 1),
    soc = SoCConfig(enableTimer = true)
  )

  // IM3UG: RV32IM + 3-stage + UART + GPIO
  val IM3UG: NeoRV32Config = NeoRV32Config(
    core = CoreConfig(useM = true, pipelineStages = 3),
    soc = SoCConfig(enableUART = true, enableGPIO = true)
  )

  // I5UT: RV32I + 5-stage + UART + Timer
  val I5UT: NeoRV32Config = NeoRV32Config(
    core = CoreConfig(pipelineStages = 5),
    soc = SoCConfig(enableUART = true, enableTimer = true)
  )

  // Default config
  val Default: NeoRV32Config = NeoRV32Config(CoreConfig(), SoCConfig())
}
