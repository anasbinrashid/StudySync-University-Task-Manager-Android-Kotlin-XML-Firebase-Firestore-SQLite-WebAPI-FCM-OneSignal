package com.anasbinrashid.studysync.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.firestore.FirebaseFirestore
import com.anasbinrashid.studysync.model.Resource
import com.anasbinrashid.studysync.model.Task
import com.anasbinrashid.studysync.model.Course

class SyncManager(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val dbHelper = DatabaseHelper(context)

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun syncResources() {
        if (!isNetworkAvailable()) return

        // Get unsynced resources
        val unsyncedResources = dbHelper.getUnsyncedResources()

        // Upload to Firestore
        for (resource in unsyncedResources) {
            db.collection("resources")
                .document(resource.id)
                .set(resource)
                .addOnSuccessListener {
                    dbHelper.markResourceAsSynced(resource.id)
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
        }

        // Download from Firestore
        db.collection("resources")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val firestoreResource = document.toObject(Resource::class.java)
                    val localResource = dbHelper.getResourceById(firestoreResource.id)

                    if (localResource == null || firestoreResource.lastModified > localResource.lastModified) {
                        dbHelper.addResource(firestoreResource)
                    }
                }
            }
    }

    private fun syncTasks() {
        if (!isNetworkAvailable()) return

        // Get unsynced tasks
        val unsyncedTasks = dbHelper.getUnsyncedTasks()

        // Upload to Firestore
        for (task in unsyncedTasks) {
            db.collection("tasks")
                .document(task.id)
                .set(task)
                .addOnSuccessListener {
                    dbHelper.markTaskAsSynced(task.id)
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
        }

        // Download from Firestore
        db.collection("tasks")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val firestoreTask = document.toObject(Task::class.java)
                    val localTask = dbHelper.getTaskById(firestoreTask.id)

                    if (localTask == null || firestoreTask.lastUpdated > localTask.lastUpdated) {
                        dbHelper.addTask(firestoreTask)
                    }
                }
            }
    }

    private fun syncCourses() {
        if (!isNetworkAvailable()) return

        // Get unsynced courses
        val unsyncedCourses = dbHelper.getUnsyncedCourses()

        // Upload to Firestore
        for (course in unsyncedCourses) {
            db.collection("courses")
                .document(course.id)
                .set(course)
                .addOnSuccessListener {
                    dbHelper.markCourseAsSynced(course.id)
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
        }

        // Download from Firestore
        db.collection("courses")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val firestoreCourse = document.toObject(Course::class.java)
                    val localCourse = dbHelper.getCourseById(firestoreCourse.id)

                    if (localCourse == null) {
                        dbHelper.addCourse(firestoreCourse)
                    }
                }
            }
    }

    fun syncAll() {
        syncResources()
        syncTasks()
        syncCourses()
    }
} 