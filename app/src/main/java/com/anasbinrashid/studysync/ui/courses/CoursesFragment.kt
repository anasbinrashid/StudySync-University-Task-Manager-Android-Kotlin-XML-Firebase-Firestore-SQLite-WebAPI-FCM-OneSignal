package com.anasbinrashid.studysync.ui.courses

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.FragmentCoursesBinding
import com.anasbinrashid.studysync.model.Course
import com.anasbinrashid.studysync.model.User
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CoursesFragment : Fragment() {

    private var _binding: FragmentCoursesBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var coursesAdapter: CoursesAdapter

    private var allCourses: List<Course> = listOf()
    private var filteredCourses: List<Course> = listOf()
    private var currentUser: User? = null
    private var selectedSemester: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoursesBinding.inflate(inflater, container, false)
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
        setupSemesterDropdown()
        setupSearch()
        loadUserData()
    }

    private fun setupRecyclerView() {
        binding.rvCourses.layoutManager = LinearLayoutManager(requireContext())
        coursesAdapter = CoursesAdapter { course ->
            // Handle course click - Navigate to CourseDetailFragment
            navigateToCourseDetail(course)
        }
        binding.rvCourses.adapter = coursesAdapter
    }

    private fun setupSemesterDropdown() {
        val semesters = arrayOf(
            "All Semesters",
            "1st Semester", "2nd Semester", "3rd Semester", "4th Semester",
            "5th Semester", "6th Semester", "7th Semester", "8th Semester"
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, semesters)
        binding.actSemesterFilter.setAdapter(adapter)
        binding.actSemesterFilter.setText(semesters[0], false)

        binding.actSemesterFilter.setOnItemClickListener { _, _, position, _ ->
            selectedSemester = semesters[position]
            filterCourses()
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                filterCourses()
            }
        })
    }

    private fun loadUserData() {
        val currentFirebaseUser = auth.currentUser

        if (currentFirebaseUser != null) {
            val userId = currentFirebaseUser.uid

            // Get user from local database
            currentUser = dbHelper.getUser(userId)

            if (currentUser != null) {
                // Set current semester in dropdown
                if (currentUser!!.currentSemester.isNotEmpty()) {
                    binding.actSemesterFilter.setText(currentUser!!.currentSemester, false)
                    selectedSemester = currentUser!!.currentSemester
                }

                // Load courses
                loadCourses(userId)
            } else {
                // If not found locally, try to fetch from Firestore
                db.collection("users")
                    .document(userId)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val userName = document.getString("name") ?: ""
                            val userEmail = document.getString("email") ?: ""
                            val university = document.getString("university") ?: ""
                            val semester = document.getString("currentSemester") ?: ""

                            currentUser = User(
                                id = userId,
                                name = userName,
                                email = userEmail,
                                university = university,
                                currentSemester = semester
                            )

                            // Save user to local database
                            dbHelper.addUser(currentUser!!)

                            // Set current semester in dropdown
                            if (semester.isNotEmpty()) {
                                binding.actSemesterFilter.setText(semester, false)
                                selectedSemester = semester
                            }

                            // Load courses
                            loadCourses(userId)
                        } else {
                            showLoading(false)
                            Toast.makeText(requireContext(), "User data not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        showLoading(false)
                        Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            showLoading(false)
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCourses(userId: String) {
        showLoading(true)

        // Load courses from local database
        allCourses = dbHelper.getCoursesForUser(userId)
        filteredCourses = allCourses

        updateCoursesList()
        showLoading(false)

        // Try to sync with Firestore if network is available
        syncCoursesWithFirestore(userId)
    }

    private fun syncCoursesWithFirestore(userId: String) {
        // Get all unsynced courses from local database
        val unsyncedCourses = dbHelper.getUnsyncedCourses()

        // Upload unsynced courses to Firestore
        for (course in unsyncedCourses) {
            db.collection("courses")
                .document(course.id)
                .set(course)
                .addOnSuccessListener {
                    // Mark course as synced in local database
                    dbHelper.markCourseAsSynced(course.id)
                }
                .addOnFailureListener { e ->
                    // Log failure but don't bother the user
                    // They can continue working offline
                }
        }

        // Get latest courses from Firestore
        db.collection("courses")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                var hasNewCourses = false

                for (document in documents) {
                    val firestoreCourse = document.toObject(Course::class.java)

                    // Check if course exists in local database
                    val localCourse = allCourses.find { it.id == firestoreCourse.id }

                    if (localCourse == null) {
                        // Add course to local database
                        dbHelper.addCourse(firestoreCourse)
                        hasNewCourses = true
                    }
                }

                if (hasNewCourses) {
                    // Reload courses from local database
                    allCourses = dbHelper.getCoursesForUser(userId)
                    filterCourses()
                }
            }
            .addOnFailureListener { e ->
                // Continue with local data, no need to alert user
            }
    }

    private fun filterCourses() {
        val searchQuery = binding.etSearch.text.toString().trim().lowercase()

        filteredCourses = allCourses

        // Filter by semester if not "All Semesters"
        if (selectedSemester.isNotEmpty() && selectedSemester != "All Semesters") {
            filteredCourses = filteredCourses.filter { it.semester == selectedSemester }
        }

        // Filter by search query
        if (searchQuery.isNotEmpty()) {
            filteredCourses = filteredCourses.filter { course ->
                course.name.lowercase().contains(searchQuery) ||
                        course.code.lowercase().contains(searchQuery) ||
                        course.instructorName.lowercase().contains(searchQuery)
            }
        }

        updateCoursesList()
    }

    private fun updateCoursesList() {
        if (filteredCourses.isEmpty()) {
            binding.rvCourses.visibility = View.GONE
            binding.tvNoCourses.visibility = View.VISIBLE
        } else {
            binding.rvCourses.visibility = View.VISIBLE
            binding.tvNoCourses.visibility = View.GONE
            coursesAdapter.submitList(filteredCourses)
        }
    }

    private fun navigateToCourseDetail(course: Course) {
        // Navigate to course detail using Navigation Component with the courseId argument
        val action = CoursesFragmentDirections.actionCoursesFragmentToCourseDetailFragment(course.id)
        findNavController().navigate(action)

    }
    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}