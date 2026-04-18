package com.notex.context;

/**
 * Redis缓存键常量定义类
 * 定义系统中所有Redis缓存键的前缀和相关配置常量
 */
public class RedisContext {

    /**
     * 最近访问笔记缓存键前缀
     * 格式: notex:recent:{userId}
     */
    public static final String RECENT_ACCESS_PREFIX = "notex:recent:";

    /**
     * 最近访问笔记列表最大缓存数量
     * 每个用户最多缓存5条最近访问记录
     */
    public static final Integer RECENT_ACCESS_MAX_SIZE = 5;

    /**
     * 笔记分组列表缓存键前缀
     * 格式: notex:group:{userId}
     */
    public static final String CACHE_GROUP_LIST_PREFIX = "notex:group:";

    /**
     * 笔记基本信息列表缓存键前缀
     * 格式: notex:note_basic_info:{groupId}
     */
    public static final String CACHE_NOTE_BASIC_INFO_LIST_PREFIX = "notex:note_basic_info:";

    /**
     * 笔记基本信息违规访问缓存键前缀
     * 用于标记笔记基本信息缓存是否被违规访问
     * 格式: notex:note_basic_info:violation_access:{userId}:{groupId}
     */
    public static final String CACHE_NOTE_BASIC_INFO_VIOLATION_ACCESS_PREFIX = "notex:note_basic_info:violation_access:";

    public static final String CACHE_USER_AI_MODEL_CONFIGURATIONS_PREFIX = "notex:user_ai_model_configurations:";

}
