package rv32Spec.soc

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rv32.soc._
import rv32.configs._

class OnChipRAMSpec extends AnyFlatSpec with ChiselSim with Matchers {
  implicit val config: CoreConfig = CoreConfig()

  behavior of "OnChipRAM"

  it should "write and read back a word" in {
    simulate(new OnChipRAM(sizeBytes = 1024)) { dut =>
      // Write 0x12345678 to address 0x100
      dut.io.req.addr.poke(0x100.U)
      dut.io.req.wdata.poke(0x12345678L.U)
      dut.io.req.wen.poke(true.B)
      dut.io.req.valid.poke(true.B)
      dut.io.req.mask.poke(0xF.U)
      dut.clock.step()

      // Read back from address 0x100
      dut.io.req.addr.poke(0x100.U)
      dut.io.req.wen.poke(false.B)
      dut.io.req.valid.poke(true.B)
      dut.clock.step()

      // Wait one more cycle for read data to appear (SyncReadMem has 1-cycle latency)
      dut.clock.step()

      dut.io.resp.rdata.expect(0x12345678L.U)
      dut.io.resp.valid.expect(true.B)
    }
  }

  it should "handle multiple addresses independently" in {
    simulate(new OnChipRAM(sizeBytes = 1024)) { dut =>
      // Write different values to different addresses
      dut.io.req.addr.poke(0x000.U)
      dut.io.req.wdata.poke(0x11111111L.U)
      dut.io.req.wen.poke(true.B)
      dut.io.req.valid.poke(true.B)
      dut.io.req.mask.poke(0xF.U)
      dut.clock.step()

      dut.io.req.addr.poke(0x004.U)
      dut.io.req.wdata.poke(0x22222222L.U)
      dut.io.req.wen.poke(true.B)
      dut.io.req.valid.poke(true.B)
      dut.io.req.mask.poke(0xF.U)
      dut.clock.step()

      dut.io.req.addr.poke(0x008.U)
      dut.io.req.wdata.poke(0x33333333L.U)
      dut.io.req.wen.poke(true.B)
      dut.io.req.valid.poke(true.B)
      dut.io.req.mask.poke(0xF.U)
      dut.clock.step()

      // Read back and verify
      dut.io.req.wen.poke(false.B)
      dut.io.req.valid.poke(true.B)

      dut.io.req.addr.poke(0x000.U)
      dut.clock.step()
      dut.clock.step()
      dut.io.resp.rdata.expect(0x11111111L.U)

      dut.io.req.addr.poke(0x004.U)
      dut.clock.step()
      dut.clock.step()
      dut.io.resp.rdata.expect(0x22222222L.U)

      dut.io.req.addr.poke(0x008.U)
      dut.clock.step()
      dut.clock.step()
      dut.io.resp.rdata.expect(0x33333333L.U)
    }
  }

  it should "initialize to zero" in {
    simulate(new OnChipRAM(sizeBytes = 1024)) { dut =>
      // Read from unwritten address
      dut.io.req.addr.poke(0x400.U)
      dut.io.req.wen.poke(false.B)
      dut.io.req.valid.poke(true.B)
      dut.clock.step()

      dut.io.resp.rdata.expect(0.U)
    }
  }

  it should "not write when wen is false" in {
    simulate(new OnChipRAM(sizeBytes = 1024)) { dut =>
      // Write initial value
      dut.io.req.addr.poke(0x500.U)
      dut.io.req.wdata.poke(0xAAAAAAAAL.U)
      dut.io.req.wen.poke(true.B)
      dut.io.req.valid.poke(true.B)
      dut.io.req.mask.poke(0xF.U)
      dut.clock.step()

      // Try to write with wen=false
      dut.io.req.addr.poke(0x500.U)
      dut.io.req.wdata.poke(0xBBBBBBBBL.U)
      dut.io.req.wen.poke(false.B)
      dut.io.req.valid.poke(true.B)
      dut.io.req.mask.poke(0xF.U)
      dut.clock.step()

      // Read back - should still be 0xAAAAAAAA
      dut.io.req.addr.poke(0x500.U)
      dut.io.req.wen.poke(false.B)
      dut.io.req.valid.poke(true.B)
      dut.clock.step()

      dut.io.resp.rdata.expect(0xAAAAAAAAL.U)
    }
  }

  it should "not respond when valid is false" in {
    simulate(new OnChipRAM(sizeBytes = 1024)) { dut =>
      // Request with valid=false
      dut.io.req.addr.poke(0x100.U)
      dut.io.req.wen.poke(false.B)
      dut.io.req.valid.poke(false.B)
      dut.clock.step()

      dut.io.resp.valid.expect(false.B)
    }
  }
}
