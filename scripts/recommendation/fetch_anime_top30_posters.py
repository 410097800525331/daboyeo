from __future__ import annotations

import argparse
import hashlib
import html
import io
import json
import re
import shutil
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urljoin
from urllib.request import Request, urlopen

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
KOBIS_BASE = "https://www.kobis.or.kr"
KOBIS_ALL_TIME_URL = (
    KOBIS_BASE
    + "/kobis/business/stat/offc/findFormerBoxOfficeList.do?loadEnd=0&searchType=search"
)
KOBIS_MOBILE_DETAIL_TEMPLATE = (
    KOBIS_BASE + "/kobis/mobile/mast/mvie/searchMovieDtl.do?movieCd={movieCd}"
)

FRONTEND_POSTER_DIR = ROOT / "frontend" / "src" / "assets" / "R2" / "posters" / "anime"
STATIC_POSTER_DIR = (
    ROOT
    / "backend"
    / "src"
    / "main"
    / "resources"
    / "static"
    / "src"
    / "assets"
    / "R2"
    / "posters"
    / "anime"
)
MANIFEST_PATH = (
    ROOT
    / "backend"
    / "src"
    / "main"
    / "resources"
    / "recommendation"
    / "korea-animation-boxoffice-top30-posters.json"
)
TAGS_PATH = (
    ROOT
    / "backend"
    / "src"
    / "main"
    / "resources"
    / "recommendation"
    / "korea-animation-boxoffice-top30-poster-tags.json"
)

REQUEST_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) daboyeo-poster-seed/1.0"
}

LABEL_RELEASE = "\uac1c\ubd09\uc77c\uc790"
LABEL_COUNTRY = "\uc81c\uc791\uad6d\uac00"
LABEL_GENRE = "\uc7a5\ub974"
LABEL_SCREENING_TYPE = "\uc0c1\uc601\ud0c0\uc785"
LABEL_RUNTIME = "\uc0c1\uc601\uc2dc\uac04"
LABEL_AGE = "\uad00\ub78c\ub4f1\uae09"
LABEL_CAST = "\uac10\ub3c5/\ucd9c\uc5f0"
GENRE_ANIMATION = "\uc560\ub2c8\uba54\uc774\uc158"


@dataclass(frozen=True)
class BoxOfficeMovie:
    kobis_all_time_rank: int
    movie_cd: str
    title_ko: str
    release_date: str
    gross_krw: int
    admissions: int
    screens: int


def fetch_text(url: str, timeout: float = 20.0, attempts: int = 3) -> str:
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            with urlopen(Request(url, headers=REQUEST_HEADERS), timeout=timeout) as response:
                return response.read().decode("utf-8", "replace")
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            if attempt < attempts:
                time.sleep(0.5 * attempt)
    raise RuntimeError(f"fetch failed after {attempts} attempts: {url}") from last_error


def fetch_bytes(url: str, timeout: float = 30.0, attempts: int = 3) -> bytes:
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            with urlopen(Request(url, headers=REQUEST_HEADERS), timeout=timeout) as response:
                return response.read()
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            if attempt < attempts:
                time.sleep(0.5 * attempt)
    raise RuntimeError(f"download failed after {attempts} attempts: {url}") from last_error


def strip_html(value: str) -> str:
    text = re.sub(r"<[^>]+>", " ", value)
    return html.unescape(re.sub(r"\s+", " ", text)).strip()


def parse_int(value: str) -> int:
    return int(re.sub(r"[^0-9]", "", value) or 0)


def cell(row: str, cell_id: str) -> str:
    match = re.search(rf'id="{re.escape(cell_id)}"[^>]*>(.*?)</td>', row, re.S)
    return strip_html(match.group(1)) if match else ""


def parse_all_time_rows(html_text: str) -> list[BoxOfficeMovie]:
    movies: list[BoxOfficeMovie] = []
    seen_codes: set[str] = set()
    for row in re.findall(r"<tr[^>]*>(.*?)</tr>", html_text, re.S):
        code_match = re.search(r"mstView\('movie','([^']+)'\)", row)
        title_match = re.search(r'title="([^"]+)"', row)
        if not code_match or not title_match:
            continue
        movie_cd = code_match.group(1)
        if movie_cd in seen_codes:
            continue
        seen_codes.add(movie_cd)
        movies.append(
            BoxOfficeMovie(
                kobis_all_time_rank=parse_int(cell(row, "td_rank")),
                movie_cd=movie_cd,
                title_ko=html.unescape(title_match.group(1)).strip(),
                release_date=cell(row, "td_openDt"),
                gross_krw=parse_int(cell(row, "td_totSalesAcc")),
                admissions=parse_int(cell(row, "td_totAudiAcc")),
                screens=parse_int(cell(row, "td_totScrnCnt")),
            )
        )
    return movies


def between(text: str, start: str, end: str) -> str:
    if start not in text:
        return ""
    tail = text.split(start, 1)[1]
    if end in tail:
        tail = tail.split(end, 1)[0]
    return tail.strip()


def poster_urls(detail_html: str) -> list[str]:
    urls: list[str] = []
    seen: set[str] = set()
    for match in re.finditer(
        r'(?:href|src)="((?:https?://www\.kobis\.or\.kr)?/'
        r'(?:common/mast/movie|upload/up_img)/[^"]+?\.(?:jpg|jpeg|png))"',
        detail_html,
        re.I,
    ):
        url = urljoin(KOBIS_BASE, html.unescape(match.group(1)))
        if "/thumb_" in url or url in seen:
            continue
        seen.add(url)
        urls.append(url)
    return urls


def first_poster_url(detail_html: str) -> str:
    urls = poster_urls(detail_html)
    return urls[0] if urls else ""


def detail_for(movie: BoxOfficeMovie) -> dict[str, Any]:
    detail_url = KOBIS_MOBILE_DETAIL_TEMPLATE.format(movieCd=movie.movie_cd)
    detail_html = fetch_text(detail_url)
    clean = strip_html(detail_html)
    return {
        "movie": movie,
        "detailUrl": detail_url,
        "country": between(clean, LABEL_COUNTRY, LABEL_GENRE),
        "genre": between(clean, LABEL_GENRE, LABEL_SCREENING_TYPE),
        "runtime": between(clean, LABEL_RUNTIME, LABEL_AGE),
        "ageRating": between(clean, LABEL_AGE, LABEL_CAST),
        "posterSourceUrl": first_poster_url(detail_html),
    }


def collect_animation_movies(target_count: int, scan_limit: int, batch_size: int, workers: int) -> tuple[list[dict[str, Any]], int, int]:
    all_time_html = fetch_text(KOBIS_ALL_TIME_URL)
    all_rows = parse_all_time_rows(all_time_html)
    selected: list[dict[str, Any]] = []
    checked = 0

    for start in range(0, min(len(all_rows), scan_limit), batch_size):
        batch = all_rows[start : start + batch_size]
        details: list[dict[str, Any]] = []
        with ThreadPoolExecutor(max_workers=workers) as executor:
            futures = {executor.submit(detail_for, movie): movie for movie in batch}
            for future in as_completed(futures):
                movie = futures[future]
                try:
                    details.append(future.result())
                except Exception as exc:  # noqa: BLE001
                    print(
                        f"warn: detail fetch failed for {movie.movie_cd} {movie.title_ko}: {exc}",
                        file=sys.stderr,
                    )
        checked += len(batch)
        details.sort(key=lambda item: item["movie"].kobis_all_time_rank)
        for item in details:
            if GENRE_ANIMATION in item["genre"]:
                selected.append(item)
                movie = item["movie"]
                print(
                    f"rank {len(selected):02d}: KOBIS #{movie.kobis_all_time_rank} "
                    f"{movie.movie_cd} {movie.title_ko} ({movie.admissions:,})",
                    file=sys.stderr,
                )
                if len(selected) >= target_count:
                    return selected[:target_count], checked, len(all_rows)
        time.sleep(0.1)

    return selected[:target_count], checked, len(all_rows)


def poster_filename(animation_rank: int, movie_cd: str) -> str:
    return f"{animation_rank:02d}-{movie_cd}.webp"


def image_size(raw: bytes) -> tuple[int, int]:
    with Image.open(io.BytesIO(raw)) as image:
        return image.size


def candidate_score(size: tuple[int, int]) -> tuple[int, int]:
    width, height = size
    portrait = 1 if height >= width else 0
    return portrait, min(width * height, 2_500_000)


def best_poster_source(movie_cd: str, fallback_url: str) -> tuple[str, bytes, tuple[int, int]]:
    candidates = [fallback_url] if fallback_url else []
    business_url = f"{KOBIS_BASE}/kobis/business/mast/mvie/searchMovieDtl.do?code={movie_cd}"
    try:
        candidates.extend(poster_urls(fetch_text(business_url)))
    except Exception as exc:  # noqa: BLE001
        print(f"warn: business poster lookup failed for {movie_cd}: {exc}", file=sys.stderr)

    seen: set[str] = set()
    best: tuple[str, bytes, tuple[int, int]] | None = None
    for url in candidates:
        if not url or url in seen:
            continue
        seen.add(url)
        try:
            raw = fetch_bytes(url)
            size = image_size(raw)
        except Exception as exc:  # noqa: BLE001
            print(f"warn: poster candidate failed for {movie_cd} {url}: {exc}", file=sys.stderr)
            continue
        if best is None or candidate_score(size) > candidate_score(best[2]):
            best = (url, raw, size)
    if best is None:
        raise RuntimeError(f"poster source missing for {movie_cd}")
    return best


def save_webp(raw: bytes, destination: Path) -> tuple[int, int, str]:
    with Image.open(io.BytesIO(raw)) as image:
        converted = image.convert("RGB")
        destination.parent.mkdir(parents=True, exist_ok=True)
        converted.save(destination, "WEBP", quality=88, method=6)
        width, height = converted.size
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    return width, height, digest


def age_tag(raw_age: str) -> str:
    if "\uc804\uccb4" in raw_age:
        return "all"
    if "12" in raw_age:
        return "12"
    if "15" in raw_age:
        return "15"
    if "18" in raw_age or "\uccad\uc18c\ub144" in raw_age:
        return "18"
    return ""


def english_genres(raw_genre: str) -> list[str]:
    mapping = [
        ("\uc560\ub2c8\uba54\uc774\uc158", "animation"),
        ("\uc5b4\ub4dc\ubca4\ucc98", "adventure"),
        ("\ubaa8\ud5d8", "adventure"),
        ("\uc561\uc158", "action"),
        ("\ucf54\ubbf8\ub514", "comedy"),
        ("\ud310\ud0c0\uc9c0", "fantasy"),
        ("\uac00\uc871", "family"),
        ("\ubba4\uc9c0\uceec", "musical"),
        ("\ub4dc\ub77c\ub9c8", "drama"),
        ("SF", "sf"),
        ("\uacf5\ud3ec", "horror"),
        ("\uc2a4\ub9b4\ub7ec", "thriller"),
        ("\uba5c\ub85c", "romance"),
    ]
    genres = [tag for needle, tag in mapping if needle in raw_genre]
    return list(dict.fromkeys(genres or ["animation"]))


def conservative_tags(raw_genre: str, raw_age: str) -> dict[str, Any]:
    genres = english_genres(raw_genre)
    moods = ["visual"]
    if "comedy" in genres:
        moods.append("funny")
    if "family" in genres or age_tag(raw_age) == "all":
        moods.append("warm")
    if "action" in genres or "adventure" in genres:
        moods.append("exciting")
    audiences = ["family", "child"] if age_tag(raw_age) == "all" else ["friends", "family"]
    return {
        "genres": genres,
        "moods": list(dict.fromkeys(moods)),
        "pace": "medium",
        "audiences": audiences,
        "avoid": [],
        "ageRating": age_tag(raw_age),
    }


def build_outputs(selected: list[dict[str, Any]], checked: int, available: int) -> tuple[dict[str, Any], dict[str, Any]]:
    FRONTEND_POSTER_DIR.mkdir(parents=True, exist_ok=True)
    STATIC_POSTER_DIR.mkdir(parents=True, exist_ok=True)

    movies: list[dict[str, Any]] = []
    tags: dict[str, Any] = {}
    fetched_at = datetime.now(timezone.utc).isoformat()

    for animation_rank, item in enumerate(selected, start=1):
        movie: BoxOfficeMovie = item["movie"]
        if not item["posterSourceUrl"]:
            raise RuntimeError(f"poster source missing for {movie.movie_cd} {movie.title_ko}")
        filename = poster_filename(animation_rank, movie.movie_cd)
        frontend_path = FRONTEND_POSTER_DIR / filename
        static_path = STATIC_POSTER_DIR / filename
        poster_url, poster_raw, _ = best_poster_source(movie.movie_cd, item["posterSourceUrl"])
        width, height, digest = save_webp(poster_raw, frontend_path)
        shutil.copy2(frontend_path, static_path)
        movie_entry = {
            "rank": animation_rank,
            "kobisAllTimeRank": movie.kobis_all_time_rank,
            "movieCd": movie.movie_cd,
            "titleKo": movie.title_ko,
            "releaseDate": movie.release_date,
            "grossKrw": movie.gross_krw,
            "admissions": movie.admissions,
            "screens": movie.screens,
            "country": item["country"],
            "genre": item["genre"],
            "runtime": item["runtime"],
            "ageRating": item["ageRating"],
            "posterPath": f"/src/assets/R2/posters/anime/{filename}",
            "posterFile": str(frontend_path.relative_to(ROOT)).replace("\\", "/"),
            "staticPosterFile": str(static_path.relative_to(ROOT)).replace("\\", "/"),
            "posterSourceUrl": poster_url,
            "posterWidth": width,
            "posterHeight": height,
            "posterSha256": digest,
        }
        movies.append(movie_entry)
        tags[movie.movie_cd] = conservative_tags(item["genre"], item["ageRating"])

    manifest = {
        "source": {
            "rankingName": "KOBIS all-time box office, animation genre top 30",
            "rankingUrl": KOBIS_ALL_TIME_URL,
            "posterDetailUrlTemplate": KOBIS_MOBILE_DETAIL_TEMPLATE,
            "basis": (
                "Korea all-time admissions, filtered by KOBIS movie detail genre "
                "containing animation, then re-ranked 1-30 for this anime poster seed."
            ),
            "fetchedAt": fetched_at,
            "kobisRowsAvailable": available,
            "kobisRowsScanned": checked,
            "note": "KOBIS cumulative values can change due to rereleases and ongoing releases.",
        },
        "movies": movies,
    }
    tag_manifest = {
        "source": {
            "checkedAt": fetched_at,
            "genreSource": "KOBIS mobile movie detail genre field",
            "note": (
                "Tags are conservative local recommendation hints derived from KOBIS genre "
                "and age fields; poster ranking remains the KOBIS admissions order."
            ),
        },
        "movies": tags,
    }
    return manifest, tag_manifest


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def verify_outputs() -> None:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    movies = manifest.get("movies", [])
    if len(movies) != 30:
        raise RuntimeError(f"expected 30 manifest movies, found {len(movies)}")
    seen_ranks: set[int] = set()
    seen_codes: set[str] = set()
    for movie in movies:
        rank = int(movie["rank"])
        code = movie["movieCd"]
        if rank in seen_ranks:
            raise RuntimeError(f"duplicate rank {rank}")
        if code in seen_codes:
            raise RuntimeError(f"duplicate movieCd {code}")
        seen_ranks.add(rank)
        seen_codes.add(code)
        frontend_path = ROOT / movie["posterFile"]
        static_path = ROOT / movie["staticPosterFile"]
        if not frontend_path.is_file():
            raise RuntimeError(f"missing frontend poster {frontend_path}")
        if not static_path.is_file():
            raise RuntimeError(f"missing static poster {static_path}")
        if hashlib.sha256(frontend_path.read_bytes()).hexdigest() != movie["posterSha256"]:
            raise RuntimeError(f"sha mismatch for {frontend_path}")
        if frontend_path.read_bytes() != static_path.read_bytes():
            raise RuntimeError(f"static mirror mismatch for {frontend_path.name}")
        with Image.open(frontend_path) as image:
            if image.format != "WEBP":
                raise RuntimeError(f"not webp: {frontend_path}")
            if image.size != (int(movie["posterWidth"]), int(movie["posterHeight"])):
                raise RuntimeError(f"dimension mismatch: {frontend_path}")
            if image.width < 400 or image.height < 600:
                raise RuntimeError(f"poster resolution below floor: {frontend_path} {image.size}")
    tags = json.loads(TAGS_PATH.read_text(encoding="utf-8"))
    tag_codes = set(tags.get("movies", {}).keys())
    if seen_codes != tag_codes:
        raise RuntimeError("tag movieCd set does not match poster manifest")
    print(f"verified {len(movies)} anime poster entries")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target-count", type=int, default=30)
    parser.add_argument("--scan-limit", type=int, default=900)
    parser.add_argument("--batch-size", type=int, default=80)
    parser.add_argument("--workers", type=int, default=10)
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument("--ranking-only", action="store_true")
    args = parser.parse_args()

    if args.verify_only:
        verify_outputs()
        return 0

    selected, checked, available = collect_animation_movies(
        args.target_count,
        args.scan_limit,
        args.batch_size,
        args.workers,
    )
    if len(selected) != args.target_count:
        raise RuntimeError(f"expected {args.target_count} animation movies, found {len(selected)}")
    if args.ranking_only:
        print(
            json.dumps(
                [
                    {
                        "rank": index,
                        "kobisAllTimeRank": item["movie"].kobis_all_time_rank,
                        "movieCd": item["movie"].movie_cd,
                        "titleKo": item["movie"].title_ko,
                        "admissions": item["movie"].admissions,
                        "genre": item["genre"],
                    }
                    for index, item in enumerate(selected, start=1)
                ],
                ensure_ascii=False,
                indent=2,
            )
        )
        return 0

    manifest, tag_manifest = build_outputs(selected, checked, available)
    write_json(MANIFEST_PATH, manifest)
    write_json(TAGS_PATH, tag_manifest)
    verify_outputs()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
