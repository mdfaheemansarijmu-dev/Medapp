package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Assignment
import com.example.data.model.Assessment
import com.example.data.model.StudyTask
import com.example.ui.viewmodel.PlannerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    viewModel: PlannerViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTabIdx by remember { mutableStateOf(0) } // 0 = Assignments, 1 = Assessments, 2 = Study Tasks
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showBatchInfoDialog by remember { mutableStateOf(false) }

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val assessments by viewModel.assessments.collectAsStateWithLifecycle()
    val studyTasks by viewModel.studyTasks.collectAsStateWithLifecycle()

    val course by viewModel.selectedCourse.collectAsStateWithLifecycle()
    val studentYear by viewModel.studentYear.collectAsStateWithLifecycle()
    val selectedSyllabusYear by viewModel.selectedSyllabusYear.collectAsStateWithLifecycle()
    val studentBatch by viewModel.studentBatch.collectAsStateWithLifecycle()
    val studentCollege by viewModel.studentCollege.collectAsStateWithLifecycle()
    val isBatchListening by viewModel.isBatchSyncListening.collectAsStateWithLifecycle()
    val batchSyncStatus by viewModel.batchSyncStatus.collectAsStateWithLifecycle()
    val activeBatchKey by viewModel.activeBatchKey.collectAsStateWithLifecycle()
    val customBatchCode by viewModel.customBatchCode.collectAsStateWithLifecycle()
    val batchFeedbackMessage by viewModel.batchShareFeedbackMessage.collectAsStateWithLifecycle()

    var preselectedSubjectForTopic by remember { mutableStateOf<String?>(null) }

    // Filter by search query
    val filteredAssignments = assignments.filter {
        it.title.contains(searchQuery, ignoreCase = true) || it.subject.contains(searchQuery, ignoreCase = true)
    }
    val filteredAssessments = assessments.filter {
        it.title.contains(searchQuery, ignoreCase = true) || it.subject.contains(searchQuery, ignoreCase = true)
    }
    val filteredStudyTasks = studyTasks.filter {
        it.title.contains(searchQuery, ignoreCase = true) || it.subject.contains(searchQuery, ignoreCase = true)
    }

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
                        text = "Curriculum Planner",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Track homework, tests, vivas and goals",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { showAddItemDialog = true },
                    modifier = Modifier.testTag("planner_add_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Batch Channel Sync Status Chip
            val effectiveBatchName = studentBatch.ifBlank { "Batch" }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isBatchListening) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp)
                    .clickable { showBatchInfoDialog = true }
                    .testTag("planner_batch_channel_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = if (isBatchListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Batch Channel: ${activeBatchKey.ifBlank { "Connecting..." }}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isBatchListening) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isBatchListening) "● LIVE" else "○ OFFLINE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = if (isBatchListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isBatchListening) "Auto-sharing active with $effectiveBatchName peers. Tap to share code." else "$batchSyncStatus (Tap to diagnose)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Channel details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // In-App Feedback Banner for auto-sharing results
            batchFeedbackMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
                        IconButton(onClick = { viewModel.clearBatchShareFeedback() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search subjects, exams or tasks...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .testTag("planner_search_bar")
            )

            // Tabs row
            ScrollableTabRow(
                selectedTabIndex = selectedTabIdx,
                modifier = Modifier.padding(horizontal = 8.dp),
                containerColor = Color.Transparent,
                edgePadding = 12.dp,
                divider = {}
            ) {
                Tab(
                    selected = selectedTabIdx == 0,
                    onClick = { selectedTabIdx = 0 },
                    text = { Text("Assignments (${assignments.count { it.status == "Pending" }})") },
                    modifier = Modifier.testTag("tab_assignments")
                )
                Tab(
                    selected = selectedTabIdx == 1,
                    onClick = { selectedTabIdx = 1 },
                    text = { Text("Assessments (${assessments.count { it.status == "Upcoming" }})") },
                    modifier = Modifier.testTag("tab_exams")
                )
                Tab(
                    selected = selectedTabIdx == 2,
                    onClick = { selectedTabIdx = 2 },
                    text = { Text("Study Goals (${studyTasks.count { it.progress < 100 }})") },
                    modifier = Modifier.testTag("tab_goals")
                )
                Tab(
                    selected = selectedTabIdx == 3,
                    onClick = { selectedTabIdx = 3 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (selectedTabIdx == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Finished", fontWeight = if (selectedTabIdx == 3) FontWeight.Bold else FontWeight.Normal)
                        }
                    },
                    modifier = Modifier.testTag("tab_finished")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lists rendering
            when (selectedTabIdx) {
                0 -> AssignmentsSubList(filteredAssignments, onDelete = { viewModel.removeAssignment(it) }, onToggle = { viewModel.toggleAssignment(it) })
                1 -> AssessmentsSubList(filteredAssessments, onDelete = { viewModel.removeAssessment(it) }, onToggle = { viewModel.toggleAssessment(it) })
                2 -> StudyTasksSubList(filteredStudyTasks, onDelete = { viewModel.removeStudyTask(it) }, onProgress = { id, prog -> viewModel.updateStudyProgress(id, prog) })
                3 -> SyllabusFinishedSubList(
                    viewModel = viewModel,
                    searchQuery = searchQuery,
                    onOpenAddDialog = { preselectedSubject ->
                        preselectedSubjectForTopic = preselectedSubject
                        showAddItemDialog = true
                    }
                )
            }
        }

        // Add dialogues
        if (showAddItemDialog) {
            when (selectedTabIdx) {
                0 -> AddAssignmentDialog(
                    onDismiss = { showAddItemDialog = false },
                    studentBatch = studentBatch.ifBlank { "Batch" },
                    activeBatchKey = activeBatchKey,
                    onSave = { subject, title, due, priority, type, notes, shareWithBatch ->
                        viewModel.addAssignment(subject, title, due, priority, type, notes, shareWithBatch)
                        showAddItemDialog = false
                    }
                )
                1 -> AddAssessmentDialog(
                    onDismiss = { showAddItemDialog = false },
                    studentBatch = studentBatch.ifBlank { "Batch" },
                    activeBatchKey = activeBatchKey,
                    onSave = { subject, title, date, type, syllabus, shareWithBatch ->
                        viewModel.addAssessment(subject, title, date, type, syllabus, shareWithBatch)
                        showAddItemDialog = false
                    }
                )
                2 -> AddStudyTaskDialog(
                    onDismiss = { showAddItemDialog = false },
                    onSave = { subject, title, due, priority, minutes ->
                        viewModel.addStudyTask(subject, title, due, priority, minutes)
                        showAddItemDialog = false
                    }
                )
                3 -> {
                    val courseCode = course?.code ?: "MBBS"
                    val effectiveYear = if (selectedSyllabusYear.isNotBlank()) {
                        selectedSyllabusYear
                    } else {
                        com.example.data.syllabus.CourseSyllabusDirectory.normalizeYear(studentYear.ifBlank { "1st Year" })
                    }
                    val availableSubjects = remember(courseCode, effectiveYear) {
                        com.example.data.syllabus.CourseSyllabusDirectory.getSubjectsForYear(courseCode, effectiveYear)
                    }
                    AddCompletedTopicDialog(
                        initialSubject = preselectedSubjectForTopic,
                        availableSubjects = availableSubjects,
                        currentAcademicYear = effectiveYear,
                        studentBatch = studentBatch.ifBlank { "Batch" },
                        onDismiss = {
                            showAddItemDialog = false
                            preselectedSubjectForTopic = null
                        },
                        onSave = { subject, topicTitle, academicYear, teacherName, notes, shareWithBatch ->
                            viewModel.addCompletedTopic(subject, topicTitle, academicYear, teacherName, notes, shareWithBatch)
                            showAddItemDialog = false
                            preselectedSubjectForTopic = null
                        }
                    )
                }
            }
        }

        if (showBatchInfoDialog) {
            BatchSyncInfoDialog(
                onDismiss = { showBatchInfoDialog = false },
                activeBatchKey = activeBatchKey,
                syncStatus = batchSyncStatus,
                isListening = isBatchListening,
                customBatchCode = customBatchCode,
                onSetCustomBatchCode = { viewModel.setCustomBatchCode(it) },
                onTestConnection = { onResult -> viewModel.testBatchSyncConnection(onResult) },
                onSyncNow = { onResult -> viewModel.fetchAndSyncBatchNow(onResult) }
            )
        }
    }
}

// 1. Assignments list
@Composable
fun AssignmentsSubList(
    assignments: List<Assignment>,
    onDelete: (Int) -> Unit,
    onToggle: (Assignment) -> Unit
) {
    if (assignments.isEmpty()) {
        EmptyListNotice("No assignments registered", "Tap the '+' icon to register a record, practical submission or viva.")
    } else {
        val formatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Group overdue
            val currentMs = System.currentTimeMillis()
            val overdue = assignments.filter { it.status == "Pending" && it.dueDate < currentMs - (12 * 60 * 60 * 1000L) }
            val pending = assignments.filter { it.status == "Pending" && it.dueDate >= currentMs - (12 * 60 * 60 * 1000L) }
            val completed = assignments.filter { it.status == "Completed" }

            if (overdue.isNotEmpty()) {
                item {
                    Text("Overdue Items", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                items(overdue, key = { it.id }) { asg ->
                    AssignmentCardRow(asg, formatter, onDelete, onToggle)
                }
            }

            if (pending.isNotEmpty()) {
                item {
                    Text("Active Assignments", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 8.dp))
                }
                items(pending, key = { it.id }) { asg ->
                    AssignmentCardRow(asg, formatter, onDelete, onToggle)
                }
            }

            if (completed.isNotEmpty()) {
                item {
                    Text("Completed", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 8.dp))
                }
                items(completed, key = { it.id }) { asg ->
                    AssignmentCardRow(asg, formatter, onDelete, onToggle)
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun AssignmentCardRow(
    asg: Assignment,
    formatter: SimpleDateFormat,
    onDelete: (Int) -> Unit,
    onToggle: (Assignment) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = asg.status == "Completed",
                onCheckedChange = { onToggle(asg) },
                modifier = Modifier.testTag("checkbox_asg_${asg.id}")
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asg.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (asg.status == "Completed") androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${asg.subject} • ${asg.type}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Due: " + formatter.format(Date(asg.dueDate)),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                val asgAuthor = when {
                    asg.authorName.isNotBlank() && asg.authorName != "Classmate" -> asg.authorName
                    asg.notes?.contains("Shared by ") == true -> asg.notes.substringAfter("Shared by ").substringBefore(")").trim()
                    asg.authorName.isNotBlank() -> asg.authorName
                    asg.notes?.contains("Shared by") == true -> "Classmate"
                    else -> null
                }
                if (asgAuthor != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Submitted by $asgAuthor",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (asg.status == "Completed") {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Saved in Finished Syllabus Area",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = Color(0xFF047857)
                            )
                        }
                    }
                }
                if (!asg.notes.isNullOrEmpty()) {
                    Text(
                        text = asg.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = { onDelete(asg.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

// 2. Assessments sublist
@Composable
fun AssessmentsSubList(
    assessments: List<Assessment>,
    onDelete: (Int) -> Unit,
    onToggle: (Assessment) -> Unit
) {
    if (assessments.isEmpty()) {
        EmptyListNotice("No assessment registered", "Keep track of class tests, internal midterms, university trials, etc.")
    } else {
        val formatter = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val upcoming = assessments.filter { it.status == "Upcoming" }
            val completed = assessments.filter { it.status == "Completed" }

            if (upcoming.isNotEmpty()) {
                item {
                    Text("Upcoming Assessments", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                items(upcoming, key = { it.id }) { exam ->
                    AssessmentCardRow(exam, formatter, onDelete, onToggle)
                }
            }

            if (completed.isNotEmpty()) {
                item {
                    Text("Completed Assessments", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 8.dp))
                }
                items(completed, key = { it.id }) { exam ->
                    AssessmentCardRow(exam, formatter, onDelete, onToggle)
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun AssessmentCardRow(
    exam: Assessment,
    formatter: SimpleDateFormat,
    onDelete: (Int) -> Unit,
    onToggle: (Assessment) -> Unit
) {
    val countdownDays = remember(exam.date) {
        val todayCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val targetCal = java.util.Calendar.getInstance().apply {
            timeInMillis = exam.date
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val diffMs = targetCal.timeInMillis - todayCal.timeInMillis
        val days = (diffMs / (24 * 60 * 60 * 1000L)).toInt()
        when {
            days < 0 -> "Passed"
            days == 0 -> "TODAY"
            days == 1 -> "1 day left"
            else -> "$days days left"
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
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = exam.status == "Completed",
                onCheckedChange = { onToggle(exam) },
                modifier = Modifier.testTag("checkbox_exam_${exam.id}")
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = exam.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (exam.status == "Completed") androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = countdownDays,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${exam.subject} • ${exam.type}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Scheduled: " + formatter.format(Date(exam.date)),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (exam.firestoreId.isNotBlank() || exam.authorName.isNotBlank()) {
                    val examAuthor = when {
                        exam.authorName.isNotBlank() && exam.authorName != "Classmate" -> exam.authorName
                        exam.authorName.isNotBlank() -> exam.authorName
                        else -> "Classmate"
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Submitted by $examAuthor",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (exam.status == "Completed") {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Saved in Finished Syllabus Area",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = Color(0xFF047857)
                            )
                        }
                    }
                }
                if (!exam.syllabus.isNullOrEmpty()) {
                    Text(
                        text = "Syllabus: " + exam.syllabus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            IconButton(onClick = { onDelete(exam.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// 3. Study tasks sublist
@Composable
fun StudyTasksSubList(
    tasks: List<StudyTask>,
    onDelete: (Int) -> Unit,
    onProgress: (Int, Int) -> Unit
) {
    if (tasks.isEmpty()) {
        EmptyListNotice("No revision tasks registered", "Create study targets to revise chapters, lecture notes, or syllabus units.")
    } else {
        val formatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val active = tasks.filter { it.progress < 100 }
            val completed = tasks.filter { it.progress >= 100 }

            if (active.isNotEmpty()) {
                item {
                    Text("Active Goals", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                items(active, key = { it.id }) { task ->
                    StudyTaskCardRow(task, formatter, onDelete, onProgress)
                }
            }

            if (completed.isNotEmpty()) {
                item {
                    Text("Completed Revision", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 8.dp))
                }
                items(completed, key = { it.id }) { task ->
                    StudyTaskCardRow(task, formatter, onDelete, onProgress)
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun StudyTaskCardRow(
    task: StudyTask,
    formatter: SimpleDateFormat,
    onDelete: (Int) -> Unit,
    onProgress: (Int, Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (task.progress >= 100) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${task.subject} • Revise: ${task.targetMinutes} mins • Due: " + formatter.format(Date(task.dueDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${task.progress}%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    IconButton(onClick = { onDelete(task.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Slider(
                value = task.progress.toFloat(),
                onValueChange = { onProgress(task.id, it.toInt()) },
                valueRange = 0f..100f,
                steps = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("planner_slider_${task.id}")
            )
        }
    }
}

@Composable
fun EmptyListNotice(title: String, desc: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// Dialogs
@Composable
fun AddAssignmentDialog(
    onDismiss: () -> Unit,
    studentBatch: String = "Batch",
    activeBatchKey: String = "",
    onSave: (String, String, Long, String, String, String?, Boolean) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Medium") }
    var type by remember { mutableStateOf("Assignment") }
    var notes by remember { mutableStateOf("") }
    var dueDateMillis by remember { mutableStateOf(System.currentTimeMillis() + 24 * 60 * 60 * 1000L) }
    var shareWithBatch by remember { mutableStateOf(true) }

    val priorities = listOf("High", "Medium", "Low")
    val types = listOf("Assignment", "Practical", "Seminar", "Viva", "Homework", "Case Record")

    var priorityExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && subject.isNotBlank()) {
                        onSave(subject, title, dueDateMillis, priority, type, notes.ifEmpty { null }, shareWithBatch)
                    }
                },
                enabled = title.isNotBlank() && subject.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("New Assignment", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Batch sync info
                val effectiveBatchName = studentBatch.ifBlank { "Batch A" }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (shareWithBatch) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = if (shareWithBatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (shareWithBatch) "Auto-Share with $effectiveBatchName" else "Personal (Private to Me)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (shareWithBatch)
                                    "All peers in channel '${activeBatchKey.ifBlank { effectiveBatchName }}' will automatically receive this in their planner."
                                else
                                    "Private task. Only saved to this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = shareWithBatch,
                            onCheckedChange = { shareWithBatch = it },
                            modifier = Modifier.testTag("switch_share_assignment_batch")
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / Task Name") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_asg_title")
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (e.g. Anatomy)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_asg_subject")
                )

                // Date Picker Field
                OutlinedTextField(
                    value = dateFormat.format(Date(dueDateMillis)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Submission Due Date") },
                    trailingIcon = {
                        IconButton(onClick = {
                            calendar.timeInMillis = dueDateMillis
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val selectedCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    dueDateMillis = selectedCal.timeInMillis
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            calendar.timeInMillis = dueDateMillis
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val selectedCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    dueDateMillis = selectedCal.timeInMillis
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                )

                // Type Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = {
                            IconButton(onClick = { typeExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable { typeExpanded = true }
                    )
                    DropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        types.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    type = item
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Priority Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = priority,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Priority") },
                        trailingIcon = {
                            IconButton(onClick = { priorityExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable { priorityExpanded = true }
                    )
                    DropdownMenu(
                        expanded = priorityExpanded,
                        onDismissRequest = { priorityExpanded = false }
                    ) {
                        priorities.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    priority = item
                                    priorityExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Additional Guidelines / Info") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
fun AddAssessmentDialog(
    onDismiss: () -> Unit,
    studentBatch: String = "Batch",
    activeBatchKey: String = "",
    onSave: (String, String, Long, String, String?, Boolean) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Internal") }
    var syllabus by remember { mutableStateOf("") }
    var examDateMillis by remember { mutableStateOf(System.currentTimeMillis() + 48 * 60 * 60 * 1000L) }
    var shareWithBatch by remember { mutableStateOf(true) }

    val types = listOf("Class Test", "Internal", "Practical Exam", "Viva", "University Exam")
    var typeExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && subject.isNotBlank()) {
                        onSave(subject, title, examDateMillis, type, syllabus.ifEmpty { null }, shareWithBatch)
                    }
                },
                enabled = title.isNotBlank() && subject.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("New Assessment", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Batch sync info
                val effectiveBatchName = studentBatch.ifBlank { "Batch A" }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (shareWithBatch) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = if (shareWithBatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (shareWithBatch) "Auto-Share with $effectiveBatchName" else "Personal (Private to Me)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (shareWithBatch)
                                    "All peers in channel '${activeBatchKey.ifBlank { effectiveBatchName }}' will automatically receive this in their schedule & alerts."
                                else
                                    "Private test schedule. Only saved to this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = shareWithBatch,
                            onCheckedChange = { shareWithBatch = it },
                            modifier = Modifier.testTag("switch_share_assessment_batch")
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Assessment Name (e.g. Physiology Internal I)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_exam_title")
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_exam_subject")
                )

                // Date Picker Field
                OutlinedTextField(
                    value = dateFormat.format(Date(examDateMillis)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Assessment Date") },
                    trailingIcon = {
                        IconButton(onClick = {
                            calendar.timeInMillis = examDateMillis
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val selectedCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    examDateMillis = selectedCal.timeInMillis
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            calendar.timeInMillis = examDateMillis
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val selectedCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    examDateMillis = selectedCal.timeInMillis
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                )

                // Type Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assessment Type") },
                        trailingIcon = {
                            IconButton(onClick = { typeExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable { typeExpanded = true }
                    )
                    DropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        types.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    type = item
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = syllabus,
                    onValueChange = { syllabus = it },
                    label = { Text("Syllabus / Portions Covered") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
fun AddStudyTaskDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String, Int) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var minutesStr by remember { mutableStateOf("30") }
    var priority by remember { mutableStateOf("Medium") }

    val priorities = listOf("High", "Medium", "Low")
    var priorityExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && subject.isNotBlank()) {
                        val min = minutesStr.toIntOrNull() ?: 30
                        onSave(subject, title, System.currentTimeMillis() + (24 * 60 * 60 * 1000L), priority, min)
                    }
                },
                enabled = title.isNotBlank() && subject.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("New Study Goal", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What are you studying?") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_goal_title")
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject Name") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_goal_subject")
                )
                OutlinedTextField(
                    value = minutesStr,
                    onValueChange = { minutesStr = it },
                    label = { Text("Daily Study Target (minutes)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Priority Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = priority,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Task Priority") },
                        trailingIcon = {
                            IconButton(onClick = { priorityExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable { priorityExpanded = true }
                    )
                    DropdownMenu(
                        expanded = priorityExpanded,
                        onDismissRequest = { priorityExpanded = false }
                    ) {
                        priorities.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    priority = item
                                    priorityExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun BatchSyncInfoDialog(
    onDismiss: () -> Unit,
    activeBatchKey: String,
    syncStatus: String,
    isListening: Boolean,
    customBatchCode: String,
    onSetCustomBatchCode: (String) -> Unit,
    onTestConnection: ((Boolean, String) -> Unit) -> Unit,
    onSyncNow: ((Boolean, String) -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    var inputCode by remember { mutableStateOf(customBatchCode) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    val hasPermIssue = syncStatus.contains("Permission Denied", ignoreCase = true) ||
            testResult?.contains("Permission Denied", ignoreCase = true) == true

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Close")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Batch Sync Channel", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isListening) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isListening) "● Feed Status: Live Synchronized" else "○ Feed Status: $syncStatus",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Channel Key: ${activeBatchKey.ifBlank { "Not connected" }}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Text(
                    text = "To receive and share assignments automatically, all classmates must be on the EXACT same Channel Key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Batch Code", activeBatchKey))
                            Toast.makeText(context, "Batch Key copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Code", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Join my MedPulse Batch")
                                putExtra(Intent.EXTRA_TEXT, "Join my MedPulse Batch Channel for live academic sync! Channel Code: $activeBatchKey")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Batch Channel"))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", style = MaterialTheme.typography.labelSmall)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Custom Batch Code (Optional)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Set a custom code (e.g. 'gmc_2024_a') so all your classmates match without profile typos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = inputCode,
                    onValueChange = { inputCode = it },
                    label = { Text("Custom Channel Code") },
                    placeholder = { Text("e.g. aiims_2024_batch_a") },
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onSetCustomBatchCode(inputCode)
                            Toast.makeText(context, "Custom batch code updated!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply Code", style = MaterialTheme.typography.labelSmall)
                    }

                    if (customBatchCode.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                inputCode = ""
                                onSetCustomBatchCode("")
                                Toast.makeText(context, "Reset to profile default!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isSyncing = true
                            testResult = "Fetching shared batch items..."
                            onSyncNow { success, message ->
                                isSyncing = false
                                testResult = message
                            }
                        },
                        enabled = !isSyncing && !isTesting,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("Sync Feed Now", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testResult = "Testing connection to Firebase..."
                            onTestConnection { success, message ->
                                isTesting = false
                                testResult = message
                            }
                        },
                        enabled = !isTesting && !isSyncing,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("Test Channel", style = MaterialTheme.typography.labelSmall)
                    }
                }

                testResult?.let { res ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = res,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (res.contains("error", ignoreCase = true) || res.contains("denied", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (hasPermIssue) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Firebase Rule Setup Required",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Your Firebase project is currently blocking Firestore writes to 'shared_batches'. To enable automatic sync for all classmates:\n1. Open Firebase Console > Firestore Database > Rules\n2. Add rule: allow read, write for 'shared_batches/{batchKey}/{document=**}'\n3. Or enable Anonymous Authentication in Firebase Console.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val rulesSnippet = "match /shared_batches/{batchKey}/{document=**} {\n  allow read, write: if true;\n}"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Firestore Rule", rulesSnippet))
                                    Toast.makeText(context, "Rule snippet copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Firestore Rule Snippet", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    )
}
