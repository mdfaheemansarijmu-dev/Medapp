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
 * AI App Action Controller: Gives the AI Assistant full access to read and modify
 * everything in the app by natural language prompts:
 * - Mark Attendance (present, absent, all classes today, past dates)
 * - Schedule Assessments, Vivas, and University Exams on exact dates & times
 * - Add, update, and manage Assignments & Deadlines
 * - Add, update, or cancel Timetable Classes & Schedule Overrides
 * - Mark Syllabus Chapters/Topics as Covered/Finished
 * - Query real-time App Data (Today's classes, attendance %, upcoming exams, finished topics)
 * - Schedule Holidays and College Closures
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
     * Inspects the prompt and executes real app modifications if an action intent is detected.
     */
    suspend fun executeActionIfCommand(
        prompt: String,
        currentCourse: MedicalCourse,
        studentName: String,
        currentYear: String
    ): ActionResult {
        val trimmed = prompt.trim()
        val lower = trimmed.lowercase(Locale.US)

        // 0. TENDON CONVERSATIONAL GREETINGS & IDENTITY
        if (isTendonGreetingOrIdentity(lower)) {
            val greetName = studentName.ifBlank { "Doctor" }
            return ActionResult(
                isHandled = true,
                feedbackMessage = "🤖 Hello $greetName! I am **Tendon**, your voice-enabled medical academic robot.\n\n" +
                        "You can talk to me anytime without typing! Try saying:\n" +
                        "• *\"We have Anatomy assessment on 24th October at 10 AM\"*\n" +
                        "• *\"Mark me present for all classes today\"*\n" +
                        "• *\"What classes do I have today?\"*\n" +
                        "• *\"Mark Upper Limb finished in Anatomy\"*",
                actionCategory = "greeting"
            )
        }

        // 1. ATTENDANCE ACTIONS
        if (isAttendanceCommand(lower)) {
            return handleAttendanceAction(lower, trimmed, currentCourse)
        }

        // 2. SYLLABUS FINISHED / COVERED ACTIONS
        if (isSyllabusCommand(lower)) {
            return handleSyllabusAction(lower, trimmed, currentCourse, studentName, currentYear)
        }

        // 3. SCHEDULE OVERRIDE / CLASS CANCELLATION ACTIONS
        if (isClassCancellationCommand(lower)) {
            return handleClassCancellation(lower, trimmed, currentCourse)
        }

        // 4. TIMETABLE CLASS ADD / DELETE ACTIONS
        if (isTimetableModificationCommand(lower)) {
            return handleTimetableModification(lower, trimmed, currentCourse, studentName)
        }

        // 5. REAL-TIME DATA QUERIES (Check queries before scheduling so "when is my assessment" is answered as a query)
        if (isAppQuery(lower)) {
            return handleAppQuery(lower, currentCourse, currentYear)
        }

        // 6. SINGLE ASSESSMENT SCHEDULING ACTION (e.g. "we have assessment on 24 oct", "schedule anatomy test on 24 oct at 10 am")
        if (isSingleAssessmentScheduleCommand(lower)) {
            return handleSingleAssessmentScheduling(lower, trimmed, currentCourse, studentName)
        }

        // 7. SINGLE ASSIGNMENT SCHEDULING ACTION (e.g. "we have physiology record submission on friday")
        if (isSingleAssignmentScheduleCommand(lower)) {
            return handleSingleAssignmentScheduling(lower, trimmed, currentCourse)
        }

        // 8. HOLIDAY / COLLEGE CLOSURE COMMAND
        if (isHolidayCommand(lower)) {
            return handleHolidayScheduling(lower, trimmed, currentCourse)
        }

        // 9. ANY GENERAL EVENT / SEMINAR / CLINICAL POSTING / REMINDER SCHEDULING BY VOICE
        if (isGeneralEventCommand(lower)) {
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
    // 1. ATTENDANCE
    // -------------------------------------------------------------
    private fun isAttendanceCommand(lower: String): Boolean {
        return (lower.contains("attendance") || lower.contains("mark me") || lower.contains("mark present") || lower.contains("mark absent") || lower.contains("marked present") || lower.contains("marked absent")) &&
                (lower.contains("present") || lower.contains("absent") || lower.contains("all classes") || lower.contains("status"))
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

        // Match day of week for timetable
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

        // Check if user wants to mark ALL classes today
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

        // Otherwise, find the specific subject
        val subject = detectSubject(lower)
        if (subject == "General") {
            // If user just said "mark me present today", and there's 1 class, or list classes
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

        // Find class matching subject
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
    // 2. SYLLABUS FINISHED / COVERED
    // -------------------------------------------------------------
    private fun isSyllabusCommand(lower: String): Boolean {
        return (lower.contains("mark") || lower.contains("covered") || lower.contains("finished") || lower.contains("completed") || lower.contains("log chapter")) &&
                (lower.contains("chapter") || lower.contains("topic") || lower.contains("syllabus") || lower.contains("finished tab") || isMedicalSubjectMentioned(lower)) &&
                !lower.contains("attendance") && !lower.contains("test") && !lower.contains("exam")
    }

    private suspend fun handleSyllabusAction(
        lower: String,
        rawPrompt: String,
        course: MedicalCourse,
        studentName: String,
        currentYear: String
    ): ActionResult {
        val subject = detectSubject(lower)
        val effectiveSubject = if (subject != "General") subject else "Anatomy"

        // Extract topic title from prompt
        val topicTitle = extractTopicTitle(rawPrompt, effectiveSubject)

        val topic = CompletedSyllabusTopic(
            courseCode = course.code,
            academicYear = currentYear,
            subject = effectiveSubject,
            topicTitle = topicTitle,
            completionDate = System.currentTimeMillis(),
            authorName = studentName.ifBlank { "Student" },
            isSharedWithBatch = true
        )
        repository.addCompletedTopic(topic)

        repository.addNotification(
            InAppNotification(
                title = "Syllabus Record Updated",
                message = "Marked '$topicTitle' covered in $effectiveSubject.",
                type = "syllabus"
            )
        )

        return ActionResult(
            isHandled = true,
            feedbackMessage = "✅ Marked **'$topicTitle'** as completed under **$effectiveSubject** ($currentYear).\n\n" +
                    "✓ Logged in your **Finished** tab\n" +
                    "✓ Progress badge and curriculum counter updated\n" +
                    "✓ Shared with your batch study log.",
            actionCategory = "syllabus"
        )
    }

    // -------------------------------------------------------------
    // 3. SCHEDULE OVERRIDE / CANCELLATION
    // -------------------------------------------------------------
    private fun isClassCancellationCommand(lower: String): Boolean {
        return (lower.contains("cancel") || lower.contains("no class") || lower.contains("postpone") || lower.contains("shifted to") || lower.contains("room change")) &&
                (lower.contains("class") || lower.contains("lecture") || lower.contains("period") || isMedicalSubjectMentioned(lower))
    }

    private suspend fun handleClassCancellation(
        lower: String,
        rawPrompt: String,
        course: MedicalCourse
    ): ActionResult {
        val dateResult = AcademicDateParser.parse(rawPrompt)
        val subject = detectSubject(lower)
        val isRoomChange = lower.contains("shifted to") || lower.contains("room") || lower.contains("hall")
        val room = if (isRoomChange) {
            if (lower.contains("hall b")) "Lecture Hall B" else if (lower.contains("hall a")) "Lecture Hall A" else "New Room"
        } else "Lecture Hall"

        val override = ScheduleOverride(
            courseCode = course.code,
            dateString = dateResult.dateString,
            subject = if (subject != "General") subject else "Class",
            startTime = dateResult.timeString.ifBlank { "09:00 AM" },
            endTime = "10:00 AM",
            room = room,
            isCancelled = !isRoomChange
        )
        repository.addOverride(override)

        val title = if (isRoomChange) "Room Change: $subject to $room" else "Class Cancelled: $subject"
        repository.addPlannerTask(
            PlannerTask(
                courseCode = course.code,
                title = title,
                date = dateResult.timeInMillis,
                category = "Schedule Override"
            )
        )

        val actionDesc = if (isRoomChange) "shifted to **$room**" else "marked **Cancelled**"
        return ActionResult(
            isHandled = true,
            feedbackMessage = "✅ Schedule Adjustment Applied: **$subject** on **${dateResult.readableDate}** is $actionDesc.\n\n" +
                    "Reflected in your Daily Routine and Calendar tab.",
            actionCategory = "override"
        )
    }

    // -------------------------------------------------------------
    // 4. TIMETABLE MODIFICATION (ADD / REMOVE CLASS)
    // -------------------------------------------------------------
    private fun isTimetableModificationCommand(lower: String): Boolean {
        return (lower.contains("add class") || lower.contains("new class") || lower.contains("add pathology") || lower.contains("add anatomy") || lower.contains("add physiology") || lower.contains("delete class") || lower.contains("remove class")) &&
                (lower.contains("monday") || lower.contains("tuesday") || lower.contains("wednesday") || lower.contains("thursday") || lower.contains("friday") || lower.contains("saturday") || lower.contains("timetable") || lower.contains("routine"))
    }

    private suspend fun handleTimetableModification(
        lower: String,
        rawPrompt: String,
        course: MedicalCourse,
        studentName: String
    ): ActionResult {
        val subject = detectSubject(lower)
        val dateResult = AcademicDateParser.parse(rawPrompt)

        // Day of week
        val dayOfWeek = when {
            lower.contains("monday") -> 1
            lower.contains("tuesday") -> 2
            lower.contains("wednesday") -> 3
            lower.contains("thursday") -> 4
            lower.contains("friday") -> 5
            lower.contains("saturday") -> 6
            lower.contains("sunday") -> 7
            else -> 1
        }
        val dayName = when (dayOfWeek) {
            1 -> "Monday"; 2 -> "Tuesday"; 3 -> "Wednesday"; 4 -> "Thursday"; 5 -> "Friday"; 6 -> "Saturday"; else -> "Sunday"
        }

        if (lower.contains("delete") || lower.contains("remove")) {
            val classes = repository.getTimetable(course.code).firstOrNull() ?: emptyList()
            val match = classes.find { it.dayOfWeek == dayOfWeek && (subject == "General" || it.subject.contains(subject, ignoreCase = true)) }
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

        // Adding class
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
    // 5. SCHEDULE SINGLE ASSESSMENT DIRECTLY (Supports natural voice like "we have assessment on 24th October")
    // -------------------------------------------------------------
    private fun isSingleAssessmentScheduleCommand(lower: String): Boolean {
        // Multi-point lists or multi-line notices should be parsed by the multi-item parser
        val hasMultiplePoints = (lower.contains("1.") || lower.contains("1)")) && (lower.contains("2.") || lower.contains("2)"))
        val subjectCount = listOf("anatomy", "physio", "biochem", "patho", "pharma", "microbio", "medicine", "surgery")
            .count { lower.contains(it) }
        if (hasMultiplePoints || subjectCount > 1 || lower.lines().size > 2) {
            return false
        }
        val hasAssessmentKeyword = lower.contains("assessment") || lower.contains("test") ||
                lower.contains("exam") || lower.contains("viva") || lower.contains("quiz") || lower.contains("midterm")
        val hasTriggerOrDate = lower.contains("schedule") || lower.contains("add") || lower.contains("set") ||
                lower.contains("create") || lower.contains("we have") || lower.contains("i have") ||
                lower.contains("there is") || lower.contains("on ") ||
                isMedicalSubjectMentioned(lower) || AcademicDateParser.containsDateReference(lower)
        return hasAssessmentKeyword && hasTriggerOrDate
    }

    private suspend fun handleSingleAssessmentScheduling(
        lower: String,
        rawPrompt: String,
        course: MedicalCourse,
        studentName: String
    ): ActionResult {
        val subject = detectSubject(lower)
        val effectiveSubject = if (subject != "General") subject else "Medical Sciences"
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
                    "📌 Saved directly to your Calendar and Planner.",
            actionCategory = "assessment",
            itemsScheduled = listOf(item)
        )
    }

    // -------------------------------------------------------------
    // 6. SCHEDULE SINGLE ASSIGNMENT DIRECTLY
    // -------------------------------------------------------------
    private fun isSingleAssignmentScheduleCommand(lower: String): Boolean {
        val hasMultiplePoints = (lower.contains("1.") || lower.contains("1)")) && (lower.contains("2.") || lower.contains("2)"))
        if (hasMultiplePoints || lower.lines().size > 2) return false
        val hasAssignKeyword = lower.contains("assignment") || lower.contains("homework") ||
                lower.contains("submission") || lower.contains("record book") || lower.contains("logbook") || lower.contains("journal")
        val hasTrigger = lower.contains("schedule") || lower.contains("add") || lower.contains("assign") ||
                lower.contains("we have") || lower.contains("i have") || lower.contains("submit") ||
                AcademicDateParser.containsDateReference(lower)
        return hasAssignKeyword && hasTrigger
    }

    private suspend fun handleSingleAssignmentScheduling(
        lower: String,
        rawPrompt: String,
        course: MedicalCourse
    ): ActionResult {
        val subject = detectSubject(lower)
        val effectiveSubject = if (subject != "General") subject else "Medical Sciences"
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
    // 7. HOLIDAY / CLOSURE
    // -------------------------------------------------------------
    private fun isHolidayCommand(lower: String): Boolean {
        return (lower.contains("holiday") || lower.contains("college closed") || lower.contains("college is closed") || lower.contains("vacation"))
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
    // 8. GENERAL EVENT / SEMINAR / CLINICAL POSTING / REMINDER BY VOICE
    // -------------------------------------------------------------
    private fun isGeneralEventCommand(lower: String): Boolean {
        val hasMultiplePoints = (lower.contains("1.") || lower.contains("1)")) && (lower.contains("2.") || lower.contains("2)"))
        if (hasMultiplePoints || lower.lines().size > 2) return false
        val hasEventTrigger = lower.contains("we have") || lower.contains("i have") ||
                lower.contains("schedule") || lower.contains("remind me") || lower.contains("add event") ||
                lower.contains("seminar") || lower.contains("workshop") || lower.contains("clinical posting") ||
                lower.contains("ward duty") || lower.contains("meeting") || lower.contains("practical")
        return hasEventTrigger && AcademicDateParser.containsDateReference(lower)
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
    // 9. REAL-TIME APP QUERIES
    // -------------------------------------------------------------
    private fun isAppQuery(lower: String): Boolean {
        return lower.startsWith("what ") || lower.startsWith("when ") || lower.startsWith("how much ") ||
                lower.startsWith("how many ") || lower.startsWith("which ") || lower.startsWith("tell me") ||
                lower.startsWith("do i have") || lower.startsWith("do we have") ||
                lower.contains("classes do i have") || lower.contains("my attendance") ||
                lower.contains("upcoming exam") || lower.contains("upcoming assessment") && (lower.contains("what") || lower.contains("when") || lower.contains("show")) ||
                lower.contains("syllabus progress") || lower.contains("what is today's schedule") || lower.contains("today's classes")
    }

    private suspend fun handleAppQuery(
        lower: String,
        course: MedicalCourse,
        currentYear: String
    ): ActionResult {
        // Attendance query
        if (lower.contains("attendance")) {
            val allRecords = repository.getAllAttendance().firstOrNull() ?: emptyList()
            if (allRecords.isEmpty()) {
                return ActionResult(
                    isHandled = true,
                    feedbackMessage = "📊 No attendance records logged yet. You can mark attendance by telling me: *\"Mark present for today's classes\"* or by visiting the Attendance tab.",
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

        // Exams / Assessments query
        if (lower.contains("exam") || lower.contains("assessment") || lower.contains("test") || lower.contains("viva")) {
            val assessments = repository.getAssessments(course.code).firstOrNull() ?: emptyList()
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
            val upcoming = assessments.filter { it.date >= System.currentTimeMillis() - 86400000L }.sortedBy { it.date }

            if (upcoming.isEmpty()) {
                return ActionResult(
                    isHandled = true,
                    feedbackMessage = "📅 You have no upcoming exams or assessments scheduled right now. You can paste a class notice anytime to schedule them!",
                    actionCategory = "query"
                )
            }

            val listStr = upcoming.joinToString("\n\n") { asm ->
                "📌 **${asm.title}** (${asm.subject})\n• Date: ${sdf.format(Date(asm.date))}\n• Type: ${asm.type}${if (!asm.syllabus.isNullOrBlank()) "\n• Syllabus: ${asm.syllabus}" else ""}"
            }
            return ActionResult(
                isHandled = true,
                feedbackMessage = "📅 **Upcoming Assessments & Exams (${upcoming.size}):**\n\n$listStr",
                actionCategory = "query"
            )
        }

        // Syllabus finished progress query
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
                        "Check your **Finished** tab for the complete detailed chapter registry.",
                actionCategory = "query"
            )
        }

        // Schedule / Classes today or tomorrow query
        val targetCal = Calendar.getInstance(AcademicDateParser.TIMEZONE)
        val isTomorrow = lower.contains("tomorrow")
        if (isTomorrow) targetCal.add(Calendar.DAY_OF_YEAR, 1)

        val dayOfWeek = when (targetCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6; else -> 7
        }
        val dayName = when (dayOfWeek) {
            1 -> "Monday"; 2 -> "Tuesday"; 3 -> "Wednesday"; 4 -> "Thursday"; 5 -> "Friday"; 6 -> "Saturday"; else -> "Sunday"
        }

        val timetable = repository.getTimetable(course.code).firstOrNull() ?: emptyList()
        val classes = timetable.filter { it.dayOfWeek == dayOfWeek }.sortedBy { it.periodNumber }

        if (classes.isEmpty()) {
            return ActionResult(
                isHandled = true,
                feedbackMessage = "🗓️ No classes scheduled for **$dayName** (${if (isTomorrow) "Tomorrow" else "Today"}). Enjoy your study or rest day!",
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
        // e.g. "mark Upper Limb finished in Anatomy"
        val regex = Regex("(?i)(?:mark|completed|covered|finished)\\s+(.*?)\\s+(?:as completed|as finished|covered|finished|in\\s+$subject|under\\s+$subject)")
        val match = regex.find(prompt)
        val extracted = match?.groupValues?.get(1)?.trim()
        if (!extracted.isNullOrBlank() && extracted.length > 2) {
            return extracted.replace(Regex("(?i)^chapter\\s*\\d*\\s*:?\\s*"), "").trim().ifBlank { extracted }
        }
        return "Topic / Chapter Coverage"
    }
}
