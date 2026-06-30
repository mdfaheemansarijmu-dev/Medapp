package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Assignment
import com.example.data.model.Assessment
import com.example.data.model.StudyTask
import com.example.data.model.TimetableClass
import com.example.ui.viewmodel.PlannerViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarScreen(
    viewModel: PlannerViewModel,
    modifier: Modifier = Modifier
) {
    val currentCalendar by viewModel.currentCalendarDate.collectAsStateWithLifecycle()
    val selectedCalendar by viewModel.selectedCalendarDate.collectAsStateWithLifecycle()

    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val assessments by viewModel.assessments.collectAsStateWithLifecycle()
    val studyTasks by viewModel.studyTasks.collectAsStateWithLifecycle()
    val timetable by viewModel.timetable.collectAsStateWithLifecycle()
    val attendanceRecords by viewModel.allAttendanceRecords.collectAsStateWithLifecycle()
    val revisions by viewModel.allRevisions.collectAsStateWithLifecycle()

    // Months and years state
    var activeMonth by remember { mutableStateOf(selectedCalendar.get(Calendar.MONTH)) }
    var activeYear by remember { mutableStateOf(selectedCalendar.get(Calendar.YEAR)) }

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

        return DailyItems(
            assignments = assignments.filter { it.dueDate in startMs until endMs },
            assessments = assessments.filter { it.date in startMs until endMs },
            studyTasks = studyTasks.filter { it.dueDate in startMs until endMs },
            classes = timetable.filter { it.dayOfWeek == dayOfWeek }
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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Academic Calendar",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Monthly view of tests and deadlines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Month navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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

            Spacer(modifier = Modifier.height(8.dp))

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
                        hasAssessments = items.assessments.isNotEmpty()
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Day Agenda List
            Text(
                text = "Agenda for " + SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault()).format(selectedCalendar.time),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isDiaryEmpty) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
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
                        item { Text("Assessments", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error) }
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
                            AgendaRow(
                                title = att.subject,
                                subtitle = "Marked ${if (att.isPresent) "PRESENT" else "ABSENT"} • Time: ${att.classTime ?: "Scheduled Time"}",
                                icon = if (att.isPresent) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                color = if (att.isPresent) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }

                    // Render AI Class Revisions (Academic Journal)
                    if (dayRevisions.isNotEmpty()) {
                        item { Text("AI Lecture Revisions & Smart Notes", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.secondary) }
                        items(dayRevisions, key = { "calendar_rev_${it.id}" }) { rev ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = rev.subject,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }

                                    Divider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f))

                                    Text(
                                        text = "Clinical Summary",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
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
                                        rev.keyPoints.split("|").forEach { pt ->
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
                                        rev.revisionQuestions.split("|").forEach { q ->
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
}

data class DailyItems(
    val assignments: List<Assignment>,
    val assessments: List<Assessment>,
    val studyTasks: List<StudyTask>,
    val classes: List<TimetableClass>
) {
    fun isEmpty() = assignments.isEmpty() && assessments.isEmpty() && studyTasks.isEmpty() && classes.isEmpty()
}

data class CalendarDots(
    val hasClasses: Boolean,
    val hasAssignments: Boolean,
    val hasAssessments: Boolean
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
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
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
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        // Indicators Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
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
