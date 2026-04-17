package com.chg.yuaicodemother.langgraph4j.node;

import cn.hutool.core.util.StrUtil;
import com.chg.yuaicodemother.constant.AppConstant;
import com.chg.yuaicodemother.core.AiCodeGeneratorFacade;
import com.chg.yuaicodemother.langgraph4j.model.QualityResult;
import com.chg.yuaicodemother.langgraph4j.state.WorkflowContext;
import com.chg.yuaicodemother.model.entity.App;
import com.chg.yuaicodemother.model.enums.CodeGenTypeEnum;
import com.chg.yuaicodemother.model.service.AppService;
import com.chg.yuaicodemother.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码生成节点
 */
@Slf4j
public class CodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码生成");
            // 使用增强提示词作为发给 AI 的用户消息
            String userMessage = buildUserMessage(context);
            CodeGenTypeEnum generationType = context.getGenerationType();
            // 获取 AI 代码生成外观服务
            AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            log.info("开始代码生成，生成类型: {}, 生成类用户消息型: {}", generationType.getValue(), userMessage);
            AppService appService = SpringContextUtil.getBean(AppService.class);
            App app = appService.getById(context.getAppId());
            Long appId = app.getId();
            String previewPath = app.getPreviewPath();
            if (StrUtil.isBlank(previewPath)) {
                LocalDateTime createTime = app.getCreateTime();
                // 定义格式 yyyy/MM/dd
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
                // 格式化获取字符串
                previewPath = createTime.format(formatter);
            }
            // 通过 appID 获取路径
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(userMessage, generationType, appId);
            codeStream.blockLast(Duration.ofMinutes(30));
            String generatedCodeDir = String.format("%s%s%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, previewPath, generationType.getValue(), appId);
            log.info("代码生成完成，目录: {}", generatedCodeDir);
            // 更新状态
            context.setCurrentStep("代码生成");
            context.setGeneratedCodeDir(generatedCodeDir);
            log.info("代码生成完成，目录: {}", generatedCodeDir);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 构造用户消息，如果存在质检失败结果则添加错误修复信息
     */
    private static String buildUserMessage(WorkflowContext context) {
        String userMessage = context.getEnhancedPrompt();
        // 检查是否存在质检失败结果
        QualityResult qualityResult = context.getQualityResult();
        if (isQualityCheckFailed(qualityResult)) {
            // 直接将错误修复信息作为新的提示词（起到了修改的作用）
            userMessage = buildErrorFixPrompt(qualityResult);
        }
        return userMessage;
    }

    /**
     * 判断质检是否失败
     */
    private static boolean isQualityCheckFailed(QualityResult qualityResult) {
        return qualityResult != null &&
                !qualityResult.getIsValid() &&
                qualityResult.getErrors() != null &&
                !qualityResult.getErrors().isEmpty();
    }

    /**
     * 构造错误修复提示词
     */
    private static String buildErrorFixPrompt(QualityResult qualityResult) {
        StringBuilder errorInfo = new StringBuilder();
        errorInfo.append("\n\n## 上次生成的代码存在以下问题，请修复：\n");
        // 添加错误列表
        qualityResult.getErrors().forEach(error ->
                errorInfo.append("- ").append(error).append("\n"));
        // 添加修复建议（如果有）
        if (qualityResult.getSuggestions() != null && !qualityResult.getSuggestions().isEmpty()) {
            errorInfo.append("\n## 修复建议：\n");
            qualityResult.getSuggestions().forEach(suggestion ->
                    errorInfo.append("- ").append(suggestion).append("\n"));
        }
        errorInfo.append("\n请根据上述问题和建议重新生成代码，确保修复所有提到的问题。");
        return errorInfo.toString();
    }


}
