package com.chg.yuaicodemother.ai;

import com.chg.yuaicodemother.ai.model.HtmlCodeResult;
import com.chg.yuaicodemother.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiCodeGeneratorServiceTest {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    // @Test
    // void generateHtmlCode() {
    //     HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode("做一个程序员的技术博客,不超过 30 行");
    //     Assertions.assertNotNull(result);
    // }
    //
    // @Test
    // void generateMultiFileCode() {
    //     MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode("做一个程序员的留言板,不超过 30 行");
    //     Assertions.assertNotNull(result);
    // }
}