package edu.nju.isefuzz;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 测试执行组件 (Test Execution Component)
 * 职责：创建子进程、运行目标、记录次数、保存结果文件
 */
public class TestRunner {

    private CoverageMonitor monitor;
    // [新增] 记录执行总次数
    public long totalExecutions = 0;

    public void setMonitor(CoverageMonitor monitor) {
        this.monitor = monitor;
    }

    public enum RunStatus {
        NORMAL, CRASH, HANG
    }

    public static class ExecutionResult {
        public RunStatus status;
        public int exitCode;
        public long executionTime;

        public ExecutionResult(RunStatus status, int exitCode, long executionTime) {
            this.status = status;
            this.exitCode = exitCode;
            this.executionTime = executionTime;
        }
    }

    /**
     * 运行目标程序
     */

    /**
     * 运行目标程序 (修改版：支持 Stdin)
     */
    public ExecutionResult run(String targetPath, List<String> args, byte[] inputData, int timeoutMs) {
        File inputFile = null;
        Process process = null;

        try {
            // [新增] 每次运行，计数器+1
            totalExecutions++;

            // 1. 创建临时种子文件 (为了兼容 readelf 等需要文件的程序)
            inputFile = File.createTempFile("fuzz_input_", ".bin");
            Files.write(inputFile.toPath(), inputData);
            String inputFilePath = inputFile.getAbsolutePath();

            // 2. 构建命令 & 决策输入方式
            List<String> command = new ArrayList<>();
            command.add(targetPath);

            boolean useStdin = false; // 默认不使用 Stdin

            // 针对 cxxfilt 的特殊补丁：强制开启 Stdin
            if (targetPath.contains("cxxfilt")|| targetPath.contains("readpng")) {
                useStdin = true;
            }

            boolean hasFileArg = false;
            for (String arg : args) {
                if (arg.equals("@@")) {
                    command.add(inputFilePath);
                    hasFileArg = true;
                } else {
                    command.add(arg);
                }
            }

            // 如果既不是 cxxfilt，参数里又没写 @@，通常意味着它可能也需要 Stdin (兜底策略)
            // 但为了安全起见，我们目前只对 cxxfilt 做强制开启

            ProcessBuilder pb = new ProcessBuilder(command);

            // 3. 注入监控 (Shared Memory)
            if (monitor != null) {
                monitor.clear();
                pb.environment().put("__AFL_SHM_ID", monitor.getShmIdString());
            }

            // 4. 处理 IO 流
            // 输出流：Fuzzing 通常不关心 stdout，丢弃以提升速度
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);

            // 输入流：如果不需要 Stdin，就设为 PIPE (默认) 或其他，但在 start 后不写入即可
            // 这里不需要特别设置 redirectInput，默认就是 PIPE，允许我们 getOutputStream

            long startTime = System.currentTimeMillis();
            process = pb.start();

            // 5. [关键修改] 如果需要 Stdin，在这里喂数据
            if (useStdin) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(inputData);
                    os.flush();
                    // 写完必须关闭流，相当于发送 EOF，否则程序会一直等待导致 Hang
                } catch (IOException e) {
                    // 忽略写入错误 (比如程序崩溃极快，导致管道破裂)
                }
            }

            // 6. 等待结果
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(RunStatus.HANG, -1, duration);
            }

            int exitCode = process.exitValue();

            // Linux 信号处理: > 128 通常意味着被信号杀死 (Crash)
            if (exitCode > 128) {
                return new ExecutionResult(RunStatus.CRASH, exitCode, duration);
            } else {
                return new ExecutionResult(RunStatus.NORMAL, exitCode, duration);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new ExecutionResult(RunStatus.NORMAL, -1, 0);
        } finally {
            // 清理临时文件
            if (inputFile != null && inputFile.exists()) {
                inputFile.delete();
            }
        }
    }
//    public ExecutionResult run(String targetPath, List<String> args, byte[] inputData, int timeoutMs) {
//        File inputFile = null;
//        Process process = null;
//
//        try {
//            // [新增] 每次运行，计数器+1
//            totalExecutions++;
//
//            // 1. 创建临时种子文件
//            inputFile = File.createTempFile("fuzz_input_", ".bin");
//            Files.write(inputFile.toPath(), inputData);
//            String inputFilePath = inputFile.getAbsolutePath();
//
//            // 2. 构建命令
//            List<String> command = new ArrayList<>();
//            command.add(targetPath);
//            for (String arg : args) {
//                if (arg.equals("@@")) {
//                    command.add(inputFilePath);
//                } else {
//                    command.add(arg);
//                }
//            }
//
//            ProcessBuilder pb = new ProcessBuilder(command);
//
//            // 3. 注入监控 (Shared Memory)
//            if (monitor != null) {
//                monitor.clear(); // 清空当次位图
//                pb.environment().put("__AFL_SHM_ID", monitor.getShmIdString());
//            }
//
//            // 4. 优化：丢弃输出流以提升速度 (Fuzzing通常不看stdout)
//            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
//            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
//
//            long startTime = System.currentTimeMillis();
//            process = pb.start();
//
//            // 5. 等待结果
//            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
//            long endTime = System.currentTimeMillis();
//            long duration = endTime - startTime;
//
//            if (!finished) {
//                process.destroyForcibly();
//                return new ExecutionResult(RunStatus.HANG, -1, duration);
//            }
//
//            int exitCode = process.exitValue();
//
//            // Linux 信号处理: > 128 通常意味着被信号杀死 (Crash)
//            if (exitCode > 128) {
//                return new ExecutionResult(RunStatus.CRASH, exitCode, duration);
//            } else {
//                return new ExecutionResult(RunStatus.NORMAL, exitCode, duration);
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return new ExecutionResult(RunStatus.NORMAL, -1, 0);
//        } finally {
//            // 清理临时文件
//            if (inputFile != null && inputFile.exists()) {
//                inputFile.delete();
//            }
//        }
//    }

    /**
     * [新增] 保存特殊测试用例 (满足作业要求: 保存特殊测试用例)
     * @param data 种子数据
     * @param outputDir 保存目录 (如 "crashes" 或 "queue")
     * @param suffix 文件名后缀
     */
    public void saveInput(byte[] data, String outputDir, String suffix) {
        try {
            File dir = new File(outputDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            // 命名格式: id_count_suffix
            String filename = String.format("id_%06d_%s", totalExecutions, suffix);
            Path path = Paths.get(outputDir, filename);
            Files.write(path, data);
            System.out.println("[+] Saved interesting input: " + path.toString());
        } catch (IOException e) {
            System.err.println("[!] Failed to save input: " + e.getMessage());
        }
    }

    // Main 用于简单的单元测试
    public static void main(String[] args) {
        System.out.println("TestRunner compiled successfully. Please run via Fuzzer class.");
    }
}
//package edu.nju.isefuzz;
//
//import java.io.*;
//import java.nio.file.Files;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//
///**
// * 测试执行组件 (Test Execution Component)
// * 集成了监控组件，负责启动目标程序并收集覆盖率
// */
//public class TestRunner {
//
//    // --- [修改点 1] 引入监控组件 ---
//    private CoverageMonitor monitor;
//
//    public void setMonitor(CoverageMonitor monitor) {
//        this.monitor = monitor;
//    }
//    // ----------------------------
//
//    public enum RunStatus {
//        NORMAL, CRASH, HANG
//    }
//
//    public static class ExecutionResult {
//        public RunStatus status;
//        public int exitCode;
//        public long executionTime;
//
//        public ExecutionResult(RunStatus status, int exitCode, long executionTime) {
//            this.status = status;
//            this.exitCode = exitCode;
//            this.executionTime = executionTime;
//        }
//    }
//
//    public ExecutionResult run(String targetPath, List<String> args, byte[] inputData, int timeoutMs) {
//        File inputFile = null;
//        Process process = null;
//
//        try {
//            // 1. 创建临时种子文件
//            inputFile = File.createTempFile("fuzz_input_", ".bin");
//            Files.write(inputFile.toPath(), inputData);
//            String inputFilePath = inputFile.getAbsolutePath();
//
//            // 2. 构建命令
//            List<String> command = new ArrayList<>();
//            command.add(targetPath);
//            for (String arg : args) {
//                if (arg.equals("@@")) {
//                    command.add(inputFilePath);
//                } else {
//                    command.add(arg);
//                }
//            }
//
//            ProcessBuilder pb = new ProcessBuilder(command);
//
//            // --- [修改点 2] 注入共享内存 ID ---
//            if (monitor != null) {
//                // A. 运行前必须清空位图，否则覆盖率会叠加
//                monitor.clear();
//
//                // B. 设置环境变量，告诉 C 程序往哪块内存写数据
//                String shmId = monitor.getShmIdString();
//                pb.environment().put("__AFL_SHM_ID", shmId);
//            }
//            // --------------------------------
//
//            // 丢弃输出流以提高速度
//            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
//            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
//
//            long startTime = System.currentTimeMillis();
//            process = pb.start();
//
//            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
//            long endTime = System.currentTimeMillis();
//            long duration = endTime - startTime;
//
//            if (!finished) {
//                process.destroyForcibly();
//                return new ExecutionResult(RunStatus.HANG, -1, duration);
//            }
//
//            int exitCode = process.exitValue();
//
//            // 简单判断 Crash (Linux 信号通常 > 128)
//            if (exitCode > 128) {
//                return new ExecutionResult(RunStatus.CRASH, exitCode, duration);
//            } else {
//                return new ExecutionResult(RunStatus.NORMAL, exitCode, duration);
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return new ExecutionResult(RunStatus.NORMAL, -1, 0);
//        } finally {
//            if (inputFile != null && inputFile.exists()) {
//                inputFile.delete();
//            }
//        }
//    }
//
//    // --- [修改点 3] Main 方法增加监控测试 ---
//    public static void main(String[] args) {
//        TestRunner runner = new TestRunner();
//
//        // 1. 初始化监控组件 (在 Docker 里才能成功)
//        try {
//            CoverageMonitor monitor = new CoverageMonitor();
//            monitor.setup(); // 申请共享内存
//            runner.setMonitor(monitor);
//            System.out.println("[*] Monitor setup OK. SHM ID: " + monitor.getShmIdString());
//        } catch (Throwable e) {
//            System.err.println("[!] Monitor setup failed (Are you in Windows? Must run in Docker): " + e.getMessage());
//            // 继续运行，方便在 Windows 调试逻辑，虽然没有覆盖率
//        }
//
//        System.out.println("\n=== Testing cxxfilt with Coverage ===");
//        // cxxfilt 不需要 @@，它从参数读或者 stdin，这里我们测试最简单的直接运行参数
//        // 为了触发覆盖率，我们随便传个参数。
//        // 注意：如果 cxxfilt 必须读文件，这里逻辑要调整。
//        // 但大部分 targets 如 readelf 是必须读文件的，我们用 readelf 模拟一下：
//
//        List<String> argsList = new ArrayList<>();
//        // 假设我们测试 cxxfilt，可以直接把字符串当参数传进去，不一定非要用文件
//        // 但为了复用 run() 里的文件逻辑，我们暂时假装它读文件，或者留空
//
//        // 针对 cxxfilt 的特殊测试：它其实是从 stdin 读的比较多。
//        // 为了验证监控组件，我们还是建议用 readelf (T02) 这种标准文件输入的程序测最准。
//        // 下面尝试运行一下 cxxfilt
//
//        String target = "/app/targets/cxxfilt";
//        // cxxfilt 可以不带参数运行，等待 stdin。但我们的 run() 逻辑是文件驱动。
//        // 让我们直接改用 readelf 来验证，因为它肯定有覆盖率变化
//
//        File checkTarget = new File("/app/targets/readelf");
//        if(checkTarget.exists()) {
//            target = "/app/targets/readelf";
//            argsList.add("-a");
//            argsList.add("@@");
//            System.out.println("[*] Detected readelf, using it for test.");
//        } else {
//            System.out.println("[*] Using cxxfilt (Note: coverage might be low if not fed correctly).");
//        }
//
//        byte[] seed = "Anything".getBytes();
//
//        // 运行第一次
//        ExecutionResult res = runner.run(target, argsList, seed, 1000);
//        System.out.println("Result: " + res.status + ", Exit: " + res.exitCode);
//
//        // 2. 检查覆盖率
//        if (runner.monitor != null) {
//            int hits = runner.monitor.countCoverage();
//            System.out.println(">>> Coverage Hits: " + hits);
//
//            if (hits > 0) {
//                System.out.println("[SUCCESS] 监控组件工作正常！抓到了覆盖率！");
//            } else {
//                System.out.println("[WARNING] 覆盖率为 0。可能是目标程序没运行，或者没插装。");
//            }
//
//            runner.monitor.cleanup(); // 别忘了清理
//        }
//    }
//}
////package edu.nju.isefuzz;
////
////import java.io.*;
////import java.nio.file.Files;
////import java.nio.file.Path;
////import java.util.ArrayList;
////import java.util.List;
////import java.util.concurrent.TimeUnit;
////
/////**
//// * 测试执行组件 (Test Execution Component)
//// * 对应 AFL++ 中的 fuzz_run_target 逻辑
//// */
////public class TestRunner {
////
////    // 定义执行结果状态，参考 afl-fuzz.h 中的定义
////    // FAULT_NONE 0, FAULT_TMOUT 1, FAULT_CRASH 2
////    public enum RunStatus {
////        NORMAL, // 正常结束
////        CRASH,  // 发生崩溃 (Segmentation Fault 等)
////        HANG    // 超时
////    }
////
////    public static class ExecutionResult {
////        public RunStatus status;
////        public int exitCode;
////        public long executionTime; // 毫秒
////
////        public ExecutionResult(RunStatus status, int exitCode, long executionTime) {
////            this.status = status;
////            this.exitCode = exitCode;
////            this.executionTime = executionTime;
////        }
////    }
////
////    /**
////     * 核心方法：运行目标程序
////     * @param targetPath 目标程序路径 (e.g., /app/targets/readelf)
////     * @param args 目标程序参数列表 (e.g., ["-a", "@@"])，"@@" 会被替换为实际输入文件路径
////     * @param inputData 喂给程序的种子数据 (byte数组)
////     * @param timeoutMs 超时时间 (毫秒)
////     */
////    public ExecutionResult run(String targetPath, List<String> args, byte[] inputData, int timeoutMs) {
////        File inputFile = null;
////        Process process = null;
////
////        try {
////            // 1. [Input Preparation] 将种子数据写入临时文件
////            // 对应 AFL 的 .cur_input
////            inputFile = File.createTempFile("fuzz_input_", ".bin");
////            Files.write(inputFile.toPath(), inputData);
////            String inputFilePath = inputFile.getAbsolutePath();
////
////            // 2. [Command Construction] 构建执行命令
////            List<String> command = new ArrayList<>();
////            command.add(targetPath);
////
////            boolean replaced = false;
////            for (String arg : args) {
////                if (arg.equals("@@")) {
////                    command.add(inputFilePath); // 替换 @@ 为文件路径
////                    replaced = true;
////                } else {
////                    command.add(arg);
////                }
////            }
////            // 如果参数里没写 @@，默认如果不通过 stdin 喂数据，通常是把文件名放在最后
////            // 这里为了简单，如果用户没指定位置，我们假设不需要文件参数(或通过stdin)
////            // 但对于 readelf, djpeg 等，必须有文件名。
////
////            ProcessBuilder pb = new ProcessBuilder(command);
////
////            // 将子进程的输出重定向到空（或者是文件），避免撑爆缓存导致死锁
////            // 在实际 Fuzzing 中我们通常不关心 stdout，只关心是否 Crash
////            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
////            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
////
////            long startTime = System.currentTimeMillis();
////
////            // 3. [Execution] 启动子进程
////            process = pb.start();
////
////            // 4. [Monitor] 等待结果
////            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
////            long endTime = System.currentTimeMillis();
////            long duration = endTime - startTime;
////
////            if (!finished) {
////                // 超时处理
////                process.destroyForcibly();
////                return new ExecutionResult(RunStatus.HANG, -1, duration);
////            }
////
////            int exitCode = process.exitValue();
////
////            // 5. [Classification] 判断结果
////            // 在 Linux 中，如果进程被信号杀死 (如 SegFault)，exit code 通常是 128 + signal
////            // 139 = 128 + 11 (SIGSEGV)
////            // 134 = 128 + 6 (SIGABRT)
////            if (exitCode != 0) {
////                // 这里简单粗暴判断：非0即由问题 (或者特定错误码)
////                // 具体的判断逻辑可以参考 WIFSIGNALED 宏
////                // 对于作业，通常 > 128 视为 Crash
////                if (exitCode > 128) {
////                    return new ExecutionResult(RunStatus.CRASH, exitCode, duration);
////                } else {
////                    // 程序报错但没崩 (比如 "File not found")，视为 Normal 但有错误码
////                    return new ExecutionResult(RunStatus.NORMAL, exitCode, duration);
////                }
////            }
////
////            return new ExecutionResult(RunStatus.NORMAL, 0, duration);
////
////        } catch (Exception e) {
////            e.printStackTrace();
////            return new ExecutionResult(RunStatus.NORMAL, -1, 0);
////        } finally {
////            // 清理临时文件
////            if (inputFile != null && inputFile.exists()) {
////                inputFile.delete();
////            }
////        }
////    }
////
////    // ==========================================
////    // Main 方法用于测试组件是否工作正常
////    // ==========================================
////    public static void main(String[] args) {
////        TestRunner runner = new TestRunner();
////
////        // --- 测试案例 1: cxxfilt (从 stdin 读，或者参数) ---
////        // cxxfilt 比较特殊，它既可以读文件，也可以直接读参数。
////        // 为了演示文件模式，我们用 readelf 或 djpeg 更合适，但这里先用 cxxfilt 简单测试
////
////        System.out.println("=== Test 1: Testing cxxfilt (Wait for Normal) ===");
////        String target1 = "/app/targets/cxxfilt";
////        // 参数为空，因为我们不通过 @@ 传文件，而是直接测试基础运行
////        // (注：如果要完全适配 cxxfilt 的 stdin 模式，上面的 run 方法需要微调，
////        // 但为了通用性，我们主要针对 readelf/djpeg 这种文件型目标)
////
////        // 让我们换个更典型的目标：readelf (如果它已经编译好了)
////        // 假设我们有一个 dummy 种子
////        String seedString = "ELF...some_random_data...";
////        byte[] seedData = seedString.getBytes();
////
////        // 尝试运行
////        // 注意：这里只是演示调用，Docker 里如果 readelf 没装好可能会报错
////        // 我们可以回退用 cxxfilt 测试最简单的 "启动能力"
////
////        // 使用 cxxfilt 测试
////        try {
////            ProcessBuilder pb = new ProcessBuilder("/app/targets/cxxfilt", "_Z1fv");
////            Process p = pb.start();
////            p.waitFor();
////            System.out.println("cxxfilt manual run exit code: " + p.exitValue());
////        } catch(Exception e) {
////            e.printStackTrace();
////        }
////
////        System.out.println("\n=== Test 2: Generic Runner Logic (Mocking) ===");
////        // 模拟一个输入
////        List<String> argsList = new ArrayList<>();
////        // 假设我们要测试 readelf，命令格式通常是: readelf -a <file>
////        argsList.add("-a");
////        argsList.add("@@"); // @@ 会被替换为临时文件路径
////
////        // 这里的 target 需要是你 Docker 里真实存在的程序路径
////        // 如果你还没确定除了 cxxfilt 谁能跑，先写 cxxfilt
////        // cxxfilt 不需要 -a 参数，直接跟文件名
////        ExecutionResult result = runner.run("/app/targets/cxxfilt", new ArrayList<>() {{ add("@@"); }}, "_Z1fv".getBytes(), 1000);
////
////        System.out.println("Status: " + result.status);
////        System.out.println("Exit Code: " + result.exitCode);
////        System.out.println("Time: " + result.executionTime + "ms");
////
////        if (result.status == RunStatus.NORMAL) {
////            System.out.println("[SUCCESS] 组件运行正常！");
////        }
////
////    }
////}
//////package edu.nju.isefuzz;
//////
//////import java.io.*;
//////import java.util.concurrent.TimeUnit;
//////
//////public class TestRunner {
//////    public static void main(String[] args) {
//////        // 1. 目标程序的路径 (这是 Docker 里的路径，不是 Windows 的 D:\...)
//////        String targetPath = "/app/targets/cxxfilt";
//////
//////        // 2. 准备种子 (Seed)：我们要测试的输入字符串
//////        // "_Z1fv" 是 C++ 里的一个混淆符号，cxxfilt 应该能把它还原
//////        String inputSeed = "_Z1fv";
//////
//////        System.out.println("[*] Starting target program: " + targetPath);
//////
//////        try {
//////            // 3. 构建进程 (相当于在命令行输入 /app/targets/cxxfilt)
//////            ProcessBuilder pb = new ProcessBuilder(targetPath);
//////            pb.redirectErrorStream(true); // 把错误输出也合并到标准输出方便查看
//////
//////            Process process = pb.start();
//////
//////            // 4. 喂数据 (通过标准输入流 Stdin)
//////            // 相当于你在键盘上输入 _Z1fv 然后按回车
//////            OutputStream stdin = process.getOutputStream();
//////            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));
//////            writer.write(inputSeed);
//////            writer.write("\n"); // 这一行很重要，模拟回车
//////            writer.flush();
//////            writer.close();
//////
//////            // 5. 读结果 (通过标准输出流 Stdout)
//////            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//////            String line;
//////            while ((line = reader.readLine()) != null) {
//////                System.out.println(">>> Output: " + line);
//////            }
//////
//////            // 6. 等待程序结束
//////            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
//////            if (finished) {
//////                System.out.println("[*] Program exited with code: " + process.exitValue());
//////            } else {
//////                process.destroy();
//////                System.out.println("[!] time exceeded, program killed");
//////            }
//////
//////        } catch (Exception e) {
//////            e.printStackTrace();
//////        }
//////    }
//////}
