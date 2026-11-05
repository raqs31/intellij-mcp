---
change_id: ij-mcp-extension
title: Run completion tools — wait_for_run, run timestamps, run history and test results
status: archived
created: 2026-11-04
updated: 2026-11-05
archived_at: 2026-11-05T11:44:13Z
---

## Notes

Agents that wait on an IDE run cannot block on a run that another caller started. They get no completion event and no run timestamps.
Twice a worker waited on a run that had already finished. This change adds `wait_for_run`, `startedAt`/`finishedAt`, `list_run_history`,
a `tests=problems|all` view and `get_test_results` to `RunControlToolset`.
