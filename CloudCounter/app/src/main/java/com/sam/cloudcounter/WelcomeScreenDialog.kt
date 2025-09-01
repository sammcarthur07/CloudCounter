package com.sam.cloudcounter

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.CheckBox
import android.widget.TextView
import androidx.cardview.widget.CardView

class WelcomeScreenDialog(
    context: Context,
    private val onComplete: () -> Unit
) : Dialog(context) {

    private lateinit var checkboxSetupStash: CheckBox
    private lateinit var checkboxSetupRatios: CheckBox
    private lateinit var checkboxCreateGoal: CheckBox
    private lateinit var buttonSkip: CardView
    private lateinit var buttonNext: CardView
    
    // Pulsing views
    private lateinit var pulseStash: View
    private lateinit var pulseRatios: View
    private lateinit var pulseGoal: View
    
    // Shimmer views
    private lateinit var shimmerSetupStash: View
    private lateinit var shimmerSetupRatios: View
    private lateinit var shimmerCreateGoal: View
    
    // Card views for click handling
    private lateinit var cardSetupStash: CardView
    private lateinit var cardSetupRatios: CardView
    private lateinit var cardCreateGoal: CardView
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("CloudCounterPrefs", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_welcome_screen)
        
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            context.resources.displayMetrics.widthPixels - (32 * context.resources.displayMetrics.density).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        setCancelable(false)
        
        initViews()
        setupAnimations()
        setupClickListeners()
    }
    
    private fun initViews() {
        checkboxSetupStash = findViewById(R.id.checkboxSetupStash)
        checkboxSetupRatios = findViewById(R.id.checkboxSetupRatios)
        checkboxCreateGoal = findViewById(R.id.checkboxCreateGoal)
        buttonSkip = findViewById(R.id.buttonSkip)
        buttonNext = findViewById(R.id.buttonNext)
        
        pulseStash = findViewById(R.id.pulseStash)
        pulseRatios = findViewById(R.id.pulseRatios)
        pulseGoal = findViewById(R.id.pulseGoal)
        
        shimmerSetupStash = findViewById(R.id.shimmerSetupStash)
        shimmerSetupRatios = findViewById(R.id.shimmerSetupRatios)
        shimmerCreateGoal = findViewById(R.id.shimmerCreateGoal)
        
        cardSetupStash = findViewById(R.id.cardSetupStash)
        cardSetupRatios = findViewById(R.id.cardSetupRatios)
        cardCreateGoal = findViewById(R.id.cardCreateGoal)
    }
    
    private fun setupAnimations() {
        // Start pulsing animations ONLY for the green dots
        val pulseAnimation = AnimationUtils.loadAnimation(context, R.anim.pulse_animation)
        pulseStash.startAnimation(pulseAnimation)
        pulseRatios.startAnimation(pulseAnimation)
        pulseGoal.startAnimation(pulseAnimation)
        
        // Remove shimmer animations - they might be causing the flashing
        // shimmerSetupStash.visibility = View.GONE
        // shimmerSetupRatios.visibility = View.GONE
        // shimmerCreateGoal.visibility = View.GONE
        
        // Title glow animation - make it subtler
        findViewById<View>(R.id.titleGlow)?.apply {
            val glowAnimation = AnimationUtils.loadAnimation(context, R.anim.glow_pulse_animation)
            startAnimation(glowAnimation)
        }
        
        // Remove animated border if it's causing issues
        findViewById<View>(R.id.animatedBorder)?.visibility = View.GONE
    }
    
    private fun setupClickListeners() {
        // Card click toggles checkbox
        cardSetupStash.setOnClickListener {
            checkboxSetupStash.isChecked = !checkboxSetupStash.isChecked
        }
        
        cardSetupRatios.setOnClickListener {
            checkboxSetupRatios.isChecked = !checkboxSetupRatios.isChecked
        }
        
        cardCreateGoal.setOnClickListener {
            checkboxCreateGoal.isChecked = !checkboxCreateGoal.isChecked
        }
        
        // Skip button
        buttonSkip.setOnClickListener {
            markWelcomeShown()
            dismiss()
            onComplete()
        }
        
        // Next button - navigate through selected dialogs
        buttonNext.setOnClickListener {
            if (!checkboxSetupStash.isChecked && 
                !checkboxSetupRatios.isChecked && 
                !checkboxCreateGoal.isChecked) {
                // Nothing selected, just close
                markWelcomeShown()
                dismiss()
                onComplete()
            } else {
                // Show dialogs in sequence based on selections
                markWelcomeShown()
                dismiss()
                showNextDialog()
            }
        }
        
        // Button press effects
        setupButtonPressEffect(buttonSkip, R.id.imageSkipBackground)
        setupButtonPressEffect(buttonNext, R.id.imageNextBackground)
    }
    
    private fun setupButtonPressEffect(button: CardView, imageViewId: Int) {
        val imageBackground = button.findViewById<View>(imageViewId)
        
        button.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    imageBackground.visibility = View.VISIBLE
                    imageBackground.alpha = 0f
                    imageBackground.animate()
                        .alpha(1f)
                        .setDuration(100)
                        .start()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    imageBackground.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction { imageBackground.visibility = View.GONE }
                        .start()
                }
            }
            false
        }
    }
    
    private fun showNextDialog() {
        // Build list of dialogs to show
        val stashSelected = checkboxSetupStash.isChecked
        val ratiosSelected = checkboxSetupRatios.isChecked
        val goalSelected = checkboxCreateGoal.isChecked
        
        // For now, show dialogs directly without chaining
        // This is simpler and will work better
        if (stashSelected) {
            showAddStashDialog()
        }
        
        if (ratiosSelected) {
            // Show ratio dialog after a delay if stash was also selected
            val delay = if (stashSelected) 300L else 0L
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showSetRatioDialog()
            }, delay)
        }
        
        if (goalSelected) {
            // Show goal dialog after a delay if other dialogs were selected
            val delay = when {
                stashSelected && ratiosSelected -> 600L
                stashSelected || ratiosSelected -> 300L
                else -> 0L
            }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showCreateGoalDialog()
            }, delay)
        }
        
        // Call completion callback
        onComplete()
    }
    
    private fun showAddStashDialog() {
        // Use MainActivity method to show stash dialog
        val activity = context as? MainActivity
        activity?.showAddStashDialog()
    }
    
    private fun showSetRatioDialog() {
        // Use MainActivity method to show ratio dialog
        val activity = context as? MainActivity
        activity?.showSetRatioDialog()
    }
    
    private fun showCreateGoalDialog() {
        // Use MainActivity method to show goal dialog
        val activity = context as? MainActivity
        activity?.showAddGoalDialog()
    }
    
    private fun markWelcomeShown() {
        sharedPreferences.edit().putBoolean("welcome_screen_shown", true).apply()
    }
    
    companion object {
        fun shouldShowWelcomeScreen(context: Context): Boolean {
            val prefs = context.getSharedPreferences("CloudCounterPrefs", Context.MODE_PRIVATE)
            return !prefs.getBoolean("welcome_screen_shown", false)
        }
    }
}