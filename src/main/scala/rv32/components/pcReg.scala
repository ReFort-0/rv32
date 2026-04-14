package rv32.components

import chisel3._
import chisel3.util._
import rv32.types._

class pcReg extends Module{
    val io = IO(new Bundle {
        val hold   =  Input(Bool())
        val jumpEn =  Input(Bool())
        val jump   =  Input(UInt(32.W))
        val reset  =  Input(Bool())
        val pc     =  Output(UInt(32.W))
    })

    val pcReg = RegInit(config.resetVector.U(32.W))
    io.pc := pcReg

    when(jumpEn){
        pcReg := io.jump
    }.elsewhen(hold){
        
    }.otherwise{
        pcReg := pcReg + 4.U
    }
}