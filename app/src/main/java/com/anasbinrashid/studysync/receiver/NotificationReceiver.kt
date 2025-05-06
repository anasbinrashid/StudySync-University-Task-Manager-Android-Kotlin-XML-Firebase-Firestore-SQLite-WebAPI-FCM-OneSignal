package com.anasbinrashid.studysync.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anasbinrashid.studysync.util.NotificationHelper

/**
 * Receiver for handling scheduled notifications
 */
class NotificationReceiver : BroadcastReceiver() {

    private val TAG = "NotificationReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received notification intent")

        try {
            val notificationId = intent.getIntExtra("notification_id", 0)
            val title = intent.getStringExtra("title") ?: "Task Reminder"
            val content = intent.getStringExtra("content") ?: "You have a task due soon"

            Log.d(TAG, "Showing notification: $title - $content")

            // Show the notification
            val notificationHelper = NotificationHelper(context)
            notificationHelper.showLocalNotification(notificationId, title, content)
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying notification", e)
        }
    }
}