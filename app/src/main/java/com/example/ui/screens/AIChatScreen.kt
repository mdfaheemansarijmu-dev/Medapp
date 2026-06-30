package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
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
import com.example.data.model.ChatMessage
import com.example.data.model.ParsedItem
import com.example.data.model.UnifiedParserResponse
import com.example.data.model.ParsedTimetableClass
import com.example.ui.viewmodel.PlannerViewModel
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(
    viewModel: PlannerViewModel,
    modifier: Modifier = Modifier
) {
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isParsing by viewModel.isParsingMessage.collectAsStateWithLifecycle()
    val activeDrafts by viewModel.activeParsedDrafts.collectAsStateWithLifecycle()
    val activeResponse by viewModel.activeUnifiedResponse.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText: String? =
                result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!spokenText.isNullOrBlank()) {
                textInput = spokenText
            }
        }
    }

    val triggerSpeechRecognition = {
        try {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().language)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to schedule a task or class...")
            }
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Speech recognition is not supported on this device.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Scroll chat to end when new messages arrive
    LaunchedEffect(chatMessages.size, isParsing) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    // Local mutable copy of drafts so the user can edit or uncheck them before saving
    var selectedDraftsMap = remember(activeDrafts) {
        mutableStateMapOf<ParsedItem, Boolean>().apply {
            activeDrafts.forEach { put(it, true) }
        }
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MedPulse AI Chat",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Import schedule via Camera, Gallery, PDF or WhatsApp text",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { viewModel.clearChatHistory() },
                    modifier = Modifier.testTag("clear_chat_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat History", tint = MaterialTheme.colorScheme.outline)
                }
            }

            // Sleek row of input sources (Camera, Gallery, PDF, Text)
            AIInputMethodsRow(
                onTextMethodSelected = {
                    // Pre-fill text input with a standard timetable WhatsApp text notice template
                    textInput = "Weekly Timetable:\nMonday\n08:30 Anatomy\n09:30 Physiology\n12:00 Lunch Break\n01:00 Anatomy Practical"
                },
                onFileSelected = { name, content, bytes, mimeType ->
                    viewModel.parseInputDocument(content, bytes, mimeType)
                }
            )

            // Message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (chatMessages.isEmpty()) {
                    item {
                        AIChatWelcomeCard()
                    }
                } else {
                    items(chatMessages) { chat ->
                        ChatBubbleRow(chat)
                    }
                }

                if (isParsing) {
                    item {
                        AIParsingLoadingRow()
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Draft / Timetable Preview Panel (Appears if Gemini successfully parsed items)
            if (activeResponse?.document_type == "Weekly Timetable") {
                TimetablePreviewPanel(
                    response = activeResponse!!,
                    onReplaceClick = { editedList ->
                        viewModel.replaceCurrentTimetable(editedList)
                    },
                    onCancelClick = {
                        viewModel.cancelUnifiedImport()
                    }
                )
            } else if (activeDrafts.isNotEmpty()) {
                DraftReviewPanel(
                    drafts = activeDrafts,
                    selectedMap = selectedDraftsMap,
                    onImportClick = {
                        val approvedList = selectedDraftsMap.entries.filter { it.value }.map { it.key }
                        viewModel.approveAndImportDrafts(approvedList)
                    },
                    onCancelClick = {
                        viewModel.cancelUnifiedImport()
                    }
                )
            }


            // Bottom text field row
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Ask Gemini, speak, or paste text...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        leadingIcon = {
                            IconButton(
                                onClick = { triggerSpeechRecognition() },
                                modifier = Modifier.testTag("mic_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Speak directly to Gemini",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FloatingActionButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.sendChatMessage(textInput)
                                textInput = ""
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_send_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send notice to AI")
                    }
                }
            }
        }
    }
}

@Composable
fun AIChatWelcomeCard() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Welcome to MedPulse AI Notice Parser",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Simply copy announcements or homework assignments from your class WhatsApp group and paste them here. The AI will extract and organize them automatically into your planner!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Try Pasting This Example:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "\"Tomorrow submit Anatomy Record. Monday Physiology Internal. Organon assignment before Friday.\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubbleRow(chat: ChatMessage) {
    val isAI = chat.sender == "ai"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAI) Arrangement.Start else Arrangement.End
    ) {
        if (isAI) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            color = if (isAI) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isAI) 4.dp else 16.dp,
                bottomEnd = if (isAI) 16.dp else 4.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = chat.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isAI) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun AIParsingLoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "MedPulse AI is organizing schedules...",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun DraftReviewPanel(
    drafts: List<ParsedItem>,
    selectedMap: MutableMap<ParsedItem, Boolean>,
    onImportClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .testTag("ai_draft_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Proposed Calendar Items",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text(
                    "${selectedMap.count { it.value }} / ${drafts.size} selected",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                drafts.forEach { item ->
                    val isChecked = selectedMap[item] ?: true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { selectedMap[item] = !isChecked }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { selectedMap[item] = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Subject: ${item.subject} • Due: ${item.due_date_description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            color = when (item.category) {
                                "Assignment", "Homework" -> MaterialTheme.colorScheme.secondaryContainer
                                "Assessment", "Exam" -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.tertiaryContainer
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = when (item.category) {
                                    "Assignment", "Homework" -> MaterialTheme.colorScheme.onSecondaryContainer
                                    "Assessment", "Exam" -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onTertiaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Discard")
                }

                Button(
                    onClick = onImportClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("import_drafts_button")
                ) {
                    Text("Import Items")
                }
            }
        }
    }
}

// Simulated preset descriptor for file templates
data class SimulatedFilePreset(
    val name: String,
    val description: String,
    val mimeType: String
) {
    fun getMockBytes(): ByteArray {
        return name.toByteArray()
    }
}

@Composable
fun AIInputMethodsRow(
    onTextMethodSelected: () -> Unit,
    onFileSelected: (name: String, content: String, bytes: ByteArray?, mimeType: String?) -> Unit
) {
    var showCameraDialog by remember { mutableStateOf(false) }
    var showGalleryDialog by remember { mutableStateOf(false) }
    var showPdfDialog by remember { mutableStateOf(false) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onFileSelected("Picked_Image.jpg", "Weekly Timetable:\nMonday\n08:30 Anatomy\n09:30 Physiology\n12:00 Lunch Break\n01:00 Anatomy Practical", null, "image/jpeg")
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onFileSelected("Picked_Document.pdf", "Weekly Timetable:\nMonday\n08:30 Anatomy\n09:30 Physiology\n12:00 Lunch Break\n01:00 Anatomy Practical", null, "application/pdf")
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Select AI Input Source",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Camera Button
                AIInputChip(
                    icon = Icons.Default.CameraAlt,
                    label = "Camera",
                    color = Color(0xFFEF4444),
                    onClick = { showCameraDialog = true }
                )
                // Gallery Button
                AIInputChip(
                    icon = Icons.Default.PhotoLibrary,
                    label = "Gallery",
                    color = Color(0xFF3B82F6),
                    onClick = { showGalleryDialog = true }
                )
                // PDF Button
                AIInputChip(
                    icon = Icons.Default.PictureAsPdf,
                    label = "PDF File",
                    color = Color(0xFF10B981),
                    onClick = { showPdfDialog = true }
                )
                // WhatsApp Button
                AIInputChip(
                    icon = Icons.Default.TextSnippet,
                    label = "WhatsApp",
                    color = Color(0xFFF59E0B),
                    onClick = onTextMethodSelected
                )
            }
        }
    }

    // Camera Simulation & Picker Dialog
    if (showCameraDialog) {
        SimulatedFileDialog(
            title = "Capture Document with Camera",
            icon = Icons.Default.CameraAlt,
            presets = listOf(
                SimulatedFilePreset("1st_Year_MBBS_Weekly_Schedule.png", "Image of a structured 1st Year MBBS Weekly Timetable (Anatomy, Physiology)", "image/png"),
                SimulatedFilePreset("Exam_Routine_July_2026.png", "Image of an Exam Timetable covering final theory dates", "image/png"),
                SimulatedFilePreset("room_change_materia_medica.jpg", "Image of a Notice Board with tomorrow's Materia Medica room changed to Lecture Hall C", "image/jpeg")
            ),
            onDismiss = { showCameraDialog = false },
            onLaunchReal = {
                showCameraDialog = false
                imagePickerLauncher.launch("image/*")
            },
            onSelectPreset = { preset ->
                showCameraDialog = false
                onFileSelected(preset.name, preset.description, preset.getMockBytes(), preset.mimeType)
            }
        )
    }

    // Gallery Picker Dialog
    if (showGalleryDialog) {
        SimulatedFileDialog(
            title = "Import Document from Gallery",
            icon = Icons.Default.PhotoLibrary,
            presets = listOf(
                SimulatedFilePreset("MBBS_Weekly_Timetable.png", "Image of a structured 1st Year MBBS Weekly Timetable (Anatomy, Physiology)", "image/png"),
                SimulatedFilePreset("Clinical_postings_Rotations.jpg", "Clinical rotation postings for Surgery & Medicine", "image/jpeg"),
                SimulatedFilePreset("cancelled_class_announcement.png", "WhatsApp screenshot stating tomorrow's Physiology is cancelled", "image/png")
            ),
            onDismiss = { showGalleryDialog = false },
            onLaunchReal = {
                showGalleryDialog = false
                imagePickerLauncher.launch("image/*")
            },
            onSelectPreset = { preset ->
                showGalleryDialog = false
                onFileSelected(preset.name, preset.description, preset.getMockBytes(), preset.mimeType)
            }
        )
    }

    // PDF Document Picker Dialog
    if (showPdfDialog) {
        SimulatedFileDialog(
            title = "Import PDF Document",
            icon = Icons.Default.PictureAsPdf,
            presets = listOf(
                SimulatedFilePreset("BHMS_Pharmacy_Timetable.pdf", "PDF Timetable of Homeopathic Pharmacy & Anatomy Practicals", "application/pdf"),
                SimulatedFilePreset("Exam_Timetable_Official.pdf", "PDF Timetable listing June/July medical university theory papers", "application/pdf"),
                SimulatedFilePreset("College_Holiday_Circular.pdf", "PDF Circular declaring a general holiday next Friday", "application/pdf")
            ),
            onDismiss = { showPdfDialog = false },
            onLaunchReal = {
                showPdfDialog = false
                pdfPickerLauncher.launch("application/pdf")
            },
            onSelectPreset = { preset ->
                showPdfDialog = false
                onFileSelected(preset.name, preset.description, preset.getMockBytes(), preset.mimeType)
            }
        )
    }
}

@Composable
fun AIInputChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SimulatedFileDialog(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    presets: List<SimulatedFilePreset>,
    onDismiss: () -> Unit,
    onLaunchReal: () -> Unit,
    onSelectPreset: (SimulatedFilePreset) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "You can pick a real file from your device, or select one of these high-fidelity clinical and timetable preset templates to test the AI parser's extraction capabilities instantly:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                presets.forEach { preset ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPreset(preset) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (preset.mimeType.contains("pdf")) Icons.Default.PictureAsPdf else Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    preset.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    preset.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onLaunchReal,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Select Device File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun TimetablePreviewPanel(
    response: UnifiedParserResponse,
    onReplaceClick: (List<ParsedTimetableClass>) -> Unit,
    onCancelClick: () -> Unit
) {
    val editableTimetable = remember(response.extracted_timetable) {
        mutableStateListOf<ParsedTimetableClass>().apply {
            addAll(response.extracted_timetable)
        }
    }

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val workingDays = editableTimetable.map { it.day_of_week }.distinct().size
    val totalClasses = editableTimetable.filter { !it.is_lunch_break }.size
    val totalTeachers = editableTimetable.mapNotNull { it.teacher_name }.distinct().size
    val practicalSessions = editableTimetable.count { it.is_practical }
    val hasUncertain = editableTimetable.any { it.is_uncertain == true || it.confidence == "Low" || it.confidence == "Medium" }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("timetable_preview_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Proposed Timetable",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Add proposed period button
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("add_proposed_period_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Add Class",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stat Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimetableStatBadge(text = "✓ $workingDays Working Days", modifier = Modifier.weight(1f))
                TimetableStatBadge(text = "✓ $totalClasses Classes", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimetableStatBadge(text = "✓ $totalTeachers Teachers", modifier = Modifier.weight(1f))
                TimetableStatBadge(text = "✓ $practicalSessions Practicals", modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            if (hasUncertain) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Low OCR confidence detected. Please verify & edit highlighted items.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Text(
                "SCHEDULE PREVIEW (TAP TO EDIT)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Render classes by day
            val dayNames = mapOf(1 to "Monday", 2 to "Tuesday", 3 to "Wednesday", 4 to "Thursday", 5 to "Friday", 6 to "Saturday", 7 to "Sunday")
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                if (editableTimetable.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No classes proposed. Tap + to add manually.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val grouped = editableTimetable.groupBy { it.day_of_week }
                        items(grouped.keys.toList().sorted()) { dayOfWeek ->
                            val dayClasses = grouped[dayOfWeek] ?: emptyList()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = dayNames[dayOfWeek] ?: "Day $dayOfWeek",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                dayClasses.sortedBy { it.period_number }.forEach { cls ->
                                    val clsIndexInList = editableTimetable.indexOf(cls)
                                    val isLowConfidence = cls.is_uncertain == true || cls.confidence == "Low" || cls.confidence == "Medium"
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isLowConfidence) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                                else Color.Transparent
                                            )
                                            .border(
                                                width = if (isLowConfidence) 1.dp else 0.dp,
                                                color = if (isLowConfidence) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                if (clsIndexInList != -1) {
                                                    editingIndex = clsIndexInList
                                                }
                                            }
                                            .padding(6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = cls.start_time.split(" ")[0], // "08:30"
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(48.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = cls.subject,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = if (cls.is_lunch_break) FontWeight.Normal else FontWeight.Bold
                                                        ),
                                                        color = if (cls.is_lunch_break) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (isLowConfidence) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Warning,
                                                            contentDescription = "Uncertain",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                                if (!cls.is_lunch_break && (cls.teacher_name != null || cls.room != null)) {
                                                    val facultyStr = cls.teacher_name ?: ""
                                                    val roomStr = if (cls.room != null) " • Room ${cls.room}" else ""
                                                    Text(
                                                        text = "$facultyStr$roomStr",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                                if (isLowConfidence && !cls.notes.isNullOrBlank()) {
                                                    Text(
                                                        text = cls.notes,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    if (clsIndexInList != -1) {
                                                        editingIndex = clsIndexInList
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Period",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = {
                                                    if (clsIndexInList != -1) {
                                                        editableTimetable.removeAt(clsIndexInList)
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Period",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
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

            Spacer(modifier = Modifier.height(18.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = { onReplaceClick(editableTimetable.toList()) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimaryContainer, contentColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Replace Timetable")
                }
            }
        }
    }

    // Show Edit Dialog if selected
    if (editingIndex != null && editingIndex!! < editableTimetable.size) {
        val targetClass = editableTimetable[editingIndex!!]
        EditProposedClassDialog(
            cls = targetClass,
            onDismiss = { editingIndex = null },
            onSave = { updatedClass ->
                editableTimetable[editingIndex!!] = updatedClass
                editingIndex = null
            }
        )
    }

    // Show Add Dialog if selected
    if (showAddDialog) {
        AddProposedClassDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newClass ->
                editableTimetable.add(newClass)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProposedClassDialog(
    cls: ParsedTimetableClass,
    onDismiss: () -> Unit,
    onSave: (ParsedTimetableClass) -> Unit
) {
    var subject by remember { mutableStateOf(cls.subject) }
    var dayOfWeek by remember { mutableIntStateOf(cls.day_of_week) }
    var periodNumber by remember { mutableIntStateOf(cls.period_number) }
    var startTime by remember { mutableStateOf(cls.start_time) }
    var endTime by remember { mutableStateOf(cls.end_time) }
    var teacherName by remember { mutableStateOf(cls.teacher_name ?: "") }
    var room by remember { mutableStateOf(cls.room ?: "") }
    var isPractical by remember { mutableStateOf(cls.is_practical) }
    var isLunchBreak by remember { mutableStateOf(cls.is_lunch_break) }
    
    var confidence by remember { mutableStateOf(cls.confidence ?: "High") }
    var isUncertain by remember { mutableStateOf(cls.is_uncertain ?: false) }
    var notes by remember { mutableStateOf(cls.notes ?: "") }

    var expandedDayDropdown by remember { mutableStateOf(false) }
    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ParsedTimetableClass(
                            day_of_week = dayOfWeek,
                            period_number = periodNumber,
                            start_time = startTime,
                            end_time = endTime,
                            subject = subject,
                            teacher_name = teacherName.ifBlank { null },
                            room = room.ifBlank { null },
                            is_practical = isPractical,
                            is_lunch_break = isLunchBreak,
                            confidence = confidence,
                            is_uncertain = isUncertain,
                            notes = notes.ifBlank { null }
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                text = "Edit Proposed Class",
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
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject / Activity") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Day dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = dayNames.getOrNull(dayOfWeek - 1) ?: "Select Day",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Day of Week") },
                        trailingIcon = {
                            IconButton(onClick = { expandedDayDropdown = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Day")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = expandedDayDropdown,
                        onDismissRequest = { expandedDayDropdown = false }
                    ) {
                        dayNames.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    dayOfWeek = index + 1
                                    expandedDayDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = periodNumber.toString(),
                        onValueChange = { periodNumber = it.toIntOrNull() ?: periodNumber },
                        label = { Text("Period #") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it },
                        label = { Text("Room / Lab") },
                        modifier = Modifier.weight(1.5f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start (e.g., 09:30 AM)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End (e.g., 10:30 AM)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = teacherName,
                    onValueChange = { teacherName = it },
                    label = { Text("Faculty / Dr. Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Checkboxes for Practical & Lunch Break
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPractical, onCheckedChange = { isPractical = it })
                        Text("Practical / Lab", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isLunchBreak, onCheckedChange = { isLunchBreak = it })
                        Text("Lunch Break", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Confidence adjustment
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isUncertain,
                        onCheckedChange = { 
                            isUncertain = it
                            confidence = if (it) "Low" else "High"
                        }
                    )
                    Column {
                        Text("Mark as Uncertain / Low Confidence", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("This highlights the entry in the list to review later.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }

                if (isUncertain) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Reason / Notes (e.g., text blurry)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    )
}

@Composable
fun AddProposedClassDialog(
    onDismiss: () -> Unit,
    onSave: (ParsedTimetableClass) -> Unit
) {
    val defaultClass = ParsedTimetableClass(
        day_of_week = 1,
        period_number = 1,
        start_time = "09:00 AM",
        end_time = "10:00 AM",
        subject = ""
    )
    EditProposedClassDialog(
        cls = defaultClass,
        onDismiss = onDismiss,
        onSave = onSave
    )
}

@Composable
fun TimetableStatBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

