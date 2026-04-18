package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片保存结果VO
 * 用于返回图片上传后的结果信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "图片保存结果")
public class ImageSaveVO {
    /**
     * 图片UUID
     * 图片的唯一标识符，用于后续获取图片
     */
    @Schema(description = "图片UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String imageUuid;
}
