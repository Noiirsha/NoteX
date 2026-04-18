package com.notex.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI模型配置DTO
 * 用于传递AI模型的配置信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "AI模型配置")
public class AiModelDTO {

    /**
     * API基础URL
     * AI模型的API基础地址
     */
    @Schema(description = "API基础URL", example = "https://api.openai.com/v1")
    private String urlBase;

    /**
     * API密钥
     * 用于调用AI模型的认证密钥
     */
    @Schema(description = "API密钥", example = "sk-xxxxxxxxxxxxxxxx")
    private String apiKey;

    /**
     * 模型名称
     * 指定使用的AI模型名称
     */
    @Schema(description = "模型名称", example = "gpt-4")
    private String modelName;

}
