package sparsevec;

import java.util.Arrays;

/**
 * 稠密向量：一条等长的 {@code double[]}。本类**已实现且勿改**（README 契约 1）。
 *
 * <p>语义：越界下标、长度不一致、`size` 为负一律抛 {@link VecException}。
 */
public final class DenseVec {

    private final double[] v;

    /**
     * 造一条长度为 {@code size} 的全零向量。
     *
     * @throws VecException {@code size < 0}
     */
    public DenseVec(int size) {
        if (size < 0) {
            throw new VecException("size 不能为负：" + size);
        }
        this.v = new double[size];
    }

    /** 用给定分量造一条向量。 */
    public static DenseVec of(double... values) {
        DenseVec d = new DenseVec(values.length);
        System.arraycopy(values, 0, d.v, 0, values.length);
        return d;
    }

    /** 逻辑长度。 */
    public int size() {
        return v.length;
    }

    /**
     * 读第 {@code i} 个分量。
     *
     * @throws VecException 下标越界
     */
    public double get(int i) {
        check(i);
        return v[i];
    }

    /**
     * 写第 {@code i} 个分量。
     *
     * @throws VecException 下标越界
     */
    public void set(int i, double x) {
        check(i);
        v[i] = x;
    }

    /**
     * 点积。
     *
     * @throws VecException 两条向量长度不一致
     */
    public double dot(DenseVec other) {
        sameLength(other);
        double sum = 0.0;
        for (int i = 0; i < v.length; i++) {
            sum += v[i] * other.v[i];
        }
        return sum;
    }

    /**
     * 逐分量相加。
     *
     * @throws VecException 两条向量长度不一致
     */
    public DenseVec plus(DenseVec other) {
        sameLength(other);
        DenseVec r = new DenseVec(v.length);
        for (int i = 0; i < v.length; i++) {
            r.v[i] = v[i] + other.v[i];
        }
        return r;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DenseVec)) {
            return false;
        }
        return Arrays.equals(v, ((DenseVec) other).v);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(v);
    }

    @Override
    public String toString() {
        return "DenseVec(size=" + v.length + ", " + Arrays.toString(v) + ")";
    }

    private void check(int i) {
        if (i < 0 || i >= v.length) {
            throw new VecException("下标越界：" + i + "（长度 " + v.length + "）");
        }
    }

    private void sameLength(DenseVec other) {
        if (other == null) {
            throw new VecException("参数为 null");
        }
        if (v.length != other.v.length) {
            throw new VecException("长度不一致：" + v.length + " vs " + other.v.length);
        }
    }
}
