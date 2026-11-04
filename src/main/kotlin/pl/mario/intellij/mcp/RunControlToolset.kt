package pl.mario.intellij.mcp

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.testframework.Printable
import com.intellij.execution.testframework.Printer
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewWithDelegate
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.ide.DataManager
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHintValue
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.project
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.regex.PatternSyntaxException
import kotlin.coroutines.coroutineContext

private const val MAX_OUTPUT_CHARS = 100_000
private const val MAX_TEST_HINTS = 20
private const val FLUSH_TIMEOUT_MS = 1_000L
private const val WAIT_POLL_MS = 500L
private const val WAIT_GRACE_MS = 5_000L

class RunControlToolset : McpToolset {
    @McpTool
    @McpToolHints(readOnlyHint = McpToolHintValue.TRUE, openWorldHint = McpToolHintValue.FALSE)
    @McpDescription(
        "List live IDE run/debug processes in the selected project. Returns execution IDs, names and states. Pass projectPath to select the project.",
    )
    suspend fun list_running_configurations(): String {
        val project = coroutineContext.project
        return withContext(Dispatchers.EDT) {
            val descriptors =
                RunContentManager
                    .getInstance(project)
                    .allDescriptors
                    .filter { it.processHandler?.isProcessTerminated == false }
            if (descriptors.isEmpty()) {
                "No running configurations."
            } else {
                descriptors.joinToString("\n") {
                    val state = if (it.processHandler?.isProcessTerminating == true) "stopping" else "running"
                    "executionId=${it.executionId}, name=${it.name()}, state=$state"
                }
            }
        }
    }

    @McpTool
    @McpToolHints(
        readOnlyHint = McpToolHintValue.FALSE,
        destructiveHint = McpToolHintValue.TRUE,
        idempotentHint = McpToolHintValue.TRUE,
        openWorldHint = McpToolHintValue.FALSE,
    )
    @McpDescription(
        "Request IntelliJ's normal Stop action for exactly one run/debug process in the selected project. Use an executionId from list_running_configurations and pass projectPath. Returns immediately; stop_requested does not mean the process has exited. Repeated calls while terminating do not force-kill it.",
    )
    suspend fun stop_run_configuration(
        @McpDescription("Execution ID returned by list_running_configurations.") executionId: Long,
    ): String {
        val project = coroutineContext.project
        return withContext(Dispatchers.EDT) {
            val matches =
                RunContentManager
                    .getInstance(project)
                    .allDescriptors
                    .filter { it.executionId == executionId && it.processHandler?.isProcessTerminated == false }
            when {
                matches.isEmpty() -> "not_running: executionId=$executionId. Refresh list_running_configurations."
                matches.size != 1 -> "ambiguous: executionId=$executionId matches multiple processes; no process stopped."
                matches.single().processHandler?.isProcessTerminating == true -> "already_stopping: executionId=$executionId"
                else -> {
                    matches.single().processHandler?.let(::requestStop)
                    "stop_requested: executionId=$executionId"
                }
            }
        }
    }

    @McpTool
    @McpToolHints(readOnlyHint = McpToolHintValue.TRUE, openWorldHint = McpToolHintValue.FALSE)
    @McpDescription(
        "Read the console output of one run/debug process in the selected project, running or finished (a finished run stays readable until its Run/Debug tab is closed). Pass exactly one of executionId (from list_running_configurations) or configurationName (reads the newest run of that configuration), plus projectPath. The header shows startedAt and finishedAt as ISO-8601 with offset: unknown when the IDE did not record it (for example a run started before the plugin loaded), and finishedAt=- while the process runs. For test runs it adds pass/fail/ignored counts and a suite-grouped test list from the IDE's test results tree, which is reliable even when exitCode is 0; tests=problems (default) lists failed and unfinished tests, tests=all lists every test. Use get_test_results for one test's full message, stack trace and own output. The console holds only IntelliJ's cycle buffer (1 MB by default), so the head of a long log may be gone.",
    )
    suspend fun get_run_output(
        @McpDescription("Execution ID returned by list_running_configurations.") executionId: Long? = null,
        @McpDescription("Run configuration name; the newest run with this name is read.") configurationName: String? = null,
        @McpDescription("Return only the last N console lines, counted after grep. 0 returns all. Default 200.") tailLines: Int = 200,
        @McpDescription("Optional Java regex; keep only console lines containing a match.") grep: String? = null,
        @McpDescription("Test list detail for test runs: problems (default) lists failed and unfinished tests, all lists every test.")
        tests: TestsDetail = TestsDetail.problems,
    ): String {
        selectorError(executionId, configurationName)?.let { return it }
        val filter =
            try {
                grep?.let(::Regex)
            } catch (e: PatternSyntaxException) {
                return "invalid_regex: ${e.description}"
            }
        return withDescriptor(executionId, configurationName) { project, descriptor ->
            runOutput(project, descriptor, tailLines, filter, tests)
        }
    }

    @McpTool
    @McpToolHints(readOnlyHint = McpToolHintValue.TRUE, openWorldHint = McpToolHintValue.FALSE)
    @McpDescription(
        "Read one test of a run/debug process in the selected project: status, duration, full error message, full stack trace and the test's own console output. Pass exactly one of executionId (from list_running_configurations) or configurationName (newest run of that configuration), plus testName as <suite>.<name> or just the test name, as listed by get_run_output, plus projectPath. Only the test output is capped, and when cut its end is kept; status, message and stack trace are always whole.",
    )
    suspend fun get_test_results(
        @McpDescription("Execution ID returned by list_running_configurations.") executionId: Long? = null,
        @McpDescription("Run configuration name; the newest run with this name is read.") configurationName: String? = null,
        @McpDescription("Test to read: <suite>.<name> as listed by get_run_output, or the test name alone when it is unique.") testName:
            String,
    ): String {
        selectorError(executionId, configurationName)?.let { return it }
        return withDescriptor(executionId, configurationName) { _, descriptor ->
            val console =
                consoleChain(descriptor.executionConsole).firstNotNullOfOrNull { it as? SMTRunnerConsoleView }
                    ?: return@withDescriptor "not_a_test_run: executionId=${descriptor.executionId} has no test results tree."
            testResults(console.resultsViewer.testsRootNode, testName)
        }
    }

    @McpTool
    @McpToolHints(readOnlyHint = McpToolHintValue.TRUE, openWorldHint = McpToolHintValue.FALSE)
    @McpDescription(
        "Wait until one run/debug process in the selected project has fully ended, including a run started by someone else, then return the same summary as get_run_output. Pass exactly one of executionId (from list_running_configurations) or configurationName (the newest run of that configuration at call time; a run started later is not followed), plus projectPath. Returns at once when the run already ended; finishedAt in the header shows when it ended. A test run counts as ended when its process ended and its test tree finished; if the tree is still unfinished 5 s after the process ended, the reply shows finished=false. The last line is wait: done after <n>s, wait: timeout after <n>s (the header then shows state=running) or wait: test tree not finished 5s after process end. If the Run/Debug tab closes during the wait, returns tab_closed with the recorded exit code, times and test counts.",
    )
    suspend fun wait_for_run(
        @McpDescription("Execution ID returned by list_running_configurations.") executionId: Long? = null,
        @McpDescription("Run configuration name; the newest run with this name at call time is waited on.") configurationName: String? =
            null,
        @McpDescription("Maximum wait in seconds, 0 to 1800. 0 checks once and returns at once. Default 300.") timeoutSeconds: Int = 300,
        @McpDescription("Test list detail for test runs: problems (default) lists failed and unfinished tests, all lists every test.")
        tests: TestsDetail = TestsDetail.problems,
        @McpDescription("Return only the last N console lines, counted after grep. 0 returns all. Default 200.") tailLines: Int = 200,
        @McpDescription("Optional Java regex; keep only console lines containing a match.") grep: String? = null,
    ): String {
        selectorError(executionId, configurationName)?.let { return it }
        val filter =
            try {
                grep?.let(::Regex)
            } catch (e: PatternSyntaxException) {
                return "invalid_regex: ${e.description}"
            }
        val (timeout, clamped) = clampTimeout(timeoutSeconds)
        val clampLine = if (clamped) "wait: timeoutSeconds clamped to $timeout\n" else ""
        var target: Long? = null
        val lookup =
            withDescriptor(executionId, configurationName) { _, descriptor ->
                target = descriptor.executionId
                ""
            }
        // Only the id is kept, so a run started later under the same name never replaces the target.
        val id = target ?: return lookup
        val project = coroutineContext.project
        val store = project.service<RunHistoryService>().store
        val startMillis = System.currentTimeMillis()
        val deadlineMillis = startMillis + timeout * 1_000L
        var seenEndedAtMillis: Long? = null
        while (true) {
            val reply =
                withContext(Dispatchers.EDT) {
                    val descriptor =
                        RunContentManager.getInstance(project).allDescriptors.firstOrNull { it.executionId == id }
                            ?: return@withContext tabClosed(id, store.get(id)) + clampLine
                    val now = System.currentTimeMillis()
                    // A descriptor without a handler never changes state, so there is nothing to wait for.
                    val processEnded = descriptor.processHandler?.isProcessTerminated != false
                    if (processEnded && seenEndedAtMillis == null) seenEndedAtMillis = now
                    val root =
                        consoleChain(descriptor.executionConsole)
                            .firstNotNullOfOrNull { it as? SMTRunnerConsoleView }
                            ?.resultsViewer
                            ?.testsRootNode
                    val verdict =
                        waitVerdict(
                            processEnded = processEnded,
                            hasTestTree = root != null,
                            treeFinished = root?.isInProgress == false,
                            processEndedAtMillis = store.get(id)?.finishedAtMillis ?: seenEndedAtMillis,
                            nowMillis = now,
                            graceMillis = WAIT_GRACE_MS,
                        )
                    val elapsed = (now - startMillis) / 1_000
                    val waitLine =
                        when {
                            verdict == WaitVerdict.DONE -> "wait: done after ${elapsed}s"
                            verdict == WaitVerdict.DONE_TREE_UNFINISHED ->
                                "wait: test tree not finished ${WAIT_GRACE_MS / 1_000}s after process end"
                            now >= deadlineMillis -> "wait: timeout after ${elapsed}s"
                            else -> return@withContext null
                        }
                    runOutput(project, descriptor, tailLines, filter, tests).trimEnd('\n') + "\n" + clampLine + waitLine
                }
            if (reply != null) return reply
            delay(minOf(WAIT_POLL_MS, deadlineMillis - System.currentTimeMillis()).coerceAtLeast(0))
        }
    }

    private fun tabClosed(
        id: Long,
        record: RunRecord?,
    ): String =
        buildString {
            appendLine(
                "tab_closed: executionId=$id, state=terminated, exitCode=${record?.exitCode ?: "unknown"}, " +
                    "startedAt=${formatInstant(record?.startedAtMillis)}, finishedAt=${formatInstant(record?.finishedAtMillis)}",
            )
            record?.tests?.let {
                appendLine(
                    "tests: total=${it.total}, passed=${it.passed}, failed=${it.failed}, ignored=${it.ignored}, other=${it.other}, finished=${it.finished}",
                )
            }
        }

    private fun selectorError(
        executionId: Long?,
        configurationName: String?,
    ): String? =
        if ((executionId == null) == (configurationName == null)) {
            "missing_argument: pass exactly one of executionId or configurationName."
        } else {
            null
        }

    private suspend fun withDescriptor(
        executionId: Long?,
        configurationName: String?,
        action: suspend (Project, RunContentDescriptor) -> String,
    ): String {
        val project = coroutineContext.project
        return withContext(Dispatchers.EDT) {
            val descriptors = RunContentManager.getInstance(project).allDescriptors.distinct()
            val matches =
                if (executionId != null) {
                    descriptors.filter { it.executionId == executionId }
                } else {
                    listOfNotNull(descriptors.filter { it.name() == configurationName }.maxByOrNull { it.executionId })
                }
            when {
                matches.isEmpty() -> "not_found: ${executionId?.let {
                    "executionId=$it"
                } ?: "configurationName=$configurationName"}. Its tab may be closed; refresh list_running_configurations."
                matches.size != 1 -> "ambiguous: executionId=$executionId matches multiple processes."
                else -> action(project, matches.single())
            }
        }
    }

    private fun requestStop(handler: ProcessHandler) {
        handler.putUserData(ProcessHandler.TERMINATION_REQUESTED, true)
        // Not a child of the tool call: stop_requested must return before the process exits.
        CoroutineScope(Dispatchers.Default).launch {
            if (!handler.isProcessTerminated) {
                if (handler.detachIsDefault()) handler.detachProcess() else handler.destroyProcess()
            }
        }
    }

    private suspend fun runOutput(
        project: Project,
        descriptor: RunContentDescriptor,
        tailLines: Int,
        filter: Regex?,
        tests: TestsDetail,
    ): String {
        val chain = consoleChain(descriptor.executionConsole).toList()
        val chainNames = chain.joinToString(" > ") { it.javaClass.simpleName }
        // Test wrappers are ConsoleViews too, but only the innermost one owns the editor.
        val console =
            chain.filterIsInstance<ConsoleView>().lastOrNull()
                ?: return "no_text_console: executionId=${descriptor.executionId} uses $chainNames."
        val handler = descriptor.processHandler
        val state =
            when {
                handler == null -> "unknown"
                handler.isProcessTerminated -> "terminated, exitCode=${handler.exitCode}"
                handler.isProcessTerminating -> "stopping"
                else -> "running"
            }
        val record = project.service<RunHistoryService>().store.get(descriptor.executionId)
        val startedAt = formatInstant(record?.startedAtMillis)
        val finishedAt = if (handler != null && !handler.isProcessTerminated) "-" else formatInstant(record?.finishedAtMillis)
        val head =
            buildString {
                appendLine(
                    "executionId=${descriptor.executionId}, name=${descriptor.name()}, state=$state, startedAt=$startedAt, finishedAt=$finishedAt, console=$chainNames",
                )
                chain.firstNotNullOfOrNull { it as? SMTRunnerConsoleView }?.let {
                    val root = it.resultsViewer.testsRootNode
                    append(formatTests(root.toTestNodes(), !root.isInProgress, tests))
                }
            }

        val flushed =
            !console.hasDeferredOutput() ||
                withTimeoutOrNull(FLUSH_TIMEOUT_MS) {
                    val done = CompletableDeferred<Unit>()
                    console.performWhenNoDeferredOutput { done.complete(Unit) }
                    done.await()
                } != null
        // console.component creates the editor of a never-shown tab, so this read stays on the EDT.
        val text =
            DataManager
                .getInstance()
                .getDataContext(console.component)
                .getData(CommonDataKeys.EDITOR)
                ?.document
                ?.text
                ?: return "no_text_console: executionId=${descriptor.executionId} uses $chainNames."
        val lines = text.lines()
        val matched = filter?.let { regex -> lines.filter { regex.containsMatchIn(it) } } ?: lines
        val shown = if (tailLines > 0) matched.takeLast(tailLines) else matched
        val body =
            buildString {
                append("--- console: ${shown.size} of ${lines.size} lines")
                filter?.let { append(", grep=/${it.pattern}/") }
                appendLine()
                if (!flushed) appendLine("console: pending output not flushed")
                shown.forEach { line ->
                    if (line.length <= MAX_LINE_CHARS) {
                        appendLine(line)
                    } else {
                        appendLine("${line.take(MAX_LINE_CHARS)}… [+${line.length - MAX_LINE_CHARS} chars]")
                    }
                }
            }
        val cappedHead = capTestList(head)
        val budget = (MAX_OUTPUT_CHARS - cappedHead.length).coerceAtLeast(0)
        return if (body.length <= budget) {
            cappedHead + body
        } else {
            cappedHead + "[truncated: first ${body.length - budget} chars dropped]\n" + body.takeLast(budget)
        }
    }

    // The marker length is reserved with the largest possible count, so the result never passes MAX_OUTPUT_CHARS.
    private fun capTestList(head: String): String {
        if (head.length <= MAX_OUTPUT_CHARS) return head
        val reserve = "[truncated: test list, ${head.length} chars dropped]\n".length
        val kept = head.substring(0, head.lastIndexOf('\n', MAX_OUTPUT_CHARS - reserve - 1) + 1)
        return kept + "[truncated: test list, ${head.length - kept.length} chars dropped]\n"
    }

    // Test consoles and decorated consoles nest the console that holds the text.
    private fun consoleChain(console: ExecutionConsole?): Sequence<ExecutionConsole> =
        generateSequence(console) {
            when (it) {
                is BaseTestsOutputConsoleView -> it.console
                is ConsoleViewWithDelegate -> it.delegate
                else -> null
            }
        }

    private fun testResults(
        root: SMTestProxy,
        testName: String,
    ): String {
        val leaves = root.testLeaves()
        val exact = leaves.filter { it.qualifiedName() == testName }
        val matches = exact.ifEmpty { leaves.filter { it.name == testName } }
        val test =
            when (matches.size) {
                0 -> {
                    val hints =
                        leaves
                            .filter { it.testStatus() == TestStatus.FAILED || it.testStatus() == TestStatus.UNFINISHED }
                            .take(MAX_TEST_HINTS)
                            .joinToString { it.qualifiedName() }
                    return "test_not_found: $testName" + if (hints.isEmpty()) "" else ". Failed or unfinished tests: $hints"
                }
                1 -> matches.single()
                else -> return "ambiguous_test: $testName matches ${matches.joinToString { it.qualifiedName() }}."
            }
        val ownOutput = StringBuilder()
        test.printOwnPrintablesOn(
            object : Printer {
                override fun print(
                    text: String,
                    contentType: ConsoleViewContentType,
                ) {
                    ownOutput.append(text)
                }

                override fun onNewAvailable(printable: Printable) = printable.printOn(this)

                override fun printHyperlink(
                    text: String,
                    info: HyperlinkInfo?,
                ) {
                    ownOutput.append(text)
                }

                override fun mark() = Unit
            },
        )
        val head =
            buildString {
                appendLine("${test.qualifiedName()}: status=${test.testStatus()}, duration=${test.duration?.let { "${it}ms" } ?: "-"}")
                appendSection("message", test.errorMessage)
                appendSection("stacktrace", test.stacktrace)
            }
        val output = ownOutput.toString().trimEnd()
        val budget = (MAX_OUTPUT_CHARS - head.length).coerceAtLeast(0)
        return when {
            output.isBlank() -> head + "output: -\n"
            output.length <= budget -> head + "output:\n$output\n"
            else -> head + "output:\n[truncated: first ${output.length - budget} chars dropped]\n" + output.takeLast(budget) + "\n"
        }
    }

    private fun StringBuilder.appendSection(
        label: String,
        text: String?,
    ) {
        if (text.isNullOrBlank()) {
            appendLine("$label: -")
        } else {
            appendLine("$label:")
            appendLine(text.trimEnd())
        }
    }

    private fun RunContentDescriptor.name(): String = displayName
}
