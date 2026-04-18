<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import { fetchIndexStatus, noteToEmbedded } from '@/api/note'
import type { NoteIndexStatus } from '@/types/api'

const router = useRouter()
const route = useRoute()
const themeStore = useThemeStore()
const authStore = useAuthStore()

const showMobileNavMenu = ref(false)
const showUserMenu = ref(false)
const isLoading = ref(false)
const indexingNoteUuid = ref('')
const notes = ref<NoteIndexStatus[]>([])
const showIndexErrorDialog = ref(false)
const indexErrorTitle = ref('')
const indexErrorDetail = ref('')

const navigationItems = [
  { label: '首页', path: '/dashboard' },
  { label: '搜索', path: '/search' },
  { label: 'NoteX AI', path: '/chat' },
  { label: '索引管理', path: '/index-management' }
]

const currentNavigationLabel = computed(() => {
  return navigationItems.find(item => item.path === route.path)?.label || '导航'
})

const sortedNotes = computed(() => {
  return [...notes.value].sort((a, b) => {
    const timeDiff = new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()

    if (a.isIndexed === b.isIndexed) {
      return timeDiff
    }

    return a.isIndexed ? 1 : -1
  })
})

const pendingIndexedNotes = computed(() => sortedNotes.value.filter(item => !item.isIndexed))
const completedIndexedNotes = computed(() => sortedNotes.value.filter(item => item.isIndexed))
const hasPendingIndexedNotes = computed(() => pendingIndexedNotes.value.length > 0)

const formatUpdatedAt = (value: string) => {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')

  return `${year}.${month}.${day} ${hours}:${minutes}`
}

const handleMobileNavigation = (path: string) => {
  showMobileNavMenu.value = false
  if (route.path !== path) {
    router.push(path)
  }
}

const handleLogout = () => {
  authStore.logout()
  router.push('/login')
}

const toggleUserMenu = () => {
  showUserMenu.value = !showUserMenu.value
}

const closeUserMenu = () => {
  showUserMenu.value = false
}

const closeIndexErrorDialog = () => {
  showIndexErrorDialog.value = false
  indexErrorTitle.value = ''
  indexErrorDetail.value = ''
}

const loadIndexStatus = async () => {
  try {
    isLoading.value = true
    notes.value = await fetchIndexStatus()
  } catch (error: any) {
    indexErrorTitle.value = error?.message || '获取索引状态失败'
    indexErrorDetail.value = typeof error?.detail === 'string' ? error.detail : ''
    showIndexErrorDialog.value = true
  } finally {
    isLoading.value = false
  }
}

const handleIndex = async (noteUuid: string) => {
  try {
    indexingNoteUuid.value = noteUuid
    await noteToEmbedded(noteUuid)
    await loadIndexStatus()
  } catch (error: any) {
    indexErrorTitle.value = error?.message || '索引失败'
    indexErrorDetail.value = error?.detail == null ? '' : String(error.detail)
    showIndexErrorDialog.value = true
  } finally {
    indexingNoteUuid.value = ''
  }
}

onMounted(() => {
  loadIndexStatus()
})
</script>

<template>
  <div class="min-h-screen bg-white dark:bg-gray-900 transition-colors duration-500">
    <div class="fixed top-0 left-0 right-0 z-10 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between h-16">
          <div class="flex items-center gap-4">
            <h1 class="text-2xl font-bold text-gray-900 dark:text-white">NoteX</h1>
            <div class="hidden md:flex items-center gap-2">
              <button
                v-for="item in navigationItems"
                :key="item.path"
                @click="router.push(item.path)"
                class="relative px-3 py-1.5 rounded-lg text-sm font-medium transition-colors"
                :class="route.path === item.path
                  ? 'bg-gray-900 text-white dark:bg-white dark:text-gray-900'
                  : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800'"
              >
                {{ item.label }}
                <span
                  v-if="item.path === '/index-management' && hasPendingIndexedNotes"
                  class="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-red-500 ring-2 ring-white dark:ring-gray-900"
                ></span>
              </button>
            </div>
            <div class="relative md:hidden">
              <button
                @click="showMobileNavMenu = !showMobileNavMenu"
                class="flex items-center gap-1.5 text-sm font-medium text-gray-700 dark:text-gray-200"
              >
                <span>{{ currentNavigationLabel }}</span>
                <svg class="w-4 h-4 text-gray-500 dark:text-gray-400 transition-transform duration-200" :class="{ 'rotate-180': showMobileNavMenu }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                </svg>
              </button>
              <Transition name="dropdown">
                <div
                  v-if="showMobileNavMenu"
                  class="absolute left-0 top-full mt-3 min-w-[148px] rounded-2xl bg-white/95 dark:bg-gray-800/95 shadow-lg backdrop-blur-md p-2"
                >
                  <button
                    v-for="item in navigationItems"
                    :key="item.path"
                    @click="handleMobileNavigation(item.path)"
                    class="w-full px-3 py-2 text-left text-sm rounded-2xl transition-colors"
                    :class="route.path === item.path
                      ? 'bg-gray-100 text-gray-900 dark:bg-gray-700 dark:text-white'
                      : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'"
                  >
                    {{ item.label }}
                    <span
                      v-if="item.path === '/index-management' && hasPendingIndexedNotes"
                      class="ml-2 inline-block w-2 h-2 rounded-full bg-red-500 align-middle"
                    ></span>
                  </button>
                </div>
              </Transition>
            </div>
          </div>

          <div class="flex items-center space-x-4">
            <button
              @click="themeStore.toggleTheme"
              class="p-2 rounded-lg bg-gray-100 dark:bg-gray-800 hover:bg-gray-200 dark:hover:bg-gray-700 transition-all duration-300"
              :title="themeStore.isDark ? '切换到明亮模式' : '切换到暗黑模式'"
            >
              <svg v-if="themeStore.isDark" class="w-5 h-5 text-gray-700 dark:text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
              <svg v-else class="w-5 h-5 text-gray-700 dark:text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
              </svg>
            </button>

            <div class="relative">
              <button
                @click="toggleUserMenu"
                class="flex items-center gap-3 p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
              >
                <div class="w-8 h-8 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center overflow-hidden">
                  <img v-if="authStore.avatar" :src="authStore.avatar" :alt="authStore.nickname" class="w-full h-full object-cover">
                  <svg v-else class="w-5 h-5 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                  </svg>
                </div>
                <span class="hidden sm:block text-sm font-medium text-gray-700 dark:text-gray-300">
                  {{ authStore.nickname || authStore.username }}
                </span>
                <svg class="w-4 h-4 text-gray-500 dark:text-gray-400 transition-transform" :class="{ 'rotate-180': showUserMenu }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                </svg>
              </button>

              <Transition name="dropdown">
                <div
                  v-if="showUserMenu"
                  class="absolute right-0 top-full mt-2 w-48 bg-white dark:bg-gray-800 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 py-2 z-50"
                >
                  <button
                    @click="router.push('/settings'); closeUserMenu()"
                    class="w-full px-4 py-2 text-left text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors flex items-center gap-3"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                    </svg>
                    设置
                  </button>
                  <div class="border-t border-gray-200 dark:border-gray-700 my-1"></div>
                  <button
                    @click="handleLogout"
                    class="w-full px-4 py-2 text-left text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors flex items-center gap-3"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                    </svg>
                    退出登录
                  </button>
                </div>
              </Transition>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-24 pb-10">
      <div class="flex flex-col gap-8">
        <div>
          <h2 class="text-3xl sm:text-4xl font-bold text-gray-900 dark:text-white">索引管理</h2>
        </div>

        <div v-if="isLoading" class="rounded-[18px] border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 p-6 text-center text-gray-500 dark:text-gray-400">
          正在加载索引状态...
        </div>

        <template v-else>
          <section class="space-y-4">
            <div class="flex items-center justify-between gap-4">
              <div>
                <h3 class="text-lg font-semibold text-gray-900 dark:text-white">待索引笔记</h3>
              </div>
              <div class="px-3 py-1 rounded-full bg-amber-100 dark:bg-amber-900/30 text-xs font-medium text-amber-700 dark:text-amber-300">
                {{ pendingIndexedNotes.length }} 条待处理
              </div>
            </div>

            <div v-if="pendingIndexedNotes.length === 0" class="rounded-[18px] border border-dashed border-gray-200 dark:border-gray-700 bg-gray-50/80 dark:bg-gray-800/50 p-6 text-center">
              <p class="text-base font-medium text-gray-900 dark:text-white">当前没有待索引笔记</p>
              <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">所有笔记都已经完成向量化</p>
            </div>

            <div v-else class="space-y-3">
              <div
                v-for="item in pendingIndexedNotes"
                :key="item.noteUuid"
                class="rounded-[18px] border border-amber-200 dark:border-amber-900/40 bg-white dark:bg-gray-800 p-4 sm:p-5"
              >
                <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                  <div class="min-w-0">
                    <div class="flex items-center gap-3 flex-wrap">
                      <h4 class="text-base font-semibold text-gray-900 dark:text-white truncate">{{ item.title }}</h4>
                      <span class="px-2.5 py-1 rounded-full bg-amber-100 dark:bg-amber-900/30 text-xs font-medium text-amber-700 dark:text-amber-300">未索引</span>
                    </div>
                    <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">最近更新：{{ formatUpdatedAt(item.updatedAt) }}</p>
                  </div>

                  <button
                    @click="handleIndex(item.noteUuid)"
                    :disabled="indexingNoteUuid === item.noteUuid"
                    class="h-10 px-4 rounded-xl bg-gray-900 dark:bg-white text-white dark:text-gray-900 text-sm font-medium hover:bg-gray-800 dark:hover:bg-gray-100 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {{ indexingNoteUuid === item.noteUuid ? '索引中...' : '开始索引' }}
                  </button>
                </div>
              </div>
            </div>
          </section>

          <section class="space-y-4">
            <div class="flex items-center justify-between gap-4">
              <div>
                <h3 class="text-lg font-semibold text-gray-900 dark:text-white">已完成索引</h3>
                <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">这些笔记已经可以参与语义检索</p>
              </div>
              <div class="px-3 py-1 rounded-full bg-emerald-100 dark:bg-emerald-900/30 text-xs font-medium text-emerald-700 dark:text-emerald-300">
                {{ completedIndexedNotes.length }} 条已完成
              </div>
            </div>

            <div v-if="completedIndexedNotes.length === 0" class="rounded-[18px] border border-dashed border-gray-200 dark:border-gray-700 bg-gray-50/80 dark:bg-gray-800/50 p-6 text-center">
              <p class="text-base font-medium text-gray-900 dark:text-white">还没有已索引笔记</p>
              <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">完成索引后会显示在这里</p>
            </div>

            <div v-else class="space-y-3">
              <div
                v-for="item in completedIndexedNotes"
                :key="item.noteUuid"
                class="rounded-[18px] border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4 sm:p-5"
              >
                <div class="flex items-center justify-between gap-4">
                  <div class="min-w-0">
                    <div class="flex items-center gap-3 flex-wrap">
                      <h4 class="text-base font-semibold text-gray-900 dark:text-white truncate">{{ item.title }}</h4>
                      <span class="px-2.5 py-1 rounded-full bg-emerald-100 dark:bg-emerald-900/30 text-xs font-medium text-emerald-700 dark:text-emerald-300">已索引</span>
                    </div>
                    <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">最近更新：{{ formatUpdatedAt(item.updatedAt) }}</p>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </template>
      </div>
    </div>

    <Transition name="fade">
      <div v-if="showIndexErrorDialog" class="fixed inset-0 z-40 flex items-center justify-center px-4">
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="closeIndexErrorDialog"></div>
        <div class="relative z-10 w-full max-w-2xl rounded-[14px] border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6 shadow-xl">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-white">索引操作失败</h2>
          <p class="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">{{ indexErrorTitle }}</p>
          <div v-if="indexErrorDetail" class="mt-4 rounded-xl bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700 p-4">
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">详细错误信息</p>
            <pre class="whitespace-pre-wrap break-all text-xs leading-6 text-gray-700 dark:text-gray-300">{{ indexErrorDetail }}</pre>
          </div>
          <div class="mt-6 flex justify-end">
            <button
              @click="closeIndexErrorDialog"
              class="px-4 py-2 rounded-xl bg-gray-900 dark:bg-white text-sm font-medium text-white dark:text-gray-900 hover:bg-gray-800 dark:hover:bg-gray-100 transition-colors"
            >
              我知道了
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>
