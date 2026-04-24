package rv32Spec.core.util

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import rv32.core.util._
import rv32.configs.CoreConfig

class ImmGenSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit val conf: CoreConfig = new CoreConfig

  "ImmGen" - {
    // Helper to build I-type instruction word (imm in bits [31:20])
    def iInst(imm: Long): UInt = ((imm & 0xFFF) << 20).U

    // Helper to build S-type instruction word
    def sInst(imm11_5: Long, imm4_0: Long): UInt = {
      ((imm11_5 & 0x7F) << 25) | ((imm4_0 & 0x1F) << 7)
    }.U

    // Helper to build B-type instruction word from byte offset
    // B-type: inst[31]=imm12, inst[30:25]=imm10_5, inst[11:8]=imm4_1, inst[7]=imm11
    def bInst(offset: Int): UInt = {
      val imm12 = (offset >> 12) & 1
      val imm11 = (offset >> 11) & 1
      val imm10_5 = (offset >> 5) & 0x3F
      val imm4_1 = (offset >> 1) & 0xF
      ((imm12 & 1L) << 31) | ((imm10_5 & 0x3FL) << 25) | ((imm4_1 & 0xFL) << 8) | ((imm11 & 1L) << 7)
    }.U

    // Helper to build J-type instruction word from byte offset
    // J-type: inst[31]=imm20, inst[30:21]=imm10_1, inst[20]=imm11, inst[19:12]=imm19_12
    def jInst(offset: Int): UInt = {
      val imm20 = (offset >> 20) & 1
      val imm19_12 = (offset >> 12) & 0xFF
      val imm11 = (offset >> 11) & 1
      val imm10_1 = (offset >> 1) & 0x3FF
      ((imm20 & 1L) << 31) | ((imm10_1 & 0x3FFL) << 21) | ((imm11 & 1L) << 20) | ((imm19_12 & 0xFFL) << 12)
    }.U

    // Helper to build U-type instruction word
    def uInst(imm31_12: Long): UInt = ((imm31_12 & 0xFFFFF) << 12).U

    "should generate I-type immediate correctly" in {
      simulate(new ImmGen) { dut =>
        dut.io.inst.poke(iInst(123))
        dut.io.sel.poke(Constants.IMM_I)
        dut.clock.step()
        dut.io.out.expect(123.U)
      }
    }

    "should generate I-type immediate with negative sign extension" in {
      simulate(new ImmGen) { dut =>
        dut.io.inst.poke(iInst(0xFF5))
        dut.io.sel.poke(Constants.IMM_I)
        dut.clock.step()
        dut.io.out.expect(0xFFFFFFF5L.U)
      }
    }

    "should generate S-type immediate correctly" in {
      simulate(new ImmGen) { dut =>
        dut.io.inst.poke(sInst(1, 5))
        dut.io.sel.poke(Constants.IMM_S)
        dut.clock.step()
        dut.io.out.expect(37.U)
      }
    }

    "should generate S-type immediate with negative sign extension" in {
      simulate(new ImmGen) { dut =>
        dut.io.inst.poke(sInst(0x7F, 0x10))
        dut.io.sel.poke(Constants.IMM_S)
        dut.clock.step()
        dut.io.out.expect(0xFFFFFFF0L.U)
      }
    }

    "should generate B-type immediate correctly" in {
      simulate(new ImmGen) { dut =>
        dut.io.inst.poke(bInst(16))
        dut.io.sel.poke(Constants.IMM_B)
        dut.clock.step()
        dut.io.out.expect(16.U)
      }
    }

    "should generate B-type immediate with negative sign extension" in {
      simulate(new ImmGen) { dut =>
        dut.io.inst.poke(bInst(-16))
        dut.io.sel.poke(Constants.IMM_B)
        dut.clock.step()
        dut.io.out.expect(0xFFFFFFF0L.U)
      }
    }

    "should generate U-type immediate correctly" in {
      simulate(new ImmGen) { dut =>
        dut.io.inst.poke(uInst(0x12345))
        dut.io.sel.poke(Constants.IMM_U)
        dut.clock.step()
        dut.io.out.expect((0x12345L << 12).U)
      }
    }

    "should generate U-type immediate for 0x80000" in {
      simulate(new ImmGen) { dut =>
        dut.io.inst.poke(uInst(0x80000))
        dut.io.sel.poke(Constants.IMM_U)
        dut.clock.step()
        dut.io.out.expect(0x80000000L.U)
      }
    }

    "should generate J-type immediate correctly" in {
      simulate(new ImmGen) { dut =>
        dut.io.inst.poke(jInst(0x400))
        dut.io.sel.poke(Constants.IMM_J)
        dut.clock.step()
        dut.io.out.expect(0x400.U)
      }
    }

    "should generate J-type immediate with negative sign extension" in {
      simulate(new ImmGen) { dut =>
        dut.io.inst.poke(jInst(-4))
        dut.io.sel.poke(Constants.IMM_J)
        dut.clock.step()
        dut.io.out.expect(0xFFFFFFFCL.U)
      }
    }

    "should handle zero immediate" in {
      simulate(new ImmGen) { dut =>
        dut.io.inst.poke(iInst(0))
        dut.io.sel.poke(Constants.IMM_I)
        dut.clock.step()
        dut.io.out.expect(0.U)
      }
    }

    "should handle maximum positive I-type immediate" in {
      simulate(new ImmGen) { dut =>
        dut.io.inst.poke(iInst(0x7FF))
        dut.io.sel.poke(Constants.IMM_I)
        dut.clock.step()
        dut.io.out.expect(2047.U)
      }
    }

    "should handle maximum negative I-type immediate" in {
      simulate(new ImmGen) { dut =>
        dut.io.inst.poke(iInst(0xFFF))
        dut.io.sel.poke(Constants.IMM_I)
        dut.clock.step()
        dut.io.out.expect(0xFFFFFFFFL.U)
      }
    }
  }
}
