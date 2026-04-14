package rv32.components

import chisel3._
import chisel3.util._
import rv32.config.CpuConfig

/**
 * 寄存器文件
 */
class CoreReg(config: CpuConfig = CpuConfig()) extends Module {
  val io = IO(new Bundle {
    val rs1         = Input(UInt(5.W))
    val rs2         = Input(UInt(5.W))
    val rd          = Input(UInt(5.W))
    val WriteEn     = Input(Bool())
    val writeData   = Input(UInt(32.W))
    val readDate1   = Output(UInt(32.W))
    val readDate2   = Output(UInt(32.W))
  })

  val regs = RegInit(VecInit(Seq.fill(config.regNum)(0.U(32.W))))

  regs(0) := 0.U(32.W)

  io.readDate1 := Mux(io.rs1 =/= 0.U, regs(io.rs1), 0.U)
  io.readDate2 := Mux(io.rs2 =/= 0.U, regs(io.rs2), 0.U)

  when(io.regWrite && io.rd =/= 0.U) {
    regs(io.rd) := io.writeData
  }
}
