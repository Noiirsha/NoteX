package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天生成标题请求DTO
 * 用于根据聊天内容自动生成会话标题
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "聊天生成标题请求")
public class ChatGenerateTitleDTO {

    /**
     * 会话ID
     * 需要生成标题的会话ID
     */
    @Schema(description = "会话ID", example = "conv_123")
    private String conversationId;

    /**
     * 第一条消息内容
     * 会话的第一条用户消息，用于生成标题
     */
    @Schema(description = "第一条消息内容", example = "你好，我想了解Java")
    private String firstContent;

}
