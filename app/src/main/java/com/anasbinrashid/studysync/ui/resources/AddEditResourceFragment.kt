package com.anasbinrashid.studysync.ui.resources

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.FragmentAddEditResourceBinding
import com.anasbinrashid.studysync.model.Course
import com.anasbinrashid.studysync.model.Resource
import com.anasbinrashid.studysync.repository.ResourceRepository
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.anasbinrashid.studysync.util.NotificationHelper
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AddEditResourceFragment : Fragment() {

    private var _binding: FragmentAddEditResourceBinding? = null
    private val binding get() = _binding!!

    private val args: AddEditResourceFragmentArgs by navArgs()
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var dbHelper: DatabaseHelper

    private var isEditMode = false
    private var currentResource: Resource? = null
    private var selectedCourse: Course? = null
    private val coursesList = mutableListOf<Course>()
    private val tagsList = mutableListOf<String>()

    private var selectedImageUri: Uri? = null
    private var selectedDocumentUri: Uri? = null
    private var currentPhotoPath: String = ""

    // Resource types
    private val TYPE_NOTE = 0
    private val TYPE_IMAGE = 1
    private val TYPE_DOCUMENT = 2
    private val TYPE_LINK = 3

    // Permission launcher
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            captureImage()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to capture images", Toast.LENGTH_SHORT).show()
        }
    }

    // Activity result launchers
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val file = File(currentPhotoPath)
                selectedImageUri = Uri.fromFile(file)
                loadImagePreview(selectedImageUri)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load captured image", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                loadImagePreview(selectedImageUri)
            }
        }
    }

    private val selectDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedDocumentUri = uri
                val documentName = getFileNameFromUri(uri)
                binding.tvDocumentName.text = documentName ?: "Selected document"
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditResourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Initialize local database
        dbHelper = DatabaseHelper(requireContext())

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

        // Load courses for dropdown
        loadCourses()

        // Setup tags input
        setupTagsInput()

        // Set up initial visibility based on default resource type (Note)
        updateResourceTypeVisibility(TYPE_NOTE)
    }

    private fun loadCourses() {
        val userId = auth.currentUser?.uid ?: return

        // Load courses from local database
        coursesList.clear()
        coursesList.addAll(dbHelper.getCoursesForUser(userId))

        if (coursesList.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Please add a course before creating a resource",
                Toast.LENGTH_LONG
            ).show()
            findNavController().navigateUp()
            return
        }

        // Create adapter for course dropdown
        val courseNames = coursesList.map { "${it.name} (${it.code})" }.toTypedArray()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, courseNames)
        binding.actCourse.setAdapter(adapter)

        // Set selection listener
        binding.actCourse.setOnItemClickListener { _, _, position, _ ->
            selectedCourse = coursesList[position]
        }

        // Pre-select course if courseId is passed
        val courseId = args.courseId
        if (courseId.isNotEmpty()) {
            val courseIndex = coursesList.indexOfFirst { it.id == courseId }
            if (courseIndex != -1) {
                binding.actCourse.setText(courseNames[courseIndex], false)
                selectedCourse = coursesList[courseIndex]
            }
        }
    }

    private fun setupTagsInput() {
        binding.etTags.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString().trim()
                if (text.endsWith(",")) {
                    // Create a tag from the input
                    val tagText = text.substring(0, text.length - 1).trim()
                    if (tagText.isNotEmpty() && !tagsList.contains(tagText)) {
                        addTagChip(tagText)
                        tagsList.add(tagText)
                        binding.etTags.setText("")
                    }
                }
            }
        })
    }

    private fun addTagChip(tagText: String) {
        val chip = Chip(requireContext()).apply {
            text = tagText
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                binding.chipGroupTags.removeView(this)
                tagsList.remove(tagText)
            }
        }
        binding.chipGroupTags.addView(chip)
    }

    private fun setupListeners() {
        // Type radio buttons
        binding.rbNote.setOnClickListener { updateResourceTypeVisibility(TYPE_NOTE) }
        binding.rbImage.setOnClickListener { updateResourceTypeVisibility(TYPE_IMAGE) }
        binding.rbDocument.setOnClickListener { updateResourceTypeVisibility(TYPE_DOCUMENT) }
        binding.rbLink.setOnClickListener { updateResourceTypeVisibility(TYPE_LINK) }

        // Camera button
        binding.btnCaptureImage.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                captureImage()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Gallery button
        binding.btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            selectImageLauncher.launch(intent)
        }

        // Select document button
        binding.btnSelectDocument.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            selectDocumentLauncher.launch(intent)
        }

        // Save button
        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                saveResource()
            }
        }

        // Delete button (visible in edit mode only)
        binding.btnDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun determineMode() {
        val resourceId = args.resourceId

        if (resourceId.isEmpty()) {
            // Add mode
            isEditMode = false
            binding.toolbar.title = "Add Resource"
            binding.btnDelete.visibility = View.GONE
        } else {
            // Edit mode
            isEditMode = true
            binding.toolbar.title = "Edit Resource"
            binding.btnDelete.visibility = View.VISIBLE

            loadResourceDetails(resourceId)
        }
    }

    private fun loadResourceDetails(resourceId: String) {
        showLoading(true)

        // Load from local database
        currentResource = dbHelper.getResourceById(resourceId)

        if (currentResource != null) {
            populateUI(currentResource!!)
            showLoading(false)
        } else {
            // If not found locally, try to fetch from Firestore
            db.collection("resources")
                .document(resourceId)
                .get()
                .addOnSuccessListener { document ->
                    showLoading(false)
                    if (document != null && document.exists()) {
                        currentResource = document.toObject(Resource::class.java)
                        if (currentResource != null) {
                            // Save to local database
                            dbHelper.addResource(currentResource!!)
                            populateUI(currentResource!!)
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
        binding.etTitle.setText(resource.title)
        binding.etDescription.setText(resource.description)

        // Set course
        val courseIndex = coursesList.indexOfFirst { it.id == resource.courseId }
        if (courseIndex != -1) {
            val courseNames = coursesList.map { "${it.name} (${it.code})" }.toTypedArray()
            binding.actCourse.setText(courseNames[courseIndex], false)
            selectedCourse = coursesList[courseIndex]
        }

        // Set tags
        tagsList.clear()
        binding.chipGroupTags.removeAllViews()
        for (tag in resource.tags) {
            addTagChip(tag)
            tagsList.add(tag)
        }

        // Set resource type
        when (resource.type) {
            TYPE_NOTE -> binding.rbNote.isChecked = true
            TYPE_IMAGE -> binding.rbImage.isChecked = true
            TYPE_DOCUMENT -> binding.rbDocument.isChecked = true
            TYPE_LINK -> binding.rbLink.isChecked = true
        }
        updateResourceTypeVisibility(resource.type)

        // Load content based on type
        when (resource.type) {
            TYPE_NOTE -> binding.etNoteContent.setText(resource.description)
            TYPE_IMAGE -> {
                if (resource.filePath.isNotEmpty()) {
                    loadImageFromPath(resource.filePath)
                }
            }
            TYPE_DOCUMENT -> {
                if (resource.filePath.isNotEmpty()) {
                    binding.tvDocumentName.text = getFileNameFromPath(resource.filePath)
                }
            }
            TYPE_LINK -> binding.etLink.setText(resource.filePath)
        }
    }

    private fun updateResourceTypeVisibility(type: Int) {
        // Hide all first
        binding.layoutNoteContent.visibility = View.GONE
        binding.layoutImageContent.visibility = View.GONE
        binding.layoutDocumentContent.visibility = View.GONE
        binding.layoutLinkContent.visibility = View.GONE

        // Show appropriate layout based on type
        when (type) {
            TYPE_NOTE -> binding.layoutNoteContent.visibility = View.VISIBLE
            TYPE_IMAGE -> binding.layoutImageContent.visibility = View.VISIBLE
            TYPE_DOCUMENT -> binding.layoutDocumentContent.visibility = View.VISIBLE
            TYPE_LINK -> binding.layoutLinkContent.visibility = View.VISIBLE
        }
    }

    private fun captureImage() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        // Create the File where the photo should go
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            // Error occurred while creating the File
            Toast.makeText(requireContext(), "Error creating image file", Toast.LENGTH_SHORT).show()
            null
        }

        // Continue only if the File was successfully created
        photoFile?.also {
            val photoURI: Uri = FileProvider.getUriForFile(
                requireContext(),
                "com.anasbinrashid.studysync.fileprovider",
                it
            )
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            takePictureLauncher.launch(takePictureIntent)
        }
    }

    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    private fun loadImagePreview(uri: Uri?) {
        if (uri != null) {
            Glide.with(requireContext())
                .load(uri)
                .placeholder(R.drawable.placeholder_image)
                .into(binding.ivImagePreview)

            binding.ivImagePreview.visibility = View.VISIBLE
        }
    }

    private fun loadImageFromPath(path: String) {
        val file = File(path)
        if (file.exists()) {
            selectedImageUri = Uri.fromFile(file)
            loadImagePreview(selectedImageUri)
        } else {
            // If local file doesn't exist, try loading from Firebase Storage
            storage.reference.child(path).downloadUrl
                .addOnSuccessListener { uri ->
                    loadImagePreview(uri)
                }
                .addOnFailureListener {
                    binding.ivImagePreview.visibility = View.GONE
                }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        val contentResolver = requireContext().contentResolver
        val cursor = contentResolver.query(uri, null, null, null, null)

        return cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    return it.getString(displayNameIndex)
                }
            }
            null
        } ?: uri.lastPathSegment
    }

    private fun getFileNameFromPath(path: String): String {
        return path.substringAfterLast('/')
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        // Reset errors
        binding.tilTitle.error = null
        binding.tilCourse.error = null
        binding.tilLink.error = null

        // Validate title
        if (binding.etTitle.text.toString().trim().isEmpty()) {
            binding.tilTitle.error = "Title is required"
            isValid = false
        }

        // Validate course
        if (selectedCourse == null) {
            binding.tilCourse.error = "Please select a course"
            isValid = false
        }

        // Validate content based on type
        val selectedType = when {
            binding.rbNote.isChecked -> TYPE_NOTE
            binding.rbImage.isChecked -> TYPE_IMAGE
            binding.rbDocument.isChecked -> TYPE_DOCUMENT
            else -> TYPE_LINK
        }

        when (selectedType) {
            TYPE_NOTE -> {
                if (binding.etNoteContent.text.toString().trim().isEmpty()) {
                    Toast.makeText(requireContext(), "Note content is required", Toast.LENGTH_SHORT).show()
                    isValid = false
                }
            }
            TYPE_IMAGE -> {
                if (selectedImageUri == null && currentResource?.filePath?.isEmpty() != false) {
                    Toast.makeText(requireContext(), "Please select an image", Toast.LENGTH_SHORT).show()
                    isValid = false
                }
            }
            TYPE_DOCUMENT -> {
                if (selectedDocumentUri == null && currentResource?.filePath?.isEmpty() != false) {
                    Toast.makeText(requireContext(), "Please select a document", Toast.LENGTH_SHORT).show()
                    isValid = false
                }
            }
            TYPE_LINK -> {
                if (binding.etLink.text.toString().trim().isEmpty()) {
                    binding.tilLink.error = "Link is required"
                    isValid = false
                }
            }
        }

        return isValid
    }

    private fun saveResource() {
        val userId = auth.currentUser?.uid ?: return
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        // Get selected type
        val selectedType = when {
            binding.rbNote.isChecked -> TYPE_NOTE
            binding.rbImage.isChecked -> TYPE_IMAGE
            binding.rbDocument.isChecked -> TYPE_DOCUMENT
            else -> TYPE_LINK
        }

        // Start creating resource with basic info
        val resourceBuilder = Resource(
            id = if (isEditMode && currentResource != null) currentResource!!.id else UUID.randomUUID().toString(),
            userId = userId,
            title = title,
            description = description,
            courseId = selectedCourse?.id ?: "",
            courseName = selectedCourse?.name ?: "",
            type = selectedType,
            dateAdded = if (isEditMode && currentResource != null) currentResource!!.dateAdded else Date(),
            lastModified = Date(),
            isSynced = false
        )

        // Handle content based on type
        when (selectedType) {
            TYPE_NOTE -> {
                val noteContent = binding.etNoteContent.text.toString().trim()
                completeResourceSave(resourceBuilder.copy(
                    description = noteContent
                ))
            }
            TYPE_IMAGE -> handleImageResourceSave(resourceBuilder)
            TYPE_DOCUMENT -> handleDocumentResourceSave(resourceBuilder)
            TYPE_LINK -> {
                val link = binding.etLink.text.toString().trim()
                completeResourceSave(resourceBuilder.copy(
                    filePath = link
                ))
            }
        }
    }

    private fun handleImageResourceSave(resource: Resource) {
        if (selectedImageUri != null) {
            showLoading(true)

            // Save image to local storage
            val localFilePath = saveImageToLocalStorage(selectedImageUri!!)

            if (localFilePath.isNotEmpty()) {
                // Generate thumbnail
                val thumbnailPath = generateThumbnail(localFilePath)

                // Save resource with local file path
                completeResourceSave(resource.copy(
                    filePath = localFilePath,
                    thumbnailPath = thumbnailPath
                ))
            } else {
                showLoading(false)
                Toast.makeText(requireContext(), "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        } else if (isEditMode && currentResource != null && currentResource!!.filePath.isNotEmpty()) {
            // Use existing file path from edit mode
            completeResourceSave(resource.copy(
                filePath = currentResource!!.filePath,
                thumbnailPath = currentResource!!.thumbnailPath
            ))
        } else {
            showLoading(false)
            Toast.makeText(requireContext(), "No image selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleDocumentResourceSave(resource: Resource) {
        if (selectedDocumentUri != null) {
            showLoading(true)

            // Save document to local storage
            val localFilePath = saveDocumentToLocalStorage(selectedDocumentUri!!)

            if (localFilePath.isNotEmpty()) {
                // Save resource with local file path
                completeResourceSave(resource.copy(
                    filePath = localFilePath
                ))
            } else {
                showLoading(false)
                Toast.makeText(requireContext(), "Failed to save document", Toast.LENGTH_SHORT).show()
            }
        } else if (isEditMode && currentResource != null && currentResource!!.filePath.isNotEmpty()) {
            // Use existing file path from edit mode
            completeResourceSave(resource.copy(
                filePath = currentResource!!.filePath
            ))
        } else {
            showLoading(false)
            Toast.makeText(requireContext(), "No document selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToLocalStorage(uri: Uri): String {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val fileName = "img_${System.currentTimeMillis()}.jpg"
            val outputDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val outputFile = File(outputDir, fileName)

            inputStream?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun saveDocumentToLocalStorage(uri: Uri): String {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val fileName = getFileNameFromUri(uri) ?: "doc_${System.currentTimeMillis()}"
            val outputDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val outputFile = File(outputDir, fileName)

            inputStream?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun generateThumbnail(imagePath: String): String {
        return try {
            // Load original bitmap but sample it down to reduce memory consumption
            val options = BitmapFactory.Options().apply {
                inSampleSize = 4 // Scale down by factor of 4
            }
            val originalBitmap = BitmapFactory.decodeFile(imagePath, options)

            // Create thumbnail
            val thumbnailWidth = 200
            val thumbnailHeight = (originalBitmap.height * (thumbnailWidth.toFloat() / originalBitmap.width)).toInt()
            val thumbnailBitmap = Bitmap.createScaledBitmap(originalBitmap, thumbnailWidth, thumbnailHeight, false)

            // Save thumbnail
            val fileName = "thumb_${imagePath.substringAfterLast('/')}"
            val outputDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val outputFile = File(outputDir, fileName)

            FileOutputStream(outputFile).use { output ->
                thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }

            // Clean up
            originalBitmap.recycle()
            thumbnailBitmap.recycle()

            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun completeResourceSave(resource: Resource) {
        // Add tags
        val finalResource = resource.copy(tags = tagsList.toList())

        // Save to local database (SQLite)
        if (isEditMode) {
            dbHelper.updateResource(finalResource)
        } else {
            dbHelper.addResource(finalResource)
        }

        // Save to MySQL/phpMyAdmin through API
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resourceRepository = ResourceRepository(requireContext())
                val result = if (isEditMode) {
                    resourceRepository.updateResource(finalResource)
                } else {
                    resourceRepository.createResource(finalResource)
                }

                result.fold(
                    onSuccess = {
                        // Mark as synced in local database
                        dbHelper.markResourceAsSynced(finalResource.id)
                    },
                    onFailure = { e ->
                        Log.e("AddEditResource", "Failed to save to MySQL: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e("AddEditResource", "Error saving to MySQL: ${e.message}")
            }
        }

        // Upload to Firebase
        uploadToFirebase(finalResource)
    }

    private fun uploadToFirebase(resource: Resource) {
        // Upload resource data to Firestore
        db.collection("resources")
            .document(resource.id)
            .set(resource)
            .addOnSuccessListener {
                // Mark as synced
                dbHelper.markResourceAsSynced(resource.id)
                showLoading(false)

                // Show notification for resource update/addition
                val notificationHelper = NotificationHelper(requireContext())
                val action = if (isEditMode) "Updated" else "Added"
                notificationHelper.showLocalNotification(
                    resource.id.hashCode(),
                    "Resource $action: ${resource.title}",
                    "${resource.courseName} - ${getResourceTypeString(resource.type)}"
                )

                Toast.makeText(
                    requireContext(),
                    if (isEditMode) "Resource updated successfully" else "Resource added successfully",
                    Toast.LENGTH_SHORT
                ).show()

                findNavController().navigateUp()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(
                    requireContext(),
                    if (isEditMode) "Resource updated locally" else "Resource added locally",
                    Toast.LENGTH_SHORT
                ).show()

                findNavController().navigateUp()
            }
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Resource")
            .setMessage("Are you sure you want to delete this resource?")
            .setPositiveButton("Delete") { _, _ ->
                deleteResource()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteResource() {
        if (currentResource != null) {
            val resourceId = currentResource!!.id
            showLoading(true)

            // Delete from local database (SQLite)
            dbHelper.deleteResource(resourceId)

            // Delete from MySQL/phpMyAdmin through API
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val resourceRepository = ResourceRepository(requireContext())
                    currentResource?.let { resource ->
                        resourceRepository.deleteResource(resource)
                    }
                } catch (e: Exception) {
                    Log.e("AddEditResource", "Error deleting from MySQL: ${e.message}")
                }
            }

            // Delete from Firestore
            db.collection("resources")
                .document(resourceId)
                .delete()
                .addOnSuccessListener {
                    showLoading(false)
                    Toast.makeText(requireContext(), "Resource deleted successfully", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    // Even if Firestore deletion fails, we've deleted from other databases
                    Toast.makeText(requireContext(), "Resource deleted from local and MySQL databases", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
        }
    }

    private fun getResourceTypeString(type: Int): String {
        return when (type) {
            TYPE_NOTE -> "Note"
            TYPE_IMAGE -> "Image"
            TYPE_DOCUMENT -> "Document"
            TYPE_LINK -> "Link"
            else -> "Unknown"
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !isLoading
        binding.btnDelete.isEnabled = !isLoading
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