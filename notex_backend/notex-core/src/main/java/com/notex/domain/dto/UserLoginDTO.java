package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录请求DTO
 * 用于用户登录认证
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户登录请求")
public class UserLoginDTO {

    /**
     * 用户名
     * 用户的登录账号
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

}
