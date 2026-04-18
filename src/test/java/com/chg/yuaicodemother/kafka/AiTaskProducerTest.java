package com.chg.yuaicodemother.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
// 1. 去掉 @EmbeddedKafka，在 properties 中直接指定本机真实 Kafka 的地址
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=192.168.31.9:9092",
        // 2. 关键：真实环境可能有以前的脏数据，使用 latest 确保只监听测试启动后发出的新消息
        "spring.kafka.consumer.auto-offset-reset=latest",
        // 3. 强制消费者使用 String 反序列化，防止双重序列化报错
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer"
})
class AiTaskProducerTest {

    @Autowired
    private AiTaskProducer aiTaskProducer;

    @Autowired
    private ObjectMapper objectMapper;

    // 定义阻塞队列，接收真实 Kafka 推送过来的消息
    private static final BlockingQueue<ConsumerRecord<String, String>> records = new LinkedBlockingQueue<>();

    /**
     * 4. 模拟一个消费者。
     * 关键改动：将 groupId 改为一个专门的测试组名 (如 test-group-local-dev)
     */
    @KafkaListener(topics = "agent-generation-tasks", groupId = "test-group-local-dev")
    public void listen(ConsumerRecord<String, String> record) {
        log.info("本地真实 Kafka 监听到消息，Key: {}, Value: {}", record.key(), record.value());
        records.add(record);
    }

    @Test
    public void testSendGenerationTask_ShouldSendMessageToKafka() throws Exception {
        // 由于是真实环境，队列里可能有别的消息。每次测试前清空本地队列，防止干扰
        records.clear();

        // ===== 1. 准备测试数据 =====
        String taskId = "task-001";
        String userId = "user-001";
        String prompt = "生成一个简单的 HTML 网页";
        String projectType = "HTML";
        String traceId = "trace-001";

        // 为了防止消费者还没连上，生产者就发了消息，稍微休眠 1 秒钟（真实环境联调常用小技巧）
        Thread.sleep(1000);

        // ===== 2. 调用方法 (生产者向真实 Kafka 发送消息) =====
        aiTaskProducer.sendGenerationTask(taskId, userId, prompt, projectType, traceId);

        // ===== 3. 从队列中拉取消息 (等待最多 10 秒) =====
        ConsumerRecord<String, String> receivedRecord = records.poll(10, TimeUnit.SECONDS);

        // ===== 4. 断言验证 =====
        assertNotNull(receivedRecord, "在 10 秒内没有从本机 Kafka 中读到消息！");

        assertEquals(taskId, receivedRecord.key(), "Kafka 的 Key 应该是 taskId");

        String jsonPayload = receivedRecord.value();
        assertNotNull(jsonPayload);
        assertTrue(jsonPayload.contains(taskId), "消息体中应该包含 taskId");
        assertTrue(jsonPayload.contains(prompt), "消息体中应该包含 prompt");
        assertTrue(jsonPayload.contains(projectType), "消息体中应该包含 projectType");

        // 反序列化验证
        AiTaskEvent receivedEvent = objectMapper.readValue(jsonPayload, AiTaskEvent.class);
        assertEquals(traceId, receivedEvent.getTraceId());
    }
}