package pl.mario.intellij.mcp

import org.junit.Assert.assertEquals
import org.junit.Test

class RunWaitTest {
    @Test
    fun runningProcessWaits() {
        assertEquals(WaitVerdict.WAIT, waitVerdict(false, false, false, null, 1_000))
    }

    @Test
    fun endedWithoutTreeIsDone() {
        assertEquals(WaitVerdict.DONE, waitVerdict(true, false, false, 1_000, 1_000))
    }

    @Test
    fun endedWithoutTreeEndNotRecordedInsideGraceWaits() {
        assertEquals(WaitVerdict.WAIT, waitVerdict(true, false, false, 1_000, 5_999, endRecorded = false))
    }

    @Test
    fun endedWithoutTreeEndNotRecordedAfterGraceIsDone() {
        assertEquals(WaitVerdict.DONE, waitVerdict(true, false, false, 1_000, 6_000, endRecorded = false))
    }

    @Test
    fun endedWithUnfinishedTreeInsideGraceWaits() {
        assertEquals(WaitVerdict.WAIT, waitVerdict(true, true, false, 1_000, 5_999))
    }

    @Test
    fun endedWithUnfinishedTreeAfterGraceReturnsUnfinished() {
        assertEquals(WaitVerdict.DONE_TREE_UNFINISHED, waitVerdict(true, true, false, 1_000, 6_000))
    }

    @Test
    fun endedWithFinishedTreeIsDone() {
        assertEquals(WaitVerdict.DONE, waitVerdict(true, true, true, 1_000, 1_000))
    }

    @Test
    fun timeoutDefaultsTo300() {
        assertEquals(300 to false, clampTimeout(null))
    }

    @Test
    fun negativeTimeoutBecomesZero() {
        assertEquals(0 to true, clampTimeout(-5))
    }

    @Test
    fun timeoutAboveMaxBecomes1800() {
        assertEquals(1800 to true, clampTimeout(5000))
    }
}
