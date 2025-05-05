package com.anasbinrashid.studysync.ui.notifications

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.FragmentNotificationsBinding
import com.anasbinrashid.studysync.model.Task
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.anasbinrashid.studysync.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var reminderAdapter: ReminderAdapter
    private lateinit var completedTasksAdapter: ReminderAdapter

    private var reminderTasks = mutableListOf<Task>()
    private var completedTasks = mutableListOf<Task>()

    // Reminder time constants (in minutes)
    private val REMINDER_15_MIN = 15
    private val REMINDER_30_MIN = 30
    private val REMINDER_1_HOUR = 60
    private val REMINDER_1_DAY = 24 * 60

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize local database
        dbHelper = DatabaseHelper(requireContext())

        // Initialize notification helper
        notificationHelper = NotificationHelper(requireContext())

        // Initialize shared preferences
        sharedPreferences = requireContext().getSharedPreferences(
            "app_preferences",
            Context.MODE_PRIVATE
        )

        setupUI()
        setupAdapters()
        loadSettings()
        loadTasksWithReminders()
    }

    private fun setupUI() {
        // Set up toolbar
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Set up save settings button
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        // Set up show completed switch
        binding.switchShowCompleted.setOnCheckedChangeListener { _, isChecked ->
            toggleCompletedTasksVisibility(isChecked)
        }
    }

    private fun setupAdapters() {
        // Setup upcoming reminders recycler view
        binding.rvReminders.layoutManager = LinearLayoutManager(requireContext())
        reminderAdapter = ReminderAdapter(
            onItemClick = { task ->
                navigateToTaskDetail(task)
            },
            onToggleReminder = { task, isEnabled ->
                toggleTaskReminder(task, isEnabled)
            }
        )
        binding.rvReminders.adapter = reminderAdapter

        // Setup completed tasks recycler view
        binding.rvCompletedTasks.layoutManager = LinearLayoutManager(requireContext())
        completedTasksAdapter = ReminderAdapter(
            onItemClick = { task ->
                navigateToTaskDetail(task)
            },
            onToggleReminder = { task, isEnabled ->
                toggleTaskReminder(task, isEnabled)
            }
        )
        binding.rvCompletedTasks.adapter = completedTasksAdapter
    }

    private fun loadSettings() {
        // Load notification enabled state
        val notificationsEnabled = sharedPreferences.getBoolean("notifications_enabled", true)
        binding.switchNotifications.isChecked = notificationsEnabled

        // Load default reminder time
        val reminderTime = sharedPreferences.getInt("default_reminder_time", REMINDER_1_HOUR)
        val reminderRadioButton = when (reminderTime) {
            REMINDER_15_MIN -> binding.rb15Min
            REMINDER_30_MIN -> binding.rb30Min
            REMINDER_1_HOUR -> binding.rb1Hour
            REMINDER_1_DAY -> binding.rb1Day
            else -> binding.rb1Hour
        }
        reminderRadioButton.isChecked = true
    }

    private fun saveSettings() {
        // Save notification enabled state
        val notificationsEnabled = binding.switchNotifications.isChecked
        sharedPreferences.edit().putBoolean("notifications_enabled", notificationsEnabled).apply()

        // Save default reminder time
        val reminderTime = when {
            binding.rb15Min.isChecked -> REMINDER_15_MIN
            binding.rb30Min.isChecked -> REMINDER_30_MIN
            binding.rb1Hour.isChecked -> REMINDER_1_HOUR
            binding.rb1Day.isChecked -> REMINDER_1_DAY
            else -> REMINDER_1_HOUR
        }
        sharedPreferences.edit().putInt("default_reminder_time", reminderTime).apply()

        // Update notification scheduling
        if (notificationsEnabled) {
            scheduleNotificationsForTasks()
        } else {
            notificationHelper.cancelAllNotifications()
        }

        Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun loadTasksWithReminders() {
        val userId = auth.currentUser?.uid ?: return
        showLoading(true)

        // Clear current lists
        reminderTasks.clear()
        completedTasks.clear()

        // Load tasks from local database
        val allTasks = dbHelper.getTasksForUser(userId)

        // Filter tasks
        val currentTime = Date().time

        for (task in allTasks) {
            if (task.status == 2) { // Completed tasks
                completedTasks.add(task)
            } else if (task.dueDate.time > currentTime) { // Upcoming tasks
                reminderTasks.add(task)
            }
        }

        // Sort tasks by due date
        reminderTasks.sortBy { it.dueDate }
        completedTasks.sortByDescending { it.dueDate }

        // Update UI
        updateRemindersUI()
        updateCompletedTasksUI()
        showLoading(false)

        // Schedule notifications if enabled
        if (binding.switchNotifications.isChecked) {
            scheduleNotificationsForTasks()
        }
    }

    private fun updateRemindersUI() {
        if (reminderTasks.isEmpty()) {
            binding.tvNoReminders.visibility = View.VISIBLE
            binding.rvReminders.visibility = View.GONE
        } else {
            binding.tvNoReminders.visibility = View.GONE
            binding.rvReminders.visibility = View.VISIBLE
            reminderAdapter.submitList(reminderTasks.toList())
        }
    }

    private fun updateCompletedTasksUI() {
        if (completedTasks.isEmpty()) {
            binding.tvNoCompleted.visibility = View.VISIBLE
            binding.rvCompletedTasks.visibility = View.GONE
        } else {
            binding.tvNoCompleted.visibility = View.GONE
            binding.rvCompletedTasks.visibility = View.VISIBLE
            completedTasksAdapter.submitList(completedTasks.toList())
        }

        // Set initial visibility based on switch state
        toggleCompletedTasksVisibility(binding.switchShowCompleted.isChecked)
    }

    private fun toggleCompletedTasksVisibility(isVisible: Boolean) {
        if (completedTasks.isEmpty()) {
            binding.tvNoCompleted.visibility = if (isVisible) View.VISIBLE else View.GONE
        } else {
            binding.rvCompletedTasks.visibility = if (isVisible) View.VISIBLE else View.GONE
        }
    }

    private fun toggleTaskReminder(task: Task, isEnabled: Boolean) {
        // Create updated task with new reminder state
        val updatedTask = task.copy(
            reminderSet = isEnabled,
            lastUpdated = Date(),
            isSynced = false
        )

        // Update in local database
        dbHelper.updateTask(updatedTask)

        // Update in Firestore
        db.collection("tasks")
            .document(updatedTask.id)
            .update("reminderSet", isEnabled, "lastUpdated", updatedTask.lastUpdated, "isSynced", false)
            .addOnSuccessListener {
                // Find and update the task in our lists
                val index = reminderTasks.indexOfFirst { it.id == task.id }
                if (index != -1) {
                    reminderTasks[index] = updatedTask
                    reminderAdapter.notifyItemChanged(index)
                } else {
                    val completedIndex = completedTasks.indexOfFirst { it.id == task.id }
                    if (completedIndex != -1) {
                        completedTasks[completedIndex] = updatedTask
                        completedTasksAdapter.notifyItemChanged(completedIndex)
                    }
                }

                // Update notification scheduling
                if (isEnabled && binding.switchNotifications.isChecked) {
                    scheduleNotificationForTask(updatedTask)
                } else {
                    notificationHelper.cancelNotification(updatedTask.id.hashCode())
                }
            }
            .addOnFailureListener { e ->
                // Continue with local update even if Firestore update fails
                // Find and update the task in our lists
                val index = reminderTasks.indexOfFirst { it.id == task.id }
                if (index != -1) {
                    reminderTasks[index] = updatedTask
                    reminderAdapter.notifyItemChanged(index)
                } else {
                    val completedIndex = completedTasks.indexOfFirst { it.id == task.id }
                    if (completedIndex != -1) {
                        completedTasks[completedIndex] = updatedTask
                        completedTasksAdapter.notifyItemChanged(completedIndex)
                    }
                }

                // Update notification scheduling
                if (isEnabled && binding.switchNotifications.isChecked) {
                    scheduleNotificationForTask(updatedTask)
                } else {
                    notificationHelper.cancelNotification(updatedTask.id.hashCode())
                }
            }
    }

    private fun scheduleNotificationsForTasks() {
        // Cancel all existing notifications first
        notificationHelper.cancelAllNotifications()

        // Schedule notifications for all tasks with reminders enabled
        for (task in reminderTasks) {
            if (task.reminderSet) {
                scheduleNotificationForTask(task)
            }
        }
    }

    private fun scheduleNotificationForTask(task: Task) {
        // Don't schedule notifications for completed tasks
        if (task.status == 2) return

        // Don't schedule if the due date has passed
        val currentTime = System.currentTimeMillis()
        if (task.dueDate.time <= currentTime) return

        // Get reminder time (in minutes before due date)
        val reminderTime = when {
            binding.rb15Min.isChecked -> REMINDER_15_MIN
            binding.rb30Min.isChecked -> REMINDER_30_MIN
            binding.rb1Hour.isChecked -> REMINDER_1_HOUR
            binding.rb1Day.isChecked -> REMINDER_1_DAY
            else -> REMINDER_1_HOUR
        }

        // Calculate notification time
        val notificationTimeMs = task.dueDate.time - (reminderTime * 60 * 1000)

        // Only schedule if notification time is in the future
        if (notificationTimeMs > currentTime) {
            notificationHelper.scheduleTaskNotification(
                task.id.hashCode(),
                task.title,
                "${task.courseName} - Due ${android.text.format.DateFormat.format("MMM dd, yyyy - hh:mm a", task.dueDate)}",
                notificationTimeMs
            )
        }
    }

    private fun navigateToTaskDetail(task: Task) {
        val action = NotificationsFragmentDirections.actionNotificationsFragmentToTaskDetailFragment(task.id)
        findNavController().navigate(action)
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        // Refresh tasks when returning to this fragment
        loadTasksWithReminders()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}