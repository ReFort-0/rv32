
### 🎯 **核心目标**
设计一个基于 Chisel 3 的开源 RISC-V RV32 SoC 生成器，通过单一参数化架构支持多种 CPU 与 SoC 配置组合，并显著优于传统 Verilog 实现在代码规模与可维护性方面。重点展示：
- **一键切换 CPU 流水线深度**（1/3/5 级）
- **一键切换指令集**（RV32E vs RV32I + 可选 M）
- **一键启用/禁用 SoC 外设**（UART, Timer, GPIO）
- **生成可综合 RTL 并提供自动化验证脚本**

---

### ⚙️ **关键设计约束**

#### **1. 参数化系统 (case class)**
- 使用 **简洁的 case class 模式**，所有配置通过 `NeoRV32Config` 传递。
- **CPU 配置** (`CoreConfig`):
  ```scala
  case class CoreConfig(
    xlen: Int = 32,              // 数据位宽
    useRV32E: Boolean = false,    // RV32E 指令集（16 寄存器）
    useM: Boolean = false,         // M 扩展（乘除法）
    pipelineStages: Int = 3        // 1, 3, or 5
  )
  ```
- **SoC 配置** (`SoCConfig`):
  ```scala
  case class SoCConfig(
    enableUART: Boolean = false,   // UART 外设
    enableTimer: Boolean = true,   // 定时器
    enableGPIO: Boolean = false,   // GPIO
    onChipRAMSize: Int = 4 * 1024 // 片上内存大小
  )
  ```
- **组合配置**:
  ```scala
  case class NeoRV32Config(core: CoreConfig, soc: SoCConfig)
  ```
- **预定义配置** (`Configs` 对象):
  - `E1T`   - RV32E + 1-stage + Timer
  - `IM3UG` - RV32IM + 3-stage + UART + GPIO
  - `I5UT`  - RV32I + 5-stage + UART + Timer

#### **2. CPU 核心架构**
- **流水线级**（IF/ID/EX/MEM/WB）由**独立的 Stage 类**实现：`FetchStage`, `DecodeStage`, `ExecuteStage`, `MemoryStage`, `WritebackStage`。
- 各 `Stage` 接收/输出 **统一的 `MicroOp` Bundle**，包含所有控制信号和数据字段。使用参数化生成避免字段膨胀：
  ```scala
  // 根据配置动态生成所需字段，而非固定大 Bundle
  class MicroOp(config: CoreConfig) extends Bundle {
    val rs1_addr = if (config.useRV32E) UInt(4.W) else UInt(5.W)
    val rs2_addr = if (config.useRV32E) UInt(4.W) else UInt(5.W)
    val useMulDiv = if (config.useM) Bool() else null
    // ... 仅包含配置所需字段
  }
  ```
- `NeoRV32Core` 根据 `pipelineStages` 参数使用不同的连接函数：`connect1Stage()` / `connect3Stage()` / `connect5Stage()`。
- **实现优先级**: 3级流水线作为基础验证目标，1级和5级作为后续扩展。
- **M 扩展**：实现为 `MulDivUnit` + `ExuBlock`，在 EX 阶段根据 `fu_sel` 调度。仅当 `useM=true` 时实例化。使用清晰的 `valid/ready` 握手支持多周期操作，支持流水线停顿/旁路。

#### **3. SoC 外围 (Minimal & Lightweight)**
- **总线**：采用自定义的 **极简内存映射总线**（`SimpleBus`），仅包含必要信号（`addr`, `wdata`, `rdata`, `wen`, `mask`, `valid`）。
- **流水线连接工具**：`PipelineConnect.scala` 提供标准化的流水线寄存器：
  ```scala
  // 支持停顿(stall)和冲刷(flush)的流水线寄存器
  object PipelineConnect {
    def apply[T <: Data](
      stage: String,
      valid: Bool, ready: Bool, 
      data: T, stall: Bool, flush: Bool
    ): T = {
      // 实现带 enable 的寄存器，stall 时保持，flush 时清零
    }
  }
  ```
  采用 `Decoupled` 风格握手接口，确保各级 Stage 正确传递控制信号。
- **外设**：
  - **UART**: 基础 TX/RX FIFO。
  - **Timer**: 64-bit 计数器，支持比较中断（信号引出，CPU 不处理）。
  - **GPIO**: 32-bit 通用输入输出。
- **地址空间**：使用**静态硬编码地址**（例如 UART @ `0x1000_0000`），**不自动生成地址解码表**。
- **连接**：CPU 通过总线 Master 接口连接到 **中央解复用器**（`Demux`），后者根据地址路由到 RAM 或外设。

#### **4. 其他约束**
- **单核**：不支持多核。
- **无异常/中断处理**：CPU 核心不实现 trap/CSR，但中断请求线从外设引出至顶层。
- **宗旨**：SoC 外围逻辑必须**精简**，避免任何不必要的复杂性。

---

### 🧪 **验证与综合友好性**

#### **自动化脚本 (`generate-configs.scala`)**
- 自动遍历典型配置并生成完整 SoC 的 Verilog，如：
  - `RV32E + 1-stage + Timer`
  - `RV32I + M + 3-stage + UART + GPIO`
  - `RV32I + 5-stage + UART + Timer`
- 每次生成后自动统计 **总 Verilog 行数**（LOC）。

#### **综合输出要求**
- 生成的 Verilog 应：
  - 无不可综合的初始化
  - 命名清晰，模块层级扁平化
  - **使用 `suggestName` 确保信号名有意义**，提升可读性

#### **测试策略**
- **单元测试**：各 Stage 独立测试（`FetchStageSpec`, `ExecuteStageSpec` 等）
- **核心测试**：`NeoRV32CoreSpec` 使用 [riscv-arch-test](https://github.com/riscv-non-isa/riscv-arch-test) 进行指令级对比验证
- **配置覆盖**：CI 自动验证预定义配置（E1T, IM3UG, I5UT）的生成和基本功能

---

### 📊 **Chisel 优势展示**

#### **1. 代码行数减少**

| 模块 | 预估 Chisel LOC | 预估 Verilog LOC | 缩减比例 |
|------|-----------------|------------------|----------|
| CPU Core | ~800 | ~3000 | 73% |
| 3级流水适配 | +50 | +1500 | 97% |
| 5级流水适配 | +50 | +1500 | 97% |
| M 扩展 | +200 | ~800 | 75% |
| SoC 总线 | ~100 | ~400 | 75% |
| 外设集合 | ~300 | ~1000 | 70% |

#### **2. 配置灵活性**
- 使用预定义配置：
  ```scala
  val config = Configs.IM3UG  // RV32IM + 3-stage + UART + GPIO
  val core = Module(new NeoRV32Core(config))
  ```
- 或自定义：
  ```scala
  val config = NeoRV32Config(
    core = CoreConfig(useM = true, pipelineStages = 5),
    soc = SoCConfig(enableUART = true, enableTimer = true)
  )
  ```

#### **3. 可视化复用性**
- 在文档中包含 **代码结构图**，展示：
  ```
  [NeoRV32Core CPU] --配置生成--> [1/3/5级流水线]
                                      |
                                      v
                               [SimpleBus]
                                      |
                                      v
                          [Demux] --地址路由-->
                                      +-- [On-Chip RAM] (始终存在)
                                      +-- [UART] (if enabled)
                                      +-- [Timer] (if enabled)
                                      +-- [GPIO] (if enabled)
  ```
  强调：**所有组件均由配置动态"按需"实例化**。

---

### 🚦 **执行优先级**

| 阶段 | 目标 | 配置覆盖 | 验证标准 |
|------|------|----------|----------|
| **P0（核心）** | 基础流水线验证 | RV32I + 3-stage + SimpleBus + RAM + Timer | riscv-arch-test 基本指令集通过 |
| **P1（扩展）** | 配置灵活性展示 | 1/5 级流水线切换 + UART + GPIO | 所有预定义配置可生成正确 Verilog |
| **P2（完整）** | 完整功能 | M 扩展 + 自动化脚本 + LOC 统计 | 乘除法测试通过，代码量对比数据完整 |
| **P3（优化）** | 工程完善 | 文档完善 + 综合脚本 + 性能对比 | README 完整，可复现的综合流程 |

---

### 🛠️ **交付物清单**
- `src/main/scala/rv32/core/`: 参数化 CPU 核心
- `src/main/scala/rv32/soc/`: 轻量总线、Demux、片上 RAM、外设（UART/Timer/GPIO）
- `src/main/scala/rv32/configs/`: 配置定义与预定义配置
- `src/main/scala/rv32/core/util/PipelineConnect.scala`: 流水线连接工具（未来可集成）
- `docs/architecture.md`: 详细架构说明

