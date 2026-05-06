# Backend

Spring Boot 기반 API 서버 영역이다.

백엔드는 수집기를 대체하지 않는다. CGV, 롯데시네마, 메가박스 원본 데이터 수집은 기존 `collectors/`와 `scripts/`를 우선 보존하고, 백엔드는 TiDB/MySQL에 저장된 상영 데이터와 추천 상태를 조회, 정리, 응답하는 API와 서비스 로직에 집중한다.

## 기술 스택

- Java 21
- Spring Boot 3.5.13
- Gradle
- Spring Web REST API
- Spring JDBC / `JdbcTemplate`
- TiDB Cloud 또는 MySQL 호환 DB
- MySQL Connector/J
- Flyway MySQL migration 준비, 기본값은 비활성화
- JUnit 5 / Spring Boot Test
- Codex bridge recommendation worker

아직 Gradle Wrapper는 넣지 않았다. 로컬에서는 설치된 `gradle` 명령을 사용한다.

## 백엔드 방향성

- 프론트엔드에 영화, 극장, 상영 시간, 좌석 요약, 예매 링크, 추천 결과를 제공한다.
- 검색과 비교의 기준은 `showtimes` 중심의 최소 공통 필드로 맞춘다.
- 제공사별 원본 데이터 특성은 수집기와 DB의 `raw_json` 또는 R2 object key로 보존한다.
- 사용자 추천은 익명 세션 기반으로 처리하고 이름, 이메일, 전화번호, 인증 토큰, 쿠키를 저장하지 않는다.
- AI 추천은 Codex bridge를 통해 GPT가 후보를 비교하되, 최종 응답은 DB에 존재하는 상영 후보만 반환한다.
- 비밀값, DB 접속 정보, 모델 설정은 환경변수로 주입한다.

## 로컬 실행

```powershell
cd backend
gradle bootRun
```

기본 포트는 `8080`이다.

DB가 없더라도 앱이 바로 죽지 않도록 Hikari fail-fast를 꺼두었다. 실제 DB 기능을 사용할 때는 루트 `.env.example`의 `TIDB_*` 또는 `DABOYEO_DB_*` 값을 참고한다. 백엔드는 시작할 때 워크스페이스 루트 `.env`를 자동으로 읽고, `DABOYEO_DB_*`가 없으면 `TIDB_HOST`, `TIDB_PORT`, `TIDB_DATABASE`, `TIDB_USER`, `TIDB_PASSWORD`에서 Spring JDBC 설정을 자동으로 만든다. 이 값은 최종적으로 `spring.datasource.*`에도 연결되므로 기본 localhost DB URL로 조용히 떨어지지 않는다. TiDB Cloud 호스트는 TLS(`useSSL=true`)를 기본으로 사용한다. Windows 편집기가 `.env`를 UTF-8 BOM으로 저장해도 첫 키를 정상적으로 읽는다. 같은 키가 OS 환경변수에도 있으면 OS 환경변수가 우선한다.

## 주요 환경변수

- `DABOYEO_BACKEND_PORT`: 백엔드 포트, 기본값 `8080`
- `DABOYEO_DB_URL`: JDBC URL
- `DABOYEO_DB_USERNAME`: DB 사용자
- `DABOYEO_DB_PASSWORD`: DB 비밀번호
- `TIDB_HOST`, `TIDB_PORT`, `TIDB_DATABASE`, `TIDB_USER`, `TIDB_PASSWORD`, `TIDB_SSL`: `DABOYEO_DB_*`가 없을 때 자동 변환되는 TiDB/MySQL 접속값
- `DABOYEO_FLYWAY_ENABLED`: Flyway 실행 여부, 기본값 `false`
- `DABOYEO_FRONTEND_ORIGINS`: 허용할 프론트엔드 origin 목록
- `DABOYEO_RECOMMEND_CODEX_MODEL`: Codex bridge 추천 모델명, 기본값 `codex`
- `DABOYEO_RECOMMEND_CODEX_FAST_AI_CANDIDATE_LIMIT`: 빠른 추천에서 Codex로 넘길 후보 수, 기본값 `12`
- `DABOYEO_RECOMMEND_CODEX_PRECISE_AI_CANDIDATE_LIMIT`: 정밀 추천에서 Codex로 넘길 후보 수, 기본값 `20`
- `DABOYEO_AI_BRIDGE_TOKEN`: Codex bridge worker 인증 토큰

## 수집 번들 적재와 live movies API

`movies.html` 연동용 백엔드는 `showtimes` 공통 테이블을 읽는다. 수집기 번들은 아래 태스크로 dry-run 후 적재할 수 있다.

```powershell
cd backend
gradle ingestCollectorBundle -Pprovider=CGV -PbundlePath=../backend/build/tmp/cgv-bundle.json
gradle ingestCollectorBundle -Pprovider=LOTTE_CINEMA -PbundlePath=../backend/build/tmp/lotte-bundle.json
gradle ingestCollectorBundle -Pprovider=MEGABOX -PbundlePath=../backend/build/tmp/megabox-bundle.json
```

- 기본 동작은 dry-run이고, 실제 upsert는 `-Pwrite=true`를 붙였을 때만 수행한다.
- 호환용 태스크로 `ingestCgvBundle`, `ingestLotteBundle`, `ingestMegaboxBundle`도 제공한다.
- 루트 PowerShell 진입점은 `scripts/ingest_collector_to_tidb.ps1`이고, `scripts/ingest_cgv_to_tidb.ps1`는 CGV 래퍼다.
- live movies 조회 엔드포인트는 `GET /api/live/nearby`, 상세 시간표는 `GET /api/live/movies/{movieKey}/schedules`를 사용한다.

스케줄러는 기본적으로 꺼져 있고, 명시적으로 켰을 때만 돈다.

```env
DABOYEO_SYNC_ENABLED=true
DABOYEO_SHOWTIME_SYNC_ENABLED=true
DABOYEO_SEAT_SYNC_ENABLED=true
```

## 추천 API 방향

추천 API는 빠른 모드와 정밀 모드를 나눈다.

- 빠른 모드: Codex bridge로 후보를 빠르게 비교하고 짧은 근거를 생성한다.
- 정밀 모드: Codex bridge로 후보와 취향 근거를 더 깊게 비교한다.
- Codex 응답이 지연되거나 깨지면 서버의 코드 점수 기반 fallback으로 DB 후보 추천을 유지한다.
- public response shape는 호환성을 우선한다. 필드 제거보다 빈 값 또는 호환 필드 추가를 선호한다.

## 검증 명령

```powershell
cd backend
gradle test
gradle bootJar
```

추천 로직만 빠르게 볼 때는 아래처럼 범위를 좁힌다.

```powershell
cd backend
gradle test --tests kr.daboyeo.backend.service.recommendation.* --tests kr.daboyeo.backend.repository.recommendation.*
```

## 패키지 경계

- `api`: HTTP 컨트롤러와 예외 응답
- `service`: 유스케이스, 추천 흐름, fallback 로직
- `repository`: DB 조회와 저장
- `domain`: 도메인 모델과 값 객체
- `config`: 설정 바인딩, CORS, 인프라 설정
