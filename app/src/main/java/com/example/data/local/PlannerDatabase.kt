package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannerDao {
    // Timetable
    @Query("SELECT * FROM timetable_classes WHERE courseCode = :courseCode ORDER BY dayOfWeek ASC, periodNumber ASC")
    fun getTimetableForCourse(courseCode: String): Flow<List<TimetableClass>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClass(classItem: TimetableClass)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClasses(classes: List<TimetableClass>)

    @Query("DELETE FROM timetable_classes WHERE id = :id")
    suspend fun deleteClassById(id: Int)

    @Query("DELETE FROM timetable_classes WHERE courseCode = :courseCode")
    suspend fun clearTimetableForCourse(courseCode: String)

    // Assignments
    @Query("SELECT * FROM assignments WHERE courseCode = :courseCode ORDER BY dueDate ASC")
    fun getAssignmentsForCourse(courseCode: String): Flow<List<Assignment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: Assignment): Long

    @Query("DELETE FROM assignments WHERE id = :id")
    suspend fun deleteAssignmentById(id: Int)

    @Query("UPDATE assignments SET status = :status WHERE id = :id")
    suspend fun updateAssignmentStatus(id: Int, status: String)

    // Assessments
    @Query("SELECT * FROM assessments WHERE courseCode = :courseCode ORDER BY date ASC")
    fun getAssessmentsForCourse(courseCode: String): Flow<List<Assessment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessment(assessment: Assessment): Long

    @Query("DELETE FROM assessments WHERE id = :id")
    suspend fun deleteAssessmentById(id: Int)

    @Query("UPDATE assessments SET status = :status WHERE id = :id")
    suspend fun updateAssessmentStatus(id: Int, status: String)

    // Study Tasks
    @Query("SELECT * FROM study_tasks WHERE courseCode = :courseCode ORDER BY dueDate ASC")
    fun getStudyTasksForCourse(courseCode: String): Flow<List<StudyTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyTask(task: StudyTask): Long

    @Query("DELETE FROM study_tasks WHERE id = :id")
    suspend fun deleteStudyTaskById(id: Int)

    @Query("UPDATE study_tasks SET progress = :progress WHERE id = :id")
    suspend fun updateStudyTaskProgress(id: Int, progress: Int)

    // Chat History
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getChatMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()

    // In-App Notifications
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getNotifications(): Flow<List<InAppNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: InAppNotification)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: Int)

    @Query("DELETE FROM notifications")
    suspend fun clearNotifications()

    // Exams
    @Query("SELECT * FROM exams WHERE courseCode = :courseCode ORDER BY date ASC")
    fun getExamsForCourse(courseCode: String): Flow<List<Exam>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: Exam)

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteExamById(id: Int)

    @Query("DELETE FROM exams WHERE courseCode = :courseCode")
    suspend fun clearExamsForCourse(courseCode: String)

    // Teachers
    @Query("SELECT * FROM teachers ORDER BY name ASC")
    fun getAllTeachers(): Flow<List<Teacher>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeacher(teacher: Teacher)

    @Query("DELETE FROM teachers WHERE name = :name")
    suspend fun deleteTeacher(name: String)

    // Rooms
    @Query("SELECT * FROM rooms ORDER BY roomName ASC")
    fun getAllRooms(): Flow<List<RoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: RoomEntity)

    @Query("DELETE FROM rooms WHERE roomName = :roomName")
    suspend fun deleteRoom(roomName: String)

    // Planner Tasks
    @Query("SELECT * FROM planner_tasks WHERE courseCode = :courseCode ORDER BY date ASC")
    fun getPlannerTasksForCourse(courseCode: String): Flow<List<PlannerTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannerTask(task: PlannerTask)

    @Query("UPDATE planner_tasks SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updatePlannerTaskStatus(id: Int, isCompleted: Int) // Store as Int (0/1) for compatibility if needed or Boolean

    @Query("DELETE FROM planner_tasks WHERE id = :id")
    suspend fun deletePlannerTaskById(id: Int)

    // Schedule Overrides
    @Query("SELECT * FROM schedule_overrides WHERE courseCode = :courseCode")
    fun getOverridesForCourse(courseCode: String): Flow<List<ScheduleOverride>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverride(override: ScheduleOverride)

    @Query("DELETE FROM schedule_overrides WHERE id = :id")
    suspend fun deleteOverrideById(id: Int)

    @Query("DELETE FROM schedule_overrides WHERE courseCode = :courseCode")
    suspend fun clearOverridesForCourse(courseCode: String)

    // User Profile
    @Query("SELECT * FROM user_profiles WHERE uid = :uid LIMIT 1")
    fun getUserProfile(uid: String): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    @Query("DELETE FROM user_profiles")
    suspend fun clearUserProfiles()

    // Attendance Records
    @Query("SELECT * FROM attendance_records WHERE dateString = :dateString")
    fun getAttendanceForDate(dateString: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records ORDER BY dateString DESC")
    fun getAllAttendance(): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecord(record: AttendanceRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecords(records: List<AttendanceRecord>)

    @Query("DELETE FROM attendance_records WHERE dateString = :dateString")
    suspend fun deleteAttendanceForDate(dateString: String)

    // Daily Subject Revisions
    @Query("SELECT * FROM daily_subject_revisions WHERE dateString = :dateString")
    fun getRevisionsForDate(dateString: String): Flow<List<DailySubjectRevision>>

    @Query("SELECT * FROM daily_subject_revisions ORDER BY dateString DESC")
    fun getAllRevisions(): Flow<List<DailySubjectRevision>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRevision(revision: DailySubjectRevision)

    @Query("DELETE FROM daily_subject_revisions WHERE id = :id")
    suspend fun deleteRevisionById(id: Int)
}

@Database(
    entities = [
        TimetableClass::class,
        Assignment::class,
        Assessment::class,
        StudyTask::class,
        ChatMessage::class,
        InAppNotification::class,
        Exam::class,
        Teacher::class,
        RoomEntity::class,
        PlannerTask::class,
        ScheduleOverride::class,
        UserProfile::class,
        AttendanceRecord::class,
        DailySubjectRevision::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun plannerDao(): PlannerDao
}

