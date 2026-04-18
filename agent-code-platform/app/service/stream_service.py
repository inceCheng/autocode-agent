import json
import logging

import redis.asyncio as aioredis

from app.config.redis import get_redis_client
from app.config.settings import get_settings
from app.model.response.stream_response import ChatCompletionChunk

logger = logging.getLogger(__name__)

_HISTORY_KEY_PREFIX = "task:"
_HISTORY_KEY_SUFFIX = ":history"
_CHANNEL_PREFIX = "channel:"


def _history_key(task_id: str) -> str:
    return f"{_HISTORY_KEY_PREFIX}{task_id}{_HISTORY_KEY_SUFFIX}"


def _channel_name(task_id: str) -> str:
    return f"{_CHANNEL_PREFIX}{task_id}"


class StreamService:
    """流式数据分发服务：封装Redis的List持久化和PubSub实时广播"""

    async def push_chunk(self, task_id: str, chunk: ChatCompletionChunk) -> None:
        """
        推送一个Chunk到Redis。先RPUSH持久化，再PUBLISH广播。
        此顺序确保SSE端先LRANGE再SUBSCRIBE时不会丢失数据。
        """
        client = get_redis_client()
        data = chunk.model_dump_json()
        key = _history_key(task_id)
        channel = _channel_name(task_id)
        await client.rpush(key, data)
        await client.publish(channel, data)

    async def push_done(self, task_id: str, done_chunk: ChatCompletionChunk) -> None:
        """
        推送完成信号并设置Redis Key的过期时间。
        """
        client = get_redis_client()
        data = done_chunk.model_dump_json()
        key = _history_key(task_id)
        channel = _channel_name(task_id)
        ttl = get_settings().redis_task_history_ttl
        await client.rpush(key, data)
        await client.publish(channel, data)
        await client.expire(key, ttl)

    async def replay_history(self, task_id: str) -> list[ChatCompletionChunk]:
        """
        从Redis List中读取任务的所有历史Chunk。
        用于SSE连接建立时补齐前端错过的数据。
        """
        client = get_redis_client()
        key = _history_key(task_id)
        raw_list = await client.lrange(key, 0, -1)
        chunks = []
        for raw in raw_list:
            try:
                chunks.append(ChatCompletionChunk.model_validate_json(raw))
            except Exception:
                logger.exception("反序列化历史Chunk失败: %s", raw)
        return chunks

    async def subscribe_channel(self, task_id: str) -> aioredis.client.PubSub:
        """
        订阅任务的Redis频道，返回PubSub对象供调用方迭代读取。
        """
        client = get_redis_client()
        pubsub = client.pubsub()
        channel = _channel_name(task_id)
        await pubsub.subscribe(channel)
        return pubsub

    @staticmethod
    async def unsubscribe_channel(pubsub: aioredis.client.PubSub, task_id: str) -> None:
        """取消订阅并关闭PubSub连接"""
        channel = _channel_name(task_id)
        await pubsub.unsubscribe(channel)
        await pubsub.aclose()
