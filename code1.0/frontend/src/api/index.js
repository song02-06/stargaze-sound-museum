import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 60000 })

// 后端统一返回体是 { code, msg, data }，这里剥一层，
// 让业务代码直接拿到 data，错误则抛出 msg。
http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return body ? body.data : null
  },
  (error) => Promise.reject(error)
)

export const ping = () => http.get('/ping')
export const draw = () => http.get('/draw')
export const stats = () => http.get('/stats')
export const getBottle = (id) => http.get(`/bottle/${id}`)
export const recentBottles = () => http.get('/bottle/recent')

export const uploadBottle = (file, note, durationMs) => {
  const form = new FormData()
  form.append('file', file, 'voice.wav')
  form.append('note', note ?? '')
  form.append('durationMs', String(durationMs ?? 0))
  return http.post('/bottle', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const reviewQueue = () => http.get('/admin/queue')
export const reviewBottle = (id, pass, reason) =>
  http.post(`/admin/review/${id}`, null, { params: { pass, reason } })
export const auditRecords = () => http.get('/admin/audit-records')
