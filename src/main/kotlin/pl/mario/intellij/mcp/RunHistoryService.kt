package pl.mario.intellij.mcp

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class RunHistoryService {
    val store = RunStore()
}

class RunHistoryExecutionListener(
    private val project: Project,
) : ExecutionListener {
    override fun processStarted(
        executorId: String,
        env: ExecutionEnvironment,
        handler: ProcessHandler,
    ) {
        project.service<RunHistoryService>().store.started(env.executionId, env.runProfile.name, System.currentTimeMillis())
    }

    override fun processTerminated(
        executorId: String,
        env: ExecutionEnvironment,
        handler: ProcessHandler,
        exitCode: Int,
    ) {
        project.service<RunHistoryService>().store.terminated(env.executionId, exitCode, System.currentTimeMillis())
    }
}

class RunHistoryTestListener(
    private val project: Project,
) : SMTRunnerEventsAdapter() {
    override fun onTestingFinished(testsRoot: SMTestProxy.SMRootTestProxy) {
        val handler = testsRoot.handler ?: return
        val counts = countTests(testsRoot)
        // root.executionId is often 0, so the run is found through its process handler; descriptors are EDT-only.
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            RunContentManager
                .getInstance(project)
                .allDescriptors
                .firstOrNull { it.processHandler === handler }
                ?.let { project.service<RunHistoryService>().store.testsFinished(it.executionId, counts) }
        }
    }
}

internal fun countTests(root: SMTestProxy): TestCounts {
    val tests = root.allTests.filter { it.isLeaf && !it.isSuite }
    // An ignored test also reports isDefect, so it is classified before the failure check.
    val ignored = tests.count { it.isIgnored }
    val failed = tests.count { !it.isIgnored && it.isDefect }
    val passed = tests.count { !it.isIgnored && !it.isDefect && it.isPassed }
    return TestCounts(
        total = tests.size,
        passed = passed,
        failed = failed,
        ignored = ignored,
        other = tests.size - ignored - failed - passed,
        finished = !root.isInProgress,
    )
}
