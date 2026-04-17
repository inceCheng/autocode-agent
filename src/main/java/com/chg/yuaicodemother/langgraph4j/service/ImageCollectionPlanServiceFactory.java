package com.chg.yuaicodemother.langgraph4j.service;

import com.chg.yuaicodemother.constant.ChatModelNameConstant;
import com.chg.yuaicodemother.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图片采集计划服务工厂
 */
@Configuration
public class ImageCollectionPlanServiceFactory {

    @Bean
    public ImageCollectionPlanService createImageCollectionPlanService() {
        ChatModel chatModel = SpringContextUtil.getBean(ChatModelNameConstant.kimiChatModel, ChatModel.class);
        return AiServices.builder(ImageCollectionPlanService.class)
                .chatModel(chatModel)
                .build();
    }
}
