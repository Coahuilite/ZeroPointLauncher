# Minecraft 版本清单：语义与时期变化

接入一个新 Minecraft 版本时，先检查清单中出现了哪些字段和参数形状，再选择解析路径。版本号用于定位样本；实际处理依据字段内容，不能把年代划分写成拒绝其他版本的白名单。

本文记录官方元数据的字段语义、结构变化，以及一次具体实现候选的覆盖情况。游戏 JVM、目标环境、用户偏好与执行边界见[运行时合同](runtime.md)。

## 1. 官方数据从哪里取得

入口是 Mojang 的 [version_manifest_v2.json](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)。按 `versions[].id` 找到版本，再读取其 `url` 指向的完整版本 JSON。`latest.release` 与 `latest.snapshot` 分别指向最新正式版和快照，不必猜测版本命名。

本轮于 **2026-11-09** 下载并读取了下表中的完整官方清单。索引当时给出的最新正式版是 `26.3`，最新快照是 `26.4-snapshot-7`。这些来源公开可取得；本轮发现的 26.x 信息缺失来自模型和样本未覆盖，不是官方没有提供数据。

历史版本采用今天官方 Launcher 分发的清单。例如 Classic 的清单已经包含 LaunchWrapper 适配；这里说明的是当前如何准备该历史版本，不把清单内容当作其最初发行时的 Applet 启动流程。

## 2. 按影响解析的变化分期

下表按正式版边界组织；26.x 的新增分组另列已核对的相邻快照边界。一个时期内仍可增加参数或库，因此代表版本只是查阅和验证入口。

| 时期与代表清单 | 参数和规则的变化 | native 的表示 | 维护者要核对什么 |
| --- | --- | --- | --- |
| Classic～1.5：[`c0.30_01c`](https://piston-meta.mojang.com/v1/packages/9392d3f635770ac4dfd3f8c9444f319b00b08945/c0.30_01c.json)、[1.5.2](https://piston-meta.mojang.com/v1/packages/924a2dcd8bdc31f8e9d36229811c298b3537bbc7/1.5.2.json) | `minecraftArguments` 字符串；用户名和会话可作为位置参数，使用 `${auth_session}`、`${game_assets}`。Classic 样本通过 LaunchWrapper 和 Tweaker 启动 | `natives` 映射与 `downloads.classifiers`；部分 LWJGL 库按 macOS 版本条件切换 | 保留位置和顺序，不把参数统一改写为现代命名参数；解析真实 `mainClass` 和 Tweaker 参数 |
| 1.6～1.12： [1.6.1](https://piston-meta.mojang.com/v1/packages/7fd8e0c76f62813eb0465e31bb74b160c01472d6/1.6.1.json)、[1.7.10](https://piston-meta.mojang.com/v1/packages/ed5d8789ed29872ea2ef1c348302b0c55e3f3468/1.7.10.json)、[1.12.2](https://piston-meta.mojang.com/v1/packages/832d95b9f40699d4961394dcf6cf549e65f15dc5/1.12.2.json) | 仍使用字符串，转为 `--username` 等命名参数；会话、认证、资源参数随版本变化。1.7.10 包含 `--userProperties` | 继续使用映射；1.7.10 的 Twitch 制品使用 `natives-windows-${arch}` | 不硬编码一份通用旧版参数模板；`${arch}` 按目标游戏架构展开；没有 `arguments.jvm` 时由后续启动规划提供所需 JVM 参数 |
| 1.13～1.18.2：[1.13](https://piston-meta.mojang.com/v1/packages/c24c2fd37c8ca2e1c18721e2c77caf4d24c87f92/1.13.json)、[1.16.5](https://piston-meta.mojang.com/v1/packages/fba9f7833e858a1257d810d21a3a9e3c967f9077/1.16.5.json)、[1.18.2](https://piston-meta.mojang.com/v1/packages/334b33fcba3c9be4b7514624c965256535bd7eba/1.18.2.json) | 改用 `arguments.game` / `arguments.jvm`；元素可以是字符串或附带规则的对象。包含 demo、分辨率、OS、版本正则和 x86 条件 | 仍存在旧式映射；1.16.5 的 LWJGL 已是 3.x，但仍使用映射布局 | 不按 LWJGL 主版本推断 native 布局；现代字符串元素已是单个 token，不再按空白拆分 |
| 1.19 起的现代布局：[1.19](https://piston-meta.mojang.com/v1/packages/14bbfb25fb1c1c798e3c9b9482b081a78d1f3a9d/1.19.json)、[1.20](https://piston-meta.mojang.com/v1/packages/b9be1f33b477eb5d39e05cb0bad77946a50d93ae/1.20.json)、[1.21.11](https://piston-meta.mojang.com/v1/packages/35f8a9bc4874e2c95e51b3ad21e043a33776f7c2/1.21.11.json) | 保留现代参数树；1.20 增加 Quick Play 的 feature 条件与参数。这是条件分支扩展，不是新的参数树格式 | native 作为含 classifier 的独立库条目，使用 `downloads.artifact`；所核对清单不再使用 `natives` 映射 | 保留这些普通库条目；旧式 native 选择器没有请求，不等于游戏没有 native。不要仅凭坐标后缀删掉其他架构制品，先遵循清单规则 |
| 新默认 JVM 参数组：[`26.1-snapshot-1`](https://piston-meta.mojang.com/v1/packages/c30a31881957d38e9be8b15322b57a42b0246d03/26.1-snapshot-1.json) → [`26.1-snapshot-2`](https://piston-meta.mojang.com/v1/packages/a1cf7df38288de83fcd20cb0892068793f6ab1dc/26.1-snapshot-2.json) | 后者新增 `arguments.default-user-jvm`，其中包括无 `rules` 的 `value` 对象，以及使用 `os.versionRange` 的条件对象 | 继续采用现代库条目布局 | 不能因为 `game/jvm` 读取成功，就认为所有参数分组都已保留；默认用户参数应与必需 JVM 参数分开表达 |
| 同一新结构的后续变化：[26.3](https://piston-meta.mojang.com/v1/packages/80608ff6753705b6a82e2cb86a5937d4d233abd4/26.3.json)、[`26.4-snapshot-7`](https://piston-meta.mojang.com/v1/packages/6be84ec668c4c843c87f80f2a8efa3095f2989d2/26.4-snapshot-7.json) | 两者都有默认用户参数组；后者把默认堆配置改为 `-XX:ErgoHeapSizeLimit=4G`、`-XX:MaxRAMPercentage=25`。普通 JVM 参数还包含 native 访问、模块导出和不同 native 子目录 | 库布局不因上述参数变化而变回旧式映射 | 从本版本清单取值，不把上一版本的内存、GC 或 native 目录模板固定下来 |

Java 25 的运行要求与默认参数分组也不是同一次结构变化：`26.1-snapshot-1` 已升级游戏运行时，而本轮核对到分组在下一个快照才出现。参见官方 [Snapshot 1](https://www.minecraft.net/en-us/article/minecraft-26-1-snapshot-1)、[Snapshot 2](https://feedback.minecraft.net/hc/en-us/articles/42480249808653-Minecraft-Java-Edition-26-1-Snapshot-2) 说明。Java 要求应读取本版本元数据，不能从参数格式反推。

## 3. 参数与条件：字段详解

| 字段或形状 | 语义与处理要点 | 容易混淆的地方 |
| --- | --- | --- |
| `id` | 版本身份字符串，按原文定位元数据 | `26.3`、`1.7.10`、`c0.30_01c` 不是可统一解析成小数的数字 |
| `mainClass` | 交给 Java 的启动主类 | 不能统一替换为现代客户端主类；历史样本可能指向 LaunchWrapper |
| `minecraftArguments` | 旧式参数模板。先按该格式分词，再绑定占位符，保持参数顺序 | 绑定后再按空格拆分，会破坏用户名、目录等值；不能按 shell 命令重新求值 |
| `arguments.game` | 已分词的游戏参数，逐元素保留或按规则展开 | 一个字符串就是一个 token；不要再次按空白拆分 |
| `arguments.jvm` | 清单声明的 JVM 参数模板，包括 classpath、native 路径等 | `-cp` 与其后值是有序 token；`-Dos.name=Windows 10` 整体是一个 token |
| 条件参数 `{ "rules": [...], "value": ... }` | `value` 可以是一个字符串或字符串数组；允许时按原序展开，禁止时不输出 | 规则列表不是“全部允许才输出”；列表规则按顺序覆盖最终动作 |
| 无规则的 `{ "value": [...] }` | 26.x 默认用户参数组中实际存在的无条件条目 | 不能要求所有对象都必须带 `rules` |
| `arguments.default-user-jvm` | 官方提供的默认用户 JVM 参数组；内存与 GC 等偏好可能在这里出现，并可附带条件 | 应保留独立分组，由后续偏好策略决定采用或覆盖；不能直接无条件追加到用户显式参数中 |
| `${...}` | 等待后续阶段绑定的参数占位符 | `${auth_session}` 与 `${auth_access_token}`、`${game_assets}` 与 `${assets_root}` 不应仅按名字相似就合并；解析层不获取秘密或创建目录 |
| `rules[].action` | 有效动作是 `allow` / `disallow`。无规则时允许；存在规则时初始拒绝，由最后一条命中的规则决定结果 | 一条禁止规则没有命中，不等于最终允许；还要看之前的动作 |
| 同一条规则的多个条件 | OS、架构、版本与 feature 条件共同约束这一条规则 | 条件间的 AND 与规则列表的顺序覆盖是两层语义 |
| `os.name` | Mojang 清单中的平台标识，例如 `windows`、`linux`、`osx` | `osx` 与项目内部可能使用的 `macos` 需要在环境适配边界映射 |
| `os.arch` | 架构条件；本轮官方 JVM 参数样本包含 `x86`，用于控制 `-Xss1M` | 使用目标游戏进程架构，不从启动器宿主 JVM 隐式读取；示例值不是架构全集 |
| `os.version` | OS 版本正则。JSON 解码后的 `^10\.` 用于匹配以 `10.` 开头的版本字符串 | 不是版本数字范围，也不能把该正则强制当成与完整版本字符串相等 |
| `os.versionRange` | 26.x 默认参数中实际使用的 OS 版本范围，包含 `min` / `max`，例如 Windows 构建号 `10.0.17134` | 不是 `os.version` 正则，也不是 Java 版本。端点包含规则尚待对照 Launcher 求值依据核实；不能只将字段加入白名单而丢掉范围条件 |
| `features` | 本次环境／启动选择提供的布尔事实，例如 demo、自定义分辨率、Quick Play | feature 名称可以扩展；未知名称、缺失事实、非布尔坏数据是不同情况，不能一概强制转成 `false` |

缺失环境事实如何告知调用方、畸形数据如何降级，是项目合同与容错策略，需要单独说明。测试固定某种降级策略，只证明实现没有偏离该策略，不自动证明该策略覆盖所有官方清单。上层的未决条件与用户继续选择仍按[运行时合同](runtime.md)处理。

## 4. 库、native 与准备元数据：字段详解

| 字段 | 语义与处理要点 | 后续责任 |
| --- | --- | --- |
| `libraries[]` | 有序依赖声明；同一坐标可出现多条、附带不同规则或下载信息。完整 1.12.2 清单中就有这种形状 | 解析时保留条目与规则；不要只按 `name` 提前去重 |
| `libraries[].name` | 库坐标，可含 classifier，例如现代 `...:natives-windows` | 保留原文；坐标到路径／classpath 的推导是另一项能力 |
| `downloads.artifact` | 单个库制品的 `path`、`url`、`sha1`、`size` 等描述；现代 native 也可在这里 | 下载、完整性校验与落盘由准备阶段处理 |
| `natives` | 旧布局中从 OS 键映射到 classifier；映射字符串可含 `${arch}` | 先按目标环境选映射，再展开占位符，不靠宿主位数选择 |
| `downloads.classifiers` | classifier 到制品描述的字典 | 用选出的 classifier 查找；缺失映射与缺失制品条目分开诊断 |
| `extract.exclude` | 旧式 native 解压排除项，例如 `META-INF/` | 解析保留，磁盘解压阶段执行；产生请求不代表已经解压成功 |
| `downloads.client` / `downloads.server` | 游戏本体制品，和 `libraries[].downloads` 分属不同对象 | 游戏制品准备；不由参数筛选器下载 |
| `assetIndex` / `assets` | 资源索引描述及资源集合标识；历史参数使用的资源目录符号也不同 | 资源准备按对应清单及索引提供目录，不只替换参数名称 |
| `javaVersion` | 本版本声明的 Java 运行要求／组件信息 | 与实际游戏 JVM 探测结果核对；当前历史清单也可能补有该字段，不能当作原始发行年代证据 |
| `logging` | 日志配置制品与注入参数的描述 | 启动规划与准备阶段另行消费；读到其他参数不代表日志配置已经处理 |
| `minimumLauncherVersion` | 官方 Launcher 协议／能力级别信息 | 与 Minecraft 版本号、Java 主版本分开处理 |
| `type`、`time`、`releaseTime` | 版本分类与时间信息 | 用于索引／展示；解析路径仍依据实际字段形状 |
| `inheritsFrom` | 组件／加载器清单使用的父清单引用 | 按对应来源处理继承；主类继承、库／参数合并与 ZPL 实例继承是不同合同 |

Quick Play 是既有 feature 条件参数的一个具体应用。维护时应同时核对关闭和启用后的 token；夹具中存在该字段，不等于其输出已经被断言或实际调用覆盖。官方支持说明见 [Quick Play](https://www.minecraft.net/es-es/article/quick-play-coming-java-and-bedrock-edition)。

## 5. 2026-11-09 的实现审查记录

审查对象为 [PR #6](https://github.com/Coahuilite/ZeroPointLauncher/pull/6) 与 [PR #8](https://github.com/Coahuilite/ZeroPointLauncher/pull/8) 合起来的候选 `5e990bcc3de2effbe1fe6ee73694aeefec53412a`。此节固定该候选的观察结果；后续功能的当前验收状态由[验收记录](acceptance.md)维护。

| 能力 | 该候选的证据与边界 |
| --- | --- |
| 旧字符串参数 | 实际调用保留 Classic 的位置参数和 Tweaker、1.6.1 的 `--session`、1.7.10 的 `--userProperties` 等；旧清单没有 JVM 参数数组时返回空列表，不负责补完整启动命令 |
| 现代 `game/jvm` 参数 | 1.13、1.16.5、1.18.2 的条件展开正常；1.16.5 的 Windows 版本参数保持单 token，x86 的 `-Xss1M`、demo／分辨率分支能生效 |
| 两种 native 布局 | 1.7.10 的 32／64 位 classifier 能区分；Classic 的 macOS 版本条件能选择不同 LWJGL 分支；1.19 起的独立 native 库条目被保留，不生成旧式请求 |
| Quick Play | 用完整 1.20 清单和启用上下文调用后，能保留 `--quickPlayPath` 与 `--quickPlaySingleplayer` 及其占位符 |
| 26.x 新默认组 | `default-user-jvm` 未建模，整组被忽略，解析告警仍为空；普通 `game/jvm` 仍能读取 |
| 新版本范围规则 | 将官方默认组中的 Windows `versionRange` 规则交给现有规则解析器，会标记为 `malformed` 并跳过；尚未支持其范围语义 |
| 主类继承 | 已有测试覆盖链顺序、主类覆盖、缺父和成环；这不等于已实现完整 loader 的库／参数合并 |
| 其他准备字段 | #6 的交付范围未包含 `assetIndex`、游戏本体下载、`javaVersion`、`logging` 的完整消费；由后续规划／准备切片接入 |
| 现有测试与 CI | 本地现有 `launch-spec` 测试通过；工作流既缺 `launch-spec/**` 触发路径，也未调用 `:launch-spec:test`。CI 绿灯只覆盖已执行的旧模块任务 |

**本轮维护者决定：接受 #8 按已实现的数据处理子集合并。** 26.x 默认组与范围条件的缺口作为后续覆盖工作，不再阻断此次合并。

后续工作归属：清单解析维护者补默认参数分组和版本范围语义，启动规划维护者定义默认组与用户偏好的组合方式。声明这些新能力可用前，使用同一份官方边界清单复核输出；不以参数数量相同或所有旧测试通过替代语义检查。

本次维护追加已为 CI 的 `push`、`pull_request` 两处触发路径加入 `launch-spec/**`，并将 `:launch-spec:test` 接入原有 Windows／Linux 构建任务；上表中的 CI 缺口描述的是追加前的候选。

## 6. 更新这份表时怎么取样

1. 从官方索引取得完整 JSON，记录版本 ID、原始 URL、核查日期和字段变化；保留独立于候选实现的原始输入。
2. 先区分新字段、新参数分组、已有条件的新分支，以及只有值发生变化的情况，再决定是否增加时期或样本。
3. 对结构变化取边界两侧；对规则变化提供能区分分支的目标事实。例如 Classic 的两个 macOS 版本、旧 native 的目标位数、现代参数的 feature 开关。
4. 用生产解析／筛选入口查看实际 token、库条目、native 请求与诊断，并对照原文。仅能反序列化、条目数量一致或夹具存在某个字段，都不等于语义完整。
5. 实现选择、已验证的外部语义和仍待核对的条件分别记录。后续发现新字段时更新所属行，不另建一份并行的“当前规则表”。
