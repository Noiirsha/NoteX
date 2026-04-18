package com.notex.service.impl;

import com.notex.domain.Response;
import com.notex.domain.po.UserChats;
import com.notex.domain.vo.ChatHistoryVO;
import com.notex.mapper.UserChatsMapper;
import com.notex.service.IUserChatsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.notex.utils.UserIdContextHolder;
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
public class UserChatsServiceImpl extends ServiceImpl<UserChatsMapper, UserChats> implements IUserChatsService {

    @Override
    public Response<List<ChatHistoryVO>> fetchChatHistory() {

        // 获取userId
        Long threadUserId = UserIdContextHolder.get();

        // 获取记录
        List<ChatHistoryVO> vos = new ArrayList<>();

        this.lambdaQuery()
                .eq(UserChats::getUserId, threadUserId)
                .orderByDesc(UserChats::getLastAccessTime)
                .select(UserChats::getConversationId, UserChats::getConversationTitle, UserChats::getLastAccessTime)
                .list()
                .forEach(userChats -> {

                    ChatHistoryVO chatHistoryVO = new ChatHistoryVO();
                    chatHistoryVO.setConversationId(userChats.getConversationId());
                    chatHistoryVO.setConversationTitle(userChats.getConversationTitle());
                    chatHistoryVO.setLastAccessTime(userChats.getLastAccessTime());
                    vos.add(chatHistoryVO);

                });

        return Response.success(vos);
    }
}
