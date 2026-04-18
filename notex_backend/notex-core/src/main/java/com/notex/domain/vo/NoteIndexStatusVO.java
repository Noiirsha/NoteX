package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 笔记索引状态VO
 * 用于返回笔记的索引状态信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "笔记索引状态")
public class NoteIndexStatusVO {

    /**
     * 笔记UUID
     * 笔记的唯一标识符
     */
    @Schema(description = "笔记UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String noteUuid;

    /**
     * 笔记标题
     * 笔记的标题信息
     */
    @Schema(description = "笔记标题", example = "我的学习笔记")
    private String title;

    /**
     * 更新时间
     * 笔记的最后更新时间
     */
    @Schema(description = "更新时间", example = "2024-01-15T10:30:00")
    private LocalDateTime updatedAt;

    /**
     * 是否已索引
     * 标识笔记是否已被向量索引
     */
    @Schema(description = "是否已索引", example = "true")
    private Boolean isIndexed;

}
