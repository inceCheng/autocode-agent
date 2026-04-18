from functools import lru_cache

from app.service.route_service import RouteService


@lru_cache(maxsize=1)
def get_route_service() -> RouteService:
    return RouteService()
