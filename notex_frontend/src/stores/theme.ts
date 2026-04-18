// 主题状态管理
import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useThemeStore = defineStore(
  'theme',
  () => {
    // 当前主题模式
    const isDark = ref(false)

    // 切换主题
    const toggleTheme = () => {
      isDark.value = !isDark.value
      applyTheme()
    }

    // 应用主题到 DOM
    const applyTheme = () => {
      if (isDark.value) {
        document.documentElement.classList.add('dark')
      } else {
        document.documentElement.classList.remove('dark')
      }
    }

    // 初始化主题
    const initTheme = () => {
      // 如果没有保存的主题，跟随系统
      if (isDark.value === false) {
        isDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
      }
      applyTheme()
    }

    return { isDark, toggleTheme, initTheme }
  },
  {
    persist: {
      key: 'theme-store',
      storage: localStorage,
      pick: ['isDark']
    }
  }
)