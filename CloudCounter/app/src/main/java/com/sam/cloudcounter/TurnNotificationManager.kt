package com.sam.cloudcounter

import android.app.ActivityManager
import android.content.Context
import android.util.Log
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
        private const val KEY_LAST_TURN_INDEX = "last_turn_index"
        private const val KEY_LAST_ACTIVITY_TYPE = "last_activity_type"
        private const val KEY_CURRENT_USER_SMOKER_ID = "current_user_smoker_id"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val notificationHelper = NotificationHelper(context)
    
    /**
     * Check if app is in foreground
     */
    fun isAppInForeground(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        
        for (process in appProcesses) {
            if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                process.processName == context.packageName) {
                return true
            }
        }
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
                
                // Don't show notifications if app is in foreground
                if (isAppInForeground()) {
                    Log.d(TAG, "App in foreground, skipping turn notification")
                    return@launch
                }
                
                // Get active participants
                val activeParticipants = getActiveParticipants(roomData)
                if (activeParticipants.isEmpty()) {
                    Log.d(TAG, "No active participants")
                    return@launch
                }
                
                // Calculate current turn
                val totalHits = roomData.activities.size
                val currentTurnIndex = if (activeParticipants.size > 0) {
                    totalHits % activeParticipants.size
                } else {
                    0
                }
                
                // Get the smoker whose turn it is
                val currentTurnSmokerId = activeParticipants.getOrNull(currentTurnIndex)
                if (currentTurnSmokerId == null) {
                    Log.d(TAG, "Could not determine current turn smoker")
                    return@launch
                }
                
                // Check if it's the current user's turn
                val isUserTurn = currentTurnSmokerId == currentUserSmokerId
                
                // Get last turn index to check if turn changed
                val lastTurnIndex = prefs.getInt(KEY_LAST_TURN_INDEX, -1)
                val turnChanged = currentTurnIndex != lastTurnIndex
                
                if (turnChanged) {
                    prefs.edit().putInt(KEY_LAST_TURN_INDEX, currentTurnIndex).apply()
                    
                    if (isUserTurn) {
                        Log.d(TAG, "It's user's turn! Showing notification")
                        
                        // Get user's smoker name
                        val userSmokerName = getUserSmokerName(roomData, currentUserSmokerId)
                        
                        // Get last activity type
                        val lastActivityType = getLastActivityType(roomData)
                        
                        // Save last activity type if found
                        lastActivityType?.let {
                            prefs.edit().putString(KEY_LAST_ACTIVITY_TYPE, it.name).apply()
                        }
                        
                        // Show notification
                        notificationHelper.showTurnNotification(
                            roomCode = currentShareCode,
                            lastActivityType = lastActivityType,
                            smokerName = userSmokerName
                        )
                    }
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
        val allParticipants = roomData.sharedSmokers?.keys?.toList() ?: emptyList()
        val pausedSmokers = roomData.pausedSmokers ?: emptyList()
        val awaySmokers = roomData.awayParticipants ?: emptyList()
        
        return allParticipants.filter { smokerId ->
            val userId = if (smokerId.startsWith("local_")) {
                smokerId.removePrefix("local_")
            } else {
                smokerId
            }
            !pausedSmokers.contains(smokerId) && !awaySmokers.contains(userId)
        }.sorted() // Sort to ensure consistent order
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
            .remove(KEY_LAST_TURN_INDEX)
            .remove(KEY_LAST_ACTIVITY_TYPE)
            .remove(KEY_CURRENT_USER_SMOKER_ID)
            .apply()
    }
}