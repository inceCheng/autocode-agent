from enum import Enum

from pydantic import BaseModel, ConfigDict, Field


class TaskStatus(str, Enum):
    """任务状态"""

    PENDING = "PENDING"
    PROCESSING = "PROCESSING"
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"
    WAITING_RETRY = "WAITING_RETRY"
    CANCELLED = "CANCELLED"
    STREAMING = "STREAMING"
    INTERRUPTED = "INTERRUPTED"


class MessageType(str, Enum):
    """消息类型"""

    AI = "ai"
    USER = "user"
    SYSTEM = "system"


class TaskResultEvent(BaseModel):
    """Kafka结果消息模型，发送到 task-result-topic"""

    task_id: str = Field(alias="taskId")
    app_id: int = Field(alias="appId")
    user_id: int = Field(alias="userId")
    status: TaskStatus = Field(alias="status")
    message_type: MessageType = Field(default=MessageType.AI, alias="messageType")
    content: str = Field(default="", alias="content")
    seq: int = Field(default=0, alias="seq")
    is_end: bool = Field(default=False, alias="isEnd")
    error_msg: str | None = Field(default=None, alias="errorMsg")
    timestamp: int = Field(alias="timestamp")

    model_config = ConfigDict(populate_by_name=True)
