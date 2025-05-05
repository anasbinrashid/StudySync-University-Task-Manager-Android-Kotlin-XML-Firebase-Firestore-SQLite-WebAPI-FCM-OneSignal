package com.anasbinrashid.studysync.model

data class Course(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val code: String = "",
    val instructorName: String = "",
    val instructorEmail: String = "",
    val room: String = "",
    val dayOfWeek: List<Int> = listOf(), // 0: Sunday, 1: Monday, ... 6: Saturday
    val startTime: String = "",
    val endTime: String = "",
    val semester: String = "",
    val creditHours: Int = 0,
    val color: Int = 0,  // Color representation for UI
    val isSynced: Boolean = false
)