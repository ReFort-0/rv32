package rv32Spec.core

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import rv32.configs._
import rv32.core.util._
import rv32.core.util.Constants._

/**
  * ImmGen unit tests - tests immediate generation for all RISC-V immediate types
  * Run with: ./mill rv32.test.testOnly rv32Spec.core.ImmGenSpec
  */
class ImmGenSpec extends AnyFreeSpec with Matchers with ChiselSim {

  "ImmGen should" - {

    "generate I-type immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // ADDI x5, x10, 100 -> imm = 100
        val inst = (100 << 20) | (10 << 15) | (0 << 12) | (5 << 7) | 0x13
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_I)
        dut.io.out.expect(100.U)
      }
    }

    "generate I-type negative immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // ADDI x5, x10, -100 -> imm = 0xFFFFFF9C
        val inst = (0xF9CL << 20) | (10L << 15) | (0L << 12) | (5L << 7) | 0x13L
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_I)
        dut.io.out.expect(0xFFFFFF9CL.U)
      }
    }

    "generate I-type maximum positive immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // imm = 2047 (0x7FF)
        val inst = (0x7FF << 20) | (1 << 15) | (0 << 12) | (2 << 7) | 0x13
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_I)
        dut.io.out.expect(2047.U)
      }
    }

    "generate I-type maximum negative immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // imm = -2048 (0x800 sign-extended)
        val inst = (0x800L << 20) | (1L << 15) | (0L << 12) | (2L << 7) | 0x13L
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_I)
        dut.io.out.expect(0xFFFFF800L.U)
      }
    }

    "generate S-type immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // SW x10, 100(x5) -> imm = 100 = 0x64
        // imm[11:5] = 0x03, imm[4:0] = 0x04
        val inst = (0x03 << 25) | (10 << 20) | (5 << 15) | (2 << 12) | (0x04 << 7) | 0x23
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_S)
        dut.io.out.expect(100.U)
      }
    }

    "generate S-type negative immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // SW x10, -100(x5) -> imm = -100 = 0xF9C
        // imm[11:5] = 0x7C, imm[4:0] = 0x1C
        val inst = (0x7CL << 25) | (10L << 20) | (5L << 15) | (2L << 12) | (0x1CL << 7) | 0x23L
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_S)
        dut.io.out.expect(0xFFFFFF9CL.U)
      }
    }

    "generate B-type immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // BEQ x1, x2, 256 -> imm = 256 = 0x100
        // imm[12] = 0, imm[10:5] = 0x08, imm[4:1] = 0x0, imm[11] = 0
        val inst = (0 << 31) | (0x08 << 25) | (2 << 20) | (1 << 15) | (0 << 12) | (0 << 8) | (0 << 7) | 0x63
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_B)
        dut.io.out.expect(256.U)
      }
    }

    "generate B-type negative immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // BEQ x1, x2, -256 -> imm = -256 = 0xF00
        // imm[12] = 1, imm[10:5] = 0x38, imm[4:1] = 0x0, imm[11] = 1
        val inst = (1L << 31) | (0x38L << 25) | (2L << 20) | (1L << 15) | (0L << 12) | (0L << 8) | (1L << 7) | 0x63L
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_B)
        dut.io.out.expect(0xFFFFFF00L.U)
      }
    }

    "generate B-type immediate with alignment correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // BEQ x3, x4, 8 -> imm = 8 = 0x08
        // imm[12] = 0, imm[10:5] = 0x00, imm[4:1] = 0x4, imm[11] = 0
        val inst = (0 << 31) | (0x00 << 25) | (4 << 20) | (3 << 15) | (0 << 12) | (0x4 << 8) | (0 << 7) | 0x63
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_B)
        dut.io.out.expect(8.U)
      }
    }

    "generate U-type immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // LUI x5, 0x12345 -> imm = 0x12345000
        val inst = (0x12345 << 12) | (5 << 7) | 0x37
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_U)
        dut.io.out.expect(0x12345000.U)
      }
    }

    "generate U-type immediate with sign bit correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // LUI x5, 0x80000 -> imm = 0x80000000
        val inst = (0x80000L << 12) | (5L << 7) | 0x37L
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_U)
        dut.io.out.expect(0x80000000L.U)
      }
    }

    "generate U-type zero immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // LUI x5, 0 -> imm = 0
        val inst = (0 << 12) | (5 << 7) | 0x37
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_U)
        dut.io.out.expect(0.U)
      }
    }

    "generate J-type immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // JAL x1, 256 -> imm = 256 = 0x100
        // imm[20] = 0, imm[10:1] = 0x80, imm[11] = 0, imm[19:12] = 0x00
        val inst = (0 << 31) | (0x00 << 12) | (0 << 20) | (0x80 << 21) | (1 << 7) | 0x6F
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_J)
        dut.io.out.expect(256.U)
      }
    }

    "generate J-type negative immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // JAL x1, -256 -> imm = -256 = 0xFFF00
        // imm[20] = 1, imm[10:1] = 0x380, imm[11] = 1, imm[19:12] = 0xFF
        val inst = (1L << 31) | (0xFFL << 12) | (1L << 20) | (0x380L << 21) | (1L << 7) | 0x6FL
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_J)
        dut.io.out.expect(0xFFFFFF00L.U)
      }
    }

    "generate J-type large positive immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // JAL x1, 524286 -> imm = 0x7FFFE (near max positive J-type, bit[20]=0)
        // J-type is 21-bit signed, so max positive with bit[20]=0 is 0x7FFFE
        // imm[20] = 0, imm[19:12] = 0x7F, imm[11] = 1, imm[10:1] = 0x3FF
        val inst = (0L << 31) | (0x3FFL << 21) | (1L << 20) | (0x7FL << 12) | (1L << 7) | 0x6FL
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_J)
        dut.io.out.expect(0x7FFFEL.U)
      }
    }

    "generate Z-type immediate (CSR) correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // CSRRWI with zimm = 15
        val inst = (0x300 << 20) | (15 << 15) | (5 << 12) | (5 << 7) | 0x73
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_Z)
        dut.io.out.expect(15.U)
      }
    }

    "generate Z-type zero immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // CSRRWI with zimm = 0
        val inst = (0x300 << 20) | (0 << 15) | (5 << 12) | (5 << 7) | 0x73
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_Z)
        dut.io.out.expect(0.U)
      }
    }

    "generate Z-type maximum immediate correctly" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        // CSRRWI with zimm = 31 (max 5-bit unsigned)
        val inst = (0x300 << 20) | (31 << 15) | (5 << 12) | (5 << 7) | 0x73
        dut.io.inst.poke(inst.U)
        dut.io.sel.poke(IMM_Z)
        dut.io.out.expect(31.U)
      }
    }

    "default to zero for unknown immediate type" in {
      implicit val config = CoreConfig()
      simulate(new ImmGen) { dut =>
        val inst = 0x12345678.U
        dut.io.inst.poke(inst)
        dut.io.sel.poke(7.U) // Invalid type
        dut.io.out.expect(0.U)
      }
    }
  }
}
