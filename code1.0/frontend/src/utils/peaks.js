/**
 * 从音频文件里提取波形峰值，用来画播放器的波形条。
 *
 * 这里走的是「解码 → 分桶取最大值 → 归一化」，得到的是一条稳定的静态波形，
 * 好处是播放前就能画出来、拖动时不会跳；播放中的实时频谱另由 AnalyserNode 叠加。
 */

const cache = new Map()

export function loadPeaks(url, buckets = 110) {
  if (!url) return Promise.resolve(null)
  const key = `${url}::${buckets}`
  if (cache.has(key)) return cache.get(key)

  const task = (async () => {
    const response = await fetch(url)
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const buffer = await response.arrayBuffer()

    const Ctx = window.AudioContext || window.webkitAudioContext
    const ctx = new Ctx()
    try {
      const audio = await ctx.decodeAudioData(buffer.slice(0))
      const channel = audio.getChannelData(0)
      const step = Math.max(1, Math.floor(channel.length / buckets))
      const peaks = new Float32Array(buckets)
      let max = 0

      for (let i = 0; i < buckets; i++) {
        const start = i * step
        let peak = 0
        // 每桶内再抽样，避免长音频遍历过慢
        for (let j = 0; j < step; j += 8) {
          const v = Math.abs(channel[start + j] || 0)
          if (v > peak) peak = v
        }
        peaks[i] = peak
        if (peak > max) max = peak
      }

      if (max > 0) {
        for (let i = 0; i < buckets; i++) peaks[i] = peaks[i] / max
      }
      return peaks
    } finally {
      ctx.close()
    }
  })()

  cache.set(key, task)
  return task
}
