package rv32.components

import chisel3._
import chisel3.util._
import rv32.types._

/**
 * 立即数生成器
 */
class ImmGen extends Module {
  val io = IO(new Bundle {
    val instruction = Input(UInt(32.W))
    val immediate   = Output(SInt(32.W))
  })

  val opcode = io.instruction(6, 0)

  val immI = Cat(io.instruction(31, 20)).asSInt
  val immS = Cat(io.instruction(31, 25), io.instruction(11, 7)).asSInt
  val immB = Cat(io.instruction(31), io.instruction(7), io.instruction(30, 25), io.instruction(11, 8), 0.U(1.W)).asSInt
  val immU = Cat(io.instruction(31, 12), 0.U(12.W)).asSInt
  val immJ = Cat(io.instruction(31), io.instruction(19, 12), io.instruction(20), io.instruction(30, 21), 0.U(1.W)).asSInt

  io.immediate := MuxCase(0.S, Seq(
    (opcode === Opcode.LOAD)   -> immI,
    (opcode === Opcode.ALUI)   -> immI,
    (opcode === Opcode.JALR)   -> immI,
    (opcode === Opcode.STORE)  -> immS,
    (opcode === Opcode.BRANCH) -> immB,
    (opcode === Opcode.LUI)    -> immU,
    (opcode === Opcode.AUIPC)  -> immU,
    (opcode === Opcode.JAL)    -> immJ
  ))
}
