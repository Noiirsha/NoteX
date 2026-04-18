<script setup lang="ts">
import { useThemeStore } from '../stores/theme'

const themeStore = useThemeStore()
</script>

<template>
  <div class="min-h-screen bg-white dark:bg-gray-900 transition-colors duration-500">
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
          <p class="text-gray-600 dark:text-gray-400">重置密码</p>
        </div>

        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">邮箱</label>
            <input
              type="email"
              class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all"
              placeholder="请输入注册邮箱"
            />
          </div>
          <button
            class="w-full py-3 px-6 bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-700 transition-all duration-300 hover:scale-[1.02] font-medium"
          >
            发送重置链接
          </button>
        </div>

        <div class="mt-6 text-center">
          <router-link to="/" class="text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white transition-colors">
            返回首页
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>