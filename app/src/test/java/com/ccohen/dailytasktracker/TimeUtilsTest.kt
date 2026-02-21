package com.ccohen.dailytasktracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeUtilsTest {
    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")

    @Test
    fun sameDayReturnsTrue() {
        val first = ZonedDateTime.of(2026, 2, 21, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val second = ZonedDateTime.of(2026, 2, 21, 23, 59, 0, 0, zone).toInstant().toEpochMilli()

        assertTrue(isSameLocalDay(first, second, zone))
    }

    @Test
    fun differentDayReturnsFalse() {
        val first = ZonedDateTime.of(2026, 2, 21, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        val second = ZonedDateTime.of(2026, 2, 22, 0, 1, 0, 0, zone).toInstant().toEpochMilli()

        assertFalse(isSameLocalDay(first, second, zone))
    }
}
