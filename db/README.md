# Database

## Canonical schema contract

- `SCHEMA_CONTRACT.md`: DB 네이밍, provider code, table 역할, stable key, 검색/추천 기준을 고정해둔 기준 문서.
- `migrations/`: 실제 TiDB/MySQL DDL의 기준 위치.

새 환경에서 작업을 이어갈 때는 먼저 `SCHEMA_CONTRACT.md`를 읽고 테이블/컬럼명을 맞춘다.

TiDB/MySQL 관련 스키마, seed, 샘플 데이터의 소유 영역이다.

## 팀원 DB 적용법

각자 DB 이름은 달라도 된다. 예를 들어 `daboyeo_lsh`, `daboyeo_ksg`, `daboyeo_kmh`처럼 나눠도 된다.
단, 내부 테이블명과 컬럼 구조는 반드시 `migrations/` 기준으로 같아야 한다.

1. 각자 로컬 `.env`에 TiDB 접속 정보를 넣는다.

```env
TIDB_HOST=your-db-host
TIDB_PORT=4000
TIDB_USER=your-db-user
TIDB_PASSWORD=your-db-password
TIDB_DATABASE=daboyeo_lsh
TIDB_SSL=true
DATABASE_URL=mysql://your-db-user:your-db-password@your-db-host:4000/daboyeo_lsh
```

2. 적용될 migration 목록을 먼저 확인한다. 이 명령은 DB를 변경하지 않는다.

```powershell
python scripts\db\apply_migrations.py --dry-run
```

현재 필수 목록은 아래 5개다.

```text
001_init_tidb_schema.sql
002_showtime_search_metrics.sql
003_raw_object_keys.sql
004_anonymous_recommendations.sql
005_collection_contract_extensions.sql
```

3. migration을 실제 DB에 적용한다.

```powershell
python scripts\db\apply_migrations.py
```

4. 적용된 테이블과 migration 버전을 확인한다.

```powershell
python scripts\db\inspect_tidb_schema.py
```

출력의 `migrations=`에 `001,002,003,004,005`가 모두 있어야 한다.

5. 팀 표준 스키마가 맞는지 검증한다.

```powershell
python scripts\verify\verify_tidb_ingest.py
```

`schema_table[...]` 값이 전부 `present`이고, `missing_required_tables` 또는 `missing_required_migrations`가 나오지 않아야 한다.

## 공통 테이블과 provider별 데이터

CGV, 롯데시네마, 메가박스별로 `cgv_movies`, `lotte_movies`, `megabox_movies` 같은 물리 테이블을 따로 만들지 않는다.
모든 provider 데이터는 공통 테이블에 넣고 `provider_code`, `external_*`, `raw_json`, `provider_meta_json`으로 구분한다.

표준 테이블 목록과 역할은 `SCHEMA_CONTRACT.md`와 `docs/TEAM_DB_SETUP.md`를 기준으로 본다.

## 디렉터리

- `migrations/`: 수동 TiDB migration의 canonical 위치. `scripts/db/apply_migrations.py`가 여기의 `*.sql`을 순서대로 실행한다.
- `seeds/`: 로컬 개발용 기준 데이터. 실제 계정, 토큰, 쿠키, 개인 위치 정보는 넣지 않는다.
- `samples/`: 공유 가능한 작은 샘플만 둔다. 대용량 raw JSON은 Cloudflare R2 또는 `.local/`에 둔다.

## 원칙

- TiDB에는 검색/비교에 필요한 최소 공통 컬럼을 둔다.
- 제공사별 원본 응답은 `raw_json` 또는 R2 object key로 보존한다.
- 좌석 상태는 덮어쓰기보다 `seat_snapshots` append를 기본으로 한다.
- 접속 정보, 비밀번호, R2 키는 SQL/문서에 쓰지 않는다.
