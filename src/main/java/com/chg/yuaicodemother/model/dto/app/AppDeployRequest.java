package com.chg.yuaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 部署请求类
 */
@Data
public class AppDeployRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    private static final long serialVersionUID = 1L;
}
