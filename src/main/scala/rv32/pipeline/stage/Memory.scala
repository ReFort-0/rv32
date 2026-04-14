package rv32.pipeline.stage

import chisel3._
import chisel3.util._
import rv32.config.CpuConfig

/**
 * 访存阶段 (Memory Stage)
 * 负责数据内存访问
 */
class Memory(config: CpuConfig = CpuConfig()) extends Module {
  val io = IO(new Bundle {
    val aluResult     = Input(UInt(config.addrWidth.W))
    val regData2      = Input(UInt(32.W))
    val memRead       = Input(Bool())
    val memWrite      = Input(Bool())
    val dmemReadData  = Input(UInt(config.dataWidth.W))
    val dmemAddr      = Output(UInt(config.addrWidth.W))
    val dmemWriteData = Output(UInt(config.dataWidth.W))
    val dmemRead      = Output(Bool())
    val dmemWrite     = Output(Bool())
    val memOutData    = Output(UInt(32.W))
  })

  io.dmemAddr := io.aluResult
  io.dmemWriteData := io.regData2
  io.dmemRead := io.memRead
  io.dmemWrite := io.memWrite
  
  io.memOutData := Mux(io.memRead, io.dmemReadData, 0.U)
}
