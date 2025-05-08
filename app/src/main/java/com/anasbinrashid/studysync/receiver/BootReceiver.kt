package com.anasbinrashid.studysync.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.anasbinrashid.studysync.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth

/**
 * Receiver to reschedule notifications after device restart
 */
class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device booted, rescheduling notifications")

            try {
                // Check if notifications are enabled
                val sharedPreferences = context.getSharedPreferences(
                    "app_preferences",
                    Context.MODE_PRIVATE
                )
                val notificationsEnabled = sharedPreferences.getBoolean("notifications_enabled", true)
                if (!notificationsEnabled) {
                    Log.d(TAG, "Notifications are disabled, skipping rescheduling")
                    return
                }

                // Get default reminder time
                val reminderTime = sharedPreferences.getInt("default_reminder_time", 60) // Default 1 hour

                // Initialize helpers
                val dbHelper = DatabaseHelper(context)
                val notificationHelper = NotificationHelper(context)

                // Get current user
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    Log.d(TAG, "No user logged in, skipping notification rescheduling")
                    return
                }

                // Get upcoming tasks with reminders
                val tasks = dbHelper.getTasksForUser(currentUser.uid)
                val currentTime = System.currentTimeMillis()

                var rescheduledCount = 0
                // Reschedule notifications for upcoming tasks with reminders
                for (task in tasks) {
                    if (task.reminderSet && task.status != 2 && task.dueDate?.time!! > currentTime) {
                        notificationHelper.scheduleTaskNotification(task, reminderTime)
                        rescheduledCount++
                    }
                }

                Log.d(TAG, "Rescheduled $rescheduledCount notifications after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling notifications after boot", e)
            }
        }
    }
}