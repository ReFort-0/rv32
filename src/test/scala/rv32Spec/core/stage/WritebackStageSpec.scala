package rv32Spec.core.stage

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import rv32.core.stage._
import rv32.core.util._
import rv32.configs.CoreConfig

class WritebackStageSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit val conf: CoreConfig = new CoreConfig

  "WritebackStage" - {
    "should write back ALU result when wb_sel=WB_ALU" in {
      simulate(new WritebackStage) { dut =>
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.rd.poke(5.U)
        dut.io.in.bits.alu_result.poke(42.U)
        dut.io.in.bits.mem_data.poke(0.U)
        dut.io.in.bits.ctrl.wb_sel.poke(Constants.WB_ALU)
        dut.io.in.bits.ctrl.rf_wen.poke(true.B)
        dut.io.in.bits.ctrl.mem_typ.poke(Constants.MT_W)
        dut.io.in.bits.mem_addr.poke(0.U)
        dut.io.in.bits.pc.poke(0.U)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)
        dut.clock.step()

        dut.io.rf.addr.expect(5.U)
        dut.io.rf.data.expect(42.U)
        dut.io.rf.wen.expect(true.B)
      }
    }

    "should write back memory data when wb_sel=WB_MEM" in {
      simulate(new WritebackStage) { dut =>
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.rd.poke(3.U)
        dut.io.in.bits.alu_result.poke(0.U)
        dut.io.in.bits.mem_data.poke(0xDEADBEEFL.U)
        dut.io.in.bits.ctrl.wb_sel.poke(Constants.WB_MEM)
        dut.io.in.bits.ctrl.rf_wen.poke(true.B)
        dut.io.in.bits.ctrl.mem_typ.poke(Constants.MT_W)
        dut.io.in.bits.mem_addr.poke(0.U)
        dut.io.in.bits.pc.poke(0.U)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)
        dut.clock.step()

        dut.io.rf.data.expect(0xDEADBEEFL.U)
      }
    }

    "should write back PC+4 when wb_sel=WB_PC4 (JAL/JALR)" in {
      simulate(new WritebackStage) { dut =>
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.rd.poke(1.U)
        dut.io.in.bits.alu_result.poke(0.U)
        dut.io.in.bits.mem_data.poke(0.U)
        dut.io.in.bits.ctrl.wb_sel.poke(Constants.WB_PC4)
        dut.io.in.bits.ctrl.rf_wen.poke(true.B)
        dut.io.in.bits.ctrl.mem_typ.poke(Constants.MT_W)
        dut.io.in.bits.mem_addr.poke(0.U)
        dut.io.in.bits.pc.poke(0x80000000L.U)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)
        dut.clock.step()

        dut.io.rf.data.expect(0x80000004L.U)
      }
    }

    "should not write when rf_wen=false" in {
      simulate(new WritebackStage) { dut =>
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.rd.poke(7.U)
        dut.io.in.bits.alu_result.poke(99.U)
        dut.io.in.bits.ctrl.wb_sel.poke(Constants.WB_ALU)
        dut.io.in.bits.ctrl.rf_wen.poke(false.B)
        dut.io.in.bits.ctrl.mem_typ.poke(Constants.MT_W)
        dut.io.in.bits.mem_addr.poke(0.U)
        dut.io.in.bits.pc.poke(0.U)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)
        dut.clock.step()

        dut.io.rf.wen.expect(false.B)
      }
    }

    "should not write when stall=true" in {
      simulate(new WritebackStage) { dut =>
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.rd.poke(5.U)
        dut.io.in.bits.alu_result.poke(42.U)
        dut.io.in.bits.ctrl.wb_sel.poke(Constants.WB_ALU)
        dut.io.in.bits.ctrl.rf_wen.poke(true.B)
        dut.io.in.bits.ctrl.mem_typ.poke(Constants.MT_W)
        dut.io.in.bits.mem_addr.poke(0.U)
        dut.io.in.bits.pc.poke(0.U)
        dut.io.stall.poke(true.B)
        dut.io.flush.poke(false.B)
        dut.clock.step()

        dut.io.rf.wen.expect(false.B)
      }
    }

    "should sign-extend LB (load byte)" in {
      simulate(new WritebackStage) { dut =>
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.rd.poke(2.U)
        dut.io.in.bits.mem_data.poke(0x000000FF.U)
        dut.io.in.bits.ctrl.wb_sel.poke(Constants.WB_MEM)
        dut.io.in.bits.ctrl.rf_wen.poke(true.B)
        dut.io.in.bits.ctrl.mem_typ.poke(Constants.MT_B)
        dut.io.in.bits.mem_addr.poke(0.U)
        dut.io.in.bits.pc.poke(0.U)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)
        dut.clock.step()

        dut.io.rf.data.expect(0xFFFFFFFFL.U)
      }
    }

    "should zero-extend LBU (load byte unsigned)" in {
      simulate(new WritebackStage) { dut =>
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.rd.poke(2.U)
        dut.io.in.bits.mem_data.poke(0x000000FF.U)
        dut.io.in.bits.ctrl.wb_sel.poke(Constants.WB_MEM)
        dut.io.in.bits.ctrl.rf_wen.poke(true.B)
        dut.io.in.bits.ctrl.mem_typ.poke(Constants.MT_BU)
        dut.io.in.bits.mem_addr.poke(0.U)
        dut.io.in.bits.pc.poke(0.U)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)
        dut.clock.step()

        dut.io.rf.data.expect(0xFF.U)
      }
    }

    "should sign-extend LH (load halfword)" in {
      simulate(new WritebackStage) { dut =>
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.rd.poke(2.U)
        dut.io.in.bits.mem_data.poke(0x0000FFFF.U)
        dut.io.in.bits.ctrl.wb_sel.poke(Constants.WB_MEM)
        dut.io.in.bits.ctrl.rf_wen.poke(true.B)
        dut.io.in.bits.ctrl.mem_typ.poke(Constants.MT_H)
        dut.io.in.bits.mem_addr.poke(0.U)
        dut.io.in.bits.pc.poke(0.U)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)
        dut.clock.step()

        dut.io.rf.data.expect(0xFFFFFFFFL.U)
      }
    }
  }
}
