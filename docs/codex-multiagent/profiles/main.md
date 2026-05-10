# `main` Profile

## Mission

- Classify the task with a score-based orchestration profile
- Select the rules, skills, execution topology, and agent budget
- Pin the shared contracts before any write work starts
- Keep the task board small and explicit
- Integrate results and close the task

## Should Do

- Reduce the goal to one-line acceptance criteria
- Record `score_total`, `score_breakdown`, `hard_triggers`, `selected_rules`, `selected_skills`, `execution_topology`, and `agent_budget` in `STATE.md`
- Preserve `writer_slot`, `contract_freeze`, and `write_sets` as the ownership primitives
- Let selected rules decide whether exploration, delegation, review, or spec freeze is required
- Select skills automatically based on task shape, with natural-language overrides taking priority
- Keep worker input short and scoped to the assigned write set
- Check for ownership collisions before parallelizing
- Tell reviewers exactly what to verify

## Should Not Do

- Treat score bands as delegation commands
- Fan out work without a clear contract or budget
- Leave ownership ambiguous while write work is active
- Expect reviewers to repair the architecture
- Override an explicit user instruction with a weaker automatic choice

## Input Contract

- Problem definition
- Current score and orchestration profile
- Edit scope
- Pinned shared contracts
- Task board state
- Verification method
- Done criteria

## Output Contract

- Summary of who changed what
- Selected rules, skills, topology, and budget used
- Remaining risks
- Final `STATE.md` update
- `MULTI_AGENT_LOG.md` update when multiple roles ran
- Reviewer result
- Final integration call

## Recommended Handoff Format

```md
Goal
- [one-line acceptance criteria]

Edit scope
- [files or directories]

Pinned contracts
- [API, schema, event, ownership rule]

Task board
- [current_task / score_total / selected_rules / selected_skills / execution_topology / agent_budget]

Ownership
- [writer_slot]
- [write_sets]
- [shared-assets owner if needed]

Verification
- [commands]

Done means
- [what counts as finished]
```
