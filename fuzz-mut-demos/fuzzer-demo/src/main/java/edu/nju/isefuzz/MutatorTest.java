package edu.nju.isefuzz;

import java.util.List;
import java.nio.charset.StandardCharsets;

public class MutatorTest {
    public static void main(String[] args) {
        Mutator mutator = new Mutator();
        String seedStr = "hello";
        byte[] seed = seedStr.getBytes(StandardCharsets.UTF_8);

        System.out.println("Original: " + seedStr);

        // 1. 测试确定性变异
        System.out.println("\n--- Deterministic Mutation (First 5 samples) ---");
        List<byte[]> detMutants = mutator.generateDeterministic(seed);
        System.out.println("Total deterministic mutants: " + detMutants.size());
        for (int i = 0; i < Math.min(5, detMutants.size()); i++) {
            printBytes(detMutants.get(i));
        }

        // 2. 测试 Havoc 变异
        System.out.println("\n--- Havoc Mutation (5 rounds) ---");
        List<byte[]> havocMutants = mutator.generateHavoc(seed, 5);
        for (byte[] m : havocMutants) {
            printBytes(m);
        }

        // 3. 测试 Splice
        System.out.println("\n--- Splice Mutation ---");
        byte[] seed2 = "world".getBytes(StandardCharsets.UTF_8);
        byte[] spliced = mutator.splice(seed, seed2);
        printBytes(spliced);
    }

    private static void printBytes(byte[] data) {
        // 尝试转成字符串打印，如果不可见字符则打印 hex
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            if (b >= 32 && b <= 126) {
                sb.append((char) b);
            } else {
                sb.append(String.format("\\x%02x", b));
            }
        }
        System.out.println(sb.toString());
    }
}