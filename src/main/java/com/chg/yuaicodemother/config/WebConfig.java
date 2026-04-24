package com.chg.yuaicodemother.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/v1/static/**")
                .addResourceLocations("file:/Users/c/workspace/coding/yu-ai-code-mother/tmp/code_deploy/")
                .resourceChain(true) // 必须开启 resourceChain
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = super.getResource(resourcePath, location);
                        // 核心逻辑：如果找到了资源，且它是个目录，就去里面找 index.html
                        if (resource != null && resource.getFile().isDirectory()) {
                            Resource index = location.createRelative(resourcePath + "/index.html");
                            if (index.exists() && index.isReadable()) {
                                return index;
                            }
                        }
                        return resource;
                    }
                });
    }
}