package rv32Spec.core

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import rv32.configs._
import rv32.core.stage._
import rv32.core.util.Constants._

/**
  * FetchStage unit tests
  * Run with: ./mill rv32.test.testOnly rv32Spec.core.FetchStageSpec
  */
class FetchStageSpec extends AnyFreeSpec with Matchers with ChiselSim {

  "FetchStage should" - {

    "initialize PC to 0x80000000 and increment after first cycle" in {
      implicit val config = CoreConfig()
      simulate(new FetchStage) { dut =>
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)
        dut.io.pc_we.poke(false.B)
        // PC starts at 0x80000000, after first clock it becomes 0x80000004
        dut.clock.step()
        dut.io.pc.expect(0x80000004L.U)
        dut.io.inst_req.addr.expect(0x80000004L.U)
      }
    }

    "increment PC by 4 each cycle when not stalled" in {
      implicit val config = CoreConfig()
      simulate(new FetchStage) { dut =>
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)
        dut.io.pc_we.poke(false.B)

        // First cycle: PC goes from 0x80000000 -> 0x80000004
        dut.clock.step()
        dut.io.pc.expect(0x80000004L.U)

        // Second cycle: PC goes to 0x80000008
        dut.clock.step()
        dut.io.pc.expect(0x80000008L.U)

        // Third cycle: PC goes to 0x8000000C
        dut.clock.step()
        dut.io.pc.expect(0x8000000CL.U)
      }
    }

    "stall PC when stall signal is high" in {
      implicit val config = CoreConfig()
      simulate(new FetchStage) { dut =>
        dut.io.stall.poke(true.B)
        dut.io.flush.poke(false.B)
        dut.io.pc_we.poke(false.B)

        dut.clock.step()
        dut.io.pc.expect(0x80000000L.U)

        dut.clock.step()
        dut.io.pc.expect(0x80000000L.U)  // PC unchanged
      }
    }

    "take branch when pc_we is high" in {
      implicit val config = CoreConfig()
      simulate(new FetchStage) { dut =>
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)
        dut.io.pc_we.poke(false.B)

        // First cycle: PC starts at 0x80000000, becomes 0x80000004
        dut.clock.step()
        dut.io.pc.expect(0x80000004L.U)

        // Branch to new PC
        dut.io.pc_we.poke(true.B)
        dut.io.pc_next.poke(0x80000100L.U)
        dut.clock.step()

        // PC should now be at branch target
        dut.io.pc.expect(0x80000100L.U)
      }
    }

    "capture instruction from response" in {
      implicit val config = CoreConfig()
      simulate(new FetchStage) { dut =>
        val testInst = 0x12345678L

        dut.io.inst_resp.valid.poke(true.B)
        dut.io.inst_resp.rdata.poke(testInst.U)
        dut.io.flush.poke(false.B)
        dut.io.stall.poke(false.B)
        dut.io.pc_we.poke(false.B)

        // First cycle: PC increments
        dut.clock.step()

        // Now check output
        dut.io.ifid.inst.expect(testInst.U)
        // PC is now at 0x80000004 after first cycle
        dut.io.ifid.pc.expect(0x80000004L.U)
        dut.io.ifid.valid.expect(true.B)
      }
    }

    "clear valid on flush" in {
      implicit val config = CoreConfig()
      simulate(new FetchStage) { dut =>
        dut.io.inst_resp.valid.poke(true.B)
        dut.io.flush.poke(true.B)

        dut.clock.step()

        dut.io.ifid.valid.expect(false.B)
      }
    }
  }
}
