package com.expensetracker.app.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.time.Duration
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object DateUtils {

    @Volatile var firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY

    fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now.toLocalDate())
            delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 500)
        }
    }.distinctUntilChanged()

    val DATE_FORMATTER_DD_MM_YYYY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a")
    val MONTH_YEAR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-yyyy")
    val DISPLAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    val DAY_MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM")

    fun formatDdMmYyyy(epochMillis: Long): String {
        val localDate = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return localDate.format(DATE_FORMATTER_DD_MM_YYYY)
    }

    fun formatDateTime(epochMillis: Long): String {
        val ldt = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        return ldt.format(DATE_TIME_FORMATTER)
    }

    fun formatFriendlyDate(epochMillis: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        }
    }

    fun getTodayStartAndEnd(today: LocalDate = LocalDate.now()): Pair<Long, Long> {
        val z = ZoneId.systemDefault()
        return Pair(today.atStartOfDay(z).toInstant().toEpochMilli(),
            today.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli() - 1)
    }

    fun getWeekStartAndEnd(startDayOfWeek: DayOfWeek = firstDayOfWeek, today: LocalDate = LocalDate.now()): Pair<Long, Long> {
        val z = ZoneId.systemDefault()
        val start = today.with(TemporalAdjusters.previousOrSame(startDayOfWeek))
        return Pair(start.atStartOfDay(z).toInstant().toEpochMilli(),
            start.plusDays(7).atStartOfDay(z).toInstant().toEpochMilli() - 1)
    }

    fun getMonthStartAndEnd(yearMonth: YearMonth = YearMonth.now()): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val startOfMonth = yearMonth.atDay(1)
        val endOfMonth = yearMonth.atEndOfMonth()
        val start = startOfMonth.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = endOfMonth.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return Pair(start, end)
    }

    fun getYearStartAndEnd(year: Int = LocalDate.now().year): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val startOfYear = LocalDate.of(year, 1, 1)
        val endOfYear = LocalDate.of(year, 12, 31)
        val start = startOfYear.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = endOfYear.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return Pair(start, end)
    }

    fun parseDdMmYyyyToEpochMillis(dateStr: String): Long? {
        return try {
            val date = LocalDate.parse(dateStr, DATE_FORMATTER_DD_MM_YYYY)
            date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
