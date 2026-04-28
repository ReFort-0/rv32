module NeoRV32_Act_tb
#(
    parameter int unsigned RAM_WORD_DEPTH = 32'h0001_0000,  // 64KB
    parameter int unsigned RAM_BASE_ADDR  = 32'h80000000
) (
    input clk,
    input rst
);

  // NeoRV32Core I/O signals (wire-based)
  wire [31:0] imem_req_addr;
  wire [31:0] imem_resp_rdata;
  wire        imem_resp_valid;

  wire [31:0] dmem_req_addr;
  wire [31:0] dmem_req_wdata;
  wire        dmem_req_wen;
  wire        dmem_req_valid;
  wire [31:0] dmem_resp_rdata;

  wire [31:0] debug_pc;

  // Create interfaces for connecting to ActRam and HTIF
  instruction_if core_inst_if ();
  memory_direct_if #( .DATA_WIDTH(32), .ADDR_WIDTH(32) ) memory ();

  // Connect NeoRV32Core wire signals to instruction interface
  assign core_inst_if.addr   = imem_req_addr;
  assign core_inst_if.enable = 1'b1;  // Always enabled
  assign imem_resp_rdata     = core_inst_if.inst;
  assign imem_resp_valid     = 1'b1;    // Always valid (single cycle memory)

  // Connect NeoRV32Core wire signals to memory interface
  assign memory.raddr      = dmem_req_addr;
  assign memory.waddr      = dmem_req_addr;
  assign memory.wdata      = dmem_req_wdata;
  assign memory.write      = dmem_req_wen && dmem_req_valid;
  assign memory.read       = !dmem_req_wen && dmem_req_valid;
  assign memory.read_size  = 2'b10;   // Word read
  assign memory.write_size = 2'b10;   // Word write
  assign dmem_resp_rdata   = memory.rdata;

  // ActRam - unified memory (uses interfaces)
  ActRam #(
      .WORD_DEPTH(RAM_WORD_DEPTH),
      .BASE_ADDR (RAM_BASE_ADDR)
  ) u_ActRam (
      .clk(clk),
      .*
  );

  // DUT - NeoRV32Core (uses wire signals)
  NeoRV32Core dut (
    .clock(clk),
    .reset(rst),
    .io_imem_req_addr(imem_req_addr),
    .io_imem_resp_rdata(imem_resp_rdata),
    .io_imem_resp_valid(imem_resp_valid),
    .io_dmem_req_addr(dmem_req_addr),
    .io_dmem_req_wdata(dmem_req_wdata),
    .io_dmem_req_wen(dmem_req_wen),
    .io_dmem_req_valid(dmem_req_valid),
    .io_dmem_resp_rdata(dmem_resp_rdata),
    .io_debug_pc(debug_pc)
  );

  // HTIF for test control (uses memory interface)
  HTIF #( .RAM_WORD_DEPTH(RAM_WORD_DEPTH) ) htif;
  string dump_file;

  initial begin
    string wave;
    $value$plusargs("dump=%s", dump_file);
    htif = new(RAM_BASE_ADDR, memory);
    if ($value$plusargs("wave=%s", wave)) begin
      $dumpfile(wave);
      $dumpvars();
    end
  end

  always @(posedge clk, posedge rst) begin
    if (rst) begin
      htif.check_halt = 0;
    end else begin
      htif.capture_write();
    end
  end

  always @(posedge clk) begin
    if (htif.check_timeout_and_plus()) begin
      htif.timeout(u_ActRam.ram);
    end else if (htif.check_halt) begin
      htif.finalize(u_ActRam.ram);
    end
  end

  // DPI-C function declarations
  import "DPI-C" function void trace_init_dpi();
  import "DPI-C" function void trace_instruction_dpi(
    input int cycle,
    input int pc,
    input int inst,
    input int imem_req_addr,
    input int dmem_req_addr,
    input int dmem_wen,
    input int dmem_valid
  );
  import "DPI-C" function void trace_fini_dpi(input int total_cycles);

  // Debug: trace first few instructions
  initial begin
    trace_init_dpi();
    $display("=== NeoRV32 ACT4 Test Starting ===");
    $display("RAM_BASE_ADDR = %08x", RAM_BASE_ADDR);
    $display("RAM_WORD_DEPTH = %0d", RAM_WORD_DEPTH);
  end

  int unsigned cycle_count = 0;
  always @(posedge clk) begin
    if (!rst) begin
      cycle_count <= cycle_count + 1;
      if (cycle_count < 20) begin
        trace_instruction_dpi(
          cycle_count,
          debug_pc,
          imem_resp_rdata,
          imem_req_addr,
          dmem_req_addr,
          {31'd0, dmem_req_wen},
          {31'd0, dmem_req_valid}
        );
      end
      if (cycle_count == 19) begin
        trace_fini_dpi(cycle_count + 1);
      end
    end
  end

endmodule
