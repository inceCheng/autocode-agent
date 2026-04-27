from pydantic import BaseModel, Field


class TitleRequest(BaseModel):
    prompt: str = Field(..., description="用户的对话输入提示词")
