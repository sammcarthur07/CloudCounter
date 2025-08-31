// SessionSummary.kt
package com.sam.cloudcounter

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_summaries")
data class SessionSummary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val smokerNames: List<String>,
    val conesPerSmoker: List<Int>,
    val totalCones: Int,
    val rounds: Int,
    val sessionLength: Long,
    val longestInterval: Long,
    val shortestInterval: Long,
    val timestamp: Long,

    // New field for live sync preference
    val liveSyncEnabled: Boolean = false,

    // ADD: Store room info for resuming
    val shareCode: String? = null,
    val roomName: String? = null
)