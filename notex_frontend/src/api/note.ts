import request from '@/utils/request'
import type {
  NoteGroup,
  CreateNoteGroupRequest,
  CreateNoteRequest,
  NoteBasicInfo,
  NoteDetail,
  ModifyNoteRequest,
  ModifyNoteGroupRequest,
  SaveNoteContentRequest,
  NoteRecentAccessVO,
  NoteIndexStatus
} from '@/types/api'

/**
 * 获取所有笔记分组
 */
export const fetchNoteGroups = async (): Promise<NoteGroup[]> => {
  return request.get('/note/fetch_note_groups')
}

/**
 * 创建笔记分组
 */
export const createNoteGroup = async (data: CreateNoteGroupRequest): Promise<{ id: number }> => {
  return request.post('/note/create_note_group', data)
}

/**
 * 创建笔记
 */
export const createNote = async (data: CreateNoteRequest): Promise<{ id: number; noteUuid: string }> => {
  return request.post('/note/create_note', data)
}

/**
 * 获取分组下的笔记列表
 */
export const fetchNotesByGroup = async (group_id: number): Promise<NoteBasicInfo[]> => {
  return request.get('/note/fetch_notes_basic_information', { params: { group_id } })
}

/**
 * 获取笔记详情
 */
export const fetchNoteDetail = async (note_uuid: string): Promise<NoteDetail> => {
  return request.get('/note/fetch_note', { params: { note_uuid } })
}

/**
 * 修改笔记
 */
export const modifyNote = async (data: ModifyNoteRequest): Promise<void> => {
  return request.put('/note/modify_note', data)
}

/**
 * 修改笔记分组
 */
export const modifyNoteGroup = async (data: ModifyNoteGroupRequest): Promise<void> => {
  return request.put('/note/modify_note_group', data)
}

/**
 * 保存笔记内容
 */
export const saveNoteContent = async (data: SaveNoteContentRequest): Promise<void> => {
  return request.put('/note/save_note_content', data)
}

/**
 * 删除笔记
 */
export const deleteNote = async (note_uuid: string): Promise<void> => {
  return request.delete('/note/delete_note', { params: { note_uuid } })
}

/**
 * 删除笔记分组
 */
export const deleteNoteGroup = async (id: number): Promise<void> => {
  return request.delete('/note/delete_note_group', { params: { group_id: id } })
}

/**
 * 获取最近访问的笔记
 */
export const fetchRecentAccessNotes = async (): Promise<NoteRecentAccessVO[]> => {
  return request.get('/note/fetch_recent_access_notes')
}

/**
 * 获取笔记索引状态
 */
export const fetchIndexStatus = async (): Promise<NoteIndexStatus[]> => {
  return request.get('/note/fetch_index_status')
}

/**
 * 触发笔记向量化
 */
export const noteToEmbedded = async (note_uuid: string): Promise<void> => {
  return request.post('/note/note_to_embedded', null, { params: { note_uuid } })
}
