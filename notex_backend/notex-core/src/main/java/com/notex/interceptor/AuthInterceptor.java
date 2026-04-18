package com.notex.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notex.config.WebMvcConfig;
import com.notex.context.UserContext;
import com.notex.domain.Response;
import com.notex.utils.JwtUtils;
import com.notex.utils.UserIdContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JWT 认证拦截器。
 * <p>
 * 对所有非白名单请求进行 JWT 校验：
 * <ul>
 *     <li>白名单路径（如登录、注册）直接放行，不做任何 Token 校验。</li>
 *     <li>非白名单路径必须携带合法的 {@code Authorization: Bearer <token>} 请求头；
 *         校验通过后将解析出的 {@code userId}（即 Token subject）写入
 *         {@link UserIdContextHolder}，供后续业务层使用。</li>
 *     <li>校验失败时返回 HTTP 401，响应体格式与业务接口保持一致（{@link Response}）。</li>
 *     <li>请求结束后（{@code afterCompletion}）无论成功与否，均会清理 ThreadLocal，
 *         防止线程池复用导致的用户 ID 泄漏。</li>
 * </ul>
 * <p>
 * 白名单路径由 {@link WebMvcConfig} 统一维护，通过构造函数注入。
 *
 * @see WebMvcConfig
 * @see UserIdContextHolder
 * @see JwtUtils
 */
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    /** Authorization 请求头名称。 */
    private static final String AUTHORIZATION_HEADER = "authorization";

    private final String jwtSecret;
    private final ObjectMapper objectMapper;

    /**
     * 构造 {@code AuthInterceptor}。
     *
     * @param jwtSecret    JWT 对称签名密钥，与生成 Token 时使用的密钥一致
     * @param objectMapper Jackson 序列化器，用于将 {@link Response} 写入响应体
     */
    public AuthInterceptor(String jwtSecret, ObjectMapper objectMapper) {
        this.jwtSecret = jwtSecret;
        this.objectMapper = objectMapper;
    }

    /**
     * 请求预处理：校验 JWT 并将 userId 写入 ThreadLocal。
     * <p>
     * 白名单路径由 {@link WebMvcConfig} 通过 {@code excludePathPatterns} 排除，
     * 因此进入本方法的请求均需要携带有效 Token。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler  目标处理器
     * @return {@code true} 表示放行；{@code false} 表示拦截并已写入 401 响应
     * @throws Exception 序列化异常（实际由 {@link #writeUnauthorized} 内部处理）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String token = request.getHeader(AUTHORIZATION_HEADER);

        if (token == null || token.isBlank()) {
            log.debug("请求 [{}] 缺少 Authorization 头，拒绝访问", request.getRequestURI());
            writeUnauthorized(response, UserContext.TOKEN_MISSING);
            return false;
        }

        if (!JwtUtils.isTokenValid(token, jwtSecret)) {
            log.debug("请求 [{}] 携带的 Token 无效或已过期", request.getRequestURI());
            writeUnauthorized(response, UserContext.UNAUTHORIZED);
            return false;
        }

        // 解析 userId 并写入 ThreadLocal
        String subject = JwtUtils.getSubject(token, jwtSecret);
        Long userId = Long.parseLong(subject);
        UserIdContextHolder.set(userId);

        log.debug("请求 [{}] 认证通过，userId={}", request.getRequestURI(), userId);
        return true;
    }

    /**
     * 请求完成后清理 ThreadLocal，防止线程池复用导致用户 ID 泄漏。
     *
     * @param request   当前 HTTP 请求
     * @param response  当前 HTTP 响应
     * @param handler   目标处理器
     * @param ex        处理过程中抛出的异常（可为 {@code null}）
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserIdContextHolder.clear();
    }

    /**
     * 向客户端写入 HTTP 401 响应，响应体为 JSON 格式的 {@link Response}。
     *
     * @param response HTTP 响应对象
     * @param message  错误提示信息
     * @throws IOException 写入响应体时发生 IO 异常
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Response<Void> body = Response.error(HttpStatus.UNAUTHORIZED, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
