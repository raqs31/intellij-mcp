package pl.mario.intellij.mcp

import com.intellij.execution.ExecutionManager
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView
import com.intellij.execution.ui.ConsoleViewWithDelegate
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHintValue
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.project
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.PatternSyntaxException
import kotlin.coroutines.coroutineContext

private const val MAX_OUTPUT_CHARS = 100_000
private const val MAX_FAILED_TESTS = 20
private const val MAX_STACK_LINES = 15
private const val MAX_LINE_CHARS = 2_000

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
                ExecutionManager
                    .getInstance(project)
                    .getRunningDescriptors { true }
                    .filter { it.processHandler?.isProcessTerminated == false }
            if (descriptors.isEmpty()) {
                "No running configurations."
            } else {
                descriptors.joinToString("\n") {
                    val state = if (it.processHandler?.isProcessTerminating == true) "stopping" else "running"
                    "executionId=${it.executionId}, name=${it.runConfigurationName ?: it.displayName}, state=$state"
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
                ExecutionManager
                    .getInstance(project)
                    .getRunningDescriptors { true }
                    .filter { it.executionId == executionId && it.processHandler?.isProcessTerminated == false }
            when {
                matches.isEmpty() -> "not_running: executionId=$executionId. Refresh list_running_configurations."
                matches.size != 1 -> "ambiguous: executionId=$executionId matches multiple processes; no process stopped."
                matches.single().processHandler?.isProcessTerminating == true -> "already_stopping: executionId=$executionId"
                else -> {
                    ExecutionManagerImpl.stopProcess(matches.single())
                    "stop_requested: executionId=$executionId"
                }
            }
        }
    }

    @McpTool
    @McpToolHints(readOnlyHint = McpToolHintValue.TRUE, openWorldHint = McpToolHintValue.FALSE)
    @McpDescription(
        "Read the console output of one run/debug process in the selected project, running or finished (a finished run stays readable until its Run/Debug tab is closed). Pass exactly one of executionId (from list_running_configurations) or configurationName (reads the newest run of that configuration), plus projectPath. For test runs it adds pass/fail/ignored counts and each failed test's message and stack trace from the IDE's test results tree, which is reliable even when exitCode is 0. The console holds only IntelliJ's cycle buffer (1 MB by default), so the head of a long log may be gone.",
    )
    suspend fun get_run_output(
        @McpDescription("Execution ID returned by list_running_configurations.") executionId: Long? = null,
        @McpDescription("Run configuration name; the newest run with this name is read.") configurationName: String? = null,
        @McpDescription("Return only the last N console lines, counted after grep. 0 returns all. Default 200.") tailLines: Int = 200,
        @McpDescription("Optional Java regex; keep only console lines containing a match.") grep: String? = null,
    ): String {
        if ((executionId == null) == (configurationName == null)) {
            return "missing_argument: pass exactly one of executionId or configurationName."
        }
        val filter =
            try {
                grep?.let(::Regex)
            } catch (e: PatternSyntaxException) {
                return "invalid_regex: ${e.description}"
            }
        val project = coroutineContext.project
        return withContext(Dispatchers.EDT) {
            val descriptors =
                (
                    RunContentManager.getInstance(project).allDescriptors +
                        ExecutionManager.getInstance(project).getRunningDescriptors { true }
                ).distinct()
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
                else -> runOutput(matches.single(), tailLines, filter)
            }
        }
    }

    private fun runOutput(
        descriptor: RunContentDescriptor,
        tailLines: Int,
        filter: Regex?,
    ): String {
        val chain = consoleChain(descriptor.executionConsole).toList()
        val chainNames = chain.joinToString(" > ") { it.javaClass.simpleName }
        val console =
            chain.firstNotNullOfOrNull { it as? ConsoleViewImpl }
                ?: return "no_text_console: executionId=${descriptor.executionId} uses $chainNames."
        val handler = descriptor.processHandler
        val state =
            when {
                handler == null -> "unknown"
                handler.isProcessTerminated -> "terminated, exitCode=${handler.exitCode}"
                handler.isProcessTerminating -> "stopping"
                else -> "running"
            }
        val head =
            buildString {
                appendLine("executionId=${descriptor.executionId}, name=${descriptor.name()}, state=$state, console=$chainNames")
                chain.firstNotNullOfOrNull { it as? SMTRunnerConsoleView }?.let { append(testSummary(it)) }
            }

        console.flushDeferredText()
        val lines = console.text.lines()
        val matched = filter?.let { regex -> lines.filter { regex.containsMatchIn(it) } } ?: lines
        val shown = if (tailLines > 0) matched.takeLast(tailLines) else matched
        val body =
            buildString {
                append("--- console: ${shown.size} of ${lines.size} lines")
                filter?.let { append(", grep=/${it.pattern}/") }
                appendLine()
                shown.forEach { line ->
                    if (line.length <= MAX_LINE_CHARS) {
                        appendLine(line)
                    } else {
                        appendLine("${line.take(MAX_LINE_CHARS)}… [+${line.length - MAX_LINE_CHARS} chars]")
                    }
                }
            }
        val budget = (MAX_OUTPUT_CHARS - head.length).coerceAtLeast(0)
        return if (body.length <= budget) {
            head + body
        } else {
            head + "[truncated: first ${body.length - budget} chars dropped]\n" + body.takeLast(budget)
        }
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

    private fun testSummary(console: SMTRunnerConsoleView): String {
        val root = console.resultsViewer.testsRootNode
        val tests = root.allTests.filter { it.isLeaf && !it.isSuite }
        // An ignored test also reports isDefect, so it is classified before the failure check.
        val ignored = tests.filter { it.isIgnored }
        val failed = tests.filter { !it.isIgnored && it.isDefect }
        val passed = tests.count { !it.isIgnored && !it.isDefect && it.isPassed }
        val other = tests.size - ignored.size - failed.size - passed
        return buildString {
            appendLine(
                "tests: total=${tests.size}, passed=$passed, failed=${failed.size}, ignored=${ignored.size}, other=$other, finished=${!root.isInProgress}",
            )
            failed.take(MAX_FAILED_TESTS).forEach { test ->
                appendLine("failed: ${test.qualifiedName()} — ${test.errorMessage?.lineSequence()?.firstOrNull().orEmpty()}")
                test.stacktrace
                    ?.lineSequence()
                    ?.take(MAX_STACK_LINES)
                    ?.forEach { appendLine("  $it") }
            }
            if (failed.size > MAX_FAILED_TESTS) appendLine("... and ${failed.size - MAX_FAILED_TESTS} more failed")
        }
    }

    private fun SMTestProxy.qualifiedName(): String {
        val suite = parent?.takeUnless { it is SMTestProxy.SMRootTestProxy }
        return if (suite == null) name else "${suite.name}.$name"
    }

    private fun RunContentDescriptor.name(): String = runConfigurationName ?: displayName
}
