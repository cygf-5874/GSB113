# sparsevec — 稠密 / 稀疏向量

`sparsevec` 是一个 Java 17 的向量库，只用 JDK 标准库（`javac` / `java`），
不引入 Maven / Gradle，也不下载任何第三方包。它分两层：

- 稠密层：`DenseVec`，**完整实现**，覆盖等长 `double[]` 上的读写与运算。
- 稀疏层：`SparseVec`，用「下标数组 + 值数组」压缩存储，**待补**。

## 目录

```
src/main/java/sparsevec/DenseVec.java   稠密向量（已实现，勿改）
src/main/java/sparsevec/SparseVec.java  稀疏向量（本次要补的那一层）
src/main/java/sparsevec/VecException.java 非法下标 / 长度不一致 / 非法构造时抛出（已给全，勿改）
src/test/java/sparsevec/VecTest.java    既有用例（13 个，只覆盖 DenseVec，起点全绿）
scripts/build.sh                        编译 src/main/java 到 out/、src/test/java 到 out-test/
scripts/test.sh                         跑既有用例（构建后执行 sparsevec.VecTest）
scripts/check.sh                        固定验收入口（勿改）
check/Checker.java                      固定验收程序（勿改）
```

## 语言版本前提

- Java 17（`javac` / `java`）。
- **只用 JDK 标准库**：不引入 Maven / Gradle，不引入任何第三方依赖。
- 构建产物放在 `out/`、`out-test/`、`out-check/`，已在 `.gitignore` 中忽略。

## 怎么跑

```bash
bash scripts/build.sh            # 编译 src/main/java 到 out/、src/test/java 到 out-test/
bash scripts/test.sh             # 既有 13 个用例，全绿时打印 13/13 passed
bash scripts/check.sh            # 固定验收，支持 -list 与 --only <组名>
bash scripts/check.sh -list      # 列出全部 11 个验收场景
bash scripts/check.sh --only arith
```

起点状态：`DenseVec` 已实现且既有用例 13/13 全绿；`SparseVec` 的方法体全抛
`UnsupportedOperationException`，`bash scripts/check.sh` 以退出码 1 结束。

> `check/` 下的是**固定验收程序，勿改**。公开的**类型名、方法名与签名**已经是最终形态
> （可以新增内部成员、私有方法或新的类），不要改动下面 `API` 一节列出的这些。

## 对外契约

下面 9 条是 `sparsevec` 的**对外契约**，它们是本题验收点的唯一出处。
**它们是契约，不是「当前行为」的转述**；实现方式不限，但必须让这 9 条同时成立。

1. 既有 `DenseVec` 行为**不得回归**：等长 `double[]` 上的 `get` / `set` / `dot` / `plus` /
   `equals` / `hashCode` 语义不变；下标越界、长度不一致、`size` 为负一律抛 `VecException`。
2. **本次新增**：`SparseVec` 用「下标数组 + 值数组」压缩存储，`nnz` 为**严格递增**的下标序列
   （`indexAt(0) < indexAt(1) < …`）。
3. `of(int size, int[] idx, double[] val)`：`size` 为逻辑长度；`idx` 必须**严格递增**且落在
   `[0, size)`，`idx` 与 `val` 长度必须一致，`size` 不得为负 —— 任一不满足抛 `VecException`；
   `val` 中**恰好为 `0.0` 的项必须被丢弃**（压缩后 `nnz` 不含零值项）。
4. `dot(SparseVec other)`：两个逻辑长度不等抛 `VecException`；用**双指针归并**求点积，
   **不得**展开成等长 `double[]`。未存储的下标按 `0.0` 参与。
5. `plus(SparseVec other)`：逐下标归并求和，结果为 `0.0` 的项**不得留在结果里**；
   两个逻辑长度不等抛 `VecException`。
6. `slice(int from, int to)`：返回 `[from, to)` 的新向量，下标**重映射**为从 0 起；
   `from` / `to` 必须满足 `0 <= from <= to <= size()`，否则抛 `VecException`；
   切片后仍不得保留零值项。
7. `nnz()` 与实际存储的项数一致；`get(i)` 对**未存储**的下标返回 `0.0`，对越界下标抛
   `VecException`。
8. `equals` / `hashCode`：两个稀疏向量相等**当且仅当逻辑长度相同且非零项集合相同**
   —— **不得**因内部表示差异（比如是否保留了零值项、`idx`/`val` 的分块方式）而判不等；
   `hashCode` 与 `equals` 一致。
9. **规模判据**：对 `size = 1_000_000`、非零项 ≤ 1000 的向量，`plus` 与 `dot` 的
   **下标访问次数**必须 `< 4000`，且不得随 `size` 线性增长。
10. **补偿点积**：构造时任何 `NaN` / `±Infinity` 都抛 `VecException`；`dot` 对非零项采用
    带补偿的求和（Neumaier 或等价的误差补偿），对 `[1e16, 1, -1e16, 1]` 与全 1 向量的点积
    必须精确得到 `2.0`，不得先展开成等长数组。
11. **零值规范化**：`+0.0` 与 `-0.0` 都不得留在压缩表示中；`nnz()`、`indexAt()`、`equals()`
    对同一逻辑向量必须给出一致结果。

## 本次要补的一层

要补的是 `SparseVec`：压缩存储 + 归并运算 + 切片 + 规模判据。
`DenseVec` / `VecException` / `VecTest` 与 `check/` 都不许动。

第 9 条的落地口径（这是「不得展开成等长数组」的可判定形式）：`SparseVec` 必须维护一个
**`public long visits` 实例字段**，记录**该实例上已发生过的压缩存储下标访问次数**——

- 每读取一次 `idx[...]` 或 `val[...]` 的元素就 `visits++`（`size()` / `nnz()` 这类只读长度字段的
  调用不计；`indexAt` / `valueAt` 读了元素，要计）；
- 二元运算 `dot` / `plus` 把对**两个操作数**数组的访问都累加到**接收者**的 `visits` 上；
- `of()` 与 `slice()` 构造出的新实例从 `0` 开始（构造本身不计访问）。

`check/` 通过反射把参与运算的向量的 `visits` 归零后再测量一次 `plus` / `dot`。

## API

```
sparsevec.VecException extends RuntimeException

sparsevec.DenseVec                      （已实现，勿改）
  DenseVec(int size)                     （size < 0 抛 VecException）
  static DenseVec of(double... values)
  #size()                  -> int
  #get(int i)              -> double     （越界抛 VecException）
  #set(int i, double x)    -> void       （越界抛 VecException）
  #dot(DenseVec)           -> double     （长度不一致抛 VecException）
  #plus(DenseVec)          -> DenseVec   （长度不一致抛 VecException）
  #equals(Object) / #hashCode() / #toString()

sparsevec.SparseVec                     （本次要补）
  public long visits;                     （下标访问计数器，见契约 9 与上文口径）
  static SparseVec of(int size, int[] idx, double[] val)
  #size()                  -> int
  #nnz()                   -> int
  #indexAt(int k)          -> int        （第 k 个非零项的下标，k ∈ [0, nnz)）
  #valueAt(int k)          -> double     （第 k 个非零项的值，k ∈ [0, nnz)）
  #get(int i)              -> double     （未存储返回 0.0；越界抛 VecException）
  #dot(SparseVec)          -> double     （长度不一致抛 VecException）
  #plus(SparseVec)         -> SparseVec  （长度不一致抛 VecException）
  #slice(int from, int to) -> SparseVec  （非法区间抛 VecException）
  #equals(Object) / #hashCode() / #toString()
```

## 验收

`check/Checker.java` 是固定验收程序，**不要修改**。它按 6 组共 11 个场景检查上面的契约。
**每个场景都在独立 JVM 子进程里跑，带看门狗**，超时判不通过；
一个场景失败不会遮蔽其余场景（失败不早退）。

| 组 | 场景 | 对应契约 |
| --- | --- | --- |
| `dense` | `basic` | 1 |
| `dense` | `arith` | 1 |
| `build` | `reject-bad` | 3、2 |
| `build` | `compress` | 3、7 |
| `arith` | `dot` | 4 |
| `arith` | `plus` | 5 |
| `arith` | `equality` | 8 |
| `slice` | `remap` | 6 |
| `scale` | `merge-visits` | 9 |

共 9 个场景。除 `scale` 组外判据全部确定性：没有墙钟、没有随机源、没有性能门槛；
`scale` 组走契约 9 的下标访问计数（确定性、与机器速度无关），并带看门狗。
