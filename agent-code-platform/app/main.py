from fastapi import FastAPI

from app.config.settings import get_settings
from app.controller.route_controller import router as route_router


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title=settings.app_name,
        debug=settings.app_debug,
    )
    app.include_router(route_router, prefix="/api/ai", tags=["ai"])
    return app


app = create_app()
