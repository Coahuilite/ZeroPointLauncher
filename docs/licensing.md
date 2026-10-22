# 许可提案：MPL-2.0 保护文件修改，发行身份另行区分

状态：[公开讨论 Issue #1](https://github.com/Coahuilite/ZeroPointLauncher/issues/1)，尚未采用。调研与维护者方向确认日期：2026-10-22。

维护者希望允许第三方商业使用，但不希望陌生修改版借用官方身份、伪装许可或让用户误以为这些改动得到本 fork 维护者认可。建议保留标准 MPL-2.0 原文，将品牌、官方渠道和不背书规则放在独立的[发行身份提案](distribution-identity.md)中。

## MPL-2.0 适合这个目标的部分

MPL-2.0 是文件级 copyleft。分发受覆盖文件的修改或包含它们的可执行程序时，需要向接收者提供相应源码及许可获取方式；不要求将修改提交回本项目，也不一律要求在公共 GitHub 仓库发布。仅在内部使用和修改，不触发同样的对外分发义务。[MPL 正文 §3.1–3.2](https://www.mozilla.org/en-US/MPL/2.0/)、[Mozilla FAQ Q5–Q10](https://www.mozilla.org/en-US/MPL/2.0/FAQ/)

商业销售与收费支持可以进行。第三方可以把这些文件与独立编写、未包含 MPL 代码的其他文件组合成闭源产品；因此，一个闭源的独立前端或商业集成可以与 MPL 核心共同发行。把原 MPL 代码搬到新文件，不会使它脱离覆盖范围。这个边界适合可复用核心，但不是“所有派生启动器都必须整体开源”。[MPL 正文 §1.7、§1.10、§3.3、§3.5](https://www.mozilla.org/en-US/MPL/2.0/)、[Mozilla FAQ Q11–Q12](https://www.mozilla.org/en-US/MPL/2.0/FAQ/)

MPL 不授予贡献者的商标、服务标志或 Logo 使用权；源码开放与发行者是否获得背书是两个问题。因此，防止冒充应通过官方渠道、发行身份及有权控制的品牌规则处理，而不是向 MPL 附加禁止商业化或禁止私下修改的条款。[MPL 正文 §2.3](https://www.mozilla.org/en-US/MPL/2.0/)

## 此次迁移没有完成换证

- 根目录旧 `LICENSE` 移至 [history/LICENSE-MIT.txt](history/LICENSE-MIT.txt)，与[原 README](history/README.original.md)一并保留。原文属于旧项目内容，不能因源码重写而忽略它的许可与署名。
- 当前 Java 文件已有沿用的 MIT 声明，本轮不批量删除或改为 MPL。这些声明的来源与署名准确性需要在正式采纳时核对；文件头不意味着原作者实际编写了重构代码。
- 已公开版本中适用的 MIT 授权不会因移动文件或删除根入口而自动撤销。新发布版本采用更严格许可，与追溯撤销既有授权不是同一件事。
- 本项目的 cleanroom 是按行为规格重新实现的工程称呼。旧源码已经被阅读，文档也保留了历史证据；不以这个名称代替版权来源判断。

MIT 要求保留适用的版权与许可声明。许可证可以位于不同文件，关键是范围清楚、实际声明被保留；只删除 `LICENSE` 既不会使已许可内容失去许可，也不能证明新许可已经生效。[MIT 正文](https://opensource.org/license/mit)、[GitHub 换证指南](https://opensource.guide/legal/#what-if-i-want-to-change-the-license-of-my-project)

## 正式采用前需要确定的范围

1. 列清可按 MPL 发布的自有文件、历史 MIT 材料和第三方文件；核对贡献者对相关部分的授权及署名。保留原 README 的 MIT 声明，不将其混入新代码换证范围。
2. 确定生效提交与版本、逐文件声明和分发时的源码入口，再加入正式 MPL 文本；不追溯宣称旧版本已经采用 MPL。
3. 将新贡献的接收许可写清楚，再继续接收需要换证的代码贡献；不由维护者替其他权利人作超出授权的许可。
4. 核对实际固定版本的依赖、打包 native、字体和品牌资产。当前 Qz Core/Platform 上游主线均声明 MIT，但不由此推导所有未来分发组件都使用同一许可。[Qz Core LICENSE](https://github.com/QuanhuZeYu/Qz-UILib-Core/blob/main/LICENSE)、[Qz Platform LICENSE](https://github.com/QuanhuZeYu/Qz-UILib-Platform/blob/main/LICENSE)

这个议题覆盖启动器代码的许可选择，不给 Minecraft、模组或整合包内容重新授权。

## 向协作者征求的意见

- 是否接受“允许商业集成、现有受覆盖文件的修改需提供源码、独立新增部分可闭源”的边界？
- 哪些现有文件的权属、署名或第三方来源需要补充说明？
- [发行身份提案](distribution-identity.md)能否让用户明确区分本 fork 发行和第三方派生？哪些名称或图形确实由维护者有权控制？

维护者倾向 MPL-2.0，不以禁止商业化为目标。禁止商业使用也不符合 OSI 的开源定义。[Open Source Definition §6](https://opensource.org/osd)
