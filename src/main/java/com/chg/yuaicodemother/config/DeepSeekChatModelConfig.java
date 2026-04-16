package com.chg.yuaicodemother.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * ChatModel 配置
 * 配置非流式聊天模型，用于图片收集等工具调用场景
 */
@Configuration
@ConfigurationProperties(prefix = "ai.deepseek")
@Data
public class DeepSeekChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Integer maxRetries;

    /**
     * 非流式 ChatModel（用于图片收集等工具调用）
     * 设置较长的超时时间以应对 DeepSeek API 响应较慢的情况
     */
    @Bean
    public ChatModel deepseekChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(120))  // 设置 120 秒超时
                .maxRetries(maxRetries)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}