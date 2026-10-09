package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Assessment
import com.example.data.model.Assignment
import com.example.data.model.CompletedSyllabusTopic
import com.example.data.syllabus.CourseSyllabusDirectory
import com.example.data.syllabus.SyllabusSubject
import com.example.ui.viewmodel.PlannerViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class FinishedCategory(val label: String) {
    ALL("All Finished"),
    CHAPTERS("Chapters"),
    ASSIGNMENTS("Assignments"),
    ASSESSMENTS("Assessments")
}

@Composable
fun SyllabusFinishedSubList(
    viewModel: PlannerViewModel,
    searchQuery: String,
    onOpenAddDialog: (preselectedSubject: String?) -> Unit
) {
    val selectedCourse by viewModel.selectedCourse.collectAsStateWithLifecycle()
    val studentYear by viewModel.studentYear.collectAsStateWithLifecycle()
    val completedTopics by viewModel.completedSyllabusTopics.collectAsStateWithLifecycle()
    val selectedSyllabusYear by viewModel.selectedSyllabusYear.collectAsStateWithLifecycle()
    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val assessments by viewModel.assessments.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf(FinishedCategory.ALL) }

    val courseCode = selectedCourse?.code ?: "MBBS"
    val courseDisplayName = selectedCourse?.displayName ?: "Medicine"

    // Default to student's year, or fallback to 1st Year
    val effectiveYear = remember(selectedSyllabusYear, studentYear) {
        if (selectedSyllabusYear.isNotBlank()) {
            selectedSyllabusYear
        } else {
            CourseSyllabusDirectory.normalizeYear(studentYear.ifBlank { "1st Year" })
        }
    }

    val availableYears = remember(courseCode) {
        CourseSyllabusDirectory.getAvailableYears(courseCode)
    }

    val subjectsForYear = remember(courseCode, effectiveYear) {
        CourseSyllabusDirectory.getSubjectsForYear(courseCode, effectiveYear)
    }

    // Filter topics for the active year and search query
    val filteredTopics = remember(completedTopics, effectiveYear, searchQuery) {
        completedTopics.filter { topic ->
            val matchYear = topic.academicYear.isBlank() ||
                    topic.academicYear.equals(effectiveYear, ignoreCase = true) ||
                    CourseSyllabusDirectory.normalizeYear(topic.academicYear) == CourseSyllabusDirectory.normalizeYear(effectiveYear)
            val matchSearch = searchQuery.isBlank() ||
                    topic.topicTitle.contains(searchQuery, ignoreCase = true) ||
                    topic.subject.contains(searchQuery, ignoreCase = true) ||
                    (topic.notes?.contains(searchQuery, ignoreCase = true) == true) ||
                    (topic.teacherName?.contains(searchQuery, ignoreCase = true) == true)
            matchYear && matchSearch
        }
    }

    // Filter completed / submitted assignments
    val completedAssignments = remember(assignments, searchQuery) {
        assignments.filter { 
            it.status.equals("Completed", ignoreCase = true) || 
            it.status.equals("Submitted", ignoreCase = true) || 
            (it.authorName.isNotBlank() && it.authorName != "Classmate") 
        }.filter { asg ->
            searchQuery.isBlank() ||
                    asg.title.contains(searchQuery, ignoreCase = true) ||
                    asg.subject.contains(searchQuery, ignoreCase = true) ||
                    (asg.notes?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    // Filter completed / submitted assessments
    val completedAssessments = remember(assessments, searchQuery) {
        assessments.filter { 
            it.status.equals("Completed", ignoreCase = true) || 
            it.status.equals("Submitted", ignoreCase = true) || 
            (it.authorName.isNotBlank() && it.authorName != "Classmate") 
        }.filter { ass ->
            searchQuery.isBlank() ||
                    ass.title.contains(searchQuery, ignoreCase = true) ||
                    ass.subject.contains(searchQuery, ignoreCase = true) ||
                    (ass.syllabus?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    // Group topics by subject
    val topicsBySubject = remember(filteredTopics) {
        filteredTopics.groupBy { it.subject.trim().lowercase() }
    }

    // Keep track of which card is expanded. Expand first subject by default if topics exist
    var expandedSubjectKey by remember(subjectsForYear) {
        mutableStateOf<String?>(subjectsForYear.firstOrNull()?.name?.trim()?.lowercase())
    }

    var topicToDelete by remember { mutableStateOf<CompletedSyllabusTopic?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
    ) {
        // 1. Sleek Academic Year Filter Row
        if (availableYears.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ACADEMIC YEAR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$courseDisplayName",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    items(availableYears) { yr ->
                        val isSelected = CourseSyllabusDirectory.normalizeYear(yr) == CourseSyllabusDirectory.normalizeYear(effectiveYear)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setSelectedSyllabusYear(yr) },
                            label = {
                                Text(
                                    text = yr,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }
        }

        // 2. High-Level Progress Overview Card (As shown in screenshot design)
        item {
            FinishedSummaryCard(
                courseDisplayName = courseDisplayName,
                effectiveYear = effectiveYear,
                chaptersFinishedCount = filteredTopics.size,
                assignmentsFinishedCount = completedAssignments.size,
                assessmentsFinishedCount = completedAssessments.size,
                onAddChapterClick = { onOpenAddDialog(null) }
            )
        }

        // 3. Category Filter Tabs/Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                items(FinishedCategory.values()) { cat ->
                    val isSelected = selectedCategory == cat
                    val count = when (cat) {
                        FinishedCategory.ALL -> filteredTopics.size + completedAssignments.size + completedAssessments.size
                        FinishedCategory.CHAPTERS -> filteredTopics.size
                        FinishedCategory.ASSIGNMENTS -> completedAssignments.size
                        FinishedCategory.ASSESSMENTS -> completedAssessments.size
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                text = "${cat.label} ($count)",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }

        // 4. Chapters Section with Beautiful Cards matching the user's design
        if (selectedCategory == FinishedCategory.ALL || selectedCategory == FinishedCategory.CHAPTERS) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SUBJECT PORTIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${filteredTopics.size} finished",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF10B981)
                    )
                }
            }

            items(subjectsForYear, key = { "subj_${it.shortName}_${it.name}" }) { subject ->
                val subjectKey = subject.name.trim().lowercase()
                val completedForSubject = topicsBySubject[subjectKey] ?: emptyList()
                val isExpanded = expandedSubjectKey == subjectKey

                ModernSubjectFinishedCard(
                    subject = subject,
                    completedTopics = completedForSubject,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedSubjectKey = if (isExpanded) null else subjectKey
                    },
                    onAddTopic = { onOpenAddDialog(subject.name) },
                    onDeleteTopic = { topicToDelete = it },
                    onQuickAddSuggestedTopic = { suggestedTitle ->
                        viewModel.addCompletedTopic(
                            subject = subject.name,
                            topicTitle = suggestedTitle,
                            academicYear = effectiveYear,
                            notes = "Completed from syllabus guide",
                            shareWithBatch = true
                        )
                    }
                )
            }

            // Custom / Additional Topics outside predefined subjects
            val standardSubjectNames = subjectsForYear.map { it.name.trim().lowercase() }.toSet()
            val otherTopics = filteredTopics.filter { it.subject.trim().lowercase() !in standardSubjectNames }
            if (otherTopics.isNotEmpty()) {
                item {
                    Text(
                        text = "OTHER RECORDED CHAPTERS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                itemsIndexed(
                    items = otherTopics,
                    key = { index, topic ->
                        if (topic.id > 0) "other_id_${topic.id}"
                        else if (topic.firestoreId.isNotBlank()) "other_fs_${topic.firestoreId}_$index"
                        else "other_idx_$index"
                    }
                ) { _, topic ->
                    CleanCompletedTopicRow(
                        topic = topic,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onDelete = { topicToDelete = topic }
                    )
                }
            }
        }

        // 5. Completed Assignments Section
        if (selectedCategory == FinishedCategory.ALL || selectedCategory == FinishedCategory.ASSIGNMENTS) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AssignmentTurnedIn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "COMPLETED ASSIGNMENTS (${completedAssignments.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            if (completedAssignments.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No finished assignments yet",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Assignments marked completed are cleanly archived here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = completedAssignments,
                    key = { index, asg ->
                        if (asg.id > 0) "asg_id_${asg.id}"
                        else if (asg.firestoreId.isNotBlank()) "asg_fs_${asg.firestoreId}_$index"
                        else "asg_idx_$index"
                    }
                ) { _, asg ->
                    CompletedAssignmentCard(
                        assignment = asg,
                        onToggle = { viewModel.toggleAssignment(it) },
                        onDelete = { viewModel.removeAssignment(it) }
                    )
                }
            }
        }

        // 6. Completed Assessments Section
        if (selectedCategory == FinishedCategory.ALL || selectedCategory == FinishedCategory.ASSESSMENTS) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "COMPLETED ASSESSMENTS (${completedAssessments.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            if (completedAssessments.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No finished assessments yet",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tests and vivas marked completed in Assessments will be archived here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = completedAssessments,
                    key = { index, ass ->
                        if (ass.id > 0) "ass_id_${ass.id}"
                        else if (ass.firestoreId.isNotBlank()) "ass_fs_${ass.firestoreId}_$index"
                        else "ass_idx_$index"
                    }
                ) { _, ass ->
                    CompletedAssessmentCard(
                        assessment = ass,
                        onToggle = { viewModel.toggleAssessment(it) },
                        onDelete = { viewModel.removeAssessment(it) }
                    )
                }
            }
        }
    }

    // Confirmation dialog for deleting recorded topic
    topicToDelete?.let { topic ->
        AlertDialog(
            onDismissRequest = { topicToDelete = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove Covered Chapter?") },
            text = {
                Text("Are you sure you want to remove '${topic.topicTitle}' from your completed ${topic.subject} syllabus record?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeCompletedTopic(topic.id)
                        topicToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { topicToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Clean, modern summary card at top of Finished view.
 */
@Composable
fun FinishedSummaryCard(
    courseDisplayName: String,
    effectiveYear: String,
    chaptersFinishedCount: Int,
    assignmentsFinishedCount: Int,
    assessmentsFinishedCount: Int,
    onAddChapterClick: () -> Unit
) {
    val totalCount = chaptersFinishedCount + assignmentsFinishedCount + assessmentsFinishedCount

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Completed Portions",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = "$courseDisplayName • $effectiveYear",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onAddChapterClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_add_covered_chapter")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Chapter", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            // 3-Metric Clean Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CleanStatMetric(
                    value = chaptersFinishedCount.toString(),
                    label = "Chapters",
                    color = Color(0xFF10B981)
                )
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
                CleanStatMetric(
                    value = assignmentsFinishedCount.toString(),
                    label = "Assignments",
                    color = MaterialTheme.colorScheme.secondary
                )
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
                CleanStatMetric(
                    value = assessmentsFinishedCount.toString(),
                    label = "Assessments",
                    color = MaterialTheme.colorScheme.tertiary
                )
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
                CleanStatMetric(
                    value = totalCount.toString(),
                    label = "Total Done",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CleanStatMetric(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Modern Subject Finished Card matching the uploaded design:
 * - Rounded white card with elevation and subtle outline
 * - Subject Icon in tinted circle
 * - Subject Name + Green "N chapters finished" subtext
 * - Circular Progress Ring showing ratio (e.g. 7/12)
 * - Expand chevron with rotation
 * - Standard Curriculum Topics horizontally scrollable
 * - Clean topic list with date, lecturer, badge, and delete
 * - Quick "Add Chapter" action button
 */
@Composable
fun ModernSubjectFinishedCard(
    subject: SyllabusSubject,
    completedTopics: List<CompletedSyllabusTopic>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddTopic: () -> Unit,
    onDeleteTopic: (CompletedSyllabusTopic) -> Unit,
    onQuickAddSuggestedTopic: (String) -> Unit
) {
    val accentColor = remember(subject.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(subject.colorHex))
        } catch (e: Exception) {
            Color(0xFF4F46E5)
        }
    }

    val iconVector = remember(subject.iconType) {
        resolveSubjectIcon(subject.iconType)
    }

    val totalStandardTopics = subject.suggestedTopics.size.coerceAtLeast(1)
    val completedCount = completedTopics.size
    val progress = remember(completedCount, totalStandardTopics) {
        if (totalStandardTopics > 0) {
            (completedCount.toFloat() / totalStandardTopics.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevron_rotation"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (completedCount > 0) accentColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("subject_card_${subject.shortName.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Clickable row to toggle expansion
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tinted Subject Icon Container
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = subject.name,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Subject Name & Finished Chapter Count
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (completedCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$completedCount chapters finished",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFF10B981)
                            )
                        }
                    } else {
                        Text(
                            text = "0 / $totalStandardTopics chapters",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Circular Progress Indicator with fraction (e.g. 7/12)
                CircularProgressFractionBadge(
                    completed = completedCount,
                    total = totalStandardTopics,
                    progress = progress,
                    accentColor = if (completedCount > 0) Color(0xFF10B981) else accentColor
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Chevron icon
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(chevronRotation)
                    )
                }
            }

            // Expanded Portion: Curriculum Topics & Logged Items
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Standard Curriculum Topics section (with suggested chips)
                    if (subject.suggestedTopics.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Standard Curriculum Topics:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Quick add custom chapter button
                            TextButton(
                                onClick = onAddTopic,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("btn_add_topic_${subject.shortName.lowercase().replace(" ", "_")}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = accentColor)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Add Custom", fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Scrollable topics chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(subject.suggestedTopics) { suggested ->
                                val isAlreadyCompleted = completedTopics.any {
                                    it.topicTitle.trim().equals(suggested.trim(), ignoreCase = true)
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isAlreadyCompleted) {
                                        Color(0xFF10B981).copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                    border = if (isAlreadyCompleted) {
                                        null
                                    } else {
                                        androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = !isAlreadyCompleted) {
                                            onQuickAddSuggestedTopic(suggested)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isAlreadyCompleted) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Finished",
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Add",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                        }
                                        Text(
                                            text = suggested,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isAlreadyCompleted) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            color = if (isAlreadyCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Completed Chapters List
                    if (completedTopics.isNotEmpty()) {
                        Text(
                            text = "Logged Chapters (${completedTopics.size}):",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            completedTopics.forEach { topic ->
                                CleanCompletedTopicRow(
                                    topic = topic,
                                    accentColor = accentColor,
                                    onDelete = { onDeleteTopic(topic) }
                                )
                            }
                        }
                    } else {
                        // Empty state inside expanded card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "No chapters recorded yet. Tap any topic above or 'Add Custom' to track covered portions.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
 * Clean circular progress ring with centered fraction (e.g. 7/12)
 */
@Composable
fun CircularProgressFractionBadge(
    completed: Int,
    total: Int,
    progress: Float,
    accentColor: Color
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(44.dp)
    ) {
        val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        Canvas(modifier = Modifier.size(40.dp)) {
            val strokeWidth = 3.5.dp.toPx()
            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // Progress
            if (progress > 0f) {
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Fraction text in center
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$completed/$total",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = if (completed > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Clean, modern completed topic row inside subject card.
 */
@Composable
fun CleanCompletedTopicRow(
    topic: CompletedSyllabusTopic,
    accentColor: Color,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val formattedDate = remember(topic.completionDate) {
        formatter.format(Date(topic.completionDate))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.topicTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Covered on $formattedDate",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    topic.teacherName?.let { prof ->
                        if (prof.isNotBlank()) {
                            Text(
                                text = "• Faculty: $prof",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = accentColor
                            )
                        }
                    }
                }

                val authorText = when {
                    topic.authorName.isNotBlank() && topic.authorName != "Classmate" -> topic.authorName
                    topic.notes?.contains("Recorded by ") == true -> topic.notes.substringAfter("Recorded by ").substringBefore(")").trim()
                    topic.notes?.contains("Completed with batch (by ") == true -> topic.notes.substringAfter("Completed with batch (by ").substringBefore(")").trim()
                    topic.authorName.isNotBlank() -> topic.authorName
                    else -> null
                }

                if (authorText != null && authorText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "Submitted by $authorText",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                topic.notes?.let { note ->
                    if (note.isNotBlank() && !note.contains("Completed with batch") && !note.contains("Recorded by")) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * Dialog to record a new completed chapter/portion in a subject.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCompletedTopicDialog(
    initialSubject: String? = null,
    availableSubjects: List<SyllabusSubject>,
    currentAcademicYear: String,
    studentBatch: String,
    onDismiss: () -> Unit,
    onSave: (subject: String, topicTitle: String, academicYear: String, teacherName: String?, notes: String?, shareWithBatch: Boolean) -> Unit
) {
    var selectedSubjectName by remember { 
        mutableStateOf(initialSubject ?: availableSubjects.firstOrNull()?.name ?: "") 
    }
    var topicTitle by remember { mutableStateOf("") }
    var teacherName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var shareWithBatch by remember { mutableStateOf(true) }
    var isSubjectDropdownOpen by remember { mutableStateOf(false) }
    var isCustomSubject by remember { mutableStateOf(false) }
    var customSubjectName by remember { mutableStateOf("") }

    val activeSubject = availableSubjects.firstOrNull { it.name == selectedSubjectName }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BookmarkAdded,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Record Covered Chapter",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Log chapters or portions completed in class for your personal syllabus record and batch schedule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Subject Selector
                if (!isCustomSubject) {
                    ExposedDropdownMenuBox(
                        expanded = isSubjectDropdownOpen,
                        onExpandedChange = { isSubjectDropdownOpen = it }
                    ) {
                        OutlinedTextField(
                            value = selectedSubjectName.ifBlank { "Select Subject" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Subject") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSubjectDropdownOpen) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("syllabus_subject_dropdown"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = isSubjectDropdownOpen,
                            onDismissRequest = { isSubjectDropdownOpen = false }
                        ) {
                            availableSubjects.forEach { subj ->
                                DropdownMenuItem(
                                    text = { Text(subj.name) },
                                    onClick = {
                                        selectedSubjectName = subj.name
                                        isSubjectDropdownOpen = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ Custom Subject...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    isCustomSubject = true
                                    isSubjectDropdownOpen = false
                                }
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = customSubjectName,
                        onValueChange = { customSubjectName = it },
                        label = { Text("Custom Subject Name") },
                        placeholder = { Text("e.g. Pathology") },
                        trailingIcon = {
                            IconButton(onClick = { isCustomSubject = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Custom")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Suggested Chapters from syllabus for quick selection
                if (activeSubject != null && activeSubject.suggestedTopics.isNotEmpty() && !isCustomSubject) {
                    Column {
                        Text(
                            text = "Suggested Chapters for ${activeSubject.shortName}:",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(activeSubject.suggestedTopics.take(6)) { suggested ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            topicTitle = suggested
                                        }
                                ) {
                                    Text(
                                        text = suggested,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Chapter / Topic Name
                OutlinedTextField(
                    value = topicTitle,
                    onValueChange = { topicTitle = it },
                    label = { Text("Completed Chapter / Topic *") },
                    placeholder = { Text("e.g. Doctrine of Signature") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_completed_topic_title"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Faculty / Teacher (Optional)
                OutlinedTextField(
                    value = teacherName,
                    onValueChange = { teacherName = it },
                    label = { Text("Faculty / Lecturer (Optional)") },
                    placeholder = { Text("e.g. Dr. Sharma") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Notes / Syllabus References (Optional)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Highlights (Optional)") },
                    placeholder = { Text("e.g. Important for upcoming viva & internal exam") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 2
                )

                // Batch broadcast toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (shareWithBatch) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { shareWithBatch = !shareWithBatch }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = if (shareWithBatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (shareWithBatch) "Broadcast to $studentBatch" else "Personal Log Only",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            )
                            Text(
                                text = if (shareWithBatch) "Notify classmates that this chapter was covered" else "Only saved to your device",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = shareWithBatch,
                            onCheckedChange = { shareWithBatch = it },
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalSubject = if (isCustomSubject) customSubjectName.trim() else selectedSubjectName.trim()
                    if (finalSubject.isNotBlank() && topicTitle.isNotBlank()) {
                        onSave(
                            finalSubject,
                            topicTitle.trim(),
                            currentAcademicYear,
                            teacherName.trim().ifBlank { null },
                            notes.trim().ifBlank { null },
                            shareWithBatch
                        )
                    }
                },
                enabled = (if (isCustomSubject) customSubjectName.isNotBlank() else selectedSubjectName.isNotBlank()) && topicTitle.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_save_completed_topic")
            ) {
                Text("Mark Completed")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun resolveSubjectIcon(iconType: String): ImageVector {
    return when (iconType.lowercase()) {
        "pharmacy" -> Icons.Default.Medication
        "book" -> Icons.Default.MenuBook
        "flask" -> Icons.Default.Science
        "bone" -> Icons.Default.AccessibilityNew
        "heart" -> Icons.Default.Favorite
        "microscope" -> Icons.Default.Biotech
        "scalpel" -> Icons.Default.Healing
        "tooth" -> Icons.Default.SentimentSatisfiedAlt
        "herb" -> Icons.Default.Spa
        "cross" -> Icons.Default.LocalHospital
        else -> Icons.Default.School
    }
}

@Composable
fun CompletedAssignmentCard(
    assignment: Assignment,
    onToggle: (Assignment) -> Unit,
    onDelete: (Int) -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val dateStr = if (assignment.dueDate > 0) formatter.format(Date(assignment.dueDate)) else "Completed"
    val accentColor = CourseSyllabusDirectory.getSubjectColor(assignment.subject)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = assignment.subject,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                            text = "COMPLETED",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = assignment.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Due / Written: $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (assignment.type.isNotBlank()) {
                    Text(
                        text = "• ${assignment.type}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val asgAuthor = when {
                assignment.authorName.isNotBlank() && assignment.authorName != "Classmate" -> assignment.authorName
                assignment.notes?.contains("Shared by ") == true -> assignment.notes.substringAfter("Shared by ").substringBefore(")").trim()
                assignment.authorName.isNotBlank() -> assignment.authorName
                else -> null
            }

            if (asgAuthor != null && asgAuthor.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Submitted by $asgAuthor",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (!assignment.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = assignment.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onToggle(assignment) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mark Pending", fontSize = 11.sp)
                }
                IconButton(
                    onClick = { onDelete(assignment.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CompletedAssessmentCard(
    assessment: Assessment,
    onToggle: (Assessment) -> Unit,
    onDelete: (Int) -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val dateStr = if (assessment.date > 0) formatter.format(Date(assessment.date)) else "Completed"
    val accentColor = CourseSyllabusDirectory.getSubjectColor(assessment.subject)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = assessment.subject,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "COMPLETED",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = assessment.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Attempted: $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (assessment.type.isNotBlank()) {
                    Text(
                        text = "• ${assessment.type}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val assAuthor = when {
                assessment.authorName.isNotBlank() && assessment.authorName != "Classmate" -> assessment.authorName
                assessment.authorName.isNotBlank() -> assessment.authorName
                else -> null
            }

            if (assAuthor != null && assAuthor.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Submitted by $assAuthor",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (!assessment.syllabus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Portions tested: ${assessment.syllabus}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onToggle(assessment) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mark Upcoming", fontSize = 11.sp)
                }
                IconButton(
                    onClick = { onDelete(assessment.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
