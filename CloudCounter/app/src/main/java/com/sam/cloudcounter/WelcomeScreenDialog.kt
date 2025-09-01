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
    private val activity: MainActivity,
    private val onComplete: () -> Unit
) : Dialog(activity) {

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
    
    private val sharedPreferences: SharedPreferences = activity.getSharedPreferences("CloudCounterPrefs", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("WELCOME_DEBUG", "🎨 WelcomeScreenDialog.onCreate() called")
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_welcome_screen)
        
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            activity.resources.displayMetrics.widthPixels - (32 * activity.resources.displayMetrics.density).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        setCancelable(false)
        
        android.util.Log.d("WELCOME_DEBUG", "🔧 Initializing views...")
        initViews()
        android.util.Log.d("WELCOME_DEBUG", "🎬 Setting up animations...")
        setupAnimations()
        android.util.Log.d("WELCOME_DEBUG", "👆 Setting up click listeners...")
        setupClickListeners()
        android.util.Log.d("WELCOME_DEBUG", "✅ WelcomeScreenDialog setup complete")
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
        val pulseAnimation = AnimationUtils.loadAnimation(activity, R.anim.pulse_animation)
        pulseStash.startAnimation(pulseAnimation)
        pulseRatios.startAnimation(pulseAnimation)
        pulseGoal.startAnimation(pulseAnimation)
        
        // Hide shimmer overlays - they might be causing the flashing
        shimmerSetupStash.visibility = View.GONE
        shimmerSetupRatios.visibility = View.GONE
        shimmerCreateGoal.visibility = View.GONE
        
        // Title glow animation - make it subtler
        findViewById<View>(R.id.titleGlow)?.apply {
            val glowAnimation = AnimationUtils.loadAnimation(activity, R.anim.glow_pulse_animation)
            startAnimation(glowAnimation)
        }
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
            // Don't mark as shown - we want it to appear every Google login
            dismiss()
            onComplete()
        }
        
        // Next button - navigate through selected dialogs
        buttonNext.setOnClickListener {
            android.util.Log.d("WELCOME_DEBUG", "📍 Next button clicked")
            android.util.Log.d("WELCOME_DEBUG", "☑️ Stash checked: ${checkboxSetupStash.isChecked}")
            android.util.Log.d("WELCOME_DEBUG", "☑️ Ratios checked: ${checkboxSetupRatios.isChecked}")
            android.util.Log.d("WELCOME_DEBUG", "☑️ Goal checked: ${checkboxCreateGoal.isChecked}")
            
            if (!checkboxSetupStash.isChecked && 
                !checkboxSetupRatios.isChecked && 
                !checkboxCreateGoal.isChecked) {
                android.util.Log.d("WELCOME_DEBUG", "❌ Nothing selected, closing dialog")
                // Nothing selected, just close
                dismiss()
                onComplete()
            } else {
                android.util.Log.d("WELCOME_DEBUG", "✅ Options selected, showing dialogs...")
                // Show dialogs in sequence based on selections
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
        android.util.Log.d("WELCOME_DEBUG", "🚀 showNextDialog() called")
        
        // Build list of dialogs to show
        val stashSelected = checkboxSetupStash.isChecked
        val ratiosSelected = checkboxSetupRatios.isChecked
        val goalSelected = checkboxCreateGoal.isChecked
        
        android.util.Log.d("WELCOME_DEBUG", "📋 Selections - Stash: $stashSelected, Ratios: $ratiosSelected, Goal: $goalSelected")
        
        // For now, show dialogs directly without chaining
        // This is simpler and will work better
        if (stashSelected) {
            android.util.Log.d("WELCOME_DEBUG", "⏱️ Scheduling stash dialog immediately")
            showAddStashDialog()
        }
        
        if (ratiosSelected) {
            // Show ratio dialog after a delay if stash was also selected
            val delay = if (stashSelected) 1000L else 0L
            android.util.Log.d("WELCOME_DEBUG", "⏱️ Scheduling ratio dialog with ${delay}ms delay")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showSetRatioDialog()
            }, delay)
        }
        
        if (goalSelected) {
            // Show goal dialog after a delay if other dialogs were selected
            val delay = when {
                stashSelected && ratiosSelected -> 2000L
                stashSelected || ratiosSelected -> 1000L
                else -> 0L
            }
            android.util.Log.d("WELCOME_DEBUG", "⏱️ Scheduling goal dialog with ${delay}ms delay")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showCreateGoalDialog()
            }, delay)
        }
        
        // Call completion callback
        android.util.Log.d("WELCOME_DEBUG", "🔚 Calling onComplete callback")
        onComplete()
    }
    
    private fun showAddStashDialog() {
        android.util.Log.d("WELCOME_DEBUG", "🏦 showAddStashDialog() called")
        android.util.Log.d("WELCOME_DEBUG", "📲 Calling MainActivity.showAddStashDialog()")
        activity.showAddStashDialog()
    }
    
    private fun showSetRatioDialog() {
        android.util.Log.d("WELCOME_DEBUG", "⚖️ showSetRatioDialog() called")
        android.util.Log.d("WELCOME_DEBUG", "📲 Calling MainActivity.showSetRatioDialog()")
        activity.showSetRatioDialog()
    }
    
    private fun showCreateGoalDialog() {
        android.util.Log.d("WELCOME_DEBUG", "🎯 showCreateGoalDialog() called")
        android.util.Log.d("WELCOME_DEBUG", "📲 Calling MainActivity.showAddGoalDialog()")
        activity.showAddGoalDialog()
    }
    
    private fun markWelcomeShown() {
        sharedPreferences.edit().putBoolean("welcome_screen_shown", true).apply()
    }
    
    companion object {
        fun shouldShowWelcomeScreen(context: Context): Boolean {
            val prefs = context.getSharedPreferences("CloudCounterPrefs", Context.MODE_PRIVATE)
            val wasShown = prefs.getBoolean("welcome_screen_shown", false)
            android.util.Log.d("WELCOME_DEBUG", "🔍 Checking if welcome was shown before: $wasShown")
            val shouldShow = !wasShown
            android.util.Log.d("WELCOME_DEBUG", "💡 Should show welcome screen: $shouldShow")
            return shouldShow
        }
    }
}