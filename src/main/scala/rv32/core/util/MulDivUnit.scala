package rv32.core.util

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig

// ============================================================
// MulDiv Unit - Multiply and Divide Unit for RV32M Extension
// ============================================================

class MulDivUnitIO(implicit config: CoreConfig) extends Bundle {
  val op = Input(UInt(3.W))  // Operation type (MUL, MULH, etc.)
  val in1 = Input(UInt(32.W))
  val in2 = Input(UInt(32.W))
  val valid = Input(Bool())
  val out = Output(UInt(32.W))
  val ready = Output(Bool())
}

class MulDivUnit(implicit config: CoreConfig) extends Module {
  import Constants._

  val io = IO(new MulDivUnitIO)

  // State machine for division
  val sIdle :: sBusy :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)

  // Multiply operations (1-2 cycles)
  val mul_result = Wire(UInt(64.W))
  val mul_op1 = Wire(SInt(33.W))
  val mul_op2 = Wire(SInt(33.W))

  // Handle signed/unsigned multiplication
  mul_op1 := MuxCase(io.in1.asSInt, Seq(
    (io.op === MULDIV_MUL || io.op === MULDIV_MULH) -> Cat(io.in1(31), io.in1).asSInt,
    (io.op === MULDIV_MULHSU) -> Cat(io.in1(31), io.in1).asSInt,
    (io.op === MULDIV_MULHU) -> Cat(0.U(1.W), io.in1).asSInt
  ))

  mul_op2 := MuxCase(io.in2.asSInt, Seq(
    (io.op === MULDIV_MUL || io.op === MULDIV_MULH) -> Cat(io.in2(31), io.in2).asSInt,
    (io.op === MULDIV_MULHSU) -> Cat(0.U(1.W), io.in2).asSInt,
    (io.op === MULDIV_MULHU) -> Cat(0.U(1.W), io.in2).asSInt
  ))

  mul_result := (mul_op1 * mul_op2).asUInt

  // Division registers (64-bit for algorithm, but inputs/outputs are 32-bit)
  val div_quotient = RegInit(0.U(32.W))
  val div_remainder = RegInit(0.U(64.W))  // 64-bit for shift-subtract
  val div_divisor = RegInit(0.U(32.W))
  val div_counter = RegInit(0.U(6.W))
  val div_sign_q = RegInit(false.B)  // Sign for quotient
  val div_sign_r = RegInit(false.B)  // Sign for remainder
  val div_is_rem = RegInit(false.B)
  val div_result_reg = RegInit(0.U(32.W))  // Latched result

  // Division state machine
  switch(state) {
    is(sIdle) {
      when(io.valid && (io.op === MULDIV_DIV || io.op === MULDIV_DIVU ||
                        io.op === MULDIV_REM || io.op === MULDIV_REMU)) {

        div_is_rem := (io.op === MULDIV_REM || io.op === MULDIV_REMU)

        // Handle division by zero
        when(io.in2 === 0.U) {
          state := sDone
          div_sign_q := false.B
          div_sign_r := false.B

          // Latch result for division by zero
          val is_rem_op = (io.op === MULDIV_REM || io.op === MULDIV_REMU)
          div_result_reg := Mux(is_rem_op, io.in1, Fill(32, 1.U))
        }.otherwise {
          state := sBusy
          div_counter := 0.U

          // Handle signed division
          val dividend_neg = io.in1(31) && (io.op === MULDIV_DIV || io.op === MULDIV_REM)
          val divisor_neg = io.in2(31) && (io.op === MULDIV_DIV || io.op === MULDIV_REM)

          val abs_dividend = Mux(dividend_neg, (~io.in1 + 1.U), io.in1)
          val abs_divisor = Mux(divisor_neg, (~io.in2 + 1.U), io.in2)

          // Initialize: dividend in lower 32 bits of remainder, upper 32 bits = 0
          div_quotient := 0.U
          div_remainder := Cat(0.U(32.W), abs_dividend)
          div_divisor := abs_divisor

          // Sign for quotient and remainder
          div_sign_q := dividend_neg ^ divisor_neg
          div_sign_r := dividend_neg
        }
      }
    }

    is(sBusy) {
      // Shift remainder left by 1
      val shifted_rem = div_remainder << 1

      // Try to subtract divisor from upper 32 bits
      val diff = shifted_rem(63, 32) - div_divisor

      val new_rem = Wire(UInt(64.W))
      when(diff(31)) {
        // Remainder < divisor (negative), quotient bit = 0
        new_rem := shifted_rem
      }.otherwise {
        // Remainder >= divisor, quotient bit = 1
        // Update upper 32 bits with diff, set LSB to 1
        new_rem := Cat(diff, shifted_rem(31, 1), 1.U(1.W))
      }

      div_remainder := new_rem
      div_counter := div_counter + 1.U

      when(div_counter === 31.U) {
        // Latch the result after 32 iterations (counter goes 0->31)
        // Quotient is in lower 32 bits, remainder in upper 32 bits
        div_result_reg := Mux(div_is_rem,
          Mux(div_sign_r, (~new_rem(63, 32) + 1.U), new_rem(63, 32)),
          Mux(div_sign_q, (~new_rem(31, 0) + 1.U), new_rem(31, 0))
        )

        state := sDone
      }
    }

    is(sDone) {
      when(!io.valid) {
        state := sIdle
      }
    }
  }

  // Output mux
  io.out := MuxCase(0.U, Seq(
    (io.op === MULDIV_MUL) -> mul_result(31, 0),
    (io.op === MULDIV_MULH || io.op === MULDIV_MULHSU || io.op === MULDIV_MULHU) -> mul_result(63, 32),
    (io.op === MULDIV_DIV || io.op === MULDIV_DIVU ||
     io.op === MULDIV_REM || io.op === MULDIV_REMU) -> div_result_reg
  ))

  // Ready signal logic
  val is_div_op = (io.op === MULDIV_DIV || io.op === MULDIV_DIVU ||
                   io.op === MULDIV_REM || io.op === MULDIV_REMU)

  io.ready := MuxCase(false.B, Seq(
    (state === sIdle && !(io.valid && is_div_op)) -> true.B,  // Idle and no div request
    (state === sDone) -> true.B,                               // Division complete
    (!is_div_op) -> true.B                                     // Multiply operations
  ))
}
