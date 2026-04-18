package com.chg.yuaicodemother.utils;

import lombok.extern.slf4j.Slf4j;

/**
 * 分布式任务 ID 生成器 (基于 Twitter Snowflake 算法)
 * 结构: 0(符号位) + 41位时间戳 + 10位工作机器ID + 12位序列号
 */
@Slf4j
public class TaskIdGenerator {

    /**
     * 开始时间截 (2020-01-01)
     * 一旦确定不能随意更改，否则会导致 ID 重复。41位时间戳可使用69年。
     */
    private final long twepoch = 1577808000000L;

    /**
     * 机器 ID 所占的位数 (10位，支持 1024 个节点)
     */
    private final long workerIdBits = 10L;

    /**
     * 支持的最大机器 ID，结果是 1023
     * (这个移位算法可以很快的计算出几位二进制数所能表示的最大十进制数)
     */
    private final long maxWorkerId = ~(-1L << workerIdBits);

    /**
     * 序列在 ID 中占的位数 (12位，每毫秒每节点可生成 4096 个 ID)
     */
    private final long sequenceBits = 12L;

    /**
     * 机器 ID 向左移 12 位
     */
    private final long workerIdShift = sequenceBits;

    /**
     * 时间截向左移 22 位 (10位机器ID + 12位序列号)
     */
    private final long timestampLeftShift = sequenceBits + workerIdBits;

    /**
     * 生成序列的掩码，这里为 4095 (0b111111111111)
     */
    private final long sequenceMask = ~(-1L << sequenceBits);

    /**
     * 工作机器 ID (0~1023)
     */
    private long workerId;

    /**
     * 毫秒内序列 (0~4095)
     */
    private long sequence = 0L;

    /**
     * 上次生成 ID 的时间截
     */
    private long lastTimestamp = -1L;

    /**
     * 构造函数
     * @param workerId 工作机器 ID (0~1023)
     */
    public TaskIdGenerator(long workerId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker Id 不能大于 %d 或小于 0", maxWorkerId));
        }
        this.workerId = workerId;
    }

    /**
     * 核心发号方法 (线程安全)
     * @return 唯一且趋势递增的 TaskId
     */
    public synchronized long nextId() {
        long timestamp = timeGen();

        // 企业级核心逻辑：解决时钟回拨问题
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                // 1. 如果回拨时间非常短（5毫秒内），可以选择等待
                try {
                    wait(offset << 1);
                    timestamp = timeGen();
                    if (timestamp < lastTimestamp) {
                        throw new RuntimeException("时钟回拨恢复失败");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("等待时钟恢复被中断", e);
                }
            } else {
                // 2. 如果回拨时间较长，直接抛出异常，或降级到备用 WorkerId
                log.error("时钟发生回拨，拒绝生成 ID {} 毫秒", offset);
                throw new RuntimeException(String.format("时钟回拨，拒绝为 %d 毫秒生成 ID", offset));
            }
        }

        // 如果是同一时间生成的，则进行毫秒内序列
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            // 毫秒内序列溢出 (达到 4096)
            if (sequence == 0) {
                // 阻塞到下一个毫秒,获得新的时间戳
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 时间戳改变，毫秒内序列重置
            sequence = 0L;
        }

        // 上次生成 ID 的时间截
        lastTimestamp = timestamp;

        // 移位并通过或运算拼到一起组成 64 位的 ID
        return ((timestamp - twepoch) << timestampLeftShift)
                | (workerId << workerIdShift)
                | sequence;
    }

    /**
     * 阻塞到下一个毫秒，直到获得新的时间戳
     */
    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 返回以毫秒为单位的当前时间
     */
    protected long timeGen() {
        return System.currentTimeMillis();
    }

    // --- 测试运行 ---
    public static void main(String[] args) {
        // 假设当前节点分配的 Worker ID 是 1
        TaskIdGenerator generator = new TaskIdGenerator(1);

        for (int i = 0; i < 10; i++) {
            System.out.println(generator.nextId());
        }
    }
}