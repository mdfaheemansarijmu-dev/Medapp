package com.example.network

import android.content.Context
import android.util.Log
import com.example.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // 1. Extract title and body from the notification payload or data payload
        var title = remoteMessage.notification?.title
        var body = remoteMessage.notification?.body
        var type = "general"
        var itemId = ""
        var subject = ""

        // Data payload overrides notification payload (or provides advanced data fields)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            remoteMessage.data["title"]?.let { title = it }
            remoteMessage.data["body"]?.let { body = it }
            remoteMessage.data["message"]?.let { body = it } // fallback parameter
            remoteMessage.data["type"]?.let { type = it }
            remoteMessage.data["itemId"]?.let { itemId = it }
            remoteMessage.data["subject"]?.let { subject = it }
        }

        val displayTitle = title ?: "MedPulse Academic Notice"
        val displayBody = body ?: "You have a new academic update."

        // 2. Trigger highly polished local notification
        val notificationId = Random.nextInt(1000, 99999)
        NotificationHelper.showNotification(
            context = applicationContext,
            title = displayTitle,
            message = displayBody,
            notificationId = notificationId,
            itemId = itemId,
            type = type,
            subject = subject
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM registration token: $token")
        
        // Save token locally in shared preferences so user can see/copy it for diagnostic test runs
        val sharedPrefs = applicationContext.getSharedPreferences("med_planner_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_registration_token", token).apply()
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}
