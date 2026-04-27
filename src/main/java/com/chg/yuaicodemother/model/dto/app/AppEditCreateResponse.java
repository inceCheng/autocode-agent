package com.chg.yuaicodemother.model.dto.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建定点修改任务响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppEditCreateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String taskId;

    private Long appId;

    private String token;

    private Long baseVersionId;

    private Long targetVersionId;

    private String status;
}
