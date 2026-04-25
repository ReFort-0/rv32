package rv32.core.functionalunit

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig
import rv32.core.util.ALU

// ============================================================
// MulDivUnit - Combined M extension unit (MUL/DIV/REM)
// Supports: MUL, MULH, MULHSU, MULHU, DIV, DIVU, REM, REMU
// ============================================================

class MulDivUnitIO(implicit conf: CoreConfig) extends Bundle {
  val op = Input(UInt(4.W))
  val a  = Input(UInt(conf.xlen.W))
  val b  = Input(UInt(conf.xlen.W))
  val in_valid = Input(Bool())
  val result = Output(UInt(conf.xlen.W))
  val out_valid = Output(Bool())
  val busy = Output(Bool())
}

class MulDivUnit(implicit conf: CoreConfig) extends Module {
  val io = IO(new MulDivUnitIO())

  val s_idle :: s_compute :: Nil = Enum(2)
  val state = RegInit(s_idle)

  val result_reg = Reg(UInt(conf.xlen.W))
  val result_valid = RegInit(false.B)

  // op(2) = 0 for mul, 1 for div
  val is_mul = !io.op(2)

  // For multiplication
  val is_mulh = io.op(1)
  val is_unsigned_second = io.op(0)

  val product_s_s = (io.a.asSInt * io.b.asSInt).asUInt
  val product_s_u = (io.a.asSInt * io.b.asUInt).asUInt
  val product_u_u = (io.a.asUInt * io.b.asUInt).asUInt

  val product = Mux(is_unsigned_second,
    Mux(is_mulh, product_s_u, product_u_u),
    product_s_s
  )

  // Division
  val div_zero = io.b === 0.U
  val a_abs = Mux(io.a(conf.xlen - 1) && io.op(2), (~io.a + 1.U).asUInt, io.a)
  val b_abs = Mux(io.b(conf.xlen - 1) && io.op(2), (~io.b + 1.U).asUInt, io.b)

  val quotient = Mux(div_zero, (~0.U).asUInt, a_abs / b_abs)
  val remainder = Mux(div_zero, io.a, a_abs % b_abs)

  // Sign correction
  val a_neg = io.a(conf.xlen - 1) && io.op(2)
  val b_neg = io.b(conf.xlen - 1) && io.op(2)
  val div_sign = (a_neg ^ b_neg) && !div_zero
  val rem_sign = a_neg && !div_zero

  val signed_quot = Mux(div_sign, (~quotient + 1.U), quotient)
  val signed_rem = Mux(rem_sign, (~remainder + 1.U).asUInt, remainder)

  switch(state) {
    is(s_idle) {
      result_valid := false.B
      when(io.in_valid) {
        state := s_compute
      }
    }
    is(s_compute) {
      result_valid := true.B
      state := s_idle
    }
  }

  // Combine results
  val mul_result = Mux(is_mulh, product(conf.xlen * 2 - 1, conf.xlen), product(conf.xlen - 1, 0))
  val div_result = Mux(io.op(0), signed_rem, signed_quot)

  result_reg := Mux(is_mul, mul_result, div_result)
  io.result := result_reg
  io.out_valid := result_valid
  io.busy := state === s_compute
}

// ============================================================
// ExuBlock - Execution Unit Block (ALU + MulDiv)
// ============================================================

class ExuBlock(implicit conf: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val alu_op  = Input(UInt(4.W))
    val muldiv_op = Input(UInt(4.W))
    val in1 = Input(UInt(conf.xlen.W))
    val in2 = Input(UInt(conf.xlen.W))
    val use_muldiv = Input(Bool())
    val out = Output(UInt(conf.xlen.W))
    val busy = Output(Bool())  // MulDivUnit busy signal passthrough
  })

  val alu = Module(new ALU())
  alu.io.op := io.alu_op
  alu.io.in1 := io.in1
  alu.io.in2 := io.in2

  val muldiv = Module(new MulDivUnit())
  muldiv.io.op := io.muldiv_op
  muldiv.io.a := io.in1
  muldiv.io.b := io.in2
  muldiv.io.in_valid := io.use_muldiv

  io.out := Mux(io.use_muldiv, muldiv.io.result, alu.io.out)
  io.busy := muldiv.io.busy
}
