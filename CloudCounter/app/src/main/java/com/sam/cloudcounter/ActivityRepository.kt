package com.sam.cloudcounter

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
    private val stashDao: StashDao
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
        // Insert locally first
        val id = if (log.gramsAtLog > 0 && log.pricePerGramAtLog > 0) {
            activityLogDao.insert(log)
        } else {
            insertWithRatio(log)
        }
        
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

    suspend fun delete(log: ActivityLog) {
        try {
            // Basic trace to catch unexpected deletions
            android.util.Log.d(
                "SeshFlow",
                "Repo.delete Activity id=${log.id}, type=${log.type}, smokerId=${log.smokerId}, ts=${log.timestamp}, sessionId=${log.sessionId}"
            )
            // Optional lightweight caller hint (best-effort)
            val st = Throwable().stackTrace
            val caller = st.getOrNull(1)
            if (caller != null) {
                android.util.Log.d("SeshFlow", "Repo.delete caller: ${caller.className}.${caller.methodName}:${caller.lineNumber}")
            }
        } catch (e: Exception) {
            // Never let logging break deletion
        }
        
        // Delete from local database
        activityLogDao.delete(log)
        
        // Delete from cloud if user is signed in
        currentUserId?.let { userId ->
            try {
                historyCloudSync.deleteActivity(userId, log.id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete activity from cloud", e)
            }
        }
    }

    suspend fun getLastLogByType(type: ActivityType): ActivityLog? =
        activityLogDao.getLastLogByType(type)

    suspend fun getLastActivityForSmoker(smokerId: Long): ActivityLog? =
        activityLogDao.getLastActivityForSmoker(smokerId)

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

    suspend fun countConesForSmokerBetween(
        smokerId: Long,
        startTime: Long,
        endTime: Long
    ): Int = activityLogDao.countConesBetweenTimestampsForSmoker(smokerId, startTime, endTime)

    suspend fun getLastBowlBefore(timestamp: Long): ActivityLog? =
        activityLogDao.getLastBowlBefore(timestamp)

    // REGION: Smoker operations

    val allSmokers: LiveData<List<Smoker>> = smokerDao.getAllSmokers()

    suspend fun insertSmoker(smoker: Smoker): Long =
        smokerDao.insert(smoker)

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
        smokerDao.upsert(smoker)
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
        currentUserId?.let { userId ->
            try {
                historyCloudSync.deleteSessionSummary(userId, summary.id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete session from cloud", e)
            }
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
