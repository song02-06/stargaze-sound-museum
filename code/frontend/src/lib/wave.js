/**
 * 把一条波形数据变成 SVG 路径。
 *
 * 数据来源是 tools/gen-audio.mjs 从真实 PCM 采样里抽出来的，
 * 所以界面上画的声波就是这段录音本身，不是装饰线。
 */

const NS = 'http://www.w3.org/2000/svg'

/** 均匀降采样，给列表里的小波形用 */
export function downsample(trace, size) {
  if (!trace || trace.length <= size) return trace || []
  const out = []
  for (let i = 0; i < size; i++) {
    const a = Math.floor((i * trace.length) / size)
    const b = Math.max(a + 1, Math.floor(((i + 1) * trace.length) / size))
    let best = 0
    for (let k = a; k < b; k++) {
      if (Math.abs(trace[k]) > Math.abs(best)) best = trace[k]
    }
    out.push(best)
  }
  return out
}

export function traceToPoints(trace, width, height, amp = 0.92) {
  const half = height / 2
  const n = trace.length
  const pts = []
  for (let i = 0; i < n; i++) {
    pts.push({
      x: n === 1 ? 0 : (i / (n - 1)) * width,
      y: half - trace[i] * half * amp
    })
  }
  return pts
}

export function pointsToPath(pts, upto) {
  const n = upto === undefined ? pts.length - 1 : Math.max(0, Math.min(upto, pts.length - 1))
  let d = ''
  for (let i = 0; i <= n; i++) {
    d += (i === 0 ? 'M' : 'L') + pts[i].x.toFixed(1) + ',' + pts[i].y.toFixed(1)
  }
  return d
}

export function svgEl(name, attrs) {
  const el = document.createElementNS(NS, name)
  for (const k in attrs) el.setAttribute(k, String(attrs[k]))
  return el
}
