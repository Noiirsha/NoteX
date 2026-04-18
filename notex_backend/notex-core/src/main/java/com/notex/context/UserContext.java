package com.notex.context;

/**
 * 用户相关业务常量。
 * <p>
 * 统一维护接口返回的提示文案，避免硬编码散落在各处。
 */
public class UserContext {

    /** JWT 认证失败：未携带令牌或令牌无效/过期。 */
    public static final String UNAUTHORIZED = "认证令牌无效或已过期，请重新登录";

    /** JWT 认证失败：请求头中缺少 Authorization 字段。 */
    public static final String TOKEN_MISSING = "未携带认证令牌，请先登录";

    /** 用户填写信息验证失败 */
    public static final String REQUIRED_INFORMATION_MISSING = "请检查是否有遗漏的信息未填写";

    /** 用户账号密码错误 */
    public static final String USERNAME_OR_PASSWORD_ERROR = "账号或密码不正确";

    /** 用户名已存在 */
    public static final String USERNAME_ALREADY_EXISTS = "已经存在相同的用户名";

    /** 邮箱已存在 */
    public static final String EMAIL_ALREADY_EXISTS = "已经存在相同的邮箱";

    /** 非法请求 */
    public static final String ILLEGAL_REQUEST = "非法请求";

    /** 密码不正确 */
    public static final String PASSWORD_INCORRECT = "密码不正确";

}
