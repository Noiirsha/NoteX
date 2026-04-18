package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录结果VO
 * 用于返回用户登录成功后的信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户登录结果")
public class UserLoginVO {

    /**
     * 用户名
     * 用户的唯一标识符
     */
    @Schema(description = "用户名", example = "admin")
    private String username;

    /**
     * 昵称
     * 用户显示的昵称
     */
    @Schema(description = "昵称", example = "管理员")
    private String nickname;

    /**
     * 头像图片URL
     * 用户头像的访问地址
     */
    @Schema(description = "头像图片URL", example = "https://example.com/avatar.jpg")
    private String avatarImageUrl;

    /**
     * 认证令牌
     * 用户登录后的JWT令牌，用于后续API认证
     */
    @Schema(description = "认证令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

}
