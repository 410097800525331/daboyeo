import math
import json
from pathlib import Path
from typing import List, Dict, Any

THEATER_DB_PATH = Path(__file__).parent.parent / "frontend/src/map/theaters.json"

def calculate_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """
    Haversine 공식을 이용한 두 지점 간의 거리 계산 (km 반환)
    """
    R = 6371  # 지구 반지름 (km)
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (math.sin(dlat / 2) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2) ** 2)
    return 2 * R * math.asin(math.sqrt(a))

def get_nearest_theaters(lat: float, lng: float, limit: int = 5) -> List[Dict[str, Any]]:
    """
    현재 위치에서 가까운 극장 목록을 반환
    """
    if not THEATER_DB_PATH.exists():
        return []

    with open(THEATER_DB_PATH, "r", encoding="utf-8") as f:
        theaters = json.load(f)

    for t in theaters:
        # 각 극장과 현재 위치 사이의 거리 계산
        t['distance'] = calculate_distance(lat, lng, t['lat'], t['lng'])

    # 거리순으로 정렬하여 제한된 수만큼 반환
    return sorted(theaters, key=lambda x: x['distance'])[:limit]

def format_theater_results(results: List[Dict[str, Any]], theaters: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    수집된 결과에 위치 메타데이터를 통합하여 포맷팅
    """
    # 극장 정보를 딕셔너리로 변환 (빠른 조회를 위함)
    theater_map = {t['code']: t for t in theaters}

    formatted = []
    for item in results:
        # 여기서는 이미 main.py에서 매칭 작업을 수행하므로 보조적인 역할만 수행
        formatted.append(item)
    return formatted
