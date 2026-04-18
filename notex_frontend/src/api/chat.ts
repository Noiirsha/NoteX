import request from '@/utils/request'
import { createApiServiceError, isApiServiceErrorResponse } from '@/utils/apiServiceError'
import type {
  ApiResponse,
  BeginChatRequest,
  ChatHistoryItem,
  ChatMessage,
  GenerateChatTitleRequest
} from '@/types/api'

export const fetchChats = async (): Promise<ChatHistoryItem[]> => {
  return request.get('/user/fetch_chats')
}

export const fetchConversation = async (conversation_id: string): Promise<ChatMessage[]> => {
  return request.get('/ai/chat/fetch_conversation', { params: { conversation_id } })
}

export const generateChatTitle = async (data: GenerateChatTitleRequest): Promise<string> => {
  return request.post('/ai/chat/generate_title', data)
}

export const deleteConversation = async (conversation_id: string): Promise<null> => {
  return request.delete('/ai/chat/delete_conversation', { params: { conversation_id } })
}

export const beginChatStream = async (data: BeginChatRequest): Promise<Response> => {
  const token = localStorage.getItem('auth-store')
  let authToken = ''

  if (token) {
    try {
      const parsed = JSON.parse(token)
      authToken = parsed.token || ''
    } catch {
      authToken = ''
    }
  }

  const response = await fetch('/api/ai/chat/begin_chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(authToken ? { Authorization: `Bearer ${authToken}` } : {})
    },
    body: JSON.stringify(data)
  })

  const contentType = response.headers.get('content-type') || ''

  if (contentType.includes('application/json')) {
    const body = await response.json() as ApiResponse

    if (isApiServiceErrorResponse(body)) {
      throw createApiServiceError(body.data)
    }

    throw new Error(body.message || `HTTP ${response.status}`)
  }

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  return response
}
