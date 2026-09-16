package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TimetableClass
import com.example.ui.viewmodel.PlannerViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    viewModel: PlannerViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTabIdx by remember { mutableStateOf(0) } // 0 = Today, 1 = Tomorrow, 2 = Weekly Grid
    var showAddClassDialog by remember { mutableStateOf(false) }
    var editingClass by remember { mutableStateOf<TimetableClass?>(null) }

    val course by viewModel.selectedCourse.collectAsStateWithLifecycle()
    val timetable by viewModel.timetable.collectAsStateWithLifecycle()

    val currentDayIdx = viewModel.getCurrentDayOfWeek()
    val tomorrowDayIdx = if (currentDayIdx == 7) 1 else currentDayIdx + 1

    val daysList = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

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
                        text = "Weekly Schedule",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Customized for ${course?.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(
                        onClick = { viewModel.selectCourse(course ?: return@IconButton) },
                        modifier = Modifier.testTag("reset_timetable_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset timetable defaults", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { showAddClassDialog = true },
                        modifier = Modifier.testTag("add_class_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Class", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Tab bar switcher
            TabRow(
                selectedTabIndex = selectedTabIdx,
                modifier = Modifier.padding(horizontal = 16.dp),
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTabIdx == 0,
                    onClick = { selectedTabIdx = 0 },
                    text = { Text("Today") },
                    modifier = Modifier.testTag("timetable_tab_today")
                )
                Tab(
                    selected = selectedTabIdx == 1,
                    onClick = { selectedTabIdx = 1 },
                    text = { Text("Tomorrow") },
                    modifier = Modifier.testTag("timetable_tab_tomorrow")
                )
                Tab(
                    selected = selectedTabIdx == 2,
                    onClick = { selectedTabIdx = 2 },
                    text = { Text("Weekly Grid") },
                    modifier = Modifier.testTag("timetable_tab_weekly")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Contents
            when (selectedTabIdx) {
                0 -> {
                    // Today Tab
                    DayClassesList(
                        dayIndex = currentDayIdx,
                        dayName = "Today's Schedule (" + viewModel.getDayName(currentDayIdx) + ")",
                        classes = timetable.filter { it.dayOfWeek == currentDayIdx },
                        onDeleteClick = { viewModel.removeTimetableClass(it) },
                        onEditClick = { editingClass = it },
                        viewModel = viewModel
                    )
                }
                1 -> {
                    // Tomorrow Tab
                    DayClassesList(
                        dayIndex = tomorrowDayIdx,
                        dayName = "Tomorrow's Schedule (" + viewModel.getDayName(tomorrowDayIdx) + ")",
                        classes = timetable.filter { it.dayOfWeek == tomorrowDayIdx },
                        onDeleteClick = { viewModel.removeTimetableClass(it) },
                        onEditClick = { editingClass = it },
                        viewModel = viewModel
                    )
                }
                2 -> {
                    // Full Weekly Grid View
                    WeeklyScheduleGrid(
                        timetable = timetable,
                        daysList = daysList,
                        onDeleteClick = { viewModel.removeTimetableClass(it) },
                        onEditClick = { editingClass = it },
                        viewModel = viewModel
                    )
                }
            }
        }

        // Add class dialog
        if (showAddClassDialog) {
            AddClassDialog(
                onDismiss = { showAddClassDialog = false },
                onAdd = { subject, day, start, end, room, teacher, period ->
                    viewModel.addTimetableClass(subject, day, start, end, room, teacher, period)
                    showAddClassDialog = false
                },
                daysList = daysList
            )
        }

        // Edit class dialog
        editingClass?.let { classToEdit ->
            EditClassDialog(
                classItem = classToEdit,
                onDismiss = { editingClass = null },
                onSave = { updatedSubject, updatedDay, updatedStart, updatedEnd, updatedRoom, updatedTeacher, updatedPeriod ->
                    viewModel.updateTimetableClass(
                        classToEdit.id,
                        updatedSubject,
                        updatedDay,
                        updatedStart,
                        updatedEnd,
                        updatedRoom,
                        updatedTeacher,
                        updatedPeriod
                    )
                    editingClass = null
                },
                daysList = daysList
            )
        }
    }
}

@Composable
fun DayClassesList(
    dayIndex: Int,
    dayName: String,
    classes: List<TimetableClass>,
    onDeleteClick: (Int) -> Unit,
    onEditClick: (TimetableClass) -> Unit,
    viewModel: PlannerViewModel
) {
    val sortedClasses = classes.sortedBy { viewModel.parseTimeToMinutes(it.startTime) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = dayName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )

        if (sortedClasses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EventBusy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Classes Scheduled",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Take a break, do research, or tap the '+' icon to add a lecture period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(sortedClasses) { classItem ->
                    TimetableClassRow(
                        classItem = classItem,
                        onDeleteClick = onDeleteClick,
                        onEditClick = { onEditClick(classItem) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun WeeklyScheduleGrid(
    timetable: List<TimetableClass>,
    daysList: List<String>,
    onDeleteClick: (Int) -> Unit,
    onEditClick: (TimetableClass) -> Unit,
    viewModel: PlannerViewModel
) {
    var expandedDayIdx by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(daysList.indices.toList()) { index ->
            val dayOfWeek = index + 1
            val dayName = daysList[index]
            val dayClasses = timetable.filter { it.dayOfWeek == dayOfWeek }
                .sortedBy { viewModel.parseTimeToMinutes(it.startTime) }
            val isExpanded = expandedDayIdx == dayOfWeek

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedDayIdx = if (isExpanded) null else dayOfWeek }
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
                                text = dayName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${dayClasses.size} lecture periods today",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand schedule"
                        )
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (dayClasses.isEmpty()) {
                                Text(
                                    "No classes scheduled for $dayName.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                dayClasses.forEach { classItem ->
                                    TimetableClassRow(
                                        classItem = classItem,
                                        onDeleteClick = onDeleteClick,
                                        onEditClick = { onEditClick(classItem) }
                                    )
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

@Composable
fun TimetableClassRow(
    classItem: TimetableClass,
    onDeleteClick: (Int) -> Unit,
    onEditClick: () -> Unit
) {
    val hexColor = try {
        Color(android.graphics.Color.parseColor(classItem.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(hexColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "P${classItem.periodNumber}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = classItem.subject,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "${classItem.startTime} - ${classItem.endTime} • Room: ${classItem.room ?: "LT"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!classItem.teacherName.isNullOrEmpty()) {
                    Text(
                        text = "By: ${classItem.teacherName}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Show Non-Lecture / Practical / Clinical Badging
                val lowerSubject = classItem.subject.lowercase()
                if (lowerSubject.contains("non-lecture") || lowerSubject.contains("posting") || lowerSubject.contains("clinical") || lowerSubject.contains("practical") || lowerSubject.contains("yoga")) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = if (lowerSubject.contains("clinical") || lowerSubject.contains("posting")) 
                            MaterialTheme.colorScheme.tertiaryContainer 
                        else 
                            MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (lowerSubject.contains("clinical") || lowerSubject.contains("posting")) 
                                "🏥 CLINICAL POSTING" 
                            else if (lowerSubject.contains("yoga"))
                                "🧘 YOGA / PRACTICE"
                            else 
                                "🔬 NON-LECTURE / PRACTICAL",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (lowerSubject.contains("clinical") || lowerSubject.contains("posting")) 
                                MaterialTheme.colorScheme.onTertiaryContainer 
                            else 
                                MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = onEditClick,
                modifier = Modifier.testTag("edit_class_${classItem.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Period",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = { onDeleteClick(classItem.id) },
                modifier = Modifier.testTag("delete_class_${classItem.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Period",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClassDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int, String, String, String?, String?, Int) -> Unit,
    daysList: List<String>
) {
    var subject by remember { mutableStateOf("") }
    var daySelectionIdx by remember { mutableStateOf(0) }
    var periodNumber by remember { mutableStateOf("1") }
    var startTime by remember { mutableStateOf("09:00 AM") }
    var endTime by remember { mutableStateOf("10:00 AM") }
    var room by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }

    var expandedDayDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (subject.isNotBlank()) {
                        val periodInt = periodNumber.toIntOrNull() ?: 1
                        onAdd(subject, daySelectionIdx + 1, startTime, endTime, room.ifEmpty { null }, teacher.ifEmpty { null }, periodInt)
                    }
                },
                enabled = subject.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add Period")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = {
            Text("Add Schedule Period", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (e.g. Materia Medica)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_class_subject")
                )

                // Day Selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = daysList[daySelectionIdx],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Day of Week") },
                        trailingIcon = {
                            IconButton(onClick = { expandedDayDropdown = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable { expandedDayDropdown = true }
                    )
                    DropdownMenu(
                        expanded = expandedDayDropdown,
                        onDismissRequest = { expandedDayDropdown = false }
                    ) {
                        daysList.forEachIndexed { index, dayName ->
                            DropdownMenuItem(
                                text = { Text(dayName) },
                                onClick = {
                                    daySelectionIdx = index
                                    expandedDayDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = periodNumber,
                        onValueChange = { periodNumber = it },
                        label = { Text("Period #") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it },
                        label = { Text("Room / Hall") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(2f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start (09:00 AM)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End (10:00 AM)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Instructor / Dr. Name") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditClassDialog(
    classItem: TimetableClass,
    onDismiss: () -> Unit,
    onSave: (String, Int, String, String, String?, String?, Int) -> Unit,
    daysList: List<String>
) {
    var subject by remember { mutableStateOf(classItem.subject) }
    var daySelectionIdx by remember { mutableStateOf((classItem.dayOfWeek - 1).coerceIn(0, daysList.size - 1)) }
    var periodNumber by remember { mutableStateOf(classItem.periodNumber.toString()) }
    var startTime by remember { mutableStateOf(classItem.startTime) }
    var endTime by remember { mutableStateOf(classItem.endTime) }
    var room by remember { mutableStateOf(classItem.room ?: "") }
    var teacher by remember { mutableStateOf(classItem.teacherName ?: "") }

    var expandedDayDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (subject.isNotBlank()) {
                        val periodInt = periodNumber.toIntOrNull() ?: 1
                        onSave(subject, daySelectionIdx + 1, startTime, endTime, room.ifEmpty { null }, teacher.ifEmpty { null }, periodInt)
                    }
                },
                enabled = subject.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = {
            Text("Edit Schedule Period", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (e.g. Materia Medica)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_class_subject")
                )

                // Day Selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = daysList[daySelectionIdx],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Day of Week") },
                        trailingIcon = {
                            IconButton(onClick = { expandedDayDropdown = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable { expandedDayDropdown = true }
                    )
                    DropdownMenu(
                        expanded = expandedDayDropdown,
                        onDismissRequest = { expandedDayDropdown = false }
                    ) {
                        daysList.forEachIndexed { index, dayName ->
                            DropdownMenuItem(
                                text = { Text(dayName) },
                                onClick = {
                                    daySelectionIdx = index
                                    expandedDayDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = periodNumber,
                        onValueChange = { periodNumber = it },
                        label = { Text("Period #") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it },
                        label = { Text("Room / Hall") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(2f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start (e.g. 09:00 AM)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End (e.g. 10:00 AM)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Instructor / Dr. Name") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}
