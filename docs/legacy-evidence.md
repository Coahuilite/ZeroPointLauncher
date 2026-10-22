# 旧版仓库证据索引

> 全文记录 `5c91145` 历史基线的仓库事实，供新规格取舍参考；源码链接固定到该提交。旧实现行为不因此成为必须兼容的契约。

## 基线与项目意图

1. 本索引对应 `UIv3` 起点提交 `5c91145`（提交说明“第二代文件系统”）；可在仓库执行 `git show --no-patch --format=fuller 5c91145` 核对提交信息。
   影响新规格：**标明讨论的历史基线，避免把后续新规格误读成旧分支现状。**
2. README 将项目定位为 GTNH 专用启动器，内部名 ZeroPointLaunch/ZPL。[README](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/README.MD#L1-L2)。
   影响新规格：**产品范围和术语。**
3. README 描述过类似 MultiMC 的实例，允许继承；子实例修改不回写父实例。[README](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/README.MD#L9-L12)。
   影响新规格：**实例/继承是否属于首版范围及其语义。**
4. 旧设计区分实例运行用 `.minecraft`、可继承 `image`、控制 mod 加载的 `version.json`。[README](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/README.MD#L13-L18)。
   影响新规格：**保留/替换这些实体边界和持久化数据形态。**
5. README 将多实例共享、分享器继承以及映射列为核心机制。[README](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/README.MD#L19-L24)。
   影响新规格：**共享数据的产品需求优先级。**
6. README 所述映射源是 `image`，目标是传统 `.minecraft`；规则包含普通文件直映、二级目录共享、include/exclude/shareDir。[README](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/README.MD#L25-L39)。
   影响新规格：**文件映射模型及冲突策略；这些旧规则仅是历史意图，不自动构成兼容承诺。**
7. README 将差异比较用于升级/迁移，并描述缺失文件补齐与过期文件排除。[README](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/README.MD#L40-L45)。
   影响新规格：**是否需要可预览差异和迁移工作流。**
8. README 还列出下载、启动、镜像生成、导出等愿景。[README](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/README.MD#L46-L69)。
   影响新规格：**初版范围切分，不代表当前功能完整。**
9. README 明说旧设计基于 symlink，需管理员权限。[README](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/README.MD#L4-L6)。
   影响新规格：**是否继续采用链接、权限提示和非链接方案；旧权限要求不应直接继承为新实现前提。**

## 文件系统实现与集成边界

10. 基线 `Core` 的文件系统 API 位于 `com.github.wohaopa.zeropointlanuch.core.filesystem`；`MyFileBase` 提供从目录/JSON构造、diff、marge、update入口。[MyFileBase](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/main/java/com/github/wohaopa/zeropointlanuch/core/filesystem/MyFileBase.java#L33-L99)。
   影响新规格：**判断旧数据/能力是否可迁移，而非将方法名当作新API。**
11. 这套 Core 实现有 `DEFAULT_FI` 与 `RUNTIME_FI` 排除项（例如 saves 与 logs/versions 等差异）。[MyFileBase](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/main/java/com/github/wohaopa/zeropointlanuch/core/filesystem/MyFileBase.java#L35-L53)。
   影响新规格：**定义扫描范围时明确哪些实例目录属于用户数据、哪些是运行时文件。**
12. Core `Mapper.update` 以运行目录创建文件树缓存，再合并 mods、sharer 与实例链。[Mapper](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/main/java/com/github/wohaopa/zeropointlanuch/core/Mapper.java#L35-L67)。
   影响新规格：**是否保留共享/继承的组合模型；需要重新定义事务和冲突处理。**
13. Core 的 `LinkTools` 若本地缺少 MappingTools.jar，会在静态初始化时尝试下载。[LinkTools](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/main/java/com/github/wohaopa/zeropointlanuch/core/LinkTools.java#L30-L43)。
   影响新规格：**离线可用性与链接器依赖的交付方式；此下载行为不是必须保留的契约。**
14. 基线中的新一代 `MyFileSystem` 源码位于当时仓库根下的 `MyFileSystem/`，含 Builder、Differ、Copier、Maker、Memorizer 等类；例如 [Builder](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/MyFileSystem/src/main/java/com/github/wohaopa/filesystem/MyFileSystemBuilder.java#L26-L71)、[Differ](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/MyFileSystem/src/main/java/com/github/wohaopa/filesystem/MyFileSystemDiffer.java#L32-L50)。
   影响新规格：**可参考的代码材料与是否将其抽取/重写为独立模块。**
15. 它没有纳入 Gradle settings 的 include 列表；当时只 include Core、MappingTools、UI、Launcher。[settings.gradle](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/settings.gradle#L1-L5)。
   影响新规格：**明确新规格依赖于已集成能力还是仅存在的实验源码。**
16. `MyFileSystem/build.gradle` 本身声明 Java 8，但这不意味着它被根构建实际编译。[MyFileSystem build](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/MyFileSystem/build.gradle#L1-L18)；[settings](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/settings.gradle#L1-L5)。
   影响新规格：**Java目标版本与模块纳入需显式决策。**
17. 基线新源码中的 `MyFileSystemMarger` 只有空类声明。[Marger](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/MyFileSystem/src/main/java/com/github/wohaopa/filesystem/MyFileSystemMarger.java#L21-L24)。
   影响新规格：**不要把“新文件系统已实现合并”写成事实；合并行为仍需规格定义。**
## 构建、UI 与测试

19. 基线根工程版本为 0.3.0，使用 Java/Java Library 和 Spotless；根构建的 copyLib 取 MappingTools、Launcher、UI及UI runtime 产物。[根构建](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/build.gradle#L3-L17)、[copyLib](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/build.gradle#L60-L73)。
   影响新规格：**打包产物与模块边界。**
20. UI 配置要求 source/target 17，并使用 OpenJFX plugin 与 JavaFX 17.0.2。[UI构建](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/UI/build.gradle#L1-L13)、[JavaFX配置](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/UI/build.gradle#L37-L40)。
   影响新规格：**Java 25迁移的构建兼容与UI技术选择；旧工程设置不是Java 25支持证据。**
21. 基线 UI 依赖 JFoenix 9.0.10 与 `:Core`；未见 Qz-UILib 的Gradle依赖声明。[UI依赖](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/UI/build.gradle#L49-L58)。
   影响新规格：**明确新UI目标是替换旧UI技术栈，而非在旧栈上迭代。**
22. UI application/Launch4j 主类指向 `ZplApplication`。[UI构建](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/UI/build.gradle#L15-L20)、[application配置](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/UI/build.gradle#L25-L34)。
   影响新规格：**启动入口与发布打包设计。**
23. Gradle配置声明 JUnit 依赖与 `useJUnitPlatform`（Core、MappingTools），UI也声明JUnit Jupiter；但这些配置不能证明测试已运行或通过。[Core构建](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/build.gradle#L25-L31)、[MappingTools构建](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/MappingTools/build.gradle#L12-L19)、[UI构建](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/UI/build.gradle#L56-L58)。
   影响新规格：**测试分层及可重复验证策略。**
24. Core 的 `src/test` 有8个 Java 文件；其中可见类主要通过 `main` 执行手工流程，而不是 `@Test` 测试方法。[测试目录示例：Download](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/DownloadTaskTest.java#L28-L36)、[Install](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/InstallTaskTest.java#L33-L56)、[Launch](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/LaunchTest.java#L29-L39)。
   影响新规格：**新测试应使用隔离夹具/自动断言，不能把旧测试目录数量等同自动化覆盖。**
25. DownloadTaskTest 使用 GTNH 下载 URL，并把结果写入硬编码 D: 路径。[DownloadTaskTest](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/DownloadTaskTest.java#L28-L36)。
   影响新规格：**默认隔离测试不使用真实用户路径或网络；获明确授权的集成验收可使用受控样本。**
26. InstallTaskTest 绑定本地 D: 数据目录，设置本机 loopback provider，之后发现实例、在线安装、建立链接并启动实例。[InstallTaskTest](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/InstallTaskTest.java#L35-L55)。
   影响新规格：**区分可安全自动运行的单元测试与具备文件/进程副作用的手工集成步骤。**
27. LaunchTest 会对固定路径的 GTNH 实例执行启动。[LaunchTest](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/LaunchTest.java#L31-L39)。
   影响新规格：**验证启动时使用可控虚拟数据与显式用户动作。**
28. 旧文件系统手工测试 `update()` 会更新硬编码实例 image 与 checksum JSON；交互差异流程还会写 JSON 输出。[MyFileSystemTest](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/MyFileSystemTest.java#L32-L50)、[diff输出](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/MyFileSystemTest.java#L53-L60)、[写出差异](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/MyFileSystemTest.java#L101-L110)。
   影响新规格：**测试目录需临时化，避免触碰用户实例。**
29. `MyFileSystemMargeTest` 也直接读取开发者硬编码的测试目录。[MyFileSystemMargeTest](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/MyFileSystemMargeTest.java#L30-L47)。
   影响新规格：**旧夹具路径不应变成兼容性要求。**
30. 本基线没有 `.github` CI workflow（只读检查时未发现 `.github` 文件）。
   影响新规格：**CI 质量门槛仍须另行定义；此项是仓库状态观察，不是代码能力结论。**

## GTNH 与 1.7.10 痕迹

31. README 直接将产品定位于 GTNH，并讨论 GTNH 包/汉化下载与镜像制作。[README定位](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/README.MD#L1-L2)、[下载](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/README.MD#L46-L53)、[镜像](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/README.MD#L60-L67)。
   影响新规格：**决定首发目标是否继续聚焦 GTNH。**
32. Core 默认下载 provider 含 1.7.10 版本 JSON、client.jar、assets 与 libraries 的特定映射 URL。[DefaultDownloadProvider](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/main/java/com/github/wohaopa/zeropointlanuch/core/download/DefaultDownloadProvider.java#L26-L38)。
   影响新规格：**明确 Minecraft 元数据/下载源策略是否重做；硬编码 URL不是扩展性契约。**
33. 老测试夹具固定在 GTNH 2.3.0/2.3.2/2.3.3/2.3.4 路径或下载包名。[Instance测试](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/InstanceTest.java#L30-L34)、[Download测试](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/DownloadTaskTest.java#L30-L35)、[安装测试](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/InstallTaskTest.java#L40-L45)。
   影响新规格：**样例数据版本可更新为合成夹具，不需兼容特定开发机目录。**
34. 一个旧 filesystem 手工示例将 `mods\1.7.10` 加入排除目录。[旧Merge测试](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/test/java/test/MyFileSystemMargeTest.java#L36-L47)。
   影响新规格：**将 1.7.10 视为历史数据/样例，而非唯一支持版本限制。**

## 署名与许可证

35. 基线根目录 `LICENSE` 明示 MIT License，版权年份2023，署名“初夏同学”。[LICENSE](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/LICENSE#L1-L21)。
   影响新规格：**分发或保留上游代码时保留适用版权与许可声明。**
36. 基线根目录 `licenseHeader` 是当时项目配置使用的 MIT 头模板，同样列出2023及“初夏同学”。[licenseHeader](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/licenseHeader#L1-L23)、[Spotless配置](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/build.gradle#L47-L55)。
   影响新规格：**保留适用署名和许可声明；历史头模板不代替当前许可决策，也不意味着无须标注新贡献者。**
37. 历史基线并非全靠根 LICENSE：多份源码文件内嵌相同 MIT 声明，例如 Core 文件系统与 MyFileSystem 新源码。[MyFileBase](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/Core/src/main/java/com/github/wohaopa/zeropointlanuch/core/filesystem/MyFileBase.java#L1-L19)、[新 Builder](https://github.com/wohaopa/ZeroPointLauncher/blob/5c91145/MyFileSystem/src/main/java/com/github/wohaopa/filesystem/MyFileSystemBuilder.java#L1-L19)。
   影响新规格：**重用或移植旧文件时逐文件检查许可证头。**
