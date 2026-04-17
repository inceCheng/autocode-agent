package com.chg.yuaicodemother.langgraph4j.node;

import com.chg.yuaicodemother.ai.AiCodeGenTypeRoutingServiceFactory;
import com.chg.yuaicodemother.langgraph4j.state.WorkflowContext;
import com.chg.yuaicodemother.model.enums.CodeGenTypeEnum;
import com.chg.yuaicodemother.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 智能路由节点
 */
@Slf4j
public class RouterNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 智能路由");

            CodeGenTypeEnum generationType = CodeGenTypeEnum.HTML;
            try {
                // 获取 AI 路由服务
                AiCodeGenTypeRoutingServiceFactory factory = SpringContextUtil.getBean(AiCodeGenTypeRoutingServiceFactory.class);
                generationType = factory.createAiCodeGenTypeRoutingService().routeCodeGenType(context.getOriginalPrompt());
                log.info("智能路由结果: {} ({})", generationType.getValue(), generationType.getText());
            } catch (Exception e) {
                log.error("智能路由失败，默认选择 HTML 类型: {}", e.getMessage());
            }
            // 更新状态
            context.setCurrentStep("智能路由");
            context.setGenerationType(generationType);
            log.info("路由决策完成，选择类型: {}", generationType.getText());
            return WorkflowContext.saveContext(context);
        });
    }
}
