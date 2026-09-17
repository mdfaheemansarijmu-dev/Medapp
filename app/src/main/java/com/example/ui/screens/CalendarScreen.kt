package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import com.example.data.model.Assignment
import com.example.data.model.Assessment
import com.example.data.model.StudyTask
import com.example.data.model.TimetableClass
import com.example.data.university.CollegeCategory
import com.example.data.university.CollegeInfo
import com.example.data.university.HolidayCountdown
import com.example.data.university.UniversityCalendarService
import com.example.data.university.UniversityDirectory
import com.example.data.university.UniversityHoliday
import com.example.ui.viewmodel.PlannerViewModel
import java.text.SimpleDateFormat
import java.util.*

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

    // Months and years state
    var activeMonth by remember { mutableStateOf(selectedCalendar.get(Calendar.MONTH)) }
    var activeYear by remember { mutableStateOf(selectedCalendar.get(Calendar.YEAR)) }

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
            holiday = holiday
        )
    }

    val selectedDayInMonth = if (selectedCalendar.get(Calendar.MONTH) == activeMonth && selectedCalendar.get(Calendar.YEAR) == activeYear) {
        selectedCalendar.get(Calendar.DAY_OF_MONTH)
    } else {
        1
    }

    val selectedDateItems = getItemsForDate(selectedDayInMonth)

    val dateString = remember(selectedCalendar) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedCalendar.time)
    }

    val dayAttendance = remember(attendanceRecords, dateString) {
        attendanceRecords.filter { it.dateString == dateString }
    }

    val dayRevisions = remember(revisions, dateString) {
        revisions.filter { it.dateString == dateString }
    }

    val isDiaryEmpty = selectedDateItems.isEmpty() && dayAttendance.isEmpty() && dayRevisions.isEmpty()

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
            // Header with University Indicator and Google Sync Action
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Academic Calendar",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showChangeCollegeDialog = true }
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedCollegeInfo?.shortName ?: studentCollege.take(30),
                                style = MaterialTheme.typography.bodySmall.copy(
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
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Google Sync and Open Calendar Actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { openGoogleCalendar() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Open Device Calendar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Button(
                            onClick = { handleGoogleCalendarSync() },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("sync_google_calendar_button")
                        ) {
                            if (isSyncingCalendar) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Syncing...", style = MaterialTheme.typography.labelMedium)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync G-Cal", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }

                // Tomorrow Holiday Alert Banner (High Priority)
                if (tomorrowHoliday != null) {
                    val hol = tomorrowHoliday!!
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tomorrow_holiday_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Celebration,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🌴 Tomorrow is a Holiday: ${hol.name}!",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF92400E)
                                    )
                                )
                                Text(
                                    text = "Official university holiday. Lectures & clinical postings suspended.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF78350F))
                                )
                            }
                        }
                    }
                } else if (todayHoliday != null) {
                    val hol = todayHoliday!!
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFE0F2FE),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF0284C7),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.BeachAccess,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🎉 Today is a Holiday: ${hol.name}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF075985)
                                    )
                                )
                                Text(
                                    text = "${hol.description} • Enjoy your academic recess.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF0C4A6E))
                                )
                            }
                        }
                    }
                }
            }

            // Month navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (activeMonth == 0) {
                        activeMonth = 11
                        activeYear -= 1
                    } else {
                        activeMonth -= 1
                    }
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                }

                Text(
                    text = "$monthName $activeYear",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("calendar_month_label")
                )

                IconButton(onClick = {
                    if (activeMonth == 11) {
                        activeMonth = 0
                        activeYear += 1
                    } else {
                        activeMonth += 1
                    }
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                }
            }

            // Days of Week Headings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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

            Spacer(modifier = Modifier.height(6.dp))

            // Calendar Days Grid
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
                    CalendarDots(
                        hasClasses = items.classes.isNotEmpty(),
                        hasAssignments = items.assignments.isNotEmpty(),
                        hasAssessments = items.assessments.isNotEmpty(),
                        hasHoliday = items.holiday != null
                    )
                }
            )

            // Upcoming Holidays Row
            if (upcomingHolidays.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Official University Holidays",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "$holidaySyncStatus • ${upcomingHolidays.size} scheduled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.syncOfficialHolidays() },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_sync_holidays")
                    ) {
                        if (isSyncingHolidays) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Sync Official Gazette Holidays",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
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
                                // Jump calendar to holiday date
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

            Spacer(modifier = Modifier.height(10.dp))

            // Day Agenda List
            Text(
                text = "Agenda for " + SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault()).format(selectedCalendar.time),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Official Holiday Card if this day is a holiday
                if (selectedDateItems.holiday != null) {
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
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No classes, exams, attendance or AI revisions recorded on this date. 🎉",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // Render exams
                    if (selectedDateItems.assessments.isNotEmpty()) {
                        item { Text("Assessments & Exams", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error) }
                        items(selectedDateItems.assessments, key = { "calendar_assessment_${it.id}" }) { exam ->
                            AgendaRow(title = exam.title, subtitle = "${exam.subject} • ${exam.type}", icon = Icons.Default.Warning, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    // Render assignments
                    if (selectedDateItems.assignments.isNotEmpty()) {
                        item { Text("Assignments Due", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary) }
                        items(selectedDateItems.assignments, key = { "calendar_assignment_${it.id}" }) { asg ->
                            AgendaRow(title = asg.title, subtitle = "${asg.subject} • ${asg.type}", icon = Icons.Default.Assignment, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Render study goals
                    if (selectedDateItems.studyTasks.isNotEmpty()) {
                        item { Text("Study Goals", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.secondary) }
                        items(selectedDateItems.studyTasks, key = { "calendar_study_${it.id}" }) { goal ->
                            AgendaRow(title = goal.title, subtitle = "${goal.subject} • Target: ${goal.targetMinutes}m", icon = Icons.Default.MenuBook, color = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    // Render classes
                    if (selectedDateItems.classes.isNotEmpty()) {
                        item { Text("Classes Scheduled", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground) }
                        items(selectedDateItems.classes.sortedBy { it.periodNumber }, key = { "calendar_class_${it.id}" }) { cls ->
                            AgendaRow(title = cls.subject, subtitle = "Period ${cls.periodNumber} • ${cls.startTime} - ${cls.endTime} • Room ${cls.room ?: "LT"}", icon = Icons.Default.School, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Render Attendance Records (Academic Journal)
                    if (dayAttendance.isNotEmpty()) {
                        item { Text("Daily Attendance", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF2E7D32)) }
                        items(dayAttendance, key = { "calendar_att_${it.id}" }) { att ->
                            val (statusText, statusIcon, statusColor) = when {
                                att.status == "NO_CLASS" -> Triple("NO CLASS", Icons.Default.Block, Color(0xFFE65100))
                                att.isPresent || att.status == "PRESENT" -> Triple("PRESENT", Icons.Default.CheckCircle, Color(0xFF2E7D32))
                                else -> Triple("ABSENT", Icons.Default.Cancel, Color(0xFFC62828))
                            }
                            AgendaRow(
                                title = att.subject,
                                subtitle = "Marked $statusText • Time: ${att.classTime ?: "Scheduled Time"}",
                                icon = statusIcon,
                                color = statusColor
                            )
                        }
                    }

                    // Render AI Revisions
                    if (dayRevisions.isNotEmpty()) {
                        item { Text("AI Lecture Revisions & Test Sets", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.tertiary) }
                        items(dayRevisions, key = { "calendar_rev_${it.id}" }) { rev ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (rev.periodNumber > 0) "🧠 ${rev.subject} (Period ${rev.periodNumber})" else "🧠 ${rev.subject}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        Text(
                                            text = if (rev.classTime.isNotBlank()) rev.classTime else rev.studentExplanation.take(25),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = rev.aiSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (rev.keyPoints.isNotBlank()) {
                                        Text(
                                            text = "Key Concepts Extracted",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        rev.keyPoints.split(Regex("[|\n]")).forEach { pt ->
                                            if (pt.isNotBlank()) {
                                                Text(
                                                    text = "• ${pt.trim()}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    if (rev.revisionQuestions.isNotBlank()) {
                                        Text(
                                            text = "Rapid-Fire Test Questions",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        rev.revisionQuestions.split(Regex("[|\n]")).forEach { q ->
                                            if (q.isNotBlank()) {
                                                Text(
                                                    text = "❓ ${q.trim()}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
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

@Composable
fun OfficialHolidayAgendaCard(
    holiday: UniversityHoliday,
    collegeName: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFEF3C7),
        border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("official_holiday_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Official University Holiday",
                        style = MaterialTheme.typography.labelMedium.copy(
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
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF78350F)
                )
            )

            Text(
                text = holiday.description,
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF92400E))
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EventBusy,
                    contentDescription = null,
                    tint = Color(0xFFB45309),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Theory classes, practicals, and clinical postings suspended.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF78350F)
                    )
                )
            }
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.width(180.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (item.daysRemaining <= 1) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = item.relativeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (item.daysRemaining <= 1) Color(0xFF92400E) else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                val dateLabel = remember(item.holiday.date) {
                    try {
                        val inFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val outFormat = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
                        inFormat.parse(item.holiday.date)?.let { outFormat.format(it) } ?: item.holiday.date
                    } catch (e: Exception) {
                        item.holiday.date
                    }
                }
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.holiday.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.holiday.type.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<CollegeCategory?>(null) }

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
                    placeholder = { Text("Search 40+ medical & AYUSH colleges...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Chips
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
                            label = { Text("AIIMS / INI") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
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
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

data class DailyItems(
    val assignments: List<Assignment>,
    val assessments: List<Assessment>,
    val studyTasks: List<StudyTask>,
    val classes: List<TimetableClass>,
    val holiday: UniversityHoliday? = null
) {
    fun isEmpty() = assignments.isEmpty() && assessments.isEmpty() && studyTasks.isEmpty() && classes.isEmpty() && holiday == null
}

data class CalendarDots(
    val hasClasses: Boolean,
    val hasAssignments: Boolean,
    val hasAssessments: Boolean,
    val hasHoliday: Boolean = false
)

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
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                dots.hasHoliday -> Color(0xFFFEF3C7)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
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
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                dots.hasHoliday -> Color(0xFF92400E)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        // Indicators Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (dots.hasHoliday) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else Color(0xFFF59E0B))
                                )
                            }
                            if (dots.hasAssessments) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else MaterialTheme.colorScheme.error)
                                )
                            }
                            if (dots.hasAssignments) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else MaterialTheme.colorScheme.primary)
                                )
                            }
                            if (dots.hasClasses) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else MaterialTheme.colorScheme.secondary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgendaRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
