package rv32.core.functionalunit

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig

// ============================================================
// MulUnit - Multiplication functional unit (M extension)
// Supports: MUL, MULH, MULHSU, MULHU
// ============================================================

class MulUnitIO(implicit conf: CoreConfig) extends Bundle {
  val op = Input(UInt(4.W))
  val a  = Input(UInt(conf.xlen.W))
  val b  = Input(UInt(conf.xlen.W))
  val in_valid = Input(Bool())
  val result = Output(UInt(conf.xlen.W))
  val out_valid = Output(Bool())
}

class MulUnit(implicit conf: CoreConfig) extends Module {
  val io = IO(new MulUnitIO())

  val s_idle :: s_compute :: Nil = Enum(2)
  val state = RegInit(s_idle)

  val result_reg = Reg(UInt((conf.xlen * 2).W))
  val result_valid = RegInit(false.B)

  // op(1) and op(0) determine the type
  // op = 0: MUL   (signed * signed, low bits)
  // op = 1: MULH  (signed * signed, high bits)
  // op = 2: MULHSU (signed * unsigned, high bits)
  // op = 3: MULHU  (unsigned * unsigned, high bits)
  val is_mulh = io.op(1)
  val is_unsigned_second = io.op(0)

  // Properly type the second operand
  val b_sext = io.b.asSInt
  val b_zext = io.b.asUInt

  // Compute with proper types
  val product_signed_signed = io.a.asSInt * b_sext
  val product_signed_unsigned = io.a.asSInt * b_zext
  val product_unsigned_unsigned = io.a.asUInt * b_zext

  // Select the right multiplication based on op bits
  val product = Mux(is_unsigned_second,
    Mux(is_mulh, product_signed_unsigned, io.a.asUInt * b_zext).asUInt,
    product_signed_signed.asUInt
  )

  switch(state) {
    is(s_idle) {
      result_valid := false.B
      when(io.in_valid) {
        result_reg := product
        state := s_compute
      }
    }
    is(s_compute) {
      result_valid := true.B
      state := s_idle
    }
  }

  // MUL = low bits, MULH* = high bits
  io.result := Mux(is_mulh, result_reg(conf.xlen * 2 - 1, conf.xlen), result_reg(conf.xlen - 1, 0))
  io.out_valid := result_valid
}