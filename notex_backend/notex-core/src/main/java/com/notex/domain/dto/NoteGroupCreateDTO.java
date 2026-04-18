package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记分组创建请求DTO
 * 用于创建新的笔记分组
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "笔记分组创建请求")
public class NoteGroupCreateDTO {

    /**
     * 分组名称
     * 笔记分组的名称，不能为空
     */
    @NotBlank(message = "分组名称不能为空")
    @Schema(description = "分组名称", example = "工作笔记")
    private String groupName;

    /**
     * 父分组ID
     * 父分组的ID，如果为null则表示创建顶层分组
     */
    @Schema(description = "父分组ID", example = "1")
    private Long parentGroupId;

}
