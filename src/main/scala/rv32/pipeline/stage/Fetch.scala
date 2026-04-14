package rv32.pipeline.stage

import chisel3._
import chisel3.util._
import rv32.config.CpuConfig

/**
 * 取指阶段 (Fetch Stage)
 */
class Fetch(config: CpuConfig = CpuConfig()) extends Module {
  val io = IO(new Bundle {
    val pcIn        = Input(UInt(config.addrWidth.W))
    val imemData    = Input(UInt(config.instWidth.W))
    val branchTaken = Input(Bool())
    val pcBranch    = Input(UInt(config.addrWidth.W))
    val pcJump      = Input(UInt(config.addrWidth.W))
    val stall       = Input(Bool())
    val pcNext      = Output(UInt(config.addrWidth.W))
    val instOut     = Output(UInt(config.instWidth.W))
  })

  val pcTarget = Mux(io.branchTaken, io.pcBranch, io.pcJump)
  val pcSelect = Mux(io.branchTaken || io.pcJump =/= 0.U, pcTarget, io.pcIn + 4.U)

  io.pcNext := Mux(io.stall, io.pcIn, pcSelect)
  io.instOut := io.imemData
}
