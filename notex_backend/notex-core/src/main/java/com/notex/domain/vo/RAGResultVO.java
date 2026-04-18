package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * RAG检索结果VO
 * 用于返回向量检索的匹配结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain=true)
@Schema(description = "RAG检索结果")
public class RAGResultVO {

    /**
     * 笔记UUID
     * 匹配到的笔记唯一标识符
     */
    @Schema(description = "笔记UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String noteUuid;

    /**
     * 笔记标题
     * 匹配到的笔记标题
     */
    @Schema(description = "笔记标题", example = "我的学习笔记")
    private String noteTitle;

    /**
     * 内容匹配率
     * 检索结果与查询的相似度分数，范围0-1
     */
    @Schema(description = "内容匹配率", example = "0.85")
    private Double contentMatchRate;

    /**
     * 内容片段
     * 匹配到的笔记内容片段
     */
    @Schema(description = "内容片段", example = "这是匹配到的笔记内容...")
    private String content;

}
