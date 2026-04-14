package rv32.components

import chisel3._
import chisel3.util._
import rv32.types._

/**
 * 控制单元（指令译码）
 */
class ControlUnit extends Module {
  val io = IO(new Bundle {
    val opcode      = Input(UInt(7.W))
    val funct3      = Input(UInt(3.W))
    val funct7      = Input(UInt(7.W))
    val ctrlSignals = Output(new ControlSignals)
    val aluOp       = Output(AluOp())
  })

  val ctrl = io.ctrlSignals

  ctrl.regWrite := false.B
  ctrl.memRead := false.B
  ctrl.memWrite := false.B
  ctrl.branch := false.B
  ctrl.aluSrc := false.B
  ctrl.memToReg := false.B
  ctrl.jump := false.B
  io.aluOp := AluOp.ADD

  switch(io.opcode) {
    is(Opcode.ALU) {
      ctrl.regWrite := true.B
      ctrl.aluSrc := false.B
      decodeAluOp(io.funct3, io.funct7)
    }
    is(Opcode.ALUI) {
      ctrl.regWrite := true.B
      ctrl.aluSrc := true.B
      decodeAluOp(io.funct3, "b0000000".U(7.W))
    }
    is(Opcode.LOAD) {
      ctrl.regWrite := true.B
      ctrl.memRead := true.B
      ctrl.aluSrc := true.B
      ctrl.memToReg := true.B
      io.aluOp := AluOp.ADD
    }
    is(Opcode.STORE) {
      ctrl.memWrite := true.B
      ctrl.aluSrc := true.B
      io.aluOp := AluOp.ADD
    }
    is(Opcode.BRANCH) {
      ctrl.branch := true.B
      ctrl.aluSrc := false.B
      io.aluOp := AluOp.SUB
    }
    is(Opcode.JAL) {
      ctrl.regWrite := true.B
      ctrl.aluSrc := true.B
      ctrl.jump := true.B
      io.aluOp := AluOp.ADD
    }
    is(Opcode.JALR) {
      ctrl.regWrite := true.B
      ctrl.aluSrc := true.B
      ctrl.jump := true.B
      io.aluOp := AluOp.ADD
    }
    is(Opcode.LUI) {
      ctrl.regWrite := true.B
      ctrl.memToReg := false.B
      io.aluOp := AluOp.ADD
    }
    is(Opcode.AUIPC) {
      ctrl.regWrite := true.B
      ctrl.aluSrc := true.B
      io.aluOp := AluOp.ADD
    }
  }

  def decodeAluOp(funct3: UInt, funct7: UInt): Unit = {
    switch(funct3) {
      is(Funct3.ADD_SUB) {
        io.aluOp := Mux(funct7 === Funct7.SUB, AluOp.SUB, AluOp.ADD)
      }
      is(Funct3.SLL) { io.aluOp := AluOp.SLL }
      is(Funct3.SLT) { io.aluOp := AluOp.SLT }
      is(Funct3.SLTU) { io.aluOp := AluOp.SLTU }
      is(Funct3.XOR) { io.aluOp := AluOp.XOR }
      is(Funct3.SR) {
        io.aluOp := Mux(funct7 === Funct7.SRA, AluOp.SRA, AluOp.SRL)
      }
      is(Funct3.OR) { io.aluOp := AluOp.OR }
      is(Funct3.AND) { io.aluOp := AluOp.AND }
    }
  }
}
