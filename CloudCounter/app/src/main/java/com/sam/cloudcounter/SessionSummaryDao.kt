package com.sam.cloudcounter

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface SessionSummaryDao {
    @Query("SELECT * FROM session_summaries ORDER BY timestamp DESC")
    fun getAllSummaries(): LiveData<List<SessionSummary>>

    @Query("SELECT * FROM session_summaries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SessionSummary?

    @Query("SELECT * FROM session_summaries")
    suspend fun getAllSummariesSync(): List<SessionSummary>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(summary: SessionSummary): Long

    @Update
    suspend fun update(summary: SessionSummary)

    @Query("SELECT * FROM session_summaries ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMostRecentSummary(): SessionSummary?


    @Delete
    suspend fun delete(summary: SessionSummary)
}