package rv32.core.util

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig

// ============================================================
// Register File - x0-x31 (or x0-x15 for RV32E)
// Chisel uses Mem for inference of regfile
// Address width is always 5 bits (RV32E only uses lower 16 addresses)
// ============================================================

class RegisterFileIO(implicit conf: CoreConfig) extends Bundle {
  val rs1_addr = Input(UInt(5.W))
  val rs2_addr = Input(UInt(5.W))
  val rd_addr  = Input(UInt(5.W))
  val rd_data  = Input(UInt(conf.xlen.W))
  val wen      = Input(Bool())

  val rs1_data = Output(UInt(conf.xlen.W))
  val rs2_data = Output(UInt(conf.xlen.W))
}

class RegisterFile(implicit conf: CoreConfig) extends Module {
  val io = IO(new RegisterFileIO)

  // Memory-based regfile - Chisel infers proper regfile from this
  val rf = Mem(conf.numRegs, UInt(conf.xlen.W))

  // Read ports - async read
  // x0 is always 0
  io.rs1_data := Mux(io.rs1_addr === 0.U, 0.U, rf(io.rs1_addr))
  io.rs2_data := Mux(io.rs2_addr === 0.U, 0.U, rf(io.rs2_addr))

  // Write port - positive edge, not x0
  when(io.wen && io.rd_addr =/= 0.U) {
    rf(io.rd_addr) := io.rd_data
  }
}