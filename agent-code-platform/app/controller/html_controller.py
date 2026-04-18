from typing import Annotated

from fastapi import APIRouter, Depends

from app.dependency.container import get_html_gen_service
from app.model.request.html_request import HtmlGenRequest
from app.model.response.html_response import HtmlGenResponse
from app.service.html_service import HtmlGenService

router = APIRouter()


@router.post("/generate-html", response_model=HtmlGenResponse)
async def generate_html(
    request: HtmlGenRequest,
    html_gen_service: Annotated[HtmlGenService, Depends(get_html_gen_service)],
) -> HtmlGenResponse:
    """
    HTML网页生成接口。

    接收用户的自然语言需求描述，调用大模型生成对应的HTML网页代码，
    提取纯HTML内容后持久化保存到本地，返回文件访问路径。
    """
    return await html_gen_service.generate(prompt=request.prompt)
