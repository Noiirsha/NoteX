package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG请求DTO
 * 用于检索增强生成的请求参数，包括笔记向量化、关键词检索和向量删除
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "RAG请求")
public class RAGRequestDTO {

    /**
     * 笔记详情
     * 用于笔记向量化操作时的笔记信息
     */
    @Schema(description = "笔记详情")
    private NoteDetail noteDetail = null;

    /**
     * 搜索请求
     * 用于关键词检索操作时的搜索内容
     */
    @Schema(description = "搜索请求")
    private SearchRequest searchRequest = null;

    /**
     * 笔记详情内部类
     * 包含笔记的基本信息和内容
     */
    @Data
    @Schema(description = "笔记详情")
    public static class NoteDetail {
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
        private String noteTitle;

        /**
         * 笔记内容
         * 笔记的完整内容，支持Markdown格式
         */
        @Schema(description = "笔记内容", example = "# 标题\n这是笔记内容")
        private String content;
    }

    /**
     * 搜索请求内部类
     * 包含检索的关键词内容
     */
    @Data
    @Schema(description = "搜索请求")
    public static class SearchRequest {
        /**
         * 搜索内容
         * 用户输入的检索关键词或问题
         */
        @Schema(description = "搜索内容", example = "如何学习Java")
        private String searchContent;
    }

}
