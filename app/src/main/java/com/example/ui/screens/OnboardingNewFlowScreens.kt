package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MedicalCourse
import kotlinx.coroutines.launch

// =========================================================================================
// 1. APP FUNCTION SHOWCASE (As shown in watermarked_img reference)
// =========================================================================================

data class AppFunctionPage(
    val title: String,
    val subtitle: String,
    val illustrationType: IllustrationType
)

enum class IllustrationType {
    SPLASH_LOGO,
    TIMETABLE,
    ATTENDANCE,
    AI_PARSER,
    OVERVIEW
}

@Composable
fun OnboardingFunctionsScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val pages = remember {
        listOf(
            AppFunctionPage(
                title = "MEDPULSE",
                subtitle = "Your college, organized.\nClasses, Attendance, Timetables, and Clinical Postings in one place.",
                illustrationType = IllustrationType.SPLASH_LOGO
            ),
            AppFunctionPage(
                title = "SMART MEDICAL TIMETABLE",
                subtitle = "Track lectures, clinical postings, and practical labs with zero schedule clashes.",
                illustrationType = IllustrationType.TIMETABLE
            ),
            AppFunctionPage(
                title = "REAL-TIME ATTENDANCE",
                subtitle = "Track university eligibility criteria with timely shortage warnings.",
                illustrationType = IllustrationType.ATTENDANCE
            ),
            AppFunctionPage(
                title = "AI SCHEDULE & NOTICE PARSER",
                subtitle = "Snap a photo of your department board or WhatsApp circulars for instant schedule extraction.",
                illustrationType = IllustrationType.AI_PARSER
            ),
            AppFunctionPage(
                title = "Your college, organized.",
                subtitle = "Everything a medical student needs to excel, stay compliant, and never miss a clinical duty.",
                illustrationType = IllustrationType.OVERVIEW
            )
        )
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages.size })

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top row: Skip button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pagerState.currentPage < pages.size - 1) {
                TextButton(
                    onClick = onGetStarted,
                    modifier = Modifier.testTag("skip_intro_button")
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
        }

        // Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (page.illustrationType) {
                    IllustrationType.SPLASH_LOGO -> {
                        MedPulseBrandLogo(modifier = Modifier.size(160.dp))
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = "MEDPULSE",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Your college, organized.",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Smart Medical Timetable  •  Real-Time Attendance  •  AI Parser",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    IllustrationType.TIMETABLE -> {
                        TimetableGraphicIllustration(modifier = Modifier.size(200.dp))
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = page.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    IllustrationType.ATTENDANCE -> {
                        AttendanceGraphicIllustration(modifier = Modifier.size(200.dp))
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = page.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    IllustrationType.AI_PARSER -> {
                        AiParserGraphicIllustration(modifier = Modifier.size(200.dp))
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = page.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    IllustrationType.OVERVIEW -> {
                        MedPulseBrandLogo(modifier = Modifier.size(80.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "MEDPULSE",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Your college, organized.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AppFeatureHighlightRow(
                                    icon = Icons.Default.CalendarMonth,
                                    title = "Smart Medical Timetable",
                                    description = "Organize theory lectures, clinical postings, and practical labs with zero schedule clashes."
                                )
                                AppFeatureHighlightRow(
                                    icon = Icons.Default.FactCheck,
                                    title = "Real-Time Attendance",
                                    description = "Track 75% university eligibility criteria with timely shortage warnings."
                                )
                                AppFeatureHighlightRow(
                                    icon = Icons.Default.AutoAwesome,
                                    title = "AI Schedule & Notice Parser",
                                    description = "Snap a photo of your department board or WhatsApp circulars for instant schedule extraction."
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Bottom Controls: Page Dots + Next / Get Started Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Page Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        label = "dotWidth"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        label = "dotColor"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            // Next / Get Started Button
            val isLastPage = pagerState.currentPage == pages.size - 1
            Button(
                onClick = {
                    if (isLastPage) {
                        onGetStarted()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .height(50.dp)
                    .testTag(if (isLastPage) "get_started_intro_button" else "next_intro_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isLastPage) "Get Started" else "Next",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AppFeatureHighlightRow(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

// =========================================================================================
// 2. STUDENT NAME SCREEN (Step 1 of 4: "starts with name of student")
// =========================================================================================

@Composable
fun OnboardingStudentNameScreen(
    studentName: String,
    onNameChanged: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar with Step Indicator "1 of 4"
            OnboardingStepHeader(
                currentStepNumber = 1,
                totalSteps = 4,
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Headline
            Text(
                text = "What is your name?",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter your full name to personalize your medical schedule, attendance records, and clinical duty plans.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Medical Student Badge Graphic
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.size(92.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = "Medical student icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Name Input Field
            OutlinedTextField(
                value = studentName,
                onValueChange = onNameChanged,
                label = { Text("Your Full Name") },
                placeholder = { Text("e.g., Alex Sharma or Dr. Priya Patel") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (studentName.isNotEmpty()) {
                        IconButton(onClick = { onNameChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear name"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (studentName.isNotBlank()) {
                            onContinue()
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_name_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Helpful Information Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Your name will appear on attendance reports, class logs, and duty records. You can update it anytime in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Continue Button
        Button(
            onClick = onContinue,
            enabled = studentName.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("name_continue_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// =========================================================================================
// 3. AUTH CHOICE SCREEN (Login option with option "Start Now without log in")
// =========================================================================================

@Composable
fun OnboardingAuthChoiceScreen(
    studentName: String,
    selectedCourse: MedicalCourse?,
    selectedCollege: String,
    selectedYear: String?,
    selectedBatch: String,
    isAuthenticating: Boolean,
    authError: String?,
    onBack: () -> Unit,
    onStartNowWithoutLogin: () -> Unit,
    onGoogleClick: () -> Unit,
    onMobileClick: () -> Unit,
    onEmailClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayName = if (studentName.isNotBlank()) studentName else "Doctor"
    val courseTitle = selectedCourse?.displayName ?: "Medical Studies"
    val yearDisplay = selectedYear ?: "1st Year"

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Welcome Badge
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "MedPulse heart",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome, $displayName!",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Sign in to backup and sync your schedule across devices, or jump right into MedPulse immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Summary Card of Student Configuration
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$courseTitle • $yearDisplay ($selectedBatch)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedCollege,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!authError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = authError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom CTAs: "Start Now (Without Log In)" as requested + Sign in options
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. PRIMARY CTA: Confirm & Finish Setup (Account Linked)
            Button(
                onClick = onStartNowWithoutLogin,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_now_without_login_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Confirm & Finish Setup",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Clean Divider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = "switch account or sync method",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // 2. Continue with Google
            OutlinedButton(
                onClick = onGoogleClick,
                enabled = !isAuthenticating,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("continue_google_button"),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Google",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Switch Google Account",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 3. Continue with Mobile
            OutlinedButton(
                onClick = onMobileClick,
                enabled = !isAuthenticating,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("continue_mobile_button"),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneIphone,
                        contentDescription = "Phone",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sign in with Mobile OTP",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 4. Sign in with Email
            TextButton(
                onClick = onEmailClick,
                modifier = Modifier.testTag("login_link_button")
            ) {
                Text(
                    text = "Sign in with different Email & Password",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "🔒 Cloud Auto-Backup Active: All schedules and attendance records are continuously backed up to your account.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// =========================================================================================
// 4. VECTOR GRAPHIC ILLUSTRATIONS (Matching watermarked_img reference)
// =========================================================================================

/**
 * MedPulse Brand Heartbeat Logo:
 * Heart shape with an ECG line passing through and a medical plus sign on the top right.
 */
@Composable
fun MedPulseBrandLogo(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Soft circular backdrop
        drawCircle(
            color = containerColor,
            radius = w * 0.46f,
            center = Offset(cx, cy)
        )

        // Draw stylized heart outline
        val heartPath = Path().apply {
            moveTo(cx, cy + h * 0.28f)
            cubicTo(
                cx - w * 0.42f, cy - h * 0.05f,
                cx - w * 0.36f, cy - h * 0.32f,
                cx, cy - h * 0.12f
            )
            cubicTo(
                cx + w * 0.36f, cy - h * 0.32f,
                cx + w * 0.42f, cy - h * 0.05f,
                cx, cy + h * 0.28f
            )
            close()
        }

        drawPath(
            path = heartPath,
            color = primaryColor,
            style = Stroke(width = w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw horizontal ECG pulse wave crossing the heart
        val ecgPath = Path().apply {
            moveTo(cx - w * 0.30f, cy + h * 0.02f)
            lineTo(cx - w * 0.12f, cy + h * 0.02f)
            lineTo(cx - w * 0.06f, cy - h * 0.15f)
            lineTo(cx + w * 0.04f, cy + h * 0.15f)
            lineTo(cx + w * 0.10f, cy - h * 0.05f)
            lineTo(cx + w * 0.14f, cy + h * 0.02f)
            lineTo(cx + w * 0.30f, cy + h * 0.02f)
        }

        drawPath(
            path = ecgPath,
            color = primaryColor,
            style = Stroke(width = w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Medical plus sign near top-right
        val plusX = cx + w * 0.22f
        val plusY = cy - h * 0.22f
        val arm = w * 0.07f
        drawLine(
            color = primaryColor,
            start = Offset(plusX - arm, plusY),
            end = Offset(plusX + arm, plusY),
            strokeWidth = w * 0.04f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = primaryColor,
            start = Offset(plusX, plusY - arm),
            end = Offset(plusX, plusY + arm),
            strokeWidth = w * 0.04f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Timetable Graphic Illustration:
 * Circular mint container with hanging wire-bound calendar, clocks, and "Class" blocks.
 */
@Composable
fun TimetableGraphicIllustration(modifier: Modifier = Modifier) {
    val mintBg = Color(0xFFE8F5E9)
    val tealColor = Color(0xFF0F766E)
    val lightTeal = Color(0xFF99F6E4)
    val cardBg = Color.White
    val outlineColor = Color(0xFF1E293B)
    val textLineColor = Color(0xFF64748B)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Soft green circular backdrop (like reference watermarked_img)
        drawCircle(
            color = mintBg,
            radius = w * 0.46f,
            center = Offset(cx, cy)
        )

        // Calendar Sheet Background
        val calWidth = w * 0.62f
        val calHeight = h * 0.64f
        val calLeft = cx - calWidth / 2f
        val calTop = cy - calHeight / 2f + h * 0.04f

        drawRoundRect(
            color = cardBg,
            topLeft = Offset(calLeft, calTop),
            size = Size(calWidth, calHeight),
            cornerRadius = CornerRadius(16f, 16f)
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(calLeft, calTop),
            size = Size(calWidth, calHeight),
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = 3f)
        )

        // Calendar Top Header Strip
        drawRoundRect(
            color = tealColor,
            topLeft = Offset(calLeft, calTop),
            size = Size(calWidth, calHeight * 0.22f),
            cornerRadius = CornerRadius(16f, 16f)
        )

        // Spiral binder rings at the top
        val ringCount = 4
        val ringSpacing = calWidth / (ringCount + 1)
        for (i in 1..ringCount) {
            val rx = calLeft + i * ringSpacing
            drawLine(
                color = outlineColor,
                start = Offset(rx, calTop - 8f),
                end = Offset(rx, calTop + 12f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        // Schedule cards ("Class", "Class", "Class")
        val blockH = calHeight * 0.18f
        val blockW = calWidth * 0.78f
        val blockLeft = calLeft + calWidth * 0.11f
        val startY = calTop + calHeight * 0.30f
        val spacing = calHeight * 0.06f

        val blockColors = listOf(Color(0xFFCCFBF1), Color(0xFFFEF3C7), Color(0xFFE0E7FF))

        for (i in 0..2) {
            val by = startY + i * (blockH + spacing)
            drawRoundRect(
                color = blockColors[i],
                topLeft = Offset(blockLeft, by),
                size = Size(blockW, blockH),
                cornerRadius = CornerRadius(8f, 8f)
            )
            // Left color bar
            drawRoundRect(
                color = tealColor,
                topLeft = Offset(blockLeft, by),
                size = Size(blockW * 0.12f, blockH),
                cornerRadius = CornerRadius(8f, 8f)
            )
            // Text placeholder line
            drawLine(
                color = textLineColor,
                start = Offset(blockLeft + blockW * 0.22f, by + blockH * 0.5f),
                end = Offset(blockLeft + blockW * 0.82f, by + blockH * 0.5f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        // Floating Analog Clock 1 (Top Left)
        val clock1X = cx - w * 0.32f
        val clock1Y = cy - h * 0.18f
        val clock1R = w * 0.12f
        drawCircle(color = cardBg, radius = clock1R, center = Offset(clock1X, clock1Y))
        drawCircle(color = outlineColor, radius = clock1R, center = Offset(clock1X, clock1Y), style = Stroke(width = 3f))
        // Clock hands (10:10)
        drawLine(color = outlineColor, start = Offset(clock1X, clock1Y), end = Offset(clock1X - clock1R * 0.5f, clock1Y - clock1R * 0.5f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(color = tealColor, start = Offset(clock1X, clock1Y), end = Offset(clock1X + clock1R * 0.6f, clock1Y - clock1R * 0.4f), strokeWidth = 3f, cap = StrokeCap.Round)

        // Floating Analog Clock 2 (Bottom Right)
        val clock2X = cx + w * 0.33f
        val clock2Y = cy + h * 0.22f
        val clock2R = w * 0.11f
        drawCircle(color = cardBg, radius = clock2R, center = Offset(clock2X, clock2Y))
        drawCircle(color = outlineColor, radius = clock2R, center = Offset(clock2X, clock2Y), style = Stroke(width = 3f))
        // Clock hands (2:40)
        drawLine(color = outlineColor, start = Offset(clock2X, clock2Y), end = Offset(clock2X + clock2R * 0.5f, clock2Y - clock2R * 0.2f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(color = tealColor, start = Offset(clock2X, clock2Y), end = Offset(clock2X - clock2R * 0.4f, clock2Y + clock2R * 0.5f), strokeWidth = 3f, cap = StrokeCap.Round)
    }
}

/**
 * Attendance Graphic Illustration:
 * Circular mint container with Attendance Dashboard browser/window, circular gauges (78%, 56%), and shortage warnings.
 */
@Composable
fun AttendanceGraphicIllustration(modifier: Modifier = Modifier) {
    val mintBg = Color(0xFFE8F5E9)
    val cardBg = Color.White
    val outlineColor = Color(0xFF1E293B)
    val tealGauge = Color(0xFF0F766E)
    val warningGauge = Color(0xFFF59E0B)
    val alertRed = Color(0xFFEF4444)
    val gaugeBg = Color(0xFFE2E8F0)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Soft green circular backdrop
        drawCircle(
            color = mintBg,
            radius = w * 0.46f,
            center = Offset(cx, cy)
        )

        // Dashboard Window
        val winW = w * 0.72f
        val winH = h * 0.60f
        val winLeft = cx - winW / 2f
        val winTop = cy - winH / 2f + h * 0.02f

        drawRoundRect(
            color = cardBg,
            topLeft = Offset(winLeft, winTop),
            size = Size(winW, winH),
            cornerRadius = CornerRadius(16f, 16f)
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(winLeft, winTop),
            size = Size(winW, winH),
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = 3f)
        )

        // Window Title Bar
        drawRoundRect(
            color = Color(0xFFF1F5F9),
            topLeft = Offset(winLeft, winTop),
            size = Size(winW, winH * 0.20f),
            cornerRadius = CornerRadius(16f, 16f)
        )
        drawLine(
            color = outlineColor,
            start = Offset(winLeft, winTop + winH * 0.20f),
            end = Offset(winLeft + winW, winTop + winH * 0.20f),
            strokeWidth = 2f
        )

        // 3 Mac-style window dots
        val dotY = winTop + winH * 0.10f
        val dotColors = listOf(Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF10B981))
        for (i in 0..2) {
            drawCircle(
                color = dotColors[i],
                radius = 4.5f,
                center = Offset(winLeft + 18f + i * 14f, dotY)
            )
        }

        // Title bar text placeholder
        drawLine(
            color = Color(0xFF64748B),
            start = Offset(winLeft + winW * 0.38f, dotY),
            end = Offset(winLeft + winW * 0.76f, dotY),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        // Left Gauge (Eligible 78% - Teal)
        val gauge1Center = Offset(winLeft + winW * 0.32f, winTop + winH * 0.58f)
        val gaugeRadius = winW * 0.16f

        drawCircle(
            color = gaugeBg,
            radius = gaugeRadius,
            center = gauge1Center,
            style = Stroke(width = 10f)
        )
        drawArc(
            color = tealGauge,
            startAngle = -90f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(gauge1Center.x - gaugeRadius, gauge1Center.y - gaugeRadius),
            size = Size(gaugeRadius * 2, gaugeRadius * 2),
            style = Stroke(width = 10f, cap = StrokeCap.Round)
        )

        // Checkmark inside Left Gauge
        val checkPath = Path().apply {
            moveTo(gauge1Center.x - 8f, gauge1Center.y)
            lineTo(gauge1Center.x - 2f, gauge1Center.y + 6f)
            lineTo(gauge1Center.x + 8f, gauge1Center.y - 6f)
        }
        drawPath(
            path = checkPath,
            color = tealGauge,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Right Gauge (Warning 56% - Orange)
        val gauge2Center = Offset(winLeft + winW * 0.72f, winTop + winH * 0.58f)
        drawCircle(
            color = gaugeBg,
            radius = gaugeRadius,
            center = gauge2Center,
            style = Stroke(width = 10f)
        )
        drawArc(
            color = warningGauge,
            startAngle = -90f,
            sweepAngle = 190f,
            useCenter = false,
            topLeft = Offset(gauge2Center.x - gaugeRadius, gauge2Center.y - gaugeRadius),
            size = Size(gaugeRadius * 2, gaugeRadius * 2),
            style = Stroke(width = 10f, cap = StrokeCap.Round)
        )

        // Warning Triangle inside Right Gauge
        val triCenterY = gauge2Center.y
        val triPath = Path().apply {
            moveTo(gauge2Center.x, triCenterY - 9f)
            lineTo(gauge2Center.x - 9f, triCenterY + 7f)
            lineTo(gauge2Center.x + 9f, triCenterY + 7f)
            close()
        }
        drawPath(path = triPath, color = alertRed)
        // Exclamation point in triangle
        drawLine(
            color = Color.White,
            start = Offset(gauge2Center.x, triCenterY - 4f),
            end = Offset(gauge2Center.x, triCenterY + 2f),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color.White,
            radius = 1.2f,
            center = Offset(gauge2Center.x, triCenterY + 5f)
        )

        // Floating Warning Badge (Top Right)
        val badgeX = cx + w * 0.32f
        val badgeY = cy - h * 0.20f
        val badgeSize = w * 0.16f
        val triBadge = Path().apply {
            moveTo(badgeX, badgeY - badgeSize * 0.45f)
            lineTo(badgeX - badgeSize * 0.45f, badgeY + badgeSize * 0.45f)
            lineTo(badgeX + badgeSize * 0.45f, badgeY + badgeSize * 0.45f)
            close()
        }
        drawPath(path = triBadge, color = warningGauge)
        drawLine(
            color = Color.White,
            start = Offset(badgeX, badgeY - badgeSize * 0.18f),
            end = Offset(badgeX, badgeY + badgeSize * 0.12f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color.White,
            radius = 2.5f,
            center = Offset(badgeX, badgeY + badgeSize * 0.28f)
        )
    }
}

/**
 * AI Notice Parser Graphic Illustration:
 * Circular mint container with notice paper document, camera scanner viewfinder frame, beam, and AI sparkles.
 */
@Composable
fun AiParserGraphicIllustration(modifier: Modifier = Modifier) {
    val mintBg = Color(0xFFE8F5E9)
    val cardBg = Color.White
    val outlineColor = Color(0xFF1E293B)
    val tealColor = Color(0xFF0F766E)
    val beamColor = Color(0xFF14B8A6)
    val sparkleColor = Color(0xFFF59E0B)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Soft green circular backdrop
        drawCircle(
            color = mintBg,
            radius = w * 0.46f,
            center = Offset(cx, cy)
        )

        // Notice Paper Sheet
        val docW = w * 0.52f
        val docH = h * 0.62f
        val docLeft = cx - docW / 2f
        val docTop = cy - docH / 2f + h * 0.02f

        drawRoundRect(
            color = cardBg,
            topLeft = Offset(docLeft, docTop),
            size = Size(docW, docH),
            cornerRadius = CornerRadius(12f, 12f)
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(docLeft, docTop),
            size = Size(docW, docH),
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(width = 3f)
        )

        // Circular seal on notice
        drawCircle(
            color = tealColor.copy(alpha = 0.2f),
            radius = docW * 0.12f,
            center = Offset(docLeft + docW * 0.22f, docTop + docH * 0.20f)
        )

        // Document text lines
        val lineSpacing = docH * 0.08f
        val lineStartY = docTop + docH * 0.38f
        for (i in 0..4) {
            val ly = lineStartY + i * lineSpacing
            val lw = if (i == 4) docW * 0.45f else docW * 0.72f
            drawLine(
                color = Color(0xFF94A3B8),
                start = Offset(docLeft + docW * 0.14f, ly),
                end = Offset(docLeft + docW * 0.14f + lw, ly),
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
        }

        // Camera Scanner Viewfinder Guides (4 corner brackets)
        val frameW = w * 0.68f
        val frameH = h * 0.64f
        val fLeft = cx - frameW / 2f
        val fTop = cy - frameH / 2f + h * 0.02f
        val cornerArm = frameW * 0.16f
        val strokeW = 5f

        // Top Left
        drawLine(color = tealColor, start = Offset(fLeft, fTop), end = Offset(fLeft + cornerArm, fTop), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color = tealColor, start = Offset(fLeft, fTop), end = Offset(fLeft, fTop + cornerArm), strokeWidth = strokeW, cap = StrokeCap.Round)

        // Top Right
        drawLine(color = tealColor, start = Offset(fLeft + frameW, fTop), end = Offset(fLeft + frameW - cornerArm, fTop), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color = tealColor, start = Offset(fLeft + frameW, fTop), end = Offset(fLeft + frameW, fTop + cornerArm), strokeWidth = strokeW, cap = StrokeCap.Round)

        // Bottom Left
        drawLine(color = tealColor, start = Offset(fLeft, fTop + frameH), end = Offset(fLeft + cornerArm, fTop + frameH), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color = tealColor, start = Offset(fLeft, fTop + frameH), end = Offset(fLeft, fTop + frameH - cornerArm), strokeWidth = strokeW, cap = StrokeCap.Round)

        // Bottom Right
        drawLine(color = tealColor, start = Offset(fLeft + frameW, fTop + frameH), end = Offset(fLeft + frameW - cornerArm, fTop + frameH), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color = tealColor, start = Offset(fLeft + frameW, fTop + frameH), end = Offset(fLeft + frameW, fTop + frameH - cornerArm), strokeWidth = strokeW, cap = StrokeCap.Round)

        // Glowing Scanning Beam Line across the center
        val beamY = cy + h * 0.02f
        drawLine(
            color = beamColor,
            start = Offset(fLeft + 8f, beamY),
            end = Offset(fLeft + frameW - 8f, beamY),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        // AI Sparkle Star 1 (Top Right)
        drawSparkleStar(this, Offset(cx + w * 0.32f, cy - h * 0.22f), w * 0.07f, sparkleColor)
        // AI Sparkle Star 2 (Bottom Left)
        drawSparkleStar(this, Offset(cx - w * 0.32f, cy + h * 0.24f), w * 0.05f, sparkleColor)
    }
}

private fun drawSparkleStar(scope: DrawScope, center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        cubicTo(center.x, center.y, center.x, center.y, center.x + radius, center.y)
        cubicTo(center.x, center.y, center.x, center.y, center.x, center.y + radius)
        cubicTo(center.x, center.y, center.x, center.y, center.x - radius, center.y)
        cubicTo(center.x, center.y, center.x, center.y, center.x, center.y - radius)
        close()
    }
    scope.drawPath(path = path, color = color)
}
