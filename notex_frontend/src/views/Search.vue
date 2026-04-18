<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import { queryByKeyword } from '@/api/rag'
import { fetchIndexStatus, fetchNoteDetail } from '@/api/note'
import type { NoteDetail, RagSearchResult } from '@/types/api'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import Toast from '@/components/Toast.vue'

const router = useRouter()
const route = useRoute()
const themeStore = useThemeStore()
const authStore = useAuthStore()
const showMobileNavMenu = ref(false)
const showUserMenu = ref(false)
const keyword = ref('')
const searched = ref(false)
const isSearching = ref(false)
const results = ref<RagSearchResult[]>([])
const showLowMatchResults = ref(false)
const showEmbeddingModelDialog = ref(false)
const showApiServiceErrorDialog = ref(false)
const apiServiceErrorDetail = ref('')
const isMobile = ref(false)
const previewId = 'rag-search-preview'
const previewAnchorId = 'rag-search-preview-anchor'
const previewContainerRef = ref<HTMLElement | null>(null)
const activePreviewResult = ref<RagSearchResult | null>(null)
const previewNoteDetail = ref<NoteDetail | null>(null)
const isPreviewLoading = ref(false)
const previewErrorMessage = ref('')
const hasPendingIndexedNotes = ref(false)
const hasShownIndexWarning = ref(false)
const toast = ref<{
  show: boolean
  message: string
  type: 'success' | 'error' | 'info' | 'warning'
}>({
  show: false,
  message: '',
  type: 'info'
})
const navigationItems = [
  { label: '首页', path: '/dashboard' },
  { label: '搜索', path: '/search' },
  { label: 'NoteX AI', path: '/chat' },
  { label: '索引管理', path: '/index-management' }
]
const currentNavigationLabel = computed(() => {
  return navigationItems.find(item => item.path === route.path)?.label || '导航'
})
const editorTheme = computed(() => themeStore.isDark ? 'dark' : 'light')
const showPreview = computed(() => activePreviewResult.value !== null)
const canUseDesktopPreview = computed(() => !isMobile.value && window.innerWidth >= 1280)
const isDesktopPreviewVisible = computed(() => showPreview.value && canUseDesktopPreview.value)
const shouldUsePreviewDialog = computed(() => showPreview.value && !canUseDesktopPreview.value)
const previewTitle = computed(() => previewNoteDetail.value?.title || activePreviewResult.value?.noteTitle || '笔记预览')

const showToast = (message: string, type: 'success' | 'error' | 'info' | 'warning') => {
  toast.value = { show: true, message, type }
}

const closeToast = () => {
  toast.value.show = false
}

const checkMobile = () => {
  isMobile.value = window.innerWidth < 1024
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

const closeEmbeddingModelDialog = () => {
  showEmbeddingModelDialog.value = false
}

const closeApiServiceErrorDialog = () => {
  showApiServiceErrorDialog.value = false
  apiServiceErrorDetail.value = ''
}

const closePreview = () => {
  activePreviewResult.value = null
  previewNoteDetail.value = null
  previewErrorMessage.value = ''
}

const goToSettingsForEmbeddingModel = () => {
  showEmbeddingModelDialog.value = false
  closeUserMenu()
  router.push('/settings')
}

const highMatchResults = computed(() => {
  return results.value.filter((item: RagSearchResult) => item.contentMatchRate > 0.5)
})

const lowMatchResults = computed(() => {
  return results.value.filter((item: RagSearchResult) => item.contentMatchRate <= 0.5)
})

const hasResults = computed(() => results.value.length > 0)

const formatMatchRate = (rate: number) => `${Math.round(rate * 100)}%`

const escapeHtml = (value: string) => {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const escapeRegExp = (value: string) => {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

const keywordTokens = computed(() => {
  return Array.from(new Set(
    keyword.value
      .split(/[\s,，.。!！?？:：;；、()（）\[\]【】{}「」『』'"“”‘’<>《》/\\|\-—_+=~`]+/)
      .map(token => token.trim())
      .filter(Boolean)
      .sort((a, b) => b.length - a.length)
  ))
})

const highlightContent = (content: string) => {
  const escapedContent = escapeHtml(content)

  if (keywordTokens.value.length === 0) {
    return escapedContent
  }

  const pattern = keywordTokens.value.map(token => escapeRegExp(escapeHtml(token))).join('|')

  if (!pattern) {
    return escapedContent
  }

  return escapedContent.replace(
    new RegExp(`(${pattern})`, 'gi'),
    '<mark class="bg-yellow-200/80 dark:bg-yellow-500/30 text-gray-900 dark:text-yellow-100 px-1 rounded-md">$1</mark>'
  )
}

const getPreviewRoot = () => {
  const container = previewContainerRef.value

  if (!container) {
    return null
  }

  return container.querySelector('.md-editor-preview-wrapper') as HTMLElement | null
}

const locatePreviewContent = async () => {
  const targetContent = activePreviewResult.value?.content?.trim()

  if (!targetContent) {
    return
  }

  await nextTick()

  window.requestAnimationFrame(() => {
    const previewRoot = getPreviewRoot()

    if (!previewRoot) {
      return
    }

    const target = previewRoot.querySelector(`#${previewAnchorId}`) as HTMLElement | null

    if (target) {
      target.scrollIntoView({ block: 'center', behavior: 'smooth' })
      return
    }

    const textNodes = Array.from(previewRoot.querySelectorAll<HTMLElement>('p, li, blockquote, pre, code, h1, h2, h3, h4, h5, h6, td'))
    const fallback = textNodes.find(node => node.textContent?.replace(/\s+/g, ' ').includes(targetContent.replace(/\s+/g, ' ')))

    fallback?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  })
}

const buildPreviewContent = (content: string, selectedText: string) => {
  if (!selectedText.trim()) {
    return content
  }

  const target = selectedText.trim()

  if (!content.includes(target)) {
    return content
  }

  return content.replace(target, `<span id="${previewAnchorId}">${target}</span>`)
}

const previewContent = computed(() => {
  if (!previewNoteDetail.value || !activePreviewResult.value) {
    return ''
  }

  return buildPreviewContent(previewNoteDetail.value.content || '', activePreviewResult.value.content || '')
})

const handleSearch = async () => {
  const searchContent = keyword.value.trim()
  if (!searchContent) {
    searched.value = true
    results.value = []
    showLowMatchResults.value = false
    return
  }

  try {
    isSearching.value = true
    searched.value = true
    showLowMatchResults.value = false
    showEmbeddingModelDialog.value = false
    showApiServiceErrorDialog.value = false
    apiServiceErrorDetail.value = ''
    closePreview()
    results.value = await queryByKeyword({ searchContent })
  } catch (error: any) {
    results.value = []
    if (error?.message === '未设置Embedded模型') {
      showEmbeddingModelDialog.value = true
    } else if (error?.message === 'API Service Error') {
      showApiServiceErrorDialog.value = true
      apiServiceErrorDetail.value = typeof error?.detail === 'string'
        ? error.detail
        : '未获取到详细错误信息'
    }
    console.error('搜索失败:', error)
  } finally {
    isSearching.value = false
  }
}

const openPreview = async (item: RagSearchResult) => {
  activePreviewResult.value = item
  previewErrorMessage.value = ''

  try {
    isPreviewLoading.value = true
    previewNoteDetail.value = await fetchNoteDetail(item.noteUuid)
    await locatePreviewContent()
  } catch (error: any) {
    previewNoteDetail.value = null
    previewErrorMessage.value = error?.message || '加载预览失败'
  } finally {
    isPreviewLoading.value = false
  }
}

const goToNote = (noteUuid: string) => {
  router.push(`/note/${noteUuid}`)
}

const loadIndexStatus = async () => {
  try {
    const indexStatuses = await fetchIndexStatus()
    const hasPending = indexStatuses.some(item => !item.isIndexed)
    hasPendingIndexedNotes.value = hasPending

    if (hasPending && !hasShownIndexWarning.value) {
      showToast('当前存在未索引的笔记，如不及时索引，将降低搜索能力', 'warning')
      hasShownIndexWarning.value = true
    }
  } catch (error) {
    console.error('获取索引状态失败:', error)
  }
}

const handlePreviewEdit = () => {
  if (activePreviewResult.value) {
    goToNote(activePreviewResult.value.noteUuid)
  }
}

watch(() => activePreviewResult.value?.content, async () => {
  if (activePreviewResult.value && previewNoteDetail.value) {
    await locatePreviewContent()
  }
})

watch(previewContent, async () => {
  if (activePreviewResult.value && previewNoteDetail.value) {
    await locatePreviewContent()
  }
})

onMounted(() => {
  checkMobile()
  loadIndexStatus()
  window.addEventListener('resize', checkMobile)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<template>
  <div class="min-h-screen bg-white dark:bg-gray-900 transition-colors duration-500">
    <Toast
      v-if="toast.show"
      :message="toast.message"
      :type="toast.type"
      @close="closeToast"
    />

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

    <div
      class="px-4 sm:px-6 lg:px-8 transition-all duration-500"
      :class="searched ? 'pt-24 pb-8' : 'min-h-screen flex items-center justify-center pt-24 pb-8'"
    >
      <div class="w-full mx-auto transition-all duration-500" :class="isDesktopPreviewVisible ? 'max-w-none' : 'max-w-4xl'">
        <div class="flex flex-col gap-6 xl:items-start" :class="isDesktopPreviewVisible ? 'xl:flex-row' : ''">
          <div
            class="min-w-0 rounded-[24px] transition-all duration-500"
            :class="isDesktopPreviewVisible ? 'xl:flex-1 xl:basis-1/2' : 'w-full'"
          >
            <div class="transition-all duration-500" :class="searched ? (isDesktopPreviewVisible ? 'p-6' : 'p-0 border-0 bg-transparent shadow-none backdrop-blur-none') : 'max-w-2xl mx-auto p-0 text-center border-0 bg-transparent shadow-none backdrop-blur-none'">
              <h1 class="text-3xl sm:text-4xl font-bold text-gray-900 dark:text-white">想找点什么?</h1>
              <p class="mt-3 text-sm sm:text-base text-gray-500 dark:text-gray-400">输入关键词，检索与你笔记语义最相关的内容</p>

              <form @submit.prevent="handleSearch" class="mt-6">
                <div class="rounded-[14px] bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-sm dark:shadow-none p-2 transition-all duration-300">
                  <div class="flex flex-col sm:flex-row sm:items-center gap-2">
                    <div class="flex-1 flex items-center gap-3 px-3 sm:px-4">
                      <svg class="w-5 h-5 text-gray-400 dark:text-gray-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-4.35-4.35m1.85-5.15a7 7 0 11-14 0 7 7 0 0114 0z" />
                      </svg>
                      <input
                        v-model="keyword"
                        type="text"
                        placeholder="搜索笔记内容、标题或知识片段"
                        class="w-full h-12 bg-transparent text-base text-gray-900 dark:text-white placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none"
                      >
                    </div>
                    <button
                      type="submit"
                      :disabled="isSearching"
                      class="h-12 px-5 sm:px-6 rounded-xl bg-gray-900 dark:bg-white text-white dark:text-gray-900 text-sm font-medium transition-all duration-300 hover:bg-gray-800 dark:hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {{ isSearching ? '搜索中...' : '搜索' }}
                    </button>
                  </div>
                </div>
              </form>
            </div>

            <div v-if="searched" class="mt-6 sm:mt-8" :class="isDesktopPreviewVisible ? 'px-6 pb-6' : ''">
              <div v-if="isSearching" class="rounded-[14px] border border-gray-200 dark:border-gray-700 bg-white/80 dark:bg-gray-800/80 p-5 text-center text-gray-500 dark:text-gray-400">
                正在检索相关内容...
              </div>

              <template v-else>
                <div v-if="!hasResults" class="rounded-[14px] border border-dashed border-gray-200 dark:border-gray-700 bg-gray-50/80 dark:bg-gray-800/50 p-6 text-center">
                  <p class="text-lg font-medium text-gray-900 dark:text-white">暂无搜索结果</p>
                  <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">换个关键词再试试</p>
                </div>

                <template v-else>
                  <div v-if="highMatchResults.length > 0" class="space-y-3">
                    <button
                      v-for="item in highMatchResults"
                      :key="`${item.noteUuid}-${item.content}`"
                      @click="openPreview(item)"
                      class="w-full text-left rounded-[14px] border p-4 sm:p-5 shadow-sm transition-all duration-300 hover:-translate-y-0.5 hover:shadow-md"
                      :class="activePreviewResult?.noteUuid === item.noteUuid && activePreviewResult?.content === item.content
                        ? 'border-gray-900 dark:border-white bg-gray-50 dark:bg-gray-800 ring-1 ring-gray-900/10 dark:ring-white/20'
                        : 'border-gray-200 dark:border-gray-700 bg-white/90 dark:bg-gray-800/90'"
                    >
                      <div class="flex items-start justify-between gap-4">
                        <div class="min-w-0">
                          <h2 class="text-lg font-semibold text-gray-900 dark:text-white truncate">{{ item.noteTitle }}</h2>
                          <p class="mt-2 text-sm leading-7 text-gray-600 dark:text-gray-300 line-clamp-4" v-html="highlightContent(item.content)"></p>
                        </div>
                        <div class="flex-shrink-0 px-3 py-1 rounded-full bg-gray-100 dark:bg-gray-700 text-xs font-medium text-gray-600 dark:text-gray-300">
                          {{ formatMatchRate(item.contentMatchRate) }}
                        </div>
                      </div>
                    </button>
                  </div>

                  <div v-if="lowMatchResults.length > 0" class="rounded-[14px] border border-gray-200 dark:border-gray-700 bg-gray-50/80 dark:bg-gray-800/60 p-4 mt-3">
                    <div v-if="highMatchResults.length === 0" class="mb-4 px-1">
                      <p class="text-base sm:text-lg font-medium text-gray-900 dark:text-white">似乎没找到比较匹配的结果哦</p>
                      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">但是下方的结果可能对您有帮助</p>
                    </div>
                    <button
                      @click="showLowMatchResults = !showLowMatchResults"
                      class="w-full flex items-center justify-between gap-4 text-left"
                    >
                      <div>
                        <p class="text-sm font-medium text-gray-900 dark:text-white">这些是匹配率较低的结果</p>
                        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">匹配率 50% 及以下的内容已默认折叠</p>
                      </div>
                      <svg class="w-5 h-5 text-gray-400 dark:text-gray-500 transition-transform duration-200" :class="{ 'rotate-180': showLowMatchResults }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                      </svg>
                    </button>

                    <div v-if="showLowMatchResults" class="mt-3 space-y-3">
                      <button
                        v-for="item in lowMatchResults"
                        :key="`${item.noteUuid}-${item.content}`"
                        @click="openPreview(item)"
                        class="w-full text-left rounded-xl border p-4 transition-colors"
                        :class="activePreviewResult?.noteUuid === item.noteUuid && activePreviewResult?.content === item.content
                          ? 'border-gray-900 dark:border-white bg-gray-100 dark:bg-gray-700/80'
                          : 'border-transparent bg-white dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700'"
                      >
                        <div class="flex items-start justify-between gap-4">
                          <div class="min-w-0">
                            <h3 class="text-sm font-medium text-gray-900 dark:text-white truncate">{{ item.noteTitle }}</h3>
                            <p class="mt-2 text-sm leading-6 text-gray-500 dark:text-gray-400 line-clamp-3" v-html="highlightContent(item.content)"></p>
                          </div>
                          <div class="flex-shrink-0 px-2.5 py-1 rounded-full bg-gray-100 dark:bg-gray-700 text-xs text-gray-500 dark:text-gray-300">
                            {{ formatMatchRate(item.contentMatchRate) }}
                          </div>
                        </div>
                      </button>
                    </div>
                  </div>
                </template>
              </template>
            </div>
          </div>

            <Transition name="slide-preview">
              <div
                v-if="isDesktopPreviewVisible"
                ref="previewContainerRef"
                class="hidden xl:flex xl:flex-1 xl:basis-1/2 xl:sticky xl:top-24 xl:h-[calc(100vh-8rem)] xl:max-h-[calc(100vh-8rem)] flex-col overflow-hidden rounded-[24px] border border-gray-200 bg-white/95 shadow-sm backdrop-blur-md dark:border-gray-700 dark:bg-gray-800/80"
              >
                <div class="flex items-center justify-between gap-3 border-b border-gray-200 dark:border-gray-700 px-6 py-5">
                  <div class="min-w-0">
                    <p class="text-xs font-medium uppercase tracking-[0.18em] text-gray-400 dark:text-gray-500">笔记预览</p>
                    <h2 class="mt-1 truncate text-lg font-semibold text-gray-900 dark:text-white">{{ previewTitle }}</h2>
                  </div>
                  <div class="flex items-center gap-2">
                    <button
                      @click="handlePreviewEdit"
                      class="inline-flex items-center gap-2 rounded-xl bg-gray-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-gray-800 dark:bg-white dark:text-gray-900 dark:hover:bg-gray-100"
                    >
                      跳转编辑
                    </button>
                    <button
                      @click="closePreview"
                      class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-gray-100 text-gray-600 transition-colors hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
                    >
                      <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                      </svg>
                    </button>
                  </div>
                </div>

                <div class="min-h-0 flex-1 overflow-y-auto px-6 py-5">
                  <div v-if="isPreviewLoading" class="flex h-full min-h-[320px] items-center justify-center text-sm text-gray-500 dark:text-gray-400">
                    正在加载预览...
                  </div>
                  <div v-else-if="previewErrorMessage" class="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-600 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-300">
                    {{ previewErrorMessage }}
                  </div>
                  <div v-else-if="previewNoteDetail" class="rag-preview-panel min-h-full rounded-2xl bg-gray-50/80 p-4 dark:bg-gray-900/60">
                    <MdPreview
                      :id="previewId"
                      :modelValue="previewContent"
                      :theme="editorTheme"
                      previewTheme="default"
                      language="zh-CN"
                    />
                  </div>
                </div>
              </div>
            </Transition>
        </div>
      </div>
    </div>

    <Transition name="fade">
      <div v-if="shouldUsePreviewDialog" class="fixed inset-0 z-40">
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="closePreview"></div>
        <div class="absolute inset-0 flex items-center justify-center px-4 py-6">
          <div class="w-full max-w-3xl h-[78vh] max-h-[720px] min-h-[420px] flex flex-col overflow-hidden rounded-[24px] border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-2xl">
          <div class="flex items-center justify-between gap-3 border-b border-gray-200 dark:border-gray-700 px-4 py-4">
            <div class="min-w-0">
              <p class="text-xs font-medium uppercase tracking-[0.18em] text-gray-400 dark:text-gray-500">笔记预览</p>
              <h2 class="mt-1 truncate text-base font-semibold text-gray-900 dark:text-white">{{ previewTitle }}</h2>
            </div>
            <div class="flex items-center gap-2">
              <button
                @click="handlePreviewEdit"
                class="rounded-xl bg-gray-900 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-gray-800 dark:bg-white dark:text-gray-900 dark:hover:bg-gray-100"
              >
                跳转编辑
              </button>
              <button
                @click="closePreview"
                class="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-gray-100 text-gray-600 transition-colors hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
              >
                <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>

          <div ref="previewContainerRef" class="min-h-0 flex-1 overflow-y-auto px-4 py-4 overscroll-contain">
            <div v-if="isPreviewLoading" class="flex h-full min-h-[320px] items-center justify-center text-sm text-gray-500 dark:text-gray-400">
              正在加载预览...
            </div>
            <div v-else-if="previewErrorMessage" class="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-600 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-300">
              {{ previewErrorMessage }}
            </div>
            <div v-else-if="previewNoteDetail" class="rag-preview-panel min-h-full rounded-2xl bg-gray-50/80 p-4 dark:bg-gray-900/60">
              <MdPreview
                :id="previewId"
                :modelValue="previewContent"
                :theme="editorTheme"
                previewTheme="default"
                language="zh-CN"
              />
            </div>
          </div>
        </div>
        </div>
      </div>
    </Transition>

    <Transition name="fade">
      <div v-if="showEmbeddingModelDialog" class="fixed inset-0 z-40 flex items-center justify-center px-4">
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="closeEmbeddingModelDialog"></div>
        <div class="relative z-10 w-full max-w-md rounded-[14px] border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6 shadow-xl">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-white">尚未配置嵌入模型</h2>
          <p class="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">
            当前无法进行 RAG 搜索，请前往设置页，在 AI 模型配置中完成嵌入模型配置。
          </p>
          <div class="mt-6 flex justify-end gap-3">
            <button
              @click="closeEmbeddingModelDialog"
              class="px-4 py-2 rounded-xl bg-gray-100 dark:bg-gray-700 text-sm font-medium text-gray-700 dark:text-gray-200 hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors"
            >
              取消
            </button>
            <button
              @click="goToSettingsForEmbeddingModel"
              class="px-4 py-2 rounded-xl bg-gray-900 dark:bg-white text-sm font-medium text-white dark:text-gray-900 hover:bg-gray-800 dark:hover:bg-gray-100 transition-colors"
            >
              前往设置
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <Transition name="fade">
      <div v-if="showApiServiceErrorDialog" class="fixed inset-0 z-40 flex items-center justify-center px-4">
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="closeApiServiceErrorDialog"></div>
        <div class="relative z-10 w-full max-w-2xl rounded-[14px] border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6 shadow-xl">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-white">API 服务出现问题</h2>
          <p class="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">
            当前搜索请求已到达后端，但后端调用外部 AI / API 服务时失败。请检查相关模型服务配置。
          </p>
          <div class="mt-4 rounded-xl bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700 p-4">
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">详细错误信息</p>
            <pre class="whitespace-pre-wrap break-all text-xs leading-6 text-gray-700 dark:text-gray-300">{{ apiServiceErrorDetail }}</pre>
          </div>
          <div class="mt-6 flex justify-end">
            <button
              @click="closeApiServiceErrorDialog"
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

<style scoped>
:deep(#rag-search-preview-anchor) {
  background: rgba(250, 204, 21, 0.28);
  color: inherit;
  border-radius: 0.5rem;
  box-shadow: 0 0 0 1px rgba(250, 204, 21, 0.2);
  padding: 0.1rem 0.25rem;
}

:global(.dark) :deep(#rag-search-preview-anchor) {
  background: rgba(250, 204, 21, 0.2);
  box-shadow: 0 0 0 1px rgba(250, 204, 21, 0.28);
}

:deep(.rag-preview-panel .md-editor-preview-wrapper) {
  padding: 0;
}

:deep(.rag-preview-panel .md-editor-preview) {
  background: transparent;
}

.slide-preview-enter-active,
.slide-preview-leave-active {
  transition: all 0.28s ease;
}

.slide-preview-enter-from,
.slide-preview-leave-to {
  opacity: 0;
  transform: translateX(24px);
}
</style>
