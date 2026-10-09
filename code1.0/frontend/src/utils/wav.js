/**
 * 浏览器原生录出来的是 webm/opus，云 ASR 只吃 16k 单声道 WAV。
 * 这里用 OfflineAudioContext 重采样 + 手写 WAV 头，全程在前端完成，不需要服务端转码。
 */

const TARGET_SAMPLE_RATE = 16000

export async function blobToWav16k(blob) {
  const arrayBuffer = await blob.arrayBuffer()
  const AudioCtx = window.AudioContext || window.webkitAudioContext
  const decodeCtx = new AudioCtx()

  let audioBuffer
  try {
    audioBuffer = await decodeCtx.decodeAudioData(arrayBuffer.slice(0))
  } finally {
    await decodeCtx.close()
  }

  const frames = Math.ceil(audioBuffer.duration * TARGET_SAMPLE_RATE)
  const offlineCtx = new OfflineAudioContext(1, frames, TARGET_SAMPLE_RATE)
  const source = offlineCtx.createBufferSource()
  source.buffer = audioBuffer
  source.connect(offlineCtx.destination)
  source.start()

  const rendered = await offlineCtx.startRendering()
  return encodeWavMono16(rendered.getChannelData(0), TARGET_SAMPLE_RATE)
}

function encodeWavMono16(samples, sampleRate) {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)

  writeAscii(view, 0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeAscii(view, 8, 'WAVE')
  writeAscii(view, 12, 'fmt ')
  view.setUint32(16, 16, true)        // PCM 子块大小
  view.setUint16(20, 1, true)         // 音频格式 = PCM
  view.setUint16(22, 1, true)         // 单声道
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)   // 字节率
  view.setUint16(32, 2, true)         // 块对齐
  view.setUint16(34, 16, true)        // 位深
  writeAscii(view, 36, 'data')
  view.setUint32(40, samples.length * 2, true)

  let offset = 44
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true)
    offset += 2
  }
  return new Blob([view], { type: 'audio/wav' })
}

function writeAscii(view, offset, text) {
  for (let i = 0; i < text.length; i++) {
    view.setUint8(offset + i, text.charCodeAt(i))
  }
}
