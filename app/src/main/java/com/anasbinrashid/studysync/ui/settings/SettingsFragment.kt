package com.anasbinrashid.studysync.ui.settings

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.anasbinrashid.studysync.LoginActivity
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.DialogEditTextBinding
import com.anasbinrashid.studysync.databinding.FragmentSettingsBinding
import com.anasbinrashid.studysync.model.User
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import java.util.Random

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var sharedPreferences: SharedPreferences

    private var currentUser: User? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize local database
        dbHelper = DatabaseHelper(requireContext())

        // Initialize shared preferences
        sharedPreferences = requireContext().getSharedPreferences(
            "app_preferences",
            Context.MODE_PRIVATE
        )

        setupSemesterDropdown()
        loadUserData()
        setupButtons()
        setupSwitches()
    }

    private fun setupSemesterDropdown() {
        val semesters = arrayOf(
            "1st Semester", "2nd Semester", "3rd Semester", "4th Semester",
            "5th Semester", "6th Semester", "7th Semester", "8th Semester"
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, semesters)
        binding.actSemester.setAdapter(adapter)

        binding.actSemester.setOnItemClickListener { _, _, position, _ ->
            val selectedSemester = semesters[position]
            updateSemester(selectedSemester)
        }
    }

    private fun loadUserData() {
        val currentFirebaseUser = auth.currentUser

        if (currentFirebaseUser != null) {
            val userId = currentFirebaseUser.uid

            // Get user from local database
            currentUser = dbHelper.getUser(userId)

            if (currentUser != null) {
                updateUI(currentUser!!)
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
                            updateUI(currentUser!!)
                        } else {
                            Toast.makeText(requireContext(), "User data not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            navigateToLogin()
        }
    }

    private fun updateUI(user: User) {
        // Set user details
        binding.tvName.text = user.name
        binding.tvEmail.text = user.email
        binding.tvUniversity.text = user.university

        // Set current semester in dropdown
        if (user.currentSemester.isNotEmpty()) {
            binding.actSemester.setText(user.currentSemester, false)
        }

        // Load app preferences
        loadAppPreferences()
    }

    private fun setupButtons() {
        // Edit name button
        binding.btnEditName.setOnClickListener {
            showEditDialog("Edit Name", currentUser?.name ?: "") { newName ->
                updateName(newName)
            }
        }

        // Edit university button
        binding.btnEditUniversity.setOnClickListener {
            showEditDialog("Edit University", currentUser?.university ?: "") { newUniversity ->
                updateUniversity(newUniversity)
            }
        }

        // Change password button
        binding.btnChangePassword.setOnClickListener {
            showResetPasswordDialog()
        }

        // Feedback button
        binding.btnFeedback.setOnClickListener {
            sendFeedback()
        }

        // Privacy policy button
        binding.btnPrivacyPolicy.setOnClickListener {
            showPrivacyPolicy()
        }
    }

    private fun setupSwitches() {
        // Notifications switch
        binding.switchNotifications.isChecked = sharedPreferences.getBoolean("notifications_enabled", true)
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("notifications_enabled", isChecked).apply()
        }

//        // Dark mode switch
//        binding.switchDarkMode.isChecked = sharedPreferences.getBoolean("dark_mode_enabled", false)
//        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
//            sharedPreferences.edit().putBoolean("dark_mode_enabled", isChecked).apply()
//            applyDarkMode(isChecked)
//        }

        // Sync switch
        binding.switchSync.isChecked = sharedPreferences.getBoolean("sync_enabled", true)
        binding.switchSync.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("sync_enabled", isChecked).apply()

            if (isChecked) {
                // Try to sync immediately when enabled
                syncWithCloud()
            }
        }
    }

    private fun loadAppPreferences() {
        // Set app version
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.tvVersion.text = packageInfo.versionName
        } catch (e: Exception) {
            binding.tvVersion.text = "1.0.0"
        }
    }

    private fun showEditDialog(title: String, currentValue: String, onSave: (String) -> Unit) {
        val dialogBinding = DialogEditTextBinding.inflate(layoutInflater)
        dialogBinding.tilEditText.hint = title
        dialogBinding.etEditText.setText(currentValue)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val newValue = dialogBinding.etEditText.text.toString().trim()
                if (newValue.isNotEmpty()) {
                    onSave(newValue)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateName(newName: String) {
        if (currentUser != null && auth.currentUser != null) {
            val updatedUser = currentUser!!.copy(name = newName)

            // Update in local database
            dbHelper.updateUser(updatedUser)
            currentUser = updatedUser
            binding.tvName.text = newName
            Toast.makeText(requireContext(), "Name updated successfully", Toast.LENGTH_SHORT).show()

            // Sync with cloud if enabled
            if (sharedPreferences.getBoolean("sync_enabled", true)) {
                syncUserDataToCloud(updatedUser)
            }
        }
    }

    private fun updateUniversity(newUniversity: String) {
        if (currentUser != null && auth.currentUser != null) {
            val updatedUser = currentUser!!.copy(university = newUniversity)

            // Update in local database
            dbHelper.updateUser(updatedUser)
            currentUser = updatedUser
            binding.tvUniversity.text = newUniversity
            Toast.makeText(requireContext(), "University updated successfully", Toast.LENGTH_SHORT).show()

            // Sync with cloud if enabled
            if (sharedPreferences.getBoolean("sync_enabled", true)) {
                syncUserDataToCloud(updatedUser)
            }
        }
    }

    private fun updateSemester(newSemester: String) {
        if (currentUser != null && auth.currentUser != null) {
            val updatedUser = currentUser!!.copy(currentSemester = newSemester)

            // Update in local database
            dbHelper.updateUser(updatedUser)
            currentUser = updatedUser
            Toast.makeText(requireContext(), "Semester updated successfully", Toast.LENGTH_SHORT).show()

            // Sync with cloud if enabled
            if (sharedPreferences.getBoolean("sync_enabled", true)) {
                syncUserDataToCloud(updatedUser)
            }
        }
    }

    private fun syncUserDataToCloud(user: User) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection. Changes will sync when online.", Toast.LENGTH_SHORT).show()
            return
        }

        // Update in Firestore
        db.collection("users")
            .document(user.id)
            .update(
                mapOf(
                    "name" to user.name,
                    "university" to user.university,
                    "currentSemester" to user.currentSemester
                )
            )
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Synced with cloud", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to sync with cloud: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun syncWithCloud() {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection. Will sync when online.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentFirebaseUser = auth.currentUser
        if (currentFirebaseUser != null && currentUser != null) {
            // Get all local data that needs to be synced
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    // Here you can add additional sync operations like tasks, schedules, etc.
                    // For now, we'll just sync user data
                }

                // Finally, sync user data
                syncUserDataToCloud(currentUser!!)
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showResetPasswordDialog() {
        val email = auth.currentUser?.email ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset Password")
            .setMessage("Send password reset email to $email?")
            .setPositiveButton("Send") { _, _ ->
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(
                                requireContext(),
                                "Password reset email sent to $email",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Failed to send reset email: ${task.exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendFeedback() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("i220907@nu.edu.pk", "i221241@nu.edu.pk"))
            putExtra(Intent.EXTRA_SUBJECT, "Feedback for University Task Manager App")
        }

        try {
            startActivity(Intent.createChooser(intent, "Send feedback via..."))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPrivacyPolicy() {
        // Random privacy policy message
        val privacyPolicies = arrayOf(
            "StudySync collects minimal data necessary for app functionality.",
            "Your privacy matters to us. StudySync stores your data securely.",
            "We collect data solely for app functionality and improvement.",
            "Your personal information is encrypted and securely stored."
        )

        val randomPolicy = privacyPolicies[Random().nextInt(privacyPolicies.size)]
        Toast.makeText(requireContext(), randomPolicy, Toast.LENGTH_LONG).show()
    }

    private fun navigateToLogin() {
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        coroutineScope.cancel()
        _binding = null
    }
}