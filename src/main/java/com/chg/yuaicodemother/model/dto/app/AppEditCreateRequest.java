package com.chg.yuaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 创建定点修改任务请求。
 */
@Data
public class AppEditCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long appId;

    private Long baseVersionId;

    private String instruction;

    private String scope = "single";

    private List<SelectedElementInfo> selectedElements;
}
