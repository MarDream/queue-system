import axios, { AxiosInstance } from 'axios'
import type { ApiResponse } from '../types'

const request: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

// Request interceptor
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor — unwrap Result<T> wrapper
request.interceptors.response.use(
  (response) => {
    const res: ApiResponse = response.data
    if (res.code !== 200) {
      return Promise.reject(new Error(res.message || 'Unknown error'))
    }
    return res.data
  },
  (error) => {
    const message = error.response?.data?.message || error.message || 'Network error'
    return Promise.reject(new Error(message))
  }
)

export default request
