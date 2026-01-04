package edu.nju.isefuzz;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 种子排序组件 (Seed Queue/Scheduling)
 * 对应 AFL afl-fuzz.c 中的 main 循环与 queue 管理逻辑
 */
public class SeedQueue {

    // 种子仓库
    private final List<Seed> queue = new ArrayList<>();

    // 当前指向的种子索引 (对应 afl->queue_cur)
    private int currentIndex = 0;

    // 排序策略枚举
    public enum Strategy {
        FIFO,           // 先入先出 (默认，按入队顺序)
        SHORTEST_FIRST, // 最短优先 (非随机排序，AFL 经典策略)
        // 扩展项: EXEC_TIME_FIRST (最快优先)
    }

    private Strategy currentStrategy = Strategy.FIFO;

    /**
     * 1. 加载初始种子目录
     */
    public void loadSeeds(String seedInputDir) {
        File dir = new File(seedInputDir);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("[!] Seed directory not found: " + seedInputDir);
            return;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && !f.getName().startsWith(".")) {
                    try {
                        addSeed(new Seed(f));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        System.out.println("[*] Loaded " + queue.size() + " seeds from " + seedInputDir);
        // 初始加载后，可以先排个序
        sort();
    }

    /**
     * 2. 添加新种子 (例如发现了新路径)
     */
    public void addSeed(Seed seed) {
        queue.add(seed);
    }

    /**
     * 3. 设置排序策略
     */
    public void setStrategy(Strategy strategy) {
        this.currentStrategy = strategy;
        System.out.println("[Queue] Strategy changed to: " + strategy);
        sort(); // 策略改变时立即重排
    }

    /**
     * 核心：排序逻辑 (对应 cull_queue 的部分思想)
     */
    private void sort() {
        if (queue.isEmpty()) return;

        switch (currentStrategy) {
            case FIFO:
                // 不需要操作，保持 List 顺序即可
                // 但为了严谨，其实 FIFO 应该是不变，这里假设 add 顺序就是 FIFO
                break;

            case SHORTEST_FIRST:
                // 按长度升序排列 (Lambda 表达式)
                Collections.sort(queue, Comparator.comparingLong(Seed::getLength));
                System.out.println("[Queue] Re-sorted seeds by length (Shortest First).");
                break;
        }
        // 排序后重置索引，从头开始跑
        currentIndex = 0;
    }

    /**
     * 4. 获取下一个种子 (Next)
     * 模拟 AFL main 循环中的 queue_cur = queue_cur->next
     */
    public Seed getNext() {
        if (queue.isEmpty()) return null;

        // 如果跑到了队尾，这就叫完成了一个 "Cycle"
        if (currentIndex >= queue.size()) {
            currentIndex = 0; // 重新开始 (Round Robin)
            System.out.println("[Queue] Cycle completed. Restarting from head.");

            // 在每一轮循环开始前，你其实可以重新 sort() 一次
            // 因为中间可能插入了新种子
            sort();
        }

        Seed s = queue.get(currentIndex);
        currentIndex++;
        return s;
    }

    // 辅助：获取队列大小
    public int size() {
        return queue.size();
    }
}
//package edu.nju.isefuzz;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.List;
//
///**
// * 种子排序/调度组件 (Seed Selection & Scheduling)
// * 对应 AFL 的 afl-fuzz.c 中的 queue 处理逻辑
// */
//public class SeedQueue {
//
//    // 种子队列
//    private final List<Seed> queue = new ArrayList<>();
//
//    // 当前指针 (Round Robin 循环指针)
//    private int currentIndex = 0;
//
//    // 排序策略枚举
//    public enum Strategy {
//        FIFO,       // 先入先出 (默认顺序)
//        SMART_SORT  // 智能排序 (覆盖率优先，其次是长度)
//    }
//
//    private Strategy currentStrategy = Strategy.FIFO;
//
//    /**
//     * 添加新种子到队列
//     */
//    public void add(Seed seed) {
//        queue.add(seed);
//    }
//
//    /**
//     * 获取队列大小
//     */
//    public int size() {
//        return queue.size();
//    }
//
//    /**
//     * 设置排序策略并立即重新排序
//     */
//    public void setStrategy(Strategy strategy) {
//        this.currentStrategy = strategy;
//        reorder();
//    }
//
//    /**
//     * 核心方法：执行排序逻辑
//     * 对应 AFL 的 cull_queue 部分思想
//     */
//    public void reorder() {
//        if (queue.isEmpty()) return;
//
//        System.out.println("[Scheduler] Reordering queue using strategy: " + currentStrategy);
//
//        if (currentStrategy == Strategy.FIFO) {
//            // FIFO 模式下，通常不需要重新排序，保持插入顺序即可
//            // 这里假设 add 也就是 append，不做额外操作
//            // 如果需要严格按文件名ID排序，可以在这里 sort by filename
//        } else if (currentStrategy == Strategy.SMART_SORT) {
//            // [非随机排序实现]
//            // 策略：优先选择 "高覆盖率" 且 "体积小" 的种子
//            // 这是一种贪心策略，希望能更快发现更多路径
//            Collections.sort(queue, new Comparator<Seed>() {
//                @Override
//                public int compare(Seed s1, Seed s2) {
//                    // 1. 覆盖率高的排前面 (Descending)
//                    if (s1.coverageScore != s2.coverageScore) {
//                        return Integer.compare(s2.coverageScore, s1.coverageScore);
//                    }
//                    // 2. 如果覆盖率一样，体积小的排前面 (Ascending)
//                    if (s1.length != s2.length) {
//                        return Long.compare(s1.length, s2.length);
//                    }
//                    // 3. 最后按执行时间，快的排前面
//                    return Long.compare(s1.execTime, s2.execTime);
//                }
//            });
//        }
//
//        // 排序后重置指针，从最好的开始跑
//        currentIndex = 0;
//    }
//
//    /**
//     * 获取下一个要进行变异测试的种子
//     * 实现了循环队列逻辑
//     */
//    public Seed next() {
//        if (queue.isEmpty()) return null;
//
//        // 如果指针超出范围，这就意味着一轮循环（Pass）结束了
//        if (currentIndex >= queue.size()) {
//            currentIndex = 0; // 回到头部
//            // 在 AFL 中，这里通常会重新 cull_queue (reorder)
//            // 我们为了演示，每跑完一轮就重新排序一次，确保新加入的种子能被排进去
//            reorder();
//        }
//
//        Seed target = queue.get(currentIndex);
//        currentIndex++;
//        return target;
//    }
//
//    // --- 辅助方法：打印当前队列状态 ---
//    public void printQueueInfo() {
//        System.out.println("--- Queue Status (" + queue.size() + " seeds) ---");
//        for (int i = 0; i < Math.min(queue.size(), 10); i++) {
//            System.out.println("[" + i + "] " + queue.get(i));
//        }
//        if (queue.size() > 10) System.out.println("... (rest omitted)");
//    }
//}