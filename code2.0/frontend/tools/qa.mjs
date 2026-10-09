/**
 * 设计 QA 脚本 —— 可复现的截图 + 响应式体检。
 *
 * 用法：
 *   npm run build
 *   npm run preview           （另开一个终端）
 *   node tools/qa.mjs
 *
 * 产出：
 *   设计参考/实现截图/*.png   桌面 1440×900 与移动 390×844 的实现截图
 *   终端里打印的行宽溢出报告（移动端最常见的塌陷就是横向滚动）
 *
 * 为什么要脚本化：手工截图证明不了「下次还这样」。
 * 这个脚本每次跑出来的东西一样，改完 UI 再跑一次就能看出是不是退步了。
 */

import { mkdirSync, existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import puppeteer from 'puppeteer-core'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const OUT = resolve(ROOT, '..', '设计参考', '实现截图')
const BASE = process.env.QA_BASE || 'http://127.0.0.1:4180'

function chromePath() {
  const candidates = [
    process.env.CHROME_PATH,
    `${process.env.ProgramFiles}\\Google\\Chrome\\Application\\chrome.exe`,
    `${process.env['ProgramFiles(x86)']}\\Google\\Chrome\\Application\\chrome.exe`,
    `${process.env.ProgramFiles}\\Microsoft\\Edge\\Application\\msedge.exe`
  ].filter(Boolean)
  return candidates.find((p) => existsSync(p))
}

const DESKTOP = { width: 1440, height: 900, deviceScaleFactor: 1 }
const MOBILE = { width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true }

async function clickByText(page, text) {
  return page.evaluate((t) => {
    const els = [...document.querySelectorAll('button, a')]
    const el = els.find((node) => node.textContent.replace(/\s+/g, ' ').trim().includes(t))
    if (!el) return false
    el.click()
    return true
  }, text)
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const SHOTS = [
  { name: '01-首屏', path: '/', wait: 900 },
  // 打捞动画总长 2.4 秒（之后跳转），1.75 秒时线、光点、涟漪都长齐了
  { name: '02-打捞中', path: '/', wait: 400, action: (p) => clickByText(p, '打捞一段'), after: 1750 },
  { name: '03-展品', path: '/exhibit/0249', wait: 2600 },
  { name: '04-展品-全文', path: '/exhibit/0249', wait: 2400, action: (p) => clickByText(p, '看全文'), after: 700 },
  { name: '05-回音', path: '/exhibit/0249/echo', wait: 1600 },
  { name: '06-馆藏', path: '/collection', wait: 800 },
  { name: '07-历史之声', path: '/archive', wait: 900 },
  { name: '08-历史彩蛋', path: '/archive/1941-07', wait: 1400 },
  { name: '09-我的声音', path: '/mine', wait: 800 },
  // 复审台需要口令。口令从环境变量读，不写进这个文件：
  //   $env:ADMIN_TOKEN="..."; npm run qa
  ...(process.env.ADMIN_TOKEN
    ? [{
        name: '10-复审台',
        path: '/admin',
        wait: 500,
        action: async (p) => {
          await p.evaluate((t) => sessionStorage.setItem('starmuseum.adminToken', t),
            process.env.ADMIN_TOKEN)
          await p.reload({ waitUntil: 'networkidle2' })
          return true
        },
        after: 1600
      }]
    : [])
]

const MOBILE_SHOTS = [
  { name: 'm-01-首屏', path: '/' },
  { name: 'm-03-展品', path: '/exhibit/0249' },
  { name: 'm-05-回音', path: '/exhibit/0249/echo' },
  { name: 'm-08-历史彩蛋', path: '/archive/1941-07' }
]

const ROUTES = SHOTS.map((s) => s.path)

const browser = await puppeteer.launch({
  executablePath: chromePath(),
  headless: true,
  args: [
    '--no-sandbox',
    '--disable-dev-shm-usage',
    '--hide-scrollbars',
    '--font-render-hinting=none',
    '--autoplay-policy=no-user-gesture-required'
  ]
})

mkdirSync(OUT, { recursive: true })

const problems = []

/* ---------------- 桌面截图 ---------------- */

const page = await browser.newPage()
page.on('console', (msg) => {
  if (msg.type() === 'error') problems.push(`控制台错误（${page.url()}）：${msg.text().slice(0, 120)}`)
})
page.on('pageerror', (err) => problems.push(`未捕获异常（${page.url()}）：${String(err).slice(0, 120)}`))

// 记下真正 404 的 URL —— 光看「Failed to load resource」不知道是谁
const failed = new Map()
page.on('response', (res) => {
  if (res.status() >= 400) failed.set(res.url(), res.status())
})

await page.setViewport(DESKTOP)

for (const shot of SHOTS) {
  await page.goto(BASE + shot.path, { waitUntil: 'networkidle2' })
  await sleep(shot.wait)
  if (shot.action) {
    const ok = await shot.action(page)
    if (!ok) problems.push(`截图 ${shot.name}：找不到要点的元素`)
    await sleep(shot.after || 0)
  }
  await page.screenshot({ path: resolve(OUT, `${shot.name}.png`) })
  console.log(`  ✓ ${shot.name}.png`)
}

/* ---------------- 移动端截图 + 行宽体检 ---------------- */

await page.setViewport(MOBILE)

for (const shot of MOBILE_SHOTS) {
  await page.goto(BASE + shot.path, { waitUntil: 'networkidle2' })
  await sleep(2200)
  await page.screenshot({ path: resolve(OUT, `${shot.name}.png`), fullPage: true })
  console.log(`  ✓ ${shot.name}.png`)
}

console.log('\n行宽体检（390px 视口，横向滚动 = 版面塌了）：')
for (const path of [...new Set(ROUTES)]) {
  await page.goto(BASE + path, { waitUntil: 'networkidle2' })
  await sleep(900)
  const report = await page.evaluate(() => {
    const doc = document.documentElement
    const overflow = doc.scrollWidth - window.innerWidth
    const wide = []
    if (overflow > 1) {
      for (const el of document.querySelectorAll('body *')) {
        const r = el.getBoundingClientRect()
        if (r.width === 0) continue
        if (r.right > window.innerWidth + 1 || r.left < -1) {
          wide.push(`${el.tagName.toLowerCase()}.${String(el.className).split(' ')[0]}`)
        }
      }
    }
    return { overflow, wide: [...new Set(wide)].slice(0, 6) }
  })
  const flag = report.overflow > 1 ? '✗' : '✓'
  console.log(`  ${flag} ${path.padEnd(24)} 溢出 ${report.overflow}px${report.wide.length ? '  → ' + report.wide.join(', ') : ''}`)
  if (report.overflow > 1) problems.push(`移动端横向溢出 ${report.overflow}px：${path}（${report.wide.join(', ')}）`)
}

await browser.close()

/* ---------------- 可选的端到端写入验证 ---------------- */
// 默认不跑（会往演示库写数据）。想验证「界面 → 后端 → 数据库」整条链路时：
//   QA_WRITE=1 npm run qa
if (process.env.QA_WRITE === '1') {
  const writer = await puppeteer.launch({
    executablePath: chromePath(),
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--hide-scrollbars']
  })
  const wPage = await writer.newPage()
  await wPage.setViewport(DESKTOP)

  const probe = `这条是在 ${new Date().toLocaleTimeString('zh-CN')} 从界面写进去的。`

  await wPage.goto(`${BASE}/exhibit/0249/echo`, { waitUntil: 'networkidle2' })
  await sleep(1500)

  const before = await wPage.evaluate(
    () => document.querySelectorAll('.list li.echo').length
  )

  await wPage.type('.bar .input', probe)
  await sleep(200)
  await wPage.click('.bar .send')
  await sleep(2500)

  const after = await wPage.evaluate(
    () => document.querySelectorAll('.list li.echo').length
  )
  const appeared = await wPage.evaluate(
    (text) => [...document.querySelectorAll('.list li.echo .body')].some((n) => n.textContent.includes(text)),
    probe
  )

  console.log('\n端到端写入验证：')
  console.log(`  写入前 ${before} 条 → 写入后 ${after} 条`)
  console.log(`  新回音出现在列表里：${appeared ? '是' : '否'}`)
  if (after <= before || !appeared) {
    problems.push('端到端写入验证失败：界面发出的回音没有出现在列表里')
  }

  await writer.close()
}

/* ---------------- 可选的复审台动作验证 ---------------- */
//   $env:ADMIN_TOKEN="..."; $env:QA_ADMIN="1"; npm run qa
// 验证「从界面点通过 → 队列少一条 → 流水里多一条 MANUAL 记录」。
if (process.env.QA_ADMIN === '1' && process.env.ADMIN_TOKEN) {
  const browser2 = await puppeteer.launch({
    executablePath: chromePath(),
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--hide-scrollbars']
  })
  const p = await browser2.newPage()
  await p.setViewport(DESKTOP)

  await p.goto(`${BASE}/admin`, { waitUntil: 'networkidle2' })
  await p.evaluate((t) => sessionStorage.setItem('starmuseum.adminToken', t),
    process.env.ADMIN_TOKEN)
  await p.reload({ waitUntil: 'networkidle2' })
  await sleep(1500)

  const before = await p.evaluate(() => document.querySelectorAll('.queue .item').length)

  console.log('\n复审台动作验证：')
  if (before === 0) {
    console.log('  队列是空的，跳过（先造一条待复审内容再跑）')
  } else {
    await p.click('.queue .item .act.ok')
    await sleep(2200)

    const after = await p.evaluate(() => document.querySelectorAll('.queue .item').length)
    const hasManual = await p.evaluate(() =>
      [...document.querySelectorAll('.log .stage')].some((n) => n.textContent.trim() === 'MANUAL'))

    console.log(`  队列 ${before} → ${after} 条`)
    console.log(`  流水里出现 MANUAL 记录：${hasManual ? '是' : '否'}`)

    if (after >= before) problems.push('复审台动作验证失败：点了通过但队列没有减少')
    if (!hasManual) problems.push('复审台动作验证失败：流水里没记下这次人工决定')
  }

  await browser2.close()
}

console.log('')
if (problems.length) {
  console.log(`发现 ${problems.length} 个问题：`)
  problems.forEach((p) => console.log('  · ' + p))
  process.exitCode = 1
} else {
  console.log('截图与体检全部通过，没有控制台错误，也没有横向溢出。')
}

if (failed.size) {
  console.log('\n失败的请求：')
  for (const [url, status] of failed) console.log(`  · ${status}  ${url}`)
}
