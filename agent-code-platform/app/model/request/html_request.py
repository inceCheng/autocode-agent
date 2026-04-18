from pydantic import BaseModel, Field


class HtmlGenRequest(BaseModel):
    """HTML代码生成请求DTO，封装用户输入的网页需求描述"""

    prompt: str = Field(
        ...,
        min_length=1,
        max_length=5000,
        description="用户的网页需求自然语言描述，如'创建一个登录页面'",
    )
