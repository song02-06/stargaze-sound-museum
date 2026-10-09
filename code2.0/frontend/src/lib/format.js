export function clock(seconds) {
  const s = Math.max(0, Math.round(Number(seconds) || 0))
  const m = Math.floor(s / 60)
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

/** 2026.07.18 */
export function dotDate(iso) {
  if (!iso) return ''
  return String(iso).replaceAll('-', '.')
}

export function daysAgo(iso) {
  if (!iso) return null
  const then = new Date(`${iso}T12:00:00`)
  if (Number.isNaN(then.getTime())) return null
  const days = Math.floor((Date.now() - then.getTime()) / 86400000)
  return Math.max(0, days)
}

export function sinceLabel(days) {
  if (days === null || days === undefined) return ''
  if (days === 0) return '今天入馆'
  if (days === 1) return '昨天入馆'
  if (days < 30) return `${days} 天前入馆`
  if (days < 365) return `${Math.floor(days / 30)} 个月前入馆`
  return `${Math.floor(days / 365)} 年前入馆`
}

/** 距离现在多久，用于回音时间戳 */
export function agoLabel(isoDateTime) {
  const t = new Date(isoDateTime).getTime()
  if (Number.isNaN(t)) return ''
  const mins = Math.floor((Date.now() - t) / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days === 1) return '昨天'
  if (days < 30) return `${days} 天前`
  return `${Math.floor(days / 30)} 个月前`
}
