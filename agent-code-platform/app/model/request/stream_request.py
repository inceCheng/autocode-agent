from pydantic import BaseModel, Field


class StreamRequest(BaseModel):
    """SSE流式请求DTO，通过JSON Body传递task_id和JWT令牌"""

    task_id: str = Field(..., description="任务ID，由Java后端创建应用时生成")
    token: str = Field(..., description="JWT令牌（Java后端创建应用时返回）")
