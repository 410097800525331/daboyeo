from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from collectors.common.poster_storage import content_type_from_path
from collectors.common.storage import load_r2_config, put_bytes


MANIFESTS = {
    "movie": ROOT / "backend" / "src" / "main" / "resources" / "recommendation" / "korea-boxoffice-top50-posters.json",
    "anime": ROOT / "backend" / "src" / "main" / "resources" / "recommendation" / "korea-animation-boxoffice-top30-posters.json",
}
FRONTEND_ROOT = ROOT / "frontend"
STATIC_ROOT = ROOT / "backend" / "src" / "main" / "resources" / "static"
POSTER_PATH_PREFIX = "src/assets/R2/"


def load_manifest(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def object_key_from_poster_path(poster_path: str) -> str:
    normalized = poster_path.strip().lstrip("/")
    if normalized.startswith(POSTER_PATH_PREFIX):
        return normalized[len(POSTER_PATH_PREFIX) :]
    if normalized.startswith("assets/R2/"):
        return normalized[len("assets/R2/") :]
    return "posters/seed/" + Path(normalized).name


def local_file_for_poster_path(poster_path: str) -> Path:
    normalized = poster_path.strip().lstrip("/")
    candidates = [
        FRONTEND_ROOT / normalized,
        STATIC_ROOT / normalized,
    ]
    for candidate in candidates:
        if candidate.exists():
            return candidate
    return candidates[0]


def iter_seed_posters(pools: list[str]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for pool in pools:
        manifest = load_manifest(MANIFESTS[pool])
        for movie in manifest.get("movies", []):
            poster_path = str(movie.get("posterPath") or "").strip()
            if not poster_path:
                continue
            local_file = local_file_for_poster_path(poster_path)
            rows.append(
                {
                    "pool": pool,
                    "movieCd": movie.get("movieCd"),
                    "titleKo": movie.get("titleKo"),
                    "posterPath": poster_path,
                    "localFile": local_file,
                    "objectKey": object_key_from_poster_path(poster_path),
                }
            )
    return rows


def upload_rows(rows: list[dict[str, Any]], write: bool) -> dict[str, Any]:
    config = load_r2_config()
    summary: dict[str, Any] = {
        "mode": "write" if write else "dry-run",
        "configured": config.configured,
        "checked": len(rows),
        "missing_files": [],
        "uploaded": 0,
        "planned": 0,
        "failed": [],
    }
    if write and not config.configured:
        raise RuntimeError("R2 config is missing. Check R2_ACCOUNT_ID/R2_ACCESS_KEY_ID/R2_SECRET_ACCESS_KEY/R2_BUCKET_NAME.")

    for row in rows:
        local_file: Path = row["localFile"]
        object_key = row["objectKey"]
        if not local_file.exists():
            summary["missing_files"].append({"pool": row["pool"], "movieCd": row["movieCd"], "objectKey": object_key})
            continue
        if not write:
            summary["planned"] += 1
            continue
        try:
            put_bytes(
                object_key,
                local_file.read_bytes(),
                content_type_from_path(str(local_file)),
                config,
            )
            summary["uploaded"] += 1
        except Exception as exc:
            summary["failed"].append({
                "pool": row["pool"],
                "movieCd": row["movieCd"],
                "objectKey": object_key,
                "error": str(exc)[:180],
            })
    return summary


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Upload fixed recommendation poster seed assets to Cloudflare R2.")
    parser.add_argument("--pool", choices=["movie", "anime", "all"], default="all")
    parser.add_argument("--write", action="store_true", help="Actually upload objects. Default is dry-run.")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    pools = ["movie", "anime"] if args.pool == "all" else [args.pool]
    rows = iter_seed_posters(pools)
    print(json.dumps(upload_rows(rows, args.write), ensure_ascii=False, indent=2, default=str))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
