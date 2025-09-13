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
     * Custom activities are excluded from stash system entirely.
     */
    fun handleActivity(
        activityType: ActivityType,
        smokerUid: String,
        smokerName: String,
        timestamp: Long
    ) {
        // IMPORTANT: Only process core activity types - exclude custom activities from stash system
        when (activityType) {
            ActivityType.JOINT,
            ActivityType.CONE,
            ActivityType.BOWL -> {
                Log.d(TAG, "Processing ${activityType.name} activity for stash consumption")
                stashViewModel.recordConsumption(activityType, smokerUid, smokerName, timestamp)
            }
            else -> {
                Log.d(TAG, "Skipping ${activityType.name} activity - custom activities don't interact with stash")
            }
        }
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

            // IMPORTANT: Only count core activity types - exclude custom activities from stash calculations
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