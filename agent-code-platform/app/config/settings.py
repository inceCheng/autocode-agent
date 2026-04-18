from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


''' 从环境变量.env 文件中读取配置，并做缓存复用 '''
class Settings(BaseSettings):
    app_name: str = "ai-agent-assistant-platform"
    app_debug: bool = False
    dashscope_api_key: SecretStr

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
