package com.sam.cloudcounter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sam.cloudcounter.StashSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import android.app.AlertDialog
import android.widget.ArrayAdapter
import androidx.core.content.res.ResourcesCompat


class GiantCounterActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "GiantCounter"
        private const val LOG_PREFIX = "🎯 GIANT: "
    }

    // UI elements
    private lateinit var backgroundImage: ImageView
    private lateinit var giantButton: ImageView
    private lateinit var counterText: TextView
    private lateinit var smokerNameText: TextView
    private lateinit var recentStatsText: TextView
    private lateinit var backButton: TextView
    private lateinit var konfettiView: KonfettiView
    // private lateinit var calculatorButton: TextView // REMOVED
    private lateinit var topControlsContainer: ViewGroup
    private lateinit var advancedButton: TextView  // Renamed from timersButton
    private lateinit var timerControlsContainer: ViewGroup
    private lateinit var textTimeSinceLast: TextView
    private lateinit var textLastGapCountdown: TextView
    private lateinit var textThisSesh: TextView
    private lateinit var roundsContainer: ViewGroup
    private lateinit var btnRoundMinus: ImageView
    private lateinit var btnRoundPlus: ImageView
    private lateinit var textRoundsLeft: TextView
    private lateinit var btnUndo: TextView
    private lateinit var btnRewind: TextView
    private lateinit var btnSkip: TextView
    private lateinit var topButtonsContainer: LinearLayout
    
    // Data
    private var currentSmoker: String = "Sam"
    private var currentActivityType: String = "cones"
    private var currentCount: Int = 0
    private var recentSmoker: String = ""
    private var recentSmokerCount: Int = 0
    private var allSmokers: List<com.sam.cloudcounter.Smoker> = emptyList()
    private var currentSmokerIndex: Int = 0
    private var sessionStart: Long = 0L
    
    // Settings from main activity
    private var isAutoMode: Boolean = true
    private var timerEnabled: Boolean = false
    private var topSectionVisible: Boolean = true
    private var timersVisible: Boolean = false
    
    // Timer data
    private var lastLogTime: Long = 0L
    private var lastConeTimestamp: Long = 0L
    private var lastJointTimestamp: Long = 0L
    private var lastBowlTimestamp: Long = 0L
    private var roundsLeft: Int = 0
    private var initialRoundsSet: Int = 0
    private val intervalsList = mutableListOf<Long>()
    private val activitiesTimestamps = mutableListOf<Long>()  // Track all activity timestamps for gap calculation
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable
    
    // Undo/Rewind functionality
    private var rewindOffset = 0L
    private val REWIND_AMOUNT_MS = 10000L  // 10 seconds per rewind
    private val activityHistory = mutableListOf<Long>()  // Track activity IDs for undo
    
    // Font settings
    private var smokerFontColor: Int = Color.parseColor("#98FB98")  // Default green
    private var smokerFontTypeface: android.graphics.Typeface? = null
    private var globalLockedColor: Int? = null
    private var globalLockedFont: android.graphics.Typeface? = null
    private var colorChangingEnabled: Boolean = true
    private var randomFontsEnabled: Boolean = true
    
    // Hold-down timing
    private var nameHoldStartTime = 0L
    private var nameHoldHandler: Handler? = null
    private var nameHoldRunnable: Runnable? = null
    private var fontCycleHandler: Handler? = null
    private var fontCycleRunnable: Runnable? = null
    private var lastSelectedFontIndex = 0
    
    // Font list (matching MainActivity)
    private val fontList = listOf(
        R.font.bitcount_prop_double,
        R.font.exile,
        R.font.modak,
        R.font.oi,
        R.font.rubik_glitch,
        R.font.sankofa_display,
        R.font.silkscreen,
        R.font.rubik_puddles,
        R.font.rubik_beastly,
        R.font.sixtyfour,
        R.font.monoton,
        R.font.sedgwick_ave_display,
        R.font.splash
    )
    
    // Neon colors for cycling
    private val NEON_COLORS = listOf(
        Color.parseColor("#FFFF66"), // Yellow
        Color.parseColor("#BF7EFF"), // Purple
        Color.parseColor("#98FB98"), // Green
        Color.parseColor("#66B2FF"), // Blue
        Color.parseColor("#FFA366")  // Orange
    )
    
    
    // ViewModels
    private lateinit var sessionStatsVM: SessionStatsViewModel
    private lateinit var stashViewModel: StashViewModel
    
    // Services
    private lateinit var goalService: GoalService
    
    // Database
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var repository: ActivityRepository
    private lateinit var vibrator: Vibrator
    private var vibrationEnabled = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make full screen
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        // Create layout programmatically for maximum control
        createLayout()
        
        // Initialize
        initializeComponents()
        loadCurrentData()
    }
    
    private fun createLayout() {
        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            clipChildren = false  // Allow children to render outside bounds
            clipToPadding = false
        }
        
        // Background image
        backgroundImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.section_background_collapsed) // Use existing background
        }
        rootLayout.addView(backgroundImage)
        
        // Undo and Rewind buttons container at top (initially hidden)
        topButtonsContainer = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 50.dpToPx()
            }
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        
        // Rewind button
        btnRewind = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                32.dpToPx()
            ).apply {
                marginEnd = 8.dpToPx()
            }
            text = "Rewind"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@GiantCounterActivity, R.color.design_default_color_secondary))
            gravity = Gravity.CENTER
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 4.dpToPx().toFloat()
                setStroke(1.dpToPx(), ContextCompat.getColor(this@GiantCounterActivity, R.color.design_default_color_secondary))
                setColor(Color.TRANSPARENT)
            }
            isClickable = true
            isFocusable = true
        }
        topButtonsContainer.addView(btnRewind)
        
        // Skip button
        btnSkip = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                32.dpToPx()
            ).apply {
                marginEnd = 8.dpToPx()
            }
            text = "Skip"
            textSize = 12f
            setTextColor(Color.parseColor("#FF91A4"))  // Neon candy color
            gravity = Gravity.CENTER
            setPadding(16.dpToPx(), 4.dpToPx(), 16.dpToPx(), 4.dpToPx())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16.dpToPx().toFloat()
                setStroke(1.dpToPx(), Color.parseColor("#FF91A4"))
                setColor(Color.TRANSPARENT)
            }
            isClickable = true
            isFocusable = true
            visibility = View.VISIBLE
        }
        topButtonsContainer.addView(btnSkip)
        
        // Undo button
        btnUndo = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                32.dpToPx()
            )
            text = "Undo"
            textSize = 12f
            setTextColor(Color.parseColor("#FFA500"))  // Orange color for undo
            gravity = Gravity.CENTER
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 4.dpToPx().toFloat()
                setStroke(1.dpToPx(), Color.parseColor("#FFA500"))
                setColor(Color.TRANSPARENT)
            }
            isClickable = true
            isFocusable = true
            visibility = if (activityHistory.isNotEmpty()) View.VISIBLE else View.GONE
        }
        topButtonsContainer.addView(btnUndo)
        
        rootLayout.addView(topButtonsContainer)
        
        // Timer controls container (initially hidden) - positioned below buttons
        timerControlsContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 100.dpToPx()  // Position below the undo/rewind buttons
            }
            visibility = View.GONE
        }
        
        // Create timer controls layout
        val timerLayout = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
        }
        
        // Timer row
        val timerRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        // Left timer (time since last)
        val leftTimerContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            gravity = Gravity.CENTER
        }
        textTimeSinceLast = TextView(this).apply {
            text = "0s"
            textSize = 24f
            setTextColor(Color.parseColor("#98FB98"))
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        leftTimerContainer.addView(textTimeSinceLast)
        
        // Middle timer (gap countdown)
        val middleTimerContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            gravity = Gravity.CENTER
        }
        textLastGapCountdown = TextView(this).apply {
            text = "0s"
            textSize = 24f
            setTextColor(Color.parseColor("#98FB98"))
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        middleTimerContainer.addView(textLastGapCountdown)
        
        // Right timer (session time)
        val rightTimerContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            gravity = Gravity.CENTER
        }
        textThisSesh = TextView(this).apply {
            text = "00:00:00"
            textSize = 24f
            setTextColor(Color.parseColor("#98FB98"))
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        rightTimerContainer.addView(textThisSesh)
        
        timerRow.addView(leftTimerContainer)
        timerRow.addView(middleTimerContainer)
        timerRow.addView(rightTimerContainer)
        timerLayout.addView(timerRow)
        
        // Rounds counter row
        roundsContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dpToPx()
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        btnRoundMinus = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                48.dpToPx(), 48.dpToPx()
            )
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(Color.parseColor("#98FB98"))
            isClickable = true
            isFocusable = true
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
        }
        
        textRoundsLeft = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 16.dpToPx()
                marginEnd = 16.dpToPx()
            }
            text = "0"
            textSize = 24f
            setTextColor(Color.parseColor("#98FB98"))
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        
        btnRoundPlus = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                48.dpToPx(), 48.dpToPx()
            )
            setImageResource(android.R.drawable.ic_media_next)
            setColorFilter(Color.parseColor("#98FB98"))
            isClickable = true
            isFocusable = true
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
        }
        
        roundsContainer.addView(btnRoundMinus)
        roundsContainer.addView(textRoundsLeft)
        roundsContainer.addView(btnRoundPlus)
        timerLayout.addView(roundsContainer)
        
        timerControlsContainer.addView(timerLayout)
        rootLayout.addView(timerControlsContainer)
        
        // Smoker name above button
        smokerNameText = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                bottomMargin = 240.dpToPx() // Moved up by ~0.5cm (20dp) to prevent accidental button presses
            }
            text = "Loading..."
            textSize = 48f  // Increased from 36f to 48f
            // Font color and typeface will be loaded from preferences
            setTextColor(smokerFontColor)
            setShadowLayer(6f, 3f, 3f, Color.BLACK)  // Increased shadow for bigger text
            typeface = smokerFontTypeface ?: android.graphics.Typeface.DEFAULT_BOLD
        }
        rootLayout.addView(smokerNameText)
        
        // Giant button container (for layering)
        val buttonContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                400.dpToPx(), // 400dp width
                400.dpToPx()  // 400dp height
            ).apply {
                gravity = Gravity.CENTER
            }
            clipChildren = false  // Allow text to render outside container bounds
            clipToPadding = false
        }
        
        // Giant button image
        giantButton = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageResource(R.drawable.giant_button_normal) // Use giant_button_normal.png
            isClickable = true
            isFocusable = true
        }
        buttonContainer.addView(giantButton)
        
        // Counter text on button
        counterText = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            text = "0"
            textSize = 96f
            setTextColor(Color.WHITE)
            setShadowLayer(8f, 4f, 4f, Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        buttonContainer.addView(counterText)
        
        // Recent stats at bottom of button
        recentStatsText = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (-10).dpToPx()  // Adjust this value to move text up/down (higher value = higher position)
            }
            text = ""
            textSize = 20f
            // Use same color and font as smokerNameText
            setTextColor(smokerFontColor)
            typeface = smokerFontTypeface ?: android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        buttonContainer.addView(recentStatsText)
        
        rootLayout.addView(buttonContainer)
        
        // Bottom controls container
        val bottomControlsContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                150.dpToPx()
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }
        
        // Calculator button (toggle top section) - REMOVED per user request
        
        // Advanced button above back button
        advancedButton = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 120.dpToPx()
            }
            text = "Advanced"
            textSize = 12f
            setTextColor(Color.parseColor("#98FB98"))
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
            setPadding(30.dpToPx(), 10.dpToPx(), 30.dpToPx(), 10.dpToPx())
            // Create a neon green outline programmatically
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 8.dpToPx().toFloat()
                setStroke(2.dpToPx(), Color.parseColor("#98FB98"))
                setColor(Color.TRANSPARENT)
            }
            isClickable = true
            isFocusable = true
        }
        bottomControlsContainer.addView(advancedButton)
        
        // Back button at bottom
        backButton = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 60.dpToPx()
            }
            text = "BACK"
            textSize = 18f
            setTextColor(Color.parseColor("#98FB98"))
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
            setPadding(30.dpToPx(), 15.dpToPx(), 30.dpToPx(), 15.dpToPx())
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
        }
        bottomControlsContainer.addView(backButton)
        
        rootLayout.addView(bottomControlsContainer)
        
        // Konfetti view for effects
        konfettiView = KonfettiView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(konfettiView)
        
        setContentView(rootLayout)
    }
    
    private fun initializeComponents() {
        Log.d(TAG, "$LOG_PREFIX initializeComponents() started")
        
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        val database = com.sam.cloudcounter.AppDatabase.getDatabase(this)
        repository = ActivityRepository(
            database.activityLogDao(),
            database.smokerDao(),
            database.sessionSummaryDao(),
            database.stashDao()
        )
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        
        // Load preferences and session data
        val prefs = getSharedPreferences("sesh", MODE_PRIVATE)
        vibrationEnabled = prefs.getBoolean("vibration_enabled", true)
        currentSmoker = prefs.getString("selected_smoker", "Sam") ?: "Sam"
        val savedSessionStart = prefs.getLong("sessionStart", 0L)
        val sessionActive = prefs.getBoolean("sessionActive", false)
        isAutoMode = prefs.getBoolean("is_auto_mode", true)
        timerEnabled = prefs.getBoolean("timer_enabled", false)
        currentActivityType = prefs.getString("current_activity_type", "cones") ?: "cones"
        
        // Load font and lock preferences
        colorChangingEnabled = prefs.getBoolean("color_changing_enabled", true)
        randomFontsEnabled = prefs.getBoolean("random_fonts_enabled", true)
        
        Log.d(TAG, "$LOG_PREFIX 🔓 Initial lock states loaded:")
        Log.d(TAG, "$LOG_PREFIX   colorChangingEnabled: $colorChangingEnabled")
        Log.d(TAG, "$LOG_PREFIX   randomFontsEnabled: $randomFontsEnabled")
        
        // Load global locked states
        val savedGlobalColor = prefs.getInt("global_locked_color", -1)
        if (savedGlobalColor != -1 && !colorChangingEnabled) {
            globalLockedColor = savedGlobalColor
        }
        
        val savedFontIndex = prefs.getInt("global_font_index", -1)
        if (savedFontIndex != -1 && !randomFontsEnabled) {
            lastSelectedFontIndex = savedFontIndex  // Track the global locked font index
            try {
                globalLockedFont = ResourcesCompat.getFont(this, fontList.getOrElse(savedFontIndex) { R.font.sedgwick_ave_display })
            } catch (e: Exception) {
                Log.e(TAG, "$LOG_PREFIX Error loading font", e)
            }
        }
        
        // Get current spinner's actual font and color
        val spinnerFontIndex = prefs.getInt("current_spinner_font_index", -1)
        val spinnerColor = prefs.getInt("current_spinner_color", -1)
        
        Log.d(TAG, "$LOG_PREFIX 🎨 Spinner state from prefs:")
        Log.d(TAG, "$LOG_PREFIX   spinnerFontIndex: $spinnerFontIndex")
        Log.d(TAG, "$LOG_PREFIX   spinnerColor: $spinnerColor")
        Log.d(TAG, "$LOG_PREFIX   colorChangingEnabled: $colorChangingEnabled")
        Log.d(TAG, "$LOG_PREFIX   randomFontsEnabled: $randomFontsEnabled")
        
        // Determine color - ALWAYS use spinner color if available
        smokerFontColor = when {
            spinnerColor != -1 -> {
                // Always use spinner color as it represents the current state
                Log.d(TAG, "$LOG_PREFIX 📱 Using spinner color: $spinnerColor")
                spinnerColor
            }
            !colorChangingEnabled && globalLockedColor != null && globalLockedColor != -1 -> {
                // Fallback to locked color if no spinner color
                Log.d(TAG, "$LOG_PREFIX 🔒 Using locked color: $globalLockedColor")
                globalLockedColor!!
            }
            else -> {
                // Default fallback
                Color.parseColor("#98FB98")
            }
        }
        
        // Determine font - ALWAYS use spinner font if available
        smokerFontTypeface = when {
            spinnerFontIndex != -1 && spinnerFontIndex < fontList.size -> {
                // Always use spinner font as it represents the current state
                lastSelectedFontIndex = spinnerFontIndex  // Track the current font index
                try {
                    val font = ResourcesCompat.getFont(this, fontList[spinnerFontIndex])
                    Log.d(TAG, "$LOG_PREFIX 📱 Using spinner font index: $spinnerFontIndex")
                    font
                } catch (e: Exception) {
                    Log.e(TAG, "$LOG_PREFIX Error loading spinner font", e)
                    android.graphics.Typeface.DEFAULT_BOLD
                }
            }
            !randomFontsEnabled && globalLockedFont != null -> {
                // Fallback to locked font if no spinner font
                Log.d(TAG, "$LOG_PREFIX 🔒 Using locked font")
                globalLockedFont
            }
            else -> {
                // Default fallback
                android.graphics.Typeface.DEFAULT_BOLD
            }
        }
        
        // Apply font settings to smoker name
        smokerNameText.setTextColor(smokerFontColor)
        smokerNameText.typeface = smokerFontTypeface
        // Also update recent stats text to match
        recentStatsText.setTextColor(smokerFontColor)
        recentStatsText.typeface = smokerFontTypeface
        
        Log.d(TAG, "$LOG_PREFIX 🔒 Lock states loaded:")
        Log.d(TAG, "$LOG_PREFIX   colorChangingEnabled: $colorChangingEnabled")
        Log.d(TAG, "$LOG_PREFIX   randomFontsEnabled: $randomFontsEnabled")
        Log.d(TAG, "$LOG_PREFIX   globalLockedColor: $globalLockedColor")
        Log.d(TAG, "$LOG_PREFIX   globalLockedFont: ${globalLockedFont != null}")
        Log.d(TAG, "$LOG_PREFIX   Current smoker color: $smokerFontColor")
        Log.d(TAG, "$LOG_PREFIX   Current smoker font: ${smokerFontTypeface?.javaClass?.simpleName}")
        
        // Load timer data from preferences
        lastLogTime = prefs.getLong("lastLogTime", 0L)
        lastConeTimestamp = prefs.getLong("lastConeTimestamp", 0L)
        lastJointTimestamp = prefs.getLong("lastJointTimestamp", 0L)
        lastBowlTimestamp = prefs.getLong("lastBowlTimestamp", 0L)
        roundsLeft = prefs.getInt("roundsLeft", 0)
        initialRoundsSet = prefs.getInt("initialRoundsSet", 0)
        rewindOffset = prefs.getLong("rewindOffset", 0L)
        
        // Load activity timestamps for gap calculation
        loadActivityTimestamps()
        
        // Load activity history for undo functionality
        loadActivityHistory()
        
        Log.d(TAG, "$LOG_PREFIX 🕐 Timer data loaded:")
        Log.d(TAG, "$LOG_PREFIX   lastLogTime: $lastLogTime")
        Log.d(TAG, "$LOG_PREFIX   activitiesTimestamps: ${activitiesTimestamps.size} entries")
        Log.d(TAG, "$LOG_PREFIX   roundsLeft: $roundsLeft")
        Log.d(TAG, "$LOG_PREFIX   initialRoundsSet: $initialRoundsSet")
        
        // Load stash source from preferences
        val stashSourceString = prefs.getString("stash_source", "MY_STASH") ?: "MY_STASH"
        val savedStashSource = when (stashSourceString) {
            "THEIR_STASH" -> StashSource.THEIR_STASH
            "EACH_TO_OWN" -> StashSource.EACH_TO_OWN
            else -> StashSource.MY_STASH
        }
        
        // Use saved session start if valid, otherwise try to find from recent activities
        sessionStart = if (savedSessionStart > 0 && sessionActive) {
            savedSessionStart
        } else {
            // Fallback: Find the earliest activity timestamp from today's session
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            Log.d(TAG, "$LOG_PREFIX No valid sessionStart in prefs, using today's start: $todayStart")
            todayStart
        }
        
        Log.d(TAG, "$LOG_PREFIX Loaded prefs:")
        Log.d(TAG, "$LOG_PREFIX   currentSmoker: $currentSmoker")
        Log.d(TAG, "$LOG_PREFIX   savedSessionStart: $savedSessionStart")
        Log.d(TAG, "$LOG_PREFIX   sessionActive: $sessionActive")
        Log.d(TAG, "$LOG_PREFIX   sessionStart (using): $sessionStart")
        Log.d(TAG, "$LOG_PREFIX   isAutoMode: $isAutoMode")
        Log.d(TAG, "$LOG_PREFIX   currentActivityType: $currentActivityType")
        Log.d(TAG, "$LOG_PREFIX   stashSource: $savedStashSource (from '$stashSourceString')")
        
        // Initialize ViewModels
        val factory = SessionStatsViewModelFactory()
        sessionStatsVM = ViewModelProvider(this, factory).get(SessionStatsViewModel::class.java)
        stashViewModel = ViewModelProvider(this).get(StashViewModel::class.java)
        
        // Set the stash source from preferences
        stashViewModel.updateStashSource(savedStashSource)
        Log.d(TAG, "$LOG_PREFIX Setting stash source to: $savedStashSource")
        
        // Initialize services
        goalService = GoalService(application)
        
        // Observe session stats for real-time updates
        sessionStatsVM.perSmokerStats.observe(this) { stats ->
            Log.d(TAG, "$LOG_PREFIX Session stats updated: ${stats.size} smokers")
            stats.forEach { stat ->
                Log.d(TAG, "$LOG_PREFIX   ${stat.smokerName}: Cones=${stat.totalCones}, Joints=${stat.totalJoints}, Bowls=${stat.totalBowls}")
            }
            updateFromSessionStats(stats)
        }
        
        // Set up button listeners
        setupButtonListeners()
        
        // Start timer updates
        startTimerUpdates()
    }
    
    @Suppress("ClickableViewAccessibility")
    private fun setupButtonListeners() {
        // Giant button press handling
        giantButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Change to pressed state
                    giantButton.setImageResource(R.drawable.giant_button_pressed)
                    // Scale down slightly for feedback
                    giantButton.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .start()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Change back to normal state
                    giantButton.setImageResource(R.drawable.giant_button_normal)
                    // Scale back to normal
                    giantButton.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                    
                    if (event.action == MotionEvent.ACTION_UP) {
                        // Increment counter and save activity
                        incrementCounter()
                        // After saving, rotate to next smoker if in auto mode
                        if (isAutoMode) {
                            // Add a small delay to ensure save completes
                            Handler(Looper.getMainLooper()).postDelayed({
                                rotateSmoker()
                            }, 100)
                        }
                    }
                    true
                }
                else -> false
            }
        }
        
        // Back button
        backButton.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        
        // Long press on smoker name to change font
        setupSmokerNameLongPress()
        
        // Advanced button (replaces timer button)
        advancedButton.setOnClickListener {
            toggleTimersVisibility()
        }
        
        // Undo button
        btnUndo.setOnClickListener {
            undoLastActivity()
        }
        
        // Rewind button
        btnRewind.setOnClickListener {
            rewindTime()
        }
        
        // Skip button
        btnSkip.setOnClickListener {
            skipToNextSmoker()
        }
        
        // Round buttons
        btnRoundMinus.setOnClickListener {
            if (initialRoundsSet > 0) {
                roundsLeft = maxOf(0, roundsLeft - 1)
                updateRoundsUI()
                saveRoundsToPrefs()
            }
        }
        
        btnRoundPlus.setOnClickListener {
            if (initialRoundsSet == 0) {
                // From infinity to 1
                initialRoundsSet = 1
                roundsLeft = 1
            } else {
                roundsLeft++
            }
            updateRoundsUI()
            saveRoundsToPrefs()
        }
        
        // Calculator button (toggle top section) - REMOVED
    }
    
    private fun loadCurrentData() {
        Log.d(TAG, "$LOG_PREFIX loadCurrentData() called")
        
        lifecycleScope.launch {
            try {
                val smokerDao = com.sam.cloudcounter.AppDatabase.getDatabase(this@GiantCounterActivity).smokerDao()
                
                // Get all smokers
                allSmokers = withContext(Dispatchers.IO) {
                    smokerDao.getAllSmokersList()
                }
                
                // Find current smoker index
                currentSmokerIndex = allSmokers.indexOfFirst { it.name == currentSmoker }
                if (currentSmokerIndex == -1) currentSmokerIndex = 0
                
                // Get current smoker
                val currentSmokerObj = allSmokers.getOrNull(currentSmokerIndex)
                
                if (currentSmokerObj != null) {
                    // Get most recent activity for this smoker
                    val recentActivity = withContext(Dispatchers.IO) {
                        repository.getLastActivityForSmoker(currentSmokerObj.smokerId)
                    }
                    
                    if (recentActivity != null) {
                        currentActivityType = when (recentActivity.type) {
                            com.sam.cloudcounter.ActivityType.CONE -> "cones"
                            com.sam.cloudcounter.ActivityType.JOINT -> "joints"
                            com.sam.cloudcounter.ActivityType.BOWL -> "bowls"
                            com.sam.cloudcounter.ActivityType.SESSION_SUMMARY -> "cones" // Default for session
                        }
                        
                        // Get the smoker who made this activity
                        val recentSmokerObj = withContext(Dispatchers.IO) {
                            smokerDao.getSmokerById(recentActivity.smokerId)
                        }
                        recentSmoker = recentSmokerObj?.name ?: ""
                    } else {
                        currentActivityType = "cones"
                    }
                    
                    // Get session activities (from session start time)
                    val sessionActivities = withContext(Dispatchers.IO) {
                        repository.getLogsInTimeRange(sessionStart, System.currentTimeMillis())
                    }
                    
                    // Count activities for current smoker and type
                    val activityType = when (currentActivityType) {
                        "cones" -> com.sam.cloudcounter.ActivityType.CONE
                        "joints" -> com.sam.cloudcounter.ActivityType.JOINT
                        "bowls" -> com.sam.cloudcounter.ActivityType.BOWL
                        else -> com.sam.cloudcounter.ActivityType.CONE
                    }
                    
                    currentCount = sessionActivities.count { 
                        it.smokerId == currentSmokerObj.smokerId && it.type == activityType 
                    }
                    
                    Log.d(TAG, "$LOG_PREFIX Loaded count for ${currentSmokerObj.name}: $currentCount $currentActivityType")
                    
                    // Load counts for all smokers to have accurate data
                    Log.d(TAG, "$LOG_PREFIX Loading counts for all smokers:")
                    allSmokers.forEach { smoker ->
                        val smokerCones = sessionActivities.count { 
                            it.smokerId == smoker.smokerId && it.type == ActivityType.CONE
                        }
                        val smokerJoints = sessionActivities.count { 
                            it.smokerId == smoker.smokerId && it.type == ActivityType.JOINT
                        }
                        val smokerBowls = sessionActivities.count { 
                            it.smokerId == smoker.smokerId && it.type == ActivityType.BOWL
                        }
                        Log.d(TAG, "$LOG_PREFIX   ${smoker.name}: C=$smokerCones, J=$smokerJoints, B=$smokerBowls")
                    }
                    
                    // Get recent smoker's count if different
                    if (recentSmoker.isNotEmpty() && recentSmoker != currentSmoker) {
                        val recentSmokerObj = withContext(Dispatchers.IO) {
                            smokerDao.getSmokerByName(recentSmoker)
                        }
                        if (recentSmokerObj != null) {
                            recentSmokerCount = sessionActivities.count {
                                it.smokerId == recentSmokerObj.smokerId && it.type == activityType
                            }
                        }
                    } else {
                        recentSmokerCount = currentCount
                    }
                } else {
                    // Default values
                    currentActivityType = "cones"
                    currentCount = 0
                }
                
                updateUI()
                updateRoundsUI()
                
                // Refresh session stats to populate the ViewModel
                refreshSessionStats()
                
            } catch (e: Exception) {
                Log.e(TAG, "$LOG_PREFIX Error loading data", e)
                // Use defaults
                currentActivityType = "cones"
                currentCount = 0
                updateUI()
            }
        }
    }
    
    private fun updateUI() {
        smokerNameText.text = currentSmoker
        counterText.text = currentCount.toString()
        
        // Update font and color if unlocked (when rotating smokers)
        if (randomFontsEnabled) {
            // Generate new random font for new smoker
            val randomIndex = (0 until fontList.size).random()
            lastSelectedFontIndex = randomIndex
            try {
                val randomFont = ResourcesCompat.getFont(this, fontList[randomIndex])
                smokerNameText.typeface = randomFont
                recentStatsText.typeface = randomFont
                smokerFontTypeface = randomFont
                Log.d(TAG, "$LOG_PREFIX 🔤 Rotation: new random font index $randomIndex")
            } catch (e: Exception) {
                Log.e(TAG, "$LOG_PREFIX Error loading random font during rotation", e)
            }
        }
        
        if (colorChangingEnabled) {
            // Generate new random color for new smoker
            val randomColor = NEON_COLORS.random()
            smokerNameText.setTextColor(randomColor)
            recentStatsText.setTextColor(randomColor)
            smokerFontColor = randomColor
            Log.d(TAG, "$LOG_PREFIX 🎨 Rotation: new random color $randomColor")
        }
        
        // Save the new display state
        saveDisplayState()
        
        if (recentSmoker.isNotEmpty() && recentSmoker != currentSmoker) {
            recentStatsText.text = "$recentSmoker: $recentSmokerCount ${currentActivityType}"
        } else {
            recentStatsText.text = "${currentActivityType.capitalize()}"
        }
    }
    
    private fun incrementCounter() {
        Log.d(TAG, "$LOG_PREFIX 🎯 incrementCounter() called")
        Log.d(TAG, "$LOG_PREFIX 🎯 Current smoker: $currentSmoker")
        Log.d(TAG, "$LOG_PREFIX 🎯 Activity type: $currentActivityType")
        Log.d(TAG, "$LOG_PREFIX 🎯 Count before: $currentCount")
        Log.d(TAG, "$LOG_PREFIX 🌿 Stash will be updated")
        Log.d(TAG, "$LOG_PREFIX 🎯 Goals will be tracked")
        
        currentCount++
        counterText.text = currentCount.toString()
        
        Log.d(TAG, "$LOG_PREFIX   Count after: $currentCount")
        
        // Vibrate if enabled
        if (vibrationEnabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
        
        // Show confetti
        showConfetti()
        
        // Save to database BEFORE rotating (so it saves to the correct smoker)
        saveActivity()
    }
    
    private fun showConfetti() {
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(
                Color.parseColor("#98FB98"),
                Color.parseColor("#00FF40"),
                Color.parseColor("#50C878"),
                Color.parseColor("#90EE90")
            ),
            shapes = listOf(Shape.Circle, Shape.Square),
            size = listOf(Size.SMALL, Size.MEDIUM),
            timeToLive = 2000L,
            fadeOutEnabled = true,
            position = Position.Relative(0.5, 0.5),
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(30)
        )
        
        konfettiView.start(party)
    }
    
    private fun saveActivity() {
        Log.d(TAG, "$LOG_PREFIX saveActivity() called")
        
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Get smoker ID
                    val smokerDao = com.sam.cloudcounter.AppDatabase.getDatabase(this@GiantCounterActivity).smokerDao()
                    val smoker = smokerDao.getSmokerByName(currentSmoker) ?: smokerDao.getAllSmokersList().firstOrNull()
                    
                    Log.d(TAG, "$LOG_PREFIX Found smoker: ${smoker?.name} with ID: ${smoker?.smokerId}")
                    
                    if (smoker != null) {
                        val activityType = when (currentActivityType) {
                            "cones" -> com.sam.cloudcounter.ActivityType.CONE
                            "joints" -> com.sam.cloudcounter.ActivityType.JOINT
                            "bowls" -> com.sam.cloudcounter.ActivityType.BOWL
                            else -> com.sam.cloudcounter.ActivityType.CONE
                        }
                        
                        Log.d(TAG, "$LOG_PREFIX Creating activity: type=$activityType, smokerId=${smoker.smokerId}")
                        
                        // Determine stash source and payer
                        val stashSource = stashViewModel.stashSource.value ?: StashSource.MY_STASH
                        val payerStashOwnerId = when (stashSource) {
                            StashSource.MY_STASH -> {
                                Log.d(TAG, "$LOG_PREFIX 💰 Using MY_STASH - payerStashOwnerId = null")
                                null
                            }
                            StashSource.THEIR_STASH -> {
                                Log.d(TAG, "$LOG_PREFIX 💰 Using THEIR_STASH - payerStashOwnerId = 'their_stash'")
                                "their_stash"
                            }
                            StashSource.EACH_TO_OWN -> {
                                // In Giant Counter, we don't have cloud user context, so default to null
                                Log.d(TAG, "$LOG_PREFIX 💰 Using EACH_TO_OWN - defaulting to MY_STASH")
                                null
                            }
                        }
                        
                        // Get current ratios for grams calculation
                        val ratios = stashViewModel.ratios.value
                        val gramsForActivity = when (activityType) {
                            com.sam.cloudcounter.ActivityType.CONE -> ratios?.coneGrams ?: 0.3
                            com.sam.cloudcounter.ActivityType.JOINT -> ratios?.jointGrams ?: 0.5
                            com.sam.cloudcounter.ActivityType.BOWL -> ratios?.bowlGrams ?: 0.2
                            else -> 0.3
                        }
                        
                        val currentStash = stashViewModel.currentStash.value
                        val pricePerGram = currentStash?.pricePerGram ?: 0.0
                        
                        Log.d(TAG, "$LOG_PREFIX 💰 Activity will consume ${gramsForActivity}g at $${pricePerGram}/g")
                        
                        val activity = com.sam.cloudcounter.ActivityLog(
                            smokerId = smoker.smokerId,
                            consumerId = smoker.smokerId,
                            payerStashOwnerId = payerStashOwnerId,
                            type = activityType,
                            timestamp = System.currentTimeMillis(),
                            sessionStartTime = sessionStart,
                            gramsAtLog = gramsForActivity,
                            pricePerGramAtLog = pricePerGram
                        )
                        
                        val insertedId = repository.insert(activity)
                        Log.d(TAG, "$LOG_PREFIX Activity inserted with ID: $insertedId")
                        
                        // Track activity for undo
                        activityHistory.add(insertedId)
                        // Update undo button visibility
                        btnUndo.visibility = View.VISIBLE
                        
                        // Update timer data
                        val currentTime = System.currentTimeMillis()
                        if (lastLogTime > 0) {
                            val interval = currentTime - lastLogTime
                            intervalsList.add(interval)
                            // Keep only last 20 intervals
                            if (intervalsList.size > 20) {
                                intervalsList.removeAt(0)
                            }
                        }
                        lastLogTime = currentTime
                        
                        // Add to activity timestamps list
                        activitiesTimestamps.add(currentTime)
                        Log.d(TAG, "$LOG_PREFIX 🕐 Added timestamp: $currentTime, total: ${activitiesTimestamps.size}")
                        
                        // Update activity type timestamps
                        when (activityType) {
                            com.sam.cloudcounter.ActivityType.CONE -> lastConeTimestamp = currentTime
                            com.sam.cloudcounter.ActivityType.JOINT -> lastJointTimestamp = currentTime
                            com.sam.cloudcounter.ActivityType.BOWL -> lastBowlTimestamp = currentTime
                            else -> {}
                        }
                        
                        // Update rounds if needed
                        if (roundsLeft > 0) {
                            roundsLeft--
                            updateRoundsUI()
                        }
                        
                        // Save timer data to preferences
                        saveTimerDataToPrefs()
                        
                        // UPDATE STASH
                        withContext(Dispatchers.Main) {
                            Log.d(TAG, "$LOG_PREFIX 🌿 Updating stash for activity type: $activityType")
                            stashViewModel.onActivityLogged(activityType)
                        }
                        
                        // UPDATE GOALS
                        withContext(Dispatchers.Main) {
                            Log.d(TAG, "$LOG_PREFIX 🎯 Updating goals for ${smoker.name}, type: $activityType")
                            val prefs = getSharedPreferences("sesh", MODE_PRIVATE)
                            val sessionActive = prefs.getBoolean("sessionActive", false)
                            val currentShareCode = if (sessionActive) prefs.getString("currentShareCode", null) else null
                            
                            try {
                                goalService.updateGoalProgressForActivity(
                                    activityType,
                                    currentShareCode,
                                    smoker.name
                                )
                                Log.d(TAG, "$LOG_PREFIX 🎯 Goal update completed successfully")
                            } catch (e: Exception) {
                                Log.e(TAG, "$LOG_PREFIX 🎯 Error updating goals: ${e.message}", e)
                            }
                        }
                        
                        // Force refresh of session stats
                        val sessionActivities = repository.getLogsInTimeRange(sessionStart, System.currentTimeMillis())
                        Log.d(TAG, "$LOG_PREFIX Total session activities: ${sessionActivities.size}")
                        
                        val smokerActivities = sessionActivities.filter { it.smokerId == smoker.smokerId }
                        Log.d(TAG, "$LOG_PREFIX Activities for ${smoker.name}: ${smokerActivities.size}")
                        
                        smokerActivities.groupBy { it.type }.forEach { (type, activities) ->
                            Log.d(TAG, "$LOG_PREFIX   $type: ${activities.size}")
                        }
                        
                        // Manually refresh session stats to update UI
                        refreshSessionStats()
                    } else {
                        Log.e(TAG, "$LOG_PREFIX No smoker found for name: $currentSmoker")
                    }
                }
                
                // Update recent smoker
                recentSmoker = currentSmoker
                recentSmokerCount = currentCount
                updateUI()
                
            } catch (e: Exception) {
                Log.e(TAG, "$LOG_PREFIX Error saving activity", e)
            }
        }
    }
    
    private fun rotateSmoker() {
        Log.d(TAG, "$LOG_PREFIX 🔄 rotateSmoker() called")
        if (allSmokers.size <= 1) {
            Log.d(TAG, "$LOG_PREFIX 🔄 Only one smoker, no rotation needed")
            return
        }
        
        // Save previous smoker as recent
        recentSmoker = currentSmoker
        recentSmokerCount = currentCount
        
        // Move to next smoker
        currentSmokerIndex = (currentSmokerIndex + 1) % allSmokers.size
        val nextSmoker = allSmokers[currentSmokerIndex]
        currentSmoker = nextSmoker.name
        
        Log.d(TAG, "$LOG_PREFIX 🔄 Rotated from $recentSmoker to $currentSmoker")
        
        // Load new smoker's count for this session
        lifecycleScope.launch {
            try {
                val sessionActivities = withContext(Dispatchers.IO) {
                    repository.getLogsInTimeRange(sessionStart, System.currentTimeMillis())
                }
                
                val activityType = when (currentActivityType) {
                    "cones" -> com.sam.cloudcounter.ActivityType.CONE
                    "joints" -> com.sam.cloudcounter.ActivityType.JOINT
                    else -> com.sam.cloudcounter.ActivityType.CONE
                }
                
                currentCount = sessionActivities.count {
                    it.smokerId == nextSmoker.smokerId && it.type == activityType
                }
                
                updateUI()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading smoker data", e)
                currentCount = 0
                updateUI()
            }
        }
    }
    
    private fun refreshSessionStats() {
        Log.d(TAG, "$LOG_PREFIX refreshSessionStats() called")
        
        lifecycleScope.launch {
            try {
                val sessionActivities = withContext(Dispatchers.IO) {
                    repository.getLogsInTimeRange(sessionStart, System.currentTimeMillis())
                }
                
                Log.d(TAG, "$LOG_PREFIX Found ${sessionActivities.size} activities in session")
                
                // Calculate per-smoker stats
                val perSmokerMap = mutableMapOf<String, PerSmokerStats>()
                var totalCones = 0
                var totalJoints = 0
                var totalBowls = 0
                
                // Group activities by smoker
                sessionActivities.groupBy { it.smokerId }.forEach { (smokerId, activities) ->
                    // Get smoker name
                    val smoker = allSmokers.find { it.smokerId == smokerId }
                    if (smoker != null) {
                        val cones = activities.count { it.type == ActivityType.CONE }
                        val joints = activities.count { it.type == ActivityType.JOINT }
                        val bowls = activities.count { it.type == ActivityType.BOWL }
                        
                        totalCones += cones
                        totalJoints += joints
                        totalBowls += bowls
                        
                        perSmokerMap[smoker.name] = PerSmokerStats(
                            smokerName = smoker.name,
                            totalCones = cones,
                            totalJoints = joints,
                            totalBowls = bowls
                        )
                        
                        Log.d(TAG, "$LOG_PREFIX ${smoker.name}: C=$cones, J=$joints, B=$bowls")
                    }
                }
                
                // Update the ViewModel
                val statsList = perSmokerMap.values.toList()
                // Note: We can't directly set _perSmokerStats as it's private
                // The stats will be updated through the observer pattern
                
                // Update group stats
                sessionStatsVM.updateGroupStats(GroupStats(
                    totalCones = totalCones,
                    totalJoints = totalJoints,
                    totalBowls = totalBowls
                ))
                
                Log.d(TAG, "$LOG_PREFIX Session stats updated: Total C=$totalCones, J=$totalJoints, B=$totalBowls")
                
            } catch (e: Exception) {
                Log.e(TAG, "$LOG_PREFIX Error refreshing session stats", e)
            }
        }
    }
    
    private fun updateFromSessionStats(stats: List<PerSmokerStats>) {
        // Find current smoker's stats from session stats
        val currentSmokerStats = stats.find { it.smokerName == currentSmoker }
        if (currentSmokerStats != null) {
            currentCount = when (currentActivityType) {
                "cones" -> currentSmokerStats.totalCones
                "joints" -> currentSmokerStats.totalJoints
                "bowls" -> currentSmokerStats.totalBowls
                else -> currentSmokerStats.totalCones
            }
            updateUI()
        }
    }
    
    private fun toggleTopSection() {
        topSectionVisible = !topSectionVisible
        
        // Animate visibility change
        if (::topControlsContainer.isInitialized) {
            if (topSectionVisible) {
                topControlsContainer.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .withStartAction { topControlsContainer.visibility = View.VISIBLE }
                    .start()
            } else {
                topControlsContainer.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction { topControlsContainer.visibility = View.GONE }
                    .start()
            }
        }
        
        // Update calculator button appearance - REMOVED
    }
    
    private fun toggleTimersVisibility() {
        timersVisible = !timersVisible
        
        if (timersVisible) {
            advancedButton.text = "See Less"
            timerControlsContainer.visibility = View.VISIBLE
            topButtonsContainer.visibility = View.VISIBLE  // Show undo/rewind buttons
        } else {
            advancedButton.text = "Advanced"
            timerControlsContainer.visibility = View.GONE
            topButtonsContainer.visibility = View.GONE  // Hide undo/rewind buttons
        }
    }
    
    private fun updateRoundsUI() {
        val displayText = when {
            initialRoundsSet == 0 -> "∞"
            roundsLeft < 0 -> "0"
            else -> roundsLeft.toString()
        }
        textRoundsLeft.text = displayText
    }
    
    private fun saveRoundsToPrefs() {
        val prefs = getSharedPreferences("sesh", MODE_PRIVATE)
        prefs.edit().apply {
            putInt("roundsLeft", roundsLeft)
            putInt("initialRoundsSet", initialRoundsSet)
            commit()
        }
    }
    
    private fun startTimerUpdates() {
        timerRunnable = object : Runnable {
            override fun run() {
                updateTimers()
                handler.postDelayed(this, 1000) // Update every second
            }
        }
        handler.post(timerRunnable)
    }
    
    private fun updateTimers() {
        val now = System.currentTimeMillis() - rewindOffset  // Apply rewind offset
        
        // LEFT TIMER: Time since last activity
        val sinceLastMs = if (lastLogTime > 0) now - lastLogTime else 0
        val sinceLastSec = sinceLastMs / 1000
        textTimeSinceLast.text = formatInterval(sinceLastSec)
        
        // MIDDLE TIMER: Gap countdown (match MainActivity logic)
        if (activitiesTimestamps.size >= 2) {
            // Calculate the gap between the last two activities
            val sortedTimestamps = activitiesTimestamps.sorted()
            val lastTwo = sortedTimestamps.takeLast(2)
            val gapBetweenLast = lastTwo[1] - lastTwo[0]
            val timeSinceLast = now - lastTwo[1]
            val remainingMs = gapBetweenLast - timeSinceLast
            val remainingSec = remainingMs / 1000
            
            val gapFormatted = if (remainingSec >= 0) {
                formatInterval(remainingSec)
            } else {
                "-${formatInterval(kotlin.math.abs(remainingSec))}"
            }
            textLastGapCountdown.text = gapFormatted
            
            Log.d(TAG, "$LOG_PREFIX 🕐 Middle timer: gap=${gapBetweenLast}ms, remaining=${remainingSec}s")
        } else {
            textLastGapCountdown.text = "0s"
            Log.d(TAG, "$LOG_PREFIX 🕐 Middle timer: Not enough activities (${activitiesTimestamps.size})")
        }
        
        // RIGHT TIMER: Session elapsed time
        val sessionElapsedMs = if (sessionStart > 0) now - sessionStart else 0
        val sessionElapsedSec = sessionElapsedMs / 1000
        textThisSesh.text = formatInterval(sessionElapsedSec)
    }
    
    private fun saveTimerDataToPrefs() {
        val prefs = getSharedPreferences("sesh", MODE_PRIVATE)
        prefs.edit().apply {
            putLong("lastLogTime", lastLogTime)
            putLong("lastConeTimestamp", lastConeTimestamp)
            putLong("lastJointTimestamp", lastJointTimestamp)
            putLong("lastBowlTimestamp", lastBowlTimestamp)
            putInt("roundsLeft", roundsLeft)
            putInt("initialRoundsSet", initialRoundsSet)
            
            // Save activity timestamps as comma-separated string
            val timestampsString = activitiesTimestamps.joinToString(",")
            putString("activitiesTimestamps", timestampsString)
            
            commit()
        }
        
        Log.d(TAG, "$LOG_PREFIX 🕐 Timer data saved: ${activitiesTimestamps.size} timestamps")
    }
    
    private fun loadActivityTimestamps() {
        lifecycleScope.launch {
            try {
                // Load from database all activities from current session
                val sessionActivities = withContext(Dispatchers.IO) {
                    repository.getLogsInTimeRange(sessionStart, System.currentTimeMillis())
                }
                
                // Extract timestamps and sort them
                activitiesTimestamps.clear()
                activitiesTimestamps.addAll(sessionActivities.map { it.timestamp }.sorted())
                
                Log.d(TAG, "$LOG_PREFIX 🕐 Loaded ${activitiesTimestamps.size} activity timestamps from database")
                Log.d(TAG, "$LOG_PREFIX 🕐 Timestamps: ${activitiesTimestamps.takeLast(5)}")
                
                // Also try to load from SharedPreferences as backup
                val prefs = getSharedPreferences("sesh", MODE_PRIVATE)
                val timestampsString = prefs.getString("activitiesTimestamps", null)
                if (timestampsString != null && timestampsString.isNotEmpty()) {
                    val savedTimestamps = timestampsString.split(",").mapNotNull { it.toLongOrNull() }
                    if (savedTimestamps.size > activitiesTimestamps.size) {
                        activitiesTimestamps.clear()
                        activitiesTimestamps.addAll(savedTimestamps.sorted())
                        Log.d(TAG, "$LOG_PREFIX 🕐 Used SharedPrefs timestamps instead: ${activitiesTimestamps.size}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "$LOG_PREFIX 🕐 Error loading activity timestamps", e)
            }
        }
    }
    
    private fun loadActivityHistory() {
        lifecycleScope.launch {
            try {
                // Load activity IDs from current session for undo functionality
                val sessionActivities = withContext(Dispatchers.IO) {
                    repository.getLogsInTimeRange(sessionStart, System.currentTimeMillis())
                }
                
                // Add activity IDs to history (sorted by timestamp)
                activityHistory.clear()
                activityHistory.addAll(sessionActivities.sortedBy { it.timestamp }.map { it.id })
                
                Log.d(TAG, "$LOG_PREFIX 🔙 Loaded ${activityHistory.size} activities into undo history")
                
                // Update undo button visibility
                if (activityHistory.isNotEmpty()) {
                    btnUndo.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "$LOG_PREFIX 🔙 Error loading activity history", e)
            }
        }
    }
    
    private fun formatInterval(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> {
                val mins = seconds / 60
                val secs = seconds % 60
                if (secs == 0L) "${mins}m" else "${mins}m ${secs}s"
            }
            else -> {
                val hours = seconds / 3600
                val mins = (seconds % 3600) / 60
                val secs = seconds % 60
                String.format("%02d:%02d:%02d", hours, mins, secs)
            }
        }
    }
    
    private fun undoLastActivity() {
        if (activityHistory.isEmpty()) {
            Log.d(TAG, "$LOG_PREFIX 🔙 No activities to undo")
            return
        }
        
        lifecycleScope.launch {
            try {
                val lastActivityId = activityHistory.removeAt(activityHistory.size - 1)
                
                // Delete from database
                withContext(Dispatchers.IO) {
                    val dao = com.sam.cloudcounter.AppDatabase.getDatabase(this@GiantCounterActivity).activityLogDao()
                    val activity = dao.getActivityById(lastActivityId)
                    if (activity != null) {
                        dao.delete(activity)
                    }
                }
                
                Log.d(TAG, "$LOG_PREFIX 🔙 Undid activity with ID: $lastActivityId")
                
                // Remove from timestamps list
                if (activitiesTimestamps.isNotEmpty()) {
                    activitiesTimestamps.removeAt(activitiesTimestamps.size - 1)
                }
                
                // Update last log time
                lastLogTime = activitiesTimestamps.lastOrNull() ?: 0L
                
                // Decrement count
                if (currentCount > 0) {
                    currentCount--
                    updateUI()
                }
                
                // Save updated data
                saveTimerDataToPrefs()
                
                // Update undo button visibility
                if (activityHistory.isEmpty()) {
                    btnUndo.visibility = View.GONE
                }
                
                // Refresh session stats
                refreshSessionStats()
                
            } catch (e: Exception) {
                Log.e(TAG, "$LOG_PREFIX 🔙 Error undoing activity", e)
            }
        }
    }
    
    private fun rewindTime() {
        rewindOffset += REWIND_AMOUNT_MS
        Log.d(TAG, "$LOG_PREFIX ⏪ Rewound by ${REWIND_AMOUNT_MS}ms, total offset: $rewindOffset")
        
        // Update timers immediately to reflect rewind
        updateTimers()
        
        // Save the rewind offset
        val prefs = getSharedPreferences("sesh", MODE_PRIVATE)
        prefs.edit().putLong("rewindOffset", rewindOffset).apply()
    }
    
    private fun skipToNextSmoker() {
        Log.d(TAG, "$LOG_PREFIX ⏭️ Skipping to next smoker")
        
        // Broadcast the skip event to MainActivity to handle the rotation
        val intent = Intent("com.sam.cloudcounter.SKIP_SMOKER")
        intent.putExtra("request_skip", true)
        sendBroadcast(intent)
        
        // MainActivity will handle the rotation and update SharedPreferences
        // Then GiantCounterActivity will pick it up from the listener
        
        Log.d(TAG, "$LOG_PREFIX ⏭️ Skip request sent to MainActivity")
    }
    
    override fun onPause() {
        super.onPause()
        // Save current display state for MainActivity to pick up
        saveDisplayState()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
    }
    
    private fun saveDisplayState() {
        val prefs = getSharedPreferences("sesh", MODE_PRIVATE)
        prefs.edit().apply {
            // Save current display values for MainActivity to load
            val currentColor = smokerNameText.currentTextColor
            putInt("giant_counter_color", currentColor)
            Log.d(TAG, "$LOG_PREFIX 💾 Saving display color: $currentColor")
            
            // Use the tracked font index instead of trying to compare typefaces
            putInt("giant_counter_font_index", lastSelectedFontIndex)
            Log.d(TAG, "$LOG_PREFIX 💾 Saving display font index: $lastSelectedFontIndex")
            
            apply()
        }
    }
    
    private fun setupSmokerNameLongPress() {
        var shouldShowDropdown = true
        var wasAbove7Seconds = false
        
        smokerNameText.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    nameHoldStartTime = System.currentTimeMillis()
                    shouldShowDropdown = true
                    wasAbove7Seconds = false
                    
                    Log.d(TAG, "$LOG_PREFIX 👆 Name touch down")
                    
                    nameHoldHandler = Handler(Looper.getMainLooper())
                    nameHoldRunnable = Runnable {
                        val holdDuration = System.currentTimeMillis() - nameHoldStartTime
                        // Get actual current color (not -256)
                        val currentColor = if (smokerNameText.currentTextColor == -256 || smokerNameText.currentTextColor == Color.YELLOW) {
                            // If yellow/default, try to get from preferences or use a neon color
                            val prefs = getSharedPreferences("sesh", MODE_PRIVATE)
                            val savedColor = prefs.getInt("current_spinner_color", -1)
                            if (savedColor != -1 && savedColor != -256) savedColor else NEON_COLORS.random()
                        } else {
                            smokerNameText.currentTextColor
                        }
                        val currentFont = smokerNameText.typeface
                        
                        when {
                            holdDuration >= 7000 -> {
                                // Font cycling mode
                                shouldShowDropdown = false
                                wasAbove7Seconds = true
                                if (fontCycleHandler == null) {
                                    Log.d(TAG, "$LOG_PREFIX 🔤 Starting font cycle at 7s")
                                    fontCycleHandler = Handler(Looper.getMainLooper())
                                    fontCycleRunnable = object : Runnable {
                                        override fun run() {
                                            val nextFont = cycleToNextFont()
                                            globalLockedFont = nextFont
                                            smokerNameText.typeface = nextFont
                                            smokerNameText.setTextColor(globalLockedColor ?: currentColor)
                                            recentStatsText.typeface = nextFont
                                            recentStatsText.setTextColor(globalLockedColor ?: currentColor)
                                            vibrateFeedback(30)
                                            fontCycleHandler?.postDelayed(this, 2000)
                                        }
                                    }
                                    fontCycleHandler?.post(fontCycleRunnable!!)
                                    showToast("Font cycling started (every 2s)")
                                }
                            }
                            holdDuration >= 5000 -> {
                                // Lock both font and color
                                shouldShowDropdown = false
                                toggleFontAndColorLock()
                                if (!randomFontsEnabled) {
                                    globalLockedFont = currentFont
                                    Log.d(TAG, "$LOG_PREFIX 🔒 Locked global font at 5s hold")
                                }
                                if (!colorChangingEnabled) {
                                    globalLockedColor = currentColor
                                    Log.d(TAG, "$LOG_PREFIX 🔒 Locked global color at 5s hold: $currentColor")
                                }
                                saveLockStates()
                                showToast(getFontAndColorLockStatusMessage())
                                vibrateFeedback(50)
                                nameHoldHandler?.postDelayed({ nameHoldRunnable?.run() }, 2000)
                            }
                            holdDuration >= 3000 -> {
                                // Lock font only
                                shouldShowDropdown = false
                                toggleFontLock()
                                if (!randomFontsEnabled) {
                                    globalLockedFont = currentFont
                                    Log.d(TAG, "$LOG_PREFIX 🔒 Locked global font at 3s hold")
                                }
                                saveLockStates()
                                showToast(getFontLockStatusMessage())
                                vibrateFeedback(50)
                                nameHoldHandler?.postDelayed({ nameHoldRunnable?.run() }, 2000)
                            }
                            holdDuration >= 1500 -> {
                                // Lock color only
                                shouldShowDropdown = false
                                toggleColorLock()
                                if (!colorChangingEnabled) {
                                    globalLockedColor = currentColor
                                    Log.d(TAG, "$LOG_PREFIX 🔒 Locked global color at 1.5s hold: $currentColor")
                                }
                                saveLockStates()
                                showToast(getColorLockStatusMessage())
                                vibrateFeedback(50)
                                nameHoldHandler?.postDelayed({ nameHoldRunnable?.run() }, 1500)
                            }
                            else -> {
                                nameHoldHandler?.postDelayed({ nameHoldRunnable?.run() }, 100)
                            }
                        }
                    }
                    nameHoldHandler?.postDelayed(nameHoldRunnable!!, 1500)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Stop font cycling if active
                    fontCycleRunnable?.let { fontCycleHandler?.removeCallbacks(it) }
                    fontCycleHandler = null
                    fontCycleRunnable = null
                    
                    if (wasAbove7Seconds) {
                        // Lock with the last cycled font
                        toggleFontAndColorLock()
                        if (!colorChangingEnabled) {
                            globalLockedColor = smokerNameText.currentTextColor
                            Log.d(TAG, "$LOG_PREFIX 🔒 Locked color after 7s cycle: $globalLockedColor")
                        }
                        saveLockStates()
                        showToast("Font & color locked")
                    }
                    
                    // Clean up handlers
                    nameHoldRunnable?.let { nameHoldHandler?.removeCallbacks(it) }
                    nameHoldHandler = null
                    nameHoldRunnable = null
                    
                    val holdDuration = System.currentTimeMillis() - nameHoldStartTime
                    Log.d(TAG, "$LOG_PREFIX 👆 Name touch up, duration: ${holdDuration}ms, shouldShowDropdown: $shouldShowDropdown")
                    
                    // If short tap, always show smoker selection popup
                    if (event.action == MotionEvent.ACTION_UP && shouldShowDropdown && holdDuration < 1500) {
                        // Always show smoker selection on tap
                        Log.d(TAG, "$LOG_PREFIX 👥 Tap: showing smoker selection")
                        showSmokerSelection()
                    }
                    
                    nameHoldStartTime = 0L
                    true
                }
                else -> false
            }
        }
    }
    
    private fun showSmokerSelection() {
        Log.d(TAG, "$LOG_PREFIX 👥 Showing smoker selection dialog")
        
        val currentIndex = allSmokers.indexOfFirst { it.name == currentSmoker }
        
        // Create custom dialog with styled theme
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .create()
        
        // Create custom view for the dialog
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000")) // Black semi-transparent background
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }
        
        // Add title
        val titleView = TextView(this).apply {
            text = "SELECT SMOKER"
            textSize = 20f
            setTextColor(Color.parseColor("#98FB98")) // Light neon green from main view
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16.dpToPx())
        }
        dialogView.addView(titleView)
        
        // Add smoker items
        allSmokers.forEachIndexed { index, smoker ->
            val itemView = TextView(this).apply {
                text = smoker.name
                textSize = 18f
                // Use the current font and color from smokerNameText
                setTextColor(smokerNameText.currentTextColor)
                typeface = smokerNameText.typeface
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                gravity = Gravity.CENTER
                
                // Highlight selected smoker with neon green border
                if (index == currentIndex) {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setStroke(2.dpToPx(), Color.parseColor("#98FB98")) // Thin neon green border like main view
                        cornerRadius = 8.dpToPx().toFloat()
                    }
                }
                
                setOnClickListener {
                    currentSmoker = smoker.name
                    currentSmokerIndex = index
                    
                    Log.d(TAG, "$LOG_PREFIX 🔄 Switched to smoker: $currentSmoker")
                    
                    // Reload count for new smoker
                    lifecycleScope.launch {
                        loadCurrentData()
                    }
                    
                    dialog.dismiss()
                }
            }
            dialogView.addView(itemView)
            
            // Add small spacing between items
            if (index < allSmokers.size - 1) {
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        4.dpToPx()
                    )
                }
                dialogView.addView(spacer)
            }
        }
        
        // Add cancel button
        val cancelButton = TextView(this).apply {
            text = "CANCEL"
            textSize = 14f  // Smaller font size
            setTextColor(Color.parseColor("#98FB98")) // Light neon green
            gravity = Gravity.CENTER
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 4.dpToPx())  // Smaller padding
            setOnClickListener {
                dialog.dismiss()
            }
        }
        dialogView.addView(cancelButton)
        
        // Apply neon green border to the entire dialog
        dialogView.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#CC000000")) // Black semi-transparent
            setStroke(2.dpToPx(), Color.parseColor("#98FB98")) // Thin neon green border
            cornerRadius = 12.dpToPx().toFloat()
        }
        
        dialog.setView(dialogView)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            // Move dialog 2cm higher
            setGravity(Gravity.CENTER)
            attributes = attributes?.apply {
                y = -80.dpToPx()  // Move up by ~2cm (80dp)
            }
        }
        dialog.show()
    }
    
    private fun cycleToNextFont(): android.graphics.Typeface {
        lastSelectedFontIndex = (lastSelectedFontIndex + 1) % fontList.size
        val nextFont = try {
            ResourcesCompat.getFont(this, fontList[lastSelectedFontIndex])!!
        } catch (e: Exception) {
            Log.e(TAG, "$LOG_PREFIX Error loading font at index $lastSelectedFontIndex", e)
            android.graphics.Typeface.DEFAULT_BOLD
        }
        Log.d(TAG, "$LOG_PREFIX 🔤 Cycled to font index $lastSelectedFontIndex")
        return nextFont
    }
    
    private fun toggleColorLock() {
        colorChangingEnabled = !colorChangingEnabled
        Log.d(TAG, "$LOG_PREFIX 🎨 Color lock toggled: enabled=$colorChangingEnabled")
        
        // Save the new state immediately
        val prefs = getSharedPreferences("sesh", MODE_PRIVATE)
        prefs.edit().putBoolean("color_changing_enabled", colorChangingEnabled).apply()
    }
    
    private fun toggleFontLock() {
        randomFontsEnabled = !randomFontsEnabled
        Log.d(TAG, "$LOG_PREFIX 🔤 Font lock toggled: enabled=$randomFontsEnabled")
        
        // Save the new state immediately
        val prefs = getSharedPreferences("sesh", MODE_PRIVATE)
        prefs.edit().putBoolean("random_fonts_enabled", randomFontsEnabled).apply()
    }
    
    private fun toggleFontAndColorLock() {
        colorChangingEnabled = !colorChangingEnabled
        randomFontsEnabled = !randomFontsEnabled
        Log.d(TAG, "$LOG_PREFIX 🔒 Both locks toggled: color=$colorChangingEnabled, font=$randomFontsEnabled")
        
        // Save the new states immediately
        val prefs = getSharedPreferences("sesh", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("color_changing_enabled", colorChangingEnabled)
            .putBoolean("random_fonts_enabled", randomFontsEnabled)
            .apply()
    }
    
    private fun getColorLockStatusMessage(): String {
        return if (colorChangingEnabled) "Color unlocked" else "Color locked"
    }
    
    private fun getFontLockStatusMessage(): String {
        return if (randomFontsEnabled) "Font unlocked" else "Font locked"
    }
    
    private fun getFontAndColorLockStatusMessage(): String {
        return when {
            !colorChangingEnabled && !randomFontsEnabled -> "Font & color locked"
            colorChangingEnabled && randomFontsEnabled -> "Font & color unlocked"
            !colorChangingEnabled -> "Color locked, font unlocked"
            else -> "Font locked, color unlocked"
        }
    }
    
    private fun saveLockStates() {
        val prefs = getSharedPreferences("sesh", MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("random_fonts_enabled", randomFontsEnabled)
            putBoolean("color_changing_enabled", colorChangingEnabled)
            if (globalLockedColor != null && globalLockedColor != -256) {
                putInt("global_locked_color", globalLockedColor!!)
            }
            if (globalLockedFont != null) {
                // Use the tracked font index instead of trying to compare typefaces
                putInt("global_font_index", lastSelectedFontIndex)
            }
            // Also save current display values for MainActivity to load
            putInt("giant_counter_color", smokerNameText.currentTextColor)
            putInt("giant_counter_font_index", lastSelectedFontIndex)
            apply()
        }
        Log.d(TAG, "$LOG_PREFIX 💾 Lock states saved, color=${smokerNameText.currentTextColor}")
    }
    
    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun vibrateFeedback(duration: Long) {
        if (vibrationEnabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }
    }
    
    // Extension function for dp to px conversion
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}