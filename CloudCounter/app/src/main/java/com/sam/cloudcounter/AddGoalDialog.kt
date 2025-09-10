package com.sam.cloudcounter

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.min

class AddGoalDialog : DialogFragment() {

    private lateinit var onGoalCreatedListener: (Goal) -> Unit

    fun setOnGoalCreatedListener(listener: (Goal) -> Unit) {
        onGoalCreatedListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.TransparentDialog)
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_add_goal, null)
        dialog.setContentView(view)

        setupGoalForm(view, dialog)
        startAdvancedAnimations(view)

        // Start with alpha 0 for fade-in animation
        view.alpha = 0f

        dialog.setOnShowListener {
            // Use the same 1-second fade-in animation as cloud session dialog (reduced by 50%)
            performManualFadeIn(view, 1000L)
        }

        return dialog
    }

    /**
     * Manually animates the fade in using a Handler to guarantee the animation runs for the full duration
     * This matches the MainActivity implementation exactly
     */
    private fun performManualFadeIn(view: View, durationMs: Long) {
        val handler = Handler(Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs

        // Animation frame rate (60 FPS = update every ~16ms)
        val frameDelayMs = 16L

        val fadeRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime
                val progress = min(elapsed.toFloat() / durationMs.toFloat(), 1f)

                // Apply easing (decelerate interpolation)
                val easedProgress = 1f - (1f - progress) * (1f - progress)

                view.alpha = easedProgress

                if (currentTime < endTime) {
                    // Continue animation
                    handler.postDelayed(this, frameDelayMs)
                } else {
                    // Animation complete - ensure final state
                    view.alpha = 1f
                }
            }
        }

        // Start the animation
        handler.post(fadeRunnable)
    }

    private fun startAdvancedAnimations(view: View) {
        // Hide the neon border - we'll use throbbing animation on cards instead
        val neonBorder = view.findViewById<View>(R.id.neonBorder)
        neonBorder.visibility = View.GONE

        // Background Shimmer Animation (keep this for background effect)
        val shimmerOverlay = view.findViewById<View>(R.id.shimmerOverlay)
        shimmerOverlay.post { // Wait for the view to be measured
            ObjectAnimator.ofFloat(shimmerOverlay, "translationY", -shimmerOverlay.height.toFloat(), shimmerOverlay.height.toFloat()).apply {
                duration = 6000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGoalForm(view: View, dialog: Dialog) {
        // --- View References ---
        val editGoalName = view.findViewById<EditText>(R.id.editGoalName)
        val radioGroupType = view.findViewById<RadioGroup>(R.id.radioGroupType)
        val radioCurrentSession = view.findViewById<RadioButton>(R.id.radioCurrentSession)
        val radioTimeBased = view.findViewById<RadioButton>(R.id.radioTimeBased)
        val layoutTimeBased = view.findViewById<LinearLayout>(R.id.layoutTimeBased)
        val editTimeDuration = view.findViewById<EditText>(R.id.editTimeDuration)
        val spinnerTimeUnit = view.findViewById<Spinner>(R.id.spinnerTimeUnit)
        val editTargetJoints = view.findViewById<EditText>(R.id.editTargetJoints)
        val editTargetCones = view.findViewById<EditText>(R.id.editTargetCones)
        val editTargetBowls = view.findViewById<EditText>(R.id.editTargetBowls)
        val radioGroupOverflow = view.findViewById<RadioGroup>(R.id.radioGroupOverflow)
        val checkboxRecurring = view.findViewById<CheckBox>(R.id.checkboxRecurring)
        val checkboxProgressNotifications = view.findViewById<CheckBox>(R.id.checkboxProgressNotifications)
        val checkboxCompletionNotifications = view.findViewById<CheckBox>(R.id.checkboxCompletionNotifications)
        val checkboxAllSmokers = view.findViewById<CheckBox>(R.id.checkboxAllSmokers)
        val smokersCheckboxContainer = view.findViewById<LinearLayout>(R.id.smokersCheckboxContainer)

        // Get button views
        val buttonCreate = view.findViewById<CardView>(R.id.buttonCreate)
        val buttonCancel = view.findViewById<CardView>(R.id.buttonCancel)
        val textCreate = view.findViewById<TextView>(R.id.textCreate)
        val textCancel = view.findViewById<TextView>(R.id.textCancel)
        val imageCreateBackground = view.findViewById<ImageView>(R.id.imageCreateBackground)
        val imageCancelBackground = view.findViewById<ImageView>(R.id.imageCancelBackground)

        val prefs = requireContext().getSharedPreferences("GoalPrefs", Context.MODE_PRIVATE)
        val selectedSmokers = mutableSetOf<String>()

        // --- Setup Smoker Selection ---
        lifecycleScope.launch {
            val smokerDao = AppDatabase.getDatabase(requireContext()).smokerDao()
            val smokersList = smokerDao.getAllSmokersList()
            val allSmokerNames = smokersList.map { it.name }

            val rememberedSmokers = prefs.getString("lastSelectedSmokers", null)?.split(",")?.toSet()
            if (rememberedSmokers.isNullOrEmpty() && allSmokerNames.isNotEmpty()) {
                selectedSmokers.add(allSmokerNames[0])
            } else if (rememberedSmokers != null) {
                selectedSmokers.addAll(rememberedSmokers)
            }

            val individualCheckBoxes = mutableListOf<CheckBox>()
            smokersList.forEach { smoker ->
                val checkBox = CheckBox(requireContext()).apply {
                    text = smoker.name
                    isChecked = selectedSmokers.contains(smoker.name)
                    setTextColor(resources.getColor(R.color.white, null))
                    buttonTintList = resources.getColorStateList(R.color.my_light_primary, null)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedSmokers.add(smoker.name) else selectedSmokers.remove(smoker.name)
                        checkboxAllSmokers.isChecked = selectedSmokers.size == allSmokerNames.size
                    }
                }
                smokersCheckboxContainer.addView(checkBox)
                individualCheckBoxes.add(checkBox)
            }

            checkboxAllSmokers.isChecked = selectedSmokers.size == allSmokerNames.size
            checkboxAllSmokers.setOnCheckedChangeListener { _, isChecked ->
                individualCheckBoxes.forEach { it.isChecked = isChecked }
                if (isChecked) selectedSmokers.addAll(allSmokerNames) else selectedSmokers.clear()
            }
        }

        // --- Setup Time Unit Spinner ---
        val timeUnits = TimeUnit.values().map { it.name.lowercase().replaceFirstChar(Char::titlecase) }.toTypedArray()
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, timeUnits)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTimeUnit.adapter = spinnerAdapter

        // --- Pre-fill Form from SharedPreferences ---
        editTargetJoints.setText(prefs.getInt("lastTargetJoints", 0).toString())
        editTargetCones.setText(prefs.getInt("lastTargetCones", 0).toString())
        editTargetBowls.setText(prefs.getInt("lastTargetBowls", 0).toString())

        val lastGoalType = GoalType.valueOf(prefs.getString("lastGoalType", GoalType.ALL_SESSIONS.name)!!)
        when (lastGoalType) {
            GoalType.CURRENT_SESSION -> radioGroupType.check(R.id.radioCurrentSession)
            GoalType.ALL_SESSIONS -> radioGroupType.check(R.id.radioAllSessions)
            GoalType.TIME_BASED -> radioGroupType.check(R.id.radioTimeBased)
        }
        editTimeDuration.setText(prefs.getInt("lastTimeDuration", 1).toString())
        val lastTimeUnitName = prefs.getString("lastTimeUnit", TimeUnit.DAY.name)
        val timeUnitPosition = TimeUnit.values().indexOfFirst { it.name == lastTimeUnitName }
        if (timeUnitPosition >= 0) spinnerTimeUnit.setSelection(timeUnitPosition)

        checkboxRecurring.isChecked = prefs.getBoolean("lastRecurring", false)
        if (prefs.getBoolean("lastAllowOverflow", true)) {
            radioGroupOverflow.check(R.id.radioAllowOverflow)
        } else {
            radioGroupOverflow.check(R.id.radioAutoComplete)
        }
        checkboxProgressNotifications.isChecked = prefs.getBoolean("lastProgressNotif", true)
        checkboxCompletionNotifications.isChecked = prefs.getBoolean("lastCompletionNotif", true)

        // --- UI Logic ---
        radioGroupType.setOnCheckedChangeListener { _, checkedId ->
            layoutTimeBased.visibility = if (checkedId == R.id.radioTimeBased) View.VISIBLE else View.GONE
            checkboxRecurring.isEnabled = checkedId != R.id.radioCurrentSession
            if (checkedId == R.id.radioCurrentSession) checkboxRecurring.isChecked = false
        }
        radioGroupType.check(radioGroupType.checkedRadioButtonId)

        // --- Setup Create Button with Image Press Effect ---
        setupButtonWithImagePress(
            buttonCreate,
            textCreate,
            imageCreateBackground,
            isPrimary = true,
            originalBackgroundColor = Color.parseColor("#98FB98"),
            originalTextColor = Color.parseColor("#424242")
        ) {
            val name = editGoalName.text.toString().trim()
            val targetJoints = editTargetJoints.text.toString().toIntOrNull() ?: 0
            val targetCones = editTargetCones.text.toString().toIntOrNull() ?: 0
            val targetBowls = editTargetBowls.text.toString().toIntOrNull() ?: 0

            if (targetJoints == 0 && targetCones == 0 && targetBowls == 0) {
                Toast.makeText(context, "Please set at least one target value.", Toast.LENGTH_SHORT).show()
                return@setupButtonWithImagePress
            }
            if (selectedSmokers.isEmpty()) {
                Toast.makeText(context, "Please select at least one smoker.", Toast.LENGTH_SHORT).show()
                return@setupButtonWithImagePress
            }

            val goalType = when (radioGroupType.checkedRadioButtonId) {
                R.id.radioCurrentSession -> GoalType.CURRENT_SESSION
                R.id.radioTimeBased -> GoalType.TIME_BASED
                else -> GoalType.ALL_SESSIONS
            }

            val timeDuration = if (goalType == GoalType.TIME_BASED) editTimeDuration.text.toString().toIntOrNull() ?: 1 else null
            val timeUnit = if (goalType == GoalType.TIME_BASED) TimeUnit.values()[spinnerTimeUnit.selectedItemPosition] else null
            val isRecurring = checkboxRecurring.isChecked && goalType != GoalType.CURRENT_SESSION
            val allowOverflow = radioGroupOverflow.checkedRadioButtonId == R.id.radioAllowOverflow
            val progressNotifications = checkboxProgressNotifications.isChecked
            val completionNotifications = checkboxCompletionNotifications.isChecked
            val smokerSelection = selectedSmokers.joinToString(",")
            val currentSessionCode = (activity as? MainActivity)?.getCurrentShareCode()

            if (goalType == GoalType.CURRENT_SESSION && currentSessionCode == null) {
                Toast.makeText(requireContext(), "No active session. Please start a session first.", Toast.LENGTH_SHORT).show()
                return@setupButtonWithImagePress
            }

            prefs.edit().apply {
                putInt("lastTargetJoints", targetJoints); putInt("lastTargetCones", targetCones); putInt("lastTargetBowls", targetBowls)
                putString("lastGoalType", goalType.name); putInt("lastTimeDuration", timeDuration ?: 1)
                putString("lastTimeUnit", timeUnit?.name ?: TimeUnit.DAY.name); putBoolean("lastRecurring", isRecurring)
                putBoolean("lastAllowOverflow", allowOverflow); putBoolean("lastProgressNotif", progressNotifications)
                putBoolean("lastCompletionNotif", completionNotifications); putString("lastSelectedSmokers", smokerSelection)
                apply()
            }

            val newGoal = Goal(
                goalName = name, goalType = goalType, targetJoints = targetJoints, targetCones = targetCones, targetBowls = targetBowls,
                isRecurring = isRecurring, progressNotificationsEnabled = progressNotifications, completionNotificationsEnabled = completionNotifications,
                allowOverflow = allowOverflow, selectedSmokers = smokerSelection, timeDuration = timeDuration, timeUnit = timeUnit,
                sessionShareCode = if (goalType == GoalType.CURRENT_SESSION) currentSessionCode else null
            )

            if (::onGoalCreatedListener.isInitialized) onGoalCreatedListener(newGoal)
            animateAndDismiss(dialog)
        }

        // --- Setup Cancel Button with Image Press Effect ---
        setupButtonWithImagePress(
            buttonCancel,
            textCancel,
            imageCancelBackground,
            isPrimary = false,
            originalBackgroundColor = Color.parseColor("#33FFFFFF"),
            originalTextColor = Color.WHITE
        ) {
            animateAndDismiss(dialog)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButtonWithImagePress(
        cardView: CardView,
        textView: TextView,
        imageView: ImageView,
        isPrimary: Boolean,
        originalBackgroundColor: Int,
        originalTextColor: Int,
        onClick: () -> Unit
    ) {
        // Add throbbing animation for primary button (Create button)
        if (isPrimary) {
            addThrobbingAnimation(cardView)
        }
        
        // DEBUG: Log initial sizes
        cardView.post {
            android.util.Log.d("AddGoalDialog", "Button initial width: ${cardView.width}, height: ${cardView.height}")
            android.util.Log.d("AddGoalDialog", "Image drawable intrinsic size: ${imageView.drawable?.intrinsicWidth} x ${imageView.drawable?.intrinsicHeight}")

            // CRITICAL FIX: Set max dimensions on the ImageView to prevent expansion
            val maxWidth = cardView.width
            val maxHeight = cardView.height

            imageView.layoutParams = imageView.layoutParams.apply {
                width = maxWidth
                height = maxHeight
            }

            // Also set max width/height constraints
            imageView.maxWidth = maxWidth
            imageView.maxHeight = maxHeight

            android.util.Log.d("AddGoalDialog", "Set ImageView max dimensions to: ${maxWidth} x ${maxHeight}")
        }

        // Store the original card dimensions
        var originalCardWidth = 0
        var originalCardHeight = 0
        var originalParentWidth = 0

        cardView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Store dimensions BEFORE any changes
                    originalCardWidth = cardView.width
                    originalCardHeight = cardView.height
                    val parent = cardView.parent as? View
                    originalParentWidth = parent?.width ?: 0

                    android.util.Log.d("AddGoalDialog", "ACTION_DOWN - Storing original dimensions:")
                    android.util.Log.d("AddGoalDialog", "  Card: ${originalCardWidth} x ${originalCardHeight}")
                    android.util.Log.d("AddGoalDialog", "  Parent: ${originalParentWidth}")

                    // CRITICAL: Set the card's layout params to fixed size BEFORE showing image
                    cardView.layoutParams = cardView.layoutParams.apply {
                        width = originalCardWidth
                        height = originalCardHeight
                    }

                    // Show image background
                    cardView.setCardBackgroundColor(Color.TRANSPARENT)
                    imageView.visibility = View.VISIBLE

                    // Change text color based on button type
                    if (isPrimary) {
                        textView.setTextColor(Color.WHITE)
                        textView.setShadowLayer(4f, 2f, 2f, Color.BLACK)
                    } else {
                        textView.setShadowLayer(4f, 2f, 2f, Color.BLACK)
                    }

                    // Force layout to not change size
                    cardView.post {
                        if (cardView.width != originalCardWidth || cardView.height != originalCardHeight) {
                            android.util.Log.d("AddGoalDialog", "WARNING: Size changed! Forcing back to original")
                            cardView.layoutParams = cardView.layoutParams.apply {
                                width = originalCardWidth
                                height = originalCardHeight
                            }
                            cardView.requestLayout()
                        }

                        android.util.Log.d("AddGoalDialog", "ACTION_DOWN - After showing image:")
                        android.util.Log.d("AddGoalDialog", "  Card: ${cardView.width} x ${cardView.height}")
                        android.util.Log.d("AddGoalDialog", "  Parent: ${(cardView.parent as? View)?.width}")
                    }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    android.util.Log.d("AddGoalDialog", "ACTION_UP/CANCEL")

                    // Restore original background
                    imageView.visibility = View.GONE
                    cardView.setCardBackgroundColor(originalBackgroundColor)

                    // Restore original text color and remove shadow
                    textView.setTextColor(originalTextColor)
                    textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)

                    // Restore original dimensions if needed
                    if (originalCardWidth > 0 && originalCardHeight > 0) {
                        cardView.layoutParams = cardView.layoutParams.apply {
                            width = originalCardWidth
                            height = originalCardHeight
                        }
                    }

                    if (event.action == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        cardView.setOnClickListener {
            onClick()
        }
    }

    private fun animateAndDismiss(dialog: Dialog) {
        val view = dialog.window?.decorView?.findViewById<View>(android.R.id.content)

        // Use the same 1-second fade-out animation as cloud session dialog (reduced by 50%)
        performManualFadeOut(view, 1000L) {
            dialog.dismiss()
        }
    }

    /**
     * Manually animates the fade out matching the cloud session dialog
     */
    private fun performManualFadeOut(view: View?, durationMs: Long, onComplete: () -> Unit) {
        if (view == null) {
            onComplete()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs
        val frameDelayMs = 16L

        val fadeRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime
                val progress = min(elapsed.toFloat() / durationMs.toFloat(), 1f)

                // Apply easing (accelerate interpolation for fade out)
                val easedProgress = progress * progress

                view.alpha = 1f - easedProgress

                if (currentTime < endTime) {
                    handler.postDelayed(this, frameDelayMs)
                } else {
                    view.alpha = 0f
                    onComplete()
                }
            }
        }

        handler.post(fadeRunnable)
    }

    private fun addThrobbingAnimation(cardView: CardView) {
        // Similar to the working animation in MainActivity/AddSmokerDialog
        val colors = intArrayOf(
            Color.parseColor("#33FFFFFF"),
            Color.parseColor("#3398FB98"),
            Color.parseColor("#33FFFFFF")
        )

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var animationProgress = 0f
        var increasing = true

        val animationRunnable = object : Runnable {
            override fun run() {
                // Update progress
                if (increasing) {
                    animationProgress += 0.02f
                    if (animationProgress >= 1f) {
                        animationProgress = 1f
                        increasing = false
                    }
                } else {
                    animationProgress -= 0.02f
                    if (animationProgress <= 0f) {
                        animationProgress = 0f
                        increasing = true
                    }
                }

                // Calculate color based on progress
                val color = if (animationProgress <= 0.5f) {
                    blendColors(colors[0], colors[1], animationProgress * 2)
                } else {
                    blendColors(colors[1], colors[2], (animationProgress - 0.5f) * 2)
                }

                cardView.setCardBackgroundColor(color)

                // Continue animation
                handler.postDelayed(this, 50) // Update every 50ms for smooth animation
            }
        }

        handler.post(animationRunnable)
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (Color.alpha(from) * inverseRatio + Color.alpha(to) * ratio).toInt()
        val r = (Color.red(from) * inverseRatio + Color.red(to) * ratio).toInt()
        val g = (Color.green(from) * inverseRatio + Color.green(to) * ratio).toInt()
        val b = (Color.blue(from) * inverseRatio + Color.blue(to) * ratio).toInt()
        return Color.argb(a, r, g, b)
    }
}