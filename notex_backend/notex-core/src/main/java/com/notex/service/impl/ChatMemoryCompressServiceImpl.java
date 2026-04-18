package com.notex.service.impl;

import com.notex.context.AiContext;
import com.notex.domain.dto.ChatRequestDTO;
import com.notex.service.IChatMemoryCompressService;
import com.notex.utils.ChatClientUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天记忆压缩服务实现类
 * 实现会话记忆压缩的核心逻辑，通过Token数量和对话轮数触发压缩
 */
@Service
@Slf4j
public class ChatMemoryCompressServiceImpl implements IChatMemoryCompressService {

    @Resource
    private ChatClientUtils chatClientUtils;

    @Resource
    private ChatMemory chatMemory;

    @Resource
    private TokenCountEstimator tokenCountEstimator;

    /**
     * 压缩消息
     * 将指定的对话消息列表总结为简洁的摘要
     *
     * @param chatRequestDTO 聊天请求DTO
     * @param messages 需要压缩的消息列表
     * @return 压缩后的摘要内容
     */
    @Override
    public String compressMessage(ChatRequestDTO chatRequestDTO, List<Message> messages) {

        String requiredCompressMessages = messages.stream()
                .map(Message::getText)
                .collect(Collectors.joining("\n"));

        // 动态构建DTO
        ChatClient chatClient = chatClientUtils.getChatClientWithNoAdvisor();

        return chatClient.prompt()
                .system("请将以下对话总结为"
                        + AiContext.COMPRESS_SUMMARIZE_MAX_WORD_COUNT
                        + "字的纯文本关键摘要，在头部添加[COMPRESSED_CONTEXT],随后在后面最终输出摘要即可，不用输出额外的东西")
                .user(requiredCompressMessages)
                .call().content();
    }

    /**
     * 检查Token数量并压缩
     * 当Token数量达到阈值时自动触发压缩
     *
     * @param dto 聊天请求DTO
     */
    @Override
    public void checkTokenAndCompress(ChatRequestDTO dto) {

        // 获取ChatMemory
        String conversationId = dto.getConversationId();
        List<Message> messages = chatMemory.get(conversationId);

        // 获取上下文Context数量并统计Token
        String context = messages.stream().map(Message::getText).collect(Collectors.joining());
        int currentTokens = tokenCountEstimator.estimate(context);

        // 调试: 检查Token数量
        log.debug("当前会话Token: {}", currentTokens);

        // 检查是否需要激活压缩
        final int COMPRESS_TRIGGER_TOKEN_COUNT = (int) (AiContext.CONTEXT_WINDOW_MAX_TOKEN * AiContext.CONTEXT_WINDOW_COMPRESS_TRIGGER_RATIO);
        final int COMPRESS_TOKEN = (int) (AiContext.CONTEXT_WINDOW_MAX_TOKEN * AiContext.CONTEXT_WINDOW_COMPRESS_SIZE_RATIO);
        if (currentTokens >= COMPRESS_TRIGGER_TOKEN_COUNT) {
            log.debug("Token: 开始压缩");

            // 开始计算压缩量
            int tokenCount = 0;
            int compressSize = 0;
            for (Message msg : messages) {
                tokenCount += tokenCountEstimator.estimate(msg.getText());
                compressSize++;
                if (tokenCount >= COMPRESS_TOKEN) break;
            }

            log.debug("Token::PreCompress:: 压缩Token量: {}, 压缩的会话轮数: {}", tokenCount, compressSize);

            // 计算完成 执行压缩
            List<Message> toCompress = messages.subList(0, compressSize);
            String summary = compressMessage(dto, toCompress);

            // 清空后在头部插入总结内容，随后再把剩下的追加上去
            chatMemory.clear(conversationId);
            chatMemory.add(conversationId, List.of(new SystemMessage(summary)));
            chatMemory.add(conversationId, messages.subList(compressSize, messages.size()));
            log.debug("Token: 压缩完成 - 压缩内容为: {}", summary);

        }

    }

    /**
     * 检查对话轮数并压缩
     * 当对话轮数达到阈值时自动触发压缩
     *
     * @param dto 聊天请求DTO
     */
    @Override
    public void checkRoundAndCompress(ChatRequestDTO dto) {
        String conversationId = dto.getConversationId();
        List<Message> messages = chatMemory.get(conversationId);

        // 调试: 检查轮数
        log.debug("当前会话轮数: {}", messages.size());

        if (messages.size() >= AiContext.COMPRESS_TRIGGERED_ROUND) {
            log.debug("Round: 开始压缩");

            int compressSize = AiContext.COMPRESS_SIZE;
            // 检查第一条信息是否属于SystemMessage的范畴 如果是，则成对匹配。
            if (messages.getFirst().getMessageType() == MessageType.SYSTEM) {
                compressSize += 1;
            }

            List<Message> toCompress = messages.subList(0, compressSize);
            String summary = compressMessage(dto, toCompress);

            // 清空后在头部插入总结内容，随后再把剩下的追加上去
            chatMemory.clear(conversationId);
            chatMemory.add(conversationId, List.of(new SystemMessage(summary)));
            chatMemory.add(conversationId, messages.subList(compressSize, messages.size()));
            log.debug("Round: 压缩完成 - 压缩内容为: {}", summary);
        }
    }

}
