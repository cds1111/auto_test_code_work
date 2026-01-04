package edu.nju.isefuzz;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.unix.LibC;

import java.nio.ByteBuffer;

/**
 * 执行结果监控组件 (Execution Monitor Component)
 * 职责：管理共享内存、记录覆盖率历史、计算执行速度、判断是否发现新路径
 */
public class CoverageMonitor {

    public static final int MAP_SIZE = 65536;

    // Linux IPC 常量
    private static final int IPC_CREAT = 01000;
    private static final int IPC_EXCL = 02000;
    private static final int IPC_RMID = 0;

    private int shmId;
    private Pointer shmPtr;
    private ByteBuffer traceBits; // 当前运行的覆盖率 (Current)

    // [新增] 全局覆盖率历史 (History)
    // 用来记录从开始到现在，哪些路径已经被探索过了
    private byte[] globalBits;

    // [新增] 记录开始时间，用于计算速度
    private long startTime;

    public interface LinuxLibC extends LibC {
        LinuxLibC INSTANCE = Native.load("c", LinuxLibC.class);
        int shmget(int key, int size, int shmflg);
        Pointer shmat(int shmid, Pointer shmaddr, int shmflg);
        int shmctl(int shmid, int cmd, Pointer buf);
    }

    public void setup() {
        // 1. 申请共享内存
        this.shmId = LinuxLibC.INSTANCE.shmget(0, MAP_SIZE, IPC_CREAT | IPC_EXCL | 0600);
        if (this.shmId < 0) {
            throw new RuntimeException("Failed to allocate shared memory id: " + shmId);
        }

        // 2. 挂载
        this.shmPtr = LinuxLibC.INSTANCE.shmat(this.shmId, null, 0);
        if (Pointer.nativeValue(shmPtr) == -1) {
            throw new RuntimeException("Failed to attach shared memory");
        }

        // 3. 映射到 Java 对象
        this.traceBits = shmPtr.getByteBuffer(0, MAP_SIZE);

        // [新增] 初始化全局历史记录
        this.globalBits = new byte[MAP_SIZE];
        this.startTime = System.currentTimeMillis();

        System.out.println("[Monitor] Shared Memory ID: " + shmId);
    }

    public String getShmIdString() {
        return String.valueOf(shmId);
    }

    public void clear() {
        // 每次运行前清空 Shared Memory，但保留 Global Bits
        // 这是一个比较耗时的操作，实战中可以使用 Unsafe 优化，这里先用简单循环
        for (int i = 0; i < MAP_SIZE; i++) {
            traceBits.put(i, (byte) 0);
        }
    }

    /**
     * [新增] 核心逻辑：检查是否发现了新路径，并更新全局历史
     * @return true 如果发现了新覆盖率 (Interesting!)
     */
    public boolean checkAndUpdate() {
        boolean isInteresting = false;

        for (int i = 0; i < MAP_SIZE; i++) {
            byte hit = traceBits.get(i);
            if (hit != 0) {
                // 如果当前命中了，且全局历史里没命中过，那就是新发现！
                if (globalBits[i] == 0) {
                    globalBits[i] = 1; // 标记为已访问
                    isInteresting = true;
                }
            }
        }
        return isInteresting;
    }

    /**
     * [新增] 计算当前覆盖了多少条边 (Total Coverage)
     */
    public int getTotalCoverage() {
        int count = 0;
        for (int i = 0; i < MAP_SIZE; i++) {
            if (globalBits[i] != 0) {
                count++;
            }
        }
        return count;
    }
    /**
     * [补全] 统计这次运行覆盖了多少条边 (Current Execution Coverage)
     * 用于评估单个种子的质量
     */
    public int countCoverage() {
        int count = 0;
        for (int i = 0; i < MAP_SIZE; i++) {
            if (traceBits.get(i) != 0) {
                count++;
            }
        }
        return count;
    }
    /**
     * [新增] 计算执行速度 (Execs/Sec)
     */
    public double getExecutionSpeed(long totalExecs) {
        long currentTime = System.currentTimeMillis();
        double seconds = (currentTime - startTime) / 1000.0;
        if (seconds < 0.001) return 0;
        return totalExecs / seconds;
    }

    public void cleanup() {
        if (shmId >= 0) {
            LinuxLibC.INSTANCE.shmctl(shmId, IPC_RMID, null);
        }
    }
}
//package edu.nju.isefuzz;
//
//import com.sun.jna.Native;
//import com.sun.jna.Pointer;
//import com.sun.jna.platform.unix.LibC;
//
//import java.nio.ByteBuffer;
//
///**
// * 监控组件：负责管理共享内存 (Shared Memory)
// * 对应 AFL++ 中的 afl-fuzz-bitmap.c 逻辑
// */
//public class CoverageMonitor {
//
//    // AFL 标准 Bitmap 大小为 64KB
//    public static final int MAP_SIZE = 65536;
//
//    // Linux IPC 常量 (参考 /usr/include/linux/ipc.h)
//    private static final int IPC_CREAT = 01000;
//    private static final int IPC_EXCL = 02000;
//    private static final int IPC_RMID = 0;
//    private static final int SHM_R = 0400;
//    private static final int SHM_W = 0200;
//
//    private int shmId;
//    private Pointer shmPtr;
//    private ByteBuffer traceBits; // Java 能直接读写的字节缓冲区
//
//    // 扩展 LibC 接口以支持 shmget/shmat
//    // 因为 JNA 标准库里可能没有这几个底层函数
//    public interface LinuxLibC extends LibC {
//        LinuxLibC INSTANCE = Native.load("c", LinuxLibC.class);
//        int shmget(int key, int size, int shmflg);
//        Pointer shmat(int shmid, Pointer shmaddr, int shmflg);
//        int shmctl(int shmid, int cmd, Pointer buf);
//    }
//
//    /**
//     * 初始化：向操作系统申请一块共享内存
//     */
//    public void setup() {
//        // 1. 申请共享内存 (IPC_PRIVATE = 0)
//        // 权限 0600 (读写)
//        this.shmId = LinuxLibC.INSTANCE.shmget(0, MAP_SIZE, IPC_CREAT | IPC_EXCL | 0600);
//
//        if (this.shmId < 0) {
//            throw new RuntimeException("Failed to allocate shared memory (shmget failed)");
//        }
//
//        // 2. 挂载到当前进程地址空间
//        this.shmPtr = LinuxLibC.INSTANCE.shmat(this.shmId, null, 0);
//        if (Pointer.nativeValue(shmPtr) == -1) {
//            throw new RuntimeException("Failed to attach shared memory (shmat failed)");
//        }
//
//        // 3. 将 Pointer 转换为 Java ByteBuffer 方便读取
//        this.traceBits = shmPtr.getByteBuffer(0, MAP_SIZE);
//
//        System.out.println("[Monitor] Shared Memory ID: " + shmId);
//    }
//
//    /**
//     * 获取用于传递给 C 程序的 ID 字符串
//     */
//    public String getShmIdString() {
//        return String.valueOf(shmId);
//    }
//
//    /**
//     * 每次运行前必须清空 Bitmap，否则覆盖率会累积
//     */
//    public void clear() {
//        // 既然是直接内存，用 put(0) 可能比较慢，但为了简单先这样写
//        // 更快的方法是用 Unsafe 或者批量 put
//        for (int i = 0; i < MAP_SIZE; i++) {
//            traceBits.put(i, (byte) 0);
//        }
//    }
//
//    /**
//     * 统计这次运行覆盖了多少条边 (Edge Coverage)
//     */
//    public int countCoverage() {
//        int count = 0;
//        for (int i = 0; i < MAP_SIZE; i++) {
//            if (traceBits.get(i) != 0) {
//                count++;
//            }
//        }
//        return count;
//    }
//
//    /**
//     * 清理资源 (程序退出时调用)
//     */
//    public void cleanup() {
//        if (shmId >= 0) {
//            LinuxLibC.INSTANCE.shmctl(shmId, IPC_RMID, null);
//        }
//    }
//}