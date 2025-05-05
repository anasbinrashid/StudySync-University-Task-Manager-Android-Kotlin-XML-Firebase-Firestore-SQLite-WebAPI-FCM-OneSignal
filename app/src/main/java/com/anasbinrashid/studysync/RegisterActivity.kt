package com.anasbinrashid.studysync

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.anasbinrashid.studysync.databinding.ActivityRegisterBinding
import com.anasbinrashid.studysync.model.User
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var realtimeDb: FirebaseDatabase
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase and SQLite
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        realtimeDb = FirebaseDatabase.getInstance()
        dbHelper = DatabaseHelper(this)

        setupSemesterDropdown()
        setupClickListeners()
    }

    private fun setupSemesterDropdown() {
        val semesters = arrayOf(
            "1st Semester", "2nd Semester", "3rd Semester", "4th Semester",
            "5th Semester", "6th Semester", "7th Semester", "8th Semester"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, semesters)
        binding.actCurrentSemester.setAdapter(adapter)
    }

    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()
            val university = binding.etUniversity.text.toString().trim()
            val currentSemester = binding.actCurrentSemester.text.toString().trim()

            if (validateInputs(fullName, email, password, confirmPassword, university, currentSemester)) {
                registerUser(fullName, email, password, university, currentSemester)
            }
        }
    }

    private fun validateInputs(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String,
        university: String,
        currentSemester: String
    ): Boolean {
        var isValid = true

        // Reset errors
        binding.tilFullName.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
        binding.tilUniversity.error = null
        binding.tilCurrentSemester.error = null

        if (fullName.isEmpty()) {
            binding.tilFullName.error = "Full name is required"
            isValid = false
        }

        if (email.isEmpty()) {
            binding.tilEmail.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Please enter a valid email"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = "Password must be at least 6 characters"
            isValid = false
        }

        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = "Please confirm your password"
            isValid = false
        } else if (password != confirmPassword) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            isValid = false
        }

        if (university.isEmpty()) {
            binding.tilUniversity.error = "University is required"
            isValid = false
        }

        if (currentSemester.isEmpty()) {
            binding.tilCurrentSemester.error = "Current semester is required"
            isValid = false
        }

        return isValid
    }

    private fun registerUser(
        fullName: String,
        email: String,
        password: String,
        university: String,
        currentSemester: String
    ) {
        showLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: ""

                    val user = User(
                        id = userId,
                        name = fullName,
                        email = email,
                        university = university,
                        currentSemester = currentSemester
                    )

                    // Save user data to all 3 storages
                    saveUserToFirestore(user)
                    saveUserToRealtimeDatabase(user)
                    saveUserToLocalDatabase(user)
                } else {
                    showLoading(false)
                    Toast.makeText(
                        this,
                        "Registration failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun saveUserToFirestore(user: User) {
        firestore.collection("users")
            .document(user.id)
            .set(user)
            .addOnSuccessListener {
                // Firestore success
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save to Firestore", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserToRealtimeDatabase(user: User) {
        realtimeDb.reference.child("users").child(user.id).setValue(user)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Failed to save to Realtime DB", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserToLocalDatabase(user: User) {
        dbHelper.addUser(user)
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !loading
    }
}
