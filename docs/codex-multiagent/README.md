# Codex Multi-Agent Kit

한국어 | [English](./README.en.md)

Codex Multi-Agent Kit은 Codex의 기본 subagent 기능 위에 전역 규칙,
작업공간별 오버라이드, 상태 기록, 검증 루틴을 얹는 운영 키트다.

목표는 단순하다.

- 모든 작업공간에 같은 기본 안전 규칙을 깐다.
- 프로젝트마다 다른 규칙은 로컬 `AGENTS.md`로 분리한다.
- 비 trivial 작업은 `STATE.md`에 분류, 계약, write set, 검증 기준을 남긴다.
- subagent는 쓸 수 있어서가 아니라 분리할 가치가 있을 때만 쓴다.

빠른 시작 · 설치 모드 · 작업 흐름 · 파일 맵 · 검증

---

## 빠른 시작

### Windows PowerShell

전역 기본 규칙을 설치한다.

```powershell
Invoke-RestMethod 'https://raw.githubusercontent.com/r2gul4r/codex_multiagent/main/installer/Bootstrap.ps1' | Invoke-Expression
Install-CodexMultiAgent -Mode InstallGlobal
```

특정 작업공간에 프로젝트 규칙을 적용한다.

```powershell
$workspace = 'C:\path\to\your\workspace'
Invoke-RestMethod 'https://raw.githubusercontent.com/r2gul4r/codex_multiagent/main/installer/Bootstrap.ps1' | Invoke-Expression
Install-CodexMultiAgent -Mode ApplyWorkspace -TargetWorkspace $workspace -IncludeDocs
```

### macOS / Linux / WSL

전역 기본 규칙을 설치한다.

```bash
curl -fsSL https://raw.githubusercontent.com/r2gul4r/codex_multiagent/main/installer/Bootstrap.sh \
  | bash -s -- install-global
```

특정 작업공간에 프로젝트 규칙을 적용한다.

```bash
workspace="/path/to/your/workspace"
curl -fsSL https://raw.githubusercontent.com/r2gul4r/codex_multiagent/main/installer/Bootstrap.sh \
  | bash -s -- apply-workspace --workspace "$workspace" --include-docs
```

---

## 한눈에 보기

| 영역 | 이 키트가 하는 일 |
| :-- | :-- |
| 전역 규칙 | `~/.codex/AGENTS.md`, `config.toml`, agent 설정, command rules 설치 |
| 작업공간 규칙 | 프로젝트 루트에 `AGENTS.md`, `STATE.md`, `ERROR_LOG.md` 생성 |
| 작업 분류 | `score_total`, hard trigger, write set, 검증 기준 기록 |
| 실행 방식 | 기본은 `single-session`, 필요할 때만 `delegated-*`로 전환 |
| 안전장치 | destructive command 차단, 사용자 변경 보존, 보안 규칙 유지 |
| 검증 | `make check`로 markdown, shell, PowerShell, generated docs 검사 |

이 키트는 Codex의 agent 기능을 대체하지 않는다.
그 기능을 실제 저장소 작업에 맞게 안전하게 굴리기 위한 운영 규칙을 제공한다.

---

## 설치 모드

| 모드 | 대상 | 결과 |
| :-- | :-- | :-- |
| `InstallGlobal` / `install-global` | Codex 홈 | 전역 `AGENTS.md`, `config.toml`, agents, rules 설치 |
| `ApplyWorkspace` / `apply-workspace` | 프로젝트 루트 | 프로젝트용 `AGENTS.md`, `STATE.md`, `ERROR_LOG.md` 생성 |
| `UpdateGlobal` / `update-global` | Codex 홈 | 전역 설치를 최신 키트 기준으로 갱신 |
| `UpdateWorkspace` / `update-workspace` | 프로젝트 루트 | 작업공간 오버라이드를 최신 템플릿 기준으로 갱신 |

설치기는 기존 파일을 바로 덮지 않고 백업을 만든다.

| 위치 | 백업 경로 |
| :-- | :-- |
| 전역 Codex 홈 | `~/.codex/backups/<timestamp>/global` |
| 작업공간 | `<workspace>/.codex-backups/<timestamp>/workspace` |

---

## 작업공간 적용 흐름

| 단계 | 할 일 | 산출물 |
| :-- | :-- | :-- |
| 1 | 전역 설치 | Codex 홈의 기본 운영 규칙 |
| 2 | `WORKSPACE_CONTEXT.toml` 작성 | 프로젝트별 명령, 금지 경로, 검증 기준 |
| 3 | 작업공간 적용 | 프로젝트 루트의 `AGENTS.md`, `STATE.md` |
| 4 | 작업 시작 | `STATE.md`에 현재 작업, profile, write set 기록 |
| 5 | 검증 | repo 검증 명령과 코드 리뷰 결과 |

`WORKSPACE_CONTEXT.toml`이 있으면 설치기는 그 내용을 먼저 읽고 프로젝트에 맞춘
`AGENTS.md`와 초기 `STATE.md`를 만든다. 없으면 내장 fallback 규칙으로 생성한다.

참고:

- [WORKSPACE_CONTEXT_TEMPLATE.toml](./WORKSPACE_CONTEXT_TEMPLATE.toml)
- [WORKSPACE_CONTEXT_GUIDE.md](./docs/WORKSPACE_CONTEXT_GUIDE.md)

---

## 템플릿

| 템플릿 | 용도 |
| :-- | :-- |
| `standard` | 일반 프로젝트. write set, 검증, worker mapping까지 충분히 기록 |
| `minimal` | 작은 저장소. 꼭 필요한 규칙만 남긴 축약형 |

PowerShell 예시:

```powershell
Install-CodexMultiAgent -Mode ApplyWorkspace `
  -TargetWorkspace 'C:\path\to\workspace' `
  -Template minimal
```

Bash 예시:

```bash
bash installer/CodexMultiAgent.sh apply-workspace \
  --workspace "/path/to/workspace" \
  --template minimal
```

---

## 작업 운영 모델

모든 non-trivial 작업은 아래 흐름을 따른다.

```text
plan -> classify -> freeze -> implement -> verify -> retrospective
```

| 단계 | 의미 |
| :-- | :-- |
| `plan` | 요청을 현재 `STATE.md`의 active task와 비교 |
| `classify` | 점수, hard trigger, profile, agent budget 결정 |
| `freeze` | acceptance, non-goals, write sets, 검증 기준 고정 |
| `implement` | 고정된 write set 안에서만 수정 |
| `verify` | repo 명령, 코드 리뷰, generated docs drift 확인 |
| `retrospective` | 필요한 경우 실제 결과와 다음 rule 조정 기록 |

작업이 작으면 이 흐름을 짧게 쓴다. 생략하는 게 아니라 압축하는 쪽이다.

---

## 분류 기준

`score_total`은 복잡도와 리스크의 사전 신호일 뿐이다.
평가 깊이와 delegation 가치는 따로 본다.

| 축 | 값 | 결정 기준 |
| :-- | :-- | :-- |
| `score_total` | `0+` | 파일 수, 모듈 수, 계약 변경, 검증 폭, 보안 영향 |
| `evaluation_need` | `none`, `light`, `full` | acceptance 애매함, 회귀 위험, 정성 리뷰 필요성 |
| `orchestration_value` | `low`, `medium`, `high` | write set 분리 가능성, handoff 비용, 독립 검증 가능성 |
| `agent_budget` | `0+` | 위 세 축과 hard trigger를 보고 산정 |

| 점수 | 기본 판단 |
| :-- | :-- |
| `0-3` | 보통 `single-session` |
| `4-6` | 평가 계획이나 spawn/no-spawn 근거를 가볍게 기록 |
| `7+` | explicit `spawn_decision`과 평가 계획 필요 |

---

## 실행 profile

| Profile | 사용 조건 |
| :-- | :-- |
| `single-session` | 기본값. `main` 하나만 write 가능 |
| `delegated-serial` | 의존성이 있어 순차 handoff가 더 안전한 경우 |
| `delegated-parallel` | 계약 고정, disjoint write sets, 독립 검증이 모두 가능한 경우 |
| `mixed` | 순차 단계와 병렬 단계를 함께 써야 하는 경우 |

`delegated-parallel`은 조건이 빡빡하다.

- contract freeze 완료
- write sets가 겹치지 않음
- shared asset owner가 한 명
- main은 병렬 단계에서 직접 write 하지 않음
- slice별 검증이 가능함
- `agent_budget > 0`

---

## 기록 파일

| 파일 | 역할 | Git 추적 |
| :-- | :-- | :-- |
| `AGENTS.md` | 저장소 또는 전역의 실행 규칙 | 추적 |
| `STATE.md` | 현재 작업의 profile, 계약, write set | 보통 ignored |
| `MULTI_AGENT_LOG.md` | 실제 agent 참여와 handoff 기록 | 보통 ignored |
| `ERROR_LOG.md` | 실행, 설치, 검증 오류 기록 | 보통 ignored |
| `WORKSPACE_CONTEXT.toml` | 프로젝트별 설치 입력 | 프로젝트 선택 |

`STATE.md`는 비 trivial 구현 작업에서 필수다.
작업 목표가 바뀌면 이전 profile을 그대로 끌고 가지 않고 다시 분류한다.

---

## 안전 규칙

| 범주 | 규칙 |
| :-- | :-- |
| 사용자 변경 | dirty worktree의 사용자 변경을 임의로 되돌리지 않음 |
| destructive command | `git reset --hard`, `git clean -fd`, `rm -rf` 등 기본 차단 |
| 보안 | auth, secrets, user input, API, DB, HTML 렌더링은 별도 보안 규칙 적용 |
| 오류 | 작업에 영향을 준 실행/검증 오류는 `ERROR_LOG.md`에 append-only 기록 |

금지 명령은 사용자가 명시적으로 요청한 경우에만 예외가 된다.

---

## 포함 파일

| 경로 | 설명 |
| :-- | :-- |
| [AGENTS.md](./AGENTS.md) | canonical global multi-agent 규칙 |
| [Makefile](./Makefile) | lint, smoke test, generated docs 검증 entrypoint |
| [installer/CodexMultiAgent.ps1](./installer/CodexMultiAgent.ps1) | Windows PowerShell 설치기 |
| [installer/CodexMultiAgent.sh](./installer/CodexMultiAgent.sh) | macOS/Linux/WSL 설치기 |
| [installer/Bootstrap.ps1](./installer/Bootstrap.ps1) | 원격 PowerShell bootstrap |
| [installer/Bootstrap.sh](./installer/Bootstrap.sh) | 원격 shell bootstrap |
| [codex_agents/](./codex_agents) | Codex agent role 설정 |
| [codex_rules/](./codex_rules) | command safety rules |
| [profiles/](./profiles) | main, explorer, worker, reviewer role contract |
| [scripts/](./scripts) | repo metrics, gap extraction, quality normalization 도구 |
| [docs/](./docs) | 운영 가이드, patch notes, generated analysis docs |

---

## 검증

개발 중 기본 검증은 `make check`다.

```bash
make check
```

| Target | 내용 |
| :-- | :-- |
| `make lint` | markdownlint, bash syntax, shellcheck, PowerShell parse |
| `make test` | installer smoke, quality normalizer, repo metrics, generated docs |
| `make check` | `lint + test` |

권장 도구:

| 도구 | 없을 때 |
| :-- | :-- |
| `markdownlint-cli2` 또는 `markdownlint` | Markdown lint skip |
| `shellcheck` | ShellCheck skip |
| `pwsh` 또는 `powershell` | PowerShell parse skip |
| `PSScriptAnalyzer` | PowerShell 정적 분석만 skip |

generated docs는 현재 repository scan 출력과 committed markdown이 같아야 통과한다.
README나 installer를 바꾸면 아래 문서가 함께 drift 날 수 있다.

- [docs/FEATURE_GAP_AREAS.md](./docs/FEATURE_GAP_AREAS.md)
- [docs/TEST_GAP_AREAS.md](./docs/TEST_GAP_AREAS.md)
- [docs/REFACTOR_CANDIDATES.md](./docs/REFACTOR_CANDIDATES.md)

---

## 이 키트가 맞는 경우

| 상황 | 이유 |
| :-- | :-- |
| 여러 저장소에서 같은 Codex 운영 규칙을 쓰고 싶다 | 전역 기본값과 workspace override를 분리 |
| delegation이 자주 흐트러진다 | `STATE.md`, write sets, contract freeze로 경계 고정 |
| 작업마다 검증 기준이 달라진다 | workspace context와 task acceptance를 함께 기록 |
| destructive command 실수를 막고 싶다 | command rules와 forbidden command 정책 포함 |
| 설치/검증 오류를 나중에 추적하고 싶다 | `ERROR_LOG.md` append-only 기록 |

## 과한 경우

| 상황 | 더 단순한 대안 |
| :-- | :-- |
| 한 파일짜리 작은 실험 | 일반 Codex 설정만으로 충분 |
| delegation을 거의 쓰지 않는다 | `minimal` 템플릿 권장 |
| 작업 기록이 필요 없다 | 이 키트의 장점이 크게 줄어듦 |

---

## 최근 변경

- [2026-04-15 patch notes](./docs/PATCH_NOTES_2026-04-15.md)
- [2026-04-14 patch notes](./docs/PATCH_NOTES_2026-04-14.md)
- [2026-04-13 patch notes](./docs/PATCH_NOTES_2026-04-13.md)
- [CHANGELOG.md](./CHANGELOG.md)

---

## 라이선스

이 저장소의 라이선스와 배포 정책은 repository metadata를 따른다.
