package com.notex.service;

import com.notex.domain.Response;
import com.notex.domain.dto.NoteGroupCreateDTO;
import com.notex.domain.dto.NoteGroupModifyDTO;
import com.notex.domain.po.NoteGroup;
import com.baomidou.mybatisplus.extension.service.IService;
import com.notex.domain.vo.NoteGroupCreateVO;
import com.notex.domain.vo.NoteGroupInformationVO;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author author
 * @since 2026-04-01
 */
public interface INoteGroupService extends IService<NoteGroup> {

    Response<List<NoteGroupInformationVO>> fetchGroups();

    Response<NoteGroupCreateVO> createGroup(NoteGroupCreateDTO noteGroupCreateDTO);

    Response<Void> modifyGroup(NoteGroupModifyDTO noteGroupModifyDTO);

    Response<Void> deleteGroup(Long groupId);

    boolean isGroupExist(Long groupId);
}
