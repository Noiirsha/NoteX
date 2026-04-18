package com.notex.service;

import com.notex.domain.Response;
import com.notex.domain.dto.RAGRequestDTO;
import com.notex.domain.vo.RAGResultVO;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * RAG检索增强生成服务接口
 * 定义笔记向量化、关键词检索和向量删除的核心功能
 */
public interface IRAGService {

    void note2EmbeddingViaAsync(String noteUuid, Long userId);

    void note2Embedding(String noteUuid, Long userId);

    /**
     * 关键词检索
     * 根据关键词在向量数据库中检索相关的笔记内容
     *
     * @param dto RAG请求DTO，包含搜索内容
     * @return 检索结果列表
     */
    Response<List<RAGResultVO>> queryByKeywords(RAGRequestDTO dto);

    List<RAGResultVO> queryByKeywords(Long userId, RAGRequestDTO dto);

    /**
     * 删除笔记向量
     * 从向量数据库中删除指定笔记的向量数据
     *
     * @param dto RAG请求DTO，包含笔记UUID
     */
    void deleteNoteVector(RAGRequestDTO dto);

    void deleteUserVector();
}
