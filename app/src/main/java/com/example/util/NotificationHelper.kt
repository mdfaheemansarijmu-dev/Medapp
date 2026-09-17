package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.receivers.NotificationReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object NotificationHelper {
    const val CHANNEL_CLASS = "class_reminders_v2"
    const val CHANNEL_ASSIGNMENT = "assignment_deadlines_v2"
    const val CHANNEL_ASSESSMENT = "assessment_reminders_v2"
    const val CHANNEL_STUDY = "study_alerts_v2"
    const val CHANNEL_GENERAL = "general_reminders_v2"

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int,
        itemId: String = "",
        type: String = "general",
        subject: String = "",
        targetTime: Long = 0L
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Determine correct channel ID & properties based on notification type
        val channelId = when (type.lowercase()) {
            "class" -> CHANNEL_CLASS
            "assignment" -> CHANNEL_ASSIGNMENT
            "assessment", "exam", "viva" -> CHANNEL_ASSESSMENT
            "study" -> CHANNEL_STUDY
            else -> CHANNEL_GENERAL
        }

        // Determine standard notification category (never CATEGORY_ALARM for class alerts)
        val category = when (type.lowercase()) {
            "class" -> NotificationCompat.CATEGORY_EVENT
            "assignment", "assessment", "exam", "viva" -> NotificationCompat.CATEGORY_REMINDER
            "study" -> NotificationCompat.CATEGORY_RECOMMENDATION
            else -> NotificationCompat.CATEGORY_STATUS
        }

        // Create Channels (Oreo and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Delete legacy alarm-style channels if present
            try {
                notificationManager.deleteNotificationChannel("class_reminders")
                notificationManager.deleteNotificationChannel("assignment_deadlines")
                notificationManager.deleteNotificationChannel("assessment_reminders")
                notificationManager.deleteNotificationChannel("study_alerts")
                notificationManager.deleteNotificationChannel("general_reminders")
            } catch (e: Exception) {
                // Ignore
            }

            val name = when (channelId) {
                CHANNEL_CLASS -> "Upcoming Classes & Reminders"
                CHANNEL_ASSIGNMENT -> "Assignment & Homework Deadlines"
                CHANNEL_ASSESSMENT -> "Exams, Vivas, & Practicals"
                CHANNEL_STUDY -> "Study Revision & Goals"
                else -> "General Reminders & Notifications"
            }
            val descriptionText = when (channelId) {
                CHANNEL_CLASS -> "Alerts for upcoming academic lectures and room changes"
                CHANNEL_ASSIGNMENT -> "Urgent reminders for assignment submission dates"
                CHANNEL_ASSESSMENT -> "Critical schedules for midterms, vivas, and final exams"
                CHANNEL_STUDY -> "Reminders to study selected subjects and topics"
                else -> "General notifications and system reminders"
            }
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC

                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 1. Post initial notification: shows briefly on display (heads-up peek)
        val initialBuilder = buildNotificationBuilder(
            context = context,
            channelId = channelId,
            title = title,
            message = message,
            notificationId = notificationId,
            itemId = itemId,
            type = type,
            subject = subject,
            targetTime = targetTime,
            category = category,
            isHeadsUp = true
        )
        notificationManager.notify(notificationId, initialBuilder.build())

        // 2. Auto-recede logic: After showing the incoming class briefly on display,
        // immediately dismiss the heads-up banner from the display while persisting
        // seamlessly in the system notification panel / shade.
        CoroutineScope(Dispatchers.Main).launch {
            delay(2500L)
            try {
                // Ensure notification is still active and hasn't been dismissed or tapped
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val activeNotifications = notificationManager.activeNotifications
                    val isStillActive = activeNotifications.any { it.id == notificationId }
                    if (!isStillActive) return@launch
                }

                // Update notification with low display priority & silence so it recedes from screen display
                // but persists cleanly in the notification panel / drawer.
                val persistentBuilder = buildNotificationBuilder(
                    context = context,
                    channelId = channelId,
                    title = title,
                    message = message,
                    notificationId = notificationId,
                    itemId = itemId,
                    type = type,
                    subject = subject,
                    targetTime = targetTime,
                    category = category,
                    isHeadsUp = false
                )
                notificationManager.notify(notificationId, persistentBuilder.build())
            } catch (e: Exception) {
                // Safe handling
            }
        }
    }

    private fun buildNotificationBuilder(
        context: Context,
        channelId: String,
        title: String,
        message: String,
        notificationId: Int,
        itemId: String,
        type: String,
        subject: String,
        targetTime: Long,
        category: String,
        isHeadsUp: Boolean
    ): NotificationCompat.Builder {
        // Content click intent (Opens MainActivity)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "planner")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 10,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(category)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        if (isHeadsUp) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 250, 150, 250))
        } else {
            // Keep notification quietly in the notification drawer/panel without lingering on display
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setOnlyAlertOnce(true)
        }

        // Action 1: Snooze (Reschedule 15 minutes in the future)
        val snoozeIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = "com.example.ACTION_SNOOZE"
            putExtra("id", notificationId)
            putExtra("item_id", itemId)
            putExtra("type", type)
            putExtra("title", title)
            putExtra("message", message)
            putExtra("subject", subject)
            putExtra("target_time", targetTime)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 20,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.ic_lock_idle_alarm,
            "Snooze 15m",
            snoozePendingIntent
        )

        // Action 2: Mark as Completed (Only for actionable study tasks, assignments, or assessments)
        val showCompletedAction = when (type.lowercase()) {
            "assignment", "assessment", "exam", "viva", "study" -> true
            else -> false
        }

        if (showCompletedAction && itemId.isNotEmpty()) {
            val doneIntent = Intent(context, NotificationReceiver::class.java).apply {
                action = "com.example.ACTION_MARK_COMPLETED"
                putExtra("id", notificationId)
                putExtra("item_id", itemId)
                putExtra("type", type)
            }
            val donePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 30,
                doneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.checkbox_on_background,
                "Mark Done",
                donePendingIntent
            )
        }

        return builder
    }
}
