package com.anasbinrashid.studysync.ui.tasks

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.DialogAddTaskBinding
import com.anasbinrashid.studysync.databinding.FragmentTaskDetailBinding
import com.anasbinrashid.studysync.model.Task
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class TaskDetailFragment : Fragment() {

    private var _binding: FragmentTaskDetailBinding? = null
    private val binding get() = _binding!!

    private val args: TaskDetailFragmentArgs by navArgs()
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper

    private var currentTask: Task? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize database helper
        dbHelper = DatabaseHelper(requireContext())

        setupToolbar()
        loadTaskDetails()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun loadTaskDetails() {
        val taskId = args.taskId

        // Load task from local database
        currentTask = dbHelper.getTaskById(taskId)

        if (currentTask != null) {
            updateUI(currentTask!!)
        } else {
            // If not found locally, try to fetch from Firestore
            db.collection("tasks")
                .document(taskId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        currentTask = document.toObject(Task::class.java)
                        if (currentTask != null) {
                            // Save to local database
                            dbHelper.addTask(currentTask!!)
                            updateUI(currentTask!!)
                        } else {
                            showError("Failed to load task")
                        }
                    } else {
                        showError("Task not found")
                    }
                }
                .addOnFailureListener { e ->
                    showError("Error loading task: ${e.message}")
                }
        }
    }

    private fun updateUI(task: Task) {
        // Set task details
        binding.tvTaskTitle.text = task.title
        binding.tvCourseName.text = task.courseName
        binding.tvDescription.text = task.description

        // Format and set due date
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault())
        binding.tvDueDate.text = dateFormat.format(task.dueDate)

        // Calculate days remaining
        val currentTime = System.currentTimeMillis()
        val dueTime = task.dueDate.time
        val diffInMillis = dueTime - currentTime
        val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)

        val daysText = when {
            diffInDays < 0 -> "Overdue!"
            diffInDays == 0L -> "Due today!"
            diffInDays == 1L -> "Due tomorrow"
            else -> "$diffInDays days left"
        }
        binding.tvDaysRemaining.text = daysText

        // Set days remaining background color based on urgency
        val daysRemainingBg = when {
            task.status == 2 -> R.color.colorSuccess // Completed
            diffInDays < 0 -> R.color.colorError     // Overdue
            diffInDays == 0L -> R.color.colorError   // Due today
            diffInDays <= 2 -> R.color.colorWarning  // Due soon
            else -> R.color.colorInfo                // Not urgent
        }
        binding.tvDaysRemaining.setBackgroundResource(daysRemainingBg)

        // Set task type icon
        val typeIcon = when (task.type) {
            0 -> R.drawable.ic_assignment // Assignment
            1 -> R.drawable.ic_project    // Project
            2 -> R.drawable.ic_exam       // Exam
            else -> R.drawable.ic_tasks   // Other
        }
        binding.ivTaskType.setImageResource(typeIcon)

        // Set priority indicator color
        val priorityColor = when (task.priority) {
            0 -> R.color.colorSuccess  // Low priority
            1 -> R.color.colorWarning  // Medium priority
            else -> R.color.colorError // High priority
        }
        binding.viewPriorityIndicator.setBackgroundResource(priorityColor)

        // Set status radio button
        val statusRadioButton = when (task.status) {
            0 -> binding.rbNotStarted
            1 -> binding.rbInProgress
            else -> binding.rbCompleted
        }
        statusRadioButton.isChecked = true

        // Set grade
        binding.etGrade.setText(if (task.grade > 0) task.grade.toString() else "")

        // Update reminder FAB icon based on current reminder state
        updateReminderIcon(task.reminderSet)
    }

    private fun setupListeners() {
        // Status change
        binding.radioGroupStatus.setOnCheckedChangeListener { _, checkedId ->
            if (currentTask != null) {
                val newStatus = when (checkedId) {
                    R.id.rb_not_started -> 0
                    R.id.rb_in_progress -> 1
                    R.id.rb_completed -> 2
                    else -> 0
                }

                if (newStatus != currentTask!!.status) {
                    updateTaskStatus(newStatus)
                }
            }
        }

        // Save grade button
        binding.btnSaveGrade.setOnClickListener {
            saveGrade()
        }

        // Edit button
        binding.btnEdit.setOnClickListener {
            showEditTaskDialog()
        }

        // Delete button
        binding.btnDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        // Reminder FAB
        binding.fabReminder.setOnClickListener {
            toggleReminder()
        }
    }

    private fun updateTaskStatus(newStatus: Int) {
        if (currentTask != null) {
            val updatedTask = currentTask!!.copy(
                status = newStatus,
                lastUpdated = Date(),
                isSynced = false
            )

            // Update in local database
            dbHelper.updateTask(updatedTask)

            // Update in Firestore
            db.collection("tasks")
                .document(updatedTask.id)
                .update("status", newStatus, "lastUpdated", updatedTask.lastUpdated, "isSynced", false)
                .addOnSuccessListener {
                    currentTask = updatedTask
                    Toast.makeText(requireContext(), "Task status updated", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    // Continue with local update even if Firestore update fails
                    currentTask = updatedTask
                    // Log error but don't bother user with network issues
                }
        }
    }

    private fun saveGrade() {
        val gradeText = binding.etGrade.text.toString().trim()

        if (gradeText.isEmpty()) {
            // Clear grade if empty
            updateGrade(0f)
            return
        }

        try {
            val grade = gradeText.toFloat()
            if (grade < 0 || grade > 100) {
                Toast.makeText(requireContext(), "Grade must be between 0 and 100", Toast.LENGTH_SHORT).show()
                return
            }

            updateGrade(grade)
        } catch (e: NumberFormatException) {
            Toast.makeText(requireContext(), "Please enter a valid number", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateGrade(grade: Float) {
        if (currentTask != null) {
            val updatedTask = currentTask!!.copy(
                grade = grade,
                lastUpdated = Date(),
                isSynced = false
            )

            // Update in local database
            dbHelper.updateTask(updatedTask)

            // Update in Firestore
            db.collection("tasks")
                .document(updatedTask.id)
                .update("grade", grade, "lastUpdated", updatedTask.lastUpdated, "isSynced", false)
                .addOnSuccessListener {
                    currentTask = updatedTask
                    Toast.makeText(requireContext(), "Grade saved", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    // Continue with local update even if Firestore update fails
                    currentTask = updatedTask
                }
        }
    }

    private fun toggleReminder() {
        if (currentTask != null) {
            val newReminderState = !currentTask!!.reminderSet

            val updatedTask = currentTask!!.copy(
                reminderSet = newReminderState,
                lastUpdated = Date(),
                isSynced = false
            )

            // Update in local database
            dbHelper.updateTask(updatedTask)

            // Update in Firestore
            db.collection("tasks")
                .document(updatedTask.id)
                .update("reminderSet", newReminderState, "lastUpdated", updatedTask.lastUpdated, "isSynced", false)
                .addOnSuccessListener {
                    currentTask = updatedTask
                    updateReminderIcon(newReminderState)

                    val message = if (newReminderState) "Reminder set" else "Reminder removed"
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

                    // In a real app, you would also schedule or cancel the actual notification here
                }
                .addOnFailureListener { e ->
                    // Continue with local update even if Firestore update fails
                    currentTask = updatedTask
                    updateReminderIcon(newReminderState)
                }
        }
    }

    private fun updateReminderIcon(reminderSet: Boolean) {
        val iconRes = if (reminderSet) R.drawable.ic_reminder else R.drawable.ic_reminder
        binding.fabReminder.setImageResource(iconRes)
    }

    private fun showEditTaskDialog() {
        if (currentTask == null) return

        val dialogBinding = DialogAddTaskBinding.inflate(layoutInflater)
        val task = currentTask!!

        // Set existing values
        dialogBinding.etTitle.setText(task.title)
        dialogBinding.etDescription.setText(task.description)

        // Set course selection (would need to populate course dropdown from database)

        // Set due date and time
        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        dialogBinding.etDate.setText(dateFormat.format(task.dueDate))
        dialogBinding.etTime.setText(timeFormat.format(task.dueDate))

        // Set priority
        val priorityRadioButton = when (task.priority) {
            0 -> dialogBinding.rbLow
            1 -> dialogBinding.rbMedium
            else -> dialogBinding.rbHigh
        }
        priorityRadioButton.isChecked = true

        // Set task type
        val typeRadioButton = when (task.type) {
            0 -> dialogBinding.rbAssignment
            1 -> dialogBinding.rbProject
            2 -> dialogBinding.rbExam
            else -> dialogBinding.rbAssignment
        }
        typeRadioButton.isChecked = true

        // Set reminder
        dialogBinding.switchReminder.isChecked = task.reminderSet

        // Date picker
        dialogBinding.etDate.setOnClickListener {
            // Show date picker dialog
            // This would be implemented in a real app
        }

        // Time picker
        dialogBinding.etTime.setOnClickListener {
            // Show time picker dialog
            // This would be implemented in a real app
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Task")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                // Get updated values and save task
                // This would be implemented in a real app
                Toast.makeText(requireContext(), "Task updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete this task?")
            .setPositiveButton("Delete") { _, _ ->
                deleteTask()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTask() {
        if (currentTask != null) {
            val taskId = currentTask!!.id

            // Delete from local database
            dbHelper.deleteTask(taskId)

            // Delete from Firestore
            db.collection("tasks")
                .document(taskId)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Task deleted", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                .addOnFailureListener { e ->
                    // Continue with local deletion even if Firestore deletion fails
                    findNavController().navigateUp()
                }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}