package com.example.domain.ai

import android.content.Context
import android.util.Log
import com.example.data.model.*
import com.example.data.repository.PlannerRepository
import com.example.data.syllabus.CourseSyllabusDirectory
import com.example.util.AcademicDateParser
import com.example.util.AttendanceTimeValidator
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.*

/**
 * AI App Action Controller: Gives Tendon & MedPulse AI full access to read and modify
 * everything in the app by natural language or voice prompts, while strictly distinguishing
 * between informational queries and explicit scheduling commands.
 */
class AIAppActionController(
    private val repository: PlannerRepository,
    private val context: Context?
) {

    data class ActionResult(
        val isHandled: Boolean,
        val feedbackMessage: String,
        val actionCategory: String = "general",
        val itemsScheduled: List<ParsedItem> = emptyList()
    )

    /**
     * Inspects the prompt and executes real app queries or modifications.
     */
    suspend fun executeActionIfCommand(
        prompt: String,
        currentCourse: MedicalCourse,
        studentName: String,
        currentYear: String
    ): ActionResult {
        val trimmed = prompt.trim()
        val lower = trimmed.lowercase(Locale.US)

        // If it's a multi-point pasted WhatsApp list (e.g. "1. ... 2. ..."), let the multi-item notice parser handle it
        val hasMultipleNumberedPoints = (lower.contains("1.") || lower.contains("1)")) &&
                (lower.contains("2.") || lower.contains("2)"))
        if (hasMultipleNumberedPoints || trimmed.lines().count { it.isNotBlank() } >= 3) {
            return ActionResult(isHandled = false, feedbackMessage = "")
        }

        // 0. TENDON CONVERSATIONAL GREETINGS & IDENTITY
        if (isTendonGreetingOrIdentity(lower)) {
            val greetName = studentName.ifBlank { "Doctor" }
            return ActionResult(
                isHandled = true,
                feedbackMessage = "🤖 Hello $greetName! I am **Tendon**, your voice-enabled medical academic robot.\n\n" +
                        "You can ask me or instruct me anytime:\n" +
                        "• *\"What is today's running assignment?\"*\n" +
                        "• *\"What assessments do I have?\"*\n" +
                        "• *\"What classes do I have today?\"*\n" +
                        "• *\"We have Anatomy assessment on 24th October at 10 AM\"*\n" +
                        "• *\"Mark me present for all classes today\"*",
                actionCategory = "greeting"
            )
        }

        // 1. UNDO / DELETE MISTAKEN ASSESSMENT OR ASSIGNMENT
        if (isDeleteOrUndoCommand(lower)) {
            return handleDeleteOrUndoAction(lower, currentCourse)
        }

        // 2. REAL-TIME DATA QUERIES (Checked BEFORE scheduling so questions about assessments/assignments NEVER schedule by mistake!)
        if (isAppQuery(lower)) {
            return handleAppQuery(lower, currentCourse, currentYear)
        }

        // 3. ATTENDANCE ACTIONS (e.g. "mark me present for all classes today")
        if (isAttendanceCommand(lower)) {
            return handleAttendanceAction(lower, trimmed, currentCourse)
        }

        // 4. SYLLABUS FINISHED / COVERED ACTIONS (e.g. "mark Upper Limb finished in Anatomy")
        if (isSyllabusCommand(lower)) {
            return handleSyllabusAction(lower, trimmed, currentCourse, studentName, currentYear)
        }

        // 5. SCHEDULE OVERRIDE / CLASS CANCELLATION ACTIONS
        if (isClassCancellationCommand(lower)) {
            return handleClassCancellation(lower, trimmed, currentCourse)
        }

        // 6. TIMETABLE CLASS ADD / DELETE ACTIONS
        if (isTimetableModificationCommand(lower)) {
            return handleTimetableModification(lower, trimmed, currentCourse, studentName)
        }

        // 7. SINGLE ASSESSMENT SCHEDULING ACTION (Requires explicit scheduling intent + explicit date!)
        if (isSingleAssessmentScheduleCommand(lower, trimmed)) {
            return handleSingleAssessmentScheduling(lower, trimmed, currentCourse)
        }

        // 8. SINGLE ASSIGNMENT SCHEDULING ACTION (Requires explicit scheduling intent + explicit date!)
        if (isSingleAssignmentScheduleCommand(lower, trimmed)) {
            return handleSingleAssignmentScheduling(lower, trimmed, currentCourse)
        }

        // 9. HOLIDAY / COLLEGE CLOSURE COMMAND
        if (isHolidayCommand(lower, trimmed)) {
            return handleHolidayScheduling(lower, trimmed, currentCourse)
        }

        // 10. GENERAL EVENT / SEMINAR / CLINICAL POSTING BY VOICE (Requires explicit date!)
        if (isGeneralEventCommand(lower, trimmed)) {
            return handleGeneralEventScheduling(lower, trimmed, currentCourse)
        }

        return ActionResult(isHandled = false, feedbackMessage = "")
    }

    private fun isTendonGreetingOrIdentity(lower: String): Boolean {
        val clean = lower.replace(Regex("[^a-z\\s]"), "").trim()
        return clean in listOf(
            "hi", "hello", "hey", "hi tendon", "hello tendon", "hey tendon",
            "who are you", "what is your name", "what can you do", "help", "help me", "how are you"
        )
    }

    // -------------------------------------------------------------
    // 1. UNDO / DELETE MISTAKEN ITEM
    // -------------------------------------------------------------
    private fun isDeleteOrUndoCommand(lower: String): Boolean {
        if (lower == "undo" || lower.contains("by mistake") || lower.contains("mistakenly")) return true
        val hasDeleteWord = lower.contains("delete") || lower.contains("remove") || lower.contains("undo") || lower.contains("clear last")
        val hasTarget = lower.contains("assessment") || lower.contains("assignment") || lower.contains("exam") || lower.contains("test") || lower.contains("last")
        return hasDeleteWord && hasTarget
    }

    private suspend fun handleDeleteOrUndoAction(
        lower: String,
        course: MedicalCourse
    ): ActionResult {
        val subject = detectSubject(lower)

        if (lower.contains("assignment")) {
            val allAsg = repository.getAllAssignmentsOnce()
            val target = if (subject != "General") {
                allAsg.lastOrNull { it.subject.equals(subject, ignoreCase = true) }
            } else {
                allAsg.lastOrNull()
            }
            if (target != null) {
                repository.deleteAssignment(target.id)
                return ActionResult(
                    isHandled = true,
                    feedbackMessage = "🗑️ Removed assignment **${target.title}** (${target.subject}) from your Planner.",
                    actionCategory = "delete"
                )
            }
        }

        val allAsm = repository.getAllAssessmentsOnce()
        val targetAsm = if (subject != "General") {
            allAsm.lastOrNull { it.subject.equals(subject, ignoreCase = true) }
        } else {
            // Prefer removing any generic/mistakenly added assessment first, otherwise the latest one
            allAsm.lastOrNull {
                it.subject.equals("Medical Sciences", ignoreCase = true) ||
                it.subject.equals("General", ignoreCase = true) ||
                it.title.equals("Internal Assessment", ignoreCase = true)
            } ?: allAsm.lastOrNull()
        }

        if (targetAsm != null) {
            repository.deleteAssessment(targetAsm.id)
            return ActionResult(
                isHandled = true,
                feedbackMessage = "🗑️ Deleted assessment **${targetAsm.title}** (${targetAsm.subject}) from your Planner and Calendar.",
                actionCategory = "delete"
            )
        }

        return ActionResult(
            isHandled = true,
            feedbackMessage = "ℹ️ There are no recent assessments or assignments to remove.",
            actionCategory = "delete"
        )
    }

    // -------------------------------------------------------------
    // 2. REAL-TIME APP QUERIES (Assignments, Assessments, Classes, Attendance, Syllabus)
    // -------------------------------------------------------------
    private fun isAppQuery(lower: String): Boolean {
        val clean = lower.replace(Regex("^(hey tendon|hi tendon|hello tendon|tendon)[,\\s]*"), "").trim()

        // If user explicitly says "schedule ...", "add ...", "create ...", "mark me present/absent", "mark ... finished", it's an action, not a query
        val startsWithExplicitAction = clean.startsWith("schedule ") ||
                clean.startsWith("add ") ||
                clean.startsWith("create ") ||
                clean.startsWith("mark ") ||
                clean.startsWith("cancel ") ||
                clean.startsWith("postpone ")
        if (startsWithExplicitAction) return false

        // Explicit question starters or query phrases
        val hasQuestionWord = clean.contains("?") ||
                clean.startsWith("what") ||
                clean.startsWith("when") ||
                clean.startsWith("which") ||
                clean.startsWith("how") ||
                clean.startsWith("where") ||
                clean.startsWith("is there") ||
                clean.startsWith("are there") ||
                clean.startsWith("do i") ||
                clean.startsWith("do we") ||
                clean.startsWith("any ") ||
                clean.startsWith("check") ||
                clean.startsWith("show") ||
                clean.startsWith("list") ||
                clean.startsWith("tell") ||
                clean.startsWith("give") ||
                clean.contains("what is") ||
                clean.contains("what are") ||
                clean.contains("when is") ||
                clean.contains("when are") ||
                clean.contains("tell me") ||
                clean.contains("show me") ||
                clean.contains("running") ||
                clean.contains("pending") ||
                clean.contains("upcoming") ||
                clean.contains("due today") ||
                clean.contains("today's") ||
                clean.contains("todays") ||
                clean.contains("my assignment") ||
                clean.contains("my assessment") ||
                clean.contains("my exam") ||
                clean.contains("my attendance") ||
                clean.contains("my class") ||
                clean.contains("my schedule") ||
                clean.contains("my routine") ||
                clean.contains("about assessment") ||
                clean.contains("about assignment") ||
                clean.contains("about exam")

        if (hasQuestionWord) return true

        // Also if the user just says "assessments", "assessment", "assignments", "assignment", "exams", "attendance", "classes today" without a specific date to schedule
        val dateCheck = AcademicDateParser.parse(clean)
        val hasSchedulingPhrase = clean.contains("we have") || clean.contains("i have") || clean.contains("there is")
        if (!hasSchedulingPhrase && !dateCheck.hasExplicitDate) {
            if (clean.contains("assignment") || clean.contains("homework") ||
                clean.contains("assessment") || clean.contains("exam") || clean.contains("viva") ||
                clean.contains("attendance") || clean.contains("syllabus") || clean.contains("classes")
            ) {
                return true
            }
        }

        return false
    }

    private suspend fun handleAppQuery(
        lower: String,
        course: MedicalCourse,
        currentYear: String
    ): ActionResult {
        // A. ASSIGNMENTS / RUNNING ASSIGNMENTS / HOMEWORK / SUBMISSIONS QUERY
        if (lower.contains("assignment") || lower.contains("homework") ||
            lower.contains("record") || lower.contains("logbook") ||
            lower.contains("submission") || lower.contains("journal") ||
            (lower.contains("running") && !lower.contains("class")) ||
            (lower.contains("pending") && !lower.contains("assessment"))
        ) {
            return handleAssignmentQuery(lower, course)
        }

        // B. ASSESSMENTS / EXAMS / VIVAS / TESTS QUERY
        if (Regex("\\b(assessment|assessments|exam|exams|viva|vivas|test|tests|quiz|quizzes|midterm)\\b").containsMatchIn(lower)) {
            return handleAssessmentQuery(lower, course)
        }

        // C. ATTENDANCE QUERY
        if (lower.contains("attendance")) {
            val allRecords = repository.getAllAttendance().firstOrNull() ?: emptyList()
            if (allRecords.isEmpty()) {
                return ActionResult(
                    isHandled = true,
                    feedbackMessage = "📊 You have no attendance records logged yet. You can tell me: *\"Mark me present for all classes today\"* to log your attendance.",
                    actionCategory = "query"
                )
            }
            val total = allRecords.size
            val presentCount = allRecords.count { it.isPresent }
            val pct = (presentCount * 100) / total

            val bySubject = allRecords.groupBy { it.subject }.map { (sub, recs) ->
                val p = recs.count { it.isPresent }
                val rate = (p * 100) / recs.size
                "• **$sub:** $rate% ($p/${recs.size} sessions)"
            }.joinToString("\n")

            return ActionResult(
                isHandled = true,
                feedbackMessage = "📊 **Your Attendance Overview:**\n\n" +
                        "🏆 Overall Attendance: **$pct%** ($presentCount / $total sessions)\n\n" +
                        "**Subject Breakdown:**\n$bySubject\n\n" +
                        if (pct >= 75) "✅ You meet the 75% university eligibility requirement!"
                        else "⚠️ Attention: Your attendance is below the 75% university requirement.",
                actionCategory = "query"
            )
        }

        // D. SYLLABUS / FINISHED TOPICS QUERY
        if (lower.contains("syllabus") || lower.contains("finished") || lower.contains("covered") || lower.contains("topic")) {
            val completed = repository.getCompletedTopicsForYear(course.code, currentYear).firstOrNull() ?: emptyList()
            val standardSubjects = CourseSyllabusDirectory.getSubjectsForYear(course.code, currentYear)
            val totalSuggested = standardSubjects.sumOf { it.suggestedTopics.size }

            val breakdown = standardSubjects.joinToString("\n") { sub ->
                val done = completed.count { it.subject.equals(sub.name, ignoreCase = true) }
                "• **${sub.name}:** $done / ${sub.suggestedTopics.size} covered"
            }

            return ActionResult(
                isHandled = true,
                feedbackMessage = "📚 **Syllabus Completion Overview ($currentYear):**\n\n" +
                        "✓ Total Topics Finished: **${completed.size}** / $totalSuggested\n\n" +
                        "**By Subject:**\n$breakdown\n\n" +
                        "Check your **Finished** tab for the complete chapter registry.",
                actionCategory = "query"
            )
        }

        // E. CLASSES / SCHEDULE TODAY OR TOMORROW QUERY
        val targetCal = Calendar.getInstance(AcademicDateParser.TIMEZONE)
        val isTomorrow = lower.contains("tomorrow")
        if (isTomorrow) targetCal.add(Calendar.DAY_OF_YEAR, 1)

        val dayOfWeek = when (targetCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        val dayName = when (dayOfWeek) {
            1 -> "Monday"; 2 -> "Tuesday"; 3 -> "Wednesday"; 4 -> "Thursday"; 5 -> "Friday"; 6 -> "Saturday"; else -> "Sunday"
        }

        val timetable = repository.getTimetable(course.code).firstOrNull() ?: emptyList()
        val classes = timetable.filter { it.dayOfWeek == dayOfWeek }.sortedBy { it.periodNumber }

        if (classes.isEmpty()) {
            return ActionResult(
                isHandled = true,
                feedbackMessage = "🗓️ No classes are scheduled for **$dayName** (${if (isTomorrow) "Tomorrow" else "Today"}). Enjoy your study or rest day!",
                actionCategory = "query"
            )
        }

        val listStr = classes.joinToString("\n") { cls ->
            val icon = if (cls.subject.contains("lunch", ignoreCase = true) || cls.subject.contains("break", ignoreCase = true)) "🍱"
            else if (cls.subject.contains("practical", ignoreCase = true) || cls.subject.contains("lab", ignoreCase = true) || cls.subject.contains("clinic", ignoreCase = true)) "🔬"
            else "📖"
            "$icon **${cls.startTime} - ${cls.endTime}**: ${cls.subject} (${cls.room ?: "Hall"})${if (!cls.teacherName.isNullOrBlank()) " • ${cls.teacherName}" else ""}"
        }

        return ActionResult(
            isHandled = true,
            feedbackMessage = "🗓️ **Classes for $dayName (${if (isTomorrow) "Tomorrow" else "Today"}):**\n\n$listStr\n\nTotal: ${classes.size} sessions.",
            actionCategory = "query"
        )
    }

    /**
     * Checks both course-specific and all assignments in the database, filters running/pending items,
     * and highlights today's items if the user asked about today's running assignments.
     */
    private suspend fun handleAssignmentQuery(
        lower: String,
        course: MedicalCourse
    ): ActionResult {
        val courseAssignments = repository.getAssignments(course.code).firstOrNull() ?: emptyList()
        val allAssignments = if (courseAssignments.isNotEmpty()) {
            courseAssignments
        } else {
            repository.getAllAssignmentsOnce()
        }

        val studyTasks = repository.getStudyTasks(course.code).firstOrNull() ?: emptyList()
        val activeAssignments = allAssignments.filter { !it.status.equals("Completed", ignoreCase = true) }.sortedBy { it.dueDate }
        val completedAssignments = allAssignments.filter { it.status.equals("Completed", ignoreCase = true) }

        val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val todayStr = sdfDate.format(Date())

        val dueToday = activeAssignments.filter { sdfDate.format(Date(it.dueDate)) == todayStr }
        val askingToday = lower.contains("today") || lower.contains("todays") || lower.contains("running")

        if (activeAssignments.isEmpty() && studyTasks.none { it.progress < 100 }) {
            val completedNote = if (completedAssignments.isNotEmpty()) {
                " You have ${completedAssignments.size} completed assignment(s) in your Planner."
            } else ""
            return ActionResult(
                isHandled = true,
                feedbackMessage = "📝 You currently have **no pending or running assignments** in your Planner!$completedNote\n\n" +
                        "To add one by voice, say: *\"We have Physiology assignment due on Friday\"*.",
                actionCategory = "query"
            )
        }

        val sb = StringBuilder()
        if (askingToday) {
            if (dueToday.isNotEmpty()) {
                sb.append("📝 **Today's Running Assignments (${dueToday.size} due today):**\n\n")
                dueToday.forEachIndexed { idx, asg ->
                    sb.append("${idx + 1}. **${asg.title}** (${asg.subject})\n")
                    sb.append("   • Status: Running / Pending • Priority: ${asg.priority}\n\n")
                }
            } else {
                sb.append("📝 You have **no assignments strictly due today ($todayStr)**, but you have **${activeAssignments.size} running assignment${if (activeAssignments.size == 1) "" else "s"}** in your Planner:\n\n")
            }
        } else {
            sb.append("📝 **Your Running / Pending Assignments (${activeAssignments.size}):**\n\n")
        }

        val listToShow = if (askingToday && dueToday.isNotEmpty()) {
            activeAssignments.filter { it !in dueToday }
        } else {
            activeAssignments
        }

        if (listToShow.isNotEmpty()) {
            if (askingToday && dueToday.isNotEmpty()) {
                sb.append("**Other Running Assignments:**\n")
            }
            listToShow.forEachIndexed { idx, asg ->
                val dueFormatted = sdfDate.format(Date(asg.dueDate))
                sb.append("${idx + 1}. **${asg.title}** (${asg.subject})\n")
                sb.append("   • Due Date: **$dueFormatted** • Priority: ${asg.priority}\n\n")
            }
        }

        return ActionResult(
            isHandled = true,
            feedbackMessage = sb.toString().trim(),
            actionCategory = "query"
        )
    }

    /**
     * Checks both course-specific and all assessments & exams in the database and replies with full details.
     */
    private suspend fun handleAssessmentQuery(
        lower: String,
        course: MedicalCourse
    ): ActionResult {
        val courseAssessments = repository.getAssessments(course.code).firstOrNull() ?: emptyList()
        val allAssessments = if (courseAssessments.isNotEmpty()) {
            courseAssessments
        } else {
            repository.getAllAssessmentsOnce()
        }

        val allExams = repository.getExams(course.code).firstOrNull() ?: emptyList()

        // Show all non-completed assessments sorted by date
        val activeAssessments = allAssessments
            .filter { !it.status.equals("Completed", ignoreCase = true) }
            .sortedBy { it.date }

        val sdf = SimpleDateFormat("EEEE, dd MMM yyyy 'at' hh:mm a", Locale.US)
        val sdfShort = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val todayStr = sdfShort.format(Date())

        if (activeAssessments.isEmpty() && allExams.isEmpty()) {
            return ActionResult(
                isHandled = true,
                feedbackMessage = "📅 Checked your Planner and Calendar: You have **no upcoming assessments, vivas, or exams** scheduled right now.\n\n" +
                        "To schedule one by voice, say: *\"We have Anatomy assessment on 24th October at 10 AM\"*.",
                actionCategory = "query"
            )
        }

        val sb = StringBuilder()
        val todayAssessments = activeAssessments.filter { sdfShort.format(Date(it.date)) == todayStr }

        if ((lower.contains("today") || lower.contains("todays")) && todayAssessments.isNotEmpty()) {
            sb.append("📅 **Today's Scheduled Assessments (${todayAssessments.size}):**\n\n")
            todayAssessments.forEachIndexed { idx, asm ->
                sb.append("${idx + 1}. 📌 **${asm.title}** (${asm.subject})\n")
                sb.append("   • Date: **${sdf.format(Date(asm.date))}**\n")
                sb.append("   • Type: ${asm.type}${if (!asm.syllabus.isNullOrBlank()) "\n   • Syllabus: ${asm.syllabus}" else ""}\n\n")
            }
        } else {
            sb.append("📅 **Your Scheduled Assessments & Exams (${activeAssessments.size}):**\n\n")
            activeAssessments.forEachIndexed { idx, asm ->
                sb.append("${idx + 1}. 📌 **${asm.title}** (${asm.subject})\n")
                sb.append("   • Date: **${sdf.format(Date(asm.date))}**\n")
                sb.append("   • Type: ${asm.type}${if (!asm.syllabus.isNullOrBlank()) "\n   • Syllabus: ${asm.syllabus}" else ""}\n\n")
            }
        }

        if (allExams.isNotEmpty() && activeAssessments.isEmpty()) {
            allExams.forEachIndexed { idx, ex ->
                sb.append("${idx + 1}. 📌 **${ex.title}** (${ex.subject}) on **${sdfShort.format(Date(ex.date))}**${if (!ex.time.isNullOrBlank()) " at ${ex.time}" else ""}\n\n")
            }
        }

        return ActionResult(
            isHandled = true,
            feedbackMessage = sb.toString().trim(),
            actionCategory = "query"
        )
    }

    // -------------------------------------------------------------
    // 3. ATTENDANCE
    // -------------------------------------------------------------
    private fun isAttendanceCommand(lower: String): Boolean {
        return (lower.contains("attendance") || lower.contains("mark me") || lower.contains("mark present") || lower.contains("mark absent") || lower.contains("marked present") || lower.contains("marked absent")) &&
                (lower.contains("present") || lower.contains("absent") || lower.contains("all classes"))
    }

    private suspend fun handleAttendanceAction(
        lower: String,
        rawPrompt: String,
        course: MedicalCourse
    ): ActionResult {
        val dateResult = AcademicDateParser.parse(rawPrompt)
        val targetDateStr = if (dateResult.hasExplicitDate) dateResult.dateString else AttendanceTimeValidator.getTodayDateString()

        val isPresent = !lower.contains("absent")
        val status = if (isPresent) "PRESENT" else "ABSENT"

        val timetableClasses = repository.getTimetable(course.code).firstOrNull() ?: emptyList()

        val targetCal = Calendar.getInstance(AcademicDateParser.TIMEZONE).apply {
            time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(targetDateStr) ?: Date()
        }
        val dayOfWeek = when (targetCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        val classesForDay = timetableClasses.filter {
            it.dayOfWeek == dayOfWeek &&
            !it.subject.contains("lunch", ignoreCase = true) &&
            !it.subject.contains("recess", ignoreCase = true)
        }

        if (lower.contains("all class") || lower.contains("all lectures") || lower.contains("everything")) {
            if (classesForDay.isEmpty()) {
                return ActionResult(
                    isHandled = true,
                    feedbackMessage = "ℹ️ No scheduled classes found on **${dateResult.readableDate}** to mark attendance.",
                    actionCategory = "attendance"
                )
            }

            for (cls in classesForDay) {
                val record = AttendanceRecord(
                    dateString = targetDateStr,
                    subject = cls.subject,
                    isPresent = isPresent,
                    classTime = "${cls.startTime} - ${cls.endTime}",
                    status = status,
                    startTime = cls.startTime,
                    endTime = cls.endTime,
                    recordedTimestamp = System.currentTimeMillis()
                )
                repository.saveAttendanceRecord(record)
            }

            val actionWord = if (isPresent) "Present" else "Absent"
            return ActionResult(
                isHandled = true,
                feedbackMessage = "✅ **Marked $actionWord for all ${classesForDay.size} classes** on **${dateResult.readableDate}**:\n" +
                        classesForDay.joinToString("\n") { "• ${it.subject} (${it.startTime} - ${it.endTime})" } +
                        "\n\n📊 Your attendance register and Calendar have been updated.",
                actionCategory = "attendance"
            )
        }

        val subject = detectSubject(lower)
        if (subject == "General") {
            if (classesForDay.size == 1) {
                val cls = classesForDay.first()
                val record = AttendanceRecord(
                    dateString = targetDateStr,
                    subject = cls.subject,
                    isPresent = isPresent,
                    classTime = "${cls.startTime} - ${cls.endTime}",
                    status = status,
                    startTime = cls.startTime,
                    endTime = cls.endTime,
                    recordedTimestamp = System.currentTimeMillis()
                )
                repository.saveAttendanceRecord(record)
                return ActionResult(
                    isHandled = true,
                    feedbackMessage = "✅ Marked **${if (isPresent) "Present" else "Absent"}** for **${cls.subject}** (${cls.startTime} - ${cls.endTime}) on **${dateResult.readableDate}**.",
                    actionCategory = "attendance"
                )
            }
            return ActionResult(
                isHandled = true,
                feedbackMessage = "Please specify which subject you want to mark. Today's classes are:\n" +
                        classesForDay.joinToString("\n") { "• **${it.subject}** (${it.startTime} - ${it.endTime})" },
                actionCategory = "attendance"
            )
        }

        val matchedClass = classesForDay.find { it.subject.contains(subject, ignoreCase = true) }
        val classTime = if (matchedClass != null) "${matchedClass.startTime} - ${matchedClass.endTime}" else null
        val startTime = matchedClass?.startTime

        val record = AttendanceRecord(
            dateString = targetDateStr,
            subject = matchedClass?.subject ?: subject,
            isPresent = isPresent,
            classTime = classTime,
            status = status,
            startTime = startTime,
            endTime = matchedClass?.endTime,
            recordedTimestamp = System.currentTimeMillis()
        )
        repository.saveAttendanceRecord(record)

        val actionWord = if (isPresent) "Present" else "Absent"
        return ActionResult(
            isHandled = true,
            feedbackMessage = "✅ Marked **$actionWord** for **${matchedClass?.subject ?: subject}** on **${dateResult.readableDate}**${if (classTime != null) " ($classTime)" else ""}.\n\nUpdated in your Attendance tab and Calendar.",
            actionCategory = "attendance"
        )
    }

    // -------------------------------------------------------------
    // 4. SYLLABUS / FINISHED TAB
    // -------------------------------------------------------------
    private fun isSyllabusCommand(lower: String): Boolean {
        return (lower.contains("syllabus") || lower.contains("chapter") || lower.contains("topic") || isMedicalSubjectMentioned(lower)) &&
                (lower.contains("mark ") || lower.contains("completed") || lower.contains("covered") || lower.contains("finished")) &&
                !lower.contains("attendance") && !lower.contains("what") && !lower.contains("how many")
    }

    private suspend fun handleSyllabusAction(
        lower: String,
        rawPrompt: String,
        course: MedicalCourse,
        studentName: String,
        currentYear: String
    ): ActionResult {
        val subject = detectSubject(lower)
        val effectiveSubject = if (subject != "General") subject else {
            CourseSyllabusDirectory.getSubjectsForYear(course.code, currentYear).firstOrNull()?.name ?: "Anatomy"
        }

        val standardSubjects = CourseSyllabusDirectory.getSubjectsForYear(course.code, currentYear)
        val subjectDef = standardSubjects.find { it.name.equals(effectiveSubject, ignoreCase = true) }

        val matchedStandardTopic = subjectDef?.suggestedTopics?.find { topic ->
            lower.contains(topic.lowercase(Locale.US))
        }

        val extractedTopic = matchedStandardTopic ?: extractTopicTitle(rawPrompt, effectiveSubject)

        val completedTopic = CompletedSyllabusTopic(
            courseCode = course.code,
            academicYear = currentYear,
            subject = effectiveSubject,
            topicTitle = extractedTopic,
            completionDate = System.currentTimeMillis(),
            authorName = studentName.ifBlank { "Student" },
            notes = "Marked covered via Tendon AI"
        )
        repository.addCompletedTopic(completedTopic)

        return ActionResult(
            isHandled = true,
            feedbackMessage = "✅ **Syllabus Updated!**\n\n" +
                    "Marked **$extractedTopic** under **$effectiveSubject** ($currentYear) as **Finished**.\n\n" +
                    "📚 Your progress bar in the **Finished** tab has been updated.",
            actionCategory = "syllabus"
        )
    }

    // -------------------------------------------------------------
    // 5. CLASS CANCELLATION / SCHEDULE OVERRIDE
    // -------------------------------------------------------------
    private fun isClassCancellationCommand(lower: String): Boolean {
        return (lower.contains("cancel") || lower.contains("postpone") || lower.contains("no class") || lower.contains("shifted to")) &&
                (lower.contains("class") || lower.contains("lecture") || lower.contains("practical") || isMedicalSubjectMentioned(lower)) &&
                !lower.contains("assessment") && !lower.contains("exam")
    }

    private suspend fun handleClassCancellation(
        lower: String,
        rawPrompt: String,
        course: MedicalCourse
    ): ActionResult {
        val dateResult = AcademicDateParser.parse(rawPrompt)
        val subject = detectSubject(lower)
        val effectiveSubject = if (subject != "General") subject else "Scheduled Lecture"
        val isCancelled = lower.contains("cancel") || lower.contains("no class") || lower.contains("postpone")

        val overrideItem = ScheduleOverride(
            courseCode = course.code,
            dateString = dateResult.dateString,
            subject = effectiveSubject,
            startTime = dateResult.timeString.ifBlank { "09:00 AM" },
            endTime = "10:00 AM",
            room = if (lower.contains("hall b")) "Lecture Hall B" else "Lecture Hall A",
            teacherName = null,
            isCancelled = isCancelled
        )
        repository.addOverride(overrideItem)

        repository.addPlannerTask(
            PlannerTask(
                courseCode = course.code,
                title = if (isCancelled) "$effectiveSubject Class Cancelled" else "$effectiveSubject Rescheduled",
                date = dateResult.timeInMillis,
                category = "Schedule Override"
            )
        )

        return ActionResult(
            isHandled = true,
            feedbackMessage = "✅ **Schedule Override Applied!**\n\n" +
                    "• **Subject:** $effectiveSubject\n" +
                    "• **Action:** ${if (isCancelled) "Cancelled" else "Shifted / Updated"}\n" +
                    "• **Date:** ${dateResult.readableDate}\n\n" +
                    "Updated on your Calendar and Timetable.",
            actionCategory = "override"
        )
    }

    // -------------------------------------------------------------
    // 6. TIMETABLE MODIFICATION (Add / Delete Class)
    // -------------------------------------------------------------
    private fun isTimetableModificationCommand(lower: String): Boolean {
        return (lower.contains("add class") || lower.contains("add lecture") || lower.contains("add a class") ||
                lower.contains("schedule class") || lower.contains("delete class") || lower.contains("remove class")) &&
                !lower.contains("assessment") && !lower.contains("assignment")
    }

    private suspend fun handleTimetableModification(
        lower: String,
        rawPrompt: String,
        course: MedicalCourse,
        studentName: String
    ): ActionResult {
        val subject = detectSubject(lower)
        val dateResult = AcademicDateParser.parse(rawPrompt)
        val cal = Calendar.getInstance(AcademicDateParser.TIMEZONE).apply { timeInMillis = dateResult.timeInMillis }
        val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        val dayName = when (dayOfWeek) {
            1 -> "Monday"; 2 -> "Tuesday"; 3 -> "Wednesday"; 4 -> "Thursday"; 5 -> "Friday"; 6 -> "Saturday"; else -> "Sunday"
        }

        if (lower.contains("delete") || lower.contains("remove")) {
            val classes = repository.getTimetable(course.code).firstOrNull() ?: emptyList()
            val match = classes.find {
                it.dayOfWeek == dayOfWeek && (subject == "General" || it.subject.contains(subject, ignoreCase = true))
            }
            if (match != null) {
                repository.deleteClass(match.id)
                return ActionResult(
                    isHandled = true,
                    feedbackMessage = "✅ Removed **${match.subject}** (${match.startTime}) on **$dayName** from your timetable.",
                    actionCategory = "timetable"
                )
            }
            return ActionResult(
                isHandled = true,
                feedbackMessage = "ℹ️ Could not find a class matching '$subject' on $dayName to remove.",
                actionCategory = "timetable"
            )
        }

        val startTime = dateResult.timeString.ifBlank { "10:00 AM" }
        val endTime = "11:00 AM"
        val room = if (lower.contains("hall b")) "Lecture Hall B" else if (lower.contains("hall a")) "Lecture Hall A" else "Lecture Hall 1"

        val newClass = TimetableClass(
            courseCode = course.code,
            dayOfWeek = dayOfWeek,
            periodNumber = 2,
            subject = if (subject != "General") subject else "Clinical Medicine",
            startTime = startTime,
            endTime = endTime,
            room = room,
            authorName = studentName.ifBlank { "Student" }
        )
        repository.addClass(newClass)

        return ActionResult(
            isHandled = true,
            feedbackMessage = "✅ Added **${newClass.subject}** on **$dayName** ($startTime - $endTime) in **$room** to your weekly timetable.",
            actionCategory = "timetable"
        )
    }

    // -------------------------------------------------------------
    // 7. SCHEDULE SINGLE ASSESSMENT DIRECTLY (Strict: requires explicit scheduling intent + explicit date!)
    // -------------------------------------------------------------
    private fun isSingleAssessmentScheduleCommand(lower: String, rawPrompt: String): Boolean {
        val hasAssessmentKeyword = Regex("\\b(assessment|test|exam|viva|quiz|midterm)\\b").containsMatchIn(lower)
        if (!hasAssessmentKeyword) return false

        // Must have an explicit scheduling trigger
        val hasExplicitTrigger = lower.contains("schedule") ||
                lower.contains("add ") ||
                lower.contains("set ") ||
                lower.contains("create ") ||
                lower.contains("we have") ||
                lower.contains("i have") ||
                lower.contains("there is a") ||
                lower.contains("there is an")

        // Must have an explicit date (e.g., "24th Oct", "tomorrow", "Friday", "24/10/2026") so random speech never schedules an assessment!
        val parsedDate = AcademicDateParser.parse(rawPrompt)
        return hasExplicitTrigger && parsedDate.hasExplicitDate
    }

    private suspend fun handleSingleAssessmentScheduling(
        lower: String,
        rawPrompt: String,
        course: MedicalCourse
    ): ActionResult {
        val subject = detectSubject(lower)
        val effectiveSubject = if (subject != "General") subject else "General Medical"
        val dateResult = AcademicDateParser.parse(rawPrompt)

        val type = when {
            lower.contains("viva") -> "Viva"
            lower.contains("quiz") -> "Quiz"
            lower.contains("exam") -> "University Exam"
            else -> "Internal Assessment"
        }

        val title = if (subject != "General") "$effectiveSubject $type" else type

        val assessment = Assessment(
            courseCode = course.code,
            subject = effectiveSubject,
            title = title,
            date = dateResult.timeInMillis,
            type = type,
            status = "Upcoming",
            syllabus = rawPrompt.trim()
        )
        repository.addAssessment(assessment)

        repository.addPlannerTask(
            PlannerTask(
                courseCode = course.code,
                title = "Prepare for $title",
                date = dateResult.timeInMillis,
                category = "Assessment"
            )
        )

        repository.addNotification(
            InAppNotification(
                title = "Assessment Scheduled by Tendon",
                message = "$title on ${dateResult.readableDate} at ${dateResult.timeString}.",
                type = "exam"
            )
        )

        val item = ParsedItem(
            category = "Assessment",
            subject = effectiveSubject,
            title = title,
            due_date_description = "${dateResult.dateString} ${dateResult.timeString}",
            priority = "High",
            details = "Scheduled on ${dateResult.readableDate} at ${dateResult.timeString}"
        )

        return ActionResult(
            isHandled = true,
            feedbackMessage = "✅ **Scheduled $title** on **${dateResult.readableDate}** at **${dateResult.timeString}**!\n\n" +
                    "📅 **Date:** ${dateResult.readableDate}\n" +
                    "⏰ **Time:** ${dateResult.timeString}\n" +
                    "📖 **Subject:** $effectiveSubject\n" +
                    "📌 Saved directly to your Calendar and Planner. (Say *\"Undo\"* if scheduled by mistake.)",
            actionCategory = "assessment",
            itemsScheduled = listOf(item)
        )
    }

    // -------------------------------------------------------------
    // 8. SCHEDULE SINGLE ASSIGNMENT DIRECTLY (Strict: requires explicit scheduling intent + explicit date!)
    // -------------------------------------------------------------
    private fun isSingleAssignmentScheduleCommand(lower: String, rawPrompt: String): Boolean {
        val hasAssignKeyword = Regex("\\b(assignment|homework|submission|record book|logbook|journal)\\b").containsMatchIn(lower)
        if (!hasAssignKeyword) return false

        val hasTrigger = lower.contains("schedule") || lower.contains("add ") || lower.contains("assign") ||
                lower.contains("we have") || lower.contains("i have") || lower.contains("submit")
        val parsedDate = AcademicDateParser.parse(rawPrompt)
        return hasTrigger && parsedDate.hasExplicitDate
    }

    private suspend fun handleSingleAssignmentScheduling(
        lower: String,
        rawPrompt: String,
        course: MedicalCourse
    ): ActionResult {
        val subject = detectSubject(lower)
        val effectiveSubject = if (subject != "General") subject else "General Medical"
        val dateResult = AcademicDateParser.parse(rawPrompt)

        val title = if (subject != "General") "$effectiveSubject Assignment Submission" else "Assignment Submission"
        val assignment = Assignment(
            courseCode = course.code,
            subject = effectiveSubject,
            title = title,
            dueDate = dateResult.timeInMillis,
            type = "Record Submission",
            priority = "High",
            status = "Pending"
        )
        repository.addAssignment(assignment)

        repository.addPlannerTask(
            PlannerTask(
                courseCode = course.code,
                title = title,
                date = dateResult.timeInMillis,
                category = "Assignment"
            )
        )

        return ActionResult(
            isHandled = true,
            feedbackMessage = "✅ **Scheduled Assignment:** $title due on **${dateResult.readableDate}** at **${dateResult.timeString}**.\n\n" +
                    "Added to your Calendar and Planner.",
            actionCategory = "assignment"
        )
    }

    // -------------------------------------------------------------
    // 9. HOLIDAY / CLOSURE
    // -------------------------------------------------------------
    private fun isHolidayCommand(lower: String, rawPrompt: String): Boolean {
        val hasHolidayWord = lower.contains("holiday") || lower.contains("college closed") || lower.contains("college is closed") || lower.contains("vacation")
        return hasHolidayWord && AcademicDateParser.parse(rawPrompt).hasExplicitDate
    }

    private suspend fun handleHolidayScheduling(
        lower: String,
        rawPrompt: String,
        course: MedicalCourse
    ): ActionResult {
        val dateResult = AcademicDateParser.parse(rawPrompt)
        val holidayName = if (lower.contains("diwali")) "Diwali Holiday" else if (lower.contains("eid")) "Eid Holiday" else "College Holiday"

        repository.addPlannerTask(
            PlannerTask(
                courseCode = course.code,
                title = holidayName,
                date = dateResult.timeInMillis,
                category = "Holiday"
            )
        )

        return ActionResult(
            isHandled = true,
            feedbackMessage = "✅ Marked **$holidayName** on **${dateResult.readableDate}** in your Calendar and Planner.",
            actionCategory = "holiday"
        )
    }

    // -------------------------------------------------------------
    // 10. GENERAL EVENT / SEMINAR / CLINICAL POSTING BY VOICE (Requires explicit date!)
    // -------------------------------------------------------------
    private fun isGeneralEventCommand(lower: String, rawPrompt: String): Boolean {
        val hasEventKeyword = lower.contains("seminar") || lower.contains("workshop") ||
                lower.contains("clinical posting") || lower.contains("ward duty") ||
                lower.contains("add event") || lower.contains("schedule event")
        return hasEventKeyword && AcademicDateParser.parse(rawPrompt).hasExplicitDate
    }

    private suspend fun handleGeneralEventScheduling(
        lower: String,
        rawPrompt: String,
        course: MedicalCourse
    ): ActionResult {
        val subject = detectSubject(lower)
        val dateResult = AcademicDateParser.parse(rawPrompt)

        val category = when {
            lower.contains("seminar") || lower.contains("workshop") -> "Seminar"
            lower.contains("posting") || lower.contains("ward") || lower.contains("clinical") -> "Clinical Posting"
            else -> "Academic Event"
        }

        val cleanTitle = rawPrompt
            .replace(Regex("(?i)^(hey tendon|hi tendon|tendon)[,\\s]*"), "")
            .replace(Regex("(?i)^(we have a|we have an|we have|i have a|i have an|i have|schedule a|schedule an|schedule|remind me to|remind me about)\\s+"), "")
            .replace(Regex("(?i)\\s+(on|at|tomorrow|today|next)\\s+.*$"), "")
            .trim()
            .replaceFirstChar { it.uppercase() }
            .ifBlank { if (subject != "General") "$subject $category" else category }

        repository.addPlannerTask(
            PlannerTask(
                courseCode = course.code,
                title = cleanTitle,
                date = dateResult.timeInMillis,
                category = category
            )
        )

        repository.addNotification(
            InAppNotification(
                title = "Event Scheduled by Tendon",
                message = "$cleanTitle scheduled on ${dateResult.readableDate} at ${dateResult.timeString}.",
                type = "class"
            )
        )

        return ActionResult(
            isHandled = true,
            feedbackMessage = "✅ **Scheduled Event:** $cleanTitle on **${dateResult.readableDate}** at **${dateResult.timeString}**.\n\n" +
                    "📌 Saved directly to your Calendar and Planner.",
            actionCategory = "event"
        )
    }

    // -------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------
    private fun isMedicalSubjectMentioned(lower: String): Boolean {
        return detectSubject(lower) != "General"
    }

    private fun detectSubject(lower: String): String {
        return when {
            lower.contains("biochem") || lower.contains("tryptophan") || lower.contains("metabolism") || lower.contains("enzyme") || lower.contains("carbohydrate") -> "Biochemistry"
            lower.contains("physio") || lower.contains("hypothalamic") || lower.contains("pituitary") || lower.contains("nerve") || lower.contains("muscle") || lower.contains("cvs") -> "Physiology"
            lower.contains("anatomy") || lower.contains("upper limb") || lower.contains("lower limb") || lower.contains("thorax") || lower.contains("abdomen") || lower.contains("head and neck") || lower.contains("embryology") || lower.contains("histology") -> "Anatomy"
            lower.contains("organon") || lower.contains("aphorism") || lower.contains("vital force") -> "Organon of Medicine"
            lower.contains("materia medica") || lower.contains("materia") -> "Materia Medica"
            lower.contains("repertory") || lower.contains("rubric") -> "Repertory"
            lower.contains("pharmacy") -> "Homeopathic Pharmacy"
            lower.contains("pharmacology") || lower.contains("pharma") -> "Pharmacology"
            lower.contains("pathology") || lower.contains("patho") -> "Pathology"
            lower.contains("microbiology") || lower.contains("microbio") -> "Microbiology"
            lower.contains("forensic") || lower.contains("fmt") || lower.contains("toxicology") -> "Forensic Medicine"
            lower.contains("community medicine") || lower.contains("psm") -> "Community Medicine"
            lower.contains("surgery") -> "Surgery"
            lower.contains("medicine") -> "Medicine"
            lower.contains("obstetrics") || lower.contains("gynaecology") || lower.contains("gynae") -> "Obstetrics & Gynaecology"
            lower.contains("pediatrics") || lower.contains("paediatrics") -> "Pediatrics"
            else -> "General"
        }
    }

    private fun extractTopicTitle(prompt: String, subject: String): String {
        val regex = Regex("(?i)(?:mark|completed|covered|finished)\\s+(.*?)\\s+(?:as completed|as finished|covered|finished|in\\s+$subject|under\\s+$subject)")
        val match = regex.find(prompt)
        val extracted = match?.groupValues?.get(1)?.trim()
        if (!extracted.isNullOrBlank() && extracted.length > 2) {
            return extracted.replace(Regex("(?i)^chapter\\s*\\d*\\s*:?\\s*"), "").trim().ifBlank { extracted }
        }
        return "Topic / Chapter Coverage"
    }
}
