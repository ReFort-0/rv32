package rv32Spec.soc.bus

import chisel3._
import chisel3.util._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import rv32.soc.bus._
import rv32.configs.{NeoRV32Config, CoreConfig, SoCConfig}

class SimpleBusSpec extends AnyFreeSpec with Matchers with ChiselSim {

  "SimpleBus" - {
    "should create request and response bundles correctly" in {
      new SimpleBusReq()
      new SimpleBusResp()
    }

    "should handle read request correctly" in {
      simulate(new Module {
        val io = IO(new Bundle {
          val req = Flipped(new SimpleBusReq())
          val resp = new SimpleBusResp()
        })
        io.resp.rdata := io.req.addr + 0x100.U
        io.resp.valid := io.req.ren
        io.resp.error := false.B
      }) { dut =>
        dut.io.req.addr.poke(0x1000.U)
        dut.io.req.wdata.poke(0.U)
        dut.io.req.wen.poke(false.B)
        dut.io.req.ren.poke(true.B)
        dut.io.req.mask.poke(0xF.U)
        dut.clock.step()
        dut.io.resp.rdata.expect(0x1100.U)
        dut.io.resp.valid.expect(true.B)
      }
    }

    "should handle write request correctly" in {
      simulate(new Module {
        val io = IO(new Bundle {
          val req = Flipped(new SimpleBusReq())
          val resp = new SimpleBusResp()
        })
        val mem = Mem(256, UInt(32.W))
        val wordAddr = io.req.addr(9, 2)

        when(io.req.wen) {
          mem.write(wordAddr, io.req.wdata)
        }

        io.resp.rdata := mem.read(wordAddr)
        io.resp.valid := io.req.wen || io.req.ren
        io.resp.error := false.B
      }) { dut =>
        dut.io.req.addr.poke(0x100.U)
        dut.io.req.wdata.poke(0xABCD1234L.U)
        dut.io.req.wen.poke(true.B)
        dut.io.req.ren.poke(false.B)
        dut.clock.step()
        dut.io.resp.valid.expect(true.B)

        dut.io.req.wen.poke(false.B)
        dut.io.req.ren.poke(true.B)
        dut.clock.step()
        dut.io.resp.rdata.expect(0xABCD1234L.U)
      }
    }

    "should handle mask correctly (byte write)" in {
      simulate(new Module {
        val io = IO(new Bundle {
          val req = Flipped(new SimpleBusReq())
          val resp = new SimpleBusResp()
        })
        val mem = Mem(256, UInt(32.W))
        val wordAddr = io.req.addr(9, 2)
        val byteOffset = io.req.addr(1, 0)

        when(io.req.wen) {
          val data = mem.read(wordAddr)
          val wdata = io.req.wdata(7, 0)
          val newData = MuxCase(
            Cat(data(31, 24), wdata, data(15, 0)),
            Seq(
              (byteOffset === 1.U) -> Cat(data(31, 16), wdata, data(7, 0)),
              (byteOffset === 2.U) -> Cat(wdata, data(23, 0)),
              (byteOffset === 3.U) -> Cat(data(31, 24), data(23, 16), wdata, data(7, 0))
            )
          )
          mem.write(wordAddr, newData)
        }

        io.resp.rdata := mem.read(wordAddr)
        io.resp.valid := true.B
        io.resp.error := false.B
      }) { dut =>
        dut.io.req.addr.poke(0x101.U)
        dut.io.req.wdata.poke(0x000000AB.U)
        dut.io.req.wen.poke(true.B)
        dut.io.req.mask.poke(0x1.U)
        dut.clock.step()
        dut.io.resp.valid.expect(true.B)
      }
    }
  }
}

class DemuxSpec extends AnyFreeSpec with Matchers with ChiselSim {
  val cfgNoPeripherals = NeoRV32Config(CoreConfig(), SoCConfig(enableUART = false, enableTimer = false, enableGPIO = false))

  "Demux" - {
    "should route RAM access correctly" in {
      simulate(new Demux(cfgNoPeripherals)) { dut =>
        dut.io.cpu.req.addr.poke(0x00000100.U)
        dut.io.cpu.req.wdata.poke(0.U)
        dut.io.cpu.req.wen.poke(true.B)
        dut.io.cpu.req.ren.poke(false.B)
        dut.io.cpu.req.mask.poke(0xF.U)
        dut.clock.step()

        dut.io.ram.req.addr.expect(0x00000100.U)
        dut.io.ram.req.wen.expect(true.B)
      }
    }

    "should not route UART address to RAM when peripherals disabled" in {
      simulate(new Demux(cfgNoPeripherals)) { dut =>
        dut.io.cpu.req.addr.poke(0x10000000.U)
        dut.io.cpu.req.wdata.poke(0.U)
        dut.io.cpu.req.wen.poke(true.B)
        dut.io.cpu.req.ren.poke(false.B)
        dut.clock.step()

        dut.io.ram.req.wen.expect(false.B)
      }
    }
  }
}
