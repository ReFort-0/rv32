package rv32Spec.core

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import rv32.configs._
import rv32.core.util.RegisterFile

class RegisterFileSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit val config: CoreConfig = CoreConfig()

  "RegisterFile" - {
    "should read zero from x0 register" in {
      simulate(new RegisterFile) { dut =>
        dut.io.rs1_addr.poke(0.U)
        dut.io.rs2_addr.poke(0.U)

        dut.io.rs1_data.expect(0.U)
        dut.io.rs2_data.expect(0.U)
      }
    }

    "should write and read back from non-zero registers" in {
      simulate(new RegisterFile) { dut =>
        dut.io.rd_addr.poke(5.U)
        dut.io.rd_data.poke(0xDEADBEEFL.U)
        dut.io.wen.poke(true.B)

        dut.clock.step(1)

        dut.io.wen.poke(false.B)
        dut.io.rs1_addr.poke(5.U)

        dut.io.rs1_data.expect(0xDEADBEEFL.U)
      }
    }

    "should not write when wen is false" in {
      simulate(new RegisterFile) { dut =>
        // First write a known value
        dut.io.rd_addr.poke(7.U)
        dut.io.rd_data.poke(0xAAAAAAAAL.U)
        dut.io.wen.poke(true.B)
        dut.clock.step(1)

        // Try to overwrite with wen=false
        dut.io.rd_addr.poke(7.U)
        dut.io.rd_data.poke(0x12345678L.U)
        dut.io.wen.poke(false.B)
        dut.clock.step(1)

        // Should still read the original value
        dut.io.rs1_addr.poke(7.U)
        dut.io.rs1_data.expect(0xAAAAAAAAL.U)
      }
    }

    "should never write to x0 register" in {
      simulate(new RegisterFile) { dut =>
        dut.io.rd_addr.poke(0.U)
        dut.io.rd_data.poke(0xFFFFFFFFL.U)
        dut.io.wen.poke(true.B)

        dut.clock.step(1)

        dut.io.wen.poke(false.B)
        dut.io.rs1_addr.poke(0.U)

        dut.io.rs1_data.expect(0.U)
      }
    }

    "should support simultaneous read from two different registers" in {
      simulate(new RegisterFile) { dut =>
        dut.io.rd_addr.poke(3.U)
        dut.io.rd_data.poke(0xAAAAAAAAL.U)
        dut.io.wen.poke(true.B)
        dut.clock.step(1)

        dut.io.rd_addr.poke(4.U)
        dut.io.rd_data.poke(0xBBBBBBBBL.U)
        dut.io.wen.poke(true.B)
        dut.clock.step(1)

        dut.io.wen.poke(false.B)
        dut.io.rs1_addr.poke(3.U)
        dut.io.rs2_addr.poke(4.U)

        dut.io.rs1_data.expect(0xAAAAAAAAL.U)
        dut.io.rs2_data.expect(0xBBBBBBBBL.U)
      }
    }

    "should support reading the same register on both ports" in {
      simulate(new RegisterFile) { dut =>
        dut.io.rd_addr.poke(10.U)
        dut.io.rd_data.poke(0xCAFEBABEL.U)
        dut.io.wen.poke(true.B)
        dut.clock.step(1)

        dut.io.wen.poke(false.B)
        dut.io.rs1_addr.poke(10.U)
        dut.io.rs2_addr.poke(10.U)

        dut.io.rs1_data.expect(0xCAFEBABEL.U)
        dut.io.rs2_data.expect(0xCAFEBABEL.U)
      }
    }

    "should handle write and read to the same register in consecutive cycles" in {
      simulate(new RegisterFile) { dut =>
        dut.io.rd_addr.poke(15.U)
        dut.io.rd_data.poke(0x11111111L.U)
        dut.io.wen.poke(true.B)
        dut.clock.step(1)

        dut.io.wen.poke(false.B)
        dut.io.rs1_addr.poke(15.U)

        dut.io.rs1_data.expect(0x11111111L.U)
      }
    }

    "should overwrite previous value when writing to the same register" in {
      simulate(new RegisterFile) { dut =>
        dut.io.rd_addr.poke(20.U)
        dut.io.rd_data.poke(0x12345678L.U)
        dut.io.wen.poke(true.B)
        dut.clock.step(1)

        dut.io.rd_addr.poke(20.U)
        dut.io.rd_data.poke(0x87654321L.U)
        dut.io.wen.poke(true.B)
        dut.clock.step(1)

        dut.io.wen.poke(false.B)
        dut.io.rs1_addr.poke(20.U)

        dut.io.rs1_data.expect(0x87654321L.U)
      }
    }

    "should maintain independence between different registers" in {
      simulate(new RegisterFile) { dut =>
        for (i <- 1 to 5) {
          dut.io.rd_addr.poke(i.U)
          dut.io.rd_data.poke((i * 0x11111111L).U)
          dut.io.wen.poke(true.B)
          dut.clock.step(1)
        }

        dut.io.wen.poke(false.B)
        for (i <- 1 to 5) {
          dut.io.rs1_addr.poke(i.U)
          dut.io.rs1_data.expect((i * 0x11111111L).U)
        }
      }
    }
  }
}
