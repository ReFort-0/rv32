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
    BR_N,       // br_type
    WB_X,       // wb_sel
    N,          // rf_wen
    M_X,        // mem_fcn
    MT_X,       // mem_typ
    N           // muldiv op (valid)
  )

  // Instruction decode table
  // Format: BitPat("binary") -> List(valid, pc_sel, op1, op2, imm, alu, fu, br, wb, rf_wen, mem_fcn, mem_typ, muldiv_valid)
  val decodeTable = Array(
    // RV32I Load/Store
    LW    -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_MEM, BR_N,   WB_MEM, Y, M_XRD, MT_W,  N),
    LH    -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_MEM, BR_N,   WB_MEM, Y, M_XRD, MT_H,  N),
    LHU   -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_MEM, BR_N,   WB_MEM, Y, M_XRD, MT_HU, N),
    LB    -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_MEM, BR_N,   WB_MEM, Y, M_XRD, MT_B,  N),
    LBU   -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_MEM, BR_N,   WB_MEM, Y, M_XRD, MT_BU, N),
    SW    -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_S,  ALU_ADD,  FU_MEM, BR_N,   WB_X,   N, M_XWR, MT_W,  N),
    SH    -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_S,  ALU_ADD,  FU_MEM, BR_N,   WB_X,   N, M_XWR, MT_H,  N),
    SB    -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_S,  ALU_ADD,  FU_MEM, BR_N,   WB_X,   N, M_XWR, MT_B,  N),

    // RV32I Integer arithmetic - immediate
    ADDI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    SLTI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_SLT,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    SLTIU -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_SLTU, FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    ANDI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_AND,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    ORI   -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_OR,   FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    XORI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_XOR,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    SLLI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_SLL,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    SRLI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_SRL,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    SRAI  -> List(Y, PC_PLUS4, OP1_RS1, OP2_IMM, IMM_I,  ALU_SRA,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),

    // RV32I Integer arithmetic - register
    ADD   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_ADD,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    SUB   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_SUB,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    SLT   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_SLT,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    SLTU  -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_SLTU, FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    AND   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_AND,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    OR    -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_OR,   FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    XOR   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_XOR,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    SLL   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_SLL,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    SRL   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_SRL,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    SRA   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_SRA,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),

    // RV32I Upper immediates
    LUI   -> List(Y, PC_PLUS4, OP1_X0,  OP2_IMM, IMM_U,  ALU_ADD,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),
    AUIPC -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_U,  ALU_ADD,  FU_ALU, BR_N,   WB_ALU, Y, M_X,   MT_X,  N),

    // RV32I Branches
    BEQ   -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_B,  ALU_ADD,  FU_ALU, BR_EQ,  WB_X,   N, M_X,   MT_X,  N),
    BNE   -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_B,  ALU_ADD,  FU_ALU, BR_NE,  WB_X,   N, M_X,   MT_X,  N),
    BLT   -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_B,  ALU_ADD,  FU_ALU, BR_LT,  WB_X,   N, M_X,   MT_X,  N),
    BGE   -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_B,  ALU_ADD,  FU_ALU, BR_GE,  WB_X,   N, M_X,   MT_X,  N),
    BLTU  -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_B,  ALU_ADD,  FU_ALU, BR_LTU, WB_X,   N, M_X,   MT_X,  N),
    BGEU  -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_B,  ALU_ADD,  FU_ALU, BR_GEU, WB_X,   N, M_X,   MT_X,  N),

    // RV32I Jumps
    JAL   -> List(Y, PC_PLUS4, OP1_PC,  OP2_IMM, IMM_J,  ALU_ADD,  FU_ALU, BR_J,   WB_PC4, Y, M_X,   MT_X,  N),
    JALR  -> List(Y, PC_JALR,  OP1_RS1, OP2_IMM, IMM_I,  ALU_ADD,  FU_ALU, BR_J,   WB_PC4, Y, M_X,   MT_X,  N),

    // RV32M Multiply Extension (only valid when useM=true)
    MUL    -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, BR_N, WB_ALU, Y, M_X, MT_X, Y),
    MULH   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, BR_N, WB_ALU, Y, M_X, MT_X, Y),
    MULHSU -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, BR_N, WB_ALU, Y, M_X, MT_X, Y),
    MULHU  -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, BR_N, WB_ALU, Y, M_X, MT_X, Y),
    DIV    -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, BR_N, WB_ALU, Y, M_X, MT_X, Y),
    DIVU   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, BR_N, WB_ALU, Y, M_X, MT_X, Y),
    REM    -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, BR_N, WB_ALU, Y, M_X, MT_X, Y),
    REMU   -> List(Y, PC_PLUS4, OP1_RS1, OP2_RS2, IMM_X,  ALU_X,    FU_MULDIV, BR_N, WB_ALU, Y, M_X, MT_X, Y)
  )

  val cs = ListLookup(io.inst, defaultCtrl, decodeTable)

  val ctrl = Wire(new ControlSignals)
  ctrl.valid      := cs(0)
  ctrl.pc_sel     := cs(1)
  ctrl.op1_sel    := cs(2)
  ctrl.op2_sel    := cs(3)
  ctrl.imm_type   := cs(4)
  ctrl.alu_op     := cs(5)
  ctrl.fu_sel     := cs(6)
  ctrl.branch_type:= cs(7)
  ctrl.wb_sel     := cs(8)
  ctrl.reg_write  := cs(9)
  ctrl.mem_en     := cs(10) =/= M_X
  ctrl.mem_rw     := cs(10) === M_XWR
  ctrl.mem_type   := cs(11)

  io.ctrl := ctrl
}
