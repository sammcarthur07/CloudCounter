package com.sam.cloudcounter

import androidx.room.TypeConverter

class GoalConverters {
    @TypeConverter
    fun fromGoalType(goalType: GoalType): String {
        return goalType.name
    }

    @TypeConverter
    fun toGoalType(goalType: String): GoalType {
        return GoalType.valueOf(goalType)
    }

    @TypeConverter
    fun fromTimeBasedType(type: TimeBasedType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toTimeBasedType(type: String?): TimeBasedType? {
        return type?.let { TimeBasedType.valueOf(it) }
    }

    @TypeConverter
    fun fromTimeUnit(unit: TimeUnit?): String? {
        return unit?.name
    }

    @TypeConverter
    fun toTimeUnit(unit: String?): TimeUnit? {
        return unit?.let { TimeUnit.valueOf(it) }
    }
}