package rv32.core.util

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig

// ============================================================
// MicroOp - Universal Bundle passed between pipeline stages
// Contains all signals needed for instruction execution
// ============================================================

class MicroOp(implicit conf: CoreConfig) extends Bundle {
  // Valid bit
  val valid = Bool()

  // Program counter
  val pc = UInt(conf.xlen.W)

  // Instruction
  val inst = UInt(32.W)

  // Register indices
  val rs1 = UInt(5.W)
  val rs2 = UInt(5.W)
  val rd  = UInt(5.W)

  // Control signals
  val ctrl = new ControlSignals()

  // Data signals
  val rs1_data = UInt(conf.xlen.W)
  val rs2_data = UInt(conf.xlen.W)
  val imm = UInt(conf.xlen.W)

  // ALU/FU results
  val alu_result = UInt(conf.xlen.W)
  val mem_data = UInt(conf.xlen.W)

  // Branch/jump target
  val target = UInt(conf.xlen.W)
  val br_taken = Bool()

  // Memory info
  val mem_addr = UInt(conf.xlen.W)
  val mem_wdata = UInt(conf.xlen.W)

  // Writeback data
  val wb_data = UInt(conf.xlen.W)
}

// ============================================================
// Control Signals - Decoded instruction control
// ============================================================

class ControlSignals extends Bundle {
  // Valid bit (from decode)
  val valid = Bool()

  // PC select
  val pc_sel = UInt(2.W)

  // Operand selects
  val op1_sel = UInt(2.W)
  val op2_sel = UInt(3.W)

  // Immediate type
  val imm_sel = UInt(3.W)

  // ALU operation
  val alu_op = UInt(4.W)

  // Functional unit
  val fu_sel = UInt(2.W)

  // Branch type
  val br_type = UInt(3.W)

  // Writeback
  val wb_sel = UInt(2.W)
  val rf_wen = Bool()

  // Memory
  val mem_fcn = UInt(3.W)
  val mem_typ = UInt(3.W)

  // M-extension specific
  val muldiv_op = UInt(4.W)
}

// ============================================================
// Instruction to MicroOp conversion
// ============================================================

object MicroOp {
  def apply(inst: UInt, pc: UInt, valid: Bool = true.B)(implicit conf: CoreConfig): MicroOp = {
    val uop = Wire(new MicroOp())
    uop := DontCare
    uop.valid := valid
    uop.pc := pc
    uop.inst := inst
    uop.rs1 := inst(19, 15)
    uop.rs2 := inst(24, 20)
    uop.rd := inst(11, 7)
    uop
  }

  def bubble(implicit conf: CoreConfig): MicroOp = {
    val uop = Wire(new MicroOp())
    uop := DontCare
    uop.valid := false.B
    uop.ctrl.rf_wen := false.B
    uop.inst := Constants.BUBBLE
    uop
  }
}
