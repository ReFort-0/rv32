package rv32.core.stage

import chisel3._
import chisel3.util._
import rv32.configs._
import rv32.core.util._

class MemoryStageIO(implicit config: CoreConfig) extends Bundle {
  val exmem = Input(new EXMEMBundle)
  val stall = Input(Bool())
  val flush = Input(Bool())

  val data_req = Output(new SimpleMemReq)
  val data_resp = Input(new SimpleMemResp)

  val memwb = Output(new MEMWBBundle)
}

class MemoryStage(implicit config: CoreConfig) extends Module {
  import Constants._
  val io = IO(new MemoryStageIO)

  val addr_offset = io.exmem.alu_result(1, 0)

  // Store: generate mask and align wdata
  val store_mask = MuxCase("b1111".U(4.W), Seq(
    (io.exmem.mem_type === MT_B)  -> (1.U(4.W) << addr_offset),
    (io.exmem.mem_type === MT_H)  -> (3.U(4.W) << addr_offset)
  ))

  val store_data = MuxCase(io.exmem.rs2_data, Seq(
    (io.exmem.mem_type === MT_B)  -> Fill(4, io.exmem.rs2_data(7, 0)),
    (io.exmem.mem_type === MT_H)  -> Fill(2, io.exmem.rs2_data(15, 0))
  ))

  // Memory alignment assertions
  when(io.exmem.valid && io.exmem.mem_en) {
    switch(io.exmem.mem_type) {
      is(MT_H, MT_HU) {
        assert(io.exmem.alu_result(0) === 0.U, "Halfword access must be 2-byte aligned")
      }
      is(MT_W) {
        assert(io.exmem.alu_result(1, 0) === 0.U, "Word access must be 4-byte aligned")
      }
    }
  }

  io.data_req.addr := io.exmem.alu_result
  io.data_req.wdata := store_data
  io.data_req.wen := io.exmem.mem_rw
  io.data_req.mask := store_mask
  io.data_req.valid := io.exmem.mem_en

  // Load: sign/zero extend (ActRam already shifts by byte offset)
  val raw_rdata = io.data_resp.rdata
  val load_data = MuxCase(raw_rdata, Seq(
    (io.exmem.mem_type === MT_B)  -> Cat(Fill(24, raw_rdata(7)), raw_rdata(7, 0)),
    (io.exmem.mem_type === MT_BU) -> Cat(0.U(24.W), raw_rdata(7, 0)),
    (io.exmem.mem_type === MT_H)  -> Cat(Fill(16, raw_rdata(15)), raw_rdata(15, 0)),
    (io.exmem.mem_type === MT_HU) -> Cat(0.U(16.W), raw_rdata(15, 0))
  ))

  val next = Wire(new MEMWBBundle)
  next.pc := io.exmem.pc
  next.inst := io.exmem.inst
  next.valid := io.exmem.valid
  next.rd_addr := io.exmem.rd_addr
  next.alu_result := io.exmem.alu_result
  next.mem_rdata := load_data
  next.wb_sel := io.exmem.wb_sel
  next.reg_write := io.exmem.reg_write

  if (config.pipelineStages == 1) {
    io.memwb := next
  } else {
    val reg = RegInit(0.U.asTypeOf(new MEMWBBundle))
    when(io.flush) { reg.valid := false.B }
    .elsewhen(!io.stall) { reg := next }
    io.memwb := reg
  }
}
