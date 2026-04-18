/**
 * 通用API响应模型
 */
export interface ApiResponse<T = any> {
  code: number | string
  message: string
  data: T
}

/**
 * 用户注册请求参数
 */
export interface RegisterRequest {
  username: string
  passwordHash: string
  email: string
  nickname: string
  darkMode?: boolean
}

/**
 * 用户登录请求参数
 */
export interface LoginRequest {
  username: string
  passwordHash: string
}

/**
 * 用户登录响应数据
 */
export interface LoginResponse {
  token: string
  username: string
  nickname: string
  avatarImageUrl?: string
}

/**
 * 用户信息
 */
export interface UserInfo {
  userId?: number
  username: string
  nickname?: string
  avatar?: string
  email?: string
}

/**
 * 修改密码请求参数
 */
export interface ChangePasswordRequest {
  oldPasswordHash: string
  newPasswordHash: string
}

/**
 * 修改个人信息请求参数
 */
export interface ChangeInfoRequest {
  nickname?: string
  avatarImageUrl?: string
}

/**
 * 用户偏好设置
 */
export interface UserPreferences {
  darkMode?: boolean
  [key: string]: any
}

/**
 * AI模型配置
 */
export interface AIModelsConfig {
  standardModelBase?: string
  standardModelApiKey?: string
  standardModelModelName?: string
  embeddedModelBase?: string
  embeddedModelApiKey?: string
  embeddedModelModelName?: string
  changedEmbeddedModel?: boolean
}

/**
 * 笔记分组
 */
export interface NoteGroup {
  id: number
  groupName: string
  parentGroupId: number
}

/**
 * 笔记基本信息
 */
export interface NoteBasicInfo {
  id: number
  noteUuid: string
  groupId: number
  title: string
  createdAt: string
  updatedAt: string
  isIndexed?: boolean
}

/**
 * 笔记详情
 */
export interface NoteDetail extends NoteBasicInfo {
  content: string
}

/**
 * 创建笔记请求参数
 */
export interface CreateNoteRequest {
  title: string
  groupId: number
}

/**
 * 创建笔记分组请求参数
 */
export interface CreateNoteGroupRequest {
  groupName: string
  parentGroupId?: number
}

/**
 * 修改笔记请求参数
 */
export interface ModifyNoteRequest {
  uuid: string
  title?: string
  groupId?: number
}

/**
 * 修改笔记分组请求参数
 */
export interface ModifyNoteGroupRequest {
  id: number
  groupName?: string
  parentGroupId?: number
}

/**
 * 保存笔记内容请求参数
 */
export interface SaveNoteContentRequest {
  uuid: string
  content: string
}

/**
 * 最近访问笔记信息
 */
export interface NoteRecentAccessVO {
  noteUuid: string
  noteTitle: string
}

/**
 * 笔记索引状态
 */
export interface NoteIndexStatus {
  noteUuid: string
  title: string
  updatedAt: string
  isIndexed: boolean
}

/**
 * RAG 搜索请求参数
 */
export interface RagSearchRequest {
  searchContent: string
}

/**
 * RAG 搜索结果
 */
export interface RagSearchResult {
  noteUuid: string
  noteTitle: string
  contentMatchRate: number
  content: string
}

/**
 * 聊天历史项
 */
export interface ChatHistoryItem {
  conversationId: string
  conversationTitle: string
  lastAccessTime: string
}

/**
 * 聊天消息
 */
export interface ChatMessage {
  content: string
  type: 'ASSISTANT' | 'USER'
}

/**
 * 聊天工作区详情
 */
export interface ChatWorkspaceDetail {
  noteUuid?: string
  groupId?: number
  noteContent?: string
}

/**
 * 发起聊天请求
 */
export interface BeginChatRequest {
  userNickname?: string
  conversationId: string
  message?: string
  workspaceDetail?: ChatWorkspaceDetail
}

/**
 * 生成标题请求
 */
export interface GenerateChatTitleRequest {
  conversationId?: string
  firstContent?: string
}
