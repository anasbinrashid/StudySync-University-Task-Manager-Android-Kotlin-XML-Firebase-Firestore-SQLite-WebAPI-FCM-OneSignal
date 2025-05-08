package com.anasbinrashid.studysync.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.anasbinrashid.studysync.util.SyncManager

class SyncService : Service() {
    private lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()
        syncManager = SyncManager(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (syncManager.isNetworkAvailable()) {
            syncManager.syncAll()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 