package com.sam.cloudcounter

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages turn detection and notifications for session activities
 */
class TurnNotificationManager(
    private val context: Context,
    private val repository: ActivityRepository
) {
    companion object {
        private const val TAG = "TurnNotificationManager"
        private const val PREFS_NAME = "turn_notifications"
        private const val KEY_LAST_NOTIFIED_ACTIVITY_COUNT = "last_notified_activity_count"
        private const val KEY_LAST_ACTIVITY_TYPE = "last_activity_type"
        private const val KEY_CURRENT_USER_SMOKER_ID = "current_user_smoker_id"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val notificationHelper = NotificationHelper(context)
    
    /**
     * Get Android device ID as fallback for user identification
     */
    private fun getAndroidDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }
    
    /**
     * Check if app is in foreground
     */
    fun isAppInForeground(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
        // Get running app processes
        val appProcesses = activityManager.runningAppProcesses
        if (appProcesses.isNullOrEmpty()) {
            Log.d(TAG, "No running app processes found")
            return false
        }
        
        val packageName = context.packageName
        
        // Find our app's process
        for (process in appProcesses) {
            if (process.processName == packageName) {
                // Check if the app is truly in foreground (visible to user)
                // IMPORTANCE_FOREGROUND = 100 (has visible activity)
                // IMPORTANCE_FOREGROUND_SERVICE = 125 (has foreground service but no visible activity)
                // IMPORTANCE_VISIBLE = 200 (visible but not in foreground)
                
                Log.d(TAG, "Foreground check - Package: $packageName, Importance: ${process.importance}")
                
                // Only return true if importance is exactly FOREGROUND (100) 
                // This means the app has a visible activity in the foreground
                // It excludes FOREGROUND_SERVICE (125) which means service running but no visible activity
                if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    Log.d(TAG, "App IS in foreground (importance = 100, visible activity)")
                    return true
                } else if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE) {
                    Log.d(TAG, "App has foreground service but no visible activity (importance = 125)")
                    return false
                } else {
                    Log.d(TAG, "App NOT in foreground (importance = ${process.importance})")
                    return false
                }
            }
        }
        
        Log.d(TAG, "App process not found")
        return false
    }
    
    /**
     * Process room update and check if it's user's turn
     */
    fun processRoomUpdate(
        roomData: RoomData,
        currentUserSmokerId: String,
        currentShareCode: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check if notifications are enabled in app preferences
                val appPrefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                val notificationsEnabled = appPrefs.getBoolean("notifications_enabled", true)
                
                if (!notificationsEnabled) {
                    Log.d(TAG, "Notifications disabled in settings, skipping turn notification")
                    return@launch
                }
                
                // Get the actual signed-in user for this app instance
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: getAndroidDeviceId()
                
                // Check if we should force notifications (for testing)
                val forceNotifications = prefs.getBoolean("force_turn_notifications", false)
                
                // Don't show notifications if app is in foreground (unless forced)
                if (!forceNotifications && isAppInForeground()) {
                    Log.d(TAG, "App in foreground, skipping turn notification")
                    return@launch
                }
                
                if (forceNotifications) {
                    Log.d(TAG, "Force notifications enabled, bypassing foreground check")
                }
                
                // Get active participants
                val activeParticipants = getActiveParticipants(roomData)
                if (activeParticipants.isEmpty()) {
                    Log.d(TAG, "No active participants")
                    return@launch
                }
                
                // Log room smokers for debugging
                Log.d(TAG, "===== TURN DETECTION DEBUG =====")
                Log.d(TAG, "Room share code: $currentShareCode")
                Log.d(TAG, "Current user Firebase UID: $currentUserId")
                Log.d(TAG, "Current user smoker ID: $currentUserSmokerId")
                Log.d(TAG, "Active participants: $activeParticipants")
                
                roomData.sharedSmokers?.forEach { (smokerId, smokerData) ->
                    val data = smokerData as? Map<*, *>
                    val name = data?.get("name")
                    val cloudUserId = data?.get("cloudUserId")
                    val isCloudSmoker = data?.get("isCloudSmoker")
                    Log.d(TAG, "Smoker in room: ID=$smokerId, name=$name, cloudUserId=$cloudUserId, isCloud=$isCloudSmoker")
                }
                
                // Calculate current turn
                val totalHits = roomData.activities.size
                val currentTurnIndex = if (activeParticipants.size > 0) {
                    totalHits % activeParticipants.size
                } else {
                    0
                }
                
                Log.d(TAG, "Turn calculation: totalHits=$totalHits, participantCount=${activeParticipants.size}, turnIndex=$currentTurnIndex")
                
                // Get the smoker whose turn it is
                val currentTurnSmokerId = activeParticipants.getOrNull(currentTurnIndex)
                if (currentTurnSmokerId == null) {
                    Log.d(TAG, "Could not determine current turn smoker")
                    return@launch
                }
                
                Log.d(TAG, "Current turn belongs to: $currentTurnSmokerId")
                
                // Check if it's the current user's turn
                // For cloud users: compare Firebase UID with the smoker's cloudUserId
                // For local smokers: compare the "local_xxx" format
                val isUserTurn = if (currentTurnSmokerId.startsWith("local_")) {
                    // It's a local smoker - check if it matches the current user's local smoker
                    currentTurnSmokerId == currentUserSmokerId
                } else {
                    // It's a cloud user - compare with Firebase UID
                    currentTurnSmokerId == currentUserId
                }
                
                // Debug logging for turn detection
                Log.d(TAG, "Turn check - Current Firebase user: $currentUserId, Current user smoker ID: $currentUserSmokerId")
                Log.d(TAG, "Turn smoker ID: $currentTurnSmokerId, Is user turn: $isUserTurn")
                Log.d(TAG, "Active participants: $activeParticipants")
                Log.d(TAG, "Total activities: $totalHits, Current turn index: $currentTurnIndex")
                Log.d(TAG, "App in foreground: ${isAppInForeground()}")
                
                // Get a unique key for this user in this room
                val userRoomKey = "${KEY_LAST_NOTIFIED_ACTIVITY_COUNT}_${currentShareCode}_${currentUserId}"
                val lastNotifiedCount = prefs.getInt(userRoomKey, -1)
                
                if (isUserTurn && totalHits > lastNotifiedCount) {
                    // It's the user's turn and there are new activities
                    Log.d(TAG, "It's user's turn! Activity count: $totalHits (last notified: $lastNotifiedCount)")
                    Log.d(TAG, "Showing notification for user: $currentUserId / $currentUserSmokerId")
                    Log.d(TAG, "Room: $currentShareCode, User room key: $userRoomKey")
                    
                    // Update last notified count for this specific user and room
                    prefs.edit()
                        .putInt(userRoomKey, totalHits)
                        .apply()
                    
                    // Get user's smoker name - handle both cloud and local smokers
                    val userSmokerName = when {
                        currentTurnSmokerId.startsWith("local_") -> {
                            // For local smokers, look up directly by the smoker ID
                            roomData.sharedSmokers?.get(currentTurnSmokerId)?.let { smokerData ->
                                (smokerData as? Map<*, *>)?.get("name") as? String
                            }
                        }
                        else -> {
                            // For cloud users, find by cloudUserId
                            roomData.sharedSmokers?.entries?.firstOrNull { entry ->
                                val smokerData = entry.value as? Map<*, *>
                                val cloudUserId = smokerData?.get("cloudUserId") as? String
                                cloudUserId == currentUserId
                            }?.let { entry ->
                                val smokerData = entry.value as? Map<*, *>
                                smokerData?.get("name") as? String
                            }
                        }
                    }
                    
                    Log.d(TAG, "User smoker name for turn: $userSmokerName (smokerId: $currentTurnSmokerId)")
                    
                    // Get last activity type
                    val lastActivityType = getLastActivityType(roomData)
                    Log.d(TAG, "Last activity type: $lastActivityType")
                    
                    // Save last activity type and turn user's smoker ID
                    lastActivityType?.let {
                        prefs.edit()
                            .putString(KEY_LAST_ACTIVITY_TYPE, it.name)
                            .putString("turn_user_smoker_id", currentTurnSmokerId)
                            .apply()
                    }
                    
                    // Show notification with the correct user's name
                    notificationHelper.showTurnNotification(
                        roomCode = currentShareCode,
                        lastActivityType = lastActivityType,
                        smokerName = userSmokerName,
                        turnUserSmokerId = currentTurnSmokerId
                    )
                } else if (isUserTurn) {
                    Log.d(TAG, "It's user's turn but no new activities ($totalHits <= $lastNotifiedCount)")
                } else {
                    Log.d(TAG, "Not user's turn (turn belongs to: $currentTurnSmokerId)")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing room update for turn notification", e)
            }
        }
    }
    
    /**
     * Get active participants (not paused, not away)
     */
    private fun getActiveParticipants(roomData: RoomData): List<String> {
        // Get all activities to find unique participants
        val participantsFromActivities = roomData.activities
            .map { it.smokerId }
            .distinct()
            .sorted() // Sort for consistent order
        
        val pausedSmokers = roomData.pausedSmokers ?: emptyList()
        val awaySmokers = roomData.awayParticipants ?: emptyList()
        
        Log.d(TAG, "getActiveParticipants - All participants from activities: $participantsFromActivities")
        Log.d(TAG, "getActiveParticipants - Paused smokers: $pausedSmokers")
        Log.d(TAG, "getActiveParticipants - Away smokers: $awaySmokers")
        
        val activeParticipants = participantsFromActivities.filter { participantId ->
            val isPaused = pausedSmokers.contains(participantId)
            val isAway = awaySmokers.contains(participantId)
            val isActive = !isPaused && !isAway
            Log.d(TAG, "Participant $participantId: paused=$isPaused, away=$isAway, active=$isActive")
            isActive
        }
        
        Log.d(TAG, "getActiveParticipants - Final active list: $activeParticipants")
        return activeParticipants
    }
    
    /**
     * Get the name of the user's smoker
     */
    private fun getUserSmokerName(roomData: RoomData, userSmokerId: String): String? {
        val smokerData = roomData.sharedSmokers?.get(userSmokerId) as? Map<*, *>
        return smokerData?.get("name") as? String
    }
    
    /**
     * Get the last activity type from room activities
     */
    private fun getLastActivityType(roomData: RoomData): ActivityType? {
        val lastActivity = roomData.activities.maxByOrNull { it.timestamp }
        return lastActivity?.type?.let {
            try {
                ActivityType.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Set the current user's smoker ID for turn detection
     */
    fun setCurrentUserSmokerId(smokerId: String) {
        prefs.edit().putString(KEY_CURRENT_USER_SMOKER_ID, smokerId).apply()
    }
    
    /**
     * Save last activity type when user adds an activity
     */
    fun saveLastActivityType(type: ActivityType) {
        prefs.edit().putString(KEY_LAST_ACTIVITY_TYPE, type.name).apply()
    }
    
    /**
     * Clear all turn notification data
     */
    fun clearTurnData() {
        prefs.edit()
            .remove(KEY_LAST_NOTIFIED_ACTIVITY_COUNT)
            .remove(KEY_LAST_ACTIVITY_TYPE)
            .remove(KEY_CURRENT_USER_SMOKER_ID)
            .apply()
    }
    
    /**
     * Enable/disable force notifications (bypasses foreground check)
     */
    fun setForceNotifications(enabled: Boolean) {
        prefs.edit().putBoolean("force_turn_notifications", enabled).apply()
        Log.d(TAG, "Force notifications set to: $enabled")
    }
}