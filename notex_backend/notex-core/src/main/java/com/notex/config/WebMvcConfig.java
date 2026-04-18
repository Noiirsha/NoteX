package com.notex.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notex.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 全局配置。
 * <p>
 * 负责注册 {@link AuthInterceptor} 并维护 JWT 认证白名单。
 * 白名单中的路径将直接放行，不进行任何 Token 校验。
 * <p>
 * 当前白名单：
 * <ul>
 *     <li>{@code GET /user/login}        — 用户登录</li>
 *     <li>{@code POST /user/register_new_account} — 注册新账号</li>
 * </ul>
 *
 * @see AuthInterceptor
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * JWT 对称签名密钥，从配置文件 {@code notex.jwt.secret} 读取。
     */
    @Value("${notex.jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper;

    /**
     * 构造 {@code WebMvcConfig}，注入 Jackson {@link ObjectMapper}。
     *
     * @param objectMapper Spring 容器中的 Jackson 序列化器
     */
    public WebMvcConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 注册 {@link AuthInterceptor}，并配置白名单路径（{@code excludePathPatterns}）。
     * <p>
     * 白名单路径不经过 JWT 校验，直接放行。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(jwtSecret, objectMapper))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register_new_account",
                        "/debug/**",

                        // 静态图片获取
                        "/static/get_image/**"
                );
    }
}
