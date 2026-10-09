$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$p = '项目计划书.md'
$lines = [System.Collections.Generic.List[string]]::new([string[]][System.IO.File]::ReadAllLines($p, [System.Text.Encoding]::UTF8))

$new = @'
## 十、参考项目清单（均已联网核实存在，星标为核实当日数据）

> 使用原则：**能用依赖就不抄代码，能抄代码就不整包 fork**。抄代码时在文件头注明来源与 License，并在论文的参考文献／开源许可里列出。
> 重要前提：这个项目的产品形态是你自己的，GitHub 上**不存在可以整包套用的同类项目**。所以下面按"你要写哪一块"分组，每条都写清楚该抄什么。

### 10.1 后端骨架与工程结构

| 仓库 | 星标 | 抄什么 |
| --- | --- | --- |
| `itbaima-study/SpringBoot-Vue-Template-Jwt` | 372 | 最贴近的起步骨架：目录分层、统一返回体、全局异常处理；不需要 JWT 就删掉 |
| `yangzongzhuan/RuoYi-Vue` | 高 | 只在做管理后台时借鉴：表格页、分页、权限拦截；**不要整包 fork**（太重，答辩讲不清） |
| `diyhi/bbs` | 1087 | Spring Boot 4.x 社区系统：内容审核、举报、后台管理的表结构设计 |
| `ZHENFENG13/spring-boot-projects` | 5768 | Spring Boot 实战教程合集，卡住时当手册查 |

### 10.2 AI 接入（DeepSeek）

| 仓库 | 星标 | 抄什么 |
| --- | --- | --- |
| `pig-mesh/deepseek4j` | 754 | DeepSeek 的 Java SDK：比手写 `WebClient` 省事，注意它封装了流式输出与函数调用 |
| `liyupi/yu-ai-code-mother` | 1944 | Spring Boot 3 + LangChain4j 的 AI 应用：看它怎么组织 Prompt、限流与异常降级 |
| `LangChat/langchat` | 1281 | 多模型接入的工程结构，想留"以后换模型"的扩展性时参考 |
| `alibaba/spring-ai-alibaba` / `spring-projects/spring-ai` | 10855 / 高 | 想写"基于 Spring AI"这个亮点时用；时间紧就用 `WebClient` 直连 OpenAI 兼容接口 |

### 10.3 语音识别接入

| 仓库 | 星标 | 抄什么 |
| --- | --- | --- |
| `Baidu-AIP/java-sdk` | 高 | **主线**：官方 Java SDK，`client.asr(bytes, "wav", 16000, null)` 三行拿到结果 |
| `AkhileshSharmaa/audio_transcription_app` | 0 | Spring Boot 的音频转写服务示例：看它"上传 → 转写 → 返回"的接口设计 |
| 腾讯云 `tencentcloud-sdk-java-asr` | — | 备选服务商（免费额度每月 5000 次） |
| `FunAudioLLM/SenseVoice`、`modelscope/FunASR` | 高 | 本项目已不用，写进"未来工作"作为离线方案 |

### 10.4 审核机制

| 仓库 | 星标 | 抄什么 |
| --- | --- | --- |
| `houbb/sensitive-word` | 6047 | **直接当 Maven 依赖**，规则路的核心；参考它的词库组织与白名单做法 |
| `diyhi/bbs` | 1087 | 社区系统的人工复审流程与后台交互设计 |

### 10.5 前端视觉（星轨 / 流星雨）

| 仓库 | 星标 | 抄什么 |
| --- | --- | --- |
| `Aizener/three-template` | 72 | **vite + vue3 + three.js 空白模板**，直接当作你的前端起点最省事 |
| `TotoroZuo/3d-car-showcase` | 122 | Vue3 + Three.js 完整项目的组织方式（场景组件拆分、性能处理） |
| `mrdoob/three.js` | 极高 | 官方 examples 里的 Points、ShaderMaterial、postprocessing（Bloom） |
| `bosombaby/web3d-product` | 35 | three.js 粒子特效案例集，星轨与流星的粒子写法可直接借鉴 |
| `TimoGxj/Star-Track-Chronicles` | 1 | 同样以"星轨"为名的 Three.js 项目，只作视觉参考（星标很低） |
| `Sean-Bradley/Three.js-TypeScript-Boilerplate` | 高 | 工程化模板：场景初始化、渲染循环、resize 处理 |
| `nikonikoCW/Meteor3DEditor` | 69 | Vue3 + Three.js 的 3D 场景编辑器，看 Vue 与 Three.js 的通信方式 |

### 10.6 音频驱动视觉与波形

| 仓库 | 星标 | 抄什么 |
| --- | --- | --- |
| `unconed/ThreeAudio.js` | 542 | **"音频驱动星轨"的关键参考**：频谱 → 几何与亮度的映射 |
| `wayou/3D_Audio_Spectrum_VIsualizer` | 176 | 频谱驱动的 3D 可视化实现 |
| `GraemeFulton/ThreeJS-3D-Music-Visualizer` | 8 | 代码量小，快速看懂"音频参数 → 视觉参数"的映射 |
| `staskobzar/vue-audio-visual`、`meer-sagor/wavesurfer-vue` | 799 / 38 | Vue 波形组件，想省时间可直接安装使用 |

### 10.7 产品形态参考（不抄代码，抄"感觉"）

| 仓库 | 星标 | 看什么 |
| --- | --- | --- |
| `kwinkle505/TimeCorridor`（时空回廊） | 5 | 与你的定位最接近的情感治愈平台：树洞、时光胶囊、留言墙的交互与文案 |
| `OxcWos/museum-of-lost-sounds` | 0 | "消失的声音博物馆"——把日常声音当作馆藏的概念，与你的历史声音档案思路相通 |
| `BugFor-Pings/nimingly` | 5 | 匿名留言程序的流程设计（提交 → 审核 → 展示） |

### 10.8 数据准备工具

| 仓库 | 星标 | 抄什么 |
| --- | --- | --- |
| `rany2/edge-tts` | 高 | 免费 TTS，批量合成约 150 条"模拟用户留言" |

### 10.9 建议的组合（避免选择困难）

| 用途 | 选谁 |
| --- | --- |
| 后端骨架 | `itbaima-study/SpringBoot-Vue-Template-Jwt`（删掉 JWT） |
| 前端起点 | `Aizener/three-template` |
| 星轨视觉 | `mrdoob/three.js` 官方示例 + `bosombaby/web3d-product` |
| 音频联动 | `unconed/ThreeAudio.js` |
| 审核 | `houbb/sensitive-word`（当依赖用） |
| 语音识别 | `Baidu-AIP/java-sdk`（当依赖用） |
| DeepSeek | 先手写 `WebClient`，嫌麻烦再换 `deepseek4j` |

'@

$s = -1; $e = -1
for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -match '^## 十、参考项目清单') { $s = $i; break } }
if ($s -ge 0) { for ($i = $s + 1; $i -lt $lines.Count; $i++) { if ($lines[$i] -match '^## 十一、分阶段实施路线') { $e = $i; break } } }
if ($s -lt 0 -or $e -lt 0) { Write-Host "MISS s=$s e=$e"; exit 1 }

$lines.RemoveRange($s, $e - $s)
$lines.InsertRange($s, [string[]]($new -split "`r?`n"))
[System.IO.File]::WriteAllLines($p, $lines, $utf8)
Write-Host "第十章已重写：原 L$($s+1)-L$e"
