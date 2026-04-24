package rv32Spec.core.util

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import rv32.core.util._
import rv32.configs.CoreConfig

class RegisterFileSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit val conf: CoreConfig = new CoreConfig

  "RegisterFile" - {
    "should read x0 as 0 regardless of writes" in {
      simulate(new RegisterFile) { dut =>
        dut.io.rd_addr.poke(0.U)
        dut.io.rd_data.poke(42.U)
        dut.io.wen.poke(true.B)
        dut.clock.step()

        dut.io.rs1_addr.poke(0.U)
        dut.clock.step()
        dut.io.rs1_data.expect(0.U)
      }
    }

    "should write and read from registers correctly" in {
      simulate(new RegisterFile) { dut =>
        dut.io.rd_addr.poke(5.U)
        dut.io.rd_data.poke(100.U)
        dut.io.wen.poke(true.B)
        dut.clock.step()

        dut.io.rs1_addr.poke(5.U)
        dut.io.rs2_addr.poke(5.U)
        dut.clock.step()
        dut.io.rs1_data.expect(100.U)
        dut.io.rs2_data.expect(100.U)
      }
    }

    "should not write when wen is false" in {
      simulate(new RegisterFile) { dut =>
        dut.io.rd_addr.poke(3.U)
        dut.io.rd_data.poke(200.U)
        dut.io.wen.poke(true.B)
        dut.clock.step()

        dut.io.rd_data.poke(300.U)
        dut.io.wen.poke(false.B)
        dut.clock.step()

        dut.io.rs1_addr.poke(3.U)
        dut.clock.step()
        dut.io.rs1_data.expect(200.U)
      }
    }

    "should read different registers from rs1 and rs2 ports" in {
      simulate(new RegisterFile) { dut =>
        dut.io.rd_addr.poke(10.U)
        dut.io.rd_data.poke(1000.U)
        dut.io.wen.poke(true.B)
        dut.clock.step()

        dut.io.rd_addr.poke(11.U)
        dut.io.rd_data.poke(2000.U)
        dut.io.wen.poke(true.B)
        dut.clock.step()

        dut.io.rs1_addr.poke(10.U)
        dut.io.rs2_addr.poke(11.U)
        dut.clock.step()
        dut.io.rs1_data.expect(1000.U)
        dut.io.rs2_data.expect(2000.U)
      }
    }

    "should handle simultaneous read and write to same register" in {
      simulate(new RegisterFile) { dut =>
        dut.io.rd_addr.poke(7.U)
        dut.io.rd_data.poke(500.U)
        dut.io.wen.poke(true.B)
        dut.clock.step()

        dut.io.rs1_addr.poke(7.U)
        dut.io.rd_data.poke(600.U)
        dut.clock.step()
        dut.io.rs1_data.expect(600.U)
      }
    }

    "should handle all registers in RV32I" in {
      simulate(new RegisterFile) { dut =>
        for (reg <- 1 to 31) {
          dut.io.rd_addr.poke(reg.U)
          dut.io.rd_data.poke((reg * 100).U)
          dut.io.wen.poke(true.B)
          dut.clock.step()

          dut.io.rs1_addr.poke(reg.U)
          dut.clock.step()
          dut.io.rs1_data.expect((reg * 100).U)
        }
      }
    }

    "should handle RV32E register file (x0-x15)" in {
      simulate(new RegisterFile()(new CoreConfig { override val useRV32E = true })) { dut =>
        for (reg <- 1 to 15) {
          dut.io.rd_addr.poke(reg.U)
          dut.io.rd_data.poke((reg * 10).U)
          dut.io.wen.poke(true.B)
          dut.clock.step()

          dut.io.rs1_addr.poke(reg.U)
          dut.clock.step()
          dut.io.rs1_data.expect((reg * 10).U)
        }
      }
    }

    "should handle sequential writes and reads" in {
      simulate(new RegisterFile) { dut =>
        val values = Seq(10, 20, 30, 40, 50)
        for ((value, idx) <- values.zipWithIndex) {
          dut.io.rd_addr.poke((idx + 1).U)
          dut.io.rd_data.poke(value.U)
          dut.io.wen.poke(true.B)
          dut.clock.step()
        }

        for ((value, idx) <- values.reverse.zipWithIndex) {
          dut.io.rs1_addr.poke((values.length - idx).U)
          dut.clock.step()
          dut.io.rs1_data.expect(value.U)
        }
      }
    }
  }
}
