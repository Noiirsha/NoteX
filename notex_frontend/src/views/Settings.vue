<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import {
  getUserInfo,
  getUserPreferences,
  getUserAIModels,
  changeInfo,
  changePassword,
  changePreferences,
  changeAIModels
} from '@/api/user'
import type { UserInfo, UserPreferences, AIModelsConfig } from '@/types/api'
import Toast from '@/components/Toast.vue'
import { encode } from '@jsquash/webp'
import CryptoJS from 'crypto-js'
import request from '@/utils/request'
import { Cropper } from 'vue-advanced-cropper'
import 'vue-advanced-cropper/dist/style.css'
import { primeUserAIModelsCache } from '@/utils/aiModel'
import { buildStaticImageUrl } from '@/utils/assets'

const router = useRouter()
const themeStore = useThemeStore()
const authStore = useAuthStore()

// 用户信息
const userInfo = ref<UserInfo | null>(null)
const userPreferences = ref<UserPreferences | null>(null)
const userAIModels = ref<AIModelsConfig | null>(null)
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

// 修改个人信息表单
const showEditInfoModal = ref(false)
const editNickname = ref('')
const editAvatar = ref('')

// 头像上传相关
const avatarFileInput = ref<HTMLInputElement | null>(null)
const isUploadingAvatar = ref(false)
const avatarPreview = ref('')
const showAvatarCropper = ref(false)
const cropperImage = ref('')
const cropperRef = ref<any>(null)

// 修改密码表单
const showChangePasswordModal = ref(false)
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')

// 修改偏好设置表单
const showEditPreferencesModal = ref(false)
const editDarkMode = ref(false)

// 修改AI模型配置表单
const showEditAIModelsModal = ref(false)
const showEmbeddedModelChangedWarningDialog = ref(false)
const editStandardModelBase = ref('')
const editStandardModelApiKey = ref('')
const editStandardModelModelName = ref('')
const editEmbeddedModelBase = ref('')
const editEmbeddedModelApiKey = ref('')
const editEmbeddedModelModelName = ref('')
const confirmEmbeddedModelChanged = ref(false)
const keepExistingVectorIndex = ref(false)
const originalEmbeddedModelBase = ref('')
const originalEmbeddedModelApiKey = ref('')
const originalEmbeddedModelModelName = ref('')

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
 * 密码哈希化（SHA-256）
 */
const hashPassword = (password: string): string => {
  return CryptoJS.SHA256(password).toString()
}

/**
 * 获取用户信息
 */
const loadUserInfo = async () => {
  try {
    isLoading.value = true
    userInfo.value = await getUserInfo()
  } catch (error: any) {
    showToast(error.message || '获取用户信息失败', 'error')
  } finally {
    isLoading.value = false
  }
}

/**
 * 获取用户偏好设置
 */
const loadUserPreferences = async () => {
  try {
    userPreferences.value = await getUserPreferences()
  } catch (error: any) {
    showToast(error.message || '获取用户偏好失败', 'error')
  }
}

/**
 * 获取用户AI模型配置
 */
const loadUserAIModels = async () => {
  try {
    userAIModels.value = await getUserAIModels()
  } catch (error: any) {
    showToast(error.message || '获取AI模型配置失败', 'error')
  }
}

/**
 * 打开修改个人信息弹窗
 */
const openEditInfoModal = () => {
  editNickname.value = userInfo.value?.nickname || ''
  editAvatar.value = userInfo.value?.avatar || ''
  showEditInfoModal.value = true
}

/**
 * 选择头像文件
 */
const selectAvatarFile = () => {
  avatarFileInput.value?.click()
}

/**
 * 处理头像文件选择
 */
const handleAvatarFileSelect = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    showToast('请选择图片文件', 'error')
    return
  }

  // 验证文件大小（最大5MB）
  if (file.size > 5 * 1024 * 1024) {
    showToast('图片大小不能超过5MB', 'error')
    return
  }

  try {
    // 读取图片
    const reader = new FileReader()
    reader.onload = (e) => {
      cropperImage.value = e.target?.result as string
      avatarPreview.value = e.target?.result as string
      showAvatarCropper.value = true
    }
    reader.readAsDataURL(file)
  } catch (error: any) {
    showToast('读取图片失败', 'error')
  }

  // 清空input，允许重复选择同一文件
  target.value = ''
}

/**
 * 确认裁剪并上传
 */
const confirmCropAndUpload = async () => {
  if (!cropperRef.value) return

  try {
    isUploadingAvatar.value = true

    // 获取裁剪后的canvas
    const { canvas } = cropperRef.value.getResult()
    if (!canvas) throw new Error('无法获取裁剪结果')

    // 获取图片数据
    const imageData = canvas.getContext('2d')?.getImageData(0, 0, canvas.width, canvas.height)
    if (!imageData) throw new Error('无法获取图片数据')

    // 转换为webp格式
    const webpBuffer = await encode(imageData, { quality: 0.85 })

    // 上传到服务器
    const formData = new FormData()
    formData.append('file', new Blob([webpBuffer], { type: 'image/webp' }), `avatar_${Date.now()}.webp`)

    const result = await request.post('/static/save_image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    }) as { imageUuid?: string }
    
    if (result?.imageUuid) {
      // 设置头像URL
      editAvatar.value = buildStaticImageUrl(result.imageUuid)
      showAvatarCropper.value = false
      showToast('头像上传成功', 'success')
    } else {
      throw new Error('上传失败')
    }
  } catch (error: any) {
    showToast(error.message || '上传失败', 'error')
  } finally {
    isUploadingAvatar.value = false
  }
}

/**
 * 取消裁剪
 */
const cancelCrop = () => {
  showAvatarCropper.value = false
  cropperImage.value = ''
  avatarPreview.value = ''
}

/**
 * 修改个人信息
 */
const handleEditInfo = async () => {
  try {
    await changeInfo({
      nickname: editNickname.value.trim() || undefined,
      avatarImageUrl: editAvatar.value.trim() || undefined
    })
    
    // 更新pinia中的用户信息
    authStore.updateUserInfo({
      nickname: editNickname.value.trim() || undefined,
      avatar: editAvatar.value.trim() || undefined
    })
    
    showToast('修改成功', 'success')
    showEditInfoModal.value = false
    await loadUserInfo()
  } catch (error: any) {
    showToast(error.message || '修改失败', 'error')
  }
}

/**
 * 打开修改密码弹窗
 */
const openChangePasswordModal = () => {
  oldPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  showChangePasswordModal.value = true
}

/**
 * 修改密码
 */
const handleChangePassword = async () => {
  if (!oldPassword.value || !newPassword.value || !confirmPassword.value) {
    showToast('请填写所有字段', 'error')
    return
  }

  if (newPassword.value !== confirmPassword.value) {
    showToast('两次输入的密码不一致', 'error')
    return
  }

  if (newPassword.value.length < 6) {
    showToast('密码长度至少6位', 'error')
    return
  }

  try {
    await changePassword({
      oldPasswordHash: hashPassword(oldPassword.value),
      newPasswordHash: hashPassword(newPassword.value)
    })
    showToast('密码修改成功', 'success')
    showChangePasswordModal.value = false
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (error: any) {
    showToast(error.message || '修改失败', 'error')
  }
}

/**
 * 打开修改偏好设置弹窗
 */
const openEditPreferencesModal = () => {
  editDarkMode.value = userPreferences.value?.darkMode || false
  showEditPreferencesModal.value = true
}

/**
 * 修改偏好设置
 */
const handleEditPreferences = async () => {
  try {
    await changePreferences({
      darkMode: editDarkMode.value
    })
    showToast('修改成功', 'success')
    showEditPreferencesModal.value = false
    await loadUserPreferences()
    // 同步本地主题
    if (themeStore.isDark !== editDarkMode.value) {
      themeStore.toggleTheme()
    }
  } catch (error: any) {
    showToast(error.message || '修改失败', 'error')
  }
}

/**
 * 打开修改AI模型配置弹窗
 */
const openEditAIModelsModal = () => {
  editStandardModelBase.value = userAIModels.value?.standardModelBase || ''
  editStandardModelApiKey.value = userAIModels.value?.standardModelApiKey || ''
  editStandardModelModelName.value = userAIModels.value?.standardModelModelName || ''
  editEmbeddedModelBase.value = userAIModels.value?.embeddedModelBase || ''
  editEmbeddedModelApiKey.value = userAIModels.value?.embeddedModelApiKey || ''
  editEmbeddedModelModelName.value = userAIModels.value?.embeddedModelModelName || ''
  originalEmbeddedModelBase.value = userAIModels.value?.embeddedModelBase || ''
  originalEmbeddedModelApiKey.value = userAIModels.value?.embeddedModelApiKey || ''
  originalEmbeddedModelModelName.value = userAIModels.value?.embeddedModelModelName || ''
  confirmEmbeddedModelChanged.value = false
  keepExistingVectorIndex.value = false
  showEmbeddedModelChangedWarningDialog.value = false
  showEditAIModelsModal.value = true
}

const closeEditAIModelsModal = () => {
  showEditAIModelsModal.value = false
  showEmbeddedModelChangedWarningDialog.value = false
  confirmEmbeddedModelChanged.value = false
  keepExistingVectorIndex.value = false
}

const hasEmbeddedModelChanged = () => {
  return editEmbeddedModelBase.value.trim() !== originalEmbeddedModelBase.value.trim()
    || editEmbeddedModelApiKey.value.trim() !== originalEmbeddedModelApiKey.value.trim()
    || editEmbeddedModelModelName.value.trim() !== originalEmbeddedModelModelName.value.trim()
}

const validateModelGroup = (groupLabel: string, values: string[]) => {
  const trimmedValues = values.map(value => value.trim())
  const filledCount = trimmedValues.filter(Boolean).length

  if (filledCount > 0 && filledCount < trimmedValues.length) {
    showToast(`${groupLabel}需要同时填写URL、密钥和模型名称`, 'error')
    return false
  }

  return true
}

const submitEditAIModels = async () => {
  const changedEmbeddedModel = confirmEmbeddedModelChanged.value || (hasEmbeddedModelChanged() && !keepExistingVectorIndex.value)

  if (!validateModelGroup('Chat模型', [
    editStandardModelBase.value,
    editStandardModelApiKey.value,
    editStandardModelModelName.value
  ])) {
    return
  }

  if (!validateModelGroup('嵌入模型', [
    editEmbeddedModelBase.value,
    editEmbeddedModelApiKey.value,
    editEmbeddedModelModelName.value
  ])) {
    return
  }

  const nextAIModelsConfig = {
    standardModelBase: editStandardModelBase.value.trim() || undefined,
    standardModelApiKey: editStandardModelApiKey.value.trim() || undefined,
    standardModelModelName: editStandardModelModelName.value.trim() || undefined,
    embeddedModelBase: editEmbeddedModelBase.value.trim() || undefined,
    embeddedModelApiKey: editEmbeddedModelApiKey.value.trim() || undefined,
    embeddedModelModelName: editEmbeddedModelModelName.value.trim() || undefined,
    changedEmbeddedModel
  }

  try {
    await changeAIModels(nextAIModelsConfig)
    primeUserAIModelsCache(nextAIModelsConfig)
    showToast('修改成功', 'success')
    closeEditAIModelsModal()
    await loadUserAIModels()
  } catch (error: any) {
    showToast(error.message || '修改失败', 'error')
  }
}

/**
 * 修改AI模型配置
 */
const handleEditAIModels = async () => {
  if (hasEmbeddedModelChanged() && !confirmEmbeddedModelChanged.value) {
    showEmbeddedModelChangedWarningDialog.value = true
    return
  }

  await submitEditAIModels()
}

const confirmEmbeddedModelChangedAndSave = async () => {
  confirmEmbeddedModelChanged.value = true
  keepExistingVectorIndex.value = false
  showEmbeddedModelChangedWarningDialog.value = false
  await submitEditAIModels()
}

const keepVectorIndexAndSave = async () => {
  keepExistingVectorIndex.value = true
  confirmEmbeddedModelChanged.value = false
  showEmbeddedModelChangedWarningDialog.value = false
  await submitEditAIModels()
}

/**
 * 返回仪表盘
 */
const goBack = () => {
  router.push('/dashboard')
}

// 页面加载时获取数据
onMounted(() => {
  loadUserInfo()
  loadUserPreferences()
  loadUserAIModels()
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
            <h1 class="text-xl font-bold text-gray-900 dark:text-white">设置</h1>
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
          </div>
        </div>
      </div>
    </div>

    <!-- 主内容区域 -->
    <div class="pt-20 pb-8 px-4 sm:px-6 lg:px-8 max-w-4xl mx-auto">
      <!-- 加载状态 -->
      <div v-if="isLoading" class="flex items-center justify-center py-12">
        <div class="text-gray-500 dark:text-gray-400">加载中...</div>
      </div>

      <!-- 设置列表 -->
      <div v-else class="space-y-6">
        <!-- 个人信息 -->
        <div class="bg-gray-50 dark:bg-gray-800/50 rounded-xl p-6">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white">个人信息</h2>
            <button
              @click="openEditInfoModal"
              class="text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white transition-colors"
            >
              编辑
            </button>
          </div>
          <div class="space-y-3">
            <div class="flex items-center gap-3">
              <div class="w-12 h-12 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center overflow-hidden">
                <img v-if="authStore?.avatar" :src="authStore.avatar" class="w-full h-full object-cover" />
                <svg v-else class="w-6 h-6 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                </svg>
              </div>
              <div>
                <div class="font-medium text-gray-900 dark:text-white">{{ userInfo?.nickname || '未设置' }}</div>
                <div class="text-sm text-gray-500 dark:text-gray-400">@{{ userInfo?.username }}</div>
              </div>
            </div>
            <div class="text-sm text-gray-600 dark:text-gray-400">
              <span class="font-medium">邮箱：</span>{{ userInfo?.email || '未设置' }}
            </div>
          </div>
        </div>

        <!-- 偏好设置 -->
        <div class="bg-gray-50 dark:bg-gray-800/50 rounded-xl p-6">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white">偏好设置</h2>
            <button
              @click="openEditPreferencesModal"
              class="text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white transition-colors"
            >
              编辑
            </button>
          </div>
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-gray-700 dark:text-gray-300">黑暗模式</span>
              <span class="text-sm text-gray-500 dark:text-gray-400">{{ userPreferences?.darkMode ? '已开启' : '已关闭' }}</span>
            </div>
          </div>
        </div>

        <!-- 安全设置 -->
        <div class="bg-gray-50 dark:bg-gray-800/50 rounded-xl p-6">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white">安全设置</h2>
          </div>
          <button
            @click="openChangePasswordModal"
            class="w-full py-3 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
          >
            修改密码
          </button>
        </div>

        <!-- AI模型配置 -->
        <div class="bg-gray-50 dark:bg-gray-800/50 rounded-xl p-6">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white">AI模型配置</h2>
            <button
              @click="openEditAIModelsModal"
              class="text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white transition-colors"
            >
              编辑
            </button>
          </div>
          <div class="space-y-3 text-sm">
            <div>
              <div class="text-gray-600 dark:text-gray-400 mb-1">标准模型</div>
              <div class="text-gray-900 dark:text-white">{{ userAIModels?.standardModelModelName || '未设置' }}</div>
            </div>
            <div>
              <div class="text-gray-600 dark:text-gray-400 mb-1">嵌入模型</div>
              <div class="text-gray-900 dark:text-white">{{ userAIModels?.embeddedModelModelName || '未设置' }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 修改个人信息弹窗 -->
    <Transition name="modal">
      <div
        v-if="showEditInfoModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showEditInfoModal = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showEditInfoModal"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">修改个人信息</h3>
            <div class="space-y-4 mb-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">昵称</label>
                <input
                  v-model="editNickname"
                  type="text"
                  class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all"
                  placeholder="请输入昵称"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">头像</label>
                <div class="flex items-center gap-4">
                  <!-- 头像预览 -->
                  <div class="w-16 h-16 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center overflow-hidden flex-shrink-0">
                    <img v-if="editAvatar" :src="editAvatar" alt="头像预览" class="w-full h-full object-cover" />
                    <svg v-else class="w-8 h-8 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                    </svg>
                  </div>
                  <!-- 上传按钮 -->
                  <div class="flex-1">
                    <button
                      @click="selectAvatarFile"
                      class="w-full py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium text-sm"
                    >
                      选择图片
                    </button>
                    <input
                      ref="avatarFileInput"
                      type="file"
                      accept="image/*"
                      class="hidden"
                      @change="handleAvatarFileSelect"
                    />
                  </div>
                </div>
              </div>
            </div>
            <div class="flex gap-3">
              <button
                @click="showEditInfoModal = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleEditInfo"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
              >
                保存
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 头像裁剪弹窗 -->
    <Transition name="modal">
      <div
        v-if="showAvatarCropper"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="cancelCrop"
      >
        <Transition name="modal-content">
          <div
            v-if="showAvatarCropper"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">裁剪头像</h3>
            
            <!-- 裁剪区域 -->
            <div class="mb-4">
              <div class="w-64 h-64 mx-auto">
                <Cropper
                  ref="cropperRef"
                  :src="cropperImage"
                  :stencil-props="{
                    aspectRatio: 1,
                    movable: true,
                    resizable: true
                  }"
                  :canvas="{
                    width: 256,
                    height: 256
                  }"
                  class="rounded-lg overflow-hidden"
                />
              </div>
              <p class="text-xs text-gray-500 dark:text-gray-400 text-center mt-2">拖动调整裁剪区域</p>
            </div>

            <!-- 确认/取消按钮 -->
            <div class="flex gap-3">
              <button
                @click="cancelCrop"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
                :disabled="isUploadingAvatar"
              >
                取消
              </button>
              <button
                @click="confirmCropAndUpload"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
                :disabled="isUploadingAvatar"
              >
                {{ isUploadingAvatar ? '上传中...' : '确认' }}
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 修改密码弹窗 -->
    <Transition name="modal">
      <div
        v-if="showChangePasswordModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showChangePasswordModal = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showChangePasswordModal"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">修改密码</h3>
            <div class="space-y-4 mb-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">旧密码</label>
                <input
                  v-model="oldPassword"
                  type="password"
                  class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all"
                  placeholder="请输入旧密码"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">新密码</label>
                <input
                  v-model="newPassword"
                  type="password"
                  class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all"
                  placeholder="请输入新密码（至少6位）"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">确认新密码</label>
                <input
                  v-model="confirmPassword"
                  type="password"
                  class="w-full px-4 py-3 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all"
                  placeholder="请再次输入新密码"
                  @keyup.enter="handleChangePassword"
                />
              </div>
            </div>
            <div class="flex gap-3">
              <button
                @click="showChangePasswordModal = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleChangePassword"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
              >
                保存
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 修改偏好设置弹窗 -->
    <Transition name="modal">
      <div
        v-if="showEditPreferencesModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
        @click.self="showEditPreferencesModal = false"
      >
        <Transition name="modal-content">
          <div
            v-if="showEditPreferencesModal"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">偏好设置</h3>
            <div class="space-y-4 mb-4">
              <div class="flex items-center justify-between">
                <span class="text-gray-700 dark:text-gray-300">黑暗模式</span>
                <button
                  @click="editDarkMode = !editDarkMode"
                  class="relative inline-flex h-6 w-11 items-center rounded-full transition-colors"
                  :class="editDarkMode ? 'bg-gray-900 dark:bg-white' : 'bg-gray-300 dark:bg-gray-600'"
                >
                  <span
                    class="inline-block h-4 w-4 transform rounded-full bg-white dark:bg-gray-900 transition-transform"
                    :class="editDarkMode ? 'translate-x-6' : 'translate-x-1'"
                  />
                </button>
              </div>
            </div>
            <div class="flex gap-3">
              <button
                @click="showEditPreferencesModal = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleEditPreferences"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
              >
                保存
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 修改AI模型配置弹窗 -->
    <Transition name="modal">
      <div
        v-if="showEditAIModelsModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm overflow-y-auto"
        @click.self="closeEditAIModelsModal()"
      >
        <Transition name="modal-content">
          <div
            v-if="showEditAIModelsModal"
            class="w-full max-w-lg bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6 my-8"
            @click.stop
          >
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">AI模型配置</h3>
            <div class="space-y-4 mb-4 max-h-[60vh] overflow-y-auto">
              <div class="border-b border-gray-200 dark:border-gray-700 pb-4">
                <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">标准模型</h4>
                <div class="space-y-3">
                  <div>
                    <label class="block text-xs text-gray-600 dark:text-gray-400 mb-1">API基础URL</label>
                    <input
                      v-model="editStandardModelBase"
                      type="text"
                      class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all text-sm"
                      placeholder="https://api.openai.com/v1"
                    />
                  </div>
                  <div>
                    <label class="block text-xs text-gray-600 dark:text-gray-400 mb-1">API密钥</label>
                    <input
                      v-model="editStandardModelApiKey"
                      type="password"
                      class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all text-sm"
                      placeholder="sk-xxxxxxxxxxxxxxxx"
                    />
                  </div>
                  <div>
                    <label class="block text-xs text-gray-600 dark:text-gray-400 mb-1">模型名称</label>
                    <input
                      v-model="editStandardModelModelName"
                      type="text"
                      class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all text-sm"
                      placeholder="gpt-4"
                    />
                  </div>
                </div>
              </div>
              <div>
                <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">嵌入模型</h4>
                <div class="space-y-3">
                  <div>
                    <label class="block text-xs text-gray-600 dark:text-gray-400 mb-1">API基础URL</label>
                    <input
                      v-model="editEmbeddedModelBase"
                      type="text"
                      class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all text-sm"
                      placeholder="https://api.openai.com/v1"
                    />
                  </div>
                  <div>
                    <label class="block text-xs text-gray-600 dark:text-gray-400 mb-1">API密钥</label>
                    <input
                      v-model="editEmbeddedModelApiKey"
                      type="password"
                      class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all text-sm"
                      placeholder="sk-xxxxxxxxxxxxxxxx"
                    />
                  </div>
                  <div>
                    <label class="block text-xs text-gray-600 dark:text-gray-400 mb-1">模型名称</label>
                    <input
                      v-model="editEmbeddedModelModelName"
                      type="text"
                      class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent outline-none transition-all text-sm"
                      placeholder="text-embedding-ada-002"
                    />
                  </div>
                  <label class="flex items-start gap-3 rounded-lg border border-yellow-200 dark:border-yellow-800/80 bg-yellow-50/80 dark:bg-yellow-900/10 px-3 py-3 cursor-pointer select-none">
                    <input
                      v-model="confirmEmbeddedModelChanged"
                      type="checkbox"
                      class="mt-0.5 h-4 w-4 rounded border-gray-300 text-gray-900 focus:ring-2 focus:ring-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-white dark:focus:ring-white"
                    />
                    <div>
                      <div class="text-sm font-medium text-yellow-800 dark:text-yellow-200">我已更换嵌入模型</div>
                      <div class="mt-1 text-xs leading-5 text-yellow-700 dark:text-yellow-300">若已更换嵌入模型，请勾选此项，未重置向量索引会导致搜索能力失效。</div>
                    </div>
                  </label>
                </div>
              </div>
            </div>
            <div class="flex gap-3">
              <button
                @click="closeEditAIModelsModal"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="handleEditAIModels"
                class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
              >
                保存
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <Transition name="modal">
      <div
        v-if="showEmbeddedModelChangedWarningDialog"
        class="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
      >
        <Transition name="modal-content">
          <div
            v-if="showEmbeddedModelChangedWarningDialog"
            class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6"
            @click.stop
          >
            <div class="flex items-start gap-3">
              <div class="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300">
                <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v4m0 4h.01M10.29 3.86l-7.4 12.82A2 2 0 004.63 19h14.74a2 2 0 001.74-2.32L13.71 3.86a2 2 0 00-3.42 0z" />
                </svg>
              </div>
              <div>
                <h3 class="text-lg font-semibold text-gray-900 dark:text-white">确认是否已更换 Embedded 模型</h3>
                <p class="mt-2 text-sm leading-6 text-gray-600 dark:text-gray-300">检测到你修改了嵌入模型配置，但尚未勾选“我已更换向量模型”。如果更换 Embedded 模型后不重置向量索引，将导致 NoteX 的搜索功能失效。</p>
              </div>
            </div>
            <div class="mt-6 flex gap-3">
              <button
                @click="showEmbeddedModelChangedWarningDialog = false"
                class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
              >
                取消
              </button>
              <button
                @click="keepVectorIndexAndSave"
                class="flex-1 py-2.5 px-4 bg-gray-200 dark:bg-gray-600 text-gray-900 dark:text-white rounded-lg hover:bg-gray-300 dark:hover:bg-gray-500 transition-all font-medium"
              >
                不清除索引
              </button>
              <button
                @click="confirmEmbeddedModelChangedAndSave"
                class="flex-1 py-2.5 px-4 bg-yellow-500 text-white rounded-lg hover:bg-yellow-600 transition-all font-medium"
              >
                重置索引并保存
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
</style>
