import json
import logging
import time
from enum import StrEnum

from pydantic import BaseModel, Field

from app.config.redis import get_redis_client
from app.config.settings import get_settings
from app.model.response.stream_response import ChatCompletionChunk

logger = logging.getLogger(__name__)

_STREAM_KEY_PREFIX = "ai:stream:"


class StreamEventType(StrEnum):
    CHUNK = "chunk"
    DONE = "done"
    ERROR = "error"


class StreamEvent(BaseModel):
    """A normalized Redis Stream event that can be sent as an SSE event."""

    id: str
    event: StreamEventType
    seq: int = 0
    data: str = ""
    code: str | None = None
    message: str | None = None
    created_at: int = Field(alias="createdAt")


def stream_key(task_id: str) -> str:
    return f"{_STREAM_KEY_PREFIX}{task_id}"


class StreamService:
    """流式数据分发服务：Redis Stream 持久化 + SSE 可靠回放。"""

    async def push_chunk(self, task_id: str, chunk: ChatCompletionChunk) -> str:
        """写入普通内容 chunk。"""
        return await self._push_event(
            task_id,
            StreamEventType.CHUNK,
            seq=chunk.seq,
            data=chunk.model_dump_json(),
        )

    async def push_done(self, task_id: str, done_chunk: ChatCompletionChunk) -> str:
        """写入完成事件并设置 Stream TTL。"""
        event_id = await self._push_event(
            task_id,
            StreamEventType.DONE,
            seq=done_chunk.seq,
            data=done_chunk.model_dump_json(),
        )
        await self._expire_stream(task_id)
        return event_id

    async def push_error(
        self,
        task_id: str,
        error_chunk: ChatCompletionChunk,
        *,
        code: str = "GENERATION_FAILED",
        message: str = "代码生成失败，请稍后重试",
    ) -> str:
        """写入失败事件并设置 Stream TTL。"""
        event_id = await self._push_event(
            task_id,
            StreamEventType.ERROR,
            seq=error_chunk.seq,
            data=error_chunk.model_dump_json(),
            code=code,
            message=message,
        )
        await self._expire_stream(task_id)
        return event_id

    async def replay_history(
        self,
        task_id: str,
        *,
        last_event_id: str | None = None,
    ) -> list[StreamEvent]:
        """读取历史事件；传入 last_event_id 时只返回其后的事件。"""
        client = get_redis_client()
        min_id = f"({last_event_id}" if last_event_id else "-"
        rows = await client.xrange(stream_key(task_id), min=min_id, max="+")
        return [self._decode_event(row_id, fields) for row_id, fields in rows]

    async def read_new_events(
        self,
        task_id: str,
        *,
        last_event_id: str,
        block_ms: int | None = None,
        count: int = 100,
    ) -> list[StreamEvent]:
        """阻塞读取 last_event_id 之后的新事件。"""
        client = get_redis_client()
        settings = get_settings()
        response = await client.xread(
            {stream_key(task_id): last_event_id},
            count=count,
            block=block_ms or settings.redis_stream_block_ms,
        )
        events: list[StreamEvent] = []
        for _, rows in response:
            events.extend(self._decode_event(row_id, fields) for row_id, fields in rows)
        return events

    async def _push_event(
        self,
        task_id: str,
        event: StreamEventType,
        *,
        seq: int,
        data: str = "",
        code: str | None = None,
        message: str | None = None,
    ) -> str:
        client = get_redis_client()
        settings = get_settings()
        fields = {
            "event": event.value,
            "seq": str(seq),
            "data": data,
            "createdAt": str(int(time.time() * 1000)),
        }
        if code:
            fields["code"] = code
        if message:
            fields["message"] = message
        return await client.xadd(
            stream_key(task_id),
            fields,
            maxlen=settings.redis_stream_max_len,
            approximate=True,
        )

    async def _expire_stream(self, task_id: str) -> None:
        settings = get_settings()
        await get_redis_client().expire(
            stream_key(task_id),
            settings.redis_task_history_ttl,
        )

    @staticmethod
    def _decode_event(row_id, fields) -> StreamEvent:
        decoded = {}
        for key, value in fields.items():
            key = key.decode("utf-8") if isinstance(key, bytes) else key
            value = value.decode("utf-8") if isinstance(value, bytes) else value
            decoded[key] = value
        event = decoded.get("event", StreamEventType.CHUNK.value)
        if event not in {item.value for item in StreamEventType}:
            logger.warning("未知Redis Stream事件类型: %s", event)
            event = StreamEventType.CHUNK.value
        return StreamEvent(
            id=row_id.decode("utf-8") if isinstance(row_id, bytes) else row_id,
            event=event,
            seq=int(decoded.get("seq") or 0),
            data=decoded.get("data") or "",
            code=decoded.get("code"),
            message=decoded.get("message"),
            createdAt=int(decoded.get("createdAt") or 0),
        )


def stream_event_payload(event: StreamEvent) -> str:
    """Build SSE data payload while keeping chunk data compatible with clients."""
    if event.event in {StreamEventType.CHUNK, StreamEventType.DONE} and event.data:
        return event.data
    if event.event == StreamEventType.ERROR and event.data:
        try:
            payload = json.loads(event.data)
        except json.JSONDecodeError:
            payload = {"message": event.data}
        if event.code:
            payload["code"] = event.code
        if event.message:
            payload["message"] = event.message
        return json.dumps(payload, ensure_ascii=False)
    return json.dumps(
        {
            "event": event.event.value,
            "seq": event.seq,
            "code": event.code,
            "message": event.message,
            "createdAt": event.created_at,
        },
        ensure_ascii=False,
    )
