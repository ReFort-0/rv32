package rv32.components

import chisel3._
import chisel3.util._
import rv32.types._

/**
 * ALU运算单元
 */
class Alu extends Module {
  val io = IO(new Bundle {
    val src1    = Input(UInt(32.W))
    val src2    = Input(UInt(32.W))
    val aluOp   = Input(AluOp())
    val result  = Output(UInt(32.W))
    val zero    = Output(Bool())
  })

  val result = Wire(UInt(32.W))

  switch(io.aluOp) {
    is(AluOp.ADD) { result := io.src1 + io.src2 }
    is(AluOp.SUB) { result := io.src1 - io.src2 }
    is(AluOp.SLL) { result := io.src1 << io.src2(4, 0) }
    is(AluOp.SLT) { result := (io.src1.asSInt < io.src2.asSInt).asUInt }
    is(AluOp.SLTU) { result := (io.src1 < io.src2).asUInt }
    is(AluOp.XOR) { result := io.src1 ^ io.src2 }
    is(AluOp.SRL) { result := io.src1 >> io.src2(4, 0) }
    is(AluOp.SRA) { result := (io.src1.asSInt >> io.src2(4, 0)).asUInt }
    is(AluOp.OR)  { result := io.src1 | io.src2 }
    is(AluOp.AND) { result := io.src1 & io.src2 }
  }

  io.result := result
  io.zero := (result === 0.U)
}
