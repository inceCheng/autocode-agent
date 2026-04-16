package com.chg.yuaicodemother.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 模型统一配置属性
 * 支持通过 ai.provider 切换不同大模型提供商
 *
 * 示例配置：
 * ai:
 *   provider: glm    # 可选: glm, deepseek, minimax
 *   glm:
 *     api-key: xxx
 *     model-name: glm-5.1
 *     max-tokens: 65536
 *   deepseek:
 *     base-url: https://api.deepseek.com
 *     api-key: xxx
 *     model-name: deepseek-chat
 *     max-tokens: 8192
 *   minimax:
 *     api-key: xxx
 *     model-name: MiniMax-2.7
 *     max-tokens: 8192
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class LLMConfig {

    /**
     * 当前使用的模型提供商
     * 可选值: glm, deepseek, minimax
     */
    private String provider = "glm";

    /**
     * GLM（智谱）模型配置
     */
    private GlmConfig glm = new GlmConfig();

    /**
     * DeepSeek 模型配置
     */
    private DeepSeekConfig deepseek = new DeepSeekConfig();

    /**
     * MiniMax 模型配置
     */
    private MiniMaxConfig minimax = new MiniMaxConfig();

    /**
     * Kimi 模型配置
     */
    private KimiConfig kimi = new KimiConfig();

    @Data
    public static class GlmConfig {
        /**
         * 智谱 API Key
         */
        private String apiKey;

        /**
         * 模型名称，默认 glm-5.1
         */
        private String modelName = "glm-5.1";

        /**
         * 最大输出 Token 数
         */
        private Integer maxTokens = 65536;

        /**
         * 温度参数，控制随机性
         */
        private Float temperature = 0.7f;

        /**
         * Top-P 参数
         */
        private Float topP;

        /**
         * 请求超时时间（秒）
         */
        private Integer timeout = 120;

        /**
         * 最大重试次数
         */
        private Integer maxRetries = 3;
    }

    @Data
    public static class DeepSeekConfig {
        /**
         * DeepSeek API Base URL（OpenAI 兼容）
         */
        private String baseUrl = "https://api.deepseek.com";

        /**
         * DeepSeek API Key
         */
        private String apiKey;

        /**
         * 模型名称
         */
        private String modelName = "deepseek-chat";

        /**
         * 最大输出 Token 数
         */
        private Integer maxTokens = 8192;

        /**
         * 温度参数
         */
        private Float temperature;

        /**
         * 最大重试次数
         */
        private Integer maxRetries = 3;

        /**
         * 请求超时时间（秒）
         */
        private Integer timeout = 120;
    }

    @Data
    public static class MiniMaxConfig {
        /**
         * MiniMax API Key
         */
        private String apiKey;

        /**
         * 模型名称，默认 M2-her
         */
        private String modelName = "M2-her";

        /**
         * 最大输出 Token 数（MiniMax M2-her 上限为 2048）
         */
        private Integer maxTokens = 2048;

        /**
         * 温度参数，控制随机性
         */
        private Float temperature = 0.7f;

        /**
         * Top-P 参数
         */
        private Float topP = 0.95f;

        /**
         * 请求超时时间（秒）
         */
        private Integer timeout = 120;

        /**
         * 最大重试次数
         */
        private Integer maxRetries = 3;
    }

    @Data
    public static class KimiConfig {

        private String baseUrl = "https://api.moonshot.cn/v1";
        /**
         * Kimi API Key
         */
        private String apiKey;

        /**
         * 模型名称，默认 kimi-k2.5
         */
        private String modelName = "kimi-k2";

        /**
         * 最大输出 Token 数（Kimi 默认 32768）
         */
        private Integer maxTokens = 32768;

        /**
         * 温度参数
         * Kimi k2.5 模型 temperature 固定为 1.0，不支持其他值
         */
        private Float temperature = 1.0f;

        /**
         * 请求超时时间（秒）
         */
        private Integer timeout = 120;

        /**
         * 最大重试次数
         */
        private Integer maxRetries = 3;
    }
}
