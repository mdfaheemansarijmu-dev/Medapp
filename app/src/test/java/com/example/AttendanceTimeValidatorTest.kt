package com.example

import com.example.util.AttendanceTimeValidator
import com.example.util.AttendanceValidationResult
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

class AttendanceTimeValidatorTest {

    @Test
    fun testParseTimeToMinutes_standardFormats() {
        assertEquals(570, AttendanceTimeValidator.parseTimeToMinutes("09:30 AM"))
        assertEquals(570, AttendanceTimeValidator.parseTimeToMinutes("9:30 AM"))
        assertEquals(630, AttendanceTimeValidator.parseTimeToMinutes("10:30 AM"))
        assertEquals(720, AttendanceTimeValidator.parseTimeToMinutes("12:00 PM"))
        assertEquals(0, AttendanceTimeValidator.parseTimeToMinutes("12:00 AM"))
        assertEquals(810, AttendanceTimeValidator.parseTimeToMinutes("01:30 PM"))
        assertEquals(870, AttendanceTimeValidator.parseTimeToMinutes("14:30"))
        assertEquals(570, AttendanceTimeValidator.parseTimeToMinutes("09:30 AM - 10:30 AM"))
        assertEquals(570, AttendanceTimeValidator.parseTimeToMinutes("09:30-10:30"))
    }

    @Test
    fun testClassAt930AM_blockedBefore930AM() {
        // Today at 9:15 AM
        val calendar = Calendar.getInstance()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

        calendar.set(Calendar.HOUR_OF_DAY, 9)
        calendar.set(Calendar.MINUTE, 15)
        val testTime915AM = calendar.timeInMillis

        val result = AttendanceTimeValidator.validateAttendanceTime(
            dateString = todayStr,
            startTime = "09:30 AM",
            classTime = "09:30 AM - 10:30 AM",
            currentTimeMillis = testTime915AM
        )

        assertTrue("Attendance must be blocked before 9:30 AM", result is AttendanceValidationResult.TooEarly)
        assertFalse(
            AttendanceTimeValidator.isAttendanceAllowed(
                dateString = todayStr,
                startTime = "09:30 AM",
                classTime = "09:30 AM - 10:30 AM",
                currentTimeMillis = testTime915AM
            )
        )
    }

    @Test
    fun testClassAt930AM_acceptedAtOrAfter930AM() {
        val calendar = Calendar.getInstance()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

        // Exactly 9:30 AM
        calendar.set(Calendar.HOUR_OF_DAY, 9)
        calendar.set(Calendar.MINUTE, 30)
        val testTime930AM = calendar.timeInMillis

        val resultAt930 = AttendanceTimeValidator.validateAttendanceTime(
            dateString = todayStr,
            startTime = "09:30 AM",
            classTime = "09:30 AM - 10:30 AM",
            currentTimeMillis = testTime930AM
        )
        assertEquals(AttendanceValidationResult.Allowed, resultAt930)

        // At 10:00 AM (during class)
        calendar.set(Calendar.HOUR_OF_DAY, 10)
        calendar.set(Calendar.MINUTE, 0)
        val testTime1000AM = calendar.timeInMillis

        val resultAt1000 = AttendanceTimeValidator.validateAttendanceTime(
            dateString = todayStr,
            startTime = "09:30 AM",
            currentTimeMillis = testTime1000AM
        )
        assertEquals(AttendanceValidationResult.Allowed, resultAt1000)

        // At 11:30 AM (after class)
        calendar.set(Calendar.HOUR_OF_DAY, 11)
        calendar.set(Calendar.MINUTE, 30)
        val testTime1130AM = calendar.timeInMillis

        val resultAt1130 = AttendanceTimeValidator.validateAttendanceTime(
            dateString = todayStr,
            startTime = "09:30 AM",
            currentTimeMillis = testTime1130AM
        )
        assertEquals(AttendanceValidationResult.Allowed, resultAt1130)
    }

    @Test
    fun testFutureDate_blocked() {
        val calendar = Calendar.getInstance()
        val todayMillis = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

        val result = AttendanceTimeValidator.validateAttendanceTime(
            dateString = tomorrowStr,
            startTime = "09:30 AM",
            currentTimeMillis = todayMillis
        )

        assertTrue(result is AttendanceValidationResult.FutureDate)
    }

    @Test
    fun testPastDate_allowed() {
        val calendar = Calendar.getInstance()
        val todayMillis = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

        val result = AttendanceTimeValidator.validateAttendanceTime(
            dateString = yesterdayStr,
            startTime = "09:30 AM",
            currentTimeMillis = todayMillis
        )

        assertEquals(AttendanceValidationResult.Allowed, result)
    }
}
