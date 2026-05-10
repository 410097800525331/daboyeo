from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any
from urllib.error import URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

from .storage import (
    R2Config,
    build_poster_object_key,
    load_r2_config,
    public_object_url,
    put_bytes,
)


DEFAULT_POSTER_TIMEOUT_SECONDS = 15
POSTER_USER_AGENT = "daboyeo-poster-storage/1.0"


@dataclass(frozen=True)
class PosterStorageResult:
    poster_url: str
    poster_source_url: str
    poster_r2_key: str
    poster_etag: str
    poster_storage_status: str
    poster_stored_at: str | None = None
    error: str = ""

    def movie_fields(self) -> dict[str, Any]:
        return {
            "poster_url": self.poster_url or None,
            "poster_source_url": self.poster_source_url or None,
            "poster_r2_key": self.poster_r2_key or None,
            "poster_etag": self.poster_etag or None,
            "poster_storage_status": self.poster_storage_status,
            "poster_stored_at": self.poster_stored_at,
        }


def mirror_poster_url(
    provider: str,
    external_movie_id: str,
    source_url: str,
    config: R2Config | None = None,
    timeout_seconds: int = DEFAULT_POSTER_TIMEOUT_SECONDS,
) -> PosterStorageResult:
    source = (source_url or "").strip()
    if not source:
        return PosterStorageResult("", "", "", "", "missing")

    effective = config or load_r2_config()
    if is_public_r2_url(source, effective):
        return PosterStorageResult(
            source,
            source,
            object_key_from_public_url(source, effective),
            "",
            "r2_existing",
            None,
        )
    if not effective.configured:
        return PosterStorageResult(source, source, "", "", "r2_unconfigured")
    if not (external_movie_id or "").strip():
        return PosterStorageResult(source, source, "", "", "r2_failed", None, "missing external movie id")

    try:
        downloaded = download_poster(source, timeout_seconds)
        object_key = build_poster_object_key(
            provider,
            external_movie_id,
            downloaded["content_type"],
            downloaded["filename_hint"],
        )
        stored = put_bytes(object_key, downloaded["body"], downloaded["content_type"], effective)
        public_url = stored.get("public_url") or public_object_url(object_key, effective)
        return PosterStorageResult(
            public_url or source,
            source,
            object_key,
            str(stored.get("etag") or ""),
            "r2_stored" if public_url else "r2_stored_private",
            datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S"),
        )
    except Exception as exc:
        return PosterStorageResult(source, source, "", "", "r2_failed", None, limited_error(exc))


def download_poster(url: str, timeout_seconds: int = DEFAULT_POSTER_TIMEOUT_SECONDS) -> dict[str, Any]:
    request = Request(url, headers={"User-Agent": POSTER_USER_AGENT})
    with urlopen(request, timeout=max(1, timeout_seconds)) as response:
        body = response.read()
        content_type = response.headers.get_content_type() or content_type_from_path(url)
    if not body:
        raise URLError("poster body is empty")
    if not content_type or content_type == "application/octet-stream":
        content_type = content_type_from_path(url)
    return {
        "body": body,
        "content_type": content_type,
        "filename_hint": urlparse(url).path,
    }


def content_type_from_path(value: str) -> str:
    path = urlparse(value).path.lower()
    if path.endswith((".jpg", ".jpeg")):
        return "image/jpeg"
    if path.endswith(".png"):
        return "image/png"
    if path.endswith(".webp"):
        return "image/webp"
    if path.endswith(".gif"):
        return "image/gif"
    if path.endswith(".avif"):
        return "image/avif"
    return "image/jpeg"


def is_public_r2_url(value: str, config: R2Config) -> bool:
    base = (config.public_base_url or "").strip().rstrip("/")
    return bool(base and value.strip().startswith(base + "/"))


def object_key_from_public_url(value: str, config: R2Config) -> str:
    base = (config.public_base_url or "").strip().rstrip("/")
    return value.strip()[len(base) + 1 :] if base and value.strip().startswith(base + "/") else ""


def limited_error(exc: Exception, limit: int = 180) -> str:
    message = str(exc).replace("\n", " ").strip()
    return message[:limit]
