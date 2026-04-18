package com.notex.context;

/**
 * AI模块上下文常量
 * 定义AI聊天和会话压缩相关的配置参数
 */
public class AiContext {

    /**
     * 最大对话轮数（一个角色算一轮）
     */
    public static final int MAX_MESSAGE_ROUND = 40;

    /*
                压缩控制
     */
    /**
     * 激活会话压缩时候的轮数
     */
    public static final int COMPRESS_TRIGGERED_ROUND = 30;

    /**
     * 压缩时压缩的对话轮数
     */
    public static final int COMPRESS_SIZE = 20;

    /**
     * 上下文窗口最大值(默认128K)
     */
    public static final int CONTEXT_WINDOW_MAX_TOKEN = 128000;

    /**
     * 激活压缩时的上下文阈值(默认80%)
     */
    public static final float CONTEXT_WINDOW_COMPRESS_TRIGGER_RATIO = 0.800f;

    /**
     * 压缩上下文量(默认全文的40%)
     */
    public static final float CONTEXT_WINDOW_COMPRESS_SIZE_RATIO = 0.400f;

    /**
     * 压缩总结字数限制
     */
    public static final int COMPRESS_SUMMARIZE_MAX_WORD_COUNT = 400;

    /*
                通用配置
     */
    /**
     * ChatMemory中的DTO标识符
     */
    public static final String CHAT_MEMORY_CHAT_DTO = "chat_memory_chat_dto";

    /**
     * 最大ChatClients存储对象
     */
    public static final int MAX_STORAGE_CHAT_CLIENTS = 1000;

    /**
     * 双写标识符
     */
    public static final String DUAL_WRITE_ADVISOR = "dual_write_advisor";

    /**
     * 双写Advisor优先级
     */
    public static final int DUAL_WRITE_ADVISOR_ORDER = 0;

    public static final String TOOL_CONTEXT_USER_ID = "tool_context_user_id";
    public static final String TOOL_CONTEXT_GROUP_ID = "tool_context_group_id";
    public static final String TOOL_CONTEXT_NOTE_UUID = "tool_context_note_uuid";
    public static final String TOOL_CONTEXT_NOTE_CONTENT = "tool_context_note_content";
}
