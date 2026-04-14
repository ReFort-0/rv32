package rv32.pipeline.stage

import chisel3._
import chisel3.util._
import rv32.config.CpuConfig

/**
 * 写回阶段 (Writeback Stage)
 * 负责将运算结果或内存数据写回寄存器
 */
class Writeback(config: CpuConfig = CpuConfig()) extends Module {
  val io = IO(new Bundle {
    val aluResult   = Input(UInt(32.W))
    val memOutData  = Input(UInt(32.W))
    val pcPlus4     = Input(UInt(32.W))
    val memToReg    = Input(Bool())
    val isJump      = Input(Bool())
    val regWrite    = Input(Bool())
    val regWriteData = Output(UInt(32.W))
    val rd          = Output(UInt(5.W))
    val regWriteEn  = Output(Bool())
  })

  val writeData = Mux(io.memToReg, io.memOutData, 
                      Mux(io.isJump, io.pcPlus4, io.aluResult))

  io.regWriteData := writeData
  io.regWriteEn := io.regWrite
  io.rd := 0.U  // rd由Decode阶段传递
}
