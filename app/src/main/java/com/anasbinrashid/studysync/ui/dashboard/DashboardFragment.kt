package com.anasbinrashid.studysync.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.anasbinrashid.studysync.MainActivity
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.FragmentDashboardBinding
import com.anasbinrashid.studysync.model.Task
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tasksAdapter: UpcomingTasksAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
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
        loadDashboardData()
    }

    private fun setupUI() {
        // Set current date
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        binding.tvDate.text = dateFormat.format(Date())

        // Set greeting with user name
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            val user = dbHelper.getUser(userId)

            if (user != null) {
                // Extract first name from full name
                val firstName = user.name.split(" ").firstOrNull() ?: "User"
                binding.tvGreeting.text = String.format("Hello, %s!", firstName)

                // Set current semester
                binding.tvCurrentSemester.text = user.currentSemester
            }
        }

        // Setup recycler view for upcoming assignments
        binding.rvUpcomingAssignments.layoutManager = LinearLayoutManager(requireContext())
        tasksAdapter = UpcomingTasksAdapter()
        binding.rvUpcomingAssignments.adapter = tasksAdapter

        // Setup button click listeners
        binding.btnViewCourses.setOnClickListener {
            // Navigate to courses tab
            (requireActivity() as? MainActivity)?.navigateToTab(R.id.coursesFragment)
        }

        binding.btnViewTasks.setOnClickListener {
            // Navigate to tasks tab
            (requireActivity() as? MainActivity)?.navigateToTab(R.id.tasksFragment)
        }
        // Setup pie chart
        setupPieChart()
    }

    private fun loadDashboardData() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val userId = currentUser.uid

            // Load courses count
            val coursesCount = dbHelper.getCoursesCount(userId)
            binding.tvCoursesCount.text = coursesCount.toString()

            // Load tasks count
            val tasksCount = dbHelper.getUpcomingTasksCount(userId)
            binding.tvTasksCount.text = tasksCount.toString()

            // Load upcoming tasks
            val upcomingTasks = dbHelper.getUpcomingTasks(userId, 5)

            if (upcomingTasks.isEmpty()) {
                binding.tvNoAssignments.visibility = View.VISIBLE
                binding.rvUpcomingAssignments.visibility = View.GONE
            } else {
                binding.tvNoAssignments.visibility = View.GONE
                binding.rvUpcomingAssignments.visibility = View.VISIBLE
                tasksAdapter.submitList(upcomingTasks)
            }

            // Update study progress chart with actual data
            updatePieChartData(userId)

            // Update semester progress
            val user = dbHelper.getUser(userId)
            if (user != null) {
                val semesterStartDate = 1737331200000L  // You must store this in your User model and DB
                val weeksCompleted = calculateWeeksCompleted(semesterStartDate)
                binding.tvWeeksCompleted.text = "$weeksCompleted Weeks"
                binding.progressSemester.progress = (weeksCompleted * 100 / 16).coerceAtMost(100)
            }
        }
    }

    private fun setupPieChart() {
        binding.pieChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)
            setTransparentCircleAlpha(0)
            holeRadius = 58f
            setDrawEntryLabels(false)
            setUsePercentValues(true)
            legend.isEnabled = true
            setEntryLabelTextSize(12f)
        }
    }

    private fun updatePieChartData(userId: String) {
        // This is sample data, in real application you would calculate this based on user's tasks
        val pieEntries = ArrayList<PieEntry>()
        val taskStatusMap = mutableMapOf<Int, Int>() // Map<Status, Count>

        // Get all tasks for the user
        val allTasks = dbHelper.getTasksForUser(userId)

        // Count tasks by status
        for (task in allTasks) {
            val count = taskStatusMap.getOrDefault(task.status, 0)
            taskStatusMap[task.status] = count + 1
        }

        // Create pie entries for each status
        val statusLabels = listOf("Not Started", "In Progress", "Completed")
        val statusColors = listOf(Color.RED, Color.YELLOW, Color.GREEN)

        for (status in 0..2) {
            val count = taskStatusMap.getOrDefault(status, 0)
            if (count > 0) {
                pieEntries.add(PieEntry(count.toFloat(), statusLabels[status]))
            }
        }

        // If there are no tasks, add a default entry
        if (pieEntries.isEmpty()) {
            pieEntries.add(PieEntry(1f, "No Tasks"))
        }

        val dataSet = PieDataSet(pieEntries, "Task Status").apply {
            colors = statusColors
            valueTextSize = 14f
            valueTextColor = Color.WHITE
        }

        val pieData = PieData(dataSet)
        binding.pieChart.data = pieData
        binding.pieChart.invalidate()
    }

    private fun calculateWeeksCompleted(startDateMillis: Long): Int {
        val currentTime = System.currentTimeMillis()
        val diffInMillis = currentTime - startDateMillis
        val weeks = (diffInMillis / (1000 * 60 * 60 * 24 * 7)).toInt()
        return weeks.coerceAtLeast(0)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}