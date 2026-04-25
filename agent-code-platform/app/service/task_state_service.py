import json
import logging
import time
import uuid
from dataclasses import dataclass
from enum import StrEnum

from app.config.redis import get_redis_client
from app.config.settings import get_settings

logger = logging.getLogger(__name__)

_STATUS_KEY_PREFIX = "ai:task:status:"
_LOCK_KEY_PREFIX = "ai:generate:lock:"

_RENEW_LOCK_SCRIPT = """
if redis.call("get", KEYS[1]) == ARGV[1] then
  return redis.call("expire", KEYS[1], ARGV[2])
end
return 0
"""

_RELEASE_LOCK_SCRIPT = """
if redis.call("get", KEYS[1]) == ARGV[1] then
  return redis.call("del", KEYS[1])
end
return 0
"""


class TaskRuntimeStatus(StrEnum):
    PENDING = "PENDING"
    PROCESSING = "PROCESSING"
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"
    WAITING_RETRY = "WAITING_RETRY"
    CANCELLED = "CANCELLED"

    @property
    def is_terminal(self) -> bool:
        return self in {
            TaskRuntimeStatus.SUCCESS,
            TaskRuntimeStatus.FAILED,
            TaskRuntimeStatus.CANCELLED,
        }


@dataclass(frozen=True)
class TaskLock:
    task_id: str
    worker_id: str
    token: str
    started_at: int

    @property
    def value(self) -> str:
        return json.dumps(
            {
                "workerId": self.worker_id,
                "token": self.token,
                "startedAt": self.started_at,
            },
            ensure_ascii=False,
            sort_keys=True,
        )


def status_key(task_id: str) -> str:
    return f"{_STATUS_KEY_PREFIX}{task_id}"


def lock_key(task_id: str) -> str:
    return f"{_LOCK_KEY_PREFIX}{task_id}"


class TaskStateService:
    """Redis task status + lease lock coordination for generation workers."""

    def __init__(self, worker_id: str | None = None) -> None:
        self.worker_id = worker_id or f"worker-{uuid.uuid4().hex[:12]}"

    async def get_status(self, task_id: str) -> TaskRuntimeStatus | None:
        raw = await get_redis_client().get(status_key(task_id))
        if raw is None:
            return None
        value = raw.decode("utf-8") if isinstance(raw, bytes) else raw
        try:
            return TaskRuntimeStatus(value)
        except ValueError:
            logger.warning("未知任务状态: task_id=%s, status=%s", task_id, value)
            return None

    async def set_status(self, task_id: str, status: TaskRuntimeStatus) -> None:
        await get_redis_client().set(status_key(task_id), status.value)

    async def has_valid_lock(self, task_id: str) -> bool:
        return await get_redis_client().exists(lock_key(task_id)) == 1

    async def acquire_lock(self, task_id: str) -> TaskLock | None:
        settings = get_settings()
        task_lock = TaskLock(
            task_id=task_id,
            worker_id=self.worker_id,
            token=uuid.uuid4().hex,
            started_at=int(time.time() * 1000),
        )
        acquired = await get_redis_client().set(
            lock_key(task_id),
            task_lock.value,
            nx=True,
            ex=settings.redis_lock_ttl_sec,
        )
        return task_lock if acquired else None

    async def renew_lock(self, task_lock: TaskLock) -> bool:
        settings = get_settings()
        result = await get_redis_client().eval(
            _RENEW_LOCK_SCRIPT,
            1,
            lock_key(task_lock.task_id),
            task_lock.value,
            settings.redis_lock_ttl_sec,
        )
        return result == 1

    async def release_lock(self, task_lock: TaskLock) -> bool:
        result = await get_redis_client().eval(
            _RELEASE_LOCK_SCRIPT,
            1,
            lock_key(task_lock.task_id),
            task_lock.value,
        )
        return result == 1

    async def owns_lock(self, task_lock: TaskLock) -> bool:
        raw = await get_redis_client().get(lock_key(task_lock.task_id))
        value = raw.decode("utf-8") if isinstance(raw, bytes) else raw
        return value == task_lock.value

    async def can_attempt_task(self, task_id: str) -> bool:
        status = await self.get_status(task_id)
        if status is None:
            logger.warning(
                "Redis任务状态不存在，按PENDING兼容处理: task_id=%s",
                task_id,
            )
            return True
        if status in {
            TaskRuntimeStatus.PENDING,
            TaskRuntimeStatus.WAITING_RETRY,
        }:
            return True
        if status == TaskRuntimeStatus.PROCESSING:
            has_lock = await self.has_valid_lock(task_id)
            if has_lock:
                return False
            logger.warning("发现PROCESSING僵尸任务，允许重新抢锁: task_id=%s", task_id)
            return True
        return False
