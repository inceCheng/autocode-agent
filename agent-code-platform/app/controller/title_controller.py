from typing import Annotated

from fastapi import APIRouter, Depends

from app.dependency.container import get_title_gen_service
from app.model.request.title_request import TitleRequest
from app.model.response.title_response import TitleResponse
from app.service.title_service import TitleGenService

router = APIRouter()


@router.post("/generate-title", response_model=TitleResponse)
async def generate_title(
    request: TitleRequest,
    title_service: Annotated[TitleGenService, Depends(get_title_gen_service)],
) -> TitleResponse:
    """对话标题生成：根据用户提示词生成简短中文摘要标题"""
    return await title_service.generate_title(prompt=request.prompt)
