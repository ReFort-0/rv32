package rv32Spec.core

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import rv32.configs._
import rv32.core.util._

/**
  * HazardUnit unit tests - tests hazard detection and forwarding logic for 5-stage pipeline
  * Run with: ./mill rv32.test.testOnly rv32Spec.core.HazardUnitSpec
  */
class HazardUnitSpec extends AnyFreeSpec with Matchers with ChiselSim {

  "HazardUnit should" - {

    "not forward when no hazard exists" in {
      implicit val config = CoreConfig()
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1_addr.poke(1.U)
        dut.io.id_rs2_addr.poke(2.U)
        dut.io.ex_rd_addr.poke(3.U)
        dut.io.ex_reg_write.poke(true.B)
        dut.io.ex_mem_read.poke(false.B)
        dut.io.mem_rd_addr.poke(4.U)
        dut.io.mem_reg_write.poke(true.B)
        dut.io.wb_rd_addr.poke(5.U)
        dut.io.wb_reg_write.poke(true.B)

        dut.io.forward_a.expect(0.U)
        dut.io.forward_b.expect(0.U)
        dut.io.stall_if.expect(false.B)
        dut.io.stall_id.expect(false.B)
      }
    }

    "forward from EX stage for rs1 RAW hazard" in {
      implicit val config = CoreConfig()
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1_addr.poke(5.U)
        dut.io.id_rs2_addr.poke(2.U)
        dut.io.ex_rd_addr.poke(5.U)
        dut.io.ex_reg_write.poke(true.B)
        dut.io.ex_mem_read.poke(false.B)
        dut.io.mem_rd_addr.poke(0.U)
        dut.io.mem_reg_write.poke(false.B)
        dut.io.wb_rd_addr.poke(0.U)
        dut.io.wb_reg_write.poke(false.B)

        dut.io.forward_a.expect(1.U)
        dut.io.forward_b.expect(0.U)
        dut.io.stall_if.expect(false.B)
        dut.io.stall_id.expect(false.B)
      }
    }

    "forward from EX stage for rs2 RAW hazard" in {
      implicit val config = CoreConfig()
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1_addr.poke(1.U)
        dut.io.id_rs2_addr.poke(6.U)
        dut.io.ex_rd_addr.poke(6.U)
        dut.io.ex_reg_write.poke(true.B)
        dut.io.ex_mem_read.poke(false.B)
        dut.io.mem_rd_addr.poke(0.U)
        dut.io.mem_reg_write.poke(false.B)
        dut.io.wb_rd_addr.poke(0.U)
        dut.io.wb_reg_write.poke(false.B)

        dut.io.forward_a.expect(0.U)
        dut.io.forward_b.expect(1.U)
        dut.io.stall_if.expect(false.B)
        dut.io.stall_id.expect(false.B)
      }
    }

    "forward from MEM stage for rs1 RAW hazard" in {
      implicit val config = CoreConfig()
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1_addr.poke(8.U)
        dut.io.id_rs2_addr.poke(2.U)
        dut.io.ex_rd_addr.poke(0.U)
        dut.io.ex_reg_write.poke(false.B)
        dut.io.ex_mem_read.poke(false.B)
        dut.io.mem_rd_addr.poke(8.U)
        dut.io.mem_reg_write.poke(true.B)
        dut.io.wb_rd_addr.poke(0.U)
        dut.io.wb_reg_write.poke(false.B)

        dut.io.forward_a.expect(2.U)
        dut.io.forward_b.expect(0.U)
        dut.io.stall_if.expect(false.B)
        dut.io.stall_id.expect(false.B)
      }
    }

    "prioritize EX forwarding over MEM forwarding" in {
      implicit val config = CoreConfig()
      simulate(new HazardUnit) { dut =>
        // Both EX and MEM write to same register, EX should take priority
        dut.io.id_rs1_addr.poke(10.U)
        dut.io.id_rs2_addr.poke(10.U)
        dut.io.ex_rd_addr.poke(10.U)
        dut.io.ex_reg_write.poke(true.B)
        dut.io.ex_mem_read.poke(false.B)
        dut.io.mem_rd_addr.poke(10.U)
        dut.io.mem_reg_write.poke(true.B)
        dut.io.wb_rd_addr.poke(0.U)
        dut.io.wb_reg_write.poke(false.B)

        dut.io.forward_a.expect(1.U)
        dut.io.forward_b.expect(1.U)
        dut.io.stall_if.expect(false.B)
        dut.io.stall_id.expect(false.B)
      }
    }

    "not forward when destination is x0" in {
      implicit val config = CoreConfig()
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1_addr.poke(0.U)
        dut.io.id_rs2_addr.poke(0.U)
        dut.io.ex_rd_addr.poke(0.U)
        dut.io.ex_reg_write.poke(true.B)
        dut.io.ex_mem_read.poke(false.B)
        dut.io.mem_rd_addr.poke(0.U)
        dut.io.mem_reg_write.poke(true.B)
        dut.io.wb_rd_addr.poke(0.U)
        dut.io.wb_reg_write.poke(true.B)

        dut.io.forward_a.expect(0.U)
        dut.io.forward_b.expect(0.U)
        dut.io.stall_if.expect(false.B)
        dut.io.stall_id.expect(false.B)
      }
    }

    "not forward when reg_write is false" in {
      implicit val config = CoreConfig()
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1_addr.poke(5.U)
        dut.io.id_rs2_addr.poke(6.U)
        dut.io.ex_rd_addr.poke(5.U)
        dut.io.ex_reg_write.poke(false.B)
        dut.io.ex_mem_read.poke(false.B)
        dut.io.mem_rd_addr.poke(6.U)
        dut.io.mem_reg_write.poke(false.B)
        dut.io.wb_rd_addr.poke(0.U)
        dut.io.wb_reg_write.poke(false.B)

        dut.io.forward_a.expect(0.U)
        dut.io.forward_b.expect(0.U)
        dut.io.stall_if.expect(false.B)
        dut.io.stall_id.expect(false.B)
      }
    }

    "stall on load-use hazard for rs1" in {
      implicit val config = CoreConfig()
      simulate(new HazardUnit) { dut =>
        // EX stage has a load instruction writing to register 5
        dut.io.id_rs1_addr.poke(5.U)
        dut.io.id_rs2_addr.poke(2.U)
        dut.io.ex_rd_addr.poke(5.U)
        dut.io.ex_reg_write.poke(true.B)
        dut.io.ex_mem_read.poke(true.B)
        dut.io.mem_rd_addr.poke(0.U)
        dut.io.mem_reg_write.poke(false.B)
        dut.io.wb_rd_addr.poke(0.U)
        dut.io.wb_reg_write.poke(false.B)

        dut.io.stall_if.expect(true.B)
        dut.io.stall_id.expect(true.B)
        dut.io.flush_ex.expect(true.B)
      }
    }

    "stall on load-use hazard for rs2" in {
      implicit val config = CoreConfig()
      simulate(new HazardUnit) { dut =>
        // EX stage has a load instruction writing to register 6
        dut.io.id_rs1_addr.poke(1.U)
        dut.io.id_rs2_addr.poke(6.U)
        dut.io.ex_rd_addr.poke(6.U)
        dut.io.ex_reg_write.poke(true.B)
        dut.io.ex_mem_read.poke(true.B)
        dut.io.mem_rd_addr.poke(0.U)
        dut.io.mem_reg_write.poke(false.B)
        dut.io.wb_rd_addr.poke(0.U)
        dut.io.wb_reg_write.poke(false.B)

        dut.io.stall_if.expect(true.B)
        dut.io.stall_id.expect(true.B)
        dut.io.flush_ex.expect(true.B)
      }
    }

    "not stall on load-use hazard when destination is x0" in {
      implicit val config = CoreConfig()
      simulate(new HazardUnit) { dut =>
        dut.io.id_rs1_addr.poke(0.U)
        dut.io.id_rs2_addr.poke(2.U)
        dut.io.ex_rd_addr.poke(0.U)
        dut.io.ex_reg_write.poke(true.B)
        dut.io.ex_mem_read.poke(true.B)
        dut.io.mem_rd_addr.poke(0.U)
        dut.io.mem_reg_write.poke(false.B)
        dut.io.wb_rd_addr.poke(0.U)
        dut.io.wb_reg_write.poke(false.B)

        dut.io.stall_if.expect(false.B)
        dut.io.stall_id.expect(false.B)
      }
    }

    "not stall when load result is not used" in {
      implicit val config = CoreConfig()
      simulate(new HazardUnit) { dut =>
        // EX stage has a load to register 7, but ID uses registers 1 and 2
        dut.io.id_rs1_addr.poke(1.U)
        dut.io.id_rs2_addr.poke(2.U)
        dut.io.ex_rd_addr.poke(7.U)
        dut.io.ex_reg_write.poke(true.B)
        dut.io.ex_mem_read.poke(true.B)
        dut.io.mem_rd_addr.poke(0.U)
        dut.io.mem_reg_write.poke(false.B)
        dut.io.wb_rd_addr.poke(0.U)
        dut.io.wb_reg_write.poke(false.B)

        dut.io.stall_if.expect(false.B)
        dut.io.stall_id.expect(false.B)
      }
    }

    "handle complex forwarding scenario" in {
      implicit val config = CoreConfig()
      simulate(new HazardUnit) { dut =>
        // rs1 needs forwarding from EX, rs2 needs forwarding from MEM
        dut.io.id_rs1_addr.poke(11.U)
        dut.io.id_rs2_addr.poke(12.U)
        dut.io.ex_rd_addr.poke(11.U)
        dut.io.ex_reg_write.poke(true.B)
        dut.io.ex_mem_read.poke(false.B)
        dut.io.mem_rd_addr.poke(12.U)
        dut.io.mem_reg_write.poke(true.B)
        dut.io.wb_rd_addr.poke(0.U)
        dut.io.wb_reg_write.poke(false.B)

        dut.io.forward_a.expect(1.U)
        dut.io.forward_b.expect(2.U)
        dut.io.stall_if.expect(false.B)
        dut.io.stall_id.expect(false.B)
      }
    }
  }
}
