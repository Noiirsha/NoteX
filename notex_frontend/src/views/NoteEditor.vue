<script setup lang="ts">
import { ref, onMounted, computed, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import { fetchNoteDetail, saveNoteContent } from '@/api/note'
import { beginChatStream, generateChatTitle } from '@/api/chat'
import type { ChatWorkspaceDetail, NoteDetail } from '@/types/api'
import { config, MdEditor, MdPreview } from 'md-editor-v3'
import type { ToolbarNames } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import 'md-editor-v3/lib/preview.css'
import { encode } from '@jsquash/webp'
import Toast from '@/components/Toast.vue'
import request from '@/utils/request'
import { isApiServiceError } from '@/utils/apiServiceError'
import { fetchCachedUserAIModels, isStandardModelConfigured } from '@/utils/aiModel'
import { buildStaticImageUrl } from '@/utils/assets'
import { createUuid } from '@/utils/uuid'

interface ChatMessageView {
  id: string
  role: 'user' | 'assistant'
  content: string
  streamBuffer?: string
  renderVersion?: number
  isStreaming?: boolean
  toolCalls?: Array<{
    functionName: string
    argumentsText: string
    completed: boolean
  }>
  hasError?: boolean
  errorReason?: string
  showErrorDetail?: boolean
}

config({
  markdownItConfig(md) {
    md.set({
      breaks: true,
      linkify: true
    })
  },
  editorConfig: {
    renderDelay: 0
  }
})

const router = useRouter()
const route = useRoute()
const themeStore = useThemeStore()
const authStore = useAuthStore()

// 笔记UUID
const noteUuid = computed(() => route.params.uuid as string)

// 笔记详情
const noteDetail = ref<NoteDetail | null>(null)
const content = ref('')
const isLoading = ref(true)
const isSaving = ref(false)
const hasUnsavedChanges = ref(false)

// 全屏状态
const isFullscreen = ref(false)

// 移动端检测
const isMobile = ref(false)
const isMobileEditing = ref(false)
const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) {
    isMobileEditing.value = false
  }
}

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
const showEmbeddingModelDialog = ref(false)
const showApiServiceErrorDialog = ref(false)
const apiServiceErrorDetail = ref('')

// md-editor-v3 主题
const editorTheme = computed(() => themeStore.isDark ? 'dark' : 'light')
const desktopToolbars: ToolbarNames[] = ['bold', 'underline', 'italic', 'strikeThrough', '-', 'title', 'sub', 'sup', 'quote', 'unorderedList', 'orderedList', 'task', '-', 'codeRow', 'code', 'link', 'image', 'table', 'mermaid', 'katex', '-', 'revoke', 'next', 'prettier', '=', 'fullscreen', 'preview', 'previewOnly', 'htmlPreview', 'catalog']
const mobileEditToolbars: ToolbarNames[] = ['bold', 'underline', 'italic', 'strikeThrough', 'title']

// 预览模式ID
const previewId = 'preview-only'
const messagesContainerRef = ref<HTMLElement | null>(null)
const showNoteChatPanel = ref(false)
const currentConversationId = ref('')
const pendingMessage = ref('')
const lastUserMessage = ref('')
const isSending = ref(false)
const activeStreamAbortController = ref<AbortController | null>(null)
const messages = ref<ChatMessageView[]>([])
const hasInitializedConversation = ref(false)

const isDesktopChatVisible = computed(() => showNoteChatPanel.value && !isMobile.value && !isFullscreen.value)
const isChatDialogVisible = computed(() => showNoteChatPanel.value && isMobile.value)
const displayNoteTitle = computed(() => {
  const title = noteDetail.value?.title || '加载中...'
  if (!isMobile.value || title.length <= 7) {
    return title
  }
  return `${title.slice(0, 7)}...`
})
const isAssistantResponding = computed(() => {
  const lastMessage = messages.value[messages.value.length - 1]
  return isSending.value || Boolean(lastMessage?.role === 'assistant' && lastMessage.isStreaming)
})
const lastConversationUserMessage = computed(() => {
  for (let index = messages.value.length - 1; index >= 0; index -= 1) {
    const message = messages.value[index]
    if (message?.role === 'user') {
      return message.content.trim()
    }
  }
  return ''
})
const lastMessageNeedsRetry = computed(() => {
  if (messages.value.length === 0 || isSending.value) {
    return false
  }

  const lastMessage = messages.value[messages.value.length - 1]
  return lastMessage?.role === 'user'
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

const closeEmbeddingModelDialog = () => {
  showEmbeddingModelDialog.value = false
}

const closeApiServiceErrorDialog = () => {
  showApiServiceErrorDialog.value = false
  apiServiceErrorDetail.value = ''
}

const showApiServiceError = (detail: string) => {
  apiServiceErrorDetail.value = detail
  showApiServiceErrorDialog.value = true
}

const goToSettingsForEmbeddingModel = () => {
  showEmbeddingModelDialog.value = false
  router.push('/settings')
}

const ensureStandardModelConfigured = async () => {
  try {
    const config = await fetchCachedUserAIModels()

    if (isStandardModelConfigured(config)) {
      return true
    }

    showEmbeddingModelDialog.value = true
    return false
  } catch (error) {
    console.error('获取AI模型配置失败:', error)
    showToast('获取AI模型配置失败', 'error')
    return false
  }
}

const toggleMobileEditMode = () => {
  if (!isMobile.value) return
  isMobileEditing.value = !isMobileEditing.value
}

const createConversationId = () => {
  return createUuid()
}

const ensureConversationId = () => {
  if (!currentConversationId.value) {
    currentConversationId.value = createConversationId()
  }
}

const scrollMessagesToBottom = () => {
  void nextTick(() => {
    const container = messagesContainerRef.value
    if (!container) return
    container.scrollTop = container.scrollHeight
  })
}

const bumpMessageRenderVersion = (message: ChatMessageView) => {
  message.renderVersion = (message.renderVersion || 0) + 1
}

const appendMessageContent = (message: ChatMessageView, chunk: string) => {
  if (!chunk) return
  message.content += chunk
  bumpMessageRenderVersion(message)
}

const parseToolCallingTag = (value: string) => {
  const closeIndex = value.indexOf('/>')
  if (closeIndex === -1) return null

  const raw = value.slice(0, closeIndex + 2)
  const match = raw.match(/<notex_tool_calling\s+function="([\s\S]*?)"\s+arguments="([\s\S]*?)"\s*\/>/)
  if (!match) return null

  return {
    raw,
    functionName: match[1],
    argumentsText: match[2]
  }
}

const parseResponseErrorTag = (value: string) => {
  const match = value.match(/<notex_response_error(?:\s+reason="([\s\S]*?)")?\s*\/>/)
  if (!match || match.index === undefined) return null
  return {
    raw: match[0],
    index: match.index,
    reason: match[1] || ''
  }
}

const flushAssistantStreamBuffer = (message: ChatMessageView) => {
  if (!message.streamBuffer) return

  while (message.streamBuffer) {
    const toolTagStart = message.streamBuffer.indexOf('<notex_tool_calling')
    const errorTagInfo = parseResponseErrorTag(message.streamBuffer)
    const errorTagStart = errorTagInfo?.index ?? -1
    const nextTagStartCandidates = [toolTagStart, errorTagStart].filter(index => index >= 0)
    const nextTagStart = nextTagStartCandidates.length > 0 ? Math.min(...nextTagStartCandidates) : -1

    if (nextTagStart === -1) {
      appendMessageContent(message, message.streamBuffer)
      message.streamBuffer = ''
      scrollMessagesToBottom()
      return
    }

    if (nextTagStart > 0) {
      appendMessageContent(message, message.streamBuffer.slice(0, nextTagStart))
      message.streamBuffer = message.streamBuffer.slice(nextTagStart)
    }

    const responseErrorTag = parseResponseErrorTag(message.streamBuffer)
    if (responseErrorTag && responseErrorTag.index === 0) {
      message.hasError = true
      message.errorReason = responseErrorTag.reason
      message.showErrorDetail = true
      bumpMessageRenderVersion(message)
      message.streamBuffer = message.streamBuffer.slice(responseErrorTag.raw.length)
      scrollMessagesToBottom()
      continue
    }

    if (message.streamBuffer.startsWith('<notex_tool_calling')) {
      if (!message.streamBuffer.includes('/>')) {
        return
      }

      const toolInfo = parseToolCallingTag(message.streamBuffer)
      if (!toolInfo) {
        const invalidTagEnd = message.streamBuffer.indexOf('/>')
        message.content += message.streamBuffer.slice(0, invalidTagEnd + 2)
        message.streamBuffer = message.streamBuffer.slice(invalidTagEnd + 2)
        continue
      }

      if (!message.toolCalls) {
        message.toolCalls = []
      }

      message.toolCalls.push({
        functionName: toolInfo.functionName,
        argumentsText: toolInfo.argumentsText,
        completed: false
      })
      bumpMessageRenderVersion(message)
      message.streamBuffer = message.streamBuffer.slice(toolInfo.raw.length)
      scrollMessagesToBottom()
      continue
    }
  }
}

const appendAssistantChunk = (chunk: string) => {
  const lastMessage = messages.value[messages.value.length - 1]

  if (!lastMessage || lastMessage.role !== 'assistant' || !lastMessage.isStreaming) {
    messages.value.push({
      id: `assistant-${Date.now()}`,
      role: 'assistant',
      content: '',
      streamBuffer: '',
      renderVersion: 0,
      isStreaming: true,
      toolCalls: [],
      hasError: false,
      errorReason: '',
      showErrorDetail: true
    })
    scrollMessagesToBottom()
  }

  const activeMessage = messages.value[messages.value.length - 1]
  if (!activeMessage) return

  activeMessage.streamBuffer = `${activeMessage.streamBuffer || ''}${chunk}`
  flushAssistantStreamBuffer(activeMessage)

  const latestToolCall = activeMessage.toolCalls?.[activeMessage.toolCalls.length - 1]
  if (latestToolCall && activeMessage.content.trim()) {
    latestToolCall.completed = true
    bumpMessageRenderVersion(activeMessage)
  }

  scrollMessagesToBottom()
}

const finalizeAssistantMessage = () => {
  const lastMessage = messages.value[messages.value.length - 1]
  if (lastMessage && lastMessage.role === 'assistant') {
    flushAssistantStreamBuffer(lastMessage)
    if (lastMessage.streamBuffer) {
      appendMessageContent(lastMessage, lastMessage.streamBuffer)
      lastMessage.streamBuffer = ''
    }
    lastMessage.isStreaming = false
    const latestToolCall = lastMessage.toolCalls?.[lastMessage.toolCalls.length - 1]
    if (latestToolCall) {
      latestToolCall.completed = true
      bumpMessageRenderVersion(lastMessage)
    }
    scrollMessagesToBottom()
  }
}

const parseSseEventData = (event: string) => {
  const dataLines: string[] = []

  for (const line of event.split(/\r?\n/)) {
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5))
      continue
    }

    if (line.startsWith(':')) {
      continue
    }
  }

  if (dataLines.length === 0) return null
  return dataLines.join('\n')
}

const appendSseEvent = (event: string) => {
  const chunk = parseSseEventData(event)
  if (chunk === null || chunk === '[DONE]') return
  appendAssistantChunk(chunk)
}

const readSseStream = async (response: Response, signal?: AbortSignal) => {
  if (!response.body) {
    throw new Error('未获取到流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const abortHandler = async () => {
    try {
      await reader.cancel('aborted')
    } catch {
      // ignore
    }
  }

  if (signal) {
    if (signal.aborted) {
      await abortHandler()
      throw new DOMException('The operation was aborted.', 'AbortError')
    }
    signal.addEventListener('abort', abortHandler, { once: true })
  }

  try {
    while (true) {
      if (signal?.aborted) {
        throw new DOMException('The operation was aborted.', 'AbortError')
      }

      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''

      for (const event of events) {
        appendSseEvent(event)
      }
    }

    if (buffer) {
      appendSseEvent(buffer)
    }
  } finally {
    if (signal) {
      signal.removeEventListener('abort', abortHandler)
    }
  }
}

const stopAssistantReply = () => {
  activeStreamAbortController.value?.abort()
}

const startNewConversation = () => {
  stopAssistantReply()
  currentConversationId.value = createConversationId()
  pendingMessage.value = ''
  lastUserMessage.value = ''
  messages.value = []
  hasInitializedConversation.value = false
}

const openNoteChat = () => {
  ensureConversationId()
  showNoteChatPanel.value = true
  scrollMessagesToBottom()
}

const closeNoteChat = () => {
  showNoteChatPanel.value = false
}

const sendMessageByText = async (messageText: string) => {
  const normalizedMessageText = messageText.trim()
  if (!normalizedMessageText || !noteDetail.value) return

  if (!(await ensureStandardModelConfigured())) {
    return
  }

  try {
    isSending.value = true
    ensureConversationId()

    messages.value.push({
      id: `user-${Date.now()}`,
      role: 'user',
      content: normalizedMessageText,
      toolCalls: [],
      hasError: false,
      errorReason: '',
      showErrorDetail: true
    })
    scrollMessagesToBottom()

    lastUserMessage.value = normalizedMessageText
    pendingMessage.value = ''

    const workspaceDetail: ChatWorkspaceDetail = {
      noteUuid: noteUuid.value,
      groupId: noteDetail.value.groupId,
      noteContent: content.value
    }

    if (!hasInitializedConversation.value) {
      await generateChatTitle({
        conversationId: currentConversationId.value,
        firstContent: normalizedMessageText
      })
      hasInitializedConversation.value = true
    }

    const response = await beginChatStream({
      userNickname: authStore.nickname || authStore.username,
      conversationId: currentConversationId.value,
      message: normalizedMessageText,
      workspaceDetail
    })
    const abortController = new AbortController()
    activeStreamAbortController.value = abortController

    await readSseStream(response, abortController.signal)
    finalizeAssistantMessage()
  } catch (error: any) {
    if (error?.name === 'AbortError') {
      showToast('您停止了AI的回复', 'info')
    } else if (isApiServiceError(error)) {
      showApiServiceError(error.detail)
    } else {
      showToast('请检查网络连接', 'error')
    }
    finalizeAssistantMessage()
  } finally {
    activeStreamAbortController.value = null
    isSending.value = false
  }
}

const sendMessage = async () => {
  const messageText = pendingMessage.value.trim()
  if (!messageText) return
  await sendMessageByText(messageText)
}

const retryLastMessage = async () => {
  const messageToRetry = lastConversationUserMessage.value || lastUserMessage.value
  if (!messageToRetry) return
  messages.value = messages.value.filter(message => !message.hasError)
  await sendMessageByText(messageToRetry)
}

const retryIncompleteReply = async () => {
  const messageToRetry = lastConversationUserMessage.value || lastUserMessage.value
  if (!messageToRetry) return
  await sendMessageByText(messageToRetry)
}

const handleChatEnterSend = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void sendMessage()
  }
}

/**
 * 加载笔记详情
 */
const loadNoteDetail = async () => {
  try {
    isLoading.value = true
    noteDetail.value = await fetchNoteDetail(noteUuid.value)
    content.value = noteDetail.value.content || ''
    hasUnsavedChanges.value = false
  } catch (error: any) {
    showToast(error.message || '加载笔记失败', 'error')
    router.back()
  } finally {
    isLoading.value = false
  }
}

/**
 * 保存笔记内容
 */
const handleSave = async () => {
  if (!noteUuid.value) return

  try {
    isSaving.value = true
    await saveNoteContent({
      uuid: noteUuid.value,
      content: content.value
    })
    hasUnsavedChanges.value = false
    showToast('保存成功', 'success')
  } catch (error: any) {
    showToast(error.message || '保存失败', 'error')
  } finally {
    isSaving.value = false
  }
}

/**
 * 内容变化时标记未保存
 */
const handleContentChange = (value: string) => {
  content.value = value
  hasUnsavedChanges.value = true
}

/**
 * 图片上传处理
 */
const handleImageUpload = async (files: File[], callback: (urls: string[]) => void) => {
  const urls: string[] = []

  for (const file of files) {
    try {
      // 使用@jsquash/webp将图片转换为webp格式
      const imgData = await createImageBitmap(file)
      const canvas = document.createElement('canvas')
      canvas.width = imgData.width
      canvas.height = imgData.height
      const ctx = canvas.getContext('2d')
      if (!ctx) throw new Error('无法创建canvas上下文')
      ctx.drawImage(imgData, 0, 0)
      
      const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
      const webpBuffer = await encode(imageData, {
        quality: 0.8
      })

      // 创建FormData上传
      const formData = new FormData()
      formData.append('file', new Blob([webpBuffer], { type: 'image/webp' }), `${file.name}.webp`)

      // 上传到后端
      const result = await request.post('/static/save_image', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      }) as { imageUuid?: string }
      
      if (result?.imageUuid) {
        // 返回图片URL
        urls.push(buildStaticImageUrl(result.imageUuid))
      } else {
        throw new Error('上传失败')
      }
    } catch (error: any) {
      showToast(`上传图片失败: ${error.message}`, 'error')
    }
  }

  callback(urls)
}

/**
 * 返回笔记列表
 */
const goBack = () => {
  if (hasUnsavedChanges.value) {
    if (confirm('有未保存的更改，确定要离开吗？')) {
      router.push(`/notes?groupId=${noteDetail.value?.groupId || 0}`)
    }
  } else {
    router.push(`/notes?groupId=${noteDetail.value?.groupId || 0}`)
  }
}

/**
 * 键盘快捷键
 */
const handleKeydown = (e: KeyboardEvent) => {
  // Ctrl+S / Cmd+S 保存
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    handleSave()
  }
}

/**
 * 监听全屏状态变化
 */
const handleFullscreenChange = () => {
  isFullscreen.value = !!(
    document.fullscreenElement ||
    (document as any).webkitFullscreenElement ||
    (document as any).mozFullScreenElement ||
    (document as any).msFullscreenElement
  )
}

// 页面加载时获取笔记详情
onMounted(() => {
  loadNoteDetail()
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('resize', checkMobile)
  window.addEventListener('fullscreenchange', handleFullscreenChange)
  window.addEventListener('webkitfullscreenchange', handleFullscreenChange)
  window.addEventListener('mozfullscreenchange', handleFullscreenChange)
  window.addEventListener('msfullscreenchange', handleFullscreenChange)
  checkMobile()
  ensureConversationId()
  hasInitializedConversation.value = false
})

// 页面卸载时移除事件监听
onBeforeUnmount(() => {
  stopAssistantReply()
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', checkMobile)
  window.removeEventListener('fullscreenchange', handleFullscreenChange)
  window.removeEventListener('webkitfullscreenchange', handleFullscreenChange)
  window.removeEventListener('mozfullscreenchange', handleFullscreenChange)
  window.removeEventListener('msfullscreenchange', handleFullscreenChange)
})

watch(() => messages.value.length, () => {
  scrollMessagesToBottom()
})

watch(() => messages.value.map(message => `${message.id}:${message.renderVersion || 0}`).join('|'), () => {
  scrollMessagesToBottom()
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

    <Transition name="fade">
      <div v-if="showApiServiceErrorDialog" class="fixed inset-0 z-40 flex items-center justify-center px-4">
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="closeApiServiceErrorDialog"></div>
        <div class="relative z-10 w-full max-w-2xl rounded-[14px] border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6 shadow-xl">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-white">大模型 API 服务错误</h2>
          <p class="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">
            请求大模型 API 服务时出现错误，请检查 Chat 模型服务配置。
          </p>
          <div class="mt-4 rounded-xl bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700 p-4">
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">错误信息</p>
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

    <Transition name="fade">
      <div v-if="showEmbeddingModelDialog" class="fixed inset-0 z-40 flex items-center justify-center px-4">
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="closeEmbeddingModelDialog"></div>
        <div class="relative z-10 w-full max-w-md rounded-[14px] border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6 shadow-xl">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-white">尚未配置Chat模型</h2>
          <p class="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">
            在使用 Ask NoteX 前，请先前往设置页面完整配置 Chat 模型的基础 URL、API 密钥与模型名称。
          </p>
          <div class="mt-6 flex justify-end gap-3">
            <button
              @click="closeEmbeddingModelDialog"
              class="px-4 py-2 rounded-xl border border-gray-200 dark:border-gray-700 text-sm font-medium text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            >
              稍后再说
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

    <!-- 顶部导航栏（全屏时隐藏） -->
    <div
      class="fixed top-0 left-0 right-0 z-10 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800 transition-transform duration-300"
      :class="{ '-translate-y-full': isFullscreen }"
    >
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between h-16">
          <!-- 左侧：返回按钮和标题 -->
          <div class="flex items-center gap-4 flex-1 min-w-0">
            <button
              @click="goBack"
              class="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors flex-shrink-0"
              title="返回"
            >
              <svg class="w-5 h-5 text-gray-700 dark:text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
              </svg>
            </button>
            <div class="min-w-0 flex-1">
              <h1 class="text-lg sm:text-xl font-bold text-gray-900 dark:text-white truncate">
                {{ displayNoteTitle }}
              </h1>
              <p v-if="hasUnsavedChanges" class="text-xs text-orange-500 dark:text-orange-400">未保存</p>
            </div>
          </div>

          <!-- 右侧操作区 -->
          <div class="flex items-center space-x-2 sm:space-x-4">
            <button
              v-if="isMobile"
              @click="toggleMobileEditMode"
              class="px-3 py-2 rounded-lg bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-200 hover:bg-gray-200 dark:hover:bg-gray-700 transition-all duration-300 font-medium text-sm flex items-center gap-2"
              :title="isMobileEditing ? '切换到预览模式' : '切换到编辑模式'"
            >
              <svg v-if="isMobileEditing" class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12H9m12 0A9 9 0 113 12a9 9 0 0118 0z" />
              </svg>
              <svg v-else class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
              <span>{{ isMobileEditing ? '预览' : '编辑' }}</span>
            </button>

            <button
              @click="showNoteChatPanel ? closeNoteChat() : openNoteChat()"
              class="px-3 sm:px-4 py-2 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all duration-300 font-medium text-sm flex items-center gap-2"
              :class="showNoteChatPanel ? 'scale-[1.02]' : ''"
              :title="showNoteChatPanel ? '关闭笔记对话' : '打开笔记对话'"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-4l-4 4v-4z" />
              </svg>
              <span class="hidden sm:inline">Ask NoteX</span>
            </button>

            <!-- 保存按钮 -->
            <button
              @click="handleSave"
              :disabled="isSaving || !hasUnsavedChanges"
              class="px-3 sm:px-4 py-2 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all duration-300 font-medium text-sm flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <svg v-if="isSaving" class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7H5a2 2 0 00-2 2v9a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-3m-1 4l-3 3m0 0l-3-3m3 3V4" />
              </svg>
              <span class="hidden sm:inline">{{ isSaving ? '保存中...' : '保存' }}</span>
            </button>

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
    <div class="min-h-screen">
      <!-- 加载状态 -->
      <div v-if="isLoading" class="flex items-center justify-center py-12">
        <div class="text-gray-500 dark:text-gray-400">加载中...</div>
      </div>

      <!-- 移动端预览/编辑模式 -->
      <div v-else-if="isMobile" class="pt-20 pb-8 px-4 max-w-4xl mx-auto">
        <MdPreview
          v-if="!isMobileEditing"
          :id="previewId"
          :modelValue="content"
          :theme="editorTheme"
          previewTheme="default"
          language="zh-CN"
        />

        <div v-else class="mobile-editor-shell overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <MdEditor
            v-model="content"
            :theme="editorTheme"
            @onChange="handleContentChange"
            previewTheme="default"
            language="zh-CN"
            class="mobile-note-editor"
            :toolbars="mobileEditToolbars"
            :preview="false"
            :htmlPreview="false"
            :toolbarsExclude="['image', 'link', 'code', 'codeRow', 'quote', 'unorderedList', 'orderedList', 'task', 'table', 'mermaid', 'katex', 'revoke', 'next', 'prettier', 'fullscreen', 'preview', 'previewOnly', 'catalog', 'pageFullscreen', 'save']"
          />
        </div>
      </div>

      <!-- 桌面端编辑器 -->
      <div v-else class="pt-20 px-4 sm:px-6 lg:px-8 pb-6">
        <div class="mx-auto flex max-w-[1600px] gap-6 items-start">
          <div class="min-w-0 flex-1 transition-all duration-300" :class="isDesktopChatVisible ? 'basis-[70%]' : 'basis-full'">
            <MdEditor
              v-model="content"
              :theme="editorTheme"
              @onChange="handleContentChange"
              @onUploadImg="handleImageUpload"
              previewTheme="default"
              language="zh-CN"
              class="min-h-[calc(100vh-7rem)]"
              :toolbars="desktopToolbars"
            />
          </div>

          <Transition name="note-chat-slide">
            <aside
              v-if="isDesktopChatVisible"
              class="sticky top-20 h-[calc(100vh-7rem)] basis-[30%] min-w-[320px] max-w-[480px] overflow-hidden border border-gray-200 bg-white/95 backdrop-blur-md dark:border-gray-700 dark:bg-gray-900/95"
            >
              <div class="flex h-full flex-col">
                <div class="flex items-center justify-between gap-3 border-b border-gray-200 px-4 py-4 dark:border-gray-800">
                  <div class="flex items-center gap-3 min-w-0">
                    <div class="flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br from-fuchsia-500 via-sky-500 to-emerald-400 text-white shadow-lg shadow-fuchsia-500/20">
                      <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-4l-4 4v-4z" />
                      </svg>
                    </div>
                    <div class="min-w-0">
                      <p class="truncate text-sm font-semibold text-gray-900 dark:text-white">NoteX AI</p>
                      <p class="truncate text-xs text-gray-500 dark:text-gray-400">当前笔记专属对话</p>
                    </div>
                  </div>

                  <div class="flex items-center gap-2">
                    <button
                      @click="startNewConversation"
                      class="h-9 rounded-xl bg-gray-900 px-3 text-sm font-medium text-white transition-colors hover:bg-gray-800 dark:bg-white dark:text-gray-900 dark:hover:bg-gray-100"
                    >
                      新建会话
                    </button>
                    <button
                      @click="closeNoteChat"
                      class="flex h-9 w-9 items-center justify-center rounded-xl bg-gray-100 text-gray-600 transition-colors hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
                    >
                      <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                      </svg>
                    </button>
                  </div>
                </div>

                <div ref="messagesContainerRef" class="flex-1 min-h-0 overflow-y-auto px-4 py-5 space-y-4">
                  <div v-if="messages.length === 0" class="flex h-full items-center justify-center">
                    <div class="max-w-xs text-center">
                      <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-fuchsia-500 via-sky-500 to-emerald-400 text-white shadow-lg shadow-fuchsia-500/20">
                        <svg class="h-7 w-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-4l-4 4v-4z" />
                        </svg>
                      </div>
                      <p class="mt-4 text-sm font-medium text-gray-900 dark:text-white">围绕当前笔记直接发问</p>
                      <p class="mt-2 text-xs leading-6 text-gray-500 dark:text-gray-400">会自动携带笔记 UUID、分组 ID 和最新内容。</p>
                    </div>
                  </div>

                  <div v-else class="space-y-4">
                    <div
                      v-for="message in messages"
                      :key="message.id"
                      class="flex"
                      :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
                    >
                      <div class="max-w-[92%]">
                        <div v-if="message.role === 'user'" class="flex items-end justify-end gap-3">
                          <div class="rounded-2xl bg-gray-900 px-4 py-3 text-sm leading-7 whitespace-pre-wrap text-white dark:bg-white dark:text-gray-900">
                            {{ message.content }}
                          </div>
                          <div class="flex h-9 w-9 items-center justify-center overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
                            <img v-if="authStore.avatar" :src="authStore.avatar" :alt="authStore.nickname" class="h-full w-full object-cover">
                            <svg v-else class="h-5 w-5 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                            </svg>
                          </div>
                        </div>

                        <div v-else class="pt-1 text-sm leading-7 text-gray-800 dark:text-gray-100">
                          <div class="mb-3 flex items-center gap-3">
                            <div class="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-fuchsia-500 via-sky-500 to-emerald-400 text-white shadow-lg shadow-fuchsia-500/20">
                              <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-4l-4 4v-4z" />
                              </svg>
                            </div>
                            <span class="text-sm font-medium text-gray-900 dark:text-white">NoteX AI</span>
                          </div>

                          <MdPreview
                            v-if="message.content"
                            :key="`${message.id}-${message.renderVersion || 0}`"
                            :id="`note-chat-preview-${message.id}-${message.renderVersion || 0}`"
                            :model-value="message.content"
                            :theme="editorTheme"
                            preview-theme="github"
                            code-theme="github"
                            :no-highlight="true"
                            :sanitize="(html) => html"
                            class="note-chat-markdown-preview"
                          />

                          <div v-if="message.hasError" class="mt-4 space-y-3">
                            <div class="flex flex-wrap items-center gap-3">
                              <span class="text-sm text-red-500 dark:text-red-400">AI 回复异常</span>
                              <button
                                @click="retryLastMessage"
                                class="rounded-lg bg-gray-900 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-gray-800 dark:bg-white dark:text-gray-900 dark:hover:bg-gray-100"
                              >
                                重试
                              </button>
                            </div>
                            <div v-if="message.errorReason" class="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-xs leading-6 text-red-700 break-all whitespace-pre-wrap dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-300">
                              {{ message.errorReason }}
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div v-if="lastMessageNeedsRetry" class="flex justify-start">
                      <div class="max-w-[92%] rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/20 dark:text-amber-300">
                        <div>检测到本轮对话缺少 AI 回复</div>
                        <button
                          @click="retryIncompleteReply"
                          class="mt-3 rounded-lg bg-gray-900 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-gray-800 dark:bg-white dark:text-gray-900 dark:hover:bg-gray-100"
                        >
                          重试上一条消息
                        </button>
                      </div>
                    </div>
                  </div>

                  <div v-if="isAssistantResponding" class="flex justify-start">
                    <div class="w-full max-w-[92%] rounded-2xl border border-gray-200 bg-gray-50/80 px-4 py-3 dark:border-gray-700 dark:bg-gray-800/80">
                      <div class="flex items-center gap-3 text-sm text-gray-500 dark:text-gray-400">
                        <svg class="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                        </svg>
                        <span>AI 正在回复...</span>
                        <button
                          @click="stopAssistantReply"
                          class="ml-auto rounded-lg border border-gray-300 px-2.5 py-1 text-xs text-gray-700 transition-colors hover:bg-gray-100 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
                        >
                          停止回复
                        </button>
                      </div>

                      <div v-if="messages[messages.length - 1]?.toolCalls?.length" class="mt-3 max-h-[5.5rem] space-y-2 overflow-y-auto pr-1">
                        <div
                          v-for="(toolCall, toolCallIndex) in messages[messages.length - 1].toolCalls"
                          :key="`note-streaming-tool-${toolCallIndex}`"
                          class="flex items-center gap-2 rounded-full border border-gray-200 bg-white px-3 py-1 text-xs text-gray-600 shadow-sm dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300"
                        >
                          <svg v-if="!toolCall.completed" class="h-3.5 w-3.5 animate-spin text-gray-500 dark:text-gray-400 flex-shrink-0" fill="none" viewBox="0 0 24 24">
                            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                          </svg>
                          <svg v-else class="h-3.5 w-3.5 text-green-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                          </svg>
                          <span class="flex-shrink-0">正在执行：{{ toolCall.functionName }}</span>
                          <span class="truncate text-gray-400 dark:text-gray-500">{{ toolCall.argumentsText }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="shrink-0 border-t border-gray-200 bg-white px-4 py-4 dark:border-gray-800 dark:bg-gray-900">
                  <div class="rounded-2xl border border-gray-200 bg-white p-3 shadow-sm dark:border-gray-700 dark:bg-gray-800">
                    <textarea
                      v-model="pendingMessage"
                      rows="3"
                      placeholder="结合当前笔记内容，输入你想询问 NoteX AI 的内容"
                      class="w-full resize-none bg-transparent text-sm leading-7 text-gray-900 placeholder:text-gray-400 focus:outline-none dark:text-white dark:placeholder:text-gray-500"
                      :disabled="isSending"
                      @keydown="handleChatEnterSend"
                    ></textarea>
                    <div class="mt-3 flex items-center justify-between gap-3">
                      <p class="truncate text-xs text-gray-500 dark:text-gray-400">当前笔记：{{ noteDetail?.title || '未命名笔记' }}</p>
                      <button
                        @click="sendMessage"
                        :disabled="isSending || !pendingMessage.trim()"
                        class="h-10 rounded-xl bg-gray-900 px-4 text-sm font-medium text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-white dark:text-gray-900 dark:hover:bg-gray-100"
                      >
                        {{ isSending ? '发送中...' : '发送' }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </aside>
          </Transition>
        </div>
      </div>
    </div>

    <Transition name="fade">
      <div v-if="isChatDialogVisible" class="fixed inset-0 z-30 flex items-center justify-center bg-black/45 p-4 backdrop-blur-sm" @click.self="closeNoteChat">
        <div class="flex h-[78vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center justify-between gap-3 border-b border-gray-200 px-4 py-4 dark:border-gray-800">
            <div class="flex items-center gap-3 min-w-0">
              <div class="flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br from-fuchsia-500 via-sky-500 to-emerald-400 text-white shadow-lg shadow-fuchsia-500/20">
                <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-4l-4 4v-4z" />
                </svg>
              </div>
              <div class="min-w-0">
                <p class="truncate text-sm font-semibold text-gray-900 dark:text-white">NoteX AI</p>
                <p class="truncate text-xs text-gray-500 dark:text-gray-400">当前笔记专属对话</p>
              </div>
            </div>

            <div class="flex items-center gap-2">
              <button
                @click="startNewConversation"
                class="h-9 rounded-xl bg-gray-900 px-3 text-sm font-medium text-white transition-colors hover:bg-gray-800 dark:bg-white dark:text-gray-900 dark:hover:bg-gray-100"
              >
                新建会话
              </button>
              <button
                @click="closeNoteChat"
                class="flex h-9 w-9 items-center justify-center rounded-xl bg-gray-100 text-gray-600 transition-colors hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
              >
                <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>

          <div ref="messagesContainerRef" class="flex-1 min-h-0 overflow-y-auto px-4 py-5 space-y-4">
            <div v-if="messages.length === 0" class="flex h-full items-center justify-center">
              <div class="max-w-xs text-center">
                <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-fuchsia-500 via-sky-500 to-emerald-400 text-white shadow-lg shadow-fuchsia-500/20">
                  <svg class="h-7 w-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-4l-4 4v-4z" />
                  </svg>
                </div>
                <p class="mt-4 text-sm font-medium text-gray-900 dark:text-white">围绕当前笔记直接发问</p>
                <p class="mt-2 text-xs leading-6 text-gray-500 dark:text-gray-400">会自动携带笔记 UUID、分组 ID 和最新内容。</p>
              </div>
            </div>

            <div v-else class="space-y-4">
              <div
                v-for="message in messages"
                :key="`${message.id}-dialog`"
                class="flex"
                :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
              >
                <div class="max-w-[92%]">
                  <div v-if="message.role === 'user'" class="flex items-end justify-end gap-3">
                    <div class="rounded-2xl bg-gray-900 px-4 py-3 text-sm leading-7 whitespace-pre-wrap text-white dark:bg-white dark:text-gray-900">
                      {{ message.content }}
                    </div>
                    <div class="flex h-9 w-9 items-center justify-center overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
                      <img v-if="authStore.avatar" :src="authStore.avatar" :alt="authStore.nickname" class="h-full w-full object-cover">
                      <svg v-else class="h-5 w-5 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                      </svg>
                    </div>
                  </div>

                  <div v-else class="pt-1 text-sm leading-7 text-gray-800 dark:text-gray-100">
                    <div class="mb-3 flex items-center gap-3">
                      <div class="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-fuchsia-500 via-sky-500 to-emerald-400 text-white shadow-lg shadow-fuchsia-500/20">
                        <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-4l-4 4v-4z" />
                        </svg>
                      </div>
                      <span class="text-sm font-medium text-gray-900 dark:text-white">NoteX AI</span>
                    </div>

                    <MdPreview
                      v-if="message.content"
                      :key="`${message.id}-${message.renderVersion || 0}-dialog-markdown`"
                      :id="`note-chat-dialog-preview-${message.id}-${message.renderVersion || 0}`"
                      :model-value="message.content"
                      :theme="editorTheme"
                      preview-theme="github"
                      code-theme="github"
                      :no-highlight="true"
                      :sanitize="(html) => html"
                      class="note-chat-markdown-preview"
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="shrink-0 border-t border-gray-200 bg-white px-4 py-4 dark:border-gray-800 dark:bg-gray-900">
            <div class="rounded-2xl border border-gray-200 bg-white p-3 shadow-sm dark:border-gray-700 dark:bg-gray-800">
              <textarea
                v-model="pendingMessage"
                rows="3"
                placeholder="结合当前笔记内容，输入你想询问 NoteX AI 的内容"
                class="w-full resize-none bg-transparent text-sm leading-7 text-gray-900 placeholder:text-gray-400 focus:outline-none dark:text-white dark:placeholder:text-gray-500"
                :disabled="isSending"
                @keydown="handleChatEnterSend"
              ></textarea>
              <div class="mt-3 flex items-center justify-between gap-3">
                <p class="truncate text-xs text-gray-500 dark:text-gray-400">当前笔记：{{ noteDetail?.title || '未命名笔记' }}</p>
                <button
                  @click="sendMessage"
                  :disabled="isSending || !pendingMessage.trim()"
                  class="h-10 rounded-xl bg-gray-900 px-4 text-sm font-medium text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-white dark:text-gray-900 dark:hover:bg-gray-100"
                >
                  {{ isSending ? '发送中...' : '发送' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
/* md-editor-v3 自定义样式 */
:deep(.md-editor) {
  background-color: transparent;
  font-family: 'HarmonyOS Sans SC', system-ui, -apple-system, sans-serif;
}

/* 编辑器输入区域字体 */
:deep(.md-editor-content) {
  font-family: 'HarmonyOS Sans SC', system-ui, -apple-system, sans-serif;
}

:deep(.md-editor-textarea) {
  font-family: 'HarmonyOS Sans SC', system-ui, -apple-system, sans-serif;
}

/* 预览区域字体 */
:deep(.md-editor-preview) {
  font-family: 'HarmonyOS Sans SC', system-ui, -apple-system, sans-serif;
}

/* 工具栏字体 */
:deep(.md-editor-toolbar-wrapper) {
  font-family: 'HarmonyOS Sans SC', system-ui, -apple-system, sans-serif;
}

/* 代码块字体保持等宽 */
:deep(.md-editor-preview code),
:deep(.md-editor-preview pre) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

/* 黑暗模式优化 - 更亮的文字颜色 */
:deep(.md-editor-dark) {
  --md-bk-color: #111827;
  --md-bk-color-outside: #111827;
  --md-bk-hover: #1f2937;
  --md-bk-color-menu: #1f2937;
  --md-bk-color-active: #374151;
  --md-border-color: #374151;
  --md-text-color: #e5e7eb;
  --md-text-color-active: #f3f4f6;
  --md-border-color-active: #cecece;
  --md-color: #c4c4c4;
}

/* 预览区域文字颜色优化 */
:deep(.md-editor-dark .md-editor-preview) {
  color: #e5e7eb;
}

:deep(.md-editor-dark .md-editor-preview h1),
:deep(.md-editor-dark .md-editor-preview h2),
:deep(.md-editor-dark .md-editor-preview h3),
:deep(.md-editor-dark .md-editor-preview h4),
:deep(.md-editor-dark .md-editor-preview h5),
:deep(.md-editor-dark .md-editor-preview h6) {
  color: #f3f4f6;
}

:deep(.md-editor-dark .md-editor-preview p),
:deep(.md-editor-dark .md-editor-preview li),
:deep(.md-editor-dark .md-editor-preview td),
:deep(.md-editor-dark .md-editor-preview th) {
  color: #d1d5db;
}

:deep(.md-editor-dark .md-editor-preview a) {
  color: #818cf8;
}

:deep(.md-editor-dark .md-editor-preview code) {
  color: #e5e7eb;
  background-color: #1f2937;
}

:deep(.md-editor-dark .md-editor-preview pre) {
  background-color: #1f2937;
}

:deep(.md-editor-dark .md-editor-preview blockquote) {
  color: #d1d5db;
  border-left-color: #c9c9c9;
}

:deep(.md-editor-dark .md-editor-preview strong) {
  color: #f3f4f6;
}

:deep(.md-editor-preview-wrapper) {
  padding: 1rem;
}

.mobile-editor-shell {
  min-height: calc(100vh - 7rem);
}

:deep(.mobile-note-editor) {
  height: calc(100vh - 7rem);
}

:deep(.mobile-note-editor .md-editor-toolbar-wrapper) {
  border-bottom: 1px solid rgb(229 231 235);
}

:deep(.dark .mobile-note-editor .md-editor-toolbar-wrapper),
:deep(.md-editor-dark.mobile-note-editor .md-editor-toolbar-wrapper) {
  border-bottom-color: rgb(55 65 81);
}

:deep(.mobile-note-editor .md-editor-input-wrapper) {
  width: 100%;
}

:deep(.mobile-note-editor .md-editor-input) {
  min-height: calc(100vh - 11rem);
}

:deep(.mobile-note-editor .md-editor-resize-operate) {
  display: none;
}

:deep(.mobile-note-editor .md-editor-preview-wrapper) {
  display: none;
}

/* 移动端适配 */
@media (max-width: 640px) {
  :deep(.md-editor-toolbar-wrapper) {
    flex-wrap: wrap;
  }

  :deep(.mobile-note-editor .md-editor-toolbar-wrapper) {
    padding: 0.5rem;
    gap: 0.25rem;
  }
  
  :deep(.md-editor-toolbar-item) {
    padding: 0.25rem;
  }

  :deep(.mobile-note-editor .md-editor-input) {
    min-height: calc(100vh - 10.5rem);
  }
}

/* 预览模式样式 */
:deep(.md-preview) {
  background-color: transparent;
  font-family: 'HarmonyOS Sans SC', system-ui, -apple-system, sans-serif;
}

/* 预览模式代码块字体保持等宽 */
:deep(.md-preview code),
:deep(.md-preview pre) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

:deep(.md-preview-dark) {
  --md-bk-color: #111827;
  --md-text-color: #e5e7eb;
  --md-border-color: #374151;
  --md-heading-color: #f3f4f6;
  --md-quote-color: #d1d5db;
  --md-link-color: #d1d1d1;
  --md-code-bg-color: #1f2937;
  --md-code-color: #e5e7eb;
}

:deep(.md-preview-dark h1),
:deep(.md-preview-dark h2),
:deep(.md-preview-dark h3),
:deep(.md-preview-dark h4),
:deep(.md-preview-dark h5),
:deep(.md-preview-dark h6) {
  color: #f3f4f6;
}

:deep(.md-preview-dark p),
:deep(.md-preview-dark li),
:deep(.md-preview-dark td),
:deep(.md-preview-dark th) {
  color: #d1d5db;
}

:deep(.md-preview-dark strong) {
  color: #f3f4f6;
}

:deep(.note-chat-markdown-preview .md-editor-preview-wrapper) {
  padding: 0;
}

:deep(.note-chat-markdown-preview .md-editor-preview) {
  background: transparent;
}

.note-chat-slide-enter-active,
.note-chat-slide-leave-active {
  transition: all 0.28s ease;
}

.note-chat-slide-enter-from,
.note-chat-slide-leave-to {
  opacity: 0;
  transform: translateX(24px);
}
</style>
