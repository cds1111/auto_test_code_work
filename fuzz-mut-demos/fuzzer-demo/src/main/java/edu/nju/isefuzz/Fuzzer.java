package edu.nju.isefuzz;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 核心 Fuzzer 类：组装所有组件，执行模糊测试主循环
 */
public class Fuzzer {

    // --- 组件引用 ---
    private TestRunner runner;
    private CoverageMonitor monitor;
    private SeedQueue queue;
    private Mutator mutator;
    private PowerSchedule powerSchedule;
    private Evaluator evaluator;

    // --- 配置 ---
    private String targetPath;
    private List<String> targetArgs;
    private String outputDir;

    public Fuzzer(String targetPath, List<String> targetArgs, String seedDir, String outputDir) {
        this.targetPath = targetPath;
        this.targetArgs = targetArgs;
        this.outputDir = outputDir;

        // 1. 初始化组件
        this.monitor = new CoverageMonitor();
        this.runner = new TestRunner();
        this.queue = new SeedQueue();
        this.mutator = new Mutator();
        this.powerSchedule = new PowerSchedule();
        this.evaluator = new Evaluator(outputDir);

        // 2. 组装组件
        this.monitor.setup();      // 申请共享内存
        this.runner.setMonitor(monitor); // 把监控器装进执行器

        // 3. 加载初始种子
        System.out.println("[Init] Loading seeds from: " + seedDir);
        this.queue.loadSeeds(seedDir);

        if (queue.size() == 0) {
            System.err.println("[Error] No seeds found! Please check path: " + seedDir);
            System.exit(1);
        }
    }

    /**
     * 开始模糊测试
     * @param timeLimitSeconds 运行总时长（秒）
     */
    /**
     * 开始模糊测试 (优化版：分批处理防 OOM)
     * @param timeLimitSeconds 运行总时长（秒）
     */
    public void start(int timeLimitSeconds) {
        System.out.println("[*] Fuzzing started! Target: " + targetPath);
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (timeLimitSeconds * 1000L);

        try {
            // --- 主循环 (Fuzz Loop) ---
            while (System.currentTimeMillis() < endTime) {
                // 1. [Schedule] 选一个种子
                Seed seed = queue.getNext();
                if (seed == null) break;

                // 2. [Power] 决定变异多少次 (Energy)
                int energy = powerSchedule.assignEnergy(seed);

                // [优化] 分批次变异，防止内存溢出 (OOM)
                // 每次只生成 50 个变异体，跑完就释放内存
                int batchSize = 50;

                for (int i = 0; i < energy; i += batchSize) {
                    // 计算这一批次跑多少个
                    int currentBatchCount = Math.min(batchSize, energy - i);

                    // 3. [Mutate] 生成这一小批变异体
                    List<byte[]> mutants = mutator.generateHavoc(seed.getData(), currentBatchCount);

                    for (byte[] inputData : mutants) {
                        // 4. [Execute] 运行目标
                        TestRunner.ExecutionResult result = runner.run(targetPath, targetArgs, inputData, 1000);

                        // 5. [Monitor] 检查覆盖率
                        boolean isNewPath = monitor.checkAndUpdate();

                        // 6. [Save] 保存结果
                        if (result.status == TestRunner.RunStatus.CRASH) {
                            System.out.println("[!] CRASH FOUND! Exit code: " + result.exitCode);
                            runner.saveInput(inputData, outputDir + "/crashes", "crash_" + result.exitCode);
                        } else if (isNewPath) {
                            System.out.println("[+] New path found! Total coverage: " + monitor.getTotalCoverage());
                            Seed newSeed = new Seed("mut_" + runner.totalExecutions, inputData);
                            newSeed.setExecTime(result.executionTime);
                            newSeed.setBitmapSize(monitor.countCoverage());
                            queue.addSeed(newSeed);
                            runner.saveInput(inputData, outputDir + "/queue", "id_" + runner.totalExecutions);

                            powerSchedule.updateStats(newSeed);
                        }

                        // 7. [Evaluate] 记录数据
                        if (runner.totalExecutions % 50 == 0 || isNewPath) {
                            evaluator.log(runner.totalExecutions, monitor.getTotalCoverage());
                        }

                        // 检查总时间
                        if (System.currentTimeMillis() > endTime) break;
                    }

                    // [关键] 主动断开引用，帮助 GC 回收这批内存
                    mutants = null;

                    if (System.currentTimeMillis() > endTime) break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("[*] Fuzzing finished.");
            System.out.println("Total Executions: " + runner.totalExecutions);
            if (monitor != null) monitor.cleanup();
            if (evaluator != null) evaluator.close();
        }
    }
    //    public void start(int timeLimitSeconds) {
//        System.out.println("[*] Fuzzing started! Target: " + targetPath);
//        long startTime = System.currentTimeMillis();
//        long endTime = startTime + (timeLimitSeconds * 1000L);
//
//        try {
//            // --- 主循环 (Fuzz Loop) ---
//            while (System.currentTimeMillis() < endTime) {
//                // 1. [Schedule] 选一个种子
//                Seed seed = queue.getNext();
//                if (seed == null) break; // 应该不会发生
//
//                // 2. [Power] 决定变异多少次 (Energy)
//                int energy = powerSchedule.assignEnergy(seed);
//
//                // 3. [Mutate & Fuzz]
//                // 这里我们使用 Havoc 策略，随机变异多次
//                // (注：为了效率，真实的AFL会先做Deterministic，这里简化为直接Havoc)
//                List<byte[]> mutants = mutator.generateHavoc(seed.getData(), energy);
//
//                for (byte[] inputData : mutants) {
//                    // 4. [Execute] 运行目标
//                    TestRunner.ExecutionResult result = runner.run(targetPath, targetArgs, inputData, 1000);
//
//                    // 5. [Monitor] 检查覆盖率
//                    boolean isNewPath = monitor.checkAndUpdate();
//
//                    // 6. [Update Stats] 更新全局统计
//                    // 实际AFL在这里会做更多统计，我们简化
//
//                    // 7. [Save] 保存结果
//                    if (result.status == TestRunner.RunStatus.CRASH) {
//                        System.out.println("[!] CRASH FOUND! Exit code: " + result.exitCode);
//                        runner.saveInput(inputData, outputDir + "/crashes", "crash_" + result.exitCode);
//                    } else if (isNewPath) {
//                        // 发现新路径，作为新种子加入队列！
//                        System.out.println("[+] New path found! Total coverage: " + monitor.getTotalCoverage());
//                        Seed newSeed = new Seed("mut_" + runner.totalExecutions, inputData);
//                        newSeed.setExecTime(result.executionTime);
//                        newSeed.setBitmapSize(monitor.countCoverage()); // 记录这次覆盖了多少
//                        queue.addSeed(newSeed);
//                        runner.saveInput(inputData, outputDir + "/queue", "id_" + runner.totalExecutions);
//
//                        // 更新能量调度器的统计信息
//                        powerSchedule.updateStats(newSeed);
//                    }
//
//                    // 8. [Evaluate] 记录数据给画图用 (每10次或者发现新路径时记一下，减少IO)
//                    if (runner.totalExecutions % 50 == 0 || isNewPath) {
//                        evaluator.log(runner.totalExecutions, monitor.getTotalCoverage());
//                    }
//
//                    // 检查时间是否到了
//                    if (System.currentTimeMillis() > endTime) break;
//                }
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            System.out.println("[*] Fuzzing finished.");
//            System.out.println("Total Executions: " + runner.totalExecutions);
//            monitor.cleanup();
//            evaluator.close();
//        }
//    }

    // ==========================================================
    // 配置入口：在这里修改你要跑哪个程序 (T01 - T10)
    // ==========================================================
    // ==========================================================
    // [修改版] 配置入口：支持通过命令行参数 args[0] 选择目标
    // ==========================================================
    // ==========================================================
    // [最终通用版] 支持参数: <TargetID> [TimeSeconds]
    // ==========================================================
    // ==========================================================
    // [修正版] 适配你的 D:\myFuzzProject\targets 目录结构
    // ==========================================================
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java ... Fuzzer <TargetID> [TimeSeconds]");
            System.exit(1);
        }

        String targetId = args[0].toUpperCase();

        // 默认跑 24 小时
        int runTimeSeconds = 86400;
        if (args.length > 1) {
            try {
                runTimeSeconds = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("[!] Invalid time format, using default 24h.");
            }
        }

        System.out.println(">>> Configuring Fuzzer for: " + targetId);
        System.out.println(">>> Time Limit: " + runTimeSeconds + " seconds");

        // 1. 基础路径配置
        // 容器内的 seeds 目录 (由脚本挂载 D:\myFuzzProject\seeds_collection\Txx -> 此处)
        String seedDir = "/app/fuzz-targets/init-seeds";
        // 容器内的 output 目录
        String outDir = "output/" + targetId;

        String targetBin = "";
        List<String> targetArgs = new ArrayList<>();

        // 2. 目标配置表 (路径已根据你的文件树修正为 /app/targets/xxx)
        switch (targetId) {
            case "T01": // cxxfilt
                targetBin = "/app/targets/cxxfilt";
                // cxxfilt 默认读 stdin，这里不传参数
                break;
            case "T02": // readelf
                targetBin = "/app/targets/readelf";
                targetArgs = Arrays.asList("-a", "@@");
                break;
            case "T03": // nm-new
                targetBin = "/app/targets/nm-new";
                targetArgs = Arrays.asList("-C", "@@");
                break;
            case "T04": // objdump
                targetBin = "/app/targets/objdump";
                targetArgs = Arrays.asList("-d", "@@");
                break;
            case "T05": // djpeg
                targetBin = "/app/targets/djpeg";
                targetArgs = Arrays.asList("@@");
                break;
//            case "T06": // readpng
//                targetBin = "/app/targets/readpng";
//                targetArgs = Arrays.asList("@@");
//                break;
//            case "T06": // readpng
//                targetBin = "/app/targets/readpng";
//                // [修改] 增加第二个参数 /dev/null 作为输出路径
//                // 这样命令就变成了: readpng /tmp/input.png /dev/null
//                targetArgs = Arrays.asList("@@", "/dev/null");
//                break;
            case "T06": // readpng
                targetBin = "/app/targets/readpng";
                // [修改] 既然是 Stdin 模式，就不传任何参数了
                // 原来的 Arrays.asList("@@") 或 Arrays.asList("@@", "/dev/null") 统统去掉
                targetArgs = new ArrayList<>();
                break;
            case "T07": // xmllint
                targetBin = "/app/targets/xmllint";
                targetArgs = Arrays.asList("--noout", "@@");
                break;
            case "T08": // mjs
                targetBin = "/app/targets/mjs";
                targetArgs = Arrays.asList("-f", "@@");
                break;
            case "T09": // lua
                targetBin = "/app/targets/lua";
                targetArgs = Arrays.asList("@@");
                break;
            case "T10": // tcpdump
                targetBin = "/app/targets/tcpdump";
                targetArgs = Arrays.asList("-nr", "@@");
                break;
            default:
                System.err.println("Unknown Target ID: " + targetId);
                System.exit(1);
        }

        // 3. 检查文件是否存在
        File binFile = new File(targetBin);
        if (!binFile.exists()) {
            System.err.println("[!] Target binary not found at: " + targetBin);
            System.err.println("[Tip] Check if D:\\myFuzzProject\\targets is mounted to /app/targets");
        }

        // 4. 启动
        Fuzzer fuzzer = new Fuzzer(targetBin, targetArgs, seedDir, outDir);
        fuzzer.start(runTimeSeconds);

        // 5. 画图
        try {
            System.out.println("[*] Generating plot for " + targetId + "...");
            ProcessBuilder pb = new ProcessBuilder("python3", "plot.py", outDir + "/fuzzer_stats.csv", outDir + "/trend.png");
            pb.inheritIO();
            pb.start().waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
//    public static void main(String[] args) {
//        if (args.length == 0) {
//            System.err.println("Usage: java edu.nju.isefuzz.Fuzzer <TargetID> (e.g., T01, T02...)");
//            System.exit(1);
//        }
//
//        String targetId = args[0].toUpperCase();
//        System.out.println(">>> Configuring Fuzzer for: " + targetId);
//
//        // 1. 基础路径配置
//        String seedDir = "/app/fuzz-targets/init-seeds"; // 确保这里有种子
//        // 输出目录根据 ID 区分，避免 10 个程序写到同一个文件里打架
//        String outDir = "output/" + targetId;
//
//        String targetBin = "";
//        List<String> targetArgs = new ArrayList<>();
//
//        // 2. 根据 ID 切换配置 (对应 PDF 表格)
//        switch (targetId) {
//            case "T01": // cxxfilt
//                targetBin = "/app/targets/binutils-2.28/binutils/cxxfilt";
//                // cxxfilt 既可以读 stdin 也可以读参数，为了统一用 @@
//                // 如果不行，可以去掉 @@ 改为 stdin 模式 (TestRunner需要支持)
//                // 这里假设 TestRunner 逻辑是：如果有 @@ 就写文件，没 @@ 就不管
//                // 建议：直接给一个混淆符号作为种子，不要用 @@，或者保持空列表(需TestRunner支持stdin)
//                // 为了保险，我们用 -t (type) 参数凑数，或者留空
//                break;
//            case "T02": // readelf (推荐演示)
//                targetBin = "/app/targets/binutils-2.28/binutils/readelf";
//                targetArgs = Arrays.asList("-a", "@@");
//                break;
//            case "T03": // nm-new
//                targetBin = "/app/targets/binutils-2.28/binutils/nm-new";
//                targetArgs = Arrays.asList("-C", "@@");
//                break;
//            case "T04": // objdump
//                targetBin = "/app/targets/binutils-2.28/binutils/objdump";
//                targetArgs = Arrays.asList("-d", "@@");
//                break;
//            case "T05": // djpeg
//                targetBin = "/app/targets/libjpeg-turbo-3.0.4/djpeg";
//                targetArgs = Arrays.asList("@@");
//                break;
//            case "T06": // readpng
//                // 注意：readpng 路径可能需要你进 docker find 一下，通常在 contrib 目录下
//                targetBin = "/app/targets/libpng-1.6.29/contrib/pngminus/png2pnm";
//                targetArgs = Arrays.asList("@@");
//                break;
//            case "T07": // xmllint
//                targetBin = "/app/targets/libxml2-2.13.4/xmllint";
//                targetArgs = Arrays.asList("--noout", "@@");
//                break;
//            case "T08": // mjs
//                targetBin = "/app/targets/mjs-2.20.0/mjs";
//                targetArgs = Arrays.asList("-f", "@@");
//                break;
//            case "T09": // lua
//                targetBin = "/app/targets/lua-5.4.7/src/lua";
//                targetArgs = Arrays.asList("@@"); // 或者 -e "dofile('@@')"
//                break;
//            case "T10": // tcpdump
//                targetBin = "/app/targets/tcpdump-tcpdump-4.99.5/tcpdump";
//                targetArgs = Arrays.asList("-nr", "@@");
//                break;
//            default:
//                System.err.println("Unknown Target ID: " + targetId);
//                System.exit(1);
//        }
//
//        // 3. 检查并启动
//        File binFile = new File(targetBin);
//        if (!binFile.exists()) {
//            System.err.println("[!] Target binary not found: " + targetBin);
//            // 仅仅为了不报错退出，你可以注释掉下面这行，或者手动处理
//            System.exit(1);
//        }
//
//        // 运行 24 小时 (24 * 3600 秒)
//        int runTimeSeconds = 24 * 60 * 60;
//
//        // 创建 Fuzzer 并开始
//        Fuzzer fuzzer = new Fuzzer(targetBin, targetArgs, seedDir, outDir);
//        fuzzer.start(runTimeSeconds);
//
//        // 结束时画图
//        try {
//            System.out.println("[*] Generating plot for " + targetId + "...");
//            ProcessBuilder pb = new ProcessBuilder("python3", "plot.py", outDir + "/fuzzer_stats.csv", outDir + "/trend.png");
//            pb.inheritIO();
//            pb.start().waitFor();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
////    public static void main(String[] args) {
////        // --- 1. 基础配置 ---
////        String seedDir = "/app/fuzz-targets/init-seeds"; // 你的种子目录
////        String outDir = "output"; // 结果输出目录
////
////        // --- 2. 选择目标 (Target Configuration) ---
////        // 你可以通过修改这里的变量来切换 T01-T10
////
////        // ==> 示例：T02 readelf
////        String targetBin = "/app/targets/binutils-2.28/binutils/readelf";
////        // readelf 需要参数 "-a" 和输入文件 "@@"
////        List<String> targetArgs = new ArrayList<>(Arrays.asList("-a", "@@"));
////
////        /* // ==> 备用：T01 cxxfilt
////        String targetBin = "/app/targets/binutils-2.28/binutils/cxxfilt";
////        List<String> targetArgs = new ArrayList<>(); // 无参数，或者也可以接受文件名
////        */
////
////        /*
////        // ==> 备用：T05 djpeg
////        String targetBin = "/app/targets/libjpeg-turbo-3.0.4/djpeg";
////        List<String> targetArgs = new ArrayList<>(Arrays.asList("@@"));
////        */
////
////        // 检查文件是否存在
////        if (!new File(targetBin).exists()) {
////            System.err.println("Target not found: " + targetBin);
////            // 自动回退到 cxxfilt 做测试
////            targetBin = "/app/targets/cxxfilt";
////            targetArgs = new ArrayList<>();
////            System.out.println("Switching to fallback: " + targetBin);
////        }
////
////        // --- 3. 启动引擎 ---
////        // 运行 60 秒用于演示 (你可以改成 300 或 3600)
////        int runTimeSeconds = 60;
////
////        Fuzzer fuzzer = new Fuzzer(targetBin, targetArgs, seedDir, outDir);
////        fuzzer.start(runTimeSeconds);
////
////        // --- 4. 自动画图 ---
////        try {
////            System.out.println("[*] Generating plot...");
////            // 调用我们之前写好的 Python 脚本
////            ProcessBuilder pb = new ProcessBuilder("python3", "plot.py", outDir + "/fuzzer_stats.csv", outDir + "/trend.png");
////            pb.inheritIO();
////            pb.start().waitFor();
////            System.out.println("[SUCCESS] Plot generated at " + outDir + "/trend.png");
////        } catch (Exception e) {
////            System.err.println("[!] Failed to run plot script.");
////        }
////    }
}