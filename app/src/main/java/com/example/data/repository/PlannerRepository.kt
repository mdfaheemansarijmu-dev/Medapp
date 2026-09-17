package com.example.data.repository

import com.example.data.local.PlannerDao
import com.example.data.model.*
import com.example.data.sync.FirestoreSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlannerRepository(private val plannerDao: PlannerDao) {

    private val timetableMutex = Mutex()
    private val syncManager = FirestoreSyncManager(plannerDao)

    val cloudSyncActive: StateFlow<Boolean> = syncManager.isActive
    val cloudSyncError: StateFlow<String?> = syncManager.lastError

    fun startCloudSync(uid: String) {
        syncManager.startSync(uid)
    }

    fun stopCloudSync() {
        syncManager.stopSync()
    }

    suspend fun forceResyncCloud(uid: String) {
        syncManager.forceResync(uid)
    }

    // Timetable API
    fun getTimetable(courseCode: String): Flow<List<TimetableClass>> =
        plannerDao.getTimetableForCourse(courseCode)

    suspend fun addClass(classItem: TimetableClass) {
        plannerDao.insertClass(classItem)
        syncManager.pushTimetableClass(classItem)
    }

    suspend fun deleteClass(id: Int) {
        val firestoreId = plannerDao.getTimetableClassById(id)?.firestoreId
        plannerDao.deleteClassById(id)
        if (firestoreId != null) {
            syncManager.deleteTimetableClass(firestoreId)
        }
    }

    suspend fun clearTimetable(courseCode: String) {
        val items = plannerDao.getAllTimetableClassesOnce().filter { it.courseCode == courseCode }
        plannerDao.clearTimetableForCourse(courseCode)
        items.forEach {
            syncManager.deleteTimetableClass(it.firestoreId)
        }
    }

    // Assignments API
    fun getAssignments(courseCode: String): Flow<List<Assignment>> =
        plannerDao.getAssignmentsForCourse(courseCode)

    suspend fun addAssignment(assignment: Assignment): Long {
        val id = plannerDao.insertAssignment(assignment)
        syncManager.pushAssignment(assignment)
        return id
    }

    suspend fun deleteAssignment(id: Int) {
        val firestoreId = plannerDao.getAssignmentById(id)?.firestoreId
        plannerDao.deleteAssignmentById(id)
        if (firestoreId != null) {
            syncManager.deleteAssignment(firestoreId)
        }
    }

    suspend fun updateAssignmentStatus(id: Int, completed: Boolean) {
        val status = if (completed) "Completed" else "Pending"
        plannerDao.updateAssignmentStatus(id, status)
        plannerDao.getAssignmentById(id)?.let {
            syncManager.pushAssignment(it)
        }
    }

    // Assessments API
    fun getAssessments(courseCode: String): Flow<List<Assessment>> =
        plannerDao.getAssessmentsForCourse(courseCode)

    suspend fun addAssessment(assessment: Assessment): Long {
        val id = plannerDao.insertAssessment(assessment)
        syncManager.pushAssessment(assessment)
        return id
    }

    suspend fun deleteAssessment(id: Int) {
        val firestoreId = plannerDao.getAssessmentById(id)?.firestoreId
        plannerDao.deleteAssessmentById(id)
        if (firestoreId != null) {
            syncManager.deleteAssessment(firestoreId)
        }
    }

    suspend fun updateAssessmentStatus(id: Int, completed: Boolean) {
        val status = if (completed) "Completed" else "Upcoming"
        plannerDao.updateAssessmentStatus(id, status)
        plannerDao.getAssessmentById(id)?.let {
            syncManager.pushAssessment(it)
        }
    }

    // Study Tasks API
    fun getStudyTasks(courseCode: String): Flow<List<StudyTask>> =
        plannerDao.getStudyTasksForCourse(courseCode)

    suspend fun addStudyTask(task: StudyTask): Long {
        val id = plannerDao.insertStudyTask(task)
        syncManager.pushStudyTask(task)
        return id
    }

    suspend fun deleteStudyTask(id: Int) {
        val firestoreId = plannerDao.getStudyTaskById(id)?.firestoreId
        plannerDao.deleteStudyTaskById(id)
        if (firestoreId != null) {
            syncManager.deleteStudyTask(firestoreId)
        }
    }

    suspend fun updateStudyTaskProgress(id: Int, progress: Int) {
        plannerDao.updateStudyTaskProgress(id, progress)
        plannerDao.getStudyTaskById(id)?.let {
            syncManager.pushStudyTask(it)
        }
    }

    // Chat History API
    fun getChatMessages(): Flow<List<ChatMessage>> = plannerDao.getChatMessages()
    suspend fun addChatMessage(message: ChatMessage) = plannerDao.insertChatMessage(message)
    suspend fun clearChat() = plannerDao.clearChatHistory()

    // Notifications API
    fun getNotifications(): Flow<List<InAppNotification>> = plannerDao.getNotifications()
    suspend fun addNotification(notification: InAppNotification) =
        plannerDao.insertNotification(notification)
    suspend fun markNotificationRead(id: Int) = plannerDao.markNotificationAsRead(id)
    suspend fun clearAllNotifications() = plannerDao.clearNotifications()

    // Exams API
    fun getExams(courseCode: String): Flow<List<Exam>> = plannerDao.getExamsForCourse(courseCode)
    suspend fun addExam(exam: Exam) {
        plannerDao.insertExam(exam)
        syncManager.pushExam(exam)
    }

    suspend fun deleteExam(id: Int) {
        val firestoreId = plannerDao.getExamById(id)?.firestoreId
        plannerDao.deleteExamById(id)
        if (firestoreId != null) {
            syncManager.deleteExam(firestoreId)
        }
    }

    suspend fun clearExams(courseCode: String) {
        val items = plannerDao.getAllExamsOnce().filter { it.courseCode == courseCode }
        plannerDao.clearExamsForCourse(courseCode)
        items.forEach {
            syncManager.deleteExam(it.firestoreId)
        }
    }

    // Teachers API
    fun getTeachers(): Flow<List<Teacher>> = plannerDao.getAllTeachers()
    suspend fun addTeacher(teacher: Teacher) = plannerDao.insertTeacher(teacher)
    suspend fun deleteTeacher(name: String) = plannerDao.deleteTeacher(name)

    // Rooms API
    fun getRooms(): Flow<List<RoomEntity>> = plannerDao.getAllRooms()
    suspend fun addRoom(room: RoomEntity) = plannerDao.insertRoom(room)
    suspend fun deleteRoom(roomName: String) = plannerDao.deleteRoom(roomName)

    // Planner Tasks API
    fun getPlannerTasks(courseCode: String): Flow<List<PlannerTask>> = plannerDao.getPlannerTasksForCourse(courseCode)
    suspend fun addPlannerTask(task: PlannerTask) {
        plannerDao.insertPlannerTask(task)
        syncManager.pushPlannerTask(task)
    }

    suspend fun updatePlannerTaskStatus(id: Int, isCompleted: Boolean) {
        plannerDao.updatePlannerTaskStatus(id, if (isCompleted) 1 else 0)
        plannerDao.getPlannerTaskById(id)?.let {
            syncManager.pushPlannerTask(it)
        }
    }

    suspend fun deletePlannerTask(id: Int) {
        val firestoreId = plannerDao.getPlannerTaskById(id)?.firestoreId
        plannerDao.deletePlannerTaskById(id)
        if (firestoreId != null) {
            syncManager.deletePlannerTask(firestoreId)
        }
    }

    // Overrides API
    fun getOverrides(courseCode: String): Flow<List<ScheduleOverride>> = plannerDao.getOverridesForCourse(courseCode)
    suspend fun addOverride(override: ScheduleOverride) {
        plannerDao.insertOverride(override)
        syncManager.pushScheduleOverride(override)
    }

    suspend fun deleteOverride(id: Int) {
        val firestoreId = plannerDao.getScheduleOverrideById(id)?.firestoreId
        plannerDao.deleteOverrideById(id)
        if (firestoreId != null) {
            syncManager.deleteScheduleOverride(firestoreId)
        }
    }

    suspend fun clearOverrides(courseCode: String) {
        val items = plannerDao.getAllScheduleOverridesOnce().filter { it.courseCode == courseCode }
        plannerDao.clearOverridesForCourse(courseCode)
        items.forEach {
            syncManager.deleteScheduleOverride(it.firestoreId)
        }
    }

    // User Profile API
    fun getUserProfile(uid: String): Flow<UserProfile?> = plannerDao.getUserProfile(uid)
    suspend fun saveUserProfile(profile: UserProfile) = plannerDao.insertUserProfile(profile)
    suspend fun clearUserProfile() = plannerDao.clearUserProfiles()

    // Attendance Records API
    fun getAttendanceForDate(dateString: String): Flow<List<AttendanceRecord>> = plannerDao.getAttendanceForDate(dateString)
    fun getAllAttendance(): Flow<List<AttendanceRecord>> = plannerDao.getAllAttendance()

    suspend fun saveAttendanceRecord(record: AttendanceRecord) {
        plannerDao.insertAttendanceRecord(record)
        syncManager.pushAttendanceRecord(record)
    }

    suspend fun saveAttendanceRecords(records: List<AttendanceRecord>) {
        plannerDao.insertAttendanceRecords(records)
        records.forEach { syncManager.pushAttendanceRecord(it) }
    }

    suspend fun deleteAttendanceForDate(dateString: String) {
        val records = plannerDao.getAllAttendanceRecordsOnce().filter { it.dateString == dateString }
        plannerDao.deleteAttendanceForDate(dateString)
        records.forEach {
            syncManager.deleteAttendanceRecord(it.firestoreId)
        }
    }

    suspend fun deleteAttendanceRecord(record: AttendanceRecord) {
        plannerDao.deleteAttendanceRecordById(record.id)
        syncManager.deleteAttendanceRecord(record.firestoreId)
    }

    // Daily Subject Revisions API
    fun getRevisionsForDate(dateString: String): Flow<List<DailySubjectRevision>> = plannerDao.getRevisionsForDate(dateString)
    fun getAllRevisions(): Flow<List<DailySubjectRevision>> = plannerDao.getAllRevisions()
    suspend fun getRevisionForClass(dateString: String, subject: String, periodNumber: Int, classTime: String): DailySubjectRevision? =
        plannerDao.getRevisionForClass(dateString, subject, periodNumber, classTime)
    suspend fun saveRevision(revision: DailySubjectRevision) = plannerDao.insertRevision(revision)
    suspend fun deleteRevision(id: Int) = plannerDao.deleteRevisionById(id)


    // Pre-populate realistic weekly schedules for different courses
    suspend fun populateDefaultTimetableIfEmpty(courseCode: String) = timetableMutex.withLock {
        var currentTimetable = plannerDao.getTimetableForCourse(courseCode).first()
        if (courseCode == "BHMS" && !currentTimetable.any { it.subject == "Repertory / Materia Medica / Yoga" }) {
            plannerDao.clearTimetableForCourse(courseCode)
            currentTimetable = emptyList()
        }
        
        // Self-healing check for duplicate classes in database
        val uniqueCount = currentTimetable.distinctBy { "${it.dayOfWeek}_${it.periodNumber}_${it.subject}_${it.startTime}_${it.endTime}" }.size
        if (currentTimetable.size > uniqueCount) {
            plannerDao.clearTimetableForCourse(courseCode)
            currentTimetable = emptyList()
        }

        if (currentTimetable.isNotEmpty()) return // Already populated!

        val defaultClasses = createDefaultClassesForCourse(courseCode)
        plannerDao.insertClasses(defaultClasses)
    }

    private fun createDefaultClassesForCourse(courseCode: String): List<TimetableClass> {
        val classes = mutableListOf<TimetableClass>()
        
        if (courseCode == "BHMS") {
            // Govt. Homoeopathic Medical College, Thiruvananthapuram, I BHMS 2025 Batch (Effective From: 15 July 2026)
            class TempClass(val subject: String, val start: String, val end: String, val period: Int)
            val daySchedules = mapOf(
                1 to listOf(
                    TempClass("Repertory / Materia Medica / Yoga", "08:30 AM", "09:30 AM", 1),
                    TempClass("Anatomy", "09:30 AM", "10:30 AM", 2),
                    TempClass("Physiology (Non-Lecture)", "10:30 AM", "01:00 PM", 3),
                    TempClass("Lunch Break", "01:00 PM", "01:30 PM", 0),
                    TempClass("Physiology", "01:30 PM", "02:30 PM", 4),
                    TempClass("Anatomy", "02:30 PM", "03:30 PM", 5)
                ),
                2 to listOf(
                    TempClass("Physiology", "08:30 AM", "09:30 AM", 1),
                    TempClass("Pharmacy", "09:30 AM", "10:30 AM", 2),
                    TempClass("Anatomy (Non-Lecture)", "10:30 AM", "01:00 PM", 3),
                    TempClass("Lunch Break", "01:00 PM", "01:30 PM", 0),
                    TempClass("Anatomy", "01:30 PM", "02:30 PM", 4),
                    TempClass("Physiology", "02:30 PM", "03:30 PM", 5)
                ),
                3 to listOf(
                    TempClass("Clinical Posting", "08:00 AM", "10:30 AM", 1),
                    TempClass("Anatomy (Non-Lecture)", "10:30 AM", "01:00 PM", 3),
                    TempClass("Lunch Break", "01:00 PM", "01:30 PM", 0),
                    TempClass("Pharmacy (Non-Lecture)", "01:30 PM", "03:30 PM", 4)
                ),
                4 to listOf(
                    TempClass("Clinical Posting", "08:00 AM", "10:30 AM", 1),
                    TempClass("Physiology (Non-Lecture)", "10:30 AM", "01:00 PM", 3),
                    TempClass("Lunch Break", "01:00 PM", "01:30 PM", 0),
                    TempClass("Anatomy", "01:30 PM", "02:30 PM", 4),
                    TempClass("Physiology", "02:30 PM", "03:30 PM", 5)
                ),
                5 to listOf(
                    TempClass("Organon", "08:30 AM", "09:30 AM", 1),
                    TempClass("Physiology", "09:30 AM", "10:30 AM", 2),
                    TempClass("Physiology (Non-Lecture)", "10:30 AM", "01:00 PM", 3),
                    TempClass("Lunch Break", "01:00 PM", "01:30 PM", 0),
                    TempClass("Anatomy", "01:30 PM", "02:30 PM", 4),
                    TempClass("Pharmacy", "02:30 PM", "03:30 PM", 5)
                ),
                6 to listOf(
                    TempClass("Materia Medica", "08:30 AM", "09:30 AM", 1),
                    TempClass("Organon", "09:30 AM", "10:30 AM", 2),
                    TempClass("Anatomy (Non-Lecture)", "10:30 AM", "01:00 PM", 3),
                    TempClass("Lunch Break", "01:00 PM", "01:30 PM", 0),
                    TempClass("Physiology", "01:30 PM", "02:30 PM", 4),
                    TempClass("Anatomy", "02:30 PM", "03:30 PM", 5)
                )
            )

            for ((day, periods) in daySchedules) {
                periods.forEachIndexed { index, item ->
                    val subject = item.subject
                    val start = item.start
                    val end = item.end
                    val periodNum = item.period
                    val color = when {
                        subject.contains("Materia") -> "#0D9488" // Teal
                        subject.contains("Anatomy (Non-Lecture)") -> "#4F46E5" // Deep indigo
                        subject.contains("Anatomy") -> "#6366F1" // Indigo
                        subject.contains("Physiology (Non-Lecture)") -> "#0369A1" // Dark sky blue
                        subject.contains("Physiology") -> "#0284C7" // Sky blue
                        subject.contains("Organon") -> "#D97706" // Amber
                        subject.contains("Pharmacy (Non-Lecture)") -> "#7C3AED" // Deep purple
                        subject.contains("Pharmacy") -> "#8B5CF6" // Purple
                        subject.contains("Clinical") -> "#EC4899" // Pink / Rose
                        subject.contains("Repertory") || subject.contains("Yoga") -> "#10B981" // Emerald
                        subject.contains("Lunch") -> "#78716C" // Stone (neutral)
                        else -> "#64748B"
                    }
                    val isLab = subject.contains("Non-Lecture") || subject.contains("Yoga") || subject.contains("Break") || subject.contains("Clinical") || subject.contains("Posting")
                    val room = when {
                        subject == "Lunch Break" -> "Cafeteria"
                        subject.contains("Clinical") -> "Clinical Ward / OPD"
                        subject.contains("Anatomy") && isLab -> "Dissection Hall"
                        subject.contains("Physiology") && isLab -> "Physiology Lab"
                        subject.contains("Pharmacy") && isLab -> "Pharmacy Lab"
                        subject.contains("Yoga") -> "Yoga/Recreation Hall"
                        else -> "Lecture Hall (I BHMS)"
                    }
                    val teacher = when {
                        subject.contains("Clinical") -> "Dr. S. Nair"
                        subject.contains("Anatomy") -> "Dr. Sharma"
                        subject.contains("Physiology") -> "Dr. Verma"
                        subject.contains("Materia") -> "Dr. Hahnemann"
                        subject.contains("Organon") -> "Dr. Kent"
                        subject.contains("Pharmacy") -> "Dr. Mehta"
                        subject.contains("Yoga") -> "Yogi Gupta"
                        else -> "N/A"
                    }
                    
                    classes.add(
                        TimetableClass(
                            courseCode = "BHMS",
                            dayOfWeek = day,
                            periodNumber = if (periodNum == 0) index + 1 else periodNum,
                            subject = subject,
                            startTime = start,
                            endTime = end,
                            room = room,
                            teacherName = if (subject == "Lunch Break") null else teacher,
                            colorHex = color
                        )
                    )
                }
            }
            return classes
        }

        // Days 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat
        val subjects = when (courseCode) {
            "BHMS" -> listOf(
                "Materia Medica",
                "Organon of Medicine",
                "Homeopathic Pharmacy",
                "Anatomy Lab",
                "Physiology",
                "Pathology"
            )
            "MBBS" -> listOf(
                "Anatomy Lecture",
                "Physiology",
                "Biochemistry",
                "Pathology Lab",
                "Pharmacology",
                "Community Medicine"
            )
            "BDS" -> listOf(
                "Dental Anatomy",
                "Oral Histology",
                "General Anatomy",
                "Physiology & Biochemistry",
                "Dental Materials",
                "Preclinical Prosthodontics"
            )
            "BAMS" -> listOf(
                "Kriya Sharir (Physiology)",
                "Rachana Sharir (Anatomy)",
                "Padartha Vigyan",
                "Sanskrit",
                "Ashtanga Hridaya",
                "Maulika Siddhanta"
            )
            "NURSING" -> listOf(
                "Nursing Foundations",
                "Nutrition & Dietetics",
                "Anatomy & Physiology",
                "Psychology",
                "Microbiology",
                "Clinical Practicum"
            )
            "PHARMACY" -> listOf(
                "Pharmaceutics",
                "Pharmaceutical Chemistry",
                "Pharmacognosy",
                "Human Anatomy & Physiology",
                "Pharmaceutical Analysis",
                "Dispensing Lab"
            )
            else -> listOf("General Medicine", "Clinical Rotation", "Surgical Seminar", "Ward Round")
        }

        // Generate a standard weekly calendar
        val colors = listOf("#4F46E5", "#06B6D4", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6")
        val periods = listOf(
            Triple("09:00 AM", "10:00 AM", 1),
            Triple("10:05 AM", "11:05 AM", 2),
            Triple("11:10 AM", "12:10 PM", 3),
            Triple("01:00 PM", "02:00 PM", 4),
            Triple("02:05 PM", "04:00 PM", 5) // Usually lab or practical
        )

        for (day in 1..6) { // Monday to Saturday
            val maxPeriod = if (day == 6) 3 else 5 // Saturday has half day
            for (pIdx in 0 until maxPeriod) {
                val period = periods[pIdx]
                // Pick subjects cyclically with a shuffle offset depending on the day
                val subIndex = (pIdx + day) % subjects.size
                val subjectName = subjects[subIndex]
                val isLab = subjectName.lowercase().contains("lab") || subjectName.lowercase().contains("clinical") || subjectName.lowercase().contains("practicum")
                
                classes.add(
                    TimetableClass(
                        courseCode = courseCode,
                        dayOfWeek = day,
                        periodNumber = period.third,
                        subject = subjectName,
                        startTime = period.first,
                        endTime = period.second,
                        room = if (isLab) "Clinical Lab 2" else "Lecture Hall ${if (day % 2 == 0) "B" else "A"}",
                        teacherName = "Dr. " + when (subjectName.take(5)) {
                            "Anato" -> "Sharma"
                            "Physi" -> "Verma"
                            "Mater" -> "Hahnemann"
                            "Organ" -> "Kent"
                            "Bioch" -> "Reddy"
                            "Pharm" -> "Mehta"
                            "Patho" -> "Joshi"
                            else -> "Gupta"
                        },
                        colorHex = colors[subIndex % colors.size]
                    )
                )
            }
        }
        return classes
    }

    fun getDefaultParsedTimetableForCourse(courseCode: String): List<com.example.data.model.ParsedTimetableClass> {
        val classes = createDefaultClassesForCourse(courseCode)
        return classes.map { cls ->
            com.example.data.model.ParsedTimetableClass(
                day_of_week = cls.dayOfWeek,
                period_number = cls.periodNumber,
                start_time = cls.startTime,
                end_time = cls.endTime,
                subject = cls.subject,
                teacher_name = cls.teacherName,
                room = cls.room,
                is_practical = cls.subject.contains("Lab", ignoreCase = true) || cls.subject.contains("Practical", ignoreCase = true) || cls.subject.contains("Non-Lecture", ignoreCase = true) || cls.subject.contains("Clinical", ignoreCase = true),
                is_lunch_break = cls.subject.contains("Lunch", ignoreCase = true),
                confidence = "High",
                is_uncertain = false,
                notes = "Pre-loaded course curriculum",
                batch = "All Batches",
                department = cls.subject.split(" ").firstOrNull() ?: "General"
            )
        }
    }
}
