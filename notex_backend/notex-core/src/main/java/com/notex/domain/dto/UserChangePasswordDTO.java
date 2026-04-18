package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户修改密码请求DTO
 * 用于用户修改登录密码
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "修改密码请求")
public class UserChangePasswordDTO {

    /**
     * 旧密码哈希值
     * 用户当前的密码哈希值，用于验证身份
     */
    @NotBlank(message = "旧密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100之间")
    @Schema(description = "旧密码哈希值", example = "your-old-password-hash-here")
    private String oldPasswordHash;

    /**
     * 新密码哈希值
     * 用户要设置的新密码哈希值
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100之间")
    @Schema(description = "新密码哈希值", example = "your-new-password-hash-here")
    private String newPasswordHash;

}
