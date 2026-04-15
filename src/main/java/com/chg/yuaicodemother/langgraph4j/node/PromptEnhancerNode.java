package com.chg.yuaicodemother.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chg.yuaicodemother.langgraph4j.state.ImageResource;
import com.chg.yuaicodemother.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.Collections;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 提示词增强节点
 */
@Slf4j
public class PromptEnhancerNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 提示词增强");

            String originalPrompt = context.getOriginalPrompt();
            String imageListStr = context.getImageListStr();
            List<ImageResource> imageList = context.getImageList();
            StringBuilder enhancePromptBuilder = new StringBuilder();
            enhancePromptBuilder.append(originalPrompt);
            if (CollUtil.isNotEmpty(imageList) || StrUtil.isNotBlank(imageListStr)) {
                enhancePromptBuilder.append("\n\n## 可用素材资源\n");
                enhancePromptBuilder.append("请在生成网站时使用以下图片素材，将这些图片合理的嵌入到网站的相应位置中。\n");

                if (CollUtil.isNotEmpty(imageList)) {
                    for (ImageResource image : imageList) {
                        enhancePromptBuilder.append("- ")
                                .append(image.getCategory().getText())
                                .append(": ")
                                .append(image.getDescription())
                                .append("（")
                                .append(image.getUrl())
                                .append("）\n");
                    }
                } else {
                    enhancePromptBuilder.append(imageListStr);
                }
            }

            // 更新状态
            context.setCurrentStep("提示词增强");
            context.setEnhancedPrompt(enhancePromptBuilder.toString());
            log.info("提示词增强完成");
            return WorkflowContext.saveContext(context);
        });
    }
}
