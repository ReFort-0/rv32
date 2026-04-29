# ACT4 — RISC-V 架构合规测试说明

---

## 一、为什么需要合规测试？

你设计了一颗 RISC-V 处理器，它能跑 `add`、`lw`、`beq`……但怎么证明它的行为和 RISC-V 规范完全一致？

**RISC-V 架构合规测试（Architecture Compliance Test，ACT）** 就是为此而生的。它提供了一套标准测试程序，每个程序测试一条或一类指令的所有边界情况，最后把关键内存区域的内容（称为"签名"）写到文件里，与黄金参考模型（Sail 模拟器）的输出逐字节比对。一致则通过，不一致则失败。

---

## 二、整体目录结构

```
ACT4/
├── readme.md                   ← 本文件
├── run.py                      ← 一键运行所有测试的 Python 脚本
├── rv32i/                      ← DUT 配置（告诉框架用什么编译器、链接脚本等）
│   ├── test_config.yaml        ← 工具链路径、参考模型等配置
│   ├── link.ld                 ← 链接脚本，决定程序加载到哪个地址
│   ├── rvmodel_macros.h        ← 测试框架宏定义（如何报告通过/失败）
│   └── ...
├── rv32i_sim/
│   ├── HDL/                    ← 处理器 RTL + 仿真专用模块
│   │   ├── *.sv                ← 处理器各流水线阶段（从 Chisel 生成）
│   │   └── tb/                 ← 仿真测试台
│   │       ├── NeoRV32_Act_tb.sv  ← 顶层测试台，把所有模块连在一起
│   │       ├── ActRam.sv          ← 仿真用内存模型
│   │       └── HTIF.sv            ← 测试控制器（检测测试结束、保存签名）
│   └── verilator_cpp/          ← C++ 仿真驱动
│       ├── sim_main.cpp        ← Verilator 主循环
│       └── trace_dpi.cpp       ← DPI-C 调试追踪函数
└── waves/                      ← 每个测试的波形文件（.fst 格式）
```

---

## 三、测试流程全景

下面这张图展示了从源码到测试结果的完整数据流：

```
riscv-arch-test 仓库
（标准测试汇编源码 .S）
         │
         │ make（交叉编译）
         ▼
      ELF 文件
（包含机器码 + 符号表）
         │
         │ objcopy（转换格式）
         ▼
      HEX 文件
（Verilog $readmemh 可读的文本格式）
         │
         │ 加载进仿真内存
         ▼
┌─────────────────────────────┐
│       Verilator 仿真        │
│                             │
│  ActRam（内存）             │
│     ↕ 指令总线              │
│  NeoRV32Core（你的处理器）  │
│     ↕ 数据总线              │
│  ActRam（同一块内存）       │
│     ↕ 监听写操作            │
│  HTIF（测试控制器）         │
└─────────────────────────────┘
         │
         │ 测试程序写 tohost=1（通过）或 tohost=3（失败）
         ▼
   HTIF 检测到结束
   → 把签名区域内存内容写入 .sig 文件
         │
         │ run.py 比对
         ▼
   与 Sail 参考模型输出对比
   → SUCCESS / FAILED
```

---

## 四、各模块详解

### 4.1 测试程序是什么样的？

每个测试（如 `I-sb-00`）是一段 RISC-V 汇编程序，结构大致如下：

```
初始化阶段：
  设置寄存器初始值，准备测试数据

测试阶段（以 sb 为例）：
  sb s5, 21(ra)        ← 执行被测指令
  lw t5, -4(ra)        ← 读回内存中的值
  lw tp, 0(ra)         ← 读取预期值
  beq tp, t5, pass     ← 比较，不等则跳到失败处理

签名保存阶段：
  把关键内存区域的内容保留在 .data 段的签名区

结束阶段：
  sw x1, 0(tohost)     ← 写 1 到 tohost 表示通过
  sw x0, 4(tohost)     ← 触发 HTIF 检测
  j self_loop          ← 原地循环，等待仿真结束
```

**签名区（Signature Region）** 是一段预先初始化为特定值（如 `0xdeadbeef`）的内存，测试程序执行过程中会用 store 指令修改其中部分字节。最终这段内存的内容就是"签名"，反映了处理器执行结果。

### 4.2 ActRam.sv — 仿真内存

这是一个用 SystemVerilog 写的内存模型，在仿真中充当处理器的 RAM。

**关键特性：**

- **统一内存**：指令和数据共用同一块 RAM（哈佛结构的处理器在仿真时用冯·诺依曼内存）
- **字节写使能**：支持按字节、半字、字写入，通过 `byte_enable` 信号控制
- **写后读转发**：同一周期内，如果读地址等于写地址，直接返回写入的新值，避免读到旧数据

**字节写入原理（以 `sb` 为例）：**

假设要把寄存器低 8 位写入地址 `0x80000005`（字节偏移 = 1）：

```
RAM 按 32 位字存储，地址 0x80000004 对应的字包含字节 [7:4]
字节偏移 addr[1:0] = 01

byte_enable = 4'b0001 << 1 = 4'b0010   ← 只使能第 1 字节

write_mask = 0x0000_FF00               ← 只修改第 1 字节
true_wdata = (新数据 & write_mask) | (旧数据 & ~write_mask)
```

这样就实现了"只改一个字节，其余字节保持不变"。

### 4.3 HTIF.sv — 测试控制器

HTIF（Host-Target Interface）是仿真专用的"测试裁判"，它监视处理器对特定地址（`tohost`）的写操作来判断测试结果。

**工作流程：**

```
处理器执行测试程序
    │
    │ 测试结束时执行：
    │   sw x1, 0(tohost)   ← 写入结果码（1=通过，3=失败）
    │   sw x0, 4(tohost)   ← 触发检测
    ▼
HTIF 的 capture_write() 检测到两次写操作
    │
    ▼
finalize() 被调用：
  - 读取 tohost_0 的值
  - 若为 1：打印 SUCCESS，保存签名
  - 若非 1：打印 FAILED，保存签名
  - 调用 $finish 结束仿真
```

**签名保存：**

HTIF 直接访问 ActRam 的内存数组，把 `rvtest_sig_begin` 到 `rvtest_sig_end` 之间的内容用 `$writememh` 写成十六进制文本文件（`.sig`）。

### 4.4 NeoRV32_Act_tb.sv — 顶层测试台

这是把所有模块连接在一起的"胶水"模块。

**连接关系：**

```
NeoRV32Core（处理器 DUT）
    │
    ├── io_imem_*  ←→  instruction_if 接口  ←→  ActRam（指令端口）
    │
    └── io_dmem_*  ←→  memory_direct_if 接口  ←→  ActRam（数据端口）
                                                        │
                                                   HTIF 监听写操作
```

**字节掩码转换：**

处理器输出的是 4 位字节使能信号 `dmem_req_mask`，而 ActRam 接受的是 2 位 `write_size`。测试台做了转换：

```systemverilog
// 数 mask 中有几个 1，就知道写了几个字节
wire [1:0] mask_ones = popcount(dmem_req_mask);
assign memory.write_size = (mask_ones == 1) ? 2'b00 :   // 字节
                           (mask_ones == 2) ? 2'b01 :   // 半字
                                              2'b10;    // 字
```

### 4.5 sim_main.cpp — Verilator 驱动

Verilator 把 SystemVerilog 代码编译成 C++ 类，`sim_main.cpp` 负责驱动这个类运行：

```
初始化
  │
  ├── 创建 VNeoRV32_Act_tb 对象
  ├── 开启 FST 波形记录
  │
  ▼
复位阶段（4 个时钟周期，rst=1）
  │
  ▼
仿真主循环：
  while (!Verilated::gotFinish()) {
      clk = 0; eval();   ← 下降沿
      clk = 1; eval();   ← 上升沿（触发时序逻辑）
  }
  │
  ▼
结束：打印总周期数，释放资源
```

---

## 五、run.py 执行流程

```
run.py
  │
  ├── 1. 读取 rv32i/test_config.yaml
  │
  ├── 2. 调用 make（在 riscv-arch-test 仓库）
  │       → 编译所有测试汇编源码为 ELF
  │
  ├── 3. 从第一个 ELF 提取入口地址（rvtest_entry_point = 0x80000000）
  │
  ├── 4. 调用 Verilator 编译 HDL
  │       → 输出可执行文件 obj_dir/VNeoRV32_Act_tb
  │
  └── 5. 对每个测试 ELF：
          │
          ├── objcopy 转换为 .hex（地址从 0 开始）
          ├── objdump 提取符号地址（sig_begin, sig_end, tohost）
          └── 运行仿真，传入 +plusargs 参数
                  → 输出 .sig（签名）、.sig.log（日志）、.fst（波形）
```

**为什么要把地址从 0 开始？**

ELF 文件中代码的虚拟地址是 `0x80000000`，但 `$readmemh` 从数组下标 0 开始加载。`objcopy --change-addresses -0x80000000` 把所有地址减去基地址，让数组下标和内存偏移对应。仿真时再通过 `-GRAM_BASE_ADDR=0x80000000` 告诉 ActRam 实际的物理基地址。

---

## 六、签名比对原理

每个测试的签名区在 ELF 的 `.data` 段，初始值是 `0xdeadbeef`（一个容易识别的魔数）。测试程序执行 store 指令时会修改其中部分字节。

**以 `I-sb-00` 为例：**

```
初始内存（签名区第一个字）：0xdeadbeef

执行 sb s5, 0(addr)，s5 = 0x7f

结果：0xdead be 7f   ← 只有最低字节被改写
```

Sail 参考模型也执行同样的程序，得到同样的签名。`run.py` 比对两个 `.sig` 文件，完全一致则 SUCCESS。

---

## 七、如何调试失败的测试

**1. 查看日志**

```bash
cat /home/re/project/riscv-arch-test/work/rv32i/build/rv32i/I/I-sb-00.sig.log
```

**2. 比对签名差异**

```bash
# 生成参考签名
sail_riscv_sim \
  --config /opt/sail/share/sail-riscv/config/rv32d_v128_e64.json \
  --test-signature /tmp/ref.sig \
  work/rv32i/elfs/rv32i/I/I-sb-00.elf

# 与 DUT 签名比对
diff work/rv32i/build/rv32i/I/I-sb-00.sig /tmp/ref.sig
```

**3. 查看波形**

```bash
gtkwave ACT4/waves/I-sb-00.fst
```

波形文件记录了仿真过程中所有信号的变化，可以精确定位是哪条指令、哪个周期出了问题。

**4. 反汇编测试程序**

```bash
riscv64-unknown-elf-objdump -d \
  work/rv32i/elfs/rv32i/I/I-sb-00.elf | less
```

---

## 八、常见问题

**Q：为什么跳过 Misalign 测试？**

非对齐访问（如把 32 位数据写到奇数地址）在标准 RISC-V 中会触发异常。本处理器不支持异常处理，所以跳过这类测试。

**Q：`tohost` 地址是什么？**

`tohost` 是测试程序和仿真环境通信的约定地址（`0x80006000`）。测试程序通过向这个地址写特定值来告知仿真器"我跑完了，结果是通过/失败"。这是 RISC-V 合规测试框架的标准约定。

**Q：签名区为什么初始化为 `0xdeadbeef`？**

这是一个容易识别的魔数。如果某个字节没有被测试程序修改，签名里就会保留 `de`、`ad`、`be`、`ef` 的某些字节，方便人眼识别哪些位置被写过、哪些没有。

**Q：`max_cycles` 是怎么算的？**

```python
max_cycles = (sig_end - entry_point) * MAX_CYCLES_FACTOR // 4
```

`sig_end - entry_point` 大约等于程序的字节数，除以 4 得到指令条数，再乘以 3 作为宽裕的超时上限（考虑到分支跳转会重复执行某些代码）。
