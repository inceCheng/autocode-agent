package com.chg.yuaicodemother.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 文件保存器
 *
 */
public class CodeFileSaver {
    // 文件保存的根目录
    public static final String ROOT_DIR = System.getProperty("user.dir") + "/code_output";

    // 保存 HTML 代码

    // 保存多文件代码

    /**
     * 构建文件的唯一路径  tmp/code_output/year/month/day/bizType_雪花 ID
     *
     * @param bizType 文件类型
     * @return 路径
     */
    private static String buildFilePath(String bizType) {
        // 获取当前日期
        LocalDate now = LocalDate.now();
        // 格式化日期为 year/month/day
        String datePath = String.format("%d/%02d/%02d",
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth());

        // 生成雪花ID
        String snowflakeId = IdUtil.getSnowflakeNextIdStr();

        // 构建完整路径
        String dirPath = String.format("tmp/code_output/%s/%s_%s", datePath, bizType, snowflakeId);
        FileUtil.mkdir(dirPath);
        return dirPath;
    }


    /**
     * 保存单个文件
     *
     * @param dirPath  目录路径
     * @param fileName 文件名
     * @param content  文件内容
     */
    private static void saveFile(String dirPath, String fileName, String content) {
        String filePath = dirPath + File.separator + fileName;
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }
}
