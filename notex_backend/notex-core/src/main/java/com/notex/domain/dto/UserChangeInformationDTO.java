package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户修改信息请求DTO
 * 用于修改用户的基本信息，如昵称和头像
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户修改信息请求")
public class UserChangeInformationDTO {

    /**
     * 昵称
     * 用户的新昵称，长度限制在1-50之间
     */
    @Size(min = 1, max = 50, message = "昵称长度必须在1-50之间")
    @Schema(description = "昵称", example = "管理员")
    private String nickname;

    /**
     * 头像图片URL
     * 用户的新头像地址
     */
    @Schema(description = "头像", example = "image.url")
    private String avatarImageUrl;

}
