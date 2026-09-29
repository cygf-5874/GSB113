import sparsevec.DenseVec;
import sparsevec.SparseVec;
import sparsevec.VecException;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * sparsevec 的固定验收程序（勿改）。
 *
 * <p>9 个场景覆盖 README「对外契约」的 9 条：
 * dense 2 / build 2 / arith 3 / slice 1 / scale 1。
 * 每个场景都在**独立 JVM 子进程**里跑（{@link ProcessBuilder}），带看门狗，超时判不通过；
 * 某个场景失败不会遮蔽其余场景（失败不早退）。判据全部确定性：没有随机源、没有墙钟、
 * 没有性能门槛（scale 组走 README 契约 9 的下标访问计数，与机器速度无关）。
 *
 * <p>用法：java -cp out;out-check Checker [-list] [--only &lt;组名&gt;[,&lt;组名&gt;...]]
 */
public final class Checker {

    /** 单场景看门狗默认值（秒）。 */
    private static final long WATCHDOG_SECONDS = 30L;

    /** 规模判据里允许的下标访问次数上限（README 契约 9）。 */
    private static final long VISITS_LIMIT = 4000L;

    private static Field visitsField;

    private interface Body {
        /** 返回 null 表示 PASS，否则返回可判定的失败原因（内含 期望=… 实际=…）。 */
        String run() throws Exception;
    }

    private static final class Scenario {
        final String name;
        final String group;
        final long watchdogSeconds;
        final Body body;

        Scenario(String name, Body body) {
            this.name = name;
            this.body = body;
            this.watchdogSeconds = WATCHDOG_SECONDS;
            int slash = name.indexOf('/');
            this.group = slash < 0 ? name : name.substring(0, slash);
        }
    }

    public static void main(String[] args) throws Exception {
        String scenarioOnly = null;
        List<String> onlyGroups = new ArrayList<>();
        boolean list = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("-list".equals(a)) {
                list = true;
            } else if ("--only".equals(a)) {
                if (i + 1 >= args.length) {
                    System.out.println("--only 需要一个组名");
                    System.exit(2);
                }
                for (String g : args[++i].split(",")) {
                    if (!g.isEmpty()) {
                        onlyGroups.add(g);
                    }
                }
            } else if ("--scenario".equals(a)) {
                if (i + 1 >= args.length) {
                    System.out.println("--scenario 需要一个场景名");
                    System.exit(2);
                }
                scenarioOnly = args[++i];
            } else {
                System.out.println("未知参数：" + a);
                System.exit(2);
            }
        }

        List<Scenario> all = scenarios();

        if (list) {
            for (Scenario s : all) {
                System.out.println(s.name);
            }
            return;
        }

        if (scenarioOnly != null) {
            System.exit(runOne(all, scenarioOnly));
        }

        List<Scenario> selected = new ArrayList<>();
        for (Scenario s : all) {
            if (onlyGroups.isEmpty() || onlyGroups.contains(s.group)) {
                selected.add(s);
            }
        }
        if (selected.isEmpty()) {
            System.out.println("没有匹配的场景（--only " + onlyGroups + "）");
            System.exit(2);
        }

        int pass = 0;
        for (Scenario s : selected) {
            String line = runInChildProcess(s);
            System.out.println(line);
            if (line.startsWith("PASS ")) {
                pass++;
            }
        }
        System.out.println("结果：通过 " + pass + "/" + selected.size());
        if (pass != selected.size()) {
            System.exit(1);
        }
    }

    // ------------------------------------------------- 子进程调度（一场景一 JVM）

    private static String runInChildProcess(Scenario s) {
        String name = s.name;
        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        ProcessBuilder pb = new ProcessBuilder(javaBin,
                "-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8",
                "-cp", classpath, "Checker", "--scenario", name);
        pb.redirectErrorStream(true);

        Process proc;
        try {
            proc = pb.start();
        } catch (Exception ex) {
            return "FAIL " + name + "  期望=能在独立 JVM 子进程里跑该场景 实际=子进程启动失败：" + ex;
        }

        StringBuilder sink = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (InputStream in = proc.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                    synchronized (sink) {
                        sink.append(chunk);
                    }
                }
            } catch (Exception ignored) {
                // 子进程被强杀时读流会失败，忽略即可
            }
        }, "checker-child-reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished;
        try {
            finished = proc.waitFor(s.watchdogSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        if (!finished) {
            proc.destroyForcibly();
            try {
                reader.join(2000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return "FAIL " + name + "  期望=场景在 " + s.watchdogSeconds + " 秒内给出 PASS/FAIL 实际="
                    + s.watchdogSeconds + " 秒看门狗超时（子进程已强杀）";
        }
        try {
            reader.join(5000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        String out;
        synchronized (sink) {
            out = sink.toString().trim();
        }
        String line = null;
        for (String l : out.split("\n")) {
            String t = l.trim();
            if (t.startsWith("PASS ") || t.startsWith("FAIL ")) {
                line = t;
            }
        }
        if (line == null) {
            String shown = out.isEmpty() ? "(无输出)" : out.replace('\n', ' ');
            return "FAIL " + name + "  期望=子进程打印 PASS/FAIL 行 实际=退出码 "
                    + (proc.isAlive() ? "未知（仍存活）" : String.valueOf(proc.exitValue())) + "，输出=" + shown;
        }
        return line;
    }

    /** 子进程模式：就地跑一个场景，打印一行 PASS/FAIL，返回退出码。 */
    private static int runOne(List<Scenario> all, String name) {
        for (Scenario s : all) {
            if (!s.name.equals(name)) {
                continue;
            }
            String err;
            try {
                err = s.body.run();
            } catch (Throwable t) {
                err = "检查过程抛异常：" + t;
            }
            if (err == null) {
                System.out.println("PASS " + s.name);
                return 0;
            }
            if (!err.contains("期望=")) {
                err = "期望=场景 " + s.name + " 通过 实际=" + err;
            }
            System.out.println("FAIL " + s.name + "  " + err);
            return 1;
        }
        System.out.println("FAIL " + name + "  期望=场景名存在于固定件 实际=未找到该场景");
        return 1;
    }

    // ------------------------------------------------------------------ 场景表

    private static List<Scenario> scenarios() {
        List<Scenario> list = new ArrayList<>();
        list.add(new Scenario("dense/basic", Checker::denseBasic));
        list.add(new Scenario("dense/arith", Checker::denseArith));
        list.add(new Scenario("build/reject-bad", Checker::buildRejectBad));
        list.add(new Scenario("build/compress", Checker::buildCompress));
        list.add(new Scenario("arith/dot", Checker::arithDot));
        list.add(new Scenario("arith/plus", Checker::arithPlus));
        list.add(new Scenario("arith/equality", Checker::arithEquality));
        list.add(new Scenario("slice/remap", Checker::sliceRemap));
        list.add(new Scenario("scale/merge-visits", Checker::scaleMergeVisits));
        list.add(new Scenario("precision/cancellation", Checker::precisionCancellation));
        list.add(new Scenario("precision/reject-non-finite", Checker::precisionRejectNonFinite));
        return list;
    }

    // ------------------------------------------------------------------ dense

    private static String denseBasic() {
        String e = eqIs("DenseVec.of(1,2,3).size()", String.valueOf(DenseVec.of(1.0, 2.0, 3.0).size()), "3");
        if (e != null) {
            return e;
        }
        DenseVec d = DenseVec.of(1.0, 2.0, 3.0);
        e = eqIs("get(1)", String.valueOf(d.get(1)), "2.0");
        if (e != null) {
            return e;
        }
        DenseVec z = new DenseVec(2);
        z.set(1, 4.5);
        e = eqIs("set/get", String.valueOf(z.get(1)), "4.5");
        if (e != null) {
            return e;
        }
        e = throwsVec("new DenseVec(-1)", () -> new DenseVec(-1));
        if (e != null) {
            return e;
        }
        e = throwsVec("get(-1)", () -> d.get(-1));
        if (e != null) {
            return e;
        }
        return throwsVec("get(3)", () -> d.get(3));
    }

    private static String denseArith() {
        DenseVec a = DenseVec.of(1.0, 3.0);
        DenseVec b = DenseVec.of(2.0, -4.0);
        String e = eqIs("dot", String.valueOf(a.dot(b)), "-10.0");
        if (e != null) {
            return e;
        }
        DenseVec r = a.plus(b);
        e = eqIs("plus.get(0)", String.valueOf(r.get(0)), "3.0");
        if (e != null) {
            return e;
        }
        e = eqIs("plus.get(1)", String.valueOf(r.get(1)), "-1.0");
        if (e != null) {
            return e;
        }
        e = throwsVec("dot 长度不一致", () -> a.dot(DenseVec.of(1.0)));
        if (e != null) {
            return e;
        }
        e = throwsVec("plus 长度不一致", () -> a.plus(DenseVec.of(1.0)));
        if (e != null) {
            return e;
        }
        if (!DenseVec.of(1.0, 2.0).equals(DenseVec.of(1.0, 2.0))) {
            return "期望=[1,2].equals([1,2]) 实际=false";
        }
        if (DenseVec.of(1.0, 2.0).hashCode() != DenseVec.of(1.0, 2.0).hashCode()) {
            return "期望=相等向量 hashCode 相同 实际=不同";
        }
        return null;
    }

    // ------------------------------------------------------------------ build

    private static String buildRejectBad() {
        String e = throwsVec("of(5,{1,1})", () -> SparseVec.of(5, new int[]{1, 1}, new double[]{1.0, 2.0}));
        if (e != null) {
            return e;
        }
        e = throwsVec("of(5,{3,1})", () -> SparseVec.of(5, new int[]{3, 1}, new double[]{1.0, 2.0}));
        if (e != null) {
            return e;
        }
        e = throwsVec("of(5,{-1})", () -> SparseVec.of(5, new int[]{-1}, new double[]{1.0}));
        if (e != null) {
            return e;
        }
        e = throwsVec("of(5,{5})", () -> SparseVec.of(5, new int[]{5}, new double[]{1.0}));
        if (e != null) {
            return e;
        }
        e = throwsVec("of(5,{1,2,3},{1,2})", () -> SparseVec.of(5, new int[]{1, 2, 3}, new double[]{1.0, 2.0}));
        if (e != null) {
            return e;
        }
        e = throwsVec("of(-1,{},{})", () -> SparseVec.of(-1, new int[0], new double[0]));
        if (e != null) {
            return e;
        }
        SparseVec empty = SparseVec.of(0, new int[0], new double[0]);
        return eqIs("of(0,{},{}) 的 nnz", String.valueOf(empty.nnz()), "0");
    }

    private static String buildCompress() {
        SparseVec v = SparseVec.of(4, new int[]{0, 1, 2, 3}, new double[]{0.0, 5.0, 0.0, 7.0});
        String e = eqIs("size", String.valueOf(v.size()), "4");
        if (e != null) {
            return e;
        }
        e = eqIs("nnz（零值被丢弃）", String.valueOf(v.nnz()), "2");
        if (e != null) {
            return e;
        }
        e = eqIs("indexAt(0)", String.valueOf(v.indexAt(0)), "1");
        if (e != null) {
            return e;
        }
        e = eqIs("valueAt(0)", String.valueOf(v.valueAt(0)), "5.0");
        if (e != null) {
            return e;
        }
        e = eqIs("indexAt(1)", String.valueOf(v.indexAt(1)), "3");
        if (e != null) {
            return e;
        }
        e = eqIs("valueAt(1)", String.valueOf(v.valueAt(1)), "7.0");
        if (e != null) {
            return e;
        }
        e = eqIs("get(0) 未存储", String.valueOf(v.get(0)), "0.0");
        if (e != null) {
            return e;
        }
        e = eqIs("get(2) 未存储", String.valueOf(v.get(2)), "0.0");
        if (e != null) {
            return e;
        }
        e = eqIs("get(3)", String.valueOf(v.get(3)), "7.0");
        if (e != null) {
            return e;
        }
        e = throwsVec("get(4) 越界", () -> v.get(4));
        if (e != null) {
            return e;
        }
        return throwsVec("get(-1) 越界", () -> v.get(-1));
    }

    // ------------------------------------------------------------------ arith

    private static String arithDot() {
        SparseVec a = SparseVec.of(6, new int[]{1, 4}, new double[]{2.0, 3.0});
        SparseVec b = SparseVec.of(6, new int[]{0, 1, 4}, new double[]{10.0, 4.0, 1.0});
        String e = eqIs("dot 重叠", String.valueOf(a.dot(b)), "11.0");
        if (e != null) {
            return e;
        }
        e = eqIs("dot 无重叠", String.valueOf(SparseVec.of(5, new int[]{1}, new double[]{2.0})
                .dot(SparseVec.of(5, new int[]{3}, new double[]{4.0}))), "0.0");
        if (e != null) {
            return e;
        }
        e = eqIs("dot 空向量", String.valueOf(a.dot(SparseVec.of(6, new int[0], new double[0]))), "0.0");
        if (e != null) {
            return e;
        }
        return throwsVec("dot 长度不一致", () -> a.dot(SparseVec.of(7, new int[0], new double[0])));
    }

    private static String arithPlus() {
        SparseVec a = SparseVec.of(6, new int[]{1, 4}, new double[]{2.0, 3.0});
        SparseVec b = SparseVec.of(6, new int[]{1, 3}, new double[]{-2.0, 5.0});
        SparseVec r = a.plus(b);
        String e = eqIs("size", String.valueOf(r.size()), "6");
        if (e != null) {
            return e;
        }
        e = eqIs("nnz（1 处抵消）", String.valueOf(r.nnz()), "2");
        if (e != null) {
            return e;
        }
        e = eqIs("indexAt(0)", String.valueOf(r.indexAt(0)), "3");
        if (e != null) {
            return e;
        }
        e = eqIs("valueAt(0)", String.valueOf(r.valueAt(0)), "5.0");
        if (e != null) {
            return e;
        }
        e = eqIs("indexAt(1)", String.valueOf(r.indexAt(1)), "4");
        if (e != null) {
            return e;
        }
        e = eqIs("valueAt(1)", String.valueOf(r.valueAt(1)), "3.0");
        if (e != null) {
            return e;
        }
        SparseVec neg = SparseVec.of(6, new int[]{1, 4}, new double[]{-2.0, -3.0});
        e = eqIs("a + (-a) 的 nnz", String.valueOf(a.plus(neg).nnz()), "0");
        if (e != null) {
            return e;
        }
        SparseVec d = SparseVec.of(5, new int[]{0}, new double[]{1.5})
                .plus(SparseVec.of(5, new int[]{2}, new double[]{2.5}));
        e = eqIs("不相交 plus 的 nnz", String.valueOf(d.nnz()), "2");
        if (e != null) {
            return e;
        }
        e = eqIs("不相交 plus 的 indexAt(1)", String.valueOf(d.indexAt(1)), "2");
        if (e != null) {
            return e;
        }
        return throwsVec("plus 长度不一致", () -> a.plus(SparseVec.of(7, new int[0], new double[0])));
    }

    private static String arithEquality() {
        SparseVec a = SparseVec.of(4, new int[]{1, 3}, new double[]{2.0, 5.0});
        SparseVec b = SparseVec.of(4, new int[]{1, 3}, new double[]{2.0, 5.0});
        if (!a.equals(b)) {
            return "期望=相同非零项集合相等 实际=false";
        }
        if (a.hashCode() != b.hashCode()) {
            return "期望=相等向量 hashCode 相同 实际=" + a.hashCode() + " / " + b.hashCode();
        }
        // 表示无关：原始数组不同（多带了零值项 / 零值位置不同），非零项集合相同即相等
        SparseVec c = SparseVec.of(4, new int[]{0, 1, 3}, new double[]{0.0, 2.0, 5.0});
        SparseVec d = SparseVec.of(4, new int[]{1, 2, 3}, new double[]{2.0, 0.0, 5.0});
        if (!a.equals(c) || !a.equals(d)) {
            return "期望=非零项集合相同即相等（与内部表示无关） 实际=" + a.equals(c) + " / " + a.equals(d);
        }
        if (a.hashCode() != c.hashCode() || a.hashCode() != d.hashCode()) {
            return "期望=表示无关的相等向量 hashCode 相同 实际=不同";
        }
        if (a.equals(SparseVec.of(4, new int[]{2}, new double[]{5.0}))) {
            return "期望=非零项位置不同则不相等 实际=true";
        }
        if (a.equals(SparseVec.of(5, new int[]{1, 3}, new double[]{2.0, 5.0}))) {
            return "期望=逻辑长度不同则不相等 实际=true";
        }
        if (a.equals(null) || a.equals("x")) {
            return "期望=与非 SparseVec（null / 字符串）比较为 false 实际=true";
        }
        if (!a.equals(a)) {
            return "期望=自反 实际=false";
        }
        return null;
    }

    // ------------------------------------------------------------------ slice

    private static String sliceRemap() {
        SparseVec a = SparseVec.of(10, new int[]{2, 5, 9}, new double[]{1.0, 2.0, 3.0});
        SparseVec s = a.slice(2, 8);
        String e = eqIs("slice(2,8).size()", String.valueOf(s.size()), "6");
        if (e != null) {
            return e;
        }
        e = eqIs("slice(2,8).nnz()", String.valueOf(s.nnz()), "2");
        if (e != null) {
            return e;
        }
        e = eqIs("slice(2,8).indexAt(0)", String.valueOf(s.indexAt(0)), "0");
        if (e != null) {
            return e;
        }
        e = eqIs("slice(2,8).valueAt(0)", String.valueOf(s.valueAt(0)), "1.0");
        if (e != null) {
            return e;
        }
        e = eqIs("slice(2,8).indexAt(1)", String.valueOf(s.indexAt(1)), "3");
        if (e != null) {
            return e;
        }
        SparseVec t = a.slice(9, 10);
        e = eqIs("slice(9,10).size()", String.valueOf(t.size()), "1");
        if (e != null) {
            return e;
        }
        e = eqIs("slice(9,10).indexAt(0)", String.valueOf(t.indexAt(0)), "0");
        if (e != null) {
            return e;
        }
        e = eqIs("slice(9,10).valueAt(0)", String.valueOf(t.valueAt(0)), "3.0");
        if (e != null) {
            return e;
        }
        e = eqIs("slice(3,3).nnz()", String.valueOf(a.slice(3, 3).nnz()), "0");
        if (e != null) {
            return e;
        }
        e = throwsVec("slice(-1,5)", () -> a.slice(-1, 5));
        if (e != null) {
            return e;
        }
        e = throwsVec("slice(5,11)", () -> a.slice(5, 11));
        if (e != null) {
            return e;
        }
        return throwsVec("slice(5,4)", () -> a.slice(5, 4));
    }

    // ------------------------------------------------------------------ scale

    private static String scaleMergeVisits() throws Exception {
        final int size = 1_000_000;
        final int nnz = 300;
        int[] aIdx = new int[nnz];
        int[] bIdx = new int[nnz];
        double[] aVal = new double[nnz];
        double[] bVal = new double[nnz];
        double[] oneVal = new double[nnz];
        for (int k = 0; k < nnz; k++) {
            aIdx[k] = k * 2000;
            bIdx[k] = k * 2000 + 1000;
            aVal[k] = k + 1.0;
            bVal[k] = 10000.0 + k;
            oneVal[k] = 1.0;
        }
        SparseVec a = SparseVec.of(size, aIdx, aVal);
        SparseVec b = SparseVec.of(size, bIdx, bVal);

        resetVisits(a);
        resetVisits(b);
        SparseVec r = a.plus(b);
        long plusVisits = readVisits(a) + readVisits(b) + readVisits(r);
        String e = bound("plus", plusVisits);
        if (e != null) {
            return e;
        }

        e = eqIs("scale plus 的 nnz", String.valueOf(r.nnz()), String.valueOf(2 * nnz));
        if (e != null) {
            return e;
        }
        e = eqIs("scale plus indexAt(0)", String.valueOf(r.indexAt(0)), "0");
        if (e != null) {
            return e;
        }
        e = eqIs("scale plus valueAt(0)", String.valueOf(r.valueAt(0)), "1.0");
        if (e != null) {
            return e;
        }
        e = eqIs("scale plus indexAt(1)", String.valueOf(r.indexAt(1)), "1000");
        if (e != null) {
            return e;
        }
        e = eqIs("scale plus valueAt(1)", String.valueOf(r.valueAt(1)), "10000.0");
        if (e != null) {
            return e;
        }
        e = eqIs("scale plus indexAt(last)", String.valueOf(r.indexAt(2 * nnz - 1)),
                String.valueOf((nnz - 1) * 2000 + 1000));
        if (e != null) {
            return e;
        }

        SparseVec d = SparseVec.of(size, aIdx, oneVal);
        resetVisits(a);
        resetVisits(d);
        double dv = a.dot(d);
        long dotVisits = readVisits(a) + readVisits(d);
        e = bound("dot", dotVisits);
        if (e != null) {
            return e;
        }
        return eqIs("scale dot 的值", String.valueOf(dv), "45150.0");
    }

    private static String bound(String op, long visits) {
        if (visits <= 0) {
            return op + " 的下标访问计数 期望=> 0（必须维护 visits 计数器） 实际=" + visits;
        }
        if (visits >= VISITS_LIMIT) {
            return op + " 的下标访问次数 期望=< " + VISITS_LIMIT + " 实际=" + visits;
        }
        return null;
    }

    // ------------------------------------------------------------------ visits 反射

    private static Field visitsField() {
        if (visitsField == null) {
            try {
                visitsField = SparseVec.class.getField("visits");
            } catch (NoSuchFieldException ex) {
                try {
                    visitsField = SparseVec.class.getDeclaredField("visits");
                    visitsField.setAccessible(true);
                } catch (NoSuchFieldException ex2) {
                    visitsField = null;
                }
            }
        }
        return visitsField;
    }

    private static void resetVisits(SparseVec v) throws Exception {
        Field f = visitsField();
        if (f != null) {
            f.setLong(v, 0L);
        }
    }

    private static long readVisits(SparseVec v) throws Exception {
        Field f = visitsField();
        return f == null ? 0L : f.getLong(v);
    }

    // ------------------------------------------------------------------ precision

    private static String precisionCancellation() {
        final int size = 4;
        int[] idx = {0, 1, 2, 3};
        double[] vals = {1.0e16, 1.0, -1.0e16, 1.0};
        double[] ones = {1.0, 1.0, 1.0, 1.0};
        SparseVec a = SparseVec.of(size, idx, vals);
        SparseVec b = SparseVec.of(size, idx, ones);
        double got = a.dot(b);
        if (Double.compare(got, 2.0) != 0) {
            return "cancellation dot 期望=2.0 实际=" + got;
        }
        return null;
    }

    private static String precisionRejectNonFinite() {
        int[] idx = {0, 1, 2};
        double[][] samples = {
            {1.0, Double.NaN, 2.0},
            {1.0, Double.POSITIVE_INFINITY, 2.0},
            {1.0, Double.NEGATIVE_INFINITY, 2.0},
        };
        for (double[] sample : samples) {
            try {
                SparseVec.of(3, idx, sample);
                return "非有限值 期望=VecException 实际=正常返回";
            } catch (VecException expected) {
                // expected
            } catch (Throwable t) {
                return "非有限值 期望=VecException 实际=" + t;
            }
        }
        SparseVec z = SparseVec.of(3, new int[] {0, 1, 2}, new double[] {-0.0, 0.0, 1.0});
        if (z.nnz() != 1 || z.indexAt(0) != 2) {
            return "负零/零归一 期望=nnz 1 且 indexAt(0)=2 实际=nnz " + z.nnz();
        }
        return null;
    }
    // ------------------------------------------------------------------ 工具

    private static String eqIs(String label, String actual, String wanted) {
        if (wanted.equals(actual)) {
            return null;
        }
        return label + " 期望=" + wanted + " 实际=" + actual;
    }

    private static String throwsVec(String label, Runnable body) {
        try {
            body.run();
            return label + " 期望=抛 VecException 实际=正常返回";
        } catch (VecException expected) {
            return null;
        } catch (Throwable t) {
            return label + " 期望=VecException 实际=" + t;
        }
    }

    private Checker() {
    }
}
