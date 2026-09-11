# VeloVNDialog

面向 Minecraft **1.21.1 / NeoForge 21.1.248** 的视觉小说对话模组，基于 [YourZi/VNDialog](https://github.com/YourZi/VNDialog)。

## 快速开始

客户端和服务端都替换为同一版本，同一端只保留一个 dialog 模组 JAR。将 [示例](examples/guard.json) 放到服务端 `config/velovn/dialogs/`，然后：

```text
/dialog reload
/dialog show guard
```

原有的数据包路径 `data/dialog/dialogs/*.json` 继续可用。配置目录中的同 ID 剧本覆盖数据包定义；加载失败会保留上一份完整内容。

最小配置仍然很小：

```json
{
  "id": "hello",
  "entries": [
    {"id": "start", "speaker": "向导", "text": "你好，@i！"}
  ]
}
```

## 条件、随机文本和剧情变量

- `when`：直接判断玩家变量和有效权限，支持 `all / any / not`。
- `set` / `add`：完成节点或选择选项时修改玩家剧情变量。
- `text.random`：从文本池中均匀抽取一次，同一节点刷新不会重抽。
- `start`：保留原有字符串写法，也支持按顺序匹配的条件开场。
- 正文、说话人、选项均支持 `@i`、`${变量名}` 和服务端 PAPI 占位符。
- 原有 `command / commands`、`visibility_command` 继续兼容；命令可写字符串或数组。

```json
{
  "id": "greeting",
  "speaker": "守卫",
  "text": {"random": ["欢迎回来，@i。", "今天也要进城吗？"]},
  "options": [
    {"text": "进入贵宾区", "when": {"permission": "story.vip"}, "target": "vip"},
    {"text": "告辞", "end": true}
  ]
}
```

上面的条目放在剧本的 `entries` 中，`vip` 指向另一条已有条目。完整的阵营分流、PAPI 信用点和随机开场示例见 [guard.json](examples/guard.json)。

## 同一个 Citizens NPC，不同玩家不同回应

D9 使用 Citizens。选中 NPC 后，为它添加一次右键指令：

```text
/npc command add -r dialog show guard <p>
```

Citizens 默认以控制台身份运行，并把 `<p>` 替换为点击者。不同玩家调用同一个 `guard`，服务端根据其阵营变量、有效权限选择开场；NPC 本身无需复制。这个内置点击者占位符不依赖额外的 PAPI Player 扩展。

权限检查使用 Bukkit 的有效权限接口，由 D9 的 LuckPerms 处理继承、否定权限和上下文。PAPI 通过可选的 Youer/Bukkit 适配器解析，无需单独安装本项目的桥接插件。

## 管理命令

```text
/dialog show <id> [player]
/dialog list
/dialog reload
/dialog refresh [player]
/dialog var <player> <key> [value]
```

命令仍要求原有的管理员权限等级 2。NPC/其他插件可从控制台使用带目标玩家的 `show`。

例如 `/dialog var Alice faction a` 设置阵营，省略最后一个值查看变量，写 `null` 删除变量。`refresh` 重新读取当前节点的变量、权限和 PAPI，保留本次随机台词。

## 持久化与兼容范围

玩家变量按 UUID 保存到服务端存档的 `data/velovn_variables.dat`，随 Minecraft 存档保存、正常关闭落盘，不占用实体 Tag 数量。每个值为字符串、数字、布尔值或 null；单值长度和变量名仍有正常的数据边界。

**当前变量存储属于本服存档，不自动跨服复制。** 跨服存储、会话接管和跨服发奖去重尚未实现。已有 LP/PAPI 数据按其插件本身的同步机制读取；如果现阶段需要共享阵营，可使用已有的跨服权限节点作为条件。

当前协议为 **3**，与协议 1/2 不兼容，两端必须一起更新。定义与玩家展示数据已分开；客户端只接收当前节点，不接收整份剧本或服务端动作。旧的全量同步和客户端任意命令入口均已删除。

详细格式见 [配置说明](docs/CONFIGURATION.md)，验证范围见 [测试说明](docs/TESTING.md)。

## 构建

需要 JDK 21：

- Windows：`./gradlew.bat build`
- Linux/macOS：`bash ./gradlew build`

产物位于 `build/libs/`，测试报告位于 `build/reports/tests/test/index.html`。

## 维护与来源

仅维护 `main` 上的 1.21.1 主线。两条上游版本分支保留导入时的源码和历史作参考。

- NeoForge 上游基线：`5b615204be6bda52b2429b551af66cd8de05a789`
- Forge 上游基线：`9253774d7ff0dd0df757bb2579a68f661102e982`

[MIT](LICENSE)，保留上游 YourZi 的版权声明。
