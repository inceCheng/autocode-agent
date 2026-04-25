package com.chg.yuaicodemother.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 *  实体类。
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ai_generation_task")
public class AiGenerationTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("taskId")
    private String taskId;

    @Column("appId")
    private Long appId;

    @Column("userId")
    private Long userId;

    @Column("projectType")
    private String projectType;

    private String status;

    @Column("retryCount")
    private Integer retryCount;

    @Column("maxRetryCount")
    private Integer maxRetryCount;

    @Column("errorCode")
    private String errorCode;

    @Column("errorMessage")
    private String errorMessage;

    @Column("requestPayload")
    private String requestPayload;

    @Column("resultContent")
    private String resultContent;

    @Column("createdAt")
    private LocalDateTime createdAt;

    @Column("updatedAt")
    private LocalDateTime updatedAt;

    @Column("startedAt")
    private LocalDateTime startedAt;

    @Column("finishedAt")
    private LocalDateTime finishedAt;

    private Integer version;

}
