package rv32.core.functionalunit

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig

// ============================================================
// DivUnit - Division functional unit (M extension)
// Supports: DIV, DIVU, REM, REMU
// Latency: up to 32 cycles for division
// ============================================================

class DivUnitIO(implicit conf: CoreConfig) extends Bundle {
  val op = Input(UInt(4.W))   // 4=DIV, 5=DIVU, 6=REM, 7=REMU
  val a  = Input(UInt(conf.xlen.W))
  val b  = Input(UInt(conf.xlen.W))
  val in_valid = Input(Bool())
  val result = Output(UInt(conf.xlen.W))
  val out_valid = Output(Bool())
}

class DivUnit(implicit conf: CoreConfig) extends Module {
  val io = IO(new DivUnitIO())

  val s_idle :: s_divide :: s_done :: Nil = Enum(3)
  val state = RegInit(s_idle)

  val quotient = Reg(UInt(conf.xlen.W))
  val remainder = Reg(UInt(conf.xlen.W))
  val result_valid = RegInit(false.B)
  val is_signed = Reg(Bool())

  // Decode op
  val div_op = (io.op === 4.U)
  val rem_op = (io.op === 6.U || io.op === 7.U)

  switch(state) {
    is(s_idle) {
      result_valid := false.B
      when(io.in_valid) {
        is_signed := div_op
        val a_pos = Mux(io.a(conf.xlen - 1) && div_op, (~io.a).asUInt, io.a)
        val b_pos = Mux(io.b(conf.xlen - 1) && div_op, (~io.b).asUInt, io.b)
        quotient := a_pos / b_pos
        remainder := a_pos % b_pos
        state := s_divide
      }
    }
    is(s_divide) {
      result_valid := true.B
      state := s_idle
    }
    is(s_done) {
      result_valid := true.B
      state := s_idle
    }
  }

  // Handle division by zero and sign correction
  val div_by_zero = io.b === 0.U
  val result_div = Mux(div_by_zero, (~0.U).asUInt, quotient)
  val result_rem = Mux(div_by_zero, io.a, remainder)

  // Select output based on op and apply signs
  val unsigned_result = Mux(rem_op, result_rem, result_div)

  // Sign correction for signed operations
  val a_neg = io.a(conf.xlen - 1) && div_op
  val b_neg = io.b(conf.xlen - 1) && div_op

  val final_result = Mux(div_op && a_neg && !b_neg && !div_by_zero,
    (~unsigned_result + 1.U), unsigned_result)

  io.result := Mux(rem_op,
    Mux(a_neg, (~unsigned_result + 1.U).asUInt, unsigned_result),
    final_result)
  io.out_valid := result_valid
}