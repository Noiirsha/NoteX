package com.notex.utils;

/**
 * 当前线程用户 ID 上下文工具。
 * <p>
 * 适用于在一次请求链路中临时保存当前登录用户 ID，
 * 例如：在拦截器中写入，在 Service/DAO 中读取，
 * 并在请求结束后清理，避免线程复用导致的数据串用问题。
 * <p>
 * 典型用法：
 * <pre>
 * // 进入请求时
 * UserIdContextHolder.set(10001L);
 *
 * // 业务代码中
 * Long userId = UserIdContextHolder.getRequired();
 *
 * // 请求结束时（务必）
 * UserIdContextHolder.clear();
 * </pre>
 */
public final class UserIdContextHolder {

    /**
     * 保存当前线程的用户 ID。
     */
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private UserIdContextHolder() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 设置当前线程的用户 ID。
     *
     * @param userId 用户 ID（允许为 {@code null}，等价于清空）
     */
    public static void set(Long userId) {
        if (userId == null) {
            USER_ID_HOLDER.remove();
            return;
        }
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 获取当前线程保存的用户 ID。
     *
     * @return 用户 ID；若未设置则返回 {@code null}
     */
    public static Long get() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 获取当前线程保存的用户 ID（强校验）。
     *
     * @return 用户 ID
     * @throws IllegalStateException 当未设置用户 ID 时抛出
     */
    public static Long getRequired() {
        Long userId = USER_ID_HOLDER.get();
        if (userId == null) {
            throw new IllegalStateException("当前线程未设置用户 ID，请先调用 UserIdContextHolder.set(userId)");
        }
        return userId;
    }

    /**
     * 判断当前线程是否已设置用户 ID。
     *
     * @return {@code true} 已设置；{@code false} 未设置
     */
    public static boolean isPresent() {
        return USER_ID_HOLDER.get() != null;
    }

    /**
     * 清理当前线程保存的用户 ID。
     * <p>
     * 强烈建议在请求结束（如过滤器 finally 块）调用，
     * 防止线程池复用导致脏数据泄漏。
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
