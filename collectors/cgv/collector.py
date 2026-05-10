from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .api import CgvApiClient


def _text(value: Any) -> str:
    return "" if value is None else str(value).strip()


def _first_text(row: dict[str, Any], *keys: str) -> str:
    for key in keys:
        value = _text(row.get(key))
        if value:
            return value
    return ""


def _number(value: Any) -> int | float | None:
    text = _text(value).replace(",", "")
    if not text:
        return None
    try:
        number = float(text)
    except ValueError:
        return None
    return int(number) if number.is_integer() else number


def _coord(row: dict[str, Any], *keys: str) -> int | float | None:
    for key in keys:
        value = _number(row.get(key))
        if value is not None:
            return value
    return None


def _dimension(start: int | float | None, end: int | float | None) -> int | float | None:
    if start is None or end is None:
        return None
    size = abs(end - start)
    return size if size > 0 else None


def _normalized_seat_status(seat: dict[str, Any]) -> str:
    code = _first_text(seat, "seatStusCd", "seat_status_code").upper()
    sale = _first_text(seat, "seatSaleYn", "seat_sale_yn").upper()
    status_name = _first_text(seat, "seatStusNm", "seat_status_name").upper()
    if any(token in status_name for token in ["LOCK", "BLOCK"]):
        return "unavailable"
    if code == "01":
        return "sold"
    if code == "00" or sale == "Y":
        return "available"
    if sale == "N":
        return "sold"
    return "unknown"


def _seat_key(seat: dict[str, Any], seat_label: str) -> str:
    stable_key = _first_text(seat, "seatLocNo", "seatUniqNo", "movAtktNo", "seatNo")
    if stable_key:
        return stable_key
    x = _first_text(seat, "xcoordStartVal", "x")
    y = _first_text(seat, "ycoordStartVal", "y")
    return f"{seat_label}:{x}:{y}".strip(":")


@dataclass
class CgvCollector:
    api: CgvApiClient | None = None
    co_cd: str = "A420"

    def __post_init__(self) -> None:
        if self.api is None:
            self.api = CgvApiClient()

    def fetch_movies(self, mov_nm: str = "", div: str = "", attr_cd: str = "") -> list[dict[str, Any]]:
        payload = self.api.fetch_movies(co_cd=self.co_cd, mov_nm=mov_nm, div=div, attr_cd=attr_cd)
        return payload.get("data") or []

    def fetch_attributes(self) -> list[dict[str, Any]]:
        payload = self.api.fetch_attributes(co_cd=self.co_cd)
        return payload.get("data") or []

    def fetch_regions_and_sites(self) -> dict[str, list[dict[str, Any]]]:
        payload = self.api.fetch_regions_and_sites(co_cd=self.co_cd)
        data = payload.get("data") or {}
        return {
            "regions": data.get("regionInfo") or [],
            "sites": data.get("siteInfo") or [],
            "recommended_sites": data.get("rcmSiteInfo") or [],
        }

    def fetch_dates_by_movie(self, site_no: str, mov_no: str, div: str = "", attr_cd: str = "") -> list[dict[str, Any]]:
        payload = self.api.fetch_dates_by_movie(
            co_cd=self.co_cd,
            site_no=site_no,
            mov_no=mov_no,
            div=div,
            attr_cd=attr_cd,
        )
        return payload.get("data") or []

    def fetch_schedules_by_movie(
        self,
        site_no: str,
        scn_ymd: str,
        mov_no: str,
        scns_no: str = "",
        scn_sseq: str = "",
        prod_no: str = "",
        rtctl_scop_cd: str = "08",
        sals_tzn_cd: str = "",
        tcscns_grad_cd: str = "",
        sascns_grad_cd: str = "",
        cust_no: str = "",
    ) -> list[dict[str, Any]]:
        payload = self.api.fetch_schedules_by_movie(
            co_cd=self.co_cd,
            site_no=site_no,
            scn_ymd=scn_ymd,
            mov_no=mov_no,
            scns_no=scns_no,
            scn_sseq=scn_sseq,
            prod_no=prod_no,
            rtctl_scop_cd=rtctl_scop_cd,
            sals_tzn_cd=sals_tzn_cd,
            tcscns_grad_cd=tcscns_grad_cd,
            sascns_grad_cd=sascns_grad_cd,
            cust_no=cust_no,
        )
        return payload.get("data") or []

    def fetch_seat_map(
        self,
        site_no: str,
        scn_ymd: str,
        scns_no: str,
        scn_sseq: str,
        seat_area_no: str = "",
        cusgd_cd: str = "",
        cust_no: str = "",
    ) -> dict[str, Any]:
        payload = self.api.fetch_seats(
            co_cd=self.co_cd,
            site_no=site_no,
            scn_ymd=scn_ymd,
            scns_no=scns_no,
            scn_sseq=scn_sseq,
            seat_area_no=seat_area_no,
            cusgd_cd=cusgd_cd,
            cust_no=cust_no,
        )
        return payload.get("data") or {}

    def build_movie_records(self) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        for row in self.fetch_movies():
            records.append(
                {
                    "provider": "CGV",
                    "co_cd": row.get("coCd") or self.co_cd,
                    "movie_no": row.get("movNo"),
                    "movie_name": row.get("movNm"),
                    "poster_filename": row.get("i320Fnm"),
                    "runtime_minutes": row.get("scnBssTm"),
                    "age_rating_code": row.get("cratgClsCd"),
                    "booking_rate": row.get("atktRate"),
                    "mobile_url": row.get("mblUrl"),
                    "raw": row,
                }
            )
        return records

    def build_attribute_records(self) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        for row in self.fetch_attributes():
            records.append(
                {
                    "provider": "CGV",
                    "attribute_code": row.get("comCd") or row.get("attrCd"),
                    "attribute_name": row.get("comCdNm") or row.get("attrNm"),
                    "raw": row,
                }
            )
        return records

    def build_region_records(self) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        for row in self.fetch_regions_and_sites()["regions"]:
            records.append(
                {
                    "provider": "CGV",
                    "region_code": row.get("regionCd") or row.get("regnCd"),
                    "region_name": row.get("regionNm") or row.get("regnNm"),
                    "region_name_en": row.get("regionEnm") or row.get("regnEnm"),
                    "raw": row,
                }
            )
        return records

    def build_site_records(self) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        for row in self.fetch_regions_and_sites()["sites"]:
            records.append(
                {
                    "provider": "CGV",
                    "co_cd": row.get("coCd") or self.co_cd,
                    "site_no": row.get("siteNo"),
                    "site_name": row.get("siteNm"),
                    "region_code": row.get("regionCd") or row.get("regnCd"),
                    "region_name": row.get("regionNm") or row.get("regnNm"),
                    "latitude": row.get("latiVal"),
                    "longitude": row.get("lntiVal"),
                    "address": row.get("roadNmAddr") or row.get("addr"),
                    "special_site_yn": row.get("speclSiteYn"),
                    "raw": row,
                }
            )
        return records

    def build_date_records(self, site_no: str, mov_no: str) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        for row in self.fetch_dates_by_movie(site_no=site_no, mov_no=mov_no):
            records.append(
                {
                    "provider": "CGV",
                    "site_no": site_no,
                    "movie_no": mov_no,
                    "screening_date": row.get("scnYmd"),
                    "holiday": row.get("hldyYn"),
                    "raw": row,
                }
            )
        return records

    def build_schedule_records(self, site_no: str, scn_ymd: str, mov_no: str) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        for row in self.fetch_schedules_by_movie(site_no=site_no, scn_ymd=scn_ymd, mov_no=mov_no):
            records.append(
                {
                    "provider": "CGV",
                    "co_cd": row.get("coCd") or self.co_cd,
                    "site_no": row.get("siteNo"),
                    "site_name": row.get("siteNm"),
                    "movie_no": row.get("movNo"),
                    "movie_name": row.get("movNm") or row.get("prodNm"),
                    "movie_name_en": row.get("movEnm") or row.get("engProdNm"),
                    "screen_no": row.get("scnsNo"),
                    "screen_name": row.get("scnsNm") or row.get("expoScnsNm"),
                    "screening_date": row.get("scnYmd"),
                    "screen_sequence": row.get("scnSseq"),
                    "product_no": row.get("prodNo"),
                    "format_code": row.get("movkndCd"),
                    "format_name": row.get("movkndDsplNm"),
                    "age_rating_code": row.get("cratgClsCd"),
                    "age_rating_name": row.get("cratgClsNm"),
                    "sales_zone_code": row.get("salsTznCd"),
                    "sales_zone_name": row.get("salsTznNm"),
                    "start_time": row.get("scnsrtTm"),
                    "end_time": row.get("scnendTm"),
                    "sale_end_time": row.get("salEndTm"),
                    "screen_grade_code": row.get("scnsGradCd"),
                    "screen_grade_name": row.get("sascnsGradNm"),
                    "total_seat_count": row.get("stcnt"),
                    "available_seat_count": row.get("frSeatCnt"),
                    "temp_locked_seat_count": row.get("frtmpSeatCnt"),
                    "caption_subtitle_code": row.get("sbtdivCd"),
                    "caption_subtitle_name": row.get("sbtdivNm"),
                    "poster_filename": row.get("physcFnm"),
                    "poster_path": row.get("physcFilePathnm"),
                    "booking_key": {
                        "site_no": row.get("siteNo"),
                        "scn_ymd": row.get("scnYmd"),
                        "scns_no": row.get("scnsNo"),
                        "scn_sseq": row.get("scnSseq"),
                    },
                    "raw": row,
                }
            )
        return records

    def build_seat_records(
        self,
        site_no: str,
        scn_ymd: str,
        scns_no: str,
        scn_sseq: str,
        seat_area_no: str = "",
    ) -> list[dict[str, Any]]:
        payload = self.fetch_seat_map(
            site_no=site_no,
            scn_ymd=scn_ymd,
            scns_no=scns_no,
            scn_sseq=scn_sseq,
            seat_area_no=seat_area_no,
        )
        return self.build_seat_records_from_payload(
            payload=payload,
            site_no=site_no,
            scn_ymd=scn_ymd,
            scns_no=scns_no,
            scn_sseq=scn_sseq,
        )

    def build_seat_records_from_payload(
        self,
        payload: dict[str, Any],
        site_no: str = "",
        scn_ymd: str = "",
        scns_no: str = "",
        scn_sseq: str = "",
    ) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        effective_site_no = site_no or _text(payload.get("siteNo"))
        effective_scn_ymd = scn_ymd or _text(payload.get("scnYmd"))
        effective_scns_no = scns_no or _text(payload.get("scnsNo"))
        effective_scn_sseq = scn_sseq or _text(payload.get("scnSseq"))
        for item in payload.get("items") or []:
            seats = item.get("seats") or item.get("seatList") or []
            for seat in seats:
                seat_row = _first_text(seat, "seatRowNm", "seat_row")
                seat_no = _first_text(seat, "seatNo", "seat_number", "seat_column")
                seat_label = f"{seat_row}{seat_no}".strip()
                x1 = _coord(seat, "xcoordStartVal", "xcoordStrtVal", "x1", "x")
                y1 = _coord(seat, "ycoordStartVal", "ycoordStrtVal", "y1", "y")
                x2 = _coord(seat, "xcoordEndVal", "xcoordEndVal", "x2")
                y2 = _coord(seat, "ycoordEndVal", "ycoordEndVal", "y2")
                width = _dimension(x1, x2)
                height = _dimension(y1, y2)
                records.append(
                    {
                        "provider": "CGV",
                        "site_no": effective_site_no,
                        "screening_date": effective_scn_ymd,
                        "screen_no": effective_scns_no,
                        "screen_sequence": effective_scn_sseq,
                        "seat_key": _seat_key(seat, seat_label),
                        "seat_loc_no": seat.get("seatLocNo"),
                        "seat_label": seat_label,
                        "seat_row": seat_row,
                        "seat_number": seat_no,
                        "seat_kind_code": seat.get("stkndCd"),
                        "seat_kind_name": seat.get("stkndNm"),
                        "seat_zone_name": seat.get("szoneNm"),
                        "seat_zone_kind_code": seat.get("szoneKindCd"),
                        "seat_status_code": seat.get("seatStusCd"),
                        "seat_status_name": seat.get("seatStusNm"),
                        "seat_sale_yn": seat.get("seatSaleYn"),
                        "left_passage_yn": seat.get("leftPwayYn"),
                        "right_passage_yn": seat.get("rghtPwayYn"),
                        "x": x1,
                        "y": y1,
                        "width": width,
                        "height": height,
                        "x1": x1,
                        "y1": y1,
                        "x2": x2,
                        "y2": y2,
                        "normalized_status": _normalized_seat_status(seat),
                        "seat_area_no": item.get("seatAreaNo") or seat.get("seatAreaNo"),
                        "terminal_no": seat.get("trmnNo"),
                        "booking_ticket_no": seat.get("movAtktNo"),
                        "raw": seat,
                    }
                )
        return records

    def build_seat_layout(
        self,
        site_no: str,
        scn_ymd: str,
        scns_no: str,
        scn_sseq: str,
        seat_area_no: str = "",
    ) -> dict[str, Any]:
        payload = self.fetch_seat_map(
            site_no=site_no,
            scn_ymd=scn_ymd,
            scns_no=scns_no,
            scn_sseq=scn_sseq,
            seat_area_no=seat_area_no,
        )
        return self.build_seat_layout_from_payload(
            payload=payload,
            site_no=site_no,
            scn_ymd=scn_ymd,
            scns_no=scns_no,
            scn_sseq=scn_sseq,
        )

    def build_seat_layout_from_payload(
        self,
        payload: dict[str, Any],
        site_no: str = "",
        scn_ymd: str = "",
        scns_no: str = "",
        scn_sseq: str = "",
    ) -> dict[str, Any]:
        seats = self.build_seat_records_from_payload(
            payload=payload,
            site_no=site_no,
            scn_ymd=scn_ymd,
            scns_no=scns_no,
            scn_sseq=scn_sseq,
        )
        row_positions: dict[str, int | float] = {}
        for seat in seats:
            row = _text(seat.get("seat_row"))
            y = seat.get("y")
            if not row or not isinstance(y, (int, float)):
                continue
            row_positions[row] = min(row_positions.get(row, y), y)

        return {
            "key": "CGV",
            "provider": "CGV",
            "siteNo": site_no or payload.get("siteNo"),
            "theaterName": payload.get("siteNm"),
            "screeningDate": scn_ymd or payload.get("scnYmd"),
            "screenNo": scns_no or payload.get("scnsNo"),
            "screenSequence": scn_sseq or payload.get("scnSseq"),
            "seatAreaNo": payload.get("seatAreaNo"),
            "screenName": payload.get("scnsNm"),
            "totalSeatCount": len(seats),
            "remainingSeatCount": sum(1 for seat in seats if seat.get("normalized_status") == "available"),
            "rowLabels": [
                {"row": row, "y": y}
                for row, y in sorted(row_positions.items(), key=lambda item: (item[1], item[0]))
            ],
            "zoneBoxes": self._build_zone_boxes(payload, seats),
            "seatBoards": self._extract_seat_boards(payload),
            "seats": [
                {
                    "id": seat.get("seat_key"),
                    "label": seat.get("seat_label"),
                    "x": seat.get("x"),
                    "y": seat.get("y"),
                    "w": seat.get("width"),
                    "h": seat.get("height"),
                    "status": seat.get("normalized_status"),
                    "type": seat.get("seat_kind_name") or seat.get("seat_kind_code"),
                    "zone": seat.get("seat_zone_name") or seat.get("seat_zone_kind_code"),
                    "providerMeta": {
                        "seatKindCode": seat.get("seat_kind_code"),
                        "seatZoneKindCode": seat.get("seat_zone_kind_code"),
                        "seatStatusCode": seat.get("seat_status_code"),
                        "seatSaleYn": seat.get("seat_sale_yn"),
                        "leftPassageYn": seat.get("left_passage_yn"),
                        "rightPassageYn": seat.get("right_passage_yn"),
                    },
                }
                for seat in seats
            ],
        }

    def summarize_seat_map(
        self,
        site_no: str,
        scn_ymd: str,
        scns_no: str,
        scn_sseq: str,
        seat_area_no: str = "",
    ) -> dict[str, Any]:
        payload = self.fetch_seat_map(
            site_no=site_no,
            scn_ymd=scn_ymd,
            scns_no=scns_no,
            scn_sseq=scn_sseq,
            seat_area_no=seat_area_no,
        )
        items = payload.get("items") or []
        seat_areas: list[dict[str, Any]] = []
        seat_boards: list[dict[str, Any]] = []
        for item in items:
            if item.get("sbord"):
                seat_boards.append(item.get("sbord"))
            seat_areas.extend(item.get("seatArea") or [])
        seat_records = self.build_seat_records_from_payload(payload, site_no, scn_ymd, scns_no, scn_sseq)

        return {
            "provider": "CGV",
            "site_no": payload.get("siteNo"),
            "site_name": payload.get("siteNm"),
            "screening_date": payload.get("scnYmd"),
            "screen_no": payload.get("scnsNo"),
            "screen_name": payload.get("scnsNm"),
            "seat_board_count": len(seat_boards),
            "seat_area_count": len(seat_areas),
            "seat_count": len(seat_records),
            "remaining_seat_count": sum(1 for seat in seat_records if seat.get("normalized_status") == "available"),
            "seat_boards": seat_boards,
            "seat_areas": seat_areas,
        }

    @staticmethod
    def _extract_seat_boards(payload: dict[str, Any]) -> list[dict[str, Any]]:
        boards: list[dict[str, Any]] = []
        for item in payload.get("items") or []:
            board = item.get("sbord")
            if isinstance(board, dict):
                boards.append(board)
        return boards

    @staticmethod
    def _build_zone_boxes(payload: dict[str, Any], seats: list[dict[str, Any]]) -> list[dict[str, Any]]:
        boxes: list[dict[str, Any]] = []
        for item in payload.get("items") or []:
            for area in item.get("seatArea") or []:
                if not isinstance(area, dict):
                    continue
                x1 = _coord(area, "xcoordStartVal", "xcoordStrtVal", "x1")
                y1 = _coord(area, "ycoordStartVal", "ycoordStrtVal", "y1")
                x2 = _coord(area, "xcoordEndVal", "xcoordEndVal", "x2")
                y2 = _coord(area, "ycoordEndVal", "ycoordEndVal", "y2")
                label = _first_text(area, "szoneNm", "seatAreaNm", "areaNm", "name")
                if x1 is None or y1 is None:
                    continue
                boxes.append(
                    {
                        "label": label,
                        "x": x1,
                        "y": y1,
                        "w": _dimension(x1, x2),
                        "h": _dimension(y1, y2),
                        "providerMeta": area,
                    }
                )

        if boxes:
            return boxes

        by_zone: dict[str, list[dict[str, Any]]] = {}
        for seat in seats:
            zone = _text(seat.get("seat_zone_name") or seat.get("seat_zone_kind_code"))
            if zone:
                by_zone.setdefault(zone, []).append(seat)
        for zone, zone_seats in by_zone.items():
            xs = [seat.get("x") for seat in zone_seats if isinstance(seat.get("x"), (int, float))]
            ys = [seat.get("y") for seat in zone_seats if isinstance(seat.get("y"), (int, float))]
            x2s = [
                seat.get("x") + (seat.get("width") or 0)
                for seat in zone_seats
                if isinstance(seat.get("x"), (int, float)) and isinstance(seat.get("width"), (int, float))
            ]
            y2s = [
                seat.get("y") + (seat.get("height") or 0)
                for seat in zone_seats
                if isinstance(seat.get("y"), (int, float)) and isinstance(seat.get("height"), (int, float))
            ]
            if not xs or not ys or not x2s or not y2s:
                continue
            min_x = min(xs)
            min_y = min(ys)
            boxes.append({"label": zone, "x": min_x, "y": min_y, "w": max(x2s) - min_x, "h": max(y2s) - min_y})
        return boxes

    def collect_bundle(
        self,
        site_no: str,
        mov_no: str,
        scn_ymd: str | None = None,
        scns_no: str | None = None,
        scn_sseq: str | None = None,
    ) -> dict[str, Any]:
        movies = self.build_movie_records()
        attributes = self.build_attribute_records()
        regions = self.build_region_records()
        sites = self.build_site_records()
        dates = self.build_date_records(site_no=site_no, mov_no=mov_no)

        effective_date = scn_ymd or (dates[0]["screening_date"] if dates else "")
        schedules = self.build_schedule_records(site_no=site_no, scn_ymd=effective_date, mov_no=mov_no) if effective_date else []

        effective_scns_no = scns_no
        effective_sseq = scn_sseq
        if schedules and (not effective_scns_no or not effective_sseq):
            effective_scns_no = effective_scns_no or str(schedules[0]["screen_no"] or "")
            effective_sseq = effective_sseq or str(schedules[0]["screen_sequence"] or "")

        seat_records: list[dict[str, Any]] = []
        seat_summary: dict[str, Any] | None = None
        if effective_date and effective_scns_no and effective_sseq:
            seat_records = self.build_seat_records(
                site_no=site_no,
                scn_ymd=effective_date,
                scns_no=effective_scns_no,
                scn_sseq=effective_sseq,
            )
            seat_summary = self.summarize_seat_map(
                site_no=site_no,
                scn_ymd=effective_date,
                scns_no=effective_scns_no,
                scn_sseq=effective_sseq,
            )

        return {
            "movie_count": len(movies),
            "attribute_count": len(attributes),
            "region_count": len(regions),
            "site_count": len(sites),
            "date_count": len(dates),
            "schedule_count": len(schedules),
            "seat_count": len(seat_records),
            "movies": movies,
            "attributes": attributes,
            "regions": regions,
            "sites": sites,
            "dates": dates,
            "schedules": schedules,
            "seat_records": seat_records,
            "seat_summary": seat_summary,
        }
