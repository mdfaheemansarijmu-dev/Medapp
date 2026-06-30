package com.example.ui.screens

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

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val assessments by viewModel.assessments.collectAsStateWithLifecycle()
    val studyTasks by viewModel.studyTasks.collectAsStateWithLifecycle()

    val course by viewModel.selectedCourse.collectAsStateWithLifecycle()

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
            TabRow(
                selectedTabIndex = selectedTabIdx,
                modifier = Modifier.padding(horizontal = 16.dp),
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTabIdx == 0,
                    onClick = { selectedTabIdx = 0 },
                    text = { Text("Assignments") },
                    modifier = Modifier.testTag("tab_assignments")
                )
                Tab(
                    selected = selectedTabIdx == 1,
                    onClick = { selectedTabIdx = 1 },
                    text = { Text("Assessments") },
                    modifier = Modifier.testTag("tab_exams")
                )
                Tab(
                    selected = selectedTabIdx == 2,
                    onClick = { selectedTabIdx = 2 },
                    text = { Text("Study Goals") },
                    modifier = Modifier.testTag("tab_goals")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lists rendering
            when (selectedTabIdx) {
                0 -> AssignmentsSubList(filteredAssignments, onDelete = { viewModel.removeAssignment(it) }, onToggle = { viewModel.toggleAssignment(it) })
                1 -> AssessmentsSubList(filteredAssessments, onDelete = { viewModel.removeAssessment(it) }, onToggle = { viewModel.toggleAssessment(it) })
                2 -> StudyTasksSubList(filteredStudyTasks, onDelete = { viewModel.removeStudyTask(it) }, onProgress = { id, prog -> viewModel.updateStudyProgress(id, prog) })
            }
        }

        // Add dialogues
        if (showAddItemDialog) {
            when (selectedTabIdx) {
                0 -> AddAssignmentDialog(
                    onDismiss = { showAddItemDialog = false },
                    onSave = { subject, title, due, priority, type, notes ->
                        viewModel.addAssignment(subject, title, due, priority, type, notes)
                        showAddItemDialog = false
                    }
                )
                1 -> AddAssessmentDialog(
                    onDismiss = { showAddItemDialog = false },
                    onSave = { subject, title, date, type, syllabus ->
                        viewModel.addAssessment(subject, title, date, type, syllabus)
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
            }
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
    onSave: (String, String, Long, String, String, String?) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Medium") }
    var type by remember { mutableStateOf("Assignment") }
    var notes by remember { mutableStateOf("") }
    var dueDateMillis by remember { mutableStateOf(System.currentTimeMillis() + 24 * 60 * 60 * 1000L) }

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
                        onSave(subject, title, dueDateMillis, priority, type, notes.ifEmpty { null })
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
    onSave: (String, String, Long, String, String?) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Internal") }
    var syllabus by remember { mutableStateOf("") }
    var examDateMillis by remember { mutableStateOf(System.currentTimeMillis() + 48 * 60 * 60 * 1000L) }

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
                        onSave(subject, title, examDateMillis, type, syllabus.ifEmpty { null })
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
