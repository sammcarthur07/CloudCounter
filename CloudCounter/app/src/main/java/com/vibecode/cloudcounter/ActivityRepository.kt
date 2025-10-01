package com.vibecode.cloudcounter

import android.util.Log
import androidx.lifecycle.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/**
 * Repository that provides a unified interface to ActivityLog, Smoker,
 * and SessionSummary data sources.
 */
class ActivityRepository(
    private val activityLogDao: ActivityLogDao,
    val smokerDao: SmokerDao,
    private val summaryDao: SessionSummaryDao,
    private val stashDao: StashDao,
    private val authManager: FirebaseAuthManager,
    private val context: android.content.Context? = null
) {
    
    private val historyCloudSync = HistoryCloudSync()
    private var currentUserId: String? = null
    
    companion object {
        private const val TAG = "ActivityRepository"
    }
    
    /**
     * Set the current user ID for cloud sync
     */
    fun setCurrentUserId(userId: String?) {
        currentUserId = userId
        Log.d(TAG, "🌐 Current user ID set: $userId")
    }
    
    /**
     * Sync history from cloud with pagination
     */
    suspend fun syncHistoryFromCloud(scope: CoroutineScope) {
        val userId = currentUserId ?: return
        
        Log.d(TAG, "🌐 Starting history sync from cloud for user: $userId")
        
        withContext(Dispatchers.IO) {
            try {
                // Download first page of activities
                var hasMoreActivities = true
                var downloadedActivities = 0
                
                while (hasMoreActivities) {
                    val result = if (downloadedActivities == 0) {
                        historyCloudSync.downloadActivities(userId)
                    } else {
                        historyCloudSync.getNextActivityPage(userId)
                    }
                    
                    if (result.isSuccess) {
                        val (activities, hasMore) = result.getOrThrow()
                        
                        // Insert activities into local database
                        activities.forEach { activity ->
                            // Check if activity already exists locally
                            val existing = activityLogDao.findExisting(
                                activity.smokerId,
                                activity.type,
                                activity.timestamp
                            )
                            
                            if (existing == null) {
                                activityLogDao.insert(activity)
                                Log.d(TAG, "🌐 Inserted activity from cloud: ${activity.type} at ${activity.timestamp}")
                            }
                        }
                        
                        downloadedActivities += activities.size
                        hasMoreActivities = hasMore
                        
                        Log.d(TAG, "🌐 Downloaded ${activities.size} activities (total: $downloadedActivities), hasMore: $hasMore")
                    } else {
                        Log.e(TAG, "🌐 Failed to download activities: ${result.exceptionOrNull()}")
                        hasMoreActivities = false
                    }
                }
                
                // Download first page of sessions
                var hasMoreSessions = true
                var downloadedSessions = 0
                
                while (hasMoreSessions) {
                    val result = if (downloadedSessions == 0) {
                        historyCloudSync.downloadSessionSummaries(userId)
                    } else {
                        historyCloudSync.getNextSessionPage(userId)
                    }
                    
                    if (result.isSuccess) {
                        val (sessions, hasMore) = result.getOrThrow()
                        
                        // Insert sessions into local database
                        sessions.forEach { session ->
                            // Check if session already exists locally
                            val existing = summaryDao.getById(session.id)
                            
                            if (existing == null) {
                                summaryDao.insert(session)
                                Log.d(TAG, "🌐 Inserted session from cloud: ${session.id}")
                            }
                        }
                        
                        downloadedSessions += sessions.size
                        hasMoreSessions = hasMore
                        
                        Log.d(TAG, "🌐 Downloaded ${sessions.size} sessions (total: $downloadedSessions), hasMore: $hasMore")
                    } else {
                        Log.e(TAG, "🌐 Failed to download sessions: ${result.exceptionOrNull()}")
                        hasMoreSessions = false
                    }
                }
                
                Log.d(TAG, "🌐 ✅ History sync complete: $downloadedActivities activities, $downloadedSessions sessions")
                
            } catch (e: Exception) {
                Log.e(TAG, "🌐 ❌ Error during history sync", e)
            }
        }
    }
    
    /**
     * Background sync - upload local activities not in cloud
     */
    suspend fun syncLocalToCloud() {
        val userId = currentUserId ?: return
        
        withContext(Dispatchers.IO) {
            try {
                // Get all local activities
                val localActivities = activityLogDao.getAllLogsSync()
                
                // Check what needs syncing
                val lastLocalTime = localActivities.maxOfOrNull { it.timestamp } ?: 0
                val needsSync = historyCloudSync.needsSync(userId, lastLocalTime)
                
                if (needsSync) {
                    Log.d(TAG, "🌐 Starting background sync to cloud")
                    
                    // Batch upload in chunks
                    localActivities.chunked(50).forEach { batch ->
                        historyCloudSync.batchUploadActivities(userId, batch)
                        Log.d(TAG, "🌐 Uploaded batch of ${batch.size} activities")
                    }
                    
                    // Upload all sessions
                    val localSessions = summaryDao.getAllSummariesSync()
                    localSessions.forEach { session ->
                        historyCloudSync.uploadSessionSummary(userId, session)
                    }
                    
                    Log.d(TAG, "🌐 ✅ Background sync complete")
                } else {
                    Log.d(TAG, "🌐 No sync needed - cloud is up to date")
                }
            } catch (e: Exception) {
                Log.e(TAG, "🌐 ❌ Background sync failed", e)
            }
        }
    }

    // REGION: ActivityLog operations

    // FIX: Added a new LiveData that observes all activities. This is the key to instant graph updates.
    val allActivities: LiveData<List<ActivityLog>> = activityLogDao.getAllLogs()

    val allLogs: LiveData<List<ActivityLog>> = activityLogDao.getAllLogs()

    fun getLogsForSmokerLive(smokerId: Long): LiveData<List<ActivityLog>> =
        activityLogDao.getLogsForSmoker(smokerId)

    fun getLogsForSmokersLive(smokerIds: List<Long>): LiveData<List<ActivityLog>> =
        activityLogDao.getLogsForSmokersLive(smokerIds)

    suspend fun getLogsForSmoker(smokerId: Long): List<ActivityLog> =
        activityLogDao.getLogsForSmokerSync(smokerId)

    /**
     * Insert activity log with current stash ratio
     */
    suspend fun insertWithRatio(log: ActivityLog): Long = withContext(Dispatchers.IO) {
        // Get current consumption ratios and price
        val ratios = stashDao.getConsumptionRatios()
        val stash = stashDao.getCurrentStash()

        val logWithRatio = when (log.type) {
            ActivityType.CONE -> log.copy(
                gramsAtLog = ratios?.coneGrams ?: 0.3,
                pricePerGramAtLog = stash?.pricePerGram ?: 0.0
            )
            ActivityType.JOINT -> log.copy(
                gramsAtLog = ratios?.jointGrams ?: 0.5,
                pricePerGramAtLog = stash?.pricePerGram ?: 0.0
            )
            ActivityType.BOWL -> log.copy(
                gramsAtLog = ratios?.bowlGrams ?: 0.2,
                pricePerGramAtLog = stash?.pricePerGram ?: 0.0
            )
            ActivityType.CUSTOM -> log.copy(
                gramsAtLog = 0.0, // Custom activities don't consume from stash
                pricePerGramAtLog = 0.0
            )
            ActivityType.CIGARETTE -> log.copy(
                gramsAtLog = 0.0, // Cigarettes don't consume from stash
                pricePerGramAtLog = 0.0
            )
            ActivityType.SESSION_SUMMARY -> log // Session summaries don't have consumption
        }

        activityLogDao.insert(logWithRatio)
    }

    suspend fun insert(log: ActivityLog): Long = withContext(Dispatchers.IO) {
        Log.d(TAG, "📱 === INSERT ACTIVITY ===")
        Log.d(TAG, "📱 Inserting activity: type=${log.type}, smokerId=${log.smokerId}, timestamp=${log.timestamp}")
        Log.d(TAG, "📱 Thread: ${Thread.currentThread().name}")
        
        // Insert locally first
        val id = if (log.gramsAtLog > 0 && log.pricePerGramAtLog > 0) {
            activityLogDao.insert(log)
        } else {
            insertWithRatio(log)
        }
        
        Log.d(TAG, "📱 Activity inserted with ID: $id")
        
        // Upload to cloud if user is signed in
        currentUserId?.let { userId ->
            try {
                Log.d(TAG, "🌐 Uploading activity to cloud: ${log.type}")
                val activityWithId = log.copy(id = id)
                historyCloudSync.uploadActivity(userId, activityWithId)
            } catch (e: Exception) {
                Log.e(TAG, "🌐 Failed to upload activity to cloud", e)
            }
        }
        
        id
    }

    /**
     * Delete an activity with optional goal and stash reversal callbacks
     * @param log The activity to delete
     * @param onReverseGoal Optional callback to reverse goal progress before deletion
     * @param onRestoreStash Optional callback to restore stash before deletion
     */
    suspend fun deleteWithCallbacks(
        log: ActivityLog,
        onReverseGoal: (suspend (ActivityLog, Smoker) -> Unit)? = null,
        onRestoreStash: (suspend (ActivityLog, Smoker) -> Unit)? = null
    ) {
        // Get smoker for callbacks BEFORE deletion
        val smoker = smokerDao.getSmokerById(log.smokerId)
        
        // Delete the activity FIRST so it's not included in recalculations
        delete(log)
        
        if (smoker != null) {
            // Execute goal reversal callback after deletion
            onReverseGoal?.invoke(log, smoker)
            
            // Execute stash restoration callback after deletion  
            onRestoreStash?.invoke(log, smoker)
        }
    }
    
    suspend fun delete(log: ActivityLog) {
        try {
            // Logging for debugging
            android.util.Log.d("SeshFlow", "Repo.delete Activity id=${log.id}, type=${log.type}, ts=${log.timestamp}, sessionId=${log.sessionId}")
            val st = Throwable().stackTrace
            st.getOrNull(1)?.let { caller ->
                android.util.Log.d("SeshFlow", "Repo.delete caller: ${caller.className}.${caller.methodName}:${caller.lineNumber}")
            }
        } catch (e: Exception) { /* Ignore logging errors */ }

        val userId = authManager.getCurrentUserId()
        var cloudDeleteSuccess = false // Track if the cloud operation was successful

        if (userId != null) {
            // STEP 1: Attempt to delete from the cloud FIRST
            try {
                val deleted = historyCloudSync.deleteActivity(userId, log.id)
                if (deleted) {
                    Log.d(TAG, "🌐 ✅ Deleted activity ${log.id} from personal cloud history")
                    cloudDeleteSuccess = true
                } else {
                    // If it's not found in the cloud, that's okay. It means it was never synced.
                    // We can safely treat this as a success and proceed with local deletion.
                    Log.w(TAG, "🌐 ⚠️ Activity ${log.id} not found in personal cloud, proceeding with local delete.")
                    cloudDeleteSuccess = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "🌐 ❌ Failed to delete activity ${log.id} from personal cloud. Aborting local delete.", e)
                cloudDeleteSuccess = false // Explicitly mark as failed
            }
            
            // STEP 1.5: CRITICAL - Also delete from cloud ROOM if in a session
            if (cloudDeleteSuccess && log.sessionId != null && log.sessionId != 0L && context != null) {
                // Check if we're in a cloud room session
                val prefs = context.getSharedPreferences("sesh", android.content.Context.MODE_PRIVATE)
                val currentShareCode = prefs.getString("currentShareCode", null)
                
                if (!currentShareCode.isNullOrEmpty()) {
                    try {
                        Log.d(TAG, "🌐🏠 Activity is part of session with cloud room: $currentShareCode")
                        
                        // Get the smoker to determine the UID
                        val smoker = smokerDao.getSmokerById(log.smokerId)
                        if (smoker != null) {
                            val smokerUid = if (smoker.isCloudSmoker && !smoker.cloudUserId.isNullOrEmpty()) {
                                smoker.cloudUserId
                            } else {
                                "local_${smoker.uid}"
                            }
                            
                            Log.d(TAG, "🌐🏠 Will attempt to remove from room - smokerUid: $smokerUid, type: ${log.type}, timestamp: ${log.timestamp}")
                            
                            // IMPORTANT: Add to blocked list BEFORE attempting removal
                            // This prevents the activity from being synced to the cloud if it's not there yet
                            val blockedPrefs = context.getSharedPreferences("blocked_activities", android.content.Context.MODE_PRIVATE)
                            val activityKey = "${smokerUid}_${log.type}_${log.timestamp}"
                            blockedPrefs.edit().putBoolean(activityKey, true).apply()
                            Log.d(TAG, "🚫 Added activity to permanent block list: $activityKey")
                            
                            // Now try to remove from cloud room (may fail if not synced yet)
                            val sessionSyncService = SessionSyncService(context)
                            val removeResult = sessionSyncService.removeActivityFromRoom(
                                shareCode = currentShareCode,
                                smokerUid = smokerUid,
                                activityType = log.type,
                                timestamp = log.timestamp
                            )
                            
                            removeResult.fold(
                                onSuccess = {
                                    Log.d(TAG, "🌐🏠✅ Successfully removed activity from cloud room")
                                },
                                onFailure = { error ->
                                    Log.d(TAG, "🌐🏠⚠️ Activity not in cloud room (may not have been synced): ${error.message}")
                                    // Activity stays on block list to prevent future sync
                                }
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "🌐🏠❌ Error during cloud room removal attempt", e)
                        // Don't abort - continue with local deletion
                    }
                }
            }
        } else {
            // If the user is not signed in, there's no cloud to delete from.
            // We can proceed with the local deletion.
            Log.d(TAG, "🌐 Skipping cloud deletion - user not signed in")
            cloudDeleteSuccess = true
        }

        // STEP 2: Only delete from the local database if the cloud operation succeeded.
        if (cloudDeleteSuccess) {
            Log.d(TAG, "🗑️ DELETING FROM LOCAL DATABASE: Activity ${log.id}")
            activityLogDao.delete(log)
            Log.d(TAG, "🗑️✅ DELETED FROM LOCAL DATABASE: Activity ${log.id}")
            
            // Verify deletion
            val checkDeleted = activityLogDao.getLogById(log.id)
            if (checkDeleted == null) {
                Log.d(TAG, "🗑️✅✅ VERIFIED: Activity ${log.id} is truly deleted from database")
            } else {
                Log.e(TAG, "🗑️❌❌ ERROR: Activity ${log.id} still exists in database after deletion!")
            }
        } else {
            // Optional: You could add a Toast or message here to inform the user
            // that the deletion failed due to a network error. For now, the item
            // will simply remain in the list, which is better than disappearing and reappearing.
            Log.e(TAG, "Local delete for activity ${log.id} aborted due to cloud sync failure.")
        }
    }

    suspend fun getLastLogByType(type: ActivityType): ActivityLog? =
        activityLogDao.getLastLogByType(type)

    suspend fun getLastActivityForSmoker(smokerId: Long): ActivityLog? =
        activityLogDao.getLastActivityForSmoker(smokerId)
    
    suspend fun getLastRealActivityForSmoker(smokerId: Long): ActivityLog? =
        activityLogDao.getLastRealActivityForSmoker(smokerId)

    suspend fun getActivitiesBySessionId(sessionId: Long): List<ActivityLog> {
        return activityLogDao.getActivitiesBySessionId(sessionId)
    }
    
    suspend fun getLogsBetweenTimestamps(startTime: Long, endTime: Long): List<ActivityLog> {
        return activityLogDao.getLogsBetweenTimestamps(startTime, endTime)
    }

    suspend fun updateSessionIdsForTimeRange(sessionId: Long, startTime: Long, endTime: Long) {
        activityLogDao.updateSessionIdsForTimeRange(sessionId, startTime, endTime)
    }

    suspend fun getActivityLogIfExists(smokerId: Long, type: ActivityType, timestamp: Long): ActivityLog? =
        activityLogDao.findExisting(smokerId, type, timestamp)

    suspend fun findLogByDetails(smokerId: Long, type: ActivityType, timestamp: Long): ActivityLog? =
        activityLogDao.findLogByDetails(smokerId, type, timestamp)

    suspend fun getLogsInTimeRange(startTime: Long?, endTime: Long?): List<ActivityLog> {
        return when {
            startTime != null && endTime != null -> activityLogDao.getLogsBetweenTimestamps(startTime, endTime)
            startTime != null -> activityLogDao.getLogsAfterTimestamp(startTime)
            endTime != null -> activityLogDao.getLogsBeforeTimestamp(endTime)
            else -> activityLogDao.getAllLogsSync()
        }
    }

    suspend fun getLogsForSmokersInTimeRange(
        smokerIds: List<Long>,
        startTime: Long?,
        endTime: Long?
    ): List<ActivityLog> {
        return when {
            startTime != null && endTime != null ->
                activityLogDao.getLogsForSmokersInTimeRange(smokerIds, startTime, endTime)
            startTime != null ->
                activityLogDao.getLogsForSmokersAfterTimestamp(smokerIds, startTime)
            endTime != null ->
                activityLogDao.getLogsForSmokersBeforeTimestamp(smokerIds, endTime)
            else ->
                activityLogDao.getLogsForSmokers(smokerIds)
        }
    }

    suspend fun countConesBetweenTimestamps(startTime: Long, endTime: Long): Int =
        activityLogDao.countConesBetweenTimestamps(startTime, endTime)

    suspend fun getTotalBowlsInTimeRange(startTime: Long, endTime: Long): Int =
        withContext(Dispatchers.IO) {
            activityLogDao.getTotalBowlsInTimeRange(startTime, endTime)
        }

    suspend fun countConesForSmokerBetween(
        smokerId: Long,
        startTime: Long,
        endTime: Long
    ): Int = activityLogDao.countConesBetweenTimestampsForSmoker(smokerId, startTime, endTime)

    suspend fun getLastBowlBefore(timestamp: Long): ActivityLog? =
        activityLogDao.getLastBowlBefore(timestamp)

    // REGION: Smoker operations

    val allSmokers: LiveData<List<Smoker>> = smokerDao.getAllSmokers()

    suspend fun insertSmoker(smoker: Smoker): Long {
        // Check if there's a soft-deleted smoker with the same name
        val existingDeleted = smokerDao.getSmokerByNameIncludingDeleted(smoker.name)
        
        return if (existingDeleted != null && existingDeleted.isDeleted) {
            // Reactivate the soft-deleted smoker
            Log.d("ActivityRepository", "Reactivating soft-deleted smoker: ${smoker.name}, ID: ${existingDeleted.smokerId}")
            
            // Update with new data while reactivating
            val reactivated = existingDeleted.copy(
                isDeleted = false,
                deletedAt = null,
                isCloudSmoker = smoker.isCloudSmoker,
                cloudUserId = if (smoker.isCloudSmoker) smoker.cloudUserId else existingDeleted.cloudUserId,
                shareCode = if (smoker.isCloudSmoker) smoker.shareCode else existingDeleted.shareCode,
                passwordHash = if (smoker.isCloudSmoker) smoker.passwordHash else existingDeleted.passwordHash,
                isPasswordVerified = if (smoker.isCloudSmoker) smoker.isPasswordVerified else existingDeleted.isPasswordVerified,
                isOwner = if (smoker.isCloudSmoker) smoker.isOwner else existingDeleted.isOwner,
                displayOrder = smoker.displayOrder,
                needsSync = smoker.needsSync
            )
            smokerDao.update(reactivated)
            Log.d("ActivityRepository", "Reactivated smoker ${smoker.name} with ID ${existingDeleted.smokerId}")
            
            existingDeleted.smokerId
        } else if (existingDeleted != null && !existingDeleted.isDeleted) {
            // Smoker already exists and is active - just return the existing ID
            Log.d("ActivityRepository", "Smoker ${smoker.name} already exists with ID ${existingDeleted.smokerId}")
            existingDeleted.smokerId
        } else {
            // Check active smoker limit before adding new smoker
            val activeCount = smokerDao.getActiveSmokersCount()
            if (activeCount >= 50) {
                throw IllegalStateException("Maximum 50 active smokers allowed. Please delete unused smokers first.")
            }
            val newId = smokerDao.insert(smoker)
            Log.d("ActivityRepository", "Created new smoker ${smoker.name} with ID $newId")
            newId
        }
    }

    suspend fun updateSmoker(smoker: Smoker) =
        smokerDao.update(smoker)

    suspend fun deleteSmoker(smoker: Smoker) =
        smokerDao.delete(smoker)

    suspend fun getSmokerById(id: Long): Smoker? =
        smokerDao.getSmokerById(id)

    suspend fun getSmokerByCloudUserId(cloudUserId: String): Smoker? {
        return smokerDao.getSmokerByCloudUserId(cloudUserId)
    }

    suspend fun getSmokerByShareCode(shareCode: String): Smoker? {
        return smokerDao.getSmokerByShareCode(shareCode)
    }

    fun getSmokerByCloudUserIdSync(cloudUserId: String): Smoker? {
        return runBlocking {
            smokerDao.getSmokerByCloudUserId(cloudUserId)
        }
    }

    /**
     * Get total activities count for a smoker
     */
    suspend fun getTotalActivitiesForSmoker(smokerId: Long): Int = withContext(Dispatchers.IO) {
        getLogsForSmoker(smokerId).size
    }

    /**
     * Get the last activity timestamp for a smoker
     */
    suspend fun getLastActivityTimestamp(smokerId: Long): Long? = withContext(Dispatchers.IO) {
        getLogsForSmoker(smokerId)
            .maxByOrNull { it.timestamp }
            ?.timestamp
    }

    /**
     * Get activity counts by type for a smoker
     */
    suspend fun getActivityCounts(smokerId: Long): ActivityCounts = withContext(Dispatchers.IO) {
        val logs = getLogsForSmoker(smokerId)
        ActivityCounts(
            bowls = logs.count { it.type == ActivityType.BOWL },
            joints = logs.count { it.type == ActivityType.JOINT },
            cones = logs.count { it.type == ActivityType.CONE }
        )
    }

    /**
     * Calculate historical cost for activities using stored ratios
     */
    suspend fun calculateHistoricalCost(logs: List<ActivityLog>): Double = withContext(Dispatchers.IO) {
        logs.sumOf { log ->
            val grams = log.gramsAtLog
            val price = log.pricePerGramAtLog
            grams * price
        }
    }

    suspend fun getActivityById(id: Long): ActivityLog? {
        return activityLogDao.getActivityById(id)
    }

    /**
     * Data class for activity counts
     */
    data class ActivityCounts(
        val bowls: Int,
        val joints: Int,
        val cones: Int
    )

    suspend fun getSmokerByUid(uid: String): Smoker? =
        smokerDao.getSmokerByUid(uid)

    suspend fun markSmokerForSync(smokerId: Long) =
        smokerDao.markSmokerForSync(smokerId)

    suspend fun markSmokerSynced(smokerId: Long) =
        smokerDao.markSmokerSynced(smokerId)

    suspend fun insertOrUpdateSmoker(smoker: Smoker) = withContext(Dispatchers.IO) {
        // Check if there's a soft-deleted smoker with the same name first
        val existingDeleted = smokerDao.getSmokerByNameIncludingDeleted(smoker.name)
        
        if (existingDeleted != null && existingDeleted.isDeleted) {
            // Reactivate and update the soft-deleted smoker
            Log.d("ActivityRepository", "Reactivating in upsert: ${smoker.name}, ID: ${existingDeleted.smokerId}")
            val reactivated = smoker.copy(
                smokerId = existingDeleted.smokerId,
                isDeleted = false,
                deletedAt = null
            )
            smokerDao.update(reactivated)
        } else if (existingDeleted != null && !existingDeleted.isDeleted) {
            // Update existing active smoker
            Log.d("ActivityRepository", "Updating existing smoker: ${smoker.name}, ID: ${existingDeleted.smokerId}")
            val updated = smoker.copy(smokerId = existingDeleted.smokerId)
            smokerDao.update(updated)
        } else {
            // Check active smoker limit before inserting
            val activeCount = smokerDao.getActiveSmokersCount()
            if (activeCount >= 50) {
                throw IllegalStateException("Maximum 50 active smokers allowed. Please delete unused smokers first.")
            }
            // Insert new smoker
            smokerDao.upsert(smoker)
        }
    }

    // REGION: SessionSummary operations

    val allSummaries: LiveData<List<SessionSummary>> = summaryDao.getAllSummaries()

    suspend fun insertSummary(summary: SessionSummary): Long {
        return withContext(Dispatchers.IO) {
            val id = summaryDao.insert(summary)
            
            // Upload to cloud if user is signed in
            currentUserId?.let { userId ->
                try {
                    Log.d(TAG, "🌐 Uploading session summary to cloud: ${summary.id}")
                    val summaryWithId = summary.copy(id = id)
                    historyCloudSync.uploadSessionSummary(userId, summaryWithId)
                } catch (e: Exception) {
                    Log.e(TAG, "🌐 Failed to upload session summary to cloud", e)
                }
            }
            
            id
        }
    }

    suspend fun getSummaryById(id: Long): SessionSummary? {
        return withContext(Dispatchers.IO) {
            try {
                summaryDao.getById(id)
            } catch (e: Exception) {
                Log.e("ActivityRepository", "Error getting summary by ID", e)
                null
            }
        }
    }

    suspend fun updateSummary(summary: SessionSummary) =
        summaryDao.update(summary)

    suspend fun deleteSummary(summary: SessionSummary) {
        // Delete from local database
        summaryDao.delete(summary)
        
        // Delete from cloud if user is signed in
        // FIX: Get user ID directly from authManager instead of using stored currentUserId
        val userId = authManager.getCurrentUserId()
        if (userId != null) {
            try {
                val deleted = historyCloudSync.deleteSessionSummary(userId, summary.id)
                if (deleted) {
                    Log.d(TAG, "🌐 ✅ Deleted session ${summary.id} from cloud")
                } else {
                    Log.w(TAG, "🌐 ⚠️ Session ${summary.id} not found in cloud")
                }
            } catch (e: Exception) {
                Log.e(TAG, "🌐 ❌ Failed to delete session ${summary.id} from cloud", e)
            }
        } else {
            Log.d(TAG, "🌐 Skipping cloud deletion - user not signed in")
        }
    }

    suspend fun getMostRecentSummary(): SessionSummary? {
        return withContext(Dispatchers.IO) {
            try {
                val summaries = summaryDao.getAllSummariesSync()
                summaries.maxByOrNull { it.timestamp }
            } catch (e: Exception) {
                Log.e("ActivityRepository", "Error getting most recent summary", e)
                null
            }
        }
    }

    suspend fun clearSessionLogsForSmoker(smokerId: Long, sessionStart: Long, sessionEnd: Long): Int =
        withContext(Dispatchers.IO) {
            val logs = activityLogDao.getLogsForSmokerInTimeRange(smokerId, sessionStart, sessionEnd)
            logs.forEach { log ->
                activityLogDao.delete(log)
            }
            Log.d("ActivityRepository", "Cleared ${logs.size} logs for smoker $smokerId in session")
            logs.size
        }

    suspend fun clearAllSessionLogs(sessionStart: Long, sessionEnd: Long): Int =
        withContext(Dispatchers.IO) {
            val logs = activityLogDao.getLogsBetweenTimestamps(sessionStart, sessionEnd)
            logs.forEach { log ->
                activityLogDao.delete(log)
            }
            Log.d("ActivityRepository", "Cleared ${logs.size} total logs in session")
            logs.size
        }

    suspend fun getSmokerByName(name: String): Smoker? {
        return smokerDao.getSmokerByName(name)
    }

    suspend fun getAllSmokersList(): List<Smoker> {
        return smokerDao.getAllSmokersList()
    }

    suspend fun getAllSmokersByName(name: String): List<Smoker> {
        return smokerDao.getAllSmokersByName(name)
    }
    
    suspend fun updateSmokerDisplayOrder(smokerId: Long, order: Int) {
        smokerDao.updateDisplayOrder(smokerId, order)
    }

    // REGION: Cloud sync statistics

    suspend fun getTotalConesForSmoker(smokerId: Long): Int =
        activityLogDao.countActivitiesForSmokerByType(smokerId, ActivityType.CONE)

    suspend fun getTotalJointsForSmoker(smokerId: Long): Int =
        activityLogDao.countActivitiesForSmokerByType(smokerId, ActivityType.JOINT)

    suspend fun getTotalBowlsForSmoker(smokerId: Long): Int =
        withContext(Dispatchers.IO) {
            activityLogDao.getTotalBowlQuantityForSmoker(smokerId)
        }
    fun countConesForSmoker(smokerId: Long): Int = runBlocking {
        activityLogDao.countActivitiesForSmokerByType(smokerId, ActivityType.CONE)
    }

    fun countJointsForSmoker(smokerId: Long): Int = runBlocking {
        activityLogDao.countActivitiesForSmokerByType(smokerId, ActivityType.JOINT)
    }

    fun countBowlsForSmoker(smokerId: Long): Int = runBlocking {
        activityLogDao.countActivitiesForSmokerByType(smokerId, ActivityType.BOWL)
    }

    // REGION: Room Sync Logic

    suspend fun getLocalSmokersForRoomSync(currentUserId: String): List<Smoker> = withContext(Dispatchers.IO) {
        return@withContext smokerDao.getAllSmokersList().sortedBy { it.smokerId }
    }

    suspend fun syncRoomSmokersToLocal(
        currentUserId: String,
        roomSmokers: List<RoomSmoker>
    ): List<Smoker> = withContext(Dispatchers.IO) {
        val newLocalSmokers = mutableListOf<Smoker>()
        val existingLocalSmokers = smokerDao.getAllSmokersList()

        val existingCloudSmokerMap = existingLocalSmokers
            .filter { it.isCloudSmoker && !it.cloudUserId.isNullOrEmpty() }
            .associateBy { it.cloudUserId!! }

        val existingSharedLocalSmokers = existingLocalSmokers
            .filter { !it.isCloudSmoker }
            .associateBy { it.name }

        roomSmokers.forEach { roomSmoker ->
            if (roomSmoker.originalOwner == currentUserId) {
                return@forEach
            }

            var smokerExists = false
            if (roomSmoker.isCloudSmoker) {
                smokerExists = existingCloudSmokerMap.containsKey(roomSmoker.cloudUserId)
            } else {
                smokerExists = existingSharedLocalSmokers.containsKey(roomSmoker.name)
            }

            if (!smokerExists) {
                val localSmoker = Smoker(
                    name = roomSmoker.name,
                    isCloudSmoker = roomSmoker.isCloudSmoker,
                    cloudUserId = roomSmoker.cloudUserId,
                    shareCode = roomSmoker.shareCode,
                    passwordHash = roomSmoker.passwordHash,
                    isPasswordVerified = false,
                    isOwner = false,
                    needsSync = false,
                    lastSyncTime = System.currentTimeMillis()
                )

                val newSmokerId = smokerDao.insert(localSmoker)
                newLocalSmokers.add(localSmoker.copy(smokerId = newSmokerId))
            }
        }
        return@withContext newLocalSmokers
    }

    fun getAllSmokersSync(): List<Smoker> = runBlocking {
        smokerDao.getAllSmokersList()
    }
}
