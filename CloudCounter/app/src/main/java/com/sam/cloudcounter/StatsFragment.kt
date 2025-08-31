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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity()).get(StatsViewModel::class.java)

        // seed internal selection so UI reflects current model state
        viewModel.selectedSmokerIds.value?.let { selectedSmokersInternal.addAll(it) }

        // observe selected smokers to update summary text
        viewModel.selectedSmokerIds.observe(viewLifecycleOwner) { ids ->
            updateSelectionSummary(ids)
            Log.d("StatsFragment", "Now showing stats for smoker IDs=$ids")
        }

        // time‐period chips
        binding.chipGroupTimePeriod.setOnCheckedStateChangeListener { group, checked ->
            if (checked.isNotEmpty()) {
                val id = checked.first()
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

        // category chips
        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, checked ->
            val type = when (checked.firstOrNull()) {
                R.id.chipCategoryJoint -> ActivityType.JOINT
                R.id.chipCategoryCone  -> ActivityType.CONE
                R.id.chipCategoryBowl  -> ActivityType.BOWL
                else                   -> null
            }
            viewModel.setActivityType(type)
        }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}