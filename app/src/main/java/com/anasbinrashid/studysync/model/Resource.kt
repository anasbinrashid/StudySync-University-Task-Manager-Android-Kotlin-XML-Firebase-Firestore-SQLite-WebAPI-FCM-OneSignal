package com.anasbinrashid.studysync.model

import java.util.Date

data class Resource(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val type: Int = 0, // 0: Note, 1: Image, 2: Document, 3: Link
    val filePath: String = "", // Local file path or URL
    val tags: List<String> = listOf(),
    val dateAdded: Date = Date(),
    val lastModified: Date = Date(),
    val isSynced: Boolean = false,
    val thumbnailPath: String = "" // For images or documents with thumbnails
)