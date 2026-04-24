package com.chg.yuaicodemother.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.HashMap;
import java.util.Map;

import static com.chg.yuaicodemother.constant.kafkaConstant.TASK_RESULT_DLT_TOPIC;

/**
 * Kafka 消费者配置。
 * <p>
 * 核心设计：
 * <ul>
 *   <li>手动 ACK：消费成功才提交 offset，避免消息丢失</li>
 *   <li>并发消费：concurrency 由配置控制，默认 3</li>
 *   <li>重试 + 死信队列：消费失败重试 3 次后进入 DLT topic</li>
 *   <li>String 反序列化：与现有 Producer 的 StringSerializer 对称</li>
 * </ul>
 */
@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:chat-history-consumer-group}")
    private String groupId;

    @Value("${spring.kafka.listener.concurrency:3}")
    private Integer concurrency;

    /**
     * 消费者工厂
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // 手动提交 offset，保证至少一次语义
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // 首次消费从最早 offset 开始，后续按已提交 offset 继续
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // 单次 poll 最大拉取条数，控制批次大小
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        // 两次 poll 最大间隔，超时将触发 rebalance
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        // 心跳间隔
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka 监听器容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        // 手动 ACK 模式
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    /**
     * 消费异常处理器：指数退避重试 3 次，失败后发送到死信队列
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // 指数退避：初始 1s，倍数 2，最多 3 次
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(
                (consumerRecord, exception) -> {
                    // 重试耗尽后进入死信队列
                    log.error("消息消费重试耗尽，发送至死信队列, topic={}, key={}, value={}",
                            consumerRecord.topic(), consumerRecord.key(), consumerRecord.value(), exception);
                    kafkaTemplate.send(TASK_RESULT_DLT_TOPIC,
                            String.valueOf(consumerRecord.key()),
                            String.valueOf(consumerRecord.value()));
                },
                backOff
        );

        // 反序列化等不可恢复异常不重试，直接进 DLT
        handler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
                com.fasterxml.jackson.core.JsonProcessingException.class
        );

        return handler;
    }
}
