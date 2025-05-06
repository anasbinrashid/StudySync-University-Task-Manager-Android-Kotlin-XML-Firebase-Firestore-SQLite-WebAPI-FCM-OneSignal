package com.anasbinrashid.studysync

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationWillDisplayEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class StudySyncApplication : Application() {

    companion object {
        private const val ONESIGNAL_APP_ID = "1b89661f-d4aa-41b3-89ff-c462b2e675b1"
        private const val TAG = "StudySyncApplication"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Initialize OneSignal
        initializeOneSignal()
    }

    private fun initializeOneSignal() {
        try {
            // Initialize OneSignal
            OneSignal.initWithContext(this, ONESIGNAL_APP_ID)

            // Enable verbose logging for debugging
            OneSignal.Debug.logLevel = LogLevel.VERBOSE

            // Request notification permission in a coroutine
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    OneSignal.Notifications.requestPermission(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting notification permission", e)
                }
            }

            // Setup notification handlers
            setupNotificationHandlers()

            Log.d(TAG, "OneSignal initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OneSignal", e)
        }
    }

    private fun setupNotificationHandlers() {
        try {
            // Set notification will show in foreground handler
            OneSignal.Notifications.addForegroundLifecycleListener(object : INotificationLifecycleListener {
                override fun onWillDisplay(event: INotificationWillDisplayEvent) {
                    try {
                        Log.d(TAG, "Notification received in foreground: ${event.notification.title}")
                        // Display the notification
                        event.notification.display()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling foreground notification", e)
                    }
                }
            })

            // Set notification opened handler
            OneSignal.Notifications.addClickListener(object : INotificationClickListener {
                override fun onClick(event: INotificationClickEvent) {
                    try {
                        val notification = event.notification
                        val title = notification.title
                        val body = notification.body
                        val data: JSONObject? = notification.additionalData

                        Log.d(TAG, "Notification clicked: $title")
                        Log.d(TAG, "Notification data: $data")

                        // Extract taskId if present
                        val taskId = data?.optString("taskId", null)

                        if (taskId != null) {
                            // Store the taskId to navigate to when app opens
                            val sharedPreferences = getSharedPreferences("app_preferences", MODE_PRIVATE)
                            sharedPreferences.edit().putString("last_clicked_task_id", taskId).apply()
                            Log.d(TAG, "Saved taskId for navigation: $taskId")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling notification click", e)
                    }
                }
            })

            Log.d(TAG, "OneSignal notification handlers set up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up OneSignal notification handlers", e)
        }
    }
}