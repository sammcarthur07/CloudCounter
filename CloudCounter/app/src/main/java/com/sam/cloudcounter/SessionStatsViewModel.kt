package com.sam.cloudcounter

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

// --- FIX IS HERE: Added default values to all parameters ---
data class GroupStats(
    val totalCones: Int = 0,
    val totalJoints: Int = 0,
    val totalBowls: Int = 0,
    val longestGapMs: Long = 0L,
    val shortestGapMs: Long = 0L,
    val sinceLastGapMs: Long = 0L,
    val sinceLastJointMs: Long = 0L,
    val sinceLastBowlMs: Long = 0L,
    val totalRounds: Int = 0,
    val hitsInCurrentRound: Int = 0,
    val participantCount: Int = 0,
    val lastConeSmokerName: String? = null,
    val lastJointSmokerName: String? = null,
    val lastBowlSmokerName: String? = null,
    val conesSinceLastBowl: Int = 0,
    val lastGapMs: Long? = null,
    val previousGapMs: Long? = null
)

// ADD: Room info data class
data class RoomInfo(
    val roomName: String,
    val shareCode: String
)

class SessionStatsViewModel : ViewModel() {
    companion object { private const val TAG = "SessionStatsVM" }

    var lastCompletedSessionId: Long? = null
        internal set
    var lastCompletedSessionStart: Long? = null
        internal set
    var lastCompletedSessionEnd: Long? = null
        internal set

    var sessionStartTime: Long = 0L
        private set


    // ADD: Track the current mode
    private var isAutoMode: Boolean = true

    // FIXED: Made this LiveData and public
    private val _isSessionActive = MutableLiveData<Boolean>(false)
    val isSessionActive: LiveData<Boolean> = _isSessionActive

    private val _elapsedTimeSec = MutableLiveData<Long>(0L)
    val elapsedTimeSec: LiveData<Long> = _elapsedTimeSec

    private val _perSmokerStats = MutableLiveData<List<PerSmokerStats>>(emptyList())
    val perSmokerStats: LiveData<List<PerSmokerStats>> = _perSmokerStats

    // ADD: Room info LiveData
    private val _roomInfo = MutableLiveData<RoomInfo?>(null)
    val roomInfo: LiveData<RoomInfo?> = _roomInfo

    // ADD: Individual room info properties for compatibility
    private val _roomName = MutableLiveData<String?>(null)
    val roomName: LiveData<String?> = _roomName

    private val _shareCode = MutableLiveData<String?>(null)
    val shareCode: LiveData<String?> = _shareCode

    // ADD: Last cone smoker name and cones since bowl
    private val _lastConeSmokerName = MutableLiveData<String?>(null)
    val lastConeSmokerName: LiveData<String?> = _lastConeSmokerName

    private val _conesSinceLastBowl = MutableLiveData<Int>(0)
    val conesSinceLastBowl: LiveData<Int> = _conesSinceLastBowl

    private val _groupStats = MutableLiveData(GroupStats()) // Now valid
    val groupStats: LiveData<GroupStats> = _groupStats

    // Add this trigger to force updates
    private val _trigger = MutableLiveData<Int>(0)

    init {
        Log.d(TAG, "🟢 INIT: SessionStatsViewModel created")
        Log.d(TAG, "🟢 INIT: isSessionActive = ${_isSessionActive.value}")
    }

    // ADD: Methods to manage room info
    fun setRoomInfo(roomName: String, shareCode: String) {
        Log.d(TAG, "🏠 Setting room info: $roomName ($shareCode)")
        _roomInfo.value = RoomInfo(roomName, shareCode)
        _roomName.value = roomName
        _shareCode.value = shareCode
    }

    fun clearRoomInfo() {
        Log.d(TAG, "🏠 Clearing room info")
        _roomInfo.value = null
        _roomName.value = null
        _shareCode.value = null
    }

    // ADD: Method to set the mode
    fun setAutoMode(isAuto: Boolean) {
        Log.d(TAG, "🔘 Mode changed to: ${if (isAuto) "AUTO" else "STICKY"}")
        isAutoMode = isAuto
    }

    fun startSession(sessionStart: Long) {
        Log.d(TAG, "🎬 START_SESSION: Called with sessionStart=$sessionStart")
        Log.d(TAG, "🎬 START_SESSION: Previous state - isActive=${_isSessionActive.value}, startTime=$sessionStartTime")

        this.sessionStartTime = sessionStart
        _isSessionActive.value = true
        _elapsedTimeSec.value = 0L
        _perSmokerStats.value = emptyList()
        _groupStats.value = GroupStats()

        Log.d(TAG, "🎬 START_SESSION: New state - isActive=${_isSessionActive.value}, startTime=$sessionStartTime")
        Log.d(TAG, "🎬 START_SESSION: Stats cleared - perSmoker=${_perSmokerStats.value?.size}, groupStats=${_groupStats.value}")
    }

    fun stopSession() {
        Log.d(TAG, "🛑 STOP_SESSION: Called")
        Log.d(TAG, "🛑 STOP_SESSION: Previous state - isActive=${_isSessionActive.value}, startTime=$sessionStartTime")
        Log.d(TAG, "🛑 STOP_SESSION: Current stats - cones=${_groupStats.value?.totalCones}, smokers=${_perSmokerStats.value?.size}")

        _isSessionActive.value = false
        sessionStartTime = 0L

        // Don't clear stats immediately - keep them for display
        // Only clear the time-based values
        val currentGroup = _groupStats.value
        if (currentGroup != null) {
            _groupStats.value = currentGroup.copy(
                sinceLastGapMs = 0L,
                sinceLastJointMs = 0L,
                sinceLastBowlMs = 0L,
                hitsInCurrentRound = 0,
                totalRounds = currentGroup.totalRounds // Keep rounds
            )
            Log.d(TAG, "🛑 STOP_SESSION: Kept stats but cleared time-based values")
        }

        _elapsedTimeSec.value = 0L

        // Clear room info
        _roomName.value = null
        _shareCode.value = null
        _roomInfo.value = null

        Log.d(TAG, "🛑 STOP_SESSION: New state - isActive=${_isSessionActive.value}, startTime=$sessionStartTime")
        Log.d(TAG, "🛑 STOP_SESSION: Final stats - cones=${_groupStats.value?.totalCones}, smokers=${_perSmokerStats.value?.size}")
    }

    fun clearAllStats() {
        Log.d(TAG, "🧹 CLEAR_ALL_STATS: Clearing all statistics")
        _groupStats.value = GroupStats()
        _perSmokerStats.value = emptyList()
        _lastConeSmokerName.value = null
        _conesSinceLastBowl.value = 0
        Log.d(TAG, "🧹 CLEAR_ALL_STATS: All stats cleared")
    }

    fun applyRoomStats(roomStats: SessionStats, sessionStart: Long) {
        Log.d(TAG, "📊 APPLY_ROOM_STATS: Applying stats - cones=${roomStats.totalCones}, joints=${roomStats.totalJoints}, bowls=${roomStats.totalBowls}")
        Log.d(TAG, "📊 APPLY_ROOM_STATS: Mode=${if (isAutoMode) "AUTO" else "STICKY"}, sessionStart=$sessionStart")
        Log.d(TAG, "📊 APPLY_ROOM_STATS: Previous isActive=${_isSessionActive.value}")

        sessionStartTime = sessionStart
        _isSessionActive.value = true

        val now = System.currentTimeMillis()
        _elapsedTimeSec.postValue((now - sessionStart) / 1000)

        val perSmokerList = roomStats.perSmokerStats.values.map { serverData ->
            PerSmokerStats(
                smokerName = serverData.smokerName,
                totalCones = serverData.totalCones,
                totalJoints = serverData.totalJoints,
                totalBowls = serverData.totalBowls,
                avgGapMs = serverData.avgGapMs,
                longestGapMs = serverData.longestGapMs,
                shortestGapMs = serverData.shortestGapMs,
                avgJointGapMs = serverData.avgJointGapMs,
                longestJointGapMs = serverData.longestJointGapMs,
                shortestJointGapMs = serverData.shortestJointGapMs,
                avgBowlGapMs = serverData.avgBowlGapMs,
                longestBowlGapMs = serverData.longestBowlGapMs,
                shortestBowlGapMs = serverData.shortestBowlGapMs
            )
        }
        _perSmokerStats.postValue(perSmokerList)

        val currentGroupStats = _groupStats.value

        val groupStats = GroupStats(
            totalCones = roomStats.totalCones,
            totalJoints = roomStats.totalJoints,
            totalBowls = roomStats.totalBowls,
            longestGapMs = roomStats.longestGapMs,
            shortestGapMs = roomStats.shortestGapMs,
            sinceLastGapMs = roomStats.sinceLastConeMs,
            sinceLastJointMs = roomStats.sinceLastJointMs,
            sinceLastBowlMs = roomStats.sinceLastBowlMs,
            totalRounds = if (isAutoMode) {
                roomStats.totalRounds
            } else {
                Log.d(TAG, "📊 STICKY MODE: Preserving local rounds (${currentGroupStats?.totalRounds ?: 0}) instead of server rounds (${roomStats.totalRounds})")
                currentGroupStats?.totalRounds ?: 0
            },
            hitsInCurrentRound = if (isAutoMode) {
                roomStats.hitsInCurrentRound
            } else {
                Log.d(TAG, "📊 STICKY MODE: Preserving local hits (${currentGroupStats?.hitsInCurrentRound ?: 0}) instead of server hits (${roomStats.hitsInCurrentRound})")
                currentGroupStats?.hitsInCurrentRound ?: 0
            },
            participantCount = roomStats.participantCount,
            lastConeSmokerName = roomStats.lastConeSmokerName,
            lastJointSmokerName = roomStats.lastJointSmokerName,
            lastBowlSmokerName = roomStats.lastBowlSmokerName,
            conesSinceLastBowl = roomStats.conesSinceLastBowl
        )
        _groupStats.postValue(groupStats)

        Log.d(TAG, "📊 APPLY_ROOM_STATS: Applied - ${perSmokerList.size} smokers, new isActive=${_isSessionActive.value}")
    }

    fun updateSinceLastCone(sinceLastMs: Long) {
        Log.d(TAG, "⏱️ UPDATE_SINCE_LAST: sinceLastMs=$sinceLastMs, isActive=${_isSessionActive.value}")
        val current = _groupStats.value ?: return
        _groupStats.postValue(current.copy(sinceLastGapMs = sinceLastMs))
    }

    fun refreshTimer() {
        val isActive = _isSessionActive.value ?: false
        if (!isActive || sessionStartTime == 0L) {
            Log.d(TAG, "⏱️ REFRESH_TIMER: Skipped - isActive=$isActive, startTime=$sessionStartTime")
            return
        }

        if (sessionStartTime > 0) {
            val now = System.currentTimeMillis()
            val elapsedSec = (now - sessionStartTime) / 1000
            Log.d(TAG, "⏱️ REFRESH_TIMER: Updating elapsed to ${elapsedSec}s")
            _elapsedTimeSec.postValue(elapsedSec)
        }
    }

    fun refreshTimerWithOffset(offsetMs: Long) {
        val isActive = _isSessionActive.value ?: false
        if (!isActive || sessionStartTime == 0L) {
            Log.d(TAG, "⏱️ REFRESH_TIMER_OFFSET: Skipped - isActive=$isActive, startTime=$sessionStartTime")
            return
        }

        if (sessionStartTime > 0) {
            val rewindedNow = System.currentTimeMillis() - offsetMs
            val elapsedMs = rewindedNow - sessionStartTime
            val elapsedSec = if (elapsedMs < 0) 0L else elapsedMs / 1000
            Log.d(TAG, "⏱️ REFRESH_TIMER_OFFSET: elapsedSec=$elapsedSec (offset=${offsetMs}ms)")
            _elapsedTimeSec.postValue(elapsedSec)
        }
    }

    fun forceLocalStatsRefresh() {
        Log.d(TAG, "🔄 FORCE_REFRESH: Triggering local stats refresh")
        viewModelScope.launch {
            _trigger.value = (_trigger.value ?: 0) + 1
        }
    }

    fun decrementActivityCount(smokerName: String, activityType: ActivityType) {
        Log.d(TAG, "➖ DECREMENT: $smokerName - $activityType")
        val currentPerSmokerStats = _perSmokerStats.value ?: emptyList()
        val currentGroupStats = _groupStats.value ?: return

        val updatedPerSmoker = currentPerSmokerStats.map { stat ->
            if (stat.smokerName == smokerName) {
                when (activityType) {
                    ActivityType.CONE -> stat.copy(totalCones = (stat.totalCones - 1).coerceAtLeast(0))
                    ActivityType.JOINT -> stat.copy(totalJoints = (stat.totalJoints - 1).coerceAtLeast(0))
                    ActivityType.BOWL -> stat.copy(totalBowls = (stat.totalBowls - 1).coerceAtLeast(0))
                    else -> stat
                }
            } else {
                stat
            }
        }

        val updatedGroup = when (activityType) {
            ActivityType.CONE -> currentGroupStats.copy(totalCones = (currentGroupStats.totalCones - 1).coerceAtLeast(0))
            ActivityType.JOINT -> currentGroupStats.copy(totalJoints = (currentGroupStats.totalJoints - 1).coerceAtLeast(0))
            ActivityType.BOWL -> currentGroupStats.copy(totalBowls = (currentGroupStats.totalBowls - 1).coerceAtLeast(0))
            else -> currentGroupStats
        }

        _perSmokerStats.value = updatedPerSmoker
        _groupStats.value = updatedGroup
        Log.d(TAG, "➖ DECREMENT: Updated totals - cones=${updatedGroup.totalCones}")
    }

    fun recalculateGaps() {
        Log.d(TAG, "🔄 RECALCULATE_GAPS: Triggering")
        viewModelScope.launch {
            _trigger.value = (_trigger.value ?: 0) + 1
        }
    }

    fun loadSummary(summary: SessionSummary) {
        Log.d(TAG, "📁 LOAD_SUMMARY: Loading summary with ${summary.totalCones} cones")
        Log.d(TAG, "📁 LOAD_SUMMARY: Current state - roomInfo=${_roomInfo.value}, groupCones=${_groupStats.value?.totalCones}")

        if (_roomInfo.value != null && _groupStats.value?.totalCones ?: 0 > 0) {
            Log.d(TAG, "📁 LOAD_SUMMARY: Skipped - already have stats loaded")
            return
        }

        _perSmokerStats.value = summary.smokerNames.mapIndexed { idx, name ->
            PerSmokerStats(
                smokerName = name,
                totalCones = summary.conesPerSmoker.getOrNull(idx) ?: 0,
                longestGapMs = summary.longestInterval,
                shortestGapMs = summary.shortestInterval,
            )
        }
        _groupStats.value = GroupStats(
            totalCones = summary.totalCones,
            longestGapMs = summary.longestInterval,
            shortestGapMs = summary.shortestInterval,
            totalRounds = summary.rounds,
            participantCount = summary.smokerNames.size
        )

        // Don't set session as active when loading summary
        Log.d(TAG, "📁 LOAD_SUMMARY: Loaded without activating session - isActive=${_isSessionActive.value}")
    }

    fun refreshStats() {
        val isActive = _isSessionActive.value ?: false
        Log.d(TAG, "📊 REFRESH_STATS: Called - isActive=$isActive, startTime=$sessionStartTime")

        if (!isActive) {
            Log.d(TAG, "📊 REFRESH_STATS: Skipped - session not active")
            return
        }

        if (sessionStartTime > 0) {
            val now = System.currentTimeMillis()
            val elapsedSec = (now - sessionStartTime) / 1000
            Log.d(TAG, "📊 REFRESH_STATS: Updating elapsed to ${elapsedSec}s")
            _elapsedTimeSec.postValue(elapsedSec)
        }
    }

    fun applyLocalStats(
        perSmoker: List<PerSmokerStats>,
        groupStats: GroupStats,
        sessionStart: Long,
        lastConeSmokerName: String? = null,
        conesSinceLastBowl: Int = 0
    ) {
        Log.d(TAG, "📦 APPLY_LOCAL_STATS: Applying ${groupStats.totalCones} cones from ${perSmoker.size} smokers")
        Log.d(TAG, "📦 APPLY_LOCAL_STATS: sessionStart=$sessionStart, will activate=${sessionStart > 0}")
        Log.d(TAG, "📦🔴 DEBUG: Received GroupStats with:")
        Log.d(TAG, "📦🔴   - lastConeSmokerName = ${groupStats.lastConeSmokerName}")
        Log.d(TAG, "📦🔴   - lastJointSmokerName = ${groupStats.lastJointSmokerName}")
        Log.d(TAG, "📦🔴   - lastBowlSmokerName = ${groupStats.lastBowlSmokerName}")
        Log.d(TAG, "📦🔴   - totalCones = ${groupStats.totalCones}")
        Log.d(TAG, "📦🔴   - totalJoints = ${groupStats.totalJoints}")
        Log.d(TAG, "📦🔴   - totalBowls = ${groupStats.totalBowls}")

        this.sessionStartTime = sessionStart
        val shouldActivate = sessionStart > 0
        _isSessionActive.value = shouldActivate

        _elapsedTimeSec.value = if (sessionStart > 0) (System.currentTimeMillis() - sessionStart) / 1000 else 0L
        _perSmokerStats.value = perSmoker
        // FIX: Use the groupStats directly since it already has all the names
        _groupStats.value = groupStats
        Log.d(TAG, "📦🔴 DEBUG: After setting _groupStats.value:")
        Log.d(TAG, "📦🔴   - cone name = ${_groupStats.value?.lastConeSmokerName}")
        Log.d(TAG, "📦🔴   - joint name = ${_groupStats.value?.lastJointSmokerName}")
        Log.d(TAG, "📦🔴   - bowl name = ${_groupStats.value?.lastBowlSmokerName}")

        Log.d(TAG, "📦 APPLY_LOCAL_STATS: Applied - isActive=${_isSessionActive.value}, elapsed=${_elapsedTimeSec.value}")
    }

    fun applyRewindOffset(offsetMs: Long) {
        Log.d(TAG, "⏪ APPLY_REWIND: offset=${offsetMs}ms, isActive=${_isSessionActive.value}")
        if (sessionStartTime > 0) {
            val rewindedNow = System.currentTimeMillis() - offsetMs
            val elapsedMs = rewindedNow - sessionStartTime
            val elapsedSec = if (elapsedMs < 0) 0L else elapsedMs / 1000
            _elapsedTimeSec.postValue(elapsedSec)
            Log.d(TAG, "⏪ APPLY_REWIND: Session elapsed with rewind: ${elapsedSec}s")
        }
        val current = _groupStats.value ?: return
        _groupStats.postValue(current.copy(
            sinceLastGapMs = current.sinceLastGapMs + offsetMs,
            sinceLastJointMs = current.sinceLastJointMs + offsetMs,
            sinceLastBowlMs = current.sinceLastBowlMs + offsetMs
        ))
    }

    fun debugCurrentState() {
        Log.d(TAG, "🐛 === SESSION STATS DEBUG ===")
        Log.d(TAG, "🐛 Session active: ${_isSessionActive.value}")
        Log.d(TAG, "🐛 Session start time: $sessionStartTime")
        Log.d(TAG, "🐛 Elapsed seconds: ${_elapsedTimeSec.value}")
        Log.d(TAG, "🐛 Per-smoker stats: ${_perSmokerStats.value?.size} smokers")
        _perSmokerStats.value?.forEach { stat ->
            Log.d(TAG, "🐛   ${stat.smokerName}: ${stat.totalCones} cones")
        }
        Log.d(TAG, "🐛 Group stats: ${_groupStats.value}")
        Log.d(TAG, "🐛 Room info: ${_roomInfo.value}")
        Log.d(TAG, "🐛 =========================")
    }
}