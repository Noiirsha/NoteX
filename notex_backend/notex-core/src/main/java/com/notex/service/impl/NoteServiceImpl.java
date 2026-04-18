package com.notex.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.notex.context.NoteContext;
import com.notex.context.RedisContext;
import com.notex.domain.Response;
import com.notex.domain.dto.NoteCreateDTO;
import com.notex.domain.dto.NoteModifyDTO;
import com.notex.domain.dto.NoteSaveContentDTO;
import com.notex.domain.dto.RAGRequestDTO;
import com.notex.domain.po.Note;
import com.notex.domain.po.NoteGroup;
import com.notex.domain.po.UserAiModels;
import com.notex.domain.vo.*;
import com.notex.exception.BusinessException;
import com.notex.mapper.NoteMapper;
import com.notex.service.INoteService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.notex.service.IRAGService;
import com.notex.utils.UserIdContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 笔记服务实现类
 * 实现笔记的创建、查询、修改、删除等核心逻辑
 */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl extends ServiceImpl<NoteMapper, Note> implements INoteService {

    private final StringRedisTemplate redisTemplate;

    private final IRAGService ragService;

    /**
     * 获取分组下的笔记基本信息
     * 获取指定分组下所有笔记的基本信息列表，使用Redis缓存提升性能
     *
     * @param groupId 笔记分组ID
     * @return 笔记基本信息列表
     */
    @Override
    public Response<List<NoteBasicInformationVO>> fetchBasicInfoByGroupId(Long groupId) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 检查这个groupId合法性
        // 1. 检查缓存中该请求是否为非法请求
        String accessPermissionKey = RedisContext.CACHE_NOTE_BASIC_INFO_VIOLATION_ACCESS_PREFIX + threadUserId + ":" + groupId;
        boolean isViolation = redisTemplate.hasKey(accessPermissionKey);
        if (isViolation) {
            throw new BusinessException(HttpStatus.NOT_FOUND, NoteContext.NOTE_GROUP_NOT_FOUND);
        }
        // 2. 如果没查到是非法请求，则查询数据库确认是否是合法请求
        boolean exists = Db.lambdaQuery(NoteGroup.class)
                .eq(NoteGroup::getUserId, threadUserId)
                .eq(NoteGroup::getId, groupId)
                .exists();

        if (!exists) {
            // 缓存非法请求，防止缓存穿透
            redisTemplate.opsForValue().set(accessPermissionKey, "", Duration.ofSeconds(10));
            throw new BusinessException(HttpStatus.NOT_FOUND, NoteContext.NOTE_GROUP_NOT_FOUND);
        }

        // 构建缓存Key, 尝试从缓存中读取
        String key = RedisContext.CACHE_NOTE_BASIC_INFO_LIST_PREFIX + groupId;
        String rawJson = redisTemplate.opsForValue().get(key);
        if (rawJson != null) {
            // 解析数据
            List<NoteBasicInformationVO> cacheList = JSON.parseArray(rawJson, NoteBasicInformationVO.class);
            // 返回
            return Response.success(cacheList);
        }

        // 缓存未命中，重新获取信息
        // 获取当前组内日志
        List<NoteBasicInformationVO> voList = new ArrayList<>();
        List<Note> dataList = this.lambdaQuery()
                .eq(Note::getGroupId, groupId)
                .select(
                        Note::getId, Note::getNoteUuid, Note::getTitle,
                        Note::getCreatedAt, Note::getUpdatedAt, Note::getIsIndexed
                )
                .list();

        dataList.forEach(note -> {

            NoteBasicInformationVO noteInformationVO = new NoteBasicInformationVO();
            BeanUtils.copyProperties(note, noteInformationVO);
            voList.add(noteInformationVO);

        });

        // 重建缓存
        String jsonString = JSON.toJSONString(voList);
        redisTemplate.opsForValue().set(key, jsonString);

        return Response.success(voList);
    }

    /**
     * 获取笔记详细信息
     * 根据笔记UUID获取笔记的完整信息，并更新最近访问记录
     *
     * @param noteUuid 笔记UUID
     * @return 笔记详细信息
     */
    @Override
    public Response<NoteInformationVO> fetchNote(String noteUuid) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 获取数据
        Note noteData = this.lambdaQuery()
                .eq(Note::getUserId, threadUserId)
                .eq(Note::getNoteUuid, noteUuid)
                .one();

        if (noteData == null) {
            return Response.error(HttpStatus.NOT_FOUND, NoteContext.NOTE_NOT_FOUND);
        }

        NoteInformationVO noteInformationVO = new NoteInformationVO();
        BeanUtils.copyProperties(noteData, noteInformationVO);

        // 写入缓存到RECENT_ACCESS(最近访问) List中
        NoteRecentAccessVO noteRecentAccessVO = new NoteRecentAccessVO();
        noteRecentAccessVO.setNoteTitle(noteData.getTitle());
        noteRecentAccessVO.setNoteUuid(noteData.getNoteUuid());

        String key = RedisContext.RECENT_ACCESS_PREFIX + threadUserId;
        String member = JSON.toJSONString(noteRecentAccessVO);
        double score = System.currentTimeMillis();

        // 最近访问缓存:
        // 1. 添加或更新（ZSET: 同一个 member 会更新分数，实现"更新访问时间"）
        redisTemplate.opsForZSet().add(key, member, score);

        // 2. 保留最近10条（按分数从小到大，删除最旧的）
        Long size = redisTemplate.opsForZSet().zCard(key);
        if (size != null && size > RedisContext.RECENT_ACCESS_MAX_SIZE) {
            // 删除分数最小的（最早访问的）元素
            redisTemplate.opsForZSet().removeRange(key, 0, size - RedisContext.RECENT_ACCESS_MAX_SIZE - 1);
        }

        return Response.success(noteInformationVO);
    }

    /**
     * 创建笔记
     * 创建一个新的笔记，并无效化相关缓存
     *
     * @param noteCreateDTO 笔记创建请求DTO
     * @return 笔记创建结果
     */
    @Override
    public Response<NoteCreateVO> createNote(NoteCreateDTO noteCreateDTO) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 确认用户是否有该组
        boolean exists = Db.lambdaQuery(NoteGroup.class)
                .eq(NoteGroup::getUserId, threadUserId)
                .eq(NoteGroup::getId, noteCreateDTO.getGroupId())
                .exists();

        if (!exists) {
            return Response.error(HttpStatus.NOT_FOUND, NoteContext.NOTE_GROUP_NOT_FOUND);
        }

        // 新建笔记
        Note note = new Note().setTitle(noteCreateDTO.getTitle())
                .setGroupId(noteCreateDTO.getGroupId())
                .setNoteUuid(UUID.randomUUID().toString())
                .setUserId(threadUserId);

        this.save(note);

        // 回传数据
        NoteCreateVO noteCreateVO = new NoteCreateVO();
        BeanUtils.copyProperties(note, noteCreateVO);

        // 无效化缓存
        invalidationCache(noteCreateDTO.getGroupId());

        return Response.success(noteCreateVO);
    }

    /**
     * 删除笔记
     * 根据笔记UUID删除笔记，并无效化相关缓存
     *
     * @param noteUuid 笔记UUID
     * @return 操作结果
     */
    @Override
    @Transactional
    public Response<Void> deleteNote(String noteUuid) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 确认GroupId
        Note targetNote = this.lambdaQuery()
                .eq(Note::getUserId, threadUserId)
                .eq(Note::getNoteUuid, noteUuid)
                .select(Note::getId, Note::getGroupId)
                .one();

        // 执行删除并获得结果
        boolean remove = this.lambdaUpdate()
                .eq(Note::getId, targetNote.getId())
                .remove();

        if (!remove) {
            return Response.error(HttpStatus.NOT_FOUND, NoteContext.NOTE_NOT_FOUND);
        }

        // 删除RAG中的内容
        RAGRequestDTO deleteRAGDTO = new RAGRequestDTO();
        RAGRequestDTO.NoteDetail noteDetail = new RAGRequestDTO.NoteDetail();
        noteDetail.setNoteUuid(noteUuid);
        deleteRAGDTO.setNoteDetail(noteDetail);
        ragService.deleteNoteVector(deleteRAGDTO);

        // 无效化缓存
        invalidationCache(targetNote.getGroupId());

        return Response.success();
    }

    /**
     * 修改笔记基本信息
     * 修改笔记的标题或所属分组，并无效化相关缓存
     *
     * @param noteModifyDTO 笔记修改请求DTO
     * @return 操作结果
     */
    @Override
    public Response<Void> modifyNote(NoteModifyDTO noteModifyDTO) {

        // 检查两个值是否都是空的
        if (StrUtil.isBlank(noteModifyDTO.getTitle()) && noteModifyDTO.getGroupId() == null) {
            return Response.success(); // 啥都不做
        }

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 检查当前笔记是否是当前用户的
        Note targetNote = this.lambdaQuery()
                .eq(Note::getUserId, threadUserId)
                .eq(Note::getNoteUuid, noteModifyDTO.getUuid())
                .select(Note::getId, Note::getGroupId)
                .one();

        if (targetNote == null) {
            return Response.error(HttpStatus.NOT_FOUND, NoteContext.NOTE_NOT_FOUND);
        }

        LambdaUpdateChainWrapper<Note> wrapper = this.lambdaUpdate()
                .eq(Note::getId, targetNote.getId());

        // 更新标题
        if (StrUtil.isNotBlank(noteModifyDTO.getTitle())) {
            wrapper.set(Note::getTitle, noteModifyDTO.getTitle());
        }

        // 更换组
        // 更换组需要无效化两个组的缓存，因此先预先放置一个invalidCacheTargetGroupId
        Long invalidCacheTargetGroupId = null;
        if (noteModifyDTO.getGroupId() != null) {
            // 检查是否尝试更换到根目录
            if (noteModifyDTO.getGroupId() == 0L) {
                return Response.error(HttpStatus.FORBIDDEN, NoteContext.NOTE_LOCATION_VIOLATION);
            }
            // 检查用户是否存在这个笔记组
            boolean isGroupExist = Db.lambdaQuery(NoteGroup.class)
                    .eq(NoteGroup::getUserId, threadUserId)
                    .eq(NoteGroup::getId, noteModifyDTO.getGroupId())
                    .exists();

            if (!isGroupExist) {
                return Response.error(HttpStatus.FORBIDDEN, NoteContext.NOTE_LOCATION_VIOLATION);
            }

            // 需要无效化修改过去的组的Cache
            invalidCacheTargetGroupId = noteModifyDTO.getGroupId();

            wrapper.set(Note::getGroupId, noteModifyDTO.getGroupId());
        }

        // 执行更新
        wrapper.update();


        // 无效化缓存
        invalidationCache(targetNote.getGroupId());
        if (invalidCacheTargetGroupId != null) {
            invalidationCache(invalidCacheTargetGroupId);
        }

        return Response.success();
    }

    /**
     * 保存笔记内容
     * 保存笔记的内容
     *
     * @param noteSaveContentDTO 笔记内容保存请求DTO
     * @return 操作结果
     */
    @Override
    public Response<Void> saveNoteContent(NoteSaveContentDTO noteSaveContentDTO) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 检查当前笔记是否是当前用户的
        boolean exists = this.lambdaQuery()
                .eq(Note::getUserId, threadUserId)
                .eq(Note::getNoteUuid, noteSaveContentDTO.getUuid())
                .exists();

        if (!exists) {
            return Response.error(HttpStatus.NOT_FOUND, "目标笔记不存在");
        }

        // 执行内容保存
        this.lambdaUpdate()
                .eq(Note::getUserId, threadUserId)
                .eq(Note::getNoteUuid, noteSaveContentDTO.getUuid())
                .set(Note::getContent, noteSaveContentDTO.getContent())
                .set(Note::getIsIndexed, false)
                .update();

        // 检查用户是否配置了Embedded，如果有，则新开线程调用note -> embedded进行保存
        UserAiModels modelConfig = Db.lambdaQuery(UserAiModels.class)
                .eq(UserAiModels::getUserId, threadUserId)
                .select(UserAiModels::getEmbeddedModelModelName, UserAiModels::getEmbeddedModelBase, UserAiModels::getEmbeddedModelApiKey)
                .one();
        if (modelConfig != null) {
            if (
                    StrUtil.isBlank(modelConfig.getEmbeddedModelBase()) ||
                    StrUtil.isBlank(modelConfig.getEmbeddedModelApiKey()) ||
                    StrUtil.isBlank(modelConfig.getEmbeddedModelModelName())
            ) {
                log.debug("用户没有配置embedding或者embedding配置不完全，取消笔记向量化...");
            } else {
                ragService.note2EmbeddingViaAsync(noteSaveContentDTO.getUuid(), threadUserId);
            }
        }

        return Response.success();
    }

    /**
     * 获取最近访问的笔记
     * 从Redis缓存中获取用户最近访问的笔记列表
     *
     * @return 最近访问笔记列表
     */
    @Override
    public Response<List<NoteRecentAccessVO>> fetchRecentAccessNotes() {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 从Redis中读取数据
        String key = RedisContext.RECENT_ACCESS_PREFIX + threadUserId;
        Set<String> rawJsonSet = redisTemplate.opsForZSet().reverseRange(key, 0, -1);

        List<NoteRecentAccessVO> noteRecentAccessVOList;

        if (rawJsonSet != null && !rawJsonSet.isEmpty()) {
            noteRecentAccessVOList = rawJsonSet.stream()
                    .map(json -> JSON.parseObject(json, NoteRecentAccessVO.class))
                    .collect(Collectors.toList());
        } else {
            noteRecentAccessVOList = Collections.emptyList();
        }

        return Response.success(noteRecentAccessVOList);
    }

    /**
     * 无效化缓存
     * @param groupId 笔记对应groupId
     */
    private void invalidationCache(Long groupId) {
        String keyNote = RedisContext.CACHE_NOTE_BASIC_INFO_LIST_PREFIX + groupId;

        // 第一次删除
        redisTemplate.delete(keyNote);

        // 第二次删除(延迟双删,高并发再用)
        /*
         * CompletableFuture.runAsync(() -> {
         *             try {
         *                 Thread.sleep(200);
         *             } catch (InterruptedException e) {
         *                 Thread.currentThread().interrupt();
         *             }
         *             redisTemplate.delete(keyNote);
         *         });
         */
    }

    /**
     * 创建笔记（工具调用）
     * 供AI工具调用创建笔记，并无效化相关缓存
     *
     * @param userId 用户ID
     * @param groupId 分组ID
     * @param title 笔记标题
     * @param content 笔记内容
     */
    @Override
    public void createNote(Long userId, Long groupId, String title, String content) {

        // 无效化目标组缓存
        invalidationCache(groupId);

        UUID uuid = UUID.randomUUID();

        Note note = new Note();
        note.setUserId(userId);
        note.setGroupId(groupId);
        note.setTitle(title);
        note.setContent(content);
        note.setNoteUuid(uuid.toString());

        this.save(note);
    }

    @Override
    public Response<List<NoteIndexStatusVO>> fetchIndexStatus() {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        List<NoteIndexStatusVO> vos = new ArrayList<>();

        this.lambdaQuery()
                .eq(Note::getUserId, threadUserId)
                .orderByDesc(Note::getUpdatedAt)
                .select(Note::getNoteUuid, Note::getTitle, Note::getUpdatedAt, Note::getIsIndexed)
                .list()
                .forEach(note -> {
                    NoteIndexStatusVO noteIndexStatusVO = new NoteIndexStatusVO();
                    BeanUtils.copyProperties(note, noteIndexStatusVO);
                    vos.add(noteIndexStatusVO);
                });

        return Response.success(vos);
    }

    @Override
    public Response<Void> noteToEmbedded(String noteUuid) {
        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 启动向量化
        ragService.note2Embedding(noteUuid, threadUserId);

        return Response.success();
    }
}
