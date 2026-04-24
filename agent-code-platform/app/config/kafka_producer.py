import json
import logging

from aiokafka import AIOKafkaProducer

from app.config.settings import Settings
from app.model.event.task_result_event import TaskResultEvent

logger = logging.getLogger(__name__)

_producer: AIOKafkaProducer | None = None


async def init_kafka_producer(settings: Settings) -> None:
    """初始化异步Kafka生产者"""
    global _producer  # noqa: PLW0603
    _producer = AIOKafkaProducer(
        bootstrap_servers=settings.kafka_bootstrap_servers,
        key_serializer=lambda k: k.encode("utf-8") if k else None,
        value_serializer=lambda v: json.dumps(v, ensure_ascii=False).encode("utf-8"),
        acks="all",
        linger_ms=10,
    )
    await _producer.start()
    logger.info(
        "Kafka生产者初始化成功: servers=%s, result_topic=%s",
        settings.kafka_bootstrap_servers,
        settings.kafka_result_topic,
    )


def get_kafka_producer() -> AIOKafkaProducer:
    """获取已初始化的Kafka生产者实例"""
    if _producer is None:
        raise RuntimeError("Kafka生产者未初始化，请先调用 init_kafka_producer()")
    return _producer


async def close_kafka_producer() -> None:
    """优雅关闭Kafka生产者连接，确保缓冲区消息刷盘"""
    global _producer  # noqa: PLW0603
    if _producer is not None:
        await _producer.stop()
        _producer = None
        logger.info("Kafka生产者连接已关闭")


async def send_task_result(event: TaskResultEvent) -> None:
    """
    发送任务结果消息到 task-result-topic。

    key=taskId 保证同一任务的消息有序落在同一partition。
    """
    producer = get_kafka_producer()
    from app.config.settings import get_settings

    topic = get_settings().kafka_result_topic
    try:
        await producer.send_and_wait(
            topic,
            value=event.model_dump(by_alias=True),
            key=event.task_id,
        )
    except Exception:
        logger.exception(
            "发送Kafka结果消息失败: task_id=%s, status=%s, seq=%d",
            event.task_id,
            event.status,
            event.seq,
        )
