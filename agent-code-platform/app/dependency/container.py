from functools import lru_cache

from app.config.settings import get_settings
from app.service.edit_service import EditGenService
from app.service.html_service import HtmlGenService
from app.service.jwt_service import JwtService
from app.service.multi_file_service import MultiFileGenService
from app.service.route_service import RouteService
from app.service.stream_service import StreamService
from app.service.title_service import TitleGenService
from app.service.vue_project_service import VueProjectGenService


@lru_cache(maxsize=1)
def get_route_service() -> RouteService:
    """依赖注入：获取路由服务单例"""
    return RouteService()


@lru_cache(maxsize=1)
def get_html_gen_service() -> HtmlGenService:
    """依赖注入：获取HTML代码生成服务单例"""
    return HtmlGenService()


@lru_cache(maxsize=1)
def get_multi_file_gen_service() -> MultiFileGenService:
    """依赖注入：获取多文件代码生成服务单例"""
    return MultiFileGenService()


@lru_cache(maxsize=1)
def get_jwt_service() -> JwtService:
    """依赖注入：获取JWT校验服务单例"""
    settings = get_settings()
    return JwtService(secret=settings.jwt_secret, algorithm=settings.jwt_algorithm)


@lru_cache(maxsize=1)
def get_stream_service() -> StreamService:
    """依赖注入：获取流式分发服务单例"""
    return StreamService()


@lru_cache(maxsize=1)
def get_vue_project_gen_service() -> VueProjectGenService:
    """依赖注入：获取Vue工程项目生成服务单例"""
    return VueProjectGenService()


@lru_cache(maxsize=1)
def get_edit_gen_service() -> EditGenService:
    """依赖注入：获取定点修改服务单例"""
    return EditGenService()


@lru_cache(maxsize=1)
def get_title_gen_service() -> TitleGenService:
    """依赖注入：获取对话标题生成服务单例"""
    return TitleGenService()
