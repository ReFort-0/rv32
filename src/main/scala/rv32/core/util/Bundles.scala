package rv32.core.util

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig

// ============================================================
// Memory Interface Bundles
// ============================================================

class SimpleMemReq(implicit config: CoreConfig) extends Bundle {
  val addr  = UInt(32.W)
  val wdata = UInt(config.xlen.W)
  val wen   = Bool()
  val mask  = UInt(4.W)  // Byte mask for writes
  val valid = Bool()
}

class SimpleMemResp(implicit config: CoreConfig) extends Bundle {
  val rdata = UInt(config.xlen.W)
  val valid = Bool()
}

// ============================================================
// Pipeline Register Bundles
// ============================================================

// IF/ID stage bundle - Instruction Fetch to Decode
class IFIDBundle(implicit config: CoreConfig) extends Bundle {
  val pc      = UInt(32.W)
  val inst    = UInt(32.W)
  val valid   = Bool()
}

// ID/EX stage bundle - Decode to Execute
class IDEXBundle(implicit config: CoreConfig) extends Bundle {
  val pc        = UInt(32.W)
  val inst      = UInt(32.W)
  val valid     = Bool()

  // Register addresses
  val rs1_addr  = UInt(5.W)
  val rs2_addr  = UInt(5.W)
  val rd_addr   = UInt(5.W)

  // Register data
  val rs1_data  = UInt(config.xlen.W)
  val rs2_data  = UInt(config.xlen.W)
  val imm       = UInt(config.xlen.W)

  // Control signals
  val op1_sel   = UInt(2.W)
  val op2_sel   = UInt(3.W)
  val alu_op    = UInt(4.W)
  val fu_sel    = UInt(2.W)
  val muldiv_op = UInt(3.W)
  val branch_type = UInt(3.W)
  val mem_en    = Bool()
  val mem_rw    = Bool()
  val mem_type  = UInt(3.W)
  val wb_sel    = UInt(2.W)
  val reg_write = Bool()
}

// EX/MEM stage bundle - Execute to Memory
class EXMEMBundle(implicit config: CoreConfig) extends Bundle {
  val pc        = UInt(32.W)
  val inst      = UInt(32.W)
  val valid     = Bool()

  val rd_addr   = UInt(5.W)
  val alu_result = UInt(config.xlen.W)
  val rs2_data  = UInt(config.xlen.W)

  // Control signals
  val fu_sel    = UInt(2.W)  // Functional unit select (needed for MulDiv stall detection)
  val mem_en    = Bool()
  val mem_rw    = Bool()
  val mem_type  = UInt(3.W)
  val wb_sel    = UInt(2.W)
  val reg_write = Bool()
}

// MEM/WB stage bundle - Memory to Writeback
class MEMWBBundle(implicit config: CoreConfig) extends Bundle {
  val pc        = UInt(32.W)
  val inst      = UInt(32.W)
  val valid     = Bool()

  val rd_addr   = UInt(5.W)
  val alu_result = UInt(config.xlen.W)
  val mem_rdata = UInt(config.xlen.W)

  // Control signals
  val wb_sel    = UInt(2.W)
  val reg_write = Bool()
}

// ============================================================
// Control Signal Bundle (for decoder output)
// ============================================================

class ControlSignals extends Bundle {
  val valid       = Bool()
  val pc_sel      = UInt(2.W)
  val op1_sel     = UInt(2.W)
  val op2_sel     = UInt(3.W)
  val alu_op      = UInt(4.W)
  val fu_sel      = UInt(2.W)
  val muldiv_op   = UInt(3.W)
  val branch_type = UInt(3.W)
  val mem_en      = Bool()
  val mem_rw      = Bool()
  val mem_type    = UInt(3.W)
  val wb_sel      = UInt(2.W)
  val reg_write   = Bool()
  val imm_type    = UInt(3.W)
}
