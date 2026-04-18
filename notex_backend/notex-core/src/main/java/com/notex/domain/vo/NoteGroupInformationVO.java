package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记分组信息VO
 * 用于返回笔记分组的基本信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "笔记分组信息")
public class NoteGroupInformationVO {
    /**
     * 分组ID
     * 数据库中的自增ID
     */
    @Schema(description = "分组ID", example = "1")
    private Long id;

    /**
     * 父分组ID
     * 父分组的ID，如果为null则表示这是顶层分组
     */
    @Schema(description = "父分组ID", example = "0")
    private Long parentGroupId;

    /**
     * 分组名称
     * 笔记分组的名称
     */
    @Schema(description = "分组名称", example = "工作笔记")
    private String groupName;
}
