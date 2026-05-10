import json
import os
import requests
import time
from pathlib import Path

# 카카오 API 키 (REST API KEY)
KAKAO_API_KEY = os.getenv("KAKAO_REST_API_KEY")
if not KAKAO_API_KEY:
    raise RuntimeError("KAKAO_REST_API_KEY 환경 변수를 설정해주세요.")

# 초정밀 제외 키워드 리스트 (부동산, 노래방, 충전소 등 원천 차단)
JUNK_KEYWORDS = [
    "부동산", "인테리어", "학원", "카페", "커피", "빌라", "아파트", "원룸",
    "주차장", "전기차", "충전소", "노래방", "PC방", "식당", "맛집", "스토어",
    "마트", "ATM", "현금인출기", "편의점", "은행", "지점", "관리실", "안내소",
    "대기실", "매점", "키오스크", "무인", "창고", "센터", "오피스", "빌딩", "입구"
]

def is_valid_theater(item):
    """카테고리 정보와 명칭을 결합한 초정밀 영화관 판별 로직"""
    name = item.get("place_name", "")
    category = item.get("category_name", "")
    clean_name = name.replace(" ", "")

    # 1. 카카오 카테고리 기반 엄격 필터링
    # '영화관' 혹은 '영화'가 카테고리에 명확히 포함되어야 함
    if "영화관" not in category and "문화,예술 > 영화" not in category:
        return False

    # 2. 강력 제외 키워드 검사 (건물 내 부속 시설물 차단)
    for word in JUNK_KEYWORDS:
        if word in clean_name:
            return False

    # 3. 진짜 영화관 이름 패턴 확인
    valid_patterns = ["CGV", "LOTTE", "MEGA", "롯데시네마", "메가박스", "시네마", "CINEMA", "극장", "씨네"]
    has_valid_pattern = any(p.upper() in clean_name.upper() for p in valid_patterns)

    # 4. 예외 케이스: 영화관 1층, 입구 등의 키워드가 있으면 부속물이므로 차단
    if any(k in clean_name for k in ["지하", "1층", "2층", "3층", "입구", "출구", "기둥"]):
        if not (name.startswith("CGV") or name.startswith("메가박스") or name.startswith("롯데시네마")):
            return False

    return has_valid_pattern or ("영화 > 영화관" in category)

def search_theater_with_pagination(query, x, y):
    all_docs = []
    headers = {"Authorization": f"KakaoAK {KAKAO_API_KEY}"}
    for page in range(1, 4):
        url = "https://dapi.kakao.com/v2/local/search/keyword.json"
        params = {"query": query, "x": x, "y": y, "radius": 20000, "size": 15, "page": page, "sort": "distance"}
        try:
            res = requests.get(url, headers=headers, params=params, timeout=10)
            if res.status_code == 200:
                data = res.json()
                documents = data.get("documents", [])
                all_docs.extend(documents)
                if data.get("meta", {}).get("is_end"): break
            else: break
        except: break
        time.sleep(0.1)
    return all_docs

# 전국 주요 거점
regions = [
    {"name": "강남", "x": "127.0276", "y": "37.4979"}, {"name": "강동", "x": "127.1237", "y": "37.5301"},
    {"name": "강서", "x": "126.8495", "y": "37.5509"}, {"name": "노원", "x": "127.0610", "y": "37.6541"},
    {"name": "은평", "x": "126.9291", "y": "37.6027"}, {"name": "영등포", "x": "126.8966", "y": "37.5263"},
    {"name": "인천", "x": "126.7052", "y": "37.4563"}, {"name": "수원", "x": "127.0089", "y": "37.2636"},
    {"name": "성남", "x": "127.1267", "y": "37.4200"}, {"name": "고양", "x": "126.8320", "y": "37.6583"},
    {"name": "용인", "x": "127.1777", "y": "37.2410"}, {"name": "안산", "x": "126.8308", "y": "37.3218"},
    {"name": "남양주", "x": "127.2165", "y": "37.6360"}, {"name": "안양", "x": "126.9234", "y": "37.3942"},
    {"name": "춘천", "x": "127.7300", "y": "37.8813"}, {"name": "대전", "x": "127.3845", "y": "36.3504"},
    {"name": "광주", "x": "126.8526", "y": "35.1595"}, {"name": "전주", "x": "127.1480", "y": "35.8242"},
    {"name": "부산", "x": "129.0756", "y": "35.1796"}, {"name": "대구", "x": "128.6014", "y": "35.8714"},
    {"name": "울산", "x": "129.3114", "y": "35.5384"}, {"name": "제주", "x": "126.5312", "y": "33.4996"}
]

search_keywords = ["CGV", "롯데시네마", "메가박스", "영화관"]
all_theaters = {}
seen_coords = set() # 좌표 기반 중복 제거용

print("전국 영화관 초정밀 필터링 수집을 시작합니다...")

for region in regions:
    print(f"[{region['name']}] 지역 수집 중...", end=" ", flush=True)
    count_before = len(all_theaters)

    for keyword in search_keywords:
        results = search_theater_with_pagination(keyword, region["x"], region["y"])
        for place in results:
            name = place["place_name"]

            # 1. 초정밀 필터링 (카테고리 + 명칭)
            if is_valid_theater(place):
                # 2. 좌표 기반 중복 제거 (소수점 3자리까지 비교)
                lat, lng = float(place["y"]), float(place["x"])
                coord_key = (round(lat, 3), round(lng, 3))

                if coord_key not in seen_coords and place["id"] not in all_theaters:
                    seen_coords.add(coord_key)
                    provider = "ETC"
                    if "CGV" in name.upper(): provider = "CGV"
                    elif "롯데시네마" in name or "LOTTE" in name.upper(): provider = "LOTTE"
                    elif "메가박스" in name or "MEGA" in name.upper(): provider = "MEGA"

                    all_theaters[place["id"]] = {
                        "provider": provider, "code": "9999", "name": name,
                        "lat": lat, "lng": lng,
                        "address": place["road_address_name"] or place["address_name"],
                        "phone": place["phone"]
                    }

    print(f"매칭 완료: {len(all_theaters) - count_before}개")
    time.sleep(0.3)

# 결과 저장
output_path = Path(__file__).parent.parent / "frontend/src/map/theaters.json"
with open(output_path, "w", encoding="utf-8") as f:
    json.dump(list(all_theaters.values()), f, indent=4, ensure_ascii=False)

print(f"\n수집 완료! 총 {len(all_theaters)}개의 정제된 데이터가 저장되었습니다.")
