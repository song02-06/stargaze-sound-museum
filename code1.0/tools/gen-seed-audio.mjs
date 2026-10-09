/**
 * 生成占位音频 —— 让 Demo 在断网、没有任何素材的情况下也能完整演示。
 *
 * 用法：node tools/gen-seed-audio.mjs
 * 输出：backend/data/audio/{bottle,heritage}/*.wav（16k 单声道，正好是云 ASR 要求的格式）
 *
 * 注意：这些是「合成音」，不是真实录音，也没有版权问题。
 * 真做历史声音彩蛋时，请按《项目计划书》第九章换成有明确出处的素材，
 * 并同步登记到 docs/素材来源.md。
 */

import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const SAMPLE_RATE = 16000
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')

/** 一个「音色」= 基频 + 泛音比例 + 包络长短 + 噪声量 */
const RECIPES = {
  bottle: [
    { name: 'seed-1.wav', base: 320, seconds: 6, harmonics: [1, 0.35, 0.12], noise: 0.02, wobble: 3.1 },
    { name: 'seed-2.wav', base: 262, seconds: 5, harmonics: [1, 0.5, 0.2], noise: 0.02, wobble: 2.2 },
    { name: 'seed-3.wav', base: 196, seconds: 7, harmonics: [1, 0.25, 0.4], noise: 0.03, wobble: 1.4 },
    { name: 'seed-4.wav', base: 294, seconds: 5, harmonics: [1, 0.15, 0.05], noise: 0.02, wobble: 4.4 },
    { name: 'seed-5.wav', base: 349, seconds: 8, harmonics: [1, 0.4, 0.22], noise: 0.03, wobble: 0.9 }
  ],
  heritage: [
    { name: 'city-dusk.wav', base: 110, seconds: 9, harmonics: [1, 0.6, 0.35, 0.15], noise: 0.16, wobble: 0.6 },
    { name: 'old-clock.wav', base: 523, seconds: 10, harmonics: [1, 0.1, 0.05], noise: 0.05, wobble: 0.3, chime: true },
    { name: 'harbor-fog.wav', base: 82, seconds: 9, harmonics: [1, 0.75, 0.4], noise: 0.22, wobble: 0.35 },
    { name: 'dial-phone.wav', base: 660, seconds: 7, harmonics: [1, 0.2, 0.6, 0.25], noise: 0.08, wobble: 0, clicks: true },
    { name: 'projector.wav', base: 140, seconds: 8, harmonics: [1, 0.45, 0.7, 0.4], noise: 0.18, wobble: 12 }
  ]
}

function synthesize(recipe) {
  const total = Math.floor(recipe.seconds * SAMPLE_RATE)
  const samples = new Float32Array(total)
  const harmonicSum = recipe.harmonics.reduce((a, b) => a + b, 0)

  for (let i = 0; i < total; i++) {
    const t = i / SAMPLE_RATE
    let value = 0

    for (let h = 0; h < recipe.harmonics.length; h++) {
      const freq = recipe.base * (h + 1)
      value += Math.sin(2 * Math.PI * freq * t) * recipe.harmonics[h]
    }
    value /= harmonicSum

    // 缓慢的音高抖动，让它听起来像「有生命的声音」而不是纯机器音
    if (recipe.wobble) {
      value *= 1 + 0.12 * Math.sin(2 * Math.PI * recipe.wobble * t)
    }
    if (recipe.noise) {
      value += (Math.random() * 2 - 1) * recipe.noise
    }
    if (recipe.chime) {
      // 每 2 秒敲一下，模拟整点报时
      const phase = t % 2
      value += Math.exp(-phase * 6) * Math.sin(2 * Math.PI * recipe.base * 2 * t) * 0.7
    }
    if (recipe.clicks) {
      // 拨号盘的回弹咔哒声
      const phase = t % 0.7
      value += Math.exp(-phase * 60) * (Math.random() * 2 - 1) * 0.8
    }

    // 整体淡入淡出，避免首尾爆音
    const fade = Math.min(1, t / 0.25, (recipe.seconds - t) / 0.4)
    samples[i] = Math.max(-1, Math.min(1, value * 0.55 * fade))
  }
  return samples
}

function encodeWav(samples) {
  const buffer = Buffer.alloc(44 + samples.length * 2)
  const writeAscii = (offset, text) => buffer.write(text, offset, 'ascii')

  writeAscii(0, 'RIFF')
  buffer.writeUInt32LE(36 + samples.length * 2, 4)
  writeAscii(8, 'WAVE')
  writeAscii(12, 'fmt ')
  buffer.writeUInt32LE(16, 16)
  buffer.writeUInt16LE(1, 20)              // PCM
  buffer.writeUInt16LE(1, 22)              // 单声道
  buffer.writeUInt32LE(SAMPLE_RATE, 24)
  buffer.writeUInt32LE(SAMPLE_RATE * 2, 28)
  buffer.writeUInt16LE(2, 32)
  buffer.writeUInt16LE(16, 34)
  writeAscii(36, 'data')
  buffer.writeUInt32LE(samples.length * 2, 40)

  for (let i = 0; i < samples.length; i++) {
    buffer.writeInt16LE(Math.round(samples[i] * 32767), 44 + i * 2)
  }
  return buffer
}

let generated = 0
for (const [folder, recipes] of Object.entries(RECIPES)) {
  const dir = resolve(ROOT, 'backend', 'data', 'audio', folder)
  mkdirSync(dir, { recursive: true })
  for (const recipe of recipes) {
    const file = resolve(dir, recipe.name)
    writeFileSync(file, encodeWav(synthesize(recipe)))
    generated += 1
    console.log(`generated ${folder}/${recipe.name}  (${recipe.seconds}s)`)
  }
}

console.log(`\n完成：共生成 ${generated} 个占位音频文件，位于 backend/data/audio/`)
