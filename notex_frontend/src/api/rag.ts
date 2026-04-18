import request from '@/utils/request'
import type { RagSearchRequest, RagSearchResult } from '@/types/api'

/**
 * RAG 关键词检索
 */
export const queryByKeyword = async (data: RagSearchRequest): Promise<RagSearchResult[]> => {
  return request.post('/ai/rag/query_by_keyword', {
    searchRequest: data
  })
}
