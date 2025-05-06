package com.anasbinrashid.studysync.service

import android.util.Log
import com.anasbinrashid.studysync.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.firestore.FirebaseFirestore

class StudySyncMessagingService : FirebaseMessagingService() {

    private val TAG = "StudySyncMessaging"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "From: ${remoteMessage.from}")

        try {
            // Check if notifications are enabled
            val sharedPreferences = applicationContext.getSharedPreferences(
                "app_preferences",
                MODE_PRIVATE
            )
            val notificationsEnabled = sharedPreferences.getBoolean("notifications_enabled", true)
            if (!notificationsEnabled) {
                Log.d(TAG, "Notifications are disabled, skipping FCM message processing")
                return
            }

            // Check if message contains a data payload
            if (remoteMessage.data.isNotEmpty()) {
                Log.d(TAG, "Message data payload: ${remoteMessage.data}")

                // Process the data payload
                handleRemoteMessage(remoteMessage)
            }

            // Check if message contains a notification payload
            remoteMessage.notification?.let {
                Log.d(TAG, "Message Notification Body: ${it.body}")

                // If this is a direct FCM notification (not through OneSignal)
                // you can handle it here
                val notificationHelper = NotificationHelper(applicationContext)
                notificationHelper.showLocalNotification(
                    System.currentTimeMillis().toInt(),
                    it.title ?: "StudySync",
                    it.body ?: "You have a new notification"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing FCM message", e)
        }
    }

    private fun handleRemoteMessage(remoteMessage: RemoteMessage) {
        try {
            val data = remoteMessage.data

            // If this is a task reminder notification
            if (data.containsKey("type") && data["type"] == "task_reminder") {
                val taskId = data["taskId"] ?: return
                val title = data["title"] ?: "Task Reminder"
                val message = data["message"] ?: "You have an upcoming task"

                // Show notification
                val notificationHelper = NotificationHelper(applicationContext)
                notificationHelper.showLocalNotification(
                    taskId.hashCode(),
                    title,
                    message
                )

                Log.d(TAG, "Task reminder notification displayed: $title")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing remote message", e)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.d(TAG, "New FCM token: $token")

        // Send the token to your server if needed
        // This is typically handled by OneSignal automatically
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        // If you need to store the token on your own server
        // You can implement that logic here

        try {
            val userId = getCurrentUserId()
            if (userId != null) {
                // Store the token in Firestore
                val db = FirebaseFirestore.getInstance()
                db.collection("users").document(userId)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        Log.d(TAG, "FCM token updated in Firestore")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update FCM token", e)
                    }
            } else {
                Log.d(TAG, "No user logged in, skipping FCM token update")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending FCM token to server", e)
        }
    }

    private fun getCurrentUserId(): String? {
        return try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user ID", e)
            null
        }
    }
}