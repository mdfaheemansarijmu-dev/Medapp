package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import com.example.ui.viewmodel.PlannerViewModel
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
            "${context.packageName}.fileprovider",
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
                    Column {
                        Text(
                            text = todayDateStr,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (studentName.isNotBlank()) "Hello, $studentName! 👋" else "Hello! 👋",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                                fontSize = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = course?.displayName ?: "No Course Selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Live Local Clock Widget
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Clock",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = liveClock.ifEmpty { "00:00" },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Class Schedule Panel (Interactive Widget)
                item {
                    LiveTimetableWidget(liveSchedule, viewModel)
                }

                // Stats Dashboard summary cards
                item {
                    val overallAttendance = viewModel.getOverallAttendancePercentage()
                    StatsMetricsGrid(
                        assignments = assignments,
                        assessments = assessments,
                        remainingClassesCount = liveSchedule.remainingClasses.size,
                        overallAttendance = overallAttendance,
                        onPendingClick = { showPendingDialog = true },
                        onUpcomingClick = { showUpcomingDialog = true },
                        onRemainingClick = { showRemainingDialog = true }
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
    val attendanceRecords by viewModel.allAttendanceRecords.collectAsStateWithLifecycle()
    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
    val todayStr = remember { sdf.format(java.util.Date()) }

    val todayAttendance = remember(attendanceRecords, todayStr) {
        attendanceRecords.filter { it.dateString == todayStr }
    }

    var selectedClassForRevision by remember { mutableStateOf<TimetableClass?>(null) }
    var explanationText by remember { mutableStateOf("") }
    
    val isGeneratingRevision by viewModel.isGeneratingRevision.collectAsStateWithLifecycle()
    val lastGeneratedRevision by viewModel.lastGeneratedRevision.collectAsStateWithLifecycle()

    if (selectedClassForRevision != null) {
        AlertDialog(
            onDismissRequest = { selectedClassForRevision = null },
            title = {
                Text(
                    text = "AI Revision: ${selectedClassForRevision?.subject}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Dictate or explain what you learned in this lecture. MedPulse AI will analyze your notes, summarize them into a clinical textbook-style guide, and generate study revision questions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = explanationText,
                        onValueChange = { explanationText = it },
                        label = { Text("Your Explanation / Lecture Notes") },
                        placeholder = { Text("Today we studied the pathogenesis of atherosclerosis, LDL accumulation, foam cell formation, and fibrous plaque build up...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .testTag("revision_explanation_input"),
                        maxLines = 10
                    )

                    if (isGeneratingRevision) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "AI is analyzing medical concepts & generating revision questions...",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (lastGeneratedRevision != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("✨ AI CLINICAL NOTES SUMMARY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                Text(lastGeneratedRevision!!.aiSummary, style = MaterialTheme.typography.bodySmall)
                                
                                Text("📌 KEY POINTS EXTRACTED", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                lastGeneratedRevision!!.keyPoints.split("|").forEach { pt ->
                                    if (pt.isNotBlank()) {
                                        Text("• ${pt.trim()}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                Text("❓ REVISION QUESTIONS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                lastGeneratedRevision!!.revisionQuestions.split("|").forEach { q ->
                                    if (q.isNotBlank()) {
                                        Text("❓ ${q.trim()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
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
                    if (lastGeneratedRevision == null) {
                        Button(
                            onClick = {
                                if (explanationText.isNotBlank()) {
                                    viewModel.generateClassRevision(selectedClassForRevision!!.subject, explanationText)
                                }
                            },
                            enabled = explanationText.isNotBlank() && !isGeneratingRevision,
                            modifier = Modifier.testTag("generate_ai_revision_btn")
                        ) {
                            Text("Generate AI Notes")
                        }
                    } else {
                        Button(
                            onClick = {
                                selectedClassForRevision = null
                                explanationText = ""
                            }
                        ) {
                            Text("Save & Close")
                        }
                    }
                }
            },
            dismissButton = {
                if (lastGeneratedRevision == null) {
                    TextButton(onClick = { selectedClassForRevision = null }) {
                        Text("Cancel")
                    }
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
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Running Class",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Today's Live Classes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = viewModel.getDayName(viewModel.getCurrentDayOfWeek()),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
                                    }

                                    // Attendance State Indicator
                                    if (attendance != null && !isLunch) {
                                        val badgeColor = if (attendance.isPresent) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                        val badgeTextColor = if (attendance.isPresent) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        val label = if (attendance.isPresent) "Present" else "Absent"

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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { viewModel.markAttendance(cls.subject, isPresent = false, classTime = "${cls.startTime}-${cls.endTime}") },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Mark Absent", style = MaterialTheme.typography.labelMedium)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        FilledTonalButton(
                                            onClick = { viewModel.markAttendance(cls.subject, isPresent = true, classTime = "${cls.startTime}-${cls.endTime}") },
                                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Mark Present", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                } else if (attendance.isPresent) {
                                    // Present means can explain class and get AI summary
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = {
                                                selectedClassForRevision = cls
                                                explanationText = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("AI Revision Notes ✨", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
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
    overallAttendance: Float,
    onPendingClick: () -> Unit,
    onUpcomingClick: () -> Unit,
    onRemainingClick: () -> Unit
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
            value = String.format("%.0f%%", overallAttendance),
            subtitle = "Overall Rate",
            color = MaterialTheme.colorScheme.secondaryContainer,
            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
            icon = Icons.Default.CheckCircle,
            modifier = Modifier
                .weight(1f)
                .clickable { onRemainingClick() }
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
