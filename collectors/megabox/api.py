from __future__ import annotations

import http.cookiejar
import json
import time
from typing import Any
from urllib.parse import urlencode
from urllib.request import HTTPCookieProcessor, Request, build_opener


MEGABOX_BASE_URL = "https://www.megabox.co.kr"
MASTER_LIST_URL = (
    "https://www.megabox.co.kr/on/oh/ohb/PlayTime/selectPlayTimeMasterList.do"
)
SCHEDULE_URL = "https://www.megabox.co.kr/on/oh/ohc/Brch/schedulePage.do"
SEAT_URL = "https://www.megabox.co.kr/on/oh/ohz/PcntSeatChoi/selectSeatList.do"
MOVIE_DETAIL_URL = "https://m.megabox.co.kr/movie-detail"
TIMETABLE_URL = "https://www.megabox.co.kr/booking/timetable"

DEFAULT_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/135.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
    "Content-Type": "application/json;charset=UTF-8",
    "Origin": MEGABOX_BASE_URL,
    "Referer": TIMETABLE_URL,
    "X-Requested-With": "XMLHttpRequest",
}


class MegaboxApiClient:
    def __init__(self, headers: dict[str, str] | None = None) -> None:
        self.headers = {**DEFAULT_HEADERS, **(headers or {})}
        self._reset_session()

    def _reset_session(self) -> None:
        self._cookie_jar = http.cookiejar.CookieJar()
        self._opener = build_opener(HTTPCookieProcessor(self._cookie_jar))
        self._session_ready = False

    def _html_headers(self) -> dict[str, str]:
        headers = {
            key: value
            for key, value in self.headers.items()
            if key.lower() not in {"content-type", "x-requested-with", "origin"}
        }
        headers["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        return headers

    def _ensure_session(self) -> None:
        if self._session_ready:
            return
        request = Request(TIMETABLE_URL, headers=self._html_headers(), method="GET")
        with self._opener.open(request, timeout=30) as response:
            response.read(1024)
        self._session_ready = True

    @staticmethod
    def _non_json_error(text: str) -> RuntimeError:
        preview = " ".join(text.split())[:160]
        return RuntimeError(f"Megabox API returned non-JSON response: {preview}")

    def _post_json(self, url: str, payload: dict[str, Any]) -> dict[str, Any]:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        last_error: Exception | None = None
        for attempt in range(4):
            try:
                try:
                    self._ensure_session()
                except OSError as exc:
                    last_error = exc
                request = Request(url, headers=self.headers, data=body, method="POST")
                with self._opener.open(request, timeout=30) as response:
                    charset = response.headers.get_content_charset() or "utf-8"
                    text = response.read().decode(charset, errors="replace")
                try:
                    return json.loads(text)
                except json.JSONDecodeError:
                    last_error = self._non_json_error(text)
                    self._reset_session()
            except OSError as exc:
                last_error = exc
                self._reset_session()
            if attempt == 3:
                break
            time.sleep(0.6 * (attempt + 1))
        raise last_error or RuntimeError("Megabox API request failed")

    def _get_text(self, url: str, params: dict[str, Any]) -> str:
        query = urlencode([(key, value) for key, value in params.items() if value])
        request_headers = self._html_headers()
        last_error: Exception | None = None
        for attempt in range(3):
            request = Request(f"{url}?{query}", headers=request_headers, method="GET")
            try:
                with self._opener.open(request, timeout=30) as response:
                    charset = response.headers.get_content_charset() or "utf-8"
                    return response.read().decode(charset, errors="replace")
            except OSError as exc:
                last_error = exc
                if attempt == 2:
                    break
                time.sleep(0.5 * (attempt + 1))
        raise last_error or RuntimeError("Megabox detail request failed")

    def fetch_master(self, play_de: str) -> dict[str, Any]:
        return self._post_json(MASTER_LIST_URL, {"playDe": play_de})

    def fetch_schedule(
        self,
        movie_no: str,
        play_de: str,
        area_cd: str,
        first_at: str = "Y",
        master_type: str = "movie",
    ) -> dict[str, Any]:
        payload = {
            "masterType": master_type,
            "movieNo": movie_no,
            "playDe": play_de,
            "areaCd": area_cd,
            "firstAt": first_at,
        }
        return self._post_json(SCHEDULE_URL, payload)

    def fetch_seats(self, play_schdl_no: str, brch_no: str) -> dict[str, Any]:
        payload = {"playSchdlNo": play_schdl_no, "brchNo": brch_no}
        return self._post_json(SEAT_URL, payload)

    def fetch_movie_detail_html(self, representative_movie_no: str) -> str:
        return self._get_text(MOVIE_DETAIL_URL, {"rpstMovieNo": representative_movie_no})
