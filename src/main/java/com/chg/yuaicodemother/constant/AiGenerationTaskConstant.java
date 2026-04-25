package com.chg.yuaicodemother.constant;

/**
 * @Author: chg
 * @Date: 2026/4/25 15:28
 * @Description: AI 生成任务常量
 */
public interface AiGenerationTaskConstant {
    String STATUS_KEY_PREFIX = "ai:task:status:";
    String PENDING = "PENDING";
    String PROCESSING = "PROCESSING";
    String SUCCESS = "SUCCESS";
    String WAITING_RETRY = "WAITING_RETRY";
    String INTERRUPTED = "INTERRUPTED";
    String FAILED = "FAILED";
    String CANCELLED = "CANCELLED";
    int RETRY_COUNT = 0;
    int MAX_RETRY_COUNT = 3;
    int DEFAULT_VERSION = 0;
    int TASK_TIMEOUT = 24;

}
