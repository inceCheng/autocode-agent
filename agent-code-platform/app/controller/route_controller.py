from typing import Annotated

from fastapi import APIRouter, Depends

from app.dependency.container import get_route_service
from app.model.request.route_request import RouteRequest
from app.model.response.route_response import RouteResponse
from app.service.route_service import RouteService

router = APIRouter()


@router.post("/route-project-type", response_model=RouteResponse)
async def route_project_type(
    request: RouteRequest,
    route_service: Annotated[RouteService, Depends(get_route_service)],
) -> RouteResponse:
    """智能路由：根据用户需求推断最合适的代码项目架构类型"""
    return await route_service.route(prompt=request.prompt)
