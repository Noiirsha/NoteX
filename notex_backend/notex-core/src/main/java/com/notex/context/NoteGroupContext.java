package com.notex.context;

/**
 * 笔记分组相关业务常量。
 * <p>
 * 统一维护笔记分组相关的提示文案，避免硬编码散落在各处。
 */
public class NoteGroupContext {

    /** 根分组ID */
    public static final Long ROOT_GROUP = 0L;

    /** 目标笔记父组不存在 */
    public static final String TARGET_PARENT_GROUP_NOT_FOUND = "目标笔记父组不存在";

    /** 目标修改笔记组不存在 */
    public static final String TARGET_MODIFY_GROUP_NOT_FOUND = "目标修改笔记组不存在";

    /** 尝试将父组修改为自己 */
    public static final String CANNOT_SET_PARENT_TO_SELF = "尝试将父组修改为自己";

    /** 未找到目标笔记组,可能其已被删除 */
    public static final String GROUP_NOT_FOUND_OR_DELETED = "未找到目标笔记组,可能其已被删除";

}
