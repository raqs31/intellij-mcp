package pl.mario.intellij.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RunStoreTest {
    @Test
    fun evictionDropsOldestFinishedAndKeepsLiveRuns() {
        val store = RunStore(maxFinished = 2)
        store.started(1, "live", 1)
        store.started(2, "a", 2)
        store.started(3, "b", 3)
        store.started(4, "c", 4)
        store.terminated(2, 0, 20)
        store.terminated(3, 0, 30)
        store.terminated(4, 0, 40)

        assertNull(store.get(2))
        assertNotNull(store.get(3))
        assertNotNull(store.get(4))
        assertNotNull(store.get(1))
    }

    @Test
    fun finishedIsNewestFirst() {
        val store = RunStore()
        store.started(1, "a", 1)
        store.started(2, "a", 2)
        store.started(3, "a", 3)
        store.terminated(2, 0, 20)
        store.terminated(3, 0, 30)
        store.terminated(1, 0, 10)

        assertEquals(listOf(3L, 2L, 1L), store.finished(null, 10).map { it.executionId })
    }

    @Test
    fun finishedFiltersByName() {
        val store = RunStore()
        store.started(1, "a", 1)
        store.started(2, "b", 2)
        store.terminated(1, 0, 10)
        store.terminated(2, 0, 20)

        assertEquals(listOf(2L), store.finished("b", 10).map { it.executionId })
    }

    @Test
    fun finishedHonoursLimit() {
        val store = RunStore()
        store.started(1, "a", 1)
        store.started(2, "a", 2)
        store.terminated(1, 0, 10)
        store.terminated(2, 0, 20)

        assertEquals(listOf(2L), store.finished(null, 1).map { it.executionId })
    }

    @Test
    fun terminatedWithoutStartHasNullStart() {
        val store = RunStore()
        store.terminated(7, 3, 70)

        val record = store.get(7)
        assertNotNull(record)
        assertNull(record!!.startedAtMillis)
        assertEquals(70L, record.finishedAtMillis)
        assertEquals(3, record.exitCode)
    }

    @Test
    fun repeatedStartReplacesRecord() {
        val store = RunStore()
        store.started(1, "old", 1)
        store.terminated(1, 0, 10)
        store.started(1, "new", 100)

        assertEquals(RunRecord(1, "new", 100, null, null, null), store.get(1))
    }

    @Test
    fun testsFinishedStoresCounts() {
        val store = RunStore()
        val counts = TestCounts(total = 5, passed = 3, failed = 1, ignored = 1, other = 0, finished = true)
        store.started(1, "a", 1)
        store.testsFinished(1, counts)

        assertEquals(counts, store.get(1)!!.tests)
    }
}
