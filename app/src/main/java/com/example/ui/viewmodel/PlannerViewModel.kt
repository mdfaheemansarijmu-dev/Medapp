package com.example.ui.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PlannerDatabase
import com.example.data.model.*
import com.example.data.repository.PlannerRepository
import com.example.network.GeminiParserService
import com.example.network.RetrofitClient
import com.example.network.UpdateService
import com.example.network.UpdateServiceImpl
import com.example.network.AppUpdateResult
import com.example.network.AppUpdateConfig
import com.squareup.moshi.Types
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
            if (course != null) repository.getTimetable(course.code)
            else flowOf(emptyList())
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
    private val _studentName = MutableStateFlow("Med Student")
    val studentName: StateFlow<String> = _studentName.asStateFlow()

    private val _studentDpUrl = MutableStateFlow("")
    val studentDpUrl: StateFlow<String> = _studentDpUrl.asStateFlow()

    private val _studentDpPreset = MutableStateFlow("doctor_male")
    val studentDpPreset: StateFlow<String> = _studentDpPreset.asStateFlow()

    // Login and Account states
    private val _loginMode = MutableStateFlow(LoginMode.UNDECIDED)
    val loginMode: StateFlow<LoginMode> = _loginMode.asStateFlow()

    private val _studentEmail = MutableStateFlow("")
    val studentEmail: StateFlow<String> = _studentEmail.asStateFlow()

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

    private val _lastCheckedTime = MutableStateFlow(0L)
    val lastCheckedTime: StateFlow<Long> = _lastCheckedTime.asStateFlow()

    private val _cachedUpdateConfig = MutableStateFlow<AppUpdateConfig?>(null)
    val cachedUpdateConfig: StateFlow<AppUpdateConfig?> = _cachedUpdateConfig.asStateFlow()

    private val _isUpdateDialogDismissed = MutableStateFlow(false)
    val isUpdateDialogDismissed: StateFlow<Boolean> = _isUpdateDialogDismissed.asStateFlow()

    private val geminiService = GeminiParserService()

    init {
        // Read stored course preference from local preferences if any
        val sharedPrefs = application.getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        _isDarkTheme.value = sharedPrefs.getBoolean("is_dark_theme", false)
        _areNotificationsEnabled.value = sharedPrefs.getBoolean("are_notifications_enabled", true)
        _studentName.value = sharedPrefs.getString("student_name", "Med Student") ?: "Med Student"
        _studentDpUrl.value = sharedPrefs.getString("student_dp_url", "") ?: ""
        _studentDpPreset.value = sharedPrefs.getString("student_dp_preset", "doctor_male") ?: "doctor_male"
        val savedLoginMode = sharedPrefs.getString("login_mode", LoginMode.UNDECIDED.name) ?: LoginMode.UNDECIDED.name
        _loginMode.value = try { LoginMode.valueOf(savedLoginMode) } catch (e: Exception) { LoginMode.UNDECIDED }
        _studentEmail.value = sharedPrefs.getString("student_email", "") ?: ""
        val savedCourseCode = sharedPrefs.getString("selected_course_code", null)
        if (savedCourseCode != null) {
            try {
                val course = MedicalCourse.valueOf(savedCourseCode)
                _selectedCourse.value = course
                _currentScreen.value = Screen.Dashboard
                viewModelScope.launch {
                    repository.populateDefaultTimetableIfEmpty(course.code)
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
            while (true) {
                _currentTimeOfDay.value = format.format(Date())
                _currentCalendarDate.value = Calendar.getInstance()
                kotlinx.coroutines.delay(10000) // Update every 10 seconds
            }
        }

        // Load cached update details
        _lastCheckedTime.value = updateService.getLastCheckedTime(application)
        _cachedUpdateConfig.value = updateService.getCachedUpdateInfo(application)

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
            repository.addNotification(
                InAppNotification(
                    title = "Course Selected: ${course.displayName}",
                    message = "Your standard weekly medical timetable has been pre-loaded! Go to the 'Timetable' tab to customize it.",
                    type = "class"
                )
            )

            // Auto navigate to dashboard
            _currentScreen.value = Screen.Dashboard
            generateSmartNotifications()
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
        return try {
            val clean = timeStr.trim().uppercase()
            val parts = clean.split(" ")
            val timeParts = parts[0].split(":")
            var hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()
            val amPm = parts[1]
            if (amPm == "PM" && hour != 12) hour += 12
            if (amPm == "AM" && hour == 12) hour = 0
            hour * 60 + minute
        } catch (e: Exception) {
            0
        }
    }

    fun getTimetableForDay(dayOfWeek: Int): List<TimetableClass> {
        return timetable.value.filter { it.dayOfWeek == dayOfWeek }
    }

    // Extract: 1. Current Running Class, 2. Next Class, 3. Remaining Classes
    data class DayClassSchedule(
        val currentClass: TimetableClass? = null,
        val nextClass: TimetableClass? = null,
        val remainingClasses: List<TimetableClass> = emptyList()
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
            remainingClasses = remaining
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
        }
    }

    fun removeTimetableClass(id: Int) {
        viewModelScope.launch {
            repository.deleteClass(id)
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
            repository.addAssignment(assignment)
            generateSmartNotifications()
            scheduleReminder(assignment.hashCode(), dueDate, "Assignment Due", "Assignment $title is due soon!")
        }
    }

    fun toggleAssignment(assignment: Assignment) {
        viewModelScope.launch {
            repository.updateAssignmentStatus(assignment.id, assignment.status != "Completed")
        }
    }

    fun removeAssignment(id: Int) {
        viewModelScope.launch {
            repository.deleteAssignment(id)
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
            repository.addAssessment(assessment)
            generateSmartNotifications()
            scheduleReminder(assessment.hashCode(), date, "Assessment Due", "Assessment $title is due soon!")
        }
    }

    fun toggleAssessment(assessment: Assessment) {
        viewModelScope.launch {
            repository.updateAssessmentStatus(assessment.id, assessment.status != "Completed")
        }
    }

    fun removeAssessment(id: Int) {
        viewModelScope.launch {
            repository.deleteAssessment(id)
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
            repository.addStudyTask(task)
            scheduleReminder(task.hashCode(), dueDate, "Study Task Due", "Study Task $title is due soon!")
        }
    }

    fun updateStudyProgress(id: Int, progress: Int) {
        viewModelScope.launch {
            repository.updateStudyTaskProgress(id, progress)
        }
    }

    fun removeStudyTask(id: Int) {
        viewModelScope.launch {
            repository.deleteStudyTask(id)
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
                val response = geminiService.parseDocument(textInput, imageBytes, mimeType)
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

                val aiResponseText = if (response.document_type == "Weekly Timetable") {
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
            repository.addNotification(
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

                when (item.category) {
                    "Class Cancellation", "Room Change", "Teacher Change", "Schedule Change" -> {
                        val isCancelled = item.category == "Class Cancellation"
                        repository.addOverride(
                            ScheduleOverride(
                                courseCode = course.code,
                                dateString = dateStr,
                                subject = item.subject,
                                startTime = "09:00 AM", // Proposed slot
                                endTime = "10:00 AM",
                                room = if (item.category == "Room Change") item.details?.substringAfter("to ")?.substringBefore(" ")?.trim() else "Lecture Hall B",
                                teacherName = if (item.category == "Teacher Change") item.details?.substringAfter("Dr. ")?.substringBefore(" ")?.trim()?.let { "Dr. $it" } else null,
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
                    }
                    "Exam" -> {
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
                        // Add companion planner task
                        repository.addPlannerTask(
                            PlannerTask(
                                courseCode = course.code,
                                title = "Exam Study: ${item.title}",
                                date = itemTimestamp,
                                category = "Exam"
                            )
                        )
                    }
                    "Assignment", "Homework", "Practical", "Lab", "Seminar" -> {
                        repository.addAssignment(
                            Assignment(
                                courseCode = course.code,
                                subject = item.subject,
                                title = item.title,
                                dueDate = itemTimestamp,
                                priority = item.priority,
                                status = "Pending",
                                type = item.category,
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
                    }
                    "Assessment", "Viva" -> {
                        repository.addAssessment(
                            Assessment(
                                courseCode = course.code,
                                subject = item.subject,
                                title = item.title,
                                date = itemTimestamp,
                                type = item.category,
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
                    }
                    "Study Task" -> {
                        repository.addStudyTask(
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
                    }
                    else -> {
                        // General Reminders/Notices mapped as a pending general Assignment or Notification alert
                        repository.addAssignment(
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
                repository.addNotification(
                    InAppNotification(
                        title = "Assignment Due Tomorrow",
                        message = "${asg.subject}: '${asg.title}' is due in less than 24 hours!",
                        type = "assignment"
                    )
                )
                if (_areNotificationsEnabled.value) {
                    triggerBrowserPush(
                        title = "Upcoming Assignment Alert",
                        message = "Assignment '${asg.title}' for ${asg.subject} is due in less than 24 hours!",
                        iconType = "assignment"
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
                repository.addNotification(
                    InAppNotification(
                        title = "Upcoming Assessment Reminder",
                        message = "${asm.subject}: '${asm.title}' is scheduled in $daysLeft day(s)!",
                        type = "exam"
                    )
                )
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
                triggerBrowserPush(
                    title = "Upcoming Lecture Today",
                    message = "${nextClass.subject} starts at ${nextClass.startTime} in ${nextClass.room ?: "the Lecture Hall"}.",
                    iconType = "lecture"
                )
            }
        } else if (tomorrowClasses.isNotEmpty()) {
            val firstClassTomorrow = tomorrowClasses.sortedBy { parseTimeToMinutes(it.startTime) }.first()
            if (_areNotificationsEnabled.value) {
                triggerBrowserPush(
                    title = "Lecture Scheduled Tomorrow",
                    message = "First class tomorrow is ${firstClassTomorrow.subject} starting at ${firstClassTomorrow.startTime}.",
                    iconType = "lecture"
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
        _studentName.value = name.ifBlank { "Med Student" }
        _studentDpUrl.value = dpUrl
        _studentDpPreset.value = dpPreset

        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("student_name", name.ifBlank { "Med Student" })
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

    fun signInWithGoogle(name: String, email: String, dpUrl: String) {
        _loginMode.value = LoginMode.GOOGLE
        _studentName.value = name
        _studentEmail.value = email
        _studentDpUrl.value = dpUrl
        _studentDpPreset.value = "none"

        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("login_mode", LoginMode.GOOGLE.name)
            .putString("student_name", name)
            .putString("student_email", email)
            .putString("student_dp_url", dpUrl)
            .putString("student_dp_preset", "none")
            .apply()

        viewModelScope.launch {
            repository.addNotification(
                InAppNotification(
                    title = "Signed in as $name",
                    message = "Your account is now securely linked locally. Cloud sync readiness active.",
                    type = "alert"
                )
            )
        }
    }

    fun signOut() {
        _loginMode.value = LoginMode.GUEST
        _studentName.value = "Med Student"
        _studentEmail.value = ""
        _studentDpUrl.value = ""
        _studentDpPreset.value = "doctor_male"

        val sharedPrefs = getApplication<Application>().getSharedPreferences("med_planner_prefs", Application.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("login_mode", LoginMode.GUEST.name)
            .putString("student_name", "Med Student")
            .putString("student_email", "")
            .putString("student_dp_url", "")
            .putString("student_dp_preset", "doctor_male")
            .apply()

        viewModelScope.launch {
            repository.addNotification(
                InAppNotification(
                    title = "Disconnected Google Account",
                    message = "Google account successfully signed out. All local offline academic planner data was safely preserved.",
                    type = "alert"
                )
            )
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
                triggerLocalSystemNotification(
                    "Academic Alerts Enabled", 
                    "You will now receive notifications for assignments and lectures due within 24 hours."
                )
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

    fun scheduleReminder(id: Int, time: Long, title: String, message: String) {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, com.example.receivers.NotificationReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("id", id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule 30 minutes before
        val triggerTime = time - (30 * 60 * 1000)
        if (triggerTime > System.currentTimeMillis()) {
            try {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } catch (e: SecurityException) {
                Log.e("PlannerViewModel", "Failed to schedule alarm", e)
            }
        }
    }

    fun triggerBrowserPush(title: String, message: String, iconType: String) {
        if (_areNotificationsEnabled.value) {
            triggerLocalSystemNotification(title, message)
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
            _cachedUpdateConfig.value = updateService.getCachedUpdateInfo(getApplication())
            
            _updateCheckInProgress.value = false

            if (!silent) {
                _isUpdateDialogDismissed.value = false
            }
        }
    }

    fun dismissUpdateDialog() {
        _isUpdateDialogDismissed.value = true
    }

    fun clearUpdateResult() {
        _updateResult.value = null
    }

    fun simulateUpdate(force: Boolean) {
        val mockConfig = AppUpdateConfig(
            latestVersion = "1.0.2",
            minimumSupportedVersion = if (force) "1.0.2" else "1.0.0",
            updateTitle = if (force) "🚀 Critical Update Required" else "🚀 New Update Available",
            updateMessage = "Important security fixes, AI assistant improvements, local database synchronization, and class scheduler enhancements.",
            downloadUrl = "https://ais-dev-famqmclmfe6gdf3uf2vkyk-1079547613754.asia-southeast1.run.app",
            forceUpdate = force
        )
        _updateResult.value = AppUpdateResult.UpdateAvailable(mockConfig, force)
        _cachedUpdateConfig.value = mockConfig
        _isUpdateDialogDismissed.value = false
    }
}

enum class Screen {
    Welcome,
    Dashboard,
    Timetable,
    Planner,
    Calendar,
    AIChat,
    Settings
}

enum class LoginMode {
    UNDECIDED,
    GUEST,
    GOOGLE
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
