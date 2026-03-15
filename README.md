# 覆盖率引导的变异式模糊测试工具 README

## 一、项目概述
### 1.1 项目背景
本项目是南京大学智能软件学院2025年自动化测试代码大作业，基于经典模糊测试工具AFL++简化实现，核心目标是开发一款**覆盖率引导的变异式模糊测试工具**，支持对10个不同类型的模糊目标（T01-T10）进行自动化测试，通过覆盖率反馈优化测试效率，最终生成可视化的覆盖率趋势图表。

### 1.2 核心功能
- 六大核心组件：测试执行、执行结果监控、变异、种子排序、能量调度、评估
- 支持10个目标程序的并发测试（多容器部署）
- 基于共享内存的覆盖率实时监控，精准捕捉新路径
- 多样化变异策略（比特翻转、算术加减、特殊值替换等）
- 智能种子调度（按长度排序、能量分配）
- 多维度数据可视化（单目标趋势图、多目标汇总图）

### 1.3 技术栈
- 开发语言：Java（核心组件）、Python（可视化）、PowerShell（自动化脚本）
- 运行环境：Docker（Ubuntu 22.04）
- 依赖工具：Maven（Java项目构建）、JNA（共享内存操作）、Matplotlib（数据可视化）、Clang/LLVM（目标程序编译依赖）

## 二、项目架构
### 2.1 目录结构
```
D:\myFuzzProject/
├── fuzz-mut-demos/          # 核心代码目录
│   ├── fuzzer-demo/
│   │   ├── src/main/java/edu/nju/isefuzz/  # 组件实现目录
│   │   │   ├── TestRunner.java           # 测试执行组件
│   │   │   ├── CoverageMonitor.java     # 执行结果监控组件
│   │   │   ├── Mutator.java             # 变异组件
│   │   │   ├── MutatorTest.java          # 变异组件测试
│   │   │   ├── Seed.java                # 种子实体类
│   │   │   ├── SeedQueue.java           # 种子排序组件
│   │   │   ├── SeedQueueTest.java       # 种子排序测试
│   │   │   ├── PowerSchedule.java       # 能量调度组件
│   │   │   ├── PowerScheduleTest.java   # 能量调度测试
│   │   │   ├── Evaluator.java          # 评估组件（Java端）
│   │   │   └── Fuzzer.java             # 总控程序
│   │   ├── plot.py                     # 单目标可视化脚本
│   │   ├── plot_all.py                 # 多目标汇总可视化脚本
│   │   ├── pom.xml                     # Maven依赖配置
│   │   └── output/                     # 测试结果输出目录
│   └── fuzz-targets/
│       └── init-seeds/                  # 初始种子目录
├── seeds_collection/                   # 分类种子目录（T01-T10）
├── targets/                             # 模糊目标程序目录
├── run_all.ps1                         # 多容器批量运行脚本
├── Dockerfile                           # 环境构建配置
└── README.md                            # 项目文档
```

### 2.2 组件交互流程
```
种子加载（SeedQueue） → 测试执行（TestRunner） → 覆盖率监控（CoverageMonitor）
→ 结果评估（Evaluator） → 变异生成（Mutator） → 新种子入队（SeedQueue）
```
- 种子排序组件按策略选择待测试种子
- 测试执行组件创建子进程运行目标程序，传递种子数据
- 监控组件通过共享内存捕获覆盖率数据，判断是否发现新路径
- 评估组件记录测试数据（时间、执行次数、覆盖率）
- 变异组件基于种子生成新变异体，新路径种子重新入队
- 能量调度组件根据种子表现（速度、覆盖率）分配变异次数

### 2.3 核心组件说明
| 组件名称               | 核心职责                                                                 | 关键特性                                                                 |
|------------------------|--------------------------------------------------------------------------|--------------------------------------------------------------------------|
| 测试执行组件（TestRunner） | 创建子进程、运行目标程序、传递输入数据、记录执行状态                       | 支持文件输入/标准输入双模式，自动清理临时文件                     |
| 执行结果监控组件（CoverageMonitor） | 共享内存管理、覆盖率统计、新路径判断                                     | 基于全局 bitmap 记录历史覆盖率，支持执行速度计算                         |
| 变异组件（Mutator）     | 实现多种变异算子，生成新测试用例                                         | 支持比特翻转、算术加减、特殊值替换、Havoc随机变异、种子拼接，PNG魔数保护 |
| 种子排序组件（SeedQueue） | 种子队列管理、排序策略实现                                               | 支持FIFO、Shortest First最短优先两种策略                     |
| 能量调度组件（PowerSchedule） | 基于种子表现分配变异能量、变异次数                                      | 结合执行速度和覆盖率动态调整，优质种子获得更多变异机会                   |
| 评估组件（Evaluator+plot脚本） | 数据记录、CSV生成、可视化图表绘制                                         | 支持单目标趋势图、10目标汇总图，图例标注目标名称                       |
| 总控程序（Fuzzer）     | 组件串联、测试流程控制、命令行参数解析                                     | 支持目标ID指定、运行时长配置，自动加载对应配置                           |

## 三、环境搭建
### 3.1 前置依赖
- 安装Docker Desktop并启动，启用WSL2
- 使用Git进行代码拉取
- 安装Java 8/11、Maven 本地代码编译

### 3.2 环境构建（Docker）
#### 方法1：通过Dockerfile构建（推荐）
1. 拉取项目代码
   ```powershell
   git clone <项目GitHub仓库地址>
   cd myFuzzProject
   ```
2. 构建Docker镜像
   ```powershell
   docker build -t my_fuzzer_image .
   ```
   镜像包含完整依赖（Java、Maven、Python、Matplotlib、目标程序依赖库）

#### 方法2：导入现成镜像
1. 获取镜像文件`fuzzer_pkg.tar`
2. 加载镜像
   ```powershell
   docker load -i fuzzer_pkg.tar
   ```

### 3.3 目录挂载说明
项目通过Docker挂载实现Windows与容器的文件同步，核心挂载关系：
- 本地目录：`D:\myFuzzProject`
- 容器目录：`/app`
- 自动同步：代码修改、测试结果、日志文件实时互通

## 四、使用指南
### 4.1 核心配置修改
1. 编辑`run_all.ps1`脚本，设置关键参数：
   ```powershell
   $projectPath = "D:\myFuzzProject"  # 本地项目路径（需修改为实际路径）
   $imageName = "my_fuzzer_image"   # Docker镜像名称
   $testDuration = 86400           # 测试时长（秒），24小时=86400秒
   ```

### 4.2 启动测试
#### 批量运行10个目标（推荐）
```powershell
# PowerShell环境直接运行
.\run_all.ps1

# CMD环境运行
powershell -ExecutionPolicy Bypass -File run_all.ps1
```
- 自动启动10个Docker容器，分别对应T01-T10
- 每个容器独立生成日志文件（`fuzz_T01.log`-`fuzz_T10.log`）
- 测试结果按目标分类存储在`output/T01`-`output/T10`目录

#### 单目标测试（调试用）
```powershell
# 格式：java -cp <jar包路径> edu.nju.isefuzz.Fuzzer <目标ID> <运行时长>
docker run --rm -v "D:\myFuzzProject:/app" my_fuzzer_image bash -c "cd /app/fuzz-mut-demos/fuzzer-demo && mvn exec:java -Dexec.mainClass='edu.nju.isefuzz.Fuzzer' -Dexec.args='T02 600'"
```
- 示例：运行T02（readelf），测试时长600秒（10分钟）

### 4.3 结果查看
1. 日志查看：`fuzz-mut-demos/fuzzer-demo`目录下的`fuzz_Txx.log`，可实时查看测试进度
2. 数据文件：`output/Txx/fuzzer_stats.csv`，包含时间、执行次数、覆盖率数据
3. 可视化图表：
   - 单目标图：`output/Txx/coverage_trend.png`
   - 多目标汇总图：运行以下命令生成
     ```powershell
     # Windows本地运行（需安装Python和Matplotlib）
     cd fuzz-mut-demos/fuzzer-demo
     python plot_all.py
     ```
     汇总图保存路径：`output/combined_trend.png`

## 四、目标程序说明
### 4.1 支持的10个模糊目标
| 目标ID | 程序名称 | 类型         | 输入方式       | 种子要求                     |
|--------|----------|--------------|----------------|------------------------------|
| T01    | cxxfilt  | C++符号还原工具 | 标准输入（Stdin） | 混淆C++符号（如`_Z1fv`）      |
| T02    | readelf  | ELF解析工具   | 文件输入       | ELF格式可执行文件             |
| T03    | nm-new   | 符号查看工具   | 文件输入       | ELF格式文件                   |
| T04    | objdump  | 反汇编工具     | 文件输入       | ELF格式文件                   |
| T05    | djpeg    | JPEG解码工具   | 文件输入       | JPEG格式图片文件               |
| T06    | readpng  | PNG解码工具   | 标准输入（Stdin） | PNG格式图片文件（带魔数保护）  |
| T07    | xmllint  | XML验证工具   | 文件输入       | XML格式文件                   |
| T08    | mjs      | JavaScript引擎 | 文件输入       | JS脚本文件                    |
| T09    | lua      | Lua解释器     | 文件输入       | Lua脚本文件                   |
| T10    | tcpdump  | 网络抓包工具   | 文件输入       | PCAP格式抓包文件               |

### 4.2 种子准备
项目已在`seeds_collection`目录按目标分类准备初始种子，如需补充：
- T01：新增混淆C++符号文本文件
- T02-T04：新增ELF格式小文件（可通过`gcc`编译简单C程序生成）
- T05：新增JPEG图片（建议<20KB）
- T06：新增PNG图片（建议<20KB）
- T07：新增XML文件（需符合XML语法）
- T08：新增JS脚本（支持简单函数、计算逻辑）
- T09：新增Lua脚本（支持循环、表操作）
- T10：新增PCAP抓包文件

## 五、常见问题排查
部分问题的经验预测以及建议
### 5.1 容器秒退，报错"No such file or directory"
- 原因：`run_all.ps1`中`$projectPath`配置错误，导致目录挂载失效
- 解决：修改`$projectPath`为本地实际项目路径（实际最好改为`$projectPath = $PSScriptRoot`自动获取路径）

### 5.2 覆盖率无变化（平直线）
- 原因1：目标程序缺少依赖库
  解决：进入容器安装依赖
  ```bash
  docker exec -it <容器ID> bash
  apt-get install -y zlib1g-dev libpcap-dev libjpeg-dev libpng-dev libxml2-dev
  ```
- 原因2：种子格式不匹配（如用文本文件测试ELF解析工具）
  解决：替换为目标程序支持的种子格式
- 原因3：参数配置错误
  解决：核对`Fuzzer.java`中目标程序的参数配置，确保与程序要求一致

### 5.3 Python绘图报错（Matplotlib）
- 原因：缺少Agg后端（Docker无GUI环境）
- 解决：确保`plot.py`和`plot_all.py`开头已添加
  ```python
  import matplotlib
  matplotlib.use('Agg')
  ```

### 5.4 内存溢出（OOM）
- 原因：批量变异生成过多变异体，内存占用超限
- 解决：确认`Fuzzer.java`中`batchSize=50`的分批变异机制已启用，避免一次性生成大量变异体

### 5.5 中文乱码
- 原因：Docker容器默认编码不支持中文
- 解决：代码日志已统一使用英文，无需额外配置

## 六、注意事项
1. 测试时长建议：单目标调试用10-30分钟，正式测试用4-24小时
2. 磁盘空间监控：Docker容器运行会生成`.vhdx`文件，需预留足够空间（建议≥20GB）
3. 容器管理：测试结束后可通过`docker rm -f $(docker ps -a -q)`清理停止的容器
4. 代码修改：本地修改代码后，需在容器内重新编译才能生效
   ```bash
   docker run --rm -v "D:\myFuzzProject:/app" my_fuzzer_image bash -c "cd /app/fuzz-mut-demos/fuzzer-demo && mvn clean package -DskipTests"
   ```


