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
from app.model.response.html_response import HtmlGenResponse

logger = logging.getLogger(__name__)

# 用于从 LLM 返回内容中提取纯 HTML 的正则表达式
# 匹配三种情况：```html...```、```...```（无语言标记）、以及无代码块的纯HTML
_HTML_CODE_BLOCK_PATTERN = re.compile(
    r"```(?:html)?\s*\n(.*?)\n\s*```", re.DOTALL
)


class HtmlGenService:
    """HTML代码生成服务：负责编排大模型调用、HTML提取与本地持久化"""

    def __init__(self) -> None:
        """初始化服务，从配置中心读取模型参数并构建LLM实例"""
        settings = get_settings()

        # 构建 LangChain ChatOpenAI 实例，全部参数均来自配置中心
        self._llm = ChatOpenAI(
            api_key=settings.html_codegen_api_key.get_secret_value(),
            base_url=settings.html_codegen_base_url,
            model=settings.html_codegen_model_name,
            temperature=settings.html_codegen_temperature,
        )

        # 缓存配置项，避免在每次调用时重复读取
        self._system_prompt_path = Path(settings.html_system_prompt_path)
        self._output_dir = Path(settings.html_output_dir)

        # 确保输出目录存在（同步创建，仅在服务初始化时执行一次）
        self._output_dir.mkdir(parents=True, exist_ok=True)

    @staticmethod
    def _extract_html(raw_content: str) -> str:
        """
        从大模型返回的原始文本中提取纯HTML源码。

        大模型通常会将代码包裹在 Markdown 代码块中返回，
        例如：```html\\n<html>...</html>\\n```
        本方法使用正则匹配提取，若无代码块标记则视为纯HTML直接返回。

        Args:
            raw_content: 大模型返回的原始文本内容

        Returns:
            提取出的纯净HTML源码字符串
        """
        match = _HTML_CODE_BLOCK_PATTERN.search(raw_content)
        if match:
            return match.group(1).strip()
        # 无代码块包裹，假定整个返回内容即为HTML
        return raw_content.strip()

    @staticmethod
    def _generate_filename() -> str:
        """
        生成唯一的HTML文件名。

        采用「时间戳 + UUID短码」的组合策略：
        - 时间戳保证按时间有序排列
        - UUID短码保证并发场景下的唯一性

        Returns:
            格式如 "20260418143025_a1b2c3d4.html" 的文件名字符串
        """
        timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
        short_uuid = uuid.uuid4().hex[:8]
        return f"{timestamp}_{short_uuid}.html"

    async def _read_system_prompt(self) -> str:
        """
        异步读取外部系统提示词文件内容。

        通过 aiofiles 异步读取提示词文件，避免阻塞事件循环。
        若文件不存在或读取失败，抛出 500 HTTP 异常。

        Returns:
            系统提示词文本内容
        """
        try:
            async with aiofiles.open(self._system_prompt_path, mode="r", encoding="utf-8") as f:
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

    async def _invoke_llm(self, system_prompt: str, user_prompt: str) -> str:
        """
        异步调用大语言模型生成HTML内容。

        使用 LangChain 的 ChatPromptTemplate 构建 System + Human 消息对，
        然后通过 ainvoke 异步调用模型。

        Args:
            system_prompt: 系统角色提示词
            user_prompt: 用户的网页需求描述

        Returns:
            大模型返回的原始文本内容
        """
        chain = self._build_chain(system_prompt)
        try:
            response = await chain.ainvoke({"user_prompt": user_prompt})
            return response.content
        except Exception:
            logger.exception("调用大模型生成HTML失败")
            raise HTTPException(
                status_code=502,
                detail="调用大模型失败，请稍后重试",
            )

    def _build_chain(self, system_prompt: str):
        """构建LangChain链：System角色设定 + Human用户需求 → LLM推理"""
        prompt_template = ChatPromptTemplate.from_messages(
            [
                ("system", system_prompt),
                ("human", "{user_prompt}"),
            ]
        )
        return prompt_template | self._llm

    def _build_output_path(self, app_id: int,preview:str) -> Path:
        """
        构建输出路径：{CODE_OUTPUT_ROOT_DIR}/{previewPath}/html_{appId}/

        Args:
            app_id: 应用ID

        Returns:
            输出目录的 Path 对象
        """
        settings = get_settings()
        output_dir = Path(settings.code_output_root_dir) / preview / f"html_{app_id}"
        output_dir.mkdir(parents=True, exist_ok=True)
        return output_dir

    async def _save_html_file(self, html_content: str, filename: str, app_id: int,preview_path:str) -> Path:
        """
        将纯HTML内容异步写入本地文件。

        使用 aiofiles 异步写入，避免阻塞事件循环。

        Args:
            html_content: 纯净的HTML源码
            filename: 目标文件名
            app_id: 应用ID，用于构建输出路径

        Returns:
            写入文件的完整 Path 对象
        """
        output_dir = self._build_output_path(app_id,preview_path)
        file_path = output_dir/filename
        try:
            async with aiofiles.open(file_path, mode="w", encoding="utf-8") as f:
                await f.write(html_content)
            logger.info("HTML文件已保存: %s", file_path)
            return file_path
        except PermissionError:
            logger.exception("无写入权限，保存HTML文件失败: %s", file_path)
            raise HTTPException(
                status_code=500,
                detail=f"无写入权限，无法保存文件到: {file_path}",
            )
        except IOError:
            logger.exception("保存HTML文件IO异常: %s", file_path)
            raise HTTPException(
                status_code=500,
                detail=f"保存HTML文件失败: {file_path}",
            )

    async def generate(self, prompt: str, app_id: int = 0) -> HtmlGenResponse:
        """
        HTML生成的完整业务编排方法。

        执行流程：
        1. 异步读取外部系统提示词
        2. 异步调用大模型生成HTML
        3. 后处理：从LLM返回内容中提取纯HTML源码
        4. 生成唯一文件名，异步写入本地文件
        5. 构建并返回响应结果

        Args:
            prompt: 用户的网页需求自然语言描述
            app_id: 应用ID，用于构建输出路径，默认为0

        Returns:
            包含文件名、路径与执行状态的响应对象
        """
        # Step 1: 异步读取系统提示词
        system_prompt = await self._read_system_prompt()

        # Step 2: 异步调用大模型
        raw_content = await self._invoke_llm(system_prompt, prompt)

        # Step 3: 后处理清洗 — 提取纯HTML源码
        html_content = self._extract_html(raw_content)

        # Step 4: 生成唯一文件名并异步保存到本地
        filename = self._generate_filename()
        saved_path = await self._save_html_file(html_content, filename, app_id)

        # Step 5: 构建响应（绝对路径 + URL访问路径）
        # 新路径格式: /static/output/{preview_path}/html_{appId}/{filename}
        settings = get_settings()
        preview = settings.preview_path.lstrip("/")
        url_path = f"/static/output/{preview}/html_{app_id}/{filename}"
        return HtmlGenResponse(
            filename=filename,
            file_path=str(saved_path.resolve()),
            url_path=url_path,
            status="success",
        )

    async def generate_stream(self, prompt: str) -> AsyncIterator[str]:
        """
        流式生成HTML代码，逐步yield大模型输出的文本Chunk。

        与generate()不同，此方法不处理文件保存，由调用方（Kafka Worker）
        负责累积Chunk、提取HTML和持久化文件。

        Args:
            prompt: 用户的网页需求自然语言描述

        Yields:
            大模型输出的原始文本片段
        """
        system_prompt = await self._read_system_prompt()
        chain = self._build_chain(system_prompt)
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
