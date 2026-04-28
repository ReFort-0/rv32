#include <stdio.h>
#include <stdint.h>
#include <svdpi.h>

// DPI-C trace function - called every cycle from SystemVerilog
extern "C" void trace_instruction_dpi(
    int cycle,
    int pc,
    int inst,
    int imem_req_addr,
    int dmem_req_addr,
    int dmem_wen,
    int dmem_valid
) {
    // Format: cycle, PC, instruction, and memory access info
    printf("[%10d] PC=%08x INST=%08x | imem=%08x dmem=%08x wen=%d valid=%d\n",
           cycle, pc, inst, imem_req_addr, dmem_req_addr, dmem_wen, dmem_valid);
}

// DPI-C initialization - called at startup
extern "C" void trace_init_dpi() {
    printf("=== DPI-C Trace Initialized ===\n");
}

// DPI-C finalization - print summary
extern "C" void trace_fini_dpi(int total_cycles) {
    printf("=== DPI-C Trace Finished: %d cycles ===\n", total_cycles);
}
