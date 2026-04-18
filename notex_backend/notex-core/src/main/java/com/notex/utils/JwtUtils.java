package com.notex.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * JWT 工具类（基于 JJWT 0.12.x）。
 * <p>
 * 该工具类提供以下能力：
 * <ul>
 *     <li>生成签名 Token（HS256）</li>
 *     <li>解析并校验 Token</li>
 *     <li>读取主题（subject）与自定义 Claims</li>
 *     <li>判断 Token 是否过期</li>
 * </ul>
 * <p>
 * 说明：
 * <ul>
 *     <li>本工具类是纯静态工具，不依赖 Spring，可直接在 notex-core 中调用。</li>
 *     <li>默认使用 HS256 对称签名算法，密钥建议不少于 32 字节。</li>
 *     <li>支持传入带有 "Bearer " 前缀的 Token，内部会自动剥离。</li>
 * </ul>
 */
public final class JwtUtils {

    /** Bearer 前缀。 */
    private static final String BEARER_PREFIX = "Bearer ";

    private JwtUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 生成 JWT Token。
     *
     * @param subject 主题（通常可放用户唯一标识，例如 userId）
     * @param claims  自定义载荷（可为空）
     * @param ttl     令牌有效时长；为 {@code null} 时表示不设置过期时间
     * @param secret  对称签名密钥（UTF-8 字符串，建议长度 >= 32 字节）
     * @return 生成后的 JWT 字符串
     * @throws NullPointerException     当 {@code subject} 或 {@code secret} 为空时抛出
     * @throws IllegalArgumentException 当密钥长度不足时抛出
     */
    public static String createToken(String subject, Map<String, Object> claims, Duration ttl, String secret) {
        Objects.requireNonNull(subject, "subject 不能为空");

        Instant now = Instant.now();
        SecretKey key = buildHmacKey(secret);

        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .signWith(key, Jwts.SIG.HS256);

        if (claims != null && !claims.isEmpty()) {
            claims.forEach(builder::claim);
        }

        if (ttl != null) {
            if (ttl.isNegative() || ttl.isZero()) {
                throw new IllegalArgumentException("ttl 必须大于 0");
            }
            builder.expiration(Date.from(now.plus(ttl)));
        }

        return builder.compact();
    }

    /**
     * 生成 JWT Token（无自定义 Claims）。
     *
     * @param subject 主题（通常可放用户唯一标识，例如 userId）
     * @param ttl     令牌有效时长；为 {@code null} 时表示不设置过期时间
     * @param secret  对称签名密钥（UTF-8 字符串，建议长度 >= 32 字节）
     * @return 生成后的 JWT 字符串
     */
    public static String createToken(String subject, Duration ttl, String secret) {
        return createToken(subject, null, ttl, secret);
    }

    /**
     * 解析并验证 JWT，返回完整 Claims。
     * <p>
     * 校验内容包括：签名、格式、过期时间（如果 Token 内包含 exp）。
     *
     * @param token  JWT 字符串，允许携带 "Bearer " 前缀
     * @param secret 对称签名密钥
     * @return 解析后的 Claims
     * @throws JwtException             当 Token 非法、签名错误、已过期等场景抛出
     * @throws IllegalArgumentException 当 token 为空或密钥不合法时抛出
     */
    public static Claims parseClaims(String token, String secret) {
        String pureToken = stripBearerPrefix(token);

        return Jwts.parser()
                .verifyWith(buildHmacKey(secret))
                .build()
                .parseSignedClaims(pureToken)
                .getPayload();
    }

    /**
     * 判断 Token 是否有效。
     * <p>
     * 若签名错误、格式错误、令牌过期等，都会返回 {@code false}。
     *
     * @param token  JWT 字符串
     * @param secret 对称签名密钥
     * @return {@code true} 表示有效；{@code false} 表示无效
     */
    public static boolean isTokenValid(String token, String secret) {
        try {
            parseClaims(token, secret);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 判断 Token 是否过期。
     *
     * @param token  JWT 字符串
     * @param secret 对称签名密钥
     * @return {@code true} 表示已过期；{@code false} 表示未过期或无过期字段
     * @throws JwtException             当 Token 非法或签名错误时抛出
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    public static boolean isTokenExpired(String token, String secret) {
        Claims claims = parseClaims(token, secret);
        Date expiration = claims.getExpiration();
        return expiration != null && expiration.before(new Date());
    }

    /**
     * 获取 Token 中的主题（subject）。
     *
     * @param token  JWT 字符串
     * @param secret 对称签名密钥
     * @return 主题字符串
     */
    public static String getSubject(String token, String secret) {
        return parseClaims(token, secret).getSubject();
    }

    /**
     * 按名称读取 Claim。
     *
     * @param token     JWT 字符串
     * @param secret    对称签名密钥
     * @param claimName Claim 名称
     * @return Claim 对应的值；若不存在则返回 {@code null}
     */
    public static Object getClaim(String token, String secret, String claimName) {
        Objects.requireNonNull(claimName, "claimName 不能为空");
        return parseClaims(token, secret).get(claimName);
    }

    /**
     * 按名称读取指定类型的 Claim。
     *
     * @param token     JWT 字符串
     * @param secret    对称签名密钥
     * @param claimName Claim 名称
     * @param type      目标类型
     * @param <T>       泛型类型
     * @return Claim 对应值；若不存在则返回 {@code null}
     */
    public static <T> T getClaim(String token, String secret, String claimName, Class<T> type) {
        Objects.requireNonNull(claimName, "claimName 不能为空");
        Objects.requireNonNull(type, "type 不能为空");
        return parseClaims(token, secret).get(claimName, type);
    }

    /**
     * 去除可选的 Bearer 前缀。
     *
     * @param token 原始 token
     * @return 不带 Bearer 前缀的 token
     */
    public static String stripBearerPrefix(String token) {
        Objects.requireNonNull(token, "token 不能为空");
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("token 不能为空字符串");
        }
        if (trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return trimmed.substring(BEARER_PREFIX.length()).trim();
        }
        return trimmed;
    }

    /**
     * 构建 HMAC 签名密钥。
     *
     * @param secret 密钥字符串（UTF-8）
     * @return SecretKey
     * @throws NullPointerException     当 secret 为空时抛出
     * @throws IllegalArgumentException 当 secret 长度不足 32 字节时抛出
     */
    private static SecretKey buildHmacKey(String secret) {
        Objects.requireNonNull(secret, "secret 不能为空");
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT 密钥长度不足：至少需要 32 字节（当前 " + keyBytes.length + " 字节）");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
