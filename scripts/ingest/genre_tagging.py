from __future__ import annotations

import html
import re
import unicodedata
from collections.abc import Iterable
from typing import Any


CANONICAL_GENRES = {
    "action",
    "adventure",
    "animation",
    "comedy",
    "crime",
    "drama",
    "family",
    "fantasy",
    "history",
    "horror",
    "music",
    "romance",
    "sf",
    "thriller",
}

GENERIC_GENRE_VALUES = {
    "general",
    "general_content",
    "general-content",
    "mega_only",
    "mega-only",
    "special_content",
    "special-content",
    "content_c",
    "content-c",
    "gv",
    "일반콘텐트",
    "스페셜콘텐트",
    "디즈니시네마",
    "굿즈패키지",
    "콘텐트c",
}

GENRE_ALIASES: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("action", ("action", "액션", "무협")),
    ("adventure", ("adventure", "어드벤처", "모험")),
    ("animation", ("animation", "anime", "애니메이션", "애니")),
    ("comedy", ("comedy", "코미디")),
    ("crime", ("crime", "범죄")),
    ("drama", ("drama", "드라마")),
    ("family", ("family", "가족", "어린이")),
    ("fantasy", ("fantasy", "판타지")),
    ("history", ("history", "historical", "역사", "사극", "시대극")),
    ("horror", ("horror", "공포", "호러")),
    ("music", ("music", "musical", "뮤직", "음악", "뮤지컬", "공연실황", "콘서트", "라이브", "오페라", "클래식")),
    ("romance", ("romance", "로맨스", "멜로")),
    ("sf", ("sf", "sci-fi", "sci_fi", "science fiction", "science-fiction", "science_fiction", "에스에프")),
    ("thriller", ("thriller", "스릴러")),
)

SPLIT_RE = re.compile(r"[,/|+·ㆍ•;]+")
BRACKET_PREFIX_RE = re.compile(r"^\s*(?:\[[^\]]{1,30}\]|\([^)]{1,30}\)|【[^】]{1,30}】)\s*")
HTML_ENTITY_RE = re.compile(r"&(?:#\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);")


def safe_text(value: Any) -> str:
    return "" if value is None else str(value).strip()


def normalize_space(value: str) -> str:
    return " ".join(value.split())


def clean_genre_value(value: Any) -> str:
    text = html.unescape(safe_text(value))
    text = normalize_space(text)
    text = text.strip(" ,/|+()[]{}<>")
    return unicodedata.normalize("NFKC", text).lower()


def compact_value(value: str) -> str:
    return re.sub(r"[\s_\-]+", "", value.lower())


def split_genre_values(value: Any) -> list[str]:
    text = clean_genre_value(value)
    if not text:
        return []
    parts: list[str] = []
    for chunk in SPLIT_RE.split(text):
        cleaned = clean_genre_value(chunk)
        if cleaned:
            parts.append(cleaned)
    return parts


def is_generic_genre_value(value: Any) -> bool:
    cleaned = clean_genre_value(value).replace(" ", "_")
    return cleaned in GENERIC_GENRE_VALUES or compact_value(cleaned) in {
        compact_value(item) for item in GENERIC_GENRE_VALUES
    }


def canonical_genres_from_values(values: Iterable[Any]) -> list[str]:
    genres: list[str] = []
    seen: set[str] = set()
    for value in values:
        for token in split_genre_values(value):
            if is_generic_genre_value(token):
                continue
            token_compact = compact_value(token)
            for canonical, aliases in GENRE_ALIASES:
                matched = False
                for alias in aliases:
                    alias_clean = clean_genre_value(alias)
                    alias_compact = compact_value(alias_clean)
                    if alias_clean.isascii():
                        matched = token == alias_clean or token_compact == alias_compact
                    else:
                        matched = alias_clean in token
                    if matched:
                        break
                if matched and canonical not in seen:
                    seen.add(canonical)
                    genres.append(canonical)
    return genres


def raw_json_dict(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    return {}


def canonical_genres_from_provider_row(row: dict[str, Any]) -> list[str]:
    raw = raw_json_dict(row.get("raw"))
    values = [
        row.get("genre_name"),
        row.get("genre"),
        row.get("genre_alt"),
        row.get("rep_genre"),
        raw.get("MovieGenreNameKR"),
        raw.get("genreAlt"),
        raw.get("repGenreNm"),
        raw.get("cttsTyDivCdNm"),
        raw.get("cttsTyDivCdEngNm"),
        raw.get("movieCttsNm"),
        raw.get("screenType"),
        raw.get("playKindNm"),
        raw.get("theabExpoNm"),
    ]
    return canonical_genres_from_values(values)


def normalize_title_for_match(value: Any) -> str:
    text = html.unescape(safe_text(value))
    text = HTML_ENTITY_RE.sub(" ", text)
    previous = None
    while text != previous:
        previous = text
        text = BRACKET_PREFIX_RE.sub("", text)
    text = re.sub(r"\b(?:2d|3d|imax|4dx|screenx|dolby|atmos|mx4d)\b", " ", text, flags=re.IGNORECASE)
    text = re.sub(r"[\[\]{}()【】<>:：·ㆍ•,_\-]", " ", text)
    text = normalize_space(unicodedata.normalize("NFKC", text)).lower()
    return text
