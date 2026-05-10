from __future__ import annotations

import json
import time
import urllib.parse
from typing import Any
from urllib.request import Request, urlopen


LOTTE_TICKETING_URL = "https://www.lottecinema.co.kr/LCWS/Ticketing/TicketingData.aspx"
LOTTE_MOVIE_URL = "https://www.lottecinema.co.kr/LCWS/Movie/MovieData.aspx"

DEFAULT_BASE_PAYLOAD = {
    "channelType": "HO",
    "osType": "W",
    "osVersion": "Mozilla/5.0",
}

DEFAULT_HEADERS = {
    "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
    "X-Requested-With": "XMLHttpRequest",
    "User-Agent": "Mozilla/5.0",
}


class LotteCinemaApiClient:
    def __init__(
        self,
        base_payload: dict[str, Any] | None = None,
        headers: dict[str, str] | None = None,
    ) -> None:
        self.base_payload = {**DEFAULT_BASE_PAYLOAD, **(base_payload or {})}
        self.headers = {**DEFAULT_HEADERS, **(headers or {})}

    def _post_to(self, url: str, payload: dict[str, Any]) -> dict[str, Any]:
        merged = {**self.base_payload, **payload}
        body = urllib.parse.urlencode(
            {"paramList": json.dumps(merged, ensure_ascii=False)}
        ).encode("utf-8")
        last_error: Exception | None = None
        for attempt in range(3):
            request = Request(
                url,
                data=body,
                headers=self.headers,
                method="POST",
            )
            try:
                with urlopen(request, timeout=30) as response:
                    charset = response.headers.get_content_charset() or "utf-8"
                    text = response.read().decode(charset, errors="replace")
                return json.loads(text)
            except OSError as exc:
                last_error = exc
                if attempt == 2:
                    break
                time.sleep(0.5 * (attempt + 1))
        raise last_error or RuntimeError("Lotte API request failed")

    def _post(self, payload: dict[str, Any]) -> dict[str, Any]:
        return self._post_to(LOTTE_TICKETING_URL, payload)

    def fetch_ticketing_page(self, member_on_no: str = "0") -> dict[str, Any]:
        return self._post(
            {
                "MethodName": "GetTicketingPageTOBE",
                "memberOnNo": member_on_no,
            }
        )

    def fetch_movie_detail(
        self,
        representation_movie_code: str,
        member_on_no: str = "0",
    ) -> dict[str, Any]:
        return self._post_to(
            LOTTE_MOVIE_URL,
            {
                "MethodName": "GetMovieDetailTOBE",
                "multiLanguageID": "KR",
                "representationMovieCode": representation_movie_code,
                "memberOnNo": member_on_no,
                "imgdivcd": 3,
            },
        )

    def fetch_play_sequences(
        self,
        play_date: str,
        cinema_id: str,
        representation_movie_code: str,
    ) -> dict[str, Any]:
        return self._post(
            {
                "MethodName": "GetPlaySequence",
                "playDate": play_date,
                "cinemaID": cinema_id,
                "representationMovieCode": representation_movie_code,
            }
        )

    def fetch_seats(
        self,
        cinema_id: int,
        screen_id: int,
        play_date: str,
        play_sequence: int,
        screen_division_code: int,
    ) -> dict[str, Any]:
        return self._post(
            {
                "MethodName": "GetSeats",
                "cinemaId": cinema_id,
                "screenId": screen_id,
                "playDate": play_date,
                "playSequence": play_sequence,
                "screenDivisionCode": screen_division_code,
            }
        )
