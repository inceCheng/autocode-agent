package com.chg.yuaicodemother.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.chg.yuaicodemother.ai.model.HtmlCodeResult;
import com.chg.yuaicodemother.ai.model.MultiFileCodeResult;
import com.chg.yuaicodemother.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 文件保存器
 *
 */
@Deprecated
public class CodeFileSaver {

    // 文件保存的根目录
    public static final String ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";


    /**
     * 保存 HtmlCodeResult
     *
     * @param result
     * @return
     */
    public static File saveHtmlCodeResult(HtmlCodeResult result) {
        String baseDirPath = buildFilePath(CodeGenTypeEnum.HTML.getValue());
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        return new File(baseDirPath);
    }


    /**
     * 保存 MultiFileCodeResult
     *
     * @param result
     * @return
     */
    public static File saveMultiFileCodeResult(MultiFileCodeResult result) {
        String baseDirPath = buildFilePath(CodeGenTypeEnum.MULTI_FILE.getValue());
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        writeToFile(baseDirPath, "script.js", result.getJsCode());
        return new File(baseDirPath);
    }

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

        // 生成雪花 ID
        String snowflakeId = IdUtil.getSnowflakeNextIdStr();

        // 构建完整路径
        String dirPath = String.format("%s/%s/%s_%s", ROOT_DIR, datePath, bizType, snowflakeId);
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
    private static void writeToFile(String dirPath, String fileName, String content) {
        String filePath = dirPath + File.separator + fileName;
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }
}
