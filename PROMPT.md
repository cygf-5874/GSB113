向量一旦稀疏到千分之一以下，现在的表示就浪费了，而且大数相消时点积还会丢低位。
sparsevec 是 Java 17 的向量库，只用 JDK 标准库（javac/java），构建走 `scripts/build.sh`，
自检走 `scripts/check.sh`（`check/` 是固定验收程序，别改），既有用例走 `bash scripts/test.sh`。

`DenseVec` 已经做完且用例全绿；`SparseVec` 是这次要补的一层，现在方法体全抛
`UnsupportedOperationException`。

任务：按 README「对外契约」的 11 条把 `SparseVec` 做出来，让固定件全过。

验收：
- bash scripts/build.sh 退出码 0；
- bash scripts/test.sh 全绿；
- bash scripts/check.sh 退出码 0，11 个场景全过
  （dense 2 + build 2 + arith 3 + slice 1 + scale 1 + precision 2）。

约束：
1. 不改 `check/`、不改 `VecException` 与 `DenseVec`；可以新增类。
2. 对外方法名与签名不许改；`VecTest.java` 里的既有用例一条都不许删或改。
3. 不许引入任何第三方依赖，不许加 `pom.xml` / `build.gradle`。
4. 稀疏运算不许展开成等长数组；点积必须对消去误差做补偿。