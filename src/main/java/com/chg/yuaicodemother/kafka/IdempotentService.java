package com.chg.yuaicodemother.kafka;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 基于 Redis 的幂等服务，防止 Kafka 消息重复消费。
 * <p>
 * 幂等 key 由 taskId + seq 组成，使用 Redis SETNX（setIfAbsent）实现分布式去重，
 * TTL 24 小时自动过期，避免 key 无限膨胀。
 */
@Slf4j
@Service
public class IdempotentService {

    /**
     * Redis key 前缀
     */
    private static final String KEY_PREFIX = "chat:consume:";

    /**
     * 幂等 key 过期时间
     */
    private static final Duration KEY_TTL = Duration.ofHours(24);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 判断消息是否为首次消费。
     * <p>
     * 内部使用 Redis SETNX 语义：
     * - 首次设置成功 → 返回 true，表示该消息未被消费过
     * - 设置失败 → 返回 false，表示该消息已消费过，应丢弃
     *
     * @param taskId 任务 ID
     * @param seq    分片序号
     * @return true=首次消费可处理，false=重复消费应丢弃
     */
    public boolean tryConsume(String taskId, int seq) {
        String key = buildKey(taskId, seq);
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        // setIfAbsent 等效于 Redis SETNX：key 不存在时设置并返回 true；已存在返回 false
        Boolean setSuccess = ops.setIfAbsent(key, "1", KEY_TTL);
        if (Boolean.FALSE.equals(setSuccess)) {
            log.debug("重复消息已丢弃, taskId={}, seq={}", taskId, seq);
            return false;
        }
        return true;
    }

    /**
     * 构建幂等 key
     * <p>
     * 格式：chat:consume:{taskId}:{seq}
     */
    private String buildKey(String taskId, int seq) {
        return KEY_PREFIX + taskId + ":" + seq;
    }
}
