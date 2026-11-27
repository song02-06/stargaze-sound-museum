# 星轨 · 声音博物馆

一个「星空下的声音漂流瓶」：用户录一段 10–60 秒的声音，写下它的故事和语境，
审核通过后沉入星空；其他人随机捞起一段声音，偶尔捞到一段历史声音的展位示例。

> **所有代码都放在 `code/` 下，后端与前端各自独立。**
> 本轮实施记录见 [`docs/本轮实施结果.md`](docs/本轮实施结果.md)。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前端 | Vue 3 + Vite（响应式用户 Web 端 + 独立管理 Web 端） |
| 后端 | Spring Boot 3.2.12 / Java 17 / Spring Data JPA |
| 数据库 | H2 文件库（`./data/museum.mv.db`），JPA 持久化 |
| 审核 | 本地 DFA 规则词表 + 人工复审队列（**没有接入真实大模型审核**） |
| 语音识别 | 百度短语音识别（可选）；未配置时语音回音**转人工** |
| 部署 | 单台 Linux + Caddy（HTTPS / 静态文件 / 反向代理）+ systemd |

## 目录结构

```
├─ code/                     ← 全部代码
│  ├─ backend/               Spring Boot 单体服务（src/main/java/com/starmuseum/）
│  ├─ frontend/              Vue 3 + Vite
│  ├─ tools/gen-audio.mjs    演示音频生成脚本（合成信号，不是真录音）
│  └─ deploy/                部署模板：Caddyfile / systemd unit / 环境变量示例
├─ docs/
│  ├─ PRODUCT.md · 产品定义.md
│  ├─ 设计参考/              视觉参考稿与实现截图
│  ├─ 视频素材/              生成原片与最终成片
│  ├─ 本轮实施结果.md         这一轮改了什么、怎么验证、哪些没做
│  ├─ 部署说明.md             服务器上的完整落地步骤（含备份与恢复）
│  ├─ 验收演示.md             A 作者 / B 听众 / C 管理员 三会话演示脚本
│  ├─ 数据库说明.md           表、字段、关系、演示查询
│  ├─ 团队贡献.md             每人负责什么、对应哪些文件与提交
│  ├─ 素材来源.md             音频素材的来源与授权登记
│  └─ README.md               文档分类索引（含评审报告与历史资料）
└─ README.md                 项目总说明
```

## 快速开始（本机）

### 1. 生成演示音频（只需一次）

```bash
cd code
node tools/gen-audio.mjs
```

在 `code/backend/data/audio/` 下生成合成占位 WAV，并写出实际时长与波形到
`code/backend/src/main/resources/seed/audio-manifest.json`。**没有这一步，页面有数据但播放 404。**

### 2. 启动后端

```bash
cd code/backend
mvn spring-boot:run          # http://127.0.0.1:8081
```

首次启动自动建表并写入演示数据。
验证：<http://127.0.0.1:8081/api/ping> 应返回馆藏规模。

要进管理端，先配口令再启动（**留空 = 管理后台关闭**）：

```powershell
$env:ADMIN_TOKEN = "本地随便一段足够长的口令"
mvn spring-boot:run
```

### 3. 启动前端

```bash
cd code/frontend
npm install
npm run dev                  # http://127.0.0.1:5174
```

打开 <http://127.0.0.1:5174>，管理端在 <http://127.0.0.1:5174/admin>。

> 录音需要浏览器麦克风权限。`localhost` 属于安全上下文，可以直接用；
> 用其他设备通过局域网 IP 访问时必须走 HTTPS，否则**能看不能录**。

### 4. 跑测试与构建

```bash
cd code/backend && mvn test        # 86 个用例
cd code/frontend && npm run build  # 产物在 dist/
```

## 部署到服务器

单台 Linux：Caddy 提供 HTTPS + 前端静态文件，Spring Boot 以 systemd 常驻，
H2 文件库与音频目录固定在服务器磁盘。

- 完整步骤：[`docs/部署说明.md`](docs/部署说明.md)
- 模板文件：[`code/deploy/Caddyfile`](code/deploy/Caddyfile)、[`code/deploy/starmuseum.service`](code/deploy/starmuseum.service)、[`code/deploy/starmuseum.env.example`](code/deploy/starmuseum.env.example)
- 演示脚本：[`docs/验收演示.md`](docs/验收演示.md)

**没有服务器访问权限或域名时，只能完成部署材料，不能宣称「已部署成功」。**

## 环境变量

| 变量 | 作用 |
| --- | --- |
| `ADMIN_TOKEN` | 管理口令。留空 = 管理后台关闭（安全默认） |
| `SPRING_DATASOURCE_URL` | H2 文件库地址，部署时用**绝对路径** |
| `MUSEUM_AUDIO_ROOT` | 音频根目录，部署时用**绝对路径** |
| `SERVER_ADDRESS` / `SERVER_PORT` | 监听地址与端口（部署时 `127.0.0.1:8081`） |
| `ASR_PROVIDER` | `mock`（默认，语音回音转人工）或 `baidu` |
| `BAIDU_APP_ID` / `BAIDU_API_KEY` / `BAIDU_SECRET_KEY` | 百度短语音识别密钥，只放服务器 |
| `MUSEUM_SIGN_PROVIDER` / `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL` | 展签远程压缩（可选，失败会退回本地压缩） |

## 这一轮改动摘要

- **完整 CRUD**：投稿 → 我的声音 → 编辑（同一件、重审、编号不变）→ 删除（清全部关联音频）。
- **多端互动**：通知表 + 5 秒轮询。投稿进管理端、审核结果回作者、回音只在实际公开时送达。
- **回音收件箱与状态**：发送人能看到自己的回音在哪、为什么没通过；失败不清空草稿。
- **管理端能听**：待审语音回音带 `audioUrl`，没有转写文本也能直接播。
- **审核如实**：标题／故事／地点分字段过规则；演唱类强制人工；删掉假的「AI 路」。
- **不再造假识别**：没配真实 ASR 时语音回音转人工，不生成伪转写。
- **工程修复**：编号改用数据库序列（删号不重用）、删除清全部语音文件、打捞按类型去重、馆藏检索。
- **部署材料**：`deploy/` 三个文件 + 部署/恢复文档。

细节、验证命令与未完成事项：**[`docs/本轮实施结果.md`](docs/本轮实施结果.md)**。

## 文档与视频素材

- [文档索引](docs/README.md)：产品定义、开发部署、验收、设计方案、评审与历史资料。
- [视频素材目录](docs/视频素材/README.md)：后续生成原片、剪辑成片及命名约定。
- [视频动效设计方案](docs/视频动效设计方案.md)：当前打捞视频的分镜与接入建议，尚未实施。