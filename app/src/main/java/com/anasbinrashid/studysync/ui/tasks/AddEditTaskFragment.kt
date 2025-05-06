package com.anasbinrashid.studysync.ui.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.FragmentAddEditTaskBinding
import com.anasbinrashid.studysync.model.Course
import com.anasbinrashid.studysync.model.Task
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.anasbinrashid.studysync.util.NotificationHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class AddEditTaskFragment : Fragment() {

    private var _binding: FragmentAddEditTaskBinding? = null
    private val binding get() = _binding!!

    private val args: AddEditTaskFragmentArgs by navArgs()
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper

    private var isEditMode = false
    private var currentTask: Task? = null
    private var selectedCourse: Course? = null
    private val coursesList = mutableListOf<Course>()
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize local database
        dbHelper = DatabaseHelper(requireContext())

        // Set up the UI
        setupUI()
        setupListeners()

        // Check if we're in edit mode
        determineMode()
    }

    private fun setupUI() {
        // Set up toolbar
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Set up date picker
        binding.etDate.setOnClickListener {
            showDatePicker()
        }

        // Set up time picker
        binding.etTime.setOnClickListener {
            showTimePicker()
        }

        // Load courses for dropdown
        loadCourses()
    }

    private fun loadCourses() {
        val userId = auth.currentUser?.uid ?: return

        // Load courses from local database
        coursesList.clear()
        coursesList.addAll(dbHelper.getCoursesForUser(userId))

        if (coursesList.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Please add a course before creating a task",
                Toast.LENGTH_LONG
            ).show()
            findNavController().navigateUp()
            return
        }

        // Create adapter for course dropdown
        val courseNames = coursesList.map { "${it.name} (${it.code})" }.toTypedArray()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, courseNames)
        binding.actCourse.setAdapter(adapter)

        // Set selection listener
        binding.actCourse.setOnItemClickListener { _, _, position, _ ->
            selectedCourse = coursesList[position]
        }

        // Pre-select course if courseId is passed
        val courseId = args.courseId
        if (courseId.isNotEmpty()) {
            val courseIndex = coursesList.indexOfFirst { it.id == courseId }
            if (courseIndex != -1) {
                binding.actCourse.setText(courseNames[courseIndex], false)
                selectedCourse = coursesList[courseIndex]
            }
        }
    }

    private fun setupListeners() {
        // Save button
        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                saveTask()
            }
        }

        // Delete button (visible in edit mode only)
        binding.btnDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun determineMode() {
        val taskId = args.taskId

        if (taskId.isEmpty()) {
            // Add mode
            isEditMode = false
            binding.toolbar.title = "Add Task"
            binding.layoutStatus.visibility = View.GONE
            binding.layoutGrade.visibility = View.GONE
            binding.btnDelete.visibility = View.GONE

            // Set default date and time (tomorrow at noon)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 12)
            calendar.set(Calendar.MINUTE, 0)
            updateDateTimeDisplay()
        } else {
            // Edit mode
            isEditMode = true
            binding.toolbar.title = "Edit Task"
            binding.layoutStatus.visibility = View.VISIBLE
            binding.layoutGrade.visibility = View.VISIBLE
            binding.btnDelete.visibility = View.VISIBLE

            loadTaskDetails(taskId)
        }
    }

    private fun loadTaskDetails(taskId: String) {
        showLoading(true)

        // Load from local database
        currentTask = dbHelper.getTaskById(taskId)

        if (currentTask != null) {
            populateUI(currentTask!!)
            showLoading(false)
        } else {
            // If not found locally, try to fetch from Firestore
            db.collection("tasks")
                .document(taskId)
                .get()
                .addOnSuccessListener { document ->
                    showLoading(false)
                    if (document != null && document.exists()) {
                        currentTask = document.toObject(Task::class.java)
                        if (currentTask != null) {
                            // Save to local database
                            dbHelper.addTask(currentTask!!)
                            populateUI(currentTask!!)
                        } else {
                            showError("Failed to load task")
                        }
                    } else {
                        showError("Task not found")
                    }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    showError("Error loading task: ${e.message}")
                }
        }
    }

    private fun populateUI(task: Task) {
        binding.etTitle.setText(task.title)
        binding.etDescription.setText(task.description)

        // Set course
        val courseIndex = coursesList.indexOfFirst { it.id == task.courseId }
        if (courseIndex != -1) {
            val courseNames = coursesList.map { "${it.name} (${it.code})" }.toTypedArray()
            binding.actCourse.setText(courseNames[courseIndex], false)
            selectedCourse = coursesList[courseIndex]
        }

        // Set due date and time
        calendar.time = task.dueDate
        updateDateTimeDisplay()

        // Set priority
        val priorityRadioButton = when (task.priority) {
            0 -> binding.rbLow
            1 -> binding.rbMedium
            else -> binding.rbHigh
        }
        priorityRadioButton.isChecked = true

        // Set task type
        val typeRadioButton = when (task.type) {
            0 -> binding.rbAssignment
            1 -> binding.rbProject
            2 -> binding.rbExam
            3 -> binding.rbReading
            else -> binding.rbAssignment
        }
        typeRadioButton.isChecked = true

        // Set status (if in edit mode)
        val statusRadioButton = when (task.status) {
            0 -> binding.rbNotStarted
            1 -> binding.rbInProgress
            else -> binding.rbCompleted
        }
        statusRadioButton.isChecked = true

        // Set reminder
        binding.switchReminder.isChecked = task.reminderSet

        // Set grade (if in edit mode)
        if (task.grade > 0) {
            binding.etGrade.setText(task.grade.toString())
        }
    }

    private fun showDatePicker() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(Calendar.YEAR, selectedYear)
                calendar.set(Calendar.MONTH, selectedMonth)
                calendar.set(Calendar.DAY_OF_MONTH, selectedDay)
                updateDateTimeDisplay()
            },
            year,
            month,
            day
        )

        datePickerDialog.show()
    }

    private fun showTimePicker() {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                calendar.set(Calendar.MINUTE, selectedMinute)
                updateDateTimeDisplay()
            },
            hour,
            minute,
            false
        )

        timePickerDialog.show()
    }

    private fun updateDateTimeDisplay() {
        binding.etDate.setText(dateFormat.format(calendar.time))
        binding.etTime.setText(timeFormat.format(calendar.time))
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        // Reset errors
        binding.tilTitle.error = null
        binding.tilCourse.error = null
        binding.tilDate.error = null
        binding.tilTime.error = null

        // Validate title
        if (binding.etTitle.text.toString().trim().isEmpty()) {
            binding.tilTitle.error = "Title is required"
            isValid = false
        }

        // Validate course
        if (selectedCourse == null) {
            binding.tilCourse.error = "Please select a course"
            isValid = false
        }

        // Validate date and time
        if (binding.etDate.text.toString().isEmpty()) {
            binding.tilDate.error = "Due date is required"
            isValid = false
        }

        if (binding.etTime.text.toString().isEmpty()) {
            binding.tilTime.error = "Due time is required"
            isValid = false
        }

        // Validate grade in edit mode
        if (isEditMode && binding.etGrade.text.toString().isNotEmpty()) {
            try {
                val grade = binding.etGrade.text.toString().toFloat()
                if (grade < 0 || grade > 100) {
                    binding.tilGrade.error = "Grade must be between 0 and 100"
                    isValid = false
                }
            } catch (e: NumberFormatException) {
                binding.tilGrade.error = "Please enter a valid number"
                isValid = false
            }
        }

        return isValid
    }

    private fun saveTask() {
        val userId = auth.currentUser?.uid ?: return
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val dueDate = calendar.time

        // Get priority
        val priority = when {
            binding.rbLow.isChecked -> 0
            binding.rbMedium.isChecked -> 1
            binding.rbHigh.isChecked -> 2
            else -> 0
        }

        // Get task type
        val type = when {
            binding.rbAssignment.isChecked -> 0
            binding.rbProject.isChecked -> 1
            binding.rbExam.isChecked -> 2
            binding.rbReading.isChecked -> 3
            else -> 0
        }

        // Get status (only in edit mode)
        val status = if (isEditMode) {
            when {
                binding.rbNotStarted.isChecked -> 0
                binding.rbInProgress.isChecked -> 1
                binding.rbCompleted.isChecked -> 2
                else -> 0
            }
        } else {
            0 // Not started for new tasks
        }

        // Get reminder setting
        val reminderSet = binding.switchReminder.isChecked

        // Get grade (only in edit mode)
        val grade = if (isEditMode && binding.etGrade.text.toString().isNotEmpty()) {
            try {
                binding.etGrade.text.toString().toFloat()
            } catch (e: NumberFormatException) {
                0f
            }
        } else {
            0f
        }

        // Create or update task
        if (isEditMode && currentTask != null) {
            // Update existing task
            val updatedTask = currentTask!!.copy(
                title = title,
                description = description,
                courseId = selectedCourse?.id ?: "",
                courseName = selectedCourse?.name ?: "",
                dueDate = dueDate,
                priority = priority,
                status = status,
                type = type,
                reminderSet = reminderSet,
                grade = grade,
                isSynced = false,
                lastUpdated = Date()
            )

            // Update in local database
            dbHelper.updateTask(updatedTask)

            // Update in Firestore
            db.collection("tasks")
                .document(updatedTask.id)
                .set(updatedTask)
                .addOnSuccessListener {
                    // Show notification for task update
                    val notificationHelper = NotificationHelper(requireContext())
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
                    val dueTimeStr = dateFormat.format(updatedTask.dueDate)
                    
                    notificationHelper.showLocalNotification(
                        updatedTask.id.hashCode(),
                        "Task Updated: ${updatedTask.title}",
                        "${updatedTask.courseName} - Due $dueTimeStr"
                    )

                    Toast.makeText(requireContext(), "Task updated successfully", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                .addOnFailureListener { e ->
                    // Continue with local update even if Firestore update fails
                    Toast.makeText(requireContext(), "Task updated locally", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
        } else {
            // Create new task
            val newTask = Task(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = title,
                description = description,
                courseId = selectedCourse?.id ?: "",
                courseName = selectedCourse?.name ?: "",
                dueDate = dueDate,
                priority = priority,
                status = status,
                type = type,
                reminderSet = reminderSet,
                grade = grade,
                isSynced = false,
                lastUpdated = Date()
            )

            // Save to local database
            dbHelper.addTask(newTask)

            // Save to Firestore
            db.collection("tasks")
                .document(newTask.id)
                .set(newTask)
                .addOnSuccessListener {
                    // Show notification for new task
                    val notificationHelper = NotificationHelper(requireContext())
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
                    val dueTimeStr = dateFormat.format(newTask.dueDate)
                    
                    // Show immediate notification
                    notificationHelper.showLocalNotification(
                        newTask.id.hashCode(),
                        "New Task Added: ${newTask.title}",
                        "${newTask.courseName} - Due $dueTimeStr"
                    )

                    // Schedule reminder notification if enabled
                    if (newTask.reminderSet) {
                        notificationHelper.scheduleTaskNotification(newTask, 60) // 60 minutes before due time
                    }

                    Toast.makeText(requireContext(), "Task added successfully", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                .addOnFailureListener { e ->
                    // Continue with local save even if Firestore save fails
                    Toast.makeText(requireContext(), "Task added locally", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
        }
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
                    // Show notification for task deletion
                    val notificationHelper = NotificationHelper(requireContext())
                    notificationHelper.showLocalNotification(
                        taskId.hashCode(),
                        "Task Deleted: ${currentTask!!.title}",
                        "Task has been removed from ${currentTask!!.courseName}"
                    )

                    Toast.makeText(requireContext(), "Task deleted", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                .addOnFailureListener { e ->
                    // Continue with local deletion even if Firestore deletion fails
                    Toast.makeText(requireContext(), "Task deleted locally", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
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