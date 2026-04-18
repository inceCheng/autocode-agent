import time
import uuid
from typing import Any

from pydantic import BaseModel, Field


class FileMeta(BaseModel):
    """单个文件的元信息"""

    filename: str = Field(..., description="文件名")
    file_path: str = Field(..., description="服务器本地绝对路径")
    url_path: str = Field(..., description="HTTP访问路径")


class CompletionMetadata(BaseModel):
    """任务完成时的元数据，兼容单文件和多文件场景"""

    filename: str | None = Field(default=None, description="主文件名（单文件场景）")
    file_path: str | None = Field(default=None, description="主文件本地路径（单文件场景）")
    url_path: str | None = Field(default=None, description="主文件HTTP路径（单文件场景）")
    files: list[FileMeta] | None = Field(default=None, description="多文件列表（多文件场景）")


# ==================== OpenAI chat.completion.chunk 规范 ====================


class ChoiceDelta(BaseModel):
    """单个Choice的增量内容"""

    content: str | None = Field(default=None, description="增量文本内容")
    metadata: dict[str, Any] | None = Field(
        default=None, description="文件元数据（仅finish时携带）"
    )


class StreamChoice(BaseModel):
    """OpenAI兼容的Choice结构"""

    index: int = Field(default=0, description="Choice序号")
    delta: ChoiceDelta = Field(..., description="增量内容")
    finish_reason: str | None = Field(
        default=None, description="结束原因: null/stop/error"
    )


class ChatCompletionChunk(BaseModel):
    """OpenAI兼容的 chat.completion.chunk 结构，seq为扩展字段用于去重"""

    id: str = Field(..., description="完成ID，格式: chatcmpl-<uuid>")
    object: str = Field(default="chat.completion.chunk")
    created: int = Field(default_factory=lambda: int(time.time()))
    model: str = Field(default="agent-code-platform", description="模型名称")
    choices: list[StreamChoice] = Field(..., description="Choice列表")
    seq: int = Field(default=0, description="[扩展] 单调递增序号，用于SSE去重")

    @staticmethod
    def new_content(
        seq: int,
        content: str,
        completion_id: str,
        model: str = "agent-code-platform",
    ) -> "ChatCompletionChunk":
        """构建内容增量Chunk"""
        return ChatCompletionChunk(
            id=completion_id,
            model=model,
            choices=[
                StreamChoice(
                    index=0, delta=ChoiceDelta(content=content), finish_reason=None
                )
            ],
            seq=seq,
        )

    @staticmethod
    def new_done(
        seq: int,
        metadata: dict[str, Any] | None,
        completion_id: str,
        model: str = "agent-code-platform",
    ) -> "ChatCompletionChunk":
        """构建完成信号Chunk"""
        return ChatCompletionChunk(
            id=completion_id,
            model=model,
            choices=[
                StreamChoice(
                    index=0,
                    delta=ChoiceDelta(content=None, metadata=metadata),
                    finish_reason="stop",
                )
            ],
            seq=seq,
        )

    @staticmethod
    def new_error(
        seq: int,
        error_msg: str,
        completion_id: str,
        model: str = "agent-code-platform",
    ) -> "ChatCompletionChunk":
        """构建错误Chunk"""
        return ChatCompletionChunk(
            id=completion_id,
            model=model,
            choices=[
                StreamChoice(
                    index=0,
                    delta=ChoiceDelta(content=error_msg),
                    finish_reason="error",
                )
            ],
            seq=seq,
        )

    @property
    def is_done(self) -> bool:
        return self.choices[0].finish_reason == "stop"

    @property
    def delta_content(self) -> str | None:
        return self.choices[0].delta.content
