package com.vibecode.cloudcounter

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
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

    private var resumedActivityCount = 0

    companion object {
        private const val TAG = "CloudCounterApp"
    }

    fun isInForeground(): Boolean = resumedActivityCount > 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                resumedActivityCount++
                Log.d(TAG, "onActivityResumed: ${activity.javaClass.simpleName}, resumedCount=$resumedActivityCount")
            }
            override fun onActivityPaused(activity: Activity) {
                resumedActivityCount = (resumedActivityCount - 1).coerceAtLeast(0)
                Log.d(TAG, "onActivityPaused: ${activity.javaClass.simpleName}, resumedCount=$resumedActivityCount")
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
