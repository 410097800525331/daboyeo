from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from collectors.common.normalize import to_int
from collectors.common.repository import ensure_connection
from collectors.common.tidb import connect_tidb, load_tidb_config
from collectors.lotte import LotteCinemaCollector
from collectors.megabox import MegaboxCollector
from scripts.ingest.collect_all_to_tidb import (
    LOTTE,
    MEGABOX,
    lotte_price_rows_from_summary,
    megabox_price_rows_from_summary,
    now_db,
    safe_text,
    upsert_showtime_price_rows,
)


PROVIDER_CODES = {
    "lotte": LOTTE,
    "megabox": MEGABOX,
}
SHOWTIME_COLUMNS = [
    "id",
    "provider_code",
    "external_showtime_key",
    "external_theater_id",
    "external_screen_id",
    "movie_title",
    "theater_name",
    "screen_name",
    "screen_type",
    "format_name",
    "show_date",
    "starts_at",
    "booking_key_json",
    "raw_json",
]


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


def append_sample(items: list[dict[str, Any]], item: dict[str, Any], limit: int = 12) -> None:
    if len(items) < limit:
        items.append(item)


def fetch_candidates(
    cursor: Any,
    *,
    provider: str,
    limit: int,
    include_priced: bool,
) -> list[dict[str, Any]]:
    where = [
        "starts_at >= CURRENT_TIMESTAMP",
        "provider_code IN (%s, %s)",
        "booking_key_json IS NOT NULL",
    ]
    params: list[Any] = [LOTTE, MEGABOX]
    if provider != "all":
        where.append("provider_code = %s")
        params.append(PROVIDER_CODES[provider])
    if not include_priced:
        where.append("min_price_amount IS NULL")

    limit_clause = ""
    if limit > 0:
        limit_clause = "LIMIT %s"
        params.append(limit)

    ensure_connection(cursor)
    cursor.execute(
        f"""
        SELECT {", ".join(SHOWTIME_COLUMNS)}
        FROM showtimes
        WHERE {" AND ".join(where)}
        ORDER BY starts_at ASC, provider_code ASC, id ASC
        {limit_clause}
        """,
        params,
    )
    return [dict(zip(SHOWTIME_COLUMNS, row, strict=True)) for row in cursor.fetchall()]


def schedule_from_candidate(candidate: dict[str, Any]) -> dict[str, Any]:
    booking_key = parse_json_dict(candidate.get("booking_key_json"))
    raw = parse_json_dict(candidate.get("raw_json"))
    provider = candidate["provider_code"]
    if provider == LOTTE:
        return {
            "cinema_id": booking_key.get("cinema_id") or candidate.get("external_theater_id"),
            "screen_id": booking_key.get("screen_id") or candidate.get("external_screen_id"),
            "play_date": booking_key.get("play_date") or str(candidate.get("show_date") or ""),
            "play_sequence": booking_key.get("play_sequence"),
            "screen_division_code": booking_key.get("screen_division_code"),
            "screen_division_name": candidate.get("screen_type"),
            "screen_type": candidate.get("screen_type"),
            "film_name": candidate.get("format_name"),
            "screen_name": candidate.get("screen_name"),
            "booking_key": booking_key,
            "raw": raw,
        }

    play_schedule_no = safe_text(booking_key.get("play_schedule_no"))
    branch_no = safe_text(booking_key.get("branch_no") or candidate.get("external_theater_id"))
    if not play_schedule_no:
        key_parts = safe_text(candidate.get("external_showtime_key")).split("|")
        if key_parts:
            play_schedule_no = key_parts[0]
        if len(key_parts) > 1 and not branch_no:
            branch_no = key_parts[1]
    return {
        "play_schedule_no": play_schedule_no,
        "branch_no": branch_no,
        "screen_type": candidate.get("screen_type"),
        "screen_name": candidate.get("screen_name"),
        "raw": raw,
    }


def price_rows_for_candidate(
    candidate: dict[str, Any],
    collectors: dict[str, Any],
) -> list[dict[str, Any]]:
    schedule = schedule_from_candidate(candidate)
    provider = candidate["provider_code"]
    if provider == LOTTE:
        booking_key = schedule.get("booking_key") or {}
        cinema_id = to_int(booking_key.get("cinema_id") or schedule.get("cinema_id"))
        screen_id = to_int(booking_key.get("screen_id") or schedule.get("screen_id"))
        play_sequence = to_int(booking_key.get("play_sequence") or schedule.get("play_sequence"))
        screen_division_code = to_int(
            booking_key.get("screen_division_code") or schedule.get("screen_division_code")
        )
        play_date = safe_text(booking_key.get("play_date") or schedule.get("play_date"))
        if cinema_id is None or screen_id is None or play_sequence is None or screen_division_code is None:
            raise ValueError("Lotte booking key is incomplete for price fetch")
        seat_summary = collectors[LOTTE].summarize_seat_map(
            cinema_id=cinema_id,
            screen_id=screen_id,
            play_date=play_date,
            play_sequence=play_sequence,
            screen_division_code=screen_division_code,
        )
        return lotte_price_rows_from_summary(seat_summary, schedule)

    if provider == MEGABOX:
        play_schedule_no = safe_text(schedule.get("play_schedule_no"))
        branch_no = safe_text(schedule.get("branch_no"))
        if not play_schedule_no or not branch_no:
            raise ValueError("Megabox booking key is incomplete for price fetch")
        seat_summary = collectors[MEGABOX].summarize_seat_map(
            play_schdl_no=play_schedule_no,
            brch_no=branch_no,
        )
        return megabox_price_rows_from_summary(seat_summary, schedule)

    raise ValueError(f"unsupported provider: {provider}")


def enrich_showtime_prices(
    cursor: Any,
    *,
    provider: str = "all",
    limit: int = 100,
    include_priced: bool = False,
    dry_run: bool = False,
) -> dict[str, Any]:
    candidates = fetch_candidates(
        cursor,
        provider=provider,
        limit=limit,
        include_priced=include_priced,
    )
    result: dict[str, Any] = {
        "mode": "dry-run" if dry_run else "write",
        "provider": provider,
        "limit": limit,
        "include_priced": include_priced,
        "candidate_count": len(candidates),
        "showtimes_checked": 0,
        "showtimes_priced": 0,
        "price_rows_planned": 0,
        "price_rows_upserted": 0,
        "fetch_errors": 0,
        "error_samples": [],
        "priced_samples": [],
    }
    collectors = {
        LOTTE: LotteCinemaCollector(),
        MEGABOX: MegaboxCollector(),
    }
    collected_at = now_db()
    for candidate in candidates:
        external_key = safe_text(candidate.get("external_showtime_key"))
        result["showtimes_checked"] += 1
        try:
            price_rows = price_rows_for_candidate(candidate, collectors)
        except Exception as exc:  # noqa: BLE001 - keep bounded batch enrichment alive.
            result["fetch_errors"] += 1
            if len(result["error_samples"]) < 8:
                result["error_samples"].append(
                    f"{candidate.get('provider_code')} {external_key}: {exc.__class__.__name__}: {safe_text(exc)}"[
                        :240
                    ]
                )
            continue
        result["price_rows_planned"] += len(price_rows)
        if dry_run:
            min_price = min(
                [int(row["total_price_amount"]) for row in price_rows if row.get("total_price_amount") is not None],
                default=None,
            )
            if min_price is not None:
                result["showtimes_priced"] += 1
            upserted = 0
        else:
            upserted, min_price = upsert_showtime_price_rows(
                cursor,
                candidate["provider_code"],
                int(candidate["id"]),
                external_key,
                price_rows,
                collected_at,
            )
            result["price_rows_upserted"] += upserted
            if min_price is not None:
                result["showtimes_priced"] += 1
        if min_price is not None:
            append_sample(
                result["priced_samples"],
                {
                    "providerCode": candidate["provider_code"],
                    "externalShowtimeKey": external_key,
                    "movieTitle": candidate.get("movie_title"),
                    "theaterName": candidate.get("theater_name"),
                    "startsAt": candidate.get("starts_at"),
                    "priceRows": len(price_rows) if dry_run else upserted,
                    "minPriceAmount": min_price,
                },
            )
    return result


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fetch bounded provider price options for current/future showtimes missing min_price_amount."
    )
    parser.add_argument("--provider", choices=["all", "lotte", "megabox"], default="all")
    parser.add_argument("--limit", type=int, default=100, help="Maximum showtimes to check; 0 means no SQL limit.")
    parser.add_argument("--include-priced", action="store_true", help="Also refetch showtimes that already have min_price_amount.")
    parser.add_argument("--dry-run", action="store_true", help="Fetch provider prices and report planned writes without mutating DB.")
    return parser.parse_args(argv)


def run(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    config = load_tidb_config()
    with connect_tidb(config) as conn:
        with conn.cursor() as cursor:
            result = enrich_showtime_prices(
                cursor,
                provider=args.provider,
                limit=args.limit,
                include_priced=args.include_priced,
                dry_run=args.dry_run,
            )
    print(json.dumps(result, ensure_ascii=False, indent=2, default=str))
    return 0


if __name__ == "__main__":
    raise SystemExit(run())
