# Workspace Tree

현재 로컬 기준 파일트리와 각 폴더 책임이다.

## 전체 구조

```text
daboyeo/
├─ backend/
│  ├─ build.gradle
│  ├─ settings.gradle
│  └─ src/
│     ├─ main/
│     │  ├─ java/kr/daboyeo/backend/
│     │  │  ├─ api/
│     │  │  ├─ config/
│     │  │  ├─ domain/
│     │  │  ├─ repository/
│     │  │  ├─ service/
│     │  │  └─ DaboyeoApplication.java
│     │  └─ resources/
│     │     ├─ application.yml
│     │     └─ db/migration/
│     └─ test/java/kr/daboyeo/backend/
│
├─ frontend/
│  ├─ index.html
│  ├─ public/assets/
│  └─ src/
│     ├─ css/
│     │  ├─ base.css
│     │  ├─ components.css
│     │  └─ layout.css
│     └─ js/
│        ├─ api/
│        ├─ pages/
│        └─ main.js
│
├─ collectors/
│  ├─ cgv/
│  │  ├─ api.py
│  │  └─ collector.py
│  ├─ lotte/
│  │  ├─ api.py
│  │  └─ collector.py
│  ├─ megabox/
│  │  ├─ api.py
│  │  └─ collector.py
│  └─ common/
│     ├─ env.py
│     ├─ tidb.py
│     ├─ normalize.py
│     ├─ repository.py
│     └─ storage.py
│
├─ db/
│  ├─ migrations/
│  │  ├─ 001_init_tidb_schema.sql
│  │  ├─ 002_showtime_search_metrics.sql
│  │  └─ 003_raw_object_keys.sql
│  ├─ seeds/
│  └─ samples/
│
├─ scripts/
│  ├─ db/
│  │  ├─ apply_migrations.py
│  │  └─ inspect_tidb_schema.py
│  ├─ ingest/
│  │  └─ collect_all_to_tidb.py
│  ├─ storage/
│  │  ├─ test_r2_connection.py
│  │  └─ inspect_r2_objects.py
│  ├─ verify/
│  │  └─ verify_tidb_ingest.py
│  ├─ cgv_collector_demo.py
│  ├─ lotte_collector_demo.py
│  └─ megabox_collector_demo.py
│
├─ docs/
│  ├─ db/
│  │  └─ schema-notes.md
│  ├─ seat-map-data/
│  ├─ seat-map-demo.html
│  └─ WORKSPACE_TREE.md
│
├─ .local/
├─ .env.example
├─ .gitignore
├─ AGENTS.md
├─ ERROR_LOG.md
├─ README.md
├─ SEED.yaml
├─ STATE.md
└─ WORKSPACE_CONTEXT.toml
```

## 폴더 책임

### `backend/`

Spring Boot 기반 API 서버 영역이다.

- TiDB/MySQL에 저장된 영화, 극장, 상영 시간, 가격, 좌석 요약 데이터를 조회한다.
- 프론트엔드에 REST API를 제공한다.
- 수집기는 여기로 옮기지 않고 `collectors/`와 `scripts/`를 우선 유지한다.
- `backend/src/main/resources/db/migration/`은 나중에 Flyway로 전환할 때 쓸 후보 위치다.

### `frontend/`

사용자 화면 영역이다.

- 바닐라 HTML/CSS/JavaScript를 기본으로 한다.
- 검색, 필터, 가격 비교, 좌석 요약, 예매 링크 이동 UI를 담당한다.
- 현재 원칙상 큰 프레임워크나 무거운 외부 라이브러리는 기본값으로 두지 않는다.

### `collectors/`

CGV, 롯데시네마, 메가박스 원본 데이터를 수집하는 Python 영역이다.

- `cgv/`, `lotte/`, `megabox/`: 극장사별 API 호출과 provider별 record build.
- `common/env.py`: 영화관 계정/env 로딩.
- `common/tidb.py`: TiDB Cloud/MySQL 연결 helper.
- `common/normalize.py`: 날짜, 시간, 숫자, 좌석 상태, 상영 키 같은 공통 변환.
- `common/repository.py`: TiDB insert/upsert helper.
- `common/storage.py`: Cloudflare R2/S3 호환 object storage helper.

### `db/`

DB 스키마와 공유 가능한 DB 자료의 기준 위치다.

- `migrations/`: 현재 수동 TiDB migration의 canonical 위치.
- `seeds/`: 로컬 개발용 최소 seed 데이터.
- `samples/`: 공유 가능한 작은 샘플 데이터.
- 실제 대용량 raw JSON, DB dump, 개인 데이터는 여기 커밋하지 않는다.

### `scripts/`

로컬 실행과 운영 보조 스크립트 영역이다.

- `scripts/db/`: migration 적용, schema inspection.
- `scripts/ingest/`: 수집 결과를 TiDB/R2 파이프라인으로 넘기는 진입점.
- `scripts/storage/`: Cloudflare R2 연결 확인, object 목록 확인.
- `scripts/verify/`: 적재 후 row count, 중복, snapshot append 검증.
- `*_collector_demo.py`: provider별 수집기 단독 실행/검증.

### `docs/`

프로젝트 문서 영역이다.

- `docs/db/`: DB/R2 구조 메모.
- `docs/seat-map-data/`: 공유 가능한 좌석 배치 데모 데이터.
- `docs/seat-map-demo.html`: 좌석 배치 데모 화면.
- `docs/WORKSPACE_TREE.md`: 현재 파일트리와 책임 설명.

### `.local/`

로컬 전용 작업 공간이다. Git에 올리지 않는다.

추천 용도:

- 임시 raw API 응답
- 로컬 로그
- DB dump
- R2 업로드 전 임시 gzip JSON
- 스크린샷, 캡처, scratch 파일

### 루트 파일

- `.env.example`: 공유 가능한 환경변수 예시. 실제 secret 없음.
- `.env`: 실제 secret. Git 제외.
- `.gitignore`: secret, local artifact, cache 제외 정책.
- `AGENTS.md`: 이 workspace의 Codex 작업 규칙.
- `ERROR_LOG.md`: 실행/검증 오류 기록.
- `README.md`: 프로젝트 개요.
- `SEED.yaml`: 작업 seed/계약이 필요할 때 쓰는 기준 파일.
- `STATE.md`: 현재 작업 상태와 orchestration 기록.
- `WORKSPACE_CONTEXT.toml`: workspace 규칙 생성/유지용 context.

## 데이터 흐름

```text
collectors/*
  -> collectors/common/normalize.py
  -> collectors/common/repository.py
  -> TiDB

collectors/*
  -> collectors/common/storage.py
  -> Cloudflare R2
  -> TiDB raw_object_key

TiDB
  -> backend API
  -> frontend
```

## 저장소에 올리지 않는 것

```text
.env
.local/
db/local/
db/samples/*.json
db/samples/*.csv
*.sqlite
*.sqlite3
*.log
```

## 현재 다음 작업 후보

1. `python scripts\db\apply_migrations.py`로 `003_raw_object_keys.sql`을 TiDB에 적용.
2. `scripts\ingest\collect_all_to_tidb.py`를 실제 upsert/append 로직으로 확장.
3. R2 bucket/env 설정 후 `scripts\storage\test_r2_connection.py`로 연결 확인.
4. 롯데/메가박스부터 실제 TiDB 적재 검증.
5. `CGV_API_SECRET` 설정 후 CGV live 수집 재검증.
