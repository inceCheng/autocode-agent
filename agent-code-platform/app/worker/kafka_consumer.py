import asyncio
import json
import logging
import time
import uuid
from contextlib import suppress
from dataclasses import dataclass

from aiokafka.structs import TopicPartition

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
from app.service.task_state_service import (
    TaskLock,
    TaskRuntimeStatus,
    TaskStateService,
)
from app.service.vue_project_service import AgentEventType, VueProjectGenService
from app.tools.path_utils import build_code_output_url, normalize_preview_path

logger = logging.getLogger(__name__)


class LockLostError(RuntimeError):
    """Raised when a worker no longer owns the Redis generation lock."""


@dataclass
class TaskProcessResult:
    topic_partition: TopicPartition
    offset: int
    task_id: str
    should_commit: bool = True


class LockedStreamService:
    """StreamService wrapper that verifies lock ownership before every write."""

    def __init__(
        self,
        delegate: StreamService,
        task_state_service: TaskStateService,
        task_lock: TaskLock,
    ) -> None:
        self._delegate = delegate
        self._task_state_service = task_state_service
        self._task_lock = task_lock

    async def push_chunk(self, task_id: str, chunk: ChatCompletionChunk) -> None:
        await self._ensure_lock(task_id)
        await self._delegate.push_chunk(task_id, chunk)

    async def push_done(self, task_id: str, done_chunk: ChatCompletionChunk) -> None:
        await self._ensure_lock(task_id)
        await self._delegate.push_done(task_id, done_chunk)

    async def push_error(
        self,
        task_id: str,
        error_chunk: ChatCompletionChunk,
        *,
        code: str = "GENERATION_FAILED",
        message: str = "代码生成失败，请稍后重试",
    ) -> None:
        await self._ensure_lock(task_id)
        await self._delegate.push_error(
            task_id,
            error_chunk,
            code=code,
            message=message,
        )

    async def _ensure_lock(self, task_id: str) -> None:
        if task_id != self._task_lock.task_id:
            raise LockLostError(f"锁任务不匹配: {task_id}")
        if not await self._task_state_service.owns_lock(self._task_lock):
            raise LockLostError(f"Redis生成锁已丢失: task_id={task_id}")
        status = await self._task_state_service.get_status(task_id)
        if status is not None and status.is_terminal:
            raise LockLostError(
                f"任务已进入终态，停止写入: task_id={task_id}, status={status}",
            )


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


async def _send_task_result(
    task_id: str,
    app_id: int,
    user_id: int,
    content: str,
    seq: int,
    *,
    status: TaskStatus,
    is_end: bool,
    error_msg: str | None = None,
) -> None:
    """发送任务状态/结果消息到Kafka。"""
    event = TaskResultEvent(
        taskId=task_id,
        appId=app_id,
        userId=user_id,
        status=status,
        messageType=MessageType.AI,
        content=content,
        seq=seq,
        isEnd=is_end,
        errorMsg=error_msg,
        timestamp=int(time.time() * 1000),
    )
    await send_task_result(event)


async def _send_processing_status(task_id: str, app_id: int, user_id: int) -> None:
    await _send_task_result(
        task_id,
        app_id,
        user_id,
        "",
        0,
        status=TaskStatus.PROCESSING,
        is_end=False,
    )


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
    await _send_task_result(
        task_id,
        app_id,
        user_id,
        content,
        seq,
        status=status,
        is_end=True,
        error_msg=error_msg,
    )


async def _handle_html(
    task_id: str,
    prompt: str,
    completion_id: str,
    app_id: int,
    user_id: int,
    preview_path: str,
    stream_service: LockedStreamService,
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

    raw_content = "".join(full_content)
    html_content = HtmlGenService._extract_html(raw_content)
    filename = "index.html"
    saved_path = await html_service._save_html_file(
        html_content,
        filename,
        app_id,
        preview_path,
    )

    safe_preview = normalize_preview_path(preview_path)
    metadata = CompletionMetadata(
        filename=filename,
        file_path=str(saved_path.resolve()),
        url_path=build_code_output_url(safe_preview, f"html_{app_id}", filename),
    )
    done_chunk = ChatCompletionChunk.new_done(
        seq=seq,
        metadata=metadata.model_dump(exclude_none=True),
        completion_id=completion_id,
    )
    await stream_service.push_done(task_id, done_chunk)

    await _send_final_result(task_id, app_id, user_id, raw_content, seq)
    logger.info("HTML任务处理完成: task_id=%s, file=%s", task_id, filename)


async def _handle_multi_file(
    task_id: str,
    prompt: str,
    completion_id: str,
    app_id: int,
    user_id: int,
    preview_path: str,
    stream_service: LockedStreamService,
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

    raw_content = "".join(full_content)
    files = MultiFileGenService.extract_files(raw_content)
    dir_name = MultiFileGenService.generate_dir_name()
    file_metas = await multi_file_service.save_files(
        files,
        app_id,
        preview_path,
    )

    metadata = CompletionMetadata(files=[f.model_dump() for f in file_metas])
    done_chunk = ChatCompletionChunk.new_done(
        seq=seq,
        metadata=metadata.model_dump(exclude_none=True),
        completion_id=completion_id,
    )
    await stream_service.push_done(task_id, done_chunk)

    await _send_final_result(task_id, app_id, user_id, raw_content, seq)
    logger.info(
        "MULTI_FILE任务处理完成: task_id=%s, dir=%s, files=%d",
        task_id,
        dir_name,
        len(file_metas),
    )


async def _handle_vue_project(
    task_id: str,
    prompt: str,
    completion_id: str,
    app_id: int,
    user_id: int,
    preview_path: str,
    stream_service: LockedStreamService,
    vue_project_service: VueProjectGenService,
) -> None:
    """处理 VUE_PROJECT 工程项目生成任务：通过 Agent + Tool Calling 流式生成"""
    seq = 0
    text_content_parts: list[str] = []
    project_dir = VueProjectGenService.build_project_path(app_id, preview_path)

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

            tool_json = f"\n\n{json.dumps(tool_info, ensure_ascii=False)}\n\n"
            text_content_parts.append(tool_json)
            chunk = ChatCompletionChunk.new_content(
                seq=seq,
                content=tool_json,
                completion_id=completion_id,
            )
            await stream_service.push_chunk(task_id, chunk)
            seq += 1

        elif event.type == AgentEventType.TOOL_END:
            tool_info = {"type": "tool_result", "action": event.tool_name}
            if event.tool_input:
                if event.tool_input.get("path"):
                    tool_info["path"] = event.tool_input["path"]
                if (
                    "content" in event.tool_input
                    and event.tool_input["content"] is not None
                ):
                    tool_info["content"] = event.tool_input["content"]

            tool_json = f"\n\n{json.dumps(tool_info, ensure_ascii=False)}\n\n"
            text_content_parts.append(tool_json)
            chunk = ChatCompletionChunk.new_content(
                seq=seq,
                content=tool_json,
                completion_id=completion_id,
            )
            await stream_service.push_chunk(task_id, chunk)
            seq += 1

    file_metas = VueProjectGenService.scan_project_files(
        project_dir,
        app_id,
        preview_path,
    )
    metadata = CompletionMetadata(files=[f.model_dump() for f in file_metas])
    done_chunk = ChatCompletionChunk.new_done(
        seq=seq,
        metadata=metadata.model_dump(exclude_none=True),
        completion_id=completion_id,
    )
    await stream_service.push_done(task_id, done_chunk)

    raw_content = "".join(text_content_parts)
    await _send_final_result(task_id, app_id, user_id, raw_content, seq)
    logger.info(
        "VUE_PROJECT任务处理完成: task_id=%s, files=%d",
        task_id,
        len(file_metas),
    )


async def _renew_lock_loop(
    task_state_service: TaskStateService,
    task_lock: TaskLock,
) -> None:
    settings = get_settings()
    while True:
        await asyncio.sleep(settings.redis_lock_renew_interval_sec)
        status = await task_state_service.get_status(task_lock.task_id)
        if status is not None and status.is_terminal:
            raise LockLostError(
                "任务已进入终态，停止续期: "
                f"task_id={task_lock.task_id}, status={status}",
            )
        renewed = await task_state_service.renew_lock(task_lock)
        if not renewed:
            raise LockLostError(f"Redis生成锁续期失败: task_id={task_lock.task_id}")


async def _wait_for_generation_with_lease(
    generation_coro,
    renew_task: asyncio.Task,
    *,
    timeout: int,
) -> None:
    generation_task = asyncio.create_task(generation_coro)
    try:
        done, _ = await asyncio.wait(
            {generation_task, renew_task},
            timeout=timeout,
            return_when=asyncio.FIRST_COMPLETED,
        )
        if not done:
            generation_task.cancel()
            with suppress(asyncio.CancelledError):
                await generation_task
            raise TimeoutError("代码生成超时")

        if renew_task in done:
            generation_task.cancel()
            with suppress(asyncio.CancelledError):
                await generation_task
            exception = renew_task.exception()
            if exception:
                raise exception
            raise LockLostError("Redis生成锁续期任务提前结束")

        await generation_task
    finally:
        if not generation_task.done():
            generation_task.cancel()
            with suppress(asyncio.CancelledError):
                await generation_task


async def _run_generation_once(
    event: AiTaskEvent,
    *,
    task_lock: TaskLock,
    task_state_service: TaskStateService,
    stream_service: StreamService,
    html_service: HtmlGenService,
    multi_file_service: MultiFileGenService,
    vue_project_service: VueProjectGenService,
) -> None:
    task_id = event.task.task_id
    prompt = event.payload.prompt
    project_type = event.task.project_type
    app_id = _resolve_app_id(event.task)
    preview_path = normalize_preview_path(event.task.preview_path)
    user_id = _resolve_user_id(event.task)
    completion_id = f"chatcmpl-{uuid.uuid4().hex[:24]}"
    locked_stream_service = LockedStreamService(
        stream_service,
        task_state_service,
        task_lock,
    )

    logger.info(
        "开始处理Kafka任务: task_id=%s, trace_id=%s, "
        "projectType=%s, app_id=%s, user_id=%s",
        task_id,
        event.trace_id,
        project_type,
        app_id,
        user_id,
    )

    await task_state_service.set_status(task_id, TaskRuntimeStatus.PROCESSING)
    await _send_processing_status(task_id, app_id, user_id)

    pt_lower = project_type.lower()
    if pt_lower == ProjectType.MULTI_FILE.value.lower():
        await _handle_multi_file(
            task_id,
            prompt,
            completion_id,
            app_id,
            user_id,
            preview_path,
            locked_stream_service,
            multi_file_service,
        )
    elif pt_lower == ProjectType.VUE_PROJECT.value.lower():
        await _handle_vue_project(
            task_id,
            prompt,
            completion_id,
            app_id,
            user_id,
            preview_path,
            locked_stream_service,
            vue_project_service,
        )
    else:
        await _handle_html(
            task_id,
            prompt,
            completion_id,
            app_id,
            user_id,
            preview_path,
            locked_stream_service,
            html_service,
        )
    await task_state_service.set_status(task_id, TaskRuntimeStatus.SUCCESS)


async def _process_message(
    message,
    *,
    task_state_service: TaskStateService,
    stream_service: StreamService,
    html_service: HtmlGenService,
    multi_file_service: MultiFileGenService,
    vue_project_service: VueProjectGenService,
) -> TaskProcessResult:
    topic_partition = TopicPartition(message.topic, message.partition)
    task_id = "unknown"
    app_id = 0
    user_id = 0
    task_lock: TaskLock | None = None
    renew_task: asyncio.Task | None = None

    try:
        event = AiTaskEvent.model_validate(message.value)
        task_id = event.task.task_id
        app_id = _resolve_app_id(event.task)
        user_id = _resolve_user_id(event.task)

        # 获取Redis任务状态
        if not await task_state_service.can_attempt_task(task_id):
            status = await task_state_service.get_status(task_id)
            logger.info(
                "跳过不可处理任务并提交offset: task_id=%s, status=%s",
                task_id,
                status,
            )
            return TaskProcessResult(topic_partition, message.offset, task_id)

        task_lock = await task_state_service.acquire_lock(task_id)
        if task_lock is None:
            logger.info("任务锁被其他Worker持有，提交当前重复消息: task_id=%s", task_id)
            return TaskProcessResult(topic_partition, message.offset, task_id)

        renew_task = asyncio.create_task(
            _renew_lock_loop(task_state_service, task_lock),
        )
        settings = get_settings()
        attempts = max(settings.generation_retry_attempts, 0) + 1
        for attempt in range(1, attempts + 1):
            try:
                await _wait_for_generation_with_lease(
                    _run_generation_once(
                        event,
                        task_lock=task_lock,
                        task_state_service=task_state_service,
                        stream_service=stream_service,
                        html_service=html_service,
                        multi_file_service=multi_file_service,
                        vue_project_service=vue_project_service,
                    ),
                    timeout=settings.generation_timeout_sec,
                    renew_task=renew_task,
                )
                return TaskProcessResult(topic_partition, message.offset, task_id)
            except LockLostError:
                raise
            except Exception as exc:
                if attempt < attempts:
                    logger.warning(
                        "任务处理失败，准备本地重试: "
                        "task_id=%s, attempt=%d/%d, error=%s",
                        task_id,
                        attempt,
                        attempts,
                        exc,
                    )
                    continue
                raise

    except LockLostError:
        logger.warning("任务锁丢失，停止写入和回传: task_id=%s", task_id)
        return TaskProcessResult(topic_partition, message.offset, task_id)
    except Exception as exc:
        logger.exception("处理Kafka消息失败: task_id=%s", task_id)
        try:
            if task_lock and await task_state_service.owns_lock(task_lock):
                completion_id = f"chatcmpl-{uuid.uuid4().hex[:24]}"
                error_chunk = ChatCompletionChunk.new_error(
                    seq=0,
                    error_msg="代码生成失败，请稍后重试",
                    completion_id=completion_id,
                )
                await stream_service.push_error(
                    task_id,
                    error_chunk,
                    code="GENERATION_FAILED",
                    message="代码生成失败，请稍后重试",
                )
                await task_state_service.set_status(task_id, TaskRuntimeStatus.FAILED)
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
            logger.exception("推送失败终态也失败: task_id=%s", task_id)
        return TaskProcessResult(topic_partition, message.offset, task_id)
    finally:
        if renew_task is not None:
            renew_task.cancel()
            with suppress(asyncio.CancelledError, Exception):
                await renew_task
        if task_lock is not None:
            with suppress(Exception):
                await task_state_service.release_lock(task_lock)


async def kafka_consumer_worker() -> None:
    """
    Kafka消费者后台工作线程。

    Consumer负责持续poll保活；生成任务交给受控runner执行，完成后再提交offset。
    """
    consumer = get_kafka_consumer()
    settings = get_settings()
    stream_service: StreamService = get_stream_service()
    html_service: HtmlGenService = get_html_gen_service()
    multi_file_service: MultiFileGenService = get_multi_file_gen_service()
    vue_project_service: VueProjectGenService = get_vue_project_gen_service()
    task_state_service = TaskStateService()
    in_flight: dict[TopicPartition, asyncio.Task[TaskProcessResult]] = {}

    async def drain_done_tasks() -> None:
        done_tps = [tp for tp, task in in_flight.items() if task.done()]
        for tp in done_tps:
            task = in_flight.pop(tp)
            try:
                result = task.result()
                if result.should_commit:
                    await consumer.commit({result.topic_partition: result.offset + 1})
                    logger.info(
                        "Kafka offset已提交: task_id=%s, partition=%s, offset=%s",
                        result.task_id,
                        result.topic_partition,
                        result.offset,
                    )
            except Exception:
                logger.exception("提交Kafka offset失败: partition=%s", tp)
            finally:
                consumer.resume(tp)

    try:
        while True:
            await drain_done_tasks()
            if not in_flight:
                paused = consumer.paused()
                if paused:
                    consumer.resume(*paused)

            records = await consumer.getmany(
                timeout_ms=settings.kafka_poll_timeout_ms,
                max_records=settings.kafka_poll_max_records,
            )
            for tp, messages in records.items():
                if tp in in_flight or not messages:
                    continue
                message = messages[0]
                consumer.pause(tp)
                in_flight[tp] = asyncio.create_task(
                    _process_message(
                        message,
                        task_state_service=task_state_service,
                        stream_service=stream_service,
                        html_service=html_service,
                        multi_file_service=multi_file_service,
                        vue_project_service=vue_project_service,
                    )
                )
    except asyncio.CancelledError:
        logger.info("Kafka消费者Worker被取消，正在关闭")
        for task in in_flight.values():
            task.cancel()
        for task in in_flight.values():
            with suppress(asyncio.CancelledError):
                await task
    finally:
        logger.info("Kafka消费者Worker已退出")
