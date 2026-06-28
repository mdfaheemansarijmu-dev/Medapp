package com.example.data.repository

import com.example.data.local.PlannerDao
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PlannerRepository(private val plannerDao: PlannerDao) {

    // Timetable API
    fun getTimetable(courseCode: String): Flow<List<TimetableClass>> =
        plannerDao.getTimetableForCourse(courseCode)

    suspend fun addClass(classItem: TimetableClass) = plannerDao.insertClass(classItem)
    suspend fun deleteClass(id: Int) = plannerDao.deleteClassById(id)
    suspend fun clearTimetable(courseCode: String) = plannerDao.clearTimetableForCourse(courseCode)

    // Assignments API
    fun getAssignments(courseCode: String): Flow<List<Assignment>> =
        plannerDao.getAssignmentsForCourse(courseCode)

    suspend fun addAssignment(assignment: Assignment): Long = plannerDao.insertAssignment(assignment)
    suspend fun deleteAssignment(id: Int) = plannerDao.deleteAssignmentById(id)
    suspend fun updateAssignmentStatus(id: Int, completed: Boolean) {
        val status = if (completed) "Completed" else "Pending"
        plannerDao.updateAssignmentStatus(id, status)
    }

    // Assessments API
    fun getAssessments(courseCode: String): Flow<List<Assessment>> =
        plannerDao.getAssessmentsForCourse(courseCode)

    suspend fun addAssessment(assessment: Assessment): Long = plannerDao.insertAssessment(assessment)
    suspend fun deleteAssessment(id: Int) = plannerDao.deleteAssessmentById(id)
    suspend fun updateAssessmentStatus(id: Int, completed: Boolean) {
        val status = if (completed) "Completed" else "Upcoming"
        plannerDao.updateAssessmentStatus(id, status)
    }

    // Study Tasks API
    fun getStudyTasks(courseCode: String): Flow<List<StudyTask>> =
        plannerDao.getStudyTasksForCourse(courseCode)

    suspend fun addStudyTask(task: StudyTask): Long = plannerDao.insertStudyTask(task)
    suspend fun deleteStudyTask(id: Int) = plannerDao.deleteStudyTaskById(id)
    suspend fun updateStudyTaskProgress(id: Int, progress: Int) =
        plannerDao.updateStudyTaskProgress(id, progress)

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
    suspend fun addExam(exam: Exam) = plannerDao.insertExam(exam)
    suspend fun deleteExam(id: Int) = plannerDao.deleteExamById(id)
    suspend fun clearExams(courseCode: String) = plannerDao.clearExamsForCourse(courseCode)

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
    suspend fun addPlannerTask(task: PlannerTask) = plannerDao.insertPlannerTask(task)
    suspend fun updatePlannerTaskStatus(id: Int, isCompleted: Boolean) = plannerDao.updatePlannerTaskStatus(id, if (isCompleted) 1 else 0)
    suspend fun deletePlannerTask(id: Int) = plannerDao.deletePlannerTaskById(id)

    // Overrides API
    fun getOverrides(courseCode: String): Flow<List<ScheduleOverride>> = plannerDao.getOverridesForCourse(courseCode)
    suspend fun addOverride(override: ScheduleOverride) = plannerDao.insertOverride(override)
    suspend fun deleteOverride(id: Int) = plannerDao.deleteOverrideById(id)
    suspend fun clearOverrides(courseCode: String) = plannerDao.clearOverridesForCourse(courseCode)


    // Pre-populate realistic weekly schedules for different courses
    suspend fun populateDefaultTimetableIfEmpty(courseCode: String) {
        var currentTimetable = plannerDao.getTimetableForCourse(courseCode).first()
        if (courseCode == "BHMS" && (currentTimetable.any { it.subject == "Pathology" } || currentTimetable.any { it.startTime == "09:00 AM" })) {
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
            // Government Homoeopathic Medical College, Thiruvananthapuram, I BHMS 2025 Batch Time Table (w.e.f. 24-11-2025)
            val daySchedules = mapOf(
                1 to listOf(
                    Triple("Materia Medica", "08:30 AM", "09:30 AM"),
                    Triple("Anatomy", "09:30 AM", "10:30 AM"),
                    Triple("Physiology (Non-Lecture)", "10:30 AM", "01:00 PM"),
                    Triple("Lunch Break", "01:00 PM", "01:30 PM"),
                    Triple("Physiology", "01:30 PM", "02:30 PM"),
                    Triple("Repertory / Yoga", "02:30 PM", "03:30 PM")
                ),
                2 to listOf(
                    Triple("Organon", "08:30 AM", "09:30 AM"),
                    Triple("Pharmacy", "09:30 AM", "10:30 AM"),
                    Triple("Anatomy (Non-Lecture)", "10:30 AM", "01:00 PM"),
                    Triple("Lunch Break", "01:00 PM", "01:30 PM"),
                    Triple("Anatomy", "01:30 PM", "02:30 PM"),
                    Triple("Physiology", "02:30 PM", "03:30 PM")
                ),
                3 to listOf(
                    Triple("Physiology", "08:30 AM", "09:30 AM"),
                    Triple("Anatomy", "09:30 AM", "10:30 AM"),
                    Triple("Anatomy (Non-Lecture)", "10:30 AM", "01:00 PM"),
                    Triple("Lunch Break", "01:00 PM", "01:30 PM"),
                    Triple("Pharmacy (Non-Lecture)", "01:30 PM", "03:30 PM")
                ),
                4 to listOf(
                    Triple("Organon", "08:30 AM", "09:30 AM"),
                    Triple("Materia Medica", "09:30 AM", "10:30 AM"),
                    Triple("Physiology (Non-Lecture)", "10:30 AM", "01:00 PM"),
                    Triple("Lunch Break", "01:00 PM", "01:30 PM"),
                    Triple("Anatomy", "01:30 PM", "02:30 PM"),
                    Triple("Physiology", "02:30 PM", "03:30 PM")
                ),
                5 to listOf(
                    Triple("Organon", "08:30 AM", "09:30 AM"),
                    Triple("Physiology", "09:30 AM", "10:30 AM"),
                    Triple("Physiology (Non-Lecture)", "10:30 AM", "01:00 PM"),
                    Triple("Lunch Break", "01:00 PM", "01:30 PM"),
                    Triple("Anatomy", "01:30 PM", "02:30 PM"),
                    Triple("Pharmacy", "02:30 PM", "03:30 PM")
                ),
                6 to listOf(
                    Triple("Materia Medica", "08:30 AM", "09:30 AM"),
                    Triple("Organon", "09:30 AM", "10:30 AM"),
                    Triple("Anatomy (Non-Lecture)", "10:30 AM", "01:00 PM"),
                    Triple("Lunch Break", "01:00 PM", "01:30 PM"),
                    Triple("Physiology", "01:30 PM", "02:30 PM"),
                    Triple("Anatomy", "02:30 PM", "03:30 PM")
                )
            )

            for ((day, periods) in daySchedules) {
                periods.forEachIndexed { index, (subject, start, end) ->
                    val color = when {
                        subject.contains("Materia") -> "#0D9488" // Teal
                        subject.contains("Anatomy (Non-Lecture)") -> "#4F46E5" // Deep indigo
                        subject.contains("Anatomy") -> "#6366F1" // Indigo
                        subject.contains("Physiology (Non-Lecture)") -> "#0369A1" // Dark sky blue
                        subject.contains("Physiology") -> "#0284C7" // Sky blue
                        subject.contains("Organon") -> "#D97706" // Amber
                        subject.contains("Pharmacy (Non-Lecture)") -> "#7C3AED" // Deep purple
                        subject.contains("Pharmacy") -> "#8B5CF6" // Purple
                        subject.contains("Repertory") || subject.contains("Yoga") -> "#10B981" // Emerald
                        subject.contains("Lunch") -> "#78716C" // Stone (neutral)
                        else -> "#64748B"
                    }
                    val isLab = subject.contains("Non-Lecture") || subject.contains("Yoga") || subject.contains("Break")
                    val room = when {
                        subject == "Lunch Break" -> "Cafeteria"
                        subject.contains("Anatomy") && isLab -> "Dissection Hall"
                        subject.contains("Physiology") && isLab -> "Physiology Lab"
                        subject.contains("Pharmacy") && isLab -> "Pharmacy Lab"
                        subject.contains("Yoga") -> "Yoga/Recreation Hall"
                        else -> "Lecture Hall (I BHMS)"
                    }
                    val teacher = when {
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
                            periodNumber = index + 1,
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
}
