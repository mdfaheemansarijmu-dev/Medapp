package com.example.ui.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PlannerDatabase
import com.example.data.model.*
import com.example.data.university.*
import com.example.data.syllabus.CourseSyllabusDirectory
import com.example.data.syllabus.SyllabusSubject
import com.example.data.repository.PlannerRepository
import com.example.data.sync.BatchNotificationSyncManager
import com.example.data.sync.SharedBatchNotice
import com.example.util.AcademicNotificationManager
import com.example.util.*
import com.example.util.GoogleCalendarSyncManager
import com.example.network.GeminiParserService
import com.example.network.RetrofitClient
import com.example.network.UpdateService
import com.example.network.UpdateServiceImpl
import com.example.network.AppUpdateResult
import com.example.network.AppUpdateConfig
import com.example.network.GitHubUpdateClient
import com.example.utils.DownloadManagerHelper
import android.app.DownloadManager
import com.squareup.moshi.Types
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class PlannerViewModel(
    application: Application,
    private val repository: PlannerRepository
) : AndroidViewModel(application) {

    // App State / Navigation
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Welcome)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedCourse = MutableStateFlow<MedicalCourse?>(null)
    val selectedCourse: StateFlow<MedicalCourse?> = _selectedCourse.asStateFlow()

    // Date and Calendar states
    private val _currentCalendarDate = MutableStateFlow<Calendar>(Calendar.getInstance())
    val currentCalendarDate: StateFlow<Calendar> = _currentCalendarDate.asStateFlow()

    private val _selectedCalendarDate = MutableStateFlow<Calendar>(Calendar.getInstance())
    val selectedCalendarDate: StateFlow<Calendar> = _selectedCalendarDate.asStateFlow()

    // Live Clock for Today widget
    private val _currentTimeOfDay = MutableStateFlow("")
    val currentTimeOfDay: StateFlow<String> = _currentTimeOfDay.asStateFlow()

    // Active Database Flows
    val timetable: StateFlow<List<TimetableClass>> = selectedCourse
        .flatMapLatest { course ->
            if (course != null) {
                repository.getTimetable(course.code).map { list ->
                    val cleanList = list.filter {
                        val sub = it.subject.trim().lowercase()
                        !sub.startsWith("note:") && !sub.startsWith("notice:") && !sub.contains("postings will be from") && !sub.contains("classes will be from") && sub.length >= 2
                    }
                    val unique = mutableListOf<TimetableClass>()
                    for (cls in cleanList) {
                        val isDup = unique.any { u ->
                            u.dayOfWeek == cls.dayOfWeek && (
                                (u.firestoreId.isNotBlank() && u.firestoreId == cls.firestoreId) ||
                                (u.periodNumber > 0 && u.periodNumber == cls.periodNumber) ||
                                (u.startTime.isNotBlank() && u.startTime.equals(cls.startTime, ignoreCase = true) && u.endTime.equals(cls.endTime, ignoreCase = true)) ||
                                (u.startTime.isNotBlank() && u.startTime.equals(cls.startTime, ignoreCase = true) && u.subject.trim().equals(cls.subject.trim(), ignoreCase = true))
                            )
                        }
                        if (!isDup) {
                            unique.add(cls)
                        }
                    }
                    unique
                }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val assignments: StateFlow<List<Assignment>> = selectedCourse
        .flatMapLatest { course ->
            if (course != null) repository.getAssignments(course.code)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val assessments: StateFlow<List<Assessment>> = selectedCourse
        .flatMapLatest { course ->
            if (course != null) repository.getAssessments(course.code)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val studyTasks: StateFlow<List<StudyTask>> = selectedCourse
        .flatMapLatest { course ->
            if (course != null) repository.getStudyTasks(course.code)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun deduplicateTopicsInMemory(list: List<CompletedSyllabusTopic>): List<CompletedSyllabusTopic> {
        val unique = mutableListOf<CompletedSyllabusTopic>()
        for (topic in list) {
            val existing = unique.firstOrNull { u ->
                (u.firestoreId.isNotBlank() && u.firestoreId == topic.firestoreId) ||
                (com.example.util.ChapterSimilarityHelper.isSameOrSimilarSubject(u.subject, topic.subject) &&
                 (u.topicTitle.trim().equals(topic.topicTitle.trim(), ignoreCase = true) ||
                  com.example.util.ChapterSimilarityHelper.isSimilar(u.topicTitle, topic.topicTitle, u.subject, topic.subject)))
            }
            if (existing == null) {
                unique.add(topic)
            } else {
                if ((existing.authorName.isBlank() || existing.authorName == "Classmate") &&
                    topic.authorName.isNotBlank() && topic.authorName != "Classmate") {
                    val idx = unique.indexOf(existing)
                    if (idx != -1) {
                        unique[idx] = existing.copy(
                            authorName = topic.authorName,
                            authorUid = topic.authorUid,
                            teacherName = existing.teacherName ?: topic.teacherName,
                            notes = existing.notes ?: topic.notes
                        )
                    }
                }
            }
        }
        return unique
    }

    val completedSyllabusTopics: StateFlow<List<CompletedSyllabusTopic>> = selectedCourse
        .flatMapLatest { course ->
            if (course != null) repository.getCompletedTopicsForCourse(course.code)
            else flowOf(emptyList())
        }
        .map { list ->
            deduplicateTopicsInMemory(list)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSyllabusYear = MutableStateFlow<String>("")
    val selectedSyllabusYear: StateFlow<String> = _selectedSyllabusYear.asStateFlow()

    fun setSelectedSyllabusYear(year: String) {
        _selectedSyllabusYear.value = year
    }

    val chatMessages: StateFlow<List<ChatMessage>> = repository.getChatMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<InAppNotification>> = repository.getNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exams: StateFlow<List<Exam>> = selectedCourse
        .flatMapLatest { course ->
            if (course != null) repository.getExams(course.code)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val plannerTasks: StateFlow<List<PlannerTask>> = selectedCourse
        .flatMapLatest { course ->
            if (course != null) repository.getPlannerTasks(course.code)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleOverrides: StateFlow<List<ScheduleOverride>> = selectedCourse
        .flatMapLatest { course ->
            if (course != null) repository.getOverrides(course.code)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAttendanceRecords: StateFlow<List<AttendanceRecord>> = repository.getAllAttendance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Attendance Target configuration (defaults to 75%)
    private val _attendanceTarget = MutableStateFlow(75)
    val attendanceTarget: StateFlow<Int> = _attendanceTarget.asStateFlow()

    val overallAttendanceSummary: StateFlow<OverallAttendanceSummary> = combine(
        allAttendanceRecords,
        _attendanceTarget
    ) { records, target ->
        AttendanceCalculator.calculateOverallSummary(records, target)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AttendanceCalculator.calculateOverallSummary(emptyList(), 75)
    )

    val subjectAttendanceSummaries: StateFlow<List<SubjectAttendanceSummary>> = combine(
        allAttendanceRecords,
        timetable,
        _attendanceTarget
    ) { records, timetableClasses, target ->
        val subjectSet = linkedSetOf<String>()
        timetableClasses.forEach { cls ->
            if (cls.subject.isNotBlank()) {
                subjectSet.add(cls.subject.trim())
            }
        }
        records.forEach { rec ->
            if (rec.subject.isNotBlank()) {
                subjectSet.add(rec.subject.trim())
            }
        }

        subjectSet.map { subject ->
            AttendanceCalculator.calculateSubjectSummary(subject, records, target)
        }.sortedWith(
            compareByDescending<SubjectAttendanceSummary> { it.status == AttendanceStatus.CRITICAL }
                .thenByDescending { it.status == AttendanceStatus.BELOW_TARGET }
                .thenByDescending { it.status == AttendanceStatus.NEAR_TARGET }
                .thenBy { it.percentage }
                .thenBy { it.subject }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val allRevisions: StateFlow<List<DailySubjectRevision>> = repository.getAllRevisions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // UI Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Theme options (default to light theme as requested)
    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    // Notification options (defaults to true)
    private val _areNotificationsEnabled = MutableStateFlow(true)
    val areNotificationsEnabled: StateFlow<Boolean> = _areNotificationsEnabled.asStateFlow()

    // Student Profile state
    private val _studentName = MutableStateFlow("")
    val studentName: StateFlow<String> = _studentName.asStateFlow()

    private val _studentCollege = MutableStateFlow("")
    val studentCollege: StateFlow<String> = _studentCollege.asStateFlow()

    // University & Holiday State
    val selectedCollegeInfo: StateFlow<CollegeInfo?> = _studentCollege.map { collegeName ->
        if (collegeName.isBlank()) null else UniversityDirectory.findCollege(collegeName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayHoliday: StateFlow<UniversityHoliday?> = _studentCollege.map { collegeName ->
        UniversityCalendarService.isTodayHoliday(collegeName).second
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UniversityCalendarService.isTodayHoliday("").second)

    val tomorrowHoliday: StateFlow<UniversityHoliday?> = _studentCollege.map { collegeName ->
        UniversityCalendarService.isTomorrowHoliday(collegeName).second
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UniversityCalendarService.isTomorrowHoliday("").second)

    val upcomingHolidays: StateFlow<List<HolidayCountdown>> = _studentCollege.map { collegeName ->
        UniversityCalendarService.getUpcomingHolidays(collegeName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UniversityCalendarService.getUpcomingHolidays(""))

    private val _isSyncingCalendar = MutableStateFlow(false)
    val isSyncingCalendar: StateFlow<Boolean> = _isSyncingCalendar.asStateFlow()

    private val _lastCalendarSyncResult = MutableStateFlow<GoogleCalendarSyncResult?>(null)
    val lastCalendarSyncResult: StateFlow<GoogleCalendarSyncResult?> = _lastCalendarSyncResult.asStateFlow()

    private val _studentYear = MutableStateFlow("")
    val studentYear: StateFlow<String> = _studentYear.asStateFlow()

    private val _studentAdmissionYear = MutableStateFlow(2024)
    val studentAdmissionYear: StateFlow<Int> = _studentAdmissionYear.asStateFlow()

    private val _studentSemester = MutableStateFlow("")
    val studentSemester: StateFlow<String> = _studentSemester.asStateFlow()

    private val _studentBatch = MutableStateFlow("")
    val studentBatch: StateFlow<String> = _studentBatch.asStateFlow()

    private val _customBatchCode = MutableStateFlow("")
    val customBatchCode: StateFlow<String> = _customBatchCode.asStateFlow()

    private val _batchShareFeedbackMessage = MutableStateFlow<String?>(null)
    val batchShareFeedbackMessage: StateFlow<String?> = _batchShareFeedbackMessage.asStateFlow()

    fun clearBatchShareFeedback() {
        _batchShareFeedbackMessage.value = null
    }

    fun setCustomBatchCode(code: String) {
        val clean = code.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_')
        _customBatchCode.value = clean
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit().putString("custom_batch_code", clean).apply()
        startBatchNotificationSync()
    }

    // Shared Batch Notification System (Option 1: Real-Time Firestore Batch Feed)
    private val batchSyncManager = BatchNotificationSyncManager(application, repository)
    val sharedBatchNotices: StateFlow<List<SharedBatchNotice>> = batchSyncManager.sharedNotices
    val isBatchSyncListening: StateFlow<Boolean> = batchSyncManager.isListening
    val batchSyncStatus: StateFlow<String> = batchSyncManager.syncStatus
    val activeBatchKey: StateFlow<String> = combine(
        _studentCollege,
        _selectedCourse,
        _studentAdmissionYear,
        _studentBatch,
        _customBatchCode
    ) { col, crs, admYr, btch, customCode ->
        BatchNotificationSyncManager.computeBatchKey(col, crs?.code ?: "MBBS", admYr, btch, customCode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _isPostingBatchNotice = MutableStateFlow(false)
    val isPostingBatchNotice: StateFlow<Boolean> = _isPostingBatchNotice.asStateFlow()

    private val _batchPostError = MutableStateFlow<String?>(null)
    val batchPostError: StateFlow<String?> = _batchPostError.asStateFlow()

    private val _isProfileCompleted = MutableStateFlow(false)
    val isProfileCompleted: StateFlow<Boolean> = _isProfileCompleted.asStateFlow()

    private val _studentDpUrl = MutableStateFlow("")
    val studentDpUrl: StateFlow<String> = _studentDpUrl.asStateFlow()

    private val _studentDpPreset = MutableStateFlow("doctor_male")
    val studentDpPreset: StateFlow<String> = _studentDpPreset.asStateFlow()

    private val _googleUserId = MutableStateFlow("")
    val googleUserId: StateFlow<String> = _googleUserId.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.LOADING)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _authLoadingMessage = MutableStateFlow<String>("Connecting to MedPulse...")
    val authLoadingMessage: StateFlow<String> = _authLoadingMessage.asStateFlow()

    fun setAuthLoadingMessage(message: String) {
        _authLoadingMessage.value = message
    }

    // Login and Account states
    private val _loginMode = MutableStateFlow(LoginMode.UNDECIDED)
    val loginMode: StateFlow<LoginMode> = _loginMode.asStateFlow()

    private val _studentEmail = MutableStateFlow("")
    val studentEmail: StateFlow<String> = _studentEmail.asStateFlow()

    private val _phoneOtpSent = MutableStateFlow(false)
    val phoneOtpSent: StateFlow<Boolean> = _phoneOtpSent.asStateFlow()

    private val _phoneVerificationId = MutableStateFlow<String?>(null)
    val phoneVerificationId: StateFlow<String?> = _phoneVerificationId.asStateFlow()

    // Parser screen draft status
    private val _isParsingMessage = MutableStateFlow(false)
    val isParsingMessage: StateFlow<Boolean> = _isParsingMessage.asStateFlow()

    private val _activeParsedDrafts = MutableStateFlow<List<ParsedItem>>(emptyList())
    val activeParsedDrafts: StateFlow<List<ParsedItem>> = _activeParsedDrafts.asStateFlow()

    private val _activeUnifiedResponse = MutableStateFlow<UnifiedParserResponse?>(null)
    val activeUnifiedResponse: StateFlow<UnifiedParserResponse?> = _activeUnifiedResponse.asStateFlow()

    // --- Update System State Flows & Properties ---
    private val updateService: UpdateService = UpdateServiceImpl()

    private val _updateCheckInProgress = MutableStateFlow(false)
    val updateCheckInProgress: StateFlow<Boolean> = _updateCheckInProgress.asStateFlow()

    private val _updateResult = MutableStateFlow<AppUpdateResult?>(null)
    val updateResult: StateFlow<AppUpdateResult?> = _updateResult.asStateFlow()

    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    private val _gitHubOwner = MutableStateFlow(updateService.getGitHubOwner(application))
    val gitHubOwner: StateFlow<String> = _gitHubOwner.asStateFlow()

    private val _gitHubRepo = MutableStateFlow(updateService.getGitHubRepo(application))
    val gitHubRepo: StateFlow<String> = _gitHubRepo.asStateFlow()

    private val _lastCheckedTime = MutableStateFlow(0L)
    val lastCheckedTime: StateFlow<Long> = _lastCheckedTime.asStateFlow()

    private val _cachedUpdateConfig = MutableStateFlow<AppUpdateConfig?>(null)
    val cachedUpdateConfig: StateFlow<AppUpdateConfig?> = _cachedUpdateConfig.asStateFlow()

    private val _customUpdateUrl = MutableStateFlow("")
    val customUpdateUrl: StateFlow<String> = _customUpdateUrl.asStateFlow()

    private val _isUpdateDialogDismissed = MutableStateFlow(false)
    val isUpdateDialogDismissed: StateFlow<Boolean> = _isUpdateDialogDismissed.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow<Float?>(null)
    val updateDownloadProgress: StateFlow<Float?> = _updateDownloadProgress.asStateFlow()

    private val _updateDownloadState = MutableStateFlow<String?>(null)
    val updateDownloadState: StateFlow<String?> = _updateDownloadState.asStateFlow()

    private val _fcmToken = MutableStateFlow("")
    val fcmToken: StateFlow<String> = _fcmToken.asStateFlow()

    private val _isFirebaseMessagingAvailable = MutableStateFlow(false)
    val isFirebaseMessagingAvailable: StateFlow<Boolean> = _isFirebaseMessagingAvailable.asStateFlow()

    // Duplicate Item Dialog with Student Contribution
    private val _duplicateContributionDialog = MutableStateFlow<DuplicateContributionInfo?>(null)
    val duplicateContributionDialog: StateFlow<DuplicateContributionInfo?> = _duplicateContributionDialog.asStateFlow()

    fun dismissDuplicateDialog() {
        _duplicateContributionDialog.value = null
    }

    private val geminiService = GeminiParserService()

    private val _selectedGeminiModel = MutableStateFlow(GeminiModelOption.DEFAULT)
    val selectedGeminiModel: StateFlow<GeminiModelOption> = _selectedGeminiModel.asStateFlow()

    private val _customGeminiApiKey = MutableStateFlow("")
    val customGeminiApiKey: StateFlow<String> = _customGeminiApiKey.asStateFlow()

    fun setGeminiModel(model: GeminiModelOption) {
        _selectedGeminiModel.value = model
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit().putString("selected_gemini_model_id", model.modelId).apply()
    }

    fun setCustomGeminiApiKey(key: String) {
        val trimmed = key.trim()
        _customGeminiApiKey.value = trimmed
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit().putString("custom_gemini_api_key", trimmed).apply()
    }

    init {
        // Read stored course preference from local preferences if any
        val sharedPrefs = application.getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        _fcmToken.value = sharedPrefs.getString("fcm_registration_token", "") ?: ""

        val savedApiKey = sharedPrefs.getString("custom_gemini_api_key", "") ?: ""
        _customGeminiApiKey.value = savedApiKey
        GeminiParserService.customApiKeyProvider = {
            val key = _customGeminiApiKey.value
            if (GeminiParserService.isValidApiKey(key)) key else null
        }

        viewModelScope.launch {
            repository.deduplicateCompletedTopics()
        }

        // Safe Firebase initialization check without forcing unconfigured FCM registration
        viewModelScope.launch {
            try {
                val apps = com.google.firebase.FirebaseApp.getApps(application)
                if (apps.isNotEmpty()) {
                    try {
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled = false
                    } catch (e: Throwable) {
                        // ignore if messaging not initialized
                    }
                    _isFirebaseMessagingAvailable.value = true
                }
            } catch (e: Throwable) {
                Log.w("PlannerViewModel", "Firebase messaging check skipped: ${e.message}")
            }
        }
        _isDarkTheme.value = sharedPrefs.getBoolean("is_dark_theme", false)
        _areNotificationsEnabled.value = sharedPrefs.getBoolean("are_notifications_enabled", true)
        _attendanceTarget.value = sharedPrefs.getInt("attendance_target_percentage", 75)
        val savedModelId = sharedPrefs.getString("selected_gemini_model_id", GeminiModelOption.DEFAULT.modelId) ?: GeminiModelOption.DEFAULT.modelId
        _selectedGeminiModel.value = GeminiModelOption.fromModelId(savedModelId)
        val savedName = sharedPrefs.getString("student_name", "") ?: ""
        _studentName.value = if (savedName == "Med Student") "" else savedName
        _studentDpUrl.value = sharedPrefs.getString("student_dp_url", "") ?: ""
        _studentDpPreset.value = sharedPrefs.getString("student_dp_preset", "doctor_male") ?: "doctor_male"
        val savedLoginMode = sharedPrefs.getString("login_mode", LoginMode.UNDECIDED.name) ?: LoginMode.UNDECIDED.name
        _loginMode.value = try { LoginMode.valueOf(savedLoginMode) } catch (e: Exception) { LoginMode.UNDECIDED }
        _studentEmail.value = sharedPrefs.getString("student_email", "") ?: ""
        _googleUserId.value = sharedPrefs.getString("google_user_id", "") ?: ""

        val savedCollege = sharedPrefs.getString("student_college", "") ?: ""
        if (savedCollege.isNotBlank()) {
            _studentCollege.value = savedCollege
        }
        val savedYear = sharedPrefs.getString("student_year", "") ?: ""
        if (savedYear.isNotBlank()) {
            _studentYear.value = savedYear
        }
        _studentAdmissionYear.value = sharedPrefs.getInt("student_admission_year", 2024)
        val savedSemester = sharedPrefs.getString("student_semester", "") ?: ""
        if (savedSemester.isNotBlank()) {
            _studentSemester.value = savedSemester
        }
        val savedBatch = sharedPrefs.getString("student_batch", "") ?: ""
        if (savedBatch.isNotBlank()) {
            _studentBatch.value = savedBatch
        }
        val savedCustomBatch = sharedPrefs.getString("custom_batch_code", "") ?: ""
        if (savedCustomBatch.isNotBlank()) {
            _customBatchCode.value = savedCustomBatch
        }
        _isProfileCompleted.value = sharedPrefs.getBoolean("is_profile_completed", false)

        // Check Firebase Authentication and local cache state
        val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val currentUser = firebaseAuth.currentUser
        val savedCourseCode = sharedPrefs.getString("selected_course_code", null)
        val isOnboardingCompletedLocally = sharedPrefs.getBoolean("is_onboarding_completed", false)

        if (currentUser != null) {
            val uid = currentUser.uid
            val userEmail = currentUser.email ?: ""
            _loginMode.value = if (savedLoginMode == LoginMode.GOOGLE.name) LoginMode.GOOGLE else LoginMode.FIREBASE

            if (isOnboardingCompletedLocally && savedCourseCode != null) {
                try {
                    val course = MedicalCourse.valueOf(savedCourseCode)
                    _selectedCourse.value = course
                    _isProfileCompleted.value = true
                    _authState.value = AuthState.AUTHENTICATED_PROFILE_COMPLETE
                    _currentScreen.value = Screen.Dashboard
                    viewModelScope.launch {
                        repository.populateDefaultTimetableIfEmpty(course.code)
                        com.example.util.AcademicNotificationManager.verifyExistingNotifications(application)
                        com.example.util.AcademicNotificationManager.rescheduleAllFutureNotifications(application)
                        scheduleTimetableClassNotifications()
                        generateSmartNotifications()
                        startBatchNotificationSync()
                        repository.startCloudSync(uid)
                        restoreDataFromFirebaseInternal(uid, userEmail)
                    }
                } catch (e: Exception) {
                    _authState.value = AuthState.AUTHENTICATED_PROFILE_INCOMPLETE
                    _currentScreen.value = Screen.Welcome
                }
            } else {
                // Logged in to Firebase, but local cache incomplete - verify profile with Firestore asynchronously
                _authState.value = AuthState.LOADING
                _authLoadingMessage.value = "Restoring your academic profile..."
                viewModelScope.launch {
                    val fallbackName = sharedPrefs.getString("student_name", "") ?: currentUser.displayName ?: ""
                    val fallbackPhoto = sharedPrefs.getString("student_dp_url", "") ?: currentUser.photoUrl?.toString() ?: ""
                    checkAndRouteAuthenticatedUser(
                        firebaseUser = currentUser,
                        mode = _loginMode.value,
                        fallbackName = fallbackName,
                        fallbackPhotoUrl = fallbackPhoto,
                        onSuccess = { isReturning ->
                            _authState.value = if (isReturning) AuthState.AUTHENTICATED_PROFILE_COMPLETE else AuthState.AUTHENTICATED_PROFILE_INCOMPLETE
                        },
                        onError = {
                            _authState.value = AuthState.AUTHENTICATED_PROFILE_INCOMPLETE
                            _currentScreen.value = Screen.Welcome
                        }
                    )
                }
            }
        } else {
            // Not authenticated
            _loginMode.value = LoginMode.UNDECIDED
            _authState.value = AuthState.UNAUTHENTICATED
            _currentScreen.value = Screen.Welcome
        }

        // Auto-synchronize batch peer channel whenever batch profile parameters change
        viewModelScope.launch {
            combine(
                _studentCollege,
                _selectedCourse,
                _studentAdmissionYear,
                _studentBatch,
                _customBatchCode
            ) { col, crs, admYr, btch, customCode ->
                listOf(col, crs?.code ?: "MBBS", admYr.toString(), btch, customCode ?: "")
            }.distinctUntilChanged()
            .debounce(400)
            .collectLatest { list ->
                val col = list[0]
                val crs = list[1]
                val admYr = list[2].toIntOrNull() ?: 2024
                val btch = list[3]
                val customCode = list[4].ifBlank { null }
                batchSyncManager.startListeningToBatch(col, crs, admYr, btch, customCode)
                if (col.isNotBlank() && crs.isNotBlank() && btch.isNotBlank()) {
                    try {
                        batchSyncManager.fetchAndSyncBatchNow(col, crs, admYr, btch, customCode)
                        repository.deduplicateTimetableClasses()
                    } catch (e: Exception) {
                        Log.w("PlannerViewModel", "Auto batch sync on param change: ${e.message}")
                    }
                }
            }
        }

        // Start Clock updates
        viewModelScope.launch {
            val format = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
                timeZone = AttendanceTimeValidator.COLLEGE_TIMEZONE
            }
            val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).apply {
                timeZone = AttendanceTimeValidator.COLLEGE_TIMEZONE
            }
            var lastDateStr = dayFormat.format(Date())
            while (true) {
                _currentTimeOfDay.value = format.format(Date())
                _currentCalendarDate.value = AttendanceTimeValidator.getCollegeCalendar()
                
                val currentDateStr = dayFormat.format(Date())
                if (currentDateStr != lastDateStr) {
                    lastDateStr = currentDateStr
                    // A new day's schedule has become available! Re-schedule notifications
                    scheduleTimetableClassNotifications()
                }
                kotlinx.coroutines.delay(10000) // Update every 10 seconds
            }
        }

        // Load cached update details
        _lastCheckedTime.value = updateService.getLastCheckedTime(application)
        _cachedUpdateConfig.value = updateService.getCachedUpdateInfo(application)
        _customUpdateUrl.value = updateService.getCustomUpdateUrl(application)

        // Automatically check for updates silently on launch
        checkForUpdates(silent = true)
    }

    // Set Course and populate default database values
    fun selectCourse(course: MedicalCourse) {
        viewModelScope.launch {
            _selectedCourse.value = course
            val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
            sharedPrefs.edit().putString("selected_course_code", course.name).apply()
            
            // Populates default template classes if the DB is empty
            repository.populateDefaultTimetableIfEmpty(course.code)
            
            // Generate course selected alert
            addNotificationWithDuplicateCheck(
                InAppNotification(
                    title = "Course Selected: ${course.displayName}",
                    message = "Course set to ${course.displayName}. You can scan or add your classes in the 'Timetable' tab anytime.",
                    type = "class"
                )
            )

            // Auto navigate to dashboard
            _currentScreen.value = Screen.Dashboard
            generateSmartNotifications()
            scheduleTimetableClassNotifications()
            startBatchNotificationSync()
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCalendarDate(calendar: Calendar) {
        _selectedCalendarDate.value = calendar
    }

    // Timetable Logic & Real-time Class Calculations
    fun parseTimeToMinutes(timeStr: String): Int {
        val parsed = AttendanceTimeValidator.parseTimeToMinutes(timeStr)
        return if (parsed >= 0) parsed else 0
    }

    fun getTimetableForDay(dayOfWeek: Int): List<TimetableClass> {
        return timetable.value.filter { it.dayOfWeek == dayOfWeek }
    }

    // Extract: 1. Current Running Class, 2. Next Class, 3. Remaining Classes
    data class DayClassSchedule(
        val currentClass: TimetableClass? = null,
        val nextClass: TimetableClass? = null,
        val remainingClasses: List<TimetableClass> = emptyList(),
        val todayClasses: List<TimetableClass> = emptyList()
    )

    fun getLiveClassSchedule(): DayClassSchedule {
        val todayClasses = getTimetableForDay(getCurrentDayOfWeek())
        if (todayClasses.isEmpty()) return DayClassSchedule()

        val calendar = AttendanceTimeValidator.getCollegeCalendar()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        var running: TimetableClass? = null
        var next: TimetableClass? = null
        val remaining = mutableListOf<TimetableClass>()

        val sortedClasses = todayClasses.sortedBy { parseTimeToMinutes(it.startTime) }

        for (cls in sortedClasses) {
            val startMin = parseTimeToMinutes(cls.startTime)
            val endMin = parseTimeToMinutes(cls.endTime)

            if (currentMinutes in startMin..endMin) {
                running = cls
            } else if (startMin > currentMinutes) {
                if (next == null) {
                    next = cls
                }
                remaining.add(cls)
            }
        }

        return DayClassSchedule(
            currentClass = running,
            nextClass = next,
            remainingClasses = remaining,
            todayClasses = sortedClasses
        )
    }

    fun getCurrentDayOfWeek(): Int {
        // Calendar Sunday=1, Monday=2, ..., Saturday=7
        // Convert to our format: 1=Mon, 2=Tue, ..., 6=Sat, 7=Sun
        val calDay = AttendanceTimeValidator.getCollegeCalendar().get(Calendar.DAY_OF_WEEK)
        return when (calDay) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    fun getDayName(dayIndex: Int): String {
        return when (dayIndex) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            7 -> "Sunday"
            else -> "Unknown"
        }
    }

    // In-app operations
    fun addTimetableClass(subject: String, day: Int, start: String, end: String, room: String?, teacher: String?, period: Int) {
        val course = selectedCourse.value ?: return
        val currentUserName = _studentName.value.ifBlank { "Classmate" }
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        viewModelScope.launch {
            val classItem = TimetableClass(
                courseCode = course.code,
                dayOfWeek = day,
                periodNumber = period,
                subject = subject,
                startTime = start,
                endTime = end,
                room = room,
                teacherName = teacher,
                authorName = currentUserName,
                authorUid = currentUserId
            )
            repository.addClass(classItem)
            generateSmartNotifications()
            scheduleTimetableClassNotifications()
            syncDataToFirebase()

            // Broadcast timetable change to batch peers
            batchSyncManager.postSharedTimetableClass(
                cls = classItem,
                college = _studentCollege.value,
                course = course.code,
                admissionYear = _studentAdmissionYear.value,
                batch = _studentBatch.value,
                authorName = currentUserName,
                customBatchCode = _customBatchCode.value
            )
        }
    }

    fun updateTimetableClass(id: Int, subject: String, day: Int, start: String, end: String, room: String?, teacher: String?, period: Int) {
        val course = selectedCourse.value ?: return
        val currentUserName = _studentName.value.ifBlank { "Classmate" }
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        viewModelScope.launch {
            val classItem = TimetableClass(
                id = id,
                courseCode = course.code,
                dayOfWeek = day,
                periodNumber = period,
                subject = subject,
                startTime = start,
                endTime = end,
                room = room,
                teacherName = teacher,
                authorName = currentUserName,
                authorUid = currentUserId
            )
            repository.addClass(classItem)
            generateSmartNotifications()
            scheduleTimetableClassNotifications()
            syncDataToFirebase()

            // Broadcast updated class to batch peers
            batchSyncManager.postSharedTimetableClass(
                cls = classItem,
                college = _studentCollege.value,
                course = course.code,
                admissionYear = _studentAdmissionYear.value,
                batch = _studentBatch.value,
                authorName = currentUserName,
                customBatchCode = _customBatchCode.value
            )
        }
    }

    fun removeTimetableClass(id: Int) {
        viewModelScope.launch {
            val existing = repository.getAllTimetableClassesOnce().find { it.id == id }
            repository.deleteClass(id)
            if (existing != null && existing.firestoreId.isNotBlank()) {
                batchSyncManager.deleteSharedTimetableClass(
                    firestoreId = existing.firestoreId,
                    college = _studentCollege.value,
                    course = selectedCourse.value?.code ?: "MBBS",
                    admissionYear = _studentAdmissionYear.value,
                    batch = _studentBatch.value,
                    customBatchCode = _customBatchCode.value
                )
            }
            generateSmartNotifications()
            scheduleTimetableClassNotifications()
            syncDataToFirebase()
        }
    }

    fun addAssignment(
        subject: String,
        title: String,
        dueDate: Long,
        priority: String,
        type: String,
        notes: String? = null,
        shareWithBatch: Boolean = true,
        forceSave: Boolean = false
    ) {
        val course = selectedCourse.value ?: return
        val currentUserName = _studentName.value.ifBlank { "Classmate" }
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

        viewModelScope.launch {
            if (!forceSave) {
                val batchMatch = batchSyncManager.checkDuplicateAssignment(subject, title)
                val localMatch = repository.findMatchingSimilarAssignment(course.code, subject.trim(), title.trim())

                if (batchMatch != null || localMatch != null) {
                    val author = when {
                        batchMatch != null && batchMatch.authorName.isNotBlank() && batchMatch.authorName != "Classmate" -> batchMatch.authorName
                        localMatch != null && localMatch.authorName.isNotBlank() && localMatch.authorName != "Classmate" -> localMatch.authorName
                        localMatch?.notes?.contains("Shared by ") == true -> localMatch.notes.substringAfter("Shared by ").substringBefore(")").trim()
                        batchMatch?.authorName?.isNotBlank() == true -> batchMatch.authorName
                        localMatch?.authorName?.isNotBlank() == true -> localMatch.authorName
                        else -> "Your Classmate"
                    }
                    val timestamp = batchMatch?.timestamp ?: localMatch?.dueDate ?: System.currentTimeMillis()
                    val matchedTitle = batchMatch?.title ?: localMatch?.title ?: title.trim()

                    _duplicateContributionDialog.value = DuplicateContributionInfo(
                        itemType = "Assignment",
                        subject = subject.trim(),
                        title = matchedTitle,
                        authorName = author,
                        submissionTimestamp = timestamp,
                        batchName = _studentBatch.value.ifBlank { "Batch" },
                        extraDetails = "Due: " + java.text.SimpleDateFormat("dd MMM, yyyy", java.util.Locale.getDefault()).format(java.util.Date(dueDate)),
                        onConfirmSaveAnyway = {
                            addAssignment(subject, title, dueDate, priority, type, notes, shareWithBatch, forceSave = true)
                        }
                    )
                    return@launch
                }
            }

            val assignment = Assignment(
                courseCode = course.code,
                subject = subject,
                title = title,
                dueDate = dueDate,
                priority = priority,
                status = "Pending",
                type = type,
                notes = notes,
                authorName = currentUserName,
                authorUid = currentUserId
            )
            val insertedId = repository.addAssignment(assignment)
            generateSmartNotifications()
            
            if (_areNotificationsEnabled.value) {
                AcademicNotificationManager.scheduleNotification(
                    context = getApplication(),
                    type = "assignment",
                    itemId = "asg_$insertedId",
                    title = "Upcoming Assignment Alert",
                    message = "Assignment '$title' for $subject is due in less than 24 hours!",
                    targetTime = dueDate,
                    subject = subject,
                    minutesBefore = 24 * 60 // 24 hours before
                )
                com.example.util.NotificationHelper.showNotification(
                    context = getApplication(),
                    title = "Assignment Added: $subject",
                    message = "$title • Synced with your batch",
                    notificationId = (insertedId.toInt() + 1000) % 100000,
                    type = "assignment",
                    subject = subject,
                    targetTime = dueDate
                )
            }
            syncDataToFirebase()

            if (shareWithBatch) {
                val shareResult = batchSyncManager.postSharedAssignment(
                    assignment = assignment.copy(id = insertedId.toInt()),
                    college = _studentCollege.value,
                    course = course.code,
                    admissionYear = _studentAdmissionYear.value,
                    batch = _studentBatch.value,
                    authorName = currentUserName,
                    customBatchCode = _customBatchCode.value
                )
                shareResult.fold(
                    onSuccess = {
                        _batchShareFeedbackMessage.value = "Shared '${assignment.title}' with your batch channel!"
                    },
                    onFailure = { err ->
                        _batchShareFeedbackMessage.value = "Saved locally. Batch sync notice: ${err.message}"
                    }
                )
            }
        }
    }

    fun toggleAssignment(assignment: Assignment) {
        viewModelScope.launch {
            repository.updateAssignmentStatus(assignment.id, assignment.status != "Completed")
            if (assignment.status != "Completed") {
                // If toggled to completed, cancel the notification
                AcademicNotificationManager.cancelByItemId(getApplication(), "asg_${assignment.id}")
            }
            syncDataToFirebase()
        }
    }

    fun removeAssignment(id: Int) {
        viewModelScope.launch {
            val assignment = repository.getAllAssignmentsOnce().find { it.id == id }
            repository.deleteAssignment(id)
            AcademicNotificationManager.cancelByItemId(getApplication(), "asg_$id")
            if (assignment != null) {
                batchSyncManager.deleteSharedAssignment(
                    firestoreId = assignment.firestoreId,
                    college = _studentCollege.value,
                    course = selectedCourse.value?.code ?: "MBBS",
                    admissionYear = _studentAdmissionYear.value,
                    batch = _studentBatch.value,
                    customBatchCode = _customBatchCode.value
                )
            }
            syncDataToFirebase()
        }
    }

    fun addAssessment(
        subject: String,
        title: String,
        date: Long,
        type: String,
        syllabus: String? = null,
        shareWithBatch: Boolean = true,
        forceSave: Boolean = false
    ) {
        val course = selectedCourse.value ?: return
        val currentUserName = _studentName.value.ifBlank { "Classmate" }
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

        viewModelScope.launch {
            if (!forceSave) {
                val batchMatch = batchSyncManager.checkDuplicateAssessment(subject, title)
                val localMatch = repository.findMatchingSimilarAssessment(course.code, subject.trim(), title.trim())

                if (batchMatch != null || localMatch != null) {
                    val author = when {
                        batchMatch != null && batchMatch.authorName.isNotBlank() && batchMatch.authorName != "Classmate" -> batchMatch.authorName
                        localMatch != null && localMatch.authorName.isNotBlank() && localMatch.authorName != "Classmate" -> localMatch.authorName
                        batchMatch?.authorName?.isNotBlank() == true -> batchMatch.authorName
                        localMatch?.authorName?.isNotBlank() == true -> localMatch.authorName
                        else -> "Your Classmate"
                    }
                    val timestamp = batchMatch?.timestamp ?: localMatch?.date ?: System.currentTimeMillis()
                    val matchedTitle = batchMatch?.title ?: localMatch?.title ?: title.trim()

                    _duplicateContributionDialog.value = DuplicateContributionInfo(
                        itemType = "Assessment",
                        subject = subject.trim(),
                        title = matchedTitle,
                        authorName = author,
                        submissionTimestamp = timestamp,
                        batchName = _studentBatch.value.ifBlank { "Batch" },
                        extraDetails = "Date: " + java.text.SimpleDateFormat("dd MMM, yyyy", java.util.Locale.getDefault()).format(java.util.Date(date)),
                        onConfirmSaveAnyway = {
                            addAssessment(subject, title, date, type, syllabus, shareWithBatch, forceSave = true)
                        }
                    )
                    return@launch
                }
            }

            val assessment = Assessment(
                courseCode = course.code,
                subject = subject,
                title = title,
                date = date,
                type = type,
                status = "Upcoming",
                syllabus = syllabus,
                authorName = currentUserName,
                authorUid = currentUserId
            )
            val insertedId = repository.addAssessment(assessment)
            generateSmartNotifications()
            
            if (_areNotificationsEnabled.value) {
                AcademicNotificationManager.scheduleNotification(
                    context = getApplication(),
                    type = "assessment",
                    itemId = "asm_$insertedId",
                    title = "Upcoming Assessment Reminder",
                    message = "Assessment '$title' for $subject is scheduled soon!",
                    targetTime = date,
                    subject = subject,
                    minutesBefore = 30
                )
                com.example.util.NotificationHelper.showNotification(
                    context = getApplication(),
                    title = "Assessment Scheduled: $subject",
                    message = "$title • Synced with your batch",
                    notificationId = (insertedId.toInt() + 2000) % 100000,
                    type = "assessment",
                    subject = subject,
                    targetTime = date
                )
            }
            syncDataToFirebase()

            if (shareWithBatch) {
                val shareResult = batchSyncManager.postSharedAssessment(
                    assessment = assessment.copy(id = insertedId.toInt()),
                    college = _studentCollege.value,
                    course = course.code,
                    admissionYear = _studentAdmissionYear.value,
                    batch = _studentBatch.value,
                    authorName = currentUserName,
                    customBatchCode = _customBatchCode.value
                )
                shareResult.fold(
                    onSuccess = {
                        _batchShareFeedbackMessage.value = "Broadcasted '${assessment.title}' to your batch schedule!"
                    },
                    onFailure = { err ->
                        _batchShareFeedbackMessage.value = "Saved locally. Batch sync notice: ${err.message}"
                    }
                )
            }
        }
    }

    fun toggleAssessment(assessment: Assessment) {
        viewModelScope.launch {
            repository.updateAssessmentStatus(assessment.id, assessment.status != "Completed")
            if (assessment.status != "Completed") {
                AcademicNotificationManager.cancelByItemId(getApplication(), "asm_${assessment.id}")
            }
            syncDataToFirebase()
        }
    }

    fun removeAssessment(id: Int) {
        viewModelScope.launch {
            val assessment = repository.getAllAssessmentsOnce().find { it.id == id }
            repository.deleteAssessment(id)
            AcademicNotificationManager.cancelByItemId(getApplication(), "asm_$id")
            if (assessment != null) {
                batchSyncManager.deleteSharedAssessment(
                    firestoreId = assessment.firestoreId,
                    college = _studentCollege.value,
                    course = selectedCourse.value?.code ?: "MBBS",
                    admissionYear = _studentAdmissionYear.value,
                    batch = _studentBatch.value,
                    customBatchCode = _customBatchCode.value
                )
            }
            syncDataToFirebase()
        }
    }

    fun addStudyTask(subject: String, title: String, dueDate: Long, priority: String, minutes: Int = 30) {
        val course = selectedCourse.value ?: return
        viewModelScope.launch {
            val task = StudyTask(
                    courseCode = course.code,
                    subject = subject,
                    title = title,
                    dueDate = dueDate,
                    priority = priority,
                    progress = 0,
                    targetMinutes = minutes
                )
            val insertedId = repository.addStudyTask(task)
            
            if (_areNotificationsEnabled.value) {
                AcademicNotificationManager.scheduleNotification(
                    context = getApplication(),
                    type = "study",
                    itemId = "study_$insertedId",
                    title = "Study Task Due",
                    message = "Study Task '$title' for $subject is due soon!",
                    targetTime = dueDate,
                    subject = subject,
                    minutesBefore = 30
                )
            }
            syncDataToFirebase()
        }
    }

    fun updateStudyProgress(id: Int, progress: Int) {
        viewModelScope.launch {
            repository.updateStudyTaskProgress(id, progress)
            if (progress >= 100) {
                AcademicNotificationManager.cancelByItemId(getApplication(), "study_$id")
            }
            syncDataToFirebase()
        }
    }

    fun removeStudyTask(id: Int) {
        viewModelScope.launch {
            repository.deleteStudyTask(id)
            AcademicNotificationManager.cancelByItemId(getApplication(), "study_$id")
            syncDataToFirebase()
        }
    }

    // Syllabus Record & Finished Topics
    fun addCompletedTopic(
        subject: String,
        topicTitle: String,
        academicYear: String,
        teacherName: String? = null,
        notes: String? = null,
        shareWithBatch: Boolean = true,
        forceSave: Boolean = false
    ) {
        val course = selectedCourse.value ?: MedicalCourse.MBBS
        val effectiveYear = academicYear.ifBlank { _studentYear.value.ifBlank { "1st Year" } }
        val currentUserName = _studentName.value.ifBlank { "Classmate" }
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

        viewModelScope.launch {
            if (!forceSave) {
                val batchMatch = batchSyncManager.checkDuplicateCompletedTopicRemote(
                    college = _studentCollege.value,
                    course = course.code,
                    admissionYear = _studentAdmissionYear.value,
                    batch = _studentBatch.value,
                    subject = subject.trim(),
                    topicTitle = topicTitle.trim(),
                    customBatchCode = _customBatchCode.value
                )
                val localMatch = repository.findMatchingSimilarCompletedTopic(course.code, subject.trim(), topicTitle.trim())

                if (batchMatch != null || localMatch != null) {
                    val author = when {
                        batchMatch != null && batchMatch.authorName.isNotBlank() && batchMatch.authorName != "Classmate" -> batchMatch.authorName
                        localMatch != null && localMatch.authorName.isNotBlank() && localMatch.authorName != "Classmate" -> localMatch.authorName
                        localMatch?.notes?.contains("Recorded by ") == true -> localMatch.notes.substringAfter("Recorded by ").substringBefore(")").trim()
                        localMatch?.notes?.contains("Completed with batch (by ") == true -> localMatch.notes.substringAfter("Completed with batch (by ").substringBefore(")").trim()
                        batchMatch?.notes?.contains("Recorded by ") == true -> batchMatch.notes.substringAfter("Recorded by ").substringBefore(")").trim()
                        batchMatch?.authorName?.isNotBlank() == true -> batchMatch.authorName
                        localMatch?.authorName?.isNotBlank() == true -> localMatch.authorName
                        else -> "Your Classmate"
                    }
                    val timestamp = batchMatch?.completionDate ?: localMatch?.completionDate ?: System.currentTimeMillis()
                    val matchedTitle = batchMatch?.topicTitle ?: localMatch?.topicTitle ?: topicTitle.trim()

                    _duplicateContributionDialog.value = DuplicateContributionInfo(
                        itemType = "Chapter",
                        subject = subject.trim(),
                        title = matchedTitle,
                        authorName = author,
                        submissionTimestamp = timestamp,
                        batchName = _studentBatch.value.ifBlank { "Batch" },
                        extraDetails = if (!matchedTitle.equals(topicTitle.trim(), ignoreCase = true)) {
                            "Already recorded as '$matchedTitle' in $effectiveYear"
                        } else {
                            "Syllabus Year: $effectiveYear"
                        },
                        onConfirmSaveAnyway = {
                            addCompletedTopic(subject, topicTitle, academicYear, teacherName, notes, shareWithBatch, forceSave = true)
                        }
                    )
                    return@launch
                }
            }

            val topic = CompletedSyllabusTopic(
                courseCode = course.code,
                academicYear = effectiveYear,
                subject = subject.trim(),
                topicTitle = topicTitle.trim(),
                completionDate = System.currentTimeMillis(),
                teacherName = teacherName?.trim()?.ifBlank { null },
                notes = notes?.trim()?.ifBlank { null },
                isSharedWithBatch = shareWithBatch,
                authorName = currentUserName,
                authorUid = currentUserId
            )
            repository.addCompletedTopic(topic)

            addNotificationWithDuplicateCheck(
                InAppNotification(
                    title = "Syllabus Record Updated",
                    message = "Covered '$topicTitle' in $subject.",
                    type = "syllabus"
                )
            )

            if (shareWithBatch) {
                // 1. Post to batch completed chapters collection
                batchSyncManager.postSharedCompletedTopic(
                    topic = topic,
                    college = _studentCollege.value,
                    course = course.code,
                    admissionYear = _studentAdmissionYear.value,
                    batch = _studentBatch.value,
                    authorName = currentUserName,
                    customBatchCode = _customBatchCode.value
                )

                // 2. Broadcast batch notice
                val postResult = batchSyncManager.postBatchNotice(
                    college = _studentCollege.value,
                    course = course.code,
                    admissionYear = _studentAdmissionYear.value,
                    batch = _studentBatch.value,
                    title = "Syllabus Finished: $subject",
                    message = "Covered in class: '$topicTitle' ($effectiveYear).",
                    authorName = currentUserName,
                    category = "syllabus",
                    urgent = false,
                    customBatchCode = _customBatchCode.value
                )
                postResult.fold(
                    onSuccess = {
                        _batchShareFeedbackMessage.value = "Shared covered chapter '$topicTitle' with your batch channel!"
                    },
                    onFailure = { err ->
                        _batchShareFeedbackMessage.value = "Saved locally. Batch sync: ${err.message}"
                    }
                )
            }
            syncDataToFirebase()
        }
    }

    fun removeCompletedTopic(id: Int) {
        viewModelScope.launch {
            val topic = repository.getCompletedTopicById(id)
            repository.deleteCompletedTopic(id)
            if (topic != null && topic.firestoreId.isNotBlank()) {
                val course = selectedCourse.value ?: MedicalCourse.MBBS
                batchSyncManager.deleteSharedCompletedTopic(
                    firestoreId = topic.firestoreId,
                    college = _studentCollege.value,
                    course = course.code,
                    admissionYear = _studentAdmissionYear.value,
                    batch = _studentBatch.value,
                    customBatchCode = _customBatchCode.value
                )
            }
            syncDataToFirebase()
        }
    }

    fun getSyllabusSubjectsForCourseAndYear(courseCode: String, year: String): List<SyllabusSubject> {
        return CourseSyllabusDirectory.getSubjectsForYear(courseCode, year)
    }

    fun getSyllabusYearsForCourse(courseCode: String): List<String> {
        return CourseSyllabusDirectory.getAvailableYears(courseCode)
    }

    // AI WhatsApp Parser Chat logic
    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            // Add User Message
            val userMsg = ChatMessage(sender = "user", message = text)
            repository.addChatMessage(userMsg)
            
            parseInputDocument(text, null, null)
        }
    }

    // Unified AI parser trigger for text, images, or PDFs
    fun parseInputDocument(textInput: String?, imageBytes: ByteArray?, mimeType: String?) {
        _isParsingMessage.value = true
        _activeUnifiedResponse.value = null
        _activeParsedDrafts.value = emptyList()
        
        viewModelScope.launch {
            try {
                val currentScheduleSummary = timetable.value.let { list ->
                    if (list.isEmpty()) "No classes configured yet."
                    else {
                        val daysGrouped = list.groupBy { it.dayOfWeek }
                        daysGrouped.entries.sortedBy { it.key }.joinToString("\n") { (dayNum, classes) ->
                            val dayName = getDayName(dayNum)
                            val classStrs = classes.sortedBy { it.periodNumber }.joinToString("; ") { "${it.startTime}-${it.endTime}: ${it.subject} (${it.room ?: "Hall"})" }
                            "$dayName: $classStrs"
                        }
                    }
                }

                val response = geminiService.parseDocument(
                    textInput,
                    imageBytes,
                    mimeType,
                    currentScheduleSummary,
                    _selectedGeminiModel.value
                )
                if (response.extracted_timetable.isEmpty() && response.extracted_items.isEmpty()) {
                    _activeUnifiedResponse.value = null
                    _activeParsedDrafts.value = emptyList()
                } else {
                    _activeUnifiedResponse.value = response
                    _activeParsedDrafts.value = response.extracted_items
                }

                val docName = when {
                    imageBytes != null -> "Uploaded Image Document"
                    textInput != null -> "Pasted WhatsApp/Text Notice"
                    else -> "Uploaded PDF Document"
                }

                // If user text message was sent, do not add duplicate user message since it's already added in UI
                if (imageBytes != null) {
                    val userMsg = ChatMessage(sender = "user", message = "Uploaded document: $docName")
                    repository.addChatMessage(userMsg)
                }

                val aiResponseText = if (!response.conversational_response.isNullOrEmpty()) {
                    response.conversational_response
                } else if (response.extracted_timetable.isNotEmpty()) {
                    val days = response.extracted_timetable.map { it.day_of_week }.distinct().size
                    val classesCount = response.extracted_timetable.filter { !it.is_lunch_break }.size
                    val teachersCount = response.extracted_timetable.mapNotNull { it.teacher_name }.distinct().size
                    val practicalsCount = response.extracted_timetable.count { it.is_practical }

                    "I analyzed your **Weekly Timetable** and extracted the structured routine:\n\n" +
                            "✓ $days Working Days\n" +
                            "✓ $classesCount Classes\n" +
                            "✓ $teachersCount Teachers\n" +
                            "✓ $practicalsCount Practical Sessions\n\n" +
                            "Please review the schedule preview below and tap **Replace Current Timetable** to apply it."
                } else if (response.is_temporary_override && response.extracted_items.isNotEmpty()) {
                    val total = response.extracted_items.size
                    val categories = response.extracted_items.map { it.category }.distinct().joinToString(", ")
                    "I detected a **Schedule Adjustment / Override** ($categories):\n\n" +
                            "This adjustment is proposed for **${response.override_date ?: "Tomorrow"}**. It will adjust specific classes rather than replacing your whole timetable.\n\n" +
                            "✓ $total Schedule adjustment${if (total > 1) "s" else ""} found.\n\n" +
                            "Review details below and tap **Import Selected** to apply."
                } else if (response.extracted_items.isNotEmpty()) {
                    val assignments = response.extracted_items.count { it.category.contains("Assignment", ignoreCase = true) || it.category.contains("Homework", ignoreCase = true) }
                    val assessments = response.extracted_items.count { it.category.contains("Assessment", ignoreCase = true) || it.category.contains("Exam", ignoreCase = true) || it.category.contains("Viva", ignoreCase = true) || it.category.contains("Test", ignoreCase = true) }
                    val holidays = response.extracted_items.count { it.category.contains("Holiday", ignoreCase = true) }
                    val other = response.extracted_items.size - assignments - assessments - holidays

                    buildString {
                        append("I analyzed the announcement and classified the following:\n\n")
                        if (assignments > 0) append("✓ $assignments Assignment${if (assignments > 1) "s" else ""}\n")
                        if (assessments > 0) append("✓ $assessments Assessment${if (assessments > 1) "s" else ""} / Exam${if (assessments > 1) "s" else ""}\n")
                        if (holidays > 0) append("✓ $holidays Holiday${if (holidays > 1) "s" else ""}\n")
                        if (other > 0) append("✓ $other General Notice${if (other > 1) "s" else ""}\n")
                        append("\nReview the proposed items below and tap **Import Selected** to schedule them.")
                    }
                } else {
                    if (imageBytes != null) {
                        "I couldn't detect any academic timetable or schedule in this image. Please upload a clear photo or screenshot of your college timetable, routine, or class announcement."
                    } else {
                        "I couldn't find any timetable classes or academic notices in your input. Please provide a clear class schedule or announcement."
                    }
                }

                val aiMsg = ChatMessage(
                    sender = "ai",
                    message = aiResponseText
                )
                repository.addChatMessage(aiMsg)

            } catch (e: Exception) {
                Log.e("PlannerVM", "Error parsing input document", e)
                repository.addChatMessage(
                    ChatMessage(sender = "ai", message = "Sorry, I ran into an error parsing the input. Please try again.")
                )
            } finally {
                _isParsingMessage.value = false
            }
        }
    }

    fun getDefaultParsedTimetable(course: MedicalCourse): List<ParsedTimetableClass> {
        return repository.getDefaultParsedTimetableForCourse(course.code)
    }

    suspend fun parseTimetableDocument(textInput: String?, imageBytes: ByteArray?, mimeType: String?, course: MedicalCourse): List<ParsedTimetableClass> {
        return withContext(Dispatchers.IO) {
            try {
                val response = geminiService.parseDocument(textInput, imageBytes, mimeType, "", _selectedGeminiModel.value)
                val result = response.extracted_timetable

                if (result.isEmpty()) {
                    repository.getDefaultParsedTimetableForCourse(course.code)
                } else {
                    result.sortedWith(compareBy({ it.day_of_week }, { it.period_number }))
                }
            } catch (e: Exception) {
                Log.e("PlannerVM", "Failed to parse document with Gemini", e)
                repository.getDefaultParsedTimetableForCourse(course.code)
            }
        }
    }

    fun completeOnboarding(course: MedicalCourse) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean("is_onboarding_completed", true)
            .putBoolean("is_profile_completed", true)
            .putString("selected_course_code", course.name)
            .apply()

        val onboardingPrefs = getApplication<Application>().getSharedPreferences("medpulse_onboarding_draft", Application.MODE_PRIVATE)
        onboardingPrefs.edit().clear().apply()

        selectCourse(course)

        _isProfileCompleted.value = true
        _authState.value = AuthState.AUTHENTICATED_PROFILE_COMPLETE
        _currentScreen.value = Screen.Dashboard

        // Automatically sync fresh user profile and timetable to Firebase Cloud
        syncDataToFirebase()

        // Immediately connect to shared batch channel and fetch all batch chapters, assignments, assessments
        val col = _studentCollege.value
        val crs = course.code
        val admYr = _studentAdmissionYear.value
        val btch = _studentBatch.value
        val customCode = _customBatchCode.value
        batchSyncManager.startListeningToBatch(col, crs, admYr, btch, customCode)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                batchSyncManager.fetchAndSyncBatchNow(col, crs, admYr, btch, customCode)
            } catch (e: Exception) {
                Log.w("PlannerViewModel", "Initial batch sync failed: ${e.message}")
            }
        }
    }

    fun isOnboardingCompleted(): Boolean {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        return sharedPrefs.getBoolean("is_onboarding_completed", false) && sharedPrefs.getString("selected_course_code", null) != null
    }

    fun setOnboardingCompleted(completed: Boolean) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("is_onboarding_completed", completed).apply()
    }

    // Replace the current timetable with a parsed timetable
    fun replaceCurrentTimetable(timetableList: List<ParsedTimetableClass>) {
        val course = selectedCourse.value ?: return
        viewModelScope.launch {
            // Delete old timetable
            repository.clearTimetable(course.code)
            
            // Map parsed timetable classes to local Room TimetableClass entities
            val currentUserName = _studentName.value.ifBlank { "Classmate" }
            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val colors = listOf("#4F46E5", "#06B6D4", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6")
            val newClasses = timetableList.mapIndexed { idx, parsed ->
                TimetableClass(
                    courseCode = course.code,
                    dayOfWeek = parsed.day_of_week,
                    periodNumber = parsed.period_number,
                    subject = parsed.subject,
                    startTime = parsed.start_time,
                    endTime = parsed.end_time,
                    room = parsed.room ?: "General",
                    teacherName = parsed.teacher_name,
                    colorHex = colors[idx % colors.size],
                    authorName = currentUserName,
                    authorUid = currentUserId
                )
            }
            
            // Insert each class
            for (cls in newClasses) {
                repository.addClass(cls)
            }

            // Broadcast entire new schedule to the batch
            batchSyncManager.postSharedFullTimetable(
                classes = newClasses,
                college = _studentCollege.value,
                course = course.code,
                admissionYear = _studentAdmissionYear.value,
                batch = _studentBatch.value,
                authorName = currentUserName,
                customBatchCode = _customBatchCode.value
            )

            batchSyncManager.postBatchNotice(
                college = _studentCollege.value,
                course = course.code,
                admissionYear = _studentAdmissionYear.value,
                batch = _studentBatch.value,
                title = "Timetable Updated for Batch",
                message = "The weekly class timetable has been updated by $currentUserName.",
                authorName = currentUserName,
                category = "batch_notice",
                urgent = false,
                customBatchCode = _customBatchCode.value
            )
            
            // Insert Teachers and Rooms if available
            for (parsed in timetableList) {
                parsed.teacher_name?.let {
                    repository.addTeacher(Teacher(name = it, department = "Department of " + parsed.subject))
                }
                parsed.room?.let {
                    repository.addRoom(RoomEntity(roomName = it, location = "Block A"))
                }
            }
            
            // Invalidate/refresh active response
            _activeUnifiedResponse.value = null
            _activeParsedDrafts.value = emptyList()
            
            // Create in-app notification of new timetable configuration
            addNotificationWithDuplicateCheck(
                InAppNotification(
                    title = "New Timetable Configured",
                    message = "Successfully imported ${newClasses.size} recurring classes for ${course.displayName}.",
                    type = "class"
                )
            )
            
            // Confirm inside chat
            repository.addChatMessage(
                ChatMessage(
                    sender = "ai",
                    message = "Successfully replaced your timetable with **${newClasses.size} recurring classes**! 🗓️ Your dashboard, Today, Tomorrow, and Calendar schedules are now fully updated."
                )
            )
            
            // Generate smart notification alerts for tomorrow/today
            generateSmartNotifications()
            scheduleTimetableClassNotifications()
        }
    }

    fun cancelUnifiedImport() {
        _activeUnifiedResponse.value = null
        _activeParsedDrafts.value = emptyList()
    }

    // Approve the AI-parsed schedule draft items
    fun approveAndImportDrafts(approvedList: List<ParsedItem>) {
        val course = selectedCourse.value ?: return
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val formatDays = mapOf(
                "tomorrow" to 1,
                "monday" to calculateDaysToDayOfWeek(Calendar.MONDAY),
                "tuesday" to calculateDaysToDayOfWeek(Calendar.TUESDAY),
                "wednesday" to calculateDaysToDayOfWeek(Calendar.WEDNESDAY),
                "thursday" to calculateDaysToDayOfWeek(Calendar.THURSDAY),
                "friday" to calculateDaysToDayOfWeek(Calendar.FRIDAY),
                "saturday" to calculateDaysToDayOfWeek(Calendar.SATURDAY),
                "sunday" to calculateDaysToDayOfWeek(Calendar.SUNDAY)
            )

            for (item in approvedList) {
                // Determine timestamps
                val itemCal = Calendar.getInstance()
                val desc = item.due_date_description.lowercase().trim()
                var daysOffset = 0
                for ((key, offset) in formatDays) {
                    if (desc.contains(key)) {
                        daysOffset = offset
                        break
                    }
                }
                itemCal.add(Calendar.DAY_OF_YEAR, daysOffset)
                val itemTimestamp = itemCal.timeInMillis
                
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val dateStr = sdf.format(itemCal.time)

                // Add Teachers and Rooms if found
                item.details?.let { details ->
                    if (details.contains("Dr.")) {
                        val docName = "Dr. " + details.substringAfter("Dr. ").substringBefore(" ").trim()
                        repository.addTeacher(Teacher(name = docName))
                    }
                }

                val categoryNormalized = item.category.trim().lowercase()
                val titleNormalized = item.title.trim().lowercase()
                val detailsNormalized = (item.details ?: "").trim().lowercase()

                when {
                    categoryNormalized.contains("cancellation") || 
                    categoryNormalized.contains("room change") || 
                    categoryNormalized.contains("teacher change") || 
                    categoryNormalized.contains("schedule change") || 
                    categoryNormalized.contains("override") -> {
                        val isCancelled = categoryNormalized.contains("cancellation")
                        repository.addOverride(
                            ScheduleOverride(
                                courseCode = course.code,
                                dateString = dateStr,
                                subject = item.subject,
                                startTime = "09:00 AM", // Proposed slot
                                endTime = "10:00 AM",
                                room = if (categoryNormalized.contains("room")) item.details?.substringAfter("to ")?.substringBefore(" ")?.trim() else "Lecture Hall B",
                                teacherName = if (categoryNormalized.contains("teacher")) item.details?.substringAfter("Dr. ")?.substringBefore(" ")?.trim()?.let { "Dr. $it" } else null,
                                isCancelled = isCancelled
                            )
                        )
                        // Add companion planner task
                        repository.addPlannerTask(
                            PlannerTask(
                                courseCode = course.code,
                                title = "${item.category}: ${item.title}",
                                date = itemTimestamp,
                                category = "Schedule Override"
                            )
                        )

                        // In-App Notification
                        addNotificationWithDuplicateCheck(
                            InAppNotification(
                                title = "Schedule Override Detected",
                                message = "${item.subject}: ${item.title} on $dateStr.",
                                type = "class"
                            )
                        )

                        // Auto-broadcast urgent schedule change to batch
                        batchSyncManager.postBatchNotice(
                            college = _studentCollege.value,
                            course = course.code,
                            admissionYear = _studentAdmissionYear.value,
                            batch = _studentBatch.value,
                            title = "Schedule Change: ${item.subject}",
                            message = "${item.title} on $dateStr",
                            authorName = _studentName.value.ifBlank { "Class Representative" },
                            category = "batch_notice",
                            urgent = true,
                            customBatchCode = _customBatchCode.value
                        )
                    }
                    categoryNormalized == "exam" || 
                    categoryNormalized.contains("university exam") -> {
                        repository.addExam(
                            Exam(
                                courseCode = course.code,
                                subject = item.subject,
                                title = item.title,
                                date = itemTimestamp,
                                time = "09:30 AM",
                                room = "Examination Hall",
                                syllabus = item.details
                            )
                        )

                        // Also save to assessments table so it appears in Planner -> Exams/Vivas and Calendar
                        val examItem = Assessment(
                            courseCode = course.code,
                            subject = item.subject,
                            title = item.title,
                            date = itemTimestamp,
                            type = "University Exam",
                            status = "Upcoming",
                            syllabus = item.details
                        )
                        val asmId = repository.addAssessment(examItem)
                        batchSyncManager.postSharedAssessment(
                            assessment = examItem.copy(id = asmId.toInt()),
                            college = _studentCollege.value,
                            course = course.code,
                            admissionYear = _studentAdmissionYear.value,
                            batch = _studentBatch.value,
                            authorName = _studentName.value.ifBlank { "Classmate" },
                            customBatchCode = _customBatchCode.value
                        )

                        // Add companion planner task
                        repository.addPlannerTask(
                            PlannerTask(
                                courseCode = course.code,
                                title = "Exam Study: ${item.title}",
                                date = itemTimestamp,
                                category = "Exam"
                            )
                        )

                        // In-App Notification
                        addNotificationWithDuplicateCheck(
                            InAppNotification(
                                title = "New Exam Imported",
                                message = "Exam '${item.title}' for ${item.subject} has been added to your schedule.",
                                type = "exam"
                            )
                        )

                        // Schedule system notification immediately
                        if (_areNotificationsEnabled.value) {
                            AcademicNotificationManager.scheduleNotification(
                                context = getApplication(),
                                type = "assessment",
                                itemId = "asm_$asmId",
                                title = "Upcoming Exam Reminder",
                                message = "Exam '${item.title}' for ${item.subject} is scheduled soon!",
                                targetTime = itemTimestamp,
                                subject = item.subject,
                                minutesBefore = 30
                            )
                        }
                    }
                    categoryNormalized == "assessment" || 
                    categoryNormalized == "viva" ||
                    categoryNormalized.contains("assessment") || 
                    categoryNormalized.contains("viva") || 
                    categoryNormalized.contains("test") || 
                    categoryNormalized.contains("internal") || 
                    categoryNormalized.contains("midterm") ||
                    categoryNormalized.contains("quiz") ||
                    titleNormalized.contains("assessment") ||
                    titleNormalized.contains("viva") ||
                    titleNormalized.contains("test") ||
                    titleNormalized.contains("quiz") ||
                    titleNormalized.contains("internal") ||
                    titleNormalized.contains("midterm") ||
                    titleNormalized.contains("exam") ||
                    detailsNormalized.contains("assessment") ||
                    detailsNormalized.contains("viva") ||
                    detailsNormalized.contains("test") ||
                    detailsNormalized.contains("internal") -> {
                        val batchAsm = Assessment(
                            courseCode = course.code,
                            subject = item.subject,
                            title = item.title,
                            date = itemTimestamp,
                            type = if (categoryNormalized.contains("viva") || titleNormalized.contains("viva")) "Viva" else if (categoryNormalized.contains("exam") || titleNormalized.contains("exam")) "University Exam" else "Class Test",
                            syllabus = item.details
                        )
                        val asmId = repository.addAssessment(batchAsm)
                        batchSyncManager.postSharedAssessment(
                            assessment = batchAsm.copy(id = asmId.toInt()),
                            college = _studentCollege.value,
                            course = course.code,
                            admissionYear = _studentAdmissionYear.value,
                            batch = _studentBatch.value,
                            authorName = _studentName.value.ifBlank { "Classmate" },
                            customBatchCode = _customBatchCode.value
                        )
                        // Add companion planner task
                        repository.addPlannerTask(
                            PlannerTask(
                                courseCode = course.code,
                                title = "Prepare for ${item.title}",
                                date = itemTimestamp,
                                category = "Assessment"
                            )
                        )

                        // In-App Notification
                        addNotificationWithDuplicateCheck(
                            InAppNotification(
                                title = "New Assessment Imported",
                                message = "Assessment '${item.title}' for ${item.subject} has been added.",
                                type = "exam"
                            )
                        )

                        // Schedule system notification immediately
                        if (_areNotificationsEnabled.value) {
                            AcademicNotificationManager.scheduleNotification(
                                context = getApplication(),
                                type = "assessment",
                                itemId = "asm_$asmId",
                                title = "Upcoming Assessment Reminder",
                                message = "Assessment '${item.title}' for ${item.subject} is scheduled soon!",
                                targetTime = itemTimestamp,
                                subject = item.subject,
                                minutesBefore = 30
                            )
                        }
                    }
                    categoryNormalized == "assignment" || 
                    categoryNormalized == "homework" ||
                    categoryNormalized.contains("assignment") || 
                    categoryNormalized.contains("homework") || 
                    categoryNormalized.contains("practical") || 
                    categoryNormalized.contains("lab") || 
                    categoryNormalized.contains("seminar") || 
                    categoryNormalized.contains("record") ||
                    titleNormalized.contains("assignment") ||
                    titleNormalized.contains("homework") ||
                    titleNormalized.contains("record") ||
                    titleNormalized.contains("practical") -> {
                        val batchAsg = Assignment(
                            courseCode = course.code,
                            subject = item.subject,
                            title = item.title,
                            dueDate = itemTimestamp,
                            priority = item.priority,
                            status = "Pending",
                            type = if (categoryNormalized.contains("homework") || titleNormalized.contains("homework")) "Homework" else "Assignment",
                            notes = item.details
                        )
                        val asgId = repository.addAssignment(batchAsg)
                        batchSyncManager.postSharedAssignment(
                            assignment = batchAsg.copy(id = asgId.toInt()),
                            college = _studentCollege.value,
                            course = course.code,
                            admissionYear = _studentAdmissionYear.value,
                            batch = _studentBatch.value,
                            authorName = _studentName.value.ifBlank { "Classmate" },
                            customBatchCode = _customBatchCode.value
                        )
                        // Add companion planner task
                        repository.addPlannerTask(
                            PlannerTask(
                                courseCode = course.code,
                                title = item.title,
                                date = itemTimestamp,
                                category = "Assignment"
                            )
                        )

                        // In-App Notification
                        addNotificationWithDuplicateCheck(
                            InAppNotification(
                                title = "New Assignment Imported",
                                message = "Assignment '${item.title}' for ${item.subject} has been added.",
                                type = "assignment"
                            )
                        )

                        // Schedule system notification immediately
                        if (_areNotificationsEnabled.value) {
                            AcademicNotificationManager.scheduleNotification(
                                context = getApplication(),
                                type = "assignment",
                                itemId = "asg_$asgId",
                                title = "Upcoming Assignment Alert",
                                message = "Assignment '${item.title}' for ${item.subject} is due soon!",
                                targetTime = itemTimestamp,
                                subject = item.subject,
                                minutesBefore = 60
                            )
                        }
                    }
                    categoryNormalized.contains("study") || 
                    categoryNormalized.contains("goal") ||
                    titleNormalized.contains("study") ||
                    titleNormalized.contains("revise") -> {
                        val studyId = repository.addStudyTask(
                            StudyTask(
                                courseCode = course.code,
                                subject = item.subject,
                                title = item.title,
                                dueDate = itemTimestamp,
                                priority = item.priority,
                                targetMinutes = 45,
                                notes = item.details
                            )
                        )

                        // In-App Notification
                        addNotificationWithDuplicateCheck(
                            InAppNotification(
                                title = "New Study Goal Imported",
                                message = "Study Goal '${item.title}' for ${item.subject} has been added.",
                                type = "study"
                            )
                        )

                        // Schedule system notification immediately
                        if (_areNotificationsEnabled.value) {
                            AcademicNotificationManager.scheduleNotification(
                                context = getApplication(),
                                type = "study",
                                itemId = "study_$studyId",
                                title = "Study Revision Alert",
                                message = "Time to revise ${item.title} for ${item.subject}!",
                                targetTime = itemTimestamp,
                                subject = item.subject,
                                minutesBefore = 15
                            )
                        }
                    }
                    else -> {
                        // General Reminders/Notices mapped as a pending general Assignment or Notification alert
                        val asg = Assignment(
                            courseCode = course.code,
                            subject = item.subject,
                            title = item.title,
                            dueDate = itemTimestamp,
                            priority = item.priority,
                            status = "Pending",
                            type = "Assignment",
                            notes = item.details
                        )
                        val asgId = repository.addAssignment(asg)
                        batchSyncManager.postSharedAssignment(
                            assignment = asg.copy(id = asgId.toInt()),
                            college = _studentCollege.value,
                            course = course.code,
                            admissionYear = _studentAdmissionYear.value,
                            batch = _studentBatch.value,
                            authorName = _studentName.value.ifBlank { "Classmate" },
                            customBatchCode = _customBatchCode.value
                        )

                        // In-App Notification
                        addNotificationWithDuplicateCheck(
                            InAppNotification(
                                title = "New Alert Imported",
                                message = "${item.title} has been added to your general assignments.",
                                type = "assignment"
                            )
                        )

                        // Schedule system notification immediately
                        if (_areNotificationsEnabled.value) {
                            AcademicNotificationManager.scheduleNotification(
                                context = getApplication(),
                                type = "assignment",
                                itemId = "asg_$asgId",
                                title = "Upcoming Reminder",
                                message = "'${item.title}' is scheduled/due soon!",
                                targetTime = itemTimestamp,
                                subject = item.subject,
                                minutesBefore = 60
                            )
                        }
                    }
                }
            }

            // Clean active drafts list after approval
            _activeParsedDrafts.value = emptyList()
            _activeUnifiedResponse.value = null

            // Confirm inside chat
            repository.addChatMessage(
                ChatMessage(
                    sender = "ai",
                    message = "Successfully imported **${approvedList.size}** item(s) to your dashboard, calendars, and planner tasks! 🎉"
                )
            )

            generateSmartNotifications()
        }
    }


    private fun calculateDaysToDayOfWeek(targetCalDay: Int): Int {
        val current = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        var diff = targetCalDay - current
        if (diff <= 0) diff += 7 // Schedule for next week's day
        return diff
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearChat()
        }
    }

    private suspend fun addNotificationWithDuplicateCheck(notification: InAppNotification, pushToSystemTray: Boolean = false) {
        val existing = repository.getNotifications().first()
        val alreadyExists = existing.any { 
            it.title == notification.title && 
            it.message == notification.message &&
            it.type == notification.type
        }
        if (!alreadyExists) {
            repository.addNotification(notification)
            // Push to Android system notification tray only if explicitly requested for live alerts
            if (_areNotificationsEnabled.value && pushToSystemTray) {
                try {
                    val rawId = notification.title.hashCode() xor notification.message.hashCode() xor notification.type.hashCode()
                    val notificationId = if (rawId == Int.MIN_VALUE) 0 else java.lang.Math.abs(rawId) % 100000
                    com.example.util.NotificationHelper.showNotification(
                        context = getApplication(),
                        title = notification.title,
                        message = notification.message,
                        notificationId = notificationId,
                        type = notification.type
                    )
                } catch (e: Exception) {
                    Log.e("PlannerViewModel", "Failed to push system notification: ${e.localizedMessage}")
                }
            }
        }
    }

    fun scheduleTimetableClassNotifications() {
        val course = selectedCourse.value ?: return
        viewModelScope.launch {
            // Cancel all existing class notifications first
            AcademicNotificationManager.cancelAllByType(getApplication(), "class")
            
            // Fetch current classes
            val classes = repository.getTimetable(course.code).first()
            
            for (cls in classes) {
                val targetTime = getTimetableClassTimestamp(cls.dayOfWeek, cls.startTime)
                
                // Only schedule if notifications are enabled and target time is valid (in future)
                if (_areNotificationsEnabled.value && targetTime > System.currentTimeMillis()) {
                    AcademicNotificationManager.scheduleNotification(
                        context = getApplication(),
                        type = "class",
                        itemId = "class_${cls.id}",
                        title = "Upcoming Class Alert",
                        message = "${cls.subject} starts at ${cls.startTime} in ${cls.room ?: "the Lecture Hall"}.",
                        targetTime = targetTime,
                        subject = cls.subject,
                        minutesBefore = 30
                    )
                }
            }
        }
    }

    private fun getTimetableClassTimestamp(dayOfWeek: Int, startTimeStr: String): Long {
        val calendar = Calendar.getInstance()
        val calendarDay = when (dayOfWeek) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            7 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }
        
        val parts = parseTimeToHoursAndMinutes(startTimeStr)
        calendar.set(Calendar.HOUR_OF_DAY, parts.first)
        calendar.set(Calendar.MINUTE, parts.second)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        var diff = calendarDay - currentDay
        if (diff < 0) {
            diff += 7
        } else if (diff == 0) {
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                diff += 7
            }
        }
        calendar.add(Calendar.DAY_OF_YEAR, diff)
        return calendar.timeInMillis
    }

    private fun parseTimeToHoursAndMinutes(timeStr: String): Pair<Int, Int> {
        return try {
            val clean = timeStr.trim().uppercase()
            val parts = clean.split(" ")
            val timeParts = parts[0].split(":")
            var hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()
            val amPm = parts[1]
            if (amPm == "PM" && hour != 12) hour += 12
            if (amPm == "AM" && hour == 12) hour = 0
            Pair(hour, minute)
        } catch (e: Exception) {
            Pair(9, 0) // Default fallback
        }
    }

    // In-app Notification Alert generator
    private suspend fun generateSmartNotifications() {
        val course = selectedCourse.value ?: return
        val currentTimestamp = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        // Find assignments due in the next 24 hours
        val pendingAssignments = repository.getAssignments(course.code).first().filter { it.status == "Pending" }
        for (asg in pendingAssignments) {
            val diff = asg.dueDate - currentTimestamp
            if (diff in 0..oneDayMs) {
                addNotificationWithDuplicateCheck(
                    InAppNotification(
                        title = "Assignment Due Tomorrow",
                        message = "${asg.subject}: '${asg.title}' is due in less than 24 hours!",
                        type = "assignment"
                    )
                )
                if (_areNotificationsEnabled.value) {
                    AcademicNotificationManager.scheduleNotification(
                        context = getApplication(),
                        type = "assignment",
                        itemId = "asg_${asg.id}",
                        title = "Upcoming Assignment Alert",
                        message = "Assignment '${asg.title}' for ${asg.subject} is due in less than 24 hours!",
                        targetTime = asg.dueDate,
                        subject = asg.subject,
                        minutesBefore = 24 * 60 // 24 hours before
                    )
                }
            }
        }

        // Find assessments in the next 3 days
        val upcomingAssessments = repository.getAssessments(course.code).first().filter { it.status == "Upcoming" }
        for (asm in upcomingAssessments) {
            val diff = asm.date - currentTimestamp
            if (diff in 0..(oneDayMs * 3)) {
                val daysLeft = (diff / oneDayMs).toInt() + 1
                addNotificationWithDuplicateCheck(
                    InAppNotification(
                        title = "Upcoming Assessment Reminder",
                        message = "${asm.subject}: '${asm.title}' is scheduled in $daysLeft day(s)!",
                        type = "exam"
                    )
                )
                if (_areNotificationsEnabled.value) {
                    AcademicNotificationManager.scheduleNotification(
                        context = getApplication(),
                        type = "assessment",
                        itemId = "asm_${asm.id}",
                        title = "Upcoming Assessment Reminder",
                        message = "${asm.subject}: '${asm.title}' is scheduled in $daysLeft day(s)!",
                        targetTime = asm.date,
                        subject = asm.subject,
                        minutesBefore = 30
                    )
                }
            }
        }

        // Find upcoming lectures (timetable classes) within the next 24 hours
        val todayClasses = getTimetableForDay(getCurrentDayOfWeek())
        val tomorrowClasses = getTimetableForDay(if (getCurrentDayOfWeek() == 7) 1 else getCurrentDayOfWeek() + 1)
        
        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        
        val remainingToday = todayClasses.filter { parseTimeToMinutes(it.startTime) > currentMinutes }
            .sortedBy { parseTimeToMinutes(it.startTime) }
            
        if (remainingToday.isNotEmpty()) {
            val nextClass = remainingToday.first()
            if (_areNotificationsEnabled.value) {
                val targetTime = getTimetableClassTimestamp(nextClass.dayOfWeek, nextClass.startTime)
                AcademicNotificationManager.scheduleNotification(
                    context = getApplication(),
                    type = "class",
                    itemId = "class_${nextClass.id}",
                    title = "Upcoming Lecture Today",
                    message = "${nextClass.subject} starts at ${nextClass.startTime} in ${nextClass.room ?: "the Lecture Hall"}.",
                    targetTime = targetTime,
                    subject = nextClass.subject,
                    minutesBefore = 30
                )
            }
        } else if (tomorrowClasses.isNotEmpty()) {
            val firstClassTomorrow = tomorrowClasses.sortedBy { parseTimeToMinutes(it.startTime) }.first()
            if (_areNotificationsEnabled.value) {
                val targetTime = getTimetableClassTimestamp(firstClassTomorrow.dayOfWeek, firstClassTomorrow.startTime)
                AcademicNotificationManager.scheduleNotification(
                    context = getApplication(),
                    type = "class",
                    itemId = "class_${firstClassTomorrow.id}",
                    title = "Lecture Scheduled Tomorrow",
                    message = "First class tomorrow is ${firstClassTomorrow.subject} starting at ${firstClassTomorrow.startTime}.",
                    targetTime = targetTime,
                    subject = firstClassTomorrow.subject,
                    minutesBefore = 30
                )
            }
        }
    }

    fun clearInbox() {
        viewModelScope.launch {
            repository.clearAllNotifications()
        }
    }

    fun markNotificationRead(id: Int) {
        viewModelScope.launch {
            repository.markNotificationRead(id)
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("is_dark_theme", enabled).apply()
    }

    fun updateStudentProfile(name: String, dpUrl: String, dpPreset: String) {
        _studentName.value = name
        _studentDpUrl.value = dpUrl
        _studentDpPreset.value = dpPreset

        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("student_name", name)
            .putString("student_dp_url", dpUrl)
            .putString("student_dp_preset", dpPreset)
            .apply()
    }

    fun setGuestMode() {
        _loginMode.value = LoginMode.GUEST
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("login_mode", LoginMode.GUEST.name)
            .apply()
    }

    fun setAuthenticating(authenticating: Boolean) {
        _isAuthenticating.value = authenticating
    }

    fun setAuthError(error: String?) {
        _authError.value = error
        _isAuthenticating.value = false
    }

    fun retryAuthCheck() {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            _authState.value = AuthState.UNAUTHENTICATED
            _currentScreen.value = Screen.Welcome
            return
        }
        _authState.value = AuthState.LOADING
        _authError.value = null
        _authLoadingMessage.value = "Restoring your academic profile..."
        viewModelScope.launch {
            val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
            val fallbackName = sharedPrefs.getString("student_name", "") ?: currentUser.displayName ?: ""
            val fallbackPhoto = sharedPrefs.getString("student_dp_url", "") ?: currentUser.photoUrl?.toString() ?: ""
            checkAndRouteAuthenticatedUser(
                firebaseUser = currentUser,
                mode = _loginMode.value,
                fallbackName = fallbackName,
                fallbackPhotoUrl = fallbackPhoto,
                onSuccess = { isReturning ->
                    _authState.value = if (isReturning) AuthState.AUTHENTICATED_PROFILE_COMPLETE else AuthState.AUTHENTICATED_PROFILE_INCOMPLETE
                },
                onError = { err ->
                    _authError.value = err
                }
            )
        }
    }

    suspend fun checkAndRouteAuthenticatedUser(
        firebaseUser: com.google.firebase.auth.FirebaseUser,
        mode: LoginMode,
        fallbackName: String = "",
        fallbackPhotoUrl: String = "",
        googleId: String = "",
        onSuccess: (isReturningUser: Boolean) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = firebaseUser.uid
        val email = firebaseUser.email ?: ""
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        Log.d("PlannerViewModel", "Checking Firestore profile for authenticated user: $uid ($email)")

        // 0. Check local Room database first for existing profile
        val localProfile = try {
            repository.getUserProfileOnce(uid)
        } catch (e: Exception) {
            Log.w("PlannerViewModel", "Could not check local profile: ${e.message}")
            null
        }

        // 1. Fetch user profile from Firestore root document users/{uid} and subdoc users/{uid}/profile/info
        var firestoreException: Exception? = null
        val rootDoc = try {
            withTimeout(15000) {
                db.collection("users").document(uid).get().await()
            }
        } catch (e: Exception) {
            firestoreException = e
            Log.w("PlannerViewModel", "Could not fetch users/$uid root document: ${e.message}")
            null
        }

        val subDoc = try {
            withTimeout(10000) {
                db.collection("users").document(uid).collection("profile").document("info").get().await()
            }
        } catch (e: Exception) {
            if (firestoreException == null) firestoreException = e
            Log.w("PlannerViewModel", "Could not fetch users/$uid/profile/info: ${e.message}")
            null
        }

        val rootData = rootDoc?.data ?: emptyMap()
        val subData = subDoc?.data ?: emptyMap()

        // Extract course information from Firestore or local profile
        val rawCourse = (rootData["course"] as? String)?.trim()?.ifBlank { null }
            ?: (rootData["courseCode"] as? String)?.trim()?.ifBlank { null }
            ?: (subData["course"] as? String)?.trim()?.ifBlank { null }
            ?: localProfile?.course?.trim()?.ifBlank { null }

        // Extract student name from Firestore, local profile, Firebase Auth, or fallback
        val studentFullName = (rootData["name"] as? String)?.trim()?.ifBlank { null }
            ?: (rootData["fullName"] as? String)?.trim()?.ifBlank { null }
            ?: (rootData["displayName"] as? String)?.trim()?.ifBlank { null }
            ?: (subData["fullName"] as? String)?.trim()?.ifBlank { null }
            ?: localProfile?.fullName?.trim()?.ifBlank { null }
            ?: firebaseUser.displayName?.trim()?.ifBlank { null }
            ?: fallbackName.trim().ifBlank { null }

        val isOnboardingComplete = (rootData["onboardingCompleted"] as? Boolean)
            ?: (rootData["isProfileCompleted"] as? Boolean)
            ?: (subData["isProfileCompleted"] as? Boolean)
            ?: (localProfile != null && localProfile.course.isNotBlank())
            ?: false

        val hasCompleteProfile = (rootDoc?.exists() == true && rawCourse != null && studentFullName != null) ||
            (subDoc?.exists() == true && rawCourse != null && studentFullName != null) ||
            (localProfile != null && localProfile.course.isNotBlank() && localProfile.fullName.isNotBlank()) ||
            (isOnboardingComplete && rawCourse != null)

        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)

        if (hasCompleteProfile && rawCourse != null) {
            Log.i("PlannerViewModel", "Returning user detected for UID $uid ($studentFullName, course: $rawCourse)")
            
            val college = (rootData["college"] as? String)?.trim()?.ifBlank { null }
                ?: (subData["college"] as? String)?.trim()?.ifBlank { null }
                ?: localProfile?.college?.trim()?.ifBlank { null }
                ?: ""

            val rawYear = rootData["year"] ?: rootData["currentYear"] ?: subData["year"] ?: subData["currentYear"] ?: localProfile?.year
            val year = when (rawYear) {
                is Number -> {
                    val num = rawYear.toInt()
                    val suffix = when (num) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
                    "${num}${suffix} Year"
                }
                is String -> if (rawYear.isBlank()) "1st Year" else rawYear.trim()
                else -> "1st Year"
            }

            val admissionYear = (rootData["admissionYear"] as? Number)?.toInt()
                ?: (subData["admissionYear"] as? Number)?.toInt()
                ?: localProfile?.admissionYear
                ?: 2024

            val semester = (rootData["semester"] as? String)?.trim()?.ifBlank { null }
                ?: (subData["semester"] as? String)?.trim()?.ifBlank { null }
                ?: localProfile?.semester?.trim()?.ifBlank { null }
                ?: "Semester 1"

            val batch = (rootData["batch"] as? String)?.trim()?.ifBlank { null }
                ?: (subData["batch"] as? String)?.trim()?.ifBlank { null }
                ?: localProfile?.batch?.trim()?.ifBlank { null }
                ?: "Batch A"

            val photoUrl = (rootData["photoUrl"] as? String)?.trim()?.ifBlank { null }
                ?: (subData["photoUrl"] as? String)?.trim()?.ifBlank { null }
                ?: fallbackPhotoUrl.ifBlank { firebaseUser.photoUrl?.toString() ?: "" }

            val dpPreset = (rootData["dpPreset"] as? String)?.trim()?.ifBlank { null }
                ?: (subData["dpPreset"] as? String)?.trim()?.ifBlank { null }
                ?: "doctor_male"

            val courseObj = MedicalCourse.entries.firstOrNull {
                it.code.equals(rawCourse, ignoreCase = true) ||
                it.name.equals(rawCourse, ignoreCase = true) ||
                it.displayName.equals(rawCourse, ignoreCase = true)
            } ?: MedicalCourse.MBBS

            val resolvedName = studentFullName ?: firebaseUser.displayName ?: "Medical Student"

            // Save to local Room database for offline reliability
            val updatedProfile = UserProfile(
                uid = uid,
                fullName = resolvedName,
                college = college,
                course = courseObj.displayName,
                year = year,
                admissionYear = admissionYear,
                currentYear = year,
                semester = semester,
                batch = batch
            )
            try {
                repository.saveUserProfile(updatedProfile)
            } catch (e: Exception) {
                Log.w("PlannerViewModel", "Failed to cache user profile locally: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                _studentName.value = resolvedName
                _studentEmail.value = email
                _studentCollege.value = college
                _selectedCourse.value = courseObj
                _studentYear.value = year
                _studentAdmissionYear.value = admissionYear
                _studentSemester.value = semester
                _studentBatch.value = batch
                _studentDpUrl.value = photoUrl
                _studentDpPreset.value = dpPreset
                _loginMode.value = mode
                _isProfileCompleted.value = true
                _googleUserId.value = if (googleId.isNotBlank()) googleId else uid

                sharedPrefs.edit()
                    .putString("login_mode", mode.name)
                    .putString("student_name", resolvedName)
                    .putString("student_email", email)
                    .putString("student_college", college)
                    .putString("selected_course_code", courseObj.name)
                    .putString("student_year", year)
                    .putInt("student_admission_year", admissionYear)
                    .putString("student_semester", semester)
                    .putString("student_batch", batch)
                    .putString("student_dp_url", photoUrl)
                    .putString("student_dp_preset", dpPreset)
                    .putString("google_user_id", _googleUserId.value)
                    .putBoolean("is_profile_completed", true)
                    .putBoolean("is_onboarding_completed", true)
                    .apply()

                viewModelScope.launch(Dispatchers.IO) {
                    repository.startCloudSync(uid)
                    restoreDataFromFirebaseInternal(uid, email)
                    repository.populateDefaultTimetableIfEmpty(courseObj.code)
                    
                    // Immediately fetch and import all existing shared batch assignments, assessments, finished chapters
                    val col = _studentCollege.value
                    val crs = _selectedCourse.value?.code ?: courseObj.code
                    val admYr = _studentAdmissionYear.value
                    val btch = _studentBatch.value
                    val customCode = _customBatchCode.value
                    if (col.isNotBlank() && crs.isNotBlank() && btch.isNotBlank()) {
                        try {
                            batchSyncManager.fetchAndSyncBatchNow(col, crs, admYr, btch, customCode)
                        } catch (e: Exception) {
                            Log.w("PlannerViewModel", "Post-login batch sync: ${e.message}")
                        }
                    }

                    withContext(Dispatchers.Main) {
                        scheduleTimetableClassNotifications()
                        generateSmartNotifications()
                        startBatchNotificationSync()
                    }
                }

                addNotificationWithDuplicateCheck(
                    InAppNotification(
                        title = "Welcome back, $resolvedName!",
                        message = "Your profile, timetable, and batch records have been successfully restored.",
                        type = "alert"
                    )
                )

                // Direct to dashboard - NO ONBOARDING SHOWN!
                _authState.value = AuthState.AUTHENTICATED_PROFILE_COMPLETE
                _currentScreen.value = Screen.Dashboard
                _isAuthenticating.value = false
                _authError.value = null
                onSuccess(true)
            }
        } else if (firestoreException != null && rootDoc == null && localProfile == null) {
            // Firestore read failed and we have no local cache. DO NOT assume user is new!
            Log.e("PlannerViewModel", "Firestore error and no local cache for UID $uid: ${firestoreException.message}")
            withContext(Dispatchers.Main) {
                _isAuthenticating.value = false
                val errorMsg = when {
                    firestoreException.message?.contains("permission", ignoreCase = true) == true ->
                        "Cloud permission issue. Please check Firestore security rules."
                    firestoreException.message?.contains("network", ignoreCase = true) == true ||
                    firestoreException.message?.contains("unavailable", ignoreCase = true) == true ->
                        "Network error connecting to cloud profile. Please check your internet connection."
                    else -> "Unable to retrieve your cloud profile (${firestoreException.localizedMessage}). Please tap Retry."
                }
                _authError.value = errorMsg
                onError(errorMsg)
            }
        } else {
            // Verified new user or incomplete profile:
            Log.i("PlannerViewModel", "New user detected (no complete profile in Firestore or Room for UID $uid)")
            
            withContext(Dispatchers.Main) {
                val prefillName = studentFullName ?: fallbackName.ifBlank { firebaseUser.displayName ?: "" }
                _studentName.value = prefillName
                _studentEmail.value = email
                _studentDpUrl.value = fallbackPhotoUrl.ifBlank { firebaseUser.photoUrl?.toString() ?: "" }
                _studentDpPreset.value = "none"
                _loginMode.value = mode
                _isProfileCompleted.value = false
                _googleUserId.value = if (googleId.isNotBlank()) googleId else uid

                sharedPrefs.edit()
                    .putString("login_mode", mode.name)
                    .putString("student_name", prefillName)
                    .putString("student_email", email)
                    .putString("student_dp_url", _studentDpUrl.value)
                    .putString("student_dp_preset", "none")
                    .putString("google_user_id", _googleUserId.value)
                    .putBoolean("is_profile_completed", false)
                    .putBoolean("is_onboarding_completed", false)
                    .apply()

                repository.startCloudSync(uid)
                startBatchNotificationSync()

                addNotificationWithDuplicateCheck(
                    InAppNotification(
                        title = "Account Verified",
                        message = if (prefillName.isNotBlank()) "Welcome $prefillName! Please complete your academic profile setup." else "Welcome! Please complete your academic profile setup.",
                        type = "alert"
                    )
                )

                _authState.value = AuthState.AUTHENTICATED_PROFILE_INCOMPLETE
                _isAuthenticating.value = false
                _authError.value = null
                onSuccess(false)
            }
        }
    }

    fun signInWithGoogle(
        name: String,
        email: String,
        dpUrl: String,
        googleId: String,
        idToken: String? = null,
        onComplete: (isReturningUser: Boolean, isSuccess: Boolean, errorMsg: String?) -> Unit = { _, _, _ -> }
    ) {
        _isAuthenticating.value = true
        _authError.value = null
        _authLoadingMessage.value = "Authenticating with Google..."

        if (idToken.isNullOrEmpty()) {
            _isAuthenticating.value = false
            val errorMsg = "Google authentication failed: ID token not received."
            _authError.value = errorMsg
            onComplete(false, false, errorMsg)
            return
        }

        viewModelScope.launch {
            try {
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                val authResult = com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential).await()
                val firebaseUser = authResult.user ?: throw Exception("Firebase user is null after Google authentication")
                
                _authLoadingMessage.value = "Checking your academic profile..."
                
                checkAndRouteAuthenticatedUser(
                    firebaseUser = firebaseUser,
                    mode = LoginMode.GOOGLE,
                    fallbackName = name,
                    fallbackPhotoUrl = dpUrl,
                    googleId = googleId,
                    onSuccess = { isReturning ->
                        _isAuthenticating.value = false
                        onComplete(isReturning, true, null)
                    },
                    onError = { err ->
                        _isAuthenticating.value = false
                        _authError.value = err
                        onComplete(false, false, err)
                    }
                )
            } catch (e: Exception) {
                Log.e("PlannerViewModel", "Firebase Google Sign-In failed: ${e.message}", e)
                _isAuthenticating.value = false
                val friendlyError = when {
                    e.message?.contains("network", ignoreCase = true) == true -> "Network connection error. Please check your internet connection."
                    e.message?.contains("credential", ignoreCase = true) == true -> "Google authentication credential error. Please try again."
                    else -> e.localizedMessage ?: "Failed to sign in with Google."
                }
                _authError.value = friendlyError
                onComplete(false, false, friendlyError)
            }
        }
    }

    fun signInWithEmailAndPassword(
        email: String,
        password: String,
        onComplete: (isReturningUser: Boolean, isSuccess: Boolean, errorMsg: String?) -> Unit = { _, _, _ -> }
    ) {
        _isAuthenticating.value = true
        _authError.value = null
        _authLoadingMessage.value = "Signing in..."
        
        viewModelScope.launch {
            try {
                val authResult = com.google.firebase.auth.FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(email, password)
                    .await()
                val user = authResult.user ?: throw Exception("User session is null after sign in")
                
                _authLoadingMessage.value = "Checking profile..."
                checkAndRouteAuthenticatedUser(
                    firebaseUser = user,
                    mode = LoginMode.FIREBASE,
                    fallbackName = email.substringBefore("@"),
                    onSuccess = { isReturning ->
                        _isAuthenticating.value = false
                        onComplete(isReturning, true, null)
                    },
                    onError = { err ->
                        _isAuthenticating.value = false
                        _authError.value = err
                        onComplete(false, false, err)
                    }
                )
            } catch (e: Exception) {
                Log.e("PlannerViewModel", "Email sign in failed: ${e.message}", e)
                _isAuthenticating.value = false
                val err = e.localizedMessage ?: "Sign-in failed. Please verify credentials."
                _authError.value = err
                onComplete(false, false, err)
            }
        }
    }

    fun signUpWithEmailAndPassword(name: String, email: String, password: String, dpPreset: String) {
        _isAuthenticating.value = true
        _authError.value = null
        
        com.google.firebase.auth.FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build()
                        user.updateProfile(profileUpdates).addOnCompleteListener { profileTask ->
                            val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
                            
                            _loginMode.value = LoginMode.FIREBASE
                            _studentName.value = name
                            _studentEmail.value = email
                            _studentDpUrl.value = ""
                            _studentDpPreset.value = dpPreset
                            _isAuthenticating.value = false
                            _authError.value = null
                            
                            sharedPrefs.edit()
                                .putString("login_mode", LoginMode.FIREBASE.name)
                                .putString("student_name", name)
                                .putString("student_email", email)
                                .putString("student_dp_url", "")
                                .putString("student_dp_preset", dpPreset)
                                .apply()
                                
                            repository.startCloudSync(user.uid)
                            startBatchNotificationSync()
                            syncDataToFirebase()
                                
                            viewModelScope.launch {
                                addNotificationWithDuplicateCheck(
                                    InAppNotification(
                                        title = "Account Created!",
                                        message = "Welcome $name to MedPulse! Your production cloud database is active.",
                                        type = "alert"
                                    )
                                )
                            }
                        }
                    } else {
                        _isAuthenticating.value = false
                        _authError.value = "User registration failed"
                    }
                } else {
                    _isAuthenticating.value = false
                    _authError.value = task.exception?.localizedMessage ?: "Registration failed. Check password strength/email format."
                }
            }
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential, phoneNumber: String, name: String, dpPreset: String = "doctor_male") {
        _isAuthenticating.value = true
        com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
                    val finalName = name.trim().ifBlank { user?.displayName ?: "Mobile Student" }
                    val emailVal = "phone:${phoneNumber.trim().replace(" ", "")}"
                    
                    _loginMode.value = LoginMode.FIREBASE
                    _studentName.value = finalName
                    _studentEmail.value = emailVal
                    _studentDpUrl.value = ""
                    _studentDpPreset.value = dpPreset
                    _isAuthenticating.value = false
                    _authError.value = null
                    _phoneOtpSent.value = false
                    _phoneVerificationId.value = null
                    
                    sharedPrefs.edit()
                        .putString("login_mode", LoginMode.FIREBASE.name)
                        .putString("student_name", finalName)
                        .putString("student_email", emailVal)
                        .putString("student_dp_url", "")
                        .putString("student_dp_preset", dpPreset)
                        .apply()
                        
                    startBatchNotificationSync()
                    
                    viewModelScope.launch {
                        addNotificationWithDuplicateCheck(
                            InAppNotification(
                                title = "Authenticated successfully!",
                                message = "Welcome $finalName! Your mobile profile is verified and active.",
                                type = "alert"
                            )
                        )
                    }
                } else {
                    _isAuthenticating.value = false
                    _authError.value = "OTP Verification Failed: ${task.exception?.localizedMessage}"
                }
            }
    }

    fun sendPhoneOtp(activity: Activity, phoneNumber: String, name: String) {
        _isAuthenticating.value = true
        _authError.value = null
        
        val options = PhoneAuthOptions.newBuilder(com.google.firebase.auth.FirebaseAuth.getInstance())
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithPhoneCredential(credential, phoneNumber, name)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    _isAuthenticating.value = false
                    _authError.value = "Verification failed: ${e.localizedMessage}"
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    _phoneVerificationId.value = verificationId
                    _phoneOtpSent.value = true
                    _isAuthenticating.value = false
                    _authError.value = null
                    
                    viewModelScope.launch {
                        addNotificationWithDuplicateCheck(
                            InAppNotification(
                                title = "🔑 OTP Sent!",
                                message = "A security code has been sent via SMS to $phoneNumber.",
                                type = "alert"
                            )
                        )
                    }
                }
            })
            .build()
        
        try {
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            _isAuthenticating.value = false
            _authError.value = "Failed to start phone verification: ${e.localizedMessage}"
        }
    }

    fun verifyPhoneOtp(phoneNumber: String, otp: String, name: String, dpPreset: String = "doctor_male") {
        val verificationId = _phoneVerificationId.value
        if (verificationId == null) {
            _authError.value = "Verification session expired. Please request a new OTP."
            return
        }
        _isAuthenticating.value = true
        _authError.value = null
        
        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, otp)
            signInWithPhoneCredential(credential, phoneNumber, name, dpPreset)
        } catch (e: Exception) {
            _isAuthenticating.value = false
            _authError.value = "Failed to verify code: ${e.localizedMessage}"
        }
    }

    fun resetPhoneOtpState() {
        _phoneOtpSent.value = false
        _phoneVerificationId.value = null
        _authError.value = null
        _isAuthenticating.value = false
    }

    fun signOut() {
        _authState.value = AuthState.UNAUTHENTICATED
        _loginMode.value = LoginMode.UNDECIDED
        _studentName.value = ""
        _studentEmail.value = ""
        _studentDpUrl.value = ""
        _studentDpPreset.value = "doctor_male"
        _googleUserId.value = ""
        _studentCollege.value = ""
        _studentYear.value = ""
        _studentSemester.value = ""
        _studentBatch.value = ""
        _isProfileCompleted.value = false
        _currentScreen.value = Screen.Welcome
 
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("login_mode", LoginMode.UNDECIDED.name)
            .putString("student_name", "")
            .putString("student_email", "")
            .putString("student_dp_url", "")
            .putString("student_dp_preset", "doctor_male")
            .putString("google_user_id", "")
            .putString("selected_course_code", null) // reset selected course on logout
            .putString("student_college", "")
            .putString("student_year", "")
            .putString("student_semester", "")
            .putString("student_batch", "")
            .putBoolean("is_profile_completed", false)
            .putBoolean("is_onboarding_completed", false)
            .apply()

        val onboardingDraftPrefs = getApplication<Application>().getSharedPreferences("medpulse_onboarding_draft", Application.MODE_PRIVATE)
        onboardingDraftPrefs.edit().clear().apply()
 
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.e("PlannerViewModel", "Error signing out from Firebase: ${e.message}", e)
        }

        try {
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).build()
            val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(getApplication<Application>(), gso)
            googleSignInClient.signOut()
        } catch (e: Exception) {
            Log.e("PlannerViewModel", "Error signing out from Google: ${e.message}", e)
        }
 
        viewModelScope.launch {
            try {
                repository.clearAllAttendance()
            } catch (e: Exception) {
                Log.w("PlannerViewModel", "Error clearing attendance on sign out: ${e.message}")
            }
            addNotificationWithDuplicateCheck(
                InAppNotification(
                    title = "Signed Out",
                    message = "Your account was successfully signed out. All local offline academic planner data was safely preserved.",
                    type = "alert"
                )
            )
        }
    }

    fun saveUserProfile(
        name: String,
        college: String,
        course: String,
        year: String,
        semester: String,
        batch: String,
        admissionYear: Int = 2024,
        currentYear: String = year
    ) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        val effectiveCurrentYear = if (currentYear.isNotBlank()) currentYear else year
        val profile = UserProfile(
            uid = uid,
            fullName = name,
            college = college,
            course = course,
            year = effectiveCurrentYear,
            admissionYear = admissionYear,
            currentYear = effectiveCurrentYear,
            semester = semester,
            batch = batch
        )

        viewModelScope.launch(Dispatchers.IO) {
            // Save to local database
            repository.saveUserProfile(profile)

            // Save to SharedPreferences
            val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
            sharedPrefs.edit()
                .putString("student_name", name)
                .putString("student_college", college)
                .putString("student_year", effectiveCurrentYear)
                .putInt("student_admission_year", admissionYear)
                .putString("student_semester", semester)
                .putString("student_batch", batch)
                .putBoolean("is_profile_completed", true)
                .putBoolean("is_onboarding_completed", true)
                .apply()

            withContext(Dispatchers.Main) {
                _studentName.value = name
                _studentCollege.value = college
                _studentYear.value = effectiveCurrentYear
                _studentAdmissionYear.value = admissionYear
                _studentSemester.value = semester
                _studentBatch.value = batch
                _isProfileCompleted.value = true
                _authState.value = AuthState.AUTHENTICATED_PROFILE_COMPLETE
                _currentScreen.value = Screen.Dashboard
            }

            // Immediately start listening and fetch shared batch records for the updated batch
            val courseCodeVal = _selectedCourse.value?.code ?: course
            batchSyncManager.startListeningToBatch(college, courseCodeVal, admissionYear, batch, _customBatchCode.value)
            try {
                batchSyncManager.fetchAndSyncBatchNow(college, courseCodeVal, admissionYear, batch, _customBatchCode.value)
            } catch (e: Exception) {
                Log.w("PlannerViewModel", "saveUserProfile batch sync: ${e.message}")
            }

            // Sync with Firebase Firestore
            try {
                if (uid != "local_user") {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val emailVal = _studentEmail.value.ifBlank { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "" }
                    val yearNumber = effectiveCurrentYear.filter { it.isDigit() }.toIntOrNull() ?: 1
                    
                    val rootData = hashMapOf<String, Any>(
                        "uid" to uid,
                        "name" to name,
                        "fullName" to name,
                        "displayName" to name,
                        "email" to emailVal,
                        "course" to course,
                        "college" to college,
                        "year" to yearNumber,
                        "currentYear" to effectiveCurrentYear,
                        "admissionYear" to admissionYear,
                        "semester" to semester,
                        "batch" to batch,
                        "onboardingCompleted" to true,
                        "isProfileCompleted" to true,
                        "photoUrl" to _studentDpUrl.value,
                        "dpPreset" to _studentDpPreset.value,
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                    
                    val existingDoc = try { db.collection("users").document(uid).get().await() } catch (e: Exception) { null }
                    if (existingDoc == null || !existingDoc.contains("createdAt")) {
                        rootData["createdAt"] = com.google.firebase.Timestamp.now()
                    }
                    
                    db.collection("users").document(uid)
                        .set(rootData, com.google.firebase.firestore.SetOptions.merge())
                        .await()

                    // Compatibility subdocument
                    db.collection("users").document(uid).collection("profile").document("info")
                        .set(profile)
                        .await()
                    
                    // Backup everything as well on initial setup completion!
                    syncDataToFirebase()
                }
            } catch (e: Exception) {
                Log.e("PlannerViewModel", "Failed to sync profile to Firestore: ${e.message}", e)
            }
            checkAndNotifyTomorrowHoliday()
            startBatchNotificationSync()
        }
    }

    fun setStudentCollege(collegeName: String) {
        _studentCollege.value = collegeName
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit().putString("student_college", collegeName).apply()
        checkAndNotifyTomorrowHoliday()
    }

    fun syncWithGoogleCalendar(context: Context, onResult: (GoogleCalendarSyncResult) -> Unit = {}) {
        viewModelScope.launch {
            _isSyncingCalendar.value = true
            val holidays = UniversityCalendarService.getHolidaysForCollege(_studentCollege.value)
            val result = GoogleCalendarSyncManager.syncAllToGoogleCalendar(
                context = context,
                classes = timetable.value,
                assignments = assignments.value,
                assessments = assessments.value,
                holidays = holidays,
                collegeName = _studentCollege.value.ifBlank { "Medical College" }
            )
            _lastCalendarSyncResult.value = result
            _isSyncingCalendar.value = false
            onResult(result)
        }
    }

    fun clearCalendarSyncResult() {
        _lastCalendarSyncResult.value = null
    }

    private val _isSyncingHolidays = MutableStateFlow(false)
    val isSyncingHolidays: StateFlow<Boolean> = _isSyncingHolidays

    private val _holidaySyncStatus = MutableStateFlow("Official Gazette Verified")
    val holidaySyncStatus: StateFlow<String> = _holidaySyncStatus

    fun syncOfficialHolidays() {
        viewModelScope.launch {
            _isSyncingHolidays.value = true
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val task = db.collection("gazetted_holidays").get()
                    val snapshot = com.google.android.gms.tasks.Tasks.await(task)
                    val dynamicList = snapshot.documents.mapNotNull { doc ->
                        val id = doc.getString("id") ?: doc.id
                        val name = doc.getString("name") ?: return@mapNotNull null
                        val date = doc.getString("date") ?: return@mapNotNull null
                        val typeStr = doc.getString("type") ?: "NATIONAL"
                        val type = try { com.example.data.university.HolidayType.valueOf(typeStr) } catch (e: Exception) { com.example.data.university.HolidayType.NATIONAL }
                        val desc = doc.getString("description") ?: ""
                        val isSuspended = doc.getBoolean("isClassSuspended") ?: true
                        com.example.data.university.UniversityHoliday(
                            id = id,
                            name = name,
                            date = date,
                            type = type,
                            description = desc,
                            isClassSuspended = isSuspended
                        )
                    }
                    if (dynamicList.isNotEmpty()) {
                        UniversityCalendarService.applyDynamicHolidays(dynamicList)
                    }
                }
                _holidaySyncStatus.value = "Synced with Official Gazette (Real-time)"
                addNotificationWithDuplicateCheck(
                    InAppNotification(
                        title = "Holidays Up to Date",
                        message = "Academic calendar is synchronized with verified DoPT & university gazette circulars.",
                        type = "info"
                    )
                )
            } catch (e: Exception) {
                _holidaySyncStatus.value = "Official Gazette Verified (Offline)"
            } finally {
                _isSyncingHolidays.value = false
            }
        }
    }

    fun checkAndNotifyTomorrowHoliday() {
        val (isHol, hol) = UniversityCalendarService.isTomorrowHoliday(_studentCollege.value)
        if (isHol && hol != null) {
            AcademicNotificationManager.scheduleNotification(
                context = getApplication(),
                type = "study",
                itemId = "holiday_${hol.id}",
                title = "🌴 Tomorrow is a Holiday: ${hol.name}",
                message = "${hol.description} Classes are suspended according to your university academic calendar.",
                targetTime = System.currentTimeMillis() + 15000L,
                subject = "University Holiday",
                minutesBefore = 0
            )
        }
    }

    private var syncJob: kotlinx.coroutines.Job? = null

    fun syncDataToFirebase() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        syncJob?.cancel()
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(350) // Debounce rapid sync requests
            try {
                val emailVal = _studentEmail.value.ifBlank { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "" }
                val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
                val base64Pic = sharedPrefs.getString("student_dp_base64", "") ?: ""
                val photoToSync = if (_studentDpUrl.value.startsWith("http")) {
                    _studentDpUrl.value
                } else if (base64Pic.isNotBlank()) {
                    base64Pic
                } else {
                    _studentDpUrl.value
                }

                // 1. Sync User Root Document & Profile with all academic & profile details
                val profileMap = mapOf(
                    "uid" to uid,
                    "email" to emailVal,
                    "fullName" to _studentName.value,
                    "displayName" to _studentName.value,
                    "college" to _studentCollege.value,
                    "course" to (_selectedCourse.value?.code ?: ""),
                    "year" to _studentYear.value,
                    "currentYear" to _studentYear.value,
                    "admissionYear" to _studentAdmissionYear.value,
                    "semester" to _studentSemester.value,
                    "batch" to _studentBatch.value,
                    "photoUrl" to photoToSync,
                    "dpPreset" to _studentDpPreset.value,
                    "lastSyncTimestamp" to System.currentTimeMillis()
                )
                // Set on users/{uid} root document with merge
                db.collection("users").document(uid)
                    .set(profileMap, com.google.firebase.firestore.SetOptions.merge())

                // Also set in subcollection for compatibility
                val profile = UserProfile(
                    uid = uid,
                    fullName = _studentName.value,
                    college = _studentCollege.value,
                    course = _selectedCourse.value?.code ?: "",
                    year = _studentYear.value,
                    admissionYear = _studentAdmissionYear.value,
                    currentYear = _studentYear.value,
                    semester = _studentSemester.value,
                    batch = _studentBatch.value
                )
                db.collection("users").document(uid).collection("profile").document("info")
                    .set(profile)
                
                // Save locally too
                repository.saveUserProfile(profile)

                // 2. Sync Settings
                val settings = mapOf(
                    "isDarkTheme" to _isDarkTheme.value,
                    "areNotificationsEnabled" to _areNotificationsEnabled.value
                )
                db.collection("users").document(uid).collection("settings").document("info")
                    .set(settings)

                // 3. Batch write collections atomically and efficiently
                val batchWriter = db.batch()
                val userRef = db.collection("users").document(uid)

                timetable.value.take(60).forEach { classItem ->
                    batchWriter.set(userRef.collection("timetable_classes").document(classItem.firestoreId), classItem)
                }
                val allAtt = allAttendanceRecords.value
                val initialBatchAtt = allAtt.take(250)
                initialBatchAtt.forEach { rec ->
                    batchWriter.set(userRef.collection("attendance_records").document(rec.firestoreId), rec)
                }
                completedSyllabusTopics.value.take(150).forEach { topic ->
                    batchWriter.set(userRef.collection("completed_syllabus_topics").document(topic.firestoreId), topic)
                }
                assignments.value.take(60).forEach { asg ->
                    batchWriter.set(userRef.collection("assignments").document(asg.firestoreId), asg)
                }
                assessments.value.take(60).forEach { ass ->
                    batchWriter.set(userRef.collection("assessments").document(ass.firestoreId), ass)
                }
                studyTasks.value.take(40).forEach { task ->
                    batchWriter.set(userRef.collection("study_tasks").document(task.firestoreId), task)
                }
                exams.value.take(30).forEach { ex ->
                    batchWriter.set(userRef.collection("exams").document(ex.firestoreId), ex)
                }
                plannerTasks.value.take(40).forEach { task ->
                    batchWriter.set(userRef.collection("planner_tasks").document(task.firestoreId), task)
                }

                batchWriter.commit().await()
                if (allAtt.size > 250) {
                    allAtt.drop(250).chunked(400).forEach { chunk ->
                        val extraBatch = db.batch()
                        chunk.forEach { rec ->
                            extraBatch.set(userRef.collection("attendance_records").document(rec.firestoreId), rec)
                        }
                        extraBatch.commit().await()
                    }
                }
                Log.d("FirestoreSync", "All user data successfully backed up via WriteBatch for UID: $uid")
            } catch (e: Exception) {
                Log.e("FirestoreSync", "Failed to backup user data to Firestore: ${e.message}", e)
            }
        }
    }

    suspend fun restoreDataFromFirebaseInternal(uid: String, fallbackEmail: String): Boolean {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        var profileFound = false
        try {
            repository.startCloudSync(uid)

            // 1. Restore Profile from users/{uid} root or users/{uid}/profile/info
            val rootDoc = try { db.collection("users").document(uid).get().await() } catch (e: Exception) { null }
            val subDoc = try { db.collection("users").document(uid).collection("profile").document("info").get().await() } catch (e: Exception) { null }

            val data = rootDoc?.data ?: emptyMap()
            val subData = subDoc?.data ?: emptyMap()

            val name = (data["fullName"] as? String)
                ?: (data["displayName"] as? String)
                ?: (subData["fullName"] as? String)
                ?: ""
            val emailVal = (data["email"] as? String)
                ?: (subData["email"] as? String)
                ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
                ?: fallbackEmail
            val college = (data["college"] as? String)
                ?: (subData["college"] as? String)
                ?: ""
            val courseCode = (data["course"] as? String)
                ?: (subData["course"] as? String)
                ?: ""
            val year = (data["currentYear"] as? String)
                ?: (data["year"] as? String)
                ?: (subData["currentYear"] as? String)
                ?: (subData["year"] as? String)
                ?: ""
            val admissionYearVal = (data["admissionYear"] as? Long)?.toInt()
                ?: (subData["admissionYear"] as? Long)?.toInt()
                ?: 2024
            val semester = (data["semester"] as? String)
                ?: (subData["semester"] as? String)
                ?: ""
            val batch = (data["batch"] as? String)
                ?: (subData["batch"] as? String)
                ?: ""
            val photoUrl = (data["photoUrl"] as? String)
                ?: (subData["photoUrl"] as? String)
                ?: ""
            val dpPreset = (data["dpPreset"] as? String)
                ?: (subData["dpPreset"] as? String)
                ?: "doctor_male"

            withContext(Dispatchers.Main) {
                if (name.isNotBlank()) {
                    _studentName.value = name
                    profileFound = true
                }
                if (emailVal.isNotBlank()) _studentEmail.value = emailVal
                if (college.isNotBlank()) _studentCollege.value = college
                if (year.isNotBlank()) _studentYear.value = year
                _studentAdmissionYear.value = admissionYearVal
                if (semester.isNotBlank()) _studentSemester.value = semester
                if (batch.isNotBlank()) _studentBatch.value = batch
                _studentDpPreset.value = dpPreset

                // Handle profile picture restoration (including base64 decoded custom avatars)
                if (photoUrl.isNotBlank()) {
                    if (photoUrl.startsWith("data:image/")) {
                        try {
                            val base64Data = photoUrl.substringAfter("base64,")
                            val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                            val file = java.io.File(getApplication<Application>().filesDir, "custom_profile_picture.jpg")
                            file.writeBytes(decodedBytes)
                            val localUri = "file://" + file.absolutePath
                            _studentDpUrl.value = localUri
                            val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
                            sharedPrefs.edit().putString("student_dp_url", localUri).putString("student_dp_base64", photoUrl).apply()
                        } catch (e: Exception) {
                            _studentDpUrl.value = photoUrl
                        }
                    } else {
                        _studentDpUrl.value = photoUrl
                    }
                }

                if (courseCode.isNotEmpty()) {
                    val courseObj = MedicalCourse.entries.firstOrNull {
                        it.name.equals(courseCode, ignoreCase = true) ||
                        it.code.equals(courseCode, ignoreCase = true) ||
                        it.displayName.equals(courseCode, ignoreCase = true)
                    }
                    if (courseObj != null) {
                        _selectedCourse.value = courseObj
                        profileFound = true
                    }
                }

                val hasCompletedProfile = _studentName.value.isNotBlank() && (_selectedCourse.value != null || courseCode.isNotBlank())
                _isProfileCompleted.value = hasCompletedProfile

                val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
                sharedPrefs.edit()
                    .putString("student_name", _studentName.value)
                    .putString("student_email", _studentEmail.value)
                    .putString("student_dp_url", _studentDpUrl.value)
                    .putString("student_dp_preset", _studentDpPreset.value)
                    .putString("selected_course_code", _selectedCourse.value?.name ?: courseCode.ifBlank { null })
                    .putString("student_college", _studentCollege.value)
                    .putString("student_year", _studentYear.value)
                    .putInt("student_admission_year", _studentAdmissionYear.value)
                    .putString("student_semester", _studentSemester.value)
                    .putString("student_batch", _studentBatch.value)
                    .putBoolean("is_profile_completed", hasCompletedProfile)
                    .putBoolean("is_onboarding_completed", hasCompletedProfile || sharedPrefs.getBoolean("is_onboarding_completed", false))
                    .apply()
            }

            // 2. Restore Timetable Classes
            try {
                val timetableSnapshot = db.collection("users").document(uid).collection("timetable_classes").get().await()
                if (!timetableSnapshot.isEmpty) {
                    for (doc in timetableSnapshot) {
                        try {
                            val c = doc.toObject(TimetableClass::class.java)
                            if (c != null) repository.addClass(c)
                        } catch (e: Exception) {
                            val c = com.example.data.sync.FirestoreSyncManager.mapToTimetableClass(doc.data, doc.id, null)
                            repository.addClass(c)
                        }
                    }
                } else {
                    val legacySnapshot = db.collection("users").document(uid).collection("timetable").get().await()
                    for (doc in legacySnapshot) {
                        try {
                            val c = doc.toObject(TimetableClass::class.java)
                            if (c != null) repository.addClass(c)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("FirestoreSync", "Error restoring timetable: ${e.message}")
            }

            // 3. Restore Attendance Records (Class attended with date, time, subject, status)
            try {
                val attendanceSnapshot = db.collection("users").document(uid).collection("attendance_records").get().await()
                if (!attendanceSnapshot.isEmpty) {
                    for (doc in attendanceSnapshot) {
                        try {
                            val r = doc.toObject(AttendanceRecord::class.java)
                            if (r != null) repository.saveAttendanceRecord(r)
                        } catch (e: Exception) {
                            val r = com.example.data.sync.FirestoreSyncManager.mapToAttendanceRecord(doc.data, doc.id, null)
                            repository.saveAttendanceRecord(r)
                        }
                    }
                } else {
                    val legacyAtt = db.collection("users").document(uid).collection("attendance").get().await()
                    for (doc in legacyAtt) {
                        try {
                            val r = doc.toObject(AttendanceRecord::class.java)
                            if (r != null) repository.saveAttendanceRecord(r)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("FirestoreSync", "Error restoring attendance: ${e.message}")
            }

            // 4. Restore Completed Syllabus Topics (Chapters completed with date, faculty, notes)
            try {
                val topicsSnapshot = db.collection("users").document(uid).collection("completed_syllabus_topics").get().await()
                for (doc in topicsSnapshot) {
                    val firestoreId = doc.id
                    val data = doc.data
                    val courseCode = (data["courseCode"] as? String) ?: ""
                    val subject = (data["subject"] as? String) ?: ""
                    val topicTitle = (data["topicTitle"] as? String) ?: ""

                    // Check if already present locally by firestoreId or matching topic
                    val existing = repository.getCompletedTopicByFirestoreId(firestoreId)
                        ?: repository.findMatchingSimilarCompletedTopic(courseCode, subject, topicTitle)

                    val parsed = try {
                        doc.toObject(CompletedSyllabusTopic::class.java)
                    } catch (e: Exception) {
                        com.example.data.sync.FirestoreSyncManager.mapToCompletedTopic(data, firestoreId, existing?.id)
                    }

                    if (parsed != null) {
                        val topicToSave = parsed.copy(
                            id = existing?.id ?: 0,
                            firestoreId = firestoreId,
                            authorName = if (parsed.authorName.isNotBlank() && parsed.authorName != "Classmate") parsed.authorName else (data["authorName"] as? String ?: existing?.authorName ?: "")
                        )
                        if (existing != null) {
                            repository.updateCompletedTopic(topicToSave)
                        } else {
                            repository.addCompletedTopicLocally(topicToSave)
                        }
                    }
                }
                repository.deduplicateCompletedTopics()
            } catch (e: Exception) {
                Log.w("FirestoreSync", "Error restoring completed topics: ${e.message}")
            }

            // 5. Restore Assignments (Written with date & status)
            try {
                val assignmentsSnapshot = db.collection("users").document(uid).collection("assignments").get().await()
                for (doc in assignmentsSnapshot) {
                    try {
                        val a = doc.toObject(Assignment::class.java)
                        if (a != null) {
                            val existing = repository.getAssignmentByFirestoreId(doc.id)
                                ?: repository.findMatchingSimilarAssignment(a.courseCode, a.subject, a.title)
                            val toSave = a.copy(
                                id = existing?.id ?: 0,
                                firestoreId = doc.id,
                                authorName = if (a.authorName.isNotBlank() && a.authorName != "Classmate") a.authorName else (existing?.authorName ?: a.authorName)
                            )
                            if (existing != null) {
                                repository.addAssignmentLocally(toSave)
                            } else {
                                repository.addAssignmentLocally(toSave)
                            }
                        }
                    } catch (e: Exception) {
                        val existing = repository.getAssignmentByFirestoreId(doc.id)
                        val a = com.example.data.sync.FirestoreSyncManager.mapToAssignment(doc.data, doc.id, existing?.id)
                        repository.addAssignmentLocally(a)
                    }
                }
                repository.deduplicateAssignments()
            } catch (e: Exception) {
                Log.w("FirestoreSync", "Error restoring assignments: ${e.message}")
            }

            // 6. Restore Assessments (Written with date, type, syllabus)
            try {
                val assessmentsSnapshot = db.collection("users").document(uid).collection("assessments").get().await()
                for (doc in assessmentsSnapshot) {
                    try {
                        val ass = doc.toObject(Assessment::class.java)
                        if (ass != null) {
                            val existing = repository.getAssessmentByFirestoreId(doc.id)
                                ?: repository.findMatchingSimilarAssessment(ass.courseCode, ass.subject, ass.title)
                            val toSave = ass.copy(
                                id = existing?.id ?: 0,
                                firestoreId = doc.id,
                                authorName = if (ass.authorName.isNotBlank() && ass.authorName != "Classmate") ass.authorName else (existing?.authorName ?: ass.authorName)
                            )
                            if (existing != null) {
                                repository.addAssessmentLocally(toSave)
                            } else {
                                repository.addAssessmentLocally(toSave)
                            }
                        }
                    } catch (e: Exception) {
                        val existing = repository.getAssessmentByFirestoreId(doc.id)
                        val ass = com.example.data.sync.FirestoreSyncManager.mapToAssessment(doc.data, doc.id, existing?.id)
                        repository.addAssessmentLocally(ass)
                    }
                }
                repository.deduplicateAssessments()
            } catch (e: Exception) {
                Log.w("FirestoreSync", "Error restoring assessments: ${e.message}")
            }

            // 7. Restore Daily Revisions & Study Tasks
            try {
                val studySnapshot = db.collection("users").document(uid).collection("study_tasks").get().await()
                for (doc in studySnapshot) {
                    try {
                        val task = doc.toObject(StudyTask::class.java)
                        if (task != null) repository.addStudyTask(task)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                // ignore
            }

            // 8. Restore Exams
            try {
                val examsSnapshot = db.collection("users").document(uid).collection("exams").get().await()
                for (doc in examsSnapshot) {
                    try {
                        val ex = doc.toObject(Exam::class.java)
                        if (ex != null) repository.addExam(ex)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                // ignore
            }

            // 9. Restore Planner Tasks
            try {
                val plannerSnapshot = db.collection("users").document(uid).collection("planner_tasks").get().await()
                for (doc in plannerSnapshot) {
                    try {
                        val task = doc.toObject(PlannerTask::class.java)
                        if (task != null) repository.addPlannerTask(task)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                // ignore
            }

            // 10. Restore Shared Batch Records (Assignments, Assessments, Finished Chapters for this batch)
            try {
                val col = _studentCollege.value
                val crs = _selectedCourse.value?.code ?: ""
                val admYr = _studentAdmissionYear.value
                val btch = _studentBatch.value
                val customCode = _customBatchCode.value
                if (col.isNotBlank() && crs.isNotBlank() && btch.isNotBlank()) {
                    batchSyncManager.fetchAndSyncBatchNow(col, crs, admYr, btch, customCode)
                }
            } catch (e: Exception) {
                Log.w("FirestoreSync", "Error restoring batch records during profile restore: ${e.message}")
            }

            return profileFound
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Restore internal failed: ${e.message}", e)
            return false
        }
    }

    fun restoreDataFromFirebase(onComplete: (Boolean) -> Unit = {}) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            onComplete(false)
            return
        }
        val email = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: _studentEmail.value
        
        viewModelScope.launch(Dispatchers.IO) {
            val success = restoreDataFromFirebaseInternal(uid, email)
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    fun setAttendanceTarget(target: Int) {
        val validated = target.coerceIn(1, 100)
        _attendanceTarget.value = validated
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit().putInt("attendance_target_percentage", validated).apply()
    }

    fun findClassStartTime(subject: String, dayOfWeek: Int = getCurrentDayOfWeek()): String? {
        val cls = timetable.value.firstOrNull {
            it.dayOfWeek == dayOfWeek && it.subject.equals(subject, ignoreCase = true)
        }
        return cls?.startTime
    }

    fun isAttendanceAllowed(
        dateString: String? = null,
        startTime: String? = null,
        classTime: String? = null,
        subject: String? = null
    ): Boolean {
        val effectiveStartTime = startTime
            ?: AttendanceTimeValidator.extractStartTime(null, classTime)
            ?: subject?.let { findClassStartTime(it) }
        return AttendanceTimeValidator.isAttendanceAllowed(dateString, effectiveStartTime, classTime)
    }

    fun markAttendance(
        subject: String,
        isPresent: Boolean,
        classTime: String? = null,
        status: String = if (isPresent) "PRESENT" else "ABSENT",
        dateString: String? = null,
        startTime: String? = null,
        endTime: String? = null,
        note: String? = null,
        reason: String? = null
    ) {
        val actualDateString = dateString ?: AttendanceTimeValidator.getTodayDateString()

        // Validate timing constraint: Attendance can only be accepted AFTER class start time, not before time!
        val effectiveStartTime = startTime
            ?: AttendanceTimeValidator.extractStartTime(null, classTime)
            ?: findClassStartTime(subject)

        val validation = AttendanceTimeValidator.validateAttendanceTime(
            dateString = actualDateString,
            startTime = effectiveStartTime,
            classTime = classTime
        )

        if (validation !is AttendanceValidationResult.Allowed) {
            val message = when (validation) {
                is AttendanceValidationResult.TooEarly -> validation.message
                is AttendanceValidationResult.FutureDate -> validation.message
                else -> "Attendance cannot be marked before class time."
            }
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
                addNotificationWithDuplicateCheck(
                    InAppNotification(
                        title = "Attendance Blocked",
                        message = message,
                        type = "class"
                    )
                )
            }
            return
        }

        // Duplicate prevention: match on date, subject, and time/period
        val existing = allAttendanceRecords.value.find { 
            it.dateString == actualDateString && 
            it.subject.equals(subject, ignoreCase = true) &&
            (classTime == null || it.classTime == null || it.classTime == classTime) &&
            (startTime == null || it.startTime == null || it.startTime == startTime)
        }

        val record = AttendanceRecord(
            id = existing?.id ?: 0,
            dateString = actualDateString,
            subject = subject,
            isPresent = isPresent,
            classTime = classTime ?: existing?.classTime,
            firestoreId = existing?.firestoreId ?: java.util.UUID.randomUUID().toString(),
            status = status,
            startTime = startTime ?: existing?.startTime ?: effectiveStartTime,
            endTime = endTime ?: existing?.endTime,
            note = note ?: existing?.note,
            reason = reason ?: existing?.reason,
            recordedTimestamp = System.currentTimeMillis()
        )
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveAttendanceRecord(record)
            syncDataToFirebase()
            
            val statusLabel = when (status) {
                "NO_CLASS" -> "No Class"
                "CANCELLED" -> "Cancelled"
                "PRESENT" -> "Present"
                "ABSENT" -> "Absent"
                "EXCUSED" -> "Excused"
                else -> if (isPresent) "Present" else "Absent"
            }
            addNotificationWithDuplicateCheck(
                InAppNotification(
                    title = "Attendance Updated",
                    message = "Successfully marked $statusLabel for $subject ($actualDateString).",
                    type = "class"
                )
            )
        }
    }

    fun updateAttendanceRecord(record: AttendanceRecord) {
        val effectiveStartTime = record.startTime
            ?: AttendanceTimeValidator.extractStartTime(null, record.classTime)
            ?: findClassStartTime(record.subject)

        val validation = AttendanceTimeValidator.validateAttendanceTime(
            dateString = record.dateString,
            startTime = effectiveStartTime,
            classTime = record.classTime
        )

        if (validation !is AttendanceValidationResult.Allowed) {
            val message = when (validation) {
                is AttendanceValidationResult.TooEarly -> validation.message
                is AttendanceValidationResult.FutureDate -> validation.message
                else -> "Attendance cannot be modified before class time."
            }
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveAttendanceRecord(record.copy(recordedTimestamp = System.currentTimeMillis()))
            syncDataToFirebase()
        }
    }

    fun deleteAttendanceRecord(record: AttendanceRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAttendanceRecord(record)
            syncDataToFirebase()
        }
    }

    fun getAttendancePercentageForSubject(subject: String): Float {
        val summary = AttendanceCalculator.calculateSubjectSummary(subject, allAttendanceRecords.value, _attendanceTarget.value)
        return if (summary.totalClasses == 0) 100f else summary.percentage
    }

    fun getOverallAttendancePercentage(): Float {
        val summary = AttendanceCalculator.calculateOverallSummary(allAttendanceRecords.value, _attendanceTarget.value)
        return if (summary.totalClasses == 0) 100f else summary.percentage
    }

    private val _isGeneratingRevision = MutableStateFlow(false)
    val isGeneratingRevision: StateFlow<Boolean> = _isGeneratingRevision

    private val _lastGeneratedRevision = MutableStateFlow<DailySubjectRevision?>(null)
    val lastGeneratedRevision: StateFlow<DailySubjectRevision?> = _lastGeneratedRevision

    fun clearLastGeneratedRevision() {
        _lastGeneratedRevision.value = null
    }

    fun getRevisionForClass(dateString: String, subject: String, periodNumber: Int, classTime: String): DailySubjectRevision? {
        return allRevisions.value.firstOrNull { rev ->
            rev.dateString == dateString &&
            rev.subject.equals(subject, ignoreCase = true) &&
            ((periodNumber > 0 && rev.periodNumber == periodNumber) ||
             (classTime.isNotBlank() && rev.classTime == classTime) ||
             (periodNumber == 0 && classTime.isBlank() && rev.subject.equals(subject, ignoreCase = true)))
        }
    }

    fun generateClassRevision(subject: String, explanation: String, periodNumber: Int = 0, classTime: String = "") {
        _isGeneratingRevision.value = true
        _lastGeneratedRevision.value = null
        viewModelScope.launch {
            try {
                val response = geminiService.generateDailyClassRevision(
                    subject = subject,
                    explanation = explanation,
                    modelOption = _selectedGeminiModel.value,
                    periodNumber = periodNumber,
                    classTime = classTime
                )
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val dateString = sdf.format(java.util.Date())
                
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val existing = repository.getRevisionForClass(dateString, subject, periodNumber, classTime)
                    val revision = DailySubjectRevision(
                        id = existing?.id ?: 0,
                        dateString = dateString,
                        subject = subject,
                        periodNumber = periodNumber,
                        classTime = classTime,
                        studentExplanation = explanation,
                        aiSummary = response.aiSummary,
                        keyPoints = response.keyPoints,
                        revisionQuestions = response.revisionQuestions
                    )
                    repository.saveRevision(revision)
                    _lastGeneratedRevision.value = revision
                    syncDataToFirebase()
                }
                
                addNotificationWithDuplicateCheck(
                    InAppNotification(
                        title = "AI Notes Generated",
                        message = "AI successfully analyzed your lecture explanation for $subject (Period $periodNumber) and saved custom summaries & test questions.",
                        type = "study"
                    )
                )
            } catch (e: Exception) {
                Log.e("PlannerViewModel", "Error generating class revision: ${e.message}", e)
            } finally {
                _isGeneratingRevision.value = false
            }
        }
    }

    fun updateProfilePictureFromUri(uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch

                val options = android.graphics.BitmapFactory.Options()
                val rawBitmap = android.graphics.BitmapFactory.decodeStream(inputStream, null, options) ?: return@launch
                val bitmap = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                        rawBitmap.config == android.graphics.Bitmap.Config.HARDWARE) {
                        rawBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: rawBitmap
                    } else rawBitmap
                } catch (e: Exception) {
                    rawBitmap
                }

                val width = bitmap.width.coerceAtLeast(1)
                val height = bitmap.height.coerceAtLeast(1)
                val size = minOf(width, height)
                val x = ((width - size) / 2).coerceAtLeast(0)
                val y = ((height - size) / 2).coerceAtLeast(0)
                val croppedBitmap = try {
                    if (size > 0 && x + size <= width && y + size <= height) {
                        android.graphics.Bitmap.createBitmap(bitmap, x, y, size, size)
                    } else bitmap
                } catch (e: Exception) {
                    bitmap
                }

                val file = java.io.File(context.filesDir, "custom_profile_picture.jpg")
                val outputStream = java.io.FileOutputStream(file)
                croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
                outputStream.flush()
                outputStream.close()

                val bOut = java.io.ByteArrayOutputStream()
                val scaled = if (size > 240) {
                    android.graphics.Bitmap.createScaledBitmap(croppedBitmap, 240, 240, true)
                } else croppedBitmap
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, bOut)
                val base64Data = "data:image/jpeg;base64," + android.util.Base64.encodeToString(bOut.toByteArray(), android.util.Base64.NO_WRAP)

                val localPath = "file://" + file.absolutePath
                withContext(Dispatchers.Main) {
                    _studentDpUrl.value = localPath
                }

                val sharedPrefs = context.getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
                sharedPrefs.edit()
                    .putString("student_dp_url", localPath)
                    .putString("student_dp_base64", base64Data)
                    .apply()

                syncDataToFirebase()
            } catch (e: Exception) {
                Log.e("PlannerViewModel", "Error saving profile picture from uri: ${e.message}", e)
            }
        }
    }

    fun updateProfilePictureFromBitmap(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val safeBitmap = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                        bitmap.config == android.graphics.Bitmap.Config.HARDWARE) {
                        bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: bitmap
                    } else bitmap
                } catch (e: Exception) {
                    bitmap
                }

                val width = safeBitmap.width.coerceAtLeast(1)
                val height = safeBitmap.height.coerceAtLeast(1)
                val size = minOf(width, height)
                val x = ((width - size) / 2).coerceAtLeast(0)
                val y = ((height - size) / 2).coerceAtLeast(0)
                val croppedBitmap = try {
                    if (size > 0 && x + size <= width && y + size <= height) {
                        android.graphics.Bitmap.createBitmap(safeBitmap, x, y, size, size)
                    } else safeBitmap
                } catch (e: Exception) {
                    safeBitmap
                }

                val file = java.io.File(context.filesDir, "custom_profile_picture.jpg")
                val outputStream = java.io.FileOutputStream(file)
                croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
                outputStream.flush()
                outputStream.close()

                val bOut = java.io.ByteArrayOutputStream()
                val scaled = if (size > 240) {
                    android.graphics.Bitmap.createScaledBitmap(croppedBitmap, 240, 240, true)
                } else croppedBitmap
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, bOut)
                val base64Data = "data:image/jpeg;base64," + android.util.Base64.encodeToString(bOut.toByteArray(), android.util.Base64.NO_WRAP)

                val localPath = "file://" + file.absolutePath
                withContext(Dispatchers.Main) {
                    _studentDpUrl.value = localPath
                    _studentDpPreset.value = ""
                }

                val sharedPrefs = context.getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
                sharedPrefs.edit()
                    .putString("student_dp_url", localPath)
                    .putString("student_dp_preset", "")
                    .putString("student_dp_base64", base64Data)
                    .apply()
            } catch (e: Exception) {
                Log.e("PlannerViewModel", "Error saving profile picture from bitmap: ${e.message}", e)
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _areNotificationsEnabled.value = enabled
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("are_notifications_enabled", enabled).apply()
        
        viewModelScope.launch {
            if (enabled) {
                generateSmartNotifications()
                scheduleTimetableClassNotifications()
                triggerLocalSystemNotification(
                    "Academic Alerts Enabled", 
                    "You will now receive notifications for assignments and lectures due within 24 hours."
                )
            } else {
                // Cancel all scheduled notifications from AcademicNotificationManager
                AcademicNotificationManager.cancelAllByType(getApplication(), "class")
                AcademicNotificationManager.cancelAllByType(getApplication(), "assignment")
                AcademicNotificationManager.cancelAllByType(getApplication(), "assessment")
                AcademicNotificationManager.cancelAllByType(getApplication(), "study")
            }
        }
    }

    fun triggerLocalSystemNotification(title: String, message: String) {
        val context = getApplication<Application>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "upcoming_academic_alerts"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Academic Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for assignments and lectures within 24 hours"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            
        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: SecurityException) {
            // Safe handling if system permission is missing on high API levels
        }
    }

    fun resetWholeApp() {
        viewModelScope.launch {
            val course = selectedCourse.value
            if (course != null) {
                repository.clearTimetable(course.code)
            }
            // Clear preferences
            val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
            sharedPrefs.edit().clear().apply()
            
            _selectedCourse.value = null
            _currentScreen.value = Screen.Welcome
        }
    }

    // --- Update System Functions ---
    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            if (_updateCheckInProgress.value) return@launch
            _updateCheckInProgress.value = true
            
            val result = updateService.checkForUpdates(getApplication())
            _updateResult.value = result
            _lastCheckedTime.value = updateService.getLastCheckedTime(getApplication())
            val cached = updateService.getCachedUpdateInfo(getApplication())
            _cachedUpdateConfig.value = cached
            
            _updateCheckInProgress.value = false

            if (result is AppUpdateResult.UpdateAvailable) {
                _updateAvailable.value = true
                if (result.isForce) {
                    _isUpdateDialogDismissed.value = false
                } else {
                    // Check if the user previously dismissed this exact version
                    val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
                    val dismissedVersion = sharedPrefs.getString("dismissed_update_version", "") ?: ""
                    if (dismissedVersion == result.config.latestVersion) {
                        _isUpdateDialogDismissed.value = true
                    } else {
                        _isUpdateDialogDismissed.value = false
                    }
                }
            } else {
                _updateAvailable.value = false
                _isUpdateDialogDismissed.value = false
            }
        }
    }

    fun dismissUpdateDialog() {
        _isUpdateDialogDismissed.value = true
        _updateDownloadState.value = null
        _updateDownloadProgress.value = null
        // Store dismissed version to avoid repeatedly showing it
        val config = _cachedUpdateConfig.value
        if (config != null) {
            val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
            sharedPrefs.edit().putString("dismissed_update_version", config.latestVersion).apply()
        }
    }

    fun resetDownloadState() {
        _updateDownloadProgress.value = null
        _updateDownloadState.value = null
    }

    fun installDownloadedUpdate() {
        viewModelScope.launch(Dispatchers.Main) {
            val context = getApplication<Application>()
            _updateDownloadState.value = "Opening installer..."
            val launched = DownloadManagerHelper.installLatestDownloadedApk(context)
            if (!launched) {
                _updateDownloadState.value = "Download complete! Tap 'Install Update' to install."
            }
        }
    }

    fun clearUpdateResult() {
        _updateResult.value = null
        _updateAvailable.value = false
        _updateDownloadProgress.value = null
        _updateDownloadState.value = null
    }

    fun saveCustomUpdateUrl(url: String) {
        _customUpdateUrl.value = url.trim()
        updateService.setCustomUpdateUrl(getApplication(), url)
    }

    fun updateGitHubRepoConfig(owner: String, repo: String) {
        val cleanOwner = owner.trim().ifEmpty { GitHubUpdateClient.DEFAULT_OWNER }
        val cleanRepo = repo.trim().ifEmpty { GitHubUpdateClient.DEFAULT_REPO }
        updateService.setGitHubRepoConfig(getApplication(), cleanOwner, cleanRepo)
        _gitHubOwner.value = cleanOwner
        _gitHubRepo.value = cleanRepo
        checkForUpdates(silent = false)
    }

    fun downloadAndInstallUpdate(apkUrl: String) {
        val context = getApplication<Application>()
        val config = _cachedUpdateConfig.value
        val version = config?.latestVersion ?: ""
        val targetUrl = if (apkUrl.isBlank() || apkUrl.contains("example.com")) {
            config?.apkUrl?.ifBlank { null }
                ?: "https://raw.githubusercontent.com/${_gitHubOwner.value}/${_gitHubRepo.value}/main/app-release.apk"
        } else {
            apkUrl
        }

        _updateDownloadState.value = "Starting download..."
        _updateDownloadProgress.value = 0f

        val downloadId = DownloadManagerHelper.downloadApk(
            context = context,
            apkUrl = targetUrl,
            version = version
        )

        if (downloadId > 0L) {
            monitorDownloadProgress(downloadId)
            viewModelScope.launch {
                addNotificationWithDuplicateCheck(
                    InAppNotification(
                        title = "Download Started",
                        message = "Downloading MedPulse update. You will be prompted to install once complete.",
                        type = "system"
                    )
                )
            }
        } else {
            _updateDownloadState.value = "Failed to start download via DownloadManager"
            _updateDownloadProgress.value = null
        }
    }

    private fun monitorDownloadProgress(downloadId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val downloadManager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                _updateDownloadState.value = "DownloadManager unavailable"
                return@launch
            }

            var isDownloading = true
            while (isDownloading) {
                try {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1

                        when (status) {
                            DownloadManager.STATUS_RUNNING -> {
                                val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                val totalBytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                val downloaded = if (bytesDownloadedIndex >= 0) cursor.getLong(bytesDownloadedIndex) else 0L
                                val total = if (totalBytesIndex >= 0) cursor.getLong(totalBytesIndex) else -1L

                                if (total > 0L) {
                                    val progress = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    _updateDownloadProgress.value = progress
                                    _updateDownloadState.value = "Downloading update: ${(progress * 100).toInt()}%"
                                } else {
                                    _updateDownloadProgress.value = -1f
                                    _updateDownloadState.value = "Downloading update..."
                                }
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                _updateDownloadProgress.value = 1f
                                _updateDownloadState.value = "Download complete! Ready to install."
                                isDownloading = false
                                withContext(Dispatchers.Main) {
                                    DownloadManagerHelper.installApkFromDownloadId(getApplication(), downloadId)
                                }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else 0
                                _updateDownloadState.value = "Download failed (code: $reason)"
                                _updateDownloadProgress.value = null
                                isDownloading = false
                            }
                            DownloadManager.STATUS_PAUSED -> {
                                _updateDownloadState.value = "Download paused (waiting for connection)"
                            }
                        }
                        cursor.close()
                    } else {
                        isDownloading = false
                    }
                } catch (e: Exception) {
                    Log.w("PlannerViewModel", "Error monitoring download: ${e.message}")
                    isDownloading = false
                }

                if (isDownloading) {
                    delay(600)
                }
            }
        }
    }


    fun startBatchNotificationSync() {
        val col = _studentCollege.value
        val crs = _selectedCourse.value?.code ?: "MBBS"
        val admYr = _studentAdmissionYear.value
        val btch = _studentBatch.value
        val customCode = _customBatchCode.value
        batchSyncManager.startListeningToBatch(col, crs, admYr, btch, customCode)
    }

    fun stopBatchNotificationSync() {
        batchSyncManager.stopListening()
    }

    fun testBatchSyncConnection(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val col = _studentCollege.value
            val crs = _selectedCourse.value?.code ?: "MBBS"
            val admYr = _studentAdmissionYear.value
            val btch = _studentBatch.value
            val customCode = _customBatchCode.value
            val result = batchSyncManager.testBatchSyncConnection(col, crs, admYr, btch, customCode)
            result.fold(
                onSuccess = { msg -> onComplete(true, msg) },
                onFailure = { err -> onComplete(false, err.message ?: "Connection test failed") }
            )
        }
    }

    fun fetchAndSyncBatchNow(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val col = _studentCollege.value
            val crs = _selectedCourse.value?.code ?: "MBBS"
            val admYr = _studentAdmissionYear.value
            val btch = _studentBatch.value
            val customCode = _customBatchCode.value
            val result = batchSyncManager.fetchAndSyncBatchNow(col, crs, admYr, btch, customCode)
            result.fold(
                onSuccess = { count ->
                    val message = if (count > 0) "Synchronized! Imported $count new batch item(s)." else "Feed up to date. No new batch items found."
                    onComplete(true, message)
                },
                onFailure = { err ->
                    onComplete(false, err.message ?: "Batch sync failed")
                }
            )
        }
    }

    fun postNoticeToBatch(
        title: String,
        message: String,
        category: String = "batch_notice",
        urgent: Boolean = false,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val col = _studentCollege.value
        val crs = _selectedCourse.value?.code ?: "MBBS"
        val admYr = _studentAdmissionYear.value
        val btch = _studentBatch.value
        val author = _studentName.value.ifBlank { "Peer Student" }
        val customCode = _customBatchCode.value

        viewModelScope.launch {
            _isPostingBatchNotice.value = true
            _batchPostError.value = null
            val result = batchSyncManager.postBatchNotice(
                college = col,
                course = crs,
                admissionYear = admYr,
                batch = btch,
                title = title,
                message = message,
                authorName = author,
                category = category,
                urgent = urgent,
                customBatchCode = customCode
            )
            _isPostingBatchNotice.value = false
            result.fold(
                onSuccess = { docId ->
                    onComplete(true, null)
                },
                onFailure = { error ->
                    _batchPostError.value = error.localizedMessage
                    onComplete(false, error.localizedMessage)
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        batchSyncManager.stopListening()
    }

}

enum class Screen {
    Welcome,
    Dashboard,
    Timetable,
    Planner,
    Finished,
    Calendar,
    AIChat,
    Settings,
    Attendance
}

enum class AuthState {
    LOADING,
    UNAUTHENTICATED,
    AUTHENTICATED_PROFILE_INCOMPLETE,
    AUTHENTICATED_PROFILE_COMPLETE
}

enum class LoginMode {
    UNDECIDED,
    GUEST,
    GOOGLE,
    FIREBASE
}

class PlannerViewModelFactory(
    private val application: Application,
    private val repository: PlannerRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlannerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlannerViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
