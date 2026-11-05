package pl.mario.intellij.mcp

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
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
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            RunContentManager
                .getInstance(project)
                .allDescriptors
                .firstOrNull { it.processHandler === handler }
                ?.let { project.service<RunHistoryService>().store.testsFinished(it.executionId, counts) }
        }, ModalityState.any())
    }
}

internal fun countTests(root: SMTestProxy): TestCounts = countTests(root.toTestNodes(), !root.isInProgress)

internal fun SMTestProxy.testLeaves(): List<SMTestProxy> = allTests.filter { it.isLeaf && !it.isSuite }

internal fun SMTestProxy.toTestNodes(): List<TestNode> = testLeaves().map { it.toTestNode() }

internal fun SMTestProxy.testStatus(): TestStatus =
    when {
        // An ignored test also reports isDefect, so it is classified before the failure check.
        isIgnored -> TestStatus.IGNORED
        isDefect -> TestStatus.FAILED
        isPassed -> TestStatus.PASSED
        else -> TestStatus.UNFINISHED
    }

internal fun SMTestProxy.suiteName(): String? = parent?.takeUnless { it is SMTestProxy.SMRootTestProxy }?.name

internal fun SMTestProxy.qualifiedName(): String = suiteName()?.let { "$it.$name" } ?: name

private fun SMTestProxy.toTestNode() = TestNode(suiteName(), name, testStatus(), errorMessage, duration)
