package org.autojs.autojs.timing

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal fun Long.toLocalTimeOfDay(): LocalTime =
    LocalTime.ofNanoOfDay(TimeUnit.MILLISECONDS.toNanos(this))

internal fun LocalTime.toMillisOfDay(): Long =
    TimeUnit.NANOSECONDS.toMillis(toNanoOfDay())

internal fun LocalTime.toEpochMillisToday(zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.now().atZone(zone)
        .withHour(hour)
        .withMinute(minute)
        .withSecond(second)
        .withNano(nano)
        .toInstant()
        .toEpochMilli()

internal fun LocalDateTime.toEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atZone(zone).toInstant().toEpochMilli()

internal fun Long.toLocalDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

internal fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
