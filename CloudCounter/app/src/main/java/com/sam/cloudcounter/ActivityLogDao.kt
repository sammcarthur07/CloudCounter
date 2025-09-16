package com.sam.cloudcounter

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ActivityLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ActivityLog): Long

    @Insert
    suspend fun insertAll(activities: List<ActivityLog>): List<Long>

    @Update
    suspend fun update(log: ActivityLog)

    @Delete
    suspend fun delete(log: ActivityLog)

    @Query("SELECT * FROM activity_logs WHERE smokerId = :smokerId AND timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getLogsForSmokerInTimeRange(smokerId: Long, startTime: Long, endTime: Long): List<ActivityLog>

    @Query("SELECT SUM(CASE WHEN type = 'BOWL' THEN bowlQuantity ELSE 1 END) FROM activity_logs WHERE smokerId = :smokerId AND timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getTotalActivityCountForSmoker(smokerId: Long, startTime: Long, endTime: Long): Int

    @Query("SELECT SUM(bowlQuantity) FROM activity_logs WHERE type = 'BOWL' AND timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getTotalBowlsInTimeRange(startTime: Long, endTime: Long): Int

    @Query("SELECT COALESCE(SUM(bowlQuantity), 0) FROM activity_logs WHERE smokerId = :smokerId AND type = 'BOWL'")
    suspend fun getTotalBowlQuantityForSmoker(smokerId: Long): Int

    @Query("SELECT * FROM activity_logs WHERE smokerId = :smokerId AND timestamp BETWEEN :start AND :end")
    suspend fun getLogsForSmokerBetween(smokerId: Long, start: Long, end: Long): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE smokerId = :smokerId ORDER BY timestamp DESC")
    fun getLogsForSmoker(smokerId: Long): LiveData<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs WHERE smokerId = :smokerId ORDER BY timestamp DESC")
    suspend fun getLogsForSmokerSync(smokerId: Long): List<ActivityLog>

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAllLogs(): LiveData<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs WHERE id = :id")
    suspend fun getLogById(id: Long): ActivityLog?

    @Query("SELECT * FROM activity_logs WHERE smokerId = :smokerId AND timestamp > :sinceTimestamp ORDER BY timestamp ASC")
    suspend fun getActivitiesForSmokerSince(smokerId: Long, sinceTimestamp: Long): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE smokerId IN (:smokerIds) ORDER BY timestamp DESC")
    fun getLogsForSmokersLive(smokerIds: List<Long>): LiveData<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs WHERE smokerId = :smokerId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastActivityForSmoker(smokerId: Long): ActivityLog?

    @Query("SELECT * FROM activity_logs WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastLogByType(type: ActivityType): ActivityLog?

    @Query("SELECT COUNT(*) FROM activity_logs WHERE smokerId = :smokerId AND type = :type")
    suspend fun countActivitiesForSmokerByType(smokerId: Long, type: ActivityType): Int

    @Query("SELECT COUNT(*) FROM activity_logs WHERE type = 'CONE' AND timestamp > :startTime AND timestamp < :endTime")
    suspend fun countConesBetweenTimestamps(startTime: Long, endTime: Long): Int

    @Query("""
        SELECT COUNT(*) 
          FROM activity_logs 
         WHERE type = 'CONE' 
           AND smokerId = :smokerId 
           AND timestamp > :startTime 
           AND timestamp < :endTime
    """)
    suspend fun countConesBetweenTimestampsForSmoker(
        smokerId: Long,
        startTime: Long,
        endTime: Long
    ): Int

    @Query("SELECT * FROM activity_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getLogsBetweenTimestamps(startTime: Long, endTime: Long): List<ActivityLog>

    @Query("""
    SELECT * FROM activity_logs 
    WHERE type = :bowlType AND associatedConesCount IS NOT NULL AND associatedConesCount > 0
    ORDER BY timestamp DESC 
    LIMIT :limit
""")
    suspend fun getRecentBowlsWithCones(
        bowlType: ActivityType,
        limit: Int
    ): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    suspend fun getLogsAfterTimestamp(startTime: Long): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE type = :activityType AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getLogsByTypeInPeriod(activityType: ActivityType, startTime: Long, endTime: Long): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getLogsBeforeTimestamp(endTime: Long): List<ActivityLog>

    @Query("SELECT * FROM activity_logs ORDER BY timestamp ASC")
    suspend fun getAllLogsSync(): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE smokerId = :smokerId AND type = :type AND timestamp = :timestamp LIMIT 1")
    suspend fun getActivityLogIfExists(smokerId: Long, type: ActivityType, timestamp: Long): ActivityLog?

    @Query("SELECT * FROM activity_logs WHERE smokerId = :smokerId AND type = :type AND timestamp = :timestamp LIMIT 1")
    suspend fun findLogByDetails(smokerId: Long, type: ActivityType, timestamp: Long): ActivityLog?

    @Query("SELECT * FROM activity_logs WHERE smokerId IN (:smokerIds) AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getLogsForSmokersInTimeRange(smokerIds: List<Long>, startTime: Long, endTime: Long): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getActivitiesBySessionId(sessionId: Long): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE smokerId IN (:smokerIds) AND timestamp >= :startTime ORDER BY timestamp ASC")
    suspend fun getLogsForSmokersAfterTimestamp(smokerIds: List<Long>, startTime: Long): List<ActivityLog>

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentActivities(limit: Int): List<ActivityLog>

    @Query("SELECT DISTINCT sessionId FROM activity_logs WHERE sessionId IS NOT NULL ORDER BY sessionId DESC LIMIT :limit")
    suspend fun getDistinctSessionIds(limit: Int): List<Long>

    @Query("UPDATE activity_logs SET sessionId = :sessionId WHERE timestamp >= :startTime AND timestamp <= :endTime AND sessionId IS NULL")
    suspend fun updateSessionIdsForTimeRange(sessionId: Long, startTime: Long, endTime: Long)

    @Query("""
        SELECT * FROM activity_logs 
        WHERE sessionId = :sessionId 
        ORDER BY timestamp ASC
    """)
    suspend fun getLogsForSession(sessionId: Long): List<ActivityLog>

    @Query("""
        SELECT * FROM activity_logs 
        WHERE sessionStartTime >= :sessionStart 
        AND sessionStartTime <= :sessionEnd
        ORDER BY timestamp ASC
    """)
    suspend fun getLogsForSessionTimeRange(sessionStart: Long, sessionEnd: Long): List<ActivityLog>

    @Query("""
        SELECT * FROM activity_logs 
        WHERE timestamp BETWEEN :startTime AND :endTime
        AND payerStashOwnerId IS NULL
        ORDER BY timestamp ASC
    """)
    suspend fun getMyStashLogsBetween(startTime: Long, endTime: Long): List<ActivityLog>

    @Query("""
        SELECT * FROM activity_logs 
        WHERE timestamp BETWEEN :startTime AND :endTime
        AND payerStashOwnerId IS NOT NULL AND payerStashOwnerId != :currentUserId
        ORDER BY timestamp ASC
    """)
    suspend fun getTheirStashLogsBetween(startTime: Long, endTime: Long, currentUserId: String): List<ActivityLog>

    @Query("""
        SELECT * FROM activity_logs 
        WHERE timestamp BETWEEN :startTime AND :endTime
        AND consumerId IN (SELECT smokerId FROM smokers WHERE cloudUserId = :currentUserId OR uid = :currentUserId)
        ORDER BY timestamp ASC
    """)
    suspend fun getOnlyMeLogsBetween(startTime: Long, endTime: Long, currentUserId: String): List<ActivityLog>


    @Query("SELECT * FROM activity_logs WHERE smokerId IN (:smokerIds) AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getLogsForSmokersBeforeTimestamp(smokerIds: List<Long>, endTime: Long): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE sessionId = :sessionId")
    suspend fun getLogsBySessionId(sessionId: Long): List<ActivityLog>

    @Query("SELECT * FROM activity_logs WHERE id = :id LIMIT 1")
    suspend fun getActivityById(id: Long): ActivityLog?

    @Query("""
        INSERT INTO activity_logs 
        (smokerId, consumerId, payerStashOwnerId, type, timestamp, sessionId, sessionStartTime, gramsAtLog, pricePerGramAtLog)
        VALUES (:smokerId, :consumerId, NULL, :type, :timestamp, :sessionId, :sessionStartTime, :gramsAtLog, :pricePerGramAtLog)
    """)
    suspend fun insertWithNullPayer(
        smokerId: Long,
        consumerId: Long,
        type: String,
        timestamp: Long,
        sessionId: Long?,
        sessionStartTime: Long?,
        gramsAtLog: Double,
        pricePerGramAtLog: Double
    )

    @Query("""
        INSERT INTO activity_logs 
        (smokerId, consumerId, payerStashOwnerId, type, timestamp, sessionId, sessionStartTime, gramsAtLog, pricePerGramAtLog)
        VALUES (:smokerId, :consumerId, :payerStashOwnerId, :type, :timestamp, :sessionId, :sessionStartTime, :gramsAtLog, :pricePerGramAtLog)
    """)
    suspend fun insertWithPayer(
        smokerId: Long,
        consumerId: Long,
        payerStashOwnerId: String,
        type: String,
        timestamp: Long,
        sessionId: Long?,
        sessionStartTime: Long?,
        gramsAtLog: Double,
        pricePerGramAtLog: Double
    )

    @Query("SELECT * FROM activity_logs WHERE smokerId IN (:smokerIds) ORDER BY timestamp ASC")
    suspend fun getLogsForSmokers(smokerIds: List<Long>): List<ActivityLog>

    @Query("""
        SELECT * 
          FROM activity_logs 
         WHERE type = 'BOWL' 
           AND timestamp < :timestamp 
         ORDER BY timestamp DESC 
         LIMIT 1
    """)
    suspend fun getLastBowlBefore(timestamp: Long): ActivityLog?

    @Query("SELECT * FROM activity_logs WHERE smokerId = :smokerId AND type = :type AND timestamp = :timestamp LIMIT 1")
    suspend fun findExisting(smokerId: Long, type: ActivityType, timestamp: Long): ActivityLog?

    @Query("""
        SELECT * FROM activity_logs
         WHERE smokerId = :smokerId
           AND type = :type
           AND ABS(timestamp - :timestamp) <= :tolerance
         LIMIT 1
    """)
    suspend fun findNearby(
        smokerId: Long,
        type: ActivityType,
        timestamp: Long,
        tolerance: Long
    ): ActivityLog?

    // ─────────────────────────────────────────────────────────────────────────────
    // Their Stash All-time totals (acts like a ledger separate from My Stash)
    // ─────────────────────────────────────────────────────────────────────────────
    @Query(
        """
        SELECT 
            COALESCE(SUM(gramsAtLog), 0.0)                    AS totalGrams,
            COALESCE(SUM(gramsAtLog * pricePerGramAtLog), 0.0) AS totalCost
        FROM activity_logs
        WHERE payerStashOwnerId = 'their_stash'
        """
    )
    suspend fun getTheirStashTotals(): TheirStashTotals?
}
