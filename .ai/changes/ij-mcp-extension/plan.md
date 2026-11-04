# Run Completion Tools Implementation Plan

## Overview

Coding agents that run tests through the IDE cannot reliably find out when a run ends. They cannot wait on a run that another caller started. They get no completion event. They cannot see when a finished run ended. Twice a worker waited on a run that had already finished.

This plan adds these tools to `RunControlToolset`:

- `wait_for_run`, a blocking wait.
- `startedAt`/`finishedAt` in the run header.
- `list_run_history`.
- A `tests=problems|all` view of the test tree.
- `get_test_results`, which returns the detail of one test.

All plugin code must use public IntelliJ API only. JetBrains Marketplace review rejects a plugin that uses internal API. So Phase 1 first removes the two `.impl.` imports that exist today, and makes `verifyPlugin` fail on internal API.

## Public API Constraint (applies to every phase)

- Never import a class from a `.impl.` package (`com.intellij.**.impl.*`), and never use a `*Impl` class.
- Never call a symbol marked `@ApiStatus.Internal`, `@ApiStatus.Experimental`, `@ApiStatus.ScheduledForRemoval` or `@Deprecated`. Some public classes have marked members. Examples:
  - `DataContext.getData(String)` is `@Internal`. Use the `getData(DataKey)` overload.
  - `SMTestProxy.getStartTimeMillis` and `getEndTimeMillis` are `@Internal` and `@Experimental`.
  - `ExecutionManager.startRunProfile` is `@Internal`.
- If no public API exists for a need, stop and ask the operator. Do not use internal API as a fallback.
- Two checks enforce the rule in every phase:
  - `verifyPlugin`, with `INTERNAL_API_USAGES` failing the build. Phase 1 removes the mute in `build.gradle.kts`.
  - The import guard `! grep -rnE '^import com\.intellij\.([a-zA-Z0-9]+\.)*(impl\.|[A-Za-z0-9]*Impl$)' src/main/kotlin`. It must print nothing.

## Current State Analysis

- `RunControlToolset` (`src/main/kotlin/pl/mario/intellij/mcp/RunControlToolset.kt`) has no state. Each call reads `RunContentManager.allDescriptors` on the EDT.
  - `list_running_configurations` shows live runs only (`:42`).
  - `get_run_output` reads one descriptor by `executionId`, or the newest descriptor for a `configurationName` (`:113`).
- `RunContentDescriptor` stores no start or end time. A run that is not in an open Run tab cannot be found.
- `testSummary` (`:184-205`) counts the leaves of the SM test tree. It prints each failed test with up to 15 stack lines, for at most 20 failed tests. `other` is every leaf that is not ignored, not failed and not passed. In practice these are tests that are unfinished or did not run.
- `execute_run_configuration` comes from JetBrains' built-in MCP server. This plugin cannot change it.
- The repo has no tests and no test source set. CI runs `spotlessCheck`, `buildPlugin` and `verifyPlugin`.
- `.ai/memory/` does not exist, so there are no recorded rules to apply.
- The code imports two `.impl.` classes:
  - `ConsoleViewImpl` (`:3`, `:133`, `:149-150`) reads the console text through `flushDeferredText()` and `getText()`.
  - `ExecutionManagerImpl.stopProcess` (`:4`, `:79`) stops a run.
- `build.gradle.kts:47-50` mutes `INTERNAL_API_USAGES` in `pluginVerification.failureLevel`, so a new internal call would not fail the build.
- On 2026-11-04, `./gradlew verifyPlugin` on 0.3.0 gave `Compatible` against IU-262.11879.100 and IU-263.7307.112. It wrote no internal-API report. The two `.impl.` imports still break the rule above.

## Desired End State

An agent can do these things:

1. Call `wait_for_run(executionId | configurationName, timeoutSeconds)`. The call blocks until the run ends and, for test runs, until the test tree is finished. It returns the `get_run_output` summary. On timeout it returns the same summary with `state=running` and a timeout line. If the run had already ended, the call returns at once, and `finishedAt` shows when the run ended.
2. See `startedAt` and `finishedAt` (ISO-8601 with offset) in the header of `get_run_output` and `wait_for_run`. The value is `unknown` for a run that started before the plugin loaded.
3. Call `list_run_history(configurationName?, limit)`. It lists finished runs, newest first, with `executionId`, `state`, `exitCode`, test counts, `startedAt`, `finishedAt` and `tabOpen`. A run stays in the list after its Run tab closes. The list holds the last 50 finished runs in memory.
4. Get the default test section, which lists only failed and unfinished tests, grouped by suite, one line each. `tests=all` lists every test.
5. Call `get_test_results(executionId | configurationName, testName)`. It returns one test's status, duration, full message, stack trace and its own console output.

6. The plugin imports no `.impl.` package and uses no internal API. `verifyPlugin` fails the build on any internal API use.

To verify: `./gradlew spotlessCheck test buildPlugin verifyPlugin` passes, with `INTERNAL_API_USAGES` not muted. The import guard prints nothing. The manual steps in each phase pass in `./gradlew runIde`.

### Key Discoveries:

- `ExecutionManager.EXECUTION_TOPIC` / `ExecutionListener` (`lib/intellij.platform.execution.jar`) is public API, with no `@ApiStatus` markers. It has `processStarted(executorId, env, handler)` and `processTerminated(executorId, env, handler, exitCode)`.
- `ExecutionManagerImpl` calls `descriptor.setExecutionId(environment.getExecutionId())`, so `env.executionId` is the same id that the tools show.
- `<projectListeners><listener topic="com.intellij.execution.ExecutionListener"/>` is a pattern that bundled plugins use: Flyway, Liquibase and javaee-app-servers. It is dynamic-plugin safe.
- `SMTRunnerEventsListener.TEST_STATUS.onTestingFinished(SMRootTestProxy)` is public API. `root.handler` is set by `SMTestRunnerConnectionUtil`. `root.executionId` is often 0, so map a root to its run through `handler`, not through the id.
- To read one test's own output, call `SMTestProxy.printOwnPrintablesOn(Printer)` with a `Printer` that appends to a `StringBuilder`. `printOn` also includes the children's output. There is no `getOutput()`.
- Avoid `SMTestProxy.getStartTimeMillis` and `getEndTimeMillis`, because they are `@Internal` and `@Experimental`. `getDuration()` is public.
- The JetBrains MCP server puts no timeout on a tool call. `McpSessionHandler` runs the tool without `withTimeout`. A long wait needs its own limit.
- `@McpTool` parameters are decoded with kotlinx.serialization. Enum parameters work, because `AddStringTypeToEnumsStep` adds `"type":"string"` to the schema.
- A plain JUnit 4 test needs only `testImplementation("junit:junit:4.13.2")`. A scratch copy of the repo ran `./gradlew test` with this line and passed. `testFramework(TestFrameworkType.Platform)` is not needed.
- Public way to read console text:
  - `ConsoleViewImpl.uiDataSnapshot` puts `CommonDataKeys.EDITOR` into the data context, and `ConsoleViewImpl.getComponent()` returns the console itself. So `DataManager.getInstance().getDataContext(console.component).getData(CommonDataKeys.EDITOR)?.document?.text` reads the same text that `getText()` read. All of these symbols are unmarked.
  - The pending output is exposed by `ConsoleView.hasDeferredOutput()` and `ConsoleView.performWhenNoDeferredOutput(Runnable)`, which is EDT-only. Both are public and unmarked. There is no public synchronous flush.
- `ExecutionManagerImpl.stopProcess(descriptor)` does three steps:
  1. It sets `handler.putUserData(ProcessHandler.TERMINATION_REQUESTED, true)`.
  2. Off the EDT, it calls `detachProcess()` if `handler.detachIsDefault()` is true, else `destroyProcess()`, unless the process is already terminated.
  3. It calls `KillableProcess.killProcess()` only when the process is already terminating.
- Every `ProcessHandler` member that step 2 uses is public and unmarked. `ExecutionManager` and `RunContentManager` have no public stop method.

## What We're NOT Doing

- No `run_finished` signal or MCP notification. Claude Code does not wake an agent on MCP server notifications. An agent that wants to be woken can call `wait_for_run` from a background subagent.
- No change to the built-in `execute_run_configuration`.
- No persisted history. An IDE restart or a plugin unload clears it.
- No IntelliJ platform fixture tests (`BasePlatformTestCase`). The IDE wiring is checked by hand in `runIde`.
- No `startedAfter` or "wait for the next run" mode. `wait_for_run(configurationName)` waits on the newest run.
- No relative time fields (`finishedAgo`, `duration`) in the header. Only ISO-8601 timestamps are shown.
- No change to the replies or parameters of `stop_run_configuration` and `list_running_configurations`. Phase 1 changes only how `stop_run_configuration` stops a process. No change to the Maven toolset.
- No force-kill branch in the new stop code. The tool already returns `already_stopping` and does not force-kill.

## Implementation Approach

- Put the logic that can break silently into pure Kotlin code with no IDE types, so plain JUnit can test it:
  - the bounded run store
  - the test-node model with its filter and formatter
  - the wait verdict
  - time formatting
- Keep the IDE glue thin:
  - two listener classes that feed a project service
  - a mapper from `SMTestProxy` to plain nodes
  - the tool methods
- `wait_for_run` polls the live state every 500 ms within its own deadline, and does not wait for an event. A run can end before the call, which is the stale-wait case. A poll sees that state on its first check, so it has no race. An event subscription would miss that event.

## Critical Implementation Details

- **Timing & lifecycle**: The process ends before `onTestingFinished`, often by tens of milliseconds. `wait_for_run` counts a test run as done only when the process ended and the test root is no longer `isInProgress`. If the tree is still in progress 5 s after the process ended (for example a crashed fork), it returns with `finished=false`. `wait_for_run(configurationName)` resolves the newest descriptor once, at the start of the call, and then waits on that `executionId` only. Without this, a run that starts during the wait would replace the target.
- **Console read timing**: There is no public flush, so the console read is a suspend step. If `hasDeferredOutput()` is true, the tool waits for the `performWhenNoDeferredOutput` callback, at most 1 s. Then it reads the editor document on the EDT. If the wait times out, it reads the document as it is and adds `console: pending output not flushed` to the reply. `console.component` creates the editor if the tab was never shown, so call it on the EDT only.
- **State sequencing**: The test-count snapshot in the store comes from `onTestingFinished`, not from `processTerminated`. If `processTerminated` stored counts, they could be incomplete. A record with no test snapshot is a non-test run, or a test run whose tree never finished.

## Phase 1: Public API baseline

### Overview

Remove the two `.impl.` imports from `RunControlToolset`. Make `verifyPlugin` fail on any internal API use. The tool replies and parameters stay the same.

### Changes Required:

#### 1. Verifier fails on internal API

**File**: `build.gradle.kts`

**Intent**: Make the build catch internal API at once, as Marketplace review does.

**Contract**:
- `pluginVerification.failureLevel` no longer filters out `INTERNAL_API_USAGES`.
- The comment that says run-control tools read internals on purpose is removed.
- `freeArgs` (the naming mutes) stays.

#### 2. Console text through public API

**File**: `src/main/kotlin/pl/mario/intellij/mcp/RunControlToolset.kt`

**Intent**: Read a run's console text without `ConsoleViewImpl`.

**Contract**:
- The console-chain walk stays. It picks the first `ConsoleView` in the chain instead of the first `ConsoleViewImpl`.
- The text is `DataManager.getInstance().getDataContext(console.component).getData(CommonDataKeys.EDITOR)?.document?.text`, read on the EDT.
- Before the read, if `console.hasDeferredOutput()` is true, wait up to 1 s for `performWhenNoDeferredOutput`, as Critical Implementation Details says.
- If no editor is found (the console is disposed or is not a text console), return `no_text_console: …`, as today.
- `get_run_output` keeps its reply format.

**Contract** (snippet — the `DataKey` overload is required, because `getData(String)` is `@Internal`):

```kotlin
val editor = DataManager.getInstance().getDataContext(console.component).getData(CommonDataKeys.EDITOR)
```

#### 3. Stop through public ProcessHandler API

**File**: `src/main/kotlin/pl/mario/intellij/mcp/RunControlToolset.kt`

**Intent**: Do what the IDE's Stop action does without `ExecutionManagerImpl.stopProcess`. The IDE then still sees the stop as one that the user asked for.

**Contract**:
- On the EDT, call `handler.putUserData(ProcessHandler.TERMINATION_REQUESTED, true)`.
- Off the EDT, on `Dispatchers.Default`, call `handler.detachProcess()` if `handler.detachIsDefault()` is true, else `handler.destroyProcess()`. Skip this if the process is already terminated.
- The tool does not wait for that call, so `stop_requested` returns at once, as today.
- There is no `KillableProcess.killProcess()` branch.
- The `stop_run_configuration` replies stay the same.

### Success Criteria:

#### Automated Verification:

- Formatting passes: `./gradlew spotlessCheck`
- Import guard prints nothing: `! grep -rnE '^import com\.intellij\.([a-zA-Z0-9]+\.)*(impl\.|[A-Za-z0-9]*Impl$)' src/main/kotlin`
- Plugin builds and verifies, with internal API failing the build: `./gradlew buildPlugin verifyPlugin`
- No internal-API report exists: `! find build/reports/pluginVerifier -iname '*internal*' | grep -q .`

#### Manual Verification:

- In `runIde`, `get_run_output` returns the same console text as the Run tab, for an application run and for a JUnit run. Check the last lines just after the run ends.
- `stop_run_configuration` stops a running application. The Run tab shows a user stop, not a crash. A second call returns `already_stopping` or `not_running`.

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Run history service and timestamps

### Overview

Add the test source set, a pure run store, and a project service that the listeners feed. Show `startedAt`/`finishedAt` in the `get_run_output` header.

### Changes Required:

#### 1. Test source set and CI

**File**: `build.gradle.kts`, `.github/workflows/ci.yml`, `CONTRIBUTING.md`

**Intent**: Add a JUnit 4 dependency so plain unit tests compile and run. Run the tests in CI and in the pre-push command.

**Contract**:
- `dependencies { testImplementation("junit:junit:4.13.2") }`.
- CI build job: add a `./gradlew test` step before `buildPlugin`.
- CONTRIBUTING "Before pushing": `./gradlew spotlessCheck test verifyPluginConfiguration buildPlugin`.

#### 2. Pure run store and time formatting

**File**: `src/main/kotlin/pl/mario/intellij/mcp/RunHistory.kt` (new)

**Intent**: Hold one record per run, with no IDE types, so that eviction and ordering are unit-testable.

**Contract**:
- `data class TestCounts(total, passed, failed, ignored, other: Int, finished: Boolean)`.
- `data class RunRecord(executionId: Long, name: String, startedAtMillis: Long?, finishedAtMillis: Long?, exitCode: Int?, tests: TestCounts?)`.
- `class RunStore(maxFinished: Int = 50)`:
  - `started(id, name, atMillis)`. A repeated id replaces the record.
  - `terminated(id, exitCode, atMillis)`. It creates a record with `startedAtMillis=null` if the start was not seen.
  - `testsFinished(id, counts)`.
  - `get(id)`.
  - `finished(name: String?, limit: Int)`, newest `finishedAtMillis` first.
- Live records are never evicted. Once there are more than `maxFinished` finished records, the oldest finished record is dropped.
- The store is thread-safe, because listener callbacks can arrive off the EDT.
- `fun formatInstant(millis: Long?): String` returns local ISO-8601 with offset at second precision, for example `2026-11-04T14:03:40+01:00`, or `unknown` for null.

#### 3. Project service and listeners

**File**: `src/main/kotlin/pl/mario/intellij/mcp/RunHistoryService.kt` (new), `src/main/resources/META-INF/plugin.xml`

**Intent**: Record the start, end and final test counts of each run in the project. The tools use this to show timestamps and history after a tab closes.

**Contract**:
- `@Service(Service.Level.PROJECT) class RunHistoryService` wraps one `RunStore`.
- `class RunHistoryExecutionListener(project: Project) : ExecutionListener`:
  - `processStarted` calls `started(env.executionId, env.runProfile.name, now)`.
  - `processTerminated` calls `terminated(env.executionId, exitCode, now)`.
- `class RunHistoryTestListener(project: Project) : SMTRunnerEventsAdapter()`. Its `onTestingFinished(root)` finds the open descriptor whose `processHandler === root.handler`, then stores `TestCounts` for that descriptor's `executionId`.
- The counting logic moves out of `testSummary` into one shared function, so the store and the tools count the same way.
- `plugin.xml` registers both classes under `<projectListeners>`, with `topic="com.intellij.execution.ExecutionListener"` and `topic="com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener"`.

#### 4. Timestamps in the run header

**File**: `src/main/kotlin/pl/mario/intellij/mcp/RunControlToolset.kt`

**Intent**: Show when the run started and ended, so an agent can see that a result is old.

**Contract**: The `runOutput` header line becomes `executionId=…, name=…, state=…, startedAt=<iso|unknown>, finishedAt=<iso|unknown|->, console=…`. A running process shows `finishedAt=-`. The `get_run_output` `@McpDescription` names the new fields.

### Success Criteria:

#### Automated Verification:

- Formatting passes: `./gradlew spotlessCheck`
- Unit tests pass: `./gradlew test` (`RunStoreTest`: eviction keeps live runs, newest-first order, name filter, terminate-without-start, repeated start replaces; `formatInstant` null and offset cases)
- Plugin builds: `./gradlew buildPlugin`
- Plugin verifier passes with internal API failing the build: `./gradlew verifyPlugin`
- Import guard prints nothing: `! grep -rnE '^import com\.intellij\.([a-zA-Z0-9]+\.)*(impl\.|[A-Za-z0-9]*Impl$)' src/main/kotlin`

#### Manual Verification:

- In `./gradlew runIde`, run a JUnit configuration twice. `get_run_output` shows real `startedAt`/`finishedAt` values, and `finishedAt` is later than `startedAt`.
- A run that was already in progress when the plugin loaded shows `startedAt=unknown`.
- A Rerun of the same tab gives a new `executionId`, or the record is replaced with a new `startedAt`.

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase. Phase blocks use plain bullets — the corresponding `- [ ]` checkboxes for these items live in the `## Progress` section at the bottom of the plan.

---

## Phase 3: Test tree output and get_test_results

### Overview

Replace the failed-test block in the run output with a suite-grouped view that a parameter selects. Add a tool that returns the detail of one test.

### Changes Required:

#### 1. Pure test-node model and formatter

**File**: `src/main/kotlin/pl/mario/intellij/mcp/TestTree.kt` (new)

**Intent**: Put the test view in one place that unit tests cover. Both `get_run_output` and `wait_for_run` use it.

**Contract**:
- `enum class TestStatus { PASSED, FAILED, IGNORED, UNFINISHED }`.
- `data class TestNode(suite: String?, name: String, status: TestStatus, message: String?, durationMs: Long?)`.
- `enum class TestsDetail { problems, all }`. The lowercase names are the MCP parameter values.
- `fun formatTests(nodes: List<TestNode>, finished: Boolean, detail: TestsDetail): String`:
  - The counts line keeps its current shape: `tests: total=…, passed=…, failed=…, ignored=…, other=…, finished=…`.
  - Then one `suite <name>: <n> tests, failed=<f>, unfinished=<u>` line per suite that has a listed test.
  - Then one indented line per listed test: `FAILED|UNFINISHED|IGNORED|PASSED <suite>.<name> — <first message line>`.
- `problems` lists FAILED and UNFINISHED tests only. `all` lists every test.
- At most 1000 test lines are listed, followed by `... and N more`.

#### 2. SM tree mapper and the tests parameter

**File**: `src/main/kotlin/pl/mario/intellij/mcp/RunControlToolset.kt`

**Intent**: Feed the formatter from the IDE tree, and let the agent pick the detail level.

**Contract**:
- `SMTestProxy` leaves map to `TestNode`, with the same ignored-before-defect rule as today.
- `get_run_output` gets `tests: TestsDetail = TestsDetail.problems`.
- `testSummary` is replaced by `formatTests`. Stack traces leave the default output, and `get_test_results` returns them.
- The `@McpDescription` names `tests` and points to `get_test_results` for stack traces and test output.

#### 3. get_test_results tool

**File**: `src/main/kotlin/pl/mario/intellij/mcp/RunControlToolset.kt`

**Intent**: Return the full detail of one test, so the run summary can stay short.

**Contract**:
- `get_test_results(executionId: Long? = null, configurationName: String? = null, testName: String)`. Read-only hints. It resolves the descriptor the same way as `get_run_output`.
- `testName` matches a leaf's `<suite>.<name>` exactly, or else the leaf's `name`.
- The reply has:
  - a header line with the qualified name, status and duration
  - `message:` with the full error message
  - `stacktrace:` with the full stack trace
  - `output:` with the test's own console text, from `printOwnPrintablesOn` with a `StringBuilder` `Printer`
- The reply is capped at `MAX_OUTPUT_CHARS`. When text is cut, the tail is kept.
- Status strings:
  - `missing_argument`
  - `not_found` (the run's tab is closed)
  - `not_a_test_run`
  - `test_not_found: <name>`, with the first 20 failed or unfinished names as hints
  - `ambiguous_test: <name>`, with the matching qualified names

### Success Criteria:

#### Automated Verification:

- Formatting passes: `./gradlew spotlessCheck`
- Unit tests pass: `./gradlew test` (`TestTreeTest`: `problems` hides PASSED and IGNORED; `all` lists all; suite grouping; 1000-line cap with `... and N more`; counts line unchanged in shape; message reduced to its first line)
- Plugin builds and verifies, with internal API failing the build: `./gradlew buildPlugin verifyPlugin`
- Import guard prints nothing: `! grep -rnE '^import com\.intellij\.([a-zA-Z0-9]+\.)*(impl\.|[A-Za-z0-9]*Impl$)' src/main/kotlin`

#### Manual Verification:

- In `runIde`, for a JUnit run with 2 failing tests, `get_run_output` lists both under their suite, without stack traces. `tests=all` also lists the passed tests.
- `get_test_results` for a failing test returns its message, its stack trace and the `System.out` text that the test printed.
- `get_test_results` with an unknown name returns `test_not_found` with hints. A non-test run returns `not_a_test_run`.

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: wait_for_run

### Overview

Add a blocking wait on any run, with its own deadline. It returns the same summary as `get_run_output`.

### Changes Required:

#### 1. Pure wait verdict

**File**: `src/main/kotlin/pl/mario/intellij/mcp/RunWait.kt` (new)

**Intent**: Keep the decision about when to stop waiting in a function that unit tests cover.

**Contract**:
- `enum class WaitVerdict { WAIT, DONE, DONE_TREE_UNFINISHED }`.
- `fun waitVerdict(processEnded: Boolean, hasTestTree: Boolean, treeFinished: Boolean, processEndedAtMillis: Long?, nowMillis: Long, graceMillis: Long = 5_000): WaitVerdict`.
- `fun clampTimeout(requested: Int?): Pair<Int, Boolean>` sets the default to 300. Values below 0 become 0, and values above 1800 become 1800. The Boolean is true when the value was clamped.

#### 2. wait_for_run tool

**File**: `src/main/kotlin/pl/mario/intellij/mcp/RunControlToolset.kt`

**Intent**: Let any agent block until a run, including one that someone else started, has fully ended. This removes the poll loops and the OS-process monitors.

**Contract**:
- `wait_for_run(executionId: Long? = null, configurationName: String? = null, timeoutSeconds: Int = 300, tests: TestsDetail = problems, tailLines: Int = 200, grep: String? = null)`. Read-only hints.
- The target is resolved once. By name, it is the newest descriptor at call time.
- The tool polls every 500 ms on the EDT until the verdict is not `WAIT` or the deadline passes. Between polls it calls `delay` outside the EDT, so the EDT is never blocked.
- `timeoutSeconds=0` checks once and returns at once.
- The grace clock uses the store's `finishedAtMillis`. If the store has none, it uses the time this call first saw the process end.
- The reply is `runOutput(…)`, with a final line `wait: done after <n>s`, `wait: timeout after <n>s` (the header then shows `state=running`) or `wait: test tree not finished <grace>s after process end`.
- A clamp adds `wait: timeoutSeconds clamped to 1800`.
- If the tab closes during the wait, the reply is built from the store record: `tab_closed: executionId=…, state=terminated, exitCode=…, startedAt=…, finishedAt=…` plus the stored counts.
- Argument errors use the same status strings as `get_run_output`.
- The `@McpDescription` says that the call returns at once when the run already ended, and that `finishedAt` shows when.

### Success Criteria:

#### Automated Verification:

- Formatting passes: `./gradlew spotlessCheck`
- Unit tests pass: `./gradlew test` (`RunWaitTest`: running → WAIT; ended without tree → DONE; ended with unfinished tree inside grace → WAIT; after grace → DONE_TREE_UNFINISHED; ended with finished tree → DONE; `clampTimeout` default, negative, above max)
- Plugin builds and verifies, with internal API failing the build: `./gradlew buildPlugin verifyPlugin`
- Import guard prints nothing: `! grep -rnE '^import com\.intellij\.([a-zA-Z0-9]+\.)*(impl\.|[A-Za-z0-9]*Impl$)' src/main/kotlin`

#### Manual Verification:

- In `runIde`, start a test run of about 20 s from the IDE UI. `wait_for_run(configurationName)` blocks and returns final counts with `finished=true`. The counts match the IDE's test tree.
- `wait_for_run` on the same run after it ended returns at once, with a `finishedAt` in the past.
- `wait_for_run(timeoutSeconds=3)` on a long run returns `state=running` and `wait: timeout after 3s`.
- While a `wait_for_run` call is active, the IDE UI stays responsive.

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 5: list_run_history and docs

### Overview

Show recent finished runs from the store, document the new tools and bump the version.

### Changes Required:

#### 1. list_run_history tool

**File**: `src/main/kotlin/pl/mario/intellij/mcp/RunControlToolset.kt`

**Intent**: Let an agent see recent finished runs of one configuration, including runs whose tab is closed.

**Contract**:
- `list_run_history(configurationName: String? = null, limit: Int = 10)`. Read-only hints. `limit` is clamped to 1..50.
- It returns one line per finished run, newest first: `executionId=…, name=…, state=terminated, exitCode=…, startedAt=…, finishedAt=…, tabOpen=true|false`. When counts exist, the line adds `, tests: total=…, passed=…, failed=…, ignored=…, other=…, finished=…`.
- `tabOpen` comes from `RunContentManager` on the EDT.
- An empty result is `No finished runs recorded since the plugin loaded.`
- The `@McpDescription` says that history is in memory, keeps the last 50 runs, and is cleared on IDE restart.

#### 2. Docs and version

**File**: `README.md`, `AGENTS.md`, `gradle.properties`

**Intent**: Document the new tools, the `tests` parameter and the service, and release them as a minor version.

**Contract**:
- README tool list and Installation step 5 (Exposed Tools) add `wait_for_run`, `get_test_results` and `list_run_history`. Add one usage example for `wait_for_run`.
- AGENTS.md "Toolsets" names `RunHistoryService` and its listeners.
- `pluginVersion = 0.4.0`.

### Success Criteria:

#### Automated Verification:

- Full pre-push check passes: `./gradlew spotlessCheck test verifyPluginConfiguration buildPlugin verifyPlugin`
- Import guard prints nothing: `! grep -rnE '^import com\.intellij\.([a-zA-Z0-9]+\.)*(impl\.|[A-Za-z0-9]*Impl$)' src/main/kotlin`
- Artifact exists: `ls build/distributions/*0.4.0*.zip`

#### Manual Verification:

- In `runIde`, after 3 runs of one configuration and 1 run of another, `list_run_history(configurationName=…)` lists the 3 runs, newest first, with counts and `finishedAt`.
- After the Run tab of a listed run is closed, the run is still listed with `tabOpen=false`.
- All 8 run and Maven tools show under Settings → Tools → MCP Server → Exposed Tools.

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful.

---

## Testing Strategy

### Unit Tests:

- `RunStoreTest`: covers eviction at 50 with live runs kept, ordering and filtering, a terminate with no start, and a start that replaces a record.
- `TimeFormatTest`: `formatInstant` with null and with an offset.
- `TestTreeTest`: covers the problems/all filter, suite grouping, the line cap and the counts line.
- `RunWaitTest`: every branch of the verdict, and the timeout clamp.

### Integration Tests:

- None automated. The IDE wiring (listeners, EDT access, SM tree mapping) is checked by hand in `runIde`.

### Manual Testing Steps:

1. Open a small JUnit project in `./gradlew runIde`. Connect an MCP client to the sandbox IDE's MCP server.
2. Start a test run from the IDE UI. Call `wait_for_run(configurationName)` from the client, and compare the reply with the IDE's test tree.
3. Call `wait_for_run` again on the same run. It returns at once, with the same `finishedAt`.
4. Call `get_test_results` for a failing test, and check its stack trace and output.
5. Close the Run tab. Call `list_run_history`, and check that the run shows `tabOpen=false`.

## Performance Considerations

- Each poll reads the descriptors on the EDT. The read is small (one descriptor and its test root), and 500 ms polls add no visible load.
- Formatting a large test tree with `tests=all` runs on the EDT once per reply. The 1000-line cap and `MAX_OUTPUT_CHARS` limit its cost.

## Migration Notes

- The default `get_run_output` test section changes shape. It now groups failed and unfinished tests by suite, one line each, with no stack traces. This is not a tool or parameter rename, so CONTRIBUTING does not count it as breaking. Name it in the commit body.

## References

- Current toolset: `src/main/kotlin/pl/mario/intellij/mcp/RunControlToolset.kt:86-205`
- Plugin descriptor: `src/main/resources/META-INF/plugin.xml`
- Listener precedent: Flyway `FlywayExecutionListener` (bundled `plugins/flyway/lib/flyway.jar`)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Public API baseline

#### Automated

- [x] 1.1 Formatting passes
- [x] 1.2 Import guard prints nothing
- [x] 1.3 Plugin builds and verifies with internal API failing the build
- [x] 1.4 No internal-API report exists

#### Manual

- [ ] 1.5 get_run_output text matches the Run tab for app and JUnit runs
- [ ] 1.6 stop_run_configuration stops as a user stop; repeat call returns already_stopping or not_running

### Phase 2: Run history service and timestamps

#### Automated

- [ ] 2.1 Formatting passes
- [ ] 2.2 Unit tests pass (RunStoreTest, formatInstant)
- [ ] 2.3 Plugin builds
- [ ] 2.4 Plugin verifier passes with internal API failing the build
- [ ] 2.5 Import guard prints nothing

#### Manual

- [ ] 2.6 startedAt/finishedAt shown for two JUnit runs
- [ ] 2.7 Run started before plugin load shows startedAt=unknown
- [ ] 2.8 Rerun gives new executionId or replaces the record

### Phase 3: Test tree output and get_test_results

#### Automated

- [ ] 3.1 Formatting passes
- [ ] 3.2 Unit tests pass (TestTreeTest)
- [ ] 3.3 Plugin builds and verifies
- [ ] 3.4 Import guard prints nothing

#### Manual

- [ ] 3.5 Failing tests listed by suite without stack traces; tests=all lists passed
- [ ] 3.6 get_test_results returns message, stack trace and test output
- [ ] 3.7 Unknown test and non-test run return status strings

### Phase 4: wait_for_run

#### Automated

- [ ] 4.1 Formatting passes
- [ ] 4.2 Unit tests pass (RunWaitTest)
- [ ] 4.3 Plugin builds and verifies
- [ ] 4.4 Import guard prints nothing

#### Manual

- [ ] 4.5 wait_for_run blocks and returns final counts matching the IDE tree
- [ ] 4.6 wait_for_run on an ended run returns at once with past finishedAt
- [ ] 4.7 Short timeout returns state=running and timeout line
- [ ] 4.8 IDE UI stays responsive during a wait

### Phase 5: list_run_history and docs

#### Automated

- [ ] 5.1 Full pre-push check passes
- [ ] 5.2 Import guard prints nothing
- [ ] 5.3 0.4.0 artifact exists

#### Manual

- [ ] 5.4 list_run_history lists runs of one configuration newest first
- [ ] 5.5 Closed-tab run still listed with tabOpen=false
- [ ] 5.6 All 8 tools visible in Exposed Tools
