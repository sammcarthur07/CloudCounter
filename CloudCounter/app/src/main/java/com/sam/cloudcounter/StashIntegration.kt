package com.sam.cloudcounter

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles integration between stash tracking and activity logging
 */
class StashIntegration(
    private val repository: ActivityRepository,
    private val stashViewModel: StashViewModel,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "StashIntegration"
    }

    /**
     * Process an activity for stash consumption.
     * This is now a simple pass-through to the ViewModel which holds the logic.
     */
    fun handleActivity(
        activityType: ActivityType,
        smokerUid: String,
        smokerName: String,
        timestamp: Long
    ) {
        // The ViewModel now contains all the necessary logic for recording consumption.
        stashViewModel.recordConsumption(activityType, smokerUid, smokerName, timestamp)
    }

    /**
     * Calculate session consumption stats.
     */
    suspend fun calculateSessionConsumption(
        sessionStart: Long,
        sessionEnd: Long
    ): SessionConsumptionStats {
        return withContext(Dispatchers.IO) {
            val activities = repository.getLogsInTimeRange(sessionStart, sessionEnd)

            // Get the current ratio and stash data from the ViewModel's LiveData
            val ratios = stashViewModel.ratios.value ?: ConsumptionRatio()
            val stash = stashViewModel.currentStash.value ?: Stash()

            val cones = activities.count { it.type == ActivityType.CONE }
            val joints = activities.count { it.type == ActivityType.JOINT }
            val bowls = activities.count { it.type == ActivityType.BOWL }

            // Calculate total grams and cost using the retrieved data
            val totalGrams = (cones * ratios.coneGrams) + (joints * ratios.jointGrams) + (bowls * ratios.bowlGrams)
            val totalCost = totalGrams * stash.pricePerGram

            SessionConsumptionStats(
                totalGrams = totalGrams,
                totalCost = totalCost,
                cones = cones,
                joints = joints,
                bowls = bowls
            )
        }
    }
}

data class SessionConsumptionStats(
    val totalGrams: Double,
    val totalCost: Double,
    val cones: Int,
    val joints: Int,
    val bowls: Int
)