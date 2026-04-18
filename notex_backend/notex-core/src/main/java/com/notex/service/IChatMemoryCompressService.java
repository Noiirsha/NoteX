package com.notex.service;

import com.notex.domain.dto.ChatRequestDTO;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 聊天记忆压缩服务接口
 * 定义会话记忆压缩的核心功能，通过Token数量和对话轮数触发压缩
 */
public interface IChatMemoryCompressService {

    /**
     * 压缩消息
     * 将指定的对话消息列表总结为简洁的摘要
     *
     * @param chatRequestDTO 聊天请求DTO
     * @param messages 需要压缩的消息列表
     * @return 压缩后的摘要内容
     */
    String compressMessage(ChatRequestDTO chatRequestDTO, List<Message> messages);

    /**
     * 检查对话轮数并压缩
     * 当对话轮数达到阈值时自动触发压缩
     *
     * @param dto 聊天请求DTO
     */
    void checkRoundAndCompress(ChatRequestDTO dto);

    /**
     * 检查Token数量并压缩
     * 当Token数量达到阈值时自动触发压缩
     *
     * @param dto 聊天请求DTO
     */
    void checkTokenAndCompress(ChatRequestDTO dto);

}
