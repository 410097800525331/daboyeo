# Task Retrospective

## 2026-04-14 - workspace skeleton

- task: `Build collaboration-ready workspace skeleton`
- score_total: `5`
- selected_profile: `single-session`
- actual_topology: `main only`
- verification_outcome: `repository verification commands passed; Gradle build not run because gradle is not available on PATH`
- collisions_or_reclassifications: `reclassified from completed TiDB schema validation; no file ownership collision found`
- next_rule_change: `When Bootstrap, Flyway baseline, or build wrapper is chosen, record it as a separate stack decision before implementation.`

## 2026-04-15 - local Gemma recommendation v1

- task: `Implement local Ollama fast/precise AI recommendation backend flow`
- score_total: `7`
- evaluation_fit: `full was appropriate because API, DB, local LLM, and anonymous storage changed`
- predicted_topology: `single-session, then mixed after user authorized subagents`
- actual_topology: `main implementation with one read-only explorer and one reviewer`
- spawn_count: `2`
- rework_or_reclassification: `fast model source briefly considered LM Studio, then reclassified back to Ollama-only after user chose to unify model serving; frontend implementation was removed after scope clarification`
- reviewer_findings: `fixed Map.of compile blocker and strengthened child age filter; JSON/test/CORS findings were rechecked after UTF-8 and recent patches`
- verification_outcome: `poster seed JSON parsed; LM Studio residue search clean; Ollama E4B registered; git diff --check passed; frontend diff removed; gradle test deferred because java/gradle/gradlew are unavailable`
- next_gate_adjustment: `Before future Spring implementation, install or locate JDK/Gradle or add a Gradle wrapper before coding so backend tests can run in-turn.`

## 2026-05-05 - selective kmh update import

- task: `Selective kmh update import`
- score_total: `8`
- selected_profile: `single-session`
- actual_topology: `main only`
- verification_outcome: `node --check passed for touched frontend/static JS; focused live/nearby Gradle tests passed outside sandbox; bootJar passed; served localhost:5500 static marker checks passed; Spring restarted on PID 30992 with /api/health 200`
- collisions_or_reclassifications: `full origin/kmh merge was rejected after merge-tree showed broad add/add and changed/both conflicts; scope was narrowed to origin/kmh 204fab1 compatible backend/frontend behavior and then expanded to static mirror files because localhost:5500 serves backend static resources`
- next_rule_change: `For teammate branch imports, inspect latest commit intent first and mirror frontend changes into the Spring static surface before runtime restart.`

## 2026-05-05 - anime theatrical poster top30 seed

- task: `Anime theatrical poster top30 seed`
- score_total: `8`
- selected_profile: `single-session`
- actual_topology: `main only`
- verification_outcome: `KOBIS all-time rows were de-duplicated by movieCd, 400 rows were scanned until 30 movie detail pages with animation genre were found, 30 WebP posters were generated and mirrored, JSON parse and script verify-only passed, runtime asset checks passed, screens_zero=0, and the minimum generated poster size is 600x861`
- collisions_or_reclassifications: `ranking-only dry run initially exposed duplicate rows from repeated KOBIS statistic tables, and code review exposed all-zero screens metadata plus a 150x215 Kung Fu Panda poster; the scraper now uses td_totScrnCnt, checks KOBIS business poster candidates, and enforces a minimum poster resolution`
- next_rule_change: `For future poster pools, make the source scraper de-duplicate official table variants before any ranking cutoff is trusted, then review low-resolution poster candidates before wiring them into a visible selection UI.`

## 2026-05-05 - Selfdex ingest duplicate cleanup

- task: `Ingest Lotte/Megabox duplicate cleanup`
- score_total: `5`
- selected_profile: `single-session`
- actual_topology: `main only after Selfdex read-only planning`
- verification_outcome: `bundled Python compileall passed for scripts/ingest/collect_all_to_tidb.py; AST key check confirmed provider_ingest_result preserves the existing result key set and both ingest functions use provider_ingest_result plus finalize_provider_ingest; git diff --check passed with CRLF warnings only`
- collisions_or_reclassifications: `Selfdex suggested python scripts/plan_next_task.py, but that command is not present in this repo, so verification used local compile and static contract checks instead; no DB write or provider crawl was run`
- next_rule_change: `When Selfdex suggests a verification command, confirm the command exists before freezing it as required verification.`

## 2026-05-05 - poster movie/anime folder split

- task: `Split poster assets into movie/anime folders`
- score_total: `5`
- selected_profile: `single-session`
- actual_topology: `main only`
- verification_outcome: `frontend, Spring static, and build runtime resources now each have 50 movie WebPs under R2/posters/movie and 30 anime WebPs under R2/posters/anime; anime verify-only and manifest hash/path checks passed; localhost:5500 health and new movie/anime static URLs returned 200 after Spring restart`
- collisions_or_reclassifications: `the task replaced the previous ingest cleanup as the active task; no file ownership collision found, but default python was absent from PATH so verification used the bundled Codex Python`
- next_rule_change: `When moving served static assets, sync build resources and restart Spring if any manifest-backed API caches resource paths at startup.`

## 2026-05-05 - anime poster pool recommendation wiring

- task: `Wire anime poster pool into recommendation flow`
- score_total: `8`
- selected_profile: `single-session`
- actual_topology: `main only`
- verification_outcome: `focused PosterSeedServiceTests and PreferenceProfileBuilderTests passed; bootJar passed; node --check passed for frontend and Spring static JS; default poster-seed API returns movie poster paths only, while genres=animation returns namespaced anime ids and R2/posters/anime paths; localhost:5500 runs from the updated bootJar on PID 27076`
- collisions_or_reclassifications: `the existing /goal object was already complete so the new goal was frozen in STATE.md instead; anime seed ids were namespaced to avoid the movieCd collisions called out during poster seed review`
- next_rule_change: `When adding a second seed pool, expose an explicit pool-selection input and namespace ids before wiring frontend choices into profile lookup.`
