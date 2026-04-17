package com.chg.yuaicodemother.ai;

import com.chg.yuaicodemother.ai.tools.*;
import com.chg.yuaicodemother.constant.ChatModelNameConstant;
import com.chg.yuaicodemother.exception.BusinessException;
import com.chg.yuaicodemother.exception.ErrorCode;
import com.chg.yuaicodemother.model.enums.CodeGenTypeEnum;
import com.chg.yuaicodemother.model.service.ChatHistoryService;
import com.chg.yuaicodemother.utils.SpringContextUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Ai 服务创建工厂
 */
@Slf4j
@Configuration
public class AiCodeGeneratorServiceFactory {

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;


    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，appId: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId 获取服务（带缓存）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        String cachedKey = buildCacheKey(appId, codeGenType);
        // 先从缓存中获取，如果缓存中没有，则根据 appId 生成一个 ai 服务
        return serviceCache.get(cachedKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 创建新的 AI 服务实例
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId) {
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(100)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        StreamingChatModel kimiStreamChatModel = SpringContextUtil.getBean(ChatModelNameConstant.deepseekChatModel, StreamingChatModel.class);
        ChatModel kimiChatModel = SpringContextUtil.getBean(ChatModelNameConstant.kimiChatModel, ChatModel.class);
        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(kimiChatModel)
                .streamingChatModel(kimiStreamChatModel)
                .chatMemory(chatMemory)
                .build();
    }

    /**
     * 创建新的 AI 服务实例
     * 根据不同的代码生成类型配置相应的模型和工具
     *
     * @param appId       应用ID，用于构建独立的对话记忆
     * @param codeGenType 代码生成类型，决定使用哪种模型和配置
     * @return 配置好的 AI 服务实例
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        // 根据 appId 构建独立的对话记忆，设置最大消息数为100
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(100)
                .build();
        // 从数据库加载历史对话到记忆中，最多加载50条记录
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 50);
        // 根据代码生成类型选择不同的模型配置
        return switch (codeGenType) {
            // Vue 项目生成使用推理模型，并添加文件写入工具
            case VUE_PROJECT -> {
                StreamingChatModel kimiStreamChatModel = SpringContextUtil.getBean(ChatModelNameConstant.kimiStreamingChatModel, StreamingChatModel.class);
                // OutputGuardrailsConfig outputGuardrailsConfig = OutputGuardrailsConfig.builder().maxRetries(3).build();
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .streamingChatModel(kimiStreamChatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools(toolManager.getAllTools())
                        .maxSequentialToolsInvocations(25) // 最多连续调用 25 次工具
                        // .outputGuardrails(new RetryOutputGuardrail())
                        // .outputGuardrailsConfig(outputGuardrailsConfig)
                        // 当 ai 尝试调用不存在的工具时的处理策略
                        .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                                // 创建一个工具执行结果消息，告诉 ai 它尝试调用的工具不存在
                                toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                        ))
                        .build();
            }
            // HTML 和多文件生成使用默认模型
            case HTML, MULTI_FILE -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel kimiStreamChatModel = SpringContextUtil.getBean(ChatModelNameConstant.kimiStreamingChatModel, StreamingChatModel.class);
                ChatModel kimiChatModel = SpringContextUtil.getBean(ChatModelNameConstant.kimiChatModel, ChatModel.class);
                // OutputGuardrailsConfig outputGuardrailsConfig = OutputGuardrailsConfig.builder().maxRetries(3).build();
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(kimiChatModel)
                        .streamingChatModel(kimiStreamChatModel)
                        .chatMemory(chatMemory)
                        .tools(toolManager.getAllTools())
                        .maxSequentialToolsInvocations(15) // 最多连续调用 15 次工具
                        // .outputGuardrails(new RetryOutputGuardrail())
                        // .outputGuardrailsConfig(outputGuardrailsConfig)
                        .build();
            }
            // 不支持的代码生成类型抛出异常
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }


    /**
     * 默认提供一个 Bean
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L, CodeGenTypeEnum.VUE_PROJECT);
    }

    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType;
    }

}
