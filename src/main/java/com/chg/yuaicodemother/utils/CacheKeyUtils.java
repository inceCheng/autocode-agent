package com.chg.yuaicodemother.utils;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.core.util.ClassUtil;

public class CacheKeyUtils {

    /**
     * 根据业务前缀和对象生成缓存 key
     *
     * @param prefix 业务前缀 (如: "user:detail")
     * @param obj    要生成 key 的参数/对象
     * @return 优化后的缓存 key
     */
    public static String generateKey(String prefix, Object obj) {
        if (obj == null) {
            return prefix + ":null";
        }

        // 1. 如果是基本类型、包装类或 String，直接拼接，保持极佳的可读性
        if (ClassUtil.isBasicType(obj.getClass()) || obj instanceof String || obj instanceof Number) {
            return prefix + ":" + obj.toString();
        }

        // 2. 复杂对象：转 JSON
        String jsonStr = JSONUtil.toJsonStr(obj);

        // 3. 换用更安全的 SHA-256（或保持 MD5，视系统要求而定）
        // 截取前 16 位通常对于缓存隔离已经足够，并且可以节省 Redis 内存
        String hash = DigestUtil.sha256Hex(jsonStr).substring(0, 16);

        // 4. 拼装最终 Key：前缀 : 类名 : Hash值
        return prefix + ":" + obj.getClass().getSimpleName() + ":" + hash;
    }
}