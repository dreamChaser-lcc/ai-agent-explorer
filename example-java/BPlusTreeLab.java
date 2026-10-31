import java.util.ArrayList;
import java.util.List;

/**
 * BPlusTreeLab —— B+ 树（2-3 教学版）：数据全在叶子 + 叶子横向链表
 * 与 B 树的两个区别（08 课）：
 *   a. 叶分裂上提的是【复制】（upKey 同时保留在右叶开头）
 *   b. 叶子之间 next 链表串联 → 范围查询顺序扫（不回树）
 */
class BPlusTreeLab {

    static class PNode {
        final List<Integer> keys = new ArrayList<>();
        final List<PNode> children = new ArrayList<>();
        PNode next;   // 叶子链表（B+ 独有）

        boolean leaf() {
            return children.isEmpty();
        }
    }

    record PSplit(int upKey, PNode left, PNode right) {
    }

    private PNode root = new PNode();

    public void insert(int key) {
        PSplit up = insert(root, key);
        if (up != null) {
            PNode nr = new PNode();
            nr.keys.add(up.upKey());
            nr.children.add(up.left());
            nr.children.add(up.right());
            root = nr;
        }
    }

    private PSplit insert(PNode n, int key) {
        int i = 0;
        while (i < n.keys.size() && key >= n.keys.get(i)) {
            i++;
        }
        if (n.leaf()) {
            n.keys.add(i, key);
        } else {
            PSplit up = insert(n.children.get(i), key);
            if (up == null) {
                return null;
            }
            n.keys.add(i, up.upKey());
            n.children.add(i + 1, up.right());
        }
        return (n.keys.size() > 2) ? split(n) : null;
    }

    private PSplit split(PNode n) {
        if (n.leaf()) {
            // 叶分裂：上提【复制】——upKey 同时保留在右叶开头
            int up = n.keys.get(1);
            PNode right = new PNode();
            right.keys.addAll(n.keys.subList(1, n.keys.size()));
            n.keys.subList(1, n.keys.size()).clear();
            right.next = n.next;
            n.next = right;
            return new PSplit(up, n, right);
        }
        // 内部分裂：上提【移动】（内部只是路标，key 不重复）
        int up = n.keys.remove(1);
        PNode right = new PNode();
        right.keys.add(n.keys.remove(1));
        right.children.add(n.children.remove(2));
        right.children.add(n.children.remove(2));
        return new PSplit(up, n, right);
    }

    public boolean contains(int key) {
        PNode n = root;
        while (!n.leaf()) {
            int i = 0;
            while (i < n.keys.size() && key >= n.keys.get(i)) {
                i++;
            }
            n = n.children.get(i);
        }
        return n.keys.contains(key);
    }

    // 范围查询：定位到叶子后沿 next 链表顺序扫（不回树）——B+ 杀手锏
    public java.util.List<Integer> range(int lo, int hi) {
        PNode n = root;
        while (!n.leaf()) {
            int i = 0;
            while (i < n.keys.size() && lo >= n.keys.get(i)) {
                i++;
            }
            n = n.children.get(i);
        }
        java.util.List<Integer> out = new java.util.ArrayList<>();
        while (n != null) {
            for (int k : n.keys) {
                if (k > hi) {
                    return out;
                }
                if (k >= lo) {
                    out.add(k);
                }
            }
            n = n.next;
        }
        return out;
    }

    public void print() {
        print(root, 0);
    }

    private void print(PNode n, int depth) {
        String tag = n.leaf() ? "LEAF  " : "INDEX ";
        System.out.println("    ".repeat(depth) + tag + n.keys);
        for (PNode c : n.children) {
            print(c, depth + 1);
        }
    }

    public void printLeaves() {
        PNode n = root;
        while (!n.leaf()) {
            n = n.children.get(0);
        }
        System.out.print("叶子链表: ");
        while (n != null) {
            System.out.print(n.keys + " -> ");
            n = n.next;
        }
        System.out.println("null");
    }

    // 带计数的查询：路标比较 + 叶内线性找
    public int searchCount(int key) {
        int comparisons = 0;
        PNode n = root;
        while (!n.leaf()) {
            int i = 0;
            while (i < n.keys.size() && key >= n.keys.get(i)) {
                comparisons++;
                i++;
            }
            if (i < n.keys.size() && key < n.keys.get(i)) {
                comparisons++;
            }
            n = n.children.get(i);
        }
        for (int k : n.keys) {
            comparisons++;
            if (k == key) {
                return comparisons;
            }
        }
        return comparisons;
    }
}