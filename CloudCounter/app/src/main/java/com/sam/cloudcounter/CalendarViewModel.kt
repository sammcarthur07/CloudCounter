package com.sam.cloudcounter

import android.app.Application
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val appDatabase = AppDatabase.getDatabase(application)
    private val activityLogDao = appDatabase.activityLogDao()
    private val smokerDao = appDatabase.smokerDao()
    private val customActivityManager = CustomActivityManager(application)
    
    private val _calendarData = MutableLiveData<Map<String, List<CalendarFragment.ActivityCount>>>()
    val calendarData: LiveData<Map<String, List<CalendarFragment.ActivityCount>>> = _calendarData
    
    // Observe all activity logs for real-time updates
    val allLogs = activityLogDao.getAllLogs()
    
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    
    // Activity type colors
    private fun getActivityColor(activityType: ActivityType?, customActivityId: String?): Int {
        return when {
            customActivityId != null -> {
                // Get custom activity color
                val customActivities = customActivityManager.getCustomActivities()
                val activity = customActivities.find { customActivity -> customActivity.id == customActivityId }
                activity?.let {
                    try {
                        Color.parseColor(it.color)
                    } catch (e: Exception) {
                        ContextCompat.getColor(getApplication(), R.color.text_secondary)
                    }
                } ?: ContextCompat.getColor(getApplication(), R.color.text_secondary)
            }
            activityType == ActivityType.JOINT -> Color.parseColor("#4CAF50") // Green - matching graph
            activityType == ActivityType.CONE -> Color.parseColor("#FF9800") // Orange - matching graph  
            activityType == ActivityType.BOWL -> Color.parseColor("#2196F3") // Blue - matching graph
            else -> ContextCompat.getColor(getApplication(), R.color.text_secondary)
        }
    }
    
    fun loadActivitiesForPeriod(calendar: Calendar, view: CalendarFragment.CalendarView) {
        viewModelScope.launch {
            val startTime: Long
            val endTime: Long
            
            when (view) {
                CalendarFragment.CalendarView.YEARLY -> {
                    val cal = calendar.clone() as Calendar
                    cal.set(Calendar.MONTH, Calendar.JANUARY)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    startTime = cal.timeInMillis
                    
                    cal.set(Calendar.MONTH, Calendar.DECEMBER)
                    cal.set(Calendar.DAY_OF_MONTH, 31)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    endTime = cal.timeInMillis
                }
                CalendarFragment.CalendarView.MONTHLY -> {
                    val cal = calendar.clone() as Calendar
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    startTime = cal.timeInMillis
                    
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    endTime = cal.timeInMillis
                }
                CalendarFragment.CalendarView.WEEKLY -> {
                    val cal = calendar.clone() as Calendar
                    cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    startTime = cal.timeInMillis
                    
                    cal.add(Calendar.DAY_OF_WEEK, 6)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    endTime = cal.timeInMillis
                }
                CalendarFragment.CalendarView.DAILY -> {
                    val cal = calendar.clone() as Calendar
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    startTime = cal.timeInMillis
                    
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    endTime = cal.timeInMillis
                }
            }
            
            // Load activities from database
            val activities = activityLogDao.getLogsBetweenTimestamps(startTime, endTime)
            
            // Group activities by date
            val groupedData = mutableMapOf<String, MutableList<CalendarFragment.ActivityCount>>()
            activities.forEach { activity ->
                val dateKey = getDateKey(activity.timestamp)
                if (!groupedData.containsKey(dateKey)) {
                    groupedData[dateKey] = mutableListOf()
                }
                
                val activityName = when {
                    activity.customActivityName != null -> activity.customActivityName
                    activity.type == ActivityType.JOINT -> "Joints"
                    activity.type == ActivityType.CONE -> "Cones"
                    activity.type == ActivityType.BOWL -> "Bowls"
                    else -> "Other"
                }
                
                val color = getActivityColor(activity.type, activity.customActivityId)
                
                // Check if this activity type already exists for this date
                val existingActivity = groupedData[dateKey]?.find { it.name == activityName }
                if (existingActivity != null) {
                    // Update count
                    val index = groupedData[dateKey]?.indexOf(existingActivity) ?: -1
                    if (index >= 0) {
                        groupedData[dateKey]?.set(index, existingActivity.copy(
                            count = existingActivity.count + (if (activity.type == ActivityType.BOWL) activity.bowlQuantity else 1)
                        ))
                    }
                } else {
                    // Add new activity
                    groupedData[dateKey]?.add(CalendarFragment.ActivityCount(
                        name = activityName,
                        count = if (activity.type == ActivityType.BOWL) activity.bowlQuantity else 1,
                        color = color
                    ))
                }
            }
            
            _calendarData.value = groupedData
        }
    }
    
    fun getActivitiesForDay(calendar: Calendar): List<CalendarFragment.ActivityCount> {
        val dateKey = getDateKey(calendar.timeInMillis)
        return _calendarData.value?.get(dateKey) ?: emptyList()
    }
    
    fun getActivitiesForMonth(calendar: Calendar): List<CalendarFragment.ActivityCount> {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        
        val monthActivities = mutableMapOf<String, Int>()
        val activityColors = mutableMapOf<String, Int>()
        
        _calendarData.value?.forEach { (dateKey, activities) ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = dateKey.toLongOrNull() ?: 0
            
            if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
                activities.forEach { activity ->
                    monthActivities[activity.name] = (monthActivities[activity.name] ?: 0) + activity.count
                    activityColors[activity.name] = activity.color
                }
            }
        }
        
        return monthActivities.map { (name, count) ->
            CalendarFragment.ActivityCount(name, count, activityColors[name] ?: 0)
        }
    }
    
    fun getActivitiesForHour(calendar: Calendar): List<CalendarFragment.ActivityCount> {
        // This returns an empty list for now since we need to load hour-specific data
        // The actual hour filtering happens in getDetailedActivitiesForHour
        return emptyList()
    }
    
    suspend fun getActivitiesForDayAsync(calendar: Calendar): List<CalendarFragment.ActivityCount> {
        val startOfDay = calendar.clone() as Calendar
        startOfDay.set(Calendar.HOUR_OF_DAY, 0)
        startOfDay.set(Calendar.MINUTE, 0)
        startOfDay.set(Calendar.SECOND, 0)
        startOfDay.set(Calendar.MILLISECOND, 0)
        
        val endOfDay = startOfDay.clone() as Calendar
        endOfDay.add(Calendar.DAY_OF_MONTH, 1)
        
        val logs = activityLogDao.getLogsBetweenTimestamps(startOfDay.timeInMillis, endOfDay.timeInMillis)
        
        val activities = mutableMapOf<String, CalendarFragment.ActivityCount>()
        
        logs.forEach { log ->
            val name = when {
                log.customActivityName != null -> log.customActivityName
                log.customActivityId != null -> {
                    val customActivities = customActivityManager.getCustomActivities()
                    customActivities.find { it.id == log.customActivityId }?.name ?: "Unknown"
                }
                else -> log.type.name.lowercase().replaceFirstChar { it.uppercase() }
            }
            
            val color = getActivityColor(log.type, log.customActivityId)
            
            if (activities.containsKey(name)) {
                val existing = activities[name]!!
                activities[name] = CalendarFragment.ActivityCount(name, existing.count + 1, color)
            } else {
                activities[name] = CalendarFragment.ActivityCount(name, 1, color)
            }
        }
        
        return activities.values.toList()
    }
    
    suspend fun getActivitiesForMonthAsync(calendar: Calendar): List<CalendarFragment.ActivityCount> {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        
        val startOfMonth = Calendar.getInstance()
        startOfMonth.set(year, month, 1, 0, 0, 0)
        startOfMonth.set(Calendar.MILLISECOND, 0)
        
        val endOfMonth = startOfMonth.clone() as Calendar
        endOfMonth.add(Calendar.MONTH, 1)
        
        val logs = activityLogDao.getLogsBetweenTimestamps(startOfMonth.timeInMillis, endOfMonth.timeInMillis)
        
        val activities = mutableMapOf<String, CalendarFragment.ActivityCount>()
        
        logs.forEach { log ->
            val name = when {
                log.customActivityName != null -> log.customActivityName
                log.customActivityId != null -> {
                    val customActivities = customActivityManager.getCustomActivities()
                    customActivities.find { it.id == log.customActivityId }?.name ?: "Unknown"
                }
                else -> log.type.name.lowercase().replaceFirstChar { it.uppercase() }
            }
            
            val color = getActivityColor(log.type, log.customActivityId)
            
            if (activities.containsKey(name)) {
                val existing = activities[name]!!
                activities[name] = CalendarFragment.ActivityCount(name, existing.count + 1, color)
            } else {
                activities[name] = CalendarFragment.ActivityCount(name, 1, color)
            }
        }
        
        return activities.values.toList()
    }
    
    suspend fun getDetailedActivitiesForHour(calendar: Calendar): List<CalendarFragment.ActivityCount> {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val startOfHour = calendar.clone() as Calendar
        startOfHour.set(Calendar.HOUR_OF_DAY, hour)
        startOfHour.set(Calendar.MINUTE, 0)
        startOfHour.set(Calendar.SECOND, 0)
        startOfHour.set(Calendar.MILLISECOND, 0)
        
        val endOfHour = startOfHour.clone() as Calendar
        endOfHour.add(Calendar.HOUR_OF_DAY, 1)
        
        val activities = activityLogDao.getLogsBetweenTimestamps(startOfHour.timeInMillis, endOfHour.timeInMillis)
        
        // Group by activity type/name
        val groupedActivities = mutableMapOf<String, MutableList<ActivityLog>>()
        activities.forEach { activity ->
            val key = when {
                activity.customActivityName != null -> activity.customActivityName
                activity.type == ActivityType.JOINT -> "Joints"
                activity.type == ActivityType.CONE -> "Cones"
                activity.type == ActivityType.BOWL -> "Bowls"
                else -> "Other"
            }
            
            if (!groupedActivities.containsKey(key)) {
                groupedActivities[key] = mutableListOf()
            }
            groupedActivities[key]?.add(activity)
        }
        
        // Convert to ActivityCount
        return groupedActivities.map { (name, logs) ->
            val count = logs.sumOf { if (it.type == ActivityType.BOWL) it.bowlQuantity else 1 }
            val color = getActivityColor(logs.firstOrNull()?.type, logs.firstOrNull()?.customActivityId)
            CalendarFragment.ActivityCount(name, count, color)
        }
    }
    
    suspend fun getDetailedActivitiesForDay(calendar: Calendar): List<CalendarFragment.DetailedActivity> {
        val startOfDay = calendar.clone() as Calendar
        startOfDay.set(Calendar.HOUR_OF_DAY, 0)
        startOfDay.set(Calendar.MINUTE, 0)
        startOfDay.set(Calendar.SECOND, 0)
        startOfDay.set(Calendar.MILLISECOND, 0)
        
        val endOfDay = calendar.clone() as Calendar
        endOfDay.set(Calendar.HOUR_OF_DAY, 23)
        endOfDay.set(Calendar.MINUTE, 59)
        endOfDay.set(Calendar.SECOND, 59)
        endOfDay.set(Calendar.MILLISECOND, 999)
        
        val activities = activityLogDao.getLogsBetweenTimestamps(startOfDay.timeInMillis, endOfDay.timeInMillis)
        
        // Group by activity type/name
        val groupedActivities = mutableMapOf<String, MutableList<ActivityLog>>()
        activities.forEach { activity ->
            val key = when {
                activity.customActivityName != null -> activity.customActivityName
                activity.type == ActivityType.JOINT -> "Joints"
                activity.type == ActivityType.CONE -> "Cones"
                activity.type == ActivityType.BOWL -> "Bowls"
                else -> "Other"
            }
            
            if (!groupedActivities.containsKey(key)) {
                groupedActivities[key] = mutableListOf()
            }
            groupedActivities[key]?.add(activity)
        }
        
        // Prepare smoker lookup once
        val smokersById = smokerDao.getAllSmokersList().associateBy { it.smokerId }

        // Convert to DetailedActivity
        return groupedActivities.map { (name, logs) ->
            val times = logs.map {
                val who = smokersById[it.effectiveConsumerId]?.name ?: "Unknown"
                "${timeFormat.format(Date(it.timestamp))} — $who"
            }
            val gaps = mutableListOf<Double>()
            
            // Calculate gaps between activities
            for (i in 1 until logs.size) {
                val gap = logs[i].timestamp - logs[i - 1].timestamp
                gaps.add(gap.toDouble())
            }
            
            val count = logs.sumOf { if (it.type == ActivityType.BOWL) it.bowlQuantity else 1 }
            val color = getActivityColor(logs.firstOrNull()?.type, logs.firstOrNull()?.customActivityId)
            
            CalendarFragment.DetailedActivity(
                name = name,
                count = count,
                color = color,
                times = times,
                gaps = gaps
            )
        }
    }

    // Detailed activities for a specific HOUR (times + gaps), scoped to that hour
    suspend fun getDetailedActivitiesForHourDetailed(calendar: Calendar): List<CalendarFragment.DetailedActivity> {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val startOfHour = calendar.clone() as Calendar
        startOfHour.set(Calendar.HOUR_OF_DAY, hour)
        startOfHour.set(Calendar.MINUTE, 0)
        startOfHour.set(Calendar.SECOND, 0)
        startOfHour.set(Calendar.MILLISECOND, 0)

        val endOfHour = startOfHour.clone() as Calendar
        endOfHour.add(Calendar.HOUR_OF_DAY, 1)

        val activities = activityLogDao.getLogsBetweenTimestamps(startOfHour.timeInMillis, endOfHour.timeInMillis)

        val groupedActivities = mutableMapOf<String, MutableList<ActivityLog>>()
        activities.forEach { activity ->
            val key = when {
                activity.customActivityName != null -> activity.customActivityName
                activity.type == ActivityType.JOINT -> "Joints"
                activity.type == ActivityType.CONE -> "Cones"
                activity.type == ActivityType.BOWL -> "Bowls"
                else -> "Other"
            }

            if (!groupedActivities.containsKey(key)) {
                groupedActivities[key] = mutableListOf()
            }
            groupedActivities[key]?.add(activity)
        }

        // Prepare smoker lookup once
        val smokersById = smokerDao.getAllSmokersList().associateBy { it.smokerId }

        return groupedActivities.map { (name, logs) ->
            val times = logs.map {
                val who = smokersById[it.effectiveConsumerId]?.name ?: "Unknown"
                "${timeFormat.format(Date(it.timestamp))} — $who"
            }
            val gaps = mutableListOf<Double>()
            for (i in 1 until logs.size) {
                val gap = logs[i].timestamp - logs[i - 1].timestamp
                gaps.add(gap.toDouble())
            }

            val count = logs.sumOf { if (it.type == ActivityType.BOWL) it.bowlQuantity else 1 }
            val color = getActivityColor(logs.firstOrNull()?.type, logs.firstOrNull()?.customActivityId)

            CalendarFragment.DetailedActivity(
                name = name,
                count = count,
                color = color,
                times = times,
                gaps = gaps
            )
        }
    }

    // Detailed activities for a specific MONTH (times + gaps), scoped to that month
    suspend fun getDetailedActivitiesForMonthDetailed(calendar: Calendar): List<CalendarFragment.DetailedActivity> {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)

        val startOfMonth = Calendar.getInstance()
        startOfMonth.set(year, month, 1, 0, 0, 0)
        startOfMonth.set(Calendar.MILLISECOND, 0)

        val endOfMonth = startOfMonth.clone() as Calendar
        endOfMonth.add(Calendar.MONTH, 1)

        val activities = activityLogDao.getLogsBetweenTimestamps(startOfMonth.timeInMillis, endOfMonth.timeInMillis)

        val groupedActivities = mutableMapOf<String, MutableList<ActivityLog>>()
        activities.forEach { activity ->
            val key = when {
                activity.customActivityName != null -> activity.customActivityName
                activity.type == ActivityType.JOINT -> "Joints"
                activity.type == ActivityType.CONE -> "Cones"
                activity.type == ActivityType.BOWL -> "Bowls"
                else -> "Other"
            }

            if (!groupedActivities.containsKey(key)) {
                groupedActivities[key] = mutableListOf()
            }
            groupedActivities[key]?.add(activity)
        }

        // Prepare smoker lookup once
        val smokersById = smokerDao.getAllSmokersList().associateBy { it.smokerId }

        return groupedActivities.map { (name, logs) ->
            val times = logs.map {
                val who = smokersById[it.effectiveConsumerId]?.name ?: "Unknown"
                "${timeFormat.format(Date(it.timestamp))} — $who"
            }
            val gaps = mutableListOf<Double>()
            for (i in 1 until logs.size) {
                val gap = logs[i].timestamp - logs[i - 1].timestamp
                gaps.add(gap.toDouble())
            }

            val count = logs.sumOf { if (it.type == ActivityType.BOWL) it.bowlQuantity else 1 }
            val color = getActivityColor(logs.firstOrNull()?.type, logs.firstOrNull()?.customActivityId)

            CalendarFragment.DetailedActivity(
                name = name,
                count = count,
                color = color,
                times = times,
                gaps = gaps
            )
        }
    }
    
    private fun getDateKey(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis.toString()
    }
}
