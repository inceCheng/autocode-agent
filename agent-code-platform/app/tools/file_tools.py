"""LangChain 文件操作工具集：供 Agent 在项目目录内读写文件和目录。"""

import logging
import os
from pathlib import Path

import aiofiles
from langchain_core.tools import tool

logger = logging.getLogger(__name__)


def create_file_tools(project_dir: Path) -> list:
    """
    工厂函数：创建绑定到指定项目目录的文件操作工具列表。

    Args:
        project_dir: 项目根目录的绝对路径，所有工具的 path 参数均相对于此目录解析。

    Returns:
        绑定了 project_dir 的工具函数列表，可直接传给 LangChain Agent。
    """
    _root = project_dir.resolve()

    def _safe_resolve(path: str) -> Path:
        """解析相对路径并确保不越界到项目目录之外。"""
        resolved = (_root / path).resolve()
        if not str(resolved).startswith(str(_root)):
            raise ValueError(f"路径越界，禁止访问项目目录之外的文件: {path}")
        return resolved

    @tool
    async def read_file(path: str) -> str:
        """读取指定文件的内容。path 为相对于项目根目录的路径。"""
        logger.info("读取文件执行")
        try:
            file_path = _safe_resolve(path)
        except ValueError as e:
            return str(e)
        if not file_path.exists():
            return f"错误：文件不存在 - {path}"
        if file_path.is_dir():
            return f"错误：路径是目录而非文件 - {path}"
        try:
            async with aiofiles.open(file_path, mode="r", encoding="utf-8") as f:
                content = await f.read()
            logger.debug("读取文件: %s (%d 字符)", path, len(content))
            return content
        except Exception as e:
            return f"读取文件失败: {path}, 错误: {e}"

    @tool
    async def write_file(path: str, content: str) -> str:
        """将内容写入指定文件。若文件已存在则覆盖，若父目录不存在则自动创建。path 为相对于项目根目录的路径。"""
        logger.info("写入文件执行")
        try:
            file_path = _safe_resolve(path)
        except ValueError as e:
            return str(e)
        file_path.parent.mkdir(parents=True, exist_ok=True)
        try:
            async with aiofiles.open(file_path, mode="w", encoding="utf-8") as f:
                await f.write(content)
            logger.info("写入文件: %s (%d 字符)", path, len(content))
            return f"文件写入成功: {path}"
        except Exception as e:
            return f"写入文件失败: {path}, 错误: {e}"

    @tool
    async def modify_file(path: str, old_content: str, new_content: str) -> str:
        """修改指定文件的部分内容，将 old_content 替换为 new_content。仅替换第一个匹配项。path 为相对于项目根目录的路径。"""
        logger.info("修改文件执行")
        try:
            file_path = _safe_resolve(path)
        except ValueError as e:
            return str(e)
        if not file_path.exists():
            return f"错误：文件不存在 - {path}"
        try:
            async with aiofiles.open(file_path, mode="r", encoding="utf-8") as f:
                content = await f.read()
            if old_content not in content:
                return f"错误：未在文件中找到要替换的内容 - {path}"
            new_file_content = content.replace(old_content, new_content, 1)
            async with aiofiles.open(file_path, mode="w", encoding="utf-8") as f:
                await f.write(new_file_content)
            logger.info("修改文件: %s", path)
            return f"文件修改成功: {path}"
        except Exception as e:
            return f"修改文件失败: {path}, 错误: {e}"

    @tool
    async def delete_file(path: str) -> str:
        """删除指定的文件。path 为相对于项目根目录的路径。"""
        logger.info("删除文件执行")
        try:
            file_path = _safe_resolve(path)
        except ValueError as e:
            return str(e)
        if not file_path.exists():
            return f"错误：文件不存在 - {path}"
        if file_path.is_dir():
            return f"错误：路径是目录而非文件，请先清空目录内容 - {path}"
        try:
            os.remove(file_path)
            logger.info("删除文件: %s", path)
            return f"文件删除成功: {path}"
        except Exception as e:
            return f"删除文件失败: {path}, 错误: {e}"

    @tool
    async def list_directory(path: str = ".") -> str:
        """列出指定目录下的文件和子目录。path 为相对于项目根目录的路径，默认为项目根目录。"""
        logger.info("读取目录执行")
        try:
            dir_path = _safe_resolve(path)
        except ValueError as e:
            return str(e)
        if not dir_path.exists():
            return f"错误：目录不存在 - {path}"
        if not dir_path.is_dir():
            return f"错误：路径是文件而非目录 - {path}"
        try:
            entries = sorted(dir_path.iterdir())
            result_lines = []
            for entry in entries:
                rel = os.path.relpath(entry, _root)
                if entry.is_dir():
                    sub_count = len(list(entry.iterdir()))
                    result_lines.append(f"📁 {rel}/ ({sub_count} 项)")
                else:
                    size = entry.stat().st_size
                    result_lines.append(f"📄 {rel} ({size} 字节)")
            output = "\n".join(result_lines) if result_lines else "(空目录)"
            logger.debug("列出目录: %s, 共 %d 项", path, len(entries))
            return output
        except Exception as e:
            return f"列出目录失败: {path}, 错误: {e}"

    return [read_file, write_file, modify_file, delete_file, list_directory]
