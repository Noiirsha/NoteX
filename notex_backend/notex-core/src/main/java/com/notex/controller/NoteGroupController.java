package com.notex.controller;

import com.notex.domain.Response;
import com.notex.domain.dto.NoteGroupCreateDTO;
import com.notex.domain.dto.NoteGroupModifyDTO;
import com.notex.domain.vo.NoteGroupCreateVO;
import com.notex.domain.vo.NoteGroupInformationVO;
import com.notex.domain.vo.NoteBasicInformationVO;
import com.notex.service.INoteGroupService;
import com.notex.service.INoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 笔记分组管理控制器
 * 提供笔记分组的创建、查询、修改、删除等功能
 */
@RestController
@RequestMapping("/note")
@RequiredArgsConstructor
@Tag(name = "笔记分组管理", description = "笔记分组的创建、查询、修改、删除等接口")
public class NoteGroupController {

    private final INoteGroupService noteGroupService;

    private final INoteService noteService;

    /**
     * 获取所有笔记分组
     *
     * @return 笔记分组信息列表
     */
    @GetMapping("/fetch_note_groups")
    @Operation(summary = "获取笔记分组", description = "获取用户的所有笔记分组")
    public Response<List<NoteGroupInformationVO>> fetchGroups(){
        return noteGroupService.fetchGroups();
    }

    /**
     * 获取指定分组下的笔记基本信息
     *
     * @param groupId 笔记分组ID
     * @return 笔记基本信息列表
     */
    @GetMapping("/fetch_notes_basic_information")
    @Operation(summary = "获取分组下的笔记", description = "获取指定分组下的所有笔记的基本信息")
    public Response<List<NoteBasicInformationVO>> fetchBasicInformation(
            @RequestParam(value = "group_id") Long groupId
    ) {
        return noteService.fetchBasicInfoByGroupId(groupId);
    }

    /**
     * 创建笔记分组
     *
     * @param noteGroupCreateDTO 笔记分组创建请求DTO
     * @return 笔记分组创建结果VO
     */
    @PostMapping("/create_note_group")
    @Operation(summary = "创建笔记分组", description = "创建一个新的笔记分组")
    public Response<NoteGroupCreateVO> createGroup(
            @RequestBody NoteGroupCreateDTO noteGroupCreateDTO
    ) {
        return noteGroupService.createGroup(noteGroupCreateDTO);
    }

    /**
     * 修改笔记分组
     *
     * @param noteGroupModifyDTO 笔记分组修改请求DTO
     * @return 空成功请求体
     */
    @PutMapping("/modify_note_group")
    @Operation(summary = "修改笔记分组", description = "修改笔记分组的名称或父分组")
    public Response<Void> modifyGroup(
            @RequestBody NoteGroupModifyDTO noteGroupModifyDTO
    ) {
        return noteGroupService.modifyGroup(noteGroupModifyDTO);
    }

    /**
     * 删除笔记分组
     *
     * @param groupId 笔记分组ID
     * @return 空成功请求体
     */
    @DeleteMapping("/delete_note_group")
    @Operation(summary = "删除笔记分组", description = "删除指定的笔记分组")
    public Response<Void> deleteGroup(@RequestParam(value = "group_id") Long groupId){
        return noteGroupService.deleteGroup(groupId);
    }



}
