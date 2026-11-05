# Run Completion Tools — Plan Brief

> Full plan: `.ai/changes/ij-mcp-extension/plan.md`

## What & Why

Agents that run tests through the IDE cannot reliably find out when a run ends:

- They cannot wait on a run that another caller started.
- They get no completion event.
- They cannot see when a finished run ended.
- OS-process monitors in WSL do not see the IDE's test JVM.

Twice a worker waited on a run that had already finished. This change gives agents a blocking wait, run timestamps, run history and a compact view of the test results. It also removes all internal IntelliJ API from the plugin, because JetBrains Marketplace review rejects a plugin that uses it.

## Starting Point

- `RunControlToolset` has no state. It reads open Run tabs (`RunContentDescriptor`) on each call.
- `get_run_output` returns the state, the exit code, the test counts with failed stack traces, and a console tail.
- The tools store no start or end time, and a run with a closed tab is gone.
- The repo has no tests.
- The code imports two `.impl.` classes: `ConsoleViewImpl` (console text) and `ExecutionManagerImpl.stopProcess` (stop).
- `build.gradle.kts` mutes `INTERNAL_API_USAGES` in the verifier.

## Desired End State

An agent can do these things:

- Call `wait_for_run` on any run. The call blocks until the process ended and the test tree is finished, and returns the final summary.
- If the run already ended, get the reply at once, with `finishedAt` in the header.
- List recent finished runs of a configuration with `list_run_history`, even after the tab closed.
- Read only failed and unfinished tests by default.
- Fetch one test's stack trace and output with `get_test_results`.

The plugin imports no `.impl.` package and uses no internal API. `verifyPlugin` fails the build if any internal use comes back.

## Key Decisions Made

| Decision                    | Choice                                                    | Why (1 sentence)                                                                |
| --------------------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------- |
| Internal API                | None anywhere. Replace both existing `.impl.` imports; the verifier fails on internal API; an import guard runs in every phase | Marketplace review rejects internal API. |
| Console text (public)       | `DataManager` → `CommonDataKeys.EDITOR` → `document.text`, after `performWhenNoDeferredOutput` (max 1 s) | Same text as `ConsoleViewImpl.getText()`, no impl import. |
| Stop (public)               | `TERMINATION_REQUESTED` + `detachProcess`/`destroyProcess` off the EDT | The same steps as `stopProcess`; there is no public stop API. |
| Scope                       | Items 1–3. The `run_finished` signal is out.              | Claude Code does not wake an agent on MCP notifications.                        |
| Wait by configurationName   | Newest run at call time; returns at once if ended         | Simple; `finishedAt` tells the agent the result is old.                         |
| Done condition              | Process ended **and** test tree finished; 5 s grace       | Counts are final when returned; a crashed fork cannot hang the wait.            |
| Default test view           | Failed + unfinished, grouped by suite; `tests=all` option | A short reply that still shows every problem.                                   |
| Run detail                  | `tests` param **and** new `get_test_results` tool         | Summary stays small; stack traces and test output are on request.               |
| Timeout                     | Default 300 s, max 1800 s, clamp reported                 | Covers long suites; the MCP server itself has no tool timeout.                  |
| History retention           | In memory, last 50 finished runs                          | No disk state; the plugin stays dynamic.                                        |
| Time format                 | ISO-8601 with offset only                                 | Your choice; no relative "ago" fields.                                          |
| Wait mechanism              | Poll every 500 ms, not an event subscription               | A poll sees a run that already ended; an event wait would miss it.              |
| Testing                     | Plain JUnit 4 on pure logic + manual `runIde`             | Fast CI net for the store, filter and wait logic; IDE wiring is checked by hand. |

## Scope

**In scope:**

- Replace `ConsoleViewImpl` and `ExecutionManagerImpl` with public API, and stop muting `INTERNAL_API_USAGES`.
- `wait_for_run`, `list_run_history` and `get_test_results`.
- `startedAt`/`finishedAt` and the `tests=problems|all` param on `get_run_output`.
- `RunHistoryService` with two `<projectListeners>`.
- A test source set and a CI test step.
- README and AGENTS.md updates, and version 0.4.0.

**Out of scope:**

- An MCP notification or wake signal.
- Changes to `execute_run_configuration`.
- Persisted history.
- Platform fixture tests.
- A "wait for the next run" mode.
- Relative time fields.

## Architecture / Approach

The two listeners on `ExecutionManager.EXECUTION_TOPIC` and `SMTRunnerEventsListener.TEST_STATUS` feed a project service, `RunHistoryService`. The service keeps a bounded `RunStore` (pure Kotlin) with the start time, the end time, the exit code and a final test-count snapshot for each run.

The tools read live state from the open descriptors and add data from the store. `wait_for_run` polls that state until a pure `waitVerdict` says that the run is done. The test tree is mapped to plain `TestNode`s, and a pure formatter prints them. The store, the formatter, the verdict and the time formatting are covered by unit tests.

## Phases at a Glance

| Phase                                     | What it delivers                                              | Key risk                                                       |
| ----------------------------------------- | ------------------------------------------------------------- | -------------------------------------------------------------- |
| 1. Public API baseline                    | No `.impl.` imports; the verifier fails on internal API        | Console read can miss output that is not flushed yet; the stop copy can drift from the IDE's own |
| 2. Run history service + timestamps       | Test source set, `RunStore`, listeners, `startedAt/finishedAt` | Stored `env.runProfile.name` may differ from the tab display name |
| 3. Test tree output + `get_test_results`  | `tests` param, suite-grouped view, per-test detail tool        | Default `get_run_output` output changes shape (no stack traces) |
| 4. `wait_for_run`                         | Blocking wait with deadline, grace and clamp                   | MCP client-side timeouts below 300 s cut the call first        |
| 5. `list_run_history` + docs              | History tool, README and AGENTS.md, version 0.4.0              | Low                                                            |

**Prerequisites:** JDK 21+, network for the first `junit:junit` download, and a small JUnit project for the `runIde` checks.
**Estimated effort:** About 3 sessions across 5 phases.

## Open Risks & Assumptions

- Runs that started before the plugin loaded show `startedAt=unknown` and do not appear in the history.
- `SMRootTestProxy.executionId` is often 0. The test snapshot maps to its run through `root.handler`, which needs an open descriptor when `onTestingFinished` fires.
- A Rerun may reuse the same `executionId`. The store then replaces the record. Phase 2 checks this by hand.
- If a later phase needs something that has no public API, the implementer stops and asks. Internal API is never a fallback.
- Some MCP clients cancel long calls on their side. With a short client timeout, the agent must pass a smaller `timeoutSeconds` and loop.

## Success Criteria (Summary)

- An agent calls `wait_for_run` on a run that someone else started and gets the final test counts. It can no longer poll or watch an OS process by mistake.
- A finished run always shows `finishedAt`, so a stale result is visible at once.
- `./gradlew spotlessCheck test buildPlugin verifyPlugin` passes with internal API failing the build. The import guard prints nothing, and the manual `runIde` checks pass.
