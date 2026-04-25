import asyncio
import logging
import time
from typing import Annotated

import jwt
from fastapi import APIRouter, Depends, HTTPException, Request
from sse_starlette.sse import EventSourceResponse, ServerSentEvent

from app.config.settings import get_settings
from app.dependency.container import get_jwt_service, get_stream_service
from app.model.request.stream_request import StreamRequest
from app.service.jwt_service import JwtService
from app.service.stream_service import (
    StreamEvent,
    StreamEventType,
    StreamService,
    stream_event_payload,
)

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post("/stream")
async def stream_task(
    request_body: StreamRequest,
    http_request: Request,
    jwt_service: Annotated[JwtService, Depends(get_jwt_service)] = None,  # noqa: RUF013
    stream_service: Annotated[StreamService, Depends(get_stream_service)] = None,  # noqa: RUF013
) -> EventSourceResponse:
    """
    SSE流式接口：通过Redis Stream历史回放 + XREAD 阻塞读取推送生成事件。

    连接关闭不表示成功；前端应以显式 done/error 事件判断终态。
    """
    try:
        claims = jwt_service.validate_token(request_body.token)
        logger.info(
            "JWT校验通过: task_id=%s, user_id=%s",
            request_body.task_id,
            claims.get("sub"),
        )
    except jwt.ExpiredSignatureError:
        logger.warning("JWT令牌已过期: task_id=%s", request_body.task_id)
        raise HTTPException(status_code=401, detail="JWT令牌已过期") from None
    except jwt.InvalidTokenError:
        logger.warning("JWT令牌无效: task_id=%s", request_body.task_id)
        raise HTTPException(status_code=401, detail="JWT令牌无效") from None

    last_event_id = (
        request_body.last_event_id
        or http_request.headers.get("last-event-id")
        or http_request.headers.get("Last-Event-ID")
    )

    return EventSourceResponse(
        _event_generator(request_body.task_id, stream_service, last_event_id),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


async def _event_generator(
    task_id: str,
    stream_service: StreamService,
    last_event_id: str | None,
):
    """SSE事件生成器：Stream历史回放 -> XREAD阻塞监听 -> 心跳/空闲超时。"""
    settings = get_settings()
    redis_last_id = last_event_id or "0-0"
    idle_started_at = time.monotonic()

    try:
        try:
            history = await stream_service.replay_history(
                task_id,
                last_event_id=last_event_id,
            )
            logger.info("历史回放: task_id=%s, events=%d", task_id, len(history))
            for event in history:
                redis_last_id = event.id
                _log_event(task_id, event, "回放")
                yield _to_sse(event)
                if event.event in {StreamEventType.DONE, StreamEventType.ERROR}:
                    logger.info("历史中已包含终态事件，关闭SSE: task_id=%s", task_id)
                    return
        except Exception:
            logger.exception("回放Redis Stream失败: task_id=%s", task_id)

        while True:
            events = await stream_service.read_new_events(
                task_id,
                last_event_id=redis_last_id,
                block_ms=settings.redis_stream_block_ms,
            )
            if not events:
                logger.debug("SSE心跳保活: task_id=%s", task_id)
                yield ServerSentEvent(
                    event="ping",
                    data='{"event":"ping"}',
                )
                idle_seconds = time.monotonic() - idle_started_at
                if idle_seconds >= settings.redis_sse_idle_timeout_sec:
                    logger.info(
                        "SSE等待超时，关闭本次连接但不标记失败: task_id=%s",
                        task_id,
                    )
                    return
                continue

            idle_started_at = time.monotonic()
            for event in events:
                redis_last_id = event.id
                _log_event(task_id, event, "推送")
                yield _to_sse(event)
                if event.event in {StreamEventType.DONE, StreamEventType.ERROR}:
                    logger.info("收到终态事件，关闭SSE: task_id=%s", task_id)
                    return

    except asyncio.CancelledError:
        logger.info("SSE连接被客户端主动断开: task_id=%s", task_id)
    except Exception:
        logger.exception("SSE流处理异常: task_id=%s", task_id)


def _to_sse(event: StreamEvent) -> ServerSentEvent:
    return ServerSentEvent(
        id=event.id,
        event=event.event.value,
        data=stream_event_payload(event),
    )


def _log_event(task_id: str, event: StreamEvent, phase: str) -> None:
    logger.debug(
        "SSE%s: task_id=%s, id=%s, event=%s, seq=%d",
        phase,
        task_id,
        event.id,
        event.event,
        event.seq,
    )
