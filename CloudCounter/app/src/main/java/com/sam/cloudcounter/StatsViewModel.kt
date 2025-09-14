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
    private val seshPreferences = application.getSharedPreferences("sesh", android.content.Context.MODE_PRIVATE)

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

    // Custom session selection mode for Stats tab
    private val _useCustomSessions = MutableLiveData(false)
    val useCustomSessions: LiveData<Boolean> = _useCustomSessions

    // List of custom session windows to include (start..end inclusive)
    private val _customSessionRanges = MutableLiveData<List<Pair<Long, Long>>>(emptyList())
    val customSessionRanges: LiveData<List<Pair<Long, Long>>> = _customSessionRanges

    fun setUseCustomSessions(enabled: Boolean) {
        _useCustomSessions.value = enabled
    }

    fun setCustomSessions(ranges: List<Pair<Long, Long>>) {
        _customSessionRanges.value = ranges
    }

    init {
        _calculatedStats.addSource(smokerLogs) { recalc() }
        _calculatedStats.addSource(_selectedTimePeriod) { recalc() }
        _calculatedStats.addSource(_selectedActivityType) { recalc() }
        _calculatedStats.addSource(_selectedCustomActivityId) { recalc() }
        _calculatedStats.addSource(_useCustomSessions) { recalc() }
        _calculatedStats.addSource(_customSessionRanges) { recalc() }
    }

    private fun recalc() {
        val logs = smokerLogs.value.orEmpty()
        val period = _selectedTimePeriod.value ?: TimePeriod.ALL_TIME
        val typeFilter = _selectedActivityType.value
        val customActivityId = _selectedCustomActivityId.value
        val useCustom = _useCustomSessions.value == true
        val rawRanges = _customSessionRanges.value.orEmpty()
        // If current session is selected, keep its end as 'now' while active so stats live-update
        val ranges = if (useCustom && rawRanges.isNotEmpty()) {
            val isActive = seshPreferences.getBoolean("sessionActive", false)
            val sessionStartPref = seshPreferences.getLong("sessionStart", 0L)
            if (isActive && sessionStartPref > 0L && rawRanges.any { it.first == sessionStartPref }) {
                rawRanges.map { r -> if (r.first == sessionStartPref) Pair(r.first, System.currentTimeMillis()) else r }
            } else rawRanges
        } else rawRanges

        val byType = if (customActivityId != null) {
            logs.filter { it.customActivityId == customActivityId }
        } else if (typeFilter == ActivityType.CUSTOM) {
            logs.filter { !it.customActivityId.isNullOrEmpty() }
        } else {
            typeFilter?.let { tf -> logs.filter { it.type == tf } } ?: logs
        }

        val byTime = if (useCustom && ranges.isNotEmpty()) {
            // Keep only logs inside any selected session window
            byType.filter { log -> ranges.any { r -> log.timestamp in r.first..r.second } }
        } else {
            val (start, end) = timeRange(period)
            if (start != null && end != null) byType.filter { it.timestamp in start..end } else byType
        }

        if (byTime.isEmpty()) {
            _calculatedStats.value = CalculatedStats()
            return
        }

        val total = byTime.size

        val avg = if (useCustom && ranges.isNotEmpty()) {
            // Custom mode: Always compute per-day average from total selected duration only
            calculateAveragePerDayForCustom(byTime, ranges)
        } else {
            val (start, end) = timeRange(period)
            calculateAverage(byTime, period, start, end)
        }

        val freq = if (useCustom && ranges.isNotEmpty()) {
            // Custom mode: only within-session intervals
            calculateFrequencyWithinRanges(byTime, ranges)
        } else {
            calculateFrequency(byTime)
        }

        _calculatedStats.value = CalculatedStats(
            totalCount = total,
            averagePerTimeUnit = avg,
            frequencyMillis = freq
        )
    }

    private fun calculateAveragePerDayForCustom(
        logs: List<ActivityLog>,
        ranges: List<Pair<Long, Long>>
    ): Double {
        if (logs.isEmpty() || ranges.isEmpty()) return 0.0
        val totalDurationMs = ranges.fold(0L) { acc, r -> acc + (r.second - r.first).coerceAtLeast(0) }
        if (totalDurationMs <= 0) return logs.size.toDouble()
        val days = TimeUnit.MILLISECONDS.toMinutes(totalDurationMs).toDouble() / (60.0 * 24.0)
        if (days <= 0.0) return logs.size.toDouble()
        return logs.size / days
    }

    private fun calculateFrequencyWithinRanges(
        logs: List<ActivityLog>,
        ranges: List<Pair<Long, Long>>
    ): Long? {
        if (logs.size < 2) return null
        val sorted = logs.sortedBy { it.timestamp }
        val intervals = mutableListOf<Long>()
        // For each contiguous segment within the same selected range, compute diffs
        for (range in ranges) {
            val seg = sorted.filter { it.timestamp in range.first..range.second }
            if (seg.size >= 2) {
                for (i in 0 until seg.size - 1) {
                    intervals.add(seg[i + 1].timestamp - seg[i].timestamp)
                }
            }
        }
        if (intervals.isEmpty()) return null
        val total = intervals.fold(0L) { acc, v -> acc + v }
        return total / intervals.size
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
