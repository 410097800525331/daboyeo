from __future__ import annotations

import json
from datetime import datetime
from decimal import Decimal, InvalidOperation
from typing import Any


def json_for_db(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), default=str)


def to_int(value: Any) -> int | None:
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def to_decimal(value: Any) -> Decimal | None:
    if value is None or value == "":
        return None
    try:
        return Decimal(str(value).replace(",", ""))
    except (InvalidOperation, ValueError):
        return None


def parse_yyyymmdd(value: Any) -> str | None:
    text = str(value or "").strip()
    if len(text) == 8 and text.isdigit():
        return f"{text[:4]}-{text[4:6]}-{text[6:8]}"
    return text or None


def normalize_time(value: Any) -> str | None:
    text = str(value or "").strip()
    if not text:
        return None
    if ":" in text:
        parts = text.split(":")
        if len(parts) >= 2:
            return f"{parts[0].zfill(2)}:{parts[1].zfill(2)}:00"
    digits = "".join(ch for ch in text if ch.isdigit())
    if len(digits) == 4:
        return f"{digits[:2]}:{digits[2:]}:00"
    return None


def combine_datetime(date_value: Any, time_value: Any) -> str | None:
    date_text = parse_yyyymmdd(date_value)
    time_text = normalize_time(time_value)
    if not date_text or not time_text:
        return None
    return f"{date_text} {time_text}"


def cgv_showtime_key(row: dict[str, Any]) -> str:
    booking_key = row.get("booking_key") or {}
    return "|".join(
        str(booking_key.get(key) or row.get(fallback) or "")
        for key, fallback in [
            ("site_no", "site_no"),
            ("scn_ymd", "screening_date"),
            ("scns_no", "screen_no"),
            ("scn_sseq", "screen_sequence"),
        ]
    )


def lotte_showtime_key(row: dict[str, Any]) -> str:
    booking_key = row.get("booking_key") or {}
    return "|".join(
        str(booking_key.get(key) or row.get(fallback) or "")
        for key, fallback in [
            ("cinema_id", "cinema_id"),
            ("screen_id", "screen_id"),
            ("play_date", "play_date"),
            ("play_sequence", "play_sequence"),
            ("screen_division_code", "screen_division_code"),
        ]
    )


def megabox_showtime_key(row: dict[str, Any]) -> str:
    return "|".join(str(row.get(key) or "") for key in ["play_schedule_no", "branch_no"])


def normalize_seat_status(provider: str, status_code: Any, raw: dict[str, Any] | None = None) -> str:
    code = str(status_code or "").upper()
    if provider == "LOTTE_CINEMA":
        if code in {"50", "OK", "AVAILABLE"}:
            return "available"
        if code in {"0", "SALE_END", "BOOKED", "SOLD"}:
            return "sold"
        return "unavailable" if "BLOCK" in code or "DISABLE" in code else "unknown"
    if provider == "MEGABOX":
        if code in {"GERN_SELL", "A"}:
            return "available"
        if code in {"SELL_END", "BOKD", "SOLD"}:
            return "sold"
        return "special" if "STOP" in code or "DISABLED" in code else "unavailable"
    if provider == "CGV":
        if code == "00":
            return "available"
        if code == "01":
            return "sold"
        return "special"
    return "unknown"


def seat_occupancy_rate(total: Any, remaining: Any) -> Decimal | None:
    total_int = to_int(total)
    remaining_int = to_int(remaining)
    if not total_int or remaining_int is None:
        return None
    sold = max(total_int - remaining_int, 0)
    return Decimal(sold * 100) / Decimal(total_int)


def utc_timestamp_compact(now: datetime | None = None) -> str:
    current = now or datetime.utcnow()
    return current.strftime("%Y%m%dT%H%M%SZ")
