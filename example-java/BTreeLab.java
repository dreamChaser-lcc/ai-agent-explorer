import java.util.ArrayList;
import java.util.List;

/**
 * BTreeLab —— B 树（2-3 树教学版：节点最多 2 key，满 3 即分裂）
 * 数据分布在【所有层】——内部节点也存 key（对照 08 课）
 */
class BTreeLab {

    static class BNode {
        final List<Integer> keys = new ArrayList<>();
        final List<BNode> children = new ArrayList<>();

        boolean leaf() {
            return children.isEmpty();
        }
    }

    record Split(int upKey, BNode left, BNode right) {
    }

    private BNode root = new BNode();

    public void insert(int key) {
        Split up = insert(root, key);
        if (up != null) {
            BNode nr = new BNode();
            nr.keys.add(up.upKey());
            nr.children.add(up.left());
            nr.children.add(up.right());
            root = nr;
        }
    }

    private Split insert(BNode n, int key) {
        int i = 0;
        while (i < n.keys.size() && key > n.keys.get(i)) {
            i++;
        }
        if (n.leaf()) {
            n.keys.add(i, key);
        } else {
            Split up = insert(n.children.get(i), key);
            if (up == null) {
                return null;
            }
            n.keys.add(i, up.upKey());
            n.children.set(i, up.left());
            n.children.add(i + 1, up.right());
        }
        return (n.keys.size() > 2) ? split(n) : null;
    }

    private Split split(BNode n) {
        int up = n.keys.remove(1);
        BNode right = new BNode();
        right.keys.add(n.keys.remove(1));
        if (!n.leaf()) {
            right.children.add(n.children.remove(2));
            right.children.add(n.children.remove(2));
        }
        return new Split(up, n, right);
    }

    public void print() {
        print(root, 0);
    }

    private void print(BNode n, int depth) {
        System.out.println("    ".repeat(depth) + n.keys);
        for (BNode c : n.children) {
            print(c, depth + 1);
        }
    }

    public List<Integer> inOrder() {
        List<Integer> out = new ArrayList<>();
        inOrder(root, out);
        return out;
    }

    private void inOrder(BNode n, List<Integer> out) {
        for (int i = 0; i < n.keys.size(); i++) {
            if (i < n.children.size()) {
                inOrder(n.children.get(i), out);
            }
            out.add(n.keys.get(i));
        }
        if (n.children.size() > n.keys.size()) {
            inOrder(n.children.get(n.keys.size()), out);
        }
    }

    // 带计数的查询：节点内线性比较 + 走子树，全部累加
    public int searchCount(int key) {
        int comparisons = 0;
        BNode n = root;
        while (true) {
            int i = 0;
            while (i < n.keys.size() && key > n.keys.get(i)) {
                comparisons++;
                i++;
            }
            if (i < n.keys.size()) {
                comparisons++;
                if (n.keys.get(i) == key) {
                    return comparisons;
                }
            }
            if (n.leaf()) {
                return comparisons;
            }
            n = n.children.get(i);
        }
    }
}