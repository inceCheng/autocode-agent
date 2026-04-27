package com.chg.yuaicodemother.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用版本展示对象。
 */
@Data
public class AppVersionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long appId;

    private Long parentVersionId;

    private Integer versionNo;

    private String taskId;

    private String codeGenType;

    private String sourcePath;

    private String manifestPath;

    private String previewUrl;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
