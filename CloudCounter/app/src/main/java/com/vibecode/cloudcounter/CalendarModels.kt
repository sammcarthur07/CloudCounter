package com.vibecode.cloudcounter

import java.util.Calendar

// All your calendar-related data classes live here now.
data class DayData(val calendar: Calendar?, val activities: List<ActivityCount>)
data class MonthData(val calendar: Calendar, val activities: List<ActivityCount>)
data class HourData(val calendar: Calendar, val activities: List<ActivityCount>)

data class ActivityCount(
    val name: String,
    val count: Int,
    val color: Int
)

data class DetailedActivity(
    val name: String,
    val count: Int,
    val color: Int,
    val times: List<String>,
    val gaps: List<Double>
)