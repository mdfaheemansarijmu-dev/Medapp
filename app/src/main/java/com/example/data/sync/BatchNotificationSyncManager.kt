package com.example.data.sync

import android.app.Application
import android.util.Log
import com.example.data.model.Assignment
import com.example.data.model.Assessment
import com.example.data.model.InAppNotification
import com.example.data.repository.PlannerRepository
import com.example.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
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

class BatchNotificationSyncManager(
    private val application: Application,
    private val repository: PlannerRepository
) {
    companion object {
        private const val TAG = "BatchNotificationSync"

        fun computeBatchKey(college: String, course: String, admissionYear: Int, batch: String): String {
            val cleanCollege = college.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(64)
            val cleanCourse = course.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(16)
            val cleanBatch = batch.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(16)
            val effectiveCollege = if (cleanCollege.isBlank()) "medical_college" else cleanCollege
            val effectiveCourse = if (cleanCourse.isBlank()) "mbbs" else cleanCourse
            val effectiveBatch = if (cleanBatch.isBlank()) "batch_a" else cleanBatch
            val effectiveYear = if (admissionYear > 1900) admissionYear else 2024
            return "${effectiveCollege}_${effectiveCourse}_${effectiveYear}_${effectiveBatch}"
        }
    }

    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val locallyPostedFirestoreIds = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    private val locallyPostedNoticeIds = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    private var noticeListener: ListenerRegistration? = null
    private var assignmentListener: ListenerRegistration? = null
    private var assessmentListener: ListenerRegistration? = null
    private var currentBatchKey: String? = null
    private var reconnectJob: Job? = null

    private val _sharedNotices = MutableStateFlow<List<SharedBatchNotice>>(emptyList())
    val sharedNotices: StateFlow<List<SharedBatchNotice>> = _sharedNotices.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _syncStatus = MutableStateFlow("Idle")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

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
        batch: String
    ) {
        val key = computeBatchKey(college, course, admissionYear, batch)
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
        }

        try {
            // 1. Listen to batch notices
            noticeListener = db.collection("shared_batches")
                .document(key)
                .collection("notices")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(40)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Batch notices listener error: ${error.message}")
                        _isListening.value = false
                        _syncStatus.value = "Feed offline: ${error.localizedMessage ?: "Connection error"}"
                        scheduleReconnect(college, course, admissionYear, batch)
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: localDeviceUid
                        val notices = snapshots.documents.mapNotNull { doc -> parseNotice(doc) }
                        _sharedNotices.value = notices
                        _isListening.value = true
                        _syncStatus.value = "Connected to $key"

                        scope.launch {
                            processIncomingNotices(notices, currentUserId, snapshots.metadata.hasPendingWrites())
                        }
                    }
                }

            // 2. Listen to shared batch assignments
            assignmentListener = db.collection("shared_batches")
                .document(key)
                .collection("assignments")
                .limit(80)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Batch assignments listener error: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: localDeviceUid
                        scope.launch {
                            for (doc in snapshots.documents) {
                                val asg = parseBatchAssignment(doc) ?: continue
                                val localExisting = repository.getAssignmentByFirestoreId(asg.firestoreId)
                                    ?: repository.findMatchingAssignment(asg.courseCode, asg.subject, asg.title, asg.dueDate)

                                if (localExisting == null) {
                                    val newAsg = Assignment(
                                        courseCode = asg.courseCode,
                                        subject = asg.subject,
                                        title = asg.title,
                                        dueDate = asg.dueDate,
                                        priority = asg.priority,
                                        status = asg.status,
                                        type = asg.type,
                                        notes = if (asg.notes.isNullOrBlank()) "Shared by ${asg.authorName}" else "${asg.notes} (Shared by ${asg.authorName})",
                                        firestoreId = asg.firestoreId
                                    )
                                    repository.addAssignmentLocally(newAsg)
                                    Log.d(TAG, "Inserted batch assignment: ${asg.title}")

                                    val isLocallyPosted = locallyPostedFirestoreIds.contains(asg.firestoreId) ||
                                            (asg.authorUid.isNotBlank() && asg.authorUid == currentUserId)

                                    if (!isLocallyPosted) {
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
                .limit(80)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Batch assessments listener error: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: localDeviceUid
                        scope.launch {
                            for (doc in snapshots.documents) {
                                val asm = parseBatchAssessment(doc) ?: continue
                                val localExisting = repository.getAssessmentByFirestoreId(asm.firestoreId)
                                    ?: repository.findMatchingAssessment(asm.courseCode, asm.subject, asm.title, asm.date)

                                if (localExisting == null) {
                                    val newAsm = Assessment(
                                        courseCode = asm.courseCode,
                                        subject = asm.subject,
                                        title = asm.title,
                                        date = asm.date,
                                        type = asm.type,
                                        status = asm.status,
                                        syllabus = asm.syllabus,
                                        firestoreId = asm.firestoreId
                                    )
                                    repository.addAssessmentLocally(newAsm)
                                    Log.d(TAG, "Inserted batch assessment: ${asm.title}")

                                    val isLocallyPosted = locallyPostedFirestoreIds.contains(asm.firestoreId) ||
                                            (asm.authorUid.isNotBlank() && asm.authorUid == currentUserId)

                                    if (!isLocallyPosted) {
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
                                    }
                                }
                            }
                        }
                    }
                }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to register batch snapshot listeners", e)
            _isListening.value = false
            _syncStatus.value = "Error: ${e.localizedMessage}"
            scheduleReconnect(college, course, admissionYear, batch)
        }
    }

    private fun scheduleReconnect(college: String, course: String, admissionYear: Int, batch: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5000)
            if (!_isListening.value) {
                Log.i(TAG, "Retrying batch sync connection...")
                startListeningToBatch(college, course, admissionYear, batch)
            }
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
        currentBatchKey = null
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

            if (!alreadyAlerted) {
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
        authorName: String
    ): Result<String> {
        return try {
            locallyPostedFirestoreIds.add(assignment.firestoreId)
            val key = computeBatchKey(college, course, admissionYear, batch)
            val uid = ensureAuthenticated()
            val docRef = db.collection("shared_batches")
                .document(key)
                .collection("assignments")
                .document(assignment.firestoreId)

            val data = hashMapOf(
                "firestoreId" to assignment.firestoreId,
                "batchKey" to key,
                "courseCode" to assignment.courseCode,
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
            Log.e(TAG, "Error posting shared assignment", e)
            Result.failure(e)
        }
    }

    suspend fun postSharedAssessment(
        assessment: Assessment,
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        authorName: String
    ): Result<String> {
        return try {
            locallyPostedFirestoreIds.add(assessment.firestoreId)
            val key = computeBatchKey(college, course, admissionYear, batch)
            val uid = ensureAuthenticated()
            val docRef = db.collection("shared_batches")
                .document(key)
                .collection("assessments")
                .document(assessment.firestoreId)

            val data = hashMapOf(
                "firestoreId" to assessment.firestoreId,
                "batchKey" to key,
                "courseCode" to assessment.courseCode,
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
            Log.e(TAG, "Error posting shared assessment", e)
            Result.failure(e)
        }
    }

    suspend fun deleteSharedAssignment(
        firestoreId: String,
        college: String,
        course: String,
        admissionYear: Int,
        batch: String
    ) {
        try {
            val key = computeBatchKey(college, course, admissionYear, batch)
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
        batch: String
    ) {
        try {
            val key = computeBatchKey(college, course, admissionYear, batch)
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

    suspend fun postBatchNotice(
        college: String,
        course: String,
        admissionYear: Int,
        batch: String,
        title: String,
        message: String,
        authorName: String,
        category: String = "batch_notice",
        urgent: Boolean = false
    ): Result<String> {
        return try {
            val key = computeBatchKey(college, course, admissionYear, batch)
            val uid = ensureAuthenticated()
            val docRef = db.collection("shared_batches")
                .document(key)
                .collection("notices")
                .document()

            locallyPostedNoticeIds.add(docRef.id)

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
            Log.e(TAG, "Error posting batch notice", e)
            Result.failure(e)
        }
    }
}
