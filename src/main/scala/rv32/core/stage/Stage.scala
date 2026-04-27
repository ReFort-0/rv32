package rv32.core.stage

import chisel3._
import chisel3.util._
import rv32.configs._
import rv32.core.util._

// ============================================================
// Fetch Stage - Instruction Fetch
// ============================================================

class FetchStageIO(implicit config: CoreConfig) extends Bundle {
  // Control inputs
  val stall = Input(Bool())
  val flush = Input(Bool())
  val pc_next = Input(UInt(32.W))
  val pc_we = Input(Bool())

  // Memory interface
  val inst_req = Output(new SimpleMemReq)
  val inst_resp = Input(new SimpleMemResp)

  // Pipeline output
  val ifid = Output(new IFIDBundle)
  val pc = Output(UInt(32.W))
}

class FetchStage(implicit config: CoreConfig) extends Module {
  val io = IO(new FetchStageIO)

  // PC Register
  val pc_reg = RegInit(0.U(32.W))

  // Instruction request - always fetch from PC
  io.inst_req.addr := pc_reg
  io.inst_req.wdata := 0.U
  io.inst_req.wen := false.B
  io.inst_req.mask := 0.U
  io.inst_req.valid := !io.stall

  // PC update
  when(io.flush) {
    pc_reg := io.pc_next
  }.elsewhen(io.pc_we) {
    pc_reg := io.pc_next
  }.elsewhen(!io.stall) {
    pc_reg := pc_reg + 4.U
  }

  // Output PC for debug
  io.pc := pc_reg

  // Pipeline output - IF/ID bundle
  io.ifid.pc := pc_reg
  io.ifid.inst := io.inst_resp.rdata
  io.ifid.valid := io.inst_resp.valid && !io.flush
}

// ============================================================
// Decode Stage - Instruction Decode
// ============================================================

class DecodeStageIO(implicit config: CoreConfig) extends Bundle {
  // Pipeline input
  val ifid = Input(new IFIDBundle)
  val stall = Input(Bool())
  val flush = Input(Bool())

  // Writeback input (for register file)
  val wb_rd_addr = Input(UInt(5.W))
  val wb_rd_data = Input(UInt(config.xlen.W))
  val wb_reg_write = Input(Bool())

  // Pipeline output
  val idex = Output(new IDEXBundle)
}

class DecodeStage(implicit config: CoreConfig) extends Module {
  val io = IO(new DecodeStageIO)

  // Submodules
  val decoder = Module(new Decoder)
  val immgen = Module(new ImmGen)
  val regfile = Module(new RegisterFile)

  // Connect decoder
  decoder.io.inst := io.ifid.inst

  // Connect immediate generator
  immgen.io.inst := io.ifid.inst
  immgen.io.sel := decoder.io.ctrl.imm_type

  // Connect register file
  regfile.io.rs1_addr := io.ifid.inst(19, 15)
  regfile.io.rs2_addr := io.ifid.inst(24, 20)
  regfile.io.rd_addr := io.wb_rd_addr
  regfile.io.rd_data := io.wb_rd_data
  regfile.io.wen := io.wb_reg_write

  // Pipeline register (or direct pass for single-cycle)
  val idex_reg = RegInit(0.U.asTypeOf(new IDEXBundle))

  when(io.flush) {
    idex_reg.valid := false.B
  }.elsewhen(!io.stall) {
    idex_reg.pc := io.ifid.pc
    idex_reg.inst := io.ifid.inst
    idex_reg.valid := io.ifid.valid

    idex_reg.rs1_addr := io.ifid.inst(19, 15)
    idex_reg.rs2_addr := io.ifid.inst(24, 20)
    idex_reg.rd_addr := io.ifid.inst(11, 7)

    idex_reg.rs1_data := regfile.io.rs1_data
    idex_reg.rs2_data := regfile.io.rs2_data
    idex_reg.imm := immgen.io.out

    idex_reg.op1_sel := decoder.io.ctrl.op1_sel
    idex_reg.op2_sel := decoder.io.ctrl.op2_sel
    idex_reg.alu_op := decoder.io.ctrl.alu_op
    idex_reg.branch_type := decoder.io.ctrl.branch_type
    idex_reg.mem_en := decoder.io.ctrl.mem_en
    idex_reg.mem_rw := decoder.io.ctrl.mem_rw
    idex_reg.mem_type := decoder.io.ctrl.mem_type
    idex_reg.wb_sel := decoder.io.ctrl.wb_sel
    idex_reg.reg_write := decoder.io.ctrl.reg_write
  }

  io.idex := idex_reg
}

// ============================================================
// Execute Stage - ALU and Branch/Jump
// ============================================================

class ExecuteStageIO(implicit config: CoreConfig) extends Bundle {
  // Pipeline input
  val idex = Input(new IDEXBundle)
  val stall = Input(Bool())
  val flush = Input(Bool())

  // Pipeline output
  val exmem = Output(new EXMEMBundle)

  // Branch/Jump output
  val pc_target = Output(UInt(32.W))
  val pc_take = Output(Bool())
}

class ExecuteStage(implicit config: CoreConfig) extends Module {
  import Constants._
  val io = IO(new ExecuteStageIO)

  // ALU instance
  val alu = Module(new ALU)

  // Operand selection
  val op1 = MuxCase(io.idex.rs1_data, Seq(
    (io.idex.op1_sel === OP1_PC) -> io.idex.pc,
    (io.idex.op1_sel === OP1_X0) -> 0.U
  ))

  val op2 = MuxCase(io.idex.rs2_data, Seq(
    (io.idex.op2_sel === OP2_IMM) -> io.idex.imm,
    (io.idex.op2_sel === OP2_4) -> 4.U
  ))

  // Connect ALU
  alu.io.op := io.idex.alu_op
  alu.io.in1 := op1
  alu.io.in2 := op2

  // Branch/Jump logic
  val rs1_data = io.idex.rs1_data
  val rs2_data = io.idex.rs2_data

  val branch_taken = MuxCase(false.B, Seq(
    (io.idex.branch_type === BR_EQ)  -> (rs1_data === rs2_data),
    (io.idex.branch_type === BR_NE)  -> (rs1_data =/= rs2_data),
    (io.idex.branch_type === BR_LT)  -> (rs1_data.asSInt < rs2_data.asSInt),
    (io.idex.branch_type === BR_LTU) -> (rs1_data < rs2_data),
    (io.idex.branch_type === BR_GE)  -> (rs1_data.asSInt >= rs2_data.asSInt),
    (io.idex.branch_type === BR_GEU) -> (rs1_data >= rs2_data),
    (io.idex.branch_type === BR_J)   -> true.B
  ))

  // PC target calculation
  val pc_plus_imm = io.idex.pc + io.idex.imm
  val jalr_target = Cat((rs1_data + io.idex.imm)(31, 1), 0.U(1.W))

  io.pc_target := Mux(io.idex.branch_type === BR_N, pc_plus_imm, jalr_target)
  io.pc_take := branch_taken

  // Pipeline output
  val exmem_reg = RegInit(0.U.asTypeOf(new EXMEMBundle))

  when(io.flush) {
    exmem_reg.valid := false.B
  }.elsewhen(!io.stall) {
    exmem_reg.pc := io.idex.pc
    exmem_reg.inst := io.idex.inst
    exmem_reg.valid := io.idex.valid
    exmem_reg.rd_addr := io.idex.rd_addr
    exmem_reg.alu_result := alu.io.out
    exmem_reg.rs2_data := io.idex.rs2_data
    exmem_reg.mem_en := io.idex.mem_en
    exmem_reg.mem_rw := io.idex.mem_rw
    exmem_reg.mem_type := io.idex.mem_type
    exmem_reg.wb_sel := io.idex.wb_sel
    exmem_reg.reg_write := io.idex.reg_write
  }

  io.exmem := exmem_reg
}

// ============================================================
// Memory Stage - Data Memory Access
// ============================================================

class MemoryStageIO(implicit config: CoreConfig) extends Bundle {
  // Pipeline input
  val exmem = Input(new EXMEMBundle)
  val stall = Input(Bool())
  val flush = Input(Bool())

  // Data memory interface
  val data_req = Output(new SimpleMemReq)
  val data_resp = Input(new SimpleMemResp)

  // Pipeline output
  val memwb = Output(new MEMWBBundle)
}

class MemoryStage(implicit config: CoreConfig) extends Module {
  val io = IO(new MemoryStageIO)

  // Data memory request
  io.data_req.addr := io.exmem.alu_result
  io.data_req.wdata := io.exmem.rs2_data
  io.data_req.wen := io.exmem.mem_rw
  io.data_req.mask := 0xf.U  // TODO: proper byte mask based on mem_type
  io.data_req.valid := io.exmem.mem_en

  // Pipeline output
  val memwb_reg = RegInit(0.U.asTypeOf(new MEMWBBundle))

  when(io.flush) {
    memwb_reg.valid := false.B
  }.elsewhen(!io.stall) {
    memwb_reg.pc := io.exmem.pc
    memwb_reg.inst := io.exmem.inst
    memwb_reg.valid := io.exmem.valid
    memwb_reg.rd_addr := io.exmem.rd_addr
    memwb_reg.alu_result := io.exmem.alu_result
    memwb_reg.mem_rdata := io.data_resp.rdata
    memwb_reg.wb_sel := io.exmem.wb_sel
    memwb_reg.reg_write := io.exmem.reg_write
  }

  io.memwb := memwb_reg
}

// ============================================================
// Writeback Stage - Register Writeback
// ============================================================

class WritebackStageIO(implicit config: CoreConfig) extends Bundle {
  // Pipeline input
  val memwb = Input(new MEMWBBundle)
  val stall = Input(Bool())

  // Register file writeback
  val wb_rd_addr = Output(UInt(5.W))
  val wb_rd_data = Output(UInt(config.xlen.W))
  val wb_reg_write = Output(Bool())
}

class WritebackStage(implicit config: CoreConfig) extends Module {
  import Constants._
  val io = IO(new WritebackStageIO)

  // Writeback data selection
  val wb_data = MuxCase(io.memwb.alu_result, Seq(
    (io.memwb.wb_sel === WB_MEM) -> io.memwb.mem_rdata,
    (io.memwb.wb_sel === WB_PC4) -> (io.memwb.pc + 4.U)
  ))

  // Outputs (registered for timing)
  val rd_addr_reg = RegNext(io.memwb.rd_addr)
  val rd_data_reg = RegNext(wb_data)
  val reg_write_reg = RegNext(io.memwb.reg_write && io.memwb.valid && !io.stall)

  io.wb_rd_addr := rd_addr_reg
  io.wb_rd_data := rd_data_reg
  io.wb_reg_write := reg_write_reg
}
