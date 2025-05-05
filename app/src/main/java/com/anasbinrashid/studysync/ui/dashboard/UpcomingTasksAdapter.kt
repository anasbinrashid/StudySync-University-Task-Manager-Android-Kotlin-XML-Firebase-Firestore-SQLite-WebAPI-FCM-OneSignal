package com.anasbinrashid.studysync.ui.dashboard

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.ItemUpcomingTaskBinding
import com.anasbinrashid.studysync.model.Task
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class UpcomingTasksAdapter : ListAdapter<Task, UpcomingTasksAdapter.TaskViewHolder>(TaskDiffCallback()) {

    private var onItemClickListener: ((Task) -> Unit)? = null

    fun setOnItemClickListener(listener: (Task) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemUpcomingTaskBinding.inflate(
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

    inner class TaskViewHolder(private val binding: ItemUpcomingTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener?.invoke(getItem(position))
                }
            }
        }

        fun bind(task: Task) {
            binding.tvTaskTitle.text = task.title
            binding.tvCourseName.text = task.courseName

            // Format due date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
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

            // Set priority indicator color
            val priorityColor = when (task.priority) {
                0 -> R.color.colorSuccess  // Low priority
                1 -> R.color.colorWarning  // Medium priority
                else -> R.color.colorError // High priority
            }
            binding.viewPriorityIndicator.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(binding.root.context, priorityColor)
            )

            // Set task type icon
            val typeIcon = when (task.type) {
                0 -> R.drawable.ic_assignment // Assignment
                1 -> R.drawable.ic_project    // Project
                2 -> R.drawable.ic_exam       // Exam
                else -> R.drawable.ic_tasks   // Other
            }
            binding.ivTaskType.setImageResource(typeIcon)

            // Show status indicator
            val statusColor = when (task.status) {
                0 -> R.color.colorError      // Not Started
                1 -> R.color.colorWarning    // In Progress
                else -> R.color.colorSuccess // Completed
            }
            binding.viewStatusIndicator.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(binding.root.context, statusColor)
            )
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