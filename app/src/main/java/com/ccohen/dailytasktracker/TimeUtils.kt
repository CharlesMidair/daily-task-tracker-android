package com.ccohen.dailytasktracker

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val eventFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
private val resetFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")

fun formatEventTimestamp(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(eventFormatter)
}

fun formatResetTimestamp(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(resetFormatter)
}

fun isSameLocalDay(first: Long, second: Long, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
    val firstDate = Instant.ofEpochMilli(first).atZone(zoneId).toLocalDate()
    val secondDate = Instant.ofEpochMilli(second).atZone(zoneId).toLocalDate()
    return firstDate == secondDate
}
