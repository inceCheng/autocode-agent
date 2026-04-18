from pydantic import BaseModel, Field


class RouteRequest(BaseModel):
    prompt: str = Field(..., description="用户的代码生成需求提示词")
