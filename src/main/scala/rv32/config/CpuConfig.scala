package rv32.config

import chisel3._
import chisel3.util._

/**
 * CPU实现类型
 */
object CpuType extends ChiselEnum {
  val SingleCycle, ThreeStage, FiveStage = Value
}

/**
 * CPU配置参数
 */
case class CpuConfig(
  cpuType: CpuType.Type = CpuType.SingleCycle,
  addrWidth: Int = 32,
  dataWidth: Int = 32,
  resetVector: BigInt = 0x80000000L,
  isRV32E: Boolean = false,
  enableMExt: Boolean = false,
  enableFExt: Boolean = false,
  pipelineStages: Int = 1
) {
  require(addrWidth > 0)
  require(dataWidth > 0)
  require(pipelineStages == 1 || pipelineStages == 3 || pipelineStages == 5)

  def regNum: Int = if (isRV32E) 16 else 32
}
