package rv32.core.util

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig

// ============================================================
// Immediate Generator - generates I/S/B/U/J-type immediates
// ============================================================

class ImmGenIO(implicit conf: CoreConfig) extends Bundle {
  val inst = Input(UInt(32.W))
  val sel  = Input(UInt(3.W))
  val out  = Output(UInt(conf.xlen.W))
}

class ImmGen(implicit conf: CoreConfig) extends Module {
  val io = IO(new ImmGenIO())

  val imm_i = Cat(Fill(20, io.inst(31)), io.inst(31, 20))
  val imm_s = Cat(Fill(20, io.inst(31)), io.inst(31, 25), io.inst(11, 7))
  val imm_b = Cat(Fill(19, io.inst(31)), io.inst(31), io.inst(7), io.inst(30, 25), io.inst(11, 8), 0.U(1.W))
  val imm_u = Cat(io.inst(31, 12), Fill(12, 0.U))
  val imm_j = Cat(Fill(11, io.inst(31)), io.inst(31), io.inst(19, 12), io.inst(20), io.inst(30, 21), 0.U(1.W))
  val imm_z = Cat(Fill(27, 0.U), io.inst(19, 15))  // For CSR instructions (zero-extended)

  io.out := MuxCase(0.U, Seq(
    (io.sel === Constants.IMM_I) -> imm_i,
    (io.sel === Constants.IMM_S) -> imm_s,
    (io.sel === Constants.IMM_B) -> imm_b,
    (io.sel === Constants.IMM_U) -> imm_u,
    (io.sel === Constants.IMM_J) -> imm_j,
    (io.sel === Constants.IMM_Z) -> imm_z
  ))
}
