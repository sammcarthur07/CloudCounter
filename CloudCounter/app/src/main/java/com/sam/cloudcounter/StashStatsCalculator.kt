package com.sam.cloudcounter

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.roundToInt

class StashStatsCalculator(
    private val activityLogDao: ActivityLogDao,
    private val stashDao: StashDao,
    private val smokerDao: SmokerDao,
    private val summaryDao: SessionSummaryDao
) {
    companion object {
        private const val TAG = "STASH_CALC"
        const val MINUTE_MS = 60 * 1000L
        const val HOUR_MS = 60 * MINUTE_MS
        const val DAY_MS = 24 * HOUR_MS
        const val WEEK_MS = 7 * DAY_MS
        const val MONTH_MS = 30 * DAY_MS
        const val YEAR_MS = 365 * DAY_MS
    }

    enum class StatsType {
        PAST, CURRENT, PROJECTED
    }

    enum class DataScope {
        MY_STASH,
        THEIR_STASH,
        ONLY_ME
    }

    data class StashStats(
        val statsType: StatsType,
        val timePeriod: StashTimePeriod,
        val dataScope: DataScope,
        val windowStartMs: Long,
        val windowEndMs: Long,
        val counts: Map<ActivityType, Int>,
        val grams: Map<ActivityType, Double>,
        val costs: Map<ActivityType, Double> = emptyMap(),  // NEW: Add costs by activity type
        val totalGrams: Double,
        val totalCost: Double,
        val projectionScale: Double? = null,
        val isUsingSnapshot: Boolean = true,
        val distributions: List<StashDistribution>? = null
    )

    suspend fun calculate(
        statsType: StatsType,
        timePeriod: StashTimePeriod,
        dataScope: DataScope,
        sessionStartTime: Long? = null,
        lastCompletedSessionId: Long? = null,
        currentUserId: String? = null
    ): StashStats {
        val userId = currentUserId ?: "default_user"

        Log.d(TAG, "")
        Log.d(TAG, "════════════════════════════════════════════════════════════════")
        Log.d(TAG, "📊 STASH STATS CALCULATION START")
        Log.d(TAG, "════════════════════════════════════════════════════════════════")
        Log.d(TAG, "Input Parameters:")
        Log.d(TAG, "  Stats Type: $statsType")
        Log.d(TAG, "  Time Period: $timePeriod")
        Log.d(TAG, "  Data Scope: $dataScope")
        Log.d(TAG, "  Session Start Time: $sessionStartTime ${sessionStartTime?.let { "(${java.util.Date(it)})" } ?: ""}")
        Log.d(TAG, "  Last Completed Session ID: $lastCompletedSessionId ${lastCompletedSessionId?.let { "(${java.util.Date(it)})" } ?: ""}")
        Log.d(TAG, "  Current User ID: $userId")
        Log.d(TAG, "────────────────────────────────────────────────────────────────")

        try {
            if (timePeriod == StashTimePeriod.THIS_SESH) {
                Log.d(TAG, "📌 SESSION STATS REQUESTED")
                val result = calculateSessionStats(statsType, dataScope, sessionStartTime, lastCompletedSessionId, userId)
                Log.d(TAG, "📊 SESSION RESULT: ${result.counts[ActivityType.CONE] ?: 0}C ${result.counts[ActivityType.BOWL] ?: 0}B = ${result.totalGrams}g")
                Log.d(TAG, "════════════════════════════════════════════════════════════════")
                return result
            }

            val now = System.currentTimeMillis()
            val (windowStart, windowEnd) = calculateTimeWindow(statsType, timePeriod, now)
            Log.d(TAG, "📅 Time Window Calculated:")
            Log.d(TAG, "  Start: $windowStart (${java.util.Date(windowStart)})")
            Log.d(TAG, "  End: $windowEnd (${java.util.Date(windowEnd)})")
            Log.d(TAG, "  Duration: ${(windowEnd - windowStart) / (1000 * 60)} minutes")

            Log.d(TAG, "🔍 Fetching activities from database...")
            val activities = activityLogDao.getLogsBetweenTimestamps(windowStart, windowEnd)
            Log.d(TAG, "  Raw activities found: ${activities.size}")

            Log.d(TAG, "🔧 Applying scope filter: $dataScope")
            val filtered = filterActivitiesByScope(activities, dataScope, currentUserId)
            Log.d(TAG, "  Activities after filter: ${filtered.size}")

            val result = when (statsType) {
                StatsType.PAST -> {
                    Log.d(TAG, "📈 Calculating PAST stats")
                    if (filtered.isEmpty()) {
                        Log.d(TAG, "  ⚠️ No activities in PAST window")
                        emptyStats(statsType, timePeriod, dataScope, windowStart, windowEnd)
                    } else {
                        calculateActualStats(filtered, statsType, timePeriod, dataScope, windowStart, windowEnd, currentUserId)
                    }
                }
                StatsType.CURRENT -> {
                    Log.d(TAG, "📈 Calculating CURRENT stats")
                    calculateActualStats(filtered, statsType, timePeriod, dataScope, windowStart, windowEnd, currentUserId)
                }
                StatsType.PROJECTED -> {
                    Log.d(TAG, "📈 Calculating PROJECTED stats")
                    calculateProjectedStats(filtered, timePeriod, dataScope, windowStart, windowEnd, now, currentUserId)
                }
            }

            Log.d(TAG, "────────────────────────────────────────────────────────────────")
            Log.d(TAG, "📊 FINAL RESULT:")
            Log.d(TAG, "  Cones: ${result.counts[ActivityType.CONE] ?: 0}")
            Log.d(TAG, "  Bowls: ${result.counts[ActivityType.BOWL] ?: 0}")
            Log.d(TAG, "  Total Grams: ${result.totalGrams}")
            Log.d(TAG, "  Total Cost: ${result.totalCost}")
            Log.d(TAG, "  Distributions: ${result.distributions?.size ?: 0} users")
            Log.d(TAG, "════════════════════════════════════════════════════════════════")

            return result
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR in calculate: ${e.message}", e)
            Log.d(TAG, "════════════════════════════════════════════════════════════════")
            return emptyStats(statsType, timePeriod, dataScope)
        }
    }

    private suspend fun calculateSessionStats(
        statsType: StatsType,
        dataScope: DataScope,
        sessionStartTime: Long?,
        lastCompletedSessionId: Long?,
        currentUserId: String?
    ): StashStats {
        Log.d(TAG, "🎯 SESSION STATS CALCULATION")
        Log.d(TAG, "  Stats Type: $statsType")
        Log.d(TAG, "  Current Session Start: $sessionStartTime")
        Log.d(TAG, "  Last Completed Session ID: $lastCompletedSessionId")
        Log.d(TAG, "  Data Scope: $dataScope")

        return when (statsType) {
            StatsType.PAST -> {
                Log.d(TAG, "  📜 PAST SESSION REQUESTED")
                val targetSessionId = lastCompletedSessionId ?: run {
                    Log.d(TAG, "  ❌ No last completed session ID, finding previous from DB...")
                    val recentSessions = activityLogDao.getDistinctSessionIds(10)
                    if (sessionStartTime != null && sessionStartTime > 0) {
                        recentSessions.find { it != sessionStartTime }
                    } else {
                        recentSessions.firstOrNull()
                    }
                }

                if (targetSessionId == null) {
                    Log.d(TAG, "  ❌ No valid previous session found")
                    return emptyStats(statsType, StashTimePeriod.THIS_SESH, dataScope)
                }

                Log.d(TAG, "  🔍 Querying activities for session ID: $targetSessionId")
                val activities = activityLogDao.getActivitiesBySessionId(targetSessionId)
                val filtered = filterActivitiesByScope(activities, dataScope, currentUserId)
                calculateActualStats(
                    filtered, statsType, StashTimePeriod.THIS_SESH, dataScope,
                    activities.minOfOrNull { it.timestamp } ?: 0,
                    activities.maxOfOrNull { it.timestamp } ?: 0,
                    currentUserId
                )
            }

            StatsType.CURRENT -> {
                Log.d(TAG, "  📍 CURRENT SESSION REQUESTED")
                if (sessionStartTime == null || sessionStartTime == 0L) {
                    return emptyStats(statsType, StashTimePeriod.THIS_SESH, dataScope)
                }

                Log.d(TAG, "  🔍 Querying activities for current session ID: $sessionStartTime")
                var activities = activityLogDao.getActivitiesBySessionId(sessionStartTime)
                if (activities.isEmpty()) {
                    Log.d(TAG, "  🔄 Fallback: querying by time range")
                    activities = activityLogDao.getLogsBetweenTimestamps(sessionStartTime, System.currentTimeMillis())
                }
                val filtered = filterActivitiesByScope(activities, dataScope, currentUserId)
                calculateActualStats(
                    filtered, statsType, StashTimePeriod.THIS_SESH, dataScope,
                    sessionStartTime, System.currentTimeMillis(), currentUserId
                )
            }

            StatsType.PROJECTED -> {
                Log.d(TAG, "  📊 PROJECTED SESSION REQUESTED")

                // FIX: For session projection, show the maximum of current session or last completed session
                // Get current session stats
                val currentStats = calculateSessionStats(StatsType.CURRENT, dataScope, sessionStartTime, null, currentUserId)

                // Get past session stats
                val pastStats = calculateSessionStats(StatsType.PAST, dataScope, sessionStartTime, lastCompletedSessionId, currentUserId)

                Log.d(TAG, "  📊 Projection Logic:")
                Log.d(TAG, "    Current session total: ${currentStats.totalGrams}g")
                Log.d(TAG, "    Past session total: ${pastStats.totalGrams}g")

                // Return whichever is higher
                if (pastStats.totalGrams > currentStats.totalGrams) {
                    Log.d(TAG, "    Using PAST session (higher)")
                    // Return past stats but marked as PROJECTED
                    return pastStats.copy(
                        statsType = StatsType.PROJECTED,
                        projectionScale = if (currentStats.totalGrams > 0) {
                            pastStats.totalGrams / currentStats.totalGrams
                        } else {
                            1.0
                        }
                    )
                } else {
                    Log.d(TAG, "    Using CURRENT session (higher or equal)")
                    // Return current stats marked as PROJECTED
                    return currentStats.copy(
                        statsType = StatsType.PROJECTED,
                        projectionScale = 1.0
                    )
                }
            }
        }
    }

    private suspend fun filterActivitiesByScope(
        activities: List<ActivityLog>,
        dataScope: DataScope,
        currentUserId: String?
    ): List<ActivityLog> {
        if (activities.isEmpty()) return emptyList()

        Log.d(TAG, ">>> FILTERING ${activities.size} ACTIVITIES | Scope: $dataScope | User: '$currentUserId'")

        // DEBUG: Log the payerStashOwnerId values we're seeing
        val payerIds = activities.map { it.payerStashOwnerId }.distinct()
        Log.d(TAG, "    Unique payerStashOwnerIds in activities: $payerIds")

        return activities.filter { activity ->
            val keep = when (dataScope) {
                DataScope.MY_STASH -> {
                    // MY_STASH: Activities where I paid (null) or explicitly me
                    activity.payerStashOwnerId == null || activity.payerStashOwnerId == currentUserId
                }
                DataScope.THEIR_STASH -> {
                    // THEIR_STASH: Activities marked as "their_stash"
                    val isTheirStash = activity.payerStashOwnerId == "their_stash"
                    if (isTheirStash) {
                        Log.d(TAG, "    ✓ Found THEIR_STASH activity: ${activity.type} at ${activity.timestamp}")
                    }
                    isTheirStash
                }
                DataScope.ONLY_ME -> {
                    // ONLY_ME: Activities where I was the consumer
                    val consumer = smokerDao.getSmokerById(activity.effectiveConsumerId)
                    val isMe = consumer?.cloudUserId == currentUserId || consumer?.uid == currentUserId
                    if (isMe) {
                        Log.d(TAG, "    ✓ Found ONLY_ME activity: ${activity.type} by ${consumer?.name}")
                    }
                    isMe
                }
            }
            keep
        }
    }

    // In StashStatsCalculator.kt, replace these two functions:

    // 1. Fix calculateTimeWindow to use NOW as the end point for PAST calculations
    private fun calculateTimeWindow(
        statsType: StatsType,
        timePeriod: StashTimePeriod,
        now: Long
    ): Pair<Long, Long> {
        Log.d(TAG, "⏰ CALCULATING TIME WINDOW | Type: $statsType, Period: $timePeriod")

        if (statsType == StatsType.PAST) {
            val endOfLastPeriod: Long = now  // FIX: Use NOW as the end point, not start of today
            val startOfLastPeriod: Long

            when (timePeriod) {
                StashTimePeriod.TODAY -> {
                    // PAST + TODAY = Yesterday (24 hours before now)
                    startOfLastPeriod = now - DAY_MS
                    Log.d(TAG, "  PAST + TODAY: Yesterday from ${java.util.Date(startOfLastPeriod)} to ${java.util.Date(endOfLastPeriod)}")
                }
                StashTimePeriod.WEEK -> {
                    // PAST + WEEK = Previous 7 days before now
                    startOfLastPeriod = now - WEEK_MS
                    Log.d(TAG, "  PAST + WEEK: Last 7 days from ${java.util.Date(startOfLastPeriod)} to ${java.util.Date(endOfLastPeriod)}")
                }
                StashTimePeriod.MONTH -> {
                    // PAST + MONTH = Previous 30 days before now
                    startOfLastPeriod = now - MONTH_MS
                    Log.d(TAG, "  PAST + MONTH: Last 30 days from ${java.util.Date(startOfLastPeriod)} to ${java.util.Date(endOfLastPeriod)}")
                }
                StashTimePeriod.YEAR -> {
                    // PAST + YEAR = Previous 365 days before now
                    startOfLastPeriod = now - YEAR_MS
                    Log.d(TAG, "  PAST + YEAR: Last 365 days from ${java.util.Date(startOfLastPeriod)} to ${java.util.Date(endOfLastPeriod)}")
                }
                StashTimePeriod.HOUR -> {
                    // PAST + HOUR = Previous hour before now
                    startOfLastPeriod = now - HOUR_MS
                    Log.d(TAG, "  PAST + HOUR: Last hour from ${java.util.Date(startOfLastPeriod)} to ${java.util.Date(endOfLastPeriod)}")
                }
                StashTimePeriod.TWELVE_H -> {
                    // PAST + 12H = Previous 12 hours before now
                    startOfLastPeriod = now - (12 * HOUR_MS)
                    Log.d(TAG, "  PAST + 12H: Last 12 hours from ${java.util.Date(startOfLastPeriod)} to ${java.util.Date(endOfLastPeriod)}")
                }
                else -> return Pair(0L, 0L)
            }
            return Pair(startOfLastPeriod, endOfLastPeriod)
        }

        // For CURRENT and PROJECTED, keep existing logic
        val startTime = when (timePeriod) {
            StashTimePeriod.TODAY -> getStartOfDay(now)
            StashTimePeriod.HOUR -> now - HOUR_MS
            StashTimePeriod.TWELVE_H -> now - 12 * HOUR_MS
            StashTimePeriod.WEEK -> now - WEEK_MS
            StashTimePeriod.MONTH -> now - MONTH_MS
            StashTimePeriod.YEAR -> now - YEAR_MS
            else -> now
        }
        return Pair(startTime, now)
    }

    private suspend fun calculateActualStats(
        activities: List<ActivityLog>,
        statsType: StatsType,
        timePeriod: StashTimePeriod,
        dataScope: DataScope,
        windowStart: Long,
        windowEnd: Long,
        currentUserId: String?
    ): StashStats {
        if (activities.isEmpty()) {
            return emptyStats(statsType, timePeriod, dataScope, windowStart, windowEnd)
        }

        Log.d(TAG, ">>> CALCULATING ACTUAL STATS for ${activities.size} activities")
        val counts = mutableMapOf<ActivityType, Int>()
        val grams = mutableMapOf<ActivityType, Double>()  // FIX: Track grams by activity type
        val costs = mutableMapOf<ActivityType, Double>()  // FIX: Track costs by activity type

        var totalGrams = 0.0
        var totalCost = 0.0
        val userStats = mutableMapOf<Long, UserActivityStats>()

        activities.forEach { activity ->
            // Update counts
            counts[activity.type] = (counts[activity.type] ?: 0) + 1

            // FIX: Update grams and costs by activity type
            grams[activity.type] = (grams[activity.type] ?: 0.0) + activity.gramsAtLog
            costs[activity.type] = (costs[activity.type] ?: 0.0) + activity.cost

            // Update totals
            totalGrams += activity.gramsAtLog
            totalCost += activity.cost

            // Update per-user stats
            val smokerId = activity.effectiveConsumerId
            val stats = userStats.getOrPut(smokerId) { UserActivityStats(smokerId) }
            userStats[smokerId] = stats.copy(
                cones = if (activity.type == ActivityType.CONE) stats.cones + 1 else stats.cones,
                joints = if (activity.type == ActivityType.JOINT) stats.joints + 1 else stats.joints,
                bowls = if (activity.type == ActivityType.BOWL) stats.bowls + 1 else stats.bowls,
                totalGrams = stats.totalGrams + activity.gramsAtLog,
                totalCost = stats.totalCost + activity.cost
            )
        }

        val distributions = userStats.values.map { stats ->
            val smoker = smokerDao.getSmokerById(stats.smokerId)
            StashDistribution(
                smokerName = smoker?.name ?: "Unknown",
                cones = stats.cones,
                joints = stats.joints,
                bowls = stats.bowls,
                totalGrams = stats.totalGrams,
                totalCost = stats.totalCost,
                percentage = if (totalGrams > 0) (stats.totalGrams / totalGrams * 100) else 0.0
            )
        }.sortedByDescending { it.totalGrams }

        // Log the individual costs for debugging
        Log.d(TAG, "  Activity costs breakdown:")
        costs.forEach { (type, cost) ->
            Log.d(TAG, "    $type: ${counts[type] ?: 0} units = ${grams[type] ?: 0.0}g = \$${"%.2f".format(cost)}")
        }

        return StashStats(
            statsType = statsType,
            timePeriod = timePeriod,
            dataScope = dataScope,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            counts = counts,
            grams = grams,  // FIX: Now includes actual grams by type
            totalGrams = totalGrams,
            totalCost = totalCost,
            distributions = distributions,
            costs = costs  // Add this new field to StashStats data class
        )
    }

    // In StashStatsCalculator.kt, replace the calculateProjectedStats function with this fixed version:

    private suspend fun calculateProjectedStats(
        activities: List<ActivityLog>,
        timePeriod: StashTimePeriod,
        dataScope: DataScope,
        windowStart: Long,
        windowEnd: Long,
        now: Long,
        currentUserId: String?
    ): StashStats {
        // ALWAYS use fresh current time for projections, not the passed-in value
        val currentTimeNow = System.currentTimeMillis()
        Log.d(TAG, "  PROJECTION TIME: Using fresh timestamp: ${java.util.Date(currentTimeNow)} (passed: ${java.util.Date(now)})")
        
        val actualStats = calculateActualStats(activities, StatsType.CURRENT, timePeriod, dataScope, windowStart, windowEnd, currentUserId)

        // Need at least 2 data points to project
        if (activities.size < 2) {
            Log.d(TAG, "  Not enough activities (${activities.size}) to project. Returning CURRENT.")
            return actualStats.copy(statsType = StatsType.PROJECTED, projectionScale = 1.0)
        }

        val firstActivityTime = activities.minOf { it.timestamp }
        val lastActivityTime = activities.maxOf { it.timestamp }
        val activeDuration = lastActivityTime - firstActivityTime

        // Don't project if total activity is less than a minute
        if (activeDuration < MINUTE_MS) {
            Log.d(TAG, "  Active duration (${activeDuration/1000}s) is too short to project. Returning CURRENT.")
            return actualStats.copy(statsType = StatsType.PROJECTED, projectionScale = 1.0)
        }

        val scale = when(timePeriod) {
            StashTimePeriod.TODAY -> {
                // FIX: Calculate projection differently for TODAY
                val startOfDay = getStartOfDay(currentTimeNow)
                val endOfDay = startOfDay + DAY_MS  // Midnight tonight
                val timeRemainingToday = endOfDay - currentTimeNow  // Time from now until midnight

                Log.d(TAG, "  TODAY Projection Debug:")
                Log.d(TAG, "    Current time: ${java.util.Date(currentTimeNow)}")
                Log.d(TAG, "    End of day: ${java.util.Date(endOfDay)}")
                Log.d(TAG, "    Time remaining today: ${String.format("%.4f", timeRemainingToday / HOUR_MS.toDouble())} hours")

                // Calculate the consumption rate based on recent activity
                val consumptionRate = if (activeDuration > 0) {
                    actualStats.totalGrams / (activeDuration.toDouble() / HOUR_MS)  // grams per hour
                } else {
                    0.0
                }

                Log.d(TAG, "    Consumption rate: ${consumptionRate} g/hour")
                Log.d(TAG, "    Active duration: ${activeDuration / MINUTE_MS} minutes")

                if (consumptionRate > 0) {
                    // Project: current consumption + (rate * hours remaining)
                    val projectedAdditionalGrams = consumptionRate * (timeRemainingToday.toDouble() / HOUR_MS)
                    val projectedTotalGrams = actualStats.totalGrams + projectedAdditionalGrams

                    // Scale is projected total / current total
                    val projectionScale = if (actualStats.totalGrams > 0) {
                        projectedTotalGrams / actualStats.totalGrams
                    } else {
                        1.0
                    }

                    Log.d(TAG, "    Current grams: ${String.format("%.3f", actualStats.totalGrams)}")
                    Log.d(TAG, "    Projected additional: ${String.format("%.3f", projectedAdditionalGrams)}")
                    Log.d(TAG, "    Projected total: ${String.format("%.3f", projectedTotalGrams)}")
                    Log.d(TAG, "    Projection scale: ${String.format("%.6f", projectionScale)}")
                    Log.d(TAG, "    Time-based change: Every 3 seconds, remaining time decreases by 0.0008333 hours")

                    projectionScale
                } else {
                    1.0
                }
            }

            StashTimePeriod.HOUR -> {
                // For HOUR projection, project to the end of the current hour
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = currentTimeNow }
                val minutesPassed = cal.get(java.util.Calendar.MINUTE)
                val minutesRemaining = 60 - minutesPassed

                if (minutesPassed > 0 && activeDuration > 0) {
                    // Calculate rate and project to end of hour
                    val consumptionRate = actualStats.totalGrams / (activeDuration.toDouble() / MINUTE_MS)
                    val projectedAdditional = consumptionRate * minutesRemaining
                    val projectedTotal = actualStats.totalGrams + projectedAdditional

                    if (actualStats.totalGrams > 0) {
                        projectedTotal / actualStats.totalGrams
                    } else {
                        1.0
                    }
                } else {
                    1.0
                }
            }

            StashTimePeriod.TWELVE_H -> {
                // For 12H projection, project to the end of the 12-hour period
                val twelveHoursAgo = currentTimeNow - (12 * HOUR_MS)
                val elapsedIn12H = currentTimeNow - kotlin.math.max(firstActivityTime, twelveHoursAgo)
                val remainingIn12H = (12 * HOUR_MS) - elapsedIn12H

                if (elapsedIn12H > 0 && activeDuration > 0 && remainingIn12H > 0) {
                    val consumptionRate = actualStats.totalGrams / (activeDuration.toDouble() / HOUR_MS)
                    val projectedAdditional = consumptionRate * (remainingIn12H.toDouble() / HOUR_MS)
                    val projectedTotal = actualStats.totalGrams + projectedAdditional

                    if (actualStats.totalGrams > 0) {
                        projectedTotal / actualStats.totalGrams
                    } else {
                        1.0
                    }
                } else {
                    1.0
                }
            }

            else -> {
                // For WEEK, MONTH, YEAR - recalculate window with fresh time
                val freshWindowEnd = when(timePeriod) {
                    StashTimePeriod.WEEK -> currentTimeNow
                    StashTimePeriod.MONTH -> currentTimeNow
                    StashTimePeriod.YEAR -> currentTimeNow
                    else -> currentTimeNow
                }
                val totalDuration = freshWindowEnd - windowStart
                
                Log.d(TAG, "    ${timePeriod} Projection: Window ${windowStart} to ${freshWindowEnd}")
                Log.d(TAG, "    Total duration: ${totalDuration / HOUR_MS} hours")
                Log.d(TAG, "    Active duration: ${activeDuration / HOUR_MS} hours")
                
                if (activeDuration > 0) totalDuration.toDouble() / activeDuration else 1.0
            }
        }

        val finalScale = if (scale < 1.0) 1.0 else scale
        Log.d(TAG, "    Final projection scale for $timePeriod: $finalScale")
        return projectStats(actualStats, finalScale)
    }

    private fun projectStats(stats: StashStats, scale: Double): StashStats {
        Log.d(TAG, ">>> PROJECTING STATS WITH SCALE: $scale")
        return stats.copy(
            statsType = StatsType.PROJECTED,
            counts = stats.counts.mapValues { (_, count) -> (count * scale).roundToInt() },
            grams = stats.grams.mapValues { (_, grams) -> grams * scale },  // FIX: Project grams
            costs = stats.costs.mapValues { (_, cost) -> cost * scale },    // FIX: Project costs
            totalGrams = stats.totalGrams * scale,
            totalCost = stats.totalCost * scale,
            projectionScale = scale,
            distributions = stats.distributions?.map { dist ->
                dist.copy(
                    cones = (dist.cones * scale).roundToInt(),
                    joints = (dist.joints * scale).roundToInt(),
                    bowls = (dist.bowls * scale).roundToInt(),
                    totalGrams = dist.totalGrams * scale,
                    totalCost = dist.totalCost * scale
                )
            }
        )
    }

    private fun emptyStats(
        statsType: StatsType,
        timePeriod: StashTimePeriod,
        dataScope: DataScope,
        windowStart: Long = 0,
        windowEnd: Long = 0
    ): StashStats {
        Log.d(TAG, ">>> RETURNING EMPTY STATS for window $windowStart to $windowEnd")
        return StashStats(
            statsType = statsType,
            timePeriod = timePeriod,
            dataScope = dataScope,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            counts = emptyMap(),
            grams = emptyMap(),
            totalGrams = 0.0,
            totalCost = 0.0,
            distributions = null
        )
    }

    private fun getStartOfDay(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private data class UserActivityStats(
        val smokerId: Long,
        val cones: Int = 0,
        val joints: Int = 0,
        val bowls: Int = 0,
        val totalGrams: Double = 0.0,
        val totalCost: Double = 0.0
    )
}