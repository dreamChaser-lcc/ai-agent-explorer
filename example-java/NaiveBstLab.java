/**
 * NaiveBstLab —— 朴素 BST（无平衡约束）——对照组
 * 递增插入 10 万条会退化成链表（树高 = 10 万），查询 O(n)
 * 迭代实现（递归会爆栈：深 10 万的递归）
 */
class NaiveBstLab {

    static class NNode {
        final int key;
        NNode left, right;

        NNode(int key) {
            this.key = key;
        }
    }

    private NNode root;

    public void insert(int key) {
        NNode n = new NNode(key);
        if (root == null) {
            root = n;
            return;
        }
        NNode cur = root;
        while (true) {
            if (key == cur.key) {
                return;
            }
            if (key < cur.key) {
                if (cur.left == null) {
                    cur.left = n;
                    return;
                }
                cur = cur.left;
            } else {
                if (cur.right == null) {
                    cur.right = n;
                    return;
                }
                cur = cur.right;
            }
        }
    }

    // 带计数的查询：走多少节点 = 多少次比较（退化时 = 链长）
    public int searchCount(int key) {
        int comparisons = 0;
        NNode cur = root;
        while (cur != null) {
            comparisons++;
            if (key == cur.key) {
                return comparisons;
            }
            cur = (key < cur.key) ? cur.left : cur.right;
        }
        return comparisons;
    }
}