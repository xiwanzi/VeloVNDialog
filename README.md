# VeloVNDialog

基于 [YourZi/VNDialog](https://github.com/YourZi/VNDialog) 的 Minecraft 视觉小说对话模组，后续面向 NeoForge 1.21.1 开发。

当前主分支开发服务端校验对话操作、同步修复与图片内存优化，尚未添加跨服持久化功能。

## 构建与验证

需要 JDK 21。在 Windows 执行 `./gradlew.bat build`，在 Linux/macOS 执行 `bash ./gradlew build`。
构建会运行回归测试，模组产物位于 `build/libs/`，测试报告位于 `build/reports/tests/test/index.html`。

## 协议与配置兼容

- 客户端和服务端必须一起更新：网络协议已升级至 `2`，不兼容原版 VNDialog 的协议 `1`。
- 客户端只提交会话、当前节点、操作序号和选项序号；命令由服务端从配置读取并执行。
- 点击、空格、自动播放和 Ctrl 快进统一完成节点。选择分支时，先执行当前节点命令，再执行所选选项命令；关闭对话不执行剩余命令。
- `commands` 与 `command` 两个 JSON 字段都支持，值必须为字符串数组。条件指令在提交时再次检查，应保持无副作用。
- 当前会话在断线时失效；动作去重仅限本次内存会话，不提供跨重启、跨服或重新开场后的奖励去重。
- 验证步骤和范围见 [测试说明](docs/TESTING.md)。

## 分支

- `main`：以 NeoForge 1.21.1 为基础的开发分支。
- `neoforge-1.21.1`：保留上游 NeoForge 1.21.1 源码与提交历史。
- `forge-1.20.1`：保留上游 Forge 1.20.1 源码与提交历史，供功能移植和对照。

## 上游基线

| Minecraft / 加载器 | 上游提交 |
| --- | --- |
| 1.21.1 / NeoForge | [`5b615204be6bda52b2429b551af66cd8de05a789`](https://github.com/YourZi/VNDialog/commit/5b615204be6bda52b2429b551af66cd8de05a789) |
| 1.20.1 / Forge | [`9253774d7ff0dd0df757bb2579a68f661102e982`](https://github.com/YourZi/VNDialog/commit/9253774d7ff0dd0df757bb2579a68f661102e982) |

导入日期：2026-09-11。两条上游版本分支保留导入时的源码；后续修复在 `main` 的 PR 中开发。

## 许可证

[MIT](LICENSE)。保留上游作者 YourZi 的版权声明，感谢原项目的工作。
