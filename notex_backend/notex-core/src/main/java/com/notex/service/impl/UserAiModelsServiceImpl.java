package com.notex.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.notex.context.RedisContext;
import com.notex.domain.Response;
import com.notex.domain.dto.UserChangeAiModelsDTO;
import com.notex.domain.po.UserAiModels;
import com.notex.domain.vo.UserAiModelsVO;
import com.notex.exception.BusinessException;
import com.notex.mapper.UserAiModelsMapper;
import com.notex.service.IUserAiModelsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.notex.utils.ChatClientUtils;
import com.notex.utils.UserIdContextHolder;
import com.notex.utils.VectorStoreUtils;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author author
 * @since 2026-04-01
 */
@Service
@RequiredArgsConstructor
public class UserAiModelsServiceImpl extends ServiceImpl<UserAiModelsMapper, UserAiModels> implements IUserAiModelsService {

    private final StringRedisTemplate redisTemplate;

    @Resource
    private ChatClientUtils chatClientUtils;

    @Resource
    private VectorStoreUtils vectorStoreUtils;

    @Override
    public Response<UserAiModelsVO> fetchUserAiModels() {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 检查缓存中是否有对应数据
        String key = RedisContext.CACHE_USER_AI_MODEL_CONFIGURATIONS_PREFIX + threadUserId;
        String rawJson = redisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(rawJson)) {
            UserAiModelsVO bean = JSONUtil.toBean(rawJson, UserAiModelsVO.class);
            return Response.success(bean);
        }

        // 缓存未命中，重新从数据库中获取
        UserAiModels userData = getUserModelsFromDB(threadUserId);

        UserAiModelsVO userAiModelsVO = new UserAiModelsVO();
        BeanUtils.copyProperties(userData, userAiModelsVO);

        // 存入缓存
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(userAiModelsVO));

        return Response.success(userAiModelsVO);
    }

    private UserAiModels getUserModelsFromDB(Long threadUserId) {
        UserAiModels one = this.lambdaQuery()
                .eq(UserAiModels::getUserId, threadUserId)
                .one();

        if (one == null) {
            throw new BusinessException("未找到对应用户模型存储信息");
        }

        return one;
    }

    @Override
    public void changeAiModels(UserChangeAiModelsDTO data) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 无效化缓存
        String key = RedisContext.CACHE_USER_AI_MODEL_CONFIGURATIONS_PREFIX + threadUserId;

        redisTemplate.delete(key);

        chatClientUtils.invalidateChatClient();
        vectorStoreUtils.invalidVectorStoreCache();

        this.lambdaUpdate()
                .eq(UserAiModels::getUserId, threadUserId)
                .set(UserAiModels::getStandardModelBase, data.getStandardModelBase())
                .set(UserAiModels::getStandardModelApiKey, data.getStandardModelApiKey())
                .set(UserAiModels::getStandardModelModelName, data.getStandardModelModelName())
                .set(UserAiModels::getEmbeddedModelBase, data.getEmbeddedModelBase())
                .set(UserAiModels::getEmbeddedModelApiKey, data.getEmbeddedModelApiKey())
                .set(UserAiModels::getEmbeddedModelModelName, data.getEmbeddedModelModelName())
                .update();

        // 重新存入缓存
        UserAiModelsVO userAiModelsVO = new UserAiModelsVO();
        BeanUtil.copyProperties(data, userAiModelsVO);
        String jsonStr = JSONUtil.toJsonStr(userAiModelsVO);
        redisTemplate.opsForValue().set(key, jsonStr);
    }
}
