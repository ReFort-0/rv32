package rv32.core

import chisel3._
import chisel3.util._
import rv32.config.CpuConfig
import rv32.types._
import rv32.pipeline.stage._

/**
 * 单周期RV32I CPU实现
 * 使用Fetch/Decode/Execute/Memory/Writeback阶段模块
 */
class SingleCycleCpu(config: CpuConfig = CpuConfig()) extends Module {
  val io = IO(new Bundle {
    val imemAddr      = Output(UInt(config.addrWidth.W))
    val imemReadData  = Input(UInt(config.dataWidth.W))
    val dmemAddr      = Output(UInt(config.addrWidth.W))
    val dmemWriteData = Output(UInt(config.dataWidth.W))
    val dmemReadData  = Input(UInt(config.dataWidth.W))
    val dmemWrite     = Output(Bool())
    val dmemRead      = Output(Bool())
  })

  // PC寄存器
  val pc = RegInit(0.U(config.addrWidth.W))

  // 实例化各阶段
  val fetchStage = Module(new Fetch(config))
  val decodeStage = Module(new Decode(config))
  val executeStage = Module(new Execute(config))
  val memoryStage = Module(new Memory(config))
  val writebackStage = Module(new Writeback(config))

  // === Fetch阶段 ===
  fetchStage.io.pcIn := pc
  fetchStage.io.imemData := io.imemReadData
  fetchStage.io.branchTaken := decodeStage.io.branchTaken
  fetchStage.io.pcBranch := decodeStage.io.pcBranch
  fetchStage.io.pcJump := decodeStage.io.pcJump
  fetchStage.io.stall := false.B
  pc := fetchStage.io.pcNext

  // === Decode阶段 ===
  decodeStage.io.instruction := fetchStage.io.instOut
  decodeStage.io.pcIn := pc
  decodeStage.io.regWriteData := writebackStage.io.regWriteData
  decodeStage.io.regWriteEn := writebackStage.io.regWriteEn
  decodeStage.io.rd := 0.U  // 需要从Writeback传递

  // === Execute阶段 ===
  executeStage.io.regData1 := decodeStage.io.regData1
  executeStage.io.regData2 := decodeStage.io.regData2
  executeStage.io.immIn := decodeStage.io.immOut
  executeStage.io.aluSrc := decodeStage.io.ctrlSignals.aluSrc
  executeStage.io.aluOp := decodeStage.io.aluOp
  executeStage.io.pcIn := pc
  executeStage.io.pcBranch := decodeStage.io.pcBranch
  executeStage.io.isJump := decodeStage.io.isJump

  // === Memory阶段 ===
  memoryStage.io.aluResult := executeStage.io.aluResult
  memoryStage.io.regData2 := decodeStage.io.regData2
  memoryStage.io.memRead := decodeStage.io.ctrlSignals.memRead
  memoryStage.io.memWrite := decodeStage.io.ctrlSignals.memWrite
  memoryStage.io.dmemReadData := io.dmemReadData

  // === Writeback阶段 ===
  writebackStage.io.aluResult := executeStage.io.aluResult
  writebackStage.io.memOutData := memoryStage.io.memOutData
  writebackStage.io.pcPlus4 := pc + 4.U
  writebackStage.io.memToReg := decodeStage.io.ctrlSignals.memToReg
  writebackStage.io.isJump := decodeStage.io.isJump
  writebackStage.io.regWrite := decodeStage.io.ctrlSignals.regWrite

  // === 内存接口 ===
  io.imemAddr := pc
  io.dmemAddr := memoryStage.io.dmemAddr
  io.dmemWriteData := memoryStage.io.dmemWriteData
  io.dmemWrite := memoryStage.io.dmemWrite
  io.dmemRead := memoryStage.io.dmemRead
}
