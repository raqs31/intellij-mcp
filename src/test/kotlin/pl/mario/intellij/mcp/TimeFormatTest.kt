package pl.mario.intellij.mcp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TimeFormatTest {
    private val zone = ZoneOffset.ofHours(1)
    private val base = OffsetDateTime.of(2026, 11, 4, 14, 3, 40, 0, zone).toInstant().toEpochMilli()

    @Test
    fun nullIsUnknown() {
        assertEquals("unknown", formatInstant(null))
    }

    @Test
    fun formatsIsoWithOffset() {
        assertEquals("2026-11-04T14:03:40+01:00", formatInstant(base, zone))
    }

    @Test
    fun dropsSubSecondPart() {
        assertEquals("2026-11-04T14:03:40+01:00", formatInstant(base + 999, zone))
    }
}
