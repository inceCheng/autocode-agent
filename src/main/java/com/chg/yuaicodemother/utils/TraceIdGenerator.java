package com.chg.yuaicodemother.utils;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分布式链路追踪 TraceId 生成器 (APM 标准型)
 * 格式: IP(Hex) + 时间戳(毫秒) + 循环自增序列 + 线程ID
 */
public class TraceIdGenerator {

    // 1. IP地址的十六进制 (静态常量，类加载时初始化，避免高并发下的性能损耗)
    private static final String IP_HEX = getIpHex();

    // 2. 自增序列 (基于 CAS 的无锁并发控制)
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1000);
    private static final int MAX_SEQ = 9000;
    private static final int MIN_SEQ = 1000;

    /**
     * 生成 TraceId 的核心方法
     * @return 全局唯一的 TraceId 字符串
     */
    public static String generateTraceId() {
        // 预分配 StringBuilder 容量 (8位IP + 13位时间戳 + 4位序列 + 预估4位线程ID = 约29位)
        // 预分配可以避免扩容带来的性能损耗
        StringBuilder traceId = new StringBuilder(32);

        // 1. 拼接 IP 十六进制 (8位)
        traceId.append(IP_HEX);

        // 2. 拼接当前时间戳 (毫秒级，13位)
        traceId.append(System.currentTimeMillis());

        // 3. 拼接自增序列 (循环递增，4位)
        traceId.append(getNextSeq());

        // 4. 拼接当前线程 ID
        traceId.append(Thread.currentThread().getId());

        return traceId.toString();
    }

    /**
     * 获取下一个序列号 (CAS 无锁操作保证并发安全)
     */
    private static int getNextSeq() {
        for (;;) {
            int current = SEQUENCE.get();
            // 当序列达到 9000 时，重置回 1000
            int next = current >= MAX_SEQ ? MIN_SEQ : current + 1;
            // 只有当当前值未被其他线程修改时，才更新为 next 并返回
            if (SEQUENCE.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    /**
     * 将本机的 IP 地址转换为十六进制字符串 (仅在类初始化时执行一次)
     */
    private static String getIpHex() {
        try {
            InetAddress address = InetAddress.getLocalHost();
            byte[] ipBytes = address.getAddress();
            StringBuilder hexString = new StringBuilder(8);
            
            for (byte b : ipBytes) {
                // 将 byte 转换为无符号的整数，并转为 16 进制
                String hex = Integer.toHexString(b & 0xFF);
                // 如果是一位数，前面补 0 (例如 0x1 变成 "01")
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            // 兜底策略：如果获取本地 IP 失败，使用 127.0.0.1 的十六进制 7f000001
            return "7f000001";
        }
    }

    // 测试运行
    public static void main(String[] args) {
        // 模拟高并发生成测试
        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                System.out.println(Thread.currentThread().getName() + " -> " + generateTraceId());
            }).start();
        }
    }
}