package com.notex.config;

import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tokenizer配置类
 * 配置Token数量估算器，用于计算文本的Token数量
 */
@Configuration
public class TokenizerConfiguration {

    /**
     * 创建Token数量估算器Bean
     * 使用JTokkit实现进行Token估算
     *
     * @return Token数量估算器实例
     */
    @Bean
    public TokenCountEstimator tokenCountEstimator() {
        return new JTokkitTokenCountEstimator();
    }

}
