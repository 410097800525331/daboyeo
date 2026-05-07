from __future__ import annotations

import argparse
import os
import json
import re
import sys
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path
from typing import Any
from urllib.parse import urlencode
from urllib.request import Request, urlopen

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from collectors.common.repository import upsert_dict
from collectors.common.tidb import connect_tidb, load_tidb_config, parse_env_file
from collectors.lotte import LotteCinemaCollector
from collectors.megabox import MegaboxCollector
from scripts.ingest.genre_tagging import (
    canonical_genres_from_provider_row,
    canonical_genres_from_values,
    normalize_title_for_match,
)


DEFAULT_OVERRIDES_PATH = Path(__file__).with_name("current_movie_tag_overrides.json")
TAG_VALUE_RE = re.compile(r"^[a-z0-9_]+$")
ALLOWED_TAG_TYPES = {"genre", "age_rating", "mood", "audience", "content", "pace", "format"}
SOURCE_PROVIDER_METADATA = "provider_metadata"
SOURCE_PROVIDER_DETAIL = "provider_detail"
SOURCE_KOBIS_METADATA = "kobis_metadata"
KOBIS_MOVIE_LIST_URL = "https://www.kobis.or.kr/kobisopenapi/webservice/rest/movie/searchMovieList.json"
KOBIS_USER_AGENT = "daboyeo-metadata-enrichment/1.0"
LOTTE = "LOTTE_CINEMA"
MEGABOX = "MEGABOX"


@dataclass(frozen=True)
class TagOverride:
    provider_code: str
    external_movie_id: str
    title_ko: str
    tags: tuple[tuple[str, str], ...]
    source: str
    confidence: Decimal


def safe_text(value: Any) -> str:
    return "" if value is None else str(value).strip()


def normalize_tag_type(value: Any) -> str:
    normalized = safe_text(value).lower()
    if normalized not in ALLOWED_TAG_TYPES:
        raise ValueError(f"unsupported tag type: {value}")
    return normalized


def normalize_tag_value(value: Any) -> str:
    normalized = safe_text(value).lower().replace("-", "_").replace(" ", "_")
    if not normalized or not TAG_VALUE_RE.match(normalized):
        raise ValueError(f"unsupported tag value: {value}")
    return normalized


def normalize_source(value: Any) -> str:
    source = safe_text(value) or "curated_metadata"
    if len(source) > 32:
        raise ValueError("source must fit movie_tags.source varchar(32)")
    return source


def normalize_confidence(value: Any) -> Decimal:
    confidence = Decimal(safe_text(value) or "0.9900")
    if confidence < Decimal("0") or confidence > Decimal("1"):
        raise ValueError("confidence must be between 0 and 1")
    return confidence.quantize(Decimal("0.0001"))


def tags_from_entry(entry: dict[str, Any]) -> tuple[tuple[str, str], ...]:
    raw_tags = entry.get("tags")
    if not isinstance(raw_tags, dict) or not raw_tags:
        raise ValueError("movie override requires non-empty tags object")

    seen: set[tuple[str, str]] = set()
    tags: list[tuple[str, str]] = []
    for raw_type, raw_values in raw_tags.items():
        tag_type = normalize_tag_type(raw_type)
        values = raw_values if isinstance(raw_values, list) else [raw_values]
        for value in values:
            tag = (tag_type, normalize_tag_value(value))
            if tag not in seen:
                seen.add(tag)
                tags.append(tag)
    return tuple(tags)


def load_overrides(path: Path = DEFAULT_OVERRIDES_PATH) -> list[TagOverride]:
    data = json.loads(path.read_text(encoding="utf-8"))
    source = normalize_source(data.get("source", {}).get("source"))
    confidence = normalize_confidence(data.get("source", {}).get("confidence"))
    raw_movies = data.get("movies")
    if not isinstance(raw_movies, list):
        raise ValueError("override file must contain a movies list")

    overrides: list[TagOverride] = []
    seen_keys: set[tuple[str, str]] = set()
    for entry in raw_movies:
        if not isinstance(entry, dict):
            raise ValueError("movie override entries must be objects")
        provider_code = safe_text(entry.get("providerCode")).upper()
        external_movie_id = safe_text(entry.get("externalMovieId"))
        title_ko = safe_text(entry.get("titleKo"))
        if not provider_code or not external_movie_id:
            raise ValueError("movie override requires providerCode and externalMovieId")
        key = (provider_code, external_movie_id)
        if key in seen_keys:
            raise ValueError(f"duplicate override key: {provider_code}:{external_movie_id}")
        seen_keys.add(key)
        overrides.append(
            TagOverride(
                provider_code=provider_code,
                external_movie_id=external_movie_id,
                title_ko=title_ko,
                tags=tags_from_entry(entry),
                source=normalize_source(entry.get("source", source)),
                confidence=normalize_confidence(entry.get("confidence", confidence)),
            )
        )
    return overrides


def current_movie_for_override(cursor: Any, override: TagOverride) -> dict[str, Any] | None:
    cursor.execute(
        """
        SELECT
          m.id,
          m.provider_code,
          m.external_movie_id,
          m.title_ko,
          m.title_en,
          COUNT(DISTINCT s.id) AS future_showtime_count,
          GROUP_CONCAT(DISTINCT CONCAT(mt.tag_type, ':', mt.tag_value) ORDER BY mt.tag_type, mt.tag_value SEPARATOR '\x1f') AS existing_tags
        FROM movies m
        LEFT JOIN showtimes s
          ON s.movie_id = m.id
         AND s.starts_at >= CURRENT_TIMESTAMP
        LEFT JOIN movie_tags mt
          ON mt.provider_code = m.provider_code
         AND mt.external_movie_id = m.external_movie_id
        WHERE m.provider_code = %s
          AND m.external_movie_id = %s
        GROUP BY m.id, m.provider_code, m.external_movie_id, m.title_ko, m.title_en
        LIMIT 1
        """,
        (override.provider_code, override.external_movie_id),
    )
    row = cursor.fetchone()
    if not row:
        return None
    movie_id, provider_code, external_movie_id, title_ko, title_en, future_showtime_count, existing_tags = row
    return {
        "movie_id": int(movie_id),
        "provider_code": provider_code,
        "external_movie_id": external_movie_id,
        "title_ko": title_ko,
        "title_en": title_en,
        "future_showtime_count": int(future_showtime_count or 0),
        "existing_tag_keys": {safe_text(value).lower() for value in split_concat_values(existing_tags)},
    }


def upsert_movie_tag(
    cursor: Any,
    movie: dict[str, Any],
    tag_type: str,
    tag_value: str,
    source: str,
    confidence: Decimal,
) -> None:
    upsert_dict(
        cursor,
        "movie_tags",
        {
            "movie_id": movie["movie_id"],
            "provider_code": movie["provider_code"],
            "external_movie_id": movie["external_movie_id"],
            "tag_type": tag_type,
            "tag_value": tag_value,
            "source": source,
            "confidence": confidence,
        },
    )


def parse_json_dict(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    if not value:
        return {}
    try:
        loaded = json.loads(value)
    except (TypeError, ValueError):
        return {}
    return loaded if isinstance(loaded, dict) else {}


def split_concat_values(value: Any) -> list[str]:
    text = safe_text(value)
    return [part for part in text.split("\x1f") if part]


def current_movies(cursor: Any, *, current_only: bool = True) -> list[dict[str, Any]]:
    future_join = "AND s.starts_at >= CURRENT_TIMESTAMP" if current_only else ""
    cursor.execute(
        f"""
        SELECT
          m.id,
          m.provider_code,
          m.external_movie_id,
          m.representative_movie_id,
          m.title_ko,
          m.title_en,
          m.release_date,
          m.raw_json,
          COUNT(DISTINCT s.id) AS future_showtime_count,
          GROUP_CONCAT(DISTINCT mt.tag_value ORDER BY mt.tag_value SEPARATOR '\x1f') AS genre_values
        FROM movies m
        LEFT JOIN showtimes s
          ON s.movie_id = m.id
         {future_join}
        LEFT JOIN movie_tags mt
          ON mt.provider_code = m.provider_code
         AND mt.external_movie_id = m.external_movie_id
         AND mt.tag_type = 'genre'
        GROUP BY m.id, m.provider_code, m.external_movie_id, m.representative_movie_id, m.title_ko, m.title_en, m.release_date, m.raw_json
        HAVING future_showtime_count > 0 OR %s = 0
        ORDER BY future_showtime_count DESC, m.id ASC
        """,
        (1 if current_only else 0,),
    )
    rows: list[dict[str, Any]] = []
    for row in cursor.fetchall():
        (
            movie_id,
            provider_code,
            external_movie_id,
            representative_movie_id,
            title_ko,
            title_en,
            release_date,
            raw_json,
            future_count,
            genre_values,
        ) = row
        existing = {safe_text(value).lower() for value in split_concat_values(genre_values)}
        rows.append(
            {
                "movie_id": int(movie_id),
                "provider_code": provider_code,
                "external_movie_id": external_movie_id,
                "representative_movie_id": representative_movie_id,
                "title_ko": title_ko,
                "title_en": title_en,
                "release_date": release_date,
                "raw": parse_json_dict(raw_json),
                "future_showtime_count": int(future_count or 0),
                "existing_genre_values": existing,
            }
        )
    return rows


def missing_genres(movie: dict[str, Any], genres: list[str]) -> list[str]:
    existing = movie.setdefault("existing_genre_values", set())
    missing: list[str] = []
    for genre in genres:
        normalized = normalize_tag_value(genre)
        if normalized not in existing:
            existing.add(normalized)
            missing.append(normalized)
    return missing


def provider_auto_genres(movie: dict[str, Any]) -> list[str]:
    genres = canonical_genres_from_provider_row({"raw": movie.get("raw") or {}})
    for genre in canonical_genres_from_values(movie.get("existing_genre_values") or []):
        if genre not in genres:
            genres.append(genre)
    return genres


def provider_detail_collectors() -> dict[str, Any]:
    return {
        LOTTE: LotteCinemaCollector(),
        MEGABOX: MegaboxCollector(),
    }


def provider_detail_search(
    movie: dict[str, Any],
    collectors: dict[str, Any],
) -> dict[str, Any]:
    provider = safe_text(movie.get("provider_code")).upper()
    external_movie_id = safe_text(movie.get("external_movie_id"))
    if not external_movie_id:
        return {"status": "skipped", "reason": "missing_external_movie_id", "genres": []}

    if provider == LOTTE:
        detail_movie_id = safe_text(movie.get("representative_movie_id")) or external_movie_id
        detail = collectors[LOTTE].fetch_movie_detail(detail_movie_id)
        genres = canonical_genres_from_values(
            [
                detail.get("MovieGenreNameKR"),
                detail.get("MovieGenreNameKR2"),
                detail.get("MovieGenreNameKR3"),
            ]
        )
    elif provider == MEGABOX:
        detail_movie_id = safe_text(movie.get("representative_movie_id")) or external_movie_id
        detail = collectors[MEGABOX].fetch_movie_detail(detail_movie_id)
        genres = canonical_genres_from_values([detail.get("genre_name")])
    else:
        return {"status": "skipped", "reason": "unsupported_provider", "genres": []}

    if not genres:
        return {"status": "skipped", "reason": "no_canonical_genre", "genres": []}
    return {"status": "matched", "genres": genres}


def kobis_api_key() -> str:
    file_values = parse_env_file()
    return (
        safe_text(os.environ.get("KOBIS_API_KEY"))
        or safe_text(os.environ.get("DABOYEO_KOBIS_API_KEY"))
        or safe_text(os.environ.get("KOFIC_API_KEY"))
        or safe_text(file_values.get("KOBIS_API_KEY"))
        or safe_text(file_values.get("DABOYEO_KOBIS_API_KEY"))
        or safe_text(file_values.get("KOFIC_API_KEY"))
    )


def open_date_text(value: Any) -> str:
    text = safe_text(value)
    if not text:
        return ""
    return "".join(ch for ch in text[:10] if ch.isdigit())


def movie_title_keys(movie: dict[str, Any]) -> set[str]:
    keys = {
        normalize_title_for_match(movie.get("title_ko")),
        normalize_title_for_match(movie.get("title_en")),
    }
    return {key for key in keys if key}


def kobis_search(movie: dict[str, Any], api_key: str) -> dict[str, Any]:
    title = safe_text(movie.get("title_ko")) or safe_text(movie.get("title_en"))
    if not title:
        return {"status": "skipped", "reason": "missing_title", "genres": []}
    params = {
        "key": api_key,
        "movieNm": title,
        "itemPerPage": "10",
    }
    url = f"{KOBIS_MOVIE_LIST_URL}?{urlencode(params)}"
    request = Request(url, headers={"User-Agent": KOBIS_USER_AGENT})
    with urlopen(request, timeout=8) as response:
        payload = json.loads(response.read().decode("utf-8"))

    candidates = payload.get("movieListResult", {}).get("movieList", [])
    title_keys = movie_title_keys(movie)
    exact = [
        item for item in candidates
        if title_keys.intersection({
            normalize_title_for_match(item.get("movieNm")),
            normalize_title_for_match(item.get("movieNmEn")),
        })
    ]
    if not exact:
        return {"status": "skipped", "reason": "no_exact_title_match", "genres": []}

    release_date = open_date_text(movie.get("release_date"))
    dated = [item for item in exact if release_date and open_date_text(item.get("openDt")) == release_date]
    if dated:
        exact = dated
    if len(exact) != 1:
        return {"status": "skipped", "reason": "ambiguous_title_match", "genres": [], "matchCount": len(exact)}

    match = exact[0]
    genres = canonical_genres_from_values([match.get("genreAlt"), match.get("repGenreNm")])
    if not genres:
        return {"status": "skipped", "reason": "no_canonical_genre", "genres": []}
    return {
        "status": "matched",
        "movieCd": match.get("movieCd"),
        "movieNm": match.get("movieNm"),
        "openDt": match.get("openDt"),
        "genres": genres,
    }


def apply_genre_tags(
    cursor: Any,
    movie: dict[str, Any],
    genres: list[str],
    *,
    source: str,
    confidence: Decimal,
    dry_run: bool,
) -> int:
    upserted = 0
    for genre in missing_genres(movie, genres):
        if not dry_run:
            upsert_movie_tag(cursor, movie, "genre", genre, source, confidence)
        upserted += 1
    return upserted


def enrich_movie_tags(
    cursor: Any,
    overrides_path: Path = DEFAULT_OVERRIDES_PATH,
    *,
    current_only: bool = True,
    dry_run: bool = False,
    provider_auto_tags: bool = True,
    provider_detail_metadata: bool = False,
    provider_detail_limit: int = 40,
    kobis_metadata: bool = True,
    kobis_limit: int = 60,
) -> dict[str, Any]:
    overrides = load_overrides(overrides_path)
    movies = current_movies(cursor, current_only=current_only)
    result: dict[str, Any] = {
        "mode": "dry-run" if dry_run else "write",
        "overrides_path": str(overrides_path),
        "current_only": current_only,
        "override_count": len(overrides),
        "current_movie_count": len(movies),
        "provider_auto_tagging": {
            "enabled": provider_auto_tags,
            "matched_movie_count": 0,
            "tags_planned": 0,
            "tags_upserted": 0,
            "movies": [],
        },
        "provider_detail_metadata": {
            "enabled": provider_detail_metadata,
            "limit": provider_detail_limit,
            "checked_movie_count": 0,
            "matched_movie_count": 0,
            "tags_planned": 0,
            "tags_upserted": 0,
            "movies": [],
            "skipped": [],
        },
        "kobis_metadata": {
            "enabled": False,
            "reason": "disabled",
            "checked_movie_count": 0,
            "matched_movie_count": 0,
            "tags_planned": 0,
            "tags_upserted": 0,
            "movies": [],
            "skipped": [],
        },
        "matched_movie_count": 0,
        "skipped_count": 0,
        "tags_planned": 0,
        "tags_upserted": 0,
        "movies": [],
        "skipped": [],
    }

    if provider_auto_tags:
        provider_confidence = Decimal("0.9500")
        for movie in movies:
            genres = provider_auto_genres(movie)
            planned = missing_genres(movie, genres)
            if not planned:
                continue
            result["provider_auto_tagging"]["matched_movie_count"] += 1
            result["provider_auto_tagging"]["tags_planned"] += len(planned)
            result["provider_auto_tagging"]["movies"].append(
                {
                    "providerCode": movie["provider_code"],
                    "externalMovieId": movie["external_movie_id"],
                    "titleKo": movie["title_ko"],
                    "futureShowtimeCount": movie["future_showtime_count"],
                    "tags": [f"genre:{genre}" for genre in planned],
                }
            )
            if dry_run:
                continue
            for genre in planned:
                upsert_movie_tag(cursor, movie, "genre", genre, SOURCE_PROVIDER_METADATA, provider_confidence)
                result["provider_auto_tagging"]["tags_upserted"] += 1

    if provider_detail_metadata:
        detail_confidence = Decimal("0.9600")
        detail_collectors = provider_detail_collectors()
        for movie in movies:
            if provider_detail_limit >= 0 and result["provider_detail_metadata"]["checked_movie_count"] >= provider_detail_limit:
                break

            result["provider_detail_metadata"]["checked_movie_count"] += 1
            try:
                lookup = provider_detail_search(movie, detail_collectors)
            except Exception as exc:  # noqa: BLE001 - provider detail calls are best-effort.
                result["provider_detail_metadata"]["skipped"].append(
                    {
                        "providerCode": movie["provider_code"],
                        "externalMovieId": movie["external_movie_id"],
                        "titleKo": movie["title_ko"],
                        "reason": "provider_detail_error",
                        "error": exc.__class__.__name__,
                    }
                )
                continue
            if lookup["status"] != "matched":
                result["provider_detail_metadata"]["skipped"].append(
                    {
                        "providerCode": movie["provider_code"],
                        "externalMovieId": movie["external_movie_id"],
                        "titleKo": movie["title_ko"],
                        "reason": lookup.get("reason", "not_matched"),
                    }
                )
                continue

            planned = missing_genres(movie, lookup["genres"])
            if not planned:
                continue
            result["provider_detail_metadata"]["matched_movie_count"] += 1
            result["provider_detail_metadata"]["tags_planned"] += len(planned)
            result["provider_detail_metadata"]["movies"].append(
                {
                    "providerCode": movie["provider_code"],
                    "externalMovieId": movie["external_movie_id"],
                    "titleKo": movie["title_ko"],
                    "futureShowtimeCount": movie["future_showtime_count"],
                    "tags": [f"genre:{genre}" for genre in planned],
                }
            )
            if dry_run:
                continue
            for genre in planned:
                upsert_movie_tag(cursor, movie, "genre", genre, SOURCE_PROVIDER_DETAIL, detail_confidence)
                result["provider_detail_metadata"]["tags_upserted"] += 1

    key = kobis_api_key()
    if kobis_metadata and key:
        result["kobis_metadata"]["enabled"] = True
        result["kobis_metadata"]["reason"] = "api_key_configured"
        kobis_confidence = Decimal("0.9500")
        for movie in movies[: max(0, kobis_limit)]:
            result["kobis_metadata"]["checked_movie_count"] += 1
            try:
                lookup = kobis_search(movie, key)
            except Exception as exc:  # noqa: BLE001 - keep batch enrichment alive.
                result["kobis_metadata"]["skipped"].append(
                    {
                        "providerCode": movie["provider_code"],
                        "externalMovieId": movie["external_movie_id"],
                        "titleKo": movie["title_ko"],
                        "reason": "kobis_error",
                        "error": exc.__class__.__name__,
                    }
                )
                continue
            if lookup["status"] != "matched":
                result["kobis_metadata"]["skipped"].append(
                    {
                        "providerCode": movie["provider_code"],
                        "externalMovieId": movie["external_movie_id"],
                        "titleKo": movie["title_ko"],
                        "reason": lookup.get("reason", "not_matched"),
                    }
                )
                continue
            planned = missing_genres(movie, lookup["genres"])
            if not planned:
                continue
            result["kobis_metadata"]["matched_movie_count"] += 1
            result["kobis_metadata"]["tags_planned"] += len(planned)
            result["kobis_metadata"]["movies"].append(
                {
                    "providerCode": movie["provider_code"],
                    "externalMovieId": movie["external_movie_id"],
                    "titleKo": movie["title_ko"],
                    "kobisMovieCd": lookup.get("movieCd"),
                    "tags": [f"genre:{genre}" for genre in planned],
                }
            )
            if dry_run:
                continue
            for genre in planned:
                upsert_movie_tag(cursor, movie, "genre", genre, SOURCE_KOBIS_METADATA, kobis_confidence)
                result["kobis_metadata"]["tags_upserted"] += 1
    elif kobis_metadata:
        result["kobis_metadata"]["reason"] = "missing_api_key"

    for override in overrides:
        movie = current_movie_for_override(cursor, override)
        if movie is None:
            result["skipped"].append(
                {
                    "providerCode": override.provider_code,
                    "externalMovieId": override.external_movie_id,
                    "reason": "movie_not_found",
                }
            )
            result["skipped_count"] += 1
            continue
        if current_only and movie["future_showtime_count"] <= 0:
            result["skipped"].append(
                {
                    "providerCode": override.provider_code,
                    "externalMovieId": override.external_movie_id,
                    "titleKo": movie["title_ko"],
                    "reason": "no_current_or_future_showtimes",
                }
            )
            result["skipped_count"] += 1
            continue

        existing_tags = movie.setdefault("existing_tag_keys", set())
        planned_tags = [
            (tag_type, tag_value)
            for tag_type, tag_value in override.tags
            if f"{tag_type}:{tag_value}".lower() not in existing_tags
        ]
        tags = [f"{tag_type}:{tag_value}" for tag_type, tag_value in planned_tags]
        if not planned_tags:
            continue
        result["matched_movie_count"] += 1
        result["tags_planned"] += len(tags)
        result["movies"].append(
            {
                "providerCode": movie["provider_code"],
                "externalMovieId": movie["external_movie_id"],
                "titleKo": movie["title_ko"],
                "futureShowtimeCount": movie["future_showtime_count"],
                "tags": tags,
            }
        )
        if dry_run:
            continue
        for tag_type, tag_value in planned_tags:
            upsert_movie_tag(cursor, movie, tag_type, tag_value, override.source, override.confidence)
            existing_tags.add(f"{tag_type}:{tag_value}".lower())
            result["tags_upserted"] += 1
    return result


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Enrich current movie_tags from exact provider movie metadata overrides."
    )
    parser.add_argument("--overrides", default=str(DEFAULT_OVERRIDES_PATH))
    parser.add_argument("--all-movies", action="store_true", help="Apply overrides even when the movie has no future showtimes.")
    parser.add_argument("--dry-run", action="store_true", help="Read DB and report planned tag writes without mutating movie_tags.")
    parser.add_argument("--validate-only", action="store_true", help="Validate the override catalog without connecting to TiDB.")
    parser.add_argument("--skip-provider-auto-tags", action="store_true", help="Skip canonical genre normalization from provider metadata.")
    parser.add_argument("--include-provider-details", action="store_true", help="Fetch provider movie detail pages for current/future movies and add any missing canonical genre tags.")
    parser.add_argument("--provider-detail-limit", type=int, default=40, help="Maximum movies to check through provider detail pages; 0 checks none.")
    parser.add_argument("--skip-kobis-metadata", action="store_true", help="Skip optional KOBIS metadata enrichment even when an API key exists.")
    parser.add_argument("--kobis-metadata-limit", type=int, default=60, help="Maximum current/future movies to check through optional KOBIS metadata enrichment.")
    return parser.parse_args(argv)


def run(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    overrides_path = Path(args.overrides)
    if args.validate_only:
        overrides = load_overrides(overrides_path)
        print(
            json.dumps(
                {
                    "mode": "validate-only",
                    "overrides_path": str(overrides_path),
                    "override_count": len(overrides),
                    "tags": sum(len(override.tags) for override in overrides),
                },
                ensure_ascii=False,
                indent=2,
                default=str,
            )
        )
        return 0

    config = load_tidb_config()
    with connect_tidb(config) as conn:
        with conn.cursor() as cursor:
            result = enrich_movie_tags(
                cursor,
                overrides_path,
                current_only=not args.all_movies,
                dry_run=args.dry_run,
                provider_auto_tags=not args.skip_provider_auto_tags,
                provider_detail_metadata=args.include_provider_details,
                provider_detail_limit=args.provider_detail_limit,
                kobis_metadata=not args.skip_kobis_metadata,
                kobis_limit=args.kobis_metadata_limit,
            )
    print(json.dumps(result, ensure_ascii=False, indent=2, default=str))
    return 0


if __name__ == "__main__":
    raise SystemExit(run())
