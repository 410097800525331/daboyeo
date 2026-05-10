# Task Retrospective Example

```md
- task: `installer update-flow hardening`
- date: `2026-04-13`
- evidence_scope: `one concrete workspace task`
- score_total: 6
- evaluation_fit: `fit`
- orchestration_fit: `fit`
- selected_profile: `single-session`
- predicted_topology: `single-session`
- actual_topology: `single-session`
- spawn_count: 0
- rework_or_reclassification:
  - `reclassified from implementation to verification-only when real smoke evidence became the primary goal`
- reviewer_findings:
  - `none; main self-review only`
- verification_outcome: `macOS workflow passed; local Linux smoke remained indirect`
- what_worked:
  - `Keeping one write lane avoided shell and PowerShell template drift`
- what_broke:
  - `The previous task state was still pinned to the older slice until reclassification`
- next_gate_adjustment:
  - `Proposal evidence only: always report the current score basis from STATE.md before entering the next slice`
- note:
  - `Do not create a separate rule-evolution artifact; reuse task retrospectives when repeated patterns justify kit-level proposals`
```
