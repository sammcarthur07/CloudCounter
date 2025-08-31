package com.sam.cloudcounter

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single logged activity in the database.
 * Now includes proper tracking of who consumed and who paid.
 */
@Entity(
    tableName = "activity_logs",
    indices = [
        Index(value = ["smokerId", "type", "timestamp"], unique = true),
        Index(value = ["consumerId"]),
        Index(value = ["payerStashOwnerId"]),
        Index(value = ["timestamp"]),
        Index(value = ["sessionId"])
    ]
)
data class ActivityLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Legacy field - kept for backward compatibility, represents who logged the activity
    val smokerId: Long,

    // NEW: Who actually consumed this activity (defaults to smokerId for migration)
    val consumerId: Long? = null,

    // NEW: Firebase UID of the stash owner who paid for this activity
    // null = MY_STASH (current user's stash)
    // "other_uid" = someone else's stash
    val payerStashOwnerId: String? = null,

    val type: ActivityType,
    val timestamp: Long,

    // NEW: Session tracking
    val sessionId: Long? = null,
    val sessionStartTime: Long? = null,

    var associatedConesCount: Int? = null,

    val bowlQuantity: Int = 1,

    // Snapshot values at the time of logging
    val gramsAtLog: Double = 0.0,
    val pricePerGramAtLog: Double = 0.0
) {
    // Helper property to get the effective consumer ID
    val effectiveConsumerId: Long
        get() = consumerId ?: smokerId

    // Helper property to check if this was paid by current user
    fun isPaidByUser(currentUserId: String?): Boolean {
        return payerStashOwnerId == null || payerStashOwnerId == currentUserId
    }

    // Calculate the cost of this activity
    val cost: Double
        get() = gramsAtLog * pricePerGramAtLog
}