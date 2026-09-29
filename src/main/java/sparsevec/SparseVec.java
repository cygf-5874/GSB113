package sparsevec;

/**
 * 稀疏向量：以「下标数组 + 值数组」压缩存储的逻辑向量。
 *
 * <p>本文件当前是**待补的一层**：方法体一律抛 {@link UnsupportedOperationException}。
 * 公开的类名、方法名与签名已经是最终形态（见 README「对外契约」9 条与「本次要补的一层」），
 * 不要改动；内部表示、私有成员与私有方法可以自由设计（也可以新增类）。
 */
public final class SparseVec {

    /**
     * 规模判据用的下标访问计数器：记录**该实例上已发生过的压缩存储下标访问次数**
     * （语义见 README 第 9 条与「本次要补的一层」）。`check/` 通过反射读它。
     */
    public long visits;

    private SparseVec() {
    }

    /**
     * 用逻辑长度 {@code size}、下标序列 {@code idx} 与非零值 {@code val} 造一条稀疏向量：
     * 下标必须严格递增且落在 {@code [0, size)}，长度必须一致，恰好为 {@code 0.0} 的项被丢弃
     * （README 契约 3）。
     *
     * @throws VecException 任一约束不满足
     */
    public static SparseVec of(int size, int[] idx, double[] val) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** 逻辑长度。 */
    public int size() {
        throw new UnsupportedOperationException("not implemented");
    }

    /** 实际存储的非零项个数（README 契约 7）。 */
    public int nnz() {
        throw new UnsupportedOperationException("not implemented");
    }

    /** 第 {@code k} 个非零项的下标，{@code k} 落在 {@code [0, nnz)}。 */
    public int indexAt(int k) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** 第 {@code k} 个非零项的值，{@code k} 落在 {@code [0, nnz)}。 */
    public double valueAt(int k) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 读第 {@code i} 个分量：未存储的下标返回 {@code 0.0}（README 契约 7）。
     *
     * @throws VecException 下标越界
     */
    public double get(int i) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 双指针归并求点积，不展开成等长数组（README 契约 4）。
     *
     * @throws VecException 逻辑长度不一致
     */
    public double dot(SparseVec other) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 逐下标归并求和，结果为 {@code 0.0} 的项不留在结果里（README 契约 5）。
     *
     * @throws VecException 逻辑长度不一致
     */
    public SparseVec plus(SparseVec other) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 取 {@code [from, to)} 的新向量，下标重映射为从 0 起（README 契约 6）。
     *
     * @throws VecException 区间非法
     */
    public SparseVec slice(int from, int to) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** 逻辑长度相同且非零项集合相同时相等（README 契约 8）。 */
    @Override
    public boolean equals(Object other) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** 与 {@link #equals(Object)} 一致（README 契约 8）。 */
    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String toString() {
        throw new UnsupportedOperationException("not implemented");
    }
}
