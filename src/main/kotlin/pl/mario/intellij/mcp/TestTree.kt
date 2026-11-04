package pl.mario.intellij.mcp

enum class TestStatus { PASSED, FAILED, IGNORED, UNFINISHED }

@Suppress("ktlint:standard:enum-entry-name-case")
enum class TestsDetail { problems, all }

data class TestNode(
    val suite: String?,
    val name: String,
    val status: TestStatus,
    val message: String?,
    val durationMs: Long?,
)

private const val MAX_TEST_LINES = 1000
private const val ROOT_SUITE = "(root)"

internal const val MAX_LINE_CHARS = 2_000

fun countTests(
    nodes: List<TestNode>,
    finished: Boolean,
): TestCounts =
    TestCounts(
        total = nodes.size,
        passed = nodes.count { it.status == TestStatus.PASSED },
        failed = nodes.count { it.status == TestStatus.FAILED },
        ignored = nodes.count { it.status == TestStatus.IGNORED },
        other = nodes.count { it.status == TestStatus.UNFINISHED },
        finished = finished,
    )

fun formatTests(
    nodes: List<TestNode>,
    finished: Boolean,
    detail: TestsDetail,
): String {
    val counts = countTests(nodes, finished)
    val listed =
        when (detail) {
            TestsDetail.problems -> nodes.filter { it.status == TestStatus.FAILED || it.status == TestStatus.UNFINISHED }
            TestsDetail.all -> nodes
        }
    val allBySuite = nodes.groupBy { it.suite }
    var remaining = MAX_TEST_LINES
    var cut = 0
    return buildString {
        appendLine(
            "tests: total=${counts.total}, passed=${counts.passed}, failed=${counts.failed}, ignored=${counts.ignored}, other=${counts.other}, finished=${counts.finished}",
        )
        for ((suite, tests) in listed.groupBy { it.suite }) {
            val shown = tests.take(remaining)
            cut += tests.size - shown.size
            remaining -= shown.size
            if (shown.isEmpty()) continue
            val inSuite = allBySuite.getValue(suite)
            appendLine(
                "suite ${suite ?: ROOT_SUITE}: ${inSuite.size} tests, " +
                    "failed=${inSuite.count { it.status == TestStatus.FAILED }}, " +
                    "unfinished=${inSuite.count { it.status == TestStatus.UNFINISHED }}",
            )
            shown.forEach { appendLine("  ${it.line()}") }
        }
        if (cut > 0) appendLine("... and $cut more")
    }
}

private fun TestNode.line(): String {
    val label = if (suite == null) name else "$suite.$name"
    val firstLine =
        message
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
    val shown =
        if (firstLine.length <= MAX_LINE_CHARS) {
            firstLine
        } else {
            "${firstLine.take(MAX_LINE_CHARS)}… [+${firstLine.length - MAX_LINE_CHARS} chars]"
        }
    return if (shown.isEmpty()) "$status $label" else "$status $label — $shown"
}
