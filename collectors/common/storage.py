from __future__ import annotations

import gzip
import os
import re
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.parse import quote

from .normalize import json_for_db, utc_timestamp_compact
from .tidb import DEFAULT_ENV_PATH, parse_env_file


@dataclass(frozen=True)
class R2Config:
    account_id: str
    access_key_id: str
    secret_access_key: str
    bucket_name: str
    endpoint_url: str
    public_base_url: str = ""

    @property
    def configured(self) -> bool:
        return bool(
            self.account_id
            and self.access_key_id
            and self.secret_access_key
            and self.bucket_name
            and self.endpoint_url
        )


def _env_value(name: str, file_values: dict[str, str], default: str = "") -> str:
    return os.environ.get(name, file_values.get(name, default))


def load_r2_config(env_path: Path = DEFAULT_ENV_PATH) -> R2Config:
    file_values = parse_env_file(env_path)
    account_id = _env_value("R2_ACCOUNT_ID", file_values)
    endpoint_url = _env_value("R2_ENDPOINT_URL", file_values)
    if not endpoint_url and account_id:
        endpoint_url = f"https://{account_id}.r2.cloudflarestorage.com"

    return R2Config(
        account_id=account_id,
        access_key_id=_env_value("R2_ACCESS_KEY_ID", file_values),
        secret_access_key=_env_value("R2_SECRET_ACCESS_KEY", file_values),
        bucket_name=_env_value("R2_BUCKET_NAME", file_values),
        endpoint_url=endpoint_url,
        public_base_url=_env_value("R2_PUBLIC_BASE_URL", file_values),
    )


def gzip_json_bytes(payload: Any) -> bytes:
    return gzip.compress(json_for_db(payload).encode("utf-8"))


def build_raw_object_key(
    provider: str,
    resource: str,
    identifier: str,
    collected_at: datetime | None = None,
) -> str:
    current = collected_at or datetime.utcnow()
    provider_part = provider.lower().replace("_", "-")
    resource_part = resource.strip("/").lower()
    safe_identifier = str(identifier or "unknown").replace("\\", "-").replace("/", "-")
    return (
        f"raw/{provider_part}/{current:%Y/%m/%d}/"
        f"{resource_part}/{safe_identifier}/{utc_timestamp_compact(current)}.json.gz"
    )


def public_object_url(object_key: str, config: R2Config | None = None) -> str:
    effective = config or load_r2_config()
    if not effective.public_base_url:
        return ""
    return effective.public_base_url.rstrip("/") + "/" + quote(object_key.strip("/"), safe="/-_.~")


def safe_object_part(value: Any, fallback: str = "unknown") -> str:
    text = str(value or "").strip().lower()
    text = re.sub(r"[^a-z0-9._-]+", "-", text)
    text = text.strip(".-")
    return text or fallback


def extension_for_content_type(content_type: str, fallback: str = ".jpg") -> str:
    normalized = (content_type or "").split(";", 1)[0].strip().lower()
    return {
        "image/jpeg": ".jpg",
        "image/jpg": ".jpg",
        "image/png": ".png",
        "image/webp": ".webp",
        "image/gif": ".gif",
        "image/avif": ".avif",
    }.get(normalized, fallback)


def build_poster_object_key(
    provider: str,
    external_movie_id: str,
    content_type: str = "",
    filename_hint: str = "",
) -> str:
    extension = Path(filename_hint).suffix.lower()
    if extension not in {".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif"}:
        extension = extension_for_content_type(content_type)
    if extension == ".jpeg":
        extension = ".jpg"
    provider_part = safe_object_part(provider)
    movie_part = safe_object_part(external_movie_id)
    return f"posters/{provider_part}/{movie_part}{extension}"


def make_s3_client(config: R2Config | None = None):
    try:
        import boto3
    except ImportError as exc:
        raise RuntimeError("R2 사용에는 boto3가 필요함. python -m pip install --user boto3") from exc

    effective = config or load_r2_config()
    if not effective.configured:
        raise RuntimeError("R2 설정이 부족함. R2_ACCOUNT_ID/R2_ACCESS_KEY_ID/R2_SECRET_ACCESS_KEY/R2_BUCKET_NAME 확인 필요")

    return boto3.client(
        "s3",
        endpoint_url=effective.endpoint_url,
        aws_access_key_id=effective.access_key_id,
        aws_secret_access_key=effective.secret_access_key,
        region_name="auto",
    )


def put_json_gzip(object_key: str, payload: Any, config: R2Config | None = None) -> dict[str, Any]:
    effective = config or load_r2_config()
    client = make_s3_client(effective)
    body = gzip_json_bytes(payload)
    response = client.put_object(
        Bucket=effective.bucket_name,
        Key=object_key,
        Body=body,
        ContentType="application/json",
        ContentEncoding="gzip",
    )
    return {
        "object_key": object_key,
        "etag": str(response.get("ETag") or "").strip('"'),
        "size": len(body),
    }


def put_bytes(
    object_key: str,
    body: bytes,
    content_type: str = "application/octet-stream",
    config: R2Config | None = None,
    cache_control: str = "public, max-age=31536000, immutable",
) -> dict[str, Any]:
    effective = config or load_r2_config()
    client = make_s3_client(effective)
    put_args: dict[str, Any] = {
        "Bucket": effective.bucket_name,
        "Key": object_key,
        "Body": body,
        "ContentType": content_type or "application/octet-stream",
    }
    if cache_control:
        put_args["CacheControl"] = cache_control
    response = client.put_object(**put_args)
    return {
        "object_key": object_key,
        "etag": str(response.get("ETag") or "").strip('"'),
        "size": len(body),
        "public_url": public_object_url(object_key, effective),
    }


def list_object_keys(prefix: str = "", config: R2Config | None = None, limit: int = 20) -> list[str]:
    effective = config or load_r2_config()
    client = make_s3_client(effective)
    response = client.list_objects_v2(
        Bucket=effective.bucket_name,
        Prefix=prefix,
        MaxKeys=limit,
    )
    return [item["Key"] for item in response.get("Contents", [])]
