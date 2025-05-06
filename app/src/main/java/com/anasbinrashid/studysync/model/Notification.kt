package com.anasbinrashid.studysync.model

data class Notification(
    val userId: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    val data: String
) 