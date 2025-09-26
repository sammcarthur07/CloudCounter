package com.vibecode.cloudcounter

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.vibecode.cloudcounter.databinding.FragmentCalendarBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: CalendarViewModel
    private lateinit var customActivityManager: CustomActivityManager

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    private val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val fmt = DecimalFormat("#.##")

    private var currentView = CalendarView.MONTHLY
    private var currentCalendar = Calendar.getInstance()

    enum class CalendarView {
        YEARLY, MONTHLY, WEEKLY, DAILY
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity()).get(CalendarViewModel::class.java)
        customActivityManager = CustomActivityManager(requireContext())

        // Setup UI components first
        setupViewModeButtons()
        setupDateNavigation()

        // Load saved view state
        loadViewState()

        // Setup observers
        observeViewModel()

        // Force initial data load after everything is set up
        lifecycleScope.launch {
            delay(100) // Small delay to ensure view is ready
            loadCalendarData()
        }
    }

    private fun setupViewModeButtons() {
        binding.btnYearlyView.setOnClickListener {
            currentView = CalendarView.YEARLY
            // Keep current year
            updateViewModeButtons()
            updateCalendarDisplay()
            saveViewState()
        }

        binding.btnMonthlyView.setOnClickListener {
            currentView = CalendarView.MONTHLY
            // Keep current month
            updateViewModeButtons()
            updateCalendarDisplay()
            saveViewState()
        }

        binding.btnWeeklyView.setOnClickListener {
            currentView = CalendarView.WEEKLY
            // Reset to current week when switching to weekly view
            currentCalendar = Calendar.getInstance()
            updateViewModeButtons()
            updateCalendarDisplay()
            saveViewState()
        }

        binding.btnDailyView.setOnClickListener {
            currentView = CalendarView.DAILY
            // Reset to current day when switching to daily view
            currentCalendar = Calendar.getInstance()
            updateViewModeButtons()
            updateCalendarDisplay()
            saveViewState()
        }

        // Set monthly as default selected
        binding.btnMonthlyView.isChecked = true
    }

    private fun updateViewModeButtons() {
        binding.btnYearlyView.isChecked = currentView == CalendarView.YEARLY
        binding.btnMonthlyView.isChecked = currentView == CalendarView.MONTHLY
        binding.btnWeeklyView.isChecked = currentView == CalendarView.WEEKLY
        binding.btnDailyView.isChecked = currentView == CalendarView.DAILY
    }

    private fun setupDateNavigation() {
        binding.btnPrevious.setOnClickListener {
            navigatePrevious()
        }

        binding.btnNext.setOnClickListener {
            navigateNext()
        }

        binding.tvCurrentPeriod.setOnClickListener {
            showDatePicker()
        }
    }

    private fun navigatePrevious() {
        when (currentView) {
            CalendarView.YEARLY -> currentCalendar.add(Calendar.YEAR, -1)
            CalendarView.MONTHLY -> currentCalendar.add(Calendar.MONTH, -1)
            CalendarView.WEEKLY -> currentCalendar.add(Calendar.WEEK_OF_YEAR, -1)
            CalendarView.DAILY -> currentCalendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        updateCalendarDisplay()
        loadCalendarData()
        saveViewState()
    }

    private fun navigateNext() {
        when (currentView) {
            CalendarView.YEARLY -> currentCalendar.add(Calendar.YEAR, 1)
            CalendarView.MONTHLY -> currentCalendar.add(Calendar.MONTH, 1)
            CalendarView.WEEKLY -> currentCalendar.add(Calendar.WEEK_OF_YEAR, 1)
            CalendarView.DAILY -> currentCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        updateCalendarDisplay()
        loadCalendarData()
        saveViewState()
    }

    private fun updateCalendarDisplay() {
        // Update period text
        val periodText = when (currentView) {
            CalendarView.YEARLY -> yearFormat.format(currentCalendar.time)
            CalendarView.MONTHLY -> monthFormat.format(currentCalendar.time)
            CalendarView.WEEKLY -> {
val startOfWeek = currentCalendar.clone() as Calendar
startOfWeek.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
val endOfWeek = startOfWeek.clone() as Calendar
endOfWeek.add(Calendar.DAY_OF_WEEK, 6)
"${dateFormat.format(startOfWeek.time)} - ${dateFormat.format(endOfWeek.time)}"
            }

            CalendarView.DAILY -> dateFormat.format(currentCalendar.time)
        }
        binding.tvCurrentPeriod.text = periodText

        // Update calendar grid
        when (currentView) {
            CalendarView.YEARLY -> displayYearlyView()
            CalendarView.MONTHLY -> displayMonthlyView()
            CalendarView.WEEKLY -> displayWeeklyView()
            CalendarView.DAILY -> displayDailyView()
        }
    }

    private fun displayYearlyView() {
        binding.calendarRecyclerView.layoutManager = GridLayoutManager(context, 3)

        val months = mutableListOf<MonthData>()
        val year = currentCalendar.get(Calendar.YEAR)

        for (month in 0..11) {
            val cal = Calendar.getInstance()
            cal.set(year, month, 1)
            months.add(MonthData(cal, emptyList()))
        }

        val adapter = YearlyAdapter(months) { monthData ->
            showMonthDetails(monthData.calendar)
        }
        binding.calendarRecyclerView.adapter = adapter

        // Load activities for each month asynchronously
        lifecycleScope.launch {
            months.forEachIndexed { index, monthData ->
val activities = viewModel.getActivitiesForMonthAsync(monthData.calendar)
months[index] = MonthData(monthData.calendar, activities)
adapter.notifyItemChanged(index)
            }
        }
    }

    private fun displayMonthlyView() {
        binding.calendarRecyclerView.layoutManager = GridLayoutManager(context, 7)

        val days = mutableListOf<DayData>()
        val cal = currentCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)

        // Add empty cells for days before month starts
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        for (i in 0 until firstDayOfWeek) {
            days.add(DayData(null, emptyList()))
        }

        // Add all days of the month
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (day in 1..maxDay) {
            val dayCal = currentCalendar.clone() as Calendar
            dayCal.set(Calendar.DAY_OF_MONTH, day)
            days.add(DayData(dayCal, emptyList()))
        }

        val adapter = MonthlyAdapter(days) { dayData ->
            dayData.calendar?.let { showDayDetails(it) }
        }
        binding.calendarRecyclerView.adapter = adapter

        // Load activities for each day asynchronously
        lifecycleScope.launch {
            days.forEachIndexed { index, dayData ->
dayData.calendar?.let { calendar ->
    val activities = viewModel.getActivitiesForDayAsync(calendar)
    days[index] = DayData(calendar, activities)
    adapter.notifyItemChanged(index)
}
            }
        }
    }

    private fun displayWeeklyView() {
        // Use LinearLayoutManager for vertical layout
        binding.calendarRecyclerView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

        val days = mutableListOf<DayData>()
        val cal = currentCalendar.clone() as Calendar
        // Get the first day of the week (Sunday) properly
        cal.firstDayOfWeek = Calendar.SUNDAY
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        cal.add(Calendar.DAY_OF_MONTH, -(dayOfWeek - Calendar.SUNDAY))

        for (i in 0..6) {
            val dayCal = cal.clone() as Calendar
            days.add(DayData(dayCal, emptyList()))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        val adapter = WeeklyAdapter(days) { dayData ->
            dayData.calendar?.let { showDayDetails(it) }
        }
        binding.calendarRecyclerView.adapter = adapter

        // Load activities for each day asynchronously
        lifecycleScope.launch {
            days.forEachIndexed { index, dayData ->
dayData.calendar?.let { calendar ->
    val activities = viewModel.getActivitiesForDayAsync(calendar)
    days[index] = DayData(calendar, activities)
    adapter.notifyItemChanged(index)
}
            }
        }
    }

    private fun displayDailyView() {
        // Use LinearLayoutManager for vertical layout
        val layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        binding.calendarRecyclerView.layoutManager = layoutManager

        val hours = mutableListOf<HourData>()
        val cal = currentCalendar.clone() as Calendar

        // Create 24 hour cards (12 AM to 11 PM)
        for (hour in 0..23) {
            val hourCal = currentCalendar.clone() as Calendar
            hourCal.set(Calendar.HOUR_OF_DAY, hour)
            hourCal.set(Calendar.MINUTE, 0)
            hourCal.set(Calendar.SECOND, 0)
            hours.add(HourData(hourCal, emptyList())) // Start with empty, will load async
        }

        val adapter = DailyAdapter(hours) { hourData ->
            showHourDetails(hourData.calendar)
        }
        binding.calendarRecyclerView.adapter = adapter

        // Load activities for each hour asynchronously
        lifecycleScope.launch {
            hours.forEachIndexed { index, hourData ->
val activities = viewModel.getDetailedActivitiesForHour(hourData.calendar)
hours[index] = HourData(hourData.calendar, activities)
adapter.notifyItemChanged(index)
            }
        }

        // Auto-scroll to current hour
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.calendarRecyclerView.post {
            layoutManager.scrollToPositionWithOffset(currentHour, 100)
        }
    }

    private fun getActivitiesForDay(calendar: Calendar): List<ActivityCount> {
        // This will be populated from the database
        return viewModel.getActivitiesForDay(calendar)
    }

    private fun getActivitiesForMonth(calendar: Calendar): List<ActivityCount> {
        // This will be populated from the database
        return viewModel.getActivitiesForMonth(calendar)
    }

    private fun getActivitiesForHour(calendar: Calendar): List<ActivityCount> {
        // This will be populated from the database
        return viewModel.getActivitiesForHour(calendar)
    }

    private fun showHourDetails(calendar: Calendar) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_calendar_day_details)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val window = dialog.window
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setGravity(Gravity.CENTER)

        val cardView = dialog.findViewById<MaterialCardView>(R.id.cardContainer)
        cardView.setCardBackgroundColor(
            ContextCompat.getColor(
requireContext(),
R.color.dialog_background
            )
        )
        cardView.strokeColor = ContextCompat.getColor(requireContext(), R.color.neon_green)
        cardView.strokeWidth = 2

        val tvDate = dialog.findViewById<TextView>(R.id.tvDate)
        val layoutActivities = dialog.findViewById<LinearLayout>(R.id.layoutActivities)
        val tvTotalStats = dialog.findViewById<TextView>(R.id.tvTotalStats)
        val btnClose = dialog.findViewById<MaterialButton>(R.id.btnClose)
        val btnDeleteAll = dialog.findViewById<MaterialButton>(R.id.btnDeleteAll)

        val hourFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val startHour = calendar.clone() as Calendar
        val endHour = calendar.clone() as Calendar
        endHour.add(Calendar.HOUR_OF_DAY, 1)
        endHour.add(Calendar.MINUTE, -1)
        tvDate.text = "${dateFormat.format(calendar.time)}  ${hourFmt.format(startHour.time)} - ${
            hourFmt.format(endHour.time)
        }"
        tvDate.setTextColor(ContextCompat.getColor(requireContext(), R.color.neon_green))

        fun refreshHourDetails() {
            layoutActivities.removeAllViews()
            lifecycleScope.launch {
                val activities = viewModel.getDetailedActivitiesForHourDetailed(calendar)
                val allLogs = viewModel.getActivityLogsForHour(calendar)

                if (activities.isEmpty()) {
                    val noDataText = TextView(context).apply {
                        text = "No activities recorded"
                        textSize = 16f
                        setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.text_secondary
                            )
                        )
                        gravity = Gravity.CENTER
                        setPadding(0, 16, 0, 16)
                    }
                    layoutActivities.addView(noDataText)
                    btnDeleteAll.visibility = View.GONE
                } else {
                    var totalCount = 0
                    activities.forEach { activity ->
                        val activityView =
                            layoutInflater.inflate(R.layout.item_calendar_activity_detail, null)

                        val tvActivityName =
                            activityView.findViewById<TextView>(R.id.tvActivityName)
                        val tvCount = activityView.findViewById<TextView>(R.id.tvCount)
                        val tvTimes = activityView.findViewById<TextView>(R.id.tvTimes)
                        val tvStats = activityView.findViewById<TextView>(R.id.tvStats)
                        val btnDeleteActivity =
                            activityView.findViewById<ImageButton>(R.id.btnDeleteActivity)
                        val btnEditActivity =
                            activityView.findViewById<ImageButton>(R.id.btnEditActivity)

                        tvActivityName.text = activity.name
                        tvActivityName.setTextColor(activity.color)
                        tvCount.text = "Count: ${activity.count}"
                        tvTimes.text = activity.times.joinToString("\n")

                        if (activity.gaps.isNotEmpty()) {
                            val avgGap = activity.gaps.average()
                            val minGap = activity.gaps.minOrNull() ?: 0.0
                            val maxGap = activity.gaps.maxOrNull() ?: 0.0
                            tvStats.text = "Avg gap: ${formatDuration(avgGap.toLong())}\n" +
                                    "Min gap: ${formatDuration(minGap.toLong())}\n" +
                                    "Max gap: ${formatDuration(maxGap.toLong())}"
                        } else {
                            tvStats.visibility = View.GONE
                        }

                        // Handle individual activity deletion
                        btnDeleteActivity.setOnClickListener {
                            showDeleteActivityConfirmation(activity.name) {
                                lifecycleScope.launch {
                                    val logsToDelete = allLogs.filter { log ->
                                        when (activity.name) {
                                            "Joints" -> log.type == ActivityType.JOINT
                                            "Cones" -> log.type == ActivityType.CONE
                                            "Bowls" -> log.type == ActivityType.BOWL
                                            "Cigarettes" -> log.type == ActivityType.CIGARETTE
                                            else -> log.customActivityName == activity.name
                                        }
                                    }
                                    val smokersMap = viewModel.getSmokersMap()
                                    logsToDelete.forEach { log ->
                                        viewModel.deleteLogWithCloudSync(log, smokersMap[log.smokerId])
                                    }
                                    refreshHourDetails()
                                }
                            }
                        }

                        // Handle edit button click
                        btnEditActivity.setOnClickListener {
                            val activityLogs = allLogs.filter { log ->
                                when (activity.name) {
                                    "Joints" -> log.type == ActivityType.JOINT
                                    "Cones" -> log.type == ActivityType.CONE
                                    "Bowls" -> log.type == ActivityType.BOWL
                                    "Cigarettes" -> log.type == ActivityType.CIGARETTE
                                    else -> log.customActivityName == activity.name
                                }
                            }
                            showEditActivities(activity.name, activityLogs, dialog) {
                                refreshHourDetails()
                            }
                        }

                        layoutActivities.addView(activityView)
                        totalCount += activity.count
                    }

                    tvTotalStats.text = "Total: $totalCount activities"
                    tvTotalStats.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.text_primary
                        )
                    )
                    btnDeleteAll.visibility = View.VISIBLE

                    // Handle delete all button
                    btnDeleteAll.setOnClickListener {
                        showDeleteAllConfirmation("hour") {
                            lifecycleScope.launch {
                                val smokersMap = viewModel.getSmokersMap()
                                allLogs.forEach { log ->
                                    viewModel.deleteLogWithCloudSync(log, smokersMap[log.smokerId])
                                }
                                refreshHourDetails()
                            }
                        }
                    }
                }
            }
        }

        refreshHourDetails()

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showMonthDetails(calendar: Calendar) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_calendar_day_details)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Set dialog width and position
        val window = dialog.window
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setGravity(Gravity.CENTER)

        // Get views
        val cardView = dialog.findViewById<MaterialCardView>(R.id.cardContainer)
        cardView.setCardBackgroundColor(
            ContextCompat.getColor(
requireContext(),
R.color.dialog_background
            )
        )
        cardView.strokeColor = ContextCompat.getColor(requireContext(), R.color.neon_green)
        cardView.strokeWidth = 2

        val tvDate = dialog.findViewById<TextView>(R.id.tvDate)
        val layoutActivities = dialog.findViewById<LinearLayout>(R.id.layoutActivities)
        val tvTotalStats = dialog.findViewById<TextView>(R.id.tvTotalStats)
        val btnClose = dialog.findViewById<MaterialButton>(R.id.btnClose)
        val btnDeleteAll = dialog.findViewById<MaterialButton>(R.id.btnDeleteAll)

        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        tvDate.text = monthFormat.format(calendar.time)
        tvDate.setTextColor(ContextCompat.getColor(requireContext(), R.color.neon_green))

        fun refreshMonthDetails() {
            layoutActivities.removeAllViews()
            lifecycleScope.launch {
                val activities = viewModel.getDetailedActivitiesForMonthDetailed(calendar)
                val allLogs = viewModel.getActivityLogsForMonth(calendar)

                if (activities.isEmpty()) {
                    val noDataText = TextView(context).apply {
                        text = "No activities this month"
                        textSize = 14f
                        setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.text_secondary
                            )
                        )
                        gravity = Gravity.CENTER
                        setPadding(16, 32, 16, 32)
                    }
                    layoutActivities.addView(noDataText)
                    tvTotalStats.visibility = View.GONE
                    btnDeleteAll.visibility = View.GONE
                } else {
                    // Display detailed activities using the same layout and font sizes as daily popup
                    var total = 0
                    activities.forEach { activity ->
                        val activityView =
                            layoutInflater.inflate(R.layout.item_calendar_activity_detail, null)

                        val tvActivityName =
                            activityView.findViewById<TextView>(R.id.tvActivityName)
                        val tvCount = activityView.findViewById<TextView>(R.id.tvCount)
                        val tvTimes = activityView.findViewById<TextView>(R.id.tvTimes)
                        val tvStats = activityView.findViewById<TextView>(R.id.tvStats)
                        val btnDeleteActivity =
                            activityView.findViewById<ImageButton>(R.id.btnDeleteActivity)
                        val btnEditActivity =
                            activityView.findViewById<ImageButton>(R.id.btnEditActivity)

                        tvActivityName.text = activity.name
                        tvActivityName.setTextColor(activity.color)
                        tvCount.text = "Count: ${activity.count}"
                        tvTimes.text = activity.times.joinToString("\n")

                        if (activity.gaps.isNotEmpty()) {
                            val avgGap = activity.gaps.average()
                            val minGap = activity.gaps.minOrNull() ?: 0.0
                            val maxGap = activity.gaps.maxOrNull() ?: 0.0
                            tvStats.text = "Avg gap: ${formatDuration(avgGap.toLong())}\n" +
                                    "Min gap: ${formatDuration(minGap.toLong())}\n" +
                                    "Max gap: ${formatDuration(maxGap.toLong())}"
                        } else {
                            tvStats.visibility = View.GONE
                        }

                        // Handle individual activity deletion
                        btnDeleteActivity.setOnClickListener {
                            showDeleteActivityConfirmation(activity.name) {
                                lifecycleScope.launch {
                                    val logsToDelete = allLogs.filter { log ->
                                        when (activity.name) {
                                            "Joints" -> log.type == ActivityType.JOINT
                                            "Cones" -> log.type == ActivityType.CONE
                                            "Bowls" -> log.type == ActivityType.BOWL
                                            "Cigarettes" -> log.type == ActivityType.CIGARETTE
                                            else -> log.customActivityName == activity.name
                                        }
                                    }
                                    val smokersMap = viewModel.getSmokersMap()
                                    logsToDelete.forEach { log ->
                                        viewModel.deleteLogWithCloudSync(log, smokersMap[log.smokerId])
                                    }
                                    refreshMonthDetails()
                                }
                            }
                        }

                        // Handle edit button click
                        btnEditActivity.setOnClickListener {
                            val activityLogs = allLogs.filter { log ->
                                when (activity.name) {
                                    "Joints" -> log.type == ActivityType.JOINT
                                    "Cones" -> log.type == ActivityType.CONE
                                    "Bowls" -> log.type == ActivityType.BOWL
                                    "Cigarettes" -> log.type == ActivityType.CIGARETTE
                                    else -> log.customActivityName == activity.name
                                }
                            }
                            showEditActivities(activity.name, activityLogs, dialog) {
                                refreshMonthDetails()
                            }
                        }

                        layoutActivities.addView(activityView)
                        total += activity.count
                    }
                    // Show totals and averages similar to daily popup
                    val calForDays = calendar.clone() as Calendar
                    val daysInMonth = calForDays.getActualMaximum(Calendar.DAY_OF_MONTH).toDouble()
                    val avgPerHour = total / (daysInMonth * 24.0)
                    tvTotalStats.text =
                        "Total: $total activities\nAverage rate: ${fmt.format(avgPerHour)}/hour"
                    tvTotalStats.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.text_primary
                        )
                    )
                    btnDeleteAll.visibility = View.VISIBLE

                    // Handle delete all button
                    btnDeleteAll.setOnClickListener {
                        showDeleteAllConfirmation("month") {
                            lifecycleScope.launch {
                                val smokersMap = viewModel.getSmokersMap()
                                allLogs.forEach { log ->
                                    viewModel.deleteLogWithCloudSync(log, smokersMap[log.smokerId])
                                }
                                refreshMonthDetails()
                            }
                        }
                    }
                }
            }

            refreshMonthDetails()

            btnClose.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }
    }

    private fun showDayDetails(calendar: Calendar) {
            val dialog = Dialog(requireContext())
            dialog.setContentView(R.layout.dialog_calendar_day_details)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // Set dialog width and position
            val window = dialog.window
            window?.setLayout(
(resources.displayMetrics.widthPixels * 0.9).toInt(),
WindowManager.LayoutParams.WRAP_CONTENT
            )
            window?.setGravity(Gravity.CENTER)

            val cardView = dialog.findViewById<MaterialCardView>(R.id.cardContainer)
            cardView.setCardBackgroundColor(
ContextCompat.getColor(
    requireContext(),
    R.color.dialog_background
)
            )
            cardView.strokeColor = ContextCompat.getColor(requireContext(), R.color.neon_green)
            cardView.strokeWidth = 2

            val tvDate = dialog.findViewById<TextView>(R.id.tvDate)
            val layoutActivities = dialog.findViewById<LinearLayout>(R.id.layoutActivities)
            val tvTotalStats = dialog.findViewById<TextView>(R.id.tvTotalStats)
            val btnClose = dialog.findViewById<MaterialButton>(R.id.btnClose)
            val btnDeleteAll = dialog.findViewById<MaterialButton>(R.id.btnDeleteAll)

            tvDate.text = dateFormat.format(calendar.time)
            tvDate.setTextColor(ContextCompat.getColor(requireContext(), R.color.neon_green))

            fun refreshDayDetails() {
                layoutActivities.removeAllViews()
                lifecycleScope.launch {
                    val activities = viewModel.getDetailedActivitiesForDay(calendar)
                    val allLogs = viewModel.getActivityLogsForDay(calendar)

                    if (activities.isEmpty()) {
                        val noDataText = TextView(context).apply {
                            text = "No activities recorded"
                            textSize = 16f
                            setTextColor(
                                ContextCompat.getColor(
                                    requireContext(),
                                    R.color.text_secondary
                                )
                            )
                            gravity = Gravity.CENTER
                            setPadding(0, 16, 0, 16)
                        }
                        layoutActivities.addView(noDataText)
                        btnDeleteAll.visibility = View.GONE
                    } else {
                        var totalCount = 0
                        activities.forEach { activity ->
                            val activityView =
                                layoutInflater.inflate(R.layout.item_calendar_activity_detail, null)

                            val tvActivityName =
                                activityView.findViewById<TextView>(R.id.tvActivityName)
                            val tvCount = activityView.findViewById<TextView>(R.id.tvCount)
                            val tvTimes = activityView.findViewById<TextView>(R.id.tvTimes)
                            val tvStats = activityView.findViewById<TextView>(R.id.tvStats)
                            val btnDeleteActivity =
                                activityView.findViewById<ImageButton>(R.id.btnDeleteActivity)
                            val btnEditActivity =
                                activityView.findViewById<ImageButton>(R.id.btnEditActivity)

                            tvActivityName.text = activity.name
                            tvActivityName.setTextColor(activity.color)
                            tvCount.text = "Count: ${activity.count}"
                            tvTimes.text = activity.times.joinToString("\n")

                            if (activity.gaps.isNotEmpty()) {
                                val avgGap = activity.gaps.average()
                                val minGap = activity.gaps.minOrNull() ?: 0.0
                                val maxGap = activity.gaps.maxOrNull() ?: 0.0
                                tvStats.text = "Avg gap: ${formatDuration(avgGap.toLong())}\n" +
                                        "Min gap: ${formatDuration(minGap.toLong())}\n" +
                                        "Max gap: ${formatDuration(maxGap.toLong())}"
                            } else {
                                tvStats.visibility = View.GONE
                            }

                            // Handle individual activity deletion
                            btnDeleteActivity.setOnClickListener {
                                showDeleteActivityConfirmation(activity.name) {
                                    lifecycleScope.launch {
                                        val logsToDelete = allLogs.filter { log ->
                                            when (activity.name) {
                                                "Joints" -> log.type == ActivityType.JOINT
                                                "Cones" -> log.type == ActivityType.CONE
                                                "Bowls" -> log.type == ActivityType.BOWL
                                                "Cigarettes" -> log.type == ActivityType.CIGARETTE
                                                else -> log.customActivityName == activity.name
                                            }
                                        }
                                        val smokersMap = viewModel.getSmokersMap()
                                        logsToDelete.forEach { log ->
                                            viewModel.deleteLogWithCloudSync(log, smokersMap[log.smokerId])
                                        }
                                        refreshDayDetails()
                                    }
                                }
                            }

                            // Handle edit button click
                            btnEditActivity.setOnClickListener {
                                val activityLogs = allLogs.filter { log ->
                                    when (activity.name) {
                                        "Joints" -> log.type == ActivityType.JOINT
                                        "Cones" -> log.type == ActivityType.CONE
                                        "Bowls" -> log.type == ActivityType.BOWL
                                        "Cigarettes" -> log.type == ActivityType.CIGARETTE
                                        else -> log.customActivityName == activity.name
                                    }
                                }
                                showEditActivities(activity.name, activityLogs, dialog) {
                                    refreshDayDetails()
                                }
                            }

                            layoutActivities.addView(activityView)
                            totalCount += activity.count
                        }

                        // Calculate and show average rate
                        val dayStart = calendar.clone() as Calendar
                        dayStart.set(Calendar.HOUR_OF_DAY, 0)
                        dayStart.set(Calendar.MINUTE, 0)
                        val dayEnd = calendar.clone() as Calendar
                        dayEnd.set(Calendar.HOUR_OF_DAY, 23)
                        dayEnd.set(Calendar.MINUTE, 59)

                        val hoursInDay = 24.0
                        val avgRate = totalCount / hoursInDay
                        tvTotalStats.text =
                            "Total: $totalCount activities\nAverage rate: ${fmt.format(avgRate)}/hour"
                        tvTotalStats.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.text_primary
                            )
                        )
                        btnDeleteAll.visibility = View.VISIBLE

                        // Handle delete all button
                        btnDeleteAll.setOnClickListener {
                            showDeleteAllConfirmation("day") {
                                lifecycleScope.launch {
                                    val smokersMap = viewModel.getSmokersMap()
                                    allLogs.forEach { log ->
                                        viewModel.deleteLogWithCloudSync(log, smokersMap[log.smokerId])
                                    }
                                    refreshDayDetails()
                                }
                            }
                        }
                    }
                }
            }

            refreshDayDetails()

            btnClose.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }

    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    private fun showDatePicker() {
        // Implementation for date picker dialog
        // This will allow users to jump to a specific date
    }

    private fun showEditActivities(
        activityName: String,
        activityLogs: List<ActivityLog>,
        parentDialog: Dialog,
        onDelete: () -> Unit
    ) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_edit_activities)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val window = dialog.window
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setGravity(Gravity.CENTER)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvTitle)
        val layoutActivities = dialog.findViewById<LinearLayout>(R.id.layoutActivities)
        val btnClose = dialog.findViewById<MaterialButton>(R.id.btnClose)

        tvTitle.text = "Edit $activityName"

        refreshEditDialog(activityLogs, layoutActivities, dialog, parentDialog, onDelete)

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun refreshEditDialog(
        activityLogs: List<ActivityLog>,
        layoutActivities: LinearLayout,
        editDialog: Dialog,
        parentDialog: Dialog,
        onParentRefresh: () -> Unit
    ) {
        layoutActivities.removeAllViews()

        lifecycleScope.launch {
            val smokersById = viewModel.getSmokersMap()
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

            if (activityLogs.isEmpty()) {
                val noDataText = TextView(context).apply {
                    text = "No activities"
                    textSize = 14f
                    setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.text_secondary
                        )
                    )
                    gravity = Gravity.CENTER
                    setPadding(16, 32, 16, 32)
                }
                layoutActivities.addView(noDataText)
            } else {
                activityLogs.forEach { log ->
                    val itemView =
                        layoutInflater.inflate(R.layout.item_individual_activity, null)
                    val tvActivityTime =
                        itemView.findViewById<TextView>(R.id.tvActivityTime)
                    val btnDeleteIndividual =
                        itemView.findViewById<ImageButton>(R.id.btnDeleteIndividual)

                    val smokerName = smokersById[log.effectiveConsumerId]?.name ?: "Unknown"
                    tvActivityTime.text =
                        "${timeFormat.format(Date(log.timestamp))} — $smokerName"

                    btnDeleteIndividual.setOnClickListener {
                        showDeleteIndividualConfirmation {
                            lifecycleScope.launch {
                                val smokersMap = viewModel.getSmokersMap()
                                viewModel.deleteLogWithCloudSync(log, smokersMap[log.smokerId])
                                // Refresh both dialogs
                                val updatedLogs = activityLogs.filter { it.id != log.id }
                                refreshEditDialog(
                                    updatedLogs,
                                    layoutActivities,
                                    editDialog,
                                    parentDialog,
                                    onParentRefresh
                                )
                                onParentRefresh()
                            }
                        }
                    }

                    layoutActivities.addView(itemView)
                }
            }
        }
    }

    private fun showDeleteActivityConfirmation(
        activityName: String,
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_confirmation)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val window = dialog.window
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setGravity(Gravity.CENTER)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = dialog.findViewById<TextView>(R.id.tvMessage)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancel)
        val btnConfirm = dialog.findViewById<MaterialButton>(R.id.btnConfirm)

        tvTitle.text = "Delete $activityName"
        tvMessage.text =
            "Are you sure you want to delete all $activityName activities from this time period?"

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteAllConfirmation(period: String, onConfirm: () -> Unit) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_confirmation)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val window = dialog.window
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setGravity(Gravity.CENTER)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = dialog.findViewById<TextView>(R.id.tvMessage)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancel)
        val btnConfirm = dialog.findViewById<MaterialButton>(R.id.btnConfirm)

        tvTitle.text = "Delete All Activities"
        tvMessage.text = "Are you sure you want to delete all activities from this $period?"

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteIndividualConfirmation(onConfirm: () -> Unit) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_confirmation)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val window = dialog.window
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setGravity(Gravity.CENTER)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = dialog.findViewById<TextView>(R.id.tvMessage)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancel)
        val btnConfirm = dialog.findViewById<MaterialButton>(R.id.btnConfirm)

        tvTitle.text = "Delete Activity"
        tvMessage.text = "Are you sure you want to delete this activity?"

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadCalendarData() {
        lifecycleScope.launch {
            viewModel.loadActivitiesForPeriod(currentCalendar, currentView)
        }
    }

    private fun observeViewModel() {
        // Observe calendar data changes
        viewModel.calendarData.observe(viewLifecycleOwner) { data ->
            // Update calendar display when data changes
            updateCalendarDisplay()
        }

        // Observe database changes for real-time updates
        viewModel.allLogs.observe(viewLifecycleOwner) { logs ->
            // Reload calendar data when activities change
            loadCalendarData()

            // For daily view, also refresh the display to show new activities in correct hour
            if (currentView == CalendarView.DAILY) {
                updateCalendarDisplay()
            }
        }
    }

    private fun saveViewState() {
        val prefs = requireContext().getSharedPreferences(
            "calendar_prefs",
            android.content.Context.MODE_PRIVATE
        )
        prefs.edit().apply {
            putString("view_mode", currentView.name)
            putLong("current_date", currentCalendar.timeInMillis)
            apply()
        }
    }

    private fun loadViewState() {
        val prefs = requireContext().getSharedPreferences(
            "calendar_prefs",
            android.content.Context.MODE_PRIVATE
        )

        // Load saved view mode
        val savedViewMode = prefs.getString("view_mode", CalendarView.MONTHLY.name)
        currentView = try {
            CalendarView.valueOf(savedViewMode ?: CalendarView.MONTHLY.name)
        } catch (e: Exception) {
            CalendarView.MONTHLY
        }

        // Load saved date (default to current date)
        val savedDate = prefs.getLong("current_date", System.currentTimeMillis())
        currentCalendar.timeInMillis = savedDate

        // Update button states and display
        updateViewModeButtons()
        updateCalendarDisplay()
    }

    override fun onResume() {
        super.onResume()
        // Reload data when fragment becomes visible
        loadCalendarData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }



    // Adapter classes
    inner class MonthlyAdapter(
        private val days: MutableList<DayData>,
        private val onDayClick: (DayData) -> Unit
    ) : RecyclerView.Adapter<MonthlyAdapter.DayViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_calendar_day, parent, false)
            return DayViewHolder(view)
        }

        override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
            holder.bind(days[position])
        }

        override fun getItemCount() = days.size

        inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val cardView = itemView.findViewById<MaterialCardView>(R.id.cardDay)
    private val tvDay = itemView.findViewById<TextView>(R.id.tvDay)
    private val layoutActivities =
        itemView.findViewById<LinearLayout>(R.id.layoutActivities)

    fun bind(dayData: DayData) {
        dayData.calendar?.let { cal ->
            tvDay.text = cal.get(Calendar.DAY_OF_MONTH).toString()

            // Check if today
            val today = Calendar.getInstance()
            if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            ) {
                cardView.strokeColor =
                    ContextCompat.getColor(itemView.context, R.color.neon_green)
                cardView.strokeWidth = 2
            } else {
                cardView.strokeWidth = 0
            }

            layoutActivities.removeAllViews()
            dayData.activities.forEach { activity ->
                val activityText = TextView(itemView.context).apply {
                    text = "${activity.count} ${activity.name}"
                    textSize = 9f
                    setTextColor(activity.color)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                layoutActivities.addView(activityText)
            }

            cardView.setOnClickListener {
                onDayClick(dayData)
            }
        } ?: run {
            tvDay.text = ""
            cardView.setOnClickListener(null)
            cardView.isClickable = false
        }
    }
}
            }

    inner class WeeklyAdapter(
private val days: MutableList<DayData>,
private val onDayClick: (DayData) -> Unit
            ) : RecyclerView.Adapter<WeeklyAdapter.DayViewHolder>() {

override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
    val view = LayoutInflater.from(parent.context)
        .inflate(R.layout.item_calendar_day_weekly, parent, false)
    return DayViewHolder(view)
}

override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
    holder.bind(days[position])
}

override fun getItemCount() = days.size

inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val cardView = itemView.findViewById<MaterialCardView>(R.id.cardDay)
    private val tvDayName = itemView.findViewById<TextView>(R.id.tvDayName)
    private val tvDayNumber = itemView.findViewById<TextView>(R.id.tvDayNumber)
    private val layoutActivities =
        itemView.findViewById<LinearLayout>(R.id.layoutActivities)
    private val scrollView = itemView.findViewById<ScrollView>(R.id.scrollView)

    fun bind(dayData: DayData) {
        dayData.calendar?.let { cal ->
            tvDayName.text = dayOfWeekFormat.format(cal.time)
            tvDayNumber.text = cal.get(Calendar.DAY_OF_MONTH).toString()

            layoutActivities.removeAllViews()
            dayData.activities.forEach { activity ->
                val activityText = TextView(itemView.context).apply {
                    text = "${activity.count} ${activity.name}"
                    textSize = 12f
                    setTextColor(activity.color)
                    setPadding(8, 4, 8, 4)
                }
                layoutActivities.addView(activityText)
            }

            // Add highlight for today
            val today = Calendar.getInstance()
            if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            ) {
                cardView.strokeColor =
                    ContextCompat.getColor(itemView.context, R.color.neon_green)
                cardView.strokeWidth = 2
            } else {
                cardView.strokeWidth = 0
            }

            // Ensure taps anywhere (including inside the ScrollView) open the popup
            layoutActivities.isClickable = true
            layoutActivities.isFocusable = false
            layoutActivities.setOnClickListener { onDayClick(dayData) }
            scrollView?.isClickable = true
            scrollView?.isFocusable = false
            scrollView?.setOnClickListener { onDayClick(dayData) }

            cardView.setOnClickListener {
                onDayClick(dayData)
            }
        }
    }
}
            }

    inner class YearlyAdapter(
private val months: MutableList<MonthData>,
private val onMonthClick: (MonthData) -> Unit
            ) : RecyclerView.Adapter<YearlyAdapter.MonthViewHolder>() {

private val monthNames = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
    val view = LayoutInflater.from(parent.context)
        .inflate(R.layout.item_calendar_month, parent, false)
    return MonthViewHolder(view)
}

override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
    holder.bind(months[position])
}

override fun getItemCount() = months.size

inner class MonthViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val cardView = itemView.findViewById<MaterialCardView>(R.id.cardMonth)
    private val tvMonthName = itemView.findViewById<TextView>(R.id.tvMonthName)
    private val layoutActivities =
        itemView.findViewById<LinearLayout>(R.id.layoutActivities)
    private val tvTotal = itemView.findViewById<TextView>(R.id.tvTotal)
    private val scrollView = itemView.findViewById<ScrollView>(R.id.scrollView)

    fun bind(monthData: MonthData) {
        val month = monthData.calendar.get(Calendar.MONTH)
        tvMonthName.text = monthNames[month]
        tvMonthName.setTextColor(
            ContextCompat.getColor(
                itemView.context,
                R.color.neon_green
            )
        )

        layoutActivities.removeAllViews()
        var totalCount = 0

        monthData.activities.forEach { activity ->
            if (activity.count > 0) {
                val activityText = TextView(itemView.context).apply {
                    // For stash activities in yearly view, remove the cost in brackets
                    val displayName =
                        if (activity.name.contains("Stash +") && activity.name.contains(
                                "($"
                            )
                        ) {
                            // Extract just the "My Stash +X.XXg" or "Their Stash +X.XXg" part without the cost
                            activity.name.substringBefore(" ($")
                        } else {
                            activity.name
                        }
                    text = "${activity.count} $displayName"
                    textSize = 12f
                    setTextColor(activity.color)
                    setPadding(8, 4, 8, 4)
                }
                layoutActivities.addView(activityText)

                totalCount += activity.count
            }
        }

        if (totalCount > 0) {
            tvTotal.text = "Total: $totalCount"
            tvTotal.visibility = View.VISIBLE
        } else {
            tvTotal.visibility = View.GONE
        }

        // Add highlight for current month
        val today = Calendar.getInstance()
        if (monthData.calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            monthData.calendar.get(Calendar.MONTH) == today.get(Calendar.MONTH)
        ) {
            cardView.strokeColor =
                ContextCompat.getColor(itemView.context, R.color.neon_green)
            cardView.strokeWidth = 2
        } else {
            cardView.strokeWidth = 0
        }

        // Ensure taps anywhere (including inside the ScrollView) open the popup
        layoutActivities.isClickable = true
        layoutActivities.isFocusable = false
        layoutActivities.setOnClickListener { onMonthClick(monthData) }
        scrollView?.isClickable = true
        scrollView?.isFocusable = false
        scrollView?.setOnClickListener { onMonthClick(monthData) }

        cardView.setOnClickListener {
            onMonthClick(monthData)
        }
    }
}
            }

    inner class DailyAdapter(
private val hours: MutableList<HourData>,
private val onHourClick: (HourData) -> Unit
            ) : RecyclerView.Adapter<DailyAdapter.HourViewHolder>() {

private val hourFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HourViewHolder {
    val view = LayoutInflater.from(parent.context)
        .inflate(R.layout.item_calendar_hour, parent, false)
    return HourViewHolder(view)
}

override fun onBindViewHolder(holder: HourViewHolder, position: Int) {
    holder.bind(hours[position])
}

override fun getItemCount() = hours.size

inner class HourViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val cardView = itemView.findViewById<MaterialCardView>(R.id.cardHour)
    private val tvHourRange = itemView.findViewById<TextView>(R.id.tvHourRange)
    private val layoutActivities =
        itemView.findViewById<LinearLayout>(R.id.layoutActivities)

    fun bind(hourData: HourData) {
        val startHour = hourData.calendar.clone() as Calendar
        val endHour = hourData.calendar.clone() as Calendar
        endHour.add(Calendar.HOUR_OF_DAY, 1)
        endHour.add(Calendar.MINUTE, -1)

        tvHourRange.text =
            "${hourFormat.format(startHour.time)} - ${hourFormat.format(endHour.time)}"
        tvHourRange.setTextColor(
            ContextCompat.getColor(
                itemView.context,
                R.color.neon_green
            )
        )

        // Highlight current hour
        val now = Calendar.getInstance()
        if (hourData.calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            hourData.calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
            hourData.calendar.get(Calendar.HOUR_OF_DAY) == now.get(Calendar.HOUR_OF_DAY)
        ) {
            cardView.strokeColor =
                ContextCompat.getColor(itemView.context, R.color.neon_green)
            cardView.strokeWidth = 2
        } else {
            cardView.strokeWidth = 0
        }

        layoutActivities.removeAllViews()
        hourData.activities.forEach { activity ->
            val activityText = TextView(itemView.context).apply {
                text = "${activity.count} ${activity.name}"
                textSize = 11f
                setTextColor(activity.color)
                setPadding(8, 4, 8, 4)
            }
            layoutActivities.addView(activityText)
        }

        // Make child views non-clickable to fix click detection
        layoutActivities.isClickable = false
        layoutActivities.isFocusable = false

        cardView.setOnClickListener {
            onHourClick(hourData)
        }
    }
}
            }
        }






