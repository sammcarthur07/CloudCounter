package com.vibecode.cloudcounter

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
        private const val KEY_LAST_TURN_SMOKER_ID = "last_turn_smoker_id"
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
     * Check if this specific profile instance should skip notifications due to being foreground
     * for the specific turn user. This ensures that when a secure folder instance is foreground,
     * it can still show notifications for the main profile user's turns.
     */
    fun shouldSkipNotificationForUser(currentTurnUserId: String): Boolean {
        // Get the current Firebase user for this profile instance
        val thisInstanceUserId = FirebaseAuth.getInstance().currentUser?.uid ?: getAndroidDeviceId()
        
        // Check if this profile instance is in foreground
        val isThisInstanceForeground = isThisInstanceInForeground()
        
        Log.d(TAG, "shouldSkipNotificationForUser - Turn user: $currentTurnUserId, This instance user: $thisInstanceUserId, This instance foreground: $isThisInstanceForeground")
        
        // Only skip notifications if:
        // 1. This profile instance is in foreground AND
        // 2. The turn belongs to the same user as this profile instance
        val shouldSkip = isThisInstanceForeground && (currentTurnUserId == thisInstanceUserId)
        
        Log.d(TAG, "shouldSkipNotificationForUser result: $shouldSkip")
        return shouldSkip
    }
    
    /**
     * Check if this specific profile instance is in foreground
     */
    private fun isThisInstanceInForeground(): Boolean {
        (context.applicationContext as? CloudCounterApplication)?.let { app ->
            val appForeground = app.isInForeground()
            Log.d(TAG, "App lifecycle callback check: foreground=$appForeground")
            return appForeground
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // Get running app processes
        val appProcesses = activityManager.runningAppProcesses
        if (appProcesses.isNullOrEmpty()) {
            Log.d(TAG, "No running app processes found")
            return false
        }
        
        val packageName = context.packageName
        val targetUid = context.applicationInfo?.uid ?: -1

        // Find the process that belongs to this user profile + package
        for (process in appProcesses) {
            if (process.processName == packageName) {
                if (process.uid != targetUid) {
                    Log.d(TAG, "Skipping process for different UID: ${process.uid}")
                    continue
                }
                // Check if the app is truly in foreground (visible to user)
                // IMPORTANCE_FOREGROUND = 100 (has visible activity)
                // IMPORTANCE_FOREGROUND_SERVICE = 125 (has foreground service but no visible activity)
                // IMPORTANCE_VISIBLE = 200 (visible but not in foreground)

                Log.d(TAG, "Foreground check - Package: $packageName, UID: ${process.uid}, Importance: ${process.importance}")
                
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
     * Check if app is in foreground (legacy method for backward compatibility)
     */
    fun isAppInForeground(): Boolean {
        return isThisInstanceInForeground()
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
                val appPrefs = context.getSharedPreferences("sesh", Context.MODE_PRIVATE)
                val notificationsEnabled = appPrefs.getBoolean("notifications_enabled", true)
                
                if (!notificationsEnabled) {
                    Log.d(TAG, "Notifications disabled in settings, skipping turn notification")
                    return@launch
                }
                
                // Get the actual signed-in user for this app instance
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: getAndroidDeviceId()
                
                // Check if we should force notifications (for testing)
                val forceNotifications = prefs.getBoolean("force_turn_notifications", false)
                
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
                Log.d(TAG, "SharedSmokers map size: ${roomData.sharedSmokers?.size ?: 0}")
                
                roomData.sharedSmokers?.forEach { (smokerId, smokerData) ->
                    val data = smokerData as? Map<*, *>
                    val name = data?.get("name")
                    val cloudUserId = data?.get("cloudUserId")
                    val isCloudSmoker = data?.get("isCloudSmoker")
                    Log.d(TAG, "Smoker in room: ID=$smokerId, name=$name, cloudUserId=$cloudUserId, isCloud=$isCloudSmoker")
                }
                
                // Use the actual active smoker from room data (includes manual changes)
                val totalHits = roomData.activities.size
                val currentTurnSmokerId = roomData.safeActiveSmokerId()
                if (currentTurnSmokerId == null) {
                    Log.d(TAG, "Could not determine current turn smoker - activeSmokerId is null")
                    return@launch
                }
                
                Log.d(TAG, "Current turn belongs to: $currentTurnSmokerId (from activeSmokerId)")
                
                // CRITICAL: Never show notifications for local smokers
                // Local smokers have IDs that start with "local_"
                val isLocalSmokerTurn = currentTurnSmokerId.startsWith("local_")
                if (isLocalSmokerTurn) {
                    Log.d(TAG, "Skipping notification - turn belongs to local smoker: $currentTurnSmokerId")
                    return@launch
                }
                
                // For cloud users, the smoker ID is the Firebase UID
                val turnUserId = getTurnUserId(roomData, currentTurnSmokerId)
                
                // Track the last user we notified about to avoid duplicate notifications
                val lastNotifiedTurnUserKey = "last_notified_turn_user_${currentShareCode}_${currentUserId}"
                val lastNotifiedTurnUser = prefs.getString(lastNotifiedTurnUserKey, "")
                
                // Check if we should skip notification based on foreground state and user
                val shouldSkipForForeground = !forceNotifications && shouldSkipNotificationForUser(turnUserId)
                
                // Check if it's the current user's turn
                // For cloud users, compare with Firebase UID
                val isUserTurn = turnUserId == currentUserId
                
                // Debug logging for turn detection
                Log.d(TAG, "Turn check - Current Firebase user: $currentUserId, Current user smoker ID: $currentUserSmokerId")
                Log.d(TAG, "Turn smoker ID: $currentTurnSmokerId, Turn user ID: $turnUserId, Is user turn: $isUserTurn")
                Log.d(TAG, "Active participants: $activeParticipants")
                Log.d(TAG, "Total activities: $totalHits")
                Log.d(TAG, "Should skip for foreground: $shouldSkipForForeground")
                
                if (shouldSkipForForeground) {
                    Log.d(TAG, "Skipping notification - this profile instance is foreground for turn user: $turnUserId")
                    // Clear the last notified user when it's our turn so we can notify again when turn changes
                    prefs.edit()
                        .putString(lastNotifiedTurnUserKey, "")
                        .apply()
                    return@launch
                }
                
                // Check if this is a different user's turn than we last notified about
                val isDifferentUserTurn = turnUserId != lastNotifiedTurnUser
                
                Log.d(TAG, "Notification turn check - Current turn user: $turnUserId")
                Log.d(TAG, "Last notified turn user: $lastNotifiedTurnUser, Is different user: $isDifferentUserTurn")
                
                // Show notification if the turn has changed to a different user
                if (isDifferentUserTurn) {
                    // Someone's turn with new activities - show notification
                    Log.d(TAG, "Turn notification triggered! Turn belongs to: $turnUserId")
                    Log.d(TAG, "Activity count: $totalHits, Current instance user: $currentUserId")
                    
                    // Update last notified turn user
                    prefs.edit()
                        .putString(lastNotifiedTurnUserKey, turnUserId)
                        .apply()
                    
                    // Get user's smoker name - look up by the turn smoker ID
                    // For cloud smokers, the currentTurnSmokerId IS their Firebase UID, which is the key
                    // For local smokers (already filtered out), the key would be "local_" + uid
                    val userSmokerName = if (roomData.sharedSmokers.isNullOrEmpty()) {
                        Log.d(TAG, "Warning: sharedSmokers map is empty, cannot look up smoker name")
                        "User"
                    } else {
                        roomData.sharedSmokers?.get(currentTurnSmokerId)?.let { smokerData ->
                            (smokerData as? Map<*, *>)?.get("name") as? String
                        } ?: run {
                            Log.d(TAG, "Warning: Could not find smoker in sharedSmokers with ID: $currentTurnSmokerId")
                            Log.d(TAG, "Available smoker IDs in room: ${roomData.sharedSmokers?.keys}")
                            "User"
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
                } else {
                    Log.d(TAG, "No notification needed - same user's turn continues: $turnUserId")
                    // Still update the turn smoker tracker even if we don't notify
                    prefs.edit()
                        .putString("turn_user_smoker_id", currentTurnSmokerId)
                        .apply()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing room update for turn notification", e)
            }
        }
    }
    
    /**
     * Get the user ID that corresponds to the current turn smoker ID
     */
     private fun getTurnUserId(roomData: RoomData, turnSmokerId: String): String {
        // For cloud users, the turnSmokerId IS the Firebase UID (user ID)
        // Local smokers start with "local_" and should have been filtered out already
        
        // If it doesn't start with "local_", it's a Firebase UID
        if (!turnSmokerId.startsWith("local_")) {
            return turnSmokerId
        }
        
        // This shouldn't happen as local smokers are filtered earlier,
        // but return the ID anyway as a fallback
        return turnSmokerId
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
