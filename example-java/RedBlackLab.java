import java.util.ArrayList;
import java.util.List;

/**
 * RedBlackLab —— 红黑树（对照 docs/数据结构实战/06 课）
 * 从 exampleJavaGrammar.java 抽出（TreeLab benchmark 引用）
 */
class RedBlackLab {

    enum Color { RED, BLACK }

    static class Node {
        final int key;
        Node left, right, parent;
        Color color;

        Node(int key, Color color) {
            this.key = key;
            this.color = color;
        }

        @Override
        public String toString() {
            return key + (color == Color.RED ? "(R)" : "(B)");
        }
    }

    private Node root;

    // R1：新节点一律红
    public void insert(int key) {
        Node parent = null, cur = root;
        while (cur != null) {
            parent = cur;
            cur = (key < cur.key) ? cur.left : cur.right;
        }
        Node x = new Node(key, Color.RED);
        x.parent = parent;
        if (parent == null) root = x;
        else if (key < parent.key) parent.left = x;
        else parent.right = x;
        fixAfterInsertion(x);
    }

    // 修复三情景（看叔叔）：红叔变色 / 黑叔拐弯掰直 / 黑叔直线旋爷收工
    private void fixAfterInsertion(Node x) {
        while (x != root && x.parent.color == Color.RED) {
            Node parent = x.parent;
            Node grand = parent.parent;
            boolean pIsLeft = (parent == grand.left);
            Node uncle = pIsLeft ? grand.right : grand.left;

            if (uncle != null && uncle.color == Color.RED) {
                parent.color = Color.BLACK;
                uncle.color = Color.BLACK;
                grand.color = Color.RED;
                x = grand;
            } else {
                if (pIsLeft && x == parent.right) {
                    x = parent;
                    rotateLeft(x);
                } else if (!pIsLeft && x == parent.left) {
                    x = parent;
                    rotateRight(x);
                }
                parent = x.parent;
                grand = parent.parent;
                parent.color = Color.BLACK;
                grand.color = Color.RED;
                if (parent == grand.left) rotateRight(grand);
                else rotateLeft(grand);
            }
        }
        root.color = Color.BLACK;
    }

    private void rotateLeft(Node x) {
        Node y = x.right;
        x.right = y.left;
        if (y.left != null) y.left.parent = x;
        y.parent = x.parent;
        if (x.parent == null) root = y;
        else if (x == x.parent.left) x.parent.left = y;
        else x.parent.right = y;
        y.left = x;
        x.parent = y;
    }

    private void rotateRight(Node x) {
        Node y = x.left;
        x.left = y.right;
        if (y.right != null) y.right.parent = x;
        y.parent = x.parent;
        if (x.parent == null) root = y;
        else if (x == x.parent.right) x.parent.right = y;
        else x.parent.left = y;
        y.right = x;
        x.parent = y;
    }

    public void print() {
        if (root == null) { System.out.println("(empty)"); return; }
        System.out.println(root + "  <- root");
        printChildren(root, "");
    }

    private void printChildren(Node n, String prefix) {
        if (n.left != null) {
            System.out.println(prefix + "|-L- " + n.left);
            if (n.left.left != null || n.left.right != null) printChildren(n.left, prefix + "|   ");
        }
        if (n.right != null) {
            System.out.println(prefix + "|-R- " + n.right);
            if (n.right.left != null || n.right.right != null) printChildren(n.right, prefix + "    ");
        }
    }

    public void checkInvariants() {
        if (root != null && root.color != Color.BLACK)
            throw new IllegalStateException("R2: 根必须黑");
        List<Integer> counts = new ArrayList<>();
        collectBlackCounts(root, 0, counts);
        for (int i = 1; i < counts.size(); i++)
            if (!counts.get(i).equals(counts.get(0)))
                throw new IllegalStateException("R5: 黑高不一致 " + counts);
        System.out.println("[OK] 红黑树不变量：根黑 / 红不叠 / 黑高一致（每条路径 "
                + counts.get(0) + " 个黑）");
    }

    // 红不计数的字面实现：红不加、黑 +1、NIL +1
    private void collectBlackCounts(Node n, int blackSoFar, List<Integer> out) {
        if (n == null) { out.add(blackSoFar + 1); return; }
        if (n.color == Color.RED) {
            if ((n.left != null && n.left.color == Color.RED)
                    || (n.right != null && n.right.color == Color.RED))
                throw new IllegalStateException("R4: 红红相连 @" + n.key);
        } else {
            blackSoFar++;
        }
        collectBlackCounts(n.left, blackSoFar, out);
        collectBlackCounts(n.right, blackSoFar, out);
    }

    // 带计数的查询（TreeLab benchmark 用）
    public int searchCount(int key) {
        int comparisons = 0;
        Node cur = root;
        while (cur != null) {
            comparisons++;
            if (key == cur.key) return comparisons;
            cur = (key < cur.key) ? cur.left : cur.right;
        }
        return comparisons;
    }

    public static void main(String[] args) {
        RedBlackLab tree = new RedBlackLab();
        for (int i = 1; i <= 7; i++) tree.insert(i);
        tree.print();
        tree.checkInvariants();
    }
}