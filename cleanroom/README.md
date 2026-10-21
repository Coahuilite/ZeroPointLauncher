# 两个同源实例，各自保留修改

从同一个玩家共享预设派生 A、B。在 A 中修改配置、添加文件、删除文件，然后重新准备：A 的改动保留，B 和原内容保持不变。这是当前可运行的文件系统切片。

## 运行真实工作区示例

准备 JDK 25，并让本机 `JAVA_HOME` 指向它。从仓库根目录执行：

**Windows PowerShell**

```powershell
.\cleanroom\gradlew.bat -p cleanroom --no-daemon :workspace-nio:run
```

**Linux / macOS shell**

```sh
bash cleanroom/gradlew -p cleanroom --no-daemon :workspace-nio:run
```

示例使用合成内容和新建实验目录，调用实际工作区实现；不会扫描 Minecraft、登录账户或操作真实游戏实例。按[用户验收指南](../docs/cleanroom/workspace-walkthrough.md)查看每一步的意义及如何保留样本检查文件。各平台实际验证状态见[验收记录](../docs/cleanroom/acceptance.md)。

首次运行会获取固定的 Gradle 9.2.1 和 Maven Central 测试依赖；缓存齐备后可以加 `--offline`。构建入口独立于根目录的旧 Gradle 工程，不初始化旧启动器或图形库。本机 Java 路径及代理通过进程环境提供，不写入仓库。

## 测试及来源解释示例

```powershell
.\cleanroom\gradlew.bat -p cleanroom --no-daemon :layerfs:test :workspace-nio:test :layerfs:run :workspace-nio:run
```

如果已安装 Gradle 9.2.1，可将上述 `gradlew.bat` 入口替换为 `gradle`，保留 `-p cleanroom` 和任务参数。

原来的 `:layerfs:run` 仍是纯内存来源解释示例：显示最终文件树、来源选择、删除原因和类型冲突。它的派生层是可继承的内容声明，新的工作区示例才展示运行后的私有 delta。

## 已实现与下一步

- 固定修订的单父继承、内容覆盖、来源解释和结构化冲突。
- 基础包、玩家共享预设、派生实例共用 `Revision`；汉化不是特殊类型。
- 真实文件的私有复制、持久基线、重新打开后的新增/修改/删除/类型变化比较。
- 同基线重新准备保留当前工作区；拒绝用新基线覆盖个人修改。
- 首次准备未完成、状态损坏、非法宿主路径等情况明确拒绝就绪。

当前不做链接优化、三方升级、delta 提升、共享可写、完整崩溃恢复、旧实例导入、游戏启动或图形界面。后续从同基线保留推进到可预览的升级与恢复。

## 实现边界

- `layerfs`：纯解析、基线和 delta 规则，不访问磁盘。
- `workspace-nio`：Java NIO 宿主实现，依赖 `layerfs`；执行复制、扫描、状态持久化和宿主路径检查。

解析入口见 [`LayerResolver`](layerfs/src/main/java/org/zeropointlauncher/layerfs/LayerResolver.java)，实例修订见 [`LayerFs`](layerfs/src/main/java/org/zeropointlauncher/layerfs/LayerFs.java)。工作区的契约与代码入口见[文件系统规格](../docs/cleanroom/layerfs.md)。

修改行为时同步规格与反例测试。提交前运行[分享检查](../docs/cleanroom/sharing.md)，不要上传本机构建报告或实验目录。
