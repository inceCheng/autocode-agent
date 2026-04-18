from functools import lru_cache

from app.config.settings import get_settings
from app.service.html_service import HtmlGenService
from app.service.jwt_service import JwtService
from app.service.route_service import RouteService
from app.service.stream_service import StreamService


@lru_cache(maxsize=1)
def get_route_service() -> RouteService:
    """依赖注入：获取路由服务单例"""
    return RouteService()


@lru_cache(maxsize=1)
def get_html_gen_service() -> HtmlGenService:
    """依赖注入：获取HTML代码生成服务单例"""
    return HtmlGenService()


@lru_cache(maxsize=1)
def get_jwt_service() -> JwtService:
    """依赖注入：获取JWT校验服务单例"""
    settings = get_settings()
    return JwtService(secret=settings.jwt_secret, algorithm=settings.jwt_algorithm)


@lru_cache(maxsize=1)
def get_stream_service() -> StreamService:
    """依赖注入：获取流式分发服务单例"""
    return StreamService()
