package com.notex.config;

import org.jspecify.annotations.NonNull;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.notex.context.RAGContext.MAX_CHUNK_SIZE;

/**
 * Token文本切分器配置类
 * 配置用于RAG的文本切分器，支持中英文混合文档的智能切分
 */
@Configuration
public class TokenSplitterConfiguration {

    /**
     * 创建自定义Token文本切分器Bean
     * 配置了针对中英文混合文档的切分规则，优先按照语义边界切分
     *
     * @return 配置好的Token文本切分器实例
     */
    @Bean
    public @NonNull TokenTextSplitter myTokenTextSplitter() {
        List<Character> mixedPunctuation = List.of(
                '\n', // 最高优先级：换行符（段落、Markdown 列表、代码行的天然边界）
                '。', '！', '？', // 中文句末
                '.', '!', '?',  // 英文句末 (技术文档常见)
                '；', ';',      // 中英文分号 (长句、代码语句行末)
                '，', ','       // 作为最后的兜底：如果一段话实在太长，只能按逗号切分
        );
        return new TokenTextSplitter(
                MAX_CHUNK_SIZE,         // 最大Chunk块大小
                30,                     // 最小Chunk块大小
                50,                     // 最小Embedding长度
                10240,                  // 最大Chunk数量（防止超长文本/恶意注入导致内存溢出）
                true,                   // 保留原始语句分隔符
                mixedPunctuation        // 按照中文标点寻找切分边界
        );
    }

}
