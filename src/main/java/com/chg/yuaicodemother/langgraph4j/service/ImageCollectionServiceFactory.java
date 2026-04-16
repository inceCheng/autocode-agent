package com.chg.yuaicodemother.langgraph4j.service;

import com.chg.yuaicodemother.constant.ChatModelNameConstant;
import com.chg.yuaicodemother.langgraph4j.tools.ImageSearchTool;
import com.chg.yuaicodemother.langgraph4j.tools.LogoGeneratorTool;
import com.chg.yuaicodemother.langgraph4j.tools.MermaidDiagramTool;
import com.chg.yuaicodemother.langgraph4j.tools.UndrawIllustrationTool;
import com.chg.yuaicodemother.utools.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Slf4j
@Configuration
public class ImageCollectionServiceFactory {
    @Resource
    private ImageSearchTool imageSearchTool;

    @Resource
    private UndrawIllustrationTool undrawIllustrationTool;

    @Resource
    private MermaidDiagramTool mermaidDiagramTool;

    @Resource
    private LogoGeneratorTool logoGeneratorTool;

    /**
     * 创建图片收集 AI 服务
     */
    @Bean
    @Scope("prototype")
    public ImageCollectionService createImageCollectionService() {
        ChatModel chatModel = SpringContextUtil.getBean(ChatModelNameConstant.kimiChatModel, ChatModel.class);
        return AiServices.builder(ImageCollectionService.class)
                .chatModel(chatModel)
                .tools(
                        imageSearchTool,
                        undrawIllustrationTool,
                        mermaidDiagramTool,
                        logoGeneratorTool
                )
                .build();
    }
}
