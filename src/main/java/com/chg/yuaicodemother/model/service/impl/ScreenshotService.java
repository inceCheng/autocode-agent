package com.chg.yuaicodemother.model.service.impl;

public interface ScreenshotService {

    /**
     * 生成并上传网页截图的方法
     *
     * @param webUrl 需要截图的网页 URL 地址
     * @return 返回处理后的结果字符串，可能包含截图的存储路径或其他相关信息
     */

    String generateAndUploadScreenshot(String webUrl);
}
