package com.notex.controller;

import com.notex.domain.Response;
import com.notex.domain.dto.RAGRequestDTO;
import com.notex.domain.vo.RAGResultVO;
import com.notex.service.IRAGService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RAG检索增强生成控制器
 * 提供笔记向量化存储、关键词检索和向量删除功能
 */
@RestController
@RequestMapping("/ai/rag")
@Tag(name = "RAG检索", description = "笔记向量化、关键词检索和向量删除的接口")
public class RAGController {

    @Resource
    private IRAGService ragService;

    /**
     * 关键词检索
     * 根据关键词在向量数据库中检索相关的笔记内容
     *
     * @param dto RAG请求DTO，包含搜索内容
     * @return 检索结果列表
     */
    @PostMapping("/query_by_keyword")
    @Operation(summary = "关键词检索", description = "根据关键词检索相关笔记内容")
    public Response<List<RAGResultVO>> queryByKeyword(@RequestBody RAGRequestDTO dto) {
        return ragService.queryByKeywords(dto);
    }

}
