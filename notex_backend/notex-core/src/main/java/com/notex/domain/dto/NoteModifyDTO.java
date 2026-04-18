package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记修改请求DTO
 * 用于修改笔记的基本信息，如标题或所属分组
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "笔记修改请求")
public class NoteModifyDTO {

    /**
     * 笔记UUID
     * 要修改的笔记的UUID，不能为空
     */
    @NotBlank(message = "笔记UUID不能为空")
    @Schema(description = "笔记UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uuid;

    /**
     * 笔记标题
     * 笔记的新标题，可选
     */
    @Schema(description = "笔记标题", example = "修改后的标题")
    private String title;

    /**
     * 笔记分组ID
     * 笔记的新分组ID，可选
     */
    @Schema(description = "笔记分组ID", example = "2")
    private Long groupId;

}
