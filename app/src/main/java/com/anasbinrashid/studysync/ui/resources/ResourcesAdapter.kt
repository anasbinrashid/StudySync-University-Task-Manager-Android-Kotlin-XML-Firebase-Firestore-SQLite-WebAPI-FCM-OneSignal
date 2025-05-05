package com.anasbinrashid.studysync.ui.resources

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.ItemResourceBinding
import com.anasbinrashid.studysync.model.Resource
import com.google.android.material.chip.Chip
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class ResourcesAdapter(private val onItemClick: (Resource) -> Unit) :
    ListAdapter<Resource, ResourcesAdapter.ResourceViewHolder>(ResourceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResourceViewHolder {
        val binding = ItemResourceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ResourceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResourceViewHolder, position: Int) {
        val resource = getItem(position)
        holder.bind(resource)
    }

    inner class ResourceViewHolder(private val binding: ItemResourceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(resource: Resource) {
            binding.tvResourceTitle.text = resource.title
            binding.tvCourseName.text = resource.courseName
            binding.tvResourceDescription.text = resource.description

            // Format date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            binding.tvDateAdded.text = dateFormat.format(resource.dateAdded)

            // Set resource type icon
            val typeIcon = when (resource.type) {
                0 -> R.drawable.ic_note
                1 -> R.drawable.ic_profile
                2 -> R.drawable.ic_document
                3 -> R.drawable.ic_assignment
                else -> R.drawable.ic_resources
            }
            binding.ivResourceType.setImageResource(typeIcon)

            // Setup tags
            setupTagChips(resource.tags)

            // Handle thumbnail for images and documents
            if (resource.type == 1 && resource.thumbnailPath.isNotEmpty()) { // Image
                binding.ivThumbnail.visibility = View.VISIBLE
                Glide.with(binding.root.context)
                    .load(File(resource.thumbnailPath))
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .centerCrop()
                    .into(binding.ivThumbnail)
            } else if (resource.type == 2 && resource.thumbnailPath.isNotEmpty()) { // Document with thumbnail
                binding.ivThumbnail.visibility = View.VISIBLE
                Glide.with(binding.root.context)
                    .load(File(resource.thumbnailPath))
                    .placeholder(R.drawable.placeholder_document)
                    .error(R.drawable.placeholder_document)
                    .centerCrop()
                    .into(binding.ivThumbnail)
            } else {
                binding.ivThumbnail.visibility = View.GONE
            }
        }

        private fun setupTagChips(tags: List<String>) {
            binding.chipGroupTags.removeAllViews()

            for (tag in tags.take(3)) { // Show max 3 tags
                val chip = Chip(binding.root.context).apply {
                    text = tag
                    setTextColor(ContextCompat.getColor(context, R.color.black))
                    chipBackgroundColor = ContextCompat.getColorStateList(
                        context,
                        R.color.chipBackground
                    )
                    isClickable = false
                    isCheckable = false
                    chipMinHeight = context.resources.getDimension(R.dimen.chip_min_height)
                    textSize = 12f
                }
                binding.chipGroupTags.addView(chip)
            }

            // Add a "+X more" chip if there are more than 3 tags
            if (tags.size > 3) {
                val chip = Chip(binding.root.context).apply {
                    text = "+${tags.size - 3} more"
                    setTextColor(ContextCompat.getColor(context, R.color.white))
                    chipBackgroundColor = ContextCompat.getColorStateList(
                        context,
                        R.color.colorPrimary
                    )
                    isClickable = false
                    isCheckable = false
                    chipMinHeight = context.resources.getDimension(R.dimen.chip_min_height)
                    textSize = 12f
                }
                binding.chipGroupTags.addView(chip)
            }
        }
    }

    class ResourceDiffCallback : DiffUtil.ItemCallback<Resource>() {
        override fun areItemsTheSame(oldItem: Resource, newItem: Resource): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Resource, newItem: Resource): Boolean {
            return oldItem == newItem
        }
    }
}