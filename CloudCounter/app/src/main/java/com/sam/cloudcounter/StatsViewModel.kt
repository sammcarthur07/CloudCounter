// app/src/main/java/com/sam/cloudcounter/StatsViewModel.kt
package com.sam.cloudcounter

import android.app.Application
import androidx.lifecycle.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class CalculatedStats(
    val totalCount: Int = 0,
    val averagePerTimeUnit: Double = 0.0,
    val frequencyMillis: Long? = null
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as CloudCounterApplication).repository

    // Selected smoker IDs (empty = all)
    private val _selectedSmokerIds = MutableLiveData<Set<Long>>(emptySet())
    val selectedSmokerIds: LiveData<Set<Long>> = _selectedSmokerIds

    fun setSmoker(smokerId: Long?) {
        _selectedSmokerIds.value = smokerId?.let { setOf(it) } ?: emptySet()
    }

    fun resetStatsToZero() {
        // This posts a new, empty CalculatedStats object, which has all default zero values.
        _calculatedStats.value = CalculatedStats()
    }

    fun setSmokers(smokerIds: Set<Long>?) {
        _selectedSmokerIds.value = smokerIds ?: emptySet()
    }

    fun addSmoker(smokerId: Long) {
        val current = _selectedSmokerIds.value.orEmpty()
        _selectedSmokerIds.value = current + smokerId
    }

    fun removeSmoker(smokerId: Long) {
        val current = _selectedSmokerIds.value.orEmpty()
        _selectedSmokerIds.value = current - smokerId
    }

    fun clearSmokers() {
        _selectedSmokerIds.value = emptySet()
    }

    private val smokerLogs: LiveData<List<ActivityLog>> =
        _selectedSmokerIds.switchMap { ids ->
            if (ids.isEmpty()) repository.allLogs
            else repository.getLogsForSmokersLive(ids.toList())
        }

    private val _selectedTimePeriod = MutableLiveData(TimePeriod.ALL_TIME)
    val selectedTimePeriod: LiveData<TimePeriod> = _selectedTimePeriod
    fun setTimePeriod(period: TimePeriod) {
        _selectedTimePeriod.value = period
    }

    private val _selectedActivityType = MutableLiveData<ActivityType?>(null)
    val selectedActivityType: LiveData<ActivityType?> = _selectedActivityType
    fun setActivityType(type: ActivityType?) {
        _selectedActivityType.value = type
    }
    
    private val _selectedCustomActivityId = MutableLiveData<String?>(null)
    val selectedCustomActivityId: LiveData<String?> = _selectedCustomActivityId
    fun setCustomActivityId(id: String?) {
        _selectedCustomActivityId.value = id
    }

    private val _calculatedStats = MediatorLiveData<CalculatedStats>()
    val calculatedStats: LiveData<CalculatedStats> = _calculatedStats

    init {
        _calculatedStats.addSource(smokerLogs) { recalc() }
        _calculatedStats.addSource(_selectedTimePeriod) { recalc() }
        _calculatedStats.addSource(_selectedActivityType) { recalc() }
        _calculatedStats.addSource(_selectedCustomActivityId) { recalc() }
    }

    private fun recalc() {
        val logs = smokerLogs.value.orEmpty()
        val period = _selectedTimePeriod.value ?: TimePeriod.ALL_TIME
        val typeFilter = _selectedActivityType.value
        val customActivityId = _selectedCustomActivityId.value

        val byType = if (customActivityId != null) {
            // Filter by custom activity ID
            logs.filter { it.customActivityId == customActivityId }
        } else if (typeFilter == ActivityType.CUSTOM) {
            // Filter for all custom activities (when CUSTOM type is selected but no specific ID)
            logs.filter { !it.customActivityId.isNullOrEmpty() }
        } else {
            // Filter by regular activity type
            typeFilter?.let { tf -> logs.filter { it.type == tf } } ?: logs
        }

        val (start, end) = timeRange(period)
        val byTime = if (start != null && end != null) {
            byType.filter { it.timestamp in start..end }
        } else byType

        if (byTime.isEmpty()) {
            _calculatedStats.value = CalculatedStats()
            return
        }

        val total = byTime.size
        val avg = calculateAverage(byTime, period, start, end)
        val freq = calculateFrequency(byTime)

        _calculatedStats.value = CalculatedStats(
            totalCount = total,
            averagePerTimeUnit = avg,
            frequencyMillis = freq
        )
    }

    private fun timeRange(period: TimePeriod): Pair<Long?, Long?> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        fun Calendar.clearTime() {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (period) {
            TimePeriod.MINUTELY -> run {
                cal.timeInMillis = now
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.MINUTE, 1)
                cal.add(Calendar.MILLISECOND, -1)
                val end = cal.timeInMillis
                Pair(start, end)
            }

            TimePeriod.HOURLY -> run {
                cal.timeInMillis = now
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.HOUR_OF_DAY, 1)
                cal.add(Calendar.MILLISECOND, -1)
                val end = cal.timeInMillis
                Pair(start, end)
            }

            TimePeriod.DAILY -> run {
                cal.timeInMillis = now
                cal.clearTime()
                Pair(cal.timeInMillis, now)
            }

            TimePeriod.WEEKLY -> run {
                cal.timeInMillis = now
                cal.clearTime()
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                Pair(cal.timeInMillis, now)
            }

            TimePeriod.FORTNIGHTLY -> run {
                cal.timeInMillis = now
                cal.clearTime()
                cal.add(Calendar.DAY_OF_YEAR, -13)
                Pair(cal.timeInMillis, now)
            }

            TimePeriod.MONTHLY -> run {
                cal.timeInMillis = now
                cal.clearTime()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                Pair(cal.timeInMillis, now)
            }

            TimePeriod.YEARLY -> run {
                cal.timeInMillis = now
                cal.clearTime()
                cal.set(Calendar.DAY_OF_YEAR, 1)
                Pair(cal.timeInMillis, now)
            }

            TimePeriod.ALL_TIME -> Pair(null, null)
        }
    }

    private fun calculateAverage(
        logs: List<ActivityLog>,
        period: TimePeriod,
        startTime: Long?,
        endTime: Long?
    ): Double {
        if (logs.isEmpty()) return 0.0

        if (period == TimePeriod.ALL_TIME) {
            val first = logs.minOf { it.timestamp }
            val last = logs.maxOf { it.timestamp }
            val days = TimeUnit.MILLISECONDS
                .toDays(last - first)
                .toDouble()
                .coerceAtLeast(1.0)
            return logs.size / days
        }

        val actualStart = startTime ?: logs.minOf { it.timestamp }
        val durationMs = System.currentTimeMillis() - actualStart
        if (durationMs <= 0) return logs.size.toDouble()

        val units = when (period) {
            TimePeriod.MINUTELY ->
                TimeUnit.MILLISECONDS.toMinutes(durationMs).toDouble().coerceAtLeast(1.0)

            TimePeriod.HOURLY ->
                TimeUnit.MILLISECONDS.toHours(durationMs).toDouble().coerceAtLeast(1.0)

            TimePeriod.DAILY ->
                TimeUnit.MILLISECONDS.toDays(durationMs).toDouble().coerceAtLeast(1.0)

            TimePeriod.WEEKLY ->
                (TimeUnit.MILLISECONDS.toDays(durationMs) / 7.0).coerceAtLeast(1.0)

            TimePeriod.FORTNIGHTLY ->
                (TimeUnit.MILLISECONDS.toDays(durationMs) / 14.0).coerceAtLeast(1.0)

            TimePeriod.MONTHLY -> run {
                val mcal = Calendar.getInstance().apply { timeInMillis = actualStart }
                val daysInMonth = mcal.getActualMaximum(Calendar.DAY_OF_MONTH).toDouble()
                (TimeUnit.MILLISECONDS.toDays(durationMs) / daysInMonth).coerceAtLeast(1.0)
            }

            TimePeriod.YEARLY -> run {
                val ycal = Calendar.getInstance().apply { timeInMillis = actualStart }
                val daysInYear =
                    if (ycal.getActualMaximum(Calendar.DAY_OF_YEAR) > 365) 366.0 else 365.0
                (TimeUnit.MILLISECONDS.toDays(durationMs) / daysInYear).coerceAtLeast(1.0)
            }

            else -> 1.0
        }

        return logs.size / units
    }


    private fun calculateFrequency(logs: List<ActivityLog>): Long? {
        if (logs.size < 2) return null
        val sorted = logs.sortedBy { it.timestamp }
        var totalDiff = 0L
        for (i in 0 until sorted.size - 1) {
            totalDiff += (sorted[i + 1].timestamp - sorted[i].timestamp)
        }
        return totalDiff / (sorted.size - 1)
    }
}
