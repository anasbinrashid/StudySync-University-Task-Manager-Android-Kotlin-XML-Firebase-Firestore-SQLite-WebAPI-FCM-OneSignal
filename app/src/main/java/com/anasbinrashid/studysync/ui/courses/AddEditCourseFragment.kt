package com.anasbinrashid.studysync.ui.courses

import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.FragmentAddEditCourseBinding
import com.anasbinrashid.studysync.model.Course
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddEditCourseFragment : Fragment() {

    private var _binding: FragmentAddEditCourseBinding? = null
    private val binding get() = _binding!!

    private val args: AddEditCourseFragmentArgs by navArgs()
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper

    private var isEditMode = false
    private var currentCourse: Course? = null
    private var selectedDays = mutableListOf<Int>()
    private var selectedColorId = R.color.colorPrimary
    private var selectedColor = 0

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val startTimeCalendar = Calendar.getInstance()
    private val endTimeCalendar = Calendar.getInstance()

    private val colorChips = mutableListOf<Chip>()
    private val dayChips = mutableListOf<Chip>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditCourseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize local database
        dbHelper = DatabaseHelper(requireContext())

        // Initialize UI components
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

        // Set up semester dropdown
        setupSemesterDropdown()

        // Set up time pickers
        binding.etStartTime.setOnClickListener {
            showTimePicker(true)
        }

        binding.etEndTime.setOnClickListener {
            showTimePicker(false)
        }

        // Set up day chips
        setupDayChips()

        // Set up color chips
        setupColorChips()
    }

    private fun setupSemesterDropdown() {
        val semesters = arrayOf(
            "1st Semester", "2nd Semester", "3rd Semester", "4th Semester",
            "5th Semester", "6th Semester", "7th Semester", "8th Semester"
        )

        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, semesters)
        binding.actSemester.setAdapter(adapter)
    }

    private fun setupDayChips() {
        dayChips.apply {
            add(binding.chipSunday)
            add(binding.chipMonday)
            add(binding.chipTuesday)
            add(binding.chipWednesday)
            add(binding.chipThursday)
            add(binding.chipFriday)
            add(binding.chipSaturday)
        }

        // Set up each day chip with the correct listener
        for (i in dayChips.indices) {
            val chip = dayChips[i]

            // Configure the chip
            chip.isCheckable = true

            // Set the click listener for toggling selection
            chip.setOnClickListener {
                if (chip.isChecked) {
                    selectedDays.add(i)
                } else {
                    selectedDays.remove(i)
                }

                // Force UI update to reflect selection state
                updateDayChipAppearance(chip, chip.isChecked)
            }
        }
    }

    private fun updateDayChipAppearance(chip: Chip, isSelected: Boolean) {
        if (isSelected) {
            chip.chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.colorPrimary)
            )
            chip.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.white)
            )
        } else {
            chip.chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.colorPrimaryLight)
            )
            chip.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.colorPrimary)
            )
        }
    }

    private fun setupColorChips() {
        colorChips.apply {
            add(binding.chipColor1)
            add(binding.chipColor2)
            add(binding.chipColor3)
            add(binding.chipColor4)
            add(binding.chipColor5)
            add(binding.chipColor6)
        }

        val colorIds = intArrayOf(
            R.color.colorPrimary,
            R.color.colorAccent,
            R.color.colorSuccess,
            R.color.colorWarning,
            R.color.colorError,
            R.color.colorInfo
        )

        // Make sure all color chips are properly configured
        for (i in colorChips.indices) {
            val chip = colorChips[i]
            chip.isCheckable = true

            // Set the color correctly
            chip.chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), colorIds[i])
            )

            // Set click listener
            chip.setOnClickListener {
                // Update selectedColorId and selectedColor
                selectedColorId = colorIds[i]
                selectedColor = ContextCompat.getColor(requireContext(), selectedColorId)

                // Update UI to show this chip as selected
                updateColorChipSelection(i)
            }
        }

        // Set first color as default
        colorChips[0].isChecked = true
        selectedColorId = colorIds[0]
        selectedColor = ContextCompat.getColor(requireContext(), selectedColorId)

        // Set ChipGroup listener to handle single selection
        binding.chipGroupColors.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chipId = checkedIds[0]
                val index = colorChips.indexOfFirst { it.id == chipId }
                if (index != -1) {
                    selectedColorId = colorIds[index]
                    selectedColor = ContextCompat.getColor(requireContext(), selectedColorId)
                }
            }
        }
    }

    private fun updateColorChipSelection(selectedIndex: Int) {
        for (i in colorChips.indices) {
            colorChips[i].isChecked = (i == selectedIndex)
        }
    }

    private fun setupListeners() {
        // Save button
        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                saveCourse()
            }
        }

        // Delete button (visible in edit mode only)
        binding.btnDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun determineMode() {
        val courseId = args.courseId

        if (courseId.isEmpty()) {
            // Add mode
            isEditMode = false
            binding.toolbar.title = "Add Course"
            binding.btnDelete.visibility = View.GONE

            // Set default times (9 AM to 10:30 AM)
            startTimeCalendar.set(Calendar.HOUR_OF_DAY, 9)
            startTimeCalendar.set(Calendar.MINUTE, 0)
            endTimeCalendar.set(Calendar.HOUR_OF_DAY, 10)
            endTimeCalendar.set(Calendar.MINUTE, 30)
            updateTimeDisplay()
        } else {
            // Edit mode
            isEditMode = true
            binding.toolbar.title = "Edit Course"
            binding.btnDelete.visibility = View.VISIBLE

            loadCourseDetails(courseId)
        }
    }

    private fun loadCourseDetails(courseId: String) {
        showLoading(true)

        // Load from local database
        currentCourse = dbHelper.getCourseById(courseId)

        if (currentCourse != null) {
            populateUI(currentCourse!!)
            showLoading(false)
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
                            populateUI(currentCourse!!)
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

    private fun populateUI(course: Course) {
        binding.etCourseName.setText(course.name)
        binding.etCourseCode.setText(course.code)
        binding.actSemester.setText(course.semester, false)
        binding.etCreditHours.setText(course.creditHours.toString())
        binding.etInstructorName.setText(course.instructorName)
        binding.etInstructorEmail.setText(course.instructorEmail)
        binding.etRoom.setText(course.room)

        // Set selected days
        selectedDays.clear()
        selectedDays.addAll(course.dayOfWeek)
        for (i in dayChips.indices) {
            val isSelected = selectedDays.contains(i)
            dayChips[i].isChecked = isSelected
            updateDayChipAppearance(dayChips[i], isSelected)
        }

        // Set times
        try {
            // Parse times from strings
            val startTime = timeFormat.parse(course.startTime)
            val endTime = timeFormat.parse(course.endTime)

            if (startTime != null) {
                startTimeCalendar.time = startTime
            }
            if (endTime != null) {
                endTimeCalendar.time = endTime
            }
        } catch (e: Exception) {
            // Set default times if parsing fails
            startTimeCalendar.set(Calendar.HOUR_OF_DAY, 9)
            startTimeCalendar.set(Calendar.MINUTE, 0)
            endTimeCalendar.set(Calendar.HOUR_OF_DAY, 10)
            endTimeCalendar.set(Calendar.MINUTE, 30)
        }
        updateTimeDisplay()

        // Set color
        selectedColor = course.color
        for (i in colorChips.indices) {
            val chipColor = ContextCompat.getColor(requireContext(), getColorResourceIdByIndex(i))
            if (chipColor == selectedColor) {
                colorChips[i].isChecked = true
                break
            }
        }
    }

    private fun getColorResourceIdByIndex(index: Int): Int {
        return when (index) {
            0 -> R.color.colorPrimary
            1 -> R.color.colorAccent
            2 -> R.color.colorSuccess
            3 -> R.color.colorWarning
            4 -> R.color.colorError
            5 -> R.color.colorInfo
            else -> R.color.colorPrimary
        }
    }

    private fun showTimePicker(isStartTime: Boolean) {
        val calendar = if (isStartTime) startTimeCalendar else endTimeCalendar
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                calendar.set(Calendar.MINUTE, selectedMinute)
                updateTimeDisplay()
            },
            hour,
            minute,
            false
        )

        timePickerDialog.show()
    }

    private fun updateTimeDisplay() {
        binding.etStartTime.setText(timeFormat.format(startTimeCalendar.time))
        binding.etEndTime.setText(timeFormat.format(endTimeCalendar.time))
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        // Reset errors
        binding.tilCourseName.error = null
        binding.tilCourseCode.error = null
        binding.tilSemester.error = null
        binding.tilCreditHours.error = null
        binding.tilInstructorName.error = null
        binding.tilRoom.error = null
        binding.tilStartTime.error = null
        binding.tilEndTime.error = null

        // Validate course name
        if (binding.etCourseName.text.toString().trim().isEmpty()) {
            binding.tilCourseName.error = "Course name is required"
            isValid = false
        }

        // Validate course code
        if (binding.etCourseCode.text.toString().trim().isEmpty()) {
            binding.tilCourseCode.error = "Course code is required"
            isValid = false
        }

        // Validate semester
        if (binding.actSemester.text.toString().trim().isEmpty()) {
            binding.tilSemester.error = "Semester is required"
            isValid = false
        }

        // Validate credit hours
        if (binding.etCreditHours.text.toString().trim().isEmpty()) {
            binding.tilCreditHours.error = "Credit hours is required"
            isValid = false
        } else {
            try {
                val creditHours = binding.etCreditHours.text.toString().toInt()
                if (creditHours <= 0 || creditHours > 10) {
                    binding.tilCreditHours.error = "Credit hours must be between 1 and 10"
                    isValid = false
                }
            } catch (e: NumberFormatException) {
                binding.tilCreditHours.error = "Please enter a valid number"
                isValid = false
            }
        }

        // Validate instructor name
        if (binding.etInstructorName.text.toString().trim().isEmpty()) {
            binding.tilInstructorName.error = "Instructor name is required"
            isValid = false
        }

        // Validate room
        if (binding.etRoom.text.toString().trim().isEmpty()) {
            binding.tilRoom.error = "Room is required"
            isValid = false
        }

        // Validate selected days
        if (selectedDays.isEmpty()) {
            Toast.makeText(requireContext(), "Please select at least one day", Toast.LENGTH_SHORT)
                .show()
            isValid = false
        }

        // Validate times
        if (binding.etStartTime.text.toString().isEmpty()) {
            binding.tilStartTime.error = "Start time is required"
            isValid = false
        }

        if (binding.etEndTime.text.toString().isEmpty()) {
            binding.tilEndTime.error = "End time is required"
            isValid = false
        }

        // Check if end time is after start time
        if (binding.etStartTime.text.toString().isNotEmpty() && binding.etEndTime.text.toString()
                .isNotEmpty()
        ) {
            if (endTimeCalendar.timeInMillis <= startTimeCalendar.timeInMillis) {
                binding.tilEndTime.error = "End time must be after start time"
                isValid = false
            }
        }

        return isValid
    }

    private fun saveCourse() {
        val userId = auth.currentUser?.uid ?: return
        val name = binding.etCourseName.text.toString().trim()
        val code = binding.etCourseCode.text.toString().trim()
        val semester = binding.actSemester.text.toString().trim()
        val creditHours = binding.etCreditHours.text.toString().toInt()
        val instructorName = binding.etInstructorName.text.toString().trim()
        val instructorEmail = binding.etInstructorEmail.text.toString().trim()
        val room = binding.etRoom.text.toString().trim()
        val startTime = binding.etStartTime.text.toString().trim()
        val endTime = binding.etEndTime.text.toString().trim()

        // Create or update course
        if (isEditMode && currentCourse != null) {
            // Update existing course
            val updatedCourse = currentCourse!!.copy(
                name = name,
                code = code,
                instructorName = instructorName,
                instructorEmail = instructorEmail,
                room = room,
                dayOfWeek = selectedDays.sorted(),
                startTime = startTime,
                endTime = endTime,
                semester = semester,
                creditHours = creditHours,
                color = selectedColor,
                isSynced = false
            )

            // Update in local database
            dbHelper.updateCourse(updatedCourse)

            // Update in Firestore
            db.collection("courses")
                .document(updatedCourse.id)
                .set(updatedCourse)
                .addOnSuccessListener {
                    Toast.makeText(
                        requireContext(),
                        "Course updated successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigateUp()
                }
                .addOnFailureListener { e ->
                    // Continue with local update even if Firestore update fails
                    Toast.makeText(requireContext(), "Course updated locally", Toast.LENGTH_SHORT)
                        .show()
                    findNavController().navigateUp()
                }
        } else {
            // Create new course
            val newCourse = Course(
                id = UUID.randomUUID().toString(),
                userId = userId,
                name = name,
                code = code,
                instructorName = instructorName,
                instructorEmail = instructorEmail,
                room = room,
                dayOfWeek = selectedDays.sorted(),
                startTime = startTime,
                endTime = endTime,
                semester = semester,
                creditHours = creditHours,
                color = selectedColor,
                isSynced = false
            )

            // Save to local database
            dbHelper.addCourse(newCourse)

            // Save to Firestore
            db.collection("courses")
                .document(newCourse.id)
                .set(newCourse)
                .addOnSuccessListener {
                    Toast.makeText(
                        requireContext(),
                        "Course added successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigateUp()
                }
                .addOnFailureListener { e ->
                    // Continue with local save even if Firestore save fails
                    Toast.makeText(requireContext(), "Course added locally", Toast.LENGTH_SHORT)
                        .show()
                    findNavController().navigateUp()
                }
        }
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Course")
            .setMessage("Are you sure you want to delete this course? All tasks associated with this course will also be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                deleteCourse()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCourse() {
        if (currentCourse != null) {
            val courseId = currentCourse!!.id

            // Delete from local database
            dbHelper.deleteCourse(courseId)

            // Delete from Firestore
            db.collection("courses")
                .document(courseId)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Course deleted", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                .addOnFailureListener { e ->
                    // Continue with local deletion even if Firestore deletion fails
                    Toast.makeText(requireContext(), "Course deleted locally", Toast.LENGTH_SHORT)
                        .show()
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