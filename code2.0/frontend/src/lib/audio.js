/**
 * 浏览器录出来的是 webm/opus，云 ASR 只吃 16k 单声道 WAV。
 * 全程在前端转，不需要服务端转码。
 */

const TARGET_SAMPLE_RATE = 16000

export function canRecord() {
  return typeof navigator !== 'undefined' &&
    !!navigator.mediaDevices?.getUserMedia &&
    typeof window !== 'undefined' &&
    !!window.MediaRecorder
}

export function createRecorder() {
  let mediaRecorder = null
  let stream = null
  let chunks = []
  let startedAt = 0

  return {
    async start() {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true }
      })
      const preferred = 'audio/webm;codecs=opus'
      const mimeType = MediaRecorder.isTypeSupported(preferred) ? preferred : ''
      mediaRecorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined)
      chunks = []
      mediaRecorder.ondataavailable = (e) => {
        if (e.data && e.data.size) chunks.push(e.data)
      }
      startedAt = performance.now()
      mediaRecorder.start()
    },

    /** 返回 { blob, wav, seconds } —— wav 是 16k 单声道，可直接上传给后端 */
    stop() {
      return new Promise((resolve, reject) => {
        if (!mediaRecorder) return reject(new Error('还没有开始录音'))
        mediaRecorder.onstop = async () => {
          const seconds = (performance.now() - startedAt) / 1000
          try {
            const blob = new Blob(chunks, { type: mediaRecorder.mimeType || 'audio/webm' })
            const wav = await blobToWav16k(blob)
            resolve({ blob, wav, seconds })
          } catch (err) {
            reject(err)
          } finally {
            stream?.getTracks().forEach((t) => t.stop())
            stream = null
            mediaRecorder = null
          }
        }
        mediaRecorder.stop()
      })
    },

    cancel() {
      try {
        mediaRecorder?.stop()
      } catch {
        /* 已经停了 */
      }
      stream?.getTracks().forEach((t) => t.stop())
      stream = null
      mediaRecorder = null
    }
  }
}

export async function blobToWav16k(blob) {
  const arrayBuffer = await blob.arrayBuffer()
  const AudioCtx = window.AudioContext || window.webkitAudioContext
  const decodeCtx = new AudioCtx()
  let decoded
  try {
    decoded = await decodeCtx.decodeAudioData(arrayBuffer.slice(0))
  } finally {
    await decodeCtx.close()
  }

  const frames = Math.ceil(decoded.duration * TARGET_SAMPLE_RATE)
  const offline = new OfflineAudioContext(1, frames, TARGET_SAMPLE_RATE)
  const source = offline.createBufferSource()
  source.buffer = decoded
  source.connect(offline.destination)
  source.start()
  const rendered = await offline.startRendering()
  return encodeWavMono16(rendered.getChannelData(0), TARGET_SAMPLE_RATE)
}

/** 从一段录音里抽波形，给回音列表画小痕迹用 */
export function peaksFromWav(blob, points = 160) {
  return blob
    .arrayBuffer()
    .then((buf) => {
      const view = new DataView(buf)
      const dataStart = 44
      const count = Math.floor((view.byteLength - dataStart) / 2)
      const trace = []
      for (let p = 0; p < points; p++) {
        const a = Math.floor((p * count) / points)
        const b = Math.max(a + 1, Math.floor(((p + 1) * count) / points))
        let best = 0
        for (let i = a; i < b; i++) {
          const v = view.getInt16(dataStart + i * 2, true) / 32768
          if (Math.abs(v) > Math.abs(best)) best = v
        }
        trace.push(Number(best.toFixed(3)))
      }
      return trace
    })
    .catch(() => [])
}

function encodeWavMono16(samples, sampleRate) {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  const ascii = (offset, text) => {
    for (let i = 0; i < text.length; i++) view.setUint8(offset + i, text.charCodeAt(i))
  }

  ascii(0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  ascii(8, 'WAVE')
  ascii(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  ascii(36, 'data')
  view.setUint32(40, samples.length * 2, true)

  let offset = 44
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true)
    offset += 2
  }
  return new Blob([view], { type: 'audio/wav' })
}
