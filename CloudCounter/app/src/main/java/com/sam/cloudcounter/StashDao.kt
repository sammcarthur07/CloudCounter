package com.sam.cloudcounter

import androidx.room.*
import java.util.Date

@Dao
interface StashDao {
    @Query("SELECT * FROM stash WHERE id = 1")
    suspend fun getCurrentStash(): Stash?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStash(stash: Stash)

    @Update
    suspend fun updateStash(stash: Stash)

    @Query("SELECT * FROM stash_entries ORDER BY timestamp DESC")
    suspend fun getStashHistory(): List<StashEntry>

    @Query("SELECT * FROM stash_entries WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    suspend fun getStashEntriesForPeriod(startDate: Date, endDate: Date): List<StashEntry>

    @Query("""
        SELECT activityType as activityType, COUNT(*) as count FROM stash_entries 
        WHERE activityType IS NOT NULL AND timestamp BETWEEN :startTime AND :endTime 
        GROUP BY activityType
    """)
    suspend fun getActivityCountsForPeriod(startTime: Long, endTime: Long): List<ActivityCount>

    @Insert
    suspend fun insertStashEntry(entry: StashEntry)

    @Query("SELECT * FROM consumption_ratios WHERE id = 1")
    suspend fun getConsumptionRatios(): ConsumptionRatio?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumptionRatio(ratio: ConsumptionRatio)

    @Update
    suspend fun updateConsumptionRatio(ratio: ConsumptionRatio)

    @Query("DELETE FROM stash_entries")
    suspend fun clearStashHistory()
}