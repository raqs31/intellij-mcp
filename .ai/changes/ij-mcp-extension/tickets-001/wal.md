# WAL — ij-mcp-extension

Append-only: board.py compact appends each board here before compacting it. Never edit or delete an entry.

## 2026-11-04 15:50 · tickets-001/board.md · 825 tokens · by compactor

~~~~markdown
# Board — Run completion tools (ij-mcp-extension)

change: ij-mcp-extension · board: main · cap: 2000 tokens · history: wal.md

## Goal

Run all 5 phases of plan.md e2e; deliver a buildPlugin zip for manual IntelliJ testing.

## Facts

## Decisions

- 2026-11-04 orchestrator: Scope: all 5 phases of plan.md; out: persisted history, MCP notifications, platform fixture tests.
- 2026-11-04 orchestrator: run: unattended
- 2026-11-04 orchestrator: Git: commit each phase on branch feat/ij-mcp-extension; no push.
- 2026-11-04 orchestrator: Hand-off: after phase 5 run ./gradlew test verifyPlugin buildPlugin; ask user to deploy the zip to IntelliJ for manual testing.
- 2026-11-04 orchestrator: Git (supersedes no push): push feat/ij-mcp-extension to origin after each phase commit; set upstream on first push; never force.
- 2026-11-04 orchestrator: Tickets confirmed as cut. T14 also bumps plugin.xml <version> to 0.4.0.
- 2026-11-04 orchestrator: [auto] T2: reviewer also reads verifyPlugin console output for internal-API lines, not only the report file.
- 2026-11-04 orchestrator: [auto] T14 replan stays in tickets-001 (no new version while T1 runs); T15 also lists plugin.xml.
- 2026-11-04 orchestrator: [auto] Unattended: follow-up triage is batched to the phase 5 hand-off question; P1 follow-ups (stop scope owner, plan wording) wait there.

## Tickets

<!-- tickets:start — rendered by board.py refresh -->
| id | status | phase | holder | needs | tags | title |
| -- | ------ | ----- | ------ | ----- | ---- | ----- |
| T3 | in_progress | 2 | a40694a8756eeae58 |  | config | Test source set: JUnit 4 dependency, CI test step, pre-push command |
| T4 | todo | 2 |  | T3 | interface | Pure run store and formatInstant (RunHistory.kt) with unit tests |
| T5 | todo | 2 |  | T4 | risk-high | RunHistoryService, run and test listeners, startedAt/finishedAt in run header |
| T6 (review) | todo | 2 |  | T3, T4, T5 |  | Phase 2 review |
| T7 | todo | 3 |  | T3, T4 | interface | Pure test-node model and formatTests (TestTree.kt) with unit tests |
| T8 | todo | 3 |  | T5, T7 |  | SM tree mapper, tests=problems\|all on get_run_output, get_test_results tool |
| T9 (review) | todo | 3 |  | T7, T8 |  | Phase 3 review |
| T10 | todo | 4 |  | T3 | interface | Pure wait verdict and timeout clamp (RunWait.kt) with unit tests |
| T11 | todo | 4 |  | T5, T8, T10 | risk-high | wait_for_run tool |
| T12 (review) | todo | 4 |  | T10, T11 |  | Phase 4 review |
| T13 | todo | 5 |  | T5, T11 |  | list_run_history tool |
| T14 | todo | 5 |  | T8, T11, T13 | docs | Docs for new tools and version 0.4.0 |
| T15 (review) | todo | 5 |  | T13, T14 |  | Phase 5 review |
Closed: T1 done · T2 done
<!-- tickets:end -->

## Log

- 2026-11-04 15:36 planner: plan done: T1-T15 (reviews T2, T6, T9, T12, T15)
- 2026-11-04 15:38 planner: replan done: T14 (plugin.xml added to files, Goal and Context name the <version> bump to 0.4.0)
- 2026-11-04 15:38 planner: replan done: T15 (plugin.xml added to files)
- 2026-11-04 15:44 orchestrator: T1 done: internal API removed (public console read + stop), verifier fails on internal API
- 2026-11-04 15:48 orchestrator: T2 done: phase 1 approved, 2 minor follow-ups
- 2026-11-04 15:49 orchestrator: Phase 1 committed 3503035; push failed: sandbox has no GitHub SSH key
~~~~

## 2026-11-04 16:03 · tickets-001/board.md · 752 tokens · by compactor

~~~~markdown
# Board — Run completion tools (ij-mcp-extension)

change: ij-mcp-extension · board: main · cap: 2000 tokens · history: wal.md

## Goal

Run all 5 phases of plan.md e2e; deliver a buildPlugin zip for manual IntelliJ testing.

## Facts

- T1 done: internal API removed (public console read and stop). Verifier fails on internal API.
- T2 done: phase 1 approved with 2 minor follow-ups.
- Phase 1 committed as 3503035 ("Public API baseline (p1)").
- Push failed: sandbox has no GitHub SSH key. Phase 1 exists only locally.

## Decisions

- 2026-11-04 orchestrator: Scope: all 5 phases of plan.md; out: persisted history, MCP notifications, platform fixture tests.
- 2026-11-04 orchestrator: run: unattended
- 2026-11-04 orchestrator: Git: commit each phase on branch feat/ij-mcp-extension; push to origin after each phase commit; set upstream on first push; never force.
- 2026-11-04 orchestrator: Hand-off: after phase 5 run ./gradlew test verifyPlugin buildPlugin; ask user to deploy the zip to IntelliJ for manual testing.
- 2026-11-04 orchestrator: Tickets confirmed as cut. T14 also bumps plugin.xml <version> to 0.4.0; T15 also lists plugin.xml.
- 2026-11-04 orchestrator: [auto] T2: reviewer also reads verifyPlugin console output for internal-API lines, not only the report file.
- 2026-11-04 orchestrator: [auto] T14 replan stays in tickets-001 (no new version while T1 runs). Unattended: follow-up triage is batched to the phase 5 hand-off question; P1 follow-ups (stop scope owner, plan wording) wait there.

## Tickets

<!-- tickets:start — rendered by board.py refresh -->
| id | status | phase | holder | needs | tags | title |
| -- | ------ | ----- | ------ | ----- | ---- | ----- |
| T7 | in_progress | 3 | af03477a6420cd2c1 |  | interface | Pure test-node model and formatTests (TestTree.kt) with unit tests |
| T8 | todo | 3 |  | T7 |  | SM tree mapper, tests=problems\|all on get_run_output, get_test_results tool |
| T9 (review) | todo | 3 |  | T7, T8 |  | Phase 3 review |
| T10 | todo | 4 |  |  | interface | Pure wait verdict and timeout clamp (RunWait.kt) with unit tests |
| T11 | todo | 4 |  | T8, T10 | risk-high | wait_for_run tool |
| T12 (review) | todo | 4 |  | T10, T11 |  | Phase 4 review |
| T13 | todo | 5 |  | T11 |  | list_run_history tool |
| T14 | todo | 5 |  | T8, T11, T13 | docs | Docs for new tools and version 0.4.0 |
| T15 (review) | todo | 5 |  | T13, T14 |  | Phase 5 review |
Closed: T1 done · T2 done · T3 done · T4 done · T5 done · T6 done
<!-- tickets:end -->

## Log

- 2026-11-04 15:50 orchestrator: T3 done: JUnit 4 test dep, CI test step, CONTRIBUTING pre-push test
- 2026-11-04 15:51 orchestrator: T4 done: RunStore, RunRecord, TestCounts, formatInstant + 10 unit tests
- 2026-11-04 15:57 orchestrator: T5 done: RunHistoryService + run/test listeners, startedAt/finishedAt in header
- 2026-11-04 16:01 orchestrator: T6 done: phase 2 approved, 4 minor follow-ups
- 2026-11-04 16:02 orchestrator: Phase 2 committed e73033a; push failed again (no SSH key)
~~~~
