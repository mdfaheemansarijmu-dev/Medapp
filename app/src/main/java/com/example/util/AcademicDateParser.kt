package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

/**
 * High-precision date and time parser for medical college announcements,
 * WhatsApp notices, timetable entries, and student conversational prompts.
 *
 * Supports Indian & International formats:
 * - DD/MM/YYYY, DD-MM-YYYY, DD.MM.YYYY, YYYY-MM-DD
 * - DD/MM, DD-MM (with auto-academic year resolution)
 * - "24th Oct 2026", "24 October 2026", "24th Oct", "24 Oct"
 * - "Oct 24, 2026", "October 24, 2026", "Oct 24", "October 24"
 * - "2nd Nov", "2 Nov", "November 2"
 * - Relative dates: "today", "tomorrow", "day after tomorrow", "next monday", "this friday"
 * - Times: "10:00 AM", "10am", "11:30 AM", "02:00 PM", "2pm", "14:00", "2.30 to 3.30"
 */
object AcademicDateParser {

    val TIMEZONE: TimeZone get() = TimeZone.getDefault()

    data class ParsedDateTimeResult(
        val timeInMillis: Long,
        val dateString: String,      // "yyyy-MM-dd"
        val readableDate: String,    // "Saturday, 24 Oct 2026"
        val timeString: String,      // "10:00 AM"
        val hasExplicitDate: Boolean,
        val hasExplicitTime: Boolean
    )

    private val MONTH_MAP = mapOf(
        "jan" to 0, "january" to 0,
        "feb" to 1, "february" to 1,
        "mar" to 2, "march" to 2,
        "apr" to 3, "april" to 3,
        "may" to 4,
        "jun" to 5, "june" to 5,
        "jul" to 6, "july" to 6,
        "aug" to 7, "august" to 7,
        "sep" to 8, "sept" to 8, "september" to 8,
        "oct" to 9, "october" to 9,
        "nov" to 10, "november" to 10,
        "dec" to 11, "december" to 11
    )

    private val DAY_OF_WEEK_MAP = mapOf(
        "monday" to Calendar.MONDAY,
        "mon" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY,
        "tue" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY,
        "wed" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY,
        "thu" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY,
        "fri" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY,
        "sat" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY,
        "sun" to Calendar.SUNDAY
    )

    /**
     * Parses any text string containing dates and/or times into a full ParsedDateTimeResult.
     */
    fun parse(
        text: String?,
        referenceTimeMillis: Long = System.currentTimeMillis(),
        defaultHour: Int = 9,
        defaultMinute: Int = 30
    ): ParsedDateTimeResult {
        val cal = Calendar.getInstance(TIMEZONE).apply {
            timeInMillis = referenceTimeMillis
            set(Calendar.HOUR_OF_DAY, defaultHour)
            set(Calendar.MINUTE, defaultMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (text.isNullOrBlank()) {
            return buildResult(cal, hasExplicitDate = false, hasExplicitTime = false)
        }

        val cleanText = text.trim()
        val lower = cleanText.lowercase(Locale.US)

        var hasExplicitDate = false
        var hasExplicitTime = false

        // 1. Try to extract explicit Time first
        val parsedTime = extractTime(lower)
        if (parsedTime != null) {
            cal.set(Calendar.HOUR_OF_DAY, parsedTime.first)
            cal.set(Calendar.MINUTE, parsedTime.second)
            hasExplicitTime = true
        }

        // 2. Check for numeric dates: DD/MM/YYYY, DD-MM-YYYY, DD.MM.YYYY, YYYY-MM-DD
        val isoMatch = Pattern.compile("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})").matcher(cleanText)
        if (isoMatch.find()) {
            val y = isoMatch.group(1)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
            val m = (isoMatch.group(2)?.toIntOrNull() ?: 1) - 1
            val d = isoMatch.group(3)?.toIntOrNull() ?: 1
            cal.set(Calendar.YEAR, y)
            cal.set(Calendar.MONTH, m)
            cal.set(Calendar.DAY_OF_MONTH, d)
            hasExplicitDate = true
        } else {
            val dmyMatch = Pattern.compile("\\b(\\d{1,2})[-/.](\\d{1,2})(?:[-/.](\\d{2,4}))?\\b").matcher(cleanText)
            if (dmyMatch.find()) {
                val d = dmyMatch.group(1)?.toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)
                val m = (dmyMatch.group(2)?.toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1
                var y = dmyMatch.group(3)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
                if (y < 100) y += 2000
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, m)
                cal.set(Calendar.DAY_OF_MONTH, d)
                hasExplicitDate = true
            }
        }

        // 3. Check for Named Month dates: "24th Oct 2026", "24 October", "Oct 24", "2nd Nov"
        if (!hasExplicitDate) {
            val monthRegex = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
            
            // Format: "24th Oct 2026" or "24 October"
            val patternDayMonth = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+${monthRegex}(?:\\s*,?\\s*(\\d{4}))?\\b", Pattern.CASE_INSENSITIVE)
            val dmMatcher = patternDayMonth.matcher(cleanText)

            if (dmMatcher.find()) {
                val day = dmMatcher.group(1)?.toIntOrNull() ?: 1
                val monthName = dmMatcher.group(2)?.lowercase(Locale.US) ?: ""
                val month = MONTH_MAP[monthName] ?: cal.get(Calendar.MONTH)
                val year = dmMatcher.group(3)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
                
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)
                hasExplicitDate = true
            } else {
                // Format: "Oct 24, 2026" or "October 24"
                val patternMonthDay = Pattern.compile("\\b${monthRegex}\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:\\s*,?\\s*(\\d{4}))?\\b", Pattern.CASE_INSENSITIVE)
                val mdMatcher = patternMonthDay.matcher(cleanText)
                if (mdMatcher.find()) {
                    val monthName = mdMatcher.group(1)?.lowercase(Locale.US) ?: ""
                    val day = mdMatcher.group(2)?.toIntOrNull() ?: 1
                    val month = MONTH_MAP[monthName] ?: cal.get(Calendar.MONTH)
                    val year = mdMatcher.group(3)?.toIntOrNull() ?: cal.get(Calendar.YEAR)

                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, month)
                    cal.set(Calendar.DAY_OF_MONTH, day)
                    hasExplicitDate = true
                }
            }
        }

        // 4. Relative terms: "today", "tomorrow", "day after tomorrow", "yesterday"
        if (!hasExplicitDate) {
            when {
                lower.contains("day after tomorrow") -> {
                    cal.add(Calendar.DAY_OF_YEAR, 2)
                    hasExplicitDate = true
                }
                lower.contains("tomorrow") -> {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    hasExplicitDate = true
                }
                lower.contains("yesterday") -> {
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    hasExplicitDate = true
                }
                lower.contains("today") -> {
                    hasExplicitDate = true
                }
                else -> {
                    // 5. Day of the week: "next monday", "this friday", "wednesday"
                    for ((dayName, targetDay) in DAY_OF_WEEK_MAP) {
                        if (lower.contains(dayName)) {
                            val isNext = lower.contains("next $dayName")
                            var diff = targetDay - cal.get(Calendar.DAY_OF_WEEK)
                            if (diff <= 0) diff += 7
                            if (isNext && diff < 7) diff += 7
                            cal.add(Calendar.DAY_OF_YEAR, diff)
                            hasExplicitDate = true
                            break
                        }
                    }
                }
            }
        }

        return buildResult(cal, hasExplicitDate, hasExplicitTime)
    }

    private fun extractTime(lower: String): Pair<Int, Int>? {
        // e.g. "10:30 AM", "10:30am", "10 AM", "10am", "14:30", "2.30 pm"
        val timeRegex = Pattern.compile("(?i)\\b(\\d{1,2})(?:[:.](\\d{2}))?\\s*([ap]\\.?m\\.?)?\\b")
        val matcher = timeRegex.matcher(lower)

        while (matcher.find()) {
            val hStr = matcher.group(1) ?: continue
            val mStr = matcher.group(2)
            val amPm = matcher.group(3)?.lowercase(Locale.US)?.replace(".", "")

            var hour = hStr.toIntOrNull() ?: continue
            val minute = mStr?.toIntOrNull() ?: 0

            // Avoid treating days like "24" or years like "2026" as an hour if no am/pm or colon
            if (amPm == null && mStr == null && hour > 12) {
                continue
            }

            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0

            if (hour in 0..23 && minute in 0..59) {
                return Pair(hour, minute)
            }
        }
        return null
    }

    private fun buildResult(
        cal: Calendar,
        hasExplicitDate: Boolean,
        hasExplicitTime: Boolean
    ): ParsedDateTimeResult {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TIMEZONE }
        val sdfReadable = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.US).apply { timeZone = TIMEZONE }
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.US).apply { timeZone = TIMEZONE }

        return ParsedDateTimeResult(
            timeInMillis = cal.timeInMillis,
            dateString = sdfDate.format(cal.time),
            readableDate = sdfReadable.format(cal.time),
            timeString = sdfTime.format(cal.time),
            hasExplicitDate = hasExplicitDate,
            hasExplicitTime = hasExplicitTime
        )
    }

    /**
     * Returns true if the string mentions any date or day keyword.
     */
    fun containsDateReference(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        if (lower.contains("today") || lower.contains("tomorrow") || lower.contains("yesterday")) return true
        if (DAY_OF_WEEK_MAP.keys.any { lower.contains(it) }) return true
        if (MONTH_MAP.keys.any { lower.contains(it) }) return true
        if (Pattern.compile("\\b\\d{1,2}[-/.]\\d{1,2}").matcher(lower).find()) return true
        return false
    }
}
