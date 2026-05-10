# Team DB Setup

이 문서는 팀원이 각자 다른 DB를 쓰더라도 백엔드와 수집기가 같은 스키마를 바라보도록 맞추는 기준이다.

## 팀원 공유용 요약

- 각자 DB 이름과 접속 계정은 달라도 된다.
- 대신 `db/migrations`의 `001`부터 `005`까지는 같은 순서로 모두 적용해야 한다.
- 내부 테이블명과 컬럼명은 개인 취향으로 바꾸지 않는다.
- 실제 수집 데이터는 아직 공유 필수가 아니다. 우선 스키마부터 통일한다.
- 세팅 후에는 `scripts\db\inspect_tidb_schema.py`와 `scripts\verify\verify_tidb_ingest.py`로 확인한다.
- 비밀번호, 토큰, API 키는 문서나 커밋에 올리지 않는다.

팀원에게는 이 파일과 `db/SCHEMA_CONTRACT.md`, `db/migrations/*.sql`을 기준으로 맞추라고 안내하면 된다.

## 핵심 원칙

- DB 이름은 각자 달라도 된다. 예: `daboyeo_lsh`, `daboyeo_ksg`, `daboyeo_kmh`
- DB 구조는 반드시 `db/migrations/*.sql` 기준으로 맞춘다.
- 내부 테이블명, 컬럼명, 인덱스명은 팀 전원이 동일해야 한다.
- 실제 비밀번호, 토큰, API 키는 repo에 커밋하지 않는다.
- 각자 로컬 `.env`에만 접속 정보를 둔다.
- 이미 공유되거나 적용된 migration 파일은 수정하지 않는다.
- 구조 변경이 필요하면 다음 번호의 새 migration 파일을 추가한다. 예: `006_add_xxx.sql`

## 현재 필수 migration

현재 팀원이 맞춰야 하는 migration은 아래 5개다.

```text
001_init_tidb_schema.sql
002_showtime_search_metrics.sql
003_raw_object_keys.sql
004_anonymous_recommendations.sql
005_collection_contract_extensions.sql
```

적용 여부는 `schema_migrations` 테이블과 검사 스크립트로 확인한다.

## 개인 DB 이름 예시

포트폴리오용으로 각자 DB를 분리하려면 DB 이름만 다르게 잡으면 된다.

```text
daboyeo_lsh
daboyeo_ksg
daboyeo_kmh
daboyeo_jyh
```

중요한 건 이름이 아니라 테이블 구조다. 전원이 같은 migration을 같은 순서로 적용해야 한다.

## 로컬 .env 예시

아래 값은 예시다. 실제 host, user, password는 각자 발급받은 값으로 바꾼다.

```env
# TiDB Cloud / MySQL-compatible Python collectors
TIDB_HOST=your-db-host
TIDB_PORT=4000
TIDB_USER=your-db-user
TIDB_PASSWORD=your-db-password
TIDB_DATABASE=daboyeo_lsh
TIDB_SSL=true

# Spring backend
# Spring also reads the TIDB_* keys above.
DABOYEO_FLYWAY_ENABLED=false
```

다른 팀원은 `daboyeo_lsh` 부분만 자기 DB 이름으로 바꾸면 된다.

## 처음 세팅 순서

1. 개인 DB를 만든다.
2. `.env.example`을 참고해서 로컬 `.env`를 만든다.
3. migration 목록을 먼저 확인한다.

```powershell
python scripts\db\apply_migrations.py --dry-run
```

4. migration을 적용한다.

```powershell
python scripts\db\apply_migrations.py
```

5. 적용된 스키마를 확인한다.

```powershell
python scripts\db\inspect_tidb_schema.py
```

출력의 `migrations=` 값에 `001,002,003,004,005`가 모두 있어야 한다.

6. 팀 표준 테이블과 핵심 적재 테이블 상태를 확인한다.

```powershell
python scripts\verify\verify_tidb_ingest.py
```

`schema_table[...]` 값이 전부 `present`이고, `missing_required_tables` 또는 `missing_required_migrations`가 나오지 않아야 한다.

## 공통 테이블과 개별 데이터 구분

현재 DB는 극장사별로 `cgv_movies`, `lotte_movies`, `megabox_movies` 같은 개별 테이블을 만들지 않는다. 대신 공통 테이블 하나에 여러 provider 데이터를 넣고, `provider_code`로 구분한다.

| 구분 | 대상 | 설명 |
| --- | --- | --- |
| 메타 테이블 | `schema_migrations`, `providers` | migration 버전과 provider 기준값 |
| 수집 실행 추적 | `collection_runs` | 언제 어떤 수집기가 돌았고 몇 건을 받았는지 기록 |
| 공통 수집 테이블 | `movies`, `theaters`, `screens`, `showtimes`, `showtime_prices`, `movie_tags` | CGV, 롯데시네마, 메가박스 데이터를 같은 테이블에 저장하고 `provider_code`로 구분 |
| 영화 통합 매핑 | `canonical_movies`, `movie_provider_links` | 3사에서 서로 다른 movie id를 같은 영화로 묶기 위한 연결 테이블 |
| 좌석 레이아웃 테이블 | `seat_layouts`, `seat_layout_items` | 상영마다 반복되는 좌석 배치 구조를 스냅샷과 분리 |
| 좌석 스냅샷 테이블 | `seat_snapshots`, `seat_snapshot_items` | 상영 회차별 좌석 상태를 시간순으로 append |
| provider 상태 코드 매핑 | `provider_status_codes` | CGV/롯데/메가박스 원본 상태 코드를 `available`, `sold`, `unavailable`, `special`로 매핑 |
| 원본 payload index | `provider_raw_payloads` | R2 또는 row-level raw JSON 위치를 추적하는 원본 응답 인덱스 |
| 추천 기능 테이블 | `recommendation_profiles`, `recommendation_runs`, `recommendation_feedback` | AI 추천 흐름에서 쓰는 익명 프로필, 추천 실행, 피드백 |
| provider별 개별 원본 데이터 | `raw_json`, `raw_summary_json`, `provider_meta_json`, `external_*` 컬럼 | 테이블을 나누지 않고 공통 테이블 안의 컬럼으로 보존 |

즉, 팀원이 통일해야 하는 것은 DB 이름이 아니라 위 테이블명과 컬럼 구조다. provider별 차이는 테이블명으로 나누지 않고 `provider_code`, `external_movie_id`, `external_theater_id`, `external_showtime_key`, `raw_json` 같은 컬럼으로 흡수한다.

## 표준 내부 테이블명

아래 테이블명은 현재 백엔드, 수집기, 검사 스크립트가 기대하는 표준 이름이다. DB 이름은 달라도 이 테이블명은 바꾸면 안 된다.

| table | role |
| --- | --- |
| `schema_migrations` | 적용된 DB migration 버전 기록 |
| `providers` | CGV, 롯데시네마, 메가박스 같은 상영 제공자 기준 정보 |
| `collection_runs` | 수집 실행 단위, 성공/실패, 응답 개수, 403 여부 기록 |
| `movies` | 영화 기본 정보와 극장사별 원본 movie id 매핑 |
| `canonical_movies` | 여러 provider의 같은 영화를 하나로 묶는 대표 영화 후보 |
| `movie_provider_links` | `canonical_movies`와 provider별 `movies` row 연결 |
| `theaters` | 극장 기본 정보와 극장사별 원본 theater id 매핑 |
| `screens` | 상영관 정보와 극장사별 원본 screen id 매핑 |
| `showtimes` | 검색, 가격 비교, 추천의 중심이 되는 상영 시간 데이터 |
| `showtime_prices` | 상영별 가격 옵션 데이터. 별도 `showtime_price_options` 테이블을 만들지 않고 이 이름으로 통일 |
| `seat_layouts` | 극장/상영관별 안정적인 좌석 배치 레이아웃 |
| `seat_layout_items` | 좌석 배치 안의 개별 좌석 좌표와 구역 정보 |
| `seat_snapshots` | 특정 시점의 좌석 상태 스냅샷 |
| `seat_snapshot_items` | 좌석 스냅샷 안의 개별 좌석 상태 |
| `provider_status_codes` | provider 원본 좌석/예매 상태 코드와 정규화 상태 매핑 |
| `provider_raw_payloads` | R2 object 또는 row raw JSON을 찾기 위한 원본 payload index |
| `movie_tags` | 추천 품질을 높이기 위한 영화 태그 |
| `recommendation_profiles` | 익명 추천 사용자 프로필 |
| `recommendation_runs` | 추천 실행 기록 |
| `recommendation_feedback` | 추천 결과에 대한 익명 피드백 |

테이블명을 개인 취향으로 바꾸면 백엔드 repository, 수집기 ingest 코드, 검증 스크립트가 같은 DB를 보지 못한다. 이름 변경이 정말 필요하면 단순 DB 수정이 아니라 migration, 백엔드 코드, 수집기 코드, 문서를 같이 바꾸는 별도 작업으로 처리한다.

## 테이블 단위 확인

특정 테이블 컬럼을 확인하고 싶으면 `--table`을 붙인다.

```powershell
python scripts\db\inspect_tidb_schema.py --table showtimes
python scripts\db\inspect_tidb_schema.py --table collection_runs
python scripts\db\inspect_tidb_schema.py --table provider_status_codes
python scripts\db\inspect_tidb_schema.py --table seat_layouts
python scripts\db\inspect_tidb_schema.py --table recommendation_runs
```

## 스키마와 데이터는 다르다

이 문서의 목표는 스키마 통일이다.

- 스키마 통일: 테이블, 컬럼, 인덱스, migration 버전이 같음
- 데이터 통일: 영화, 극장, 상영시간, 태그 데이터까지 같음

백엔드 API 개발만 맞추려면 스키마 통일이 먼저다. AI 추천이나 검색 결과까지 팀원끼리 똑같이 맞춰야 하면 별도의 seed 또는 수집 데이터 공유가 필요하다.

## 데이터 적재 기준

수집 데이터를 넣을 때도 같은 스키마를 기준으로 동작해야 한다.

- `collection_runs`는 수집 실행 기록이다. 에러, 403, 응답 개수, 결과 개수는 여기에 남긴다.
- `movies`, `theaters`, `screens`, `showtimes`는 수집 데이터의 공통 검색/비교 기준이다.
- `showtime_prices`는 성인/청소년/특별관/좌석등급/수수료 같은 가격 옵션 확장용 테이블이다.
- `movie_tags`는 AI 추천 품질을 높이는 태그 데이터다.
- `canonical_movies`, `movie_provider_links`는 CGV/롯데/메가박스의 서로 다른 영화 ID를 같은 영화로 묶기 위한 테이블이다.
- `seat_layouts`, `seat_layout_items`는 변하지 않는 좌석 배치 구조를 담고, `seat_snapshots`는 특정 시점의 판매 상태만 담는다.
- `seat_snapshots`, `seat_snapshot_items`는 좌석 상태 스냅샷이다.
- `provider_status_codes`는 provider 원본 상태 코드를 정규화 상태로 매핑한다.
- `provider_raw_payloads`는 큰 원본 응답이나 R2 object를 추적하는 index다.
- `recommendation_profiles`, `recommendation_runs`, `recommendation_feedback`는 익명 추천 흐름 기록이다.

각자 DB를 쓰면 데이터 양은 달라질 수 있다. 그래서 API 테스트 결과가 다르면 먼저 `showtimes`와 `movie_tags` 데이터가 충분히 들어갔는지 확인한다.

## 자정 넘김 상영 시간 규칙

CGV와 롯데시네마는 `2400`, `2609`, `24:17`처럼 자정을 넘긴 시간을 줄 수 있다. 이 값은 DB에서 아래처럼 처리한다.

- `start_time_raw`, `end_time_raw`는 provider 원본 문자열 그대로 저장한다.
- `starts_at`, `ends_at`는 정렬과 필터를 위해 `DATETIME`으로 정규화한다.
- `2400`은 다음날 `00:00`이다.
- `2609`는 다음날 `02:09`다.
- `24:17`은 다음날 `00:17`이다.
- 종료 시간이 시작 시간보다 빠르게 계산되면 종료 시간은 다음날로 본다.

이 규칙을 지키지 않으면 심야/새벽 상영 정렬과 AI 추천 시간 필터가 틀어진다.

## 관측된 좌석 상태 코드

`provider_status_codes`에는 최신 3사 수집에서 확인한 좌석 상태 코드의 기본 매핑을 seed로 넣는다.

| provider | status_domain | provider_status_code | normalized_status | 근거 |
| --- | --- | --- | --- | --- |
| `CGV` | `seat` | `00` | `available` | CGV 좌석 144개 모두 `00`, 판매 가능 |
| `CGV` | `seat` | `01` | `sold` | 기존 정규화 로직의 판매 완료 코드 |
| `LOTTE_CINEMA` | `seat` | `0` | `sold` | 최신 샘플에서 140개, 상영 잔여석 2석과 반대 수량 |
| `LOTTE_CINEMA` | `seat` | `50` | `available` | 최신 샘플에서 2개, 상영 잔여석 2석과 일치 |
| `MEGABOX` | `seat` | `GERN_SELL` | `available` | 최신 샘플에서 102개, 상영 잔여석 102석과 일치 |
| `MEGABOX` | `seat` | `SCT04` | `unavailable` | 최신 샘플에서 14개, 잔여석에서 제외 |

새로운 provider 코드가 나오면 애플리케이션 코드에만 하드코딩하지 말고, 이 테이블에도 매핑을 추가한다.

## 구조 변경 규칙

DB 구조를 바꿀 때는 아래 순서를 지킨다.

1. 기존 migration 파일을 수정하지 않는다.
2. 다음 번호의 migration 파일을 추가한다.
3. 새 migration은 가능하면 다시 실행해도 깨지지 않게 작성한다.
4. `schema_migrations`에 새 버전을 기록한다.
5. 팀원에게 새 migration 번호와 적용 필요 여부를 공유한다.

예시:

```text
006_add_movie_runtime.sql
007_add_showtime_provider_status.sql
```

## 보안 규칙

- `.env`는 커밋하지 않는다.
- 실제 DB 비밀번호를 README, docs, issue, PR, commit message에 쓰지 않는다.
- 계정 공유가 필요하면 팀 외부에 노출되지 않는 별도 채널을 사용한다.
- 가능하면 팀원별 DB 계정을 발급한다.
- 포트폴리오 공개용 DB에는 민감한 사용자 정보나 개인 계정 토큰을 넣지 않는다.
- `provider_raw_payloads.raw_json`이나 R2 raw archive에는 쿠키, 토큰, 계정 정보를 저장하지 않는다.

## 문제 해결 체크리스트

- `migration files not found`가 나오면 repo 루트에서 명령을 실행했는지 확인한다.
- `TiDB 설정이 부족함`류 오류가 나오면 `.env`의 `TIDB_HOST`, `TIDB_USER`, `TIDB_PASSWORD`, `TIDB_DATABASE`를 확인한다.
- 백엔드나 Python 수집기 연결이 안 되면 `TIDB_*`를 확인한다.
- 추천 API 결과가 비어 있으면 `showtimes` 데이터가 있는지 먼저 확인한다.
- 새벽 상영 정렬이 이상하면 `start_time_raw`, `end_time_raw`, `starts_at`, `ends_at`를 같이 확인한다.
