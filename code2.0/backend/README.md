# 星轨 · 声音博物馆 · 后端

Spring Boot 3.2 / Java 17 / Spring Data JPA / H2。
产品定义见 `../PRODUCT.md` 与 `../产品定义.md`。

## 跑起来

```bash
node ../tools/gen-audio.mjs    # 只需一次：生成演示音频 + 真实波形
mvn spring-boot:run            # http://127.0.0.1:8081
# 要用真实语音识别：
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

首次启动自动建表并写入演示数据（5 件展品 / 7 条回音 / 3 段历史之声）。
数据库落在 `./data/museum.mv.db`，删掉它就能重新播种。

默认走 `mock` 识别（不联网、不花钱）。`local` profile 会读 `application-local.yml`
里的真实密钥 —— 那个文件已被 `.gitignore` 排除，**不要提交、不要截图**。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/ping` | 健康检查 + 馆藏规模 + 当前展签 provider |
| GET | `/api/ping/lexicon` | 两份词表的规模 |
| POST | `/api/session` | 建会话（`deviceKey` + 可选昵称） |
| PATCH | `/api/session/nickname` | 改昵称 |
| GET | `/api/exhibits` | 馆藏卡片（**只有展签**） |
| GET | `/api/exhibits/recent?limit=3` | 最近入馆 |
| GET | `/api/exhibits/{id或编号}` | 详情（**这里才给全文**） |
| DELETE | `/api/exhibits/{id或编号}` | 删掉自己埋下的一段（只认埋它的人；回音一起删，审核流水保留） |
| POST | `/api/exhibits` | 上传展品（multipart）。响应带 `status` / `auditNote` —— 投稿的人要知道它进没进馆、为什么 |
| GET | `/api/exhibits/{id}/echoes` | 回音列表（公开） |
| POST | `/api/exhibits/{id}/echoes` | 写回音（有 `file` 就是语音，没有就是文字） |
| GET | `/api/heritage` · `/api/heritage/{slug}` | 历史之声专区 |
| GET | `/api/mine` | 我回应过的展品 + **我埋下的声音**（含每条的审核状态与原因） |
| POST | `/api/sign/check` | **展签守卫自检**：丢一段改坏的展签进来，看它会不会被拦 |
| GET | `/api/admin/queue` | 人工复审队列 |
| POST | `/api/admin/review/{type}/{id}?pass=true` | 复审通过／驳回 |
| GET | `/api/admin/audit-records` | 审核流水（论文数据源） |
| GET | `/audio/**` | 音频静态资源，**支持 HTTP Range** |

除 `/api/ping`、`/api/session`、`/api/heritage` 外，其余接口需要请求头
`X-Session-Id: <会话 id>`。

**`/api/admin/**` 还要多一个 `X-Admin-Token`。** 令牌来自 `museum.admin.token`
（环境变量 `ADMIN_TOKEN`，或 `application-local.yml`）。
**留空 = 关闭管理口**，不是 = 不设防 —— 忘了配令牌的代价应该是「进不去后台」，
而不是「后台敞着门」。没配时这些接口返回 503 并说明怎么开。

## 三条承诺，三条实现

**1. 展签只做减法 → `sign/SignGuard.java`**
展签必须是全文的**字符子序列**。这不是提示词，是数学判断 ——
只要成立，就保证了 AI 没有添加任何新内容。标点会被归一化，
所以「把逗号改成句号」不会误报。
配套测试：`SignGuardTest`（10 例）+ `SignCompressorContractTest`（对每条种子全文跑一遍）。

**2. 时长是硬约束 → `media/WavSupport.java`**
15–60 秒（展品）、10–30 秒（语音回音）由**服务端读 WAV 头**裁决，
客户端报上来的数字不作数。不合格的文件会被删掉，不留垃圾。

**3. 波形来自真实采样 → `media/WavSupport.extractTrace`**
返回给前端的那 720 个点是从 PCM 采样里抽出来的。
界面上的声波就是这段录音本身，换音频波形就变。

## 语音识别

百度短语音识别（REST，不引 SDK —— 官方 java-sdk 最后一次更新是 2023 年）。

```
AsrClient（接口）
 ├─ MockAsrClient    离线兜底：不联网、不花钱，答辩断网也不怕
 └─ BaiduAsrClient   真实调用，access_token 缓存 25 天
```

踩过的四个硬约束：只吃 **16k / 单声道 / 16 位 WAV**；单次 ≤ **60 秒**；
`err_no != 0` 就是失败，必须显式判断；只返回文本，没有情绪和语种。
`BaiduAsrClient` 把常见的 `err_no` 翻译成了能直接照做的中文提示。

**方言**：`museum.asr.baidu.dev-pid` 可切模型 —— `1537` 普通话、`1637` 粤语、
`1837` 四川话。

### 转写失败时怎么办（这条最要紧）

两者是**刻意不对称**的：

| | 转写失败时 | 为什么 |
| --- | --- | --- |
| **语音回音** | **转人工复审，绝不放行** | 它只有转写这一条审核依据。转写不出来 = 这段音频从没被人看过。 |
| **展品音频** | 放行，但记一条流水 | 展品的主要审核对象是用户手写的全文，转写只是附加的**隐私检查**。 |

展品那一路对应的是产品定义 5.4 的承诺：「音频里如果能听清别人的名字或隐私对话，
必须处理后再上传」—— 这条只能靠转写来兑现，没有文本就没得查。
不想要这层检查可以关掉 `museum.asr.exhibit-privacy-check`（省额度）。

两条规则都有测试守着：`VoiceEchoAuditTest`。

## 审核三路

```
文字 → 规则（本地 DFA，微秒级）
        ├─ 命中拦截词     → 直接拒（且**不调用 AI**，这是成本控制点）
        ├─ 命中隐私词     → 转人工复审
        └─ 通过           → AI 判定（默认 mock，可换真实模型）
                             └─ 存疑 → 人工复审队列
```

每一路都写一条 `audit_record`，带 `costMs` —— 没有耗时就没法比较成本，
也就没法做论文里那个「审核对比实验」。

## 切换真实服务

展签压缩默认走本地确定性压缩器（零依赖、可离线、可复现）。
接真实模型只要改配置：

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

## 测试

```bash
mvn test     # 47 个用例
```

| 测试类 | 管什么 |
| --- | --- |
| `SignGuardTest` | 展签守卫的 10 条契约（含 4 条必须失败的造假样本） |
| `SignCompressorContractTest` | 每条种子全文压出来都必须过守卫、不超长、不新增实体 |
| `RuleAuditAcceptanceTest` | 10 条验收样本（违规 5 / 正常 5）+ 白名单 + 隐私词 |
| `VoiceEchoAuditTest` | **转写失败必须转人工**、转写成功用转写文本走审核 |
| `ApiSmokeTest` | 12 条端到端，含彩蛋保底、时长拒收、语音回音 |

## 已知缺口

- 词表是起步规模（拦截词 22 个、隐私词 8 个），生产环境应换成成熟词库。
- AI 审核默认是 mock，只返回 safe；接真模型后要补「存疑」分支的测试。
- 展品音频的隐私检查会在**每次上传**都调一次识别 —— 环境声也调。
  想省额度可以关掉 `museum.asr.exhibit-privacy-check`，但那样这条承诺就落空了。
- 演示音频全部是合成占位信号，不是真实录音；**历史之声**那三段的出处必须替换。
- 管理口令是「一个令牌管全部」，没有多操作员、没有分角色、没有登录态过期。
  个人项目够用，多人协作要换成一整套账号体系。
