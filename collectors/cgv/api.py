from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import time
from pathlib import Path
from typing import Any
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen


API_BASE = "https://api.cgv.co.kr"
PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ENV_PATH = PROJECT_ROOT / ".env"
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


def parse_env_file(path: Path = DEFAULT_ENV_PATH) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def load_cgv_api_secret(env_path: Path = DEFAULT_ENV_PATH) -> str:
    return os.environ.get("CGV_API_SECRET", parse_env_file(env_path).get("CGV_API_SECRET", ""))


class CgvApiClient:
    def __init__(
        self,
        api_base: str = API_BASE,
        api_secret: str | None = None,
        headers: dict[str, str] | None = None,
    ) -> None:
        self.api_base = api_base
        self.api_secret = api_secret or load_cgv_api_secret()
        if not self.api_secret:
            raise RuntimeError("CGV_API_SECRET 환경변수 또는 루트 .env 값이 필요함")
        self.headers = {**DEFAULT_HEADERS, **(headers or {})}

    def _build_signature(self, pathname: str, body: str, timestamp: str) -> str:
        payload = f"{timestamp}|{pathname}|{body}"
        digest = hmac.new(
            self.api_secret.encode("utf-8"),
            payload.encode("utf-8"),
            hashlib.sha256,
        ).digest()
        return base64.b64encode(digest).decode("ascii")

    def request(
        self,
        path: str,
        params: dict[str, Any] | None = None,
        method: str = "GET",
        body_obj: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        if params:
            query = urlencode([(k, v) for k, v in params.items() if v is not None])
            url = f"{self.api_base}{path}?{query}"
        else:
            url = f"{self.api_base}{path}"

        body = ""
        data = None
        headers = self.headers.copy()

        if body_obj is not None:
            body = json.dumps(body_obj, ensure_ascii=False, separators=(",", ":"))
            data = body.encode("utf-8")
            headers["Content-Type"] = "application/json"

        pathname = urlparse(url).path
        timestamp = str(int(time.time()))
        headers["X-TIMESTAMP"] = timestamp
        headers["X-SIGNATURE"] = self._build_signature(pathname, body, timestamp)

        request = Request(url, headers=headers, method=method, data=data)
        with urlopen(request, timeout=30) as response:
            payload = response.read().decode("utf-8", errors="replace")
        return json.loads(payload)

    def fetch_movies(self, co_cd: str = "A420", mov_nm: str = "", div: str = "", attr_cd: str = "") -> dict[str, Any]:
        return self.request(
            "/cnm/atkt/searchAtktTopPostrList",
            params={"coCd": co_cd, "movNm": mov_nm, "div": div, "attrCd": attr_cd},
        )

    def fetch_attributes(self, co_cd: str = "A420") -> dict[str, Any]:
        return self.request(
            "/cnm/atkt/searchAtktTopPostrAttrList",
            params={"coCd": co_cd},
        )

    def fetch_regions_and_sites(self, co_cd: str = "A420") -> dict[str, Any]:
        return self.request(
            "/cnm/site/searchAllRegionAndSite",
            params={"coCd": co_cd},
        )

    def fetch_dates_by_movie(
        self,
        site_no: str,
        mov_no: str,
        co_cd: str = "A420",
        div: str = "",
        attr_cd: str = "",
    ) -> dict[str, Any]:
        return self.request(
            "/cnm/atkt/searchSiteScnscYmdListByMov",
            params={
                "coCd": co_cd,
                "siteNo": site_no,
                "movNo": mov_no,
                "div": div,
                "attrCd": attr_cd,
            },
        )

    def fetch_schedules_by_movie(
        self,
        site_no: str,
        scn_ymd: str,
        mov_no: str,
        co_cd: str = "A420",
        scns_no: str = "",
        scn_sseq: str = "",
        prod_no: str = "",
        rtctl_scop_cd: str = "08",
        sals_tzn_cd: str = "",
        tcscns_grad_cd: str = "",
        sascns_grad_cd: str = "",
        cust_no: str = "",
    ) -> dict[str, Any]:
        return self.request(
            "/cnm/atkt/searchSchByMov",
            params={
                "coCd": co_cd,
                "siteNo": site_no,
                "scnYmd": scn_ymd,
                "scnsNo": scns_no,
                "scnSseq": scn_sseq,
                "movNo": mov_no,
                "prodNo": prod_no,
                "rtctlScopCd": rtctl_scop_cd,
                "salsTznCd": sals_tzn_cd,
                "tcscnsGradCd": tcscns_grad_cd,
                "sascnsGradCd": sascns_grad_cd,
                "custNo": cust_no,
            },
        )

    def fetch_seats(
        self,
        site_no: str,
        scn_ymd: str,
        scns_no: str,
        scn_sseq: str,
        co_cd: str = "A420",
        seat_area_no: str = "",
        cusgd_cd: str = "",
        cust_no: str = "",
    ) -> dict[str, Any]:
        return self.request(
            "/cnm/atkt/searchIfSeatData",
            params={
                "coCd": co_cd,
                "siteNo": site_no,
                "scnYmd": scn_ymd,
                "scnsNo": scns_no,
                "scnSseq": scn_sseq,
                "seatAreaNo": seat_area_no,
                "cusgdCd": cusgd_cd,
                "custNo": cust_no,
            },
        )
