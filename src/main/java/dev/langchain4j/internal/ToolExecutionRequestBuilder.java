package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Json.fromJson;

@Internal
// TODO location
// TODO name
public class ToolExecutionRequestBuilder {

    private final AtomicReference<Integer> index;

    private final AtomicReference<String> id = new AtomicReference<>();
    private final AtomicReference<String> name = new AtomicReference<>();
    private final StringBuffer arguments = new StringBuffer();

    private final List<ToolExecutionRequest> allToolExecutionRequests = new ArrayList<>();

    public ToolExecutionRequestBuilder() {
        this(0);
    }

    public ToolExecutionRequestBuilder(int index) {
        this.index = new AtomicReference(index);
    }

    public int index() {
        return index.get();
    }

    public int updateIndex(Integer index) {
        if (index != null) {
            this.index.set(index);
        }
        return this.index.get();
    }

    public String id() {
        return id.get();
    }

    public String updateId(String id) {
        if (isNotNullOrBlank(id)) {
            this.id.set(id);
        }
        return this.id.get();
    }

    public String name() {
        return name.get();
    }

    public String updateName(String name) {
        if (isNotNullOrBlank(name)) {
            this.name.set(name);
        }
        return this.name.get();
    }

    public void appendArguments(String partialArguments) {
        if (isNotNullOrEmpty(partialArguments)) {
            arguments.append(partialArguments);
        }
    }

    public ToolExecutionRequest build() {
        // TODO store it till complete response?
        String arguments = this.arguments.toString().trim();

        // 验证参数是否为有效的 JSON 格式
        // 某些模型（如 DeepSeek）可能返回非 JSON 格式的工具调用参数
        if (!arguments.isEmpty() && !isValidJson(arguments)) {
            System.err.println("Warning: Tool arguments are not valid JSON, replacing with empty object. " +
                    "Original arguments: " + (arguments.length() > 100 ? arguments.substring(0, 100) + "..." : arguments));
            arguments = "{}";
        }

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id(id.get())
                .name(name.get())
                .arguments(arguments.isEmpty() ? "{}" : arguments)
                .build();
        allToolExecutionRequests.add(toolExecutionRequest); // TODO method name, rethink
        reset();
        return toolExecutionRequest;
    }

    /**
     * 检查字符串是否为有效的 JSON 格式
     * 使用 Jackson 尝试解析来验证
     */
    private boolean isValidJson(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        try {
            // 尝试解析为 Map 来验证 JSON 格式
            fromJson(str, java.util.Map.class);
            return true;
        } catch (Exception e) {
            // JSON 解析失败，格式无效
            return false;
        }
    }

    private void reset() {
        id.set(null);
        name.set(null);
        arguments.setLength(0);
    }

    public boolean hasToolExecutionRequests() {
        return !allToolExecutionRequests.isEmpty() || name.get() != null;
    }

    public List<ToolExecutionRequest> allToolExecutionRequests() {
        return allToolExecutionRequests;
    }
}