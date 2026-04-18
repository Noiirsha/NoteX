package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户修改偏好设定请求DTO
 * 用于修改用户的偏好设置，如黑暗模式等
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户修改偏好设定请求")
public class UserChangePreferencesDTO {

    /**
     * 黑暗模式开关
     * true表示开启黑暗模式，false表示关闭
     */
    @Schema(description = "黑暗模式开关", example = "true")
    private Boolean darkMode;

}
