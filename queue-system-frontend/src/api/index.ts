import axios, { AxiosInstance } from 'axios'
import type { ApiResponse } from '../types'

const request: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

function isUsableToken(token: string | null) {
  if (!token) return false
  const trimmed = token.trim()
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

function isBinaryResponseType(responseType?: string) {
  return responseType === 'blob' || responseType === 'arraybuffer'
}

async function parseBinaryApiResponse(data: unknown, contentType?: string): Promise<ApiResponse | null> {
  const normalizedType = (contentType || '').toLowerCase()

  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    const blobType = (data.type || normalizedType).toLowerCase()
    if (!blobType.includes('application/json') && !blobType.startsWith('text/')) {
      return null
    }

    const text = await data.text()
    if (!text) {
      return null
    }

    try {
      return JSON.parse(text) as ApiResponse
    } catch {
      return null
    }
  }

  if (data instanceof ArrayBuffer) {
    if (!normalizedType.includes('application/json') && !normalizedType.startsWith('text/')) {
      return null
    }

    const text = new TextDecoder('utf-8').decode(data)
    if (!text) {
      return null
    }

    try {
      return JSON.parse(text) as ApiResponse
    } catch {
      return null
    }
  }

  return null
}

// Request interceptor
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (isUsableToken(token)) {
      config.headers.Authorization = `Bearer ${token!.trim()}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor — unwrap Result<T> wrapper
request.interceptors.response.use(
  async (response) => {
    if (isBinaryResponseType(response.config.responseType)) {
      const payload = await parseBinaryApiResponse(response.data, response.headers?.['content-type'])
      if (payload) {
        if (payload.code !== 200) {
          return Promise.reject(new Error(payload.message || 'Unknown error'))
        }
        return payload.data
      }
      return response.data
    }

    const res: ApiResponse = response.data
    if (res.code !== 200) {
      return Promise.reject(new Error(res.message || 'Unknown error'))
    }
    return res.data
  },
  async (error) => {
    const status = error.response?.status
    if (status === 401 || status === 403) {
      clearAuthStorage()
      if (!window.location.pathname.startsWith('/login')) {
        window.location.replace('/login')
      }
      return Promise.reject(new Error('未登录或登录已过期，请重新登录'))
    }

    const payload = await parseBinaryApiResponse(error.response?.data, error.response?.headers?.['content-type'])
    const message = payload?.message || error.response?.data?.message || error.message || 'Network error'
    return Promise.reject(new Error(message))
  }
)

export default request
