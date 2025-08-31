package com.sam.cloudcounter

data class RoomData(
    val shareCode: String = "",
    val name: String = "",
    val owner: String = "",
    val createdBy: String = "",
    val participants: List<String> = emptyList(),
    val activeParticipants: List<String> = emptyList(),
    val awayParticipants: List<String> = emptyList(),
    val pausedSmokers: List<String> = emptyList(),
    val activities: List<SessionActivity> = emptyList(),
    val currentStats: SessionStats = SessionStats(),
    val autoAddState: AutoAddState = AutoAddState(),
    val startTime: Long = System.currentTimeMillis(),
    val lastActivityTime: Long = System.currentTimeMillis(),
    val active: Boolean = true,
    val roundsCounter: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sharedSmokers: Map<String, Map<String, Any>> = emptyMap(),
    val passwordHash: String? = null, // NEW: Add password field
    val joinedUsers: List<String> = emptyList() // NEW: Track users who have successfully joined with password
) {
    // Existing safe methods...
    fun safeActivities(): List<SessionActivity> = activities
    fun safeCurrentStats(): SessionStats = currentStats
    fun safeAutoAddState(): AutoAddState = autoAddState
    fun safeAwayParticipants(): List<String> = awayParticipants
    fun safePausedSmokers(): List<String> = pausedSmokers
    fun safeSharedSmokers(): Map<String, Map<String, Any>> = sharedSmokers

    // NEW: Check if user has already joined with password
    fun hasUserJoined(userId: String): Boolean = joinedUsers.contains(userId)
}

// NEW: Smoker data that gets shared in rooms
data class RoomSmoker(
    val smokerId: String = "",        // Local smoker ID from original device
    val name: String = "",
    val originalOwner: String = "",   // User ID who originally added this smoker
    val addedAt: Long = 0L,
    val isCloudSmoker: Boolean = false,
    val cloudUserId: String? = null,
    val shareCode: String? = null,
    val passwordHash: String? = null
)

// Activity stored in room (not in separate collection)
data class SessionActivity(
    val smokerId: String = "",
    val smokerName: String = "",
    val type: String = "",         // "CONE", "JOINT", "BOWL"
    val timestamp: Long = 0L,
    val deviceId: String = ""      // Which device added it (for debugging)
)

// Auto-add state that syncs across all devices
data class AutoAddState(
    val coneAutoEnabled: Boolean = false,
    val jointAutoEnabled: Boolean = false,
    val bowlAutoEnabled: Boolean = false,
    val coneNextAutoTime: Long = 0L,
    val jointNextAutoTime: Long = 0L,
    val bowlNextAutoTime: Long = 0L,

    // Store the original gaps established when auto-add was first enabled
    val coneOriginalGap: Long = 0L,
    val jointOriginalGap: Long = 0L,
    val bowlOriginalGap: Long = 0L,

    val lastUpdated: Long = 0L
)

data class PerSmokerData(
    val smokerName: String = "",
    val totalCones: Int = 0,
    val totalJoints: Int = 0,
    val totalBowls: Int = 0,
    val avgGapMs: Long = 0,
    val longestGapMs: Long = 0,
    val shortestGapMs: Long = 0,
    val avgJointGapMs: Long = 0,
    val longestJointGapMs: Long = 0,
    val shortestJointGapMs: Long = 0,
    val avgBowlGapMs: Long = 0,
    val longestBowlGapMs: Long = 0,
    val shortestBowlGapMs: Long = 0,
    val lastActivityTime: Long = 0L
)

// Pre-calculated stats stored in room
data class SessionStats(
    val totalCones: Int = 0,
    val totalJoints: Int = 0,
    val totalBowls: Int = 0,
    val longestGapMs: Long = 0,
    val shortestGapMs: Long = 0,
    val sinceLastConeMs: Long = 0,
    val sinceLastJointMs: Long = 0,
    val sinceLastBowlMs: Long = 0,
    val perSmokerStats: Map<String, PerSmokerData> = emptyMap(),
    val lastCalculated: Long = 0L,

    val lastGapMs: Long? = null,  // ADD THIS if missing
    val previousGapMs: Long? = null,

    val lastConeSmokerName: String? = null,
    val conesSinceLastBowl: Int = 0,

    // Rounds tracking
    val totalRounds: Int = 0,
    val hitsInCurrentRound: Int = 0,
    val participantCount: Int = 0
)