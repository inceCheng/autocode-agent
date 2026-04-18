from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


''' 从环境变量.env 文件中读取配置，并做缓存复用 '''
class Settings(BaseSettings):
    # ==================== 应用基础配置 ====================
    app_name: str = "ai-agent-assistant-platform"
    app_debug: bool = False
    dashscope_api_key: SecretStr

    # ==================== HTML代码生成模型配置 ====================
    # 大模型API密钥（支持OpenAI兼容接口）
    html_codegen_api_key: SecretStr
    # 大模型API基础URL（如DashScope兼容模式地址）
    html_codegen_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    # 大模型名称（如 qwen3.5-flash, qwen-plus 等）
    html_codegen_model_name: str = "qwen3.5-flash"
    # 生成温度参数，控制创造性（0.0=确定性，1.0=高随机性）
    html_codegen_temperature: float = 0.7

    # ==================== HTML代码生成输出配置 ====================
    # 生成的HTML文件保存目录（相对于项目根目录）
    html_output_dir: str = "./output/html"
    # 系统提示词文件路径（相对于项目根目录）
    html_system_prompt_path: str = "app/prompts/codegen-html-system-prompt.txt"

    # ==================== 多文件代码生成模型配置 ====================
    # 大模型API密钥（默认复用HTML生成密钥）
    multi_file_codegen_api_key: SecretStr | None = None
    # 大模型API基础URL
    multi_file_codegen_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    # 大模型名称
    multi_file_codegen_model_name: str = "qwen3.5-flash"
    # 生成温度参数
    multi_file_codegen_temperature: float = 0.7

    # ==================== 多文件代码生成输出配置 ====================
    # 生成的多文件项目保存目录
    multi_file_output_dir: str = "./output/multi_file"
    # 系统提示词文件路径
    multi_file_system_prompt_path: str = "app/prompts/codegen-multi-file-system-prompt.txt"

    # ==================== Kafka 配置 ====================
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_topic: str = "agent-generation-tasks"
    kafka_consumer_group: str = "agent-code-platform-worker"

    # ==================== Redis 配置 ====================
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: str = ""
    redis_db: int = 0
    redis_task_history_ttl: int = 3600  # 任务历史过期时间（秒），默认1小时

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
