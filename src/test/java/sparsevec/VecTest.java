package sparsevec;

import java.util.ArrayList;
import java.util.List;

/**
 * sparsevec 的既有用例（13 个），**只覆盖 `DenseVec`**。
 *
 * <p>不依赖 JUnit：静态方法加 {@code main} 聚合自跑，全部通过时打印 {@code 13/13 passed}
 * 并以退出码 0 结束；有失败时打印失败清单并以退出码 1 结束。
 *
 * <p>这 13 条是「既有行为」的基线（见 README「对外契约」第 1 条），**一条都不许删或改**。
 */
public final class VecTest {

    private interface Body {
        /** 返回 null 表示通过，否则返回失败原因。 */
        String run();
    }

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        run("construct-size", VecTest::testConstructSize);
        run("of-values", VecTest::testOfValues);
        run("get-set", VecTest::testGetSet);
        run("get-out-of-range", VecTest::testGetOutOfRange);
        run("set-out-of-range", VecTest::testSetOutOfRange);
        run("negative-size", VecTest::testNegativeSize);
        run("dot-basic", VecTest::testDotBasic);
        run("dot-length-mismatch", VecTest::testDotLengthMismatch);
        run("plus-basic", VecTest::testPlusBasic);
        run("plus-length-mismatch", VecTest::testPlusLengthMismatch);
        run("equals", VecTest::testEquals);
        run("hashCode", VecTest::testHashCode);
        run("toString", VecTest::testToString);

        System.out.println(passed + "/13 passed");
        if (!failures.isEmpty()) {
            for (String f : failures) {
                System.out.println("FAIL " + f);
            }
            System.exit(1);
        }
    }

    private static void run(String name, Body body) {
        try {
            String err = body.run();
            if (err == null) {
                passed++;
            } else {
                failures.add(name + "：" + err);
            }
        } catch (Throwable t) {
            failures.add(name + "：抛出 " + t);
        }
    }

    // ---------------------------------------------------------------- 构造与读写

    private static String testConstructSize() {
        return expect("new DenseVec(4).size()", String.valueOf(new DenseVec(4).size()), "4");
    }

    private static String testOfValues() {
        DenseVec d = DenseVec.of(1.5, 2.5, 3.5);
        String e = expect("size", String.valueOf(d.size()), "3");
        if (e != null) {
            return e;
        }
        e = expect("get(0)", String.valueOf(d.get(0)), "1.5");
        if (e != null) {
            return e;
        }
        return expect("get(2)", String.valueOf(d.get(2)), "3.5");
    }

    private static String testGetSet() {
        DenseVec d = new DenseVec(3);
        String e = expect("初值 get(1)", String.valueOf(d.get(1)), "0.0");
        if (e != null) {
            return e;
        }
        d.set(1, 7.25);
        return expect("set 后 get(1)", String.valueOf(d.get(1)), "7.25");
    }

    private static String testGetOutOfRange() {
        DenseVec d = DenseVec.of(1.0, 2.0);
        String e = expectThrow("get(-1)", () -> d.get(-1));
        if (e != null) {
            return e;
        }
        return expectThrow("get(2)", () -> d.get(2));
    }

    private static String testSetOutOfRange() {
        DenseVec d = DenseVec.of(1.0, 2.0);
        return expectThrow("set(5, 1.0)", () -> d.set(5, 1.0));
    }

    private static String testNegativeSize() {
        return expectThrow("new DenseVec(-1)", () -> new DenseVec(-1));
    }

    // ---------------------------------------------------------------- 运算

    private static String testDotBasic() {
        DenseVec a = DenseVec.of(1.0, 3.0, -2.0);
        DenseVec b = DenseVec.of(2.0, 4.0, 5.0);
        return expect("dot", String.valueOf(a.dot(b)), "4.0");
    }

    private static String testDotLengthMismatch() {
        DenseVec a = DenseVec.of(1.0, 2.0);
        DenseVec b = DenseVec.of(1.0, 2.0, 3.0);
        return expectThrow("dot 长度不一致", () -> a.dot(b));
    }

    private static String testPlusBasic() {
        DenseVec a = DenseVec.of(1.0, 2.0, 3.0);
        DenseVec b = DenseVec.of(10.0, -2.0, 5.0);
        DenseVec r = a.plus(b);
        String e = expect("size", String.valueOf(r.size()), "3");
        if (e != null) {
            return e;
        }
        e = expect("get(0)", String.valueOf(r.get(0)), "11.0");
        if (e != null) {
            return e;
        }
        e = expect("get(1)", String.valueOf(r.get(1)), "0.0");
        if (e != null) {
            return e;
        }
        return expect("get(2)", String.valueOf(r.get(2)), "8.0");
    }

    private static String testPlusLengthMismatch() {
        DenseVec a = DenseVec.of(1.0, 2.0);
        DenseVec b = DenseVec.of(1.0);
        return expectThrow("plus 长度不一致", () -> a.plus(b));
    }

    // ---------------------------------------------------------------- 相等与文本

    private static String testEquals() {
        DenseVec a = DenseVec.of(1.0, 2.0);
        DenseVec b = DenseVec.of(1.0, 2.0);
        DenseVec c = DenseVec.of(1.0, 2.5);
        if (!a.equals(b)) {
            return "期望=[1,2] 等于 [1,2] 实际=false";
        }
        if (a.equals(c)) {
            return "期望=[1,2] 不等于 [1,2.5] 实际=true";
        }
        return a.equals("x") ? "期望=与非 DenseVec 比较为 false 实际=true" : null;
    }

    private static String testHashCode() {
        DenseVec a = DenseVec.of(1.0, 2.0);
        DenseVec b = DenseVec.of(1.0, 2.0);
        if (a.hashCode() != b.hashCode()) {
            return "期望=相等的向量 hashCode 相同 实际=" + a.hashCode() + " / " + b.hashCode();
        }
        return null;
    }

    private static String testToString() {
        return expect("[1.0, 2.0]", DenseVec.of(1.0, 2.0).toString(), "DenseVec(size=2, [1.0, 2.0])");
    }

    // ---------------------------------------------------------------- 工具

    private static String expect(String label, String actual, String wanted) {
        if (wanted.equals(actual)) {
            return null;
        }
        return label + " 期望=" + wanted + " 实际=" + actual;
    }

    private static String expectThrow(String label, Runnable body) {
        try {
            body.run();
            return label + " 期望=抛 VecException 实际=正常返回";
        } catch (VecException expected) {
            return null;
        } catch (Throwable t) {
            return label + " 期望=VecException 实际=" + t;
        }
    }

    private VecTest() {
    }
}
