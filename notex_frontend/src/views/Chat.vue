<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { config, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import { fetchIndexStatus, fetchNoteGroups } from '@/api/note'
import { beginChatStream, deleteConversation, fetchChats, fetchConversation, generateChatTitle } from '@/api/chat'
import FolderSelector from '@/components/FolderSelector.vue'
import Toast from '@/components/Toast.vue'
import { fetchCachedUserAIModels, isStandardModelConfigured } from '@/utils/aiModel'
import { isApiServiceError } from '@/utils/apiServiceError'
import { createUuid } from '@/utils/uuid'
import type { ChatHistoryItem, ChatMessage, NoteGroup } from '@/types/api'

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

const showMobileNavMenu = ref(false)
const showUserMenu = ref(false)
const showWorkspaceSelector = ref(false)
const showNoWorkspaceHint = ref(false)
const showSidebar = ref(false)
const isLoadingChats = ref(false)
const isLoadingConversation = ref(false)
const isSending = ref(false)
const messagesContainerRef = ref<HTMLElement | null>(null)
const activeStreamAbortController = ref<AbortController | null>(null)
const chats = ref<ChatHistoryItem[]>([])
const messages = ref<ChatMessageView[]>([])
const noteGroups = ref<NoteGroup[]>([])
const selectedWorkspaceGroupId = ref(-1)
const currentConversationId = ref('')
const currentConversationTitle = ref('新会话')
const pendingMessage = ref('')
const lastUserMessage = ref('')
const isNewConversation = ref(true)
const hasPendingIndexedNotes = ref(false)
const hasShownIndexWarning = ref(false)
const showEmbeddingModelDialog = ref(false)
const showApiServiceErrorDialog = ref(false)
const apiServiceErrorDetail = ref('')
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

const selectedWorkspaceName = computed(() => {
  if (selectedWorkspaceGroupId.value === -1) return '请选择工作区'
  const folder = noteGroups.value.find((group: NoteGroup) => group.id === selectedWorkspaceGroupId.value)
  return folder?.groupName || '未知工作区'
})

const currentChatHistoryTitle = computed(() => {
  const matched = chats.value.find((item: ChatHistoryItem) => item.conversationId === currentConversationId.value)
  return matched?.conversationTitle || currentConversationTitle.value || '新会话'
})

const formatChatAccessTime = (value: string) => {
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
  if (messages.value.length === 0 || isLoadingConversation.value || isSending.value) {
    return false
  }

  const lastMessage = messages.value[messages.value.length - 1]
  return lastMessage?.role === 'user'
})

const isAssistantResponding = computed(() => {
  const lastMessage = messages.value[messages.value.length - 1]
  return isSending.value || Boolean(lastMessage?.role === 'assistant' && lastMessage.isStreaming)
})

const showToast = (message: string, type: 'success' | 'error' | 'info' | 'warning') => {
  toast.value = { show: true, message, type }
}

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
  closeUserMenu()
  router.push('/settings')
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

const handleDeleteConversation = async (conversationId: string) => {
  try {
    await deleteConversation(conversationId)

    if (currentConversationId.value === conversationId) {
      currentConversationId.value = ''
      currentConversationTitle.value = '新会话'
      messages.value = []
      lastUserMessage.value = ''
      isNewConversation.value = true
    }

    await loadChats()

    if (currentConversationId.value) {
      const stillExists = chats.value.some((item: ChatHistoryItem) => item.conversationId === currentConversationId.value)
      if (!stillExists) {
        currentConversationId.value = ''
      }
    }

    if (!currentConversationId.value) {
      if (chats.value.length > 0) {
        await openConversation(chats.value[0].conversationId)
      } else {
        await startNewConversation()
      }
    }

    showToast('删除会话成功', 'success')
  } catch (error: any) {
    showToast(error.message || '删除会话失败', 'error')
  }
}

const createConversationId = () => {
  return createUuid()
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

const normalizeAssistantMessage = (content: string): Pick<ChatMessageView, 'content' | 'toolCalls' | 'hasError' | 'errorReason' | 'showErrorDetail'> => {
  let normalizedContent = content || ''
  const toolCalls: NonNullable<ChatMessageView['toolCalls']> = []
  let hasError = false
  let errorReason = ''

  const responseErrorTag = parseResponseErrorTag(normalizedContent)
  if (responseErrorTag) {
    hasError = true
    errorReason = responseErrorTag.reason
    normalizedContent = normalizedContent.replace(responseErrorTag.raw, '')
  }

  while (true) {
    const toolTagStart = normalizedContent.indexOf('<notex_tool_calling')
    if (toolTagStart === -1) break

    const toolInfo = parseToolCallingTag(normalizedContent.slice(toolTagStart))
    if (toolInfo) {
      toolCalls.push({
        functionName: toolInfo.functionName,
        argumentsText: toolInfo.argumentsText,
        completed: true
      })
      normalizedContent = `${normalizedContent.slice(0, toolTagStart)}${normalizedContent.slice(toolTagStart + toolInfo.raw.length)}`
      continue
    }

    break
  }

  return {
    content: normalizedContent,
    toolCalls,
    hasError,
    errorReason,
    showErrorDetail: true
  }
}

const mapConversationMessages = (items: ChatMessage[]): ChatMessageView[] => {
  return items.map((item, index) => {
    const role = (item.type === 'USER' ? 'user' : 'assistant') as ChatMessageView['role']
    const assistantState = role === 'assistant'
      ? normalizeAssistantMessage(item.content || '')
      : {
          content: item.content || '',
          toolCalls: [],
          hasError: false,
          errorReason: '',
          showErrorDetail: true
        }

    return {
      id: `${item.type}-${index}-${Date.now()}`,
      role,
      content: assistantState.content,
      streamBuffer: '',
      renderVersion: 0,
      toolCalls: assistantState.toolCalls,
      hasError: assistantState.hasError,
      errorReason: assistantState.errorReason,
      showErrorDetail: assistantState.showErrorDetail
    }
  })
}

const loadChats = async () => {
  try {
    isLoadingChats.value = true
    chats.value = await fetchChats()
  } catch (error: any) {
    showToast(error.message || '获取历史会话失败', 'error')
  } finally {
    isLoadingChats.value = false
  }
}

const loadWorkspaces = async () => {
  try {
    noteGroups.value = await fetchNoteGroups()
    if (selectedWorkspaceGroupId.value === -1 && noteGroups.value.length > 0) {
      selectedWorkspaceGroupId.value = noteGroups.value[0].id
    }
  } catch (error: any) {
    showToast(error.message || '获取工作区失败', 'error')
  }
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

const openWorkspaceSelector = async () => {
  await loadWorkspaces()
  if (noteGroups.value.length === 0) {
    showNoWorkspaceHint.value = true
    return
  }
  showWorkspaceSelector.value = true
}

const openConversation = async (conversationId: string) => {
  try {
    isLoadingConversation.value = true
    currentConversationId.value = conversationId
    isNewConversation.value = false
    showSidebar.value = false
    const history = await fetchConversation(conversationId)
    messages.value = mapConversationMessages(history)
    lastUserMessage.value = lastConversationUserMessage.value
    const matched = chats.value.find((item: ChatHistoryItem) => item.conversationId === conversationId)
    currentConversationTitle.value = matched?.conversationTitle || '会话详情'
  } catch (error: any) {
    showToast(error.message || '获取会话失败', 'error')
  } finally {
    isLoadingConversation.value = false
  }
}

const startNewConversation = async () => {
  await loadWorkspaces()
  if (noteGroups.value.length === 0) {
    showNoWorkspaceHint.value = true
    return
  }
  currentConversationId.value = createConversationId()
  currentConversationTitle.value = '新会话'
  messages.value = []
  pendingMessage.value = ''
  lastUserMessage.value = ''
  isNewConversation.value = true
  showSidebar.value = false
}

const parseToolCallingTag = (content: string) => {
  const closeIndex = content.indexOf('/>')
  if (closeIndex === -1) return null

  const raw = content.slice(0, closeIndex + 2)
  const match = raw.match(/<notex_tool_calling\s+function="([\s\S]*?)"\s+arguments="([\s\S]*?)"\s*\/>/)
  if (!match) return null

  return {
    raw,
    functionName: match[1],
    argumentsText: match[2]
  }
}

const parseResponseErrorTag = (content: string) => {
  const match = content.match(/<notex_response_error(?:\s+reason="([\s\S]*?)")?\s*\/>/)
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
      const payload = line.slice(5)
      dataLines.push(payload)
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

const syncGeneratedTitle = async (conversationId: string, firstContent: string) => {
  try {
    const title = await generateChatTitle({ conversationId, firstContent })
    currentConversationTitle.value = title || '新会话'
    await loadChats()
  } catch (error) {
    if (isApiServiceError(error)) {
      showApiServiceError(error.detail)
    }
    console.error('生成标题失败:', error)
  }
}

const sendMessageByText = async (messageText: string) => {
  const normalizedMessageText = messageText.trim()
  if (!normalizedMessageText) return

  if (selectedWorkspaceGroupId.value === -1 || selectedWorkspaceGroupId.value === 0) {
    await openWorkspaceSelector()
    return
  }

  if (!(await ensureStandardModelConfigured())) {
    return
  }

  try {
    isSending.value = true
    if (!currentConversationId.value) {
      currentConversationId.value = createConversationId()
      isNewConversation.value = true
    }

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

    const workspaceDetail = {
      groupId: selectedWorkspaceGroupId.value
    }

    const response = await beginChatStream({
      userNickname: authStore.nickname || authStore.username,
      conversationId: currentConversationId.value,
      message: normalizedMessageText,
      workspaceDetail
    })
    const abortController = new AbortController()
    activeStreamAbortController.value = abortController

    if (isNewConversation.value) {
      void syncGeneratedTitle(currentConversationId.value, normalizedMessageText)
      isNewConversation.value = false
    }

    await readSseStream(response, abortController.signal)
    finalizeAssistantMessage()
    await loadChats()
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

const handleEnterSend = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void sendMessage()
  }
}

const stopAssistantReply = () => {
  activeStreamAbortController.value?.abort()
}

onMounted(async () => {
  await Promise.all([loadChats(), loadWorkspaces(), loadIndexStatus()])
  if (chats.value.length > 0) {
    await openConversation(chats.value[0].conversationId)
  } else {
    await startNewConversation()
  }
  scrollMessagesToBottom()
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
            在使用 NoteX AI 前，请先前往设置页面完整配置 Chat 模型的基础 URL、API 密钥与模型名称。
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

    <div class="fixed top-0 left-0 right-0 z-20 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between h-16">
          <div class="flex items-center gap-4">
            <button
              @click="showSidebar = !showSidebar"
              class="lg:hidden p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            >
              <svg class="w-5 h-5 text-gray-700 dark:text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            </button>

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

    <div class="pt-16 min-h-screen flex">
      <Transition name="fade">
        <div
          v-if="showSidebar"
          class="lg:hidden fixed inset-0 z-10 bg-black/40 backdrop-blur-sm"
          @click="showSidebar = false"
        ></div>
      </Transition>

      <aside class="fixed lg:static top-16 bottom-0 left-0 z-20 w-80 max-w-[85vw] border-r border-gray-200 dark:border-gray-800 bg-white/95 dark:bg-gray-900/95 backdrop-blur-md transform transition-transform duration-300 lg:translate-x-0" :class="showSidebar ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'">
        <div class="h-full flex flex-col">
          <div class="p-4 border-b border-gray-200 dark:border-gray-800">
            <button
              @click="startNewConversation"
              class="w-full h-11 rounded-xl bg-gray-900 dark:bg-white text-white dark:text-gray-900 text-sm font-medium hover:bg-gray-800 dark:hover:bg-gray-100 transition-colors"
            >
              新建会话
            </button>
          </div>

          <div class="flex-1 overflow-y-auto p-3 space-y-2">
            <div v-if="isLoadingChats" class="px-3 py-4 text-sm text-gray-500 dark:text-gray-400">加载历史会话中...</div>
            <div
              v-for="item in chats"
              :key="item.conversationId"
              class="group rounded-xl px-3 py-3 transition-colors"
              :class="currentConversationId === item.conversationId
                ? 'bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-white'
                : 'hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-700 dark:text-gray-300'"
            >
              <div class="flex items-start gap-2">
                <button
                  @click="openConversation(item.conversationId)"
                  class="min-w-0 flex-1 text-left"
                >
                  <div class="text-sm font-medium truncate">{{ item.conversationTitle }}</div>
                  <div class="mt-1 text-xs text-gray-500 dark:text-gray-400 truncate">{{ formatChatAccessTime(item.lastAccessTime) }}</div>
                </button>

                <button
                  @click.stop="handleDeleteConversation(item.conversationId)"
                  class="mt-0.5 flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition-colors hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20 dark:hover:text-red-400"
                  title="删除会话"
                >
                  <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6M9 7V4a1 1 0 011-1h4a1 1 0 011 1v3m-7 0h8" />
                  </svg>
                </button>
              </div>
            </div>
            <div v-if="!isLoadingChats && chats.length === 0" class="px-3 py-6 text-sm text-gray-500 dark:text-gray-400 text-center">
              暂无历史会话
            </div>
          </div>
        </div>
      </aside>

      <main class="flex-1 lg:ml-0 w-full h-[calc(100vh-4rem)] overflow-hidden">
        <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-6 sm:py-8 flex flex-col h-full overflow-hidden">
          <div class="shrink-0 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-5 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900">
            <div>
              <h2 class="text-2xl font-bold text-gray-900 dark:text-white truncate">{{ currentChatHistoryTitle }}</h2>
              <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">选择工作区后即可开始与 NoteX AI 对话</p>
            </div>

            <div class="flex items-center gap-3">
              <button
                @click="openWorkspaceSelector"
                class="flex items-center justify-between gap-3 min-w-[220px] px-4 py-3 rounded-xl border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 text-sm text-gray-900 dark:text-white hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
              >
                <span class="truncate">{{ selectedWorkspaceName }}</span>
                <svg class="w-4 h-4 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                </svg>
              </button>
            </div>
          </div>

          <div ref="messagesContainerRef" class="flex-1 min-h-0 py-6 space-y-4 overflow-y-auto scroll-smooth">
            <div v-if="isLoadingConversation" class="text-center text-sm text-gray-500 dark:text-gray-400 py-10">
              正在加载会话内容...
            </div>

            <div v-else-if="messages.length === 0" class="h-full flex items-center justify-center">
              <div class="text-center max-w-xl">
                <h3 class="text-3xl font-bold text-gray-900 dark:text-white">开始一段新的对话</h3>
                <p class="mt-3 text-gray-500 dark:text-gray-400">先选择工作区，再向 NoteX AI 提问。如有文件创建，将会在选中的工作区进行创建。</p>
              </div>
            </div>

            <div v-else class="space-y-4">
              <div
                v-for="message in messages"
                :key="message.id"
                class="flex"
                :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
              >
                <div class="max-w-[85%] sm:max-w-[75%]">
                  <div v-if="message.role === 'user'" class="flex items-end justify-end gap-3">
                    <div class="rounded-2xl bg-gray-900 dark:bg-white text-white dark:text-gray-900 px-4 py-3 text-sm leading-7 whitespace-pre-wrap">
                      {{ message.content }}
                    </div>
                    <div class="w-9 h-9 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center overflow-hidden flex-shrink-0">
                      <img v-if="authStore.avatar" :src="authStore.avatar" :alt="authStore.nickname" class="w-full h-full object-cover">
                      <svg v-else class="w-5 h-5 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                      </svg>
                    </div>
                  </div>

                  <div v-else class="pt-1 text-sm text-gray-800 dark:text-gray-100 leading-7">
                    <MdPreview
                      v-if="message.content"
                      :key="`${message.id}-${message.renderVersion || 0}`"
                      :id="`chat-preview-${message.id}-${message.renderVersion || 0}`"
                      :model-value="message.content"
                      :theme="themeStore.isDark ? 'dark' : 'light'"
                      preview-theme="github"
                      code-theme="github"
                      :no-highlight="true"
                      :sanitize="(html) => html"
                      class="chat-markdown-preview"
                    />

                    <div v-if="message.hasError" class="mt-4 space-y-3">
                      <div class="flex flex-wrap items-center gap-3">
                        <span class="text-sm text-red-500 dark:text-red-400">AI 回复异常</span>
                        <button
                          @click="retryLastMessage"
                          class="px-3 py-1.5 rounded-lg bg-gray-900 dark:bg-white text-white dark:text-gray-900 text-xs font-medium hover:bg-gray-800 dark:hover:bg-gray-100 transition-colors"
                        >
                          重试
                        </button>
                      </div>
                      <div
                        v-if="message.errorReason"
                        class="rounded-xl border border-red-200 dark:border-red-900/60 bg-red-50 dark:bg-red-950/30 px-3 py-2 text-xs leading-6 text-red-700 dark:text-red-300 break-all whitespace-pre-wrap"
                      >
                        {{ message.errorReason }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="lastMessageNeedsRetry" class="flex justify-start">
                <div class="max-w-[85%] sm:max-w-[75%] rounded-2xl border border-amber-200 dark:border-amber-900/60 bg-amber-50 dark:bg-amber-950/20 px-4 py-3 text-sm text-amber-700 dark:text-amber-300">
                  <div>检测到本轮对话缺少 AI 回复</div>
                  <button
                    @click="retryIncompleteReply"
                    class="mt-3 px-3 py-1.5 rounded-lg bg-gray-900 dark:bg-white text-white dark:text-gray-900 text-xs font-medium hover:bg-gray-800 dark:hover:bg-gray-100 transition-colors"
                  >
                    重试上一条消息
                  </button>
                </div>
              </div>
            </div>

            <div v-if="isAssistantResponding" class="flex justify-start">
              <div class="w-full max-w-[85%] sm:max-w-[75%] rounded-2xl border border-gray-200 dark:border-gray-700 bg-gray-50/80 dark:bg-gray-800/80 px-4 py-3">
                <div class="flex items-center gap-3 text-sm text-gray-500 dark:text-gray-400">
                  <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                  </svg>
                  <span>AI 正在回复...</span>
                  <button
                    @click="stopAssistantReply"
                    class="ml-auto px-2.5 py-1 rounded-lg border border-gray-300 dark:border-gray-600 text-xs text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                  >
                    停止回复
                  </button>
                </div>

                <div
                  v-if="messages[messages.length - 1]?.toolCalls?.length"
                  class="mt-3 max-h-[5.5rem] overflow-y-auto pr-1 space-y-2"
                >
                  <div
                    v-for="(toolCall, toolCallIndex) in messages[messages.length - 1].toolCalls"
                    :key="`streaming-tool-${toolCallIndex}`"
                    class="flex items-center gap-2 rounded-full bg-white dark:bg-gray-900 px-3 py-1 text-xs text-gray-600 dark:text-gray-300 border border-gray-200 dark:border-gray-700 shadow-sm"
                  >
                    <svg v-if="!toolCall.completed" class="w-3.5 h-3.5 animate-spin text-gray-500 dark:text-gray-400 flex-shrink-0" fill="none" viewBox="0 0 24 24">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                    </svg>
                    <svg v-else class="w-3.5 h-3.5 text-green-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                    </svg>
                    <span class="flex-shrink-0">正在执行：{{ toolCall.functionName }}</span>
                    <span class="text-gray-400 dark:text-gray-500 truncate">{{ toolCall.argumentsText }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="shrink-0 pt-4 border-t border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900">
            <div class="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-3 shadow-sm">
              <textarea
                v-model="pendingMessage"
                rows="3"
                placeholder="选择工作区后，输入你想让 NoteX AI 帮你完成的内容"
                class="w-full resize-none bg-transparent text-sm text-gray-900 dark:text-white placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none leading-7"
                :disabled="isSending"
                @keydown="handleEnterSend"
              ></textarea>
              <div class="mt-3 flex items-center justify-between gap-3">
                <p class="text-xs text-gray-500 dark:text-gray-400 truncate">当前工作区：{{ selectedWorkspaceName }}</p>
                <button
                  @click="sendMessage"
                  :disabled="isSending || !pendingMessage.trim()"
                  class="h-10 px-4 rounded-xl bg-gray-900 dark:bg-white text-white dark:text-gray-900 text-sm font-medium hover:bg-gray-800 dark:hover:bg-gray-100 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {{ isSending ? '发送中...' : '发送' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>

    <div
      v-if="showWorkspaceSelector"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
      @click.self="showWorkspaceSelector = false"
    >
      <div class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6">
        <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-4">选择工作区</h3>
        <FolderSelector
          :note-groups="noteGroups"
          :selected-group-id="selectedWorkspaceGroupId"
          @update:selected-group-id="selectedWorkspaceGroupId = $event"
        />
        <div class="flex gap-3 mt-6">
          <button
            @click="showWorkspaceSelector = false"
            class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
          >
            取消
          </button>
          <button
            @click="showWorkspaceSelector = false"
            class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
          >
            确认
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="showNoWorkspaceHint"
      class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
      @click.self="showNoWorkspaceHint = false"
    >
      <div class="w-full max-w-md bg-white dark:bg-gray-800 rounded-xl shadow-2xl p-6">
        <div class="flex items-center justify-center w-16 h-16 mx-auto mb-4 rounded-full bg-gray-100 dark:bg-gray-700">
          <svg class="w-8 h-8 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
          </svg>
        </div>
        <h3 class="text-xl font-semibold text-gray-900 dark:text-white mb-2 text-center">暂无工作区</h3>
        <p class="text-gray-600 dark:text-gray-400 text-center">请先创建一个工作区，再开始与 NoteX AI 对话</p>
        <div class="flex gap-3 mt-6">
          <button
            @click="showNoWorkspaceHint = false"
            class="flex-1 py-2.5 px-4 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-all font-medium"
          >
            取消
          </button>
          <button
            @click="router.push('/notes'); showNoWorkspaceHint = false"
            class="flex-1 py-2.5 px-4 bg-gray-900 dark:bg-white text-white dark:text-gray-900 rounded-lg hover:bg-gray-800 dark:hover:bg-gray-100 transition-all font-medium"
          >
            前往创建
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
.chat-markdown-preview {
  background: transparent;
}

.chat-markdown-preview .md-editor-preview-wrapper {
  padding: 0;
  background: transparent;
}

.chat-markdown-preview .md-editor-preview {
  background: transparent;
  color: inherit;
}

.chat-markdown-preview .md-editor-preview p:first-child {
  margin-top: 0;
}

.chat-markdown-preview .md-editor-preview p:last-child {
  margin-bottom: 0;
}
</style>
