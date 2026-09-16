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
        val now = System.currentTimeMillis()
        if (targetTime <= now) {
            // Event is already in the past, skip
            return
        }

        val calculatedTriggerTime = targetTime - (minutesBefore * 60 * 1000L)
        val triggerTime = if (calculatedTriggerTime <= now) {
            now + 2000L // If reminder time passed but event hasn't started, notify immediately in 2s
        } else {
            calculatedTriggerTime
        }

        val currentList = getScheduledNotifications(context).toMutableList()

        // Check for duplicate: same type, subject/class, and targetTime
        val alreadyExists = currentList.any {
            it.type == type &&
            it.subject.equals(subject, ignoreCase = true) &&
            it.targetTime == targetTime
        }

        if (alreadyExists) {
            Log.d(TAG, "Notification for $type: $subject at $targetTime already exists. Skipping duplicate.")
            return
        }

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
            val showIntent = PendingIntent.getActivity(
                context,
                uniqueId,
                Intent(context, com.example.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val clockInfo = AlarmManager.AlarmClockInfo(triggerTime, showIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)

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
            Log.d(TAG, "Successfully scheduled alarm clock ID: $uniqueId for $type: $subject at trigger $triggerTime")
        } catch (e: Exception) {
            Log.w(TAG, "Failed setAlarmClock, trying setExactAndAllowWhileIdle fallback: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
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
            } catch (e2: Exception) {
                Log.e(TAG, "Failed all alarm scheduling for ID: $uniqueId", e2)
            }
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

    // Verify existing notifications (removes past entries where event target time has passed)
    @Synchronized
    fun verifyExistingNotifications(context: Context) {
        val currentList = getScheduledNotifications(context)
        val now = System.currentTimeMillis()
        
        // Remove notifications where target event time is in the past
        val futureNotifications = currentList.filter { it.targetTime > now }
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
        val futureNotifications = currentList.filter { it.targetTime > now }
        
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
            val triggerTime = if (item.triggerTime <= now) now + 2000L else item.triggerTime
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
                val showIntent = PendingIntent.getActivity(
                    context,
                    item.id,
                    Intent(context, com.example.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val clockInfo = AlarmManager.AlarmClockInfo(triggerTime, showIntent)
                alarmManager.setAlarmClock(clockInfo, pendingIntent)
                Log.d(TAG, "Rescheduled alarm clock ID: ${item.id} at $triggerTime")
            } catch (e: Exception) {
                Log.w(TAG, "Failed setAlarmClock during reschedule, trying fallback for ID: ${item.id}", e)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed fallback reschedule for ID: ${item.id}", e2)
                }
            }
        }
    }
}
