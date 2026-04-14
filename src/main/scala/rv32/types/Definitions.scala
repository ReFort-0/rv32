package rv32.types

import chisel3._
import chisel3.util._

/**
 * RV32I 基础类型定义
 */

// ALU 操作类型枚举
object AluOp extends ChiselEnum {
  val ADD, SUB, SLL, SLT, SLTU, XOR, SRL, SRA, OR, AND = Value
}

// 内存操作类型
object MemOp extends ChiselEnum {
  val LOAD, STORE, NONE = Value
}

// 控制信号Bundle
class ControlSignals extends Bundle {
  val regWrite    = Bool()  // 寄存器写使能
  val memRead     = Bool()  // 内存读使能
  val memWrite    = Bool()  // 内存写使能
  val branch      = Bool()  // 分支标志
  val aluSrc      = Bool()  // ALU源操作数选择 (0: reg, 1: imm)
  val memToReg    = Bool()  // 写回数据来源 (0: ALU, 1: Memory)
  val jump        = Bool()  // 跳转标志
}

// 指令Bundle
class Instruction extends Bundle {
  val bits = UInt(32.W)
  
  // 指令字段提取
  def opcode:    UInt = bits(6, 0)
  def rd:        UInt = bits(11, 7)
  def funct3:    UInt = bits(14, 12)
  def rs1:       UInt = bits(19, 15)
  def rs2:       UInt = bits(24, 20)
  def funct7:    UInt = bits(31, 25)
  
  // 立即数提取
  def immI:  SInt = Cat(bits(31, 20)).asSInt
  def immS:  SInt = Cat(bits(31, 25), bits(11, 7)).asSInt
  def immB:  SInt = Cat(bits(31), bits(7), bits(30, 25), bits(11, 8), 0.U(1.W)).asSInt
  def immU:  UInt = Cat(bits(31, 12), 0.U(12.W))
  def immJ:  SInt = Cat(bits(31), bits(19, 12), bits(20), bits(30, 21), 0.U(1.W)).asSInt
}

// 操作码定义 (RV32I)
object Opcode {
  val LOAD    = "b0000011".U(7.W)
  val STORE   = "b0100011".U(7.W)
  val BRANCH  = "b1100011".U(7.W)
  val ALUI    = "b0010011".U(7.W)
  val ALU     = "b0110011".U(7.W)
  val JAL     = "b1101111".U(7.W)
  val JALR    = "b1100111".U(7.W)
  val LUI     = "b0110111".U(7.W)
  val AUIPC   = "b0010111".U(7.W)
}

// funct3 定义
object Funct3 {
  val ADD_SUB = "b000".U(3.W)
  val SLL     = "b001".U(3.W)
  val SLT     = "b010".U(3.W)
  val SLTU    = "b011".U(3.W)
  val XOR     = "b100".U(3.W)
  val SR      = "b101".U(3.W)
  val OR      = "b110".U(3.W)
  val AND     = "b111".U(3.W)
  
  // 分支条件
  val BEQ  = "b000".U(3.W)
  val BNE  = "b001".U(3.W)
  val BLT  = "b100".U(3.W)
  val BGE  = "b101".U(3.W)
  val BLTU = "b110".U(3.W)
  val BGEU = "b111".U(3.W)
}

// funct7 定义
object Funct7 {
  val ADD = "b0000000".U(7.W)
  val SUB = "b0100000".U(7.W)
  val SRL = "b0000000".U(7.W)
  val SRA = "b0100000".U(7.W)
}
