package de.lolhens.resticui.ui

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Formatters {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val dateTimeShortFormatter = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss")

    fun dateTime(dateTime: ZonedDateTime): String =
        dateTime.withZoneSameInstant(ZoneId.of("Europe/Berlin")).format(dateTimeFormatter)

    fun dateTimeShort(dateTime: ZonedDateTime): String =
        dateTime.withZoneSameInstant(ZoneId.of("Europe/Berlin")).format(dateTimeShortFormatter)

    fun durationDaysHours(duration: Duration) = when {
        duration.toHours() < 24 -> "${duration.toHours()} hours"
        duration.toHours() % 24 <= 0 -> "${duration.toHours() / 24} days"
        else -> "${duration.toHours() / 24} days ${duration.toHours()} hours"
    }
}
