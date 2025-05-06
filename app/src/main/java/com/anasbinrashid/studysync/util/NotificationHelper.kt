package com.anasbinrashid.studysync.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.anasbinrashid.studysync.MainActivity
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.model.Task
import com.anasbinrashid.studysync.receiver.NotificationReceiver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.notifications.INotificationReceivedEvent
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "studysync_notification_channel"
        const val NOTIFICATION_REQUEST_CODE = 123
        private const val TAG = "NotificationHelper"

        // OneSignal constants
        private const val ONESIGNAL_APP_ID = "1b89661f-d4aa-41b3-89ff-c462b2e675b1"
        private const val ONESIGNAL_REST_API_KEY = "os_v2_app_doewmh6uvja3hcp7yrrlfztvwfcptszlx6oe5emjy5xkmkhuyusttqwmia3ptvu7rxkorz7logovwqiawdoll4zalf5zjo3rppqbami" // Replace this with your actual REST API key
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val client = OkHttpClient()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "StudySync Notifications"
            val descriptionText = "Notifications for study tasks and reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showLocalNotification(notificationId: Int, title: String, content: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    // Schedule notification using the same method signature as called in NotificationsFragment
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleTaskNotification(notificationId: Int, title: String, content: String, timeInMillis: Long) {
        // Calculate the task ID string from the notification ID integer
        val taskId = notificationId.toString()

        // Don't schedule if the time has passed
        val currentTime = System.currentTimeMillis()
        if (timeInMillis <= currentTime) return

        // Schedule local notification
        scheduleLocalNotification(notificationId, title, content, timeInMillis)

        // Schedule cloud notification
        scheduleCloudNotification(taskId, title, content, timeInMillis)
    }

    // Original method for scheduling from Task object
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleTaskNotification(task: Task, reminderMinutes: Int) {
        // Don't schedule for completed tasks
        if (task.status == 2) return

        // Don't schedule if the due date has passed
        val currentTime = System.currentTimeMillis()
        if (task.dueDate.time <= currentTime) return

        // Calculate notification time
        val notificationTimeMs = task.dueDate.time - (reminderMinutes * 60 * 1000)

        // Only schedule if notification time is in the future
        if (notificationTimeMs > currentTime) {
            // Format date string
            val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
            val dueTimeStr = dateFormat.format(task.dueDate)

            // Schedule local notification
            scheduleLocalNotification(
                task.id.hashCode(),
                task.title,
                "${task.courseName} - Due $dueTimeStr",
                notificationTimeMs
            )

            // Schedule cloud notification
            scheduleCloudNotification(
                task.id,
                task.title,
                "${task.courseName} - Due $dueTimeStr",
                notificationTimeMs
            )
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleLocalNotification(notificationId: Int, title: String, content: String, timeInMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("notification_id", notificationId)
            putExtra("title", title)
            putExtra("content", content)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
        }

        Log.d(TAG, "Local notification scheduled for ${SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(Date(timeInMillis))}")
    }

    private fun scheduleCloudNotification(taskId: String, title: String, content: String, timeInMillis: Long) {
        try {
            val userId = auth.currentUser?.uid ?: return

            // Calculate delay in seconds
            val currentTimeMillis = System.currentTimeMillis()
            val delaySeconds = (timeInMillis - currentTimeMillis) / 1000

            // Don't schedule if time has already passed
            if (delaySeconds <= 0) return

            // Store the scheduled notification in Firestore
            db.collection("scheduledNotifications")
                .document(userId + "_" + taskId)
                .set(mapOf(
                    "userId" to userId,
                    "taskId" to taskId,
                    "title" to title,
                    "content" to content,
                    "scheduledTime" to timeInMillis,
                    "createdAt" to System.currentTimeMillis()
                ))
                .addOnSuccessListener {
                    Log.d(TAG, "Cloud notification stored in Firestore")

                    // Schedule using OneSignal REST API
                    sendOneSignalNotification(userId, title, content, taskId, timeInMillis)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error storing notification in Firestore", e)
                }

        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling cloud notification", e)
        }
    }

    private fun sendOneSignalNotification(userId: String, title: String, content: String, taskId: String, timeInMillis: Long) {
        try {
            // Format the timestamp for OneSignal
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'GMT'Z", Locale.US)
            val sendAfterTime = dateFormat.format(Date(timeInMillis))

            // Create the JSON payload for OneSignal REST API
            val jsonPayload = JSONObject().apply {
                put("app_id", ONESIGNAL_APP_ID)
                put("headings", JSONObject().put("en", title))
                put("contents", JSONObject().put("en", content))
                put("data", JSONObject().put("taskId", taskId).put("type", "task_reminder"))
                put("send_after", sendAfterTime)

                // Target the specific user by their external_user_id
                put("include_external_user_ids", JSONArray().put(userId))
            }

            // Send the request using OkHttp
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://onesignal.com/api/v1/notifications")
                .post(requestBody)
                .addHeader("Authorization", "Basic $ONESIGNAL_REST_API_KEY")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed to schedule OneSignal notification", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "OneSignal notification scheduled successfully: ${response.body?.string()}")
                    } else {
                        Log.e(TAG, "Error scheduling OneSignal notification: ${response.code} - ${response.body?.string()}")
                    }
                    response.close()
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error creating OneSignal notification", e)
        }
    }

    fun cancelNotification(notificationId: Int) {
        // Cancel local notification
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()

        // Cancel cloud notification
        cancelCloudNotification(notificationId.toString())
    }

    private fun cancelCloudNotification(taskId: String) {
        try {
            val userId = auth.currentUser?.uid ?: return

            db.collection("scheduledNotifications")
                .document(userId + "_" + taskId)
                .delete()
                .addOnSuccessListener {
                    Log.d(TAG, "Cloud notification canceled in Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error canceling cloud notification", e)
                }

        } catch (e: Exception) {
            Log.e(TAG, "Error canceling cloud notification", e)
        }
    }

    fun cancelAllNotifications() {
        // Cancel local notifications
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        // Cancel cloud notifications
        cancelAllCloudNotifications()

        Log.d(TAG, "All notifications canceled")
    }

    private fun cancelAllCloudNotifications() {
        try {
            val userId = auth.currentUser?.uid ?: return

            db.collection("scheduledNotifications")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        document.reference.delete()
                    }
                    Log.d(TAG, "All cloud notifications canceled in Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error canceling all cloud notifications", e)
                }

        } catch (e: Exception) {
            Log.e(TAG, "Error canceling all cloud notifications", e)
        }
    }
}