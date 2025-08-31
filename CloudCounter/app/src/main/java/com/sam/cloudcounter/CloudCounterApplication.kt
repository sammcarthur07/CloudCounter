package com.sam.cloudcounter

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore

class CloudCounterApplication : Application() {
    // Database instance
    val database by lazy { AppDatabase.getDatabase(this) }

    // Repository with all required DAOs including stashDao
    val repository by lazy {
        ActivityRepository(
            database.activityLogDao(),
            database.smokerDao(),
            database.sessionSummaryDao(),
            database.stashDao() // Added the missing stashDao parameter
        )
    }

    // Auth manager for Firebase authentication
    val authManager by lazy {
        FirebaseAuthManager(this)
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