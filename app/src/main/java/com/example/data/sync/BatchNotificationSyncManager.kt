package com.example.data.sync

import android.app.Application
import android.util.Log
import com.example.data.model.Assignment
import com.example.data.model.Assessment
import com.example.data.model.CompletedSyllabusTopic
import com.example.data.model.TimetableClass
import com.example.data.model.InAppNotification
import com.example.data.repository.PlannerRepository
import com.example.util.AcademicNotificationManager
import com.example.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SharedBatchNotice(
    val id: String = "",
    val batchKey: String = "",
    val title: String = "",
    val message: String = "",
    val authorName: String = "Class Representative",
    val authorUid: String = "",
    val category: String = "batch_notice", // "batch_notice", "shared_assignment", "exam_alert"
    val timestamp: Long = System.currentTimeMillis(),
    val urgent: Boolean = false
)

data class SharedBatchAssignment(
    val firestoreId: String = "",
    val batchKey: String = "",
    val courseCode: String = "",
    val subject: String = "",
    val title: String = "",
    val dueDate: Long = 0L,
    val priority: String = "Medium",
    val status: String = "Pending",
    val type: String = "Assignment",
    val notes: String? = null,
    val authorName: String = "Classmate",
    val authorUid: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class SharedBatchAssessment(
    val firestoreId: String = "",
    val batchKey: String = "",
    val courseCode: String = "",
    val subject: String = "",
    val title: String = "",
    val date: Long = 0L,
    val type: String = "Internal",
    val status: String = "Upcoming",
    val syllabus: String? = null,
    val authorName: String = "Classmate",
    val authorUid: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class SharedBatchCompletedTopic(
    val firestoreId: String = "",
    val batchKey: String = "",
    val courseCode: String = "",
    val academicYear: String = "",
    val subject: String = "",
    val topicTitle: String = "",
    val completionDate: Long = System.currentTimeMillis(),
    val teacherName: String? = null,
    val notes: String? = null,
    val authorName: String = "Classmate",
    val authorUid: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class SharedBatchTimetableClass(
    val firestoreId: String = "",
    val batchKey: String = "",
    val courseCode: String = "",
    val dayOfWeek: Int = 1,
    val periodNumber: Int = 1,
    val subject: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val room: String? = null,
    val teacherName: String? = null,
    val colorHex: String = "#4F46E5",
    val authorName: String = "Classmate",
    val authorUid: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class BatchNotificationSyncManager(
    private val application: Application,
    private val repository: PlannerRepository
) {
    companion object {
        private const val TAG = "BatchNotificationSync"

        fun computeBatchKey(college: String, course: String, admissionYear: Int, batch: String, customBatchCode: String? = null): String {
            if (!customBatchCode.isNullOrBlank()) {
                val cleanCustom = customBatchCode.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_').take(64)
                if (cleanCustom.isNotBlank()) return cleanCustom
            }
            val directoryMatch = if (college.isNotBlank()) com.example.data.university.UniversityDirectory.findCollege(college) else null
            val cleanCollege = if (directoryMatch != null && directoryMatch.id.isNotBlank() && directoryMatch.id != "other_custom_institute") {
                directoryMatch.id.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(48)
            } else {
                college.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(48)
            }
            val cleanCourse = when {
                course.contains("bhms", ignoreCase = true) -> "bhms"
                course.contains("mbbs", ignoreCase = true) -> "mbbs"
                course.contains("bds", ignoreCase = true) -> "bds"
                course.contains("bams", ignoreCase = true) -> "bams"
                course.contains("nurs", ignoreCase = true) -> "nursing"
                course.contains("pharm", ignoreCase = true) -> "pharmacy"
                else -> course.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(16)
            }
            val rawBatch = batch.trim().lowercase()
            val cleanBatch = when {
                rawBatch == "a" || rawBatch == "batch a" || rawBatch == "batch_a" || rawBatch == "batch-a" || rawBatch == "1" || rawBatch == "batch 1" || rawBatch == "batch_1" -> "batch_a"
                rawBatch == "b" || rawBatch == "batch b" || rawBatch == "batch_b" || rawBatch == "batch-b" || rawBatch == "2" || rawBatch == "batch 2" || rawBatch == "batch_2" -> "batch_b"
                rawBatch == "c" || rawBatch == "batch c" || rawBatch == "batch_c" || rawBatch == "batch-c" || rawBatch == "3" || rawBatch == "batch 3" || rawBatch == "batch_3" -> "batch_c"
                rawBatch == "d" || rawBatch == "batch d" || rawBatch == "batch_d" || rawBatch == "batch-d" || rawBatch == "4" || rawBatch == "batch 4" || rawBatch == "batch_4" -> "batch_d"
                rawBatch == "all" || rawBatch == "whole" || rawBatch == "entire" || rawBatch == "general" || rawBatch == "full" || rawBatch.contains("all") || rawBatch.contains("whole") || rawBatch.contains("full") -> "all"
                else -> rawBatch.replace(Regex("[^a-z0-9]+"), "_").trim('_').take(16)
            }
            val effectiveCollege = if (cleanCollege.isBlank()) "medical_college" else cleanCollege
            val effectiveCourse = if (cleanCourse.isBlank()) "mbbs" else cleanCourse
            val effectiveBatch = if (cleanBatch.isBlank()) "batch_a" else cleanBatch
            val effectiveYear = if (admissionYear in 1990..2100) admissionYear else 2024
            return "${effectiveCollege}_${effectiveCourse}_${effectiveYear}_${effectiveBatch}"
        }
    }

    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val appStartTime = System.currentTimeMillis()
    private val locallyPostedFirestoreIds = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    private val locallyPostedNoticeIds = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    data class BatchConnectionParams(
        val college: String,
        val course: String,
        val admissionYear: Int,
        val batch: String,
        val customBatchCode: String?
    )
    private var currentBatchParams: BatchConnectionParams? = null

    private var noticeListener: ListenerRegistration? = null
    private var assignmentListener: ListenerRegistration? = null
    private var assessmentListener: ListenerRegistration? = null
    private var completedTopicListener: ListenerRegistration? = null
    private var timetableListener: ListenerRegistration? = null
    private var currentBatchKey: String? = null
    private var reconnectJob: Job? = null

    private var isNoticesConnected = false
    private var isAssignmentsConnected = false
    private var isAssessmentsConnected = false
    private var isCompletedTopicsConnected = false
    private var isTimetableConnected = false
    private var lastNoticeError: String? = null
    private var lastAssignmentError: String? = null
    private var lastAssessmentError: String? = null
    private var lastCompletedTopicsError: String? = null
    private var lastTimetableError: String? = null

    private fun updateAggregateStatus(key: String) {
        val anyPermDenied = (lastNoticeError?.contains("PERMISSION_DENIED", true) == true) ||
                (lastAssignmentError?.contains("PERMISSION_DENIED", true) == true) ||
                (lastAssessmentError?.contains("PERMISSION_DENIED", true) == true) ||
                (lastCompletedTopicsError?.contains("PERMISSION_DENIED", true) == true) ||
                (lastTimetableError?.contains("PERMISSION_DENIED", true) == true)

        if (isNoticesConnected && isAssignmentsConnected && isAssessmentsConnected) {
            _isListening.value = true
            _syncStatus.value = "Connected to $key"
        } else if (isNoticesConnected && anyPermDenied) {
            _isListening.value = true
            _syncStatus.value = "Notices online. Batch items blocked: Add 'match /shared_batches/{batchKey}/{document=**} { allow read, write: if true; }' in Firebase Console"
        } else if (isNoticesConnected) {
            _isListening.value = true
            _syncStatus.value = "Notices online. Syncing batch items..."
        } else if (anyPermDenied) {
            _isListening.value = false
            _syncStatus.value = "Permission Denied: Configure Firestore rules for 'shared_batches/{batchKey}/{document=**}' in Firebase Console"
        } else if (isAssignmentsConnected || isAssessmentsConnected || isCompletedTopicsConnected || isTimetableConnected) {
            _isListening.value = true
            _syncStatus.value = "Connected to $key (Partial)"
        } else {
            _isListening.value = false
            val err = lastNoticeError ?: lastAssignmentError ?: lastAssessmentError ?: lastCompletedTopicsError ?: lastTimetableError
            _syncStatus.value = if (err != null) "Connection error: $err" else "Connecting to $key..."
        }
    }

    private val _sharedNotices = MutableStateFlow<List<SharedBatchNotice>>(emptyList())
    val sharedNotices: StateFlow<List<SharedBatchNotice>> = _sharedNotices.asStateFlow()

    private val _sharedAssignments = MutableStateFlow<List<SharedBatchAssignment>>(emptyList())
    val sharedAssignments: StateFlow<List<SharedBatchAssignment>> = _sharedAssignments.asStateFlow()

    private val _sharedAssessments = MutableStateFlow<List<SharedBatchAssessment>>(emptyList())
    val sharedAssessments: StateFlow<List<SharedBatchAssessment>> = _sharedAssessments.asStateFlow()

    private val _sharedCompletedTopics = MutableStateFlow<List<SharedBatchCompletedTopic>>(emptyList())
    val sharedCompletedTopics: StateFlow<List<SharedBatchCompletedTopic>> = _sharedCompletedTopics.asStateFlow()

    private val _sharedTimetableClasses = MutableStateFlow<List<SharedBatchTimetableClass>>(emptyList())
    val sharedTimetableClasses: StateFlow<List<SharedBatchTimetableClass>> = _sharedTimetableClasses.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _syncStatus = MutableStateFlow("Idle")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    @Volatile private var isFirstNoticeSnapshot = true
    @Volatile private var isFirstAssignmentSnapshot = true
    @Volatile private var isFirstAssessmentSnapshot = true
    @Volatile private var isFirstCompletedTopicsSnapshot = true
    @Volatile private var isFirstTimetableSnapshot = true

    init {
        try {
            FirebaseAuth.getInstance().addAuthStateListener { auth ->
                val user = auth.currentUser
                Log.d(TAG, "FirebaseAuth state changed: user=${user?.uid}")
                if (user != null && currentBatchParams != null && !_isListening.value) {
                    val p = currentBatchParams!!
                    scope.launch {
                        delay(600)
                        startListeningToBatch(p.college, p.course, p.admissionYear, p.batch, p.customBatchCode)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register AuthStateListener: ${e.message}")
        }
    }

    private val localDeviceUid: String by lazy {
        val prefs = application.getSharedPreferences("med_planner_batch_prefs", Application.MODE_PRIVATE)
        var id = prefs.getString("device_uid", null)
        if (id.isNullOrBlank()) {
            id = "device_" + java.util.UUID.randomUUID().toString().take(12)
            prefs.edit().putString("device_uid", id).apply()
        }
        id
    }

    private suspend fun ensureAuthenticated(): String {
        val auth = FirebaseAuth.getInstance()
        val existing = auth.currentUser
        if (existing != null) return existing.uid
        return try {
            val result = auth.signInAnonymously().await()
            result.user?.uid ?: localDeviceUid
        } catch (e: Exception) {
            Log.w(TAG, "Anonymous auth fallback to device UID: ${e.message}")
            localDeviceUid
        }
    }

    fun startListeningToBatch(
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        customBatchCode: String? = null
    ) {
        val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
        currentBatchParams = BatchConnectionParams(college, course, admissionYear, batch, customBatchCode)
        if (currentBatchKey == key && _isListening.value) {
            return
        }

        stopListening()
        currentBatchKey = key
        _syncStatus.value = "Connecting to batch channel ($key)..."

        scope.launch {
            try {
                ensureAuthenticated()
            } catch (e: Exception) {
                Log.w(TAG, "Pre-auth check: ${e.message}")
            }
            attachListeners(key, college, course, admissionYear, batch, customBatchCode)
        }
    }

    private fun attachListeners(
        key: String,
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        customBatchCode: String? = null
    ) {
        if (currentBatchKey != key) return

        try {
            // 1. Listen to batch notices (no composite order to avoid missing index errors)
            noticeListener = db.collection("shared_batches")
                .document(key)
                .collection("notices")
                .limit(50)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Batch notices listener error: ${error.message}")
                        isNoticesConnected = false
                        lastNoticeError = error.message
                        updateAggregateStatus(key)
                        if (error.message?.contains("PERMISSION_DENIED", ignoreCase = true) != true) {
                            scheduleReconnect(college, course, admissionYear, batch, customBatchCode)
                        }
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        isNoticesConnected = true
                        lastNoticeError = null
                        updateAggregateStatus(key)
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: localDeviceUid
                        val notices = snapshots.documents.mapNotNull { doc -> parseNotice(doc) }
                            .sortedByDescending { it.timestamp }
                        _sharedNotices.value = notices

                        val isInitialNotice = isFirstNoticeSnapshot
                        isFirstNoticeSnapshot = false
                        scope.launch {
                            processIncomingNotices(notices, currentUserId, snapshots.metadata.hasPendingWrites() || isInitialNotice)
                        }
                    }
                }

            // 2. Listen to shared batch assignments
            assignmentListener = db.collection("shared_batches")
                .document(key)
                .collection("assignments")
                .limit(100)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Batch assignments listener error: ${error.message}")
                        isAssignmentsConnected = false
                        lastAssignmentError = error.message
                        updateAggregateStatus(key)
                        if (error.message?.contains("PERMISSION_DENIED", ignoreCase = true) != true) {
                            scheduleReconnect(college, course, admissionYear, batch, customBatchCode)
                        }
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        isAssignmentsConnected = true
                        lastAssignmentError = null
                        updateAggregateStatus(key)
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: localDeviceUid
                        val asgs = snapshots.documents.mapNotNull { parseBatchAssignment(it) }
                        _sharedAssignments.value = asgs

                        scope.launch {
                            val isInitial = isFirstAssignmentSnapshot
                            isFirstAssignmentSnapshot = false

                            for (change in snapshots.documentChanges) {
                                when (change.type) {
                                    DocumentChange.Type.ADDED,
                                    DocumentChange.Type.MODIFIED -> {
                                        val asg = parseBatchAssignment(change.document) ?: continue
                                        val localExisting = repository.getAssignmentByFirestoreId(asg.firestoreId)
                                            ?: repository.findMatchingAssignment(asg.courseCode, asg.subject, asg.title, asg.dueDate)

                                        if (localExisting == null) {
                                            val newAsg = Assignment(
                                                courseCode = asg.courseCode.trim().uppercase(),
                                                subject = asg.subject,
                                                title = asg.title,
                                                dueDate = asg.dueDate,
                                                priority = asg.priority,
                                                status = asg.status,
                                                type = asg.type,
                                                notes = if (asg.notes.isNullOrBlank()) "Shared by ${asg.authorName}" else "${asg.notes} (Shared by ${asg.authorName})",
                                                firestoreId = asg.firestoreId,
                                                authorName = asg.authorName,
                                                authorUid = asg.authorUid
                                            )
                                            val insertedId = repository.addAssignmentLocally(newAsg)
                                            Log.d(TAG, "Inserted batch assignment: ${asg.title} (ID: $insertedId)")

                                            val isLocallyPosted = locallyPostedFirestoreIds.contains(asg.firestoreId) ||
                                                    (asg.authorUid.isNotBlank() && asg.authorUid == currentUserId)
                                            val isRecentLiveEvent = asg.timestamp > (appStartTime + 5_000L)

                                            if (!isLocallyPosted && !isInitial && isRecentLiveEvent) {
                                                repository.addNotification(
                                                    InAppNotification(
                                                        title = "New Assignment: ${asg.subject}",
                                                        message = "'${asg.title}' added by ${asg.authorName} for your batch.",
                                                        timestamp = asg.timestamp,
                                                        isRead = false,
                                                        type = "assignment"
                                                    )
                                                )
                                                val notifId = asg.firestoreId.hashCode().let { if (it == Int.MIN_VALUE) 101 else kotlin.math.abs(it) % 100000 }
                                                try {
                                                    NotificationHelper.showNotification(
                                                        context = application,
                                                        title = "New Assignment: ${asg.subject}",
                                                        message = "${asg.title} (Added by: ${asg.authorName})",
                                                        notificationId = notifId,
                                                        type = "assignment",
                                                        subject = asg.subject,
                                                        targetTime = asg.dueDate
                                                    )
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Notification show error", e)
                                                }

                                                // Schedule reminder alarm for classmates
                                                try {
                                                    AcademicNotificationManager.scheduleNotification(
                                                        context = application,
                                                        type = "assignment",
                                                        itemId = "asg_$insertedId",
                                                        title = "Upcoming Assignment Alert",
                                                        message = "Assignment '${asg.title}' for ${asg.subject} is due soon!",
                                                        targetTime = asg.dueDate,
                                                        subject = asg.subject,
                                                        minutesBefore = 24 * 60
                                                    )
                                                } catch (e: Exception) {
                                                    Log.w(TAG, "Failed scheduling reminder alarm for shared assignment", e)
                                                }
                                            }
                                        } else {
                                            if ((localExisting.authorName.isBlank() || localExisting.authorName == "Classmate") &&
                                                asg.authorName.isNotBlank() && asg.authorName != "Classmate") {
                                                repository.addAssignmentLocally(localExisting.copy(
                                                    authorName = asg.authorName,
                                                    authorUid = asg.authorUid
                                                ))
                                            }
                                        }
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        val firestoreId = change.document.getString("firestoreId") ?: change.document.id
                                        val localExisting = repository.getAssignmentByFirestoreId(firestoreId)
                                        if (localExisting != null) {
                                            repository.deleteAssignment(localExisting.id)
                                            AcademicNotificationManager.cancelByItemId(application, "asg_${localExisting.id}")
                                            Log.d(TAG, "Removed deleted batch assignment: ${localExisting.title}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            // 3. Listen to shared batch assessments
            assessmentListener = db.collection("shared_batches")
                .document(key)
                .collection("assessments")
                .limit(100)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Batch assessments listener error: ${error.message}")
                        isAssessmentsConnected = false
                        lastAssessmentError = error.message
                        updateAggregateStatus(key)
                        if (error.message?.contains("PERMISSION_DENIED", ignoreCase = true) != true) {
                            scheduleReconnect(college, course, admissionYear, batch, customBatchCode)
                        }
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        isAssessmentsConnected = true
                        lastAssessmentError = null
                        updateAggregateStatus(key)
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: localDeviceUid
                        val asms = snapshots.documents.mapNotNull { parseBatchAssessment(it) }
                        _sharedAssessments.value = asms

                        scope.launch {
                            val isInitial = isFirstAssessmentSnapshot
                            isFirstAssessmentSnapshot = false

                            for (change in snapshots.documentChanges) {
                                when (change.type) {
                                    DocumentChange.Type.ADDED,
                                    DocumentChange.Type.MODIFIED -> {
                                        val asm = parseBatchAssessment(change.document) ?: continue
                                        val localExisting = repository.getAssessmentByFirestoreId(asm.firestoreId)
                                            ?: repository.findMatchingAssessment(asm.courseCode, asm.subject, asm.title, asm.date)

                                        if (localExisting == null) {
                                            val newAsm = Assessment(
                                                courseCode = asm.courseCode.trim().uppercase(),
                                                subject = asm.subject,
                                                title = asm.title,
                                                date = asm.date,
                                                type = asm.type,
                                                status = asm.status,
                                                syllabus = asm.syllabus,
                                                firestoreId = asm.firestoreId,
                                                authorName = asm.authorName,
                                                authorUid = asm.authorUid
                                            )
                                            val insertedId = repository.addAssessmentLocally(newAsm)
                                            Log.d(TAG, "Inserted batch assessment: ${asm.title} (ID: $insertedId)")

                                            val isLocallyPosted = locallyPostedFirestoreIds.contains(asm.firestoreId) ||
                                                    (asm.authorUid.isNotBlank() && asm.authorUid == currentUserId)
                                            val isRecentLiveEvent = asm.timestamp > (appStartTime + 5_000L)

                                            if (!isLocallyPosted && !isInitial && isRecentLiveEvent) {
                                                repository.addNotification(
                                                    InAppNotification(
                                                        title = "New Assessment: ${asm.subject}",
                                                        message = "'${asm.title}' scheduled by ${asm.authorName} for your batch.",
                                                        timestamp = asm.timestamp,
                                                        isRead = false,
                                                        type = "exam"
                                                    )
                                                )
                                                val notifId = asm.firestoreId.hashCode().let { if (it == Int.MIN_VALUE) 202 else kotlin.math.abs(it) % 100000 }
                                                try {
                                                    NotificationHelper.showNotification(
                                                        context = application,
                                                        title = "New Assessment: ${asm.subject}",
                                                        message = "${asm.title} (Scheduled by: ${asm.authorName})",
                                                        notificationId = notifId,
                                                        type = "assessment",
                                                        subject = asm.subject,
                                                        targetTime = asm.date
                                                    )
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Notification show error", e)
                                                }

                                                // Schedule reminder alarm for classmates
                                                try {
                                                    AcademicNotificationManager.scheduleNotification(
                                                        context = application,
                                                        type = "assessment",
                                                        itemId = "asm_$insertedId",
                                                        title = "Upcoming Assessment Alert",
                                                        message = "Assessment '${asm.title}' for ${asm.subject} is scheduled soon!",
                                                        targetTime = asm.date,
                                                        subject = asm.subject,
                                                        minutesBefore = 24 * 60
                                                    )
                                                } catch (e: Exception) {
                                                    Log.w(TAG, "Failed scheduling reminder alarm for shared assessment", e)
                                                }
                                            }
                                        } else {
                                            if ((localExisting.authorName.isBlank() || localExisting.authorName == "Classmate") &&
                                                asm.authorName.isNotBlank() && asm.authorName != "Classmate") {
                                                repository.addAssessmentLocally(localExisting.copy(
                                                    authorName = asm.authorName,
                                                    authorUid = asm.authorUid
                                                ))
                                            }
                                        }
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        val firestoreId = change.document.getString("firestoreId") ?: change.document.id
                                        val localExisting = repository.getAssessmentByFirestoreId(firestoreId)
                                        if (localExisting != null) {
                                            repository.deleteAssessment(localExisting.id)
                                            AcademicNotificationManager.cancelByItemId(application, "asm_${localExisting.id}")
                                            Log.d(TAG, "Removed deleted batch assessment: ${localExisting.title}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            // 4. Listen to shared batch completed chapters (Common for batch)
            completedTopicListener = db.collection("shared_batches")
                .document(key)
                .collection("completed_chapters")
                .limit(150)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Batch completed chapters listener error: ${error.message}")
                        isCompletedTopicsConnected = false
                        lastCompletedTopicsError = error.message
                        updateAggregateStatus(key)
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        isCompletedTopicsConnected = true
                        lastCompletedTopicsError = null
                        updateAggregateStatus(key)
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: localDeviceUid
                        val topics = snapshots.documents.mapNotNull { parseBatchCompletedTopic(it) }
                        _sharedCompletedTopics.value = topics

                        scope.launch {
                            val isInitial = isFirstCompletedTopicsSnapshot
                            isFirstCompletedTopicsSnapshot = false

                            for (change in snapshots.documentChanges) {
                                when (change.type) {
                                    DocumentChange.Type.ADDED,
                                    DocumentChange.Type.MODIFIED -> {
                                        val topic = parseBatchCompletedTopic(change.document) ?: continue
                                        val localExisting = repository.getCompletedTopicByFirestoreId(topic.firestoreId)
                                            ?: repository.findMatchingSimilarCompletedTopic(topic.courseCode, topic.subject, topic.topicTitle)

                                        if (localExisting == null) {
                                            val newTopic = CompletedSyllabusTopic(
                                                courseCode = topic.courseCode.trim().uppercase(),
                                                academicYear = topic.academicYear,
                                                subject = topic.subject,
                                                topicTitle = topic.topicTitle,
                                                completionDate = topic.completionDate,
                                                teacherName = topic.teacherName,
                                                notes = if (topic.notes.isNullOrBlank()) "Completed with batch (by ${topic.authorName})" else "${topic.notes} (Recorded by ${topic.authorName})",
                                                isSharedWithBatch = true,
                                                firestoreId = topic.firestoreId,
                                                authorName = topic.authorName,
                                                authorUid = topic.authorUid
                                            )
                                            val insertedId = repository.addCompletedTopicLocally(newTopic)
                                            Log.d(TAG, "Inserted batch completed chapter: ${topic.topicTitle} (ID: $insertedId)")

                                            val isLocallyPosted = locallyPostedFirestoreIds.contains(topic.firestoreId) ||
                                                    (topic.authorUid.isNotBlank() && topic.authorUid == currentUserId)

                                            // NEVER alert for all completed chapters upon opening the app
                                            val isRecentLiveEvent = topic.timestamp > (appStartTime + 5_000L)
                                            if (!isLocallyPosted && !isInitial && isRecentLiveEvent) {
                                                repository.addNotification(
                                                    InAppNotification(
                                                        title = "Chapter Completed: ${topic.subject}",
                                                        message = "'${topic.topicTitle}' marked completed by ${topic.authorName} for your batch schedule.",
                                                        timestamp = topic.timestamp,
                                                        isRead = false,
                                                        type = "syllabus"
                                                    )
                                                )
                                                val notifId = kotlin.math.abs(topic.topicTitle.hashCode()) % 100000 + 50000
                                                try {
                                                    NotificationHelper.showNotification(
                                                        context = application,
                                                        title = "Chapter Completed: ${topic.subject}",
                                                        message = "${topic.topicTitle} (Marked by: ${topic.authorName})",
                                                        notificationId = notifId,
                                                        type = "class",
                                                        subject = topic.subject,
                                                        targetTime = topic.completionDate
                                                    )
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Notification show error", e)
                                                }
                                            }
                                        } else {
                                            // Preserve and record contributor name if missing
                                            if ((localExisting.authorName.isBlank() || localExisting.authorName == "Classmate") &&
                                                topic.authorName.isNotBlank() && topic.authorName != "Classmate") {
                                                repository.updateCompletedTopic(localExisting.copy(
                                                    authorName = topic.authorName,
                                                    authorUid = topic.authorUid,
                                                    teacherName = localExisting.teacherName ?: topic.teacherName,
                                                    notes = localExisting.notes ?: topic.notes
                                                ))
                                            }
                                        }
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        val firestoreId = change.document.getString("firestoreId") ?: change.document.id
                                        repository.deleteCompletedTopicByFirestoreId(firestoreId)
                                    }
                                }
                            }
                            // Clean up duplicates immediately after processing
                            repository.deduplicateCompletedTopics()
                        }
                    }
                }

            // 5. Listen to shared batch timetable schedule (Common for batch)
            timetableListener = db.collection("shared_batches")
                .document(key)
                .collection("timetable")
                .limit(150)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Batch timetable listener error: ${error.message}")
                        isTimetableConnected = false
                        lastTimetableError = error.message
                        updateAggregateStatus(key)
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        isTimetableConnected = true
                        lastTimetableError = null
                        updateAggregateStatus(key)
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: localDeviceUid
                        val classes = snapshots.documents.mapNotNull { parseBatchTimetableClass(it) }
                        _sharedTimetableClasses.value = classes

                        scope.launch {
                            val isInitial = isFirstTimetableSnapshot
                            isFirstTimetableSnapshot = false

                            if (classes.isNotEmpty()) {
                                val batchFirestoreIds = classes.map { it.firestoreId }.toSet()
                                val localClasses = repository.getAllTimetableClassesOnce().filter {
                                    it.courseCode.equals(course, ignoreCase = true)
                                }
                                for (localCls in localClasses) {
                                    if (localCls.firestoreId.isNotBlank() && !batchFirestoreIds.contains(localCls.firestoreId)) {
                                        repository.deleteClass(localCls.id)
                                    }
                                }
                            }

                            for (change in snapshots.documentChanges) {
                                when (change.type) {
                                    DocumentChange.Type.ADDED,
                                    DocumentChange.Type.MODIFIED -> {
                                        val cls = parseBatchTimetableClass(change.document) ?: continue
                                        val localExisting = repository.getTimetableClassByFirestoreId(cls.firestoreId)
                                            ?: if (cls.periodNumber > 0) repository.findMatchingTimetableClass(cls.courseCode, cls.dayOfWeek, cls.periodNumber) else null

                                        val updatedClass = TimetableClass(
                                            id = localExisting?.id ?: 0,
                                            courseCode = cls.courseCode.trim().uppercase(),
                                            dayOfWeek = cls.dayOfWeek,
                                            periodNumber = cls.periodNumber,
                                            subject = cls.subject,
                                            startTime = cls.startTime,
                                            endTime = cls.endTime,
                                            room = cls.room,
                                            teacherName = cls.teacherName,
                                            colorHex = cls.colorHex,
                                            firestoreId = cls.firestoreId,
                                            authorName = cls.authorName,
                                            authorUid = cls.authorUid
                                        )
                                        repository.addClassLocally(updatedClass)
                                        Log.d(TAG, "Synced batch timetable class: ${cls.subject} P${cls.periodNumber}")

                                        val isLocallyPosted = locallyPostedFirestoreIds.contains(cls.firestoreId) ||
                                                (cls.authorUid.isNotBlank() && cls.authorUid == currentUserId)

                                        if (!isLocallyPosted && localExisting == null && !isInitial) {
                                            repository.addNotification(
                                                InAppNotification(
                                                    title = "Timetable Updated: ${cls.subject}",
                                                    message = "Class on ${getDayName(cls.dayOfWeek)} Period ${cls.periodNumber} (${cls.startTime} - ${cls.endTime}) updated by ${cls.authorName}.",
                                                    timestamp = cls.timestamp,
                                                    isRead = false,
                                                    type = "class"
                                                )
                                            )
                                        }
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        val firestoreId = change.document.getString("firestoreId") ?: change.document.id
                                        repository.deleteTimetableClassByFirestoreId(firestoreId)
                                    }
                                }
                            }
                            repository.deduplicateTimetableClasses()
                        }
                    }
                }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to register batch snapshot listeners", e)
            _isListening.value = false
            val isPerm = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true
            _syncStatus.value = if (isPerm) "Permission Denied: Configure Firestore rules in Firebase Console" else "Error: ${e.localizedMessage}"
            scheduleReconnect(college, course, admissionYear, batch, customBatchCode)
        }
    }

    private fun scheduleReconnect(college: String, course: String, admissionYear: Int, batch: String, customBatchCode: String? = null) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5000)
            if (!_isListening.value) {
                Log.i(TAG, "Retrying batch sync connection...")
                startListeningToBatch(college, course, admissionYear, batch, customBatchCode)
            }
        }
    }

    private fun getDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            7 -> "Sunday"
            else -> "Day $dayOfWeek"
        }
    }

    fun stopListening() {
        reconnectJob?.cancel()
        reconnectJob = null
        noticeListener?.remove()
        noticeListener = null
        assignmentListener?.remove()
        assignmentListener = null
        assessmentListener?.remove()
        assessmentListener = null
        completedTopicListener?.remove()
        completedTopicListener = null
        timetableListener?.remove()
        timetableListener = null
        currentBatchKey = null
        isNoticesConnected = false
        isAssignmentsConnected = false
        isAssessmentsConnected = false
        isCompletedTopicsConnected = false
        isTimetableConnected = false
        lastNoticeError = null
        lastAssignmentError = null
        lastAssessmentError = null
        lastCompletedTopicsError = null
        lastTimetableError = null
        isFirstNoticeSnapshot = true
        isFirstAssignmentSnapshot = true
        isFirstAssessmentSnapshot = true
        isFirstCompletedTopicsSnapshot = true
        isFirstTimetableSnapshot = true
        _isListening.value = false
        _syncStatus.value = "Stopped"
    }

    private fun parseNotice(doc: DocumentSnapshot): SharedBatchNotice? {
        return try {
            SharedBatchNotice(
                id = doc.id,
                batchKey = doc.getString("batchKey") ?: "",
                title = doc.getString("title") ?: "Batch Notice",
                message = doc.getString("message") ?: "",
                authorName = doc.getString("authorName") ?: "Batch Representative",
                authorUid = doc.getString("authorUid") ?: "",
                category = doc.getString("category") ?: "batch_notice",
                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                urgent = doc.getBoolean("urgent") ?: false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing notice ${doc.id}", e)
            null
        }
    }

    private fun parseBatchAssignment(doc: DocumentSnapshot): SharedBatchAssignment? {
        return try {
            SharedBatchAssignment(
                firestoreId = doc.getString("firestoreId") ?: doc.id,
                batchKey = doc.getString("batchKey") ?: "",
                courseCode = doc.getString("courseCode") ?: "MBBS",
                subject = doc.getString("subject") ?: "",
                title = doc.getString("title") ?: "",
                dueDate = doc.getLong("dueDate") ?: System.currentTimeMillis(),
                priority = doc.getString("priority") ?: "Medium",
                status = doc.getString("status") ?: "Pending",
                type = doc.getString("type") ?: "Assignment",
                notes = doc.getString("notes"),
                authorName = doc.getString("authorName") ?: "Classmate",
                authorUid = doc.getString("authorUid") ?: "",
                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing batch assignment ${doc.id}", e)
            null
        }
    }

    private fun parseBatchAssessment(doc: DocumentSnapshot): SharedBatchAssessment? {
        return try {
            SharedBatchAssessment(
                firestoreId = doc.getString("firestoreId") ?: doc.id,
                batchKey = doc.getString("batchKey") ?: "",
                courseCode = doc.getString("courseCode") ?: "MBBS",
                subject = doc.getString("subject") ?: "",
                title = doc.getString("title") ?: "",
                date = doc.getLong("date") ?: System.currentTimeMillis(),
                type = doc.getString("type") ?: "Internal",
                status = doc.getString("status") ?: "Upcoming",
                syllabus = doc.getString("syllabus"),
                authorName = doc.getString("authorName") ?: "Classmate",
                authorUid = doc.getString("authorUid") ?: "",
                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing batch assessment ${doc.id}", e)
            null
        }
    }

    private fun parseBatchCompletedTopic(doc: DocumentSnapshot): SharedBatchCompletedTopic? {
        return try {
            SharedBatchCompletedTopic(
                firestoreId = doc.getString("firestoreId") ?: doc.id,
                batchKey = doc.getString("batchKey") ?: "",
                courseCode = doc.getString("courseCode") ?: "MBBS",
                academicYear = doc.getString("academicYear") ?: "1st Year",
                subject = doc.getString("subject") ?: "",
                topicTitle = doc.getString("topicTitle") ?: "",
                completionDate = doc.getLong("completionDate") ?: System.currentTimeMillis(),
                teacherName = doc.getString("teacherName"),
                notes = doc.getString("notes"),
                authorName = doc.getString("authorName") ?: "Classmate",
                authorUid = doc.getString("authorUid") ?: "",
                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing batch completed topic ${doc.id}", e)
            null
        }
    }

    private fun parseBatchTimetableClass(doc: DocumentSnapshot): SharedBatchTimetableClass? {
        return try {
            SharedBatchTimetableClass(
                firestoreId = doc.getString("firestoreId") ?: doc.id,
                batchKey = doc.getString("batchKey") ?: "",
                courseCode = doc.getString("courseCode") ?: "MBBS",
                dayOfWeek = doc.getLong("dayOfWeek")?.toInt() ?: 1,
                periodNumber = doc.getLong("periodNumber")?.toInt() ?: 1,
                subject = doc.getString("subject") ?: "",
                startTime = doc.getString("startTime") ?: "",
                endTime = doc.getString("endTime") ?: "",
                room = doc.getString("room"),
                teacherName = doc.getString("teacherName"),
                colorHex = doc.getString("colorHex") ?: "#4F46E5",
                authorName = doc.getString("authorName") ?: "Classmate",
                authorUid = doc.getString("authorUid") ?: "",
                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing batch timetable class ${doc.id}", e)
            null
        }
    }

    private suspend fun processIncomingNotices(
        notices: List<SharedBatchNotice>,
        currentUserId: String,
        hasPendingWrites: Boolean
    ) {
        val existingNotifications = repository.getNotifications().first()
        for (notice in notices) {
            val alreadyAlerted = existingNotifications.any {
                it.title == notice.title && it.message.contains(notice.message.take(20))
            }

            val isAuthor = locallyPostedNoticeIds.contains(notice.id) ||
                    (notice.authorUid.isNotBlank() && notice.authorUid == currentUserId)
            val isRecentLiveEvent = notice.timestamp > (appStartTime + 5_000L)

            if (!alreadyAlerted && isRecentLiveEvent && !hasPendingWrites) {
                val alertType = when (notice.category) {
                    "shared_assignment" -> "assignment"
                    "exam_alert" -> "exam"
                    else -> "batch_notice"
                }

                val newNotification = InAppNotification(
                    title = notice.title,
                    message = "${notice.message}\n(Posted by: ${notice.authorName})",
                    timestamp = notice.timestamp,
                    isRead = false,
                    type = alertType
                )
                repository.addNotification(newNotification)

                // Trigger heads-up notification for classmates
                if (!isAuthor && !hasPendingWrites) {
                    val rawId = notice.title.hashCode() xor notice.message.hashCode()
                    val notificationId = if (rawId == Int.MIN_VALUE) 0 else kotlin.math.abs(rawId) % 100000
                    try {
                        NotificationHelper.showNotification(
                            context = application,
                            title = "[${if (notice.urgent) "URGENT BATCH" else "Batch Alert"}] ${notice.title}",
                            message = "${notice.message} (From: ${notice.authorName})",
                            notificationId = notificationId,
                            type = alertType
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to display system notification for shared notice", e)
                    }
                }
            }
        }
    }

    suspend fun postSharedAssignment(
        assignment: Assignment,
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        authorName: String,
        customBatchCode: String? = null
    ): Result<String> {
        return try {
            val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
            val uid = ensureAuthenticated()
            val docRef = db.collection("shared_batches")
                .document(key)
                .collection("assignments")
                .document(assignment.firestoreId)

            val data = hashMapOf(
                "firestoreId" to assignment.firestoreId,
                "batchKey" to key,
                "courseCode" to assignment.courseCode.trim().uppercase(),
                "subject" to assignment.subject,
                "title" to assignment.title,
                "dueDate" to assignment.dueDate,
                "priority" to assignment.priority,
                "status" to assignment.status,
                "type" to assignment.type,
                "notes" to (assignment.notes ?: ""),
                "authorName" to authorName.ifBlank { "Classmate" },
                "authorUid" to uid,
                "timestamp" to System.currentTimeMillis()
            )
            docRef.set(data).await()
            locallyPostedFirestoreIds.add(assignment.firestoreId)
            _syncStatus.value = "Synced with $key"
            Log.i(TAG, "Shared assignment '${assignment.title}' to batch $key")

            // Confirmation alert for the author
            try {
                val notifId = kotlin.math.abs(assignment.title.hashCode()) % 100000 + 30000
                NotificationHelper.showNotification(
                    context = application,
                    title = "Assignment Shared with Batch",
                    message = "${assignment.title} (${assignment.subject}) shared with $batch.",
                    notificationId = notifId,
                    type = "assignment",
                    subject = assignment.subject,
                    targetTime = assignment.dueDate
                )
            } catch (e: Exception) {
                Log.w(TAG, "Confirmation notification failed: ${e.message}")
            }

            Result.success(docRef.id)
        } catch (e: Exception) {
            val isPerm = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true
            val errorMsg = if (isPerm) {
                "Firebase Permission Denied: Allow shared_batches read/write in Firebase Console"
            } else {
                e.localizedMessage ?: "Unknown network error"
            }
            _syncStatus.value = "Share failed: $errorMsg"
            Log.e(TAG, "Error posting shared assignment", e)
            Result.failure(Exception(errorMsg, e))
        }
    }

    suspend fun postSharedAssessment(
        assessment: Assessment,
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        authorName: String,
        customBatchCode: String? = null
    ): Result<String> {
        return try {
            val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
            val uid = ensureAuthenticated()
            val docRef = db.collection("shared_batches")
                .document(key)
                .collection("assessments")
                .document(assessment.firestoreId)

            val data = hashMapOf(
                "firestoreId" to assessment.firestoreId,
                "batchKey" to key,
                "courseCode" to assessment.courseCode.trim().uppercase(),
                "subject" to assessment.subject,
                "title" to assessment.title,
                "date" to assessment.date,
                "type" to assessment.type,
                "status" to assessment.status,
                "syllabus" to (assessment.syllabus ?: ""),
                "authorName" to authorName.ifBlank { "Classmate" },
                "authorUid" to uid,
                "timestamp" to System.currentTimeMillis()
            )
            docRef.set(data).await()
            locallyPostedFirestoreIds.add(assessment.firestoreId)
            _syncStatus.value = "Synced with $key"
            Log.i(TAG, "Shared assessment '${assessment.title}' to batch $key")

            // Confirmation alert for the author
            try {
                val notifId = kotlin.math.abs(assessment.title.hashCode()) % 100000 + 40000
                NotificationHelper.showNotification(
                    context = application,
                    title = "Assessment Scheduled with Batch",
                    message = "${assessment.title} (${assessment.subject}) broadcasted to $batch.",
                    notificationId = notifId,
                    type = "assessment",
                    subject = assessment.subject,
                    targetTime = assessment.date
                )
            } catch (e: Exception) {
                Log.w(TAG, "Confirmation notification failed: ${e.message}")
            }

            Result.success(docRef.id)
        } catch (e: Exception) {
            val isPerm = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true
            val errorMsg = if (isPerm) {
                "Firebase Permission Denied: Allow shared_batches read/write in Firebase Console"
            } else {
                e.localizedMessage ?: "Unknown network error"
            }
            _syncStatus.value = "Share failed: $errorMsg"
            Log.e(TAG, "Error posting shared assessment", e)
            Result.failure(Exception(errorMsg, e))
        }
    }

    suspend fun deleteSharedAssignment(
        firestoreId: String,
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        customBatchCode: String? = null
    ) {
        try {
            val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
            db.collection("shared_batches")
                .document(key)
                .collection("assignments")
                .document(firestoreId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete shared assignment: ${e.message}")
        }
    }

    suspend fun deleteSharedAssessment(
        firestoreId: String,
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        customBatchCode: String? = null
    ) {
        try {
            val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
            db.collection("shared_batches")
                .document(key)
                .collection("assessments")
                .document(firestoreId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete shared assessment: ${e.message}")
        }
    }

    suspend fun postSharedCompletedTopic(
        topic: CompletedSyllabusTopic,
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        authorName: String,
        customBatchCode: String? = null
    ): Result<String> {
        return try {
            val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
            val uid = ensureAuthenticated()
            val docRef = db.collection("shared_batches")
                .document(key)
                .collection("completed_chapters")
                .document(topic.firestoreId)

            val data = hashMapOf(
                "firestoreId" to topic.firestoreId,
                "batchKey" to key,
                "courseCode" to topic.courseCode.trim().uppercase(),
                "academicYear" to topic.academicYear,
                "subject" to topic.subject,
                "topicTitle" to topic.topicTitle,
                "completionDate" to topic.completionDate,
                "teacherName" to (topic.teacherName ?: ""),
                "notes" to (topic.notes ?: ""),
                "authorName" to authorName.ifBlank { "Classmate" },
                "authorUid" to uid,
                "timestamp" to System.currentTimeMillis()
            )
            docRef.set(data).await()
            locallyPostedFirestoreIds.add(topic.firestoreId)
            _syncStatus.value = "Synced with $key"
            Log.i(TAG, "Shared completed chapter '${topic.topicTitle}' to batch $key")

            // Local alert confirmation
            try {
                val notifId = kotlin.math.abs(topic.topicTitle.hashCode()) % 100000 + 45000
                NotificationHelper.showNotification(
                    context = application,
                    title = "Chapter Shared with Batch",
                    message = "${topic.topicTitle} (${topic.subject}) logged for $batch.",
                    notificationId = notifId,
                    type = "class",
                    subject = topic.subject,
                    targetTime = topic.completionDate
                )
            } catch (e: Exception) {
                Log.w(TAG, "Confirmation notification failed: ${e.message}")
            }

            Result.success(docRef.id)
        } catch (e: Exception) {
            val isPerm = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true
            val errorMsg = if (isPerm) "Firebase Permission Denied: Allow shared_batches read/write in Firebase Console" else (e.localizedMessage ?: "Unknown network error")
            _syncStatus.value = "Share failed: $errorMsg"
            Log.e(TAG, "Error posting shared completed topic", e)
            Result.failure(Exception(errorMsg, e))
        }
    }

    suspend fun deleteSharedCompletedTopic(
        firestoreId: String,
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        customBatchCode: String? = null
    ) {
        try {
            val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
            db.collection("shared_batches")
                .document(key)
                .collection("completed_chapters")
                .document(firestoreId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete shared completed topic: ${e.message}")
        }
    }

    suspend fun postSharedTimetableClass(
        cls: TimetableClass,
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        authorName: String,
        customBatchCode: String? = null
    ): Result<String> {
        return try {
            val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
            val uid = ensureAuthenticated()
            val docRef = db.collection("shared_batches")
                .document(key)
                .collection("timetable")
                .document(cls.firestoreId)

            val data = hashMapOf(
                "firestoreId" to cls.firestoreId,
                "batchKey" to key,
                "courseCode" to cls.courseCode.trim().uppercase(),
                "dayOfWeek" to cls.dayOfWeek,
                "periodNumber" to cls.periodNumber,
                "subject" to cls.subject,
                "startTime" to cls.startTime,
                "endTime" to cls.endTime,
                "room" to (cls.room ?: ""),
                "teacherName" to (cls.teacherName ?: ""),
                "colorHex" to cls.colorHex,
                "authorName" to authorName.ifBlank { "Classmate" },
                "authorUid" to uid,
                "timestamp" to System.currentTimeMillis()
            )
            docRef.set(data).await()
            locallyPostedFirestoreIds.add(cls.firestoreId)
            _syncStatus.value = "Synced with $key"
            Log.i(TAG, "Shared timetable class '${cls.subject}' to batch $key")
            Result.success(docRef.id)
        } catch (e: Exception) {
            val isPerm = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true
            val errorMsg = if (isPerm) "Firebase Permission Denied: Allow shared_batches read/write in Firebase Console" else (e.localizedMessage ?: "Unknown network error")
            _syncStatus.value = "Share failed: $errorMsg"
            Log.e(TAG, "Error posting shared timetable class", e)
            Result.failure(Exception(errorMsg, e))
        }
    }

    suspend fun postSharedFullTimetable(
        classes: List<TimetableClass>,
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        authorName: String,
        customBatchCode: String? = null
    ): Result<Int> {
        if (classes.isEmpty()) return Result.success(0)
        return try {
            val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
            val uid = ensureAuthenticated()
            val timetableCol = db.collection("shared_batches").document(key).collection("timetable")

            // Delete previous timetable classes for this batch to ensure old/orphan periods from past routines do not linger
            try {
                val oldSnap = timetableCol.get().await()
                if (!oldSnap.isEmpty) {
                    val deleteBatch = db.batch()
                    for (doc in oldSnap.documents) {
                        deleteBatch.delete(doc.reference)
                    }
                    deleteBatch.commit().await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Notice clearing previous timetable before posting new: ${e.message}")
            }

            val batchWriter = db.batch()
            for (cls in classes) {
                val docRef = timetableCol.document(cls.firestoreId)
                val data = hashMapOf(
                    "firestoreId" to cls.firestoreId,
                    "batchKey" to key,
                    "courseCode" to cls.courseCode.trim().uppercase(),
                    "dayOfWeek" to cls.dayOfWeek,
                    "periodNumber" to cls.periodNumber,
                    "subject" to cls.subject,
                    "startTime" to cls.startTime,
                    "endTime" to cls.endTime,
                    "room" to (cls.room ?: ""),
                    "teacherName" to (cls.teacherName ?: ""),
                    "colorHex" to cls.colorHex,
                    "authorName" to authorName.ifBlank { "Classmate" },
                    "authorUid" to uid,
                    "timestamp" to System.currentTimeMillis()
                )
                batchWriter.set(docRef, data)
                locallyPostedFirestoreIds.add(cls.firestoreId)
            }
            batchWriter.commit().await()
            _syncStatus.value = "Synced full timetable with $key"
            Log.i(TAG, "Shared ${classes.size} classes for batch $key")
            Result.success(classes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting full timetable to batch", e)
            Result.failure(e)
        }
    }

    suspend fun deleteSharedTimetableClass(
        firestoreId: String,
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        customBatchCode: String? = null
    ) {
        try {
            val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
            db.collection("shared_batches")
                .document(key)
                .collection("timetable")
                .document(firestoreId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete shared timetable class: ${e.message}")
        }
    }

    // Duplicate Detection Helpers
    fun checkDuplicateAssignment(subject: String, title: String): SharedBatchAssignment? {
        val sTrim = subject.trim()
        val tTrim = title.trim()
        return _sharedAssignments.value.firstOrNull {
            it.subject.trim().equals(sTrim, ignoreCase = true) &&
            (it.title.trim().equals(tTrim, ignoreCase = true) ||
             com.example.util.ChapterSimilarityHelper.isSimilar(it.title, tTrim, it.subject, sTrim))
        }
    }

    fun checkDuplicateAssessment(subject: String, title: String): SharedBatchAssessment? {
        val sTrim = subject.trim()
        val tTrim = title.trim()
        return _sharedAssessments.value.firstOrNull {
            it.subject.trim().equals(sTrim, ignoreCase = true) &&
            (it.title.trim().equals(tTrim, ignoreCase = true) ||
             com.example.util.ChapterSimilarityHelper.isSimilar(it.title, tTrim, it.subject, sTrim))
        }
    }

    fun checkDuplicateCompletedTopic(subject: String, topicTitle: String): SharedBatchCompletedTopic? {
        val sTrim = subject.trim()
        val tTrim = topicTitle.trim()
        return _sharedCompletedTopics.value.firstOrNull {
            com.example.util.ChapterSimilarityHelper.isSameOrSimilarSubject(it.subject, sTrim) &&
            (it.topicTitle.trim().equals(tTrim, ignoreCase = true) ||
             com.example.util.ChapterSimilarityHelper.isSimilar(it.topicTitle, tTrim, it.subject, sTrim))
        }
    }

    suspend fun checkDuplicateCompletedTopicRemote(
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        subject: String,
        topicTitle: String,
        customBatchCode: String? = null
    ): SharedBatchCompletedTopic? {
        val localMem = checkDuplicateCompletedTopic(subject, topicTitle)
        if (localMem != null) return localMem

        return try {
            val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
            val sTrim = subject.trim()
            val tTrim = topicTitle.trim()
            val snap = db.collection("shared_batches").document(key).collection("completed_chapters").limit(150).get().await()
            val topics = snap.documents.mapNotNull { parseBatchCompletedTopic(it) }
            topics.firstOrNull {
                com.example.util.ChapterSimilarityHelper.isSameOrSimilarSubject(it.subject, sTrim) &&
                (it.topicTitle.trim().equals(tTrim, ignoreCase = true) ||
                 com.example.util.ChapterSimilarityHelper.isSimilar(it.topicTitle, tTrim, it.subject, sTrim))
            }
        } catch (e: Exception) {
            null
        }
    }

    fun checkDuplicateTimetableClass(dayOfWeek: Int, periodNumber: Int): SharedBatchTimetableClass? {
        return _sharedTimetableClasses.value.firstOrNull {
            it.dayOfWeek == dayOfWeek && it.periodNumber == periodNumber
        }
    }

    suspend fun postBatchNotice(
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        title: String,
        message: String,
        authorName: String,
        category: String = "batch_notice",
        urgent: Boolean = false,
        customBatchCode: String? = null
    ): Result<String> {
        return try {
            val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
            val uid = ensureAuthenticated()
            val docRef = db.collection("shared_batches")
                .document(key)
                .collection("notices")
                .document()

            val effectiveBatchName = batch.ifBlank { "Batch A" }
            val noticeData = hashMapOf(
                "id" to docRef.id,
                "batchKey" to key,
                "title" to title.trim(),
                "message" to message.trim(),
                "authorName" to authorName.ifBlank { "Class Representative" },
                "authorUid" to uid,
                "category" to category,
                "timestamp" to System.currentTimeMillis(),
                "urgent" to urgent
            )

            docRef.set(noticeData).await()
            locallyPostedNoticeIds.add(docRef.id)

            val alertType = when (category) {
                "shared_assignment" -> "assignment"
                "exam_alert" -> "exam"
                else -> "batch_notice"
            }
            repository.addNotification(
                InAppNotification(
                    title = title.trim(),
                    message = "${message.trim()}\n(Broadcasted to $effectiveBatchName by you)",
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    type = alertType
                )
            )

            // Local system notification confirmation for sender
            try {
                val notifId = kotlin.math.abs((title + message).hashCode()) % 100000 + 50000
                NotificationHelper.showNotification(
                    context = application,
                    title = "[Broadcast Sent] ${title.trim()}",
                    message = "${message.trim()} (Sent to $effectiveBatchName)",
                    notificationId = notifId,
                    type = alertType
                )
            } catch (e: Exception) {
                Log.w(TAG, "Confirmation notification failed: ${e.message}")
            }

            Result.success(docRef.id)
        } catch (e: Exception) {
            val isPerm = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true
            val errorMsg = if (isPerm) {
                "Firebase Permission Denied: Allow shared_batches read/write in Firebase Console"
            } else {
                e.localizedMessage ?: "Unknown network error"
            }
            _syncStatus.value = "Broadcast failed: $errorMsg"
            Log.e(TAG, "Error posting batch notice", e)
            Result.failure(Exception(errorMsg, e))
        }
    }

    suspend fun testBatchSyncConnection(
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        customBatchCode: String? = null
    ): Result<String> {
        return try {
            val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
            ensureAuthenticated()
            val pingDoc = db.collection("shared_batches")
                .document(key)
                .collection("_health")
                .document("ping")
            pingDoc.set(mapOf("ping" to System.currentTimeMillis(), "status" to "ok")).await()
            _isListening.value = true
            _syncStatus.value = "Connected to $key"
            Result.success("Success: Channel '$key' is online and write permissions verified!")
        } catch (e: Exception) {
            val isPerm = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true
            val errorMsg = if (isPerm) {
                "Firebase Permission Denied: Your Firestore rules are blocking writes. In Firebase Console, ensure rules allow read/write for 'shared_batches/{batchKey}/{document=**}', or enable Anonymous Authentication."
            } else {
                "Connection failed: ${e.localizedMessage ?: "Network error"}"
            }
            _syncStatus.value = errorMsg
            Result.failure(Exception(errorMsg, e))
        }
    }

    data class BatchSyncDiagnostics(
        val batchKey: String,
        val noticesOk: Boolean,
        val noticesMessage: String,
        val assignmentsOk: Boolean,
        val assignmentsMessage: String,
        val assessmentsOk: Boolean,
        val assessmentsMessage: String,
        val isAnyRuleViolation: Boolean,
        val overallStatus: String
    )

    suspend fun diagnoseBatchSync(
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        customBatchCode: String? = null
    ): BatchSyncDiagnostics {
        val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
        try { ensureAuthenticated() } catch (_: Exception) {}

        var noticesOk = false
        var noticesMsg = ""
        var assignmentsOk = false
        var assignmentsMsg = ""
        var assessmentsOk = false
        var assessmentsMsg = ""

        // Test Notices Read
        try {
            val res = db.collection("shared_batches").document(key).collection("notices").limit(1).get().await()
            noticesOk = true
            noticesMsg = "Accessible (${res.size()} notices)"
        } catch (e: Exception) {
            noticesOk = false
            noticesMsg = e.message ?: "Failed"
        }

        // Test Assignments Read
        try {
            val res = db.collection("shared_batches").document(key).collection("assignments").limit(1).get().await()
            assignmentsOk = true
            assignmentsMsg = "Accessible (${res.size()} assignments)"
        } catch (e: Exception) {
            assignmentsOk = false
            assignmentsMsg = e.message ?: "Failed"
        }

        // Test Assessments Read
        try {
            val res = db.collection("shared_batches").document(key).collection("assessments").limit(1).get().await()
            assessmentsOk = true
            assessmentsMsg = "Accessible (${res.size()} assessments)"
        } catch (e: Exception) {
            assessmentsOk = false
            assessmentsMsg = e.message ?: "Failed"
        }

        val anyRuleViolation = noticesMsg.contains("PERMISSION_DENIED", true) ||
                assignmentsMsg.contains("PERMISSION_DENIED", true) ||
                assessmentsMsg.contains("PERMISSION_DENIED", true)

        val overall = when {
            noticesOk && assignmentsOk && assessmentsOk -> "All 3 batch subcollections (Notices, Assignments, Assessments) are accessible and communicating!"
            anyRuleViolation -> "Firebase Firestore Rules are blocking subcollections! In Firebase Console, set rules to: match /shared_batches/{batchKey}/{document=**} { allow read, write: if true; }"
            else -> "Network or connection error. Please verify device internet connectivity."
        }

        return BatchSyncDiagnostics(
            batchKey = key,
            noticesOk = noticesOk,
            noticesMessage = noticesMsg,
            assignmentsOk = assignmentsOk,
            assignmentsMessage = assignmentsMsg,
            assessmentsOk = assessmentsOk,
            assessmentsMessage = assessmentsMsg,
            isAnyRuleViolation = anyRuleViolation,
            overallStatus = overall
        )
    }

    suspend fun fetchAndSyncBatchNow(
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        customBatchCode: String? = null
    ): Result<Int> {
        return try {
            val key = computeBatchKey(college, course, admissionYear, batch, customBatchCode)
            ensureAuthenticated()
            var importedCount = 0

            // 1. Fetch assignments
            val asgSnapshots = db.collection("shared_batches")
                .document(key)
                .collection("assignments")
                .limit(250)
                .get()
                .await()

            val parsedAsgs = asgSnapshots.documents.mapNotNull { parseBatchAssignment(it) }
            _sharedAssignments.value = parsedAsgs

            for (asg in parsedAsgs) {
                val localExisting = repository.getAssignmentByFirestoreId(asg.firestoreId)
                    ?: repository.findMatchingAssignment(asg.courseCode, asg.subject, asg.title, asg.dueDate)
                if (localExisting == null) {
                    val newAsg = Assignment(
                        courseCode = asg.courseCode.trim().uppercase(),
                        subject = asg.subject,
                        title = asg.title,
                        dueDate = asg.dueDate,
                        priority = asg.priority,
                        status = asg.status,
                        type = asg.type,
                        notes = if (asg.notes.isNullOrBlank()) "Shared by ${asg.authorName}" else "${asg.notes} (Shared by ${asg.authorName})",
                        firestoreId = asg.firestoreId,
                        authorName = asg.authorName,
                        authorUid = asg.authorUid
                    )
                    repository.addAssignmentLocally(newAsg)
                    importedCount++
                }
            }

            // 2. Fetch assessments
            val asmSnapshots = db.collection("shared_batches")
                .document(key)
                .collection("assessments")
                .limit(250)
                .get()
                .await()

            val parsedAsms = asmSnapshots.documents.mapNotNull { parseBatchAssessment(it) }
            _sharedAssessments.value = parsedAsms

            for (asm in parsedAsms) {
                val localExisting = repository.getAssessmentByFirestoreId(asm.firestoreId)
                    ?: repository.findMatchingAssessment(asm.courseCode, asm.subject, asm.title, asm.date)
                if (localExisting == null) {
                    val newAsm = Assessment(
                        courseCode = asm.courseCode.trim().uppercase(),
                        subject = asm.subject,
                        title = asm.title,
                        date = asm.date,
                        type = asm.type,
                        status = asm.status,
                        syllabus = asm.syllabus,
                        firestoreId = asm.firestoreId,
                        authorName = asm.authorName,
                        authorUid = asm.authorUid
                    )
                    repository.addAssessmentLocally(newAsm)
                    importedCount++
                }
            }

            // 3. Fetch completed chapters
            val topicSnapshots = db.collection("shared_batches")
                .document(key)
                .collection("completed_chapters")
                .limit(250)
                .get()
                .await()

            val parsedTopics = topicSnapshots.documents.mapNotNull { parseBatchCompletedTopic(it) }
            _sharedCompletedTopics.value = parsedTopics

            for (topic in parsedTopics) {
                val localExisting = (if (topic.firestoreId.isNotBlank()) repository.getCompletedTopicByFirestoreId(topic.firestoreId) else null)
                    ?: repository.findMatchingCompletedTopic(topic.courseCode, topic.subject, topic.topicTitle)
                if (localExisting == null) {
                    val newTopic = CompletedSyllabusTopic(
                        courseCode = topic.courseCode.trim().uppercase(),
                        academicYear = topic.academicYear,
                        subject = topic.subject,
                        topicTitle = topic.topicTitle,
                        completionDate = topic.completionDate,
                        teacherName = topic.teacherName,
                        notes = if (topic.notes.isNullOrBlank()) "Completed with batch (by ${topic.authorName})" else "${topic.notes} (Recorded by ${topic.authorName})",
                        isSharedWithBatch = true,
                        firestoreId = topic.firestoreId,
                        authorName = topic.authorName,
                        authorUid = topic.authorUid
                    )
                    repository.addCompletedTopicLocally(newTopic)
                    importedCount++
                }
            }

            // 4. Fetch timetable schedule
            val timetableSnapshots = db.collection("shared_batches")
                .document(key)
                .collection("timetable")
                .limit(100)
                .get()
                .await()

            val batchClasses = timetableSnapshots.documents.mapNotNull { parseBatchTimetableClass(it) }
            _sharedTimetableClasses.value = batchClasses

            if (batchClasses.isNotEmpty()) {
                val batchFirestoreIds = batchClasses.map { it.firestoreId }.toSet()
                val localClasses = repository.getAllTimetableClassesOnce().filter {
                    it.courseCode.equals(course, ignoreCase = true)
                }
                for (localCls in localClasses) {
                    if (localCls.firestoreId.isNotBlank() && !batchFirestoreIds.contains(localCls.firestoreId)) {
                        repository.deleteClass(localCls.id)
                    }
                }

                for (cls in batchClasses) {
                    val localExisting = repository.getTimetableClassByFirestoreId(cls.firestoreId)
                        ?: if (cls.periodNumber > 0) repository.findMatchingTimetableClass(cls.courseCode, cls.dayOfWeek, cls.periodNumber) else null

                    val updatedClass = TimetableClass(
                        id = localExisting?.id ?: 0,
                        courseCode = cls.courseCode.trim().uppercase(),
                        dayOfWeek = cls.dayOfWeek,
                        periodNumber = cls.periodNumber,
                        subject = cls.subject,
                        startTime = cls.startTime,
                        endTime = cls.endTime,
                        room = cls.room,
                        teacherName = cls.teacherName,
                        colorHex = cls.colorHex,
                        firestoreId = cls.firestoreId,
                        authorName = cls.authorName,
                        authorUid = cls.authorUid
                    )
                    repository.addClassLocally(updatedClass)
                    importedCount++
                }
                repository.deduplicateTimetableClasses()
            }

            // 5. Fetch notices
            val noticeSnapshots = db.collection("shared_batches")
                .document(key)
                .collection("notices")
                .limit(50)
                .get()
                .await()

            val notices = noticeSnapshots.documents.mapNotNull { doc -> parseNotice(doc) }.sortedByDescending { it.timestamp }
            _sharedNotices.value = notices
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: localDeviceUid
            processIncomingNotices(notices, currentUserId, false)

            _isListening.value = true
            _syncStatus.value = "Connected to $key"
            Result.success(importedCount)
        } catch (e: Exception) {
            val isPerm = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true
            val errorMsg = if (isPerm) {
                "Firebase Permission Denied: Allow shared_batches read/write in Firebase Console"
            } else {
                e.localizedMessage ?: "Network error during sync"
            }
            _syncStatus.value = "Sync error: $errorMsg"
            Result.failure(Exception(errorMsg, e))
        }
    }
}
