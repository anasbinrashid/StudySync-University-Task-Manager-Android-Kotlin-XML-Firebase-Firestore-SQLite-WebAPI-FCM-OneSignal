package com.anasbinrashid.studysync.model

import java.util.Date

data class Resource(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val type: Int = 0,
    val filePath: String = "",
    val tags: List<String> = emptyList(),
    val dateAdded: Date = Date(),
    val lastModified: Date = Date(),
    val thumbnailPath: String = "",
    val isSynced: Boolean = false
) {
    // Computed properties for API serialization
    val user_id: String get() = userId
    val course_id: String get() = courseId
    val course_name: String get() = courseName
    val file_path: String get() = filePath
    val date_added: Long get() = dateAdded.time
    val last_updated: Long get() = lastModified.time
    val thumbnail_path: String get() = thumbnailPath
}