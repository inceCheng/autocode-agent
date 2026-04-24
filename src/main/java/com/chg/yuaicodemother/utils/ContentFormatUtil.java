package com.chg.yuaicodemother.utils;

/**
 * 内容格式化工具，在入库前对 LLM 生成的文本做可读性优化。
 * <p>
 * 当前处理：
 * <ul>
 *   <li>去除多余连续空行（3 个及以上换行 → 2 个换行）</li>
 *   <li>去除首尾空白</li>
 * </ul>
 */
public class ContentFormatUtil {

    /**
     * 匹配 3 个及以上连续换行（含中间可能夹带的空白字符）
     */
    private static final String EXCESSIVE_NEWLINES_REGEX = "(\\r?\\n\\s*){3,}";

    /**
     * 替换为 2 个换行
     */
    private static final String NORMALIZED_NEWLINES = "\n\n";

    private ContentFormatUtil() {
    }

    /**
     * 格式化内容，去除多余换行并修剪首尾空白。
     *
     * @param content 原始内容，可能为 null
     * @return 格式化后的内容，null 输入返回空字符串
     */
    public static String format(String content) {
        if (content == null) {
            return "";
        }
        String formatted = content.replaceAll(EXCESSIVE_NEWLINES_REGEX, NORMALIZED_NEWLINES);
        return formatted.trim();
    }
}
