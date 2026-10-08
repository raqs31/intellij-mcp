package pl.mario.intellij.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHintValue
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.project
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenControlToolset : McpToolset {
    @McpTool
    @McpToolHints(
        readOnlyHint = McpToolHintValue.FALSE,
        idempotentHint = McpToolHintValue.TRUE,
        openWorldHint = McpToolHintValue.FALSE,
    )
    @McpDescription(
        "Forces IntelliJ to re-import, sync, and resolve all Maven projects and dependencies in the workspace. Pass projectPath to select the project.",
    )
    suspend fun reload_maven_projects(): String {
        val project = currentCoroutineContext().project
        return withContext(Dispatchers.EDT) {
            val manager = MavenProjectsManager.getInstance(project)
            if (!manager.isMavenizedProject) {
                return@withContext "project_not_maven: Workspace does not contain an active Maven configuration."
            }

            manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
            val moduleCount = manager.projects.size
            "sync_triggered: Reload and dependency resolution scheduled for $moduleCount Maven module(s)."
        }
    }

    @McpTool
    @McpToolHints(
        readOnlyHint = McpToolHintValue.TRUE,
        openWorldHint = McpToolHintValue.FALSE,
    )
    @McpDescription(
        "Inspects the status, coordinates, and modules of Maven projects loaded in IntelliJ. Pass projectPath to select the project.",
    )
    suspend fun list_maven_modules(): String {
        val project = currentCoroutineContext().project
        return withContext(Dispatchers.EDT) {
            val manager = MavenProjectsManager.getInstance(project)
            if (!manager.isMavenizedProject) {
                return@withContext "No active Maven projects found in workspace."
            }

            val projects = manager.projects
            if (projects.isEmpty()) {
                return@withContext "Maven project manager is active but contains no imported modules."
            }

            projects.joinToString("\n") { mavenProj ->
                val mavenId = mavenProj.mavenId
                val coords = "${mavenId.groupId}:${mavenId.artifactId}:${mavenId.version}"
                "name=${mavenProj.displayName}, coordinates=$coords, packaging=${mavenProj.packaging}, directory=${mavenProj.directory}"
            }
        }
    }
}
