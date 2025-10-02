package com.vibecode.cloudcounter

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
    val totalCigarettes: Int = 0,
    val bulkBowlAdditions: Int = 0,
    val longestGapMs: Long = 0L,
    val shortestGapMs: Long = 0L,
    val sinceLastGapMs: Long = 0L,
    val sinceLastJointMs: Long = 0L,
    val sinceLastBowlMs: Long = 0L,
    val sinceLastCigaretteMs: Long = 0L,
    val totalRounds: Int = 0,
    val hitsInCurrentRound: Int = 0,
    val participantCount: Int = 0,
    val lastConeSmokerName: String? = null,
    val lastJointSmokerName: String? = null,
    val lastBowlSmokerName: String? = null,
    val lastCigaretteSmokerName: String? = null,
    val conesSinceLastBowl: Int = 0,
    val lastGapMs: Long? = null,
    val previousGapMs: Long? = null,
    // Custom activity stats: Map of customActivityId to group stats
    val customActivityGroupStats: Map<String, CustomActivityGroupStat> = emptyMap()
)

data class CustomActivityGroupStat(
    val activityName: String,
    val total: Int = 0,
    val lastSmokerName: String? = null,
    val sinceLastMs: Long = 0L
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

    // Track carried-over stats from "Continue with last bowl"
    private var carriedOverCones: Int = 0
    private var carriedOverRounds: Int = 0
    private var carriedOverBowls: Int = 0
    private var isInContinueBowlMode: Boolean = false
    private var continueBowlSmokerId: Long? = null
    private var continueBowlSmokerName: String? = null

    // ADD: Track the current mode
    private var isAutoMode: Boolean = true

    // FIXED: Made this LiveData and public
    private val _isSessionActive = MutableLiveData<Boolean>(false)
    val isSessionActive: LiveData<Boolean> = _isSessionActive

    // ADD: Track the current session ID for proper activity association
    private val _currentSessionId = MutableLiveData<Long?>(null)
    val currentSessionId: LiveData<Long?> = _currentSessionId

    private val _elapsedTimeSec = MutableLiveData<Long>(0L)
    val elapsedTimeSec: LiveData<Long> = _elapsedTimeSec

    val _perSmokerStats = MutableLiveData<List<PerSmokerStats>>(emptyList())
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

    val _groupStats = MutableLiveData(GroupStats()) // Now valid
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
    
    fun updateRoomInfo(roomName: String, shareCode: String) {
        Log.d(TAG, "🏠 Updating room info: $roomName ($shareCode)")
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
        Log.d(TAG, "🎬 START_SESSION: Previous state - isActive=${_isSessionActive.value}, startTime=$sessionStartTime, sessionId=${_currentSessionId.value}")

        this.sessionStartTime = sessionStart
        _currentSessionId.value = sessionStart  // Use session start time as session ID
        _isSessionActive.value = true
        _elapsedTimeSec.value = 0L
        _perSmokerStats.value = emptyList()
        _groupStats.value = GroupStats()

        Log.d(TAG, "🎬 START_SESSION: New state - isActive=${_isSessionActive.value}, startTime=$sessionStartTime, sessionId=${_currentSessionId.value}")
        Log.d(TAG, "🎬 START_SESSION: Stats cleared - perSmoker=${_perSmokerStats.value?.size}, groupStats=${_groupStats.value}")
    }

    fun stopSession() {
        Log.d(TAG, "🛑 STOP_SESSION: Called")
        Log.d(TAG, "🛑 STOP_SESSION: Previous state - isActive=${_isSessionActive.value}, startTime=$sessionStartTime, sessionId=${_currentSessionId.value}")
        Log.d(TAG, "🛑 STOP_SESSION: Current stats - cones=${_groupStats.value?.totalCones}, smokers=${_perSmokerStats.value?.size}")

        _isSessionActive.value = false
        _currentSessionId.value = null  // Clear session ID when session ends
        sessionStartTime = 0L
        
        // Clear carried-over stats when session ends
        clearCarriedOverStats()

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

    fun applyRoomStats(roomStats: SessionStats, sessionStart: Long, smokerDisplayOrder: Map<String, Int>? = null) {
        Log.d(TAG, "📊 APPLY_ROOM_STATS: Applying stats - cones=${roomStats.totalCones}, joints=${roomStats.totalJoints}, bowls=${roomStats.totalBowls}")
        Log.d(TAG, "📊 APPLY_ROOM_STATS: Mode=${if (isAutoMode) "AUTO" else "STICKY"}, sessionStart=$sessionStart")
        Log.d(TAG, "📊 APPLY_ROOM_STATS: Previous isActive=${_isSessionActive.value}, sessionId=${_currentSessionId.value}")

        sessionStartTime = sessionStart
        _currentSessionId.value = sessionStart  // Set session ID when applying room stats
        _isSessionActive.value = true

        val now = System.currentTimeMillis()
        _elapsedTimeSec.postValue((now - sessionStart) / 1000)

        val perSmokerList = roomStats.perSmokerStats.values.map { serverData ->
            // Debug logging for stats values
            Log.d(TAG, "📊 PER-SMOKER DEBUG: ${serverData.smokerName}")
            Log.d(TAG, "📊   Cones: total=${serverData.totalCones}, avg=${serverData.avgGapMs}ms")
            Log.d(TAG, "📊   Joints: total=${serverData.totalJoints}, avg=${serverData.avgJointGapMs}ms")
            Log.d(TAG, "📊   Bowls: total=${serverData.totalBowls}, avg=${serverData.avgBowlGapMs}ms")
            Log.d(TAG, "📊   Last activity time: ${serverData.lastActivityTime}ms")
            
            // For now, use average as last gap (will be calculated properly in MainActivity)
            // TODO: Calculate actual last gaps from activity history
            PerSmokerStats(
                smokerName = serverData.smokerName,
                totalCones = serverData.totalCones,
                totalJoints = serverData.totalJoints,
                totalBowls = serverData.totalBowls,
                totalCigarettes = serverData.totalCigarettes,
                avgGapMs = serverData.avgGapMs,
                longestGapMs = serverData.longestGapMs,
                shortestGapMs = serverData.shortestGapMs,
                lastGapMs = serverData.avgGapMs,  // TODO: Calculate from activity history
                lastConeTime = 0L,  // TODO: Get from last cone activity
                avgJointGapMs = serverData.avgJointGapMs,
                longestJointGapMs = serverData.longestJointGapMs,
                shortestJointGapMs = serverData.shortestJointGapMs,
                lastJointGapMs = serverData.avgJointGapMs,  // TODO: Calculate from activity history
                lastJointTime = 0L,  // TODO: Get from last joint activity
                avgBowlGapMs = serverData.avgBowlGapMs,
                longestBowlGapMs = serverData.longestBowlGapMs,
                shortestBowlGapMs = serverData.shortestBowlGapMs,
                lastBowlGapMs = serverData.avgBowlGapMs,  // TODO: Calculate from activity history
                lastBowlTime = 0L,  // TODO: Get from last bowl activity
                avgCigaretteGapMs = serverData.avgCigaretteGapMs,
                longestCigaretteGapMs = serverData.longestCigaretteGapMs,
                shortestCigaretteGapMs = serverData.shortestCigaretteGapMs,
                lastCigaretteGapMs = serverData.avgCigaretteGapMs,
                lastCigaretteTime = 0L,
                lastActivityTime = serverData.lastActivityTime
            )
        }.let { list ->
            // Sort by displayOrder if provided, otherwise keep original order
            if (smokerDisplayOrder != null) {
                list.sortedBy { smokerDisplayOrder[it.smokerName] ?: Int.MAX_VALUE }
            } else {
                list
            }
        }
        Log.d(TAG, "📊 STATS UI UPDATE: Posting ${perSmokerList.size} per-smoker stats to UI")
        perSmokerList.forEach { stat ->
            Log.d(TAG, "📊 STATS UI: ${stat.smokerName} - C:${stat.totalCones}, J:${stat.totalJoints}, B:${stat.totalBowls}")
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
    
    fun updateGroupStats(stats: GroupStats) {
        _groupStats.postValue(stats)
    }
    
    fun setCarriedOverStats(cones: Int, rounds: Int, bowls: Int = 0) {
        Log.d(TAG, "📦 Setting carried-over stats - Cones: $cones, Rounds: $rounds, Bowls: $bowls")
        carriedOverCones = cones
        carriedOverRounds = rounds
        carriedOverBowls = bowls
        isInContinueBowlMode = true
    }
    
    fun setContinueBowlSmoker(smokerId: Long, smokerName: String) {
        Log.d(TAG, "📦 Setting continue bowl smoker: ID=$smokerId, Name=$smokerName")
        continueBowlSmokerId = smokerId
        continueBowlSmokerName = smokerName
        Log.d(TAG, "📦 Continue mode is now: $isInContinueBowlMode with smoker ID: $continueBowlSmokerId, Name: $continueBowlSmokerName")
    }
    
    fun getContinueBowlSmokerId(): Long? {
        Log.d(TAG, "📦 Getting continue bowl smoker ID: $continueBowlSmokerId (continue mode: $isInContinueBowlMode)")
        return continueBowlSmokerId
    }
    
    fun getCarriedOverStats(): Triple<Int, Int, Int> {
        return Triple(carriedOverCones, carriedOverRounds, carriedOverBowls)
    }
    
    fun isInContinueMode(): Boolean {
        return isInContinueBowlMode
    }
    
    fun clearCarriedOverStats() {
        Log.d(TAG, "🧹 Clearing carried-over stats")
        carriedOverCones = 0
        carriedOverRounds = 0
        carriedOverBowls = 0
        isInContinueBowlMode = false
        continueBowlSmokerId = null
        continueBowlSmokerName = null
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

    fun decrementActivityCount(smokerName: String, activityType: ActivityType, customActivityId: String? = null) {
        Log.d(TAG, "➖ DECREMENT: $smokerName - $activityType${if (customActivityId != null) " (customId=$customActivityId)" else ""}")
        val currentPerSmokerStats = _perSmokerStats.value ?: emptyList()
        val currentGroupStats = _groupStats.value ?: return

        val updatedPerSmoker = currentPerSmokerStats.map { stat ->
            if (stat.smokerName == smokerName) {
                when (activityType) {
                    ActivityType.CONE -> stat.copy(totalCones = (stat.totalCones - 1).coerceAtLeast(0))
                    ActivityType.JOINT -> stat.copy(totalJoints = (stat.totalJoints - 1).coerceAtLeast(0))
                    ActivityType.BOWL -> stat.copy(totalBowls = (stat.totalBowls - 1).coerceAtLeast(0))
                    ActivityType.CIGARETTE -> stat.copy(totalCigarettes = (stat.totalCigarettes - 1).coerceAtLeast(0))
                    ActivityType.CUSTOM -> {
                        // Decrement the per-smoker count for this custom activity ID
                        if (customActivityId != null && stat.customActivityStats.containsKey(customActivityId)) {
                            val updatedCustom = stat.customActivityStats.toMutableMap()
                            val existing = updatedCustom[customActivityId]!!
                            updatedCustom[customActivityId] = existing.copy(
                                total = (existing.total - 1).coerceAtLeast(0)
                            )
                            stat.copy(customActivityStats = updatedCustom)
                        } else {
                            stat
                        }
                    }
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
            ActivityType.CIGARETTE -> currentGroupStats.copy(totalCigarettes = (currentGroupStats.totalCigarettes - 1).coerceAtLeast(0))
            ActivityType.CUSTOM -> {
                if (customActivityId != null) {
                    val updatedCustomGroup = currentGroupStats.customActivityGroupStats.toMutableMap()
                    val existing = updatedCustomGroup[customActivityId]
                    if (existing != null) {
                        updatedCustomGroup[customActivityId] = existing.copy(
                            total = (existing.total - 1).coerceAtLeast(0)
                        )
                        currentGroupStats.copy(customActivityGroupStats = updatedCustomGroup)
                    } else currentGroupStats
                } else currentGroupStats
            }
            else -> currentGroupStats
        }

        _perSmokerStats.value = updatedPerSmoker
        _groupStats.value = updatedGroup
        Log.d(TAG, "➖ DECREMENT: Updated totals - cones=${updatedGroup.totalCones}, joints=${updatedGroup.totalJoints}, bowls=${updatedGroup.totalBowls}")
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
        conesSinceLastBowl: Int = 0,
        smokerDisplayOrder: Map<String, Int>? = null
    ) {
        // If we're in continue mode, adjust the incoming stats with carried-over values
        val adjustedGroupStats = if (isInContinueBowlMode && carriedOverCones > 0) {
            Log.d(TAG, "📦 CONTINUE MODE ACTIVE: Adjusting incoming stats")
            Log.d(TAG, "📦   Carried-over: C=$carriedOverCones, B=$carriedOverBowls, R=$carriedOverRounds")
            groupStats.copy(
                // Keep totalCones as current session only
                totalCones = groupStats.totalCones,
                totalBowls = groupStats.totalBowls + carriedOverBowls,
                totalRounds = groupStats.totalRounds + carriedOverRounds,
                // Only conesSinceLastBowl includes carried-over cones
                conesSinceLastBowl = (groupStats.conesSinceLastBowl ?: 0) + carriedOverCones
            )
        } else {
            groupStats
        }
        
        Log.d(TAG, "📦 APPLY_LOCAL_STATS: Applying ${adjustedGroupStats.totalCones} cones from ${perSmoker.size} smokers")
        Log.d(TAG, "📦 APPLY_LOCAL_STATS: sessionStart=$sessionStart, will activate=${sessionStart > 0}")
        Log.d(TAG, "📦🔴 DEBUG: Received GroupStats with:")
        Log.d(TAG, "📦🔴   - lastConeSmokerName = ${adjustedGroupStats.lastConeSmokerName}")
        Log.d(TAG, "📦🔴   - lastJointSmokerName = ${adjustedGroupStats.lastJointSmokerName}")
        Log.d(TAG, "📦🔴   - lastBowlSmokerName = ${adjustedGroupStats.lastBowlSmokerName}")
        Log.d(TAG, "📦🔴   - totalCones = ${adjustedGroupStats.totalCones}")
        Log.d(TAG, "📦🔴   - totalJoints = ${adjustedGroupStats.totalJoints}")
        Log.d(TAG, "📦🔴   - totalBowls = ${adjustedGroupStats.totalBowls}")
        Log.d(TAG, "📦🔴   - totalCigarettes = ${adjustedGroupStats.totalCigarettes}")

        this.sessionStartTime = sessionStart
        val shouldActivate = sessionStart > 0
        _currentSessionId.value = if (shouldActivate) sessionStart else null  // Set session ID when applying local stats
        _isSessionActive.value = shouldActivate

        _elapsedTimeSec.value = if (sessionStart > 0) (System.currentTimeMillis() - sessionStart) / 1000 else 0L
        
        // Also adjust per-smoker stats if in continue mode
        val adjustedPerSmoker = if (isInContinueBowlMode && continueBowlSmokerId != null) {
            Log.d(TAG, "📦 CONTINUE MODE: Adjusting per-smoker stats for smoker ID $continueBowlSmokerId")
            // First check if the continue smoker is already in the list
            val hasContinueSmoker = perSmoker.any { stat ->
                stat.smokerName == continueBowlSmokerName
            }
            
            if (hasContinueSmoker) {
                // Update existing smoker's bowls
                val updatedList = perSmoker.map { stat ->
                    if (stat.smokerName == continueBowlSmokerName) {
                        Log.d(TAG, "📦 CONTINUE MODE: Adding $carriedOverBowls bowls to ${stat.smokerName}")
                        stat.copy(totalBowls = stat.totalBowls + carriedOverBowls)
                    } else {
                        stat
                    }
                }
                updatedList
            } else if (carriedOverBowls > 0) {
                // Add the continue smoker if not in list but has carried-over bowls
                val smokerName = continueBowlSmokerName ?: "Unknown"
                Log.d(TAG, "📦 CONTINUE MODE: Adding new per-smoker entry for $smokerName with $carriedOverBowls bowls")
                perSmoker + PerSmokerStats(
                    smokerName = smokerName,
                    totalCones = 0,
                    totalJoints = 0,
                    totalBowls = carriedOverBowls,
                    avgGapMs = 0,
                    longestGapMs = 0,
                    shortestGapMs = 0,
                    avgJointGapMs = 0,
                    longestJointGapMs = 0,
                    shortestJointGapMs = 0,
                    avgBowlGapMs = 0,
                    longestBowlGapMs = 0,
                    shortestBowlGapMs = 0
                )
            } else {
                perSmoker
            }
        } else {
            perSmoker
        }
        
        // Sort by displayOrder if provided, otherwise keep original order
        val sortedPerSmoker = if (smokerDisplayOrder != null) {
            adjustedPerSmoker.sortedBy { smokerDisplayOrder[it.smokerName] ?: Int.MAX_VALUE }
        } else {
            adjustedPerSmoker
        }
        Log.d(TAG, "📊 STATS UI UPDATE (loadStats): Setting ${sortedPerSmoker.size} per-smoker stats to UI")
        sortedPerSmoker.forEach { stat ->
            Log.d(TAG, "📊 STATS UI (loadStats): ${stat.smokerName} - C:${stat.totalCones}, J:${stat.totalJoints}, B:${stat.totalBowls}")
        }
        _perSmokerStats.value = sortedPerSmoker
        // FIX: Use the adjusted groupStats
        _groupStats.value = adjustedGroupStats
        Log.d(TAG, "📦🔴 DEBUG: After setting _groupStats.value:")
        Log.d(TAG, "📦🔴   - cone name = ${_groupStats.value?.lastConeSmokerName}")
        
        // Log cigarette stats
        Log.d(TAG, "🚬 CIGARETTE_VM: Set perSmokerStats with ${sortedPerSmoker.size} smokers")
        sortedPerSmoker.forEach { stat ->
            Log.d(TAG, "🚬 CIGARETTE_VM:   ${stat.smokerName}: totalCigarettes=${stat.totalCigarettes}")
        }
        Log.d(TAG, "🚬 CIGARETTE_VM: Set groupStats with totalCigarettes=${adjustedGroupStats.totalCigarettes}")
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
    
    fun endSession() {
        Log.d(TAG, "🔚 ENDING SESSION in ViewModel")
        Log.d(TAG, "🔚 Previous state: isActive=${_isSessionActive.value}, sessionStart=$sessionStartTime")
        
        // Clear all session data
        sessionStartTime = 0L
        _currentSessionId.value = null
        _isSessionActive.value = false
        _elapsedTimeSec.value = 0L
        _perSmokerStats.value = emptyList()
        _groupStats.value = GroupStats()
        
        // Clear room info
        clearRoomInfo()
        
        // Clear carried-over stats
        carriedOverCones = 0
        carriedOverRounds = 0
        carriedOverBowls = 0
        isInContinueBowlMode = false
        continueBowlSmokerId = null
        continueBowlSmokerName = null
        
        Log.d(TAG, "🔚 Session ended - all data cleared")
        Log.d(TAG, "🔚 New state: isActive=${_isSessionActive.value}, sessionStart=$sessionStartTime")
    }
    
    fun forceStatsRefresh() {
        Log.d(TAG, "🔄 FORCE_STATS_REFRESH called")
        // This will trigger observers to refresh
        _perSmokerStats.value = _perSmokerStats.value
        _groupStats.value = _groupStats.value
        _trigger.value = (_trigger.value ?: 0) + 1
    }
}
