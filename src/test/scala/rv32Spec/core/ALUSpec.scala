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
  * ALU unit tests
  * Run with: ./mill rv32.test.testOnly rv32Spec.core.ALUSpec
  */
class ALUSpec extends AnyFreeSpec with Matchers with ChiselSim {

  "ALU should" - {

    "perform ADD correctly" in {
      implicit val config = CoreConfig()
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU_ADD)
        dut.io.in1.poke(5.U)
        dut.io.in2.poke(3.U)
        dut.io.out.expect(8.U)
      }
    }

    "perform SUB correctly" in {
      implicit val config = CoreConfig()
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU_SUB)
        dut.io.in1.poke(10.U)
        dut.io.in2.poke(3.U)
        dut.io.out.expect(7.U)
      }
    }

    "perform AND correctly" in {
      implicit val config = CoreConfig()
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU_AND)
        dut.io.in1.poke(0xFF00.U)
        dut.io.in2.poke(0x0F0F.U)
        dut.io.out.expect(0x0F00.U)
      }
    }

    "perform OR correctly" in {
      implicit val config = CoreConfig()
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU_OR)
        dut.io.in1.poke(0xFF00.U)
        dut.io.in2.poke(0x000F.U)
        dut.io.out.expect(0xFF0F.U)
      }
    }

    "perform XOR correctly" in {
      implicit val config = CoreConfig()
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU_XOR)
        dut.io.in1.poke(0xFFFF.U)
        dut.io.in2.poke(0x0F0F.U)
        dut.io.out.expect(0xF0F0.U)
      }
    }

    "perform SLL correctly" in {
      implicit val config = CoreConfig()
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU_SLL)
        dut.io.in1.poke(1.U)
        dut.io.in2.poke(4.U)
        dut.io.out.expect(16.U)
      }
    }

    "perform SRL correctly" in {
      implicit val config = CoreConfig()
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU_SRL)
        dut.io.in1.poke(16.U)
        dut.io.in2.poke(2.U)
        dut.io.out.expect(4.U)
      }
    }

    "perform SRA correctly (sign extend)" in {
      implicit val config = CoreConfig()
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU_SRA)
        dut.io.in1.poke(0x80000000L.U)  // MSB set
        dut.io.in2.poke(4.U)
        dut.io.out.expect(0xF8000000L.U)  // Sign extended
      }
    }

    "perform SLT correctly (signed less than)" in {
      implicit val config = CoreConfig()
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU_SLT)
        // -5 < 3 should be true (1)
        // -5 as 32-bit two's complement
        val neg5 = BigInt("FFFFFFFFB", 16)  // Use BigInt to avoid negative
        dut.io.in1.poke(neg5.U)
        dut.io.in2.poke(3.U)
        dut.io.out.expect(1.U)
      }
    }

    "perform SLTU correctly (unsigned less than)" in {
      implicit val config = CoreConfig()
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU_SLTU)
        // 0xFFFFFFFF > 3 as unsigned
        dut.io.in1.poke(0xFFFFFFFFL.U)
        dut.io.in2.poke(3.U)
        dut.io.out.expect(0.U)
      }
    }
  }
}
