import axios from 'axios'
import { ElMessage } from 'element-plus'
import { auth, clearAuth } from '../auth'

const http = axios.create({ baseURL: '/api', timeout: 15000 })

/** Errors are already shown via ElMessage; callers only need the rejection to abort their flow. */
function reported(err) {
  err.reported = true
  return Promise.reject(err)
}
window.addEventListener('unhandledrejection', (e) => {
  if (e.reason && e.reason.reported) e.preventDefault()
})

http.interceptors.request.use((config) => {
  if (auth.token) config.headers.Authorization = `Bearer ${auth.token}`
  return config
})

let redirecting = false
function toLogin() {
  if (redirecting || location.pathname === '/login') return
  redirecting = true
  clearAuth()
  const back = encodeURIComponent(location.pathname + location.search)
  location.assign(`/login?redirect=${back}`)
}

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body instanceof Blob) return body
    if (body && body.code !== undefined && body.code !== 0) {
      ElMessage.error(body.msg || '请求失败')
      return reported(new Error(body.msg))
    }
    return body ? body.data : body
  },
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.msg || err.message || '网络错误'
    if (status === 401) {
      ElMessage.warning(msg)
      toLogin()
    } else {
      ElMessage.error(msg)
    }
    return reported(err)
  }
)

export default http
