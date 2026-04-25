from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


''' 从环境变量.env 文件中读取配置，并做缓存复用 '''
class Settings(BaseSettings):
    # ==================== 应用基础配置 ====================
    app_name: str = "ai-agent-assistant-platform"
    app_debug: bool = False
    route_api_key: SecretStr
    route_codegen_base_url: str = "https://api.minimaxi.com/v1"
    route_codegen_model_name: str = "MiniMax-M2.7"
    route_codegen_temperature: float = 0.3


    # ==================== HTML代码生成模型配置 ====================
    # 大模型API密钥（支持OpenAI兼容接口）
    html_codegen_api_key: SecretStr
    # 大模型API基础URL（如DashScope兼容模式地址）
    html_codegen_base_url: str = "https://api.minimaxi.com/v1"
    # 大模型名称（如 qwen3.5-flash, qwen-plus 等）
    html_codegen_model_name: str = "MiniMax-M2.7"
    # 生成温度参数，控制创造性（0.0=确定性，1.0=高随机性）
    html_codegen_temperature: float = 0.7

    # ==================== 代码输出根目录配置 ====================
    # 代码输出根目录（对应Java的 CODE_OUTPUT_ROOT_DIR），相对项目根目录的上一级
    code_output_root_dir: str = "../tmp/code_output"
    # 预览路径，格式如 /2026/04/23（暂时写死，后期加动态逻辑）
    preview_path: str = "/2026/04/23"

    # ==================== HTML代码生成输出配置 ====================
    # 生成的HTML文件保存目录（相对于项目根目录）
    html_output_dir: str = "./output/html"
    # 系统提示词文件路径（相对于项目根目录）
    html_system_prompt_path: str = "app/prompts/codegen-html-system-prompt.txt"

    # ==================== 多文件代码生成模型配置 ====================
    # 大模型API密钥（默认复用HTML生成密钥）
    multi_file_codegen_api_key: SecretStr | None = None
    # 大模型API基础URL
    multi_file_codegen_base_url: str = "https://api.minimaxi.com/v1"
    # 大模型名称
    multi_file_codegen_model_name: str = "MiniMax-M2.7"
    # 生成温度参数
    multi_file_codegen_temperature: float = 0.7

    # ==================== 多文件代码生成输出配置 ====================
    # 生成的多文件项目保存目录
    multi_file_output_dir: str = "./output/multi_file"
    # 系统提示词文件路径
    multi_file_system_prompt_path: str = "app/prompts/codegen-multi-file-system-prompt.txt"

    # ==================== Vue工程项目生成模型配置 ====================
    # 大模型API密钥（默认复用HTML生成密钥）
    vue_project_codegen_api_key: SecretStr | None = None
    # 大模型API基础URL
    vue_project_codegen_base_url: str = "https://api.minimaxi.com/v1"
    # 大模型名称（需支持Tool Calling）
    vue_project_codegen_model_name: str = "MiniMax-M2.7"
    # 生成温度参数
    vue_project_codegen_temperature: float = 0.7

    # ==================== Vue工程项目生成输出配置 ====================
    # 生成的Vue工程项目保存目录
    vue_project_output_dir: str = "./output/vue_project"
    # 系统提示词文件路径
    vue_project_system_prompt_path: str = "app/prompts/codegen-vue-project-system-prompt.txt"
    # 静态文件URL前缀（与main.py中挂载路径保持一致）
    vue_project_base_url_prefix: str = "/static/vue_project"

    # ==================== Kafka 配置 ====================
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_topic: str = "agent-generation-tasks"
    kafka_consumer_group: str = "agent-code-platform-worker"
    kafka_result_topic: str = "task-result-topic"
    kafka_max_poll_interval_ms: int = 1_800_000
    kafka_poll_timeout_ms: int = 1_000
    kafka_poll_max_records: int = 1

    # ==================== Redis 配置 ====================
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: str = ""
    redis_db: int = 0
    redis_task_history_ttl: int = 3600  # 任务历史过期时间（秒），默认1小时
    redis_stream_max_len: int = 10_000
    redis_stream_block_ms: int = 15_000
    redis_sse_idle_timeout_sec: int = 180
    redis_lock_ttl_sec: int = 300
    redis_lock_renew_interval_sec: int = 30
    generation_timeout_sec: int = 900
    generation_retry_attempts: int = 0

    # ==================== JWT 配置 ====================
    # Base64编码的密钥，必须与Java后端 jwt.secret 保持一致
    jwt_secret: str
    jwt_algorithm: str = "HS256"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
