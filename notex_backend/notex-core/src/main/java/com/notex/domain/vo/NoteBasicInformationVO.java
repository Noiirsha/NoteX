package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 笔记基本信息VO
 * 用于返回笔记的基本信息，不包含内容
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "笔记基本信息")
public class NoteBasicInformationVO {
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

    /**
     * 笔记分组ID
     * 笔记所属的分组ID
     */
    @Schema(description = "笔记分组ID", example = "1")
    private Long groupId;

    /**
     * 笔记标题
     * 笔记的标题
     */
    @Schema(description = "笔记标题", example = "我的笔记")
    private String title;

    /**
     * 创建时间
     * 笔记的创建时间
     */
    @Schema(description = "创建时间", example = "2024-01-01T00:00:00")
    private LocalDateTime createdAt;

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
