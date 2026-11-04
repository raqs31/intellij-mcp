package pl.mario.intellij.mcp

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class TestCounts(
    val total: Int,
    val passed: Int,
    val failed: Int,
    val ignored: Int,
    val other: Int,
    val finished: Boolean,
)

data class RunRecord(
    val executionId: Long,
    val name: String,
    val startedAtMillis: Long?,
    val finishedAtMillis: Long?,
    val exitCode: Int?,
    val tests: TestCounts?,
)

class RunStore(
    private val maxFinished: Int = 50,
) {
    // Listener callbacks can arrive off the EDT, so every access goes through this lock.
    private val lock = Any()
    private val records = LinkedHashMap<Long, RunRecord>()

    fun started(
        id: Long,
        name: String,
        atMillis: Long,
    ) = synchronized(lock) {
        records.remove(id)
        records[id] = RunRecord(id, name, atMillis, null, null, null)
    }

    fun terminated(
        id: Long,
        exitCode: Int,
        atMillis: Long,
    ) = synchronized(lock) {
        val existing = records[id] ?: RunRecord(id, "", null, null, null, null)
        records[id] = existing.copy(finishedAtMillis = atMillis, exitCode = exitCode)
        evictOldestFinished()
    }

    fun testsFinished(
        id: Long,
        counts: TestCounts,
    ) = synchronized(lock) {
        records[id]?.let { records[id] = it.copy(tests = counts) }
        Unit
    }

    fun get(id: Long): RunRecord? = synchronized(lock) { records[id] }

    fun finished(
        name: String?,
        limit: Int,
    ): List<RunRecord> =
        synchronized(lock) {
            records.values
                .filter { it.finishedAtMillis != null && (name == null || it.name == name) }
                .sortedByDescending { it.finishedAtMillis }
                .take(limit)
        }

    private fun evictOldestFinished() {
        val done = records.values.filter { it.finishedAtMillis != null }
        if (done.size > maxFinished) {
            records.remove(done.minBy { it.finishedAtMillis!! }.executionId)
        }
    }
}

fun formatInstant(
    millis: Long?,
    zone: ZoneId = ZoneId.systemDefault(),
): String =
    if (millis == null) {
        "unknown"
    } else {
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(millis).truncatedTo(ChronoUnit.SECONDS).atZone(zone),
        )
    }
