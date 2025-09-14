package com.sam.cloudcounter

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlin.math.floor
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
    // Track last non-custom time chip to restore on cancel
    private var lastNonCustomGraphTimeChipId: Int = R.id.chipGraphAllTime
    // Track selected sessions count to update chip label
    private var lastSelectedSessionsCountGraph: Int = 0
    // Guard to prevent double-opening the custom picker dialog
    private var isGraphCustomDialogShowing: Boolean = false

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
                // Set time period chip or custom state
                val useCustom = graphViewModel.useCustomSessions.value == true
                val initialPeriod = graphViewModel.selectedTimePeriod.value ?: TimePeriod.ALL_TIME
                if (useCustom) {
                    it.chipGroupGraphTimePeriod.check(R.id.chipGraphCustom)
                } else {
                    it.chipGroupGraphTimePeriod.check(getChipIdForTimePeriod(initialPeriod))
                }

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

        // Observe custom session selections to update chip label and selection
        graphViewModel.customSessionRanges.observe(viewLifecycleOwner, Observer { ranges ->
            lastSelectedSessionsCountGraph = ranges?.size ?: 0
            updateGraphCustomChipLabel(lastSelectedSessionsCountGraph == 0)
        })
        graphViewModel.useCustomSessions.observe(viewLifecycleOwner, Observer { useCustom ->
            val group = _binding?.chipGroupGraphTimePeriod ?: return@Observer
            if (useCustom == true) {
                if (group.checkedChipId != R.id.chipGraphCustom) group.check(R.id.chipGraphCustom)
            } else {
                if (group.checkedChipId == R.id.chipGraphCustom) group.check(lastNonCustomGraphTimeChipId)
            }
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

    private fun updateGraphCustomChipLabel(reset: Boolean = false) {
        val chip = binding.chipGroupGraphTimePeriod.findViewById<Chip>(R.id.chipGraphCustom)
        if (reset || lastSelectedSessionsCountGraph <= 0) {
            chip?.text = "Custom"
        } else {
            chip?.text = "${lastSelectedSessionsCountGraph} sesh's selected"
        }
    }

    private fun showBarChart(chartUiData: ChartUiData) {
        Log.d("GraphDebug", "=== SHOWING BAR CHART ===")

        lineChart.visibility = View.GONE
        barChart.visibility = View.VISIBLE

        if (chartUiData.lineData != null && chartUiData.lineData.entryCount > 0) {
            Log.d("GraphDebug", "Converting line data to GROUPED bar data")
            Log.d("GraphDebug", "Line data has ${chartUiData.lineData.dataSetCount} datasets")

            // 1) Build a sorted list of unique time buckets from all datasets
            val timestampSet = mutableSetOf<Long>()
            chartUiData.lineData.dataSets.forEach { ds ->
                val lds = ds as? LineDataSet ?: return@forEach
                for (i in 0 until lds.entryCount) {
                    val e = lds.getEntryForIndex(i)
                    timestampSet.add(e.x.toLong())
                }
            }
            val timestamps = timestampSet.toList().sorted()
            val indexByTimestamp = timestamps.withIndex().associate { it.value to it.index }
            val groupCount = timestamps.size

            // 2) For each dataset, create a BarDataSet with one entry per group index (fill 0f for missing)
            val barDataSets = mutableListOf<BarDataSet>()
            chartUiData.lineData.dataSets.forEach { ds ->
                val lds = ds as? LineDataSet ?: return@forEach
                val yByIndex = FloatArray(groupCount) { 0f }
                for (i in 0 until lds.entryCount) {
                    val e = lds.getEntryForIndex(i)
                    val idx = indexByTimestamp[e.x.toLong()] ?: continue
                    yByIndex[idx] = e.y
                }
                val bars = ArrayList<BarEntry>(groupCount)
                for (i in 0 until groupCount) {
                    bars.add(BarEntry(i.toFloat(), yByIndex[i]))
                }
                val bds = BarDataSet(bars, lds.label)
                bds.color = lds.color
                bds.valueTextColor = Color.WHITE
                bds.setDrawValues(false)
                barDataSets.add(bds)
            }

            if (barDataSets.isNotEmpty() && groupCount > 0) {
                val iSets: List<IBarDataSet> = barDataSets.map { it as IBarDataSet }
                val barData = BarData(iSets)

                // 3) Configure grouped bars: define space distribution inside each group
                val dataSetCount = barDataSets.size
                val groupSpace = 0.20f
                val barSpace = 0.02f
                val barWidth = ((1f - groupSpace) / dataSetCount) - barSpace
                barData.barWidth = barWidth.coerceAtLeast(0.02f)

                // 4) Axis: switch to index-based groups with timestamp labels
                val xAxis = barChart.xAxis
                xAxis.setCenterAxisLabels(true)
                // Set axis range to fit all groups
                val startX = 0f
                val groupWidth = barData.getGroupWidth(groupSpace, barSpace)
                // Use group width for granularity so ticks align with groups
                xAxis.granularity = groupWidth
                xAxis.isGranularityEnabled = true
                xAxis.setAvoidFirstLastClipping(false)
                // Aim for ~8 labels or fewer
                val desiredLabels = if (groupCount > 8) 8 else groupCount
                xAxis.setLabelCount(desiredLabels, true)

                // Use index->timestamp formatter for labels with grouping geometry
                xAxis.valueFormatter = IndexTimestampAxisValueFormatter(timestamps, chartUiData.processedPeriod, startX, groupWidth)

                xAxis.axisMinimum = startX
                xAxis.axisMaximum = startX + groupWidth * groupCount

                // Apply grouping
                try {
                    barData.groupBars(startX, groupSpace, barSpace)
                } catch (e: Exception) {
                    Log.w("GraphDebug", "groupBars failed: ${e.message}")
                }

                // 5) Dynamic height based on group count
                val baseHeightPx = (300 * resources.displayMetrics.density).toInt()
                val extraPerGroupPx = (0.5f * resources.displayMetrics.density).toInt()
                val computedHeight = baseHeightPx + groupCount * extraPerGroupPx
                val maxHeight = (resources.displayMetrics.heightPixels * 0.8).toInt()
                val finalHeight = computedHeight.coerceAtMost(maxHeight)
                barChart.layoutParams = barChart.layoutParams.apply { height = finalHeight }
                barChart.requestLayout()

                // 6) Assign data and refresh
                barChart.data = barData
                barChart.description.text = chartUiData.descriptionText
                barChart.animateY(750)
                barChart.invalidate()

                Log.d("GraphDebug", "Grouped Bar chart updated: groups=$groupCount, sets=${dataSetCount}, barWidth=$barWidth")
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

            // Build a jittered copy so overlapping datasets are visible
            val jitteredData = buildJitteredLineData(
                chartUiData.lineData,
                chartUiData.effectiveDisplayMode,
                chartUiData.processedPeriod
            )

            customizeDataSetsInFragment(jitteredData)

            lineChart.xAxis.valueFormatter = TimestampXAxisValueFormatter(chartUiData.processedPeriod)
            configureXAxisLabelCount(lineChart.xAxis, jitteredData, chartUiData.processedPeriod, chartUiData.effectiveDisplayMode)

            lineChart.data = jitteredData
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

    // Create a copy of LineData with per-dataset x-jitter so overlapping values are visible.
    private fun buildJitteredLineData(
        source: LineData,
        effectiveMode: EffectiveDisplayMode?,
        processedPeriod: TimePeriod
    ): LineData {
        val dataSetCount = source.dataSetCount
        if (dataSetCount <= 1) return source

        val effMode = effectiveMode ?: runCatching { EffectiveDisplayMode.valueOf(processedPeriod.name) }.getOrNull()
        val bucketWidthMs = when (effMode) {
            EffectiveDisplayMode.MINUTELY -> 60_000f
            EffectiveDisplayMode.HOURLY -> 3_600_000f
            EffectiveDisplayMode.DAILY -> 86_400_000f
            EffectiveDisplayMode.WEEKLY -> 86_400_000f // daily buckets across a week
            EffectiveDisplayMode.MONTHLY -> 86_400_000f // daily buckets across a month
            EffectiveDisplayMode.YEARLY -> 2_592_000_000f // ~30 days
            else -> 86_400_000f
        }

        // Spread datasets within a fraction of the bucket (smaller for larger buckets)
        val jitterFraction = when (effMode) {
            EffectiveDisplayMode.MINUTELY -> 0.6f
            EffectiveDisplayMode.HOURLY -> 0.5f
            EffectiveDisplayMode.DAILY, EffectiveDisplayMode.WEEKLY, EffectiveDisplayMode.MONTHLY -> 0.25f
            EffectiveDisplayMode.YEARLY -> 0.10f
            else -> 0.25f
        }
        val totalJitter = bucketWidthMs * jitterFraction
        val step = if (dataSetCount > 1) totalJitter / (dataSetCount - 1) else 0f
        val center = -totalJitter / 2f

        val newSets = mutableListOf<LineDataSet>()
        for ((idx, ds) in source.dataSets.withIndex()) {
            val lds = ds as? LineDataSet ?: continue
            val offset = center + step * idx
            val newEntries = ArrayList<com.github.mikephil.charting.data.Entry>(lds.entryCount)
            for (i in 0 until lds.entryCount) {
                val e = lds.getEntryForIndex(i)
                newEntries.add(com.github.mikephil.charting.data.Entry(e.x + offset, e.y))
            }
            val copy = LineDataSet(newEntries, lds.label)
            // Preserve color; styling will be applied later
            copy.color = lds.color
            copy.setCircleColor(lds.color)
            newSets.add(copy)
        }
        return LineData(newSets as List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>)
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
                iDataSet.circleRadius = 4.0f
                iDataSet.setDrawCircleHole(true)
                iDataSet.circleHoleRadius = 2.2f
                iDataSet.setCircleHoleColor(Color.WHITE)
                iDataSet.lineWidth = 2.2f
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
                if (chipId == R.id.chipGraphCustom) {
                    // Enter custom session selection mode
                    showCustomSessionPickerDialog(
                        onDone = { selectedRanges ->
                            if (selectedRanges.isEmpty()) {
                                graphViewModel.setUseCustomSessions(false)
                                group.check(lastNonCustomGraphTimeChipId)
                                if (lastSelectedSessionsCountGraph == 0) updateGraphCustomChipLabel(true)
                            } else {
                                graphViewModel.setCustomSessions(selectedRanges)
                                graphViewModel.setUseCustomSessions(true)
                                lastSelectedSessionsCountGraph = selectedRanges.size
                                updateGraphCustomChipLabel(false)
                            }
                        },
                        onCancel = {
                            graphViewModel.setUseCustomSessions(false)
                            group.check(lastNonCustomGraphTimeChipId)
                            if (lastSelectedSessionsCountGraph == 0) updateGraphCustomChipLabel(true)
                        }
                    )
                } else if (group.findViewById<View>(chipId) != null) {
                    // Record non-custom selection and disable custom mode
                    lastNonCustomGraphTimeChipId = chipId
                    graphViewModel.setUseCustomSessions(false)
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

        // Allow tapping the Custom chip again to reopen and edit selection
        binding.root.post {
            val customChip = binding.chipGroupGraphTimePeriod.findViewById<Chip>(R.id.chipGraphCustom)
            customChip?.setOnClickListener {
                val alreadyChecked = binding.chipGroupGraphTimePeriod.checkedChipId == R.id.chipGraphCustom
                if (!alreadyChecked) return@setOnClickListener
                showCustomSessionPickerDialog(
                    onDone = { selectedRanges ->
                        if (selectedRanges.isEmpty()) {
                            graphViewModel.setUseCustomSessions(false)
                            if (binding.chipGroupGraphTimePeriod.checkedChipId == R.id.chipGraphCustom) {
                                binding.chipGroupGraphTimePeriod.check(lastNonCustomGraphTimeChipId)
                            }
                            if (lastSelectedSessionsCountGraph == 0) updateGraphCustomChipLabel(true)
                        } else {
                            graphViewModel.setCustomSessions(selectedRanges)
                            graphViewModel.setUseCustomSessions(true)
                            lastSelectedSessionsCountGraph = selectedRanges.size
                            if (binding.chipGroupGraphTimePeriod.checkedChipId != R.id.chipGraphCustom) {
                                binding.chipGroupGraphTimePeriod.check(R.id.chipGraphCustom)
                            }
                            updateGraphCustomChipLabel(false)
                        }
                    },
                    onCancel = {
                        if (lastSelectedSessionsCountGraph == 0 && binding.chipGroupGraphTimePeriod.checkedChipId == R.id.chipGraphCustom) {
                            binding.chipGroupGraphTimePeriod.check(lastNonCustomGraphTimeChipId)
                        }
                    }
                )
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

    // ===== Custom Session Picker Dialog (1:1 styling with Stats) =====
    private fun showCustomSessionPickerDialog(
        onDone: (List<Pair<Long, Long>>) -> Unit,
        onCancel: () -> Unit
    ) {
        // Prevent double-opening due to both ChipGroup state change and chip click firing
        if (isGraphCustomDialogShowing) return
        isGraphCustomDialogShowing = true

        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        val rootContainer = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.TRANSPARENT)
        }

        val mainCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 16.dpToPx().toFloat()
            cardElevation = 8.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).let {
                android.widget.FrameLayout.LayoutParams(it.width, it.height).apply {
                    gravity = android.view.Gravity.CENTER
                    setMargins(16.dpToPx(), 0, 16.dpToPx(), 0)
                }
            }
        }

        val contentLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(20.dpToPx(), 20.dpToPx(), 20.dpToPx(), 12.dpToPx())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleText = android.widget.TextView(requireContext()).apply {
            text = "SELECT SESSIONS"
            textSize = 18f
            setTextColor(Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.1f
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        val subtitle = android.widget.TextView(requireContext()).apply {
            text = "Tap to select multiple sessions"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12.dpToPx()
            }
        }
        contentLayout.addView(subtitle)

        val divider = View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2.dpToPx()).apply {
                topMargin = 4.dpToPx()
                bottomMargin = 12.dpToPx()
            }
            setBackgroundColor(Color.parseColor("#3398FB98"))
        }
        contentLayout.addView(divider)

        val scroll = android.widget.ScrollView(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val listContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(listContainer)
        contentLayout.addView(scroll)

        val buttonRow = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12.dpToPx()
            }
        }

        val cancelButton = createGraphThemedDialogButton("Cancel", false, Color.WHITE) {
            animateCardSelectionQuick(dialog) { onCancel() }
        }.apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, 44.dpToPx(), 1f).apply { marginEnd = 8.dpToPx() }
        }

        val selectedRanges = mutableListOf<Pair<Long, Long>>()
        graphViewModel.customSessionRanges.value?.let { selectedRanges.addAll(it) }

        val doneButton = createGraphThemedDialogButton("Done", true, Color.parseColor("#98FB98")) {
            // If current session is selected, refresh its end time to 'now' before returning
            val seshPrefs = requireContext().getSharedPreferences("sesh", android.content.Context.MODE_PRIVATE)
            val isActive = seshPrefs.getBoolean("sessionActive", false)
            val sessionStartPref = seshPrefs.getLong("sessionStart", 0L)
            if (isActive && sessionStartPref > 0L && selectedRanges.any { it.first == sessionStartPref }) {
                selectedRanges.removeAll { it.first == sessionStartPref }
                selectedRanges.add(Pair(sessionStartPref, System.currentTimeMillis()))
            }
            val finalList = selectedRanges.toList()
            animateCardSelectionQuick(dialog) { onDone(finalList) }
        }.apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, 44.dpToPx(), 1f).apply { marginStart = 8.dpToPx() }
        }

        buttonRow.addView(cancelButton)
        buttonRow.addView(doneButton)
        contentLayout.addView(buttonRow)
        mainCard.addView(contentLayout)
        rootContainer.addView(mainCard)

        // Dismiss when tapping outside
        rootContainer.setOnClickListener { v -> if (v == rootContainer) animateCardSelectionQuick(dialog) { onCancel() } }

        dialog.setContentView(rootContainer)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#80000000")))
            setFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }

        // Reset guard when dialog is dismissed from any path
        dialog.setOnDismissListener { isGraphCustomDialogShowing = false }

        // Populate sessions list (most recent first)
        val app = requireActivity().application as CloudCounterApplication
        val repo = app.repository
        repo.allSummaries.observe(viewLifecycleOwner) { list ->
            listContainer.removeAllViews()
            // Add current session card if active
            val seshPrefs = requireContext().getSharedPreferences("sesh", android.content.Context.MODE_PRIVATE)
            val isActive = seshPrefs.getBoolean("sessionActive", false)
            val sessionStartPref = seshPrefs.getLong("sessionStart", 0L)
            val currentRoomName = seshPrefs.getString("currentRoomName", null)
            if (isActive && sessionStartPref > 0L) {
                val endNow = System.currentTimeMillis()
                val initiallySelected = selectedRanges.any { it.first == sessionStartPref }
                val currentItem = createGraphCurrentSessionListItemView(
                    title = currentRoomName ?: "Current Session",
                    start = sessionStartPref,
                    end = endNow,
                    initiallySelected = initiallySelected
                ) { view, isSelected ->
                    if (isSelected) {
                        // Replace or add with up-to-date end time
                        selectedRanges.removeAll { it.first == sessionStartPref }
                        selectedRanges.add(Pair(sessionStartPref, System.currentTimeMillis()))
                        setNeonBorder(view, true)
                    } else {
                        selectedRanges.removeAll { it.first == sessionStartPref }
                        setNeonBorder(view, false)
                    }
                }
                listContainer.addView(currentItem)
            }
            val sessions = list.sortedByDescending { it.timestamp }
            sessions.forEach { summary ->
                val start = (summary.timestamp - summary.sessionLength).coerceAtLeast(0)
                val end = summary.timestamp
                val initiallySelected = selectedRanges.contains(Pair(start, end))
                val item = createGraphSessionListItemView(summary, start, end, initiallySelected) { view, isSelected ->
                    if (isSelected) {
                        selectedRanges.add(Pair(start, end))
                        setNeonBorder(view, true)
                    } else {
                        selectedRanges.remove(Pair(start, end))
                        setNeonBorder(view, false)
                    }
                }
                listContainer.addView(item)
            }
        }

        // Ensure buttons remain above the system bottom navigation bar (match Stats)
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val extra = (12 * resources.displayMetrics.density).toInt()
            contentLayout.setPadding(
                contentLayout.paddingLeft,
                contentLayout.paddingTop,
                contentLayout.paddingRight,
                extra + bottomInset
            )
            insets
        }

        // Fade in
        rootContainer.alpha = 0f
        dialog.show()
        performManualFadeIn(rootContainer, 250L)
    }

    private fun createGraphSessionListItemView(
        summary: SessionSummary,
        start: Long,
        end: Long,
        initiallySelected: Boolean,
        onToggle: (View, Boolean) -> Unit
    ): View {
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dpToPx()
            }
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            background = android.graphics.drawable.ColorDrawable(Color.parseColor("#262626"))
        }

        val card = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 2.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#2C2C2C"))
        }

        val inner = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
        }

        val title = android.widget.TextView(requireContext()).apply {
            val name = summary.roomName ?: "Local Session"
            text = name
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_dark_background))
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val fmt = java.text.SimpleDateFormat("MMM d, h:mma", java.util.Locale.getDefault())
        val durationMin = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes((end - start).coerceAtLeast(0))
        val subtitle = android.widget.TextView(requireContext()).apply {
            text = "${fmt.format(java.util.Date(start))} → ${fmt.format(java.util.Date(end))}  •  ${durationMin} min"
            setTextColor(Color.LTGRAY)
            textSize = 12f
        }

        val statsLine = android.widget.TextView(requireContext()).apply {
            val total = summary.totalCones
            text = "Total: ${total}"
            setTextColor(Color.LTGRAY)
            textSize = 12f
        }

        inner.addView(title)
        inner.addView(subtitle)
        inner.addView(statsLine)
        card.addView(inner)
        container.addView(card)

        var selected = initiallySelected
        setNeonBorder(card, selected)
        container.setOnClickListener {
            selected = !selected
            onToggle(card, selected)
        }

        return container
    }

    private fun createGraphCurrentSessionListItemView(
        title: String,
        start: Long,
        end: Long,
        initiallySelected: Boolean,
        onToggle: (View, Boolean) -> Unit
    ): View {
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dpToPx()
            }
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            background = android.graphics.drawable.ColorDrawable(Color.parseColor("#262626"))
        }

        val card = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 2.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#2C2C2C"))
        }

        val inner = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
        }

        val titleView = android.widget.TextView(requireContext()).apply {
            text = title
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_dark_background))
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val fmt = java.text.SimpleDateFormat("MMM d, h:mma", java.util.Locale.getDefault())
        val durationMin = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes((end - start).coerceAtLeast(0))
        val subtitle = android.widget.TextView(requireContext()).apply {
            text = "${fmt.format(java.util.Date(start))} → ${fmt.format(java.util.Date(end))}  •  ${durationMin} min"
            setTextColor(Color.LTGRAY)
            textSize = 12f
        }

        val statsLine = android.widget.TextView(requireContext()).apply {
            text = "Live"
            setTextColor(Color.LTGRAY)
            textSize = 12f
        }

        inner.addView(titleView)
        inner.addView(subtitle)
        inner.addView(statsLine)
        card.addView(inner)
        container.addView(card)

        var selected = initiallySelected
        setNeonBorder(card, selected)
        container.setOnClickListener {
            selected = !selected
            onToggle(card, selected)
        }

        return container
    }

    private fun setNeonBorder(view: View, selected: Boolean) {
        val bg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 12.dpToPx().toFloat()
            setColor(Color.parseColor("#2C2C2C"))
            val strokeColor = if (selected) Color.parseColor("#98FB98") else Color.parseColor("#303030")
            setStroke((2 * resources.displayMetrics.density).toInt(), strokeColor)
        }
        (view as? androidx.cardview.widget.CardView)?.background = bg
    }

    private fun performManualFadeIn(view: View, durationMs: Long) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs
        val frameDelayMs = 16L
        val runnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val progress = ((now - startTime).toFloat() / durationMs).coerceIn(0f, 1f)
                val eased = 1f - (1f - progress) * (1f - progress)
                view.alpha = eased
                if (now < endTime) handler.postDelayed(this, frameDelayMs) else view.alpha = 1f
            }
        }
        handler.post(runnable)
    }

    private fun createGraphThemedDialogButton(text: String, isPrimary: Boolean, color: Int, onClick: () -> Unit): View {
        val ctx = requireContext()
        val buttonContainer = androidx.cardview.widget.CardView(ctx).apply {
            radius = 20.dpToPx().toFloat()
            cardElevation = if (isPrimary) 4.dpToPx().toFloat() else 0f
            setCardBackgroundColor(if (isPrimary) color else Color.parseColor("#33FFFFFF"))
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dpToPx()).apply {
                bottomMargin = 12.dpToPx()
            }
            isClickable = true
            isFocusable = true
        }

        val contentFrame = android.widget.FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val buttonText = android.widget.TextView(ctx).apply {
            this.text = text
            textSize = 14f
            setTextColor(if (isPrimary) Color.parseColor("#424242") else Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        contentFrame.addView(buttonText)
        buttonContainer.addView(contentFrame)

        buttonContainer.setOnClickListener { onClick() }
        return buttonContainer
    }

    private fun animateCardSelectionQuick(dialog: android.app.Dialog, onComplete: () -> Unit) {
        val contentView = dialog.window?.decorView?.findViewById<View>(android.R.id.content)
        val fadeOut = android.animation.ObjectAnimator.ofFloat(contentView, "alpha", 1f, 0f).apply {
            duration = 400L
            interpolator = android.view.animation.AccelerateInterpolator()
        }
        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                dialog.dismiss()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ onComplete() }, 100)
            }
        })
        fadeOut.start()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
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

// Maps x-index (group index) back to a timestamp label using existing timestamp formatter
class IndexTimestampAxisValueFormatter(
    private val timestamps: List<Long>,
    private val period: TimePeriod,
    private val startX: Float,
    private val groupWidth: Float
) : ValueFormatter() {
    private val delegate = TimestampXAxisValueFormatter(period)

    override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
        if (timestamps.isEmpty()) return ""
        // Convert chart x back to group index using group geometry
        val idx = floor(((value - startX) / groupWidth).toDouble()).toInt().coerceIn(0, timestamps.size - 1)
        val ts = timestamps[idx]
        return try {
            delegate.getAxisLabel(ts.toFloat(), axis)
        } catch (_: Exception) {
            ""
        }
    }
}
