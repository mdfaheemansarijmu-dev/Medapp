package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MedicalCourse
import com.example.data.model.ParsedTimetableClass
import com.example.ui.viewmodel.LoginMode
import com.example.ui.viewmodel.PlannerViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Clean, fast, student-focused Onboarding Flow:
 * 1. Screen 1: Welcome (MEDPULSE, benefits, Get Started, simplified auth, Guest Mode)
 * 2. Screen 2: Course (What are you studying? 1 of 4)
 * 3. Screen 3: Academic Details (Which year are you in? 2 of 4)
 * 4. Screen 4: Add Timetable (Bring your timetable + AI extraction review 3 of 4)
 * 5. Screen 5: Notifications (Never miss what's important 4 of 4)
 * 6. Screen 6: Completion (You're all set 🎉 -> Go to Dashboard)
 */
enum class OnboardingStep {
    APP_FUNCTIONS,
    STUDENT_NAME,
    COURSE,
    UNIVERSITY,
    ACADEMIC_DETAILS,
    AUTH_CHOICE,
    WELCOME,
    ADD_TIMETABLE,
    NOTIFICATIONS,
    COMPLETION
}

enum class TimetableProcessingStage {
    IDLE,
    READING_FILE,
    FINDING_CLASSES,
    PREPARING_SCHEDULE
}

enum class TimetableParseErrorType {
    NONE,
    UNREADABLE,
    UNCLEAR_IMAGE,
    UNSUPPORTED_PDF
}

enum class AuthSubView {
    NONE,
    EMAIL_SIGN_IN,
    EMAIL_SIGN_UP,
    PHONE_SIGN_IN
}

data class CourseCardInfo(
    val course: MedicalCourse,
    val title: String,
    val program: String,
    val icon: ImageVector,
    val duration: String
)

val availableCourses = listOf(
    CourseCardInfo(MedicalCourse.MBBS, "MBBS", "Medicine", Icons.Default.LocalHospital, "5.5 Years"),
    CourseCardInfo(MedicalCourse.BDS, "BDS", "Dental", Icons.Default.MedicalServices, "5 Years"),
    CourseCardInfo(MedicalCourse.BAMS, "BAMS", "Ayurveda", Icons.Default.Spa, "5.5 Years"),
    CourseCardInfo(MedicalCourse.BHMS, "BHMS", "Homeopathy", Icons.Default.Vaccines, "5.5 Years"),
    CourseCardInfo(MedicalCourse.NURSING, "B.Sc Nursing", "Nursing", Icons.Default.HealthAndSafety, "4 Years"),
    CourseCardInfo(MedicalCourse.PHARMACY, "B.Pharm", "Pharmacy", Icons.Default.Medication, "4 Years")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    viewModel: PlannerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(OnboardingStep.APP_FUNCTIONS) }
    var authSubView by remember { mutableStateOf(AuthSubView.NONE) }

    // User choices
    var selectedCourse by remember { mutableStateOf<MedicalCourse?>(null) }
    var selectedCollege by remember { mutableStateOf("All India Institute of Medical Sciences (AIIMS), New Delhi") }
    var selectedYear by remember { mutableStateOf<String?>(null) }
    var admissionYearStr by remember { mutableStateOf("") }
    var selectedSemester by remember { mutableStateOf("Semester 1") }
    var selectedBatch by remember { mutableStateOf("Batch A") }
    var studentName by remember { mutableStateOf("") }

    // Extracted timetable state
    var extractedClasses by remember { mutableStateOf<List<ParsedTimetableClass>>(emptyList()) }
    var isTimetableAnalyzed by remember { mutableStateOf(false) }
    var isAnalyzingTimetable by remember { mutableStateOf(false) }
    var processingStage by remember { mutableStateOf(TimetableProcessingStage.IDLE) }
    var parseErrorType by remember { mutableStateOf(TimetableParseErrorType.NONE) }
    var editingClassIndex by remember { mutableStateOf<Int?>(null) }

    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val phoneOtpSent by viewModel.phoneOtpSent.collectAsStateWithLifecycle()
    val loginMode by viewModel.loginMode.collectAsStateWithLifecycle()
    val studentEmail by viewModel.studentEmail.collectAsStateWithLifecycle()

    // Onboarding draft persistence: allows user to safely resume if closed halfway through
    val onboardingDraftPrefs = remember { context.getSharedPreferences("medpulse_onboarding_draft", Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        val draftStepStr = onboardingDraftPrefs.getString("draft_step", null)
        if (draftStepStr != null) {
            try {
                val step = OnboardingStep.valueOf(draftStepStr)
                val courseCode = onboardingDraftPrefs.getString("draft_course", null)
                if (courseCode != null) {
                    selectedCourse = MedicalCourse.valueOf(courseCode)
                }
                selectedCollege = onboardingDraftPrefs.getString("draft_college", "All India Institute of Medical Sciences (AIIMS), New Delhi") ?: "All India Institute of Medical Sciences (AIIMS), New Delhi"
                selectedYear = onboardingDraftPrefs.getString("draft_year", null)
                admissionYearStr = onboardingDraftPrefs.getString("draft_admission_year", "") ?: ""
                selectedSemester = onboardingDraftPrefs.getString("draft_semester", "Semester 1") ?: "Semester 1"
                selectedBatch = onboardingDraftPrefs.getString("draft_batch", "Batch A") ?: "Batch A"
                studentName = onboardingDraftPrefs.getString("draft_name", "") ?: ""
                if (step != OnboardingStep.APP_FUNCTIONS && step != OnboardingStep.WELCOME && step != OnboardingStep.COMPLETION) {
                    currentStep = step
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
    }

    LaunchedEffect(currentStep, selectedCourse, selectedCollege, selectedYear, admissionYearStr, selectedSemester, selectedBatch, studentName) {
        if (currentStep == OnboardingStep.APP_FUNCTIONS || currentStep == OnboardingStep.WELCOME || currentStep == OnboardingStep.COMPLETION) {
            onboardingDraftPrefs.edit().clear().apply()
        } else {
            onboardingDraftPrefs.edit()
                .putString("draft_step", currentStep.name)
                .putString("draft_course", selectedCourse?.name)
                .putString("draft_college", selectedCollege)
                .putString("draft_year", selectedYear)
                .putString("draft_admission_year", admissionYearStr)
                .putString("draft_semester", selectedSemester)
                .putString("draft_batch", selectedBatch)
                .putString("draft_name", studentName)
                .apply()
        }
    }

    fun persistProfileAndTimetable(course: MedicalCourse) {
        val chosenCollege = if (selectedCollege.isNotBlank()) selectedCollege else "All India Institute of Medical Sciences (AIIMS), New Delhi"
        val finalName = if (studentName.isNotBlank()) studentName else "Medical Student"
        val currentYr = selectedYear ?: "1st Year"
        val admYear = admissionYearStr.trim().toIntOrNull() ?: 2024
        viewModel.saveUserProfile(
            name = finalName,
            college = chosenCollege,
            course = course.displayName,
            year = currentYr,
            semester = selectedSemester,
            batch = selectedBatch,
            admissionYear = admYear,
            currentYear = currentYr
        )
        viewModel.setStudentCollege(chosenCollege)
        if (extractedClasses.isNotEmpty()) {
            viewModel.replaceCurrentTimetable(extractedClasses)
        }
    }

    LaunchedEffect(loginMode) {
        if (loginMode == LoginMode.FIREBASE && authSubView != AuthSubView.NONE) {
            authSubView = AuthSubView.NONE
            if (viewModel.isOnboardingCompleted()) {
                viewModel.navigateTo(com.example.ui.viewmodel.Screen.Dashboard)
            } else if (currentStep == OnboardingStep.AUTH_CHOICE) {
                val course = selectedCourse ?: MedicalCourse.MBBS
                persistProfileAndTimetable(course)
                viewModel.completeOnboarding(course)
            } else {
                currentStep = OnboardingStep.COURSE
            }
        }
    }

    // Google Sign-In setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
    }
    val googleSignInClient = remember(context, gso) {
        GoogleSignIn.getClient(context, gso)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.setAuthenticating(false)
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val name = account.displayName ?: "Medical Student"
                val email = account.email ?: ""
                val photoUrl = account.photoUrl?.toString() ?: ""
                val id = account.id ?: ""
                val idToken = account.idToken

                if (studentName.isBlank()) {
                    studentName = name
                }
                viewModel.signInWithGoogle(name, email, photoUrl, id, idToken)
                Toast.makeText(context, "Welcome, $name!", Toast.LENGTH_SHORT).show()
                if (viewModel.isOnboardingCompleted()) {
                    viewModel.navigateTo(com.example.ui.viewmodel.Screen.Dashboard)
                } else if (currentStep == OnboardingStep.AUTH_CHOICE) {
                    val course = selectedCourse ?: MedicalCourse.MBBS
                    persistProfileAndTimetable(course)
                    viewModel.completeOnboarding(course)
                } else {
                    currentStep = OnboardingStep.COURSE
                }
            } catch (e: ApiException) {
                val errorMsg = when (e.statusCode) {
                    com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> "Network error. Continuing in guest mode."
                    com.google.android.gms.common.api.CommonStatusCodes.CANCELED -> "Google sign-in cancelled."
                    else -> "Sign-in error (${e.statusCode}). You can continue as guest."
                }
                viewModel.setAuthError(errorMsg)
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera launcher for timetable photo
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            coroutineScope.launch {
                extractedClasses = emptyList()
                isTimetableAnalyzed = false
                parseErrorType = TimetableParseErrorType.NONE
                isAnalyzingTimetable = true
                processingStage = TimetableProcessingStage.READING_FILE
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val bytes = stream.toByteArray()
                processingStage = TimetableProcessingStage.FINDING_CLASSES
                val course = selectedCourse ?: MedicalCourse.MBBS
                val parsed = viewModel.parseTimetableDocument(null, bytes, "image/jpeg", course)
                if (parsed.isNotEmpty()) {
                    processingStage = TimetableProcessingStage.PREPARING_SCHEDULE
                    extractedClasses = parsed
                    isTimetableAnalyzed = true
                    parseErrorType = TimetableParseErrorType.NONE
                } else {
                    extractedClasses = emptyList()
                    isTimetableAnalyzed = false
                    parseErrorType = TimetableParseErrorType.UNCLEAR_IMAGE
                }
                processingStage = TimetableProcessingStage.IDLE
                isAnalyzingTimetable = false
            }
        }
    }

    // File picker launcher for PDF
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                extractedClasses = emptyList()
                isTimetableAnalyzed = false
                parseErrorType = TimetableParseErrorType.NONE
                isAnalyzingTimetable = true
                processingStage = TimetableProcessingStage.READING_FILE
                val bytes = try {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (e: Exception) { null }
                if (bytes == null || bytes.isEmpty()) {
                    processingStage = TimetableProcessingStage.IDLE
                    isAnalyzingTimetable = false
                    parseErrorType = TimetableParseErrorType.UNSUPPORTED_PDF
                    return@launch
                }
                processingStage = TimetableProcessingStage.FINDING_CLASSES
                val course = selectedCourse ?: MedicalCourse.MBBS
                val parsed = viewModel.parseTimetableDocument(null, bytes, "application/pdf", course)
                if (parsed.isNotEmpty()) {
                    processingStage = TimetableProcessingStage.PREPARING_SCHEDULE
                    extractedClasses = parsed
                    isTimetableAnalyzed = true
                    parseErrorType = TimetableParseErrorType.NONE
                } else {
                    extractedClasses = emptyList()
                    isTimetableAnalyzed = false
                    parseErrorType = TimetableParseErrorType.UNSUPPORTED_PDF
                }
                processingStage = TimetableProcessingStage.IDLE
                isAnalyzingTimetable = false
            }
        }
    }

    // Image picker launcher for screenshot / gallery
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                extractedClasses = emptyList()
                isTimetableAnalyzed = false
                parseErrorType = TimetableParseErrorType.NONE
                isAnalyzingTimetable = true
                processingStage = TimetableProcessingStage.READING_FILE
                val bytes = try {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (e: Exception) { null }
                if (bytes == null || bytes.isEmpty()) {
                    processingStage = TimetableProcessingStage.IDLE
                    isAnalyzingTimetable = false
                    parseErrorType = TimetableParseErrorType.UNCLEAR_IMAGE
                    return@launch
                }
                processingStage = TimetableProcessingStage.FINDING_CLASSES
                val course = selectedCourse ?: MedicalCourse.MBBS
                val parsed = viewModel.parseTimetableDocument(null, bytes, "image/png", course)
                if (parsed.isNotEmpty()) {
                    processingStage = TimetableProcessingStage.PREPARING_SCHEDULE
                    extractedClasses = parsed
                    isTimetableAnalyzed = true
                    parseErrorType = TimetableParseErrorType.NONE
                } else {
                    extractedClasses = emptyList()
                    isTimetableAnalyzed = false
                    parseErrorType = TimetableParseErrorType.UNCLEAR_IMAGE
                }
                processingStage = TimetableProcessingStage.IDLE
                isAnalyzingTimetable = false
            }
        }
    }

    // Track notification permission states
    val sharedPrefs = remember { context.getSharedPreferences("medpulse_prefs", Context.MODE_PRIVATE) }
    var showPermissionPermanentlyDeniedDialog by remember { mutableStateOf(false) }

    // System Notification permission launcher (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setNotificationsEnabled(true)
            Toast.makeText(context, "Class & exam reminders enabled!", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.setNotificationsEnabled(false)
            Toast.makeText(context, "No problem! You can turn on reminders anytime in Settings.", Toast.LENGTH_LONG).show()
        }
        currentStep = OnboardingStep.COMPLETION
    }

    fun handleAllowNotifications() {
        val course = selectedCourse ?: MedicalCourse.MBBS
        persistProfileAndTimetable(course)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                // State: Permission granted already
                viewModel.setNotificationsEnabled(true)
                Toast.makeText(context, "Reminders enabled! Welcome to MedPulse.", Toast.LENGTH_SHORT).show()
                currentStep = OnboardingStep.COMPLETION
            } else {
                val act = context as? Activity
                val wasRequestedBefore = sharedPrefs.getBoolean("post_notifications_requested", false)
                val isPermanentlyDenied = wasRequestedBefore && act != null &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(act, android.Manifest.permission.POST_NOTIFICATIONS)

                if (isPermanentlyDenied) {
                    showPermissionPermanentlyDeniedDialog = true
                } else {
                    sharedPrefs.edit().putBoolean("post_notifications_requested", true).apply()
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            viewModel.setNotificationsEnabled(true)
            Toast.makeText(context, "Class & exam reminders enabled!", Toast.LENGTH_SHORT).show()
            currentStep = OnboardingStep.COMPLETION
        }
    }

    fun handleMaybeLater() {
        val course = selectedCourse ?: MedicalCourse.MBBS
        persistProfileAndTimetable(course)
        viewModel.setNotificationsEnabled(false)
        currentStep = OnboardingStep.COMPLETION
    }

    // Mandatory Cloud Authentication Gateway: Guarantees user data is never lost
    if (loginMode == LoginMode.UNDECIDED || loginMode == LoginMode.GUEST) {
        AuthScreen(
            viewModel = viewModel,
            onAuthSuccess = { isNewUser ->
                if (viewModel.isOnboardingCompleted()) {
                    viewModel.navigateTo(com.example.ui.viewmodel.Screen.Dashboard)
                } else {
                    currentStep = OnboardingStep.STUDENT_NAME
                }
            },
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                }
            },
            label = "OnboardingStepTransition"
        ) { step ->
            when (step) {
                OnboardingStep.APP_FUNCTIONS, OnboardingStep.WELCOME -> {
                    OnboardingFunctionsScreen(
                        onGetStarted = {
                            currentStep = OnboardingStep.STUDENT_NAME
                        }
                    )
                }

                OnboardingStep.STUDENT_NAME -> {
                    OnboardingStudentNameScreen(
                        studentName = studentName,
                        onNameChanged = { studentName = it },
                        onBack = { currentStep = OnboardingStep.APP_FUNCTIONS },
                        onContinue = {
                            if (studentName.isNotBlank()) {
                                currentStep = OnboardingStep.COURSE
                            }
                        }
                    )
                }

                OnboardingStep.COURSE -> {
                    OnboardingCourseScreen(
                        selectedCourse = selectedCourse,
                        onSelectCourse = { selectedCourse = it },
                        onBack = { currentStep = OnboardingStep.STUDENT_NAME },
                        onContinue = {
                            if (selectedCourse != null) {
                                currentStep = OnboardingStep.UNIVERSITY
                            }
                        },
                        stepNumber = 2,
                        totalSteps = 4
                    )
                }

                OnboardingStep.UNIVERSITY -> {
                    OnboardingUniversityScreen(
                        selectedCollege = selectedCollege,
                        selectedCourse = selectedCourse,
                        onSelectCollege = { selectedCollege = it },
                        onBack = { currentStep = OnboardingStep.COURSE },
                        onContinue = {
                            currentStep = OnboardingStep.ACADEMIC_DETAILS
                        },
                        stepNumber = 3,
                        totalSteps = 4
                    )
                }

                OnboardingStep.ACADEMIC_DETAILS -> {
                    OnboardingAcademicDetailsScreen(
                        selectedYear = selectedYear,
                        admissionYear = admissionYearStr,
                        selectedSemester = selectedSemester,
                        selectedBatch = selectedBatch,
                        studentName = studentName,
                        onYearSelected = { selectedYear = it },
                        onAdmissionYearChanged = { admissionYearStr = it },
                        onSemesterSelected = { selectedSemester = it },
                        onBatchSelected = { selectedBatch = it },
                        onNameChanged = { studentName = it },
                        onBack = { currentStep = OnboardingStep.UNIVERSITY },
                        onContinue = {
                            currentStep = OnboardingStep.AUTH_CHOICE
                        },
                        stepNumber = 4,
                        totalSteps = 4
                    )
                }

                OnboardingStep.AUTH_CHOICE -> {
                    val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    val isUserLoggedIn = (loginMode != LoginMode.UNDECIDED && loginMode != LoginMode.GUEST) || (fbUser != null && !fbUser.isAnonymous)
                    val currentUserEmail = fbUser?.email ?: fbUser?.phoneNumber ?: studentEmail.ifBlank { null }

                    OnboardingAuthChoiceScreen(
                        studentName = studentName,
                        selectedCourse = selectedCourse,
                        selectedCollege = selectedCollege,
                        selectedYear = selectedYear,
                        selectedBatch = selectedBatch,
                        isAuthenticating = isAuthenticating,
                        authError = authError,
                        isUserLoggedIn = isUserLoggedIn,
                        userEmail = currentUserEmail,
                        onBack = { currentStep = OnboardingStep.ACADEMIC_DETAILS },
                        onStartNowWithoutLogin = {
                            val course = selectedCourse ?: MedicalCourse.MBBS
                            persistProfileAndTimetable(course)
                            viewModel.completeOnboarding(course)
                            Toast.makeText(context, "Welcome! Your medical routine is securely synced with your batch.", Toast.LENGTH_SHORT).show()
                        },
                        onGoogleClick = {
                            viewModel.setAuthenticating(true)
                            viewModel.setAuthError(null)
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        },
                        onMobileClick = {
                            authSubView = AuthSubView.PHONE_SIGN_IN
                        },
                        onEmailClick = {
                            authSubView = AuthSubView.EMAIL_SIGN_IN
                        }
                    )
                }

                OnboardingStep.ADD_TIMETABLE -> {
                    OnboardingAddTimetableScreen(
                        selectedCourse = selectedCourse ?: MedicalCourse.MBBS,
                        extractedClasses = extractedClasses,
                        isTimetableAnalyzed = isTimetableAnalyzed,
                        isAnalyzing = isAnalyzingTimetable,
                        processingStage = processingStage,
                        parseErrorType = parseErrorType,
                        onClearError = { parseErrorType = TimetableParseErrorType.NONE },
                        onImportPdf = {
                            parseErrorType = TimetableParseErrorType.NONE
                            pdfPickerLauncher.launch("application/pdf")
                        },
                        onTakePhoto = {
                            parseErrorType = TimetableParseErrorType.NONE
                            cameraLauncher.launch(null)
                        },
                        onChooseScreenshot = {
                            parseErrorType = TimetableParseErrorType.NONE
                            imagePickerLauncher.launch("image/*")
                        },
                        onAddManuallyOrPreset = {
                            val course = selectedCourse ?: MedicalCourse.MBBS
                            extractedClasses = viewModel.getDefaultParsedTimetable(course)
                            isTimetableAnalyzed = true
                            parseErrorType = TimetableParseErrorType.NONE
                            processingStage = TimetableProcessingStage.IDLE
                        },
                        onClearExtracted = {
                            extractedClasses = emptyList()
                            isTimetableAnalyzed = false
                            parseErrorType = TimetableParseErrorType.NONE
                        },
                        onEditClass = { index -> editingClassIndex = index },
                        onDeleteClass = { index ->
                            extractedClasses = extractedClasses.toMutableList().also { it.removeAt(index) }
                        },
                        onAddAll = {
                            val course = selectedCourse ?: MedicalCourse.MBBS
                            viewModel.replaceCurrentTimetable(extractedClasses)
                            currentStep = OnboardingStep.NOTIFICATIONS
                        },
                        onSkip = {
                            currentStep = OnboardingStep.NOTIFICATIONS
                        },
                        onBack = { currentStep = OnboardingStep.ACADEMIC_DETAILS }
                    )
                }

                OnboardingStep.NOTIFICATIONS -> {
                    OnboardingNotificationsScreen(
                        onAllowNotifications = { handleAllowNotifications() },
                        onMaybeLater = { handleMaybeLater() },
                        onBack = { currentStep = OnboardingStep.ADD_TIMETABLE }
                    )
                }

                OnboardingStep.COMPLETION -> {
                    OnboardingCompletionScreen(
                        selectedCourse = selectedCourse ?: MedicalCourse.MBBS,
                        selectedYear = selectedYear ?: "1st Year",
                        selectedBatch = selectedBatch,
                        admissionYear = admissionYearStr,
                        classCount = extractedClasses.size,
                        notificationsEnabled = viewModel.areNotificationsEnabled.value,
                        onGoToDashboard = {
                            val course = selectedCourse ?: MedicalCourse.MBBS
                            viewModel.completeOnboarding(course)
                        }
                    )
                }
            }
        }

        // Dialog for handling permanently denied notification permission without trapping the user
        if (showPermissionPermanentlyDeniedDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPermissionPermanentlyDeniedDialog = false
                    currentStep = OnboardingStep.COMPLETION
                },
                title = {
                    Text(
                        text = "Enable Notifications in Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Text(
                        text = "Notifications are currently turned off in your device settings. You can turn them on in Settings anytime, or continue to MedPulse without notifications.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionPermanentlyDeniedDialog = false
                            try {
                                val intent = Intent().apply {
                                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(fallback)
                            }
                            currentStep = OnboardingStep.COMPLETION
                        }
                    ) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPermissionPermanentlyDeniedDialog = false
                            currentStep = OnboardingStep.COMPLETION
                        }
                    ) {
                        Text("Continue to MedPulse")
                    }
                }
            )
        }

        // Dialog for editing an individual class item in Screen 4
        editingClassIndex?.let { index ->
            if (index in extractedClasses.indices) {
                val itemToEdit = extractedClasses[index]
                EditClassDialog(
                    item = itemToEdit,
                    onDismiss = { editingClassIndex = null },
                    onSave = { updated ->
                        val list = extractedClasses.toMutableList()
                        list[index] = updated
                        extractedClasses = list
                        editingClassIndex = null
                    }
                )
            }
        }

        // Authentication overlays for existing accounts or mobile sign-in
        if (authSubView != AuthSubView.NONE) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                when (authSubView) {
                    AuthSubView.EMAIL_SIGN_IN -> {
                        EmailSignInForm(
                            isAuthenticating = isAuthenticating,
                            authError = authError,
                            onBack = { authSubView = AuthSubView.NONE },
                            onSignIn = { email, pass ->
                                viewModel.signInWithEmailAndPassword(email, pass)
                            }
                        )
                    }
                    AuthSubView.PHONE_SIGN_IN -> {
                        PhoneSignInForm(
                            isAuthenticating = isAuthenticating,
                            authError = authError,
                            phoneOtpSent = phoneOtpSent,
                            onBack = { authSubView = AuthSubView.NONE },
                            onSendCode = { phone ->
                                (context as? Activity)?.let { act ->
                                    val nameToUse = studentName.ifBlank { "Medical Student" }
                                    viewModel.sendPhoneOtp(act, phone, nameToUse)
                                }
                            },
                            onVerifyCode = { phone, otp ->
                                val nameToUse = studentName.ifBlank { "Medical Student" }
                                viewModel.verifyPhoneOtp(phoneNumber = phone, otp = otp, name = nameToUse)
                            }
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 1 — WELCOME
// -----------------------------------------------------------------------------------------
@Composable
fun OnboardingWelcomeScreen(
    isAuthenticating: Boolean,
    authError: String?,
    onGetStarted: () -> Unit,
    onGoogleClick: () -> Unit,
    onMobileClick: () -> Unit,
    onGuestClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Medical Heartbeat Icon
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "MedPulse emblem",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // MEDPULSE Title
            Text(
                text = "MEDPULSE",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Your college, organized.
            Text(
                text = "Your college, organized.",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Classes • Attendance • Assignments • Exams
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Classes  •  Attendance  •  Assignments  •  Exams",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Value Proposition Card (Answers: "What is MedPulse and why should I use it?")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BenefitHighlightRow(
                        icon = Icons.Default.CalendarMonth,
                        title = "Smart Medical Timetable",
                        description = "Organize theory lectures, clinical postings, and practical labs with zero schedule clashes."
                    )
                    BenefitHighlightRow(
                        icon = Icons.Default.FactCheck,
                        title = "Real-Time Attendance",
                        description = "Track 75% university eligibility criteria with timely shortage warnings."
                    )
                    BenefitHighlightRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "AI Schedule & Notice Parser",
                        description = "Snap a photo of your department board or WhatsApp circulars for instant schedule extraction."
                    )
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

        // Bottom CTA & Simplified Authentication Options
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // PRIMARY CTA: "Get Started"
            Button(
                onClick = onGetStarted,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("get_started_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Get Started",
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
                    text = "or",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // Simplified Auth: Continue with Google
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
                        text = "Continue with Google",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Continue with Mobile
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
                        text = "Continue with Mobile",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 4. Continue as Guest (Legitimate first-class option)
            Surface(
                onClick = onGuestClick,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("continue_guest_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Continue as Guest",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "You can create an account later to sync your data.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Already have an account? Log in
            TextButton(
                onClick = onLoginClick,
                modifier = Modifier.testTag("login_link_button")
            ) {
                Text(
                    text = "Already have an account? Log in",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun BenefitHighlightRow(
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
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
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

// -----------------------------------------------------------------------------------------
// SCREEN 2 — COURSE ("What are you studying?")
// -----------------------------------------------------------------------------------------
@Composable
fun OnboardingCourseScreen(
    selectedCourse: MedicalCourse?,
    onSelectCourse: (MedicalCourse) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    stepNumber: Int = 2,
    totalSteps: Int = 4,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar with Step Indicator
            OnboardingStepHeader(
                currentStepNumber = stepNumber,
                totalSteps = totalSteps,
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Headline
            Text(
                text = "What are you studying?",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Select your course to customize your curriculum and academic calendar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 6 Selectable Cards in a 2-column or 1-column responsive layout
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false)
            ) {
                availableCourses.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { item ->
                            CourseSelectCard(
                                item = item,
                                isSelected = selectedCourse == item.course,
                                onClick = { onSelectCourse(item.course) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "You can add custom electives or switch courses anytime in Settings.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Continue Button: Disabled until course selection
        Button(
            onClick = onContinue,
            enabled = selectedCourse != null,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("course_continue_button"),
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

@Composable
fun CourseSelectCard(
    item: CourseCardInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        label = "courseBorder"
    )
    val animatedBgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        label = "courseBg"
    )

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = animatedBgColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, animatedBorderColor),
        modifier = modifier
            .height(96.dp)
            .testTag("course_card_${item.course.code}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.program,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 3 — ACADEMIC DETAILS ("Which year are you in?")
// -----------------------------------------------------------------------------------------
@Composable
fun OnboardingAcademicDetailsScreen(
    selectedYear: String?,
    admissionYear: String,
    selectedSemester: String,
    selectedBatch: String,
    studentName: String,
    onYearSelected: (String) -> Unit,
    onAdmissionYearChanged: (String) -> Unit,
    onSemesterSelected: (String) -> Unit,
    onBatchSelected: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    stepNumber: Int = 4,
    totalSteps: Int = 4,
    modifier: Modifier = Modifier
) {
    val years = listOf("1st Year", "2nd Year", "3rd Year", "4th Year", "5th Year", "Internship")
    val semesters = listOf("Semester 1", "Semester 2", "Annual System")
    val batches = listOf("Batch A", "Batch B", "Batch C", "Full Batch")
    val currentCalendarYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val admissionYearSuggestions = listOf(
        currentCalendarYear.toString(),
        (currentCalendarYear - 1).toString(),
        (currentCalendarYear - 2).toString(),
        (currentCalendarYear - 3).toString(),
        (currentCalendarYear - 4).toString(),
        (currentCalendarYear - 5).toString()
    )

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
            // Header with Progress Indicator
            OnboardingStepHeader(
                currentStepNumber = stepNumber,
                totalSteps = totalSteps,
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Which year are you in?",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "MedPulse will tailor your clinical rotations, lecture timings, and subjects.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Year Selection Chips Grid (2 columns)
            years.chunked(2).forEach { rowYears ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowYears.forEach { yr ->
                        val isSelected = selectedYear == yr
                        Card(
                            onClick = { onYearSelected(yr) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("year_chip_$yr")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = yr,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Progressive Disclosure: Semester & Batch appear only when Year is selected
            AnimatedVisibility(
                visible = selectedYear != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Semester Selection
                    Column {
                        Text(
                            text = "Semester / Term",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            semesters.forEach { sem ->
                                FilterChip(
                                    selected = selectedSemester == sem,
                                    onClick = { onSemesterSelected(sem) },
                                    label = { Text(sem) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // Batch / Section Selection
                    Column {
                        Text(
                            text = "Batch / Clinical Section",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            batches.forEach { batch ->
                                FilterChip(
                                    selected = selectedBatch == batch,
                                    onClick = { onBatchSelected(batch) },
                                    label = { Text(batch) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // Admission Year Selection
                    Column {
                        Text(
                            text = "Year of Admission (Batch Year)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Used to sync assignments & notices with your university batchmates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            admissionYearSuggestions.forEach { yr ->
                                FilterChip(
                                    selected = admissionYear == yr,
                                    onClick = { onAdmissionYearChanged(yr) },
                                    label = { Text(yr) },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = admissionYear,
                            onValueChange = { newVal ->
                                if (newVal.all { it.isDigit() } && newVal.length <= 4) {
                                    onAdmissionYearChanged(newVal)
                                }
                            },
                            placeholder = { Text("Or enter year, e.g. 2023") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Optional Student Name
                    Column {
                        Text(
                            text = "Your Name (Optional)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = studentName,
                            onValueChange = onNameChanged,
                            placeholder = { Text("e.g., Alex Sharma") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Continue Button
        Button(
            onClick = onContinue,
            enabled = selectedYear != null,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("academic_continue_button"),
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

// -----------------------------------------------------------------------------------------
// SCREEN 4 — ADD TIMETABLE ("Bring your timetable")
// -----------------------------------------------------------------------------------------
@Composable
fun OnboardingAddTimetableScreen(
    selectedCourse: MedicalCourse,
    extractedClasses: List<ParsedTimetableClass>,
    isTimetableAnalyzed: Boolean,
    isAnalyzing: Boolean,
    processingStage: TimetableProcessingStage,
    parseErrorType: TimetableParseErrorType,
    onClearError: () -> Unit,
    onImportPdf: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseScreenshot: () -> Unit,
    onAddManuallyOrPreset: () -> Unit,
    onClearExtracted: () -> Unit = {},
    onEditClass: (Int) -> Unit,
    onDeleteClass: (Int) -> Unit,
    onAddAll: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        ) {
            // Header with Progress Indicator "4 of 5"
            OnboardingStepHeader(
                currentStepNumber = 4,
                totalSteps = 5,
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (!isTimetableAnalyzed && !isAnalyzing) {
                // Initial Prompt State: Options to bring timetable
                Text(
                    text = "Bring your timetable",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "MedPulse can organize it automatically.",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Upload your university circular or notice board photo. Our medical parser extracts classes, rooms, and teachers for review.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Error State if previous import attempt failed
                if (parseErrorType != TimetableParseErrorType.NONE) {
                    TimetableErrorCard(
                        errorType = parseErrorType,
                        onPrimaryAction = {
                            when (parseErrorType) {
                                TimetableParseErrorType.UNCLEAR_IMAGE -> onTakePhoto()
                                TimetableParseErrorType.UNSUPPORTED_PDF -> onImportPdf()
                                else -> onClearError()
                            }
                        },
                        onAddManually = onAddManuallyOrPreset
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 4 Import Options
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimetableImportOptionCard(
                        icon = Icons.Default.PictureAsPdf,
                        title = "Import PDF",
                        description = "Upload college timetable circular or routine document",
                        iconColor = Color(0xFF10B981),
                        onClick = onImportPdf,
                        testTag = "import_pdf_button"
                    )
                    TimetableImportOptionCard(
                        icon = Icons.Default.CameraAlt,
                        title = "Take Photo",
                        description = "Capture printed notice board or classroom board",
                        iconColor = Color(0xFFEF4444),
                        onClick = onTakePhoto,
                        testTag = "take_photo_button"
                    )
                    TimetableImportOptionCard(
                        icon = Icons.Default.PhotoLibrary,
                        title = "Choose Screenshot",
                        description = "Select WhatsApp image or gallery screenshot",
                        iconColor = Color(0xFF3B82F6),
                        onClick = onChooseScreenshot,
                        testTag = "choose_screenshot_button"
                    )
                    TimetableImportOptionCard(
                        icon = Icons.Default.EditCalendar,
                        title = "Add Manually",
                        description = "Start with standard ${selectedCourse.displayName} routine and customize",
                        iconColor = Color(0xFF8B5CF6),
                        onClick = onAddManuallyOrPreset,
                        testTag = "add_manually_button"
                    )
                }
            } else if (isAnalyzing || processingStage != TimetableProcessingStage.IDLE) {
                // Analyzing State with honest processing stages
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 5.dp
                        )
                        Text(
                            text = when (processingStage) {
                                TimetableProcessingStage.READING_FILE -> "Reading your timetable..."
                                TimetableProcessingStage.FINDING_CLASSES -> "Finding classes..."
                                TimetableProcessingStage.PREPARING_SCHEDULE -> "Preparing your schedule..."
                                else -> "Analyzing your timetable..."
                            },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = when (processingStage) {
                                TimetableProcessingStage.READING_FILE -> "Scanning file structure and optical clarity."
                                TimetableProcessingStage.FINDING_CLASSES -> "Detecting subjects, days, lecture halls, and faculty with Gemini AI."
                                TimetableProcessingStage.PREPARING_SCHEDULE -> "Formatting recurring timetable schedule."
                                else -> "Extracting schedule details."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Confirmation / Review Screen ("I found 42 classes")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "I found ${extractedClasses.size} classes",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Review extracted classes before adding them to your schedule.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onClearExtracted) {
                        Text("Clear")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reviewable List of Extracted Classes
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(extractedClasses) { index, item ->
                        ExtractedClassReviewCard(
                            item = item,
                            onEdit = { onEditClass(index) },
                            onDelete = { onDeleteClass(index) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons at Bottom
        if (isTimetableAnalyzed && !isAnalyzing) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAddAll,
                    enabled = extractedClasses.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("add_all_classes_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add All (${extractedClasses.size} Classes)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Customize Later in Timetable Tab",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        } else if (!isAnalyzing) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("skip_timetable_button")
            ) {
                Text(
                    text = "Skip for now (Use Standard Routine)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun TimetableImportOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    iconColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconColor.copy(alpha = 0.12f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ExtractedClassReviewCard(
    item: ParsedTimetableClass,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dayName = when (item.day_of_week) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        else -> "Sun"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(width = 44.dp, height = 40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.subject,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.is_practical) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Practical",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.start_time} - ${item.end_time}  •  ${item.room ?: "Hall A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.teacher_name.isNullOrBlank()) {
                    Text(
                        text = item.teacher_name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Edit button
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit class",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Delete button
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete class",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 5 — NOTIFICATIONS ("Never miss what's important")
// -----------------------------------------------------------------------------------------
@Composable
fun OnboardingNotificationsScreen(
    onAllowNotifications: () -> Unit,
    onMaybeLater: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            // Header with Progress Indicator "5 of 5"
            OnboardingStepHeader(
                currentStepNumber = 5,
                totalSteps = 5,
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Notification Bell Icon Emblem with soft container
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Notifications",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Title: "Never miss what's important"
            Text(
                text = "Never miss what's important",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Supporting text: "We'll remind you about classes, assignments, exams and deadlines."
            Text(
                text = "We'll remind you about classes, assignments, exams and deadlines.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Benefit Items explaining WHY notifications are useful
            // 🔔 Classes
            // 📝 Assignments
            // 📚 Exams
            // 📅 Deadlines
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                NotificationBenefitItem(
                    emoji = "🔔",
                    title = "Classes",
                    description = "10-minute heads-up before lectures, clinical postings, and practical labs."
                )
                NotificationBenefitItem(
                    emoji = "📝",
                    title = "Assignments",
                    description = "Timely reminders before practical logbooks, case reports, and assignment deadlines."
                )
                NotificationBenefitItem(
                    emoji = "📚",
                    title = "Exams",
                    description = "Advance countdown alerts for internal assessments, term vivas, and university papers."
                )
                NotificationBenefitItem(
                    emoji = "📅",
                    title = "Deadlines",
                    description = "Helpful reminders for exam registrations, clinical sign-offs, and fee cutoffs."
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Primary button: "Allow Notifications"
            Button(
                onClick = onAllowNotifications,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("allow_notifications_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Allow Notifications",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Secondary option: "Maybe Later"
            TextButton(
                onClick = onMaybeLater,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("maybe_later_button")
            ) {
                Text(
                    text = "Maybe Later",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
fun NotificationBenefitItem(
    emoji: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = emoji,
                        fontSize = 20.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$emoji $title",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// STEP HEADER COMPONENT (Shows "1 of 4", progress bar, and back arrow)
// -----------------------------------------------------------------------------------------
@Composable
fun OnboardingStepHeader(
    currentStepNumber: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "$currentStepNumber of $totalSteps",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress Bar
        val animatedProgress by animateFloatAsState(
            targetValue = currentStepNumber.toFloat() / totalSteps.toFloat(),
            label = "stepProgress"
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}

// -----------------------------------------------------------------------------------------
// EDIT EXTRACTED CLASS DIALOG
// -----------------------------------------------------------------------------------------
@Composable
fun EditClassDialog(
    item: ParsedTimetableClass,
    onDismiss: () -> Unit,
    onSave: (ParsedTimetableClass) -> Unit
) {
    var subject by remember { mutableStateOf(item.subject) }
    var startTime by remember { mutableStateOf(item.start_time) }
    var endTime by remember { mutableStateOf(item.end_time) }
    var room by remember { mutableStateOf(item.room ?: "") }
    var teacher by remember { mutableStateOf(item.teacher_name ?: "") }
    var dayOfWeek by remember { mutableStateOf(item.day_of_week) }
    var isPractical by remember { mutableStateOf(item.is_practical) }

    val days = listOf("Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5, "Sat" to 6)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Edit Class Details",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Subject Name
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Day Selection Chips
                Column {
                    Text(
                        text = "Day of Week",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        days.forEach { (label, dayNum) ->
                            FilterChip(
                                selected = dayOfWeek == dayNum,
                                onClick = { dayOfWeek = dayNum },
                                label = { Text(label) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Start and End Times
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start Time") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End Time") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Room / Hall
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Lecture Hall / Room") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Teacher
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Teacher / Faculty") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Practical Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isPractical = !isPractical }
                ) {
                    Checkbox(
                        checked = isPractical,
                        onCheckedChange = { isPractical = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "This is a Practical / Lab / Clinical session",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updated = item.copy(
                                subject = subject.trim(),
                                start_time = startTime.trim(),
                                end_time = endTime.trim(),
                                room = room.trim().ifEmpty { null },
                                teacher_name = teacher.trim().ifEmpty { null },
                                day_of_week = dayOfWeek,
                                is_practical = isPractical
                            )
                            onSave(updated)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// AUTHENTICATION FORMS (FOR EXISTING ACCOUNTS & MOBILE OTP)
// -----------------------------------------------------------------------------------------
@Composable
fun EmailSignInForm(
    isAuthenticating: Boolean,
    authError: String?,
    onBack: () -> Unit,
    onSignIn: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Log in with your academic credentials.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("College or Personal Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (!authError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = authError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Button(
            onClick = { onSignIn(email, password) },
            enabled = email.isNotBlank() && password.isNotBlank() && !isAuthenticating,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (isAuthenticating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Log In", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
fun PhoneSignInForm(
    isAuthenticating: Boolean,
    authError: String?,
    phoneOtpSent: Boolean,
    onBack: () -> Unit,
    onSendCode: (String) -> Unit,
    onVerifyCode: (String, String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Mobile Login",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (!phoneOtpSent) "Enter your phone number to receive a verification OTP." else "Enter the 6-digit verification code sent to your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!phoneOtpSent) {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number (+91 ...)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { otpCode = it },
                    label = { Text("6-Digit OTP") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!authError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = authError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Button(
            onClick = {
                if (!phoneOtpSent) onSendCode(phoneNumber) else onVerifyCode(phoneNumber, otpCode)
            },
            enabled = (if (!phoneOtpSent) phoneNumber.length >= 10 else otpCode.length >= 6) && !isAuthenticating,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (isAuthenticating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(
                    text = if (!phoneOtpSent) "Send Verification Code" else "Verify & Continue",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TIMETABLE ERROR CARD (Section 21 Error States)
// -----------------------------------------------------------------------------------------
@Composable
fun TimetableErrorCard(
    errorType: TimetableParseErrorType,
    onPrimaryAction: () -> Unit,
    onAddManually: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = when (errorType) {
                        TimetableParseErrorType.UNCLEAR_IMAGE -> Icons.Default.Warning
                        TimetableParseErrorType.UNSUPPORTED_PDF -> Icons.Default.PictureAsPdf
                        else -> Icons.Default.ErrorOutline
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = when (errorType) {
                        TimetableParseErrorType.UNCLEAR_IMAGE -> "This image is difficult to read."
                        TimetableParseErrorType.UNSUPPORTED_PDF -> "This PDF can't be processed."
                        else -> "MedPulse couldn't read this timetable."
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Text(
                text = when (errorType) {
                    TimetableParseErrorType.UNCLEAR_IMAGE -> "Please try a clearer, well-lit photo or screenshot of your timetable, or add your schedule manually."
                    TimetableParseErrorType.UNSUPPORTED_PDF -> "Please try another timetable PDF file, or start with your course standard schedule manually."
                    else -> "We couldn't detect classes from this file. You can try again or set up your schedule manually."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPrimaryAction,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = when (errorType) {
                            TimetableParseErrorType.UNCLEAR_IMAGE -> "Try a clearer photo"
                            TimetableParseErrorType.UNSUPPORTED_PDF -> "Try another file"
                            else -> "Try Again"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onError
                    )
                }

                OutlinedButton(
                    onClick = onAddManually,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Add Manually",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 6 — ONBOARDING COMPLETION (Section 23: Fast, delightful finish state)
// -----------------------------------------------------------------------------------------
@Composable
fun OnboardingCompletionScreen(
    selectedCourse: MedicalCourse,
    selectedYear: String,
    selectedBatch: String,
    admissionYear: String = "",
    classCount: Int,
    notificationsEnabled: Boolean,
    onGoToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Celebration Badge
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "🎉", fontSize = 42.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title: "You're all set 🎉"
            Text(
                text = "You're all set 🎉",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle: "MedPulse is ready to organize your college life."
            Text(
                text = "MedPulse is ready to organize your college life.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Summary Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CompletionSummaryRow(
                        icon = Icons.Default.School,
                        label = "Course & Year",
                        value = "${selectedCourse.displayName} • $selectedYear"
                    )
                    CompletionSummaryRow(
                        icon = Icons.Default.Groups,
                        label = "Clinical Section",
                        value = if (admissionYear.isNotBlank()) "$selectedBatch (Admitted $admissionYear)" else selectedBatch
                    )
                    CompletionSummaryRow(
                        icon = Icons.Default.CalendarMonth,
                        label = "Timetable",
                        value = if (classCount > 0) "$classCount recurring classes active" else "Standard routine loaded"
                    )
                    CompletionSummaryRow(
                        icon = if (notificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        label = "Reminders",
                        value = if (notificationsEnabled) "Class & deadline alerts active" else "Turn on anytime in Settings"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Primary Action: "Go to Dashboard"
        Button(
            onClick = onGoToDashboard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("go_to_dashboard_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Go to Dashboard",
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

@Composable
fun CompletionSummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
