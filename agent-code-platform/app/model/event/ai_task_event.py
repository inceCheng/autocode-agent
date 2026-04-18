from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class TaskInfo(BaseModel):
    task_id: str = Field(alias="taskId")
    user_id: str = Field(alias="userId")
    project_type: str = Field(alias="projectType")

    model_config = ConfigDict(populate_by_name=True)


class Payload(BaseModel):
    prompt: str
    context_messages: list[Any] = Field(default_factory=list, alias="contextMessages")

    model_config = ConfigDict(populate_by_name=True)


class AiTaskEvent(BaseModel):
    event_id: str = Field(alias="eventId")
    timestamp: int
    trace_id: str = Field(alias="traceId")
    task: TaskInfo
    payload: Payload

    model_config = ConfigDict(populate_by_name=True)
