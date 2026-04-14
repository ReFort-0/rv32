package rv32

import chisel3._
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage

class RV32Top extends Module {
  val io = IO(new Bundle {

  })
}

/**
 * Generate Verilog sources
 */
object RV32Top extends App {
  ChiselStage.emitSystemVerilogFile(
    new RV32Top,
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info", "-default-layer-specialization=enable")
  )
}
