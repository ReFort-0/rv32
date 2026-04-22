package rv32.core.util

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig

// ============================================================
// ALU - Arithmetic Logic Unit
// Supports: ADD, SUB, AND, OR, XOR, SLT, SLTU, SLL, SRL, SRA
// ============================================================

class ALUIO(implicit conf: CoreConfig) extends Bundle {
  val op = Input(UInt(4.W))
  val in1 = Input(UInt(conf.xlen.W))
  val in2 = Input(UInt(conf.xlen.W))
  val out = Output(UInt(conf.xlen.W))
}

object ALU {
  // ALU operation codes (same as Constants.ALU_*) for convenience
  val ADD  = 0.U(4.W)
  val SUB  = 1.U(4.W)
  val AND  = 2.U(4.W)
  val OR   = 3.U(4.W)
  val XOR  = 4.U(4.W)
  val SLT  = 5.U(4.W)
  val SLTU = 6.U(4.W)
  val SLL  = 7.U(4.W)
  val SRL  = 8.U(4.W)
  val SRA  = 9.U(4.W)
  val COPY1 = 10.U(4.W)
}

class ALU(implicit conf: CoreConfig) extends Module {
  val io = IO(new ALUIO())

  val shamt = io.in2(4, 0)

  val add_sub_result = io.in1 + Mux(io.op === ALU.SUB, -io.in2, io.in2)

  val xor_result = io.in1 ^ io.in2
  val or_result  = io.in1 | io.in2
  val and_result = io.in1 & io.in2

  val sll_result = io.in1 << shamt
  val srl_result = io.in1 >> shamt
  val sra_result = (io.in1.asSInt >> shamt).asUInt

  val slt_result  = (io.in1.asSInt < io.in2.asSInt).asUInt
  val sltu_result = (io.in1.asUInt < io.in2.asUInt).asUInt

  // Use MuxCase instead of MuxLookup for better compatibility
  io.out := MuxCase(io.in1, Seq(
    (io.op === ALU.ADD)  -> add_sub_result,
    (io.op === ALU.SUB)  -> add_sub_result,
    (io.op === ALU.AND)  -> and_result,
    (io.op === ALU.OR)   -> or_result,
    (io.op === ALU.XOR)  -> xor_result,
    (io.op === ALU.SLT)  -> slt_result,
    (io.op === ALU.SLTU) -> sltu_result,
    (io.op === ALU.SLL)  -> sll_result,
    (io.op === ALU.SRL)  -> srl_result,
    (io.op === ALU.SRA)  -> sra_result,
    (io.op === ALU.COPY1) -> io.in1
  ))
}
