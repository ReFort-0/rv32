package rv32Spec.core.util

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import rv32.core.util._
import rv32.configs.CoreConfig

class ALUSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit val conf: CoreConfig = new CoreConfig

  "ALU" - {
    "should perform ADD operation correctly" in {
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU.ADD)
        dut.io.in1.poke(5.U)
        dut.io.in2.poke(3.U)
        dut.clock.step()
        dut.io.out.expect(8.U)
      }
    }

    "should perform SUB operation correctly" in {
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU.SUB)
        dut.io.in1.poke(10.U)
        dut.io.in2.poke(4.U)
        dut.clock.step()
        dut.io.out.expect(6.U)
      }
    }

    "should perform AND operation correctly" in {
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU.AND)
        dut.io.in1.poke(0xFF00FF00L.U)
        dut.io.in2.poke(0x0FF00FF0L.U)
        dut.clock.step()
        dut.io.out.expect(0x0F000F00L.U)
      }
    }

    "should perform OR operation correctly" in {
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU.OR)
        dut.io.in1.poke(0xFF000000L.U)
        dut.io.in2.poke(0x000000FFL.U)
        dut.clock.step()
        dut.io.out.expect(0xFF0000FFL.U)
      }
    }

    "should perform XOR operation correctly" in {
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU.XOR)
        dut.io.in1.poke(0xFFFF0000L.U)
        dut.io.in2.poke(0x0000FFFFL.U)
        dut.clock.step()
        dut.io.out.expect(0xFFFFFFFFL.U)
      }
    }

    "should perform SLT operation correctly (signed comparison)" in {
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU.SLT)
        dut.io.in1.poke(5.U)
        dut.io.in2.poke(10.U)
        dut.clock.step()
        dut.io.out.expect(1.U)

        dut.io.op.poke(ALU.SLT)
        dut.io.in1.poke(10.U)
        dut.io.in2.poke(5.U)
        dut.clock.step()
        dut.io.out.expect(0.U)

        dut.io.op.poke(ALU.SLT)
        dut.io.in1.poke(0xFFFFFFFFL.U)
        dut.io.in2.poke(0.U)
        dut.clock.step()
        dut.io.out.expect(1.U)
      }
    }

    "should perform SLTU operation correctly (unsigned comparison)" in {
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU.SLTU)
        dut.io.in1.poke(5.U)
        dut.io.in2.poke(10.U)
        dut.clock.step()
        dut.io.out.expect(1.U)

        dut.io.op.poke(ALU.SLTU)
        dut.io.in1.poke(0xFFFFFFFFL.U)
        dut.io.in2.poke(0.U)
        dut.clock.step()
        dut.io.out.expect(0.U)
      }
    }

    "should perform SLL operation correctly" in {
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU.SLL)
        dut.io.in1.poke(1.U)
        dut.io.in2.poke(4.U)
        dut.clock.step()
        dut.io.out.expect(0x10.U)
      }
    }

    "should perform SRL operation correctly" in {
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU.SRL)
        dut.io.in1.poke(0x100.U)
        dut.io.in2.poke(4.U)
        dut.clock.step()
        dut.io.out.expect(0x10.U)
      }
    }

    "should perform SRA operation correctly (arithmetic shift)" in {
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU.SRA)
        dut.io.in1.poke(0x10.U)
        dut.io.in2.poke(2.U)
        dut.clock.step()
        dut.io.out.expect(0x4.U)

        dut.io.op.poke(ALU.SRA)
        dut.io.in1.poke(0x80000000L.U)
        dut.io.in2.poke(4.U)
        dut.clock.step()
        dut.io.out.expect(0xF8000000L.U)
      }
    }

    "should perform COPY1 operation correctly" in {
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU.COPY1)
        dut.io.in1.poke(42.U)
        dut.io.in2.poke(0.U)
        dut.clock.step()
        dut.io.out.expect(42.U)
      }
    }

    "should handle zero inputs correctly" in {
      simulate(new ALU) { dut =>
        dut.io.op.poke(ALU.ADD)
        dut.io.in1.poke(0.U)
        dut.io.in2.poke(0.U)
        dut.clock.step()
        dut.io.out.expect(0.U)
      }
    }
  }
}
