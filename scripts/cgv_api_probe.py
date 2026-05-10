import base64
import hashlib
import hmac
import json
import sys
import time
from pathlib import Path
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from collectors.cgv.api import load_cgv_api_secret


API_BASE = "https://api.cgv.co.kr"
DEFAULT_HEADERS = {
    "Accept": "application/json",
    "Accept-Language": "ko-KR",
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/135.0.0.0 Safari/537.36"
    ),
    "Origin": "https://cgv.co.kr",
    "Referer": "https://cgv.co.kr/cnm/movieBook/movie",
}


def build_signature(pathname: str, body: str, timestamp: str) -> str:
    api_secret = load_cgv_api_secret()
    if not api_secret:
        raise RuntimeError("CGV_API_SECRET 환경변수 또는 루트 .env 값이 필요함")

    payload = f"{timestamp}|{pathname}|{body}"
    digest = hmac.new(
        api_secret.encode("utf-8"),
        payload.encode("utf-8"),
        hashlib.sha256,
    ).digest()
    return base64.b64encode(digest).decode("ascii")


def call_api(path: str, params: dict | None = None, method: str = "GET", body_obj=None):
    if params:
        query = urlencode([(k, v) for k, v in params.items() if v is not None])
        url = f"{API_BASE}{path}?{query}"
    else:
        url = f"{API_BASE}{path}"

    body = ""
    data = None
    headers = DEFAULT_HEADERS.copy()

    if body_obj is not None:
        body = json.dumps(body_obj, ensure_ascii=False, separators=(",", ":"))
        data = body.encode("utf-8")
        headers["Content-Type"] = "application/json"

    pathname = urlparse(url).path
    timestamp = str(int(time.time()))
    headers["X-TIMESTAMP"] = timestamp
    headers["X-SIGNATURE"] = build_signature(pathname, body, timestamp)

    req = Request(url, headers=headers, method=method, data=data)
    with urlopen(req, timeout=30) as resp:
        payload = resp.read().decode("utf-8")
        return json.loads(payload)


def print_section(title: str, payload):
    print(f"\n=== {title} ===")
    print(json.dumps(payload, ensure_ascii=False, indent=2)[:6000])


def main():
    movie_payload = call_api(
        "/cnm/atkt/searchAtktTopPostrList",
        params={"coCd": "A420", "movNm": "", "div": "", "attrCd": ""},
    )
    region_payload = call_api(
        "/cnm/site/searchAllRegionAndSite",
        params={"coCd": "A420"},
    )
    attr_payload = call_api(
        "/cnm/atkt/searchAtktTopPostrAttrList",
        params={"coCd": "A420"},
    )

    sample_movie = next(
        (movie for movie in movie_payload.get("data", []) if movie.get("movNm") == "왕과 사는 남자"),
        movie_payload.get("data", [None])[0],
    )
    sample_site = next(
        (site for site in region_payload.get("data", {}).get("siteInfo", []) if site.get("siteNo") == "0056"),
        region_payload.get("data", {}).get("siteInfo", [None])[0],
    )

    sample_dates = None
    sample_schedule = None
    sample_seat_map = None

    if sample_movie and sample_site:
        sample_dates = call_api(
            "/cnm/atkt/searchSiteScnscYmdListByMov",
            params={
                "coCd": "A420",
                "siteNo": sample_site["siteNo"],
                "movNo": sample_movie["movNo"],
                "div": "",
                "attrCd": "",
            },
        )
        first_date = sample_dates.get("data", [None])[0]
        if first_date:
            sample_schedule = call_api(
                "/cnm/atkt/searchSchByMov",
                params={
                    "coCd": "A420",
                    "siteNo": sample_site["siteNo"],
                    "scnYmd": first_date["scnYmd"],
                    "scnsNo": "",
                    "scnSseq": "",
                    "movNo": sample_movie["movNo"],
                    "prodNo": "",
                    "rtctlScopCd": "08",
                    "salsTznCd": "",
                    "tcscnsGradCd": "",
                    "sascnsGradCd": "",
                    "custNo": "",
                },
            )

        first_schedule = sample_schedule.get("data", [None])[0] if sample_schedule else None
        if first_schedule:
            sample_seat_map = call_api(
                "/cnm/atkt/searchIfSeatData",
                params={
                    "coCd": first_schedule["coCd"],
                    "siteNo": first_schedule["siteNo"],
                    "scnYmd": first_schedule["scnYmd"],
                    "scnsNo": first_schedule["scnsNo"],
                    "scnSseq": first_schedule["scnSseq"],
                    "seatAreaNo": "",
                    "cusgdCd": "",
                    "custNo": "",
                },
            )

    print_section("movies", movie_payload)
    print_section("regions", region_payload)
    print_section("attributes", attr_payload)
    print_section("sample_movie", sample_movie)
    print_section("sample_site", sample_site)
    print_section("sample_dates", sample_dates)
    print_section("sample_schedule", sample_schedule)
    print_section("sample_seat_map", sample_seat_map)


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
