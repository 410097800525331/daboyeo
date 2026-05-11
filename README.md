# DABOYEO

DABOYEO는 영화 상영 정보, 극장 위치, 좌석/상영 조건, 가격 비교, AI 추천을 한 화면에서 확인하기 위한 포트폴리오용 영화 예매 보조 서비스다.

배포 주소: http://129.146.142.223/

현재 배포 기준은 Spring Boot 백엔드가 정적 프론트엔드까지 함께 서빙하는 구조다. 운영 포트는 기본 `5500`이고, 데이터 저장소는 TiDB/MySQL 호환 DB를 사용한다.

## 현재 배포 기준

- 런타임: Java 21, Spring Boot 3.5.13
- 프론트엔드: vanilla HTML/CSS/JavaScript
- 정적 파일 배포 위치: `backend/src/main/resources/static`
- 원본 프론트엔드 작업 위치: `frontend`
- DB: TiDB Cloud 또는 MySQL 호환 DB
- 수집기: Python 기반 롯데시네마/메가박스 수집
- 현재 사용 provider: `LOTTE_CINEMA`, `MEGABOX`
- CGV는 현재 배포 대상에서 제외한다.
- 추천 기본값은 `fallback`이며, Codex AI 추천은 브릿지 worker가 실행 중일 때만 활성화한다.

## 주요 기능

- 지역/좌표 기반 주변 영화관 검색
- 반경 8km 기준 주변 상영 정보 조회
- 롯데시네마/메가박스 상영 정보 수집 및 TiDB 저장
- 상영 시간대, 좌석 상태, 특별관, 영화관 조건 필터
- 이벤트/영화 포스터 정적 화면 제공
- 사용자 입력 기반 영화 추천
- Codex bridge를 통한 AI 추천 결과 생성

## 디렉터리

- `backend/`: Spring Boot API, 정적 프론트 서빙, 추천/수집 연동 로직
- `frontend/`: 프론트엔드 원본 파일
- `collectors/`: 영화관별 Python 수집기
- `scripts/`: 배포, 수집, 검증, AI 브릿지 worker 스크립트
- `db/`: DB migration 및 schema 관련 파일
- `docs/`: 보조 문서
- `tools/bridge-gui/`: AI 브릿지 worker 실행용 Electron GUI

## 환경 변수

실제 비밀값은 `.env`에만 둔다. `.env`는 커밋하지 않는다.

```powershell
Copy-Item .env.example .env
```

필수 계열:

- `TIDB_HOST`, `TIDB_PORT`, `TIDB_USER`, `TIDB_PASSWORD`, `TIDB_DATABASE`
- `DABOYEO_BACKEND_PORT=5500`
- `DABOYEO_FRONTEND_ORIGINS`
- `DABOYEO_SYNC_PYTHON`
- `ORACLE_HOST`, `ORACLE_USER`, `ORACLE_SSH_KEY_PATH`

공개 배포 기본 정책:

- `DABOYEO_PUBLIC_COLLECTION_ENABLED=false`
- `DABOYEO_PUBLIC_SEAT_LAYOUT_ENABLED=false`
- `DABOYEO_PUBLIC_NEARBY_REFRESH_ENABLED=true`
- `DABOYEO_SHOWTIME_NEARBY_REFRESH_RADIUS_KM=8`

## 로컬 실행

환경 변수는 PowerShell 세션, IDE 실행 설정, 또는 별도 env loader로 주입한다.

```powershell
gradle -p backend bootRun --no-problems-report
```

빌드만 확인하려면:

```powershell
gradle -p backend bootJar --no-problems-report
```

실행 후 확인:

```text
http://localhost:5500/
http://localhost:5500/api/health
```

## AI 추천과 Codex 브릿지

Codex 기반 AI 추천은 Spring 서버만 켜서는 동작하지 않는다. 반드시 별도 bridge worker가 실행 중이어야 한다.

필수 조건:

- Codex CLI 또는 Codex 데스크톱 앱이 설치되어 있어야 한다.
- `codex` 명령이 PATH에서 실행 가능해야 한다.
- bridge worker를 실행하는 계정에서 Codex 로그인이 완료되어 있어야 한다.
- 서버 env와 worker env의 `DABOYEO_AI_BRIDGE_TOKEN` 값이 같아야 한다.
- 서버 추천 provider가 Codex를 사용하도록 설정되어 있어야 한다.

Codex 설치/로그인 확인:

```powershell
codex --version
codex login
codex login status
```

서버 쪽 AI 관련 env 예시:

```dotenv
DABOYEO_RECOMMEND_PROVIDER=codex
DABOYEO_AI_BRIDGE_TOKEN=<long-random-bridge-token>
DABOYEO_RECOMMEND_CODEX_FAST_MODEL=gpt-5.4-mini
DABOYEO_RECOMMEND_CODEX_PRECISE_MODEL=gpt-5.5
DABOYEO_RECOMMEND_CODEX_PRECISE_REASONING_EFFORT=xhigh
```

worker 쪽 env 예시:

```dotenv
DABOYEO_BRIDGE_SERVER=http://127.0.0.1:5500
DABOYEO_AI_BRIDGE_TOKEN=<same-token-as-server>
DABOYEO_BRIDGE_PROVIDERS=codex
DABOYEO_CODEX_COMMAND=codex
DABOYEO_CODEX_CWD=.
```

로컬 worker 실행:

```powershell
python scripts/ai_bridge_agent.py
```

GUI로 실행하려면:

```powershell
.\start-bridge-gui.cmd
```

Oracle 서버를 대상으로 로컬 PC에서 bridge worker를 돌릴 때는 `DABOYEO_BRIDGE_SERVER`를 배포 서버 주소로 바꾼다.

```dotenv
DABOYEO_BRIDGE_SERVER=http://<oracle-host>
```

브릿지가 정상 실행되면 `/api/recommendation/providers/health`에서 Codex provider 상태가 ready/available 계열로 바뀐다. 브릿지가 꺼져 있으면 추천은 fallback 결과를 사용한다.

## 상영 정보 수집

Spring 서버는 설정된 cron에 따라 상영 정보를 갱신한다.

주요 설정:

```dotenv
DABOYEO_SYNC_ENABLED=true
DABOYEO_SHOWTIME_SYNC_ENABLED=true
DABOYEO_SHOWTIME_SYNC_CRON="0 0 * * * *"
DABOYEO_SHOWTIME_DATE_OFFSETS=0,1,2
DABOYEO_SHOWTIME_AUTO_DISCOVERY_ENABLED=true
DABOYEO_SHOWTIME_ENTRY_REFRESH_ENABLED=true
DABOYEO_SHOWTIME_NEARBY_REFRESH_ENABLED=true
DABOYEO_SHOWTIME_NEARBY_REFRESH_RADIUS_KM=8
DABOYEO_SYNC_PYTHON=python
```

Oracle 배포 스크립트는 서버 환경에 맞춰 `DABOYEO_SYNC_PYTHON=python3`을 강제한다.

## Oracle 배포

배포는 PowerShell 스크립트로 진행한다. 스크립트는 `.env`를 읽고, `ORACLE_*` 값은 원격 env 업로드 대상에서 제외한다.

먼저 dry-run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/deploy/deploy_oracle_portfolio.ps1 -DryRun
```

빌드 포함 실제 배포:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/deploy/deploy_oracle_portfolio.ps1 -Build
```

배포 스크립트가 처리하는 것:

- `backend` bootJar 빌드
- sanitizer를 거친 env 업로드
- app jar 업로드
- `scripts/ai_bridge_agent.py` 업로드
- `collectors/`, `scripts/ingest/`, `requirements.txt` 수집 런타임 업로드
- systemd 서비스 재시작
- public `/api/health` 확인
- Codex provider health 확인

AI 추천을 시연하려면 배포 후에도 bridge worker를 별도로 실행해야 한다. worker는 상시 서비스가 아니라 시연 중에만 켜는 운영을 기본으로 한다.

## 검증 명령

기본 검증:

```powershell
git status --short
gradle -p backend test --no-problems-report
gradle -p backend bootJar --no-problems-report
git diff --check
```

프론트 JS를 바꿨다면 해당 파일에 대해:

```powershell
node --check frontend/src/js/liveMovies.js
```

정적 파일을 바꿨다면 `frontend` 원본과 `backend/src/main/resources/static` mirror가 같은지 확인한다.

## 운영 주의사항

- 비밀값, 토큰, 쿠키, SSH key 경로는 `.env`에만 둔다.
- `.env`, 로컬 로그, task board, 임시 산출물은 main에 올리지 않는다.
- public collection과 seat layout API는 기본적으로 닫아 둔다.
- nearby refresh만 제한적으로 열고 rate limit/TTL로 수집 트리거를 제한한다.
- Codex bridge token이 비어 있으면 Codex provider는 비활성화된다.
- Codex bridge worker는 추천 요청을 처리하기 위해 `codex exec`를 호출한다. 따라서 worker 실행 계정의 Codex 설치와 로그인이 실제 운영 조건이다.
