from pydantic import BaseModel, Field


class TitleResponse(BaseModel):
    title: str = Field(..., description="生成的对话标题")
