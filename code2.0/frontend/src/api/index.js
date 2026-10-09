/**
 * 后端接口层。
 *
 * 两件事收敛在这里，别处不再直接 fetch：
 *   1）剥掉 { code, msg, data } 这层信封，业务代码直接拿 data；
 *   2）会话管理 —— 客户端生成 deviceKey 并保管，服务端据此认人。
 *      没有注册、没有密码，这是产品定义里的承诺。
 */

const DEVICE_KEY_STORAGE = 'starmuseum.deviceKey'
const SESSION_STORAGE = 'starmuseum.sessionId'

let sessionId = readSession()
let sessionPromise = null

function readSession() {
  const raw = localStorage.getItem(SESSION_STORAGE)
  const id = Number(raw)
  return Number.isFinite(id) && id > 0 ? id : null
}

function deviceKey() {
  let key = localStorage.getItem(DEVICE_KEY_STORAGE)
  if (!key || key.length < 8) {
    key =
      typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `dev-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
    localStorage.setItem(DEVICE_KEY_STORAGE, key)
  }
  return key
}

async function unwrap(res) {
  let body = null
  try {
    body = await res.json()
  } catch {
    // 后端没返回 JSON（比如 500 的兜底页）
  }
  if (!res.ok || (body && typeof body.code === 'number' && body.code !== 0)) {
    throw new Error((body && body.msg) || `请求失败（${res.status}）`)
  }
  return body ? body.data : null
}

function headers(extra = {}) {
  const h = { ...extra }
  if (sessionId) {
    h['X-Session-Id'] = String(sessionId)
  }
  return h
}

function request(path, options = {}) {
  return fetch(path, { ...options, headers: headers(options.headers) }).then(unwrap)
}

/**
 * 取回会话。服务端按 deviceKey 认人，所以这个调用是幂等的：
 * 同一个 deviceKey 永远拿到同一个会话，同时带回服务端权威的昵称。
 */
export function ensureSession() {
  if (!sessionPromise) {
    sessionPromise = fetch('/api/session', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceKey: deviceKey() })
    })
      .then(unwrap)
      .then((session) => {
        sessionId = session.id
        localStorage.setItem(SESSION_STORAGE, String(session.id))
        return session
      })
      .catch((err) => {
        sessionPromise = null
        throw err
      })
  }
  return sessionPromise
}

export async function rename(nickname) {
  await ensureSession()
  return request('/api/session/nickname', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nickname })
  })
}

export const ping = () => request('/api/ping')
export const exhibits = () => request('/api/exhibits')
export const recent = (limit = 3) => request(`/api/exhibits/recent?limit=${limit}`)
export const exhibit = (id) => request(`/api/exhibits/${encodeURIComponent(id)}`)
export const echoes = (id) => request(`/api/exhibits/${encodeURIComponent(id)}/echoes`)
export const heritageList = () => request('/api/heritage')
export const heritageOne = (id) => request(`/api/heritage/${encodeURIComponent(id)}`)

export async function draw() {
  await ensureSession()
  return request('/api/draw')
}

export async function mine() {
  await ensureSession()
  return request('/api/mine')
}

export async function postEcho(exhibitId, echo) {
  await ensureSession()
  const form = new FormData()
  if (echo.kind === 'voice') {
    form.append('file', echo.wav, 'echo.wav')
  } else {
    form.append('body', echo.body)
  }
  return request(`/api/exhibits/${encodeURIComponent(exhibitId)}/echoes`, {
    method: 'POST',
    body: form
  })
}

/** 上传展品。服务端会自己读时长和波形，客户端只负责把文件交上去。 */
export async function uploadExhibit({ file, title, full, place, recordedOn }) {
  await ensureSession()
  const form = new FormData()
  form.append('file', file, 'exhibit.wav')
  form.append('title', title)
  form.append('full', full)
  form.append('place', place)
  form.append('recordedOn', recordedOn)
  return request('/api/exhibits', { method: 'POST', body: form })
}

/** 删掉自己埋下的一段。服务端只认埋它的人 —— 所有权认会话，不认请求里写了什么。 */
export async function deleteExhibit(idOrNo) {
  await ensureSession()
  return request(`/api/exhibits/${encodeURIComponent(idOrNo)}`, { method: 'DELETE' })
}

/* ------------------------------------------------------------------ */
/* 管理口                                                              */
/* ------------------------------------------------------------------ */

const ADMIN_TOKEN_STORAGE = 'starmuseum.adminToken'

/**
 * 管理口令只存在 sessionStorage 里 —— 关掉标签页就没了。
 * 不放 localStorage：那会在这台电脑上长期留一份，共用机器时不合适。
 */
export function adminToken() {
  return sessionStorage.getItem(ADMIN_TOKEN_STORAGE) || ''
}

export function setAdminToken(token) {
  sessionStorage.setItem(ADMIN_TOKEN_STORAGE, token || '')
}

function adminRequest(path, options = {}) {
  return fetch(path, {
    ...options,
    headers: { ...(options.headers || {}), 'X-Admin-Token': adminToken() }
  }).then(unwrap)
}

export const adminQueue = () => adminRequest('/api/admin/queue')

export const adminAuditRecords = () => adminRequest('/api/admin/audit-records')

export function adminReview(type, id, pass, reason = '') {
  const qs = new URLSearchParams({ pass: String(pass), reason })
  return adminRequest(`/api/admin/review/${encodeURIComponent(type)}/${id}?${qs}`, {
    method: 'POST'
  })
}
