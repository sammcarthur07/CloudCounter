package com.vibecode.cloudcounter

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore

class CloudCounterApplication : Application() {
    // Database instance
    val database by lazy { AppDatabase.getDatabase(this) }

    // Auth manager for Firebase authentication (initialized first since repository depends on it)
    val authManager by lazy {
        FirebaseAuthManager(this)
    }

    // Repository with all required DAOs including stashDao and authManager
    val repository by lazy {
        ActivityRepository(
            database.activityLogDao(),
            database.smokerDao(),
            database.sessionSummaryDao(),
            database.stashDao(), // Added the missing stashDao parameter
            authManager, // Pass authManager to repository for cloud operations
            this // Pass context for SharedPreferences access
        )
    }

    // Cloud sync service for Firestore synchronization
    val cloudSyncService by lazy {
        CloudSyncService(
            firestore = FirebaseFirestore.getInstance(),
            repository = repository
        )
    }

    // Default smoker ID property
    var defaultSmokerId: Long = 0L

    override fun onCreate() {
        super.onCreate()
        // Any additional initialization can go here if needed
    }
}