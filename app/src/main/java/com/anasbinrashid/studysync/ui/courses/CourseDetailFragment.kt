package com.anasbinrashid.studysync.ui.courses

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.FragmentCourseDetailBinding
import com.anasbinrashid.studysync.model.Course
import com.anasbinrashid.studysync.model.Task
import com.anasbinrashid.studysync.ui.tasks.TasksAdapter
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CourseDetailFragment : Fragment() {

    private var _binding: FragmentCourseDetailBinding? = null
    private val binding get() = _binding!!

    private val args: CourseDetailFragmentArgs by navArgs()
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tasksAdapter: TasksAdapter

    private var currentCourse: Course? = null
    private var courseTasks: List<Task> = listOf()

    private val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCourseDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize local database
        dbHelper = DatabaseHelper(requireContext())

        setupUI()
        loadCourseDetails()
        setupListeners()
    }

    private fun setupUI() {
        // Set up toolbar
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Set up tasks recycler view
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
        tasksAdapter = TasksAdapter { task ->
            // Navigate to task detail screen
            navigateToTaskDetail(task)
        }
        binding.rvTasks.adapter = tasksAdapter
    }

    private fun loadCourseDetails() {
        showLoading(true)
        val courseId = args.courseId

        // Load course from local database
        currentCourse = dbHelper.getCourseById(courseId)

        if (currentCourse != null) {
            updateUI(currentCourse!!)
            loadCourseTasks(courseId)
        } else {
            // If not found locally, try to fetch from Firestore
            db.collection("courses")
                .document(courseId)
                .get()
                .addOnSuccessListener { document ->
                    showLoading(false)
                    if (document != null && document.exists()) {
                        currentCourse = document.toObject(Course::class.java)
                        if (currentCourse != null) {
                            // Save to local database
                            dbHelper.addCourse(currentCourse!!)
                            updateUI(currentCourse!!)
                            loadCourseTasks(courseId)
                        } else {
                            showError("Failed to load course")
                        }
                    } else {
                        showError("Course not found")
                    }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    showError("Error loading course: ${e.message}")
                }
        }
    }

    private fun loadCourseTasks(courseId: String) {
        // Load tasks for this course from local database
        courseTasks = dbHelper.getTasksForCourse(courseId)

        // Update UI with tasks
        if (courseTasks.isEmpty()) {
            binding.tvNoTasks.visibility = View.VISIBLE
            binding.rvTasks.visibility = View.GONE
        } else {
            binding.tvNoTasks.visibility = View.GONE
            binding.rvTasks.visibility = View.VISIBLE
            tasksAdapter.submitList(courseTasks)
        }

        showLoading(false)
    }

    private fun updateUI(course: Course) {
        // Set course details in the UI
        binding.tvCourseName.text = course.name
        binding.tvCourseCode.text = course.code
        binding.tvSemester.text = "${course.semester} • ${course.creditHours} Credit Hours"

        // Set course color
        val courseColor = if (course.color != 0) {
            course.color
        } else {
            ContextCompat.getColor(requireContext(), R.color.colorPrimary)
        }

        binding.viewColorBand.setBackgroundColor(courseColor)
        binding.tvCourseCode.backgroundTintList = ColorStateList.valueOf(courseColor)

        // Set instructor info
        binding.tvInstructorName.text = course.instructorName
        binding.tvInstructorEmail.text = course.instructorEmail

        // Set class schedule
        val classDays = course.dayOfWeek.map { dayNames[it] }.joinToString(", ")
        binding.tvClassDays.text = classDays
        binding.tvClassTime.text = "${course.startTime} - ${course.endTime}"
        binding.tvClassRoom.text = "Room ${course.room}"
    }

    private fun setupListeners() {
        // Edit course button
        binding.fabEditCourse.setOnClickListener {
            navigateToEditCourse()
        }

        // Add task button
        binding.btnAddTask.setOnClickListener {
            navigateToAddTask()
        }
    }

    private fun navigateToTaskDetail(task: Task) {
        // Navigate to task detail screen
        val action = CourseDetailFragmentDirections.actionCourseDetailFragmentToTaskDetailFragment(task.id)
        findNavController().navigate(action)
    }

    private fun navigateToEditCourse() {
        // Navigate to edit course screen
        if (currentCourse != null) {
            val action = CourseDetailFragmentDirections.actionCourseDetailFragmentToAddEditCourseFragment(
                courseId = currentCourse!!.id
            )
            findNavController().navigate(action)
        }
    }

    private fun navigateToAddTask() {
        // Navigate to add task screen with course pre-selected
        if (currentCourse != null) {
            val action = CourseDetailFragmentDirections.actionCourseDetailFragmentToAddEditTaskFragment(
                courseId = currentCourse!!.id
            )
            findNavController().navigate(action)
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