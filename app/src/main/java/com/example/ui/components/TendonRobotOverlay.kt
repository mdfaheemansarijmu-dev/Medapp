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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * Floating "Tendon" Robot button rendered in a sleek transparent glassmorphic style
 * with the white & glowing-cyan 3D robot avatar.
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
        targetValue = if (isSpeaking || isListening) 1.10f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking || isListening) 550 else 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowColor = when {
        isListening -> Color(0xFF00F0FF)
        isSpeaking -> Color(0xFF38BDF8)
        else -> Color(0xFF3B82F6)
    }

    Box(
        modifier = modifier
            .scale(pulseScale)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F294A).copy(alpha = 0.72f),
                        Color(0xFF071326).copy(alpha = 0.55f)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.95f),
                        Color(0xFF60A5FA).copy(alpha = 0.35f),
                        glowColor.copy(alpha = 0.85f)
                    )
                ),
                shape = CircleShape
            )
            .clickable { viewModel.openTendonRobot() }
            .padding(10.dp)
            .testTag("tendon_floating_button"),
        contentAlignment = Alignment.Center
    ) {
        TendonMiniAvatar(
            isSpeaking = isSpeaking,
            isListening = isListening,
            modifier = Modifier.size(42.dp)
        )
    }
}

/**
 * Mini 3D White & Glowing Cyan Robot Avatar for the floating button and chat headers.
 */
@Composable
fun TendonMiniAvatar(
    isSpeaking: Boolean,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val cyanGlow = when {
        isListening -> Color(0xFF00F5FF)
        isSpeaking -> Color(0xFF38BDF8)
        else -> Color(0xFF00D2FF)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Subtle outer cyan halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cyanGlow.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.52f),
                radius = w * 0.5f
            ),
            radius = w * 0.5f,
            center = Offset(w * 0.5f, h * 0.52f)
        )

        // Antenna stem
        drawLine(
            color = Color(0xFFE2E8F0),
            start = Offset(w * 0.5f, h * 0.08f),
            end = Offset(w * 0.5f, h * 0.24f),
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round
        )
        // Glowing Antenna Orb
        drawCircle(
            color = cyanGlow,
            radius = w * 0.09f,
            center = Offset(w * 0.5f, h * 0.08f)
        )

        // Side Ear Pods
        drawRoundRect(
            color = Color(0xFFCBD5E1),
            topLeft = Offset(w * 0.06f, h * 0.40f),
            size = Size(w * 0.09f, h * 0.24f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )
        drawRoundRect(
            color = Color(0xFFCBD5E1),
            topLeft = Offset(w * 0.85f, h * 0.40f),
            size = Size(w * 0.09f, h * 0.24f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Glossy 3D White Helmet Shell
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFDBEAFE), Color(0xFF93C5FD))
            ),
            topLeft = Offset(w * 0.13f, h * 0.22f),
            size = Size(w * 0.74f, h * 0.64f),
            cornerRadius = CornerRadius(w * 0.28f, w * 0.28f)
        )

        // Dark Visor Screen
        drawRoundRect(
            color = Color(0xFF061021),
            topLeft = Offset(w * 0.21f, h * 0.31f),
            size = Size(w * 0.58f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
        )

        // Glowing Cyan Eyes
        drawCircle(
            color = cyanGlow,
            radius = w * 0.075f,
            center = Offset(w * 0.37f, h * 0.49f)
        )
        drawCircle(
            color = cyanGlow,
            radius = w * 0.075f,
            center = Offset(w * 0.63f, h * 0.49f)
        )

        // Cute Cyan Smile Curve
        val smilePath = Path().apply {
            moveTo(w * 0.42f, h * 0.61f)
            quadraticTo(w * 0.50f, h * 0.69f, w * 0.58f, h * 0.61f)
        }
        drawPath(
            path = smilePath,
            color = cyanGlow,
            style = Stroke(width = w * 0.055f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Transparent Callout-Style Voice Assistant Overlay for the "Tendon" Robot.
 * Opens with a transparent backdrop, the floating 3D White & Cyan AI Robot,
 * and ONLY the text of what Tendon is telling the user inside a transparent
 * glassmorphic callout graphic (speech bubble with pointer tail).
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
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Tendon (e.g. 'What is today's running assignment?')")
            }
            intentSpeechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Voice input not supported on this device.",
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
                "Hello! I'm Tendon AI. Ask me anything about your classes, assignments, or assessments!"
            )
        }
    }

    // Determine the single text that Tendon is currently telling (or hearing)
    val rawDisplayedText = when {
        isListening && partialTranscript.isNotBlank() -> partialTranscript
        isListening -> "Listening... Speak now"
        isParsing -> "Checking your schedule..."
        lastReply.isNotBlank() -> lastReply
        else -> "Hello! 👋\nI'm Tendon AI.\nAsk me anything — classes, running assignments, assessments, or schedule an event by voice."
    }

    val cleanDisplayedText = remember(rawDisplayedText) {
        rawDisplayedText
            .replace("**", "")
            .replace("*", "")
            .trim()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Pure transparent full-screen overlay; tapping outside dismisses
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF020817).copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .testTag("tendon_robot_modal"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 420.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks on the callout/robot so they don't dismiss
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 1. TRANSPARENT GLASSMORPHIC CALLOUT GRAPHIC (Only the text what it is telling!)
                AnimatedVisibility(
                    visible = cleanDisplayedText.isNotBlank(),
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.92f)
                ) {
                    TransparentCalloutBubble(
                        text = cleanDisplayedText,
                        isSpeaking = isSpeaking,
                        isListening = isListening,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSpeaking) {
                                    viewModel.tendonVoiceManager.stopSpeaking()
                                } else {
                                    viewModel.tendonVoiceManager.setVoiceReplyEnabled(true)
                                    viewModel.tendonVoiceManager.speak(cleanDisplayedText)
                                }
                            }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2. FLOATING 3D WHITE & CYAN ROBOT CHARACTER (Tap robot to talk / listen)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(230.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { handleMicButtonClick() }
                        )
                        .testTag("tendon_mic_button")
                ) {
                    AnimatedTendonRobotCharacter(
                        isListening = isListening,
                        isSpeaking = isSpeaking,
                        isThinking = isParsing,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Transparent glassmorphic speech callout graphic with a bottom pointer tail pointing to the robot.
 * Displays ONLY the text that Tendon is telling the user.
 */
@Composable
fun TransparentCalloutBubble(
    text: String,
    isSpeaking: Boolean,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val tailWidthPx = with(density) { 22.dp.toPx() }
    val tailHeightPx = with(density) { 14.dp.toPx() }
    val cornerRadiusPx = with(density) { 22.dp.toPx() }

    val calloutShape = remember(tailWidthPx, tailHeightPx, cornerRadiusPx) {
        GenericShape { size, _ ->
            val rectHeight = (size.height - tailHeightPx).coerceAtLeast(0f)
            val roundRect = RoundRect(
                rect = Rect(0f, 0f, size.width, rectHeight),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
            addRoundRect(roundRect)

            // Pointer triangle tail centered at the bottom pointing down at the robot
            val centerX = size.width * 0.5f
            moveTo(centerX - tailWidthPx / 2f, rectHeight - 1f)
            lineTo(centerX, size.height)
            lineTo(centerX + tailWidthPx / 2f, rectHeight - 1f)
            close()
        }
    }

    val borderCyan = when {
        isListening -> Color(0xFF00F5FF)
        isSpeaking -> Color(0xFF38BDF8)
        else -> Color(0xFF60A5FA)
    }

    Box(
        modifier = modifier
            .clip(calloutShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F294A).copy(alpha = 0.56f),
                        Color(0xFF08172E).copy(alpha = 0.48f)
                    )
                ),
                shape = calloutShape
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        borderCyan.copy(alpha = 0.92f),
                        Color(0xFF93C5FD).copy(alpha = 0.38f),
                        borderCyan.copy(alpha = 0.85f)
                    )
                ),
                shape = calloutShape
            )
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 16.dp + 14.dp // extra bottom padding for tail height
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 23.sp,
                    letterSpacing = 0.1.sp
                ),
                color = Color(0xFFF8FAFC),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 3D-Styled Floating White & Glowing Cyan Robot Character matching the MedPulse AI reference design:
 * - Concentric glowing blue/cyan energy rings behind the robot
 * - Glossy white 3D rounded helmet with side ear pods and top glowing cyan antenna
 * - Deep dark visor with two glowing cyan circular eyes and a cute cyan curved smile
 * - Floating white 3D torso with rounded arms and a glowing cyan "AI" badge on the chest
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
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )

    val ringPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_pulse"
    )

    val cyanAccent = when {
        isListening -> Color(0xFF00F5FF)
        isSpeaking -> Color(0xFF38BDF8)
        isThinking -> Color(0xFF60A5FA)
        else -> Color(0xFF00D2FF)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dy = floatOffset

        val centerX = w * 0.5f
        val centerY = h * 0.48f + dy

        // 1. Outer Ambient Blue/Cyan Halo & Concentric Energy Rings (like reference image)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    cyanAccent.copy(alpha = 0.28f),
                    Color(0xFF1D4ED8).copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = w * 0.48f * ringPulse
            ),
            radius = w * 0.48f * ringPulse,
            center = Offset(centerX, centerY)
        )

        drawCircle(
            color = cyanAccent.copy(alpha = 0.30f),
            radius = w * 0.42f * ringPulse,
            center = Offset(centerX, centerY),
            style = Stroke(width = w * 0.008f)
        )

        drawCircle(
            color = cyanAccent.copy(alpha = 0.45f),
            radius = w * 0.34f / ringPulse,
            center = Offset(centerX, centerY),
            style = Stroke(width = w * 0.010f)
        )

        // 2. Top Antenna Stem & Glowing Cyan Orb
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, Color(0xFF93C5FD))
            ),
            start = Offset(centerX, h * 0.11f + dy),
            end = Offset(centerX, h * 0.22f + dy),
            strokeWidth = w * 0.032f,
            cap = StrokeCap.Round
        )
        // Base ring of antenna
        drawOval(
            color = Color(0xFFDBEAFE),
            topLeft = Offset(centerX - w * 0.045f, h * 0.205f + dy),
            size = Size(w * 0.09f, h * 0.03f)
        )
        // Glowing orb on antenna tip
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, cyanAccent, cyanAccent.copy(alpha = 0f)),
                center = Offset(centerX, h * 0.095f + dy),
                radius = w * 0.065f
            ),
            radius = w * 0.065f,
            center = Offset(centerX, h * 0.095f + dy)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.032f,
            center = Offset(centerX, h * 0.095f + dy)
        )

        // 3. Floating Left & Right Arms (3D glossy white)
        val armBrush = Brush.verticalGradient(
            colors = listOf(Color.White, Color(0xFFDBEAFE), Color(0xFF60A5FA))
        )
        // Left Arm
        drawRoundRect(
            brush = armBrush,
            topLeft = Offset(w * 0.21f, h * 0.62f + dy),
            size = Size(w * 0.085f, h * 0.20f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
        // Right Arm
        drawRoundRect(
            brush = armBrush,
            topLeft = Offset(w * 0.705f, h * 0.62f + dy),
            size = Size(w * 0.085f, h * 0.20f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )

        // 4. Floating 3D White Torso with Cyan "AI" Core
        val torsoPath = Path().apply {
            moveTo(w * 0.31f, h * 0.61f + dy)
            lineTo(w * 0.69f, h * 0.61f + dy)
            quadraticTo(w * 0.68f, h * 0.87f + dy, centerX, h * 0.90f + dy)
            quadraticTo(w * 0.32f, h * 0.87f + dy, w * 0.31f, h * 0.61f + dy)
            close()
        }
        drawPath(
            path = torsoPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFEFF6FF), Color(0xFF93C5FD))
            )
        )
        drawPath(
            path = torsoPath,
            color = cyanAccent.copy(alpha = 0.55f),
            style = Stroke(width = w * 0.012f)
        )

        // Chest Glowing Cyan "AI" Core Circle
        val chestCenter = Offset(centerX, h * 0.73f + dy)
        drawCircle(
            color = cyanAccent,
            radius = w * 0.082f,
            center = chestCenter
        )
        drawCircle(
            color = Color(0xFF061021),
            radius = w * 0.068f,
            center = chestCenter
        )
        // Subtle inner pulse inside chest core when listening/speaking
        val corePulse = if (isListening || isSpeaking) (0.045f + 0.015f * sin(wavePhase)) else 0.04f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cyanAccent, Color.Transparent),
                center = chestCenter,
                radius = w * corePulse * 1.6f
            ),
            radius = w * corePulse * 1.6f,
            center = chestCenter
        )

        // 5. Side Ear Pods on Head
        drawRoundRect(
            brush = armBrush,
            topLeft = Offset(w * 0.16f, h * 0.34f + dy),
            size = Size(w * 0.06f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )
        drawRoundRect(
            brush = armBrush,
            topLeft = Offset(w * 0.78f, h * 0.34f + dy),
            size = Size(w * 0.06f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )

        // 6. Main 3D Glossy White Helmet Head
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFF1F5F9),
                    Color(0xFFBFDBFE)
                )
            ),
            topLeft = Offset(w * 0.20f, h * 0.22f + dy),
            size = Size(w * 0.60f, h * 0.37f),
            cornerRadius = CornerRadius(w * 0.20f, w * 0.20f)
        )
        drawRoundRect(
            color = cyanAccent.copy(alpha = 0.65f),
            topLeft = Offset(w * 0.20f, h * 0.22f + dy),
            size = Size(w * 0.60f, h * 0.37f),
            cornerRadius = CornerRadius(w * 0.20f, w * 0.20f),
            style = Stroke(width = w * 0.012f)
        )

        // 7. Dark Rounded Visor Face Screen
        drawRoundRect(
            color = Color(0xFF050D1A),
            topLeft = Offset(w * 0.26f, h * 0.275f + dy),
            size = Size(w * 0.48f, h * 0.26f),
            cornerRadius = CornerRadius(w * 0.13f, w * 0.13f)
        )

        // 8. Glowing Cyan Circular Eyes
        val eyeRadius = w * 0.048f
        val leftEyeCenter = Offset(w * 0.39f, h * 0.385f + dy)
        val rightEyeCenter = Offset(w * 0.61f, h * 0.385f + dy)

        // Eye outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cyanAccent.copy(alpha = 0.8f), Color.Transparent),
                center = leftEyeCenter,
                radius = eyeRadius * 1.9f
            ),
            radius = eyeRadius * 1.9f,
            center = leftEyeCenter
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cyanAccent.copy(alpha = 0.8f), Color.Transparent),
                center = rightEyeCenter,
                radius = eyeRadius * 1.9f
            ),
            radius = eyeRadius * 1.9f,
            center = rightEyeCenter
        )

        // Eye cores
        drawCircle(
            color = cyanAccent,
            radius = eyeRadius,
            center = leftEyeCenter
        )
        drawCircle(
            color = cyanAccent,
            radius = eyeRadius,
            center = rightEyeCenter
        )
        // Specular highlight
        drawCircle(
            color = Color.White,
            radius = eyeRadius * 0.38f,
            center = Offset(leftEyeCenter.x - eyeRadius * 0.25f, leftEyeCenter.y - eyeRadius * 0.25f)
        )
        drawCircle(
            color = Color.White,
            radius = eyeRadius * 0.38f,
            center = Offset(rightEyeCenter.x - eyeRadius * 0.25f, rightEyeCenter.y - eyeRadius * 0.25f)
        )

        // 9. Cute Glowing Cyan Smile Arc (animates gently when speaking/listening)
        val smileDepth = if (isSpeaking || isListening) {
            h * (0.485f + 0.018f * kotlin.math.abs(sin(wavePhase)))
        } else {
            h * 0.48f
        }
        val smilePath = Path().apply {
            moveTo(w * 0.455f, h * 0.445f + dy)
            quadraticTo(centerX, smileDepth + dy, w * 0.545f, h * 0.445f + dy)
        }
        drawPath(
            path = smilePath,
            color = cyanAccent,
            style = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
        )
    }
}
