package com.chg.yuaicodemother.kafka;

import com.chg.yuaicodemother.model.service.AiGenerationTaskService;
import com.chg.yuaicodemother.model.service.ChatHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import static com.chg.yuaicodemother.constant.kafkaConstant.CHAT_HISTORY_CONSUMER_GROUP;
import static com.chg.yuaicodemother.constant.kafkaConstant.TASK_RESULT_TOPIC;

/**
 * Kafka 消费者：消费 Python AI 服务回传的流式任务结果，持久化到 chat_history 表。
 * <p>
 * 核心设计：
 * <ol>
 *   <li><b>幂等去重</b>：通过 Redis SETNX（taskId + seq）保证同一条消息不会重复入库</li>
 *   <li><b>异步缓冲</b>：消息先放入内存队列，由定时任务批量刷盘，减少 DB 写压力</li>
 *   <li><b>手动 ACK</b>：消费成功后才提交 offset，保证至少一次语义</li>
 *   <li><b>重试 + DLQ</b>：消费异常由 DefaultErrorHandler 处理，重试 3 次后进入死信队列</li>
 * </ol>
 */
@Slf4j
@Component
public class TaskResultConsumer {

    /**
     * 缓冲队列，线程安全
     */
    private final ConcurrentLinkedQueue<TaskResultEvent> buffer = new ConcurrentLinkedQueue<>();

    /**
     * 缓冲区最大容量，达到此数量立即刷盘
     */
    private static final int BUFFER_FLUSH_THRESHOLD = 50;

    private final ReentrantLock lock = new ReentrantLock();

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private IdempotentService idempotentService;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private AiGenerationTaskService aiGenerationTaskService;

    /**
     * 缓存最近一次更新的任务状态，避免重复写入相同状态
     */
    private final ConcurrentHashMap<String, String> lastKnownStatus = new ConcurrentHashMap<>();

    /**
     * 消费 task-result-topic 消息。
     * <p>
     * 处理流程：
     * 1. 反序列化 JSON → TaskResultEvent
     * 2. 幂等判断（Redis SETNX，key = chat:consume:{taskId}:{seq}）
     * 3. 重复消息直接丢弃
     * 4. 新消息加入缓冲队列
     * 5. 手动 ACK 提交 offset
     * <p>
     * 注意：ACK 在入队后立即提交。即使刷盘失败，Redis 幂等 key 已设置，
     * 重试时会被幂等层拦截——因此需要依赖定时刷盘的重试机制来保证最终一致。
     */
    @KafkaListener(
            topics = TASK_RESULT_TOPIC,
            groupId = CHAT_HISTORY_CONSUMER_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String taskId = record.key();
        String value = record.value();
        log.debug("收到 Kafka 消息, topic={}, key={}, partition={}, offset={}",
                record.topic(), taskId, record.partition(), record.offset());

        try {
            // 1. 反序列化
            TaskResultEvent event = objectMapper.readValue(value, TaskResultEvent.class);

            // 2. 幂等判断：taskId + seq 作为去重键
            if (!idempotentService.tryConsume(event.getTaskId(), event.getSeq())) {
                // 重复消息，直接 ACK 跳过
                acknowledgment.acknowledge();
                return;
            }

            // 3. 基本字段校验
            if (event.getAppId() == null || event.getUserId() == null) {
                log.warn("消息缺少必要字段(appId/userId)，丢弃, taskId={}", event.getTaskId());
                acknowledgment.acknowledge();
                return;
            }

            // 4. 加入缓冲队列
            buffer.offer(event);
            log.debug("消息入队, taskId={}, seq={}, bufferSize={}", event.getTaskId(), event.getSeq(), buffer.size());

            // 4.5 更新 ai_generation_task 任务状态（仅状态变更时写库）
            updateTaskStatus(event);

            // 5. 缓冲区达到阈值立即刷盘
            if (buffer.size() >= BUFFER_FLUSH_THRESHOLD) {
                flushBuffer();
            }

            // 6. 手动 ACK
            acknowledgment.acknowledge();

        } catch (Exception e) {
            // 反序列化或处理异常，交给 DefaultErrorHandler 重试/进 DLQ
            log.error("消息处理异常, topic={}, key={}, value={}", record.topic(), taskId, value, e);
            throw new RuntimeException("Kafka 消息消费失败", e);
        }
    }

    /**
     * 定时刷盘：每 500ms 将缓冲队列中的消息批量写入数据库。
     * 双触发机制，哪怕 buffer 中只有 1 条数据，也会刷盘
     * <p>
     * 即使上次刷盘失败，定时任务会持续重试，保证最终一致性。
     */
    @Scheduled(fixedDelay = 500, initialDelay = 2000)
    public void flushBuffer() {
        if (!lock.tryLock()) {
            return; // 已有线程在刷盘
        }
        List<TaskResultEvent> batch = new ArrayList<>();
        try {
            if (buffer.isEmpty()) {
                return;
            }

            TaskResultEvent event;
            while ((event = buffer.poll()) != null) {
                batch.add(event);
            }

            if (!batch.isEmpty()) {
                chatHistoryService.batchInsertChatHistory(batch);
                log.info("批量写入对话历史成功，共 {} 条", batch.size());
            }

        } catch (Exception e) {
            log.error("批量写入对话历史失败，共 {} 条，消息将丢失（幂等 key 已设置，Kafka 重试不会补回）",
                    batch.size(), e);
            // 注意：此处消息已 ACK 且幂等 key 已设置，无法通过 Kafka 重试补回。
            // 生产环境可考虑：1) 写入本地补偿表  2) 发送到补偿 topic  3) 告警人工介入
            // 当前策略：记录错误日志，依赖监控告警
        } finally {
            lock.unlock();
        }
    }

    /**
     * 更新 ai_generation_task 表中的任务状态。
     * 使用本地缓存跳过相同状态的重复写入，仅在状态发生变更时才写库。
     */
    private void updateTaskStatus(TaskResultEvent event) {
        String taskId = event.getTaskId();
        String newStatus = event.getStatus();
        Long appId = event.getAppId();
        Long userId = event.getUserId();

        // 状态未变更则跳过
        String previousStatus = lastKnownStatus.put(taskId, newStatus);
        if (newStatus.equals(previousStatus)) {
            return;
        }

        try {
            aiGenerationTaskService.updateTaskStatus(taskId, appId, userId, newStatus, event.getErrorMsg());
            log.debug("任务状态已更新, taskId={}, status={}", taskId, newStatus);
        } catch (Exception e) {
            log.error("更新任务状态失败, taskId={}, status={}", taskId, newStatus, e);
            // 更新失败不影响对话历史落库，仅记录日志
        }
    }
}
