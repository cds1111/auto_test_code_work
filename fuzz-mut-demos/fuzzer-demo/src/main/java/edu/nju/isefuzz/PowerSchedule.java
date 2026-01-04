package edu.nju.isefuzz;

/**
 * 能量调度组件 (Power Schedule Component)
 * 职责：根据种子的表现（速度、覆盖率）分配变异能量
 * 参考：AFL afl-fuzz.c 中的 calculate_score 函数
 */
public class PowerSchedule {

    // 全局统计数据，用于对比
    private long avgExecTime = 0;
    private double avgBitmapSize = 0;
    private long totalSeeds = 0;
    private long totalExecTime = 0;
    private long totalBitmapSize = 0;

    /**
     * 当有新种子加入队列时，更新全局平均值
     */
    public void updateStats(Seed seed) {
        totalSeeds++;
        totalExecTime += seed.getExecTime();
        totalBitmapSize += seed.getBitmapSize();

        if (totalSeeds > 0) {
            avgExecTime = totalExecTime / totalSeeds;
            avgBitmapSize = (double) totalBitmapSize / totalSeeds;
        }
    }

    /**
     * 核心算法：计算能量 (Power)
     * @param seed 要测试的种子
     * @return 应该进行的变异次数 (Mutations count)
     */
    public int assignEnergy(Seed seed) {
        // 基础分数 (AFL 默认起步分是 100)
        int perfScore = 100;

        long execUs = seed.getExecTime();
        int bitmapSize = seed.getBitmapSize();

        // ----------------------------------------------------
        // 1. 速度调整 (Execution Speed)
        // 跑得越快，给的能量越多
        // ----------------------------------------------------
        if (avgExecTime > 0) {
            if (execUs * 0.1 > avgExecTime) perfScore = 10;       // 特别慢 (10%)
            else if (execUs * 0.25 > avgExecTime) perfScore = 25; // 很慢 (25%)
            else if (execUs * 0.5 > avgExecTime) perfScore = 50;  // 慢 (50%)
            else if (execUs * 0.75 > avgExecTime) perfScore = 75; // 稍慢 (75%)
            else if (execUs * 4 < avgExecTime) perfScore = 300;   // 特别快 (300%)
            else if (execUs * 3 < avgExecTime) perfScore = 200;   // 很快 (200%)
            else if (execUs * 2 < avgExecTime) perfScore = 150;   // 快 (150%)
        }

        // ----------------------------------------------------
        // 2. 覆盖率调整 (Bitmap Size)
        // 覆盖路径越多，说明这可能是一个好种子，给更多能量
        // ----------------------------------------------------
        if (avgBitmapSize > 0) {
            if (bitmapSize * 0.3 > avgBitmapSize) perfScore *= 3;
            else if (bitmapSize * 0.5 > avgBitmapSize) perfScore *= 2;
            else if (bitmapSize * 0.75 > avgBitmapSize) perfScore *= 1.5;
            else if (bitmapSize * 3 < avgBitmapSize) perfScore *= 0.25;
            else if (bitmapSize * 2 < avgBitmapSize) perfScore *= 0.5;
            else if (bitmapSize * 1.5 < avgBitmapSize) perfScore *= 0.75;
        }

        // 3. 深度 (Depth) / 障碍 (Handicap)
        // (为了作业简化，暂时忽略这两个因素，上面的两个 heuristic 已经足够满足作业要求)

        // 4. 最终能量计算
        // 将抽象的分数转换为具体的“变异次数”。
        // 假设基础变异轮次是 16 (AFL 中的 havoc_div 概念)
        // Score 100 -> ~500 mutations

        // 归一化，防止分数为0
        if (perfScore < 10) perfScore = 10;

        // 我们设定：能量 = 分数 * 系数
        // 比如分数 100 代表要进行 1000 次变异尝试
        int energy = perfScore * 10;

        // 设定上限，防止死循环 (AFL 通常也有上限)
        if (energy > 20000) energy = 20000;

        return energy;
    }
}