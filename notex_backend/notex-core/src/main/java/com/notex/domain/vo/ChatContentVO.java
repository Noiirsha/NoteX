package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天内容VO
 * 用于返回单条聊天消息的内容
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "聊天内容")
public class ChatContentVO {

    /**
     * 消息内容
     * 聊天消息的文本内容
     */
    @Schema(description = "消息内容", example = "你好，我是AI助手")
    private String content;

    /**
     * 消息类型
     * 标识消息发送者，值为 ASSISTANT（AI）或 USER（用户）
     */
    @Schema(description = "消息类型", example = "ASSISTANT")
    private String type; // ASSISTANT / USER

}
