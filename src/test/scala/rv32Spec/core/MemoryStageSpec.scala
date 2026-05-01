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
  * MemoryStage unit tests
  * Run with: ./mill rv32.test.testOnly rv32Spec.core.MemoryStageSpec
  */
class MemoryStageSpec extends AnyFreeSpec with Matchers with ChiselSim {

  "MemoryStage should" - {

    "generate memory read request for load instructions" in {
      implicit val config = CoreConfig()
      simulate(new MemoryStage) { dut =>
        val loadAddr = 0x80001000L
        val rd = 5

        dut.io.exmem.pc.poke(0x80000000L.U)
        dut.io.exmem.inst.poke(0x00002283L)  // lw x5, 0(x0)
        dut.io.exmem.valid.poke(true.B)
        dut.io.exmem.rd_addr.poke(rd.U)
        dut.io.exmem.alu_result.poke(loadAddr.U)
        dut.io.exmem.mem_en.poke(true.B)
        dut.io.exmem.mem_rw.poke(false.B)  // read
        dut.io.exmem.wb_sel.poke(WB_MEM)
        dut.io.exmem.reg_write.poke(true.B)

        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step()

        // Check memory request
        dut.io.data_req.addr.expect(loadAddr.U)
        dut.io.data_req.wen.expect(false.B)
        dut.io.data_req.valid.expect(true.B)

        // Check pipeline output
        dut.io.memwb.rd_addr.expect(rd.U)
        dut.io.memwb.wb_sel.expect(WB_MEM)
        dut.io.memwb.reg_write.expect(true.B)
      }
    }

    "generate memory write request for store instructions" in {
      implicit val config = CoreConfig()
      simulate(new MemoryStage) { dut =>
        val storeAddr = 0x80002000L
        val storeData = 0x12345678L

        dut.io.exmem.pc.poke(0x80000004L.U)
        dut.io.exmem.inst.poke(0x00A02023L)  // sw x10, 0(x0)
        dut.io.exmem.valid.poke(true.B)
        dut.io.exmem.alu_result.poke(storeAddr.U)
        dut.io.exmem.rs2_data.poke(storeData.U)
        dut.io.exmem.mem_en.poke(true.B)
        dut.io.exmem.mem_rw.poke(true.B)  // write
        dut.io.exmem.wb_sel.poke(WB_ALU)  // No writeback for store
        dut.io.exmem.reg_write.poke(false.B)

        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step()

        // Check memory request
        dut.io.data_req.addr.expect(storeAddr.U)
        dut.io.data_req.wdata.expect(storeData.U)
        dut.io.data_req.wen.expect(true.B)
        dut.io.data_req.valid.expect(true.B)

        // Store doesn't write to register
        dut.io.memwb.reg_write.expect(false.B)
      }
    }

    "pass ALU result for non-memory instructions" in {
      implicit val config = CoreConfig()
      simulate(new MemoryStage) { dut =>
        val result = 0xABCD1234L
        val rd = 3

        dut.io.exmem.pc.poke(0x80000008L.U)
        dut.io.exmem.inst.poke(0x00C101B3L)  // add x3, x2, x12
        dut.io.exmem.valid.poke(true.B)
        dut.io.exmem.rd_addr.poke(rd.U)
        dut.io.exmem.alu_result.poke(result.U)
        dut.io.exmem.mem_en.poke(false.B)  // No memory access
        dut.io.exmem.wb_sel.poke(WB_ALU)
        dut.io.exmem.reg_write.poke(true.B)

        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step()

        // ALU result should pass through
        dut.io.memwb.alu_result.expect(result.U)
        dut.io.memwb.reg_write.expect(true.B)
        dut.io.memwb.rd_addr.expect(rd.U)

        // No memory request
        dut.io.data_req.valid.expect(false.B)
      }
    }

    "clear valid on flush" in {
      implicit val config = CoreConfig(pipelineStages = 5)
      simulate(new MemoryStage) { dut =>
        dut.io.exmem.pc.poke(0x80000000L.U)
        dut.io.exmem.valid.poke(true.B)
        dut.io.exmem.mem_en.poke(false.B)
        dut.io.exmem.reg_write.poke(true.B)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step()
        dut.io.memwb.valid.expect(true.B)

        dut.io.flush.poke(true.B)
        dut.clock.step()
        dut.io.memwb.valid.expect(false.B)
      }
    }

    "pass memory read data to writeback" in {
      implicit val config = CoreConfig()
      simulate(new MemoryStage) { dut =>
        val memData = 0xDEADBEEFL

        dut.io.exmem.pc.poke(0x80000000L.U)
        dut.io.exmem.valid.poke(true.B)
        dut.io.exmem.mem_en.poke(true.B)
        dut.io.exmem.wb_sel.poke(WB_MEM)
        dut.io.exmem.reg_write.poke(true.B)

        dut.io.data_resp.rdata.poke(memData.U)

        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step()

        dut.io.memwb.mem_rdata.expect(memData.U)
      }
    }
  }
}
