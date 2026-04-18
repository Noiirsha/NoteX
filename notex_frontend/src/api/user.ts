import request from '@/utils/request'
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  ChangePasswordRequest,
  ChangeInfoRequest,
  UserInfo,
  UserPreferences,
  AIModelsConfig
} from '@/types/api'

/**
 * 用户登录
 * @param data 登录参数（用户名、密码哈希）
 * @returns 登录响应（包含token）
 */
export const login = (data: LoginRequest): Promise<LoginResponse> => {
  return request({
    url: '/user/login',
    method: 'POST',
    data
  })
}

/**
 * 用户注册
 * @param data 注册参数（用户名、密码哈希、邮箱、昵称、黑暗模式）
 * @returns void
 */
export const register = (data: RegisterRequest): Promise<void> => {
  return request({
    url: '/user/register_new_account',
    method: 'POST',
    data
  })
}

/**
 * 获取用户信息
 * @returns 用户信息
 */
export const getUserInfo = (): Promise<UserInfo> => {
  return request({
    url: '/user/fetch_user_information',
    method: 'GET'
  })
}

/**
 * 获取用户偏好设置
 * @returns 用户偏好设置
 */
export const getUserPreferences = (): Promise<UserPreferences> => {
  return request({
    url: '/user/fetch_user_preferences',
    method: 'GET'
  })
}

/**
 * 获取用户AI模型配置
 * @returns AI模型配置
 */
export const getUserAIModels = (): Promise<AIModelsConfig> => {
  return request({
    url: '/user/fetch_user_ai_models',
    method: 'GET'
  })
}

/**
 * 修改密码
 * @param data 修改密码参数（旧密码、新密码）
 */
export const changePassword = (data: ChangePasswordRequest): Promise<void> => {
  return request({
    url: '/user/change_password',
    method: 'PUT',
    data
  })
}

/**
 * 修改个人信息
 * @param data 修改信息参数（昵称、头像）
 */
export const changeInfo = (data: ChangeInfoRequest): Promise<void> => {
  return request({
    url: '/user/change_information',
    method: 'PUT',
    data
  })
}

/**
 * 修改用户偏好设置
 * @param data 偏好设置参数
 */
export const changePreferences = (data: UserPreferences): Promise<void> => {
  return request({
    url: '/user/change_preferences',
    method: 'PUT',
    data
  })
}

/**
 * 修改AI模型配置
 * @param data AI模型配置参数
 */
export const changeAIModels = (data: AIModelsConfig): Promise<void> => {
  return request({
    url: '/user/change_ai_model',
    method: 'PUT',
    params: {
      changed_embedded_model: data.changedEmbeddedModel ?? false
    },
    data
  })
}
