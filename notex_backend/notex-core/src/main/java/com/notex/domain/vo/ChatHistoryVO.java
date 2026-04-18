package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天会话历史VO
 * 用于返回用户的聊天会话列表信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "聊天会话历史")
public class ChatHistoryVO {

    /**
     * 会话ID
     * 聊天会话的唯一标识符
     */
    @Schema(description = "会话ID", example = "conv_123")
    private String conversationId;

    /**
     * 会话标题
     * 聊天会话的标题，通常由AI自动生成
     */
    @Schema(description = "会话标题", example = "关于Java的讨论")
    private String conversationTitle;

    /**
     * 最后访问时间
     * 该会话的最后访问时间
     */
    @Schema(description = "最后访问时间", example = "2024-01-15T10:30:00")
    private LocalDateTime lastAccessTime;

}
