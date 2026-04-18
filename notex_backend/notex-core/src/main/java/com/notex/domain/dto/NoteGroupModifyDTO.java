package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记分组修改请求DTO
 * 用于修改笔记分组的基本信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "笔记分组修改请求")
public class NoteGroupModifyDTO {

    /**
     * 分组ID
     * 要修改的笔记分组ID，不能为空
     */
    @NotNull(message = "分组ID不能为空")
    @Schema(description = "分组ID", example = "1")
    private Long id;

    /**
     * 分组名称
     * 笔记分组的新名称，可选
     */
    @Schema(description = "分组名称", example = "修改后的分组名")
    private String groupName;

    /**
     * 父分组ID
     * 笔记分组的新父分组ID，可选
     */
    @Schema(description = "父分组ID", example = "2")
    private Long parentGroupId;

}
