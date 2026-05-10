# AGENTS Example

This example shows how the template can look in a larger web or service repository.

Adjust the paths and commands to match the real repository.

## Operating Goal

- Use a score-based orchestration profile instead of a fixed route split
- Let `main` select `selected_rules`, `selected_skills`, `execution_topology`, `orchestration_value`, `evaluation_need`, and `agent_budget`
- Treat `score_total` as a complexity/risk prior only; it does not choose evaluator strength or delegation by itself
- Pin API, schema, and ownership contracts before workers start
- Keep `writer_slot`, `contract_freeze`, and `write_sets` as the shared tracking primitives
- Use delegation only when the current user or workspace instructions authorize it and the efficiency gate passes

## Roles

- `main`
  Orchestration, contract pinning, and final integration
- `explorer`
  Read-only scouting when `exploration_required` is selected
- `worker_shared`
  Shared types, common utilities, and other shared assets
- `ui_worker`
  `apps/web`, `packages/ui`
- `backend_worker`
  `apps/api`, `packages/server`
- `data_worker`
  `packages/schema`, migrations, provider contracts
- `reviewer`
  Final read-only review when `review_required` is selected

## Repository-Specific Rules

- `explorer` and `reviewer` are read-only
- `main` selects the orchestration profile, not a fixed route
- `single-session` is the default profile for one local write lane
- `delegated-serial` is for dependent slices where handoff lowers risk
- `delegated-parallel` is allowed only after the full parallel gate passes
- `mixed` is for tasks that need a serial contract-freeze phase before safe fan-out
- Workers write only inside their assigned `write_set`
- Keep `STATE.md` updated with `score_total`, `score_breakdown`, `hard_triggers`, `selected_rules`, `selected_skills`, `execution_topology`, `orchestration_value`, `agent_budget`, `evaluation_need`, `writer_slot`, `contract_freeze`, and `write_sets`
- Keep hard checks ahead of LLM review; use `llm_review_rubric` only as a soft second pass
- Every non-trivial workspace task follows `plan -> classify -> freeze -> implement -> verify -> retrospective`
- Task-local recursive improvement is bounded repair only inside the current task's pinned write set and verification surface
- Global-kit rule evolution stays proposal-only unless the user explicitly asks for kit-level implementation
- Review/design mode is read-only until `main` pins the patch scope and explicitly enters implementation
- If both `apps/web` and `apps/api` are touched, request payload contracts must be pinned first
- If a migration file is involved, do not parallelize unless the selected rules and write sets prove the split is safe
- Do not edit `generated/` or `dist/` directly
- Treat explicit natural-language overrides from the user as higher priority than automatic selection

## Local Persona Override Example

- The global kit default persona remains `gogi`
- Default response language remains Korean unless the user asks otherwise
- Default speech style remains concise Korean banmal, with a dry, confident senior-engineer tone
- Local persona overrides narrow only the conversational surface; unspecified fields inherit the global defaults
- Example override: `response_language = "English"`, `speech_style = "concise plain English"`, `tone = "calm senior engineer"`, `allow_mild_profanity = false`, `code_comment_language = "repository convention first"`
- Generated artifacts follow repository and audience conventions before persona defaults
- Policy examples and installer-generated rule text should stay English/ASCII-first unless explicit workspace content requires otherwise

## Verification Commands

- `pnpm lint`
- `pnpm test`
- `pnpm build`

## Parallel Work That Is Safe

- Read-only `explorer` slices can narrow scope when the selected rules and task-scoped `agent_budget` allow exploration
- A designated `worker_shared` owns shared types and common utilities when shared assets are in scope
- Feature workers edit separate file ranges
- Reviewers can split final checking by concern if the selected rules and budget allow it
- `main` keeps the work inside the selected orchestration profile and budget
- `delegated-parallel` is allowed only with frozen contracts, disjoint write sets, explicit shared asset owner, independent verification, `main` not writing during fan-out, and `agent_budget > 0`
- `4-6` point work records a lightweight spawn/no-spawn basis only when the delegation choice is non-obvious
- `7+` point work records an explicit `spawn_decision`; concrete blockers can still keep the task `single-session`
- High evaluation need and high orchestration value are separate; a task can have one without the other
- File count alone does not upgrade `evaluation_need` or `orchestration_value`

## Parallel Work That Is Not Safe

- Splitting a form UI, validator, and submit payload across different workers without a pinned contract
- Splitting one migration and the code that depends on it
- Letting feature workers edit shared types or shared utilities directly
- Any flow where reviewer would need to implement fixes just to make the task finish

## Done Means

- The goal can be explained in one line
- Shared contracts match across code and docs
- Required verification passed or the skip reason is explicit
- Reviewer can close with no critical risk
