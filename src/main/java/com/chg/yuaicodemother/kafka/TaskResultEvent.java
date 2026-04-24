package com.chg.yuaicodemother.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Python AI 服务回传的任务结果事件，对应 task-result-topic 中的消息格式。
 * 字段名使用 camelCase（与 Python 端 Pydantic alias 一致）。
 *
 * @see com.chg.yuaicodemother.constant.kafkaConstant#TASK_RESULT_TOPIC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResultEvent {

    /**
     * 任务唯一标识，同时作为 Kafka 消息 key（保证同任务分到同一 partition）
     */
    @JsonProperty("taskId")
    private String taskId;

    /**
     * 应用 ID
     */
    @JsonProperty("appId")
    private Long appId;

    /**
     * 用户 ID
     */
    @JsonProperty("userId")
    private Long userId;

    /**
     * 任务状态：STREAMING / SUCCESS / FAILED / INTERRUPTED
     */
    @JsonProperty("status")
    private String status;

    /**
     * 消息类型：ai / user / system
     */
    @JsonProperty("messageType")
    private String messageType;

    /**
     * 本次生成的文本片段
     */
    @JsonProperty("content")
    private String content;

    /**
     * 分片序号，从 0 开始递增，用于幂等 key 和前端排序
     */
    @JsonProperty("seq")
    private Integer seq;

    /**
     * 是否为最后一条消息
     */
    @JsonProperty("isEnd")
    private Boolean isEnd;

    /**
     * 错误信息（仅 FAILED 状态时有值）
     */
    @JsonProperty("errorMsg")
    private String errorMsg;

    /**
     * 毫秒级时间戳
     */
    @JsonProperty("timestamp")
    private Long timestamp;
}
