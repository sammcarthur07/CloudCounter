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
    }

    // UI elements
    private lateinit var backgroundImage: ImageView
    private lateinit var giantButton: ImageView
    private lateinit var counterText: TextView
    private lateinit var smokerNameText: TextView
    private lateinit var recentStatsText: TextView
    private lateinit var backButton: TextView
    private lateinit var konfettiView: KonfettiView
    
    // Data
    private var currentSmoker: String = "Sam"
    private var currentActivityType: String = "cones"
    private var currentCount: Int = 0
    private var recentSmoker: String = ""
    private var recentSmokerCount: Int = 0
    
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
        rootLayout.addView(backButton)
        
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
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        val database = com.sam.cloudcounter.CloudCounterDatabase.getDatabase(this)
        repository = ActivityRepository(
            database.activityLogDao(),
            database.smokerDao(),
            database.sessionSummaryDao(),
            database.stashDao()
        )
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        
        // Load vibration preference
        val prefs = getSharedPreferences("CloudCounterPrefs", MODE_PRIVATE)
        vibrationEnabled = prefs.getBoolean("vibration_enabled", true)
        currentSmoker = prefs.getString("selected_smoker", "Sam") ?: "Sam"
        
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
    }
    
    private fun loadCurrentData() {
        lifecycleScope.launch {
            try {
                val smokerDao = com.sam.cloudcounter.CloudCounterDatabase.getDatabase(this@GiantCounterActivity).smokerDao()
                
                // Get current smoker
                val currentSmokerObj = withContext(Dispatchers.IO) {
                    smokerDao.getByName(currentSmoker) ?: smokerDao.getAll().firstOrNull()
                }
                
                if (currentSmokerObj != null) {
                    // Get most recent activity for this smoker
                    val recentActivity = withContext(Dispatchers.IO) {
                        repository.getLastActivityForSmoker(currentSmokerObj.id)
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
                            smokerDao.getById(recentActivity.smokerId)
                        }
                        recentSmoker = recentSmokerObj?.name ?: ""
                    } else {
                        currentActivityType = "cones"
                    }
                    
                    // Get today's count for current activity type
                    val todayStart = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    
                    val todayActivities = withContext(Dispatchers.IO) {
                        repository.getLogsInTimeRange(todayStart, System.currentTimeMillis())
                    }
                    
                    // Count activities for current smoker and type
                    val activityType = when (currentActivityType) {
                        "cones" -> com.sam.cloudcounter.ActivityType.CONE
                        "joints" -> com.sam.cloudcounter.ActivityType.JOINT
                        else -> com.sam.cloudcounter.ActivityType.CONE
                    }
                    
                    currentCount = todayActivities.count { 
                        it.smokerId == currentSmokerObj.id && it.type == activityType 
                    }
                    
                    // Get recent smoker's count if different
                    if (recentSmoker.isNotEmpty() && recentSmoker != currentSmoker) {
                        val recentSmokerObj = withContext(Dispatchers.IO) {
                            smokerDao.getByName(recentSmoker)
                        }
                        if (recentSmokerObj != null) {
                            recentSmokerCount = todayActivities.count {
                                it.smokerId == recentSmokerObj.id && it.type == activityType
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
        currentCount++
        counterText.text = currentCount.toString()
        
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
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Get smoker ID
                    val smokerDao = com.sam.cloudcounter.CloudCounterDatabase.getDatabase(this@GiantCounterActivity).smokerDao()
                    val smoker = smokerDao.getByName(currentSmoker) ?: smokerDao.getAll().firstOrNull()
                    
                    if (smoker != null) {
                        val activityType = when (currentActivityType) {
                            "cones" -> com.sam.cloudcounter.ActivityType.CONE
                            "joints" -> com.sam.cloudcounter.ActivityType.JOINT
                            else -> com.sam.cloudcounter.ActivityType.CONE
                        }
                        
                        val activity = com.sam.cloudcounter.ActivityLog(
                            smokerId = smoker.id,
                            type = activityType,
                            timestamp = System.currentTimeMillis()
                        )
                        repository.insert(activity)
                    }
                }
                
                // Update recent smoker
                recentSmoker = currentSmoker
                recentSmokerCount = currentCount
                updateUI()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving activity", e)
            }
        }
    }
    
    // Extension function for dp to px conversion
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}