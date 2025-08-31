package com.sam.cloudcounter

data class PerSmokerStats(
    val smokerName: String = "",
    val totalCones: Int = 0,
    val totalJoints: Int = 0,
    val totalBowls: Int = 0,
    val avgGapMs: Long = 0L,
    val longestGapMs: Long = 0L,
    val shortestGapMs: Long = 0L,
    val avgJointGapMs: Long = 0L,
    val longestJointGapMs: Long = 0L,
    val shortestJointGapMs: Long = 0L,
    val avgBowlGapMs: Long = 0L,
    val longestBowlGapMs: Long = 0L,
    val shortestBowlGapMs: Long = 0L
)