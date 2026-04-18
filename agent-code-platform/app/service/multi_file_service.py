import logging
import re
import uuid
from collections.abc import AsyncIterator
from datetime import datetime
from pathlib import Path

import aiofiles
from fastapi import HTTPException
from langchain_core.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI

from app.config.settings import get_settings
from app.model.response.stream_response import FileMeta

logger = logging.getLogger(__name__)

# 从 LLM 返回内容中提取 html/css/js 代码块
_CODE_BLOCK_PATTERN = re.compile(
    r"```(html|css|js|javascript)\s*\n(.*?)\n\s*```", re.DOTALL
)


class MultiFileGenService:
    """多文件代码生成服务：流式生成后提取 HTML/CSS/JS 三个文件并保存到独立目录"""

    def __init__(self) -> None:
        settings = get_settings()

        # API密钥：优先使用专用密钥，否则复用HTML生成密钥
        api_key = (
            settings.multi_file_codegen_api_key.get_secret_value()
            if settings.multi_file_codegen_api_key
            else settings.html_codegen_api_key.get_secret_value()
        )

        self._llm = ChatOpenAI(
            api_key=api_key,
            base_url=settings.multi_file_codegen_base_url,
            model=settings.multi_file_codegen_model_name,
            temperature=settings.multi_file_codegen_temperature,
        )
        self._system_prompt_path = Path(settings.multi_file_system_prompt_path)
        self._output_dir = Path(settings.multi_file_output_dir)
        self._output_dir.mkdir(parents=True, exist_ok=True)

    @staticmethod
    def extract_files(raw_content: str) -> dict[str, str]:
        """
        从 LLM 返回的原始文本中提取 html/css/js 代码块。

        Returns:
            字典，key 为文件类型（html/css/js），value 为对应源码。
            js 和 javascript 均映射为 js。
        """
        files: dict[str, str] = {}
        for match in _CODE_BLOCK_PATTERN.finditer(raw_content):
            lang = match.group(1).lower()
            code = match.group(2).strip()
            if lang == "javascript":
                lang = "js"
            files[lang] = code
        return files

    @staticmethod
    def generate_dir_name() -> str:
        """生成唯一的目录名：时间戳 + UUID短码"""
        timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
        short_uuid = uuid.uuid4().hex[:8]
        return f"{timestamp}_{short_uuid}"

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
        except IOError:
            logger.exception("读取系统提示词文件失败: %s", self._system_prompt_path)
            raise HTTPException(
                status_code=500,
                detail=f"读取系统提示词文件失败: {self._system_prompt_path}",
            )

    async def generate_stream(self, prompt: str) -> AsyncIterator[str]:
        """
        流式生成多文件代码，逐步 yield LLM 输出的文本 Chunk。

        Args:
            prompt: 用户的网页需求自然语言描述

        Yields:
            大模型输出的原始文本片段
        """
        system_prompt = await self._read_system_prompt()
        prompt_template = ChatPromptTemplate.from_messages(
            [
                ("system", system_prompt),
                ("human", "{user_prompt}"),
            ]
        )
        chain = prompt_template | self._llm
        try:
            async for chunk in chain.astream({"user_prompt": prompt}):
                if chunk.content:
                    yield chunk.content
        except Exception:
            logger.exception("流式调用大模型失败")
            raise HTTPException(
                status_code=502,
                detail="流式调用大模型失败，请稍后重试",
            )

    async def save_files(
        self, dir_name: str, files: dict[str, str]
    ) -> list[FileMeta]:
        """
        将提取出的代码保存到独立目录，返回文件元信息列表。

        Args:
            dir_name: 目录名（由 generate_dir_name 生成）
            files: 提取出的代码字典，key 为 html/css/js

        Returns:
            FileMeta 列表
        """
        project_dir = self._output_dir / dir_name
        project_dir.mkdir(parents=True, exist_ok=True)

        filename_map = {"html": "index.html", "css": "style.css", "js": "script.js"}
        result: list[FileMeta] = []

        for file_type, content in files.items():
            filename = filename_map.get(file_type, f"main.{file_type}")
            file_path = project_dir / filename
            try:
                async with aiofiles.open(
                    file_path, mode="w", encoding="utf-8"
                ) as f:
                    await f.write(content)
                result.append(
                    FileMeta(
                        filename=filename,
                        file_path=str(file_path.resolve()),
                        url_path=f"/static/multi_file/{dir_name}/{filename}",
                    )
                )
                logger.info("文件已保存: %s", file_path)
            except (PermissionError, IOError):
                logger.exception("保存文件失败: %s", file_path)
                raise HTTPException(
                    status_code=500,
                    detail=f"保存文件失败: {file_path}",
                )

        return result
