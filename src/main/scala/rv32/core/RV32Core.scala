package rv32.core

import chisel3._
import chisel3.util._
import rv32.config._
import rv32.pipeline.stage._

/**
 * RV32 CPU顶层模块
 * 根据配置参数生成不同实现的处理器
 */
class RV32Core(config: CpuConfig = CpuConfig()) extends Module {
  val io = IO(new Bundle {
    val imemAddr    = Output(UInt(config.addrWidth.W))
    val imemReadData = Input(UInt(config.dataWidth.W))
    val dmemAddr    = Output(UInt(config.addrWidth.W))
    val dmemWriteData = Output(UInt(config.dataWidth.W))
    val dmemReadData  = Input(UInt(config.dataWidth.W))
    val dmemWrite   = Output(Bool())
    val dmemRead    = Output(Bool())
  })

  // 根据配置选择不同的CPU实现
  config.cpuType match {
    case CpuType.SingleCycle =>
      val cpu = Module(new SingleCycleCpu(config))
      io <> cpu.io
      
    case CpuType.ThreeStage =>
      // TODO: 三级流水线实现
      assert(false.B, "ThreeStage pipeline not implemented yet")
      
    case CpuType.FiveStage =>
      // TODO: 五级流水线实现
      assert(false.B, "FiveStage pipeline not implemented yet")
  }
}
