import logging
from pathlib import Path

import aiofiles
from fastapi import HTTPException
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.config.settings import get_settings
from app.model.response.title_response import TitleResponse

logger = logging.getLogger(__name__)

FALLBACK_TITLE = "新对话"


class TitleGenService:
    """对话标题生成服务：根据用户提示词调用大模型生成简洁标题"""

    def __init__(self) -> None:
        settings = get_settings()

        api_key = (
            settings.title_gen_api_key.get_secret_value()
            if settings.title_gen_api_key
            else settings.html_codegen_api_key.get_secret_value()
        )
        base_url = settings.title_gen_base_url
        model_name = settings.title_gen_model_name
        temperature = settings.title_gen_temperature

        llm = ChatOpenAI(
            api_key=api_key,
            base_url=base_url,
            model=model_name,
            temperature=temperature,
            max_tokens=50,
            extra_body={"enable_thinking": False},
        )
        self._structured_llm = llm.with_structured_output(
            TitleResponse,
            method="function_calling",
        )
        self._system_prompt_path = Path(settings.title_gen_system_prompt_path)

    async def _read_system_prompt(self) -> str:
        try:
            async with aiofiles.open(
                self._system_prompt_path, mode="r", encoding="utf-8"
            ) as f:
                return await f.read()
        except FileNotFoundError:
            logger.exception("系统提示词文件未找到: %s", self._system_prompt_path)
            raise HTTPException(
                status_code=500,
                detail=f"系统提示词文件未找到: {self._system_prompt_path}",
            )
        except OSError:
            logger.exception("读取系统提示词文件失败: %s", self._system_prompt_path)
            raise HTTPException(
                status_code=500,
                detail=f"读取系统提示词文件失败: {self._system_prompt_path}",
            )

    async def generate_title(self, prompt: str) -> TitleResponse:
        system_prompt = await self._read_system_prompt()
        messages = [
            SystemMessage(content=system_prompt),
            HumanMessage(content=prompt),
        ]
        try:
            result = await self._structured_llm.ainvoke(messages)
            if result is None or not result.title:
                logger.warning("标题生成结果为空，回退返回默认标题")
                return TitleResponse(title=FALLBACK_TITLE)
            return result
        except Exception:
            logger.exception("标题生成调用大模型失败，回退返回默认标题")
            return TitleResponse(title=FALLBACK_TITLE)
