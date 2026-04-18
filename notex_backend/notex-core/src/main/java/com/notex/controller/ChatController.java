package com.notex.controller;

import com.notex.domain.Response;
import com.notex.domain.dto.ChatGenerateTitleDTO;
import com.notex.domain.dto.ChatRequestDTO;
import com.notex.domain.vo.ChatContentVO;
import com.notex.service.IChatHistoryService;
import com.notex.service.IChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI聊天控制器
 * 提供与AI助手进行对话的功能
 */
@RestController
@RequestMapping("/ai/chat")
@Tag(name = "AI聊天", description = "与AI助手进行对话的接口")
public class ChatController {

    @Resource
    private IChatService chatService;

    @Resource
    private IChatHistoryService chatHistoryService;

    /**
     * 发起AI聊天
     * 向AI助手发送消息并获取流式响应
     *
     * @param chatRequestDTO 聊天请求DTO
     * @return AI的流式响应内容
     */
    @PostMapping(
            value = "/begin_chat",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8"
    )
    @Operation(summary = "发起AI聊天", description = "向AI助手发送消息并获取流式响应")
    public Flux<String> doChat(@RequestBody ChatRequestDTO chatRequestDTO) {
        return chatService.doChat(chatRequestDTO);
    }

    /**
     * 生成会话标题
     * 根据聊天内容自动生成会话标题
     *
     * @param chatGenerateTitleDTO 生成标题请求DTO
     * @return 生成的会话标题
     */
    @PostMapping("/generate_title")
    @Operation(summary = "生成会话标题", description = "根据聊天内容自动生成会话标题")
    public Response<String> generateTitle(@RequestBody ChatGenerateTitleDTO chatGenerateTitleDTO){
        return chatService.generateTitle(chatGenerateTitleDTO);
    }

    /**
     * 获取会话历史
     * 获取指定会话的所有聊天消息
     *
     * @param conversationId 会话ID
     * @return 聊天内容列表
     */
    @GetMapping("/fetch_conversation")
    @Operation(summary = "获取会话历史", description = "获取指定会话的所有聊天消息")
    public Response<List<ChatContentVO>>  fetchConversation(
            @RequestParam(value = "conversation_id") String conversationId
    ){
        return chatHistoryService.fetchConversation(conversationId);
    }

    /**
     * 删除会话
     * 删除指定的聊天会话
     *
     * @param conversationId 会话ID
     * @return 空成功请求体
     */
    @DeleteMapping("/delete_conversation")
    @Operation(summary = "删除会话", description = "删除指定的聊天会话")
    public Response<Void> deleteConversation(@RequestParam("conversation_id") String conversationId){
        return chatService.deleteConversationById(conversationId);
    }


}
