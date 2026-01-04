package edu.nju.isefuzz;

public class SeedQueueTest {
    public static void main(String[] args) {
        SeedQueue q = new SeedQueue();

        // 模拟手动添加几个种子
        System.out.println("--- Adding seeds (A, B, C) ---");
        q.addSeed(new Seed("long_file", new byte[]{1, 2, 3, 4, 5}));      // Len 5
        q.addSeed(new Seed("short_file", new byte[]{1}));                 // Len 1
        q.addSeed(new Seed("medium_file", new byte[]{1, 2, 3}));          // Len 3

        // 1. 测试默认 FIFO (入队顺序)
        System.out.println("\n[Test 1] Strategy: FIFO");
        q.setStrategy(SeedQueue.Strategy.FIFO);
        printTop3(q);

        // 2. 测试 Shortest First (应该变成: short -> medium -> long)
        System.out.println("\n[Test 2] Strategy: SHORTEST_FIRST");
        q.setStrategy(SeedQueue.Strategy.SHORTEST_FIRST);
        printTop3(q);
    }

    private static void printTop3(SeedQueue q) {
        // 注意：getNext 会移动指针，为了演示我们只取前3个打印
        // 实际使用时不要这样连续取，除非为了测试
        // 这里只是为了看排序效果，重启时会 reset index
        // 我们 hack 一下重新排序 reset index

        for (int i = 0; i < q.size(); i++) {
            Seed s = q.getNext();
            System.out.println("Got seed: " + s);
        }
    }
}
//package edu.nju.isefuzz;
//
//public class QueueTest {
//    public static void main(String[] args) {
//        SeedQueue scheduler = new SeedQueue();
//
//        // 1. 模拟创建一些种子
//        Seed s1 = new Seed(new byte[10], "id_001");
//        s1.coverageScore = 50; s1.length = 100;
//
//        Seed s2 = new Seed(new byte[20], "id_002");
//        s2.coverageScore = 100; s2.length = 200; // 覆盖率最高，应该排第一
//
//        Seed s3 = new Seed(new byte[5], "id_003");
//        s3.coverageScore = 50; s3.length = 10;   // 覆盖率和s1一样，但更短，应该排s1前面
//
//        scheduler.add(s1);
//        scheduler.add(s2);
//        scheduler.add(s3);
//
//        // 2. 测试默认 FIFO 顺序
//        System.out.println(">>> Testing FIFO Strategy:");
//        scheduler.printQueueInfo();
//
//        // 3. 测试智能排序
//        System.out.println("\n>>> Testing SMART_SORT Strategy:");
//        scheduler.setStrategy(SeedQueue.Strategy.SMART_SORT);
//        scheduler.printQueueInfo();
//
//        // 验证预期顺序： s2 (Cov 100) -> s3 (Cov 50, Len 10) -> s1 (Cov 50, Len 100)
//        Seed first = scheduler.next();
//        if (first.filename.equals("id_002")) {
//            System.out.println("\n[SUCCESS] 排序逻辑正确！高覆盖率种子优先。");
//        } else {
//            System.out.println("\n[FAIL] 排序逻辑有误。");
//        }
//    }
//}