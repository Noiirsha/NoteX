package com.notex.service;

import com.notex.domain.Response;
import com.notex.domain.po.ChatHistory;
import com.baomidou.mybatisplus.extension.service.IService;
import com.notex.domain.vo.ChatContentVO;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Noiirsha
 * @since 2026-04-15
 */
public interface IChatHistoryService extends IService<ChatHistory> {

    Response<List<ChatContentVO>> fetchConversation(String conversationId);
}
