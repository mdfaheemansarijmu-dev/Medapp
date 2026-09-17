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
import com.example.data.repository.PlannerRepository
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                    list.distinctBy { "${it.dayOfWeek}_${it.periodNumber}_${it.subject}_${it.startTime}_${it.endTime}" }
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

    private val _studentSemester = MutableStateFlow("")
    val studentSemester: StateFlow<String> = _studentSemester.asStateFlow()

    private val _studentBatch = MutableStateFlow("")
    val studentBatch: StateFlow<String> = _studentBatch.asStateFlow()

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

    private val geminiService = GeminiParserService()

    private val _selectedGeminiModel = MutableStateFlow(GeminiModelOption.DEFAULT)
    val selectedGeminiModel: StateFlow<GeminiModelOption> = _selectedGeminiModel.asStateFlow()

    fun setGeminiModel(model: GeminiModelOption) {
        _selectedGeminiModel.value = model
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit().putString("selected_gemini_model_id", model.modelId).apply()
    }

    init {
        // Read stored course preference from local preferences if any
        val sharedPrefs = application.getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        _fcmToken.value = sharedPrefs.getString("fcm_registration_token", "") ?: ""

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

        // Verify real Google or Firebase session
        if (savedLoginMode == LoginMode.GOOGLE.name) {
            try {
                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(application)
                if (account != null) {
                    _googleUserId.value = account.id ?: ""
                    _studentName.value = account.displayName ?: ""
                    _studentEmail.value = account.email ?: ""
                    _studentDpUrl.value = account.photoUrl?.toString() ?: ""
                    _studentDpPreset.value = "none"
                } else {
                    // If no active Google account found, reset login to UNDECIDED
                    _loginMode.value = LoginMode.UNDECIDED
                    _googleUserId.value = ""
                    _studentName.value = ""
                    _studentEmail.value = ""
                    _studentDpUrl.value = ""
                    _studentDpPreset.value = "doctor_male"
                    sharedPrefs.edit()
                        .putString("login_mode", LoginMode.UNDECIDED.name)
                        .putString("google_user_id", "")
                        .putString("student_name", "")
                        .putString("student_email", "")
                        .putString("student_dp_url", "")
                        .putString("student_dp_preset", "doctor_male")
                        .putString("selected_course_code", null)
                        .apply()
                }
            } catch (e: Throwable) {
                Log.w("PlannerViewModel", "GoogleSignIn status check handled: ${e.message}")
            }
        } else if (savedLoginMode == LoginMode.FIREBASE.name) {
            try {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    _studentEmail.value = user.email ?: ""
                    _studentName.value = sharedPrefs.getString("student_name", "") ?: user.displayName ?: ""
                    _studentDpUrl.value = sharedPrefs.getString("student_dp_url", "") ?: ""
                    _studentDpPreset.value = sharedPrefs.getString("student_dp_preset", "doctor_male") ?: "doctor_male"
                    _studentCollege.value = sharedPrefs.getString("student_college", "") ?: ""
                    _studentYear.value = sharedPrefs.getString("student_year", "") ?: ""
                    _studentSemester.value = sharedPrefs.getString("student_semester", "") ?: ""
                    _studentBatch.value = sharedPrefs.getString("student_batch", "") ?: ""
                    _isProfileCompleted.value = sharedPrefs.getBoolean("is_profile_completed", false)
                } else {
                    _loginMode.value = LoginMode.UNDECIDED
                    _studentName.value = ""
                    _studentEmail.value = ""
                    _studentDpUrl.value = ""
                    _studentDpPreset.value = "doctor_male"
                    sharedPrefs.edit()
                        .putString("login_mode", LoginMode.UNDECIDED.name)
                        .putString("student_name", "")
                        .putString("student_email", "")
                        .putString("student_dp_url", "")
                        .putString("student_dp_preset", "doctor_male")
                        .putString("selected_course_code", null)
                        .apply()
                }
            } catch (e: Throwable) {
                Log.w("PlannerViewModel", "FirebaseAuth status check handled: ${e.message}")
            }
        }

        val updatedLoginMode = _loginMode.value
        val savedCourseCode = sharedPrefs.getString("selected_course_code", null)
        val isOnboardingCompleted = sharedPrefs.getBoolean("is_onboarding_completed", false)
        if (savedCourseCode != null && (isOnboardingCompleted || updatedLoginMode != LoginMode.UNDECIDED)) {
            try {
                val course = MedicalCourse.valueOf(savedCourseCode)
                _selectedCourse.value = course
                _currentScreen.value = Screen.Dashboard
                viewModelScope.launch {
                    repository.populateDefaultTimetableIfEmpty(course.code)
                    com.example.util.AcademicNotificationManager.verifyExistingNotifications(application)
                    com.example.util.AcademicNotificationManager.rescheduleAllFutureNotifications(application)
                    scheduleTimetableClassNotifications()
                    generateSmartNotifications()
                }
            } catch (e: Exception) {
                _currentScreen.value = Screen.Welcome
            }
        } else {
            _currentScreen.value = Screen.Welcome
        }

        // Start Clock updates
        viewModelScope.launch {
            val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            var lastDateStr = dayFormat.format(Date())
            while (true) {
                _currentTimeOfDay.value = format.format(Date())
                _currentCalendarDate.value = Calendar.getInstance()
                
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
            
            // Generate nice starter in-app alerts
            addNotificationWithDuplicateCheck(
                InAppNotification(
                    title = "Course Selected: ${course.displayName}",
                    message = "Your standard weekly medical timetable has been pre-loaded! Go to the 'Timetable' tab to customize it.",
                    type = "class"
                )
            )

            // Auto navigate to dashboard
            _currentScreen.value = Screen.Dashboard
            generateSmartNotifications()
            scheduleTimetableClassNotifications()
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

        val calendar = Calendar.getInstance()
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
        val calDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
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
        viewModelScope.launch {
            repository.addClass(
                TimetableClass(
                    courseCode = course.code,
                    dayOfWeek = day,
                    periodNumber = period,
                    subject = subject,
                    startTime = start,
                    endTime = end,
                    room = room,
                    teacherName = teacher
                )
            )
            generateSmartNotifications()
            scheduleTimetableClassNotifications()
            syncDataToFirebase()
        }
    }

    fun updateTimetableClass(id: Int, subject: String, day: Int, start: String, end: String, room: String?, teacher: String?, period: Int) {
        val course = selectedCourse.value ?: return
        viewModelScope.launch {
            repository.addClass(
                TimetableClass(
                    id = id,
                    courseCode = course.code,
                    dayOfWeek = day,
                    periodNumber = period,
                    subject = subject,
                    startTime = start,
                    endTime = end,
                    room = room,
                    teacherName = teacher
                )
            )
            generateSmartNotifications()
            scheduleTimetableClassNotifications()
            syncDataToFirebase()
        }
    }

    fun removeTimetableClass(id: Int) {
        viewModelScope.launch {
            repository.deleteClass(id)
            generateSmartNotifications()
            scheduleTimetableClassNotifications()
            syncDataToFirebase()
        }
    }

    fun addAssignment(subject: String, title: String, dueDate: Long, priority: String, type: String, notes: String? = null) {
        val course = selectedCourse.value ?: return
        viewModelScope.launch {
            val assignment = Assignment(
                    courseCode = course.code,
                    subject = subject,
                    title = title,
                    dueDate = dueDate,
                    priority = priority,
                    status = "Pending",
                    type = type,
                    notes = notes
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
            }
            syncDataToFirebase()
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
            repository.deleteAssignment(id)
            AcademicNotificationManager.cancelByItemId(getApplication(), "asg_$id")
            syncDataToFirebase()
        }
    }

    fun addAssessment(subject: String, title: String, date: Long, type: String, syllabus: String? = null) {
        val course = selectedCourse.value ?: return
        viewModelScope.launch {
            val assessment = Assessment(
                    courseCode = course.code,
                    subject = subject,
                    title = title,
                    date = date,
                    type = type,
                    status = "Upcoming",
                    syllabus = syllabus
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
            }
            syncDataToFirebase()
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
            repository.deleteAssessment(id)
            AcademicNotificationManager.cancelByItemId(getApplication(), "asm_$id")
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
                _activeUnifiedResponse.value = response
                _activeParsedDrafts.value = response.extracted_items

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
                } else if (response.document_type == "Weekly Timetable") {
                    val days = response.extracted_timetable.map { it.day_of_week }.distinct().size
                    val classesCount = response.extracted_timetable.filter { !it.is_lunch_break }.size
                    val teachersCount = response.extracted_timetable.mapNotNull { it.teacher_name }.distinct().size
                    val practicalsCount = response.extracted_timetable.count { it.is_practical }

                    "I analyzed your **Weekly Timetable** and understood the structured table:\n\n" +
                            "✓ $days Working Days\n" +
                            "✓ $classesCount Classes\n" +
                            "✓ $teachersCount Teachers\n" +
                            "✓ $practicalsCount Practical Sessions\n\n" +
                            "Please review the preview and choose to replace your current timetable."
                } else if (response.is_temporary_override) {
                    val total = response.extracted_items.size
                    "I detected a **Temporary Schedule Override**:\n\n" +
                            "This override is proposed for **${response.override_date ?: "Tomorrow"}** only. It will override specific classes rather than replacing your whole timetable.\n\n" +
                            "✓ $total Temporary override details found."
                } else {
                    val assignments = response.extracted_items.count { it.category == "Assignment" || it.category == "Homework" }
                    val assessments = response.extracted_items.count { it.category == "Assessment" || it.category == "Exam" || it.category == "Viva" }
                    val holidays = response.extracted_items.count { it.category == "Holiday" }
                    val other = response.extracted_items.size - assignments - assessments - holidays

                    "I parsed the notice and found:\n" +
                            "✓ $assignments Assignments\n" +
                            "✓ $assessments Assessments / Exams\n" +
                            "✓ $holidays Holidays\n" +
                            (if (other > 0) "✓ $other General notices/seminars\n" else "") +
                            "\nReview details in the Proposed Items panel below."
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
                if (response.extracted_timetable.isNotEmpty()) {
                    response.extracted_timetable
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("PlannerVM", "Failed to parse document with Gemini", e)
                emptyList()
            }
        }
    }

    fun completeOnboarding(course: MedicalCourse) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean("is_onboarding_completed", true)
            .putString("selected_course_code", course.name)
            .apply()

        val onboardingPrefs = getApplication<Application>().getSharedPreferences("medpulse_onboarding_draft", Application.MODE_PRIVATE)
        onboardingPrefs.edit().clear().apply()

        selectCourse(course)
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
                    colorHex = colors[idx % colors.size]
                )
            }
            
            // Insert each class
            for (cls in newClasses) {
                repository.addClass(cls)
            }
            
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
                        val asmId = repository.addAssessment(
                            Assessment(
                                courseCode = course.code,
                                subject = item.subject,
                                title = item.title,
                                date = itemTimestamp,
                                type = "University Exam",
                                status = "Upcoming",
                                syllabus = item.details
                            )
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
                        val asmId = repository.addAssessment(
                            Assessment(
                                courseCode = course.code,
                                subject = item.subject,
                                title = item.title,
                                date = itemTimestamp,
                                type = if (categoryNormalized.contains("viva") || titleNormalized.contains("viva")) "Viva" else if (categoryNormalized.contains("exam") || titleNormalized.contains("exam")) "University Exam" else "Class Test",
                                syllabus = item.details
                            )
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
                        val asgId = repository.addAssignment(
                            Assignment(
                                courseCode = course.code,
                                subject = item.subject,
                                title = item.title,
                                dueDate = itemTimestamp,
                                priority = item.priority,
                                status = "Pending",
                                type = if (categoryNormalized.contains("homework") || titleNormalized.contains("homework")) "Homework" else "Assignment",
                                notes = item.details
                            )
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
                        val asgId = repository.addAssignment(
                            Assignment(
                                courseCode = course.code,
                                subject = item.subject,
                                title = item.title,
                                dueDate = itemTimestamp,
                                priority = item.priority,
                                status = "Pending",
                                type = "Assignment",
                                notes = item.details
                            )
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

    private suspend fun addNotificationWithDuplicateCheck(notification: InAppNotification) {
        val existing = repository.getNotifications().first()
        val alreadyExists = existing.any { 
            it.title == notification.title && 
            it.message == notification.message &&
            it.type == notification.type
        }
        if (!alreadyExists) {
            repository.addNotification(notification)
            // Push to Android system notification tray immediately
            if (_areNotificationsEnabled.value) {
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

    fun signInWithGoogle(name: String, email: String, dpUrl: String, googleId: String, idToken: String? = null) {
        _loginMode.value = LoginMode.GOOGLE
        _studentName.value = name
        _studentEmail.value = email
        _studentDpUrl.value = dpUrl
        _studentDpPreset.value = "none"
        _googleUserId.value = googleId
        _isAuthenticating.value = false
        _authError.value = null

        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("login_mode", LoginMode.GOOGLE.name)
            .putString("student_name", name)
            .putString("student_email", email)
            .putString("student_dp_url", dpUrl)
            .putString("student_dp_preset", "none")
            .putString("google_user_id", googleId)
            .apply()

        if (!idToken.isNullOrEmpty()) {
            try {
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("PlannerViewModel", "Firebase Auth session connected via Google credential.")
                        } else {
                            Log.w("PlannerViewModel", "Firebase Google credential sign in note: ${task.exception?.message}")
                        }
                    }
            } catch (e: Exception) {
                Log.e("PlannerViewModel", "Error creating Google Auth credential: ${e.message}")
            }
        }

        viewModelScope.launch {
            addNotificationWithDuplicateCheck(
                InAppNotification(
                    title = "Signed in as $name",
                    message = "Successfully authenticated via Google ($email). Profile and schedule sync active.",
                    type = "alert"
                )
            )
        }
    }

    fun signInWithEmailAndPassword(email: String, password: String) {
        _isAuthenticating.value = true
        _authError.value = null
        
        com.google.firebase.auth.FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
                        
                        val name = user.displayName ?: sharedPrefs.getString("student_name", "") ?: email.substringBefore("@")
                        val photoUrl = user.photoUrl?.toString() ?: ""
                        val preset = sharedPrefs.getString("student_dp_preset", "doctor_male") ?: "doctor_male"
                        
                        _loginMode.value = LoginMode.FIREBASE
                        _studentName.value = name
                        _studentEmail.value = user.email ?: email
                        _studentDpUrl.value = photoUrl
                        _studentDpPreset.value = preset
                        _isAuthenticating.value = false
                        _authError.value = null
                        
                        sharedPrefs.edit()
                            .putString("login_mode", LoginMode.FIREBASE.name)
                            .putString("student_name", name)
                            .putString("student_email", user.email ?: email)
                            .putString("student_dp_url", photoUrl)
                            .putString("student_dp_preset", preset)
                            .apply()
                            
                        viewModelScope.launch {
                            addNotificationWithDuplicateCheck(
                                InAppNotification(
                                    title = "Signed in as $name",
                                    message = "Welcome back! Your academic profile is successfully synced.",
                                    type = "alert"
                                )
                            )
                        }
                    } else {
                        _isAuthenticating.value = false
                        _authError.value = "User session was empty"
                    }
                } else {
                    _isAuthenticating.value = false
                    _authError.value = task.exception?.localizedMessage ?: "Sign-in failed. Please verify credentials."
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
            addNotificationWithDuplicateCheck(
                InAppNotification(
                    title = "Signed Out",
                    message = "Your account was successfully signed out. All local offline academic planner data was safely preserved.",
                    type = "alert"
                )
            )
        }
    }

    fun saveUserProfile(name: String, college: String, course: String, year: String, semester: String, batch: String) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        val profile = UserProfile(
            uid = uid,
            fullName = name,
            college = college,
            course = course,
            year = year,
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
                .putString("student_year", year)
                .putString("student_semester", semester)
                .putString("student_batch", batch)
                .putBoolean("is_profile_completed", true)
                .apply()

            _studentName.value = name
            _studentCollege.value = college
            _studentYear.value = year
            _studentSemester.value = semester
            _studentBatch.value = batch
            _isProfileCompleted.value = true

            // Sync with Firebase Firestore
            try {
                if (uid != "local_user") {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection("users").document(uid).collection("profile").document("info")
                        .set(profile)
                    
                    // Backup everything as well on initial setup completion!
                    syncDataToFirebase()
                }
            } catch (e: Exception) {
                Log.e("PlannerViewModel", "Failed to sync profile to Firestore: ${e.message}", e)
            }
            checkAndNotifyTomorrowHoliday()
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

    fun syncDataToFirebase() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Sync Profile
                val profile = UserProfile(
                    uid = uid,
                    fullName = _studentName.value,
                    college = _studentCollege.value,
                    course = _selectedCourse.value?.code ?: "",
                    year = _studentYear.value,
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

                // Timetable Classes backup
                val classes = timetable.value
                classes.forEach { classItem ->
                    db.collection("users").document(uid).collection("timetable").document(classItem.id.toString())
                        .set(classItem)
                }

                // Attendance Records backup
                val attendance = allAttendanceRecords.value
                attendance.forEach { rec ->
                    db.collection("users").document(uid).collection("attendance").document(rec.id.toString())
                        .set(rec)
                }

                // Daily Revisions backup
                val revisions = allRevisions.value
                revisions.forEach { rev ->
                    db.collection("users").document(uid).collection("revisions").document(rev.id.toString())
                        .set(rev)
                }

                // Assignments backup
                val assignmentsList = assignments.value
                assignmentsList.forEach { asg ->
                    db.collection("users").document(uid).collection("assignments").document(asg.id.toString())
                        .set(asg)
                }

                // Exams backup
                val examsList = exams.value
                examsList.forEach { ex ->
                    db.collection("users").document(uid).collection("exams").document(ex.id.toString())
                        .set(ex)
                }

                // Planner backup
                val plannerList = plannerTasks.value
                plannerList.forEach { task ->
                    db.collection("users").document(uid).collection("planner").document(task.id.toString())
                        .set(task)
                }

                // Chat history backup
                val chatList = chatMessages.value
                chatList.forEach { chat ->
                    db.collection("users").document(uid).collection("chat_history").document(chat.id.toString())
                        .set(chat)
                }

                Log.d("FirestoreSync", "All user data successfully backed up to Firestore under UID: $uid")
            } catch (e: Exception) {
                Log.e("FirestoreSync", "Failed to backup user data to Firestore: ${e.message}", e)
            }
        }
    }

    fun restoreDataFromFirebase(onComplete: (Boolean) -> Unit = {}) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Restore Profile
                db.collection("users").document(uid).collection("profile").document("info")
                    .get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val name = doc.getString("fullName") ?: ""
                            val college = doc.getString("college") ?: ""
                            val courseCode = doc.getString("course") ?: ""
                            val year = doc.getString("year") ?: ""
                            val semester = doc.getString("semester") ?: ""
                            val batch = doc.getString("batch") ?: ""
                            
                            _studentName.value = name
                            _studentCollege.value = college
                            _studentYear.value = year
                            _studentSemester.value = semester
                            _studentBatch.value = batch
                            _isProfileCompleted.value = true
                            
                            if (courseCode.isNotEmpty()) {
                                try {
                                    val courseObj = MedicalCourse.valueOf(courseCode)
                                    _selectedCourse.value = courseObj
                                    
                                    val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
                                    sharedPrefs.edit()
                                        .putString("student_name", name)
                                        .putString("selected_course_code", courseObj.name)
                                        .putString("student_college", college)
                                        .putString("student_year", year)
                                        .putString("student_semester", semester)
                                        .putString("student_batch", batch)
                                        .putBoolean("is_profile_completed", true)
                                        .apply()
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                        }
                    }

                // 2. Restore Timetable
                db.collection("users").document(uid).collection("timetable")
                    .get()
                    .addOnSuccessListener { result ->
                        viewModelScope.launch(Dispatchers.IO) {
                            val list = mutableListOf<TimetableClass>()
                            for (doc in result) {
                                val c = doc.toObject(TimetableClass::class.java)
                                list.add(c)
                            }
                            if (list.isNotEmpty()) {
                                _selectedCourse.value?.code?.let { code ->
                                    repository.clearTimetable(code)
                                    list.forEach { repository.addClass(it) }
                                }
                            }
                        }
                    }

                // 3. Restore Attendance Records
                db.collection("users").document(uid).collection("attendance")
                    .get()
                    .addOnSuccessListener { result ->
                        viewModelScope.launch(Dispatchers.IO) {
                            val list = mutableListOf<AttendanceRecord>()
                            for (doc in result) {
                                val r = doc.toObject(AttendanceRecord::class.java)
                                list.add(r)
                            }
                            if (list.isNotEmpty()) {
                                list.forEach { repository.saveAttendanceRecord(it) }
                            }
                        }
                    }

                // 4. Restore Daily Revisions
                db.collection("users").document(uid).collection("revisions")
                    .get()
                    .addOnSuccessListener { result ->
                        viewModelScope.launch(Dispatchers.IO) {
                            val list = mutableListOf<DailySubjectRevision>()
                            for (doc in result) {
                                val r = doc.toObject(DailySubjectRevision::class.java)
                                list.add(r)
                            }
                            if (list.isNotEmpty()) {
                                list.forEach { repository.saveRevision(it) }
                            }
                        }
                    }

                // 5. Restore Assignments
                db.collection("users").document(uid).collection("assignments")
                    .get()
                    .addOnSuccessListener { result ->
                        viewModelScope.launch(Dispatchers.IO) {
                            for (doc in result) {
                                val a = doc.toObject(Assignment::class.java)
                                repository.addAssignment(a)
                            }
                        }
                    }

                // 6. Restore Exams
                db.collection("users").document(uid).collection("exams")
                    .get()
                    .addOnSuccessListener { result ->
                        viewModelScope.launch(Dispatchers.IO) {
                            for (doc in result) {
                                val ex = doc.toObject(Exam::class.java)
                                repository.addExam(ex)
                            }
                        }
                    }

                // 7. Restore Planner
                db.collection("users").document(uid).collection("planner")
                    .get()
                    .addOnSuccessListener { result ->
                        viewModelScope.launch(Dispatchers.IO) {
                            for (doc in result) {
                                val task = doc.toObject(PlannerTask::class.java)
                                repository.addPlannerTask(task)
                            }
                        }
                    }

                onComplete(true)
            } catch (e: Exception) {
                Log.e("FirestoreSync", "Restore failed: ${e.message}", e)
                onComplete(false)
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
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val actualDateString = dateString ?: sdf.format(java.util.Date())

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
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream, null, options) ?: return@launch

                val width = bitmap.width
                val height = bitmap.height
                val size = if (width < height) width else height
                val x = (width - size) / 2
                val y = (height - size) / 2
                val croppedBitmap = android.graphics.Bitmap.createBitmap(bitmap, x, y, size, size)

                val file = java.io.File(context.filesDir, "custom_profile_picture.jpg")
                val outputStream = java.io.FileOutputStream(file)
                croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
                outputStream.flush()
                outputStream.close()

                if (bitmap != croppedBitmap) {
                    bitmap.recycle()
                }
                croppedBitmap.recycle()

                val localPath = "file://" + file.absolutePath
                _studentDpUrl.value = localPath

                val sharedPrefs = context.getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
                sharedPrefs.edit().putString("student_dp_url", localPath).apply()
            } catch (e: Exception) {
                e.printStackTrace()
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
        // Store dismissed version to avoid repeatedly showing it
        val config = _cachedUpdateConfig.value
        if (config != null) {
            val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
            sharedPrefs.edit().putString("dismissed_update_version", config.latestVersion).apply()
        }
    }

    fun clearUpdateResult() {
        _updateResult.value = null
        _updateAvailable.value = false
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
                                _updateDownloadState.value = "Download complete! Opening installer..."
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


}

enum class Screen {
    Welcome,
    Dashboard,
    Timetable,
    Planner,
    Calendar,
    AIChat,
    Settings,
    Attendance
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
