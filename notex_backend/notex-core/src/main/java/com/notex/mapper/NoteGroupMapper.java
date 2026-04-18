package com.notex.mapper;

import com.notex.domain.po.NoteGroup;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author author
 * @since 2026-04-01
 */
public interface NoteGroupMapper extends BaseMapper<NoteGroup> {
    int deleteGroupRecursively(@Param("groupId") Long groupId);

    List<Long> queryAllGroupInTargetGroup(@Param("groupId") Long groupId);
}
