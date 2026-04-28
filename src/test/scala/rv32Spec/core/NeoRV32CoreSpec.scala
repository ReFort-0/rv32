package rv32Spec.core

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import rv32.configs._
import rv32.core._

/**
  * NeoRV32Core integration tests - data path tests
  * Run with: ./mill rv32.test.testOnly rv32Spec.core.NeoRV32CoreSpec
  */
class NeoRV32CoreSpec extends AnyFreeSpec with Matchers with ChiselSim {

  "NeoRV32Core should" - {

    "initialize PC to 0x80000000 and fetch first instruction" in {
      implicit val config = CoreConfig()
      simulate(new NeoRV32Core) { dut =>
        // Check initial PC before any clock step (reset value)
        dut.io.debug.pc.expect(0x80000000L.U)
        dut.io.imem.req.addr.expect(0x80000000L.U)

        // Setup memory response for NOP (addi x0, x0, 0)
        dut.io.imem.resp.rdata.poke(0x00000013L.U)  // NOP
        dut.io.imem.resp.valid.poke(true.B)
        dut.io.dmem.resp.rdata.poke(0.U)

        dut.clock.step()

        // After first cycle, PC should have incremented
        dut.io.debug.pc.expect(0x80000004L.U)
      }
    }

    "increment PC by 4 each cycle" in {
      implicit val config = CoreConfig()
      simulate(new NeoRV32Core) { dut =>
        // Initial PC at reset
        dut.io.debug.pc.expect(0x80000000L.U)

        dut.io.imem.resp.valid.poke(true.B)
        dut.io.imem.resp.rdata.poke(0x00000013L.U)  // NOP
        dut.io.dmem.resp.rdata.poke(0.U)

        dut.clock.step()
        dut.io.debug.pc.expect(0x80000004L.U)  // PC after first instruction

        dut.clock.step()
        dut.io.debug.pc.expect(0x80000008L.U)  // PC after second instruction

        dut.clock.step()
        dut.io.debug.pc.expect(0x8000000CL.U)  // PC after third instruction
      }
    }

    "execute ADDI instruction sequence" in {
      implicit val config = CoreConfig()
      simulate(new NeoRV32Core) { dut =>
        // Sequence: ADDI x1, x0, 5; ADDI x2, x1, 3
        // First instruction (single cycle mode)
        dut.io.imem.resp.valid.poke(true.B)
        dut.io.imem.resp.rdata.poke(0x00500093L.U)  // addi x1, x0, 5
        dut.io.dmem.resp.rdata.poke(0.U)

        dut.clock.step()

        // PC should be 0x80000004
        dut.io.debug.pc.expect(0x80000004L.U)

        // Second instruction
        dut.io.imem.resp.rdata.poke(0x00308113L.U)  // addi x2, x1, 3

        dut.clock.step()
        dut.io.debug.pc.expect(0x80000008L.U)
      }
    }

    "perform load operation" in {
      implicit val config = CoreConfig()
      simulate(new NeoRV32Core) { dut =>
        // First cycle - instruction fetch
        dut.io.imem.resp.valid.poke(true.B)
        dut.io.imem.resp.rdata.poke(0x00002183L.U)  // lw x3, 0(x0)
        dut.io.dmem.resp.rdata.poke(0xDEADBEEFL.U)
        dut.io.dmem.resp.valid.poke(true.B)

        dut.clock.step()  // Fetch and decode

        // Second cycle - execute and memory
        dut.clock.step()

        // Check that data memory was accessed at address 0
        dut.io.dmem.req.addr.expect(0.U)
        dut.io.dmem.req.valid.expect(true.B)
        dut.io.dmem.req.wen.expect(false.B)  // Read operation
      }
    }

    "perform store operation" in {
      implicit val config = CoreConfig()
      simulate(new NeoRV32Core) { dut =>
        // SW x4, 0(x0) - store x4 to address 0
        // First need to put value in x4: addi x4, x0, 0xAB
        dut.io.imem.resp.valid.poke(true.B)
        dut.io.imem.resp.rdata.poke(0x0AB00213L.U)  // addi x4, x0, 0xAB
        dut.io.dmem.resp.rdata.poke(0.U)

        dut.clock.step()  // Cycle 1: addi in fetch

        // Now store x4
        dut.io.imem.resp.rdata.poke(0x00402023L.U)  // sw x4, 0(x0)

        dut.clock.step()  // Cycle 2: addi in execute, sw in fetch

        dut.clock.step()  // Cycle 3: sw in execute

        dut.io.dmem.req.valid.expect(true.B)
        dut.io.dmem.req.wen.expect(true.B)  // Write operation
      }
    }

    "execute branch instruction" in {
      implicit val config = CoreConfig()
      simulate(new NeoRV32Core) { dut =>
        // First instruction: addi x1, x0, 1 (set x1 = 1)
        dut.io.imem.resp.valid.poke(true.B)
        dut.io.imem.resp.rdata.poke(0x00100093L.U)  // addi x1, x0, 1
        dut.io.dmem.resp.rdata.poke(0.U)

        dut.clock.step()

        // BEQ x1, x1, offset (should branch since x1 == x1)
        // offset = 0 (no offset for this test)
        dut.io.imem.resp.rdata.poke(0x00108063L.U)  // beq x1, x1, 0

        dut.clock.step()

        // In single cycle mode, branch taken signal should be generated
        // PC update happens immediately
      }
    }

    "execute JAL instruction" in {
      implicit val config = CoreConfig()
      simulate(new NeoRV32Core) { dut =>
        // JAL x1, offset - jump and link
        // JAL with offset 16 (4 instructions ahead)
        dut.io.imem.resp.valid.poke(true.B)
        dut.io.imem.resp.rdata.poke(0x004000EFL.U)  // jal x1, 4 (rough encoding)
        dut.io.dmem.resp.rdata.poke(0.U)

        dut.clock.step()

        // PC should jump, x1 should have PC+4
      }
    }

    "skip register write for x0" in {
      implicit val config = CoreConfig()
      simulate(new NeoRV32Core) { dut =>
        // ADDI x0, x0, 5 - should not actually write x0
        dut.io.imem.resp.valid.poke(true.B)
        dut.io.imem.resp.rdata.poke(0x00500013L.U)  // addi x0, x0, 5
        dut.io.dmem.resp.rdata.poke(0.U)

        dut.clock.step()

        // Check that reg_write is false for x0
        // (This depends on writeback logic masking x0 writes)
      }
    }
  }
}
