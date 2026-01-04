package edu.nju.isefuzz;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 变异组件 (Mutation Component)
 * 对应 AFL 的 afl-fuzz-one.c 中的 fuzz_one 逻辑
 * 实现了 Bitflip, Arithmetic, Interesting, Havoc, Splice 算子
 */
public class Mutator {

    private final Random random = new Random();

    // --- AFL 标准 "Interesting Values" (参考 afl-fuzz.h / config.h) ---
    private static final byte[] INTERESTING_8 = {
            (byte) -128, (byte) -1, 0, 1, 16, 32, 64, 100, 127
    };
    private static final short[] INTERESTING_16 = {
            -32768, -129, 128, 255, 256, 512, 1000, 1024, 4096, 32767
    };
    private static final int[] INTERESTING_32 = {
            -2147483648, -100663046, -32769, 32768, 65535, 65536, 100663045, 2147483647
    };

    /**
     * [阶段 1-3] 确定性变异生成器
     * 会生成大量变异体，用于彻底扫描种子
     */
    public List<byte[]> generateDeterministic(byte[] seed) {
        List<byte[]> mutants = new ArrayList<>();

        // 1. Bitflip (翻转比特位)
        // 简化版：只做 bitflip 1/1 (翻转每一个 bit)
        for (int i = 0; i < seed.length * 8; i++) {
            byte[] mutant = seed.clone();
            int byteIndex = i / 8;
            int bitIndex = i % 8;
            mutant[byteIndex] ^= (1 << bitIndex);
            mutants.add(mutant);
        }

        // 2. Arithmetic (算术加减)
        // 简化版：对每个字节进行 +/- 1 到 5 的加减
        for (int i = 0; i < seed.length; i++) {
            for (int j = 1; j <= 5; j++) {
                // +j
                byte[] plus = seed.clone();
                plus[i] = (byte) (plus[i] + j);
                mutants.add(plus);

                // -j
                byte[] minus = seed.clone();
                minus[i] = (byte) (minus[i] - j);
                mutants.add(minus);
            }
        }

        // 3. Interesting Values (特殊值替换)
        // 8-bit 替换
        for (int i = 0; i < seed.length; i++) {
            for (byte interest : INTERESTING_8) {
                byte[] mutant = seed.clone();
                mutant[i] = interest;
                mutants.add(mutant);
            }
        }

        // 略：为了代码简洁，这里省略了 16-bit 和 32-bit 的滑动替换
        // 在完整项目中应该加上

        return mutants;
    }

    /**
     * [阶段 4] Havoc (大破坏)
     * 随机组合多种变异算子，这是发现 Crash 的主力
     * @param seed 原始种子
     * @param power 能量调度 (变异多少轮)
     */
    public List<byte[]> generateHavoc(byte[] seed, int power) {
        List<byte[]> mutants = new ArrayList<>();

        for (int i = 0; i < power; i++) {
            byte[] mutant = seed.clone();
            // 对每个种子随机堆叠 1-8 个变异操作
            int stackCount = 1 + random.nextInt(8);

            for (int j = 0; j < stackCount; j++) {
                int action = random.nextInt(6); // 随机选动作

                switch (action) {
                    case 0: // 随机翻转一个 bit
                        if (mutant.length > 0) {
                            int pos = random.nextInt(mutant.length * 8);
                            mutant[pos / 8] ^= (1 << (pos % 8));
                        }
                        break;
                    case 1: // 随机替换字节为 "Interesting Value"
                        if (mutant.length > 0) {
                            int pos = random.nextInt(mutant.length);
                            mutant[pos] = INTERESTING_8[random.nextInt(INTERESTING_8.length)];
                        }
                        break;
                    case 2: // 随机算术加减
                        if (mutant.length > 0) {
                            int pos = random.nextInt(mutant.length);
                            int val = 1 + random.nextInt(35);
                            if (random.nextBoolean()) mutant[pos] += (byte) val;
                            else mutant[pos] -= (byte) val;
                        }
                        break;
                    case 3: // 随机删除一段字节 (Block Deletion)
                        if (mutant.length > 2) {
                            int delLen = 1 + random.nextInt(mutant.length - 1);
                            int delPos = random.nextInt(mutant.length - delLen);
                            mutant = deleteBytes(mutant, delPos, delLen);
                        }
                        break;
                    case 4: // 随机插入一段字节 (Block Insertion)
                        if (mutant.length < 10240) { // 限制最大长度防止爆炸
                            byte[] chunk = new byte[1 + random.nextInt(10)];
                            random.nextBytes(chunk); // 随机数据，也可以是克隆的一段
                            int insPos = random.nextInt(mutant.length + 1);
                            mutant = insertBytes(mutant, insPos, chunk);
                        }
                        break;
                    case 5: // 随机字节打乱 (Shuffle) - 简单模拟
                        if (mutant.length > 0) {
                            int pos = random.nextInt(mutant.length);
                            mutant[pos] = (byte) random.nextInt(256);
                        }
                        break;
                }
            }
            mutants.add(mutant);
        }
        return mutants;
    }

    /**
     * [阶段 5] Splice (拼接)
     * 将两个种子切开并拼接在一起
     */
    public byte[] splice(byte[] seed1, byte[] seed2) {
        if (seed1.length < 2 || seed2.length < 2) return seed1;

        // 随机切分点
        int split1 = 1 + random.nextInt(seed1.length - 1);
        int split2 = 1 + random.nextInt(seed2.length - 1);

        // 拼接：seed1的前半部分 + seed2的后半部分
        // 注意：AFL 的 splice 通常保持总长度不变或取两者的子集
        // 这里为了简单，我们取 seed1[0...split1] + seed2[split2...end]

        byte[] result = new byte[split1 + (seed2.length - split2)];
        System.arraycopy(seed1, 0, result, 0, split1);
        System.arraycopy(seed2, split2, result, split1, seed2.length - split2);

        return result;
    }

    // --- 辅助方法 ---

    private byte[] deleteBytes(byte[] original, int pos, int len) {
        byte[] result = new byte[original.length - len];
        System.arraycopy(original, 0, result, 0, pos);
        System.arraycopy(original, pos + len, result, pos, original.length - (pos + len));
        return result;
    }

    private byte[] insertBytes(byte[] original, int pos, byte[] chunk) {
        byte[] result = new byte[original.length + chunk.length];
        System.arraycopy(original, 0, result, 0, pos);
        System.arraycopy(chunk, 0, result, pos, chunk.length);
        System.arraycopy(original, pos, result, pos + chunk.length, original.length - pos);
        return result;
    }
}