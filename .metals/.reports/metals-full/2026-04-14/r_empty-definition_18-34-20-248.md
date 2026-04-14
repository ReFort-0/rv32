error id: file://<WORKSPACE>/src/main/scala/rv32/RV32Core.scala:
file://<WORKSPACE>/src/main/scala/rv32/RV32Core.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/controlUnit/io/controls.
	 -chisel3/util/controlUnit/io/controls.
	 -rv32/core/datapath/controlUnit/io/controls.
	 -rv32/core/control/controlUnit/io/controls.
	 -controlUnit/io/controls.
	 -scala/Predef.controlUnit.io.controls.
offset: 2852
uri: file://<WORKSPACE>/src/main/scala/rv32/RV32Core.scala
text:
```scala
package rv32

import chisel3._
import chisel3.util._
import rv32.config.RV32Config
import rv32.core.datapath._
import rv32.core.control._

/**
 * Core 指令存储器接口
 */
class CoreIMemInterface extends Bundle {
  val addr = Output(UInt(32.W))    // 指令地址
  val instr = Input(UInt(32.W))    // 指令数据
  val valid = Output(Bool())       // 读使能
}

/**
 * Core 数据存储器接口
 */
class CoreDMemInterface extends Bundle {
  val addr = Output(UInt(32.W))    // 数据地址
  val writeData = Output(UInt(32.W))  // 写数据
  val readData = Input(UInt(32.W))    // 读数据
  val writeEn = Output(Bool())        // 写使能
  val readEn = Output(Bool())         // 读使能
  val byteEn = Output(UInt(4.W))      // 字节使能 (用于 SB/SH/SW)
}

/**
 * RV32 处理器核心
 * 
 * 支持单周期和五级流水线模式
 * 目前优先实现单周期模式
 * 
 * Chisel 优势体现:
 * - 参数化配置 (RV32Config)
 * - Bundle 自动连接
 * - MuxCase/MuxLookup 简化控制逻辑
 * - 类型安全，编译时检查
 */
class RV32Core(config: RV32Config) extends Module {
  val io = IO(new Bundle {
    val iMem = new CoreIMemInterface       // 指令存储器接口
    val dMem = new CoreDMemInterface       // 数据存储器接口
    val interrupt = Input(UInt(config.numInterrupts.W))  // 中断输入
    val pcInit = Input(UInt(32.W))         // 初始 PC 值
  })

  // 实例化组件
  val pc = Module(new PC)
  val regFile = Module(new RegisterFile(config))
  val alu = Module(new ALU(config))
  val immGen = Module(new ImmGen)
  val decoder = Module(new InstructionDecoder)
  val controlUnit = Module(new ControlUnit)

  // ==================== 取指阶段 ====================
  // PC 连接到指令存储器
  io.iMem.addr := pc.io.pc
  io.iMem.valid := true.B
  
  // PC 更新逻辑
  val pcPlus4 = pc.io.pc + 4.U
  val branchTarget = pc.io.pc + immGen.io.imm
  val jumpTarget = Mux(controlUnit.io.controls.pcSrc === 2.U, 
                       pc.io.pc + immGen.io.imm, 
                       alu.io.result)
  
  pc.io.pcNext := MuxLookup(controlUnit.io.controls.pcSrc, pcPlus4)(Seq(
    0.U -> pcPlus4,           // 顺序执行
    1.U -> branchTarget,      // 分支
    2.U -> jumpTarget,        // 跳转
    3.U -> io.pcInit          // 异常/中断
  ))
  pc.io.pcEn := true.B
  pc.io.pcInit := io.pcInit

  // ==================== 译码阶段 ====================
  // 指令译码
  decoder.io.instr := io.iMem.instr
  
  // 读取寄存器
  regFile.io.rs1Addr := decoder.io.rs1
  regFile.io.rs2Addr := decoder.io.rs2
  regFile.io.rdAddr := decoder.io.rd
  regFile.io.rdEn := controlUnit.io.controls.regWrite

  // 生成立即数
  immGen.io.instr := io.iMem.instr
  immGen.io.immSel := controlUnit.io.controls.immSel

  // 控制单元
  controlUnit.io.opcode := decoder.io.opcode
  controlUnit.io.funct3 := decoder.io.funct3
  controlUnit.io.funct7 := decoder.io.funct7
  controlUnit.io.zero := alu.io.zero

  // ==================== 执行阶段 ====================
  // ALU 操作数选择
  val aluSrc1 = regFile.io.rs1Data
  val aluSrc2 = Mux(controlUnit.io.controls.aluSrc, immGen.io.imm, regFile.io.rs2Data)
  
  alu.io.op := controlUnit.io.contro@@ls.aluOp
  alu.io.src1 := aluSrc1
  alu.io.src2 := aluSrc2

  // ==================== 访存阶段 ====================
  // 数据存储器接口
  io.dMem.addr := alu.io.result
  io.dMem.writeData := regFile.io.rs2Data
  io.dMem.readEn := controlUnit.io.controls.memRead
  io.dMem.writeEn := controlUnit.io.controls.memWrite
  io.dMem.byteEn := "b1111".U  // 默认全字访问 (TODO: 根据 funct3 调整)

  // ==================== 写回阶段 ====================
  // 选择写回数据
  val writeBackData = Mux(controlUnit.io.controls.memToReg, 
                          io.dMem.readData, 
                          alu.io.result)
  
  regFile.io.rdData := writeBackData
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 