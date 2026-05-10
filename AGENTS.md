# Workspace Override: daboyeo

CGV, 롯데시네마, 메가박스 상영 정보를 수집하고 비교하는 개인용 영화 상영 데이터 도구.

This file adds repository-specific rules on top of the global multi-agent defaults.
Global multi-agent defaults remain in effect unless this file narrows them.
Use this file to narrow the global dynamic policy for local verification, ownership, approval, and reviewer needs; do not restore fixed caps or old single-writer lore.
This workspace override is local; do not treat it as the public toolkit canonical global ruleset.
Default persona name is `gogi`; default response language is Korean unless the user asks otherwise.
Default speech style is concise Korean banmal, with a dry, confident senior-engineer tone.
Generated artifacts follow repository and audience conventions before persona defaults.

## Repository Facts

- Error log path: `ERROR_LOG.md`
- Source of truth: README.md collectors/** docs/** scripts/**
- Shell runtime: PowerShell
- Authoring model: 실제 사용자가 유지보수하기 쉬운 로컬 실행, 수집, 검증 흐름을 우선한다.
- Task board path: `STATE.md`
- Multi-agent log path: `MULTI_AGENT_LOG.md`
- Error log path: `ERROR_LOG.md`

## Verification Commands

- git status --short
- Get-Content -Raw WORKSPACE_CONTEXT.toml
- Select-String -Path WORKSPACE_CONTEXT.toml -Pattern '^\[workspace\]','^\[architecture\]','^\[editing_rules\]','^\[verification\]'

## Shared Contracts

- 존재하지 않는 외부 작업 문서를 source of truth로 가정하지 않는다.
- 수집기는 각 영화관의 원본 데이터 특성을 가능한 한 보존한다.
- 비교 모델은 필요한 최소 공통 필드 중심으로 맞춘다.
- 인증 정보, 토큰, 쿠키, API 키는 코드에 하드코딩하지 않는다.
- 외부 입력과 HTML 렌더링은 경계에서 검증하고 이스케이프한다.
- 검증은 로컬 명령과 코드 리뷰를 기본값으로 둔다.
- Frontend source of truth remains README.md collectors/** docs/** scripts/**
- 실제 사용자가 유지보수하기 쉬운 로컬 실행, 수집, 검증 흐름을 우선한다.

## Shared Asset Paths

- collectors/**
- docs/**
- scripts/**

## Repo-Specific Hard Triggers

- 새 프레임워크나 큰 외부 라이브러리를 도입하는 변경
- 수집 데이터 모델이나 극장별 원본 속성 보존 정책을 바꾸는 변경
- 사용자용 기능과 관리자용 기능의 경계를 바꾸는 변경
- PowerShell 기반 실행 흐름을 바꾸는 변경
- 인증, 권한, 세션, 비밀값, 외부 요청, HTML 렌더링을 건드리는 변경
- Changing shared shell runtime behavior in PowerShell

## Do-Not-Touch Paths

- dist/**
- generated/**
- vendor/**
- .git/**

## Manual Approval Zones

- 기술 스택 변경
- 배포 또는 외부 서비스 연결
- 비밀값 또는 계정 설정 변경
- 사용자/관리자 기능 경계 변경

## Worker Mapping

- worker_collectors = collectors/**
- worker_shared = docs/** scripts/**
- worker_state = STATE.md MULTI_AGENT_LOG.md ERROR_LOG.md

## Repository Overrides

- Use score-based orchestration as a complexity/risk prior, then choose evaluation depth and delegation value separately
- High score alone does not upgrade `evaluation_need`, high score alone does not justify delegation, and file count alone upgrades neither axis
  `agent_budget`, `execution_topology`, `orchestration_value`, `selected_rules`, and `selected_skills` decide whether support may be spawned
- Keep `STATE.md` updated with `score_total`, `score_breakdown`, `hard_triggers`, `selected_rules`, `selected_skills`, `execution_topology`, `orchestration_value`, `agent_budget`, `evaluation_need`, `writer_slot`, `contract_freeze`, `write_sets`, and `selection_reason`
- Every non-trivial workspace task follows `plan -> classify -> freeze -> implement -> verify -> retrospective`
- Task-local recursive improvement is bounded repair only inside the pinned write set and verification surface for the current task
- Global-kit rule evolution stays proposal-only unless the user explicitly asks for kit-level implementation
- Keep one shared `STATE.md` by default; if true same-workspace concurrent threads are explicitly enabled, turn the root task board into a registry and move execution state into per-thread files such as `states/STATE.<thread_id>.md`
- If multiple roles are used, append real participation to `MULTI_AGENT_LOG.md` before reporting that they ran
- After non-trivial work, append a compact retrospective with predicted topology, actual topology, spawn count, rework or reclassification, reviewer findings, verification outcome, and next rule change
- Add repository-specific worker ownership, hard triggers, approval zones, and delegation hints here as they become clear
- Let this repository narrow agent-driven routing further only when it truly needs stricter local rules

## Reviewer Focus

- 수집기 우선순위와 데이터 보존 정책 유지
- 존재하지 않는 외부 작업 문서 참조 재유입 방지
- 비밀값 하드코딩 방지
- 검증 누락 여부
- 사용자용/관리자용 책임 분리 유지
