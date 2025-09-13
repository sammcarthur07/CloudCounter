package com.sam.cloudcounter

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class GoalService(private val application: Application) {
    companion object {
        private const val TAG = "GoalService"
    }

    private val goalDao = AppDatabase.getDatabase(application).goalDao()
    private val notificationHelper = NotificationHelper(application)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    init {
        Log.d(TAG, "🎯 GoalService initialized")
        startGoalMonitoring()
        startNotificationUpdater()
    }

    data class SmokerProgress(
        val joints: Int = 0,
        val cones: Int = 0,
        val bowls: Int = 0,
        val customActivities: Map<String, Int> = emptyMap()
    )

    private fun startNotificationUpdater() {
        scope.launch {
            // Track which goals we've shown initial notifications for
            val shownInitialNotifications = mutableSetOf<Long>()

            goalDao.getGoalsWithNotificationsEnabled().collect { goals ->
                goals.forEach { goal ->
                    if (!goal.notificationsEnabled) {
                        // If notifications are disabled, ensure it's cancelled
                        notificationHelper.cancelGoalNotification(goal.goalId)
                        shownInitialNotifications.remove(goal.goalId)
                    } else {
                        // Check if this is the first time showing this goal's notification
                        val isInitial = !shownInitialNotifications.contains(goal.goalId)

                        if (isInitial) {
                            // First time showing - use non-silent notification
                            notificationHelper.showPersistentGoalNotification(goal, isSilentUpdate = false)
                            shownInitialNotifications.add(goal.goalId)
                            Log.d(TAG, "🎯 Showing initial notification for goal ${goal.goalId}")
                        } else {
                            // Update existing - use silent notification
                            notificationHelper.showPersistentGoalNotification(goal, isSilentUpdate = true)
                            Log.d(TAG, "🎯 Updating notification for goal ${goal.goalId}")
                        }
                    }
                }

                // Clean up notifications for deleted goals
                val currentGoalIds = goals.map { it.goalId }.toSet()
                val toRemove = shownInitialNotifications.filter { it !in currentGoalIds }
                toRemove.forEach { goalId ->
                    notificationHelper.cancelGoalNotification(goalId)
                    shownInitialNotifications.remove(goalId)
                    Log.d(TAG, "🎯 Cancelled notification for deleted goal $goalId")
                }
            }
        }
    }

    suspend fun reverseGoalProgressForActivity(
        activityType: ActivityType,
        sessionShareCode: String?,
        smokerName: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🎯↩️ Reversing goal progress for $smokerName - $activityType")

                val prefs = application.getSharedPreferences("sesh", android.content.Context.MODE_PRIVATE)
                val currentSessionId = prefs.getLong("current_session_id", 0L)
                val isSessionActive = prefs.getBoolean("session_active", false)

                val goals = goalDao.getActiveGoals().first()

                goals.forEach { goal ->
                    if (!shouldGoalBeAffected(goal, sessionShareCode, currentSessionId, isSessionActive, smokerName)) {
                        return@forEach
                    }

                    val reverseAmount = when (activityType) {
                        ActivityType.JOINT -> if (goal.targetJoints > 0) 1 else 0
                        ActivityType.CONE -> if (goal.targetCones > 0) 1 else 0
                        ActivityType.BOWL -> if (goal.targetBowls > 0) 1 else 0
                        else -> 0
                    }

                    if (reverseAmount == 0) {
                        return@forEach
                    }

                    val newJoints = if (activityType == ActivityType.JOINT) {
                        kotlin.math.max(0, goal.currentJoints - reverseAmount)
                    } else goal.currentJoints

                    val newCones = if (activityType == ActivityType.CONE) {
                        kotlin.math.max(0, goal.currentCones - reverseAmount)
                    } else goal.currentCones

                    val newBowls = if (activityType == ActivityType.BOWL) {
                        kotlin.math.max(0, goal.currentBowls - reverseAmount)
                    } else goal.currentBowls

                    val smokerProgress = try {
                        val progressMap: MutableMap<String, SmokerProgress> = if (goal.smokerProgress.isNotEmpty()) {
                            gson.fromJson(goal.smokerProgress, object : TypeToken<Map<String, SmokerProgress>>() {}.type)
                                ?: mutableMapOf()
                        } else {
                            mutableMapOf()
                        }

                        if (progressMap.containsKey(smokerName)) {
                            val currentProgress = progressMap[smokerName]!!
                            val updatedProgress = when (activityType) {
                                ActivityType.JOINT -> currentProgress.copy(
                                    joints = kotlin.math.max(0, currentProgress.joints - reverseAmount)
                                )
                                ActivityType.CONE -> currentProgress.copy(
                                    cones = kotlin.math.max(0, currentProgress.cones - reverseAmount)
                                )
                                ActivityType.BOWL -> currentProgress.copy(
                                    bowls = kotlin.math.max(0, currentProgress.bowls - reverseAmount)
                                )
                                else -> currentProgress
                            }
                            progressMap[smokerName] = updatedProgress
                        }

                        gson.toJson(progressMap)
                    } catch (e: Exception) {
                        Log.e(TAG, "🎯↩️ Error updating smoker progress: ${e.message}")
                        goal.smokerProgress
                    }

                    goalDao.updateGoalProgressWithSmoker(
                        goalId = goal.goalId,
                        joints = newJoints,
                        cones = newCones,
                        bowls = newBowls,
                        smokerProgress = smokerProgress
                    )

                    // Update notification silently (since this is an undo)
                    val updatedGoal = goal.copy(
                        currentJoints = newJoints,
                        currentCones = newCones,
                        currentBowls = newBowls,
                        smokerProgress = smokerProgress
                    )
                    notificationHelper.showPersistentGoalNotification(updatedGoal, isSilentUpdate = true)

                    Log.d(TAG, "🎯↩️ Reversed progress for goal ${goal.goalId}")

                    if (!goal.isActive && !goal.allowOverflow) {
                        val totalTarget = goal.targetJoints + goal.targetCones + goal.targetBowls
                        val newTotal = newJoints + newCones + newBowls

                        if (newTotal < totalTarget) {
                            val reactivatedGoal = goal.copy(
                                isActive = true,
                                completedAt = null
                            )
                            goalDao.updateGoal(reactivatedGoal)
                            // Silent update for reactivation
                            notificationHelper.showPersistentGoalNotification(reactivatedGoal, isSilentUpdate = true)
                            Log.d(TAG, "🎯↩️ Reactivated goal ${goal.goalId} after undo")
                        }
                    }
                }

                Log.d(TAG, "🎯↩️ Goal progress reversal completed")

            } catch (e: Exception) {
                Log.e(TAG, "🎯↩️ Error reversing goal progress: ${e.message}", e)
            }
        }
    }

    private fun shouldGoalBeAffected(
        goal: Goal,
        sessionShareCode: String?,
        currentSessionId: Long,
        isSessionActive: Boolean,
        smokerName: String
    ): Boolean {
        if (goal.isPaused) {
            Log.d(TAG, "🎯↩️ Goal ${goal.goalId} is paused, skipping")
            return false
        }

        val selectedSmokers = when (goal.selectedSmokers) {
            "ALL", null, "" -> listOf(smokerName)
            else -> goal.selectedSmokers.split(",").map { it.trim() }
        }

        if (!selectedSmokers.contains(smokerName)) {
            Log.d(TAG, "🎯↩️ Goal ${goal.goalId} doesn't include smoker $smokerName")
            return false
        }

        return when (goal.goalType) {
            GoalType.CURRENT_SESSION -> {
                if (!isSessionActive) {
                    false
                } else {
                    val matchesCloud = sessionShareCode != null && goal.sessionShareCode == sessionShareCode
                    val matchesLocal = sessionShareCode == null && goal.sessionShareCode == null && currentSessionId > 0
                    matchesCloud || matchesLocal
                }
            }
            GoalType.ALL_SESSIONS -> true
            GoalType.TIME_BASED -> {
                val now = System.currentTimeMillis()
                val elapsed = now - goal.lastResetAt
                val duration = getGoalDurationMillis(goal)
                elapsed < duration
            }
        }
    }

    private fun startGoalMonitoring() {
        scope.launch {
            Log.d(TAG, "🎯 Starting goal monitoring")
            goalDao.getActiveGoals().collect { goals ->
                Log.d(TAG, "🎯 Monitoring ${goals.size} active goals")
                goals.forEach { goal ->
                    Log.d(TAG, "🎯 Checking goal ${goal.goalId}: ${goal.goalName}")
                    checkTimeBasedGoalExpiry(goal)
                }
            }
        }
    }

    fun updateGoalProgressForActivity(
        activityType: ActivityType,
        sessionShareCode: String? = null,
        currentSmokerName: String = "Sam"
    ) {
        Log.d(TAG, "🎯 === UPDATE GOAL PROGRESS ===")
        Log.d(TAG, "🎯 Activity type: $activityType")
        Log.d(TAG, "🎯 Session share code: $sessionShareCode")
        Log.d(TAG, "🎯 Current smoker: $currentSmokerName")

        val prefs = application.getSharedPreferences("sesh", android.content.Context.MODE_PRIVATE)
        val currentSessionId = prefs.getLong("current_session_id", 0L)
        val isSessionActive = prefs.getBoolean("session_active", false)

        Log.d(TAG, "🎯 Current session ID: $currentSessionId")
        Log.d(TAG, "🎯 Is session active: $isSessionActive")

        scope.launch {
            try {
                val goalList = goalDao.getActiveGoals().first()
                Log.d(TAG, "🎯 Found ${goalList.size} active goals")

                goalList.forEach { goal ->
                    Log.d(TAG, "🎯 Checking goal ${goal.goalId}: ${goal.goalName} (type: ${goal.goalType})")

                    // Skip the shouldUpdateGoalFixed check for now and check activity match first
                    // Check if this activity matches the goal's selected activity type
                    val shouldTrackThisActivity = when (activityType) {
                        ActivityType.JOINT -> goal.selectedActivityType == "joints"
                        ActivityType.CONE -> goal.selectedActivityType == "cones"
                        ActivityType.BOWL -> goal.selectedActivityType == "bowls"
                        else -> false
                    }

                    if (shouldTrackThisActivity) {
                        if (shouldUpdateGoalFixed(goal, sessionShareCode, currentSessionId, isSessionActive, currentSmokerName)) {
                            Log.d(TAG, "🎯 ✅ Goal ${goal.goalId} tracks this activity type (${goal.selectedActivityType}), updating...")
                            updateSingleGoalForSelectedActivity(goal.goalId, currentSmokerName)
                        } else {
                            Log.d(TAG, "🎯 ❌ Goal ${goal.goalId} tracks correct activity but doesn't apply to session/smoker")
                        }
                    } else {
                        Log.d(TAG, "🎯 ❌ Goal ${goal.goalId} tracks '${goal.selectedActivityType}' but activity is $activityType")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "🎯 Error updating goal progress", e)
            }
        }
    }

    // New unified function for single activity type goals
    fun updateGoalProgressForSelectedActivity(
        activityType: ActivityType,
        customActivityId: String? = null,
        customActivityName: String? = null,
        sessionShareCode: String? = null,
        currentSmokerName: String = "Sam"
    ) {
        Log.d(TAG, "🎯 === UPDATE GOAL PROGRESS (SELECTED ACTIVITY) ===")
        Log.d(TAG, "🎯 Activity type: $activityType")
        Log.d(TAG, "🎯 Custom activity: $customActivityName (ID: $customActivityId)")
        Log.d(TAG, "🎯 Session share code: $sessionShareCode")
        Log.d(TAG, "🎯 Current smoker: $currentSmokerName")

        val prefs = application.getSharedPreferences("sesh", android.content.Context.MODE_PRIVATE)
        val currentSessionId = prefs.getLong("current_session_id", 0L)
        val isSessionActive = prefs.getBoolean("session_active", false)

        scope.launch {
            try {
                val goalList = goalDao.getActiveGoals().first()
                Log.d(TAG, "🎯 Found ${goalList.size} active goals")

                goalList.forEach { goal ->
                    Log.d(TAG, "🎯 Checking goal ${goal.goalId}: ${goal.goalName} (type: ${goal.goalType})")
                    Log.d(TAG, "🎯 Goal tracks activity type: ${goal.selectedActivityType}")

                    if (shouldUpdateGoalFixed(goal, sessionShareCode, currentSessionId, isSessionActive, currentSmokerName)) {
                        // Check if this activity matches the goal's selected activity type
                        Log.d(TAG, "🎯 DEBUG: Checking activity match for goal ${goal.goalId}")
                        Log.d(TAG, "🎯   Goal selectedActivityType: '${goal.selectedActivityType}'")
                        Log.d(TAG, "🎯   Incoming customActivityId: '$customActivityId'")
                        Log.d(TAG, "🎯   Incoming activityType: $activityType")
                        
                        val shouldTrackThisActivity = when {
                            customActivityId != null -> {
                                val matches = goal.selectedActivityType == customActivityId
                                Log.d(TAG, "🎯   Custom activity comparison: '${goal.selectedActivityType}' == '$customActivityId' = $matches")
                                matches
                            }
                            activityType == ActivityType.JOINT -> goal.selectedActivityType == "joints"
                            activityType == ActivityType.CONE -> goal.selectedActivityType == "cones"
                            activityType == ActivityType.BOWL -> goal.selectedActivityType == "bowls"
                            else -> false
                        }

                        if (shouldTrackThisActivity) {
                            Log.d(TAG, "🎯 ✅ Goal ${goal.goalId} MATCHES! Updating progress...")
                            updateSingleGoalForSelectedActivity(goal.goalId, currentSmokerName)
                        } else {
                            Log.d(TAG, "🎯 ❌ Goal ${goal.goalId} NO MATCH - goal tracks '${goal.selectedActivityType}' but received: customId='$customActivityId', type=$activityType")
                        }
                    } else {
                        Log.d(TAG, "🎯 ❌ Goal ${goal.goalId} does not apply to this session/smoker")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "🎯 Error updating goal progress for selected activity", e)
            }
        }
    }

    // Overloaded function for custom activities
    fun updateGoalProgressForCustomActivity(
        customActivityId: String,
        customActivityName: String,
        sessionShareCode: String? = null,
        currentSmokerName: String = "Sam"
    ) {
        Log.d(TAG, "🎯 === UPDATE GOAL PROGRESS (CUSTOM ACTIVITY) ===")
        Log.d(TAG, "🎯 Custom activity: $customActivityName (ID: $customActivityId)")
        Log.d(TAG, "🎯 Session share code: $sessionShareCode")
        Log.d(TAG, "🎯 Current smoker: $currentSmokerName")

        val prefs = application.getSharedPreferences("sesh", android.content.Context.MODE_PRIVATE)
        val currentSessionId = prefs.getLong("current_session_id", 0L)
        val isSessionActive = prefs.getBoolean("session_active", false)

        scope.launch {
            try {
                val goalList = goalDao.getActiveGoals().first()
                Log.d(TAG, "🎯 Found ${goalList.size} active goals")

                goalList.forEach { goal ->
                    Log.d(TAG, "🎯 Checking goal ${goal.goalId}: ${goal.goalName} (type: ${goal.goalType})")

                    if (shouldUpdateGoalFixed(goal, sessionShareCode, currentSessionId, isSessionActive, currentSmokerName)) {
                        Log.d(TAG, "🎯 ✅ Goal ${goal.goalId} applies to this custom activity, updating...")
                        updateSingleGoalProgressForCustomActivity(goal.goalId, customActivityId, customActivityName, currentSmokerName)

                        // Force immediate notification update after database update
                        val updatedGoal = goalDao.getGoalById(goal.goalId)
                        updatedGoal?.let {
                            if (it.notificationsEnabled) {
                                // Calculate total including custom activities
                                val customActivitiesJson = it.customActivities
                                val customTargets: Map<String, Int> = if (customActivitiesJson.isNotEmpty()) {
                                    try {
                                        gson.fromJson(customActivitiesJson, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()
                                    } catch (e: Exception) {
                                        emptyMap()
                                    }
                                } else {
                                    emptyMap()
                                }
                                
                                val totalTarget = it.targetJoints + it.targetCones + it.targetBowls + customTargets.values.sum()
                                val totalCurrent = it.currentJoints + it.currentCones + it.currentBowls + getCustomActivitiesCurrentTotal(it)
                                val progressPercentage = if (totalTarget > 0) (totalCurrent * 100 / totalTarget) else 0

                                val isSilent = progressPercentage !in listOf(25, 50, 75, 100)
                                notificationHelper.showPersistentGoalNotification(it, isSilentUpdate = isSilent)
                                Log.d(TAG, "🎯 Notification updated immediately for goal ${it.goalId}")
                            }
                        }
                    } else {
                        Log.d(TAG, "🎯 ❌ Goal ${goal.goalId} does not apply to this custom activity")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "🎯 Error updating goal progress for custom activity", e)
            }
        }
    }

    private fun shouldUpdateGoalFixed(
        goal: Goal,
        sessionShareCode: String?,
        currentSessionId: Long,
        isSessionActive: Boolean,
        currentSmokerName: String
    ): Boolean {
        if (goal.isPaused) {
            Log.d(TAG, "🎯 Goal ${goal.goalId} is paused, skipping update")
            return false
        }

        val selectedSmokers = when (goal.selectedSmokers) {
            "ALL", null, "" -> return continueGoalCheck(goal, sessionShareCode, currentSessionId, isSessionActive)
            else -> goal.selectedSmokers.split(",")
        }

        if (!selectedSmokers.contains(currentSmokerName)) {
            Log.d(TAG, "🎯 Goal ${goal.goalId} doesn't include smoker $currentSmokerName")
            return false
        }

        return continueGoalCheck(goal, sessionShareCode, currentSessionId, isSessionActive)
    }

    private fun continueGoalCheck(
        goal: Goal,
        sessionShareCode: String?,
        currentSessionId: Long,
        isSessionActive: Boolean
    ): Boolean {
        if (!goal.allowOverflow) {
            // Check if this is a new single-activity goal (using targetValue) or legacy multi-activity goal
            val isNewSingleActivityGoal = goal.targetValue > 0
            
            val totalTarget: Int
            val totalCurrent: Int
            
            if (isNewSingleActivityGoal) {
                // New single-activity goal system uses targetValue/currentValue
                totalTarget = goal.targetValue
                totalCurrent = goal.currentValue
                Log.d(TAG, "🎯 Goal ${goal.goalId} overflow check: NEW system - current=$totalCurrent, target=$totalTarget")
            } else {
                // Legacy multi-activity goal system
                val customTargets: Map<String, Int> = if (goal.customActivities.isNotEmpty()) {
                    try {
                        gson.fromJson(goal.customActivities, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()
                    } catch (e: Exception) {
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }
                totalTarget = goal.targetJoints + goal.targetCones + goal.targetBowls + customTargets.values.sum()
                totalCurrent = goal.currentJoints + goal.currentCones + goal.currentBowls + getCustomActivitiesCurrentTotal(goal)
                Log.d(TAG, "🎯 Goal ${goal.goalId} overflow check: LEGACY system - current=$totalCurrent, target=$totalTarget")
            }
            
            if (totalCurrent >= totalTarget) {
                Log.d(TAG, "🎯 Goal ${goal.goalId} has reached 100% and overflow not allowed")
                return false
            }
        }

        val shouldUpdate = when (goal.goalType) {
            GoalType.CURRENT_SESSION -> {
                if (!isSessionActive) {
                    Log.d(TAG, "🎯   CURRENT_SESSION check: no active session")
                    false
                } else {
                    val matchesCloud = sessionShareCode != null && goal.sessionShareCode == sessionShareCode
                    val matchesLocal = sessionShareCode == null && goal.sessionShareCode == null && currentSessionId > 0
                    val matches = matchesCloud || matchesLocal

                    Log.d(TAG, "🎯   CURRENT_SESSION check:")
                    Log.d(TAG, "🎯     goal.sessionShareCode=${goal.sessionShareCode}")
                    Log.d(TAG, "🎯     current sessionShareCode=$sessionShareCode")
                    Log.d(TAG, "🎯     current sessionId=$currentSessionId")
                    Log.d(TAG, "🎯     matchesCloud=$matchesCloud, matchesLocal=$matchesLocal")
                    Log.d(TAG, "🎯     final matches=$matches")

                    matches
                }
            }
            GoalType.ALL_SESSIONS -> {
                Log.d(TAG, "🎯   ALL_SESSIONS: always applies")
                true
            }
            GoalType.TIME_BASED -> {
                val now = System.currentTimeMillis()
                val elapsed = now - goal.lastResetAt
                val duration = getGoalDurationMillis(goal)
                val inWindow = elapsed < duration
                Log.d(TAG, "🎯   TIME_BASED check: elapsed=${elapsed}ms, duration=${duration}ms, in window=$inWindow")
                inWindow
            }
        }
        return shouldUpdate
    }

    private suspend fun updateSingleGoalProgress(
        goalId: Long,
        activityType: ActivityType,
        smokerName: String
    ) {
        Log.d(TAG, "🎯 === UPDATE SINGLE GOAL PROGRESS ===")
        Log.d(TAG, "🎯 Goal ID: $goalId, Activity: $activityType, Smoker: $smokerName")

        val goal = goalDao.getGoalById(goalId)
        if (goal == null) {
            Log.e(TAG, "🎯 Goal $goalId not found!")
            return
        }

        // Store previous progress to determine if this is first activity or milestone
        val previousJoints = goal.currentJoints
        val previousCones = goal.currentCones
        val previousBowls = goal.currentBowls
        val previousTotal = previousJoints + previousCones + previousBowls
        val totalTarget = goal.targetJoints + goal.targetCones + goal.targetBowls
        val previousPercentage = if (totalTarget > 0) (previousTotal * 100 / totalTarget) else 0

        if (!goal.allowOverflow) {
            val customTargets: Map<String, Int> = if (goal.customActivities.isNotEmpty()) {
                try {
                    gson.fromJson(goal.customActivities, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()
                } catch (e: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
            val totalTargetWithCustom = goal.targetJoints + goal.targetCones + goal.targetBowls + customTargets.values.sum()
            val totalCurrentWithCustom = goal.currentJoints + goal.currentCones + goal.currentBowls + getCustomActivitiesCurrentTotal(goal)
            if (totalCurrentWithCustom >= totalTargetWithCustom) {
                Log.d(TAG, "🎯 Goal already at 100% and auto-complete is enabled, not updating")
                return
            }
        }

        val newJoints = when (activityType) {
            ActivityType.JOINT -> goal.currentJoints + 1
            ActivityType.CONE, ActivityType.BOWL, ActivityType.SESSION_SUMMARY -> goal.currentJoints
        }

        val newCones = when (activityType) {
            ActivityType.CONE -> goal.currentCones + 1
            ActivityType.JOINT, ActivityType.BOWL, ActivityType.SESSION_SUMMARY -> goal.currentCones
        }

        val newBowls = when (activityType) {
            ActivityType.BOWL -> goal.currentBowls + 1
            ActivityType.JOINT, ActivityType.CONE, ActivityType.SESSION_SUMMARY -> goal.currentBowls
        }

        val updatedSmokerProgress = updateSmokerProgressJson(
            goal.smokerProgress,
            smokerName,
            activityType
        )

        // Update database
        goalDao.updateGoalProgressWithSmoker(
            goalId,
            newJoints,
            newCones,
            newBowls,
            updatedSmokerProgress
        )

        Log.d(TAG, "🎯 Database updated with individual smoker progress")

        val updatedGoal = goal.copy(
            currentJoints = newJoints,
            currentCones = newCones,
            currentBowls = newBowls,
            smokerProgress = updatedSmokerProgress
        )

        // Calculate new progress percentage
        val newTotal = newJoints + newCones + newBowls
        val newPercentage = if (totalTarget > 0) (newTotal * 100 / totalTarget) else 0

        // Determine if this should be a silent update
        val isSilentUpdate = when {
            previousTotal == 0 -> false // First activity - make sound
            newPercentage >= 100 && previousPercentage < 100 -> false // Just completed - make sound
            newPercentage >= 75 && previousPercentage < 75 -> false // Hit 75% milestone
            newPercentage >= 50 && previousPercentage < 50 -> false // Hit 50% milestone
            newPercentage >= 25 && previousPercentage < 25 -> false // Hit 25% milestone
            else -> true // Regular progress - silent
        }

        // CRITICAL: Always update the notification, regardless of whether it's silent
        // This ensures real-time updates
        if (goal.notificationsEnabled) {
            notificationHelper.showPersistentGoalNotification(updatedGoal, isSilentUpdate)
            Log.d(TAG, "🎯 Notification updated (silent: $isSilentUpdate) - ${newPercentage}%")
        }

        checkGoalProgress(updatedGoal)
    }

    private fun updateSmokerProgressJson(
        currentJson: String,
        smokerName: String,
        activityType: ActivityType
    ): String {
        return try {
            val json = if (currentJson.isEmpty() || currentJson == "{}") {
                org.json.JSONObject()
            } else {
                org.json.JSONObject(currentJson)
            }

            val smokerData = if (json.has(smokerName)) {
                json.getJSONObject(smokerName)
            } else {
                org.json.JSONObject().apply {
                    put("j", 0)
                    put("c", 0)
                    put("b", 0)
                }
            }

            when (activityType) {
                ActivityType.JOINT -> smokerData.put("j", smokerData.optInt("j", 0) + 1)
                ActivityType.CONE -> smokerData.put("c", smokerData.optInt("c", 0) + 1)
                ActivityType.BOWL -> smokerData.put("b", smokerData.optInt("b", 0) + 1)
                ActivityType.SESSION_SUMMARY -> {
                    // Don't update individual counts for session summary
                }
            }

            json.put(smokerName, smokerData)
            json.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating smoker progress JSON", e)
            currentJson
        }
    }

    private suspend fun checkTimeBasedGoalExpiry(goal: Goal) {
        if (goal.goalType != GoalType.TIME_BASED || !goal.isActive) return

        val now = System.currentTimeMillis()
        val elapsed = now - goal.lastResetAt
        val duration = getGoalDurationMillis(goal)

        Log.d(TAG, "🎯 Checking time-based goal ${goal.goalId}")
        Log.d(TAG, "🎯   Elapsed: ${elapsed}ms, Duration: ${duration}ms")
        Log.d(TAG, "🎯   Time remaining: ${duration - elapsed}ms")

        if (elapsed >= duration) {
            Log.d(TAG, "🎯 Time-based goal ${goal.goalId} has expired!")
            Log.d(TAG, "🎯   Is recurring: ${goal.isRecurring}")
            Log.d(TAG, "🎯   Allow overflow: ${goal.allowOverflow}")

            when {
                goal.isRecurring -> {
                    Log.d(TAG, "🎯 Resetting recurring time-based goal")
                    resetGoal(goal)
                }
                !goal.allowOverflow -> {
                    Log.d(TAG, "🎯 Auto-end enabled, marking time-based goal as ended")
                    goalDao.markGoalCompleted(goal.goalId, now)
                    val completedGoal = goal.copy(isActive = false, completedAt = now)
                    notificationHelper.showPersistentGoalNotification(completedGoal)
                }
                else -> {
                    Log.d(TAG, "🎯 Continue tracking enabled, time-based goal continues despite expiry")
                }
            }
        }
    }

    private suspend fun checkGoalProgress(goal: Goal) {
        Log.d(TAG, "🎯 === CHECK GOAL PROGRESS ===")
        Log.d(TAG, "🎯 Goal ${goal.goalId}: ${goal.goalName}")

        val customTargets: Map<String, Int> = if (goal.customActivities.isNotEmpty()) {
            try {
                gson.fromJson(goal.customActivities, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }
        val totalTarget = goal.targetJoints + goal.targetCones + goal.targetBowls + customTargets.values.sum()
        val totalCurrent = goal.currentJoints + goal.currentCones + goal.currentBowls + getCustomActivitiesCurrentTotal(goal)

        Log.d(TAG, "🎯 Total current: $totalCurrent")
        Log.d(TAG, "🎯 Total target: $totalTarget")
        Log.d(TAG, "🎯 Allow overflow: ${goal.allowOverflow}")
        Log.d(TAG, "🎯 Is Active: ${goal.isActive}")

        if (totalTarget == 0) {
            Log.d(TAG, "🎯 No targets set, skipping")
            return
        }

        val progressPercentage = (totalCurrent * 100 / totalTarget)
        Log.d(TAG, "🎯 Progress percentage: $progressPercentage%")

        // Check for 100% completion
        if (progressPercentage >= 100 && goal.isActive) {
            Log.d(TAG, "🎯 🎉 GOAL REACHED 100%!")

            when {
                goal.allowOverflow -> {
                    Log.d(TAG, "🎯 Continue tracking enabled, goal continues past 100%")
                }
                goal.isRecurring -> {
                    Log.d(TAG, "🎯 Goal is recurring, resetting...")
                    resetGoal(goal)
                }
                else -> {
                    Log.d(TAG, "🎯 Auto-end enabled, marking goal as ended")
                    goalDao.markGoalCompleted(goal.goalId, System.currentTimeMillis())
                    val completedGoal = goal.copy(isActive = false, completedAt = System.currentTimeMillis())
                    notificationHelper.showPersistentGoalNotification(completedGoal)
                }
            }
        }

        Log.d(TAG, "🎯 === END CHECK GOAL PROGRESS ===")
    }

    private suspend fun resetGoal(goal: Goal) {
        Log.d(TAG, "🎯 Resetting goal ${goal.goalId}")

        if (goal.isRecurring) {
            goalDao.resetGoalProgress(goal.goalId, System.currentTimeMillis())
            val resetGoal = goal.copy(
                currentJoints = 0,
                currentCones = 0,
                currentBowls = 0,
                lastResetAt = System.currentTimeMillis(),
                completedRounds = goal.completedRounds + 1
            )
            notificationHelper.showPersistentGoalNotification(resetGoal)
        }

        Log.d(TAG, "🎯 Goal reset complete")
    }

    private fun getGoalDurationMillis(goal: Goal): Long {
        val baseMillis = when (goal.timeUnit) {
            TimeUnit.MINUTE -> 60000L
            TimeUnit.HOUR -> 3600000L
            TimeUnit.DAY -> 86400000L
            TimeUnit.WEEK -> 604800000L
            TimeUnit.FORTNIGHT -> 1209600000L
            TimeUnit.MONTH -> 2592000000L
            TimeUnit.YEAR -> 31536000000L
            null -> 0L
        }
        return baseMillis * (goal.timeDuration ?: 1)
    }


    suspend fun endCurrentSessionGoals(sessionShareCode: String) {
        Log.d(TAG, "🎯 Ending all CURRENT_SESSION goals for session $sessionShareCode")

        val goals = goalDao.getActiveGoals().first()
        val sessionEndTime = System.currentTimeMillis()

        goals.filter { it.goalType == GoalType.CURRENT_SESSION && it.sessionShareCode == sessionShareCode }
            .forEach { goal ->
                Log.d(TAG, "🎯 Marking session goal ${goal.goalId} as paused")

                // Update the goal with session end date and pause status
                val updatedGoal = goal.copy(
                    isPaused = true,
                    sessionEndDate = sessionEndTime,
                    wasManuallyPaused = false // This is automatic from session end
                )
                goalDao.updateGoal(updatedGoal)

                // Update the notification silently to show "Past Sesh"
                notificationHelper.showPersistentGoalNotification(updatedGoal, isSilentUpdate = true)
            }
    }

    suspend fun resumeCurrentSessionGoals(sessionShareCode: String) {
        Log.d(TAG, "🎯 Resuming all CURRENT_SESSION goals for session $sessionShareCode")

        val goals = goalDao.getActiveGoals().first()

        goals.filter {
            it.goalType == GoalType.CURRENT_SESSION &&
                    it.sessionShareCode == sessionShareCode &&
                    !it.wasManuallyPaused // Only resume if not manually paused
        }.forEach { goal ->
            Log.d(TAG, "🎯 Resuming session goal ${goal.goalId}")

            // Clear the session end date and unpause
            val updatedGoal = goal.copy(
                isPaused = false,
                sessionEndDate = null
            )
            goalDao.updateGoal(updatedGoal)

            // Update the notification silently to show "Current Sesh" again
            notificationHelper.showPersistentGoalNotification(updatedGoal, isSilentUpdate = true)
        }
    }

    suspend fun toggleGoalNotifications(goalId: Long, enabled: Boolean): Boolean {
        Log.d(TAG, "🎯🔔 === TOGGLE NOTIFICATIONS ===")
        Log.d(TAG, "🎯🔔 Goal ID: $goalId")
        Log.d(TAG, "🎯🔔 New state: $enabled")

        return try {
            // Get the current goal
            val goal = goalDao.getGoalById(goalId)
            if (goal != null) {
                Log.d(TAG, "🎯🔔 Current goal found: ${goal.goalName}")
                Log.d(TAG, "🎯🔔 Current notificationsEnabled: ${goal.notificationsEnabled}")
                Log.d(TAG, "🎯🔔 Current progress: J${goal.currentJoints}/C${goal.currentCones}/B${goal.currentBowls}")

                // CRITICAL: Only update the notificationsEnabled field, preserve all other data
                val updatedGoal = goal.copy(notificationsEnabled = enabled)
                goalDao.updateGoal(updatedGoal)

                Log.d(TAG, "🎯🔔 Updated goal in database with notificationsEnabled = $enabled")

                if (!enabled) {
                    // Cancel the notification immediately
                    notificationHelper.cancelGoalNotification(goalId)
                    Log.d(TAG, "🎯🔔 Notification cancelled for goal $goalId")
                } else {
                    // Show the notification (non-silent since user just enabled it)
                    notificationHelper.showPersistentGoalNotification(updatedGoal, isSilentUpdate = false)
                    Log.d(TAG, "🎯🔔 Notification shown for goal $goalId")
                }

                Log.d(TAG, "🎯🔔 Toggle successful")
                true // Success
            } else {
                Log.e(TAG, "🎯🔔 Goal $goalId not found")
                false // Failure
            }
        } catch (e: Exception) {
            Log.e(TAG, "🎯🔔 Error toggling notifications: ${e.message}", e)
            e.printStackTrace()
            false // Failure
        }
    }

    suspend fun deleteGoal(goalId: Long) {
        Log.d(TAG, "🎯 Deleting goal $goalId")

        // Cancel the notification first
        notificationHelper.cancelGoalNotification(goalId)

        // Then delete from database
        val goal = goalDao.getGoalById(goalId)
        goal?.let {
            goalDao.deleteGoal(it)
        }

        Log.d(TAG, "🎯 Goal $goalId deleted and notification cancelled")
    }

    private suspend fun updateSingleGoalProgressForCustomActivity(
        goalId: Long,
        customActivityId: String,
        customActivityName: String,
        smokerName: String
    ) {
        Log.d(TAG, "🎯 === UPDATE SINGLE GOAL PROGRESS (CUSTOM ACTIVITY) ===")
        Log.d(TAG, "🎯 Goal ID: $goalId, Custom Activity: $customActivityName (ID: $customActivityId), Smoker: $smokerName")

        val goal = goalDao.getGoalById(goalId)
        if (goal == null) {
            Log.e(TAG, "🎯 Goal $goalId not found!")
            return
        }

        // Parse existing custom activities from JSON
        val customActivitiesJson = goal.customActivities
        val currentCustomActivities: MutableMap<String, Int> = if (customActivitiesJson.isNotEmpty()) {
            try {
                gson.fromJson(customActivitiesJson, object : TypeToken<MutableMap<String, Int>>() {}.type) ?: mutableMapOf()
            } catch (e: Exception) {
                Log.e(TAG, "🎯 Error parsing custom activities JSON", e)
                mutableMapOf()
            }
        } else {
            mutableMapOf()
        }

        // Increment the count for this custom activity
        currentCustomActivities[customActivityId] = (currentCustomActivities[customActivityId] ?: 0) + 1

        // Update smoker progress
        val updatedSmokerProgress = updateSmokerProgressJsonForCustomActivity(
            goal.smokerProgress,
            smokerName,
            customActivityId
        )

        // Save updated goal
        val updatedCustomActivitiesJson = gson.toJson(currentCustomActivities)
        goalDao.updateGoalCustomActivities(goalId, updatedCustomActivitiesJson)
        goalDao.updateGoalSmokerProgress(goalId, updatedSmokerProgress)

        Log.d(TAG, "🎯 Updated custom activities: $updatedCustomActivitiesJson")
        Log.d(TAG, "🎯 Updated smoker progress: $updatedSmokerProgress")
    }

    private fun updateSmokerProgressJsonForCustomActivity(
        existingSmokerProgressJson: String,
        smokerName: String,
        customActivityId: String
    ): String {
        val progressMap: MutableMap<String, SmokerProgress> = if (existingSmokerProgressJson.isNotEmpty()) {
            try {
                gson.fromJson(existingSmokerProgressJson, object : TypeToken<MutableMap<String, SmokerProgress>>() {}.type) ?: mutableMapOf()
            } catch (e: Exception) {
                Log.e(TAG, "🎯 Error parsing smoker progress JSON", e)
                mutableMapOf()
            }
        } else {
            mutableMapOf()
        }

        val currentProgress = progressMap[smokerName] ?: SmokerProgress()
        val updatedCustomActivities = currentProgress.customActivities.toMutableMap()
        updatedCustomActivities[customActivityId] = (updatedCustomActivities[customActivityId] ?: 0) + 1

        val updatedProgress = currentProgress.copy(customActivities = updatedCustomActivities)
        progressMap[smokerName] = updatedProgress

        return gson.toJson(progressMap)
    }

    private fun getCustomActivitiesCurrentTotal(goal: Goal): Int {
        return if (goal.customActivities.isNotEmpty()) {
            try {
                val customActivities: Map<String, Int> = gson.fromJson(goal.customActivities, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()
                customActivities.values.sum()
            } catch (e: Exception) {
                Log.e(TAG, "🎯 Error parsing custom activities for total calculation", e)
                0
            }
        } else {
            0
        }
    }

    private suspend fun updateSingleGoalForSelectedActivity(
        goalId: Long,
        smokerName: String
    ) {
        Log.d(TAG, "🎯 === UPDATE SINGLE GOAL FOR SELECTED ACTIVITY ===")
        Log.d(TAG, "🎯 Goal ID: $goalId, Smoker: $smokerName")

        val goal = goalDao.getGoalById(goalId)
        if (goal == null) {
            Log.e(TAG, "🎯 Goal $goalId not found!")
            return
        }

        // Check if goal allows overflow
        if (!goal.allowOverflow && goal.currentValue >= goal.targetValue) {
            Log.d(TAG, "🎯 Goal already at 100% and auto-complete is enabled, not updating")
            return
        }

        // Increment the current value
        val newCurrentValue = goal.currentValue + 1
        goalDao.updateGoalCurrentValue(goalId, newCurrentValue)

        Log.d(TAG, "🎯 Updated goal ${goalId} current value: ${goal.currentValue} -> $newCurrentValue")
        Log.d(TAG, "🎯 Target value: ${goal.targetValue}")

        // Calculate progress percentage
        val progressPercentage = if (goal.targetValue > 0) (newCurrentValue * 100 / goal.targetValue) else 0
        Log.d(TAG, "🎯 Progress percentage: $progressPercentage%")

        // Update notification if enabled
        val updatedGoal = goalDao.getGoalById(goalId)
        updatedGoal?.let {
            if (it.notificationsEnabled) {
                // Check for milestones
                val previousPercentage = if (goal.targetValue > 0) (goal.currentValue * 100 / goal.targetValue) else 0
                val isSilent = when {
                    goal.currentValue == 0 -> false // First activity - make sound
                    progressPercentage >= 100 && previousPercentage < 100 -> false // Just completed - make sound
                    progressPercentage >= 75 && previousPercentage < 75 -> false // Hit 75% milestone
                    progressPercentage >= 50 && previousPercentage < 50 -> false // Hit 50% milestone
                    progressPercentage >= 25 && previousPercentage < 25 -> false // Hit 25% milestone
                    else -> true // Regular progress - silent
                }
                
                notificationHelper.showPersistentGoalNotification(it, isSilentUpdate = isSilent)
                Log.d(TAG, "🎯 Notification updated (silent: $isSilent) - ${progressPercentage}%")
            }
        }

        // Handle goal completion
        if (progressPercentage >= 100 && goal.isActive) {
            when {
                goal.isRecurring -> {
                    Log.d(TAG, "🎯 Goal is recurring, resetting...")
                    goalDao.updateGoalCurrentValue(goalId, 0)
                }
                !goal.allowOverflow -> {
                    Log.d(TAG, "🎯 Auto-end enabled, marking goal as completed")
                    goalDao.markGoalCompleted(goalId, System.currentTimeMillis())
                }
                else -> {
                    Log.d(TAG, "🎯 Continue tracking enabled, goal continues past 100%")
                }
            }
        }
    }

    fun cleanup() {
        Log.d(TAG, "🎯 Cleaning up GoalService")
        scope.cancel()
    }
}