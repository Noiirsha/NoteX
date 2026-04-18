package com.notex.controller;

import com.notex.domain.Response;
import com.notex.domain.dto.NoteCreateDTO;
import com.notex.domain.dto.NoteModifyDTO;
import com.notex.domain.dto.NoteSaveContentDTO;
import com.notex.domain.vo.NoteCreateVO;
import com.notex.domain.vo.NoteIndexStatusVO;
import com.notex.domain.vo.NoteInformationVO;
import com.notex.domain.vo.NoteRecentAccessVO;
import com.notex.service.INoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 笔记管理控制器
 * 提供笔记的创建、查询、修改、删除以及内容保存等功能
 */
@RestController
@RequestMapping("/note")
@RequiredArgsConstructor
@Tag(name = "笔记管理", description = "笔记的创建、查询、修改、删除等接口")
public class NoteController {

    private final INoteService noteService;

    /**
     * 获取笔记信息
     *
     * @param noteUuid 笔记UUID
     * @return 笔记信息VO
     */
    @GetMapping("/fetch_note")
    @Operation(summary = "获取笔记", description = "根据笔记UUID获取笔记的详细信息")
    public Response<NoteInformationVO> fetchNote(@RequestParam(value = "note_uuid") String noteUuid) {
        return noteService.fetchNote(noteUuid);
    }

    /**
     * 创建新笔记
     *
     * @param noteCreateDTO 笔记创建请求DTO
     * @return 笔记创建结果VO，包含笔记ID和UUID
     */
    @PostMapping("/create_note")
    @Operation(summary = "创建笔记", description = "创建一个新的笔记")
    public Response<NoteCreateVO> createNote(
            @RequestBody NoteCreateDTO noteCreateDTO
    ) {
        return noteService.createNote(noteCreateDTO);
    }

    /**
     * 删除笔记
     *
     * @param noteUuid 笔记UUID
     * @return 空成功请求体
     */
    @DeleteMapping("/delete_note")
    @Operation(summary = "删除笔记", description = "根据笔记UUID删除笔记")
    public Response<Void> deleteNote(@RequestParam(value = "note_uuid") String noteUuid) {
        return noteService.deleteNote(noteUuid);
    }

    /**
     * 修改笔记基本信息
     *
     * @param noteModifyDTO 笔记修改请求DTO
     * @return 空成功请求体
     */
    @PutMapping("/modify_note")
    @Operation(summary = "修改笔记", description = "修改笔记的标题或所属分组")
    public Response<Void> modifyNote(@RequestBody NoteModifyDTO noteModifyDTO) {
        return noteService.modifyNote(noteModifyDTO);
    }

    /**
     * 保存笔记内容
     *
     * @param noteSaveContentDTO 笔记内容保存请求DTO
     * @return 空成功请求体
     */
    @PutMapping("/save_note_content")
    @Operation(summary = "保存笔记内容", description = "保存笔记的内容")
    public Response<Void> saveNoteContent(
            @RequestBody NoteSaveContentDTO noteSaveContentDTO
    ) {
        return noteService.saveNoteContent(noteSaveContentDTO);
    }

    /**
     * 获取最近访问的笔记列表
     * 从Redis缓存中获取用户最近访问的笔记
     *
     * @return 最近访问笔记列表VO
     */
    @GetMapping("/fetch_recent_access_notes")
    @Operation(summary = "获取最近访问笔记", description = "获取用户最近访问的笔记列表")
    public Response<List<NoteRecentAccessVO>> fetchRecentAccessNotes(){
        return noteService.fetchRecentAccessNotes();
    }

    /**
     * 获取笔记索引状态
     * 获取所有笔记的索引状态列表
     *
     * @return 笔记索引状态列表
     */
    @GetMapping("/fetch_index_status")
    @Operation(summary = "获取笔记索引状态", description = "获取所有笔记的索引状态列表")
    public Response<List<NoteIndexStatusVO>> fetchIndexStatus(){
        return noteService.fetchIndexStatus();
    }

    /**
     * 笔记向量化
     * 将指定笔记内容进行向量化处理
     *
     * @param noteUuid 笔记UUID
     * @return 空成功请求体
     */
    @PostMapping("/note_to_embedded")
    @Operation(summary = "笔记向量化", description = "将指定笔记内容进行向量化处理")
    public Response<Void> noteToEmbedded(
            @RequestParam(value = "note_uuid") String noteUuid
    ) {
        return noteService.noteToEmbedded(noteUuid);
    }

}
