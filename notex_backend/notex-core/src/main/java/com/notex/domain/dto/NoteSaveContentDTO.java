package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 笔记内容保存请求DTO
 * 用于保存笔记的内容
 */
@Data
@Schema(description = "笔记内容保存请求")
public class NoteSaveContentDTO {

    /**
     * 笔记UUID
     * 要保存内容的笔记UUID，不能为空
     */
    @NotBlank(message = "笔记UUID不能为空")
    @Schema(description = "笔记UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uuid;

    /**
     * 笔记内容
     * 笔记的内容，不能为空
     */
    @NotBlank(message = "笔记内容不能为空")
    @Schema(description = "笔记内容", example = "# 我的笔记\n\n这是笔记的内容...")
    private String content;

}
