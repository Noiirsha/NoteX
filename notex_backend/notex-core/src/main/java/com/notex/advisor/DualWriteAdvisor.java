package com.notex.advisor;

import com.notex.context.AiContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 双写Advisor
 * 在AI聊天过程中同时将消息写入Spring AI的ChatMemory和自定义的chat_history表
 */
@Component
@Slf4j
public class DualWriteAdvisor implements StreamAdvisor {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 获取Advisor名称
     *
     * @return Advisor的唯一标识符
     */
    @Override
    public String getName() {
        return AiContext.DUAL_WRITE_ADVISOR;
    }

    /**
     * 获取Advisor优先级
     *
     * @return 优先级数值，数值越小优先级越高
     */
    @Override
    public int getOrder() {
        return AiContext.DUAL_WRITE_ADVISOR_ORDER;
    }

    /**
     * 处理流式响应
     * 监听AI的流式响应，拼接完整内容并在流结束时写入数据库
     *
     * @param request 聊天请求
     * @param chain Advisor链
     * @return 流式响应
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {

        // 获取会话id
        String conversationId = request.context().get(ChatMemory.CONVERSATION_ID).toString();

        // 1. 写完整记录表
        appendUserContext(conversationId, request);

        // 用于拼接 AI 的流式回复
        StringBuilder assistantContentBuilder = new StringBuilder();

        return chain.nextStream(request)
                // 监听每一个流式数据块
                .doOnNext(response -> {
                    if (response != null && response.chatResponse() != null) {
                        // 返回对象
                        AssistantMessage output = response.chatResponse().getResult().getOutput();

                        String textBlock = output.getText();
                        if (textBlock != null) {
                            assistantContentBuilder.append(textBlock); // 持续拼接
                        }
                    }
                })
                // 当流完全结束时触发
                .doOnComplete(() -> {
                    // 获取完整的 AI 回复
                    String fullAssistantMessage = assistantContentBuilder.toString();

                    // 写完整记录表 (保存 AI 的完整回复)
                    appendAssistantContext(conversationId, fullAssistantMessage);

                })
                // 异常处理兜底（可选）
                .doOnError(throwable -> {
                    log.error("AI 响应流异常，当前已接收内容: {}", assistantContentBuilder.toString(), throwable);
                    // 获取完整的 AI 回复
                    String fullAssistantMessage = assistantContentBuilder.toString() + "\n<notex_response_error reason=\"%s\" />".formatted(throwable.getMessage());

                    // 写完整记录表 (保存 AI 的完整回复)
                    appendAssistantContext(conversationId, fullAssistantMessage);
                });
    }

    /**
     * 追加用户消息到数据库
     *
     * @param conversationId 会话ID
     * @param request 聊天请求
     */
    private void appendUserContext(String conversationId, ChatClientRequest request) {
        String content = request.prompt().getUserMessage().getText();
        if (content.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO chat_history (conversation_id, content, type) VALUES (?, ?, 'USER')";
        try {
            jdbcTemplate.update(sql, conversationId, content);
        } catch (Exception e) {
            log.error("写入用户消息失败 - conversationId: {}, error: {}", conversationId, e.getMessage(), e);
        }
    }

    /**
     * 追加助手消息到数据库
     *
     * @param conversationId 会话ID
     * @param message AI回复的完整消息
     */
    private void appendAssistantContext(String conversationId, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO chat_history (conversation_id, content, type) VALUES (?, ?, 'ASSISTANT')";
        try {
            jdbcTemplate.update(sql, conversationId, message);
        } catch (Exception e) {
            log.error("写入助手消息失败 - conversationId: {}, error: {}", conversationId, e.getMessage(), e);
        }
    }

}
