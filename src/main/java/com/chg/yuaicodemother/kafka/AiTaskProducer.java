package com.chg.yuaicodemother.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.chg.yuaicodemother.constant.AiGenerationTaskConstant.TASK_TYPE_EDIT;
import static com.chg.yuaicodemother.constant.AiGenerationTaskConstant.TASK_TYPE_GENERATE;
import static com.chg.yuaicodemother.constant.kafkaConstant.TOPIC_NAME;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTaskProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendGenerationTask(String taskId, String userId, String appId, String prompt, String projectType, String traceId, String previewPath) {
        sendGenerationTask(taskId, userId, appId, prompt, projectType, traceId, previewPath, null, null);
    }

    public void sendGenerationTask(String taskId, String userId, String appId, String prompt, String projectType,
                                   String traceId, String previewPath, Long targetVersionId, String targetSourcePath) {
        AiTaskEvent event = new AiTaskEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                traceId,
                new AiTaskEvent.TaskInfo(
                        taskId,
                        userId,
                        appId,
                        previewPath,
                        projectType,
                        TASK_TYPE_GENERATE,
                        null,
                        targetVersionId == null ? null : String.valueOf(targetVersionId),
                        null,
                        targetSourcePath
                ),
                new AiTaskEvent.Payload(prompt, Collections.emptyList(), Collections.emptyList(), "single")
        );

        sendEvent(taskId, event);
    }

    public void sendEditTask(String taskId, String userId, String appId, String instruction, String projectType,
                             String traceId, String previewPath, Long baseVersionId, Long targetVersionId,
                             String baseSourcePath, String targetSourcePath, List<Object> selectedElements,
                             String scope) {
        AiTaskEvent event = new AiTaskEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                traceId,
                new AiTaskEvent.TaskInfo(
                        taskId,
                        userId,
                        appId,
                        previewPath,
                        projectType,
                        TASK_TYPE_EDIT,
                        String.valueOf(baseVersionId),
                        String.valueOf(targetVersionId),
                        baseSourcePath,
                        targetSourcePath
                ),
                new AiTaskEvent.Payload(
                        instruction,
                        Collections.emptyList(),
                        selectedElements == null ? Collections.emptyList() : selectedElements,
                        scope
                )
        );

        sendEvent(taskId, event);
    }

    private void sendEvent(String taskId, AiTaskEvent event) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(event);
            // 使用 taskId 作为 Kafka 的 key，保证同一任务的重试或追加指令发送到同一个 Partition，保证顺序性
            kafkaTemplate.send(TOPIC_NAME, taskId, jsonMessage);
            // 日志记录，topic,key, value
            log.info("Send AI task to Kafka, topic: {}, key: {}, value: {}", TOPIC_NAME, taskId, jsonMessage);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
        }
    }
}
