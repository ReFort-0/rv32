package rv32Spec.core

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import rv32.configs._
import rv32.core.stage._
import rv32.core.util.Constants._

class ExecuteStageSpec extends AnyFreeSpec with Matchers with ChiselSim {
  implicit val config: CoreConfig = CoreConfig(pipelineStages = 1)

  "ExecuteStage" - {
    "should perform ALU ADD operation" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.alu_op.poke(ALU_ADD)
        dut.io.idex.op1_sel.poke(OP1_RS1)
        dut.io.idex.op2_sel.poke(OP2_RS2)
        dut.io.idex.rs1_data.poke(10.U)
        dut.io.idex.rs2_data.poke(20.U)
        dut.io.idex.branch_type.poke(BR_N)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.exmem.alu_result.expect(30.U)
        dut.io.pc_take.expect(false.B)
      }
    }

    "should perform ALU SUB operation" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.alu_op.poke(ALU_SUB)
        dut.io.idex.op1_sel.poke(OP1_RS1)
        dut.io.idex.op2_sel.poke(OP2_RS2)
        dut.io.idex.rs1_data.poke(50.U)
        dut.io.idex.rs2_data.poke(30.U)
        dut.io.idex.branch_type.poke(BR_N)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.exmem.alu_result.expect(20.U)
      }
    }

    "should select PC as operand 1 for AUIPC" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.pc.poke(0x80000000L.U)
        dut.io.idex.alu_op.poke(ALU_ADD)
        dut.io.idex.op1_sel.poke(OP1_PC)
        dut.io.idex.op2_sel.poke(OP2_IMM)
        dut.io.idex.imm.poke(0x1000.U)
        dut.io.idex.branch_type.poke(BR_N)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.exmem.alu_result.expect(0x80001000L.U)
      }
    }

    "should select zero as operand 1 for LUI" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.alu_op.poke(ALU_ADD)
        dut.io.idex.op1_sel.poke(OP1_X0)
        dut.io.idex.op2_sel.poke(OP2_IMM)
        dut.io.idex.imm.poke(0x12345000L.U)
        dut.io.idex.branch_type.poke(BR_N)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.exmem.alu_result.expect(0x12345000L.U)
      }
    }

    "should take branch on BEQ when rs1 == rs2" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.pc.poke(0x80000000L.U)
        dut.io.idex.imm.poke(0x100.U)
        dut.io.idex.op1_sel.poke(OP1_PC)
        dut.io.idex.op2_sel.poke(OP2_IMM)
        dut.io.idex.rs1_data.poke(42.U)
        dut.io.idex.rs2_data.poke(42.U)
        dut.io.idex.branch_type.poke(BR_EQ)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.pc_take.expect(true.B)
        dut.io.pc_target.expect(0x80000100L.U)
      }
    }

    "should not take branch on BEQ when rs1 != rs2" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.rs1_data.poke(42.U)
        dut.io.idex.rs2_data.poke(43.U)
        dut.io.idex.branch_type.poke(BR_EQ)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.pc_take.expect(false.B)
      }
    }

    "should take branch on BNE when rs1 != rs2" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.rs1_data.poke(42.U)
        dut.io.idex.rs2_data.poke(43.U)
        dut.io.idex.branch_type.poke(BR_NE)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.pc_take.expect(true.B)
      }
    }

    "should take branch on BLT when rs1 < rs2 (signed)" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.rs1_data.poke(0xFFFFFFFFL.U)
        dut.io.idex.rs2_data.poke(1.U)
        dut.io.idex.branch_type.poke(BR_LT)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.pc_take.expect(true.B)
      }
    }

    "should not take branch on BLT when rs1 >= rs2 (signed)" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.rs1_data.poke(1.U)
        dut.io.idex.rs2_data.poke(0xFFFFFFFFL.U)
        dut.io.idex.branch_type.poke(BR_LT)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.pc_take.expect(false.B)
      }
    }

    "should take branch on BLTU when rs1 < rs2 (unsigned)" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.rs1_data.poke(1.U)
        dut.io.idex.rs2_data.poke(0xFFFFFFFFL.U)
        dut.io.idex.branch_type.poke(BR_LTU)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.pc_take.expect(true.B)
      }
    }

    "should take branch on BGE when rs1 >= rs2 (signed)" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.rs1_data.poke(1.U)
        dut.io.idex.rs2_data.poke(0xFFFFFFFFL.U)
        dut.io.idex.branch_type.poke(BR_GE)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.pc_take.expect(true.B)
      }
    }

    "should take branch on BGEU when rs1 >= rs2 (unsigned)" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.rs1_data.poke(0xFFFFFFFFL.U)
        dut.io.idex.rs2_data.poke(1.U)
        dut.io.idex.branch_type.poke(BR_GEU)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.pc_take.expect(true.B)
      }
    }

    "should always take branch on JAL" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.pc.poke(0x80000000L.U)
        dut.io.idex.imm.poke(0x100.U)
        dut.io.idex.op1_sel.poke(OP1_PC)
        dut.io.idex.branch_type.poke(BR_J)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.pc_take.expect(true.B)
        dut.io.pc_target.expect(0x80000100L.U)
      }
    }

    "should compute JALR target and clear LSB" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.rs1_data.poke(0x80000005L.U)
        dut.io.idex.imm.poke(0x10.U)
        dut.io.idex.op1_sel.poke(OP1_RS1)
        dut.io.idex.branch_type.poke(BR_J)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.pc_take.expect(true.B)
        dut.io.pc_target.expect(0x80000014L.U)
      }
    }

    "should propagate control signals through pipeline" in {
      simulate(new ExecuteStage) { dut =>
        dut.io.idex.valid.poke(true.B)
        dut.io.idex.pc.poke(0x80000000L.U)
        dut.io.idex.inst.poke(0x12345678L.U)
        dut.io.idex.rd_addr.poke(5.U)
        dut.io.idex.rs2_data.poke(0xDEADBEEFL.U)
        dut.io.idex.mem_en.poke(true.B)
        dut.io.idex.mem_rw.poke(true.B)
        dut.io.idex.mem_type.poke(MT_W)
        dut.io.idex.wb_sel.poke(WB_MEM)
        dut.io.idex.reg_write.poke(true.B)
        dut.io.idex.alu_op.poke(ALU_ADD)
        dut.io.idex.op1_sel.poke(OP1_RS1)
        dut.io.idex.op2_sel.poke(OP2_RS2)
        dut.io.idex.rs1_data.poke(10.U)
        dut.io.idex.branch_type.poke(BR_N)
        dut.io.stall.poke(false.B)
        dut.io.flush.poke(false.B)

        dut.clock.step(1)

        dut.io.exmem.valid.expect(true.B)
        dut.io.exmem.pc.expect(0x80000000L.U)
        dut.io.exmem.inst.expect(0x12345678L.U)
        dut.io.exmem.rd_addr.expect(5.U)
        dut.io.exmem.rs2_data.expect(0xDEADBEEFL.U)
        dut.io.exmem.mem_en.expect(true.B)
        dut.io.exmem.mem_rw.expect(true.B)
        dut.io.exmem.mem_type.expect(MT_W)
        dut.io.exmem.wb_sel.expect(WB_MEM)
        dut.io.exmem.reg_write.expect(true.B)
      }
    }
  }
}
