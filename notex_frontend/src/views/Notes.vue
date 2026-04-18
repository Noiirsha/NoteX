<script setup lang="ts">
import { ref, onMounted, computed, nextTick, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import {
  fetchNoteGroups,
  fetchNotesByGroup,
  createNote,
  createNoteGroup,
  modifyNote,
  deleteNote,
  modifyNoteGroup,
  deleteNoteGroup
} from '@/api/note'
import type { NoteBasicInfo, NoteGroup } from '@/types/api'
import Toast from '@/components/Toast.vue'
import FolderSelector from '@/components/FolderSelector.vue'

const router = useRouter()
const route = useRoute()
const themeStore = useThemeStore()
const authStore = useAuthStore()

// 根据groupId query参数自动导航到指定文件夹
const navigateToGroupById = async (groupId: number) => {
  if (groupId === 0) {
    currentPath.value = [{ id: 0, name: '根目录' }]
    return
  }

  // 查找完整路径
  const path: { id: number; name: string }[] = []
  let currentId = groupId

  while (currentId !== 0) {
    const group = noteGroups.value.find((g: NoteGroup) => g.id === currentId)
    if (!group) {
      currentPath.value = [{ id: 0, name: '根目录' }]
      return
    }
    path.unshift({ id: currentId, name: group.groupName })
    currentId = group.parentGroupId
  }

  // 添加根目录
  path.unshift({ id: 0, name: '根目录' })
  currentPath.value = path
}

// 当前路径（面包屑导航）
const currentPath = ref<{ id: number; name: string }[]>([
  { id: 0, name: '根目录' }
])

// 当前分组ID（0表示根目录）
const currentGroupId = computed(() => {
  if (currentPath.value.length === 0) return 0
  return currentPath.value[currentPath.value.length - 1].id
})

// 笔记组列表
const noteGroups = ref<NoteGroup[]>([])
// 笔记列表
const notes = ref<NoteBasicInfo[]>([])
const isLoading = ref(false)

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

// 新建笔记弹窗
const showCreateNoteModal = ref(false)
const newNoteTitle = ref('')

// 新建文件夹弹窗
const showCreateFolderModal = ref(false)
const newFolderName = ref('')

// 修改笔记弹窗
const showEditNoteModal = ref(false)
const editingNote = ref<NoteBasicInfo | null>(null)
const editNoteTitle = ref('')

// 修改文件夹弹窗
const showEditFolderModal = ref(false)
const editingFolder = ref<NoteGroup | null>(null)
const editFolderName = ref('')

// 移动笔记弹窗
const showMoveNoteModal = ref(false)
const movingNote = ref<NoteBasicInfo | null>(null)
// 移动笔记时，默认选中 -1，后续会在打开弹窗时处理
const selectedTargetGroupId = ref(-1)

// 移动文件夹弹窗
const showMoveFolderModal = ref(false)
const movingFolder = ref<NoteGroup | null>(null)
const selectedFolderTargetGroupId = ref(-1)

// 删除笔记确认弹窗
const showDeleteNoteModal = ref(false)
const deletingNote = ref<NoteBasicInfo | null>(null)

// 删除文件夹确认弹窗
const showDeleteFolderModal = ref(false)
const deletingFolder = ref<NoteGroup | null>(null)

// 用户菜单
const showUserMenu = ref(false)

// 操作菜单状态
const showActionMenu = ref<{ id: string; type: 'folder' | 'note'; item: NoteGroup | NoteBasicInfo } | null>(null)

// 移动端操作菜单
const showMobileMenu = ref(false)
const mobileMenuTarget = ref<{ type: 'folder' | 'note'; item: NoteGroup | NoteBasicInfo } | null>(null)
const longPressTimer = ref<number | null>(null)
const isMobile = ref(false)

const getMenuItemId = (type: 'folder' | 'note', item: NoteGroup | NoteBasicInfo) => {
  if (type === 'folder') {
    return `folder-${item.id}`
  }

  return `note-${(item as NoteBasicInfo).noteUuid}`
}

// 打开操作菜单
const openActionMenu = (type: 'folder' | 'note', item: NoteGroup | NoteBasicInfo) => {
  const id = getMenuItemId(type, item)
  showActionMenu.value = { id, type, item }
}

// 关闭操作菜单
const closeActionMenu = () => {
  showActionMenu.value = null
}

// 检查操作菜单是否打开
const isActionMenuOpen = (type: 'folder' | 'note', item: NoteGroup | NoteBasicInfo) => {
  const id = getMenuItemId(type, item)
  return showActionMenu.value?.id === id
}

// 检测是否为移动端
const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

// 长按处理
const handleLongPressStart = (type: 'folder' | 'note', item: NoteGroup | NoteBasicInfo) => {
  if (!isMobile.value) return
  longPressTimer.value = window.setTimeout(() => {
    mobileMenuTarget.value = { type, item }
    showMobileMenu.value = true
    // 震动反馈
    if (navigator.vibrate) {
      navigator.vibrate(50)
    }
  }, 500)
}

const handleLongPressEnd = () => {
  if (longPressTimer.value) {
    clearTimeout(longPressTimer.value)
    longPressTimer.value = null
  }
}

const handleLongPressMove = () => {
  handleLongPressEnd()
}

// 关闭移动端菜单
const closeMobileMenu = () => {
  showMobileMenu.value = false
  mobileMenuTarget.value = null
}

// 移动端菜单操作
const handleMobileEdit = () => {
  if (!mobileMenuTarget.value) return
  if (mobileMenuTarget.value.type === 'folder') {
    openEditFolderModal(mobileMenuTarget.value.item as NoteGroup)
  } else {
    openEditNoteModal(mobileMenuTarget.value.item as NoteBasicInfo)
  }
  closeMobileMenu()
}

const handleMobileMove = () => {
  if (!mobileMenuTarget.value) return
  if (mobileMenuTarget.value.type === 'note') {
    openMoveNoteModal(mobileMenuTarget.value.item as NoteBasicInfo)
  } else {
    openMoveFolderModal(mobileMenuTarget.value.item as NoteGroup)
  }
  closeMobileMenu()
}

const handleMobileDelete = () => {
  if (!mobileMenuTarget.value) return
  if (mobileMenuTarget.value.type === 'folder') {
    openDeleteFolderModal(mobileMenuTarget.value.item as NoteGroup)
  } else {
    openDeleteNoteModal(mobileMenuTarget.value.item as NoteBasicInfo)
  }
  closeMobileMenu()
}

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
 * 获取笔记组列表
 */
const loadNoteGroups = async () => {
  try {
    isLoading.value = true
    noteGroups.value = await fetchNoteGroups()
  } catch (error: any) {
    showToast(error.message || '获取笔记组失败', 'error')
  } finally {
    isLoading.value = false
  }
}

/**
 * 获取当前目录下的笔记
 */
const loadNotes = async () => {
  if (currentGroupId.value === 0) {
    notes.value = []
    return
  }
  try {
    isLoading.value = true
    notes.value = await fetchNotesByGroup(currentGroupId.value)
  } catch (error: any) {
    showToast(error.message || '获取笔记失败', 'error')
  } finally {
    isLoading.value = false
  }
}

/**
 * 获取当前目录下的子文件夹
 */
const subFolders = computed(() => {
  return noteGroups.value.filter((group: NoteGroup) => group.parentGroupId === currentGroupId.value)
})

/**
 * 导航到指定目录
 */
const navigateTo = (groupId: number, groupName: string) => {
  currentPath.value.push({ id: groupId, name: groupName })
  loadNotes()
  scrollToLatestBreadcrumb()
}

/**
 * 点击面包屑导航
 */
const navigateToBreadcrumb = (index: number) => {
  currentPath.value = currentPath.value.slice(0, index + 1)
  loadNotes()
  scrollToLatestBreadcrumb()
}

/**
 * 进入文件夹
 */
const enterFolder = (folder: NoteGroup) => {
  navigateTo(folder.id, folder.groupName)
}

/**
 * 创建笔记
 */
const handleCreateNote = async () => {
  if (!newNoteTitle.value.trim()) {
    showToast('请输入笔记标题', 'error')
    return
  }

  try {
    await createNote({
      title: newNoteTitle.value.trim(),
      groupId: currentGroupId.value
    })
    showToast('创建成功', 'success')
    newNoteTitle.value = ''
    showCreateNoteModal.value = false
    await loadNotes()
  } catch (error: any) {
    showToast(error.message || '创建失败', 'error')
  }
}

/**
 * 创建文件夹
 */
const handleCreateFolder = async () => {
  if (!newFolderName.value.trim()) {
    showToast('请输入文件夹名称', 'error')
    return
  }

  try {
    await createNoteGroup({
      groupName: newFolderName.value.trim(),
      parentGroupId: currentGroupId.value
    })
    showToast('创建成功', 'success')
    newFolderName.value = ''
    showCreateFolderModal.value = false
    await loadNoteGroups()
    // 如果在子目录下，同时刷新笔记列表
    if (currentGroupId.value !== 0) {
      await loadNotes()
    }
  } catch (error: any) {
    showToast(error.message || '创建失败', 'error')
  }
}

/**
 * 打开修改笔记弹窗
 */
const openEditNoteModal = (note: NoteBasicInfo) => {
  editingNote.value = note
  editNoteTitle.value = note.title
  showEditNoteModal.value = true
}

/**
 * 打开移动笔记弹窗
 */
const openMoveNoteModal = (note: NoteBasicInfo) => {
  movingNote.value = note
  selectedTargetGroupId.value = note.groupId
  showMoveNoteModal.value = true
}

/**
 * 移动笔记到指定分组
 */
const handleMoveNote = async () => {
  if (!movingNote.value) return

  if (selectedTargetGroupId.value === 0 || selectedTargetGroupId.value === -1) {
    showToast('请选择有效的文件夹', 'error')
    return
  }

  try {
    await modifyNote({
      uuid: movingNote.value.noteUuid,
      groupId: selectedTargetGroupId.value
    })
    showToast('移动成功', 'success')
    showMoveNoteModal.value = false
    movingNote.value = null
    await loadNotes()
  } catch (error: any) {
    showToast(error.message || '移动失败', 'error')
  }
}

/**
 * 修改笔记
 */
const handleEditNote = async () => {
  if (!editingNote.value || !editNoteTitle.value.trim()) {
    showToast('请输入笔记标题', 'error')
    return
  }

  try {
    await modifyNote({
      uuid: editingNote.value.noteUuid,
      title: editNoteTitle.value.trim()
    })
    showToast('修改成功', 'success')
    showEditNoteModal.value = false
    editingNote.value = null
    editNoteTitle.value = ''
    await loadNotes()
  } catch (error: any) {
    showToast(error.message || '修改失败', 'error')
  }
}

/**
 * 打开移动文件夹弹窗
 */
const openMoveFolderModal = (folder: NoteGroup) => {
  movingFolder.value = folder
  selectedFolderTargetGroupId.value = folder.parentGroupId
  showMoveFolderModal.value = true
}

/**
 * 移动文件夹到指定分组
 */
const handleMoveFolder = async () => {
  if (!movingFolder.value) return

  if (selectedFolderTargetGroupId.value === -1) {
    showToast('请选择有效的文件夹', 'error')
    return
  }

  try {
    await modifyNoteGroup({
      id: movingFolder.value.id,
      parentGroupId: selectedFolderTargetGroupId.value
    })
    showToast('移动成功', 'success')
    showMoveFolderModal.value = false
    movingFolder.value = null
    await loadNoteGroups()
    if (currentGroupId.value !== 0) {
      await loadNotes()
    }
  } catch (error: any) {
    showToast(error.message || '移动失败', 'error')
  }
}

/**
 * 打开修改文件夹弹窗
 */
const openEditFolderModal = (folder: NoteGroup) => {
  editingFolder.value = folder
  editFolderName.value = folder.groupName
  showEditFolderModal.value = true
}

/**
 * 修改文件夹
 */
const handleEditFolder = async () => {
  if (!editingFolder.value || !editFolderName.value.trim()) {
    showToast('请输入文件夹名称', 'error')
    return
  }

  try {
    await modifyNoteGroup({
      id: editingFolder.value.id,
      groupName: editFolderName.value.trim()
    })
    showToast('修改成功', 'success')
    showEditFolderModal.value = false
    editingFolder.value = null
    editFolderName.value = ''
    await loadNoteGroups()
    // 如果在子目录下，同时刷新笔记列表
    if (currentGroupId.value !== 0) {
      await loadNotes()
    }
  } catch (error: any) {
    showToast(error.message || '修改失败', 'error')
  }
}

/**
 * 打开删除笔记确认弹窗
 */
const openDeleteNoteModal = (note: NoteBasicInfo) => {
  deletingNote.value = note
  showDeleteNoteModal.value = true
}

/**
 * 删除笔记
 */
const handleDeleteNote = async () => {
  if (!deletingNote.value) return

  try {
    await deleteNote(deletingNote.value.noteUuid)
    showToast('删除成功', 'success')
    showDeleteNoteModal.value = false
    deletingNote.value = null
    await loadNotes()
  } catch (error: any) {
    showToast(error.message || '删除失败', 'error')
  }
}

/**
 * 打开删除文件夹确认弹窗
 */
const openDeleteFolderModal = (folder: NoteGroup) => {
  deletingFolder.value = folder
  showDeleteFolderModal.value = true
}

/**
 * 删除文件夹
 */
const handleDeleteFolder = async () => {
  if (!deletingFolder.value) return

  try {
    await deleteNoteGroup(deletingFolder.value.id)
    showToast('删除成功', 'success')
    showDeleteFolderModal.value = false
    deletingFolder.value = null
    await loadNoteGroups()
    // 如果在子目录下，同时刷新笔记列表
    if (currentGroupId.value !== 0) {
      await loadNotes()
    }
  } catch (error: any) {
    showToast(error.message || '删除失败', 'error')
  }
}

/**
 * 返回仪表盘
 */
const goBack = () => {
  router.push('/dashboard')
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

/**
 * 格式化日期
 */
const formatDate = (dateString: string) => {
  const date = new Date(dateString)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff < 0 ? 0 : (diff / (1000 * 60 * 60 * 24)))

  if (days === 0) {
    return '今天'
  } else if (days === 1) {
    return '昨天'
  } else if (days < 7) {
    return `${days}天前`
  } else {
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  }
}

// 面包屑滚动容器引用
const breadcrumbScrollRef = ref<HTMLElement | null>(null)

// 自动滚动到最新打开的文件夹
const scrollToLatestBreadcrumb = async () => {
  await nextTick()
  if (breadcrumbScrollRef.value) {
    const container = breadcrumbScrollRef.value
    container.scrollLeft = container.scrollWidth
  }
}

const handleWindowClick = () => {
  if (showActionMenu.value) {
    closeActionMenu()
  }
}

// 页面加载时获取数据
onMounted(async () => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  window.addEventListener('click', handleWindowClick)
  await loadNoteGroups()
  
  // 检查是否有groupId query参数需要导航
  const groupId = route.query.groupId
  if (groupId) {
    await navigateToGroupById(parseInt(groupId as string))
  }
  
  await loadNotes()
  // 导航后滚动到面包屑最右侧
  await scrollToLatestBreadcrumb()
})

// 清理事件监听
onBeforeUnmount(() => {
  window.removeEventListener('click', handleWindowClick)
})
</script>

<style scoped>
/* 面包屑滚动容器样式 */
.breadcrumb-scroll {
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE/Edge */
}

.breadcrumb-scroll::-webkit-scrollbar {
  display: none; /* Chrome/Safari */
}


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

/* 文本截断 */
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 移动端菜单滑入动画 */
.slide-up-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-up-leave-active {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-up-enter-from {
  transform: translateY(100%);
}

.slide-up-leave-to {
  transform: translateY(100%);
}
</style>

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
          <!-- 左侧：返回按钮和标题 -->
          <div class="flex items-center gap-4">
            <button
              @click="goBack"
              class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
              title="返回"
            >
              <svg class="w-5 h-5 text-gray-700 dark:text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
              </svg>
            </button>
            <div>
              <h1 class="text-xl font-bold text-gray-900 dark:text-white">笔记库</h1>
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
                    @click="authStore.logout(); router.push('/login')"
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

    <!-- 主内容区域 -->
    <div class="pt-20 pb-8 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto">
      <!-- 面包屑导航 -->
      <div
        ref="breadcrumbScrollRef"
        class="breadcrumb-scroll relative flex items-center gap-2 mb-6 text-sm"
      >
        <!-- 导航长度优化 -->
        <div class="absolute left-0 top-0 bottom-0 w-12  from-white dark:from-gray-900 via-white/80 dark:via-gray-900/80 to-transparent pointer-events-none z-10"></div>
        
        <button
          v-for="(item, index) in currentPath"
          :key="item.id"
          @click="navigateToBreadcrumb(index)"
          class="flex items-center gap-2 px-3 py-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors whitespace-nowrap flex-shrink-0"
          :class="index === currentPath.length - 1 ? 'text-gray-900 dark:text-white font-medium' : 'text-gray-600 dark:text-gray-400'"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
          </svg>
          {{ item.name }}
          <svg v-if="index < currentPath.length - 1" class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>

      <!-- 操作按钮 -->
      <div class="flex flex-wrap gap-3 mb-6">
        <button
          @click="showCreateFolderModal = true"
          class="px-4 py-2 bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-700 transition-all duration-300 font-medium flex items-center gap-2"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          新建文件夹
        </button>
        <button
          v-if="currentGroupId !== 0"
          @click="showCreateNoteModal = true"
          class="px-4 py-2 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all duration-300 font-medium flex items-center gap-2"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          新建笔记
        </button>
      </div>

      <!-- 加载状态 -->
      <div v-if="isLoading" class="flex items-center justify-center py-12">
        <div class="text-gray-500 dark:text-gray-400">加载中...</div>
      </div>

      <!-- 空状态 -->
      <div v-else-if="subFolders.length === 0 && notes.length === 0" class="flex flex-col items-center justify-center py-16">
        <svg class="w-24 h-24 text-gray-300 dark:text-gray-700 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
        </svg>
        <h3 class="text-lg font-medium text-gray-900 dark:text-white mb-2">文件夹为空</h3>
        <p class="text-gray-500 dark:text-gray-400 mb-6">创建文件夹或笔记开始记录</p>
      </div>

      <!-- 文件夹和笔记列表 -->
      <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
        <!-- 文件夹 -->
        <div
          v-for="folder in subFolders"
          :key="folder.id"
          class="group relative p-4 bg-gray-50 dark:bg-gray-800/50 rounded-xl hover:bg-gray-100 dark:hover:bg-gray-800 transition-all duration-300 hover:scale-[1.02] cursor-pointer border border-gray-200 dark:border-gray-700"
          @click="enterFolder(folder)"
          @dblclick="enterFolder(folder)"
          @touchstart="handleLongPressStart('folder', folder)"
          @touchend="handleLongPressEnd"
          @touchmove="handleLongPressMove"
        >
          <!-- 文件夹图标 -->
          <div class="flex flex-col items-center justify-center text-center">
            <svg class="w-16 h-16 text-gray-500 dark:text-gray-400 mb-3" fill="currentColor" viewBox="0 0 24 24">
              <path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/>
            </svg>
            <h3 class="text-sm font-medium text-gray-900 dark:text-white line-clamp-2 text-center">{{ folder.groupName }}</h3>
          </div>

          <!-- 操作按钮（悬停显示） -->
          <div class="absolute top-2 right-2 flex items-center opacity-0 group-hover:opacity-100 transition-opacity">
            <div class="relative">
              <button
                @click.stop="isActionMenuOpen('folder', folder) ? closeActionMenu() : openActionMenu('folder', folder)"
                class="p-1.5 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-lg transition-colors"
                title="更多操作"
              >
                <svg class="w-4 h-4 text-gray-600 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 12h.01M12 12h.01M19 12h.01M6 12a1 1 0 11-2 0 1 1 0 012 0zm7 0a1 1 0 11-2 0 1 1 0 012 0zm7 0a1 1 0 11-2 0 1 1 0 012 0z" />
                </svg>
              </button>
              <!-- 操作菜单 -->
              <Transition name="dropdown">
                <div
                  v-if="isActionMenuOpen('folder', folder)"
                  class="absolute right-0 top-full mt-1 w-32 bg-white dark:bg-gray-800 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 py-1 z-50"
                  @click.stop
                >
                  <button
                    @click="openEditFolderModal(folder); closeActionMenu()"
                    class="w-full px-3 py-2 text-left text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors flex items-center gap-2"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                    </svg>
                    编辑
                  </button>
                  <button
                    @click="openMoveFolderModal(folder); closeActionMenu()"
                    class="w-full px-3 py-2 text-left text-sm text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors flex items-center gap-2"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 14l-7 7m0 0l-7-7m7 7V3" />
                    </svg>
                    移动
                  </button>
                  <button
                    @click="openDeleteFolderModal(folder); closeActionMenu()"
                    class="w-full px-3 py-2 text-left text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors flex items-center gap-2"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                    删除
                  </button>
                </div>
              </Transition>
            </div>
          </div>
        </div>

        <!-- 笔记 -->
        <div
          v-for="note in notes"
          :key="note.noteUuid"
          class="group relative p-4 bg-gray-50 dark:bg-gray-800/50 rounded-xl hover:bg-gray-100 dark:hover:bg-gray-800 transition-all duration-300 hover:scale-[1.02] cursor-pointer border border-gray-200 dark:border-gray-700"
          @click="router.push(`/note/${note.noteUuid}`)"
          @touchstart="handleLongPressStart('note', note)"
          @touchend="handleLongPressEnd"
          @touchmove="handleLongPressMove"
        >
          <!-- 笔记图标 -->
          <div class="flex flex-col items-center text-center">
            <svg class="w-16 h-16 text-blue-500 dark:text-blue-400 mb-3" fill="currentColor" viewBox="0 0 24 24">
              <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/>
            </svg>
            <h3 class="text-sm font-medium text-gray-900 dark:text-white line-clamp-2 mb-1">{{ note.title }}</h3>
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ formatDate(note.updatedAt) }}</p>
          </div>

          <!-- 操作按钮（悬停显示） -->
          <div class="absolute top-2 right-2 flex items-center opacity-0 group-hover:opacity-100 transition-opacity">
            <div class="relative">
              <button
                @click.stop="isActionMenuOpen('note', note) ? closeActionMenu() : openActionMenu('note', note)"
                class="p-1.5 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-lg transition-colors"
                title="更多操作"
              >
                <svg class="w-4 h-4 text-gray-600 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 12h.01M12 12h.01M19 12h.01M6 12a1 1 0 11-2 0 1 1 0 012 0zm7 0a1 1 0 11-2 0 1 1 0 012 0zm7 0a1 1 0 11-2 0 1 1 0 012 0z" />
                </svg>
              </button>
              <!-- 操作菜单 -->
              <Transition name="dropdown">
                <div
                  v-if="isActionMenuOpen('note', note)"
                  class="absolute right-0 top-full mt-1 w-32 bg-white dark:bg-gray-800 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 py-1 z-50"
                  @click.stop
                >
                  <button
                    @click="openEditNoteModal(note); closeActionMenu()"
                    class="w-full px-3 py-2 text-left text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors flex items-center gap-2"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                    </svg>
                    编辑
                  </button>
                  <button
                    @click="openMoveNoteModal(note); closeActionMenu()"
                    class="w-full px-3 py-2 text-left text-sm text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors flex items-center gap-2"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 14l-7 7m0 0l-7-7m7 7V3" />
                    </svg>
                    移动
                  </button>
                  <button
                    @click="openDeleteNoteModal(note); closeActionMenu()"
                    class="w-full px-3 py-2 text-left text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors flex items-center gap-2"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                    删除
                  </button>
                </div>
              </Transition>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建笔记弹窗 -->
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
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">创建笔记</h3>
            <input
              v-model="newNoteTitle"
              type="text"
              class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all mb-4"
              placeholder="请输入笔记标题"
              @keyup.enter="handleCreateNote"
            />
            <div class="flex gap-3">
              <button
                @click="showCreateNoteModal = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleCreateNote"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
              >
                创建
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 创建文件夹弹窗 -->
    <Transition name="modal">
      <div
        v-if="showCreateFolderModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showCreateFolderModal = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showCreateFolderModal"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">创建文件夹</h3>
            <input
              v-model="newFolderName"
              type="text"
              class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all mb-4"
              placeholder="请输入文件夹名称"
              @keyup.enter="handleCreateFolder"
            />
            <div class="flex gap-3">
              <button
                @click="showCreateFolderModal = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleCreateFolder"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
              >
                创建
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 修改笔记弹窗 -->
    <Transition name="modal">
      <div
        v-if="showEditNoteModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showEditNoteModal = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showEditNoteModal"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">修改笔记</h3>
            <input
              v-model="editNoteTitle"
              type="text"
              class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all mb-4"
              placeholder="请输入笔记标题"
              @keyup.enter="handleEditNote"
            />
            <div class="flex gap-3">
              <button
                @click="showEditNoteModal = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleEditNote"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
              >
                保存
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 修改文件夹弹窗 -->
    <Transition name="modal">
      <div
        v-if="showEditFolderModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showEditFolderModal = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showEditFolderModal"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">修改文件夹</h3>
            <input
              v-model="editFolderName"
              type="text"
              class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all mb-4"
              placeholder="请输入文件夹名称"
              @keyup.enter="handleEditFolder"
            />
            <div class="flex gap-3">
              <button
                @click="showEditFolderModal = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleEditFolder"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
              >
                保存
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 删除笔记确认弹窗 -->
    <Transition name="modal">
      <div
        v-if="showDeleteNoteModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showDeleteNoteModal = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showDeleteNoteModal"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">删除笔记</h3>
            <p class="text-gray-600 dark:text-gray-400 mb-6">
              确定要删除笔记"{{ deletingNote?.title }}"吗？此操作不可恢复。
            </p>
            <div class="flex gap-3">
              <button
                @click="showDeleteNoteModal = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleDeleteNote"
                class="flex-1 py-2.5 px-4 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-all font-medium"
              >
                删除
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 删除文件夹确认弹窗 -->
    <Transition name="modal">
      <div
        v-if="showDeleteFolderModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showDeleteFolderModal = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showDeleteFolderModal"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">删除文件夹</h3>
            <p class="text-gray-600 dark:text-gray-400 mb-6">
              确定要删除文件夹"{{ deletingFolder?.groupName }}"吗？此操作不可恢复。
            </p>
            <div class="flex gap-3">
              <button
                @click="showDeleteFolderModal = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleDeleteFolder"
                class="flex-1 py-2.5 px-4 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-all font-medium"
              >
                删除
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 移动笔记弹窗 -->
    <Transition name="modal">
      <div
        v-if="showMoveNoteModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showMoveNoteModal = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showMoveNoteModal"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">移动笔记</h3>
            <!-- 文件夹选择器组件（不显示根目录） -->
            <FolderSelector
              :note-groups="noteGroups"
              v-model:selectedGroupId="selectedTargetGroupId"
              :disabled-group-id="movingNote?.groupId || -1"
              :include-root="false"
            />
            <br>
            <div class="flex gap-3">
              <button
                @click="showMoveNoteModal = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleMoveNote"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
              >
                移动
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 移动文件夹弹窗 -->
    <Transition name="modal">
      <div
        v-if="showMoveFolderModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showMoveFolderModal = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showMoveFolderModal"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">移动文件夹</h3>
            <!-- 移动到根目录按钮 -->
            <button
              @click="selectedFolderTargetGroupId = 0"
              class="w-full mb-3 px-4 py-2.5 rounded-lg border transition-all text-left flex items-center gap-2 text-sm"
              :class="selectedFolderTargetGroupId === 0
                ? 'border-gray-900 dark:border-white bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white font-medium'
                : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700/50'"
            >
              <svg class="w-4 h-4 flex-shrink-0" fill="currentColor" viewBox="0 0 24 24">
                <path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/>
              </svg>
              根目录
            </button>
            <!-- 文件夹选择器组件（不显示根目录） -->
            <FolderSelector
              :note-groups="noteGroups"
              v-model:selectedGroupId="selectedFolderTargetGroupId"
              :disabled-group-id="movingFolder?.id || -1"
              :include-root="false"
            />
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-2">无法移动到自己的子文件夹内</p>
            <div class="flex gap-3">
              <button
                @click="showMoveFolderModal = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleMoveFolder"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
              >
                移动
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 移动端操作菜单 -->
    <Transition name="modal">
      <div
        v-if="showMobileMenu"
        class="fixed inset-0 z-50 flex items-end justify-center bg-black/50 backdrop-blur-sm"
        @click="closeMobileMenu"
      >
        <Transition name="slide-up">
          <div
            v-if="showMobileMenu"
            class="w-full max-w-lg bg-white dark:bg-gray-800 rounded-t-2xl shadow-2xl p-4"
            @click.stop
          >
            <div class="w-12 h-1 bg-gray-300 dark:bg-gray-600 rounded-full mx-auto mb-4"></div>
            <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-4 text-center">
              {{ mobileMenuTarget?.type === 'folder' ? (mobileMenuTarget.item as NoteGroup).groupName : (mobileMenuTarget?.item as NoteBasicInfo).title }}
            </h3>
            <div class="grid grid-cols-3 gap-3" v-if="mobileMenuTarget?.type === 'note'">
              <button
                @click="handleMobileEdit"
                class="py-3 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-xl hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium flex items-center justify-center gap-2"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
                <span class="text-xs">编辑</span>
              </button>
              <button
                @click="handleMobileMove"
                class="py-3 px-4 bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded-xl hover:bg-blue-200 dark:hover:bg-blue-900/50 transition-all font-medium flex items-center justify-center gap-2"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 14l-7 7m0 0l-7-7m7 7V3" />
                </svg>
                <span class="text-xs">移动</span>
              </button>
              <button
                @click="handleMobileDelete"
                class="py-3 px-4 bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400 rounded-xl hover:bg-red-200 dark:hover:bg-red-900/50 transition-all font-medium flex items-center justify-center gap-2"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
                <span class="text-xs">删除</span>
              </button>
            </div>
            
            <div class="grid grid-cols-3 gap-3" v-else>
              <button
                @click="handleMobileEdit"
                class="py-3 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-xl hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium flex items-center justify-center gap-2"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
                <span class="text-xs">编辑</span>
              </button>
              <button
                @click="handleMobileMove"
                class="py-3 px-4 bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded-xl hover:bg-blue-200 dark:hover:bg-blue-900/50 transition-all font-medium flex items-center justify-center gap-2"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 14l-7 7m0 0l-7-7m7 7V3" />
                </svg>
                <span class="text-xs">移动</span>
              </button>
              <button
                @click="handleMobileDelete"
                class="py-3 px-4 bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400 rounded-xl hover:bg-red-200 dark:hover:bg-red-900/50 transition-all font-medium flex items-center justify-center gap-2"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
                <span class="text-xs">删除</span>
              </button>
            </div>
            <button
              @click="closeMobileMenu"
              class="w-full mt-3 py-3 px-4 bg-gray-200 dark:bg-gray-700 text-gray-900 dark:text-white rounded-xl hover:bg-gray-300 dark:hover:bg-gray-600 transition-all font-medium"
            >
              取消
            </button>
          </div>
        </Transition>
      </div>
    </Transition>
  </div>
</template>
