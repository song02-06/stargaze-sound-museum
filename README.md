# 星轨 · 声音博物馆

一个「星空下的声音漂流瓶」：用户留下一段话，AI 把它润色成一个小故事，审核通过后沉入星空；
其他人随机捞起一段声音，偶尔捞到一段历史声音彩蛋。

本项目是《项目计划书.md》与《实施手册.md》对应的**可运行 Demo**，
覆盖阶段 0–2 与阶段 6 的骨架，ASR 与 AI 默认走 Mock，**不需要任何 API Key 就能跑起来**。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Spring Boot 3.2.12 / Java 17 / Spring Data JPA / H2 文件库 |
| 前端 | Vue 3 + Vite + Three.js |
| 视觉 | Three.js 自定义 Shader 星空 + 星轨弧线 + 流星雨，由音频频谱驱动 |
| 审核 | `houbb/sensitive-word`（规则路，Apache-2.0）+ AI 判定（AI 路）+ 人工复审（人工路） |

## 快速开始

### 1. 生成占位音频（只需一次）

```bash
node tools/gen-seed-audio.mjs
```

会在 `backend/data/audio/` 下生成 10 个 16k 单声道 WAV，
让 Demo 在断网、没有任何素材的情况下也能完整演示。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

首次启动会自动建表并写入种子数据（5 条留言瓶 + 5 条历史彩蛋）。
验证：浏览器打开 <http://localhost:8080/api/ping>，应返回 `pong`。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 <http://localhost:5173>。

> 录音需要浏览器麦克风权限，`localhost` 属于安全上下文，可以直接用；
> 若用局域网 IP 访问，需要 HTTPS。

## 目录结构

```
├─ backend/                      Spring Boot 单体服务
│  └─ src/main/java/com/yourname/museum/
│     ├─ controller/             Ping / Bottle / Draw / Admin
│     ├─ service/                BottleService / AuditService / DrawService / FileStorageService
│     ├─ client/                 AsrClient / AiClient —— 所有外部调用都收敛在这里
│     ├─ entity/ repository/ dto/ config/ common/
├─ frontend/                     Vue3 + Vite
│  └─ src/
│     ├─ components/StarField.vue    Three.js 星轨 + 流星雨（音频驱动）
│     ├─ components/Recorder.vue     录音 + 前端转 16k WAV
│     └─ utils/wav.js                webm/opus → WAV 的重采样与封装
├─ tools/gen-seed-audio.mjs      占位音频生成脚本
└─ docs/                         素材来源登记、参考项目核实结果
```

## 接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/ping` | 健康检查，顺带返回当前 provider 与语料规模 |
| POST | `/api/bottle` | 上传音频（multipart：`file` / `note` / `durationMs`） |
| GET | `/api/bottle/{id}` | 查询单条留言 |
| GET | `/api/bottle/recent` | 最近通过的留言 |
| GET | `/api/draw` | 随机捞取（权重 7:3，本轮不重复） |
| GET | `/api/admin/queue` | 人工复审队列 |
| POST | `/api/admin/review/{id}?pass=true` | 人工通过／驳回 |
| GET | `/api/admin/audit-records` | 审核流水（论文实验二的数据源） |
| GET | `/audio/**` | 音频静态资源，支持 Range 请求 |

## 切换到真实云服务

默认 `museum.asr.provider=mock`、`museum.ai.provider=mock`，不联网、不花钱。
要接真实服务，只改 `backend/src/main/resources/application.yml` 两行：

```yaml
museum:
  asr:
    provider: baidu       # 需要 BAIDU_APP_ID / BAIDU_API_KEY / BAIDU_SECRET_KEY
  ai:
    provider: deepseek    # 需要 AI_API_KEY
```

密钥只放环境变量，**不要写进代码、不要提交 Git**：

```powershell
setx BAIDU_APP_ID "你的AppID"
setx BAIDU_API_KEY "你的APIKey"
setx BAIDU_SECRET_KEY "你的SecretKey"
setx AI_API_KEY "sk-你的DeepSeekKey"
```

两个 Key 都没配也能完整演示：Mock 实现会一直保留，同时它就是断网时的兜底。

## 审核引擎（阶段 4）

规则路有两个可切换的实现，配置项是 `museum.audit.engine`：

| 值 | 引擎 | 特点 |
| --- | --- | --- |
| `houbb`（默认） | houbb/sensitive-word | 内置约 6 万条词库，支持繁简互换、全半角、拼音变体识别 |
| `dfa` | 自研 DFA | 约 80 行、零依赖、可离线，只做精确匹配，用作对照组与断网兜底 |

词表分散在 `backend/src/main/resources/sensitive/` 下四份文件，各司其职：

| 文件 | 作用 |
| --- | --- |
| `deny-words.txt` | 拦截词，命中直接拒绝 |
| `extra-words.txt` | 按测试集反馈持续补充的词 |
| `review-only.txt` | 隐私类词，命中后转人工复审而不是拒绝 |
| `whitelist.txt` | 白名单，消除「正常词含敏感子串」的误伤 |

> 实现上的一个坑：`wordDeny(...)` 是**替换**而不是追加。直接传自定义词表会把库自带的
> 6 万词全丢掉，必须用 `WordDenys.chains(WordDenys.defaults(), 自定义)` 包一层。
> 白名单同理用 `WordAllows.chains(...)`。

## 运行测试

```bash
cd backend
mvn test
```

当前覆盖阶段 4 的验收项：10 条验收样本（违规 5 / 正常 5）、隐私词转复审、
白名单消除误伤。测试直接复用 `AuditService.classifyRule`，保证与线上判定一致。

## 已知待办

- [ ] `BaiduAsrClient` 按官方 REST 文档编写，尚未用真实 Key 联网验证 —— 对应手册「阶段 1 任务 A」。
- [ ] `DeepSeekAiClient` 同上。**降级分支已完整验证**（错误 Key、不可达地址两条路径），
      正常润色分支仍需真实 Key。
- [ ] 星轨 Bloom 后期未接入，`StarField.vue` 里留了 TODO 与注意事项。
- [ ] `docs/素材来源.md` 里的历史彩蛋仍是占位素材，需按计划书第九章替换为有明确出处的音频。
- [ ] 管理后台目前只有接口，没有独立页面。
- [ ] 自动化测试目前只覆盖审核模块，Service 层其余部分（抽取权重、文件存储、上传校验）尚未覆盖。
