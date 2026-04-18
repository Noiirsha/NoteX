package com.notex.service;

import com.notex.domain.Response;
import com.notex.domain.dto.ChatGenerateTitleDTO;
import com.notex.domain.dto.ChatRequestDTO;
import reactor.core.publisher.Flux;

/**
 * AI聊天服务接口
 * 定义与AI助手进行对话的核心功能
 */
public interface IChatService {

    /**
     * 执行AI聊天
     * 向AI助手发送消息并获取流式响应
     *
     * @param chatRequestDTO 聊天请求DTO，包含用户消息和配置
     * @return AI的流式响应内容
     */
    Flux<String> doChat(ChatRequestDTO chatRequestDTO);

    /**
     * 生成会话标题
     * 根据聊天内容自动生成会话标题
     *
     * @param chatGenerateTitleDTO 生成标题请求DTO
     * @return 生成的会话标题
     */
    Response<String> generateTitle(ChatGenerateTitleDTO chatGenerateTitleDTO);

    /**
     * 删除会话
     * 删除指定的聊天会话及相关历史记录
     *
     * @param conversationId 会话ID
     * @return 操作结果
     */
    Response<Void> deleteConversationById(String conversationId);
}
