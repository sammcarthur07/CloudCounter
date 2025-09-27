package com.vibecode.cloudcounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActivityNotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ActivityNotifReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val app = context.applicationContext as CloudCounterApplication
        val repo = app.repository
        val sessionSyncService = SessionSyncService(context, repository = repo)
        val action = intent.action ?: return
        
        // Handle ACTION_ADD_FROM_TURN separately
        if (action == NotificationHelper.ACTION_ADD_FROM_TURN) {
            handleTurnNotificationAction(context, intent, app, repo, sessionSyncService)
            return
        }
        
        val type = intent.getSerializableExtra(NotificationHelper.EXTRA_TYPE) as? ActivityType
            ?: return

        val sessionShareCode = intent.getStringExtra(NotificationHelper.EXTRA_SESSION_SHARE_CODE)
        val smokerCloudId = intent.getStringExtra(NotificationHelper.EXTRA_SMOKER_CLOUD_ID)

        // Get session state from preferences to understand auto/sticky mode
        val prefs = context.getSharedPreferences("sesh", Context.MODE_PRIVATE)
        val sessionActive = prefs.getBoolean("sessionActive", false)
        val isAutoMode = prefs.getBoolean("isAutoMode", true) // Default to auto if not set

        CoroutineScope(Dispatchers.IO).launch {
            var usedSmokerId: Long = app.defaultSmokerId
            var usedCloudSmokerId: String? = null
            var smokerForActivity: Smoker? = null

            // Get current smoker selection from preferences for auto mode
            val currentSelectedSmokerId = if (sessionActive && isAutoMode) {
                app.defaultSmokerId // Use currently selected smoker in auto mode
            } else {
                app.defaultSmokerId // In sticky mode or no session, use default
            }

            // Find the appropriate smoker
            if (!smokerCloudId.isNullOrBlank()) {
                val local = repo.getSmokerByCloudUserId(smokerCloudId)
                if (local != null) {
                    smokerForActivity = local
                }
            }

            // Fallback to current selected smoker if the cloud one isn't found
            if (smokerForActivity == null) {
                smokerForActivity = repo.getSmokerById(currentSelectedSmokerId)
            }

            if (smokerForActivity == null) {
                Log.e(TAG, "No valid smoker found for notification action.")
                return@launch
            }

            usedSmokerId = smokerForActivity.smokerId
            usedCloudSmokerId = smokerForActivity.cloudUserId

            var justAdded = false
            val addedAt = System.currentTimeMillis()

            when (action) {
                NotificationHelper.ACTION_ADD_ENTRY -> {
                    Log.d(TAG, "🔔 Adding ${type.name} from notification for ${smokerForActivity.name}")

                    if (!sessionShareCode.isNullOrBlank()) {
                        // Cloud session - add to room
                        val smokerActivityUid = if (smokerForActivity.isCloudSmoker) {
                            smokerForActivity.cloudUserId!!
                        } else {
                            "local_${smokerForActivity.uid}"
                        }

                        val deviceId = Settings.Secure.getString(
                            context.contentResolver,
                            Settings.Secure.ANDROID_ID
                        ) ?: "unknown"

                        sessionSyncService.addActivityToRoom(
                            shareCode = sessionShareCode,
                            smokerUid = smokerActivityUid,
                            smokerName = smokerForActivity.name,
                            activityType = type,
                            timestamp = addedAt,
                            deviceId = deviceId
                        ).fold(
                            onSuccess = {
                                Log.d(TAG, "✅ Added activity to room $sessionShareCode for smoker ${smokerForActivity.name}")
                                justAdded = true

                                // SEND BROADCAST HERE to update the spinner
                                sendSmokerUpdateBroadcast(context, smokerForActivity)

                                // Handle smoker rotation ONLY for auto mode in cloud sessions
                                if (sessionActive && isAutoMode) {
                                    handleSmokerRotationInNotification(app, repo, sessionShareCode, context)
                                }
                            },
                            onFailure = { error ->
                                Log.e(TAG, "❌ Failed to add activity to room: ${error.message}")
                                // Fallback to local database
                                repo.insert(ActivityLog(type = type, timestamp = addedAt, smokerId = usedSmokerId))
                                justAdded = true

                                // SEND BROADCAST HERE too
                                sendSmokerUpdateBroadcast(context, smokerForActivity)
                            }
                        )
                    } else {
                        // Local session - add to local database
                        repo.insert(ActivityLog(type = type, timestamp = addedAt, smokerId = usedSmokerId))
                        justAdded = true
                        Log.d(TAG, "✅ Added activity locally for smoker ${smokerForActivity.name}")

                        // SEND BROADCAST HERE for local sessions
                        sendSmokerUpdateBroadcast(context, smokerForActivity)

                        // Handle smoker rotation ONLY for auto mode in local sessions
                        if (sessionActive && isAutoMode) {
                            handleLocalSmokerRotation(app, repo, context)
                        }
                    }

                    // Update session stats and timers
                    if (sessionActive) {
                        updateSessionStatsFromNotification(context, prefs, addedAt, isAutoMode)
                    }
                }

                NotificationHelper.ACTION_REMOVE_LAST_ENTRY -> {
                    val prefs = context.getSharedPreferences("sesh", Context.MODE_PRIVATE)
                    val sessionActive = prefs.getBoolean("sessionActive", false)
                    val sessionStart = prefs.getLong("sessionStart", System.currentTimeMillis())

                    // Get the last activity for this type within the current session
                    val lastLog = if (sessionActive) {
                        repo.getLogsInTimeRange(sessionStart, System.currentTimeMillis())
                            .filter { it.type == type }
                            .maxByOrNull { it.timestamp }
                    } else {
                        repo.getLastLogByType(type)
                    }

                    lastLog?.let {
                        // Get the smoker for this activity
                        val smokerForUndo = repo.getSmokerById(it.smokerId)

                        // Reverse goal progress before removing the activity
                        val goalSessionCode = if (sessionActive) sessionShareCode else null
                        try {
                            reverseGoalProgressForUndo(
                                context = context,
                                activityLog = it,
                                smoker = smokerForUndo,
                                sessionShareCode = goalSessionCode
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "🎯❌ Failed to reverse goal progress during notification undo: ${e.message}", e)
                        }

                        // Handle stash restoration directly here
                        if (it.payerStashOwnerId == null && it.gramsAtLog > 0) {
                            // This activity consumed from MY stash, we need to restore it
                            Log.d(TAG, "🔙 Undoing stash consumption from notification")
                            Log.d(TAG, "🔙   Activity type: ${it.type}")
                            Log.d(TAG, "🔙   Grams: ${it.gramsAtLog}")
                            Log.d(TAG, "🔙   Price per gram: ${it.pricePerGramAtLog}")

                            // Restore stash directly through database
                            restoreStashDirectly(context, it, smokerForUndo?.name)
                        }

                        // Delete the activity
                        Log.d("SeshFlow", "Notification undo: deleting activity id=${it.id}, smokerId=${it.smokerId}, type=${it.type}, ts=${it.timestamp}")
                        repo.delete(it)
                        Log.d(TAG, "🗑️ Removed last ${type.name} from notification")

                        // Remove from room if in cloud session
                        if (!sessionShareCode.isNullOrBlank() && smokerForUndo != null) {
                            val smokerActivityUid = if (smokerForUndo.isCloudSmoker) {
                                smokerForUndo.cloudUserId!!
                            } else {
                                "local_${smokerForUndo.uid}"
                            }

                            sessionSyncService.removeActivityFromRoom(
                                shareCode = sessionShareCode,
                                smokerUid = smokerActivityUid,
                                activityType = type,
                                timestamp = it.timestamp
                            ).fold(
                                onSuccess = {
                                    Log.d(TAG, "✅ Removed activity from room")

                                    // Force room refresh to update all stats
                                    sessionSyncService.forceRefreshRoom(sessionShareCode)
                                },
                                onFailure = { error ->
                                    Log.e(TAG, "❌ Failed to remove activity from room: ${error.message}")
                                }
                            )
                        }

                        // Send a broadcast to update the MainActivity
                        val updateIntent = Intent("com.vibecode.cloudcounter.ACTIVITY_UNDONE").apply {
                            putExtra("activityType", type.name)
                            putExtra("smokerId", it.smokerId)
                            putExtra("hadStashConsumption", it.payerStashOwnerId == null && it.gramsAtLog > 0)
                            setPackage(context.packageName)
                        }
                        context.sendBroadcast(updateIntent)

                        // Update the smoker selection if needed
                        smokerForUndo?.let { smoker ->
                            sendSmokerUpdateBroadcast(context, smoker)
                        }
                    }
                }

                NotificationHelper.ACTION_REWIND_SESSION -> {
                    Log.d(TAG, "⏪ Rewind action triggered from notification")

                    // Get current session state
                    val prefs = context.getSharedPreferences("sesh", Context.MODE_PRIVATE)
                    val sessionActive = prefs.getBoolean("sessionActive", false)

                    if (!sessionActive) {
                        Log.w(TAG, "⏪ Cannot rewind - no active session")
                        // Optionally show a toast to inform the user
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "No active session to rewind",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    // Get current rewind offset and apply more rewind
                    var rewindOffset = prefs.getLong("rewindOffset", 0L)
                    val sessionStart = prefs.getLong("sessionStart", System.currentTimeMillis())
                    val REWIND_AMOUNT_MS = 10000L // 10 seconds, same as MainActivity

                    // Check if we can rewind further
                    val realNow = System.currentTimeMillis()
                    val currentElapsed = realNow - sessionStart - rewindOffset

                    if (currentElapsed < REWIND_AMOUNT_MS) {
                        Log.w(TAG, "⏪ Cannot rewind past session start")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Cannot rewind past session start",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    // Apply rewind
                    rewindOffset += REWIND_AMOUNT_MS

                    // Save the new rewind offset
                    prefs.edit()
                        .putLong("rewindOffset", rewindOffset)
                        .apply()

                    Log.d(TAG, "⏪ Applied rewind from notification: total offset = ${rewindOffset}ms")

                    // Send broadcast to notify MainActivity to update timers
                    val rewindIntent = Intent("com.vibecode.cloudcounter.SESSION_REWOUND").apply {
                        putExtra("rewindOffset", rewindOffset)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(rewindIntent)

                    // Show feedback to user
                    val totalRewoundSeconds = rewindOffset / 1000
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Rewound ${totalRewoundSeconds}s total",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    // Update the notification to reflect current state
                    // (The notification will be refreshed at the end of this method)
                    Log.d(TAG, "⏪ Rewind applied successfully")
                }
            }

            // Recompute display values for notification update
            val lastLog = repo.getLastLogByType(type)
            val lastTs = lastLog?.timestamp
            val conesSinceLastBowl = if (type == ActivityType.CONE && lastTs != null) {
                repo.getLastBowlBefore(lastTs)?.let { bowl ->
                    repo.countConesBetweenTimestamps(bowl.timestamp, lastTs)
                }
            } else null

            // Get the smoker name for the last activity
            val lastSmokerName = lastLog?.let { log ->
                repo.getSmokerById(log.smokerId)?.name
            }

            withContext(Dispatchers.Main) {
                NotificationHelper(context)
                    .showActivityNotification(
                        type,
                        lastTs,
                        conesSinceLastBowl,
                        sessionShareCode,
                        usedCloudSmokerId,
                        justAdded = justAdded,
                        addedAt = if (justAdded) addedAt else null,
                        lastSmokerName = lastSmokerName
                    )
            }
        }
    }

    /**
     * Restore stash directly through database operations
     * This is used when undoing from notification where we don't have access to ViewModels
     */
    private suspend fun restoreStashDirectly(
        context: Context,
        activityLog: ActivityLog,
        smokerName: String?
    ) {
        try {
            val db = AppDatabase.getDatabase(context)
            val stashDao = db.stashDao()

            // Get current stash
            val currentStash = stashDao.getCurrentStash() ?: return

            // Get current ratios
            val currentRatios = stashDao.getConsumptionRatios() ?: return

            // Check if this activity type was deducting from stash
            val wasDeducting = when (activityLog.type) {
                ActivityType.CONE -> currentRatios.deductConesFromStash
                ActivityType.JOINT -> currentRatios.deductJointsFromStash
                ActivityType.BOWL -> currentRatios.deductBowlsFromStash
                else -> false
            }

            if (!wasDeducting) {
                Log.d(TAG, "🔙 ${activityLog.type} wasn't deducting from stash, skipping restoration")
                return
            }

            // Get the grams that were consumed
            val gramsToRestore = if (activityLog.gramsAtLog > 0) {
                activityLog.gramsAtLog
            } else {
                when (activityLog.type) {
                    ActivityType.CONE -> currentRatios.coneGrams
                    ActivityType.JOINT -> currentRatios.jointGrams
                    ActivityType.BOWL -> currentRatios.bowlGrams
                    else -> 0.0
                }
            }

            if (gramsToRestore <= 0) {
                Log.d(TAG, "🔙 No grams to restore")
                return
            }

            // Calculate the cost to restore
            val pricePerGram = if (activityLog.pricePerGramAtLog > 0) {
                activityLog.pricePerGramAtLog
            } else {
                currentStash.pricePerGram
            }
            val costToRestore = gramsToRestore * pricePerGram

            Log.d(TAG, "🔙 Restoring ${gramsToRestore}g ($${costToRestore}) to stash")

            // Restore the stash amount
            val newCurrentGrams = currentStash.currentGrams + gramsToRestore

            // Recalculate weighted average price per gram
            val currentTotalValue = currentStash.currentGrams * currentStash.pricePerGram
            val newTotalValue = currentTotalValue + costToRestore
            val newPricePerGram = if (newCurrentGrams > 0) {
                newTotalValue / newCurrentGrams
            } else {
                pricePerGram
            }

            val updatedStash = currentStash.copy(
                currentGrams = newCurrentGrams,
                pricePerGram = newPricePerGram,
                lastUpdated = java.util.Date()
            )

            // Update stash in database
            stashDao.updateStash(updatedStash)

            // Add a reversal entry to history
            val reversalEntry = StashEntry(
                timestamp = java.util.Date(),
                type = StashEntryType.ADJUST,
                grams = gramsToRestore,
                pricePerGram = pricePerGram,
                totalCost = costToRestore,
                activityType = activityLog.type,
                smokerName = smokerName,
                notes = "Undo from notification: ${activityLog.type.name} restored"
            )
            stashDao.insertStashEntry(reversalEntry)

            Log.d(TAG, "🔙 Stash restored from notification - new amount: ${newCurrentGrams}g @ $${newPricePerGram}/g")

            // Send broadcast to trigger UI updates
            val stashUpdateIntent = Intent("com.vibecode.cloudcounter.STASH_UPDATED").apply {
                putExtra("gramsRestored", gramsToRestore)
                putExtra("newTotal", newCurrentGrams)
                setPackage(context.packageName)
            }
            context.sendBroadcast(stashUpdateIntent)

        } catch (e: Exception) {
            Log.e(TAG, "🔙 Error restoring stash from notification", e)
        }
    }

    // NEW: Handle smoker rotation for cloud sessions
    private suspend fun handleSmokerRotationInNotification(
        app: CloudCounterApplication,
        repo: ActivityRepository,
        shareCode: String,
        context: Context
    ) {
        try {
            // Get room data to check active participants
            val roomData = withContext(Dispatchers.IO) {
                SessionSyncService(context, repository = repo).getRoomData(shareCode)
            }

            if (roomData == null) {
                Log.w(TAG, "Room data not found for rotation")
                return
            }

            // Get all smokers and filter active ones
            val allSmokers = repo.getAllSmokersList()
            val pausedSmokerIds = roomData.safePausedSmokers()
            val awaySmokers = roomData.safeAwayParticipants()

            // Filter to only active smokers
            val activeSmokers = allSmokers.filter { smoker ->
                val smokerId = if (smoker.isCloudSmoker) {
                    smoker.cloudUserId ?: return@filter false
                } else {
                    "local_${smoker.smokerId}"
                }
                val userId = smoker.cloudUserId

                // Include only if not paused AND not away
                !pausedSmokerIds.contains(smokerId) && !awaySmokers.contains(userId)
            }

            if (activeSmokers.isEmpty()) {
                Log.w(TAG, "No active smokers for rotation")
                return
            }

            // Find current smoker
            val currentSmokerId = app.defaultSmokerId
            val currentIndex = activeSmokers.indexOfFirst { it.smokerId == currentSmokerId }

            if (currentIndex < 0) {
                // Current smoker not in active list, just use first active
                val nextSmoker = activeSmokers.first()
                app.defaultSmokerId = nextSmoker.smokerId
                sendSmokerUpdateBroadcast(context, nextSmoker)
                return
            }

            // Get next active smoker
            val nextIndex = (currentIndex + 1) % activeSmokers.size
            val nextSmoker = activeSmokers[nextIndex]

            // Update default smoker selection
            app.defaultSmokerId = nextSmoker.smokerId

            // Save to preferences
            val prefs = context.getSharedPreferences("sesh", Context.MODE_PRIVATE)
            prefs.edit().putLong("defaultSmokerId", nextSmoker.smokerId).apply()

            Log.d(TAG, "🔄 Rotated to next active smoker: ${nextSmoker.name} (${if (nextSmoker.isCloudSmoker) "cloud" else "local"})")

            // Send broadcast to update UI
            sendSmokerUpdateBroadcast(context, nextSmoker)

        } catch (e: Exception) {
            Log.e(TAG, "Error rotating smoker: ${e.message}", e)
        }
    }

    // NEW: Handle smoker rotation for local sessions
    private suspend fun handleLocalSmokerRotation(
        app: CloudCounterApplication,
        repo: ActivityRepository,
        context: Context
    ) {
        try {
            val allSmokers = repo.getAllSmokersList()
            if (allSmokers.isEmpty()) {
                Log.w(TAG, "No smokers available for rotation")
                return
            }

            val currentSmokerId = app.defaultSmokerId
            val currentIndex = allSmokers.indexOfFirst { it.smokerId == currentSmokerId }

            val nextSmoker = if (currentIndex >= 0) {
                val nextIndex = (currentIndex + 1) % allSmokers.size
                allSmokers[nextIndex]
            } else {
                // Current smoker not found, use first available
                allSmokers.first()
            }

            // Update default smoker
            app.defaultSmokerId = nextSmoker.smokerId

            // Save to preferences
            val prefs = context.getSharedPreferences("sesh", Context.MODE_PRIVATE)
            prefs.edit().putLong("defaultSmokerId", nextSmoker.smokerId).apply()

            Log.d(TAG, "🔄 Rotated smoker locally to ${nextSmoker.name} (${if (nextSmoker.isCloudSmoker) "cloud" else "local"})")

            // Send broadcast to update UI
            sendSmokerUpdateBroadcast(context, nextSmoker)

        } catch (e: Exception) {
            Log.e(TAG, "Error rotating smoker locally: ${e.message}", e)
        }
    }

    // Helper function to send broadcast
    private fun sendSmokerUpdateBroadcast(context: Context, smoker: Smoker) {
        Log.d(TAG, "📡 === SENDING BROADCAST ===")
        Log.d(TAG, "📡 Smoker: ${smoker.name} (ID: ${smoker.smokerId}, cloud: ${smoker.isCloudSmoker})")

        val updateIntent = Intent("com.vibecode.cloudcounter.UPDATE_SMOKER_SELECTION").apply {
            putExtra("smokerId", smoker.smokerId)
            putExtra("smokerName", smoker.name)
            putExtra("isCloudSmoker", smoker.isCloudSmoker)
            setPackage(context.packageName) // Ensure it's delivered to our app
        }

        Log.d(TAG, "📡 Intent package: ${updateIntent.`package`}")

        // Just send the broadcast normally - the receiver registration handles the security
        context.sendBroadcast(updateIntent)
        Log.d(TAG, "📡 Broadcast sent")

        Log.d(TAG, "📡 === BROADCAST SENT ===")
    }

    // NEW: Update session stats from notification
    private fun updateSessionStatsFromNotification(
        context: Context,
        prefs: android.content.SharedPreferences,
        addedAt: Long,
        isAutoMode: Boolean
    ) {
        try {
            // Update last log time
            prefs.edit().putLong("lastLogTime", addedAt).apply()

            // Update rounds if in auto mode
            if (isAutoMode) {
                val hitsThisRound = prefs.getInt("hitsThisRound", 0) + 1
                val actualRounds = prefs.getInt("actualRounds", 0)

                prefs.edit()
                    .putInt("hitsThisRound", hitsThisRound)
                    .putInt("actualRounds", actualRounds)
                    .apply()

                Log.d(TAG, "📊 Updated session stats: hits=$hitsThisRound, rounds=$actualRounds")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating session stats: ${e.message}")
        }
    }

    private fun handleTurnNotificationAction(
        context: Context,
        intent: Intent,
        app: CloudCounterApplication,
        repo: ActivityRepository,
        sessionSyncService: SessionSyncService
    ) {
        val typeString = intent.getStringExtra(NotificationHelper.EXTRA_TYPE) ?: return
        val type = try {
            ActivityType.valueOf(typeString)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid activity type: $typeString")
            return
        }
        
        val roomCode = intent.getStringExtra("room_code")
        val turnUserSmokerId = intent.getStringExtra("turn_user_smoker_id")
        
        // Save last activity type to preferences
        val prefs = context.getSharedPreferences("turn_notifications", Context.MODE_PRIVATE)
        prefs.edit().putString("last_activity_type", type.name).apply()
        
        // Cancel the turn notification
        NotificationHelper(context).cancelTurnNotification()
        
        CoroutineScope(Dispatchers.IO).launch {
            // Find the correct smoker for the turn user
            val smoker = if (turnUserSmokerId != null) {
                // Try to find smoker by cloud user ID or local smoker ID
                repo.getSmokerByCloudUserId(turnUserSmokerId) 
                    ?: repo.getAllSmokersList().firstOrNull { "local_${it.uid}" == turnUserSmokerId }
                    ?: repo.getSmokerById(app.defaultSmokerId)
            } else {
                repo.getSmokerById(app.defaultSmokerId)
            } ?: return@launch
            
            val addedAt = System.currentTimeMillis()
            
            if (roomCode != null) {
                // Cloud session - add to room
                val smokerActivityUid = if (smoker.isCloudSmoker) {
                    smoker.cloudUserId!!
                } else {
                    "local_${smoker.uid}"
                }
                
                val deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: "unknown"
                
                sessionSyncService.addActivityToRoom(
                    shareCode = roomCode,
                    smokerUid = smokerActivityUid,
                    smokerName = smoker.name,
                    activityType = type,
                    timestamp = addedAt,
                    deviceId = deviceId
                ).fold(
                    onSuccess = {
                        Log.d(TAG, "✅ Added $type from turn notification")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "${type.name} added", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "❌ Failed to add from turn notification: ${error.message}")
                        // Fallback to local
                        repo.insert(ActivityLog(type = type, timestamp = addedAt, smokerId = smoker.smokerId))
                    }
                )
            } else {
                // Local session
                repo.insert(ActivityLog(type = type, timestamp = addedAt, smokerId = smoker.smokerId))
                Log.d(TAG, "✅ Added $type locally from turn notification")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "${type.name} added", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Update session stats and trigger auto-advance
            val sessionPrefs = context.getSharedPreferences("sesh", Context.MODE_PRIVATE)
            val sessionActive = sessionPrefs.getBoolean("sessionActive", false)
            val isAutoMode = sessionPrefs.getBoolean("isAutoMode", true)
            
            if (sessionActive) {
                updateSessionStatsFromNotification(context, sessionPrefs, addedAt, isAutoMode)
                
                // Send broadcast to trigger auto-advance in MainActivity
                if (isAutoMode) {
                    val autoAdvanceIntent = Intent("com.vibecode.cloudcounter.TRIGGER_AUTO_ADVANCE").apply {
                        // Remove setPackage to allow cross-app broadcast (for secure folder communication)
                        putExtra("from_notification", true)
                        putExtra("activity_type", type.name)
                        putExtra("smoker_id", smoker.smokerId)
                    }
                    context.sendBroadcast(autoAdvanceIntent)
                    Log.d(TAG, "🔄 Sent auto-advance broadcast for notification activity (type: ${type.name}, smoker: ${smoker.smokerId})")
                }
            }
        }
    }

    private suspend fun reverseGoalProgressForUndo(
        context: Context,
        activityLog: ActivityLog,
        smoker: Smoker?,
        sessionShareCode: String?
    ) {
        if (smoker == null) {
            Log.w(TAG, "🎯 Undo requested from notification but smoker information is missing")
            return
        }

        val smokerName = smoker.name

        // Try using the shared callbacks first (wired up by MainActivity when it is alive)
        val callback = CalendarViewModel.onReverseGoal ?: HistoryViewModel.onReverseGoal
        if (callback != null) {
            try {
                Log.d(TAG, "🎯 Using ViewModel callback to reverse goal progress for ${activityLog.type} by $smokerName")
                callback(activityLog, smoker, sessionShareCode)
                return
            } catch (e: Exception) {
                Log.e(TAG, "🎯 Callback reversal failed, falling back to GoalService: ${e.message}", e)
            }
        }

        // Fallback: use GoalService directly (singleton to avoid duplicate monitors)
        val application = context.applicationContext as CloudCounterApplication
        val goalService = GoalServiceHolder.get(application)

        if (activityLog.type == ActivityType.CUSTOM && !activityLog.customActivityId.isNullOrEmpty()) {
            Log.d(TAG, "🎯↩️ Reversing custom goal progress (notification undo)")
            goalService.reverseGoalProgressForSelectedActivity(
                activityType = activityLog.type,
                customActivityId = activityLog.customActivityId,
                customActivityName = activityLog.customActivityName,
                sessionShareCode = sessionShareCode,
                currentSmokerName = smokerName
            )
        } else {
            Log.d(TAG, "🎯↩️ Reversing standard goal progress (notification undo)")
            goalService.reverseGoalProgressForSelectedActivity(
                activityType = activityLog.type,
                customActivityId = null,
                customActivityName = null,
                sessionShareCode = sessionShareCode,
                currentSmokerName = smokerName
            )
            goalService.reverseGoalProgressForActivity(
                activityType = activityLog.type,
                sessionShareCode = sessionShareCode,
                smokerName = smokerName
            )
        }
    }
}

private object GoalServiceHolder {
    @Volatile
    private var goalService: GoalService? = null

    fun get(application: CloudCounterApplication): GoalService {
        return goalService ?: synchronized(this) {
            goalService ?: GoalService(application).also { goalService = it }
        }
    }
}
