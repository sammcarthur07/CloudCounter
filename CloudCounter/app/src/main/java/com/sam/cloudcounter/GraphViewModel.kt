package com.sam.cloudcounter

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class EffectiveDisplayMode {
    MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY
}

class GraphViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences = application.getSharedPreferences("graph_prefs", Context.MODE_PRIVATE)
    private val repository: ActivityRepository

    // This holds the final data for the UI
    private val _chartUiData = MediatorLiveData<ChartUiData>()
    val chartUiData: LiveData<ChartUiData> = _chartUiData

    // Chart type with persistence
    private val _chartType = MutableLiveData<ChartType>().apply {
        // Load saved chart type or default to BAR
        val savedType = sharedPreferences.getString("chart_type", ChartType.BAR.name)
        value = ChartType.valueOf(savedType ?: ChartType.BAR.name)
    }
    val chartType: LiveData<ChartType> = _chartType

    enum class ChartType {
        LINE, BAR
    }

    private val _selectedTimePeriod = MutableLiveData(TimePeriod.ALL_TIME)
    val selectedTimePeriod: LiveData<TimePeriod> = _selectedTimePeriod

    private val _selectedSmokerIds = MutableLiveData<Set<Long>?>(null)
    val selectedSmokerIds: LiveData<Set<Long>?> = _selectedSmokerIds

    private val _showJoints = MutableLiveData(true)
    val showJoints: LiveData<Boolean> = _showJoints

    private val _showCones = MutableLiveData(true)
    val showCones: LiveData<Boolean> = _showCones

    private val _showBowls = MutableLiveData(true)
    val showBowls: LiveData<Boolean> = _showBowls
    
    // Track visibility of custom activities
    private val _showCustomActivities = mutableMapOf<String, MutableLiveData<Boolean>>()
    val showCustomActivities: Map<String, LiveData<Boolean>> = _showCustomActivities

    val colorJoints: Int = Color.parseColor("#4CAF50")
    val colorCones: Int = Color.parseColor("#FF9800")
    val colorBowls: Int = Color.parseColor("#2196F3")
    
    // Neon colors for custom activities
    val customActivityColors = listOf(
        Color.parseColor("#FF91A4"), // neon_candy (pink)
        Color.parseColor("#BF7EFF"), // neon_purple
        Color.parseColor("#FFFF66"), // neon_yellow
        Color.parseColor("#5591a4"), // sort_smokers_blue
        Color.parseColor("#FFA366"), // neon_orange
        Color.parseColor("#98FB98")  // neon_green
    )

    // This is the raw data stream from the database
    private val allActivities: LiveData<List<ActivityLog>>

    init {
        val database = AppDatabase.getDatabase(application)
        val activityLogDao = database.activityLogDao()
        val smokerDao = database.smokerDao()
        val summaryDao = database.sessionSummaryDao()
        val stashDao = database.stashDao()
        repository = ActivityRepository(activityLogDao, smokerDao, summaryDao, stashDao)

        // Initialize the live data stream
        allActivities = repository.allActivities

        // Add all sources that should trigger a chart refresh
        _chartUiData.addSource(_selectedTimePeriod) { refreshChartData() }
        _chartUiData.addSource(_selectedSmokerIds) { refreshChartData() }
        _chartUiData.addSource(_showJoints) { refreshChartData() }
        _chartUiData.addSource(_showCones) { refreshChartData() }
        _chartUiData.addSource(_showBowls) { refreshChartData() }
        _chartUiData.addSource(_chartType) { refreshChartData() }

        // Add the new LiveData from the repository as a source.
        // Now, any change in the database will trigger refreshChartData().
        _chartUiData.addSource(allActivities) { refreshChartData() }
    }

    fun setChartType(type: ChartType) {
        Log.d("GraphViewModel", "Setting chart type to: $type")
        _chartType.value = type
        // Save to SharedPreferences
        sharedPreferences.edit().putString("chart_type", type.name).apply()
        // Trigger data refresh with the new chart type
        _chartUiData.value?.let {
            _chartUiData.value = it
        }
    }

    fun setGraphTimePeriod(period: TimePeriod) {
        if (_selectedTimePeriod.value != period) _selectedTimePeriod.value = period
    }

    fun setShowJoints(show: Boolean) {
        if (_showJoints.value != show) _showJoints.value = show
    }

    fun setShowCones(show: Boolean) {
        if (_showCones.value != show) _showCones.value = show
    }

    fun setShowBowls(show: Boolean) {
        if (_showBowls.value != show) _showBowls.value = show
    }
    
    fun setShowCustomActivity(customId: String, show: Boolean) {
        if (!_showCustomActivities.containsKey(customId)) {
            val liveData = MutableLiveData(true) // Default to visible
            _showCustomActivities[customId] = liveData
            _chartUiData.addSource(liveData) { refreshChartData() }
        }
        _showCustomActivities[customId]?.value = show
    }
    
    fun getShowCustomActivity(customId: String): Boolean {
        return _showCustomActivities[customId]?.value ?: true // Default to visible
    }

    fun setSmokers(smokerIds: Set<Long>?) {
        if (_selectedSmokerIds.value != smokerIds) _selectedSmokerIds.value = smokerIds
    }

    fun clearSmokers() {
        if (_selectedSmokerIds.value != null) _selectedSmokerIds.value = null
    }

    private fun refreshChartData() {
        viewModelScope.launch {
            // Get the latest values from all our sources
            val currentActivities = allActivities.value ?: emptyList()
            val period = _selectedTimePeriod.value ?: TimePeriod.ALL_TIME
            val smokers = _selectedSmokerIds.value

            // Generate the chart data using the latest information
            val data = generateChartData(currentActivities, period, smokers)
            _chartUiData.postValue(data)
        }
    }

    // This function is now synchronous and takes the activity list as a parameter
    private fun generateChartData(allLogs: List<ActivityLog>, period: TimePeriod, smokers: Set<Long>?): ChartUiData {
        val (start, end) = getTimeRangeForPeriod(period)

        // Filter the full list of activities based on the current filters
        val logs = allLogs.filter { log ->
            val timeMatch = (start == null || log.timestamp >= start) && (end == null || log.timestamp <= end)
            val smokerMatch = smokers.isNullOrEmpty() || smokers.contains(log.smokerId)
            timeMatch && smokerMatch
        }

        if (logs.isEmpty()) {
            return ChartUiData(
                LineData(), "No activity in this period.", period,
                _chartType.value ?: ChartType.LINE, Pair(start, end), null
            )
        }

        val firstLogTime = logs.minOf { it.timestamp }
        val lastLogTime = logs.maxOf { it.timestamp }
        val effectiveMode = determineEffectiveDisplayMode(period, lastLogTime - firstLogTime)

        val dataSets = mutableListOf<ILineDataSet>()
        if (_showJoints.value == true) processLogsForChart(logs, ActivityType.JOINT, effectiveMode).takeIf { it.isNotEmpty() }?.let { dataSets.add(LineDataSet(it, "Joints").apply { styleDataSet(this, colorJoints) }) }
        if (_showCones.value == true) processLogsForChart(logs, ActivityType.CONE, effectiveMode).takeIf { it.isNotEmpty() }?.let { dataSets.add(LineDataSet(it, "Cones").apply { styleDataSet(this, colorCones) }) }
        if (_showBowls.value == true) processLogsForChart(logs, ActivityType.BOWL, effectiveMode).takeIf { it.isNotEmpty() }?.let { dataSets.add(LineDataSet(it, "Bowls").apply { styleDataSet(this, colorBowls) }) }
        
        // Process custom activities
        val customActivities = logs.filter { !it.customActivityId.isNullOrEmpty() }
            .groupBy { it.customActivityId }
        
        var colorIndex = 0
        customActivities.forEach { (customId, customLogs) ->
            if (customId != null && getShowCustomActivity(customId)) {
                val activityName = customLogs.firstOrNull()?.customActivityName ?: "Custom"
                processCustomLogsForChart(customLogs, effectiveMode).takeIf { it.isNotEmpty() }?.let {
                    val color = customActivityColors.getOrElse(colorIndex % customActivityColors.size) { customActivityColors[0] }
                    dataSets.add(LineDataSet(it, activityName).apply { styleDataSet(this, color) })
                    colorIndex++
                }
            }
        }

        val descriptionSuffix = period.name.replace("_", " ").lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
        val displayModeText = if (effectiveMode.name != period.name) " (as ${effectiveMode.name.lowercase(Locale.getDefault())})" else ""
        val finalDescription = if (dataSets.isEmpty()) "No data for $descriptionSuffix" else "$descriptionSuffix$displayModeText"
        return ChartUiData(LineData(dataSets), finalDescription, period, _chartType.value ?: ChartType.LINE, Pair(firstLogTime, lastLogTime), effectiveMode)
    }

    private fun determineEffectiveDisplayMode(period: TimePeriod, dataSpanMillis: Long): EffectiveDisplayMode {
        val dataSpanMinutes = TimeUnit.MILLISECONDS.toMinutes(dataSpanMillis)
        val dataSpanHours = TimeUnit.MILLISECONDS.toHours(dataSpanMillis)
        val dataSpanDays = TimeUnit.MILLISECONDS.toDays(dataSpanMillis)

        if (period == TimePeriod.MINUTELY) {
            return EffectiveDisplayMode.MINUTELY
        }

        return when {
            dataSpanMinutes < 60 -> EffectiveDisplayMode.MINUTELY
            dataSpanHours < 24 -> EffectiveDisplayMode.HOURLY
            dataSpanDays < 7 -> EffectiveDisplayMode.DAILY
            dataSpanDays < 30 -> EffectiveDisplayMode.WEEKLY
            dataSpanDays < 365 -> EffectiveDisplayMode.MONTHLY
            else -> EffectiveDisplayMode.YEARLY
        }
    }

    private fun processLogsForChart(logs: List<ActivityLog>, type: ActivityType, mode: EffectiveDisplayMode): List<Entry> {
        val filtered = logs.filter { it.type == type && it.customActivityId.isNullOrEmpty() }
        if (filtered.isEmpty()) return emptyList()

        val format = when (mode) {
            EffectiveDisplayMode.MINUTELY -> "yyyyMMddHHmm"
            EffectiveDisplayMode.HOURLY -> "yyyyMMddHH"
            EffectiveDisplayMode.DAILY, EffectiveDisplayMode.WEEKLY -> "yyyyMMdd"
            EffectiveDisplayMode.MONTHLY -> "yyyyMMdd"
            EffectiveDisplayMode.YEARLY -> "yyyyMM"
        }
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        return filtered.groupBy { sdf.format(Date(it.timestamp)) }
            .map { (_, group) -> Entry(group.minOf { it.timestamp }.toFloat(), group.size.toFloat()) }
            .sortedBy { it.x }
    }

    private fun processCustomLogsForChart(logs: List<ActivityLog>, mode: EffectiveDisplayMode): List<Entry> {
        if (logs.isEmpty()) return emptyList()
        
        val format = when (mode) {
            EffectiveDisplayMode.MINUTELY -> "yyyyMMddHHmm"
            EffectiveDisplayMode.HOURLY -> "yyyyMMddHH"
            EffectiveDisplayMode.DAILY, EffectiveDisplayMode.WEEKLY -> "yyyyMMdd"
            EffectiveDisplayMode.MONTHLY -> "yyyyMMdd"
            EffectiveDisplayMode.YEARLY -> "yyyyMM"
        }
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        return logs.groupBy { sdf.format(Date(it.timestamp)) }
            .map { (_, group) -> Entry(group.minOf { it.timestamp }.toFloat(), group.size.toFloat()) }
            .sortedBy { it.x }
    }
    
    private fun styleDataSet(dataSet: LineDataSet, color: Int) {
        dataSet.color = color
        dataSet.setCircleColor(color)
        dataSet.circleRadius = 3f
        dataSet.lineWidth = 2f
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.HORIZONTAL_BEZIER
    }

    private fun getTimeRangeForPeriod(period: TimePeriod): Pair<Long?, Long?> {
        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()
        val start: Long? = when (period) {
            TimePeriod.MINUTELY -> now - TimeUnit.MINUTES.toMillis(60)
            TimePeriod.HOURLY -> now - TimeUnit.HOURS.toMillis(24)
            TimePeriod.DAILY -> cal.apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            TimePeriod.WEEKLY -> now - TimeUnit.DAYS.toMillis(7)
            TimePeriod.FORTNIGHTLY -> now - TimeUnit.DAYS.toMillis(14)
            TimePeriod.MONTHLY -> cal.apply { timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            TimePeriod.YEARLY -> cal.apply { timeInMillis = now; set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            TimePeriod.ALL_TIME -> null
        }
        return Pair(start, now)
    }
}