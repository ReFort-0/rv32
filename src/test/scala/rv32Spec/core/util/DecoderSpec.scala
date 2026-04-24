package rv32Spec.core.util

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import rv32.core.util._
import rv32.configs.CoreConfig

class DecoderSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit val conf: CoreConfig = new CoreConfig

  "Decoder" - {
    def rType(rd: Int, rs1: Int, rs2: Int, funct3: Int, funct7: Int, opcode: Int): UInt = {
      ((funct7 & 0x7F) << 25) | ((rs2 & 0x1F) << 20) | ((rs1 & 0x1F) << 15) |
        ((funct3 & 0x7) << 12) | ((rd & 0x1F) << 7) | (opcode & 0x7F)
    }.U(32.W)

    def iType(rd: Int, rs1: Int, imm: Int, funct3: Int, opcode: Int): UInt = {
      ((imm & 0xFFF) << 20) | ((rs1 & 0x1F) << 15) | ((funct3 & 0x7) << 12) |
        ((rd & 0x1F) << 7) | (opcode & 0x7F)
    }.U(32.W)

    def sType(rs1: Int, rs2: Int, imm: Int, funct3: Int, opcode: Int): UInt = {
      val imm11_5 = (imm >> 5) & 0x7F
      val imm4_0 = imm & 0x1F
      ((imm11_5) << 25) | ((rs2 & 0x1F) << 20) | ((rs1 & 0x1F) << 15) |
        ((funct3 & 0x7) << 12) | ((imm4_0) << 7) | (opcode & 0x7F)
    }.U(32.W)

    def bType(rs1: Int, rs2: Int, imm: Int, funct3: Int, opcode: Int): UInt = {
      val imm12 = (imm >> 12) & 0x1
      val imm10_5 = (imm >> 5) & 0x3F
      val imm4_1 = (imm >> 1) & 0xF
      val imm11 = (imm >> 11) & 0x1
      ((imm12) << 31) | ((imm10_5) << 25) | ((rs2 & 0x1F) << 20) | ((rs1 & 0x1F) << 15) |
        ((funct3 & 0x7) << 12) | ((imm4_1) << 8) | ((imm11) << 7) | (opcode & 0x7F)
    }.U(32.W)

    def uType(rd: Int, imm: Int, opcode: Int): UInt = {
      ((imm & 0xFFFFF) << 12) | ((rd & 0x1F) << 7) | (opcode & 0x7F)
    }.U(32.W)

    def jType(rd: Int, imm: Int, opcode: Int): UInt = {
      val imm20 = (imm >> 20) & 0x1
      val imm10_1 = (imm >> 1) & 0x3FF
      val imm11 = (imm >> 11) & 0x1
      val imm19_12 = (imm >> 12) & 0xFF
      ((imm20) << 31) | ((imm10_1) << 21) | ((imm11) << 20) | ((imm19_12) << 12) |
        ((rd & 0x1F) << 7) | (opcode & 0x7F)
    }.U(32.W)

    val OPCODE_LOAD = 0x03
    val OPCODE_STORE = 0x23
    val OPCODE_BRANCH = 0x63
    val OPCODE_JAL = 0x6F
    val OPCODE_JALR = 0x67
    val OPCODE_OP_IMM = 0x13
    val OPCODE_OP = 0x33
    val OPCODE_LUI = 0x37
    val OPCODE_AUIPC = 0x17

    "should decode ADD instruction correctly" in {
      simulate(new Decoder) { dut =>
        val inst = rType(5, 6, 7, 0, 0, OPCODE_OP)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.valid.expect(true.B)
        dut.io.ctrl.alu_op.expect(Constants.ALU_ADD)
        dut.io.ctrl.fu_sel.expect(Constants.FU_ALU)
        dut.io.ctrl.rf_wen.expect(true.B)
      }
    }

    "should decode SUB instruction correctly" in {
      simulate(new Decoder) { dut =>
        val inst = rType(5, 6, 7, 0, 0x20, OPCODE_OP)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.alu_op.expect(Constants.ALU_SUB)
      }
    }

    "should decode ADDI instruction correctly" in {
      simulate(new Decoder) { dut =>
        val inst = iType(5, 6, 42, 0, OPCODE_OP_IMM)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.alu_op.expect(Constants.ALU_ADD)
        dut.io.ctrl.imm_sel.expect(Constants.IMM_I)
        dut.io.ctrl.op2_sel.expect(Constants.OP2_IMM)
      }
    }

    "should decode LW instruction correctly" in {
      simulate(new Decoder) { dut =>
        val inst = iType(5, 6, 0, 2, OPCODE_LOAD)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.fu_sel.expect(Constants.FU_MEM)
        dut.io.ctrl.wb_sel.expect(Constants.WB_MEM)
      }
    }

    "should decode SW instruction correctly" in {
      simulate(new Decoder) { dut =>
        val inst = sType(6, 7, 0, 2, OPCODE_STORE)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.fu_sel.expect(Constants.FU_MEM)
        dut.io.ctrl.rf_wen.expect(false.B)
      }
    }

    "should decode BEQ instruction correctly" in {
      simulate(new Decoder) { dut =>
        val inst = bType(5, 6, 16, 0, OPCODE_BRANCH)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.br_type.expect(Constants.BR_EQ)
        dut.io.ctrl.imm_sel.expect(Constants.IMM_B)
      }
    }

    "should decode BNE instruction correctly" in {
      simulate(new Decoder) { dut =>
        val inst = bType(5, 6, 16, 1, OPCODE_BRANCH)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.br_type.expect(Constants.BR_NE)
      }
    }

    "should decode JAL instruction correctly" in {
      simulate(new Decoder) { dut =>
        val inst = jType(1, 0x400, OPCODE_JAL)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.br_type.expect(Constants.BR_J)
        dut.io.ctrl.wb_sel.expect(Constants.WB_PC4)
        dut.io.ctrl.imm_sel.expect(Constants.IMM_J)
      }
    }

    "should decode JALR instruction correctly" in {
      simulate(new Decoder) { dut =>
        val inst = iType(1, 5, 0, 0, OPCODE_JALR)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.pc_sel.expect(Constants.PC_JALR)
      }
    }

    "should decode LUI instruction correctly" in {
      simulate(new Decoder) { dut =>
        val inst = uType(5, 0x12345, OPCODE_LUI)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.alu_op.expect(Constants.ALU_ADD)
        dut.io.ctrl.op1_sel.expect(Constants.OP1_X0)
        dut.io.ctrl.imm_sel.expect(Constants.IMM_U)
      }
    }

    "should decode AUIPC instruction correctly" in {
      simulate(new Decoder) { dut =>
        val inst = uType(5, 0x12345, OPCODE_AUIPC)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.op1_sel.expect(Constants.OP1_PC)
        dut.io.ctrl.op2_sel.expect(Constants.OP2_IMM)
      }
    }

    "should decode AND/OR/XOR instructions correctly" in {
      simulate(new Decoder) { dut =>
        var inst = rType(5, 6, 7, 7, 0, OPCODE_OP)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.alu_op.expect(Constants.ALU_AND)

        inst = rType(5, 6, 7, 6, 0, OPCODE_OP)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.alu_op.expect(Constants.ALU_OR)

        inst = rType(5, 6, 7, 4, 0, OPCODE_OP)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.alu_op.expect(Constants.ALU_XOR)
      }
    }

    "should decode SLT/SLTU instructions correctly" in {
      simulate(new Decoder) { dut =>
        var inst = rType(5, 6, 7, 2, 0, OPCODE_OP)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.alu_op.expect(Constants.ALU_SLT)

        inst = rType(5, 6, 7, 3, 0, OPCODE_OP)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.alu_op.expect(Constants.ALU_SLTU)
      }
    }

    "should decode shift instructions correctly" in {
      simulate(new Decoder) { dut =>
        var inst = rType(5, 6, 7, 1, 0, OPCODE_OP)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.alu_op.expect(Constants.ALU_SLL)

        inst = rType(5, 6, 7, 5, 0, OPCODE_OP)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.alu_op.expect(Constants.ALU_SRL)

        inst = rType(5, 6, 7, 5, 0x20, OPCODE_OP)
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.alu_op.expect(Constants.ALU_SRA)
      }
    }

    "should decode invalid instruction as bubble (valid=false)" in {
      simulate(new Decoder) { dut =>
        dut.io.inst.poke(0.U)
        dut.clock.step()
        dut.io.ctrl.valid.expect(false.B)
      }
    }

    "should decode FENCE as invalid (not in decode table)" in {
      simulate(new Decoder) { dut =>
        val inst = BigInt("00FF000F", 16).U
        dut.io.inst.poke(inst)
        dut.clock.step()
        dut.io.ctrl.valid.expect(false.B)
      }
    }
  }
}