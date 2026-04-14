# RV32 Chisel 框架目录结构设计

## 设计原则

### 1. 模块化设计（体现Chisel与Verilog的区别）

**Verilog方式：**
- 模块通过端口列表连接，信号需要手动声明input/output
- 参数通过parameter传递，层级深时冗长
- 条件编译使用`ifdef，难以维护

**Chisel方式：**
- 使用Bundle封装信号，类型安全，编译期检查
- IO接口定义为Input/Output，语义清晰
- 使用Scala的类和对象，支持面向对象和函数式编程
- 配置通过case class传递，可包含复杂逻辑

### 2. 命名规范

- 文件名：驼峰命名（如 RegFile.scala, Alu.scala）
- 类名：驼峰命名（如 RVCore, BranchPredictor）
- 对象名：驼峰命名（如 CpuConfig, AluOpType）
- 函数名：驼峰命名（如 decodeInst, calcBranch）
- 信号名：驼峰命名（如 pcNext, instValid）


---

## 目录结构

```
src/
├── main/scala/rv32/
│   ├── config/
│   │   └── CpuConfig.scala          # CPU配置参数（模式、特性开关）
│   │
│   ├── types/
│   │   └── Definitions.scala        # 通用类型定义（指令、寄存器、ALU操作枚举）
│   │
│   ├── core/
│   │   ├── Rv32Core.scala           # CPU顶层模块（根据配置生成不同流水线）
│   │   ├── SingleCycleCpu.scala     # 单周期实现
│   │   ├── ThreeStageCpu.scala      # 三级流水线
│   │   └── FiveStageCpu.scala       # 五级流水线
│   │
│   ├── pipeline/
│   │   ├── stage/
│   │   │   ├── Fetch.scala     # 取指阶段
│   │   │   ├── Decode.scala    # 译码阶段
│   │   │   ├── Execute.scala   # 执行阶段
│   │   │   ├── Memory.scala    # 访存阶段
│   │   │   └── Writeback.scala # 写回阶段
│   │   │
│   │   └── regs/
│   │       ├── IfIdReg.scala        # IF/ID流水线寄存器
│   │       ├── IdExReg.scala        # ID/EX流水线寄存器
│   │       ├── ExMemReg.scala       # EX/MEM流水线寄存器
│   │       └── MemWbReg.scala       # MEM/WB流水线寄存器
│   │
│   ├── components/
│   │   ├── Alu.scala            # ALU运算单元
│   │   ├── CoreReg.scala            # 寄存器文件
│   │   ├── ControlUnit.scala        # 控制单元（指令译码）
│   │   ├── ImmGen.scala             # 立即数生成器
│   │   ├── BranchPredictor.scala    # 分支预测器（可选）
│   │   ├── HazardUnit.scala         # 冒险检测单元
│   │   └── ForwardingUnit.scala     # 前递单元
│   │
│   └── memory/
│       ├── InstMemory.scala         # 指令存储器接口
│       └── DataMemory.scala         # 数据存储器接口
│
└── test/scala/rv32/
    │
    ├── core/
    │   ├── SingleCycleCpuSpec.scala # 单周期测试
    │   ├── ThreeStageCpuSpec.scala  # 三级流水线测试
    │   └── FiveStageCpuSpec.scala   # 五级流水线测试
    │
    ├── pipeline/
    │   ├── stage/
    │   │   ├── FetchSpec.scala
    │   │   ├── DecodeSpec.scala
    │   │   ├── ExecuSpecageSpec.scala
    │   │   ├── MemorySpec.scala
    │   │   └── WritebackSpec.scala
    │   │
    │   └── regs/
    │       ├── IfIdRegSpec.scala
    │       ├── IdExRegSpec.scala
    │       ├── ExMemRegSpec.scala
    │       └── MemWbRegSpec.scala
    │
    ├── components/
    │   ├── AluSpec.scala
    │   ├── CoreRegSpec.scala
    │   ├── ControlUnitSpec.scala
    │   ├── ImmGenSpec.scala
    │   ├── BranchPredictorSpec.scala
    │   ├── HazardUnitSpec.scala
    │   └── ForwardingUnitSpec.scala
    │
    └── memory/
        ├── InstMemorySpec.scala
        └── DataMemorySpec.scala
```

---

**Chisel方式：**
```scala
class CoreReg extends Module {
  val io = IO(new Bundle {
    val we = Input(Bool())
    val raddr1 = Input(UInt(5.W))
    val raddr2 = Input(UInt(5.W))
    val waddr = Input(UInt(5.W))
    val wdata = Input(UInt(32.W))
    val rdata1 = Output(UInt(32.W))
    val rdata2 = Output(UInt(32.W))
  })
  
  // 使用Scala的Seq和Reg，支持高阶函数
  val regs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  
  when(io.we && io.waddr =/= 0.U) {
    regs(io.waddr) := io.wdata
  }
  
  // x0硬连线处理 - 使用Chisel的Mux，语义更清晰
  io.rdata1 := Mux(io.raddr1 === 0.U, 0.U, regs(io.raddr1))
  io.rdata2 := Mux(io.raddr2 === 0.U, 0.U, regs(io.raddr2))
}
```

### 示例2: ALU运算单元

**Chisel方式：**
```scala
object AluOp {
  // 使用Scala object定义常量，编译期检查
  val ADD = 0.U(4.W)
  val SUB = 1.U(4.W)
  val AND = 2.U(4.W)
  val OR = 3.U(4.W)
  val XOR = 4.U(4.W)
  val SLT = 5.U(4.W)
  val SLL = 7.U(4.W)
  val SRL = 8.U(4.W)
  val SRA = 9.U(4.W)
}

class Alu extends Module {
  val io = IO(new Bundle {
    val op = Input(UInt(4.W))
    val a = Input(UInt(32.W))
    val b = Input(UInt(32.W))
    val result = Output(UInt(32.W))
    val zero = Output(Bool())
  })
  
  io.result := MuxCase(0.U, Seq(
    (io.op === AluOp.ADD) -> (io.a + io.b),
    (io.op === AluOp.SUB) -> (io.a - io.b),
    (io.op === AluOp.AND) -> (io.a & io.b),
    (io.op === AluOp.OR) -> (io.a | io.b),
    (io.op === AluOp.XOR) -> (io.a ^ io.b),
    (io.op === AluOp.SLT) -> (io.a.asSInt < io.b.asSInt).asUInt,
    (io.op === AluOp.SLL) -> (io.a << io.b(4, 0)),
    (io.op === AluOp.SRL) -> (io.a >> io.b(4, 0)),
    (io.op === AluOp.SRA) -> (io.a.asSInt >> io.b(4, 0)).asUInt
  ))
  
  io.zero := io.result === 0.U
}
```

### 示例3: 配置传递

**Chisel方式：**
```scala
// 使用Scala case class，类型安全，编译期检查
case class CpuConfig(
    mode: PipelineMode,
    enableBranchPrediction: Boolean,
    enableForwarding: Boolean
)

sealed trait PipelineMode
case object SingleCycle extends PipelineMode
case object FiveStage extends PipelineMode

class Rv32Core(config: CpuConfig) extends Module {
  // 根据配置动态生成不同架构
  val cpu = config.mode match {
    case SingleCycle => Module(new SingleCycleCpu())
    case FiveStage => Module(new FiveStageCpu())
  }
  
  // Scala的灵活性：可以在生成时决定包含哪些模块
  if (config.enableForwarding) {
    val forwardingUnit = Module(new ForwardingUnit())
    // ...
  }
}
```

---

## 测试策略

### Chisel测试 vs Verilog测试

**Verilog测试：**
- 需要编写testbench，使用initial块生成激励
- 波形查看需要手动dump
- 断言检查有限

**Chisel测试（使用ScalaTest）：**
- 使用poke函数驱动输入，expect检查输出
- 支持Scala的测试框架，断言丰富
- 可生成VCD波形用于调试
- 支持参数化测试

### 测试示例

```scala
class AluUnitTest extends ChiselScalatestTester with Matchers {
  // 参数化测试 - Chisel优势
  "AluUnit" should "handle all operations" in {
    val testCases = Seq(
      (AluOp.ADD, 5, 3, 8),
      (AluOp.SUB, 5, 3, 2),
      (AluOp.AND, 0xF, 0x3, 0x3),
      (AluOp.OR, 0xC, 0x3, 0xF)
    )
  }
}
```

---

## 完整目录结构

```
src/
├── main/scala/rv32/
│   ├── config/
│   │   └── CpuConfig.scala          # CPU配置参数（模式、特性开关）
│   │   └── SocConfig.scala          # SOC配置（外设、地址映射、中断）
│   │
│   ├── types/
│   │   └── Definitions.scala        # 通用类型定义（指令、寄存器、ALU操作枚举）
│   │   └── BusProtocol.scala        # 总线协议类型定义（AXI/AHB/APB或自定义）
│   │
│   ├── core/
│   │   ├── Rv32Core.scala           # CPU顶层模块（根据配置生成不同流水线）
│   │   ├── SingleCycleCpu.scala     # 单周期实现
│   │   ├── ThreeStageCpu.scala      # 三级流水线
│   │   └── FiveStageCpu.scala       # 五级流水线
│   │
│   ├── pipeline/
│   │   ├── stage/
│   │   │   ├── Fetch.scala          # 取指阶段
│   │   │   ├── Decode.scala         # 译码阶段
│   │   │   ├── Execute.scala        # 执行阶段
│   │   │   ├── Memory.scala         # 访存阶段
│   │   │   └── Writeback.scala      # 写回阶段
│   │   │
│   │   └── regs/
│   │       ├── IfIdReg.scala        # IF/ID流水线寄存器
│   │       ├── IdExReg.scala        # ID/EX流水线寄存器
│   │       ├── ExMemReg.scala       # EX/MEM流水线寄存器
│   │       └── MemWbReg.scala       # MEM/WB流水线寄存器
│   │
│   ├── components/
│   │   ├── Alu.scala                # ALU运算单元
│   │   ├── CoreReg.scala            # 寄存器文件
│   │   ├── ControlUnit.scala        # 控制单元（指令译码）
│   │   ├── ImmGen.scala             # 立即数生成器
│   │   ├── BranchPredictor.scala    # 分支预测器（可选）
│   │   ├── HazardUnit.scala         # 冒险检测单元
│   │   └── ForwardingUnit.scala     # 前递单元
│   │
│   ├── csr/
│   │   ├── CsrFile.scala            # 控制状态寄存器文件
│   │   ├── CsrTypes.scala           # CSR类型定义
│   │   ├── ExceptionUnit.scala      # 异常处理单元
│   │   └── InterruptCtrl.scala      # 中断控制器
│   │
│   ├── soc/
│   │   ├── Rv32Soc.scala            # SOC顶层模块（集成CPU+外设）
│   │   ├── bus/
│   │   │   ├── BusMatrix.scala      # 总线矩阵（多主从仲裁）
│   │   │   ├── BusDecoder.scala     # 总线地址解码器
│   │   │   └── BusBridge.scala      # 总线桥接器（如AXI转APB）
│   │   │
│   │   ├── peripherals/
│   │   │   ├── UartCtrl.scala       # UART控制器
│   │   │   ├── SpiCtrl.scala        # SPI控制器
│   │   │   ├── I2cCtrl.scala        # I2C控制器
│   │   │   ├── GpioCtrl.scala       # GPIO控制器
│   │   │   ├── TimerCtrl.scala      # 定时器/计数器
│   │   │   ├── WdtCtrl.scala        # 看门狗定时器
│   │   │   └── PwmCtrl.scala        # PWM控制器
│   │   │
│   │   ├── memory/
│   │   │   ├── RamCtrl.scala        # SRAM控制器
│   │   │   ├── RomCtrl.scala        # ROM控制器（启动代码）
│   │   │   └── FlashCtrl.scala      # Flash控制器
│   │   │
│   │   ├── dma/
│   │   │   ├── DmaCtrl.scala        # DMA控制器
│   │   │   └── DmaChannel.scala     # DMA通道
│   │   │
│   │   └── interconnect/
│   │       ├── Crossbar.scala       # 交叉开关
│   │       └── Arbiter.scala        # 仲裁器
│   │
│   ├── debug/
│   │   ├── JtagCtrl.scala           # JTAG调试控制器
│   │   ├── DtmCtrl.scala            # 调试传输模块
│   │   └── TriggerUnit.scala        # 触发器单元
│   │
│   └── system/
│       ├── ClockDomain.scala        # 时钟域管理
│       ├── ResetCtrl.scala          # 复位控制器
│       └── PowerCtrl.scala          # 电源管理
│
└── test/scala/rv32/
    ├── config/
    │   └── CpuConfigSpec.scala
    │   └── SocConfigSpec.scala
    │
    ├── core/
    │   ├── SingleCycleCpuSpec.scala # 单周期测试
    │   ├── ThreeStageCpuSpec.scala  # 三级流水线测试
    │   └── FiveStageCpuSpec.scala   # 五级流水线测试
    │
    ├── pipeline/
    │   ├── stage/
    │   │   ├── FetchSpec.scala
    │   │   ├── DecodeSpec.scala
    │   │   ├── ExecuteSpec.scala
    │   │   ├── MemorySpec.scala
    │   │   └── WritebackSpec.scala
    │   │
    │   └── regs/
    │       ├── IfIdRegSpec.scala
    │       ├── IdExRegSpec.scala
    │       ├── ExMemRegSpec.scala
    │       └── MemWbRegSpec.scala
    │
    ├── components/
    │   ├── AluSpec.scala
    │   ├── CoreRegSpec.scala
    │   ├── ControlUnitSpec.scala
    │   ├── ImmGenSpec.scala
    │   ├── BranchPredictorSpec.scala
    │   ├── HazardUnitSpec.scala
    │   └── ForwardingUnitSpec.scala
    │
    ├── csr/
    │   ├── CsrFileSpec.scala
    │   ├── ExceptionUnitSpec.scala
    │   └── InterruptCtrlSpec.scala
    │
    ├── soc/
    │   ├── Rv32SocSpec.scala        # SOC集成测试
    │   ├── bus/
    │   │   ├── BusMatrixSpec.scala
    │   │   ├── BusDecoderSpec.scala
    │   │   └── BusBridgeSpec.scala
    │   │
    │   ├── peripherals/
    │   │   ├── UartCtrlSpec.scala
    │   │   ├── SpiCtrlSpec.scala
    │   │   ├── I2cCtrlSpec.scala
    │   │   ├── GpioCtrlSpec.scala
    │   │   ├── TimerCtrlSpec.scala
    │   │   ├── WdtCtrlSpec.scala
    │   │   └── PwmCtrlSpec.scala
    │   │
    │   ├── memory/
    │   │   ├── RamCtrlSpec.scala
    │   │   ├── RomCtrlSpec.scala
    │   │   └── FlashCtrlSpec.scala
    │   │
    │   └── dma/
    │       ├── DmaCtrlSpec.scala
    │       └── DmaChannelSpec.scala
    │
    └── debug/
        ├── JtagCtrlSpec.scala
        └── DtmCtrlSpec.scala
```

---

## SOC架构设计

### 1. SOC顶层模块

**Chisel方式：**
```scala
case class SocConfig(
    cpuConfig: CpuConfig,
    ramSize: Int = 64 * 1024,        // 64KB RAM
    romSize: Int = 16 * 1024,        // 16KB ROM
    enableDma: Boolean = true,
    enableDebug: Boolean = true,
    uartBaudRate: Int = 115200,
    timerFreq: Int = 1000000         // 1MHz
)

class Rv32Soc(socConfig: SocConfig) extends Module {
  val io = IO(new Bundle {
    // 外部接口
    val uartTx = Output(Bool())
    val uartRx = Input(Bool())
    val gpioPins = Vec(16, Bidirectional(Bool()))
    val spiClk = Output(Bool())
    val spiMosi = Output(Bool())
    val spiMiso = Input(Bool())
    val debugJtag = new JtagInterface()
  })
  
  // CPU实例
  val cpu = Module(new Rv32Core(socConfig.cpuConfig))
  
  // 总线矩阵
  val busMatrix = Module(new BusMatrix(
    masters = 2,  // CPU + DMA
    slaves = 6    // RAM, ROM, UART, GPIO, SPI, Timer
  ))
  
  // 外设实例
  val uart = Module(new UartCtrl(socConfig.uartBaudRate))
  val gpio = Module(new GpioCtrl(16))
  val timer = Module(new TimerCtrl(socConfig.timerFreq))
  val ram = Module(new RamCtrl(socConfig.ramSize))
  val rom = Module(new RomCtrl(socConfig.romSize))
  
  // 连接CPU到总线
  busMatrix.io.masters(0) <> cpu.io.bus
  
  // 连接外设到总线
  busMatrix.io.slaves(0) <> ram.io.bus
  busMatrix.io.slaves(1) <> rom.io.bus
  busMatrix.io.slaves(2) <> uart.io.bus
  
  // 外部引脚连接
  io.uartTx := uart.io.txPin
  uart.io.rxPin := io.uartRx
}
```

### 2. 地址映射

**Chisel优势：使用Scala定义地址空间，类型安全**

```scala
object AddrMap {
  // 使用case class定义地址区域
  case class MemRegion(start: Long, size: Long, name: String) {
    def contains(addr: Long): Boolean = 
      addr >= start && addr < start + size
  }
  
  // 地址空间定义
  val regions = Seq(
    MemRegion(0x00000000, 64.K, "RAM"),
    MemRegion(0x10000000, 16.K, "ROM"),
    MemRegion(0x20000000, 1.K,  "UART"),
    MemRegion(0x20001000, 1.K,  "GPIO"),
    MemRegion(0x20002000, 1.K,  "SPI"),
    MemRegion(0x20003000, 1.K,  "Timer"),
    MemRegion(0x20004000, 1.K,  "DMA"),
    MemRegion(0x20005000, 1.K,  "CSR")
  )
}
```

### 3. 总线矩阵

**Verilog方式：**
```verilog
// 需要大量case语句和优先级编码器
// 主从设备数量变化时需要大量修改
// 容易出错
```

**Chisel方式：**
```scala
class BusMatrix(masters: Int, slaves: Int) extends Module {
  val io = IO(new Bundle {
    val masters = Flipped(Vec(masters, new BusInterface()))
    val slaves = Vec(slaves, new BusInterface())
  })
  
  // 使用Scala的函数式编程实现仲裁
  // 可以根据配置动态生成连接
  // 支持AXI/AHB/APB等多种协议
  
  // 地址解码 - 使用Scala的灵活性
  val decoder = Module(new BusDecoder(AddrMap.regions))
  
  // 仲裁逻辑 - 使用Chisel的Vec和Mux
  for (m <- 0 until masters) {
    val granted = decoder.io.grant(m)
    io.slaves(granted) <> io.masters(m)
  }
}
```

### 4. UART控制器

**Chisel方式：**
```scala
class UartCtrl(baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val bus = Flipped(new BusInterface())
    val txPin = Output(Bool())
    val rxPin = Input(Bool())
  })
  
  // 波特率发生器
  val baudGen = Module(new BaudGenerator(baudRate))
  
  // 发送FIFO
  val txFifo = Module(new Queue(UInt(8.W), 16))
  
  // 接收FIFO
  val rxFifo = Module(new Queue(UInt(8.W), 16))
  
  // 发送状态机
  val txState = RegInit(0.U(2.W))
  
  switch(txState) {
    is(0.U) { // 空闲
      when(txFifo.io.deq.valid) {
        txFifo.io.deq.ready := true.B
        txState := 1.U
      }
    }
    is(1.U) { // 发送起始位
      io.txPin := 0.B
      txState := 2.U
    }
    is(2.U) { // 发送数据位
      // 使用Chisel的BitPat简化状态机
    }
  }
}
```

### 5. DMA控制器

**Chisel方式：**
```scala
case class DmaChannelConfig(
    channel: Int,
    enable: Boolean,
    priority: Int
)

class DmaChannel(chConfig: DmaChannelConfig) extends Module {
  val io = IO(new Bundle {
    val bus = new BusInterface()
    val config = Input(new Bundle {
      val srcAddr = UInt(32.W)
      val dstAddr = UInt(32.W)
      val length = UInt(16.W)
      val start = Bool()
    })
    val done = Output(Bool())
  })
  
  // DMA状态机
  val state = RegInit(0.U(3.W))
  val currentAddr = Reg(UInt(32.W))
  val remainingLen = Reg(UInt(16.W))
  
  // 使用Chisel的条件连接
  when(io.config.start && remainingLen =/= 0.U) {
    // 启动传输
    state := 1.U
  }
  
  // 地址自增逻辑
  when(state =/= 0.U) {
    currentAddr := currentAddr + 4.U
    remainingLen := remainingLen - 1.U
  }
}
```

---

## 关键设计点

### 1. CPU核心与SOC解耦
- CPU核心可以独立编译和测试
- SOC配置包含CPU配置，通过CpuConfig传递
- 支持多核扩展（通过添加更多CPU实例）

### 2. 外设可配置
- 外设数量可配置（通过SocConfig开关）
- 地址映射可修改（通过AddrMap object）
- 时钟域可配置（不同外设可使用不同时钟）

### 3. 总线协议抽象
- 使用Bundle定义标准总线接口
- 支持AXI/AHB/APB或自定义总线
- 总线桥接器自动转换协议

### 4. 测试分层
- **单元测试**：每个组件独立测试
- **集成测试**：外设与总线组合测试
- **系统测试**：完整SOC运行固件程序
- **Co-simulation**：与C/C++模型联合仿真

---

## 下一步

请确认：
1. SOC目录结构是否符合预期？
2. 需要添加哪些具体外设？（UART/SPI/GPIO/I2C等）
3. 总线协议选择？（AXI/AHB/APB或自定义简单总线）
4. 是否需要添加多核支持？
5. 确认后我开始构建实际代码

