package com.sam.cloudcounter

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface SmokerDao {

    @Query("SELECT * FROM smokers ORDER BY displayOrder ASC, name ASC")
    fun getAllSmokers(): LiveData<List<Smoker>>

    @Query("SELECT * FROM smokers ORDER BY displayOrder ASC, name ASC")
    suspend fun getAllSmokersList(): List<Smoker>

    @Query("SELECT * FROM smokers WHERE smokerId = :id")
    suspend fun getSmokerById(id: Long): Smoker?

    @Query("SELECT * FROM smokers WHERE name = :name LIMIT 1")
    suspend fun getSmokerByName(name: String): Smoker?

    @Query("SELECT * FROM smokers WHERE name = :name")
    suspend fun getAllSmokersByName(name: String): List<Smoker>

    @Query("SELECT * FROM smokers WHERE cloudUserId = :cloudUserId LIMIT 1")
    suspend fun getSmokerByCloudUserId(cloudUserId: String): Smoker?

    @Query("SELECT * FROM smokers WHERE shareCode = :shareCode LIMIT 1")
    suspend fun getSmokerByShareCode(shareCode: String): Smoker?

    @Query("SELECT * FROM smokers WHERE smokerId IN (:smokerIds) AND isCloudSmoker = 1")
    suspend fun getCloudSmokersByIds(smokerIds: List<Long>): List<Smoker>

    @Query("SELECT * FROM smokers WHERE uid = :uid LIMIT 1")
    suspend fun getSmokerByUid(uid: String): Smoker?

    @Query("SELECT * FROM smokers WHERE needsSync = 1")
    suspend fun getSmokersNeedingSync(): List<Smoker>

    @Insert
    suspend fun insert(smoker: Smoker): Long

    @Update
    suspend fun update(smoker: Smoker)

    @Delete
    suspend fun delete(smoker: Smoker)

    @Query("UPDATE smokers SET needsSync = 1 WHERE smokerId = :smokerId")
    suspend fun markSmokerForSync(smokerId: Long)

    @Query("UPDATE smokers SET needsSync = 0, lastSyncTime = :syncTime WHERE smokerId = :smokerId")
    suspend fun markSmokerSynced(smokerId: Long, syncTime: Long = System.currentTimeMillis())

    @Query("UPDATE smokers SET lastSyncTime = :syncTime WHERE smokerId = :smokerId")
    suspend fun updateSyncTime(smokerId: Long, syncTime: Long)

    @Query("UPDATE smokers SET isPasswordVerified = 1 WHERE smokerId = :smokerId")
    suspend fun markSmokerPasswordVerified(smokerId: Long)

    @Query("UPDATE smokers SET passwordHash = :passwordHash WHERE smokerId = :smokerId")
    suspend fun updateSmokerPassword(smokerId: Long, passwordHash: String?)

    @Query("SELECT * FROM smokers WHERE cloudUserId = :cloudId LIMIT 1")
    fun getSmokerByCloudIdSync(cloudId: String): Smoker?

    @Query("UPDATE smokers SET displayOrder = :order WHERE smokerId = :smokerId")
    suspend fun updateDisplayOrder(smokerId: Long, order: Int)
    
    @Query("SELECT MAX(displayOrder) FROM smokers")
    suspend fun getMaxDisplayOrder(): Int?

    @Transaction
    suspend fun upsert(smoker: Smoker) {
        // Check if a smoker with the same cloudUserId already exists
        // We use cloudUserId here because it's the unique identifier for cloud smokers.
        // If it's a local smoker, this will return null, and we'll insert a new one.
        val existingSmoker = if (smoker.isCloudSmoker) {
            smoker.cloudUserId?.let { getSmokerByCloudUserId(it) }
        } else {
            // We can't really "upsert" a local smoker in this context, but this handles the case
            // of inserting a brand new one.
            getSmokerByUid(smoker.uid)
        }

        if (existingSmoker == null) {
            insert(smoker)
            Log.d("SmokerDao", "Inserted new smoker: ${smoker.name} with UID ${smoker.uid}")
        } else {
            // Update the existing smoker with the new data
            update(smoker.copy(smokerId = existingSmoker.smokerId))
            Log.d("SmokerDao", "Updated existing smoker: ${smoker.name} with UID ${smoker.uid}")
        }
    }
}