package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户偏好VO
 * 用于返回用户的偏好设置信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户偏好")
public class UserPreferenceVO {

    /**
     * 黑暗模式开关
     * true表示开启黑暗模式，false表示关闭
     */
    @Schema(description = "黑暗模式开关", example = "true")
    private Boolean darkMode;

}
