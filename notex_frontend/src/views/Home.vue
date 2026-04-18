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
    <div class="flex flex-col lg:flex-row min-h-screen">
      <!-- 左侧 Logo 区域 -->
      <div class="flex-1 flex items-center justify-center p-8 lg:p-16">
        <div class="text-center lg:text-left">
          <h1 class="text-5xl lg:text-7xl font-bold text-gray-900 dark:text-white mb-4 tracking-tight animate-fade-in">
            NoteX
          </h1>
          <p class="text-xl lg:text-2xl text-gray-600 dark:text-gray-400 animate-fade-in-delay">
            属于自己的云端笔记仓
          </p>
        </div>
      </div>

      <!-- 中间分隔线 -->
      <div class="hidden lg:flex items-center justify-center px-4">
        <div class="w-px h-64 bg-gray-300 dark:bg-gray-700"></div>
      </div>

      <!-- 右侧登录区域 -->
      <div class="flex-1 flex items-center justify-center p-8 lg:p-16">
        <div class="w-full max-w-md">
          <h2 class="text-2xl lg:text-3xl font-semibold text-gray-900 dark:text-white mb-8 text-center animate-fade-in-delay">
            开始使用
          </h2>
          <div class="space-y-4">
            <router-link
              to="/login"
              class="block w-full py-3 px-6 text-center bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-700 transition-all duration-300 hover:scale-[1.02] font-medium"
            >
              登录
            </router-link>
            <router-link
              to="/register"
              class="block w-full py-3 px-6 text-center bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-700 transition-all duration-300 hover:scale-[1.02] font-medium"
            >
              注册
            </router-link>
            <router-link
              to="/forgot-password"
              class="block w-full py-3 px-6 text-center text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white transition-all duration-300"
            >
              忘记密码
            </router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fade-in {
  animation: fadeIn 0.6s ease-out;
}

.animate-fade-in-delay {
  animation: fadeIn 0.6s ease-out 0.2s backwards;
}
</style>