package com.notex.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.notex.advisor.DualWriteAdvisor;
import com.notex.advisor.MyToolCallAdvisor;
import com.notex.context.AiContext;
import com.notex.context.RedisContext;
import com.notex.domain.dto.AiModelDTO;
import com.notex.domain.dto.ChatRequestDTO;
import com.notex.domain.po.UserAiModels;
import com.notex.domain.vo.UserAiModelsVO;
import com.notex.exception.BusinessException;
import com.notex.service.IUserAiModelsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * ChatClient工具类
 * 提供动态创建和缓存ChatClient对象的功能
 */
@Component
@Slf4j
public class ChatClientUtils {

    /**
     * Chat Memory管理: Spring Ai内置, 个人项目够用，日后升级到2.0之后会有Redis 本项目暂时用不上。
     */
    @Resource
    private JdbcChatMemoryRepository chatMemoryRepository;

    /**
     * 双写/压缩Advisor
     */
    @Resource
    private DualWriteAdvisor dualWriteAdvisor;

    /**
     * MCP Tool Callback 提供者以及Advisor
     */
    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    @Resource
    private ToolCallingManager toolCallingManager;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    /**
     * ChatClient缓存
     * 使用Caffeine缓存ChatClient对象，避免重复创建
     */
    private final Cache<String, ChatClient> clients = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(AiContext.MAX_STORAGE_CHAT_CLIENTS)
            .build();

    /**
     * 动态获取ChatClient对象
     * 根据配置参数创建或从缓存中获取ChatClient
     *
     * @return ChatClient对象
     */
    public ChatClient getChatClient() {

        // 从当前线程中获取对应的userId
        Long threadUserId = UserIdContextHolder.get();

        // 从Redis中获取对应的模型配置
        AiModelDTO model = fetchUserChatModelByUserId(threadUserId);
        String cacheKey = "CHAT_CLIENT:" + threadUserId;

        // 从缓存中获取对应的对象
        return clients.get(cacheKey, key -> {

            // 构建API
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(model.getUrlBase())
                    .apiKey(model.getApiKey())
                    .build();

            // 构建Chat Model
            OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder().model(model.getModelName()).build())
                    .build();

            // 构建Chat Memory
            MessageWindowChatMemory messageWindowChatMemory = MessageWindowChatMemory.builder()
                    .chatMemoryRepository(chatMemoryRepository)
                    .maxMessages(AiContext.MAX_MESSAGE_ROUND)
                    .build();

            // 最终构建Chat Client;
            return ChatClient
                    .builder(openAiChatModel)
                    .defaultToolCallbacks(toolCallbackProvider.getToolCallbacks())
                    .defaultAdvisors(
                            new MyToolCallAdvisor(toolCallingManager,0),
                            dualWriteAdvisor,
                            MessageChatMemoryAdvisor.builder(messageWindowChatMemory).build()
                    ).build();
        });
    }

    /**
     * 动态获取ChatClient对象 - 没有Advisor
     * 创建不带Advisor的ChatClient，用于压缩等特殊场景
     *
     * @return 不带Advisor的ChatClient对象
     */
    public ChatClient getChatClientWithNoAdvisor() {
        // 从当前线程中获取对应的userId
        Long threadUserId = UserIdContextHolder.get();

        // 从Redis中获取对应的模型配置
        AiModelDTO model = fetchUserChatModelByUserId(threadUserId);
        String cacheKey = "CHAT_CLIENT_NO_ADVISOR:" + threadUserId;

        // 从缓存中获取对应的对象
        return clients.get(cacheKey, key -> {

            // 构建API
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(model.getUrlBase())
                    .apiKey(model.getApiKey())
                    .build();

            // 构建Chat Model
            OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder().model(model.getModelName()).build())
                    .build();

            // 最终构建Chat Client;
            return ChatClient
                    .builder(openAiChatModel)
                    .build();
        });
    }

    /**
     * 无效化ChatClient缓存对象
     */
    public void invalidateChatClient() {
        Long threadUserId = UserIdContextHolder.get();
        log.debug("ChatClient::InvalidCache::UserID - {}",  threadUserId);
        String cacheKey = "CHAT_CLIENT:" + threadUserId;
        String cacheKeyNoAdvisor = "CHAT_CLIENT_NO_ADVISOR:" + threadUserId;
        clients.invalidate(cacheKey);
        clients.invalidate(cacheKeyNoAdvisor);
    }

    /**
     * 从数据库获取用户聊天模型配置
     *
     * @param threadUserId 用户ID
     * @return AI模型配置DTO
     */
    private AiModelDTO getUserModelsFromDB(Long threadUserId) {
        UserAiModels one = Db.lambdaQuery(UserAiModels.class)
                .eq(UserAiModels::getUserId, threadUserId)
                .select(
                        UserAiModels::getStandardModelBase, UserAiModels::getStandardModelApiKey, UserAiModels::getStandardModelModelName
                ).one();

        if (one == null) {
            throw new BusinessException("未设置Chat模型");
        }

        AiModelDTO aiModelDTO = new AiModelDTO();
        aiModelDTO.setUrlBase(one.getStandardModelBase());
        aiModelDTO.setApiKey(one.getStandardModelApiKey());
        aiModelDTO.setModelName(one.getStandardModelModelName());

        return aiModelDTO;
    }

    /**
     * 根据用户ID获取聊天模型配置
     * 优先从Redis缓存中获取，缓存未命中则从数据库获取
     *
     * @param userId 用户ID
     * @return AI模型配置DTO
     */
    private AiModelDTO fetchUserChatModelByUserId(Long userId) {

        String key = RedisContext.CACHE_USER_AI_MODEL_CONFIGURATIONS_PREFIX + userId;
        String rawJson = redisTemplate.opsForValue().get(key);
        AiModelDTO model;

        if (StrUtil.isBlank(rawJson)) {
            // 缓存未设置，重新设置缓存
            model = getUserModelsFromDB(userId);
        } else {
            UserAiModelsVO bean = JSONUtil.toBean(rawJson, UserAiModelsVO.class);
            model = new AiModelDTO();
            model.setUrlBase(bean.getStandardModelBase());
            model.setApiKey(bean.getStandardModelApiKey());
            model.setModelName(bean.getStandardModelModelName());
        }

        // 检查ChatModel是否是空的
        if (
                StrUtil.isBlank(model.getUrlBase()) ||
                        StrUtil.isBlank(model.getApiKey()) ||
                        StrUtil.isBlank(model.getModelName())
        ) {
            throw new BusinessException("未设置Chat模型");
        }

        return model;
    }

}
