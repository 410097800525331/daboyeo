# WORKSPACE_CONTEXT Guide

`WORKSPACE_CONTEXT.toml`은 설치 전에 프로젝트 방향성을 정리해 두는 선택 입력 파일이다.
installer는 이 파일을 읽어서 프로젝트에 맞는 `AGENTS.md`와 초기 `STATE.md`를 생성한다.

## 핵심 원칙

- 이 파일은 규칙 생성용 힌트다.
- 전역 dynamic multi-agent policy를 저장소 상황에 맞게 좁히는 입력이며, fixed caps나 old single-writer lore를 되살리는 입력이 아니다.
- 없는 파일을 억지로 만들 필요는 없다.
- 경로를 넣으면 생성된 `AGENTS.md`가 그 경로를 작업 전 참고 후보로 보여줄 수 있다.
- 더 이상 쓰지 않는 경로, 예전 harness, 임시 문서 이름은 반드시 지워라.
- Context values may be UTF-8, but generated policy text should stay English/ASCII-first unless explicit workspace content requires otherwise.
- 생성된 project-local `AGENTS.md` / `STATE.md`가 해당 workspace의 non-trivial task loop를 소유한다.

## 최소 권장 항목

| 항목 | 설명 | 예시 |
| --- | --- | --- |
| `workspace.name` | 작업공간 이름 | `billing-api`, `jejugroup` |
| `workspace.summary` | 프로젝트 목적 한 줄 | `결제 API 서버`, `배치 리포트 생성 파이프라인` |
| `workspace.task_board_path` | 상태 파일 경로 | `STATE.md` |
| `workspace.error_log_path` | 오류 로그 경로 | `ERROR_LOG.md` |
| `repository.facts` | 저장소 사실 | `Package manager: pnpm` |
| `required_context.read` | 작업 전 읽을 문서 | `docs/architecture.md` |
| `verification.commands` | 검증 명령 | `pnpm test`, `pytest` |

## 선택 항목

| 항목 | 설명 | 예시 |
| --- | --- | --- |
| `contracts.shared` | 유지해야 할 계약 | `Public API routes stay stable` |
| `paths.shared_assets` | 공유 자산 경로 | `src/shared/**` |
| `paths.do_not_touch` | 직접 수정 금지 경로 | `dist/**`, `generated/**` |
| `triggers.hard` | 재분류가 필요한 변경 | `API contract change` |
| `approval.zones` | 사용자 확인이 필요한 영역 | `deploy`, `db migration` |
| `workers.mapping` | worker write-set 힌트 | `worker_api = src/api/**` |
| `reviewer.focus` | 리뷰 우선순위 | `contract drift`, `verification gaps` |
| `forbidden.patterns` | 저장소에서 금지할 위임/수정 패턴 | `Direct edits to generated output` |
| `persona_override.*` | 대화 표면용 persona field override | `response_language = "English"` |

## Local Delegation Narrowing

Workspace context는 global model을 더 엄격하게 좁히는 입력이지, 더 느슨하게 푸는 입력이 아니다.
여기에는 local verification commands, shared contracts, shared asset paths, hard trigger additions, approval zones, write-set ownership hints, reviewer focus, forbidden patterns를 적는다.

`writer 1`이나 `explorer 3` 같은 fixed per-role caps를 canonical model처럼 적지 마라.
생성된 workspace rules는 계속 `score_total`, hard triggers, `execution_topology`, ownership clarity, independent verification, handoff cost, task-scoped `agent_budget`로 support 여부를 판단한다.

더 엄격한 local delegation hints가 필요하면 다음 위치에 넣어라.

- reclassification trigger: `triggers.hard`
- write ownership hint: `workers.mapping`
- shared-owner path: `paths.shared_assets`
- reviewer priority: `reviewer.focus`
- banned local pattern: `forbidden.patterns`

## Baseline Spec And Evaluation Inputs

`WORKSPACE_CONTEXT.toml` carries project baseline constraints, not the full task spec.
Do not paste README-scale prose into `STATE.md`; derive the baseline from structured context and add the task overlay in the current `STATE.md`.

- `workspace.summary`, `contracts.shared`, `paths.do_not_touch`, and `forbidden.patterns` become `project_invariants`
- `verification.commands` becomes the default `hard_checks`
- `reviewer.focus` becomes `llm_review_rubric` hints
- `workers.mapping` and `paths.shared_assets` help estimate `orchestration_value`
- current user request and pinned plan become `task_acceptance`, `non_goals`, and `evidence_required`

Hard checks are the source of truth when they exist.
LLM review is a soft second pass for ambiguity, subtle behavior, wording, UX, or contract judgment.
High `score_total` alone must not upgrade `evaluation_need` or `orchestration_value`, and file count alone upgrades neither axis.

## Persona Override Example

The global default persona is `gogi`. Default response language remains Korean unless the user asks otherwise, default speech style remains concise Korean banmal, and default tone remains dry, confident senior engineer.

Add `[persona_override]` only when the workspace needs to narrow conversational persona fields. Missing keys inherit the global default field-by-field.
Generated artifacts follow repository and audience conventions before persona defaults, and generated policy text should stay English/ASCII-first unless explicit workspace content requires otherwise.

```toml
[persona_override]
name = "gogi"
response_language = "English"
speech_style = "concise plain English"
tone = "calm senior engineer"
allow_mild_profanity = false
code_comment_language = "repository convention first, English if no convention exists"
```

## 기존 프로젝트에 적용할 때

기존 프로젝트라면 실제로 존재하는 문서와 경로만 넣어라.
예전에 쓰던 `PROJECT_HARNESS.md` 같은 파일명이 남아 있으면 생성된 `AGENTS.md`가 그 파일을 찾아야 하는 것처럼 보일 수 있다.

추천 요청 문장:

```text
이 프로젝트 폴더를 읽고 WORKSPACE_CONTEXT.toml 초안을 만들어줘.

반드시 채워줘:
- workspace.name
- workspace.summary
- workspace.task_board_path
- workspace.error_log_path
- repository.facts
- required_context.read
- verification.commands

없는 파일은 required_context.read에 넣지 마.
예전 harness나 임시 문서명은 넣지 마.
```

## 새 프로젝트에 적용할 때

새 프로젝트라면 실제 파일이 아직 없을 수 있으니 `required_context.read`는 비워 두거나 확정된 문서만 넣어라.

추천 요청 문장:

```text
내가 만들려는 프로젝트 방향을 바탕으로 WORKSPACE_CONTEXT.toml 초안을 만들어줘.

아직 없는 파일은 required_context.read에 넣지 말고,
검증 명령은 나중에 확정할 수 있게 후보로만 적어줘.
```

## 확인 체크리스트

- `WORKSPACE_CONTEXT.toml`이 UTF-8로 저장되어 있는가
- `required_context.read`의 파일들이 실제로 존재하는가
- 오래된 harness, 임시 문서, 다른 프로젝트 경로가 남아 있지 않은가
- 검증 명령이 현재 저장소에서 실행 가능한가
- `paths.do_not_touch`가 너무 넓어서 실제 작업을 막지 않는가
