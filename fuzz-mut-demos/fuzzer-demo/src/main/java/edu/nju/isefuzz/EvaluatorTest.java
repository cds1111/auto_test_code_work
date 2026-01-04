package edu.nju.isefuzz;

import java.io.File;
import java.util.Random;

public class EvaluatorTest {
    public static void main(String[] args) throws Exception {
        // 1. 设置输出目录
        String outDir = "output";
        Evaluator evaluator = new Evaluator(outDir);

        System.out.println("--- Simulating Fuzzing Process ---");

        // 2. 模拟 Fuzzing 过程：每隔 100ms 记录一次数据
        long execs = 0;
        int coverage = 50; // 初始覆盖率
        Random rand = new Random();

        for (int i = 0; i < 20; i++) {
            execs += 100 + rand.nextInt(50);

            // 模拟偶尔发现新路径，覆盖率增加
            if (rand.nextInt(10) > 6) {
                coverage += 1 + rand.nextInt(5);
                System.out.println("New path found! Cov: " + coverage);
            }

            evaluator.log(execs, coverage);
            Thread.sleep(100); // 模拟耗时
        }

        evaluator.close();
        System.out.println("[*] Data logging finished.");

        // 3. (可选) 尝试调用 Python 脚本自动画图
        // 这一步是为了让你在 Docker 里一键看到结果
        try {
            System.out.println("[*] Calling plot.py to generate graph...");
            ProcessBuilder pb = new ProcessBuilder("python3", "plot.py", "output/fuzzer_stats.csv", "output/trend.png");
            // 假设 plot.py 在项目根目录，即 user.dir
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
        } catch (Exception e) {
            System.out.println("[!] Could not run python script automatically. Please run manually: python3 plot.py");
        }
    }
}