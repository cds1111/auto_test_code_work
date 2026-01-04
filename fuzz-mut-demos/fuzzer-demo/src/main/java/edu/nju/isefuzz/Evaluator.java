package edu.nju.isefuzz;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * 评估组件 (Evaluation Component)
 * 职责：记录实验数据（时间、执行次数、覆盖率），用于后续画图分析
 * 参考：AFL afl-fuzz-stats.c 中的 write_plot_data
 */
public class Evaluator {

    private final String outputDir;
    private final long startTime;
    private PrintWriter csvWriter;

    public Evaluator(String outputDir) {
        this.outputDir = outputDir;
        this.startTime = System.currentTimeMillis();
        setup();
    }

    /**
     * 初始化：创建目录和 CSV 文件
     */
    private void setup() {
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File csvFile = new File(dir, "fuzzer_stats.csv");
        try {
            // true 表示追加模式 (append)，但在新一轮实验开始时通常覆盖
            // 这里为了简单，每次 new Evaluator 都会覆盖旧文件
            csvWriter = new PrintWriter(new FileWriter(csvFile, false));
            // 写入表头
            csvWriter.println("Time(s),Execs,Coverage");
            csvWriter.flush();
            System.out.println("[Evaluator] Stats file created at: " + csvFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 核心方法：记录一个数据点
     * @param totalExecs 当前总执行次数
     * @param currentCoverage 当前覆盖率（边数）
     */
    public void log(long totalExecs, int currentCoverage) {
        if (csvWriter == null) return;

        // 计算相对时间（秒）
        long currentTime = System.currentTimeMillis();
        double timeSeconds = (currentTime - startTime) / 1000.0;

        // 写入一行: 时间,次数,覆盖率
        csvWriter.printf("%.2f,%d,%d%n", timeSeconds, totalExecs, currentCoverage);
        csvWriter.flush();
    }

    /**
     * 关闭资源
     */
    public void close() {
        if (csvWriter != null) {
            csvWriter.close();
        }
    }
}