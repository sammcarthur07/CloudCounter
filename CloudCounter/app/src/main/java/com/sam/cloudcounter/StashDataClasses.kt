package com.sam.cloudcounter

import androidx.room.*
import java.util.Date

// DATA CLASS FOR QUERY RESULTS
data class ActivityCount(
    val activityType: ActivityType,
    val count: Int
)

// ENUM FOR STASH SOURCE (for attribution when logging)
enum class StashSource {
    MY_STASH, THEIR_STASH, EACH_TO_OWN
}

// ENUM FOR TIME PERIODS
enum class StashTimePeriod {
    THIS_SESH,     // Current or last session (was SESH)
    HOUR,          // Rolling 1 hour window
    TWELVE_H,      // Rolling 12 hour window
    TODAY,         // Calendar day (midnight to midnight)
    WEEK,          // Rolling 7 day window
    MONTH,         // Rolling 30 day window
    YEAR           // Rolling 365 day window
}

// DATABASE ENTITY
@Entity(tableName = "stash")
data class Stash(
    @PrimaryKey
    val id: Int = 1,
    val totalGrams: Double = 0.0,
    val currentGrams: Double = 0.0,
    val pricePerGram: Double = 0.0,
    val gramsPerBowl: Double = 0.366,
    val conesPerBowl: Double = 0.0,
    val consumeFromStash: Boolean = false,
    val lastUpdated: Date = Date()
)

// DATABASE ENTITY
@Entity(tableName = "stash_entries")
data class StashEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Date,
    val type: StashEntryType,
    val grams: Double,
    val pricePerGram: Double,
    val totalCost: Double,
    val activityType: ActivityType? = null,
    val smokerName: String? = null,
    val notes: String? = null
)

enum class StashEntryType {
    ADD,
    CONSUME,
    ADJUST,
    REMOVE, // For manual removal
    RESET
}

// DATABASE ENTITY
@Entity(tableName = "consumption_ratios")
data class ConsumptionRatio(
    @PrimaryKey
    val id: Int = 1,
    val coneGrams: Double = 0.3,
    val jointGrams: Double = 0.2,
    val bowlGrams: Double = 0.366,
    val userDefinedConeGrams: Double? = null,
    val deductConesFromStash: Boolean = true,  // NEW: default true
    val deductJointsFromStash: Boolean = true, // NEW: default true
    val deductBowlsFromStash: Boolean = false, // NEW: default false
    val lastUpdated: Date = Date()
)

// DATABASE ENTITY - For storing stash snapshots with activities
@Entity(tableName = "stash_snapshots")
data class StashSnapshot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val activityLogId: Long,
    val timestamp: Long,
    val gramsUsed: Double,
    val pricePerGram: Double,
    val payerStashOwnerId: String? = null
)

data class StashDistribution(
    val smokerName: String,
    val smokerUid: String = "",
    val cones: Int,
    val joints: Int,
    val bowls: Int,
    val conesGiven: Int = 0,
    val jointsGiven: Int = 0,
    val bowlsGiven: Int = 0,
    val totalGrams: Double,
    val totalCost: Double,
    val totalValue: Double = 0.0,
    val percentage: Double
)