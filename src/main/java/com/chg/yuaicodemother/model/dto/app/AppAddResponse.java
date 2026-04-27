package com.chg.yuaicodemother.model.dto.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建应用请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppAddResponse implements Serializable {

    /**
     * 应用 taskId
     */
    private String taskId;
    /**
     * appId
     */
    private Long appId;

    /**
     * 应用 token
     */
    private String token;

    /**
     * 本次任务创建的目标版本 id
     */
    private Long targetVersionId;


    @Serial
    private static final long serialVersionUID = 1L;
}
