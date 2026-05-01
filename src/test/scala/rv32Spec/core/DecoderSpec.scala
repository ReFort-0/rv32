package rv32Spec.core

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import rv32.configs._
import rv32.core.util._
import rv32.core.util.Constants._

/**
  * Decoder unit tests - tests instruction decoding for all RV32I/M instructions
  * Run with: ./mill rv32.test.testOnly rv32Spec.core.DecoderSpec
  */
class DecoderSpec extends AnyFreeSpec with Matchers with ChiselSim {

  "Decoder should" - {

    // Helper function to assemble RISC-V instruction
    def encodeRType(opcode: Int, rd: Int, funct3: Int, rs1: Int, rs2: Int, funct7: Int): Int = {
      (funct7 << 25) | (rs2 << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | opcode
    }

    def encodeIType(opcode: Int, rd: Int, funct3: Int, rs1: Int, imm: Int): Int = {
      (imm << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | opcode
    }

    def encodeSType(opcode: Int, funct3: Int, rs1: Int, rs2: Int, imm: Int): Int = {
      ((imm & 0xFE0) << 20) | (rs2 << 20) | (rs1 << 15) | (funct3 << 12) | ((imm & 0x1F) << 7) | opcode
    }

    def encodeBType(opcode: Int, funct3: Int, rs1: Int, rs2: Int, imm: Int): Int = {
      ((imm & 0x1000) << 19) | ((imm & 0x7E0) << 20) | (rs2 << 20) | (rs1 << 15) |
      (funct3 << 12) | ((imm & 0x1E) << 7) | ((imm & 0x800) >> 4) | opcode
    }

    def encodeUType(opcode: Int, rd: Int, imm: Int): Int = {
      (imm & 0xFFFFF000) | (rd << 7) | opcode
    }

    def encodeJType(opcode: Int, rd: Int, imm: Int): Int = {
      ((imm & 0x100000) << 11) | ((imm & 0x7FE) << 20) | ((imm & 0x800) << 9) |
      ((imm & 0xFF000)) | (rd << 7) | opcode
    }

    "decode LUI instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeUType(0x37, 5, 0x12345000)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.op1_sel.expect(OP1_X0)
        dut.io.ctrl.op2_sel.expect(OP2_IMM)
        dut.io.ctrl.imm_type.expect(IMM_U)
        dut.io.ctrl.alu_op.expect(ALU_ADD)
        dut.io.ctrl.wb_sel.expect(WB_ALU)
        dut.io.ctrl.reg_write.expect(true.B)
        dut.io.ctrl.mem_en.expect(false.B)
      }
    }

    "decode AUIPC instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeUType(0x17, 10, 0x80000)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.op1_sel.expect(OP1_PC)
        dut.io.ctrl.op2_sel.expect(OP2_IMM)
        dut.io.ctrl.imm_type.expect(IMM_U)
        dut.io.ctrl.alu_op.expect(ALU_ADD)
        dut.io.ctrl.wb_sel.expect(WB_ALU)
        dut.io.ctrl.reg_write.expect(true.B)
      }
    }

    "decode JAL instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeJType(0x6F, 1, 0x100)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.branch_type.expect(BR_J)
        dut.io.ctrl.wb_sel.expect(WB_PC4)
        dut.io.ctrl.reg_write.expect(true.B)
        dut.io.ctrl.imm_type.expect(IMM_J)
      }
    }

    "decode JALR instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x67, 1, 0, 5, 8)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.pc_sel.expect(PC_JALR)
        dut.io.ctrl.branch_type.expect(BR_J)
        dut.io.ctrl.wb_sel.expect(WB_PC4)
        dut.io.ctrl.reg_write.expect(true.B)
        dut.io.ctrl.op1_sel.expect(OP1_RS1)
        dut.io.ctrl.op2_sel.expect(OP2_IMM)
        dut.io.ctrl.imm_type.expect(IMM_I)
      }
    }

    "decode BEQ instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeBType(0x63, 0, 1, 2, 0x100)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.branch_type.expect(BR_EQ)
        dut.io.ctrl.imm_type.expect(IMM_B)
        dut.io.ctrl.reg_write.expect(false.B)
        dut.io.ctrl.mem_en.expect(false.B)
      }
    }

    "decode BNE instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeBType(0x63, 1, 3, 4, 0x80)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.branch_type.expect(BR_NE)
        dut.io.ctrl.imm_type.expect(IMM_B)
      }
    }

    "decode BLT instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeBType(0x63, 4, 5, 6, 0x40)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.branch_type.expect(BR_LT)
      }
    }

    "decode BGE instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeBType(0x63, 5, 7, 8, 0x20)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.branch_type.expect(BR_GE)
      }
    }

    "decode BLTU instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeBType(0x63, 6, 9, 10, 0x10)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.branch_type.expect(BR_LTU)
      }
    }

    "decode BGEU instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeBType(0x63, 7, 11, 12, 0x8)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.branch_type.expect(BR_GEU)
      }
    }

    "decode LW instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x03, 5, 2, 10, 0x100)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.mem_en.expect(true.B)
        dut.io.ctrl.mem_rw.expect(false.B)
        dut.io.ctrl.mem_type.expect(MT_W)
        dut.io.ctrl.wb_sel.expect(WB_MEM)
        dut.io.ctrl.reg_write.expect(true.B)
      }
    }

    "decode LH instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x03, 6, 1, 11, 0x50)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.mem_en.expect(true.B)
        dut.io.ctrl.mem_type.expect(MT_H)
        dut.io.ctrl.wb_sel.expect(WB_MEM)
      }
    }

    "decode LHU instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x03, 7, 5, 12, 0x20)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.mem_en.expect(true.B)
        dut.io.ctrl.mem_type.expect(MT_HU)
      }
    }

    "decode LB instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x03, 8, 0, 13, 0x10)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.mem_en.expect(true.B)
        dut.io.ctrl.mem_type.expect(MT_B)
      }
    }

    "decode LBU instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x03, 9, 4, 14, 0x8)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.mem_en.expect(true.B)
        dut.io.ctrl.mem_type.expect(MT_BU)
      }
    }

    "decode SW instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeSType(0x23, 2, 5, 10, 0x100)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.mem_en.expect(true.B)
        dut.io.ctrl.mem_rw.expect(true.B)
        dut.io.ctrl.mem_type.expect(MT_W)
        dut.io.ctrl.reg_write.expect(false.B)
      }
    }

    "decode SH instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeSType(0x23, 1, 6, 11, 0x50)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.mem_en.expect(true.B)
        dut.io.ctrl.mem_rw.expect(true.B)
        dut.io.ctrl.mem_type.expect(MT_H)
      }
    }

    "decode SB instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeSType(0x23, 0, 7, 12, 0x20)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.mem_en.expect(true.B)
        dut.io.ctrl.mem_rw.expect(true.B)
        dut.io.ctrl.mem_type.expect(MT_B)
      }
    }

    "decode ADDI instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x13, 5, 0, 10, 100)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.op1_sel.expect(OP1_RS1)
        dut.io.ctrl.op2_sel.expect(OP2_IMM)
        dut.io.ctrl.imm_type.expect(IMM_I)
        dut.io.ctrl.alu_op.expect(ALU_ADD)
        dut.io.ctrl.wb_sel.expect(WB_ALU)
        dut.io.ctrl.reg_write.expect(true.B)
      }
    }

    "decode SLTI instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x13, 6, 2, 11, 50)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_SLT)
        dut.io.ctrl.wb_sel.expect(WB_ALU)
      }
    }

    "decode SLTIU instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x13, 7, 3, 12, 25)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_SLTU)
      }
    }

    "decode XORI instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x13, 8, 4, 13, 0xFF)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_XOR)
      }
    }

    "decode ORI instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x13, 9, 6, 14, 0xAA)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_OR)
      }
    }

    "decode ANDI instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x13, 10, 7, 15, 0x55)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_AND)
      }
    }

    "decode SLLI instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x13, 11, 1, 1, 5)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_SLL)
      }
    }

    "decode SRLI instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x13, 12, 5, 2, 3)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_SRL)
      }
    }

    "decode SRAI instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeIType(0x13, 13, 5, 3, 0x400 | 7)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_SRA)
      }
    }

    "decode ADD instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 5, 0, 10, 15, 0)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.op1_sel.expect(OP1_RS1)
        dut.io.ctrl.op2_sel.expect(OP2_RS2)
        dut.io.ctrl.alu_op.expect(ALU_ADD)
        dut.io.ctrl.wb_sel.expect(WB_ALU)
        dut.io.ctrl.reg_write.expect(true.B)
      }
    }

    "decode SUB instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 6, 0, 11, 16, 0x20)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_SUB)
      }
    }

    "decode SLL instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 7, 1, 12, 17, 0)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_SLL)
      }
    }

    "decode SLT instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 8, 2, 13, 18, 0)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_SLT)
      }
    }

    "decode SLTU instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 9, 3, 14, 19, 0)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_SLTU)
      }
    }

    "decode XOR instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 10, 4, 15, 20, 0)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_XOR)
      }
    }

    "decode SRL instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 11, 5, 1, 21, 0)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_SRL)
      }
    }

    "decode SRA instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 12, 5, 2, 22, 0x20)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_SRA)
      }
    }

    "decode OR instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 13, 6, 3, 23, 0)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_OR)
      }
    }

    "decode AND instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 14, 7, 4, 24, 0)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(ALU_AND)
      }
    }

    "decode MUL instruction correctly" in {
      implicit val config = CoreConfig(useM = true)
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 5, 0, 10, 15, 1)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.fu_sel.expect(FU_MULDIV)
        dut.io.ctrl.wb_sel.expect(WB_ALU)
        dut.io.ctrl.reg_write.expect(true.B)
      }
    }

    "decode MULH instruction correctly" in {
      implicit val config = CoreConfig(useM = true)
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 6, 1, 11, 16, 1)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.fu_sel.expect(FU_MULDIV)
      }
    }

    "decode DIV instruction correctly" in {
      implicit val config = CoreConfig(useM = true)
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 7, 4, 12, 17, 1)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.fu_sel.expect(FU_MULDIV)
      }
    }

    "decode REM instruction correctly" in {
      implicit val config = CoreConfig(useM = true)
      simulate(new Decoder) { dut =>
        val inst = encodeRType(0x33, 8, 6, 13, 18, 1)
        dut.io.inst.poke(inst.U)
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.fu_sel.expect(FU_MULDIV)
      }
    }

    "return default control signals for invalid instruction" in {
      implicit val config = CoreConfig()
      simulate(new Decoder) { dut =>
        dut.io.inst.poke(0xFFFFFFFFL.U)
        dut.io.ctrl.valid.expect(false.B)
        dut.io.ctrl.reg_write.expect(false.B)
        dut.io.ctrl.mem_en.expect(false.B)
      }
    }
  }
}
