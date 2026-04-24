package rv32Spec.soc.peripherals

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import rv32.soc.peripherals._
import rv32.configs.CoreConfig
import rv32.configs.SoCConfig

class OnChipRAMSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit val conf: CoreConfig = new CoreConfig

  "OnChipRAM" - {
    "should write and read a word at address 0" in {
      simulate(new OnChipRAM(1024)) { dut =>
        dut.io.bus.req.addr.poke(0x0.U)
        dut.io.bus.req.wdata.poke(0x12345678L.U)
        dut.io.bus.req.wen.poke(true.B)
        dut.io.bus.req.ren.poke(false.B)
        dut.clock.step()

        dut.io.bus.req.wen.poke(false.B)
        dut.io.bus.req.ren.poke(true.B)
        dut.clock.step()
        dut.io.bus.resp.rdata.expect(0x12345678L.U)
        dut.io.bus.resp.valid.expect(true.B)
      }
    }

    "should write and read multiple words at different addresses" in {
      simulate(new OnChipRAM(1024)) { dut =>
        dut.io.bus.req.wen.poke(true.B)
        dut.io.bus.req.ren.poke(false.B)

        dut.io.bus.req.addr.poke(0x00.U)
        dut.io.bus.req.wdata.poke(0xAAAAL.U)
        dut.clock.step()

        dut.io.bus.req.addr.poke(0x04.U)
        dut.io.bus.req.wdata.poke(0xBBBBBBBBL.U)
        dut.clock.step()

        dut.io.bus.req.addr.poke(0x08.U)
        dut.io.bus.req.wdata.poke(0xCCCCCCCCL.U)
        dut.clock.step()

        dut.io.bus.req.wen.poke(false.B)
        dut.io.bus.req.ren.poke(true.B)

        dut.io.bus.req.addr.poke(0x00.U)
        dut.clock.step()
        dut.io.bus.resp.rdata.expect(0xAAAAL.U)

        dut.io.bus.req.addr.poke(0x04.U)
        dut.clock.step()
        dut.io.bus.resp.rdata.expect(0xBBBBBBBBL.U)

        dut.io.bus.req.addr.poke(0x08.U)
        dut.clock.step()
        dut.io.bus.resp.rdata.expect(0xCCCCCCCCL.U)
      }
    }

    "should report error for out-of-range access" in {
      simulate(new OnChipRAM(256)) { dut =>
        dut.io.bus.req.addr.poke(0x200.U)
        dut.io.bus.req.wen.poke(false.B)
        dut.io.bus.req.ren.poke(true.B)
        dut.clock.step()
        dut.io.bus.resp.error.expect(true.B)
      }
    }

    "should handle back-to-back writes and reads" in {
      simulate(new OnChipRAM(512)) { dut =>
        for (i <- 0 until 10) {
          dut.io.bus.req.addr.poke((i * 4).U)
          dut.io.bus.req.wdata.poke((0x1000 + i).U)
          dut.io.bus.req.wen.poke(true.B)
          dut.io.bus.req.ren.poke(false.B)
          dut.clock.step()

          dut.io.bus.req.wen.poke(false.B)
          dut.io.bus.req.ren.poke(true.B)
          dut.clock.step()
          dut.io.bus.resp.rdata.expect((0x1000 + i).U)
        }
      }
    }
  }
}

class UARTSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit val conf: SoCConfig = new SoCConfig

  "UART" - {
    "should write TX data and update valid signal" in {
      simulate(new UART) { dut =>
        dut.io.bus.req.addr.poke(0x10000000.U)
        dut.io.bus.req.wdata.poke(0x55.U)
        dut.io.bus.req.wen.poke(true.B)
        dut.io.bus.req.ren.poke(false.B)
        dut.clock.step()

        dut.io.tx.expect(true.B)
        dut.io.bus.resp.valid.expect(true.B)
      }
    }

    "should read back TX data" in {
      simulate(new UART) { dut =>
        dut.io.bus.req.addr.poke(0x10000000.U)
        dut.io.bus.req.wdata.poke(0xAB.U)
        dut.io.bus.req.wen.poke(true.B)
        dut.clock.step()

        dut.io.bus.req.wen.poke(false.B)
        dut.io.bus.req.ren.poke(true.B)
        dut.clock.step()
        dut.io.bus.resp.rdata.expect(0xAB.U)
      }
    }

    "should handle multiple writes" in {
      simulate(new UART) { dut =>
        dut.io.bus.req.addr.poke(0x10000000.U)
        dut.io.bus.req.wdata.poke(0x55.U)
        dut.io.bus.req.wen.poke(true.B)
        dut.clock.step()

        dut.io.bus.req.wdata.poke(0x66.U)
        dut.clock.step()

        dut.io.bus.req.wen.poke(false.B)
        dut.io.bus.req.ren.poke(true.B)
        dut.clock.step()
        dut.io.bus.resp.rdata.expect(0x66.U)
      }
    }
  }
}

class TimerSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit val conf: SoCConfig = new SoCConfig

  "Timer" - {
    "should write and read counter low register" in {
      simulate(new Timer) { dut =>
        dut.io.bus.req.addr.poke(0x20000000.U)
        dut.io.bus.req.wdata.poke(0x12345678L.U)
        dut.io.bus.req.wen.poke(true.B)
        dut.io.bus.req.ren.poke(false.B)
        dut.clock.step()

        dut.io.bus.req.wen.poke(false.B)
        dut.io.bus.req.ren.poke(true.B)
        dut.clock.step()
        dut.io.bus.resp.valid.expect(true.B)
      }
    }

    "should write and read compare registers" in {
      simulate(new Timer) { dut =>
        dut.io.bus.req.addr.poke(0x20000008.U)
        dut.io.bus.req.wdata.poke(0xDEADBEEFL.U)
        dut.io.bus.req.wen.poke(true.B)
        dut.clock.step()

        dut.io.bus.req.wen.poke(false.B)
        dut.io.bus.req.ren.poke(true.B)
        dut.clock.step()
        dut.io.bus.resp.rdata.expect(0xDEADBEEFL.U)
      }
    }

    "should generate interrupt when counter matches compare" in {
      simulate(new Timer) { dut =>
        dut.io.bus.req.addr.poke(0x20000008.U)
        dut.io.bus.req.wdata.poke(10.U)
        dut.io.bus.req.wen.poke(true.B)
        dut.clock.step()

        dut.io.bus.req.addr.poke(0x20000010.U)
        dut.io.bus.req.wdata.poke(1.U)
        dut.io.bus.req.wen.poke(true.B)
        dut.clock.step()
      }
    }

    "should increment counter automatically" in {
      simulate(new Timer) { dut =>
        dut.io.bus.req.addr.poke(0x20000000.U)
        dut.io.bus.req.wdata.poke(0.U)
        dut.io.bus.req.wen.poke(true.B)
        dut.clock.step()

        dut.io.bus.req.wen.poke(false.B)
        dut.io.bus.req.ren.poke(true.B)

        dut.clock.step()
        dut.io.bus.resp.rdata.expect(1.U)

        dut.clock.step()
        dut.io.bus.resp.rdata.expect(2.U)

        dut.clock.step()
        dut.io.bus.resp.rdata.expect(3.U)
      }
    }
  }
}

class GPIOSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit val conf: SoCConfig = new SoCConfig

  "GPIO" - {
    "should write and read data register" in {
      simulate(new GPIO) { dut =>
        dut.io.bus.req.addr.poke(0x30000000.U)
        dut.io.bus.req.wdata.poke(0xF0F0F0F0L.U)
        dut.io.bus.req.wen.poke(true.B)
        dut.io.bus.req.ren.poke(false.B)
        dut.clock.step()

        dut.io.pins_out.expect(0xF0F0F0F0L.U)

        dut.io.bus.req.wen.poke(false.B)
        dut.io.bus.req.ren.poke(true.B)
        dut.clock.step()
        dut.io.bus.resp.rdata.expect(0xF0F0F0F0L.U)
      }
    }

    "should write and read direction register" in {
      simulate(new GPIO) { dut =>
        dut.io.bus.req.addr.poke(0x30000004.U)
        dut.io.bus.req.wdata.poke(0xFFFFFFFFL.U)
        dut.io.bus.req.wen.poke(true.B)
        dut.clock.step()

        dut.io.bus.req.wen.poke(false.B)
        dut.io.bus.req.ren.poke(true.B)
        dut.clock.step()
        dut.io.bus.resp.rdata.expect(0xFFFFFFFFL.U)
      }
    }

    "should handle partial updates to data register" in {
      simulate(new GPIO) { dut =>
        dut.io.bus.req.addr.poke(0x30000000.U)
        dut.io.bus.req.wdata.poke(0x00000055.U)
        dut.io.bus.req.wen.poke(true.B)
        dut.clock.step()
        dut.io.pins_out.expect(0x55.U)

        dut.io.bus.req.wdata.poke(0x000000AA.U)
        dut.clock.step()
        dut.io.pins_out.expect(0xAA.U)
      }
    }
  }
}
