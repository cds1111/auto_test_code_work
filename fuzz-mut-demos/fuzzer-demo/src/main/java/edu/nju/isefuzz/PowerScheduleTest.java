package edu.nju.isefuzz;

public class PowerScheduleTest {
    public static void main(String[] args) {
        PowerSchedule scheduler = new PowerSchedule();

        // 1. 创建几个模拟种子
        // 种子A: 普通 (基准)
        Seed seedA = new Seed("A", new byte[]{});
        seedA.setExecTime(100);    // 100ms
        seedA.setBitmapSize(500);  // 覆盖500个点
        scheduler.updateStats(seedA); // 让调度器知道当前的平均水平

        // 种子B: 优等生 (跑得快，覆盖多)
        Seed seedB = new Seed("B", new byte[]{});
        seedB.setExecTime(20);     // 20ms (快5倍)
        seedB.setBitmapSize(800);  // 覆盖800 (高1.6倍)

        // 种子C: 差生 (跑得慢，覆盖少)
        Seed seedC = new Seed("C", new byte[]{});
        seedC.setExecTime(400);    // 400ms (慢4倍)
        seedC.setBitmapSize(100);  // 覆盖100 (少5倍)

        // 2. 计算能量
        int energyA = scheduler.assignEnergy(seedA);
        int energyB = scheduler.assignEnergy(seedB);
        int energyC = scheduler.assignEnergy(seedC);

        System.out.println("--- Power Schedule Test ---");
        System.out.println("Average Exec: " + 100 + "ms, Avg Bitmap: " + 500);
        System.out.println("\nSeed A (Avg):   Energy = " + energyA);
        System.out.println("Seed B (Good):  Energy = " + energyB + "  <-- 应该最高");
        System.out.println("Seed C (Bad):   Energy = " + energyC + "  <-- 应该最低");
    }
}