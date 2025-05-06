package com.anasbinrashid.studysync.model

import java.util.Date

data class Task(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val courseId: String = "",
    val courseName: String = "",
    var dueDate: Date = Date(),
    val priority: Int = 0,  // 0: Low, 1: Medium, 2: High
    val status: Int = 0,    // 0: Not Started, 1: In Progress, 2: Completed
    val type: Int = 0,      // 0: Assignment, 1: Project, 2: Exam, 3: Reading, etc.
    val reminderSet: Boolean = false,
    val grade: Float = 0f,
    val isSynced: Boolean = false,
    val lastUpdated: Date = Date()
)