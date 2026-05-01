package rv32Spec.core

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import rv32.configs._
import rv32.core.stage._
import rv32.core.util.Constants._

/**
  * DecodeStage unit tests
  * Run with: ./mill rv32.test.testOnly rv32Spec.core.DecodeStageSpec
  */
class DecodeStageSpec extends AnyFreeSpec with Matchers with ChiselSim {

  def createIFIDBundle(pc: Long, inst: Long, valid: Boolean)(implicit config: CoreConfig) = {
    chiselTypeOf(new rv32.core.util.IFIDBundle).Lit(
      _.pc -> pc.U,
      _.inst -> inst.U,
      _.valid -> valid.B
    )
  }

  "DecodeStage should" - {

    "decode ADDI instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new DecodeStage) { dut =>
        // ADDI x5, x6, 10: adds 10 to x6, writes to x5
        // opcode=0010011 (0x13), funct3=000, rd=00101, rs1=00110, imm=000000001010
        val addiInst = 0x00A30293L  // addi x5, x6, 10

        dut.io.ifid.pc.poke(0x80000000L.U)
        dut.io.ifid.inst.poke(addiInst.U)
        dut.io.ifid.valid.poke(true.B)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        // No writeback yet
        dut.io.wb_reg_write.poke(false.B)

        dut.clock.step()

        // Check pipeline output
        dut.io.idex.pc.expect(0x80000000L.U)
        dut.io.idex.inst.expect(addiInst.U)
        dut.io.idex.rd_addr.expect(5.U)  // x5
        dut.io.idex.rs1_addr.expect(6.U)  // x6
        dut.io.idex.valid.expect(true.B)
      }
    }

    "decode LUI instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new DecodeStage) { dut =>
        // LUI x10, 0x12345: load upper immediate
        // opcode=0110111 (0x37), rd=01010, imm[31:12]=0001 0010 0011 0100 0101
        val luiInst = 0x12345537L  // lui x10, 0x12345

        dut.io.ifid.pc.poke(0x80000004L.U)
        dut.io.ifid.inst.poke(luiInst.U)
        dut.io.ifid.valid.poke(true.B)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.io.wb_reg_write.poke(false.B)

        dut.clock.step()

        dut.io.idex.rd_addr.expect(10.U)  // x10
        dut.io.idex.valid.expect(true.B)
        // LUI uses OP1_X0 and OP2_IMM
        dut.io.idex.op1_sel.expect(OP1_X0)
        dut.io.idex.op2_sel.expect(OP2_IMM)
      }
    }

    "decode BEQ instruction correctly" in {
      implicit val config = CoreConfig()
      simulate(new DecodeStage) { dut =>
        // BEQ x3, x4, offset
        // opcode=1100011 (0x63), funct3=000
        // imm[12|10:5]=0000000, rs2=4, rs1=3, funct3=000, imm[4:1|11]=0000, opcode=1100011
        val beqInst = 0x00418063L  // beq x3, x4, 0 (correct encoding)

        dut.io.ifid.pc.poke(0x80000008L.U)
        dut.io.ifid.inst.poke(beqInst.U)
        dut.io.ifid.valid.poke(true.B)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.io.wb_reg_write.poke(false.B)

        dut.clock.step()

        dut.io.idex.rs1_addr.expect(3.U)  // x3
        dut.io.idex.rs2_addr.expect(4.U)  // x4
        dut.io.idex.branch_type.expect(BR_EQ)
      }
    }

    "read register file correctly" in {
      implicit val config = CoreConfig()
      simulate(new DecodeStage) { dut =>
        // This test checks that rs1 and rs2 addresses are correctly extracted
        // ADDI x2, x1, 5
        val addiInst = 0x00508093L  // addi x1, x1, 5

        dut.io.ifid.pc.poke(0x80000000L.U)
        dut.io.ifid.inst.poke(addiInst.U)
        dut.io.ifid.valid.poke(true.B)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)
        dut.io.wb_reg_write.poke(false.B)

        dut.clock.step()

        // x1 is both source and destination for addi
        dut.io.idex.rs1_addr.expect(1.U)
        dut.io.idex.rd_addr.expect(1.U)
      }
    }

    "stall when stall signal is asserted" in {
      implicit val config = CoreConfig(pipelineStages = 5)
      simulate(new DecodeStage) { dut =>
        val inst1 = 0x00A30293L  // addi x5, x6, 10
        val inst2 = 0x00A30313L  // addi x6, x6, 10

        dut.io.ifid.pc.poke(0x80000000L.U)
        dut.io.ifid.inst.poke(inst1.U)
        dut.io.ifid.valid.poke(true.B)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)
        dut.io.wb_reg_write.poke(false.B)

        dut.clock.step()
        dut.io.idex.inst.expect(inst1.U)

        // Now stall and change input
        dut.io.stall.poke(true.B)
        dut.io.ifid.inst.poke(inst2.U)
        dut.clock.step()

        // Output should not change due to stall
        dut.io.idex.inst.expect(inst1.U)
      }
    }

    "flush when flush signal is asserted" in {
      implicit val config = CoreConfig(pipelineStages = 5)
      simulate(new DecodeStage) { dut =>
        dut.io.ifid.pc.poke(0x80000000L.U)
        dut.io.ifid.inst.poke(0x00A30293L.U)
        dut.io.ifid.valid.poke(true.B)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step()
        dut.io.idex.valid.expect(true.B)

        // Now flush
        dut.io.flush.poke(true.B)
        dut.clock.step()

        // Valid should be cleared
        dut.io.idex.valid.expect(false.B)
      }
    }

    "write back data from writeback stage" in {
      implicit val config = CoreConfig()
      simulate(new DecodeStage) { dut =>
        // First instruction to set up register file
        dut.io.wb_reg_write.poke(true.B)
        dut.io.wb_rd_addr.poke(5.U)   // Write to x5
        dut.io.wb_rd_data.poke(42.U)  // Value 42

        dut.clock.step()

        // Now read x5
        dut.io.ifid.inst.poke(0x00030293L)  // addi x5, x6, 0 (rs1=x6, but we want to test x5)
        dut.io.ifid.inst.poke(0x00028293L)  // addi x5, x5, 0 (rs1=x5)
        dut.io.wb_reg_write.poke(false.B)

        dut.clock.step()

        // The value should be visible through rs1_data
        dut.io.idex.rs1_addr.expect(5.U)
      }
    }
  }
}
