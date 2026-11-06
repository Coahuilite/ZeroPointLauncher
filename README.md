# ZeroPointLauncher

ZeroPointLauncher 是一个面向 Minecraft 的分层实例管理与启动器核心。它旨在解决复杂模组整合包（如 GT New Horizons 等）在长期调试、多配置分发和升级过程中的状态管理痛点。

项目延续原作者初夏同学关于**“实例继承、隔离与差异管理”**的核心设计：在保持基底只读的同时，让子实例能够自由演进且互不干扰。

> **当前阶段**：项目目前专注于**文件继承与工作区文件管理核心**的实现与自动化验收。尚未接入账号登录、Minecraft 启动器运行时与图形界面（GUI）。

---

## 核心思路

在大型整合包的日常游玩与测试中，传统启动器往往需要为每个测试环境完整复制数 GB 的目录。这不仅浪费磁盘空间，更导致后期难以分辨哪些改动来自整合包原始文件、哪些是个人配置。

ZeroPointLauncher 采用类似分层文件系统与版本控制的机制来管理实例：

* **层级派生（Inheritance）**：以基础整合包或团队预设为起点快速派生新实例，清晰追踪每个文件的来源层级。
* **写时隔离（Isolation）**：子实例对配置或模组的增删改动，严格局限在自身工作区，绝不污染父实例与其他并行实例。
* **变更持久化（Delta Preservation）**：重新构建或刷新工作区时，自动识别并保留用户的个人改动（新增、修改与删除/墓碑标记）。
* **增量分发（Delta Sharing）**：未来支持仅提取并共享配置差异，告别每次微调都需要重新传输整包的繁琐流程。

---

## 模块结构

仓库采用核心规则与物理 I/O 解耦的净室设计：

| 模块 / 目录 | 职责说明 |
| :--- | :--- |
| [`layerfs/`](layerfs/) | 纯逻辑层：负责解析继承拓扑、计算文件遮蔽与差异规则，**无磁盘 I/O 副作用** |
| [`workspace-nio/`](workspace-nio/) | 执行层：基于 Java NIO 负责工作区文件扫描、隔离复制、状态持久化与路径校验 |
| [`docs/`](docs/README.md) | 架构设计文档、行为规范约定与验收说明 |
| [`tools/`](tools/) | 辅助开发与代码/日志隐私脱敏工具 |

---

## 运行演示

目前仓库包含可运行的文件管理核心原型演示。示例会在独立的临时目录中派生两个实例，模拟并验证修改隔离与状态保留逻辑（不会修改你本机现有的 Minecraft 目录）。

### 环境需求
* **JDK 25**（请确保环境变量 `JAVA_HOME` 正确配置）
* 内置 Gradle Wrapper（无需单独安装 Gradle）

### 运行步骤

**Windows (PowerShell)**
```powershell
.\gradlew.bat --no-daemon :workspace-nio:run
```

**Linux / macOS (Shell)**
```bash
bash gradlew --no-daemon :workspace-nio:run
```

运行全部单元测试及演示：
```bash
# Windows
.\gradlew.bat --no-daemon :layerfs:test :workspace-nio:test :layerfs:run :workspace-nio:run

# Linux / macOS
bash gradlew --no-daemon :layerfs:test :workspace-nio:test :layerfs:run :workspace-nio:run
```

更多测试细节与预期输出，请参阅 [工作区验收指南](docs/workspace-walkthrough.md) 和 [验收记录](docs/acceptance.md)。

---

## 参与贡献

欢迎对实例管理、分层文件系统或 Minecraft 启动器架构感兴趣的朋友一起交流讨论！

* 深入了解系统架构，请阅读 [开发文档导航](docs/README.md)。
* 提交代码或上传测试日志前，请务必阅读 [分享前检查指南](docs/sharing.md)，避免将本地绝对路径或敏感信息提交至公开仓库。

---

## 致谢与历史

感谢原作者 **初夏同学** 在原版 ZeroPointLauncher 中提出的实例继承设计思想。

为了记录项目演进脉络，仓库完整保留了 [原作者 README 归档](docs/history/README.original.md) 及 [历史 MIT 许可证与署名记录](docs/history/LICENSE-MIT.txt)（源自历史提交 `5c91145`）。

## 许可证说明

本项目历史代码遵循 MIT 许可证。重构版本目前正在讨论向 **MPL-2.0** 迁移的提案，具体细节请关注相关 Issue 讨论。