# 星轨 · 声音博物馆 · 后端

Spring Boot 3.2 / Java 17 / Spring Data JPA / H2 文件库。
产品定义见 `../../docs/PRODUCT.md` 与 `../../docs/产品定义.md`；部署步骤见 `../../docs/部署说明.md`。

## 跑起来

```bash
node ../tools/gen-audio.mjs    # 只需一次：生成演示音频 + 真实波形
mvn spring-boot:run            # http://127.0.0.1:8081
```

首次启动自动建表并写入演示数据（5 件展品 / 3 段历史之声，回音见 `resources/seed/seed.json`）。
数据库落在 `./data/museum.mv.db`。**不要为了重新播种就删库** ——
种子只在空库时写入；已有数据时只做两件自愈：修正演示数据的时间、更新演示展位的说明文字。

默认 `mock` 识别（不联网、不花钱）。`local` profile 会读 `application-local.yml`
里的真实密钥 —— 那个文件已被 `.gitignore` 排除，**不要提交、不要截图**。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/ping` | 健康检查 + 馆藏规模 + 当前展签 provider |
| GET | `/api/ping/lexicon` | 两份词表的规模 |
| POST | `/api/session` | 建会话（`deviceKey` + 可选昵称） |
| PATCH | `/api/session/nickname` | 改昵称 |
| GET | `/api/exhibits?q=` | 馆藏卡片（**只有展签**）。`q` 覆盖标题／故事／地点，只筛已通过 |
| GET | `/api/exhibits/recent?limit=3` | 最近入馆 |
| GET | `/api/exhibits/{id或编号}` | 详情（**这里才给全文**）。未通过返回 **404** |
| POST | `/api/exhibits` | 投稿（multipart，含 `soundType` / `originalConfirmed`）。响应带 `status` / `auditNote` |
| PATCH | `/api/exhibits/{id或编号}` | **编辑自己的投稿**（JSON）。编号不变、不新增记录，改完重新审核 |
| DELETE | `/api/exhibits/{id或编号}` | 删掉自己埋下的一段（只认埋它的人；回音与**全部**语音文件一起删，审核流水保留） |
| GET | `/api/exhibits/{id}/echoes` | 回音列表（公开，只含 PASS） |
| POST | `/api/exhibits/{id}/echoes` | 写回音（有 `file` 就是语音）。响应带 `status` / `auditNote` / `authorId` |
| GET | `/api/heritage` · `/api/heritage/{slug}` | 历史之声专区 |
| GET | `/api/mine` | 我的投稿（含状态）、我发出的回音、回音收件箱、消息列表与未读数 |
| GET | `/api/mine/uploads/{no}` | 编辑页回填（**只给原作者**，含待审/驳回内容） |
| GET | `/api/mine/echoes` | 我发出的回音（含审核状态与原因） |
| GET | `/api/mine/inbox` | 我的展品收到的公开回音 |
| GET | `/api/mine/notifications` | 消息列表 + 未读数 |
| GET | `/api/mine/notifications/count` | 只取未读数（顶栏 5 秒轮询用） |
| PATCH | `/api/mine/notifications/{id}/read` | 标记本人消息已读（别人的标不动） |
| GET | `/api/draw` | 打捞（彩蛋保底 + 分类型去重） |
| POST | `/api/sign/check` | **展签守卫自检**：丢一段改坏的展签进来，看它会不会被拦 |
| GET | `/api/admin/queue` | 人工复审队列（带 `audioUrl` / `seconds` / 地点 / 日期） |
| POST | `/api/admin/review/{type}/{id}?pass=&reason=` | 复审通过／驳回（**驳回必须写原因**） |
| GET | `/api/admin/audit-records` | 审核流水（论文数据源） |
| GET | `/audio/**` | 音频静态资源，**支持 HTTP Range** |

除 `/api/ping`、`/api/session`、`/api/heritage`、`/api/exhibits`（列表/详情）、
`/api/exhibits/{id}/echoes`（列表）、`/api/draw` 外，其余接口需要请求头
`X-Session-Id: <会话 id>`。

**`/api/admin/**` 还要多一个 `X-Admin-Token`。** 令牌来自 `museum.admin.token`
（环境变量 `ADMIN_TOKEN`，或 `application-local.yml`）。
**留空 = 关闭管理口**，不是 = 不设防 —— 忘了配令牌的代价应该是「进不去后台」，
而不是「后台敞着门」。没配时这些接口返回 503 并说明怎么开。

## 三条承诺，三条实现

**1. 展签只做减法 → `sign/SignGuard.java`**
展签必须是全文的**字符子序列**。这不是提示词，是数学判断。
标点会被归一化，所以「把逗号改成句号」不会误报。
配套测试：`SignGuardTest`（10 例）+ `SignCompressorContractTest`（对每条种子全文跑一遍）。
**它限制的是删改，不等于完整的语义真实性证明** —— 文档里不要夸大成「证明了内容真实」。

**2. 时长是硬约束 → `media/WavSupport.java`**
10–60 秒（展品）、10–30 秒（语音回音）由**服务端读 WAV 头**裁决，
客户端报上来的数字不作数。不合格的文件会被删掉，不留垃圾。

**3. 波形来自真实采样 → `media/WavSupport.extractTrace`**
返回给前端的那 720 个点是从 PCM 采样里抽出来的。

## 审核：本地规则 + 人工复审

```
文字 → 规则（本地 DFA，微秒级）
        ├─ 命中拦截词     → 直接驳回
        ├─ 命中隐私词     → 转人工复审
        └─ 通过           → 通过
```

展品的**标题、故事、地点分别过规则**，结论按 `REJECT > REVIEW > PASS` 合并。
演唱类（`SINGING`）不依赖自动识别，**一律强制转人工**。

**这里没有「AI 路」。** 以前这里有一行直接写 `mock 判定：safe` 的所谓 AI 判定 ——
那不是审核结果，只是给流水凑了一行。本项目不接真实大模型内容审核，所以如实只保留
规则和人工两道。`AuditRecord.Stage.AI` 保留在枚举里只是为了兼容老库里的历史数据，
现在的代码不会再写新的 AI 流水。`/api/ping` 之外可以用 `AuditService.capability()`
看到当前审核能力的诚实描述。

每一路都写一条 `audit_record`，带 `costMs`。人工复审由 `ReviewService` 在**同一个事务**里
完成三件事：改状态、写流水、发消息 —— 少写一件，收件箱和流水就会对不上。

## 语音识别

百度短语音识别（REST，不引 SDK）。

```
AsrClient（接口）
 ├─ MockAsrClient    离线兜底：不联网、不花钱；reliable() = false
 └─ BaiduAsrClient   真实调用，access_token 缓存 25 天；reliable() = true
```

踩过的四个硬约束：只吃 **16k / 单声道 / 16 位 WAV**；单次 ≤ **60 秒**；
`err_no != 0` 就是失败，必须显式判断；只返回文本，没有情绪和语种。

### 没有真实识别时不能造假

`MockAsrClient.reliable()` 返回 `false`，此时语音回音的处理是：

- **不生成任何转写文本**（不会把样例句子写进用户的回音正文）；
- 直接 `REVIEW` 转人工，理由写清 `provider=mock`；
- 规则路一步都不跑 —— 没有文本，就没有可审的对象。

### 转写失败时怎么办（这条最要紧）

两者是**刻意不对称**的：

| | 转写失败时 | 为什么 |
| --- | --- | --- |
| **语音回音** | **转人工复审，绝不放行** | 它只有转写这一条审核依据。转写不出来 = 这段音频从没被人看过。 |
| **展品音频** | 放行，但记一条流水 | 展品的主要审核对象是用户手写的全文，转写只是附加的**隐私检查**。 |

展品那一路对应产品定义 5.4 的承诺。不想要这层检查可以关掉
`museum.asr.exhibit-privacy-check`（省额度）。

两条规则都有测试守着：`VoiceEchoAuditTest`（4 例，含「Mock 不造伪转写」）。

## 编号、删除、打捞

- **编号**：`config/ExhibitNoAllocator` 用数据库序列分配。删掉最大编号后再投稿不会重用旧编号，
  两个人同时投稿也不会读到同一个最大值。序列在启动时和 `max(no)` 对一次账，落后就往前重启；
  既有编号一律不重排。
- **删除**：数据库删除在事务里完成，音频文件在**事务提交之后**才删；删的是展品音频
  + **全部**语音回音（含待审、被驳回的），只清 PASS 会把文件留在磁盘上。
  指向该展品的消息同时失效，不留一个必然点不开的入口。
- **打捞去重**：最近记录按 `targetType:targetId` 组成键比较。exhibit 与 heritage 的自增 id
  都从 1 开始，只比数字会互相排掉（`DrawServiceTest` 做确定性验证）。

## 切换真实服务

展签压缩默认走本地确定性压缩器（零依赖、可离线、可复现）。接真实模型只要改配置：

```yaml
museum:
  sign:
    provider: remote
    base-url: https://api.deepseek.com
    api-key: ${AI_API_KEY}
    model: deepseek-chat
```

**模型输出同样要过 SignGuard，过不了就自动退回本地压缩器。**
宁可展签朴素一点，不能造假。

语音识别只有在**真实密钥 + 真人录音验证识别文本确实对应音频**之后才把
`ASR_PROVIDER` 改成 `baidu`；否则保持 `mock`，也就是「语音回音全部人工复审」。

## 测试

```bash
mvn test     # 78 个用例，0 失败 0 跳过
```

| 测试类 | 用例 | 管什么 |
| --- | --- | --- |
| `SignGuardTest` | 10 | 展签守卫的契约（含必须失败的造假样本） |
| `SignCompressorContractTest` | 11 | 每条种子全文压出来都必须过守卫、不超长、不新增实体 |
| `RuleAuditAcceptanceTest` | 14 | 10 条验收样本 + 白名单 + 隐私词 |
| `VoiceEchoAuditTest` | 4 | 转写失败转人工、转写成功用转写文本、**Mock 不造伪转写**、通过后通知作者 |
| `DrawServiceTest` | 3 | 打捞去重必须分类型 |
| `AdminAuthFilterTest` | 4 | 管理口安全默认与口令校验 |
| `ApiSmokeTest` | 14 | 端到端：彩蛋保底、时长拒收、语音回音、删除清理、编号 |
| `MuseumFlowTest` | 18 | 一次完整业务流：编辑重审、通知与已读、收件箱、待审音频、驳回原因、编号不复用、检索 |

**跑测试前先准备音频**（`node ../tools/gen-audio.mjs`）。缺文件时部分用例会跳过 ——
跳过不等于通过。

## 已知缺口

- 词表是起步规模（拦截词 22 个、隐私词 8 个），生产环境应换成成熟词库。
- 没有接入真实大模型内容审核，也没有打算用一行 `safe` 冒充它。
- 展品音频的隐私检查会在**每次上传**都调一次识别 —— 环境声也调。
  想省额度可以关掉 `museum.asr.exhibit-privacy-check`，但那样这条承诺就落空了。
- 演示音频全部是合成占位信号，不是真实录音；**历史之声**那三段是「展位示例」。
- 管理口令是「一个令牌管全部」，没有多操作员、没有分角色、没有登录态过期。
- 轻身份不提供跨设备恢复：换浏览器、清 localStorage 就是另一个人，本轮不新增账号体系。
