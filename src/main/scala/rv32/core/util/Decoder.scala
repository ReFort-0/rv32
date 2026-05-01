package rv32.core.util

import chisel3._
import chisel3.util._
import rv32.configs.CoreConfig

// ============================================================
// Decoder - ListLookup based instruction decoder
// Pattern from rocket-chip/sodor: ListLookup(inst, default, table)
// ============================================================

class DecoderIO extends Bundle {
  val inst = Input(UInt(32.W))
  val ctrl = Output(new ControlSignals)
}

class Decoder(implicit config: CoreConfig) extends Module {
  import Constants._

  val io = IO(new DecoderIO)

  val defaultCtrl = List(
    N,          // valid
    PC_PLUS4,   // pc_sel
    OP1_X,      // op1_sel
    OP2_X,      // op2_sel
    IMM_X,      // imm_sel
    ALU_X,      // alu_op
    FU_ALU,     // fu_sel
    0.U(3.W),   // muldiv_op
    BR_N,       // br_type
    WB_X,       // wb_sel
    N,          // rf_wen
    M_X,        // mem_fcn
    MT_X        // mem_typ
  )

  // Base RV32I decode table
  val baseDecodeTable = Array(
    // RV32I Load/Store
    LW    -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_MEM, 0.U(3.W), BR_N,   WB_MEM, Y, M_XRD, MT_W),
    LH    -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_MEM, 0.U(3.W), BR_N,   WB_MEM, Y, M_XRD, MT_H),
    LHU   -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_MEM, 0.U(3.W), BR_N,   WB_MEM, Y, M_XRD, MT_HU),
    LB    -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_MEM, 0.U(3.W), BR_N,   WB_MEM, Y, M_XRD, MT_B),
    LBU   -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_MEM, 0.U(3.W), BR_N,   WB_MEM, Y, M_XRD, MT_BU),
    SW    -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_S,  ALU_ADD,  FU_MEM, 0.U(3.W), BR_N,   WB_X,   N, M_XWR, MT_W),
    SH    -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_S,  ALU_ADD,  FU_MEM, 0.U(3.W), BR_N,   WB_X,   N, M_XWR, MT_H),
    SB    -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_S,  ALU_ADD,  FU_MEM, 0.U(3.W), BR_N,   WB_X,   N, M_XWR, MT_B),

    // RV32I Integer arithmetic - immediate
    ADDI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    SLTI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_SLT,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    SLTIU -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_SLTU, FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    ANDI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_AND,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    ORI   -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_OR,   FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    XORI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_XOR,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    SLLI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_SLL,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    SRLI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_SRL,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    SRAI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_SRA,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),

    // RV32I Integer arithmetic - register
    ADD   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_ADD,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    SUB   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_SUB,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    SLT   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_SLT,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    SLTU  -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_SLTU, FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    AND   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_AND,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    OR    -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_OR,   FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    XOR   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_XOR,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    SLL   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_SLL,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    SRL   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_SRL,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    SRA   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_SRA,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),

    // RV32I Upper immediates
    LUI   -> List(Y, PC_PLUS4, OP1_X0,  OP2_IMM, IMM_U,  ALU_ADD,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),
    AUIPC -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_U,  ALU_ADD,  FU_ALU, 0.U(3.W), BR_N,   WB_ALU, Y, M_X,   MT_X),

    // RV32I Branches
    BEQ   -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_B,  ALU_ADD,  FU_ALU, 0.U(3.W), BR_EQ,  WB_X,   N, M_X,   MT_X),
    BNE   -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_B,  ALU_ADD,  FU_ALU, 0.U(3.W), BR_NE,  WB_X,   N, M_X,   MT_X),
    BLT   -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_B,  ALU_ADD,  FU_ALU, 0.U(3.W), BR_LT,  WB_X,   N, M_X,   MT_X),
    BGE   -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_B,  ALU_ADD,  FU_ALU, 0.U(3.W), BR_GE,  WB_X,   N, M_X,   MT_X),
    BLTU  -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_B,  ALU_ADD,  FU_ALU, 0.U(3.W), BR_LTU, WB_X,   N, M_X,   MT_X),
    BGEU  -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_B,  ALU_ADD,  FU_ALU, 0.U(3.W), BR_GEU, WB_X,   N, M_X,   MT_X),

    // RV32I Jumps
    JAL   -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_J,  ALU_ADD,  FU_ALU, 0.U(3.W), BR_J,   WB_PC4, Y, M_X,   MT_X),
    JALR  -> List(Y, PC_JALR,  OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_ALU, 0.U(3.W), BR_J,   WB_PC4, Y, M_X,   MT_X)
  )

  // RV32M extension decode table (conditionally included)
  val mExtensionTable = Array(
    MUL    -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, MULDIV_MUL,    BR_N, WB_ALU, Y, M_X, MT_X),
    MULH   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, MULDIV_MULH,   BR_N, WB_ALU, Y, M_X, MT_X),
    MULHSU -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, MULDIV_MULHSU, BR_N, WB_ALU, Y, M_X, MT_X),
    MULHU  -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, MULDIV_MULHU,  BR_N, WB_ALU, Y, M_X, MT_X),
    DIV    -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, MULDIV_DIV,    BR_N, WB_ALU, Y, M_X, MT_X),
    DIVU   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, MULDIV_DIVU,   BR_N, WB_ALU, Y, M_X, MT_X),
    REM    -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, MULDIV_REM,    BR_N, WB_ALU, Y, M_X, MT_X),
    REMU   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, MULDIV_REMU,   BR_N, WB_ALU, Y, M_X, MT_X)
  )

  // Build final decode table based on configuration
  val decodeTable = if (config.useM) {
    baseDecodeTable ++ mExtensionTable
  } else {
    baseDecodeTable
  }

  val cs = ListLookup(io.inst, defaultCtrl, decodeTable)

  val ctrl = Wire(new ControlSignals)
  ctrl.valid      := cs(0)
  ctrl.pc_sel     := cs(1)
  ctrl.op1_sel    := cs(2)
  ctrl.op2_sel    := cs(3)
  ctrl.imm_type   := cs(4)
  ctrl.alu_op     := cs(5)
  ctrl.fu_sel     := cs(6)
  ctrl.muldiv_op  := cs(7)
  ctrl.branch_type:= cs(8)
  ctrl.wb_sel     := cs(9)
  ctrl.reg_write  := cs(10)
  ctrl.mem_en     := cs(11) =/= M_X
  ctrl.mem_rw     := cs(11) === M_XWR
  ctrl.mem_type   := cs(12)

  io.ctrl := ctrl
}
