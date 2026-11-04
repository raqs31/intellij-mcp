# Board — Run completion tools (ij-mcp-extension)

change: ij-mcp-extension · board: main · cap: 2000 tokens · history: wal.md

## Goal

Run all 5 phases of plan.md e2e; deliver a buildPlugin zip for manual IntelliJ testing.

## Facts

- Phases 1 and 2 done and committed locally: 3503035 (p1), e73033a (p2). Phase 3 in progress (T7 active).
- T1 done: internal API removed (public console read and stop). Verifier fails on internal API.
- T2 done: phase 1 approved with 2 minor follow-ups.
- T3 done: JUnit 4 test dep, CI test step, CONTRIBUTING pre-push test.
- T4 done: RunStore, RunRecord, TestCounts, formatInstant, 10 unit tests.
- T5 done: RunHistoryService and run/test listeners; startedAt/finishedAt in header.
- T6 done: phase 2 approved with 4 minor follow-ups.
- Push failed twice: sandbox has no GitHub SSH key. Phases 1 and 2 exist only locally.

## Decisions

- 2026-11-04 orchestrator: Scope: all 5 phases of plan.md; out: persisted history, MCP notifications, platform fixture tests.
- 2026-11-04 orchestrator: run: unattended
- 2026-11-04 orchestrator: Git: commit each phase on branch feat/ij-mcp-extension; push to origin after each phase commit; set upstream on first push; never force.
- 2026-11-04 orchestrator: Hand-off: after phase 5 run ./gradlew test verifyPlugin buildPlugin; ask user to deploy the zip to IntelliJ for manual testing.
- 2026-11-04 orchestrator: Tickets confirmed as cut. T14 also bumps plugin.xml <version> to 0.4.0; T15 also lists plugin.xml.
- 2026-11-04 orchestrator: [auto] T14 replan stays in tickets-001 (no new version while T1 runs). Follow-up triage is batched to the phase 5 hand-off question; P1 follow-ups (stop scope owner, plan wording) wait there.
- 2026-11-04 orchestrator: Phase 4 gets a fix ticket before wait_for_run: test-listener invokeLater uses ModalityState.any() (RunHistoryService.kt:47).

## Tickets

<!-- tickets:start — rendered by board.py refresh -->
| id | status | phase | holder | needs | tags | title |
| -- | ------ | ----- | ------ | ----- | ---- | ----- |
| T9 (review) | todo | 3 |  |  |  | Phase 3 review |
| T10 | todo | 4 |  |  | interface | Pure wait verdict and timeout clamp (RunWait.kt) with unit tests |
| T11 | todo | 4 |  | T10, T16 | risk-high | wait_for_run tool |
| T12 (review) | todo | 4 |  | T10, T11, T16 |  | Phase 4 review |
| T13 | todo | 5 |  | T11 |  | list_run_history tool |
| T14 | todo | 5 |  | T11, T13 | docs | Docs for new tools and version 0.4.0 |
| T15 (review) | todo | 5 |  | T13, T14 |  | Phase 5 review |
| T16 | todo | 4 |  |  |  | Test listener stores counts under a modal dialog (ModalityState.any) |
Closed: T1 done · T2 done · T3 done · T4 done · T5 done · T6 done · T7 done · T8 done
<!-- tickets:end -->

## Log

- 2026-11-04 16:05 orchestrator: T7 done: TestNode model, countTests, formatTests + 6 unit tests
- 2026-11-04 16:06 planner: replan done: T16 added (phase 4, ModalityState.any), T11, T12 updated
- 2026-11-04 16:14 orchestrator: T8 done: SM tree mapper, tests=problems|all, get_test_results, shared descriptor lookup
- 2026-11-04 16:18 orchestrator: T9 done: phase 3 approved, 3 minor follow-ups
