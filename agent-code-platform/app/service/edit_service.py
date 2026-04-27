"""Versioned visual edit service for generated applications."""

import asyncio
import json
import logging
import re
import shutil
from pathlib import Path
from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from pydantic import BaseModel, ConfigDict, Field

from app.config.settings import get_settings
from app.model.event.ai_task_event import AiTaskEvent
from app.service.version_manifest import (
    build_manifest_relative_path,
    build_preview_url,
    read_manifest,
    resolve_code_output_relative,
    write_element_manifest,
)
from app.tools.path_utils import ensure_relative_to

logger = logging.getLogger(__name__)


class ModifiedFile(BaseModel):
    path: str
    content: str


class EditModelResult(BaseModel):
    modified_files: list[ModifiedFile] = Field(
        default_factory=list,
        alias="modifiedFiles",
    )
    summary: str = "修改完成"

    model_config = ConfigDict(populate_by_name=True)


class EditApplyResult(BaseModel):
    summary: str
    source_path: str
    manifest_path: str
    preview_url: str
    modified_files: list[str]


class EditGenService:
    """Apply a user edit instruction to a copied version workspace."""

    def __init__(self) -> None:
        settings = get_settings()
        api_key = (
            settings.edit_codegen_api_key.get_secret_value()
            if settings.edit_codegen_api_key
            else (
                settings.vue_project_codegen_api_key.get_secret_value()
                if settings.vue_project_codegen_api_key
                else settings.html_codegen_api_key.get_secret_value()
            )
        )
        self._llm = ChatOpenAI(
            api_key=api_key,
            base_url=settings.edit_codegen_base_url,
            model=settings.edit_codegen_model_name,
            temperature=settings.edit_codegen_temperature,
        )

    async def apply_edit(self, event: AiTaskEvent) -> EditApplyResult:
        task = event.task
        if not task.base_source_path or not task.target_source_path:
            raise ValueError("EDIT任务缺少baseSourcePath或targetSourcePath")

        base_dir = resolve_code_output_relative(task.base_source_path)
        target_dir = resolve_code_output_relative(task.target_source_path)
        if not base_dir.exists() or not base_dir.is_dir():
            raise FileNotFoundError(f"基础版本目录不存在: {task.base_source_path}")

        if target_dir.exists():
            shutil.rmtree(target_dir)
        shutil.copytree(
            base_dir,
            target_dir,
            ignore=shutil.ignore_patterns("node_modules", "dist", ".git"),
        )

        manifest = read_manifest(target_dir)
        candidate_files = self._select_candidate_files(
            target_dir,
            manifest,
            event.payload.selected_elements,
            task.project_type,
        )
        prompt = self._build_prompt(event, candidate_files, target_dir, manifest)
        result = await self._invoke_model(prompt)
        modified_paths = await self._apply_modified_files(
            target_dir,
            result.modified_files,
        )

        await self._build_if_needed(target_dir)
        write_element_manifest(
            target_dir,
            source_path=task.target_source_path,
            version_id=task.target_version_id,
        )

        return EditApplyResult(
            summary=result.summary,
            source_path=task.target_source_path,
            manifest_path=build_manifest_relative_path(task.target_source_path),
            preview_url=build_preview_url(task.target_source_path, task.project_type),
            modified_files=modified_paths,
        )

    def _select_candidate_files(
        self,
        project_dir: Path,
        manifest: dict[str, Any],
        selected_elements: list[Any],
        project_type: str,
    ) -> list[Path]:
        node_ids = {
            item.get("nodeId")
            for item in selected_elements
            if isinstance(item, dict) and item.get("nodeId")
        }
        manifest_files = {
            item.get("file")
            for item in manifest.get("elements", [])
            if (
                isinstance(item, dict)
                and item.get("nodeId") in node_ids
                and item.get("file")
            )
        }
        files = [project_dir / file for file in manifest_files]
        files = [file.resolve() for file in files if file.exists() and file.is_file()]
        if files:
            return files

        text_hints = [
            str(item.get("text") or item.get("textContent") or "").strip()
            for item in selected_elements
            if isinstance(item, dict)
        ]
        text_hints = [hint for hint in text_hints if hint]
        matched = self._find_files_by_text(project_dir, text_hints)
        if matched:
            return matched

        if project_type.lower() == "html":
            return [project_dir / "index.html"]
        if project_type.lower() == "multi_file":
            return [
                project_dir / "index.html",
                project_dir / "style.css",
                project_dir / "script.js",
            ]
        return [
            path
            for path in [
                project_dir / "src" / "App.vue",
                project_dir / "src" / "main.js",
            ]
            if path.exists()
        ]

    def _find_files_by_text(
        self,
        project_dir: Path,
        text_hints: list[str],
    ) -> list[Path]:
        if not text_hints:
            return []
        result: list[Path] = []
        for file_path in project_dir.rglob("*"):
            if not file_path.is_file() or file_path.suffix.lower() not in {
                ".html",
                ".vue",
                ".js",
                ".ts",
                ".css",
            }:
                continue
            if any(
                part in {"node_modules", "dist", ".git"}
                for part in file_path.parts
            ):
                continue
            try:
                content = file_path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            if any(hint and hint in content for hint in text_hints):
                result.append(file_path)
        return result[:5]

    def _build_prompt(
        self,
        event: AiTaskEvent,
        candidate_files: list[Path],
        project_dir: Path,
        manifest: dict[str, Any],
    ) -> str:
        selected = json.dumps(
            event.payload.selected_elements,
            ensure_ascii=False,
            indent=2,
        )
        files_context = []
        for file_path in candidate_files:
            ensure_relative_to(file_path.resolve(), project_dir.resolve())
            rel = file_path.relative_to(project_dir).as_posix()
            content = file_path.read_text(encoding="utf-8")
            files_context.append(f"--- {rel} ---\n{content}")

        return f"""
你正在修改一个已经生成的网站项目。

硬性要求：
1. 只修改用户选中的元素或与其直接相关的样式和结构。
2. 不要重写整个页面，不要删除 data-ai-id。
3. 保持其他区域尽量不变。
4. 返回严格 JSON，不要包含 Markdown 代码块。
5. JSON 格式为：
{{"modifiedFiles":[{{"path":"相对路径","content":"完整文件内容"}}],"summary":"一句话总结"}}。

项目类型：{event.task.project_type}
作用范围：{event.payload.scope}
用户修改要求：{event.payload.prompt}

选中元素：
{selected}

manifest 摘要：
{json.dumps(manifest, ensure_ascii=False)[:4000]}

相关源码文件：
{chr(10).join(files_context)}
"""

    async def _invoke_model(self, prompt: str) -> EditModelResult:
        response = await self._llm.ainvoke(
            [
                SystemMessage(content="你是严格按 JSON 输出的前端代码修改助手。"),
                HumanMessage(content=prompt),
            ]
        )
        content = str(response.content)
        parsed = self._extract_json(content)
        return EditModelResult.model_validate(parsed)

    def _extract_json(self, content: str) -> dict[str, Any]:
        stripped = content.strip()
        if stripped.startswith("```"):
            stripped = re.sub(r"^```(?:json)?\s*", "", stripped)
            stripped = re.sub(r"\s*```$", "", stripped)
        try:
            return json.loads(stripped)
        except json.JSONDecodeError:
            match = re.search(r"\{.*\}", stripped, re.DOTALL)
            if not match:
                raise
            return json.loads(match.group(0))

    async def _apply_modified_files(
        self,
        project_dir: Path,
        modified_files: list[ModifiedFile],
    ) -> list[str]:
        if not modified_files:
            raise ValueError("模型未返回任何修改文件")
        root = project_dir.resolve()
        modified_paths: list[str] = []
        for item in modified_files:
            target = (root / item.path).resolve()
            ensure_relative_to(target, root)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(item.content, encoding="utf-8")
            modified_paths.append(target.relative_to(root).as_posix())
        return modified_paths

    async def _build_if_needed(self, project_dir: Path) -> None:
        package_json = project_dir / "package.json"
        if not package_json.exists():
            return
        settings = get_settings()
        install = await asyncio.create_subprocess_exec(
            "npm",
            "install",
            cwd=str(project_dir),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        await asyncio.wait_for(
            install.communicate(),
            timeout=settings.edit_build_timeout_sec,
        )
        if install.returncode != 0:
            raise RuntimeError("npm install 失败")

        build = await asyncio.create_subprocess_exec(
            "npm",
            "run",
            "build",
            cwd=str(project_dir),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await asyncio.wait_for(
            build.communicate(),
            timeout=settings.edit_build_timeout_sec,
        )
        if build.returncode != 0:
            detail = (stderr or stdout).decode("utf-8", errors="ignore")[-1000:]
            raise RuntimeError(f"项目构建失败: {detail}")
