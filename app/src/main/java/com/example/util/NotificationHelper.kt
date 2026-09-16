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

object NotificationHelper {
    const val CHANNEL_CLASS = "class_reminders"
    const val CHANNEL_ASSIGNMENT = "assignment_deadlines"
    const val CHANNEL_ASSESSMENT = "assessment_reminders"
    const val CHANNEL_STUDY = "study_alerts"
    const val CHANNEL_GENERAL = "general_reminders"

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

        // Create Channels (Oreo and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(alarmSound, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 1. Content click intent (Opens MainActivity)
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

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Builder setup
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Make visible on lock screen
            .setFullScreenIntent(contentPendingIntent, true) // Heads-up alert on locked screen
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setContentIntent(contentPendingIntent)

        // 2. Action: Snooze (Reschedule 15 minutes in the future)
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

        // 3. Action: Mark as Completed (Only for actionable study tasks, assignments, or assessments)
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

        // Show notification
        notificationManager.notify(notificationId, builder.build())
    }
}
