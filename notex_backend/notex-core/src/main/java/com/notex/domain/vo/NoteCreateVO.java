package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记创建结果VO
 * 用于返回创建笔记后的结果信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "笔记创建结果")
public class NoteCreateVO {

    /**
     * 笔记ID
     * 数据库中的自增ID
     */
    @Schema(description = "笔记ID", example = "1")
    private Long id;

    /**
     * 笔记UUID
     * 笔记的唯一标识符
     */
    @Schema(description = "笔记UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String noteUuid;

}
