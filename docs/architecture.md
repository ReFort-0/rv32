# RV32 架构设计文档

## 概述

这是一个基于 Chisel 3 的开源 RISC-V RV32 SoC 生成器项目，通过单一参数化架构支持多种 CPU 与 SoC 配置组合。相比传统 Verilog 实现，在代码规模与可维护性方面有显著优势。

---

## 系统架构图

```
+----------------------------------+
|           NeoRV32SoC                |
|  +----------------------------+  |
|  |       NeoRV32Core CPU         |  |
|  |  +--------------------+    |  |
|  |  |   Pipeline Stages  |    |  |
|  |  |  +---+---+---+---+ |    |  |
|  |  |  | IF| ID| EX|MEM|WB|    |  |
|  |  |  +---+---+---+---+ |    |  |
|  |  |       (1/3/5级可配)      |  |
|  |  +--------------------+    |  |
|  +-----------|-----------------+  |
|              | Simple Bus        |
|  +-----------V-----------------+  |
|  |           Demux              |  |
|  +-----+------+------+------+---+  |
|        |      |      |      |      |
|    +---V--+ +V--+ +V--+ +V--+     |
|    |  RAM | |UART| |Timer| |GPIO|  |
|    +------+ +----+ +-----+ +----+  |
+----------------------------------+
```

---

## 目录结构

```
src/main/scala/
├── rv32/
│   ├── core/           # CPU 核心
│   │   ├── NeoRV32Core.scala     # 顶层 CPU（根据 PipelineStages 实例化）
│   │   │
│   │   ├── stage/               # 流水线各级（统一 Stage 模板）
│   │   │   ├── Stage.scala      # 参数化流水线级模板
│   │   │   ├── FetchStage.scala # IF 取指级
│   │   │   ├── DecodeStage.scala# ID 译码级
│   │   │   ├── ExecuteStage.scala# EX 执行级
│   │   │   ├── MemoryStage.scala# MEM 访存级
│   │   │   └── WritebackStage.scala# WB 写回级
│   │   │
│   │   ├── components/          # 功能组件
│   │   │   ├── CoreReg.scala    # 寄存器文件（x0-x31，UseRVE 时为 x0-x15）
│   │   │   ├── Decoder.scala    # 指令译码器（RV32I 指令子集，自动适配 E）
│   │   │   ├── ImmGen.scala     # 立即数生成器（I/S/B/U/J-type）
│   │   │   ├── Alu.scala        # ALU 运算单元
│   │   │   ├── MulDivUnit.scala # M 扩展乘除法单元
│   │   │   ├── HazardUnit.scala # 冒险检测
│   │   │   └── BypassNet.scala  # 数据旁路
│   │   │
│   │   ├── functionalunit/      # 可插拔功能单元（M/F 扩展模式）
│   │   │   ├── FunctionalUnit.scala# FU 基类
│   │   │   ├── FUDecoder.scala  # FU 调度器
│   │   │   ├── ExuBlock.scala   # 执行单元块
│   │   │   └── ALU.scala        # ALU（add/sub/and/or/xor/slt/sll/srl/sra）
│   │   │
│   │   ├── MicroOp.scala        # 通用微操作 Bundle
│   │   └── CoreBusIF.scala      # CPU→SoC 总线桥
│   │
│   ├── bus/            # 总线系统
│   │   ├── SimpleBus.scala      # 极简内存映射总线定义
│   │   └── BusArbiter.scala     # 总线仲裁器
│   │
│   ├── peripherals/    # 外设
│   │   ├── UART.scala           # UART 控制器
│   │   ├── Timer.scala          # 定时器
│   │   └── GPIO.scala           # GPIO 控制器
│   │
│   ├── soc/            # SoC 集成
│   │   ├── NeoRV32SOC.scala     # 顶层 SoC
│   │   ├── Demux.scala          # 地址解复用器
│   │   └── OnChipRAM.scala      # 片上 RAM
│   │
│   └── configs/        # 配置
│       ├── Parameters.scala     # 参数定义
│       └── Configurations.scala # 预定义配置
```

---

## 核心设计

### 1. 参数化系统 (Parameters)

使用 Chisel `Parameters` 模式，所有配置通过顶层 `p: Parameters` 传递：

```scala
// CPU 配置
abstract class CPUConfig extends Parameters {
  def useRVE: Boolean
  def useM: Boolean
  def pipelineStages: Int  // 1, 3, or 5
}

// SoC 配置
abstract class SOCConfig extends Parameters {
  def enableUART: Boolean
  def enableTimer: Boolean
  def enableGPIO: Boolean
  def onChipRAMSize: Int   // 默认 4KB
}
```

#### 预定义配置示例

| 配置名 | 描述 | 流水线 | 指令集 | 外设 |
|--------|------|--------|--------|------|
| E1T | RV32E 1级流水 + Timer | 1 | RV32E | Timer |
| IM3UG | RV32IM 3级 + UART + GPIO | 3 | RV32IM | UART, GPIO |
| I5UT | RV32I 5级 + UART + Timer | 5 | RV32I | UART, Timer |

---

### 2. CPU 核心架构 (NeoRV32Core)

#### 2.1 流水线结构（参数化实例化）

```scala
class NeoRV32Core(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val bus = new SimpleBus  // 连接 SoC
    val interrupt = Input(Bool()) // 中断请求（引出至顶层，CPU 不处理）
  })
  
  // 根据 PipelineStages 参数实例化不同流水线
  p(PipelineStages) match {
    case 1 => new SingleCycleCore  // 单周期（IF/ID/EX/MEM/WB 合一）
    case 3 => new ThreeStageCore   // 3级（Fetch | Decode/Execute | Memory/Writeback）
    case 5 => new FiveStageCore    // 5级（IF | ID | EX | MEM | WB）
  }
}
```

**统一 Stage 模板**：

```scala
class Stage(stageType: StageType)(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new MicroOp))   // 输入级间寄存器
    val out = Decoupled(new MicroOp)           // 输出到下一级
    val stall = Input(Bool())                   // 流水线暂停
    val flush = Input(Bool())                   // 流水线冲刷
  })
  // 根据 stageType 实现具体逻辑
}
```

#### 2.2 流水线级功能

| 级 | 功能 | 关键操作 | 实例模块 |
|----|------|----------|----------|
| IF | 取指 | PC 生成、指令存储器访问 | `FetchStage.scala` |
| ID | 译码 | 指令解码、寄存器读取、冒险检测 | `DecodeStage.scala` |
| EX | 执行 | ALU/FU 运算、分支判断、地址计算 | `ExecuteStage.scala` |
| MEM | 访存 | 数据存储器访问、Load/Store | `MemoryStage.scala` |
| WB | 写回 | 结果写回寄存器 | `WritebackStage.scala` |

#### 2.3 功能组件 (components/)

**寄存器文件** (`CoreReg.scala`)：
```scala
class CoreReg(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val rs1 = Input(UInt(5.W))
    val rs2 = Input(UInt(5.W))
    val rd = Input(UInt(5.W))
    val wdata = Input(UInt(32.W))
    val wen = Input(Bool())
    val rdata1 = Output(UInt(32.W))
    val rdata2 = Output(UInt(32.W))
  })
  // 根据 UseRVE 决定寄存器数量（16 vs 32）
  val numRegs = if (p(UseRVE)) 16 else 32
}
```

**指令译码** (`Decoder.scala` + `ImmGen.scala`)：
```scala
class Decoder(implicit p: Parameters) extends Module {
  // 统一解码 RV32I 指令集，自动适配 E（仅使用低 16 寄存器）
  // 输出：控制信号 + 立即数类型
}

class ImmGen extends Module {
  // 生成 I/S/B/U/J-type 立即数
}
```

**ALU** (`Alu.scala`)：
```scala
class Alu extends Module {
  // add/sub/and/or/xor/slt/sltu/sll/srl/sra
}
```

**乘除法单元** (`MulDivUnit.scala`，可选 M 扩展)：
```scala
class MulDivUnit(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val op = Input(UInt(4.W))    // mul/mulh/mulhsu/mulhu/div/rem/divu/remu
    val a = Input(UInt(32.W))
    val b = Input(UInt(32.W))
    val result = Output(UInt(32.W))
    val busy = Output(Bool())      // 多周期操作指示
  })
  // 仅在 p(UseM) 时实例化
}
```

**流水线控制**：
- `HazardUnit.scala`：检测数据冒险、控制冒险，生成 stall/flush 信号
- `BypassNet.scala`：数据前递/旁路网络（EX→ID, MEM→ID, WB→ID）

#### 2.4 可插拔功能单元 (functionalunit/)

M 扩展实现为可插拔 FU，便于未来扩展 F/D 等：

```scala
// FU 基类 trait
trait FunctionalUnit {
  def supportedOps: Set[UInt]
  def execute(op: UInt, a: UInt, b: UInt): UInt
  def latency: Int  // 周期数（ALU=1, MUL=2-3, DIV=32）
}

// FU 调度器：根据 opcode 选择执行单元
class FUDecoder extends Module {
  // ALU 总是存在
  // MUL/DIV 根据 p(UseM) 动态实例化
}

// 执行单元块：聚合所有 FU 输出
class ExuBlock(implicit p: Parameters) extends Module {
  val alu = Module(new ALU())        // 始终存在
  val mul = p(UseM).option(Module(new MulUnit()))
  val div = p(UseM).option(Module(new DivUnit()))
  // 输出选择逻辑
}
```

**扩展性设计**：未来添加 F 扩展只需：
1. 实现 `FPU.scala` 继承 `FunctionalUnit`
2. 在 `FUDecoder` 注册 FPU opcode
3. **无需改动核心流水线级**

**扩展性设计：** 未来添加 F 扩展只需实现新的 FU 并注册，无需改动核心流水线。

---

### 3. 总线系统 (Simple Bus)

采用自定义极简内存映射总线（非 AXI4/Wishbone）：

```scala
class SimpleBusReq extends Bundle {
  val addr = UInt(32.W)
  val wdata = UInt(32.W)
  val wen = Bool()
  val ren = Bool()
  val wmask = UInt(4.W)
}

class SimpleBusResp extends Bundle {
  val rdata = UInt(32.W)
  val valid = Bool()
  val error = Bool()
}

class SimpleBus extends Bundle {
  val req = Output(new SimpleBusReq)
  val resp = Input(new SimpleBusResp)
}
```

---

### 4. SoC 外围

#### 4.1 地址空间 (静态硬编码)

| 区域 | 起始地址 | 大小 | 说明 |
|------|----------|------|------|
| RAM | 0x0000_0000 | 4KB | 片上内存 |
| UART | 0x1000_0000 | 4B | TX/RX 数据寄存器 |
| Timer | 0x2000_0000 | 16B | 计数器/比较器/控制 |
| GPIO | 0x3000_0000 | 8B | 方向/数据寄存器 |

#### 4.2 外设实现

**UART:** 基础 TX/RX FIFO，波特率可配置
**Timer:** 64-bit 计数器，支持比较中断（信号引出至顶层，CPU 不处理中断）
**GPIO:** 32-bit 通用输入输出，独立方向控制

#### 4.3 地址解复用器 (Demux)

```scala
class Demux(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val cpu = Flipped(new SimpleBus)
    val ram = new SimpleBus
    val uart = p(EnableUART).option(new SimpleBus)
    val timer = p(EnableTimer).option(new SimpleBus)
    val gpio = p(EnableGPIO).option(new SimpleBus)
  })
  // 根据地址路由请求
}
```

---

## 关键约束

| 约束 | 说明 |
|------|------|
| 单核 | 不支持多核架构 |
| 无异常/中断 | CPU 核心不实现 trap/CSR，中断请求线从外设引出至顶层 |
| 精简外设 | SoC 外围逻辑极度精简，避免不必要的复杂性 |
| 静态地址 | 地址空间硬编码，不自动生成地址解码表 |

---

## 验证与综合

### 自动化脚本

`generate-configs.scala`: 自动遍历典型配置并生成 Verilog

```scala
val configs = Seq(
  ("E1T",   RV32E_1Stage_Timer),
  ("IM3UG", RV32IM_3Stage_UART_GPIO),
  ("I5UT",  RV32I_5Stage_UART_Timer)
)

configs.foreach { case (name, cfg) =>
  val verilog = generateVerilog(cfg)
  val loc = countLines(verilog)
  println(s"$name: $loc lines")
}
```

### 综合输出要求

- 无不可综合的初始化
- 命名清晰，模块层级扁平化
- 可综合为目标 FPGA/ASIC

---

## Chisel 优势展示

### 1. 代码行数对比

| 模块 | 预估 Chisel LOC | 预估 Verilog LOC | 缩减比例 |
|------|-----------------|------------------|----------|
| CPU Core | ~800 | ~3000 | 73% |
| 3级流水适配 | +50 | +1500 | 97% |
| 5级流水适配 | +50 | +1500 | 97% |
| M 扩展 | +200 | +800 | 75% |
| SoC 总线 | ~100 | ~400 | 75% |
| 外设集合 | ~300 | ~1000 | 70% |

**关键优势：** 修改配置仅需改动几行参数代码，而非重写整套 RTL。

### 2. 配置灵活性

```scala
// 一键切换整个 SoC 配置
val config = Parameters.empty.alter((site, here, up) => {
  case PipelineStages => 5
  case UseRVE         => false
  case UseM           => true
  case EnableUART     => true
  case EnableGPIO     => false
})
```

### 3. 可视化复用性

所有组件均由 `Parameters` 动态"按需"实例化：

```
[NeoCore CPU] --参数化生成--> [1/3/5级流水线]
                                    |
                                    v
                            [Simple Bus]
                                    |
                                    v
                            [Demux] --根据配置实例化-->
                                    +-- [On-Chip RAM] (始终存在)
                                    +-- [UART] (EnableUART 时)
                                    +-- [Timer] (EnableTimer 时)
                                    +-- [GPIO] (EnableGPIO 时)
```

---
F
## 交付物清单

| 路径 | 内容 |
|------|------|
| `src/main/scala/core/` | 参数化 CPU 核心 |
| `src/main/scala/bus/` | 轻量总线系统 |
| `src/main/scala/peripherals/` | UART/Timer/GPIO 外设 |
| `src/main/scala/soc/` | SoC 集成 (NeoRV32SOC, Demux, RAM) |
| `src/main/scala/configs/` | 预定义配置对象 |
| `docs/architecture.md` | 本文档 |
| `README.md` | 快速入门、配置示例、综合结果 |
| `scripts/generate-configs.scala` | 自动化生成脚本 |

---

## 后续扩展建议

1. **F 扩展 (浮点)**：新增 `FPUnit` 功能单元
2. **中断控制器**：添加简易中断聚合模块
3. **DMA 引擎**：扩展总线支持 DMA Master
4. **调试接口**：添加 JTAG TAP 控制器
5. **BootROM**：支持从 ROM 启动
