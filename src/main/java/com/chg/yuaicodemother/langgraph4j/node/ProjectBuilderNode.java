package com.chg.yuaicodemother.langgraph4j.node;

import com.chg.yuaicodemother.core.builder.VueProjectBuilder;
import com.chg.yuaicodemother.exception.BusinessException;
import com.chg.yuaicodemother.langgraph4j.state.WorkflowContext;
import com.chg.yuaicodemother.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;

import static com.chg.yuaicodemother.exception.ErrorCode.SYSTEM_ERROR;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 项目构建节点
 */
@Slf4j
public class ProjectBuilderNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 项目构建");
            String buildResultDir = context.getGeneratedCodeDir();
            try {
                VueProjectBuilder vueProjectBuilder = SpringContextUtil.getBean(VueProjectBuilder.class);
                boolean buildResult = vueProjectBuilder.buildProject(buildResultDir);
                if (buildResult) {
                    buildResultDir = buildResultDir + File.separator + "dist";
                    log.info("Vue项目构建成功，dist 目录: {}", buildResultDir);
                } else {
                    throw new BusinessException(SYSTEM_ERROR, "Vue 项目构建失败");
                }
            } catch (BusinessException e) {
                // 异常时返回项目原路径
                log.error("Vue 项目构建失败: {}", e.getMessage());
            }

            // 更新状态
            context.setCurrentStep("项目构建");
            context.setBuildResultDir(buildResultDir);
            log.info("项目构建完成，结果目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }
}
