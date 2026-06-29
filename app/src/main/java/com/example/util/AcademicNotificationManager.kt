package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.model.ScheduledNotification
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Calendar

object AcademicNotificationManager {
    private const val PREFS_NAME = "academic_notifications_prefs"
    private const val KEY_SCHEDULED = "scheduled_notifications"
    private const val TAG = "NotificationManager"

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, ScheduledNotification::class.java)
    private val adapter = moshi.adapter<List<ScheduledNotification>>(listType)

    // Load list of already scheduled notifications
    @Synchronized
    fun getScheduledNotifications(context: Context): List<ScheduledNotification> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SCHEDULED, null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse scheduled notifications", e)
            emptyList()
        }
    }

    // Save list of scheduled notifications
    @Synchronized
    private fun saveScheduledNotifications(context: Context, list: List<ScheduledNotification>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val json = adapter.toJson(list)
            prefs.edit().putString(KEY_SCHEDULED, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize scheduled notifications", e)
        }
    }

    // Schedule a single notification
    @Synchronized
    fun scheduleNotification(
        context: Context,
        type: String,
        itemId: String,
        title: String,
        message: String,
        targetTime: Long,
        subject: String,
        minutesBefore: Int = 30
    ) {
        val triggerTime = targetTime - (minutesBefore * 60 * 1000L)
        if (triggerTime <= System.currentTimeMillis()) {
            // Already in past, don't schedule
            return
        }

        val currentList = getScheduledNotifications(context).toMutableList()

        // Requirement 3 & 9: Before scheduling a notification, check whether a notification for the same class, date, and time already exists.
        // Check for duplicate: same type, subject/class, and triggerTime (date & time)
        val alreadyExists = currentList.any {
            it.type == type &&
            it.subject.equals(subject, ignoreCase = true) &&
            it.targetTime == targetTime
        }

        if (alreadyExists) {
            Log.d(TAG, "Notification for $type: $subject at $targetTime already exists. Skipping duplicate.")
            return
        }

        // Requirement 5: Assign a unique ID to every notification.
        // We can generate unique ID via hashCode of distinct properties
        val idString = "$type:$itemId:$targetTime"
        val uniqueId = idString.hashCode()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, com.example.receivers.NotificationReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("id", uniqueId)
            putExtra("item_id", itemId)
            putExtra("type", type)
            putExtra("subject", subject)
            putExtra("target_time", targetTime)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            uniqueId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }

            // Save to persistent record
            val newNotification = ScheduledNotification(
                id = uniqueId,
                type = type,
                itemId = itemId,
                title = title,
                message = message,
                triggerTime = triggerTime,
                subject = subject,
                targetTime = targetTime
            )
            currentList.add(newNotification)
            saveScheduledNotifications(context, currentList)
            Log.d(TAG, "Successfully scheduled notification ID: $uniqueId for $type: $subject")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule exact alarm", e)
        }
    }

    // Requirement 6: Cancel and recreate notifications if the timetable changes.
    @Synchronized
    fun cancelAllByType(context: Context, type: String) {
        val currentList = getScheduledNotifications(context).toMutableList()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val iterator = currentList.iterator()

        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.type == type) {
                // Cancel Alarm
                val intent = Intent(context, com.example.receivers.NotificationReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    item.id,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
                iterator.remove()
                Log.d(TAG, "Canceled alarm with ID: ${item.id} of type: $type")
            }
        }
        saveScheduledNotifications(context, currentList)
    }

    @Synchronized
    fun cancelNotification(context: Context, id: Int) {
        val currentList = getScheduledNotifications(context).toMutableList()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val itemIndex = currentList.indexOfFirst { it.id == id }
        if (itemIndex != -1) {
            val item = currentList[itemIndex]
            val intent = Intent(context, com.example.receivers.NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                item.id,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
            currentList.removeAt(itemIndex)
            saveScheduledNotifications(context, currentList)
            Log.d(TAG, "Canceled single alarm with ID: $id")
        }
    }

    @Synchronized
    fun cancelByItemId(context: Context, itemId: String) {
        val currentList = getScheduledNotifications(context).toMutableList()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val itemIndex = currentList.indexOfFirst { it.itemId == itemId }
        if (itemIndex != -1) {
            val item = currentList[itemIndex]
            val intent = Intent(context, com.example.receivers.NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                item.id,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
            currentList.removeAt(itemIndex)
            saveScheduledNotifications(context, currentList)
            Log.d(TAG, "Canceled alarm with itemId: $itemId")
        }
    }

    // Verify existing notifications (removes past entries)
    @Synchronized
    fun verifyExistingNotifications(context: Context) {
        // Requirement 8: On app launch, only verify existing notifications instead of recreating them.
        val currentList = getScheduledNotifications(context)
        val now = System.currentTimeMillis()
        
        // Remove past notifications from SharedPreferences record
        val futureNotifications = currentList.filter { it.triggerTime > now }
        if (futureNotifications.size != currentList.size) {
            saveScheduledNotifications(context, futureNotifications)
            Log.d(TAG, "Verified existing notifications. Cleared ${currentList.size - futureNotifications.size} expired items.")
        } else {
            Log.d(TAG, "Verified existing notifications. All ${currentList.size} items are in the future.")
        }
    }

    // Reschedule all future notifications on boot/settings change
    @Synchronized
    fun rescheduleAllFutureNotifications(context: Context) {
        val currentList = getScheduledNotifications(context)
        val now = System.currentTimeMillis()
        val futureNotifications = currentList.filter { it.triggerTime > now }
        
        // Clear all from OS system alarm manager first to prevent duplication
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (item in futureNotifications) {
            val intent = Intent(context, com.example.receivers.NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                item.id,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }

        // Re-schedule future ones
        for (item in futureNotifications) {
            val intent = Intent(context, com.example.receivers.NotificationReceiver::class.java).apply {
                putExtra("title", item.title)
                putExtra("message", item.message)
                putExtra("id", item.id)
                putExtra("item_id", item.itemId)
                putExtra("type", item.type)
                putExtra("subject", item.subject)
                putExtra("target_time", item.targetTime)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                item.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.triggerTime, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.triggerTime, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.triggerTime, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, item.triggerTime, pendingIntent)
                }
                Log.d(TAG, "Rescheduled alarm with ID: ${item.id}")
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to reschedule alarm ID: ${item.id}", e)
            }
        }
    }
}
