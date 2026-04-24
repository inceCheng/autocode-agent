import asyncio
import json
import logging
import time
import uuid

from app.common.enums.project_type import ProjectType
from app.config.kafka import get_kafka_consumer
from app.config.kafka_producer import send_task_result
from app.config.settings import get_settings
from app.dependency.container import (
    get_html_gen_service,
    get_multi_file_gen_service,
    get_stream_service,
    get_vue_project_gen_service,
)
from app.model.event.ai_task_event import AiTaskEvent
from app.model.event.task_result_event import MessageType, TaskResultEvent, TaskStatus
from app.model.response.stream_response import ChatCompletionChunk, CompletionMetadata
from app.service.html_service import HtmlGenService
from app.service.multi_file_service import MultiFileGenService
from app.service.stream_service import StreamService
from app.service.vue_project_service import AgentEventType, VueProjectGenService

logger = logging.getLogger(__name__)


def _resolve_app_id(task_info) -> int:
    """从TaskInfo中解析app_id，优先使用显式app_id，否则尝试将task_id转为int"""
    if task_info.app_id is not None:
        try:
            return int(task_info.app_id)
        except (ValueError, TypeError):
            pass
    try:
        return int(task_info.task_id)
    except (ValueError, TypeError):
        return 0


def _resolve_user_id(task_info) -> int:
    try:
        return int(task_info.user_id)
    except (ValueError, TypeError):
        return 0
def _resolve_preview_path(task_info)->str:
    if task_info.preview_path is not None:
        try:
            return task_info.preview_path
        except (ValueError, TypeError):
            pass
    try:
        return "default"
    except (ValueError, TypeError):
        return 0



async def _send_final_result(
    task_id: str,
    app_id: int,
    user_id: int,
    content: str,
    seq: int,
    *,
    status: TaskStatus = TaskStatus.SUCCESS,
    error_msg: str | None = None,
) -> None:
    """发送最终结果消息到Kafka（SUCCESS或FAILED）"""
    event = TaskResultEvent(
        taskId=task_id,
        appId=app_id,
        userId=user_id,
        status=status,
        messageType=MessageType.AI,
        content=content,
        seq=seq,
        isEnd=True,
        errorMsg=error_msg,
        timestamp=int(time.time() * 1000),
    )
    await send_task_result(event)


async def _handle_html(
    task_id: str,
    prompt: str,
    completion_id: str,
    app_id: int,
    user_id: int,
    preview_path: str,
    stream_service: StreamService,
    html_service: HtmlGenService,
) -> None:
    """处理 HTML 单文件生成任务"""
    seq = 0
    full_content: list[str] = []

    async for chunk_text in html_service.generate_stream(prompt):
        full_content.append(chunk_text)
        chunk = ChatCompletionChunk.new_content(
            seq=seq, content=chunk_text, completion_id=completion_id,
        )
        await stream_service.push_chunk(task_id, chunk)
        seq += 1

    # 后处理：提取HTML并保存文件
    raw_content = "".join(full_content)
    html_content = HtmlGenService._extract_html(raw_content)
    filename = "index.html"
    saved_path = await html_service._save_html_file(html_content, filename, app_id,preview_path)

    # 推送完成信号（单文件用 filename/file_path/url_path 字段）
    metadata = CompletionMetadata(
        filename=filename,
        file_path=str(saved_path.resolve()),
        url_path=f"/static/html/{filename}",
    )
    done_chunk = ChatCompletionChunk.new_done(
        seq=seq, metadata=metadata.model_dump(exclude_none=True), completion_id=completion_id,
    )
    await stream_service.push_done(task_id, done_chunk)

    # 发送最终结果到Kafka
    await _send_final_result(task_id, app_id, user_id, raw_content, seq)
    logger.info("HTML任务处理完成: task_id=%s, file=%s", task_id, filename)


async def _handle_multi_file(
    task_id: str,
    prompt: str,
    completion_id: str,
    app_id: int,
    user_id: int,
    preview_path: str,
    stream_service: StreamService,
    multi_file_service: MultiFileGenService,
) -> None:
    """处理 MULTI_FILE 多文件生成任务"""
    seq = 0
    full_content: list[str] = []

    async for chunk_text in multi_file_service.generate_stream(prompt):
        full_content.append(chunk_text)
        chunk = ChatCompletionChunk.new_content(
            seq=seq, content=chunk_text, completion_id=completion_id,
        )
        await stream_service.push_chunk(task_id, chunk)
        seq += 1

    # 后处理：提取 html/css/js 并保存到独立目录
    raw_content = "".join(full_content)
    files = MultiFileGenService.extract_files(raw_content)
    dir_name = MultiFileGenService.generate_dir_name()
    file_metas = await multi_file_service.save_files(dir_name, files, app_id,preview_path)

    # 推送完成信号（多文件用 files 列表字段）
    metadata = CompletionMetadata(
        files=[f.model_dump() for f in file_metas],
    )
    done_chunk = ChatCompletionChunk.new_done(
        seq=seq, metadata=metadata.model_dump(exclude_none=True), completion_id=completion_id,
    )
    await stream_service.push_done(task_id, done_chunk)

    # 发送最终结果到Kafka
    await _send_final_result(task_id, app_id, user_id, raw_content, seq)
    logger.info(
        "MULTI_FILE任务处理完成: task_id=%s, dir=%s, files=%d",
        task_id, dir_name, len(file_metas),
    )


async def _handle_vue_project(
    task_id: str,
    prompt: str,
    completion_id: str,
    app_id: int,
    user_id: int,
    preview_path: str,
    stream_service: StreamService,
    vue_project_service: VueProjectGenService,
) -> None:
    """处理 VUE_PROJECT 工程项目生成任务：通过 Agent + Tool Calling 流式生成"""
    seq = 0
    text_content_parts: list[str] = []

    # 创建项目目录（使用新的路径格式，不含时间戳子目录）
    project_dir = VueProjectGenService.build_project_path(app_id,preview_path)

    async for event in vue_project_service.generate_stream(prompt, project_dir):
        if event.type == AgentEventType.TEXT:
            text_content_parts.append(event.content)
            chunk = ChatCompletionChunk.new_content(
                seq=seq, content=event.content, completion_id=completion_id,
            )
            await stream_service.push_chunk(task_id, chunk)
            seq += 1

        elif event.type == AgentEventType.TOOL_START:
            tool_info = {"type": "tool_call", "action": event.tool_name}
            if event.tool_input and event.tool_input.get("path"):
                tool_info["path"] = event.tool_input["path"]

            # ✨ 关键修改：前后增加换行符
            tool_json = f"\n\n{json.dumps(tool_info, ensure_ascii=False)}\n\n"

            text_content_parts.append(tool_json)
            chunk = ChatCompletionChunk.new_content(seq=seq, content=tool_json, completion_id=completion_id)
            await stream_service.push_chunk(task_id, chunk)
            seq += 1

        elif event.type == AgentEventType.TOOL_END:
            tool_info = {"type": "tool_result", "action": event.tool_name}
            if event.tool_input:
                if event.tool_input.get("path"):
                    tool_info["path"] = event.tool_input["path"]
                if "content" in event.tool_input and event.tool_input["content"] is not None:
                    tool_info["content"] = event.tool_input["content"]

            # ✨ 关键修改：前后增加换行符
            tool_json = f"\n\n{json.dumps(tool_info, ensure_ascii=False)}\n\n"

            text_content_parts.append(tool_json)
            chunk = ChatCompletionChunk.new_content(seq=seq, content=tool_json, completion_id=completion_id)
            await stream_service.push_chunk(task_id, chunk)
            seq += 1

        # elif event.type == AgentEventType.TOOL_START:
        #     tool_info = {"type": "tool_call", "action": event.tool_name}
        #     if event.tool_input and event.tool_input.get("path"):
        #         tool_info["path"] = event.tool_input["path"]
        #     tool_json = json.dumps(tool_info, ensure_ascii=False)
        #     text_content_parts.append(tool_json)
        #     chunk = ChatCompletionChunk.new_content(
        #         seq=seq,
        #         content=tool_json,
        #         completion_id=completion_id,
        #     )
        #     await stream_service.push_chunk(task_id, chunk)
        #     seq += 1
        #
        # elif event.type == AgentEventType.TOOL_END:
        #     tool_info = {"type": "tool_result", "action": event.tool_name}
        #     if event.tool_input:
        #         if event.tool_input.get("path"):
        #             tool_info["path"] = event.tool_input["path"]
        #         if "content" in event.tool_input and event.tool_input["content"] is not None:
        #             tool_info["content"] = event.tool_input["content"]
        #     tool_json = json.dumps(tool_info, ensure_ascii=False)
        #     text_content_parts.append(tool_json)
        #     chunk = ChatCompletionChunk.new_content(
        #         seq=seq,
        #         content=tool_json,
        #         completion_id=completion_id,
        #     )
        #     await stream_service.push_chunk(task_id, chunk)
        #     seq += 1

    # 后处理：扫描项目目录，收集所有文件元信息
    file_metas = VueProjectGenService.scan_project_files(project_dir, app_id,preview_path)
    metadata = CompletionMetadata(
        files=[f.model_dump() for f in file_metas],
    )
    done_chunk = ChatCompletionChunk.new_done(
        seq=seq, metadata=metadata.model_dump(exclude_none=True), completion_id=completion_id,
    )
    await stream_service.push_done(task_id, done_chunk)

    # 发送最终结果到Kafka（拼接所有文本和工具调用信息）
    raw_content = "".join(text_content_parts)
    await _send_final_result(task_id, app_id, user_id, raw_content, seq)
    logger.info(
        "VUE_PROJECT任务处理完成: task_id=%s, files=%d",
        task_id, len(file_metas),
    )


async def kafka_consumer_worker() -> None:
    """
    Kafka消费者后台工作线程。

    监听 agent-generation-tasks Topic，根据 projectType 分发到不同的生成服务：
    - HTML: 单文件生成
    - MULTI_FILE: 多文件（html/css/js）生成
    - VUE_PROJECT: Vue3工程项目生成（Agent + Tool Calling）

    每个 Chunk 以 OpenAI chat.completion.chunk 格式通过 Redis 持久化 + 广播。
    同时将结果消息发送到 task-result-topic Kafka Topic。
    """
    consumer = get_kafka_consumer()
    stream_service: StreamService = get_stream_service()
    html_service: HtmlGenService = get_html_gen_service()
    multi_file_service: MultiFileGenService = get_multi_file_gen_service()
    vue_project_service: VueProjectGenService = get_vue_project_gen_service()

    try:
        async for message in consumer:
            task_id = "unknown"
            app_id = 0
            user_id = 0
            try:
                event = AiTaskEvent.model_validate(message.value)
                task_id = event.task.task_id
                prompt = event.payload.prompt
                project_type = event.task.project_type
                app_id = _resolve_app_id(event.task)
                preview_path =  event.task.preview_path
                user_id = _resolve_user_id(event.task)

                logger.info(
                    "开始处理Kafka任务: task_id=%s, trace_id=%s, projectType=%s, app_id=%s, user_id=%s",
                    task_id, event.trace_id, project_type, app_id, user_id,
                )

                completion_id = f"chatcmpl-{uuid.uuid4().hex[:24]}"

                # 按 projectType 分发
                pt_lower = project_type.lower()
                if pt_lower == ProjectType.MULTI_FILE.value.lower():
                    await _handle_multi_file(
                        task_id, prompt, completion_id, app_id, user_id,preview_path,
                        stream_service, multi_file_service,
                    )
                elif pt_lower == ProjectType.VUE_PROJECT.value.lower():
                    await _handle_vue_project(
                        task_id, prompt, completion_id, app_id, user_id,preview_path,
                        stream_service, vue_project_service,
                    )
                else:
                    await _handle_html(
                        task_id, prompt, completion_id, app_id, user_id,preview_path,
                        stream_service, html_service,
                    )

                # 处理成功，手动提交offset
                await consumer.commit()

            except Exception as exc:
                logger.exception("处理Kafka消息失败: task_id=%s", task_id)
                # 推送错误Chunk通知前端
                try:
                    completion_id = f"chatcmpl-{uuid.uuid4().hex[:24]}"
                    error_chunk = ChatCompletionChunk.new_error(
                        seq=0,
                        error_msg="代码生成失败，请稍后重试",
                        completion_id=completion_id,
                    )
                    await stream_service.push_chunk(task_id, error_chunk)
                except Exception:
                    logger.exception("推送错误Chunk也失败: task_id=%s", task_id)

                # 发送FAILED结果消息到Kafka
                try:
                    await _send_final_result(
                        task_id=task_id,
                        app_id=app_id,
                        user_id=user_id,
                        content="",
                        seq=0,
                        status=TaskStatus.FAILED,
                        error_msg=str(exc)[:500],
                    )
                except Exception:
                    logger.exception("发送Kafka FAILED消息也失败: task_id=%s", task_id)
                # 不提交offset，等待重新投递

    except asyncio.CancelledError:
        logger.info("Kafka消费者Worker被取消，正在关闭")
    finally:
        logger.info("Kafka消费者Worker已退出")
