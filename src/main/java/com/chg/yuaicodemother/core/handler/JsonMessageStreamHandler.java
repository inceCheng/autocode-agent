package com.chg.yuaicodemother.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chg.yuaicodemother.ai.model.message.*;
import com.chg.yuaicodemother.ai.tools.BaseTool;
import com.chg.yuaicodemother.ai.tools.ToolManager;
import com.chg.yuaicodemother.constant.AppConstant;
import com.chg.yuaicodemother.core.builder.VueProjectBuilder;
import com.chg.yuaicodemother.model.entity.User;
import com.chg.yuaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.chg.yuaicodemother.model.enums.CodeGenTypeEnum;
import com.chg.yuaicodemother.model.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ToolManager toolManager;

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, String previewPath, User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字串
                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史
                    try {
                        String aiResponse = chatHistoryStringBuilder.toString();
                        chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    } catch (Exception e) {
                        log.error("保存 AI 对话历史失败, appId: {}", appId, e);
                        // 保存失败不影响主流程
                    }
                    // 构建完整路径
                    // 异步构造 Vue 项目
                    String vueProjectPath = String.format("%s/%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, previewPath, CodeGenTypeEnum.VUE_PROJECT, appId);
                    vueProjectBuilder.buildProjectAsync(vueProjectPath);
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    try {
                        String errorMessage = "AI回复失败: " + error.getMessage();
                        chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    } catch (Exception e) {
                        log.error("保存 AI 错误对话历史失败, appId: {}", appId, e);
                        // 保存失败不影响主流程
                    }
                });
    }

    /**
     * 解析并收集 TokenStream 数据
     * 处理不同类型的消息流，包括AI响应、工具请求和工具执行结果
     *
     * @param chunk                    接收到的消息块数据
     * @param chatHistoryStringBuilder 用于构建聊天历史记录的字符串构建器
     * @param seenToolIds              已处理的工具ID集合，用于去重
     * @return 根据消息类型返回相应的处理结果字符串
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 解析 JSON 消息块为 StreamMessage 对象
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        // 根据消息类型获取对应的枚举值
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        // 根据不同的消息类型进行相应处理
        switch (typeEnum) {
            // 处理 AI 响应消息
            case AI_RESPONSE -> {
                // 将消息块解析为 AI 响应消息对象
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并返回工具信息
                    seenToolIds.add(toolId);
                    // 根据工具名称获取工具实例
                    BaseTool tool = toolManager.getTool(toolName);
                    // 返回格式化的工具调用信息
                    return tool.generateToolRequestResponse();
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                String toolName = toolExecutedMessage.getName();
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                // 根据工具名称获取工具实例并生成相应的结果格式
                BaseTool tool = toolManager.getTool(toolName);
                String result = tool.generateToolExecutedResult(jsonObject);
                // 输出前端和要持久化的内容
                String output = String.format("\n\n%s\n\n", result);
                chatHistoryStringBuilder.append(output);
                return output;
            }

            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }
}
