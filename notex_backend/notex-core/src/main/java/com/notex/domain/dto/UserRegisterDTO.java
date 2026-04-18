package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注册请求DTO
 * 用于新用户注册账号
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户注册请求")
public class UserRegisterDTO {

    /**
     * 用户名
     * 用户的登录账号，长度限制在3-20之间
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20之间")
    @Schema(description = "用户名", example = "admin")
    private String username;

    /**
     * 密码哈希值
     * 用户登录密码的哈希值
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100之间")
    @Schema(description = "密码哈希值", example = "your-password-hash-here")
    private String passwordHash;

    /**
     * 邮箱
     * 用户的注册邮箱，用于账号验证
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱", example = "admin@example.com")
    private String email;

    /**
     * 昵称
     * 用户显示的昵称，长度限制在1-50之间
     */
    @NotBlank(message = "昵称不能为空")
    @Size(min = 1, max = 50, message = "昵称长度必须在1-50之间")
    @Schema(description = "昵称", example = "管理员")
    private String nickname;

    /**
     * 黑暗模式
     * 是否开启黑暗模式的初始设置
     */
    @Schema(description = "黑暗模式", example = "false")
    private Boolean darkMode;

}
