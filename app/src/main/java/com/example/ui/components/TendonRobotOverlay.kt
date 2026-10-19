package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.PlannerViewModel
import java.util.Locale
import kotlin.math.sin

/**
 * Floating "Tendon" Robot button that can be tapped from anywhere in the app
 * to talk with the Tendon medical AI voice robot.
 */
@Composable
fun TendonFloatingButton(
    viewModel: PlannerViewModel,
    modifier: Modifier = Modifier
) {
    val isSpeaking by viewModel.tendonVoiceManager.isSpeaking.collectAsStateWithLifecycle()
    val isListening by viewModel.tendonVoiceManager.isListening.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "tendon_fab_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSpeaking || isListening) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking || isListening) 550 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Surface(
        onClick = { viewModel.openTendonRobot() },
        modifier = modifier
            .scale(pulseScale)
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .testTag("tendon_floating_button"),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = if (isListening) {
                            listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))
                        } else if (isSpeaking) {
                            listOf(Color(0xFF10B981), Color(0xFF059669))
                        } else {
                            listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                        }
                    )
                )
                .border(
                    width = 1.5.dp,
                    color = Color.White.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mini Tendon Robot Head Icon
            TendonMiniAvatar(
                isSpeaking = isSpeaking,
                isListening = isListening,
                modifier = Modifier.size(28.dp)
            )

            Column {
                Text(
                    text = "Tendon AI",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = when {
                        isListening -> "Listening..."
                        isSpeaking -> "Speaking..."
                        else -> "Tap to Talk"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.88f)
                )
            }

            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Talk to Tendon",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Mini Canvas Robot Avatar for the floating button and headers.
 */
@Composable
fun TendonMiniAvatar(
    isSpeaking: Boolean,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val eyeColor = when {
        isListening -> Color(0xFF38BDF8)
        isSpeaking -> Color(0xFF34D399)
        else -> Color(0xFFA5B4FC)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Antenna
        drawLine(
            color = Color.White,
            start = Offset(w * 0.5f, h * 0.05f),
            end = Offset(w * 0.5f, h * 0.22f),
            strokeWidth = w * 0.08f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = eyeColor,
            radius = w * 0.1f,
            center = Offset(w * 0.5f, h * 0.07f)
        )

        // Robot Head Shell
        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(w * 0.12f, h * 0.22f),
            size = Size(w * 0.76f, h * 0.68f),
            cornerRadius = CornerRadius(w * 0.22f, w * 0.22f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.12f, h * 0.22f),
            size = Size(w * 0.76f, h * 0.68f),
            cornerRadius = CornerRadius(w * 0.22f, w * 0.22f),
            style = Stroke(width = w * 0.06f)
        )

        // Glowing Eyes
        drawCircle(
            color = eyeColor,
            radius = w * 0.09f,
            center = Offset(w * 0.35f, h * 0.48f)
        )
        drawCircle(
            color = eyeColor,
            radius = w * 0.09f,
            center = Offset(w * 0.65f, h * 0.48f)
        )

        // Smile / Voice Bar
        drawLine(
            color = eyeColor,
            start = Offset(w * 0.35f, h * 0.70f),
            end = Offset(w * 0.65f, h * 0.70f),
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Full Interactive Voice Assistant Modal for the "Tendon" Robot.
 * Students can speak naturally to schedule assessments, events, attendance, or ask questions,
 * and Tendon replies in voice form.
 */
@Composable
fun TendonRobotModal(
    viewModel: PlannerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isSpeaking by viewModel.tendonVoiceManager.isSpeaking.collectAsStateWithLifecycle()
    val isListening by viewModel.tendonVoiceManager.isListening.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.tendonVoiceManager.partialTranscript.collectAsStateWithLifecycle()
    val isVoiceEnabled by viewModel.tendonVoiceManager.isVoiceReplyEnabled.collectAsStateWithLifecycle()
    val statusText by viewModel.tendonVoiceManager.statusText.collectAsStateWithLifecycle()
    val isParsing by viewModel.isParsingMessage.collectAsStateWithLifecycle()

    val lastUserSpeech by viewModel.tendonLastUserSpeech.collectAsStateWithLifecycle()
    val lastReply by viewModel.tendonLastReply.collectAsStateWithLifecycle()

    // Fallback Intent Speech Recognizer
    val intentSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrBlank()) {
                viewModel.sendVoiceCommandToTendon(spoken)
            }
        }
    }

    val launchIntentFallback = {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Tendon (e.g. 'We have Anatomy assessment on 24 Oct')")
            }
            intentSpeechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Voice input not supported on this device. Tap any voice command below to test Tendon!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val startTendonListening = {
        viewModel.tendonVoiceManager.startListening(
            onResult = { spokenText ->
                viewModel.sendVoiceCommandToTendon(spokenText)
            },
            onFallbackRequired = {
                launchIntentFallback()
            }
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startTendonListening()
        } else {
            launchIntentFallback()
        }
    }

    val handleMicButtonClick = {
        if (isListening) {
            viewModel.tendonVoiceManager.stopListening()
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                startTendonListening()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Greet student by voice when Tendon opens for the first time in session
    LaunchedEffect(Unit) {
        if (!isSpeaking && lastUserSpeech.isBlank()) {
            viewModel.tendonVoiceManager.speak(
                "Hi! I am Tendon, your medical AI robot. Tap the mic and tell me any assessment date, class change, or question!"
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .heightIn(max = 680.dp)
                .testTag("tendon_robot_modal"),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar: Badge + Mute Toggle + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isListening -> Color(0xFF38BDF8)
                                        isSpeaking -> Color(0xFF10B981)
                                        isParsing -> Color(0xFFF59E0B)
                                        else -> Color(0xFF818CF8)
                                    }
                                )
                        )
                        Text(
                            text = "TENDON • VOICE AI ROBOT",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { viewModel.tendonVoiceManager.toggleVoiceReply() },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .testTag("tendon_mute_toggle")
                        ) {
                            Icon(
                                imageVector = if (isVoiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = if (isVoiceEnabled) "Mute Tendon Voice" else "Unmute Tendon Voice",
                                tint = if (isVoiceEnabled) Color(0xFF34D399) else Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .testTag("tendon_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Tendon",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Animated Tendon Robot Avatar
                AnimatedTendonRobotCharacter(
                    isListening = isListening,
                    isSpeaking = isSpeaking,
                    isThinking = isParsing,
                    modifier = Modifier
                        .size(150.dp)
                        .clickable { handleMicButtonClick() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = when {
                        isListening -> Color(0xFF0284C7).copy(alpha = 0.25f)
                        isSpeaking -> Color(0xFF059669).copy(alpha = 0.25f)
                        isParsing -> Color(0xFFD97706).copy(alpha = 0.25f)
                        else -> Color(0xFF1E293B)
                    }
                ) {
                    Text(
                        text = if (isParsing) "Tendon is scheduling..." else statusText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = when {
                            isListening -> Color(0xFF38BDF8)
                            isSpeaking -> Color(0xFF34D399)
                            isParsing -> Color(0xFFFBBF24)
                            else -> Color(0xFFCBD5E1)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Student Spoken Transcript Bubble
                val activeSpeech = if (partialTranscript.isNotBlank()) partialTranscript else lastUserSpeech
                AnimatedVisibility(
                    visible = activeSpeech.isNotBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (partialTranscript.isNotBlank()) "Listening..." else "You told Tendon:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF38BDF8)
                                )
                                Text(
                                    text = "\"$activeSpeech\"",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Tendon's Voice & Text Reply Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSpeaking) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFF334155)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Tendon's Voice Response",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF34D399)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isSpeaking) {
                                    TextButton(
                                        onClick = { viewModel.tendonVoiceManager.stopSpeaking() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop speaking",
                                            tint = Color(0xFFF87171),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Stop", fontSize = 11.sp, color = Color(0xFFF87171))
                                    }
                                } else {
                                    TextButton(
                                        onClick = {
                                            viewModel.tendonVoiceManager.setVoiceReplyEnabled(true)
                                            viewModel.tendonVoiceManager.speak(lastReply)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay,
                                            contentDescription = "Speak again",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Speak Aloud", fontSize = 11.sp, color = Color(0xFF38BDF8))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = lastReply.replace("**", ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE2E8F0),
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Big Pulsing Microphone Button
                val micScale by rememberInfiniteTransition(label = "mic_pulse").animateFloat(
                    initialValue = 1f,
                    targetValue = if (isListening) 1.15f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "mic_scale"
                )

                Box(contentAlignment = Alignment.Center) {
                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .scale(micScale)
                                .clip(CircleShape)
                                .background(Color(0xFF06B6D4).copy(alpha = 0.25f))
                        )
                    }

                    Button(
                        onClick = { handleMicButtonClick() },
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("tendon_mic_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isListening) Color(0xFFEF4444) else Color(0xFF4F46E5)
                        ),
                        contentPadding = PaddingValues(0.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop Listening" else "Talk to Tendon",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isListening) "Listening... Tap to stop" else "Tap Microphone & Speak to Tendon",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Voice Command Starters (1-Tap to simulate or trigger voice instructions)
                Text(
                    text = "Or tap a voice instruction to talk to Tendon:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                val voicePresets = listOf(
                    "🎙️ \"We have Anatomy assessment on 24th October at 10 AM\"" to
                            "We have Anatomy assessment on 24th October at 10:00 AM",
                    "🎙️ \"We have 3 upcoming assessments\"" to
                            "Upcoming Internal Assessments:\n1. Anatomy - 24/10/2026 at 10:00 AM (Upper Limb)\n2. Physiology - 28/10/2026 at 11:30 AM (Nerve Muscle)\n3. Biochemistry - 02/11/2026 at 02:00 PM (Metabolism)",
                    "🎙️ \"What classes do I have today?\"" to
                            "What classes do I have today?",
                    "🎙️ \"Mark me present for all classes today\"" to
                            "Mark me present for all classes today",
                    "🎙️ \"When are my upcoming exams?\"" to
                            "When are my upcoming exams?",
                    "🎙️ \"We have Clinical Posting tomorrow at 9 AM\"" to
                            "We have Clinical Posting tomorrow at 09:00 AM"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    voicePresets.forEach { (label, command) ->
                        Surface(
                            onClick = { viewModel.sendVoiceCommandToTendon(command) },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated Robot Avatar for Tendon with glowing eyes, antenna, and live audio waveform.
 */
@Composable
fun AnimatedTendonRobotCharacter(
    isListening: Boolean,
    isSpeaking: Boolean,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tendon_avatar_anim")

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )

    val primaryColor = when {
        isListening -> Color(0xFF38BDF8) // Cyan when listening
        isSpeaking -> Color(0xFF34D399)  // Emerald when speaking
        isThinking -> Color(0xFFFBBF24)  // Amber when thinking
        else -> Color(0xFF818CF8)        // Indigo idle
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dy = floatOffset

        // Outer Ambient Halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryColor.copy(alpha = 0.28f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.5f + dy),
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = Offset(w * 0.5f, h * 0.5f + dy)
        )

        // Antenna Stem & Glowing Orb
        drawLine(
            color = Color(0xFF94A3B8),
            start = Offset(w * 0.5f, h * 0.10f + dy),
            end = Offset(w * 0.5f, h * 0.24f + dy),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = primaryColor,
            radius = w * 0.055f,
            center = Offset(w * 0.5f, h * 0.09f + dy)
        )

        // Side Ears / Audio Receivers
        drawRoundRect(
            color = primaryColor.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.11f, h * 0.42f + dy),
            size = Size(w * 0.07f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )
        drawRoundRect(
            color = primaryColor.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.82f, h * 0.42f + dy),
            size = Size(w * 0.07f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )

        // Main Robot Head Chassis
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(w * 0.17f, h * 0.24f + dy),
            size = Size(w * 0.66f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
        )
        drawRoundRect(
            color = primaryColor.copy(alpha = 0.8f),
            topLeft = Offset(w * 0.17f, h * 0.24f + dy),
            size = Size(w * 0.66f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
            style = Stroke(width = w * 0.025f)
        )

        // Inner Visor Screen
        drawRoundRect(
            color = Color(0xFF090D16),
            topLeft = Offset(w * 0.23f, h * 0.31f + dy),
            size = Size(w * 0.54f, h * 0.42f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )

        // Robot Eyes
        val eyeRadius = w * 0.06f
        drawCircle(
            color = primaryColor,
            radius = eyeRadius,
            center = Offset(w * 0.37f, h * 0.45f + dy)
        )
        drawCircle(
            color = primaryColor,
            radius = eyeRadius,
            center = Offset(w * 0.63f, h * 0.45f + dy)
        )

        // Eye Specular Highlights
        drawCircle(
            color = Color.White,
            radius = eyeRadius * 0.35f,
            center = Offset(w * 0.35f, h * 0.43f + dy)
        )
        drawCircle(
            color = Color.White,
            radius = eyeRadius * 0.35f,
            center = Offset(w * 0.61f, h * 0.43f + dy)
        )

        // Animated Soundwave Mouth inside Visor
        val barCount = 7
        val startX = w * 0.33f
        val endX = w * 0.67f
        val stepX = (endX - startX) / (barCount - 1)
        val centerY = h * 0.61f + dy

        for (i in 0 until barCount) {
            val x = startX + i * stepX
            val activeAmplitude = if (isSpeaking || isListening) {
                val wave = sin(wavePhase + i * 0.9f)
                (h * 0.02f) + kotlin.math.abs(wave) * (h * 0.055f)
            } else {
                h * 0.012f
            }

            drawLine(
                color = primaryColor,
                start = Offset(x, centerY - activeAmplitude),
                end = Offset(x, centerY + activeAmplitude),
                strokeWidth = w * 0.024f,
                cap = StrokeCap.Round
            )
        }
    }
}
