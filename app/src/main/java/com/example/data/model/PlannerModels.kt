package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

enum class MedicalCourse(val displayName: String, val code: String) {
    BHMS("Homeopathy (BHMS)", "BHMS"),
    MBBS("Allopathy (MBBS)", "MBBS"),
    BDS("Dental (BDS)", "BDS"),
    BAMS("Ayurveda (BAMS)", "BAMS"),
    NURSING("Nursing (B.Sc)", "NURSING"),
    PHARMACY("Pharmacy (B.Pharm)", "PHARMACY")
}

@Entity(tableName = "timetable_classes")
data class TimetableClass(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseCode: String,
    val dayOfWeek: Int, // 1 = Monday, 2 = Tuesday, ..., 6 = Saturday, 7 = Sunday
    val periodNumber: Int,
    val subject: String,
    val startTime: String, // e.g., "09:00 AM"
    val endTime: String, // e.g., "10:00 AM"
    val room: String? = null,
    val teacherName: String? = null,
    val colorHex: String = "#4F46E5", // Color representation
    val firestoreId: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "assignments")
data class Assignment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseCode: String,
    val subject: String,
    val title: String,
    val dueDate: Long, // timestamp
    val priority: String, // "High", "Medium", "Low"
    val status: String, // "Pending", "Completed"
    val type: String, // "Assignment", "Practical", "Seminar", "Viva", "Homework", "Case Record"
    val notes: String? = null,
    val firestoreId: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "assessments")
data class Assessment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseCode: String,
    val subject: String,
    val title: String,
    val date: Long, // timestamp
    val type: String, // "Class Test", "Internal", "Practical Exam", "Viva", "University Exam"
    val status: String = "Upcoming", // "Upcoming", "Completed"
    val syllabus: String? = null,
    val firestoreId: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "study_tasks")
data class StudyTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseCode: String,
    val subject: String,
    val title: String,
    val dueDate: Long, // timestamp
    val priority: String, // "High", "Medium", "Low"
    val progress: Int = 0, // 0 to 100
    val targetMinutes: Int = 30, // daily study goal for this task
    val notes: String? = null,
    val firestoreId: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String, // "user" or "ai"
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val parsedJson: String? = null // If AI extracted items, they're stored here to construct draft card
)

@Entity(tableName = "notifications")
data class InAppNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val type: String // "class", "assignment", "exam", "study"
)

// Helper model to represent parsed JSON output from Gemini
@JsonClass(generateAdapter = true)
data class ParsedItem(
    val category: String, // "Assignment", "Assessment", "Exam", "Practical", "Lab", "Seminar", "Homework", "General Notice", "Holiday", "Schedule Change", "Reminder", "Study Task"
    val subject: String,
    val title: String,
    val due_date_description: String, // e.g. "Tomorrow", "Next Monday", "2026-07-01"
    val priority: String, // "High", "Medium", "Low"
    val details: String? = null
)

@JsonClass(generateAdapter = true)
data class ParserResponse(
    val extracted_items: List<ParsedItem> = emptyList()
)

@Entity(tableName = "exams")
@JsonClass(generateAdapter = true)
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseCode: String,
    val subject: String,
    val title: String,
    val date: Long, // timestamp
    val time: String? = null,
    val room: String? = null,
    val syllabus: String? = null,
    val status: String = "Upcoming", // "Upcoming", "Completed"
    val firestoreId: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "teachers")
@JsonClass(generateAdapter = true)
data class Teacher(
    @PrimaryKey val name: String,
    val department: String? = null,
    val contact: String? = null
)

@Entity(tableName = "rooms")
@JsonClass(generateAdapter = true)
data class RoomEntity(
    @PrimaryKey val roomName: String,
    val location: String? = null
)

@Entity(tableName = "planner_tasks")
@JsonClass(generateAdapter = true)
data class PlannerTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseCode: String,
    val title: String,
    val date: Long, // timestamp
    val isCompleted: Boolean = false,
    val category: String = "General", // "General", "Clinical Posting", etc.
    val firestoreId: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "schedule_overrides")
@JsonClass(generateAdapter = true)
data class ScheduleOverride(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseCode: String,
    val dateString: String, // "YYYY-MM-DD"
    val timetableClassId: Int? = null,
    val originalPeriodNumber: Int? = null,
    val subject: String,
    val startTime: String,
    val endTime: String,
    val room: String? = null,
    val teacherName: String? = null,
    val isCancelled: Boolean = false,
    val colorHex: String = "#EF4444",
    val firestoreId: String = java.util.UUID.randomUUID().toString()
)

@JsonClass(generateAdapter = true)
data class ParsedTimetableClass(
    val day_of_week: Int, // 1=Mon, ..., 7=Sun
    val period_number: Int,
    val start_time: String,
    val end_time: String,
    val subject: String,
    val teacher_name: String? = null,
    val room: String? = null,
    val is_practical: Boolean = false,
    val is_lunch_break: Boolean = false,
    val confidence: String? = "High", // "High", "Medium", "Low"
    val is_uncertain: Boolean? = false,
    val notes: String? = null,
    val batch: String? = null,
    val department: String? = null
)

@JsonClass(generateAdapter = true)
data class UnifiedParserResponse(
    val document_type: String, // "Weekly Timetable", "Exam Timetable", "Assignment Notice", etc.
    val is_temporary_override: Boolean = false,
    val override_date: String? = null, // "YYYY-MM-DD"
    val extracted_timetable: List<ParsedTimetableClass> = emptyList(),
    val extracted_items: List<ParsedItem> = emptyList(),
    val conversational_response: String? = null
)

@JsonClass(generateAdapter = true)
data class ScheduledNotification(
    val id: Int,
    val type: String, // "class", "assignment", "assessment", "study"
    val itemId: String, // e.g. "asg_1"
    val title: String,
    val message: String,
    val triggerTime: Long,
    val subject: String,
    val targetTime: Long
)

@Entity(tableName = "user_profiles")
@JsonClass(generateAdapter = true)
data class UserProfile(
    @PrimaryKey val uid: String,
    val fullName: String,
    val college: String,
    val course: String,
    val year: String,
    val semester: String = "",
    val batch: String = ""
)

@Entity(tableName = "attendance_records")
@JsonClass(generateAdapter = true)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateString: String, // "YYYY-MM-DD"
    val subject: String,
    val isPresent: Boolean,
    val classTime: String? = null,
    val firestoreId: String = java.util.UUID.randomUUID().toString(),
    val status: String = if (isPresent) "PRESENT" else "ABSENT",
    val startTime: String? = null,
    val endTime: String? = null,
    val note: String? = null,
    val reason: String? = null,
    val recordedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_subject_revisions")
@JsonClass(generateAdapter = true)
data class DailySubjectRevision(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateString: String = "", // "YYYY-MM-DD"
    val subject: String = "",
    val periodNumber: Int = 0,
    val classTime: String = "",
    val studentExplanation: String = "",
    val aiSummary: String = "",
    val keyPoints: String = "", // delimiter separated
    val revisionQuestions: String = "" // delimiter separated
)


