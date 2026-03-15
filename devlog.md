# 覆盖率引导的变异式模糊测试工具开发日志
## 一、项目基础信息
1. 项目名称：覆盖率引导的变异式模糊测试工具开发
2. 项目背景：南京大学软件学院智能软件与工程学院2025年自动化测试代码大作业
3. 核心目标：基于AFL++简化实现模糊测试工具，覆盖六大核心组件，完成10个目标24h测试与可视化分析
4. 技术栈：Java、Python、Docker（Ubuntu 22.04）、Maven、AFL++相关原理
5. 项目周期：代码2天、跑测试2天、开发日志和项目文档撰写2天
6. 任务分配：白佳昊-代码编写、王天宇-代码运行和结果分析、罗鸿-开发日志编写、曹家雄-项目文档撰写
## 二、开发准备阶段
### （一）需求分析与参考资料梳理
- 深度解读课程要求PDF，明确六大组件（测试执行、执行结果监控、变异、种子排序、能量调度、评估）的实现标准，确认10个测试目标（T01-T10）的运行要求与交付清单。
- 从Github仓库克隆往年Demo项目，逐目录分析文件结构，重点拆解核心组件的交互逻辑，标记与本年度任务无关的冗余文件，因不熟悉架构最初未敢贸然删除，选择在指定目录下重新实现组件。
- 定位AFL++关键源码参考位置，整理核心函数映射关系（测试执行组件参考`fuzz_run_target()`、能量调度组件参考`calculate_score()`等），形成开发参考手册。

### （二）环境搭建
环境搭建核心围绕 docker配置-代码获取 - 容器配置 - 依赖安装 - 编译运行 五步展开：首先安装docker平台并配置好与运行环境，通过 Git 克隆项目代码到本地，再利用 Dockerfile 构建统一运行环境，安装项目所需依赖库与工具，最后编译代码并通过脚本启动测试，全程需确保 Windows 与 Docker 的文件共享、依赖完整性及路径配置正确。
#### 核心问题1：Docker环境与Windows文件共享不通
- 问题描述：启动Docker容器后，本地编写的代码无法同步到容器内，不清楚如何实现Windows与Docker的文件传输。
- 解决方法：通过Docker挂载命令建立"传送窗"，在Windows终端执行`docker run -it -v D:\myFuzzProject:/app --name my_fuzz_lab ubuntu:22.04 /bin/bash`，将本地项目目录直接映射到容器的`/app`目录，实现文件实时同步。

#### 核心问题2：容器内依赖库缺失导致工具无法安装
- 问题描述：安装clang、llvm等AFL++核心依赖时，出现"package not found"报错，且不清楚需要安装哪些具体依赖。
- 解决方法：按作业要求逐一安装依赖，执行系列命令：
  1. `apt-get update`更新软件源；
  2. `apt-get install -y build-essential libtool cmake python3`安装基础编译工具；
  3. `apt-get install -y clang llvm`安装核心依赖；
  4. `apt-get install -y file binutils git vim`安装辅助工具。
- 补充操作：针对10个目标程序的特殊依赖，后续追加安装`nasm zlib1g-dev libpcap-dev liblzma-dev libreadline-dev`，避免编译时缺失动态库。

#### 核心问题 3：Docker 容器内中文乱码
- 问题描述：运行测试程序时，中文提示语显示为问号，无法正常查看日志信息。
- 解决方法：采用修改代码提示语为英文的极简方案，避免编码适配复杂操作，同时保证日志可读性，符合程序员开发规范。

### （三）项目架构设计
- 规划本地与容器内统一的项目目录结构，以`D:\myFuzzProject`（本地）/`/app`（容器）为根目录，核心目录职责如下：
  - `fuzz-mut-demos`：存放核心代码与测试用例，其中`fuzzer-demo/src/main/java/edu/nju/isefuzz`为组件实现目录，后续新增的`TestRunner.java`、`CoverageMonitor.java`等核心组件文件均存放于此；同时该目录下的`fuzzer-demo`子目录需预留`plot.py`（评估组件Python端）、`plot_all.py`（多目标数据汇总绘图）的存放位置。
  - `seeds_collection`：按T01-T10分类存放初始种子文件，为各目标程序提供针对性输入数据。
  - `targets`：存放10个模糊目标程序（cxxfilt、readelf等），直接复用现有文件结构。
  - 根目录：预留`run_all.ps1`脚本位置，用于后续多容器批量运行；同时保留项目原有`Dockerfile`，用于统一构建运行环境。
- 新增文件规划：明确开发过程中需新增的核心文件及存放路径：
  - 组件实现文件：`TestRunner.java`、`CoverageMonitor.java`、`Mutator.java`、`Seed.java`、`SeedQueue.java`、`PowerSchedule.java`、`Evaluator.java`，均存放于`fuzzer-demo/src/main/java/edu/nju/isefuzz`目录；
  - 组件测试文件：`MutatorTest.java`、`SeedQueueTest.java`、`PowerScheduleTest.java`、`EvaluatorTest.java`，与对应组件文件同目录；
  - 总控程序：`Fuzzer.java`，存放于`fuzzer-demo/src/main/java/edu/nju/isefuzz`目录；
  - 绘图脚本：`plot_all.py`存放于`fuzzer-demo`目录，与`plot.py`同级；
  - 批量运行脚本：`run_all.ps1`存放于项目根目录。
- 梳理组件交互逻辑，绘制流程图：种子加载→测试执行→覆盖率监控→结果评估→变异/新种子入队，明确各组件的输入输出与依赖关系，为后续组件联动开发奠定基础。

### （四）模糊目标的编译与插装
- 任务目标：完成10个模糊目标程序的编译与AFL插装，生成支持覆盖率追踪的可执行文件。
- 操作过程：
  1. 依赖安装：在Ubuntu 22.04环境中，通过`apt`安装插装所需工具链：`libtool`、`build-essential`（make）、`cmake`、`python3`、`clang`、`llvm`、`file`、`binutils`（nm、objdump）；
  2. 工具链替换：将默认编译工具`gcc`/`clang`替换为AFL++的`afl-cc`，实现编译期插装；
  3. 分步编译：对每个模糊目标依次执行预编译、编译、汇编、链接步骤，生成带插装的ELF可执行文件；
  4. 效果验证：用`file`命令确认目标程序格式，运行测试用例验证覆盖率数据可被监控组件捕获。
- 输出结果：10个插装后的目标程序已存放至`targets`目录，支持后续模糊测试的覆盖率追踪。

## 三、核心开发阶段
### （一）组件开发
1. 测试执行组件（TestRunner.java）
   - 开发思路与参考依据：参考AFL++的`fuzz_run_target()`，实现子进程创建、目标程序调用、执行状态捕获功能。
   - 核心功能实现：支持通过临时文件或标准输入传递测试数据，记录执行时间、次数与退出状态，适配不同目标程序的参数格式。
   - 测试与调试记录：编写简单测试用例调用cxxfilt（T01），初始因未适配标准输入导致程序超时，后续通过`OutputStream`向子进程写入数据并关闭流，模拟EOF信号，解决超时问题。

2. 执行结果监控组件（CoverageMonitor.java）
   - 依赖引入与配置修改：修改pom.xml文件，引入JNA库，实现Linux系统调用功能，解决Java无法直接操作共享内存的问题。
   - 共享内存操作实现：封装shmget、shmat等系统调用，创建65536字节的共享内存区域，用于存储覆盖率bitmap。
   - 核心问题：初始未实现全局覆盖率历史记录，导致无法判断新路径。
   - 解决方法：新增`globalBits`数组记录历史覆盖率，实现`checkAndUpdate()`方法，通过对比当前覆盖率与历史数据，返回是否发现新路径。
   - 联动修改：调整TestRunner.java，添加监控组件调用逻辑，确保执行前后清空内存、同步覆盖率数据。

3. 变异组件（Mutator.java/MutatorTest.java）
   - 五大变异算子实现细节：依次实现比特位翻转、算术加减、特殊值替换、随机堆叠变异、种子拼接算子，其中随机堆叠变异算子支持1-15层随机变异组合。
   - 核心问题1：PNG目标程序（T06）因变异破坏文件头魔数，导致程序提前退出。
   - 解决方法：新增PNG魔数常量（`(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A`），在随机堆叠变异后自动修复文件头，确保程序能正常解析文件。
   - 核心问题2：变异生成大量PNG变异体导致内存溢出。
   - 解决方法：优化Havoc算子，限制单次生成变异体数量，后续在Fuzzer主循环中引入分批变异机制（batchSize=50），进一步避免内存占用过高。
   - 过程性测试：以"hello"为测试种子，通过MutatorTest.java验证各算子变异效果，输出前5条变异结果进行人工校验。

4. 种子排序组件（Seed.java/SeedQueue.java/SeedQueueTest.java）
   - 种子属性定义：在Seed.java中封装文件名、数据、长度、执行时间、覆盖率大小等属性，提供getter/setter方法。
   - 排序策略实现：SeedQueue.java支持FIFO与Shortest First两种排序策略，实现`loadSeeds()`（加载种子）、`addSeed()`（添加种子）、`getNext()`（循环获取下一个种子）功能。
   - 测试验证：通过SeedQueueTest.java模拟添加不同长度的种子，初始因排序后未重置索引导致种子获取顺序异常，修复后确保队列循环调度逻辑正常。

5. 能量调度组件（PowerSchedule.java/PowerScheduleTest.java）
   - Seed.java字段扩展：为Seed类新增bitmapSize字段，用于记录种子触发的路径数量。
   - 能量分配逻辑：基于种子执行速度与覆盖率表现分配变异能量，表现越优（执行快、覆盖率高）的种子获得越多变异次数，基础能量为100，上限20000次。
   - 测试验证：通过PowerScheduleTest.java创建模拟种子（普通种子、优质种子、劣质种子），验证能量分配比例符合预期，初始因未更新全局统计数据导致能量分配失衡，补充`updateStats()`方法后解决。

6. 评估组件（Evaluator.java/plot.py）
   - 数据汇总与CSV生成：Evaluator.java负责记录时间、执行次数、覆盖率数据，按格式输出到CSV文件，存储于output对应目标目录。
   - 核心问题：Docker中Python绘图因缺少后端引擎无法运行。
   - 解决方法：在plot.py中添加`import matplotlib; matplotlib.use('Agg')`配置，使用服务器专用后端引擎适配Docker环境，避免弹窗导致的报错。
   - 可视化效果调试：确保plot.py能读取CSV数据，生成趋势图并保存到output目录，支持单目标数据可视化。

### （二）总控程序与辅助脚本开发
1. Fuzzer.java优化
   - 实现初始化、种子加载、主循环三大核心步骤，串联所有组件，形成完整模糊测试流程。
   - 支持命令行参数：接收测试目标ID与运行时间参数，自动加载对应配置，适配多目标测试需求。
   - 核心问题：T06因参数格式错误导致程序秒退，queue文件夹仅保留1个初始种子。
   - 解决过程：
     1. 初始配置为`targetArgs = Arrays.asList("@@")`，程序提示参数不足；
     2. 按朋友建议添加输出参数`"/dev/null"`，配置改为`Arrays.asList("@@", "/dev/null")`，仍报错；
     3. 最终确认readpng需从标准输入读取数据，移除所有参数，修改TestRunner.java新增`targetPath.contains("readpng")`判定，强制开启Stdin模式，问题解决。

2. 多容器运行脚本（run_all.ps1）
   - 脚本功能：配置项目路径、Docker镜像名、测试时长等参数，循环启动10个容器，对应T01-T10，自动挂载种子目录与目标程序目录。
   - 核心问题：运行脚本时容器秒退，报错`cd: /app/fuzz-mut-demos/fuzzer-demo: No such file or directory`。
   - 解决方法：
     1. 原脚本`$projectPath`写死为本地路径，改为动态获取当前目录`$projectPath = $PSScriptRoot`；
     2. 确保本地目录结构与挂载路径一致，重新运行脚本后容器正常启动。
   - 结果输出：运行后在指定目录生成10个目标的日志文件，并为每个目标创建独立文件夹存储CSV数据。

3. 多目标数据汇总绘图（plot_all.py）
   - 在`fuzz-mut-demos/fuzzer-demo`目录下创建脚本，支持读取T01-T10的CSV数据，汇总生成一张可视化图表，图例标注为"T01_cxxfilt"格式，便于对比各目标覆盖率表现。
   - 核心优化：新增目标ID与名称映射字典，自动关联目标编号与程序名称，提升图表可读性。

## 四、测试与优化阶段
### （一）功能测试
- 单组件独立测试：运行各组件测试用例，修复能量调度逻辑偏差、种子排序失效等问题。
- 组件联动测试：启动Fuzzer.java测试T02（readelf）完整流程，初始因容器内目标程序缺少执行权限导致覆盖率为0，执行`chmod +x /app/targets/*`赋予权限后恢复正常。
- 10个测试目标适配测试：逐一运行T01-T10，记录各目标运行状态，排查参数格式不匹配、依赖缺失等适配问题，其中T01（cxxfilt）因输入方式适配耗时最长，最终通过Stdin模式完美解决。

### （二）问题排查与优化
1. 初始结果异常（平直线）解决
   - 问题描述：所有目标测试结果为平直线，覆盖率无变化。
   - 原因分析：
     1. 部分目标程序缺少依赖库（如readelf缺少zlib1g-dev）；
     2. 参数格式错误导致程序未执行核心逻辑；
     3. 共享内存未正确挂载，覆盖率数据未同步。
   - 解决方法：
     1. 批量安装缺失依赖`apt-get install -y zlib1g-dev libpcap-dev libjpeg-dev libpng-dev libxml2-dev`；
     2. 逐目标核对参数配置，确保与程序要求一致；
     3. 检查CoverageMonitor.java中共享内存初始化逻辑，修复shmget调用参数错误。

2. readpng的性能优化
   - 问题描述：readpng跑出来的结果太差，想通过找更多种子进行优化，但是文件太大，再增加种子后大批量变异导致内存溢出，程序崩溃，无法跑完。
   - 解决方法：在Fuzzer.java中引入分批变异机制（batchSize=50），每次生成少量变异体，跑完后主动释放引用辅助GC回收，避免内存累积。

3. readpng目标特殊处理
   - 问题描述：修复参数与文件头后，覆盖率仍卡在40左右，无法突破。
   - 原因分析：PNG文件的CRC校验码机制，变异后数据完整性被破坏，程序在校验阶段退出。
   - 决策：因CRC校验修复超出作业简化版Fuzzer的能力范畴，决定保持现状，在报告中说明该限制对测试结果的影响，体现Fuzzing技术的局限性。

### 4. Docker `.vhdx` 文件膨胀问题的深度优化（最后24h的重大成果）
- 问题描述：初始24小时测试中，仅运行1小时C盘Docker目录下的`docker_data.vhdx`文件就暴涨至几十GB，本地磁盘空间快速消耗。经分析，膨胀源于三方面：容器日志无限制累积、临时文件频繁读写、crash文件爆炸式生成。
- 优化方案（分步骤实施）：
  1. **限制容器日志大小**：
     - 进入Docker Desktop的`Settings → Docker Engine`，在JSON配置中添加日志驱动限制：
       ```json
       "log-driver": "json-file",
       "log-opts": {
         "max-size": "10m",
         "max-file": "3"
       }
       ```
     - 配置后每个容器日志最大仅保留10MB，最多存3份，超过自动清理旧日志。
  2. **RAM Disk挂载临时目录**：
     - 修改`run_all.ps1`脚本的`docker run`命令，添加`--tmpfs /tmp`参数，将容器内的`/tmp`目录挂载到内存（RAM）中：
       ```powershell
       docker run -d `
         -v "$projectPath:/app" `
         -v "$projectPath\seeds_collection\$tid:/app/fuzz-targets/init-seeds" `
         --tmpfs /tmp `  # 核心优化：/tmp目录读写直接走内存
         --name "fuzz_$tid" `
         $imageName `
         bash -c "cd /app/fuzz-mut-demos/fuzzer-demo && mvn exec:java -Dexec.mainClass='edu.nju.isefuzz.Fuzzer' -Dexec.args='$tid $testDuration'"
       ```
     - 此操作让`TestRunner`生成的临时文件（如`/tmp/fuzz_input_fixed.bin`）直接在内存中读写，完全不占用`.vhdx`磁盘空间。
  3. **限制crash文件数量**：
     - 修改`Fuzzer.java`的`saveInput`逻辑，添加crash文件数量限制（仅保留前100个）：
       ```java
       if (result.status == TestRunner.RunStatus.CRASH) {
          System.out.println("[!] CRASH FOUND! Exit code: " + result.exitCode);
         if (new File(outputDir + "/crashes").list().length < 100) {
           runner.saveInput(...);
         }// 只完整打印崩溃日志，限制crash文件数量，防止小文件元数据占用磁盘
       }
       ```
- 优化效果：
  实施后，24小时测试全程`docker_data.vhdx`文件仅增长几十MB，彻底解决磁盘膨胀问题；同时内存读写替代磁盘IO，测试执行效率提升约15%，所有目标程序均无中断完成24小时测试，数据完整性与稳定性大幅提升。这一优化是本次开发中最具成就感的突破之一，有效解决了本地环境的资源瓶颈。


### （三）24小时长时间运行测试
#### 运行设备运行前准备
#### 1. 准备工作
- 安装Git、Docker Desktop。
- 确保Java与Maven环境可用。

#### 2. 获取代码
```powershell
# 首次下载
git clone <GitHub仓库地址>
cd myFuzzProject

# 已下载过，更新代码
cd myFuzzProject
git pull
```

#### 3. 导入Docker镜像
```powershell
# 加载镜像包
docker load -i fuzzer_pkg.tar
```

#### 4. 编译Java代码
```powershell
# Docker内编译（无需本地安装Maven）
docker run --rm -v "%cd%:/app" my_fuzzer_image_fixed bash -c "cd /app/fuzz-mut-demos/fuzzer-demo && mvn clean package -DskipTests"
```

#### 5. 配置运行脚本
- 编辑`run_all.ps1`：
  - 修改`$projectPath`为本地项目实际路径（如`D:\Program\test\auto_test_code_work`）；
  - 修改`$testDuration`设置测试时长（秒），4小时=14400秒。

#### 6. 启动测试
```powershell
# PowerShell环境
.\run_all.ps1

# CMD环境
powershell -ExecutionPolicy Bypass -File run_all.ps1
```
- 运行环境监控：启动run_all.ps1脚本，批量运行10个目标，实时监控容器资源占用（CPU、内存、磁盘），发现C盘Docker目录下`.vhdx`文件膨胀问题。
- ~~异常情况处理：因无服务器使用经验，无法彻底解决磁盘膨胀，通过限制电脑性能、定期清理临时文件，勉强完成一次24小时测试，未出现程序崩溃。~~ 此问题后续已解决
- 测试结果完整性验证：24小时运行结束后，检查各目标的CSV数据与可视化图表是否完整，确认无数据丢失或程序崩溃情况。

## 五、项目交付阶段
### （一）代码整理与版本管理
- 代码注释完善：为各组件核心方法添加注释，说明功能、参数含义与返回值，提高代码可读性。
- Github仓库提交：将源代码、脚本、测试用例等文件提交至私有仓库，因开发时间集中在期末周，未采用Git分支管理，直接提交最终版本。
- 协作支持：为组员提供镜像打包方案，通过`docker save -o fuzzer_pkg.tar my_fuzzer_image_fixed`生成镜像文件，避免组员重复搭建环境。

### （二）Docker镜像制作与协作适配
- 镜像配置与优化：在容器内固化开发环境与项目依赖，清理冗余文件，减小镜像体积。
- 跨环境适配：针对组员的CMD环境（Win10），编写`setup.bat`脚本，替换PowerShell专属语法（如`${PWD}`改为`%cd%`），确保CMD用户能正常执行。
- 调用测试：将Docker镜像、配置脚本分享在组内，解决镜像导入、路径配置等问题，确保所有人能正常运行工具。
