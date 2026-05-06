from __future__ import annotations

import argparse
import json
import sys
from collections.abc import Iterable
from datetime import datetime, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from collectors.common.normalize import (
    combine_datetime,
    json_for_db,
    lotte_showtime_key,
    megabox_showtime_key,
    normalize_seat_status,
    parse_yyyymmdd,
    seat_occupancy_rate,
    to_decimal,
    to_int,
)
from collectors.common.repository import insert_dict, insert_many_dicts, upsert_and_select_id, upsert_dict
from collectors.common.tidb import connect_tidb, load_tidb_config
from collectors.lotte import LotteCinemaCollector
from collectors.megabox import MegaboxCollector
from scripts.ingest.genre_tagging import canonical_genres_from_provider_row


LOTTE = "LOTTE_CINEMA"
MEGABOX = "MEGABOX"
LOTTE_BOOKING_URL = "https://www.lottecinema.co.kr/NLCHS/Ticketing"
TAG_SOURCE_INGEST = "ingest"
DEFAULT_METADATA_OVERRIDES_PATH = Path(__file__).with_name("current_movie_tag_overrides.json")
FORMAT_TAG_PATTERNS: list[tuple[str, str]] = [
    ("imax", "imax"),
    ("4dx", "4dx"),
    ("screenx", "screenx"),
    ("dolby", "dolby"),
    ("atmos", "atmos"),
    ("mx4d", "mx4d"),
    ("2d", "2d"),
    ("3d", "3d"),
    ("vip", "vip"),
    ("boutique", "boutique"),
]


def now_db() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def today_yyyymmdd() -> str:
    return datetime.now().strftime("%Y%m%d")


def today_date() -> datetime:
    return datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)


def blank_to_none(value: Any) -> Any:
    if value is None:
        return None
    if isinstance(value, str) and not value.strip():
        return None
    return value


def safe_text(value: Any, fallback: str = "") -> str:
    text = "" if value is None else str(value).strip()
    return text or fallback


def db_date(value: Any) -> str | None:
    text = str(value or "").strip()
    if not text:
        return None
    if len(text) >= 10 and text[4] == "-" and text[7] == "-":
        return text[:10]
    if len(text) >= 8 and text[:8].isdigit():
        return parse_yyyymmdd(text[:8])
    return None


def future_or_today_date(value: Any) -> bool:
    date_text = db_date(value)
    if not date_text:
        return False
    return datetime.strptime(date_text, "%Y-%m-%d") >= today_date()


def unique_values(values: Iterable[str]) -> list[str]:
    seen: set[str] = set()
    items: list[str] = []
    for value in values:
        text = safe_text(value)
        if text and text not in seen:
            seen.add(text)
            items.append(text)
    return items


def db_datetime(date_value: Any, time_value: Any) -> str | None:
    date_text = db_date(date_value)
    if not date_text:
        return None

    text = str(time_value or "").strip()
    if not text:
        return None

    try:
        if ":" in text:
            parts = text.split(":")
            if len(parts) < 2:
                return None
            hour = int(parts[0])
            minute = int(parts[1])
            second = int(parts[2]) if len(parts) >= 3 and parts[2] else 0
        else:
            digits = "".join(ch for ch in text if ch.isdigit())
            if len(digits) < 4:
                return None
            hour = int(digits[:-2])
            minute = int(digits[-2:])
            second = 0
    except ValueError:
        return combine_datetime(date_text, time_value)

    if minute > 59 or second > 59:
        return None

    day_offset, hour = divmod(hour, 24)
    dt = datetime.strptime(date_text, "%Y-%m-%d") + timedelta(days=day_offset)
    return f"{dt.strftime('%Y-%m-%d')} {hour:02d}:{minute:02d}:{second:02d}"


def db_rate(total: Any, remaining: Any) -> Decimal | None:
    rate = seat_occupancy_rate(total, remaining)
    return rate.quantize(Decimal("0.001")) if rate is not None else None


def sold_count(total: Any, remaining: Any) -> int | None:
    total_int = to_int(total)
    remaining_int = to_int(remaining)
    if total_int is None or remaining_int is None:
        return None
    return max(total_int - remaining_int, 0)


def limited(rows: Iterable[dict[str, Any]], limit: int | None) -> list[dict[str, Any]]:
    items = list(rows)
    return items if limit is None or limit < 1 else items[:limit]


def unique_seat_key(raw_key: Any, index: int, seen: set[str]) -> str:
    base = safe_text(raw_key, f"seat-{index}")
    key = base
    suffix = 2
    while key in seen:
        key = f"{base}-{suffix}"
        suffix += 1
    seen.add(key)
    return key


def status_counts(provider: str, seats: list[dict[str, Any]]) -> dict[str, int]:
    counts = {"available": 0, "sold": 0, "unavailable": 0, "special": 0, "unknown": 0}
    for seat in seats:
        status = normalize_seat_status(provider, seat.get("seat_status_code"), seat.get("raw"))
        counts[status if status in counts else "unknown"] += 1
    return counts


def provider_ingest_result(provider: str, play_dates: list[str]) -> dict[str, Any]:
    return {
        "provider": provider,
        "play_dates": play_dates,
        "play_date_count": len(play_dates),
        "movies_upserted": 0,
        "theaters_upserted": 0,
        "screens_upserted": 0,
        "schedule_queries": 0,
        "showtimes_upserted": 0,
        "movie_tags_upserted": 0,
        "movies_backfilled_from_showtimes": 0,
        "showtime_movie_links_repaired": 0,
        "showtime_location_links_repaired": 0,
        "seat_snapshots_inserted": 0,
        "seat_items_inserted": 0,
    }


def _clean_tag_value(value: Any) -> str:
    text = safe_text(value)
    if not text:
        return ""
    text = " ".join(text.split())
    text = text.strip(" ,/|+()[]{}<>")
    return text.lower()


def _split_genre_values(value: Any) -> list[str]:
    text = safe_text(value)
    if not text:
        return []
    parts = []
    for chunk in text.replace("／", "/").replace("·", "/").split("/"):
        for item in chunk.split(","):
            normalized = _clean_tag_value(item)
            if normalized:
                parts.append(normalized)
    return parts


def _raw_json_dict(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    if not value:
        return {}
    try:
        loaded = json.loads(value)
    except (TypeError, ValueError):
        return {}
    return loaded if isinstance(loaded, dict) else {}


def _normalize_age_tag(value: Any) -> str | None:
    text = safe_text(value).lower()
    if not text:
        return None
    if "전체" in text or "all" in text or "전연령" in text:
        return "all"
    if any(token in text for token in ["청불", "관람불가", "불가"]):
        return "19"
    if "12" in text:
        return "12"
    if "15" in text:
        return "15"
    if "18" in text:
        return "18"
    if "19" in text:
        return "19"
    return None


def _normalize_format_tags(value: Any) -> list[str]:
    text = safe_text(value).lower()
    if not text:
        return []
    compact = "".join(ch for ch in text if ch.isalnum())
    tags: list[str] = []
    for needle, normalized in FORMAT_TAG_PATTERNS:
        if needle in compact or needle in text:
            tags.append(normalized)
    return tags


def _append_tag(
    tags: list[dict[str, Any]],
    seen: set[tuple[str, str]],
    tag_type: str,
    tag_value: str,
    confidence: Decimal,
) -> None:
    if not tag_value:
        return
    key = (tag_type, tag_value)
    if key in seen:
        return
    seen.add(key)
    tags.append(
        {
            "tag_type": tag_type,
            "tag_value": tag_value,
            "confidence": confidence,
        }
    )


def collect_movie_tags(provider: str, row: dict[str, Any]) -> list[dict[str, Any]]:
    tags: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()
    raw = row.get("raw") if isinstance(row.get("raw"), dict) else {}

    for genre in canonical_genres_from_provider_row(row):
        _append_tag(tags, seen, "genre", genre, Decimal("0.9500"))

    age_fields = [
        row.get("age_rating"),
        row.get("admisClassCdNm"),
        raw.get("ViewGradeNameKR"),
        raw.get("admisClassCdNm"),
    ]
    for field_value in age_fields:
        age_tag = _normalize_age_tag(field_value)
        if age_tag:
            _append_tag(tags, seen, "age_rating", age_tag, Decimal("1.0000"))

    format_fields = [
        row.get("screening_type"),
        row.get("screen_type"),
        row.get("screen_division_name"),
        row.get("film_name"),
        row.get("sound_type_name"),
        raw.get("screenType"),
        raw.get("playKindNm"),
        raw.get("theabExpoNm"),
    ]
    for field_value in format_fields:
        for format_tag in _normalize_format_tags(field_value):
            _append_tag(tags, seen, "format", format_tag, Decimal("0.9000"))

    return tags


def upsert_movie_tags(
    cursor: Any,
    provider: str,
    movie_id: int,
    external_movie_id: str,
    row: dict[str, Any],
    seen_tags: set[tuple[str, str, str, str]],
) -> int:
    if not external_movie_id:
        return 0
    upserted = 0
    for tag in collect_movie_tags(provider, row):
        cache_key = (provider, external_movie_id, tag["tag_type"], tag["tag_value"])
        if cache_key in seen_tags:
            continue
        seen_tags.add(cache_key)
        upsert_dict(
            cursor,
            "movie_tags",
            {
                "movie_id": movie_id,
                "provider_code": provider,
                "external_movie_id": external_movie_id,
                "tag_type": tag["tag_type"],
                "tag_value": tag["tag_value"],
                "source": TAG_SOURCE_INGEST,
                "confidence": tag["confidence"],
            },
        )
        upserted += 1
    return upserted


def movie_payload(provider: str, row: dict[str, Any], collected_at: str) -> dict[str, Any]:
    if provider == LOTTE:
        external_id = safe_text(row.get("movie_no"))
        return {
            "provider_code": LOTTE,
            "external_movie_id": external_id,
            "representative_movie_id": external_id,
            "title_ko": safe_text(row.get("movie_name"), f"LOTTE_{external_id}"),
            "title_en": blank_to_none(row.get("movie_name_en")),
            "age_rating": blank_to_none(row.get("age_rating")),
            "runtime_minutes": to_int(row.get("runtime_minutes")),
            "release_date": db_date(row.get("release_date")),
            "booking_rate": to_decimal(row.get("booking_rate")),
            "box_office_rank": None,
            "poster_url": blank_to_none(row.get("poster_url")),
            "raw_json": json_for_db(row.get("raw") or row),
            "last_collected_at": collected_at,
        }

    external_id = safe_text(row.get("movie_no"), safe_text(row.get("representative_movie_no")))
    return {
        "provider_code": MEGABOX,
        "external_movie_id": external_id,
        "representative_movie_id": blank_to_none(row.get("representative_movie_no")),
        "title_ko": safe_text(row.get("movie_name"), f"MEGABOX_{external_id}"),
        "title_en": blank_to_none(row.get("movie_name_en")),
        "age_rating": blank_to_none(row.get("age_rating")),
        "runtime_minutes": to_int(row.get("runtime_minutes")),
        "release_date": db_date(row.get("release_date")),
        "booking_rate": to_decimal(row.get("booking_rate")),
        "box_office_rank": to_int(row.get("box_office_rank")),
        "poster_url": blank_to_none(row.get("poster_url")),
        "raw_json": json_for_db(row.get("raw") or row),
        "last_collected_at": collected_at,
    }


def theater_payload(provider: str, row: dict[str, Any], collected_at: str) -> dict[str, Any]:
    if provider == LOTTE:
        external_id = safe_text(row.get("cinema_id"))
        return {
            "provider_code": LOTTE,
            "external_theater_id": external_id,
            "name": safe_text(row.get("cinema_name"), f"LOTTE_THEATER_{external_id}"),
            "region_code": blank_to_none(row.get("cinema_area_code") or row.get("detail_division_code")),
            "region_name": blank_to_none(row.get("cinema_area_name") or row.get("detail_division_name")),
            "address": blank_to_none(row.get("address_summary")),
            "latitude": to_decimal(row.get("latitude")),
            "longitude": to_decimal(row.get("longitude")),
            "raw_json": json_for_db(row.get("raw") or row),
            "last_collected_at": collected_at,
        }

    external_id = safe_text(row.get("branch_no"))
    return {
        "provider_code": MEGABOX,
        "external_theater_id": external_id,
        "name": safe_text(row.get("branch_name"), f"MEGABOX_THEATER_{external_id}"),
        "region_code": blank_to_none(row.get("area_code")),
        "region_name": blank_to_none(row.get("area_name")),
        "address": None,
        "latitude": None,
        "longitude": None,
        "raw_json": json_for_db(row.get("raw") or row),
        "last_collected_at": collected_at,
    }


def screen_payload(
    provider: str,
    row: dict[str, Any],
    theater_id: int,
    collected_at: str,
) -> dict[str, Any]:
    if provider == LOTTE:
        external_theater_id = safe_text(row.get("cinema_id"))
        external_screen_id = safe_text(row.get("screen_id"), safe_text(row.get("screen_name"), "UNKNOWN"))
        return {
            "provider_code": LOTTE,
            "theater_id": theater_id,
            "external_theater_id": external_theater_id,
            "external_screen_id": external_screen_id,
            "name": safe_text(row.get("screen_name"), f"LOTTE_SCREEN_{external_screen_id}"),
            "screen_type": blank_to_none(row.get("screen_division_name") or row.get("film_name")),
            "floor_name": blank_to_none(row.get("screen_floor")),
            "total_seat_count": to_int(row.get("total_seat_count")),
            "raw_json": json_for_db(row.get("raw") or row),
            "last_collected_at": collected_at,
        }

    external_theater_id = safe_text(row.get("branch_no"))
    external_screen_id = safe_text(row.get("theater_no"), safe_text(row.get("screen_name"), "UNKNOWN"))
    return {
        "provider_code": MEGABOX,
        "theater_id": theater_id,
        "external_theater_id": external_theater_id,
        "external_screen_id": external_screen_id,
        "name": safe_text(row.get("screen_name"), f"MEGABOX_SCREEN_{external_screen_id}"),
        "screen_type": blank_to_none(row.get("screen_type")),
        "floor_name": None,
        "total_seat_count": to_int(row.get("total_seat_count")),
        "raw_json": json_for_db(row.get("raw") or row),
        "last_collected_at": collected_at,
    }


def upsert_movie(cursor: Any, provider: str, row: dict[str, Any], collected_at: str) -> int:
    payload = movie_payload(provider, row, collected_at)
    if not payload["external_movie_id"]:
        raise ValueError(f"{provider} external_movie_id is empty")
    return upsert_and_select_id(
        cursor,
        "movies",
        payload,
        {"provider_code": provider, "external_movie_id": payload["external_movie_id"]},
    )


def upsert_movie_from_schedule(
    cursor: Any,
    provider: str,
    schedule_row: dict[str, Any],
    collected_at: str,
) -> int:
    payload = movie_payload(provider, schedule_row, collected_at)
    if not payload["external_movie_id"]:
        raise ValueError(f"{provider} external_movie_id is empty")
    return upsert_and_select_id(
        cursor,
        "movies",
        payload,
        {"provider_code": provider, "external_movie_id": payload["external_movie_id"]},
    )


def showtime_movie_row(
    provider: str,
    external_movie_id: str,
    movie_title: str,
    raw_json: Any,
) -> dict[str, Any]:
    raw = _raw_json_dict(raw_json)
    if provider == MEGABOX:
        return {
            "provider": MEGABOX,
            "movie_no": external_movie_id,
            "representative_movie_no": raw.get("rpstMovieNo"),
            "movie_name": movie_title or raw.get("movieNm") or raw.get("rpstMovieNm"),
            "movie_name_en": raw.get("movieEngNm"),
            "age_rating": raw.get("admisClassCdNm"),
            "runtime_minutes": raw.get("playTime"),
            "booking_rate": raw.get("boxoBokdRt"),
            "box_office_rank": raw.get("boxoRank"),
            "release_date": raw.get("openDt"),
            "screening_type": raw.get("screenType"),
            "screen_type": raw.get("playKindNm"),
            "poster_url": MegaboxCollector._build_poster_url(raw.get("moviePosterImg") or raw.get("movieImgPath")),
            "raw": raw,
        }
    return {
        "provider": LOTTE,
        "movie_no": external_movie_id,
        "movie_name": movie_title,
        "age_rating": raw.get("ViewGradeNameKR") or raw.get("age_rating"),
        "runtime_minutes": raw.get("playTime") or raw.get("runtime_minutes"),
        "release_date": raw.get("releaseDate") or raw.get("release_date"),
        "raw": raw,
    }


def backfill_missing_movies_from_showtimes(
    cursor: Any,
    provider: str,
    collected_at: str,
    seen_tags: set[tuple[str, str, str, str]],
) -> tuple[int, int]:
    cursor.execute(
        """
        SELECT s.external_movie_id, s.movie_title, s.raw_json
        FROM showtimes s
        LEFT JOIN movies m
          ON m.provider_code = s.provider_code
         AND m.external_movie_id = s.external_movie_id
        WHERE s.provider_code = %s
          AND s.external_movie_id IS NOT NULL
          AND m.id IS NULL
        ORDER BY s.last_collected_at DESC
        """,
        [provider],
    )
    seen_external_ids: set[str] = set()
    backfilled = 0
    tags_upserted = 0
    for external_movie_id, movie_title, raw_json in cursor.fetchall():
        external_id = safe_text(external_movie_id)
        if not external_id or external_id in seen_external_ids:
            continue
        seen_external_ids.add(external_id)
        row = showtime_movie_row(provider, external_id, safe_text(movie_title), raw_json)
        movie_id = upsert_movie_from_schedule(cursor, provider, row, collected_at)
        tags_upserted += upsert_movie_tags(cursor, provider, movie_id, external_id, row, seen_tags)
        backfilled += 1
    return backfilled, tags_upserted


def finalize_provider_ingest(
    cursor: Any,
    provider: str,
    collected_at: str,
    movie_tag_keys: set[tuple[str, str, str, str]],
    result: dict[str, Any],
) -> dict[str, Any]:
    backfilled, backfill_tags = backfill_missing_movies_from_showtimes(
        cursor,
        provider,
        collected_at,
        movie_tag_keys,
    )
    result["movies_backfilled_from_showtimes"] = backfilled
    result["movie_tags_upserted"] += backfill_tags
    result["showtime_movie_links_repaired"] = repair_showtime_movie_links(cursor, provider)
    result["showtime_location_links_repaired"] = repair_showtime_location_links(cursor, provider)
    return result


def resolve_megabox_movie_id(
    cursor: Any,
    schedule_row: dict[str, Any],
    movie_ids: dict[str, int],
    collected_at: str,
) -> tuple[int | None, str, bool]:
    schedule_movie_no = safe_text(schedule_row.get("movie_no"))
    schedule_representative_movie_no = safe_text(schedule_row.get("representative_movie_no"))
    movie_key = schedule_movie_no or schedule_representative_movie_no
    created = False
    if schedule_movie_no:
        movie_id = movie_ids.get(schedule_movie_no)
        if movie_id is None:
            movie_id = upsert_movie_from_schedule(cursor, MEGABOX, schedule_row, collected_at)
            created = True
    elif schedule_representative_movie_no:
        movie_id = movie_ids.get(schedule_representative_movie_no)
        if movie_id is None:
            movie_id = upsert_movie_from_schedule(cursor, MEGABOX, schedule_row, collected_at)
            created = True
    else:
        return None, movie_key, created
    movie_ids[movie_key] = movie_id
    if schedule_movie_no:
        movie_ids[schedule_movie_no] = movie_id
    if schedule_representative_movie_no:
        movie_ids[schedule_representative_movie_no] = movie_id
    return movie_id, movie_key, created


def upsert_theater(cursor: Any, provider: str, row: dict[str, Any], collected_at: str) -> int:
    payload = theater_payload(provider, row, collected_at)
    if not payload["external_theater_id"]:
        raise ValueError(f"{provider} external_theater_id is empty")
    return upsert_and_select_id(
        cursor,
        "theaters",
        payload,
        {"provider_code": provider, "external_theater_id": payload["external_theater_id"]},
    )


def upsert_screen(cursor: Any, provider: str, row: dict[str, Any], theater_id: int, collected_at: str) -> int:
    payload = screen_payload(provider, row, theater_id, collected_at)
    return upsert_and_select_id(
        cursor,
        "screens",
        payload,
        {
            "provider_code": provider,
            "external_theater_id": payload["external_theater_id"],
            "external_screen_id": payload["external_screen_id"],
        },
    )


def showtime_payload(
    provider: str,
    row: dict[str, Any],
    movie_id: int,
    theater_id: int,
    screen_id: int,
    collected_at: str,
    theater_row: dict[str, Any] | None = None,
) -> dict[str, Any]:
    total = to_int(row.get("total_seat_count"))
    remaining = to_int(row.get("remaining_seat_count"))
    if provider == LOTTE:
        theater = theater_row or {}
        key = lotte_showtime_key(row)
        return {
            "provider_code": LOTTE,
            "external_showtime_key": key,
            "movie_id": movie_id,
            "theater_id": theater_id,
            "screen_id": screen_id,
            "external_movie_id": blank_to_none(row.get("movie_no")),
            "external_theater_id": blank_to_none(row.get("cinema_id")),
            "external_screen_id": blank_to_none(row.get("screen_id")),
            "movie_title": safe_text(row.get("movie_name"), "Untitled"),
            "theater_name": safe_text(row.get("cinema_name"), "Unknown Theater"),
            "region_name": blank_to_none(theater.get("cinema_area_name") or theater.get("detail_division_name")),
            "region_code": blank_to_none(theater.get("cinema_area_code") or theater.get("detail_division_code")),
            "screen_name": blank_to_none(row.get("screen_name")),
            "screen_type": blank_to_none(row.get("screen_division_name")),
            "format_name": blank_to_none(row.get("film_name") or row.get("sound_type_name")),
            "show_date": db_date(row.get("play_date")),
            "starts_at": db_datetime(row.get("play_date"), row.get("start_time")),
            "ends_at": db_datetime(row.get("play_date"), row.get("end_time")),
            "start_time_raw": blank_to_none(row.get("start_time")),
            "end_time_raw": blank_to_none(row.get("end_time")),
            "total_seat_count": total,
            "remaining_seat_count": remaining,
            "sold_seat_count": sold_count(total, remaining),
            "seat_occupancy_rate": db_rate(total, remaining),
            "remaining_seat_source": "provider",
            "booking_available": blank_to_none(row.get("booking_available")),
            "min_price_amount": None,
            "currency_code": "KRW",
            "booking_key_json": json_for_db(row.get("booking_key") or {}),
            "booking_url": LOTTE_BOOKING_URL,
            "raw_json": json_for_db(row.get("raw") or row),
            "last_collected_at": collected_at,
        }

    key = megabox_showtime_key(row)
    return {
        "provider_code": MEGABOX,
        "external_showtime_key": key,
        "movie_id": movie_id,
        "theater_id": theater_id,
        "screen_id": screen_id,
        "external_movie_id": blank_to_none(row.get("movie_no") or row.get("representative_movie_no")),
        "external_theater_id": blank_to_none(row.get("branch_no")),
        "external_screen_id": blank_to_none(row.get("theater_no")),
        "movie_title": safe_text(row.get("movie_name"), "Untitled"),
        "theater_name": safe_text(row.get("branch_name"), "Unknown Theater"),
        "region_name": blank_to_none(row.get("area_name")),
        "region_code": blank_to_none(row.get("area_code")),
        "screen_name": blank_to_none(row.get("screen_name")),
        "screen_type": blank_to_none(row.get("screen_type")),
        "format_name": blank_to_none(row.get("screen_type")),
        "show_date": db_date(row.get("play_date")),
        "starts_at": db_datetime(row.get("play_date"), row.get("start_time")),
        "ends_at": db_datetime(row.get("play_date"), row.get("end_time")),
        "start_time_raw": blank_to_none(row.get("start_time")),
        "end_time_raw": blank_to_none(row.get("end_time")),
        "total_seat_count": total,
        "remaining_seat_count": remaining,
        "sold_seat_count": sold_count(total, remaining),
        "seat_occupancy_rate": db_rate(total, remaining),
        "remaining_seat_source": "provider",
        "booking_available": blank_to_none(row.get("booking_available")),
        "min_price_amount": None,
        "currency_code": "KRW",
        "booking_key_json": json_for_db(
            {"play_schedule_no": row.get("play_schedule_no"), "branch_no": row.get("branch_no")}
        ),
        "booking_url": blank_to_none(row.get("booking_url")),
        "raw_json": json_for_db(row.get("raw") or row),
        "last_collected_at": collected_at,
    }


def upsert_showtime(
    cursor: Any,
    provider: str,
    row: dict[str, Any],
    movie_id: int,
    theater_id: int,
    screen_id: int,
    collected_at: str,
    theater_row: dict[str, Any] | None = None,
) -> int:
    payload = showtime_payload(provider, row, movie_id, theater_id, screen_id, collected_at, theater_row)
    if not payload["external_showtime_key"] or not payload["show_date"]:
        raise ValueError(f"{provider} showtime key or show_date is empty")
    return upsert_and_select_id(
        cursor,
        "showtimes",
        payload,
        {"provider_code": provider, "external_showtime_key": payload["external_showtime_key"]},
    )


def repair_showtime_movie_links(cursor: Any, provider: str) -> int:
    cursor.execute(
        """
        UPDATE showtimes s
        JOIN movies m
          ON m.provider_code = s.provider_code
         AND m.external_movie_id = s.external_movie_id
        SET s.movie_id = m.id
        WHERE s.provider_code = %s
          AND s.external_movie_id IS NOT NULL
          AND (s.movie_id IS NULL OR s.movie_id <> m.id)
        """,
        [provider],
    )
    return cursor.rowcount


def repair_showtime_location_links(cursor: Any, provider: str) -> int:
    cursor.execute(
        """
        UPDATE showtimes s
        JOIN theaters t
          ON t.provider_code = s.provider_code
         AND t.external_theater_id = s.external_theater_id
        LEFT JOIN screens sc
          ON sc.provider_code = s.provider_code
         AND sc.external_theater_id = s.external_theater_id
         AND sc.external_screen_id = s.external_screen_id
        SET s.theater_id = t.id,
            s.screen_id = COALESCE(sc.id, s.screen_id),
            s.region_name = COALESCE(NULLIF(s.region_name, ''), t.region_name),
            s.region_code = COALESCE(NULLIF(s.region_code, ''), t.region_code)
        WHERE s.provider_code = %s
          AND s.external_theater_id IS NOT NULL
          AND (
            s.theater_id IS NULL
            OR (sc.id IS NOT NULL AND s.screen_id IS NULL)
            OR s.region_name IS NULL
            OR s.region_name = ''
            OR s.region_code IS NULL
            OR s.region_code = ''
          )
        """,
        [provider],
    )
    return cursor.rowcount


def insert_seat_snapshot(
    cursor: Any,
    provider: str,
    showtime_id: int,
    external_showtime_key: str,
    schedule: dict[str, Any],
    seats: list[dict[str, Any]],
) -> tuple[int, int]:
    counts = status_counts(provider, seats)
    total = len(seats) or to_int(schedule.get("total_seat_count"))
    remaining = counts["available"] if seats else to_int(schedule.get("remaining_seat_count"))
    snapshot_id = insert_dict(
        cursor,
        "seat_snapshots",
        {
            "showtime_id": showtime_id,
            "provider_code": provider,
            "external_showtime_key": external_showtime_key,
            "snapshot_at": now_db(),
            "total_seat_count": total,
            "remaining_seat_count": remaining,
            "sold_seat_count": counts["sold"] if seats else sold_count(total, remaining),
            "unavailable_seat_count": counts["unavailable"] + counts["unknown"],
            "special_seat_count": counts["special"],
            "raw_summary_json": json_for_db(
                {"schedule": schedule, "seat_count": len(seats), "status_counts": counts}
            ),
        },
    )

    seen: set[str] = set()
    item_rows = []
    for index, seat in enumerate(seats, start=1):
        raw_key = seat.get("seat_no") if provider == LOTTE else seat.get("seat_id")
        item_rows.append(
            {
                "seat_snapshot_id": snapshot_id,
                "seat_key": unique_seat_key(raw_key, index, seen),
                "seat_label": blank_to_none(seat.get("seat_label")),
                "seat_row": blank_to_none(seat.get("seat_row")),
                "seat_column": blank_to_none(
                    seat.get("seat_column") or seat.get("seat_number") or seat.get("column_number")
                ),
                "normalized_status": normalize_seat_status(provider, seat.get("seat_status_code"), seat.get("raw")),
                "provider_status_code": blank_to_none(seat.get("seat_status_code")),
                "seat_type": blank_to_none(
                    seat.get("customer_division_code")
                    or seat.get("seat_block_set")
                    or seat.get("seat_type_code")
                    or seat.get("seat_class_code")
                ),
                "zone_name": blank_to_none(
                    seat.get("logical_block_code")
                    or seat.get("physical_block_code")
                    or seat.get("seat_zone_code")
                    or seat.get("seat_group_name")
                ),
                "x": to_decimal(seat.get("x") or seat.get("x_rate")),
                "y": to_decimal(seat.get("y") or seat.get("y_rate")),
                "width": to_decimal(seat.get("width") or seat.get("width_rate")),
                "height": to_decimal(seat.get("height")),
                "provider_meta_json": json_for_db(
                    {
                        "screen_floor": seat.get("screen_floor"),
                        "seat_floor": seat.get("seat_floor"),
                        "row_number": seat.get("row_number"),
                        "column_number": seat.get("column_number"),
                        "notice": seat.get("notice"),
                    }
                ),
                "raw_json": json_for_db(seat.get("raw") or seat),
            }
        )
    return snapshot_id, insert_many_dicts(cursor, "seat_snapshot_items", item_rows)


def choose_lotte_play_date(rows: list[dict[str, Any]], explicit: str | None) -> str:
    if explicit:
        return explicit
    for row in rows:
        raw = row.get("raw") or {}
        if raw.get("IsPlayDate") == "Y" or row.get("is_play") == "Y":
            return safe_text(row.get("play_date"))
    return safe_text(rows[0].get("play_date")) if rows else datetime.now().strftime("%Y-%m-%d")


def choose_lotte_play_dates(rows: list[dict[str, Any]], args: argparse.Namespace) -> list[str]:
    if args.lotte_play_date:
        return [args.lotte_play_date]
    if not args.all_provider_dates:
        return [choose_lotte_play_date(rows, None)]

    dates = unique_values(
        safe_text(row.get("play_date"))
        for row in rows
        if future_or_today_date(row.get("play_date")) and safe_text(row.get("is_play"), "Y") != "N"
    )
    if args.max_provider_dates and args.max_provider_dates > 0:
        dates = dates[: args.max_provider_dates]
    return dates or [choose_lotte_play_date(rows, None)]


def ingest_lotte(cursor: Any, args: argparse.Namespace) -> dict[str, Any]:
    collector = LotteCinemaCollector()
    collected_at = now_db()
    movies = limited(collector.build_movie_records(), args.limit_movies)
    theaters = limited(collector.build_cinema_records(), args.limit_theaters)
    play_date_rows = collector.build_play_date_records()
    play_dates = choose_lotte_play_dates(play_date_rows, args)
    result: dict[str, Any] = provider_ingest_result(LOTTE, play_dates)

    movie_ids: dict[str, int] = {}
    theater_ids: dict[str, int] = {}
    theater_by_id: dict[str, dict[str, Any]] = {}
    movie_tag_keys: set[tuple[str, str, str, str]] = set()
    for movie in movies:
        movie_id = upsert_movie(cursor, LOTTE, movie, collected_at)
        movie_no = safe_text(movie.get("movie_no"))
        if movie_no:
            movie_ids[movie_no] = movie_id
        result["movie_tags_upserted"] += upsert_movie_tags(
            cursor,
            LOTTE,
            movie_id,
            movie_no,
            movie,
            movie_tag_keys,
        )
        result["movies_upserted"] += 1
    for theater in theaters:
        theater_id = upsert_theater(cursor, LOTTE, theater, collected_at)
        theater_key = safe_text(theater.get("cinema_id"))
        theater_ids[theater_key] = theater_id
        theater_by_id[theater_key] = theater
        result["theaters_upserted"] += 1

    for play_date in play_dates:
        for movie in movies:
            if args.limit_schedules and result["showtimes_upserted"] >= args.limit_schedules:
                break
            movie_no = safe_text(movie.get("movie_no"))
            if not movie_no:
                continue
            for theater in theaters:
                if args.limit_schedules and result["showtimes_upserted"] >= args.limit_schedules:
                    break
                schedules = collector.build_schedule_records(
                    play_date,
                    collector.build_cinema_selector(theater.get("raw") or {}),
                    movie_no,
                )
                result["schedule_queries"] += 1
                for schedule in schedules:
                    if args.limit_schedules and result["showtimes_upserted"] >= args.limit_schedules:
                        break
                    theater_key = safe_text(schedule.get("cinema_id"))
                    theater_id = theater_ids.get(theater_key) or theater_ids[safe_text(theater.get("cinema_id"))]
                    movie_id = movie_ids.get(safe_text(schedule.get("movie_no"))) or movie_ids[movie_no]
                    screen_id = upsert_screen(cursor, LOTTE, schedule, theater_id, collected_at)
                    showtime_id = upsert_showtime(
                        cursor,
                        LOTTE,
                        schedule,
                        movie_id,
                        theater_id,
                        screen_id,
                        collected_at,
                        theater_by_id.get(theater_key) or theater,
                    )
                    result["screens_upserted"] += 1
                    result["showtimes_upserted"] += 1
                    schedule_movie_no = safe_text(schedule.get("movie_no")) or movie_no
                    if schedule_movie_no:
                        result["movie_tags_upserted"] += upsert_movie_tags(
                            cursor,
                            LOTTE,
                            movie_id,
                            schedule_movie_no,
                            schedule,
                            movie_tag_keys,
                        )

                    if args.include_seats and result["seat_snapshots_inserted"] < args.max_seat_snapshots:
                        booking_key = schedule.get("booking_key") or {}
                        seats = collector.build_seat_records(
                            cinema_id=to_int(booking_key.get("cinema_id")) or 0,
                            screen_id=to_int(booking_key.get("screen_id")) or 0,
                            play_date=safe_text(booking_key.get("play_date")),
                            play_sequence=to_int(booking_key.get("play_sequence")) or 0,
                            screen_division_code=to_int(booking_key.get("screen_division_code")) or 0,
                        )
                        _, item_count = insert_seat_snapshot(
                            cursor,
                            LOTTE,
                            showtime_id,
                            lotte_showtime_key(schedule),
                            schedule,
                            seats,
                        )
                        result["seat_snapshots_inserted"] += 1
                        result["seat_items_inserted"] += item_count
    return finalize_provider_ingest(cursor, LOTTE, collected_at, movie_tag_keys, result)


def area_codes_from_branches(branches: list[dict[str, Any]]) -> list[str]:
    codes: list[str] = []
    for branch in branches:
        code = safe_text(branch.get("area_code"))
        if code and code not in codes:
            codes.append(code)
    return codes


def choose_megabox_play_dates(args: argparse.Namespace) -> list[str]:
    if args.megabox_play_de:
        return [args.megabox_play_de]
    if not args.all_provider_dates:
        return [today_yyyymmdd()]

    days = max(1, args.megabox_date_days)
    dates = [
        (today_date() + timedelta(days=offset)).strftime("%Y%m%d")
        for offset in range(days)
    ]
    if args.max_provider_dates and args.max_provider_dates > 0:
        dates = dates[: args.max_provider_dates]
    return dates


def megabox_theater_from_schedule(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "provider": MEGABOX,
        "area_code": row.get("area_code"),
        "area_name": row.get("area_name"),
        "branch_no": row.get("branch_no"),
        "branch_name": row.get("branch_name"),
        "raw": row.get("raw") or row,
    }


def ingest_megabox(cursor: Any, args: argparse.Namespace) -> dict[str, Any]:
    collector = MegaboxCollector()
    collected_at = now_db()
    play_dates = choose_megabox_play_dates(args)
    result: dict[str, Any] = provider_ingest_result(MEGABOX, play_dates)

    movie_ids: dict[str, int] = {}
    theater_ids: dict[str, int] = {}
    movie_tag_keys: set[tuple[str, str, str, str]] = set()
    for play_de in play_dates:
        movies = limited(collector.build_movie_records(play_de), args.limit_movies)
        branches = limited(collector.build_area_records(play_de), args.limit_theaters)
        for movie in movies:
            movie_id = upsert_movie(cursor, MEGABOX, movie, collected_at)
            movie_no = safe_text(movie.get("movie_no"))
            representative_movie_no = safe_text(movie.get("representative_movie_no"))
            if movie_no:
                movie_ids[movie_no] = movie_id
            if representative_movie_no:
                movie_ids[representative_movie_no] = movie_id
            result["movie_tags_upserted"] += upsert_movie_tags(
                cursor,
                MEGABOX,
                movie_id,
                movie_no,
                movie,
                movie_tag_keys,
            )
            result["movies_upserted"] += 1
        for branch in branches:
            theater_key = safe_text(branch.get("branch_no"))
            if theater_key in theater_ids:
                continue
            theater_id = upsert_theater(cursor, MEGABOX, branch, collected_at)
            theater_ids[theater_key] = theater_id
            result["theaters_upserted"] += 1

        for movie in movies:
            if args.limit_schedules and result["showtimes_upserted"] >= args.limit_schedules:
                break
            movie_no = safe_text(movie.get("movie_no"))
            if not movie_no:
                continue
            for area_code in area_codes_from_branches(branches):
                if args.limit_schedules and result["showtimes_upserted"] >= args.limit_schedules:
                    break
                schedules = collector.build_schedule_records(movie_no=movie_no, play_de=play_de, area_cd=area_code)
                result["schedule_queries"] += 1
                for schedule in schedules:
                    if args.limit_schedules and result["showtimes_upserted"] >= args.limit_schedules:
                        break
                    theater_key = safe_text(schedule.get("branch_no"))
                    theater_id = theater_ids.get(theater_key)
                    if theater_id is None:
                        theater_id = upsert_theater(cursor, MEGABOX, megabox_theater_from_schedule(schedule), collected_at)
                        theater_ids[theater_key] = theater_id
                        result["theaters_upserted"] += 1
                    movie_id, movie_key, movie_created = resolve_megabox_movie_id(cursor, schedule, movie_ids, collected_at)
                    if movie_id is None:
                        continue
                    if movie_created:
                        result["movies_upserted"] += 1
                    screen_id = upsert_screen(cursor, MEGABOX, schedule, theater_id, collected_at)
                    showtime_id = upsert_showtime(cursor, MEGABOX, schedule, movie_id, theater_id, screen_id, collected_at)
                    result["screens_upserted"] += 1
                    result["showtimes_upserted"] += 1
                    if movie_key:
                        result["movie_tags_upserted"] += upsert_movie_tags(
                            cursor,
                            MEGABOX,
                            movie_id,
                            movie_key,
                            schedule,
                            movie_tag_keys,
                        )

                    if args.include_seats and result["seat_snapshots_inserted"] < args.max_seat_snapshots:
                        seats = collector.build_seat_records(
                            play_schdl_no=safe_text(schedule.get("play_schedule_no")),
                            brch_no=safe_text(schedule.get("branch_no")),
                        )
                        _, item_count = insert_seat_snapshot(
                            cursor,
                            MEGABOX,
                            showtime_id,
                            megabox_showtime_key(schedule),
                            schedule,
                            seats,
                        )
                        result["seat_snapshots_inserted"] += 1
                        result["seat_items_inserted"] += item_count
    return finalize_provider_ingest(cursor, MEGABOX, collected_at, movie_tag_keys, result)


def collect_dry_run(args: argparse.Namespace) -> dict[str, Any]:
    result: dict[str, Any] = {"mode": "dry-run"}
    if args.provider in {"lotte", "all"}:
        lotte = LotteCinemaCollector()
        play_date_rows = lotte.build_play_date_records()
        play_dates = choose_lotte_play_dates(play_date_rows, args)
        result["lotte"] = {
            "movies": len(lotte.build_movie_records()),
            "theaters": len(lotte.build_cinema_records()),
            "provider_play_dates": len(play_date_rows),
            "selected_play_dates": play_dates,
            "selected_play_date_count": len(play_dates),
        }
    if args.provider in {"megabox", "all"}:
        play_dates = choose_megabox_play_dates(args)
        play_de = play_dates[0]
        megabox = MegaboxCollector()
        result["megabox"] = {
            "selected_play_dates": play_dates,
            "selected_play_date_count": len(play_dates),
            "movies": len(megabox.build_movie_records(play_de)),
            "area_branches": len(megabox.build_area_records(play_de)),
        }
    return result


def enrich_metadata_tags(cursor: Any, args: argparse.Namespace) -> dict[str, Any]:
    from scripts.ingest.enrich_movie_tags import enrich_movie_tags

    return enrich_movie_tags(
        cursor,
        Path(args.metadata_overrides),
        current_only=True,
        dry_run=False,
        provider_auto_tags=not args.skip_provider_auto_tags,
        kobis_metadata=not args.skip_kobis_metadata,
        kobis_limit=args.kobis_metadata_limit,
    )


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Collect Lotte/Megabox data and ingest it into TiDB")
    parser.add_argument("--provider", choices=["lotte", "megabox", "all"], default="all")
    parser.add_argument("--all-provider-dates", action="store_true", help="Ingest every future booking date exposed or supported by the provider API.")
    parser.add_argument("--max-provider-dates", type=int, default=0, help="Optional cap for selected provider dates; 0 means no cap for provider-listed dates.")
    parser.add_argument("--megabox-date-days", type=int, default=14, help="Future day range for Megabox when --all-provider-dates is used.")
    parser.add_argument("--lotte-play-date", help="Lotte play date, for example 2026-04-14")
    parser.add_argument("--megabox-play-de", help="Megabox playDe, for example 20260414")
    parser.add_argument("--limit-movies", type=int, default=10)
    parser.add_argument("--limit-theaters", type=int, default=10)
    parser.add_argument("--limit-schedules", type=int, default=5)
    parser.add_argument("--include-seats", action="store_true")
    parser.add_argument("--max-seat-snapshots", type=int, default=1)
    parser.add_argument("--metadata-overrides", default=str(DEFAULT_METADATA_OVERRIDES_PATH))
    parser.add_argument("--skip-metadata-tags", action="store_true", help="Skip post-crawl curated movie_tags enrichment.")
    parser.add_argument("--skip-provider-auto-tags", action="store_true", help="Skip canonical genre normalization from provider metadata during post-crawl enrichment.")
    parser.add_argument("--skip-kobis-metadata", action="store_true", help="Skip optional KOBIS metadata enrichment even when a KOBIS API key is configured.")
    parser.add_argument("--kobis-metadata-limit", type=int, default=60, help="Maximum current/future movies to check through optional KOBIS metadata enrichment.")
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    return run(argv)


def run(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.dry_run:
        print(json.dumps(collect_dry_run(args), ensure_ascii=False, indent=2, default=str))
        return 0

    config = load_tidb_config()
    result: dict[str, Any] = {"mode": "write", "target": "configured_tidb", "providers": []}
    with connect_tidb(config) as conn:
        with conn.cursor() as cursor:
            if args.provider in {"lotte", "all"}:
                result["providers"].append(ingest_lotte(cursor, args))
            if args.provider in {"megabox", "all"}:
                result["providers"].append(ingest_megabox(cursor, args))
            if not args.skip_metadata_tags:
                result["metadata_tag_enrichment"] = enrich_metadata_tags(cursor, args)

    print(json.dumps(result, ensure_ascii=False, indent=2, default=str))
    return 0


if __name__ == "__main__":
    raise SystemExit(run())
