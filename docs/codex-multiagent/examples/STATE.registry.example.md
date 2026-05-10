# Root Registry Example

```md
# Current Task

- Root registry for same-workspace concurrent execution.

# Orchestration Profile

- state_mode: `concurrent-registry`
- registry_note: `Optional mode only; root STATE.md coordinates live thread ownership while thread state files hold execution details.`
- score_total: `n/a - tracked in thread state files`
- score_breakdown: `n/a - tracked in thread state files`
- hard_triggers: `n/a - tracked in thread state files`
- selected_rules:
  - `state_integrity_required`
- selected_skills:
  - `none`
- execution_topology: `concurrent-registry`
- orchestration_value: `medium`
- agent_budget: `n/a - tracked per thread`
- selection_reason: `Same-workspace concurrent threads were explicitly chosen and ownership can stay disjoint.`

# Evaluation Plan

- evaluation_need: `light`
- project_invariants:
  - `Root registry tracks ownership only; per-thread files own task execution detail.`
  - `Concurrent mode stays optional.`
- task_acceptance:
  - `No two active threads own overlapping write sets.`
- non_goals:
  - `No background scheduler or queue.`
- hard_checks:
  - `manual registry ownership review`
- llm_review_rubric:
  - `Check that registry notes do not look like a new runtime.`
- evidence_required:
  - `active_threads and workspace_locks are current`

- shared_contracts:
  - `installer rule text must stay aligned across shell and PowerShell generators`
- active_threads:
  - `thread-1042`: `states/STATE.thread-1042.md`
  - `thread-1043`: `states/STATE.thread-1043.md`
- workspace_locks:
  - `AGENTS.md`: `thread-1042`
  - `docs/CONCURRENT_STATE_MODE.md`: `thread-1042`
  - `examples/TASK_RETROSPECTIVE.example.md`: `thread-1043`

# Writer Slot

- writer_slot: `registry-only`
- owned_files:
  - `STATE.md`
- write_set: `registry coordination only`
- write_sets:
  - `registry`:
    - `STATE.md`
  - `thread-1042`: `states/STATE.thread-1042.md owns its execution write set`
  - `thread-1043`: `states/STATE.thread-1043.md owns its execution write set`
- shared_assets_owner: `none`

# Contract Freeze

- contract_freeze: `root registry only`
- handoff_queue:
  - `none`
- escalation_note: `If a thread needs a locked file, stop and reclassify before writes.`

# Reviewer

- reviewer: `none`
- reviewer_target: `none`
- reviewer_focus: `n/a`

# Last Update

- phase: coordination
- last_registry_update: `2026-04-13T16:20:00+09:00`
```
