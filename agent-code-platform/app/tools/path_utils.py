import re
from pathlib import Path

_SAFE_SEGMENT_PATTERN = re.compile(r"^[A-Za-z0-9_-]+$")


def normalize_preview_path(preview_path: str | None) -> str:
    """Return a safe relative preview path such as 2026/04/23."""
    raw = (preview_path or "default").strip().replace("\\", "/").strip("/")
    if not raw:
        return "default"
    parts = raw.split("/")
    for part in parts:
        if part in {"", ".", ".."} or not _SAFE_SEGMENT_PATTERN.fullmatch(part):
            raise ValueError(f"非法previewPath: {preview_path}")
    return "/".join(parts)


def ensure_relative_to(path: Path, root: Path) -> None:
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise ValueError(f"路径越界: {path}") from exc


def build_code_output_dir(
    root_dir: str,
    preview_path: str | None,
    project_dir_name: str,
) -> tuple[Path, str]:
    """Build a safe output directory under CODE_OUTPUT_ROOT_DIR."""
    safe_preview = normalize_preview_path(preview_path)
    root = Path(root_dir).resolve()
    output_dir = (root / safe_preview / project_dir_name).resolve()
    ensure_relative_to(output_dir, root)
    output_dir.mkdir(parents=True, exist_ok=True)
    return output_dir, safe_preview


def build_code_output_url(
    safe_preview: str,
    project_dir_name: str,
    rel_path: str,
) -> str:
    safe_rel = rel_path.replace("\\", "/").lstrip("/")
    return f"/static/code_output/{safe_preview}/{project_dir_name}/{safe_rel}"
