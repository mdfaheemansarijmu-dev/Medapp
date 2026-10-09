package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Assessment
import com.example.data.model.Assignment
import com.example.data.model.AttendanceRecord
import com.example.data.model.DailySubjectRevision
import com.example.data.model.StudyTask
import com.example.data.model.TimetableClass
import com.example.data.university.CollegeCategory
import com.example.data.university.CollegeInfo
import com.example.data.university.HolidayCountdown
import com.example.data.university.UniversityCalendarService
import com.example.data.university.UniversityDirectory
import com.example.data.university.UniversityHoliday
import com.example.ui.viewmodel.PlannerViewModel
import com.example.util.AttendanceTimeValidator
import java.text.SimpleDateFormat
import java.util.*

enum class CalendarViewMode {
    WEEK,
    MONTH
}

enum class AgendaFilter(val label: String) {
    ALL("All"),
    CLASSES("Classes"),
    TASKS("Exams & Tasks"),
    REVISIONS("AI Notes")
}

@Composable
fun CalendarScreen(
    viewModel: PlannerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentCalendar by viewModel.currentCalendarDate.collectAsStateWithLifecycle()
    val selectedCalendar by viewModel.selectedCalendarDate.collectAsStateWithLifecycle()

    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val assessments by viewModel.assessments.collectAsStateWithLifecycle()
    val studyTasks by viewModel.studyTasks.collectAsStateWithLifecycle()
    val timetable by viewModel.timetable.collectAsStateWithLifecycle()
    val attendanceRecords by viewModel.allAttendanceRecords.collectAsStateWithLifecycle()
    val revisions by viewModel.allRevisions.collectAsStateWithLifecycle()

    // University & Holiday State
    val studentCollege by viewModel.studentCollege.collectAsStateWithLifecycle()
    val selectedCollegeInfo by viewModel.selectedCollegeInfo.collectAsStateWithLifecycle()
    val todayHoliday by viewModel.todayHoliday.collectAsStateWithLifecycle()
    val tomorrowHoliday by viewModel.tomorrowHoliday.collectAsStateWithLifecycle()
    val upcomingHolidays by viewModel.upcomingHolidays.collectAsStateWithLifecycle()
    val isSyncingCalendar by viewModel.isSyncingCalendar.collectAsStateWithLifecycle()
    val lastCalendarSyncResult by viewModel.lastCalendarSyncResult.collectAsStateWithLifecycle()
    val isSyncingHolidays by viewModel.isSyncingHolidays.collectAsStateWithLifecycle()
    val holidaySyncStatus by viewModel.holidaySyncStatus.collectAsStateWithLifecycle()

    var showChangeCollegeDialog by remember { mutableStateOf(false) }

    // Active View Mode: Default to WEEK view for spacious, uncrowded layout
    var viewMode by remember { mutableStateOf(CalendarViewMode.WEEK) }
    var selectedFilter by remember { mutableStateOf(AgendaFilter.ALL) }

    // Months and years state
    var activeMonth by remember { mutableStateOf(selectedCalendar.get(Calendar.MONTH)) }
    var activeYear by remember { mutableStateOf(selectedCalendar.get(Calendar.YEAR)) }

    // Sync activeMonth/Year when selectedCalendar updates from outside
    LaunchedEffect(selectedCalendar) {
        activeMonth = selectedCalendar.get(Calendar.MONTH)
        activeYear = selectedCalendar.get(Calendar.YEAR)
    }

    // Calendar sync permission launcher
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_CALENDAR] == true &&
                permissions[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) {
            viewModel.syncWithGoogleCalendar(context)
        } else {
            Toast.makeText(context, "Calendar permission required to sync with Google Calendar.", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleGoogleCalendarSync() {
        val hasRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val hasWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (hasRead && hasWrite) {
            viewModel.syncWithGoogleCalendar(context)
        } else {
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    fun openGoogleCalendar() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("content://com.android.calendar/time/${selectedCalendar.timeInMillis}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No default Calendar app found on device.", Toast.LENGTH_SHORT).show()
        }
    }

    // Feedback for Google Calendar Sync
    LaunchedEffect(lastCalendarSyncResult) {
        lastCalendarSyncResult?.let { result ->
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        }
    }

    val daysInMonth = remember(activeMonth, activeYear) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, activeYear)
            set(Calendar.MONTH, activeMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val firstDayOfWeek = remember(activeMonth, activeYear) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, activeYear)
            set(Calendar.MONTH, activeMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday...
    }

    val monthName = remember(activeMonth) {
        val cal = Calendar.getInstance().apply { set(Calendar.MONTH, activeMonth) }
        SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
    }

    // Helper to calculate items on a given day of the active month
    fun getItemsForDate(dayOfMonth: Int): DailyItems {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, activeYear)
            set(Calendar.MONTH, activeMonth)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMs = cal.timeInMillis
        val endMs = startMs + (24 * 60 * 60 * 1000L)

        val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        val holiday = UniversityCalendarService.getHolidayForDate(dateStr, studentCollege)

        return DailyItems(
            assignments = assignments.filter { it.dueDate in startMs until endMs },
            assessments = assessments.filter { it.date in startMs until endMs },
            studyTasks = studyTasks.filter { it.dueDate in startMs until endMs },
            classes = timetable.filter { it.dayOfWeek == dayOfWeek },
            holiday = holiday,
            dateString = dateStr
        )
    }

    fun getItemsForCalendar(cal: Calendar): DailyItems {
        val tempCal = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMs = tempCal.timeInMillis
        val endMs = startMs + (24 * 60 * 60 * 1000L)

        val dayOfWeek = when (tempCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tempCal.time)
        val holiday = UniversityCalendarService.getHolidayForDate(dateStr, studentCollege)

        return DailyItems(
            assignments = assignments.filter { it.dueDate in startMs until endMs },
            assessments = assessments.filter { it.date in startMs until endMs },
            studyTasks = studyTasks.filter { it.dueDate in startMs until endMs },
            classes = timetable.filter { it.dayOfWeek == dayOfWeek },
            holiday = holiday,
            dateString = dateStr
        )
    }

    val selectedDayInMonth = if (selectedCalendar.get(Calendar.MONTH) == activeMonth && selectedCalendar.get(Calendar.YEAR) == activeYear) {
        selectedCalendar.get(Calendar.DAY_OF_MONTH)
    } else {
        -1
    }

    val selectedDateItems = remember(selectedCalendar, assignments, assessments, studyTasks, timetable, studentCollege) {
        getItemsForCalendar(selectedCalendar)
    }

    val dateString = remember(selectedCalendar) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedCalendar.time)
    }

    val todayDateString = remember {
        AttendanceTimeValidator.getTodayDateString()
    }

    val isPastDate = remember(dateString, todayDateString) {
        dateString < todayDateString
    }
    val isToday = remember(dateString, todayDateString) {
        dateString == todayDateString
    }
    val isFutureDate = remember(dateString, todayDateString) {
        dateString > todayDateString
    }

    val dayAttendance = remember(attendanceRecords, dateString) {
        attendanceRecords.filter { it.dateString == dateString }
    }

    val dayRevisions = remember(revisions, dateString) {
        revisions.filter { it.dateString == dateString }
    }

    val classRecords = remember(selectedDateItems.classes, dayAttendance) {
        val matchedAttendanceIds = mutableSetOf<Int>()
        val result = mutableListOf<CalendarClassRecord>()

        // 1. Map all scheduled classes for that day of week
        for (cls in selectedDateItems.classes.sortedBy { it.periodNumber }) {
            val matchingAtt = dayAttendance.firstOrNull { att ->
                !matchedAttendanceIds.contains(att.id) &&
                att.subject.equals(cls.subject, ignoreCase = true) &&
                (
                    (!att.startTime.isNullOrBlank() && cls.startTime.isNotBlank() && att.startTime == cls.startTime) ||
                    (att.classTime?.contains(cls.startTime) == true) ||
                    (att.classTime?.contains("Period ${cls.periodNumber}", ignoreCase = true) == true)
                )
            } ?: dayAttendance.firstOrNull { att ->
                !matchedAttendanceIds.contains(att.id) && att.subject.equals(cls.subject, ignoreCase = true)
            }

            if (matchingAtt != null) {
                matchedAttendanceIds.add(matchingAtt.id)
            }

            result.add(
                CalendarClassRecord(
                    scheduledClass = cls,
                    subject = cls.subject,
                    periodNumber = cls.periodNumber,
                    startTime = cls.startTime,
                    endTime = cls.endTime,
                    room = cls.room,
                    attendanceRecord = matchingAtt,
                    isExtraSession = false
                )
            )
        }

        // 2. Map any extra attendance records logged on this date
        for (att in dayAttendance) {
            if (!matchedAttendanceIds.contains(att.id)) {
                result.add(
                    CalendarClassRecord(
                        scheduledClass = null,
                        subject = att.subject,
                        periodNumber = 0,
                        startTime = att.startTime ?: att.classTime?.substringBefore("-")?.trim() ?: "Special Session",
                        endTime = att.endTime ?: att.classTime?.substringAfter("-", "")?.trim() ?: "",
                        room = null,
                        attendanceRecord = att,
                        isExtraSession = true
                    )
                )
            }
        }

        result
    }

    // 7 days of the currently selected week
    val currentWeekDays = remember(selectedCalendar) {
        val cal = (selectedCalendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        (0..6).map {
            (cal.clone() as Calendar).also { cal.add(Calendar.DAY_OF_MONTH, 1) }
        }
    }

    val isDiaryEmpty = selectedDateItems.assignments.isEmpty() &&
                       selectedDateItems.assessments.isEmpty() &&
                       selectedDateItems.studyTasks.isEmpty() &&
                       classRecords.isEmpty() &&
                       selectedDateItems.holiday == null &&
                       dayRevisions.isEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Modern, Uncrowded Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Calendar",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showChangeCollegeDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedCollegeInfo?.shortName ?: studentCollege.take(22),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Change University",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Compact action icons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Holiday Sync Icon Button
                    IconButton(
                        onClick = { viewModel.syncOfficialHolidays() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .testTag("btn_sync_holidays")
                    ) {
                        if (isSyncingHolidays) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = "Sync Official Gazette Holidays",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Open Device Calendar
                    IconButton(
                        onClick = { openGoogleCalendar() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Open Device Calendar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Sync Google Calendar Button
                    FilledTonalButton(
                        onClick = { handleGoogleCalendarSync() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("sync_google_calendar_button")
                    ) {
                        if (isSyncingCalendar) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Syncing", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "G-Cal",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Compact Holiday Alert Banner if active
            if (tomorrowHoliday != null) {
                val hol = tomorrowHoliday!!
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("tomorrow_holiday_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Celebration,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tomorrow: ${hol.name}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Classes & clinical postings suspended",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFB45309)),
                                maxLines = 1
                            )
                        }
                    }
                }
            } else if (todayHoliday != null) {
                val hol = todayHoliday!!
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE0F2FE),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BeachAccess,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Today: ${hol.name}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF075985)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Enjoy your academic recess",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF0284C7)),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Sleek Calendar Navigation & Mode Switcher Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Month Title & Today Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$monthName $activeYear",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.testTag("calendar_month_label")
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val today = Calendar.getInstance()
                                activeMonth = today.get(Calendar.MONTH)
                                activeYear = today.get(Calendar.YEAR)
                                viewModel.selectCalendarDate(today)
                            }
                    ) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Mode Toggle (Week | Month) + Chevrons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Week / Month Toggle Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (viewMode == CalendarViewMode.WEEK) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewMode = CalendarViewMode.WEEK }
                            ) {
                                Text(
                                    text = "Week",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (viewMode == CalendarViewMode.WEEK) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (viewMode == CalendarViewMode.MONTH) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewMode = CalendarViewMode.MONTH }
                            ) {
                                Text(
                                    text = "Month",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (viewMode == CalendarViewMode.MONTH) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Previous Button
                    IconButton(
                        onClick = {
                            if (viewMode == CalendarViewMode.MONTH) {
                                if (activeMonth == 0) {
                                    activeMonth = 11
                                    activeYear -= 1
                                } else {
                                    activeMonth -= 1
                                }
                            } else {
                                val newCal = (selectedCalendar.clone() as Calendar).apply {
                                    add(Calendar.DAY_OF_MONTH, -7)
                                }
                                activeMonth = newCal.get(Calendar.MONTH)
                                activeYear = newCal.get(Calendar.YEAR)
                                viewModel.selectCalendarDate(newCal)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = "Previous",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Next Button
                    IconButton(
                        onClick = {
                            if (viewMode == CalendarViewMode.MONTH) {
                                if (activeMonth == 11) {
                                    activeMonth = 0
                                    activeYear += 1
                                } else {
                                    activeMonth += 1
                                }
                            } else {
                                val newCal = (selectedCalendar.clone() as Calendar).apply {
                                    add(Calendar.DAY_OF_MONTH, 7)
                                }
                                activeMonth = newCal.get(Calendar.MONTH)
                                activeYear = newCal.get(Calendar.YEAR)
                                viewModel.selectCalendarDate(newCal)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Next",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Calendar Display: Week Strip or Month Grid
            if (viewMode == CalendarViewMode.WEEK) {
                // Sleek, Compact 7-Day Week Strip (Leaves 80% screen height for agenda!)
                WeekDaysStrip(
                    weekDays = currentWeekDays,
                    selectedCalendar = selectedCalendar,
                    onDayClick = { cal ->
                        viewModel.selectCalendarDate(cal)
                    },
                    getDotsForCal = { cal ->
                        val items = getItemsForCalendar(cal)
                        val dayAtt = attendanceRecords.filter { it.dateString == items.dateString }
                        val hasPresent = dayAtt.any { it.isPresent || it.status == "PRESENT" }
                        val hasAbsent = dayAtt.any { !it.isPresent && it.status == "ABSENT" }
                        CalendarDots(
                            hasClasses = items.classes.isNotEmpty(),
                            hasAssignments = items.assignments.isNotEmpty(),
                            hasAssessments = items.assessments.isNotEmpty(),
                            hasHoliday = items.holiday != null,
                            hasPresent = hasPresent,
                            hasAbsent = hasAbsent
                        )
                    }
                )
            } else {
                // Full Month View Grid with clean day headers
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val weekDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        weekDays.forEach { day ->
                            Text(
                                text = day,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    CalendarDaysGrid(
                        daysInMonth = daysInMonth,
                        firstDayOfWeek = firstDayOfWeek,
                        selectedDay = selectedDayInMonth,
                        onDayClick = { day ->
                            val nextSel = Calendar.getInstance().apply {
                                set(Calendar.YEAR, activeYear)
                                set(Calendar.MONTH, activeMonth)
                                set(Calendar.DAY_OF_MONTH, day)
                            }
                            viewModel.selectCalendarDate(nextSel)
                        },
                        getDotsForDay = { day ->
                            val items = getItemsForDate(day)
                            val dayAtt = attendanceRecords.filter { it.dateString == items.dateString }
                            val hasPresent = dayAtt.any { it.isPresent || it.status == "PRESENT" }
                            val hasAbsent = dayAtt.any { !it.isPresent && it.status == "ABSENT" }
                            CalendarDots(
                                hasClasses = items.classes.isNotEmpty(),
                                hasAssignments = items.assignments.isNotEmpty(),
                                hasAssessments = items.assessments.isNotEmpty(),
                                hasHoliday = items.holiday != null,
                                hasPresent = hasPresent,
                                hasAbsent = hasAbsent
                            )
                        }
                    )
                }
            }

            // Compact Upcoming Holidays Carousel (Clean, single-line horizontal chips)
            if (upcomingHolidays.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(upcomingHolidays, key = { it.holiday.id }) { item ->
                        UpcomingHolidayChip(
                            item = item,
                            onClick = {
                                try {
                                    val parts = item.holiday.date.split("-")
                                    if (parts.size == 3) {
                                        val y = parts[0].toInt()
                                        val m = parts[1].toInt() - 1
                                        val d = parts[2].toInt()
                                        activeYear = y
                                        activeMonth = m
                                        val cal = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, y)
                                            set(Calendar.MONTH, m)
                                            set(Calendar.DAY_OF_MONTH, d)
                                        }
                                        viewModel.selectCalendarDate(cal)
                                    }
                                } catch (e: Exception) { /* no-op */ }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Day Agenda Header & Category Filter Tabs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault()).format(selectedCalendar.time),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    if (isToday) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Streamlined Filter Pills (Prevent clutter, allow focusing on Classes / Attendance)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AgendaFilter.values()) { filter ->
                        val count = when (filter) {
                            AgendaFilter.ALL -> null
                            AgendaFilter.CLASSES -> classRecords.size
                            AgendaFilter.TASKS -> selectedDateItems.assessments.size + selectedDateItems.assignments.size
                            AgendaFilter.REVISIONS -> dayRevisions.size
                        }
                        val isSelected = selectedFilter == filter
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedFilter = filter }
                        ) {
                            Text(
                                text = if (count != null) "${filter.label} ($count)" else filter.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day Agenda List (Spacious & Clean)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Official Holiday Card
                if (selectedDateItems.holiday != null && (selectedFilter == AgendaFilter.ALL)) {
                    item {
                        OfficialHolidayAgendaCard(
                            holiday = selectedDateItems.holiday!!,
                            collegeName = selectedCollegeInfo?.shortName ?: studentCollege
                        )
                    }
                }

                if (isDiaryEmpty) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventAvailable,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No Classes or Deadlines",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "No classes, exams, attendance or tasks recorded for this date.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // 1. Classes & Attendance Section
                    if (selectedFilter == AgendaFilter.ALL || selectedFilter == AgendaFilter.CLASSES) {
                        if (classRecords.isNotEmpty()) {
                            // Sleek Attendance Summary Bar
                            item {
                                val dateFormatted = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(selectedCalendar.time)
                                PastClassAttendanceSummaryCard(
                                    classRecords = classRecords,
                                    dateFormatted = dateFormatted
                                )
                            }

                            // Class sessions with streamlined segmented attendance selectors
                            items(classRecords, key = { "class_${it.subject}_${it.periodNumber}_${it.startTime}" }) { record ->
                                PastClassRecordCard(
                                    record = record,
                                    dateString = dateString,
                                    onMarkAttendance = { isPresent, status ->
                                        viewModel.markAttendance(
                                            subject = record.subject,
                                            isPresent = isPresent,
                                            status = status,
                                            classTime = if (record.periodNumber > 0) "Period ${record.periodNumber} (${record.startTime}-${record.endTime})" else "${record.startTime}-${record.endTime}",
                                            startTime = record.startTime,
                                            endTime = record.endTime,
                                            dateString = dateString
                                        )
                                        val statusDisplay = when (status) {
                                            "PRESENT" -> "Present"
                                            "ABSENT" -> "Absent"
                                            else -> "No Class"
                                        }
                                        Toast.makeText(context, "${record.subject} marked $statusDisplay", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        } else if (selectedFilter == AgendaFilter.CLASSES) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No scheduled classes on this day.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Assessments & Assignments Section
                    if (selectedFilter == AgendaFilter.ALL || selectedFilter == AgendaFilter.TASKS) {
                        if (selectedDateItems.assessments.isNotEmpty()) {
                            items(selectedDateItems.assessments, key = { "cal_exam_${it.id}" }) { exam ->
                                AgendaRow(
                                    title = exam.title,
                                    subtitle = "${exam.subject} • ${exam.type}",
                                    icon = Icons.Default.Warning,
                                    color = MaterialTheme.colorScheme.error,
                                    tag = "Exam"
                                )
                            }
                        }

                        if (selectedDateItems.assignments.isNotEmpty()) {
                            items(selectedDateItems.assignments, key = { "cal_asg_${it.id}" }) { asg ->
                                AgendaRow(
                                    title = asg.title,
                                    subtitle = "${asg.subject} • ${asg.type}",
                                    icon = Icons.Default.Assignment,
                                    color = MaterialTheme.colorScheme.primary,
                                    tag = "Assignment"
                                )
                            }
                        }

                        if (selectedDateItems.studyTasks.isNotEmpty()) {
                            items(selectedDateItems.studyTasks, key = { "cal_study_${it.id}" }) { goal ->
                                AgendaRow(
                                    title = goal.title,
                                    subtitle = "${goal.subject} • Target: ${goal.targetMinutes}m",
                                    icon = Icons.Default.MenuBook,
                                    color = MaterialTheme.colorScheme.secondary,
                                    tag = "Study"
                                )
                            }
                        }
                    }

                    // 3. AI Lecture Revisions Section
                    if (selectedFilter == AgendaFilter.ALL || selectedFilter == AgendaFilter.REVISIONS) {
                        if (dayRevisions.isNotEmpty()) {
                            items(dayRevisions, key = { "cal_rev_${it.id}" }) { rev ->
                                CompactRevisionCard(rev = rev)
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    // Change College / University Dialog
    if (showChangeCollegeDialog) {
        ChangeCollegeSelectionDialog(
            currentCollege = studentCollege,
            onSelectCollege = { newCol ->
                viewModel.setStudentCollege(newCol)
                showChangeCollegeDialog = false
                Toast.makeText(context, "University updated to $newCol. Calendar synced!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showChangeCollegeDialog = false }
        )
    }
}

/**
 * Compact, Spacious 7-Day Week Strip
 * Renders the 7 days of the currently selected week horizontally
 */
@Composable
fun WeekDaysStrip(
    weekDays: List<Calendar>,
    selectedCalendar: Calendar,
    onDayClick: (Calendar) -> Unit,
    getDotsForCal: (Calendar) -> CalendarDots
) {
    val dayNames = listOf("S", "M", "T", "W", "T", "F", "S")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        weekDays.forEachIndexed { index, cal ->
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            val isSelected = cal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR) &&
                    cal.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH) &&
                    dayOfMonth == selectedCalendar.get(Calendar.DAY_OF_MONTH)

            val dots = getDotsForCal(cal)

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    dots.hasHoliday -> Color(0xFFFEF3C7)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
                border = if (!isSelected && dots.hasHoliday) BorderStroke(1.dp, Color(0xFFF59E0B)) else null,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDayClick(cal) }
                    .testTag("calendar_day_$dayOfMonth")
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = dayNames[index],
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            dots.hasHoliday -> Color(0xFF92400E)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            dots.hasHoliday -> Color(0xFF92400E)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Subtle Micro-Dot Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dots.hasPresent) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else Color(0xFF2E7D32))
                            )
                        }
                        if (dots.hasAbsent) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else Color(0xFFC62828))
                            )
                        }
                        if (dots.hasHoliday) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else Color(0xFFF59E0B))
                            )
                        } else if (dots.hasClasses && !dots.hasPresent && !dots.hasAbsent) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full Month Days Grid
 */
@Composable
fun CalendarDaysGrid(
    daysInMonth: Int,
    firstDayOfWeek: Int, // 1 = Sunday, 2 = Monday...
    selectedDay: Int,
    onDayClick: (Int) -> Unit,
    getDotsForDay: (Int) -> CalendarDots
) {
    val daysList = mutableListOf<Int?>()

    // Add empty spacers before first day of month
    val emptyPreDays = firstDayOfWeek - 1
    for (i in 0 until emptyPreDays) {
        daysList.add(null)
    }

    // Add days
    for (day in 1..daysInMonth) {
        daysList.add(day)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(daysList) { day ->
            if (day == null) {
                Box(modifier = Modifier.aspectRatio(1f))
            } else {
                val isSelected = day == selectedDay
                val dots = getDotsForDay(day)

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                dots.hasHoliday -> Color(0xFFFEF3C7)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            }
                        )
                        .clickable { onDayClick(day) }
                        .testTag("calendar_day_$day"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = day.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                dots.hasHoliday -> Color(0xFF92400E)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        // Clean Indicators Row
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (dots.hasHoliday) {
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else Color(0xFFF59E0B))
                                )
                            }
                            if (dots.hasPresent) {
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else Color(0xFF2E7D32))
                                )
                            }
                            if (dots.hasAbsent) {
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else Color(0xFFC62828))
                                )
                            }
                            if (!dots.hasHoliday && !dots.hasPresent && !dots.hasAbsent && (dots.hasClasses || dots.hasAssessments)) {
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern, Spacious Past Class Record Card
 * With Segmented Attendance Selector to avoid visual clutter
 */
@Composable
fun PastClassRecordCard(
    record: CalendarClassRecord,
    dateString: String,
    onMarkAttendance: (isPresent: Boolean, status: String) -> Unit
) {
    val statusLabel = when (record.status) {
        "PRESENT" -> "PRESENT"
        "ABSENT" -> "ABSENT"
        "NO_CLASS" -> "NO CLASS"
        else -> "UNMARKED"
    }
    val statusColor = when (record.status) {
        "PRESENT" -> Color(0xFF1B5E20)
        "ABSENT" -> Color(0xFFB71C1C)
        "NO_CLASS" -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusBg = when (record.status) {
        "PRESENT" -> Color(0xFFE8F5E9)
        "ABSENT" -> Color(0xFFFFEBEE)
        "NO_CLASS" -> Color(0xFFFFF3E0)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val statusBorder = when (record.status) {
        "PRESENT" -> Color(0xFF81C784)
        "ABSENT" -> Color(0xFFE57373)
        "NO_CLASS" -> Color(0xFFFFB74D)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }
    val statusIcon = when (record.status) {
        "PRESENT" -> Icons.Default.CheckCircle
        "ABSENT" -> Icons.Default.Cancel
        "NO_CLASS" -> Icons.Default.Block
        else -> Icons.Default.Schedule
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        shadowElevation = 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("class_attendance_card_${record.subject}")
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left Accent Color Strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(90.dp)
                    .background(statusBorder)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header: Subject & Current Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.subject,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (record.isExtraSession) {
                                "Special Session • ${record.startTime}"
                            } else {
                                "Period ${record.periodNumber} • ${record.startTime} - ${record.endTime}${if (!record.room.isNullOrBlank()) " • Room " + record.room else ""}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Compact Status Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusBg,
                        border = BorderStroke(1.dp, statusBorder),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = statusColor
                            )
                        }
                    }
                }

                // Sleek Segmented Attendance Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Present Segment
                    val isPresentActive = record.status == "PRESENT"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isPresentActive) Color(0xFF2E7D32) else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onMarkAttendance(true, "PRESENT") }
                            .testTag("btn_mark_present_${record.subject}")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isPresentActive) Color.White else Color(0xFF2E7D32),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Present",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPresentActive) Color.White else Color(0xFF2E7D32)
                                )
                            )
                        }
                    }

                    // Absent Segment
                    val isAbsentActive = record.status == "ABSENT"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isAbsentActive) Color(0xFFC62828) else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onMarkAttendance(false, "ABSENT") }
                            .testTag("btn_mark_absent_${record.subject}")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = if (isAbsentActive) Color.White else Color(0xFFC62828),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Absent",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAbsentActive) Color.White else Color(0xFFC62828)
                                )
                            )
                        }
                    }

                    // No Class Segment
                    val isNoClassActive = record.status == "NO_CLASS"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isNoClassActive) Color(0xFFE65100) else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onMarkAttendance(false, "NO_CLASS") }
                            .testTag("btn_mark_noclass_${record.subject}")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = null,
                                tint = if (isNoClassActive) Color.White else Color(0xFFE65100),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "No Class",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNoClassActive) Color.White else Color(0xFFE65100)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact, Clean Day Attendance Summary Card
 */
@Composable
fun PastClassAttendanceSummaryCard(
    classRecords: List<CalendarClassRecord>,
    dateFormatted: String
) {
    val presentCount = classRecords.count { it.status == "PRESENT" }
    val absentCount = classRecords.count { it.status == "ABSENT" }
    val totalCount = classRecords.size
    val attendedTotal = presentCount + absentCount
    val rate = if (attendedTotal > 0) (presentCount * 100) / attendedTotal else null

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("past_attendance_summary_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FactCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Day Attendance",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (rate != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (rate >= 75) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    ) {
                        Text(
                            text = "$rate% Attended",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (rate >= 75) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Stats Pill Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "✓ $presentCount Present",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1B5E20),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "✕ $absentCount Absent",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFB71C1C),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Total $totalCount Classes",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Clean Agenda Row for Assessments, Assignments, and Study Goals
 */
@Composable
fun AgendaRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    tag: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = color.copy(alpha = 0.12f)
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = color),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Compact AI Revision Card
 */
@Composable
fun CompactRevisionCard(rev: com.example.data.model.DailySubjectRevision) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🧠 ${rev.subject}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = if (rev.classTime.isNotBlank()) rev.classTime else "Revision",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = rev.aiSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OfficialHolidayAgendaCard(
    holiday: UniversityHoliday,
    collegeName: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFEF3C7),
        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("official_holiday_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Celebration,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Official Holiday",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFB45309)
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFDE68A)
                ) {
                    Text(
                        text = holiday.type.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = holiday.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF78350F)
                )
            )

            Text(
                text = "${holiday.description} • Lectures & postings suspended.",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF92400E)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun UpcomingHolidayChip(
    item: HolidayCountdown,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.width(150.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (item.daysRemaining <= 1) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = item.relativeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (item.daysRemaining <= 1) Color(0xFF92400E) else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                val dateLabel = remember(item.holiday.date) {
                    try {
                        val inFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val outFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                        inFormat.parse(item.holiday.date)?.let { outFormat.format(it) } ?: item.holiday.date
                    } catch (e: Exception) {
                        item.holiday.date
                    }
                }
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.holiday.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ChangeCollegeSelectionDialog(
    currentCollege: String,
    onSelectCollege: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<CollegeCategory?>(null) }
    var showCustomInput by remember { mutableStateOf(false) }
    var customCollegeName by remember { mutableStateOf("") }

    val colleges = remember(query, selectedCategory) {
        UniversityDirectory.filterColleges(query, selectedCategory)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Switch University / College",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Selecting your college automatically updates your calendar with official academic holidays, exams, and university schedules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search medical, AYUSH, homeopathy...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Chips for Quick Filtering
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == CollegeCategory.AYUSH,
                            onClick = { selectedCategory = if (selectedCategory == CollegeCategory.AYUSH) null else CollegeCategory.AYUSH },
                            label = { Text("🌿 AYUSH") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == CollegeCategory.CENTRAL_INI,
                            onClick = { selectedCategory = if (selectedCategory == CollegeCategory.CENTRAL_INI) null else CollegeCategory.CENTRAL_INI },
                            label = { Text("AIIMS") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == CollegeCategory.ALLOPATHIC,
                            onClick = { selectedCategory = if (selectedCategory == CollegeCategory.ALLOPATHIC) null else CollegeCategory.ALLOPATHIC },
                            label = { Text("MBBS") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (showCustomInput) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Enter Custom College Name:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = customCollegeName,
                                onValueChange = { customCollegeName = it },
                                placeholder = { Text("e.g. GMC Jammu") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showCustomInput = false }) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        if (customCollegeName.isNotBlank()) {
                                            onSelectCollege(customCollegeName.trim())
                                        }
                                    },
                                    enabled = customCollegeName.isNotBlank()
                                ) {
                                    Text("Save College")
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(colleges, key = { it.id }) { col ->
                        val isSelected = currentCollege.equals(col.name, ignoreCase = true) ||
                                currentCollege.equals(col.shortName, ignoreCase = true)

                        Surface(
                            onClick = { onSelectCollege(col.name) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = col.shortName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${col.name} • ${col.state}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!showCustomInput) {
                    TextButton(onClick = { showCustomInput = true }) {
                        Text("+ Custom")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

data class DailyItems(
    val assignments: List<Assignment>,
    val assessments: List<Assessment>,
    val studyTasks: List<StudyTask>,
    val classes: List<TimetableClass>,
    val holiday: UniversityHoliday? = null,
    val dateString: String = ""
) {
    fun isEmpty() = assignments.isEmpty() && assessments.isEmpty() && studyTasks.isEmpty() && classes.isEmpty() && holiday == null
}

data class CalendarDots(
    val hasClasses: Boolean,
    val hasAssignments: Boolean,
    val hasAssessments: Boolean,
    val hasHoliday: Boolean = false,
    val hasPresent: Boolean = false,
    val hasAbsent: Boolean = false
)

data class CalendarClassRecord(
    val scheduledClass: TimetableClass?,
    val subject: String,
    val periodNumber: Int,
    val startTime: String,
    val endTime: String,
    val room: String?,
    val attendanceRecord: AttendanceRecord?,
    val isExtraSession: Boolean = false
) {
    val status: String
        get() = when {
            attendanceRecord == null -> "NOT_MARKED"
            attendanceRecord.status == "NO_CLASS" || attendanceRecord.status == "CANCELLED" -> "NO_CLASS"
            attendanceRecord.isPresent || attendanceRecord.status == "PRESENT" -> "PRESENT"
            else -> "ABSENT"
        }
}
