package com.example.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
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
import com.example.data.model.MedicalCourse
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

    var topicToDelete by remember { mutableStateOf<CompletedSyllabusTopic?>(null) }
    var selectedSubjectDetail by remember { mutableStateOf<SyllabusSubject?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // 1. Academic Year Switcher Chips (shown for chapters)
        if (selectedCategory == FinishedCategory.ALL || selectedCategory == FinishedCategory.CHAPTERS) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "ACADEMIC YEAR CURRICULUM",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableYears) { yr ->
                            val isSelected = CourseSyllabusDirectory.normalizeYear(yr) == CourseSyllabusDirectory.normalizeYear(effectiveYear)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setSelectedSyllabusYear(yr) },
                                label = {
                                    Text(
                                        text = yr,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Syllabus Coverage Summary Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FactCheck,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Finished Syllabus Tracker",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "$courseDisplayName • All Finished Portions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { onOpenAddDialog(null) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_add_covered_chapter")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Chapter", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CoverageStatItem(
                            value = "${filteredTopics.size}",
                            label = "Chapters",
                            icon = Icons.Default.MenuBook,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        CoverageStatItem(
                            value = "${completedAssignments.size}",
                            label = "Assignments",
                            icon = Icons.Default.AssignmentTurnedIn,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        CoverageStatItem(
                            value = "${completedAssessments.size}",
                            label = "Assessments",
                            icon = Icons.Default.FactCheck,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        CoverageStatItem(
                            value = "${filteredTopics.size + completedAssignments.size + completedAssessments.size}",
                            label = "Total Finished",
                            icon = Icons.Default.CheckCircle,
                            tint = Color(0xFF10B981)
                        )
                    }
                }
            }
        }

        // 3. Category Filter Chips (All, Chapters, Assignments, Assessments)
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
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
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // 4. Chapters Section
        if (selectedCategory == FinishedCategory.ALL || selectedCategory == FinishedCategory.CHAPTERS) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COVERED CHAPTERS ($effectiveYear)".uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${filteredTopics.size} Chapters Logged",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(subjectsForYear, key = { "subj_${it.shortName}_${it.name}" }) { subject ->
                val subjectKey = subject.name.trim().lowercase()
                val completedForSubject = topicsBySubject[subjectKey] ?: emptyList()
                var isExpanded by remember { mutableStateOf(false) }

                SubjectSyllabusCard(
                    subject = subject,
                    completedTopics = completedForSubject,
                    isExpanded = isExpanded,
                    onToggleExpand = { isExpanded = !isExpanded },
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
                        text = "OTHER RECORDED TOPICS",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                    CompletedTopicRow(
                        topic = topic,
                        onDelete = { topicToDelete = topic }
                    )
                }
            }
        }

        // 5. Completed Assignments Section
        if (selectedCategory == FinishedCategory.ALL || selectedCategory == FinishedCategory.ASSIGNMENTS) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AssignmentTurnedIn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "COMPLETED ASSIGNMENTS (${completedAssignments.size})".uppercase(),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            if (completedAssignments.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
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
                                text = "When you complete tasks in the Assignments tab, they are automatically saved here!",
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
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "COMPLETED ASSESSMENTS (${completedAssessments.size})".uppercase(),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            if (completedAssessments.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
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
                                text = "Tests and vivas marked completed in the Assessments tab will be archived here!",
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

@Composable
fun SubjectSyllabusCard(
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
                color = if (completedTopics.isNotEmpty()) accentColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("subject_card_${subject.shortName.lowercase().replace(" ", "_")}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Subject Icon, Name & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (completedTopics.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${completedTopics.size} chapter${if (completedTopics.size > 1) "s" else ""} finished",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = Color(0xFF10B981)
                            )
                        } else {
                            Text(
                                text = "Not started yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Add button for this specific subject
                IconButton(
                    onClick = onAddTopic,
                    modifier = Modifier.testTag("btn_add_topic_${subject.shortName.lowercase().replace(" ", "_")}")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Add Chapter to ${subject.name}",
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Expand/collapse button if topics exist
                if (completedTopics.isNotEmpty()) {
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Suggested Topics Chips (High-yield curriculum items like "Doctrine of Signatures")
            if (subject.suggestedTopics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Standard Curriculum Topics:",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(subject.suggestedTopics) { suggested ->
                        val isAlreadyCompleted = completedTopics.any { 
                            it.topicTitle.trim().equals(suggested.trim(), ignoreCase = true) 
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAlreadyCompleted) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isAlreadyCompleted) {
                                    onQuickAddSuggestedTopic(suggested)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isAlreadyCompleted) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Finished",
                                        tint = accentColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = suggested,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isAlreadyCompleted) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isAlreadyCompleted) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Completed topics list for this subject
            if (completedTopics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                val displayedTopics = if (isExpanded) completedTopics else completedTopics.take(2)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    displayedTopics.forEach { topic ->
                        CompletedTopicRow(
                            topic = topic,
                            onDelete = { onDeleteTopic(topic) }
                        )
                    }
                }

                if (!isExpanded && completedTopics.size > 2) {
                    TextButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "View all ${completedTopics.size} finished chapters",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompletedTopicRow(
    topic: CompletedSyllabusTopic,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val formattedDate = remember(topic.completionDate) {
        formatter.format(Date(topic.completionDate))
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.topicTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Covered on $formattedDate",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    topic.teacherName?.let { prof ->
                        Text(
                            text = " • Faculty: $prof",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                val authorText = when {
                    topic.authorName.isNotBlank() && topic.authorName != "Classmate" -> topic.authorName
                    topic.notes?.contains("Recorded by ") == true -> topic.notes.substringAfter("Recorded by ").substringBefore(")").trim()
                    topic.notes?.contains("Completed with batch (by ") == true -> topic.notes.substringAfter("Completed with batch (by ").substringBefore(")").trim()
                    topic.authorName.isNotBlank() -> topic.authorName
                    else -> "Classmate"
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 3.dp)
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
                            text = "Submitted by $authorText",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                topic.notes?.let { note ->
                    if (note.isNotBlank()) {
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun CoverageStatItem(
    value: String,
    label: String,
    icon: ImageVector,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                            availableSubjects.forEach { sub ->
                                DropdownMenuItem(
                                    text = { Text(sub.name) },
                                    onClick = {
                                        selectedSubjectName = sub.name
                                        isSubjectDropdownOpen = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("+ Other / Custom Subject", color = MaterialTheme.colorScheme.primary) },
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
                        placeholder = { Text("e.g. Clinical Rotation") },
                        trailingIcon = {
                            IconButton(onClick = { isCustomSubject = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel custom")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Suggested Topic Chips from standard curriculum
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.15f)
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
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
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
                else -> "Classmate"
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.15f)
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
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
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
                else -> "Classmate"
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
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
