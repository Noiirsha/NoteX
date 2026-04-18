import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import type { ApiResponse } from '@/types/api'
import { useAuthStore } from '@/stores/auth'

/**
 * 创建axios实例
 * 配置基础URL和默认请求头
 */
const request: AxiosInstance = axios.create({
  baseURL: '/api', // 使用vite代理配置
  timeout: 10000, // 请求超时时间
  headers: {
    'Content-Type': 'application/json'
  }
})

/**
 * 请求拦截器
 * 自动添加JWT token到请求头
 */
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 从auth store获取token
    const authStore = useAuthStore()
    const token = authStore.token
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器
 * 统一处理响应数据和错误
 */
request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { code, message, data } = response.data
    
    // 根据业务状态码处理
    if (code == "OK") {
      return data
    } else {
      // 业务错误处理
      console.error('API错误:', message)
      const businessError = new Error(message || '请求失败') as Error & {
        detail?: unknown
        code?: unknown
      }
      businessError.detail = data
      businessError.code = code
      return Promise.reject(businessError)
    }
  },
  (error) => {
    // HTTP错误处理
    if (error.response) {
      const { status } = error.response
      switch (status) {
        case 401:
          // 未授权，清除token并跳转登录
          const authStore = useAuthStore()
          authStore.logout()
          window.location.href = '/login'
          break
        case 403:
          console.error('无权限访问')
          break
        case 404:
          console.error('请求的资源不存在')
          break
        case 500:
          console.error('服务器错误')
          break
        default:
          console.error('请求错误:', error.response.data?.message || '未知错误')
      }
    } else if (error.request) {
      console.error('网络错误，请检查网络连接')
    } else {
      console.error('请求配置错误:', error.message)
    }
    return Promise.reject(error)
  }
)

export default request
