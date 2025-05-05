package com.anasbinrashid.studysync.ui.notifications

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.ItemReminderBinding
import com.anasbinrashid.studysync.model.Task
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ReminderAdapter(
    private val onItemClick: (Task) -> Unit,
    private val onToggleReminder: (Task, Boolean) -> Unit
) : ListAdapter<Task, ReminderAdapter.ReminderViewHolder>(ReminderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val binding = ItemReminderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReminderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val task = getItem(position)
        holder.bind(task)
    }

    inner class ReminderViewHolder(private val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onToggleReminder(getItem(position), isChecked)
                }
            }
        }

        fun bind(task: Task) {
            val context = binding.root.context

            // Set task details
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

            // Set days remaining background color based on urgency
            val daysRemainingBg = when {
                task.status == 2 -> R.color.colorSuccess // Completed
                diffInDays < 0 -> R.color.colorError     // Overdue
                diffInDays == 0L -> R.color.colorError   // Due today
                diffInDays <= 2 -> R.color.colorWarning  // Due soon
                else -> R.color.colorInfo                // Not urgent
            }
            binding.tvDaysRemaining.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, daysRemainingBg)
            )

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
                ContextCompat.getColor(context, priorityColor)
            )

            // Set reminder switch
            binding.switchReminder.isChecked = task.reminderSet
        }
    }

    class ReminderDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem == newItem
        }
    }
}