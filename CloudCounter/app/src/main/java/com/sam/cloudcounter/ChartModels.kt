package com.sam.cloudcounter

import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Data class to hold all prepared chart data and related UI information.
 * @param lineData The LineData object for MPAndroidChart.
 * @param descriptionText Text to display as the chart's description.
 * @param processedPeriod The TimePeriod for which this data was processed.
 * @param chartType The type of chart to display (LINE or BAR).
 * @param actualTimeRange The actual start and end timestamps of the data entries.
 * @param effectiveDisplayMode The calculated display granularity (e.g., HOURLY, DAILY).
 */
data class ChartUiData(
    val lineData: LineData?,
    val descriptionText: String,
    val processedPeriod: TimePeriod,
    val chartType: GraphViewModel.ChartType,
    val actualTimeRange: Pair<Long?, Long?>,
    val effectiveDisplayMode: EffectiveDisplayMode?
)