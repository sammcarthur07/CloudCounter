package com.sam.cloudcounter

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer // FIX: Added the missing import for Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.android.material.chip.Chip
import com.sam.cloudcounter.databinding.FragmentGraphBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GraphFragment : Fragment() {

    private var _binding: FragmentGraphBinding? = null
    private val binding get() = _binding!!

    private lateinit var graphViewModel: GraphViewModel
    private lateinit var lineChart: LineChart
    private lateinit var barChart: BarChart

    private var allSmokersCache: List<Smoker> = emptyList()
    private var smokerSelectionDebounceJob: Job? = null
    private val selectedSmokersInternal = mutableSetOf<Long>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGraphBinding.inflate(inflater, container, false)
        lineChart = binding.lineChart
        barChart = BarChart(requireContext()).apply {
            layoutParams = lineChart.layoutParams
            visibility = View.GONE
        }
        (lineChart.parent as ViewGroup).addView(barChart)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        graphViewModel = ViewModelProvider(requireActivity()).get(GraphViewModel::class.java)

        setupChartAppearance()
        setupBarChartAppearance()
        setupListeners()
        observeViewModel()

        // Set initial chip selections based on ViewModel values
        viewLifecycleOwner.lifecycleScope.launch {
            delay(100) // Small delay for layout
            _binding?.let {
                // Set time period chip
                val initialPeriod = graphViewModel.selectedTimePeriod.value ?: TimePeriod.ALL_TIME
                it.chipGroupGraphTimePeriod.check(getChipIdForTimePeriod(initialPeriod))

                // Set chart type chip based on ViewModel's current value
                val currentChartType = graphViewModel.chartType.value ?: GraphViewModel.ChartType.BAR
                val chipToCheck = if (currentChartType == GraphViewModel.ChartType.BAR) {
                    R.id.chipBar
                } else {
                    R.id.chipLine
                }
                it.chipGroupChartType.check(chipToCheck)

                // Force update the display with the current chart type
                graphViewModel.chartUiData.value?.let { data ->
                    updateChartDisplay(data, currentChartType)
                }
            }
        }
    }

    // FIX: Created this function to group all listener setups.
    private fun setupListeners() {
        setupTimePeriodChipGroupListener()
        setupChartTypeChipGroupListener()
        setupLineToggleCheckBoxListeners()
        setupUserSelection()
        observeCustomActivities()
    }

    // FIX: Created this function to group all ViewModel observers.
    private fun observeViewModel() {
        graphViewModel.chartUiData.observe(viewLifecycleOwner, Observer { chartUiData ->
            chartUiData?.let {
                binding.textChartDescription.text = it.descriptionText
                // Always use the current chart type from ViewModel
                val currentType = graphViewModel.chartType.value ?: GraphViewModel.ChartType.BAR
                updateChartDisplay(it, currentType)
            }
        })

        graphViewModel.chartType.observe(viewLifecycleOwner, Observer { chartType ->
            chartType?.let { type ->
                // Update chip selection to match ViewModel
                val chipToCheck = if (type == GraphViewModel.ChartType.BAR) {
                    R.id.chipBar
                } else {
                    R.id.chipLine
                }
                if (binding.chipGroupChartType.checkedChipId != chipToCheck) {
                    binding.chipGroupChartType.check(chipToCheck)
                }

                // Update chart display
                graphViewModel.chartUiData.value?.let { data ->
                    updateChartDisplay(data, type)
                }
            }
        })

        graphViewModel.selectedTimePeriod.observe(viewLifecycleOwner, Observer { period ->
            period?.let {
                val formatter = TimestampXAxisValueFormatter(it)
                lineChart.xAxis.valueFormatter = formatter
                barChart.xAxis.valueFormatter = formatter
                lineChart.invalidate()
                barChart.invalidate()
            }
        })

        graphViewModel.selectedSmokerIds.observe(viewLifecycleOwner, Observer { ids ->
            updateSelectionSummary(ids)
        })
    }

    private fun updateChartDisplay(chartUiData: ChartUiData, chartType: GraphViewModel.ChartType) {
        Log.d("GraphDebug", "=== updateChartDisplay ===")
        Log.d("GraphDebug", "Updating display for chart type: $chartType")

        if (chartType == GraphViewModel.ChartType.BAR) {
            Log.d("GraphDebug", "Switching to BAR chart")
            showBarChart(chartUiData)
        } else {
            Log.d("GraphDebug", "Switching to LINE chart")
            showLineChart(chartUiData)
        }

        Log.d("GraphDebug", "=== END updateChartDisplay ===")
    }

    private fun setupChartTypeChipGroupListener() {
        Log.d("GraphFragment", "Setting up chart type chip listener")
        binding.chipGroupChartType.setOnCheckedStateChangeListener { group, checkedIds ->
            Log.d("GraphFragment", "Chart type chip state changed, checkedIds: $checkedIds")
            if (checkedIds.isNotEmpty()) {
                val chipId = checkedIds.first()
                val chartType = when (chipId) {
                    R.id.chipLine -> GraphViewModel.ChartType.LINE
                    R.id.chipBar -> GraphViewModel.ChartType.BAR
                    else -> GraphViewModel.ChartType.LINE
                }
                Log.d("GraphFragment", "Chart type chip selected: $chartType")
                graphViewModel.setChartType(chartType)
            }
        }
    }

    private fun setupBarChartAppearance() {
        barChart.setTouchEnabled(true)
        barChart.isDragEnabled = true
        barChart.setScaleEnabled(true)
        barChart.setPinchZoom(true)

        // CHANGE: Set transparent background instead of grey
        barChart.setBackgroundColor(Color.TRANSPARENT)

        barChart.description.isEnabled = false

        // FIX: Disable the built-in legend entirely
        barChart.legend.isEnabled = false

        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = Color.DKGRAY
        xAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_on_dark_background)
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true
        xAxis.labelRotationAngle = -60f
        xAxis.setAvoidFirstLastClipping(true)
        xAxis.textSize = 9f

        val leftAxis = barChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.DKGRAY
        leftAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_on_dark_background)
        leftAxis.axisMinimum = 0f
        leftAxis.granularity = 1f
        leftAxis.isGranularityEnabled = true

        barChart.axisRight.isEnabled = false

        barChart.setNoDataText("Select filters to see data.")
        barChart.setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_dark_background))

        // FIX: Reset top offset and keep bottom padding
        barChart.setExtraOffsets(0f, 0f, 0f, 50f)
    }

    private fun showBarChart(chartUiData: ChartUiData) {
        Log.d("GraphDebug", "=== SHOWING BAR CHART ===")

        lineChart.visibility = View.GONE
        barChart.visibility = View.VISIBLE

        if (chartUiData.lineData != null && chartUiData.lineData.entryCount > 0) {
            Log.d("GraphDebug", "Converting line data to bar data")
            Log.d("GraphDebug", "Line data has ${chartUiData.lineData.dataSetCount} datasets")

            // Convert LineData to BarData
            val barDataSets = mutableListOf<BarDataSet>()

            for (dataSet in chartUiData.lineData.dataSets) {
                val lineDataSet = dataSet as? LineDataSet ?: continue
                val barEntries = mutableListOf<BarEntry>()

                Log.d("GraphDebug", "Converting dataset: ${lineDataSet.label} with ${lineDataSet.entryCount} entries")

                for (i in 0 until lineDataSet.entryCount) {
                    val entry = lineDataSet.getEntryForIndex(i)
                    barEntries.add(BarEntry(entry.x, entry.y))
                }

                val barDataSet = BarDataSet(barEntries, lineDataSet.label)
                barDataSet.color = lineDataSet.color
                barDataSet.valueTextColor = Color.WHITE
                barDataSet.setDrawValues(false)

                barDataSets.add(barDataSet)
            }

            if (barDataSets.isNotEmpty()) {
                val barData = BarData(barDataSets.toList())

                // Bar width calculation based on effective display mode
                val entryCount = barDataSets.maxOfOrNull { it.entryCount } ?: 1
                val effectiveMode = chartUiData.effectiveDisplayMode
                val period = chartUiData.processedPeriod

                val barWidth = when (effectiveMode) {
                    EffectiveDisplayMode.MINUTELY -> 180000f // 3 minutes
                    EffectiveDisplayMode.HOURLY -> 1800000f // 30 minutes
                    EffectiveDisplayMode.DAILY -> 3600000f // 1 hour
                    EffectiveDisplayMode.WEEKLY -> 43200000f // 12 hours
                    EffectiveDisplayMode.MONTHLY -> 86400000f // 1 day
                    EffectiveDisplayMode.YEARLY -> 2592000000f // 30 days
                    else -> {
                        // Fallback to period-based width
                        when (period) {
                            TimePeriod.MINUTELY -> 180000f // 3 minutes
                            TimePeriod.HOURLY -> 1800000f
                            TimePeriod.DAILY -> 3600000f
                            TimePeriod.WEEKLY -> 43200000f
                            TimePeriod.FORTNIGHTLY -> 43200000f
                            TimePeriod.MONTHLY -> 86400000f
                            TimePeriod.YEARLY -> 2592000000f
                            TimePeriod.ALL_TIME -> {
                                val range = barData.xMax - barData.xMin
                                if (range > 0) {
                                    (range / (entryCount * 3)).toFloat()
                                } else {
                                    86400000f
                                }
                            }
                        }
                    }
                }

                barData.barWidth = barWidth

                Log.d("GraphDebug", "Bar data created: ${barDataSets.size} datasets, width: $barWidth, period: $period, effective: $effectiveMode")

                // Dynamic height
                val baseHeightPx = (300 * resources.displayMetrics.density).toInt()
                val extraPerEntryPx = (0.5f * resources.displayMetrics.density).toInt()
                val computedHeight = baseHeightPx + entryCount * extraPerEntryPx
                val maxHeight = (resources.displayMetrics.heightPixels * 0.8).toInt()
                val finalHeight = computedHeight.coerceAtMost(maxHeight)

                barChart.layoutParams = barChart.layoutParams.apply {
                    height = finalHeight
                }
                barChart.requestLayout()

                barChart.xAxis.valueFormatter = TimestampXAxisValueFormatter(chartUiData.processedPeriod)
                configureXAxisLabelCount(barChart.xAxis, barData, chartUiData.processedPeriod, chartUiData.effectiveDisplayMode)

                barChart.data = barData
                barChart.description.text = chartUiData.descriptionText
                barChart.animateY(750)
                barChart.invalidate()

                Log.d("GraphDebug", "Bar chart updated successfully")
            } else {
                Log.d("GraphDebug", "No bar datasets created")
                barChart.clear()
                barChart.description.text = "No data to display"
            }
        } else {
            Log.d("GraphDebug", "No data for bar chart")
            barChart.clear()
            barChart.description.text = chartUiData.descriptionText
            barChart.invalidate()
        }

        Log.d("GraphDebug", "=== END BAR CHART ===")
    }

    private fun showLineChart(chartUiData: ChartUiData) {
        Log.d("GraphFragment", "=== SHOWING LINE CHART ===")

        barChart.visibility = View.GONE
        lineChart.visibility = View.VISIBLE

        if (chartUiData.lineData != null && chartUiData.lineData.entryCount > 0) {
            // Dynamic height adjustment
            val baseHeightPx = (300 * resources.displayMetrics.density).toInt()
            val extraPerEntryPx = (0.5f * resources.displayMetrics.density).toInt()
            val computedHeight = baseHeightPx + chartUiData.lineData.entryCount * extraPerEntryPx
            val maxHeight = (resources.displayMetrics.heightPixels * 0.8).toInt()
            val finalHeight = computedHeight.coerceAtMost(maxHeight)

            lineChart.layoutParams = lineChart.layoutParams.apply {
                height = finalHeight
            }
            lineChart.requestLayout()

            customizeDataSetsInFragment(chartUiData.lineData)

            lineChart.xAxis.valueFormatter = TimestampXAxisValueFormatter(chartUiData.processedPeriod)
            configureXAxisLabelCount(lineChart.xAxis, chartUiData.lineData, chartUiData.processedPeriod, chartUiData.effectiveDisplayMode)

            lineChart.data = chartUiData.lineData
            lineChart.description.text = chartUiData.descriptionText
            lineChart.animateX(750)
            lineChart.invalidate()
        } else {
            lineChart.clear()
            lineChart.description.text = chartUiData.descriptionText
            lineChart.xAxis.valueFormatter = TimestampXAxisValueFormatter(chartUiData.processedPeriod)
            lineChart.invalidate()
        }

        Log.d("GraphFragment", "=== LINE CHART COMPLETE ===")
    }

    fun refreshGraph() {
        // Force the graph to refresh its data
        graphViewModel.setGraphTimePeriod(graphViewModel.selectedTimePeriod.value ?: TimePeriod.ALL_TIME)
    }

    private fun setupUserSelection() {
        // Seed internal selection from ViewModel
        graphViewModel.selectedSmokerIds.value?.let { selectedSmokersInternal.addAll(it) }

        // Observe smokers list and rebuild chips (same pattern as StatsFragment)
        val repo = (requireActivity().application as CloudCounterApplication).repository
        repo.allSmokers.observe(viewLifecycleOwner) { list ->
            allSmokersCache = list

            // Rebuild user selection chips
            binding.chipGroupUsers.removeAllViews()

            // "All Smokers" chip
            val allChip = Chip(requireContext()).apply {
                text = "All Smokers"
                isCheckable = true
                isChecked = graphViewModel.selectedSmokerIds.value.isNullOrEmpty()
                setOnClickListener {
                    smokerSelectionDebounceJob?.cancel()
                    selectedSmokersInternal.clear()
                    graphViewModel.clearSmokers()
                    // Uncheck others manually
                    for (i in 0 until binding.chipGroupUsers.childCount) {
                        (binding.chipGroupUsers.getChildAt(i) as? Chip)?.isChecked = false
                    }
                    isChecked = true
                }
            }
            binding.chipGroupUsers.addView(allChip)

            // Individual smoker chips
            list.forEach { smoker ->
                val chip = Chip(requireContext()).apply {
                    text = smoker.name + if (smoker.isCloudSmoker) " ☁️" else ""
                    isCheckable = true
                    val current = graphViewModel.selectedSmokerIds.value
                    isChecked = current?.contains(smoker.smokerId) ?: false

                    setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            allChip.isChecked = false
                            selectedSmokersInternal.add(smoker.smokerId)
                        } else {
                            selectedSmokersInternal.remove(smoker.smokerId)
                        }

                        // Debounce applying to ViewModel (same as StatsFragment)
                        smokerSelectionDebounceJob?.cancel()
                        smokerSelectionDebounceJob = lifecycleScope.launch {
                            delay(150) // Batch quick toggles
                            graphViewModel.setSmokers(
                                selectedSmokersInternal.toSet().takeIf { it.isNotEmpty() }
                            )
                        }
                    }
                }
                binding.chipGroupUsers.addView(chip)
            }

            // Initial summary update
            updateSelectionSummary(graphViewModel.selectedSmokerIds.value)
        }
    }

    private fun updateSelectionSummary(selected: Set<Long>?) {
        val summary = if (selected.isNullOrEmpty()) {
            "All Smokers"
        } else {
            val names = allSmokersCache.filter { it.smokerId in selected }.map { it.name }
            when {
                names.isEmpty() -> "Unknown"
                names.size <= 2 -> names.joinToString(" + ")
                else -> names.take(2).joinToString(" + ") + " +${names.size - 2}"
            }
        }
        binding.textSelectedUsersSummary.text = summary
    }

    private fun setupChartAppearance() {
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.isDoubleTapToZoomEnabled = true

        // CHANGE: Set transparent background instead of grey
        lineChart.setBackgroundColor(Color.TRANSPARENT)

        lineChart.description.isEnabled = false

        // FIX: Disable the built-in legend entirely
        lineChart.legend.isEnabled = false

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = Color.DKGRAY
        xAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_on_dark_background)
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true
        xAxis.labelRotationAngle = -60f
        xAxis.setAvoidFirstLastClipping(true)
        xAxis.textSize = 9f

        val leftAxis = lineChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.DKGRAY
        leftAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_on_dark_background)
        leftAxis.axisMinimum = 0f
        leftAxis.granularity = 1f
        leftAxis.isGranularityEnabled = true

        lineChart.axisRight.isEnabled = false

        lineChart.setNoDataText("Select filters to see data.")
        lineChart.setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_dark_background))

        // FIX: Reset top offset and keep bottom padding
        lineChart.setExtraOffsets(0f, 0f, 0f, 50f)
    }


    private fun configureXAxisLabelCount(xAxis: XAxis, data: Any?, period: TimePeriod, effectiveDisplayMode: EffectiveDisplayMode? = null) {
        Log.d("GraphDebug", "=== configureXAxisLabelCount ===")
        Log.d("GraphDebug", "Period: $period, Effective Mode: $effectiveDisplayMode")

        val entryCount = when (data) {
            is LineData -> if (data.entryCount == 0) return else data.entryCount
            is BarData -> if (data.entryCount == 0) return else data.entryCount
            else -> return
        }

        val (xMin, xMax) = when (data) {
            is LineData -> Pair(data.xMin, data.xMax)
            is BarData -> Pair(data.xMin, data.xMax)
            else -> return
        }

        val range = xMax - xMin
        Log.d("GraphDebug", "Data range: ${range / 1000 / 60} minutes (${Date(xMin.toLong())} to ${Date(xMax.toLong())})")

        // Use effective display mode for label count if available
        val desiredLabelCount = if (effectiveDisplayMode != null) {
            when (effectiveDisplayMode) {
                EffectiveDisplayMode.MINUTELY -> 6
                EffectiveDisplayMode.HOURLY -> 8
                EffectiveDisplayMode.DAILY -> 8
                EffectiveDisplayMode.WEEKLY -> 7
                EffectiveDisplayMode.MONTHLY -> 8
                EffectiveDisplayMode.YEARLY -> 6
            }
        } else {
            when (period) {
                TimePeriod.MINUTELY -> 6
                TimePeriod.HOURLY -> 8
                TimePeriod.DAILY -> 8
                TimePeriod.WEEKLY -> 7
                TimePeriod.FORTNIGHTLY -> 7
                TimePeriod.MONTHLY -> 8
                TimePeriod.YEARLY -> 6
                TimePeriod.ALL_TIME -> 8
            }
        }

        Log.d("GraphDebug", "Setting label count to: $desiredLabelCount")

        xAxis.setLabelCount(desiredLabelCount, true)
        xAxis.isGranularityEnabled = false

        // Check if it's a bar chart
        val isBarChart = data is BarData

        if (!isBarChart) {
            // Only set custom axis ranges for line charts
            when (effectiveDisplayMode ?: EffectiveDisplayMode.valueOf(period.name)) {
                EffectiveDisplayMode.MINUTELY -> {
                    val startCal = Calendar.getInstance().apply {
                        timeInMillis = xMin.toLong()
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        val minute = get(Calendar.MINUTE)
                        set(Calendar.MINUTE, (minute / 10) * 10)
                    }
                    val endCal = Calendar.getInstance().apply {
                        timeInMillis = xMax.toLong()
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        val minute = get(Calendar.MINUTE)
                        set(Calendar.MINUTE, ((minute / 10) + 1) * 10)
                    }
                    xAxis.axisMinimum = startCal.timeInMillis.toFloat()
                    xAxis.axisMaximum = endCal.timeInMillis.toFloat()
                }
                else -> {
                    xAxis.axisMinimum = xMin
                    xAxis.axisMaximum = xMax
                }
            }
        } else {
            // For bar charts, add padding to make bars visible for Minutely
            if (period == TimePeriod.MINUTELY || effectiveDisplayMode == EffectiveDisplayMode.MINUTELY) {
                // Add 5 minutes padding on each side for minutely bar charts
                xAxis.axisMinimum = xMin - TimeUnit.MINUTES.toMillis(5).toFloat()
                xAxis.axisMaximum = xMax + TimeUnit.MINUTES.toMillis(5).toFloat()
                Log.d("GraphDebug", "Bar chart MINUTELY - added padding")
            } else {
                xAxis.resetAxisMinimum()
                xAxis.resetAxisMaximum()
            }
            Log.d("GraphDebug", "Bar chart detected - axis range adjusted")
        }

        // Set formatter based on effective display mode
        val formatterPeriod = when (effectiveDisplayMode) {
            EffectiveDisplayMode.MINUTELY -> TimePeriod.MINUTELY
            EffectiveDisplayMode.HOURLY -> TimePeriod.HOURLY
            EffectiveDisplayMode.DAILY -> TimePeriod.DAILY
            EffectiveDisplayMode.WEEKLY -> TimePeriod.WEEKLY
            EffectiveDisplayMode.MONTHLY -> TimePeriod.MONTHLY
            EffectiveDisplayMode.YEARLY -> TimePeriod.YEARLY
            else -> period
        }

        if (formatterPeriod != TimePeriod.MINUTELY) {
            xAxis.setAvoidFirstLastClipping(false)
            val formatter = TimestampXAxisValueFormatterWithSkip(formatterPeriod, skipFirstLast = true)
            formatter.setTotalLabels(desiredLabelCount)
            xAxis.valueFormatter = formatter
        } else {
            xAxis.setAvoidFirstLastClipping(true)
            xAxis.valueFormatter = TimestampXAxisValueFormatter(formatterPeriod)
        }

        Log.d("GraphDebug", "Axis range: min=${xAxis.axisMinimum}, max=${xAxis.axisMaximum}")
        Log.d("GraphDebug", "=== END configureXAxisLabelCount ===")
    }

    private fun customizeDataSetsInFragment(data: LineData) {
        for (iDataSet: ILineDataSet in data.dataSets) {
            if (iDataSet is LineDataSet) {
                // Common styling for all lines
                iDataSet.circleRadius = 3.0f
                iDataSet.setDrawCircleHole(false)
                iDataSet.lineWidth = 2.0f
                iDataSet.setDrawValues(false)
                iDataSet.valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_on_dark_background)
                iDataSet.mode = LineDataSet.Mode.HORIZONTAL_BEZIER

                // Specific styling based on label
                when (iDataSet.label) {
                    "Joints" -> {
                        iDataSet.color = graphViewModel.colorJoints
                        iDataSet.setCircleColor(graphViewModel.colorJoints)
                    }
                    "Cones" -> {
                        iDataSet.color = graphViewModel.colorCones
                        iDataSet.setCircleColor(graphViewModel.colorCones)
                    }
                    "Bowls" -> {
                        iDataSet.color = graphViewModel.colorBowls
                        iDataSet.setCircleColor(graphViewModel.colorBowls)
                    }
                    else -> {
                        // Custom activity - color already set in ViewModel
                        // Just ensure circle color matches line color
                        iDataSet.setCircleColor(iDataSet.color)
                    }
                }
            }
        }
    }

    private fun getChipIdForTimePeriod(period: TimePeriod): Int {
        return when (period) {
            TimePeriod.MINUTELY -> R.id.chipGraphMinutely
            TimePeriod.HOURLY -> R.id.chipGraphHourly
            TimePeriod.DAILY -> R.id.chipGraphDaily
            TimePeriod.WEEKLY -> R.id.chipGraphWeekly
            TimePeriod.FORTNIGHTLY -> R.id.chipGraphFortnightly
            TimePeriod.MONTHLY -> R.id.chipGraphMonthly
            TimePeriod.YEARLY -> R.id.chipGraphYearly
            TimePeriod.ALL_TIME -> R.id.chipGraphAllTime
        }
    }

    private fun setupTimePeriodChipGroupListener() {
        binding.chipGroupGraphTimePeriod.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chipId = checkedIds.first()
                if (group.findViewById<View>(chipId) != null) {
                    val period = when (chipId) {
                        R.id.chipGraphMinutely -> TimePeriod.MINUTELY
                        R.id.chipGraphHourly -> TimePeriod.HOURLY
                        R.id.chipGraphDaily -> TimePeriod.DAILY
                        R.id.chipGraphWeekly -> TimePeriod.WEEKLY
                        R.id.chipGraphFortnightly -> TimePeriod.FORTNIGHTLY
                        R.id.chipGraphMonthly -> TimePeriod.MONTHLY
                        R.id.chipGraphYearly -> TimePeriod.YEARLY
                        R.id.chipGraphAllTime -> TimePeriod.ALL_TIME
                        else -> {
                            Log.w("GraphFragment", "Unknown chip ID in TimePeriod listener: $chipId")
                            TimePeriod.ALL_TIME
                        }
                    }
                    Log.d("GraphFragment", "Chip selected, setting period in ViewModel: $period")
                    graphViewModel.setGraphTimePeriod(period)
                } else {
                    Log.e("GraphFragment", "Selected chip ID not found in group: $chipId")
                }
            }
        }
    }

    private fun setupLineToggleCheckBoxListeners() {
        binding.checkBoxShowJoints.setOnCheckedChangeListener { _, isChecked ->
            graphViewModel.setShowJoints(isChecked)
        }
        binding.checkBoxShowCones.setOnCheckedChangeListener { _, isChecked ->
            graphViewModel.setShowCones(isChecked)
        }
        binding.checkBoxShowBowls.setOnCheckedChangeListener { _, isChecked ->
            graphViewModel.setShowBowls(isChecked)
        }

        // Set initial states from ViewModel
        binding.checkBoxShowJoints.isChecked = graphViewModel.showJoints.value ?: true
        binding.checkBoxShowCones.isChecked = graphViewModel.showCones.value ?: true
        binding.checkBoxShowBowls.isChecked = graphViewModel.showBowls.value ?: true
    }
    
    private fun observeCustomActivities() {
        // Get active custom activities from CustomActivityManager
        val customActivityManager = (requireActivity() as? MainActivity)?.customActivityManager
        if (customActivityManager != null) {
            // Get currently active custom activities from the manager
            val activeCustomActivities = customActivityManager.getCustomActivities()
            
            // Update UI with active custom activities
            updateCustomActivityCheckboxes(activeCustomActivities)
            updateCustomActivityLegend(activeCustomActivities)
            
            // Also observe for changes in logged activities to refresh the graph
            val repo = (requireActivity().application as CloudCounterApplication).repository
            repo.allActivities.observe(viewLifecycleOwner) { _ ->
                // Refresh UI when activities change
                val currentCustomActivities = customActivityManager.getCustomActivities()
                updateCustomActivityCheckboxes(currentCustomActivities)
                updateCustomActivityLegend(currentCustomActivities)
            }
        }
    }
    
    private fun updateCustomActivityCheckboxes(customActivities: List<CustomActivity>) {
        // Get the parent container of the checkboxes
        val checkboxContainer = binding.checkBoxShowJoints.parent as? LinearLayout ?: return
        
        // Remove any existing custom activity checkboxes (keep first 3 for joints/cones/bowls)
        while (checkboxContainer.childCount > 3) {
            checkboxContainer.removeViewAt(3)
        }
        
        // Add checkbox for each custom activity
        customActivities.forEach { activity ->
            val checkbox = CheckBox(requireContext()).apply {
                text = activity.name
                isChecked = graphViewModel.getShowCustomActivity(activity.id)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_dark_background))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                
                setOnCheckedChangeListener { _, isChecked ->
                    graphViewModel.setShowCustomActivity(activity.id, isChecked)
                }
            }
            checkboxContainer.addView(checkbox)
        }
    }
    
    private fun updateCustomActivityLegend(customActivities: List<CustomActivity>) {
        // Get the legend container
        val legendContainer = binding.root.findViewById<LinearLayout>(R.id.custom_legend_container) ?: return
        
        // Remove any existing custom activity legend items (keep first 3 for joints/cones/bowls)
        while (legendContainer.childCount > 3) {
            legendContainer.removeViewAt(3)
        }
        
        // Add legend item for each custom activity (up to 6 total custom activities)
        customActivities.take(6).forEachIndexed { index, activity ->
            val legendItem = TextView(requireContext()).apply {
                text = activity.name
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_dark_background))
                textSize = 12f
                
                // Create colored dot drawable
                val dot = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    val color = graphViewModel.customActivityColors.getOrElse(index) { 
                        graphViewModel.customActivityColors[0] 
                    }
                    setColor(color)
                    setSize(24, 24)
                }
                setCompoundDrawablesWithIntrinsicBounds(dot, null, null, null)
                compoundDrawablePadding = 4 * resources.displayMetrics.density.toInt()
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 12 * resources.displayMetrics.density.toInt()
                }
            }
            legendContainer.addView(legendItem)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class TimestampXAxisValueFormatterWithSkip(
    private var timePeriod: TimePeriod,
    private val skipFirstLast: Boolean = false
) : ValueFormatter() {
    private val minutelySdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val hourlySdf = SimpleDateFormat("ha", Locale.getDefault()) // Shorter format
    private val dailyTimeSdf = SimpleDateFormat("MMM d ha", Locale.getDefault()) // Shorter format
    private val dailySdf = SimpleDateFormat("MMM d", Locale.getDefault())
    private val monthlySdf = SimpleDateFormat("MMM d", Locale.getDefault())
    private val yearlySdf = SimpleDateFormat("MMM yy", Locale.getDefault())

    private var labelIndex = 0
    private var totalLabels = 0

    fun setTotalLabels(count: Int) {
        totalLabels = count
        labelIndex = 0
    }

    override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
        // Track which label we're formatting
        val currentIndex = labelIndex++

        // Skip first and last labels if requested
        if (skipFirstLast && totalLabels > 0) {
            if (currentIndex == 0 || currentIndex == totalLabels - 1) {
                return "" // Return empty string for first and last labels
            }
        }

        val timestamp = value.toLong()

        // Round timestamp to clean intervals based on period
        val roundedTimestamp = when (timePeriod) {
            TimePeriod.MINUTELY -> {
                // Round to nearest 10 minutes
                val cal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    val minute = get(Calendar.MINUTE)
                    set(Calendar.MINUTE, (minute / 10) * 10)
                }
                cal.timeInMillis
            }
            TimePeriod.HOURLY -> {
                // Round to nearest hour
                val cal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }
            TimePeriod.DAILY -> {
                // Round to nearest 3 hours for cleaner display
                val cal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    val hour = get(Calendar.HOUR_OF_DAY)
                    set(Calendar.HOUR_OF_DAY, (hour / 3) * 3)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }
            TimePeriod.WEEKLY, TimePeriod.FORTNIGHTLY -> {
                // Round to start of day
                val cal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }
            TimePeriod.MONTHLY -> {
                // Round to start of day
                val cal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }
            TimePeriod.YEARLY -> {
                // Round to start of month
                val cal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }
            TimePeriod.ALL_TIME -> timestamp  // Don't round for all time
        }

        return try {
            when (timePeriod) {
                TimePeriod.MINUTELY -> minutelySdf.format(Date(roundedTimestamp))
                TimePeriod.HOURLY -> hourlySdf.format(Date(roundedTimestamp))
                TimePeriod.DAILY -> hourlySdf.format(Date(roundedTimestamp))
                TimePeriod.WEEKLY, TimePeriod.FORTNIGHTLY -> dailySdf.format(Date(roundedTimestamp))
                TimePeriod.MONTHLY -> monthlySdf.format(Date(roundedTimestamp))
                TimePeriod.YEARLY -> yearlySdf.format(Date(roundedTimestamp))
                TimePeriod.ALL_TIME -> dailySdf.format(Date(roundedTimestamp))
            }
        } catch (e: Exception) {
            Log.e("XAxisFormatter", "Error formatting timestamp: $value for period $timePeriod", e)
            ""
        }
    }
}

// TimestampXAxisValueFormatter
class TimestampXAxisValueFormatter(private var timePeriod: TimePeriod) : ValueFormatter() {
    private val minutelySdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val hourlySdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dailyTimeSdf = SimpleDateFormat("MMM d h:mma", Locale.getDefault())
    private val dailySdf = SimpleDateFormat("MMM d", Locale.getDefault())
    private val monthlySdf = SimpleDateFormat("MMM d", Locale.getDefault())
    private val yearlySdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())

    override fun getAxisLabel(
        value: Float,
        axis: com.github.mikephil.charting.components.AxisBase?
    ): String {
        val timestamp = value.toLong()

        // Round timestamp to clean intervals based on period
        val roundedTimestamp = when (timePeriod) {
            TimePeriod.MINUTELY -> {
                // Round to nearest 10 minutes
                val cal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    val minute = get(Calendar.MINUTE)
                    set(Calendar.MINUTE, (minute / 10) * 10)
                }
                cal.timeInMillis
            }

            TimePeriod.HOURLY -> {
                // Round to nearest hour
                val cal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }

            TimePeriod.DAILY -> {
                // Round to nearest 2 hours
                val cal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    val hour = get(Calendar.HOUR_OF_DAY)
                    set(Calendar.HOUR_OF_DAY, (hour / 2) * 2)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }

            TimePeriod.WEEKLY, TimePeriod.FORTNIGHTLY -> {
                // Round to noon or midnight
                val cal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    val hour = get(Calendar.HOUR_OF_DAY)
                    set(Calendar.HOUR_OF_DAY, if (hour < 12) 0 else 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }

            TimePeriod.MONTHLY -> {
                // Round to start of day
                val cal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }

            TimePeriod.YEARLY -> {
                // Round to start of month
                val cal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }

            TimePeriod.ALL_TIME -> timestamp  // Don't round for all time
        }

        return try {
            when (timePeriod) {
                TimePeriod.MINUTELY -> minutelySdf.format(Date(roundedTimestamp))
                TimePeriod.HOURLY -> hourlySdf.format(Date(roundedTimestamp))
                TimePeriod.DAILY -> dailyTimeSdf.format(Date(roundedTimestamp))
                TimePeriod.WEEKLY, TimePeriod.FORTNIGHTLY -> dailyTimeSdf.format(
                    Date(
                        roundedTimestamp
                    )
                )

                TimePeriod.MONTHLY -> monthlySdf.format(Date(roundedTimestamp))
                TimePeriod.YEARLY -> yearlySdf.format(Date(roundedTimestamp))
                TimePeriod.ALL_TIME -> dailySdf.format(Date(roundedTimestamp))
            }
        } catch (e: Exception) {
            Log.e("XAxisFormatter", "Error formatting timestamp: $value for period $timePeriod", e)
            value.toLong().toString()
        }
    }
}