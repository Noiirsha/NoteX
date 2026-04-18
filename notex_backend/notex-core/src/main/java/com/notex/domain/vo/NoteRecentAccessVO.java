package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 最近访问笔记视图对象
 * 用于返回用户最近访问的笔记信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "最近访问笔记信息")
public class NoteRecentAccessVO {

    /**
     * 笔记UUID
     */
    @Schema(description = "笔记UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String noteUuid;

    /**
     * 笔记标题
     */
    @Schema(description = "笔记标题", example = "我的第一篇笔记")
    private String noteTitle;
}
