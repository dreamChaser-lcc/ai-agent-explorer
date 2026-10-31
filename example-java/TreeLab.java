import java.util.ArrayList;
import java.util.List;

/**
 * TreeLab —— 树的裸跑实验场（javac/java 直接跑，无 Spring）
 *
 * 实现类（本目录独立文件）：BTreeLab / BPlusTreeLab / NaiveBstLab
 * 红黑树在 exampleJavaGrammar.java 的 RedBlackLab
 *
 * 运行：javac -encoding utf-8 TreeLab.java BTreeLab.java BPlusTreeLab.java NaiveBstLab.java exampleJavaGrammar.java
 *       然后 java TreeLab
 */
public class TreeLab {
    public static void main(String[] args) {
        System.out.println("=== 1 B 树（2-3 树）：插入 1~10 ===");
        BTreeLab bt = new BTreeLab();
        for (int i = 1; i <= 10; i++) {
            bt.insert(i);
        }
        bt.print();
        System.out.println("中序遍历（应有序）: " + bt.inOrder());

        System.out.println();
        System.out.println("=== 2 B+ 树（2-3）：插入 1~10 + 叶子链 + 范围查询 ===");
        BPlusTreeLab bp = new BPlusTreeLab();
        for (int i = 1; i <= 10; i++) {
            bp.insert(i);
        }
        bp.print();
        bp.printLeaves();
        System.out.println("范围查询 [4,8] = " + bp.range(4, 8) + "   <- 沿叶子链表扫，不回树");
        System.out.println("范围查询 [1,10] = " + bp.range(1, 10) + "   <- 全域有序 = 隐式自检");
        System.out.println();
        benchmarkQuery();
    }

    /** 查询性能对比：10 万数据 × 1 万随机查询（一半命中一半未命中） */
    private static void benchmarkQuery() {
        final int N = 100000;
        final int Q = 10000;
        java.util.Random rnd = new java.util.Random(42);

        System.out.println("=== 3 查询性能对比（建库 " + N + " 条）===");
        long t0 = System.nanoTime();
        BTreeLab bt = new BTreeLab();
        for (int i = 1; i <= N; i++) {
            bt.insert(i);
        }
        long insBt = System.nanoTime() - t0;

        t0 = System.nanoTime();
        BPlusTreeLab bp = new BPlusTreeLab();
        for (int i = 1; i <= N; i++) {
            bp.insert(i);
        }
        long insBp = System.nanoTime() - t0;

        t0 = System.nanoTime();
        RedBlackLab rb = new RedBlackLab();
        for (int i = 1; i <= N; i++) {
            rb.insert(i);
        }
        long insRb = System.nanoTime() - t0;

        t0 = System.nanoTime();
        NaiveBstLab nb = new NaiveBstLab();
        for (int i = 1; i <= N; i++) {
            nb.insert(i);
        }
        long insNaive = System.nanoTime() - t0;

        List<Integer> list = new ArrayList<>();
        for (int i = 1; i <= N; i++) {
            list.add(i);
        }

        int[] queries = new int[Q];
        for (int i = 0; i < Q; i++) {
            queries[i] = (i % 2 == 0) ? 1 + rnd.nextInt(N) : N + 1 + rnd.nextInt(N);
        }
        for (int i = 0; i < 500; i++) {
            bt.searchCount(queries[i % Q]);
            bp.searchCount(queries[i % Q]);
            rb.searchCount(queries[i % Q]);
            nb.searchCount(queries[i % Q]);
        }

        long begin;
        long cmp;

        begin = System.nanoTime();
        cmp = 0;
        for (int q : queries) {
            cmp += bt.searchCount(q);
        }
        long tBt = System.nanoTime() - begin;
        long btAvg = cmp / Q;

        begin = System.nanoTime();
        cmp = 0;
        for (int q : queries) {
            cmp += bp.searchCount(q);
        }
        long tBp = System.nanoTime() - begin;
        long bpAvg = cmp / Q;

        begin = System.nanoTime();
        cmp = 0;
        for (int q : queries) {
            cmp += rb.searchCount(q);
        }
        long tRb = System.nanoTime() - begin;
        long rbAvg = cmp / Q;

        begin = System.nanoTime();
        cmp = 0;
        for (int q : queries) {
            cmp += nb.searchCount(q);
        }
        long tNb = System.nanoTime() - begin;
        long nbAvg = cmp / Q;

        begin = System.nanoTime();
        for (int q : queries) {
            list.contains(q);
        }
        long tList = System.nanoTime() - begin;
        long listAvg = N / 2;

        System.out.println();
        System.out.println("建库耗时(ms)  B树=" + insBt / 1000000
                + "  B+树=" + insBp / 1000000
                + "  红黑=" + insRb / 1000000
                + "  朴素BST=" + insNaive / 1000000);
        System.out.println();
        System.out.println("=== 查询 " + Q + " 次（一半命中一半未命中）===");
        System.out.println("结构                    平均比较次数    平均耗时(ns)");
        System.out.println("线性 ArrayList         " + String.format("%10d %12d", listAvg, tList / Q));
        System.out.println("朴素BST(退化链表)      " + String.format("%10d %12d", nbAvg, tNb / Q));
        System.out.println("红黑树                 " + String.format("%10d %12d", rbAvg, tRb / Q));
        System.out.println("B树(2-3)               " + String.format("%10d %12d", btAvg, tBt / Q));
        System.out.println("B+树(2-3)              " + String.format("%10d %12d", bpAvg, tBp / Q));
        System.out.println("(checksum=" + cmp + ")");
    }
}