package com.anasbinrashid.studysync.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anasbinrashid.studysync.util.NotificationHelper

/**
 * BroadcastReceiver to handle scheduled notifications
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Extract notification details from intent
        val notificationId = intent.getIntExtra("notification_id", 0)
        val title = intent.getStringExtra("title") ?: "Task Reminder"
        val content = intent.getStringExtra("content") ?: "You have a task due soon"

        // Show the notification
        val notificationHelper = NotificationHelper(context)
        notificationHelper.showNotification(notificationId, title, content)
    }
}