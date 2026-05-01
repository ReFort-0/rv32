package rv32Spec.core

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import rv32.configs._
import rv32.core.util._

class MulDivUnitSpec extends AnyFreeSpec with Matchers with ChiselSim {
  import Constants._

  implicit val config: CoreConfig = CoreConfig(useM = true)

  "MulDivUnit" - {
    "MUL operations" - {
      "should multiply two positive numbers" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_MUL)
          dut.io.in1.poke(12.U)
          dut.io.in2.poke(13.U)
          dut.io.valid.poke(true.B)
          dut.clock.step()
          dut.io.out.expect(156.U)
          dut.io.ready.expect(true.B)
        }
      }

      "should handle multiplication by zero" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_MUL)
          dut.io.in1.poke(12345.U)
          dut.io.in2.poke(0.U)
          dut.io.valid.poke(true.B)
          dut.clock.step()
          dut.io.out.expect(0.U)
        }
      }

      "should handle negative numbers (signed multiply)" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_MUL)
          dut.io.in1.poke(0xFFFFFFFFL.U)  // -1
          dut.io.in2.poke(5.U)
          dut.io.valid.poke(true.B)
          dut.clock.step()
          dut.io.out.expect(0xFFFFFFFBL.U)  // -5
        }
      }

      "should handle overflow correctly (take lower 32 bits)" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_MUL)
          dut.io.in1.poke(0x80000000L.U)  // 2^31
          dut.io.in2.poke(2.U)
          dut.io.valid.poke(true.B)
          dut.clock.step()
          dut.io.out.expect(0.U)  // Lower 32 bits of 2^32
        }
      }
    }

    "MULH operations" - {
      "should return upper 32 bits of signed multiplication" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_MULH)
          dut.io.in1.poke(0x80000000L.U)  // -2^31
          dut.io.in2.poke(2.U)
          dut.io.valid.poke(true.B)
          dut.clock.step()
          dut.io.out.expect(0xFFFFFFFFL.U)  // Upper 32 bits (sign extended)
        }
      }

      "should handle positive × positive" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_MULH)
          dut.io.in1.poke(0x7FFFFFFFL.U)  // 2^31 - 1
          dut.io.in2.poke(2.U)
          dut.io.valid.poke(true.B)
          dut.clock.step()
          dut.io.out.expect(0.U)  // Upper 32 bits
        }
      }
    }

    "MULHU operations" - {
      "should return upper 32 bits of unsigned multiplication" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_MULHU)
          dut.io.in1.poke(0xFFFFFFFFL.U)  // Max unsigned
          dut.io.in2.poke(0xFFFFFFFFL.U)
          dut.io.valid.poke(true.B)
          dut.clock.step()
          dut.io.out.expect(0xFFFFFFFEL.U)  // Upper 32 bits
        }
      }
    }

    "MULHSU operations" - {
      "should handle signed × unsigned multiplication" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_MULHSU)
          dut.io.in1.poke(0xFFFFFFFFL.U)  // -1 (signed)
          dut.io.in2.poke(0xFFFFFFFFL.U)  // Max unsigned
          dut.io.valid.poke(true.B)
          dut.clock.step()
          dut.io.out.expect(0xFFFFFFFFL.U)  // Upper 32 bits
        }
      }
    }

    "DIV operations" - {
      "should divide two positive numbers" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_DIV)
          dut.io.in1.poke(100.U)
          dut.io.in2.poke(7.U)
          dut.io.valid.poke(true.B)

          // Wait for division to complete (32 cycles)
          var cycles = 0
          while (!dut.io.ready.peek().litToBoolean && cycles < 40) {
            dut.clock.step()
            cycles += 1
          }

          dut.io.out.expect(14.U)  // 100 / 7 = 14
        }
      }

      "should handle division by zero" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_DIV)
          dut.io.in1.poke(100.U)
          dut.io.in2.poke(0.U)
          dut.io.valid.poke(true.B)
          dut.clock.step()

          // Division by zero should return all 1s immediately
          dut.io.out.expect(0xFFFFFFFFL.U)
          dut.io.ready.expect(true.B)
        }
      }

      "should handle negative dividend" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_DIV)
          dut.io.in1.poke(0xFFFFFF9CL.U)  // -100
          dut.io.in2.poke(7.U)
          dut.io.valid.poke(true.B)

          var cycles = 0
          while (!dut.io.ready.peek().litToBoolean && cycles < 40) {
            dut.clock.step()
            cycles += 1
          }

          dut.io.out.expect(0xFFFFFFF2L.U)  // -14
        }
      }

      "should handle negative divisor" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_DIV)
          dut.io.in1.poke(100.U)
          dut.io.in2.poke(0xFFFFFFF9L.U)  // -7
          dut.io.valid.poke(true.B)

          var cycles = 0
          while (!dut.io.ready.peek().litToBoolean && cycles < 40) {
            dut.clock.step()
            cycles += 1
          }

          dut.io.out.expect(0xFFFFFFF2L.U)  // -14
        }
      }
    }

    "DIVU operations" - {
      "should perform unsigned division" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_DIVU)
          dut.io.in1.poke(0xFFFFFFFFL.U)  // Max unsigned
          dut.io.in2.poke(2.U)
          dut.io.valid.poke(true.B)

          var cycles = 0
          while (!dut.io.ready.peek().litToBoolean && cycles < 40) {
            dut.clock.step()
            cycles += 1
          }

          dut.io.out.expect(0x7FFFFFFFL.U)  // 2^32-1 / 2
        }
      }
    }

    "REM operations" - {
      "should return remainder of division" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_REM)
          dut.io.in1.poke(100.U)
          dut.io.in2.poke(7.U)
          dut.io.valid.poke(true.B)

          var cycles = 0
          while (!dut.io.ready.peek().litToBoolean && cycles < 40) {
            dut.clock.step()
            cycles += 1
          }

          dut.io.out.expect(2.U)  // 100 % 7 = 2
        }
      }

      "should handle division by zero (return dividend)" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_REM)
          dut.io.in1.poke(100.U)
          dut.io.in2.poke(0.U)
          dut.io.valid.poke(true.B)
          dut.clock.step()

          dut.io.out.expect(100.U)  // Return dividend
          dut.io.ready.expect(true.B)
        }
      }
    }

    "REMU operations" - {
      "should return unsigned remainder" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_REMU)
          dut.io.in1.poke(0xFFFFFFFFL.U)
          dut.io.in2.poke(10.U)
          dut.io.valid.poke(true.B)

          var cycles = 0
          while (!dut.io.ready.peek().litToBoolean && cycles < 40) {
            dut.clock.step()
            cycles += 1
          }

          dut.io.out.expect(5.U)  // (2^32-1) % 10 = 5
        }
      }
    }

    "Ready signal" - {
      "should be true for multiply operations immediately" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_MUL)
          dut.io.in1.poke(10.U)
          dut.io.in2.poke(20.U)
          dut.io.valid.poke(true.B)
          dut.clock.step()
          dut.io.ready.expect(true.B)
        }
      }

      "should be false during division, true when done" in {
        simulate(new MulDivUnit) { dut =>
          dut.io.op.poke(MULDIV_DIV)
          dut.io.in1.poke(100.U)
          dut.io.in2.poke(7.U)
          dut.io.valid.poke(false.B)
          dut.clock.step()
          dut.io.ready.expect(true.B)  // Idle, ready

          dut.io.valid.poke(true.B)
          dut.clock.step()
          dut.io.valid.poke(false.B)

          // Should be busy now
          var foundBusy = false
          for (_ <- 0 until 35) {
            if (!dut.io.ready.peek().litToBoolean) {
              foundBusy = true
            }
            dut.clock.step()
          }

          foundBusy shouldBe true
          dut.io.ready.expect(true.B)  // Should be done now
        }
      }
    }
  }
}
