package com.notex.service;

import com.notex.domain.Response;
import com.notex.domain.dto.NoteCreateDTO;
import com.notex.domain.dto.NoteModifyDTO;
import com.notex.domain.dto.NoteSaveContentDTO;
import com.notex.domain.po.Note;
import com.baomidou.mybatisplus.extension.service.IService;
import com.notex.domain.vo.*;

import java.util.List;

/**
 * 笔记服务接口
 * 定义笔记的创建、查询、修改、删除等核心功能
 */
public interface INoteService extends IService<Note> {

    /**
     * 获取分组下的笔记基本信息
     * 获取指定分组下所有笔记的基本信息列表
     *
     * @param groupId 笔记分组ID
     * @return 笔记基本信息列表
     */
    Response<List<NoteBasicInformationVO>> fetchBasicInfoByGroupId(Long groupId);

    /**
     * 获取笔记详细信息
     * 根据笔记UUID获取笔记的完整信息
     *
     * @param noteUuid 笔记UUID
     * @return 笔记详细信息
     */
    Response<NoteInformationVO> fetchNote(String noteUuid);

    /**
     * 创建笔记
     * 创建一个新的笔记
     *
     * @param noteCreateDTO 笔记创建请求DTO
     * @return 笔记创建结果
     */
    Response<NoteCreateVO> createNote(NoteCreateDTO noteCreateDTO);

    /**
     * 删除笔记
     * 根据笔记UUID删除笔记
     *
     * @param noteUuid 笔记UUID
     * @return 操作结果
     */
    Response<Void> deleteNote(String noteUuid);

    /**
     * 修改笔记基本信息
     * 修改笔记的标题或所属分组
     *
     * @param noteModifyDTO 笔记修改请求DTO
     * @return 操作结果
     */
    Response<Void> modifyNote(NoteModifyDTO noteModifyDTO);

    /**
     * 保存笔记内容
     * 保存笔记的内容
     *
     * @param noteSaveContentDTO 笔记内容保存请求DTO
     * @return 操作结果
     */
    Response<Void> saveNoteContent(NoteSaveContentDTO noteSaveContentDTO);

    /**
     * 获取最近访问的笔记
     * 获取用户最近访问的笔记列表
     *
     * @return 最近访问笔记列表
     */
    Response<List<NoteRecentAccessVO>> fetchRecentAccessNotes();

    /**
     * 创建笔记（工具调用）
     * 供AI工具调用创建笔记
     *
     * @param userId 用户ID
     * @param groupId 分组ID
     * @param title 笔记标题
     * @param content 笔记内容
     */
    void createNote(Long userId, Long groupId, String title, String content);

    /**
     * 获取笔记索引状态
     * 获取所有笔记的索引状态列表
     *
     * @return 笔记索引状态列表
     */
    Response<List<NoteIndexStatusVO>> fetchIndexStatus();

    /**
     * 笔记向量化
     * 将指定笔记内容进行向量化处理
     *
     * @param noteUuid 笔记UUID
     * @return 操作结果
     */
    Response<Void> noteToEmbedded(String noteUuid);
}
