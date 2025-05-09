package com.anasbinrashid.studysync.ui.resources

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import androidx.lifecycle.lifecycleScope
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.FragmentViewResourceBinding
import com.anasbinrashid.studysync.model.Resource
import com.anasbinrashid.studysync.repository.ResourceRepository
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

                // Delete from MySQL/PHPMyAdmin through API
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val resourceRepository = ResourceRepository(requireContext())
                        val result = resourceRepository.deleteResource(resource)

                        // Log the result
                        if (result.isSuccess) {
                            Log.d("ViewResourceFragment", "Resource deleted from MySQL successfully")
                        } else {
                            Log.e("ViewResourceFragment", "Failed to delete resource from MySQL: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("ViewResourceFragment", "Error deleting resource from MySQL", e)
                    }

                    // Switch back to main thread for Firebase operations and UI updates
                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext // Check if Fragment is still attached

                        // Delete from Firestore
                        db.collection("resources")
                            .document(resource.id)
                            .delete()
                            .addOnSuccessListener {
                                if (!isAdded) return@addOnSuccessListener
                                showLoading(false)
                                Toast.makeText(requireContext(), "Resource deleted successfully", Toast.LENGTH_SHORT).show()
                                findNavController().navigateUp()
                            }
                            .addOnFailureListener { e ->
                                if (!isAdded) return@addOnFailureListener
                                showLoading(false)
                                // Even if Firestore deletion fails, we've deleted locally
                                Toast.makeText(requireContext(), "Resource deleted locally", Toast.LENGTH_SHORT).show()
                                findNavController().navigateUp()
                            }
                    }
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
                if (path.isNotEmpty()) {
                    File(path).delete()
                }
            }

            // Delete thumbnail if exists
            resource.thumbnailPath?.let { path ->
                if (path.isNotEmpty()) {
                    File(path).delete()
                }
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
        // Set title in toolbar
        binding.toolbar.title = resource.title

        // Set course info
        binding.tvCourseInfo.text = "Course: ${resource.courseName}"

        // Show content based on resource type
        when (resource.type) {
            0 -> showNoteContent(resource) // Note
            1 -> showImageContent(resource) // Image
            2 -> showDocumentContent(resource) // Document
            3 -> showLinkContent(resource) // Link
            else -> {
                // Fallback for unknown types
                showNoteContent(resource)
                Log.w("ViewResourceFragment", "Unknown resource type: ${resource.type}")
            }
        }
    }

    private fun showNoteContent(resource: Resource) {
        binding.layoutNoteContent.visibility = View.VISIBLE
        binding.layoutImageContent.visibility = View.GONE
        binding.layoutDocumentContent.visibility = View.GONE
        binding.layoutLinkContent.visibility = View.GONE

        // Read note content from the file path
        try {

                    val content = resource.description
                    if (content.isNotEmpty()) {
                        binding.tvNoteContent.text = content
                    } else {
                        binding.tvNoteContent.text = "Note content is empty"
                    }


        } catch (e: Exception) {
            Log.e("ViewResourceFragment", "Error reading note content: ${e.message}")
            binding.tvNoteContent.text = "Could not load note content: ${e.message}"
        }
    }

    private fun showImageContent(resource: Resource) {
        binding.layoutNoteContent.visibility = View.GONE
        binding.layoutImageContent.visibility = View.VISIBLE
        binding.layoutDocumentContent.visibility = View.GONE
        binding.layoutLinkContent.visibility = View.GONE

        // Load image using Glide
        try {
            if (resource.filePath.isNotEmpty()) {
                val imageFile = File(resource.filePath)
                if (imageFile.exists() && imageFile.canRead()) {
                    Glide.with(requireContext())
                        .load(imageFile)
                        .placeholder(R.drawable.placeholder_image)
                        .error(R.drawable.placeholder_image)
                        .into(binding.ivImageContent)
                } else {
                    binding.ivImageContent.setImageResource(R.drawable.placeholder_image)
                    Log.w("ViewResourceFragment", "Image file doesn't exist or can't be read: ${resource.filePath}")
                }
            } else {
                binding.ivImageContent.setImageResource(R.drawable.placeholder_image)
                Log.w("ViewResourceFragment", "Image file path is empty")
            }
        } catch (e: Exception) {
            Log.e("ViewResourceFragment", "Error loading image: ${e.message}")
            binding.ivImageContent.setImageResource(R.drawable.placeholder_image)
            showError("Could not load image: ${e.message}")
        }
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

        // Display the link
        binding.tvLink.text = resource.filePath

        // Set up link opening options
        binding.btnOpenLink.setOnClickListener {
            openLink(resource.filePath)
        }

        // Add this new button to show options for opening links
//        binding.btnLinkOptions.setOnClickListener {
//            showLinkOptions(resource.filePath)
//        }
    }

    private fun showLinkOptions(url: String) {
        val options = arrayOf(
            "Open in default browser",
            "Open in Chrome",
            "Open in Firefox",
            "Copy link to clipboard",
            "Share link"
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Open Link With")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openLink(url) // Default browser
                    1 -> openLinkWithApp(url, "com.android.chrome")
                    2 -> openLinkWithApp(url, "org.mozilla.firefox")
                    3 -> copyLinkToClipboard(url)
                    4 -> shareLink(url)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copyLinkToClipboard(url: String) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Link", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun shareLink(url: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share link via"))
    }

    private fun openLinkWithApp(url: String, packageName: String) {
        try {
            // Add http:// prefix if the URL doesn't have a scheme
            val completeUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(completeUrl)).apply {
                setPackage(packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // Check if the specific browser is installed
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
            } else {
                // If not installed, offer to install it
                val marketIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                if (marketIntent.resolveActivity(requireActivity().packageManager) != null) {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Browser Not Installed")
                        .setMessage("Would you like to install this browser from the Play Store?")
                        .setPositiveButton("Yes") { _, _ -> startActivity(marketIntent) }
                        .setNegativeButton("No") { _, _ -> openLink(url) } // Fallback to default
                        .show()
                } else {
                    // If Play Store isn't available, just try the default browser
                    openLink(url)
                }
            }
        } catch (e: Exception) {
            Log.e("ViewResourceFragment", "Error opening link with specific app: ${e.message}")
            Toast.makeText(requireContext(), "Error opening link: ${e.message}", Toast.LENGTH_SHORT).show()
            // Fallback to default browser
            openLink(url)
        }
    }

    private fun openDocument(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                showError("Document file not found")
                return
            }

            // Use FileProvider to get content URI
            val authority = "${requireContext().packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(requireContext(), authority, file)

            // Create an intent to open the document
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, getMimeType(filePath))
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // Check if there's an app that can handle this intent
            val packageManager = requireActivity().packageManager
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // Show document opening options
                showDocumentOptions(filePath, contentUri)
            }
        } catch (e: Exception) {
            Log.e("ViewResourceFragment", "Error opening document: ${e.message}")
            Toast.makeText(requireContext(), "Error opening document: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDocumentOptions(filePath: String, contentUri: Uri) {
        val options = arrayOf(
            "Open with default app",
            "Open with document viewer",
            "Share document",
            "Install document viewer"
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Open Document With")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openWithDefaultApp(contentUri, getMimeType(filePath))
                    1 -> openWithDocumentViewer(contentUri, getMimeType(filePath))
                    2 -> shareDocument(contentUri, getMimeType(filePath))
                    3 -> installDocumentViewer()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openWithDefaultApp(contentUri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("ViewResourceFragment", "Error opening with default app: ${e.message}")
            Toast.makeText(requireContext(), "No app found to open this document", Toast.LENGTH_SHORT).show()
            installDocumentViewer()
        }
    }

    private fun openWithDocumentViewer(contentUri: Uri, mimeType: String) {
        // Try to open with popular document viewers
        val viewers = arrayOf(
            "com.adobe.reader", // Adobe Acrobat
            "cn.wps.moffice_eng", // WPS Office
            "com.microsoft.office.word", // Microsoft Word
            "com.google.android.apps.docs" // Google Docs
        )

        for (viewer in viewers) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, mimeType)
                    setPackage(viewer)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }

                if (intent.resolveActivity(requireActivity().packageManager) != null) {
                    startActivity(intent)
                    return
                }
            } catch (e: Exception) {
                continue
            }
        }

        // If no specific viewer was found
        openWithDefaultApp(contentUri, mimeType)
    }

    private fun shareDocument(contentUri: Uri, mimeType: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, contentUri)
            type = mimeType
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(Intent.createChooser(shareIntent, "Share document via"))
    }

    private fun installDocumentViewer() {
        val marketIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://search?q=pdf viewer&c=apps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (marketIntent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(marketIntent)
        } else {
            Toast.makeText(requireContext(), "Play Store not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(filePath: String): String {
        return when {
            filePath.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            filePath.endsWith(".doc", ignoreCase = true) -> "application/msword"
            filePath.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            filePath.endsWith(".xls", ignoreCase = true) -> "application/vnd.ms-excel"
            filePath.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            filePath.endsWith(".ppt", ignoreCase = true) -> "application/vnd.ms-powerpoint"
            filePath.endsWith(".pptx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            filePath.endsWith(".txt", ignoreCase = true) -> "text/plain"
            else -> "*/*"
        }
    }

    private fun openLink(url: String) {
        try {
            // Add http:// prefix if the URL doesn't have a scheme
            val completeUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }

            // Create a more robust intent for opening the link
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(completeUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try {
                // Try to open the link with an explicit chooser
                startActivity(Intent.createChooser(intent, "Open link with"))
                return
            } catch (e: Exception) {
                Log.e("ViewResourceFragment", "Error opening link with chooser: ${e.message}")
                // Continue with fallback options
            }

            // Check if there's a browser that can handle this intent
            val packageManager = requireActivity().packageManager
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // Try with specific browsers
                val browsers = arrayOf(
                    "com.android.chrome",
                    "org.mozilla.firefox",
                    "com.opera.browser",
                    "com.microsoft.emmx"
                )

                for (browser in browsers) {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(completeUrl)).apply {
                            setPackage(browser)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }

                        if (browserIntent.resolveActivity(packageManager) != null) {
                            startActivity(browserIntent)
                            return
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }

                // If all else fails, show error and offer to install a browser
                showError("No browser found to open this link")
                offerBrowserInstall()
            }
        } catch (e: Exception) {
            Log.e("ViewResourceFragment", "Error opening link: ${e.message}")
            Toast.makeText(requireContext(), "Error opening link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun offerBrowserInstall() {
        val marketIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=com.android.chrome")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (marketIntent.resolveActivity(requireActivity().packageManager) != null) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("No Browser Found")
                .setMessage("Would you like to install Chrome from the Play Store?")
                .setPositiveButton("Yes") { _, _ -> startActivity(marketIntent) }
                .setNegativeButton("No", null)
                .show()
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