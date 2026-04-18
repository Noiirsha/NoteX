package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户信息VO
 * 用于返回用户的基本信息，包括用户名、昵称、邮箱、头像等
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户信息")
public class UserInformationVO {

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
     * 邮箱
     * 用户的注册邮箱
     */
    @Schema(description = "邮箱", example = "admin@example.com")
    private String email;

    /**
     * 头像图片URL
     * 用户头像的访问地址，如果为null表示用户未设置头像
     */
    @Schema(description = "头像图片URL", example = "https://example.com/avatar.jpg")
    private String avatarImageUrl;

    /**
     * 注册时间
     * 用户账号的注册时间
     */
    @Schema(description = "注册时间", example = "2024-01-01T00:00:00")
    private LocalDateTime registerTime;

    /**
     * 最后登录时间
     * 用户最后一次登录的时间
     */
    @Schema(description = "最后登录时间", example = "2024-01-15T10:30:00")
    private LocalDateTime lastLoginTime;

}
