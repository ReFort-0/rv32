#include "VNeoRV32_Act_tb.h"
#include "verilated.h"
#include <iostream>

// DPI-C function declarations from trace_dpi.cpp
extern "C" {
    void trace_init_dpi();
    void trace_instruction_dpi(int cycle, int pc, int inst,
                               int imem_req_addr, int dmem_req_addr,
                               int dmem_wen, int dmem_valid);
    void trace_fini_dpi(int total_cycles);
}

VNeoRV32_Act_tb* top;

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
    Verilated::traceEverOn(true);

    // Initialize DPI trace
    trace_init_dpi();

    // 初始化模块
    top = new VNeoRV32_Act_tb();

    // 复位
    Verilated::timeInc(1);
    top->clk = 0;
    top->rst = 1;
    top->eval();
    for (size_t i = 0; i < 4; i++) {
        Verilated::timeInc(1);
        top->clk = !top->clk;
        top->eval();
    }
    top->rst = 0;

    // 测试运行
    int cycle = 0;
    while (!Verilated::gotFinish()) {
        Verilated::timeInc(1);
        top->clk = !top->clk;
        top->eval();
        cycle++;
    }

    // Finalize DPI trace
    trace_fini_dpi(cycle);

    delete top;
    return 0;
}
