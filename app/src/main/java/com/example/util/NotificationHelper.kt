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

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()

            val channels = listOf(
                NotificationChannel(
                    CHANNEL_ASSIGNMENT,
                    "Assignments & Deadlines",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts and batch updates for assignments and homework"
                    enableLights(true)
                    lightColor = Color.GREEN
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setSound(soundUri, audioAttributes)
                },
                NotificationChannel(
                    CHANNEL_ASSESSMENT,
                    "Assessments & Exams",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Critical schedules for tests, exams, vivas, and practicals"
                    enableLights(true)
                    lightColor = Color.RED
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350, 150, 350)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setSound(soundUri, audioAttributes)
                },
                NotificationChannel(
                    CHANNEL_CLASS,
                    "Upcoming Classes & Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts for upcoming academic lectures and room changes"
                    enableLights(true)
                    lightColor = Color.BLUE
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 150, 250)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setSound(soundUri, audioAttributes)
                },
                NotificationChannel(
                    CHANNEL_GENERAL,
                    "General Reminders & Batch Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "General batch updates and academic notices"
                    enableLights(true)
                    enableVibration(true)
                    setShowBadge(true)
                    setSound(soundUri, audioAttributes)
                }
            )

            channels.forEach { channel ->
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

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
        initChannels(context)
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

        // Post prominent notification (Heads-up banner + status bar icon + vibration)
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

        // Auto-recede logic ONLY applies to short-lived "class" peek reminders.
        // Assignments and assessments MUST remain visible in the notification tray so students don't miss them.
        if (type.equals("class", ignoreCase = true)) {
            CoroutineScope(Dispatchers.Main).launch {
                delay(3000L)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val activeNotifications = notificationManager.activeNotifications
                        val isStillActive = activeNotifications.any { it.id == notificationId }
                        if (!isStillActive) return@launch
                    }

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
