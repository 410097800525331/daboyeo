# Minimal AGENTS Example

This is the fast-start version for small repositories or personal projects.

## Core Rules

- Default to `single-session`
- Do not use multiple agents for simple investigation or short edits unless the selected rules justify it
- Check hard triggers before score, then select delegation only when ownership, verification, and handoff cost justify it
- Let `main` choose `selected_rules`, `selected_skills`, `execution_topology`, `orchestration_value`, and `evaluation_need`
- Before closing delegated work, let `reviewer` do one read-only pass when `review_required` is selected
- Keep a small `STATE.md` with `score_total`, `hard_triggers`, `selected_rules`, `selected_skills`, `execution_topology`, `orchestration_value`, `agent_budget`, `evaluation_need`, `writer_slot`, `contract_freeze`, `write_sets`, and `selection_reason`
- Every non-trivial workspace task follows `plan -> classify -> freeze -> implement -> verify -> retrospective`
- Task-local recursive improvement is bounded repair only inside the current task's pinned write set and verification surface
- High score and file count do not upgrade evaluator strength or delegation by themselves
- Hard checks outrank LLM review; use `llm_review_rubric` as a soft second pass only when judgment is needed

## Execution Profiles

- `single-session`: default local path with one write-capable lane
- `delegated-serial`: one slice at a time when handoff lowers risk
- `delegated-parallel`: only after frozen contracts, disjoint write sets, one shared owner, independent verification, main read-only, and `agent_budget > 0`
- `mixed`: serial contract-freeze first, then safe fan-out only if the parallel gate passes

## Roles

- `main`
  Pins the goal, selects the orchestration profile, integrates the result, makes the final call
- `worker`
  Makes the actual changes
- `reviewer`
  Performs the final read-only review

## Parallelization

- Default to no parallelization
- For `4-6` point work, record a lightweight spawn/no-spawn basis only when the choice is non-obvious
- For `7+` point work, record `spawn_decision`; stay local when a concrete blocker makes delegation more expensive or less safe
- If the shared contract starts drifting, collapse back to `main`
- Fixed per-role caps are not the model; task-scoped `agent_budget` decides whether support can be spawned
- `evaluation_need` and `orchestration_value` are separate gates; keep one local writer when handoff value is low even if evaluation needs a stronger checklist

## Persona Override

- Inherit the global default persona `gogi` unless the user request or workspace override changes a field
- Default response language remains Korean unless the user asks otherwise
- Default speech style remains concise Korean banmal, with a dry, confident senior-engineer tone
- Example local override: `response_language = "English"`, `tone = "calm senior engineer"`, `allow_mild_profanity = false`
- Missing fields inherit the global default; generated artifacts follow repository convention first

## Done Means

- The goal fits in one line
- The edit scope is small and clear
- Required verification ran or the reason for skipping is recorded
- Reviewer can close with no critical risk
