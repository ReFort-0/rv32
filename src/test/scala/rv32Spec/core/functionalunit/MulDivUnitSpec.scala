package rv32Spec.core.functionalunit

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import rv32.core.functionalunit.MulDivUnit
import rv32.configs.CoreConfig

class MulDivUnitSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit val conf: CoreConfig = new CoreConfig(useM = true)

  "MulDivUnit" - {
    def waitForResult(dut: MulDivUnit, expected: Long): Unit = {
      dut.io.in_valid.poke(true.B)
      var done = false
      var cycles = 0
      while (!done && cycles < 10) {
        dut.clock.step()
        cycles += 1
        done = dut.io.out_valid.peekValue().asBigInt == 1
      }
      require(done, s"MulDivUnit timeout after ${cycles} cycles")
      dut.io.result.expect(expected.U)
    }

    "should perform MUL (signed * signed, low bits)" in {
      simulate(new MulDivUnit) { dut =>
        dut.io.op.poke(0.U) // MUL
        dut.io.a.poke(16.U)
        dut.io.b.poke(4.U)
        waitForResult(dut, 64)
      }
    }

    "should perform MUL with large operands" in {
      simulate(new MulDivUnit) { dut =>
        dut.io.op.poke(0.U) // MUL
        dut.io.a.poke(256.U)
        dut.io.b.poke(256.U)
        waitForResult(dut, 65536)
      }
    }

    "should handle zero operands" in {
      simulate(new MulDivUnit) { dut =>
        dut.io.op.poke(0.U) // MUL
        dut.io.a.poke(0.U)
        dut.io.b.poke(123.U)
        waitForResult(dut, 0)
      }
    }

    "should chain multiple multiplications" in {
      simulate(new MulDivUnit) { dut =>
        dut.io.op.poke(0.U) // MUL
        dut.io.a.poke(2.U)
        dut.io.b.poke(3.U)
        waitForResult(dut, 6)

        dut.io.a.poke(4.U)
        dut.io.b.poke(5.U)
        waitForResult(dut, 20)

        dut.io.a.poke(10.U)
        dut.io.b.poke(10.U)
        waitForResult(dut, 100)
      }
    }
  }
}
