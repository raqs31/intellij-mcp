# Board — Run completion tools (ij-mcp-extension)

change: ij-mcp-extension · board: main · cap: 2000 tokens · history: wal.md

## Goal

Run all 5 phases of plan.md e2e; deliver a buildPlugin zip for manual IntelliJ testing.

## Facts

- Phases 1, 2 and 3 done and committed locally: 3503035 (p1), e73033a (p2), 3a6d583 (p3). Phase 4 in progress (T10, T16 active).
- Phases 1, 2 and 3 approved; minor follow-ups recorded in the phase reviews (phase 2: 4, phase 3: 3).
- Done: T1 internal API removed; T2 phase 1 review; T3 JUnit 4 test dep, CI test step, CONTRIBUTING pre-push test; T4 RunStore, RunRecord, TestCounts, formatInstant, 10 unit tests; T5 RunHistoryService and run/test listeners; T6 phase 2 review; T7 TestNode model, countTests, formatTests, 6 unit tests; T8 SM tree mapper, tests=problems|all, get_test_results; T9 phase 3 review.
- Push failed twice: sandbox has no GitHub SSH key. Phases 1, 2 and 3 exist only locally.
- Phase 4 done and approved (T10, T11, T16, T17); 3 minor follow-ups in T12, triage at phase 5 hand-off. Board layout is tickets/ plus board.md at change root.

## Decisions

- 2026-11-04 orchestrator: Scope: all 5 phases of plan.md; out: persisted history, MCP notifications, platform fixture tests.
- 2026-11-04 orchestrator: run: unattended
- 2026-11-04 orchestrator: Git: commit each phase on branch feat/ij-mcp-extension; push to origin after each phase commit; set upstream on first push; never force.
- 2026-11-04 orchestrator: Hand-off: after phase 5 run ./gradlew test verifyPlugin buildPlugin; ask user to deploy the zip to IntelliJ for manual testing.
- 2026-11-04 orchestrator: Tickets confirmed as cut. T14 also bumps plugin.xml <version> to 0.4.0; T15 also lists plugin.xml.
- 2026-11-04 orchestrator: [auto] T14 replan stays in tickets-001 (no new version while T1 runs). Follow-up triage is batched to the phase 5 hand-off question; P1 follow-ups (stop scope owner, plan wording) wait there.
- 2026-11-04 orchestrator: Phase 4 gets a fix ticket before wait_for_run: test-listener invokeLater uses ModalityState.any() (RunHistoryService.kt:47).
- 2026-11-04 orchestrator: Phase 4 fixes before T11: get_test_results caps only its output section; formatTests shortens message lines and the test list counts against MAX_OUTPUT_CHARS. Ambiguous nested test names: deferred to final triage.
- 2026-11-04 orchestrator: [auto] T17: steps 4.1/4.3/4.4 as cut; runOutput/testResults caps checked by hand in phase 4 manual checks; test list may take the whole cap.
- 2026-11-05 orchestrator: [auto] Resume: moved tickets-001 to tickets, board.md and wal.md to change root; dropped unsupported steps key from tickets (steps stay in plan.md). Scope as before: phases 4-5, unattended.

## Tickets

<!-- tickets:start — rendered by board.py refresh -->
| id | status | phase | holder | needs | tags | title |
| -- | ------ | ----- | ------ | ----- | ---- | ----- |
| T12 (review) | todo | 4 |  |  |  | Phase 4 review |
| T13 | todo | 5 |  |  |  | list_run_history tool |
| T14 | todo | 5 |  | T13 | docs | Docs for new tools and version 0.4.0 |
| T15 (review) | todo | 5 |  | T13, T14 |  | Phase 5 review |
Closed: T1 done · T2 done · T3 done · T4 done · T5 done · T6 done · T7 done · T8 done · T9 done · T10 done · T11 done · T16 done · T17 done
<!-- tickets:end -->

## Log

- 2026-11-04 16:19 orchestrator: T10 done: waitVerdict + clampTimeout + 8 unit tests
- 2026-11-04 16:20 planner: replan done: T17 new; T11, T12 changed
- 2026-11-04 16:21 orchestrator: T16 done: test listener invokeLater uses ModalityState.any()
- 2026-11-04 16:24 orchestrator: T17 done: get_test_results caps output only; test list and message lines inside caps
- 2026-11-04 16:27 orchestrator: User stopped work; T11 stopped before verify, set todo; WIP committed
- 2026-11-05 11:43 orchestrator: T11 done: wait_for_run verified, no new edits
- 2026-11-05 11:47 orchestrator: T12 done: phase 4 approved, 3 minor follow-ups
