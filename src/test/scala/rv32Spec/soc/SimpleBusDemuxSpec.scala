package rv32Spec.soc

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import rv32.soc._
import rv32.configs._

class SimpleBusDemuxSpec extends AnyFlatSpec with ChiselSim with Matchers {
  implicit val config: CoreConfig = CoreConfig()

  behavior of "SimpleBusDemux"

  it should "route requests to correct slave based on address ranges" in {
    simulate(new SimpleBusDemux(nSlaves = 2)) { dut =>

      // Configure address ranges
      // Slave 0: 0x00000000 - 0x10000000 (RAM)
      dut.io.addrRanges(0).start.poke(0x00000000L.U)
      dut.io.addrRanges(0).end.poke(0x10000000L.U)

      // Slave 1: 0x10000000 - 0x20000000 (Peripheral)
      dut.io.addrRanges(1).start.poke(0x10000000L.U)
      dut.io.addrRanges(1).end.poke(0x20000000L.U)

      // Test routing to slave 0 (RAM at 0x00001000)
      dut.io.master.req.addr.poke(0x00001000L.U)
      dut.io.master.req.valid.poke(true.B)
      dut.io.master.req.wen.poke(false.B)
      dut.io.master.req.wdata.poke(0.U)
      dut.io.master.req.mask.poke(0xF.U)

      dut.clock.step()

      dut.io.slaves(0).req.valid.expect(true.B)
      dut.io.slaves(1).req.valid.expect(false.B)

      // Test routing to slave 1 (Peripheral at 0x10000004)
      dut.io.master.req.addr.poke(0x10000004L.U)
      dut.io.master.req.valid.poke(true.B)

      dut.clock.step()

      dut.io.slaves(0).req.valid.expect(false.B)
      dut.io.slaves(1).req.valid.expect(true.B)
    }
  }

  it should "multiplex responses from slaves" in {
    simulate(new SimpleBusDemux(nSlaves = 2)) { dut =>

      // Configure address ranges
      dut.io.addrRanges(0).start.poke(0x00000000L.U)
      dut.io.addrRanges(0).end.poke(0x10000000L.U)
      dut.io.addrRanges(1).start.poke(0x10000000L.U)
      dut.io.addrRanges(1).end.poke(0x20000000L.U)

      // Setup slave 0 response
      dut.io.slaves(0).resp.valid.poke(true.B)
      dut.io.slaves(0).resp.rdata.poke(0xDEADBEEFL.U)

      // Setup slave 1 response
      dut.io.slaves(1).resp.valid.poke(false.B)
      dut.io.slaves(1).resp.rdata.poke(0.U)

      // Request to slave 0
      dut.io.master.req.addr.poke(0x00001000L.U)
      dut.io.master.req.valid.poke(true.B)

      dut.clock.step()

      dut.io.master.resp.valid.expect(true.B)
      dut.io.master.resp.rdata.expect(0xDEADBEEFL.U)
    }
  }
}
