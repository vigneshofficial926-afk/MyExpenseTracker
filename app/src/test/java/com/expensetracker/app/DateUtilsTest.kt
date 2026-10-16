package com.expensetracker.app

import com.expensetracker.app.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class DateUtilsTest {

    @Test
    fun testDateFormatting_DD_MM_YYYY() {
        val zone = ZoneId.systemDefault()
        val testDate = LocalDate.of(2026, 10, 16)
        val millis = testDate.atStartOfDay(zone).toInstant().toEpochMilli()

        val formatted = DateUtils.formatDdMmYyyy(millis)
        assertEquals("16-10-2026", formatted)
    }

    @Test
    fun testParseDdMmYyyyToEpochMillis() {
        val parsed = DateUtils.parseDdMmYyyyToEpochMillis("16-10-2026")
        assertNotNull(parsed)
        val formatted = DateUtils.formatDdMmYyyy(parsed!!)
        assertEquals("16-10-2026", formatted)
    }

    @Test
    fun testLeapYearMonthEndCalculation() {
        // 2028 is a leap year, February has 29 days
        val leapFeb = YearMonth.of(2028, 2)
        val (start, end) = DateUtils.getMonthStartAndEnd(leapFeb)

        val zone = ZoneId.systemDefault()
        val endLocalDate = java.time.Instant.ofEpochMilli(end).atZone(zone).toLocalDate()

        assertEquals(29, endLocalDate.dayOfMonth)
        assertEquals(2, endLocalDate.monthValue)
        assertEquals(2028, endLocalDate.year)
    }
}
