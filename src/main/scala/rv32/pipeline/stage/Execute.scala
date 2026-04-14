package rv32.pipeline.stage

import chisel3._
import chisel3.util._
import rv32.config.CpuConfig
import rv32.types._
import rv32.components._

/**
 * 执行阶段 (Execute Stage)
 * 负责ALU运算
 */
class Execute(config: CpuConfig = CpuConfig()) extends Module {
  val io = IO(new Bundle {
    val regData1    = Input(UInt(32.W))
    val regData2    = Input(UInt(32.W))
    val immIn       = Input(SInt(32.W))
    val aluSrc      = Input(Bool())
    val aluOp       = Input(AluOp())
    val pcIn        = Input(UInt(config.addrWidth.W))
    val aluResult   = Output(UInt(32.W))
    val branchTaken = Output(Bool())
    val pcBranch    = Input(UInt(config.addrWidth.W))
    val isJump      = Input(Bool())
    val pcJump      = Output(UInt(config.addrWidth.W))
  })

  val alu = Module(new Alu)

  val aluSrc2 = Mux(io.aluSrc, io.immIn.asUInt, io.regData2)
  
  alu.io.src1 := io.regData1
  alu.io.src2 := aluSrc2
  alu.io.aluOp := io.aluOp

  io.aluResult := alu.io.result
  io.branchTaken := alu.io.zero && io.isJump === false.B
  
  val pcPlus4 = io.pcIn + 4.U
  io.pcJump := Mux(io.isJump, io.pcBranch, pcPlus4)
}
