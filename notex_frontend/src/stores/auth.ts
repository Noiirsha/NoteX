import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi, getUserInfo } from '@/api/user'
import type { LoginRequest, RegisterRequest, UserInfo } from '@/types/api'
import { clearUserAIModelsCache } from '@/utils/aiModel'

/**
 * 认证状态管理Store
 */
export const useAuthStore = defineStore(
  'auth',
  () => {
  // 状态
  const token = ref<string | null>(null)
  const userInfo = ref<UserInfo | null>(null)
  const isLoading = ref(false)

  // 计算属性
  const isAuthenticated = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')
  const nickname = computed(() => userInfo.value?.nickname || '')
  const avatar = computed(() => userInfo.value?.avatar || '')

  /**
   * 设置token
   */
  const setToken = (newToken: string) => {
    token.value = newToken
  }

  /**
   * 清除token
   */
  const clearToken = () => {
    token.value = null
  }

  /**
   * 设置用户信息
   */
  const setUserInfo = (info: UserInfo) => {
    userInfo.value = info
  }

  /**
   * 更新用户信息
   */
  const updateUserInfo = (updates: Partial<UserInfo>) => {
    if (userInfo.value) {
      userInfo.value = { ...userInfo.value, ...updates }
    }
  }

  /**
   * 清除用户信息
   */
  const clearUserInfo = () => {
    userInfo.value = null
  }

  /**
   * 用户登录
   * @param credentials 登录凭证
   * @returns 登录结果
   */
  const login = async (credentials: LoginRequest) => {
    isLoading.value = true
    try {
      const response = await loginApi(credentials)
      // 设置token
      setToken(response.token)
      // 设置用户信息
      setUserInfo({
        username: response.username,
        nickname: response.nickname,
        avatar: response.avatarImageUrl
      })
      return { success: true, message: '登录成功' }
    } catch (error: any) {
      return { success: false, message: error.message || '登录失败' }
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 用户注册
   * @param data 注册信息
   * @returns 注册结果
   */
  const register = async (data: RegisterRequest) => {
    isLoading.value = true
    try {
      await registerApi(data)
      return { success: true, message: '注册成功' }
    } catch (error: any) {
      return { success: false, message: error.message || '注册失败' }
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取用户信息
   */
  const fetchUserInfo = async () => {
    if (!token.value) return

    isLoading.value = true
    try {
      const info = await getUserInfo()
      setUserInfo(info)
    } catch (error) {
      console.error('获取用户信息失败:', error)
      // 如果获取用户信息失败，可能是token过期，清除认证状态
      logout()
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 用户登出
   */
  const logout = () => {
    clearToken()
    clearUserInfo()
    clearUserAIModelsCache()
  }

  /**
   * 初始化认证状态
   * 如果有token，尝试获取用户信息
   */
  const initAuth = async () => {
    if (token.value) {
      await fetchUserInfo()
    }
  }

    return {
      // 状态
      token,
      userInfo,
      isLoading,
      // 计算属性
      isAuthenticated,
      username,
      nickname,
      avatar,
      // 方法
      login,
      register,
      logout,
      fetchUserInfo,
      initAuth,
      updateUserInfo
    }
  },
  {
    persist: {
      key: 'auth-store',
      storage: localStorage,
      pick: ['token', 'userInfo']
    }
  }
)
