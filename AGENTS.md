# Repository Guidelines

## Overview
`intellij-mcp-stop` is an IntelliJ IDEA plugin extending JetBrains' built-in Model Context Protocol (MCP) server (`com.intellij.mcpServer`). It exposes native tools to autonomous coding agents for:
1. **Execution Lifecycle Control:** Listing running configurations and gracefully terminating active processes.
2. **Maven Build & Project Model Automation:** Triggering full Maven workspace sync/reload and introspecting Maven module definitions.

## Project Structure & Architecture
- **Root Package:** `pl.mario.intellij.mcp`
- **Toolsets (flat under root package):**
  - `pl.mario.intellij.mcp.RunControlToolset`: Implements `list_running_configurations` and `stop_run_configuration` using `ExecutionManager` and `ExecutionManagerImpl.stopProcess`.
  - `pl.mario.intellij.mcp.MavenControlToolset`: Implements `reload_maven_projects` and `list_maven_modules` using `MavenProjectsManager`.
- **Plugin Descriptor:** `src/main/resources/META-INF/plugin.xml` registers toolsets under extension point `com.intellij.mcpServer.mcpToolset`.

## Build and Quality Commands
- **Build Plugin Archive:** `./gradlew buildPlugin` (packages distribution to `build/distributions/`)
- **Run Sandbox IDE:** `./gradlew runIde` (launches sandbox IDE with loaded plugin)
- **Check Formatting:** `./gradlew spotlessCheck`
- **Apply Formatting:** `./gradlew spotlessApply`
- **Verify Plugin Compatibility:** `./gradlew verifyPlugin`

## Tool Guidelines & Constraints
- **Concurrency & Threading:** All IDE mutations and UI thread operations must be wrapped in `withContext(Dispatchers.EDT)`.
- **Project Scoping:** Always resolve project context via `coroutineContext.project`. MCP client calls should pass `projectPath` to target the right workspace.
- **Error Handling & Idempotency:**
  - Return clear, diagnostic status strings (e.g., `not_running`, `already_stopping`, `project_not_maven`, `sync_triggered`).
  - Do not throw unhandled exceptions out of toolset methods; return readable error messages to avoid crashing agent conversations.
