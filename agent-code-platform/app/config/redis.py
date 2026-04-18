import logging

import redis.asyncio as aioredis

from app.config.settings import Settings

logger = logging.getLogger(__name__)

_redis_client: aioredis.Redis | None = None


async def init_redis_client(settings: Settings) -> None:
    """初始化异步Redis客户端连接池"""
    global _redis_client  # noqa: PLW0603
    _redis_client = aioredis.Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        password=settings.redis_password or None,
        db=settings.redis_db,
        decode_responses=True,
    )
    # 验证连接是否正常
    await _redis_client.ping()
    logger.info("Redis客户端初始化成功: %s:%d", settings.redis_host, settings.redis_port)


def get_redis_client() -> aioredis.Redis:
    """获取已初始化的Redis客户端实例"""
    if _redis_client is None:
        raise RuntimeError("Redis客户端未初始化，请先调用 init_redis_client()")
    return _redis_client


async def close_redis_client() -> None:
    """优雅关闭Redis客户端连接"""
    global _redis_client  # noqa: PLW0603
    if _redis_client is not None:
        await _redis_client.aclose()
        _redis_client = None
        logger.info("Redis客户端连接已关闭")
