package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记创建请求DTO
 * 用于创建新笔记的请求参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "笔记创建请求")
public class NoteCreateDTO {

    /**
     * 笔记标题
     * 笔记的标题，不能为空
     */
    @NotBlank(message = "笔记标题不能为空")
    @Schema(description = "笔记标题", example = "我的第一篇笔记")
    private String title;

    /**
     * 笔记分组ID
     * 笔记所属的分组ID，不能为空
     */
    @NotNull(message = "笔记分组ID不能为空")
    @Schema(description = "笔记分组ID", example = "1")
    private Long groupId;

}
