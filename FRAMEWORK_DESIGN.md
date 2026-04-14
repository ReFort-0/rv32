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

## 关键设计点

### 1. 流水线寄存器可配置
- 根据CpuConfig.mode动态生成对应数量的流水线寄存器
- 单周期时无流水线寄存器
- 五级流水线时生成全部4对流水线寄存器

### 2. 冒险处理可配置
- enableForwarding控制是否包含前递单元
- enableHazardDetection控制是否包含冒险检测
- enableBranchPrediction控制是否包含分支预测

