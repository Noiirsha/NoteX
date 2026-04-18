package com.notex.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.notex.context.AiContext;
import com.notex.domain.Response;
import com.notex.domain.dto.ChatGenerateTitleDTO;
import com.notex.domain.dto.ChatRequestDTO;
import com.notex.domain.po.ChatHistory;
import com.notex.domain.po.UserChats;
import com.notex.service.IChatHistoryService;
import com.notex.service.IChatMemoryCompressService;
import com.notex.service.IChatService;
import com.notex.service.IUserChatsService;
import com.notex.tools.NoteTools;
import com.notex.utils.ChatClientUtils;
import com.notex.utils.UserIdContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * AI聊天服务实现类
 * 实现与AI助手进行对话的核心逻辑
 */
@Service
@Slf4j
public class ChatServiceImpl implements IChatService {

    @Resource
    private ChatClientUtils chatClientUtils;

    @Resource
    private IChatMemoryCompressService compressionService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private NoteTools noteTools;

    @Resource
    private ChatMemory chatMemory;

    /**
     * 执行AI聊天
     * 构建AI助手的提示词，处理会话压缩，并返回流式响应
     *
     * @param chatRequestDTO 聊天请求DTO，包含用户消息和配置
     * @return AI的流式响应内容
     */
    @Override
    public Flux<String> doChat(ChatRequestDTO chatRequestDTO) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 动态获取Chat Client
        ChatClient chatClient = chatClientUtils.getChatClient();

        // 更新访问时间
        Db.lambdaUpdate(UserChats.class)
                .eq(UserChats::getUserId, threadUserId)
                .eq(UserChats::getConversationId, chatRequestDTO.getConversationId())
                .set(UserChats::getLastAccessTime, LocalDateTime.now())
                .update();

        // 构建Prompt
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(
                """
                        你是NoteX的AI助手小N，请用简洁的语气回答用户的问题。
                      
                        【用户名】{user_name}（仅在问好时使用）。
                        【表情】尽量减少使用emoji，除非用户明确要求。
                        
                        # 工具
                        - getCurrentPageNoteContent：获取当前打开的笔记内容
                        - getNoteByUUID(uuid)：通过UUID获取指定笔记
                        - searchByKeyWord(keywords)：向量检索笔记内容
                        - createNote(content, title)：在当前文件夹创建笔记
                        
                        # 调用规则
                        1. **获取正在编辑的笔记页面的内容**：如果用户明确询问有关当前笔记页面的内容，优先用 getCurrentPageNoteContent；内容不足时提示用户提供UUID，用 getNoteByUUID 补充。
                        2. **搜索**：用户查找/回忆时或者需要搜索整个笔记仓库的内容的时候，调用 searchByKeyWord；若返回“未配置向量模型”，引导用户配置。
                        3. **创建**：用户明确要求“记下来/保存/创建笔记”时调用，标题需简洁概括内容。
                        4. **联动示例**：
                           - 搜索后整理 → createNote
                           - 当前笔记缺详情 → 通过searchByKeyWord的结果中获取UUID → getNoteByUUID
                        5. **禁止**：猜测UUID、重复调用失败工具、自动创建未确认的笔记。
                        
                        # 输出
                        清晰友好，用Markdown，错误信息人性化（不暴露技术栈）。
                        
                        现在请回答用户的问题。
                        """
        );

        Prompt prompt = systemPromptTemplate.create(Map.of(
                "user_name", chatRequestDTO.getUserNickname()
        ));

        // 工具Context
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(AiContext.TOOL_CONTEXT_USER_ID, threadUserId);

        // 传入工作区参数
        ChatRequestDTO.WorkspaceDetail workspaceDetail = chatRequestDTO.getWorkspaceDetail();
        if (workspaceDetail != null) {
            if (workspaceDetail.getGroupId() != null) toolContext.put(AiContext.TOOL_CONTEXT_GROUP_ID, workspaceDetail.getGroupId());
            if (StrUtil.isNotBlank(workspaceDetail.getNoteUuid()))toolContext.put(AiContext.TOOL_CONTEXT_NOTE_UUID, workspaceDetail.getNoteUuid());
            if (StrUtil.isNotBlank(workspaceDetail.getNoteContent()))toolContext.put(AiContext.TOOL_CONTEXT_NOTE_CONTENT, workspaceDetail.getNoteContent());
        }

        // 清洗Tool Calling Tag，否则可能污染上下文
        clearTagBeforeOperation(chatRequestDTO.getConversationId());

        // 会话压缩激活器,优先检查Token再检查对话轮数
        compressionService.checkTokenAndCompress(chatRequestDTO);
        compressionService.checkRoundAndCompress(chatRequestDTO);

        return chatClient.prompt(prompt)
                .user(chatRequestDTO.getMessage())
                .tools(noteTools)
                .toolContext(toolContext)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatRequestDTO.getConversationId()))
                .advisors(advisorSpec -> advisorSpec.param(AiContext.CHAT_MEMORY_CHAT_DTO, chatRequestDTO))
                .stream()
                .content();
    }

    /**
     * 生成会话标题
     * 根据聊天内容自动生成会话标题
     *
     * @param chatGenerateTitleDTO 生成标题请求DTO
     * @return 生成的会话标题
     */
    @Override
    public Response<String> generateTitle(ChatGenerateTitleDTO chatGenerateTitleDTO) {

        // 获取UserId
        Long threadUserId = UserIdContextHolder.get();

        // 检查是否有已经存在的标题
        UserChats currentChat = Db.lambdaQuery(UserChats.class)
                .eq(UserChats::getUserId, threadUserId)
                .eq(UserChats::getConversationId, chatGenerateTitleDTO.getConversationId())
                .select(UserChats::getUserId, UserChats::getConversationTitle)
                .one();

        if (currentChat == null) {
            // 创建新会话
            UserChats userChats = new UserChats();
            userChats.setUserId(threadUserId);
            userChats.setConversationTitle("新对话");
            userChats.setConversationId(chatGenerateTitleDTO.getConversationId());
            Db.save(userChats);
            currentChat = userChats;
        }

        if (!currentChat.getConversationTitle().equals("新对话")) {
            return Response.success(currentChat.getConversationTitle());
        }

        // 动态获取Chat Client
        ChatClient chatClient = chatClientUtils.getChatClientWithNoAdvisor();

        // 构建Prompt
        String content = chatClient.prompt()
                .system("""
                        你是一个专业的标题生成助手。你的任务是为用户提供的文本生成一个简洁、准确的标题。
                        
                        核心约束：
                        1. 标题必须是对原文核心信息的直接概括，不是总结、不是大纲、不是分点列表。
                        2. 中文标题严格控制在20字以内，英文标题控制在50个字符以内。
                        3. 直接输出标题文本，不要输出任何解释、前言、后缀或格式符号（如“标题：”、“——”等）。
                        4. 严禁使用分点、序号、换行展开内容。
                        5. 如果原文主题明确，直接提取关键词组合；如果模糊，选择最核心的一个对象+一个动作/类型。
                        
                        错误示例（严禁出现）：
                        - “蔚蓝档案要点总结 （可基于您的实际笔记补充内容）”
                        - “角色系统：xxx 战斗机制：xxx”
                        
                        正确示例：
                        - 原文：“帮我找一下我笔记里面关于蔚蓝档案对应的笔记，并且简单总结给我看”
                          输出：蔚蓝档案笔记总结
                        """)
                .user(chatGenerateTitleDTO.getFirstContent())
                .call()
                .content();

        // 保存至新对话
        Db.lambdaUpdate(UserChats.class)
                .eq(UserChats::getId, currentChat.getId())
                .set(UserChats::getConversationTitle, content)
                .update();

        return Response.success(content);
    }

    /**
     * 删除会话
     * 删除指定的聊天会话及相关历史记录
     *
     * @param conversationId 会话ID
     * @return 操作结果
     */
    @Override
    public Response<Void> deleteConversationById(String conversationId) {

        // 获取目前线程
        Long threadUserId = UserIdContextHolder.get();

        // 检查是否是自己的Conversation
        boolean exists = Db.lambdaQuery(UserChats.class)
                .eq(UserChats::getUserId, threadUserId)
                .eq(UserChats::getConversationId, conversationId)
                .exists();

        if (exists) {
            // 1. 删除user_chats
            Db.lambdaUpdate(UserChats.class)
                    .eq(UserChats::getUserId, threadUserId)
                    .eq(UserChats::getConversationId, conversationId)
                    .remove();

            // 2. 删除chats_history
            Db.lambdaUpdate(ChatHistory.class)
                    .eq(ChatHistory::getConversationId, conversationId)
                    .remove();

            // 3. 删除spring_ai_chat_memory
            chatMemory.clear(conversationId);
        }

        return Response.success();
    }

    /**
     * 脏数据清洗
     * 防止tool calling标签污染下方的上下文
     * @param conversationId 会话ID
     */
    private void clearTagBeforeOperation(String conversationId) {
        String sql = """
            UPDATE spring_ai_chat_memory
            SET content = REGEXP_REPLACE(content, '(?s)"?<notex_.*?\\s?/>"?\\s*', '', 'g')
            WHERE (conversation_id, timestamp) IN (
                SELECT conversation_id, timestamp
                FROM spring_ai_chat_memory
                WHERE conversation_id = ? AND type = 'ASSISTANT'
                ORDER BY timestamp DESC
                LIMIT 1
            )
        """;

        try {
            int update = jdbcTemplate.update(sql, conversationId);
            log.debug("清洗: {}", update);
        } catch (Exception e) {
            log.error("写入助手消息失败 - conversationId: {}, error: {}", conversationId, e.getMessage(), e);
        }

    }
}
