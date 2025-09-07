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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
    private lateinit var calculatorButton: TextView
    private lateinit var topControlsContainer: ViewGroup
    
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
    
    // ViewModels
    private lateinit var sessionStatsVM: SessionStatsViewModel
    
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
        
        // Smoker name at top
        smokerNameText = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 120 // Space from top
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
            setTextColor(Color.parseColor("#98FB98"))
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
                bottomMargin = 20.dpToPx()
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
        
        // Calculator button (toggle top section)
        calculatorButton = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 120.dpToPx()
            }
            text = "📱"
            textSize = 32f
            setPadding(20.dpToPx(), 10.dpToPx(), 20.dpToPx(), 10.dpToPx())
            setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
            isClickable = true
            isFocusable = true
        }
        bottomControlsContainer.addView(calculatorButton)
        
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
            setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
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
        val prefs = getSharedPreferences("CloudCounterPrefs", MODE_PRIVATE)
        vibrationEnabled = prefs.getBoolean("vibration_enabled", true)
        currentSmoker = prefs.getString("selected_smoker", "Sam") ?: "Sam"
        sessionStart = prefs.getLong("sessionStart", System.currentTimeMillis())
        isAutoMode = prefs.getBoolean("is_auto_mode", true)
        timerEnabled = prefs.getBoolean("timer_enabled", false)
        currentActivityType = prefs.getString("current_activity_type", "cones") ?: "cones"
        
        Log.d(TAG, "$LOG_PREFIX Loaded prefs:")
        Log.d(TAG, "$LOG_PREFIX   currentSmoker: $currentSmoker")
        Log.d(TAG, "$LOG_PREFIX   sessionStart: $sessionStart")
        Log.d(TAG, "$LOG_PREFIX   isAutoMode: $isAutoMode")
        Log.d(TAG, "$LOG_PREFIX   currentActivityType: $currentActivityType")
        
        // Initialize ViewModels
        val factory = SessionStatsViewModelFactory()
        sessionStatsVM = ViewModelProvider(this, factory).get(SessionStatsViewModel::class.java)
        
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
                        // Increment counter
                        incrementCounter()
                        // Only rotate smoker if in auto mode
                        if (isAutoMode) {
                            rotateSmoker()
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
        
        // Calculator button (toggle top section)
        calculatorButton.setOnClickListener {
            toggleTopSection()
        }
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
                        else -> com.sam.cloudcounter.ActivityType.CONE
                    }
                    
                    currentCount = sessionActivities.count { 
                        it.smokerId == currentSmokerObj.smokerId && it.type == activityType 
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
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading data", e)
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
        
        // Save to database
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
                        
                        val activity = com.sam.cloudcounter.ActivityLog(
                            smokerId = smoker.smokerId,
                            type = activityType,
                            timestamp = System.currentTimeMillis(),
                            sessionStartTime = sessionStart
                        )
                        
                        val insertedId = repository.insert(activity)
                        Log.d(TAG, "$LOG_PREFIX Activity inserted with ID: $insertedId")
                        
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
        if (allSmokers.size <= 1) return // No rotation needed if only one smoker
        
        // Save previous smoker as recent
        recentSmoker = currentSmoker
        recentSmokerCount = currentCount
        
        // Move to next smoker
        currentSmokerIndex = (currentSmokerIndex + 1) % allSmokers.size
        val nextSmoker = allSmokers[currentSmokerIndex]
        currentSmoker = nextSmoker.name
        
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
        
        // Update calculator button appearance
        calculatorButton.text = if (topSectionVisible) "📱" else "🧮"
    }
    
    // Extension function for dp to px conversion
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}