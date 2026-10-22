# ZeroPointLauncher

延续实例继承、共享和差异管理的设计，让整合包、玩家共享预设和自建实例使用同一套管理模型。GTNH-first，not only GTNH。

当前可运行的是 Java 25 文件管理核心：解析固定修订的继承关系，将内容私有复制到真实工作区，重新准备时保留个人修改。Minecraft 启动与 Qz 桌面界面尚未接入。

## 致原作者

原作者初夏同学把“子实例可以改变自己，而不改变父实例”放在设计中心，也把共享和差异管理视为启动器的重要能力。本 fork 希望接续这份思路，把它发展为适用于更多整合包和自建实例的工具。

我们完整保留了[原作者 README 原文](docs/history/README.original.md)，作为项目起点的纪念。原文来自历史提交 `5c91145`，未改写措辞或删节；其中的功能、联系方式与操作方式属于历史版本。对应的 [MIT 许可与署名](docs/history/LICENSE-MIT.txt)一并保存。

## 运行：改我的实例，不影响另一个实例

准备 JDK 25，将本机 `JAVA_HOME` 指向它，在仓库根目录运行：

```powershell
.\gradlew.bat --no-daemon :workspace-nio:run
```

Linux/macOS shell 使用 `bash gradlew --no-daemon :workspace-nio:run`。示例只操作新建的合成实验目录，不扫描或修改真实游戏实例。它会派生 A/B，修改 A、重新打开 A，再验证原内容和 B 未受影响；还会验证未发布的个人修改不进入后代实例。

按[用户验收指南](docs/workspace-walkthrough.md)保留实验文件、逐项查看结果。已验证平台见[验收记录](docs/acceptance.md)，不由命令入口推断平台支持。

## 实例修订与个人修改分开

基础整合包、玩家共享预设和派生实例地位相同，共用 `Revision`；汉化、性能配置只是预设的用途。实例引用固定父修订，不随父实例更新而漂移。

工作区运行后的新增、修改、删除和类型变化是私有 delta。重新准备不会覆盖配置、复活删除文件或清理新增文件；这些改动也不会自动进入继承链。遇到新基线时，当前入口明确要求后续升级流程，不直接覆盖。

下一切片是只读升级预览：比较旧基线、新基线与个人现状。之后再接冲突选择、可恢复提交和选定 delta 的分享修订，暂不做链接优化或共享存档。

## 开发入口

- [`layerfs/`](layerfs/)：纯解析、来源解释、基线与 delta 规则，不访问磁盘。
- [`workspace-nio/`](workspace-nio/)：复制、扫描、持久化和宿主路径检查，单向依赖核心。
- [`docs/`](docs/README.md)：架构、文件系统、游戏运行时及验收规格。
- [`tools/`](tools/)：分享前的隐私检查与隔离回归。

根目录是唯一活动构建入口；使用固定的 Gradle 9.2.1 Wrapper。运行全部测试和两个示例：

```powershell
.\gradlew.bat --no-daemon :layerfs:test :workspace-nio:test :layerfs:run :workspace-nio:run
```

首次运行会下载 Gradle 与测试依赖，缓存齐备后可加 `--offline`。本机 Java 路径、代理、实例数据和报告不进入提交；提交前执行[分享检查](docs/sharing.md)。

## 许可与第三方发行身份

维护者倾向采用 **MPL-2.0，并单独明确品牌和官方发行身份**。目标是允许商业使用，让分发时的受覆盖文件修改向接收者提供源码，同时避免第三方修改版冒充本 fork 的官方发行。

MPL-2.0 目前是讨论提案，尚未生效。现有文件中的 MIT 声明继续保留，撤下根目录旧 `LICENSE` 不会撤销历史授权或把这些文件自动变成 MPL。欢迎在[公开讨论 Issue #1](https://github.com/Coahuilite/ZeroPointLauncher/issues/1)反馈，背景见[许可讨论](docs/licensing.md)与[第三方发行身份提案](docs/distribution-identity.md)。

本 fork 的维护与发布渠道为 [Coahuilite/ZeroPointLauncher](https://github.com/Coahuilite/ZeroPointLauncher)。第三方收费、修改和支持不能仅因沿用代码或署名，就被视为本 fork 维护者或原作者的背书。
