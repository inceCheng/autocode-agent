import asyncio
import logging
from contextlib import asynccontextmanager
from pathlib import Path

# 应用级别日志配置
logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s %(levelname)-8s %(name)s | %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
# 抑制第三方库的DEBUG噪声
for _lib in ("httpx", "httpcore", "aiokafka", "redis", "urllib3"):
    logging.getLogger(_lib).setLevel(logging.WARNING)

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from app.config.kafka import close_kafka_consumer, init_kafka_consumer
from app.config.redis import close_redis_client, init_redis_client
from app.config.settings import get_settings
from app.controller.html_controller import router as html_router
from app.controller.route_controller import router as route_router
from app.controller.stream_controller import router as stream_router
from app.worker.kafka_consumer import kafka_consumer_worker

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理：启动时初始化基础设施，关闭时清理资源"""
    settings = get_settings()

    # ---- Startup ----
    try:
        await init_redis_client(settings)
        logger.info("Redis连接已建立")
    except Exception:
        logger.exception("Redis初始化失败，请检查配置")

    try:
        await init_kafka_consumer(settings)
        logger.info("Kafka消费者已启动")
    except Exception:
        logger.exception("Kafka初始化失败，请检查配置")

    # 启动Kafka消费后台任务
    consumer_task = asyncio.create_task(kafka_consumer_worker())
    logger.info("Kafka消费者Worker已启动")

    yield

    # ---- Shutdown ----
    consumer_task.cancel()
    try:
        await consumer_task
    except asyncio.CancelledError:
        pass
    logger.info("Kafka消费者Worker已停止")

    await close_kafka_consumer()
    await close_redis_client()
    logger.info("应用资源已清理完毕")


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title=settings.app_name,
        debug=settings.app_debug,
        lifespan=lifespan,
    )

    # ==================== 注册API路由 ====================
    app.include_router(route_router, prefix="/api/ai", tags=["ai"])
    app.include_router(html_router, prefix="/api/ai", tags=["ai"])
    app.include_router(stream_router, prefix="/api/ai", tags=["ai"])

    # ==================== 挂载静态文件服务 ====================
    # 将HTML输出目录挂载为静态资源目录，使生成的HTML文件可通过URL直接访问
    html_output_dir = Path(settings.html_output_dir)
    html_output_dir.mkdir(parents=True, exist_ok=True)
    app.mount("/static/html", StaticFiles(directory=str(html_output_dir)), name="html-static")

    # 将多文件输出目录挂载为静态资源目录
    multi_file_output_dir = Path(settings.multi_file_output_dir)
    multi_file_output_dir.mkdir(parents=True, exist_ok=True)
    app.mount("/static/multi_file", StaticFiles(directory=str(multi_file_output_dir)), name="multi-file-static")

    return app


app = create_app()
