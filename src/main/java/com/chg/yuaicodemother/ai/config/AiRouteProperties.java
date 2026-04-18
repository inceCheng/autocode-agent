package com.chg.yuaicodemother.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.route")
public class AiRouteProperties {

    /**
     * AI 路由接口地址
     */
    private String url;
}