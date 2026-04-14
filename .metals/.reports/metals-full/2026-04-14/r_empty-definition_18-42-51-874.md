error id: file://<WORKSPACE>/src/main/scala/rv32/core/control/ControlUnit.scala:
file://<WORKSPACE>/src/main/scala/rv32/core/control/ControlUnit.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/ALUOps.ALU_SLL.
	 -chisel3/ALUOps.ALU_SLL#
	 -chisel3/ALUOps.ALU_SLL().
	 -chisel3/util/ALUOps.ALU_SLL.
	 -chisel3/util/ALUOps.ALU_SLL#
	 -chisel3/util/ALUOps.ALU_SLL().
	 -rv32/core/datapath/ALUOps.ALU_SLL.
	 -rv32/core/datapath/ALUOps.ALU_SLL#
	 -rv32/core/datapath/ALUOps.ALU_SLL().
	 -ALUOps.ALU_SLL.
	 -ALUOps.ALU_SLL#
	 -ALUOps.ALU_SLL().
	 -scala/Predef.ALUOps.ALU_SLL.
	 -scala/Predef.ALUOps.ALU_SLL#
	 -scala/Predef.ALUOps.ALU_SLL().
offset: 1725
uri: file://<WORKSPACE>/src/main/scala/rv32/core/control/ControlUnit.scala
text:
```scala
package rv32.core.control

import chisel3._
import chisel3.util._
import rv32.core.datapath.ALUOps
import rv32.core.control.Decoder.{Opcodes, ALUFunct3}

/**
 * 控制信号束
 * 
 * 控制单元生成的所有控制信号
 */
class ControlSignals extends Bundle {
  // 寄存器堆控制
  val regWrite   = Bool()      // 寄存器写使能
  
  // ALU 控制
  val aluSrc     = Bool()      // ALU 第二操作数选择 (0=rs2, 1=立即数)
  val aluOp      = UInt(4.W)   // ALU 操作码
  
  // 数据通路控制
  val memRead    = Bool()      // 数据存储器读使能
  val memWrite   = Bool()      // 数据存储器写使能
  val memToReg   = Bool()      // 存储器到寄存器 (0=ALU结果, 1=存储器数据)
  
  // PC 控制
  val pcSrc      = UInt(2.W)   // PC 源选择 (00=PC+4, 01=分支, 10=跳转, 11=异常)
  val jump       = Bool()      // 跳转使能
  
  // 立即数类型
  val immSel     = UInt(3.W)   // 立即数生成类型
}

/**
 * 控制单元 (Control Unit)
 * 
 * 根据指令操作码生成所有控制信号
 * 使用 Chisel 的 MuxLookup 简化控制逻辑
 */
class ControlUnit extends Module {
  val io = IO(new Bundle {
    val opcode = Input(UInt(7.W))
    val funct3 = Input(UInt(3.W))
    val funct7 = Input(UInt(7.W))
    val zero   = Input(Bool())         // ALU 零标志 (用于分支)
    val controls = Output(new ControlSignals)
  })

  // 默认控制信号
  val ctrl = Wire(new ControlSignals)
  ctrl.regWrite := false.B
  ctrl.aluSrc   := false.B
  ctrl.aluOp    := ALUOps.ALU_ADD
  ctrl.memRead  := false.B
  ctrl.memWrite := false.B
  ctrl.memToReg := false.B
  ctrl.pcSrc    := 0.U
  ctrl.jump     := false.B
  ctrl.immSel   := 0.U

  // 根据操作码生成控制信号
  switch(io.opcode) {
    // R 型指令 (寄存器-寄存器)
    is(Opcodes.REG) {
      ctrl.regWrite := true.B
      ctrl.aluSrc   := false.B
      ctrl.aluOp    := MuxLookup(io.funct3, ALUOps.ALU_ADD)(Seq(
        ALUFunct3.ADD_SUB -> Mux(io.funct7(5), ALUOps.ALU_SUB, ALUOps.ALU_ADD),
        ALUFunct3.SLL     -> ALUOps.@@ALU_SLL,
        ALUFunct3.SLT     -> ALUOps.ALU_SLT,
        ALUFunct3.SLTU    -> ALUOps.ALU_SLTU,
        ALUFunct3.XOR     -> ALUOps.ALU_XOR,
        ALUFunct3.SRL_SRA -> Mux(io.funct7(5), ALUOps.ALU_SRA, ALUOps.ALU_SRL),
        ALUFunct3.OR      -> ALUOps.ALU_OR,
        ALUFunct3.AND     -> ALUOps.ALU_AND
      ))
    }
    
    // I 型指令 (寄存器-立即数)
    is(Opcodes.IMM) {
      ctrl.regWrite := true.B
      ctrl.aluSrc   := true.B
      ctrl.aluOp    := MuxLookup(io.funct3, ALUOps.ALU_ADD)(Seq(
        ALUFunct3.ADD_SUB -> ALUOps.ALU_ADD,
        ALUFunct3.SLL     -> ALUOps.ALU_SLL,
        ALUFunct3.SLT     -> ALUOps.ALU_SLT,
        ALUFunct3.SLTU    -> ALUOps.ALU_SLTU,
        ALUFunct3.XOR     -> ALUOps.ALU_XOR,
        ALUFunct3.SRL_SRA -> ALUOps.ALU_SRL,
        ALUFunct3.OR      -> ALUOps.ALU_OR,
        ALUFunct3.AND     -> ALUOps.ALU_AND
      ))
      ctrl.immSel := 0.U  // I 型立即数
    }
    
    // 加载指令
    is(Opcodes.LOAD) {
      ctrl.regWrite := true.B
      ctrl.aluSrc   := true.B
      ctrl.aluOp    := ALUOps.ALU_ADD
      ctrl.memRead  := true.B
      ctrl.memToReg := true.B
      ctrl.immSel   := 0.U  // I 型立即数
    }
    
    // 存储指令
    is(Opcodes.STORE) {
      ctrl.aluSrc   := true.B
      ctrl.aluOp    := ALUOps.ALU_ADD
      ctrl.memWrite := true.B
      ctrl.immSel   := 1.U  // S 型立即数
    }
    
    // 分支指令
    is(Opcodes.BRANCH) {
      ctrl.aluSrc   := true.B  // 实际使用 rs2 用于比较
      ctrl.aluOp    := ALUOps.ALU_SUB  // 比较 rs1 和 rs2
      ctrl.pcSrc    := Mux(io.zero, 1.U, 0.U)
      ctrl.immSel   := 2.U  // B 型立即数
    }
    
    // JAL 指令
    is(Opcodes.JAL) {
      ctrl.regWrite := true.B
      ctrl.pcSrc    := 2.U
      ctrl.jump     := true.B
      ctrl.immSel   := 4.U  // J 型立即数
    }
    
    // JALR 指令
    is(Opcodes.JALR) {
      ctrl.regWrite := true.B
      ctrl.aluSrc   := true.B
      ctrl.aluOp    := ALUOps.ALU_ADD
      ctrl.pcSrc    := 2.U
      ctrl.jump     := true.B
      ctrl.immSel   := 0.U  // I 型立即数
    }
    
    // LUI 指令
    is(Opcodes.LUI) {
      ctrl.regWrite := true.B
      ctrl.aluOp    := ALUOps.ALU_COPY
      ctrl.immSel   := 3.U  // U 型立即数
    }
    
    // AUIPC 指令
    is(Opcodes.AUIPC) {
      ctrl.regWrite := true.B
      ctrl.aluOp    := ALUOps.ALU_ADD
      ctrl.immSel   := 3.U  // U 型立即数
    }
    
    // SYSTEM 指令 (ECALL, EBREAK 等)
    is(Opcodes.SYSTEM) {
      // TODO: 处理系统调用
      ctrl.pcSrc := 3.U  // 异常处理
    }
  }

  io.controls := ctrl
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 