# 配置说明

## 一份剧本

`id` 和 `entries` 必需。`start` 省略时使用第一条；写出的起点、next、target 必须存在。ID 在各自范围内唯一。

```json
{
  "id": "guard",
  "start": [
    {"when": {"var": {"faction": "a"}}, "target": "a"},
    {"when": {"var": {"faction": "b"}}, "target": "b"},
    {"target": "neutral"}
  ],
  "entries": [
    {"id": "a", "text": "A 阵营的朋友，欢迎回来。", "end": true},
    {"id": "b", "text": "B 阵营的使者，请走侧门。", "end": true},
    {"id": "neutral", "text": "你还没有选择阵营。", "end": true}
  ]
}
```

条件开场按配置顺序取第一个满足的规则；无条件的兜底规则放最后。原有 `"start": "entry_id"` 不变。

节点的 `next` 省略时按 entries 顺序前进；`"end": true` 在该节点完成后结束。`endDialog` 是兼容别名。选项可以写 `"end": true` 直接结束，不需要空的结束节点。

## when：直接表达条件

同一个 when 对象内的条件全部需要满足。查询只在进入节点、刷新、提交选择时执行，不在客户端逐帧查询。

```json
{"when": {"permission": "story.vip"}}
```

```json
{"when": {"var": {"faction": "a", "reputation": {"gte": 10}}}}
```

```json
{
  "when": {
    "any": [
      {"permission": "story.vip"},
      {"var": {"reputation": {"gte": 100}}}
    ],
    "not": {"var": {"banned": true}}
  }
}
```

| 条件 | 写法 |
| --- | --- |
| 权限 | `"permission": "story.vip"`；字符串数组表示全部拥有 |
| 变量等于 | `"var": {"faction": "a", "accepted": true}` |
| 数值比较 | `"var": {"reputation": {"gte": 10, "lt": 50}}` |
| 相等 / 不等 | `eq / ne` |
| 大小比较 | `gt / gte / lt / lte` |
| 是否存在 | `"var": {"faction": {"exists": false}}` |
| 组合 | `all`、`any` 使用非空条件数组；`not` 使用条件对象 |
| 旧实体 Tag | `"tag": "old_tag"`；数组表示全部存在 |

比较有类型：数字 12 不等于字符串 "12"。不存在的变量等于 null。权限由 Bukkit/LP 的有效权限检查决定，不是简单地检查是否直接拥有一条配置；LP 的继承、否定和上下文仍然生效。阵营只能选一个时，使用单值 faction 变量更明确。

when 可用在整个剧本、条件开场、节点和选项上。整个剧本的条件会在开场和后续有效操作时检查。未满足条件的节点按其 next/顺序跳过，不执行其中动作；只含不可见节点的循环会被拒绝。选项只隐藏于本次展示，不从剧本定义中删除；提交时再验证。建议条件选项保留一个无条件的离开选项，避免所有选项都不可用。

已有 `visibility_command` 继续有效，并与 when 合并为 AND。它维持“返回 1 才满足”的兼容语义，会执行真实指令，因此只用于已有的只读条件；新配置优先用结构化条件。

## set / add：玩家剧情状态

```json
{
  "text": "加入 A 阵营",
  "set": {"faction": "a", "joined": true},
  "add": {"reputation": 5},
  "target": "a"
}
```

set、add 可以放在节点或选项。接受一次有效完成/选择后，先按节点、选项的顺序合并变量修改，再执行节点、选项的兼容命令，最后生成下一个节点。

set 使用字符串、数字、布尔值；null 删除变量。add 只接受数字，未设置时从 0 开始；已有字符串不能被悄悄转换成数字。数值保留十进制精度。

变量按玩家 UUID 隔离。建议通用状态用 `faction` 等简短键，任务状态用 `quest.first_visit` 等前缀避免冲突。变量名可使用字母、数字、汉字以及 `_.:-`，长度 1–128；序列化后的单值上限为 16384 字符。

变量随本服 Minecraft 存档持久化。正常退出/重启可恢复；非正常崩溃仍受 Minecraft 存档周期影响。它不是跨服数据库，也不自动复制到其他后端。

## 随机与文本

普通字符串和原有 JSON Text Component 都可用：

```json
{"text": {"random": ["欢迎回来，@i。", "今天也要进城吗？"]}}
```

需要样式时把完整组件放入池中：

```json
{"text": {"random": [
  {"text": "欢迎回来。", "color": "gold"},
  {"text": "请进。", "color": "green"}
]}}
```

每次进入该节点均匀抽取一次；刷新不会重抽，重复网络操作不会触发额外抽取。再次开场或合法循环回到同一节点时重新抽取。

原有 `"text": ["第一段", "第二段"]` 仍然表示组件拼接，不能当作随机池。正文、说话人、选项文本均支持 random。

文本中的替换：

- `@i`：当前玩家名称。
- `${faction}`：当前玩家的剧情变量，支持点号等命名；未设置时保留占位符。
- `%xconomy_balance_value%` 等：由当前后端的 PAPI 按当前玩家解析。

PAPI 只处理文本，不插入命令、资源路径或条件语法。富文本只遍历 text、extra、with、fallback 等文本部分，保留样式、翻译键及结构，避免对整段 JSON 做字符串替换。

PAPI 在生成当前节点时解析，之后客户端使用展示快照。前一节点的同步动作完成后再生成下一节点；外部插件若异步更新，可在完成后触发 `dialog refresh <player>`。缺少 PAPI 时保留原始占位符并记录诊断；缺少 Bukkit 权限能力时，权限查询不会被当作“无权限”再经 not 反转放行。

PAPI 余额文案是展示数据。将来做扣费等经济动作，应以经济系统的操作结果为准，不能把格式化文案当作事务凭据。

## 命令与 NPC

```text
/dialog show guard
/dialog show guard Alice
/dialog var Alice faction a
/dialog var Alice faction
/dialog var Alice faction null
/dialog refresh Alice
/dialog reload
/dialog list
```

Citizens：选中 NPC，执行 `/npc command add -r dialog show guard <p>`。保持默认控制台执行方式；无需给普通玩家对话管理权限。其他能执行控制台命令的 NPC/菜单插件也可以调用带目标玩家的 show。

## 关闭、快进与兼容

- 剧本根级 `allowClose` 默认 true；false 禁止正常关闭操作，服务端也验证。
- 节点 `allowSkip` 默认 true；false 禁用客户端 Ctrl 快进。正常完成节点仍需服务端确认。
- `command` 与 `commands` 均可使用字符串或字符串数组，二者不要同时配置。它们是可信配置中的兼容动作，不接受客户端命令文本。
- 点击、空格、自动播放、Ctrl 都走同一服务端完成入口；选择分支时，节点和选项动作均执行。
- 主动关闭不执行剩余动作。会话内重复/越序请求不执行动作，重新开场和合法循环仍遵循配置。
- 数据包继续使用 `data/dialog/dialogs/`；也可放在 `config/velovn/dialogs/`。重载会先验证完整内容，失败不替换旧目录；已开始的会话继续使用其原始定义。
