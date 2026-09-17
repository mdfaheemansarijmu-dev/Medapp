package com.example.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.Assessment
import com.example.data.model.Assignment
import com.example.data.model.TimetableClass
import com.example.data.university.GoogleCalendarSyncResult
import com.example.data.university.UniversityHoliday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object GoogleCalendarSyncManager {

    private const val TAG = "GoogleCalendarSync"

    fun hasCalendarPermissions(context: Context): Boolean {
        val readPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR)
        val writePerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR)
        return readPerm == PackageManager.PERMISSION_GRANTED && writePerm == PackageManager.PERMISSION_GRANTED
    }

    suspend fun syncAllToGoogleCalendar(
        context: Context,
        classes: List<TimetableClass>,
        assignments: List<Assignment>,
        assessments: List<Assessment>,
        holidays: List<UniversityHoliday>,
        collegeName: String
    ): GoogleCalendarSyncResult = withContext(Dispatchers.IO) {
        if (!hasCalendarPermissions(context)) {
            return@withContext GoogleCalendarSyncResult(
                isSuccess = false,
                message = "Calendar permissions (READ/WRITE) are needed to synchronize with Google Calendar."
            )
        }

        try {
            val calendarId = getOrCreatePrimaryCalendarId(context)
            if (calendarId == -1L) {
                return@withContext GoogleCalendarSyncResult(
                    isSuccess = false,
                    message = "No active Google or system calendar account found on this device."
                )
            }

            var syncedCount = 0
            val resolver = context.contentResolver
            val timeZone = TimeZone.getDefault().id

            // 1. Sync University Holidays
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            for (holiday in holidays) {
                try {
                    val date = sdf.parse(holiday.date) ?: continue
                    val cal = Calendar.getInstance().apply {
                        time = date
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    val startMs = cal.timeInMillis
                    val endMs = startMs + (8 * 60 * 60 * 1000L) // 8-hour span

                    val values = ContentValues().apply {
                        put(CalendarContract.Events.DTSTART, startMs)
                        put(CalendarContract.Events.DTEND, endMs)
                        put(CalendarContract.Events.TITLE, "🌴 Holiday: ${holiday.name}")
                        put(CalendarContract.Events.DESCRIPTION, "${holiday.description}\n\nUniversity: $collegeName\nCategory: ${holiday.type.displayName}")
                        put(CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
                        put(CalendarContract.Events.ALL_DAY, 1)
                        put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    }

                    val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    if (uri != null) syncedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed syncing holiday ${holiday.name}", e)
                }
            }

            // 2. Sync Assessments / Exams
            for (exam in assessments) {
                try {
                    val startMs = exam.date
                    val endMs = startMs + (3 * 60 * 60 * 1000L) // 3 hours exam duration

                    val values = ContentValues().apply {
                        put(CalendarContract.Events.DTSTART, startMs)
                        put(CalendarContract.Events.DTEND, endMs)
                        put(CalendarContract.Events.TITLE, "📝 ${exam.type}: ${exam.title}")
                        put(CalendarContract.Events.DESCRIPTION, "Subject: ${exam.subject}\nType: ${exam.type}\nSyllabus: ${exam.syllabus ?: "Standard curriculum"}\nSynced from MedPulse")
                        put(CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
                        put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    }

                    val eventUri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    if (eventUri != null) {
                        syncedCount++
                        val eventId = ContentUris.parseId(eventUri)
                        // Add 1-day advance reminder
                        val reminderValues = ContentValues().apply {
                            put(CalendarContract.Reminders.EVENT_ID, eventId)
                            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                            put(CalendarContract.Reminders.MINUTES, 24 * 60)
                        }
                        resolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed syncing exam ${exam.title}", e)
                }
            }

            // 3. Sync Assignments / Deadlines
            for (asg in assignments) {
                try {
                    val dueMs = asg.dueDate
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.DTSTART, dueMs - (60 * 60 * 1000L))
                        put(CalendarContract.Events.DTEND, dueMs)
                        put(CalendarContract.Events.TITLE, "📌 Due: ${asg.title}")
                        put(CalendarContract.Events.DESCRIPTION, "Subject: ${asg.subject}\nPriority: ${asg.priority}\nSynced from MedPulse")
                        put(CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
                        put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    }
                    val eventUri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    if (eventUri != null) {
                        syncedCount++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed syncing assignment ${asg.title}", e)
                }
            }

            return@withContext GoogleCalendarSyncResult(
                isSuccess = true,
                message = "Successfully synchronized $syncedCount academic events and university holidays with Google Calendar!",
                syncedCount = syncedCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error synchronizing with Google Calendar", e)
            return@withContext GoogleCalendarSyncResult(
                isSuccess = false,
                message = "Google Calendar sync error: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    private fun getOrCreatePrimaryCalendarId(context: Context): Long {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.IS_PRIMARY
        )

        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )

            cursor?.use {
                var fallbackId = -1L
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val accountType = it.getString(2) ?: ""
                    val isPrimary = if (it.columnCount > 4) it.getInt(4) else 0

                    if (fallbackId == -1L) fallbackId = id

                    // Prefer Google account or primary device calendar
                    if (accountType.contains("google", ignoreCase = true) || isPrimary == 1) {
                        return id
                    }
                }
                return fallbackId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed querying calendars", e)
        }
        return -1L
    }

    fun createOpenGoogleCalendarIntent(timestamp: Long = System.currentTimeMillis()): Intent {
        val builder = Uri.parse("content://com.android.calendar/time").buildUpon()
        ContentUris.appendId(builder, timestamp)
        return Intent(Intent.ACTION_VIEW).apply {
            data = builder.build()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun createGoogleCalendarAddEventIntent(
        title: String,
        description: String,
        location: String = "",
        startTime: Long = System.currentTimeMillis() + 3600000L,
        endTime: Long = startTime + 3600000L
    ): Intent {
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun generateIcsContent(
        holidays: List<UniversityHoliday>,
        assessments: List<Assessment>,
        collegeName: String
    ): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\n")
        sb.append("VERSION:2.0\n")
        sb.append("PRODID:-//MedPulse//Academic & University Calendar//EN\n")
        sb.append("X-WR-CALNAME:MedPulse - $collegeName\n")
        sb.append("X-WR-TIMEZONE:Asia/Kolkata\n")

        val sdfDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val sdfDateTime = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        for (holiday in holidays) {
            try {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(holiday.date) ?: continue
                val dateStr = sdfDate.format(d)
                sb.append("BEGIN:VEVENT\n")
                sb.append("UID:${holiday.id}@medpulse.app\n")
                sb.append("DTSTAMP:${sdfDateTime.format(java.util.Date())}\n")
                sb.append("DTSTART;VALUE=DATE:$dateStr\n")
                sb.append("SUMMARY:🌴 Holiday: ${holiday.name}\n")
                sb.append("DESCRIPTION:${holiday.description.replace("\n", "\\n")}\n")
                sb.append("LOCATION:$collegeName\n")
                sb.append("STATUS:CONFIRMED\n")
                sb.append("END:VEVENT\n")
            } catch (e: Exception) {
                // Ignore
            }
        }

        for (exam in assessments) {
            try {
                val startCal = Calendar.getInstance().apply { timeInMillis = exam.date }
                val endCal = (startCal.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 3) }
                sb.append("BEGIN:VEVENT\n")
                sb.append("UID:exam_${exam.id}@medpulse.app\n")
                sb.append("DTSTAMP:${sdfDateTime.format(java.util.Date())}\n")
                sb.append("DTSTART:${sdfDateTime.format(startCal.time)}\n")
                sb.append("DTEND:${sdfDateTime.format(endCal.time)}\n")
                sb.append("SUMMARY:📝 ${exam.type}: ${exam.title}\n")
                sb.append("DESCRIPTION:Subject: ${exam.subject}\\nType: ${exam.type}\\nSyllabus: ${exam.syllabus ?: "N/A"}\n")
                sb.append("LOCATION:$collegeName\n")
                sb.append("STATUS:CONFIRMED\n")
                sb.append("END:VEVENT\n")
            } catch (e: Exception) {
                // Ignore
            }
        }

        sb.append("END:VCALENDAR\n")
        return sb.toString()
    }
}
