package com.anasbinrashid.studysync.ui.resources

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.FragmentViewResourceBinding
import com.anasbinrashid.studysync.model.Resource
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

class ViewResourceFragment : Fragment() {

    private var _binding: FragmentViewResourceBinding? = null
    private val binding get() = _binding!!

    private val args: ViewResourceFragmentArgs by navArgs()
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper
    private var currentResource: Resource? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        _binding = FragmentViewResourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize local database
        dbHelper = DatabaseHelper(requireContext())

        setupToolbar()
        loadResourceDetails()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Add delete menu item
        binding.toolbar.inflateMenu(R.menu.menu_view_resource)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_delete -> {
                    showDeleteConfirmation()
                    true
                }
                else -> false
            }
        }
    }

    private fun showDeleteConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Resource")
            .setMessage("Are you sure you want to delete this resource? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteResource()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteResource() {
        showLoading(true)
        currentResource?.let { resource ->
            try {
                // Delete from local database
                dbHelper.deleteResource(resource.id)

                // Delete associated files
                deleteResourceFiles(resource)

                // Delete from Firestore
                db.collection("resources")
                    .document(resource.id)
                    .delete()
                    .addOnSuccessListener {
                        showLoading(false)
                        Toast.makeText(requireContext(), "Resource deleted successfully", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        // Even if Firestore deletion fails, we've deleted locally
                        Toast.makeText(requireContext(), "Resource deleted locally", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
            } catch (e: Exception) {
                showLoading(false)
                showError("Error deleting resource: ${e.message}")
            }
        }
    }

    private fun deleteResourceFiles(resource: Resource) {
        try {
            // Delete main file
            resource.filePath?.let { path ->
                File(path).delete()
            }

            // Delete thumbnail if exists
            resource.thumbnailPath?.let { path ->
                File(path).delete()
            }
        } catch (e: Exception) {
            // Log error but continue with deletion
            e.printStackTrace()
        }
    }

    private fun loadResourceDetails() {
        showLoading(true)

        // Load from local database
        val resource = dbHelper.getResourceById(args.resourceId)

        if (resource != null) {
            currentResource = resource
            populateUI(resource)
            showLoading(false)
        } else {
            // If not found locally, try to fetch from Firestore
            db.collection("resources")
                .document(args.resourceId)
                .get()
                .addOnSuccessListener { document ->
                    showLoading(false)
                    if (document != null && document.exists()) {
                        val resource = document.toObject(Resource::class.java)
                        if (resource != null) {
                            // Save to local database
                            dbHelper.addResource(resource)
                            currentResource = resource
                            populateUI(resource)
                        } else {
                            showError("Failed to load resource")
                        }
                    } else {
                        showError("Resource not found")
                    }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    showError("Error loading resource: ${e.message}")
                }
        }
    }

    private fun populateUI(resource: Resource) {
        // Set course info
        binding.tvCourseInfo.text = "Course: ${resource.courseName}"

        // Set tags
        binding.tvTags.text = resource.tags.joinToString(", ")

        // Show content based on resource type
        when (resource.type) {
            0 -> showNoteContent(resource) // Note
            1 -> showImageContent(resource) // Image
            2 -> showDocumentContent(resource) // Document
            3 -> showLinkContent(resource) // Link
        }
    }

    private fun showNoteContent(resource: Resource) {
        binding.layoutNoteContent.visibility = View.VISIBLE
        binding.layoutImageContent.visibility = View.GONE
        binding.layoutDocumentContent.visibility = View.GONE
        binding.layoutLinkContent.visibility = View.GONE

        binding.tvNoteContent.text = resource.filePath
    }

    private fun showImageContent(resource: Resource) {
        binding.layoutNoteContent.visibility = View.GONE
        binding.layoutImageContent.visibility = View.VISIBLE
        binding.layoutDocumentContent.visibility = View.GONE
        binding.layoutLinkContent.visibility = View.GONE

        // Load image using Glide
        Glide.with(this)
            .load(resource.filePath)
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.placeholder_image)
            .into(binding.ivImageContent)
    }

    private fun showDocumentContent(resource: Resource) {
        binding.layoutNoteContent.visibility = View.GONE
        binding.layoutImageContent.visibility = View.GONE
        binding.layoutDocumentContent.visibility = View.VISIBLE
        binding.layoutLinkContent.visibility = View.GONE

        binding.tvDocumentName.text = resource.title

        binding.btnOpenDocument.setOnClickListener {
            openDocument(resource.filePath)
        }
    }

    private fun showLinkContent(resource: Resource) {
        binding.layoutNoteContent.visibility = View.GONE
        binding.layoutImageContent.visibility = View.GONE
        binding.layoutDocumentContent.visibility = View.GONE
        binding.layoutLinkContent.visibility = View.VISIBLE

        binding.tvLink.text = resource.filePath

        binding.btnOpenLink.setOnClickListener {
            openLink(resource.filePath)
        }
    }

    private fun openDocument(filePath: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = Uri.parse(filePath)
            intent.setDataAndType(uri, "application/pdf")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error opening document: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error opening link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}