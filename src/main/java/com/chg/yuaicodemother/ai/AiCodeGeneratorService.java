package com.chg.yuaicodemother.ai;

import com.chg.yuaicodemother.ai.model.HtmlCodeResult;
import com.chg.yuaicodemother.ai.model.MultiFileResult;
import dev.langchain4j.service.SystemMessage;

public interface AiCodeGeneratorService {

    /**
     *
     * @param userMessage 用户提示词
     * @return
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);

    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileResult generateMultiFileCode(String userMessage);
}
