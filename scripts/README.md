# Scripts

로컬 실행, 수집 검증, DB 확인, R2 확인용 스크립트 영역이다.

## 디렉터리

- `db/`: TiDB migration 적용과 스키마 검사 실행 스크립트.
- `ingest/`: 수집 결과를 TiDB/R2 파이프라인으로 넘기는 진입점.
- `storage/`: Cloudflare R2 연결과 object 확인 스크립트.
- `verify/`: 적재 후 row count, 중복 정책, snapshot append 검증 스크립트.

## R2 포스터

- 고정 추천 포스터는 `storage/upload_seed_posters_to_r2.py --write`로 R2에 업로드한다.
- 수집 중 발견된 provider 포스터는 `ingest/collect_all_to_tidb.py`가 R2 설정이 있을 때 자동으로 미러링한다.
- R2가 없거나 실패하면 수집은 계속 진행하고 TiDB에는 원본 포스터 URL과 실패 상태를 남긴다.

## 시연용 Codex 브릿지

- `ai_bridge_agent.py`는 `.env`의 `DABOYEO_BRIDGE_SERVER`, `DABOYEO_AI_BRIDGE_TOKEN`, `DABOYEO_BRIDGE_PROVIDERS`를 자동으로 읽는다.
- 서버 env에서 `DABOYEO_AI_BRIDGE_TOKEN`을 비우면 브릿지 엔드포인트와 Codex provider가 비활성화된다.
- 포트폴리오 배포에서는 worker를 서비스로 등록하지 말고, 시연 중에만 `python scripts/ai_bridge_agent.py`로 실행한다.
- Electron GUI는 `tools/bridge-gui`에 두며, 자동실행 없이 버튼으로만 worker를 시작/종료한다.

## 포트폴리오 공개 API 가드

- Oracle 배포에서는 `DABOYEO_PUBLIC_COLLECTION_ENABLED=false`, `DABOYEO_PUBLIC_NEARBY_REFRESH_ENABLED=false`, `DABOYEO_PUBLIC_SEAT_LAYOUT_ENABLED=false`를 기본으로 둔다.
- 관리자가 수동 refresh/crawl을 호출해야 할 때만 `DABOYEO_ADMIN_TOKEN`을 설정하고 `X-DABOYEO-ADMIN-TOKEN` 헤더로 호출한다.
- `DABOYEO_FRONTEND_ORIGINS`는 배포 origin을 정확히 적는다. `http://*:5173` 같은 wildcard dev-port origin은 운영 env에 넣지 않는다.

## Oracle 배포

- `deploy/deploy_oracle_portfolio.ps1`는 `.env`의 `ORACLE_HOST`, `ORACLE_USER`, `ORACLE_SSH_KEY_PATH`를 읽어 Oracle VM에 jar/env/브릿지 worker를 반영한다.
- 서버로 올리는 env에서는 `ORACLE_*` 키를 자동 제외하고, 출력에는 토큰이나 비밀번호 값을 찍지 않는다.
- 배포 env에는 상영 정보 1시간 갱신을 명시한다. `DABOYEO_SYNC_PYTHON=python3`, `DABOYEO_SHOWTIME_SYNC_ENABLED=true`, `DABOYEO_SHOWTIME_SYNC_CRON="0 0 * * * *"`, `DABOYEO_SHOWTIME_STARTUP_ENABLED=false`를 서버용 env에 반영한다.
- Spring의 hourly sync가 Python 수집기를 import할 수 있도록 `collectors/`, `scripts/ingest/`, `requirements.txt`도 함께 배포한다.
- remote install은 systemd `User=` 또는 `/opt/daboyeo` 소유자를 감지해서 적용한다.
- 먼저 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/deploy/deploy_oracle_portfolio.ps1 -DryRun`으로 계획만 확인한다.
- 실제 반영은 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/deploy/deploy_oracle_portfolio.ps1`로 실행한다.

## 원칙

- 실제 SQL migration 파일은 `db/migrations/`에 둔다.
- 스크립트는 `.env`를 읽되 비밀번호나 secret을 출력하지 않는다.
- 외부 쓰기 작업은 명령 이름과 출력에서 명확히 드러나야 한다.
