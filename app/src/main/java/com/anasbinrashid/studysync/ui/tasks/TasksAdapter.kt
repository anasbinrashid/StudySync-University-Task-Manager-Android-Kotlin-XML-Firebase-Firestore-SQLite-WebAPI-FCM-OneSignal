package com.anasbinrashid.studysync.ui.tasks

import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.ItemTaskBinding
import com.anasbinrashid.studysync.model.Task
import com.anasbinrashid.studysync.util.DatabaseHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TasksAdapter(private val onItemClick: (Task) -> Unit) :
    ListAdapter<Task, TasksAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = getItem(position)
        holder.bind(task)
    }

    inner class TaskViewHolder(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            binding.checkboxStatus.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val task = getItem(position)
                    val dbHelper = DatabaseHelper(binding.root.context)

                    // Toggle task status between not started (0) and completed (2)
                    val newStatus = if (task.status != 2) 2 else 0

                    // Create a new task with updated status
                    val updatedTask = task.copy(
                        status = newStatus,
                        lastUpdated = Date(),
                        isSynced = false
                    )

                    // Update task in database
                    dbHelper.updateTask(updatedTask)

                    // Update UI
                    updateTaskAppearance(binding, updatedTask)
                }
            }
        }

        fun bind(task: Task) {
            binding.tvTaskTitle.text = task.title
            binding.tvCourseName.text = task.courseName

            // Format due date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
            binding.tvDueDate.text = dateFormat.format(task.dueDate)

            // Set checkbox state
            binding.checkboxStatus.isChecked = task.status == 2

            // Calculate days remaining
            val currentTime = System.currentTimeMillis()
            val dueTime = task.dueDate?.time
            val diffInMillis = dueTime?.minus(currentTime)
            val diffInDays = diffInMillis?.let { TimeUnit.MILLISECONDS.toDays(it) }

            val daysText = when {
                diffInDays!! < 0 -> "Overdue!"
                diffInDays == 0L -> "Due today!"
                diffInDays == 1L -> "Due tomorrow"
                else -> "$diffInDays days left"
            }
            binding.tvDaysRemaining.text = daysText

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
            binding.viewPriorityIndicator.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(binding.root.context, priorityColor)
            )

            // Set days remaining background color based on urgency
            val daysRemainingBg = when {
                task.status == 2 -> R.color.colorSuccess // Completed
                diffInDays < 0 -> R.color.colorError     // Overdue
                diffInDays == 0L -> R.color.colorError   // Due today
                diffInDays <= 2 -> R.color.colorWarning  // Due soon
                else -> R.color.colorInfo                // Not urgent
            }
            binding.tvDaysRemaining.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(binding.root.context, daysRemainingBg)
            )

            // Update task appearance based on status
            updateTaskAppearance(binding, task)
        }

        private fun updateTaskAppearance(binding: ItemTaskBinding, task: Task) {
            if (task.status == 2) { // Completed
                binding.tvTaskTitle.apply {
                    paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    alpha = 0.6f
                }
                binding.tvCourseName.alpha = 0.6f
                binding.tvDueDate.alpha = 0.6f
                binding.ivTaskType.alpha = 0.6f
            } else {
                binding.tvTaskTitle.apply {
                    paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    alpha = 1.0f
                }
                binding.tvCourseName.alpha = 1.0f
                binding.tvDueDate.alpha = 1.0f
                binding.ivTaskType.alpha = 1.0f
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem == newItem
        }
    }
}