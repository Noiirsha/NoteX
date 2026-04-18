package com.notex.service.impl;

import com.notex.domain.Response;
import com.notex.domain.po.ChatHistory;
import com.notex.domain.po.UserChats;
import com.notex.domain.vo.ChatContentVO;
import com.notex.exception.BusinessException;
import com.notex.mapper.ChatHistoryMapper;
import com.notex.service.IChatHistoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.notex.service.IUserChatsService;
import com.notex.utils.UserIdContextHolder;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Noiirsha
 * @since 2026-04-15
 */
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements IChatHistoryService {

    @Resource
    private IUserChatsService userChatsService;

    @Override
    public Response<List<ChatContentVO>> fetchConversation(String conversationId) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 检查是否是目前用户的对话
        boolean exists = userChatsService.lambdaQuery()
                .eq(UserChats::getUserId, threadUserId)
                .eq(UserChats::getConversationId, conversationId)
                .exists();

        if (!exists) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "该对话不存在或可能已被删除");
        }

        List<ChatContentVO> vos = new ArrayList<>();

        // 获取对话并返回
        this.lambdaQuery()
                .eq(ChatHistory::getConversationId, conversationId)
                .orderByAsc(ChatHistory::getTimestamp)
                .list()
                .forEach(chat -> {
                    ChatContentVO chatContentVO = new ChatContentVO();
                    chatContentVO.setContent(chat.getContent());
                    chatContentVO.setType(chat.getType());
                    vos.add(chatContentVO);
                });

        return Response.success(vos);
    }
}
