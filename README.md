# MCP Run Control

An IntelliJ IDEA plugin extending JetBrains' built-in Model Context Protocol (MCP) server with live execution management tools:

- `list_running_configurations`: List live run/debug executions in the selected project (including execution IDs, configuration names, and termination states).
- `stop_run_configuration`: Request standard IDE termination for a specific execution using its `executionId`.

---

## Features

- **Native Integration:** Hooks directly into `com.intellij.mcpServer.mcpToolset`.
- **Graceful Process Shutdown:** Uses `ExecutionManagerImpl.stopProcess` to ensure child processes, sockets, and resources are closed cleanly.
- **Project-Scoped:** Execution lookups and termination requests are scoped safely to the caller's active project context (`projectPath`).

---

## Prerequisites

- JDK 21+
- IntelliJ IDEA 2024.2+ (with bundled MCP Server plugin enabled)

---

## Build & Development

This project is built using Gradle with the official **IntelliJ Platform Gradle Plugin 2.x**.

### Build Plugin Archive
To build and package the distributable plugin ZIP:
```bash
./gradlew buildPlugin
```
The output distribution ZIP will be located in:
`build/distributions/mcp-run-control-0.1.0.zip`

### Run in Sandbox IDE
To launch an isolated IntelliJ IDEA sandbox with the plugin loaded:
```bash
./gradlew runIde
```

### Verification
To verify plugin compatibility:
```bash
./gradlew verifyPlugin
```

---

## Installation

1. In IntelliJ IDEA, open **Settings** (or **Preferences**) → **Plugins**.
2. Click the gear icon (⚙️) and select **Install Plugin from Disk...**.
3. Select the built artifact `build/distributions/mcp-run-control-0.1.0.zip`.
4. Restart the IDE when prompted.
5. Go to **Settings** → **Tools** → **MCP Server** and ensure `list_running_configurations` and `stop_run_configuration` are active under **Exposed Tools**.

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

---

## License

Apache 2.0 (or MIT) - see LICENSE for details.
