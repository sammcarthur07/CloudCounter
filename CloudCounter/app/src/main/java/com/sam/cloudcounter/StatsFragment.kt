package com.sam.cloudcounter

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.sam.cloudcounter.databinding.FragmentStatsBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.Toast
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: StatsViewModel
    private val fmt = DecimalFormat("#.##")

    // keep current smoker list so we can render summary
    private var allSmokersCache: List<Smoker> = emptyList()

    // for debounce when user toggles selection rapidly
    private var smokerSelectionDebounceJob: Job? = null

    // internal mutable set backing the selection chips
    private val selectedSmokersInternal = mutableSetOf<Long>()

    // Track last non-custom time chip to restore on cancel
    private var lastNonCustomTimeChipId: Int = R.id.chipAllTime
    // Track selected sessions count to update chip label
    private var lastSelectedSessionsCount: Int = 0
    // Guard to prevent double-opening the custom picker dialog
    private var isCustomDialogShowing: Boolean = false
    
    // Broadcast receiver for custom activity changes
    private val customActivityChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MainActivity.ACTION_CUSTOM_ACTIVITIES_CHANGED) {
                Log.d("StatsFragment", "📡 Received custom activities changed broadcast")
                setupCategoryChips()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity()).get(StatsViewModel::class.java)
        
        // Apply window insets to handle navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Add bottom padding to the content layout to account for navigation bar
            binding.statsContentLayout.setPadding(
                binding.statsContentLayout.paddingLeft,
                binding.statsContentLayout.paddingTop,
                binding.statsContentLayout.paddingRight,
                systemBars.bottom + (16 * resources.displayMetrics.density).toInt()
            )
            insets
        }
        
        // Register broadcast receiver for custom activity changes
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            customActivityChangeReceiver,
            IntentFilter(MainActivity.ACTION_CUSTOM_ACTIVITIES_CHANGED)
        )

        // seed internal selection so UI reflects current model state
        viewModel.selectedSmokerIds.value?.let { selectedSmokersInternal.addAll(it) }

        // observe selected smokers to update summary text
        viewModel.selectedSmokerIds.observe(viewLifecycleOwner) { ids ->
            updateSelectionSummary(ids)
            Log.d("StatsFragment", "Now showing stats for smoker IDs=$ids")
        }

        // time period chips (includes Custom)
        binding.chipGroupTimePeriod.setOnCheckedStateChangeListener { group, checked ->
            if (checked.isEmpty()) return@setOnCheckedStateChangeListener
            val id = checked.first()
            if (id == R.id.chipCustom) {
                // Enter custom session selection mode
                // Keep Custom visually selected for now, but don't alter lastNonCustomTimeChipId
                showCustomSessionPickerDialog(
                    onDone = { selectedRanges ->
                        if (selectedRanges.isEmpty()) {
                            // Treat as cancel
                            viewModel.setUseCustomSessions(false)
                            binding.chipGroupTimePeriod.check(lastNonCustomTimeChipId)
                            if (lastSelectedSessionsCount == 0) updateCustomChipLabel(reset = true)
                        } else {
                            // Apply custom mode and update chip text
                            viewModel.setCustomSessions(selectedRanges)
                            viewModel.setUseCustomSessions(true)
                            lastSelectedSessionsCount = selectedRanges.size
                            updateCustomChipLabel()
                        }
                    },
                    onCancel = {
                        // Revert to last non-custom selection
                        viewModel.setUseCustomSessions(false)
                        binding.chipGroupTimePeriod.check(lastNonCustomTimeChipId)
                        // Reset Custom chip label if no selections
                        if (lastSelectedSessionsCount == 0) updateCustomChipLabel(reset = true)
                    }
                )
            } else {
                // Record non-custom selection and disable custom mode
                lastNonCustomTimeChipId = id
                viewModel.setUseCustomSessions(false)
                val period = when (id) {
                    R.id.chipMinutely     -> TimePeriod.MINUTELY
                    R.id.chipHourly       -> TimePeriod.HOURLY
                    R.id.chipDaily        -> TimePeriod.DAILY
                    R.id.chipWeekly       -> TimePeriod.WEEKLY
                    R.id.chipFortnightly  -> TimePeriod.FORTNIGHTLY
                    R.id.chipMonthly      -> TimePeriod.MONTHLY
                    R.id.chipYearly       -> TimePeriod.YEARLY
                    R.id.chipAllTime      -> TimePeriod.ALL_TIME
                    else                  -> TimePeriod.ALL_TIME
                }
                viewModel.setTimePeriod(period)
            }
        }

        // Allow tapping the Custom chip again to reopen and edit selection
        val customChip = binding.chipGroupTimePeriod.findViewById<Chip>(R.id.chipCustom)
        customChip?.setOnClickListener {
            // Only open on tap if Custom is already the selected chip.
            // If switching from another chip to Custom, let the ChipGroup listener handle it.
            val alreadyChecked = binding.chipGroupTimePeriod.checkedChipId == R.id.chipCustom
            if (!alreadyChecked) return@setOnClickListener

            showCustomSessionPickerDialog(
                onDone = { selectedRanges ->
                    if (selectedRanges.isEmpty()) {
                        // Treat as cancel if nothing selected
                        viewModel.setUseCustomSessions(false)
                        if (binding.chipGroupTimePeriod.checkedChipId == R.id.chipCustom) {
                            binding.chipGroupTimePeriod.check(lastNonCustomTimeChipId)
                        }
                        if (lastSelectedSessionsCount == 0) updateCustomChipLabel(reset = true)
                    } else {
                        viewModel.setCustomSessions(selectedRanges)
                        viewModel.setUseCustomSessions(true)
                        lastSelectedSessionsCount = selectedRanges.size
                        // Ensure the Custom chip appears selected and labeled
                        if (binding.chipGroupTimePeriod.checkedChipId != R.id.chipCustom) {
                            binding.chipGroupTimePeriod.check(R.id.chipCustom)
                        }
                        updateCustomChipLabel()
                    }
                },
                onCancel = {
                    // No changes on cancel when reopening; keep existing state
                    if (lastSelectedSessionsCount == 0 && binding.chipGroupTimePeriod.checkedChipId == R.id.chipCustom) {
                        // If no selection exists, revert visual selection to last non-custom
                        binding.chipGroupTimePeriod.check(lastNonCustomTimeChipId)
                    }
                }
            )
        }

        // Set up the listener first, before adding chips
        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, checked ->
            Log.d("StatsFragment", "ChipGroup checked state changed: $checked")
            val chipId = checked.firstOrNull()
            if (chipId != null) {
                val chip = binding.chipGroupCategory.findViewById<Chip>(chipId)
                val tag = chip?.tag
                Log.d("StatsFragment", "Selected chip: id=$chipId, text=${chip?.text}, tag=$tag")
                
                if (tag is String && tag.startsWith("CUSTOM_")) {
                    // For custom activities, we use CUSTOM type and filter by customActivityId
                    Log.d("StatsFragment", "Setting custom activity: ${tag.removePrefix("CUSTOM_")}")
                    viewModel.setActivityType(ActivityType.CUSTOM)
                    viewModel.setCustomActivityId(tag.removePrefix("CUSTOM_"))
                } else {
                    val type = when (chipId) {
                        R.id.chipCategoryJoint -> ActivityType.JOINT
                        R.id.chipCategoryCone  -> ActivityType.CONE
                        R.id.chipCategoryBowl  -> ActivityType.BOWL
                        R.id.chipCategoryAll -> null
                        else                   -> null
                    }
                    Log.d("StatsFragment", "Setting activity type: $type")
                    viewModel.setActivityType(type)
                    viewModel.setCustomActivityId(null)
                }
            } else {
                Log.d("StatsFragment", "No chip selected")
                viewModel.setActivityType(null)
                viewModel.setCustomActivityId(null)
            }
        }
        
        // Now add custom activity chips after the listener is set
        setupCategoryChips()

        // user / smoker selection chips
        val repo = (requireActivity().application as CloudCounterApplication).repository
        repo.allSmokers.observe(viewLifecycleOwner) { list ->
            allSmokersCache = list

            // rebuild chips
            binding.chipGroupUsers.removeAllViews()

            // "All Smokers" chip — clears explicit selection
            val allChip = Chip(requireContext()).apply {
                text = "All Smokers"
                isCheckable = true
                isChecked = viewModel.selectedSmokerIds.value.isNullOrEmpty()
                setOnClickListener {
                    smokerSelectionDebounceJob?.cancel()
                    selectedSmokersInternal.clear()
                    viewModel.clearSmokers()
                    // uncheck others manually
                    for (i in 0 until binding.chipGroupUsers.childCount) {
                        (binding.chipGroupUsers.getChildAt(i) as? Chip)?.isChecked = false
                    }
                    isChecked = true
                }
            }
            binding.chipGroupUsers.addView(allChip)

            list.forEach { smoker ->
                val chip = Chip(requireContext()).apply {
                    text = smoker.name + if (smoker.isCloudSmoker) " ☁️" else ""
                    isCheckable = true
                    val current = viewModel.selectedSmokerIds.value
                    isChecked = current?.contains(smoker.smokerId) ?: false

                    setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            allChip.isChecked = false
                            selectedSmokersInternal.add(smoker.smokerId)
                        } else {
                            selectedSmokersInternal.remove(smoker.smokerId)
                        }

                        // debounce applying to ViewModel
                        smokerSelectionDebounceJob?.cancel()
                        smokerSelectionDebounceJob = lifecycleScope.launch {
                            delay(150) // batch quick toggles
                            viewModel.setSmokers(
                                selectedSmokersInternal.toSet().takeIf { it.isNotEmpty() }
                            )
                        }
                    }
                }
                binding.chipGroupUsers.addView(chip)
            }

            // initial summary update
            updateSelectionSummary(viewModel.selectedSmokerIds.value)
        }

        // observe stats
        viewModel.calculatedStats.observe(viewLifecycleOwner, Observer { stats ->
            binding.textViewTotalCount.text = stats.totalCount.toString()
            binding.textViewAverage.text = fmt.format(stats.averagePerTimeUnit)
            binding.textViewFrequency.text = formatFreq(stats.frequencyMillis)
        })
        setupRefreshButton()
    }

    private fun updateCustomChipLabel(reset: Boolean = false) {
        val chip = binding.chipGroupTimePeriod.findViewById<Chip>(R.id.chipCustom)
        if (reset || lastSelectedSessionsCount <= 0) {
            chip?.text = "Custom"
        } else {
            chip?.text = "${lastSelectedSessionsCount} sesh's selected"
        }
    }

    private fun setupRefreshButton() {
        val fabRefresh = binding.root.findViewById<FloatingActionButton>(R.id.fabRefreshStats)

        fabRefresh?.setOnClickListener {
            // Vibrate for 1.5 seconds
            vibrateDevice(1500)

            // Animate to neon green for 1 second
            animateRefreshButton(fabRefresh)

            // Show confirmation dialog
            showRefreshConfirmationDialog()
        }
    }

    private fun showCustomSessionPickerDialog(
        onDone: (List<Pair<Long, Long>>) -> Unit,
        onCancel: () -> Unit
    ) {
        // Prevent double-opening due to both ChipGroup state change and chip click firing
        if (isCustomDialogShowing) return
        isCustomDialogShowing = true
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        val rootContainer = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Main card following confirm-undo style
        val mainCard = CardView(requireContext()).apply {
            radius = 16.dpToPx().toFloat()
            cardElevation = 8.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                setMargins(16.dpToPx(), 0, 16.dpToPx(), 0)
            }
        }

        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(), 20.dpToPx(), 20.dpToPx(), 12.dpToPx())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleText = TextView(requireContext()).apply {
            text = "SELECT SESSIONS"
            textSize = 18f
            setTextColor(Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        val subtitle = TextView(requireContext()).apply {
            text = "Tap to select multiple sessions"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12.dpToPx()
            }
        }
        contentLayout.addView(subtitle)

        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2.dpToPx()).apply {
                topMargin = 4.dpToPx()
                bottomMargin = 12.dpToPx()
            }
            setBackgroundColor(Color.parseColor("#3398FB98"))
        }
        contentLayout.addView(divider)

        val scroll = android.widget.ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val listContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(listContainer)
        contentLayout.addView(scroll)

        val buttonRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12.dpToPx()
            }
        }

        val cancelButton = createThemedDialogButton("Cancel", false, Color.WHITE) {
            animateCardSelectionQuick(dialog) { onCancel() }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, 44.dpToPx(), 1f).apply { marginEnd = 8.dpToPx() }
        }

        // Seed with any existing selection so the dialog remembers prior choices
        val selectedRanges = mutableListOf<Pair<Long, Long>>()
        viewModel.customSessionRanges.value?.let { selectedRanges.addAll(it) }

        val doneButton = createThemedDialogButton("Done", true, Color.parseColor("#98FB98")) {
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
            layoutParams = LinearLayout.LayoutParams(0, 44.dpToPx(), 1f).apply { marginStart = 8.dpToPx() }
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
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
            setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }

        // Reset guard when dialog is dismissed from any path
        dialog.setOnDismissListener { isCustomDialogShowing = false }

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
                val currentItem = createCurrentSessionListItemView(
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
                val item = createSessionListItemView(summary, start, end, initiallySelected) { view, isSelected ->
                    // Toggle selection
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

        // Ensure buttons remain above the system bottom navigation bar
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

        // Ensure buttons remain above the system bottom navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            // Add extra bottom padding to content so buttons stay above nav bar
            val extra = (12 * resources.displayMetrics.density).toInt()
            contentLayout.setPadding(
                contentLayout.paddingLeft,
                contentLayout.paddingTop,
                contentLayout.paddingRight,
                extra + bottomInset
            )
            insets
        }
    }

    private fun createSessionListItemView(
        summary: SessionSummary,
        start: Long,
        end: Long,
        initiallySelected: Boolean,
        onToggle: (View, Boolean) -> Unit
    ): View {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dpToPx()
            }
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            background = ColorDrawable(Color.parseColor("#262626"))
        }

        // Wrap with CardView for elevation and rounded corners
        val card = CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 2.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#2C2C2C"))
        }

        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
        }

        val title = TextView(requireContext()).apply {
            val name = summary.roomName ?: "Local Session"
            text = name
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_dark_background))
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val fmt = java.text.SimpleDateFormat("MMM d, h:mma", java.util.Locale.getDefault())
        val durationMin = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes((end - start).coerceAtLeast(0))
        val subtitle = TextView(requireContext()).apply {
            text = "${fmt.format(java.util.Date(start))} → ${fmt.format(java.util.Date(end))}  •  ${durationMin} min"
            setTextColor(Color.LTGRAY)
            textSize = 12f
        }

        val statsLine = TextView(requireContext()).apply {
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

        // Selection state
        var selected = initiallySelected
        setNeonBorder(card, selected)
        container.setOnClickListener {
            selected = !selected
            onToggle(card, selected)
        }

        return container
    }

    private fun createCurrentSessionListItemView(
        title: String,
        start: Long,
        end: Long,
        initiallySelected: Boolean,
        onToggle: (View, Boolean) -> Unit
    ): View {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dpToPx()
            }
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            background = ColorDrawable(Color.parseColor("#262626"))
        }

        val card = CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 2.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#2C2C2C"))
        }

        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
        }

        val titleView = TextView(requireContext()).apply {
            text = title
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_dark_background))
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val fmt = java.text.SimpleDateFormat("MMM d, h:mma", java.util.Locale.getDefault())
        val durationMin = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes((end - start).coerceAtLeast(0))
        val subtitle = TextView(requireContext()).apply {
            text = "${fmt.format(java.util.Date(start))} → ${fmt.format(java.util.Date(end))}  •  ${durationMin} min"
            setTextColor(Color.LTGRAY)
            textSize = 12f
        }

        val statsLine = TextView(requireContext()).apply {
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
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12.dpToPx().toFloat()
            setColor(Color.parseColor("#2C2C2C"))
            val strokeColor = if (selected) Color.parseColor("#98FB98") else Color.parseColor("#303030")
            setStroke((2 * resources.displayMetrics.density).toInt(), strokeColor)
        }
        (view as? CardView)?.background = bg
    }

    // Manual fade-in matching confirm-undo behavior
    private fun performManualFadeIn(view: View, durationMs: Long) {
        val handler = Handler(Looper.getMainLooper())
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

    // Quick fade-out to match confirm-undo behavior (~400ms)
    private fun animateCardSelectionQuick(dialog: Dialog, onComplete: () -> Unit) {
        val contentView = dialog.window?.decorView?.findViewById<View>(android.R.id.content)
        val fadeOut = ObjectAnimator.ofFloat(contentView, "alpha", 1f, 0f).apply {
            duration = 400L
            interpolator = android.view.animation.AccelerateInterpolator()
        }
        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                dialog.dismiss()
                Handler(Looper.getMainLooper()).postDelayed({ onComplete() }, 100)
            }
        })
        fadeOut.start()
    }

    private fun createThemedDialogButton(text: String, isPrimary: Boolean, color: Int, onClick: () -> Unit): View {
        val ctx = requireContext()
        val buttonContainer = androidx.cardview.widget.CardView(ctx).apply {
            radius = 20.dpToPx().toFloat()
            cardElevation = if (isPrimary) 4.dpToPx().toFloat() else 0f
            setCardBackgroundColor(if (isPrimary) color else Color.parseColor("#33FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dpToPx()).apply {
                bottomMargin = 12.dpToPx()
            }
            isClickable = true
            isFocusable = true
        }

        val contentFrame = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val buttonText = TextView(ctx).apply {
            this.text = text
            textSize = 14f
            setTextColor(if (isPrimary) Color.parseColor("#424242") else Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        contentFrame.addView(buttonText)
        buttonContainer.addView(contentFrame)

        buttonContainer.setOnClickListener { onClick() }
        return buttonContainer
    }

    private fun vibrateDevice(duration: Long) {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun animateRefreshButton(button: FloatingActionButton) {
        val originalIconTint = Color.parseColor("#80FFFFFF") // 50% transparent white
        val neonGreen = Color.parseColor("#98FB98") // Your neon green color

        // Animate the icon's tint
        val iconColorAnimation = ValueAnimator.ofArgb(originalIconTint, neonGreen, originalIconTint).apply {
            duration = 2000 // 2 seconds for the animation
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                button.imageTintList = android.content.res.ColorStateList.valueOf(color)
            }
        }

        iconColorAnimation.start()
    }

    private fun showRefreshConfirmationDialog() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        val dialogView = createRefreshConfirmationDialogView(dialog)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
            setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }

        dialogView.alpha = 0f
        dialog.show()
        performDialogFadeIn(dialogView, 2000L)
    }

    private fun createRefreshConfirmationDialogView(dialog: Dialog): View {
        val rootContainer = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        val contentWrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val topSpacer = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        contentWrapper.addView(topSpacer)

        val mainCard = CardView(requireContext()).apply {
            radius = 20.dpToPx().toFloat()
            cardElevation = 12.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16.dpToPx(), 0, 16.dpToPx(), 180.dpToPx())
            }
        }

        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }

        // Surprised face emoji
        val emojiText = TextView(requireContext()).apply {
            text = "😮"
            textSize = 48f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }
        contentLayout.addView(emojiText)

        // Title
        val titleText = TextView(requireContext()).apply {
            text = "REFRESH STATS"
            textSize = 22f
            setTextColor(Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        // Message
        val messageText = TextView(requireContext()).apply {
            text = "Are you sure you want to refresh your stats? This will reset all statistics on this tab to zero."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }
        contentLayout.addView(messageText)

        // Button container
        val buttonContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Cancel button (left)
        val cancelButton = createThemedButton("Cancel", false) {
            animateCardSelection(dialog) {}
        }
        cancelButton.layoutParams = LinearLayout.LayoutParams(
            0,
            48.dpToPx(),
            1f
        ).apply {
            marginEnd = 8.dpToPx()
        }
        buttonContainer.addView(cancelButton)

        // Refresh Stats button (right) with image press effect
        val refreshButton = createThemedButton("Refresh Stats", true) {
            animateCardSelection(dialog) {
                resetStats()
            }
        }
        refreshButton.layoutParams = LinearLayout.LayoutParams(
            0,
            48.dpToPx(),
            1f
        ).apply {
            marginStart = 8.dpToPx()
        }
        buttonContainer.addView(refreshButton)

        contentLayout.addView(buttonContainer)
        mainCard.addView(contentLayout)
        contentWrapper.addView(mainCard)
        rootContainer.addView(contentWrapper)

        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                animateCardSelection(dialog) {}
            }
        }

        return rootContainer
    }

    private fun createThemedButton(text: String, isPrimary: Boolean, onClick: () -> Unit): View {
        val buttonContainer = CardView(requireContext()).apply {
            radius = 20.dpToPx().toFloat()
            cardElevation = if (isPrimary) 4.dpToPx().toFloat() else 0f
            setCardBackgroundColor(
                if (isPrimary) Color.parseColor("#98FB98")
                else Color.parseColor("#33FFFFFF")
            )

            isClickable = true
            isFocusable = true
        }

        val contentFrame = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Image view for pressed state (initially hidden)
        val imageView = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.button_pressed_background)
            visibility = View.GONE
        }

        val buttonText = TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            setTextColor(
                if (isPrimary) Color.parseColor("#424242")
                else Color.WHITE
            )
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        contentFrame.addView(imageView)
        contentFrame.addView(buttonText)
        buttonContainer.addView(contentFrame)

        val originalBackgroundColor = if (isPrimary) Color.parseColor("#98FB98") else Color.parseColor("#33FFFFFF")
        val originalTextColor = if (isPrimary) Color.parseColor("#424242") else Color.WHITE

        buttonContainer.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    buttonContainer.setCardBackgroundColor(Color.TRANSPARENT)
                    imageView.visibility = View.VISIBLE
                    buttonText.setTextColor(Color.WHITE)
                    buttonText.setShadowLayer(4f, 2f, 2f, Color.BLACK)
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    imageView.visibility = View.GONE
                    buttonContainer.setCardBackgroundColor(originalBackgroundColor)
                    buttonText.setTextColor(originalTextColor)
                    buttonText.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)

                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        buttonContainer.setOnClickListener {
            onClick()
        }

        return buttonContainer
    }

    private fun animateCardSelection(dialog: Dialog, onComplete: () -> Unit) {
        val contentView = dialog.window?.decorView?.findViewById<View>(android.R.id.content)
        val fadeOut = ObjectAnimator.ofFloat(contentView, "alpha", 1f, 0f).apply {
            duration = 2000L
            interpolator = android.view.animation.AccelerateInterpolator()
        }

        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                dialog.dismiss()
                Handler(Looper.getMainLooper()).postDelayed({
                    onComplete()
                }, 200)
            }
        })

        fadeOut.start()
    }

    private fun performDialogFadeIn(view: View, durationMs: Long) {
        val handler = Handler(Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs
        val frameDelayMs = 16L

        val fadeRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime
                val progress = kotlin.math.min(elapsed.toFloat() / durationMs.toFloat(), 1f)
                val easedProgress = 1f - (1f - progress) * (1f - progress)

                view.alpha = easedProgress

                if (currentTime < endTime) {
                    handler.postDelayed(this, frameDelayMs)
                } else {
                    view.alpha = 1f
                }
            }
        }

        handler.post(fadeRunnable)
    }

    private fun resetStats() {
        viewModel.resetStatsToZero()
        Toast.makeText(requireContext(), "Stats reset to zero", Toast.LENGTH_SHORT).show()
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
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

    private fun formatFreq(ms: Long?): String {
        if (ms == null || ms <= 0) return "N/A"
        val d = TimeUnit.MILLISECONDS.toDays(ms)
        val h = TimeUnit.MILLISECONDS.toHours(ms) % 24
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val parts = mutableListOf<String>()
        if (d > 0) parts += "${d}d"
        if (h > 0) parts += "${h}h"
        if (m > 0 || parts.isEmpty()) parts += "${m}m"
        return "Every ${parts.joinToString(" ")}"
    }

    private fun setupCategoryChips() {
        // First, remove any previously added custom activity chips
        val chipGroup = binding.chipGroupCategory
        val chipsToRemove = mutableListOf<View>()
        
        for (i in 0 until chipGroup.childCount) {
            val child = chipGroup.getChildAt(i)
            if (child is Chip && child.tag != null && child.tag.toString().startsWith("CUSTOM_")) {
                chipsToRemove.add(child)
            }
        }
        chipsToRemove.forEach { chipGroup.removeView(it) }
        
        // Add custom activity chips dynamically with proper styling
        val customActivityManager = CustomActivityManager(requireContext())
        val customActivities = customActivityManager.getCustomActivities()
        
        customActivities.forEach { activity ->
            // Inflate a chip from XML to get proper styling
            val chip = layoutInflater.inflate(R.layout.chip_category_item, chipGroup, false) as Chip
            chip.apply {
                text = activity.name
                tag = "CUSTOM_${activity.id}"
                id = View.generateViewId() // Generate unique ID for the chip
                // Ensure the chip is checkable (should be from style, but let's be explicit)
                isCheckable = true
                Log.d("StatsFragment", "Added custom chip: ${activity.name} with id: $id and tag: $tag")
            }
            chipGroup.addView(chip)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh category chips when fragment becomes visible again
        // This ensures custom activities are updated if they changed while on another tab
        setupCategoryChips()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Unregister broadcast receiver
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(customActivityChangeReceiver)
        _binding = null
    }
}
