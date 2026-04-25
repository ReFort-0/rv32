package rv32.core.util

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig

// ============================================================
// HazardUnit - Pipeline hazard detection and forwarding
// Handles:
//   - Data forwarding (bypassing) from EX/MEM/WB to ID
//   - Load-use hazard detection and stall insertion
//   - Structural hazards (MulDiv busy)
//   - Branch flush generation
// ============================================================

class HazardUnitIO(implicit conf: CoreConfig) extends Bundle {
  // From ID stage (decode)
  val id_rs1 = Input(UInt(5.W))
  val id_rs2 = Input(UInt(5.W))
  val id_valid = Input(Bool())
  val id_use_rs1 = Input(Bool())  // Whether instruction uses rs1
  val id_use_rs2 = Input(Bool())  // Whether instruction uses rs2

  // From EX stage
  val ex_rd = Input(UInt(5.W))
  val ex_rf_wen = Input(Bool())
  val ex_valid = Input(Bool())
  val ex_is_load = Input(Bool())  // Load instruction in EX
  val ex_alu_result = Input(UInt(32.W))
  val ex_fu_sel = Input(UInt(2.W))

  // From MEM stage (ex_mem register outputs)
  val mem_rd = Input(UInt(5.W))
  val mem_rf_wen = Input(Bool())
  val mem_valid = Input(Bool())
  val mem_result = Input(UInt(32.W))

  // From WB stage
  val wb_rd = Input(UInt(5.W))
  val wb_rf_wen = Input(Bool())
  val wb_valid = Input(Bool())
  val wb_result = Input(UInt(32.W))

  // From Execute stage (MulDiv busy signal)
  val muldiv_busy = Input(Bool())

  // Branch detection from Execute stage
  val br_taken = Input(Bool())

  // Outputs: Stall signal
  val stall = Output(Bool())

  // Outputs: Forwarding for rs1
  // 00 = use regfile, 01 = forward from EX, 10 = forward from MEM, 11 = forward from WB
  val fwd_rs1_sel = Output(UInt(2.W))
  val fwd_rs1_data_ex = Output(UInt(32.W))
  val fwd_rs1_data_mem = Output(UInt(32.W))
  val fwd_rs1_data_wb = Output(UInt(32.W))

  // Outputs: Forwarding for rs2
  val fwd_rs2_sel = Output(UInt(2.W))
  val fwd_rs2_data_ex = Output(UInt(32.W))
  val fwd_rs2_data_mem = Output(UInt(32.W))
  val fwd_rs2_data_wb = Output(UInt(32.W))

  // MulDiv busy output for Execute stage feedback
  val muldiv_busy_out = Output(Bool())
}

class HazardUnit(implicit conf: CoreConfig) extends Module {
  val io = IO(new HazardUnitIO())

  // ============================================================
  // Data Forwarding Logic
  // Check each pipeline stage for pending writes to rs1/rs2
  // Priority: EX > MEM > WB > RegFile
  // ============================================================

  // Forward from EX stage
  val ex_hazard_rs1 = io.id_use_rs1 && io.ex_valid && io.ex_rf_wen &&
                      (io.ex_rd =/= 0.U) && (io.ex_rd === io.id_rs1)
  val ex_hazard_rs2 = io.id_use_rs2 && io.ex_valid && io.ex_rf_wen &&
                      (io.ex_rd =/= 0.U) && (io.ex_rd === io.id_rs2)

  // Forward from MEM stage
  val mem_hazard_rs1 = io.id_use_rs1 && io.mem_valid && io.mem_rf_wen &&
                       (io.mem_rd =/= 0.U) && (io.mem_rd === io.id_rs1)
  val mem_hazard_rs2 = io.id_use_rs2 && io.mem_valid && io.mem_rf_wen &&
                       (io.mem_rd =/= 0.U) && (io.mem_rd === io.id_rs2)

  // Forward from WB stage
  val wb_hazard_rs1 = io.id_use_rs1 && io.wb_valid && io.wb_rf_wen &&
                      (io.wb_rd =/= 0.U) && (io.wb_rd === io.id_rs1)
  val wb_hazard_rs2 = io.id_use_rs2 && io.wb_valid && io.wb_rf_wen &&
                      (io.wb_rd =/= 0.U) && (io.wb_rd === io.id_rs2)

  // Forwarding select signals (priority encoded)
  // 01 = EX, 10 = MEM, 11 = WB, 00 = no forwarding (use regfile)
  io.fwd_rs1_sel := MuxCase(0.U, Array(
    ex_hazard_rs1  -> 1.U,
    mem_hazard_rs1 -> 2.U,
    wb_hazard_rs1  -> 3.U
  ))

  io.fwd_rs2_sel := MuxCase(0.U, Array(
    ex_hazard_rs2  -> 1.U,
    mem_hazard_rs2 -> 2.U,
    wb_hazard_rs2  -> 3.U
  ))

  // Forwarding data sources
  io.fwd_rs1_data_ex  := io.ex_alu_result
  io.fwd_rs1_data_mem := io.mem_result
  io.fwd_rs1_data_wb  := io.wb_result

  io.fwd_rs2_data_ex  := io.ex_alu_result
  io.fwd_rs2_data_mem := io.mem_result
  io.fwd_rs2_data_wb  := io.wb_result

  // ============================================================
  // Load-Use Hazard Detection
  // If EX stage is a load and ID stage needs that result, stall for 1 cycle
  // Condition: Load in EX, register in ID matches EX.rd, and that register is used
  // ============================================================

  val load_use_rs1 = io.id_use_rs1 && io.ex_is_load && io.ex_valid &&
                     (io.ex_rd =/= 0.U) && (io.ex_rd === io.id_rs1)
  val load_use_rs2 = io.id_use_rs2 && io.ex_is_load && io.ex_valid &&
                     (io.ex_rd =/= 0.U) && (io.ex_rd === io.id_rs2)

  val load_use_hazard = load_use_rs1 || load_use_rs2

  // ============================================================
  // Structural Hazard: MulDiv Unit Busy
  // If MulDiv is busy (multi-cycle) and current ID instruction needs MulDiv, stall
  // Note: In this implementation, MulDiv takes 1 cycle but we keep the infrastructure
  // for future multi-cycle implementations
  // ============================================================

  val muldiv_hazard = io.muldiv_busy && io.id_valid

  // ============================================================
  // Final Stall Signal
  // ============================================================

  io.stall := load_use_hazard || muldiv_hazard

  // ============================================================
  // MulDiv Busy Passthrough
  // ============================================================

  io.muldiv_busy_out := io.muldiv_busy

  // Note: Branch flush is handled directly in NeoRV32Core via execute.io.br_taken
  // This HazardUnit provides stall and forwarding signals only
}
