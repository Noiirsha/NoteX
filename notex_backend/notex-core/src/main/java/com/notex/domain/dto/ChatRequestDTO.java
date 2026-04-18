package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * 聊天请求DTO
 * 用于发起AI聊天的请求参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "聊天请求")
public class ChatRequestDTO {

    /**
     * 用户昵称
     * 用于AI问候时使用
     */
    @Schema(description = "用户昵称", example = "张三")
    private String userNickname;

    /**
     * 会话ID
     * 标识当前聊天会话，用于记忆上下文
     */
    @NotNull(message = "会话ID不能为空")
    @Schema(description = "会话ID", example = "conv_123")
    private String conversationId;

    /**
     * 用户消息
     * 用户发送给AI的消息内容
     */
    @Schema(description = "用户消息", example = "你好，请介绍一下自己")
    private String message;

    private WorkspaceDetail workspaceDetail;

    @Data
    public static class WorkspaceDetail {

        private String noteUuid;

        private Long groupId;

        private String noteContent;
    }

}
