package com.sam.cloudcounter

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Insert
    suspend fun insertGoal(goal: Goal): Long

    @Update
    suspend fun updateGoal(goal: Goal)

    @Delete
    suspend fun deleteGoal(goal: Goal)

    @Query("UPDATE goals SET isPaused = :paused, wasManuallyPaused = :wasManuallyPaused WHERE goalType = 'CURRENT_SESSION' AND sessionShareCode = :sessionShareCode")
    suspend fun updateSessionGoalsPauseStatus(sessionShareCode: String, paused: Boolean, wasManuallyPaused: Boolean)

    @Query("UPDATE goals SET sessionStartDate = :startDate, sessionEndDate = :endDate WHERE goalType = 'CURRENT_SESSION' AND sessionShareCode = :sessionShareCode")
    suspend fun updateSessionDates(sessionShareCode: String, startDate: Long?, endDate: Long?)

    @Query("UPDATE goals SET notificationsEnabled = :enabled WHERE goalId = :goalId")
    suspend fun updateNotificationStatus(goalId: Long, enabled: Boolean)

    @Query("SELECT * FROM goals WHERE notificationsEnabled = 1")
    fun getGoalsWithNotificationsEnabled(): Flow<List<Goal>>

    @Query("UPDATE goals SET currentJoints = :joints, currentCones = :cones, currentBowls = :bowls, smokerProgress = :smokerProgress WHERE goalId = :goalId")
    suspend fun updateGoalProgressWithSmoker(goalId: Long, joints: Int, cones: Int, bowls: Int, smokerProgress: String)

    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun getAllGoals(): LiveData<List<Goal>>

    @Query("SELECT * FROM goals WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getActiveGoals(): Flow<List<Goal>>

    @Query("""
        SELECT * FROM goals 
        ORDER BY 
            CASE 
                WHEN isPaused = 1 THEN 0
                WHEN isActive = 1 THEN 1
                ELSE 2
            END,
            createdAt DESC
    """)
    fun getAllGoalsSorted(): LiveData<List<Goal>>

    @Query("SELECT * FROM goals WHERE goalId = :goalId")
    suspend fun getGoalById(goalId: Long): Goal?

    @Query("UPDATE goals SET currentJoints = :joints, currentCones = :cones, currentBowls = :bowls WHERE goalId = :goalId")
    suspend fun updateGoalProgress(goalId: Long, joints: Int, cones: Int, bowls: Int)

    @Query("UPDATE goals SET isActive = 0, completedAt = :completedAt WHERE goalId = :goalId")
    suspend fun markGoalCompleted(goalId: Long, completedAt: Long)

    @Query("UPDATE goals SET currentJoints = 0, currentCones = 0, currentBowls = 0, lastResetAt = :resetTime, completedRounds = completedRounds + 1 WHERE goalId = :goalId")
    suspend fun resetGoalProgress(goalId: Long, resetTime: Long)

    @Query("UPDATE goals SET lastNotificationPercentage = :percentage WHERE goalId = :goalId")
    suspend fun updateLastNotificationPercentage(goalId: Long, percentage: Int)

    @Query("UPDATE goals SET customActivities = :customActivities WHERE goalId = :goalId")
    suspend fun updateGoalCustomActivities(goalId: Long, customActivities: String)

    @Query("UPDATE goals SET smokerProgress = :smokerProgress WHERE goalId = :goalId")
    suspend fun updateGoalSmokerProgress(goalId: Long, smokerProgress: String)

    @Query("UPDATE goals SET currentValue = :currentValue WHERE goalId = :goalId")
    suspend fun updateGoalCurrentValue(goalId: Long, currentValue: Int)
}