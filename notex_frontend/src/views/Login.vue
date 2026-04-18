<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import type { LoginRequest } from '@/types/api'
import Toast from '@/components/Toast.vue'
import CryptoJS from 'crypto-js'

const router = useRouter()
const themeStore = useThemeStore()
const authStore = useAuthStore()

// 表单数据
const formData = ref({
  username: '',
  password: ''
})

// 错误信息
const errors = ref<Record<string, string>>({})

// Toast状态
const toast = ref<{
  show: boolean
  message: string
  type: 'success' | 'error' | 'info'
}>({
  show: false,
  message: '',
  type: 'info'
})

// 加载状态
const isLoading = ref(false)

/**
 * 显示Toast提示
 */
const showToast = (message: string, type: 'success' | 'error' | 'info') => {
  toast.value = { show: true, message, type }
}

/**
 * 关闭Toast
 */
const closeToast = () => {
  toast.value.show = false
}

/**
 * 验证表单
 */
const validateForm = (): boolean => {
  errors.value = {}

  // 验证用户名
  if (!formData.value.username) {
    errors.value.username = '用户名不能为空'
  } else if (formData.value.username.length < 3 || formData.value.username.length > 20) {
    errors.value.username = '用户名长度必须在3-20之间'
  }

  // 验证密码
  if (!formData.value.password) {
    errors.value.password = '密码不能为空'
  } else if (formData.value.password.length < 6 || formData.value.password.length > 100) {
    errors.value.password = '密码长度必须在6-100之间'
  }

  return Object.keys(errors.value).length === 0
}

/**
 * 密码哈希化（SHA-256）
 */
const hashPassword = (password: string): string => {
  return CryptoJS.SHA256(password).toString()
}

/**
 * 处理登录
 */
const handleLogin = async () => {
  // 验证表单
  if (!validateForm()) {
    return
  }

  isLoading.value = true

  try {
    // 构建登录请求
    const loginData: LoginRequest = {
      username: formData.value.username,
      passwordHash: hashPassword(formData.value.password)
    }

    // 调用登录API
    const result = await authStore.login(loginData)

    if (result.success) {
      showToast('登录成功', 'success')
      // 延迟跳转到仪表盘
      setTimeout(() => {
        router.push('/dashboard')
      }, 1000)
    } else {
      showToast(result.message, 'error')
    }
  } catch (error: any) {
    // 登录失败，显示服务器返回的错误信息
    const errorMessage = error.response?.data?.message || error.message || '登录失败，请稍后重试'
    showToast(errorMessage, 'error')
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-white dark:bg-gray-900 transition-colors duration-500">
    <!-- Toast组件 -->
    <Toast
      v-if="toast.show"
      :message="toast.message"
      :type="toast.type"
      @close="closeToast"
    />

    <!-- 顶部主题切换按钮 -->
    <div class="fixed top-6 right-6 z-10">
      <button
        @click="themeStore.toggleTheme"
        class="p-2 rounded-lg bg-gray-100 dark:bg-gray-800 hover:bg-gray-200 dark:hover:bg-gray-700 transition-all duration-300 hover:scale-105"
        :title="themeStore.isDark ? '切换到明亮模式' : '切换到暗黑模式'"
      >
        <svg v-if="themeStore.isDark" class="w-5 h-5 text-gray-700 dark:text-gray-300 transition-transform duration-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
        </svg>
        <svg v-else class="w-5 h-5 text-gray-700 dark:text-gray-300 transition-transform duration-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
        </svg>
      </button>
    </div>

    <!-- 主内容区域 -->
    <div class="flex items-center justify-center min-h-screen p-8">
      <div class="w-full max-w-md">
        <div class="text-center mb-8">
          <h1 class="text-4xl font-bold text-gray-900 dark:text-white mb-2">NoteX</h1>
          <p class="text-gray-600 dark:text-gray-400">登录您的账户</p>
        </div>

        <div class="space-y-4">
          <!-- 用户名 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">用户名</label>
            <input
              v-model="formData.username"
              type="text"
              class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all"
              placeholder="请输入用户名（3-20字符）"
              :class="{ 'border-red-500 focus:ring-red-500': errors.username }"
            />
            <p v-if="errors.username" class="mt-1 text-sm text-red-500">{{ errors.username }}</p>
          </div>

          <!-- 密码 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">密码</label>
            <input
              v-model="formData.password"
              type="password"
              class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all"
              placeholder="请输入密码（6-100字符）"
              :class="{ 'border-red-500 focus:ring-red-500': errors.password }"
            />
            <p v-if="errors.password" class="mt-1 text-sm text-red-500">{{ errors.password }}</p>
          </div>

          <!-- 登录按钮 -->
          <button
            @click="handleLogin"
            :disabled="isLoading"
            class="w-full py-3 px-6 bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-700 transition-all duration-300 hover:scale-[1.02] font-medium disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:scale-100"
          >
            {{ isLoading ? '登录中...' : '登录' }}
          </button>
        </div>

        <div class="mt-6 text-center space-y-2">
          <router-link to="/register" class="block text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white transition-colors">
            还没有账户？去注册
          </router-link>
          <router-link to="/" class="block text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white transition-colors">
            返回主页
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>
