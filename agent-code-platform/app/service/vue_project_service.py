"""Vue 工程项目生成服务：基于 LangGraph Agent + Tool Calling 流式生成完整 Vue3 工程。"""

import json
import logging
import re
from collections.abc import AsyncIterator
from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path

import aiofiles
from langchain_core.messages import AIMessageChunk, HumanMessage, ToolMessage
from langchain_openai import ChatOpenAI
from langgraph.prebuilt import create_react_agent

from app.config.settings import get_settings
from app.model.response.stream_response import FileMeta
from app.tools.file_tools import create_file_tools
from app.tools.path_utils import build_code_output_dir, build_code_output_url

logger = logging.getLogger(__name__)


class AgentEventType(StrEnum):
    TEXT = "text"
    TOOL_START = "tool_start"
    TOOL_END = "tool_end"


@dataclass
class AgentEvent:
    type: AgentEventType
    content: str | None = None
    tool_name: str | None = None
    tool_input: dict | None = None
    tool_output: str | None = None


def _extract_path(args_str: str) -> str | None:
    """从工具调用的 JSON 参数片段中提取 path 字段值（支持部分 JSON）。"""
    match = re.search(r'"path"\s*:\s*"([^"]*)"', args_str)
    return match.group(1) if match else None


class VueProjectGenService:
    """Vue 工程项目生成服务：通过 LangGraph Agent 调用文件工具创建完整工程目录。"""

    def __init__(self) -> None:
        settings = get_settings()

        api_key = (
            settings.vue_project_codegen_api_key.get_secret_value()
            if settings.vue_project_codegen_api_key
            else settings.html_codegen_api_key.get_secret_value()
        )

        self._llm = ChatOpenAI(
            api_key=api_key,
            base_url=settings.vue_project_codegen_base_url,
            model=settings.vue_project_codegen_model_name,
            temperature=settings.vue_project_codegen_temperature,
        )
        self._system_prompt_path = Path(settings.vue_project_system_prompt_path)
        self._output_dir = Path(settings.vue_project_output_dir)
        self._output_dir.mkdir(parents=True, exist_ok=True)

    @staticmethod
    def build_project_path(app_id: int, preview: str) -> Path:
        """
        构建 Vue 项目输出路径：{CODE_OUTPUT_ROOT_DIR}/{previewPath}/vue_project_{appId}/

        Args:
            app_id: 应用ID

        Returns:
            项目目录的 Path 对象
        """
        settings = get_settings()
        project_dir, _ = build_code_output_dir(
            settings.code_output_root_dir,
            preview,
            f"vue_project_{app_id}",
        )
        return project_dir

    async def _read_system_prompt(self) -> str:
        try:
            async with aiofiles.open(
                    self._system_prompt_path, mode="r", encoding="utf-8"
            ) as f:
                return await f.read()
        except FileNotFoundError:
            logger.exception("系统提示词文件未找到: %s", self._system_prompt_path)
            raise
        except IOError:
            logger.exception("读取系统提示词文件失败: %s", self._system_prompt_path)
            raise

    async def generate_stream(
            self, prompt: str, project_dir: Path
    ) -> AsyncIterator[AgentEvent]:
        """
        运行 Vue 项目 Agent，通过 astream + stream_mode="messages" 流式输出事件。

        使用 stream_mode="messages" 而非 astream_events，确保 ToolMessage 事件能被正确接收。

        Args:
            prompt: 用户的自然语言需求描述
            project_dir: 项目文件写入的绝对路径

        Yields:
            AgentEvent: 包含文本内容、工具调用开始/结束的事件
        """
        system_prompt = await self._read_system_prompt()
        tools = create_file_tools(project_dir)

        agent = create_react_agent(
            model=self._llm,
            tools=tools,
            prompt=system_prompt,
        )

        inputs = {"messages": [HumanMessage(content=prompt)]}

        # 已发出 TOOL_START 的工具调用 ID 集合
        announced_tool_ids: set[str] = set()
        # tc_id -> 累积的 args JSON 片段
        tool_call_args: dict[str, str] = {}
        # tc_id -> 工具名称
        tool_call_names: dict[str, str] = {}
        # 并行工具调用时，index -> tc_id 的映射（首 chunk 带 id，后续 chunk 只有 index）
        index_to_id: dict[int, str] = {}

        async for msg, metadata in agent.astream(inputs, stream_mode="messages"):
            if isinstance(msg, AIMessageChunk):
                # LLM 正在输出文本内容
                if msg.content:
                    yield AgentEvent(type=AgentEventType.TEXT, content=msg.content)
                # LLM 正在生成工具调用（content 为空，tool_call_chunks 有数据）
                elif hasattr(msg, "tool_call_chunks") and msg.tool_call_chunks:
                    for tc_chunk in msg.tool_call_chunks:
                        tc_id = tc_chunk.get("id", "") or ""
                        tc_name = tc_chunk.get("name", "") or ""
                        tc_index = tc_chunk.get("index")

                        # 首个 chunk 带 id，建立 index -> id 映射
                        if tc_id and tc_index is not None:
                            index_to_id[tc_index] = tc_id

                        # 后续 chunk 无 id，通过 index 反查
                        if not tc_id and tc_index is not None and tc_index in index_to_id:
                            tc_id = index_to_id[tc_index]

                        # 累积 args 片段
                        if tc_id:
                            args_fragment = tc_chunk.get("args", "") or ""
                            tool_call_args[tc_id] = tool_call_args.get(tc_id, "") + args_fragment
                            if tc_name:
                                tool_call_names[tc_id] = tc_name

                    # 处理完本批 chunks 后，检查是否有新的工具调用可以提取 path 并推送
                    for tid, tname in list(tool_call_names.items()):
                        if tid in announced_tool_ids:
                            continue
                        path = _extract_path(tool_call_args.get(tid, ""))
                        if path:
                            announced_tool_ids.add(tid)
                            logger.info("检测到工具调用: name=%s, id=%s, path=%s", tname, tid, path)
                            yield AgentEvent(
                                type=AgentEventType.TOOL_START,
                                tool_name=tname,
                                tool_input={"path": path},
                            )

            elif isinstance(msg, ToolMessage):
                tc_id = msg.tool_call_id

                # 解析累积的完整 args JSON，提取 path 和 content
                file_content = None
                raw_args = tool_call_args.get(tc_id, "") if tc_id else ""
                path = _extract_path(raw_args) if raw_args else None
                if raw_args:
                    try:
                        parsed = json.loads(raw_args)
                        if not path:
                            path = parsed.get("path")
                        if msg.name == "write_file":
                            file_content = parsed.get("content")
                    except json.JSONDecodeError:
                        logger.warning(
                            "工具调用 args JSON 解析失败: tc_id=%s, args_len=%d, args_head=%.100s",
                            tc_id, len(raw_args), raw_args,
                        )

                # 兜底：如果 TOOL_START 还没发出（path 之前未提取到），现在补发
                if tc_id and tc_id not in announced_tool_ids:
                    announced_tool_ids.add(tc_id)
                    logger.info("补发工具调用: name=%s, path=%s", msg.name, path)
                    yield AgentEvent(
                        type=AgentEventType.TOOL_START,
                        tool_name=tool_call_names.get(tc_id, msg.name or "unknown"),
                        tool_input={"path": path} if path else None,
                    )

                logger.info("工具执行完成: name=%s, path=%s, has_content=%s", msg.name, path, file_content is not None)
                tool_input = {}
                if path:
                    tool_input["path"] = path
                if file_content is not None:
                    tool_input["content"] = file_content
                yield AgentEvent(
                    type=AgentEventType.TOOL_END,
                    tool_name=msg.name or "unknown",
                    tool_output=str(msg.content)[:500],
                    tool_input=tool_input if tool_input else None,
                )

    @staticmethod
    def scan_project_files(
        project_dir: Path,
        app_id: int,
        preview: str,
    ) -> list[FileMeta]:
        """扫描项目目录，返回所有文件的元信息列表。

        Args:
            project_dir: 项目目录的 Path 对象
            app_id: 应用ID，用于构建URL路径

        Returns:
            FileMeta 列表
        """
        result: list[FileMeta] = []
        _, safe_preview = build_code_output_dir(
            get_settings().code_output_root_dir,
            preview,
            f"vue_project_{app_id}",
        )
        for file_path in sorted(project_dir.rglob("*")):
            if file_path.is_file():
                rel_path = file_path.relative_to(project_dir)
                url_path = build_code_output_url(
                    safe_preview,
                    f"vue_project_{app_id}",
                    str(rel_path),
                )
                result.append(
                    FileMeta(
                        filename=str(rel_path),
                        file_path=str(file_path.resolve()),
                        url_path=url_path,
                    )
                )
        return result
