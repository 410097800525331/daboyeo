from __future__ import annotations

import re
from dataclasses import dataclass
from html import unescape
from typing import Any

from .api import MEGABOX_BASE_URL, MegaboxApiClient


@dataclass
class MegaboxCollector:
    api: MegaboxApiClient | None = None
    _movie_detail_cache: dict[str, dict[str, Any]] | None = None

    def __post_init__(self) -> None:
        if self.api is None:
            self.api = MegaboxApiClient()

    def fetch_master(self, play_de: str) -> dict[str, Any]:
        return self.api.fetch_master(play_de)

    def fetch_movies(self, play_de: str) -> list[dict[str, Any]]:
        master = self.fetch_master(play_de)
        movie_list = master.get("movieList") or []
        curation_list = master.get("crtnMovieList") or []
        merged: dict[str, dict[str, Any]] = {}
        for row in movie_list + curation_list:
            key = str(row.get("movieNo") or row.get("rpstMovieNo") or "")
            if key and key not in merged:
                merged[key] = row
        return list(merged.values())

    def fetch_movie_detail(self, movie_no: Any) -> dict[str, Any]:
        code = "" if movie_no is None else str(movie_no).strip()
        if not code:
            return {}
        if self._movie_detail_cache is None:
            self._movie_detail_cache = {}
        if code not in self._movie_detail_cache:
            html_text = self.api.fetch_movie_detail_html(code) if self.api else ""
            self._movie_detail_cache[code] = self._parse_movie_detail_html(code, html_text)
        return self._movie_detail_cache[code]

    def build_movie_records(self, play_de: str, include_details: bool = True) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        for row in self.fetch_movies(play_de):
            detail = self.fetch_movie_detail(row.get("rpstMovieNo") or row.get("movieNo")) if include_details else {}
            raw = {**row, "movieDetail": detail} if detail else row
            records.append(
                {
                    "provider": "MEGABOX",
                    "movie_no": row.get("movieNo"),
                    "representative_movie_no": row.get("rpstMovieNo"),
                    "movie_name": row.get("movieNm") or detail.get("title"),
                    "movie_name_en": row.get("movieEngNm"),
                    "age_rating": row.get("admisClassCdNm"),
                    "runtime_minutes": row.get("playTime"),
                    "booking_rate": row.get("bookRate"),
                    "box_office_rank": row.get("boxoRank"),
                    "release_date": row.get("openDt"),
                    "genre_name": detail.get("genre_name"),
                    "screening_type": row.get("screenType"),
                    "poster_url": self._build_poster_url(row.get("movieImgPath")),
                    "raw": raw,
                }
            )
        return records

    def fetch_areas(self, play_de: str) -> list[dict[str, Any]]:
        master = self.fetch_master(play_de)
        return master.get("areaBrchList") or []

    def build_area_records(self, play_de: str) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        for row in self.fetch_areas(play_de):
            records.append(
                {
                    "provider": "MEGABOX",
                    "area_code": row.get("areaCd"),
                    "area_name": row.get("areaCdNm"),
                    "branch_no": row.get("brchNo"),
                    "branch_name": row.get("brchNm"),
                    "branch_link": (
                        f"{MEGABOX_BASE_URL}/theater?brchNo={row.get('brchNo')}"
                        if row.get("brchNo")
                        else ""
                    ),
                    "special_theater_available": row.get("spclbAt"),
                    "booking_open": row.get("bokdAbleAt"),
                    "raw": row,
                }
            )
        return records

    def fetch_schedules(
        self,
        movie_no: str,
        play_de: str,
        area_cd: str,
        first_at: str = "Y",
    ) -> list[dict[str, Any]]:
        response = self.api.fetch_schedule(
            movie_no=movie_no,
            play_de=play_de,
            area_cd=area_cd,
            first_at=first_at,
        )
        return (response.get("megaMap") or {}).get("movieFormList") or []

    def fetch_seat_map(self, play_schdl_no: str, brch_no: str) -> dict[str, Any]:
        return self.api.fetch_seats(play_schdl_no=play_schdl_no, brch_no=brch_no)

    def build_schedule_records(
        self,
        movie_no: str,
        play_de: str,
        area_cd: str,
        first_at: str = "Y",
        include_details: bool = True,
    ) -> list[dict[str, Any]]:
        rows = self.fetch_schedules(
            movie_no=movie_no,
            play_de=play_de,
            area_cd=area_cd,
            first_at=first_at,
        )
        records: list[dict[str, Any]] = []
        for row in rows:
            detail = (
                self.fetch_movie_detail(row.get("rpstMovieNo") or row.get("movieNo") or movie_no)
                if include_details
                else {}
            )
            raw = {**row, "movieDetail": detail} if detail else row
            records.append(
                {
                    "provider": "MEGABOX",
                    "movie_no": row.get("movieNo"),
                    "representative_movie_no": row.get("rpstMovieNo"),
                    "movie_name": row.get("movieNm") or row.get("rpstMovieNm"),
                    "movie_name_en": row.get("movieEngNm"),
                    "movie_status": row.get("movieStatCdNm"),
                    "age_rating": row.get("admisClassCdNm"),
                    "genre_name": detail.get("genre_name"),
                    "area_code": row.get("areaCd"),
                    "area_name": row.get("areaCdNm"),
                    "branch_no": row.get("brchNo"),
                    "branch_name": row.get("brchNm"),
                    "theater_no": row.get("theabNo"),
                    "screen_name": self._clean_text(row.get("theabExpoNm")),
                    "screen_type": self._clean_text(row.get("playKindNm")),
                    "screen_kind_code": row.get("theabKindCd"),
                    "play_date": row.get("playDe"),
                    "start_time": row.get("playStartTime"),
                    "end_time": row.get("playEndTime"),
                    "play_sequence": row.get("seq"),
                    "play_schedule_no": str(row.get("playSchdlNo") or ""),
                    "times_division_name": row.get("timesDivNm"),
                    "remaining_seat_count": row.get("restSeatCnt"),
                    "total_seat_count": row.get("totSeatCnt"),
                    "booking_available": row.get("bokdAbleAt"),
                    "poster_url": self._build_poster_url(row.get("moviePosterImg")),
                    "booking_url": (
                        f"{MEGABOX_BASE_URL}/on/oh/ohz/PcntSeatChoi/"
                        f"selectPcntSeatChoi.do?playSchdlNo={row.get('playSchdlNo')}"
                    ),
                    "raw": raw,
                }
            )
        return records

    def build_seat_records(
        self, play_schdl_no: str, brch_no: str
    ) -> list[dict[str, Any]]:
        payload = self.fetch_seat_map(play_schdl_no=play_schdl_no, brch_no=brch_no)
        records: list[dict[str, Any]] = []
        for row in payload.get("seatListSD01") or []:
            row_name = str(row.get("rowNm") or "")
            seat_no = str(row.get("seatNo") or "")
            records.append(
                {
                    "provider": "MEGABOX",
                    "play_schedule_no": play_schdl_no,
                    "branch_no": brch_no,
                    "seat_id": row.get("seatUniqNo"),
                    "seat_label": f"{row_name}{seat_no}".strip(),
                    "seat_row": row_name,
                    "seat_number": seat_no,
                    "row_number": row.get("rowNo"),
                    "column_number": row.get("colNo"),
                    "seat_zone_code": row.get("seatZoneCd"),
                    "seat_class_code": row.get("seatClassCd"),
                    "seat_type_code": row.get("seatSellTyCd"),
                    "seat_status_code": row.get("seatStatCd"),
                    "row_status_code": row.get("rowStatCd"),
                    "exposed": row.get("seatExpoAt"),
                    "x": row.get("horzCoorVal"),
                    "y": row.get("vertCoorVal"),
                    "width_rate": row.get("horzSizeRt"),
                    "x_rate": row.get("horzPosiRt"),
                    "y_rate": row.get("vertPosiRt"),
                    "seat_group_name": row.get("seatChoiGrpNm"),
                    "seat_group_direction": row.get("seatChoiDircVal"),
                    "notice": row.get("seatNotiMsg"),
                    "raw": row,
                }
            )
        return records

    def summarize_seat_map(self, play_schdl_no: str, brch_no: str) -> dict[str, Any]:
        payload = self.fetch_seat_map(play_schdl_no=play_schdl_no, brch_no=brch_no)
        movie_dtl = payload.get("movieDtlInfo") or {}
        seat_list = payload.get("seatListSD01") or []
        play_seq_list = payload.get("playSeqList") or []
        return {
            "provider": "MEGABOX",
            "play_schedule_no": play_schdl_no,
            "branch_no": brch_no,
            "movie_detail": movie_dtl,
            "other_sequences": play_seq_list,
            "seat_count": len(seat_list),
            "seat_sample": seat_list[:20],
            "seat_class_codes": payload.get("seatClassCdList") or [],
            "seat_policy_list": payload.get("seatPolicyList") or [],
            "seat_ticket_amounts": payload.get("seatTicketAmtList") or [],
        }

    def collect_bundle(
        self,
        play_de: str,
        movie_no: str,
        area_cd: str,
        seat_play_schdl_no: str | None = None,
        seat_brch_no: str | None = None,
    ) -> dict[str, Any]:
        movies = self.build_movie_records(play_de)
        areas = self.build_area_records(play_de)
        schedules = self.build_schedule_records(
            movie_no=movie_no,
            play_de=play_de,
            area_cd=area_cd,
        )

        seat_summary: dict[str, Any] | None = None
        seat_records: list[dict[str, Any]] = []
        if seat_play_schdl_no and seat_brch_no:
            seat_summary = self.summarize_seat_map(
                play_schdl_no=seat_play_schdl_no,
                brch_no=seat_brch_no,
            )
            seat_records = self.build_seat_records(
                play_schdl_no=seat_play_schdl_no,
                brch_no=seat_brch_no,
            )

        return {
            "play_de": play_de,
            "movie_count": len(movies),
            "area_branch_count": len(areas),
            "schedule_count": len(schedules),
            "seat_count": len(seat_records),
            "movies": movies,
            "areas": areas,
            "schedules": schedules,
            "seat_records": seat_records,
            "seat_summary": seat_summary,
        }

    @staticmethod
    def _build_poster_url(path: str | None) -> str:
        if not path:
            return ""
        if path.startswith("http://") or path.startswith("https://"):
            return path
        return f"{MEGABOX_BASE_URL}{path}"

    @staticmethod
    def _clean_text(value: Any) -> str:
        return unescape(str(value or "")).strip()

    @classmethod
    def _parse_movie_detail_html(cls, movie_no: str, html_text: str) -> dict[str, Any]:
        title_match = re.search(
            r'<meta\s+property="name"[^>]*content="\[메가박스\]([^"]*)"',
            html_text,
        )
        return {
            "movieNo": movie_no,
            "detailUrl": f"https://m.megabox.co.kr/movie-detail?rpstMovieNo={movie_no}",
            "title": cls._clean_text(title_match.group(1)) if title_match else "",
            "genre_name": cls._extract_detail_item(html_text, "장르"),
        }

    @classmethod
    def _extract_detail_item(cls, html_text: str, label: str) -> str:
        marker = f"<span>{label}</span>"
        if marker not in html_text:
            return ""
        value = html_text.split(marker, 1)[1].split("</li>", 1)[0]
        value = re.sub(r"<[^>]*>", " ", value)
        return cls._clean_text(value)
