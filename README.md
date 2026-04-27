#  零代码应用生成平台

AI 代码生成平台，用户在前端输入提示词后，由 Java 业务服务创建应用和任务，Python AI 服务消费 Kafka 任务并生成代码，前端通过 SSE 实时展示进度并预览生成的网站。

## 服务目录

- `src/`：Java Spring Boot 业务服务，负责用户、应用、任务、版本、部署、下载和数据库持久化。
- `agent-code-platform/`：Python FastAPI AI 服务，负责项目类型路由、Kafka Worker、Redis Stream SSE、代码生成和定点修改。
- `yu-ai-code-mother-frontend/`：Vue 3 前端，负责主页、对话页、应用预览、可视化元素选择和管理页。
- `sql/create_table.sql`：MySQL 表结构。

## 启动命令

Java 业务服务：

```bash
mvn spring-boot:run
```

Python AI 服务：

```bash
cd agent-code-platform
uv sync
uv run uvicorn app.main:app --reload
```

前端：

```bash
cd yu-ai-code-mother-frontend
npm install
npm run dev
```

常用检查：

```bash
mvn test
cd agent-code-platform && uvx ruff check .
cd yu-ai-code-mother-frontend && npm run build
```

## 核心生成链路

1. 前端主页调用 Java `POST /api/v1/app/add`。
2. Java 创建 `app`、`ai_generation_task`、`app_version(v1)`，写 Redis `ai:task:status:{taskId}=PENDING`。
3. Java 调用 Python 路由服务识别 `codeGenType`，发送 Kafka `agent-generation-tasks`，返回 `taskId/appId/token`。
4. Python Worker 抢占 `ai:generate:lock:{taskId}`，按 `html/multi_file/vue_project` 生成代码。
5. Python 将 chunk 写入 Redis Stream `ai:stream:{taskId}`，并在结束时发送 Kafka `task-result-topic`。
6. 前端用 `POST /api/ai/stream` 带 `taskId/token/appId` 读取 Redis Stream 历史和实时事件。
7. Java 消费 `task-result-topic`，更新任务、版本和 app 当前版本快照。

运行时状态以 `taskId` 为主键；`appId` 只是业务归属和查询维度。

## 定点修改链路

定点修改不是直接改 iframe DOM，而是基于当前成功版本创建新版本：

```text
baseVersion -> EDIT task -> targetVersion
```

流程：

1. 用户在右侧预览开启编辑模式并选择元素。
2. Java 静态资源服务在 `?visualEdit=1` 的 HTML 中注入 bridge script，iframe 通过 `postMessage` 返回 `nodeId/selector/outerHTML/text/computedStyle/rect`。
3. 前端调用 Java `POST /api/v1/app/edit/create`，提交 `appId/baseVersionId/instruction/selectedElements/scope`。
4. Java 校验应用归属、版本归属、同 app 无运行中任务，创建 `EDIT` 类型任务和新的 `app_version`。
5. Python Worker 复制 base version 目录到 target version 目录，读取 `.ai/manifest.json` 和相关源码文件。
6. AI 返回 `modifiedFiles` 完整文件内容，Worker 覆盖 target workspace 文件并重新写 manifest。
7. Vue 项目会执行 `npm install && npm run build`；HTML/MULTI_FILE 做基础文件校验。
8. 成功后 Java 将 target version 标记为当前版本；失败时保留旧版本不变。

第一版支持 `single` 和 `section` 两种范围，默认 `single`。

## 数据模型

核心表：

- `app`：应用主表，保存 `currentVersionId/currentTaskId/generateStatus` 作为 UI 快照。
- `ai_generation_task`：生成和编辑任务表，保存 `taskId/taskType/projectType/baseVersionId/targetVersionId/status/requestPayload`。
- `app_version`：版本表，保存 `versionNo/sourcePath/manifestPath/previewUrl/status`。
- `chat_history`：用户和 AI 对话历史。

版本规则：

- 初次生成写入 `v1`。
- 每次定点修改生成 `v2/v3/...`。
- 失败任务不切换 `currentVersionId`。
- 部署和下载默认使用当前成功版本。

## Redis 与 Kafka

Redis：

- `ai:stream:{taskId}`：Redis Stream，前端 SSE 的历史和实时事件来源。
- `ai:task:status:{taskId}`：运行时任务状态。
- `ai:generate:lock:{taskId}`：Python Worker lease lock，TTL 是故障恢复窗口。

Kafka：

- `agent-generation-tasks`：Java 到 Python，消息 key 为 `taskId`。
- `task-result-topic`：Python 到 Java，回传 PROCESSING/SUCCESS/FAILED 等状态。

## 预览与安全

- 生成代码统一落到 `tmp/code_output/{previewPath}/{projectDir}`。
- Java 静态预览入口为 `/api/v1/static/**`。
- 预览 iframe 只通过 `postMessage` 与主站通信，不暴露主站 token。
- 父页面处理 iframe 消息时校验 `event.source`。
- Python 文件读写必须限制在版本 workspace 内，禁止路径越界。

## 开发注意事项

- 不新增 GET/EventSource 流接口；继续使用 `POST /api/ai/stream`。
- 不要把任务状态改成只按 `appId` 标识；同一个 app 可以有多次生成、重试和定点修改。
- 生成和编辑 prompt 都要要求主要元素保留 `data-ai-id`，否则后续定位只能走不稳定的 selector/text 降级。
- 定点修改失败时不要覆盖 base version，也不要切换预览。
- 前端 `src/api/*Controller.ts` 由 OpenAPI 生成，通常不要手工维护；临时新增接口可用 `request.ts` 直接调用，后续再执行 `npm run openapi2ts` 同步。
