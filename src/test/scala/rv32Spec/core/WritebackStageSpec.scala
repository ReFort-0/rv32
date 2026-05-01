package rv32Spec.core

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import rv32.configs._
import rv32.core.stage.WritebackStage
import rv32.core.util.Constants._

/**
  * WritebackStage unit tests
  * Run with: ./mill rv32.test.testOnly rv32Spec.core.WritebackStageSpec
  */
class WritebackStageSpec extends AnyFreeSpec with Matchers with ChiselSim {

  "WritebackStage should" - {

    "select ALU result when wb_sel is WB_ALU" in {
      implicit val config = CoreConfig(pipelineStages = 1)
      simulate(new WritebackStage) { dut =>
        dut.io.stall.poke(false.B)
        dut.io.memwb.pc.poke(0x80000000L.U)
        dut.io.memwb.inst.poke(0x00000013L.U)
        dut.io.memwb.valid.poke(true.B)
        dut.io.memwb.rd_addr.poke(5.U)
        dut.io.memwb.alu_result.poke(0x12345678L.U)
        dut.io.memwb.mem_rdata.poke(0xDEADBEEFL.U)
        dut.io.memwb.wb_sel.poke(WB_ALU)
        dut.io.memwb.reg_write.poke(true.B)

        dut.io.wb_rd_data.expect(0x12345678L.U)
        dut.io.wb_reg_write.expect(true.B)
        dut.io.wb_rd_addr.expect(5.U)
      }
    }

    "select memory data when wb_sel is WB_MEM" in {
      implicit val config = CoreConfig(pipelineStages = 1)
      simulate(new WritebackStage) { dut =>
        dut.io.stall.poke(false.B)
        dut.io.memwb.pc.poke(0x80000000L.U)
        dut.io.memwb.inst.poke(0x00000013L.U)
        dut.io.memwb.valid.poke(true.B)
        dut.io.memwb.rd_addr.poke(10.U)
        dut.io.memwb.alu_result.poke(0x12345678L.U)
        dut.io.memwb.mem_rdata.poke(0xDEADBEEFL.U)
        dut.io.memwb.wb_sel.poke(WB_MEM)
        dut.io.memwb.reg_write.poke(true.B)

        dut.io.wb_rd_data.expect(0xDEADBEEFL.U)
        dut.io.wb_reg_write.expect(true.B)
        dut.io.wb_rd_addr.expect(10.U)
      }
    }

    "select PC+4 when wb_sel is WB_PC4" in {
      implicit val config = CoreConfig(pipelineStages = 1)
      simulate(new WritebackStage) { dut =>
        dut.io.stall.poke(false.B)
        dut.io.memwb.pc.poke(0x80000000L.U)
        dut.io.memwb.inst.poke(0x00000013L.U)
        dut.io.memwb.valid.poke(true.B)
        dut.io.memwb.rd_addr.poke(1.U)
        dut.io.memwb.alu_result.poke(0x12345678L.U)
        dut.io.memwb.mem_rdata.poke(0xDEADBEEFL.U)
        dut.io.memwb.wb_sel.poke(WB_PC4)
        dut.io.memwb.reg_write.poke(true.B)

        dut.io.wb_rd_data.expect(0x80000004L.U)
        dut.io.wb_reg_write.expect(true.B)
        dut.io.wb_rd_addr.expect(1.U)
      }
    }

    "not write to register when reg_write is false" in {
      implicit val config = CoreConfig(pipelineStages = 1)
      simulate(new WritebackStage) { dut =>
        dut.io.stall.poke(false.B)
        dut.io.memwb.pc.poke(0x80000000L.U)
        dut.io.memwb.inst.poke(0x00000013L.U)
        dut.io.memwb.valid.poke(true.B)
        dut.io.memwb.rd_addr.poke(5.U)
        dut.io.memwb.alu_result.poke(0x12345678L.U)
        dut.io.memwb.mem_rdata.poke(0.U)
        dut.io.memwb.wb_sel.poke(WB_ALU)
        dut.io.memwb.reg_write.poke(false.B)

        dut.io.wb_reg_write.expect(false.B)
      }
    }

    "not write when valid is false" in {
      implicit val config = CoreConfig(pipelineStages = 1)
      simulate(new WritebackStage) { dut =>
        dut.io.stall.poke(false.B)
        dut.io.memwb.pc.poke(0x80000000L.U)
        dut.io.memwb.inst.poke(0x00000013L.U)
        dut.io.memwb.valid.poke(false.B)
        dut.io.memwb.rd_addr.poke(5.U)
        dut.io.memwb.alu_result.poke(0x12345678L.U)
        dut.io.memwb.mem_rdata.poke(0.U)
        dut.io.memwb.wb_sel.poke(WB_ALU)
        dut.io.memwb.reg_write.poke(true.B)

        dut.io.wb_reg_write.expect(false.B)
      }
    }
  }
}
