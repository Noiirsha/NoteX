package com.notex.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户AI模型配置VO
 * 用于返回用户的AI模型配置信息，包括标准模型和嵌入模型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户AI模型配置")
public class UserAiModelsVO {

    /**
     * 标准模型API基础URL
     * 用于调用标准AI模型的API地址
     */
    @Schema(description = "标准模型API基础URL", example = "https://api.openai.com/v1")
    private String standardModelBase;

    /**
     * 标准模型API密钥
     * 用于调用标准AI模型的认证密钥
     */
    @Schema(description = "标准模型API密钥", example = "sk-xxxxxxxxxxxxxxxx")
    private String standardModelApiKey;

    /**
     * 标准模型名称
     * 指定使用的标准AI模型名称
     */
    @Schema(description = "标准模型名称", example = "gpt-4")
    private String standardModelModelName;

    /**
     * 嵌入模型API基础URL
     * 用于调用嵌入模型的API地址
     */
    @Schema(description = "嵌入模型API基础URL", example = "https://api.openai.com/v1")
    private String embeddedModelBase;

    /**
     * 嵌入模型API密钥
     * 用于调用嵌入模型的认证密钥
     */
    @Schema(description = "嵌入模型API密钥", example = "sk-xxxxxxxxxxxxxxxx")
    private String embeddedModelApiKey;

    /**
     * 嵌入模型名称
     * 指定使用的嵌入模型名称
     */
    @Schema(description = "嵌入模型名称", example = "text-embedding-ada-002")
    private String embeddedModelModelName;

}
