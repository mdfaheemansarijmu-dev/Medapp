package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AttendanceRecord
import com.example.data.model.TimetableClass
import com.example.ui.viewmodel.PlannerViewModel
import com.example.ui.viewmodel.Screen
import com.example.util.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    viewModel: PlannerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val overallSummary by viewModel.overallAttendanceSummary.collectAsStateWithLifecycle()
    val subjectSummaries by viewModel.subjectAttendanceSummaries.collectAsStateWithLifecycle()
    val attendanceTarget by viewModel.attendanceTarget.collectAsStateWithLifecycle()
    val timetable by viewModel.timetable.collectAsStateWithLifecycle()
    val allRecords by viewModel.allAttendanceRecords.collectAsStateWithLifecycle()
    val selectedCourse by viewModel.selectedCourse.collectAsStateWithLifecycle()

    var showTargetDialog by remember { mutableStateOf(false) }
    var selectedSubjectForDetails by remember { mutableStateOf<SubjectAttendanceSummary?>(null) }
    var editingRecord by remember { mutableStateOf<AttendanceRecord?>(null) }
    var showQuickRecordDialog by remember { mutableStateOf(false) }

    // Tab view: 0 = Subjects, 1 = History Log
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf<String?>(null) }

    // Keep selected subject summary updated reactively
    val currentSelectedSubject = selectedSubjectForDetails?.let { sel ->
        subjectSummaries.find { it.subject.equals(sel.subject, ignoreCase = true) } ?: sel
    }

    BackHandler { onBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Attendance & Safety",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                        Text(
                            text = "${selectedCourse?.displayName ?: "Medical Student"} • Target: $attendanceTarget%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("attendance_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Target configuration button
                    AssistChip(
                        onClick = { showTargetDialog = true },
                        label = {
                            Text(
                                text = "Target: $attendanceTarget%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("btn_configure_target")
                    )

                    IconButton(
                        onClick = { showQuickRecordDialog = true },
                        modifier = Modifier.testTag("btn_quick_record_attendance")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddTask,
                            contentDescription = "Record Class",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Check condition: Empty timetable state (Section 21)
            if (timetable.isEmpty() && allRecords.isEmpty()) {
                NoTimetableEmptyState(
                    onAddTimetable = { viewModel.navigateTo(Screen.Timetable) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // 1. Overall Attendance Overview Card (Section 1)
                    item {
                        AttendanceOverviewCard(
                            summary = overallSummary,
                            onEditTarget = { showTargetDialog = true }
                        )
                    }

                    // 2. Actionable Warning Banner if any subject is below target (Section 15)
                    val riskySubjects = subjectSummaries.filter {
                        it.status == AttendanceStatus.BELOW_TARGET || it.status == AttendanceStatus.CRITICAL
                    }
                    if (riskySubjects.isNotEmpty()) {
                        item {
                            AttendanceAlertBanner(
                                count = riskySubjects.size,
                                firstSubject = riskySubjects.first(),
                                onSubjectClick = { selectedSubjectForDetails = it }
                            )
                        }
                    } else if (overallSummary.hasData) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFE8F5E9)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Attendance is on track across all subjects.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF1B5E20)
                                    )
                                }
                            }
                        }
                    }

                    // Navigation view tabs (Subjects vs Attendance History)
                    item {
                        PrimaryTabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            divider = {}
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = {
                                    Text(
                                        text = "Subject Attendance (${subjectSummaries.size})",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                },
                                modifier = Modifier.testTag("tab_subjects")
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = {
                                    Text(
                                        text = "History Log (${allRecords.size})",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                },
                                modifier = Modifier.testTag("tab_history")
                            )
                        }
                    }

                    if (selectedTab == 0) {
                        // Section 2: Subject-Wise Attendance list
                        if (subjectSummaries.isEmpty()) {
                            item {
                                NoAttendanceRecordsEmptyState(
                                    onRecordClass = { showQuickRecordDialog = true }
                                )
                            }
                        } else {
                            items(subjectSummaries, key = { it.subject }) { subjectSummary ->
                                SubjectAttendanceCard(
                                    summary = subjectSummary,
                                    onClick = { selectedSubjectForDetails = subjectSummary }
                                )
                            }
                        }
                    } else {
                        // Section 11: Attendance History Log
                        item {
                            // Filter chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = filterStatus == null,
                                    onClick = { filterStatus = null },
                                    label = { Text("All (${allRecords.size})") }
                                )
                                FilterChip(
                                    selected = filterStatus == "PRESENT",
                                    onClick = { filterStatus = if (filterStatus == "PRESENT") null else "PRESENT" },
                                    label = { Text("Present") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                )
                                FilterChip(
                                    selected = filterStatus == "ABSENT",
                                    onClick = { filterStatus = if (filterStatus == "ABSENT") null else "ABSENT" },
                                    label = { Text("Absent") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                )
                                FilterChip(
                                    selected = filterStatus == "EXCUSED",
                                    onClick = { filterStatus = if (filterStatus == "EXCUSED") null else "EXCUSED" },
                                    label = { Text("Excused") }
                                )
                            }
                        }

                        val filteredRecords = allRecords.filter { rec ->
                            val statusMatch = when (filterStatus) {
                                null -> true
                                "PRESENT" -> rec.status == "PRESENT" || rec.isPresent
                                "ABSENT" -> rec.status == "ABSENT"
                                "EXCUSED" -> rec.status == "EXCUSED"
                                else -> rec.status == filterStatus
                            }
                            statusMatch
                        }.sortedWith(compareByDescending<AttendanceRecord> { it.dateString }.thenByDescending { it.recordedTimestamp })

                        if (filteredRecords.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No records found for this filter.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(filteredRecords, key = { it.id }) { record ->
                                AttendanceHistoryRow(
                                    record = record,
                                    onClick = { editingRecord = record }
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }
    }

    // Target Configuration Dialog (Section 13)
    if (showTargetDialog) {
        AttendanceTargetDialog(
            currentTarget = attendanceTarget,
            onDismiss = { showTargetDialog = false },
            onSave = { newTarget ->
                viewModel.setAttendanceTarget(newTarget)
                showTargetDialog = false
            }
        )
    }

    // Subject Details Sheet / Dialog (Section 12)
    currentSelectedSubject?.let { subjectSummary ->
        SubjectDetailsDialog(
            summary = subjectSummary,
            onDismiss = { selectedSubjectForDetails = null },
            onRecordClassForSubject = {
                selectedSubjectForDetails = null
                showQuickRecordDialog = true
            },
            onEditRecord = { record ->
                editingRecord = record
            }
        )
    }

    // Edit Attendance Dialog with Confirmation (Section 9, 10, 24)
    editingRecord?.let { record ->
        EditAttendanceDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onSave = { updatedRecord ->
                viewModel.updateAttendanceRecord(updatedRecord)
                editingRecord = null
            },
            onDelete = { recordToDelete ->
                viewModel.deleteAttendanceRecord(recordToDelete)
                editingRecord = null
            }
        )
    }

    // Quick Record Attendance Dialog
    if (showQuickRecordDialog) {
        QuickRecordAttendanceDialog(
            timetable = timetable,
            onDismiss = { showQuickRecordDialog = false },
            onRecord = { subject, status, classTime, dateString, note, reason ->
                val isPresent = status == "PRESENT" || status == "EXCUSED"
                viewModel.markAttendance(
                    subject = subject,
                    isPresent = isPresent,
                    classTime = classTime,
                    status = status,
                    dateString = dateString,
                    note = note,
                    reason = reason
                )
                showQuickRecordDialog = false
            }
        )
    }
}

// ==========================================
// 1. ATTENDANCE OVERVIEW CARD
// ==========================================

@Composable
fun AttendanceOverviewCard(
    summary: OverallAttendanceSummary,
    onEditTarget: () -> Unit
) {
    val statusColor = when (summary.status) {
        AttendanceStatus.ON_TRACK -> Color(0xFF2E7D32)
        AttendanceStatus.NEAR_TARGET -> Color(0xFFE65100)
        AttendanceStatus.BELOW_TARGET -> Color(0xFFC62828)
        AttendanceStatus.CRITICAL -> Color(0xFFB71C1C)
        AttendanceStatus.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusContainerColor = when (summary.status) {
        AttendanceStatus.ON_TRACK -> Color(0xFFE8F5E9)
        AttendanceStatus.NEAR_TARGET -> Color(0xFFFFF3E0)
        AttendanceStatus.BELOW_TARGET -> Color(0xFFFFEBEE)
        AttendanceStatus.CRITICAL -> Color(0xFFFFCDD2)
        AttendanceStatus.NO_DATA -> MaterialTheme.colorScheme.surfaceVariant
    }

    val statusIcon = when (summary.status) {
        AttendanceStatus.ON_TRACK -> Icons.Default.CheckCircle
        AttendanceStatus.NEAR_TARGET -> Icons.Default.Info
        AttendanceStatus.BELOW_TARGET -> Icons.Default.Warning
        AttendanceStatus.CRITICAL -> Icons.Default.ErrorOutline
        AttendanceStatus.NO_DATA -> Icons.Default.HelpOutline
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("attendance_overview_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Overall Attendance",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (summary.hasData) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f%%", summary.percentage),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-1).sp
                                ),
                                color = statusColor
                            )
                        }
                        Text(
                            text = "${summary.totalAttended} / ${summary.totalClasses} classes attended",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = "No records yet",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "0 classes recorded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badge (Section 3: Text + Icon + Color)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusContainerColor,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = summary.statusLabel,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = summary.statusLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = statusColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actionable Intelligence Card (Section 32: "Am I safe?")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (summary.status == AttendanceStatus.BELOW_TARGET || summary.status == AttendanceStatus.CRITICAL) {
                            Icons.Default.Warning
                        } else {
                            Icons.Default.Lightbulb
                        },
                        contentDescription = null,
                        tint = if (summary.status == AttendanceStatus.BELOW_TARGET || summary.status == AttendanceStatus.CRITICAL) {
                            Color(0xFFC62828)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.actionableInsight,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Target requirement: ${summary.target}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Secondary Metrics (Section 16 Streak, Section 18 Trend)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Streak Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Streak",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (summary.currentStreak > 0) "${summary.currentStreak} in a row" else "0 classes",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Trend Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (summary.trend) {
                                AttendanceTrend.IMPROVING -> Icons.Default.TrendingUp
                                AttendanceTrend.DECLINING -> Icons.Default.TrendingDown
                                else -> Icons.Default.TrendingFlat
                            },
                            contentDescription = null,
                            tint = when (summary.trend) {
                                AttendanceTrend.IMPROVING -> Color(0xFF2E7D32)
                                AttendanceTrend.DECLINING -> Color(0xFFC62828)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Monthly Trend",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = when (summary.trend) {
                                    AttendanceTrend.IMPROVING -> "Improving (+${summary.trendDifference.roundToInt()}%)"
                                    AttendanceTrend.DECLINING -> "Declining (${summary.trendDifference.roundToInt()}%)"
                                    AttendanceTrend.STABLE -> "Stable"
                                    AttendanceTrend.INSUFFICIENT_DATA -> "Active tracking"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. SUBJECT-WISE ATTENDANCE CARD
// ==========================================

@Composable
fun SubjectAttendanceCard(
    summary: SubjectAttendanceSummary,
    onClick: () -> Unit
) {
    val statusColor = when (summary.status) {
        AttendanceStatus.ON_TRACK -> Color(0xFF2E7D32)
        AttendanceStatus.NEAR_TARGET -> Color(0xFFE65100)
        AttendanceStatus.BELOW_TARGET -> Color(0xFFC62828)
        AttendanceStatus.CRITICAL -> Color(0xFFB71C1C)
        AttendanceStatus.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusContainerColor = when (summary.status) {
        AttendanceStatus.ON_TRACK -> Color(0xFFE8F5E9)
        AttendanceStatus.NEAR_TARGET -> Color(0xFFFFF3E0)
        AttendanceStatus.BELOW_TARGET -> Color(0xFFFFEBEE)
        AttendanceStatus.CRITICAL -> Color(0xFFFFCDD2)
        AttendanceStatus.NO_DATA -> MaterialTheme.colorScheme.surfaceVariant
    }

    val statusIcon = when (summary.status) {
        AttendanceStatus.ON_TRACK -> Icons.Default.CheckCircle
        AttendanceStatus.NEAR_TARGET -> Icons.Default.Info
        AttendanceStatus.BELOW_TARGET -> Icons.Default.Warning
        AttendanceStatus.CRITICAL -> Icons.Default.ErrorOutline
        AttendanceStatus.NO_DATA -> Icons.Default.HelpOutline
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("subject_attendance_${summary.subject.replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.subject,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (summary.totalClasses > 0) {
                            "${summary.attendedClasses} / ${summary.totalClasses} classes"
                        } else {
                            "No classes recorded yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (summary.totalClasses > 0) {
                            String.format(Locale.getDefault(), "%.0f%%", summary.percentage)
                        } else {
                            "—"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = statusColor
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusContainerColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = summary.statusMessage,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = statusColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actionable calculation summary (Section 4 & 5)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = summary.actionableInsight,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 3. ATTENDANCE ALERT BANNER
// ==========================================

@Composable
fun AttendanceAlertBanner(
    count: Int,
    firstSubject: SubjectAttendanceSummary,
    onSubjectClick: (SubjectAttendanceSummary) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFEBEE)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSubjectClick(firstSubject) }
            .testTag("attendance_alert_banner")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = Color(0xFFC62828),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "⚠️ $count ${if (count == 1) "subject requires" else "subjects require"} attention",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFB71C1C)
                )
                Text(
                    text = "${firstSubject.subject} is at ${firstSubject.percentage.roundToInt()}% (${firstSubject.actionableInsight})",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7F0000)
                )
            }
            Text(
                text = "View",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFC62828)
            )
        }
    }
}

// ==========================================
// 4. ATTENDANCE HISTORY ROW
// ==========================================

@Composable
fun AttendanceHistoryRow(
    record: AttendanceRecord,
    onClick: () -> Unit
) {
    val isAttended = record.status == "PRESENT" || record.status == "EXCUSED" || record.isPresent
    val isExcused = record.status == "EXCUSED"
    val isCancelled = record.status == "NO_CLASS" || record.status == "CANCELLED"

    val statusColor = when {
        isCancelled -> Color(0xFFE65100)
        isExcused -> Color(0xFF1565C0)
        isAttended -> Color(0xFF2E7D32)
        else -> Color(0xFFC62828)
    }

    val statusBg = when {
        isCancelled -> Color(0xFFFFF3E0)
        isExcused -> Color(0xFFE3F2FD)
        isAttended -> Color(0xFFE8F5E9)
        else -> Color(0xFFFFEBEE)
    }

    val statusLabel = when (record.status) {
        "PRESENT" -> "Present"
        "ABSENT" -> "Absent"
        "EXCUSED" -> "Excused"
        "NO_CLASS", "CANCELLED" -> "Cancelled"
        else -> if (record.isPresent) "Present" else "Absent"
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("attendance_record_${record.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.subject,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${record.dateString}${if (!record.classTime.isNullOrBlank()) " • ${record.classTime}" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!record.reason.isNullOrBlank() || !record.note.isNullOrBlank()) {
                    Text(
                        text = "Note: ${record.reason ?: record.note}",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            isCancelled -> Icons.Default.Block
                            isExcused -> Icons.Default.MedicalServices
                            isAttended -> Icons.Default.Check
                            else -> Icons.Default.Close
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor
                    )
                }
            }
        }
    }
}

// ==========================================
// 5. SUBJECT DETAILS DIALOG
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailsDialog(
    summary: SubjectAttendanceSummary,
    onDismiss: () -> Unit,
    onRecordClassForSubject: () -> Unit,
    onEditRecord: (AttendanceRecord) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.subject.uppercase(),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Target: ${summary.target}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Big percentage & count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (summary.totalClasses > 0) {
                                String.format(Locale.getDefault(), "%.1f%%", summary.percentage)
                            } else "—",
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (summary.percentage >= summary.target) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        Text(
                            text = "${summary.attendedClasses} / ${summary.totalClasses} classes attended",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (summary.percentage >= summary.target) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    ) {
                        Text(
                            text = summary.statusMessage,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (summary.percentage >= summary.target) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Intelligence guidance
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = summary.actionableInsight,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (summary.classesCanMiss > 0) {
                            Text(
                                text = "Safety margin: ${summary.classesCanMiss} missed classes will keep you >= ${summary.target}%.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (summary.classesToRecover > 0) {
                            Text(
                                text = "Recovery requirement: Attend the next ${summary.classesToRecover} consecutive classes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // History for this subject
                Text(
                    text = "Class Session History (${summary.records.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (summary.records.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No sessions recorded for ${summary.subject}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(summary.records, key = { it.id }) { rec ->
                            AttendanceHistoryRow(
                                record = rec,
                                onClick = { onEditRecord(rec) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onRecordClassForSubject,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record Session")
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. SAFE ATTENDANCE INTERACTION & EDIT DIALOG
// ==========================================

@Composable
fun EditAttendanceDialog(
    record: AttendanceRecord,
    onDismiss: () -> Unit,
    onSave: (AttendanceRecord) -> Unit,
    onDelete: (AttendanceRecord) -> Unit
) {
    var selectedStatus by remember { mutableStateOf(record.status) }
    var noteText by remember { mutableStateOf(record.note ?: record.reason ?: "") }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val statusChanged = selectedStatus != record.status

    val statusOptions = listOf(
        "PRESENT" to "Present ✓",
        "ABSENT" to "Absent ✗",
        "EXCUSED" to "Excused 📋",
        "NO_CLASS" to "No Class 🚫"
    )

    val lastUpdatedDate = remember(record.recordedTimestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        sdf.format(Date(record.recordedTimestamp))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Edit Attendance",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${record.subject} • ${record.dateString}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Attendance Status",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                // Safe status selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    statusOptions.forEach { (statusKey, label) ->
                        val isSelected = selectedStatus == statusKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedStatus = statusKey },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note / Reason (Optional)") },
                    placeholder = { Text("e.g. Ward duty, fever, official duty") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )

                Text(
                    text = "Last updated: $lastUpdatedDate",
                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (statusChanged) {
                        showConfirmationDialog = true
                    } else {
                        onSave(
                            record.copy(
                                status = selectedStatus,
                                isPresent = selectedStatus == "PRESENT" || selectedStatus == "EXCUSED",
                                note = noteText.ifBlank { null },
                                reason = noteText.ifBlank { null }
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { showDeleteConfirmation = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )

    // Status change confirmation (Section 9: Safe Attendance Interaction)
    if (showConfirmationDialog) {
        val originalLabel = when (record.status) {
            "PRESENT" -> "Present"
            "ABSENT" -> "Absent"
            "EXCUSED" -> "Excused"
            else -> "No Class"
        }
        val newLabel = when (selectedStatus) {
            "PRESENT" -> "Present"
            "ABSENT" -> "Absent"
            "EXCUSED" -> "Excused"
            else -> "No Class"
        }

        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text("Change Attendance Status?") },
            text = {
                Text("Change attendance for ${record.subject} on ${record.dateString} from $originalLabel to $newLabel?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmationDialog = false
                        onSave(
                            record.copy(
                                status = selectedStatus,
                                isPresent = selectedStatus == "PRESENT" || selectedStatus == "EXCUSED",
                                note = noteText.ifBlank { null },
                                reason = noteText.ifBlank { null }
                            )
                        )
                    }
                ) {
                    Text("Change")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete record confirmation (Section 30: Data Safety)
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Attendance Record?") },
            text = {
                Text("Are you sure you want to permanently delete this record for ${record.subject} on ${record.dateString}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete(record)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==========================================
// 7. ATTENDANCE TARGET CONFIGURATION DIALOG
// ==========================================

@Composable
fun AttendanceTargetDialog(
    currentTarget: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var targetValue by remember { mutableStateOf(currentTarget.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Attendance Target",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Configure your institution's minimum attendance requirement:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Target:",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "${targetValue.roundToInt()}%",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Slider(
                    value = targetValue,
                    onValueChange = { targetValue = it },
                    valueRange = 50f..95f,
                    steps = 8,
                    modifier = Modifier.testTag("slider_attendance_target")
                )

                // Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(75, 80, 85).forEach { preset ->
                        OutlinedButton(
                            onClick = { targetValue = preset.toFloat() },
                            modifier = Modifier.weight(1f),
                            colors = if (targetValue.roundToInt() == preset) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text("$preset%")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(targetValue.roundToInt()) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Target")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==========================================
// 8. QUICK RECORD ATTENDANCE DIALOG
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickRecordAttendanceDialog(
    timetable: List<TimetableClass>,
    onDismiss: () -> Unit,
    onRecord: (subject: String, status: String, classTime: String?, dateString: String, note: String?, reason: String?) -> Unit
) {
    val distinctSubjects = remember(timetable) {
        timetable.map { it.subject }.filter { it.isNotBlank() }.distinct()
    }

    var selectedSubject by remember {
        mutableStateOf(distinctSubjects.firstOrNull() ?: "")
    }
    var customSubject by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("PRESENT") }
    var note by remember { mutableStateOf("") }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayDate = remember { sdf.format(Date()) }
    var dateString by remember { mutableStateOf(todayDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Record Class Attendance",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select Subject",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                if (distinctSubjects.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        distinctSubjects.take(5).forEach { sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedSubject = sub }
                                    .background(
                                        if (selectedSubject == sub) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedSubject == sub,
                                    onClick = { selectedSubject = sub }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (selectedSubject == sub) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = customSubject,
                        onValueChange = { customSubject = it },
                        label = { Text("Subject Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("PRESENT" to "Present ✓", "ABSENT" to "Absent ✗", "EXCUSED" to "Excused 📋").forEach { (st, label) ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Optional Note") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            val finalSubject = if (distinctSubjects.isNotEmpty()) selectedSubject else customSubject
            Button(
                onClick = {
                    if (finalSubject.isNotBlank()) {
                        onRecord(finalSubject, selectedStatus, null, dateString, note.ifBlank { null }, note.ifBlank { null })
                    }
                },
                enabled = finalSubject.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Record")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==========================================
// 9. EMPTY STATES (Section 20 & 21)
// ==========================================

@Composable
fun NoTimetableEmptyState(
    onAddTimetable: () -> Unit
) {
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
                imageVector = Icons.Default.EventBusy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Add your timetable first",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "MedPulse needs your class schedule to track attendance accurately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAddTimetable,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_empty_add_timetable")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Timetable")
            }
        }
    }
}

@Composable
fun NoAttendanceRecordsEmptyState(
    onRecordClass: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.FactCheck,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No attendance recorded yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your attendance will appear here after your first class.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onRecordClass,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Record First Class")
            }
        }
    }
}
