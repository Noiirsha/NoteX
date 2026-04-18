import { getUserAIModels } from '@/api/user'
import type { AIModelsConfig } from '@/types/api'

let cachedUserAIModels: AIModelsConfig | null = null
let userAIModelsRequest: Promise<AIModelsConfig> | null = null

const hasValue = (value?: string) => Boolean(value?.trim())

export const isStandardModelConfigured = (config?: AIModelsConfig | null) => {
  if (!config) return false

  return hasValue(config.standardModelBase)
    && hasValue(config.standardModelApiKey)
    && hasValue(config.standardModelModelName)
}

export const isEmbeddedModelConfigured = (config?: AIModelsConfig | null) => {
  if (!config) return false

  return hasValue(config.embeddedModelBase)
    && hasValue(config.embeddedModelApiKey)
    && hasValue(config.embeddedModelModelName)
}

export const fetchCachedUserAIModels = async (forceRefresh: boolean = false): Promise<AIModelsConfig> => {
  if (forceRefresh) {
    cachedUserAIModels = null
    userAIModelsRequest = null
  }

  if (cachedUserAIModels) {
    return cachedUserAIModels
  }

  if (!userAIModelsRequest) {
    userAIModelsRequest = getUserAIModels()
      .then((config: AIModelsConfig) => {
        cachedUserAIModels = config
        return config
      })
      .finally(() => {
        userAIModelsRequest = null
      })
  }

  return userAIModelsRequest
}

export const primeUserAIModelsCache = (config: AIModelsConfig | null) => {
  cachedUserAIModels = config ? { ...config } : null
  userAIModelsRequest = null
}

export const clearUserAIModelsCache = () => {
  cachedUserAIModels = null
  userAIModelsRequest = null
}
