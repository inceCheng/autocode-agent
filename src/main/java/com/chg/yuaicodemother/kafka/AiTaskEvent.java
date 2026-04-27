package com.chg.yuaicodemother.kafka;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AiTaskEvent {
    private String eventId;     // 消息全局唯一ID，用于防重
    private Long timestamp;     // 毫秒级时间戳
    private String traceId;     // 链路追踪 ID
    private TaskInfo task;      // 任务核心信息
    private Payload payload;    // 具体载荷

    @Data
    @Builder
    public static class TaskInfo {
        private String taskId;
        private String userId;
        private String appId;
        private String previewPath;
        private String projectType; // 智能路由结果: HTML/VUE_PROJECT 等
        private String taskType; // GENERATE / EDIT
        private String baseVersionId;
        private String targetVersionId;
        private String baseSourcePath;
        private String targetSourcePath;
    }

    @Data
    @Builder
    public static class Payload {
        private String prompt;
        private List<Object> contextMessages;
        private List<Object> selectedElements;
        private String scope;
    }
}
