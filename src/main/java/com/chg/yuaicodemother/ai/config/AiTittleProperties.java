package com.chg.yuaicodemother.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.tittle")
public class AiTittleProperties {

    /**
     * AI 标题接口地址
     */
    private String url;
}