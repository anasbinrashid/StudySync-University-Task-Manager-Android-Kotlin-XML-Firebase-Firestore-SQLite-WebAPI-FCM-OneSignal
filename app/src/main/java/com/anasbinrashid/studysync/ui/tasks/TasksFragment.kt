package com.anasbinrashid.studysync.ui.tasks

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.anasbinrashid.studysync.databinding.FragmentTasksBinding
import com.anasbinrashid.studysync.model.Task
import com.anasbinrashid.studysync.ui.courses.CoursesFragmentDirections
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Date

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tasksAdapter: TasksAdapter

    private var allTasks: List<Task> = listOf()
    private var filteredTasks: List<Task> = listOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize local database
        dbHelper = DatabaseHelper(requireContext())

        setupRecyclerView()
        setupFilterChips()
        setupSearch()
        loadTasks()
    }

    private fun setupRecyclerView() {
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
        tasksAdapter = TasksAdapter { task ->
            // Handle task click - Navigate to TaskDetailFragment
            navigateToTaskDetail(task)
        }
        binding.rvTasks.adapter = tasksAdapter
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener { filterTasks(TaskFilter.ALL) }
        binding.chipToday.setOnClickListener { filterTasks(TaskFilter.TODAY) }
        binding.chipUpcoming.setOnClickListener { filterTasks(TaskFilter.UPCOMING) }
        binding.chipCompleted.setOnClickListener { filterTasks(TaskFilter.COMPLETED) }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                filterTasksBySearchQuery(s.toString())
            }
        })
    }

    private fun loadTasks() {
        showLoading(true)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid

            // Load tasks from local database
            allTasks = dbHelper.getTasksForUser(userId)
            filteredTasks = allTasks

            updateTasksList()
            showLoading(false)

            // Try to sync with Firestore if network is available
            syncTasksWithFirestore(userId)
        } else {
            showLoading(false)
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncTasksWithFirestore(userId: String) {
        // Get all unsynced tasks from local database
        val unsyncedTasks = dbHelper.getUnsyncedTasks()

        // Upload unsynced tasks to Firestore
        for (task in unsyncedTasks) {
            db.collection("tasks")
                .document(task.id)
                .set(task)
                .addOnSuccessListener {
                    // Mark task as synced in local database
                    dbHelper.markTaskAsSynced(task.id)
                }
                .addOnFailureListener { e ->
                    // Log failure but don't bother the user
                    // They can continue working offline
                }
        }

        // Get latest tasks from Firestore
        db.collection("tasks")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                var hasNewTasks = false

                for (document in documents) {
                    val firestoreTask = document.toObject(Task::class.java)

                    // Check if task exists in local database
                    val localTask = allTasks.find { it.id == firestoreTask.id }

                    if (localTask == null || firestoreTask.lastUpdated > localTask.lastUpdated) {
                        // Add or update task in local database
                        dbHelper.addTask(firestoreTask)
                        hasNewTasks = true
                    }
                }

                if (hasNewTasks) {
                    // Reload tasks from local database
                    allTasks = dbHelper.getTasksForUser(userId)
                    updateTasksList()
                }
            }
            .addOnFailureListener { e ->
                // Continue with local data, no need to alert user
            }
    }

    private fun filterTasks(filter: TaskFilter) {
        filteredTasks = when (filter) {
            TaskFilter.ALL -> allTasks
            TaskFilter.TODAY -> {
                val calendar = Calendar.getInstance()
                val startOfDay = calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time

                val endOfDay = calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.time

                allTasks.filter { task ->
                    task.dueDate in startOfDay..endOfDay
                }
            }
            TaskFilter.UPCOMING -> {
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.time

                allTasks.filter { task ->
                    task.dueDate > today && task.status != 2
                }
            }
            TaskFilter.COMPLETED -> {
                allTasks.filter { task -> task.status == 2 }
            }
        }

        // Apply current search filter too
        val searchQuery = binding.etSearch.text.toString()
        if (searchQuery.isNotEmpty()) {
            filteredTasks = filteredTasks.filter { task ->
                task.title.contains(searchQuery, ignoreCase = true) ||
                        task.description.contains(searchQuery, ignoreCase = true) ||
                        task.courseName.contains(searchQuery, ignoreCase = true)
            }
        }

        updateTasksList()
    }

    private fun filterTasksBySearchQuery(query: String) {
        if (query.isEmpty()) {
            // If search query is empty, apply only the selected chip filter
            when {
                binding.chipAll.isChecked -> filterTasks(TaskFilter.ALL)
                binding.chipToday.isChecked -> filterTasks(TaskFilter.TODAY)
                binding.chipUpcoming.isChecked -> filterTasks(TaskFilter.UPCOMING)
                binding.chipCompleted.isChecked -> filterTasks(TaskFilter.COMPLETED)
                else -> filterTasks(TaskFilter.ALL)
            }
        } else {
            // Apply search filter to current filtered tasks
            filteredTasks = filteredTasks.filter { task ->
                task.title.contains(query, ignoreCase = true) ||
                        task.description.contains(query, ignoreCase = true) ||
                        task.courseName.contains(query, ignoreCase = true)
            }
            updateTasksList()
        }
    }

    private fun updateTasksList() {
        if (filteredTasks.isEmpty()) {
            binding.rvTasks.visibility = View.GONE
            binding.tvNoTasks.visibility = View.VISIBLE
        } else {
            binding.rvTasks.visibility = View.VISIBLE
            binding.tvNoTasks.visibility = View.GONE
            tasksAdapter.submitList(filteredTasks)
        }
    }

    private fun navigateToTaskDetail(task: Task) {
        // Navigate to task detail using Navigation Component
        val action = TasksFragmentDirections.actionTasksFragmentToTaskDetailFragment(task.id)
        findNavController().navigate(action)
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    enum class TaskFilter {
        ALL, TODAY, UPCOMING, COMPLETED
    }
}