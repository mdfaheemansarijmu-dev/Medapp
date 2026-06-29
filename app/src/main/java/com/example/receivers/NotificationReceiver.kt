package com.example.receivers

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.room.Room
import com.example.data.local.PlannerDatabase
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    private val TAG = "NotificationReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val id = intent.getIntExtra("id", 0)
        val itemId = intent.getStringExtra("item_id") ?: ""
        val type = intent.getStringExtra("type") ?: "general"
        val title = intent.getStringExtra("title") ?: "Reminder"
        val message = intent.getStringExtra("message") ?: "Upcoming academic event"
        val subject = intent.getStringExtra("subject") ?: ""
        val targetTime = intent.getLongExtra("target_time", 0L)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        Log.d(TAG, "Notification received action: $action, ID: $id, ItemID: $itemId, Type: $type")

        when (action) {
            "com.example.ACTION_MARK_COMPLETED" -> {
                // 1. Immediately dismiss the notification
                notificationManager.cancel(id)

                // 2. Perform Room DB update asynchronously in IO Thread
                if (itemId.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = Room.databaseBuilder(
                                context.applicationContext,
                                PlannerDatabase::class.java,
                                "acuity_planner_db"
                            ).fallbackToDestructiveMigration().build()

                            when {
                                itemId.startsWith("asg_") -> {
                                    val idVal = itemId.substringAfter("asg_").toIntOrNull()
                                    if (idVal != null) {
                                        db.plannerDao().updateAssignmentStatus(idVal, "Completed")
                                        Log.d(TAG, "Successfully marked assignment $idVal as Completed in background")
                                    }
                                }
                                itemId.startsWith("asm_") -> {
                                    val idVal = itemId.substringAfter("asm_").toIntOrNull()
                                    if (idVal != null) {
                                        db.plannerDao().updateAssessmentStatus(idVal, "Completed")
                                        Log.d(TAG, "Successfully marked assessment $idVal as Completed in background")
                                    }
                                }
                                itemId.startsWith("study_") -> {
                                    val idVal = itemId.substringAfter("study_").toIntOrNull()
                                    if (idVal != null) {
                                        db.plannerDao().updateStudyTaskProgress(idVal, 100)
                                        Log.d(TAG, "Successfully marked study task $idVal as 100% completed in background")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update item status from background notification action", e)
                        }
                    }
                }
            }

            "com.example.ACTION_SNOOZE" -> {
                // 1. Immediately cancel current notification
                notificationManager.cancel(id)

                // 2. Schedule a new exact alarm in 15 minutes
                val snoozeTime = System.currentTimeMillis() + (15 * 60 * 1000L) // 15 mins later
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                val nextIntent = Intent(context, NotificationReceiver::class.java).apply {
                    putExtra("title", "$title (Snoozed)")
                    putExtra("message", message)
                    putExtra("id", id)
                    putExtra("item_id", itemId)
                    putExtra("type", type)
                    putExtra("subject", subject)
                    putExtra("target_time", targetTime)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    id,
                    nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
                        } else {
                            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
                    }
                    Log.d(TAG, "Scheduled snooze alarm successfully for ID: $id at $snoozeTime")
                } catch (e: SecurityException) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent)
                    Log.e(TAG, "Exact alarm permission missing. Fell back to standard alarm for snooze.", e)
                }
            }

            else -> {
                // Default: Display the system notification alert
                NotificationHelper.showNotification(
                    context = context,
                    title = title,
                    message = message,
                    notificationId = id,
                    itemId = itemId,
                    type = type,
                    subject = subject,
                    targetTime = targetTime
                )
            }
        }
    }
}
