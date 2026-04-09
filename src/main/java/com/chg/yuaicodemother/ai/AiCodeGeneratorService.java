package com.chg.yuaicodemother.ai;

import com.chg.yuaicodemother.ai.model.HtmlCodeResult;
import com.chg.yuaicodemother.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.SystemMessage;
import reactor.core.publisher.Flux;

/**
 * AI代码生成器服务接口
 * 该接口定义了用于生成代码的核心方法，包括生成HTML代码和多文件代码的功能
 */
public interface AiCodeGeneratorService {

    /**
     * 生成 HTML 代码的方法
     * 该方法使用系统提示词文件来指导代码生成过程
     * <p>
     * 返回一个 Flux<String> 类型的响应流，可以逐步接收生成的 HTML 代码片段
     *
     * @param userMessage 用户提示词，包含用户期望生成的 HTML 代码的具体要求
     * @return HTML 代码结果，以 Flux<String> 流的形式返回，允许实时接收生成的代码片段
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);

    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     * 生成多文件代码的方法
     *
     * @param userMessage 用户提示词
     * @return 多文件代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);


    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);
}
