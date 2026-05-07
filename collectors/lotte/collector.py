from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .api import LotteCinemaApiClient


@dataclass
class LotteCinemaCollector:
    api: LotteCinemaApiClient | None = None
    _ticketing_page_cache: dict[str, Any] | None = None
    _movie_detail_cache: dict[str, dict[str, Any]] | None = None

    def __post_init__(self) -> None:
        if self.api is None:
            self.api = LotteCinemaApiClient()

    def fetch_ticketing_page(self) -> dict[str, Any]:
        if self._ticketing_page_cache is None:
            self._ticketing_page_cache = self.api.fetch_ticketing_page()
        return self._ticketing_page_cache

    def fetch_movies(self) -> list[dict[str, Any]]:
        page = self.fetch_ticketing_page()
        return ((page.get("Movies") or {}).get("Movies") or {}).get("Items") or []

    def fetch_movie_detail(self, representation_movie_code: Any) -> dict[str, Any]:
        code = "" if representation_movie_code is None else str(representation_movie_code).strip()
        if not code:
            return {}
        if self._movie_detail_cache is None:
            self._movie_detail_cache = {}
        if code not in self._movie_detail_cache:
            payload = self.api.fetch_movie_detail(code) if self.api else {}
            self._movie_detail_cache[code] = payload.get("Movie") or {}
        return self._movie_detail_cache[code]

    def fetch_cinemas(self) -> list[dict[str, Any]]:
        page = self.fetch_ticketing_page()
        return ((page.get("Cinemas") or {}).get("Cinemas") or {}).get("Items") or []

    def fetch_play_dates(self) -> list[dict[str, Any]]:
        page = self.fetch_ticketing_page()
        return ((page.get("MoviePlayDates") or {}).get("Items") or {}).get("Items") or []

    def build_movie_records(self, include_details: bool = True) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        for row in self.fetch_movies():
            detail = self.fetch_movie_detail(row.get("RepresentationMovieCode")) if include_details else {}
            genre_names = self._detail_genre_names(detail, row)
            raw = {**row, "movieDetail": detail} if detail else row
            records.append(
                {
                    "provider": "LOTTE_CINEMA",
                    "movie_no": row.get("RepresentationMovieCode"),
                    "movie_name": detail.get("MovieNameKR") or row.get("MovieNameKR"),
                    "movie_name_en": detail.get("MovieNameUS") or row.get("MovieNameUS"),
                    "age_rating": detail.get("ViewGradeNameKR") or row.get("ViewGradeNameKR"),
                    "booking_rate": row.get("BookingRate"),
                    "evaluation": row.get("Evaluation"),
                    "release_date": detail.get("ReleaseDate") or row.get("ReleaseDate"),
                    "runtime_minutes": detail.get("PlayTime") or row.get("PlayTime"),
                    "poster_url": detail.get("PosterURL") or row.get("PosterURL"),
                    "director_name": detail.get("DirectorName") or row.get("DirectorName"),
                    "actor_name": detail.get("ActorName") or row.get("ActorName"),
                    "genre_name": genre_names[0] if genre_names else row.get("MovieGenreNameKR"),
                    "genre_alt": ", ".join(genre_names[1:]),
                    "genre_names": genre_names,
                    "special_screen_codes": row.get("SpecialScreenDivisionCode"),
                    "raw": raw,
                }
            )
        return records

    def build_cinema_records(self) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        for row in self.fetch_cinemas():
            records.append(
                {
                    "provider": "LOTTE_CINEMA",
                    "division_code": row.get("DivisionCode"),
                    "detail_division_code": row.get("DetailDivisionCode"),
                    "detail_division_name": row.get("DetailDivisionNameKR"),
                    "cinema_id": row.get("CinemaID"),
                    "cinema_name": row.get("CinemaNameKR"),
                    "cinema_name_en": row.get("CinemaNameUS"),
                    "latitude": row.get("Latitude"),
                    "longitude": row.get("Longitude"),
                    "address_summary": row.get("CinemaAddrSummary"),
                    "cinema_area_code": row.get("CinemaAreaCode"),
                    "cinema_area_name": row.get("CinemaAreaName"),
                    "smart_order": row.get("SmartOrderYN"),
                    "open_dt": row.get("OpenDtYN"),
                    "stage_greeting": row.get("StageGreetingYN"),
                    "raw": row,
                }
            )
        return records

    def build_play_date_records(self) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        for row in self.fetch_play_dates():
            records.append(
                {
                    "provider": "LOTTE_CINEMA",
                    "play_date": row.get("PlayDate"),
                    "day_of_week": row.get("DayOfWeek"),
                    "is_play": row.get("PlayYN"),
                    "raw": row,
                }
            )
        return records

    def build_cinema_selector(self, cinema: dict[str, Any]) -> str:
        return (
            f"{cinema.get('DivisionCode')}|"
            f"{cinema.get('DetailDivisionCode')}|"
            f"{cinema.get('CinemaID')}"
        )

    def fetch_play_sequences(
        self,
        play_date: str,
        cinema_selector: str,
        representation_movie_code: str,
    ) -> list[dict[str, Any]]:
        response = self.api.fetch_play_sequences(
            play_date=play_date,
            cinema_id=cinema_selector,
            representation_movie_code=representation_movie_code,
        )
        return ((response.get("PlaySeqs") or {}).get("Items")) or []

    def build_schedule_records(
        self,
        play_date: str,
        cinema_selector: str,
        representation_movie_code: str,
        include_details: bool = True,
    ) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        for row in self.fetch_play_sequences(
            play_date=play_date,
            cinema_selector=cinema_selector,
            representation_movie_code=representation_movie_code,
        ):
            detail = (
                self.fetch_movie_detail(row.get("RepresentationMovieCode") or representation_movie_code)
                if include_details
                else {}
            )
            genre_names = self._detail_genre_names(detail, row)
            raw = {**row, "movieDetail": detail} if detail else row
            records.append(
                {
                    "provider": "LOTTE_CINEMA",
                    "movie_no": row.get("RepresentationMovieCode"),
                    "movie_name": detail.get("MovieNameKR") or row.get("MovieNameKR"),
                    "movie_name_en": detail.get("MovieNameUS") or row.get("MovieNameUS"),
                    "age_rating": detail.get("ViewGradeNameKR") or row.get("ViewGradeNameKR"),
                    "runtime_minutes": detail.get("PlayTime"),
                    "genre_name": genre_names[0] if genre_names else row.get("MovieGenreNameKR"),
                    "genre_alt": ", ".join(genre_names[1:]),
                    "genre_names": genre_names,
                    "cinema_id": row.get("CinemaID"),
                    "cinema_name": row.get("CinemaNameKR"),
                    "screen_id": row.get("ScreenID"),
                    "screen_name": row.get("ScreenNameKR"),
                    "screen_division_code": row.get("ScreenDivisionCode"),
                    "screen_division_name": row.get("ScreenDivisionNameKR"),
                    "film_code": row.get("FilmCode"),
                    "film_name": row.get("FilmNameKR"),
                    "sound_type_code": row.get("SoundTypeCode"),
                    "sound_type_name": row.get("SoundTypeNameKR"),
                    "translation_division_code": row.get("TranslationDivisionCode"),
                    "translation_division_name": row.get("TranslationDivisionNameKR"),
                    "play_date": row.get("PlayDt"),
                    "play_day": row.get("PlayDayKR"),
                    "play_sequence": row.get("PlaySequence"),
                    "start_time": row.get("StartTime"),
                    "end_time": row.get("EndTime"),
                    "total_seat_count": row.get("TotalSeatCount"),
                    "remaining_seat_count": self._calculate_remaining_seats(row),
                    "booked_seat_count": row.get("BookingSeatCount"),
                    "booking_available": row.get("IsBookingYN"),
                    "sequence_group_name": row.get("SequenceNoGroupNameKR"),
                    "screen_floor": row.get("ScreenFloor"),
                    "poster_url": detail.get("PosterURL") or row.get("PosterURL"),
                    "booking_key": {
                        "cinema_id": row.get("CinemaID"),
                        "screen_id": row.get("ScreenID"),
                        "play_date": row.get("PlayDt"),
                        "play_sequence": row.get("PlaySequence"),
                        "screen_division_code": row.get("ScreenDivisionCode"),
                    },
                    "raw": raw,
                }
            )
        return records

    @staticmethod
    def _detail_genre_names(detail: dict[str, Any], row: dict[str, Any]) -> list[str]:
        values = [
            detail.get("MovieGenreNameKR"),
            detail.get("MovieGenreNameKR2"),
            detail.get("MovieGenreNameKR3"),
            row.get("MovieGenreNameKR"),
            row.get("MovieGenreNameKR2"),
            row.get("MovieGenreNameKR3"),
        ]
        genres: list[str] = []
        for value in values:
            text = "" if value is None else str(value).strip()
            if text and text not in genres:
                genres.append(text)
        return genres

    def fetch_seat_map(
        self,
        cinema_id: int,
        screen_id: int,
        play_date: str,
        play_sequence: int,
        screen_division_code: int,
    ) -> dict[str, Any]:
        return self.api.fetch_seats(
            cinema_id=cinema_id,
            screen_id=screen_id,
            play_date=play_date,
            play_sequence=play_sequence,
            screen_division_code=screen_division_code,
        )

    def build_seat_records(
        self,
        cinema_id: int,
        screen_id: int,
        play_date: str,
        play_sequence: int,
        screen_division_code: int,
    ) -> list[dict[str, Any]]:
        payload = self.fetch_seat_map(
            cinema_id=cinema_id,
            screen_id=screen_id,
            play_date=play_date,
            play_sequence=play_sequence,
            screen_division_code=screen_division_code,
        )
        records: list[dict[str, Any]] = []
        for row in ((payload.get("Seats") or {}).get("Items")) or []:
            seat_row = str(row.get("ShowSeatRow") or row.get("SeatRow") or "")
            seat_column = row.get("ShowSeatColumn") or row.get("SeatColumn")
            records.append(
                {
                    "provider": "LOTTE_CINEMA",
                    "cinema_id": cinema_id,
                    "screen_id": screen_id,
                    "play_date": play_date,
                    "play_sequence": play_sequence,
                    "screen_division_code": screen_division_code,
                    "seat_no": row.get("SeatNo"),
                    "seat_label": f"{seat_row}{seat_column}" if seat_row else str(row.get("SeatNo") or ""),
                    "seat_row": seat_row,
                    "seat_column": seat_column,
                    "screen_floor": row.get("ScreenFloor"),
                    "seat_floor": row.get("SeatFloor"),
                    "seat_status_code": row.get("SeatStatusCode"),
                    "customer_division_code": row.get("CustomerDivisionCode"),
                    "physical_block_code": row.get("PhysicalBlockCode"),
                    "logical_block_code": row.get("LogicalBlockCode"),
                    "seat_block_set": row.get("SeatBlockSet"),
                    "sales_disable_ticket_code": row.get("SalesDisableTicketCode"),
                    "x": row.get("SeatXCoordinate"),
                    "y": row.get("SeatYCoordinate"),
                    "width": row.get("SeatXLength"),
                    "height": row.get("SeatYLength"),
                    "sweet_spot": row.get("SweetSpotYN"),
                    "raw": row,
                }
            )
        return records

    def summarize_seat_map(
        self,
        cinema_id: int,
        screen_id: int,
        play_date: str,
        play_sequence: int,
        screen_division_code: int,
    ) -> dict[str, Any]:
        payload = self.fetch_seat_map(
            cinema_id=cinema_id,
            screen_id=screen_id,
            play_date=play_date,
            play_sequence=play_sequence,
            screen_division_code=screen_division_code,
        )
        return {
            "provider": "LOTTE_CINEMA",
            "cinema_id": cinema_id,
            "screen_id": screen_id,
            "play_date": play_date,
            "play_sequence": play_sequence,
            "screen_division_code": screen_division_code,
            "customer_divisions": ((payload.get("CustomerDivision") or {}).get("Items")) or [],
            "screen_seat_info": ((payload.get("ScreenSeatInfo") or {}).get("Items")) or [],
            "entrances": ((payload.get("Enterences") or {}).get("Items")) or [],
            "seat_count": len(((payload.get("Seats") or {}).get("Items")) or []),
            "booking_seat_count": len(((payload.get("BookingSeats") or {}).get("Items")) or []),
            "fee_items": ((payload.get("Fees") or {}).get("Items")) or [],
            "play_seq_details": ((payload.get("PlaySeqsDetails") or {}).get("Items")) or [],
            "additional_messages": ((payload.get("AdditionalMessages") or {}).get("Items")) or [],
            "seat_info_img": payload.get("SeatInfoImg"),
        }

    def collect_bundle(
        self,
        play_date: str,
        representation_movie_code: str,
        cinema_selector: str,
        seat_cinema_id: int | None = None,
        seat_screen_id: int | None = None,
        seat_play_sequence: int | None = None,
        seat_screen_division_code: int | None = None,
    ) -> dict[str, Any]:
        movies = self.build_movie_records()
        cinemas = self.build_cinema_records()
        play_dates = self.build_play_date_records()
        schedules = self.build_schedule_records(
            play_date=play_date,
            cinema_selector=cinema_selector,
            representation_movie_code=representation_movie_code,
        )

        seat_summary: dict[str, Any] | None = None
        seat_records: list[dict[str, Any]] = []
        if (
            seat_cinema_id is not None
            and seat_screen_id is not None
            and seat_play_sequence is not None
            and seat_screen_division_code is not None
        ):
            seat_summary = self.summarize_seat_map(
                cinema_id=seat_cinema_id,
                screen_id=seat_screen_id,
                play_date=play_date,
                play_sequence=seat_play_sequence,
                screen_division_code=seat_screen_division_code,
            )
            seat_records = self.build_seat_records(
                cinema_id=seat_cinema_id,
                screen_id=seat_screen_id,
                play_date=play_date,
                play_sequence=seat_play_sequence,
                screen_division_code=seat_screen_division_code,
            )

        return {
            "play_date": play_date,
            "movie_count": len(movies),
            "cinema_count": len(cinemas),
            "play_date_count": len(play_dates),
            "schedule_count": len(schedules),
            "seat_count": len(seat_records),
            "movies": movies,
            "cinemas": cinemas,
            "play_dates": play_dates,
            "schedules": schedules,
            "seat_records": seat_records,
            "seat_summary": seat_summary,
        }

    @staticmethod
    def _calculate_remaining_seats(row: dict[str, Any]) -> int | None:
        total = row.get("TotalSeatCount")
        booked = row.get("BookingSeatCount")
        if isinstance(total, int) and isinstance(booked, int):
            return total - booked
        return None
