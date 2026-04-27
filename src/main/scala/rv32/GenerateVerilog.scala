package rv32

import chisel3._
import circt.stage.ChiselStage
import rv32.configs.{Configs, NeoRV32Config}
import rv32.soc.NeoRV32SoC

// ============================================================
// Verilog generation script for all predefined configurations
// ============================================================

object GenerateVerilog extends App {

  val configs = Seq(
    // ("E1T", Configs.E1T),
    // ("IM3UG", Configs.IM3UG),
    // ("I5UT", Configs.I5UT),
    ("Default", Configs.Default)
  )

  configs.foreach { case (name, config) =>
    println(s"Generating Verilog for $name configuration...")
    println(s"  Pipeline: ${config.core.pipelineStages} stages")
    println(s"  RV32E: ${config.core.useRV32E}, M-extension: ${config.core.useM}")
    println(s"  Peripherals: UART=${config.soc.enableUART}, Timer=${config.soc.enableTimer}, GPIO=${config.soc.enableGPIO}")

    implicit val conf: NeoRV32Config = config

    // Generate Verilog with full hierarchy
    ChiselStage.emitSystemVerilogFile(
      new NeoRV32SoC,
      Array("--target-dir", s"verilog/$name", "--split-verilog"),
      firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
    )

    println(s"  Generated: verilog/$name/")
    println()
  }

  println("All configurations generated successfully!")
}
