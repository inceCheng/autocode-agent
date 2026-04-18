package com.chg.yuaicodemother.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.UUID;

import static com.chg.yuaicodemother.constant.kafkaConstant.TOPIC_NAME;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTaskProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendGenerationTask(String taskId, String userId, String prompt, String projectType, String traceId) {
        AiTaskEvent event = new AiTaskEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                traceId,
                new AiTaskEvent.TaskInfo(taskId, userId, projectType),
                new AiTaskEvent.Payload(prompt, Collections.emptyList())
        );

        try {
            String jsonMessage = objectMapper.writeValueAsString(event);
            // 使用 taskId 作为 Kafka 的 key，保证同一任务的重试或追加指令发送到同一个 Partition，保证顺序性
            kafkaTemplate.send(TOPIC_NAME, taskId, jsonMessage);
            // 日志记录，topic,key, value
            log.info("Send generation task to Kafka, topic: {}, key: {}, value: {}", TOPIC_NAME, taskId, jsonMessage);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
        }
    }
}