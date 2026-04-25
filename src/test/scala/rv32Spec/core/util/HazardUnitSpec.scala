package rv32Spec.util.core

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import rv32.core.util.HazardUnit
import rv32.configs.CoreConfig

class HazardUnitSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit val conf: CoreConfig = new CoreConfig

  "HazardUnit" - {
    "should not forward when no hazards exist" in {
      simulate(new HazardUnit) { dut =>
        // ID stage: rs1=1, rs2=2
        dut.io.id_rs1.poke(1.U)
        dut.io.id_rs2.poke(2.U)
        dut.io.id_valid.poke(true.B)
        dut.io.id_use_rs1.poke(true.B)
        dut.io.id_use_rs2.poke(true.B)

        // EX stage: rd=3 (different from rs1/rs2)
        dut.io.ex_rd.poke(3.U)
        dut.io.ex_rf_wen.poke(true.B)
        dut.io.ex_valid.poke(true.B)
        dut.io.ex_is_load.poke(false.B)
        dut.io.ex_alu_result.poke(0xDEADBEEFL.U)
        dut.io.ex_fu_sel.poke(0.U)  // FU_ALU

        // MEM stage: rd=4
        dut.io.mem_rd.poke(4.U)
        dut.io.mem_rf_wen.poke(true.B)
        dut.io.mem_valid.poke(true.B)
        dut.io.mem_result.poke(0xCAFEBABEL.U)

        // WB stage: rd=5
        dut.io.wb_rd.poke(5.U)
        dut.io.wb_rf_wen.poke(true.B)
        dut.io.wb_valid.poke(true.B)
        dut.io.wb_result.poke(0x12345678.U)

        dut.io.muldiv_busy.poke(false.B)
        dut.io.br_taken.poke(false.B)

        dut.clock.step()

        // No forwarding (fwd_sel = 0)
        dut.io.fwd_rs1_sel.expect(0.U)
        dut.io.fwd_rs2_sel.expect(0.U)

        // No stall
        dut.io.stall.expect(false.B)
      }
    }

    "should forward from EX stage when rd matches rs1" in {
      simulate(new HazardUnit) { dut =>
        // ID stage: rs1=1, rs2=2
        dut.io.id_rs1.poke(1.U)
        dut.io.id_rs2.poke(2.U)
        dut.io.id_valid.poke(true.B)
        dut.io.id_use_rs1.poke(true.B)
        dut.io.id_use_rs2.poke(false.B)

        // EX stage: rd=1 (matches rs1), result=0xAAAA
        dut.io.ex_rd.poke(1.U)
        dut.io.ex_rf_wen.poke(true.B)
        dut.io.ex_valid.poke(true.B)
        dut.io.ex_is_load.poke(false.B)
        dut.io.ex_alu_result.poke(0xAAAABBBBL.U)
        dut.io.ex_fu_sel.poke(0.U)

        // MEM and WB don't match
        dut.io.mem_rd.poke(4.U)
        dut.io.mem_rf_wen.poke(true.B)
        dut.io.mem_valid.poke(true.B)
        dut.io.mem_result.poke(0xCAFEBABEL.U)
        dut.io.wb_rd.poke(5.U)
        dut.io.wb_rf_wen.poke(true.B)
        dut.io.wb_valid.poke(true.B)
        dut.io.wb_result.poke(0x12345678.U)
        dut.io.muldiv_busy.poke(false.B)
        dut.io.br_taken.poke(false.B)

        dut.clock.step()

        // Forward from EX (fwd_sel = 1)
        dut.io.fwd_rs1_sel.expect(1.U)
        dut.io.fwd_rs1_data_ex.expect(0xAAAABBBBL.U)
      }
    }

    "should forward from MEM stage when EX doesn't match but MEM does" in {
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1.poke(1.U)
        dut.io.id_rs2.poke(2.U)
        dut.io.id_valid.poke(true.B)
        dut.io.id_use_rs1.poke(true.B)
        dut.io.id_use_rs2.poke(false.B)

        // EX stage: rd=3 (no match)
        dut.io.ex_rd.poke(3.U)
        dut.io.ex_rf_wen.poke(true.B)
        dut.io.ex_valid.poke(true.B)
        dut.io.ex_is_load.poke(false.B)
        dut.io.ex_alu_result.poke(0x11111111.U)
        dut.io.ex_fu_sel.poke(0.U)

        // MEM stage: rd=1 (matches rs1), result=0xBBBB
        dut.io.mem_rd.poke(1.U)
        dut.io.mem_rf_wen.poke(true.B)
        dut.io.mem_valid.poke(true.B)
        dut.io.mem_result.poke(0xBBBBCCCCL.U)

        // WB stage
        dut.io.wb_rd.poke(5.U)
        dut.io.wb_rf_wen.poke(true.B)
        dut.io.wb_valid.poke(true.B)
        dut.io.wb_result.poke(0x12345678.U)
        dut.io.muldiv_busy.poke(false.B)
        dut.io.br_taken.poke(false.B)

        dut.clock.step()

        // Forward from MEM (fwd_sel = 2)
        dut.io.fwd_rs1_sel.expect(2.U)
        dut.io.fwd_rs1_data_mem.expect(0xBBBBCCCCL.U)
      }
    }

    "should forward from WB stage when only WB matches" in {
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1.poke(1.U)
        dut.io.id_rs2.poke(2.U)
        dut.io.id_valid.poke(true.B)
        dut.io.id_use_rs1.poke(true.B)
        dut.io.id_use_rs2.poke(false.B)

        // EX and MEM don't match
        dut.io.ex_rd.poke(3.U)
        dut.io.ex_rf_wen.poke(true.B)
        dut.io.ex_valid.poke(true.B)
        dut.io.ex_is_load.poke(false.B)
        dut.io.ex_alu_result.poke(0x11111111.U)
        dut.io.ex_fu_sel.poke(0.U)
        dut.io.mem_rd.poke(4.U)
        dut.io.mem_rf_wen.poke(true.B)
        dut.io.mem_valid.poke(true.B)
        dut.io.mem_result.poke(0x22222222.U)

        // WB stage: rd=1 (matches), result=0xCCCC
        dut.io.wb_rd.poke(1.U)
        dut.io.wb_rf_wen.poke(true.B)
        dut.io.wb_valid.poke(true.B)
        dut.io.wb_result.poke(0xCCCCDDD0L.U)
        dut.io.muldiv_busy.poke(false.B)
        dut.io.br_taken.poke(false.B)

        dut.clock.step()

        // Forward from WB (fwd_sel = 3)
        dut.io.fwd_rs1_sel.expect(3.U)
        dut.io.fwd_rs1_data_wb.expect(0xCCCCDDD0L.U)
      }
    }

    "should give EX priority over MEM when both match (EX closer to ID)" in {
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1.poke(1.U)
        dut.io.id_rs2.poke(2.U)
        dut.io.id_valid.poke(true.B)
        dut.io.id_use_rs1.poke(true.B)
        dut.io.id_use_rs2.poke(false.B)

        // EX stage: rd=1, result=0xEAEA
        dut.io.ex_rd.poke(1.U)
        dut.io.ex_rf_wen.poke(true.B)
        dut.io.ex_valid.poke(true.B)
        dut.io.ex_is_load.poke(false.B)
        dut.io.ex_alu_result.poke(0xEAEA0000L.U)
        dut.io.ex_fu_sel.poke(0.U)

        // MEM stage: also rd=1 (older), result=0xBBBB
        dut.io.mem_rd.poke(1.U)
        dut.io.mem_rf_wen.poke(true.B)
        dut.io.mem_valid.poke(true.B)
        dut.io.mem_result.poke(0xBBBB0000L.U)

        dut.io.wb_rd.poke(5.U)
        dut.io.wb_rf_wen.poke(true.B)
        dut.io.wb_valid.poke(true.B)
        dut.io.wb_result.poke(0x12345678.U)
        dut.io.muldiv_busy.poke(false.B)
        dut.io.br_taken.poke(false.B)

        dut.clock.step()

        // Forward from EX (priority: EX > MEM > WB)
        dut.io.fwd_rs1_sel.expect(1.U)
        dut.io.fwd_rs1_data_ex.expect(0xEAEA0000L.U)
      }
    }

    "should stall on load-use hazard (load in EX, dependent instruction in ID)" in {
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1.poke(1.U)
        dut.io.id_rs2.poke(0.U)
        dut.io.id_valid.poke(true.B)
        dut.io.id_use_rs1.poke(true.B)
        dut.io.id_use_rs2.poke(false.B)

        // EX stage: load instruction writing to rd=1
        dut.io.ex_rd.poke(1.U)
        dut.io.ex_rf_wen.poke(true.B)
        dut.io.ex_valid.poke(true.B)
        dut.io.ex_is_load.poke(true.B)  // LOAD!
        dut.io.ex_alu_result.poke(0.U)
        dut.io.ex_fu_sel.poke(0.U)

        dut.io.mem_rd.poke(0.U)
        dut.io.mem_rf_wen.poke(false.B)
        dut.io.mem_valid.poke(false.B)
        dut.io.mem_result.poke(0.U)
        dut.io.wb_rd.poke(0.U)
        dut.io.wb_rf_wen.poke(false.B)
        dut.io.wb_valid.poke(false.B)
        dut.io.wb_result.poke(0.U)
        dut.io.muldiv_busy.poke(false.B)
        dut.io.br_taken.poke(false.B)

        dut.clock.step()

        // Must stall!
        dut.io.stall.expect(true.B)
      }
    }

    "should not stall on load-use when register is x0" in {
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1.poke(0.U)  // x0
        dut.io.id_rs2.poke(0.U)
        dut.io.id_valid.poke(true.B)
        dut.io.id_use_rs1.poke(true.B)
        dut.io.id_use_rs2.poke(false.B)

        // EX stage: load writing to x0 (should be ignored)
        dut.io.ex_rd.poke(0.U)
        dut.io.ex_rf_wen.poke(true.B)
        dut.io.ex_valid.poke(true.B)
        dut.io.ex_is_load.poke(true.B)
        dut.io.ex_alu_result.poke(0.U)
        dut.io.ex_fu_sel.poke(0.U)

        dut.io.mem_rd.poke(0.U)
        dut.io.mem_rf_wen.poke(false.B)
        dut.io.mem_valid.poke(false.B)
        dut.io.mem_result.poke(0.U)
        dut.io.wb_rd.poke(0.U)
        dut.io.wb_rf_wen.poke(false.B)
        dut.io.wb_valid.poke(false.B)
        dut.io.wb_result.poke(0.U)
        dut.io.muldiv_busy.poke(false.B)
        dut.io.br_taken.poke(false.B)

        dut.clock.step()

        // No stall because rd=x0
        dut.io.stall.expect(false.B)
      }
    }

    "should stall on load-use for both rs1 and rs2 dependencies" in {
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1.poke(2.U)
        dut.io.id_rs2.poke(3.U)
        dut.io.id_valid.poke(true.B)
        dut.io.id_use_rs1.poke(true.B)
        dut.io.id_use_rs2.poke(true.B)

        // EX stage: load writing to rd=2 (matches rs1)
        dut.io.ex_rd.poke(2.U)
        dut.io.ex_rf_wen.poke(true.B)
        dut.io.ex_valid.poke(true.B)
        dut.io.ex_is_load.poke(true.B)
        dut.io.ex_alu_result.poke(0.U)
        dut.io.ex_fu_sel.poke(0.U)

        dut.io.mem_rd.poke(0.U)
        dut.io.mem_rf_wen.poke(false.B)
        dut.io.mem_valid.poke(false.B)
        dut.io.mem_result.poke(0.U)
        dut.io.wb_rd.poke(0.U)
        dut.io.wb_rf_wen.poke(false.B)
        dut.io.wb_valid.poke(false.B)
        dut.io.wb_result.poke(0.U)
        dut.io.muldiv_busy.poke(false.B)
        dut.io.br_taken.poke(false.B)

        dut.clock.step()

        // Stall because rs1 depends on load
        dut.io.stall.expect(true.B)
      }
    }

    "should not forward when rf_wen is false" in {
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1.poke(1.U)
        dut.io.id_rs2.poke(2.U)
        dut.io.id_valid.poke(true.B)
        dut.io.id_use_rs1.poke(true.B)
        dut.io.id_use_rs2.poke(false.B)

        // EX stage: rd=1 but wen=false
        dut.io.ex_rd.poke(1.U)
        dut.io.ex_rf_wen.poke(false.B)  // NOT writing!
        dut.io.ex_valid.poke(true.B)
        dut.io.ex_is_load.poke(false.B)
        dut.io.ex_alu_result.poke(0xDEADBEEFL.U)
        dut.io.ex_fu_sel.poke(0.U)

        dut.io.mem_rd.poke(0.U)
        dut.io.mem_rf_wen.poke(false.B)
        dut.io.mem_valid.poke(false.B)
        dut.io.mem_result.poke(0.U)
        dut.io.wb_rd.poke(0.U)
        dut.io.wb_rf_wen.poke(false.B)
        dut.io.wb_valid.poke(false.B)
        dut.io.wb_result.poke(0.U)
        dut.io.muldiv_busy.poke(false.B)
        dut.io.br_taken.poke(false.B)

        dut.clock.step()

        // No forwarding because wen is false
        dut.io.fwd_rs1_sel.expect(0.U)
      }
    }

    "should not forward when stage is not valid" in {
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1.poke(1.U)
        dut.io.id_rs2.poke(2.U)
        dut.io.id_valid.poke(true.B)
        dut.io.id_use_rs1.poke(true.B)
        dut.io.id_use_rs2.poke(false.B)

        // EX stage: rd=1, valid=false
        dut.io.ex_rd.poke(1.U)
        dut.io.ex_rf_wen.poke(true.B)
        dut.io.ex_valid.poke(false.B)  // INVALID!
        dut.io.ex_is_load.poke(false.B)
        dut.io.ex_alu_result.poke(0xDEADBEEFL.U)
        dut.io.ex_fu_sel.poke(0.U)

        dut.io.mem_rd.poke(0.U)
        dut.io.mem_rf_wen.poke(false.B)
        dut.io.mem_valid.poke(false.B)
        dut.io.mem_result.poke(0.U)
        dut.io.wb_rd.poke(0.U)
        dut.io.wb_rf_wen.poke(false.B)
        dut.io.wb_valid.poke(false.B)
        dut.io.wb_result.poke(0.U)
        dut.io.muldiv_busy.poke(false.B)
        dut.io.br_taken.poke(false.B)

        dut.clock.step()

        // No forwarding because stage not valid
        dut.io.fwd_rs1_sel.expect(0.U)
      }
    }

    "should not forward when use_rs is false" in {
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1.poke(1.U)
        dut.io.id_rs2.poke(1.U)  // Same register
        dut.io.id_valid.poke(true.B)
        dut.io.id_use_rs1.poke(false.B)  // NOT using rs1!
        dut.io.id_use_rs2.poke(false.B)  // NOT using rs2!

        // EX stage: rd=1, should match but won't forward
        dut.io.ex_rd.poke(1.U)
        dut.io.ex_rf_wen.poke(true.B)
        dut.io.ex_valid.poke(true.B)
        dut.io.ex_is_load.poke(false.B)
        dut.io.ex_alu_result.poke(0xDEADBEEFL.U)
        dut.io.ex_fu_sel.poke(0.U)

        dut.io.mem_rd.poke(0.U)
        dut.io.mem_rf_wen.poke(false.B)
        dut.io.mem_valid.poke(false.B)
        dut.io.mem_result.poke(0.U)
        dut.io.wb_rd.poke(0.U)
        dut.io.wb_rf_wen.poke(false.B)
        dut.io.wb_valid.poke(false.B)
        dut.io.wb_result.poke(0.U)
        dut.io.muldiv_busy.poke(false.B)
        dut.io.br_taken.poke(false.B)

        dut.clock.step()

        // No forwarding because use_rs is false
        dut.io.fwd_rs1_sel.expect(0.U)
        dut.io.fwd_rs2_sel.expect(0.U)
      }
    }
  }
}
