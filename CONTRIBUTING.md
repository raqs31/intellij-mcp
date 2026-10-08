# Contributing

## IntelliJ plugin practices

Based on the [IntelliJ Platform SDK docs](https://plugins.jetbrains.com/docs/intellij/welcome.html).

- **Threading:** run UI work and project-model writes on the EDT (`withContext(Dispatchers.EDT)`), read PSI and
  the project model inside `readAction {}`, and keep I/O and long work off the EDT.
- **Public API only:** avoid `*Impl` classes and `@ApiStatus.Internal` / `@ApiStatus.Experimental` symbols where a
  public alternative exists; `./gradlew verifyPlugin` reports them.
- **Platform libraries:** the IDE provides the Kotlin stdlib and kotlinx.coroutines. Never bundle them
  (`kotlin.stdlib.default.dependency = false`).
- **Dynamic plugin:** register everything through extension points in `plugin.xml` and keep no static mutable
  state, so the plugin installs and unloads without an IDE restart.
- **Compatibility:** `sinceBuild` in `build.gradle.kts` and `since-build` in `plugin.xml` must match the
  `platformVersion` you compile against.
- **Agent-facing tools:** return diagnostic status strings (`not_running`, `sync_triggered`) instead of throwing.
  Resolve the project with `coroutineContext.project`.
- **Try it in a sandbox first:** `./gradlew runIde` launches a throwaway IDE with the plugin loaded. Install the zip in
  your main IDE only after that.

Before pushing: `./gradlew spotlessCheck verifyPluginConfiguration buildPlugin`.

## Commit style

Commit messages follow [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).

```
<type>(<scope>): <description>

[optional body — the why, wrapped at 100 chars]

[optional footers — Refs:, Co-authored-by:, BREAKING CHANGE:]
```

Header: imperative mood, lower case, no trailing period, ≤ 72 characters. One logical change per commit.

### Types

| Type       | Use for                                                          |
|------------|------------------------------------------------------------------|
| `feat`     | New user-visible capability (MCP tool, tool parameter)           |
| `fix`      | Bug fix                                                          |
| `perf`     | Performance change with no behavioural difference                |
| `refactor` | Neither a fix nor a feature                                      |
| `test`     | Tests only                                                       |
| `docs`     | Documentation only                                               |
| `build`    | Gradle, dependencies, IntelliJ platform target, version bumps    |
| `ci`       | Pipeline configuration                                           |
| `chore`    | Anything else that touches no production behaviour               |
| `revert`   | Reverts a previous commit (body: `Reverts <sha>`)                |
| `ai`       | Changes related to ai configuration or memories                  |

### Scopes

The toolset the change belongs to — `run`, `maven` — or `plugin` for `plugin.xml`. Omit when the change is
genuinely cross-cutting.

### Breaking changes

`!` after the type/scope **and** a `BREAKING CHANGE:` footer describing the migration. Renaming or removing an
MCP tool or one of its parameters is breaking — agents call tools by name.

```
feat(run)!: rename stop_run_configuration to stop_execution

BREAKING CHANGE: stop_run_configuration is gone. Call stop_execution with the same executionId.
```

### Examples

```
feat(maven): add list_maven_modules tool
fix(run): skip descriptors whose process already terminated
build: target IntelliJ 2026.2 with the bundled MCP server
docs: describe installing the plugin from disk
```
