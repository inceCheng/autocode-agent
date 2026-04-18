import asyncio
import logging
from typing import Annotated

import jwt
from fastapi import APIRouter, Depends, HTTPException
from sse_starlette.sse import EventSourceResponse, ServerSentEvent

from app.dependency.container import get_jwt_service, get_stream_service
from app.model.request.stream_request import StreamRequest
from app.model.response.stream_response import ChatCompletionChunk
from app.service.jwt_service import JwtService
from app.service.stream_service import StreamService

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post("/stream")
async def stream_task(
    request: StreamRequest,
    jwt_service: Annotated[JwtService, Depends(get_jwt_service)] = None,  # noqa: RUF013
    stream_service: Annotated[StreamService, Depends(get_stream_service)] = None,  # noqa: RUF013
) -> EventSourceResponse:
    """
    SSE流式接口：通过Redis历史回放 + PubSub实时订阅，向前端推送代码生成Chunk。

    返回格式兼容OpenAI chat.completion.chunk规范，最终发送 data: [DONE] 关闭流。

    流程：
    1. 校验JWT Token
    2. 从Redis List回放历史Chunk
    3. 订阅Redis PubSub接收增量Chunk（基于seq去重）
    4. 收到finish_reason=stop后发送[DONE]并关闭连接
    """
    # Step 0: JWT校验
    try:
        claims = jwt_service.validate_token(request.token)
        logger.info(
            "JWT校验通过: task_id=%s, user_id=%s",
            request.task_id,
            claims.get("sub"),
        )
    except jwt.ExpiredSignatureError:
        logger.warning("JWT令牌已过期: task_id=%s", request.task_id)
        raise HTTPException(status_code=401, detail="JWT令牌已过期")
    except jwt.InvalidTokenError:
        logger.warning("JWT令牌无效: task_id=%s", request.task_id)
        raise HTTPException(status_code=401, detail="JWT令牌无效")

    return EventSourceResponse(
        _event_generator(request.task_id, stream_service),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )

async def _event_generator(task_id: str, stream_service: StreamService):
    """SSE事件生成器：先订阅 -> 后回放 -> 持续监听(带心跳) -> seq去重"""
    last_seq = -1
    pubsub = None

    try:
        # 👑【核心修复 1】：先订阅 PubSub！确保不错过任何增量消息
        pubsub = await stream_service.subscribe_channel(task_id)

        # Step 1: 获取历史并回放
        try:
            history = await stream_service.replay_history(task_id)
            logger.info("历史回放: task_id=%s, chunks=%d", task_id, len(history))
            for chunk in history:
                last_seq = max(last_seq, chunk.seq)
                _log_chunk(task_id, chunk, "回放")
                yield ServerSentEvent(data=chunk.model_dump_json())
        except Exception:
            logger.exception("回放历史Chunk失败: task_id=%s", task_id)

        # 如果历史中已包含完成信号，直接关闭
        if history and any(c.is_done for c in history):
            logger.info("历史中已包含完成信号，关闭SSE: task_id=%s", task_id)
            yield ServerSentEvent(data="[DONE]")
            return

        # Step 2: 监听增量 Chunk (使用超时机制替代无尽阻塞)
        while True:
            # 👑【核心修复 2】：使用 get_message 并设置 timeout，防止长时间无输出导致网关掐断
            message = await pubsub.get_message(ignore_subscribe_messages=True, timeout=15.0)

            # 如果 15 秒内没有任何消息（AI 正在长时间思考），发送 SSE 心跳注释
            if message is None:
                logger.debug("SSE心跳保活: task_id=%s", task_id)
                # 发送格式为 ": ping\n\n" 的 SSE 注释，前端 EventSource 会自动忽略
                yield ServerSentEvent(comment="ping")
                continue

            # 处理收到的消息
            raw_data = message["data"]
            if isinstance(raw_data, bytes):
                raw_data = raw_data.decode("utf-8")

            try:
                chunk = ChatCompletionChunk.model_validate_json(raw_data)
            except Exception:
                logger.exception("反序列化PubSub消息失败: %s", raw_data)
                continue

            # 基于 seq 去重：跳过已在历史中回放过的，或者在订阅缝隙中重复收到的 Chunk
            if chunk.seq <= last_seq:
                logger.debug("SSE去重跳过: task_id=%s, seq=%d (当前last_seq=%d)", task_id, chunk.seq, last_seq)
                continue

            # 更新 last_seq 并下发
            last_seq = chunk.seq
            _log_chunk(task_id, chunk, "推送")
            yield ServerSentEvent(data=chunk.model_dump_json())

            # 收到结束信号
            if chunk.is_done:
                logger.info("收到完成信号，关闭SSE: task_id=%s", task_id)
                yield ServerSentEvent(data="[DONE]")
                break

    except asyncio.CancelledError:
        # 客户端（前端或网关）主动断开连接
        logger.info("SSE连接被客户端主动断开: task_id=%s", task_id)
    except Exception:
        logger.exception("SSE流处理异常: task_id=%s", task_id)
    finally:
        # 确保资源释放，无论发生什么情况都退订
        if pubsub is not None:
            await stream_service.unsubscribe_channel(pubsub, task_id)
            logger.debug("PubSub退订成功: task_id=%s", task_id)


# async def _event_generator(task_id: str, stream_service: StreamService):
#     """SSE事件生成器：历史回放 + 实时订阅 + seq去重，直接透传OpenAI格式"""
#     last_seq = -1

#     # Step 1: 从Redis List回放历史Chunk
#     try:
#         history = await stream_service.replay_history(task_id)
#         logger.info("历史回放: task_id=%s, chunks=%d", task_id, len(history))
#         for chunk in history:
#             last_seq = max(last_seq, chunk.seq)
#             _log_chunk(task_id, chunk, "回放")
#             yield ServerSentEvent(data=chunk.model_dump_json())
#     except Exception:
#         logger.exception("回放历史Chunk失败: task_id=%s", task_id)

#     # 如果历史中已包含完成信号，发送[DONE]并结束
#     if history and any(c.is_done for c in history):
#         logger.info("历史中已包含完成信号，关闭SSE: task_id=%s", task_id)
#         yield ServerSentEvent(data="[DONE]")
#         return

#     # Step 2: 订阅Redis PubSub接收增量Chunk
#     pubsub = None
#     try:
#         pubsub = await stream_service.subscribe_channel(task_id)
#         async for message in pubsub.listen():
#             if message["type"] != "message":
#                 continue

#             raw_data = message["data"]
#             if isinstance(raw_data, bytes):
#                 raw_data = raw_data.decode("utf-8")

#             try:
#                 chunk = ChatCompletionChunk.model_validate_json(raw_data)
#             except Exception:
#                 logger.exception("反序列化PubSub消息失败: %s", raw_data)
#                 continue

#             # 基于seq去重：跳过已在历史中回放过的Chunk
#             if chunk.seq <= last_seq:
#                 logger.debug("SSE去重跳过: task_id=%s, seq=%d", task_id, chunk.seq)
#                 continue

#             _log_chunk(task_id, chunk, "推送")
#             yield ServerSentEvent(data=chunk.model_dump_json())

#             if chunk.is_done:
#                 logger.info("收到完成信号，关闭SSE: task_id=%s", task_id)
#                 yield ServerSentEvent(data="[DONE]")
#                 break

#     except asyncio.CancelledError:
#         logger.info("SSE连接被客户端断开: task_id=%s", task_id)
#     except Exception:
#         logger.exception("PubSub订阅异常: task_id=%s", task_id)
#     finally:
#         if pubsub is not None:
#             await stream_service.unsubscribe_channel(pubsub, task_id)


def _log_chunk(task_id: str, chunk: ChatCompletionChunk, phase: str) -> None:
    content = chunk.delta_content
    logger.debug(
        "SSE%s: task_id=%s, seq=%d, finish_reason=%s, content=%s",
        phase,
        task_id,
        chunk.seq,
        chunk.choices[0].finish_reason,
        content[:100] if content else "",
    )
