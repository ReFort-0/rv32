package rv32.pipeline.stage

import chisel3._
import chisel3.util._
import rv32.config.CpuConfig
import rv32.types._
import rv32.components._

/**
 * 译码阶段 (Decode Stage)
 * 负责指令译码,生成控制信号,读取寄存器
 */
class Decode(config: CpuConfig = CpuConfig()) extends Module {
  val io = IO(new Bundle {
    val instruction   = Input(UInt(config.instWidth.W))
    val pcIn          = Input(UInt(config.addrWidth.W))
    val regWriteData  = Input(UInt(32.W))
    val regWriteEn    = Input(Bool())
    val rd            = Input(UInt(5.W))
    val pcNext        = Output(UInt(config.addrWidth.W))
    val regData1      = Output(UInt(32.W))
    val regData2      = Output(UInt(32.W))
    val immOut        = Output(SInt(32.W))
    val ctrlSignals   = Output(new ControlSignals)
    val aluOp         = Output(AluOp())
    val branchTaken   = Output(Bool())
    val pcBranch      = Output(UInt(config.addrWidth.W))
    val isJump        = Output(Bool())
    val pcJump        = Output(UInt(config.addrWidth.W))
  })

  val controlUnit = Module(new ControlUnit)
  val immGen = Module(new ImmGen)
  val regFile = Module(new CoreReg(config))

  // 指令字段提取
  val opcode = io.instruction(6, 0)
  val funct3 = io.instruction(14, 12)
  val funct7 = io.instruction(31, 25)

  // 控制单元
  controlUnit.io.opcode := opcode
  controlUnit.io.funct3 := funct3
  controlUnit.io.funct7 := funct7
  io.ctrlSignals := controlUnit.io.ctrlSignals
  io.aluOp := controlUnit.io.aluOp

  // 立即数生成
  immGen.io.instruction := io.instruction
  io.immOut := immGen.io.immediate

  // 寄存器读
  regFile.io.rs1 := io.instruction(19, 15)
  regFile.io.rs2 := io.instruction(24, 20)
  regFile.io.rd := io.rd
  regFile.io.regWrite := io.regWriteEn
  regFile.io.writeData := io.regWriteData

  io.regData1 := regFile.io.rd1
  io.regData2 := regFile.io.rd2

  // 分支计算
  val pcPlus4 = io.pcIn + 4.U
  io.pcBranch := io.pcIn + io.immOut.asUInt
  io.pcJump := Mux(opcode === Opcode.JALR, 
                   (regFile.io.rd1.asUInt + io.immOut.asUInt) & ~1.U, 
                   io.pcIn + io.immOut.asUInt)

  // 分支判断
  val condEq = (regFile.io.rd1 === regFile.io.rd2)
  val condNeq = !condEq
  val condLt = (regFile.io.rd1.asSInt < regFile.io.rd2.asSInt)
  val condGe = !condLt
  val condLtu = (regFile.io.rd1 < regFile.io.rd2)
  val condGeu = !condLtu

  io.branchTaken := MuxCase(false.B, Seq(
    (funct3 === "b000".U && condEq) -> true.B,
    (funct3 === "b001".U && condNeq) -> true.B,
    (funct3 === "b100".U && condLt) -> true.B,
    (funct3 === "b101".U && condGe) -> true.B,
    (funct3 === "b110".U && condLtu) -> true.B,
    (funct3 === "b111".U && condGeu) -> true.B
  ))

  io.isJump := (opcode === Opcode.JAL) || (opcode === Opcode.JALR)
  io.pcJump := Mux(opcode === Opcode.JALR, 
                   (regFile.io.rd1.asUInt + io.immOut.asUInt) & ~1.U, 
                   pcPlus4 + io.immOut.asUInt)

  io.pcNext := pcPlus4
}
