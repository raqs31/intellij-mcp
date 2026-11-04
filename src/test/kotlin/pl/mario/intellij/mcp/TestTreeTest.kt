package pl.mario.intellij.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestTreeTest {
    private fun node(
        name: String,
        status: TestStatus,
        suite: String? = "S",
        message: String? = null,
    ) = TestNode(suite, name, status, message, null)

    private val mixed =
        listOf(
            node("ok", TestStatus.PASSED),
            node("bad", TestStatus.FAILED, message = "boom"),
            node("skip", TestStatus.IGNORED),
            node("hang", TestStatus.UNFINISHED),
        )

    @Test
    fun problemsHidesPassedAndIgnored() {
        val out = formatTests(mixed, true, TestsDetail.problems)

        assertTrue(out.contains("  FAILED S.bad — boom"))
        assertTrue(out.contains("  UNFINISHED S.hang"))
        assertFalse(out.contains("S.ok"))
        assertFalse(out.contains("S.skip"))
    }

    @Test
    fun allListsEveryTest() {
        val out = formatTests(mixed, true, TestsDetail.all)

        assertTrue(out.contains("  PASSED S.ok"))
        assertTrue(out.contains("  FAILED S.bad — boom"))
        assertTrue(out.contains("  IGNORED S.skip"))
        assertTrue(out.contains("  UNFINISHED S.hang"))
    }

    @Test
    fun suiteLinesCountAllTestsOfTheSuiteAndPrecedeTheirTests() {
        val nodes =
            listOf(
                node("a", TestStatus.PASSED, suite = "A"),
                node("b", TestStatus.FAILED, suite = "B"),
                node("c", TestStatus.FAILED, suite = "A"),
                node("d", TestStatus.UNFINISHED, suite = "A"),
                node("e", TestStatus.PASSED, suite = null),
                node("f", TestStatus.FAILED, suite = null),
            )

        val lines = formatTests(nodes, true, TestsDetail.problems).lines()

        assertEquals(
            listOf(
                "suite B: 1 tests, failed=1, unfinished=0",
                "  FAILED B.b",
                "suite A: 3 tests, failed=1, unfinished=1",
                "  FAILED A.c",
                "  UNFINISHED A.d",
                "suite (root): 2 tests, failed=1, unfinished=0",
                "  FAILED f",
            ),
            lines.drop(1).filter { it.isNotEmpty() },
        )
    }

    @Test
    fun capsListedTestsAtThousandWithMoreLine() {
        val nodes = (1..1001).map { node("t$it", TestStatus.FAILED) }

        val lines = formatTests(nodes, true, TestsDetail.problems).lines()

        assertEquals(1000, lines.count { it.startsWith("  FAILED ") })
        assertTrue(lines.contains("... and 1 more"))
    }

    @Test
    fun countsLineKeepsItsShape() {
        val out = formatTests(mixed, false, TestsDetail.problems)

        assertEquals(
            "tests: total=4, passed=1, failed=1, ignored=1, other=1, finished=false",
            out.lines().first(),
        )
    }

    @Test
    fun messageIsReducedToItsFirstLine() {
        val nodes = listOf(node("bad", TestStatus.FAILED, message = "first\nsecond\nthird"))

        val out = formatTests(nodes, true, TestsDetail.problems)

        assertTrue(out.contains("  FAILED S.bad — first\n"))
        assertFalse(out.contains("second"))
    }

    @Test
    fun longFirstMessageLineIsShortenedWithMarker() {
        val message = "x".repeat(MAX_LINE_CHARS + 5)
        val nodes = listOf(node("bad", TestStatus.FAILED, message = message))

        val out = formatTests(nodes, true, TestsDetail.problems)

        assertTrue(out.contains("  FAILED S.bad — ${"x".repeat(MAX_LINE_CHARS)}… [+5 chars]\n"))
    }

    @Test
    fun firstMessageLineOfExactCapStaysWhole() {
        val message = "x".repeat(MAX_LINE_CHARS)
        val nodes = listOf(node("bad", TestStatus.FAILED, message = message))

        val out = formatTests(nodes, true, TestsDetail.problems)

        assertTrue(out.contains("  FAILED S.bad — $message\n"))
        assertFalse(out.contains("chars]"))
    }
}
