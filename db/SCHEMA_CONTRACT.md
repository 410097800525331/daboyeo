# DB Schema Contract

이 파일은 daboyeo DB 네이밍과 구조의 기준 문서다.
작업 환경이 바뀌어도 테이블명, 컬럼명, provider code, upsert key를 헷갈리지 않기 위해 유지한다.

## Canonical Source

- 실제 DDL 기준: `db/migrations/*.sql`
- 사람이 읽는 네이밍 기준: `db/SCHEMA_CONTRACT.md`
- 구현 기준: `collectors/common/normalize.py`, `collectors/common/repository.py`, `scripts/ingest/collect_all_to_tidb.py`
- TiDB dialect: MySQL compatible TiDB Cloud
- charset/collation: `utf8mb4`, `utf8mb4_bin`

## Naming Rules

- 테이블명은 복수형 snake_case를 쓴다.
- 컬럼명은 snake_case를 쓴다.
- 내부 PK는 모든 주요 테이블에서 `id`를 쓴다.
- 외부 사이트 원본 ID는 `external_*_id` 또는 `external_showtime_key`로 쓴다.
- 영화관 구분은 항상 `provider_code`를 쓴다.
- 원본 JSON은 `raw_json` 또는 `raw_summary_json`에 보존한다.
- R2 원본 보관 포인터는 `raw_object_key`, `raw_object_etag`, `raw_object_size`, `raw_archived_at`를 쓴다.
- 수집 시각은 `first_collected_at`, `last_collected_at`, 수정 시각은 `updated_at`를 쓴다.
- 날짜는 `show_date`, 시작/종료 시각은 `starts_at`, `ends_at`를 쓴다.
- 표시용 원본 시간은 `start_time_raw`, `end_time_raw`를 쓴다.

## Provider Codes

| code | site | status |
|---|---|---|
| `CGV` | CGV | schema ready, ingest deferred until `CGV_API_SECRET` exists |
| `LOTTE_CINEMA` | 롯데시네마 | ingest active |
| `MEGABOX` | 메가박스 | ingest active |

Provider code는 변경하지 않는다.
특히 롯데는 `LOTTE`, `LOTTECINEMA`, `LotteCinema`가 아니라 반드시 `LOTTE_CINEMA`다.

## Core Tables

| table | role | write policy |
|---|---|---|
| `providers` | 영화관 provider seed | upsert |
| `movies` | provider별 영화 기본 정보 | upsert |
| `theaters` | provider별 극장 기본 정보 | upsert |
| `screens` | provider별 상영관 정보 | upsert |
| `showtimes` | 검색/비교의 중심 상영 정보 | upsert |
| `showtime_prices` | 가격 비교용 상세 가격 | upsert, active via bounded price ingest |
| `seat_snapshots` | 특정 시점 좌석 요약 | append |
| `seat_snapshot_items` | 특정 snapshot의 좌석별 상태 | append with snapshot |
| `movie_tags` | 감정/날씨/추천용 태그 | upsert |
| `schema_migrations` | 적용된 migration 기록 | upsert |

## Stable Keys

| table | unique key | meaning |
|---|---|---|
| `providers` | `code` | provider code |
| `movies` | `provider_code`, `external_movie_id` | 사이트별 영화 |
| `theaters` | `provider_code`, `external_theater_id` | 사이트별 극장 |
| `screens` | `provider_code`, `external_theater_id`, `external_screen_id` | 사이트별 상영관 |
| `showtimes` | `provider_code`, `external_showtime_key` | 사이트별 단일 상영 회차 |
| `showtime_prices` | `provider_code`, `external_showtime_key`, `price_key` | 상영 회차별 가격 항목 |
| `seat_snapshot_items` | `seat_snapshot_id`, `seat_key` | snapshot 안의 좌석 |
| `movie_tags` | `provider_code`, `external_movie_id`, `tag_type`, `tag_value` | 영화별 태그 |

`showtimes`는 반복 수집해도 같은 `external_showtime_key`면 row가 늘어나면 안 된다.
좌석 변화는 `seat_snapshots`에 새 row로 쌓는다.

## Main Columns

### `movies`

| column | rule |
|---|---|
| `provider_code` | `CGV`, `LOTTE_CINEMA`, `MEGABOX` 중 하나 |
| `external_movie_id` | provider 원본 영화 ID |
| `representative_movie_id` | provider가 제공하는 대표 영화 ID, 없으면 NULL |
| `title_ko` | 검색 표시용 한국어 제목 |
| `title_en` | 영어 제목, 없으면 NULL |
| `age_rating` | 원본 관람 등급 문자열 |
| `runtime_minutes` | 분 단위 러닝타임 |
| `release_date` | 개봉일 `DATE` |
| `booking_rate` | 예매율 |
| `box_office_rank` | 박스오피스/노출 순위 |
| `poster_url` | 포스터 URL |
| `raw_json` | provider 원본 영화 payload |

### `theaters`

| column | rule |
|---|---|
| `external_theater_id` | provider 원본 극장/지점 ID |
| `name` | 극장명 |
| `region_code` | provider 지역 코드 |
| `region_name` | 지역명 |
| `address` | 주소, 없으면 NULL |
| `latitude`, `longitude` | 좌표, 없으면 NULL |
| `raw_json` | provider 원본 극장 payload |

### `screens`

| column | rule |
|---|---|
| `theater_id` | 내부 `theaters.id`, 매칭 실패 시 NULL 가능 |
| `external_theater_id` | provider 원본 극장 ID |
| `external_screen_id` | provider 원본 상영관 ID |
| `name` | 상영관명 |
| `screen_type` | 2D, IMAX, Dolby, special hall 등 provider 표시값 |
| `floor_name` | 층 정보, 없으면 NULL |
| `total_seat_count` | 상영관 또는 회차 기준 좌석 수 |

### `showtimes`

| column | rule |
|---|---|
| `external_showtime_key` | provider별 회차 식별 key |
| `movie_id`, `theater_id`, `screen_id` | 내부 FK처럼 쓰는 참조 ID, DB FK 제약은 아직 없음 |
| `external_movie_id`, `external_theater_id`, `external_screen_id` | 원본 매칭용 ID |
| `movie_title`, `theater_name`, `screen_name` | 검색 결과 표시용 denormalized 이름 |
| `region_code`, `region_name` | 지역 필터용 |
| `screen_type`, `format_name` | 포맷/상영 타입 필터용 |
| `show_date` | 날짜 필터의 기준 |
| `starts_at`, `ends_at` | 정렬/시간 필터 기준 |
| `total_seat_count`, `remaining_seat_count`, `sold_seat_count` | 좌석 요약 |
| `seat_occupancy_rate` | `sold / total * 100` |
| `booking_available` | provider 원본 예매 가능 여부 |
| `min_price_amount` | 최저가 비교용, 가격 수집 전에는 NULL |
| `booking_key_json` | 좌석/예매 링크 재조회에 필요한 원본 key |
| `booking_url` | 사용자가 누르면 넘어갈 외부 예매 URL |
| `raw_json` | provider 원본 상영 payload |

### `seat_snapshots`

| column | rule |
|---|---|
| `showtime_id` | 내부 `showtimes.id` |
| `external_showtime_key` | snapshot 대상 회차 key |
| `snapshot_at` | 좌석 상태를 본 시각 |
| `total_seat_count`, `remaining_seat_count`, `sold_seat_count` | 좌석 요약 |
| `unavailable_seat_count`, `special_seat_count` | 판매불가/특수 좌석 요약 |
| `raw_summary_json` | snapshot 생성에 사용한 요약 payload |

### `seat_snapshot_items`

| column | rule |
|---|---|
| `seat_snapshot_id` | 내부 `seat_snapshots.id` |
| `seat_key` | snapshot 안에서 unique한 좌석 key |
| `seat_label` | 사용자 표시 좌석명, 예: `A7` |
| `seat_row`, `seat_column` | 좌석 행/열 |
| `normalized_status` | `available`, `sold`, `unavailable`, `special`, `unknown` |
| `provider_status_code` | provider 원본 좌석 상태 코드 |
| `seat_type`, `zone_name` | 좌석 유형/구역 |
| `x`, `y`, `width`, `height` | 좌석 배치 좌표 |
| `provider_meta_json`, `raw_json` | provider별 부가 정보와 원본 |

## Provider Showtime Keys

### Lotte Cinema

`external_showtime_key`는 아래 값을 `|`로 이어서 만든다.

```text
cinema_id|screen_id|play_date|play_sequence|screen_division_code
```

### Megabox

`external_showtime_key`는 아래 값을 `|`로 이어서 만든다.

```text
play_schedule_no|branch_no
```

### CGV

CGV는 schema 준비 상태이며 ingest 활성화 전이다.
현재 의도한 key는 아래 형태다.

```text
site_no|scn_ymd|scns_no|scn_sseq
```

CGV ingest를 붙일 때 이 문서와 `collectors/common/normalize.py`를 같이 갱신한다.

## Search Contract

검색/비교 API는 우선 `showtimes`를 중심으로 만든다.

- 영화명 검색: `showtimes.movie_title`
- 날짜 필터: `showtimes.show_date`
- 시간 정렬: `showtimes.starts_at`
- 지역 필터: `showtimes.region_code`, `showtimes.region_name`
- 극장 필터: `showtimes.provider_code`, `showtimes.theater_name`, `showtimes.external_theater_id`
- 잔여좌석 필터: `showtimes.remaining_seat_count`
- 예매 가능 필터: `showtimes.booking_available`
- 가격 정렬: `showtimes.min_price_amount`, 추후 `showtime_prices.total_price_amount`
- 예매 이동: `showtimes.booking_url`

## Recommendation Contract

감정/날씨 추천은 DB 기준으로는 `movie_tags`를 먼저 쓴다.
AI 또는 로컬 LLM은 태그 생성/보정/자연어 검색 보조로 붙이고, 최종 검색은 DB 필터로 재현 가능해야 한다.

권장 `tag_type` 예시는 아래와 같다.

- `mood`: `warm`, `sad`, `thrill`, `comfort`, `funny`
- `weather`: `rain`, `snow`, `hot`, `cold`, `clear`
- `genre`: provider 장르 또는 자체 정규화 장르
- `pace`: `slow`, `medium`, `fast`
- `audience`: `date`, `family`, `alone`, `friends`

## Storage Contract

TiDB에는 검색/비교에 필요한 필드와 작은 원본 JSON만 둔다.
큰 원본 응답이나 장기 보관 payload는 R2에 저장하고 아래 컬럼으로 연결한다.

- `raw_object_key`
- `raw_object_etag`
- `raw_object_size`
- `raw_archived_at`

R2 연결 전까지 이 컬럼들은 NULL이어도 정상이다.

## Do Not Rename Without Migration

아래 이름은 코드와 DB가 이미 의존한다.
바꾸려면 migration, ingest, verify, API 쿼리를 같이 바꾼다.

- `provider_code`
- `external_movie_id`
- `external_theater_id`
- `external_screen_id`
- `external_showtime_key`
- `booking_key_json`
- `booking_url`
- `raw_json`
- `raw_summary_json`
- `seat_snapshots`
- `seat_snapshot_items`
- `movie_tags`

## Anonymous Recommendation Tables

Migration `004_anonymous_recommendations.sql` adds login-free AI recommendation state.
These tables must not store names, emails, phone numbers, auth tokens, cookies, or provider account data.

| table | role | retention |
|---|---|---|
| `recommendation_profiles` | anonymous survey, poster choices, and learned tag weights | until user reset |
| `recommendation_runs` | recommendation request/result audit for one anonymous ID | until user reset |
| `recommendation_feedback` | `like`, `dislike`, `booking_view` feedback | until user reset |

Stable API-facing key is `anonymous_id`. It is an opaque random ID, not a login identity.

## Migration 005 Team Sharing Extensions

Migration `005_collection_contract_extensions.sql` freezes the tables that are needed after the fresh 3-provider crawl classification. These tables are part of the shared schema contract even if some ingest paths do not populate all of them yet.

| table | role | write policy |
|---|---|---|
| `collection_runs` | one collector execution, scope, counts, status, and error summary | insert/update per run |
| `canonical_movies` | provider-independent movie identity candidate | upsert/manual review |
| `movie_provider_links` | link one canonical movie to provider-specific movie rows | upsert |
| `seat_layouts` | stable auditorium layout for one provider/theater/screen/fingerprint | upsert |
| `seat_layout_items` | stable seats inside one layout | upsert with layout |
| `provider_status_codes` | provider raw status code to normalized status mapping | upsert |
| `provider_raw_payloads` | raw response or R2 object index | append/index |

`showtime_price_options` is not a separate table name in this schema. Price options use the existing `showtime_prices` table.

Price ingest is showtime-key based. Do not persist separate movie-price or theater-price aggregates; store one row per `(provider_code, external_showtime_key, price_key)` and mirror the collected minimum into `showtimes.min_price_amount` for recommendation/list rendering.

## Migration 005 Stable Keys

| table | unique key | meaning |
|---|---|---|
| `collection_runs` | `run_key` | one logical collector run |
| `canonical_movies` | `canonical_key` | normalized movie identity candidate |
| `movie_provider_links` | `provider_code`, `external_movie_id` | one provider movie maps to one canonical movie |
| `seat_layouts` | `provider_code`, `external_theater_id`, `external_screen_id`, `layout_fingerprint` | one stable screen layout |
| `seat_layout_items` | `seat_layout_id`, `seat_key` | one stable seat inside a layout |
| `provider_status_codes` | `provider_code`, `status_domain`, `provider_status_code` | one raw status mapping |

## Showtime Midnight Rule

Provider raw times can cross midnight. CGV can return compact values such as `2400` or `2609`; Lotte can return values such as `24:17`.

- Keep `start_time_raw` and `end_time_raw` exactly as the provider returned them.
- Normalize `starts_at` and `ends_at` to real `DATETIME` values for sorting and filtering.
- `2400` means next day `00:00`.
- `2609` means next day `02:09`.
- `24:17` means next day `00:17`.
- If the normalized end time is not after the start time, treat the end time as next day.

This rule is required for night searches, AI recommendation time filters, and seat snapshot lookahead.

## Provider Status Code Contract

Provider-specific seat or booking status codes should not be hardcoded only in application logic once they are observed in real data. Record observed mappings in `provider_status_codes` with:

- `status_domain`: for example `seat`, `booking`, or `showtime`
- `provider_status_code`: raw provider code, for example `00`, `50`, `GERN_SELL`, `SCT04`
- `provider_status_name`: raw display label when available
- `normalized_status`: `available`, `sold`, `unavailable`, `special`, or `unknown`
- `sample_json`: small redacted example only, with no cookies, tokens, or account data

Migration 005 seeds the mappings observed in the fresh 2026-04-27 crawl:

| provider | domain | code | normalized_status | evidence |
|---|---|---|---|---|
| `CGV` | `seat` | `00` | `available` | 144 observed seats; sale flag available |
| `CGV` | `seat` | `01` | `sold` | existing normalizer sold code |
| `LOTTE_CINEMA` | `seat` | `0` | `sold` | 140 observed seats while remaining seats were 2 |
| `LOTTE_CINEMA` | `seat` | `50` | `available` | 2 observed seats matching remaining seats |
| `MEGABOX` | `seat` | `GERN_SELL` | `available` | 102 observed seats matching remaining seats |
| `MEGABOX` | `seat` | `SCT04` | `unavailable` | 14 observed seats excluded from remaining seats |

## Seat Layout Contract

Use `seat_layouts` and `seat_layout_items` for stable screen geometry. Use `seat_snapshots` and `seat_snapshot_items` for changing sale state.

- Layout tables answer: where seats are and what zones/types they belong to.
- Snapshot tables answer: which seats are currently available, sold, blocked, or special.
- `seat_layouts.layout_fingerprint` should be derived from stable seat geometry, not from sale state.
- `seat_snapshot_items.seat_key` should match `seat_layout_items.seat_key` when the provider gives a stable key.

## Raw Payload Contract

Large provider responses should stay out of committed files and should not expose credentials. Use `provider_raw_payloads` to point at either row-level `raw_json` or an external object key.

- `object_key`, `object_etag`, and `object_size` are for R2 or equivalent raw archive storage.
- `raw_json` is optional and should be used only for bounded, safe payloads.
- Never store provider account IDs, passwords, cookies, API secrets, or bearer tokens in `raw_json`.
- `collection_run_id` links raw payload indexes back to the run that produced them.
