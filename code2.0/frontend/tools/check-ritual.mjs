/**
 * 打捞的幕布与三记声音 —— 一次性体检。
 *
 * 打捞那几秒里只有两件事：幕布（垂线／光点／涟漪）和三记声音。
 * 声音是最容易悄悄坏掉的那一半 —— 路径改了、被自动播放策略拦了、
 * 系统开了「减少动态效果」却还在响 —— 所以这里量的是它：
 *
 *   1）正常用户：三记都被真的拉下来，幕布按时收起，落点的展品随后出声；
 *   2）减少动态效果：一声都不响，而且收场要快。
 *
 * 用法：先跑起来（另开一个终端 npm run dev），然后
 *   node tools/check-ritual.mjs
 */

import { existsSync } from 'node:fs'
import puppeteer from 'puppeteer-core'

const BASE = process.env.QA_BASE || 'http://127.0.0.1:5174'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function chromePath() {
  const candidates = [
    process.env.CHROME_PATH,
    `${process.env.ProgramFiles}\\Google\\Chrome\\Application\\chrome.exe`,
    `${process.env['ProgramFiles(x86)']}\\Google\\Chrome\\Application\\chrome.exe`,
    `${process.env.ProgramFiles}\\Microsoft\\Edge\\Application\\msedge.exe`
  ].filter(Boolean)
  return candidates.find((p) => existsSync(p))
}

const browser = await puppeteer.launch({
  executablePath: chromePath(),
  headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage', '--autoplay-policy=no-user-gesture-required']
})

const problems = []

function watchPage(page, label) {
  page.on('console', (m) => {
    if (m.type() === 'error') problems.push(`控制台错误（${label}）：${m.text().slice(0, 140)}`)
  })
  page.on('pageerror', (e) => problems.push(`未捕获异常（${label}）：${String(e).slice(0, 140)}`))
  page.on('response', (r) => {
    if (r.status() >= 400) problems.push(`请求失败（${label}）${r.status()}：${r.url()}`)
  })
}

/* ---------------- 一、正常用户 ---------------- */

const page = await browser.newPage()
watchPage(page, '正常')
await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 })
await page.goto(`${BASE}/`, { waitUntil: 'networkidle2' })
await sleep(1400)

// 真鼠标事件：脚本 click() 不算用户手势，会连声音一起废掉
await page.click('.btn.primary')
await sleep(200)
const during = await page.evaluate(() => ({ up: !!document.querySelector('.veil') }))

const glow = () =>
  page.evaluate(() => {
    const el = document.querySelector('.afterglow')
    return el ? Number(getComputedStyle(el).opacity) : null
  })

await sleep(1300) // ≈1500ms：线已落到底，余晖最亮的时候
const glowPeak = await glow()
await sleep(1000) // ≈2500ms：应该已经退成一条很淡的余晖
const glowEnd = await glow()

await sleep(900)
const after = await page.evaluate(() => ({
  up: !!document.querySelector('.veil'),
  url: location.pathname + location.search,
  hits: performance
    .getEntriesByType('resource')
    .filter((r) => r.name.includes('/audio/ritual/'))
    .map((r) => r.name.split('/').pop()),
  audio: (() => {
    const a = document.querySelector('audio')
    return a ? { paused: a.paused, t: Number(a.currentTime.toFixed(2)) } : null
  })()
}))

console.log('打捞体检：')
console.log(`  幕布起来了：${during.up ? '是' : '否'}`)
console.log(`  幕布收掉了：${after.up ? '否' : '是'}`)
console.log(`  余晖：落下时 ${glowPeak}，退下后 ${glowEnd}（很淡但要还在）`)
console.log(`  三记声音：${after.hits.join(' / ') || '一个都没加载'}`)
console.log(
  `  落点：${after.url}，展品声音${after.audio ? (after.audio.paused ? '没响' : `在响（${after.audio.t}s）`) : '没有'}`
)

if (!during.up) problems.push('打捞期间没有幕布')
if (after.up) problems.push('幕布没有收掉')
if (glowPeak === null) problems.push('幕布上没有余晖')
else if (glowPeak < 0.2) problems.push(`余晖在落下时太暗，看不见（${glowPeak}）`)
else if (glowPeak > 0.7) problems.push(`余晖太亮，像第二条光柱（${glowPeak}）`)
if (glowEnd !== null && glowEnd < 0.05) problems.push(`余晖没有留下来（${glowEnd}）`)
if (glowEnd !== null && glowEnd > 0.3) problems.push(`余晖没有退到很淡（${glowEnd}）`)
if (after.hits.length !== 3) problems.push(`三记声音只加载了 ${after.hits.length} 条`)
if (after.audio?.paused) problems.push('落点的展品没有出声')

/* ---------------- 二、系统开了「减少动态效果」 ---------------- */

const quiet = await browser.newPage()
watchPage(quiet, '减少动态效果')
await quiet.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 })
await quiet.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }])

const quietHits = []
quiet.on('request', (r) => {
  if (r.url().includes('/audio/ritual/')) quietHits.push(r.url().split('/').pop())
})

await quiet.goto(`${BASE}/`, { waitUntil: 'networkidle2' })
await sleep(1400)
await quiet.click('.btn.primary')
await sleep(160)
const quietDuring = await quiet.evaluate(() => ({ up: !!document.querySelector('.veil') }))
await sleep(900)
const quietAfter = await quiet.evaluate(() => ({
  up: !!document.querySelector('.veil'),
  url: location.pathname
}))

await browser.close()

console.log('减少动态效果：')
console.log(`  幕布仍在：${quietDuring.up ? '是' : '否'}（160ms 时，动作要有交代）`)
console.log(`  收起得很快：${quietAfter.up ? '否' : '是'}（1 秒后已到 ${quietAfter.url}）`)
console.log(`  三记声音：${quietHits.length ? quietHits.join(' / ') : '没有请求'}`)

if (!quietDuring.up) problems.push('减少动态效果时幕布没有出现，动作没有交代')
if (quietAfter.up) problems.push('减少动态效果时幕布没有及时收起')
if (quietHits.length) problems.push('减少动态效果时仍然播了三记声音')

console.log('')
if (problems.length) {
  console.log(`发现 ${problems.length} 个问题：`)
  problems.forEach((p) => console.log('  · ' + p))
  process.exitCode = 1
} else {
  console.log('全部通过。')
}
