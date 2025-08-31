package com.sam.cloudcounter

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Manages auto-add timers with three-phase behavior:
 * Phase 1: Standard countdown (on-time)
 * Phase 2: Overdue countdown (late)
 * Phase 3: Reset after activity logged
 */
class AutoAddManager(
    private val coroutineScope: CoroutineScope,
    private val onAutoAdd: (ActivityType) -> Unit,
    private val onTimerUpdate: (ActivityType, Long) -> Unit,
    private val getTimeSinceLastActivity: (ActivityType) -> Long
) {
    companion object {
        private const val TAG = "AutoAddManager"
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val AUTO_ADD_COOLDOWN_MS = 2000L
    }

    private var autoAddState: AutoAddState = AutoAddState()
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var rewindOffset = 0L

    // Track recent auto-adds to prevent double-firing
    private val recentAutoAdds = mutableMapOf<ActivityType, Long>()

    // CRITICAL: Track calculated intervals - must be set BEFORE timer starts
    private val calculatedIntervals = mutableMapOf<ActivityType, Long>()

    // Track when auto was enabled for each activity type
    private val autoEnabledTimes = mutableMapOf<ActivityType, Long>()

    // Track countdown start values for Phase 2
    private val countdownStartValues = mutableMapOf<ActivityType, Long>()

    // Track if we're in Phase 2 (overdue mode) for each activity
    private val isInOverduePhase = mutableMapOf<ActivityType, Boolean>()

    // Track the actual last activity timestamps
    private val lastActivityTimes = mutableMapOf<ActivityType, Long>()

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            val realNow = System.currentTimeMillis()
            val rewindedNow = realNow - rewindOffset
            var hasActiveTimers = false

            listOf(ActivityType.CONE, ActivityType.JOINT, ActivityType.BOWL).forEach { activityType ->
                val enabled = autoAddState.isEnabled(activityType)

                if (enabled) {
                    // CRITICAL CHECK: Don't process if we don't have an interval
                    val interval = calculatedIntervals[activityType]
                    if (interval == null || interval <= 0) {
                        Log.w(TAG, "🤖⚠️ SKIPPING_TICK: $activityType - no interval set yet!")
                        return@forEach
                    }

                    val enabledTime = autoEnabledTimes[activityType] ?: rewindedNow
                    val timeSinceLastActivity = getTimeSinceLastActivity(activityType)

                    Log.d(TAG, "🤖🔄 TIMER_TICK: $activityType")
                    Log.d(TAG, "🤖🔄   interval: ${interval}ms")
                    Log.d(TAG, "🤖🔄   timeSinceLastActivity: ${timeSinceLastActivity}ms")

                    val isOverdue = isInOverduePhase[activityType] ?: false

                    val remainingMs = if (isOverdue) {
                        // Phase 2: Counting down from the overdue start value
                        val startValue = countdownStartValues[activityType] ?: timeSinceLastActivity
                        val elapsedSinceEnabled = rewindedNow - enabledTime
                        val remaining = startValue - elapsedSinceEnabled

                        Log.d(TAG, "🤖🚀 PHASE_2_CALC: $activityType")
                        Log.d(TAG, "🤖🚀   startValue: ${startValue}ms")
                        Log.d(TAG, "🤖🚀   elapsedSinceEnabled: ${elapsedSinceEnabled}ms")
                        Log.d(TAG, "🤖🚀   remaining: ${remaining}ms")

                        remaining
                    } else {
                        // Phase 1: Standard countdown
                        val remaining = interval - timeSinceLastActivity

                        Log.d(TAG, "🤖⏳ PHASE_1_CALC: $activityType")
                        Log.d(TAG, "🤖⏳   interval: ${interval}ms")
                        Log.d(TAG, "🤖⏳   timeSinceLastActivity: ${timeSinceLastActivity}ms")
                        Log.d(TAG, "🤖⏳   remaining: ${remaining}ms")

                        remaining
                    }

                    when {
                        remainingMs > 1000 -> {
                            onTimerUpdate(activityType, remainingMs)
                            hasActiveTimers = true
                        }
                        remainingMs > 0 -> {
                            onTimerUpdate(activityType, remainingMs)
                            hasActiveTimers = true
                        }
                        remainingMs >= -1000 -> {
                            if (!hasRecentAutoAdd(activityType)) {
                                Log.d(TAG, "🤖🎯 AUTO_ADD_TRIGGERED: $activityType")
                                markRecentAutoAdd(activityType)

                                // Update the interval based on actual time passed
                                val actualInterval = if (isOverdue) {
                                    countdownStartValues[activityType] ?: timeSinceLastActivity
                                } else {
                                    timeSinceLastActivity
                                }
                                calculatedIntervals[activityType] = actualInterval
                                Log.d(TAG, "🤖🔄 NEW_INTERVAL_SET: $activityType = ${actualInterval}ms")

                                // Reset phase tracking
                                isInOverduePhase[activityType] = false
                                countdownStartValues.remove(activityType)
                                autoEnabledTimes[activityType] = rewindedNow

                                onAutoAdd(activityType)
                            }
                        }
                        else -> {
                            Log.w(TAG, "🤖⚠️ OVERDUE: $activityType is way overdue (${remainingMs}ms)")
                            onTimerUpdate(activityType, 0)
                        }
                    }
                }
            }

            if (isRunning) {
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }

    // CRITICAL FIX: This should ONLY update state, not start timers without intervals
    fun updateAutoAddState(newState: AutoAddState) {
        val oldState = autoAddState
        autoAddState = newState

        val rewindedNow = System.currentTimeMillis() - rewindOffset

        Log.d(TAG, "🤖📊 STATE_UPDATE at $rewindedNow:")
        Log.d(TAG, "🤖📊   Cone: enabled=${newState.coneAutoEnabled}")
        Log.d(TAG, "🤖📊   Joint: enabled=${newState.jointAutoEnabled}")
        Log.d(TAG, "🤖📊   Bowl: enabled=${newState.bowlAutoEnabled}")

        // Check each activity type
        listOf(ActivityType.CONE, ActivityType.JOINT, ActivityType.BOWL).forEach { activityType ->
            val wasEnabled = oldState.isEnabled(activityType)
            val isNowEnabled = newState.isEnabled(activityType)

            if (!wasEnabled && isNowEnabled) {
                // Just enabled
                Log.d(TAG, "🤖✅ ENABLED: $activityType")

                // CRITICAL: Check if we have interval data
                val hasInterval = calculatedIntervals[activityType] != null && calculatedIntervals[activityType]!! > 0

                if (!hasInterval) {
                    Log.w(TAG, "🤖⚠️ WARNING: $activityType enabled but NO INTERVAL SET - timer will not start!")
                    // Don't start the timer! Wait for enableAutoAddWithPhaseDetection to be called
                    return@forEach
                }

                // We have interval, so we can record the enable time
                autoEnabledTimes[activityType] = rewindedNow

            } else if (wasEnabled && !isNowEnabled) {
                // Just disabled
                Log.d(TAG, "🤖❌ DISABLED: $activityType")
                autoEnabledTimes.remove(activityType)
                isInOverduePhase[activityType] = false
                countdownStartValues.remove(activityType)
                calculatedIntervals.remove(activityType)
            }
        }

        // Only start timer if we have at least one enabled timer WITH an interval set
        val hasEnabledTimersWithIntervals = listOf(ActivityType.CONE, ActivityType.JOINT, ActivityType.BOWL).any { type ->
            autoAddState.isEnabled(type) && calculatedIntervals[type] != null && calculatedIntervals[type]!! > 0
        }

        if (hasEnabledTimersWithIntervals && !isRunning) {
            Log.d(TAG, "🤖▶️ Starting timer - we have enabled timers with intervals")
            startTimerUpdates()
        } else if (!hasEnabledTimersWithIntervals && isRunning) {
            Log.d(TAG, "🤖⏹️ Stopping timer - no enabled timers with intervals")
            stopTimerUpdates()
        } else if (autoAddState.hasAnyEnabled() && !hasEnabledTimersWithIntervals) {
            Log.w(TAG, "🤖⚠️ Have enabled timers but NO INTERVALS - timer not started")
        }
    }

    // This is the PROPER way to enable auto-add with all necessary data
    fun enableAutoAddWithPhaseDetection(
        activityType: ActivityType,
        interval: Long,
        timeSinceLastActivity: Long,
        lastActivityTime: Long
    ) {
        val rewindedNow = System.currentTimeMillis() - rewindOffset

        Log.d(TAG, "🤖🎬 ENABLE_WITH_PHASE_DETECTION: $activityType")
        Log.d(TAG, "🤖🎬   interval: ${interval}ms")
        Log.d(TAG, "🤖🎬   timeSinceLastActivity: ${timeSinceLastActivity}ms")
        Log.d(TAG, "🤖🎬   lastActivityTime: $lastActivityTime")

        // CRITICAL: Set the interval FIRST
        calculatedIntervals[activityType] = interval
        autoEnabledTimes[activityType] = rewindedNow
        lastActivityTimes[activityType] = lastActivityTime

        // Determine phase
        if (timeSinceLastActivity > interval) {
            // Phase 2: Overdue
            isInOverduePhase[activityType] = true
            countdownStartValues[activityType] = timeSinceLastActivity
            Log.d(TAG, "🤖🚀 STARTING_PHASE_2: $activityType - countdown from ${timeSinceLastActivity}ms")
        } else {
            // Phase 1: Standard
            isInOverduePhase[activityType] = false
            countdownStartValues.remove(activityType)
            val remaining = interval - timeSinceLastActivity
            Log.d(TAG, "🤖⏳ STARTING_PHASE_1: $activityType - ${remaining}ms remaining")
        }

        // Update the state to mark as enabled
        val currentState = autoAddState
        val newState = when (activityType) {
            ActivityType.CONE -> currentState.copy(
                coneAutoEnabled = true,
                coneNextAutoTime = lastActivityTime + interval
            )
            ActivityType.JOINT -> currentState.copy(
                jointAutoEnabled = true,
                jointNextAutoTime = lastActivityTime + interval
            )
            ActivityType.BOWL -> currentState.copy(
                bowlAutoEnabled = true,
                bowlNextAutoTime = lastActivityTime + interval
            )
            else -> currentState
        }
        autoAddState = newState

        // NOW we can safely start the timer
        if (!isRunning) {
            Log.d(TAG, "🤖▶️ Starting timer after phase detection setup")
            startTimerUpdates()
        }
    }

    fun disableAutoAdd(activityType: ActivityType) {
        Log.d(TAG, "🤖🛑 DISABLE_AUTO_ADD: $activityType")

        // Clear all tracking for this activity type
        autoEnabledTimes.remove(activityType)
        isInOverduePhase[activityType] = false
        countdownStartValues.remove(activityType)
        calculatedIntervals.remove(activityType)
        lastActivityTimes.remove(activityType)

        // Update state
        val currentState = autoAddState
        val newState = when (activityType) {
            ActivityType.CONE -> currentState.copy(coneAutoEnabled = false, coneNextAutoTime = 0L)
            ActivityType.JOINT -> currentState.copy(jointAutoEnabled = false, jointNextAutoTime = 0L)
            ActivityType.BOWL -> currentState.copy(bowlAutoEnabled = false, bowlNextAutoTime = 0L)
            else -> currentState
        }
        autoAddState = newState

        // Check if we should stop the timer completely
        val hasAnyEnabled = autoAddState.hasAnyEnabled()
        if (!hasAnyEnabled && isRunning) {
            stopTimerUpdates()
        }
    }

    fun onActivityLogged(activityType: ActivityType, timestamp: Long) {
        val rewindedNow = System.currentTimeMillis() - rewindOffset

        Log.d(TAG, "🤖📝 ACTIVITY_LOGGED: $activityType at $timestamp")

        // Update last activity time
        lastActivityTimes[activityType] = timestamp

        // If this activity type has auto-add enabled, update its tracking
        if (autoAddState.isEnabled(activityType)) {
            // Calculate new interval based on actual time passed
            val actualTimePassed = if (isInOverduePhase[activityType] == true) {
                countdownStartValues[activityType] ?: calculatedIntervals[activityType] ?: 0L
            } else {
                getTimeSinceLastActivity(activityType)
            }

            if (actualTimePassed > 0) {
                calculatedIntervals[activityType] = actualTimePassed
                Log.d(TAG, "🤖🔄 PHASE_3_RESET: $activityType - new interval: ${actualTimePassed}ms")
            }

            // Reset to Phase 1
            isInOverduePhase[activityType] = false
            countdownStartValues.remove(activityType)
            autoEnabledTimes[activityType] = rewindedNow
        }
    }

    fun applyRewindOffset(offsetMs: Long) {
        rewindOffset = offsetMs
        Log.d(TAG, "⏪ Applied rewind offset: ${offsetMs}ms")

        if (isRunning) {
            handler.removeCallbacks(timerRunnable)
            handler.post(timerRunnable)
        }
    }

    fun startTimerUpdates() {
        if (isRunning) return
        isRunning = true
        Log.d(TAG, "🤖▶️ TIMER_UPDATES_STARTED")
        handler.post(timerRunnable)
    }

    fun stopTimerUpdates() {
        isRunning = false
        handler.removeCallbacks(timerRunnable)
        Log.d(TAG, "🤖⏹️ TIMER_UPDATES_STOPPED")
    }

    fun cleanup() {
        stopTimerUpdates()
        rewindOffset = 0L
        recentAutoAdds.clear()
        calculatedIntervals.clear()
        autoEnabledTimes.clear()
        countdownStartValues.clear()
        isInOverduePhase.clear()
        lastActivityTimes.clear()
    }

    fun isAutoEnabled(activityType: ActivityType): Boolean {
        return autoAddState.isEnabled(activityType)
    }

    fun hasEnoughDataForAuto(activities: List<SessionActivity>, activityType: ActivityType): Boolean {
        val typeActivities = activities.filter { it.type == activityType.name }
        return typeActivities.size >= 2
    }

    private fun hasRecentAutoAdd(activityType: ActivityType): Boolean {
        val lastAutoAdd = recentAutoAdds[activityType] ?: 0L
        val timeSinceLastAutoAdd = System.currentTimeMillis() - lastAutoAdd
        return timeSinceLastAutoAdd < AUTO_ADD_COOLDOWN_MS
    }

    private fun markRecentAutoAdd(activityType: ActivityType) {
        recentAutoAdds[activityType] = System.currentTimeMillis()
        Log.d(TAG, "🤖✅ RECENT_AUTO_ADD_MARKED: $activityType")
    }
}

// Extension functions
private fun AutoAddState.isEnabled(activityType: ActivityType): Boolean {
    return when (activityType) {
        ActivityType.CONE -> coneAutoEnabled
        ActivityType.JOINT -> jointAutoEnabled
        ActivityType.BOWL -> bowlAutoEnabled
        else -> false
    }
}

private fun AutoAddState.hasAnyEnabled(): Boolean {
    return coneAutoEnabled || jointAutoEnabled || bowlAutoEnabled
}

private fun AutoAddState.getNextTime(activityType: ActivityType): Long {
    return when (activityType) {
        ActivityType.CONE -> coneNextAutoTime
        ActivityType.JOINT -> jointNextAutoTime
        ActivityType.BOWL -> bowlNextAutoTime
        else -> 0L
    }
}