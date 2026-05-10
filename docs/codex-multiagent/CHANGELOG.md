# Changelog

## Unreleased

### Removed

- Removed bundled legacy workflow skills.
- Removed the legacy command-routing rule, porting note, and micro-seed example.
- Updated shell and PowerShell installers so they no longer copy bundled workflow skills and clean up the previously installer-managed workflow-skill kit folder.
- Removed remaining active seed/interview/evaluate terminology from current docs and installer-generated state templates.
- Removed committed `docs/codex-multiagent/` mirror copies from the repository surface; installers still create workspace-local supporting docs when requested.
- Removed obsolete `agentplan.txt`.
- Removed obsolete `examples/RULE_EVOLUTION_LOG.example.md`; task retrospectives remain the evidence trail for future rule proposals.

### Added

- `docs/CONCURRENT_STATE_MODE.md` 를 추가해 기본 single `STATE.md` 유지 원칙, concurrent-registry 전환 조건, root registry 필드, thread state 파일 구조를 문서화
- `docs/OPERATIONS_RETROSPECTIVE.md` 를 추가해 task retrospective 와 rule evolution log 를 어떤 기준으로 남길지 운영 규칙을 정리
- `examples/STATE.registry.example.md`, `examples/STATE.thread.example.md` 로 동시 작업용 root/thread 상태 파일 예시를 추가
- `examples/TASK_RETROSPECTIVE.example.md` 로 사후 회고 예시를 추가

- 각 핵심 기능 영역의 기대 동작을 `pass|partial|fail` 로 판정할 수 있도록 `docs/AREA_EVALUATION_METRICS.md` 에 공통 평가 축, 측정 항목, 최소 합격선, 기록 예시를 추가
- 프로젝트 목표 기준으로 저장소를 비교할 수 있도록 `docs/GOAL_COMPARISON_AREAS.md`에 핵심 기능 영역, 포함/제외 범위, 비교 질문, 최소 합격선을 추가
- `docs/GOAL_COMPARISON_AREAS.md` 각 기능 영역에 현재 구현과 직접 대조 가능한 `기대 동작`, `현재 구현에서 확인할 증거`, `비교 기록 포맷`을 추가
- `docs/GOAL_COMPARISON_AREAS.md` 와 `docs/AREA_EVALUATION_METRICS.md` 에 기능 영역, 기대 동작, 평가 기준, 최소 게이트, 갭 기록, 다음 액션을 한 레코드로 묶는 통합 갭 검토 워크시트 형식을 추가
- 루트 `Makefile` 에 `make lint`, `make test`, `make check` 검증 엔트리포인트 추가
- 저장소 방향성과 목표에서 직접 뽑은 상위 원칙, 공통 평가 축, 계획 단계 goal gate를 `docs/GOAL_ALIGNMENT_FRAMEWORK.md`에 추가
- 작은 기능 후보를 `value`, `implementation_cost`, `goal_alignment`, `ripple_effect` 기준으로 점수화하고 우선순위화하는 세부 루브릭을 `docs/GOAL_ALIGNMENT_FRAMEWORK.md`에 추가
- 리팩터링 후보를 `quality_impact`, `risk`, `maintainability`, `feature_goal_contribution` 기준으로 점수화하고 우선순위화하는 세부 루브릭을 `docs/GOAL_ALIGNMENT_FRAMEWORK.md`에 추가
- 기능 후보와 리팩터링 후보를 같은 후보 카드, `common_score`, `specific_score`, `priority_score`, `priority_grade` 규칙으로 비교·정렬하는 공통 점수화 체계를 `docs/GOAL_ALIGNMENT_FRAMEWORK.md`에 추가
- 도구 실행 결과에서 실패·경고·커버리지 신호를 공통 JSON 필드로 정규화하는 `scripts/normalize_quality_signals.py` 와 샘플 입력 `examples/quality_signal_samples.json` 추가
- 정규화된 분석 결과를 지정한 `--history` 경로에 append-only JSON 이력으로 누적 저장하고 `latest|summary|all` 로 조회하는 기능 추가

### Changed

- 루트에 있던 품질/리포트 Python 도구들을 `scripts/` 폴더로 이동하고 Makefile 및 문서의 실행 경로를 갱신
- PowerShell installer가 UTF-8 no BOM `WORKSPACE_CONTEXT.toml` 의 한글을 깨뜨리지 않도록 명시적 UTF-8 읽기/쓰기로 고정
- 설치 시 함께 복사되는 `docs/WORKSPACE_CONTEXT_GUIDE.md` 의 깨진 한글 문서를 정상 한국어 가이드로 교체
- `AGENTS.md` 와 `MULTI_AGENT_GUIDE.md` 에 same-workspace 동시 작업 충돌을 위한 optional concurrent registry mode, overlap 충돌 시 중단 규칙, retrospective/metrics 기록 규칙을 추가
- shell/PowerShell installer 생성 문구가 기본 single `STATE.md` 유지, concurrent registry 전환, retrospective/rule-evolution 기록 규칙까지 함께 출력하도록 동기화

- `docs/GOAL_COMPARISON_AREAS.md` 가 추상 비교 문서에 머물지 않도록 영역별 실제 판정 루브릭 문서 연결을 추가
- `docs/AREA_EVALUATION_METRICS.md` 와 `docs/GOAL_COMPARISON_AREAS.md` 가 후보 종류별 개별 루브릭뿐 아니라 동일 포맷 비교 규칙까지 확인하도록 보강
- 저장소 기본 검증 도구를 `markdownlint-cli2`, `shellcheck`, `pwsh + PSScriptAnalyzer` 기준으로 식별하고, 미설치 환경에서는 문법 검사와 스모크 테스트로 폴백하도록 정리
- `single-session` 판단이 최종 수정 파일 수에 과적합되지 않도록 `AGENTS.md`, `MULTI_AGENT_GUIDE.md`, installer 생성 문구를 보강
- 샘플 데모에서 실데이터 반영으로 계약이 바뀌는 순간 `execution_topology` 를 재평가하도록 규칙 명시
- 업스트림 수집, 정규화, read-heavy 조사도 독립 책임과 `write_sets` 후보로 보도록 문서화
- `STATE.md` 를 읽은 직후 현재 점수와 그 점수가 착수 방식에 미치는 영향까지 먼저 보고하도록 시작 규칙 추가
- Score 2.0-lite hard trigger, profile decision gate, explorer-first discovery, `contract_blocked` / `reclassify_required` 재오케스트레이션, profile별 verification gate 규칙 추가
- `make test` 가 샘플 기반 품질 신호 정규화 검사까지 포함하도록 확장
- `make test` 가 정규화 결과의 저장/조회 스모크 검사까지 포함하도록 확장
- macOS `Bootstrap.sh` 가 GNU `find` 깊이 옵션에 기대지 않도록 추출 루트 탐색을 shell loop로 교체하고, macOS CI에서 local bootstrap + `curl | bash` 둘 다 검증하도록 보강
- shell installer 가 BSD/GNU `paste -sd` 동작 차이에 기대지 않도록 join 로직을 shell helper로 교체하고 `pipefail` 을 켜서 macOS shell 경로를 더 엄격하게 검증
- shell/PowerShell installer 와 bootstrap 에 `update-global`, `update-workspace` 경로를 추가해서 기존 설치본을 최신 관리 규칙으로 재적용할 수 있게 함
- guide 에 profile 시작 decision tree 를 추가하고, macOS CI 에 update flow 재적용 검증을 추가

## v0.4.0 - 2026-04-09

### Changed

- 저장소 상위 오케스트레이션 모델을 legacy route model 에서 점수 기반 `orchestration profile` 흐름으로 전환
- `score_total`, `score_breakdown`, `hard_triggers`, `selected_rules`, `selected_skills`, `execution_topology`, `agent_budget`, `selection_reason` 추적 필드를 `STATE.md`와 installer 생성 템플릿에 반영
- 서브에이전트 위임을 사용자 명령 전제 대신 에이전트의 자동 delegation 판단으로 재정의하고 자연어 override 우선 규칙을 문서 전반에 통일
- 고정 role cap 문구를 제거하고 작업별 동적 `agent_budget` 기준으로 `worker`, `reviewer`, `explorer` 사용량을 설명하도록 갱신
- `AGENTS.md`, `README.md`, `MULTI_AGENT_GUIDE.md`, `profiles/*`, `examples/*`, legacy porting note, legacy command rule 을 새 용어 체계에 맞춰 정렬
- Windows PowerShell installer 실생성 검증으로 새 `AGENTS.md` / `STATE.md` 템플릿 출력이 `Orchestration Profile` 스키마를 따르는지 확인

## v0.3.0 - 2026-03-22

### Changed

- 저장소 라우트 모델을 legacy two-route model 로 정리
- main-only 경로를 no-subagent route 로 재정의
- delegated 경로가 old 대형 작업 의미를 흡수
- `AGENTS.md`, `README.md`, `MULTI_AGENT_GUIDE.md`, `profiles/*`, legacy workflow skills, legacy porting note, `examples/*` 를 당시 라우트 모델에 맞게 정렬

## v0.1.13 - 2026-03-22

### Added

- legacy interview/seed/run/evaluate workflow skills 추가
- append-only `ERROR_LOG.md` 템플릿 추가
- `WORKSPACE_CONTEXT_TEMPLATE.toml` 에 `error_log_path` 추가
- `codex_rules/ouroboros-lite.md` 추가
- `examples/micro-seed.md` 추가

### Changed

- `AGENTS.md` 에 spec-first workflow, error logging, subagent hygiene 규칙 추가
- installer 가 legacy workflow skills 를 전역 설치와 workspace docs 복사 흐름에 포함하도록 확장
- shell / PowerShell installer 가 workspace-relative `task_board_path` 와 `error_log_path` 만 허용하도록 보강
- shell installer 의 top-level iteration 을 deterministic 하게 정리하고 GNU `find -maxdepth` 의존 제거
- stale managed skill 정리를 manifest 기반으로 바꿔 same-prefix user skill 보존
- generated developer instructions 를 route-gated subagent hygiene 규칙과 append-only error logging 규칙에 맞게 정렬
- README 에 workflow, logging, hygiene 변경 사항 반영

## v0.1.12 - 2026-03-21

### Changed

- `WORKSPACE_CONTEXT.toml` 기반 workspace override 생성기를 확장해 풍부한 프로젝트 컨텍스트를 `AGENTS.md` 와 `STATE.md` 에 더 많이 반영하도록 개선
- explicit `workers.mapping`, `reviewer.focus`, `approval.zones`, `editing_rules` 값이 있으면 자동 파생보다 우선 적용하도록 installer 출력 규칙을 정리
- shell installer 를 macOS 기본 `bash 3.2` 환경에서도 동작하도록 `mapfile` 의존성과 빈 배열 `nounset` 오류를 제거
- 서브에이전트 설정에 `model_reasoning_effort` 를 명시해 `default`, `worker`, `explorer`, `reviewer` 역할별 추론 강도를 고정
- `README` 에 `WORKSPACE_CONTEXT.toml` 작성 기준표와 별도 가이드 링크를 추가
- `docs/WORKSPACE_CONTEXT_GUIDE.md` 문서를 추가해 필수 항목 기준표와 프로젝트별 생성 프롬프트를 정리

## v0.1.11 - 2026-03-21

### Changed

- 전역 규칙에 `route/reason` 선기록, legacy route 승격 규칙, delegated route 최소 worker/reviewer 요구를 추가
- 작업공간 오버라이드 설치 시 `WORKSPACE_CONTEXT.toml` 을 우선 읽어 맞춤형 `AGENTS.md` 와 초기 `STATE.md` 를 생성하도록 installer 를 확장
- `WORKSPACE_CONTEXT_TEMPLATE.toml` 예시 파일과 관련 문서를 추가
- macOS GitHub Actions 검증에 `WORKSPACE_CONTEXT.toml` 기반 workspace 생성 경로를 추가
- `.gitattributes` 를 추가해 `md`, `toml`, `yml`, `yaml`, `sh`, `ps1` 파일을 LF로 고정

## v0.1.10 - 2026-03-20

### Changed

- 전역/저장소 AGENTS 템플릿에 legacy route model 기반 작업 크기 게이트 추가
- `main` 직접 수정과 delegated planner-only 경로를 분리
- 작업공간 오버라이드 템플릿과 `STATE_TEMPLATE.md` 에 `route` 와 `write_sets` 개념 추가
- 운영 가이드와 예시를 route 기반 멀티에이전트 모델로 갱신

## v0.1.3 - 2026-03-19

### Added

- Codex macOS/Linux용 shell 설치기 `installer/CodexMultiAgent.sh` 추가
- `curl | bash` 원클릭 설치용 `installer/Bootstrap.sh` 추가
- GitHub Actions `macos-latest` 기반 설치 검증 워크플로우 추가

### Changed

- macOS 설치 경로를 README 중심으로 정리하고 GitHub Actions `macos-latest` 기준 전역 설치, workspace 오버라이드, bootstrap 경로 실검증 완료

## v0.1.2 - 2026-03-19

### Added

- Codex macOS/Linux용 shell 설치기 `installer/CodexMultiAgent.sh` 추가
- `curl | bash` 원클릭 설치용 `installer/Bootstrap.sh` 추가
- GitHub Actions `macos-latest` 기반 설치 검증 워크플로우 추가

### Changed

- macOS 설치 경로를 README 중심으로 정리하고 GitHub Actions `macos-latest` 기준 전역 설치, workspace 오버라이드, bootstrap 경로 실검증 완료

## v0.1.1 - 2026-03-18

### Added

- Codex 전역 설치 시 `%USERPROFILE%\.codex\rules\default.rules` 기본 command rules도 함께 배포
- `git reset --hard`, `git checkout --`, `git restore`, `git clean`, `rm -rf`, `del /s /q`, `Remove-Item -Recurse -Force` 같은 파괴적 명령 기본 차단 규칙 추가

### Changed

- 에이전트가 스스로 디스크 삭제나 강제 되돌리기 명령을 치는 기본 흐름을 rules 레이어에서 차단

## v0.1.0 - 2026-03-18

### Added

- Codex 전역 설치 시 `%USERPROFILE%\.codex\agents\*.toml` 서브에이전트 오버라이드도 함께 배포
- Codex built-in 서브에이전트 `default`, `worker`, `explorer`, `reviewer` 용 `gpt-5.4-mini` 모델 패치 템플릿 추가
- 전역 Codex 워크스페이스 기본 페르소나로 `gogi` 적용
- 이 워크스페이스 기본 페르소나로 `gogi` 적용

### Changed

- Codex 메인 세션 모델은 기존 사용자 `config.toml` 설정을 그대로 유지
- 서브에이전트만 더 가벼운 `gpt-5.4-mini` 모델을 사용하도록 분리
- 저장소/전역 AGENTS 문서의 기본 응답 언어를 한국어 중심으로 정리
- 저장소/전역 AGENTS 문서의 기본 톤을 간결한 banmal 기반 시니어 엔지니어 톤으로 정리
