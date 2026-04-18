<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import { createNoteGroup, createNote, fetchNoteGroups, fetchRecentAccessNotes, fetchNoteDetail, fetchIndexStatus } from '@/api/note'
import type { NoteGroup, NoteRecentAccessVO } from '@/types/api'
import Toast from '@/components/Toast.vue'
import FolderSelector from '@/components/FolderSelector.vue'

const navigationItems = [
  { label: '首页', path: '/dashboard' },
  { label: '搜索', path: '/search' },
  { label: 'NoteX AI', path: '/chat' },
  { label: '索引管理', path: '/index-management' }
]

const router = useRouter()
const route = useRoute()
const themeStore = useThemeStore()
const authStore = useAuthStore()

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

// 新建笔记组弹窗
const showCreateGroupModal = ref(false)
const newGroupName = ref('')

// 新建笔记弹窗
const showCreateNoteModal = ref(false)
const newNoteTitle = ref('')
// 默认选中第一个文件夹，如果没有文件夹则保持 -1 状态（后续会处理）
const selectedGroupId = ref<number>(-1)
const noteGroups = ref<NoteGroup[]>([])
const isLoadingGroups = ref(false)

// 文件夹选择器弹窗
const showFolderSelector = ref(false)

// 无文件夹提示卡片
const showNoFolderHint = ref(false)

// 最近访问的笔记
const recentNotes = ref<NoteRecentAccessVO[]>([])
const isLoadingRecentNotes = ref(false)
const isRecentNotesCollapsed = ref(false)

// 用户菜单
const showUserMenu = ref(false)
const showMobileNavMenu = ref(false)
const hasPendingIndexedNotes = ref(false)

// 获取当前选中的文件夹名称
const selectedFolderName = computed(() => {
  if (selectedGroupId.value === -1) return '请选择文件夹'
  const folder = noteGroups.value.find((g: NoteGroup) => g.id === selectedGroupId.value)
  return folder?.groupName || '未知'
})

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
 * 获取问候语
 */
const greeting = computed(() => {
  const hour = new Date().getHours()
  const username = authStore.nickname || authStore.username || '用户'
  
  if (hour >= 5 && hour < 12) {
    return `早上好，${username}`
  } else if (hour >= 12 && hour < 14) {
    return `中午好，${username}`
  } else if (hour >= 14 && hour < 18) {
    return `下午好，${username}`
  } else {
    return `晚上好，${username}`
  }
})

/**
 * 创建笔记组
 */
const handleCreateGroup = async () => {
  if (!newGroupName.value.trim()) {
    showToast('请输入分组名称', 'error')
    return
  }

  try {
    await createNoteGroup({
      groupName: newGroupName.value.trim(),
      parentGroupId: 0
    })
    showToast('创建成功', 'success')
    newGroupName.value = ''
    showCreateGroupModal.value = false
    // 跳转到笔记库页面
    router.push('/notes')
  } catch (error: any) {
    showToast(error.message || '创建失败', 'error')
  }
}

/**
 * 获取笔记组列表
 */
const loadNoteGroups = async () => {
  try {
    isLoadingGroups.value = true
    noteGroups.value = await fetchNoteGroups()
    // 如果没有选中文件夹且存在文件夹，则默认选中第一个
    if (selectedGroupId.value === -1 && noteGroups.value.length > 0) {
      selectedGroupId.value = noteGroups.value[0].id
    }
  } catch (error: any) {
    showToast(error.message || '获取笔记组失败', 'error')
  } finally {
    isLoadingGroups.value = false
  }
}

/**
 * 获取最近访问的笔记
 */
const loadRecentNotes = async () => {
  try {
    isLoadingRecentNotes.value = true
    recentNotes.value = await fetchRecentAccessNotes()
  } catch (error: any) {
    // 静默失败，不显示错误提示
    console.error('获取最近访问笔记失败:', error)
  } finally {
    isLoadingRecentNotes.value = false
  }
}

const loadIndexStatus = async () => {
  try {
    const indexStatuses = await fetchIndexStatus()
    hasPendingIndexedNotes.value = indexStatuses.some(item => !item.isIndexed)
  } catch (error) {
    console.error('获取索引状态失败:', error)
  }
}

/**
 * 检测是否有足够空间展开
 */
const checkAvailableSpace = () => {
  const windowHeight = window.innerHeight
  const threshold = 700 // 阈值，超过此高度则自动展开
  const isMobile = window.innerWidth < 768
  
  if (!isMobile && windowHeight >= threshold) {
    isRecentNotesCollapsed.value = false
  } else {
    isRecentNotesCollapsed.value = true
  }
}

/**
 * 打开最近访问的笔记
 */
const openRecentNote = async (noteUuid: string) => {
  try {
    await fetchNoteDetail(noteUuid)
    router.push(`/note/${noteUuid}`)
  } catch (error: any) {
    showToast('笔记不存在', 'error')
  }
}

/**
 * 切换折叠状态
 */
const toggleRecentNotesCollapse = () => {
  isRecentNotesCollapsed.value = !isRecentNotesCollapsed.value
}

/**
 * 跳转到新建笔记页面
 */
const goToCreateNote = async () => {
  await loadNoteGroups()
  // 如果没有文件夹，显示提示卡片
  if (noteGroups.value.length === 0) {
    showNoFolderHint.value = true
  } else {
    showCreateNoteModal.value = true
  }
}

/**
 * 检测是否为移动端
 */
const isMobile = () => {
  return window.innerWidth < 768
}

/**
 * 创建笔记
 */
const handleCreateNote = async () => {
  if (!newNoteTitle.value.trim()) {
    showToast('请输入笔记标题', 'error')
    return
  }

  if (selectedGroupId.value === -1 || selectedGroupId.value === 0) {
    showToast('请选择有效的文件夹', 'error')
    return
  }

  try {
    const result = await createNote({
      title: newNoteTitle.value.trim(),
      groupId: selectedGroupId.value
    })
    
    newNoteTitle.value = ''
    // 重置为默认状态
    selectedGroupId.value = -1
    showCreateNoteModal.value = false
    showNoFolderHint.value = false
    
    // 移动端显示提示，PC端跳转到笔记编辑页面
    if (isMobile()) {
      showToast('笔记新建成功，如需编辑请使用桌面版', 'info')
    } else {
      router.push(`/note/${result.noteUuid}`)
    }
  } catch (error: any) {
    showToast(error.message || '创建失败', 'error')
  }
}

/**
 * 跳转到笔记库页面
 */
const goToNotesLibrary = () => {
  router.push('/notes')
}

/**
 * 退出登录
 */
const handleLogout = () => {
  authStore.logout()
  router.push('/login')
}

/**
 * 切换用户菜单
 */
const toggleUserMenu = () => {
  showUserMenu.value = !showUserMenu.value
}

/**
 * 关闭用户菜单
 */
const closeUserMenu = () => {
  showUserMenu.value = false
}

const currentNavigationLabel = computed(() => {
  return navigationItems.find(item => item.path === route.path)?.label || '导航'
})

const handleMobileNavigation = (path: string) => {
  showMobileNavMenu.value = false
  if (route.path !== path) {
    router.push(path)
  }
}

// 页面加载时获取笔记组和最近访问的笔记
onMounted(() => {
  loadNoteGroups()
  loadRecentNotes()
  loadIndexStatus()
  checkAvailableSpace()
  window.addEventListener('resize', checkAvailableSpace)
})

// 组件卸载时移除监听器
onUnmounted(() => {
  window.removeEventListener('resize', checkAvailableSpace)
})
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

    <!-- 顶部导航栏 -->
    <div class="fixed top-0 left-0 right-0 z-10 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between h-16">
          <!-- Logo -->
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

          <!-- 右侧操作区 -->
          <div class="flex items-center space-x-4">
            <!-- 主题切换 -->
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

            <!-- 用户菜单 -->
            <div class="relative">
              <button
                @click="toggleUserMenu"
                class="flex items-center gap-3 p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
              >
                <!-- 用户头像 -->
                <div class="w-8 h-8 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center overflow-hidden">
                  <img v-if="authStore.avatar" :src="authStore.avatar" :alt="authStore.nickname" class="w-full h-full object-cover" />
                  <svg v-else class="w-5 h-5 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                  </svg>
                </div>
                <!-- 用户昵称 -->
                <span class="hidden sm:block text-sm font-medium text-gray-700 dark:text-gray-300">
                  {{ authStore.nickname || authStore.username }}
                </span>
                <!-- 下拉箭头 -->
                <svg class="w-4 h-4 text-gray-500 dark:text-gray-400 transition-transform" :class="{ 'rotate-180': showUserMenu }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                </svg>
              </button>

              <!-- 下拉菜单 -->
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

    <!-- 主内容区域 - 居中显示 -->
    <div class="flex items-center justify-center min-h-screen px-4 py-20">
      <div class="w-full max-w-2xl space-y-6">
        <!-- 问候语 -->
        <div class="text-center">
          <h2 class="text-4xl font-bold text-gray-900 dark:text-white mb-2">
            {{ greeting }}
          </h2>
          <p class="text-gray-600 dark:text-gray-400">今天想记录些什么？</p>
        </div>

        <!-- 操作卡片 -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <!-- 新建笔记卡片 -->
          <button
            @click="goToCreateNote"
            class="group p-8 bg-gray-50 dark:bg-gray-800/50 rounded-2xl hover:bg-gray-100 dark:hover:bg-gray-800 transition-all duration-300 hover:scale-[1.02] text-left border border-gray-200 dark:border-gray-700"
          >
            <div class="flex flex-col items-center text-center">
              <div class="p-4 bg-gray-200 dark:bg-gray-700 rounded-xl group-hover:bg-gray-300 dark:group-hover:bg-gray-600 transition-colors mb-4">
                <svg class="w-8 h-8 text-gray-700 dark:text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                </svg>
              </div>
              <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-2">新建笔记</h3>
              <p class="text-sm text-gray-600 dark:text-gray-400">创建新的笔记并开始记录</p>
            </div>
          </button>

          <!-- 打开笔记库卡片 -->
          <button
            @click="goToNotesLibrary"
            class="group p-8 bg-gray-50 dark:bg-gray-800/50 rounded-2xl hover:bg-gray-100 dark:hover:bg-gray-800 transition-all duration-300 hover:scale-[1.02] text-left border border-gray-200 dark:border-gray-700"
          >
            <div class="flex flex-col items-center text-center">
              <div class="p-4 bg-gray-200 dark:bg-gray-700 rounded-xl group-hover:bg-gray-300 dark:group-hover:bg-gray-600 transition-colors mb-4">
                <svg class="w-8 h-8 text-gray-700 dark:text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                </svg>
              </div>
              <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-2">打开笔记库</h3>
              <p class="text-sm text-gray-600 dark:text-gray-400">浏览和管理所有笔记</p>
            </div>
          </button>
        </div>

        <!-- 最近访问的笔记卡片 -->
        <div v-if="recentNotes.length > 0" class="bg-gray-50 dark:bg-gray-800/50 rounded-2xl transition-all duration-300 border border-gray-200 dark:border-gray-700 overflow-hidden">
          <button
            @click="toggleRecentNotesCollapse"
            class="w-full px-5 py-4 flex items-center justify-between hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
          >
            <h3 class="text-lg font-semibold text-gray-900 dark:text-white">最近访问</h3>
            <svg
              class="w-5 h-5 text-gray-500 dark:text-gray-400 transition-transform duration-300"
              :class="{ 'rotate-180': isRecentNotesCollapsed }"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
            </svg>
          </button>
          <Transition name="collapse">
            <div v-show="!isRecentNotesCollapsed" class="max-h-80 overflow-y-auto">
              <div class="divide-y divide-gray-200 dark:divide-gray-700">
                <button
                  v-for="note in recentNotes.slice(0, 10)"
                  :key="note.noteUuid"
                  @click="openRecentNote(note.noteUuid)"
                  class="w-full px-5 py-3 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors text-left flex items-center justify-between group"
                >
                  <div class="flex items-center gap-3 flex-1 min-w-0">
                    <svg class="w-4 h-4 text-gray-500 dark:text-gray-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                    </svg>
                    <span class="text-sm text-gray-900 dark:text-white truncate group-hover:text-gray-700 dark:group-hover:text-gray-200 transition-colors">
                      {{ note.noteTitle }}
                    </span>
                  </div>
                </button>
              </div>
            </div>
          </Transition>
        </div>
      </div>
    </div>

    <!-- 创建笔记组弹窗 -->
    <Transition name="modal">
      <div
        v-if="showCreateGroupModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showCreateGroupModal = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showCreateGroupModal"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">创建笔记组</h3>
            <input
              v-model="newGroupName"
              type="text"
              class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all mb-4"
              placeholder="请输入分组名称"
              @keyup.enter="handleCreateGroup"
            />
            <div class="flex gap-3">
              <button
                @click="showCreateGroupModal = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleCreateGroup"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
              >
                创建
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 新建笔记弹窗 -->
    <Transition name="modal">
      <div
        v-if="showCreateNoteModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showCreateNoteModal = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showCreateNoteModal"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">新建笔记</h3>
            
            <!-- 笔记标题 -->
            <input
              v-model="newNoteTitle"
              type="text"
              class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all mb-4"
              placeholder="请输入笔记标题"
              @keyup.enter="handleCreateNote"
            />
            
            <!-- 文件夹选择 -->
            <div class="mb-4">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">选择文件夹</label>
              <button
                @click="showFolderSelector = true"
                class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all text-left flex items-center justify-between"
              >
                <span>{{ selectedFolderName }}</span>
                <svg class="w-4 h-4 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                </svg>
              </button>
            </div>
            
            <div class="flex gap-3">
              <button
                @click="showCreateNoteModal = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleCreateNote"
                :disabled="selectedGroupId === -1"
                :class="selectedGroupId === -1 ? 'opacity-50 cursor-not-allowed' : 'hover:bg-gray-800 dark:hover:bg-gray-100'"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg transition-all font-medium"
              >
                创建
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 文件夹选择器弹窗 -->
    <Transition name="modal">
      <div
        v-if="showFolderSelector"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showFolderSelector = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showFolderSelector"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">选择文件夹</h3>
            <FolderSelector
              :note-groups="noteGroups"
              v-model:selectedGroupId="selectedGroupId"
              :include-root="false"
            />
            <div class="mt-4 flex gap-3">
              <button
                @click="showFolderSelector = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="showFolderSelector = false"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
              >
                确定
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 无文件夹提示卡片 -->
    <Transition name="modal">
      <div
        v-if="showNoFolderHint"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showNoFolderHint = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showNoFolderHint"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <div class="text-center mb-6">
              <div class="w-16 h-16 mx-auto mb-4 bg-gray-100 dark:bg-gray-700 rounded-full flex items-center justify-center">
                <svg class="w-8 h-8 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 13h6m-3-3v6m-9 1V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
                </svg>
              </div>
              <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-2">暂无文件夹</h3>
              <p class="text-gray-600 dark:text-gray-400">请先创建一个文件夹来存放您的笔记</p>
            </div>
            <div class="flex gap-3">
              <button
                @click="showNoFolderHint = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="goToNotesLibrary"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
              >
                前往创建
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
/* 弹窗背景过渡动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.3s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

/* 弹窗内容过渡动画 */
.modal-content-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.modal-content-leave-active {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.modal-content-enter-from {
  opacity: 0;
  transform: scale(0.95) translateY(-10px);
}

.modal-content-leave-to {
  opacity: 0;
  transform: scale(0.95) translateY(-10px);
}

/* 下拉菜单过渡动画 */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.2s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* 折叠过渡动画 */
.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.3s ease;
}

.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
