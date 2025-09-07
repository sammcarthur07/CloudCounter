package com.sam.cloudcounter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
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
    private lateinit var timersButton: TextView
    private lateinit var timerControlsContainer: ViewGroup
    private lateinit var textTimeSinceLast: TextView
    private lateinit var textLastGapCountdown: TextView
    private lateinit var textThisSesh: TextView
    private lateinit var roundsContainer: ViewGroup
    private lateinit var btnRoundMinus: ImageView
    private lateinit var btnRoundPlus: ImageView
    private lateinit var textRoundsLeft: TextView
    
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
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable
    
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
        
        // TIMERS button at top
        timersButton = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 50.dpToPx()
            }
            text = "TIMERS"
            textSize = 16f
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
        rootLayout.addView(timersButton)
        
        // Timer controls container (initially hidden)
        timerControlsContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 100.dpToPx()
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
                bottomMargin = 220.dpToPx() // Position above the button
            }
            text = "Loading..."
            textSize = 36f
            setTextColor(Color.parseColor("#98FB98"))
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
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
                bottomMargin = (3).dpToPx()  // Negative margin moves it down further from bottom
            }
            text = ""
            textSize = 20f
            setTextColor(Color.parseColor("#98FB98"))
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
        
        // Load timer data from preferences
        lastLogTime = prefs.getLong("lastLogTime", 0L)
        lastConeTimestamp = prefs.getLong("lastConeTimestamp", 0L)
        lastJointTimestamp = prefs.getLong("lastJointTimestamp", 0L)
        lastBowlTimestamp = prefs.getLong("lastBowlTimestamp", 0L)
        roundsLeft = prefs.getInt("roundsLeft", 0)
        initialRoundsSet = prefs.getInt("initialRoundsSet", 0)
        
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
        
        // Timer button
        timersButton.setOnClickListener {
            toggleTimersVisibility()
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
        
        if (recentSmoker.isNotEmpty() && recentSmoker != currentSmoker) {
            recentStatsText.text = "$recentSmoker: $recentSmokerCount ${currentActivityType}"
        } else {
            recentStatsText.text = "${currentActivityType.capitalize()}"
        }
    }
    
    private fun incrementCounter() {
        Log.d(TAG, "$LOG_PREFIX incrementCounter() called")
        Log.d(TAG, "$LOG_PREFIX   Current smoker: $currentSmoker")
        Log.d(TAG, "$LOG_PREFIX   Activity type: $currentActivityType")
        Log.d(TAG, "$LOG_PREFIX   Count before: $currentCount")
        Log.d(TAG, "$LOG_PREFIX   🌿 Stash will be updated")
        Log.d(TAG, "$LOG_PREFIX   🎯 Goals will be tracked")
        
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
        Log.d(TAG, "$LOG_PREFIX rotateSmoker() called")
        if (allSmokers.size <= 1) {
            Log.d(TAG, "$LOG_PREFIX   Only one smoker, no rotation needed")
            return
        }
        
        // Save previous smoker as recent
        recentSmoker = currentSmoker
        recentSmokerCount = currentCount
        
        // Move to next smoker
        currentSmokerIndex = (currentSmokerIndex + 1) % allSmokers.size
        val nextSmoker = allSmokers[currentSmokerIndex]
        currentSmoker = nextSmoker.name
        
        Log.d(TAG, "$LOG_PREFIX   Rotated from $recentSmoker to $currentSmoker")
        
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
            timersButton.text = "HIDE TIMERS"
            timerControlsContainer.visibility = View.VISIBLE
        } else {
            timersButton.text = "TIMERS"
            timerControlsContainer.visibility = View.GONE
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
        val now = System.currentTimeMillis()
        
        // LEFT TIMER: Time since last activity
        val sinceLastMs = if (lastLogTime > 0) now - lastLogTime else 0
        val sinceLastSec = sinceLastMs / 1000
        textTimeSinceLast.text = formatInterval(sinceLastSec)
        
        // MIDDLE TIMER: Gap countdown
        if (intervalsList.size >= 2) {
            val averageInterval = intervalsList.takeLast(10).average().toLong()
            val timeSinceLastActivity = now - lastLogTime
            val remainingMs = averageInterval - timeSinceLastActivity
            val remainingSec = remainingMs / 1000
            
            val gapFormatted = if (remainingSec >= 0) {
                formatInterval(remainingSec)
            } else {
                "-${formatInterval(kotlin.math.abs(remainingSec))}"
            }
            textLastGapCountdown.text = gapFormatted
        } else {
            textLastGapCountdown.text = "0s"
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
            commit()
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
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
    }
    
    // Extension function for dp to px conversion
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}