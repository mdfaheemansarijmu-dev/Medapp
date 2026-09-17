package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Result of checking whether attendance can be recorded or changed for a given class session.
 */
sealed class AttendanceValidationResult {
    object Allowed : AttendanceValidationResult()

    data class TooEarly(
        val startTime: String,
        val message: String = "Attendance can only be marked after the class start time ($startTime)."
    ) : AttendanceValidationResult()

    data class FutureDate(
        val dateString: String,
        val message: String = "Attendance cannot be marked for future dates ($dateString)."
    ) : AttendanceValidationResult()
}

/**
 * Validates attendance marking rules based on scheduled class start times.
 * Enforces rule: Students cannot mark present or absent before class start time.
 * Attendance is only accepted at or after the scheduled class start time.
 */
object AttendanceTimeValidator {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Parses a time string (e.g. "09:30 AM", "9:30 AM", "14:30", "09:30") into total minutes from midnight.
     * Returns -1 if unable to parse.
     */
    fun parseTimeToMinutes(timeStr: String?): Int {
        if (timeStr.isNullOrBlank()) return -1
        return try {
            val trimmed = timeStr.trim().uppercase(Locale.US)
            // If passed a range like "09:30 AM - 10:30 AM" or "09:30-10:30", take the first part
            val firstSegment = if (trimmed.contains("-")) {
                trimmed.split("-").first().trim()
            } else {
                trimmed
            }

            val parts = firstSegment.split("\\s+".toRegex())
            val timeParts = parts[0].split(":")
            var hour = timeParts[0].toInt()
            val minute = if (timeParts.size > 1) timeParts[1].toInt() else 0

            if (parts.size > 1) {
                val amPm = parts[1]
                if (amPm == "PM" && hour != 12) hour += 12
                if (amPm == "AM" && hour == 12) hour = 0
            }
            hour * 60 + minute
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Extracts a clean start time representation from either startTime or classTime range.
     */
    fun extractStartTime(startTime: String?, classTime: String?): String? {
        if (!startTime.isNullOrBlank()) {
            return startTime.trim()
        }
        if (!classTime.isNullOrBlank()) {
            val firstPart = classTime.split("-").firstOrNull()?.trim()
            if (!firstPart.isNullOrBlank()) {
                return firstPart
            }
        }
        return null
    }

    /**
     * Checks if attendance is allowed for a given date and class time.
     *
     * Rules:
     * 1. Past dates: Allowed (class has already taken place).
     * 2. Future dates: Not allowed (class is in the future).
     * 3. Today:
     *    - If current time is BEFORE class start time: Not allowed (TooEarly).
     *    - If current time is AT OR AFTER class start time: Allowed.
     */
    fun validateAttendanceTime(
        dateString: String?,
        startTime: String?,
        classTime: String? = null,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): AttendanceValidationResult {
        val todayStr = synchronized(DATE_FORMAT) {
            DATE_FORMAT.format(Date(currentTimeMillis))
        }
        val targetDateStr = if (dateString.isNullOrBlank()) todayStr else dateString.trim()

        // Compare target date with today
        if (targetDateStr > todayStr) {
            return AttendanceValidationResult.FutureDate(
                dateString = targetDateStr,
                message = "Attendance cannot be marked for a future date ($targetDateStr)."
            )
        } else if (targetDateStr < todayStr) {
            // Past dates are allowed (session already occurred)
            return AttendanceValidationResult.Allowed
        }

        // Target date is TODAY: check start time
        val effectiveStartTime = extractStartTime(startTime, classTime) ?: return AttendanceValidationResult.Allowed

        val classStartMinutes = parseTimeToMinutes(effectiveStartTime)
        if (classStartMinutes < 0) {
            // Could not parse start time, permit as fallback
            return AttendanceValidationResult.Allowed
        }

        val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        return if (currentMinutes < classStartMinutes) {
            AttendanceValidationResult.TooEarly(
                startTime = effectiveStartTime,
                message = "Attendance for this class opens at $effectiveStartTime. You cannot mark attendance before class time."
            )
        } else {
            AttendanceValidationResult.Allowed
        }
    }

    /**
     * Convenience boolean check.
     */
    fun isAttendanceAllowed(
        dateString: String?,
        startTime: String?,
        classTime: String? = null,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return validateAttendanceTime(dateString, startTime, classTime, currentTimeMillis) is AttendanceValidationResult.Allowed
    }
}
