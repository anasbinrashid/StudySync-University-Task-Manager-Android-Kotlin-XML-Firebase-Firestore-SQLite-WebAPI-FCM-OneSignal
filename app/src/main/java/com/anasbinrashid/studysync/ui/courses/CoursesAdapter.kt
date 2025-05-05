package com.anasbinrashid.studysync.ui.courses

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.ItemCourseBinding
import com.anasbinrashid.studysync.model.Course
import com.google.android.material.chip.Chip

class CoursesAdapter(private val onItemClick: (Course) -> Unit) :
    ListAdapter<Course, CoursesAdapter.CourseViewHolder>(CourseDiffCallback()) {

    private val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val binding = ItemCourseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CourseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val course = getItem(position)
        holder.bind(course)

//        holder.itemView.setOnClickListener {
//            onItemClick(course)
//        }
    }

    inner class CourseViewHolder(private val binding: ItemCourseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(course: Course) {
            // Set course details
            binding.tvCourseName.text = course.name
            binding.tvCourseCode.text = course.code
            binding.tvCreditHours.text = "${course.creditHours} Credit Hours"
            binding.tvInstructorName.text = course.instructorName
            binding.tvLocation.text = "Room ${course.room}"

            // Set course color
            val courseColor = if (course.color != 0) {
                course.color
            } else {
                ContextCompat.getColor(binding.root.context, R.color.colorPrimary)
            }

            binding.viewColorIndicator.setBackgroundColor(courseColor)
            binding.tvCourseCode.backgroundTintList = ColorStateList.valueOf(courseColor)

            // Set class schedule
            setupDayChips(course)

            // Add time chip
            binding.chipTime.text = "${course.startTime} - ${course.endTime}"
        }

        private fun setupDayChips(course: Course) {
            // Clear existing day chips (except time chip)
            binding.chipGroupDays.removeAllViews()

            // Add day chips
            for (day in course.dayOfWeek) {
                val chip = Chip(binding.root.context).apply {
                    text = dayNames[day]
                    setTextColor(ContextCompat.getColor(context, R.color.black))
                    chipBackgroundColor = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.colorPrimary)
                    )
                    isClickable = false
                    isCheckable = false
                    // Fix: Use a direct value or standard dimension instead of custom resource
                    // Original: chipMinHeight = resources.getDimension(R.dimen.chip_min_height)
                    chipMinHeight = 32f // Set a direct value in dp
                    textSize = 12f
                }
                binding.chipGroupDays.addView(chip)
            }

            // Add time chip
            binding.chipGroupDays.addView(binding.chipTime)
        }
    }

    class CourseDiffCallback : DiffUtil.ItemCallback<Course>() {
        override fun areItemsTheSame(oldItem: Course, newItem: Course): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Course, newItem: Course): Boolean {
            return oldItem == newItem
        }
    }
}