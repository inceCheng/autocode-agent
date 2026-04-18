import json
import logging

from aiokafka import AIOKafkaConsumer

from app.config.settings import Settings

logger = logging.getLogger(__name__)

_consumer: AIOKafkaConsumer | None = None


async def init_kafka_consumer(settings: Settings) -> None:
    """初始化异步Kafka消费者"""
    global _consumer  # noqa: PLW0603
    _consumer = AIOKafkaConsumer(
        settings.kafka_topic,
        bootstrap_servers=settings.kafka_bootstrap_servers,
        group_id=settings.kafka_consumer_group,
        auto_offset_reset="earliest",
        enable_auto_commit=False,
        key_deserializer=lambda k: k.decode("utf-8") if k else None,
        value_deserializer=lambda v: json.loads(v.decode("utf-8")),
    )
    await _consumer.start()
    logger.info(
        "Kafka消费者初始化成功: servers=%s, topic=%s, group=%s",
        settings.kafka_bootstrap_servers,
        settings.kafka_topic,
        settings.kafka_consumer_group,
    )


def get_kafka_consumer() -> AIOKafkaConsumer:
    """获取已初始化的Kafka消费者实例"""
    if _consumer is None:
        raise RuntimeError("Kafka消费者未初始化，请先调用 init_kafka_consumer()")
    return _consumer


async def close_kafka_consumer() -> None:
    """优雅关闭Kafka消费者连接"""
    global _consumer  # noqa: PLW0603
    if _consumer is not None:
        await _consumer.stop()
        _consumer = None
        logger.info("Kafka消费者连接已关闭")
