# ERROR LOG

Append-only log for installer, execution, tool, and verification errors.
Add new entries with timestamp, location, summary, and details.
Do not rewrite existing entries; append only.

- time: `2026-04-10 00:00:00 +09:00`
  location: `cgv_collector_demo.py, lotte_collector_demo.py, megabox_collector_demo.py`
  summary: `데모 스크립트 공통 실행 실패`
  details: `collectors/__init__.py 가 존재하지 않는 collectors.api 를 임포트해서 세 스크립트 모두 ModuleNotFoundError 로 즉시 종료됨`
  status: `open`

- time: `2026-04-10 00:10:00 +09:00`
  location: `collectors/__init__.py`
  summary: `공통 임포트 오류 해결`
  details: `패키지 루트가 각 하위 수집기 모듈의 실제 심볼만 재수출하도록 수정했고 세 데모 스크립트 재실행이 모두 성공함`
  status: `resolved`

- time: `2026-04-10 12:08:00 +09:00`
  location: `workspace search`
  summary: `rg 실행 권한 오류`
  details: `코드 검색에 rg 를 사용하려 했으나 Access is denied 로 실패했고 같은 탐색은 PowerShell Select-String 으로 우회했다`
  status: `resolved`

- time: `2026-04-10 12:25:03 +09:00`
  location: `PROJECT_HARNESS.md`
  summary: `필수 컨텍스트 파일 누락`
  details: `레포 규칙상 편집 전 읽어야 하는 PROJECT_HARNESS.md 를 확인하려 했지만 파일이 존재하지 않아 README.md, STATE.md, WORKSPACE_CONTEXT.toml, collectors/**, docs/** 를 대체 소스로 사용했다`
  status: `open`

- time: `2026-04-13 09:20:16 +09:00`
  location: `PROJECT_HARNESS.md`
  summary: `필수 컨텍스트 파일 누락 재확인`
  details: `이번 파일트리 정리 작업 전에도 PROJECT_HARNESS.md 를 읽으려 했지만 파일이 없어 README.md, STATE.md, WORKSPACE_CONTEXT.toml 및 파일트리 탐색 결과를 기준으로 진행했다`
  status: `open`

- time: `2026-04-13 09:20:16 +09:00`
  location: `workspace search`
  summary: `rg 실행 권한 오류 재발`
  details: `rg --files 실행이 Access is denied 로 실패해 PowerShell Get-ChildItem 기반 파일 탐색으로 우회했다`
  status: `resolved`

- time: `2026-04-13 16:56:02 +09:00`
  location: `TiDB Cloud connection verification`
  summary: `TiDB 인증 실패`
  details: `로컬 .env 값으로 PyMySQL TLS 연결을 시도했으나 TiDB가 Access denied 를 반환했다. 네트워크 도달성은 확인되었고 username/password 조합 또는 .env 중복/복사 오류를 우선 점검한다.`
  status: `open`

- time: `2026-04-13 17:02:43 +09:00`
  location: `TiDB Cloud connection verification`
  summary: `TiDB 인증 실패 해결`
  details: `.env 의 TiDB username prefix 가 TiDB Cloud Connect 화면 값과 달라 발생한 문제였다. TIDB_USER 및 DATABASE_URL 의 username 을 화면 값으로 맞춘 뒤 PyMySQL TLS 연결이 성공했다.`
  status: `resolved`

- time: `2026-04-13 16:45:22 +09:00`
  location: `PROJECT_HARNESS.md, WORKSPACE_CONTEXT.toml`
  summary: `하네스 컨텍스트가 현재 사용자 작업 방식과 불일치`
  details: `PROJECT_HARNESS.md 는 존재하지 않고 WORKSPACE_CONTEXT.toml 은 하네스 참조와 깨진 텍스트를 포함하고 있어 사용자 맞춤 workspace context 로 교체했다`
  status: `resolved`

- time: `2026-04-13 16:45:22 +09:00`
  location: `workspace search`
  summary: `rg 실행 권한 오류 재발`
  details: `rg --files 실행이 Access is denied 로 실패해 Get-ChildItem 과 직접 파일 읽기로 우회했다`
  status: `resolved`

- time: `2026-04-13 17:42:29 +09:00`
  location: `fresh collector sample probe`
  summary: `Python zoneinfo Asia/Seoul 로드 실패`
  details: `Windows Python 환경에 tzdata 패키지가 없어 ZoneInfo('Asia/Seoul') 초기화가 실패했다. 로컬 시스템 시간이 이미 +09:00 이므로 timezone 의존 없이 datetime.now() 기반으로 재실행한다.`
  status: `resolved`

- time: `2026-04-13 17:44:47 +09:00`
  location: `CGV fresh collector sample probe`
  summary: `CGV 최신 샘플 수집 보류`
  details: `.env 에 TIDB 연결 값은 있으나 CGV_API_SECRET 값이 없어 CGV API 클라이언트 초기화가 RuntimeError 로 중단되었다. 롯데시네마와 메가박스 최신 샘플, 기존 CGV 수집기 필드 매핑, 기존 좌석 fixture 를 기준으로 스키마 검토를 계속한다.`
  status: `deferred`

- time: `2026-04-13 17:45:30 +09:00`
  location: `scripts/db/002_showtime_search_metrics.sql`
  summary: `TiDB ALTER TABLE 컬럼 순서 참조 실패`
  details: `한 ALTER TABLE 문 안에서 sold_seat_count 를 추가한 직후 seat_occupancy_rate 의 AFTER 기준으로 다시 참조하자 TiDB가 Unknown column 을 반환했다. 컬럼별 ALTER TABLE 문으로 쪼개 재적용한다.`
  status: `resolved`

## 2026-04-14 10:26:29 +09:00

- time: `2026-04-14 10:26:29 +09:00`
- location: `git rm --cached -- AGENTS.md ERROR_LOG.md STATE.md WORKSPACE_CONTEXT.toml`
- summary: `Index cleanup initially failed because STATE.md had staged content different from both HEAD and the working tree.`
- details: `Retried with git rm --cached -f, which removed only index entries and preserved local files.`
- status: `resolved`

- time: `2026-04-14 13:06:01 +09:00`
- location: `scripts/db/apply_migrations.py`
- summary: `003 마이그레이션 적용 중 SQL 분리 실패`
- details: `003_raw_object_keys.sql 주석의 세미콜론을 단순 split 로직이 SQL 구분자로 처리해 TiDB가 주석 일부를 SQL로 받아 문법 오류를 반환했다. 주석 제거 후 statement를 분리하도록 마이그레이션 러너를 보강한다.`
- status: `open`

- time: `2026-04-14 13:06:37 +09:00`
- location: `scripts/db/apply_migrations.py`
- summary: `SQL 분리 실패 해결`
- details: `마이그레이션 러너가 -- line comment 를 제거한 뒤 statement 를 분리하도록 수정해 주석 내 세미콜론 문제는 해결됐다.`
- status: `resolved`

- time: `2026-04-14 13:06:37 +09:00`
- location: `db/migrations/003_raw_object_keys.sql`
- summary: `TiDB multi-column ALTER 순서 참조 실패`
- details: `movies 테이블에서 raw_object_key 를 추가한 같은 ALTER 문 안에서 raw_object_etag 의 AFTER raw_object_key 를 참조하자 TiDB가 Unknown column 을 반환했다. 002와 같은 방식으로 컬럼별 ALTER TABLE 문으로 쪼갠다.`
- status: `open`

- time: `2026-04-14 13:07:00 +09:00`
- location: `db/migrations/003_raw_object_keys.sql`
- summary: `TiDB multi-column ALTER 순서 참조 실패 해결`
- details: `003 마이그레이션의 raw_object_* 컬럼 추가를 컬럼별 ALTER TABLE 문으로 분리했고 재실행 결과 schema_migrations 가 001,002,003 상태로 확인됐다.`
- status: `resolved`

- time: `2026-04-14 15:12:22 +09:00`
- location: `scripts/cgv_collector_demo.py, scripts/cgv_api_probe.py`
- summary: `CGV 최신 데이터 추출 보류`
- details: `롯데시네마 최신 데이터 추출은 성공했지만 CGV 스크립트는 CGV_API_SECRET 환경변수가 없어 RuntimeError 로 중단됐다. 비밀값은 출력하지 않았고 CGV 수집은 secret 설정 후 재실행해야 한다.`
- status: `deferred`

- time: `2026-04-14 15:21:00 +09:00`
- location: `collectors/cgv/api.py, scripts/cgv_collector_demo.py, scripts/cgv_api_probe.py`
- summary: `CGV 서명 API 호출은 secret 부재로 계속 보류`
- details: `CGV_API_SECRET 를 환경변수 또는 루트 .env 에서 읽도록 보강했고 누락 시 스택트레이스 없이 명확한 오류로 종료하게 했다. 현재 로컬 .env 에 CGV_API_SECRET 값이 없어 실제 CGV API 호출은 아직 실행하지 못했다.`
- status: `deferred`

- time: `2026-04-14 15:29:00 +09:00`
- location: `.local/api-responses/fresh-cgv-20260414-152645`
- summary: `CGV 서명 API 최신 샘플 수집 성공`
- details: `사용자가 로컬 .env 에 CGV_API_SECRET 을 설정한 뒤 CGV 영화, 속성, 지역, 극장, 날짜, 상영, 좌석 샘플 수집이 성공했다. 비밀값은 출력하지 않았고 raw/normalized JSON 은 ignored .local 경로에 저장했다.`
- status: `resolved`

- time: `2026-04-14 15:29:00 +09:00`
- location: `PowerShell ConvertFrom-Json over CGV UTF-8 JSON`
- summary: `PowerShell 기본 인코딩으로 CGV JSON 파싱 실패`
- details: `Python 이 UTF-8 JSON 을 정상 저장했지만 Windows PowerShell Get-Content 기본 인코딩으로 읽자 한글 바이트가 깨져 ConvertFrom-Json 이 실패했다. Get-Content -Encoding UTF8 로 재실행해 정상 집계했다.`
- status: `resolved`

- time: `2026-04-15 17:35:00 +09:00`
- location: `backend verification`
- summary: `Java/Gradle PATH 부재로 백엔드 테스트 실행 보류`
- details: `로컬 Gemma 추천 API 구현 후 java -version 과 gradle test 를 실행했지만 둘 다 PATH 에서 명령을 찾지 못해 테스트를 실행하지 못했다. JDK 21 과 Gradle 경로 설정 후 gradle test 를 재실행해야 한다.`
- status: `deferred`

- time: `2026-04-16 10:43:22 +09:00`
- location: `backend gradle test after JDK/Gradle install`
- summary: `백엔드 컴파일 실패`
- details: `JDK 21, Gradle 8.14.4 설치 후 backend 에서 gradle test 를 재실행했으나 RecommendationService.java:103 의 lambda 캡처 변수가 effectively final 이 아니어서 compileJava 단계에서 실패했다. 설치 검증은 통과했고, 코드 수정 후 gradle test 재실행이 필요하다.`
- status: `open`

- time: `2026-04-16 14:50:44 +09:00`
- location: `repository root gradle verification`
- summary: `Gradle 테스트를 레포 루트에서 실행해 빌드 루트를 찾지 못함`
- details: `gradle test --tests kr.daboyeo.backend.service.recommendation.PreferenceProfileBuilderTests 를 C:\lsh\git\daboyeo 에서 실행해 settings.gradle/build.gradle 을 찾지 못했다. 같은 명령을 C:\lsh\git\daboyeo\backend 에서 재실행해 성공했다.`
- status: `resolved`

- time: `2026-04-16 17:23:52 +09:00`
- location: `backend Gradle verification`
- summary: `동일 Gradle build 디렉터리를 대상으로 테스트를 병렬 실행해 test-results 삭제 충돌 발생`
- details: `PreferenceProfileBuilderTests 와 recommendation 패키지 테스트를 동시에 실행하면서 C:\lsh\git\daboyeo\backend\build\test-results\test\binary\output.bin 삭제가 실패했다. 코드 실패가 아니라 검증 명령 병렬화 문제이므로 테스트를 직렬로 재실행한다.`
- status: `resolved`

- time: `2026-04-17 09:51:51 +09:00`
- location: `LM Studio direct chat completion verification`
- summary: `LM Studio가 OpenAI json_object response_format 값을 거부함`
- details: `POST http://127.0.0.1:1234/v1/chat/completions 에 response_format.type=json_object 를 보내자 'response_format.type' must be 'json_schema' or 'text' 오류가 반환됐다. 백엔드 구현은 response_format.type=text 와 기존 JSON-only 프롬프트/파서 fallback 조합으로 고정했고, 직접 호출 및 Gradle 테스트를 재실행해 통과했다.`
- status: `resolved`

- time: `2026-04-17 14:47:29 +09:00`
- location: `local recommendation E2E verification`
- summary: `세션 API가 DB 준비와 JDBC URL 보간 문제로 503 반환`
- details: `초기 /api/recommendation/sessions 호출은 추천 저장 테이블이 없어 503을 반환했다. 기존 V004 anonymous recommendation migration만 적용해 테이블을 생성했다. 이후 Java/Spring 실행용 JDBC URL을 PowerShell에서 만들 때 $database?serverTimezone 형태가 잘못 보간되어 DB명이 깨졌고 Java 연결 테스트에서 Unknown database 오류가 확인됐다. ${database}?serverTimezone 형태로 보간을 수정해 java -jar 백엔드를 재시작한 뒤 세션 생성과 추천 no_candidates 응답이 200으로 통과했다.`
- status: `resolved`

- time: `2026-04-17 15:17:55 +09:00`
- location: `scripts/ingest/collect_all_to_tidb.py lotte ingest`
- summary: `롯데 실제 수집 데이터의 24시 이후 종료 시간이 DB datetime 입력을 막음`
- details: `롯데 실제 ingest 실행 중 원본 종료 시간이 24:22 형태로 들어와 showtimes.ends_at 입력에서 Incorrect datetime value 오류가 발생했다. fake seed 대신 실제 수집 데이터를 쓰기 위해 수집 스크립트의 시간 정규화가 필요하다.`
- status: `open`

- time: `2026-04-17 15:44:23 +09:00`
- location: `scripts/ingest/collect_all_to_tidb.py lotte ingest`
- summary: `롯데 24시 이후 종료 시간 정규화 해결`
- details: `DB datetime 변환에서 24:22, 2515 같은 시간을 다음날 00:22:00, 01:15:00으로 정규화하도록 수정했다. 롯데 실제 ingest를 재실행해 실제 상영 20건이 upsert됐다.`
- status: `resolved`

- time: `2026-04-17 15:31:03 +09:00`
- location: `backend recommendation API to LM Studio`
- summary: `LM Studio 호출이 무기한 대기해 추천 API가 응답하지 않음`
- details: `실제 수집 후보가 있는 fast 추천 요청에서 Spring RestClient가 LM Studio /chat/completions 응답을 기다리며 180초 이상 반환하지 않았다. Java thread dump에서 LocalModelRecommendationClient.callLmStudio 내부 HTTP 요청 대기가 확인됐고, 추천 API가 fallback으로도 빠지지 못했다.`
- status: `open`

- time: `2026-04-17 15:44:23 +09:00`
- location: `backend recommendation API to LM Studio`
- summary: `LM Studio 호출 대기 및 JSON 잘림 해결`
- details: `LM Studio 호출에 connect/read timeout을 추가하고 JSON schema 문자열 maxLength 및 max_tokens 260을 적용했다. E2B fast와 E4B precise 모두 실제 수집 후보로 status=ok, recommendation_count=3 응답을 확인했다.`
- status: `resolved`

- time: `2026-04-20 09:54:16 +09:00`
- location: `backend recommendation API to LM Studio`
- summary: `최적화 중 토큰 예산 과축소로 AI 응답 JSON 잘림`
- details: `fast 200, precise 240 max_tokens 설정에서 두 모델 모두 3번째 추천 JSON이 잘려 API가 fallback으로 응답했다. 후보/프롬프트 축소는 유지하고 fast 280, precise 320으로 토큰 예산을 올려 fast와 precise 모두 status=ok를 확인했다.`
- status: `resolved`

- time: `2026-04-20 10:05:16 +09:00`
- location: `scripts/ingest/collect_all_to_tidb.py, showtimes.movie_id`
- summary: `메가박스 수집 데이터에서 showtimes.movie_title 과 movies.title_ko 불일치 발견`
- details: `대량 수집 후 검증 쿼리에서 showtimes.movie_title <> movies.title_ko 인 행이 139개 확인됐다. 특히 하나의 movie_id가 여러 메가박스 상영 제목에 연결되어 추천 응답의 movieId와 피드백 대상 정합성이 흔들릴 수 있다.`
- status: `open`

- time: `2026-04-20 10:24:00 +09:00`
- location: `backend recommendation focused tests`
- summary: `영화 다양성 테스트 기대값 불일치`
- details: `제목 우선 중복 제거 정책으로 고정한 뒤 기존 테스트가 서로 다른 제목이 같은 movie_id 를 가진 경우까지 중복으로 기대해 실패했다. 메가박스 과거 매핑 오류를 고려해 같은 제목의 다른 시간표를 중복으로 보는 테스트로 수정했고 추천 패키지 테스트를 재실행해 통과했다.`
- status: `resolved`

- time: `2026-04-20 10:55:00 +09:00`
- location: `scripts/ingest/collect_all_to_tidb.py, showtimes.movie_id`
- summary: `메가박스 movie_id 매핑 오류 해결`
- details: `메가박스 상영 row의 movieNo 를 우선 사용해 영화 row를 upsert하고, showtimes에는 있으나 movies에는 없는 external_movie_id 를 raw showtime 기반으로 backfill한 뒤 showtimes.external_movie_id 와 movies.external_movie_id 기준으로 기존 연결을 보정하도록 수정했다. 검증 결과 오늘 메가박스 title mismatch 0, multi-title movie_id 그룹 0, 전체 external link mismatch 0을 확인했다.`
- status: `resolved`

- time: `2026-04-20 12:45:00 +09:00`
- location: `frontend benchmark via Chrome CDP`
- summary: `프론트 벤치마크 초기 CDP 타임아웃`
- details: `Chrome /json/list 의 첫 target 이 page가 아니라 extension background_page 여서 벤치 DOM 완료 신호를 읽지 못하고 두 차례 타임아웃됐다. 이후 type=page target만 선택하도록 벤치 드라이버를 수정해 fast/precise 5회 측정을 완료했다.`
- status: `resolved`

- time: `2026-04-20 14:10:33 +09:00`
- location: `frontend benchmark via Chrome CDP`
- summary: `벤치 드라이버 WebSocket 경로와 소켓 타임아웃 문제`
- details: `수동 CDP WebSocket 연결에서 query가 없는 target path 뒤에 불필요한 ?를 붙여 Chrome이 target id를 찾지 못했고, 수정 후에는 기본 socket timeout 30초가 추론 중 먼저 만료됐다. 요청 측정값으로 쓰지 않고 request path 처리와 socket timeout을 수정한 뒤 fast/precise 5회 벤치를 완료했다.`
- status: `resolved`

- time: `2026-04-20 14:50:59 +09:00`
- location: `backend restart for compact JSON frontend benchmark`
- summary: `백엔드 재시작 시 Spring DB 환경변수 매핑 누락으로 세션 API 503`
- details: `.env`에는 TIDB_HOST/TIDB_PORT/TIDB_USER/TIDB_PASSWORD/TIDB_DATABASE가 있었지만 Spring은 DABOYEO_DB_URL/DABOYEO_DB_USERNAME/DABOYEO_DB_PASSWORD를 사용한다. 첫 재시작은 이 매핑 없이 기본 datasource로 떠서 /api/recommendation/sessions가 503을 반환했다. 값은 출력하지 않고 TiDB env에서 Spring datasource env를 구성해 재시작한 뒤 health와 session create/delete를 확인하고 벤치를 재실행했다.`
- status: `resolved`

- time: `2026-04-20 16:12:39 +09:00`
- location: `backend recommendation focused tests`
- summary: `analysisPoint를 precise 전용으로 바꾸면서 fast 기대값 테스트가 실패`
- details: `기존 품질 테스트 3개가 fast/E2B 응답에도 analysisPoint=#애니메이션취향 이 있다고 기대해 실패했다. 새 계약에 맞춰 fast는 blank analysisPoint, precise/E4B는 selected-poster genre analysisPoint를 기대하도록 테스트를 수정했고 focused/package/full Gradle 테스트를 재실행해 통과했다.`
- status: `resolved`

- time: `2026-04-21 20:16:00 +09:00`
  location: `STATE.md`
  summary: `작업 재분류 중 STATE.md 인코딩 손상`
  details: `Stitch-led AI 페이지 리뉴얼 태스크로 보드를 갱신하는 중 STATE.md 가 혼합 인코딩으로 깨져 읽기 어려운 상태가 됐다. 현재 보드를 UTF-8로 재구성해 계속 진행한다.`
  status: `resolved`

- time: `2026-04-21 20:18:00 +09:00`
  location: `mcp__stitch__.create_design_system, mcp__stitch__.generate_screen_from_text`
  summary: `Stitch 디자인 시스템/화면 생성 호출 실패`
  details: `create_design_system 은 invalid argument 를 반환했고, 이어진 generate_screen_from_text 는 service unavailable 을 반환했다. Stitch를 디자인 근거 시도로는 사용했지만 실제 화면 구현은 동일한 브리프를 기반으로 로컬 코드에서 우회했다.`
  status: `deferred`
## 2026-04-21 17:17:07 +09:00
- time: `2026-04-21 17:17:07 +09:00`
- location: `mcp__stitch__.edit_screens`
- summary: `Results-screen Stitch regeneration timed out during edit_screens call`
- details: `A focused dark-layout redesign prompt was sent to Stitch for screen a11a57f615384ba28f95ce67ac495b55 in project 7742688576431333902. The tool timed out after 120 seconds. Per tool instructions, the generation may still have completed server-side, so the next step is to verify with get_screen instead of retrying immediately.`
- status: `open`

## 2026-04-21 17:17:07 +09:00
- time: `2026-04-21 17:17:07 +09:00`
- location: `mcp__stitch__.generate_variants`
- summary: `Results-screen variant generation rejected the variantOptions argument`
- details: `Tried to generate one dark-theme variant from screen a11a57f615384ba28f95ce67ac495b55 in project 7742688576431333902, but Stitch returned 'Request contains an invalid argument.' The next fallback is to generate a fresh results screen from text in the same project instead of variant mode.`
- status: `open`

## 2026-04-21 17:17:07 +09:00
- time: `2026-04-21 17:17:07 +09:00`
- location: `mcp__stitch__.generate_screen_from_text`
- summary: `Results-screen redesign recovered via short prompt fallback`
- details: `After one timed-out long prompt and one rejected variant call, a shorter direct generation prompt succeeded in project 7742688576431333902 and created screen 8c8ebac647434c24a68f40e105393323 titled AI 추천 결과 (Dark Cinematic Stage). This resolves the immediate need for a dark replacement results screen.`
- status: `resolved`

- time: `2026-04-22 13:10:11 +09:00`
  location: `mcp__stitch__.create_design_system`
  summary: `1440x1026 PPT 새 세션 생성 중 Stitch 디자인 시스템 생성 실패`
  details: `새 PPT 프로젝트 13482283388031437931 에서 스타일을 먼저 고정하려고 create_design_system 을 호출했지만 'Request contains an invalid argument.' 를 반환했다. 작업은 각 slide generation prompt 에 스타일 규칙을 직접 포함하는 방식으로 우회했다.`
  status: `resolved`

- time: `2026-04-22 13:10:11 +09:00`
  location: `mcp__stitch__.generate_screen_from_text`
  summary: `Stitch가 1440x1026 exact frame size를 보장하지 않음`
  details: `사용자 요구는 1440x1026 발표 프레임이었지만 생성된 screen 메타데이터는 2560x2052, 2880x2052, 2752x2240 등 더 큰 기본 desktop canvas 로 반환됐다. 내용과 톤은 생성했지만 exact pixel 규격은 현재 Stitch 생성 경로에서 강제되지 않았다.`
  status: `open`

- time: `2026-04-22 13:10:11 +09:00`
  location: `mcp__stitch__.list_screens`
  summary: `생성 후 list_screens 결과가 비어 있음`
  details: `프로젝트 13482283388031437931 에 여러 screen 생성 호출이 성공했지만 list_screens 는 빈 객체를 반환했다. 이번 턴의 산출물 확인은 각 generate_screen_from_text 성공 응답의 screen id 와 screenshot metadata 를 근거로 했다.`
  status: `open`

- time: `2026-04-22 14:20:00 +09:00`
  location: `mcp__stitch__.generate_screen_from_text`
  summary: `UI/UX 슬라이드 생성 응답이 120초 타임아웃으로 끊김`
  details: `프로젝트 10979052864160268633 에서 '7. 주요 화면 (UI/UX)' 슬라이드를 생성하던 중 Stitch 호출이 120초 후 타임아웃됐다. 도구 안내상 서버 쪽 생성이 계속됐을 가능성은 있지만, 같은 환경에서 list_screens 도 빈 객체를 반환해 즉시 복구 확인을 하지 못했다.`
  status: `open`

- time: `2026-04-22 14:38:20 +09:00`
  location: `tool discovery for Figma resizing`
  summary: `현 세션에 exact 1440x1026 프레임 리사이즈용 Figma write tool 이 노출되지 않음`
  details: `사용자가 rebuilt deck 의 정확한 1440x1026 사이즈를 요구해 도구 탐색을 다시 수행했지만, 현재 세션에서는 Stitch generate/edit 와 Figma read/generate 계열만 확인됐고 기존 슬라이드 프레임 크기를 직접 수정하는 write/resize 액션은 찾지 못했다. 이 때문에 현재 자동 경로로는 exact pixel size enforcement 가 막혀 있다.`
  status: `open`

- time: `2026-04-28 00:00:00 +09:00`
  location: `backend gradle verification`
  summary: `팀 DB 스키마 마무리 중 Gradle 테스트 시작 실패`
  details: `gradle test --tests kr.daboyeo.backend.ingest.CollectorBundleIngestCommandTests 를 backend 디렉터리에서 실행했지만 Gradle native-platform.dll 로딩 실패로 빌드가 시작되지 않았다. 코드 테스트 결과가 아니라 로컬 Gradle 런타임 문제이며, 가능한 정적 검증과 diff 검증으로 대체한다.`
  status: `open`

- time: `2026-04-28 13:18:00 +09:00`
  location: `backend Spring startup for AI recommendation smoke`
  summary: `Spring bootRun 시작 전 Gradle native-platform.dll 로딩 실패`
  details: `AI 추천 백엔드 확인을 위해 backend 디렉터리에서 gradle --version 을 먼저 실행했지만 Gradle native services 초기화 중 native-platform.dll 로딩 실패가 재현됐다. bootRun은 같은 Gradle 런타임에 의존하므로 실행하지 못했고, 기존 build/libs jar 실행으로 우회해 8080 health를 확인한다.`
  status: `open`

- time: `2026-04-28 14:26:00 +09:00`
  location: `AI recommendation backend smoke`
  summary: `Spring health는 정상이나 recommendation session API가 타임아웃`
  details: `기존 build/libs jar를 숨김 PowerShell 프로세스로 실행하면 /api/health 는 status=ok 를 반환한다. 하지만 /api/recommendation/sessions POST 는 8초 안에 응답하지 않아 프론트가 로컬 프리뷰 세션으로 fallback 된다. /api/recommendation/poster-seed 는 200을 반환하므로 서버 전체 다운이 아니라 세션 저장소 또는 DB 연결 경로를 우선 확인해야 한다.`
  status: `open`

- time: `2026-04-28 17:00:40 +09:00`
  location: `backend static mirror runtime`
  summary: `샌드박스 내부 Start-Process 로 띄운 Spring 서버가 명령 종료 후 유지되지 않음`
  details: `Spring boot jar 자체는 약 13초 후 정상 시작하고 static/index.html 도 잡히지만, 기본 샌드박스 내부에서 분리 실행한 Java 프로세스는 앱 브라우저 확인 전에 정리되어 ERR_CONNECTION_REFUSED 로 보였다. 샌드박스 밖에서 같은 jar를 실행하자 /api/health, /, /src/pages/daboyeoAi.html 모두 200으로 확인됐다.`
  status: `resolved`

- time: `2026-04-29 10:42:00 +09:00`
  location: `backend recommendation session runtime`
  summary: `익명 세션 생성 API가 DB 설정 문제로 타임아웃`
  details: `/api/health 는 정상 응답했지만 /api/recommendation/sessions POST 가 타임아웃됐다. 원인은 Spring 서버가 처음에는 기본 localhost DB URL로 실행됐고, 이후 TiDB URL 조립 중 PowerShell 변수 보간 오류로 DB명이 깨졌으며, 다음 실행에서는 useSSL=false 로 TiDB Cloud가 연결을 거부한 것이다. .env의 TiDB 값을 DABOYEO_DB_* 로 매핑하고 URL 보간을 ${db} 형태로 고친 뒤 useSSL=true 로 재시작해 세션 API 200을 확인했다.`
  status: `resolved`

- time: `2026-04-29 10:58:00 +09:00`
  location: `manual Java config verification`
  summary: `JShell 검증이 Windows prefs 권한 문제로 실패`
  details: `변경된 dotenv 설정 로직을 jshell 로 검증하려 했지만 Java Preferences 레지스트리 접근 권한 문제로 jshell 이 히스토리 저장 중 종료됐다. 같은 검증은 build/tmp 아래 임시 Java 클래스 컴파일/실행으로 우회했다.`
  status: `resolved`

- time: `2026-04-29 11:01:00 +09:00`
  location: `RootDotenvLoader`
  summary: `.env UTF-8 BOM 때문에 첫 TIDB_HOST 키를 읽지 못함`
  details: `루트 .env 첫 키가 Java 로더에서 TIDB_HOST가 아니라 BOM이 붙은 키로 읽혀 Spring datasource 파생값이 생성되지 않았다. RootDotenvLoader가 BOM을 제거하도록 수정하고 BOM 회귀 테스트를 추가했다.`
  status: `resolved`

- time: `2026-04-29 11:07:00 +09:00`
  location: `backend boot jar runtime`
  summary: `기존 boot jar에 EnvironmentPostProcessor 등록 리소스가 없어 dotenv 로직이 실행되지 않음`
  details: `소스와 build resources에는 META-INF/spring.factories가 있었지만 현재 실행 중인 build/libs jar에는 BOOT-INF/classes/META-INF/spring.factories가 없어 RootDotenvEnvironmentPostProcessor가 등록되지 않았다. 현재 jar에 등록 리소스를 반영한 뒤 정상 jar 실행에서 /api/recommendation/sessions 200을 확인했다.`
  status: `resolved`

- time: `2026-04-29 11:32:48 +09:00`
  location: `backend boot jar runtime`
  summary: `RecommendationProperties 보조 생성자 추가 후 Spring 설정 바인딩 실패`
  details: `GPT provider 설정 필드를 추가하면서 테스트 호환용 보조 생성자를 함께 둔 결과, Spring Boot가 @ConfigurationProperties record의 바인딩 생성자를 고르지 못하고 기본 생성자를 찾다가 boot jar가 종료됐다. canonical record 생성자에 @ConstructorBinding을 명시하고 jar를 다시 패치한 뒤 /api/health 200을 확인했다.`
  status: `resolved`

- time: `2026-04-29 13:36:00 +09:00`
  location: `GPT recommendation prompt depth verification`
  summary: `Gradle 테스트 시작 실패와 Start-Process Path/PATH 중복 오류`
  details: `추천 프롬프트 차별화 검증 중 backend 디렉터리에서 gradle test --tests kr.daboyeo.backend.service.recommendation.* 를 실행했지만 native-platform.dll 로딩 실패로 Gradle이 시작되지 않았다. 변경 Java 클래스는 Gradle 캐시 jar를 classpath로 둔 javac 직접 컴파일로 검증했다. 이후 jar 재시작 때 Start-Process가 Windows 환경 변수 Path/PATH 중복으로 실패해 cmd start /b 경로로 우회했고 /api/health 200을 확인했다.`
  status: `resolved`

- time: `2026-04-29 14:08:00 +09:00`
  location: `recommendation future showtime recovery`
  summary: `Lotte 적재와 jar 패치 중 로컬 환경 문제가 발생`
  details: `TiDB 쓰기용 Python 경로는 Anaconda였지만 PyMySQL은 다른 Python user-site에만 잡혀 있어 repo 내부 backend/build/tmp/pymysql_vendor에 PyMySQL을 설치해 PYTHONPATH로 우회했다. Lotte 실제 적재는 영화 목록 API가 JSON이 아닌 응답을 반환해 중단됐고, Megabox 오늘 데이터 160건만 bounded upsert했다. 실행 중인 Spring jar가 파일 잠금을 잡아 첫 jar update가 실패했으며, 서버를 내린 뒤 jar를 패치하고 PID 6192로 재시작해 /api/health 200을 확인했다.`
  status: `resolved`

- time: `2026-04-29 14:15:00 +09:00`
  location: `provider health jar patch`
  summary: `새 nested class 누락으로 Spring 첫 재시작 실패`
  details: `provider health 응답 record를 추가한 뒤 jar 패치 스테이징에서 RecommendationModels*.class 복사가 누락되어 BOOT-INF/classes 안에 RecommendationModels$AiProviderStatus.class가 없었다. Spring이 LocalModelRecommendationClient를 introspect하다 NoClassDefFoundError로 종료됐다. class 복사를 wildcard Path 방식으로 다시 수행하고 jar에 domain/recommendation class들을 재반영한 뒤 PID 5500으로 /api/health 200을 확인했다.`
  status: `resolved`

- time: `2026-04-29 14:27:00 +09:00`
  location: `fallback result verification`
  summary: `poster-seed limit 파라미터 바인딩이 수동 jar 패치 환경에서 400 반환`
  details: `/api/recommendation/poster-seed?limit=12 검증 중 @RequestParam 이 limit 이름을 명시하지 않아, 현재 수동 컴파일/패치된 jar에서 Java parameter-name metadata 를 찾지 못하고 400 bad_request 를 반환했다. RecommendationController 의 @RequestParam 에 name = "limit" 를 명시해 런타임 바인딩이 컴파일 플래그에 의존하지 않도록 고쳤다.`
  status: `resolved`

- time: `2026-04-29 15:44:35 +09:00`
  location: `CGV seat-layout live API fetch`
  summary: `CGV_API_SECRET 값이 비어 있어 실제 CGV API 호출이 중단됨`
  details: `python scripts\cgv_collector_demo.py --mode movies 로 CGV signed API 호출을 시작했지만 CgvApiClient 가 CGV_API_SECRET 환경변수 또는 루트 .env 값이 필요하다고 중단했다. .env에는 CGV_API_SECRET 키는 있으나 값이 비어 있는 것으로 확인했다. API 기반 구현은 유지되며, 실제 좌석배치도 수신은 유효한 CGV_API_SECRET 설정 후 재시도해야 한다.`
  status: `open`

- time: `2026-04-29 16:38:26 +09:00`
  location: `browser-use CGV homepage access-scope check`
  summary: `browser-use URL 네비게이션이 Codex app-server 경로 오류로 실패`
  details: `CGV 홈페이지를 browser-use로 열어 .env CGV_API_SECRET 접근 가능 범위를 확인하려 했지만, 탭 생성과 about:blank 스크린샷은 성공한 반면 tab.goto("https://cgv.co.kr/")와 tab.goto("https://www.cgv.co.kr/")가 모두 "failed to start codex app-server: 지정된 경로를 찾을 수 없습니다. (os error 3)"로 실패했다. 이후 http://127.0.0.1:5500/ 이동은 정상 동작해 browser-use 전체 고장이 아니라 외부 도메인 네비게이션 경로 문제로 좁혀졌다. 사용자가 직접 https://cgv.co.kr/ 를 연 뒤 browser-use tabs.list 로 URL과 제목 "깊이 빠져 보다, CGV"는 확인했지만, DOM snapshot, screenshot, dev logs는 같은 app-server 오류로 실패했다. 직접 CGV API 호출은 사용자 요청에 따라 실행하지 않았다.`
  status: `open`

- time: `2026-04-30 13:19:30 +09:00`
  location: `AI recommendation runtime recovery`
  summary: `샌드박스 background 서버와 PyMySQL 경로가 런타임 점검을 막음`
  details: `Spring jar는 foreground 실행에서 정상 기동됐지만 샌드박스 내부 background 실행 프로세스는 명령 종료 뒤 유지되지 않아 127.0.0.1:8080 연결이 끊겼다. 샌드박스 밖에서 같은 jar를 PID 19872로 실행해 /api/health 200을 확인했다. 또한 기본 sandbox Python에서는 PyMySQL import가 실패했지만 사용자 Python site-packages에는 PyMySQL 1.1.2가 이미 있어 escalated 환경에서 read-only DB coverage probe를 수행했다.`
  status: `resolved`

- time: `2026-05-04 10:10:10 +09:00`
  location: `selfdex planning for daboyeo AI bridge`
  summary: `Selfdex read-only planning command timed out`
  details: `User invoked selfdex for the Codex/local-model recommendation bridge task. The read-only command python C:\Users\pc07-00\selfdex\scripts\plan_external_project.py --root C:\Users\pc07-00\selfdex --project-root C:\lsh\git\daboyeo --project-name daboyeo --format markdown timed out after 120 seconds, so implementation proceeds from the workspace source files and frozen STATE.md contract.`
  status: `deferred`

- time: `2026-04-30 13:31:23 +09:00`
  location: `AI recommendation jar restart`
  summary: `새 boot jar 첫 재시작이 기존 8080 서버와 충돌`
  details: `RecommendationService 수정 후 bootJar는 성공했지만, 기존 Spring PID 19872가 포트 8080을 계속 점유해 첫 새 jar 실행 PID 19036이 "Port 8080 was already in use"로 종료됐다. netstat로 PID 19872 점유를 확인하고 sandbox 밖에서 종료한 뒤 새 jar를 PID 14040으로 재시작해 /api/health 200과 추천 API entity 디코딩 결과를 확인했다.`
  status: `resolved`

- time: `2026-04-30 13:50:21 +09:00`
  location: `AI recommendation fallback differentiation`
  summary: `RecommendationService 기본 장르 필터 추가 중 Optional import 누락으로 compileJava 실패`
  details: `genre:popular 및 genre:일반콘텐트 같은 임시/기본 장르를 사유와 분석 포인트에서 제외하도록 RecommendationService를 수정한 뒤 focused Gradle test를 실행했지만 java.util.Optional import가 빠져 compileJava가 실패했다. import를 추가한 뒤 같은 focused test 세트가 통과했다.`
  status: `resolved`

- time: `2026-04-30 14:37:42 +09:00`
  location: `poster tag verification`
  summary: `샌드박스 Gradle native-platform.dll 로딩 실패`
  details: `포스터 태그/AI 페이지 버튼 수정 후 focused Gradle test를 샌드박스에서 실행했지만 native-platform.dll 로딩 실패로 Gradle이 시작되지 않았다. 같은 명령을 정상 Windows 권한으로 재실행해 PreferenceProfileBuilderTests, RecommendationScorerTests, RecommendationServiceQualityTests가 통과했다. PowerShell JSON 파서는 인코딩 문제로 포스터 manifest 파싱을 실패해 Node JSON 파싱으로 50/50 태그 커버리지를 확인했다.`
  status: `resolved`

- time: `2026-04-30 14:59:50 +09:00`
  location: `GPT recommendation analysis enhancement`
  summary: `샌드박스 Gradle 시작 실패와 fast 프롬프트 테스트 경계 누락`
  details: `GPT fast/precise 프롬프트 강화 후 focused Gradle test와 bootJar가 샌드박스에서 native-platform.dll 로딩 실패로 시작되지 않았다. 정상 Windows 권한으로 재실행했다. 첫 테스트 실행은 fast 프롬프트 안내문에 precise 전용 tradeoffHints 단어가 남아 실패했으며, 모드별 안내문을 분리한 뒤 LocalModelRecommendationClientTests, RecommendationServiceCandidateFilterTests, RecommendationServiceQualityTests가 통과했다.`
  status: `resolved`

- time: `2026-04-30 15:36:00 +09:00`
  location: `PR #1 selected patch import`
  summary: `PowerShell native pipe 기반 git apply가 UTF-8 patch 적용에 실패`
  details: `origin/ksg의 allMovies 파일만 선별 적용하려고 git diff 출력을 PowerShell pipe로 git apply에 넘겼지만 한글 포함 패치가 적용되지 않았다. 3-way 적용은 처음에 Git index lock 접근이 sandbox에서 막혔고, 권한 상승 후에도 같은 patch 적용 실패가 반복됐다. git diff --output으로 patch 파일을 만든 뒤 git apply로 적용해 해결했고 임시 patch 파일은 삭제했다.`
  status: `resolved`

- time: `2026-04-30 16:18:00 +09:00`
  location: `PR #2 nearby refresh focused tests`
  summary: `샌드박스 Gradle 시작 실패와 LiveMovieSearchCriteria 자정 넘김 누락`
  details: `PR #2 선별 import 후 focused Gradle test를 샌드박스에서 실행했지만 native-platform.dll 로딩 실패로 Gradle이 시작되지 않았다. 정상 Windows 권한으로 재실행하자 LiveMovieDemoDataService가 PR의 LiveMovieSearchCriteria.matchesTime() 변경까지 필요로 해 compileJava가 실패했다. LiveMovieSearchCriteria에 자정 넘김 허용과 matchesTime/crossesMidnight를 추가한 뒤 nearby refresh/showtime cleanup/sync/CGV seat focused test 세트가 통과했다.`
  status: `resolved`

- time: `2026-04-30 17:44:00 +09:00`
  location: `Spring 5500 Kakao Maps runtime check`
  summary: `backend 폴더 안에 Gradle wrapper가 없어 첫 bootJar 재시작 명령 실패`
  details: `Spring을 localhost:5500에서 재기동하려고 backend 폴더에서 .\gradlew.bat bootJar를 실행했지만 이 repo에는 backend/build.gradle만 있고 wrapper가 없어 CommandNotFoundException이 발생했다. 시스템 gradle bootJar 경로로 재시도한다.`
  status: `resolved - 시스템 gradle bootJar로 재빌드하고 Spring을 localhost:5500에서 재기동했다.`

- time: `2026-05-04 10:30:00 +09:00`
  location: `selfdex planning for daboyeo AI bridge`
  summary: `Selfdex planning timeout was handled by local source-driven implementation`
  details: `The selfdex planning command remained unavailable within the 120 second bound, so the AI bridge task was completed from the local repository contract in STATE.md and verified with focused Java/Python/JS checks plus runtime bridge smoke tests.`
  status: `resolved`

- time: `2026-05-04 10:30:00 +09:00`
  location: `AI bridge internal auth smoke`
  summary: `ResponseStatusException was wrapped as HTTP 500 by the global API handler`
  details: `Unauthenticated GET /api/internal/ai-bridge/jobs initially returned INTERNAL_ERROR 500 because ApiExceptionHandler did not preserve ResponseStatusException statuses. Added a dedicated ResponseStatusException handler and ApiExceptionHandlerTests; the same request now returns HTTP 401 with code UNAUTHORIZED.`
  status: `resolved`

- time: `2026-05-04 11:17:00 +09:00`
  location: `nearby Kakao map Spring rebuild`
  summary: `샌드박스 Gradle native-platform.dll 로딩 실패`
  details: `Nearby map SDK fix 검증을 위해 bootJar를 실행했지만 sandbox 안의 gradle이 native-platform.dll을 로드하지 못해 시작 실패했다. 같은 gradle bootJar 명령을 승인된 정상 Windows 권한으로 재실행해 빌드 성공 후 Spring static 리소스를 반영했다.`
  status: `resolved`

- time: `2026-05-04 11:18:00 +09:00`
  location: `nearby Kakao map Spring restart`
  summary: `첫 재시작에서 오래된 jar 파일명을 사용해 서버가 바로 종료됨`
  details: `Start-Process java -jar build\\libs\\daboyeo-backend-0.0.1-SNAPSHOT.jar 로 실행해 실제 생성된 daboyeo-backend-0.1.0-SNAPSHOT.jar와 파일명이 맞지 않았다. build/libs를 확인한 뒤 올바른 jar 경로로 재시작했고, 이후 샌드박스 밖 Start-Process로 8080 서버를 유지했다.`
  status: `resolved`

- time: `2026-05-04 12:35:46 +09:00`
  location: `selfdex planning for local/Codex recommendation runtime`
  summary: `Selfdex read-only planner timed out again`
  details: `The command python C:\Users\pc07-00\selfdex\scripts\plan_external_project.py --root C:\Users\pc07-00\selfdex --project-root C:\lsh\git\daboyeo --project-name daboyeo --format markdown timed out after 180 seconds. The task continues from the local source, existing STATE.md AI bridge contract, provider health output, Codex CLI check, and local model endpoint check.`
  status: `deferred`

- time: `2026-05-04 12:46:47 +09:00`
  location: `Codex bridge worker runtime`
  summary: `Codex exec is blocked by .codex session ACL`
  details: `Provider health reached codex ready after tokenized Spring/bridge worker startup, but an actual codex recommendation POST still fell back. Direct codex exec smoke reports that Codex cannot access session files under C:\Users\pc07-00\.codex\sessions because the sandbox group has read-only access. A proposed ACL change for only .codex\sessions and .codex\tmp was rejected by the safety reviewer until the user explicitly approves the security-setting change.`
  status: `blocked`

- time: `2026-05-04 12:56:17 +09:00`
  location: `Codex bridge worker runtime`
  summary: `Codex recommendation bridge runtime was verified after scoped ACL approval`
  details: `After explicit user approval, C:\Users\pc07-00\.codex\sessions and C:\Users\pc07-00\.codex\tmp were granted Modify for DESKTOP-EC0A64I\CodexSandboxUsers. WindowsApps codex.exe still could not run from the sandbox-outside worker context, so a local runtime copy was placed under gitignored backend/build/tools/codex.exe. codex exec schema smoke returned {"ok":true}, provider health reports codex ready, and POST /api/recommendations with aiProvider=codex returned status ok/model codex/count 3.`
  status: `resolved`

- time: `2026-05-04 13:33:00 +09:00`
  location: `entry showtime refresh smoke`
  summary: `Initial button-triggered refresh stayed running because TiDB persistence was too broad`
  details: `The new /api/showtimes/refresh endpoint returned quickly, but the background entry-refresh thread remained in CollectorBundlePersistenceService.findScreenId while writing many provider schedules to TiDB. Thread.print showed a MySQL SSL socket read inside showtime persistence. The entry path was tightened to one date, smaller discovery limits, and max 40 schedules per provider bundle; after restart, the refresh completed with 4 Lotte/Megabox bundles and 80 showtimes.`
  status: `resolved`

- time: `2026-05-04 14:13:54 +09:00`
  location: `hourly showtime sync verification`
  summary: `샌드박스 Gradle native-platform.dll 로딩 실패`
  details: `Hourly scheduled Lotte/Megabox sync 변경 후 focused Gradle test를 샌드박스에서 실행했지만 native-platform.dll 로딩 실패로 Gradle이 시작되지 않았다. 같은 gradle test 명령을 정상 Windows 권한으로 재실행해 ShowtimeSyncServiceTests가 통과했고, bootJar도 정상 Windows 권한으로 통과했다.`
  status: `resolved`

- time: `2026-05-04 14:15:00 +09:00`
  location: `hourly showtime sync runtime restart`
  summary: `PowerShell token generation API mismatch`
  details: `Spring/AI bridge 재시작 스크립트가 [System.Security.Cryptography.RandomNumberGenerator]::Fill을 호출했지만 현재 PowerShell/.NET 런타임에는 해당 정적 메서드가 없어 중단됐다. RNGCryptoServiceProvider.GetBytes 방식으로 바꿔 재시작을 완료했다.`
  status: `resolved`

- time: `2026-05-04 14:18:00 +09:00`
  location: `startup showtime sync Lotte ingest`
  summary: `롯데 release_date 원본 문자열 파싱 실패`
  details: `Startup sync에서 Lotte 수집 번들의 release_date가 yyyy-MM-dd 오전/오후 hh:mm:ss 형태로 들어와 LocalDate.parse가 실패했다. CollectorBundleIngestCommand.parseDate가 날짜 앞부분과 구분자 변형을 안전하게 처리하도록 보정했고 focused ingest/sync tests 및 bootJar가 통과했다.`
  status: `resolved`

- time: `2026-05-04 14:24:00 +09:00`
  location: `recommendation smoke during TiDB sync`
  summary: `추천 이력 저장 중 TiDB read timeout`
  details: `Startup sync가 TiDB에 showtimes를 쓰는 동안 추천 스모크 한 번이 recommendation_runs 저장 단계에서 Communications link failure로 503을 반환했다. RecommendationService.saveRun 경로를 best-effort로 낮춰 추천 결과 반환을 막지 않도록 수정했고 focused recommendation tests 및 bootJar가 통과했다.`
  status: `resolved`

- time: `2026-05-04 14:53:00 +09:00`
  location: `five recommendation smoke runtime restart`
  summary: `Spring/Codex bridge 재시작 스크립트의 PowerShell 변수/API 오류`
  details: `추천 5회 smoke 전 런타임 토큰으로 Spring과 bridge를 재시작하는 첫 스크립트에서 $PID 예약 변수명 충돌과 RandomNumberGenerator.Fill 미지원으로 기존 no-token Spring이 유지되어 provider health가 codex not_configured를 반환했다. 변수명을 $targetPid로 바꾸고 RandomNumberGenerator.Create().GetBytes()로 토큰을 생성해 Spring PID 22240, bridge PID 9200으로 재시작했으며 provider health가 codex ready로 회복됐다.`
  status: `resolved`

- time: `2026-05-04 15:10:00 +09:00`
  location: `GPT recommendation analysis prompt test`
  summary: `샌드박스 Gradle native-platform.dll 로딩 실패`
  details: `GPT/Codex 분석 프롬프트 수정 후 focused Gradle test를 샌드박스에서 실행했지만 native-platform.dll 로딩 실패로 Gradle이 시작되지 않았다. 같은 gradle test --tests kr.daboyeo.backend.service.recommendation.LocalModelRecommendationClientTests 명령을 정상 Windows 권한으로 재실행해 통과했다.`
  status: `resolved`

- time: `2026-05-04 15:30:06 +09:00`
  location: `genre-guided recommendation focused tests`
  summary: `샌드박스 Gradle native-platform.dll 로딩 실패`
  details: `장르 선택/분석 로직 개편 후 focused Gradle test를 샌드박스에서 실행했지만 native-platform.dll 로딩 실패로 Gradle이 시작되지 않았다. 같은 gradle test --tests kr.daboyeo.backend.service.recommendation.PreferenceProfileBuilderTests --tests kr.daboyeo.backend.service.recommendation.LocalModelRecommendationClientTests 명령을 정상 Windows 권한으로 재실행해 통과했다.`
  status: `resolved`

- time: `2026-05-04 15:30:42 +09:00`
  location: `genre-guided recommendation bootJar`
  summary: `샌드박스 Gradle native-platform.dll 로딩 실패`
  details: `장르 선택/분석 로직 개편 후 bootJar를 샌드박스에서 실행했지만 native-platform.dll 로딩 실패로 Gradle이 시작되지 않았다. 같은 gradle bootJar 명령을 정상 Windows 권한으로 재실행해 통과했다.`
  status: `resolved`

- time: `2026-05-04 15:57:00 +09:00`
  location: `genre-anchor scoring focused tests`
  summary: `샌드박스 Gradle native-platform.dll 로딩 실패`
  details: `선택 장르 앵커/점수 캡 수정 후 focused Gradle test를 샌드박스에서 실행했지만 native-platform.dll 로딩 실패로 Gradle이 시작되지 않았다. 같은 gradle test --tests kr.daboyeo.backend.service.recommendation.PreferenceProfileBuilderTests --tests kr.daboyeo.backend.service.recommendation.RecommendationScorerTests --tests kr.daboyeo.backend.service.recommendation.LocalModelRecommendationClientTests 명령을 정상 Windows 권한으로 재실행해 통과했다.`
  status: `resolved`

- time: `2026-05-04 16:12:47 +09:00`
  location: `taste-focused candidate pool tests`
  summary: `샌드박스 Gradle native-platform.dll 로딩 실패`
  details: `추천 후보 풀 선별 수정 후 focused Gradle test를 샌드박스에서 실행했지만 native-platform.dll 로딩 실패로 Gradle이 시작되지 않았다. 같은 gradle test --tests kr.daboyeo.backend.service.recommendation.RecommendationServiceCandidateFilterTests --tests kr.daboyeo.backend.service.recommendation.RecommendationScorerTests 명령을 정상 Windows 권한으로 재실행해 통과했다.`
  status: `resolved`

- time: `2026-05-04 17:32:25 +09:00`
  location: `Codex-scored reserve recommendation verification`
  summary: `샌드박스 Gradle native-platform.dll 로딩 실패 및 중복 헬퍼 컴파일 오류`
  details: `Codex 점수/예비후보 추천 수정 후 focused Gradle test와 bootJar를 샌드박스에서 실행했지만 native-platform.dll 로딩 실패로 Gradle이 시작되지 않았다. 정상 Windows 권한으로 focused test를 재실행하자 RecommendationService.tasteAnchorGenres 중복 정의 컴파일 오류가 드러났고, 기존 헬퍼 하나로 합친 뒤 같은 focused test와 bootJar가 통과했다. 런타임 smoke 중 새 Spring 프로세스에 bridge token이 없으면 Codex provider가 not_configured로 내려가서, 값을 출력하지 않는 임시 프로세스 토큰으로 Spring/bridge를 같이 재시작해 provider health를 ready로 회복했다.`
  status: `resolved`

- time: `2026-05-04 17:49:59 +09:00`
  location: `git staging before push`
  summary: `기본 sandbox에서 git index.lock 생성 권한 실패`
  details: `커밋 준비 중 git add -A가 C:/lsh/git/daboyeo/.git/index.lock Permission denied로 실패했다. 같은 git add -A를 정상 Windows 권한으로 재실행해 스테이징을 완료했다.`
  status: `resolved`

- time: `2026-05-05 14:03:00 +09:00`
  location: `localhost:5500 Spring runtime restart`
  summary: `샌드박스 Gradle loopback 오류와 PowerShell Start-Process 환경변수 중복`
  details: `Spring을 5500으로 재기동하는 과정에서 PowerShell Start-Process가 Path/PATH 중복 환경변수로 실패했고, 샌드박스 안 Gradle bootRun은 Unable to establish loopback connection으로 실패했다. 외부 실행 승인을 받은 뒤 숨김 cmd 프로세스에서 Gradle bootRun을 실행했고, /api/health가 HTTP 200을 반환했다.`
  status: `resolved`

- time: `2026-05-05 14:18:00 +09:00`
  location: `selective kmh import verification`
  summary: `샌드박스 Gradle loopback 오류 및 kmh 테스트 기대문구 불일치`
  details: `origin/kmh 204fab1 selective import 후 focused Gradle test를 샌드박스에서 실행했지만 Unable to establish loopback connection으로 Gradle이 시작되지 않았다. 정상 Windows 권한으로 재실행하자 LiveMovieControllerTests의 kmh 신규 테스트가 영어 BAD_REQUEST 메시지를 기대했지만 현재 API 계약은 한국어 메시지를 반환해 1개 실패했다. 테스트 기대값을 현재 API 메시지에 맞춘 뒤 같은 focused test와 bootJar가 통과했다.`
  status: `resolved`

- time: `2026-05-05 14:46:00 +09:00`
  location: `localhost:5500 Spring runtime restart`
  summary: `기존 PID 확인 누락과 샌드박스 Tomcat loopback 오류`
  details: `처음 Get-NetTCPConnection 결과가 비어 기존 5500 리스너가 없는 것으로 판단했지만 netstat 확인 결과 기존 Spring PID 30992가 여전히 LISTENING 중이었다. 기존 PID를 종료한 뒤 샌드박스 안에서 Java jar를 직접 실행하자 Tomcat이 Unable to establish loopback connection으로 실패했다. 정상 Windows 권한의 숨김 cmd 프로세스로 다시 시작해 새 PID 2004가 localhost:5500을 리슨했고 /api/health가 HTTP 200을 반환했다.`
  status: `resolved`

- time: `2026-05-05 15:27:20 +09:00`
  location: `anime poster seed generation`
  summary: `KOBIS 통계 테이블 중복 row와 실행 중 Spring 정적 리소스 미반영`
  details: `ranking-only dry run에서 KOBIS 역대 박스오피스 HTML의 반복 통계 테이블 때문에 같은 movieCd가 중복 선택됐다. 스크립트를 movieCd 기준 첫 all-time row만 쓰도록 고친 뒤 30개를 재산출했다. asset 생성 후 실행 중인 5500 서버가 새 anime-posters 리소스를 못 봐 500 NoResourceFoundException을 반환했으므로 build resources 동기화 후 Spring을 PID 21432로 재시작했고 /api/health 및 rank 1/rank 30 poster URL이 200을 반환했다.`
  status: `resolved`

- time: `2026-05-05 15:40:00 +09:00`
  location: `anime poster seed code review`
  summary: `스크린 수 0 메타데이터와 저해상도 포스터 후보`
  details: `코드리뷰에서 anime manifest의 screens가 전부 0이고 쿵푸 팬더 포스터가 150x215 썸네일급으로 선택된 점이 드러났다. 스크립트를 KOBIS td_totScrnCnt 셀을 읽도록 고치고, 선택된 30개 포스터는 KOBIS business 상세 후보까지 검사해 portrait 해상도 우선으로 고르도록 수정했다. 재생성 후 screens_zero=0, 최소 포스터 크기 600x861, 쿵푸 팬더 600x861, verify-only 및 런타임 asset check가 통과했다.`
  status: `resolved`

- time: `2026-05-05 20:08:00 +09:00`
  location: `poster folder split verification`
  summary: `기본 python 명령이 PATH에 없음`
  details: `포스터 경로 재배치 후 verify-only와 manifest integrity 검증을 기본 python 명령으로 실행했지만 PATH에서 python을 찾지 못해 실패했다. Codex bundled Python 경로를 확인한 뒤 같은 검증을 번들 Python으로 재실행했고 통과했다.`
  status: `resolved`

- time: `2026-05-05 20:19:00 +09:00`
  location: `anime poster pool focused Gradle tests`
  summary: `GRADLE_USER_HOME 상대 경로 오지정 및 샌드박스 native-platform.dll 로딩 실패`
  details: `focused Gradle test를 backend working directory에서 실행하면서 GRADLE_USER_HOME을 .\\backend\\.gradle-runtime으로 잡아 backend\\backend\\.gradle-runtime Resolve-Path가 실패했다. 이어 샌드박스 Gradle은 기존과 같은 native-platform.dll 로딩 실패로 시작하지 못했다. 올바른 .\\.gradle-runtime 경로와 정상 Windows 실행으로 재시도해 focused tests가 통과했다.`
  status: `resolved`

- time: `2026-05-05 20:24:00 +09:00`
  location: `localhost:5500 Spring restart after anime poster pool wiring`
  summary: `Gradle bootRun 재시작 명령 타임아웃`
  details: `새 poster-seed API를 반영하려고 기존 5500 리스너를 종료하고 Gradle bootRun으로 재시작했지만 명령이 180초 타임아웃됐고 이후 5500 리스너가 비어 있었다. bootJar 산출물은 이미 생성되어 있으므로 jar 직접 실행 방식으로 서버를 복구했고, localhost:5500 health가 200을 반환했다.`
  status: `resolved`

- time: `2026-05-05 20:26:00 +09:00`
  location: `git staging anime poster pool goal`
  summary: `기본 sandbox에서 git index.lock 생성 권한 실패`
  details: `검증 완료 후 git add -A를 실행했지만 D:/git/daboyeo/.git/index.lock Permission denied로 staging이 실패했다. 동일한 staging을 정상 Windows 권한으로 재시도해 완료했다.`
  status: `resolved`

- time: `2026-05-06 11:06:00 +09:00`
  location: `poster rename/tag enrichment verification`
  summary: `sandbox Gradle native DLL 실패와 구버전 5500 Spring PID 포트 점유`
  details: `focused Gradle tests와 bootJar는 sandbox에서 Windows native-platform.dll 로딩 실패로 시작하지 못해 정상 Windows 권한으로 재실행했고 둘 다 통과했다. 런타임 확인 중 기존 Spring PID 16648이 localhost:5500을 계속 점유해 새 jar가 포트 충돌로 종료되었고, PID 16648을 종료한 뒤 새 bootJar를 PID 13688로 재시작해 /api/health 및 poster-seed API/static asset 검증이 통과했다.`
  status: `resolved`

- time: `2026-05-06 12:32:00 +09:00`
  location: `Codex-only recommendation provider verification`
  summary: `sandbox Gradle native DLL 실패와 기본 샌드박스 Spring 자식 프로세스 종료`
  details: `Codex-only provider cleanup 후 focused Gradle test와 bootJar는 sandbox에서 Windows native-platform.dll 로딩 실패로 시작되지 않아 정상 Windows 권한으로 재실행했고 통과했다. 기본 샌드박스에서 Start-Process로 띄운 Java jar는 /api/health 200 확인 직후 command runner 종료와 함께 내려가서, 정상 Windows 권한으로 localhost:5500 Spring jar를 다시 실행했고 PID 13548에서 /api/health 및 served HTML/JS/provider health 검증이 통과했다.`
  status: `resolved`

- time: `2026-05-06 12:47:00 +09:00`
  location: `anime fallback genre evidence verification`
  summary: `sandbox Gradle native DLL 실패와 회귀 테스트 기대값 조정`
  details: `추천 장르 근거 수정 후 sandbox에서 focused Gradle test를 실행했지만 기존과 같은 Windows native-platform.dll 로딩 실패로 Gradle이 시작되지 않았다. 정상 Windows 권한으로 재실행한 첫 테스트에서는 새 explicit tense 테스트가 audience:friends 태그를 명시하지 않아 기대값이 실패했고, 테스트 데이터를 명시 태그 기반 계약에 맞춘 뒤 focused tests와 bootJar가 통과했다.`
  status: `resolved`

- time: `2026-05-06 12:55:35 +09:00`
  location: `selected-genre fallback verification`
  summary: `sandbox Gradle native DLL 실패 후 정상 Windows 권한 검증 통과`
  details: `선택 장르 fallback을 명시 장르 태그 기반 eligibility로 고친 뒤 sandbox Gradle test가 기존과 같은 native-platform.dll 로딩 실패로 시작되지 않았다. 정상 Windows 권한으로 focused RecommendationService/RecommendationScorer tests와 bootJar를 재실행해 통과했고, localhost:5500 Spring을 새 jar로 재시작해 animation 선택 smoke에서 no_selected_genre_candidates와 추천 0건을 확인했다.`
  status: `resolved`

- time: `2026-05-06 13:26:19 +09:00`
  location: `Codex bridge runtime connection`
  summary: `브릿지 heartbeat는 ready였지만 실제 Codex job 실행이 WinError 5로 실패`
  details: `기존 브릿지 워커는 provider health에서 polling 중으로 보였지만 실제 추천 job에서는 기본 Codex 실행 경로가 Access denied로 실패했다. Spring과 bridge를 같은 임시 토큰으로 재시작하고, bridge의 codex command를 ignored build 경로의 workspace-local codex.exe로 고정한 뒤 provider health와 실제 aiProvider=codex 추천 smoke가 통과했다. 토큰과 Codex 인증 내용은 기록하지 않았다.`
  status: `resolved`

- time: `2026-05-06 14:17:00 +09:00`
  location: `crawler ingest verification`
  summary: `sandbox Python의 PyMySQL 접근 불가와 schema verifier 확장 테이블 누락`
  details: `기본 샌드박스 Python은 AppData user site-packages 접근이 막혀 PyMySQL import에 실패했다. 정상 Windows 권한에서는 PyMySQL 접근과 TiDB 연결이 가능해 dry-run, bounded write ingest, DB count 검증을 진행했다. scripts/verify/verify_tidb_ingest.py는 collection_runs/canonical_movies/provider_raw_payloads 등 확장 테이블 누락으로 실패했지만, 이번 collect_all_to_tidb.py write 경로에 필요한 movies/theaters/screens/showtimes/movie_tags 코어 테이블은 존재해 적재와 런타임 smoke가 통과했다.`
  status: `resolved`

- time: `2026-05-06 14:40:40 +09:00`
  location: `selected-genre candidate fetch verification`
  summary: `sandbox Gradle native DLL 실패 후 정상 Windows 권한 테스트 통과`
  details: `선택 장르가 있을 때 후보 조회 폭을 넓히는 RecommendationService 변경 후 sandbox Gradle test가 기존과 같은 native-platform.dll 로딩 실패로 시작되지 않았다. 정상 Windows 권한으로 focused RecommendationServiceCandidateFilterTests를 재실행해 통과했다.`
  status: `resolved`

- time: `2026-05-06 14:47:56 +09:00`
  location: `Codex bridge restart after recommendation rebuild`
  summary: `bridge 재시작 인자 불일치 후 현재 스크립트 계약으로 재연결`
  details: `새 bootJar를 localhost:5500에 반영하면서 ai_bridge_agent.py를 과거 인자인 --base-url/--log-level로 시작해 bridge가 usage 오류로 종료됐다. 현재 스크립트 사용법인 --server/--codex-command/--codex-cwd로 Spring과 bridge를 같은 임시 토큰에 맞춰 재시작했고 /api/recommendation/providers/health에서 codex ready를 확인했다. 토큰과 인증 내용은 기록하지 않았다.`
  status: `resolved`
# 2026-05-06 15:29 +09:00

- time: `2026-05-06 15:29 +09:00`
- location: `backend gradle focused recommendation tests`
- summary: `Sandboxed Gradle could not initialize native services on Windows.`
- details: `gradle test --tests kr.daboyeo.backend.service.recommendation.RecommendationServiceCandidateFilterTests --tests kr.daboyeo.backend.service.recommendation.CodexRecommendationClientTests failed with native-platform.dll load error inside sandbox. The same focused command passed when rerun outside sandbox with approval.`
- status: `resolved`

# 2026-05-07 11:50 +09:00

- time: `2026-05-07 11:50 +09:00`
- location: `collect_all_to_tidb.py dry-run refresh`
- summary: `All-provider dry-run was blocked by a Megabox non-JSON master response.`
- details: `The requested refresh started with python scripts/ingest/collect_all_to_tidb.py --provider all --all-provider-dates --max-provider-dates 0 --megabox-date-days 14 --limit-movies 80 --limit-theaters 80 --dry-run. Lotte date discovery ran first, then Megabox build_area_records(fetch_master) raised JSONDecodeError because the provider response body was not JSON. A second Megabox dry-run for playDe=20260507 failed the same way. No Megabox DB write was attempted. Lotte was refreshed separately and DB max starts_at advanced to 2026-05-14 23:35:00.`
- status: `open; Megabox provider response still needs follow-up`

# 2026-05-07 10:13 +09:00

- time: `2026-05-07 10:13 +09:00`
- location: `AI bridge restart and live benchmark`
- summary: `Bridge jobs initially fell back because the worker resolved the WindowsApps Codex executable, then the worker exited during a Spring restart.`
- details: `ai-bridge.err.log showed WinError 5 access denied when the worker tried to execute the WindowsApps codex.exe path. Restarting the bridge with the workspace-local backend/build/codex-bin/codex.exe restored precise status=ok. During the later Spring restart, the worker exited on a transient ConnectionRefused heartbeat failure, so scripts/ai_bridge_agent.py now catches server polling errors, sleeps, and retries instead of terminating. Tokens were loaded through environment only and were not logged.`
- status: `resolved`

# 2026-05-07 14:44 +09:00

- time: `2026-05-07 14:44 +09:00`
- location: `14-day Lotte ingest`
- summary: `The first broad Lotte 14-day loop hit the shell timeout after committing 2026-05-07 through 2026-05-14, and a follow-up 2026-05-15 batch lost the TiDB connection before showtime rows were written.`
- details: `Post-timeout DB audit showed Lotte showtimes through 2026-05-14 and 200 Lotte priced showtimes. The 2026-05-15 retry failed with pymysql OperationalError 2013 Lost connection to MySQL server during query, and audit showed no 2026-05-15 through 2026-05-20 Lotte rows yet. The likely cause is a long provider lookup leaving the TiDB connection idle before the first later-date upsert. collectors/common/repository.py now pings/reconnects before repository execute/executemany calls.`
- status: `resolved; repository DB writes now ping/reconnect before execute, and the 2026-05-15 retry plus later Lotte dates completed.`

# 2026-05-06 15:38 +09:00

- time: `2026-05-06 15:38 +09:00`
- location: `Spring restart on localhost:5500`
- summary: `Initial background restart attempts failed because the sandbox shell had duplicate Path/PATH environment keys and old PID 16976 still owned port 5500.`
- details: `gradle bootJar had the same sandbox native-platform.dll failure and passed outside sandbox. Start-Process failed until the process environment Path key was normalized. The stale Java process on port 5500 was stopped, then the rebuilt jar was started outside sandbox as PID 20368 with startup showtime sync disabled. /api/health returned ok and the AI page served the 20260506-fast-cta cache key.`
- status: `resolved`

# 2026-05-06 15:46 +09:00

- time: `2026-05-06 15:46 +09:00`
- location: `AI bridge worker startup`
- summary: `Initial bridge worker launches did not register a Codex heartbeat because the launch environment could not see the Local OpenAI Codex path and a quoted WindowsApps path was split by Start-Process.`
- details: `The repo .env token was used through process environment only, not printed or passed as a command-line token. The first worker started but advertised no active codex provider because DABOYEO_CODEX_COMMAND pointed to an invisible Local path in the elevated launch environment. The second attempt failed from a space-split codex path. Restarting without DABOYEO_CODEX_COMMAND let Python resolve codex.exe from PATH and provider health returned ready.`
- status: `resolved`

# 2026-05-06 15:52 +09:00

- time: `2026-05-06 15:52 +09:00`
- location: `AI bridge Codex job execution`
- summary: `Provider health was ready but recommendation jobs still fell back because the bridge worker resolved the WindowsApps codex.exe path, which cannot be executed directly from the worker.`
- details: `ai-bridge.err.log showed WinError 5 access denied for Codex jobs. Local OpenAI codex.exe executed correctly in the interactive shell, while WindowsApps codex.exe returned access denied. A workspace-local ignored copy was placed at backend/build/tools/codex.exe and the bridge worker was restarted with that explicit executable. Provider health returned ready and a fast recommendation API smoke returned status=ok, model=codex, and 3 recommendations.`
- status: `resolved`

# 2026-05-06 16:20 +09:00

- time: `2026-05-06 16:20 +09:00`
- location: `genre metadata enrichment dry-run setup`
- summary: `Default Python lacked PyMySQL and sandboxed pip could not unpack wheels due Windows temp permission errors.`
- details: `The enrichment scripts require PyMySQL for TiDB access. Installing PyMySQL into backend/build/tmp/pymysql_vendor_current failed inside the sandbox even after redirecting TMP/TEMP into the workspace, then succeeded with approved elevated pip install scoped to the ignored build/tmp target.`
- status: `resolved`

# 2026-05-06 17:00 +09:00

- time: `2026-05-06 17:00 +09:00`
- location: `AI bridge Codex exec after Spring restart`
- summary: `Codex bridge worker was ready but jobs fell back because Codex CLI plugin sync failed with 403 before producing a result.`
- details: `The recommendation worker does not need user/plugin config. scripts/ai_bridge_agent.py was changed to run codex exec with --ignore-user-config while keeping CODEX_HOME auth available, avoiding plugin sync during bridge jobs.`
- status: `resolved`

# 2026-05-06 17:14 +09:00

- time: `2026-05-06 17:14 +09:00`
- location: `AI bridge Codex exec after Spring restart`
- summary: `Codex bridge still needed plugin feature suppression after --ignore-user-config.`
- details: `A workspace-local codex.exe mini run succeeded with --disable plugins even though analytics warnings still printed 403. scripts/ai_bridge_agent.py now passes --ignore-user-config and --disable plugins before bridge jobs; live recommendation smokes returned status=ok/model=codex.`
- status: `resolved`

# 2026-05-06 17:14 +09:00

- time: `2026-05-06 17:14 +09:00`
- location: `localhost:5500 Spring restart`
- summary: `PowerShell DB URL interpolation broke the restarted Spring datasource URL.`
- details: `The first restart script built jdbc:mysql://.../$db?serverTimezone... with PowerShell interpolation, so $db? was parsed incorrectly and Spring tried to connect to an invalid database name. The server was restarted again using explicit string concatenation for the JDBC URL; /api/health, provider health, and live recommendation smokes passed.`
- status: `resolved`

# 2026-05-07 10:47 +09:00

- time: `2026-05-07 10:47 +09:00`
- location: `fast/precise Codex model split verification`
- summary: `Sandbox Gradle native DLL failure and bridge reasoning fallback bug were found during verification.`
- details: `The focused Gradle recommendation test command again failed inside the sandbox with native-platform.dll initialization, then passed outside sandbox with approval. A script-level bridge option check also caught that a fast job with request.model but no request.reasoning_effort would inherit DABOYEO_CODEX_REASONING_EFFORT; scripts/ai_bridge_agent.py now treats that case as true default reasoning while still allowing legacy env fallback when a job has no model. Tokens were not printed or written.`
- status: `resolved`

# 2026-05-07 11:17 +09:00

- time: `2026-05-07 11:17 +09:00`
- location: `Megabox no-write schedule mismatch probe`
- summary: `A live Megabox verification probe returned non-JSON after earlier successful dry-run/date probes.`
- details: `After adding the deterministic row-level date filter, a no-write probe for playDe=20260611 attempted to fetch Megabox master/schedule data and failed with JSONDecodeError because the provider returned a non-JSON response. The acceptance check used local helper assertions, script compile, and bounded dry-run instead; no DB write was attempted.`
- status: `resolved`

# 2026-05-07 17:46 +09:00

- time: `2026-05-07 17:46 +09:00`
- location: `Spring restart on localhost:5500`
- summary: `Initial restart attempts failed because this PowerShell process had duplicate Path/PATH environment keys, and a fallback clean environment started Java with an invalid Windows socket state.`
- details: `Start-Process first failed with a duplicate Path/PATH key error. Starting with UseNewEnvironment avoided that error but Tomcat failed with Windows socket error 10106. cmd start quoting attempts also failed. The resolved restart normalized the process environment to a single Path key, then Start-Process launched the rebuilt jar as PID 20428; /api/health returned ok.`
- status: `resolved`

# 2026-05-08 11:01 +09:00

- time: `2026-05-08 11:01 +09:00`
- location: `poster quality Spring restart on localhost:5500`
- summary: `The first restart attempts did not replace the old localhost:5500 Java process and Start-Process again hit duplicate Path/PATH environment handling.`
- details: `PID 5736 was still serving localhost:5500, so the first new process could not take the port. After stopping PID 5736, Start-Process failed with duplicate Path/PATH. A direct .NET ProcessStartInfo launch outside the sandbox started the rebuilt jar as PID 13640; /api/health returned ok and served static checks confirmed the poster-quality cache key and CSS/JS changes.`
- status: `resolved`

# 2026-05-08 13:03 +09:00

- time: `2026-05-08 13:03 +09:00`
- location: `floating poster Spring restart on localhost:5500`
- summary: `Served static checks initially read stale AI page files, and sandboxed background launches did not persist after the shell command ended.`
- details: `gradle processResources and bootJar succeeded and build/resources matched frontend/static sources, but the existing PID 13640 served the old cache key. Get-NetTCPConnection was denied, so netstat identified PID 13640. After stopping it, bootRun and java subprocess attempts inside the sandbox either hit duplicate Path/PATH handling or were cleaned up after the command returned. The resolved launch normalized Path/PATH and started the rebuilt jar outside the sandbox as PID 10580; /api/health returned 200 and served HTML/CSS/JS checks confirmed the floating-poster cache key and scroll/floating CSS.`
- status: `resolved`

# 2026-05-08 13:20 +09:00

- time: `2026-05-08 13:20 +09:00`
- location: `AI result snap-scroll Spring restart on localhost:5500`
- summary: `Served static checks stayed stale until the rebuilt jar replaced the previous localhost:5500 Java process.`
- details: `frontend and backend static sources matched, and gradle processResources updated build/resources, but the running PID 10580 still served the floating-poster cache key. A sandboxed Start-Process launch returned a healthy response once and then did not persist outside the command lifetime. The resolved launch used an approved out-of-sandbox hidden Start-Process with log redirection; the rebuilt jar is running as PID 16476, /api/health returns ok, and served HTML/CSS checks confirm 20260508-floating-snap plus hidden-scrollbar and mandatory snap CSS.`
- status: `resolved`

# 2026-05-08 14:18 +09:00

- time: `2026-05-08 14:18 +09:00`
- location: `AI result one-card snap verification`
- summary: `The previous localhost:5500 process returned 500 for the AI page and the in-app browser automation plugin was missing browser-client.mjs during visual verification.`
- details: `PID 16476 still passed /api/health but returned 500 for /src/pages/daboyeoAi.html, with a Spring error-page NoClassDefFoundError in the previous log. After bootJar, the process was replaced by an approved out-of-sandbox launch as PID 9160; /api/health returned ok and served HTML/CSS/JS checks confirmed the result-snap-clean cache key and updated rules. In-app browser automation could not be completed because C:\Users\pc07-00\.codex\plugins\cache\openai-bundled\browser-use\0.1.0-alpha2\scripts\browser-client.mjs was absent; verification fell back to syntax, static parity, build, health, and served resource checks.`
- status: `resolved-with-browser-gap`

# 2026-05-08 14:55 +09:00

- time: `2026-05-08 14:55 +09:00`
- location: `Codex AI bridge precise recommendation runtime`
- summary: `The bridge heartbeat was ready, but a precise Codex job timed out and then failed to report because Spring had already removed the job.`
- details: `Provider health returned ready, but .local/ai-bridge.err.log showed gpt-5.5 with xhigh reasoning timing out after 180 seconds, followed by HTTP 404 from /api/internal/ai-bridge/jobs/{jobId}/result. The repair lowers local precise defaults to gpt-5.4/medium with fewer candidates and tokens, shortens Spring bridge timeouts, and makes the worker timeout shorter than Spring's result wait so stale job reports do not linger.`
- status: `resolved`
