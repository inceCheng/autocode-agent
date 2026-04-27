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
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.config.kafka import close_kafka_consumer, init_kafka_consumer
from app.config.kafka_producer import close_kafka_producer, init_kafka_producer
from app.config.redis import close_redis_client, init_redis_client
from app.config.settings import get_settings
from app.controller.html_controller import router as html_router
from app.controller.route_controller import router as route_router
from app.controller.stream_controller import router as stream_router
from app.controller.title_controller import router as title_router
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

    try:
        await init_kafka_producer(settings)
        logger.info("Kafka生产者已启动")
    except Exception:
        logger.exception("Kafka生产者初始化失败，请检查配置")

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

    await close_kafka_producer()
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

    # ==================== CORS配置 ====================
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # ==================== 注册API路由 ====================
    app.include_router(route_router, prefix="/api/ai", tags=["ai"])
    app.include_router(html_router, prefix="/api/ai", tags=["ai"])
    app.include_router(stream_router, prefix="/api/ai", tags=["ai"])
    app.include_router(title_router, prefix="/api/ai", tags=["ai"])

    # ==================== 挂载静态文件服务 ====================
    # 挂载代码输出根目录（上一级目录的 static/output），使生成的文件可通过URL直接访问
    # 路径格式: {code_output_root_dir}/{preview_path}/{type}_{appId}/
    code_output_dir = Path(settings.code_output_root_dir).resolve()
    code_output_dir.mkdir(parents=True, exist_ok=True)
    app.mount("/static/code_output", StaticFiles(directory=str(code_output_dir)), name="code-output-static")

    return app


app = create_app()
