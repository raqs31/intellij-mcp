package local.mcpstop

import com.intellij.execution.ExecutionManager
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.annotations.McpToolHintValue
import com.intellij.mcpserver.project
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class RunControlToolset : McpToolset {
    @McpTool
    @McpToolHints(readOnlyHint = McpToolHintValue.TRUE, openWorldHint = McpToolHintValue.FALSE)
    @McpDescription("List live IDE run/debug processes in the selected project. Returns execution IDs, names and states. Pass projectPath to select the project.")
    suspend fun list_running_configurations(): String {
        val project = coroutineContext.project
        return withContext(Dispatchers.EDT) {
            val descriptors = ExecutionManager.getInstance(project).getRunningDescriptors { true }
                .filter { it.processHandler?.isProcessTerminated == false }
            if (descriptors.isEmpty()) "No running configurations."
            else descriptors.joinToString("\n") {
                val state = if (it.processHandler?.isProcessTerminating == true) "stopping" else "running"
                "executionId=${it.executionId}, name=${it.runConfigurationName ?: it.displayName}, state=$state"
            }
        }
    }

    @McpTool
    @McpToolHints(readOnlyHint = McpToolHintValue.FALSE, destructiveHint = McpToolHintValue.TRUE,
        idempotentHint = McpToolHintValue.TRUE, openWorldHint = McpToolHintValue.FALSE)
    @McpDescription("Request IntelliJ's normal Stop action for exactly one run/debug process in the selected project. Use an executionId from list_running_configurations and pass projectPath. Returns immediately; stop_requested does not mean the process has exited. Repeated calls while terminating do not force-kill it.")
    suspend fun stop_run_configuration(
        @McpDescription("Execution ID returned by list_running_configurations.") executionId: Long,
    ): String {
        val project = coroutineContext.project
        return withContext(Dispatchers.EDT) {
            val matches = ExecutionManager.getInstance(project).getRunningDescriptors { true }
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
}
