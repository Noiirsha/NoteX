package com.notex.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.notex.context.RAGContext;
import com.notex.context.RedisContext;
import com.notex.domain.dto.AiModelDTO;
import com.notex.domain.dto.RAGRequestDTO;
import com.notex.domain.po.UserAiModels;
import com.notex.domain.vo.UserAiModelsVO;
import com.notex.exception.BusinessException;
import com.notex.service.IUserAiModelsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * VectorStore工具类
 * 提供动态创建和缓存VectorStore对象的功能
 */
@Component
@Slf4j
public class VectorStoreUtils {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    /**
     * VectorStore缓存
     * 使用Caffeine缓存VectorStore对象，避免重复创建
     */
    private final Cache<String, VectorStore> vectorStoreCache = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(RAGContext.MAX_STORAGE_VECTOR_STORES)
            .build();


    /**
     * 获取VectorStore对象
     * 根据配置参数创建或从缓存中获取VectorStore
     *
     * @return VectorStore对象
     */
    public VectorStore getVectorStore() {

        // 获取当前线程的threadUserId
        Long threadUserId = UserIdContextHolder.get();

        // 从Redis中获取对应的模型配置
        AiModelDTO model = fetchUserEmbedModelByUserId(threadUserId);

        String cacheKey = "VECTOR_STORE:" + threadUserId;

        return vectorStoreCache.get(cacheKey, key-> createVectorStore(createEmbeddingModel(
                model.getUrlBase(),
                model.getApiKey(),
                model.getModelName())));
    }

    /**
     * 获取VectorStore对象（指定用户ID）
     * 根据配置参数创建或从缓存中获取VectorStore
     *
     * @param userId 用户ID
     * @return VectorStore对象
     */
    public VectorStore getVectorStore(Long userId) {

        // 从Redis中获取对应的模型配置
        AiModelDTO model = fetchUserEmbedModelByUserId(userId);

        String cacheKey = "VECTOR_STORE:" + userId;

        return vectorStoreCache.get(cacheKey, key-> createVectorStore(createEmbeddingModel(
                model.getUrlBase(),
                model.getApiKey(),
                model.getModelName())));
    }

    /**
     * 创建嵌入模型
     * 根据配置参数创建OpenAiEmbeddingModel
     *
     * @param baseUrl API基础URL
     * @param apiKey API密钥
     * @param modelName 模型名称
     * @return EmbeddingModel对象
     */
    private EmbeddingModel createEmbeddingModel(
            String baseUrl,
            String apiKey,
            String modelName
    ) {

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        return new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(modelName).build()
        );
    }

    /**
     * 构建 VectorStore
     * 根据EmbeddingModel创建PgVectorStore
     *
     * @param embeddingModel 嵌入模型对象
     * @return VectorStore对象
     */
    private VectorStore createVectorStore(EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1024)
                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW)                     // Optional: defaults to HNSW
                .initializeSchema(true)              // Optional: defaults to false
                .schemaName("public")                // Optional: defaults to "public"
                .vectorTableName("vector_store")     // Optional: defaults to "vector_store"
                .maxDocumentBatchSize(10000)         // Optional: defaults to 10000
                .build();
    }

    /**
     * 无效化VectorStore缓存对象
     */
    public void invalidVectorStoreCache() {
        Long threadUserId = UserIdContextHolder.get();
        log.debug("VectorStore::InvalidCache::UserID - {}",  threadUserId);
        String cacheKey = "VECTOR_STORE:" + threadUserId;
        vectorStoreCache.invalidate(cacheKey);
    }

    /**
     * 从数据库获取用户嵌入模型配置
     *
     * @param threadUserId 用户ID
     * @return AI模型配置DTO
     */
    private AiModelDTO getUserModelsFromDB(Long threadUserId) {
        UserAiModels one = Db.lambdaQuery(UserAiModels.class)
                .eq(UserAiModels::getUserId, threadUserId)
                .select(
                        UserAiModels::getEmbeddedModelBase, UserAiModels::getEmbeddedModelApiKey, UserAiModels::getEmbeddedModelModelName
                ).one();

        if (one == null) {
            throw new BusinessException("未设置Embedded模型");
        }

        AiModelDTO aiModelDTO = new AiModelDTO();
        aiModelDTO.setUrlBase(one.getEmbeddedModelBase());
        aiModelDTO.setApiKey(one.getEmbeddedModelApiKey());
        aiModelDTO.setModelName(one.getEmbeddedModelModelName());

        return aiModelDTO;
    }


    /**
     * 根据用户ID获取嵌入模型配置
     * 优先从Redis缓存中获取，缓存未命中则从数据库获取
     *
     * @param userId 用户ID
     * @return AI模型配置DTO
     */
    private AiModelDTO fetchUserEmbedModelByUserId(Long userId) {

        String key = RedisContext.CACHE_USER_AI_MODEL_CONFIGURATIONS_PREFIX + userId;
        String rawJson = redisTemplate.opsForValue().get(key);
        AiModelDTO model;

        if (StrUtil.isBlank(rawJson)) {
            // 缓存未设置，重新设置缓存
            model = getUserModelsFromDB(userId);
        } else {
            UserAiModelsVO bean = JSONUtil.toBean(rawJson, UserAiModelsVO.class);
            model = new AiModelDTO();
            model.setUrlBase(bean.getEmbeddedModelBase());
            model.setApiKey(bean.getEmbeddedModelApiKey());
            model.setModelName(bean.getEmbeddedModelModelName());
        }

        // 检查EmbeddedModel是否是空的
        if (
                StrUtil.isBlank(model.getUrlBase()) ||
                        StrUtil.isBlank(model.getApiKey()) ||
                        StrUtil.isBlank(model.getModelName())
        ) {
            throw new BusinessException("未设置Embedded模型");
        }

        return model;
    }
}
