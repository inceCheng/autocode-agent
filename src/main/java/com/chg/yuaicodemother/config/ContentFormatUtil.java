package com.chg.yuaicodemother.config;

import java.util.ArrayList;
import java.util.List;

public class ContentFormatUtil {

    /**
     * 格式化 AI 返回的 content
     */
    public static String format(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        // 统一换行符
        content = content.replace("\r\n", "\n");

        StringBuilder result = new StringBuilder();

        String[] lines = content.split("\n");

        boolean inCodeBlock = false;
        List<String> buffer = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();

            // 判断代码块开始/结束
            if (trimmed.startsWith("```")) {
                // 先处理普通文本缓存
                appendNormalText(result, buffer);
                buffer.clear();

                inCodeBlock = !inCodeBlock;

                result.append(trimmed).append("\n");
                continue;
            }

            if (inCodeBlock) {
                // 代码块：原样保留（只去掉行尾空格）
                result.append(rtrim(line)).append("\n");
            } else {
                // 普通文本：进入 buffer
                buffer.add(line);
            }
        }

        // 处理最后一段普通文本
        appendNormalText(result, buffer);

        // 压缩多余空行（>=2 → 1）
        String finalText = result.toString()
                .replaceAll("\n{3,}", "\n\n")
                .trim();

        return finalText;
    }

    /**
     * 处理普通文本段
     */
    private static void appendNormalText(StringBuilder result, List<String> buffer) {
        if (buffer.isEmpty()) return;

        StringBuilder paragraph = new StringBuilder();

        for (String line : buffer) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                // 遇到空行 → 结束当前段落
                if (paragraph.length() > 0) {
                    result.append(paragraph.toString().trim()).append("\n\n");
                    paragraph.setLength(0);
                }
            } else {
                // 合并行（防止被 AI 硬换行拆碎）
                if (paragraph.length() > 0) {
                    paragraph.append(" ");
                }
                paragraph.append(trimmed);
            }
        }

        if (paragraph.length() > 0) {
            result.append(paragraph.toString().trim()).append("\n");
        }
    }

    /**
     * 去掉行尾空格
     */
    private static String rtrim(String str) {
        int i = str.length() - 1;
        while (i >= 0 && Character.isWhitespace(str.charAt(i))) {
            i--;
        }
        return str.substring(0, i + 1);
    }
}