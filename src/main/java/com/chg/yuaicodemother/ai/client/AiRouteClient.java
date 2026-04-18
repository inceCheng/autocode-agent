package com.chg.yuaicodemother.ai.client;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chg.yuaicodemother.ai.config.AiRouteProperties;
import com.chg.yuaicodemother.model.enums.CodeGenTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiRouteClient {

    private final AiRouteProperties properties;

    public CodeGenTypeEnum getProjectType(String prompt) {

        String response = HttpUtil.createPost(properties.getUrl())
                .header("Content-Type", "application/json")
                .body(JSONUtil.createObj().set("prompt", prompt).toString())
                .timeout(5000) // 5秒超时
                .execute()
                .body();

        JSONObject jsonObject = JSONUtil.parseObj(response);
        String projectType = jsonObject.getStr("project_type");

        return CodeGenTypeEnum.getEnumByValue(projectType.toLowerCase());
    }
}