# DB / R2 구조 메모

## 역할 분리

- TiDB: 영화, 극장, 상영 시간, 가격, 좌석 요약처럼 검색과 비교에 필요한 데이터.
- Cloudflare R2: 원본 API 응답, 대용량 좌석 JSON, 수집 실행 archive.

## 현재 흐름

```text
collector
  -> normalize
  -> TiDB upsert / seat snapshot append
  -> optional R2 raw archive
  -> TiDB raw_object_key 연결
```

## 파일 위치

```text
db/migrations/             SQL migration 기준 위치
collectors/common/tidb.py  TiDB 연결
collectors/common/storage.py R2 연결
collectors/common/normalize.py 공통 변환
collectors/common/repository.py DB 쓰기 helper
scripts/db/                migration 실행/검사
scripts/ingest/            수집 적재 진입점
scripts/storage/           R2 점검
scripts/verify/            적재 검증
```

## 다음 구현 순서

1. `scripts/db/apply_migrations.py`로 003까지 TiDB 반영.
2. 롯데/메가박스 수집 결과를 `movies`, `theaters`, `showtimes`에 upsert.
3. 좌석은 `seat_snapshots`, `seat_snapshot_items`에 append.
4. raw JSON은 R2에 gzip 저장하고 TiDB에 `raw_object_key`를 남김.
