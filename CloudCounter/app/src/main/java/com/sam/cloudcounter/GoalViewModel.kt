package com.sam.cloudcounter

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class GoalViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ActivityRepository =
        (application as CloudCounterApplication).repository
    private val goalDao = AppDatabase.getDatabase(application).goalDao()

    val allGoals: LiveData<List<Goal>> = goalDao.getAllGoals()
    val allGoalsSorted: LiveData<List<Goal>> = goalDao.getAllGoalsSorted()
    val activeGoals: LiveData<List<Goal>> = goalDao.getActiveGoals().asLiveData()

    fun insertGoal(goal: Goal) {
        viewModelScope.launch {
            goalDao.insertGoal(goal)
        }
    }

    fun forceRefreshGoals() {
        viewModelScope.launch {
            // This forces the LiveData to re-query from the database
            // by triggering any operation that causes a database read
            checkTimeBasedGoals()
        }
    }

    fun updateGoal(goal: Goal) {
        viewModelScope.launch {
            goalDao.updateGoal(goal)
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            goalDao.deleteGoal(goal)
        }
    }

    fun checkTimeBasedGoals() {
        viewModelScope.launch {
            try {
                // Use first() on the Flow to get a single emission
                val goals = goalDao.getActiveGoals().first()

                Log.d("GoalViewModel", "🎯 Checking ${goals.size} active goals for time expiry")

                goals.forEach { goal ->
                    if (goal.goalType == GoalType.TIME_BASED && goal.isActive) {
                        val now = System.currentTimeMillis()
                        val elapsed = now - goal.lastResetAt
                        val duration = getGoalDurationMillis(goal)

                        Log.d("GoalViewModel", "🎯 Goal ${goal.goalId}: elapsed=$elapsed, duration=$duration")

                        if (elapsed >= duration) {
                            Log.d("GoalViewModel", "🎯 Time-based goal ${goal.goalId} expired!")

                            when {
                                goal.isRecurring -> {
                                    Log.d("GoalViewModel", "🎯 Resetting recurring goal")
                                    goalDao.resetGoalProgress(goal.goalId, now)
                                }
                                !goal.allowOverflow -> {
                                    // Auto-end is enabled (allowOverflow = false)
                                    Log.d("GoalViewModel", "🎯 Auto-end enabled, marking as completed")
                                    goalDao.markGoalCompleted(goal.goalId, now)
                                }
                                else -> {
                                    Log.d("GoalViewModel", "🎯 Continue tracking enabled, goal continues")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GoalViewModel", "🎯 Error checking time-based goals", e)
            }
        }
    }

    private fun getGoalDurationMillis(goal: Goal): Long {
        val baseMillis = when (goal.timeUnit) {
            TimeUnit.MINUTE -> 60000L  // ADD THIS
            TimeUnit.HOUR -> 3600000L
            TimeUnit.DAY -> 86400000L
            TimeUnit.WEEK -> 604800000L
            TimeUnit.FORTNIGHT -> 1209600000L
            TimeUnit.MONTH -> 2592000000L
            TimeUnit.YEAR -> 31536000000L
            null -> 0L
        }
        return baseMillis * (goal.timeDuration ?: 1)
    }

    fun updateGoalProgress(goalId: Long, activityType: ActivityType, amount: Int = 1) {
        viewModelScope.launch {
            val goal = goalDao.getGoalById(goalId) ?: return@launch

            val newJoints = if (activityType == ActivityType.JOINT) goal.currentJoints + amount else goal.currentJoints
            val newCones = if (activityType == ActivityType.CONE) goal.currentCones + amount else goal.currentCones
            val newBowls = if (activityType == ActivityType.BOWL) goal.currentBowls + amount else goal.currentBowls

            goalDao.updateGoalProgress(goalId, newJoints, newCones, newBowls)

            // Check if goal is completed
            checkGoalCompletion(goal.copy(
                currentJoints = newJoints,
                currentCones = newCones,
                currentBowls = newBowls
            ))
        }
    }

    private suspend fun checkGoalCompletion(goal: Goal) {
        val totalTarget = goal.targetJoints + goal.targetCones + goal.targetBowls
        val totalCurrent = goal.currentJoints + goal.currentCones + goal.currentBowls

        if (totalCurrent >= totalTarget && goal.isActive) {
            when {
                goal.allowOverflow -> {
                    // Continue tracking, don't mark as completed
                    return
                }
                goal.isRecurring -> {
                    // Reset for next cycle
                    goalDao.resetGoalProgress(goal.goalId, System.currentTimeMillis())
                }
                else -> {
                    // Mark as completed (auto-end enabled)
                    goalDao.markGoalCompleted(goal.goalId, System.currentTimeMillis())
                }
            }
        }
    }

    fun checkAndResetTimeBasedGoals() {
        viewModelScope.launch {
            val activeGoals = goalDao.getActiveGoals()
            activeGoals.collect { goals ->
                goals.forEach { goal ->
                    if (goal.goalType == GoalType.TIME_BASED && goal.isRecurring) {
                        if (shouldResetGoal(goal)) {
                            goalDao.resetGoalProgress(goal.goalId, System.currentTimeMillis())
                        }
                    }
                }
            }
        }
    }

    private fun shouldResetGoal(goal: Goal): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceReset = now - goal.lastResetAt

        val resetInterval = when (goal.timeUnit) {
            TimeUnit.MINUTE -> 60000L  // ADD THIS
            TimeUnit.HOUR -> 3600000L
            TimeUnit.DAY -> 86400000L
            TimeUnit.WEEK -> 604800000L
            TimeUnit.FORTNIGHT -> 1209600000L
            TimeUnit.MONTH -> 2592000000L
            TimeUnit.YEAR -> 31536000000L
            null -> return false
        }

        return timeSinceReset >= (resetInterval * (goal.timeDuration ?: 1))
    }
}