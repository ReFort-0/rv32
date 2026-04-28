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
  * Test Jump/Branch instruction decoding and execution
  * From mill use:
  * mill rv32.test.testOnly rv32Spec.core.JumpBranchSpec
  */
class JumpBranchSpec extends AnyFreeSpec with Matchers with ChiselSim {

  "JAL instruction should jump to correct target" in {
    implicit val config = CoreConfig()
    simulate(new ExecuteStage) { dut =>
      // Reset
      dut.clock.step()

      // Setup JAL instruction at PC=0x80000004, imm=0x100 (target=0x80000104)
      dut.io.idex.pc.poke(0x80000004L.U)
      dut.io.idex.imm.poke(0x100.S(32.W).asUInt)  // positive offset
      dut.io.idex.branch_type.poke(BR_J)
      dut.io.idex.valid.poke(true.B)

      // Check branch is taken
      dut.io.pc_take.expect(true.B)
      // Target = PC + imm = 0x80000004 + 0x100 = 0x80000104
      dut.io.pc_target.expect(0x80000104L.U)
    }
  }

  "JALR instruction should calculate correct target with LSB cleared" in {
    implicit val config = CoreConfig()
    simulate(new ExecuteStage) { dut =>
      dut.clock.step()

      // JALR: rd = PC+4, jump to rs1 + imm with LSB=0
      // rs1 = 0x80000000, imm = 0x50, expected target = 0x80000050 with LSB=0
      dut.io.idex.pc.poke(0x80000008L.U)
      dut.io.idex.rs1_data.poke(0x80000000L.U)
      dut.io.idex.imm.poke(0x50.S(32.W).asUInt)
      dut.io.idex.branch_type.poke(BR_N)  // JALR uses BR_N, not BR_J
      dut.io.idex.valid.poke(true.B)

      dut.io.pc_take.expect(false.B)  // JALR doesn't use branch_taken, PC handled separately
      // JALR target = (rs1 + imm) with LSB cleared
      dut.io.pc_target.expect(0x80000050L.U)  // 0x80000000 + 0x50 = 0x80000050, LSB=0
    }
  }

  "BEQ should branch when rs1 == rs2" in {
    implicit val config = CoreConfig()
    simulate(new ExecuteStage) { dut =>
      dut.clock.step()

      // BEQ with equal values
      dut.io.idex.pc.poke(0x80000020L.U)
      dut.io.idex.rs1_data.poke(42.U)
      dut.io.idex.rs2_data.poke(42.U)
      dut.io.idex.imm.poke(0x20.S(32.W).asUInt)
      dut.io.idex.branch_type.poke(BR_EQ)
      dut.io.idex.valid.poke(true.B)

      dut.io.pc_take.expect(true.B)
      dut.io.pc_target.expect(0x80000040L.U)  // PC + imm
    }
  }

  "BEQ should not branch when rs1 != rs2" in {
    implicit val config = CoreConfig()
    simulate(new ExecuteStage) { dut =>
      dut.clock.step()

      // BEQ with unequal values
      dut.io.idex.pc.poke(0x80000020L.U)
      dut.io.idex.rs1_data.poke(42.U)
      dut.io.idex.rs2_data.poke(100.U)
      dut.io.idex.imm.poke(0x20.S(32.W).asUInt)
      dut.io.idex.branch_type.poke(BR_EQ)
      dut.io.idex.valid.poke(true.B)

      dut.io.pc_take.expect(false.B)
    }
  }
}
