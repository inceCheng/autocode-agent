from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class TaskInfo(BaseModel):
    task_id: str = Field(alias="taskId")
    user_id: str = Field(alias="userId")
    project_type: str = Field(alias="projectType")
    preview_path: str = Field(alias="previewPath")
    app_id: str | None = Field(default=None, alias="appId")
    task_type: str = Field(default="GENERATE", alias="taskType")
    base_version_id: str | None = Field(default=None, alias="baseVersionId")
    target_version_id: str | None = Field(default=None, alias="targetVersionId")
    base_source_path: str | None = Field(default=None, alias="baseSourcePath")
    target_source_path: str | None = Field(default=None, alias="targetSourcePath")

    model_config = ConfigDict(populate_by_name=True)


class Payload(BaseModel):
    prompt: str
    context_messages: list[Any] = Field(default_factory=list, alias="contextMessages")
    selected_elements: list[Any] = Field(default_factory=list, alias="selectedElements")
    scope: str = "single"

    model_config = ConfigDict(populate_by_name=True)


class AiTaskEvent(BaseModel):
    event_id: str = Field(alias="eventId")
    timestamp: int
    trace_id: str = Field(alias="traceId")
    task: TaskInfo
    payload: Payload

    model_config = ConfigDict(populate_by_name=True)
