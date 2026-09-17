package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Assignment
import com.example.data.model.Assessment
import com.example.data.model.StudyTask
import com.example.data.model.TimetableClass
import com.example.data.university.UniversityHoliday
import com.example.util.OverallAttendanceSummary
import com.example.ui.viewmodel.PlannerViewModel
import com.example.ui.viewmodel.Screen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: PlannerViewModel,
    modifier: Modifier = Modifier
) {
    val course by viewModel.selectedCourse.collectAsStateWithLifecycle()
    val liveClock by viewModel.currentTimeOfDay.collectAsStateWithLifecycle()
    val timetableClasses by viewModel.timetable.collectAsStateWithLifecycle()
    val exams by viewModel.exams.collectAsStateWithLifecycle()
    val liveSchedule = remember(timetableClasses, liveClock) { viewModel.getLiveClassSchedule() }

    val studentName by viewModel.studentName.collectAsStateWithLifecycle()
    val studentDpUrl by viewModel.studentDpUrl.collectAsStateWithLifecycle()
    val studentDpPreset by viewModel.studentDpPreset.collectAsStateWithLifecycle()

    var showPhotoSourceChooser by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val tempUri = remember {
        val tempFile = java.io.File(context.cacheDir, "temp_profile_capture_dashboard.jpg")
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempFile
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.updateProfilePictureFromUri(tempUri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.updateProfilePictureFromUri(uri)
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.updateProfilePictureFromUri(uri)
        }
    }

    if (showPhotoSourceChooser) {
        ProfilePictureSourceDialog(
            onDismiss = { showPhotoSourceChooser = false },
            onCameraSelect = {
                showPhotoSourceChooser = false
                cameraLauncher.launch(tempUri)
            },
            onGallerySelect = {
                showPhotoSourceChooser = false
                galleryLauncher.launch("image/*")
            },
            onFilesSelect = {
                showPhotoSourceChooser = false
                fileLauncher.launch("image/*")
            }
        )
    }

    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val assessments by viewModel.assessments.collectAsStateWithLifecycle()
    val studyTasks by viewModel.studyTasks.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val todayHoliday by viewModel.todayHoliday.collectAsStateWithLifecycle()
    val tomorrowHoliday by viewModel.tomorrowHoliday.collectAsStateWithLifecycle()
    val studentCollege by viewModel.studentCollege.collectAsStateWithLifecycle()

    val today = remember(liveClock) { Calendar.getInstance() }

    val todayPendingItems = remember(assignments, today) {
        val combined = mutableListOf<Pair<TodayPendingItem, Long>>()

        assignments.filter { it.status == "Pending" }.forEach { asg ->
            val daysRemaining = getDaysRemaining(asg.dueDate)
            val dueLabel = when {
                daysRemaining < 0 -> "Overdue by ${-daysRemaining}d"
                daysRemaining == 0 -> "Due Today"
                daysRemaining == 1 -> "Due Tomorrow"
                else -> "Due in $daysRemaining days"
            }
            combined.add(
                Pair(
                    TodayPendingItem(
                        id = "assignment_${asg.id}",
                        title = asg.title,
                        subtitle = "${asg.subject} • ${asg.type} • $dueLabel",
                        priority = asg.priority,
                        onToggle = { viewModel.toggleAssignment(asg) }
                    ),
                    asg.dueDate
                )
            )
        }

        combined.sortBy { it.second }
        combined.map { it.first }
    }

    val upcomingEvents = remember(exams, assessments, today) {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val examCandidates = exams.filter { it.status == "Upcoming" && it.date >= todayStart }.map {
            UpcomingExamEvent(
                title = it.title,
                date = it.date,
                subject = it.subject,
                type = "Exam"
            )
        }

        val assessmentCandidates = assessments.filter {
            it.status == "Upcoming" && it.date >= todayStart && (
                it.type.contains("Internal", ignoreCase = true) ||
                it.type.contains("Viva", ignoreCase = true) ||
                it.type.contains("Practical", ignoreCase = true) ||
                it.type.contains("Exam", ignoreCase = true) ||
                it.type.contains("Test", ignoreCase = true)
            )
        }.map {
            UpcomingExamEvent(
                title = it.title,
                date = it.date,
                subject = it.subject,
                type = when {
                    it.type.contains("Viva", ignoreCase = true) -> "Viva"
                    it.type.contains("Practical", ignoreCase = true) -> "Practical Exam"
                    it.type.contains("Internal", ignoreCase = true) -> "Internal Assessment"
                    else -> "Exam"
                }
            )
        }

        (examCandidates + assessmentCandidates)
            .distinctBy { it.title.lowercase().trim() + "_" + it.subject.lowercase().trim() + "_" + it.date }
            .sortedBy { it.date }
    }

    var showNotificationsTray by remember { mutableStateOf(false) }
    var showPendingDialog by remember { mutableStateOf(false) }
    var showUpcomingDialog by remember { mutableStateOf(false) }
    var showRemainingDialog by remember { mutableStateOf(false) }

    val todayDateStr = remember(liveClock) {
        val format = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
        format.format(Date())
    }

    val unreadNotificationsCount = notifications.count { !it.isRead }

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
            // Dashboard Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserProfileAvatar(
                        studentDpUrl = studentDpUrl,
                        studentDpPreset = studentDpPreset,
                        studentName = studentName,
                        size = 52.dp,
                        modifier = Modifier.padding(end = 12.dp),
                        onClick = { showPhotoSourceChooser = true }
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = todayDateStr,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (studentName.isNotBlank()) "Hello, $studentName! 👋" else "Hello! 👋",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                                fontSize = 18.sp,
                                lineHeight = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = course?.displayName ?: "No Course Selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Settings icon button
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Settings) },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("header_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Bell icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { showNotificationsTray = !showNotificationsTray }
                            .testTag("notifications_bell")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notification Inbox",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        if (unreadNotificationsCount > 0) {
                            Badge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                            ) {
                                Text(unreadNotificationsCount.toString())
                            }
                        }
                    }
                }
            }

            // Main Contents Scroller
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // University Holiday Alert Card
                if (tomorrowHoliday != null) {
                    item {
                        UniversityHolidayAlertCard(
                            isTomorrow = true,
                            holiday = tomorrowHoliday!!,
                            collegeName = studentCollege,
                            onViewCalendar = { viewModel.navigateTo(Screen.Calendar) }
                        )
                    }
                } else if (todayHoliday != null) {
                    item {
                        UniversityHolidayAlertCard(
                            isTomorrow = false,
                            holiday = todayHoliday!!,
                            collegeName = studentCollege,
                            onViewCalendar = { viewModel.navigateTo(Screen.Calendar) }
                        )
                    }
                }

                // Class Schedule Panel (Interactive Widget)
                item {
                    LiveTimetableWidget(liveSchedule, viewModel)
                }

                // Stats Dashboard summary cards
                item {
                    val overallAttendanceSummary by viewModel.overallAttendanceSummary.collectAsStateWithLifecycle()
                    StatsMetricsGrid(
                        assignments = assignments,
                        assessments = assessments,
                        remainingClassesCount = liveSchedule.remainingClasses.size,
                        overallAttendanceSummary = overallAttendanceSummary,
                        onPendingClick = { showPendingDialog = true },
                        onUpcomingClick = { showUpcomingDialog = true },
                        onAttendanceClick = { viewModel.navigateTo(Screen.Attendance) }
                    )
                }

                // Today's Assignments Section
                item {
                    SectionHeader(title = "Active Assignments", icon = Icons.Default.Assignment)
                }

                if (todayPendingItems.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No pending assignments.",
                            desc = "No assignments due for now.",
                            icon = Icons.Default.CheckCircle
                        )
                    }
                } else {
                    items(todayPendingItems, key = { it.id }) { item ->
                        TodayFocusRow(
                            title = item.title,
                            subtitle = item.subtitle,
                            priority = item.priority,
                            testTag = "toggle_${item.id}",
                            onCheckChanged = item.onToggle
                        )
                    }
                }

                // Upcoming Assessments & Exams Section
                item {
                    SectionHeader(title = "Upcoming Assessments", icon = Icons.Default.EventNote)
                }

                if (upcomingEvents.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No upcoming assessments.",
                            desc = "No assessments or tests registered for now.",
                            icon = Icons.Default.SentimentSatisfiedAlt
                        )
                    }
                } else {
                    items(upcomingEvents) { event ->
                        UpcomingExamCard(event = event)
                    }
                }

                // Study Goals Progress Section
                item {
                    SectionHeader(title = "Daily Revision Goals", icon = Icons.Default.TrackChanges)
                }

                val activeStudyTasks = studyTasks.filter { it.progress < 100 }

                if (activeStudyTasks.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "All revision chapters complete!",
                            desc = "Great job! Add new tasks in the Study Planner.",
                            icon = Icons.Default.EmojiEvents
                        )
                    }
                } else {
                    items(activeStudyTasks, key = { it.id }) { task ->
                        StudyTaskDashboardCard(task, onProgressChanged = { progress ->
                            viewModel.updateStudyProgress(task.id, progress)
                        })
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom navbar
                }
            }
        }

        // Expanded notification inbox sheet from the top-right bell
        AnimatedVisibility(
            visible = showNotificationsTray,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .shadow(12.dp, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MedPulse Alerts Inbox",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row {
                            TextButton(onClick = { viewModel.clearInbox() }) {
                                Text("Clear All")
                            }
                            IconButton(onClick = { showNotificationsTray = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close notifications")
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    if (notifications.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No notifications yet. Excellent work! 🎉",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(notifications) { alert ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (alert.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                            )
                                            .clickable { viewModel.markNotificationRead(alert.id) }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val icon = when (alert.type) {
                                            "class" -> Icons.Default.School
                                            "assignment" -> Icons.Default.Assignment
                                            "exam" -> Icons.Default.Assessment
                                            else -> Icons.Default.Notifications
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (alert.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = alert.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = alert.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showPendingDialog) {
            PendingAssignmentsDialog(
                assignments = assignments,
                onDismiss = { showPendingDialog = false },
                onToggle = { asg ->
                    viewModel.toggleAssignment(asg)
                    showPendingDialog = false
                }
            )
        }

        if (showUpcomingDialog) {
            UpcomingAssessmentsDialog(
                assessments = assessments,
                onDismiss = { showUpcomingDialog = false },
                onToggle = { ass ->
                    viewModel.toggleAssessment(ass)
                    showUpcomingDialog = false
                }
            )
        }

        if (showRemainingDialog) {
            RemainingClassesDialog(
                classes = liveSchedule.remainingClasses,
                onDismiss = { showRemainingDialog = false }
            )
        }
    }
}

@Composable
fun LiveTimetableWidget(
    schedule: PlannerViewModel.DayClassSchedule,
    viewModel: PlannerViewModel
) {
    val context = LocalContext.current
    val attendanceRecords by viewModel.allAttendanceRecords.collectAsStateWithLifecycle()
    val allRevisions by viewModel.allRevisions.collectAsStateWithLifecycle()
    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
    val todayStr = remember { sdf.format(java.util.Date()) }

    val todayAttendance = remember(attendanceRecords, todayStr) {
        attendanceRecords.filter { it.dateString == todayStr }
    }

    var selectedClassForRevision by remember { mutableStateOf<TimetableClass?>(null) }
    var explanationText by remember { mutableStateOf("") }
    
    val isGeneratingRevision by viewModel.isGeneratingRevision.collectAsStateWithLifecycle()
    val lastGeneratedRevision by viewModel.lastGeneratedRevision.collectAsStateWithLifecycle()

    // Find if the currently selected class has an existing revision recorded for today
    val existingClassRevision = remember(selectedClassForRevision, allRevisions, todayStr) {
        val cls = selectedClassForRevision ?: return@remember null
        allRevisions.firstOrNull { rev ->
            rev.dateString == todayStr &&
            rev.subject.equals(cls.subject, ignoreCase = true) &&
            ((cls.periodNumber > 0 && rev.periodNumber == cls.periodNumber) ||
             (rev.classTime.isNotBlank() && rev.classTime == "${cls.startTime}-${cls.endTime}") ||
             (rev.periodNumber == 0 && rev.classTime.isBlank() && rev.subject.equals(cls.subject, ignoreCase = true)))
        }
    }

    // Active revision strictly matching this exact class and period/hour
    val currentRevisionForClass = remember(selectedClassForRevision, lastGeneratedRevision, existingClassRevision) {
        val cls = selectedClassForRevision ?: return@remember null
        val generated = lastGeneratedRevision?.takeIf {
            it.subject.equals(cls.subject, ignoreCase = true) &&
            (cls.periodNumber == 0 || it.periodNumber == cls.periodNumber || it.classTime == "${cls.startTime}-${cls.endTime}")
        }
        generated ?: existingClassRevision
    }

    LaunchedEffect(selectedClassForRevision) {
        val cls = selectedClassForRevision
        if (cls != null) {
            viewModel.clearLastGeneratedRevision()
            val existing = allRevisions.firstOrNull { rev ->
                rev.dateString == todayStr &&
                rev.subject.equals(cls.subject, ignoreCase = true) &&
                ((cls.periodNumber > 0 && rev.periodNumber == cls.periodNumber) ||
                 (rev.classTime.isNotBlank() && rev.classTime == "${cls.startTime}-${cls.endTime}") ||
                 (rev.periodNumber == 0 && rev.classTime.isBlank() && rev.subject.equals(cls.subject, ignoreCase = true)))
            }
            explanationText = existing?.studentExplanation ?: ""
        } else {
            explanationText = ""
            viewModel.clearLastGeneratedRevision()
        }
    }

    if (selectedClassForRevision != null) {
        val cls = selectedClassForRevision!!
        AlertDialog(
            onDismissRequest = {
                selectedClassForRevision = null
                explanationText = ""
                viewModel.clearLastGeneratedRevision()
            },
            title = {
                Column {
                    Text(
                        text = "AI Revision: ${cls.subject}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Period ${cls.periodNumber} • ${cls.startTime} - ${cls.endTime}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentRevisionForClass == null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✨ New Session for this Hour: Dictate or enter today's topic and what you learned in ${cls.subject} (Period ${cls.periodNumber}). MedPulse AI will create high-yield summaries, key points, and revision test questions specifically for this class hour.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Class notes & AI revision set for Period ${cls.periodNumber}. You can review below or update your topic notes to regenerate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = explanationText,
                        onValueChange = { explanationText = it },
                        label = { Text("Today's Topic / Lecture Notes (Period ${cls.periodNumber})") },
                        placeholder = { Text("Enter what was taught in ${cls.subject} during this hour...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .testTag("revision_explanation_input"),
                        maxLines = 8
                    )

                    if (isGeneratingRevision) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "AI is analyzing medical concepts for Period ${cls.periodNumber}...",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (currentRevisionForClass != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("✨ AI CLINICAL NOTES SUMMARY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                    Text("Period ${currentRevisionForClass.periodNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(currentRevisionForClass.aiSummary, style = MaterialTheme.typography.bodySmall)
                                
                                if (currentRevisionForClass.keyPoints.isNotBlank()) {
                                    Text("📌 KEY POINTS EXTRACTED", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                    currentRevisionForClass.keyPoints.split(Regex("[|\n]")).forEach { pt ->
                                        if (pt.isNotBlank()) {
                                            Text("• ${pt.trim()}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }

                                if (currentRevisionForClass.revisionQuestions.isNotBlank()) {
                                    Text("❓ REVISION QUESTIONS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                    currentRevisionForClass.revisionQuestions.split(Regex("[|\n]")).forEach { q ->
                                        if (q.isNotBlank()) {
                                            Text("❓ ${q.trim()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentRevisionForClass == null) {
                        Button(
                            onClick = {
                                if (explanationText.isNotBlank()) {
                                    viewModel.generateClassRevision(
                                        subject = cls.subject,
                                        explanation = explanationText,
                                        periodNumber = cls.periodNumber,
                                        classTime = "${cls.startTime}-${cls.endTime}"
                                    )
                                }
                            },
                            enabled = explanationText.isNotBlank() && !isGeneratingRevision,
                            modifier = Modifier.testTag("generate_ai_revision_btn")
                        ) {
                            Text("Generate AI Notes")
                        }
                    } else {
                        if (explanationText != currentRevisionForClass.studentExplanation && explanationText.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.generateClassRevision(
                                        subject = cls.subject,
                                        explanation = explanationText,
                                        periodNumber = cls.periodNumber,
                                        classTime = "${cls.startTime}-${cls.endTime}"
                                    )
                                },
                                enabled = !isGeneratingRevision
                            ) {
                                Text("Update Notes")
                            }
                        }
                        Button(
                            onClick = {
                                selectedClassForRevision = null
                                explanationText = ""
                                viewModel.clearLastGeneratedRevision()
                            }
                        ) {
                            Text("Done")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedClassForRevision = null
                        explanationText = ""
                        viewModel.clearLastGeneratedRevision()
                    }
                ) {
                    Text(if (currentRevisionForClass == null) "Cancel" else "Close")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_timetable_widget")
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = "Today's Schedule",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Today's Schedule",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (schedule.todayClasses.isEmpty()) {
                Text(
                    text = "No classes scheduled for today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    schedule.todayClasses.forEach { cls ->
                        val isLunch = cls.subject.contains("lunch", ignoreCase = true)
                        val attendance = if (isLunch) null else todayAttendance.find { it.subject.lowercase() == cls.subject.lowercase() }
                        
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isLunch) MaterialTheme.colorScheme.tertiaryContainer 
                                                else Color(android.graphics.Color.parseColor(cls.colorHex ?: "#4CAF50"))
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (isLunch) Icons.Default.Restaurant else Icons.Default.School,
                                            contentDescription = null,
                                            tint = if (isLunch) MaterialTheme.colorScheme.onTertiaryContainer else Color.White,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .align(Alignment.Center)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cls.subject,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${cls.startTime} - ${cls.endTime} • Room: ${cls.room ?: "L-1"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        
                                        // Badge for Clinical, Non-Lecture, Practical, Yoga
                                        val lowerSubject = cls.subject.lowercase()
                                        if (lowerSubject.contains("non-lecture") || lowerSubject.contains("posting") || lowerSubject.contains("clinical") || lowerSubject.contains("practical") || lowerSubject.contains("yoga")) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                color = if (lowerSubject.contains("clinical") || lowerSubject.contains("posting")) 
                                                    MaterialTheme.colorScheme.tertiaryContainer 
                                                else 
                                                    MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (lowerSubject.contains("clinical") || lowerSubject.contains("posting")) 
                                                        "🏥 CLINICAL" 
                                                    else if (lowerSubject.contains("yoga"))
                                                        "🧘 YOGA"
                                                    else 
                                                        "🔬 PRACTICAL",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f),
                                                    color = if (lowerSubject.contains("clinical") || lowerSubject.contains("posting")) 
                                                        MaterialTheme.colorScheme.onTertiaryContainer 
                                                    else 
                                                        MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Attendance State Indicator
                                    if (attendance != null && !isLunch) {
                                        val (badgeColor, badgeTextColor, label) = when {
                                            attendance.status == "NO_CLASS" -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "No Class")
                                            attendance.isPresent || attendance.status == "PRESENT" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Present")
                                            else -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Absent")
                                        }

                                        Surface(
                                            color = badgeColor,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = badgeTextColor,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                if (isLunch) {
                                    // Lunch is a break, show cozy break description or text
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Break Time ☕",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                } else if (attendance == null) {
                                    val calendar = java.util.Calendar.getInstance()
                                    val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
                                    val classStartMinutes = viewModel.parseTimeToMinutes(cls.startTime)
                                    val isClassStarted = currentMinutes >= classStartMinutes

                                    if (!isClassStarted) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Lock,
                                                        contentDescription = "Attendance Locked",
                                                        modifier = Modifier.size(13.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Opens at ${cls.startTime}",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        Toast.makeText(context, "Attendance for ${cls.subject} only opens after ${cls.startTime}.", Toast.LENGTH_SHORT).show()
                                                    },
                                                    enabled = false,
                                                    colors = ButtonDefaults.textButtonColors(
                                                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.testTag("btn_absent_locked_${cls.id}")
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Absent", style = MaterialTheme.typography.labelMedium)
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                FilledTonalButton(
                                                    onClick = {
                                                        Toast.makeText(context, "Attendance for ${cls.subject} only opens after ${cls.startTime}.", Toast.LENGTH_SHORT).show()
                                                    },
                                                    enabled = false,
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.testTag("btn_present_locked_${cls.id}")
                                                ) {
                                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Present", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.markAttendance(cls.subject, isPresent = false, classTime = "${cls.startTime}-${cls.endTime}", status = "NO_CLASS", startTime = cls.startTime, endTime = cls.endTime) },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("No Class", style = MaterialTheme.typography.labelMedium)
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            TextButton(
                                                onClick = { viewModel.markAttendance(cls.subject, isPresent = false, classTime = "${cls.startTime}-${cls.endTime}", status = "ABSENT", startTime = cls.startTime, endTime = cls.endTime) },
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Mark Absent", style = MaterialTheme.typography.labelMedium)
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            FilledTonalButton(
                                                onClick = { viewModel.markAttendance(cls.subject, isPresent = true, classTime = "${cls.startTime}-${cls.endTime}", status = "PRESENT", startTime = cls.startTime, endTime = cls.endTime) },
                                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Mark Present", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                } else {
                                    val calendar = java.util.Calendar.getInstance()
                                    val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
                                    val classStartMinutes = viewModel.parseTimeToMinutes(cls.startTime)
                                    val isClassStarted = currentMinutes >= classStartMinutes

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        var showStatusMenu by remember { mutableStateOf(false) }
                                        Box {
                                            OutlinedButton(
                                                onClick = {
                                                    if (isClassStarted) {
                                                        showStatusMenu = true
                                                    } else {
                                                        Toast.makeText(context, "Attendance status cannot be modified before class time (${cls.startTime}).", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isClassStarted) 0.5f else 0.25f)),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .height(34.dp)
                                                    .testTag("btn_change_status_${cls.id}")
                                            ) {
                                                Icon(
                                                    if (isClassStarted) Icons.Default.Edit else Icons.Default.Lock,
                                                    contentDescription = "Change Status",
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    if (isClassStarted) "Change Status" else "Locked until ${cls.startTime}",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                                                )
                                            }
                                            if (isClassStarted) {
                                                DropdownMenu(
                                                    expanded = showStatusMenu,
                                                    onDismissRequest = { showStatusMenu = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Mark Present ✓") },
                                                        onClick = {
                                                            showStatusMenu = false
                                                            viewModel.markAttendance(cls.subject, isPresent = true, classTime = "${cls.startTime}-${cls.endTime}", status = "PRESENT", startTime = cls.startTime, endTime = cls.endTime)
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32)) }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Mark Absent ✗") },
                                                        onClick = {
                                                            showStatusMenu = false
                                                            viewModel.markAttendance(cls.subject, isPresent = false, classTime = "${cls.startTime}-${cls.endTime}", status = "ABSENT", startTime = cls.startTime, endTime = cls.endTime)
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFC62828)) }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("No Class 🚫") },
                                                        onClick = {
                                                            showStatusMenu = false
                                                            viewModel.markAttendance(cls.subject, isPresent = false, classTime = "${cls.startTime}-${cls.endTime}", status = "NO_CLASS", startTime = cls.startTime, endTime = cls.endTime)
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFE65100)) }
                                                    )
                                                }
                                            }
                                        }

                                        if (attendance.isPresent || attendance.status == "PRESENT") {
                                            val hasPeriodRevision = allRevisions.any { rev ->
                                                rev.dateString == todayStr &&
                                                rev.subject.equals(cls.subject, ignoreCase = true) &&
                                                ((cls.periodNumber > 0 && rev.periodNumber == cls.periodNumber) ||
                                                 (rev.classTime.isNotBlank() && rev.classTime == "${cls.startTime}-${cls.endTime}") ||
                                                 (rev.periodNumber == 0 && rev.classTime.isBlank()))
                                            }

                                            FilledTonalButton(
                                                onClick = {
                                                    selectedClassForRevision = cls
                                                },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = if (hasPeriodRevision) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = if (hasPeriodRevision) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .height(34.dp)
                                                    .testTag("btn_ai_revision_${cls.id}")
                                            ) {
                                                Icon(
                                                    imageVector = if (hasPeriodRevision) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = if (hasPeriodRevision) "AI Notes ✓" else "+ AI Notes",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsMetricsGrid(
    assignments: List<Assignment>,
    assessments: List<Assessment>,
    remainingClassesCount: Int,
    overallAttendanceSummary: OverallAttendanceSummary,
    onPendingClick: () -> Unit,
    onUpcomingClick: () -> Unit,
    onAttendanceClick: () -> Unit
) {
    val pendingAsg = assignments.count { it.status == "Pending" }
    val upcomingExm = assessments.count { it.status == "Upcoming" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            title = "Assignments",
            value = pendingAsg.toString(),
            subtitle = "Pending",
            color = MaterialTheme.colorScheme.errorContainer,
            textColor = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Default.Assignment,
            modifier = Modifier
                .weight(1f)
                .clickable { onPendingClick() }
        )
        StatCard(
            title = "Assessments",
            value = upcomingExm.toString(),
            subtitle = "Upcoming",
            color = MaterialTheme.colorScheme.tertiaryContainer,
            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = Icons.Default.Assessment,
            modifier = Modifier
                .weight(1f)
                .clickable { onUpcomingClick() }
        )
        StatCard(
            title = "Attendance",
            value = if (overallAttendanceSummary.hasData) String.format(java.util.Locale.getDefault(), "%.0f%%", overallAttendanceSummary.percentage) else "—",
            subtitle = if (overallAttendanceSummary.hasData) overallAttendanceSummary.statusLabel else "No records",
            color = MaterialTheme.colorScheme.secondaryContainer,
            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
            icon = Icons.Default.CheckCircle,
            modifier = Modifier
                .weight(1f)
                .clickable { onAttendanceClick() }
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    textColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = textColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun AssignmentDashboardRow(
    assignment: Assignment,
    onCheckChanged: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = assignment.status == "Completed",
                onCheckedChange = { onCheckChanged() },
                modifier = Modifier.testTag("toggle_assignment_${assignment.id}")
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assignment.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (assignment.status == "Completed") androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${assignment.subject} • ${assignment.type}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = when (assignment.priority) {
                    "High" -> MaterialTheme.colorScheme.errorContainer
                    "Medium" -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = assignment.priority,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = when (assignment.priority) {
                        "High" -> MaterialTheme.colorScheme.onErrorContainer
                        "Medium" -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AssessmentDashboardCard(assessment: Assessment) {
    val countdownDays = remember(assessment.date) {
        val days = getDaysRemaining(assessment.date)
        when {
            days < 0 -> "Expired"
            days == 0 -> "Today"
            days == 1 -> "In 1 day"
            else -> "In $days days"
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = assessment.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${assessment.subject} • ${assessment.type}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = countdownDays,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun StudyTaskDashboardCard(
    task: StudyTask,
    onProgressChanged: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${task.subject} • Goal: ${task.targetMinutes}m revision",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${task.progress}%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Slider(
                value = task.progress.toFloat(),
                onValueChange = { onProgressChanged(it.toInt()) },
                valueRange = 0f..100f,
                steps = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("study_progress_slider_${task.id}")
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun UserProfileAvatar(
    studentDpUrl: String,
    studentDpPreset: String,
    studentName: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val initials = if (studentName.isNotBlank()) {
        studentName.trim().split("\\s+".toRegex()).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
    } else {
        "MS"
    }

    val clickableModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Box(
        modifier = clickableModifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (studentDpUrl.isNotBlank()) {
            coil.compose.AsyncImage(
                model = studentDpUrl,
                contentDescription = "Profile Picture",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.secondaryContainer)
            )
        } else {
            when (studentDpPreset) {
                "doctor_male" -> PresetAvatarImage(Icons.Default.Face, "Male Doctor preset avatar")
                "doctor_female" -> PresetAvatarImage(Icons.Default.Face, "Female Doctor preset avatar") // fallback if FaceRetouchingNatural not in default set
                "nurse" -> PresetAvatarImage(Icons.Default.Healing, "Nurse preset avatar")
                "surgeon" -> PresetAvatarImage(Icons.Default.MedicalServices, "Surgeon preset avatar")
                "scientist" -> PresetAvatarImage(Icons.Default.Science, "Scientist preset avatar")
                "stethoscope" -> PresetAvatarImage(Icons.Default.MedicalServices, "Stethoscope preset avatar")
                else -> {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun PresetAvatarImage(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxSize(0.6f)
    )
}

data class TodayPendingItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val priority: String,
    val onToggle: () -> Unit
)

data class UpcomingExamEvent(
    val title: String,
    val date: Long,
    val subject: String,
    val type: String
)

@Composable
fun TodayFocusRow(
    title: String,
    subtitle: String,
    priority: String,
    testTag: String,
    onCheckChanged: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = false,
                onCheckedChange = { onCheckChanged() },
                modifier = Modifier.testTag(testTag)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = when (priority) {
                    "High" -> MaterialTheme.colorScheme.errorContainer
                    "Medium" -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = priority,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = when (priority) {
                        "High" -> MaterialTheme.colorScheme.onErrorContainer
                        "Medium" -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun UpcomingExamCard(event: UpcomingExamEvent) {
    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy", Locale.US) }
    val days = getDaysRemaining(event.date)
    val daysStr = when {
        days == 0 -> "Today"
        days == 1 -> "1 Day Remaining"
        else -> "$days Days Remaining"
    }

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("upcoming_exam_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Upcoming ${event.type}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = daysStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = event.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Text(
                text = event.subject,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = dateFormat.format(Date(event.date)),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun getDaysRemaining(targetTimeMs: Long): Int {
    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val targetCal = Calendar.getInstance().apply {
        timeInMillis = targetTimeMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val diffMs = targetCal.timeInMillis - todayCal.timeInMillis
    return (diffMs / (24 * 60 * 60 * 1000L)).toInt()
}

@Composable
fun PendingAssignmentsDialog(
    assignments: List<Assignment>,
    onDismiss: () -> Unit,
    onToggle: (Assignment) -> Unit
) {
    val pending = remember(assignments) { assignments.filter { it.status == "Pending" } }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Assignment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text("Pending Assignments", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        text = {
            if (pending.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No pending assignments!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pending, key = { it.id }) { asg ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = false,
                                    onCheckedChange = { onToggle(asg) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = asg.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${asg.subject} • ${asg.type}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Due: ${dateFormat.format(Date(asg.dueDate))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun UpcomingAssessmentsDialog(
    assessments: List<Assessment>,
    onDismiss: () -> Unit,
    onToggle: (Assessment) -> Unit
) {
    val upcoming = remember(assessments) { assessments.filter { it.status == "Upcoming" } }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Upcoming Assessments", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        text = {
            if (upcoming.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No upcoming assessments!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(upcoming, key = { it.id }) { ass ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = false,
                                    onCheckedChange = { onToggle(ass) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ass.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${ass.subject} • ${ass.type}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Date: ${dateFormat.format(Date(ass.date))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (!ass.syllabus.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Syllabus: ${ass.syllabus}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun RemainingClassesDialog(
    classes: List<TimetableClass>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text("Remaining Classes Today", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        text = {
            if (classes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No remaining classes for today!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(classes, key = { it.id }) { cls ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp).fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Period ${cls.periodNumber}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "${cls.startTime} - ${cls.endTime}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = cls.subject,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                if (!cls.teacherName.isNullOrEmpty() || !cls.room.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = listOfNotNull(
                                            cls.teacherName?.let { "Dr. $it" },
                                            cls.room?.let { "Room: $it" }
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun UniversityHolidayAlertCard(
    isTomorrow: Boolean,
    holiday: UniversityHoliday,
    collegeName: String,
    onViewCalendar: () -> Unit
) {
    Surface(
        onClick = onViewCalendar,
        shape = RoundedCornerShape(18.dp),
        color = if (isTomorrow) Color(0xFFFEF3C7) else Color(0xFFE0F2FE),
        border = BorderStroke(1.5.dp, if (isTomorrow) Color(0xFFF59E0B) else Color(0xFF38BDF8)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isTomorrow) "tomorrow_holiday_alert" else "today_holiday_alert")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isTomorrow) Color(0xFFF59E0B) else Color(0xFF0284C7),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isTomorrow) Icons.Default.Celebration else Icons.Default.BeachAccess,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isTomorrow) "🌴 TOMORROW IS A HOLIDAY" else "🎉 TODAY IS A HOLIDAY",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isTomorrow) Color(0xFFB45309) else Color(0xFF0369A1)
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${holiday.name} • Classes Suspended",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isTomorrow) Color(0xFF78350F) else Color(0xFF0C4A6E)
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Per your university schedule. Tap to open calendar.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isTomorrow) Color(0xFF92400E) else Color(0xFF075985)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View Calendar",
                tint = if (isTomorrow) Color(0xFFB45309) else Color(0xFF0369A1),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
