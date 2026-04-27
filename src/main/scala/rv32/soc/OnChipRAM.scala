package rv32.soc

import chisel3._
import chisel3.util._
import rv32.configs._

// ============================================================
// On-Chip RAM - Synchronous read/write memory
// ============================================================

class OnChipRAM(sizeBytes: Int)(implicit config: CoreConfig) extends Module {
  val io = IO(new SimpleBusSlave)

  val nWords = sizeBytes / 4
  require(nWords > 0, "RAM size must be at least 4 bytes")

  // Use SyncReadMem for single-cycle read
  val mem = SyncReadMem(nWords, UInt(32.W))

  // Word address (ignore lower 2 bits)
  val addr = io.req.addr(log2Ceil(nWords) + 1, 2)

  // Read port
  val rdata = mem.read(addr, io.req.valid && !io.req.wen)

  // Write port - full word write (simplified for P0)
  when(io.req.valid && io.req.wen) {
    mem.write(addr, io.req.wdata)
  }

  // Response - single cycle for P0
  io.resp.valid := RegNext(io.req.valid)
  io.resp.rdata := RegNext(rdata)
}
