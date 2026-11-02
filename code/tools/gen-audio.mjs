/**
 * 生成演示音频 —— 全项目共用一份脚本，两个消费方：
 *
 *   node tools/gen-audio.mjs                  → backend/（后端持有并提供音频）
 *   node tools/gen-audio.mjs --target frontend → frontend/public/（前端独立演示用）
 *
 * 产出：
 *   <target>/data/audio/{exhibits,heritage,echoes}/*.wav
 *   <target>/.../audio-manifest.json     每条音频的真实时长 + 真实波形
 *
 * ⚠️ 全部是**合成信号，不是真实录音**，也没有版权问题。
 *    正式版本必须按 PRODUCT.md 的版权红线，把 heritage 换成公有领域原作，
 *    并逐条登记来源（名称 / 链接 / 授权状态）。
 *
 * 波形是真的：界面上画的每一条声波痕迹都取自这里生成的 PCM 采样，
 * 不是随手画的装饰线。换音频 → 波形跟着变。
 */

import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const SR = 16000
const TRACE_POINTS = 720
const HERE = resolve(dirname(fileURLToPath(import.meta.url)))
const PROJECT = resolve(HERE, '..')

const target = process.argv.includes('--target')
  ? process.argv[process.argv.indexOf('--target') + 1]
  : 'backend'

const TARGETS = {
  backend: {
    audio: resolve(PROJECT, 'backend', 'data', 'audio'),
    manifest: resolve(PROJECT, 'backend', 'src', 'main', 'resources', 'seed', 'audio-manifest.json')
  },
  frontend: {
    audio: resolve(PROJECT, 'frontend', 'public', 'audio'),
    manifest: resolve(PROJECT, 'frontend', 'src', 'data', 'audio-manifest.json')
  }
}

if (!TARGETS[target]) {
  console.error(`未知 target：${target}（可选 backend / frontend）`)
  process.exit(1)
}

function mulberry(seed) {
  let a = seed >>> 0
  return function () {
    a = (a + 0x6d2b79f5) >>> 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/* ------------------------------------------------------------------ */
/* 配方：每条音频 = 基音 + 泛音 + 漂移 + 噪声床 + 若干事件            */
/* ------------------------------------------------------------------ */

const RECIPES = {
  exhibits: [
    {
      id: 'rain', seconds: 34, base: 148, seed: 1101, noise: 0.20, drift: 0.06,
      harmonics: [1, 0.45, 0.28, 0.14],
      events: [
        { at: 3, kind: 'drop' }, { at: 7.5, kind: 'drop' }, { at: 12, kind: 'gust' },
        { at: 18, kind: 'drop' }, { at: 22, kind: 'drop' }, { at: 27, kind: 'gust' }
      ]
    },
    {
      id: 'store', seconds: 28, base: 92, seed: 1202, noise: 0.14, drift: 0.03,
      harmonics: [1, 0.6, 0.3, 0.2],
      events: [
        { at: 2, kind: 'chime' }, { at: 6, kind: 'click' }, { at: 11, kind: 'click' },
        { at: 16, kind: 'chime' }, { at: 21, kind: 'click' }
      ]
    },
    {
      id: 'typhoon', seconds: 38, base: 64, seed: 1303, noise: 0.30, drift: 0.09,
      harmonics: [1, 0.8, 0.5, 0.25],
      events: [
        { at: 4, kind: 'gust' }, { at: 13, kind: 'gust' }, { at: 21, kind: 'gust' },
        { at: 30, kind: 'gust' }
      ]
    },
    {
      id: 'well', seconds: 26, base: 210, seed: 1404, noise: 0.10, drift: 0.05,
      harmonics: [1, 0.3, 0.12],
      events: [
        { at: 2.5, kind: 'drop' }, { at: 5.5, kind: 'drop' }, { at: 9, kind: 'drop' },
        { at: 14, kind: 'drop' }, { at: 18, kind: 'drop' }
      ]
    },
    {
      id: 'bus', seconds: 31, base: 118, seed: 1505, noise: 0.16, drift: 0.04,
      harmonics: [1, 0.5, 0.35, 0.18],
      events: [
        { at: 3, kind: 'voice' }, { at: 12, kind: 'voice' }, { at: 22, kind: 'voice' },
        { at: 26, kind: 'click' }
      ]
    }
  ],
  heritage: [
    {
      id: '1941-07', seconds: 46, base: 104, seed: 2101, noise: 0.24, drift: 0.02,
      harmonics: [1, 0.55, 0.4, 0.22],
      events: [
        { at: 3, kind: 'voice' }, { at: 9, kind: 'voice' }, { at: 15, kind: 'click' },
        { at: 20, kind: 'voice' }, { at: 29, kind: 'voice' }, { at: 38, kind: 'voice' }
      ]
    },
    {
      id: '1954-03', seconds: 41, base: 132, seed: 2202, noise: 0.19, drift: 0.03,
      harmonics: [1, 0.42, 0.25, 0.12],
      events: [
        { at: 4, kind: 'voice' }, { at: 12, kind: 'voice' }, { at: 21, kind: 'voice' },
        { at: 32, kind: 'voice' }
      ]
    },
    {
      id: '1924-11', seconds: 33, base: 176, seed: 2303, noise: 0.26, drift: 0.06,
      harmonics: [1, 0.65, 0.45],
      events: [
        { at: 2.5, kind: 'chime' }, { at: 8, kind: 'click' }, { at: 14, kind: 'chime' },
        { at: 22, kind: 'click' }, { at: 28, kind: 'chime' }
      ]
    }
  ],
  // 演示用的语音回音。真接后端之后，这些会换成用户自己录的那一段。
  echoes: [
    {
      id: 'echo-a', seconds: 12, base: 188, seed: 3101, noise: 0.17, drift: 0.08,
      harmonics: [1, 0.6, 0.3, 0.16],
      events: [
        { at: 1, kind: 'voice' }, { at: 4, kind: 'voice' }, { at: 7.5, kind: 'voice' }
      ]
    },
    {
      id: 'echo-b', seconds: 9, base: 236, seed: 3202, noise: 0.21, drift: 0.11,
      harmonics: [1, 0.5, 0.35],
      events: [
        { at: 0.8, kind: 'voice' }, { at: 3.6, kind: 'voice' }, { at: 6, kind: 'click' }
      ]
    }
  ]
}

/* ------------------------------------------------------------------ */
/* 打捞的三记声音                                                      */
/* ------------------------------------------------------------------ */

/**
 * 打捞是点击触发的，浏览器允许出声。三记按仪式的节拍落，全部在幕布的一生之内：
 *
 *   0ms      descend  垂下 —— 一记低频起音 + 空气感
 *   800ms    catch    勾住 —— 一记很轻的实音（金属泛音 + 水感）
 *   1500ms   lift     提起 —— 一声很轻的上行，然后退干净
 *
 * 三记在 2.4 秒内全部收干净 —— 展品自己的声音大约在 2.4 秒时起来，
 * 那之后不该再有任何衬底和它抢。之后什么都不放。
 * 全部是合成信号 —— 不出圈、不花钱、没有版权问题，和其余占位音频同源。
 */

const RITUAL = [
  { id: 'descend', seconds: 1.8, seed: 4101, render: renderDescend },
  { id: 'catch', seconds: 1.6, seed: 4202, render: renderCatch },
  { id: 'lift', seconds: 0.9, seed: 4303, render: renderLift }
]

function renderDescend(seconds, rnd) {
  const N = Math.floor(seconds * SR)
  const out = new Float32Array(N)
  let lp = 0

  for (let i = 0; i < N; i++) {
    const t = i / SR
    // 低频：70 → 46Hz 下滑，60ms 起音，然后指数衰减
    const f = 70 - 24 * Math.min(1, t / 0.9)
    const env = Math.min(1, t / 0.06) * Math.exp(-Math.max(0, t - 0.06) * 2.6)
    let v = Math.sin(2 * Math.PI * f * t) * 0.75 * env

    // 空气感：白噪过一阶低通，通带随时间收窄
    const noise = rnd() * 2 - 1
    lp += (noise - lp) * (0.05 + 0.25 * Math.exp(-t * 1.6))
    v += lp * 0.5 * Math.min(1, t / 0.25) * Math.exp(-Math.max(0, t - 0.25) * 1.9)

    out[i] = v
  }
  return out
}

function renderCatch(seconds, rnd) {
  const N = Math.floor(seconds * SR)
  const out = new Float32Array(N)
  // 非整数比的三根分音 —— 听起来是「金属」而不是「音符」
  const partials = [
    { f: 523.25, a: 1.0, d: 3.4 },
    { f: 786.5, a: 0.42, d: 4.6 },
    { f: 1231.0, a: 0.22, d: 6.2 }
  ]

  for (let i = 0; i < N; i++) {
    const t = i / SR
    let v = 0
    for (const p of partials) {
      v += Math.sin(2 * Math.PI * p.f * t) * p.a * Math.exp(-t * p.d)
    }

    // 水感：落在实音前面一点点的一记下滑短音
    const dt = t - 0.02
    if (dt >= 0) {
      const f = 900 * Math.exp(-dt * 6) + 220
      v += Math.sin(2 * Math.PI * f * dt) * 0.35 * Math.exp(-dt * 9)
    }

    out[i] = v * Math.min(1, t / 0.005)
  }
  return out
}

function renderLift(seconds, rnd) {
  const N = Math.floor(seconds * SR)
  const out = new Float32Array(N)

  for (let i = 0; i < N; i++) {
    const t = i / SR
    // 上行：180 → 310Hz，缓起缓落，音量明显低于前两记
    const f = 180 + 130 * Math.min(1, t / 0.7)
    const env = Math.min(1, t / 0.25) * Math.exp(-Math.max(0, t - 0.25) * 1.5)
    let v = Math.sin(2 * Math.PI * f * t) * 0.6 * env
    v += Math.sin(2 * Math.PI * f * 2.01 * t) * 0.14 * env
    v += (rnd() * 2 - 1) * 0.02 * env
    out[i] = v
  }
  return out
}

/* ------------------------------------------------------------------ */
/* 埋下的那一记：落定                                                  */
/* ------------------------------------------------------------------ */

/**
 * 打捞是三记、向上、勾住、末段归位；埋下是**一记**、向下、收束、落定即静。
 * 两边要能听出是同一套声音，所以用同一个合成器、同一套收尾。
 * 只有这一记，之后什么都不放 —— 位置留给那件东西自己。
 */

const BURY = [{ id: 'settle', seconds: 1.3, seed: 5104, render: renderSettle }]

function renderSettle(seconds, rnd) {
  const N = Math.floor(seconds * SR)
  const out = new Float32Array(N)
  let lp = 0

  for (let i = 0; i < N; i++) {
    const t = i / SR
    // 低频：66 → 43Hz 往下走，30ms 起音，约 0.9s 收束
    const f = 66 - 23 * Math.min(1, t / 0.55)
    const env = Math.min(1, t / 0.03) * Math.exp(-Math.max(0, t - 0.03) * 3.1)
    let v = Math.sin(2 * Math.PI * f * t) * 0.8 * env

    // 落定的那一下：一记很短的闷响，像东西被轻轻放在桌面上
    const thud = Math.exp(-t * 42) * (0.5 + 0.5 * Math.sin(2 * Math.PI * 130 * t))
    v += (rnd() * 2 - 1) * thud * 0.35

    // 空气收尾：一阶低通，通带随时间收窄
    const noise = rnd() * 2 - 1
    lp += (noise - lp) * (0.03 + 0.1 * Math.exp(-t * 2.2))
    v += lp * 0.35 * Math.exp(-Math.max(0, t - 0.1) * 3.4)

    out[i] = v
  }
  return out
}

/** 归一化 + 首尾淡入淡出。和 synthesize 用的是同一套收尾，避免爆音。 */
function finish(out, seconds) {
  const N = out.length
  let peak = 0
  for (let i = 0; i < N; i++) peak = Math.max(peak, Math.abs(out[i]))
  const gain = peak > 0 ? 0.85 / peak : 1
  for (let i = 0; i < N; i++) {
    const t = i / SR
    const fade = Math.min(1, t / 0.02, (seconds - t) / 0.25)
    out[i] = Math.max(-1, Math.min(1, out[i] * gain * Math.max(0, fade)))
  }
  return out
}

function synthesize(r) {
  const N = Math.floor(r.seconds * SR)
  const out = new Float32Array(N)
  const rnd = mulberry(r.seed)
  const harmSum = r.harmonics.reduce((a, b) => a + b, 0)

  for (let i = 0; i < N; i++) {
    const t = i / SR
    let v = 0
    const bend = Math.sin(2 * Math.PI * 0.11 * t + r.seed) * 0.5
    for (let h = 0; h < r.harmonics.length; h++) {
      v += Math.sin(2 * Math.PI * r.base * (h + 1) * t + bend) * r.harmonics[h]
    }
    v /= harmSum
    v *= 0.72 + 0.28 * Math.sin(2 * Math.PI * r.drift * t + r.seed * 0.7)
    v += (rnd() * 2 - 1) * r.noise * (0.6 + 0.4 * Math.sin(2 * Math.PI * 0.05 * t))

    for (const e of r.events) {
      const dt = t - e.at
      if (dt < 0) continue
      if (e.kind === 'chime') v += Math.exp(-dt * 4.5) * Math.sin(2 * Math.PI * r.base * 2.02 * t) * 0.7
      else if (e.kind === 'click') v += Math.exp(-dt * 70) * (rnd() * 2 - 1) * 0.8
      else if (e.kind === 'gust') v += Math.exp(-Math.pow((dt - 1.4) / 1.7, 2)) * (rnd() * 2 - 1) * 0.55
      else if (e.kind === 'drop') v += Math.exp(-dt * 10) * Math.sin(2 * Math.PI * r.base * 3.4 * dt) * 0.65
      else if (e.kind === 'voice') v += Math.exp(-Math.pow((dt - 0.9) / 0.95, 2)) * Math.sin(2 * Math.PI * r.base * 1.5 * t) * 0.5
    }
    out[i] = v
  }

  let peak = 0
  for (let i = 0; i < N; i++) peak = Math.max(peak, Math.abs(out[i]))
  const gain = peak > 0 ? 0.9 / peak : 1
  for (let i = 0; i < N; i++) {
    const t = i / SR
    const fade = Math.min(1, t / 0.4, (r.seconds - t) / 0.6)
    out[i] = Math.max(-1, Math.min(1, out[i] * gain * Math.max(0, fade)))
  }
  return out
}

/** 每段取绝对值最大的采样、保留符号 —— 就是示波器上那条线 */
function extractTrace(samples, points) {
  const n = samples.length
  const trace = []
  for (let p = 0; p < points; p++) {
    const a = Math.floor((p * n) / points)
    const b = Math.max(a + 1, Math.floor(((p + 1) * n) / points))
    let best = 0
    for (let i = a; i < b; i++) {
      if (Math.abs(samples[i]) > Math.abs(best)) best = samples[i]
    }
    trace.push(Number(best.toFixed(3)))
  }
  return trace
}

function encodeWav(samples) {
  const buffer = Buffer.alloc(44 + samples.length * 2)
  buffer.write('RIFF', 0, 'ascii')
  buffer.writeUInt32LE(36 + samples.length * 2, 4)
  buffer.write('WAVE', 8, 'ascii')
  buffer.write('fmt ', 12, 'ascii')
  buffer.writeUInt32LE(16, 16)
  buffer.writeUInt16LE(1, 20)
  buffer.writeUInt16LE(1, 22)
  buffer.writeUInt32LE(SR, 24)
  buffer.writeUInt32LE(SR * 2, 28)
  buffer.writeUInt16LE(2, 32)
  buffer.writeUInt16LE(16, 34)
  buffer.write('data', 36, 'ascii')
  buffer.writeUInt32LE(samples.length * 2, 40)
  for (let i = 0; i < samples.length; i++) {
    buffer.writeInt16LE(Math.round(samples[i] * 32767), 44 + i * 2)
  }
  return buffer
}

// ritual 不是馆藏内容，是界面的声音 —— 后端种子只读 exhibits/heritage/echoes，
// 这里登记一份只是为了「所有占位音频都在这份清单里」这件事成立。
const manifest = { exhibits: [], heritage: [], echoes: [], ritual: [], bury: [], synthetic: true }
let bytes = 0

for (const [group, list] of Object.entries(RECIPES)) {
  const dir = resolve(TARGETS[target].audio, group)
  mkdirSync(dir, { recursive: true })

  for (const recipe of list) {
    const samples = synthesize(recipe)
    const wav = encodeWav(samples)
    writeFileSync(resolve(dir, `${recipe.id}.wav`), wav)
    bytes += wav.length

    manifest[group].push({
      id: recipe.id,
      src: `/audio/${group}/${recipe.id}.wav`,
      seconds: recipe.seconds,
      trace: extractTrace(samples, TRACE_POINTS)
    })
    console.log(`  ${group}/${recipe.id}.wav   ${recipe.seconds}s   ${(wav.length / 1024).toFixed(0)} KB`)
  }
}

// 界面的声音（打捞三记 + 埋下一记）：短、自带包络，不需要 trace
for (const [group, list] of [
  ['ritual', RITUAL],
  ['bury', BURY]
]) {
  const dir = resolve(TARGETS[target].audio, group)
  mkdirSync(dir, { recursive: true })

  for (const recipe of list) {
    const samples = finish(recipe.render(recipe.seconds, mulberry(recipe.seed)), recipe.seconds)
    const wav = encodeWav(samples)
    writeFileSync(resolve(dir, `${recipe.id}.wav`), wav)
    bytes += wav.length

    manifest[group].push({
      id: recipe.id,
      src: `/audio/${group}/${recipe.id}.wav`,
      seconds: recipe.seconds
    })
    console.log(`  ${group}/${recipe.id}.wav   ${recipe.seconds}s   ${(wav.length / 1024).toFixed(0)} KB`)
  }
}

mkdirSync(dirname(TARGETS[target].manifest), { recursive: true })
writeFileSync(TARGETS[target].manifest, JSON.stringify(manifest))

const total = Object.values(manifest).filter(Array.isArray).reduce((n, l) => n + l.length, 0)
console.log(`\n目标：${target}`)
console.log(`完成：${total} 条音频，合计 ${(bytes / 1024 / 1024).toFixed(1)} MB`)
console.log(`音频目录：${TARGETS[target].audio}`)
console.log(`波形数据：${TARGETS[target].manifest}`)
console.log('\n⚠️  全部为合成占位音频，不是真实录音。上线前须替换并登记来源。')
