import axios from 'axios'

const counterRequest = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

function isUsableToken(token) {
  if (!token) return false
  const trimmed = String(token).trim()
  if (!trimmed) return false
  return trimmed !== 'undefined' && trimmed !== 'null'
}

function clearAuthStorage() {
  localStorage.removeItem('token')
  localStorage.removeItem('userId')
  localStorage.removeItem('username')
  localStorage.removeItem('name')
  localStorage.removeItem('role')
  localStorage.removeItem('regionId')
  localStorage.removeItem('regionCode')
  localStorage.removeItem('regionName')
  localStorage.removeItem('menuPaths')
  localStorage.removeItem('buttonCodes')
  localStorage.removeItem('lastActivityTime')
}

// Request interceptor — add auth token
counterRequest.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (isUsableToken(token)) {
      config.headers.Authorization = `Bearer ${token.trim()}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor — unwrap Result<T> wrapper
counterRequest.interceptors.response.use((response) => {
  const res = response.data
  if (res.code !== 200) {
    return Promise.reject(new Error(res.message || 'Unknown error'))
  }
  // 特殊处理：data 为 null 时，message 包含业务提示，抛出以便调用方处理
  if (res.data === null && res.message) {
    return Promise.reject(new Error(res.message))
  }
  return res.data
}, (error) => {
  const status = error.response?.status
  if (status === 401 || status === 403) {
    clearAuthStorage()
    if (!window.location.pathname.startsWith('/login')) {
      window.location.replace('/login')
    }
    return Promise.reject(new Error('未登录或登录已过期，请重新登录'))
  }
  const message = error.response?.data?.message || error.message || 'Network error'
  return Promise.reject(new Error(message))
})

/**
 * Call the next ticket in queue.
 * @param {string|number} counterId
 * @returns {Promise<Object>}
 */
export function callNext(counterId) { return counterRequest.post('/counter/call/next', { counterId }) }

/**
 * Re-call the currently active ticket.
 * @param {string|number} counterId
 * @returns {Promise<void>}
 */
export function recall(counterId) { return counterRequest.post('/counter/call/recall', { counterId }) }

/**
 * Skip the current ticket.
 * @param {string|number} counterId
 * @returns {Promise<void>}
 */
export function skip(counterId) { return counterRequest.post('/counter/call/skip', { counterId }) }

/**
 * Begin serving the current ticket.
 * @param {string|number} counterId
 * @returns {Promise<void>}
 */
export function serve(counterId) { return counterRequest.post('/counter/serve', { counterId }) }

/**
 * Complete the current ticket.
 * @param {string|number} counterId
 * @returns {Promise<void>}
 */
export function complete(counterId) { return counterRequest.post('/counter/complete', { counterId }) }

/**
 * Toggle pause/resume state of a counter.
 * @param {string|number} counterId
 * @returns {Promise<Object>} updated counter state
 */
export function togglePause(counterId) { return counterRequest.post('/counter/toggle-pause', { counterId }) }

/**
 * Reactivate a skipped ticket back to waiting queue front.
 * @param {string} ticketNo
 * @returns {Promise<void>}
 */
export function reactivate(ticketNo) { return counterRequest.post('/counter/reactivate', { ticketNo }) }
