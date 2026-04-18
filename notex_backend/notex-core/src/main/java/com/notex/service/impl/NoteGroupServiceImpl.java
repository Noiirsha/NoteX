package com.notex.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.notex.context.NoteGroupContext;
import com.notex.context.RedisContext;
import com.notex.domain.Response;
import com.notex.domain.dto.NoteGroupCreateDTO;
import com.notex.domain.dto.NoteGroupModifyDTO;
import com.notex.domain.po.NoteGroup;
import com.notex.domain.vo.NoteGroupCreateVO;
import com.notex.domain.vo.NoteGroupInformationVO;
import com.notex.mapper.NoteGroupMapper;
import com.notex.service.INoteGroupService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.notex.utils.UserIdContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.notex.context.NoteGroupContext.ROOT_GROUP;

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
@Slf4j
public class NoteGroupServiceImpl extends ServiceImpl<NoteGroupMapper, NoteGroup> implements INoteGroupService {

    private final StringRedisTemplate redisTemplate;

    private final NoteGroupMapper noteGroupMapper;

    @Override
    public Response<List<NoteGroupInformationVO>> fetchGroups() {

        // 获取UserId，构建缓存Key
        Long threadUserId = UserIdContextHolder.get();
        String key = RedisContext.CACHE_GROUP_LIST_PREFIX + threadUserId;

        // 尝试从缓存中读取
        String rawJson = redisTemplate.opsForValue().get(key);
        if (rawJson != null) {
            // 解析数据
            List<NoteGroupInformationVO> cacheList = JSON.parseArray(rawJson, NoteGroupInformationVO.class);
            // 返回
            return Response.success(cacheList);
        }

        // 缓存未命中，获取Group信息
        List<NoteGroupInformationVO> voList = new ArrayList<>();
        List<NoteGroup> dataList = this.lambdaQuery()
                .eq(NoteGroup::getUserId, threadUserId)
                .orderByAsc(NoteGroup::getId)
                .select(NoteGroup::getId, NoteGroup::getParentGroupId, NoteGroup::getGroupName)
                .list();

        // 添加至数组中
        dataList.forEach(item -> {
            NoteGroupInformationVO noteGroupInformationVO = new NoteGroupInformationVO();
            BeanUtils.copyProperties(item, noteGroupInformationVO);
            voList.add(noteGroupInformationVO);
        });

        // 重建缓存
        String jsonString = JSON.toJSONString(voList);
        redisTemplate.opsForValue().set(key, jsonString);

        return Response.success(voList);
    }

    @Override
    public Response<NoteGroupCreateVO> createGroup(NoteGroupCreateDTO noteGroupCreateDTO) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 创建Group
        NoteGroup noteGroup = new NoteGroup()
                .setUserId(threadUserId)
                .setGroupName(noteGroupCreateDTO.getGroupName());

        // 如果parentGroupId不为空或根目录，则先检查是否存在目标父组
        if (noteGroupCreateDTO.getParentGroupId() != null && !noteGroupCreateDTO.getParentGroupId().equals(ROOT_GROUP)) {

            boolean exists = this.lambdaQuery()
                    .eq(NoteGroup::getUserId, threadUserId)
                    .eq(NoteGroup::getId, noteGroupCreateDTO.getParentGroupId())
                    .exists();

            if (!exists) {
                return Response.error(HttpStatus.FORBIDDEN, NoteGroupContext.TARGET_PARENT_GROUP_NOT_FOUND);
            }

            // 目标父组存在
            noteGroup.setParentGroupId(noteGroupCreateDTO.getParentGroupId());
        }

        // 保存到数据库中
        this.save(noteGroup);

        // 回传数据
        NoteGroupCreateVO noteGroupCreateVO = new NoteGroupCreateVO();
        noteGroupCreateVO.setId(noteGroup.getId());

        // 无效化缓存
        invalidationCache(threadUserId);

        return Response.success(noteGroupCreateVO);
    }

    @Override
    public Response<Void> modifyGroup(NoteGroupModifyDTO noteGroupModifyDTO) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 防止非法请求: 啥都不更新
        if (StrUtil.isBlank(noteGroupModifyDTO.getGroupName()) && noteGroupModifyDTO.getParentGroupId() == null) {
            return Response.success();
        }

        // 修改目标组
        // 二次确认目标组是否存在
        NoteGroup origin = this.lambdaQuery()
                .eq(NoteGroup::getUserId, threadUserId)
                .eq(NoteGroup::getId, noteGroupModifyDTO.getId())
                .one();

        if (origin == null) {
            return Response.error(HttpStatus.FORBIDDEN, NoteGroupContext.TARGET_MODIFY_GROUP_NOT_FOUND);
        }

        // 修改wrapper
        LambdaUpdateChainWrapper<NoteGroup> wrapper = this.lambdaUpdate()
                .eq(NoteGroup::getUserId, threadUserId)
                .eq(NoteGroup::getId, noteGroupModifyDTO.getId());

        // 1. 用户修改组名
        if (StrUtil.isNotBlank(noteGroupModifyDTO.getGroupName())) {
            wrapper.set(NoteGroup::getGroupName, noteGroupModifyDTO.getGroupName());
        }

        // 2. 用户修改父组
        if (noteGroupModifyDTO.getParentGroupId() != null) {

            // 检查父组是不是自己（防止非法请求）
            if (noteGroupModifyDTO.getParentGroupId().equals(noteGroupModifyDTO.getId()))
                return Response.error(HttpStatus.FORBIDDEN, NoteGroupContext.CANNOT_SET_PARENT_TO_SELF);

            // 修改为根目组
            if (noteGroupModifyDTO.getParentGroupId().equals(ROOT_GROUP)) {
                wrapper.set(NoteGroup::getParentGroupId, ROOT_GROUP);
            } else {
                // 查询用户下是否存在该父组
                boolean exists = this.lambdaQuery()
                        .eq(NoteGroup::getUserId, threadUserId)
                        .eq(NoteGroup::getId, noteGroupModifyDTO.getParentGroupId())
                        .exists();

                if (!exists) {
                    return Response.error(HttpStatus.FORBIDDEN, NoteGroupContext.TARGET_PARENT_GROUP_NOT_FOUND);
                }

                // 存在则进行更新
                wrapper.set(NoteGroup::getParentGroupId, noteGroupModifyDTO.getParentGroupId());
            }

        }

        // 执行更新
        wrapper.update();

        // 无效化缓存
        invalidationCache(threadUserId);

        return Response.success();
    }

    @Override
    @Transactional
    public Response<Void> deleteGroup(Long groupId) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 检查当前请求组是否是当前用户下的组或组是否仍存在
        boolean exists = this.lambdaQuery()
                .eq(NoteGroup::getUserId, threadUserId)
                .eq(NoteGroup::getId, groupId)
                .exists();

        if (!exists) {
            return Response.error(HttpStatus.NOT_FOUND, NoteGroupContext.GROUP_NOT_FOUND_OR_DELETED);
        }

        // 执行关联删除(注: note关联删除会依据onDelete: Cascade关系进行删除)
        List<Long> toBeDeleteGroupIds = noteGroupMapper.queryAllGroupInTargetGroup(groupId);
        toBeDeleteGroupIds.add(groupId); // 把当前组也给加进去
        noteGroupMapper.deleteGroupRecursively(groupId);

        // 无效化缓存
        invalidationCache(threadUserId, toBeDeleteGroupIds);
        return Response.success();
    }

    @Override
    public boolean isGroupExist(Long groupId) {

        // 获取用户ID
        Long threadUserId = UserIdContextHolder.get();

        // 从Redis中查找
        String key = RedisContext.CACHE_GROUP_LIST_PREFIX + threadUserId;
        String rawJson = redisTemplate.opsForValue().get(key);
        if (!StrUtil.isBlank(rawJson)) {
            // 数据存在，开始查找是否存在对应的group id
            List<NoteGroupInformationVO> cacheList = JSON.parseArray(rawJson, NoteGroupInformationVO.class);
            return cacheList.stream()
                    .anyMatch(group -> group.getId().equals(groupId));
        }

        // Redis没数据，找数据库
        return this.lambdaQuery()
                .eq(NoteGroup::getUserId, threadUserId)
                .eq(NoteGroup::getId, groupId)
                .exists();
    }

    /**
     * 无效化组缓存
     * @param threadUserId 线程用户ID
     */
    private void invalidationCache(Long threadUserId) {
        // 第一次删除
        String keyGroup = RedisContext.CACHE_GROUP_LIST_PREFIX + threadUserId;
        redisTemplate.delete(keyGroup);

        // 第二次删除(延迟双删,高并发再用)
        /*
         * CompletableFuture.runAsync(() -> {
         *             try {
         *                 Thread.sleep(200);
         *             } catch (InterruptedException e) {
         *                 Thread.currentThread().interrupt();
         *             }
         *             redisTemplate.delete(keyGroup);
         *         });
         */

    }

    /**
     * 无效化组缓存
     * @param threadUserId 线程用户ID
     * @param currentGroupId 当前组ID
     */
    private void invalidationCache(Long threadUserId, Long currentGroupId) {
        // 第一次删除
        String keyGroup = RedisContext.CACHE_GROUP_LIST_PREFIX + threadUserId;
        redisTemplate.delete(keyGroup);

        String keyNote = RedisContext.CACHE_NOTE_BASIC_INFO_LIST_PREFIX + currentGroupId;
        redisTemplate.delete(keyNote);

    }

    /**
     * 无效化组缓存
     * @param threadUserId 线程用户ID
     * @param toBeDeletedGroupIds 删除关联Group下对应的笔记组
     */
    private void invalidationCache(Long threadUserId, List<Long> toBeDeletedGroupIds) {

        // 删除Group
        String keyGroup = RedisContext.CACHE_GROUP_LIST_PREFIX + threadUserId;
        redisTemplate.delete(keyGroup);

        // 删除关联Group下对应的笔记基本信息
        toBeDeletedGroupIds.forEach(groupId -> {
            String keyNote = RedisContext.CACHE_NOTE_BASIC_INFO_LIST_PREFIX + groupId;
            redisTemplate.delete(keyNote);
        });

    }
}
