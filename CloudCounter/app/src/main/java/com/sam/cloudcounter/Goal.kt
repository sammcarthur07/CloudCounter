package com.sam.cloudcounter

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true)
    val goalId: Long = 0,
    val goalType: GoalType = GoalType.ALL_SESSIONS,
    val timeBasedType: TimeBasedType? = null,
    val timeDuration: Int? = null,
    val timeUnit: TimeUnit? = null,
    val targetJoints: Int = 0,
    val targetCones: Int = 0,
    val targetBowls: Int = 0,
    val currentJoints: Int = 0,
    val currentCones: Int = 0,
    val currentBowls: Int = 0,
    val customActivities: String = "{}",  // JSON map of custom activity counts
    val isRecurring: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val lastResetAt: Long = System.currentTimeMillis(),
    val progressNotificationsEnabled: Boolean = true,
    val completionNotificationsEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val sessionShareCode: String? = null,
    val goalName: String = "",
    val lastNotificationPercentage: Int = 0,
    val isPaused: Boolean = false,
    val allowOverflow: Boolean = true,
    val completedRounds: Int = 0,
    val selectedSmokers: String = "ALL",
    val smokerProgress: String = "{}",
    val sessionStartDate: Long? = null,
    val sessionEndDate: Long? = null,
    val wasManuallyPaused: Boolean = false
)

enum class GoalType {
    CURRENT_SESSION,
    ALL_SESSIONS,
    TIME_BASED
}

enum class TimeBasedType {
    ROLLING,
    FIXED_PERIOD
}

enum class TimeUnit {
    MINUTE,
    HOUR,
    DAY,
    WEEK,
    FORTNIGHT,
    MONTH,
    YEAR
}