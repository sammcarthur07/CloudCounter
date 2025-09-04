package com.sam.cloudcounter

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import kotlinx.coroutines.delay

class StashViewModel(application: Application) : AndroidViewModel(application) {

    private val stashDao = AppDatabase.getDatabase(application).stashDao()
    private val activityLogDao = AppDatabase.getDatabase(application).activityLogDao()
    private val smokerDao = AppDatabase.getDatabase(application).smokerDao()
    private val summaryDao = AppDatabase.getDatabase(application).sessionSummaryDao()

    // Initialize the stats calculator - AFTER all DAOs are declared
    private val statsCalculator = StashStatsCalculator(activityLogDao, stashDao, smokerDao, summaryDao)

    private val _currentStash = MutableLiveData<Stash?>()
    val currentStash: LiveData<Stash?> = _currentStash

    private val _stashHistory = MutableLiveData<List<StashEntry>>()
    val stashHistory: LiveData<List<StashEntry>> = _stashHistory

    private val _stashSource = MutableLiveData(StashSource.MY_STASH)
    val stashSource: LiveData<StashSource> = _stashSource

    private val _ratios = MutableLiveData<ConsumptionRatio?>()
    val ratios: LiveData<ConsumptionRatio?> = _ratios

    // Single LiveData for all stats types
    private val _stashStats = MutableLiveData<StashStatsCalculator.StashStats?>()
    val stashStats: LiveData<StashStatsCalculator.StashStats?> = _stashStats

    // Keep these for backward compatibility but they'll all use the same underlying stats
    private val _historicalStats = MutableLiveData<GroupStats>()
    val historicalStats: LiveData<GroupStats> = _historicalStats

    private val _previousStats = MutableLiveData<GroupStats>()
    val previousStats: LiveData<GroupStats> = _previousStats

    private val _projectedStats = MutableLiveData<GroupStats>()
    val projectedStats: LiveData<GroupStats> = _projectedStats

    private var currentUserId: String? = null
    private var calculatedConesPerBowl: Double = 0.0

    // Current stats configuration
    private var currentStatsType = StashStatsCalculator.StatsType.CURRENT
    private var currentTimePeriod = StashTimePeriod.THIS_SESH
    private var currentDataScope = StashStatsCalculator.DataScope.MY_STASH
    private var sessionStartTime: Long? = null
    private var lastCompletedSessionId: Long? = null

    private var lastCompletedSessionStart: Long? = null
    private var lastCompletedSessionEnd: Long? = null

    // Store ratios at the time of each activity
    private val activityRatios = mutableMapOf<Long, ConsumptionRatio>()
    
    // Timer job for real-time projection updates
    private var projectionUpdateJob: Job? = null

    companion object {
        private const val TAG = "StashViewModel"
        private const val PROJECTION_UPDATE_INTERVAL_MS = 3000L // Update every 3 seconds
    }

    init {
        loadCurrentStash()
        loadStashHistory()
        loadRatios()
        calculateConesPerBowl()
    }

    fun setCurrentUserId(userId: String) {
        currentUserId = userId
        Log.d("STASH_VM", "User ID set: $userId")
    }

    fun setLastCompletedSessionBounds(startTime: Long, endTime: Long) {
        lastCompletedSessionStart = startTime
        lastCompletedSessionEnd = endTime
        Log.d("STASH_VM", "Last completed session bounds set: start=$startTime, end=$endTime")
    }

    fun runDebugStatsTest() {
        viewModelScope.launch {
            Log.d("STASH_DEBUG", "")
            Log.d("STASH_DEBUG", "=================================================================")
            Log.d("STASH_DEBUG", "RUNNING ENHANCED STATS TEST WITH ACTIVITY SIMULATION")
            Log.d("STASH_DEBUG", "=================================================================")

            // Store initial state
            val initialSessionStart = sessionStartTime
            val initialLastCompleted = lastCompletedSessionId

            Log.d("STASH_DEBUG", "Initial State:")
            Log.d("STASH_DEBUG", "  Session Start Time: $initialSessionStart")
            Log.d("STASH_DEBUG", "  Last Completed Session ID: $initialLastCompleted")
            Log.d("STASH_DEBUG", "  Current User ID: $currentUserId")

            // First run - baseline
            Log.d("STASH_DEBUG", "")
            Log.d("STASH_DEBUG", "PHASE 1: BASELINE TEST")
            Log.d("STASH_DEBUG", "-----------------------------------------------------------------")
            runDebugTestCycle()

            // Wait a bit
            delay(2000)

            // Simulate adding 3 cones over 5 seconds
            Log.d("STASH_DEBUG", "")
            Log.d("STASH_DEBUG", "PHASE 2: SIMULATING 3 CONE ACTIVITIES")
            Log.d("STASH_DEBUG", "-----------------------------------------------------------------")

            for (i in 1..3) {
                Log.d("STASH_DEBUG", "Simulating cone #$i...")
                Log.d("STASH_DEBUG", "  Would add cone at timestamp: ${System.currentTimeMillis()}")
                delay(1667) // ~5 seconds total for 3 cones
            }

            // Second run - after simulated activities
            Log.d("STASH_DEBUG", "")
            Log.d("STASH_DEBUG", "PHASE 3: RETEST AFTER SIMULATION")
            Log.d("STASH_DEBUG", "-----------------------------------------------------------------")
            runDebugTestCycle()

            // Show calculation details for key scenarios
            Log.d("STASH_DEBUG", "")
            Log.d("STASH_DEBUG", "PHASE 4: DETAILED CALCULATIONS")
            Log.d("STASH_DEBUG", "-----------------------------------------------------------------")
            showDebugCalculationDetails()

            Log.d("STASH_DEBUG", "")
            Log.d("STASH_DEBUG", "=================================================================")
            Log.d("STASH_DEBUG", "ENHANCED TEST COMPLETE")
            Log.d("STASH_DEBUG", "=================================================================")
        }
    }

    private suspend fun runDebugTestCycle() {
        val statsTypes = listOf(
            StashStatsCalculator.StatsType.PAST to "PAST",
            StashStatsCalculator.StatsType.CURRENT to "CURRENT",
            StashStatsCalculator.StatsType.PROJECTED to "PROJECTED"
        )

        val timePeriods = listOf(
            StashTimePeriod.THIS_SESH to "THIS_SESH",
            StashTimePeriod.HOUR to "HOUR",
            StashTimePeriod.TWELVE_H to "TWELVE_H",
            StashTimePeriod.TODAY to "TODAY",
            StashTimePeriod.WEEK to "WEEK",
            StashTimePeriod.MONTH to "MONTH",
            StashTimePeriod.YEAR to "YEAR"
        )

        val dataScopes = listOf(
            StashStatsCalculator.DataScope.MY_STASH to "MY_STASH",
            StashStatsCalculator.DataScope.THEIR_STASH to "THEIR_STASH",
            StashStatsCalculator.DataScope.ONLY_ME to "ONLY_ME"
        )

        for ((statsType, statsName) in statsTypes) {
            for ((timePeriod, periodName) in timePeriods) {
                for ((dataScope, scopeName) in dataScopes) {
                    Log.d("STASH_DEBUG", "")
                    Log.d("STASH_DEBUG", "TEST: $statsName + $periodName + $scopeName")

                    recalculateStats(statsType, timePeriod, dataScope)
                    delay(200)

                    val stats = _stashStats.value
                    if (stats != null) {
                        Log.d("STASH_DEBUG", "  Result: ${stats.counts[ActivityType.CONE] ?: 0}C ${stats.counts[ActivityType.BOWL] ?: 0}B = ${stats.totalGrams}g (\$${stats.totalCost})")

                        if (statsType == StashStatsCalculator.StatsType.PROJECTED && timePeriod == StashTimePeriod.THIS_SESH) {
                            Log.d("STASH_DEBUG", "  Projection Scale: ${stats.projectionScale ?: 1.0}")
                        }
                    } else {
                        Log.d("STASH_DEBUG", "  Result: NULL")
                    }
                }
            }
        }
    }

    private suspend fun showDebugCalculationDetails() {
        Log.d("STASH_DEBUG", "")
        Log.d("STASH_DEBUG", "Detailed Calculation: MY_STASH + CURRENT + SESH")
        Log.d("STASH_DEBUG", "-----------------------------------------------------------------")

        recalculateStats(
            StashStatsCalculator.StatsType.CURRENT,
            StashTimePeriod.THIS_SESH,
            StashStatsCalculator.DataScope.MY_STASH
        )
        delay(200)

        val stats = _stashStats.value
        if (stats != null) {
            Log.d("STASH_DEBUG", "Activities breakdown:")
            stats.distributions?.forEach { dist ->
                Log.d("STASH_DEBUG", "  ${dist.smokerName}:")
                Log.d("STASH_DEBUG", "    Cones: ${dist.cones} × ${ratios.value?.coneGrams ?: 0.3}g = ${dist.cones * (ratios.value?.coneGrams ?: 0.3)}g")
                Log.d("STASH_DEBUG", "    Bowls: ${dist.bowls} × ${ratios.value?.bowlGrams ?: 0.2}g = ${dist.bowls * (ratios.value?.bowlGrams ?: 0.2)}g")
                Log.d("STASH_DEBUG", "    Total: ${dist.totalGrams}g × \$${currentStash.value?.pricePerGram ?: 15.0}/g = \$${dist.totalCost}")
            }
            Log.d("STASH_DEBUG", "Grand Total: ${stats.totalGrams}g = \$${stats.totalCost}")
        }
    }

    fun setSessionStartTime(startTime: Long?) {
        sessionStartTime = startTime
        Log.d("STASH_VM", "Session start time set: $startTime")
    }

    fun loadCurrentStash() {
        viewModelScope.launch {
            val stash = withContext(Dispatchers.IO) { stashDao.getCurrentStash() }
            if (stash != null) {
                _currentStash.postValue(stash)
            } else {
                val newStash = Stash()
                withContext(Dispatchers.IO) { stashDao.insertStash(newStash) }
                _currentStash.postValue(newStash)
            }
        }
    }

    fun setLastCompletedSessionId(sessionId: Long?) {
        lastCompletedSessionId = sessionId
        Log.d("STASH_VM", "Last completed session ID set: $sessionId")
    }

    fun loadStashHistory() {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { stashDao.getStashHistory() }
            _stashHistory.postValue(history)
        }
    }

    fun loadRatios() {
        viewModelScope.launch {
            var ratios = withContext(Dispatchers.IO) { stashDao.getConsumptionRatios() }
            if (ratios == null) {
                ratios = ConsumptionRatio()
                withContext(Dispatchers.IO) { stashDao.insertConsumptionRatio(ratios) }
            }
            _ratios.postValue(ratios)
        }
    }

    fun updateRatios(
        coneGrams: Double?,
        jointGrams: Double,
        bowlGrams: Double,
        deductCones: Boolean = true,
        deductJoints: Boolean = true,
        deductBowls: Boolean = false
    ) {
        viewModelScope.launch {
            // Determine the effective cone grams value
            val effectiveConeGrams = if (coneGrams != null && coneGrams > 0) {
                // User provided a value, use it
                coneGrams
            } else if (calculatedConesPerBowl > 0) {
                // No user value, use auto-calculated
                bowlGrams / calculatedConesPerBowl
            } else {
                // No user value and no calculated value, use default
                0.3
            }

            val newRatios = ConsumptionRatio(
                coneGrams = effectiveConeGrams,
                jointGrams = jointGrams,
                bowlGrams = bowlGrams,
                userDefinedConeGrams = coneGrams, // Store the user input (or null)
                deductConesFromStash = deductCones,
                deductJointsFromStash = deductJoints,
                deductBowlsFromStash = deductBowls,
                lastUpdated = Date()
            )

            withContext(Dispatchers.IO) {
                stashDao.updateConsumptionRatio(newRatios)
            }
            _ratios.postValue(newRatios)

            Log.d(TAG, "Updated ratios - Cone: ${effectiveConeGrams}g (${if (coneGrams != null) "manual" else "auto"}), Joint: ${jointGrams}g, Bowl: ${bowlGrams}g")
            Log.d(TAG, "Deduction settings - Cones: $deductCones, Joints: $deductJoints, Bowls: $deductBowls")

            // Recalculate stats with new ratios
            recalculateStats()
        }
    }

    fun calculateConesPerBowl() {
        viewModelScope.launch {
            try {
                val bowlsWithCones = withContext(Dispatchers.IO) {
                    activityLogDao.getRecentBowlsWithCones(bowlType = ActivityType.BOWL, limit = 10)
                }

                if (bowlsWithCones.isNotEmpty()) {
                    val validBowls = bowlsWithCones
                        .filter { it.associatedConesCount != null && it.associatedConesCount!! > 0 }

                    if (validBowls.size >= 2) {
                        val avgConesPerBowl = validBowls
                            .mapNotNull { it.associatedConesCount }
                            .average()

                        if (avgConesPerBowl > 0 && avgConesPerBowl != calculatedConesPerBowl) {
                            calculatedConesPerBowl = avgConesPerBowl

                            val currentRatios = _ratios.value ?: return@launch

                            // Only update cone grams if user hasn't defined their own value
                            if (currentRatios.userDefinedConeGrams == null) {
                                val newConeGrams = currentRatios.bowlGrams / avgConesPerBowl
                                val updatedRatios = currentRatios.copy(coneGrams = newConeGrams)

                                withContext(Dispatchers.IO) {
                                    stashDao.updateConsumptionRatio(updatedRatios)
                                }
                                _ratios.postValue(updatedRatios)

                                Log.d(TAG, "Auto-updated cone ratio: ${avgConesPerBowl} cones/bowl = ${newConeGrams}g/cone")
                            } else {
                                Log.d(TAG, "Skipping auto-update: User has defined cone ratio as ${currentRatios.userDefinedConeGrams}g")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating cones per bowl", e)
            }
        }
    }

    fun getCalculatedConesPerBowl(): Double = calculatedConesPerBowl

    fun addToStash(grams: Double, pricePerGram: Double) {
        viewModelScope.launch {
            val current = _currentStash.value ?: Stash()
            val totalValue = (current.currentGrams * current.pricePerGram) + (grams * pricePerGram)
            val newTotalGrams = current.currentGrams + grams
            val newPricePerGram = if (newTotalGrams > 0) totalValue / newTotalGrams else pricePerGram

            val updated = current.copy(
                totalGrams = current.totalGrams + grams,
                currentGrams = newTotalGrams,
                pricePerGram = newPricePerGram,
                lastUpdated = Date()
            )
            withContext(Dispatchers.IO) {
                stashDao.updateStash(updated)
                val entry = StashEntry(
                    timestamp = Date(),
                    type = StashEntryType.ADD,
                    grams = grams,
                    pricePerGram = pricePerGram,
                    totalCost = grams * pricePerGram,
                    notes = "Added to stash"
                )
                stashDao.insertStashEntry(entry)
            }
            _currentStash.postValue(updated)
            loadStashHistory()
        }
    }

    fun recordConsumption(activityType: ActivityType, smokerUid: String, smokerName: String, timestamp: Long, bowlQuantity: Int = 1) {
        val source = _stashSource.value ?: return
        val current = _currentStash.value ?: return
        val currentRatios = _ratios.value ?: return

        // Check if this activity type should deduct from stash
        val shouldDeductType = when (activityType) {
            ActivityType.CONE -> currentRatios.deductConesFromStash
            ActivityType.JOINT -> currentRatios.deductJointsFromStash
            ActivityType.BOWL -> currentRatios.deductBowlsFromStash
            else -> false
        }

        if (!shouldDeductType) {
            Log.d(TAG, "$activityType deduction disabled - not deducting from stash")
            recalculateStats()
            return
        }

        // Store the current ratio for this activity
        activityRatios[timestamp] = currentRatios

        val shouldConsume = when (source) {
            StashSource.MY_STASH -> true
            StashSource.THEIR_STASH -> false
            StashSource.EACH_TO_OWN -> smokerUid == currentUserId
        }

        if (!shouldConsume) {
            Log.d(TAG, "Not consuming from stash: source=$source, smokerUid=$smokerUid, currentUserId=$currentUserId")
            recalculateStats()
            return
        }

        val ratioToUse = activityRatios[timestamp] ?: currentRatios

        val grams = when (activityType) {
            ActivityType.CONE -> ratioToUse.coneGrams
            ActivityType.JOINT -> ratioToUse.jointGrams
            ActivityType.BOWL -> ratioToUse.bowlGrams * bowlQuantity  // THIS IS ALREADY CORRECT IN YOUR CODE
            else -> 0.0
        }

        // IMPORTANT: Only consume if we actually have enough
        // Don't consume if insufficient (the switch should have already happened in MainActivity)
        if (grams > 0 && current.currentGrams >= grams) {
            viewModelScope.launch {
                val updated = current.copy(
                    currentGrams = (current.currentGrams - grams).coerceAtLeast(0.0),
                    lastUpdated = Date()
                )
                withContext(Dispatchers.IO) {
                    stashDao.updateStash(updated)
                    val entry = StashEntry(
                        timestamp = Date(timestamp),
                        type = StashEntryType.CONSUME,
                        grams = -grams,
                        pricePerGram = current.pricePerGram,
                        totalCost = -(grams * current.pricePerGram),
                        activityType = activityType,
                        smokerName = smokerName,
                        notes = "${activityType.name} by $smokerName (ratio: ${String.format("%.2f", grams)}g)"
                    )
                    stashDao.insertStashEntry(entry)
                }
                _currentStash.postValue(updated)
                loadStashHistory()

                // Recalculate stats after consumption
                recalculateStats()
            }
        } else {
            // Still recalculate stats even if no consumption
            // Log that we're not consuming because there's not enough
            if (grams > 0 && current.currentGrams < grams) {
                Log.d(TAG, "Not consuming from stash: insufficient grams (needed: $grams, available: ${current.currentGrams})")
            }
            recalculateStats()
        }
    }

    /**
     * Undo the stash consumption for a specific activity
     * This reverses the consumption and removes the history entry
     */
    fun undoStashConsumption(
        activityLog: ActivityLog,
        smokerName: String? = null
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔙 Undoing stash consumption for ${activityLog.type} at ${activityLog.timestamp}")
                Log.d(TAG, "🔙 PayerStashOwnerId: '${activityLog.payerStashOwnerId}'")
                Log.d(TAG, "🔙 Activity had ${activityLog.gramsAtLog}g at ${activityLog.pricePerGramAtLog}/g")

                val current = _currentStash.value ?: return@launch
                val currentRatios = _ratios.value ?: return@launch

                // Check if this activity type was deducting from stash
                val wasDeducting = when (activityLog.type) {
                    ActivityType.CONE -> currentRatios.deductConesFromStash
                    ActivityType.JOINT -> currentRatios.deductJointsFromStash
                    ActivityType.BOWL -> currentRatios.deductBowlsFromStash
                    else -> false
                }

                if (!wasDeducting) {
                    Log.d(TAG, "🔙 ${activityLog.type} wasn't set to deduct from stash, skipping physical restoration")
                    // Still add a history entry and recalculate stats
                    withContext(Dispatchers.IO) {
                        val reversalEntry = StashEntry(
                            timestamp = Date(),
                            type = StashEntryType.ADJUST,
                            grams = 0.0,
                            pricePerGram = 0.0,
                            totalCost = 0.0,
                            activityType = activityLog.type,
                            smokerName = smokerName,
                            notes = "Undo: ${activityLog.type.name} (deduction was disabled)"
                        )
                        stashDao.insertStashEntry(reversalEntry)
                    }
                    loadStashHistory()
                    recalculateStats()
                    return@launch
                }

                // Get the grams that were consumed (use stored values if available, otherwise use current ratios)
                val gramsToProcess = when {
                    activityLog.gramsAtLog > 0 -> activityLog.gramsAtLog
                    else -> when (activityLog.type) {
                        ActivityType.CONE -> currentRatios.coneGrams
                        ActivityType.JOINT -> currentRatios.jointGrams
                        ActivityType.BOWL -> currentRatios.bowlGrams
                        else -> 0.0
                    }
                }

                val pricePerGram = if (activityLog.pricePerGramAtLog > 0) {
                    activityLog.pricePerGramAtLog
                } else {
                    current.pricePerGram
                }

                val costToProcess = gramsToProcess * pricePerGram

                Log.d(TAG, "🔙 Activity details: ${gramsToProcess}g @ $${pricePerGram}/g = $${costToProcess}")

                // Determine what type of undo to perform based on payerStashOwnerId
                when (activityLog.payerStashOwnerId) {
                    null -> {
                        // MY_STASH - Restore the consumed grams back to My Stash
                        Log.d(TAG, "🔙 MY_STASH activity - restoring ${gramsToProcess}g to My Stash")

                        if (gramsToProcess <= 0) {
                            Log.d(TAG, "🔙 No grams to restore")
                            recalculateStats()
                            return@launch
                        }

                        // Restore the stash amount
                        val newCurrentGrams = current.currentGrams + gramsToProcess

                        // Recalculate weighted average price per gram
                        val currentTotalValue = current.currentGrams * current.pricePerGram
                        val newTotalValue = currentTotalValue + costToProcess
                        val newPricePerGram = if (newCurrentGrams > 0) {
                            newTotalValue / newCurrentGrams
                        } else {
                            pricePerGram
                        }

                        val updated = current.copy(
                            currentGrams = newCurrentGrams,
                            pricePerGram = newPricePerGram,
                            lastUpdated = Date()
                        )

                        withContext(Dispatchers.IO) {
                            // Update stash
                            stashDao.updateStash(updated)

                            // Add a reversal entry to history
                            val reversalEntry = StashEntry(
                                timestamp = Date(),
                                type = StashEntryType.ADJUST,
                                grams = gramsToProcess,
                                pricePerGram = pricePerGram,
                                totalCost = costToProcess,
                                activityType = activityLog.type,
                                smokerName = smokerName,
                                notes = "Undo (My Stash): ${activityLog.type.name} - restored ${String.format("%.2f", gramsToProcess)}g"
                            )
                            stashDao.insertStashEntry(reversalEntry)
                        }

                        _currentStash.postValue(updated)
                        Log.d(TAG, "🔙 ✅ My Stash restored - new amount: ${String.format("%.2f", newCurrentGrams)}g @ $${String.format("%.2f", newPricePerGram)}/g")
                    }

                    "their_stash" -> {
                        // THEIR_STASH - Track the reversal (reduce their stash consumption tracking)
                        Log.d(TAG, "🔙 THEIR_STASH activity - tracking reversal of ${gramsToProcess}g")

                        // Note: We're not modifying My Stash here, just tracking that Their Stash consumption is reduced
                        withContext(Dispatchers.IO) {
                            val reversalEntry = StashEntry(
                                timestamp = Date(),
                                type = StashEntryType.ADJUST,
                                grams = -gramsToProcess, // Negative to show reduction in Their Stash consumption
                                pricePerGram = pricePerGram,
                                totalCost = -costToProcess,
                                activityType = activityLog.type,
                                smokerName = smokerName,
                                notes = "Undo (Their Stash): ${activityLog.type.name} - reversed ${String.format("%.2f", gramsToProcess)}g"
                            )
                            stashDao.insertStashEntry(reversalEntry)
                        }

                        Log.d(TAG, "🔙 ✅ Their Stash tracking reversed")
                    }

                    else -> {
                        // EACH_TO_OWN (other person's stash) - starts with "other_"
                        if (activityLog.payerStashOwnerId?.startsWith("other_") == true) {
                            Log.d(TAG, "🔙 EACH_TO_OWN (other's stash) - tracking reversal of ${gramsToProcess}g")

                            // Track as Their Stash reversal
                            withContext(Dispatchers.IO) {
                                val reversalEntry = StashEntry(
                                    timestamp = Date(),
                                    type = StashEntryType.ADJUST,
                                    grams = -gramsToProcess, // Negative to show reduction
                                    pricePerGram = pricePerGram,
                                    totalCost = -costToProcess,
                                    activityType = activityLog.type,
                                    smokerName = smokerName,
                                    notes = "Undo (Each-to-Own/Other): ${activityLog.type.name} - reversed ${String.format("%.2f", gramsToProcess)}g"
                                )
                                stashDao.insertStashEntry(reversalEntry)
                            }

                            Log.d(TAG, "🔙 ✅ Each-to-Own (other's stash) tracking reversed")
                        } else {
                            // Unknown payerStashOwnerId - log warning but don't crash
                            Log.w(TAG, "🔙 Unknown payerStashOwnerId: '${activityLog.payerStashOwnerId}'")

                            withContext(Dispatchers.IO) {
                                val reversalEntry = StashEntry(
                                    timestamp = Date(),
                                    type = StashEntryType.ADJUST,
                                    grams = 0.0,
                                    pricePerGram = 0.0,
                                    totalCost = 0.0,
                                    activityType = activityLog.type,
                                    smokerName = smokerName,
                                    notes = "Undo: ${activityLog.type.name} (unknown source: ${activityLog.payerStashOwnerId})"
                                )
                                stashDao.insertStashEntry(reversalEntry)
                            }
                        }
                    }
                }

                // Always reload history and recalculate stats
                loadStashHistory()
                recalculateStats()

                Log.d(TAG, "🔙 ✅ Undo stash consumption completed")

            } catch (e: Exception) {
                Log.e(TAG, "🔙 ❌ Error undoing stash consumption", e)
            }
        }
    }

    /**
     * Main method to recalculate stats based on current configuration
     */
    fun recalculateStats(
        statsType: StashStatsCalculator.StatsType = currentStatsType,
        timePeriod: StashTimePeriod = currentTimePeriod,
        dataScope: StashStatsCalculator.DataScope = currentDataScope
    ) {
        // Store current selections
        currentStatsType = statsType
        currentTimePeriod = timePeriod
        currentDataScope = dataScope
        
        // Start or stop the projection update timer based on stats type
        if (statsType == StashStatsCalculator.StatsType.PROJECTED) {
            startProjectionUpdateTimer()
        } else {
            stopProjectionUpdateTimer()
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "Recalculating stats: $statsType, $timePeriod, $dataScope")

                // Use the existing statsCalculator instance and pass currentUserId
                val stats = statsCalculator.calculate(
                    statsType = statsType,
                    timePeriod = timePeriod,
                    dataScope = dataScope,
                    sessionStartTime = sessionStartTime,
                    lastCompletedSessionId = lastCompletedSessionId,
                    currentUserId = currentUserId  // Pass the stored user ID
                )

                _stashStats.value = stats
                Log.d(TAG, "Stats updated: ${stats.totalGrams}g, $${stats.totalCost}")
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating stats", e)
                _stashStats.value = null
            }
        }
    }

    // Backward compatibility methods that use the new calculator
    fun loadHistoricalStats(period: StashTimePeriod) {
        recalculateStats(StashStatsCalculator.StatsType.CURRENT, period, currentDataScope)
    }

    fun loadPreviousPeriodStats() {
        recalculateStats(StashStatsCalculator.StatsType.PAST, currentTimePeriod, currentDataScope)
    }

    fun loadProjectedStats(basePeriod: StashTimePeriod = StashTimePeriod.WEEK) {
        recalculateStats(StashStatsCalculator.StatsType.PROJECTED, basePeriod, currentDataScope)
    }

    fun removeFromStash(gramsToRemove: Double, costToRemove: Double) {
        viewModelScope.launch {
            val current = _currentStash.value ?: return@launch
            if (gramsToRemove <= 0) return@launch

            val newCurrentGrams = (current.currentGrams - gramsToRemove).coerceAtLeast(0.0)

            val currentTotalValue = current.currentGrams * current.pricePerGram
            val newTotalValue = (currentTotalValue - costToRemove).coerceAtLeast(0.0)
            val newPricePerGram = if (newCurrentGrams > 0) {
                newTotalValue / newCurrentGrams
            } else {
                0.0
            }

            val updated = current.copy(
                currentGrams = newCurrentGrams,
                pricePerGram = newPricePerGram,
                lastUpdated = Date()
            )

            withContext(Dispatchers.IO) {
                stashDao.updateStash(updated)

                val entry = StashEntry(
                    timestamp = Date(),
                    type = StashEntryType.REMOVE,
                    grams = -gramsToRemove,
                    pricePerGram = if (gramsToRemove > 0) costToRemove / gramsToRemove else 0.0,
                    totalCost = -costToRemove,
                    notes = "Manually removed from stash"
                )
                stashDao.insertStashEntry(entry)
            }
            _currentStash.postValue(updated)
            loadStashHistory()
            recalculateStats()
        }
    }

    fun removeAllFromStash() {
        viewModelScope.launch {
            val current = _currentStash.value ?: return@launch
            if (current.currentGrams <= 0) return@launch

            val totalValue = current.currentGrams * current.pricePerGram

            val updated = current.copy(
                currentGrams = 0.0,
                pricePerGram = 0.0,
                lastUpdated = Date()
            )

            withContext(Dispatchers.IO) {
                stashDao.updateStash(updated)
                val entry = StashEntry(
                    timestamp = Date(),
                    type = StashEntryType.REMOVE,
                    grams = -current.currentGrams,
                    pricePerGram = current.pricePerGram,
                    totalCost = -totalValue,
                    notes = "Removed all from stash"
                )
                stashDao.insertStashEntry(entry)
            }
            _currentStash.postValue(updated)
            loadStashHistory()
            recalculateStats()
        }
    }

    fun updateStashSource(source: StashSource) {
        _stashSource.value = source
    }

    fun updateDataScope(scope: StashStatsCalculator.DataScope) {
        currentDataScope = scope
        recalculateStats()
    }
    
    /**
     * Call this when the Stash tab becomes visible
     */
    fun onStashTabResumed() {
        Log.d("PROJ_TIMER", "onStashTabResumed called - Current stats type: $currentStatsType")
        if (currentStatsType == StashStatsCalculator.StatsType.PROJECTED) {
            Log.d("PROJ_TIMER", "Tab resumed with PROJECTED mode - starting timer")
            startProjectionUpdateTimer()
        } else {
            Log.d("PROJ_TIMER", "Tab resumed but not in PROJECTED mode - no timer needed")
        }
    }
    
    /**
     * Call this when the Stash tab is no longer visible
     */
    fun onStashTabPaused() {
        Log.d("PROJ_TIMER", "onStashTabPaused called - stopping any active timer")
        stopProjectionUpdateTimer()
    }

    fun onBowlActivityLogged() {
        calculateConesPerBowl()
    }

    fun refreshStatsAfterSessionChange() {
        Log.d(TAG, "📊 Refreshing stats after session change")
        // Trigger recalculation with current settings
        recalculateStats(currentStatsType, currentTimePeriod, currentDataScope)
    }

    fun forceStatsRefresh() {
        Log.d(TAG, "📊 Force refreshing all stats")
        viewModelScope.launch {
            // Small delay to ensure database writes are complete
            delay(100)
            recalculateStats(currentStatsType, currentTimePeriod, currentDataScope)
        }
    }

    fun onActivityLogged(activityType: ActivityType) {
        if (activityType == ActivityType.BOWL || activityType == ActivityType.CONE) {
            calculateConesPerBowl()
        }

        // Force immediate recalculation
        viewModelScope.launch {
            delay(100) // Small delay to ensure database write completes
            recalculateStats()
        }
    }

    fun debugTheirStashData() {
        viewModelScope.launch {
            Log.d("STASH_DEBUG", "")
            Log.d("STASH_DEBUG", "════════════════════════════════════════════════════════════════")
            Log.d("STASH_DEBUG", "THEIR STASH DATA DEBUG")
            Log.d("STASH_DEBUG", "════════════════════════════════════════════════════════════════")

            // Get all recent activities
            val recentActivities = withContext(Dispatchers.IO) {
                activityLogDao.getRecentActivities(50)
            }

            Log.d("STASH_DEBUG", "Recent activities (last 50):")
            Log.d("STASH_DEBUG", "")

            // Group by payerStashOwnerId
            val grouped = recentActivities.groupBy { it.payerStashOwnerId }

            grouped.forEach { (payerId, activities) ->
                val displayId = payerId ?: "MY_STASH (null)"
                Log.d("STASH_DEBUG", "PayerStashOwnerId: '$displayId'")
                Log.d("STASH_DEBUG", "  Count: ${activities.size} activities")

                activities.take(3).forEach { activity ->
                    Log.d("STASH_DEBUG", "    - ${activity.type} at ${java.util.Date(activity.timestamp)}")
                }
                Log.d("STASH_DEBUG", "")
            }

            // Count by type
            val theirStashCount = recentActivities.count { it.payerStashOwnerId == "their_stash" }
            val myStashCount = recentActivities.count { it.payerStashOwnerId == null }
            val otherCount = recentActivities.count {
                it.payerStashOwnerId != null && it.payerStashOwnerId != "their_stash"
            }

            Log.d("STASH_DEBUG", "Summary:")
            Log.d("STASH_DEBUG", "  My Stash (null): $myStashCount activities")
            Log.d("STASH_DEBUG", "  Their Stash ('their_stash'): $theirStashCount activities")
            Log.d("STASH_DEBUG", "  Other/Each-to-own: $otherCount activities")

            if (theirStashCount == 0) {
                Log.d("STASH_DEBUG", "")
                Log.d("STASH_DEBUG", "⚠️ NO ACTIVITIES FOUND WITH payerStashOwnerId = 'their_stash'")
                Log.d("STASH_DEBUG", "This explains why Their Stash chip shows no data!")
                Log.d("STASH_DEBUG", "")
                Log.d("STASH_DEBUG", "To fix: Make sure when 'Their Stash' radio is selected,")
                Log.d("STASH_DEBUG", "activities are saved with payerStashOwnerId = 'their_stash'")
            }

            Log.d("STASH_DEBUG", "════════════════════════════════════════════════════════════════")
        }
    }

    fun runMinimalDebugTest() {
        viewModelScope.launch {
            Log.d("STASH_DEBUG", "")
            Log.d("STASH_DEBUG", "════════════════════════════════════════════════════════════════")
            Log.d("STASH_DEBUG", "COMPREHENSIVE DEBUG TEST - ALL 3x3x7 COMBINATIONS")
            Log.d("STASH_DEBUG", "════════════════════════════════════════════════════════════════")

            // Log current state
            Log.d("STASH_DEBUG", "Current State:")
            Log.d("STASH_DEBUG", "  Session Start Time: $sessionStartTime (${sessionStartTime?.let { Date(it) }})")
            Log.d("STASH_DEBUG", "  Last Completed Session ID: $lastCompletedSessionId (${lastCompletedSessionId?.let { Date(it) }})")
            Log.d("STASH_DEBUG", "  Current User ID: $currentUserId")
            Log.d("STASH_DEBUG", "")

            val allDataScopes = StashStatsCalculator.DataScope.values()
            val allStatsTypes = StashStatsCalculator.StatsType.values()
            val allTimePeriods = StashTimePeriod.values()

            // Loop through every single combination
            for (dataScope in allDataScopes) {
                Log.d("STASH_DEBUG", "")
                Log.d("STASH_DEBUG", "═══ SCOPE: $dataScope ═══")

                for (statsType in allStatsTypes) {
                    for (timePeriod in allTimePeriods) {
                        Log.d("STASH_DEBUG", "────────────────────────────────────────────────────────────────")
                        Log.d("STASH_DEBUG", "Testing: $statsType + $timePeriod + $dataScope")

                        recalculateStats(statsType, timePeriod, dataScope)
                        delay(100) // Brief delay for LiveData to update

                        val stats = _stashStats.value
                        if (stats != null) {
                            val cones = stats.counts[ActivityType.CONE] ?: 0
                            val bowls = stats.counts[ActivityType.BOWL] ?: 0
                            val joints = stats.counts[ActivityType.JOINT] ?: 0

                            val resultSymbol = if (stats.totalGrams > 0) "✓" else "○"

                            Log.d("STASH_DEBUG", "  $resultSymbol Result: ${cones}C ${bowls}B ${joints}J")
                            Log.d("STASH_DEBUG", "    Grams: ${String.format("%.2f", stats.totalGrams)}g")
                            Log.d("STASH_DEBUG", "    Cost: $${String.format("%.2f", stats.totalCost)}")

                            // Log projection scale where applicable
                            if (statsType == StashStatsCalculator.StatsType.PROJECTED) {
                                val scale = stats.projectionScale ?: 1.0
                                val scaleWarning = if (scale > 10.0) " ⚠️ HIGH" else ""
                                Log.d("STASH_DEBUG", "    Projection Scale: ${String.format("%.2f", scale)}$scaleWarning")
                            }

                            // Always log the time window for clarity
                            if (stats.windowStartMs > 0) {
                                Log.d("STASH_DEBUG", "    Window: ${Date(stats.windowStartMs)} to ${Date(stats.windowEndMs)}")
                            }

                            // Log user distribution for session stats if available
                            if (timePeriod == StashTimePeriod.THIS_SESH && !stats.distributions.isNullOrEmpty()) {
                                Log.d("STASH_DEBUG", "    Users (${stats.distributions.size}): ${stats.distributions.joinToString { it.smokerName }}")
                            }
                        } else {
                            Log.d("STASH_DEBUG", "  ✗ Result: NULL")
                        }
                    }
                }
            }

            // Final summary
            Log.d("STASH_DEBUG", "")
            Log.d("STASH_DEBUG", "════════════════════════════════════════════════════════════════")
            Log.d("STASH_DEBUG", "SUMMARY")
            Log.d("STASH_DEBUG", "────────────────────────────────────────────────────────────────")
            Log.d("STASH_DEBUG", "Check for:")
            Log.d("STASH_DEBUG", "  ✓ = Has data")
            Log.d("STASH_DEBUG", "  ○ = No data (may be expected)")
            Log.d("STASH_DEBUG", "  ✗ = NULL result (error)")
            Log.d("STASH_DEBUG", "")
            Log.d("STASH_DEBUG", "Look for HIGH projection scales and correct Window dates.")
            Log.d("STASH_DEBUG", "════════════════════════════════════════════════════════════════")
            Log.d("STASH_DEBUG", "COMPREHENSIVE TEST COMPLETE")
            Log.d("STASH_DEBUG", "════════════════════════════════════════════════════════════════")
        }
    }
    
    /**
     * Start the projection update timer for real-time updates
     */
    private fun startProjectionUpdateTimer() {
        // Cancel any existing timer
        stopProjectionUpdateTimer()
        
        Log.d("PROJ_TIMER", "════════════════════════════════════════════════════════")
        Log.d("PROJ_TIMER", "STARTING PROJECTION TIMER")
        Log.d("PROJ_TIMER", "Current stats type: $currentStatsType")
        Log.d("PROJ_TIMER", "Current time period: $currentTimePeriod")
        Log.d("PROJ_TIMER", "Current data scope: $currentDataScope")
        Log.d("PROJ_TIMER", "Timer interval: ${PROJECTION_UPDATE_INTERVAL_MS}ms")
        Log.d("PROJ_TIMER", "════════════════════════════════════════════════════════")
        
        projectionUpdateJob = viewModelScope.launch(Dispatchers.IO) {
            var tickCount = 0
            while (true) {
                delay(PROJECTION_UPDATE_INTERVAL_MS)
                tickCount++
                
                // Only update if we're still in PROJECTED mode
                if (currentStatsType == StashStatsCalculator.StatsType.PROJECTED) {
                    val currentTime = System.currentTimeMillis()
                    Log.d("PROJ_TIMER", "────────────────────────────────────────────────────────")
                    Log.d("PROJ_TIMER", "TIMER TICK #$tickCount at ${java.util.Date(currentTime)}")
                    Log.d("PROJ_TIMER", "Stats type: $currentStatsType")
                    Log.d("PROJ_TIMER", "Time period: $currentTimePeriod")
                    Log.d("PROJ_TIMER", "Data scope: $currentDataScope")
                    
                    try {
                        // Recalculate stats with current timestamp
                        Log.d("PROJ_TIMER", "Calculating new projection...")
                        val stats = statsCalculator.calculate(
                            statsType = StashStatsCalculator.StatsType.PROJECTED,
                            timePeriod = currentTimePeriod,
                            dataScope = currentDataScope,
                            sessionStartTime = sessionStartTime,
                            lastCompletedSessionId = lastCompletedSessionId,
                            currentUserId = currentUserId
                        )
                        
                        Log.d("PROJ_TIMER", "Calculation complete. Total grams: ${stats.totalGrams}")
                        Log.d("PROJ_TIMER", "Projection scale: ${stats.projectionScale}")
                        
                        // Update the LiveData on main thread
                        withContext(Dispatchers.Main) {
                            Log.d("PROJ_TIMER", "Updating LiveData on main thread...")
                            _stashStats.value = stats
                            Log.d("PROJ_TIMER", "LiveData updated successfully")
                        }
                        
                        Log.d("PROJ_TIMER", "────────────────────────────────────────────────────────")
                    } catch (e: Exception) {
                        Log.e("PROJ_TIMER", "ERROR during projection update: ${e.message}", e)
                        Log.d("PROJ_TIMER", "────────────────────────────────────────────────────────")
                    }
                } else {
                    Log.d("PROJ_TIMER", "════════════════════════════════════════════════════════")
                    Log.d("PROJ_TIMER", "STOPPING TIMER - No longer in PROJECTED mode")
                    Log.d("PROJ_TIMER", "Current mode: $currentStatsType")
                    Log.d("PROJ_TIMER", "Total ticks: $tickCount")
                    Log.d("PROJ_TIMER", "════════════════════════════════════════════════════════")
                    break
                }
            }
        }
    }
    
    /**
     * Stop the projection update timer
     */
    private fun stopProjectionUpdateTimer() {
        if (projectionUpdateJob != null) {
            Log.d("PROJ_TIMER", "════════════════════════════════════════════════════════")
            Log.d("PROJ_TIMER", "STOPPING PROJECTION TIMER")
            Log.d("PROJ_TIMER", "Job was active: ${projectionUpdateJob?.isActive}")
            Log.d("PROJ_TIMER", "════════════════════════════════════════════════════════")
            projectionUpdateJob?.cancel()
            projectionUpdateJob = null
        } else {
            Log.d("PROJ_TIMER", "No timer to stop (already null)")
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopProjectionUpdateTimer()
    }

}