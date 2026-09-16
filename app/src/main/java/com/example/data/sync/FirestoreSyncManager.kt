package com.example.data.sync

import android.util.Log
import com.example.data.local.PlannerDao
import com.example.data.model.*
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestoreSyncManager(private val plannerDao: PlannerDao) {

    private val db = FirebaseFirestore.getInstance()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var activeUid: String? = null

    fun startSync(uid: String) {
        if (activeUid == uid) return
        stopSync()
        activeUid = uid
        _isActive.value = true
        _lastError.value = null

        scope.launch {
            try {
                // 1. Initial Bulk Push (idempotent, ensures cloud is populated)
                performBulkPush(uid)

                // 2. Register real-time listeners for all 8 collections
                setupRealtimeListeners(uid)
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error starting sync", e)
                _lastError.value = e.localizedMessage ?: "Sync startup error"
            }
        }
    }

    fun stopSync() {
        _isActive.value = false
        activeUid = null
        synchronized(listeners) {
            listeners.forEach { it.remove() }
            listeners.clear()
        }
    }

    suspend fun forceResync(uid: String) {
        _lastError.value = null
        try {
            stopSync()
            performBulkPush(uid)
            startSync(uid)
        } catch (e: Exception) {
            _lastError.value = e.localizedMessage ?: "Resync failed"
            Log.e("FirestoreSyncManager", "Error forcing resync", e)
        }
    }

    private suspend fun performBulkPush(uid: String) {
        try {
            plannerDao.getAllTimetableClassesOnce().forEach { pushTimetableClassInternal(uid, it) }
            plannerDao.getAllAssignmentsOnce().forEach { pushAssignmentInternal(uid, it) }
            plannerDao.getAllAssessmentsOnce().forEach { pushAssessmentInternal(uid, it) }
            plannerDao.getAllStudyTasksOnce().forEach { pushStudyTaskInternal(uid, it) }
            plannerDao.getAllExamsOnce().forEach { pushExamInternal(uid, it) }
            plannerDao.getAllPlannerTasksOnce().forEach { pushPlannerTaskInternal(uid, it) }
            plannerDao.getAllScheduleOverridesOnce().forEach { pushScheduleOverrideInternal(uid, it) }
            plannerDao.getAllAttendanceRecordsOnce().forEach { pushAttendanceRecordInternal(uid, it) }
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Error in bulk push", e)
            _lastError.value = e.localizedMessage
        }
    }

    private fun setupRealtimeListeners(uid: String) {
        synchronized(listeners) {
            // 1. Timetable Classes
            val timetableListener = db.collection("users").document(uid).collection("timetable_classes")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        _lastError.value = e.localizedMessage
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        scope.launch {
                            for (change in snapshots.documentChanges) {
                                val doc = change.document
                                val fId = doc.id
                                val map = doc.data
                                when (change.type) {
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val local = plannerDao.getTimetableClassByFirestoreId(fId)
                                        val item = mapToTimetableClass(map, fId, local?.id)
                                        plannerDao.insertClass(item)
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        plannerDao.getTimetableClassByFirestoreId(fId)?.let {
                                            plannerDao.deleteClassById(it.id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            listeners.add(timetableListener)

            // 2. Assignments
            val assignmentListener = db.collection("users").document(uid).collection("assignments")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        _lastError.value = e.localizedMessage
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        scope.launch {
                            for (change in snapshots.documentChanges) {
                                val doc = change.document
                                val fId = doc.id
                                val map = doc.data
                                when (change.type) {
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val local = plannerDao.getAssignmentByFirestoreId(fId)
                                        val item = mapToAssignment(map, fId, local?.id)
                                        plannerDao.insertAssignment(item)
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        plannerDao.getAssignmentByFirestoreId(fId)?.let {
                                            plannerDao.deleteAssignmentById(it.id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            listeners.add(assignmentListener)

            // 3. Assessments
            val assessmentListener = db.collection("users").document(uid).collection("assessments")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        _lastError.value = e.localizedMessage
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        scope.launch {
                            for (change in snapshots.documentChanges) {
                                val doc = change.document
                                val fId = doc.id
                                val map = doc.data
                                when (change.type) {
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val local = plannerDao.getAssessmentByFirestoreId(fId)
                                        val item = mapToAssessment(map, fId, local?.id)
                                        plannerDao.insertAssessment(item)
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        plannerDao.getAssessmentByFirestoreId(fId)?.let {
                                            plannerDao.deleteAssessmentById(it.id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            listeners.add(assessmentListener)

            // 4. Study Tasks
            val studyTaskListener = db.collection("users").document(uid).collection("study_tasks")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        _lastError.value = e.localizedMessage
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        scope.launch {
                            for (change in snapshots.documentChanges) {
                                val doc = change.document
                                val fId = doc.id
                                val map = doc.data
                                when (change.type) {
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val local = plannerDao.getStudyTaskByFirestoreId(fId)
                                        val item = mapToStudyTask(map, fId, local?.id)
                                        plannerDao.insertStudyTask(item)
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        plannerDao.getStudyTaskByFirestoreId(fId)?.let {
                                            plannerDao.deleteStudyTaskById(it.id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            listeners.add(studyTaskListener)

            // 5. Exams
            val examListener = db.collection("users").document(uid).collection("exams")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        _lastError.value = e.localizedMessage
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        scope.launch {
                            for (change in snapshots.documentChanges) {
                                val doc = change.document
                                val fId = doc.id
                                val map = doc.data
                                when (change.type) {
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val local = plannerDao.getExamByFirestoreId(fId)
                                        val item = mapToExam(map, fId, local?.id)
                                        plannerDao.insertExam(item)
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        plannerDao.getExamByFirestoreId(fId)?.let {
                                            plannerDao.deleteExamById(it.id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            listeners.add(examListener)

            // 6. Planner Tasks
            val plannerTaskListener = db.collection("users").document(uid).collection("planner_tasks")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        _lastError.value = e.localizedMessage
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        scope.launch {
                            for (change in snapshots.documentChanges) {
                                val doc = change.document
                                val fId = doc.id
                                val map = doc.data
                                when (change.type) {
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val local = plannerDao.getPlannerTaskByFirestoreId(fId)
                                        val item = mapToPlannerTask(map, fId, local?.id)
                                        plannerDao.insertPlannerTask(item)
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        plannerDao.getPlannerTaskByFirestoreId(fId)?.let {
                                            plannerDao.deletePlannerTaskById(it.id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            listeners.add(plannerTaskListener)

            // 7. Schedule Overrides
            val overrideListener = db.collection("users").document(uid).collection("schedule_overrides")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        _lastError.value = e.localizedMessage
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        scope.launch {
                            for (change in snapshots.documentChanges) {
                                val doc = change.document
                                val fId = doc.id
                                val map = doc.data
                                when (change.type) {
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val local = plannerDao.getScheduleOverrideByFirestoreId(fId)
                                        val item = mapToScheduleOverride(map, fId, local?.id)
                                        plannerDao.insertOverride(item)
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        plannerDao.getScheduleOverrideByFirestoreId(fId)?.let {
                                            plannerDao.deleteOverrideById(it.id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            listeners.add(overrideListener)

            // 8. Attendance Records
            val attendanceListener = db.collection("users").document(uid).collection("attendance_records")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        _lastError.value = e.localizedMessage
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        scope.launch {
                            for (change in snapshots.documentChanges) {
                                val doc = change.document
                                val fId = doc.id
                                val map = doc.data
                                when (change.type) {
                                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                        val local = plannerDao.getAttendanceRecordByFirestoreId(fId)
                                        val item = mapToAttendanceRecord(map, fId, local?.id)
                                        plannerDao.insertAttendanceRecord(item)
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        plannerDao.getAttendanceRecordByFirestoreId(fId)?.let {
                                            plannerDao.deleteAttendanceForDate(it.dateString)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            listeners.add(attendanceListener)
        }
    }

    suspend fun pushTimetableClass(item: TimetableClass) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                pushTimetableClassInternal(uid, item)
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error pushing timetable class", e)
            }
        }
    }
    private suspend fun pushTimetableClassInternal(uid: String, item: TimetableClass) {
        val map = mapOf(
            "courseCode" to item.courseCode,
            "dayOfWeek" to item.dayOfWeek.toLong(),
            "periodNumber" to item.periodNumber.toLong(),
            "subject" to item.subject,
            "startTime" to item.startTime,
            "endTime" to item.endTime,
            "room" to item.room,
            "teacherName" to item.teacherName,
            "colorHex" to item.colorHex
        )
        db.collection("users").document(uid).collection("timetable_classes").document(item.firestoreId).set(map).await()
    }

    suspend fun pushAssignment(item: Assignment) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                pushAssignmentInternal(uid, item)
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error pushing assignment", e)
            }
        }
    }
    private suspend fun pushAssignmentInternal(uid: String, item: Assignment) {
        val map = mapOf(
            "courseCode" to item.courseCode,
            "subject" to item.subject,
            "title" to item.title,
            "dueDate" to item.dueDate,
            "priority" to item.priority,
            "status" to item.status,
            "type" to item.type,
            "notes" to item.notes
        )
        db.collection("users").document(uid).collection("assignments").document(item.firestoreId).set(map).await()
    }

    suspend fun pushAssessment(item: Assessment) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                pushAssessmentInternal(uid, item)
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error pushing assessment", e)
            }
        }
    }
    private suspend fun pushAssessmentInternal(uid: String, item: Assessment) {
        val map = mapOf(
            "courseCode" to item.courseCode,
            "subject" to item.subject,
            "title" to item.title,
            "date" to item.date,
            "type" to item.type,
            "status" to item.status,
            "syllabus" to item.syllabus
        )
        db.collection("users").document(uid).collection("assessments").document(item.firestoreId).set(map).await()
    }

    suspend fun pushStudyTask(item: StudyTask) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                pushStudyTaskInternal(uid, item)
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error pushing study task", e)
            }
        }
    }
    private suspend fun pushStudyTaskInternal(uid: String, item: StudyTask) {
        val map = mapOf(
            "courseCode" to item.courseCode,
            "subject" to item.subject,
            "title" to item.title,
            "dueDate" to item.dueDate,
            "priority" to item.priority,
            "progress" to item.progress.toLong(),
            "targetMinutes" to item.targetMinutes.toLong(),
            "notes" to item.notes
        )
        db.collection("users").document(uid).collection("study_tasks").document(item.firestoreId).set(map).await()
    }

    suspend fun pushExam(item: Exam) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                pushExamInternal(uid, item)
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error pushing exam", e)
            }
        }
    }
    private suspend fun pushExamInternal(uid: String, item: Exam) {
        val map = mapOf(
            "courseCode" to item.courseCode,
            "subject" to item.subject,
            "title" to item.title,
            "date" to item.date,
            "time" to item.time,
            "room" to item.room,
            "syllabus" to item.syllabus,
            "status" to item.status
        )
        db.collection("users").document(uid).collection("exams").document(item.firestoreId).set(map).await()
    }

    suspend fun pushPlannerTask(item: PlannerTask) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                pushPlannerTaskInternal(uid, item)
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error pushing planner task", e)
            }
        }
    }
    private suspend fun pushPlannerTaskInternal(uid: String, item: PlannerTask) {
        val map = mapOf(
            "courseCode" to item.courseCode,
            "title" to item.title,
            "date" to item.date,
            "isCompleted" to item.isCompleted,
            "category" to item.category
        )
        db.collection("users").document(uid).collection("planner_tasks").document(item.firestoreId).set(map).await()
    }

    suspend fun pushScheduleOverride(item: ScheduleOverride) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                pushScheduleOverrideInternal(uid, item)
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error pushing schedule override", e)
            }
        }
    }
    private suspend fun pushScheduleOverrideInternal(uid: String, item: ScheduleOverride) {
        val map = mapOf(
            "courseCode" to item.courseCode,
            "dateString" to item.dateString,
            "timetableClassId" to item.timetableClassId?.toLong(),
            "originalPeriodNumber" to item.originalPeriodNumber?.toLong(),
            "subject" to item.subject,
            "startTime" to item.startTime,
            "endTime" to item.endTime,
            "room" to item.room,
            "teacherName" to item.teacherName,
            "isCancelled" to item.isCancelled,
            "colorHex" to item.colorHex
        )
        db.collection("users").document(uid).collection("schedule_overrides").document(item.firestoreId).set(map).await()
    }

    suspend fun pushAttendanceRecord(item: AttendanceRecord) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                pushAttendanceRecordInternal(uid, item)
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error pushing attendance record", e)
            }
        }
    }
    private suspend fun pushAttendanceRecordInternal(uid: String, item: AttendanceRecord) {
        val map = mutableMapOf<String, Any?>(
            "dateString" to item.dateString,
            "subject" to item.subject,
            "isPresent" to item.isPresent,
            "classTime" to item.classTime,
            "status" to item.status,
            "startTime" to item.startTime,
            "endTime" to item.endTime,
            "note" to item.note,
            "reason" to item.reason,
            "recordedTimestamp" to item.recordedTimestamp
        )
        db.collection("users").document(uid).collection("attendance_records").document(item.firestoreId).set(map).await()
    }

    fun deleteTimetableClass(firestoreId: String) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                db.collection("users").document(uid).collection("timetable_classes").document(firestoreId).delete().await()
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error deleting timetable class", e)
            }
        }
    }
    fun deleteAssignment(firestoreId: String) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                db.collection("users").document(uid).collection("assignments").document(firestoreId).delete().await()
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error deleting assignment", e)
            }
        }
    }
    fun deleteAssessment(firestoreId: String) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                db.collection("users").document(uid).collection("assessments").document(firestoreId).delete().await()
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error deleting assessment", e)
            }
        }
    }
    fun deleteStudyTask(firestoreId: String) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                db.collection("users").document(uid).collection("study_tasks").document(firestoreId).delete().await()
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error deleting study task", e)
            }
        }
    }
    fun deleteExam(firestoreId: String) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                db.collection("users").document(uid).collection("exams").document(firestoreId).delete().await()
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error deleting exam", e)
            }
        }
    }
    fun deletePlannerTask(firestoreId: String) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                db.collection("users").document(uid).collection("planner_tasks").document(firestoreId).delete().await()
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error deleting planner task", e)
            }
        }
    }
    fun deleteScheduleOverride(firestoreId: String) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                db.collection("users").document(uid).collection("schedule_overrides").document(firestoreId).delete().await()
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error deleting override", e)
            }
        }
    }
    fun deleteAttendanceRecord(firestoreId: String) {
        val uid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                db.collection("users").document(uid).collection("attendance_records").document(firestoreId).delete().await()
            } catch (e: Exception) {
                Log.e("FirestoreSyncManager", "Error deleting attendance", e)
            }
        }
    }

    private fun mapToTimetableClass(map: Map<String, Any?>, firestoreId: String, localId: Int?): TimetableClass {
        return TimetableClass(
            id = localId ?: 0,
            courseCode = map["courseCode"] as? String ?: "",
            dayOfWeek = (map["dayOfWeek"] as? Long)?.toInt() ?: 1,
            periodNumber = (map["periodNumber"] as? Long)?.toInt() ?: 1,
            subject = map["subject"] as? String ?: "",
            startTime = map["startTime"] as? String ?: "",
            endTime = map["endTime"] as? String ?: "",
            room = map["room"] as? String,
            teacherName = map["teacherName"] as? String,
            colorHex = map["colorHex"] as? String ?: "#4F46E5",
            firestoreId = firestoreId
        )
    }

    private fun mapToAssignment(map: Map<String, Any?>, firestoreId: String, localId: Int?): Assignment {
        return Assignment(
            id = localId ?: 0,
            courseCode = map["courseCode"] as? String ?: "",
            subject = map["subject"] as? String ?: "",
            title = map["title"] as? String ?: "",
            dueDate = (map["dueDate"] as? Long) ?: 0L,
            priority = map["priority"] as? String ?: "Medium",
            status = map["status"] as? String ?: "Pending",
            type = map["type"] as? String ?: "Assignment",
            notes = map["notes"] as? String,
            firestoreId = firestoreId
        )
    }

    private fun mapToAssessment(map: Map<String, Any?>, firestoreId: String, localId: Int?): Assessment {
        return Assessment(
            id = localId ?: 0,
            courseCode = map["courseCode"] as? String ?: "",
            subject = map["subject"] as? String ?: "",
            title = map["title"] as? String ?: "",
            date = (map["date"] as? Long) ?: 0L,
            type = map["type"] as? String ?: "Class Test",
            status = map["status"] as? String ?: "Upcoming",
            syllabus = map["syllabus"] as? String,
            firestoreId = firestoreId
        )
    }

    private fun mapToStudyTask(map: Map<String, Any?>, firestoreId: String, localId: Int?): StudyTask {
        return StudyTask(
            id = localId ?: 0,
            courseCode = map["courseCode"] as? String ?: "",
            subject = map["subject"] as? String ?: "",
            title = map["title"] as? String ?: "",
            dueDate = (map["dueDate"] as? Long) ?: 0L,
            priority = map["priority"] as? String ?: "Medium",
            progress = (map["progress"] as? Long)?.toInt() ?: 0,
            targetMinutes = (map["targetMinutes"] as? Long)?.toInt() ?: 30,
            notes = map["notes"] as? String,
            firestoreId = firestoreId
        )
    }

    private fun mapToExam(map: Map<String, Any?>, firestoreId: String, localId: Int?): Exam {
        return Exam(
            id = localId ?: 0,
            courseCode = map["courseCode"] as? String ?: "",
            subject = map["subject"] as? String ?: "",
            title = map["title"] as? String ?: "",
            date = (map["date"] as? Long) ?: 0L,
            time = map["time"] as? String,
            room = map["room"] as? String,
            syllabus = map["syllabus"] as? String,
            status = map["status"] as? String ?: "Upcoming",
            firestoreId = firestoreId
        )
    }

    private fun mapToPlannerTask(map: Map<String, Any?>, firestoreId: String, localId: Int?): PlannerTask {
        return PlannerTask(
            id = localId ?: 0,
            courseCode = map["courseCode"] as? String ?: "",
            title = map["title"] as? String ?: "",
            date = (map["date"] as? Long) ?: 0L,
            isCompleted = map["isCompleted"] as? Boolean ?: false,
            category = map["category"] as? String ?: "General",
            firestoreId = firestoreId
        )
    }

    private fun mapToScheduleOverride(map: Map<String, Any?>, firestoreId: String, localId: Int?): ScheduleOverride {
        return ScheduleOverride(
            id = localId ?: 0,
            courseCode = map["courseCode"] as? String ?: "",
            dateString = map["dateString"] as? String ?: "",
            timetableClassId = (map["timetableClassId"] as? Long)?.toInt(),
            originalPeriodNumber = (map["originalPeriodNumber"] as? Long)?.toInt(),
            subject = map["subject"] as? String ?: "",
            startTime = map["startTime"] as? String ?: "",
            endTime = map["endTime"] as? String ?: "",
            room = map["room"] as? String,
            teacherName = map["teacherName"] as? String,
            isCancelled = map["isCancelled"] as? Boolean ?: false,
            colorHex = map["colorHex"] as? String ?: "#EF4444",
            firestoreId = firestoreId
        )
    }

    private fun mapToAttendanceRecord(map: Map<String, Any?>, firestoreId: String, localId: Int?): AttendanceRecord {
        val isPresent = map["isPresent"] as? Boolean ?: false
        val status = map["status"] as? String ?: (if (isPresent) "PRESENT" else "ABSENT")
        val timestamp = (map["recordedTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        return AttendanceRecord(
            id = localId ?: 0,
            dateString = map["dateString"] as? String ?: "",
            subject = map["subject"] as? String ?: "",
            isPresent = isPresent,
            classTime = map["classTime"] as? String,
            firestoreId = firestoreId,
            status = status,
            startTime = map["startTime"] as? String,
            endTime = map["endTime"] as? String,
            note = map["note"] as? String,
            reason = map["reason"] as? String,
            recordedTimestamp = timestamp
        )
    }
}
