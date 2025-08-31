package com.sam.cloudcounter

import android.util.Log
import androidx.lifecycle.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

/**
 * Repository that provides a unified interface to ActivityLog, Smoker,
 * and SessionSummary data sources.
 */
class ActivityRepository(
    private val activityLogDao: ActivityLogDao,
    private val smokerDao: SmokerDao,
    private val summaryDao: SessionSummaryDao,
    private val stashDao: StashDao
) {

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
            ActivityType.SESSION_SUMMARY -> log // Session summaries don't have consumption
        }

        activityLogDao.insert(logWithRatio)
    }

    suspend fun insert(log: ActivityLog): Long = withContext(Dispatchers.IO) {
        // If gramsAtLog and pricePerGramAtLog are already set, use them directly
        // Otherwise, fetch current values
        if (log.gramsAtLog > 0 && log.pricePerGramAtLog > 0) {
            activityLogDao.insert(log)
        } else {
            insertWithRatio(log)
        }
    }

    suspend fun delete(log: ActivityLog) =
        activityLogDao.delete(log)

    suspend fun getLastLogByType(type: ActivityType): ActivityLog? =
        activityLogDao.getLastLogByType(type)

    suspend fun getLastActivityForSmoker(smokerId: Long): ActivityLog? =
        activityLogDao.getLastActivityForSmoker(smokerId)

    suspend fun getActivitiesBySessionId(sessionId: Long): List<ActivityLog> {
        return activityLogDao.getActivitiesBySessionId(sessionId)
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
            summaryDao.insert(summary)
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

    suspend fun deleteSummary(summary: SessionSummary) =
        summaryDao.delete(summary)

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