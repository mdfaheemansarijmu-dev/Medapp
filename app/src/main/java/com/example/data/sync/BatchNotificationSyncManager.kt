package com.example.data.sync

import android.app.Application
import android.util.Log
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
import kotlinx.coroutines.SupervisorJob
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

class BatchNotificationSyncManager(
    private val application: Application,
    private val repository: PlannerRepository
) {
    companion object {
        private const val TAG = "BatchNotificationSync"

        fun computeBatchKey(college: String, course: String, admissionYear: Int, batch: String): String {
            val cleanCollege = college.trim().lowercase().replace(Regex("[^a-z0-9]"), "_").take(24)
            val cleanCourse = course.trim().lowercase().replace(Regex("[^a-z0-9]"), "_").take(12)
            val cleanBatch = batch.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
            val effectiveCollege = if (cleanCollege.isBlank()) "default_college" else cleanCollege
            val effectiveCourse = if (cleanCourse.isBlank()) "mbbs" else cleanCourse
            val effectiveBatch = if (cleanBatch.isBlank()) "batch_a" else cleanBatch
            return "${effectiveCollege}_${effectiveCourse}_${admissionYear}_${effectiveBatch}"
        }
    }

    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var noticeListener: ListenerRegistration? = null
    private var currentBatchKey: String? = null

    private val _sharedNotices = MutableStateFlow<List<SharedBatchNotice>>(emptyList())
    val sharedNotices: StateFlow<List<SharedBatchNotice>> = _sharedNotices.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _syncStatus = MutableStateFlow("Idle")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

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
        _syncStatus.value = "Connecting to batch feed ($key)..."

        try {
            noticeListener = db.collection("shared_batches")
                .document(key)
                .collection("notices")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(40)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Batch notices listener error: ${error.message}", error)
                        _syncStatus.value = "Sync offline: ${error.localizedMessage}"
                        _isListening.value = false
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
                        val notices = snapshots.documents.mapNotNull { doc -> parseNotice(doc) }
                        _sharedNotices.value = notices
                        _isListening.value = true
                        _syncStatus.value = "Synced with batch feed (${notices.size} notices)"

                        // Process incoming items for in-app alert inbox and system push
                        scope.launch {
                            processIncomingNotices(notices, currentUserId)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register batch snapshot listener", e)
            _syncStatus.value = "Error: ${e.localizedMessage}"
        }
    }

    fun stopListening() {
        noticeListener?.remove()
        noticeListener = null
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

    private suspend fun processIncomingNotices(notices: List<SharedBatchNotice>, currentUserId: String) {
        val existingNotifications = repository.getNotifications().first()
        for (notice in notices) {
            // Check if this notice already generated an in-app notification
            val alreadyAlerted = existingNotifications.any {
                it.title == notice.title && it.message == notice.message
            }

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

                // If notice wasn't authored by this device right now, fire system notification
                if (notice.authorUid != currentUserId) {
                    val rawId = notice.title.hashCode() xor notice.message.hashCode()
                    val notificationId = if (rawId == Int.MIN_VALUE) 0 else java.lang.Math.abs(rawId) % 100000
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
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
            val docRef = db.collection("shared_batches")
                .document(key)
                .collection("notices")
                .document()

            val noticeData = hashMapOf(
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

            // Also post locally into in-app notification immediately
            val alertType = when (category) {
                "shared_assignment" -> "assignment"
                "exam_alert" -> "exam"
                else -> "batch_notice"
            }
            repository.addNotification(
                InAppNotification(
                    title = title.trim(),
                    message = "${message.trim()}\n(Posted by you to $batch)",
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    type = alertType
                )
            )

            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting batch notice", e)
            Result.failure(e)
        }
    }
}
