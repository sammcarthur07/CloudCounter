package com.sam.cloudcounter

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * Data class for Sam's statistics
 */
data class SamStats(
    val todayCones: Int = 0,
    val todayJoints: Int = 0,
    val allTimeCones: Int = 0,
    val allTimeJoints: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Data class for Firebase-stored adjustments
 */
data class StatsAdjustments(
    @get:PropertyName("today_cones_adjustment")
    @set:PropertyName("today_cones_adjustment")
    var todayConesAdjustment: Int = 0,

    @get:PropertyName("today_joints_adjustment")
    @set:PropertyName("today_joints_adjustment")
    var todayJointsAdjustment: Int = 0,

    @get:PropertyName("alltime_cones_adjustment")
    @set:PropertyName("alltime_cones_adjustment")
    var allTimeConesAdjustment: Int = 0,

    @get:PropertyName("alltime_joints_adjustment")
    @set:PropertyName("alltime_joints_adjustment")
    var allTimeJointsAdjustment: Int = 0,

    @get:PropertyName("last_updated")
    @set:PropertyName("last_updated")
    var lastUpdated: Timestamp = Timestamp.now()
)

/**
 * Cache structure for efficient stats loading
 */
data class StatsCache(
    val todayCones: Int = 0,
    val todayJoints: Int = 0,
    val allTimeCones: Int = 0,
    val allTimeJoints: Int = 0,
    val lastSyncTimestamp: Long = 0,
    val todayDate: String = "",
    val cacheVersion: Int = 1
)