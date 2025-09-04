package com.sam.cloudcounter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sam.cloudcounter.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import android.widget.Button
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.text.InputFilter
import com.google.firebase.auth.FirebaseAuth
import android.graphics.Color
import android.content.res.ColorStateList
import android.widget.ImageView
import com.google.android.material.tabs.TabLayout
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.animation.*
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import android.view.WindowManager
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import androidx.transition.Fade
import androidx.transition.TransitionManager
import android.animation.ValueAnimator
import kotlin.math.min
import androidx.core.content.res.ResourcesCompat
import android.graphics.Typeface
import kotlin.random.Random
import java.util.UUID
import java.util.Date
import android.widget.GridLayout


class MainActivity : AppCompatActivity() {

    private lateinit var smokerManager: SmokerManager
    private lateinit var smokerAdapterNew: SmokerAdapter

    companion object {
        private const val TAG = "MainActivity"
        private const val ADMIN_UID = "diY4ATkGQYhYndv2lQY4rZAUKGl2"

        // Add neon colors for font coloring
        private val NEON_COLORS = listOf(
            Color.parseColor("#FFFF66"),  // Neon Yellow
            Color.parseColor("#BF7EFF"),  // Neon Purple
            Color.parseColor("#98FB98"),  // Neon Green
            Color.parseColor("#66B2FF"),  // Neon Blue
            Color.parseColor("#FFA366")   // Neon Orange
        )
    }

    private val ROOM_NAMES = listOf(
        // Cloud/Sky Themed
        "Catching Clouds", "Cloud Nine", "Sky High Session", "Above the Clouds",
        "Floating Dreams", "Cloudy With a Chance", "Silver Lining Society",
        "Cumulus Club", "Stratosphere Station", "Head in the Clouds",
        "Vapor Trail", "Cloud Hopping",

        // Time-Based
        "Waking and Baking", "Afternoon Delight", "Midnight Express", "Sunrise Session",
        "Evening Vibes", "Tea Time Tokes", "Dawn Patrol", "Twilight Zone",
        "Happy Hour Haven", "Brunch Bunch", "Late Night Lounge", "Early Bird Special",

        // Mood/Vibe Themed
        "Cheeky Seshy", "Mellow Yellow", "Green Dreams", "Peaceful Puffs",
        "Happy Hour", "Chill Zone", "Good Vibes Only", "Zen Garden",
        "Blissful Moments", "Serenity Now", "Tranquil Times", "Feel Good Factory",

        // Nature Themed
        "Behind the Rainbow", "Forest Fog", "Mountain Mist", "Garden Party",
        "Beach Breeze", "Desert Daze", "River Rapids", "Jungle Journey",
        "Ocean Breeze", "Prairie Wind", "Valley Vista", "Meadow Magic",

        // Fun/Playful
        "Puff Puff Pass", "Circle of Trust", "Giggle Factory", "Snack Attack Central",
        "Couch Lock Lodge", "Munchie Manor", "Laughter Lounge", "Comedy Club",
        "Smile Station", "Joy Ride", "Fun House", "Happy Place",

        // Cosmic/Space
        "Cosmic Journey", "Star Gazing", "Lunar Landing", "Astro Session",
        "Galaxy Express", "Space Cake Station", "Neptune's Lounge", "Mars Bar",
        "Saturn's Rings", "Milky Way Cafe", "Comet Trail", "Asteroid Belt",

        // Adventure/Journey
        "Mystery Tour", "Magic Carpet Ride", "Time Machine", "Dream Weaver",
        "Vision Quest", "Mind Palace", "Wonder Land", "Enchanted Forest",
        "Crystal Cave", "Hidden Temple", "Secret Garden", "Mystic Mountain",

        // Music/Arts
        "Jazz Lounge", "Rock & Roll Hall", "Acoustic Corner", "Bass Drop Zone",
        "Vinyl Vibes", "Studio Session", "Jam Session", "Beat Box",
        "Melody Mansion", "Rhythm Room", "Harmony House", "Echo Chamber",

        // Food/Culinary
        "Cookie Jar", "Brownie Points", "Candy Land", "Sweet Spot",
        "Flavor Town", "Taste Buds", "Kitchen Sync", "Snack Shack",
        "Munchie Mart", "Treat Street", "Craving Cave", "Nibble Nook",

        // Retro/Nostalgic
        "Groovy Grove", "Disco Inferno", "Retro Lounge", "Vintage Vibes",
        "Old School Cool", "Classic Corner", "Throwback Thursday", "Memory Lane",
        "Nostalgia Station", "Time Capsule", "Golden Age", "Back in the Day"
    )

    private lateinit var supportMessagesWatcher: SupportMessagesWatcher

    private fun getRandomRoomName(): String {
        val baseName = ROOM_NAMES.random()
        // Add a number between 1-999 to ensure uniqueness
        val number = (1..999).random()
        return "$baseName $number"
    }


    private fun generateShareCode(): String {
        return (10000..99999).random().toString()
    }

    private var currentFontCycleIndex = 0
    private var spinnerLongPressHandler: Handler? = null
    private var spinnerLongPressRunnable: Runnable? = null


    private var vibrationsEnabled = true  // Track vibration state


    private var pendingBowlQuantity = 1

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


    private var spinnerHoldStartTime = 0L


    private var currentFontIndex = 0
    private val random = java.util.Random()

    /**
     * Data structure for organized smoker display
     */
    private data class SmokerSection(
        val title: String?,
        val smokers: List<Smoker>
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private var currentDialog: Dialog? = null

    private var addSmokerShimmerAnimation: ShimmerTextAnimation? = null

    private val repo by lazy { (application as CloudCounterApplication).repository }
    private val statsVM by lazy { ViewModelProvider(this).get(StatsViewModel::class.java) }
    private val sessionStatsVM by lazy {
        ViewModelProvider(this, SessionStatsViewModelFactory()).get(SessionStatsViewModel::class.java)
    }

    private var currentSmokerIndex = 0

    private var processedActivityIds = mutableSetOf<String>()

    private var lastSelectedActivityButton: Button? = null

    // Cloud functionality
    private lateinit var authManager: FirebaseAuthManager
    private lateinit var cloudSyncService: CloudSyncService
    private lateinit var sessionSyncService: SessionSyncService
    private var currentRoom: RoomData? = null

    private var sessionDialogEffects: CloudSessionDialogEffects? = null

    private var timersVisible = true

    private var isInFirstConeDialog = false

    private var notificationsEnabled = true  // Track notification state

    private lateinit var addSmokerDialog: AddSmokerDialog
    private lateinit var passwordDialog: PasswordDialog

    private var smokers: List<Smoker> = emptyList()

    private lateinit var rewindReceiver: BroadcastReceiver

    private lateinit var smokerUpdateReceiver: BroadcastReceiver
    private lateinit var undoReceiver: BroadcastReceiver
    private lateinit var deletionReceiver: BroadcastReceiver

    private lateinit var stashViewModel: StashViewModel
    private var stashIntegration: StashIntegration? = null

    private var rewindOffset = 0L  // Total milliseconds rewound
    private val REWIND_AMOUNT_MS = 10000L  // 10 seconds per rewind

    private var lastRoundButtonClickTime = 0L
    private val ROUND_BUTTON_DEBOUNCE_MS = 300L

    private var actualLastLogTime = 0L  // The actual last activity timestamp (not affected by rewind)
    private var lastLogTimeBeforeRewind = 0L  // Store the last log time when we start rewinding
    private var lastConeTimestamp = 0L  // Track last cone timestamp for live timer
    private var lastJointTimestamp = 0L  // Track last joint timestamp for live timer
    private var lastBowlTimestamp = 0L  // Track last bowl timestamp for live timer

    // Remember the room we're in
    private var currentShareCode: String? = null

    //goals
    private lateinit var goalService: GoalService

    // session state
    private var sessionActive = false
    private var sessionStart = 0L
    private var lastLogTime = 0L
    private var lastIntervalMillis = 0L
    private var roundsLeft = 0
    private var hitsThisRound = 0
    private var actualRounds = 0
    private val intervalsList = mutableListOf<Long>()
    private var isAutoMode = true  // true = auto, false = sticky
    private var initialRoundsSet = 0  // Store the initial rounds when session starts
    private var currentRoomName: String? = null
    private var pendingActivityType: ActivityType? = null  // Store activity type when showing no session popup   //
    private var isUpdatingRoundsLocally = false
    private var localRoundsUpdateTime = 0L
    // Cached latest room snapshot so notifications can reflect remote activity immediately
    private var latestRoomData: RoomData? = null

    // properties for offline activity queueing
    private val offlineActivityQueue = mutableListOf<OfflineActivity>()
    private var syncCheckHandler: Handler? = null
    private var syncCheckRunnable: Runnable? = null
    private var lastSyncAttempt = 0L
    private val SYNC_RETRY_INTERVAL = 10000L // Try to sync every 10 seconds when online


    // to differentiate UI-originated hits (which already advance spinner) vs notification-originated
    private var lastHitCameFromUI = false
    private val activityHistory = mutableListOf<ActivityLog>()

    //Confetti
    private lateinit var confettiHelper: ConfettiHelper


    private var smokersTakenTurnSinceCounterChange = mutableSetOf<String>()
    private var lastCounterChangeTime = 0L

    // editing/resuming
    private var editingSummaryId: Long? = null
    private var lastLoadedSummary: SessionSummary? = null

    // Prevent rapid clicks
    private var isLoggingHit = false
    private val hitLoggingLock = Any()
    private var lastHitTime = 0L
    private val MIN_HIT_INTERVAL_MS = 500L

    private val handler = Handler(Looper.getMainLooper())

    private var activitiesTimestamps = mutableListOf<Long>()  // NEW: Track all activity timestamps

    // ADD: New properties for timer sound and auto-add features
    private lateinit var timerSoundHelper: TimerSoundHelper
    private lateinit var autoAddManager: AutoAddManager
    private var lastMiddleTimerValue: Long = 0L // Track when timer crosses zero
    private var wasMiddleTimerPositive = true
    
    // Retroactive activity logging properties
    private var countdownStartTime: Long = 0L // When countdown started (when last activity was logged)
    private var countdownEndTime: Long = 0L // When countdown reached 0
    private var longPressStartTime: Long = 0L
    private var isLongPressing = false
    private val LONG_PRESS_DURATION = 1000L // 1 second for long press
    private var retroactiveDialog: Dialog? = null
    private val retroactiveActivities = mutableListOf<Long>() // Track bulk added activity timestamps for undo

    // pausing functions
    private var isPaused = false
    private var pausedSmokerIds = mutableListOf<String>() // Smoker IDs that are paused (not user IDs)
    private var awaySmokers = mutableListOf<String>()     // User IDs that are away

    // Add these new properties for sync status tracking
    private val smokerSyncStatus = mutableMapOf<String, SyncStatus>()
    private var isNetworkAvailable = true
    private var networkCheckHandler: Handler? = null
    private var networkCheckRunnable: Runnable? = null

    // Sync status enum
    private enum class SyncStatus {
        SYNCED,     // Green - online and synced
        SYNCING,    // Orange - online but syncing/not synced
        OFFLINE     // Red - offline
    }

    // Sound picker launcher
    private val soundPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                timerSoundHelper.setSelectedSoundUri(uri)
                Toast.makeText(this, "Timer sound updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }


    // Simplified timerRunnable - only handles main session timers
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!sessionActive) {
                Log.d(TAG, "⏰ Timer stopped - session not active")
                return
            }

            val realNow = System.currentTimeMillis()
            val rewindedNow = realNow - rewindOffset

            // Find the most recent activity that's before our rewound time
            val effectiveLastLogTime = activitiesTimestamps
                .filter { it <= rewindedNow }
                .maxOrNull() ?: 0L

            // LEFT TIMER: Time since last activity or session start
            val sinceLastMs = if (effectiveLastLogTime > 0) {
                (rewindedNow - effectiveLastLogTime).coerceAtLeast(0)
            } else {
                (rewindedNow - sessionStart).coerceAtLeast(0)
            }

            val sinceLastSec = sinceLastMs / 1000
            val sinceLastFormatted = formatInterval(sinceLastSec)
            binding.textTimeSinceLast.text = sinceLastFormatted

            // MIDDLE TIMER: Gap countdown
            // Only show countdown if we have at least 2 activities before our rewound time
            val activitiesBeforeRewind = activitiesTimestamps.filter { it <= rewindedNow }.sorted()

            if (activitiesBeforeRewind.size >= 2) {
                // Calculate the gap between the last two activities
                val lastTwo = activitiesBeforeRewind.takeLast(2)
                val gapBetweenLast = lastTwo[1] - lastTwo[0]
                val timeSinceLast = rewindedNow - lastTwo[1]
                val remainingMs = gapBetweenLast - timeSinceLast
                val remainingSec = remainingMs / 1000

                // Check if timer crossed from positive to negative (hit zero)
                val isCurrentlyPositive = remainingSec >= 0
                if (wasMiddleTimerPositive && !isCurrentlyPositive) {
                    Log.d(TAG, "🔔 Middle timer hit zero - playing sound")
                    timerSoundHelper.playTimerSound()
                    // Track when countdown reaches zero for retroactive time travel
                    countdownEndTime = System.currentTimeMillis()
                }
                wasMiddleTimerPositive = isCurrentlyPositive

                val gapFormatted = if (remainingSec >= 0) {
                    formatInterval(remainingSec)
                } else {
                    "-${formatInterval(kotlin.math.abs(remainingSec))}"
                }
                binding.textLastGapCountdown.text = gapFormatted
            } else {
                // Not enough activities for countdown
                binding.textLastGapCountdown.text = "0s"
                wasMiddleTimerPositive = true
            }

            // RIGHT TIMER: Session elapsed
            val sessionElapsedMs = (rewindedNow - sessionStart).coerceAtLeast(0)
            val sessionElapsedSec = sessionElapsedMs / 1000
            val sessionElapsedFormatted = formatInterval(sessionElapsedSec)
            binding.textThisSesh.text = sessionElapsedFormatted

            // Update session timer in ViewModel
            sessionStatsVM.refreshTimerWithOffset(rewindOffset)

            // Update the "since last" stats for all activity types
            val current = sessionStatsVM.groupStats.value
            if (current != null) {
                val updatedStats = current.copy(
                    sinceLastGapMs = if (lastConeTimestamp > 0) (rewindedNow - lastConeTimestamp).coerceAtLeast(0) else 0L,
                    sinceLastJointMs = if (lastJointTimestamp > 0) (rewindedNow - lastJointTimestamp).coerceAtLeast(0) else 0L,
                    sinceLastBowlMs = if (lastBowlTimestamp > 0) (rewindedNow - lastBowlTimestamp).coerceAtLeast(0) else 0L
                )
                sessionStatsVM.updateGroupStats(updatedStats)
            }

            handler.postDelayed(this, 1000)
        }
    }

    private fun addLightningEffect(button: View) {
        // Create a FrameLayout to hold the button and lightning overlay
        val parent = button.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(button)

        // Create lightning overlay
        val lightningOverlay = View(this).apply {
            layoutParams = button.layoutParams
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.electric_spark_animation)
            alpha = 0f
            isClickable = false
            isFocusable = false
        }

        // Add the overlay on top of the button
        parent.addView(lightningOverlay, index + 1)

        // Create the lightning animation
        val alphaIn = ObjectAnimator.ofFloat(lightningOverlay, "alpha", 0f, 1f).apply {
            duration = 50
        }

        val alphaOut = ObjectAnimator.ofFloat(lightningOverlay, "alpha", 1f, 0f).apply {
            duration = 200
            startDelay = 100
        }

        // Add particle sparks around the edges
        createElectricSparks(button)

        // Play the animation
        AnimatorSet().apply {
            playSequentially(alphaIn, alphaOut)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    parent.removeView(lightningOverlay)
                }
            })
            start()
        }

        // Start the drawable animation if it's an AnimationDrawable
        (lightningOverlay.background as? android.graphics.drawable.AnimationDrawable)?.start()
    }

    private fun createElectricSparks(view: View) {
        val parent = view.parent as? ViewGroup ?: return

        // Create sparks at corners and edges
        val sparkPositions = listOf(
            Pair(0f, 0f),           // Top-left
            Pair(1f, 0f),           // Top-right
            Pair(0f, 1f),           // Bottom-left
            Pair(1f, 1f),           // Bottom-right
            Pair(0.5f, 0f),         // Top-center
            Pair(0.5f, 1f),         // Bottom-center
            Pair(0f, 0.5f),         // Left-center
            Pair(1f, 0.5f)          // Right-center
        )

        sparkPositions.forEach { (xRatio, yRatio) ->
            createSingleSpark(parent, view, xRatio, yRatio)
        }
    }

    private fun createSingleSpark(parent: ViewGroup, anchorView: View, xRatio: Float, yRatio: Float) {
        val spark = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(12, 12)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
        }

        parent.addView(spark)

        // Position the spark
        val startX = anchorView.x + (anchorView.width * xRatio) - 6
        val startY = anchorView.y + (anchorView.height * yRatio) - 6

        spark.x = startX
        spark.y = startY
        spark.scaleX = 0f
        spark.scaleY = 0f

        // Create random end position for spark to fly to
        val angle = Math.random() * Math.PI * 2
        val distance = 50f + (Math.random() * 100f).toFloat()
        val endX = startX + (Math.cos(angle) * distance).toFloat()
        val endY = startY + (Math.sin(angle) * distance).toFloat()

        // Animate the spark
        val scaleUp = ObjectAnimator.ofFloat(spark, "scaleX", 0f, 1.5f, 0f).apply {
            duration = 300
        }
        val scaleUpY = ObjectAnimator.ofFloat(spark, "scaleY", 0f, 1.5f, 0f).apply {
            duration = 300
        }
        val moveX = ObjectAnimator.ofFloat(spark, "x", startX, endX).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }
        val moveY = ObjectAnimator.ofFloat(spark, "y", startY, endY).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }
        val fade = ObjectAnimator.ofFloat(spark, "alpha", 1f, 0f).apply {
            duration = 300
        }

        // Animate spark color from white to electric blue to yellow
        val colorAnim = ValueAnimator.ofArgb(Color.WHITE, Color.CYAN, Color.YELLOW).apply {
            duration = 300
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                (spark.background as? GradientDrawable)?.setColor(color)
            }
        }

        AnimatorSet().apply {
            playTogether(scaleUp, scaleUpY, moveX, moveY, fade, colorAnim)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    parent.removeView(spark)
                }
            })
            start()
        }
    }

    private fun updateTimersForRewind() {
        Log.d(TAG, "⏪ Updating all timers for rewind offset: ${rewindOffset}ms")

        // The main timer runnable will automatically pick up the new rewindOffset
        // Force an immediate timer update
        handler.removeCallbacks(timerRunnable)
        handler.post(timerRunnable)

        // Update auto-add manager with the rewind offset
        if (::autoAddManager.isInitialized) {
            autoAddManager.applyRewindOffset(rewindOffset)
        }

        // Update session stats view model
        sessionStatsVM.applyRewindOffset(rewindOffset)

        // If we're in a room, we need to adjust the auto-add state times
        currentShareCode?.let { shareCode ->
            latestRoomData?.let { room ->
                val autoState = room.safeAutoAddState()
                if (autoState.coneAutoEnabled || autoState.jointAutoEnabled || autoState.bowlAutoEnabled) {
                    // Don't adjust the actual nextAutoTime values in autoState
                    // The rewind offset is applied when calculating remaining time
                    autoAddManager.updateAutoAddState(autoState)
                    Log.d(TAG, "⏪ Auto-add state refreshed with rewind offset")
                }
            }
        }
    }

    // External organizer used by adapter and other logic
    private fun organizeSmokers(): List<SmokerSection> {
        val sections = mutableListOf<SmokerSection>()

        // Separate smokers by status
        val activeSmokers = mutableListOf<Smoker>()
        val pausedSmokers = mutableListOf<Smoker>()
        val awaySmokersInSection = mutableListOf<Smoker>()

        smokers.forEach { smoker ->
            val smokerId = if (smoker.isCloudSmoker) smoker.cloudUserId else "local_${smoker.smokerId}"
            val userId = smoker.cloudUserId

            // Only consider away/paused status if we're in a room
            if (currentShareCode != null) {
                when {
                    pausedSmokerIds.contains(smokerId) -> pausedSmokers.add(smoker)
                    awaySmokers.contains(userId) -> awaySmokersInSection.add(smoker)
                    else -> activeSmokers.add(smoker)
                }
            } else {
                // Not in a room, so all smokers are just active
                activeSmokers.add(smoker)
            }
        }

        // Add sections in order: Active → Paused → Away
        if (activeSmokers.isNotEmpty()) {
            sections.add(SmokerSection(null, activeSmokers)) // No header for active
        }

        if (pausedSmokers.isNotEmpty()) {
            sections.add(SmokerSection("Paused", pausedSmokers))
        }

        if (awaySmokersInSection.isNotEmpty()) {
            sections.add(SmokerSection("Away", awaySmokersInSection))
        }

        return sections
    }

    // ADD: Track recent auto-adds to prevent double-firing
    private val recentAutoAdds = mutableMapOf<ActivityType, Long>()

    private fun hasRecentAutoAdd(activityType: ActivityType): Boolean {
        val lastAutoAdd = recentAutoAdds[activityType] ?: 0L
        return (System.currentTimeMillis() - lastAutoAdd) < 2000L // 2 second cooldown
    }

    private fun markRecentAutoAdd(activityType: ActivityType) {
        recentAutoAdds[activityType] = System.currentTimeMillis()
    }


    // Add this function to handle bowl long press
    private fun setupBowlLongPress() {
        binding.btnAddBowl.setOnLongClickListener {
            vibrateFeedback(50) // Short vibration feedback
            showBowlQuantityDialog() // Corrected line
            true // Consume the long click
        }
    }

    // Add this function to show the quantity selection dialog
    private fun showBowlQuantityDialog() {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)

        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#80000000"))
            isClickable = true
        }

        val card = androidx.cardview.widget.CardView(this).apply {
            radius = 12.dpToPx(this@MainActivity).toFloat()
            cardElevation = 8.dpToPx(this@MainActivity).toFloat()
            setCardBackgroundColor(Color.parseColor("#2A2A2A"))

            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = params
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(this@MainActivity), 16.dpToPx(this@MainActivity),
                20.dpToPx(this@MainActivity), 16.dpToPx(this@MainActivity))
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "How many bowls?"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(title)

        val gridLayout = GridLayout(this).apply {
            rowCount = 2
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        // Load the last saved quantity from preferences
        val lastBowlQuantity = prefs.getInt("last_bowl_quantity", 1)

        // Create number options, replacing "1" with the last saved quantity if it's 2 or 3
        val numbers = when (lastBowlQuantity) {
            2 -> listOf("1", "2", "3", "More")  // Keep 2 in the list
            3 -> listOf("1", "2", "3", "More")  // Keep 3 in the list
            else -> listOf("1", "2", "3", "More") // Default list for other values
        }

        numbers.forEachIndexed { index, number ->
            val button = com.google.android.material.button.MaterialButton(this).apply {
                text = number
                setTextColor(Color.WHITE)

                // Highlight the last used quantity
                if ((number == "1" && lastBowlQuantity == 1) ||
                    (number == "2" && lastBowlQuantity == 2) ||
                    (number == "3" && lastBowlQuantity == 3)) {
                    setBackgroundColor(Color.parseColor("#5A5A5A")) // Slightly lighter to show it was last used
                } else {
                    setBackgroundColor(Color.parseColor("#424242"))
                }

                val gridParams = GridLayout.LayoutParams().apply {
                    width = 80.dpToPx(this@MainActivity)
                    height = 60.dpToPx(this@MainActivity)
                    rowSpec = GridLayout.spec(index / 2, 1f)
                    columnSpec = GridLayout.spec(index % 2, 1f)
                    setMargins(4.dpToPx(this@MainActivity), 4.dpToPx(this@MainActivity),
                        4.dpToPx(this@MainActivity), 4.dpToPx(this@MainActivity))
                }
                layoutParams = gridParams

                setOnTouchListener { v, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            setBackgroundColor(Color.parseColor("#98FB98"))
                            setTextColor(Color.parseColor("#424242"))
                            true
                        }
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            setBackgroundColor(Color.parseColor("#424242"))
                            setTextColor(Color.WHITE)

                            if (event.action == android.view.MotionEvent.ACTION_UP) {
                                v.performClick()
                            }
                            true
                        }
                        else -> false
                    }
                }

                setOnClickListener {
                    when (number) {
                        "More" -> {
                            dialog.dismiss()
                            showBowlQuantityInputDialog()
                        }
                        else -> {
                            val quantity = number.toInt()
                            // Save the selected quantity to preferences
                            prefs.edit().putInt("last_bowl_quantity", quantity).apply()
                            dialog.dismiss()
                            logBowlsWithQuantity(quantity)
                        }
                    }
                }
            }
            gridLayout.addView(button)
        }

        contentLayout.addView(gridLayout)
        card.addView(contentLayout)
        container.addView(card)

        container.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(container)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        dialog.show()
    }

    // Add this function for manual number input
    private fun showBowlQuantityInputDialog() {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)

        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#80000000"))
            isClickable = true
        }

        val card = androidx.cardview.widget.CardView(this).apply {
            radius = 12.dpToPx(this@MainActivity).toFloat()
            cardElevation = 8.dpToPx(this@MainActivity).toFloat()
            setCardBackgroundColor(Color.parseColor("#2A2A2A"))

            val params = FrameLayout.LayoutParams(
                280.dpToPx(this@MainActivity),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = params
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(this@MainActivity), 24.dpToPx(this@MainActivity),
                24.dpToPx(this@MainActivity), 24.dpToPx(this@MainActivity))
        }

        val title = TextView(this).apply {
            text = "Enter number of bowls"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(title)

        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Number of bowls"

            // Load the last saved value from preferences
            val lastBowlQuantity = prefs.getInt("last_bowl_quantity", 1)
            setText(lastBowlQuantity.toString())
            selectAll() // Select all text for easy replacement

            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#808080"))
            gravity = Gravity.CENTER

            // Fix the input field background
            setBackgroundColor(Color.parseColor("#1A1A1A"))  // Darker than dialog background
            setPadding(16.dpToPx(this@MainActivity), 12.dpToPx(this@MainActivity),
                16.dpToPx(this@MainActivity), 12.dpToPx(this@MainActivity))

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(input)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val cancelButton = Button(this).apply {
            text = "CANCEL"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#424242"))
            setOnClickListener { dialog.dismiss() }
        }

        val okButton = Button(this).apply {
            text = "OK"
            setTextColor(Color.parseColor("#424242"))
            setBackgroundColor(Color.parseColor("#98FB98"))
            setOnClickListener {
                val quantity = input.text.toString().toIntOrNull() ?: 1
                if (quantity > 0) {
                    // Save the quantity to preferences
                    prefs.edit().putInt("last_bowl_quantity", quantity).apply()

                    dialog.dismiss()
                    logBowlsWithQuantity(quantity)
                } else {
                    Toast.makeText(this@MainActivity, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
        }

        buttonLayout.addView(cancelButton)
        buttonLayout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(16.dpToPx(this@MainActivity), 0)
        })
        buttonLayout.addView(okButton)

        contentLayout.addView(buttonLayout)
        card.addView(contentLayout)
        container.addView(card)

        container.setOnClickListener { dialog.dismiss() }

        dialog.setContentView(container)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        dialog.show()

        // Show keyboard
        input.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    // Add this function to log bowls with quantity
    private fun logBowlsWithQuantity(quantity: Int) {
        if (quantity <= 0) return

        Log.d(TAG, "🎯 Logging $quantity bowls")

        if (quantity == 1) {
            // Single bowl - normal flow but with special handling
            pendingBowlQuantity = 1
            confettiHelper.showConfettiFromButton(binding.btnAddBowl)

            // Store current auto mode and temporarily disable it for bowls
            val originalAutoMode = isAutoMode
            isAutoMode = false

            logHitSafe(ActivityType.BOWL)

            // Restore auto mode after a delay
            handler.postDelayed({
                isAutoMode = originalAutoMode
            }, 100)
        } else {
            // Multiple bowls - bypass the synchronization in logHitSafe
            lifecycleScope.launch {
                val now = System.currentTimeMillis()

                // Get the current selected smoker
                val selectedPosition = binding.spinnerSmoker.selectedItemPosition
                val organizedSmokers = organizeSmokers().flatMap { it.smokers }
                val selectedSmoker = organizedSmokers.getOrNull(selectedPosition)

                if (selectedSmoker == null) {
                    Toast.makeText(this@MainActivity, "Please select a smoker", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Store current auto mode and disable it
                val originalAutoMode = isAutoMode
                isAutoMode = false

                // Create multiple entries with slightly different timestamps
                for (i in 0 until quantity) {
                    val timestamp = now + (i * 100) // 100ms apart

                    // Log directly without going through logHitSafe
                    logHit(ActivityType.BOWL, timestamp)

                    // Small delay to ensure database writes complete
                    delay(50)
                }

                // Restore auto mode
                isAutoMode = originalAutoMode

                // Show confetti after all bowls are logged
                withContext(Dispatchers.Main) {
                    confettiHelper.showConfettiFromButton(binding.btnAddBowl)

                    // Refresh stats
                    if (currentShareCode == null) {
                        refreshLocalSessionStatsIfNeeded()
                    }
                }
            }
        }

        // Reset pending quantity
        pendingBowlQuantity = 1
    }

    // Setup button with both click and long-press support for retroactive logging
    private fun setupRetroactiveButton(button: View, activityType: ActivityType) {
        var longPressHandler: Handler? = null
        var longPressRunnable: Runnable? = null
        
        // Handle both touch down and up events to detect long press
        button.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Start tracking long press
                    longPressStartTime = System.currentTimeMillis()
                    isLongPressing = false
                    
                    // No vibration on touch down, only on long press
                    
                    // Create handler for long press detection
                    longPressHandler = Handler(Looper.getMainLooper())
                    longPressRunnable = Runnable {
                        if (!isLongPressing) {
                            isLongPressing = true
                            // Long press detected - show retroactive dialog
                            vibrateFeedback(2000) // 2 second vibration for long press
                            showRetroactiveAddDialog(activityType)
                        }
                    }
                    longPressHandler?.postDelayed(longPressRunnable!!, LONG_PRESS_DURATION)
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    // Cancel long press detection
                    longPressHandler?.removeCallbacks(longPressRunnable!!)
                    
                    if (!isLongPressing) {
                        // It was a regular click (not long press)
                        val pressDuration = System.currentTimeMillis() - longPressStartTime
                        if (pressDuration < LONG_PRESS_DURATION) {
                            // Regular click action
                            vibrateFeedback(50)
                            
                            // Reset previous button
                            lastSelectedActivityButton?.let { setActivityButtonSelected(it, false) }
                            
                            // Set this button as selected
                            setActivityButtonSelected(button as Button, true)
                            lastSelectedActivityButton = button as Button
                            
                            // Your existing code
                            confettiHelper.showConfettiFromButton(button)
                            
                            // Track countdown timing when activity is logged
                            val now = System.currentTimeMillis()
                            countdownStartTime = now
                            
                            logHitSafe(activityType)
                        }
                    }
                    isLongPressing = false
                    true
                }
                else -> false
            }
        }
    }

    // Show retroactive add dialog
    private fun showRetroactiveAddDialog(activityType: ActivityType) {
        // Prevent showing dialog if session is not active
        if (!sessionActive) {
            Toast.makeText(this, "Please start a session first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (retroactiveDialog?.isShowing == true) {
            return
        }
        
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        retroactiveDialog = dialog
        
        // Create the main container with semi-transparent background
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#CC000000")) // Darker background
            isClickable = true
            setOnClickListener { dialog.dismiss() }
        }
        
        // Create card for the popup content
        val card = CardView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                350.dpToPx(this@MainActivity),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            radius = 16.dpToPx(this@MainActivity).toFloat()
            cardElevation = 8.dpToPx(this@MainActivity).toFloat()
            setCardBackgroundColor(Color.parseColor("#1E1E1E"))
        }
        
        // Main content layout
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(this@MainActivity), 24.dpToPx(this@MainActivity),
                24.dpToPx(this@MainActivity), 24.dpToPx(this@MainActivity))
        }
        
        // Title
        val title = TextView(this).apply {
            text = "Add ${activityType.name.lowercase().capitalize()} Activities"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(title)
        
        // Quantity section
        val quantityTitle = TextView(this).apply {
            text = "How many?"
            textSize = 14f
            setTextColor(Color.parseColor("#B0B0B0"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(quantityTitle)
        
        // Quantity buttons in a grid
        val quantityGrid = GridLayout(this).apply {
            columnCount = 4
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dpToPx(this@MainActivity)
            }
        }
        
        var selectedQuantity = 1
        val quantityButtons = mutableListOf<Button>()
        
        // Create quantity buttons (1, 2, 3, More)
        val quantities = listOf(1, 2, 3, -1) // -1 represents "More"
        quantities.forEach { qty ->
            val btn = Button(this).apply {
                text = if (qty == -1) "More" else qty.toString()
                textSize = 16f
                setTextColor(if (qty == 1) Color.parseColor("#1E1E1E") else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8.dpToPx(this@MainActivity).toFloat()
                    setColor(if (qty == 1) Color.parseColor("#98FB98") else Color.parseColor("#2C2C2C"))
                    setStroke(2.dpToPx(this@MainActivity), Color.parseColor("#444444"))
                }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 70.dpToPx(this@MainActivity)
                    height = 50.dpToPx(this@MainActivity)
                    setMargins(4.dpToPx(this@MainActivity), 4.dpToPx(this@MainActivity),
                        4.dpToPx(this@MainActivity), 4.dpToPx(this@MainActivity))
                }
                
                setOnClickListener {
                    if (qty == -1) {
                        // Show number input dialog
                        showQuantityInputDialog { inputQty ->
                            selectedQuantity = inputQty
                            // Update button visuals
                            quantityButtons.forEach { b ->
                                (b.background as? GradientDrawable)?.setColor(Color.parseColor("#2C2C2C"))
                                b.setTextColor(Color.WHITE)
                            }
                            (background as? GradientDrawable)?.setColor(Color.parseColor("#98FB98"))
                            setTextColor(Color.parseColor("#1E1E1E")) // Dark grey text when selected
                            text = inputQty.toString()
                        }
                    } else {
                        selectedQuantity = qty
                        // Update button visuals
                        quantityButtons.forEach { b ->
                            (b.background as? GradientDrawable)?.setColor(Color.parseColor("#2C2C2C"))
                            b.setTextColor(Color.WHITE)
                            if (b.text == "More" && b.text.toString().toIntOrNull() != null) {
                                b.text = "More"
                            }
                        }
                        (background as? GradientDrawable)?.setColor(Color.parseColor("#98FB98"))
                        setTextColor(Color.parseColor("#1E1E1E"))
                    }
                }
            }
            quantityButtons.add(btn)
            quantityGrid.addView(btn)
        }
        
        // First button already selected by default initialization above
        
        contentLayout.addView(quantityGrid)
        
        // Custom quantity input
        val customQuantityLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20.dpToPx(this@MainActivity)
            }
            visibility = View.GONE // Hidden by default
        }
        
        val customQuantityInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Enter quantity"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#808080"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        customQuantityLayout.addView(customQuantityInput)
        
        contentLayout.addView(customQuantityLayout)
        
        // Time control section
        val timeTitle = TextView(this).apply {
            text = "Time Control"
            textSize = 14f
            setTextColor(Color.parseColor("#B0B0B0"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dpToPx(this@MainActivity)
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(timeTitle)
        
        // Time mode options
        var selectedTimeMode = 0 // 0=Time Travel, 1=Current Time (spaced), 2=Current Time (instant)
        val timeModeOptions = listOf(
            "⏪ Time Travel Back" to "Go back to when timer hit 0",
            "⏰ Stay at Current Time" to "Space between last activity",
            "🚫 No Spacing" to "Add all at current timestamp"
        )
        
        val timeModeButtons = mutableListOf<LinearLayout>()
        
        timeModeOptions.forEachIndexed { index, (title, desc) ->
            val optionLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8.dpToPx(this@MainActivity).toFloat()
                    setColor(if (index == 0) Color.parseColor("#98FB98") else Color.parseColor("#2C2C2C"))
                    setStroke(2.dpToPx(this@MainActivity), Color.parseColor("#444444"))
                }
                setPadding(12.dpToPx(this@MainActivity), 8.dpToPx(this@MainActivity),
                    12.dpToPx(this@MainActivity), 8.dpToPx(this@MainActivity))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dpToPx(this@MainActivity)
                }
                isClickable = true
                
                setOnClickListener {
                    selectedTimeMode = index
                    // Update visuals
                    timeModeButtons.forEach { layout ->
                        (layout.background as? GradientDrawable)?.setColor(Color.parseColor("#2C2C2C"))
                        // Reset text color for all children to white
                        for (i in 0 until layout.childCount) {
                            (layout.getChildAt(i) as? TextView)?.setTextColor(Color.WHITE)
                        }
                    }
                    (background as? GradientDrawable)?.setColor(Color.parseColor("#98FB98"))
                    // Set selected text color to dark grey
                    for (i in 0 until childCount) {
                        (getChildAt(i) as? TextView)?.setTextColor(Color.parseColor("#1E1E1E"))
                    }
                }
            }
            
            val optionTitle = TextView(this).apply {
                text = title
                textSize = 14f
                setTextColor(if (index == 0) Color.parseColor("#1E1E1E") else Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            optionLayout.addView(optionTitle)
            
            val optionDesc = TextView(this).apply {
                text = desc
                textSize = 12f
                setTextColor(if (index == 0) Color.parseColor("#1E1E1E") else Color.parseColor("#808080"))
            }
            optionLayout.addView(optionDesc)
            
            timeModeButtons.add(optionLayout)
            contentLayout.addView(optionLayout)
        }
        
        // Add button
        val addButton = Button(this).apply {
            text = "ADD ACTIVITIES"
            textSize = 16f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8.dpToPx(this@MainActivity).toFloat()
                setColor(Color.parseColor("#98FB98"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                56.dpToPx(this@MainActivity)
            ).apply {
                topMargin = 20.dpToPx(this@MainActivity)
            }
            
            setOnClickListener {
                // Vibrate on add
                vibrateFeedback(50)
                
                // Get custom quantity if needed
                val finalQuantity = if (customQuantityInput.text.isNotEmpty()) {
                    customQuantityInput.text.toString().toIntOrNull() ?: selectedQuantity
                } else {
                    selectedQuantity
                }
                
                // Add retroactive activities based on selected mode
                addRetroactiveActivities(activityType, finalQuantity, selectedTimeMode)
                
                // Dismiss dialog
                dialog.dismiss()
            }
        }
        contentLayout.addView(addButton)
        
        // Cancel button
        val cancelButton = TextView(this).apply {
            text = "CANCEL"
            textSize = 14f
            setTextColor(Color.parseColor("#808080"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dpToPx(this@MainActivity)
            }
            
            setOnClickListener {
                dialog.dismiss()
            }
        }
        contentLayout.addView(cancelButton)
        
        card.addView(contentLayout)
        container.addView(card)
        
        dialog.setContentView(container)
        
        // Animate dialog entry
        card.scaleX = 0.8f
        card.scaleY = 0.8f
        card.alpha = 0f
        card.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        dialog.show()
    }
    
    // Show quantity input dialog for "More" option
    private fun showQuantityInputDialog(onQuantitySelected: (Int) -> Unit) {
        val inputDialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Enter Quantity")
            .setView(EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                hint = "Number of activities"
                id = android.R.id.edit
            })
            .setPositiveButton("OK") { dialog, _ ->
                val input = (dialog as AlertDialog).findViewById<EditText>(android.R.id.edit)
                val quantity = input?.text?.toString()?.toIntOrNull() ?: 1
                onQuantitySelected(quantity.coerceIn(1, 99))
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        inputDialog.show()
    }
    
    // Add retroactive activities based on selected mode
    private fun addRetroactiveActivities(activityType: ActivityType, quantity: Int, timeMode: Int) {
        lifecycleScope.launch {
            try {
                val now = System.currentTimeMillis()
                retroactiveActivities.clear() // Clear previous bulk add for undo
                
                // Calculate timestamps based on time mode
                val timestamps = when (timeMode) {
                    0 -> { // Time Travel Back
                        // Space activities between countdownStartTime and countdownEndTime
                        if (countdownEndTime > 0 && countdownStartTime > 0 && quantity > 1) {
                            val interval = (countdownEndTime - countdownStartTime) / quantity
                            List(quantity) { i ->
                                countdownStartTime + (interval * i)
                            }
                        } else {
                            // Fallback: add all at countdown end time or current time
                            val baseTime = if (countdownEndTime > 0) countdownEndTime else now
                            List(quantity) { baseTime - (it * 1000) } // 1 second apart
                        }
                    }
                    1 -> { // Stay at Current Time (spaced)
                        // Space between last activity and now
                        val lastActivityTime = activitiesTimestamps.maxOrNull() ?: sessionStart
                        if (quantity > 1 && lastActivityTime < now) {
                            val interval = (now - lastActivityTime) / quantity
                            List(quantity) { i ->
                                lastActivityTime + (interval * (i + 1))
                            }
                        } else {
                            List(quantity) { now - (it * 1000) } // 1 second apart
                        }
                    }
                    2 -> { // No Spacing - add small offset to prevent treating as one
                        List(quantity) { index -> now - (index * 100) } // 100ms apart to ensure uniqueness
                    }
                    else -> List(quantity) { now }
                }
                
                // Get current selected smoker
                val selectedPosition = binding.spinnerSmoker.selectedItemPosition
                val organizedSmokers = organizeSmokers().flatMap { it.smokers }
                val selectedSmoker = organizedSmokers.getOrNull(selectedPosition)
                
                if (selectedSmoker == null) {
                    Toast.makeText(this@MainActivity, "Please select a smoker", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Store original auto mode and spinner position
                val wasAutoMode = isAutoMode
                val originalPosition = selectedPosition
                
                // Temporarily disable auto-advance for bulk adds
                isAutoMode = false
                
                // Add activities with calculated timestamps
                timestamps.forEachIndexed { index, timestamp ->
                    // Ensure spinner stays on same smoker
                    if (binding.spinnerSmoker.selectedItemPosition != originalPosition) {
                        binding.spinnerSmoker.setSelection(originalPosition)
                    }
                    
                    // Use the internal logHit function with specific timestamp
                    logHit(activityType, timestamp)
                    
                    // Track timestamp for undo
                    retroactiveActivities.add(timestamp)
                    
                    // Small delay between additions for visual feedback
                    if (index < timestamps.size - 1) {
                        delay(50)
                    }
                }
                
                // Re-enable auto-advance if it was on
                isAutoMode = wasAutoMode
                
                // Update all stats immediately
                withContext(Dispatchers.Main) {
                    // Force refresh all stats
                    refreshLocalSessionStatsIfNeeded()
                    sessionStatsVM.recalculateGaps()
                    val historyFragment = supportFragmentManager.findFragmentByTag("history") as? HistoryFragment
                    historyFragment?.refreshHistory()
                    val graphFragment = supportFragmentManager.findFragmentByTag("graph") as? GraphFragment
                    graphFragment?.refreshGraph()
                    
                    // Show confirmation
                    val message = when (timeMode) {
                        0 -> "Added $quantity ${activityType.name.lowercase()} activities (time traveled)"
                        1 -> "Added $quantity ${activityType.name.lowercase()} activities (spaced)"
                        2 -> "Added $quantity ${activityType.name.lowercase()} activities"
                        else -> "Added $quantity ${activityType.name.lowercase()} activities"
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    
                    // Advance to next smoker if in auto mode
                    if (isAutoMode && quantity > 0) {
                        handler.postDelayed({
                            nextSmoker()
                        }, 100)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error adding retroactive activities", e)
                Toast.makeText(this@MainActivity, "Error adding activities", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // In MainActivity onCreate, after binding = ActivityMainBinding.inflate(layoutInflater)
// The binding.btnNotificationToggle will be automatically available if you add the button to your layout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // CRITICAL: Initialize prefs FIRST before using it
        prefs = getSharedPreferences("sesh", Context.MODE_PRIVATE)

        // Initialize helpers
        confettiHelper = ConfettiHelper(this)
        confettiHelper.setupKonfettiOverlay(this)

        // Initialize cloud services and restore session
        initializeCloudServices()
        initializeSmokerManager()
        restoreSessionFromPrefs()
        setupSpinnerNew()

        initializeSupportMessagesWatcher()

        setupVibrationToggle()
        setupLayoutRotation()

        // Initialize Stash ViewModel if not already initialized by delegation
        if (!::stashViewModel.isInitialized) {
            stashViewModel = ViewModelProvider(this).get(StashViewModel::class.java)
        }

        // Initialize goal service
        goalService = GoalService(application)

        // Setup stash integration observer
        stashViewModel.currentStash.observe(this) { stashData ->
            if (stashData != null && stashIntegration == null) {
                stashIntegration = StashIntegration(
                    repository = repo,
                    stashViewModel = stashViewModel,
                    coroutineScope = lifecycleScope
                )
                Log.d(TAG, "Stash integration initialized")
            }
        }

        // Setup UI components
        setupTabs()
        askNotificationPermission()
        triggerInitialNotifications()
        setupSessionControls()
        setupRewindButton()
        updateUIForSessionState()

        // Setup broadcast receiver for smoker updates
        setupSmokerUpdateReceiver()

        // Initialize timer and auto-add components
        initializeTimerSoundAndAutoAdd()
        setupAutoAddControls()
        setupTimerSoundButton()

        // CRITICAL FIX: Restore the last completed session ID after ViewModels are ready
        val lastCompletedId = prefs.getLong("last_completed_session_id", 0L)
        if (lastCompletedId > 0) {
            Log.d(TAG, "📊 Restoring last completed session ID: $lastCompletedId")
            sessionStatsVM.lastCompletedSessionId = lastCompletedId
            stashViewModel.setLastCompletedSessionId(lastCompletedId)

            // Also update activities if they're missing session IDs
            lifecycleScope.launch(Dispatchers.IO) {
                val activitiesInSession = repo.getActivitiesBySessionId(lastCompletedId)
                if (activitiesInSession.isEmpty()) {
                    // Try to find activities in the time range and update them
                    Log.d(TAG, "📊 No activities found for session $lastCompletedId, checking for activities to update...")
                    val sessionEndTime = lastCompletedId + (2 * 60 * 60 * 1000L) // Assume max 2 hour session
                    repo.updateSessionIdsForTimeRange(lastCompletedId, lastCompletedId, sessionEndTime)
                    Log.d(TAG, "📊 Updated session IDs for activities in range")
                }
            }
        }

        // Setup button click listeners with long-press support for retroactive logging
        setupRetroactiveButton(binding.btnAddJoint, ActivityType.JOINT)
        setupRetroactiveButton(binding.btnAddCone, ActivityType.CONE)
        setupRetroactiveButton(binding.btnAddBowl, ActivityType.BOWL)

        setupBowlLongPress()

        // Bowl button to debug offline queue (commented out)
      //  binding.btnAddBowl.setOnLongClickListener {
       //     debugOfflineQueue()
       //     true
      //  }

        // [KEEP ALL YOUR EXISTING TOUCH LISTENERS AND OTHER SETUP CODE HERE]

        // ADD THIS AT THE END OF onCreate:
        // Start network monitoring for sync status
        startNetworkMonitoring()

        // Start network monitoring for sync status
        startNetworkMonitoring()

// Initialize offline queue system
        loadOfflineQueue()
        startOfflineSyncChecker()
        debugOfflineQueue() // Initial debug output

// After all initialization is done, check for active session
        checkAndRestoreActiveSession()
        
        // Show welcome screen on first launch
        showWelcomeScreenIfNeeded()
    }

    private fun initializeSmokerManager() {
        smokerManager = SmokerManager(
            context = this,
            repository = repo,
            lifecycleScope = lifecycleScope,
            authManager = authManager,
            cloudSyncService = cloudSyncService,
            sessionSyncService = sessionSyncService
        )

        // Set up callbacks
        smokerManager.onSyncCloudSmoker = { smoker -> syncCloudSmoker(smoker) }
        smokerManager.onRefreshCloudSmokerName = { smoker -> refreshCloudSmokerName(smoker) }
        smokerManager.onEditSmoker = { smoker -> showEditSmokerDialog(smoker) }
        smokerManager.onChangePassword = { smoker -> showChangePasswordDialog(smoker) }
        smokerManager.onTogglePause = { smoker -> toggleSmokerPause(smoker) }
        smokerManager.onDeleteSmoker = { smoker ->
            showThemedDeleteConfirmationForSmoker(smoker) { confirmed ->
                if (confirmed) {
                    deleteSmokerFromRoom(smoker)
                }
            }
        }
        smokerManager.onUpdateSyncStatusDot = { dot, smoker -> updateSyncStatusDot(dot, smoker) }

        // Sync state
        // CURRENT (ERROR):
        smokerManager.randomFontsEnabled = prefs.getBoolean("random_fonts_enabled", true)
        smokerManager.colorChangingEnabled = prefs.getBoolean("color_changing_enabled", true)

// FIX - Access through smokerManager:
        smokerManager.randomFontsEnabled = prefs.getBoolean("random_fonts_enabled", true)
        smokerManager.colorChangingEnabled = prefs.getBoolean("color_changing_enabled", true)
    }

    private fun setupSpinnerNew() {
        val spinner: Spinner = binding.spinnerSmoker

        setupSpinnerLongPress(spinner)

        smokerAdapterNew = SmokerAdapter(
            context = this,
            layoutInflater = layoutInflater,
            smokerManager = smokerManager,
            onAddSmokerClick = { addSmokerDialog.show() },
            onDeleteAllClick = { showThemedDeleteAllDialog() },
            onSmokerSelected = { smoker ->
                if (smoker == null) {
                    addSmokerDialog.show()
                } else {
                    handleSmokerSelection(smoker)
                }
            }
        )

        spinner.adapter = smokerAdapterNew

        spinner.dropDownVerticalOffset = 8

        repo.allSmokers.observe(this) { list ->
            handleSmokersListUpdate(list)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val sel = smokerAdapterNew.getItem(pos)
                if (sel == null) {
                    if (smokers.isEmpty()) {
                        addSmokerDialog.show()
                    } else {
                        addSmokerDialog.show()
                    }
                } else {
                    handleSmokerSelection(sel)
                }
                smokerManager.dismissSpinnerDropDown()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun handleSmokersListUpdate(list: List<Smoker>) {
        val app = application as CloudCounterApplication
        val previous = app.defaultSmokerId

        smokers = list

        // Update manager state
        smokerManager.currentShareCode = currentShareCode
        smokerManager.pausedSmokerIds.clear()
        smokerManager.pausedSmokerIds.addAll(pausedSmokerIds)
        smokerManager.awaySmokers.clear()
        smokerManager.awaySmokers.addAll(awaySmokers)

        smokerAdapterNew.refreshOrganizedList(smokers, currentShareCode, pausedSmokerIds, awaySmokers)

        val sections = organizeSmokers()
        val organizedSmokers = sections.flatMap { it.smokers }

        if (previous == 0L && organizedSmokers.isNotEmpty()) {
            app.defaultSmokerId = organizedSmokers[0].smokerId
            binding.spinnerSmoker.setSelection(0, false)
        } else {
            val defIdx = organizedSmokers.indexOfFirst { it.smokerId == previous }
            if (defIdx >= 0) {
                binding.spinnerSmoker.setSelection(defIdx, false)
            } else if (organizedSmokers.isNotEmpty()) {
                app.defaultSmokerId = organizedSmokers[0].smokerId
                binding.spinnerSmoker.setSelection(0, false)
            }
        }
    }

    private fun handleSmokerSelection(smoker: Smoker) {
        if (smoker.isCloudSmoker && smoker.passwordHash != null && !smoker.isPasswordVerified) {
            passwordDialog.showVerifyPasswordDialog(
                smokerName = smoker.name,
                onPasswordEntered = { pw ->
                    verifyPasswordAndSelectSmoker(smoker, pw, binding.spinnerSmoker.selectedItemPosition)
                }
            )
        } else {
            selectSmoker(smoker)
        }
    }


    fun getCurrentShareCode(): String? {
        return currentShareCode
    }

    // TEMPORARY DEBUG FUNCTION
    private fun debugRoomsAfterEndSession() {
        lifecycleScope.launch {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val snapshot = firestore.collection("rooms").get().await()

                Log.d(TAG, "🔍 === ROOMS DEBUG AFTER END SESSION ===")
                Log.d(TAG, "🔍 Total rooms found: ${snapshot.documents.size}")

                snapshot.documents.forEach { doc ->
                    val room = doc.toObject(RoomData::class.java)
                    Log.d(TAG, "🔍 Room ${doc.id}:")
                    Log.d(TAG, "    Name: ${room?.name}")
                    Log.d(TAG, "    Active: ${room?.active}")
                    Log.d(TAG, "    Participants: ${room?.participants?.size}")
                    Log.d(TAG, "    Active participants: ${room?.activeParticipants?.size}")
                    Log.d(TAG, "    Activities: ${room?.activities?.size}")
                }

                // Test the same query that getActiveRooms() uses
                val activeRooms = sessionSyncService.getActiveRooms()
                activeRooms.fold(
                    onSuccess = { rooms ->
                        Log.d(TAG, "🔍 getActiveRooms() returned: ${rooms.size} rooms")
                        rooms.forEach { room ->
                            Log.d(TAG, "🔍   Active room: ${room.name} (${room.shareCode})")
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "🔍 getActiveRooms() failed: ${error.message}")
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "🔍 Debug error: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        // Stop network monitoring
        stopNetworkMonitoring()

        // Stop offline sync checker
        stopOfflineSyncChecker()

        // Save any pending offline activities
        saveOfflineQueue()

        // Stop all shimmer animations
        addSmokerShimmerAnimation?.stopShimmer()
        addSmokerShimmerAnimation = null

        // Clean up spinner animations
        try {
            val spinner = binding.spinnerSmoker
            val selectedView = spinner.selectedView
            if (selectedView != null) {
                val container = selectedView as? FrameLayout
                val linearLayout = container?.getChildAt(0) as? LinearLayout
                val textView = linearLayout?.findViewById<TextView>(R.id.textName)
                (textView?.tag as? ShimmerTextAnimation)?.stopShimmer()
                textView?.tag = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up spinner animations", e)
        }

        // Stop session sync service listeners - FIXED
        spinnerLongPressRunnable?.let { runnable ->
            spinnerLongPressHandler?.removeCallbacks(runnable)
        }
        spinnerLongPressHandler = null
        spinnerLongPressRunnable = null
        sessionSyncService.stopAllListeners()

        // Remove timer callbacks
        handler.removeCallbacks(timerRunnable)

        // Cleanup helpers
        confettiHelper.cleanup()
        timerSoundHelper.cleanup()
        autoAddManager.cleanup()
        goalService.cleanup()

        // Unregister broadcast receivers
        try {
            if (::smokerUpdateReceiver.isInitialized) {
                unregisterReceiver(smokerUpdateReceiver)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering smoker update receiver: ${e.message}")
        }

        try {
            if (::undoReceiver.isInitialized) {
                unregisterReceiver(undoReceiver)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering undo receiver: ${e.message}")
        }

        try {
            if (::rewindReceiver.isInitialized) {
                unregisterReceiver(rewindReceiver)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering rewind receiver: ${e.message}")
        }

        try {
            if (::deletionReceiver.isInitialized) {
                unregisterReceiver(deletionReceiver)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering deletion receiver: ${e.message}")
        }

        super.onDestroy()
    }


    private fun initializeCloudServices() {
        authManager = (application as CloudCounterApplication).authManager
        cloudSyncService = (application as CloudCounterApplication).cloudSyncService
        sessionSyncService = SessionSyncService(repository = repo)
        passwordDialog = PasswordDialog(this)

        addSmokerDialog = AddSmokerDialog(
            context = this,
            cloudSyncService = cloudSyncService,
            authManager = authManager,
            googleSignInLauncher = googleSignInLauncher,
            lifecycleScope = lifecycleScope,
            // THE CRITICAL FIX IS HERE: Use upsert() instead of insert()
            onSmokerAdded = { smoker ->
                Log.d("WELCOME_DEBUG", "🎯 onSmokerAdded called - smoker: ${smoker.name}, isCloud: ${smoker.isCloudSmoker}")
                lifecycleScope.launch(Dispatchers.IO) {
                    repo.insertOrUpdateSmoker(smoker)
                    Log.d("WELCOME_DEBUG", "✅ Smoker inserted/updated in DB")
                    
                    // Check if this is the first cloud smoker and show welcome screen
                    Log.d("WELCOME_DEBUG", "🔍 Checking if smoker is cloud smoker: ${smoker.isCloudSmoker}")
                    if (smoker.isCloudSmoker) {
                        val allSmokers = repo.getAllSmokersSync()
                        val cloudSmokerCount = allSmokers.count { it.isCloudSmoker }
                        Log.d("WELCOME_DEBUG", "📊 Total smokers: ${allSmokers.size}, Cloud smokers: $cloudSmokerCount")
                        Log.d("WELCOME_DEBUG", "📋 All smokers: ${allSmokers.map { "${it.name}(cloud:${it.isCloudSmoker})" }}")
                        
                        // Show welcome screen only for the first cloud smoker
                        if (cloudSmokerCount == 1) {
                            Log.d("WELCOME_DEBUG", "🎉 First cloud smoker detected! Showing welcome screen...")
                            withContext(Dispatchers.Main) {
                                showWelcomeScreenForFirstCloudSmoker()
                            }
                        } else {
                            Log.d("WELCOME_DEBUG", "❌ Not first cloud smoker (count: $cloudSmokerCount), skipping welcome")
                        }
                    } else {
                        Log.d("WELCOME_DEBUG", "❌ Not a cloud smoker, skipping welcome check")
                    }
                }
            },
            getCurrentShareCode = { currentShareCode },
            sessionSyncService = sessionSyncService,
            repository = repo // ADD THIS
        )
    }

    private fun initializeSupportMessagesWatcher() {
        supportMessagesWatcher = SupportMessagesWatcher(this)
    }

    private fun restoreSessionFromPrefs() {
        sessionActive = prefs.getBoolean("sessionActive", false)
        if (sessionActive) {
            sessionStart = prefs.getLong("sessionStart", System.currentTimeMillis())
            lastLogTime = prefs.getLong("lastLogTime", sessionStart)
            lastIntervalMillis = prefs.getLong("lastInterval", 0L)
            roundsLeft = prefs.getInt("roundsLeft", 0)
            hitsThisRound = prefs.getInt("hitsThisRound", 0)
            actualRounds = prefs.getInt("actualRounds", 0)
            initialRoundsSet = prefs.getInt("initialRoundsLeft", roundsLeft)
            currentShareCode = prefs.getString("currentShareCode", null)
            currentRoomName = prefs.getString("currentRoomName", null)

            // ADD: Debug logging
            Log.d(TAG, "🏠 DEBUG: Restored currentShareCode = $currentShareCode")
            Log.d(TAG, "🏠 DEBUG: Restored currentRoomName = $currentRoomName")

            sessionStatsVM.startSession(sessionStart)

            // ADD: Set room info if we have it after restore
            if (currentShareCode != null && currentRoomName != null) {
                Log.d(TAG, "🏠 DEBUG: Setting room info after restore: $currentRoomName ($currentShareCode)")
                sessionStatsVM.setRoomInfo(currentRoomName!!, currentShareCode!!)
            }

            handler.post(timerRunnable)

            currentShareCode?.let { shareCode ->
                reconnectToRoom(shareCode)
            }
        }
    }

    private fun reconnectToRoom(shareCode: String) {
        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "🔄 Cannot reconnect to room - not signed in")
                // Toast.makeText(this@MainActivity, "Please sign in to reconnect to session", Toast.LENGTH_LONG).show()
                return@launch
            }

            Log.d(TAG, "🔄 Attempting to reconnect to room: $shareCode")

            // Try to rejoin the room and return from away status
            sessionSyncService.joinRoom(userId, shareCode).fold(
                onSuccess = { room ->
                    Log.d(TAG, "🔄 Successfully reconnected to room: ${room.name}")
                    currentRoomName = room.name
                    currentRoom = room

                    // Return from away status and mark as current smoker
                    sessionSyncService.returnFromAway(userId, shareCode)
                    sessionSyncService.markActive(userId, shareCode)

                    // Set this user as the current smoker when rejoining
                    val userSmoker = smokers.find { it.cloudUserId == userId }
                    userSmoker?.let { smoker ->
                        val smokerIndex = smokers.indexOf(smoker)
                        if (smokerIndex >= 0) {
                            binding.spinnerSmoker.setSelection(smokerIndex)
                            selectSmoker(smoker)
                            Log.d(TAG, "🔄 Set rejoining user as current smoker: ${smoker.name}")
                        }
                    }

                    startRoomListener(shareCode)
                    // REMOVED: Toast.makeText(this@MainActivity, "Reconnected to room: ${room.name}", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Log.e(TAG, "🔄 Failed to reconnect to room: ${error.message}")
                    Toast.makeText(this@MainActivity, "Failed to reconnect to session room: ${error.message}", Toast.LENGTH_LONG).show()

                    // Clear the session since room is no longer available
                    endSession()
                }
            )
        }
    }

    private fun saveSessionToPrefs() {
        prefs.edit()
            .putBoolean("sessionActive", sessionActive)
            .putLong("sessionStart", sessionStart)
            .putLong("lastLogTime", lastLogTime)
            .putLong("actualLastLogTime", actualLastLogTime)
            .putLong("lastInterval", lastIntervalMillis)
            .putInt("roundsLeft", roundsLeft)
            .putInt("hitsThisRound", hitsThisRound)
            .putInt("actualRounds", actualRounds)
            .putInt("initialRoundsLeft", initialRoundsSet)
            .putString("currentShareCode", currentShareCode)
            .putString("currentRoomName", currentRoomName)
            .putBoolean("isAutoMode", isAutoMode)
            .putLong("defaultSmokerId", (application as CloudCounterApplication).defaultSmokerId)
            .putLong("rewindOffset", rewindOffset)  // Save rewind offset
            .putString("activitiesTimestamps", activitiesTimestamps.joinToString(","))
            .apply()
    }


    // Replace the entire setupSpinnerLongPress() function:
    private fun setupSpinnerLongPress(spinner: Spinner) {
        // This handler logic is now entirely managed by the SmokerManager
        // We just need to pass the touch events to it.
        spinner.setOnTouchListener { view, event ->
            // Delegate all the complex logic to the manager
            val handled = smokerManager.handleLongPress(view, event, binding.spinnerSmoker, smokerAdapterNew)

            // The manager will tell us if it consumed the event (i.e., a long press happened)
            // If it returns true, we don't want the spinner's default click to happen.
            handled
        }
    }


    private fun vibrateFeedback(duration: Long = 50) {
        // Only vibrate if vibrations are enabled
        if (!vibrationsEnabled) return

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun setupVibrationToggle() {
        // Load vibration preference
        vibrationsEnabled = prefs.getBoolean("vibrations_enabled", true)
        updateVibrationButtonState()

        binding.btnVibrationToggle.setOnClickListener {
            toggleVibrations()
        }
    }

    private fun toggleVibrations() {
        vibrationsEnabled = !vibrationsEnabled
        prefs.edit().putBoolean("vibrations_enabled", vibrationsEnabled).apply()

        updateVibrationButtonState()

        // Animate the button with neon green flash
        animateVibrationToggle()

        val message = if (vibrationsEnabled) "Vibrations enabled" else "Vibrations disabled"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateVibrationButtonState() {
        val iconRes = if (vibrationsEnabled) {
            R.drawable.ic_vibration_on
        } else {
            R.drawable.ic_vibration_off
        }
        binding.btnVibrationToggle.setImageResource(iconRes)
    }

    private fun animateVibrationToggle() {
        val originalTint = ContextCompat.getColor(this, android.R.color.darker_gray)
        val neonGreen = Color.parseColor("#98FB98")

        // Create color animation from neon green to grey
        val colorAnimation = ValueAnimator.ofArgb(neonGreen, originalTint).apply {
            duration = 2000 // 2 seconds
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                binding.btnVibrationToggle.setColorFilter(color)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Ensure final state is grey
                    binding.btnVibrationToggle.setColorFilter(originalTint)
                }
            })
        }

        colorAnimation.start()
    }

    
    private fun setupLayoutRotation() {
        // Load layout position preference (false = top, true = bottom)
        var isLayoutAtBottom = prefs.getBoolean("layout_at_bottom", false)
        updateLayoutPosition(isLayoutAtBottom)
        
        binding.btnLayoutRotation.setOnClickListener {
            isLayoutAtBottom = !isLayoutAtBottom
            prefs.edit().putBoolean("layout_at_bottom", isLayoutAtBottom).apply()
            
            updateLayoutPosition(isLayoutAtBottom)
            animateLayoutRotation()
            
            val message = if (isLayoutAtBottom) "Controls moved to bottom" else "Controls moved to top"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateLayoutPosition(isAtBottom: Boolean) {
        val rootLayout = binding.mainActivityRootLayout
        val topSection = binding.topSectionContainer
        val tabLayout = binding.tabLayout
        val viewPager = binding.viewPager
        
        // Get system window insets
        val statusBarHeight = getStatusBarHeight()
        val navigationBarHeight = getNavigationBarHeight()
        
        // Remove views first
        rootLayout.removeView(topSection)
        rootLayout.removeView(tabLayout)
        rootLayout.removeView(viewPager)
        
        // Re-add in the correct order
        if (isAtBottom) {
            // Order: TabLayout, ViewPager, TopSection
            rootLayout.addView(tabLayout)
            rootLayout.addView(viewPager)
            rootLayout.addView(topSection)
            
            // Add top padding to TabLayout when it's at the top to avoid status bar
            tabLayout.setPadding(
                tabLayout.paddingLeft,
                statusBarHeight,
                tabLayout.paddingRight,
                tabLayout.paddingBottom
            )
            
            // Add bottom padding to topSection when it's at the bottom to avoid navigation bar
            topSection.setPadding(
                topSection.paddingLeft,
                topSection.paddingTop,
                topSection.paddingRight,
                navigationBarHeight
            )
            
            // Adjust button container margin when at bottom (normal spacing)
            val buttonContainer = findViewById<LinearLayout>(R.id.buttonContainer)
            val layoutParams = buttonContainer.layoutParams as LinearLayout.LayoutParams
            layoutParams.topMargin = (-19).dpToPx(this) // Original margin
            buttonContainer.layoutParams = layoutParams
            
            // Set ViewPager to take remaining space
            viewPager.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        } else {
            // Order: TopSection, TabLayout, ViewPager (original)
            rootLayout.addView(topSection)
            rootLayout.addView(tabLayout)
            rootLayout.addView(viewPager)
            
            // Reset padding when in normal position
            tabLayout.setPadding(
                tabLayout.paddingLeft,
                0,
                tabLayout.paddingRight,
                tabLayout.paddingBottom
            )
            
            topSection.setPadding(
                topSection.paddingLeft,
                topSection.paddingTop,
                topSection.paddingRight,
                0
            )
            
            // Adjust button container margin when at top (reduced spacing)
            val buttonContainer = findViewById<LinearLayout>(R.id.buttonContainer)
            val layoutParams = buttonContainer.layoutParams as LinearLayout.LayoutParams
            
            // Check if Advanced button is visible (timers showing)
            val isAdvancedVisible = binding.btnToggleTimers.visibility == View.VISIBLE
            if (isAdvancedVisible) {
                layoutParams.topMargin = (-25).dpToPx(this) // Much tighter spacing when Advanced shown
            } else {
                layoutParams.topMargin = (-8).dpToPx(this) // Normal reduced spacing
            }
            buttonContainer.layoutParams = layoutParams
            
            // Set ViewPager to take remaining space
            viewPager.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
    }
    
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
    
    private fun getNavigationBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
    
    private fun animateLayoutRotation() {
        // Animate the rotation button with a spin
        val rotation = ObjectAnimator.ofFloat(binding.btnLayoutRotation, "rotation", 0f, 180f).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
        }
        
        // Flash with neon green color
        val originalTint = ContextCompat.getColor(this, android.R.color.darker_gray)
        val neonGreen = Color.parseColor("#98FB98")
        
        val colorAnimation = ValueAnimator.ofArgb(neonGreen, originalTint).apply {
            duration = 1000
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                binding.btnLayoutRotation.setColorFilter(color)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.btnLayoutRotation.setColorFilter(originalTint)
                }
            })
        }
        
        AnimatorSet().apply {
            playTogether(rotation, colorAnimation)
            start()
        }
    }


    // Add this new function

    // Add this new function for color generation:
    private fun regenerateSmokerColors() {
        smokerManager.clearAllColorCaches()
        // Colors will be generated on-demand when needed
        Log.d(TAG, "🌈 Smoker colors map cleared for regeneration")
    }


    private fun regenerateSmokerFonts() {
        smokerManager.clearAllFontCaches()

        if (smokerManager.randomFontsEnabled) {
            // Shuffle the font list for random order
            val shuffledIndices = fontList.indices.toMutableList()
            shuffledIndices.shuffle(Random)

            // Reset the cycle index
            currentFontCycleIndex = 0

            // Log available fonts
            Log.d(TAG, "🎨 Available fonts (${fontList.size} total):")
            fontList.forEachIndexed { index, fontRes ->
                try {
                    val fontName = resources.getResourceEntryName(fontRes)
                    Log.d(TAG, "🎨   [$index] $fontName")
                } catch (e: Exception) {
                    Log.d(TAG, "🎨   [$index] Unknown font resource")
                }
            }
        }
    }

    private fun applyFontToSpinner() {
        val currentPosition = binding.spinnerSmoker.selectedItemPosition
        if (currentPosition >= 0) {
            val sections = organizeSmokers()
            val organizedSmokers = sections.flatMap { it.smokers }
            val selectedSmoker = organizedSmokers.getOrNull(currentPosition)

            selectedSmoker?.let { smoker ->
                val spinnerView = binding.spinnerSmoker.selectedView
                val container = spinnerView as? FrameLayout
                container?.findViewById<TextView>(R.id.textName)?.let { textView ->
                    val font = smokerManager.getFontForSmoker(smoker.smokerId)
                    textView.typeface = font

                    val color = smokerManager.getColorForSmoker(smoker.smokerId)
                    textView.setTextColor(color)
                }
            }
        }
    }

    // Also add this crash handler to see what's happening:
    private fun setupCrashDebugging() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "💥 UNCAUGHT EXCEPTION in thread ${thread.name}: ${throwable.message}", throwable)
            // Log the full stack trace
            throwable.printStackTrace()
        }
    }


    private fun verifyPasswordAndSelectSmoker(smoker: Smoker, password: String, position: Int) {
        lifecycleScope.launch {
            // ADD THIS LOG STATEMENT
            Log.d(TAG, "Attempting to verify password for '${smoker.name}'...")

            val isValid = smoker.passwordHash
                ?.let { PasswordUtils.verifyPassword(password, it) }
                ?: false

            // ADD THIS LOG STATEMENT
            Log.d(TAG, "Password verification result: $isValid")

            if (isValid) {
                val verified = smoker.copy(isPasswordVerified = true)

                // ADD THIS LOG STATEMENT
                Log.d(TAG, "Password verified! Updating smoker. New isPasswordVerified: ${verified.isPasswordVerified}")

                repo.updateSmoker(verified)
                selectSmoker(verified)
                Toast.makeText(this@MainActivity, "Password verified for ${smoker.name}", Toast.LENGTH_SHORT).show()


                val prefs = getSharedPreferences("smoker_passwords", Context.MODE_PRIVATE)
                prefs.edit().putString(smoker.smokerId.toString(), password).apply()


            } else {
                Toast.makeText(this@MainActivity, "Incorrect password for ${smoker.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Replace the selectSmoker() function (around line 1420-1440):
    private fun selectSmoker(smoker: Smoker) {
        val app = application as CloudCounterApplication
        if (app.defaultSmokerId != smoker.smokerId) {
            app.defaultSmokerId = smoker.smokerId
            statsVM.setSmoker(smoker.smokerId)

            val currentSpinnerIdx = binding.spinnerSmoker.selectedItemPosition
            val sections = organizeSmokers()
            val organizedSmokers = sections.flatMap { it.smokers }
            val correctIdx = organizedSmokers.indexOfFirst { it.smokerId == smoker.smokerId }

            if (currentSpinnerIdx != correctIdx && correctIdx >= 0) {
                binding.spinnerSmoker.setSelection(correctIdx, false)
            }
        }

        // Clear font/color caches if randomization enabled
        smokerManager.clearFontCache(smoker.smokerId)
        smokerManager.clearColorCache(smoker.smokerId)

        applyFontToSpinner()
    }

    private fun setupTabs() {
        val historyFrag = HistoryFragment().apply {
            onDeleteLog = { log ->
                lifecycleScope.launch(Dispatchers.IO) {
                    repo.delete(log)
                }
            }
            onDeleteSummary = { summary ->
                lifecycleScope.launch(Dispatchers.IO) {
                    repo.deleteSummary(summary)
                    authManager.getCurrentUserId()?.let { me ->
                        currentShareCode?.let { code ->
                            sessionSyncService.leaveRoom(me, code)
                        }
                    }
                }
            }
            onResumeSummary = { summary ->
                resumeSession(summary)
            }
            setConfettiHelper(confettiHelper)
        }

        val seshFrag = SeshFragment().apply {
            onResumeSesh = {
                Log.d(TAG, "📱 Resume button clicked in SeshFragment")
                resumeLastSummary()
            }
            setConfettiHelper(confettiHelper)
        }

        val statsFrag = StatsFragment()
        val graphFrag = GraphFragment()
        val stashFrag = StashFragment()

        val chatFrag = ChatFragment().apply {
            setConfettiHelper(confettiHelper)
        }

        val goalFrag = GoalFragment().apply {
            setConfettiHelper(confettiHelper)
        }

        val aboutOrInboxFrag = AboutOrInboxFragment()

        binding.viewPager.adapter =
            ViewPagerAdapter(this, listOf(
                historyFrag,
                seshFrag,
                statsFrag,
                graphFrag,
                stashFrag,
                chatFrag,
                goalFrag,
                aboutOrInboxFrag
            ))

        // Setup tab layout with icons — Option A (stateful tint owned by TabLayout)
       // binding.tabLayout.tabIconTint = android.content.res.ColorStateList(
       //     arrayOf(
        //        intArrayOf(android.R.attr.state_selected), // selected
        //        intArrayOf()                                // unselected
        //    ),
        //    intArrayOf(
       //         ContextCompat.getColor(this, R.color.my_light_primary),                  // selected = bright green
       //         ContextCompat.getColor(this, R.color.tab_unselected_text_color_on_grey)  // unselected = grey
       //     )
      //  )

        com.google.android.material.tabs.TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            when (pos) {
                0 -> tab.setIcon(R.drawable.ic_history_selector)  // Use the selector instead
                1 -> tab.text = getString(R.string.tab_sesh)
                2 -> tab.text = getString(R.string.tab_stats)
                3 -> tab.text = getString(R.string.tab_graph)
                4 -> tab.text = getString(R.string.tab_stash)
                5 -> tab.text = getString(R.string.tab_chat)
                6 -> tab.text = getString(R.string.tab_goals)
                7 -> tab.setIcon(R.drawable.ic_about_selector)  // This stays as is
                else -> tab.text = ""
            }
        }.attach()




// Set consistent text size and appearance for all tabs
        binding.tabLayout.post {
            for (i in 0 until binding.tabLayout.tabCount) {
                val tab = (binding.tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(i)
                tab?.let { tabView ->
                    // Find the TextView in the tab
                    val textView = tabView.findViewById<TextView>(android.R.id.text1)
                    textView?.let {
                        it.textSize = 12f  // Consistent size for all text tabs
                        it.isAllCaps = false  // Disable all caps
                        it.typeface = android.graphics.Typeface.DEFAULT  // Same typeface
                    }
                }
            }
        }

        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            private var previousTabPosition = 0  // Track the previous tab

            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.let {
                    // Update history icon color when unselected
                    if (tab?.position == 0) {
                        val customView = tab.customView as? LinearLayout
                        val iconView = customView?.tag as? android.widget.ImageView
                        iconView?.setColorFilter(
                            ContextCompat.getColor(this@MainActivity, R.color.tab_unselected_text_color_on_grey),
                            android.graphics.PorterDuff.Mode.SRC_IN
                        )
                    }

                    Log.d("MainActivity", "Tab selected: position=${tab.position}")

                    val tabView = (binding.tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(tab.position)
                    tabView?.let { view ->
                        // Pass the previous and current tab positions for directional confetti
                        confettiHelper.showCelebrationBurst(view, previousTabPosition, tab.position)
                    }

                    // Update previous tab position for next time
                    previousTabPosition = tab.position

                    if (tab.position == 5) {
                        val chatFragment = supportFragmentManager.findFragmentByTag("f5") as? ChatFragment
                        if (chatFragment != null) {
                            Log.d("MainActivity", "Found ChatFragment, calling onTabSelected")
                            chatFragment.onTabSelected()
                        } else {
                            Log.d("MainActivity", "ChatFragment not found with tag")
                            val viewPagerFragment = supportFragmentManager.findFragmentById(binding.viewPager.id)
                            if (viewPagerFragment != null) {
                                val childFragments = viewPagerFragment.childFragmentManager.fragments
                                childFragments.filterIsInstance<ChatFragment>().firstOrNull()?.let {
                                    Log.d("MainActivity", "Found ChatFragment via child fragments")
                                    it.onTabSelected()
                                }
                            }
                        }
                    }
                    
                    // Refresh About/Inbox tab when selected (position 7)
                    if (tab.position == 7) {
                        Log.d("MainActivity", "About/Inbox tab selected - refreshing auth state")
                        val aboutOrInboxFragment = supportFragmentManager.findFragmentByTag("f7") as? AboutOrInboxFragment
                        if (aboutOrInboxFragment != null) {
                            Log.d("MainActivity", "Found AboutOrInboxFragment, refreshing auth state")
                            aboutOrInboxFragment.refreshAuthStateAndUI()
                        } else {
                            Log.d("MainActivity", "AboutOrInboxFragment not found with tag, trying child fragments")
                            val viewPagerFragment = supportFragmentManager.findFragmentById(binding.viewPager.id)
                            if (viewPagerFragment != null) {
                                val childFragments = viewPagerFragment.childFragmentManager.fragments
                                childFragments.filterIsInstance<AboutOrInboxFragment>().firstOrNull()?.let {
                                    Log.d("MainActivity", "Found AboutOrInboxFragment via child fragments")
                                    it.refreshAuthStateAndUI()
                                }
                            }
                        }
                    }
                }
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                // Update history icon color when unselected
                if (tab?.position == 0) {
                    val customView = tab.customView as? LinearLayout
                    val iconView = customView?.tag as? android.widget.ImageView
                    iconView?.setColorFilter(
                        ContextCompat.getColor(this@MainActivity, R.color.tab_unselected_text_color_on_grey),
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                }

                Log.d("MainActivity", "Tab unselected: position=${tab?.position}")

                if (tab?.position == 5) {
                    Log.d("MainActivity", "Chat tab unselected - notifying fragment")

                    val chatFragment = supportFragmentManager.findFragmentByTag("f5") as? ChatFragment
                    if (chatFragment != null) {
                        chatFragment.onTabUnselected()
                    } else {
                        val viewPagerFragment = supportFragmentManager.findFragmentById(binding.viewPager.id)
                        if (viewPagerFragment != null) {
                            val childFragments = viewPagerFragment.childFragmentManager.fragments
                            childFragments.filterIsInstance<ChatFragment>().firstOrNull()?.onTabUnselected()
                        }
                    }
                }
            }

            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.let {
                    val tabView = (binding.tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(tab.position)
                    tabView?.let { view ->
                        confettiHelper.showMiniConfettiFromButton(view)
                    }
                }
            }
        })
    }

    fun triggerGoogleSignIn() {
        // Check network first
        if (!authManager.isNetworkAvailable()) {
            // Let ChatFragment handle the no-internet popup
            return
        }

        // Directly trigger Google sign-in
        val signInIntent = authManager.getSignInIntent()
        googleSignInLauncher.launch(signInIntent)
    }



    private fun setupSmokerUpdateReceiver() {
        // Setup undo receiver first
        undoReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.sam.cloudcounter.ACTIVITY_UNDONE") {
                    Log.d(TAG, "📡 Received undo broadcast from notification")

                    // Remove the last matching activity from history
                    val activityType = intent.getStringExtra("activityType")
                    val smokerId = intent.getLongExtra("smokerId", -1)

                    if (activityType != null && smokerId != -1L) {
                        val type = try {
                            ActivityType.valueOf(activityType)
                        } catch (e: Exception) {
                            null
                        }

                        type?.let {
                            // Remove the matching activity from history using removeIf
                            activityHistory.removeIf { activity ->
                                activity.smokerId == smokerId && activity.type == type
                            }
                            updateUndoButtonVisibility()
                        }
                    }

                    // Refresh stats
                    if (currentShareCode == null) {
                        refreshLocalSessionStatsIfNeeded()
                    }

                    // Refresh all fragments
                    sessionStatsVM.recalculateGaps()
                    statsVM.setSmoker((application as CloudCounterApplication).defaultSmokerId)
                }
            }
        }

        // Setup rewind receiver
        rewindReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.sam.cloudcounter.SESSION_REWOUND") {
                    val newRewindOffset = intent.getLongExtra("rewindOffset", 0L)
                    Log.d(TAG, "⏪📡 Received rewind broadcast: offset = ${newRewindOffset}ms")

                    // Update the local rewind offset
                    rewindOffset = newRewindOffset
                    updateTimersForRewind()
                    sessionStatsVM.applyRewindOffset(rewindOffset)

                    if (::autoAddManager.isInitialized) {
                        autoAddManager.applyRewindOffset(rewindOffset)
                    }

                    handler.removeCallbacks(timerRunnable)
                    handler.post(timerRunnable)
                    Log.d(TAG, "⏪📡 Rewind applied from notification")
                }
            }
        }

        // CORRECTED SECTION
        val rewindFilter = IntentFilter("com.sam.cloudcounter.SESSION_REWOUND")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(rewindReceiver, rewindFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(rewindReceiver, rewindFilter)
        }
        Log.d(TAG, "⏪📡 Rewind broadcast receiver registered")
        // END CORRECTED SECTION


        val undoFilter = IntentFilter("com.sam.cloudcounter.ACTIVITY_UNDONE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(undoReceiver, undoFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(undoReceiver, undoFilter)
        }

        // Setup smoker update receiver
        smokerUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "🔄📡 === BROADCAST RECEIVED ===")
                Log.d(TAG, "🔄📡 Action: ${intent?.action}")
                Log.d(TAG, "🔄📡 Package: ${intent?.`package`}")

                if (intent?.action == "com.sam.cloudcounter.UPDATE_SMOKER_SELECTION") {
                    val smokerId = intent.getLongExtra("smokerId", 0L)
                    val smokerName = intent.getStringExtra("smokerName") ?: ""
                    val isCloudSmoker = intent.getBooleanExtra("isCloudSmoker", false)

                    Log.d(TAG, "🔄📡 Received smoker update: $smokerName (ID: $smokerId, cloud: $isCloudSmoker)")
                    Log.d(TAG, "🔄📡 Current spinner position: ${binding.spinnerSmoker.selectedItemPosition}")
                    Log.d(TAG, "🔄📡 Is UI thread: ${Looper.myLooper() == Looper.getMainLooper()}")

                    // Update the spinner selection to match the new smoker
                    val sections = organizeSmokers()
                    val organizedSmokers = sections.flatMap { it.smokers }

                    Log.d(TAG, "🔄📡 Organized smokers count: ${organizedSmokers.size}")
                    organizedSmokers.forEachIndexed { index, smoker ->
                        Log.d(TAG, "🔄📡   [$index] ${smoker.name} (ID: ${smoker.smokerId})")
                    }

                    val smokerIndex = organizedSmokers.indexOfFirst { it.smokerId == smokerId }
                    Log.d(TAG, "🔄📡 Found smoker at index: $smokerIndex")

                    if (smokerIndex >= 0) {
                        runOnUiThread {
                            Log.d(TAG, "🔄📡 Setting spinner selection to $smokerIndex")
                            binding.spinnerSmoker.setSelection(smokerIndex, false)
                            // Force refresh the adapter
                            smokerAdapterNew.notifyDataSetChanged()
                            Log.d(TAG, "🔄📡 Spinner selection set to ${binding.spinnerSmoker.selectedItemPosition}")
                        }
                    } else {
                        Log.w(TAG, "🔄📡 Smoker not found in organized list!")
                    }
                }
                Log.d(TAG, "🔄📡 === BROADCAST HANDLED ===")
            }
        }

        val filter = IntentFilter("com.sam.cloudcounter.UPDATE_SMOKER_SELECTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smokerUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smokerUpdateReceiver, filter)
        }
        Log.d(TAG, "🔄📡 Broadcast receiver registered")


        // Add activity deletion receiver
        deletionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.sam.cloudcounter.ACTIVITY_DELETED") {
                    Log.d(TAG, "📡 Received activity deletion broadcast")

                    val activityType = intent.getStringExtra("activityType")
                    val smokerId = intent.getLongExtra("smokerId", -1)
                    val smokerName = intent.getStringExtra("smokerName") ?: "Unknown"
                    val timestamp = intent.getLongExtra("timestamp", -1)

                    Log.d(TAG, "📡 Deleted activity: $activityType for $smokerName at $timestamp")

                    // Refresh all fragments and stats
                    lifecycleScope.launch {
                        if (currentShareCode == null) {
                            refreshLocalSessionStatsIfNeeded()
                        }
                        if (sessionActive) {
                            refreshNotificationsWithSession()
                        } else {
                            triggerInitialNotifications()
                        }
                        val app = application as CloudCounterApplication
                        statsVM.setSmoker(app.defaultSmokerId)
                        val graphFragment = supportFragmentManager.fragments
                            .find { it is GraphFragment } as? GraphFragment
                        graphFragment?.refreshGraph()
                        sessionStatsVM.recalculateGaps()
                        val historyFragment = supportFragmentManager.fragments
                            .find { it is HistoryFragment } as? HistoryFragment
                        historyFragment?.refreshHistory()
                    }
                }
            }
        }

        val deletionFilter = IntentFilter("com.sam.cloudcounter.ACTIVITY_DELETED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(deletionReceiver, deletionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(deletionReceiver, deletionFilter)
        }
        Log.d(TAG, "📡 All broadcast receivers registered (undo, smoker update, deletion)")
    }

    private fun updateRoundsInRoom() {
        currentShareCode?.let { shareCode ->
            lifecycleScope.launch {
                // Calculate the rounds based on what the user set
                val totalRounds = if (initialRoundsSet > 0) {
                    kotlin.math.max(0, initialRoundsSet - roundsLeft)
                } else {
                    0 // Infinite rounds
                }

                sessionSyncService.updateRoundsInRoom(shareCode, totalRounds).fold(
                    onSuccess = {
                        Log.d(TAG, "🔄 Successfully updated rounds to $totalRounds in room")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "🔄 Failed to update rounds: ${error.message}")
                    }
                )
            }
        }
    }

    private var lastRoundsUpdate = 0L



    private fun updateRoundsCounterInRoom() {
        currentShareCode?.let { shareCode ->
            lifecycleScope.launch {
                // FIXED: Ensure we're updating with the correct value
                if (initialRoundsSet < 0) {
                    Log.e(TAG, "🔄 Invalid rounds counter: $initialRoundsSet, not syncing")
                    return@launch
                }

                Log.d(TAG, "🔄 Syncing rounds counter $initialRoundsSet to room $shareCode")
                localRoundsUpdateTime = System.currentTimeMillis() // Update timestamp before sync

                sessionSyncService.updateRoundsCounterInRoom(shareCode, initialRoundsSet).fold(
                    onSuccess = {
                        Log.d(TAG, "🔄 Successfully updated rounds counter to $initialRoundsSet in room")
                        // Keep the flag set for longer to avoid race conditions
                        handler.postDelayed({
                            isUpdatingRoundsLocally = false
                            Log.d(TAG, "🔄 Reset local update flag")
                        }, 3000) // Increased to 3 seconds
                    },
                    onFailure = { error ->
                        Log.e(TAG, "🔄 Failed to update rounds counter: ${error.message}")
                        isUpdatingRoundsLocally = false
                        // Revert local changes on failure
                        Toast.makeText(this@MainActivity, "Failed to sync rounds counter", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }


    private fun startLocalSession() {
        Log.d(TAG, "🏠 Starting local-only session, pendingActivityType=$pendingActivityType")

        // Check if we have at least one smoker
        if (smokers.isEmpty()) {
            // Create a default local smoker
            val defaultSmoker = Smoker(
                smokerId = 0,
                name = "Me",
                isCloudSmoker = false,
                cloudUserId = null,
                shareCode = null,
                passwordHash = null,
                isPasswordVerified = false,
                isOwner = true,
                needsSync = false,
                lastSyncTime = System.currentTimeMillis(),
                uid = java.util.UUID.randomUUID().toString()
            )

            lifecycleScope.launch(Dispatchers.IO) {
                val newId = repo.insertSmoker(defaultSmoker)
                
                // Fetch the newly created smoker with its ID
                val newSmoker = repo.getSmokerById(newId)
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "🏠 Created default smoker with ID: $newId")
                    
                    // Update the smokers list with the new smoker
                    if (newSmoker != null) {
                        smokers = listOf(newSmoker)
                        smokerAdapterNew.refreshOrganizedList(smokers, currentShareCode, pausedSmokerIds, awaySmokers)
                        binding.spinnerSmoker.setSelection(0, false)
                    }

                    // Start session after smoker is created
                    startSession(System.currentTimeMillis())
                    currentShareCode = null
                    currentRoomName = null
                    sessionStatsVM.clearRoomInfo()
                    
                    // Log pending activity if there is one
                    pendingActivityType?.let { type ->
                        Log.d(TAG, "🏠 Logging pending activity: $type")
                        // Small delay to ensure UI is updated
                        lifecycleScope.launch {
                            delay(100)
                            logHitSafe(type)
                        }
                        pendingActivityType = null
                    }

                    // Save session state
                    saveSessionToPrefs()

                    Toast.makeText(this@MainActivity, "Local session started", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "🏠 Local session started successfully")
                }
            }
            return
        }

        // Start session without room
        startSession(System.currentTimeMillis())
        currentShareCode = null
        currentRoomName = null
        sessionStatsVM.clearRoomInfo()

        // Save session state
        saveSessionToPrefs()

        Toast.makeText(this, "Local session started", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "🏠 Local session started successfully")
        
        // Log pending activity if there is one
        pendingActivityType?.let { type ->
            Log.d(TAG, "🏠 Logging pending activity: $type")
            lifecycleScope.launch {
                delay(100) // Small delay to ensure session is fully started
                logHitSafe(type)
            }
            pendingActivityType = null
        }
    }


    private fun showCloudSessionOptions() {
        // Check if we have any smokers first
        if (smokers.isEmpty()) {
            Log.d(TAG, "🏠 No smokers exist - showing add smoker dialog")
            addSmokerDialog.show()
            return
        }

        Log.d(TAG, "🏠 showCloudSessionOptions called")

        // CHECK FOR INTERNET CONNECTION FIRST
        if (!authManager.isNetworkAvailable()) {
            Log.d(TAG, "🏠 No internet connection - showing offline dialog")
            showOfflineCloudSessionDialog()
            return
        }

        val currentUserId = authManager.getCurrentUserId()
        Log.d(TAG, "🏠 Current user ID: $currentUserId")

        if (currentUserId == null) {
            // Show sign-in dialog if not signed in
            showCloudSignInDialog()
            return
        }

        Log.d(TAG, "🏠 User is signed in, showing cloud options")

        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        currentDialog = dialog

        val dialogView = createThemedCloudSessionDialog(dialog)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))

            // Disable any hardware acceleration that might interfere
            setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }

        dialog.setOnDismissListener {
            currentDialog = null
            Log.d(TAG, "🏠 Cloud dialog dismissed, reference cleared")
        }

        // Set initial alpha to 0 (invisible)
        dialogView.alpha = 0f

        dialog.show()

        // GUARANTEED 2-SECOND FADE USING HANDLER
        performManualFadeIn(dialogView, 2000L)
    }

    /**
     * Manually animates the fade in using a Handler to guarantee the animation runs for the full duration
     * This bypasses any system optimizations that might skip the animation
     */
    private fun performManualFadeIn(view: View, durationMs: Long) {
        val handler = Handler(Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs

        // Animation frame rate (60 FPS = update every ~16ms)
        val frameDelayMs = 16L

        Log.d(TAG, "🏠 Starting manual fade animation - Duration: ${durationMs}ms")
        Log.d(TAG, "🏠 Start time: $startTime, End time: $endTime")

        val fadeRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime
                val progress = min(elapsed.toFloat() / durationMs.toFloat(), 1f)

                // Apply easing (decelerate interpolation)
                val easedProgress = 1f - (1f - progress) * (1f - progress)

                view.alpha = easedProgress

                // Log every 10th frame to avoid log spam
                if (elapsed % 160 < frameDelayMs) {
                    Log.d(TAG, "🏠 Fade progress: ${(progress * 100).toInt()}% (alpha: ${String.format("%.2f", view.alpha)}) at ${elapsed}ms")
                }

                if (currentTime < endTime) {
                    // Continue animation
                    handler.postDelayed(this, frameDelayMs)
                } else {
                    // Animation complete - ensure final state
                    view.alpha = 1f
                    Log.d(TAG, "🏠 Manual fade COMPLETED - Duration: ${System.currentTimeMillis() - startTime}ms, Final alpha: ${view.alpha}")
                }
            }
        }

        // Start the animation
        handler.post(fadeRunnable)
    }

// Alternative approach if the above still doesn't work - using coroutines
// Add this dependency to build.gradle if not present:
// implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    /*
    import kotlinx.coroutines.*

    private fun performCoroutineFadeIn(view: View, durationMs: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            val startTime = System.currentTimeMillis()
            val frameDelay = 16L // ~60 FPS

            Log.d(TAG, "🏠 Starting coroutine fade - Duration: ${durationMs}ms")

            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = min(elapsed.toFloat() / durationMs.toFloat(), 1f)

                // Apply decelerate interpolation
                val easedProgress = 1f - (1f - progress) * (1f - progress)
                view.alpha = easedProgress

                if (elapsed % 160 < frameDelay) {
                    Log.d(TAG, "🏠 Coroutine fade: ${(progress * 100).toInt()}% at ${elapsed}ms")
                }

                if (progress >= 1f) {
                    view.alpha = 1f
                    Log.d(TAG, "🏠 Coroutine fade COMPLETED - Final alpha: ${view.alpha}")
                    break
                }

                delay(frameDelay)
            }
        }
    }
    */

    // NUCLEAR OPTION - If even manual animation doesn't work, use a completely different approach
    private fun performFadeInWithMultipleApproaches(view: View, durationMs: Long) {
        // Try approach 1: Manual Handler animation
        performManualFadeIn(view, durationMs)

        // Simultaneously try approach 2: PostDelayed alpha changes
        val steps = 50
        val stepDuration = durationMs / steps

        for (i in 0..steps) {
            val delay = i * stepDuration
            val alpha = i.toFloat() / steps.toFloat()

            view.postDelayed({
                if (view.alpha < alpha) {
                    view.alpha = alpha
                }
            }, delay)
        }

        // Approach 3: Backup using View.animate() with explicit duration
        view.animate()
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .setUpdateListener { animation ->
                // Force invalidation on each frame
                view.invalidate()
                view.requestLayout()
            }
            .start()
    }

    private fun createThemedCloudSessionDialog(dialog: Dialog): View {
        // Root container - full screen
        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Create a vertical LinearLayout to hold spacer and card
        val contentWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // INVISIBLE SPACER - Takes up top space
        val topSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f  // Weight 1 = takes all available space
            )
        }
        contentWrapper.addView(topSpacer)

        // Main card at bottom - RAISED BY 180dp
        val mainCard = androidx.cardview.widget.CardView(this).apply {
            radius = 20.dpToPx(context).toFloat()
            cardElevation = 12.dpToPx(context).toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16.dpToPx(context), 0, 16.dpToPx(context), 180.dpToPx(context))
            }
        }

        // Store card for animation reference
        rootContainer.tag = mainCard

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(context), 24.dpToPx(context),
                24.dpToPx(context), 24.dpToPx(context))
        }

        // Title
        val titleText = TextView(this).apply {
            text = "START CLOUD SESSION"
            textSize = 22f
            setTextColor(Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.15f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 40.dpToPx(context)
            }
        }
        contentLayout.addView(titleText)

        // Create option cards with image press effect
        // New Room button (primary - green)
        val newRoomCard = createCloudOptionCard("🚀", "New Room", "Create a fresh session", true) {
            animateCardSelection(dialog) { promptNewRoom() }
        }
        contentLayout.addView(newRoomCard)

        // Existing Room button (secondary)
        val existingRoomCard = createCloudOptionCard("🔥", "Existing Room", "Continue active sessions", false) {
            animateCardSelection(dialog) { showExistingRoomsDialog() }
        }
        contentLayout.addView(existingRoomCard)

        // Join by Code button (secondary)
        val joinByCodeCard = createCloudOptionCard("🔗", "Join by Code", "Enter a share code", false) {
            animateCardSelection(dialog) { showJoinByCodeDialog() }
        }
        contentLayout.addView(joinByCodeCard)

        // Divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2.dpToPx(context)
            ).apply {
                topMargin = 16.dpToPx(context)
                bottomMargin = 16.dpToPx(context)
            }
            setBackgroundColor(Color.parseColor("#3398FB98"))
        }
        contentLayout.addView(divider)

        // Local session option (secondary style)
        val localOption = createCloudOptionCard("💨", "Local Session", "Solo session on device", false) {
            animateCardSelection(dialog) { startLocalSession() }
        }
        contentLayout.addView(localOption)

        mainCard.addView(contentLayout)
        contentWrapper.addView(mainCard)
        rootContainer.addView(contentWrapper)

        // Add click to dismiss on background
        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                animateCardSelection(dialog) {}
            }
        }

        return rootContainer
    }

    private fun createCloudOptionCard(emoji: String, title: String, subtitle: String, isPrimary: Boolean, onClick: () -> Unit): View {
        val cardContainer = androidx.cardview.widget.CardView(this).apply {
            radius = 12.dpToPx(context).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(
                if (isPrimary) Color.parseColor("#33FFFFFF")  // CHANGED BACK to semi-transparent for primary too
                else Color.parseColor("#33FFFFFF")  // Semi-transparent for secondary
            )

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                70.dpToPx(context)
            ).apply {
                bottomMargin = 12.dpToPx(context)
            }

            isClickable = true
            isFocusable = true
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16.dpToPx(context), 0, 16.dpToPx(context), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Image view for pressed state (initially hidden)
        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.button_pressed_background)
            visibility = View.GONE
        }

        // Emoji icon with background
        val iconBackground = TextView(this).apply {
            text = emoji
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                48.dpToPx(context),
                48.dpToPx(context)
            )
            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12.dpToPx(context).toFloat()
                setColor(Color.parseColor("#3398FB98"))
            }
            background = bgDrawable
        }

        // Text container
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 16.dpToPx(context)
            }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)  // Always white text
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val subtitleText = TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(Color.parseColor("#D3D3D3"))  // Light gray
        }

        textContainer.addView(titleText)
        textContainer.addView(subtitleText)

        // Indicator dot
        val indicatorDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                8.dpToPx(context),
                8.dpToPx(context)
            )
            val dotDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#98FB98"))
            }
            background = dotDrawable

            // Add pulsing animation
            ObjectAnimator.ofFloat(this, "alpha", 1f, 0.3f, 1f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }

        contentLayout.addView(iconBackground)
        contentLayout.addView(textContainer)
        contentLayout.addView(indicatorDot)

        // Create a frame to hold everything
        val frameLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        frameLayout.addView(imageView)
        frameLayout.addView(contentLayout)
        cardContainer.addView(frameLayout)

        // Add throbbing animation for "New Room" button (primary)
        if (isPrimary && title == "New Room") {
            addThrobbingAnimation(cardContainer)
        }

        // Store original colors
        val originalBackgroundColor = Color.parseColor("#33FFFFFF")

        // Handle touch events
        cardContainer.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Show image background
                    cardContainer.setCardBackgroundColor(Color.TRANSPARENT)
                    imageView.visibility = View.VISIBLE

                    // Add shadow to text for visibility
                    titleText.setShadowLayer(4f, 2f, 2f, Color.BLACK)
                    subtitleText.setShadowLayer(4f, 2f, 2f, Color.BLACK)
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // Restore original background
                    imageView.visibility = View.GONE
                    // Don't restore background color if it's animating (for New Room)
                    if (!isPrimary || title != "New Room") {
                        cardContainer.setCardBackgroundColor(originalBackgroundColor)
                    }

                    // Remove shadows
                    titleText.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                    subtitleText.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)

                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        cardContainer.setOnClickListener {
            onClick()
        }

        return cardContainer
    }

    private fun createOptionCard(
        emoji: String,
        title: String,
        subtitle: String,
        onClick: () -> Unit,
        isNewRoom: Boolean = false
    ): androidx.cardview.widget.CardView {
        return androidx.cardview.widget.CardView(this).apply {
            radius = 12.dpToPx(context).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#33FFFFFF"))

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                70.dpToPx(context)
            ).apply {
                bottomMargin = 12.dpToPx(context)
            }

            val contentLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(16.dpToPx(context), 0, 16.dpToPx(context), 0)
            }

            // Emoji icon with background
            val iconBackground = TextView(context).apply {
                text = emoji
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    48.dpToPx(context),
                    48.dpToPx(context)
                )
                val bgDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12.dpToPx(context).toFloat()
                    setColor(Color.parseColor("#3398FB98"))
                }
                background = bgDrawable
            }

            // Text container
            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = 16.dpToPx(context)
                }
            }

            val titleText = TextView(context).apply {
                text = title
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val subtitleText = TextView(context).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#D3D3D3"))
            }

            textContainer.addView(titleText)
            textContainer.addView(subtitleText)

            // Indicator dot
            val indicatorDot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    8.dpToPx(context),
                    8.dpToPx(context)
                )
                val dotDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#98FB98"))
                }
                background = dotDrawable

                // Add pulsing animation
                ObjectAnimator.ofFloat(this, "alpha", 1f, 0.3f, 1f).apply {
                    duration = 1500
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }

            contentLayout.addView(iconBackground)
            contentLayout.addView(textContainer)
            contentLayout.addView(indicatorDot)

            addView(contentLayout)

            // Add ripple effect on click
            foreground = context.getDrawable(android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true

            setOnClickListener { onClick() }

            // Add throbbing animation for New Room button
            if (isNewRoom) {
                addThrobbingAnimation(this)
            }

            // Add shimmer effect
            addShimmerEffect(this)
        }
    }

    private fun createLocalSessionOption(onClick: () -> Unit): androidx.cardview.widget.CardView {
        return androidx.cardview.widget.CardView(this).apply {
            radius = 12.dpToPx(context).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#1A000000"))

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                60.dpToPx(context)
            )

            val contentLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(16.dpToPx(context), 0, 16.dpToPx(context), 0)
            }

            val iconBackground = TextView(context).apply {
                text = "💨"
                textSize = 20f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    40.dpToPx(context),
                    40.dpToPx(context)
                )
                val bgDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10.dpToPx(context).toFloat()
                    setColor(Color.parseColor("#33FFFFFF"))
                }
                background = bgDrawable
            }

            val titleText = TextView(context).apply {
                text = "Local Session"
                textSize = 15f
                setTextColor(Color.parseColor("#D3D3D3"))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = 16.dpToPx(context)
                }
            }

            contentLayout.addView(iconBackground)
            contentLayout.addView(titleText)

            addView(contentLayout)

            foreground = context.getDrawable(android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true

            setOnClickListener { onClick() }
        }
    }

    private fun addThrobbingAnimation(view: View) {
        // Create smooth gradual fade animation for green color
        val cardView = view as? androidx.cardview.widget.CardView ?: return

        // Smooth color transition using Handler for compatibility
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


    private fun addGlowEffect(view: View) {
        val glow = ObjectAnimator.ofFloat(view, "elevation",
            12.dpToPx(this).toFloat(), 24.dpToPx(this).toFloat(), 12.dpToPx(this).toFloat())
        glow.duration = 300
        glow.start()
    }

    private fun addShimmerEffect(card: View) {
        val shimmerView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            val shimmerDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12.dpToPx(context).toFloat()
                colors = intArrayOf(
                    Color.TRANSPARENT,
                    Color.parseColor("#1A98FB98"),
                    Color.TRANSPARENT
                )
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            background = shimmerDrawable
            alpha = 0f
        }

        (card as ViewGroup).addView(shimmerView, 0)

        ObjectAnimator.ofFloat(shimmerView, "translationX",
            -card.width.toFloat(), card.width.toFloat()).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            startDelay = kotlin.random.Random.nextLong(0, 1000)
            start()
        }
    }

    private fun addAnimatedBorder(mainCard: View) {
        // Add rotating border animation
        val borderView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            val borderDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20.dpToPx(context).toFloat()
                setStroke(2.dpToPx(context), Color.parseColor("#98FB98"))
            }
            background = borderDrawable
            alpha = 0.5f
        }

        if (mainCard is ViewGroup) {
            mainCard.addView(borderView, 0)

            ObjectAnimator.ofFloat(borderView, "rotation", 0f, 360f).apply {
                duration = 10000
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                start()
            }
        }
    }


    private fun createThemedSignInDialog(): View {
        // Create a FULL SCREEN container
        val containerLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            // Click outside to dismiss
            setOnClickListener {
                currentDialog?.let { dialog ->
                    animateCardSelection(dialog) {
                        // Just dismiss
                    }
                }
            }
        }

        // Create main card at bottom - RAISED BY 180dp
        val mainCard = androidx.cardview.widget.CardView(this).apply {
            radius = 20.dpToPx(context).toFloat()
            cardElevation = 12.dpToPx(context).toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))

            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM  // Position at bottom
                setMargins(16.dpToPx(context), 0,
                    16.dpToPx(context), 180.dpToPx(context))
            }
            layoutParams = params
        }

        // Store card reference for animation
        containerLayout.tag = mainCard

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(context), 24.dpToPx(context),
                24.dpToPx(context), 24.dpToPx(context))
        }

        // Add title
        val titleText = TextView(this).apply {
            text = "START SESSION"
            textSize = 20f
            setTextColor(Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx(context)
            }
        }
        contentLayout.addView(titleText)

        val messageText = TextView(this).apply {
            text = "Choose session type:"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx(context)
            }
        }
        contentLayout.addView(messageText)

        // Create buttons with proper animation
        val cloudButton = createThemedButton("Cloud Session (Sign In)", true) {
            currentDialog?.let { dialog ->
                animateCardSelection(dialog) {
                    showCloudSignInDialog()
                }
            }
        }
        contentLayout.addView(cloudButton)

        val localButton = createThemedButton("Local Session", false) {
            currentDialog?.let { dialog ->
                animateCardSelection(dialog) {
                    startLocalSession()
                }
            }
        }
        contentLayout.addView(localButton)

        mainCard.addView(contentLayout)
        containerLayout.addView(mainCard)

        return containerLayout
    }
    
    private fun createThemedButton(text: String, isPrimary: Boolean, onClick: () -> Unit): View {
        return androidx.cardview.widget.CardView(this).apply {
            radius = 20.dpToPx(context).toFloat()
            cardElevation = if (isPrimary) 4.dpToPx(context).toFloat() else 0f
            setCardBackgroundColor(
                if (isPrimary) Color.parseColor("#98FB98")
                else Color.parseColor("#33FFFFFF")
            )

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx(context)
            ).apply {
                bottomMargin = 12.dpToPx(context)
            }

            val buttonText = TextView(context).apply {
                this.text = text
                textSize = 14f
                setTextColor(
                    if (isPrimary) Color.parseColor("#424242")
                    else Color.WHITE
                )
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            addView(buttonText)

            foreground = context.getDrawable(android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true

            setOnClickListener { onClick() }
        }
    }

    // Helper extension
    private fun Int.dpToPx(context: android.content.Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }


    private fun animateCardSelection(dialog: Dialog, onComplete: () -> Unit) {
        // Get the card from the container's tag
        val contentView = dialog.window?.decorView?.findViewById<View>(android.R.id.content)
        val container = contentView as? ViewGroup
        val mainCard = container?.tag as? View ?: container?.getChildAt(0) ?: contentView

        Log.d(TAG, "🏠 Starting card selection fade-out animation")

        // Fade out animation with 2-second duration
        val fadeOut = ObjectAnimator.ofFloat(mainCard, "alpha", 1f, 0f)
        fadeOut.duration = 2000L  // Changed from 300ms to 2000ms (2 seconds)
        fadeOut.interpolator = android.view.animation.AccelerateInterpolator()

        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                Log.d(TAG, "🏠 Card selection fade-out completed")
                dialog.dismiss()

                // Small delay before showing the next dialog to ensure smooth transition
                Handler(Looper.getMainLooper()).postDelayed({
                    onComplete()
                }, 200)  // Small gap between fade-out and fade-in
            }
        })

        fadeOut.start()
    }

    private fun createSelectionParticles(view: View) {
        // Create green particle explosion when option is selected
        val parent = view.parent as? ViewGroup ?: return

        for (i in 0..20) {
            val particle = View(this).apply {
                layoutParams = ViewGroup.LayoutParams(8, 8)
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.my_light_primary))
            }

            parent.addView(particle)

            val startX = view.x + view.width / 2
            val startY = view.y + view.height / 2
            val angle = (i * 18).toDouble()
            val distance = 200f
            val endX = startX + (Math.cos(Math.toRadians(angle)) * distance).toFloat()
            val endY = startY + (Math.sin(Math.toRadians(angle)) * distance).toFloat()

            val translateX = ObjectAnimator.ofFloat(particle, "x", startX, endX)
            val translateY = ObjectAnimator.ofFloat(particle, "y", startY, endY)
            val alpha = ObjectAnimator.ofFloat(particle, "alpha", 1f, 0f)
            val scale = ObjectAnimator.ofFloat(particle, "scaleX", 1f, 0f)

            AnimatorSet().apply {
                playTogether(translateX, translateY, alpha, scale)
                duration = 600
                interpolator = android.view.animation.DecelerateInterpolator()

                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        parent.removeView(particle)
                    }
                })

                start()
            }
        }
    }

    private fun promptNewRoom() {
        val dialog = Dialog(this, R.style.TransparentDialog)
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_room, null)
        dialog.setContentView(dialogView)

        val roomNameInput = dialogView.findViewById<EditText>(R.id.editRoomName)
        val shareCodeInput = dialogView.findViewById<EditText>(R.id.editShareCode)
        val passwordInput = dialogView.findViewById<EditText>(R.id.editRoomPassword)

        // Pre-fill with auto-generated values
        roomNameInput.setText(getRandomRoomName())
        shareCodeInput.setText(generateShareCode())

        // Select all text for easy replacement
        roomNameInput.selectAll()

        // Limit share code length
        shareCodeInput.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(20))

        // Set up button clicks
        dialogView.findViewById<View>(R.id.btnCreate).setOnClickListener {
            val roomName = roomNameInput.text.toString().trim()
            val shareCode = shareCodeInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (shareCode.isEmpty()) {
                Toast.makeText(this, "Share code cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            lifecycleScope.launch {
                createRoomWithCustomCode(roomName, shareCode, password)
            }
        }

        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        // Configure dialog window
        dialog.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        // Set initial alpha to 0 for fade-in
        dialogView.alpha = 0f

        dialog.show()

        // Apply fade-in animation with 2-second duration
        performDialogFadeIn(dialogView, 2000L)  // Changed from 500L to 2000L

        // Focus on room name input
        roomNameInput.requestFocus()
    }


    private fun showExistingRoomsDialog() {
        // Add debug call
        debugExistingRooms()

        // Create the dialog with transparent theme
        val dialog = Dialog(this, R.style.TransparentDialog)

        // Use your existing layout
        val view = layoutInflater.inflate(R.layout.dialog_search_results, null)
        dialog.setContentView(view)

        val rv = view.findViewById<RecyclerView>(R.id.recyclerSearchResults)
        rv.layoutManager = LinearLayoutManager(this)

        // Make dialog window transparent and full width
        dialog.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // Add margin from edges
            val params = attributes
            params.horizontalMargin = 0.05f // 5% margin on each side
            attributes = params
        }

        // Set initial alpha to 0 for fade-in
        view.alpha = 0f

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { sessionSyncService.getActiveRooms() }
            result.fold(
                onSuccess = { rooms ->
                    Log.d(TAG, "🏠 getActiveRooms returned ${rooms.size} rooms")
                    withContext(Dispatchers.Main) {
                        if (rooms.isEmpty()) {
                            Toast.makeText(this@MainActivity, "No active rooms found", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        } else {
                            // Use the new modern adapter
                            rv.adapter = RoomListAdapter(rooms) { room ->
                                dialog.dismiss()
                                joinRoomSafely(room.shareCode, null)
                            }
                        }
                    }
                },
                onFailure = { err ->
                    Log.e(TAG, "🏠 getActiveRooms failed: ${err.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error loading rooms: ${err.message}", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                }
            )
        }

        dialog.show()

        // Apply fade-in animation with 2-second duration
        performDialogFadeIn(view, 2000L)  // Changed from 500L to 2000L
    }

    private fun performDialogFadeIn(view: View, durationMs: Long) {
        val handler = Handler(Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs

        val frameDelayMs = 16L // ~60 FPS

        Log.d(TAG, "🏠 Starting dialog fade-in animation - Duration: ${durationMs}ms")

        val fadeRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime
                val progress = min(elapsed.toFloat() / durationMs.toFloat(), 1f)

                // Apply decelerate interpolation for smooth effect
                val easedProgress = 1f - (1f - progress) * (1f - progress)

                view.alpha = easedProgress

                if (currentTime < endTime) {
                    // Continue animation
                    handler.postDelayed(this, frameDelayMs)
                } else {
                    // Animation complete - ensure final state
                    view.alpha = 1f
                    Log.d(TAG, "🏠 Dialog fade-in COMPLETED")
                }
            }
        }

        // Start the animation
        handler.post(fadeRunnable)
    }

    private fun debugExistingRooms() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "🏠 === ROOM DEBUG START ===")

                // Get all rooms directly from Firestore
                val snapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("rooms")
                    .get()
                    .await()

                Log.d(TAG, "🏠 Found ${snapshot.documents.size} total rooms in Firestore")

                snapshot.documents.forEach { doc ->
                    Log.d(TAG, "🏠 Room ${doc.id}:")
                    Log.d(TAG, "    Data: ${doc.data}")

                    try {
                        val room = doc.toObject(RoomData::class.java)
                        Log.d(TAG, "    Parsed successfully: ${room?.name}")
                        Log.d(TAG, "    Participants: ${room?.participants?.size}")
                        Log.d(TAG, "    Active participants: ${room?.activeParticipants?.size}")
                        Log.d(TAG, "    Activities: ${room?.activities?.size}")
                    } catch (e: Exception) {
                        Log.e(TAG, "    PARSE ERROR: ${e.message}")
                    }
                }

                Log.d(TAG, "🏠 === ROOM DEBUG END ===")

            } catch (e: Exception) {
                Log.e(TAG, "🏠 Debug error: ${e.message}")
            }
        }
    }

    //  this function resume the last loaded summary
    private fun resumeLastSummary() {
        Log.d(TAG, "📱 resumeLastSummary called")

        // First check if we have a loaded summary
        if (lastLoadedSummary != null) {
            Log.d(TAG, "📱 Using lastLoadedSummary")
            resumeSession(lastLoadedSummary!!)
            return
        }

        // If no loaded summary, try to get the most recent one from the database
        lifecycleScope.launch {
            try {
                Log.d(TAG, "📱 No lastLoadedSummary, fetching from database")

                // Get the most recent summary
                val recentSummary = withContext(Dispatchers.IO) {
                    repo.getMostRecentSummary()
                }

                if (recentSummary != null) {
                    Log.d(TAG, "📱 Found recent summary: ${recentSummary.roomName} with ${recentSummary.totalCones} cones")

                    // Store it as the last loaded summary for future use
                    lastLoadedSummary = recentSummary

                    withContext(Dispatchers.Main) {
                        resumeSession(recentSummary)

                        // Switch to the Sesh tab after resuming
                        binding.viewPager.currentItem = 1
                    }
                } else {
                    Log.d(TAG, "📱 No summaries found to resume")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No previous session to resume", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "📱 Error resuming last summary", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error resuming session", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createRoomWithCustomCode(roomName: String, customShareCode: String, password: String? = null) {
        Log.d(TAG, "🏠 createRoomWithCustomCode called: name=$roomName, code=$customShareCode, hasPassword=${password != null}")

        lifecycleScope.launch {
            Log.d(TAG, "🏠 Coroutine started")

            val creatorId = authManager.getCurrentUserId() ?: getAndroidDeviceId()
            Log.d(TAG, "🏠 Creator ID: $creatorId")

            // First check if this share code already exists
            val existingRoom = sessionSyncService.getRoomData(customShareCode)
            if (existingRoom != null) {
                Log.d(TAG, "🏠 Share code already exists")
                Toast.makeText(this@MainActivity, "Share code '$customShareCode' is already taken. Please choose a different code.", Toast.LENGTH_LONG).show()
                promptNewRoom() // Show dialog again
                return@launch
            }

            // Hash the password if provided
            val passwordHash = if (!password.isNullOrEmpty()) {
                PasswordUtils.hashPassword(password)
            } else null

            // Create room with custom share code
            val now = System.currentTimeMillis()
            val room = RoomData(
                owner = creatorId,
                name = roomName,
                shareCode = customShareCode,
                participants = listOf(creatorId),
                activeParticipants = listOf(creatorId),
                active = true,
                createdAt = now,
                updatedAt = now,
                startTime = now,
                lastActivityTime = now,
                activities = emptyList(),
                currentStats = SessionStats(),
                roundsCounter = 0,
                autoAddState = AutoAddState(),
                passwordHash = passwordHash,
                joinedUsers = listOf(creatorId)
            )

            try {
                Log.d(TAG, "🏠 About to create room document in Firestore")

                // Directly create the room document with the custom share code as ID
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("rooms")
                    .document(customShareCode)
                    .set(room)
                    .await()

                Log.d(TAG, "🏠 Room document created successfully in Firestore")

                startSession(room.startTime)
                currentShareCode = room.shareCode
                currentRoomName = room.name
                currentRoom = room

                sessionStatsVM.setRoomInfo(room.name, room.shareCode)
                Log.d(TAG, "🏠 Room created with custom code: ${room.name} (${room.shareCode})")

                // Sync local smokers to the new room
                val localSmokers = withContext(Dispatchers.IO) {
                    repo.allSmokers.value?.filter { !it.isCloudSmoker } ?: emptyList()
                }

                if (localSmokers.isNotEmpty()) {
                    sessionSyncService.syncLocalSmokersToRoom(creatorId, customShareCode, localSmokers)
                }

                startRoomListener(room.shareCode)

                val message = if (passwordHash != null) {
                    "Created password-protected room: ${room.name} (Code: ${room.shareCode})"
                } else {
                    "Created room: ${room.name} (Code: ${room.shareCode})"
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()

                // Log pending activity if there is one
                pendingActivityType?.let { type ->
                    Log.d(TAG, "🏠 Logging pending activity after room creation: $type")
                    lifecycleScope.launch {
                        delay(100) // Small delay to ensure room is fully set up
                        logHitSafe(type)
                    }
                    pendingActivityType = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "🏠 Failed to create room", e)
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Failed to create room: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showJoinByCodeDialog() {
        val dialog = Dialog(this, R.style.TransparentDialog)
        val dialogView = layoutInflater.inflate(R.layout.dialog_join_by_code, null)
        dialog.setContentView(dialogView)

        val input = dialogView.findViewById<EditText>(R.id.editShareCode)

        dialogView.findViewById<View>(R.id.btnJoin).setOnClickListener {
            val code = input.text.toString().trim()
            if (code.isNotEmpty()) {
                dialog.dismiss()
                joinRoomSafely(code, null)
            } else {
                Toast.makeText(this, "Please enter a code", Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        // Configure dialog window
        dialog.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        // Set initial alpha to 0 for fade-in
        dialogView.alpha = 0f

        dialog.show()

        // Apply fade-in animation with 2-second duration
        performDialogFadeIn(dialogView, 2000L)  // Changed from 500L to 2000L

        // Focus and show keyboard
        input.requestFocus()
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
    }
    private fun joinRoomSafely(shareCode: String, dialogToClose: AlertDialog?) {
        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId() ?: getAndroidDeviceId()

            // First get the room to check if it has a password
            val roomData = sessionSyncService.getRoomData(shareCode)
            if (roomData == null) {
                Toast.makeText(this@MainActivity, "Room not found", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Check if room has password and user hasn't joined yet
            if (roomData.passwordHash != null && !roomData.hasUserJoined(userId)) {
                dialogToClose?.dismiss()

                // Show password dialog
                showRoomPasswordDialog(roomData, userId)
            } else {
                // No password or already joined, proceed normally
                proceedWithJoinRoom(shareCode, userId, dialogToClose)
            }
        }
    }

    private fun showRoomPasswordDialog(room: RoomData, userId: String) {
        val input = EditText(this).apply {
            hint = "Enter room password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Password Required")
            .setMessage("This room is password protected. Please enter the password to join.")
            .setView(input)
            .setPositiveButton("Join") { _, _ ->
                val enteredPassword = input.text.toString()
                verifyRoomPassword(room, userId, enteredPassword)
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun verifyRoomPassword(room: RoomData, userId: String, enteredPassword: String) {
        lifecycleScope.launch {
            val isValid = room.passwordHash?.let { hash ->
                PasswordUtils.verifyPassword(enteredPassword, hash)
            } ?: false

            if (isValid) {
                // Password correct, add user to joinedUsers and proceed
                sessionSyncService.addUserToJoinedList(room.shareCode, userId).fold(
                    onSuccess = {
                        proceedWithJoinRoom(room.shareCode, userId, null)
                    },
                    onFailure = { error ->
                        Toast.makeText(this@MainActivity, "Failed to join room: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                )
            } else {
                Toast.makeText(this@MainActivity, "Incorrect password", Toast.LENGTH_SHORT).show()
                // Show the password dialog again
                showRoomPasswordDialog(room, userId)
            }
        }
    }

    private fun proceedWithJoinRoom(shareCode: String, userId: String, dialogToClose: AlertDialog?) {
        lifecycleScope.launch {
            // Get local smokers BEFORE joining the room
            val localSmokers = withContext(Dispatchers.IO) {
                repo.getAllSmokersList().filter { !it.isCloudSmoker }
            }

            Log.d(TAG, "🏠 Joining room with ${localSmokers.size} local smokers")

            // Use the enhanced join method that syncs smokers
            sessionSyncService.joinRoomWithSmokerSync(userId, shareCode, localSmokers).fold(
                onSuccess = { room: RoomData ->  // Explicitly specify the type
                    dialogToClose?.dismiss()
                    startSession(room.startTime)
                    currentShareCode = shareCode
                    currentRoomName = room.name
                    currentRoom = room

                    sessionStatsVM.setRoomInfo(room.name, shareCode)
                    Log.d(TAG, "🏠 Room info set for joined room: ${room.name} ($shareCode)")

                    // Initialize rounds counter from room
                    initialRoundsSet = room.roundsCounter
                    val completedRounds = room.safeCurrentStats().totalRounds
                    roundsLeft = if (initialRoundsSet > 0) {
                        kotlin.math.max(0, initialRoundsSet - completedRounds)
                    } else {
                        0
                    }
                    updateRoundsUI()

                    startRoomListener(shareCode)

                    // Toast message about successful join and smoker sync
                    withContext(Dispatchers.Main) {
                        val message = if (localSmokers.isNotEmpty()) {
                            "Joined room and shared ${localSmokers.size} local smokers"
                        } else {
                            "Joined room: $shareCode"
                        }
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()

                        // Log pending activity if there is one
                        pendingActivityType?.let { type ->
                            Log.d(TAG, "🏠 Logging pending activity after joining room: $type")
                            lifecycleScope.launch {
                                delay(100) // Small delay to ensure room is fully joined
                                logHitSafe(type)
                            }
                            pendingActivityType = null
                        }
                    }
                },
                onFailure = { error: Throwable ->  // Explicitly specify the type
                    Toast.makeText(this@MainActivity, "Join failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun refreshNotificationsWithSession() {
        if (!notificationsEnabled) return  // Skip if notifications are disabled

        val helper = NotificationHelper(this)
        val sessionCode = currentShareCode ?: return
        val selectedPosition = binding.spinnerSmoker.selectedItemPosition
        val smokerCloudId = smokers.getOrNull(selectedPosition)?.cloudUserId

        lifecycleScope.launch(Dispatchers.IO) {
            val types = listOf(ActivityType.JOINT, ActivityType.CONE, ActivityType.BOWL)
            for (type in types) {
                val lastTs = getLastTimestampForType(type)

                // Get smoker name for the last activity
                val lastSmokerName = getLastSmokerNameForType(type)

                val conesSinceLastBowl = if (type == ActivityType.CONE) {
                    getConesSinceLastBowlForTimestamp(lastTs)
                } else null

                withContext(Dispatchers.Main) {
                    helper.showActivityNotification(
                        type,
                        lastTs,
                        conesSinceLastBowl,
                        sessionCode,
                        smokerCloudId,
                        justAdded = false,
                        addedAt = null,
                        lastSmokerName = lastSmokerName
                    )
                }
            }
        }
    }


    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun triggerInitialNotifications() {
        if (!notificationsEnabled) return  // Skip if notifications are disabled

        val helper = NotificationHelper(this)
        lifecycleScope.launch(Dispatchers.IO) {
            listOf(ActivityType.JOINT, ActivityType.CONE, ActivityType.BOWL).forEach { type ->
                val lastLog = repo.getLastLogByType(type)
                val lastTs = lastLog?.timestamp

                // Get smoker name for the last log
                val lastSmokerName = lastLog?.let { log ->
                    repo.getSmokerById(log.smokerId)?.name
                }

                val conesSinceLastBowl = if (type == ActivityType.CONE && lastTs != null) {
                    repo.getLastBowlBefore(lastTs)?.let { bowl ->
                        repo.countConesBetweenTimestamps(bowl.timestamp, lastTs)
                    }
                } else null

                withContext(Dispatchers.Main) {
                    helper.showActivityNotification(
                        type,
                        lastTs,
                        conesSinceLastBowl,
                        lastSmokerName = lastSmokerName
                    )
                }
            }
        }
    }

    // UPDATE: Modified updateUIForSessionState to show/hide auto-add controls
    private fun updateUIForSessionState() {
        // Get button container for margin adjustment
        val buttonContainer = binding.buttonContainer
        val params = buttonContainer.layoutParams as LinearLayout.LayoutParams

        // Get the activity buttons
        val jointButton = binding.btnAddJoint
        val coneButton = binding.btnAddCone
        val bowlButton = binding.btnAddBowl

        if (sessionActive) {
            binding.timerContainer.visibility = if (timersVisible) View.VISIBLE else View.GONE
            binding.roundsContainer.visibility = if (timersVisible) View.VISIBLE else View.GONE
            binding.btnEndSesh.visibility = View.VISIBLE
            binding.btnStartSesh.visibility = View.GONE
            binding.btnRewind.visibility = View.VISIBLE
            binding.btnToggleTimers.visibility = View.VISIBLE

            // Show auto-add controls during session (if timers are visible)
            binding.layoutConeAutoControls.visibility = if (timersVisible) View.VISIBLE else View.GONE
            binding.layoutJointAutoControls.visibility = if (timersVisible) View.VISIBLE else View.GONE
            binding.layoutBowlAutoControls.visibility = if (timersVisible) View.VISIBLE else View.GONE

            // Adjust button heights based on timer visibility
            if (timersVisible) {
                // Double the height when "See Less" is shown (timers visible)
                setActivityButtonHeights(jointButton, coneButton, bowlButton, 96.dpToPx(this))
                // Apply margin for expanded state
                params.topMargin = -19.dpToPx(this)
            } else {
                // Normal height when "Advanced" is shown (timers hidden)
                setActivityButtonHeights(jointButton, coneButton, bowlButton, 48.dpToPx(this))
                // Apply margin for collapsed state
                params.topMargin = -5.dpToPx(this)
            }

            // Update undo button visibility
            updateUndoButtonVisibility()
        } else {
            binding.timerContainer.visibility = View.GONE
            binding.roundsContainer.visibility = View.GONE
            binding.btnEndSesh.visibility = View.GONE
            binding.btnStartSesh.visibility = View.VISIBLE
            binding.btnStartSesh.setBackgroundColor(ContextCompat.getColor(this, R.color.my_light_primary))
            binding.btnStartSesh.setTextColor(ContextCompat.getColor(this, R.color.my_dark_grey_background))

            binding.btnRewind.visibility = View.GONE

            // Show Advanced button when not in session
            binding.btnToggleTimers.visibility = View.VISIBLE
            binding.btnToggleTimers.text = "Advanced"

            // Hide auto-add controls when no session
            binding.layoutConeAutoControls.visibility = View.GONE
            binding.layoutJointAutoControls.visibility = View.GONE
            binding.layoutBowlAutoControls.visibility = View.GONE

            // Normal height for non-session state
            setActivityButtonHeights(jointButton, coneButton, bowlButton, 48.dpToPx(this))

            // Apply margin for non-session state (Advanced showing, no auto controls)
            params.topMargin = -5.dpToPx(this)

            // Clear activity history and hide undo button when session ends
            activityHistory.clear()
            binding.btnUndoLastActivity.visibility = View.GONE

            // Stop auto-add manager only if it's initialized
            if (::autoAddManager.isInitialized) {
                autoAddManager.stopTimerUpdates()
            }
        }

        buttonContainer.layoutParams = params
        updateRoundsUI()
    }

    private fun startSession(startTime: Long) {
        // Clear any editing state - we're starting a NEW session
        editingSummaryId = null
        lastLoadedSummary = null

        // Set the session start time which will be used as the session ID
        sessionStart = startTime
        sessionActive = true

        // Initialize session tracking variables
        lastLogTime = startTime
        actualLastLogTime = 0L
        lastLogTimeBeforeRewind = 0L
        lastConeTimestamp = 0L
        lastJointTimestamp = 0L
        lastBowlTimestamp = 0L
        lastIntervalMillis = 0L
        intervalsList.clear()
        activitiesTimestamps.clear()
        hitsThisRound = 0
        actualRounds = 0
        rewindOffset = 0L

        Log.d(TAG, "🎬 Session started with ID: $sessionStart at ${java.util.Date(startTime)}")
        Log.d(TAG, "🎬 Session active: $sessionActive")

        if (roundsLeft > 0) {
            initialRoundsSet = roundsLeft
            prefs.edit().putInt("initialRoundsLeft", roundsLeft).apply()
            Log.d(TAG, "🎬 Initial rounds set: $initialRoundsSet")
        } else {
            initialRoundsSet = 0
            Log.d(TAG, "🎬 Session started with infinite rounds")
        }

        // Set session info in both ViewModels
        sessionStatsVM.startSession(startTime)
        stashViewModel.setSessionStartTime(sessionStart)

        // Save the current session ID for later use
        prefs.edit()
            .putLong("current_session_id", sessionStart)
            .putBoolean("session_active", true)
            .apply()

        updateUIForSessionState()
        handler.post(timerRunnable)

        // Notify SeshFragment that session started
        val seshFragment = supportFragmentManager.fragments
            .filterIsInstance<SeshFragment>()
            .firstOrNull()
        seshFragment?.onSessionStarted()

        // Save session state
        saveActiveSessionState()

        // Force initial stats refresh
        if (currentShareCode == null) {
            lifecycleScope.launch {
                delay(100)
                refreshLocalSessionStatsIfNeeded()
            }
        }

        // Apply random font for session start
        handler.postDelayed({ applyFontToSpinner() }, 200)
    }

    private fun endSession() {
        // CRITICAL: Store the session ID before clearing anything
        val completedSessionId = if (sessionActive && sessionStart > 0) {
            sessionStart
        } else {
            null
        }

        Log.d(TAG, "📊 Ending session with ID: $completedSessionId")

        // Store in preferences immediately if we have a valid session ID
        if (completedSessionId != null && completedSessionId > 0) {
            prefs.edit().putLong("last_completed_session_id", completedSessionId).apply()
            Log.d(TAG, "📊 Saved last completed session ID to prefs: $completedSessionId")

            // Update both ViewModels immediately
            sessionStatsVM.lastCompletedSessionId = completedSessionId
            stashViewModel.setLastCompletedSessionId(completedSessionId)

            // Also ensure all activities in this session have the session ID
            lifecycleScope.launch(Dispatchers.IO) {
                val sessionEndTime = System.currentTimeMillis()
                repo.updateSessionIdsForTimeRange(completedSessionId, completedSessionId, sessionEndTime)
                Log.d(TAG, "📊 Updated session IDs for all activities in session")

                // Handle session goals - pause them and update their dates
                currentShareCode?.let { shareCode ->
                    goalService.endCurrentSessionGoals(shareCode)
                }
            }
        }

        sessionActive = false
        rewindOffset = 0L
        actualLastLogTime = 0L
        lastConeTimestamp = 0L
        lastJointTimestamp = 0L
        lastBowlTimestamp = 0L
        activitiesTimestamps.clear()
        lastLogTimeBeforeRewind = 0L
        handler.removeCallbacks(timerRunnable)

        sessionStatsVM.stopSession()
        activityHistory.clear()
        clearActiveSessionState()

        if (::autoAddManager.isInitialized) {
            autoAddManager.stopTimerUpdates()
        }

        // Mark as away if in cloud room
        if (currentShareCode != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                authManager.getCurrentUserId()?.let { me ->
                    currentShareCode?.let { code ->
                        sessionSyncService.markUserAway(me, code)
                    }
                }
            }
        }

        val sessionEnd = System.currentTimeMillis()
        lifecycleScope.launch(Dispatchers.IO) {
            val names = smokers.map { it.name }

            val originalSessionStart = if (editingSummaryId != null && lastLoadedSummary != null) {
                sessionEnd - lastLoadedSummary!!.sessionLength - (sessionEnd - sessionStart)
            } else {
                sessionStart
            }

            val conesList = if (currentShareCode != null && currentRoom != null) {
                val roomStats = currentRoom!!.safeCurrentStats()
                val perSmokerStats = roomStats.perSmokerStats

                smokers.map { smoker ->
                    val smokerId = if (smoker.isCloudSmoker) {
                        smoker.cloudUserId ?: ""
                    } else {
                        "local_${smoker.uid}"
                    }
                    perSmokerStats[smokerId]?.totalCones ?: 0
                }
            } else {
                smokers.map { s ->
                    repo.countConesForSmokerBetween(s.smokerId, originalSessionStart, sessionEnd)
                }
            }

            val total = conesList.sum()
            val length = sessionEnd - originalSessionStart
            val longest = intervalsList.maxOrNull() ?: 0L
            val shortest = intervalsList.minOrNull() ?: 0L

            val summary = SessionSummary(
                id = editingSummaryId ?: 0L,
                smokerNames = names,
                conesPerSmoker = conesList,
                totalCones = total,
                rounds = actualRounds,
                sessionLength = length,
                longestInterval = longest,
                shortestInterval = shortest,
                timestamp = sessionEnd,
                liveSyncEnabled = true,
                shareCode = currentShareCode,
                roomName = currentRoomName
            )

            val summaryId = if (editingSummaryId != null) {
                repo.updateSummary(summary)
                editingSummaryId!!
            } else {
                repo.insertSummary(summary)
            }

            withContext(Dispatchers.Main) {
                // Trigger stats refresh with the completed session ID
                stashViewModel.refreshStatsAfterSessionChange()

                sessionStatsVM.loadSummary(summary)
                sessionStatsVM.clearRoomInfo()

                currentShareCode = null
                currentRoomName = null
                currentRoom = null

                val seshFragment = supportFragmentManager.fragments
                    .filterIsInstance<SeshFragment>()
                    .firstOrNull()
                seshFragment?.onSessionEnded()
            }

            editingSummaryId = null
            lastLoadedSummary = null
        }

        // Clear session start AFTER everything else
        sessionStart = 0L

        updateUIForSessionState()
    }


    private fun checkAndSwitchStashSource(activityType: ActivityType) {
        val stashViewModel = ViewModelProvider(this).get(StashViewModel::class.java)
        val currentStash = stashViewModel.currentStash.value ?: return
        val ratios = stashViewModel.ratios.value ?: return

        val requiredGrams = when (activityType) {
            ActivityType.CONE -> ratios.coneGrams
            ActivityType.JOINT -> ratios.jointGrams
            ActivityType.BOWL -> ratios.bowlGrams
            else -> 0.0
        }

        val currentSource = stashViewModel.stashSource.value
        val currentUserId = authManager.getCurrentUserId() ?: getAndroidDeviceId()

        // Get current selected smoker
        val selectedPosition = binding.spinnerSmoker.selectedItemPosition
        val organizedSmokers = organizeSmokers().flatMap { it.smokers }
        val selectedSmoker = organizedSmokers.getOrNull(selectedPosition)

        Log.d(TAG, "🎯 checkAndSwitchStashSource: currentSource=$currentSource, requiredGrams=$requiredGrams, currentGrams=${currentStash.currentGrams}")

        // Check if we need to switch based on current source
        when (currentSource) {
            StashSource.MY_STASH -> {
                if (currentStash.currentGrams < requiredGrams) {
                    Log.d(TAG, "🎯 Insufficient My Stash (${currentStash.currentGrams}g < ${requiredGrams}g), switching to Their Stash")

                    // Auto-switch to Their Stash silently
                    stashViewModel.updateStashSource(StashSource.THEIR_STASH)

                    // Force update the radio button in StashFragment
                    supportFragmentManager.fragments
                        .filterIsInstance<ViewPagerAdapter>()
                        .firstOrNull()?.let { adapter ->
                            // Get the StashFragment from ViewPager
                            val stashFragment = supportFragmentManager.findFragmentByTag("f4") as? StashFragment
                                ?: supportFragmentManager.fragments
                                    .filterIsInstance<StashFragment>()
                                    .firstOrNull()

                            stashFragment?.let { fragment ->
                                runOnUiThread {
                                    fragment.setAttributionRadioSilently(StashSource.THEIR_STASH)
                                    Log.d(TAG, "🎯 Updated StashFragment radio to Their Stash")
                                }
                            }
                        }

                    Log.d(TAG, "🎯 Auto-switched to Their Stash due to insufficient My Stash")
                } else {
                    Log.d(TAG, "🎯 Sufficient My Stash (${currentStash.currentGrams}g >= ${requiredGrams}g)")
                }
            }
            StashSource.EACH_TO_OWN -> {
                // Check if the selected smoker is the current user
                val isCurrentUser = selectedSmoker?.let { smoker ->
                    (smoker.isCloudSmoker && smoker.cloudUserId == currentUserId) ||
                            (!smoker.isCloudSmoker && smoker.uid == currentUserId)
                } ?: false

                if (isCurrentUser && currentStash.currentGrams < requiredGrams) {
                    Log.d(TAG, "🎯 Insufficient stash for current user in Each-to-Own, switching to Their Stash")

                    // Auto-switch to Their Stash silently for current user when they don't have enough
                    stashViewModel.updateStashSource(StashSource.THEIR_STASH)

                    // Force update the radio button in StashFragment
                    supportFragmentManager.fragments
                        .filterIsInstance<ViewPagerAdapter>()
                        .firstOrNull()?.let { adapter ->
                            // Get the StashFragment from ViewPager
                            val stashFragment = supportFragmentManager.findFragmentByTag("f4") as? StashFragment
                                ?: supportFragmentManager.fragments
                                    .filterIsInstance<StashFragment>()
                                    .firstOrNull()

                            stashFragment?.let { fragment ->
                                runOnUiThread {
                                    fragment.setAttributionRadioSilently(StashSource.THEIR_STASH)
                                    Log.d(TAG, "🎯 Updated StashFragment radio to Their Stash (Each-to-Own)")
                                }
                            }
                        }

                    Log.d(TAG, "🎯 Auto-switched to Their Stash for current user in Each-to-Own mode")
                }
            }
            StashSource.THEIR_STASH -> {
                // No auto-switch needed when already on Their Stash
                Log.d(TAG, "🎯 Already on Their Stash, no switch needed")
            }
            else -> {
                Log.d(TAG, "🎯 Unknown stash source: $currentSource")
            }
        }
    }


    private fun updateRoundsUI() {
        val displayText = when {
            // Show infinity when initialRoundsSet is 0 (infinity mode)
            initialRoundsSet == 0 -> "∞"
            // Show the remaining rounds
            roundsLeft < 0 -> "0" // Never show negative
            else -> roundsLeft.toString()
        }
        binding.textRoundsLeft.text = displayText
    }

    private fun resumeSession(summary: SessionSummary) {
        if (sessionActive) endSession()

        val resumeTime = System.currentTimeMillis()
        editingSummaryId = summary.id
        sessionStart = summary.timestamp - summary.sessionLength
        lastLogTime = resumeTime
        actualLastLogTime = 0L
        lastLogTimeBeforeRewind = 0L
        lastConeTimestamp = 0L
        lastJointTimestamp = 0L
        lastBowlTimestamp = 0L
        lastIntervalMillis = 0L
        intervalsList.clear()
        activitiesTimestamps.clear()
        hitsThisRound = 0
        actualRounds = summary.rounds
        sessionActive = true
        rewindOffset = 0L

        lastLoadedSummary = summary

        sessionStatsVM.startSession(sessionStart)
        stashViewModel.setSessionStartTime(sessionStart)

        prefs.edit()
            .putLong("current_session_id", sessionStart)
            .putBoolean("session_active", true)
            .apply()

        updateUIForSessionState()
        handler.post(timerRunnable)

        binding.tabLayout.getTabAt(1)?.select()

        val seshFragment = supportFragmentManager.fragments
            .filterIsInstance<SeshFragment>()
            .firstOrNull()
        seshFragment?.onSummaryLoaded()

        summary.shareCode?.let { shareCode ->
            currentShareCode = shareCode
            currentRoomName = summary.roomName

            if (currentRoomName != null) {
                sessionStatsVM.setRoomInfo(currentRoomName!!, shareCode)
            }

            lifecycleScope.launch {
                val userId = authManager.getCurrentUserId()
                if (userId != null) {
                    Log.d(TAG, "🔄 Attempting to resume session in room: $shareCode")
                    sessionSyncService.joinRoom(userId, shareCode).fold(
                        onSuccess = { room ->
                            currentRoom = room

                            withContext(Dispatchers.Main) {
                                val roomStats = room.safeCurrentStats()
                                sessionStatsVM.applyRoomStats(roomStats, room.startTime)
                                Log.d(TAG, "🔄 Applied initial room stats on resume")
                            }

                            sessionSyncService.returnFromAway(userId, shareCode)
                            sessionSyncService.markActive(userId, shareCode)

                            val userSmoker = smokers.find { it.cloudUserId == userId }
                            userSmoker?.let { smoker ->
                                val smokerIndex = smokers.indexOf(smoker)
                                if (smokerIndex >= 0) {
                                    binding.spinnerSmoker.setSelection(smokerIndex)
                                    selectSmoker(smoker)
                                    Log.d(TAG, "🔄 Set resuming user as current smoker: ${smoker.name}")
                                }
                            }

                            startRoomListener(shareCode)

                            // Resume session goals
                            goalService.resumeCurrentSessionGoals(shareCode)
                        },
                        onFailure = { error ->
                            Log.w(TAG, "🔄 Could not rejoin original room: ${error.message}")
                            currentShareCode = null
                            currentRoomName = null
                            currentRoom = null

                            sessionStatsVM.clearRoomInfo()

                            withContext(Dispatchers.Main) {
                                sessionStatsVM.loadSummary(summary)
                                refreshLocalSessionStatsIfNeeded()
                            }
                        }
                    )
                } else {
                    sessionStatsVM.loadSummary(summary)
                    refreshLocalSessionStatsIfNeeded()
                }
            }
        } ?: run {
            sessionStatsVM.loadSummary(summary)
            refreshLocalSessionStatsIfNeeded()
        }

        saveActiveSessionState()

        // Apply random font for session resume
        handler.postDelayed({ applyFontToSpinner() }, 200)
    }

    private fun checkAndRestoreActiveSession() {
        lifecycleScope.launch {
            try {
                // Check if we have an active session in preferences
                val prefs = getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
                val activeSessionId = prefs.getLong("active_session_id", -1L)
                val activeShareCode = prefs.getString("active_share_code", null)
                val activeRoomName = prefs.getString("active_room_name", null)
                val activeSessionStart = prefs.getLong("active_session_start", 0L)

                if (activeSessionId != -1L && activeSessionStart > 0) {
                    Log.d(TAG, "🔄 Found active session to restore: ID=$activeSessionId, room=$activeShareCode")

                    // Restore session state
                    sessionActive = true
                    sessionStart = activeSessionStart
                    lastLogTime = System.currentTimeMillis()
                    editingSummaryId = activeSessionId
                    currentShareCode = activeShareCode
                    currentRoomName = activeRoomName

                    // Start the view model session
                    sessionStatsVM.startSession(sessionStart)

                    // Update UI
                    withContext(Dispatchers.Main) {
                        updateUIForSessionState()
                        handler.post(timerRunnable)
                    }

                    // If it's a cloud session, reconnect to the room
                    if (activeShareCode != null && activeRoomName != null) {
                        sessionStatsVM.setRoomInfo(activeRoomName, activeShareCode)

                        val userId = authManager.getCurrentUserId()
                        if (userId != null) {
                            sessionSyncService.joinRoom(userId, activeShareCode).fold(
                                onSuccess = { room ->
                                    currentRoom = room

                                    // Apply room stats immediately - THIS IS THE KEY!
                                    withContext(Dispatchers.Main) {
                                        val roomStats = room.safeCurrentStats()
                                        sessionStatsVM.applyRoomStats(roomStats, room.startTime)
                                        Log.d(TAG, "🔄 Applied room stats after app restart")
                                    }

                                    // Mark as active and start listener
                                    sessionSyncService.markActive(userId, activeShareCode)
                                    startRoomListener(activeShareCode)

                                    Log.d(TAG, "🔄 Successfully restored cloud session")

                                    // DON'T call refreshLocalSessionStatsIfNeeded() for cloud sessions!
                                },
                                onFailure = { error ->
                                    Log.e(TAG, "🔄 Failed to restore cloud session: ${error.message}")
                                    // Fall back to local session
                                    currentShareCode = null
                                    currentRoomName = null
                                    sessionStatsVM.clearRoomInfo()
                                    refreshLocalSessionStatsIfNeeded()
                                }
                            )
                        } else {
                            // Not signed in, but we have a cloud session saved
                            // Fall back to local mode but keep room info for display
                            Log.d(TAG, "🔄 Not signed in, using local mode for cloud session")

                            // Clear the share code to indicate we're in local mode
                            // but keep room name for UI display
                            currentShareCode = null

                            // Clear room info in ViewModel temporarily to allow stats loading
                            sessionStatsVM.clearRoomInfo()

                            // Load stats from local database
                            refreshLocalSessionStatsIfNeeded()

                            // After loading stats, set room info back for UI display if we had one
                            if (activeRoomName != null) {
                                sessionStatsVM.setRoomInfo(activeRoomName, "OFFLINE")
                            }
                        }
                    } else {
                        // Local session - use refreshLocalSessionStatsIfNeeded
                        refreshLocalSessionStatsIfNeeded()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring session", e)
            }
        }
    }
    
    private fun showWelcomeScreenIfNeeded() {
        // No longer showing on first launch - now triggered when first cloud smoker is added
    }
    
    fun showWelcomeScreenForFirstCloudSmoker() {
        Log.d("WELCOME_DEBUG", "🚀 showWelcomeScreenForFirstCloudSmoker() called")
        
        // Check if we should show the welcome screen (hasn't been shown before)
        val shouldShow = WelcomeScreenDialog.shouldShowWelcomeScreen(this)
        Log.d("WELCOME_DEBUG", "🔑 Should show welcome? $shouldShow")
        
        if (shouldShow) {
            Log.d("WELCOME_DEBUG", "⏰ Scheduling welcome screen to show in 500ms...")
            // Show the welcome screen after a short delay to ensure UI is ready
            handler.postDelayed({
                Log.d("WELCOME_DEBUG", "🎭 Creating and showing WelcomeScreenDialog now!")
                val welcomeDialog = WelcomeScreenDialog(this) {
                    // On completion callback - nothing special needed here
                    Log.d("WELCOME_DEBUG", "✨ Welcome screen completed for first cloud smoker")
                }
                welcomeDialog.show()
                Log.d("WELCOME_DEBUG", "📱 WelcomeScreenDialog.show() called")
            }, 500)
        } else {
            Log.d("WELCOME_DEBUG", "⚠️ Welcome screen already shown before, skipping")
        }
    }
    
    private fun checkAndShowWelcomeForFirstCloudSmoker() {
        Log.d("WELCOME_DEBUG", "🔎 checkAndShowWelcomeForFirstCloudSmoker() called")
        lifecycleScope.launch {
            // Check if there are any existing cloud smokers
            val allSmokers = repo.getAllSmokersList()
            val existingCloudSmokers = allSmokers.filter { smoker: Smoker -> smoker.isCloudSmoker }
            Log.d("WELCOME_DEBUG", "☁️ Found ${existingCloudSmokers.size} existing cloud smokers")
            
            // Only show welcome if this is the FIRST cloud smoker (none existed before)
            // We check for size == 1 because the new one was just added
            if (existingCloudSmokers.size == 1) {
                Log.d("WELCOME_DEBUG", "🎉 This is the first cloud smoker! Showing welcome...")
                withContext(Dispatchers.Main) {
                    showWelcomeScreenAfterGoogleLogin()
                }
            } else {
                Log.d("WELCOME_DEBUG", "⏭️ Not the first cloud smoker (found ${existingCloudSmokers.size}), skipping welcome")
            }
        }
    }
    
    private fun showWelcomeScreenAfterGoogleLogin() {
        Log.d("WELCOME_DEBUG", "🚀 showWelcomeScreenAfterGoogleLogin() called")
        
        // Always show after Google login, regardless of previous showing
        Log.d("WELCOME_DEBUG", "⏰ Scheduling welcome screen to show in 500ms...")
        handler.postDelayed({
            Log.d("WELCOME_DEBUG", "🎭 Creating and showing WelcomeScreenDialog now!")
            val welcomeDialog = WelcomeScreenDialog(this) {
                // On completion callback - nothing special needed here
                Log.d("WELCOME_DEBUG", "✨ Welcome screen completed after Google login")
            }
            welcomeDialog.show()
            Log.d("WELCOME_DEBUG", "📱 WelcomeScreenDialog.show() called")
        }, 1000)
    }
    
    // Public methods for showing dialogs from WelcomeScreenDialog
    fun showAddStashDialog() {
        Log.d("WELCOME_DEBUG", "🏦 MainActivity.showAddStashDialog() called")
        // Navigate to stash tab (index 4) and show dialog
        Log.d("WELCOME_DEBUG", "📍 Navigating to stash tab (index 4)")
        binding.viewPager.currentItem = 4
        
        handler.postDelayed({
            Log.d("WELCOME_DEBUG", "🔍 Looking for StashFragment...")
            // Get all fragments and find StashFragment
            val fragments = supportFragmentManager.fragments
            Log.d("WELCOME_DEBUG", "📋 Found ${fragments.size} fragments total")
            val stashFragment = fragments.filterIsInstance<StashFragment>().firstOrNull()
            
            if (stashFragment != null) {
                Log.d("WELCOME_DEBUG", "✅ Found StashFragment, calling showAddStashDialogPublic()")
                stashFragment.showAddStashDialogPublic()
            } else {
                Log.d("WELCOME_DEBUG", "⚠️ StashFragment not found, retrying in 500ms...")
                // If fragment not found, try again after a delay
                handler.postDelayed({
                    val retryFragments = supportFragmentManager.fragments
                    val retryStashFragment = retryFragments.filterIsInstance<StashFragment>().firstOrNull()
                    if (retryStashFragment != null) {
                        Log.d("WELCOME_DEBUG", "✅ Found StashFragment on retry")
                        retryStashFragment.showAddStashDialogPublic()
                    } else {
                        Log.d("WELCOME_DEBUG", "❌ StashFragment still not found after retry")
                    }
                }, 500)
            }
        }, 1000)
    }
    
    fun showSetRatioDialog() {
        Log.d("WELCOME_DEBUG", "⚖️ MainActivity.showSetRatioDialog() called")
        // Navigate to stash tab (index 4) and show ratio dialog
        Log.d("WELCOME_DEBUG", "📍 Navigating to stash tab (index 4)")
        binding.viewPager.currentItem = 4
        
        handler.postDelayed({
            Log.d("WELCOME_DEBUG", "🔍 Looking for StashFragment...")
            // Get all fragments and find StashFragment
            val fragments = supportFragmentManager.fragments
            Log.d("WELCOME_DEBUG", "📋 Found ${fragments.size} fragments total")
            val stashFragment = fragments.filterIsInstance<StashFragment>().firstOrNull()
            
            if (stashFragment != null) {
                Log.d("WELCOME_DEBUG", "✅ Found StashFragment, calling showSetRatioDialogPublic()")
                stashFragment.showSetRatioDialogPublic()
            } else {
                Log.d("WELCOME_DEBUG", "⚠️ StashFragment not found, retrying in 500ms...")
                // If fragment not found, try again after a delay
                handler.postDelayed({
                    Log.d("WELCOME_DEBUG", "🔄 Retry: Looking for StashFragment...")
                    val retryFragments = supportFragmentManager.fragments
                    Log.d("WELCOME_DEBUG", "📋 Retry: Found ${retryFragments.size} fragments total")
                    val retryStashFragment = retryFragments.filterIsInstance<StashFragment>().firstOrNull()
                    
                    if (retryStashFragment != null) {
                        Log.d("WELCOME_DEBUG", "✅ Retry: Found StashFragment, calling showSetRatioDialogPublic()")
                        retryStashFragment.showSetRatioDialogPublic()
                    } else {
                        Log.d("WELCOME_DEBUG", "❌ Retry: StashFragment still not found!")
                    }
                }, 500)
            }
        }, 1000)
    }
    
    fun showAddGoalDialog() {
        Log.d("WELCOME_DEBUG", "🎯 MainActivity.showAddGoalDialog() called")
        // Navigate to goals tab (index 6) and show dialog
        Log.d("WELCOME_DEBUG", "📍 Navigating to goals tab (index 6)")
        binding.viewPager.currentItem = 6
        
        handler.postDelayed({
            Log.d("WELCOME_DEBUG", "🔍 Looking for GoalFragment...")
            // Get all fragments and find GoalFragment
            val fragments = supportFragmentManager.fragments
            Log.d("WELCOME_DEBUG", "📋 Found ${fragments.size} fragments total")
            val goalFragment = fragments.filterIsInstance<GoalFragment>().firstOrNull()
            
            if (goalFragment != null) {
                Log.d("WELCOME_DEBUG", "✅ Found GoalFragment, calling showAddGoalDialogPublic()")
                goalFragment.showAddGoalDialogPublic()
            } else {
                Log.d("WELCOME_DEBUG", "⚠️ GoalFragment not found, retrying in 500ms...")
                // If fragment not found, try again after a delay
                handler.postDelayed({
                    Log.d("WELCOME_DEBUG", "🔄 Retry: Looking for GoalFragment...")
                    val retryFragments = supportFragmentManager.fragments
                    Log.d("WELCOME_DEBUG", "📋 Retry: Found ${retryFragments.size} fragments total")
                    val retryGoalFragment = retryFragments.filterIsInstance<GoalFragment>().firstOrNull()
                    
                    if (retryGoalFragment != null) {
                        Log.d("WELCOME_DEBUG", "✅ Retry: Found GoalFragment, calling showAddGoalDialogPublic()")
                        retryGoalFragment.showAddGoalDialogPublic()
                    } else {
                        Log.d("WELCOME_DEBUG", "❌ Retry: GoalFragment still not found!")
                    }
                }, 500)
            }
        }, 1000)
    }

    // Save session state when starting/resuming
    private fun saveActiveSessionState() {
        if (sessionActive) {
            val prefs = getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putLong("active_session_id", editingSummaryId ?: -1L)
                putString("active_share_code", currentShareCode)
                putString("active_room_name", currentRoomName)
                putLong("active_session_start", sessionStart)
                apply()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveSessionToPrefs()
        saveActiveSessionState()
    }

    // Clear session state when ending
    private fun clearActiveSessionState() {
        val prefs = getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    private fun startRoomListener(shareCode: String) {
        Log.d(TAG, "🎧 Starting room listener for room: $shareCode")
        sessionSyncService.startRoomListener(
            shareCode,
            onChange = { updatedRoom ->
                // Call the handleRoomUpdate method
                handleRoomUpdate(updatedRoom)

                // Then do the rest of the onChange logic
                onRoomUpdated(updatedRoom)
                latestRoomData = updatedRoom

                Log.d(TAG, "🎧 Room updated!")
                Log.d(TAG, "    Activities: ${updatedRoom.safeActivities().size}")
                Log.d(TAG, "    Participants: ${updatedRoom.participants.size}")
                Log.d(TAG, "    Active: ${updatedRoom.activeParticipants.size}")
                Log.d(TAG, "    Shared smokers: ${updatedRoom.safeSharedSmokers().size}")

                // Update local pause/away status from room data
                pausedSmokerIds.clear()
                pausedSmokerIds.addAll(updatedRoom.safePausedSmokers())

                awaySmokers.clear()
                awaySmokers.addAll(updatedRoom.safeAwayParticipants())

                runOnUiThread {

                }

                // CRITICAL FIX: Calculate gaps from room activities before applying stats
                val roomActivities = updatedRoom.safeActivities()
                val sortedActivities = roomActivities.sortedBy { it.timestamp }

                var lastGapMs: Long? = null
                var previousGapMs: Long? = null

                if (sortedActivities.size >= 2) {
                    // Calculate gap between last two activities
                    val lastActivity = sortedActivities[sortedActivities.size - 1]
                    val secondLastActivity = sortedActivities[sortedActivities.size - 2]
                    lastGapMs = lastActivity.timestamp - secondLastActivity.timestamp

                    Log.d(TAG, "🎧 Calculated last gap: ${lastActivity.type} - ${secondLastActivity.type} = ${lastGapMs}ms")

                    // Calculate previous gap if we have 3+ activities
                    if (sortedActivities.size >= 3) {
                        val thirdLastActivity = sortedActivities[sortedActivities.size - 3]
                        previousGapMs = secondLastActivity.timestamp - thirdLastActivity.timestamp
                        Log.d(TAG, "🎧 Calculated previous gap: ${previousGapMs}ms")
                    }
                }

                // Apply room stats with calculated gaps
                val roomStats = updatedRoom.safeCurrentStats()

                // Get current group stats value for preserving rounds in sticky mode
                val currentGroupStats = sessionStatsVM.groupStats.value

                // Create GroupStats with the calculated gaps
                val groupStats = GroupStats(
                    totalCones = roomStats.totalCones,
                    totalJoints = roomStats.totalJoints,
                    totalBowls = roomStats.totalBowls,
                    longestGapMs = roomStats.longestGapMs,
                    shortestGapMs = roomStats.shortestGapMs,
                    sinceLastGapMs = roomStats.sinceLastConeMs,
                    sinceLastJointMs = roomStats.sinceLastJointMs,
                    sinceLastBowlMs = roomStats.sinceLastBowlMs,
                    totalRounds = if (isAutoMode) {
                        roomStats.totalRounds
                    } else {
                        Log.d(TAG, "📊 STICKY MODE: Preserving local rounds")
                        currentGroupStats?.totalRounds ?: 0
                    },
                    hitsInCurrentRound = if (isAutoMode) {
                        roomStats.hitsInCurrentRound
                    } else {
                        currentGroupStats?.hitsInCurrentRound ?: 0
                    },
                    participantCount = roomStats.participantCount,
                    lastConeSmokerName = roomStats.lastConeSmokerName,
                    lastJointSmokerName = roomStats.lastJointSmokerName,
                    lastBowlSmokerName = roomStats.lastBowlSmokerName,
                    conesSinceLastBowl = roomStats.conesSinceLastBowl,
                    lastGapMs = lastGapMs,
                    previousGapMs = previousGapMs
                )

                // Apply the stats with gaps using applyLocalStats instead of applyRoomStats
                sessionStatsVM.applyLocalStats(
                    roomStats.perSmokerStats.values.map { serverData ->
                        PerSmokerStats(
                            smokerName = serverData.smokerName,
                            totalCones = serverData.totalCones,
                            totalJoints = serverData.totalJoints,
                            totalBowls = serverData.totalBowls,
                            avgGapMs = serverData.avgGapMs,
                            longestGapMs = serverData.longestGapMs,
                            shortestGapMs = serverData.shortestGapMs,
                            avgJointGapMs = serverData.avgJointGapMs,
                            longestJointGapMs = serverData.longestJointGapMs,
                            shortestJointGapMs = serverData.shortestJointGapMs,
                            avgBowlGapMs = serverData.avgBowlGapMs,
                            longestBowlGapMs = serverData.longestBowlGapMs,
                            shortestBowlGapMs = serverData.shortestBowlGapMs
                        )
                    },
                    groupStats,
                    updatedRoom.startTime,
                    roomStats.lastConeSmokerName,
                    roomStats.conesSinceLastBowl
                )

                // Handle auto-add state changes
                val autoState = updatedRoom.safeAutoAddState()
                updateAutoAddUI(autoState, updatedRoom.safeActivities())
                autoAddManager.updateAutoAddState(autoState)

                // Update timing variables
                val activities = updatedRoom.safeActivities()
                if (activities.isNotEmpty()) {
                    val lastActivityTime = activities.maxOfOrNull { it.timestamp } ?: sessionStart
                    lastLogTime = lastActivityTime

                    val sortedActivitiesForInterval = activities.sortedBy { it.timestamp }
                    lastIntervalMillis = if (sortedActivitiesForInterval.size >= 2) {
                        val lastTwo = sortedActivitiesForInterval.takeLast(2)
                        lastTwo[1].timestamp - lastTwo[0].timestamp
                    } else {
                        0L
                    }

                    // FIX: Handle auto-advance for both cloud and local smokers
                    val latestActivity = activities.maxByOrNull { it.timestamp }
                    latestActivity?.let { act ->
                        val currentSelectedPos = binding.spinnerSmoker.selectedItemPosition
                        val sections = organizeSmokers()
                        val organizedSmokers = sections.flatMap { it.smokers }
                        val currentSmoker = organizedSmokers.getOrNull(currentSelectedPos)

                        if (currentSmoker != null) {
                            // Get the UID for the current smoker (works for both cloud and local)
                            val currentSmokerUid = if (currentSmoker.isCloudSmoker) {
                                currentSmoker.cloudUserId
                            } else {
                                "local_${currentSmoker.uid}"
                            }

                            // Check if this activity just came from UI (within last 2 seconds)
                            val timeSinceActivity = System.currentTimeMillis() - act.timestamp
                            val isFromUI = lastHitCameFromUI && timeSinceActivity < 2000

                            Log.d(TAG, "🎧 Checking auto-advance: activitySmoker=${act.smokerId}, currentSmoker=$currentSmokerUid, autoMode=$isAutoMode, isFromUI=$isFromUI, timeSince=${timeSinceActivity}ms")

                            // Check if the activity is from the current smoker and we should advance
                            if (isAutoMode && isFromUI && act.smokerId == currentSmokerUid && smokers.isNotEmpty()) {
                                runOnUiThread {
                                    Log.d(TAG, "🎧 Auto-advancing to next smoker from room sync")
                                    moveToNextActiveSmoker()
                                }
                            }

                            // Reset flag if enough time has passed
                            if (timeSinceActivity > 2000) {
                                lastHitCameFromUI = false
                            }
                        }
                    }
                } else {
                    // No activities, reset flag
                    lastHitCameFromUI = false
                }

                // Sync rounds counter from room - but ONLY if we're not actively updating
                val now = System.currentTimeMillis()
                val roomRoundsCounter = updatedRoom.roundsCounter

                // Only sync from room if we're not actively changing the counter locally
                when {
                    // Case 1: We're actively updating rounds locally - ignore room sync completely
                    isUpdatingRoundsLocally && (now - localRoundsUpdateTime < 5000) -> {
                        Log.d(TAG, "🔄 Ignoring room counter sync - local update in progress")
                    }

                    // Case 2: Room counter changed and we're not updating locally
                    roomRoundsCounter != initialRoundsSet && !isUpdatingRoundsLocally -> {
                        Log.d(TAG, "🔄 Room counter changed from $initialRoundsSet to $roomRoundsCounter")
                        // Only update if it's actually different and not just catching up
                        if (roomRoundsCounter != roundsLeft) {
                            initialRoundsSet = roomRoundsCounter
                            roundsLeft = roomRoundsCounter
                            // Reset tracking when counter changes from room
                            smokersTakenTurnSinceCounterChange.clear()
                            lastCounterChangeTime = now
                            updateRoundsUI()
                        }
                    }
                }

                // Track activities for rounds counter - completely separate from session rounds
                if (roundsLeft > 0 && isAutoMode) {
                    // Get activities that happened after the counter was last changed
                    val activitiesAfterCounterChange = activities.filter { it.timestamp > lastCounterChangeTime }

                    // Get active smokers (not paused or away)
                    val sharedSmokers = updatedRoom.safeSharedSmokers()
                    val pausedSmokers = updatedRoom.safePausedSmokers()
                    val activeSmokerIds = sharedSmokers.keys.filter { smokerId ->
                        !pausedSmokers.contains(smokerId) && !awaySmokers.contains(smokerId)
                    }.toSet()

                    // If no shared smokers yet, use local smokers
                    val effectiveSmokerIds = if (activeSmokerIds.isEmpty()) {
                        smokers.filter { !pausedSmokerIds.contains(it.smokerId.toString()) }
                            .map { smoker ->
                                if (smoker.isCloudSmoker) {
                                    smoker.cloudUserId ?: ""
                                } else {
                                    "local_${smoker.uid}"
                                }
                            }.filter { it.isNotEmpty() }.toSet()
                    } else {
                        activeSmokerIds
                    }

                    Log.d(TAG, "🔄 Tracking turns for ${effectiveSmokerIds.size} active smokers, roundsLeft=$roundsLeft")
                    Log.d(TAG, "🔄 Activities after counter change: ${activitiesAfterCounterChange.size}")
                    Log.d(TAG, "🔄 Smokers who have taken turns: ${smokersTakenTurnSinceCounterChange.joinToString()}")

                    // Process only NEW activities that we haven't seen before
                    for (activity in activitiesAfterCounterChange) {
                        // Create a unique ID for this activity
                        val activityId = "${activity.smokerId}_${activity.timestamp}_${activity.type}"

                        // Skip if we've already processed this activity
                        if (processedActivityIds.contains(activityId)) {
                            continue
                        }

                        // Mark this activity as processed
                        processedActivityIds.add(activityId)

                        val smokerId = activity.smokerId
                        if (smokerId in effectiveSmokerIds && !smokersTakenTurnSinceCounterChange.contains(smokerId)) {
                            smokersTakenTurnSinceCounterChange.add(smokerId)
                            Log.d(TAG, "🔄 Smoker $smokerId has taken their turn (${smokersTakenTurnSinceCounterChange.size}/${effectiveSmokerIds.size})")
                        }
                    }

                    // Check if everyone has had a turn (outside the loop!)
                    if (effectiveSmokerIds.isNotEmpty() && smokersTakenTurnSinceCounterChange.size >= effectiveSmokerIds.size) {
                        // Everyone has had a turn, decrement counter
                        val newRoundsLeft = kotlin.math.max(0, roundsLeft - 1)

                        // Only update if it actually changed
                        if (newRoundsLeft != roundsLeft) {
                            roundsLeft = newRoundsLeft
                            smokersTakenTurnSinceCounterChange.clear() // Clear for the next round

                            Log.d(TAG, "🔄 All smokers have taken a turn, decremented counter to: $roundsLeft")

                            // If we hit 0, switch to infinity mode
                            if (roundsLeft == 0 && initialRoundsSet > 0) {
                                initialRoundsSet = 0
                                processedActivityIds.clear() // Clear processed activities when going to infinity
                                // Mark that we're updating locally to prevent sync issues
                                isUpdatingRoundsLocally = true
                                localRoundsUpdateTime = System.currentTimeMillis()
                                updateRoundsCounterInRoom()
                                Log.d(TAG, "🔄 Counter reached 0, switching to infinity mode")
                            }

                            updateRoundsUI()
                        }
                    }
                }

                // Only reconcile activities, don't auto-sync smokers
                lifecycleScope.launch(Dispatchers.IO) {
                    reconcileRemoteActivitiesIntoLocal(updatedRoom)
                }

                runOnUiThread {
                    refreshNotificationsWithSession()
                }
            },
            onSmokerDeleted = { deletedSmokerId ->
                Log.d(TAG, "🗑️ Smoker removed from room: $deletedSmokerId")

                lifecycleScope.launch(Dispatchers.IO) {
                    // Find the local smoker to remove from UI but keep their data
                    val smokerToRemove = if (deletedSmokerId.startsWith("local_")) {
                        val localSmokerId = deletedSmokerId.removePrefix("local_").toLongOrNull()
                        if (localSmokerId != null) {
                            repo.getSmokerById(localSmokerId)
                        } else {
                            null
                        }
                    } else {
                        repo.getSmokerByCloudUserId(deletedSmokerId)
                    }

                    smokerToRemove?.let { smoker ->
                        Log.d(TAG, "🗑️ Removing smoker from session (keeping data): ${smoker.name}")

                        // Don't delete the smoker or their logs, just refresh the UI
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "${smoker.name} was removed from the session", Toast.LENGTH_SHORT).show()


                            // If this was the selected smoker, select a different one
                            val currentSelection = binding.spinnerSmoker.selectedItemPosition
                            val sections = organizeSmokers()
                            val organizedSmokers = sections.flatMap { it.smokers }

                            if (currentSelection >= 0 && currentSelection < organizedSmokers.size) {
                                val currentSmoker = organizedSmokers[currentSelection]
                                if (currentSmoker.smokerId == smoker.smokerId) {
                                    // Select the first available smoker
                                    if (organizedSmokers.isNotEmpty()) {
                                        binding.spinnerSmoker.setSelection(0)
                                        selectSmoker(organizedSmokers[0])
                                    }
                                }
                            }
                        }
                    }
                }
            },
            onAllSmokersDeleted = {
                Log.d(TAG, "🗑️ All smokers removed from session")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "All smokers were removed from the session", Toast.LENGTH_SHORT).show()

                }
            },
            onError = { error ->
                Log.e(TAG, "🎧 Room listener error: ${error.message}", error)
                Toast.makeText(
                    this@MainActivity,
                    "Room sync error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    // Add this helper function to MainActivity class if it doesn't exist
    private suspend fun getSmokerNameForActivity(smokerId: Long): String? {
        return withContext(Dispatchers.IO) {
            repo.getSmokerById(smokerId)?.name
        }
    }

    // Move handleRoomUpdate to be a separate method in the class
    private fun handleRoomUpdate(room: RoomData) {
        Log.d(TAG, "🎧 Room updated!")
        Log.d(TAG, "     Activities: ${room.safeActivities().size}")

        activitiesTimestamps.clear()
        room.safeActivities()
            .filter { it.timestamp >= sessionStart }
            .forEach { activity ->
                activitiesTimestamps.add(activity.timestamp)
            }
        activitiesTimestamps.sort()
        actualLastLogTime = activitiesTimestamps.maxOrNull() ?: 0L
        
        // Load last timestamps for each activity type
        val roomActivities = room.safeActivities()
        val coneLogs = roomActivities.filter { it.type == "CONE" }
        val jointLogs = roomActivities.filter { it.type == "JOINT" }
        val bowlLogs = roomActivities.filter { it.type == "BOWL" }
        
        lastConeTimestamp = coneLogs.maxOfOrNull { it.timestamp } ?: 0L
        lastJointTimestamp = jointLogs.maxOfOrNull { it.timestamp } ?: 0L
        lastBowlTimestamp = bowlLogs.maxOfOrNull { it.timestamp } ?: 0L

        // Rebuild activity history from room activities for current session
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sessionActivities = room.safeActivities()
                    .filter { it.timestamp >= sessionStart }
                    .sortedBy { it.timestamp }

                // Convert to ActivityLog objects
                val activityLogs = sessionActivities.mapNotNull { activity ->
                    // Find the smoker by UID
                    val smoker = smokers.find { smoker ->
                        val smokerUid = if (smoker.isCloudSmoker && !smoker.cloudUserId.isNullOrEmpty()) {
                            smoker.cloudUserId
                        } else {
                            "local_${smoker.uid}"
                        }
                        smokerUid == activity.smokerId
                    }

                    smoker?.let {
                        ActivityLog(
                            id = 0L, // Will be set by database if needed
                            smokerId = it.smokerId,
                            type = ActivityType.valueOf(activity.type),
                            timestamp = activity.timestamp
                            // REMOVED: sessionCount = 0
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    activityHistory.clear()
                    activityHistory.addAll(activityLogs)
                    updateUndoButtonVisibility()
                    Log.d(TAG, "🎧 Activity history rebuilt: ${activityHistory.size} activities")
                }
            } catch (e: Exception) {
                Log.e(TAG, "🎧 Error rebuilding activity history", e)
            }
        }
    }

    private fun updateAutoAddUI(autoState: AutoAddState, activities: List<SessionActivity>) {
        runOnUiThread {
            // Update checkboxes to match cloud state
            binding.checkboxConeAuto.isChecked = autoState.coneAutoEnabled
            binding.checkboxJointAuto.isChecked = autoState.jointAutoEnabled
            binding.checkboxBowlAuto.isChecked = autoState.bowlAutoEnabled

            // CRITICAL FIX: When enabling from cloud, we need to set up the intervals properly
            lifecycleScope.launch {
                if (autoState.coneAutoEnabled && !autoAddManager.isAutoEnabled(ActivityType.CONE)) {
                    setupAutoAddFromCloud(ActivityType.CONE, activities)
                }
                if (autoState.jointAutoEnabled && !autoAddManager.isAutoEnabled(ActivityType.JOINT)) {
                    setupAutoAddFromCloud(ActivityType.JOINT, activities)
                }
                if (autoState.bowlAutoEnabled && !autoAddManager.isAutoEnabled(ActivityType.BOWL)) {
                    setupAutoAddFromCloud(ActivityType.BOWL, activities)
                }
            }

            // Show/hide timers based on state and data availability
            updateAutoAddTimerVisibility(ActivityType.CONE,
                autoState.coneAutoEnabled && autoAddManager.hasEnoughDataForAuto(activities, ActivityType.CONE))
            updateAutoAddTimerVisibility(ActivityType.JOINT,
                autoState.jointAutoEnabled && autoAddManager.hasEnoughDataForAuto(activities, ActivityType.JOINT))
            updateAutoAddTimerVisibility(ActivityType.BOWL,
                autoState.bowlAutoEnabled && autoAddManager.hasEnoughDataForAuto(activities, ActivityType.BOWL))
        }
    }

    private suspend fun setupAutoAddFromCloud(activityType: ActivityType, activities: List<SessionActivity>) {
        val realNow = System.currentTimeMillis()
        val rewindedNow = realNow - rewindOffset

        // Calculate interval from activities
        val typeActivities = activities.filter {
            it.type.equals(activityType.name, ignoreCase = true)
        }.sortedBy { it.timestamp }

        if (typeActivities.size < 2) {
            Log.w(TAG, "🤖☁️ Not enough data for $activityType from cloud")
            return
        }

        val lastActivity = typeActivities.last()
        val secondLastActivity = typeActivities[typeActivities.size - 2]
        val interval = lastActivity.timestamp - secondLastActivity.timestamp
        val timeSinceLastActivity = rewindedNow - lastActivity.timestamp

        Log.d(TAG, "🤖☁️ Setting up $activityType from cloud: interval=${interval}ms, timeSince=${timeSinceLastActivity}ms")

        // Properly initialize with phase detection
        autoAddManager.enableAutoAddWithPhaseDetection(
            activityType = activityType,
            interval = interval,
            timeSinceLastActivity = timeSinceLastActivity,
            lastActivityTime = lastActivity.timestamp
        )
    }

    private fun updateRoundsFromServerStats(serverStats: SessionStats) {
        // Only update countdown if we're not making local changes
        if (!isUpdatingRoundsLocally) {
            val completedRounds = serverStats.totalRounds
            hitsThisRound = serverStats.hitsInCurrentRound
            actualRounds = completedRounds

            // Calculate remaining rounds
            if (initialRoundsSet > 0) {
                // We have a target number of rounds set
                val newRoundsLeft = kotlin.math.max(0, initialRoundsSet - completedRounds)
                if (newRoundsLeft != roundsLeft) {
                    roundsLeft = newRoundsLeft
                    updateRoundsUI()
                    Log.d(TAG, "🔄 Countdown: initial=$initialRoundsSet, completed=$completedRounds, remaining=$roundsLeft")
                }
            } else {
                // Infinity mode - keep showing infinity
                if (roundsLeft != 0) {
                    roundsLeft = 0 // 0 represents infinity
                    updateRoundsUI()
                    Log.d(TAG, "🔄 Infinite rounds: completed=$completedRounds")
                }
            }
        }
    }

    private fun debugSecureFolderCrash() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "🔍 === SECURE FOLDER DEBUG ===")

                // Check authentication
                val currentUserId = authManager.getCurrentUserId()
                Log.d(TAG, "🔍 Current user ID: $currentUserId")
                Log.d(TAG, "🔍 Is signed in: ${authManager.isSignedIn}")

                // Check smokers
                Log.d(TAG, "🔍 Total smokers: ${smokers.size}")
                smokers.forEach { smoker ->
                    Log.d(TAG, "🔍   Smoker: ${smoker.name} (cloud: ${smoker.isCloudSmoker}, ID: ${smoker.cloudUserId})")
                }

                // Check current smoker selection
                val currentSmoker = smokers.find { it.cloudUserId == currentUserId }
                Log.d(TAG, "🔍 Current smoker: ${currentSmoker?.name ?: "NOT FOUND"}")

                // Check session state
                Log.d(TAG, "🔍 Session active: $sessionActive")
                Log.d(TAG, "🔍 Current share code: $currentShareCode")

                // Check Firebase connection
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                Log.d(TAG, "🔍 Firebase project: ${firestore.app.options.projectId}")

                Log.d(TAG, "🔍 === END SECURE FOLDER DEBUG ===")

            } catch (e: Exception) {
                Log.e(TAG, "🔍 Debug error: ${e.message}", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // No-op placeholder if needed
    }

    private suspend fun updateParticipantsFromRoom(room: RoomData) {
        Log.d(TAG, "👥 Updating participants from room")
        var newSmokersAdded = false

        // Handle regular participants (cloud smokers)
        for (cloudId in room.participants) {
            val existingSmoker = repo.getSmokerByCloudUserId(cloudId)
            if (existingSmoker == null) {
                Log.d(TAG, "👥 Participant $cloudId not found locally, fetching profile...")

                cloudSyncService.getCloudSmokerProfile(cloudId).fold(
                    onSuccess = { cloudProfile ->
                        if (cloudProfile != null) {
                            val newSmoker = Smoker(
                                smokerId = 0L,
                                cloudUserId = cloudId,
                                name = cloudProfile.name,
                                isCloudSmoker = true,
                                shareCode = cloudProfile.shareCode,
                                lastSyncTime = System.currentTimeMillis()
                            )
                            repo.insertOrUpdateSmoker(newSmoker)
                            newSmokersAdded = true
                            Log.d(TAG, "👥 ✅ Added participant: ${newSmoker.name}")
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "👥 ❌ Failed to get profile for $cloudId: ${error.message}")
                    }
                )
            }
        }

        // Explicitly skip syncing shared smokers here to prevent duplicates.
        Log.d(TAG, "👥 Skipping shared smoker auto-sync here; use syncSharedSmokersFromRoom when desired")

        if (newSmokersAdded) {
            withContext(Dispatchers.Main) {
                Log.d(TAG, "👥 New participants added, smoker list will refresh")
            }
        }
    }

    // handle smoker deletions
    private fun deleteSmokerFromRoom(smoker: Smoker) {
        Log.d(TAG, "🗑️ === DELETE SMOKER START ===")
        Log.d(TAG, "🗑️ Smoker: ${smoker.name} (ID: ${smoker.smokerId}, Cloud: ${smoker.isCloudSmoker})")

        // Show dialog with keep data option
        showDeleteSmokerDialog(smoker) { keepData ->
            Log.d(TAG, "🗑️ User choice - Keep data: $keepData")

            val shareCode = currentShareCode
            if (shareCode == null) {
                // Just delete locally if not in a room
                deleteLocalSmoker(smoker, keepData)
                return@showDeleteSmokerDialog
            }

            lifecycleScope.launch {
                val currentUserId = authManager.getCurrentUserId()
                if (currentUserId != null) {
                    // Use the correct UID for removal
                    val smokerUidToRemove = if (smoker.isCloudSmoker) {
                        smoker.cloudUserId!!
                    } else {
                        "local_${smoker.uid}"
                    }

                    Log.d(TAG, "🗑️ Removing from room - UID: $smokerUidToRemove")

                    sessionSyncService.removeSmokerFromRoom(
                        shareCode = shareCode,
                        smokerUid = smokerUidToRemove,
                        removedByUserId = currentUserId
                    ).fold(
                        onSuccess = {
                            Log.d(TAG, "🗑️ ✅ Removed from room successfully")
                            deleteLocalSmoker(smoker, keepData)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    if (keepData) "Removed ${smoker.name} (data kept)"
                                    else "Deleted ${smoker.name} completely",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "🗑️ ❌ Failed to remove from room: ${error.message}")
                            deleteLocalSmoker(smoker, keepData)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Deleted locally (room sync failed)",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                } else {
                    // Not signed in, just delete locally
                    deleteLocalSmoker(smoker, keepData)
                }
            }
        }

        Log.d(TAG, "🗑️ === DELETE SMOKER END ===")
    }

    private fun deleteLocalSmoker(smoker: Smoker, keepData: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d(TAG, "🗑️ Deleting locally - Keep data: $keepData")

            if (!keepData) {
                // Delete all activity logs
                val logs = repo.getLogsForSmoker(smoker.smokerId)
                Log.d(TAG, "🗑️ Deleting ${logs.size} activity logs")
                logs.forEach { log ->
                    repo.delete(log)
                }
            } else {
                Log.d(TAG, "🗑️ Keeping ${repo.getLogsForSmoker(smoker.smokerId).size} activity logs")
            }

            // Always delete the smoker entity
            repo.deleteSmoker(smoker)
            Log.d(TAG, "🗑️ ✅ Smoker deleted from local database")

            withContext(Dispatchers.Main) {
                val message = if (keepData) {
                    "${smoker.name} removed (historical data kept)"
                } else {
                    "${smoker.name} and all data deleted"
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteSmokerDialog(smoker: Smoker, onResult: (Boolean) -> Unit) {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        val mainCard = androidx.cardview.widget.CardView(this).apply {
            radius = 16.dpToPx(this@MainActivity).toFloat()
            cardElevation = 8.dpToPx(this@MainActivity).toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))

            layoutParams = FrameLayout.LayoutParams(
                300.dpToPx(this@MainActivity),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(this@MainActivity), 20.dpToPx(this@MainActivity),
                20.dpToPx(this@MainActivity), 20.dpToPx(this@MainActivity))
        }

        val warningIcon = TextView(this).apply {
            text = "⚠️"
            textSize = 36f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(warningIcon)

        val titleText = TextView(this).apply {
            text = "DELETE ${smoker.name.uppercase()}"
            textSize = 18f
            setTextColor(Color.parseColor("#FFA366"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(titleText)

        val messageText = TextView(this).apply {
            text = "What would you like to do?"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(messageText)

        // NEW BUTTON: Clear Sesh Stats (at the top)
        val clearStatsButton = createThemedDialogButton("Clear Sesh Stats", false, Color.parseColor("#98FB98")) {
            Log.d(TAG, "🗑️ User selected: Clear Sesh Stats for ${smoker.name}")
            dialog.dismiss()
            clearSeshStatsForSmoker(smoker)
        }
        clearStatsButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            44.dpToPx(this@MainActivity)
        ).apply {
            bottomMargin = 8.dpToPx(this@MainActivity)
        }
        contentLayout.addView(clearStatsButton)

        // Keep Data button
        val keepDataButton = createThemedDialogButton("Remove Smoker (Keep Data)", false, Color.parseColor("#66B2FF")) {
            Log.d(TAG, "🗑️ User selected: Keep Data")
            dialog.dismiss()
            onResult(true) // true = keep data
        }
        keepDataButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            44.dpToPx(this@MainActivity)
        ).apply {
            bottomMargin = 8.dpToPx(this@MainActivity)
        }
        contentLayout.addView(keepDataButton)

        // Delete Everything button
        val deleteAllButton = createThemedDialogButton("Delete Everything", true, Color.parseColor("#FFA366")) {
            Log.d(TAG, "🗑️ User selected: Delete Everything")
            dialog.dismiss()
            onResult(false) // false = delete everything
        }
        deleteAllButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            44.dpToPx(this@MainActivity)
        ).apply {
            bottomMargin = 8.dpToPx(this@MainActivity)
        }
        contentLayout.addView(deleteAllButton)

        // Cancel button
        val cancelButton = createThemedDialogButton("Cancel", false, Color.WHITE) {
            Log.d(TAG, "🗑️ User selected: Cancel")
            dialog.dismiss()
        }
        cancelButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            44.dpToPx(this@MainActivity)
        )
        contentLayout.addView(cancelButton)

        mainCard.addView(contentLayout)
        rootContainer.addView(mainCard)

        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                dialog.dismiss()
            }
        }

        dialog.setContentView(rootContainer)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
        }

        rootContainer.alpha = 0f
        dialog.show()
        performManualFadeIn(rootContainer, 500L)
    }

    private fun showDeleteAllDialog(onResult: (Boolean) -> Unit) {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        val mainCard = androidx.cardview.widget.CardView(this).apply {
            radius = 16.dpToPx(this@MainActivity).toFloat()
            cardElevation = 8.dpToPx(this@MainActivity).toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))

            layoutParams = FrameLayout.LayoutParams(
                300.dpToPx(this@MainActivity),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(this@MainActivity), 20.dpToPx(this@MainActivity),
                20.dpToPx(this@MainActivity), 20.dpToPx(this@MainActivity))
        }

        val warningIcon = TextView(this).apply {
            text = "⚠️"
            textSize = 36f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(warningIcon)

        val titleText = TextView(this).apply {
            text = "DELETE ALL SMOKERS"
            textSize = 18f
            setTextColor(Color.parseColor("#FFA366"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(titleText)

        val messageText = TextView(this).apply {
            text = buildString {
                append("This affects ALL ${smokers.size} smokers.")
                if (currentShareCode != null) {
                    append("\n\nThis will affect all participants in the room.")
                }
                append("\n\nWhat would you like to do?")
            }
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(messageText)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2.dpToPx(this@MainActivity)
            ).apply {
                topMargin = 4.dpToPx(this@MainActivity)
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
            setBackgroundColor(Color.parseColor("#3398FB98"))
        }
        contentLayout.addView(divider)

        // NEW BUTTON: Clear All Sesh Stats (at the top)
        val clearAllStatsButton = createThemedDialogButton("Clear All Sesh Stats", false, Color.parseColor("#98FB98")) {
            Log.d(TAG, "🗑️🔴 User selected: Clear All Sesh Stats")
            dialog.dismiss()
            clearAllSeshStats()
        }
        clearAllStatsButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            44.dpToPx(this@MainActivity)
        ).apply {
            bottomMargin = 8.dpToPx(this@MainActivity)
        }
        contentLayout.addView(clearAllStatsButton)

        // Keep Data button
        val keepDataButton = createThemedDialogButton("Remove All (Keep Data)", false, Color.parseColor("#66B2FF")) {
            Log.d(TAG, "🗑️🔴 User selected: Keep Data")
            dialog.dismiss()
            onResult(true) // true = keep data
        }
        keepDataButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            44.dpToPx(this@MainActivity)
        ).apply {
            bottomMargin = 8.dpToPx(this@MainActivity)
        }
        contentLayout.addView(keepDataButton)

        // Delete All button
        val deleteAllButton = createThemedDialogButton("Delete Everything", true, Color.parseColor("#FFA366")) {
            Log.d(TAG, "🗑️🔴 User selected: Delete Everything")
            dialog.dismiss()
            onResult(false) // false = delete everything
        }
        deleteAllButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            44.dpToPx(this@MainActivity)
        ).apply {
            bottomMargin = 8.dpToPx(this@MainActivity)
        }
        contentLayout.addView(deleteAllButton)

        // Cancel button
        val cancelButton = createThemedDialogButton("Cancel", false, Color.WHITE) {
            Log.d(TAG, "🗑️🔴 User selected: Cancel")
            dialog.dismiss()
        }
        cancelButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            44.dpToPx(this@MainActivity)
        )
        contentLayout.addView(cancelButton)

        // REMOVED: contentLayout.addView(buttonContainer) - this line was the error

        mainCard.addView(contentLayout)
        rootContainer.addView(mainCard)

        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                dialog.dismiss()
            }
        }

        dialog.setContentView(rootContainer)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
        }

        rootContainer.alpha = 0f
        dialog.show()
        performManualFadeIn(rootContainer, 500L)
    }
    
    private fun clearSeshStatsForSmoker(smoker: Smoker) {
        Log.d(TAG, "🧹 === CLEAR SESH STATS FOR ${smoker.name} START ===")
        Log.d(TAG, "🧹 Session active: $sessionActive")
        Log.d(TAG, "🧹 Session start: $sessionStart")
        Log.d(TAG, "🧹 Current share code: $currentShareCode")

        if (!sessionActive) {
            Toast.makeText(this, "No active session to clear", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val now = System.currentTimeMillis()

                // Clear from local database
                withContext(Dispatchers.IO) {
                    val logsCleared = repo.clearSessionLogsForSmoker(smoker.smokerId, sessionStart, now)
                    Log.d(TAG, "🧹 Cleared $logsCleared local activities for ${smoker.name}")
                }

                // Clear from cloud room if in one
                currentShareCode?.let { shareCode ->
                    val smokerUid = if (smoker.isCloudSmoker && !smoker.cloudUserId.isNullOrEmpty()) {
                        smoker.cloudUserId
                    } else {
                        "local_${smoker.uid}"
                    }

                    sessionSyncService.clearSessionActivitiesForSmoker(
                        shareCode = shareCode,
                        smokerUid = smokerUid!!,
                        sessionStart = sessionStart
                    ).fold(
                        onSuccess = {
                            Log.d(TAG, "🧹 ✅ Cleared cloud activities for ${smoker.name}")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "🧹 ❌ Failed to clear cloud activities: ${error.message}")
                        }
                    )
                }

                // Remove from activity history
                activityHistory.removeAll { it.smokerId == smoker.smokerId }

                // Force refresh stats
                withContext(Dispatchers.Main) {
                    if (currentShareCode == null) {
                        refreshLocalSessionStatsIfNeeded()
                    }
                    sessionStatsVM.forceLocalStatsRefresh()
                    updateUndoButtonVisibility()

                    Toast.makeText(this@MainActivity, "Cleared session stats for ${smoker.name}", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "🧹 === CLEAR SESH STATS FOR ${smoker.name} COMPLETE ===")
                }

            } catch (e: Exception) {
                Log.e(TAG, "🧹 Error clearing stats for ${smoker.name}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error clearing stats", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun clearAllSeshStats() {
        Log.d(TAG, "🧹🔴 === CLEAR ALL SESH STATS START ===")
        Log.d(TAG, "🧹🔴 Session active: $sessionActive")
        Log.d(TAG, "🧹🔴 Session start: $sessionStart")
        Log.d(TAG, "🧹🔴 Current share code: $currentShareCode")

        if (!sessionActive) {
            Toast.makeText(this, "No active session to clear", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val now = System.currentTimeMillis()
                var totalCleared = 0

                // Clear for all smokers
                smokers.forEach { smoker ->
                    withContext(Dispatchers.IO) {
                        val logsCleared = repo.clearSessionLogsForSmoker(smoker.smokerId, sessionStart, now)
                        totalCleared += logsCleared
                        Log.d(TAG, "🧹🔴 Cleared $logsCleared activities for ${smoker.name}")
                    }
                }

                Log.d(TAG, "🧹🔴 Total local activities cleared: $totalCleared")

                // Clear from cloud room if in one
                currentShareCode?.let { shareCode ->
                    sessionSyncService.clearAllSessionActivities(
                        shareCode = shareCode,
                        sessionStart = sessionStart
                    ).fold(
                        onSuccess = {
                            Log.d(TAG, "🧹🔴 ✅ Cleared all cloud activities")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "🧹🔴 ❌ Failed to clear cloud activities: ${error.message}")
                        }
                    )
                }

                // Clear all activity history
                activityHistory.clear()
                activitiesTimestamps.clear()

                // Reset session variables
                actualLastLogTime = 0L
                lastLogTime = sessionStart
                lastIntervalMillis = 0L
                intervalsList.clear()
                hitsThisRound = 0
                actualRounds = 0

                // Force refresh stats
                withContext(Dispatchers.Main) {
                    if (currentShareCode == null) {
                        refreshLocalSessionStatsIfNeeded()
                    }
                    sessionStatsVM.clearAllStats()
                    sessionStatsVM.forceLocalStatsRefresh()
                    updateUndoButtonVisibility()

                    Toast.makeText(this@MainActivity, "Cleared all session stats", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "🧹🔴 === CLEAR ALL SESH STATS COMPLETE ===")
                }

            } catch (e: Exception) {
                Log.e(TAG, "🧹🔴 Error clearing all stats", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error clearing stats", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Reconcile remote room activities into the local Room DB, avoiding duplicates.
     */
    private suspend fun reconcileRemoteActivitiesIntoLocal(updatedRoom: RoomData) {
        val remoteActivities = updatedRoom.safeActivities()
        Log.d(TAG, "🔁 Reconciling ${remoteActivities.size} remote activities")

        // IMPORTANT: First, remove any local activities that are no longer in the room
        // This handles the undo case where activities were removed from the room
        val localSessionActivities = withContext(Dispatchers.IO) {
            repo.getLogsInTimeRange(sessionStart, System.currentTimeMillis())
        }

        // Create a set of remote activity identifiers for quick lookup
        val remoteActivityIds = remoteActivities.map {
            "${it.smokerId}_${it.type}_${it.timestamp}"
        }.toSet()

        // Delete local activities that are no longer in the remote room
        for (localActivity in localSessionActivities) {
            val smoker = repo.getSmokerById(localActivity.smokerId)
            if (smoker != null) {
                val smokerUid = if (smoker.isCloudSmoker && !smoker.cloudUserId.isNullOrEmpty()) {
                    smoker.cloudUserId
                } else {
                    "local_${smoker.uid}"
                }

                val localActivityId = "${smokerUid}_${localActivity.type.name}_${localActivity.timestamp}"

                if (!remoteActivityIds.contains(localActivityId)) {
                    // This activity exists locally but not in the room - delete it
                    Log.d(TAG, "🔁 Removing local activity not in room: ${smoker.name} ${localActivity.type} @ ${localActivity.timestamp}")
                    repo.delete(localActivity)
                }
            }
        }

        // Now add any remote activities that don't exist locally
        for (remote in remoteActivities) {
            try {
                val smokerUid = remote.smokerId
                val localSmoker = if (smokerUid.startsWith("local_")) {
                    repo.getSmokerByUid(smokerUid.removePrefix("local_"))
                } else {
                    repo.getSmokerByCloudUserId(smokerUid)
                }

                if (localSmoker == null) {
                    Log.w(TAG, "🔁 No local smoker found for activity with UID: ${remote.smokerId} (Name: ${remote.smokerName}) - skipping")
                    continue
                }

                val activityType = try {
                    ActivityType.valueOf(remote.type.uppercase())
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "🔁 Unknown activity type: ${remote.type}")
                    continue
                }

                // Check if this exact log already exists locally
                val existingLog = repo.findLogByDetails(localSmoker.smokerId, activityType, remote.timestamp)

                if (existingLog != null) {
                    // Already exists, skip
                    continue
                }

                // Create the activity log
                val newLog = ActivityLog(
                    id = 0L,
                    smokerId = localSmoker.smokerId,
                    type = activityType,
                    timestamp = remote.timestamp
                )

                repo.insert(newLog)
                Log.d(TAG, "🔁 ✅ Reconciled activity: ${localSmoker.name} ${activityType.name} @ ${remote.timestamp}")

            } catch (e: Exception) {
                Log.e(TAG, "🔁 Error reconciling activity: ${e.message}", e)
            }
        }
        Log.d(TAG, "🔁 Activity reconciliation complete")
    }

    private suspend fun logHit(type: ActivityType, now: Long) {
        // Add session check at the beginning
        if (!sessionActive) {
            Log.w(TAG, "🎯 Cannot log hit - session not active")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Please start a session first", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // CHECK AND SWITCH STASH SOURCE IF NEEDED
        checkAndSwitchStashSource(type)

        val stashViewModel = ViewModelProvider(this).get(StashViewModel::class.java)
        val currentStash = stashViewModel.currentStash.value
        val ratios = stashViewModel.ratios.value

        val adjustedNow = now - rewindOffset

        Log.d(TAG, "🎯 === LOGHIT START ===")
        Log.d(TAG, "🎯 Type: $type, Effective Time: $adjustedNow, Real Time: $now, Rewind offset: $rewindOffset")
        Log.d(TAG, "🎯 Session active: $sessionActive")
        Log.d(TAG, "🎯 currentShareCode: $currentShareCode")
        Log.d(TAG, "🎯 sessionStart: $sessionStart")
        Log.d(TAG, "🎯 Network available: $isNetworkAvailable")

        // Log bowl quantity if it's a bowl
        if (type == ActivityType.BOWL) {
            Log.d(TAG, "🎯 Bowl quantity: $pendingBowlQuantity")
        }

        val selectedPosition = binding.spinnerSmoker.selectedItemPosition
        val organizedSmokers = organizeSmokers().flatMap { it.smokers }
        if (selectedPosition < 0 || selectedPosition >= organizedSmokers.size) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Please select a valid smoker!", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val selectedSmoker = organizedSmokers[selectedPosition]
        val shareCode = currentShareCode

        // CRITICAL FIX: Always use the selected smoker's UID, not the current user's ID
        val smokerActivityUid = if (selectedSmoker.isCloudSmoker && !selectedSmoker.cloudUserId.isNullOrEmpty()) {
            selectedSmoker.cloudUserId!!
        } else {
            "local_${selectedSmoker.uid}"
        }

        Log.d(TAG, "🎯 Selected smoker: ${selectedSmoker.name}, UID for activity: $smokerActivityUid")

        // Get stash source and determine payerStashOwnerId
        val stashSource = stashViewModel.stashSource.value ?: StashSource.MY_STASH
        val currentUserId = authManager.getCurrentUserId() ?: getAndroidDeviceId()

        val payerStashOwnerId = when (stashSource) {
            StashSource.MY_STASH -> {
                Log.d(TAG, "🎯 MY_STASH selected - setting payerStashOwnerId to null")
                null
            }
            StashSource.THEIR_STASH -> {
                Log.d(TAG, "🎯 THEIR_STASH selected - setting payerStashOwnerId to 'their_stash'")
                "their_stash"
            }
            StashSource.EACH_TO_OWN -> {
                if (selectedSmoker.cloudUserId == currentUserId || selectedSmoker.uid == currentUserId) {
                    Log.d(TAG, "🎯 EACH_TO_OWN - Current user, setting to null")
                    null
                } else {
                    val otherId = "other_${selectedSmoker.smokerId}"
                    Log.d(TAG, "🎯 EACH_TO_OWN - Other user, setting to $otherId")
                    otherId
                }
            }
        }

        // ALWAYS create and insert the activity log locally
        val sessionId = if (sessionActive) sessionStart else null
        Log.d(TAG, "🎯 Creating activity with sessionId: $sessionId")

        val activityLog = ActivityLog(
            smokerId = selectedSmoker.smokerId,
            consumerId = selectedSmoker.smokerId,
            payerStashOwnerId = payerStashOwnerId,
            type = type,
            timestamp = adjustedNow,
            sessionId = sessionId,
            sessionStartTime = if (sessionActive) sessionStart else null,
            bowlQuantity = if (type == ActivityType.BOWL) pendingBowlQuantity else 1,
            gramsAtLog = when (type) {
                ActivityType.CONE -> ratios?.coneGrams ?: 0.3
                ActivityType.JOINT -> ratios?.jointGrams ?: 0.5
                ActivityType.BOWL -> (ratios?.bowlGrams ?: 0.2) * pendingBowlQuantity
                else -> 0.0
            },
            pricePerGramAtLog = currentStash?.pricePerGram ?: 15.0
        )

        // ALWAYS insert to local database first
        withContext(Dispatchers.IO) {
            val insertedId = repo.insert(activityLog)
            Log.d(TAG, "🎯 Inserted activity to local DB with ID: $insertedId, sessionId: $sessionId")
        }

        // THEN sync to cloud if in a cloud session
        if (shareCode != null) {
            Log.d(TAG, "🎯 Cloud session detected, checking network status...")
            val deviceId = getAndroidDeviceId()

            if (!isNetworkAvailable) {
                // OFFLINE - Add to queue
                Log.d(TAG, "📴 OFFLINE: Adding activity to queue for later sync")
                val offlineActivity = OfflineActivity(
                    shareCode = shareCode,
                    smokerUid = smokerActivityUid,
                    smokerName = selectedSmoker.name,
                    activityType = type,
                    timestamp = adjustedNow,
                    deviceId = deviceId
                )
                addToOfflineQueue(offlineActivity)

                // Still trigger local UI updates
                lastHitCameFromUI = true
                handler.postDelayed({
                    lastHitCameFromUI = false
                }, 500)
            } else {
                // ONLINE - Try to sync immediately
                Log.d(TAG, "🎯 Online - syncing to cloud room $shareCode")
                sessionSyncService.addActivityToRoom(
                    shareCode = shareCode,
                    smokerUid = smokerActivityUid,
                    smokerName = selectedSmoker.name,
                    activityType = type,
                    timestamp = adjustedNow,
                    deviceId = deviceId
                ).fold(
                    onSuccess = {
                        Log.d(TAG, "🎯 ✅ Activity synced to cloud room with smoker UID: $smokerActivityUid")
                        lastHitCameFromUI = true
                        handler.postDelayed({
                            lastHitCameFromUI = false
                            Log.d(TAG, "🎯 Reset lastHitCameFromUI flag after delay")
                        }, 500)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "🎯 ❌ Failed to sync to cloud room: ${error.message}")
                        // Add to offline queue as fallback
                        val offlineActivity = OfflineActivity(
                            shareCode = shareCode,
                            smokerUid = smokerActivityUid,
                            smokerName = selectedSmoker.name,
                            activityType = type,
                            timestamp = adjustedNow,
                            deviceId = deviceId
                        )
                        addToOfflineQueue(offlineActivity)
                    }
                )
            }
        } else {
            // Local session - refresh stats
            Log.d(TAG, "🎯 Local session, refreshing stats...")
            refreshLocalSessionStatsIfNeeded()
        }

        // Handle post-hit actions
        handlePostHitActionsWithPayer(selectedSmoker, selectedPosition, type, adjustedNow, payerStashOwnerId)

        Log.d(TAG, "🎯 === LOGHIT END ===")
    }

    private suspend fun syncSharedSmokersFromRoom(room: RoomData) {
        Log.d(TAG, "👥 Syncing shared smokers from room")

        val sharedSmokers = room.safeSharedSmokers()
        val currentUserId = authManager.getCurrentUserId() ?: return

        val newLocalSmokers = sessionSyncService.syncRoomSmokersToLocal(currentUserId, sharedSmokers)

        if (newLocalSmokers.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Added ${newLocalSmokers.size} new smokers from room", Toast.LENGTH_SHORT).show()
            }
            Log.d(TAG, "👥 ✅ Synced ${newLocalSmokers.size} new smokers from room")
        }
    }

    private suspend fun handlePostHitActionsWithPayer(
        selectedSmoker: Smoker,
        selectedPosition: Int,
        type: ActivityType,
        now: Long,
        payerStashOwnerId: String?
    ) {
        Log.d(TAG, "🎯 === HANDLE POST HIT ACTIONS WITH PAYER START ===")
        Log.d(TAG, "🎯 PayerStashOwnerId: '$payerStashOwnerId'")
        Log.d(TAG, "🎯 Auto mode: $isAutoMode")
        Log.d(TAG, "🎯 Activity type: $type")
        Log.d(TAG, "🎯🔴 DEBUG: sessionActive = $sessionActive")
        Log.d(TAG, "🎯🔴 DEBUG: activitiesTimestamps.size = ${activitiesTimestamps.size}")
        Log.d(TAG, "🎯🔴 DEBUG: smokers.size = ${smokers.size}")
        Log.d(TAG, "🎯🔴 DEBUG: currentShareCode = $currentShareCode")
        Log.d(TAG, "🎯🔴 DEBUG: selectedSmoker = ${selectedSmoker.name}")

        // Only update session-related data if session is active
        if (sessionActive) {
            activitiesTimestamps.add(now)
            activitiesTimestamps.sort()
            actualLastLogTime = activitiesTimestamps.maxOrNull() ?: now
            lastLogTime = now
            
            // Update specific activity type timestamps
            when (type) {
                ActivityType.CONE -> lastConeTimestamp = now
                ActivityType.JOINT -> lastJointTimestamp = now
                ActivityType.BOWL -> lastBowlTimestamp = now
                ActivityType.SESSION_SUMMARY -> { /* Session summaries don't update timestamps */ }
            }

            // Notify auto-add manager about the manual activity - FIXED: Added timestamp parameter
            if (::autoAddManager.isInitialized) {
                autoAddManager.onActivityLogged(type, now)
            }

            // Create activity log for history tracking
            val activityLog = ActivityLog(
                id = 0L,
                smokerId = selectedSmoker.smokerId,
                consumerId = selectedSmoker.smokerId,
                payerStashOwnerId = payerStashOwnerId,
                type = type,
                timestamp = now,
                sessionId = if (sessionActive) sessionStart else null,
                sessionStartTime = if (sessionActive) sessionStart else null,
                gramsAtLog = 0.0, // These will be set by stash tracking
                pricePerGramAtLog = 0.0
            )

            activityHistory.add(activityLog)
            if (activityHistory.size > 10) {
                activityHistory.removeAt(0)
            }

            val activitiesBeforeThis = activitiesTimestamps.filter { it < now }
            if (activitiesBeforeThis.isNotEmpty()) {
                val prevActivity = activitiesBeforeThis.last()
                val interval = now - prevActivity
                lastIntervalMillis = interval
                intervalsList.add(interval)
            } else {
                intervalsList.add(0L)
            }

            // Handle rounds counter for local sessions - EXCLUDE BOWLS
            if (isAutoMode && currentShareCode == null && initialRoundsSet > 0 && type != ActivityType.BOWL) {
                val smokerUid = if (selectedSmoker.isCloudSmoker && !selectedSmoker.cloudUserId.isNullOrEmpty()) {
                    selectedSmoker.cloudUserId
                } else {
                    "local_${selectedSmoker.uid}"
                }

                if (!smokersTakenTurnSinceCounterChange.contains(smokerUid)) {
                    smokersTakenTurnSinceCounterChange.add(smokerUid)
                    Log.d(TAG, "🔄 Local: Smoker ${selectedSmoker.name} has taken their turn (${smokersTakenTurnSinceCounterChange.size}/${getActiveSmokers().size})")
                }

                val activeSmokerCount = getActiveSmokers().size
                if (activeSmokerCount > 0 && smokersTakenTurnSinceCounterChange.size >= activeSmokerCount) {
                    roundsLeft = kotlin.math.max(0, roundsLeft - 1)
                    smokersTakenTurnSinceCounterChange.clear()

                    Log.d(TAG, "🔄 Local: All smokers have taken a turn, decremented counter to: $roundsLeft")

                    if (roundsLeft == 0 && initialRoundsSet > 0) {
                        initialRoundsSet = 0
                        Log.d(TAG, "🔄 Local: Counter reached 0, switching to infinity mode")
                    }

                    updateRoundsUI()
                }
            }

            // Handle session rounds - EXCLUDE BOWLS
            if (isAutoMode && currentShareCode == null && type != ActivityType.BOWL) {
                hitsThisRound++
                val activeSmokerCount = getActiveSmokers().size
                if (activeSmokerCount > 0 && hitsThisRound >= activeSmokerCount) {
                    hitsThisRound = 0
                    actualRounds++
                    updateRoundsUI()
                }
            }

            if (notificationsEnabled) {
                val helper = NotificationHelper(this@MainActivity)
                val smokerCloudId = selectedSmoker.cloudUserId
                withContext(Dispatchers.Main) {
                    helper.showActivityNotification(
                        type,
                        lastTimestamp = now,
                        conesSinceLastBowl = null,
                        currentShareCode,
                        smokerCloudId,
                        justAdded = true,
                        addedAt = now,
                        lastSmokerName = selectedSmoker.name
                    )
                }
            }

            if (notificationsEnabled) {
                handler.postDelayed({
                    refreshNotificationsWithSession()
                }, 500)
            }

            sessionStatsVM.refreshTimer()
        }

        // CRITICAL FIX: Move auto-advance logic outside sessionActive block
        // This ensures first activity also triggers auto-advance
        Log.d(TAG, "🎯🔴 DEBUG AUTO-ADVANCE CHECK:")
        Log.d(TAG, "🎯🔴   - isAutoMode = $isAutoMode")
        Log.d(TAG, "🎯🔴   - smokers.isNotEmpty() = ${smokers.isNotEmpty()}")
        Log.d(TAG, "🎯🔴   - currentShareCode = $currentShareCode")
        Log.d(TAG, "🎯🔴   - type = $type")
        Log.d(TAG, "🎯🔴   - type != ActivityType.BOWL = ${type != ActivityType.BOWL}")
        Log.d(TAG, "🎯🔴   - type == ActivityType.CONE = ${type == ActivityType.CONE}")
        Log.d(TAG, "🎯🔴   - SHOULD ADVANCE? = ${isAutoMode && smokers.isNotEmpty() && currentShareCode == null && type != ActivityType.BOWL}")
        
        if (isAutoMode && smokers.isNotEmpty() && currentShareCode == null && type != ActivityType.BOWL) {
            withContext(Dispatchers.Main) {
                Log.d(TAG, "🎯🟢 ADVANCING to next smoker after ${selectedSmoker.name} (local session, type: $type)")
                moveToNextActiveSmoker()
                Log.d(TAG, "🎯🟢 ADVANCE COMPLETE")
            }
        } else if (isAutoMode && currentShareCode != null && type != ActivityType.BOWL) {
            Log.d(TAG, "🎯🟡 NOT advancing smoker (will be handled by room sync)")
        } else {
            Log.d(TAG, "🎯🔴 NOT advancing smoker (autoMode: $isAutoMode, type: $type, isBowl: ${type == ActivityType.BOWL})")
        }

        // STASH TRACKING
        val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
        if (stashViewModel.currentStash.value != null) {
            val smokerUid = if (selectedSmoker.isCloudSmoker && !selectedSmoker.cloudUserId.isNullOrEmpty()) {
                selectedSmoker.cloudUserId
            } else {
                "local_${selectedSmoker.uid}"
            }
            stashViewModel.recordConsumption(
                activityType = type,
                smokerUid = smokerUid!!,
                smokerName = selectedSmoker.name,
                timestamp = now
            )
            stashViewModel.onActivityLogged(type)
        }

        // GOAL TRACKING
        Log.d(TAG, "🎯 ABOUT TO UPDATE GOALS")
        Log.d(TAG, "🎯 goalService initialized: ${::goalService.isInitialized}")

        if (::goalService.isInitialized) {
            val sessionShareCode = if (sessionActive) currentShareCode else null
            Log.d(TAG, "🎯 Calling goalService.updateGoalProgressForActivity")
            Log.d(TAG, "🎯   type: $type")
            Log.d(TAG, "🎯   sessionShareCode: $sessionShareCode")
            Log.d(TAG, "🎯   smokerName: ${selectedSmoker.name}")

            try {
                goalService.updateGoalProgressForActivity(
                    type,
                    sessionShareCode,
                    selectedSmoker.name
                )
                Log.d(TAG, "🎯 Goal update call completed")
            } catch (e: Exception) {
                Log.e(TAG, "🎯 ERROR calling goal service: ${e.message}", e)
            }
        } else {
            Log.e(TAG, "🎯 ERROR: goalService is not initialized!")
        }

        withContext(Dispatchers.Main) {
            updateUndoButtonVisibility()
        }

        Log.d(TAG, "🎯 === HANDLE POST HIT ACTIONS WITH PAYER END ===")
    }

    private suspend fun calculateIntervalForActivityType(activityType: ActivityType): Long {
        return withContext(Dispatchers.IO) {
            // Get activities from current room or local database
            val activities = latestRoomData?.safeActivities() ?: run {
                // Fallback to local database if no room data
                val logs = repo.getLogsInTimeRange(sessionStart, System.currentTimeMillis())
                logs.map { log ->
                    val smoker = smokers.find { it.smokerId == log.smokerId }
                    SessionActivity(
                        smokerId = smoker?.cloudUserId ?: "local_${log.smokerId}",
                        smokerName = smoker?.name ?: "Unknown",
                        type = log.type.name,
                        timestamp = log.timestamp
                    )
                }
            }

            // Filter activities for this type
            val typeActivities = activities.filter {
                it.type.equals(activityType.name, ignoreCase = true)
            }.sortedBy { it.timestamp }

            Log.d(TAG, "🤖 Found ${typeActivities.size} activities of type $activityType for interval calculation")

            if (typeActivities.size < 2) {
                Log.d(TAG, "🤖 Not enough data for auto-add (need at least 2 activities)")
                return@withContext 0L
            }

            // Calculate the gap between the last two activities
            val lastActivity = typeActivities.last()
            val secondLastActivity = typeActivities[typeActivities.size - 2]
            val lastGap = lastActivity.timestamp - secondLastActivity.timestamp

            Log.d(TAG, "🤖 Gap between last two $activityType activities: ${lastGap}ms (${lastGap/1000}s)")

            lastGap
        }
    }

    private fun refreshLocalSessionStatsIfNeeded() {
        Log.d(TAG, "🔍 === refreshLocalSessionStatsIfNeeded CALLED ===")
        Log.d(TAG, "🔍 Session active: $sessionActive")

        // IMPORTANT: Don't refresh stats if session is not active
        if (!sessionActive) {
            Log.d(TAG, "🔍 Skipping stats refresh - session not active")
            return
        }

        val isConnectedToCloud = currentShareCode != null && currentRoom != null && authManager.isSignedIn

        if (isConnectedToCloud) {
            Log.d(TAG, "🔍 Skipping local stats refresh - connected to cloud room")
            return
        }

        lifecycleScope.launch {
            val allSmokersFromDb = withContext(Dispatchers.IO) {
                repo.getAllSmokersList()
            }

            val now = System.currentTimeMillis()
            val perSmokerList = mutableListOf<PerSmokerStats>()
            var totalCones = 0
            var totalJoints = 0
            var totalBowls = 0

            // Track last smoker info for each activity type
            var lastConeSmokerName: String? = null
            var lastJointSmokerName: String? = null
            var lastBowlSmokerName: String? = null
            var lastConeTimestamp: Long = 0L
            var lastBowlTimestamp: Long = 0L
            var conesSinceLastBowl = 0

            // Track gaps - these will be calculated from ALL activities
            var lastGapMs: Long? = null  // The gap between the two most recent activities
            var previousGapMs: Long? = null  // The gap before that
            var longestConeGapMs: Long = 0L  // Longest gap between cones specifically
            var shortestConeGapMs: Long = Long.MAX_VALUE  // Shortest gap between cones specifically

            // Get ALL activities in session (not just cones)
            val allSessionActivities = withContext(Dispatchers.IO) {
                repo.getLogsInTimeRange(sessionStart, now)
            }.sortedBy { it.timestamp }

            Log.d(TAG, "🔍 Total activities in session: ${allSessionActivities.size}")

            // CRITICAL FIX: Calculate "last gap" from ALL activities, not just cones
            if (allSessionActivities.size >= 2) {
                // Sort activities by timestamp to ensure correct order
                val sortedActivities = allSessionActivities.sortedBy { it.timestamp }

                // Get the two most recent activities
                val lastActivity = sortedActivities[sortedActivities.size - 1]
                val secondLastActivity = sortedActivities[sortedActivities.size - 2]

                // Calculate the gap between them
                lastGapMs = lastActivity.timestamp - secondLastActivity.timestamp

                Log.d(TAG, "🔍 Last gap calculation (ALL activities):")
                Log.d(TAG, "🔍    Activity ${sortedActivities.size - 1}: ${lastActivity.type} at ${lastActivity.timestamp}")
                Log.d(TAG, "🔍    Activity ${sortedActivities.size - 2}: ${secondLastActivity.type} at ${secondLastActivity.timestamp}")
                Log.d(TAG, "🔍    Last gap: ${lastGapMs}ms = ${lastGapMs / 1000}s = ${lastGapMs / 60000}m")

                // If we have 3+ activities, calculate the previous gap for comparison
                if (sortedActivities.size >= 3) {
                    val thirdLastActivity = sortedActivities[sortedActivities.size - 3]
                    previousGapMs = secondLastActivity.timestamp - thirdLastActivity.timestamp

                    Log.d(TAG, "🔍    Activity ${sortedActivities.size - 3}: ${thirdLastActivity.type} at ${thirdLastActivity.timestamp}")
                    Log.d(TAG, "🔍    Previous gap: ${previousGapMs}ms = ${previousGapMs / 1000}s")

                    // Calculate the difference for logging
                    val difference = lastGapMs - previousGapMs
                    val changeText = when {
                        difference > 0 -> "${difference / 1000}s longer"
                        difference < 0 -> "${kotlin.math.abs(difference) / 1000}s shorter"
                        else -> "same"
                    }
                    Log.d(TAG, "🔍    Gap comparison: $changeText than previous")
                }
            } else {
                Log.d(TAG, "🔍 Not enough activities for gap calculation (need at least 2)")
            }

            // Calculate gaps between CONES specifically for longest/shortest cone stats
            val coneLogs = allSessionActivities.filter { it.type == ActivityType.CONE }.sortedBy { it.timestamp }
            Log.d(TAG, "🔍 Cone activities: ${coneLogs.size}")

            if (coneLogs.size >= 2) {
                val coneGaps = mutableListOf<Long>()
                for (i in 1 until coneLogs.size) {
                    val gap = coneLogs[i].timestamp - coneLogs[i - 1].timestamp
                    coneGaps.add(gap)

                    // Track longest and shortest cone gaps
                    if (gap > longestConeGapMs) longestConeGapMs = gap
                    if (gap < shortestConeGapMs) shortestConeGapMs = gap
                }

                Log.d(TAG, "🔍 Cone gaps: ${coneGaps.size} gaps")
                if (coneGaps.isNotEmpty()) {
                    Log.d(TAG, "🔍    Longest cone gap: ${longestConeGapMs / 1000}s")
                    Log.d(TAG, "🔍    Shortest cone gap: ${shortestConeGapMs / 1000}s")
                    Log.d(TAG, "🔍    Average cone gap: ${coneGaps.average() / 1000}s")
                }
            }

            // If no cone gaps found, set shortest to 0
            if (shortestConeGapMs == Long.MAX_VALUE) {
                shortestConeGapMs = 0L
            }

            // Find last cone and its smoker
            Log.d(TAG, "🔍🔴 DEBUG: Finding last activities for name display")
            Log.d(TAG, "🔍🔴   - coneLogs.size = ${coneLogs.size}")
            Log.d(TAG, "🔍🔴   - allSessionActivities.size = ${allSessionActivities.size}")
            
            val lastCone = coneLogs.lastOrNull()
            if (lastCone != null) {
                lastConeTimestamp = lastCone.timestamp
                val coneSmoker = withContext(Dispatchers.IO) {
                    repo.getSmokerById(lastCone.smokerId)
                }
                lastConeSmokerName = coneSmoker?.name
                Log.d(TAG, "🔍🟢 Found last CONE smoker: $lastConeSmokerName")
            } else {
                Log.d(TAG, "🔍🔴 No CONE found in session")
            }

            // Find last joint and its smoker
            val jointLogs = allSessionActivities.filter { it.type == ActivityType.JOINT }
            Log.d(TAG, "🔍🔴   - jointLogs.size = ${jointLogs.size}")
            val lastJoint = jointLogs.lastOrNull()
            if (lastJoint != null) {
                Log.d(TAG, "🔍🔴 DEBUG: lastJoint.smokerId = ${lastJoint.smokerId}")
                val jointSmoker = withContext(Dispatchers.IO) {
                    val smoker = repo.getSmokerById(lastJoint.smokerId)
                    Log.d(TAG, "🔍🔴 DEBUG: getSmokerById(${lastJoint.smokerId}) returned: ${smoker?.name} (id: ${smoker?.smokerId})")
                    
                    // If smoker not found, log all available smokers
                    if (smoker == null) {
                        val allSmokers = repo.getAllSmokersList()
                        Log.d(TAG, "🔍🔴 DEBUG: Available smokers in DB:")
                        allSmokers.forEach { s ->
                            Log.d(TAG, "🔍🔴 DEBUG:   - ${s.name} (id: ${s.smokerId})")
                        }
                    }
                    
                    smoker
                }
                lastJointSmokerName = jointSmoker?.name
                Log.d(TAG, "🔍🟢 Found last JOINT smoker: $lastJointSmokerName")
            } else {
                Log.d(TAG, "🔍🔴 No JOINT found in session")
            }

            // Find last bowl and count cones since
            val bowlLogs = allSessionActivities.filter { it.type == ActivityType.BOWL }
            Log.d(TAG, "🔍🔴   - bowlLogs.size = ${bowlLogs.size}")
            val lastBowl = bowlLogs.maxByOrNull { it.timestamp }

            if (lastBowl != null) {
                lastBowlTimestamp = lastBowl.timestamp
                Log.d(TAG, "🔍🔴 DEBUG: lastBowl.smokerId = ${lastBowl.smokerId}")
                val bowlSmoker = withContext(Dispatchers.IO) {
                    val smoker = repo.getSmokerById(lastBowl.smokerId)
                    Log.d(TAG, "🔍🔴 DEBUG: getSmokerById(${lastBowl.smokerId}) returned: ${smoker?.name} (id: ${smoker?.smokerId})")
                    smoker
                }
                lastBowlSmokerName = bowlSmoker?.name
                Log.d(TAG, "🔍🟢 Found last BOWL smoker: $lastBowlSmokerName")
                conesSinceLastBowl = allSessionActivities
                    .filter { it.type == ActivityType.CONE && it.timestamp > lastBowlTimestamp }
                    .size
            } else {
                conesSinceLastBowl = coneLogs.size
            }

            // Calculate per-smoker stats
            for (smoker in allSmokersFromDb) {
                val allLogs = withContext(Dispatchers.IO) {
                    repo.getLogsForSmoker(smoker.smokerId)
                }

                val sessionLogs = allLogs.filter { it.timestamp >= sessionStart && it.timestamp <= now }

                val cones = sessionLogs.count { it.type == ActivityType.CONE }
                val joints = sessionLogs.count { it.type == ActivityType.JOINT }
                val bowls = sessionLogs.count { it.type == ActivityType.BOWL }

                if (cones > 0 || joints > 0 || bowls > 0) {
                    totalCones += cones
                    totalJoints += joints
                    totalBowls += bowls

                    // Calculate gaps for each activity type
                    val coneGaps = calculateGapsForType(sessionLogs, ActivityType.CONE)
                    val jointGaps = calculateGapsForType(sessionLogs, ActivityType.JOINT)
                    val bowlGaps = calculateGapsForType(sessionLogs, ActivityType.BOWL)

                    val perSmokerStat = PerSmokerStats(
                        smokerName = smoker.name,
                        totalCones = cones,
                        totalJoints = joints,
                        totalBowls = bowls,
                        avgGapMs = coneGaps.avg,
                        longestGapMs = coneGaps.longest,
                        shortestGapMs = coneGaps.shortest,
                        avgJointGapMs = jointGaps.avg,
                        longestJointGapMs = jointGaps.longest,
                        shortestJointGapMs = jointGaps.shortest,
                        avgBowlGapMs = bowlGaps.avg,
                        longestBowlGapMs = bowlGaps.longest,
                        shortestBowlGapMs = bowlGaps.shortest
                    )

                    perSmokerList.add(perSmokerStat)
                }
            }

            val sinceLastConeMs = if (lastConeTimestamp > 0) {
                now - lastConeTimestamp
            } else {
                0L
            }
            
            // Calculate time since last joint
            val lastJointTimestamp = jointLogs.lastOrNull()?.timestamp ?: 0L
            val sinceLastJointMs = if (lastJointTimestamp > 0) {
                now - lastJointTimestamp
            } else {
                0L
            }
            
            // Calculate time since last bowl
            val sinceLastBowlMs = if (lastBowlTimestamp > 0) {
                now - lastBowlTimestamp
            } else {
                0L
            }

            Log.d(TAG, "🔍🔴 DEBUG: Creating GroupStats with:")
            Log.d(TAG, "🔍🔴   - lastConeSmokerName = $lastConeSmokerName")
            Log.d(TAG, "🔍🔴   - lastJointSmokerName = $lastJointSmokerName")
            Log.d(TAG, "🔍🔴   - lastBowlSmokerName = $lastBowlSmokerName")
            Log.d(TAG, "🔍🔴   - sinceLastConeMs = $sinceLastConeMs")
            Log.d(TAG, "🔍🔴   - sinceLastJointMs = $sinceLastJointMs")
            Log.d(TAG, "🔍🔴   - sinceLastBowlMs = $sinceLastBowlMs")
            
            val groupStats = GroupStats(
                totalCones = totalCones,
                totalJoints = totalJoints,
                totalBowls = totalBowls,
                longestGapMs = longestConeGapMs,  // This is specifically for cones
                shortestGapMs = shortestConeGapMs,  // This is specifically for cones
                sinceLastGapMs = sinceLastConeMs,
                sinceLastJointMs = sinceLastJointMs,
                sinceLastBowlMs = sinceLastBowlMs,
                totalRounds = actualRounds,
                hitsInCurrentRound = hitsThisRound,
                participantCount = perSmokerList.size,
                lastConeSmokerName = lastConeSmokerName,
                lastJointSmokerName = lastJointSmokerName,
                lastBowlSmokerName = lastBowlSmokerName,
                conesSinceLastBowl = conesSinceLastBowl,
                lastGapMs = lastGapMs,  // Gap between last two activities of ANY type
                previousGapMs = previousGapMs  // Gap before that
            )

            withContext(Dispatchers.Main) {
                Log.d(TAG, "🔍 === FINAL STATS SUMMARY ===")
                Log.d(TAG, "🔍 Total activities: ${allSessionActivities.size}")
                Log.d(TAG, "🔍🔴 Last smoker names - Cone: $lastConeSmokerName, Joint: $lastJointSmokerName, Bowl: $lastBowlSmokerName")
                Log.d(TAG, "🔍 Last gap (any type): ${lastGapMs?.let { "${it / 1000}s" } ?: "N/A"}")
                Log.d(TAG, "🔍 Previous gap (any type): ${previousGapMs?.let { "${it / 1000}s" } ?: "N/A"}")
                Log.d(TAG, "🔍 Longest cone gap: ${longestConeGapMs / 1000}s")
                Log.d(TAG, "🔍 Shortest cone gap: ${shortestConeGapMs / 1000}s")
                Log.d(TAG, "🔍 ============================")

                sessionStatsVM.applyLocalStats(
                    perSmokerList,
                    groupStats,
                    sessionStart,
                    lastConeSmokerName,
                    conesSinceLastBowl
                )
                Log.d(TAG, "🔍 ✅ Local stats applied to ViewModel")
            }
        }
    }


    // Replace the entire handlePostHitActions function in MainActivity.kt with this:

    private suspend fun handlePostHitActions(
        selectedSmoker: Smoker,
        selectedPosition: Int,
        type: ActivityType,
        now: Long
    ) {
        Log.d(TAG, "🎯 === HANDLE POST HIT ACTIONS START ===")
        Log.d(TAG, "🎯 Session active: $sessionActive")
        Log.d(TAG, "🎯 Session start time: $sessionStart")

        // Get stash attribution info BEFORE creating the activity log
        val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
        val stashSource = stashViewModel.stashSource.value ?: StashSource.MY_STASH
        val currentUserId = authManager.getCurrentUserId() ?: getAndroidDeviceId()

        // CRITICAL DEBUG LOGGING
        Log.d(TAG, "🎯 STASH SOURCE FROM VIEWMODEL: $stashSource")

        // Determine who's paying based on stash source
        val payerStashOwnerId = when (stashSource) {
            StashSource.MY_STASH -> {
                Log.d(TAG, "🎯 MY_STASH selected - setting payerStashOwnerId to null")
                null  // null means "my stash"
            }
            StashSource.THEIR_STASH -> {
                Log.d(TAG, "🎯 THEIR_STASH selected - setting payerStashOwnerId to 'their_stash'")
                "their_stash"  // THIS IS CRITICAL - must be exactly "their_stash"
            }
            StashSource.EACH_TO_OWN -> {
                if (selectedSmoker.cloudUserId == currentUserId ||
                    selectedSmoker.uid == currentUserId) {
                    Log.d(TAG, "🎯 EACH_TO_OWN - Current user, setting to null")
                    null  // Current user pays from their stash
                } else {
                    val otherId = "other_${selectedSmoker.smokerId}"
                    Log.d(TAG, "🎯 EACH_TO_OWN - Other user, setting to $otherId")
                    otherId  // Someone else's stash
                }
            }
        }

        // CRITICAL: Log what we're about to store
        Log.d(TAG, "🎯 FINAL payerStashOwnerId being stored: '$payerStashOwnerId'")

        // Get current ratios and stash for snapshot
        val currentRatios = stashViewModel.ratios.value
        val currentStash = stashViewModel.currentStash.value

        val gramsForActivity = when (type) {
            ActivityType.CONE -> currentRatios?.coneGrams ?: 0.3
            ActivityType.JOINT -> currentRatios?.jointGrams ?: 0.5
            ActivityType.BOWL -> currentRatios?.bowlGrams ?: 0.2
            else -> 0.0
        }

        val pricePerGram = currentStash?.pricePerGram ?: 15.0

        // CRITICAL FIX: Set session ID properly
        val currentSessionId = if (sessionActive && sessionStart > 0) {
            sessionStart  // Use sessionStart as the session ID
        } else {
            null
        }

        Log.d(TAG, "🎯 Creating activity with sessionId: $currentSessionId (sessionActive: $sessionActive, sessionStart: $sessionStart)")

        // Create the activity log object with session ID
        val activityLog = ActivityLog(
            id = 0L,
            smokerId = selectedSmoker.smokerId,
            consumerId = selectedSmoker.smokerId,
            payerStashOwnerId = payerStashOwnerId,  // THIS IS WHERE THE STASH ATTRIBUTION IS SET
            type = type,
            timestamp = now,
            sessionId = currentSessionId,
            sessionStartTime = if (sessionActive) sessionStart else null,
            gramsAtLog = gramsForActivity,
            pricePerGramAtLog = pricePerGram
        )

        // Store in local database (only for local sessions, not cloud)
        if (currentShareCode == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val insertedId = repo.insert(activityLog)
                Log.d(TAG, "🎯 Inserted activity ID: $insertedId with payerStashOwnerId: '$payerStashOwnerId'")

                // Verify it was stored correctly
                val verifyActivity = repo.getActivityById(insertedId)
                Log.d(TAG, "🎯 Verification - stored payerStashOwnerId: '${verifyActivity?.payerStashOwnerId}'")

                if (verifyActivity?.payerStashOwnerId != payerStashOwnerId) {
                    Log.e(TAG, "🎯 ERROR: payerStashOwnerId mismatch! Expected: '$payerStashOwnerId', Got: '${verifyActivity?.payerStashOwnerId}'")
                }
            }
        }

        // Rest of the function continues as normal...
        // Only update session-related data if session is active
        if (sessionActive) {
            activitiesTimestamps.add(now)
            activitiesTimestamps.sort()
            actualLastLogTime = activitiesTimestamps.maxOrNull() ?: now
            lastLogTime = now
            
            // Update specific activity type timestamps
            when (type) {
                ActivityType.CONE -> lastConeTimestamp = now
                ActivityType.JOINT -> lastJointTimestamp = now
                ActivityType.BOWL -> lastBowlTimestamp = now
                ActivityType.SESSION_SUMMARY -> { /* Session summaries don't update timestamps */ }
            }

            activityHistory.add(activityLog)
            if (activityHistory.size > 10) {
                activityHistory.removeAt(0)
            }

            val activitiesBeforeThis = activitiesTimestamps.filter { it < now }
            if (activitiesBeforeThis.isNotEmpty()) {
                val prevActivity = activitiesBeforeThis.last()
                val interval = now - prevActivity
                lastIntervalMillis = interval
                intervalsList.add(interval)
            } else {
                intervalsList.add(0L)
            }

            if (isAutoMode && currentShareCode == null) {
                hitsThisRound++
                val activeSmokerCount = getActiveSmokers().size
                if (activeSmokerCount > 0 && hitsThisRound >= activeSmokerCount) {
                    hitsThisRound = 0
                    actualRounds++
                    if (initialRoundsSet > 0) {
                        roundsLeft = kotlin.math.max(0, initialRoundsSet - actualRounds)
                    } else {
                        roundsLeft = 0
                    }
                    updateRoundsUI()
                }
            }

            if (notificationsEnabled) {
                val helper = NotificationHelper(this@MainActivity)
                val smokerCloudId = selectedSmoker.cloudUserId
                withContext(Dispatchers.Main) {
                    helper.showActivityNotification(
                        type,
                        lastTimestamp = now,
                        conesSinceLastBowl = null,
                        currentShareCode,
                        smokerCloudId,
                        justAdded = true,
                        addedAt = now,
                        lastSmokerName = selectedSmoker.name
                    )
                }
            }

            if (notificationsEnabled) {
                handler.postDelayed({
                    refreshNotificationsWithSession()
                }, 500)
            }

            if (isAutoMode && smokers.isNotEmpty()) {
                lastHitCameFromUI = true
                withContext(Dispatchers.Main) {
                    moveToNextActiveSmoker()
                }
            }

            sessionStatsVM.refreshTimer()
        }

        // STASH TRACKING
        if (stashViewModel.currentStash.value != null) {
            val smokerUid = if (selectedSmoker.isCloudSmoker && !selectedSmoker.cloudUserId.isNullOrEmpty()) {
                selectedSmoker.cloudUserId
            } else {
                "local_${selectedSmoker.uid}"
            }
            stashViewModel.recordConsumption(
                activityType = type,
                smokerUid = smokerUid!!,
                smokerName = selectedSmoker.name,
                timestamp = now
            )
            stashViewModel.onActivityLogged(type)
        }

        // GOAL TRACKING
        val sessionShareCode = if (sessionActive) currentShareCode else null
        goalService.updateGoalProgressForActivity(
            type,
            sessionShareCode,
            selectedSmoker.name
        )

        withContext(Dispatchers.Main) {
            updateUndoButtonVisibility()
        }

        Log.d(TAG, "🎯 === HANDLE POST HIT ACTIONS END ===")
    }


    private fun getAndroidDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }

    private fun logHitSafe(type: ActivityType) {
        Log.d(TAG, "🎯 logHitSafe called - type: $type")
        Log.d(TAG, "🎯 Session state - active: $sessionActive, start: $sessionStart")
        Log.d(TAG, "🎯 Current smokers count: ${smokers.size}")

        if (smokers.isEmpty()) {
            Log.d(TAG, "🎯 No smokers exist - showing add smoker dialog")
            addSmokerDialog.show()
            return
        }

        // Check if there are any cloud smokers
        val hasCloudSmokers = smokers.any { it.isCloudSmoker }
        Log.d(TAG, "🎯 Has cloud smokers: $hasCloudSmokers")

        if (!sessionActive) {
            Log.w(TAG, "🎯 WARNING: Activity logged without active session!")

            // Store the pending activity type
            pendingActivityType = type

            // If no cloud smokers, show the new popup directly
            if (!hasCloudSmokers) {
                Log.d(TAG, "🎯 No cloud smokers - showing no cloud user popup")
                showNoCloudUserPopup()
            } else {
                Log.d(TAG, "🎯 Showing no active session popup for type: $type")
                showNoActiveSessionPopupForType(type)
            }
            return
        }

        val now = System.currentTimeMillis()
        synchronized(hitLoggingLock) {
            if (isLoggingHit || now - lastHitTime < MIN_HIT_INTERVAL_MS) return
            isLoggingHit = true
            lastHitTime = now
        }

        val selectedPosition = binding.spinnerSmoker.selectedItemPosition
        val organizedSmokers = organizeSmokers().flatMap { it.smokers }
        val capturedSmoker = organizedSmokers.getOrNull(selectedPosition)

        if (capturedSmoker == null) {
            synchronized(hitLoggingLock) { isLoggingHit = false }
            Toast.makeText(this, "Please select a valid smoker!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
                val currentStash = stashViewModel.currentStash.value
                val ratios = stashViewModel.ratios.value

                if (currentStash != null && ratios != null) {
                    val requiredGrams = when (type) {
                        ActivityType.CONE -> ratios.coneGrams
                        ActivityType.JOINT -> ratios.jointGrams
                        ActivityType.BOWL -> ratios.bowlGrams
                        else -> 0.0
                    }

                    val currentSource = stashViewModel.stashSource.value ?: StashSource.MY_STASH
                    val currentUserId = authManager.getCurrentUserId() ?: getAndroidDeviceId()

                    var switchedToTheirStash = false

                    when (currentSource) {
                        StashSource.MY_STASH -> {
                            if (currentStash.currentGrams < requiredGrams) {
                                stashViewModel.updateStashSource(StashSource.THEIR_STASH)
                                switchedToTheirStash = true
                                Log.d(TAG, "🎯 Auto-switched to Their Stash due to insufficient My Stash")
                            }
                        }
                        StashSource.EACH_TO_OWN -> {
                            val isCurrentUser = (capturedSmoker.isCloudSmoker && capturedSmoker.cloudUserId == currentUserId) ||
                                    (!capturedSmoker.isCloudSmoker && capturedSmoker.uid == currentUserId)

                            if (isCurrentUser && currentStash.currentGrams < requiredGrams) {
                                stashViewModel.updateStashSource(StashSource.THEIR_STASH)
                                switchedToTheirStash = true
                                Log.d(TAG, "🎯 Auto-switched to Their Stash for current user in Each-to-Own mode")
                            }
                        }
                        StashSource.THEIR_STASH -> {
                            Log.d(TAG, "🎯 Already on Their Stash, no switch needed")
                        }
                    }

                    if (switchedToTheirStash) {
                        withContext(Dispatchers.Main) {
                            supportFragmentManager.fragments
                                .filterIsInstance<StashFragment>()
                                .firstOrNull()?.let { fragment ->
                                    fragment.setAttributionRadioSilently(StashSource.THEIR_STASH)
                                }
                        }
                        delay(100)
                    }
                }

                val finalStashSource = stashViewModel.stashSource.value ?: StashSource.MY_STASH
                Log.d(TAG, "🎯 CRITICAL: Final stash source before logging: $finalStashSource")

                if (type == ActivityType.CONE && sessionActive) {
                    val sessionActivities = withContext(Dispatchers.IO) {
                        repo.getLogsInTimeRange(sessionStart, System.currentTimeMillis())
                    }

                    val hasBowl = sessionActivities.any { it.type == ActivityType.BOWL }
                    val hasCone = sessionActivities.any { it.type == ActivityType.CONE }

                    if (!hasBowl && !hasCone) {
                        withContext(Dispatchers.Main) {
                            showThemedConfirmationDialog(capturedSmoker, finalStashSource, now)
                        }
                        return@launch
                    }
                }

                proceedWithLogHitWithSourceAndSmoker(type, now, finalStashSource, capturedSmoker)
            } catch (e: Exception) {
                Log.e(TAG, "Error in logHitSafe", e)
            } finally {
                synchronized(hitLoggingLock) { isLoggingHit = false }
            }
        }
    }
    
    // ADD this new function to handle the captured smoker
    private suspend fun proceedWithLogHitWithSourceAndSmoker(
        type: ActivityType,
        timestamp: Long,
        stashSource: StashSource,
        capturedSmoker: Smoker
    ) {
        Log.d(TAG, "🎯 proceedWithLogHitWithSourceAndSmoker: type=$type, source=$stashSource, smoker=${capturedSmoker.name}")

        val currentUserId = authManager.getCurrentUserId() ?: getAndroidDeviceId()

        // CRITICAL: Determine payerStashOwnerId based on stash source
        val payerStashOwnerId = when (stashSource) {
            StashSource.MY_STASH -> {
                Log.d(TAG, "🎯 Setting payerStashOwnerId to null (MY_STASH)")
                null
            }
            StashSource.THEIR_STASH -> {
                Log.d(TAG, "🎯 Setting payerStashOwnerId to 'their_stash' (THEIR_STASH)")
                "their_stash"
            }
            StashSource.EACH_TO_OWN -> {
                if (capturedSmoker.cloudUserId == currentUserId || capturedSmoker.uid == currentUserId) {
                    Log.d(TAG, "🎯 Setting payerStashOwnerId to null (EACH_TO_OWN - current user)")
                    null
                } else {
                    val otherId = "other_${capturedSmoker.smokerId}"
                    Log.d(TAG, "🎯 Setting payerStashOwnerId to '$otherId' (EACH_TO_OWN - other user)")
                    otherId
                }
            }
        }

        // Check if password verification is needed
        if (capturedSmoker.isCloudSmoker &&
            capturedSmoker.passwordHash != null &&
            !capturedSmoker.isPasswordVerified) {

            withContext(Dispatchers.Main) {
                passwordDialog.showVerifyPasswordDialog(
                    smokerName = capturedSmoker.name,
                    onPasswordEntered = { password ->
                        verifyPasswordAndLogHitWithPayerAndSmoker(capturedSmoker, type, timestamp, password, payerStashOwnerId)
                    }
                )
            }
        } else {
            // No password needed or already verified - use captured smoker
            logHitWithPayerAndSmoker(type, timestamp, payerStashOwnerId, capturedSmoker)
        }
    }

    // ADD this new function
    private fun verifyPasswordAndLogHitWithPayerAndSmoker(
        smoker: Smoker,
        type: ActivityType,
        timestamp: Long,
        password: String,
        payerStashOwnerId: String?
    ) {
        lifecycleScope.launch {
            val isValid = smoker.passwordHash
                ?.let { PasswordUtils.verifyPassword(password, it) }
                ?: false

            if (isValid) {
                val verified = smoker.copy(isPasswordVerified = true)
                withContext(Dispatchers.IO) {
                    repo.updateSmoker(verified)
                }

                val prefs = getSharedPreferences("smoker_passwords", Context.MODE_PRIVATE)
                prefs.edit().putString(smoker.cloudUserId ?: smoker.smokerId.toString(), password).apply()

                logHitWithPayerAndSmoker(type, timestamp, payerStashOwnerId, verified)
                Toast.makeText(this@MainActivity, "Password verified for ${smoker.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Incorrect password for ${smoker.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ADD this new function that uses the captured smoker
    private suspend fun logHitWithPayerAndSmoker(
        type: ActivityType,
        now: Long,
        payerStashOwnerId: String?,
        capturedSmoker: Smoker
    ) {
        Log.d(TAG, "🎯 === logHitWithPayerAndSmoker START ===")
        Log.d(TAG, "🎯 Type: $type, Time: $now, PayerStashOwnerId: '$payerStashOwnerId', Smoker: ${capturedSmoker.name}")

        if (!sessionActive) {
            Log.w(TAG, "🎯 Cannot log hit - session not active")
            return
        }

        val adjustedNow = now - rewindOffset
        val stashViewModel = ViewModelProvider(this).get(StashViewModel::class.java)
        val currentStash = stashViewModel.currentStash.value
        val ratios = stashViewModel.ratios.value

        // Create the activity log with the CAPTURED smoker
        val activityLog = ActivityLog(
            id = 0L,
            smokerId = capturedSmoker.smokerId,
            consumerId = capturedSmoker.smokerId,
            payerStashOwnerId = payerStashOwnerId,
            type = type,
            timestamp = adjustedNow,
            sessionId = if (sessionActive) sessionStart else null,
            sessionStartTime = if (sessionActive) sessionStart else null,
            gramsAtLog = when (type) {
                ActivityType.CONE -> ratios?.coneGrams ?: 0.3
                ActivityType.JOINT -> ratios?.jointGrams ?: 0.5
                ActivityType.BOWL -> ratios?.bowlGrams ?: 0.2
                else -> 0.0
            },
            pricePerGramAtLog = currentStash?.pricePerGram ?: 15.0
        )

        // ALWAYS store in local database first
        val insertedId = withContext(Dispatchers.IO) {
            val id = repo.insert(activityLog)
            Log.d(TAG, "🎯 INSERTED activity ID $id for smoker ${capturedSmoker.name}")
            id
        }

        // THEN handle cloud sync if in a cloud session
        if (currentShareCode != null) {
            val smokerActivityUid = if (capturedSmoker.isCloudSmoker) {
                capturedSmoker.cloudUserId!!
            } else {
                "local_${capturedSmoker.uid}"
            }

            sessionSyncService.addActivityToRoom(
                shareCode = currentShareCode!!,
                smokerUid = smokerActivityUid,
                smokerName = capturedSmoker.name,
                activityType = type,
                timestamp = adjustedNow,
                deviceId = getAndroidDeviceId()
            ).fold(
                onSuccess = {
                    Log.d(TAG, "🎯 Activity also synced to cloud room for ${capturedSmoker.name}")
                    lastHitCameFromUI = true
                    handler.postDelayed({
                        lastHitCameFromUI = false
                    }, 500)
                },
                onFailure = { error ->
                    Log.e(TAG, "🎯 Failed to sync to room: ${error.message}")
                }
            )
        } else {
            // Local session - just refresh stats
            refreshLocalSessionStatsIfNeeded()
        }

        // Get the current spinner position BEFORE any changes
        val currentSpinnerPosition = binding.spinnerSmoker.selectedItemPosition

        // Handle post-hit actions with the CAPTURED smoker and position
        handlePostHitActionsWithPayerAndSmoker(capturedSmoker, currentSpinnerPosition, type, adjustedNow, payerStashOwnerId)

        Log.d(TAG, "🎯 === logHitWithPayerAndSmoker END ===")
    }

    // ADD this modified version that uses the captured smoker
    private suspend fun handlePostHitActionsWithPayerAndSmoker(
        capturedSmoker: Smoker,
        capturedPosition: Int,
        type: ActivityType,
        now: Long,
        payerStashOwnerId: String?
    ) {
        Log.d(TAG, "🎯 === HANDLE POST HIT ACTIONS WITH CAPTURED SMOKER START ===")
        Log.d(TAG, "🎯 Captured Smoker: ${capturedSmoker.name}")
        Log.d(TAG, "🎯 PayerStashOwnerId: '$payerStashOwnerId'")
        Log.d(TAG, "🎯 Auto mode: $isAutoMode")
        Log.d(TAG, "🎯 Activity type: $type")

        // Only update session-related data if session is active
        if (sessionActive) {
            activitiesTimestamps.add(now)
            activitiesTimestamps.sort()
            actualLastLogTime = activitiesTimestamps.maxOrNull() ?: now
            lastLogTime = now
            
            // Update specific activity type timestamps
            when (type) {
                ActivityType.CONE -> lastConeTimestamp = now
                ActivityType.JOINT -> lastJointTimestamp = now
                ActivityType.BOWL -> lastBowlTimestamp = now
                ActivityType.SESSION_SUMMARY -> { /* Session summaries don't update timestamps */ }
            }

            // Create activity log for history tracking using CAPTURED smoker
            val activityLog = ActivityLog(
                id = 0L,
                smokerId = capturedSmoker.smokerId,
                consumerId = capturedSmoker.smokerId,
                payerStashOwnerId = payerStashOwnerId,
                type = type,
                timestamp = now,
                sessionId = if (sessionActive) sessionStart else null,
                sessionStartTime = if (sessionActive) sessionStart else null,
                gramsAtLog = 0.0, // These will be set by stash tracking
                pricePerGramAtLog = 0.0
            )

            activityHistory.add(activityLog)
            if (activityHistory.size > 10) {
                activityHistory.removeAt(0)
            }

            val activitiesBeforeThis = activitiesTimestamps.filter { it < now }
            if (activitiesBeforeThis.isNotEmpty()) {
                val prevActivity = activitiesBeforeThis.last()
                val interval = now - prevActivity
                lastIntervalMillis = interval
                intervalsList.add(interval)
            } else {
                intervalsList.add(0L)
            }

            // Handle rounds counter for local sessions - EXCLUDE BOWLS
            if (isAutoMode && currentShareCode == null && initialRoundsSet > 0 && type != ActivityType.BOWL) {
                val smokerUid = if (capturedSmoker.isCloudSmoker && !capturedSmoker.cloudUserId.isNullOrEmpty()) {
                    capturedSmoker.cloudUserId
                } else {
                    "local_${capturedSmoker.uid}"
                }

                if (!smokersTakenTurnSinceCounterChange.contains(smokerUid)) {
                    smokersTakenTurnSinceCounterChange.add(smokerUid)
                    Log.d(TAG, "🔄 Local: Smoker ${capturedSmoker.name} has taken their turn")
                }

                val activeSmokerCount = getActiveSmokers().size
                if (activeSmokerCount > 0 && smokersTakenTurnSinceCounterChange.size >= activeSmokerCount) {
                    roundsLeft = kotlin.math.max(0, roundsLeft - 1)
                    smokersTakenTurnSinceCounterChange.clear()
                    Log.d(TAG, "🔄 Local: All smokers have taken a turn, decremented counter to: $roundsLeft")

                    if (roundsLeft == 0 && initialRoundsSet > 0) {
                        initialRoundsSet = 0
                        Log.d(TAG, "🔄 Local: Counter reached 0, switching to infinity mode")
                    }

                    updateRoundsUI()
                }
            }

            // Handle session rounds - EXCLUDE BOWLS
            if (isAutoMode && currentShareCode == null && type != ActivityType.BOWL) {
                hitsThisRound++
                val activeSmokerCount = getActiveSmokers().size
                if (activeSmokerCount > 0 && hitsThisRound >= activeSmokerCount) {
                    hitsThisRound = 0
                    actualRounds++
                    updateRoundsUI()
                }
            }

            if (notificationsEnabled) {
                val helper = NotificationHelper(this@MainActivity)
                val smokerCloudId = capturedSmoker.cloudUserId
                withContext(Dispatchers.Main) {
                    helper.showActivityNotification(
                        type,
                        lastTimestamp = now,
                        conesSinceLastBowl = null,
                        currentShareCode,
                        smokerCloudId,
                        justAdded = true,
                        addedAt = now,
                        lastSmokerName = capturedSmoker.name
                    )
                }
            }

            if (notificationsEnabled) {
                handler.postDelayed({
                    refreshNotificationsWithSession()
                }, 500)
            }

            // CRITICAL: Only advance smoker for NON-BOWL activities
            if (isAutoMode && smokers.isNotEmpty() && currentShareCode == null && type != ActivityType.BOWL) {
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "🎯 Advancing to next smoker after ${capturedSmoker.name} (local session, type: $type)")
                    moveToNextActiveSmoker()
                }
            } else if (isAutoMode && currentShareCode != null && type != ActivityType.BOWL) {
                Log.d(TAG, "🎯 NOT advancing smoker (will be handled by room sync)")
            } else {
                Log.d(TAG, "🎯 NOT advancing smoker (autoMode: $isAutoMode, type: $type, isBowl: ${type == ActivityType.BOWL})")
            }

            sessionStatsVM.refreshTimer()
        }

        // STASH TRACKING using CAPTURED smoker
        val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
        if (stashViewModel.currentStash.value != null) {
            val smokerUid = if (capturedSmoker.isCloudSmoker && !capturedSmoker.cloudUserId.isNullOrEmpty()) {
                capturedSmoker.cloudUserId
            } else {
                "local_${capturedSmoker.uid}"
            }
            stashViewModel.recordConsumption(
                activityType = type,
                smokerUid = smokerUid!!,
                smokerName = capturedSmoker.name,
                timestamp = now
            )
            stashViewModel.onActivityLogged(type)
        }

        // GOAL TRACKING using CAPTURED smoker
        Log.d(TAG, "🎯 ABOUT TO UPDATE GOALS for ${capturedSmoker.name}")
        if (::goalService.isInitialized) {
            val sessionShareCode = if (sessionActive) currentShareCode else null
            try {
                goalService.updateGoalProgressForActivity(
                    type,
                    sessionShareCode,
                    capturedSmoker.name
                )
                Log.d(TAG, "🎯 Goal update completed for ${capturedSmoker.name}")
            } catch (e: Exception) {
                Log.e(TAG, "🎯 ERROR calling goal service: ${e.message}", e)
            }
        }

        withContext(Dispatchers.Main) {
            updateUndoButtonVisibility()
        }

        Log.d(TAG, "🎯 === HANDLE POST HIT ACTIONS WITH CAPTURED SMOKER END ===")
    }

    private suspend fun proceedWithLogHit(type: ActivityType, timestamp: Long) {
        val selectedPosition = binding.spinnerSmoker.selectedItemPosition
        val organizedSmokers = organizeSmokers().flatMap { it.smokers }

        if (selectedPosition < 0 || selectedPosition >= organizedSmokers.size) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Please select a valid smoker!", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val selectedSmoker = organizedSmokers[selectedPosition]

        // Check if password verification is needed
        if (selectedSmoker.isCloudSmoker &&
            selectedSmoker.passwordHash != null &&
            !selectedSmoker.isPasswordVerified) {

            withContext(Dispatchers.Main) {
                passwordDialog.showVerifyPasswordDialog(
                    smokerName = selectedSmoker.name,
                    onPasswordEntered = { password ->
                        verifyPasswordAndLogHit(selectedSmoker, type, timestamp, password)
                    }
                )
            }
        } else {
            // No password needed or already verified
            logHit(type, timestamp)
        }
    }

    private fun verifyPasswordAndLogHit(
        smoker: Smoker,
        type: ActivityType,
        timestamp: Long,
        password: String
    ) {
        lifecycleScope.launch {
            val isValid = smoker.passwordHash
                ?.let { PasswordUtils.verifyPassword(password, it) }
                ?: false

            if (isValid) {
                val verified = smoker.copy(isPasswordVerified = true)
                withContext(Dispatchers.IO) {
                    repo.updateSmoker(verified)
                }

                // Store the password for future use
                val prefs = getSharedPreferences("smoker_passwords", Context.MODE_PRIVATE)
                prefs.edit().putString(smoker.cloudUserId ?: smoker.smokerId.toString(), password).apply()

                logHit(type, timestamp)
                Toast.makeText(this@MainActivity, "Password verified for ${smoker.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Incorrect password for ${smoker.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun debugSyncStatus() {
        Log.d(TAG, "🔍 === SYNC DEBUG START ===")
        Log.d(TAG, "🔍 Session active: $sessionActive")
        Log.d(TAG, "🔍 Current share code: $currentShareCode")

        val currentUserId = authManager.getCurrentUserId()
        Log.d(TAG, "🔍 Current user ID: $currentUserId")

        Log.d(TAG, "🔍 Total smokers: ${smokers.size}")
        smokers.forEach { smoker ->
            val coneCount = withContext(Dispatchers.IO) {
                repo.countConesForSmoker(smoker.smokerId)
            }
            Log.d(TAG, "🔍   ${smoker.name}: $coneCount cones (cloud: ${smoker.isCloudSmoker}, ID: ${smoker.cloudUserId})")
        }

        val cloudSmokers = smokers.filter { it.isCloudSmoker && it.cloudUserId != null }
        Log.d(TAG, "🔍 Cloud smokers: ${cloudSmokers.size}")
        cloudSmokers.forEach { smoker ->
            Log.d(TAG, "🔍   → ${smoker.name} (${smoker.cloudUserId})")
        }

        Log.d(TAG, "🔍 === SYNC DEBUG END ===")
    }

    private fun debugLocalSmokers() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allSmokers = repo.getAllSmokersList()
            Log.d(TAG, "🐛 === LOCAL SMOKERS DEBUG ===")
            Log.d(TAG, "🐛 Total smokers in database: ${allSmokers.size}")

            val localSmokers = allSmokers.filter { !it.isCloudSmoker }
            Log.d(TAG, "🐛 Local smokers: ${localSmokers.size}")

            localSmokers.forEach { smoker ->
                Log.d(TAG, "🐛   ID: ${smoker.smokerId}, Name: '${smoker.name}', Cloud: ${smoker.isCloudSmoker}")
            }

            // Check for duplicate names
            val nameGroups = localSmokers.groupBy { it.name }
            nameGroups.forEach { (name, smokersWithName) ->
                if (smokersWithName.size > 1) {
                    Log.w(TAG, "🐛 DUPLICATE NAME FOUND: '$name' appears ${smokersWithName.size} times")
                    smokersWithName.forEach { smoker ->
                        Log.w(TAG, "🐛     ID: ${smoker.smokerId}, Created: ${smoker.lastSyncTime}")
                    }
                }
            }
            Log.d(TAG, "🐛 === END DEBUG ===")
        }
    }

    private fun cleanupDuplicateLocalSmokers() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🧹 Starting duplicate smoker cleanup")

                val allSmokers = repo.getAllSmokersList()
                val localSmokers = allSmokers.filter { !it.isCloudSmoker }

                // Group by name to find duplicates
                val smokersByName = localSmokers.groupBy { it.name.trim() }

                smokersByName.forEach { (name, smokersWithName) ->
                    if (smokersWithName.size > 1) {
                        Log.w(TAG, "🧹 Found ${smokersWithName.size} smokers with name '$name'")

                        // Keep the oldest one (lowest ID) and merge activities
                        val keepSmoker = smokersWithName.minByOrNull { it.smokerId }!!
                        val duplicates = smokersWithName.filter { it.smokerId != keepSmoker.smokerId }

                        Log.d(TAG, "🧹 Keeping smoker ID ${keepSmoker.smokerId}, removing ${duplicates.size} duplicates")

                        // Move all activities from duplicates to the keeper
                        duplicates.forEach { duplicate ->
                            val activities = repo.getLogsForSmoker(duplicate.smokerId)
                            Log.d(TAG, "🧹 Moving ${activities.size} activities from ${duplicate.smokerId} to ${keepSmoker.smokerId}")

                            activities.forEach { activity ->
                                val newActivity = activity.copy(
                                    id = 0L, // New ID will be assigned
                                    smokerId = keepSmoker.smokerId
                                )

                                // Check if this exact activity already exists for the keeper
                                val existingActivities = repo.getLogsForSmoker(keepSmoker.smokerId)
                                val alreadyExists = existingActivities.any {
                                    it.type == activity.type && it.timestamp == activity.timestamp
                                }

                                if (!alreadyExists) {
                                    repo.insert(newActivity)
                                    Log.d(TAG, "🧹 Moved activity: ${activity.type} @ ${activity.timestamp}")
                                } else {
                                    Log.d(TAG, "🧹 Skipped duplicate activity: ${activity.type} @ ${activity.timestamp}")
                                }
                            }

                            // Delete the duplicate smoker and their original activities
                            repo.deleteSmoker(duplicate)
                            Log.d(TAG, "🧹 Deleted duplicate smoker: ${duplicate.name} (ID: ${duplicate.smokerId})")
                        }
                    }
                }

                Log.d(TAG, "🧹 Duplicate cleanup completed")

            } catch (e: Exception) {
                Log.e(TAG, "🧹 Error during duplicate cleanup: ${e.message}", e)
            }
        }
    }

    private fun syncCloudSmoker(smoker: Smoker) {
        lifecycleScope.launch {
            val currentUserId = authManager.getCurrentUserId()

            if (smoker.cloudUserId != null && smoker.cloudUserId == currentUserId) {
                // This is the current user - fetch their latest name from cloud
                cloudSyncService.getCloudSmokerProfile(currentUserId).fold(
                    onSuccess = { cloudProfile ->
                        if (cloudProfile != null) {
                            val nameChanged = cloudProfile.name != smoker.name

                            if (nameChanged) {
                                // Update local database
                                val updatedSmoker = smoker.copy(
                                    name = cloudProfile.name,
                                    lastSyncTime = System.currentTimeMillis()
                                )

                                withContext(Dispatchers.IO) {
                                    repo.updateSmoker(updatedSmoker)
                                }

                                // Update in Firestore cloud profile
                                cloudSyncService.updateCloudSmokerName(currentUserId, cloudProfile.name)

                                // If in a room, update the shared smoker name
                                currentShareCode?.let { shareCode ->
                                    sessionSyncService.updateSharedSmokerInRoom(
                                        shareCode = shareCode,
                                        smokerUid = smoker.cloudUserId,
                                        updatedName = cloudProfile.name
                                    )
                                }

                                withContext(Dispatchers.Main) {

                                    Toast.makeText(
                                        this@MainActivity,
                                        "Name updated to: ${cloudProfile.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Name is already up to date",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    },
                    onFailure = { error ->
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "Failed to sync: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "You can only update your own name",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun isStashTrackingActive(): Boolean {
        return stashIntegration != null && stashViewModel.currentStash.value != null
    }

    private fun getStashStatus(): String? {
        val stashData = stashViewModel.currentStash.value ?: return null
        val source = stashViewModel.stashSource.value ?: StashSource.MY_STASH

        return when (source) {
            StashSource.MY_STASH -> "Using my stash (${String.format("%.2f", stashData.currentGrams)}g)"
            StashSource.THEIR_STASH -> "Using their stash"
            StashSource.EACH_TO_OWN -> "Each using own stash"
        }
    }

    private fun promptStashSignIn() {
        AlertDialog.Builder(this)
            .setTitle("Sign In for Stash Tracking")
            .setMessage("To track stash consumption, please sign in on the Stash tab.")
            .setPositiveButton("Go to Stash") { _, _ ->
                binding.viewPager.currentItem = 4 // Switch to Stash tab
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Optional: Add this to show stash consumption in notifications
    private fun getStashConsumptionText(type: ActivityType): String? {
        val stashData = stashViewModel.currentStash.value ?: return null
        val gramsPerBowl = stashData.gramsPerBowl ?: return null

        val grams = when (type) {
            ActivityType.CONE -> gramsPerBowl / (stashData.conesPerBowl ?: 6.0)
            ActivityType.JOINT -> gramsPerBowl * 1.5
            ActivityType.BOWL -> gramsPerBowl
            else -> 0.0
        }

        val cost = grams * (stashData.pricePerGram ?: 10.0)
        return String.format("%.2fg ($%.2f)", grams, cost)
    }

    private fun showEditSmokerDialog(smoker: Smoker) {
        val editText = EditText(this).apply {
            setText(smoker.name)
            selectAll()
            hint = "Enter smoker name"
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Smoker Name")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString().trim()
                when {
                    newName.isEmpty() ->
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    newName != smoker.name ->
                        updateSmokerName(smoker, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateSmokerName(smoker: Smoker, newName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val updated = smoker.copy(name = newName)
            repo.updateSmoker(updated)

            if (smoker.isCloudSmoker && smoker.cloudUserId != null) {
                // Update in Firestore cloud profile
                cloudSyncService.updateCloudSmokerName(smoker.cloudUserId, newName).fold(
                    onSuccess = {
                        Log.d(TAG, "Cloud profile name updated to: $newName")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to update cloud profile name: ${error.message}")
                    }
                )

                // Mark for sync
                repo.markSmokerForSync(smoker.smokerId)

                // If in a room, update shared smoker
                currentShareCode?.let { shareCode ->
                    sessionSyncService.updateSharedSmokerInRoom(
                        shareCode = shareCode,
                        smokerUid = smoker.cloudUserId,
                        updatedName = newName
                    )
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Name updated and synced!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // Handle local smoker rename in room
                currentShareCode?.let { shareCode ->
                    // For local smokers, the room ID is "local_" + uid
                    val roomSmokerId = "local_${smoker.uid}"

                    Log.d(TAG, "Updating local smoker in room: $roomSmokerId -> $newName")

                    sessionSyncService.updateSharedSmokerInRoom(
                        shareCode = shareCode,
                        smokerUid = roomSmokerId,
                        updatedName = newName
                    ).fold(
                        onSuccess = {
                            Log.d(TAG, "Local smoker name updated in room: $newName")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to update local smoker in room: ${error.message}")
                        }
                    )
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Name updated!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showChangePasswordDialog(smoker: Smoker) {
        if (!smoker.isOwner) {
            Toast.makeText(this, "Only the owner can change the password", Toast.LENGTH_SHORT).show()
            return
        }
        passwordDialog.showChangePasswordDialog(
            smokerName = smoker.name,
            onPasswordChanged = { newPass ->
                updateSmokerPassword(smoker, newPass)
            }
        )
    }

    private fun updateSmokerPassword(smoker: Smoker, newPassword: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val newHash = newPassword?.let { PasswordUtils.hashPassword(it) }
            val updated = smoker.copy(passwordHash = newHash)
            repo.updateSmoker(updated)

            if (smoker.isCloudSmoker && smoker.cloudUserId != null) {
                cloudSyncService.updateCloudSmokerPassword(smoker.cloudUserId, newHash).fold(
                    onSuccess = {
                        withContext(Dispatchers.Main) {
                            val msg = if (newPassword != null) "Password updated" else "Password removed"
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { err ->
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "Cloud update failed: ${err.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            } else {
                withContext(Dispatchers.Main) {
                    val msg = if (newPassword != null) "Password updated" else "Password removed"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun undoLastActivity() {
        Log.d(TAG, "🔙 === UNDO START ===")
        Log.d(TAG, "🔙 Activity history size: ${activityHistory.size}")
        Log.d(TAG, "🔙 Retroactive activities size: ${retroactiveActivities.size}")
        Log.d(TAG, "🔙 Current share code: $currentShareCode")

        // Check if we should undo bulk retroactive activities
        if (retroactiveActivities.isNotEmpty()) {
            // Undo all retroactive activities from the last bulk add
            undoBulkRetroactiveActivities()
            return
        }

        if (activityHistory.isEmpty()) {
            Toast.makeText(this, "No recent activity to undo", Toast.LENGTH_SHORT).show()
            return
        }

        val lastActivity = activityHistory.removeLastOrNull()
        if (lastActivity == null) {
            Toast.makeText(this, "No activity to undo", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "🔙 Undoing: ${lastActivity.type} for smoker ${lastActivity.smokerId} at ${lastActivity.timestamp}")
        Log.d(TAG, "🔙 PayerStashOwnerId: '${lastActivity.payerStashOwnerId}'")
        Log.d(TAG, "🔙 gramsAtLog: ${lastActivity.gramsAtLog}, pricePerGramAtLog: ${lastActivity.pricePerGramAtLog}")
        Log.d(TAG, "🔙 Activities remaining: ${activityHistory.size}")

        // Store the current smoker before undo
        val currentSmokerId = binding.spinnerSmoker.selectedItemPosition.let { pos ->
            smokerAdapterNew.getItem(pos)?.smokerId
        }
        Log.d(TAG, "🔙 Current smoker before undo: $currentSmokerId")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get the smoker for this activity
                val smoker = repo.getSmokerById(lastActivity.smokerId)
                if (smoker == null) {
                    Log.e(TAG, "🔙 Smoker not found for undo operation")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error: Smoker not found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                Log.d(TAG, "🔙 Found smoker: ${smoker.name}")

                // === CRITICAL ADDITION: REVERSE GOAL PROGRESS ===
                // This must happen BEFORE deleting the activity from the database
                if (::goalService.isInitialized) {
                    Log.d(TAG, "🔙🎯 Reversing goal progress for ${smoker.name} - ${lastActivity.type}")
                    val sessionShareCode = if (sessionActive) currentShareCode else null

                    try {
                        goalService.reverseGoalProgressForActivity(
                            activityType = lastActivity.type,
                            sessionShareCode = sessionShareCode,
                            smokerName = smoker.name
                        )
                        Log.d(TAG, "🔙🎯 Goal progress reversed successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "🔙🎯 Error reversing goal progress: ${e.message}", e)
                    }
                } else {
                    Log.w(TAG, "🔙🎯 GoalService not initialized, skipping goal reversal")
                }
                // === END GOAL PROGRESS REVERSAL ===

                // Handle stash reversal (existing code)
                val shouldUndoStash = ::stashViewModel.isInitialized && stashViewModel.currentStash.value != null

                if (shouldUndoStash) {
                    Log.d(TAG, "🔙 Calling StashViewModel.undoStashConsumption for potential stash reversal")
                    Log.d(TAG, "🔙   PayerStashOwnerId: '${lastActivity.payerStashOwnerId}'")
                    Log.d(TAG, "🔙   Type: ${lastActivity.type}")
                    Log.d(TAG, "🔙   Grams at log: ${lastActivity.gramsAtLog}")
                    Log.d(TAG, "🔙   Price per gram at log: ${lastActivity.pricePerGramAtLog}")

                    withContext(Dispatchers.Main) {
                        stashViewModel.undoStashConsumption(lastActivity, smoker.name)
                        Log.d(TAG, "🔙 StashViewModel.undoStashConsumption called")
                    }
                } else {
                    Log.d(TAG, "🔙 Stash tracking not active, skipping stash undo")
                }

                // Delete from local database
                repo.delete(lastActivity)
                Log.d(TAG, "🔙 Deleted from local database")

                // Remove from cloud room if in shared session
                if (!currentShareCode.isNullOrEmpty()) {
                    try {
                        // Get smoker UID for cloud removal
                        val smokerUid = if (smoker.isCloudSmoker && !smoker.cloudUserId.isNullOrEmpty()) {
                            smoker.cloudUserId
                        } else {
                            "local_${smoker.uid}"
                        }

                        val removeResult = sessionSyncService.removeActivityFromRoom(
                            shareCode = currentShareCode!!,
                            smokerUid = smokerUid,
                            activityType = lastActivity.type,
                            timestamp = lastActivity.timestamp
                        )

                        if (removeResult.isSuccess) {
                            Log.d(TAG, "🔙 Successfully removed activity from cloud room")

                            // Force an immediate room data refresh
                            val roomData = sessionSyncService.getRoomData(currentShareCode!!)
                            if (roomData != null) {
                                withContext(Dispatchers.Main) {
                                    handleRoomUpdate(roomData)
                                    onRoomUpdated(roomData)
                                    sessionStatsVM.applyRoomStats(roomData.safeCurrentStats(), roomData.startTime)
                                }
                            }
                        } else {
                            Log.e(TAG, "🔙 Failed to remove activity from cloud room")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "🔙 Error removing activity from cloud", e)
                    }
                } else {
                    // For local sessions, immediately update the session stats
                    withContext(Dispatchers.Main) {
                        sessionStatsVM.decrementActivityCount(smoker.name, lastActivity.type)
                    }
                }

                // Count remaining activities in session
                val remainingActivities = withContext(Dispatchers.IO) {
                    repo.getLogsInTimeRange(sessionStart, System.currentTimeMillis())
                }
                Log.d(TAG, "🔙 Remaining activities in session: ${remainingActivities.size}")

                // Update intervals list
                if (intervalsList.isNotEmpty()) {
                    intervalsList.removeLastOrNull()
                }

                // Update session timing
                if (remainingActivities.isNotEmpty()) {
                    val lastRemainingActivity = remainingActivities.maxByOrNull { it.timestamp }
                    lastRemainingActivity?.let {
                        lastLogTime = it.timestamp
                    }
                } else {
                    lastLogTime = sessionStart
                }

                // Recalculate last interval
                if (remainingActivities.size >= 2) {
                    val sorted = remainingActivities.sortedBy { it.timestamp }
                    lastIntervalMillis = sorted.last().timestamp - sorted[sorted.size - 2].timestamp
                } else {
                    lastIntervalMillis = 0L
                }

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "🔙 Updating UI...")

                    // Update the history view model
                    val historyFragment = supportFragmentManager.fragments
                        .find { it is HistoryFragment } as? HistoryFragment
                    historyFragment?.refreshHistory()

                    // === REFRESH GOAL FRAGMENT ===
                    val goalFragment = supportFragmentManager.fragments
                        .find { it is GoalFragment } as? GoalFragment
                    goalFragment?.let {
                        Log.d(TAG, "🔙🎯 Triggering goal fragment refresh")
                        // The fragment will automatically refresh via the LiveData observer
                    }

                    // If in auto mode, go back to the previous smoker
                    if (isAutoMode && activityHistory.isNotEmpty()) {
                        val previousActivity = activityHistory.last()
                        val previousSmoker = repo.getSmokerById(previousActivity.smokerId)
                        if (previousSmoker != null) {
                            val sections = organizeSmokers()
                            val organizedSmokers = sections.flatMap { it.smokers }
                            val previousIndex = organizedSmokers.indexOfFirst { it.smokerId == previousSmoker.smokerId }
                            if (previousIndex >= 0) {
                                Log.d(TAG, "🔙 Rolling back to previous smoker: ${previousSmoker.name}")
                                binding.spinnerSmoker.setSelection(previousIndex)
                                selectSmoker(previousSmoker)
                            }
                        }
                    }

                    // Force refresh stats based on session type
                    if (currentShareCode == null) {
                        refreshLocalSessionStatsIfNeeded()
                    }

                    // Force refresh of all ViewModels
                    val app = application as CloudCounterApplication
                    statsVM.setSmoker(app.defaultSmokerId)

                    // Force refresh stash stats
                    if (::stashViewModel.isInitialized) {
                        stashViewModel.forceStatsRefresh()
                    }

                    // Refresh graph
                    val graphFragment = supportFragmentManager.fragments
                        .find { it is GraphFragment } as? GraphFragment
                    graphFragment?.refreshGraph()

                    // Refresh notifications
                    refreshNotificationsWithSession()

                    updateUndoButtonVisibility()
                    Log.d(TAG, "🔙 Undo button visible: ${binding.btnUndoLastActivity.visibility == View.VISIBLE}")
                    Log.d(TAG, "🔙 === UNDO COMPLETE ===")

                    Toast.makeText(this@MainActivity, "Activity removed, stash restored, and goals updated", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "🔙 Error undoing activity", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error undoing activity", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Undo all retroactive activities from the last bulk add
    private fun undoBulkRetroactiveActivities() {
        Log.d(TAG, "🔙 === BULK UNDO START ===")
        Log.d(TAG, "🔙 Undoing ${retroactiveActivities.size} retroactive activities")
        
        if (retroactiveActivities.isEmpty()) {
            return
        }
        
        val timestampsToUndo = retroactiveActivities.toList()
        retroactiveActivities.clear()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Delete all activities from database
                timestampsToUndo.forEach { timestamp ->
                    Log.d(TAG, "🔙 Deleting retroactive activity at timestamp: $timestamp")
                    
                    // Find activities at this timestamp for this session
                    val activities = repo.getActivitiesBySessionId(sessionStart)
                    activities.filter { activity ->
                        Math.abs(activity.timestamp - timestamp) < 100 // Within 100ms 
                    }.forEach { activity ->
                        Log.d(TAG, "🔙 Found and deleting activity: ${activity.type} by ${activity.smokerId}")
                        repo.delete(activity)
                        
                        // Remove from activity history and timestamps
                        activityHistory.removeAll { it.id == activity.id }
                        activitiesTimestamps.remove(activity.timestamp)
                    }
                }
                
                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    // Force refresh all stats
                    refreshLocalSessionStatsIfNeeded()
                    
                    // Refresh fragments
                    sessionStatsVM.recalculateGaps()
                    val historyFragment = supportFragmentManager.findFragmentByTag("history") as? HistoryFragment
                    historyFragment?.refreshHistory()
                    val graphFragment = supportFragmentManager.findFragmentByTag("graph") as? GraphFragment
                    graphFragment?.refreshGraph()
                    
                    // Update undo button visibility
                    updateUndoButtonVisibility()
                    
                    Toast.makeText(
                        this@MainActivity, 
                        "Undid ${timestampsToUndo.size} retroactive activities", 
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    Log.d(TAG, "🔙 === BULK UNDO COMPLETE ===")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "🔙 Error undoing bulk activities", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error undoing activities", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateUndoButtonVisibility() {
        val shouldShow = sessionActive && (activityHistory.isNotEmpty() || retroactiveActivities.isNotEmpty())
        binding.btnUndoLastActivity.visibility = if (shouldShow) View.VISIBLE else View.GONE
        Log.d(TAG, "Undo button visibility: ${if (shouldShow) "VISIBLE" else "GONE"}, history size: ${activityHistory.size}, retroactive size: ${retroactiveActivities.size}")
    }

    // Helper data class for gap statistics
    private data class GapStats(
        val avg: Long = 0L,
        val longest: Long = 0L,
        val shortest: Long = 0L
    )

    // Helper function to calculate gaps for a specific activity type
    private fun calculateGapsForType(logs: List<ActivityLog>, type: ActivityType): GapStats {
        val typeLogs = logs.filter { it.type == type }.sortedBy { it.timestamp }

        if (typeLogs.size < 2) {
            return GapStats()
        }

        val gaps = mutableListOf<Long>()
        for (i in 1 until typeLogs.size) {
            gaps.add(typeLogs[i].timestamp - typeLogs[i - 1].timestamp)
        }

        return if (gaps.isNotEmpty()) {
            GapStats(
                avg = gaps.average().toLong(),
                longest = gaps.maxOrNull() ?: 0L,
                shortest = gaps.minOrNull() ?: 0L
            )
        } else {
            GapStats()
        }
    }

    private fun setupSessionControls() {
        // Set initial state - collapsed by default
        timersVisible = false
        binding.btnToggleTimers.text = "Advanced"
        binding.timerContainer.visibility = View.GONE
        binding.roundsContainer.visibility = View.GONE
        binding.layoutConeAutoControls.visibility = View.GONE
        binding.layoutJointAutoControls.visibility = View.GONE
        binding.layoutBowlAutoControls.visibility = View.GONE

        // Load notification preference
        notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        updateNotificationButtonState()

        binding.btnStartSesh.setOnClickListener {
            if (sessionActive) {
                Toast.makeText(this, "Session already in progress", Toast.LENGTH_SHORT).show()
            } else {
                confettiHelper.showCelebrationBurst(binding.btnStartSesh)
                showCloudSessionOptions()
            }
        }

        binding.btnEndSesh.setOnClickListener {
            confettiHelper.showCelebrationBurst(binding.btnEndSesh)
            endSession()
        }

        binding.btnToggleTimers.setOnClickListener {
            toggleTimersVisibility()
        }

        binding.btnUndoLastActivity.setOnClickListener {
            confettiHelper.showMiniConfettiFromButton(binding.btnUndoLastActivity)
            undoLastActivity()
        }

        // Add notification toggle button listener
        binding.btnNotificationToggle.setOnClickListener {
            toggleNotifications()
        }

        binding.btnRoundPlus.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastRoundButtonClickTime < ROUND_BUTTON_DEBOUNCE_MS) {
                return@setOnClickListener
            }
            lastRoundButtonClickTime = now

            confettiHelper.showMiniConfettiFromButton(binding.btnRoundPlus)

            isUpdatingRoundsLocally = true
            localRoundsUpdateTime = System.currentTimeMillis()

            // Simple increment logic
            if (initialRoundsSet == 0) {
                // From infinity to 1
                initialRoundsSet = 1
                roundsLeft = 1
            } else {
                // Increment by 1
                initialRoundsSet++
                roundsLeft = initialRoundsSet
            }

            // Reset the turn tracking
            smokersTakenTurnSinceCounterChange.clear()
            processedActivityIds.clear() // Clear processed activities
            lastCounterChangeTime = System.currentTimeMillis()

            updateRoundsUI()
            updateRoundsCounterInRoom()

            Log.d(TAG, "🔄 Increased rounds counter to: $initialRoundsSet")
        }

        binding.btnRoundMinus.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastRoundButtonClickTime < ROUND_BUTTON_DEBOUNCE_MS) {
                return@setOnClickListener
            }
            lastRoundButtonClickTime = now

            confettiHelper.showMiniConfettiFromButton(binding.btnRoundMinus)

            isUpdatingRoundsLocally = true
            localRoundsUpdateTime = System.currentTimeMillis()

            if (initialRoundsSet > 1) {
                // Decrement by 1
                initialRoundsSet--
                roundsLeft = initialRoundsSet
            } else if (initialRoundsSet == 1) {
                // Go to infinity mode
                initialRoundsSet = 0
                roundsLeft = 0
            }

            // Reset the turn tracking
            smokersTakenTurnSinceCounterChange.clear()
            processedActivityIds.clear() // Clear processed activities
            lastCounterChangeTime = System.currentTimeMillis()

            updateRoundsUI()
            updateRoundsCounterInRoom()

            Log.d(TAG, "🔄 Decreased rounds counter to: $initialRoundsSet")
        }

        binding.radioAuto.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                confettiHelper.showMiniConfettiFromButton(buttonView)
                isAutoMode = true
                sessionStatsVM.setAutoMode(true)
                Log.d(TAG, "🔘 Auto mode enabled")
            }
        }

        binding.radioSticky.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                confettiHelper.showMiniConfettiFromButton(buttonView)
                isAutoMode = false
                sessionStatsVM.setAutoMode(false)
                Log.d(TAG, "🔘 Sticky mode enabled")
            }
        }
    }


    private fun setupRewindButton() {
        binding.btnRewind.setOnClickListener {
            if (!sessionActive) {
                Toast.makeText(this, "No active session to rewind", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val realNow = System.currentTimeMillis()
            val currentElapsed = realNow - sessionStart - rewindOffset

            // Check if we can rewind further
            if (currentElapsed < REWIND_AMOUNT_MS) {
                Toast.makeText(this, "Cannot rewind past session start", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ADD CONFETTI HERE
            confettiHelper.showMiniConfettiFromButton(binding.btnRewind)

            // Store state before first rewind
            if (rewindOffset == 0L) {
                lastLogTimeBeforeRewind = actualLastLogTime
            }

            // Apply rewind
            rewindOffset += REWIND_AMOUNT_MS

            Log.d(TAG, "⏪ REWIND DEBUG:")
            Log.d(TAG, "⏪   Rewind offset: ${rewindOffset}ms")
            Log.d(TAG, "⏪   Session start: $sessionStart")
            Log.d(TAG, "⏪   Actual last log: $actualLastLogTime")
            Log.d(TAG, "⏪   Real now: $realNow")
            Log.d(TAG, "⏪   Rewinded now: ${realNow - rewindOffset}")

            val rewindedNow = realNow - rewindOffset
            if (actualLastLogTime > 0) {
                if (rewindedNow < actualLastLogTime) {
                    Log.d(TAG, "⏪   We've rewound BEFORE the last activity")
                } else {
                    Log.d(TAG, "⏪   We're still AFTER the last activity")
                }
            }

            // Update all timers
            updateTimersForRewind()

            // Show feedback
            val totalRewoundSeconds = rewindOffset / 1000
            Toast.makeText(this, "Rewound ${totalRewoundSeconds}s total", Toast.LENGTH_SHORT).show()

            // Update session stats
            sessionStatsVM.applyRewindOffset(rewindOffset)

            // Update auto-add timers
            if (::autoAddManager.isInitialized) {
                autoAddManager.applyRewindOffset(rewindOffset)
            }

            // Force immediate timer update
            handler.removeCallbacks(timerRunnable)
            handler.post(timerRunnable)
        }
    }


    private fun removeDuplicateCloudSmokers() {
        lifecycleScope.launch(Dispatchers.IO) {
            val currentUserId = authManager.getCurrentUserId() ?: return@launch

            // Get all smokers with the same cloud user ID
            val allSmokers = repo.getAllSmokersList()
            val duplicates = allSmokers.filter { it.cloudUserId == currentUserId }

            if (duplicates.size > 1) {
                Log.d(TAG, "Found ${duplicates.size} duplicate smokers for user $currentUserId")

                // Keep the oldest one (lowest ID) with activities, or just the oldest
                val smokerWithCounts = duplicates.map { smoker ->
                    val activityCount = repo.getLogsForSmoker(smoker.smokerId).size
                    Triple(smoker, activityCount, smoker.smokerId)
                }.sortedBy { it.third } // Sort by ID (oldest first)

                val keepSmoker = smokerWithCounts.firstOrNull { it.second > 0 }?.first
                    ?: smokerWithCounts.first().first

                // Delete all others
                duplicates.filter { it.smokerId != keepSmoker.smokerId }.forEach { duplicate ->
                    Log.d(TAG, "Deleting duplicate smoker: ${duplicate.name} (ID: ${duplicate.smokerId})")
                    repo.deleteSmoker(duplicate)
                }
            }
        }
    }

    private suspend fun getLastTimestampForType(type: ActivityType): Long? {
        latestRoomData?.let { room ->
            val activitiesOfType = room.safeActivities()
                .filter { it.type.equals(type.name, ignoreCase = true) }
            val last = activitiesOfType.maxByOrNull { it.timestamp }
            if (last != null) return last.timestamp
        }
        return repo.getLastLogByType(type)?.timestamp
    }

    private suspend fun getConesSinceLastBowlForTimestamp(lastConeTs: Long?): Int? {
        if (lastConeTs == null) return null

        latestRoomData?.let { room ->
            val lastBowl = room.safeActivities()
                .filter { it.type.equals(ActivityType.BOWL.name, ignoreCase = true) && it.timestamp < lastConeTs }
                .maxByOrNull { it.timestamp }
            if (lastBowl != null) {
                val cones = room.safeActivities()
                    .filter {
                        it.type.equals(ActivityType.CONE.name, ignoreCase = true)
                                && it.timestamp in (lastBowl.timestamp + 1) until lastConeTs
                    }
                return cones.size
            }
        }

        // fallback to local repo logic
        return repo.getLastBowlBefore(lastConeTs)?.let { bowl ->
            repo.countConesBetweenTimestamps(bowl.timestamp, lastConeTs)
        }
    }


    private fun formatInterval(sec: Long): String {
        val hours = sec / 3600
        val minutes = (sec % 3600) / 60
        val seconds = sec % 60
        return when {
            hours > 0 -> "${hours}h ${minutes.toString().padStart(2, '0')}m ${seconds.toString().padStart(2, '0')}s"
            minutes > 0 -> "${minutes}m ${seconds.toString().padStart(2, '0')}s"
            else -> "${seconds}s"
        }
    }



    private fun initializeTimerSoundAndAutoAdd() {
        // Initialize timer sound helper
        timerSoundHelper = TimerSoundHelper(this)

        // Initialize auto-add manager with time calculation function
        autoAddManager = AutoAddManager(
            coroutineScope = lifecycleScope,
            onAutoAdd = { activityType ->
                // This will be called when a timer reaches zero
                handleAutoAdd(activityType)
            },
            onTimerUpdate = { activityType, remainingMs ->
                // This will be called every second to update countdown displays
                updateAutoAddTimerDisplay(activityType, remainingMs)
            },
            getTimeSinceLastActivity = { activityType ->
                // Provide actual time since last activity
                val realNow = System.currentTimeMillis()
                val rewindedNow = realNow - rewindOffset

                // Find the last activity of this type
                val lastActivity = activitiesTimestamps.lastOrNull() ?: sessionStart
                val timeSince = rewindedNow - lastActivity

                Log.d(TAG, "🤖⏱️ GET_TIME_SINCE: $activityType = ${timeSince}ms (lastActivity: $lastActivity)")
                timeSince
            }
        )
    }



     fun showCloudSignInDialog() {
        Log.d(TAG, "🔐 showCloudSignInDialog called")

        // Check network first
        if (!authManager.isNetworkAvailable()) {
            AlertDialog.Builder(this)
                .setTitle("No Internet Connection")
                .setMessage("Google Sign-In requires an internet connection. Would you like to start a local session instead?")
                .setPositiveButton("Local Session") { _, _ ->
                    startLocalSession()
                }
                .setNegativeButton("Retry") { _, _ ->
                    showCloudSignInDialog()
                }
                .setNeutralButton("Cancel", null)
                .show()
            return
        }

        // Create custom dialog with fade animation
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        currentDialog = dialog

        val dialogView = createThemedSignInDialog()
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
            setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }

        dialog.setOnDismissListener {
            currentDialog = null
            Log.d(TAG, "🔐 Sign-in dialog dismissed")
        }

        // Set initial alpha to 0 for fade-in
        dialogView.alpha = 0f

        dialog.show()

        // Apply fade-in animation with 2-second duration
        performManualFadeIn(dialogView, 2000L)  // Already 2000L, keeping it consistent
    }



    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, "🔐 Sign-in result received: resultCode=${result.resultCode}")
            Log.d(TAG, "🔐 Sign-in data: ${result.data}")
            Log.d("WELCOME_DEBUG", "🌟 Google sign-in result received")

            lifecycleScope.launch {
                authManager.handleSignInResult(result).fold(
                    onSuccess = { user ->
                        Log.d(TAG, "🔐 Sign-in successful: userId=${user.uid}")
                        Log.d("WELCOME_DEBUG", "✅ Sign-in successful for user: ${user.uid}")
                        val userId = user.uid
                        val googleName = user.displayName

                        Log.d(TAG, "=== SIGN IN DEBUG ===")
                        Log.d(TAG, "User ID: $userId")
                        Log.d(TAG, "Google Account Name: $googleName")

                        // First, clean up any duplicates
                        removeDuplicateCloudSmokers()

                        // Check for existing smoker in database (after cleanup)
                        val existingSmoker = withContext(Dispatchers.IO) {
                            repo.getSmokerByCloudUserId(userId)
                        }

                        Log.d(TAG, "Existing smoker in DB: ${existingSmoker?.name} (ID: ${existingSmoker?.smokerId})")

                        // Check cloud profile
                        val cloudProfile = cloudSyncService.getCloudSmokerProfile(userId).getOrNull()
                        Log.d(TAG, "Cloud profile: ${cloudProfile?.name}, has password: ${cloudProfile?.passwordHash != null}")

                        if (existingSmoker != null) {
                            // We already have this smoker locally
                            Log.d(TAG, "Using existing smoker: ${existingSmoker.name}")
                            Log.d("WELCOME_DEBUG", "📋 Found existing smoker: ${existingSmoker.name}, isCloud: ${existingSmoker.isCloudSmoker}")

                            // Update to ensure password verification is correct
                            val updated = existingSmoker.copy(
                                passwordHash = cloudProfile?.passwordHash,
                                isPasswordVerified = true  // Always true when signing in with Google
                            )
                            withContext(Dispatchers.IO) {
                                repo.updateSmoker(updated)
                            }

                            // Select this smoker
                            withContext(Dispatchers.Main) {
                                val sections = organizeSmokers()
                                val organizedSmokers = sections.flatMap { it.smokers }
                                val smokerIndex = organizedSmokers.indexOfFirst { it.smokerId == existingSmoker.smokerId }
                                if (smokerIndex >= 0) {
                                    binding.spinnerSmoker.setSelection(smokerIndex)
                                    selectSmoker(updated) // Use the updated smoker
                                }

                                Toast.makeText(
                                    this@MainActivity,
                                    "Signed in as ${existingSmoker.name}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                
                                // Check if this is the first cloud smoker and show welcome
                                Log.d("WELCOME_DEBUG", "🔍 Checking for first cloud smoker after existing smoker sign-in")
                                checkAndShowWelcomeForFirstCloudSmoker()
                            }

                        } else if (cloudProfile != null) {
                            // No local smoker but cloud profile exists
                            Log.d(TAG, "Creating local smoker from cloud profile: ${cloudProfile.name}")
                            Log.d("WELCOME_DEBUG", "🆕 Creating new smoker from cloud profile: ${cloudProfile.name}")

                            // When signing in with Google, we trust the user owns this account
                            // So we mark it as verified even if it has a password
                            val newSmoker = Smoker(
                                smokerId = 0,
                                cloudUserId = userId,
                                name = cloudProfile.name,
                                isCloudSmoker = true,
                                shareCode = cloudProfile.shareCode,
                                passwordHash = cloudProfile.passwordHash,
                                isPasswordVerified = true,  // Always true when signing in with Google
                                isOwner = true,
                                lastSyncTime = System.currentTimeMillis(),
                                uid = java.util.UUID.randomUUID().toString()
                            )

                            val newSmokerId = withContext(Dispatchers.IO) {
                                repo.insertSmoker(newSmoker)
                            }

                            Log.d(TAG, "Created smoker with ID: $newSmokerId, isPasswordVerified: true")

                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Signed in as ${cloudProfile.name}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                
                                // Check if this is the first cloud smoker and show welcome
                                Log.d("WELCOME_DEBUG", "🔍 Checking for first cloud smoker after creating from cloud profile")
                                checkAndShowWelcomeForFirstCloudSmoker()
                            }

                            // Don't show the dialog - they're already authenticated via Google

                        } else {
                            // No existing smoker and no cloud profile - show dialog to create new
                            withContext(Dispatchers.Main) {
                                addSmokerDialog.onGoogleSignInComplete()
                            }
                        }

                        Log.d(TAG, "=== END SIGN IN DEBUG ===")

                        // Notify ChatFragment
                        withContext(Dispatchers.Main) {
                            val chatFragment = supportFragmentManager.fragments
                                .find { it is ChatFragment } as? ChatFragment
                            chatFragment?.onAuthStateChanged()
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "🔐 Sign in failed: ${error.message}", error)
                        Log.e(TAG, "🔐 Error class: ${error.javaClass.simpleName}")
                        Log.e(TAG, "🔐 Stack trace:", error)

                        // Check if it's a network error
                        val isNetworkError = error.message?.contains("network", ignoreCase = true) == true ||
                                error.message?.contains("connection", ignoreCase = true) == true ||
                                error.message?.contains("offline", ignoreCase = true) == true ||
                                !authManager.isNetworkAvailable()

                        if (isNetworkError) {
                            // Show the offline popup instead of just a toast
                            showOfflineCloudSessionDialog()
                        } else {
                            // Show regular error toast for other failures
                            Toast.makeText(
                                this@MainActivity,
                                "Sign in failed: ${error.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }


    private fun setupTimerSoundButton() {
        // Update button icon based on current state
        updateTimerSoundButtonIcon()

        binding.btnTimerSound.setOnClickListener {
            val currentlyEnabled = timerSoundHelper.isSoundEnabled()
            timerSoundHelper.setSoundEnabled(!currentlyEnabled)
            updateTimerSoundButtonIcon()

            val message = if (!currentlyEnabled) "Timer sound enabled" else "Timer sound disabled"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // Long press to open sound picker
        binding.btnTimerSound.setOnLongClickListener {
            openSoundPicker()
            true
        }
    }

    private fun updateTimerSoundButtonIcon() {
        val iconRes = if (timerSoundHelper.isSoundEnabled()) {
            android.R.drawable.ic_lock_silent_mode_off // Speaker icon
        } else {
            android.R.drawable.ic_lock_silent_mode // Muted speaker icon
        }
        binding.btnTimerSound.setImageResource(iconRes)
    }

    private fun openSoundPicker() {
        try {
            val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                putExtra(Settings.EXTRA_CHANNEL_ID, TimerSoundHelper.CHANNEL_ID)
            }
            soundPickerLauncher.launch(intent)
        } catch (e: Exception) {
            // Fallback to general sound settings
            val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
            soundPickerLauncher.launch(intent)
        }
    }

    private suspend fun getLastSmokerNameForType(type: ActivityType): String? {
        // Check room data first
        latestRoomData?.let { room ->
            val lastActivity = room.safeActivities()
                .filter { it.type.equals(type.name, ignoreCase = true) }
                .maxByOrNull { it.timestamp }

            if (lastActivity != null) {
                return lastActivity.smokerName
            }
        }

        // Fallback to local database
        val lastLog = repo.getLastLogByType(type)
        return lastLog?.let { log ->
            repo.getSmokerById(log.smokerId)?.name
        }
    }

    private fun calculateGapsFromRoomActivities(activities: List<SessionActivity>): Pair<Long?, Long?> {
        Log.d(TAG, "🔍 Calculating gaps from ${activities.size} room activities")

        // Sort all activities by timestamp
        val sortedActivities = activities.sortedBy { it.timestamp }

        if (sortedActivities.size < 2) {
            Log.d(TAG, "🔍 Not enough activities for gap calculation")
            return Pair(null, null)
        }

        // Calculate last gap (between two most recent activities of ANY type)
        val lastActivity = sortedActivities.last()
        val secondLastActivity = sortedActivities[sortedActivities.size - 2]
        val lastGap = lastActivity.timestamp - secondLastActivity.timestamp

        Log.d(TAG, "🔍 Last gap: ${lastActivity.type} - ${secondLastActivity.type} = ${lastGap}ms")

        // Calculate previous gap if we have 3+ activities
        val previousGap = if (sortedActivities.size >= 3) {
            val thirdLastActivity = sortedActivities[sortedActivities.size - 3]
            val gap = secondLastActivity.timestamp - thirdLastActivity.timestamp
            Log.d(TAG, "🔍 Previous gap: ${secondLastActivity.type} - ${thirdLastActivity.type} = ${gap}ms")
            gap
        } else {
            null
        }

        return Pair(lastGap, previousGap)
    }

    private fun setupAutoAddControls() {
        // Setup checkbox listeners with confetti
        binding.checkboxConeAuto.setOnCheckedChangeListener { buttonView, isChecked ->
            // ADD CONFETTI when checked
            if (isChecked) {
                confettiHelper.showMiniConfettiFromButton(buttonView)
            }
            handleAutoAddToggle(ActivityType.CONE, isChecked)
        }

        binding.checkboxJointAuto.setOnCheckedChangeListener { buttonView, isChecked ->
            // ADD CONFETTI when checked
            if (isChecked) {
                confettiHelper.showMiniConfettiFromButton(buttonView)
            }
            handleAutoAddToggle(ActivityType.JOINT, isChecked)
        }

        binding.checkboxBowlAuto.setOnCheckedChangeListener { buttonView, isChecked ->
            // ADD CONFETTI when checked
            if (isChecked) {
                confettiHelper.showMiniConfettiFromButton(buttonView)
            }
            handleAutoAddToggle(ActivityType.BOWL, isChecked)
        }
    }

    private fun handleAutoAddToggle(activityType: ActivityType, enabled: Boolean) {
        if (!sessionActive) {
            Log.w(TAG, "Cannot toggle auto-add when session is not active")
            return
        }

        Log.d(TAG, "🤖🔀 AUTO_ADD_TOGGLE: $activityType, enabled: $enabled")

        if (!enabled) {
            // Disabling auto-add
            Log.d(TAG, "🤖🔀 DISABLING: $activityType")
            autoAddManager.disableAutoAdd(activityType)
            updateAutoAddTimerVisibility(activityType, false)

            // For cloud sessions, update the cloud state
            currentShareCode?.let { shareCode ->
                lifecycleScope.launch {
                    sessionSyncService.updateAutoAddState(shareCode, activityType, false).fold(
                        onSuccess = {
                            Log.d(TAG, "🤖☁️ CLOUD_DISABLED: $activityType")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "🤖☁️ CLOUD_DISABLE_FAILED: ${error.message}")
                            runOnUiThread {
                                getCheckboxForActivityType(activityType)?.isChecked = true
                                Toast.makeText(this@MainActivity, "Failed to sync auto-add setting", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
            return
        }

        // Enabling auto-add - need to calculate interval and determine phase
        lifecycleScope.launch {
            val realNow = System.currentTimeMillis()
            val rewindedNow = realNow - rewindOffset

            // Get the last activity time for this type
            val lastActivityTime = getLastActivityTimeForType(activityType)
            val timeSinceLastActivity = if (lastActivityTime > 0) {
                rewindedNow - lastActivityTime
            } else {
                0L
            }

            Log.d(TAG, "🤖🔀 ENABLE_CHECK: $activityType")
            Log.d(TAG, "🤖🔀   lastActivityTime: $lastActivityTime")
            Log.d(TAG, "🤖🔀   timeSinceLastActivity: ${timeSinceLastActivity}ms")

            val interval = calculateIntervalForActivityType(activityType)

            if (interval <= 0) {
                Log.d(TAG, "🤖🔀 NO_DATA: Not enough data for $activityType")
                runOnUiThread {
                    getCheckboxForActivityType(activityType)?.isChecked = false
                    Toast.makeText(this@MainActivity, "Need at least 2 activities for auto-add", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            Log.d(TAG, "🤖🔀 INTERVAL_CALCULATED: ${interval}ms")

            // Determine which phase we're in
            val isOverdue = timeSinceLastActivity > interval

            if (isOverdue) {
                Log.d(TAG, "🤖🚀 PHASE_2_DETECTED: Overdue - will countdown from ${timeSinceLastActivity}ms")
            } else {
                val remaining = interval - timeSinceLastActivity
                Log.d(TAG, "🤖⏳ PHASE_1_DETECTED: Standard - ${remaining}ms remaining")
            }

            // For cloud sessions
            currentShareCode?.let { shareCode ->
                sessionSyncService.updateAutoAddState(shareCode, activityType, true).fold(
                    onSuccess = {
                        Log.d(TAG, "🤖☁️ CLOUD_ENABLED: $activityType")
                        // Cloud will handle the state update via room listener
                    },
                    onFailure = { error ->
                        Log.e(TAG, "🤖☁️ CLOUD_ENABLE_FAILED: ${error.message}")
                        runOnUiThread {
                            getCheckboxForActivityType(activityType)?.isChecked = false
                            Toast.makeText(this@MainActivity, "Failed to sync auto-add setting", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } ?: run {
                // Local session - FIXED: Only call enableAutoAddWithPhaseDetection, NOT updateAutoAddState
                Log.d(TAG, "🤖🏠 LOCAL_ENABLE: $activityType")

                // This single call sets everything up properly with the interval BEFORE starting the timer
                autoAddManager.enableAutoAddWithPhaseDetection(
                    activityType = activityType,
                    interval = interval,
                    timeSinceLastActivity = timeSinceLastActivity,
                    lastActivityTime = lastActivityTime
                )

                // REMOVED: The updateAutoAddState call that was causing the bug
                // We don't need it because enableAutoAddWithPhaseDetection handles everything

                updateAutoAddTimerVisibility(activityType, true)
            }
        }
    }


    private suspend fun getLastActivityTimeForType(activityType: ActivityType): Long {
        // Check room data first if in cloud session
        latestRoomData?.let { room ->
            val lastActivity = room.safeActivities()
                .filter { it.type.equals(activityType.name, ignoreCase = true) }
                .maxByOrNull { it.timestamp }

            if (lastActivity != null) {
                return lastActivity.timestamp
            }
        }

        // Fallback to local database
        return withContext(Dispatchers.IO) {
            val lastLog = repo.getLastLogByType(activityType)
            lastLog?.timestamp ?: sessionStart
        }
    }

    private suspend fun calculateNextAutoTime(activityType: ActivityType): Long {
        return withContext(Dispatchers.IO) {
            // Get activities from current room or local database
            val activities = latestRoomData?.safeActivities() ?: run {
                // Fallback to local database if no room data
                val logs = repo.getLogsInTimeRange(sessionStart, null)
                logs.map { log ->
                    val smoker = smokers.find { it.smokerId == log.smokerId }
                    SessionActivity(
                        smokerId = smoker?.cloudUserId ?: "local_${log.smokerId}",
                        smokerName = smoker?.name ?: "Unknown",
                        type = log.type.name,
                        timestamp = log.timestamp
                    )
                }
            }

            // Filter activities for this type
            val typeActivities = activities.filter {
                it.type.equals(activityType.name, ignoreCase = true)
            }.sortedBy { it.timestamp }

            Log.d(TAG, "🤖 Found ${typeActivities.size} activities of type $activityType for calculation")

            if (typeActivities.size < 2) {
                Log.d(TAG, "🤖 Not enough data for auto-add (need at least 2 activities)")
                return@withContext 0L
            }

            // Use the gap between the last two activities (not average)
            val lastActivity = typeActivities.last()
            val secondLastActivity = typeActivities[typeActivities.size - 2]
            val lastGap = lastActivity.timestamp - secondLastActivity.timestamp

            Log.d(TAG, "🤖 Last gap for $activityType: ${lastGap}ms")

            // Calculate next auto time based on last activity + last gap
            val nextAutoTime = lastActivity.timestamp + lastGap

            Log.d(TAG, "🤖 Last activity: ${lastActivity.timestamp}, next auto time: $nextAutoTime")

            nextAutoTime
        }
    }

    private fun createLocalAutoAddState(activityType: ActivityType, enabled: Boolean, nextAutoTime: Long): AutoAddState {
        return AutoAddState(
            coneAutoEnabled = if (activityType == ActivityType.CONE) enabled else false,
            jointAutoEnabled = if (activityType == ActivityType.JOINT) enabled else false,
            bowlAutoEnabled = if (activityType == ActivityType.BOWL) enabled else false,
            coneNextAutoTime = if (activityType == ActivityType.CONE) nextAutoTime else 0L,
            jointNextAutoTime = if (activityType == ActivityType.JOINT) nextAutoTime else 0L,
            bowlNextAutoTime = if (activityType == ActivityType.BOWL) nextAutoTime else 0L,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun handleAutoAdd(activityType: ActivityType) {
        Log.d(TAG, "🤖🎯 HANDLE_AUTO_ADD: $activityType")

        // Play timer sound if enabled
        if (timerSoundHelper.isSoundEnabled()) {
            Log.d(TAG, "🔔 Playing auto-add timer sound")
            timerSoundHelper.playTimerSound()
        }

        // Get current selected smoker
        val selectedPosition = binding.spinnerSmoker.selectedItemPosition
        if (selectedPosition < 0 || selectedPosition >= smokers.size) {
            Log.w(TAG, "🤖 Cannot auto-add: no valid smoker selected")
            return
        }

        // Notify the auto-add manager that an activity is being logged
        val now = System.currentTimeMillis() - rewindOffset
        autoAddManager.onActivityLogged(activityType, now)

        // Add the activity (this will trigger the normal flow)
        logHitSafe(activityType)
    }


    private fun updateAutoAddTimerDisplay(activityType: ActivityType, remainingMs: Long) {
        val totalSeconds = remainingMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        // Changed format from "2:12" to "2m 12s"
        val timerText = if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }

        Log.d(TAG, "🖥️ updateAutoAddTimerDisplay DETAILED:")
        Log.d(TAG, "🖥️   Activity: $activityType")
        Log.d(TAG, "🖥️   Input remainingMs: $remainingMs")
        Log.d(TAG, "🖥️   totalSeconds: $totalSeconds")
        Log.d(TAG, "🖥️   minutes: $minutes")
        Log.d(TAG, "🖥️   seconds: $seconds")
        Log.d(TAG, "🖥️   Final timerText: '$timerText'")

        runOnUiThread {
            when (activityType) {
                ActivityType.CONE -> {
                    binding.textConeTimer.text = timerText
                    binding.textConeTimer.visibility = View.VISIBLE
                    Log.d(TAG, "🖥️ SET cone timer UI to '$timerText'")
                }
                ActivityType.JOINT -> {
                    binding.textJointTimer.text = timerText
                    binding.textJointTimer.visibility = View.VISIBLE
                    Log.d(TAG, "🖥️ SET joint timer UI to '$timerText'")
                }
                ActivityType.BOWL -> {
                    binding.textBowlTimer.text = timerText
                    binding.textBowlTimer.visibility = View.VISIBLE
                    Log.d(TAG, "🖥️ SET bowl timer UI to '$timerText'")
                }
                else -> { /* ignore */ }
            }
        }
    }

    private fun updateAutoAddTimerVisibility(activityType: ActivityType, enabled: Boolean) {
        val timerView = when (activityType) {
            ActivityType.CONE -> binding.textConeTimer
            ActivityType.JOINT -> binding.textJointTimer
            ActivityType.BOWL -> binding.textBowlTimer
            else -> return
        }

        timerView.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun getCheckboxForActivityType(activityType: ActivityType): CheckBox? {
        return when (activityType) {
            ActivityType.CONE -> binding.checkboxConeAuto
            ActivityType.JOINT -> binding.checkboxJointAuto
            ActivityType.BOWL -> binding.checkboxBowlAuto
            else -> null
        }
    }

    private fun formatTimerCountdown(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "${minutes}:${seconds.toString().padStart(2, '0')}"
    }

    private fun nextSmoker() {
        if (smokers.size > 1) {
            val previousSmoker = smokers[currentSmokerIndex].name
            currentSmokerIndex = (currentSmokerIndex + 1) % smokers.size
            val newSmoker = smokers[currentSmokerIndex].name

            Log.d("MainActivity", "🔄 Switching smoker from $previousSmoker to $newSmoker (index: $currentSmokerIndex)")

            updateSmokerDisplay()
            saveCurrentSmokerIndex()
        }
    }

    private fun updateSmokerDisplay() {
        // Update the spinner to show the current smoker
        val sections = organizeSmokers()
        val organizedSmokers = sections.flatMap { it.smokers }
        if (currentSmokerIndex < organizedSmokers.size) {
            binding.spinnerSmoker.setSelection(currentSmokerIndex)
        }
    }

    private fun saveCurrentSmokerIndex() {
        prefs.edit().putInt("current_smoker_index", currentSmokerIndex).apply()
    }

    private fun getActiveSmokers(): List<Smoker> {
        val currentUserId = authManager.getCurrentUserId()

        return smokers.filter { smoker ->
            val smokerId = smoker.cloudUserId ?: "local_${smoker.smokerId}"

            // Include if:
            // - Not in paused list AND not in away list
            // - OR is current user (always show current user)
            (!pausedSmokerIds.contains(smokerId) && !awaySmokers.contains(smokerId)) ||
                    smokerId == currentUserId
        }
    }


    private fun moveToNextActiveSmoker() {
        Log.d(TAG, "🔄 MOVE_TO_NEXT_ACTIVE_SMOKER called")
        Log.d(TAG, "🔄   randomFontsEnabled: ${smokerManager.randomFontsEnabled}")
        Log.d(TAG, "🔄   colorChangingEnabled: ${smokerManager.colorChangingEnabled}")

        // Remove any session state checks - this should work regardless
        val activeSmokers = smokers.filter { smoker ->
            val smokerId = if (smoker.isCloudSmoker) smoker.cloudUserId else "local_${smoker.smokerId}"
            val userId = smoker.cloudUserId

            // Only check paused/away status if we're in a room (not session)
            if (currentShareCode != null) {
                !pausedSmokerIds.contains(smokerId) && !awaySmokers.contains(userId)
            } else {
                true // All smokers are active when not in a room
            }
        }

        if (activeSmokers.isEmpty()) {
            Log.w(TAG, "🔄 No active smokers available for rotation")
            return
        }

        // Find current smoker in the organized list
        val sections = organizeSmokers()
        val organizedSmokers = sections.flatMap { it.smokers }
        val currentPosition = binding.spinnerSmoker.selectedItemPosition
        val currentSmoker = if (currentPosition >= 0 && currentPosition < organizedSmokers.size) {
            organizedSmokers[currentPosition]
        } else null

        Log.d(TAG, "🔄 Current smoker: ${currentSmoker?.name}")

        // Find next active smoker
        val currentIndexInActive = currentSmoker?.let { activeSmokers.indexOf(it) } ?: -1
        val nextSmoker = activeSmokers[(currentIndexInActive + 1) % activeSmokers.size]

        Log.d(TAG, "🔄 Next smoker: ${nextSmoker.name}")



        // Find position of next smoker in organized list
        val nextAdapterIndex = organizedSmokers.indexOf(nextSmoker)
        if (nextAdapterIndex >= 0) {
            binding.spinnerSmoker.setSelection(nextAdapterIndex)
            selectSmoker(nextSmoker)
            Log.d(TAG, "🔄 Moved to next active smoker: ${nextSmoker.name} (skipped inactive smokers)")

            // Apply font and color after small delay
            handler.postDelayed({
                Log.d(TAG, "🔄 Applying font/color after smoker change")
                applyFontToSpinner()
            }, 100)
        }
    }

    // NEW METHOD 1: joinRoom
    private fun joinRoomLocally(shareCode: String) {
        lifecycleScope.launch {
            try {
                val currentUserId = authManager.getCurrentUserId()
                if (currentUserId == null) {
                    Toast.makeText(this@MainActivity, "Please sign in first", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Get all local smokers to sync to the room
                val localSmokers = withContext(Dispatchers.IO) {
                    repo.allSmokers.value?.filter { !it.isCloudSmoker } ?: emptyList()
                }

                // Join room and sync smokers using SessionSyncService
                sessionSyncService.joinRoomWithSmokerSync(
                    currentUserId,
                    shareCode,
                    localSmokers
                ).fold(
                    onSuccess = { roomData ->
                        currentRoom = roomData
                        startSession(roomData.startTime)
                        currentShareCode = roomData.shareCode
                        currentRoomName = roomData.name
                        startRoomListener(shareCode)

                        // Sync room smokers back to local database
                        lifecycleScope.launch(Dispatchers.IO) {
                            val roomSmokers = roomData.safeSharedSmokers()
                            val newLocalSmokers = sessionSyncService.syncRoomSmokersToLocal(currentUserId, roomSmokers)

                            withContext(Dispatchers.Main) {
                                if (newLocalSmokers.isNotEmpty()) {
                                    Toast.makeText(this@MainActivity, "Added ${newLocalSmokers.size} new smokers from room", Toast.LENGTH_SHORT).show()
                                }
                                Toast.makeText(this@MainActivity, "Joined ${roomData.name}", Toast.LENGTH_SHORT).show()
                                cleanupDuplicateLocalSmokers()
                            }
                        }

                        // Switch to session tab
                        binding.viewPager.currentItem = 0
                    },
                    onFailure = { error ->
                        Toast.makeText(this@MainActivity, "Failed to join room: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error joining room: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Add this function to MainActivity.kt
    private fun debugStashSource() {
        val stashViewModel = ViewModelProvider(this).get(StashViewModel::class.java)
        val currentSource = stashViewModel.stashSource.value
        Log.d(TAG, "🔍 === STASH SOURCE DEBUG ===")
        Log.d(TAG, "🔍 Current stash source from ViewModel: $currentSource")

        val radioId = when(currentSource) {
            StashSource.MY_STASH -> R.id.radioMyStashAttribution
            StashSource.THEIR_STASH -> R.id.radioTheirStashAttribution
            StashSource.EACH_TO_OWN -> R.id.radioEachToOwnAttribution
            else -> -1
        }

        // Check what's actually selected in the StashFragment
        supportFragmentManager.fragments
            .filterIsInstance<StashFragment>()
            .firstOrNull()?.let { fragment ->
                Log.d(TAG, "🔍 StashFragment found, checking radio state...")
                // The fragment should log its radio state
            }

        Log.d(TAG, "🔍 Expected radio ID: $radioId")
        Log.d(TAG, "🔍 === END STASH SOURCE DEBUG ===")
    }

    // NEW METHOD 2: createRoom
    private fun createRoom(roomName: String) {
        lifecycleScope.launch {
            try {
                val currentUserId = authManager.getCurrentUserId()
                if (currentUserId == null) {
                    Toast.makeText(this@MainActivity, "Please sign in first", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                sessionSyncService.createRoom(currentUserId, roomName).fold(
                    onSuccess = { roomData ->
                        currentRoom = roomData
                        startSession(roomData.startTime)
                        currentShareCode = roomData.shareCode
                        currentRoomName = roomData.name

                        // Sync local smokers to the new room
                        val localSmokers = withContext(Dispatchers.IO) {
                            repo.allSmokers.value?.filter { !it.isCloudSmoker } ?: emptyList()
                        }
                        sessionSyncService.syncLocalSmokersToRoom(
                            currentUserId,
                            roomData.shareCode,
                            localSmokers
                        ).fold(
                            onSuccess = {
                                startRoomListener(roomData.shareCode)
                                // Toast.makeText(this@MainActivity, "Created room: ${roomData.name}", Toast.LENGTH_SHORT).show()
                                binding.viewPager.currentItem = 0
                                // Add this after successful room join/create
                                cleanupDuplicateLocalSmokers()
                            },
                            onFailure = { syncError ->
                                startRoomListener(roomData.shareCode)
                                Toast.makeText(this@MainActivity, "Created room: ${roomData.name}", Toast.LENGTH_SHORT).show()
                                binding.viewPager.currentItem = 0
                                // Add this after successful room join/create
                                cleanupDuplicateLocalSmokers()
                            }
                        )
                    },
                    onFailure = { error ->
                        Toast.makeText(this@MainActivity, "Failed to create room: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error creating room: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun proceedWithLogHitWithSource(type: ActivityType, timestamp: Long, stashSource: StashSource) {
        Log.d(TAG, "🎯 proceedWithLogHitWithSource: type=$type, source=$stashSource")

        val selectedPosition = binding.spinnerSmoker.selectedItemPosition
        val organizedSmokers = organizeSmokers().flatMap { it.smokers }

        if (selectedPosition < 0 || selectedPosition >= organizedSmokers.size) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Please select a valid smoker!", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val selectedSmoker = organizedSmokers[selectedPosition]
        val currentUserId = authManager.getCurrentUserId() ?: getAndroidDeviceId()

        // CRITICAL: Determine payerStashOwnerId based on stash source
        val payerStashOwnerId = when (stashSource) {
            StashSource.MY_STASH -> {
                Log.d(TAG, "🎯 Setting payerStashOwnerId to null (MY_STASH)")
                null
            }
            StashSource.THEIR_STASH -> {
                Log.d(TAG, "🎯 Setting payerStashOwnerId to 'their_stash' (THEIR_STASH)")
                "their_stash"
            }
            StashSource.EACH_TO_OWN -> {
                if (selectedSmoker.cloudUserId == currentUserId || selectedSmoker.uid == currentUserId) {
                    Log.d(TAG, "🎯 Setting payerStashOwnerId to null (EACH_TO_OWN - current user)")
                    null
                } else {
                    val otherId = "other_${selectedSmoker.smokerId}"
                    Log.d(TAG, "🎯 Setting payerStashOwnerId to '$otherId' (EACH_TO_OWN - other user)")
                    otherId
                }
            }
        }

        // Check if password verification is needed
        if (selectedSmoker.isCloudSmoker &&
            selectedSmoker.passwordHash != null &&
            !selectedSmoker.isPasswordVerified) {

            withContext(Dispatchers.Main) {
                passwordDialog.showVerifyPasswordDialog(
                    smokerName = selectedSmoker.name,
                    onPasswordEntered = { password ->
                        verifyPasswordAndLogHitWithPayer(selectedSmoker, type, timestamp, password, payerStashOwnerId)
                    }
                )
            }
        } else {
            // No password needed or already verified
            logHitWithPayer(type, timestamp, payerStashOwnerId)
        }
    }

    private suspend fun logHitWithPayer(type: ActivityType, now: Long, payerStashOwnerId: String?) {
        Log.d(TAG, "🎯 === logHitWithPayer START ===")
        Log.d(TAG, "🎯 Type: $type, Time: $now, PayerStashOwnerId: '$payerStashOwnerId'")

        if (!sessionActive) {
            Log.w(TAG, "🎯 Cannot log hit - session not active")
            return
        }

        val adjustedNow = now - rewindOffset
        val selectedPosition = binding.spinnerSmoker.selectedItemPosition
        val organizedSmokers = organizeSmokers().flatMap { it.smokers }

        if (selectedPosition < 0 || selectedPosition >= organizedSmokers.size) {
            return
        }

        val selectedSmoker = organizedSmokers[selectedPosition]
        val stashViewModel = ViewModelProvider(this).get(StashViewModel::class.java)
        val currentStash = stashViewModel.currentStash.value
        val ratios = stashViewModel.ratios.value

        // Create the activity log with the specified payerStashOwnerId
        val activityLog = ActivityLog(
            id = 0L,
            smokerId = selectedSmoker.smokerId,
            consumerId = selectedSmoker.smokerId,
            payerStashOwnerId = payerStashOwnerId,
            type = type,
            timestamp = adjustedNow,
            sessionId = if (sessionActive) sessionStart else null,
            sessionStartTime = if (sessionActive) sessionStart else null,
            gramsAtLog = when (type) {
                ActivityType.CONE -> ratios?.coneGrams ?: 0.3
                ActivityType.JOINT -> ratios?.jointGrams ?: 0.5
                ActivityType.BOWL -> ratios?.bowlGrams ?: 0.2
                else -> 0.0
            },
            pricePerGramAtLog = currentStash?.pricePerGram ?: 15.0
        )

        // ALWAYS store in local database first
        withContext(Dispatchers.IO) {
            val insertedId = repo.insert(activityLog)
            Log.d(TAG, "🎯 INSERTED activity ID $insertedId with sessionId: ${if (sessionActive) sessionStart else null}")

            // Verify it was stored correctly
            val verifyActivity = repo.getActivityById(insertedId)
            Log.d(TAG, "🎯 VERIFICATION - stored sessionId: ${verifyActivity?.sessionId}")
        }

        // THEN handle cloud sync if in a cloud session
        if (currentShareCode != null) {
            val smokerActivityUid = if (selectedSmoker.isCloudSmoker) {
                selectedSmoker.cloudUserId!!
            } else {
                "local_${selectedSmoker.uid}"
            }

            sessionSyncService.addActivityToRoom(
                shareCode = currentShareCode!!,
                smokerUid = smokerActivityUid,
                smokerName = selectedSmoker.name,
                activityType = type,
                timestamp = adjustedNow,
                deviceId = getAndroidDeviceId()
            ).fold(
                onSuccess = {
                    Log.d(TAG, "🎯 Activity also synced to cloud room")
                },
                onFailure = { error ->
                    Log.e(TAG, "🎯 Failed to sync to room: ${error.message}")
                }
            )
        } else {
            // Local session - just refresh stats
            refreshLocalSessionStatsIfNeeded()
        }

        // CRITICAL FIX: Call handlePostHitActionsWithPayer instead of handlePostHitActionsSimple
        // This ensures goals are updated!
        handlePostHitActionsWithPayer(selectedSmoker, selectedPosition, type, adjustedNow, payerStashOwnerId)

        Log.d(TAG, "🎯 === logHitWithPayer END ===")
    }


    private suspend fun handlePostHitActionsSimple(selectedSmoker: Smoker, type: ActivityType, timestamp: Long) {
        if (sessionActive) {
            activitiesTimestamps.add(timestamp)
            activitiesTimestamps.sort()
            actualLastLogTime = activitiesTimestamps.maxOrNull() ?: timestamp
            lastLogTime = timestamp

            if (isAutoMode && smokers.isNotEmpty()) {
                lastHitCameFromUI = true
                withContext(Dispatchers.Main) {
                    moveToNextActiveSmoker()
                }
            }

            sessionStatsVM.refreshTimer()
        }

        // Update stash tracking
        val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
        if (stashViewModel.currentStash.value != null) {
            val smokerUid = if (selectedSmoker.isCloudSmoker && !selectedSmoker.cloudUserId.isNullOrEmpty()) {
                selectedSmoker.cloudUserId
            } else {
                "local_${selectedSmoker.uid}"
            }
            stashViewModel.recordConsumption(
                activityType = type,
                smokerUid = smokerUid!!,
                smokerName = selectedSmoker.name,
                timestamp = timestamp
            )
            stashViewModel.onActivityLogged(type)
        }

        withContext(Dispatchers.Main) {
            updateUndoButtonVisibility()
        }
    }

    private fun verifyPasswordAndLogHitWithPayer(
        smoker: Smoker,
        type: ActivityType,
        timestamp: Long,
        password: String,
        payerStashOwnerId: String?
    ) {
        lifecycleScope.launch {
            val isValid = smoker.passwordHash
                ?.let { PasswordUtils.verifyPassword(password, it) }
                ?: false

            if (isValid) {
                val verified = smoker.copy(isPasswordVerified = true)
                withContext(Dispatchers.IO) {
                    repo.updateSmoker(verified)
                }

                val prefs = getSharedPreferences("smoker_passwords", Context.MODE_PRIVATE)
                prefs.edit().putString(smoker.cloudUserId ?: smoker.smokerId.toString(), password).apply()

                logHitWithPayer(type, timestamp, payerStashOwnerId)
                Toast.makeText(this@MainActivity, "Password verified for ${smoker.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Incorrect password for ${smoker.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // NEW METHOD 3: addSmoker
    private fun addSmoker(name: String, isCloudSmoker: Boolean = false, cloudUserId: String? = null) {
        lifecycleScope.launch {
            try {
                val smoker = Smoker(
                    name = name,
                    isCloudSmoker = isCloudSmoker,
                    cloudUserId = cloudUserId,
                    needsSync = isCloudSmoker
                )

                val smokerId = withContext(Dispatchers.IO) {
                    repo.insertSmoker(smoker)
                }
                val newSmoker = smoker.copy(smokerId = smokerId)

                // If we're in a room, sync this new smoker to the room
                currentRoom?.let { room ->
                    val currentUserId = authManager.getCurrentUserId()
                    if (currentUserId != null) {
                        sessionSyncService.addSharedSmokerToRoom(
                            shareCode = room.shareCode,
                            addedByUserId = currentUserId,
                            smoker = newSmoker
                        ).fold(
                            onSuccess = {
                                Log.d("MainActivity", "Successfully synced new smoker to room")
                            },
                            onFailure = { error ->
                                Log.w("MainActivity", "Failed to sync new smoker to room: ${error.message}")
                            }
                        )
                    }
                }

                Toast.makeText(this@MainActivity, "Added smoker: $name", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error adding smoker: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // NEW METHOD 4: onRoomUpdated
    private fun onRoomUpdated(roomData: RoomData) {
        Log.d(TAG, "👥 onRoomUpdated called with ${roomData.safeSharedSmokers().size} shared smokers")
        lifecycleScope.launch {
            try {
                currentRoom = roomData

                // Try to get current user ID, but also check Firebase Auth directly
                var currentUserId = authManager.getCurrentUserId()
                if (currentUserId == null) {
                    // Fallback to Firebase Auth directly
                    currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    Log.d(TAG, "👥 authManager returned null, Firebase Auth user: $currentUserId")
                }

                if (currentUserId == null) {
                    // Still null? Try the Android device ID as last resort
                    currentUserId = getAndroidDeviceId()
                    Log.d(TAG, "👥 Using Android device ID as fallback: $currentUserId")
                }

                Log.d(TAG, "👥 Final user ID for sync: $currentUserId")

                withContext(Dispatchers.IO) {
                    // Sync both participants and shared smokers
                    updateParticipantsFromRoom(roomData)

                    // Sync shared smokers but with better duplicate prevention
                    syncSharedSmokersFromRoomSafely(roomData)

                    // CRITICAL FIX: Also sync our local smokers TO the room
                    // This ensures when User B joins, their local smokers are added to the room
                    val localSmokers = repo.getAllSmokersList().filter { !it.isCloudSmoker }
                    if (localSmokers.isNotEmpty()) {
                        Log.d(TAG, "👥 Syncing ${localSmokers.size} local smokers TO room")
                        sessionSyncService.syncLocalSmokersToRoom(
                            userId = currentUserId,
                            shareCode = roomData.shareCode,
                            localSmokers = localSmokers
                        ).fold(
                            onSuccess = {
                                Log.d(TAG, "👥 Successfully synced local smokers to room")
                            },
                            onFailure = { error ->
                                Log.e(TAG, "👥 Failed to sync local smokers to room: ${error.message}")
                            }
                        )
                    }
                }

                // ADD THIS: Refresh the adapter after syncing
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "👥 Refreshing smoker adapter after room update")

                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Error handling room update", e)
            }
        }
    }

    private suspend fun syncSharedSmokersFromRoomSafely(room: RoomData) {
        Log.d(TAG, "👥 Safely syncing shared smokers from room")

        val sharedSmokers = room.safeSharedSmokers()
        Log.d(TAG, "👥 Room has ${sharedSmokers.size} shared smokers")

        // Log what's in the room
        sharedSmokers.forEach { (id, data) ->
            Log.d(TAG, "👥 Shared smoker: id=$id, data=$data")
        }

        // REMOVED THE USER ID CHECK - we don't need it for syncing smokers

        // Get current local smokers to prevent duplicates by UID
        val currentLocalSmokersByUid = smokers.filter { !it.isCloudSmoker }.associateBy { it.uid }

        Log.d(TAG, "👥 Current local smokers by UID: ${currentLocalSmokersByUid.keys}")

        var newSmokersAdded = 0

        // Process shared smokers with better duplicate checking
        for ((smokerRoomId, smokerData) in sharedSmokers) {
            try {
                val name = smokerData["name"] as? String ?: continue
                val isLocal = smokerData["isLocal"] as? Boolean ?: false

                if (isLocal && smokerRoomId.startsWith("local_")) {
                    // Extract the UID from the key, e.g., "local_UUID-A" -> "UUID-A"
                    val uidFromRoom = smokerRoomId.removePrefix("local_")

                    // Check if a smoker with this specific UID already exists locally
                    if (!currentLocalSmokersByUid.containsKey(uidFromRoom)) {
                        Log.d(TAG, "👥 Creating missing shared smoker: '$name' with UID: $uidFromRoom")

                        val newSmoker = Smoker(
                            uid = uidFromRoom, // Use the UID from the cloud here!
                            name = name,
                            isCloudSmoker = false,
                            cloudUserId = null,
                            shareCode = null,
                            lastSyncTime = System.currentTimeMillis()
                        )

                        repo.insertSmoker(newSmoker)
                        newSmokersAdded++

                        Log.d(TAG, "👥 ✅ Created shared smoker: '$name' with shared UID: $uidFromRoom")
                    } else {
                        Log.d(TAG, "👥 Smoker with UID '$uidFromRoom' already exists locally - skipping")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "👥 Error syncing shared smoker: ${e.message}")
            }
        }

        if (newSmokersAdded > 0) {
            withContext(Dispatchers.Main) {
                // Toast.makeText(this@MainActivity, "Added $newSmokersAdded new smokers from room", Toast.LENGTH_SHORT).show()
            }
            Log.d(TAG, "👥 ✅ Synced $newSmokersAdded new smokers from room")
        } else {
            Log.d(TAG, "👥 No new smokers to sync")
        }
    }




    private fun toggleTimersVisibility() {
        Log.d(TAG, "🔘 === TOGGLE TIMERS START ===")
        Log.d(TAG, "🔘 Current state - timersVisible: $timersVisible")

        timersVisible = !timersVisible
        Log.d(TAG, "🔘 New state - timersVisible: $timersVisible")

        val buttonContainer = binding.buttonContainer
        val params = buttonContainer.layoutParams as LinearLayout.LayoutParams

        // Get the activity buttons
        val jointButton = binding.btnAddJoint
        val coneButton = binding.btnAddCone
        val bowlButton = binding.btnAddBowl

        if (timersVisible) {
            binding.btnToggleTimers.text = "See Less"
            binding.timerContainer.visibility = View.VISIBLE
            binding.roundsContainer.visibility = View.VISIBLE
            binding.layoutConeAutoControls.visibility = View.VISIBLE
            binding.layoutJointAutoControls.visibility = View.VISIBLE
            binding.layoutBowlAutoControls.visibility = View.VISIBLE

            // Double the height when "See Less" is shown
            setActivityButtonHeights(jointButton, coneButton, bowlButton, 96.dpToPx(this))
            params.topMargin = -19.dpToPx(this)
            
            // Switch to expanded background
            binding.topSectionContainer.setBackgroundResource(R.drawable.section_background_expanded)

            Log.d(TAG, "🔘 Showing all timer controls with doubled button heights")
        } else {
            binding.btnToggleTimers.text = "Advanced"
            binding.timerContainer.visibility = View.GONE
            binding.roundsContainer.visibility = View.GONE
            binding.layoutConeAutoControls.visibility = View.GONE
            binding.layoutJointAutoControls.visibility = View.GONE
            binding.layoutBowlAutoControls.visibility = View.GONE

            // Normal height when "Advanced" is shown
            setActivityButtonHeights(jointButton, coneButton, bowlButton, 48.dpToPx(this))
            params.topMargin = -5.dpToPx(this)
            
            // Switch to collapsed background
            binding.topSectionContainer.setBackgroundResource(R.drawable.section_background_collapsed)

            Log.d(TAG, "🔘 Hiding all timer controls with normal button heights")
        }

        buttonContainer.layoutParams = params
        Log.d(TAG, "🔘 === TOGGLE TIMERS COMPLETE ===")
    }
    
    private fun refreshCloudSmokerName(smoker: Smoker) {
        if (!smoker.isCloudSmoker || smoker.cloudUserId == null) return

        lifecycleScope.launch {
            cloudSyncService.getCloudSmokerProfile(smoker.cloudUserId).fold(
                onSuccess = { cloudProfile ->
                    if (cloudProfile != null && cloudProfile.name != smoker.name) {
                        val updated = smoker.copy(
                            name = cloudProfile.name,
                            lastSyncTime = System.currentTimeMillis()
                        )

                        withContext(Dispatchers.IO) {
                            repo.updateSmoker(updated)
                        }

                        withContext(Dispatchers.Main) {

                            Toast.makeText(
                                this@MainActivity,
                                "Updated ${smoker.name} → ${cloudProfile.name}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "${smoker.name} is already up to date",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onFailure = { error ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to refresh: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }

    private suspend fun logHitForSpecificSmoker(type: ActivityType, timestamp: Long, smoker: Smoker) {
        Log.d(TAG, "🎯 Logging ${type.name} for specific smoker: ${smoker.name}")

        val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
        val stashSource = stashViewModel.stashSource.value ?: StashSource.MY_STASH
        val currentUserId = authManager.getCurrentUserId() ?: getAndroidDeviceId()

        val payerStashOwnerId = when (stashSource) {
            StashSource.MY_STASH -> null
            StashSource.THEIR_STASH -> "their_stash"
            StashSource.EACH_TO_OWN -> {
                if (smoker.cloudUserId == currentUserId || smoker.uid == currentUserId) {
                    null
                } else {
                    "other_${smoker.smokerId}"
                }
            }
        }

        val currentSessionId = if (sessionActive) sessionStart else null

        val activityLog = ActivityLog(
            id = 0L,
            smokerId = smoker.smokerId,
            consumerId = smoker.smokerId,
            payerStashOwnerId = payerStashOwnerId,
            type = type,
            timestamp = timestamp,
            sessionId = currentSessionId,
            sessionStartTime = if (sessionActive) sessionStart else null,
            gramsAtLog = when (type) {
                ActivityType.CONE -> stashViewModel.ratios.value?.coneGrams ?: 0.3
                ActivityType.JOINT -> stashViewModel.ratios.value?.jointGrams ?: 0.5
                ActivityType.BOWL -> stashViewModel.ratios.value?.bowlGrams ?: 0.2
                else -> 0.0
            },
            pricePerGramAtLog = stashViewModel.currentStash.value?.pricePerGram ?: 15.0
        )

        withContext(Dispatchers.IO) {
            repo.insert(activityLog)
        }

        // Trigger stats refresh
        stashViewModel.onActivityLogged(type)
    }

    private fun toggleNotifications() {
        notificationsEnabled = !notificationsEnabled
        prefs.edit().putBoolean("notifications_enabled", notificationsEnabled).apply()

        updateNotificationButtonState()

        if (!notificationsEnabled) {
            // Clear all existing notifications
            clearAllNotifications()
            Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
        } else {
            // Re-show notifications if session is active
            if (sessionActive) {
                refreshNotificationsWithSession()
            } else {
                triggerInitialNotifications()
            }
            Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptStartSessionForActivity(type: ActivityType) {
        AlertDialog.Builder(this)
            .setTitle("No Active Session")
            .setMessage("You need to start a session to track activities. Start a session now?")
            .setPositiveButton("Start Session") { _, _ ->
                showCloudSessionOptions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNoActiveSessionPopup() {
        // Redirect to the new function with CONE as default
        showNoActiveSessionPopupForType(ActivityType.CONE)
    }

    private fun showNoActiveSessionPopupForType(type: ActivityType) {
        Log.d(TAG, "🎯 Showing no active session popup for type: $type")

        // Check if there are any cloud smokers
        val hasCloudSmokers = smokers.any { it.isCloudSmoker }

        if (!hasCloudSmokers) {
            // Show the new "No Cloud User" popup instead
            showNoCloudUserPopup()
            return
        }

        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        currentDialog = dialog

        val dialogView = createThemedNoActiveSessionDialogForType(dialog, type)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
            setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }

        dialog.setOnDismissListener {
            currentDialog = null
            pendingActivityType = null
            Log.d(TAG, "🎯 No active session dialog dismissed for type: $type")
        }

        dialogView.alpha = 0f
        dialog.show()
        performManualFadeIn(dialogView, 2000L)
    }

    private fun createThemedNoActiveSessionDialogForType(dialog: Dialog, activityType: ActivityType): View {
        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        val contentWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val topSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        contentWrapper.addView(topSpacer)

        val mainCard = androidx.cardview.widget.CardView(this).apply {
            radius = 20.dpToPx(this@MainActivity).toFloat()
            cardElevation = 12.dpToPx(this@MainActivity).toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16.dpToPx(this@MainActivity), 0, 16.dpToPx(this@MainActivity), 180.dpToPx(this@MainActivity))
            }
        }

        rootContainer.tag = mainCard

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(this@MainActivity), 24.dpToPx(this@MainActivity),
                24.dpToPx(this@MainActivity), 24.dpToPx(this@MainActivity))
        }

        val titleText = TextView(this).apply {
            text = "START SESSION"
            textSize = 22f
            setTextColor(Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.15f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(titleText)

        val activityName = when(activityType) {
            ActivityType.CONE -> "cone"
            ActivityType.JOINT -> "joint"
            ActivityType.BOWL -> "bowl"
            else -> "activity"
        }

        val messageText = TextView(this).apply {
            text = "You need to start a session to add a $activityName. Please choose your session type."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(messageText)

        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val cloudSessionButton = createImagePressButton("Cloud Session", true) {
            animateCardSelection(dialog) {
                showCloudSessionOptions()
            }
        }
        buttonContainer.addView(cloudSessionButton)

        val localSessionButton = createImagePressButton("Local Session", false) {
            animateCardSelection(dialog) {
                startLocalSession()
            }
        }
        buttonContainer.addView(localSessionButton)

        val cancelButton = TextView(this).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(Color.parseColor("#B0B0B0"))
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dpToPx(this@MainActivity)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animateCardSelection(dialog) {}
            }
        }
        buttonContainer.addView(cancelButton)

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

    private fun createImagePressButton(text: String, isPrimary: Boolean, onClick: () -> Unit): View {
        val buttonContainer = androidx.cardview.widget.CardView(this).apply {
            radius = 20.dpToPx(context).toFloat()
            cardElevation = if (isPrimary) 4.dpToPx(context).toFloat() else 0f
            setCardBackgroundColor(
                if (isPrimary) Color.parseColor("#98FB98")
                else Color.parseColor("#33FFFFFF")
            )

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx(this@MainActivity)
            ).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }

            isClickable = true
            isFocusable = true
        }

        // Create a FrameLayout to hold background image and text
        val contentFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Image view for pressed state (initially hidden)
        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.button_pressed_background)
            visibility = View.GONE
        }

        // Text on top
        val buttonText = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(
                if (isPrimary) Color.parseColor("#424242")
                else Color.WHITE
            )
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Add views in correct order
        contentFrame.addView(imageView)
        contentFrame.addView(buttonText)
        buttonContainer.addView(contentFrame)

        // Store original colors
        val originalBackgroundColor = if (isPrimary) Color.parseColor("#98FB98") else Color.parseColor("#33FFFFFF")
        val originalTextColor = if (isPrimary) Color.parseColor("#424242") else Color.WHITE

        // Handle touch events
        buttonContainer.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Show image, hide solid color
                    buttonContainer.setCardBackgroundColor(Color.TRANSPARENT)
                    imageView.visibility = View.VISIBLE

                    // Change text color to white for both button types when pressed
                    buttonText.setTextColor(Color.WHITE)
                    buttonText.setShadowLayer(4f, 2f, 2f, Color.BLACK)
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // Hide image, restore solid color
                    imageView.visibility = View.GONE
                    buttonContainer.setCardBackgroundColor(originalBackgroundColor)

                    // Restore original text color
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




    private fun showThemedConfirmationDialog(capturedSmoker: Smoker, finalStashSource: StashSource, now: Long) {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        // Create themed dialog view
        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        val contentWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val topSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        contentWrapper.addView(topSpacer)

        // Main card - RAISED BY 180dp
        val mainCard = androidx.cardview.widget.CardView(this).apply {
            radius = 20.dpToPx(this@MainActivity).toFloat()
            cardElevation = 12.dpToPx(this@MainActivity).toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16.dpToPx(this@MainActivity), 0, 16.dpToPx(this@MainActivity), 180.dpToPx(this@MainActivity))
            }
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(this@MainActivity), 24.dpToPx(this@MainActivity),
                24.dpToPx(this@MainActivity), 24.dpToPx(this@MainActivity))
        }

        // Title
        val titleText = TextView(this).apply {
            text = "Confirmation"
            textSize = 22f
            setTextColor(Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(titleText)

        // Message
        val messageText = TextView(this).apply {
            text = "Just to be sure, what are you wanting to do?"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(messageText)

        // Button container
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Add with new bowl button (primary - green)
        val addWithBowlButton = createImagePressButton("Add with new bowl", true) {
            animateCardSelection(dialog) {
                confettiHelper.showSuccessConfetti()
                lifecycleScope.launch {
                    val originalAutoMode = isAutoMode
                    isAutoMode = false

                    Log.d(TAG, "🎯 Adding bowl for ${capturedSmoker.name}")
                    val bowlTimestamp = now - 100
                    proceedWithLogHitWithSourceAndSmoker(ActivityType.BOWL, bowlTimestamp, finalStashSource, capturedSmoker)

                    delay(200)

                    isAutoMode = originalAutoMode

                    Log.d(TAG, "🎯 Adding cone for ${capturedSmoker.name}")
                    proceedWithLogHitWithSourceAndSmoker(ActivityType.CONE, now, finalStashSource, capturedSmoker)

                    withContext(Dispatchers.Main) {
                        if (currentShareCode == null) {
                            refreshLocalSessionStatsIfNeeded()
                        }
                        sessionStatsVM.refreshTimer()
                        stashViewModel.onActivityLogged(ActivityType.CONE)
                    }
                }
            }
        }
        buttonContainer.addView(addWithBowlButton)

        // Add without bowl button (secondary)
        val addWithoutBowlButton = createImagePressButton("Add without bowl", false) {
            animateCardSelection(dialog) {
                confettiHelper.showSuccessConfetti()
                lifecycleScope.launch {
                    proceedWithLogHitWithSourceAndSmoker(ActivityType.CONE, now, finalStashSource, capturedSmoker)
                }
            }
        }
        buttonContainer.addView(addWithoutBowlButton)

        contentLayout.addView(buttonContainer)
        mainCard.addView(contentLayout)
        contentWrapper.addView(mainCard)
        rootContainer.addView(contentWrapper)

        // Click to dismiss on background
        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                animateCardSelection(dialog) {}
            }
        }

        dialog.setContentView(rootContainer)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
            setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }

        // Set initial alpha to 0 for fade-in
        rootContainer.alpha = 0f

        dialog.show()

        // Apply fade-in animation
        performManualFadeIn(rootContainer, 2000L)
    }

    // Data class for offline activities
    data class OfflineActivity(
        val activityId: String = UUID.randomUUID().toString(),
        val shareCode: String,
        val smokerUid: String,
        val smokerName: String,
        val activityType: ActivityType,
        val timestamp: Long,
        val deviceId: String,
        val retryCount: Int = 0,
        val maxRetries: Int = 10
    )


    private fun updateNotificationButtonState() {
        if (notificationsEnabled) {
            // Normal bell icon, no strikethrough
            binding.btnNotificationToggle.setImageResource(android.R.drawable.ic_popup_reminder)
            binding.btnNotificationToggle.alpha = 1.0f
        } else {
            // Bell with strikethrough effect (using alpha and different icon if available)
            binding.btnNotificationToggle.setImageResource(android.R.drawable.ic_popup_reminder)
            binding.btnNotificationToggle.alpha = 0.4f  // Dimmed to indicate disabled
        }
    }

    private suspend fun proceedWithLogHitForSmoker(
        type: ActivityType,
        timestamp: Long,
        smoker: Smoker,
        shouldAdvanceSmoker: Boolean
    ) {
        Log.d(TAG, "🎯 Logging ${type.name} for specific smoker: ${smoker.name}, shouldAdvance: $shouldAdvanceSmoker")

        // Check password verification if needed
        if (smoker.isCloudSmoker && smoker.passwordHash != null && !smoker.isPasswordVerified) {
            withContext(Dispatchers.Main) {
                passwordDialog.showVerifyPasswordDialog(
                    smokerName = smoker.name,
                    onPasswordEntered = { password ->
                        lifecycleScope.launch {
                            val isValid = smoker.passwordHash?.let {
                                PasswordUtils.verifyPassword(password, it)
                            } ?: false

                            if (isValid) {
                                val verified = smoker.copy(isPasswordVerified = true)
                                repo.updateSmoker(verified)
                                logHitForSmokerInternal(type, timestamp, verified, shouldAdvanceSmoker)
                            } else {
                                Toast.makeText(this@MainActivity, "Incorrect password", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        } else {
            logHitForSmokerInternal(type, timestamp, smoker, shouldAdvanceSmoker)
        }
    }

    private suspend fun logHitForSmokerInternal(
        type: ActivityType,
        timestamp: Long,
        smoker: Smoker,
        shouldAdvanceSmoker: Boolean
    ) {
        val adjustedTimestamp = timestamp - rewindOffset

        Log.d(TAG, "🎯 Internal hit for ${smoker.name}: $type at $adjustedTimestamp, advance: $shouldAdvanceSmoker")

        // Handle cloud session
        if (currentShareCode != null) {
            val deviceId = getAndroidDeviceId()
            val smokerActivityUid = if (smoker.isCloudSmoker) {
                smoker.cloudUserId!!
            } else {
                "local_${smoker.uid}"
            }

            sessionSyncService.addActivityToRoom(
                shareCode = currentShareCode!!,
                smokerUid = smokerActivityUid,
                smokerName = smoker.name,
                activityType = type,
                timestamp = adjustedTimestamp,
                deviceId = deviceId
            ).fold(
                onSuccess = {
                    Log.d(TAG, "🎯 ✅ Activity added to cloud room for ${smoker.name}")
                },
                onFailure = { error ->
                    Log.e(TAG, "🎯 ❌ Failed to add to room: ${error.message}")
                }
            )
        } else {
            // Local session - store in database
            val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
            val stashSource = stashViewModel.stashSource.value ?: StashSource.MY_STASH
            val currentUserId = authManager.getCurrentUserId() ?: getAndroidDeviceId()

            // DEBUG: Log the stash source
            Log.d(TAG, "🎯 logHitForSmokerInternal - Stash Source: $stashSource")

            val payerStashOwnerId = when (stashSource) {
                StashSource.MY_STASH -> {
                    Log.d(TAG, "🎯 Setting payerStashOwnerId to null (MY_STASH)")
                    null
                }
                StashSource.THEIR_STASH -> {
                    Log.d(TAG, "🎯 Setting payerStashOwnerId to 'their_stash' (THEIR_STASH)")
                    "their_stash"
                }
                StashSource.EACH_TO_OWN -> {
                    if (smoker.cloudUserId == currentUserId || smoker.uid == currentUserId) {
                        Log.d(TAG, "🎯 Setting payerStashOwnerId to null (EACH_TO_OWN - current user)")
                        null
                    } else {
                        val otherId = "other_${smoker.smokerId}"
                        Log.d(TAG, "🎯 Setting payerStashOwnerId to '$otherId' (EACH_TO_OWN - other user)")
                        otherId
                    }
                }
            }

            val currentSessionId = if (sessionActive) sessionStart else null

            val activityLog = ActivityLog(
                id = 0L,
                smokerId = smoker.smokerId,
                consumerId = smoker.smokerId,
                payerStashOwnerId = payerStashOwnerId,  // CRITICAL: Set the correct value here
                type = type,
                timestamp = adjustedTimestamp,
                sessionId = currentSessionId,
                sessionStartTime = if (sessionActive) sessionStart else null,
                gramsAtLog = when (type) {
                    ActivityType.CONE -> stashViewModel.ratios.value?.coneGrams ?: 0.3
                    ActivityType.JOINT -> stashViewModel.ratios.value?.jointGrams ?: 0.5
                    ActivityType.BOWL -> stashViewModel.ratios.value?.bowlGrams ?: 0.2
                    else -> 0.0
                },
                pricePerGramAtLog = stashViewModel.currentStash.value?.pricePerGram ?: 15.0
            )

            withContext(Dispatchers.IO) {
                val insertedId = repo.insert(activityLog)
                Log.d(TAG, "🎯 Inserted to local DB with payerStashOwnerId: '$payerStashOwnerId'")

                // Verify it was stored correctly
                val verifyActivity = repo.getActivityById(insertedId)
                Log.d(TAG, "🎯 Verification - stored payerStashOwnerId: '${verifyActivity?.payerStashOwnerId}'")
            }
        }

        // Handle post-hit actions but DON'T advance smoker here - it will be done separately
        withContext(Dispatchers.Main) {
            // Update session tracking
            if (sessionActive) {
                activitiesTimestamps.add(adjustedTimestamp)
                activitiesTimestamps.sort()
                actualLastLogTime = activitiesTimestamps.maxOrNull() ?: adjustedTimestamp
                lastLogTime = adjustedTimestamp

                val activityLog = ActivityLog(
                    id = 0L,
                    smokerId = smoker.smokerId,
                    consumerId = smoker.smokerId,
                    payerStashOwnerId = null,  // This is just for history tracking, not stored
                    type = type,
                    timestamp = adjustedTimestamp,
                    sessionId = if (sessionActive) sessionStart else null,
                    sessionStartTime = if (sessionActive) sessionStart else null,
                    gramsAtLog = 0.0,
                    pricePerGramAtLog = 0.0
                )

                activityHistory.add(activityLog)
                if (activityHistory.size > 10) {
                    activityHistory.removeAt(0)
                }
            }

            // Update stash tracking
            val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
            if (stashViewModel.currentStash.value != null) {
                val smokerUid = if (smoker.isCloudSmoker && !smoker.cloudUserId.isNullOrEmpty()) {
                    smoker.cloudUserId
                } else {
                    "local_${smoker.uid}"
                }
                stashViewModel.recordConsumption(
                    activityType = type,
                    smokerUid = smokerUid!!,
                    smokerName = smoker.name,
                    timestamp = adjustedTimestamp
                )
                stashViewModel.onActivityLogged(type)
            }

            // Update goals
            val sessionShareCode = if (sessionActive) currentShareCode else null
            goalService.updateGoalProgressForActivity(
                type,
                sessionShareCode,
                smoker.name
            )

            // Show notification
            if (notificationsEnabled) {
                val helper = NotificationHelper(this@MainActivity)
                helper.showActivityNotification(
                    type,
                    lastTimestamp = adjustedTimestamp,
                    conesSinceLastBowl = null,
                    currentShareCode,
                    smoker.cloudUserId,
                    justAdded = true,
                    addedAt = adjustedTimestamp,
                    lastSmokerName = smoker.name
                )
            }

            // Update rounds if auto mode
            if (isAutoMode && currentShareCode == null) {
                hitsThisRound++
                val activeSmokerCount = getActiveSmokers().size
                if (activeSmokerCount > 0 && hitsThisRound >= activeSmokerCount) {
                    hitsThisRound = 0
                    actualRounds++
                    if (initialRoundsSet > 0) {
                        roundsLeft = kotlin.math.max(0, initialRoundsSet - actualRounds)
                    } else {
                        roundsLeft = 0
                    }
                    updateRoundsUI()
                }
            }

            // NOW advance to next smoker ONLY if requested
            if (shouldAdvanceSmoker && isAutoMode && smokers.isNotEmpty()) {
                Log.d(TAG, "🎯 Advancing to next smoker after ${smoker.name}")
                lastHitCameFromUI = true
                moveToNextActiveSmoker()
            } else {
                Log.d(TAG, "🎯 NOT advancing smoker (shouldAdvance: $shouldAdvanceSmoker, autoMode: $isAutoMode)")
            }

            // Refresh stats
            sessionStatsVM.refreshTimer()
            updateUndoButtonVisibility()
        }
    }

    private fun clearAllNotifications() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancelAll()
    }

    private fun setActivityButtonSelected(button: Button, isSelected: Boolean) {
        if (isSelected) {
            // Filled state - green background, grey text (same as app background)
            button.setBackgroundColor(ContextCompat.getColor(this, R.color.my_light_primary))
            button.setTextColor(ContextCompat.getColor(this, android.R.color.background_dark))
            // Remove stroke for filled state
            (button as? com.google.android.material.button.MaterialButton)?.strokeWidth = 0
        } else {
            // Outlined state - transparent background, green text and border
            button.setBackgroundColor(Color.TRANSPARENT)
            button.setTextColor(ContextCompat.getColor(this, R.color.my_light_primary))
            // Add stroke back for outlined state
            (button as? com.google.android.material.button.MaterialButton)?.apply {
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.my_light_primary))
                strokeWidth = 4
            }
        }
    }

    private fun debugGoalTracking(type: ActivityType) {
        Log.d(TAG, "🎯 === DEBUG GOAL TRACKING ===")
        Log.d(TAG, "🎯 Activity type: $type")
        Log.d(TAG, "🎯 Session active: $sessionActive")
        Log.d(TAG, "🎯 Session start: $sessionStart")
        Log.d(TAG, "🎯 Current share code: $currentShareCode")

        val selectedPosition = binding.spinnerSmoker.selectedItemPosition
        val organizedSmokers = organizeSmokers().flatMap { it.smokers }
        val selectedSmoker = organizedSmokers.getOrNull(selectedPosition)

        Log.d(TAG, "🎯 Selected smoker: ${selectedSmoker?.name}")
        Log.d(TAG, "🎯 Is GoalService initialized: ${::goalService.isInitialized}")

        // Check if we're actually calling the goal service
        lifecycleScope.launch {
            val goals = AppDatabase.getDatabase(application).goalDao().getAllGoalsSorted()
            goals.observe(this@MainActivity) { goalList ->
                Log.d(TAG, "🎯 Active goals in DB: ${goalList.size}")
                goalList.forEach { goal ->
                    Log.d(TAG, "🎯   Goal ${goal.goalId}: ${goal.goalName}")
                    Log.d(TAG, "🎯     Type: ${goal.goalType}")
                    Log.d(TAG, "🎯     Current: J${goal.currentJoints}/C${goal.currentCones}/B${goal.currentBowls}")
                    Log.d(TAG, "🎯     Target: J${goal.targetJoints}/C${goal.targetCones}/B${goal.targetBowls}")
                    Log.d(TAG, "🎯     Session code: ${goal.sessionShareCode}")
                    Log.d(TAG, "🎯     Selected smokers: ${goal.selectedSmokers}")
                }
            }
        }

        Log.d(TAG, "🎯 === END DEBUG ===")
    }

    private fun toggleSmokerPause(smoker: Smoker) {
        val shareCode = currentShareCode
        if (shareCode == null) {
            Toast.makeText(this, "Can only pause smokers in cloud sessions", Toast.LENGTH_SHORT).show()
            return
        }

        val smokerId = if (smoker.isCloudSmoker) smoker.cloudUserId else "local_${smoker.smokerId}"
        if (smokerId == null) {
            Toast.makeText(this, "Cannot pause this smoker", Toast.LENGTH_SHORT).show()
            return
        }

        val isPaused = pausedSmokerIds.contains(smokerId)

        lifecycleScope.launch {
            if (isPaused) {
                // Resume smoker
                sessionSyncService.resumeSmoker(shareCode, smokerId).fold(
                    onSuccess = {
                        // Set the resumed smoker as the current selected smoker
                        val sections = organizeSmokers()
                        val organizedSmokers = sections.flatMap { it.smokers }
                        val smokerIndex = organizedSmokers.indexOf(smoker)
                        if (smokerIndex >= 0) {
                            binding.spinnerSmoker.setSelection(smokerIndex)
                            selectSmoker(smoker)
                            Log.d(TAG, "🔄 Set resumed smoker as current: ${smoker.name}")
                        }
                        Toast.makeText(this@MainActivity, "${smoker.name} resumed and set as current smoker", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(this@MainActivity, "Failed to resume: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                // Pause smoker
                sessionSyncService.pauseSmoker(shareCode, smokerId).fold(
                    onSuccess = {
                        val currentSelection = binding.spinnerSmoker.selectedItem
                        if (currentSelection == smoker) {
                            moveToNextActiveSmoker()
                        }
                        Toast.makeText(this@MainActivity, "${smoker.name} paused", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(this@MainActivity, "Failed to pause: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // PASTE THE FOLLOWING CODE INSIDE MainActivity, BUT OUTSIDE THE SmokerAdapter

    private fun showThemedDeleteAllDialog() {
        // Simply call deleteAllSmokers which now shows the dialog with options
        deleteAllSmokers()
    }

    private fun createThemedDeleteAllDialogView(dialog: Dialog): View {
        // Root container - full screen with center gravity
        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Main card - CENTERED, not at bottom
        val mainCard = androidx.cardview.widget.CardView(this).apply {
            radius = 16.dpToPx(this@MainActivity).toFloat()
            cardElevation = 8.dpToPx(this@MainActivity).toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER  // CENTER the card
                // Smaller margins for a more compact look
                setMargins(32.dpToPx(this@MainActivity), 0,
                    32.dpToPx(this@MainActivity), 0)
            }
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(this@MainActivity), 20.dpToPx(this@MainActivity),
                20.dpToPx(this@MainActivity), 20.dpToPx(this@MainActivity))
            // Set a fixed width for consistency
            layoutParams = ViewGroup.LayoutParams(
                280.dpToPx(this@MainActivity),  // Fixed width for smaller dialog
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Warning Icon - smaller
        val warningIcon = TextView(this).apply {
            text = "⚠️"
            textSize = 36f  // Reduced from 48f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(warningIcon)

        // Title - smaller
        val titleText = TextView(this).apply {
            text = "DELETE ALL SMOKERS"
            textSize = 18f  // Reduced from 22f
            setTextColor(Color.parseColor("#FFA366"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(titleText)

        // Message - smaller text
        val messageText = TextView(this).apply {
            text = buildString {
                append("Are you sure you want to delete ALL smokers and their activity logs?")
                if (currentShareCode != null) {
                    append("\n\nThis will delete for all participants in the room.")
                }
            }
            textSize = 14f  // Reduced from 16f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(messageText)

        // Green divider line (matching goal dialog)
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2.dpToPx(this@MainActivity)
            ).apply {
                topMargin = 4.dpToPx(this@MainActivity)
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
            setBackgroundColor(Color.parseColor("#3398FB98"))  // Green divider
        }
        contentLayout.addView(divider)

        // Button container - horizontal layout
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Cancel button (left)
        val cancelButton = createThemedDialogButton("Cancel", false, Color.WHITE) {
            animateCardSelection(dialog) {
                // Just dismiss
            }
        }
        cancelButton.layoutParams = LinearLayout.LayoutParams(
            0,
            44.dpToPx(this@MainActivity),
            1f
        ).apply {
            marginEnd = 8.dpToPx(this@MainActivity)
        }
        buttonContainer.addView(cancelButton)

        // Delete All button (right)
        val deleteAllButton = createThemedDialogButton("Delete All", true, Color.parseColor("#FFA366")) {
            animateCardSelection(dialog) {
                deleteAllSmokers()
            }
        }
        deleteAllButton.layoutParams = LinearLayout.LayoutParams(
            0,
            44.dpToPx(this@MainActivity),
            1f
        ).apply {
            marginStart = 8.dpToPx(this@MainActivity)
        }
        buttonContainer.addView(deleteAllButton)

        contentLayout.addView(buttonContainer)
        mainCard.addView(contentLayout)
        rootContainer.addView(mainCard)

        // Add click to dismiss on background
        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                animateCardSelection(dialog) {}
            }
        }

        return rootContainer
    }


    private fun showOfflineCloudSessionDialog() {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        currentDialog = dialog

        val dialogView = createThemedOfflineDialog(dialog)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
            setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }

        dialog.setOnDismissListener {
            currentDialog = null
            Log.d(TAG, "🌐 Offline dialog dismissed")
        }

        // Set initial alpha to 0 for fade-in
        dialogView.alpha = 0f

        dialog.show()

        // Apply fade-in animation with 2-second duration
        performManualFadeIn(dialogView, 2000L)
    }

    private fun createThemedOfflineDialog(dialog: Dialog): View {
        // Root container - full screen with center gravity
        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Main card - CENTERED, not at bottom
        val mainCard = androidx.cardview.widget.CardView(this).apply {
            radius = 16.dpToPx(this@MainActivity).toFloat()
            cardElevation = 8.dpToPx(this@MainActivity).toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER  // CENTER the card
                // Smaller margins for a more compact look
                setMargins(32.dpToPx(this@MainActivity), 0,
                    32.dpToPx(this@MainActivity), 0)
            }
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(this@MainActivity), 20.dpToPx(this@MainActivity),
                20.dpToPx(this@MainActivity), 20.dpToPx(this@MainActivity))
            // Set a fixed width for consistency
            layoutParams = ViewGroup.LayoutParams(
                280.dpToPx(this@MainActivity),  // Fixed width for smaller dialog
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Warning Icon - using WiFi off emoji
        val warningIcon = TextView(this).apply {
            text = "📵"
            textSize = 36f  // Same size as delete dialog
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(warningIcon)

        // Title - neon green instead of orange
        val titleText = TextView(this).apply {
            text = "NO INTERNET CONNECTION"
            textSize = 18f  // Same size as delete dialog
            setTextColor(Color.parseColor("#98FB98"))  // Neon green
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(titleText)

        // Message
        val messageText = TextView(this).apply {
            text = "You need an internet connection to create a cloud session.\n\nYou can still create a local session to track your activities offline."
            textSize = 14f  // Same size as delete dialog
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(messageText)

        // Green divider line (matching delete dialog style)
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2.dpToPx(this@MainActivity)
            ).apply {
                topMargin = 4.dpToPx(this@MainActivity)
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
            setBackgroundColor(Color.parseColor("#3398FB98"))  // Green divider
        }
        contentLayout.addView(divider)

        // Button container - horizontal layout
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Cancel button (left)
        val cancelButton = createThemedDialogButton("Cancel", false, Color.WHITE) {
            animateCardSelection(dialog) {
                // Just dismiss
            }
        }
        cancelButton.layoutParams = LinearLayout.LayoutParams(
            0,
            44.dpToPx(this@MainActivity),
            1f
        ).apply {
            marginEnd = 8.dpToPx(this@MainActivity)
        }
        buttonContainer.addView(cancelButton)

        // Create Local Session button (right) - neon green
        val createLocalButton = createThemedDialogButton("Create Local", true, Color.parseColor("#98FB98")) {
            animateCardSelection(dialog) {
                startLocalSession()
            }
        }
        createLocalButton.layoutParams = LinearLayout.LayoutParams(
            0,
            44.dpToPx(this@MainActivity),
            1f
        ).apply {
            marginStart = 8.dpToPx(this@MainActivity)
        }
        buttonContainer.addView(createLocalButton)

        contentLayout.addView(buttonContainer)
        mainCard.addView(contentLayout)
        rootContainer.addView(mainCard)

        // Add click to dismiss on background
        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                animateCardSelection(dialog) {}
            }
        }

        return rootContainer
    }

    private fun showNoCloudUserPopup() {
        Log.d(TAG, "🏠 Showing no cloud user popup")

        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        currentDialog = dialog

        val dialogView = createThemedNoCloudUserDialog(dialog)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
            setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }

        dialog.setOnDismissListener {
            currentDialog = null
            Log.d(TAG, "🏠 No cloud user dialog dismissed")
        }

        // Set initial alpha to 0 for fade-in
        dialogView.alpha = 0f

        dialog.show()

        // Apply fade-in animation with 2-second duration
        performManualFadeIn(dialogView, 2000L)
    }


    private fun createThemedNoCloudUserDialog(dialog: Dialog): View {
        // Root container - full screen with center gravity
        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Main card - CENTERED, not at bottom
        val mainCard = androidx.cardview.widget.CardView(this).apply {
            radius = 16.dpToPx(this@MainActivity).toFloat()
            cardElevation = 8.dpToPx(this@MainActivity).toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER  // CENTER the card
                // Smaller margins for a more compact look
                setMargins(32.dpToPx(this@MainActivity), 0,
                    32.dpToPx(this@MainActivity), 0)
            }
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(this@MainActivity), 20.dpToPx(this@MainActivity),
                20.dpToPx(this@MainActivity), 20.dpToPx(this@MainActivity))
            // Set a fixed width for consistency
            layoutParams = ViewGroup.LayoutParams(
                280.dpToPx(this@MainActivity),  // Fixed width for smaller dialog
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // House Icon
        val houseIcon = TextView(this).apply {
            text = "🏠"
            textSize = 36f  // Same size as offline dialog
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(houseIcon)

        // Title - neon green like offline dialog
        val titleText = TextView(this).apply {
            text = "NO CLOUD USER"
            textSize = 18f  // Same size as offline dialog
            setTextColor(Color.parseColor("#98FB98"))  // Neon green
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(titleText)

        // Message
        val messageText = TextView(this).apply {
            text = "There is currently no cloud user added to the top left list.\n\nTo have an online sesh, you need to add a google account"
            textSize = 14f  // Same size as offline dialog
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(messageText)

        // Green divider line (matching offline dialog style)
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2.dpToPx(this@MainActivity)
            ).apply {
                topMargin = 4.dpToPx(this@MainActivity)
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
            setBackgroundColor(Color.parseColor("#3398FB98"))  // Green divider
        }
        contentLayout.addView(divider)

        // Button container - horizontal layout
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Start Local Sesh button (left) - USING createThemedDialogButton for consistency
        val localButton = createThemedDialogButton("Start Local Sesh", false, Color.WHITE) {
            animateCardSelection(dialog) {
                startLocalSession()
            }
        }
        localButton.layoutParams = LinearLayout.LayoutParams(
            0,
            44.dpToPx(this@MainActivity),
            1f
        ).apply {
            marginEnd = 8.dpToPx(this@MainActivity)
        }
        buttonContainer.addView(localButton)

        // Create Google Account button (right) - neon green with image background effect
        val googleButton = createThemedDialogButton("Create Google Account", true, Color.parseColor("#98FB98")) {
            animateCardSelection(dialog) {
                // Show the add smoker dialog which will handle Google sign-in
                addSmokerDialog.show()
            }
        }
        googleButton.layoutParams = LinearLayout.LayoutParams(
            0,
            44.dpToPx(this@MainActivity),
            1f
        ).apply {
            marginStart = 8.dpToPx(this@MainActivity)
        }
        buttonContainer.addView(googleButton)

        contentLayout.addView(buttonContainer)
        mainCard.addView(contentLayout)
        rootContainer.addView(mainCard)

        // Add click to dismiss on background
        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                animateCardSelection(dialog) {}
            }
        }

        return rootContainer
    }



    private fun createThemedDialogButton(text: String, isPrimary: Boolean, color: Int, onClick: () -> Unit): View {
        val buttonContainer = androidx.cardview.widget.CardView(this).apply {
            radius = 20.dpToPx(this@MainActivity).toFloat()
            cardElevation = if (isPrimary) 4.dpToPx(this@MainActivity).toFloat() else 0f
            setCardBackgroundColor(
                if (isPrimary) color
                else Color.parseColor("#33FFFFFF")
            )

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx(this@MainActivity)
            ).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }

            isClickable = true
            isFocusable = true
        }

        // Create a FrameLayout to hold background image and text
        val contentFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Image view for pressed state (initially hidden)
        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.button_pressed_background)
            visibility = View.GONE
        }

        // Text on top
        val buttonText = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(
                if (isPrimary) Color.parseColor("#424242")
                else Color.WHITE
            )
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Add views in correct order
        contentFrame.addView(imageView)
        contentFrame.addView(buttonText)
        buttonContainer.addView(contentFrame)

        // Store original colors
        val originalBackgroundColor = if (isPrimary) color else Color.parseColor("#33FFFFFF")
        val originalTextColor = if (isPrimary) Color.parseColor("#424242") else Color.WHITE

        // Handle touch events
        buttonContainer.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Show image, hide solid color
                    buttonContainer.setCardBackgroundColor(Color.TRANSPARENT)
                    imageView.visibility = View.VISIBLE

                    // Change text color to white for both button types when pressed
                    buttonText.setTextColor(Color.WHITE)
                    buttonText.setShadowLayer(4f, 2f, 2f, Color.BLACK)
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // Hide image, restore solid color
                    imageView.visibility = View.GONE
                    buttonContainer.setCardBackgroundColor(originalBackgroundColor)

                    // Restore original text color
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

    private fun deleteAllSmokers() {
        Log.d(TAG, "🗑️🔴 === DELETE ALL SMOKERS START ===")
        Log.d(TAG, "🗑️🔴 Total smokers to delete: ${smokers.size}")

        // Show dialog with keep data option
        showDeleteAllDialog { keepData ->
            Log.d(TAG, "🗑️🔴 User choice - Keep data: $keepData")

            val shareCode = currentShareCode
            val currentUserId = authManager.getCurrentUserId()

            Log.d(TAG, "🗑️🔴 Current share code: $shareCode")
            Log.d(TAG, "🗑️🔴 Current user ID: $currentUserId")

            // If we're in a cloud room, clear the shared smokers from the room first
            if (shareCode != null) {
                Log.d(TAG, "🗑️🔴 In cloud room - clearing shared smokers from room first")

                lifecycleScope.launch {
                    // Clear all smokers from the room
                    sessionSyncService.deleteAllSmokersFromRoom(shareCode, currentUserId ?: "unknown").fold(
                        onSuccess = {
                            Log.d(TAG, "🗑️🔴 ✅ Successfully cleared smokers from room")
                            // Now proceed with local deletion
                            proceedWithLocalDeletion(keepData)
                        },
                        onFailure = { error ->
                            Log.e(TAG, "🗑️🔴 ❌ Failed to clear smokers from room: ${error.message}")
                            // Still proceed with local deletion
                            proceedWithLocalDeletion(keepData)
                        }
                    )
                }
            } else {
                // Not in a room, just do local deletion
                proceedWithLocalDeletion(keepData)
            }
        }
    }

    private fun proceedWithLocalDeletion(keepData: Boolean) {
        Log.d(TAG, "🗑️🔴 Proceeding with local deletion - Keep data: $keepData")

        val allSmokersToDelete = smokers.toList()

        lifecycleScope.launch(Dispatchers.IO) {
            var totalLogsDeleted = 0
            var totalLogsKept = 0

            allSmokersToDelete.forEach { smoker ->
                Log.d(TAG, "🗑️🔴 Processing ${smoker.name}")

                if (!keepData) {
                    // Delete all activity logs
                    val logs = repo.getLogsForSmoker(smoker.smokerId)
                    logs.forEach { log ->
                        repo.delete(log)
                    }
                    totalLogsDeleted += logs.size
                    Log.d(TAG, "🗑️🔴 Deleted ${logs.size} logs for ${smoker.name}")
                } else {
                    val logCount = repo.getLogsForSmoker(smoker.smokerId).size
                    totalLogsKept += logCount
                    Log.d(TAG, "🗑️🔴 Keeping $logCount logs for ${smoker.name}")
                }

                // Always delete the smoker entity
                repo.deleteSmoker(smoker)
                Log.d(TAG, "🗑️🔴 ✅ Deleted smoker: ${smoker.name}")
            }

            withContext(Dispatchers.Main) {
                // Sign out if needed
                val hasCloudUser = allSmokersToDelete.any { it.cloudUserId == authManager.getCurrentUserId() }
                if (hasCloudUser) {
                    Log.d(TAG, "🗑️🔴 Signing out user")
                    authManager.signOut()
                }

                val message = if (keepData) {
                    "Deleted ${allSmokersToDelete.size} smokers (kept $totalLogsKept activity logs)"
                } else {
                    "Deleted ${allSmokersToDelete.size} smokers and $totalLogsDeleted activity logs"
                }

                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                Log.d(TAG, "🗑️🔴 === DELETE ALL SMOKERS COMPLETE ===")
            }
        }
    }



    private fun deleteSmokersWithIndividualConfirmation(
        localSmokers: List<Smoker>,
        cloudSmokers: List<Smoker>,
        currentIndex: Int
    ) {
        Log.d(TAG, "🗑️🔴 deleteSmokersWithIndividualConfirmation - Index: $currentIndex of ${localSmokers.size} local smokers")

        if (currentIndex < localSmokers.size) {
            val smoker = localSmokers[currentIndex]
            Log.d(TAG, "🗑️🔴 Showing confirmation for local smoker: ${smoker.name}")

            showThemedDeleteConfirmationForSmoker(smoker) { confirmed ->
                if (confirmed) {
                    Log.d(TAG, "🗑️🔴 User confirmed deletion of ${smoker.name}")

                    lifecycleScope.launch(Dispatchers.IO) {
                        // Delete this smoker
                        val logs = repo.getLogsForSmoker(smoker.smokerId)
                        logs.forEach { log ->
                            repo.delete(log)
                        }
                        repo.deleteSmoker(smoker)
                        Log.d(TAG, "🗑️🔴 Deleted ${smoker.name} and ${logs.size} logs")

                        withContext(Dispatchers.Main) {

                            // Continue to next smoker
                            deleteSmokersWithIndividualConfirmation(localSmokers, cloudSmokers, currentIndex + 1)
                        }
                    }
                } else {
                    Log.d(TAG, "🗑️🔴 User cancelled deletion of ${smoker.name}, continuing to next")
                    // Skip this smoker and continue to next
                    deleteSmokersWithIndividualConfirmation(localSmokers, cloudSmokers, currentIndex + 1)
                }
            }
        } else {
            // Done with local smokers, now delete cloud smokers without confirmation
            Log.d(TAG, "🗑️🔴 Done with local smokers, deleting ${cloudSmokers.size} cloud smokers")

            lifecycleScope.launch(Dispatchers.IO) {
                cloudSmokers.forEach { smoker ->
                    Log.d(TAG, "🗑️🔴 Deleting cloud smoker: ${smoker.name}")

                    // Delete logs but keep minimal activity record
                    val logs = repo.getLogsForSmoker(smoker.smokerId)
                    logs.forEach { log ->
                        repo.delete(log)
                    }

                    // Delete the smoker
                    repo.deleteSmoker(smoker)
                    Log.d(TAG, "🗑️🔴 Deleted cloud smoker ${smoker.name}")
                }

                withContext(Dispatchers.Main) {


                    // Sign out if needed
                    val hasCloudUser = cloudSmokers.any { it.cloudUserId == authManager.getCurrentUserId() }
                    if (hasCloudUser) {
                        Log.d(TAG, "🗑️🔴 Signing out user")
                        authManager.signOut()
                        Toast.makeText(this@MainActivity, "Deleted all smokers and signed out", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Deleted all smokers", Toast.LENGTH_SHORT).show()
                    }

                    Log.d(TAG, "🗑️🔴 === DELETE ALL SMOKERS COMPLETE ===")
                }
            }
        }
    }

    private fun startNetworkMonitoring() {
        // Check network status periodically
        networkCheckHandler = Handler(Looper.getMainLooper())
        networkCheckRunnable = object : Runnable {
            override fun run() {
                checkNetworkAndSyncStatus()
                networkCheckHandler?.postDelayed(this, 5000) // Check every 5 seconds
            }
        }
        networkCheckHandler?.post(networkCheckRunnable!!)
    }

    private fun stopNetworkMonitoring() {
        networkCheckRunnable?.let { runnable ->
            networkCheckHandler?.removeCallbacks(runnable)
        }
        networkCheckHandler = null
        networkCheckRunnable = null
    }

    private fun checkNetworkAndSyncStatus() {
        // Check network availability
        val wasNetworkAvailable = isNetworkAvailable
        isNetworkAvailable = authManager.isNetworkAvailable()

        // Check Firebase auth status
        val isAuthenticated = authManager.isSignedIn

        // Update sync status for all cloud smokers
        smokers.filter { it.isCloudSmoker }.forEach { smoker ->
            val smokerId = smoker.cloudUserId ?: return@forEach

            val newStatus = when {
                !isNetworkAvailable -> SyncStatus.OFFLINE
                !isAuthenticated -> SyncStatus.OFFLINE
                smoker.needsSync -> SyncStatus.SYNCING
                else -> SyncStatus.SYNCED
            }

            val oldStatus = smokerSyncStatus[smokerId]
            if (oldStatus != newStatus) {
                smokerSyncStatus[smokerId] = newStatus
                // Refresh the adapter if status changed
                runOnUiThread {
                    smokerAdapterNew.notifyDataSetChanged()
                }
            }
        }

        // Log status change
        if (wasNetworkAvailable != isNetworkAvailable) {
            Log.d(TAG, "🌐 Network status changed: ${if (isNetworkAvailable) "ONLINE" else "OFFLINE"}")
        }
    }

    private fun getSyncStatusColor(status: SyncStatus): Int {
        return when (status) {
            SyncStatus.SYNCED -> ContextCompat.getColor(this, R.color.my_light_primary) // Neon green
            SyncStatus.SYNCING -> ContextCompat.getColor(this, R.color.neon_orange) // Neon orange
            SyncStatus.OFFLINE -> ContextCompat.getColor(this, R.color.neon_red) // Neon red
        }
    }

    private fun updateSyncStatusDot(dotView: View?, smoker: Smoker) {
        if (dotView == null || !smoker.isCloudSmoker) {
            dotView?.visibility = View.GONE
            return
        }

        val smokerId = smoker.cloudUserId ?: return
        val status = smokerSyncStatus[smokerId] ?: SyncStatus.OFFLINE

        dotView.visibility = View.VISIBLE
        val drawable = dotView.background
        if (drawable is android.graphics.drawable.GradientDrawable) {
            drawable.setColor(getSyncStatusColor(status))
        } else {
            dotView.setBackgroundColor(getSyncStatusColor(status))
        }
    }

    private fun showThemedDeleteConfirmationForSmoker(smoker: Smoker, onResult: (Boolean) -> Unit) {
        // This is now handled by the new showDeleteSmokerDialog
        // Just redirect to the new function
        onResult(true) // Default to confirmed since the new dialog handles the choice
    }

    // Offline queue management functions
    private fun saveOfflineQueue() {
        val gson = com.google.gson.Gson()
        val json = gson.toJson(offlineActivityQueue)
        prefs.edit().putString("offline_activity_queue", json).apply()
        Log.d(TAG, "💾 OFFLINE_QUEUE: Saved ${offlineActivityQueue.size} activities to prefs")
    }

    private fun loadOfflineQueue() {
        val json = prefs.getString("offline_activity_queue", null) ?: return
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<OfflineActivity>>() {}.type
        try {
            val loaded = gson.fromJson<List<OfflineActivity>>(json, type)
            offlineActivityQueue.clear()
            offlineActivityQueue.addAll(loaded)
            Log.d(TAG, "💾 OFFLINE_QUEUE: Loaded ${offlineActivityQueue.size} activities from prefs")
        } catch (e: Exception) {
            Log.e(TAG, "💾 OFFLINE_QUEUE: Error loading queue", e)
        }
    }

    private fun addToOfflineQueue(activity: OfflineActivity) {
        offlineActivityQueue.add(activity)
        saveOfflineQueue()
        Log.d(TAG, "📴 OFFLINE_QUEUE: Added activity - ${activity.activityType} for ${activity.smokerName}")
        Log.d(TAG, "📴 OFFLINE_QUEUE: Queue size now: ${offlineActivityQueue.size}")

        // Show toast to user
        runOnUiThread {
            Toast.makeText(this, "Activity saved offline, will sync when online", Toast.LENGTH_LONG).show()
        }
    }

    private fun startOfflineSyncChecker() {
        syncCheckHandler = Handler(Looper.getMainLooper())
        syncCheckRunnable = object : Runnable {
            override fun run() {
                if (isNetworkAvailable && offlineActivityQueue.isNotEmpty()) {
                    Log.d(TAG, "🔄 SYNC_CHECK: Network available, attempting to sync ${offlineActivityQueue.size} offline activities")
                    processOfflineQueue()
                }
                syncCheckHandler?.postDelayed(this, SYNC_RETRY_INTERVAL)
            }
        }
        syncCheckHandler?.post(syncCheckRunnable!!)
        Log.d(TAG, "🔄 SYNC_CHECK: Started offline sync checker")
    }

    private fun stopOfflineSyncChecker() {
        syncCheckRunnable?.let { runnable ->
            syncCheckHandler?.removeCallbacks(runnable)
        }
        syncCheckHandler = null
        syncCheckRunnable = null
        Log.d(TAG, "🔄 SYNC_CHECK: Stopped offline sync checker")
    }

    private fun processOfflineQueue() {
        if (offlineActivityQueue.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastSyncAttempt < 5000) {
            Log.d(TAG, "🔄 SYNC_QUEUE: Skipping sync, too soon since last attempt")
            return
        }

        lastSyncAttempt = now
        val toSync = offlineActivityQueue.toList() // Create a copy to avoid concurrent modification

        Log.d(TAG, "🔄 SYNC_QUEUE: Processing ${toSync.size} offline activities")

        lifecycleScope.launch {
            for (activity in toSync) {
                Log.d(TAG, "🔄 SYNC_ITEM: Syncing ${activity.activityType} for ${activity.smokerName}")

                sessionSyncService.addActivityToRoom(
                    shareCode = activity.shareCode,
                    smokerUid = activity.smokerUid,
                    smokerName = activity.smokerName,
                    activityType = activity.activityType,
                    timestamp = activity.timestamp,
                    deviceId = activity.deviceId
                ).fold(
                    onSuccess = {
                        Log.d(TAG, "✅ SYNC_SUCCESS: ${activity.activityType} for ${activity.smokerName}")
                        offlineActivityQueue.remove(activity)
                        saveOfflineQueue()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "❌ SYNC_FAIL: ${activity.activityType} - ${error.message}")

                        // Update retry count
                        val index = offlineActivityQueue.indexOf(activity)
                        if (index >= 0) {
                            val updated = activity.copy(retryCount = activity.retryCount + 1)
                            if (updated.retryCount >= updated.maxRetries) {
                                Log.e(TAG, "❌ SYNC_FAIL: Max retries reached, removing from queue")
                                offlineActivityQueue.removeAt(index)
                            } else {
                                offlineActivityQueue[index] = updated
                            }
                            saveOfflineQueue()
                        }
                    }
                )

                // Small delay between sync attempts
                delay(500)
            }

            if (offlineActivityQueue.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "All offline activities synced!", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "✅ SYNC_COMPLETE: All offline activities synced")
            } else {
                Log.d(TAG, "⚠️ SYNC_PARTIAL: ${offlineActivityQueue.size} activities still pending")
            }
        }
    }

    private fun setActivityButtonHeights(jointButton: Button, coneButton: Button, bowlButton: Button, heightPx: Int) {
        val jointParams = jointButton.layoutParams
        jointParams.height = heightPx
        jointButton.layoutParams = jointParams

        val coneParams = coneButton.layoutParams
        coneParams.height = heightPx
        coneButton.layoutParams = coneParams

        val bowlParams = bowlButton.layoutParams
        bowlParams.height = heightPx
        bowlButton.layoutParams = bowlParams
    }

    // Debug function for offline queue
    private fun debugOfflineQueue() {
        Log.d(TAG, "📱 === OFFLINE QUEUE DEBUG ===")
        Log.d(TAG, "📱 Queue size: ${offlineActivityQueue.size}")
        Log.d(TAG, "📱 Network available: $isNetworkAvailable")
        Log.d(TAG, "📱 Current share code: $currentShareCode")

        offlineActivityQueue.forEachIndexed { index, activity ->
            Log.d(TAG, "📱 [$index] ${activity.activityType} - ${activity.smokerName}")
            Log.d(TAG, "📱      Timestamp: ${Date(activity.timestamp)}")
            Log.d(TAG, "📱      Retries: ${activity.retryCount}/${activity.maxRetries}")
            Log.d(TAG, "📱      ShareCode: ${activity.shareCode}")
        }
        Log.d(TAG, "📱 === END DEBUG ===")
    }

}

// Extension function for dp to px conversion
fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}