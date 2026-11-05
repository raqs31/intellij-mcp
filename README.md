# intellij-mcp-addons

An IntelliJ IDEA plugin extending JetBrains' built-in Model Context Protocol (MCP) server with execution management and Maven automation tools for coding agents:

- `list_running_configurations`: List live run/debug executions in the selected project (including execution IDs, configuration names, and termination states).
- `stop_run_configuration`: Request standard IDE termination for a specific execution using its `executionId`.
- `get_run_output`: Read the console output of a running or finished execution (by `executionId` or newest run of a `configurationName`), with `tailLines`/`grep` filtering and, for test runs, pass/fail/ignored counts and a test list (`tests=problems` or `tests=all`). Use `get_test_results` for one test's full message and stack trace.
- `wait_for_run`: Wait until a run has ended, up to `timeoutSeconds`, then return the same summary as `get_run_output`.
- `get_test_results`: Read one test's status, duration, full error message, full stack trace and its own console output.
- `list_run_history`: List the last finished runs of the project, newest first, with exit code, times and test counts. History is in memory, keeps the last 50 runs and is cleared on IDE restart.
- `reload_maven_projects`: Force IntelliJ to re-import, sync, and resolve all Maven projects and dependencies in the workspace.
- `list_maven_modules`: Inspect the status, coordinates, packaging, and directories of Maven projects loaded in IntelliJ.

---

## Features

- **Root Package:** `pl.mario.intellij.mcp`
- **Native Integration:** Hooks directly into `com.intellij.mcpServer.mcpToolset`.
- **Graceful Process Shutdown:** Requests the run process's normal stop through its `ProcessHandler` (detach or destroy) so child processes, sockets, and resources are closed cleanly.
- **Maven Lifecycle Automation:** Directly invokes `MavenProjectsManager` to trigger dependency downloads, module imports, and model updates.
- **Project-Scoped:** All lookups and actions are scoped safely to the caller's active project context (`projectPath`).

---

## Prerequisites

- JDK 21+
- IntelliJ IDEA 2024.2+ (with bundled MCP Server and Maven plugins enabled)

---

## Build & Development

This project is built using Gradle with the official **IntelliJ Platform Gradle Plugin 2.x**.

### Build Plugin Archive
To build and package the distributable plugin ZIP:
```bash
./gradlew buildPlugin
```
The output distribution ZIP will be located in:
`build/distributions/intellij-mcp-addons-0.4.0.zip`

### Run in Sandbox IDE
To launch an isolated IntelliJ IDEA sandbox with the plugin loaded:
```bash
./gradlew runIde
```

### Verification & Formatting
To check code formatting with Spotless:
```bash
./gradlew spotlessCheck
```

To auto-format Kotlin files and build scripts:
```bash
./gradlew spotlessApply
```

To verify plugin compatibility:
```bash
./gradlew verifyPlugin
```

---

## Installation

1. In IntelliJ IDEA, open **Settings** (or **Preferences**) → **Plugins**.
2. Click the gear icon (⚙️) and select **Install Plugin from Disk...**.
3. Select the built artifact `build/distributions/*.zip`.
4. Restart the IDE when prompted.
5. Go to **Settings** → **Tools** → **MCP Server** and ensure all tools (`list_running_configurations`, `stop_run_configuration`, `get_run_output`, `wait_for_run`, `get_test_results`, `list_run_history`, `reload_maven_projects`, `list_maven_modules`) are active under **Exposed Tools**.

---

## Usage Example

Call from your MCP client:

1. **List active processes:**
   ```json
   {
     "name": "list_running_configurations",
     "arguments": {
       "projectPath": "/path/to/your/project"
     }
   }
   ```

2. **Stop a target process:**
   ```json
   {
     "name": "stop_run_configuration",
     "arguments": {
       "executionId": 123456789,
       "projectPath": "/path/to/your/project"
     }
   }
   ```

3. **Read the output of a run (last 50 lines matching a regex):**
   ```json
   {
     "name": "get_run_output",
     "arguments": {
       "configurationName": "MySpec",
       "tailLines": 50,
       "grep": "ERROR|FAILED",
       "projectPath": "/path/to/your/project"
     }
   }
   ```

4. **Wait up to 300 seconds for a run to end, then read its summary:**
   ```json
   {
     "name": "wait_for_run",
     "arguments": {
       "configurationName": "MySpec",
       "timeoutSeconds": 300,
       "projectPath": "/path/to/your/project"
     }
   }
   ```

5. **Reload / Sync Maven projects:**
   ```json
   {
     "name": "reload_maven_projects",
     "arguments": {
       "projectPath": "/path/to/your/project"
     }
   }
   ```

6. **List Maven modules:**
   ```json
   {
     "name": "list_maven_modules",
     "arguments": {
       "projectPath": "/path/to/your/project"
     }
   }
   ```

---

## License

Apache 2.0 (or MIT) - see LICENSE for details.
