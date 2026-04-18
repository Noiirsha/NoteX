package com.notex.service;

import com.notex.domain.Response;
import com.notex.domain.po.UserChats;
import com.baomidou.mybatisplus.extension.service.IService;
import com.notex.domain.vo.ChatHistoryVO;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Noiirsha
 * @since 2026-04-15
 */
public interface IUserChatsService extends IService<UserChats> {

    Response<List<ChatHistoryVO>> fetchChatHistory();
}
