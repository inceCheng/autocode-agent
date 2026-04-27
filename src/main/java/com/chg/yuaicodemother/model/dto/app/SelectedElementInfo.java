package com.chg.yuaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 前端在预览 iframe 中选中的元素上下文。
 */
@Data
public class SelectedElementInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String nodeId;

    private String tagName;

    private String id;

    private String className;

    private String text;

    private String textContent;

    private String selector;

    private String outerHTML;

    private String pagePath;

    private Map<String, Object> computedStyle;

    private Map<String, Object> rect;
}
