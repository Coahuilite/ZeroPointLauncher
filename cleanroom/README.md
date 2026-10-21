# 运行三层实例原型

用一个小样例观察“基础包 → 汉化 → 个人修改”如何组成最终文件树，以及每个文件为什么来自某一层。这是 ZeroPointLauncher 文件系统重构的第一个可执行切片。

## 测试并运行

准备 JDK 25，并让本机 `JAVA_HOME` 指向它。从仓库根目录执行：

**Windows PowerShell**

```powershell
.\cleanroom\gradlew.bat -p cleanroom --no-daemon :layerfs:test :layerfs:run
```

**Linux / macOS shell**

```sh
bash cleanroom/gradlew -p cleanroom --no-daemon :layerfs:test :layerfs:run
```

首次运行会获取固定的 Gradle 9.2.1 和 Maven Central 测试依赖；缓存齐备后可以加 `--offline`。构建入口独立于根目录的旧 Gradle 工程，不初始化旧启动器、图形库或游戏实例。

如果已安装 Gradle 9.2.1，也可以使用本轮已验证的入口：

```sh
gradle -p cleanroom --no-daemon :layerfs:test :layerfs:run
```

`test` 执行内存夹具；`run` 调用同一个解析器输出最终树、来源解释、删除原因和一个故意制造的类型冲突。示例内容是合成标识，不读取本机 Minecraft、账户或目录。

## 这轮实现什么

- 固定修订的单父继承，身份与显示名分离；拒绝缺失引用和环。
- 显式共享内容层 → 祖先层 → 当前层的确定性解析。
- 普通覆盖、来源排除、来源选择、删除标记和子树替换。
- 保留候选及其遮蔽/排除原因，供之后的界面解释来源。
- 逻辑路径校验、文件/目录冲突和同层规则冲突；不根据遍历顺序猜测结果。

不可变内容层负责“期望看到什么”，未来的宿主物化器负责“如何在磁盘上实现”。当前没有目录扫描、复制/链接、共享可写存档、三方升级或崩溃恢复；本轮通过的解析测试不能替代这些能力的验收。

## 代码入口

实现位于 `layerfs/src/main/java/org/zeropointlauncher/layerfs/`：

- [`LogicalPath`](layerfs/src/main/java/org/zeropointlauncher/layerfs/LogicalPath.java)：宿主无关的相对路径。
- [`LayerFs`](layerfs/src/main/java/org/zeropointlauncher/layerfs/LayerFs.java)：修订、层、规则、结果和诊断的不可变值。
- [`LayerResolver`](layerfs/src/main/java/org/zeropointlauncher/layerfs/LayerResolver.java)：纯解析入口。
- [`LayerFsDemo`](layerfs/src/main/java/org/zeropointlauncher/layerfs/demo/LayerFsDemo.java)：三层样例。
- [`LayerResolverTest`](layerfs/src/test/java/org/zeropointlauncher/layerfs/LayerResolverTest.java)：与规格场景关联的回归测试。

讨论行为时先看[文件系统契约](../docs/cleanroom/layerfs.md)；查看本轮证据与后续边界时看[验收](../docs/cleanroom/acceptance.md)。修改规则需同步契约和反例测试，而不是只改示例输出。

## 如何继续参与

首轮重点是检查继承、规则组合和来源解释是否符合预期。后续先接临时目录中的私有物化与写入隔离，再做三方升级和失败恢复。Qz 前端、游戏下载与启动继续使用独立边界。

提交前运行[分享检查](../docs/cleanroom/sharing.md)。不要将机器上的 Java 位置写进构建文件，也不要上传包含本机路径的测试报告。
