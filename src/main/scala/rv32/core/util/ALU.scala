package rv32.core.util

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig

// ============================================================
// ALU - Arithmetic Logic Unit
// Supports: ADD, SUB, AND, OR, XOR, SLT, SLTU, SLL, SRL, SRA
// Uses Constants.ALU_* for operation codes
// ============================================================

class ALUIO(implicit config: CoreConfig) extends Bundle {
  val op = Input(UInt(4.W))
  val in1 = Input(UInt(config.xlen.W))
  val in2 = Input(UInt(config.xlen.W))
  val out = Output(UInt(config.xlen.W))
}

class ALU(implicit config: CoreConfig) extends Module {
  import Constants._

  val io = IO(new ALUIO)

  val shamt = io.in2(4, 0)

  val add_sub_result = io.in1 + Mux(io.op === ALU_SUB, -io.in2, io.in2)

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
    (io.op === ALU_ADD)  -> add_sub_result,
    (io.op === ALU_SUB)  -> add_sub_result,
    (io.op === ALU_AND)  -> and_result,
    (io.op === ALU_OR)   -> or_result,
    (io.op === ALU_XOR)  -> xor_result,
    (io.op === ALU_SLT)  -> slt_result,
    (io.op === ALU_SLTU) -> sltu_result,
    (io.op === ALU_SLL)  -> sll_result,
    (io.op === ALU_SRL)  -> srl_result,
    (io.op === ALU_SRA)  -> sra_result,
    (io.op === ALU_COPY1) -> io.in1
  ))
}
