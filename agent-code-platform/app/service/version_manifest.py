"""Helpers for versioned generated projects and editable element manifests."""

import json
import re
from pathlib import Path
from typing import Any

from app.config.settings import get_settings
from app.tools.path_utils import ensure_relative_to, normalize_preview_path

_DATA_AI_ID_PATTERN = re.compile(r"""data-ai-id=["']([^"']+)["']""")
_SCAN_EXTENSIONS = {".html", ".vue", ".js", ".ts", ".css"}


def resolve_code_output_relative(relative_path: str) -> Path:
    """Resolve a version source path relative to CODE_OUTPUT_ROOT_DIR safely."""
    safe_rel = normalize_preview_path(relative_path)
    root = Path(get_settings().code_output_root_dir).resolve()
    target = (root / safe_rel).resolve()
    ensure_relative_to(target, root)
    return target


def build_preview_url(source_path: str, project_type: str) -> str:
    if project_type.lower() == "vue_project":
        return f"/static/{source_path}/dist/index.html#/"
    return f"/static/{source_path}/index.html"


def build_manifest_relative_path(source_path: str) -> str:
    return f"{source_path}/.ai/manifest.json"


def write_element_manifest(
    project_dir: Path,
    *,
    source_path: str,
    version_id: str | None = None,
) -> Path:
    """Scan editable nodes and write `.ai/manifest.json` into the project."""
    elements: list[dict[str, Any]] = []
    for file_path in sorted(project_dir.rglob("*")):
        if not file_path.is_file() or file_path.suffix.lower() not in _SCAN_EXTENSIONS:
            continue
        if any(part in {"node_modules", "dist", ".git"} for part in file_path.parts):
            continue
        try:
            content = file_path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        rel = file_path.relative_to(project_dir).as_posix()
        seen_in_file: set[str] = set()
        for match in _DATA_AI_ID_PATTERN.finditer(content):
            node_id = match.group(1)
            if node_id in seen_in_file:
                continue
            seen_in_file.add(node_id)
            elements.append(
                {
                    "nodeId": node_id,
                    "file": rel,
                    "description": "",
                }
            )

    manifest = {
        "versionId": version_id,
        "sourcePath": source_path,
        "elements": elements,
    }
    manifest_dir = project_dir / ".ai"
    manifest_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = manifest_dir / "manifest.json"
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return manifest_path


def read_manifest(project_dir: Path) -> dict[str, Any]:
    manifest_path = project_dir / ".ai" / "manifest.json"
    if not manifest_path.exists():
        return {"elements": []}
    try:
        return json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {"elements": []}
