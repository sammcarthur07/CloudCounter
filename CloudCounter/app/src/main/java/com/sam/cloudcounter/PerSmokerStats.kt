package com.sam.cloudcounter

data class PerSmokerStats(
    val smokerName: String = "",
    val totalCones: Int = 0,
    val totalJoints: Int = 0,
    val totalBowls: Int = 0,
    val avgGapMs: Long = 0L,
    val longestGapMs: Long = 0L,
    val shortestGapMs: Long = 0L,
    val lastGapMs: Long = 0L,  // Last gap between cone activities
    val lastConeTime: Long = 0L,  // Timestamp of last cone activity
    val avgJointGapMs: Long = 0L,
    val longestJointGapMs: Long = 0L,
    val shortestJointGapMs: Long = 0L,
    val lastJointGapMs: Long = 0L,  // Last gap between joint activities
    val lastJointTime: Long = 0L,  // Timestamp of last joint activity
    val avgBowlGapMs: Long = 0L,
    val longestBowlGapMs: Long = 0L,
    val shortestBowlGapMs: Long = 0L,
    val lastBowlGapMs: Long = 0L,  // Last gap between bowl activities
    val lastBowlTime: Long = 0L,  // Timestamp of last bowl activity
    val lastActivityTime: Long = 0L,  // Timestamp of last activity (any type)
    // Custom activity stats: Map of customActivityId to stats
    val customActivityStats: Map<String, CustomActivityStat> = emptyMap()
)

data class CustomActivityStat(
    val activityName: String,
    val total: Int = 0,
    val avgGapMs: Long = 0L,
    val longestGapMs: Long = 0L,
    val shortestGapMs: Long = 0L,
    val lastGapMs: Long = 0L,
    val lastActivityTime: Long = 0L
)