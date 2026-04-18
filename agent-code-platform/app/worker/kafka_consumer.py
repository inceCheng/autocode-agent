import asyncio
import logging
import uuid

from app.config.kafka import get_kafka_consumer
from app.dependency.container import get_html_gen_service, get_stream_service
from app.model.event.ai_task_event import AiTaskEvent
from app.model.response.stream_response import ChatCompletionChunk, CompletionMetadata
from app.service.html_service import HtmlGenService
from app.service.stream_service import StreamService

logger = logging.getLogger(__name__)


async def kafka_consumer_worker() -> None:
    """
    Kafka消费者后台工作线程。

    监听 agent-generation-tasks Topic，解析消息后调用大模型流式生成HTML，
    将每个Chunk以OpenAI chat.completion.chunk格式通过Redis List持久化 + PubSub广播。
    """
    consumer = get_kafka_consumer()
    stream_service: StreamService = get_stream_service()
    html_service: HtmlGenService = get_html_gen_service()

    try:
        async for message in consumer:
            task_id = "unknown"
            seq = 0
            try:
                event = AiTaskEvent.model_validate(message.value)
                task_id = event.task.task_id
                prompt = event.payload.prompt

                logger.info(
                    "开始处理Kafka任务: task_id=%s, trace_id=%s",
                    task_id,
                    event.trace_id,
                )

                completion_id = f"chatcmpl-{uuid.uuid4().hex[:24]}"
                full_content: list[str] = []

                async for chunk_text in html_service.generate_stream(prompt):
                    full_content.append(chunk_text)
                    chunk = ChatCompletionChunk.new_content(
                        seq=seq,
                        content=chunk_text,
                        completion_id=completion_id,
                    )
                    await stream_service.push_chunk(task_id, chunk)
                    seq += 1

                # 后处理：提取HTML并保存文件
                raw_content = "".join(full_content)
                html_content = HtmlGenService._extract_html(raw_content)
                filename = HtmlGenService._generate_filename()
                saved_path = await html_service._save_html_file(html_content, filename)

                # 推送完成信号
                metadata = CompletionMetadata(
                    filename=filename,
                    file_path=str(saved_path.resolve()),
                    url_path=f"/static/html/{filename}",
                )
                done_chunk = ChatCompletionChunk.new_done(
                    seq=seq,
                    metadata=metadata.model_dump(),
                    completion_id=completion_id,
                )
                await stream_service.push_done(task_id, done_chunk)

                # 处理成功，手动提交offset
                await consumer.commit()
                logger.info("任务处理完成: task_id=%s, file=%s", task_id, filename)

            except Exception:
                logger.exception("处理Kafka消息失败: task_id=%s", task_id)
                # 推送错误Chunk通知前端
                try:
                    completion_id = f"chatcmpl-{uuid.uuid4().hex[:24]}"
                    error_chunk = ChatCompletionChunk.new_error(
                        seq=seq,
                        error_msg="代码生成失败，请稍后重试",
                        completion_id=completion_id,
                    )
                    await stream_service.push_chunk(task_id, error_chunk)
                except Exception:
                    logger.exception("推送错误Chunk也失败: task_id=%s", task_id)
                # 不提交offset，等待重新投递

    except asyncio.CancelledError:
        logger.info("Kafka消费者Worker被取消，正在关闭")
    finally:
        logger.info("Kafka消费者Worker已退出")
