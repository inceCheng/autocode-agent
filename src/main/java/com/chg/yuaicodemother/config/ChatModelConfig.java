package com.chg.yuaicodemother.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * ChatModel 配置
 * 配置非流式聊天模型，用于图片收集等工具调用场景
 */
@Configuration
public class ChatModelConfig {

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;

    @Value("${langchain4j.open-ai.chat-model.max-tokens:8192}")
    private Integer maxTokens;

    @Value("${langchain4j.open-ai.chat-model.max-retries:3}")
    private Integer maxRetries;

    /**
     * 非流式 ChatModel（用于图片收集等工具调用）
     * 设置较长的超时时间以应对 DeepSeek API 响应较慢的情况
     */
    @Bean
    public ChatModel chatModel() {
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