package rv32.core.functionalunit

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig

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

  // op bits: 0=MUL, 1=MULH, 2=MULHSU, 3=MULHU
  val is_mulh = io.op(1)
  val is_unsigned_second = io.op(0)

  val b_sext = io.b.asSInt
  val b_zext = io.b.asUInt

  val product_signed_signed = io.a.asSInt * b_sext
  val product_signed_unsigned = io.a.asSInt * b_zext
  val product_unsigned_unsigned = io.a.asUInt * b_zext

  val product = Mux(is_unsigned_second,
    Mux(is_mulh, product_unsigned_unsigned.asUInt, product_signed_unsigned.asUInt),
    Mux(is_mulh, product_signed_signed.asUInt, product_signed_signed.asUInt)
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

  io.result := Mux(is_mulh, result_reg(conf.xlen * 2 - 1, conf.xlen), result_reg(conf.xlen - 1, 0))
  io.out_valid := result_valid
}
