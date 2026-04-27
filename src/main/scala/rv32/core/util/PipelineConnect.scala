package rv32.core.util

import chisel3._
import chisel3.util._

// ============================================================
// Pipeline Connect - Standardized pipeline register handling
// Provides: valid/ready handshake, stall (bubble), flush support
// ============================================================

object PipelineConnect {

  // Create a pipeline register with stall and flush support
  // Returns the registered output
  def apply[T <: Data](
    valid: Bool,
    data: T,
    stall: Bool,
    flush: Bool
  ): T = {
    // When stall is true, keep the previous value
    // When flush is true, clear valid and data (valid=0, data=0)
    // Otherwise, capture new data when valid

    val dataReg = RegInit(0.U.asTypeOf(data.cloneType))

    when(flush) {
      dataReg := 0.U.asTypeOf(data.cloneType)
    }.elsewhen(!stall && valid) {
      dataReg := data
    }

    dataReg
  }

  // Create a pipeline register for valid signal
  // Returns the registered valid output
  def validReg(
    valid: Bool,
    stall: Bool,
    flush: Bool
  ): Bool = {
    val validReg = RegInit(false.B)

    when(flush) {
      validReg := false.B
    }.elsewhen(!stall) {
      validReg := valid
    }

    validReg
  }

  // Pipeline connection helper with ready signal
  // Returns (valid_out, data_out, ready_in)
  // - valid_in indicates upstream has data
  // - ready_in indicates downstream can accept data
  // - stall = !ready_in when valid
  // - flush clears the stage
  def withHandshake[T <: Data](
    valid_in: Bool,
    data_in: T,
    ready_out: Bool,
    stall: Bool,    // Additional stall from hazard unit
    flush: Bool
  ): (Bool, T, Bool) = {

    val valid_reg = RegInit(false.B)
    val data_reg = RegInit(0.U.asTypeOf(data_in.cloneType))

    // ready to accept when not stalled
    val ready_in = !valid_reg || (ready_out && !stall)

    when(flush) {
      valid_reg := false.B
      data_reg := 0.U.asTypeOf(data_in.cloneType)
    }.elsewhen(ready_in && valid_in) {
      valid_reg := true.B
      data_reg := data_in
    }.elsewhen(ready_out && !stall && valid_reg) {
      // Data accepted by downstream, clear valid if no new data
      valid_reg := false.B
    }

    (valid_reg, data_reg, ready_in)
  }

  // Simple 1-stage pipeline register (no handshake)
  // Just captures data each cycle unless stalled
  def simple[T <: Data](
    data: T,
    enable: Bool
  ): T = {
    val reg = RegEnable(data, enable)
    reg
  }
}

// ============================================================
// Hazard Detection Unit
// Detects data hazards and generates stall/forward control signals
// ============================================================

class HazardUnitIO(implicit config: CoreConfig) extends Bundle {
  // From ID stage (current instruction in decode)
  val id_rs1_addr = Input(UInt(5.W))
  val id_rs2_addr = Input(UInt(5.W))

  // From EX stage (instruction in execute)
  val ex_rd_addr = Input(UInt(5.W))
  val ex_reg_write = Input(Bool())
  val ex_mem_read = Input(Bool())  // Load instruction

  // From MEM stage (instruction in memory)
  val mem_rd_addr = Input(UInt(5.W))
  val mem_reg_write = Input(Bool())

  // From WB stage (instruction in writeback)
  val wb_rd_addr = Input(UInt(5.W))
  val wb_reg_write = Input(Bool())

  // Control outputs
  val stall_if = Output(Bool())
  val stall_id = Output(Bool())
  val flush_ex = Output(Bool())

  // Forwarding signals (for bypassing)
  val forward_a = Output(UInt(2.W))  // 00=reg, 01=ex, 10=mem, 11=wb
  val forward_b = Output(UInt(2.W))
}

class HazardUnit(implicit config: CoreConfig) extends Module {
  val io = IO(new HazardUnitIO)

  // Hazard detection for load-use (needs stall)
  // Load instruction in EX stage, next instruction uses loaded value
  val load_use_hazard = io.ex_mem_read &&
                      io.ex_reg_write &&
                      (io.ex_rd_addr =/= 0.U) &&
                      ((io.id_rs1_addr === io.ex_rd_addr) ||
                       (io.id_rs2_addr === io.ex_rd_addr))

  // Generate stall signals
  io.stall_if := load_use_hazard
  io.stall_id := load_use_hazard
  io.flush_ex := load_use_hazard

  // Forwarding logic (EX hazard)
  val forward_a_ex = io.ex_reg_write &&
                     (io.ex_rd_addr =/= 0.U) &&
                     (io.id_rs1_addr === io.ex_rd_addr)

  val forward_b_ex = io.ex_reg_write &&
                     (io.ex_rd_addr =/= 0.U) &&
                     (io.id_rs2_addr === io.ex_rd_addr)

  // Forwarding logic (MEM hazard)
  val forward_a_mem = io.mem_reg_write &&
                      (io.mem_rd_addr =/= 0.U) &&
                      (io.id_rs1_addr === io.mem_rd_addr) &&
                      !forward_a_ex

  val forward_b_mem = io.mem_reg_write &&
                      (io.mem_rd_addr =/= 0.U) &&
                      (io.id_rs2_addr === io.mem_rd_addr) &&
                      !forward_b_ex

  // Encode forwarding signals
  io.forward_a := MuxCase(0.U(2.W), Array(
    forward_a_ex -> 1.U,
    forward_a_mem -> 2.U
  ))

  io.forward_b := MuxCase(0.U(2.W), Array(
    forward_b_ex -> 1.U,
    forward_b_mem -> 2.U
  ))
}
