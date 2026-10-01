package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.syllabus.CourseSyllabusDirectory
import com.example.ui.viewmodel.PlannerViewModel
import com.example.ui.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishedScreen(
    viewModel: PlannerViewModel,
    modifier: Modifier = Modifier
) {
    // Intercept back button to return to Dashboard
    BackHandler {
        viewModel.navigateTo(Screen.Dashboard)
    }

    val selectedCourse by viewModel.selectedCourse.collectAsStateWithLifecycle()
    val studentYear by viewModel.studentYear.collectAsStateWithLifecycle()
    val selectedSyllabusYear by viewModel.selectedSyllabusYear.collectAsStateWithLifecycle()
    val studentBatch by viewModel.studentBatch.collectAsStateWithLifecycle()
    val completedTopics by viewModel.completedSyllabusTopics.collectAsStateWithLifecycle()
    val batchFeedbackMessage by viewModel.batchShareFeedbackMessage.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var preselectedSubject by remember { mutableStateOf<String?>(null) }

    val courseCode = selectedCourse?.code ?: "MBBS"
    val courseDisplayName = selectedCourse?.displayName ?: "Medicine"

    val effectiveYear = if (selectedSyllabusYear.isNotBlank()) {
        selectedSyllabusYear
    } else {
        CourseSyllabusDirectory.normalizeYear(studentYear.ifBlank { "1st Year" })
    }

    val availableSubjects = remember(courseCode, effectiveYear) {
        CourseSyllabusDirectory.getSubjectsForYear(courseCode, effectiveYear)
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
            // Screen Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Dashboard) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .testTag("finished_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Completed Portions",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "$courseDisplayName • $effectiveYear Curriculum",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Search toggle
                    IconButton(
                        onClick = { isSearchActive = !isSearchActive },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.SearchOff else Icons.Default.Search,
                            contentDescription = "Search Syllabus",
                            tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Add Button
                    Button(
                        onClick = {
                            preselectedSubject = null
                            showAddDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("finished_header_add_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Chapter", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Search Bar (if activated)
            AnimatedVisibility(
                visible = isSearchActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search covered chapter, topic or faculty...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("finished_search_input")
                )
            }

            // Batch Feedback Notification Banner (e.g. "Shared 'Doctrine of Signature' with Batch")
            batchFeedbackMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearBatchShareFeedback() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Main Syllabus Finished List Content
            SyllabusFinishedSubList(
                viewModel = viewModel,
                searchQuery = searchQuery,
                onOpenAddDialog = { subj ->
                    preselectedSubject = subj
                    showAddDialog = true
                }
            )
        }

        // Add Dialog
        if (showAddDialog) {
            AddCompletedTopicDialog(
                initialSubject = preselectedSubject,
                availableSubjects = availableSubjects,
                currentAcademicYear = effectiveYear,
                studentBatch = studentBatch.ifBlank { "Batch" },
                onDismiss = {
                    showAddDialog = false
                    preselectedSubject = null
                },
                onSave = { subject, topicTitle, academicYear, teacherName, notes, shareWithBatch ->
                    viewModel.addCompletedTopic(
                        subject = subject,
                        topicTitle = topicTitle,
                        academicYear = academicYear,
                        teacherName = teacherName,
                        notes = notes,
                        shareWithBatch = shareWithBatch
                    )
                    showAddDialog = false
                    preselectedSubject = null
                }
            )
        }
    }
}
