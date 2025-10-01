package com.vibecode.cloudcounter

import android.Manifest
import android.Manifest.permission.ACCESS_COARSE_LOCATION
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
import android.graphics.drawable.GradientDrawable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vibecode.cloudcounter.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Deferred
import kotlin.math.floor
import android.widget.Button
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.text.InputFilter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import android.graphics.Color
import android.content.res.ColorStateList
import android.widget.ImageView
import com.google.android.material.tabs.TabLayout
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.widget.GridLayout
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.content.res.ResourcesCompat
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import kotlin.random.Random
import androidx.cardview.widget.CardView
import android.view.WindowManager
import androidx.transition.Fade
import androidx.transition.TransitionManager
import kotlin.math.min
import android.graphics.Typeface
import java.util.UUID
import java.util.Date

private const val INITIAL_BACKOFF_MS = 5_000L
private const val MIN_QUEUE_POLL_MS = 500L
private const val NETWORK_RETRY_MS = 15_000L
private const val MAX_BACKOFF_MS = 120_000L
private const val SYNC_RETRY_SPACING_MS = 250L

class MainActivity : AppCompatActivity() {

    private lateinit var smokerManager: SmokerManager
    private lateinit var smokerAdapterNew: SmokerAdapter
    private lateinit var ratioManager: SmokeRatioManager

    companion object {
        private const val TAG = "MainActivity"
        private const val GIANT_COUNTER_REQUEST_CODE = 1001
        const val ACTION_CUSTOM_ACTIVITIES_CHANGED = "com.vibecode.cloudcounter.CUSTOM_ACTIVITIES_CHANGED"
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
    private var confettiEnabled = true  // Track confetti animation state


    private var pendingBowlQuantity = 1

    private val fontList = listOf(
        R.font.bitcount_prop_double,
        R.font.exile,
        R.font.modak,
        R.font.oi,
        R.font.rubik_glitch,
        R.font.sankofa_display,
        R.font.silkscreen,
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
    private lateinit var onboardingPrefs: SharedPreferences
    private lateinit var onboardingController: OnboardingFlowController
    lateinit var customActivityManager: CustomActivityManager

    private var currentDialog: Dialog? = null

    private var addSmokerShimmerAnimation: ShimmerTextAnimation? = null

    private val repo by lazy { (application as CloudCounterApplication).repository }
    private val statsVM by lazy { ViewModelProvider(this).get(StatsViewModel::class.java) }
    private val sessionStatsVM by lazy {
        ViewModelProvider(this, SessionStatsViewModelFactory()).get(SessionStatsViewModel::class.java)
    }

    private var currentSmokerIndex = 0
    private var sharedActiveSmokerId: String? = null
    private var isApplyingRemoteSpinnerUpdate = false
    private var isApplyingRemoteAutoMode = false
    private var isUpdatingAutoModeToFirestore = false
    private var lastModeToggleTime = 0L
    private var lastLocalAutoModeValue: Boolean? = null  // Track what we last set locally

    private var processedActivityIds = mutableSetOf<String>()

    private var lastSelectedActivityButton: Button? = null
    private val customActivityButtons = mutableListOf<Button>()
    private val coreActivityButtons = mutableListOf<Button>()

    // Track the most recent activity selection so GiantCounter launches with correct context
    private var lastSelectedActivityType: ActivityType = ActivityType.CONE
    private var lastSelectedCustomActivityId: String? = null
    private var lastSelectedCustomActivityName: String? = null

    // Cloud functionality
    private lateinit var authManager: FirebaseAuthManager
    private lateinit var cloudSyncService: CloudSyncService
    private lateinit var sessionSyncService: SessionSyncService
    private var currentRoom: RoomData? = null

    private var sessionDialogEffects: CloudSessionDialogEffects? = null

    private var timersVisible = true

    private var isInFirstConeDialog = false
    private var firstConePromptShown = false

    private var notificationsEnabled = true  // Track notification state
    private var isAddSmokerDialogShown = false  // Track if add smoker dialog was already shown
    private var sequentialDialogCallback: (() -> Unit)? = null  // Callback for sequential dialogs

    private lateinit var addSmokerDialog: AddSmokerDialog
    private lateinit var passwordDialog: PasswordDialog

    private var smokers: List<Smoker> = emptyList()

    private lateinit var rewindReceiver: BroadcastReceiver
    private lateinit var skipReceiver: BroadcastReceiver

    private lateinit var smokerUpdateReceiver: BroadcastReceiver
    private lateinit var undoReceiver: BroadcastReceiver
    private lateinit var deletionReceiver: BroadcastReceiver
    private lateinit var autoAdvanceReceiver: BroadcastReceiver

    private lateinit var stashViewModel: StashViewModel
    private var stashIntegration: StashIntegration? = null
    
    // Turn notification manager
    private lateinit var turnNotificationManager: TurnNotificationManager

    private var rewindOffset = 0L  // Total milliseconds rewound
    private val REWIND_AMOUNT_MS = 10000L  // 10 seconds per rewind

    private var lastRoundButtonClickTime = 0L
    private val ROUND_BUTTON_DEBOUNCE_MS = 300L

    private var actualLastLogTime = 0L  // The actual last activity timestamp (not affected by rewind)
    private var lastLogTimeBeforeRewind = 0L  // Store the last log time when we start rewinding
    private var lastConeTimestamp = 0L  // Track last cone timestamp for live timer
    private var lastJointTimestamp = 0L  // Track last joint timestamp for live timer
    private var lastBowlTimestamp = 0L  // Track last bowl timestamp for live timer
    private var lastCigaretteTimestamp = 0L  // Track last cigarette timestamp for stats
    private val lastCustomActivityTimestamps = mutableMapOf<String, Long>()  // Track last timestamp for each custom activity by ID

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
    private var pendingActivityType: ActivityType? = null
    private var pendingCustomActivity: CustomActivity? = null  // Store activity type when showing no session popup   //
    private var isUpdatingRoundsLocally = false
    private var localRoundsUpdateTime = 0L
    // Cached latest room snapshot so notifications can reflect remote activity immediately
    private var latestRoomData: RoomData? = null

    // properties for offline activity queueing
    private val offlineActivityQueue = mutableListOf<OfflineActivity>()
    private var syncCheckHandler: Handler? = null
    private var syncCheckRunnable: Runnable? = null
    

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
    private var pendingResumeSummary: SessionSummary? = null // For safe end->resume handoff

    // Activity queue for handling rapid clicks
    data class QueuedActivity(
        val type: ActivityType,
        val timestamp: Long,
        val smoker: Smoker,
        val customActivity: CustomActivity? = null
    )
    
    private val activityQueue = mutableListOf<QueuedActivity>()
    private val queueLock = Any()
    private var isProcessingQueue = false
    private var isOptimisticMode = false // Prevent DB overwrites during batch processing
    private var justRotatedFromUI = false // Prevent double rotation from room sync
    
    // Optimistic UI update tracking
    private val optimisticCounts = mutableMapOf<String, MutableMap<ActivityType, Int>>()

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

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("FIRST_LAUNCH_FLOW", "🔔 Notification permission result: $granted")
            if (!granted) {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
            // Notify the onboarding controller if initialized
            if (::onboardingController.isInitialized) {
                onboardingController.onNotificationPermissionResult(granted)
            }
        }
    
    private val locationPermissionLauncher = 
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("FIRST_LAUNCH_FLOW", "📍 Location permission result: $granted")
            // Notify the onboarding controller if initialized
            if (::onboardingController.isInitialized) {
                onboardingController.onLocationPermissionResult(granted)
            }
        }
    
    private val cameraPermissionLauncher = 
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("FIRST_LAUNCH_FLOW", "📷 Camera permission result: $granted")
            // Notify the onboarding controller if initialized
            if (::onboardingController.isInitialized) {
                onboardingController.onCameraPermissionResult(granted)
            }
        }
    
    private val audioPermissionLauncher = 
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("FIRST_LAUNCH_FLOW", "🎤 Audio permission result: $granted")
            // Notify the onboarding controller if initialized
            if (::onboardingController.isInitialized) {
                onboardingController.onAudioPermissionResult(granted)
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
                // Update standard activity timers
                var updatedStats = current.copy(
                    sinceLastGapMs = if (lastConeTimestamp > 0) (rewindedNow - lastConeTimestamp).coerceAtLeast(0) else 0L,
                    sinceLastJointMs = if (lastJointTimestamp > 0) (rewindedNow - lastJointTimestamp).coerceAtLeast(0) else 0L,
                    sinceLastBowlMs = if (lastBowlTimestamp > 0) (rewindedNow - lastBowlTimestamp).coerceAtLeast(0) else 0L
                )
                
                // Update custom activity timers using actual timestamps
                val updatedCustomStats = current.customActivityGroupStats.mapValues { (customId, stat) ->
                    if (stat.lastSmokerName != null && stat.total > 0) {
                        // Use the actual timestamp we're tracking
                        val lastTimestamp = lastCustomActivityTimestamps[customId]
                        if (lastTimestamp != null && lastTimestamp > 0) {
                            val newSinceLastMs = (rewindedNow - lastTimestamp).coerceAtLeast(0)
                            
                            if (stat.sinceLastMs != newSinceLastMs) {
                                Log.d(TAG, "⏰ CUSTOM_TIMER: ${stat.activityName} (ID: $customId) timer update")
                                Log.d(TAG, "⏰ CUSTOM_TIMER:   Timestamp: $lastTimestamp")
                                Log.d(TAG, "⏰ CUSTOM_TIMER:   Old sinceLastMs: ${stat.sinceLastMs}ms (${stat.sinceLastMs/1000}s)")
                                Log.d(TAG, "⏰ CUSTOM_TIMER:   New sinceLastMs: ${newSinceLastMs}ms (${newSinceLastMs/1000}s)")
                            }
                            
                            stat.copy(sinceLastMs = newSinceLastMs)
                        } else {
                            // No timestamp tracked yet, keep original
                            Log.d(TAG, "⏰ CUSTOM_TIMER: No timestamp for ${stat.activityName} (ID: $customId), keeping original")
                            stat
                        }
                    } else {
                        stat
                    }
                }
                
                // Apply the updated custom activity stats
                updatedStats = updatedStats.copy(customActivityGroupStats = updatedCustomStats)
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
        var selectedRatio: SmokeRatio? = null  // Move this up for broader scope

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
                            logBowlsWithQuantity(quantity, selectedRatio)
                        }
                    }
                }
            }
            gridLayout.addView(button)
        }

        contentLayout.addView(gridLayout)
        
        // Add ratio dropdown
        val ratioSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16.dpToPx(this@MainActivity)
            }
        }
        
        val ratioLabel = TextView(this).apply {
            text = "Ratio:"
            textSize = 14f
            setTextColor(Color.parseColor("#B0B0B0"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dpToPx(this@MainActivity)
            }
        }
        ratioSection.addView(ratioLabel)
        
        // Create dropdown button
        val lastSelectedBowlRatioId = prefs.getString("last_bowl_ratio_id", null)
        val bowlRatios = ratioManager.getRatiosForType(SmokeRatio.RatioType.BOWL)
        
        // Try to find last selected ratio
        if (lastSelectedBowlRatioId != null) {
            selectedRatio = bowlRatios.firstOrNull { it.id == lastSelectedBowlRatioId }
        }
        // If no last selected or not found, use the one marked as selected
        if (selectedRatio == null) {
            selectedRatio = bowlRatios.firstOrNull { it.isSelected }
        }
        
        val dropdownButton = TextView(this).apply {
            text = selectedRatio?.name ?: "Normal ratio"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3A3A3A"))
            setPadding(12.dpToPx(this@MainActivity), 10.dpToPx(this@MainActivity),
                12.dpToPx(this@MainActivity), 10.dpToPx(this@MainActivity))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx(this@MainActivity)
            )
            
            // Add dropdown arrow
            setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0)
            compoundDrawablePadding = 8.dpToPx(this@MainActivity)
            
            setOnClickListener {
                // Show ratio selection dropdown
                showRatioDropdown(this, bowlRatios, selectedRatio) { ratio ->
                    selectedRatio = ratio
                    text = ratio?.name ?: "Normal ratio"
                    // Save selected ratio ID
                    if (ratio != null) {
                        prefs.edit().putString("last_bowl_ratio_id", ratio.id).apply()
                    } else {
                        prefs.edit().remove("last_bowl_ratio_id").apply()
                    }
                }
            }
        }
        ratioSection.addView(dropdownButton)
        
        contentLayout.addView(ratioSection)
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

    // Add this function to show bowl quantity dialog when long-pressing cone confirmation
    private fun showBowlQuantityDialogForCone(smoker: Smoker, stashSource: StashSource, coneTimestamp: Long) {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

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
            text = "Add activities"
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

        // Create number options
        val numbers = listOf("1", "2", "3", "More")

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
                            showBowlQuantityInputDialogForCone(smoker, stashSource, coneTimestamp)
                        }
                        else -> {
                            val quantity = number.toInt()
                            // Save the selected quantity to preferences
                            prefs.edit().putInt("last_bowl_quantity", quantity).apply()
                            dialog.dismiss()
                            logBowlsAndConeWithQuantity(quantity, smoker, stashSource, coneTimestamp)
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

    // Add this function for manual number input for cone scenario
    private fun showBowlQuantityInputDialogForCone(smoker: Smoker, stashSource: StashSource, coneTimestamp: Long) {
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
            text = "Add activities"
            setTextColor(Color.parseColor("#424242"))
            setBackgroundColor(Color.parseColor("#98FB98"))
            setOnClickListener {
                val quantity = input.text.toString().toIntOrNull() ?: 1
                if (quantity > 0) {
                    // Save the quantity to preferences
                    prefs.edit().putInt("last_bowl_quantity", quantity).apply()

                    dialog.dismiss()
                    logBowlsAndConeWithQuantity(quantity, smoker, stashSource, coneTimestamp)
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

    // Add this function to log multiple bowls and then the cone
    private fun logBowlsAndConeWithQuantity(quantity: Int, smoker: Smoker, stashSource: StashSource, coneTimestamp: Long) {
        if (quantity <= 0) return

        Log.d(TAG, "🎯 Logging $quantity bowls and cone for ${smoker.name}")
        confettiHelper.showSuccessConfetti()

        lifecycleScope.launch {
            val originalAutoMode = isAutoMode
            isAutoMode = false

            // Update bulk bowl count in session stats for cone auto-calculation
            if (quantity > 1) {
                val currentStats = sessionStatsVM.groupStats.value ?: GroupStats()
                sessionStatsVM.updateGroupStats(currentStats.copy(bulkBowlAdditions = quantity))
                Log.d(TAG, "🎯 Updated bulk bowl additions: $quantity bowls")
            }

            // Add the bowls first
            for (i in 0 until quantity) {
                val bowlTimestamp = coneTimestamp - ((quantity - i) * 100) // Bowls before cone
                Log.d(TAG, "🎯 🍶 Adding bowl ${i + 1}/$quantity for ${smoker.name}")
                proceedWithLogHitWithSourceAndSmoker(ActivityType.BOWL, bowlTimestamp, stashSource, smoker)
                delay(50) // Small delay between bowls
            }

            // Add the cone
            delay(200)
            Log.d(TAG, "🎯 🌿 Adding cone for ${smoker.name}")
            proceedWithLogHitWithSourceAndSmoker(ActivityType.CONE, coneTimestamp, stashSource, smoker)

            // Restore auto mode
            isAutoMode = originalAutoMode
            Log.d(TAG, "🎯 ↻ Restored auto mode to: $originalAutoMode")

            withContext(Dispatchers.Main) {
                if (currentShareCode == null) {
                    refreshLocalSessionStatsIfNeeded()
                }
                sessionStatsVM.refreshTimer()
                stashViewModel.onActivityLogged(ActivityType.CONE)
                
                // CRITICAL FIX: Manually trigger auto-advance after bowl+cone combo
                if (originalAutoMode && smokers.isNotEmpty()) {
                    Log.d(TAG, "🎯 ➡️ Manually advancing smoker after bowl+cone combo")
                    handler.postDelayed({
                        moveToNextActiveSmoker()
                        Log.d(TAG, "🎯 ✅ Auto-advance completed after bowl+cone combo")
                    }, 300) // Small delay to ensure all operations complete
                }
            }
        }
    }

    // Handle cone to cigarette tracking through bowl conversion
    private suspend fun handleConeToBasedOnBowlRatio(
        timestamp: Long,
        smoker: Smoker,
        payerStashOwnerId: String?
    ) {
        // Get the bowl ratio if one is selected
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastSelectedBowlRatioId = prefs.getString("last_bowl_ratio_id", null)
        
        val bowlRatio = if (lastSelectedBowlRatioId != null) {
            val ratios = ratioManager.getRatiosForType(SmokeRatio.RatioType.BOWL)
            ratios.firstOrNull { it.id == lastSelectedBowlRatioId }
                ?: ratios.firstOrNull { it.isSelected }
        } else {
            val ratios = ratioManager.getRatiosForType(SmokeRatio.RatioType.BOWL)
            ratios.firstOrNull { it.isSelected }
        }
        
        if (bowlRatio == null) {
            Log.d(TAG, "🚬 No bowl ratio selected, skipping cone cigarette tracking")
            return
        }
        
        Log.d(TAG, "🚬 Bowl ratio: ${bowlRatio.name}, cigarettesPerBowl=${bowlRatio.cigarettesPerSmoke}")
        
        // Get the cone/bowl conversion ratio
        val stashViewModel = ViewModelProvider(this).get(StashViewModel::class.java)
        val ratios = stashViewModel.ratios.value
        // Calculate cones per bowl from grams: bowlGrams / coneGrams
        val conesPerBowl = if (ratios != null && ratios.coneGrams > 0) {
            val calculated = ratios.bowlGrams / ratios.coneGrams
            Log.d(TAG, "🚬 Cone/Bowl calculation: ${ratios.bowlGrams}g bowl / ${ratios.coneGrams}g cone = $calculated cones/bowl")
            calculated // Keep as float for accurate fraction calculation
        } else {
            Log.d(TAG, "🚬 No ratios found, using default 4 cones = 1 bowl")
            4.0 // Default 4 cones = 1 bowl
        }
        
        // When custom bowl ratio is selected, cones should track cigarettes directly
        // Instead of cone -> bowl -> cigarette, we go cone -> cigarette
        val cigarettesPerCone = bowlRatio.cigarettesPerSmoke / conesPerBowl
        
        // Track cigarette fraction directly for cones
        val fractionBefore = ratioManager.getCigaretteFraction(smoker.smokerId)
        var cigaretteFraction = fractionBefore
        Log.d(TAG, "🚬 Cone tracking: ${smoker.name} - previous cigarette fraction: $cigaretteFraction")
        
        // Add the cigarette fraction for this cone
        cigaretteFraction += cigarettesPerCone
        Log.d(TAG, "🚬 Cone tracking: 1 cone = ${String.format("%.3f", cigarettesPerCone)} cigarettes (${String.format("%.2f", conesPerBowl)} cones = 1 bowl = ${bowlRatio.cigarettesPerSmoke} cigarettes)")
        Log.d(TAG, "🚬 Cone tracking: ${smoker.name} - new cigarette fraction: $cigaretteFraction")
        
        // Add whole cigarettes when fraction >= 1.0
        var cigarettesAdded = 0
        var cigaretteTimestamp = timestamp  // Start with base timestamp
        
        while (cigaretteFraction >= 1.0) {
            cigarettesAdded++
            val cigaretteLog = ActivityLog(
                id = 0L,
                smokerId = smoker.smokerId,
                consumerId = smoker.smokerId,
                payerStashOwnerId = payerStashOwnerId,
                type = ActivityType.CIGARETTE,
                timestamp = cigaretteTimestamp,  // Use incremented timestamp
                sessionId = sessionStatsVM.currentSessionId.value,
                sessionStartTime = if (sessionActive) sessionStart else null,
                gramsAtLog = 0.0,
                pricePerGramAtLog = 0.0,
                customRatioName = "From cones via ${bowlRatio.name}",
                cigaretteFractionContribution = -1.0,  // Cigarettes consume 1.0 fraction  
                cigaretteFractionBefore = cigaretteFraction  // Fraction before consuming 1.0
            )
            
            cigaretteTimestamp += 100  // Increment by 100ms for next cigarette
            Log.d(TAG, "🚬📊 Creating cone-based cigarette #$cigarettesAdded at timestamp $cigaretteTimestamp")
            
            withContext(Dispatchers.IO) {
                val id = repo.insert(cigaretteLog)
                Log.d(TAG, "🚬 Added cigarette from cones to ActivityLog with ID: $id")
            }
            
            // Sync cigarette to cloud if in session
            if (currentShareCode != null) {
                val smokerActivityUid = if (smoker.isCloudSmoker && !smoker.cloudUserId.isNullOrEmpty()) {
                    smoker.cloudUserId!!
                } else {
                    "local_${smoker.uid}"
                }
                
                val deviceId = getAndroidDeviceId()
                val localActivityId = "cig_${smokerActivityUid}_${timestamp}"
                sessionSyncService.addActivityToRoom(
                    shareCode = currentShareCode!!,
                    smokerUid = smokerActivityUid,
                    smokerName = smoker.name,
                    activityType = ActivityType.CIGARETTE,
                    timestamp = timestamp,
                    deviceId = deviceId,
                    cigaretteFractionContribution = -1.0,
                    cigaretteFractionBefore = cigaretteFraction,
                    customRatioName = "From cones via ${bowlRatio.name}"
                ).fold(
                    onSuccess = { Log.d(TAG, "🚬 Synced cigarette to cloud") },
                    onFailure = { error ->
                        Log.e(TAG, "🚬 Failed to sync cigarette to cloud", error)
                        handleCloudSyncFailure(
                            error = error,
                            shareCode = currentShareCode!!,
                            smokerUid = smokerActivityUid,
                            smokerName = smoker.name,
                            activityType = ActivityType.CIGARETTE,
                            timestamp = timestamp,
                            deviceId = deviceId,
                            localActivityId = localActivityId,
                            cigaretteFractionContribution = -1.0,
                            cigaretteFractionBefore = cigaretteFraction,
                            customRatioName = "From cones via ${bowlRatio.name}"
                        )
                    }
                )
            }
            
            cigaretteFraction -= 1.0
        }
        
        if (cigarettesAdded > 0) {
            // Update last cigarette timestamp for stats tracking
            lastCigaretteTimestamp = cigaretteTimestamp - 100  // Use the last actual timestamp used
            Log.d(TAG, "🚬📊 Cone-based: Updated lastCigaretteTimestamp to $lastCigaretteTimestamp")
            Log.d(TAG, "🚬 Added $cigarettesAdded cigarette(s) from cones to ActivityLog")
            
            // Update optimistic UI for each cigarette added
            repeat(cigarettesAdded) {
                updateOptimisticUI(
                    smokerName = smoker.name,
                    type = ActivityType.CIGARETTE,
                    customActivity = null
                )
            }
            
            // Update goals for each cigarette added
            val sessionShareCode = if (sessionActive) currentShareCode else null
            repeat(cigarettesAdded) {
                Log.d(TAG, "🎯 Updating goals for cigarette activity (from cones)")
                goalService.updateGoalProgressForSelectedActivity(
                    activityType = ActivityType.CIGARETTE,
                    sessionShareCode = sessionShareCode,
                    currentSmokerName = smoker.name
                )
            }
        } else {
            Log.d(TAG, "🚬 No cigarettes added yet (cigarette fraction: $cigaretteFraction)")
        }
        
        // Save cigarette fraction
        ratioManager.saveCigaretteFraction(cigaretteFraction, smoker.smokerId)
        Log.d(TAG, "🚬 Saved cigarette fraction: $cigaretteFraction for ${smoker.name}")
    }
    
    // Handle cigarette tracking based on smoke ratio
    private suspend fun handleCigaretteTracking(
        ratio: SmokeRatio,
        activityType: ActivityType,
        timestamp: Long,
        smoker: Smoker,
        payerStashOwnerId: String?
    ) {
        // Calculate cigarettes per activity (not per smoke)
        // cigarettesPerSmoke field represents cigarettes per bowl/joint
        val quantity = if (activityType == ActivityType.BOWL) pendingBowlQuantity else 1
        val cigarettesToAdd = ratio.cigarettesPerSmoke * quantity
        
        // Get current fraction from SharedPreferences for this smoker BEFORE logging
        val fractionBefore = ratioManager.getCigaretteFraction(smoker.smokerId)
        var currentFraction = fractionBefore
        Log.d(TAG, "🚬 Cigarette tracking: ${ratio.name} - $quantity ${activityType.name.lowercase()} × ${ratio.cigarettesPerSmoke} cigs/activity = $cigarettesToAdd cigarettes")
        Log.d(TAG, "🚬 Previous fraction: $currentFraction for smoker ${smoker.name}")
        
        currentFraction += cigarettesToAdd
        Log.d(TAG, "🚬 New total fraction: $currentFraction for smoker ${smoker.name}")
        
        // Only add whole cigarettes to ActivityLog when we have >= 1.0
        var cigarettesAdded = 0
        var cigaretteTimestamp = timestamp  // Start with the base timestamp
        
        while (currentFraction >= 1.0) {
            cigarettesAdded++
            // Create cigarette activity log
            val currentSessionId = sessionStatsVM.currentSessionId.value
            val currentSessionStart = if (sessionActive) sessionStart else null
            
            Log.d(TAG, "🚬📊 Creating cigarette #$cigarettesAdded: smoker=${smoker.name}(${smoker.smokerId}), timestamp=$cigaretteTimestamp")
            Log.d(TAG, "🚬 Session info: sessionId=$currentSessionId, sessionStart=$currentSessionStart, sessionActive=$sessionActive")
            
            val cigaretteLog = ActivityLog(
                id = 0L,
                smokerId = smoker.smokerId,
                consumerId = smoker.smokerId,
                payerStashOwnerId = payerStashOwnerId,
                type = ActivityType.CIGARETTE,
                timestamp = cigaretteTimestamp,  // Use incremented timestamp
                sessionId = currentSessionId,
                sessionStartTime = currentSessionStart,
                gramsAtLog = 0.0,  // No cannabis in cigarettes
                pricePerGramAtLog = 0.0,
                customRatioName = "From ${ratio.name}",
                cigaretteFractionContribution = -1.0,  // Cigarettes consume 1.0 fraction
                cigaretteFractionBefore = currentFraction  // Fraction before consuming 1.0
            )
            
            cigaretteTimestamp += 100  // Increment by 100ms for next cigarette
            
            val insertedId = withContext(Dispatchers.IO) {
                val id = repo.insert(cigaretteLog)
                Log.d(TAG, "🚬 Added cigarette to ActivityLog with ID: $id, sessionId=$currentSessionId")
                id
            }
            
            // Sync cigarette to cloud if in session
            if (currentShareCode != null) {
                val smokerActivityUid = if (smoker.isCloudSmoker && !smoker.cloudUserId.isNullOrEmpty()) {
                    smoker.cloudUserId!!
                } else {
                    "local_${smoker.uid}"
                }
                
                val deviceId = getAndroidDeviceId()
                val localActivityId = "cig_${smokerActivityUid}_${timestamp}"
                sessionSyncService.addActivityToRoom(
                    shareCode = currentShareCode!!,
                    smokerUid = smokerActivityUid,
                    smokerName = smoker.name,
                    activityType = ActivityType.CIGARETTE,
                    timestamp = timestamp,
                    deviceId = deviceId,
                    cigaretteFractionContribution = -1.0,
                    cigaretteFractionBefore = currentFraction,
                    customRatioName = "From ${ratio.name}"
                ).fold(
                    onSuccess = { Log.d(TAG, "🚬 Synced cigarette to cloud") },
                    onFailure = { error ->
                        Log.e(TAG, "🚬 Failed to sync cigarette: ${error.message}")
                        handleCloudSyncFailure(
                            error = error,
                            shareCode = currentShareCode!!,
                            smokerUid = smokerActivityUid,
                            smokerName = smoker.name,
                            activityType = ActivityType.CIGARETTE,
                            timestamp = timestamp,
                            deviceId = deviceId,
                            localActivityId = localActivityId.ifEmpty { insertedId.toString() },
                            cigaretteFractionContribution = -1.0,
                            cigaretteFractionBefore = currentFraction,
                            customRatioName = "From ${ratio.name}"
                        )
                    }
                )
            }
            
            // Update last cigarette timestamp for stats tracking
            lastCigaretteTimestamp = cigaretteTimestamp - 100  // Use the last actual timestamp used
            Log.d(TAG, "🚬📊 Updated lastCigaretteTimestamp to $lastCigaretteTimestamp")
            
            currentFraction -= 1.0
        }
        
        if (cigarettesAdded > 0) {
            Log.d(TAG, "🚬 Added $cigarettesAdded cigarette(s) to ActivityLog")
            
            // Update optimistic UI for each cigarette added
            repeat(cigarettesAdded) {
                updateOptimisticUI(
                    smokerName = smoker.name,
                    type = ActivityType.CIGARETTE,
                    customActivity = null
                )
            }
            
            // Update goals for each cigarette added
            val sessionShareCode = if (sessionActive) currentShareCode else null
            repeat(cigarettesAdded) {
                Log.d(TAG, "🎯 Updating goals for cigarette activity")
                goalService.updateGoalProgressForSelectedActivity(
                    activityType = ActivityType.CIGARETTE,
                    sessionShareCode = sessionShareCode,
                    currentSmokerName = smoker.name
                )
            }
        } else {
            Log.d(TAG, "🚬 No cigarettes added yet (fraction: $currentFraction)")
        }
        
        // Save remaining fraction for this smoker (including when no cigarettes were added)
        ratioManager.saveCigaretteFraction(currentFraction, smoker.smokerId)
        Log.d(TAG, "🚬 Saved remaining fraction: $currentFraction for smoker ${smoker.name}")
    }
    
    // Helper function to create ratio display content
    private fun createRatioDisplayContent(ratio: SmokeRatio?): View {
        // Get stash ratios for Normal ratio display
        val stashViewModel = ViewModelProvider(this).get(StashViewModel::class.java)
        val stashRatios = stashViewModel.ratios.value
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Text container
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        // Name text with checkmark if selected
        val nameText = TextView(this).apply {
            text = if (ratio != null) {
                "✓ ${ratio.name}"
            } else {
                "Normal ratio"
            }
            textSize = 16f
            setTextColor(if (ratio != null) Color.parseColor("#98FB98") else Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        textContainer.addView(nameText)
        
        // Details text
        if (ratio != null) {
            val detailsText = TextView(this).apply {
                text = "${ratio.numberOfSmokes} smokes, ${ratio.thcPercent.toInt()}% THC, ${String.format("%.4f", ratio.chopAmount)}g chop"
                textSize = 12f
                setTextColor(Color.parseColor("#707070"))
            }
            textContainer.addView(detailsText)
        } else {
            val detailsText = TextView(this).apply {
                text = if (stashRatios != null) {
                    "Bowl: ${String.format("%.2f", stashRatios.bowlGrams)}g, Joint: ${String.format("%.2f", stashRatios.jointGrams)}g, Cone: ${String.format("%.2f", stashRatios.coneGrams)}g"
                } else {
                    "Bowl: 0.20g, Joint: 0.50g, Cone: 0.30g"
                }
                textSize = 12f
                setTextColor(Color.parseColor("#707070"))
            }
            textContainer.addView(detailsText)
        }
        
        container.addView(textContainer)
        
        // Arrow indicator
        val arrowText = TextView(this).apply {
            text = "▼"
            textSize = 14f
            setTextColor(Color.parseColor("#707070"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = 8.dpToPx(this@MainActivity)
            }
        }
        container.addView(arrowText)
        
        return container
    }
    
    // Helper function to show ratio dropdown
    private fun showRatioDropdown(
        anchorView: View,
        ratios: List<SmokeRatio>,
        currentRatio: SmokeRatio?,
        onRatioSelected: (SmokeRatio?) -> Unit
    ) {
        // Get current stash ratios for Normal ratio display
        val stashViewModel = ViewModelProvider(this).get(StashViewModel::class.java)
        val stashRatios = stashViewModel.ratios.value
        // Create a custom dropdown dialog
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { dialog.dismiss() }
        }
        
        // Create card for dropdown with green border
        val cardContainer = LinearLayout(this).apply {
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            
            layoutParams = FrameLayout.LayoutParams(
                anchorView.width,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = location[0]
                topMargin = location[1] + anchorView.height
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8.dpToPx(this@MainActivity).toFloat()
                setColor(Color.parseColor("#1a1a1a"))
                // Removed border as it was getting cut off
            }
        }
        
        // Create scroll view for the dropdown items
        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // Limit max height
            val maxHeight = 300.dpToPx(this@MainActivity)
            minimumHeight = 0
            layoutParams.height = maxHeight
        }
        
        val itemsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1a1a1a"))
        }
        
        // Add "Normal ratio" option with stash ratios
        val normalRatioItem = createDropdownItem(null, currentRatio == null, stashRatios)
        normalRatioItem.setOnClickListener {
            onRatioSelected(null)
            dialog.dismiss()
        }
        itemsLayout.addView(normalRatioItem)
        
        // Add divider
        itemsLayout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.dpToPx(this@MainActivity)
            )
            setBackgroundColor(Color.parseColor("#444444"))
        })
        
        // Add ratio items
        ratios.forEach { ratio ->
            val item = createDropdownItem(ratio, currentRatio?.id == ratio.id)
            item.setOnClickListener {
                onRatioSelected(ratio)
                dialog.dismiss()
            }
            itemsLayout.addView(item)
            
            // Add divider between items
            if (ratio != ratios.last()) {
                itemsLayout.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1.dpToPx(this@MainActivity)
                    )
                    setBackgroundColor(Color.parseColor("#444444"))
                })
            }
        }
        
        scrollView.addView(itemsLayout)
        cardContainer.addView(scrollView)
        container.addView(cardContainer)
        dialog.setContentView(container)
        dialog.show()
    }
    
    // Helper function to create styled dropdown item
    private fun createDropdownItem(ratio: SmokeRatio?, isSelected: Boolean, stashRatios: ConsumptionRatio? = null): View {
        val itemContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2a2a2a"))
            }
            setPadding(12.dpToPx(this@MainActivity), 12.dpToPx(this@MainActivity),
                12.dpToPx(this@MainActivity), 12.dpToPx(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Name text
        val nameText = TextView(this).apply {
            text = if (ratio != null) {
                if (isSelected) "✓ ${ratio.name}" else ratio.name
            } else {
                if (isSelected) "✓ Normal ratio" else "Normal ratio"
            }
            textSize = 16f
            setTextColor(if (isSelected) Color.parseColor("#98FB98") else Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        itemContainer.addView(nameText)
        
        // Details text
        val detailsText = TextView(this).apply {
            text = if (ratio != null) {
                "${ratio.numberOfSmokes} smokes, ${ratio.thcPercent.toInt()}% THC, ${String.format("%.4f", ratio.chopAmount)}g chop"
            } else {
                // Show actual user ratios for Normal ratio
                if (stashRatios != null) {
                    "Bowl: ${String.format("%.2f", stashRatios.bowlGrams)}g, Joint: ${String.format("%.2f", stashRatios.jointGrams)}g, Cone: ${String.format("%.2f", stashRatios.coneGrams)}g"
                } else {
                    "Bowl: 0.20g, Joint: 0.50g, Cone: 0.30g"
                }
            }
            textSize = 12f
            setTextColor(Color.parseColor("#707070"))
        }
        itemContainer.addView(detailsText)
        
        return itemContainer
    }
    
    // Add this function to log bowls with quantity
    private fun logBowlsWithQuantity(quantity: Int, selectedRatio: SmokeRatio? = null) {
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
                            Log.d(TAG, "📱 BUTTON: Activity button clicked - type: $activityType, timestamp: ${System.currentTimeMillis()}")
                            confettiHelper.showConfettiFromButton(button)

                            // Track countdown timing when activity is logged
                            val now = System.currentTimeMillis()
                            countdownStartTime = now
                            Log.d(TAG, "📱 BUTTON: Calling logHitSafe for $activityType")

                            updateCurrentActivitySelection(activityType)
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

    private fun updateCurrentActivitySelection(activityType: ActivityType, customActivity: CustomActivity? = null) {
        lastSelectedActivityType = activityType
        lastSelectedCustomActivityId = customActivity?.id
        lastSelectedCustomActivityName = customActivity?.name

        val editor = prefs.edit()
        Log.d(TAG, "🎯 PREFS: Storing activity selection ${activityType.name} (customId=${customActivity?.id ?: "none"})")
        editor.putString("current_activity_type", activityTypeToPrefValue(activityType))
        if (activityType == ActivityType.CUSTOM && customActivity != null) {
            editor.putString("current_custom_activity_id", customActivity.id)
            editor.putString("current_custom_activity_name", customActivity.name)
        } else {
            editor.remove("current_custom_activity_id")
            editor.remove("current_custom_activity_name")
        }
        editor.apply()
    }

    private fun activityTypeToPrefValue(activityType: ActivityType): String = when (activityType) {
        ActivityType.CONE -> "cones"
        ActivityType.JOINT -> "joints"
        ActivityType.BOWL -> "bowls"
        ActivityType.CUSTOM -> "custom"
        ActivityType.CIGARETTE -> "cigarettes"
        else -> activityType.name.lowercase()
    }

    private fun prefValueToActivityType(value: String?): ActivityType = when (value?.lowercase()) {
        "joints" -> ActivityType.JOINT
        "bowls" -> ActivityType.BOWL
        "custom" -> ActivityType.CUSTOM
        "cigarettes" -> ActivityType.CIGARETTE
        else -> ActivityType.CONE
    }

    private fun SharedPreferences.Editor.applyCustomActivityPrefs(): SharedPreferences.Editor {
        return if (lastSelectedActivityType == ActivityType.CUSTOM &&
            !lastSelectedCustomActivityId.isNullOrEmpty() &&
            !lastSelectedCustomActivityName.isNullOrEmpty()
        ) {
            putString("current_custom_activity_id", lastSelectedCustomActivityId)
            putString("current_custom_activity_name", lastSelectedCustomActivityName)
        } else {
            remove("current_custom_activity_id")
            remove("current_custom_activity_name")
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
        
        // Ratio dropdown section
        var selectedRatio: SmokeRatio? = null
        val ratioSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dpToPx(this@MainActivity)
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        
        val ratioLabel = TextView(this).apply {
            text = "Ratio:"
            textSize = 14f
            setTextColor(Color.parseColor("#B0B0B0"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dpToPx(this@MainActivity)
            }
        }
        ratioSection.addView(ratioLabel)
        
        // Get ratios based on activity type
        val lastSelectedRatioKey = if (activityType == ActivityType.BOWL) "last_bowl_ratio_id" else "last_joint_ratio_id"
        val lastSelectedRatioId = prefs.getString(lastSelectedRatioKey, null)
        val ratios = ratioManager.getRatiosForType(
            if (activityType == ActivityType.BOWL) SmokeRatio.RatioType.BOWL else SmokeRatio.RatioType.JOINT
        )
        
        // Try to find last selected ratio
        if (lastSelectedRatioId != null) {
            selectedRatio = ratios.firstOrNull { it.id == lastSelectedRatioId }
        }
        // If no last selected or not found, use the one marked as selected
        if (selectedRatio == null) {
            selectedRatio = ratios.firstOrNull { it.isSelected }
        }
        
        // Create a container that looks like the ratio cards
        val dropdownContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8.dpToPx(this@MainActivity).toFloat()
                setColor(Color.parseColor("#2a2a2a"))
                // Set border based on selection
                if (selectedRatio != null) {
                    setStroke(1.dpToPx(this@MainActivity), Color.parseColor("#98FB98"))
                } else {
                    setStroke(1.dpToPx(this@MainActivity), Color.parseColor("#444444"))
                }
            }
            setPadding(12.dpToPx(this@MainActivity), 12.dpToPx(this@MainActivity),
                12.dpToPx(this@MainActivity), 12.dpToPx(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            
            setOnClickListener {
                // Show ratio selection dropdown
                showRatioDropdown(this, ratios, selectedRatio) { ratio ->
                    selectedRatio = ratio
                    
                    // Update the container styling and content
                    removeAllViews()
                    
                    // Update border color based on selection
                    (background as? GradientDrawable)?.apply {
                        if (ratio != null) {
                            setStroke(1.dpToPx(this@MainActivity), Color.parseColor("#98FB98"))
                        } else {
                            setStroke(1.dpToPx(this@MainActivity), Color.parseColor("#444444"))
                        }
                    }
                    
                    // Add the content
                    addView(createRatioDisplayContent(ratio))
                    
                    // Save selected ratio ID
                    if (ratio != null) {
                        prefs.edit().putString(lastSelectedRatioKey, ratio.id).apply()
                    } else {
                        prefs.edit().remove(lastSelectedRatioKey).apply()
                    }
                }
            }
        }
        
        // Add initial content
        dropdownContainer.addView(createRatioDisplayContent(selectedRatio))
        
        ratioSection.addView(dropdownContainer)
        
        contentLayout.addView(ratioSection)
        
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
                addRetroactiveActivities(activityType, finalQuantity, selectedTimeMode, selectedRatio)
                
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
    private fun addRetroactiveActivities(activityType: ActivityType, quantity: Int, timeMode: Int, selectedRatio: SmokeRatio? = null) {
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
                    
                    // Use the internal logHit function with specific timestamp and ratio
                    logHit(activityType, timestamp, selectedRatio)
                    
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
        onboardingPrefs = getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)

        // Restore last selected activity context for GiantCounter handoff
        val storedActivityType = prefs.getString("current_activity_type", "cones")
        lastSelectedActivityType = prefValueToActivityType(storedActivityType)
        if (lastSelectedActivityType == ActivityType.CUSTOM) {
            lastSelectedCustomActivityId = prefs.getString("current_custom_activity_id", null)
            lastSelectedCustomActivityName = prefs.getString("current_custom_activity_name", null)
        } else {
            lastSelectedCustomActivityId = null
            lastSelectedCustomActivityName = null
        }
        
        // Initialize onboarding controller
        onboardingController = OnboardingFlowController(
            activity = this,
            handler = Handler(Looper.getMainLooper()),
            onboardingPrefs = onboardingPrefs
        )

        // Initialize helpers
        confettiHelper = ConfettiHelper(this)
        confettiHelper.setupKonfettiOverlay(this)
        customActivityManager = CustomActivityManager(this)
        ratioManager = SmokeRatioManager(this)

        // Initialize cloud services and restore session
        initializeCloudServices()
        initializeSmokerManager()
        restoreSessionFromPrefs()
        setupSpinnerNew()

        initializeSupportMessagesWatcher()

        setupVibrationToggle()
        setupConfettiToggle()
        setupModeToggleButton()
        setupLayoutRotation()
        setupGiantCounterButton()
        setupCustomActivityButton()
        setupActivityButtons()

        // Initialize Stash ViewModel if not already initialized by delegation
        if (!::stashViewModel.isInitialized) {
            stashViewModel = ViewModelProvider(this).get(StashViewModel::class.java)
        }

        // Initialize goal service
        goalService = GoalService(application)
        
        // Setup CalendarViewModel callbacks for goal and stash handling
        CalendarViewModel.onReverseGoal = { activityLog, smoker, sessionShareCode ->
            Log.d(TAG, "🎯 CalendarViewModel: Reversing goal progress for ${activityLog.type} by ${smoker.name}")
            try {
                if (activityLog.type == ActivityType.CUSTOM && !activityLog.customActivityId.isNullOrEmpty()) {
                    goalService.reverseGoalProgressForSelectedActivity(
                        activityType = activityLog.type,
                        customActivityId = activityLog.customActivityId,
                        customActivityName = activityLog.customActivityName,
                        sessionShareCode = sessionShareCode,
                        currentSmokerName = smoker.name
                    )
                } else {
                    // For regular activities, try the new selected activity system first
                    goalService.reverseGoalProgressForSelectedActivity(
                        activityType = activityLog.type,
                        customActivityId = null,
                        customActivityName = null,
                        sessionShareCode = sessionShareCode,
                        currentSmokerName = smoker.name
                    )
                    
                    // Also try the legacy system for backwards compatibility
                    goalService.reverseGoalProgressForActivity(
                        activityType = activityLog.type,
                        sessionShareCode = sessionShareCode,
                        smokerName = smoker.name
                    )
                }
                Log.d(TAG, "🎯✅ CalendarViewModel: Goal progress reversed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "🎯❌ CalendarViewModel: Error reversing goal progress: ${e.message}", e)
            }
        }
        
        CalendarViewModel.onRestoreStash = { activityLog, smoker ->
            Log.d(TAG, "📦 CalendarViewModel: Restoring stash for ${activityLog.type} by ${smoker.name}")
            try {
                withContext(Dispatchers.Main) {
                    stashViewModel.undoStashConsumption(activityLog, smoker.name)
                }
                Log.d(TAG, "📦✅ CalendarViewModel: Stash restored successfully")
            } catch (e: Exception) {
                Log.e(TAG, "📦❌ CalendarViewModel: Error restoring stash: ${e.message}", e)
            }
        }
        
        // Setup HistoryViewModel callbacks for goal and stash handling
        HistoryViewModel.onReverseGoal = { activityLog, smoker, sessionShareCode ->
            Log.d(TAG, "🎯 HistoryViewModel: Reversing goal progress for ${activityLog.type} by ${smoker.name}")
            try {
                if (activityLog.type == ActivityType.CUSTOM && !activityLog.customActivityId.isNullOrEmpty()) {
                    goalService.reverseGoalProgressForSelectedActivity(
                        activityType = activityLog.type,
                        customActivityId = activityLog.customActivityId,
                        customActivityName = activityLog.customActivityName,
                        sessionShareCode = sessionShareCode,
                        currentSmokerName = smoker.name
                    )
                } else {
                    // For regular activities, try the new selected activity system first
                    goalService.reverseGoalProgressForSelectedActivity(
                        activityType = activityLog.type,
                        customActivityId = null,
                        customActivityName = null,
                        sessionShareCode = sessionShareCode,
                        currentSmokerName = smoker.name
                    )
                    
                    // Also try the legacy system for backwards compatibility
                    goalService.reverseGoalProgressForActivity(
                        activityType = activityLog.type,
                        sessionShareCode = sessionShareCode,
                        smokerName = smoker.name
                    )
                }
                Log.d(TAG, "🎯✅ HistoryViewModel: Goal progress reversed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "🎯❌ HistoryViewModel: Error reversing goal progress: ${e.message}", e)
            }
        }
        
        HistoryViewModel.onRestoreStash = { activityLog, smoker ->
            Log.d(TAG, "📦 HistoryViewModel: Restoring stash for ${activityLog.type} by ${smoker.name}")
            try {
                withContext(Dispatchers.Main) {
                    stashViewModel.undoStashConsumption(activityLog, smoker.name)
                }
                Log.d(TAG, "📦✅ HistoryViewModel: Stash restored successfully")
            } catch (e: Exception) {
                Log.e(TAG, "📦❌ HistoryViewModel: Error restoring stash: ${e.message}", e)
            }
        }

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
        
        // Check if we're coming from splash and if we should run onboarding
        val fromSplash = intent.getBooleanExtra(SplashActivity.EXTRA_FROM_SPLASH, false)
        val shouldRunOnboarding = intent.getBooleanExtra(SplashActivity.EXTRA_SHOULD_RUN_ONBOARDING, false)
        
        Log.d("FIRST_LAUNCH_FLOW", "📱 MainActivity started - fromSplash=$fromSplash, shouldRunOnboarding=$shouldRunOnboarding")
        
        // Start onboarding flow if needed
        onboardingController.start(shouldRunOnboarding, fromSplash)
        
        triggerInitialNotifications()
        setupSessionControls()
        setupRewindButton()
        setupSkipButton()
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
        
        // Initialize 420 notifications
        Notification420Receiver.schedule420Notifications(this)

// Initialize offline queue system
        loadOfflineQueue()
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
        smokerManager.onUpdateQueueStatusDot = { dot, smoker -> updateQueueStatusDot(dot, smoker) }

        // Load lock states from preferences
        smokerManager.randomFontsEnabled = prefs.getBoolean("random_fonts_enabled", true)
        smokerManager.colorChangingEnabled = prefs.getBoolean("color_changing_enabled", true)
        
        // Load global locked values if they exist
        val globalLockedColor = prefs.getInt("global_locked_color", -1)
        if (globalLockedColor != -1 && !smokerManager.colorChangingEnabled) {
            smokerManager.setGlobalLockedColor(globalLockedColor)
        }
        
        val globalLockedFontIndex = prefs.getInt("global_font_index", -1)
        if (globalLockedFontIndex != -1 && !smokerManager.randomFontsEnabled) {
            smokerManager.setGlobalLockedFontIndex(globalLockedFontIndex)
        }
        
        Log.d(TAG, "🔒 Loaded lock states on app start:")
        Log.d(TAG, "   randomFontsEnabled: ${smokerManager.randomFontsEnabled}")
        Log.d(TAG, "   colorChangingEnabled: ${smokerManager.colorChangingEnabled}")
        Log.d(TAG, "   globalLockedColor: $globalLockedColor")
        Log.d(TAG, "   globalLockedFontIndex: $globalLockedFontIndex")
    }

    private fun setupSpinnerNew() {
        val spinner: Spinner = binding.spinnerSmoker

        setupSpinnerLongPress(spinner)

        // Create reorder dialog
        val reorderDialog = SmokerReorderDialog(
            context = this,
            repository = repo,
            smokerManager = smokerManager,
            lifecycleScope = lifecycleScope,
            onOrderChanged = {
                // Refresh the list after reordering
                Log.d(TAG, "🔄 Smoker order changed, refreshing list")
            }
        )
        
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
            },
            onReorderClick = {
                // Show reorder dialog with current smokers
                val currentSmokers = smokerAdapterNew.getAllSmokers()
                reorderDialog.show(currentSmokers)
            }
        )

        spinner.adapter = smokerAdapterNew

        spinner.dropDownVerticalOffset = 8

        repo.allSmokers.observe(this) { list ->
            handleSmokersListUpdate(list)
        }
        
        // Auto-purge soft-deleted smokers older than 30 days
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cutoffTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30 days
                val smokerDao = AppDatabase.getDatabase(this@MainActivity).smokerDao()
                smokerDao.purgeOldSoftDeletedSmokers(cutoffTime)
                Log.d(TAG, "Auto-purged soft-deleted smokers older than 30 days")
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto-purge: ${e.message}", e)
            }
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var isFirstSelection = true
            
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                // Skip the first automatic selection during initialization
                if (isFirstSelection) {
                    isFirstSelection = false
                    // Don't show dialog on first automatic selection
                    val sel = smokerAdapterNew.getItem(pos)
                    if (sel != null) {
                        handleSmokerSelection(sel)
                    }
                    smokerManager.dismissSpinnerDropDown()
                    return
                }
                
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

        refreshQueueIndicators()
    }

    private fun handleSmokersListUpdate(list: List<Smoker>) {
        Log.d("MainActivity", "📋 handleSmokersListUpdate: ${list.size} smokers")
        Log.d("MainActivity", "📋 Smokers: ${list.map { "${it.name}(id:${it.smokerId},deleted:${it.isDeleted})" }}")
        
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

        applyActiveSmokerFromRoomIfNeeded(sharedActiveSmokerId)
    }

    private fun handleSmokerSelection(smoker: Smoker) {
        if (isApplyingRemoteSpinnerUpdate) {
            try {
                selectSmoker(smoker, broadcastToRoom = false)
            } finally {
                isApplyingRemoteSpinnerUpdate = false
            }
            return
        }

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

    private fun getRoomSmokerId(smoker: Smoker): String? {
        val cloudId = smoker.cloudUserId
        return if (!cloudId.isNullOrEmpty()) {
            cloudId
        } else {
            "local_${smoker.uid}"
        }
    }

    private fun findSmokerIndexByRoomId(organizedSmokers: List<Smoker>, roomSmokerId: String): Int {
        return organizedSmokers.indexOfFirst { smoker ->
            val candidate = if (!smoker.cloudUserId.isNullOrEmpty()) {
                smoker.cloudUserId
            } else {
                "local_${smoker.uid}"
            }
            candidate == roomSmokerId
        }
    }

    private fun applyActiveSmokerFromRoomIfNeeded(roomSmokerId: String?) {
        sharedActiveSmokerId = roomSmokerId
        val targetId = roomSmokerId ?: return

        val sections = organizeSmokers()
        val organizedSmokers = sections.flatMap { it.smokers }
        if (organizedSmokers.isEmpty()) {
            return
        }

        val targetIndex = findSmokerIndexByRoomId(organizedSmokers, targetId)
        if (targetIndex == -1) {
            return
        }

        val targetSmoker = organizedSmokers.getOrNull(targetIndex) ?: return
        val currentIndex = binding.spinnerSmoker.selectedItemPosition

        if (currentIndex == targetIndex) {
            val app = application as CloudCounterApplication
            if (app.defaultSmokerId != targetSmoker.smokerId) {
                isApplyingRemoteSpinnerUpdate = true
                try {
                    selectSmoker(targetSmoker, broadcastToRoom = false)
                } finally {
                    isApplyingRemoteSpinnerUpdate = false
                }
            }
            return
        }

        isApplyingRemoteSpinnerUpdate = true
        binding.spinnerSmoker.setSelection(targetIndex, false)
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
            if (::skipReceiver.isInitialized) {
                unregisterReceiver(skipReceiver)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering skip receiver: ${e.message}")
        }

        try {
            if (::deletionReceiver.isInitialized) {
                unregisterReceiver(deletionReceiver)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering deletion receiver: ${e.message}")
        }

        try {
            if (::autoAdvanceReceiver.isInitialized) {
                unregisterReceiver(autoAdvanceReceiver)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering auto-advance receiver: ${e.message}")
        }

        super.onDestroy()
    }


    private fun initializeCloudServices() {
        authManager = (application as CloudCounterApplication).authManager
        cloudSyncService = (application as CloudCounterApplication).cloudSyncService
        sessionSyncService = SessionSyncService(this, repository = repo)
        passwordDialog = PasswordDialog(this)
        
        // Initialize turn notification manager
        turnNotificationManager = TurnNotificationManager(this, repo)

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
                    try {
                        repo.insertOrUpdateSmoker(smoker)
                        Log.d("WELCOME_DEBUG", "✅ Smoker inserted/updated in DB")
                    } catch (e: IllegalStateException) {
                        if (e.message?.contains("Maximum 50 active smokers") == true) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Maximum 50 active smokers reached. Please delete unused smokers first.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return@launch
                        } else {
                            throw e
                        }
                    }
                    
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
                    
                    // Update active session summary with room info if reconnecting
                    if (editingSummaryId != null) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val currentSummary = repo.getSummaryById(editingSummaryId!!)
                            if (currentSummary != null && currentSummary.roomName.isNullOrEmpty()) {
                                val updatedSummary = currentSummary.copy(
                                    shareCode = shareCode,
                                    roomName = room.name
                                )
                                repo.updateSummary(updatedSummary)
                                Log.d("SessionDebug", "Updated reconnected session with room info: ${room.name}")
                            }
                        }
                    }

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
        val editor = prefs.edit()
            .putBoolean("sessionActive", sessionActive)
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
        
        // Only save sessionStart if it's valid (not 0)
        if (sessionStart > 0) {
            editor.putLong("sessionStart", sessionStart)
            Log.d(TAG, "💾 Saving sessionStart: $sessionStart")
        } else {
            Log.d(TAG, "💾 Not saving sessionStart (value is 0)")
        }
        
        val success = editor
            .putString("current_activity_type", activityTypeToPrefValue(lastSelectedActivityType))
            .applyCustomActivityPrefs()
            .putLong("rewindOffset", rewindOffset)  // Save rewind offset
            .putString("activitiesTimestamps", activitiesTimestamps.joinToString(","))
            .putLong("lastConeTimestamp", lastConeTimestamp)
            .putLong("lastJointTimestamp", lastJointTimestamp)
            .putLong("lastBowlTimestamp", lastBowlTimestamp)
            .putInt("initialRoundsSet", initialRoundsSet)
            .commit()  // Use commit() for synchronous save
            
        Log.d(TAG, "💾 Session saved to prefs: ${if (success) "SUCCESS" else "FAILED"}")
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
            // Check if chat tab is currently selected
            val currentTabPosition = binding.tabLayout.selectedTabPosition
            if (currentTabPosition == 5) {
                // Chat tab is active - prevent switching to bottom
                Toast.makeText(this, "Can't switch controls position while in chat", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            isLayoutAtBottom = !isLayoutAtBottom
            Log.d("KEYBOARD_FIX", "🔄 Layout rotation clicked. New position: ${if (isLayoutAtBottom) "BOTTOM" else "TOP"}")
            
            // Use commit() instead of apply() for immediate synchronous save
            val saved = prefs.edit().putBoolean("layout_at_bottom", isLayoutAtBottom).commit()
            Log.d("KEYBOARD_FIX", "🔄 Preference saved successfully: $saved")
            
            // Verify the save
            val verifyPref = prefs.getBoolean("layout_at_bottom", false)
            Log.d("KEYBOARD_FIX", "🔄 Verified preference value: $verifyPref")
            
            updateLayoutPosition(isLayoutAtBottom)
            animateLayoutRotation()
            
            // Notify ChatFragment about the layout change AFTER a delay to ensure visual layout is complete
            Handler(Looper.getMainLooper()).postDelayed({
                val chatFragment = supportFragmentManager.fragments.find { it is ChatFragment } as? ChatFragment
                chatFragment?.let {
                    Log.d("KEYBOARD_FIX", "🔄 Notifying ChatFragment of layout change (delayed)")
                    it.onLayoutPositionChanged()
                } ?: Log.e("KEYBOARD_FIX", "🔄 ChatFragment not found to notify!")
            }, 300) // 300ms delay to allow animation and layout to complete
            
            val message = if (isLayoutAtBottom) "Controls moved to bottom" else "Controls moved to top"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupGiantCounterButton() {
        binding.btnGiantCounter.setOnClickListener {
            // Save current session and state to prefs for GiantCounterActivity
            saveSessionToPrefs()
            
            // Save lock states from smokerManager to SharedPreferences
            Log.d(TAG, "🔒 Saving lock states before launching GiantCounter:")
            Log.d(TAG, "   randomFontsEnabled: ${smokerManager.randomFontsEnabled}")
            Log.d(TAG, "   colorChangingEnabled: ${smokerManager.colorChangingEnabled}")
            prefs.edit()
                .putBoolean("random_fonts_enabled", smokerManager.randomFontsEnabled)
                .putBoolean("color_changing_enabled", smokerManager.colorChangingEnabled)
                .apply()
            
            // Get the actual smoker name, not the toString() of the object
            val selectedPosition = binding.spinnerSmoker.selectedItemPosition
            val organizedSmokers = organizeSmokers().flatMap { it.smokers }
            val selectedSmokerObj = organizedSmokers.getOrNull(selectedPosition)
            val selectedSmoker = selectedSmokerObj?.name ?: "Sam"
            
            // Get current stash source
            val currentStashSource = stashViewModel.stashSource.value ?: StashSource.MY_STASH
            val stashSourceString = when (currentStashSource) {
                StashSource.MY_STASH -> "MY_STASH"
                StashSource.THEIR_STASH -> "THEIR_STASH"
                StashSource.EACH_TO_OWN -> "EACH_TO_OWN"
            }
            
            // Save current spinner font and color
            if (selectedSmokerObj != null) {
                // Try to get from the current view first (most accurate for current display)
                val spinnerView = binding.spinnerSmoker.selectedView
                var actualColor: Int
                var actualFontIndex: Int
                
                if (spinnerView != null) {
                    val container = spinnerView as? FrameLayout
                    val textView = container?.findViewById<TextView>(R.id.textName)
                    if (textView != null) {
                        // Get the actual displayed color
                        actualColor = textView.currentTextColor
                        
                        // Get the actual displayed font and find its index
                        val currentTypeface = textView.typeface
                        val fontList = listOf(
                            R.font.bitcount_prop_double,
                            R.font.exile,
                            R.font.modak,
                            R.font.oi,
                            R.font.rubik_glitch,
                            R.font.sankofa_display,
                            R.font.silkscreen,
                            R.font.rubik_beastly,
                            R.font.sixtyfour,
                            R.font.monoton,
                            R.font.sedgwick_ave_display,
                            R.font.splash
                        )
                        
                        // Try to match the typeface to find index
                        actualFontIndex = 0 // Default
                        for (i in fontList.indices) {
                            try {
                                val testFont = ResourcesCompat.getFont(this, fontList[i])
                                if (testFont == currentTypeface) {
                                    actualFontIndex = i
                                    break
                                }
                            } catch (e: Exception) {
                                // Continue
                            }
                        }
                        
                        Log.d(TAG, "🎨 Saving spinner color: $actualColor (from view)")
                        Log.d(TAG, "🔤 Saving spinner font index: $actualFontIndex (from view)")
                    } else {
                        // TextView is null, use defaults
                        actualColor = Color.parseColor("#98FB98")
                        actualFontIndex = 0
                        Log.d(TAG, "⚠️ TextView is null, using defaults")
                    }
                } else {
                    // View is null, use defaults
                    actualColor = Color.parseColor("#98FB98")
                    actualFontIndex = 0
                    Log.d(TAG, "⚠️ Spinner view is null, using defaults")
                }
                
                prefs.edit()
                    .putInt("current_spinner_color", actualColor)
                    .putInt("current_spinner_font_index", actualFontIndex)
                    .apply()
            } else {
                // No smoker selected, save defaults
                Log.d(TAG, "⚠️ No smoker selected, saving default font/color")
                prefs.edit()
                    .putInt("current_spinner_color", Color.parseColor("#98FB98"))
                    .putInt("current_spinner_font_index", 0)
                    .apply()
            }
            
            val launchEditor = prefs.edit()
                .putString("selected_smoker", selectedSmoker)
                .putString("current_activity_type", activityTypeToPrefValue(lastSelectedActivityType))
                .putBoolean("is_auto_mode", isAutoMode)
                .putBoolean("timer_enabled", false) // Default to false for now
                .putString("stash_source", stashSourceString) // Pass stash source

            // Persist custom selection context for GiantCounter if applicable
            launchEditor.applyCustomActivityPrefs().commit()

            // Launch Giant Counter Activity and expect result
            val intent = Intent(this, GiantCounterActivity::class.java)
            startActivityForResult(intent, GIANT_COUNTER_REQUEST_CODE)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
    
    private fun setupCustomActivityButton() {
        Log.d("CUSTOM_ACTIVITY", "🎯 Setting up custom activity button")
        binding.btnAddCustomActivity.setOnClickListener {
            Log.d("CUSTOM_ACTIVITY", "➕ Custom activity button clicked")
            showCustomActivityDialog()
        }
    }
    
    private fun setupActivityButtons() {
        Log.d("CUSTOM_ACTIVITY", "🔄 Setting up activity buttons")
        
        // Clear the wrapper first
        val wrapper = binding.activityButtonWrapper
        wrapper.removeAllViews()
        
        // Clear button references
        customActivityButtons.clear()
        coreActivityButtons.clear()
        
        // Get current button order
        val order = customActivityManager.getActivityOrder()
        val customActivities = customActivityManager.getCustomActivities()
        
        Log.d("CUSTOM_ACTIVITY", "📊 Button order: $order")
        Log.d("CUSTOM_ACTIVITY", "📋 Custom activities: ${customActivities.size}")
        
        val numberOfButtons = order.size // Total number of buttons to display
        
        // Create the appropriate container based on button count
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(
                8.dpToPx(this@MainActivity),
                0,
                8.dpToPx(this@MainActivity),
                8.dpToPx(this@MainActivity)
            )
        }
        
        // Calculate button width for when we have more than 4 buttons
        val displayWidth = resources.displayMetrics.widthPixels
        val containerPadding = (16 * resources.displayMetrics.density).toInt() // Total horizontal padding (8dp each side)
        val availableWidth = displayWidth - containerPadding
        
        // Calculate button width only for fixed-width mode (5+ buttons)
        // For 1-4 buttons, we'll use weight=1f to distribute evenly
        val buttonMargins = (4 * resources.displayMetrics.density).toInt() * (4 - 1) // 3 margins between 4 buttons
        val buttonWidth = if (numberOfButtons > 4) {
            (availableWidth - buttonMargins) / 4 // Fixed width based on 4 buttons
        } else {
            0 // Not used when weight=1f
        }
        
        Log.d("BUTTON_RESIZE", "📏 Display width: $displayWidth")
        Log.d("BUTTON_RESIZE", "📏 Available width: $availableWidth")  
        Log.d("BUTTON_RESIZE", "📏 Number of buttons: $numberOfButtons")
        Log.d("BUTTON_RESIZE", "📏 Button width: $buttonWidth")
        
        // Create buttons in order
        order.forEach { activityId ->
            when (activityId) {
                "joint" -> addActivityButton(container, "ADD JOINT", ActivityType.JOINT, buttonWidth)
                "cone" -> addActivityButton(container, "ADD CONE", ActivityType.CONE, buttonWidth)
                "bowl" -> addActivityButton(container, "ADD BOWL", ActivityType.BOWL, buttonWidth)
                else -> {
                    // Custom activity
                    val customActivity = customActivities.find { it.id == activityId }
                    customActivity?.let {
                        addCustomActivityButton(container, it, buttonWidth)
                    }
                }
            }
        }
        
        // Add the container to the wrapper, with or without scroll view
        if (numberOfButtons > 4) {
            // Wrap in HorizontalScrollView for 5+ buttons
            val scrollView = HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isHorizontalScrollBarEnabled = false
            }
            scrollView.addView(container)
            wrapper.addView(scrollView)
        } else {
            // Add LinearLayout directly for 1-4 buttons
            wrapper.addView(container)
        }
    }
    
    private fun addActivityButton(container: LinearLayout, text: String, type: ActivityType, width: Int) {
        val numberOfButtons = customActivityManager.getActivityOrder().size
        val useFixedWidth = numberOfButtons > 4
        
        val buttonContainer = LinearLayout(this).apply {
            layoutParams = if (useFixedWidth) {
                // For 5+ buttons: use fixed width
                LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = if (container.childCount > 0) 4.dpToPx(this@MainActivity) else 0
                    marginEnd = if (container.childCount == numberOfButtons - 1) 4.dpToPx(this@MainActivity) else 0
                }
            } else {
                // For 1-4 buttons: use weight to fill available space evenly
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = if (container.childCount > 0) 4.dpToPx(this@MainActivity) else 0
                    // No marginEnd when using weight - let weight distribution handle spacing
                }
            }
            orientation = LinearLayout.VERTICAL
        }
        
        val button = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = when (type) {
                ActivityType.JOINT -> R.id.btnAddJoint
                ActivityType.CONE -> R.id.btnAddCone
                ActivityType.BOWL -> R.id.btnAddBowl
                else -> View.generateViewId()
            }
            this.text = text
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(getColor(R.color.my_light_primary))
            strokeColor = ColorStateList.valueOf(getColor(R.color.my_light_primary))
            strokeWidth = 4
            cornerRadius = 8.dpToPx(this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        
        // Store reference to core button
        coreActivityButtons.add(button)
        
        // Set up click listeners
        setupRetroactiveButton(button, type)
        if (type == ActivityType.BOWL) {
            button.setOnLongClickListener {
                vibrateFeedback(50)
                showBowlQuantityDialog()
                true
            }
        }
        
        buttonContainer.addView(button)
        
        // Add auto controls if needed
        val autoControls = createAutoControls(type)
        buttonContainer.addView(autoControls)
        
        container.addView(buttonContainer)
        
        // Store button references (removed as they're vals in binding)
    }
    
    private fun addCustomActivityButton(container: LinearLayout, activity: CustomActivity, width: Int) {
        Log.d("CUSTOM_ACTIVITY", "➕ Adding custom button: ${activity.name}")
        
        val numberOfButtons = customActivityManager.getActivityOrder().size
        val useFixedWidth = numberOfButtons > 4
        
        val buttonContainer = LinearLayout(this).apply {
            layoutParams = if (useFixedWidth) {
                // For 5+ buttons: use fixed width
                LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = if (container.childCount > 0) 4.dpToPx(this@MainActivity) else 0
                    marginEnd = if (container.childCount == numberOfButtons - 1) 4.dpToPx(this@MainActivity) else 0
                }
            } else {
                // For 1-4 buttons: use weight to fill available space evenly
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = if (container.childCount > 0) 4.dpToPx(this@MainActivity) else 0
                    // No marginEnd when using weight - let weight distribution handle spacing
                }
            }
            orientation = LinearLayout.VERTICAL
        }
        
        val button = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = View.generateViewId()
            text = activity.getButtonText()
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(getColor(R.color.my_light_primary)) // Use same green color as other buttons
            strokeColor = ColorStateList.valueOf(getColor(R.color.my_light_primary)) // Green border
            strokeWidth = 4 // Same stroke width as other buttons
            cornerRadius = 8.dpToPx(this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

            // Ensure 6-letter names fit on one line without changing button width
            // - Reduce inner side padding
            // - Remove default minWidth constraint
            // - Force single line to avoid wrapping
            minimumWidth = 0
            minWidth = 0
            // Trim side padding further so 8-char names fit on line 2
            setPaddingRelative(2.dpToPx(this@MainActivity), 8.dpToPx(this@MainActivity), 2.dpToPx(this@MainActivity), 8.dpToPx(this@MainActivity))
            // Two-line layout: "ADD" on first line, name on second line
            isSingleLine = false
            maxLines = 2
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = android.view.Gravity.CENTER
            ellipsize = android.text.TextUtils.TruncateAt.END
            
            // If icon-based: center the icon and show no text inside the button
            activity.iconResId?.let { iconRes ->
                text = "" // No ADD text for icon-based buttons
                setIconResource(iconRes)
                iconTint = ColorStateList.valueOf(getColor(R.color.my_light_primary))
                // Center the icon: no text, zero padding, and center gravity.
                // Use TEXT_START as a safe iconGravity; with empty text and center gravity
                // the icon renders centered across Material versions.
                iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = 0
                gravity = android.view.Gravity.CENTER
                // Clear any compound drawables just in case
                setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            }
        }
        
        // Store reference to custom button
        customActivityButtons.add(button)
        
        // Set click listener for custom activity (short click)
        button.setOnClickListener {
            Log.d("CUSTOM_ACTIVITY", "🎯 Custom activity clicked: ${activity.name}")
            vibrateFeedback(50)
            
            // Reset previous button
            lastSelectedActivityButton?.let { setActivityButtonSelected(it, false) }
            
            // Set this button as selected
            setActivityButtonSelected(button, true)
            lastSelectedActivityButton = button

            updateCurrentActivitySelection(ActivityType.CUSTOM, activity)

            // Show confetti
            Log.d(TAG, "📱 BUTTON: Custom activity button clicked - name: ${activity.name}, timestamp: ${System.currentTimeMillis()}")
            confettiHelper.showConfettiFromButton(button)

            // Track countdown timing
            val now = System.currentTimeMillis()
            countdownStartTime = now
            Log.d(TAG, "📱 BUTTON: Calling handleCustomActivityClick for ${activity.name}")
            
            handleCustomActivityClick(activity)
        }
        
        // Long click for retroactive logging (future feature)
        button.setOnLongClickListener {
            Log.d("CUSTOM_ACTIVITY", "📝 Custom activity long clicked: ${activity.name}")
            vibrateFeedback(50)
            // Could add retroactive logging for custom activities here
            true
        }
        
        buttonContainer.addView(button)
        container.addView(buttonContainer)
    }
    
    private fun createAutoControls(type: ActivityType): LinearLayout {
        return LinearLayout(this).apply {
            id = when (type) {
                ActivityType.JOINT -> R.id.layoutJointAutoControls
                ActivityType.CONE -> R.id.layoutConeAutoControls
                ActivityType.BOWL -> R.id.layoutBowlAutoControls
                else -> View.generateViewId()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (-10).dpToPx(this@MainActivity)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            
            val checkBox = CheckBox(this@MainActivity).apply {
                id = when (type) {
                    ActivityType.JOINT -> R.id.checkboxJointAuto
                    ActivityType.CONE -> R.id.checkboxConeAuto
                    ActivityType.BOWL -> R.id.checkboxBowlAuto
                    else -> View.generateViewId()
                }
                text = "Auto"
                textSize = 12f
            }
            
            val timer = TextView(this@MainActivity).apply {
                id = when (type) {
                    ActivityType.JOINT -> R.id.textJointTimer
                    ActivityType.CONE -> R.id.textConeTimer
                    ActivityType.BOWL -> R.id.textBowlTimer
                    else -> View.generateViewId()
                }
                text = "0:00"
                textSize = 12f
                visibility = View.GONE
                (layoutParams as? LinearLayout.LayoutParams)?.marginStart = 8.dpToPx(this@MainActivity)
            }
            
            addView(checkBox)
            addView(timer)
        }
    }
    
    private fun showCustomActivityDialog() {
        Log.d("CUSTOM_ACTIVITY", "📝 Showing custom activity dialog")
        
        val dialog = Dialog(this, R.style.TransparentDialog)
        val view = layoutInflater.inflate(R.layout.dialog_add_custom_activity, null)
        dialog.setContentView(view)
        
        // Get views
        val etActivityName = view.findViewById<EditText>(R.id.etActivityName)
        val tvIconSelectionLabel = view.findViewById<TextView>(R.id.tvIconSelectionLabel)
        val iconGrid = view.findViewById<GridLayout>(R.id.iconGrid)
        // Make icon selection always visible and optional
        tvIconSelectionLabel.visibility = View.VISIBLE
        iconGrid.visibility = View.VISIBLE
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnReset = view.findViewById<Button>(R.id.btnReset)
        val btnAdd = view.findViewById<Button>(R.id.btnAdd)
        val layoutCurrentActivities = view.findViewById<LinearLayout>(R.id.layoutCurrentActivities)
        val tvCurrentActivitiesLabel = view.findViewById<TextView>(R.id.tvCurrentActivitiesLabel)
        
        var selectedIconRes: Int? = null
        var selectedIconName: String? = null
        
        fun refreshActivityList() {
            layoutCurrentActivities.removeAllViews()
            
            // Get disabled core activities
            val disabledCore = customActivityManager.getDisabledCoreActivities()
            
            // Show core activities first (if not disabled)
            val coreActivities = listOf(
                Triple("joint", "Joint", R.color.my_light_primary),
                Triple("cone", "Cone", R.color.my_light_primary),
                Triple("bowl", "Bowl", R.color.my_light_primary)
            )
            
            // Build a list of all activities in order
            val allOrderedActivities = mutableListOf<Triple<String, String, Boolean>>() // id, name, isCore
            val currentOrder = customActivityManager.getActivityOrder()
            val customActivities = customActivityManager.getCustomActivities()
            
            currentOrder.forEach { activityId ->
                when (activityId) {
                    "joint" -> if (!disabledCore.contains("joint")) allOrderedActivities.add(Triple(activityId, "Joint", true))
                    "cone" -> if (!disabledCore.contains("cone")) allOrderedActivities.add(Triple(activityId, "Cone", true))
                    "bowl" -> if (!disabledCore.contains("bowl")) allOrderedActivities.add(Triple(activityId, "Bowl", true))
                    else -> {
                        customActivities.find { it.id == activityId }?.let {
                            allOrderedActivities.add(Triple(activityId, it.name, false))
                        }
                    }
                }
            }
            
            Log.d("CUSTOM_ACTIVITY_REORDER", "📋 All activities in order: ${allOrderedActivities.map { it.second }}")
            
            // Create a container for activity items
            val activityItemsContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            
            // Display ALL activities in the saved order (not just core activities separately)
            allOrderedActivities.forEachIndexed { orderIndex, (activityId, activityName, isCore) ->
                val itemView = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 8.dpToPx(this@MainActivity)
                    }
                    setPadding(8.dpToPx(this@MainActivity), 8.dpToPx(this@MainActivity), 
                              8.dpToPx(this@MainActivity), 8.dpToPx(this@MainActivity))
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.rounded_edittext_background)
                }
                
                // Up arrow button for all activities
                val upButton = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "▲"
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        48.dpToPx(this@MainActivity),
                        48.dpToPx(this@MainActivity)
                    ).apply {
                        marginEnd = 4.dpToPx(this@MainActivity)
                    }
                    minWidth = 0
                    minHeight = 0
                    setPadding(0, 0, 0, 0)
                    isEnabled = orderIndex > 0
                    alpha = if (orderIndex > 0) 1.0f else 0.3f
                    setTextColor(if (isEnabled) Color.WHITE else Color.GRAY)
                    strokeColor = ColorStateList.valueOf(if (isEnabled) getColor(R.color.my_light_primary) else Color.GRAY)
                    strokeWidth = 2
                    
                    if (isEnabled) {
                        setOnClickListener {
                            Log.d("CUSTOM_ACTIVITY_REORDER", "⬆️ Up clicked for $activityName at index $orderIndex")
                            vibrateFeedback(30)
                            moveActivity(activityId, -1)
                            dialog.dismiss()
                            showCustomActivityDialog()
                        }
                    }
                }
                
                // Down arrow button for all activities
                val downButton = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "▼"
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        48.dpToPx(this@MainActivity),
                        48.dpToPx(this@MainActivity)
                    ).apply {
                        marginEnd = 8.dpToPx(this@MainActivity)
                    }
                    minWidth = 0
                    minHeight = 0
                    setPadding(0, 0, 0, 0)
                    isEnabled = orderIndex < allOrderedActivities.size - 1
                    alpha = if (orderIndex < allOrderedActivities.size - 1) 1.0f else 0.3f
                    setTextColor(if (isEnabled) Color.WHITE else Color.GRAY)
                    strokeColor = ColorStateList.valueOf(if (isEnabled) getColor(R.color.my_light_primary) else Color.GRAY)
                    strokeWidth = 2
                    
                    if (isEnabled) {
                        setOnClickListener {
                            Log.d("CUSTOM_ACTIVITY_REORDER", "⬇️ Down clicked for $activityName at index $orderIndex")
                            vibrateFeedback(30)
                            moveActivity(activityId, 1)
                            dialog.dismiss()
                            showCustomActivityDialog()
                        }
                    }
                }
                
                val nameText = TextView(this).apply {
                    text = if (isCore) "ADD $activityName (Core)" else "$activityName (Custom)"
                    textSize = 16f
                    setTextColor(if (isCore) getColor(R.color.my_light_primary) else Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    gravity = Gravity.CENTER_VERTICAL
                }
                
                // Different controls for core vs custom
                if (isCore) {
                    val deleteButton = Button(this).apply {
                        text = "Remove"
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        setOnClickListener {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Remove $activityName Button?")
                                .setMessage("This will hide the $activityName button. You can restore it later using the Reset button.")
                                .setPositiveButton("Remove") { _, _ ->
                                    val newDisabled = disabledCore.toMutableSet()
                                    newDisabled.add(activityId)
                                    customActivityManager.setDisabledCoreActivities(newDisabled)
                                    refreshActivityList()
                                    setupActivityButtons()
                                    
                                    // Update remaining slots text
                                    val maxCustom = customActivityManager.getMaxCustomActivities()
                                    val currentCustom = customActivityManager.getCustomActivities().size
                                    val remaining = maxCustom - currentCustom
                                    Toast.makeText(this@MainActivity, 
                                        "$remaining custom activity slot${if (remaining != 1) "s" else ""} available", 
                                        Toast.LENGTH_SHORT).show()
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                    
                    itemView.addView(upButton)
                    itemView.addView(downButton)
                    itemView.addView(nameText)
                    itemView.addView(deleteButton)
                } else {
                    // Custom activity - add delete button
                    val customActivity = customActivities.find { it.id == activityId }
                    val deleteButton = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "Delete"
                        textSize = 12f
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            48.dpToPx(this@MainActivity)
                        )
                        setTextColor(Color.parseColor("#FF6B6B")) // Red color for delete
                        strokeColor = ColorStateList.valueOf(Color.parseColor("#FF6B6B"))
                        strokeWidth = 2
                        setOnClickListener {
                            Log.d("CUSTOM_ACTIVITY_REORDER", "🗑️ Delete button clicked for $activityName")
                            vibrateFeedback(30)
                            customActivity?.let { showDeleteConfirmationDialog(it, dialog) }
                        }
                    }
                    
                    itemView.addView(upButton)
                    itemView.addView(downButton)
                    itemView.addView(nameText)
                    itemView.addView(deleteButton)
                }
                
                activityItemsContainer.addView(itemView)
            }
            
            // Decide whether to wrap in ScrollView based on activity count
            if (allOrderedActivities.size > 3) {
                // Calculate height for 3.5 items
                // Item height = 48dp (button) + 16dp (padding) = 64dp + 8dp margin = 72dp per item
                val itemHeightWithMargin = (48 + 16 + 8).dpToPx(this@MainActivity)
                val scrollHeight = (itemHeightWithMargin * 3.5).toInt()
                
                val scrollView = ScrollView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        scrollHeight
                    )
                    
                    // Always show scrollbar when scrolling is enabled
                    isVerticalScrollBarEnabled = true
                    scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
                    isScrollbarFadingEnabled = false
                }
                
                scrollView.addView(activityItemsContainer)
                layoutCurrentActivities.addView(scrollView)
            } else {
                // Add items directly without ScrollView for 3 or fewer activities
                layoutCurrentActivities.addView(activityItemsContainer)
            }
            
            // All activities are now displayed above in the correct order
            
            // Update save button state based on available slots
            val maxCustom = customActivityManager.getMaxCustomActivities()
            val hasSpace = customActivities.size < maxCustom
            if (!hasSpace) {
                etActivityName.isEnabled = false
                etActivityName.hint = "Remove an activity to add custom"
            } else {
                etActivityName.isEnabled = true
                etActivityName.hint = "Enter name (8 chars or icon)"
            }
            // Save button should always be clickable
            btnSave.isEnabled = true
            // Add button is disabled initially
            btnAdd.isEnabled = false
        }
        
        // Initial load
        refreshActivityList()
        
        // Setup icon selection
        val iconOptions = listOf(
            view.findViewById<LinearLayout>(R.id.iconOption1),
            view.findViewById<LinearLayout>(R.id.iconOption2),
            view.findViewById<LinearLayout>(R.id.iconOption3),
            view.findViewById<LinearLayout>(R.id.iconOption4),
            view.findViewById<LinearLayout>(R.id.iconOption5),
            view.findViewById<LinearLayout>(R.id.iconOption6)
        )
        
        val iconResources = listOf(
            R.drawable.ic_pills,
            R.drawable.ic_bong,
            R.drawable.ic_cough,
            R.drawable.ic_stretch,
            R.drawable.ic_cigarette,
            R.drawable.ic_water_glass
        )
        
        val iconNames = listOf("Pills", "Bong", "Cough", "Stretch", "Cigarette", "Water")
        
        iconOptions.forEachIndexed { index, option ->
            option.setOnClickListener {
                // Reset all backgrounds
                iconOptions.forEach { it.setBackgroundColor(Color.TRANSPARENT) }
                // Highlight selected
                option.setBackgroundColor(Color.parseColor("#33ff91a4"))
                selectedIconRes = iconResources[index]
                selectedIconName = iconNames[index]
                Log.d("CUSTOM_ACTIVITY", "🎨 Icon selected: ${iconNames[index]}")
            }
        }
        
        // Text change listener
        etActivityName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s?.toString() ?: ""
                Log.d("CUSTOM_ACTIVITY", "📝 Text changed: '$text' (length: ${text.length})")
                // Icon selection remains visible; do not auto-clear any selection
                btnSave.isEnabled = true
                // Enable Add button only when there's text
                btnAdd.isEnabled = text.isNotEmpty()
            }
        })
        
        // Reset button listener
        btnReset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset to Default?")
                .setMessage("This will remove all custom activities and their data, and restore the default Joint, Cone, and Bowl buttons. This action cannot be undone.")
                .setPositiveButton("Reset") { _, _ ->
                    customActivityManager.clearAllCustomActivities()
                    setupActivityButtons()
                    dialog.dismiss()
                    Toast.makeText(this, "Reset to default activities", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        // Button listeners
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        // Add button listener - adds activity without closing dialog
        btnAdd.setOnClickListener {
            val name = etActivityName.text.toString().trim()
            
            // Attempt to add if a name is provided and there is capacity
            val maxCustom = customActivityManager.getMaxCustomActivities()
            val hasSpace = customActivityManager.getCustomActivities().size < maxCustom
            
            if (name.isNotEmpty() && hasSpace) {
                // If name exceeds max length and no icon chosen, pick a default icon
                var finalIconRes = selectedIconRes
                var finalIconName = selectedIconName
                if (name.length > CustomActivity.MAX_NAME_LENGTH && finalIconRes == null) {
                    finalIconRes = CustomActivity.AVAILABLE_ICONS.firstOrNull()
                    finalIconName = CustomActivity.ICON_NAMES.firstOrNull()
                }
                
                val isIconBased = (finalIconRes != null) || (name.length > CustomActivity.MAX_NAME_LENGTH)
                val displayName = if (isIconBased) {
                    "ADD"
                } else {
                    "ADD ${name.uppercase()}"
                }
                
                val customActivity = CustomActivity(
                    // Always keep the full typed name (even when icon is used)
                    name = name,
                    displayName = displayName,
                    // Use icon if selected, or auto-assigned for long names
                    iconResId = finalIconRes
                )
                
                Log.d("CUSTOM_ACTIVITY", "💾 Adding custom activity: ${customActivity.name}")
                
                if (customActivityManager.addCustomActivity(customActivity)) {
                    Toast.makeText(this, "Custom activity added: ${customActivity.name}", Toast.LENGTH_SHORT).show()
                    
                    // Clear the input field and icon selection
                    etActivityName.setText("")
                    selectedIconRes = null
                    selectedIconName = null
                    iconOptions.forEach { it.setBackgroundColor(Color.TRANSPARENT) }
                    
                    // Refresh the activity list in the dialog
                    refreshActivityList()
                    
                    // Refresh main screen buttons
                    setupActivityButtons()
                    
                    // Broadcast that custom activities have changed
                    broadcastCustomActivitiesChanged()
                    
                    // Sync to cloud if in session
                    currentShareCode?.let { code ->
                        syncCustomActivityToCloud(customActivity, code)
                    }
                } else {
                    Toast.makeText(this, "Failed to add custom activity", Toast.LENGTH_SHORT).show()
                }
            } else if (!hasSpace) {
                Toast.makeText(this, "Remove an activity first to add custom", Toast.LENGTH_SHORT).show()
            }
            // Dialog stays open after adding
        }
        
        btnSave.setOnClickListener {
            val name = etActivityName.text.toString().trim()

            // Attempt to save if a name is provided and there is capacity
            val maxCustom = customActivityManager.getMaxCustomActivities()
            val hasSpace = customActivityManager.getCustomActivities().size < maxCustom

            if (name.isNotEmpty() && hasSpace) {
                // If name exceeds max length and no icon chosen, pick a default icon
                var finalIconRes = selectedIconRes
                var finalIconName = selectedIconName
                if (name.length > CustomActivity.MAX_NAME_LENGTH && finalIconRes == null) {
                    finalIconRes = CustomActivity.AVAILABLE_ICONS.firstOrNull()
                    finalIconName = CustomActivity.ICON_NAMES.firstOrNull()
                }

                val isIconBased = (finalIconRes != null) || (name.length > CustomActivity.MAX_NAME_LENGTH)
                val displayName = if (isIconBased) {
                    "ADD"
                } else {
                    "ADD ${name.uppercase()}"
                }

                val customActivity = CustomActivity(
                    // Always keep the full typed name (even when icon is used)
                    name = name,
                    displayName = displayName,
                    // Use icon if selected, or auto-assigned for long names
                    iconResId = finalIconRes
                )

                Log.d("CUSTOM_ACTIVITY", "💾 Saving custom activity: ${customActivity.name}")

                if (customActivityManager.addCustomActivity(customActivity)) {
                    Toast.makeText(this, "Custom activity added: ${customActivity.name}", Toast.LENGTH_SHORT).show()
                    setupActivityButtons() // Refresh buttons

                    // Broadcast that custom activities have changed
                    broadcastCustomActivitiesChanged()

                    // Sync to cloud if in session
                    currentShareCode?.let { code ->
                        syncCustomActivityToCloud(customActivity, code)
                    }
                } else {
                    Toast.makeText(this, "Failed to add custom activity", Toast.LENGTH_SHORT).show()
                }
            } else if (!hasSpace) {
                Toast.makeText(this, "Remove an activity first to add custom", Toast.LENGTH_SHORT).show()
            }

            // Close the dialog regardless of state — Save is always clickable
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun broadcastCustomActivitiesChanged() {
        // Send a local broadcast to notify fragments about custom activity changes
        val intent = Intent(ACTION_CUSTOM_ACTIVITIES_CHANGED)
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("CUSTOM_ACTIVITY", "📡 Broadcasting custom activities changed")
    }
    
    private fun moveCustomActivity(activityId: String, direction: Int) {
        // Delegate to the more general moveActivity function
        moveActivity(activityId, direction)
    }
    
    private fun moveActivity(activityId: String, direction: Int) {
        Log.d("CUSTOM_ACTIVITY_REORDER", "🔄 Moving activity $activityId by $direction")
        
        val currentOrder = customActivityManager.getActivityOrder().toMutableList()
        val currentIndex = currentOrder.indexOf(activityId)
        
        if (currentIndex == -1) {
            Log.e("CUSTOM_ACTIVITY_REORDER", "❌ Activity not found in order: $activityId")
            return
        }
        
        val newIndex = currentIndex + direction
        
        // Validate new index
        if (newIndex < 0 || newIndex >= currentOrder.size) {
            Log.w("CUSTOM_ACTIVITY_REORDER", "⚠️ Invalid new index: $newIndex")
            return
        }
        
        // Log the move
        Log.d("CUSTOM_ACTIVITY_REORDER", "📊 Current order: $currentOrder")
        Log.d("CUSTOM_ACTIVITY_REORDER", "🔄 Moving $activityId from position $currentIndex to $newIndex")
        
        // Swap the items
        val temp = currentOrder[currentIndex]
        currentOrder[currentIndex] = currentOrder[newIndex]
        currentOrder[newIndex] = temp
        
        Log.d("CUSTOM_ACTIVITY_REORDER", "📊 New order: $currentOrder")
        
        // Save the new order
        customActivityManager.saveActivityOrder(currentOrder)
        
        // Update the UI
        setupActivityButtons()
        
        // Broadcast the change
        broadcastCustomActivitiesChanged()
        
        Log.d("CUSTOM_ACTIVITY_REORDER", "✅ Successfully moved activity from position $currentIndex to $newIndex")
    }
    
    private fun showDeleteConfirmationDialog(activity: CustomActivity, parentDialog: Dialog) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${activity.name}?")
            .setMessage("This will delete all data associated with this custom activity. This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                Log.d("CUSTOM_ACTIVITY", "🗑️ Deleting custom activity: ${activity.name}")
                if (customActivityManager.deleteCustomActivity(activity.id)) {
                    Toast.makeText(this, "Custom activity deleted", Toast.LENGTH_SHORT).show()
                    setupActivityButtons() // Refresh buttons
                    
                    // Refresh the dialog to show updated list
                    parentDialog.dismiss()
                    showCustomActivityDialog()
                    
                    // Broadcast that custom activities have changed
                    broadcastCustomActivitiesChanged()
                    
                    // TODO: Delete from cloud if in session
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun handleCustomActivityClick(activity: CustomActivity) {
        Log.d("CUSTOM_ACTIVITY", "🎯 Handling custom activity click: ${activity.name}")
        
        // Use the same logic as regular activities
        // This will handle session checks, stash deduction, cloud sync, etc.
        logCustomActivitySafe(activity)
    }
    
    private fun logCustomActivitySafe(activity: CustomActivity) {
        Log.d("CUSTOM_ACTIVITY", "📱 === CUSTOM BUTTON PRESS DETECTED ===")
        Log.d("CUSTOM_ACTIVITY", "📱 logCustomActivitySafe called - activity: ${activity.name}")
        Log.d("CUSTOM_ACTIVITY", "📱 Session state - active: $sessionActive, start: $sessionStart")

        if (activity.name.equals("Cigarettes", ignoreCase = true)) {
            Log.d("CUSTOM_ACTIVITY", "📱 Redirecting custom 'Cigarettes' button to core cigarette flow")
            logHitSafe(ActivityType.CIGARETTE)
            return
        }

        if (smokers.isEmpty()) {
            Log.d("CUSTOM_ACTIVITY", "🎯 No smokers exist - showing add smoker dialog")
            addSmokerDialog.show()
            return
        }
        
        val hasCloudSmokers = smokers.any { it.isCloudSmoker }
        
        if (!sessionActive) {
            Log.w("CUSTOM_ACTIVITY", "🎯 WARNING: Activity logged without active session!")
            
            // Store pending custom activity
            pendingCustomActivity = activity
            pendingActivityType = ActivityType.CUSTOM // Use CUSTOM type for custom activities
            
            if (!hasCloudSmokers) {
                Log.d("CUSTOM_ACTIVITY", "🎯 No cloud smokers - showing no cloud user popup")
                showNoCloudUserPopup()
            } else {
                Log.d("CUSTOM_ACTIVITY", "🎯 Showing no active session popup for custom activity: ${activity.name}")
                showNoActiveSessionPopupForType(ActivityType.JOINT)
            }
            return
        }
        
        // NO MORE THROTTLING for custom activities
        val now = System.currentTimeMillis()
        
        val selectedPosition = binding.spinnerSmoker.selectedItemPosition
        val organizedSmokers = organizeSmokers().flatMap { it.smokers }
        val capturedSmoker = organizedSmokers.getOrNull(selectedPosition)
        
        if (capturedSmoker == null) {
            Toast.makeText(this, "Please select a valid smoker!", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Add custom activity to queue
        synchronized(queueLock) {
            val queuedActivity = QueuedActivity(ActivityType.CUSTOM, now, capturedSmoker, activity)
            activityQueue.add(queuedActivity)
            Log.d("CUSTOM_ACTIVITY", "📱 Added custom to queue: ${activity.name} for ${capturedSmoker.name}, queue size: ${activityQueue.size}")
        }
        
        updateOptimisticUI(capturedSmoker.name, ActivityType.CUSTOM, activity)
        
        // Process the queue
        processActivityQueue()
        return
    }
    
    private suspend fun proceedWithCustomActivityLog(
        activity: CustomActivity,
        timestamp: Long,
        stashSource: StashSource,
        capturedSmoker: Smoker
    ) {
        Log.d("CUSTOM_ACTIVITY", "🎯 proceedWithCustomActivityLog: activity=${activity.name}, source=$stashSource, smoker=${capturedSmoker.name}")
        
        val currentUserId = authManager.getCurrentUserId() ?: getAndroidDeviceId()
        
        // Determine payerStashOwnerId based on stash source
        val payerStashOwnerId = when (stashSource) {
            StashSource.MY_STASH -> null
            StashSource.THEIR_STASH -> "their_stash"
            StashSource.EACH_TO_OWN -> {
                if (capturedSmoker.cloudUserId == currentUserId || capturedSmoker.uid == currentUserId) {
                    null
                } else {
                    "other_${capturedSmoker.smokerId}"
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
                        verifyPasswordAndLogCustomActivity(capturedSmoker, activity, timestamp, password, payerStashOwnerId)
                    }
                )
            }
        } else {
            // No password needed or already verified
            logCustomActivityWithPayerAndSmoker(activity, timestamp, payerStashOwnerId, capturedSmoker)
        }
    }
    
    private fun verifyPasswordAndLogCustomActivity(
        smoker: Smoker,
        activity: CustomActivity,
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
                logCustomActivityWithPayerAndSmoker(activity, timestamp, payerStashOwnerId, verified)
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Incorrect password", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private suspend fun logCustomActivityWithPayerAndSmoker(
        activity: CustomActivity,
        now: Long,
        payerStashOwnerId: String?,
        capturedSmoker: Smoker
    ) {
        Log.d("CUSTOM_ACTIVITY", "🎯 === logCustomActivityWithPayerAndSmoker START ===")
        Log.d("CUSTOM_ACTIVITY", "🎯 Activity: ${activity.name}, Time: $now, PayerStashOwnerId: '$payerStashOwnerId', Smoker: ${capturedSmoker.name}")

        if (activity.name.equals("Cigarettes", ignoreCase = true)) {
            Log.d("CUSTOM_ACTIVITY", "🎯 Redirecting custom 'Cigarettes' activity to core cigarette log")
            logHitWithPayerAndSmoker(ActivityType.CIGARETTE, now, payerStashOwnerId, capturedSmoker)
            return
        }

        if (!sessionActive) {
            Log.w("CUSTOM_ACTIVITY", "🎯 Cannot log hit - session not active")
            return
        }
        
        val adjustedNow = now - rewindOffset
        val stashViewModel = ViewModelProvider(this).get(StashViewModel::class.java)
        val currentStash = stashViewModel.currentStash.value
        val ratios = stashViewModel.ratios.value
        
        // Create the activity log with custom activity info
        val activityLog = ActivityLog(
            id = 0L,
            smokerId = capturedSmoker.smokerId,
            consumerId = capturedSmoker.smokerId,
            payerStashOwnerId = payerStashOwnerId,
            type = ActivityType.CUSTOM, // Use CUSTOM type for custom activities
            timestamp = adjustedNow,
            sessionId = sessionStatsVM.currentSessionId.value,
            sessionStartTime = if (sessionActive) sessionStart else null,
            customActivityId = activity.id,
            customActivityName = activity.name,
            gramsAtLog = 0.0, // Custom activities don't consume from stash
            pricePerGramAtLog = 0.0 // No cost for custom activities
        )
        
        // Store in local database first
        val insertedId = withContext(Dispatchers.IO) {
            val id = repo.insert(activityLog)
            Log.d("CUSTOM_ACTIVITY", "🎯 INSERTED custom activity ID $id for smoker ${capturedSmoker.name}")
            id
        }
        
        // Update active session summary
        updateActiveSessionSummary()
        
        // Handle cloud sync if in a cloud session
        if (currentShareCode != null) {
            val smokerActivityUid = if (capturedSmoker.isCloudSmoker) {
                capturedSmoker.cloudUserId!!
            } else {
                "local_${capturedSmoker.uid}"
            }
            val deviceId = getAndroidDeviceId()

            // Sync custom activity to cloud
            sessionSyncService.addCustomActivityToRoom(
                shareCode = currentShareCode!!,
                smokerUid = smokerActivityUid,
                smokerName = capturedSmoker.name,
                customActivity = activity,
                timestamp = adjustedNow,
                deviceId = deviceId
            ).fold(
                onSuccess = {
                    Log.d("CUSTOM_ACTIVITY", "🎯 Custom activity synced to cloud room for ${capturedSmoker.name}")
                    lastHitCameFromUI = true
                    handler.postDelayed({
                        lastHitCameFromUI = false
                    }, 500)
                },
                onFailure = { error ->
                    Log.e("CUSTOM_ACTIVITY", "🎯 Failed to sync to room: ${error.message}")
                    // Fall back to syncing as JOINT type
                    sessionSyncService.addActivityToRoom(
                        shareCode = currentShareCode!!,
                        smokerUid = smokerActivityUid,
                        smokerName = capturedSmoker.name,
                        activityType = ActivityType.JOINT,
                        timestamp = adjustedNow,
                        deviceId = deviceId
                    ).fold(
                        onSuccess = {
                            Log.d("CUSTOM_ACTIVITY", "🎯 Fallback JOINT synced for ${capturedSmoker.name}")
                        },
                        onFailure = { fallbackError ->
                            Log.e("CUSTOM_ACTIVITY", "🎯 Fallback JOINT sync failed: ${fallbackError.message}")
                            val handled = handleCloudSyncFailure(
                                error = fallbackError,
                                shareCode = currentShareCode!!,
                                smokerUid = smokerActivityUid,
                                smokerName = capturedSmoker.name,
                                activityType = ActivityType.JOINT,
                                timestamp = adjustedNow,
                                deviceId = deviceId,
                                localActivityId = insertedId.toString()
                            )
                            if (!handled) {
                                Log.w("CUSTOM_ACTIVITY", "🎯 Fallback failure not queued (non-quota issue)")
                            }
                        }
                    )
                }
            )
        } else {
            // Local session - skip immediate refresh if processing queue
            // Stats will be refreshed after all queued activities are processed
            if (!isProcessingQueue) {
                refreshLocalSessionStatsIfNeeded()
            }
        }
        
        // Add to activity history for undo functionality
        if (sessionActive) {
            Log.d("CUSTOM_ACTIVITY", "🎯 UNDO FIX: Adding custom activity to activityHistory")
            Log.d("CUSTOM_ACTIVITY", "🎯 UNDO FIX: type=CUSTOM, customId=${activity.id}, customName=${activity.name}")
            
            // Create a copy of the activity log for history (with the inserted ID if needed)
            val historyLog = activityLog.copy()
            
            activityHistory.add(historyLog)
            if (activityHistory.size > 10) {
                activityHistory.removeAt(0)
            }
            Log.d("CUSTOM_ACTIVITY", "🎯 UNDO FIX: Activity history size after add: ${activityHistory.size}")
            
            // Update timestamps
            activitiesTimestamps.add(adjustedNow)
            activitiesTimestamps.sort()
            actualLastLogTime = activitiesTimestamps.maxOrNull() ?: adjustedNow
            lastLogTime = adjustedNow
            
            // Update intervals
            val activitiesBeforeThis = activitiesTimestamps.filter { it < adjustedNow }
            if (activitiesBeforeThis.isNotEmpty()) {
                val prevActivity = activitiesBeforeThis.last()
                val interval = adjustedNow - prevActivity
                lastIntervalMillis = interval
                intervalsList.add(interval)
            } else {
                intervalsList.add(0L)
            }
            
            // Update undo button visibility
            updateUndoButtonVisibility()
        }
        
        // Update goals tracking for custom activities
        updateCustomActivityGoals(activity)
        
        Log.d("CUSTOM_ACTIVITY", "🎯 === logCustomActivityWithPayerAndSmoker END ===")
    }
    
    private fun updateCustomActivityGoals(activity: CustomActivity) {
        // Update goals that track custom activities
        lifecycleScope.launch {
            // Get current smoker name using the same pattern as other functions
            val selectedPosition = binding.spinnerSmoker.selectedItemPosition
            val organizedSmokers = organizeSmokers().flatMap { it.smokers }
            val selectedSmokerObj = organizedSmokers.getOrNull(selectedPosition)
            val currentSmokerName = selectedSmokerObj?.name ?: "Sam"
            
            val currentShareCode = getCurrentShareCode()
            
            Log.d("CUSTOM_ACTIVITY", "📊 === GOAL UPDATE FOR CUSTOM ACTIVITY ===")
            Log.d("CUSTOM_ACTIVITY", "📊 Activity name: ${activity.name}")
            Log.d("CUSTOM_ACTIVITY", "📊 Activity ID: '${activity.id}'")
            Log.d("CUSTOM_ACTIVITY", "📊 Current smoker: $currentSmokerName")
            Log.d("CUSTOM_ACTIVITY", "📊 Session share code: $currentShareCode")
            
            // Call the new unified goal service function
            Log.d("CUSTOM_ACTIVITY", "📊 Calling goalService.updateGoalProgressForSelectedActivity")
            Log.d("CUSTOM_ACTIVITY", "📊   with customActivityId: '${activity.id}'")
            
            goalService.updateGoalProgressForSelectedActivity(
                activityType = ActivityType.JOINT, // Custom activities are logged as JOINT
                customActivityId = activity.id,
                customActivityName = activity.name,
                sessionShareCode = currentShareCode,
                currentSmokerName = currentSmokerName
            )
            
            Log.d("CUSTOM_ACTIVITY", "📊 Goal service call completed")
        }
    }
    
    private fun syncCustomActivityToCloud(activity: CustomActivity, roomCode: String) {
        Log.d("CUSTOM_ACTIVITY", "☁️ Syncing custom activity to cloud: ${activity.name}")
        // TODO: Implement cloud sync for custom activities
    }
    
    private fun syncCustomActivityLogToCloud(log: ActivityLog, roomCode: String) {
        Log.d("CUSTOM_ACTIVITY", "☁️ Syncing custom activity log to cloud")
        // TODO: Implement cloud sync for custom activity logs
    }
    
    private fun updateLayoutPosition(isAtBottom: Boolean) {
        val rootLayout = binding.mainActivityRootLayout
        val topSection = binding.topSectionContainer
        val tabSectionContainer = binding.tabSectionContainer
        val tabLayout = binding.tabLayout
        val viewPager = binding.viewPager
        
        // Get system window insets
        val statusBarHeight = getStatusBarHeight()
        val navigationBarHeight = getNavigationBarHeight()
        
        // Remove views first
        rootLayout.removeView(topSection)
        rootLayout.removeView(tabSectionContainer)
        rootLayout.removeView(viewPager)
        
        // Re-add in the correct order
        if (isAtBottom) {
            // Order: TabSectionContainer (with TabLayout inside), ViewPager, TopSection
            rootLayout.addView(tabSectionContainer)
            rootLayout.addView(viewPager)
            rootLayout.addView(topSection)
            
            // Add padding to the CONTAINER to extend the background up
            tabSectionContainer.setPadding(
                0,
                statusBarHeight,
                0,
                0
            )
            
            // TabLayout doesn't need padding since container has it
            tabLayout.setPadding(
                tabLayout.paddingLeft,
                0,
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
            
            // Adjust button container margin when at bottom
            val buttonContainer = findViewById<LinearLayout>(R.id.buttonContainer)
            val layoutParams = buttonContainer.layoutParams as LinearLayout.LayoutParams
            // Check if timers are visible to set correct margin
            if (timersVisible) {
                layoutParams.topMargin = (-19).dpToPx(this) // Expanded state margin
            } else {
                layoutParams.topMargin = (-5).dpToPx(this) // Collapsed state margin
            }
            buttonContainer.layoutParams = layoutParams
            
            // Set ViewPager to take remaining space
            viewPager.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        } else {
            // Order: TopSection, TabSectionContainer (with TabLayout inside), ViewPager (original)
            rootLayout.addView(topSection)
            rootLayout.addView(tabSectionContainer)
            rootLayout.addView(viewPager)
            
            // Reset padding when in normal position
            tabLayout.setPadding(
                tabLayout.paddingLeft,
                0,
                tabLayout.paddingRight,
                tabLayout.paddingBottom
            )
            
            tabSectionContainer.setPadding(0, 0, 0, 0)
            
            topSection.setPadding(
                topSection.paddingLeft,
                topSection.paddingTop,
                topSection.paddingRight,
                0
            )
            
            // Adjust button container margin when at top
            val buttonContainer = findViewById<LinearLayout>(R.id.buttonContainer)
            val layoutParams = buttonContainer.layoutParams as LinearLayout.LayoutParams
            
            // Check if timers are visible to set correct margin
            if (timersVisible) {
                layoutParams.topMargin = (-19).dpToPx(this) // Expanded state margin
            } else {
                layoutParams.topMargin = (-5).dpToPx(this) // Collapsed state margin
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
    private fun selectSmoker(smoker: Smoker, broadcastToRoom: Boolean = true) {
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

            if (broadcastToRoom && !isApplyingRemoteSpinnerUpdate) {
                val shareCode = currentShareCode
                val smokerRoomId = getRoomSmokerId(smoker)

                if (!shareCode.isNullOrEmpty() && !smokerRoomId.isNullOrEmpty()) {
                    sharedActiveSmokerId = smokerRoomId

                    lifecycleScope.launch {
                        sessionSyncService.updateActiveSmokerInRoom(shareCode, smokerRoomId).fold(
                            onSuccess = {
                                Log.d(TAG, "🔄📡 Active smoker synced to room: ${smoker.name} ($smokerRoomId)")
                            },
                            onFailure = { error ->
                                Log.e(TAG, "🔄📡 Failed to sync active smoker: ${error.message}")
                            }
                        )
                    }
                }
            }
        }

        // Clear font/color caches if randomization enabled
        smokerManager.clearFontCache(smoker.smokerId)
        smokerManager.clearColorCache(smoker.smokerId)

        applyFontToSpinner()
    }

    // Wrapper method to delete activity with goal and stash reversal
    private fun deleteActivityWithReversal(log: ActivityLog) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                repo.deleteWithCallbacks(
                    log = log,
                    onReverseGoal = { activityLog, smoker ->
                        // Reverse goal progress BEFORE deleting
                        if (::goalService.isInitialized) {
                            Log.d(TAG, "🎯 Reversing goal progress for deleted activity: ${activityLog.type} by ${smoker.name}")
                            val sessionShareCode = if (sessionActive) currentShareCode else null
                            
                            try {
                                if (activityLog.type == ActivityType.CUSTOM && !activityLog.customActivityId.isNullOrEmpty()) {
                                    goalService.reverseGoalProgressForSelectedActivity(
                                        activityType = activityLog.type,
                                        customActivityId = activityLog.customActivityId,
                                        customActivityName = activityLog.customActivityName,
                                        sessionShareCode = sessionShareCode,
                                        currentSmokerName = smoker.name
                                    )
                                } else {
                                    goalService.reverseGoalProgressForActivity(
                                        activityType = activityLog.type,
                                        sessionShareCode = sessionShareCode,
                                        smokerName = smoker.name
                                    )
                                }
                                Log.d(TAG, "🎯✅ Goal progress reversed successfully")
                            } catch (e: Exception) {
                                Log.e(TAG, "🎯❌ Error reversing goal progress: ${e.message}", e)
                            }
                        }
                    },
                    onRestoreStash = { activityLog, smoker ->
                        // Restore stash BEFORE deleting
                        if (::stashViewModel.isInitialized && stashViewModel.currentStash.value != null) {
                            Log.d(TAG, "📦 Restoring stash for deleted activity: ${activityLog.type} by ${smoker.name}")
                            try {
                                withContext(Dispatchers.Main) {
                                    stashViewModel.undoStashConsumption(activityLog, smoker.name)
                                }
                                Log.d(TAG, "📦✅ Stash restored successfully")
                            } catch (e: Exception) {
                                Log.e(TAG, "📦❌ Error restoring stash: ${e.message}", e)
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in deleteActivityWithReversal: ${e.message}", e)
            }
        }
    }

    private fun setupTabs() {
        val historyFrag = HistoryFragment().apply {
            onDeleteLog = { log ->
                deleteActivityWithReversal(log)
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
            // onDeleteSummaryWithActivities is now handled by HistoryViewModel
            onResumeSummary = { summary ->
                // If the tapped summary is the active one, navigate to the live Sesh view
                val isActiveSummary = summary.isActive
                val isCurrentActive = sessionActive && (
                        (editingSummaryId != null && editingSummaryId == summary.id) ||
                        (sessionStart > 0 && summary.timestamp == sessionStart)
                )
                if (isActiveSummary && isCurrentActive) {
                    Log.d(
                        "SeshFlow",
                        "History tap on ACTIVE summary; navigating to live Sesh (summaryId=${summary.id}, sessionStart=$sessionStart)"
                    )
                    // Go to Sesh tab and ensure live mode
                    binding.viewPager.currentItem = 1
                    val sesh = supportFragmentManager.fragments.filterIsInstance<SeshFragment>().firstOrNull()
                    if (sesh != null) {
                        Log.d("SeshFlow", "Focusing live Sesh in SeshFragment (onSessionStarted)")
                        sesh.onSessionStarted()
                    } else {
                        Log.d("SeshFlow", "SeshFragment not found while focusing live session; tab switch should create it")
                    }
                } else {
                    Log.d(
                        "SeshFlow",
                        "History card clicked for preview: id=${summary.id}, room=${summary.roomName}, code=${summary.shareCode} (isActive=$isActiveSummary, isCurrentActive=$isCurrentActive)"
                    )
                    previewSession(summary)
                }
            }
            setConfettiHelper(confettiHelper)
        }

        val seshFrag = SeshFragment().apply {
            onResumeSesh = {
                Log.d(TAG, "📱 Resume button clicked in SeshFragment")
                val toResume = lastLoadedSummary
                if (toResume != null) {
                    Log.d("SeshFlow", "Resume FAB pressed; resuming previewed summary id=${toResume.id}")
                    resumeSession(toResume)
                } else {
                    Log.d("SeshFlow", "Resume FAB pressed with no preview; falling back to lastSummary")
                    resumeLastSummary()
                }
            }
            setConfettiHelper(confettiHelper)
        }

        val statsFrag = StatsFragment()
        val graphFrag = GraphFragment()
        val calendarFrag = CalendarFragment()
        val stashFrag = StashFragment()

        val chatFrag = ChatFragment().apply {
            setConfettiHelper(confettiHelper)
        }

        val goalFrag = GoalFragment().apply {
            setConfettiHelper(confettiHelper)
        }

        val aboutOrInboxFrag = AboutOrInboxFragment()

        val fragmentList = listOf(
            historyFrag,
            seshFrag,
            statsFrag,
            graphFrag,
            calendarFrag,
            stashFrag,
            chatFrag,
            goalFrag,  // Index 7
            aboutOrInboxFrag
        )
        
        // Log all fragments being added
        fragmentList.forEachIndexed { index, fragment ->
            Log.d("FIRST_LAUNCH_FLOW", "📱 ViewPager fragment[$index]: ${fragment.javaClass.simpleName}")
        }
        
        binding.viewPager.adapter = ViewPagerAdapter(this, fragmentList)
        
        // IMPORTANT: Preload all fragments to ensure they're available for dialogs
        // This ensures GoalFragment (at index 7) is loaded even when not visible
        binding.viewPager.offscreenPageLimit = 8  // Load all 9 fragments (0-8)
        Log.d("FIRST_LAUNCH_FLOW", "📱 Set ViewPager offscreenPageLimit to 8 - all fragments will be preloaded")
        
        // Force initialization of all fragments
        binding.viewPager.post {
            Log.d("FIRST_LAUNCH_FLOW", "🔄 Forcing fragment initialization...")
            // Navigate to last fragment and back to ensure all are loaded
            binding.viewPager.setCurrentItem(8, false)
            handler.postDelayed({
                binding.viewPager.setCurrentItem(0, false)
                Log.d("FIRST_LAUNCH_FLOW", "✅ Fragment initialization complete")
            }, 100)
        }
        
        // Restore last selected tab
        val savedTabPosition = prefs.getInt("last_selected_tab", 0)
        binding.viewPager.post {
            binding.viewPager.setCurrentItem(savedTabPosition, false)
        }

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
                4 -> tab.setIcon(R.drawable.ic_calendar_selector)  // Calendar icon only
                5 -> tab.text = getString(R.string.tab_stash)
                6 -> tab.text = getString(R.string.tab_chat)
                7 -> tab.text = getString(R.string.tab_goals)
                8 -> tab.setIcon(R.drawable.ic_about_selector)  // This stays as is
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
                    // Save the selected tab position
                    prefs.edit().putInt("last_selected_tab", tab.position).apply()
                    
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
                        // Auto-switch button section to top when chat tab is selected and it's at bottom
                        val isLayoutAtBottom = prefs.getBoolean("layout_at_bottom", false)
                        if (isLayoutAtBottom) {
                            Log.d("MainActivity", "Chat tab selected with bottom layout - auto-switching to top")
                            prefs.edit().putBoolean("layout_at_bottom", false).commit()
                            updateLayoutPosition(false)
                            animateLayoutRotation()
                            
                            // Show toast to inform user
                            Toast.makeText(this@MainActivity, "Controls moved to top for chat", Toast.LENGTH_SHORT).show()
                            
                            // Notify ChatFragment after layout change
                            Handler(Looper.getMainLooper()).postDelayed({
                                val chatFragment = supportFragmentManager.fragments.find { it is ChatFragment } as? ChatFragment
                                chatFragment?.let {
                                    it.onLayoutPositionChanged()
                                    it.onTabSelected()
                                }
                            }, 300)
                        } else {
                            // Normal chat tab selection when already at top
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
                if (intent?.action == "com.vibecode.cloudcounter.ACTIVITY_UNDONE") {
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
                if (intent?.action == "com.vibecode.cloudcounter.SESSION_REWOUND") {
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

        // Register rewind receiver
        val rewindFilter = IntentFilter("com.vibecode.cloudcounter.SESSION_REWOUND")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(rewindReceiver, rewindFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(rewindReceiver, rewindFilter)
        }
        Log.d(TAG, "⏪📡 Rewind broadcast receiver registered")
        
        // Register skip receiver
        skipReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "🎯 SKIP DEBUG: BroadcastReceiver onReceive called")
                Log.d(TAG, "🎯 SKIP DEBUG: Intent action: ${intent?.action}")
                Log.d(TAG, "🎯 SKIP DEBUG: Intent extras: ${intent?.extras}")
                
                if (intent?.action == "com.vibecode.cloudcounter.SKIP_SMOKER") {
                    Log.d(TAG, "🎯 SKIP DEBUG: Correct action received")
                    val requestSkip = intent.getBooleanExtra("request_skip", false)
                    val fromSmoker = intent.getStringExtra("current_smoker")
                    Log.d(TAG, "🎯 SKIP DEBUG: request_skip = $requestSkip, from_smoker = $fromSmoker")
                    
                    if (requestSkip) {
                        Log.d(TAG, "🎯 SKIP DEBUG: Processing skip request from GiantCounterActivity")
                        Log.d(TAG, "🎯 SKIP DEBUG: Current spinner selection before skip: ${(binding.spinnerSmoker.selectedItem as? Smoker)?.name}")
                        
                        // Skip to the next smoker
                        moveToNextActiveSmoker()
                        
                        // Update SharedPreferences with the new smoker
                        val currentSmoker = binding.spinnerSmoker.selectedItem as? Smoker
                        currentSmoker?.let {
                            Log.d(TAG, "🎯 SKIP DEBUG: New smoker after skip: ${it.name}")
                            val prefs = getSharedPreferences("sesh", MODE_PRIVATE)
                            prefs.edit().putString("selected_smoker", it.name).apply()
                            Log.d(TAG, "🎯 SKIP DEBUG: Updated SharedPreferences with: ${it.name}")
                        } ?: run {
                            Log.e(TAG, "🎯 SKIP DEBUG: ERROR - currentSmoker is null after moveToNextActiveSmoker")
                        }
                    }
                } else {
                    Log.d(TAG, "🎯 SKIP DEBUG: Received broadcast with different action: ${intent?.action}")
                }
            }
        }
        val skipFilter = IntentFilter("com.vibecode.cloudcounter.SKIP_SMOKER")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(skipReceiver, skipFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(skipReceiver, skipFilter)
        }
        Log.d(TAG, "⏭️📡 Skip broadcast receiver registered")

        // Register undo receiver
        val undoFilter = IntentFilter("com.vibecode.cloudcounter.ACTIVITY_UNDONE")
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

                if (intent?.action == "com.vibecode.cloudcounter.UPDATE_SMOKER_SELECTION") {
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

        val filter = IntentFilter("com.vibecode.cloudcounter.UPDATE_SMOKER_SELECTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smokerUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smokerUpdateReceiver, filter)
        }
        Log.d(TAG, "🔄📡 Broadcast receiver registered")

        // Add activity deletion receiver
        deletionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.vibecode.cloudcounter.ACTIVITY_DELETED") {
                    Log.d(TAG, "📡 Received activity deletion broadcast")

                    val activityType = intent.getStringExtra("activityType")
                    val smokerId = intent.getLongExtra("smokerId", -1)
                    val smokerName = intent.getStringExtra("smokerName") ?: "Unknown"
                    val timestamp = intent.getLongExtra("timestamp", -1)

                    Log.d(TAG, "📡 Deleted activity: $activityType for $smokerName at $timestamp")
                    
                    // Track this deletion to prevent re-adding during reconciliation
                    if (activityType != null && timestamp != -1L) {
                        // Track with local smokerId
                        val activityKey = "${smokerId}_${activityType}_${timestamp}"
                        recentlyDeletedActivities.add(activityKey)
                        Log.d(TAG, "📡 Added to recently deleted: $activityKey")
                        
                        // Also track with just type and timestamp (to catch any smoker format)
                        val universalKey = "${activityType}_${timestamp}"
                        recentlyDeletedActivities.add(universalKey)
                        Log.d(TAG, "📡 Added universal key to recently deleted: $universalKey")
                        
                        // Remove from tracking after 30 seconds (increased from 10)
                        lifecycleScope.launch {
                            delay(30000)
                            recentlyDeletedActivities.remove(activityKey)
                            recentlyDeletedActivities.remove(universalKey)
                            Log.d(TAG, "📡 Removed from recently deleted tracking: $activityKey and $universalKey")
                        }
                    }

                    // Refresh all fragments and stats
                    // Add a small delay to ensure the deletion has completed in the database
                    lifecycleScope.launch {
                        // Wait a bit longer to ensure deletion is really complete
                        delay(100)
                        
                        // Force refresh after deletion, even if in cloud room
                        refreshLocalSessionStatsIfNeeded(forceRefresh = true)
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
                        
                        // CRITICAL: Refresh SeshFragment to update per-smoker stats
                        val seshFragment = supportFragmentManager.fragments
                            .find { it is SeshFragment } as? SeshFragment
                        seshFragment?.refreshStats()
                        Log.d(TAG, "📡 Triggered SeshFragment refresh after activity deletion (with delay)")
                    }
                }
            }
        }

        val deletionFilter = IntentFilter("com.vibecode.cloudcounter.ACTIVITY_DELETED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(deletionReceiver, deletionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(deletionReceiver, deletionFilter)
        }

        // Add SESSION_ENDED receiver to handle session ending from History
        val sessionEndedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.vibecode.cloudcounter.SESSION_ENDED") {
                    Log.d(TAG, "📡 Received SESSION_ENDED broadcast from ${intent.getStringExtra("source")}")
                    
                    // End the session and refresh everything
                    lifecycleScope.launch {
                        // Clear session state
                        sessionActive = false
                        sessionStart = 0L
                        sessionStatsVM.endSession()
                        
                        // Clear the current room info if any
                        currentShareCode = null
                        sharedActiveSmokerId = null
                        currentRoomName = null
                        sessionStatsVM.clearRoomInfo()
                        
                        // Update UI
                        withContext(Dispatchers.Main) {
                            updateUIForSessionState()
                            
                            // Refresh SeshFragment to show no active session
                            val seshFragment = supportFragmentManager.fragments
                                .find { it is SeshFragment } as? SeshFragment
                            seshFragment?.onSessionEnded()
                            
                            Toast.makeText(this@MainActivity, "Session ended from History", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        
        val sessionEndedFilter = IntentFilter("com.vibecode.cloudcounter.SESSION_ENDED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sessionEndedReceiver, sessionEndedFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(sessionEndedReceiver, sessionEndedFilter)
        }

        // Add auto-advance receiver for notification activities - COMPLETE REPLACEMENT
        autoAdvanceReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.vibecode.cloudcounter.TRIGGER_AUTO_ADVANCE") {
                    Log.d(TAG, "🔄📡 Received auto-advance broadcast from notification")

                    // Check if this came from a notification
                    val fromNotification = intent.getBooleanExtra("from_notification", false)
                    val activityType = intent.getStringExtra("activity_type")
                    val smokerId = intent.getLongExtra("smoker_id", -1L)

                    Log.d(TAG, "🔄📡 From notification: $fromNotification, Activity: $activityType, Smoker ID: $smokerId")

                    runOnUiThread {
                        // Get the current state
                        val shareCode = currentShareCode
                        val sections = organizeSmokers()
                        val organizedSmokers = sections.flatMap { it.smokers }

                        if (organizedSmokers.isEmpty()) {
                            Log.w(TAG, "🔄📡 No smokers available for rotation")
                            return@runOnUiThread
                        }

                        if (shareCode != null && !fromNotification) {
                            // Cloud session - room sync will handle rotation (unless from notification)
                            Log.d(TAG, "🔄📡 Cloud session detected, rotation handled by room sync")
                        } else {
                            // Local session OR notification action - manually advance the spinner
                            val sessionType = if (shareCode != null) "Cloud session (from notification)" else "Local session"
                            Log.d(TAG, "🔄📡 $sessionType, manually advancing spinner")

                            // Get current position
                            val currentPos = binding.spinnerSmoker.selectedItemPosition
                            Log.d(TAG, "🔄📡 Current spinner position: $currentPos")

                            // Calculate next position
                            val nextPos = if (currentPos >= 0 && currentPos < organizedSmokers.size - 1) {
                                currentPos + 1
                            } else {
                                0 // Wrap around to first smoker
                            }

                            Log.d(TAG, "🔄📡 Next spinner position: $nextPos")

                            if (nextPos < organizedSmokers.size) {
                                val nextSmoker = organizedSmokers[nextPos]
                                Log.d(TAG, "🔄📡 Advancing to: ${nextSmoker.name} (ID: ${nextSmoker.smokerId})")

                                // Update the spinner selection
                                binding.spinnerSmoker.setSelection(nextPos, false)

                                // Update the application's default smoker
                                val app = application as CloudCounterApplication
                                app.defaultSmokerId = nextSmoker.smokerId

                                // Call selectSmoker to ensure all state is updated
                                selectSmoker(nextSmoker)

                                // Force adapter refresh to ensure UI updates
                                smokerAdapterNew.notifyDataSetChanged()

                                Log.d(TAG, "🔄📡 Successfully advanced to ${nextSmoker.name}")
                            } else {
                                Log.e(TAG, "🔄📡 Invalid next position: $nextPos (smokers count: ${organizedSmokers.size})")
                            }
                        }
                    }
                }
            }
        }

        val autoAdvanceFilter = IntentFilter("com.vibecode.cloudcounter.TRIGGER_AUTO_ADVANCE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(autoAdvanceReceiver, autoAdvanceFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(autoAdvanceReceiver, autoAdvanceFilter)
        }

        Log.d(TAG, "📡 All broadcast receivers registered (undo, smoker update, deletion, auto-advance)")
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
                    sharedActiveSmokerId = null
                    currentRoomName = null
                    sessionStatsVM.clearRoomInfo()
                    
                    // Log pending activity if there is one
                    pendingActivityType?.let { type ->
                        Log.d(TAG, "🏠 Logging pending activity: $type")
                        // Small delay to ensure UI is updated
                        lifecycleScope.launch {
                            delay(100)
                            if (type == ActivityType.CUSTOM && pendingCustomActivity != null) {
                                // Handle custom activity
                                Log.d(TAG, "🏠 Logging pending custom activity: ${pendingCustomActivity?.name}")
                                pendingCustomActivity?.let { customActivity ->
                                    logCustomActivitySafe(customActivity)
                                }
                                pendingCustomActivity = null
                            } else {
                                // Handle regular activity
                                logHitSafe(type)
                            }
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
        sharedActiveSmokerId = null
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
                if (type == ActivityType.CUSTOM && pendingCustomActivity != null) {
                    // Handle custom activity
                    Log.d(TAG, "🏠 Logging pending custom activity: ${pendingCustomActivity?.name}")
                    pendingCustomActivity?.let { customActivity ->
                        logCustomActivitySafe(customActivity)
                    }
                    pendingCustomActivity = null
                } else {
                    // Handle regular activity
                    logHitSafe(type)
                }
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

        // REMOVED CHECK: Always show full cloud options menu regardless of sign-in status
        // The individual options will check for sign-in and show appropriate popup
        Log.d(TAG, "🏠 Showing cloud options menu (signed in: ${currentUserId != null})")

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

        // GUARANTEED 1-SECOND FADE USING HANDLER (reduced by 50%)
        performManualFadeIn(dialogView, 1000L)
    }

    /**
     * Manually animates the fade in using a Handler to guarantee the animation runs for the full duration
     * This bypasses any system optimizations that might skip the animation
     */
    private fun calculateRoundsFromActivities(activities: List<ActivityLog>): Int {
        if (activities.isEmpty()) return 0
        
        // Group activities by smoker to track rounds
        val smokerActivities = mutableMapOf<Long, Int>()
        var rounds = 0
        
        for (activity in activities.sortedBy { it.timestamp }) {
            val smokerId = activity.smokerId
            val currentCount = smokerActivities.getOrDefault(smokerId, 0)
            smokerActivities[smokerId] = currentCount + 1
            
            // When we see a smoker for the second+ time, it's a new round
            if (currentCount > 0 && smokerActivities.values.all { it > rounds }) {
                rounds++
            }
        }
        
        // If everyone has had at least one hit, we're in round 1 minimum
        if (smokerActivities.isNotEmpty() && smokerActivities.values.all { it > 0 }) {
            rounds = smokerActivities.values.minOrNull() ?: 0
        }
        
        return rounds
    }

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
            animateCardSelection(dialog) {
                if (authManager.getCurrentUserId() == null) {
                    showNotSignedInPopup()
                } else {
                    promptNewRoom()
                }
            }
        }
        contentLayout.addView(newRoomCard)

        // Sesh Roulette button (primary - styled like New Room)
        val rouletteCard = createCloudOptionCard("🎲", "Sesh Roulette", "Jump into a random social sesh", true) {
            Log.d("Roulette", "UI click: Sesh Roulette selected")
            animateCardSelection(dialog) {
                if (authManager.getCurrentUserId() == null) {
                    showNotSignedInPopup()
                } else {
                    startSeshRoulette()
                }
            }
        }
        contentLayout.addView(rouletteCard)

        // Existing Room button (secondary)
        val existingRoomCard = createCloudOptionCard("🔥", "Existing Room", "Continue active sessions", false) {
            animateCardSelection(dialog) {
                if (authManager.getCurrentUserId() == null) {
                    showNotSignedInPopup()
                } else {
                    showExistingRoomsDialog()
                }
            }
        }
        contentLayout.addView(existingRoomCard)

        // Join by Code button (secondary)
        val joinByCodeCard = createCloudOptionCard("🔗", "Join by Code", "Enter a share code", false) {
            animateCardSelection(dialog) {
                if (authManager.getCurrentUserId() == null) {
                    showNotSignedInPopup()
                } else {
                    showJoinByCodeDialog()
                }
            }
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

        // Add throbbing animation for primary attention-grab options
        if (isPrimary && (title == "New Room" || title == "Sesh Roulette")) {
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

        // Ensure the card has at least minimal elevation for proper background rendering
        if (cardView.cardElevation == 0f) {
            cardView.cardElevation = 1f
        }

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

        // Fade out animation with 0.4 second duration for quick transition
        val fadeOut = ObjectAnimator.ofFloat(mainCard, "alpha", 1f, 0f)
        fadeOut.duration = 400L  // Quick fade-out (0.4 seconds)
        fadeOut.interpolator = android.view.animation.AccelerateInterpolator()

        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                Log.d(TAG, "🏠 Card selection fade-out completed")
                dialog.dismiss()

                // Very small delay before showing the next dialog to ensure smooth transition
                Handler(Looper.getMainLooper()).postDelayed({
                    onComplete()
                }, 100)  // Small gap between fade-out and fade-in (total transition = 500ms)
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
        val socialCheckbox = dialogView.findViewById<CheckBox>(R.id.checkboxSocialRoom)

        // Pre-fill with auto-generated values
        roomNameInput.setText(getRandomRoomName())
        shareCodeInput.setText(generateShareCode())

        // Select all text for easy replacement
        roomNameInput.selectAll()

        // Limit share code length
        shareCodeInput.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(20))

        // Disable/enable password based on social toggle
        socialCheckbox.setOnCheckedChangeListener { _, isChecked ->
            Log.d("Roulette", "CreateRoom: Social checkbox toggled = $isChecked")
            if (isChecked) {
                passwordInput.setText("")
                passwordInput.isEnabled = false
                passwordInput.alpha = 0.5f
                Log.d("Roulette", "CreateRoom: Password cleared and disabled due to social mode")
            } else {
                passwordInput.isEnabled = true
                passwordInput.alpha = 1f
                Log.d("Roulette", "CreateRoom: Password re-enabled")
            }
        }

        // Set up button clicks
        dialogView.findViewById<View>(R.id.btnCreate).setOnClickListener {
            val roomName = roomNameInput.text.toString().trim()
            val shareCode = shareCodeInput.text.toString().trim()
            val isSocial = socialCheckbox.isChecked
            Log.d("Roulette", "CreateRoom: Create tapped name='$roomName' code='$shareCode' isSocial=$isSocial")
            val password = if (isSocial) "" else passwordInput.text.toString().trim()

            if (shareCode.isEmpty()) {
                Toast.makeText(this, "Share code cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            lifecycleScope.launch {
                createRoomWithCustomCode(roomName, shareCode, password, isSocial)
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

    private fun startSeshRoulette() {
        // Simple loading dialog
        val loadingDialog = AlertDialog.Builder(this)
            .setMessage("Loading sesh…")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            try {
                Log.d("Roulette", "Start: Sesh Roulette flow begin")
                val result = withContext(Dispatchers.IO) { sessionSyncService.getActiveRooms() }
                result.fold(
                    onSuccess = { rooms ->
                        lifecycleScope.launch {
                            val now = System.currentTimeMillis()
                            val windowMs = 20 * 60 * 1000L // 20 minutes
                            val me = authManager.getCurrentUserId() ?: getAndroidDeviceId()
                            Log.d("Roulette", "Loaded rooms=${rooms.size} me='$me'")
                            rooms.forEach { r ->
                                Log.d(
                                    "Roulette",
                                    "Room ${r.name} (${r.shareCode}) social=${r.isSocialRoom} acts=${r.activities.size} active=${r.activeParticipants.size} hasPw=${r.passwordHash != null} lastAgeMs=${now - r.lastActivityTime} meIn=${r.participants.contains(me) || r.activeParticipants.contains(me) || r.safeAwayParticipants().contains(me)}"
                                )
                            }

                            // Base filters
                            val candidates = rooms.filter { room ->
                                val notMe =
                                    !room.participants.contains(me) &&
                                    !room.activeParticipants.contains(me) &&
                                    !room.safeAwayParticipants().contains(me)

                                room.isSocialRoom &&
                                    room.passwordHash == null &&
                                    room.activeParticipants.isNotEmpty() &&
                                    room.activeParticipants.size <= 4 &&
                                    (now - room.lastActivityTime) <= windowMs &&
                                    notMe
                            }
                            Log.d("Roulette", "Candidates after filter count=${candidates.size}")

                            // Prioritize rooms with active video participants
                            val videoActive = mutableListOf<RoomData>()
                            for (room in candidates) {
                                Log.d("Roulette", "Checking video activity for ${room.shareCode}")
                                if (hasActiveVideoParticipants(room.shareCode)) {
                                    videoActive.add(room)
                                    Log.d("Roulette", "Video-active: ${room.shareCode}")
                                }
                            }

                            val pickFrom = if (videoActive.isNotEmpty()) videoActive else candidates
                            Log.d("Roulette", "pickFrom size=${pickFrom.size} (videoPreferred=${videoActive.isNotEmpty()})")

                            if (pickFrom.isEmpty()) {
                                Log.d("Roulette", "No social candidates - trying fallback (non-social rooms)")

                                // FALLBACK: relax social filter to include non-social rooms
                                val fallbackCandidates = rooms.filter { room ->
                                    val notMe =
                                        !room.participants.contains(me) &&
                                        !room.activeParticipants.contains(me) &&
                                        !room.safeAwayParticipants().contains(me)

                                    // Same guardrails except social flag
                                    room.passwordHash == null &&
                                        room.activeParticipants.isNotEmpty() &&
                                        room.activeParticipants.size <= 4 &&
                                        (now - room.lastActivityTime) <= windowMs &&
                                        notMe
                                }
                                Log.d("Roulette", "Fallback candidates count=${fallbackCandidates.size}")

                                val videoActiveFallback = mutableListOf<RoomData>()
                                for (room in fallbackCandidates) {
                                    if (hasActiveVideoParticipants(room.shareCode)) {
                                        videoActiveFallback.add(room)
                                    }
                                }
                                val pickFromFallback = if (videoActiveFallback.isNotEmpty()) videoActiveFallback else fallbackCandidates
                                Log.d("Roulette", "pickFromFallback size=${pickFromFallback.size} (videoPreferred=${videoActiveFallback.isNotEmpty()})")

                                if (pickFromFallback.isEmpty()) {
                                    loadingDialog.dismiss()
                                    val noneDialog = AlertDialog.Builder(this@MainActivity)
                                        .setMessage("Sorry, it seems like everyone is sleeping at the moment. Try again in a bit.")
                                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                                        .create()
                                    noneDialog.setOnDismissListener {
                                        // Return to Start Cloud Session popup after message closes
                                        showCloudSessionOptions()
                                    }
                                    noneDialog.show()

                                    // Auto-dismiss after 3 seconds
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        if (noneDialog.isShowing) noneDialog.dismiss()
                                    }, 3000)
                                    Log.d("Roulette", "No candidates found after fallback - sleeping dialog shown and returning to options")
                                } else {
                                    val chosen = pickFromFallback.random()
                                    Log.d("Roulette", "Chosen (fallback) ${chosen.name} (${chosen.shareCode})")
                                    loadingDialog.dismiss()
                                    joinRoomSafely(chosen.shareCode, null)
                                }
                            } else {
                                val chosen = pickFrom.random()
                                Log.d("Roulette", "Chosen room ${chosen.name} (${chosen.shareCode}) active=${chosen.activeParticipants.size} acts=${chosen.activities.size}")
                                loadingDialog.dismiss()
                                joinRoomSafely(chosen.shareCode, null)
                                Log.d("Roulette", "joinRoomSafely invoked for ${chosen.shareCode}")
                            }
                        }
                    },
                    onFailure = { err ->
                        loadingDialog.dismiss()
                        Toast.makeText(
                            this@MainActivity,
                            "Error loading rooms: ${err.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e("Roulette", "getActiveRooms failed: ${err.message}")
                    }
                )
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("Roulette", "Exception in Sesh Roulette: ${e.message}", e)
            }
        }
    }

    private suspend fun hasActiveVideoParticipants(shareCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val roomId = "sesh_${shareCode}"
            val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("video_rooms")
                .document(roomId)
                .collection("participants")
                .whereEqualTo("isActive", true)
                .get()
                .await()

            if (snap.isEmpty) {
                Log.d("Roulette", "Video: no active participants for room=$shareCode")
                return@withContext false
            }

            val now = System.currentTimeMillis()
            val freshWindow = 5 * 60 * 1000L // 5 minutes
            val anyFresh = snap.documents.any { doc ->
                val lastHeartbeat = doc.getLong("lastHeartbeat") ?: 0L
                lastHeartbeat > 0L && (now - lastHeartbeat) <= freshWindow
            }
            Log.d("Roulette", "Video: room=$shareCode hasFresh=$anyFresh participants=${snap.size()} (freshWindow=${freshWindow}ms)")
            return@withContext anyFresh
        } catch (e: Exception) {
            Log.e(TAG, "Error checking video participants for $shareCode: ${e.message}")
            return@withContext false
        }
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

    // Preview a past session without activating it; shows stats + Resume button
    private fun previewSession(summary: SessionSummary) {
        Log.d("SeshFlow", "Previewing session id=${summary.id}, room=${summary.roomName}, code=${summary.shareCode}")
        lastLoadedSummary = summary

        // Load stats into the Sesh tab without activating the session
        sessionStatsVM.loadSummary(summary)

        // Show room info if available (use OFFLINE code for historical cloud sessions)
        if (!summary.roomName.isNullOrEmpty()) {
            val code = summary.shareCode ?: "OFFLINE"
            sessionStatsVM.setRoomInfo(summary.roomName!!, code)
        } else {
            sessionStatsVM.clearRoomInfo()
        }

        // Notify SeshFragment to show Resume button
        val seshFragments = supportFragmentManager.fragments.filterIsInstance<SeshFragment>()
        Log.d("SeshFlow", "previewSession: found ${seshFragments.size} SeshFragment instance(s)")
        val sesh = seshFragments.firstOrNull()
        if (sesh == null) {
            Log.d("SeshFlow", "previewSession: SeshFragment not found; will rely on tab switch to create it")
        } else {
            Log.d("SeshFlow", "previewSession: calling onSummaryLoaded() on existing SeshFragment")
            sesh.onSummaryLoaded()
            Log.d("SeshFlow", "previewSession: onSummaryLoaded() call returned")
        }

        // Navigate to Sesh tab
        binding.viewPager.currentItem = 1
        Log.d("SeshFlow", "previewSession: switched to Sesh tab (index 1)")
    }

    private fun createRoomWithCustomCode(roomName: String, customShareCode: String, password: String? = null, isSocialRoom: Boolean = false) {
        Log.d(TAG, "🏠 createRoomWithCustomCode called: name=$roomName, code=$customShareCode, hasPassword=${password != null}, isSocial=$isSocialRoom")
        Log.d("Roulette", "CreateRoom: Creating room code=$customShareCode social=$isSocialRoom passwordProvided=${!password.isNullOrEmpty()}")

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
            val passwordHash = if (!password.isNullOrEmpty() && !isSocialRoom) {
                PasswordUtils.hashPassword(password)
            } else null
            Log.d("Roulette", "CreateRoom: passwordHashSet=${passwordHash != null}")

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
                joinedUsers = listOf(creatorId),
                isSocialRoom = isSocialRoom
            )

            try {
                Log.d(TAG, "🏠 About to create room document in Firestore")
                Log.d("Roulette", "CreateRoom: Writing room doc social=${room.isSocialRoom} participants=${room.activeParticipants.size}")

                // Directly create the room document with the custom share code as ID
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("rooms")
                    .document(customShareCode)
                    .set(room)
                    .await()

                Log.d(TAG, "🏠 Room document created successfully in Firestore")

                // Set room info BEFORE starting session so the initial summary has correct room name
                currentShareCode = room.shareCode
                currentRoomName = room.name
                currentRoom = room
                refreshQueueIndicators()
                Log.d("SeshFlow", "createRoom: setting room info BEFORE startSession (name=${room.name}, code=${room.shareCode})")
                startSession(room.startTime)

                sessionStatsVM.setRoomInfo(room.name, room.shareCode)
                Log.d("SeshFlow", "createRoom: room info applied to VM (name=${room.name}, code=${room.shareCode})")
                Log.d(TAG, "🏠 Room created with custom code: ${room.name} (${room.shareCode})")

                // Sync local smokers to the new room
                val localSmokers = withContext(Dispatchers.IO) {
                    repo.allSmokers.value?.filter { !it.isCloudSmoker } ?: emptyList()
                }

                if (localSmokers.isNotEmpty()) {
                    sessionSyncService.syncLocalSmokersToRoom(creatorId, customShareCode, localSmokers)
                }

                startRoomListener(room.shareCode)
                Log.d("SeshFlow", "createRoom: started room listener for ${room.shareCode}")

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
                        if (type == ActivityType.CUSTOM && pendingCustomActivity != null) {
                            // Handle custom activity
                            Log.d(TAG, "🏠 Logging pending custom activity: ${pendingCustomActivity?.name}")
                            pendingCustomActivity?.let { customActivity ->
                                logCustomActivitySafe(customActivity)
                            }
                            pendingCustomActivity = null
                        } else {
                            // Handle regular activity
                            logHitSafe(type)
                        }
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
        Log.d("Roulette", "joinRoomSafely called for code=$shareCode (dialogClose=${dialogToClose != null})")
        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId() ?: getAndroidDeviceId()
            Log.d("Roulette", "joinRoomSafely: resolved userId='$userId'")

            // First get the room to check if it has a password
            val roomData = sessionSyncService.getRoomData(shareCode)
            if (roomData == null) {
                Toast.makeText(this@MainActivity, "Room not found", Toast.LENGTH_SHORT).show()
                Log.e("Roulette", "joinRoomSafely: Room not found for code=$shareCode")
                return@launch
            }

            // Check if room has password and user hasn't joined yet
            if (roomData.passwordHash != null && !roomData.hasUserJoined(userId)) {
                dialogToClose?.dismiss()
                
                // Show password dialog
                showRoomPasswordDialog(roomData, userId)
                Log.d("Roulette", "joinRoomSafely: Password required for code=$shareCode; prompting")
            } else {
                // No password or already joined, proceed normally
                proceedWithJoinRoom(shareCode, userId, dialogToClose)
                Log.d("Roulette", "joinRoomSafely: Proceeding without password for code=$shareCode")
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
        Log.d("Roulette", "proceedWithJoinRoom: shareCode=$shareCode userId=$userId")
        lifecycleScope.launch {
            // Get local smokers BEFORE joining the room
            val localSmokers = withContext(Dispatchers.IO) {
                repo.getAllSmokersList().filter { !it.isCloudSmoker }
            }

            Log.d(TAG, "🏠 Joining room with ${localSmokers.size} local smokers")
            Log.d("Roulette", "proceedWithJoinRoom: localSmokers=${localSmokers.size}")

            // Use the enhanced join method that syncs smokers
            sessionSyncService.joinRoomWithSmokerSync(userId, shareCode, localSmokers).fold(
                onSuccess = { room: RoomData ->  // Explicitly specify the type
                    dialogToClose?.dismiss()
                    // Set room info BEFORE starting session so the initial summary has correct room name
                    currentShareCode = shareCode
                    currentRoomName = room.name
                    currentRoom = room
                    refreshQueueIndicators()
                    Log.d("SeshFlow", "joinRoom: setting room info BEFORE startSession (name=${room.name}, code=$shareCode)")
                    startSession(room.startTime)

                    sessionStatsVM.setRoomInfo(room.name, shareCode)
                    Log.d(TAG, "🏠 Room info set for joined room: ${room.name} ($shareCode)")
                    Log.d("Roulette", "proceedWithJoinRoom: Joined room ${room.name} ($shareCode); starting session")
                    
                    // Update active session summary with room info (redundant with reorder but harmless)
                    if (editingSummaryId != null) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val currentSummary = repo.getSummaryById(editingSummaryId!!)
                            if (currentSummary != null) {
                                val updatedSummary = currentSummary.copy(
                                    shareCode = shareCode,
                                    roomName = room.name
                                )
                                repo.updateSummary(updatedSummary)
                                Log.d("SessionDebug", "Updated active session with room info: ${room.name}")
                                Log.d("SeshFlow", "joinRoom: updated summaryId=${editingSummaryId} with room info (${room.name}, $shareCode)")
                            }
                        }
                    }

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
                                if (type == ActivityType.CUSTOM && pendingCustomActivity != null) {
                                    // Handle custom activity
                                    Log.d(TAG, "🏠 Logging pending custom activity: ${pendingCustomActivity?.name}")
                                    pendingCustomActivity?.let { customActivity ->
                                        logCustomActivitySafe(customActivity)
                                    }
                                    pendingCustomActivity = null
                                } else {
                                    // Handle regular activity
                                    logHitSafe(type)
                                }
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
            binding.btnSkip.visibility = View.VISIBLE
            binding.btnToggleTimers.visibility = View.VISIBLE

            // Show auto-add controls during session (if timers are visible)
            binding.layoutConeAutoControls.visibility = if (timersVisible) View.VISIBLE else View.GONE
            binding.layoutJointAutoControls.visibility = if (timersVisible) View.VISIBLE else View.GONE
            binding.layoutBowlAutoControls.visibility = if (timersVisible) View.VISIBLE else View.GONE

            // Adjust button heights based on timer visibility
            if (timersVisible) {
                // Double the height when "See Less" is shown (timers visible)
                setActivityButtonHeights(jointButton, coneButton, bowlButton, 96.dpToPx(this))
                // Apply margin for expanded state - same spacing as collapsed
                params.topMargin = -5.dpToPx(this)
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
            binding.btnSkip.visibility = View.GONE

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

    private fun updateActiveSessionSummary() {
        if (!sessionActive || editingSummaryId == null) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sessionEnd = System.currentTimeMillis()
                val length = sessionEnd - sessionStart
                
                // Get all activities for the current session
                val allActivities = repo.getLogsInTimeRange(sessionStart, sessionEnd)
                val activityMap = mutableMapOf<String, Int>()
                
                allActivities.forEach { log: ActivityLog ->
                    val activityName = when {
                        !log.customActivityName.isNullOrEmpty() -> log.customActivityName
                        log.type == ActivityType.JOINT -> "Joint"
                        log.type == ActivityType.CONE -> "Cone"
                        log.type == ActivityType.BOWL -> "Bowl"
                        else -> log.type.name
                    }
                    
                    val countToAdd = if (log.type == ActivityType.BOWL) log.bowlQuantity else 1
                    activityMap[activityName] = (activityMap[activityName] ?: 0) + countToAdd
                }
                
                val activityBreakdown = if (activityMap.isNotEmpty()) {
                    org.json.JSONObject(activityMap as Map<*, *>).toString()
                } else null
                
                // Get cone counts for each smoker
                val names = smokers.map { it.name }
                val conesList = smokers.map { s ->
                    repo.countConesForSmokerBetween(s.smokerId, sessionStart, sessionEnd)
                }
                val total = conesList.sum()
                
                // Update the active session summary
                val summary = SessionSummary(
                    id = editingSummaryId!!,
                    smokerNames = names,
                    conesPerSmoker = conesList,
                    totalCones = total,
                    rounds = actualRounds,
                    sessionLength = length,
                    longestInterval = intervalsList.maxOrNull() ?: 0L,
                    shortestInterval = intervalsList.minOrNull() ?: 0L,
                    timestamp = sessionStart,  // Keep original start time
                    liveSyncEnabled = currentShareCode != null,
                    shareCode = currentShareCode,
                    roomName = currentRoomName,
                    activityBreakdown = activityBreakdown,
                    isActive = true  // Keep as active
                )
                
                repo.updateSummary(summary)
                Log.d("SessionDebug", "Updated active session summary: ${activityMap.size} activity types")
            } catch (e: Exception) {
                Log.e("SessionDebug", "Error updating active session summary", e)
            }
        }
    }
    
    private fun startSession(startTime: Long) {
        // Clear any editing state - we're starting a NEW session
        editingSummaryId = null
        lastLoadedSummary = null
        
        // UNDO FIX: Clear recently undone activities when starting new session
        recentlyUndoneActivities.clear()
        Log.d(TAG, "🔙 UNDO FIX: Cleared recently undone activities for new session")
        
        // Clear carried-over stats when starting a fresh session
        sessionStatsVM.clearCarriedOverStats()

        // Set the session start time which will be used as the session ID
        sessionStart = startTime
        sessionActive = true
        firstConePromptShown = false
        isInFirstConeDialog = false

        // Initialize session tracking variables
        lastLogTime = startTime
        actualLastLogTime = 0L
        lastLogTimeBeforeRewind = 0L
        lastConeTimestamp = 0L
        lastJointTimestamp = 0L
        lastBowlTimestamp = 0L
        lastCustomActivityTimestamps.clear()
        lastIntervalMillis = 0L
        intervalsList.clear()
        
        // Create initial SessionSummary for live tracking
        // Use runBlocking to ensure editingSummaryId is set before continuing
        Log.d("SessionDebug", "Creating initial session summary...")
        Log.d("SeshFlow", "startSession: creating initial summary at $startTime with shareCode=$currentShareCode, roomName=$currentRoomName")
        runBlocking {
            withContext(Dispatchers.IO) {
                val names = smokers.map { it.name }
                val initialSummary = SessionSummary(
                    smokerNames = names,
                    conesPerSmoker = List(names.size) { 0 },
                    totalCones = 0,
                    rounds = 0,
                    sessionLength = 0L,
                    longestInterval = 0L,
                    shortestInterval = 0L,
                    timestamp = startTime,
                    liveSyncEnabled = currentShareCode != null,
                    shareCode = currentShareCode,
                    roomName = currentRoomName,
                    activityBreakdown = null,
                    isActive = true  // Mark as active
                )
                
                val summaryId = repo.insertSummary(initialSummary)
                editingSummaryId = summaryId
                Log.d("SessionDebug", "Created active session with ID: $summaryId, editingSummaryId is now set")
                Log.d("SeshFlow", "startSession: inserted initial summaryId=$summaryId (liveSync=${currentShareCode != null}, roomName=${currentRoomName})")
            }
        }
        Log.d("SessionDebug", "Session creation complete, editingSummaryId = $editingSummaryId")
        Log.d("SeshFlow", "startSession: complete; editingSummaryId=$editingSummaryId")
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
        // UNDO FIX: Clear recently undone activities when ending session
        recentlyUndoneActivities.clear()
        Log.d(TAG, "🔙 UNDO FIX: Cleared recently undone activities for session end")
        
        // CRITICAL: Store the session ID and times before clearing anything
        val completedSessionId = if (sessionActive && sessionStart > 0) {
            sessionStart
        } else {
            null
        }
        val sessionEndTime = System.currentTimeMillis()

        Log.d(TAG, "📊 Ending session with ID: $completedSessionId at time: $sessionEndTime")

        // Store in preferences immediately if we have a valid session ID
        if (completedSessionId != null && completedSessionId > 0) {
            prefs.edit().putLong("last_completed_session_id", completedSessionId).apply()
            Log.d(TAG, "📊 Saved last completed session ID to prefs: $completedSessionId")

            // Update both ViewModels immediately with session ID and times
            sessionStatsVM.lastCompletedSessionId = completedSessionId
            sessionStatsVM.lastCompletedSessionStart = completedSessionId // Session ID is the start time
            sessionStatsVM.lastCompletedSessionEnd = sessionEndTime
            stashViewModel.setLastCompletedSessionId(completedSessionId)

            // Also ensure all activities in this session have the session ID
            lifecycleScope.launch(Dispatchers.IO) {
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
        lastCustomActivityTimestamps.clear()
        activitiesTimestamps.clear()
        lastLogTimeBeforeRewind = 0L
        handler.removeCallbacks(timerRunnable)

        sessionStatsVM.stopSession()
        activityHistory.clear()
        clearActiveSessionState()
        firstConePromptShown = false
        isInFirstConeDialog = false

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
        
        // Capture these values before clearing them
        val capturedSessionStart = sessionStart
        val capturedEditingSummaryId = editingSummaryId
        val capturedLastLoadedSummary = lastLoadedSummary
        val capturedCurrentShareCode = currentShareCode
        val capturedCurrentRoomName = currentRoomName
        
        Log.d("SessionDebug", "=== SESSION END DEBUG ===")
        Log.d("SessionDebug", "sessionEnd timestamp: $sessionEnd")
        Log.d("SessionDebug", "sessionStart value: $capturedSessionStart")
        Log.d("SessionDebug", "sessionActive: $sessionActive")
        Log.d("SessionDebug", "editingSummaryId: $capturedEditingSummaryId")
        
        lifecycleScope.launch(Dispatchers.IO) {
            val names = smokers.map { it.name }

            Log.d("SessionDebug", "Determining originalSessionStart:")
            Log.d("SessionDebug", "  - editingSummaryId: $capturedEditingSummaryId")
            Log.d("SessionDebug", "  - lastLoadedSummary: ${capturedLastLoadedSummary?.id}")
            Log.d("SessionDebug", "  - current sessionStart: $capturedSessionStart")
            
            val originalSessionStart = if (capturedEditingSummaryId != null && capturedLastLoadedSummary != null) {
                // When editing a session, sessionStart should already be set to the correct value
                // from when we resumed the session. Just use it directly.
                Log.d("SessionDebug", "EDITING PATH - session was resumed or continued")
                Log.d("SessionDebug", "  - Using sessionStart: $capturedSessionStart")
                if (capturedSessionStart > 0) {
                    capturedSessionStart
                } else {
                    // Fallback: if sessionStart is invalid, calculate from the loaded summary
                    val calculated = capturedLastLoadedSummary.timestamp - capturedLastLoadedSummary.sessionLength
                    Log.d("SessionDebug", "  - sessionStart was invalid (<=0), calculated from summary: $calculated")
                    calculated
                }
            } else if (capturedEditingSummaryId != null && capturedLastLoadedSummary == null) {
                // This is a newly created session that hasn't been loaded
                Log.d("SessionDebug", "NEW SESSION PATH - editingSummaryId exists but no loaded summary")
                Log.d("SessionDebug", "  - This is a newly created session")
                Log.d("SessionDebug", "  - Using sessionStart: $capturedSessionStart")
                capturedSessionStart
            } else {
                // If sessionStart is 0 or invalid, use the timestamp of the first activity
                // or default to a reasonable session start time (e.g., 1 hour ago)
                if (capturedSessionStart <= 0) {
                    Log.d("SessionDebug", "Invalid sessionStart ($capturedSessionStart), finding first activity...")
                    // Try to get the first activity timestamp in a reasonable time range
                    val recentActivities = repo.getLogsInTimeRange(sessionEnd - 86400000, sessionEnd) // Last 24 hours
                    if (recentActivities.isNotEmpty()) {
                        val firstActivity = recentActivities.minOf { it.timestamp }
                        Log.d("SessionDebug", "Using first activity timestamp: $firstActivity")
                        firstActivity
                    } else {
                        Log.d("SessionDebug", "No activities found, defaulting to 1 hour ago")
                        sessionEnd - 3600000 // Default to 1 hour session if no activities
                    }
                } else {
                    Log.d("SessionDebug", "Using existing sessionStart: $capturedSessionStart")
                    capturedSessionStart
                }
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
            var length = sessionEnd - originalSessionStart
            Log.d("SessionDebug", "originalSessionStart: $originalSessionStart")
            Log.d("SessionDebug", "sessionEnd: $sessionEnd")
            Log.d("SessionDebug", "Calculated length in ms: $length")
            Log.d("SessionDebug", "Calculated length in seconds: ${length / 1000}")
            Log.d("SessionDebug", "Calculated length in minutes: ${length / 60000}")
            Log.d("SessionDebug", "Calculated length in hours: ${length / 3600000}")
            
            // Validate session length - maximum 7 days
            val maxSessionLength = 7L * 24 * 60 * 60 * 1000 // 7 days in ms
            if (length < 0) {
                Log.d("SessionDebug", "ERROR: Negative session length detected!")
                Log.d("SessionDebug", "  - originalSessionStart: $originalSessionStart")
                Log.d("SessionDebug", "  - sessionEnd: $sessionEnd")
                Log.d("SessionDebug", "  - calculated length: $length")
                Log.d("SessionDebug", "  - editingSummaryId: $editingSummaryId")
                Log.d("SessionDebug", "  - sessionStart: $sessionStart")
                Log.d("SessionDebug", "  - Using 1 hour default")
                length = 3600000L // 1 hour default
            } else if (length > maxSessionLength) {
                Log.d("SessionDebug", "ERROR: Session length too long!")
                Log.d("SessionDebug", "  - length: ${length}ms (${length/3600000} hours)")
                Log.d("SessionDebug", "  - max allowed: ${maxSessionLength}ms (168 hours)")
                Log.d("SessionDebug", "  - originalSessionStart: $originalSessionStart")
                Log.d("SessionDebug", "  - sessionEnd: $sessionEnd")
                Log.d("SessionDebug", "  - Using 1 hour default")
                length = 3600000L // 1 hour default
            } else {
                Log.d("SessionDebug", "Session length validated OK: ${length}ms (${length/1000/60} minutes)")
            }
            
            val longest = intervalsList.maxOrNull() ?: 0L
            val shortest = intervalsList.minOrNull() ?: 0L
            
            // Get all activities for the session to create breakdown
            val allActivities = repo.getLogsInTimeRange(originalSessionStart, sessionEnd)
            Log.d("SessionDebug", "Activities found for session: ${allActivities.size}")
            val activityMap = mutableMapOf<String, Int>()
            
            allActivities.forEach { log: ActivityLog ->
                val activityName = when {
                    !log.customActivityName.isNullOrEmpty() -> log.customActivityName
                    log.type == ActivityType.JOINT -> "Joint"
                    log.type == ActivityType.CONE -> "Cone"
                    log.type == ActivityType.BOWL -> "Bowl"
                    else -> log.type.name
                }
                
                // For bowls, add the quantity; for others add 1
                val countToAdd = if (log.type == ActivityType.BOWL) log.bowlQuantity else 1
                activityMap[activityName] = (activityMap[activityName] ?: 0) + countToAdd
            }
            
            // Convert to JSON string
            val activityBreakdown = if (activityMap.isNotEmpty()) {
                org.json.JSONObject(activityMap as Map<*, *>).toString()
            } else null

            Log.d("SessionDebug", "=== UPDATING SESSION SUMMARY ===")
            Log.d("SessionDebug", "sessionLength to store: $length ms (${length/1000} seconds)")
            Log.d("SessionDebug", "Activity breakdown: $activityBreakdown")
            
            val summary = SessionSummary(
                id = capturedEditingSummaryId ?: 0L,
                smokerNames = names,
                conesPerSmoker = conesList,
                totalCones = total,
                rounds = actualRounds,
                sessionLength = length,
                longestInterval = longest,
                shortestInterval = shortest,
                timestamp = capturedSessionStart,  // FIX: Use sessionStart as sessionId, not sessionEnd
                liveSyncEnabled = true,
                shareCode = capturedCurrentShareCode,
                roomName = capturedCurrentRoomName,
                activityBreakdown = activityBreakdown,
                isActive = false  // Mark as completed when ending
            )

            // Always update since we create the summary when starting
            if (capturedEditingSummaryId != null) {
                Log.d("SessionDebug", "Updating existing session summary:")
                Log.d("SessionDebug", "  - ID: $capturedEditingSummaryId")
                Log.d("SessionDebug", "  - Final length: ${summary.sessionLength}ms (${summary.sessionLength/1000/60} minutes)")
                Log.d("SessionDebug", "  - Activities: ${summary.activityBreakdown}")
                repo.updateSummary(summary)
                Log.d("SessionDebug", "Updated session $capturedEditingSummaryId to inactive")
            } else {
                // Fallback - shouldn't happen but handle it
                Log.d("SessionDebug", "WARNING: editingSummaryId is null at session end!")
                Log.d("SessionDebug", "  - This shouldn't happen with synchronous creation")
                Log.d("SessionDebug", "  - Creating new summary as fallback")
                val newId = repo.insertSummary(summary)
                Log.d("SessionDebug", "Created fallback summary with ID: $newId")
            }

            withContext(Dispatchers.Main) {
                // Trigger stats refresh with the completed session ID
                stashViewModel.refreshStatsAfterSessionChange()

                sessionStatsVM.loadSummary(summary)
                sessionStatsVM.clearRoomInfo()

                currentShareCode = null
                sharedActiveSmokerId = null
                currentRoomName = null
                currentRoom = null
                refreshQueueIndicators()

                val seshFragment = supportFragmentManager.fragments
                    .filterIsInstance<SeshFragment>()
                    .firstOrNull()
                seshFragment?.onSessionEnded()
                
                // Clear session variables INSIDE the coroutine after all work is done
                sessionStart = 0L
                editingSummaryId = null
                lastLoadedSummary = null

                // If a resume was requested during endSession, perform it now
                val pending = pendingResumeSummary
                if (pending != null) {
                    Log.d("SeshFlow", "End complete; proceeding to resume pending id=${pending.id}")
                    pendingResumeSummary = null
                    resumeSession(pending)
                } else {
                    Log.d("SeshFlow", "End complete; no pending resume")
                }
            }
        }

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
        Log.d("SeshFlow", "resumeSession requested: id=${summary.id}, active=$sessionActive")
        if (sessionActive) {
            Log.d("SeshFlow", "Active session present; deferring resume of id=${summary.id} until endSession completes")
            pendingResumeSummary = summary
            endSession()
            return
        }

        val resumeTime = System.currentTimeMillis()
        editingSummaryId = summary.id
        // Protect against invalid session lengths (e.g., 55 years)
        // Maximum reasonable session length is 7 days
        val maxSessionLength = 7L * 24 * 60 * 60 * 1000 // 7 days in ms
        val sessionLength = if (summary.sessionLength > maxSessionLength) {
            Log.d("SessionDebug", "Invalid session length detected: ${summary.sessionLength} ms, using 1 hour default")
            3600000L // Default to 1 hour
        } else {
            summary.sessionLength
        }
        sessionStart = summary.timestamp - sessionLength
        Log.d("SessionDebug", "Resuming session: timestamp=${summary.timestamp}, length=$sessionLength, calculated start=$sessionStart")
        Log.d("SeshFlow", "Resuming session now: id=${summary.id}, start=$sessionStart")
        
        // Mark the session as active again in the database
        lifecycleScope.launch(Dispatchers.IO) {
            val updatedSummary = summary.copy(isActive = true)
            repo.updateSummary(updatedSummary)
            Log.d("SessionDebug", "Marked session ${summary.id} as active")
        }
        
        lastLogTime = resumeTime
        actualLastLogTime = 0L
        lastLogTimeBeforeRewind = 0L
        lastConeTimestamp = 0L
        lastJointTimestamp = 0L
        lastBowlTimestamp = 0L
        lastCustomActivityTimestamps.clear()
        lastIntervalMillis = 0L
        intervalsList.clear()
        activitiesTimestamps.clear()
        hitsThisRound = 0
        actualRounds = summary.rounds
        sessionActive = true
        rewindOffset = 0L

        lastLoadedSummary = summary

        // Load the summary into the stats view model so Stats tab shows correct data
        sessionStatsVM.loadSummary(summary)
        Log.d("SessionDebug", "Loaded summary into sessionStatsVM for session ${summary.id}")
        
        sessionStatsVM.startSession(sessionStart)
        stashViewModel.setSessionStartTime(sessionStart)
        
        // Notify SeshFragment that session has been resumed/started
        val seshFragment = supportFragmentManager.fragments
            .filterIsInstance<SeshFragment>()
            .firstOrNull()
        seshFragment?.onSessionStarted()
        Log.d("SessionDebug", "Notified SeshFragment of resumed session")

        prefs.edit()
            .putLong("current_session_id", sessionStart)
            .putBoolean("session_active", true)
            .apply()

        updateUIForSessionState()
        handler.post(timerRunnable)

        binding.tabLayout.getTabAt(1)?.select()

        // We just resumed; ensure preview mode is cleared and FAB is hidden
        Log.d("SeshFlow", "Resumed session; clearing preview mode")

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
                                // Use applyRoomStatsWithCustom to filter old session activities
                                applyRoomStatsWithCustom(room)
                                Log.d(TAG, "🔄 Applied initial room stats on resume (with session filtering)")
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
                            Log.d("SeshFlow", "Resume: join failed; keeping room name and showing OFFLINE")
                            // Keep room name for display; mark as offline in VM
                            currentRoom = null

                            withContext(Dispatchers.Main) {
                                val roomName = summary.roomName ?: currentRoomName ?: "Session"
                                sessionStatsVM.setRoomInfo(roomName, "OFFLINE")
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
                                        // Use applyRoomStatsWithCustom to filter old session activities
                                        applyRoomStatsWithCustom(room)
                                        Log.d(TAG, "🔄 Applied room stats after app restart (with session filtering)")
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
                                    sharedActiveSmokerId = null
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
                            sharedActiveSmokerId = null

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
        
        // IMPORTANT: Dismiss add smoker dialog first
        Log.d("FIRST_LAUNCH_FLOW", "🚪 Dismissing AddSmokerDialog before showing welcome")
        addSmokerDialog.dismiss()
        
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
                Log.d("WELCOME_DEBUG", "🎉 This is the first cloud smoker!")
                withContext(Dispatchers.Main) {
                    // The onboarding controller will handle showing the welcome screen
                    onboardingController.onAddSmokerStepCompleted(isFirstCloudSmoker = true)
                }
            } else {
                Log.d("WELCOME_DEBUG", "⏭️ Not the first cloud smoker (found ${existingCloudSmokers.size}), skipping welcome")
            }
        }
    }
    
    // Public methods for showing dialogs from WelcomeScreenDialog
    // Onboarding Flow Methods
    fun handlePostOnboardingLaunch() {
        Log.d("FIRST_LAUNCH_FLOW", "✅ Post-onboarding launch - resuming normal app flow")
        // Resume normal app functionality
        // All onboarding is complete
    }
    
    fun markLegacyFirstLaunchHandled() {
        prefs.edit().putBoolean("is_first_launch", false).apply()
    }
    
    fun shouldRequestNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }
    
    fun shouldRequestLocationPermission(): Boolean {
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
    }
    
    fun launchNotificationPermissionRequest() {
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    
    fun launchLocationPermissionRequest() {
        locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
    
    fun shouldRequestCameraPermission(): Boolean {
        return checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
    }
    
    fun shouldRequestAudioPermission(): Boolean {
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
    }
    
    fun launchCameraPermissionRequest() {
        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }
    
    fun launchAudioPermissionRequest() {
        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    
    fun hasAnySmokers(): Boolean {
        return smokers.isNotEmpty()
    }
    
    fun showAddSmokerDialogForOnboarding() {
        Log.d("FIRST_LAUNCH_FLOW", "📝 Showing add smoker dialog for onboarding")
        
        addSmokerDialog.setOnSuccessCallback { smoker ->
            Log.d("FIRST_LAUNCH_FLOW", "✅ Smoker added: ${smoker.name}")
            val isFirstCloud = smokers.count { it.isCloudSmoker } == 1
            onboardingController.onAddSmokerStepCompleted(isFirstCloud)
        }
        addSmokerDialog.setOnCancelCallback {
            Log.d("FIRST_LAUNCH_FLOW", "❌ Add smoker dialog cancelled")
            onboardingController.onAddSmokerCancelledOrSkipped()
        }
        addSmokerDialog.show()
    }
    
    fun showAddStashDialog(onDismiss: () -> Unit = {}) {
        Log.d("FIRST_LAUNCH_FLOW", "🏦 MainActivity.showAddStashDialog() called with callback")
        // DON'T navigate to tabs during welcome flow - just show dialog
        Log.d("FIRST_LAUNCH_FLOW", "🔍 Looking for StashFragment...")
        
        // Get all fragments and find StashFragment
        val fragments = supportFragmentManager.fragments
        Log.d("FIRST_LAUNCH_FLOW", "📋 Found ${fragments.size} fragments total")
        val stashFragment = fragments.filterIsInstance<StashFragment>().firstOrNull()
        
        if (stashFragment != null) {
            Log.d("FIRST_LAUNCH_FLOW", "✅ Found StashFragment, calling showAddStashDialogPublic()")
            stashFragment.showAddStashDialogPublic()
            onDismiss()
        } else {
            Log.d("FIRST_LAUNCH_FLOW", "⚠️ StashFragment not found, trying with navigation...")
            // Navigate to stash tab and retry
            binding.viewPager.currentItem = 4
            handler.postDelayed({
                val retryFragments = supportFragmentManager.fragments
                val retryStashFragment = retryFragments.filterIsInstance<StashFragment>().firstOrNull()
                if (retryStashFragment != null) {
                    Log.d("FIRST_LAUNCH_FLOW", "✅ Found StashFragment on retry")
                    retryStashFragment.showAddStashDialogPublic()
                    onDismiss()
                } else {
                    Log.d("FIRST_LAUNCH_FLOW", "❌ StashFragment still not found after retry")
                }
            }, 500)
        }
    }
    
    fun showSetRatioDialog(onDismiss: () -> Unit = {}) {
        Log.d("FIRST_LAUNCH_FLOW", "⚖️ MainActivity.showSetRatioDialog() called with callback")
        // DON'T navigate during welcome flow
        Log.d("FIRST_LAUNCH_FLOW", "🔍 Looking for StashFragment for ratio dialog...")
        
        // Get all fragments and find StashFragment
        val fragments = supportFragmentManager.fragments
        Log.d("FIRST_LAUNCH_FLOW", "📋 Found ${fragments.size} fragments total")
        val stashFragment = fragments.filterIsInstance<StashFragment>().firstOrNull()
        
        if (stashFragment != null) {
            Log.d("FIRST_LAUNCH_FLOW", "✅ Found StashFragment, calling showSetRatioDialogPublic()")
            stashFragment.showSetRatioDialogPublic()
            onDismiss()
        } else {
            Log.d("FIRST_LAUNCH_FLOW", "⚠️ StashFragment not found, trying with navigation...")
            // Navigate to stash tab and retry
            binding.viewPager.currentItem = 4
            handler.postDelayed({
                val retryFragments = supportFragmentManager.fragments
                val retryStashFragment = retryFragments.filterIsInstance<StashFragment>().firstOrNull()
                if (retryStashFragment != null) {
                    Log.d("FIRST_LAUNCH_FLOW", "✅ Found StashFragment on retry")
                    retryStashFragment.showSetRatioDialogPublic()
                    onDismiss()
                } else {
                    Log.d("FIRST_LAUNCH_FLOW", "❌ StashFragment still not found!")
                }
            }, 500)
        }
    }
    
    fun showAddGoalDialog(onDismiss: () -> Unit = {}) {
        Log.d("FIRST_LAUNCH_FLOW", "🎯 MainActivity.showAddGoalDialog() called with callback")
        
        // No delay needed - fragments are already preloaded!
        Log.d("FIRST_LAUNCH_FLOW", "🔍 Looking for GoalFragment immediately...")
        
        // Look in all fragments
        val fragments = supportFragmentManager.fragments
        Log.d("FIRST_LAUNCH_FLOW", "📋 Found ${fragments.size} fragments total")
        
        val goalFragment = fragments.filterIsInstance<GoalFragment>().firstOrNull()
        
        if (goalFragment != null) {
            Log.d("FIRST_LAUNCH_FLOW", "✅ Found GoalFragment immediately! Showing dialog...")
            goalFragment.showAddGoalDialogPublic(onDismiss)
        } else {
            Log.d("FIRST_LAUNCH_FLOW", "⚠️ GoalFragment not found, trying navigation...")
            // Navigate to goals tab - GoalFragment is at index 7
            binding.viewPager.setCurrentItem(7, false)
            
            handler.postDelayed({
                Log.d("FIRST_LAUNCH_FLOW", "🔄 Retry after navigation...")
                val retryFragments = supportFragmentManager.fragments
                val retryGoalFragment = retryFragments.filterIsInstance<GoalFragment>().firstOrNull()
                
                if (retryGoalFragment != null) {
                    Log.d("FIRST_LAUNCH_FLOW", "✅ Found GoalFragment on retry")
                    retryGoalFragment.showAddGoalDialogPublic()
                } else {
                    Log.d("FIRST_LAUNCH_FLOW", "❌ GoalFragment still not found! This shouldn't happen with preloading")
                }
            }, 500)  // Short delay just for navigation
        }
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

                // Use applyRoomStatsWithCustom which properly filters blocked activities and old session activities
                runOnUiThread {
                    applyRoomStatsWithCustom(updatedRoom)
                }

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
                            val isSameSmoker = act.smokerId == currentSmokerUid
                            val isRemoteSource = act.deviceId.isNotEmpty() && act.deviceId != getAndroidDeviceId()
                            val isRecent = timeSinceActivity < 5000

                            Log.d(TAG, "🎧 Checking auto-advance: activitySmoker=${act.smokerId}, currentSmoker=$currentSmokerUid, autoMode=$isAutoMode, isFromUI=$isFromUI, fromRemote=$isRemoteSource, timeSince=${timeSinceActivity}ms")

                            // Check if we should advance the spinner
                            val shouldAdvance = isAutoMode && smokers.isNotEmpty() && (
                                (isFromUI && isSameSmoker) ||
                                (isRecent && (isSameSmoker || isRemoteSource))
                            )
                            if (shouldAdvance && !justRotatedFromUI) {
                                runOnUiThread {
                                    Log.d(TAG, "🎧 Auto-advancing to next smoker from room sync (fromUI=$isFromUI, recent=$isRecent, remote=$isRemoteSource, justRotated=$justRotatedFromUI)")
                                    moveToNextActiveSmoker()
                                }
                            } else if (shouldAdvance && justRotatedFromUI) {
                                Log.d(TAG, "🎧 Skipping auto-advance - just rotated from UI")
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
                val currentTimeMillis = System.currentTimeMillis()
                val roomRoundsCounter = updatedRoom.roundsCounter

                // Only sync from room if we're not actively changing the counter locally
                when {
                    // Case 1: We're actively updating rounds locally - ignore room sync completely
                    isUpdatingRoundsLocally && (currentTimeMillis - localRoundsUpdateTime < 5000) -> {
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
                            lastCounterChangeTime = currentTimeMillis
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

    // Add flag to track if we're performing an undo operation  
    private var isPerformingUndo = false
    
    // UNDO FIX: Track recently undone activities to filter them from room rebuilds
    // Store as Set of "type:timestamp" strings for efficient lookup
    private val recentlyUndoneActivities = mutableSetOf<String>()
    
    // Track recently deleted activities to prevent re-adding during reconciliation
    private val recentlyDeletedActivities = mutableSetOf<String>()
    
    // Move handleRoomUpdate to be a separate method in the class
    private fun handleRoomUpdate(room: RoomData) {
        lifecycleScope.launch {
            val refreshedSmokers = ensureRoomDataReady(room)
            if (refreshedSmokers != null) {
                Log.d(TAG, "👥 Room update refreshed ${refreshedSmokers.size} smokers before processing activities")
                handleSmokersListUpdate(refreshedSmokers)
            }
            processRoomUpdate(room)
        }
    }

    private suspend fun ensureRoomDataReady(room: RoomData): List<Smoker>? = withContext(Dispatchers.IO) {
        val missingShared = mutableListOf<String>()
        val missingParticipants = mutableListOf<String>()

        room.safeSharedSmokers().forEach { (smokerRoomId, _) ->
            val exists = if (smokerRoomId.startsWith("local_")) {
                val localUid = smokerRoomId.removePrefix("local_")
                repo.getSmokerByUid(localUid) != null
            } else {
                repo.getSmokerByCloudUserId(smokerRoomId) != null
            }

            if (!exists) {
                missingShared.add(smokerRoomId)
            }
        }

        room.participants.forEach { participantId ->
            if (repo.getSmokerByCloudUserId(participantId) == null) {
                missingParticipants.add(participantId)
            }
        }

        if (missingShared.isEmpty() && missingParticipants.isEmpty()) {
            return@withContext null
        }

        if (missingParticipants.isNotEmpty()) {
            Log.d(TAG, "👥 Ensuring ${missingParticipants.size} participant profiles exist locally before applying room update")
            updateParticipantsFromRoom(room)
        }

        if (missingShared.isNotEmpty()) {
            Log.d(TAG, "👥 Ensuring ${missingShared.size} shared smokers exist locally before applying room update")
            syncSharedSmokersFromRoomSafely(room)
        }

        repo.getAllSmokersList()
    }

    private fun updateCigaretteStatsFromActivities(activities: List<SessionActivity>) {
        // Get cigarettes from the activities and update stats
        val cigaretteActivities = activities.filter { it.type == "CIGARETTE" }
        if (cigaretteActivities.isEmpty()) return
        
        Log.d(TAG, "🚬📊 ROOM_SYNC: Updating cigarette stats from ${cigaretteActivities.size} cigarettes")
        
        // Group cigarettes by smoker
        val cigarettesBySmoker = cigaretteActivities.groupBy { it.smokerId }
        
        cigarettesBySmoker.forEach { (smokerId, smokerCigarettes) ->
            // Find smoker name
            val smokerName = when {
                smokerId.startsWith("local_") -> {
                    val localUid = smokerId.removePrefix("local_")
                    smokers.find { it.uid == localUid }?.name
                }
                else -> {
                    smokers.find { it.cloudUserId == smokerId }?.name
                }
            } ?: return@forEach
            
            // Sort by timestamp
            val sortedCigarettes = smokerCigarettes.sortedBy { it.timestamp }
            val lastCigaretteTime = sortedCigarettes.lastOrNull()?.timestamp ?: 0L
            
            // Calculate gaps if there are 2+ cigarettes
            val gaps = if (sortedCigarettes.size >= 2) {
                sortedCigarettes.zipWithNext { prev, curr ->
                    curr.timestamp - prev.timestamp
                }
            } else emptyList()
            
            val lastGap = gaps.lastOrNull() ?: 0L
            val avgGap = if (gaps.isNotEmpty()) gaps.average().toLong() else 0L
            val shortestGap = gaps.minOrNull() ?: 0L
            val longestGap = gaps.maxOrNull() ?: 0L
            
            // Update the per-smoker stats
            val currentStats = sessionStatsVM._perSmokerStats.value ?: emptyList()
            val updatedStats = currentStats.map { stat ->
                if (stat.smokerName == smokerName) {
                    stat.copy(
                        totalCigarettes = smokerCigarettes.size,
                        lastCigaretteTime = lastCigaretteTime,
                        lastCigaretteGapMs = lastGap,
                        avgCigaretteGapMs = avgGap,
                        shortestCigaretteGapMs = shortestGap,
                        longestCigaretteGapMs = longestGap
                    )
                } else stat
            }
            
            Log.d(TAG, "🚬📊 ROOM_SYNC: Updated ${smokerName} - ${smokerCigarettes.size} cigarettes, lastTime=$lastCigaretteTime")
            sessionStatsVM._perSmokerStats.postValue(updatedStats)
        }
    }
    
    private fun processRoomUpdate(room: RoomData) {
        Log.d(TAG, "🎧 Room updated!")
        Log.d(TAG, "     Activities: ${room.safeActivities().size}")
        Log.d(TAG, "     Is performing undo: $isPerformingUndo")

        val remoteAutoMode = room.isAutoMode
        // Only apply remote auto mode if we're not currently updating it locally
        if (remoteAutoMode != isAutoMode && !isUpdatingAutoModeToFirestore) {
            val timeSinceLastToggle = System.currentTimeMillis() - lastModeToggleTime
            
            // Smart conflict detection:
            // 1. If we just toggled locally and the remote value matches our local value, it's our own update coming back
            if (remoteAutoMode == lastLocalAutoModeValue && timeSinceLastToggle < 5000) {
                Log.d(TAG, "🔘📡 Ignoring echo of our own update: remote=$remoteAutoMode matches our last local change")
            }
            // 2. If remote is different from what we set, and we have a recent local change,
            //    compare against what we expect and ignore if it doesn't match for up to 15 seconds
            else if (lastLocalAutoModeValue != null && remoteAutoMode != lastLocalAutoModeValue && timeSinceLastToggle < 15000) {
                Log.d(TAG, "🔘📡 Ignoring stale remote value: $remoteAutoMode (expecting $lastLocalAutoModeValue, toggled ${timeSinceLastToggle}ms ago)")
            }
            // 3. Otherwise, it's a legitimate remote update from another user
            else {
                Log.d(TAG, "🔘📡 Applying remote auto mode: $remoteAutoMode (from another user)")
                isApplyingRemoteAutoMode = true
                try {
                    isAutoMode = remoteAutoMode
                    sessionStatsVM.setAutoMode(isAutoMode)
                    updateModeButtonText()
                    // Clear our local tracking since we're accepting a remote change
                    lastLocalAutoModeValue = null
                } finally {
                    isApplyingRemoteAutoMode = false
                }
            }
        } else if (isUpdatingAutoModeToFirestore) {
            Log.d(TAG, "🔘📡 Skipping remote auto mode update - local update in progress")
        }

        applyActiveSmokerFromRoomIfNeeded(room.safeActiveSmokerId())

        // Check for turn changes and show notification if needed
        currentShareCode?.let { shareCode ->
            lifecycleScope.launch {
                // Get current selected smoker
                val app = application as CloudCounterApplication
                val currentSmoker = withContext(Dispatchers.IO) {
                    repo.getSmokerById(app.defaultSmokerId)
                }
                
                // Get current user's Firebase ID - turn notifications are based on the signed-in user, not selected smoker
                val currentUserId = authManager.getCurrentUserId() ?: getAndroidDeviceId()
                
                // Process turn notification
                turnNotificationManager.processRoomUpdate(room, currentUserId, shareCode)
            }
        }

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
        
        // Track custom activity timestamps
        val customActivities = roomActivities.filter { it.type.startsWith("CUSTOM_") }
        val customByType = customActivities.groupBy { it.type.removePrefix("CUSTOM_") }
        customByType.forEach { (customId, activities) ->
            val lastTimestamp = activities.maxOfOrNull { it.timestamp } ?: 0L
            if (lastTimestamp > 0) {
                lastCustomActivityTimestamps[customId] = lastTimestamp
                Log.d(TAG, "⏰ CUSTOM_TIMER: Loaded timestamp for custom activity ID $customId: $lastTimestamp")
            }
        }

        // UNDO FIX: Don't rebuild activity history if we're performing an undo
        // The local activityHistory is the source of truth during undo operations
        if (isPerformingUndo) {
            Log.d(TAG, "🎧 UNDO FIX: Skipping activity history rebuild during undo operation")
            return
        }

        // Rebuild activity history from room activities for current session
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sessionActivities = room.safeActivities()
                    .filter { it.timestamp >= sessionStart }
                    .sortedBy { it.timestamp }

                // Convert to ActivityLog objects
                val activityLogs = sessionActivities.mapNotNull { activity ->
                    // UNDO FIX: Check if this activity was recently undone
                    val activityKey = "${activity.type}:${activity.timestamp}"
                    if (recentlyUndoneActivities.contains(activityKey)) {
                        Log.d(TAG, "🎧 UNDO FIX: Filtering out recently undone activity: $activityKey")
                        return@mapNotNull null
                    }
                    
                    // Find the smoker by UID
                    var resolvedSmoker = smokers.find { smoker ->
                        val smokerUid = if (smoker.isCloudSmoker && !smoker.cloudUserId.isNullOrEmpty()) {
                            smoker.cloudUserId
                        } else {
                            "local_${smoker.uid}"
                        }
                        smokerUid == activity.smokerId
                    }

                    if (resolvedSmoker == null) {
                        resolvedSmoker = if (activity.smokerId.startsWith("local_")) {
                            repo.getSmokerByUid(activity.smokerId.removePrefix("local_"))
                        } else {
                            repo.getSmokerByCloudUserId(activity.smokerId)
                        }
                        resolvedSmoker?.let {
                            Log.d(TAG, "🎧 Resolved missing smoker '${it.name}' from repository during room sync")
                        }
                    }

                    resolvedSmoker?.let {
                        // Check if this is a custom activity
                        val isCustom = activity.type.startsWith("CUSTOM_")
                        val activityType = if (isCustom) {
                            ActivityType.CUSTOM // Use CUSTOM type for custom activities
                        } else {
                            ActivityType.valueOf(activity.type)
                        }
                        val customId = if (isCustom) {
                            activity.type.removePrefix("CUSTOM_")
                        } else {
                            null
                        }
                        
                        ActivityLog(
                            id = 0L, // Will be set by database if needed
                            smokerId = it.smokerId,
                            type = activityType,
                            timestamp = activity.timestamp,
                            customActivityId = customId,
                            customActivityName = activity.customActivityName,
                            // Add cigarette fraction fields from remote activity
                            cigaretteFractionContribution = activity.cigaretteFractionContribution,
                            cigaretteFractionBefore = activity.cigaretteFractionBefore,
                            customRatioId = activity.customRatioId,
                            customRatioName = activity.customRatioName
                            // REMOVED: sessionCount = 0
                        )
                    }
                }

                // Instead of using incomplete remote data, fetch the actual activities from the database
                // which have all the fields including cigarette fraction data
                val sessionId = sessionStatsVM.currentSessionId.value
                if (sessionId != null) {
                    val dbActivities = withContext(Dispatchers.IO) {
                        repo.getActivitiesBySessionId(sessionId)
                            .takeLast(10)  // Only keep last 10 for history
                    }
                    
                    withContext(Dispatchers.Main) {
                        activityHistory.clear()
                        activityHistory.addAll(dbActivities)
                        updateUndoButtonVisibility()
                        Log.d(TAG, "🎧 Activity history rebuilt from DB: ${activityHistory.size} activities")
                        
                        // Debug log to verify we have the right data
                        if (activityHistory.isNotEmpty()) {
                            val last = activityHistory.last()
                            Log.d(TAG, "🚬💉 REBUILD: Last activity - type=${last.type}, customRatioName=${last.customRatioName}, contribution=${last.cigaretteFractionContribution}")
                        }
                        
                        // Update cigarette stats from activities in the history
                        updateCigaretteStatsFromActivities(sessionActivities)
                    }
                } else {
                    // Fallback to remote data if no session
                    withContext(Dispatchers.Main) {
                        activityHistory.clear()
                        activityHistory.addAll(activityLogs)
                        updateUndoButtonVisibility()
                        Log.d(TAG, "🎧 Activity history rebuilt from remote: ${activityHistory.size} activities")
                    }
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
        // Refresh stats when returning from GiantCounterActivity or other activities
        val statsNeedRefresh = prefs.getBoolean("stats_need_refresh", false)
        if (statsNeedRefresh) {
            Log.d(TAG, "🎯 Stats refresh flag detected on resume (sessionActive=$sessionActive)")
            prefs.edit().putBoolean("stats_need_refresh", false).apply()

            if (sessionActive) {
                Log.d(TAG, "🎯 Forcing stats refresh due to GiantCounter activity")
                refreshLocalSessionStatsIfNeeded(forceRefresh = true)
                Log.d(TAG, "🎯 Forced stats refresh triggered")
            } else {
                Log.d(TAG, "🎯 Session inactive; skipping forced stats refresh")
            }
        }

        if (sessionActive) {
            // Reload timer data from SharedPreferences (in case it was updated in GiantCounter)
            lastLogTime = prefs.getLong("lastLogTime", lastLogTime)
            lastConeTimestamp = prefs.getLong("lastConeTimestamp", lastConeTimestamp)
            lastJointTimestamp = prefs.getLong("lastJointTimestamp", lastJointTimestamp)
            lastBowlTimestamp = prefs.getLong("lastBowlTimestamp", lastBowlTimestamp)
            roundsLeft = prefs.getInt("roundsLeft", roundsLeft)
            initialRoundsSet = prefs.getInt("initialRoundsSet", initialRoundsSet)
            
            // Reload activity timestamps
            val timestampsString = prefs.getString("activitiesTimestamps", null)
            if (timestampsString != null && timestampsString.isNotEmpty()) {
                val savedTimestamps = timestampsString.split(",").mapNotNull { it.toLongOrNull() }
                if (savedTimestamps.isNotEmpty()) {
                    activitiesTimestamps.clear()
                    activitiesTimestamps.addAll(savedTimestamps.sorted())
                    Log.d(TAG, "📱 Reloaded ${activitiesTimestamps.size} timestamps from prefs")
                }
            }
            
            updateRoundsUI()
            refreshLocalSessionStatsIfNeeded()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == GIANT_COUNTER_REQUEST_CODE) {
            Log.d(TAG, "🎯 Returned from GiantCounterActivity, forcing stats refresh...")
            
            // Reload lock states from GiantCounter
            val updatedRandomFontsEnabled = prefs.getBoolean("random_fonts_enabled", true)
            val updatedColorChangingEnabled = prefs.getBoolean("color_changing_enabled", true)
            smokerManager.randomFontsEnabled = updatedRandomFontsEnabled
            smokerManager.colorChangingEnabled = updatedColorChangingEnabled
            Log.d(TAG, "🔒 Reloaded lock states from GiantCounter:")
            Log.d(TAG, "   randomFontsEnabled: $updatedRandomFontsEnabled")
            Log.d(TAG, "   colorChangingEnabled: $updatedColorChangingEnabled")
            
            // Reload font and color changes from GiantCounter
            val giantCounterColor = prefs.getInt("giant_counter_color", -1)
            val giantCounterFontIndex = prefs.getInt("giant_counter_font_index", -1)
            
            if (giantCounterColor != -1 || giantCounterFontIndex != -1) {
                Log.d(TAG, "🎨 Reloading font/color from GiantCounter - color: $giantCounterColor, fontIndex: $giantCounterFontIndex")
                
                // Check if fonts/colors are locked - if so, update global values
                if (!smokerManager.colorChangingEnabled && giantCounterColor != -1) {
                    // Color is locked - update global locked color
                    smokerManager.setGlobalLockedColor(giantCounterColor)
                    prefs.edit().putInt("global_locked_color", giantCounterColor).apply()
                    Log.d(TAG, "🔒 Updated global locked color: $giantCounterColor")
                }
                
                if (!smokerManager.randomFontsEnabled && giantCounterFontIndex != -1) {
                    // Font is locked - update global locked font
                    smokerManager.setGlobalLockedFontIndex(giantCounterFontIndex)
                    prefs.edit().putInt("global_font_index", giantCounterFontIndex).apply()
                    Log.d(TAG, "🔒 Updated global locked font index: $giantCounterFontIndex")
                }
                
                // Also update for the current smoker if unlocked
                val selectedPosition = binding.spinnerSmoker.selectedItemPosition
                val organizedSmokers = organizeSmokers().flatMap { it.smokers }
                val selectedSmoker = organizedSmokers.getOrNull(selectedPosition)
                
                if (selectedSmoker != null) {
                    // Update individual smoker values (for when unlocked)
                    if (giantCounterColor != -1 && smokerManager.colorChangingEnabled) {
                        smokerManager.setColorForSmoker(selectedSmoker.smokerId, giantCounterColor)
                        Log.d(TAG, "🎨 Updated smoker ${selectedSmoker.smokerId} color: $giantCounterColor")
                    }
                    
                    if (giantCounterFontIndex != -1 && smokerManager.randomFontsEnabled) {
                        val fontList = listOf(
                            R.font.bitcount_prop_double,
                            R.font.exile,
                            R.font.modak,
                            R.font.oi,
                            R.font.rubik_glitch,
                            R.font.sankofa_display,
                            R.font.silkscreen,
                            R.font.rubik_beastly,
                            R.font.sixtyfour,
                            R.font.monoton,
                            R.font.sedgwick_ave_display,
                            R.font.splash
                        )
                        
                        if (giantCounterFontIndex < fontList.size) {
                            val newFont = ResourcesCompat.getFont(this, fontList[giantCounterFontIndex])
                            if (newFont != null) {
                                smokerManager.setFontForSmoker(selectedSmoker.smokerId, newFont)
                                smokerManager.setFontIndexForSmoker(selectedSmoker.smokerId, giantCounterFontIndex)
                                Log.d(TAG, "🔤 Updated smoker ${selectedSmoker.smokerId} font: index $giantCounterFontIndex")
                            }
                        }
                    }
                    
                    // Force the spinner to completely refresh
                    Log.d(TAG, "🔄 Forcing spinner refresh...")
                    
                    // First invalidate the current view to force redraw
                    binding.spinnerSmoker.invalidate()
                    
                    // Notify adapter that data changed
                    smokerAdapterNew.notifyDataSetChanged()
                    
                    // Force recreate the spinner view by setting adapter again
                    val currentPosition = binding.spinnerSmoker.selectedItemPosition
                    binding.spinnerSmoker.adapter = null
                    binding.spinnerSmoker.adapter = smokerAdapterNew
                    binding.spinnerSmoker.setSelection(currentPosition, false)
                    Log.d(TAG, "🔄 Reset spinner adapter and restored position $currentPosition")
                    
                    // Wait a bit then update the view directly as a fallback
                    binding.spinnerSmoker.post {
                        val spinnerView = binding.spinnerSmoker.selectedView
                        if (spinnerView != null) {
                            val container = spinnerView as? FrameLayout
                            val textView = container?.findViewById<TextView>(R.id.textName)
                            if (textView != null) {
                                if (giantCounterColor != -1) {
                                    textView.setTextColor(giantCounterColor)
                                    Log.d(TAG, "🎨 Also applied color directly to current view")
                                }
                                if (giantCounterFontIndex != -1) {
                                    val fontList = listOf(
                                        R.font.bitcount_prop_double,
                                        R.font.exile,
                                        R.font.modak,
                                        R.font.oi,
                                        R.font.rubik_glitch,
                                        R.font.sankofa_display,
                                        R.font.silkscreen,
                                        R.font.rubik_beastly,
                                        R.font.sixtyfour,
                                        R.font.monoton,
                                        R.font.sedgwick_ave_display,
                                        R.font.splash
                                    )
                                    if (giantCounterFontIndex < fontList.size) {
                                        val newFont = ResourcesCompat.getFont(this@MainActivity, fontList[giantCounterFontIndex])
                                        textView.typeface = newFont
                                    }
                                    Log.d(TAG, "🔤 Also applied font directly to current view")
                                }
                            } else {
                                Log.d(TAG, "⚠️ TextView in spinner view is null")
                            }
                        } else {
                            Log.d(TAG, "⚠️ Spinner selected view is null")
                        }
                    }
                } else {
                    Log.d(TAG, "⚠️ No selected smoker found at position $selectedPosition")
                }
            }
            
            if (sessionActive) {
                Log.d(TAG, "🎯 Triggering shared stats refresh after GiantCounter return")
                refreshLocalSessionStatsIfNeeded(forceRefresh = true)
            }
        }
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
            Log.d(TAG, "🗑️ Smoker: ${smoker.name}, ID: ${smoker.smokerId}, isCloud: ${smoker.isCloudSmoker}")

            val sessionDao = AppDatabase.getDatabase(this@MainActivity).sessionSummaryDao()
            val smokerDao = AppDatabase.getDatabase(this@MainActivity).smokerDao()
            
            // Check if this smoker has participated in cloud sessions
            val hasCloudParticipation = sessionDao.hasSmokerParticipatedInCloudSessions(smoker.name)
            Log.d(TAG, "🗑️ Has cloud participation: $hasCloudParticipation")
            
            // Use soft delete for local smokers with cloud participation OR when keeping data
            // For cloud smokers with keepData, also use soft delete but sign out
            if ((hasCloudParticipation && !smoker.isCloudSmoker) || keepData) {
                // Soft delete the smoker
                Log.d(TAG, "🗑️ Using SOFT DELETE for ${smoker.name}")
                
                smokerDao.softDeleteSmoker(smoker.smokerId)
                
                // Check if we need to sign out the user (for cloud smokers being soft deleted)
                if (smoker.isCloudSmoker && smoker.cloudUserId == authManager.getCurrentUserId()) {
                    Log.d(TAG, "🗑️ Signing out user as their cloud smoker was soft deleted")
                    withContext(Dispatchers.Main) {
                        authManager.signOut()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    val message = when {
                        smoker.isCloudSmoker && keepData -> "${smoker.name} removed (data kept, signed out)"
                        hasCloudParticipation -> "${smoker.name} removed (cloud data preserved)"
                        else -> "${smoker.name} removed (historical data kept)"
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            } else {
                // No cloud participation or is a cloud smoker - proceed with hard delete
                if (!keepData) {
                    // Delete or update session summaries that include this smoker
                    val allSummaries = sessionDao.getAllSummariesSync()
                    var sessionsDeleted = 0
                    var sessionsUpdated = 0
                    
                    Log.d(TAG, "🗑️ Checking ${allSummaries.size} session summaries for ${smoker.name}")
                    
                    for (summary in allSummaries) {
                        if (summary.smokerNames.contains(smoker.name)) {
                            if (summary.smokerNames.size == 1) {
                                // Single smoker session - delete entirely
                                sessionDao.delete(summary)
                                sessionsDeleted++
                                Log.d(TAG, "🗑️ Deleted session ${summary.id} (single smoker)")
                            } else {
                                // Multi-smoker session - remove this smoker from the list
                                val updatedNames = summary.smokerNames.filter { name -> name != smoker.name }
                                val smokerIndex = summary.smokerNames.indexOf(smoker.name)
                                
                                // Update cones per smoker list if index is valid
                                val updatedConesPerSmoker = if (smokerIndex >= 0 && smokerIndex < summary.conesPerSmoker.size) {
                                    summary.conesPerSmoker.filterIndexed { index, value -> index != smokerIndex }
                                } else {
                                    summary.conesPerSmoker
                                }
                                
                                // Recalculate total cones
                                val updatedTotalCones = updatedConesPerSmoker.sum()
                                
                                val updatedSummary = summary.copy(
                                    smokerNames = updatedNames,
                                    conesPerSmoker = updatedConesPerSmoker,
                                    totalCones = updatedTotalCones
                                )
                                sessionDao.update(updatedSummary)
                                sessionsUpdated++
                                Log.d(TAG, "🗑️ Updated session ${summary.id} (removed ${smoker.name})")
                            }
                        }
                    }
                    
                    Log.d(TAG, "🗑️ Sessions deleted: $sessionsDeleted, updated: $sessionsUpdated")
                    
                    // Delete all activity logs
                    val logs = repo.getLogsForSmoker(smoker.smokerId)
                    Log.d(TAG, "🗑️ Deleting ${logs.size} activity logs")
                    logs.forEach { log ->
                        repo.delete(log)
                    }
                } else {
                    Log.d(TAG, "🗑️ Keeping ${repo.getLogsForSmoker(smoker.smokerId).size} activity logs")
                    Log.d(TAG, "🗑️ Keeping session summaries intact")
                }

                // Hard delete the smoker entity
                repo.deleteSmoker(smoker)
                Log.d(TAG, "🗑️ ✅ Smoker deleted from local database")
                
                // Check if we need to sign out the user
                if (smoker.isCloudSmoker && smoker.cloudUserId == authManager.getCurrentUserId()) {
                    Log.d(TAG, "🗑️ Signing out user as their cloud smoker was deleted")
                    withContext(Dispatchers.Main) {
                        authManager.signOut()
                    }
                }

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
            text = "What would you like to do?\n\n'Delete Everything' will remove all activities and session history"
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
        performManualFadeIn(rootContainer, 250L)
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
        performManualFadeIn(rootContainer, 250L)
    }
    
    private fun clearSeshStatsForSmoker(smoker: Smoker) {
        Log.d(TAG, "🧹 === CLEAR SESH STATS FOR ${smoker.name} START ===")
        Log.d("SeshFlow", "CLEAR per-smoker requested: smokerId=${smoker.smokerId}, active=$sessionActive, sessionStart=$sessionStart")
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
                    Log.d("SeshFlow", "CLEAR per-smoker deletedCount=$logsCleared (range=[$sessionStart..$now])")
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
        Log.d("SeshFlow", "CLEAR ALL requested: active=$sessionActive, sessionStart=$sessionStart")
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
                        Log.d("SeshFlow", "CLEAR ALL per-smoker deletedCount=$logsCleared for smokerId=${smoker.smokerId} (range=[$sessionStart..$now])")
                    }
                }

                Log.d(TAG, "🧹🔴 Total local activities cleared: $totalCleared")
                Log.d("SeshFlow", "CLEAR ALL total deletedCount=$totalCleared (range=[$sessionStart..$now])")

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
        Log.d("SeshFlow", "Reconcile start: room=${updatedRoom.shareCode}, name=${updatedRoom.name}")
        Log.d("SeshFlow", "Reconcile context: sessionStart=$sessionStart, active=$sessionActive")

        // Guard: Only reconcile while an active session is running and for the same room
        if (!sessionActive || sessionStart <= 0L) {
            Log.d(
                "SeshFlow",
                "Reconcile skipped: inactive or invalid session (active=$sessionActive, sessionStart=$sessionStart)"
            )
            return
        }
        val currentCode = currentShareCode
        if (currentCode.isNullOrEmpty() || updatedRoom.shareCode != currentCode) {
            Log.d(
                "SeshFlow",
                "Reconcile skipped: room mismatch or no current room (current=$currentCode, incoming=${updatedRoom.shareCode})"
            )
            return
        }

        // IMPORTANT: First, remove any local activities that are no longer in the room
        // This handles the undo case where activities were removed from the room
        // Limit reconciliation strictly to the current session's activities
        val currentSessionId = sessionStatsVM.currentSessionId.value ?: return
        val localSessionActivities = withContext(Dispatchers.IO) {
            repo.getActivitiesBySessionId(currentSessionId)
        }
        Log.d(
            "SeshFlow",
            "Reconcile scope: sessionId=$currentSessionId, localCount=${localSessionActivities.size}"
        )

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

                // For custom activities, use CUSTOM_[id] format to match remote format
                val activityTypeStr = if (!localActivity.customActivityId.isNullOrEmpty()) {
                    "CUSTOM_${localActivity.customActivityId}"
                } else {
                    localActivity.type.name
                }
                
                val localActivityId = "${smokerUid}_${activityTypeStr}_${localActivity.timestamp}"
                val existsRemotely = remoteActivityIds.contains(localActivityId)
                Log.d(
                    "SeshFlow",
                    "Reconcile check: localId=$localActivityId, existsRemotely=$existsRemotely, sessionId=${localActivity.sessionId}"
                )

                // Only delete if it's from the CURRENT session
                val currentSessionId = sessionStatsVM.currentSessionId.value
                val isFromCurrentSession =
                    (localActivity.sessionId != null && localActivity.sessionId == currentSessionId) ||
                            (localActivity.sessionStartTime != null && localActivity.sessionStartTime == sessionStart)

                if (!existsRemotely && isFromCurrentSession) {
                    // Check if this activity was just created (within last 5 seconds)
                    val ageMs = System.currentTimeMillis() - localActivity.timestamp
                    if (ageMs < 5000) {
                        // Activity was just created, give it time to sync
                        Log.d(TAG, "🔁 Skipping deletion of recently created activity: ${smoker.name} ${localActivity.type} @ ${localActivity.timestamp} (age: ${ageMs}ms)")
                    } else {
                        // This activity exists locally but not in the room - delete it
                        Log.d(TAG, "🔁 Removing local activity not in room: ${smoker.name} ${localActivity.type} @ ${localActivity.timestamp}")
                        Log.d(
                            "SeshFlow",
                            "Reconcile deleting local-only activity id=${localActivity.id}, smokerId=${localActivity.smokerId}, type=${localActivity.type}, ts=${localActivity.timestamp}"
                        )
                        repo.delete(localActivity)
                    }
                } else if (!existsRemotely) {
                    Log.d(
                        "SeshFlow",
                        "Reconcile NOT deleting (not current session): id=${localActivity.id}, sessionId=${localActivity.sessionId}, startTime=${localActivity.sessionStartTime}"
                    )
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
                
                // Check if this activity was recently deleted - if so, skip it
                val activityKey = "${localSmoker.smokerId}_${remote.type}_${remote.timestamp}"
                val universalKey = "${remote.type}_${remote.timestamp}"
                
                if (recentlyDeletedActivities.contains(activityKey) || 
                    recentlyDeletedActivities.contains(universalKey)) {
                    Log.d(TAG, "🔁 Skipping recently deleted activity: $activityKey or $universalKey")
                    continue
                }
                
                // Also check permanent block list
                val blockedPrefs = getSharedPreferences("blocked_activities", Context.MODE_PRIVATE)
                val cloudActivityKey = "${remote.smokerId}_${remote.type}_${remote.timestamp}"
                if (blockedPrefs.getBoolean(cloudActivityKey, false)) {
                    Log.d(TAG, "🚫 Skipping permanently blocked activity: $cloudActivityKey")
                    continue
                }

                // Check if this is a custom activity
                val isCustom = remote.type.startsWith("CUSTOM_")
                val activityType = if (isCustom) {
                    ActivityType.CUSTOM // Use CUSTOM type for custom activities
                } else {
                    try {
                        ActivityType.valueOf(remote.type.uppercase())
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "🔁 Unknown activity type: ${remote.type}")
                        continue
                    }
                }
                
                val customId = if (isCustom) {
                    remote.type.removePrefix("CUSTOM_")
                } else {
                    null
                }

                // Check if this exact log already exists locally
                val existingLog = repo.findLogByDetails(localSmoker.smokerId, activityType, remote.timestamp)

                if (existingLog != null) {
                    // Already exists, skip
                    continue
                }
                
                // CRITICAL: Only add activities that belong to the current session
                // Check if the activity timestamp is within the current session time range
                val currentSessionId = sessionStatsVM.currentSessionId.value
                if (sessionActive && currentSessionId != null && remote.timestamp < currentSessionId) {
                    Log.d(TAG, "🔁 ⏭️ Skipping old activity from previous session: ${localSmoker.name} ${activityType.name} @ ${remote.timestamp} (before session start: $currentSessionId)")
                    continue
                }

                // Create the activity log with session info
                val newLog = ActivityLog(
                    id = 0L,
                    smokerId = localSmoker.smokerId,
                    type = activityType,
                    timestamp = remote.timestamp,
                    sessionId = if (sessionActive) currentSessionId else null,
                    sessionStartTime = if (sessionActive) sessionStart else null,
                    customActivityId = customId,
                    customActivityName = remote.customActivityName,
                    customRatioId = remote.customRatioId,
                    customRatioName = remote.customRatioName,
                    cigaretteFractionContribution = remote.cigaretteFractionContribution,
                    cigaretteFractionBefore = remote.cigaretteFractionBefore
                )

                repo.insert(newLog)
                Log.d(TAG, "🔁 ✅ ADDED activity from cloud: ${localSmoker.name} ${activityType.name} @ ${remote.timestamp}")
                Log.d(TAG, "🔁   Details: smokerId=${localSmoker.smokerId}, remoteSmokerUid=${remote.smokerId}")

            } catch (e: Exception) {
                Log.e(TAG, "🔁 Error reconciling activity: ${e.message}", e)
            }
        }
        Log.d(TAG, "🔁 Activity reconciliation complete")
    }

    private suspend fun logHit(type: ActivityType, now: Long, customRatio: SmokeRatio? = null) {
        Log.d(TAG, "📱 === logHit ENTRY ===")
        Log.d(TAG, "📱 Activity type: $type, timestamp: $now")
        Log.d(TAG, "📱 Thread: ${Thread.currentThread().name}")
        Log.d("CUSTOM_STASH_DEBUG", "🚀 === logHit ENTRY ===")
        Log.d("CUSTOM_STASH_DEBUG", "🚀 Activity type: $type")
        Log.d("CUSTOM_STASH_DEBUG", "🚀 Is custom type: ${type == ActivityType.CUSTOM}")
        
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
        val sessionId = sessionStatsVM.currentSessionId.value
        Log.d(TAG, "🎯 Creating activity with sessionId: $sessionId")

        // Calculate grams based on custom ratio or default ratios
        val gramsForActivity = when {
            customRatio != null && (type == ActivityType.BOWL || type == ActivityType.JOINT) -> {
                // Use custom ratio: chopAmount divided by numberOfSmokes
                val gramsPerSmoke = customRatio.chopAmount / customRatio.numberOfSmokes
                Log.d(TAG, "🎯 Using custom ratio: ${customRatio.name} - ${gramsPerSmoke}g per smoke")
                gramsPerSmoke * (if (type == ActivityType.BOWL) pendingBowlQuantity else 1)
            }
            else -> when (type) {
                ActivityType.CONE -> ratios?.coneGrams ?: 0.3
                ActivityType.JOINT -> ratios?.jointGrams ?: 0.5
                ActivityType.BOWL -> (ratios?.bowlGrams ?: 0.2) * pendingBowlQuantity
                else -> 0.0
            }
        }

        // Calculate cigarette fraction contribution for this activity
        val cigaretteFractionContribution = when {
            customRatio != null && (type == ActivityType.BOWL || type == ActivityType.JOINT) -> {
                // Calculate how much this activity contributes to cigarette fraction
                val quantity = if (type == ActivityType.BOWL) pendingBowlQuantity else 1
                val contribution = customRatio.cigarettesPerSmoke * quantity
                Log.d(TAG, "🚬💉 JOINT/BOWL contribution calc: ${type.name} with customRatio=${customRatio.name}, cigarettesPerSmoke=${customRatio.cigarettesPerSmoke}, quantity=$quantity = $contribution")
                contribution
            }
            else -> 0.0
        }
        
        // Get the fraction before this activity (for tracking)
        val fractionBefore = if (cigaretteFractionContribution > 0) {
            ratioManager.getCigaretteFraction(selectedSmoker.smokerId)
        } else {
            0.0
        }

        val activityLog = ActivityLog(
            smokerId = selectedSmoker.smokerId,
            consumerId = selectedSmoker.smokerId,
            payerStashOwnerId = payerStashOwnerId,
            type = type,
            timestamp = adjustedNow,
            sessionId = sessionId,
            sessionStartTime = if (sessionActive) sessionStart else null,
            bowlQuantity = if (type == ActivityType.BOWL) pendingBowlQuantity else 1,
            gramsAtLog = gramsForActivity,
            pricePerGramAtLog = currentStash?.pricePerGram ?: 15.0,
            customRatioId = customRatio?.id,
            customRatioName = customRatio?.name,
            cigaretteFractionContribution = cigaretteFractionContribution,
            cigaretteFractionBefore = fractionBefore
        )

        // ALWAYS insert to local database first
        val insertedId = withContext(Dispatchers.IO) {
            val id = repo.insert(activityLog)
            Log.d(TAG, "🎯 Inserted activity to local DB with ID: $id, sessionId: $sessionId")
            Log.d(TAG, "🚬💉 STORED: ${type.name} customRatioName=${activityLog.customRatioName}, cigaretteFractionContribution=${activityLog.cigaretteFractionContribution}")
            id
        }

        val historyActivity = activityLog.copy(id = insertedId)
        
        // Add to activity history for undo functionality
        activityHistory.add(historyActivity)
        if (activityHistory.size > 10) {
            activityHistory.removeAt(0)
        }
        Log.d(TAG, "🚬💉 HISTORY: Added ${type.name} to history with customRatioName=${historyActivity.customRatioName}, contribution=${historyActivity.cigaretteFractionContribution}")
        
        // Handle cigarette tracking if using custom ratio (joints only, not bowls)
        // Wait for cigarette tracking to complete before continuing
        if (customRatio != null && type == ActivityType.JOINT) {
            handleCigaretteTracking(customRatio, type, adjustedNow, selectedSmoker, payerStashOwnerId)
            // Add a small delay to ensure database write completes
            delay(100)
            Log.d(TAG, "🚬 CIGARETTE_STATS: Cigarette tracking completed for joint")
        }
        
        // Update active session summary
        updateActiveSessionSummary()

        // THEN sync to cloud if in a cloud session
        if (shareCode != null) {
            Log.d(TAG, "🎯 Cloud session detected, checking network status...")
            val deviceId = getAndroidDeviceId()

            if (!isNetworkAvailable) {
                // OFFLINE - Add to queue
                Log.d(TAG, "📴 OFFLINE: Adding activity to queue for later sync")
                val offlineActivity = OfflineActivity(
                    activityId = insertedId.toString(),
                    shareCode = shareCode,
                    smokerUid = smokerActivityUid,
                    smokerName = selectedSmoker.name,
                    activityType = type,
                    timestamp = adjustedNow,
                    deviceId = deviceId,
                    cigaretteFractionContribution = cigaretteFractionContribution,
                    cigaretteFractionBefore = fractionBefore,
                    customRatioId = customRatio?.id,
                    customRatioName = customRatio?.name
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
                    deviceId = deviceId,
                    cigaretteFractionContribution = cigaretteFractionContribution,
                    cigaretteFractionBefore = fractionBefore,
                    customRatioId = customRatio?.id,
                    customRatioName = customRatio?.name
                ).fold(
                    onSuccess = {
                        Log.d(TAG, "🎯 ✅ Activity synced to cloud room with smoker UID: $smokerActivityUid")
                        // Save last activity type for turn notifications
                        turnNotificationManager.saveLastActivityType(type)
                        lastHitCameFromUI = true
                        handler.postDelayed({
                            lastHitCameFromUI = false
                            Log.d(TAG, "🎯 Reset lastHitCameFromUI flag after delay")
                        }, 500)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "🎯 ❌ Failed to sync to cloud room: ${error.message}")
                        val handled = handleCloudSyncFailure(
                            error = error,
                            shareCode = shareCode,
                            smokerUid = smokerActivityUid,
                            smokerName = selectedSmoker.name,
                            activityType = type,
                            timestamp = adjustedNow,
                            deviceId = deviceId,
                            localActivityId = insertedId.toString(),
                            cigaretteFractionContribution = cigaretteFractionContribution,
                            cigaretteFractionBefore = fractionBefore,
                            customRatioId = customRatio?.id,
                            customRatioName = customRatio?.name
                        )
                        if (!handled) {
                            Log.w(TAG, "🎯 Cloud sync failure not queued (non-quota issue)")
                        }
                    }
                )
            }
        } else {
            // Local session - refresh stats will be done after cigarette tracking completes
            Log.d(TAG, "🎯 Local session, will refresh stats after cigarette tracking...")
            
            // Refresh stats - cigarette tracking has already been handled with delays above
            refreshLocalSessionStatsIfNeeded()
        }

        // Handle post-hit actions
        handlePostHitActionsWithPayer(
            selectedSmoker,
            selectedPosition,
            type,
            adjustedNow,
            payerStashOwnerId,
            customRatio,
            historyActivity
        )

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
        payerStashOwnerId: String?,
        customRatio: SmokeRatio? = null,
        historyActivityOverride: ActivityLog? = null
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
                ActivityType.CUSTOM -> {
                    // Track custom activity timestamps using the last selected custom activity ID
                    lastSelectedCustomActivityId?.let { customId ->
                        lastCustomActivityTimestamps[customId] = now
                        Log.d(TAG, "⏰ CUSTOM_TIMER: Updated timestamp for custom activity (ID: $customId) to $now")
                    }
                }
                ActivityType.CIGARETTE -> { /* Cigarettes don't update core timestamps */ }
                ActivityType.SESSION_SUMMARY -> { /* Session summaries don't update timestamps */ }
            }

            // Notify auto-add manager about the manual activity - FIXED: Added timestamp parameter
            if (::autoAddManager.isInitialized) {
                autoAddManager.onActivityLogged(type, now)
            }

            val activityLog = historyActivityOverride ?: run {
                val cigaretteFractionContribution = when {
                    type == ActivityType.CIGARETTE && payerStashOwnerId == null -> {
                        -1.0
                    }
                    customRatio != null && (type == ActivityType.BOWL || type == ActivityType.JOINT) -> {
                        val quantity = if (type == ActivityType.BOWL) pendingBowlQuantity else 1
                        customRatio.cigarettesPerSmoke * quantity
                    }
                    type == ActivityType.CONE -> {
                        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val lastSelectedBowlRatioId = prefs.getString("last_bowl_ratio_id", null)
                        if (lastSelectedBowlRatioId != null) {
                            val bowlRatios = ratioManager.getRatiosForType(SmokeRatio.RatioType.BOWL)
                            val bowlRatio = bowlRatios.find { it.id == lastSelectedBowlRatioId }
                            if (bowlRatio != null) {
                                val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
                                val ratios = stashViewModel.ratios.value
                                val conesPerBowl = if (ratios != null && ratios.coneGrams > 0) {
                                    ratios.bowlGrams / ratios.coneGrams
                                } else {
                                    4.0
                                }
                                bowlRatio.cigarettesPerSmoke / conesPerBowl
                            } else {
                                0.0
                            }
                        } else {
                            0.0
                        }
                    }
                    else -> 0.0
                }

                val fractionBefore = if (cigaretteFractionContribution != 0.0) {
                    ratioManager.getCigaretteFraction(selectedSmoker.smokerId)
                } else {
                    0.0
                }

                ActivityLog(
                    id = 0L,
                    smokerId = selectedSmoker.smokerId,
                    consumerId = selectedSmoker.smokerId,
                    payerStashOwnerId = payerStashOwnerId,
                    type = type,
                    timestamp = now,
                    sessionId = sessionStatsVM.currentSessionId.value,
                    sessionStartTime = if (sessionActive) sessionStart else null,
                    gramsAtLog = 0.0,
                    pricePerGramAtLog = 0.0,
                    cigaretteFractionContribution = cigaretteFractionContribution,
                    cigaretteFractionBefore = fractionBefore
                )
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

        // STASH TRACKING WITH CUSTOM ACTIVITY DEBUG
        val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
        Log.d("CUSTOM_STASH_DEBUG", "🔵 === STASH TRACKING CHECK ===")
        Log.d("CUSTOM_STASH_DEBUG", "🔵 Activity type: $type")
        Log.d("CUSTOM_STASH_DEBUG", "🔵 Is core activity: ${type in listOf(ActivityType.JOINT, ActivityType.CONE, ActivityType.BOWL)}")
        Log.d("CUSTOM_STASH_DEBUG", "🔵 Stash available: ${stashViewModel.currentStash.value != null}")
        
        if (stashViewModel.currentStash.value != null) {
            // IMPORTANT: Only process core activities for stash system
            if (type in listOf(ActivityType.JOINT, ActivityType.CONE, ActivityType.BOWL)) {
                Log.d("CUSTOM_STASH_DEBUG", "🔵 ✅ Processing CORE activity for stash")
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
            } else {
                Log.d("CUSTOM_STASH_DEBUG", "🔵 ❌ SKIPPING CUSTOM activity - not processing for stash")
            }
        } else {
            Log.d("CUSTOM_STASH_DEBUG", "🔵 ❌ No stash available - skipping stash tracking")
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
                goalService.updateGoalProgressForSelectedActivity(
                    activityType = type,
                    sessionShareCode = sessionShareCode,
                    currentSmokerName = selectedSmoker.name
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

    fun refreshLocalSessionStatsIfNeeded(forceRefresh: Boolean = false) {
        val timestamp = System.currentTimeMillis()
        Log.d(TAG, "📊 === STATS REFRESH CALLED ===")
        Log.d(TAG, "📊 refreshLocalSessionStatsIfNeeded at timestamp: $timestamp")
        Log.d(TAG, "📊 Session active: $sessionActive, forceRefresh: $forceRefresh, isOptimisticMode: $isOptimisticMode")
        Log.d(TAG, "📊 Thread: ${Thread.currentThread().name}")

        // Don't refresh if in optimistic mode (batch processing)
        if (isOptimisticMode && !forceRefresh) {
            Log.d(TAG, "📊 Skipping stats refresh - in optimistic mode")
            return
        }

        // IMPORTANT: Don't refresh stats if session is not active
        if (!sessionActive) {
            Log.d(TAG, "🔍 Skipping stats refresh - session not active")
            return
        }

        val isConnectedToCloud = currentShareCode != null && currentRoom != null && authManager.isSignedIn
        
        // Check if we're in continue mode - if so, we need to refresh even if in a room
        val isInContinueMode = sessionStatsVM.isInContinueMode()

        // If forceRefresh is true (e.g., after deletion), always refresh regardless of cloud status
        if (!forceRefresh && isConnectedToCloud && !isInContinueMode) {
            Log.d(TAG, "🔍 Skipping local stats refresh - connected to cloud room and not in continue mode")
            return
        }
        
        if (isConnectedToCloud && isInContinueMode) {
            Log.d(TAG, "🔍 In cloud room but continue mode active - proceeding with refresh")
        }
        
        if (forceRefresh) {
            Log.d(TAG, "🔍 Force refresh requested - proceeding regardless of cloud status")
        }

        lifecycleScope.launch {
            Log.d(TAG, "🔍🚀 Starting refresh coroutine...")
            
            val allSmokersFromDb = withContext(Dispatchers.IO) {
                repo.getAllSmokersList()
            }
            Log.d(TAG, "🔍👥 Found ${allSmokersFromDb.size} smokers in database")

            val now = System.currentTimeMillis()
            val perSmokerList = mutableListOf<PerSmokerStats>()
            var totalCones = 0
            var totalJoints = 0
            var totalBowls = 0
            var totalCigarettes = 0

            // Track last smoker info for each activity type
            var lastConeSmokerName: String? = null
            var lastJointSmokerName: String? = null
            var lastBowlSmokerName: String? = null
            var lastCigaretteSmokerName: String? = null
            var lastConeTimestamp: Long = 0L
            var lastBowlTimestamp: Long = 0L
            var conesSinceLastBowl = 0
            
            // Get carried-over stats from ViewModel early so we can use them throughout
            val (carriedOverCones, carriedOverRounds, carriedOverBowls) = sessionStatsVM.getCarriedOverStats()
            val isInContinueMode = sessionStatsVM.isInContinueMode()
            Log.d(TAG, "🔍📦 Carried-over stats - Cones: $carriedOverCones, Rounds: $carriedOverRounds, Bowls: $carriedOverBowls")
            Log.d(TAG, "🔍🌁 Continue mode active: $isInContinueMode")

            // Track gaps - these will be calculated from ALL activities
            var lastGapMs: Long? = null  // The gap between the two most recent activities
            var previousGapMs: Long? = null  // The gap before that
            var longestConeGapMs: Long = 0L  // Longest gap between cones specifically
            var shortestConeGapMs: Long = Long.MAX_VALUE  // Shortest gap between cones specifically

            // Get ALL activities in session (not just cones)
            val allSessionActivitiesRaw = withContext(Dispatchers.IO) {
                repo.getLogsInTimeRange(sessionStart, now)
            }
            
            // Filter out blocked activities
            val blockedPrefs = getSharedPreferences("blocked_activities", Context.MODE_PRIVATE)
            val allSessionActivities = allSessionActivitiesRaw.filter { activity ->
                val smokerUid = if (activity.smokerId > 0) {
                    val smoker = allSmokersFromDb.find { it.smokerId == activity.smokerId }
                    smoker?.cloudUserId ?: smoker?.uid ?: "unknown"
                } else {
                    "unknown"
                }
                val activityKey = "${smokerUid}_${activity.type}_${activity.timestamp}"
                val isBlocked = blockedPrefs.getBoolean(activityKey, false)
                if (isBlocked) {
                    Log.d(TAG, "🚫 Filtering out blocked activity from stats: $activityKey")
                }
                !isBlocked
            }.sortedBy { it.timestamp }

            Log.d(TAG, "🔍 Total activities in session: ${allSessionActivities.size}")
            Log.d(TAG, "🔍🔍 === FETCHED ACTIVITIES FROM DATABASE ===")
            allSessionActivities.forEach { activity ->
                Log.d(TAG, "🔍🔍 Activity ID=${activity.id}, type=${activity.type}, timestamp=${activity.timestamp}, smokerId=${activity.smokerId}")
            }
            Log.d(TAG, "🔍🔍 === END FETCHED ACTIVITIES ===")
            
            // Debug: Check for cigarettes specifically
            val cigaretteCount = allSessionActivities.count { it.type == ActivityType.CIGARETTE }
            Log.d(TAG, "🚬 CIGARETTE_DEBUG: Found $cigaretteCount cigarettes in allSessionActivities")
            allSessionActivities.filter { it.type == ActivityType.CIGARETTE }.forEach { cig ->
                Log.d(TAG, "🚬 CIGARETTE_DEBUG: Cigarette - timestamp=${cig.timestamp}, smokerId=${cig.smokerId}, sessionId=${cig.sessionId}")
            }

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
                
                // Check if this bowl has associated cones from a previous session
                val associatedCones = lastBowl.associatedConesCount ?: 0
                Log.d(TAG, "🔍🌿 Bowl has associated cones from previous session: $associatedCones")
                
                // Count cones since this bowl in current session
                val currentSessionCones = allSessionActivities
                    .filter { it.type == ActivityType.CONE && it.timestamp > lastBowlTimestamp }
                    .size
                    
                // If this bowl has associated cones, those are already counted in totalCones
                // So we only count cones AFTER this bowl
                conesSinceLastBowl = currentSessionCones
                
                Log.d(TAG, "🔍🌿 Cones since last bowl: $conesSinceLastBowl (current session only)")
            } else {
                conesSinceLastBowl = coneLogs.size
            }
            
            // Find last cigarette and its smoker
            val cigaretteLogs = allSessionActivities.filter { it.type == ActivityType.CIGARETTE }
            Log.d(TAG, "🚬 CIGARETTE_STATS: Found ${cigaretteLogs.size} cigarettes in session")
            val lastCigarette = cigaretteLogs.lastOrNull()
            if (lastCigarette != null) {
                val cigaretteSmoker = withContext(Dispatchers.IO) {
                    repo.getSmokerById(lastCigarette.smokerId)
                }
                lastCigaretteSmokerName = cigaretteSmoker?.name
                Log.d(TAG, "🚬 CIGARETTE_STATS: Found last CIGARETTE smoker: $lastCigaretteSmokerName")
            } else {
                Log.d(TAG, "🚬 CIGARETTE_STATS: No CIGARETTE found in session")
            }

            // Track custom activities stats
            val customActivityGroupStats = mutableMapOf<String, CustomActivityGroupStat>()
            
            // Calculate per-smoker stats
            for (smoker in allSmokersFromDb) {
                // Get all logs for this smoker (including cigarettes they consume)
                val allLogs = withContext(Dispatchers.IO) {
                    val normalLogs = repo.getLogsForSmoker(smoker.smokerId)
                    // Also get cigarettes where this smoker is the consumer (in case they're different)
                    val cigaretteLogs = repo.getLogsInTimeRange(0L, Long.MAX_VALUE).filter { 
                        it.type == ActivityType.CIGARETTE && it.consumerId == smoker.smokerId 
                    }
                    // Combine and remove duplicates
                    (normalLogs + cigaretteLogs).distinctBy { it.id }
                }
                
                // Debug: Check what we got for this smoker
                val cigarettesInAllLogs = allLogs.filter { it.type == ActivityType.CIGARETTE }
                Log.d(TAG, "🚬📊 FIX: For ${smoker.name} (smokerId=${smoker.smokerId}), found ${allLogs.size} total logs, ${cigarettesInAllLogs.size} cigarettes")
                cigarettesInAllLogs.take(3).forEach { cig ->
                    Log.d(TAG, "🚬📊 FIX:   Cigarette - id=${cig.id}, smokerId=${cig.smokerId}, consumerId=${cig.consumerId}, timestamp=${cig.timestamp}")
                }

                val sessionLogsRaw = allLogs.filter { it.timestamp >= sessionStart && it.timestamp <= now }
                
                // Filter out blocked activities from per-smoker stats
                val smokerUid = smoker.cloudUserId ?: smoker.uid ?: "unknown"
                val sessionLogs = sessionLogsRaw.filter { activity ->
                    val activityKey = "${smokerUid}_${activity.type}_${activity.timestamp}"
                    val isBlocked = blockedPrefs.getBoolean(activityKey, false)
                    if (isBlocked) {
                        Log.d(TAG, "🚫 Filtering out blocked activity from per-smoker stats: $activityKey")
                    }
                    !isBlocked
                }
                
                // Debug log all activity types for this smoker
                val activityTypes = sessionLogs.groupBy { it.type }
                Log.d(TAG, "🚬 CIGARETTE_STATS: ${smoker.name} activities breakdown:")
                activityTypes.forEach { (type, logs) ->
                    Log.d(TAG, "🚬 CIGARETTE_STATS:   $type: ${logs.size} activities")
                }

                // UNDO FIX: Filter out recently undone activities when counting
                val cones = sessionLogs.count { log -> 
                    if (log.type == ActivityType.CONE && log.customActivityId.isNullOrEmpty()) {
                        val activityKey = "${log.type}:${log.timestamp}"
                        !recentlyUndoneActivities.contains(activityKey)
                    } else {
                        false
                    }
                }
                val joints = sessionLogs.count { log ->
                    if (log.type == ActivityType.JOINT) {
                        val activityKey = "${log.type}:${log.timestamp}"
                        !recentlyUndoneActivities.contains(activityKey)
                    } else {
                        false
                    }
                }
                val bowls = sessionLogs.count { log ->
                    if (log.type == ActivityType.BOWL) {
                        val activityKey = "${log.type}:${log.timestamp}"
                        !recentlyUndoneActivities.contains(activityKey)
                    } else {
                        false
                    }
                }
                val cigarettes = sessionLogs.count { log ->
                    if (log.type == ActivityType.CIGARETTE) {
                        val activityKey = "${log.type}:${log.timestamp}"
                        val isUndone = recentlyUndoneActivities.contains(activityKey)
                        Log.d(TAG, "🚬 CIGARETTE_STATS: Found cigarette at ${log.timestamp}, sessionId=${log.sessionId}, sessionStart=$sessionStart, undone=$isUndone")
                        !isUndone
                    } else {
                        false
                    }
                }
                
                Log.d(TAG, "🚬 CIGARETTE_STATS: ${smoker.name} - sessionLogs=${sessionLogs.size}, cigarettes=$cigarettes")
                Log.d(TAG, "🚬 CIGARETTE_STATS: Session time range: $sessionStart to $now")
                
                // Extra debug: List all cigarettes for this smoker
                if (cigarettes == 0 && sessionLogs.any { it.type == ActivityType.CIGARETTE }) {
                    Log.d(TAG, "🚬 CIGARETTE_STATS: WARNING - Found cigarette logs but count is 0!")
                    sessionLogs.filter { it.type == ActivityType.CIGARETTE }.forEach { log ->
                        Log.d(TAG, "🚬 CIGARETTE_STATS:   Cigarette log: timestamp=${log.timestamp}, sessionId=${log.sessionId}")
                    }
                }

                // Check if this smoker should get carried-over bowls
                val continueBowlSmokerId = sessionStatsVM.getContinueBowlSmokerId()
                Log.d(TAG, "🔍🔍 Checking smoker ${smoker.name} (ID: ${smoker.smokerId}) vs continue ID: $continueBowlSmokerId")
                val isThisSmokerContinuing = isInContinueMode && smoker.smokerId == continueBowlSmokerId
                val adjustedBowls = if (isThisSmokerContinuing) {
                    // Add carried-over bowls to the specific smoker who continued
                    Log.d(TAG, "🔍🎯 Continue mode MATCH: Adding ${carriedOverBowls} carried bowls to ${smoker.name} (had $bowls session bowls)")
                    bowls + carriedOverBowls
                } else {
                    Log.d(TAG, "🔍❌ No match for ${smoker.name}: continue=${isInContinueMode}, match=${smoker.smokerId == continueBowlSmokerId}")
                    bowls
                }
                
                // Calculate custom activity stats for this smoker
                val customActivityStats = mutableMapOf<String, CustomActivityStat>()
                val customLogs = sessionLogs.filter { log ->
                    if (log.customActivityId.isNullOrEmpty()) {
                        false
                    } else {
                        // UNDO FIX: Filter out recently undone custom activities
                        val activityKey = "CUSTOM_${log.customActivityId}:${log.timestamp}"
                        val isRecentlyUndone = recentlyUndoneActivities.contains(activityKey)
                        if (isRecentlyUndone) {
                            Log.d(TAG, "🌟 UNDO FIX: Filtering out recently undone custom activity from stats: $activityKey")
                        }
                        !isRecentlyUndone
                    }
                }
                
                Log.d(TAG, "🌟 CUSTOM_ACTIVITY: Found ${customLogs.size} custom activities for ${smoker.name}")
                customLogs.forEach { log ->
                    Log.d(TAG, "🌟 CUSTOM_ACTIVITY:   - ${log.customActivityName} (id: ${log.customActivityId}) at ${log.timestamp}")
                }
                
                // Group custom activities by their ID
                val customByType = customLogs.groupBy { it.customActivityId }
                customByType.forEach { (customId, logs) ->
                    if (customId != null && logs.isNotEmpty()) {
                        val activityName = logs.firstOrNull()?.customActivityName ?: "Custom"
                        val sortedLogs = logs.sortedBy { it.timestamp }
                        
                        // Calculate gaps for this custom activity type
                        val gaps = mutableListOf<Long>()
                        if (sortedLogs.size >= 2) {
                            for (i in 1 until sortedLogs.size) {
                                gaps.add(sortedLogs[i].timestamp - sortedLogs[i - 1].timestamp)
                            }
                        }
                        
                        val avgGap = if (gaps.isNotEmpty()) gaps.average().toLong() else 0L
                        val longestGap = gaps.maxOrNull() ?: 0L
                        val shortestGap = gaps.minOrNull() ?: 0L
                        val lastGap = gaps.lastOrNull() ?: 0L
                        val lastTime = sortedLogs.lastOrNull()?.timestamp ?: 0L
                        
                        customActivityStats[customId] = CustomActivityStat(
                            activityName = activityName,
                            total = logs.size,
                            avgGapMs = avgGap,
                            longestGapMs = longestGap,
                            shortestGapMs = shortestGap,
                            lastGapMs = lastGap,
                            lastActivityTime = lastTime
                        )
                        
                        Log.d(TAG, "🌟 CUSTOM_ACTIVITY: Stats for ${smoker.name} - $activityName:")
                        Log.d(TAG, "🌟 CUSTOM_ACTIVITY:   Total: ${logs.size}, LastTime: $lastTime")
                        Log.d(TAG, "🌟 CUSTOM_ACTIVITY:   Gaps - Avg: ${avgGap}ms, Last: ${lastGap}ms")
                        
                        // Update group stats for this custom activity
                        if (!customActivityGroupStats.containsKey(customId)) {
                            customActivityGroupStats[customId] = CustomActivityGroupStat(
                                activityName = activityName,
                                total = 0,
                                lastSmokerName = null,
                                sinceLastMs = 0L
                            )
                        }
                        
                        val currentGroupStat = customActivityGroupStats[customId]!!
                        val newTotal = currentGroupStat.total + logs.size
                        
                        // Check if this smoker has the most recent of this custom activity
                        val existingLastTime = if (currentGroupStat.sinceLastMs > 0) {
                            now - currentGroupStat.sinceLastMs
                        } else {
                            0L
                        }
                        
                        if (lastTime > existingLastTime) {
                            customActivityGroupStats[customId] = currentGroupStat.copy(
                                total = newTotal,
                                lastSmokerName = smoker.name,
                                sinceLastMs = now - lastTime
                            )
                            // Track the timestamp for timer updates
                            lastCustomActivityTimestamps[customId] = lastTime
                            Log.d(TAG, "⏰ CUSTOM_TIMER: Tracked timestamp for $activityName (ID: $customId): $lastTime")
                        } else {
                            customActivityGroupStats[customId] = currentGroupStat.copy(
                                total = newTotal
                            )
                        }
                    }
                }
                
                val hasRegularActivities = cones > 0 || joints > 0 || adjustedBowls > 0 || cigarettes > 0
                val hasCustomActivities = customActivityStats.isNotEmpty()
                
                if (hasRegularActivities || hasCustomActivities) {
                    if (hasRegularActivities) {
                        totalCones += cones
                        totalJoints += joints
                        totalBowls += bowls  // Still sum raw bowls for group total
                        totalCigarettes += cigarettes
                        Log.d(TAG, "🚬 CIGARETTE_STATS: Added $cigarettes cigarettes to total, now totalCigarettes=$totalCigarettes")
                    }

                    // Calculate gaps for each activity type (exclude custom activities from regular types)
                    val regularLogs = sessionLogs.filter { it.customActivityId.isNullOrEmpty() }
                    
                    // Debug logging for cigarette stats investigation
                    Log.d(TAG, "🚬📊 FILTER DEBUG: sessionLogs total = ${sessionLogs.size}")
                    Log.d(TAG, "🚬📊 FILTER DEBUG: regularLogs total = ${regularLogs.size}")
                    val cigarettesInSession = sessionLogs.filter { it.type == ActivityType.CIGARETTE }
                    val cigarettesInRegular = regularLogs.filter { it.type == ActivityType.CIGARETTE }
                    Log.d(TAG, "🚬📊 FILTER DEBUG: cigarettes in sessionLogs = ${cigarettesInSession.size}")
                    Log.d(TAG, "🚬📊 FILTER DEBUG: cigarettes in regularLogs = ${cigarettesInRegular.size}")
                    cigarettesInSession.forEach { cig ->
                        Log.d(TAG, "🚬📊 FILTER DEBUG: Cigarette customActivityId='${cig.customActivityId}', isNull=${cig.customActivityId == null}, isEmpty=${cig.customActivityId?.isEmpty()}")
                    }
                    
                    val coneGaps = calculateGapsForType(regularLogs, ActivityType.CONE)
                    val jointGaps = calculateGapsForType(regularLogs, ActivityType.JOINT)
                    val bowlGaps = calculateGapsForType(regularLogs, ActivityType.BOWL)
                    val cigaretteGaps = calculateGapsForType(regularLogs, ActivityType.CIGARETTE)

                    // Get last timestamps for time calculations
                    val lastConeLog = regularLogs.filter { it.type == ActivityType.CONE }.maxByOrNull { it.timestamp }
                    val lastJointLog = regularLogs.filter { it.type == ActivityType.JOINT }.maxByOrNull { it.timestamp }
                    val lastBowlLog = regularLogs.filter { it.type == ActivityType.BOWL }.maxByOrNull { it.timestamp }
                    val lastCigaretteLog = regularLogs.filter { it.type == ActivityType.CIGARETTE }.maxByOrNull { it.timestamp }
                    
                    // Debug logging for cigarette timestamps
                    Log.d(TAG, "🚬📊 TIMESTAMP DEBUG: lastCigaretteLog = ${lastCigaretteLog?.timestamp}, time since = ${if (lastCigaretteLog != null) now - lastCigaretteLog.timestamp else 0}ms")
                    
                    val perSmokerStat = PerSmokerStats(
                        smokerName = smoker.name,
                        totalCones = cones,
                        totalJoints = joints,
                        totalBowls = adjustedBowls,  // Use adjusted bowls for display
                        totalCigarettes = cigarettes,
                        avgGapMs = coneGaps.avg,
                        longestGapMs = coneGaps.longest,
                        shortestGapMs = coneGaps.shortest,
                        lastGapMs = coneGaps.last,
                        lastConeTime = lastConeLog?.timestamp ?: 0L,
                        avgJointGapMs = jointGaps.avg,
                        longestJointGapMs = jointGaps.longest,
                        shortestJointGapMs = jointGaps.shortest,
                        lastJointGapMs = jointGaps.last,
                        lastJointTime = lastJointLog?.timestamp ?: 0L,
                        avgBowlGapMs = bowlGaps.avg,
                        longestBowlGapMs = bowlGaps.longest,
                        shortestBowlGapMs = bowlGaps.shortest,
                        lastBowlGapMs = bowlGaps.last,
                        lastBowlTime = lastBowlLog?.timestamp ?: 0L,
                        avgCigaretteGapMs = cigaretteGaps.avg,
                        longestCigaretteGapMs = cigaretteGaps.longest,
                        shortestCigaretteGapMs = cigaretteGaps.shortest,
                        lastCigaretteGapMs = cigaretteGaps.last,
                        lastCigaretteTime = lastCigaretteLog?.timestamp ?: 0L,
                        lastActivityTime = sessionLogs.maxByOrNull { it.timestamp }?.timestamp ?: 0L,
                        customActivityStats = customActivityStats
                    )

                    perSmokerList.add(perSmokerStat)
                    
                    Log.d(TAG, "🔍📊 ${smoker.name} stats: C=$cones, J=$joints, B=$adjustedBowls (session=$bowls, carried=$carriedOverBowls), Cig=$cigarettes")
                    Log.d(TAG, "🚬 CIGARETTE_STATS: Creating PerSmokerStat for ${smoker.name} with totalCigarettes=$cigarettes")
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
            
            val sinceLastCigaretteMs = if (cigaretteLogs.isNotEmpty()) {
                val lastCigaretteTimestamp = cigaretteLogs.maxOf { it.timestamp }
                now - lastCigaretteTimestamp
            } else {
                0L
            }

            Log.d(TAG, "🔍🔴 DEBUG: Creating GroupStats with:")
            Log.d(TAG, "🔍🔴   - lastConeSmokerName = $lastConeSmokerName")
            Log.d(TAG, "🔍🔴   - lastJointSmokerName = $lastJointSmokerName")
            Log.d(TAG, "🔍🔴   - lastBowlSmokerName = $lastBowlSmokerName")
            Log.d(TAG, "🔍🔴   - lastCigaretteSmokerName = $lastCigaretteSmokerName")
            Log.d(TAG, "🔍🔴   - sinceLastConeMs = $sinceLastConeMs")
            Log.d(TAG, "🔍🔴   - sinceLastJointMs = $sinceLastJointMs")
            Log.d(TAG, "🔍🔴   - sinceLastBowlMs = $sinceLastBowlMs")
            Log.d(TAG, "🔍🔴   - sinceLastCigaretteMs = $sinceLastCigaretteMs")
            
            // Adjust stats based on continue mode
            var groupTotalCones = if (isInContinueMode) {
                // In continue mode, use carried-over cones plus new ones
                carriedOverCones + totalCones
            } else {
                totalCones  // Normal mode - just use per-smoker sum
            }
            
            var adjustedTotalBowls = if (isInContinueMode) {
                // In continue mode, add carried-over bowls to session bowls
                carriedOverBowls + totalBowls
            } else {
                totalBowls
            }
            
            var adjustedTotalRounds = if (isInContinueMode) {
                // In continue mode, use carried-over rounds plus new ones
                carriedOverRounds + actualRounds
            } else {
                actualRounds
            }
            
            var adjustedConesSinceLastBowl = if (isInContinueMode) {
                // In continue mode, show carried-over cones plus new ones since continue started
                carriedOverCones + totalCones
            } else {
                conesSinceLastBowl
            }
            
            // If we have bowls in this session, check for associated cones
            if (bowlLogs.isNotEmpty()) {
                val bowlsWithAssociatedCones = bowlLogs.filter { (it.associatedConesCount ?: 0) > 0 }
                if (bowlsWithAssociatedCones.isNotEmpty()) {
                    val totalAssociatedCones = bowlsWithAssociatedCones.sumOf { it.associatedConesCount ?: 0 }
                    Log.d(TAG, "🔍🍶 Found ${bowlsWithAssociatedCones.size} bowls with total associated cones: $totalAssociatedCones")
                    
                    // Add associated cones ONLY to group total, not per-smoker
                    groupTotalCones += totalAssociatedCones
                    Log.d(TAG, "🔍🌿 Group total cones (with carried-over): $groupTotalCones")
                    Log.d(TAG, "🔍🌿 Per-smoker total cones (without carried-over): $totalCones")
                    
                    // Also add carried-over rounds if we have a bowl with associated cones
                    if (carriedOverRounds > 0) {
                        adjustedTotalRounds += carriedOverRounds
                        Log.d(TAG, "🔍🌁 Added carried-over rounds: $carriedOverRounds")
                    }
                }
            }
            
            val groupStats = GroupStats(
                totalCones = groupTotalCones,  // This includes carried-over cones
                totalJoints = totalJoints,
                totalBowls = adjustedTotalBowls,  // This includes carried-over bowls
                totalCigarettes = totalCigarettes,
                longestGapMs = longestConeGapMs,  // This is specifically for cones
                shortestGapMs = shortestConeGapMs,  // This is specifically for cones
                sinceLastGapMs = sinceLastConeMs,
                sinceLastJointMs = sinceLastJointMs,
                sinceLastBowlMs = sinceLastBowlMs,
                sinceLastCigaretteMs = sinceLastCigaretteMs,
                totalRounds = adjustedTotalRounds,
                hitsInCurrentRound = hitsThisRound,
                participantCount = perSmokerList.size,
                lastConeSmokerName = lastConeSmokerName,
                lastJointSmokerName = lastJointSmokerName,
                lastBowlSmokerName = lastBowlSmokerName,
                lastCigaretteSmokerName = lastCigaretteSmokerName,
                conesSinceLastBowl = adjustedConesSinceLastBowl,
                lastGapMs = lastGapMs,  // Gap between last two activities of ANY type
                previousGapMs = previousGapMs,  // Gap before that
                customActivityGroupStats = customActivityGroupStats
            )

            withContext(Dispatchers.Main) {
                Log.d(TAG, "🔍 === FINAL STATS SUMMARY ===")
                Log.d(TAG, "🔍 Total activities: ${allSessionActivities.size}")
                Log.d(TAG, "🔍🔴 Last smoker names - Cone: $lastConeSmokerName, Joint: $lastJointSmokerName, Bowl: $lastBowlSmokerName")
                Log.d(TAG, "🔍 Group total cones: $groupTotalCones (per-smoker sum: $totalCones)")
                Log.d(TAG, "🔍 Total bowls: $adjustedTotalBowls (session: $totalBowls, carried: $carriedOverBowls)")
                Log.d(TAG, "🔍 Total rounds: $adjustedTotalRounds")
                Log.d(TAG, "🔍 Cones since last bowl: $adjustedConesSinceLastBowl")
                Log.d(TAG, "🔍 Carried-over values - Cones: $carriedOverCones, Rounds: $carriedOverRounds, Bowls: $carriedOverBowls")
                Log.d(TAG, "🔍 Continue mode active: $isInContinueMode")
                Log.d(TAG, "🔍 Last gap (any type): ${lastGapMs?.let { "${it / 1000}s" } ?: "N/A"}")
                Log.d(TAG, "🔍 Previous gap (any type): ${previousGapMs?.let { "${it / 1000}s" } ?: "N/A"}")
                Log.d(TAG, "🔍 Longest cone gap: ${longestConeGapMs / 1000}s")
                Log.d(TAG, "🔍 Shortest cone gap: ${shortestConeGapMs / 1000}s")
                Log.d(TAG, "🔍 ============================")
                
                // Log what we're sending to ViewModel
                Log.d(TAG, "🔍📤 Sending to ViewModel:")
                Log.d(TAG, "🔍📤 Per-smoker count: ${perSmokerList.size}")
                perSmokerList.forEach { stat ->
                    Log.d(TAG, "🔍📤   ${stat.smokerName}: C=${stat.totalCones}, J=${stat.totalJoints}, B=${stat.totalBowls}, Cig=${stat.totalCigarettes}")
                    Log.d(TAG, "🚬 CIGARETTE_STATS: Sending ${stat.smokerName} with ${stat.totalCigarettes} cigarettes to ViewModel")
                }
                Log.d(TAG, "🔍📤 Group: C=${groupStats.totalCones}, J=${groupStats.totalJoints}, B=${groupStats.totalBowls}, Cig=${groupStats.totalCigarettes}, R=${groupStats.totalRounds}")
                Log.d(TAG, "🚬 CIGARETTE_STATS: Sending GroupStats with totalCigarettes=${groupStats.totalCigarettes}")
                
                // Log custom activity group stats
                Log.d(TAG, "🌟 CUSTOM_ACTIVITY: Group stats for custom activities:")
                customActivityGroupStats.forEach { (id, stat) ->
                    Log.d(TAG, "🌟 CUSTOM_ACTIVITY:   ${stat.activityName}: Total=${stat.total}, LastBy=${stat.lastSmokerName}, SinceLast=${stat.sinceLastMs}ms")
                }
                
                // Log per-smoker custom stats
                Log.d(TAG, "🌟 CUSTOM_ACTIVITY: Per-smoker custom activity stats:")
                perSmokerList.forEach { smokerStat ->
                    if (smokerStat.customActivityStats.isNotEmpty()) {
                        Log.d(TAG, "🌟 CUSTOM_ACTIVITY:   ${smokerStat.smokerName} has ${smokerStat.customActivityStats.size} custom activities")
                        smokerStat.customActivityStats.forEach { (id, customStat) ->
                            Log.d(TAG, "🌟 CUSTOM_ACTIVITY:     - ${customStat.activityName}: ${customStat.total} total")
                        }
                    }
                }

                val smokerDisplayOrder = smokers.associate { it.name to it.displayOrder }
                sessionStatsVM.applyLocalStats(
                    perSmokerList,
                    groupStats,
                    sessionStart,
                    lastConeSmokerName,
                    conesSinceLastBowl,
                    smokerDisplayOrder
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

        // CRITICAL FIX: Use ViewModel's currentSessionId for proper session association
        val currentSessionId = sessionStatsVM.currentSessionId.value

        Log.d(TAG, "🎯 Creating activity with sessionId: $currentSessionId (from ViewModel)")

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
                
                // Update active session summary
                withContext(Dispatchers.Main) {
                    updateActiveSessionSummary()
                }

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
                ActivityType.CUSTOM -> {
                    // Track custom activity timestamps using the last selected custom activity ID
                    lastSelectedCustomActivityId?.let { customId ->
                        lastCustomActivityTimestamps[customId] = now
                        Log.d(TAG, "⏰ CUSTOM_TIMER: Updated timestamp for custom activity (ID: $customId) to $now")
                    }
                }
                ActivityType.CIGARETTE -> { /* Cigarettes don't update core timestamps */ }
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

        // STASH TRACKING WITH CUSTOM ACTIVITY DEBUG
        Log.d("CUSTOM_STASH_DEBUG", "🟡 === STASH TRACKING CHECK (Location 2) ===")
        Log.d("CUSTOM_STASH_DEBUG", "🟡 Activity type: $type")
        Log.d("CUSTOM_STASH_DEBUG", "🟡 Is core activity: ${type in listOf(ActivityType.JOINT, ActivityType.CONE, ActivityType.BOWL)}")
        Log.d("CUSTOM_STASH_DEBUG", "🟡 Stash available: ${stashViewModel.currentStash.value != null}")
        
        if (stashViewModel.currentStash.value != null) {
            // IMPORTANT: Only process core activities for stash system
            if (type in listOf(ActivityType.JOINT, ActivityType.CONE, ActivityType.BOWL)) {
                Log.d("CUSTOM_STASH_DEBUG", "🟡 ✅ Processing CORE activity for stash")
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
            } else {
                Log.d("CUSTOM_STASH_DEBUG", "🟡 ❌ SKIPPING CUSTOM activity - not processing for stash")
            }
        } else {
            Log.d("CUSTOM_STASH_DEBUG", "🟡 ❌ No stash available - skipping stash tracking")
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

    private suspend fun hasBowlsLoggedSince(startTime: Long, endTime: Long): Boolean {
        if (startTime <= 0L || endTime <= startTime) return false
        val totalBowls = repo.getTotalBowlsInTimeRange(startTime, endTime)
        Log.d(TAG, "🎯 BOWL CHECK: Found $totalBowls bowls between $startTime and $endTime")
        return totalBowls > 0
    }

    private fun maybeShowConePrompt(
        type: ActivityType,
        capturedSmoker: Smoker,
        now: Long,
        queueAction: () -> Unit
    ): Boolean {
        if (type != ActivityType.CONE) return false
        if (!sessionActive || sessionStart <= 0L) return false
        if (firstConePromptShown) return false
        if (isInFirstConeDialog) return true

        val bowlsRecorded = sessionStatsVM.groupStats.value?.totalBowls ?: 0
        if (bowlsRecorded > 0) {
            return false
        }

        isInFirstConeDialog = true
        lifecycleScope.launch {
            try {
                val bowlsLogged = hasBowlsLoggedSince(sessionStart, now)
                if (bowlsLogged) {
                    withContext(Dispatchers.Main) {
                        isInFirstConeDialog = false
                        queueAction()
                    }
                } else {
                    val finalStashSource = prepareStashSourceForActivity(ActivityType.CONE, capturedSmoker)
                    firstConePromptShown = true
                    withContext(Dispatchers.Main) {
                        showThemedConfirmationDialog(capturedSmoker, finalStashSource, now)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "🎯 BOWL CHECK ERROR: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    isInFirstConeDialog = false
                    queueAction()
                }
            }
        }
        return true
    }

    private fun logHitSafe(type: ActivityType) {
        val timestamp = System.currentTimeMillis()
        Log.d(TAG, "📱 === BUTTON PRESS DETECTED ===")
        Log.d(TAG, "📱 logHitSafe called - type: $type at timestamp: $timestamp")
        Log.d(TAG, "📱 Session state - active: $sessionActive, start: $sessionStart")
        Log.d(TAG, "📱 Current smokers count: ${smokers.size}")
        Log.d(TAG, "📱 Thread: ${Thread.currentThread().name}")

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

        // NO MORE THROTTLING - process every click
        val now = System.currentTimeMillis()
        
        val selectedPosition = binding.spinnerSmoker.selectedItemPosition
        val organizedSmokers = organizeSmokers().flatMap { it.smokers }
        val capturedSmoker = organizedSmokers.getOrNull(selectedPosition)
        
        if (capturedSmoker == null) {
            Toast.makeText(this, "Please select a valid smoker!", Toast.LENGTH_SHORT).show()
            return
        }
        
        val queueAction = {
            synchronized(queueLock) {
                val queuedActivity = QueuedActivity(type, now, capturedSmoker, null)
                activityQueue.add(queuedActivity)
                Log.d(TAG, "📱 Added to queue: $type for ${capturedSmoker.name}, queue size: ${activityQueue.size}")
            }

            updateOptimisticUI(capturedSmoker.name, type)

            if (isAutoMode && smokers.size > 1 && type != ActivityType.BOWL) {
                Log.d(TAG, "📱 Immediately rotating to next smoker")
                justRotatedFromUI = true
                moveToNextActiveSmoker()
                handler.postDelayed({
                    justRotatedFromUI = false
                }, 500)
            }

            processActivityQueue()
        }

        if (maybeShowConePrompt(type, capturedSmoker, now, queueAction)) {
            return
        }

        queueAction()
    }
    
    private fun updateOptimisticUI(
        smokerName: String,
        type: ActivityType,
        customActivity: CustomActivity? = null
    ) {
        val smokerCounts = optimisticCounts.getOrPut(smokerName) { mutableMapOf() }
        smokerCounts[type] = (smokerCounts[type] ?: 0) + 1

        val currentStats = sessionStatsVM.perSmokerStats.value ?: emptyList()
        var statsUpdated = false
        val now = System.currentTimeMillis()

        val updatedStats = currentStats.map { stat ->
            if (stat.smokerName == smokerName) {
                statsUpdated = true
                when (type) {
                    ActivityType.CONE -> stat.copy(totalCones = stat.totalCones + 1)
                    ActivityType.JOINT -> stat.copy(totalJoints = stat.totalJoints + 1)
                    ActivityType.BOWL -> stat.copy(totalBowls = stat.totalBowls + 1)
                    ActivityType.CIGARETTE -> stat.copy(
                        totalCigarettes = stat.totalCigarettes + 1,
                        lastCigaretteTime = now,
                        lastCigaretteGapMs = if (stat.lastCigaretteTime > 0) now - stat.lastCigaretteTime else 0L
                    )
                    ActivityType.CUSTOM -> {
                        val details = customActivity ?: run {
                            Log.w(TAG, "📊 OPTIMISTIC UI: Missing custom activity details for $smokerName")
                            return@map stat
                        }
                        val updatedCustomStats = stat.customActivityStats.toMutableMap()
                        val existing = updatedCustomStats[details.id]
                        val newEntry = existing?.copy(
                            total = existing.total + 1,
                            lastActivityTime = now,
                            lastGapMs = 0L
                        ) ?: CustomActivityStat(
                            activityName = details.name,
                            total = 1,
                            lastActivityTime = now
                        )
                        updatedCustomStats[details.id] = newEntry
                        stat.copy(customActivityStats = updatedCustomStats)
                    }
                    else -> stat
                }
            } else {
                stat
            }
        }.toMutableList()

        if (!statsUpdated) {
            val newStat = when (type) {
                ActivityType.CONE -> PerSmokerStats(smokerName = smokerName, totalCones = 1)
                ActivityType.JOINT -> PerSmokerStats(smokerName = smokerName, totalJoints = 1)
                ActivityType.BOWL -> PerSmokerStats(smokerName = smokerName, totalBowls = 1)
                ActivityType.CIGARETTE -> PerSmokerStats(
                    smokerName = smokerName, 
                    totalCigarettes = 1,
                    lastCigaretteTime = now
                )
                ActivityType.CUSTOM -> {
                    val details = customActivity
                    val customStats = if (details != null) {
                        mapOf(details.id to CustomActivityStat(
                            activityName = details.name,
                            total = 1,
                            lastActivityTime = now
                        ))
                    } else emptyMap()
                    PerSmokerStats(smokerName = smokerName, customActivityStats = customStats)
                }
                else -> PerSmokerStats(smokerName = smokerName)
            }
            updatedStats.add(newStat)
        }

        val currentGroup = sessionStatsVM.groupStats.value ?: GroupStats()
        var updatedGroup = when (type) {
            ActivityType.CONE -> currentGroup.copy(totalCones = currentGroup.totalCones + 1)
            ActivityType.JOINT -> currentGroup.copy(totalJoints = currentGroup.totalJoints + 1)
            ActivityType.BOWL -> currentGroup.copy(totalBowls = currentGroup.totalBowls + 1)
            ActivityType.CIGARETTE -> currentGroup.copy(totalCigarettes = currentGroup.totalCigarettes + 1)
            else -> currentGroup
        }

        if (type == ActivityType.CUSTOM) {
            val details = customActivity
            if (details != null) {
                val newCustomMap = currentGroup.customActivityGroupStats.toMutableMap()
                val existing = newCustomMap[details.id]
                val newEntry = existing?.copy(
                    total = existing.total + 1,
                    lastSmokerName = smokerName,
                    sinceLastMs = 0L
                ) ?: CustomActivityGroupStat(
                    activityName = details.name,
                    total = 1,
                    lastSmokerName = smokerName,
                    sinceLastMs = 0L
                )
                newCustomMap[details.id] = newEntry
                updatedGroup = updatedGroup.copy(customActivityGroupStats = newCustomMap)
            } else {
                Log.w(TAG, "📊 OPTIMISTIC UI: Skipping group custom update due to missing details")
            }
        }

        Log.d(TAG, "📊 OPTIMISTIC UI: Updating stats for $smokerName - $type")
        sessionStatsVM._perSmokerStats.postValue(updatedStats)
        sessionStatsVM._groupStats.postValue(updatedGroup)

        runOnUiThread {
            sessionStatsVM._perSmokerStats.value = updatedStats
            sessionStatsVM._groupStats.value = updatedGroup
        }
    }

    private fun updateOptimisticUIBatch(activities: List<QueuedActivity>) {
        if (activities.isEmpty()) return
        activities.forEach { queued ->
            updateOptimisticUI(
                smokerName = queued.smoker.name,
                type = queued.type,
                customActivity = queued.customActivity
            )
        }
    }
    
    private fun processActivityQueue() {
        if (isProcessingQueue) {
            Log.d(TAG, "📱 Already processing queue, skipping")
            return
        }
        
        lifecycleScope.launch {
            isProcessingQueue = true
            isOptimisticMode = true // Prevent DB overwrites while processing
            
            try {
                val activeJobs = mutableListOf<Deferred<*>>()
                
                // Process activities continuously as they come in
                while (true) {
                    // Get next activity from queue (or multiple if available)
                    val activitiesToProcess = synchronized(queueLock) {
                        if (activityQueue.isEmpty()) {
                            null
                        } else {
                            // Take up to 10 activities at once for efficiency
                            val batch = activityQueue.take(minOf(10, activityQueue.size)).toList()
                            activityQueue.removeAll(batch)
                            batch
                        }
                    }
                    
                    if (activitiesToProcess.isNullOrEmpty()) {
                        // No more activities, but wait for any still processing
                        if (activeJobs.isNotEmpty()) {
                            Log.d(TAG, "📱 Waiting for ${activeJobs.size} activities to finish processing")
                            activeJobs.awaitAll()
                            activeJobs.clear()
                        }
                        break
                    }
                    
                    Log.d(TAG, "📱 Processing ${activitiesToProcess.size} activities")
                    
                    // Launch processing for these activities
                    val newJobs = activitiesToProcess.map { activity ->
                        async {
                            Log.d(TAG, "📱 Processing: ${activity.type} for ${activity.smoker.name}")
                            processQueuedActivityWithoutAutoAdvance(activity)
                        }
                    }
                    activeJobs.addAll(newJobs)
                    
                    // Don't wait here - continue checking for more activities
                    // But also don't let too many accumulate
                    if (activeJobs.size > 20) {
                        // Wait for some to complete before continuing
                        activeJobs.awaitAll()
                        activeJobs.clear()
                    }
                }
                
                // NO auto-advance here - it already happened immediately after button press
                
                // Small delay for DB writes to complete
                delay(100)
                
                // Now refresh from database if local session
                if (currentShareCode == null) {
                    isOptimisticMode = false // Allow DB refresh now
                    Log.d(TAG, "📱 Refreshing stats after processing")
                    refreshLocalSessionStatsIfNeeded()
                }
                
            } finally {
                isProcessingQueue = false
                isOptimisticMode = false
                Log.d(TAG, "📱 Queue processing complete")
            }
        }
    }

    private suspend fun prepareStashSourceForActivity(
        activityType: ActivityType,
        smoker: Smoker
    ): StashSource {
        val currentStash = stashViewModel.currentStash.value
        val ratios = stashViewModel.ratios.value

        if (currentStash != null && ratios != null) {
            val requiredGrams = when (activityType) {
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
                    val isCurrentUser = (smoker.isCloudSmoker && smoker.cloudUserId == currentUserId) ||
                        (!smoker.isCloudSmoker && smoker.uid == currentUserId)

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
                        .firstOrNull()
                        ?.setAttributionRadioSilently(StashSource.THEIR_STASH)
                }
            }
        }

        return stashViewModel.stashSource.value ?: StashSource.MY_STASH
    }

    private suspend fun processQueuedActivityWithoutAutoAdvance(activity: QueuedActivity) {
        try {
            val finalStashSource = prepareStashSourceForActivity(activity.type, activity.smoker)
            
            // Handle custom activities differently
            if (activity.type == ActivityType.CUSTOM && activity.customActivity != null) {
                proceedWithCustomActivityLog(activity.customActivity, activity.timestamp, finalStashSource, activity.smoker)
            } else {
                // Skip the cone/bowl confirmation dialog for queued activities
                proceedWithLogHitWithSourceAndSmoker(activity.type, activity.timestamp, finalStashSource, activity.smoker)
            }
            
            // NO auto-advance here - will be done once at the end of batch
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing queued activity", e)
        }
    }
    
    // Keep the original for backwards compatibility if needed
    private suspend fun processQueuedActivity(activity: QueuedActivity) {
        processQueuedActivityWithoutAutoAdvance(activity)
        
        // Auto-advance if enabled
        if (isAutoMode && smokers.size > 1) {
            withContext(Dispatchers.Main) {
                Log.d(TAG, "📱 Auto-advancing to next smoker after activity")
                moveToNextActiveSmoker()
            }
        }
    }
    
    // ADD this new function to handle the captured smoker
    private suspend fun proceedWithLogHitWithSourceAndSmoker(
        type: ActivityType,
        timestamp: Long,
        stashSource: StashSource,
        capturedSmoker: Smoker,
        customRatio: SmokeRatio? = null
    ) {
        Log.d(TAG, "🎯 proceedWithLogHitWithSourceAndSmoker: type=$type, source=$stashSource, smoker=${capturedSmoker.name}")
        
        // Get the selected ratio if not provided
        val ratioToUse = if (customRatio == null && (type == ActivityType.BOWL || type == ActivityType.JOINT)) {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val lastSelectedRatioKey = if (type == ActivityType.BOWL) "last_bowl_ratio_id" else "last_joint_ratio_id"
            val lastSelectedRatioId = prefs.getString(lastSelectedRatioKey, null)
            
            if (lastSelectedRatioId != null) {
                val ratios = ratioManager.getRatiosForType(
                    if (type == ActivityType.BOWL) SmokeRatio.RatioType.BOWL else SmokeRatio.RatioType.JOINT
                )
                val selectedRatio = ratios.firstOrNull { it.id == lastSelectedRatioId }
                    ?: ratios.firstOrNull { it.isSelected }
                if (selectedRatio != null) {
                    Log.d(TAG, "🚬 Selected ratio: ${selectedRatio.name}, numberOfSmokes: ${selectedRatio.numberOfSmokes}, cigarettesPerSmoke: ${selectedRatio.cigarettesPerSmoke}")
                }
                selectedRatio
            } else {
                val ratios = ratioManager.getRatiosForType(
                    if (type == ActivityType.BOWL) SmokeRatio.RatioType.BOWL else SmokeRatio.RatioType.JOINT
                )
                ratios.firstOrNull { it.isSelected }
            }
        } else {
            customRatio
        }
        
        Log.d(TAG, "🎯 Using ratio: ${ratioToUse?.name ?: "none"}")

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
            logHitWithPayerAndSmoker(type, timestamp, payerStashOwnerId, capturedSmoker, ratioToUse)
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
        capturedSmoker: Smoker,
        customRatio: SmokeRatio? = null
    ) {
        Log.d(TAG, "🎯 === logHitWithPayerAndSmoker START ===")
        Log.d(TAG, "🎯 Type: $type, Time: $now, PayerStashOwnerId: '$payerStashOwnerId', Smoker: ${capturedSmoker.name}")
        Log.d(TAG, "🚬💉 ENTRY: customRatio=${customRatio?.name}, id=${customRatio?.id}, cigarettesPerSmoke=${customRatio?.cigarettesPerSmoke}")

        if (!sessionActive) {
            Log.w(TAG, "🎯 Cannot log hit - session not active")
            return
        }

        val adjustedNow = now - rewindOffset
        val stashViewModel = ViewModelProvider(this).get(StashViewModel::class.java)
        val currentStash = stashViewModel.currentStash.value
        val ratios = stashViewModel.ratios.value

        // Calculate grams based on custom ratio or default ratios
        val gramsForActivity = when {
            customRatio != null && (type == ActivityType.BOWL || type == ActivityType.JOINT) -> {
                // Use custom ratio: chopAmount divided by numberOfSmokes
                val gramsPerSmoke = customRatio.chopAmount / customRatio.numberOfSmokes
                Log.d(TAG, "🎯 Using custom ratio: ${customRatio.name} - ${gramsPerSmoke}g per smoke")
                gramsPerSmoke
            }
            else -> when (type) {
                ActivityType.CONE -> ratios?.coneGrams ?: 0.3
                ActivityType.JOINT -> ratios?.jointGrams ?: 0.5
                ActivityType.BOWL -> ratios?.bowlGrams ?: 0.2
                else -> 0.0
            }
        }

        // Calculate cigarette fraction contribution for cones with bowl ratio
        var coneCustomRatioName: String? = null
        var coneCustomRatioId: String? = null
        val cigaretteFractionContribution = if (type == ActivityType.CONE) {
            // Check if a bowl ratio is selected for cones
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val lastSelectedBowlRatioId = prefs.getString("last_bowl_ratio_id", null)
            
            val bowlRatio = if (lastSelectedBowlRatioId != null) {
                val bowlRatios = ratioManager.getRatiosForType(SmokeRatio.RatioType.BOWL)
                val ratio = bowlRatios.find { it.id == lastSelectedBowlRatioId }
                Log.d(TAG, "🚬💉 CONE: Found bowl ratio by ID '$lastSelectedBowlRatioId': ${ratio?.name}")
                ratio
            } else {
                // Fallback to currently selected bowl ratio (same as handleConeToBasedOnBowlRatio)
                val bowlRatios = ratioManager.getRatiosForType(SmokeRatio.RatioType.BOWL)
                val ratio = bowlRatios.firstOrNull { it.isSelected }
                Log.d(TAG, "🚬💉 CONE: No saved ID, using selected bowl ratio: ${ratio?.name}")
                ratio
            }
            
            if (bowlRatio != null) {
                // Store the bowl ratio info for cones
                coneCustomRatioName = "From cones via ${bowlRatio.name}"
                coneCustomRatioId = bowlRatio.id
                
                // Calculate cones per bowl and cigarettes per cone
                val conesPerBowl = if (ratios != null && ratios.coneGrams > 0) {
                    ratios.bowlGrams / ratios.coneGrams
                } else {
                    4.0 // Default 4 cones = 1 bowl
                }
                val contribution = bowlRatio.cigarettesPerSmoke / conesPerBowl
                Log.d(TAG, "🚬💉 CONE contribution calc: bowlRatio=${bowlRatio.name}, cigarettesPerSmoke=${bowlRatio.cigarettesPerSmoke}, conesPerBowl=$conesPerBowl = $contribution")
                contribution
            } else {
                Log.d(TAG, "🚬💉 CONE: No bowl ratio found, contribution=0.0")
                0.0
            }
        } else if (customRatio != null && type == ActivityType.JOINT) {
            val contribution = customRatio.cigarettesPerSmoke
            Log.d(TAG, "🚬💉 JOINT contribution calc: customRatio=${customRatio.name}, cigarettesPerSmoke=${customRatio.cigarettesPerSmoke} = $contribution")
            contribution
        } else {
            Log.d(TAG, "🚬💉 NO CONTRIBUTION: type=$type, customRatio=${customRatio?.name}")
            0.0
        }
        
        // Get the fraction before this activity (for tracking)
        val fractionBefore = if (cigaretteFractionContribution > 0) {
            ratioManager.getCigaretteFraction(capturedSmoker.smokerId)
        } else {
            0.0
        }

        // Create the activity log with the CAPTURED smoker
        val activityLog = ActivityLog(
            id = 0L,
            smokerId = capturedSmoker.smokerId,
            consumerId = capturedSmoker.smokerId,
            payerStashOwnerId = payerStashOwnerId,
            type = type,
            timestamp = adjustedNow,
            sessionId = sessionStatsVM.currentSessionId.value,
            sessionStartTime = if (sessionActive) sessionStart else null,
            gramsAtLog = gramsForActivity,
            pricePerGramAtLog = currentStash?.pricePerGram ?: 15.0,
            customRatioId = if (type == ActivityType.CONE) coneCustomRatioId else customRatio?.id,
            customRatioName = if (type == ActivityType.CONE) coneCustomRatioName else customRatio?.name,
            cigaretteFractionContribution = cigaretteFractionContribution,
            cigaretteFractionBefore = fractionBefore
        )

        // ALWAYS store in local database first
        val insertedId = withContext(Dispatchers.IO) {
            val id = repo.insert(activityLog)
            Log.d(TAG, "🎯 INSERTED activity ID $id for smoker ${capturedSmoker.name}")
            Log.d(TAG, "🚬💉 STORED: ${type.name} customRatioName=${activityLog.customRatioName}, cigaretteFractionContribution=${activityLog.cigaretteFractionContribution}")
            id
        }

        val historyActivity = activityLog.copy(id = insertedId)
        
        // Add to activity history for undo functionality
        activityHistory.add(historyActivity)
        if (activityHistory.size > 10) {
            activityHistory.removeAt(0)
        }
        Log.d(TAG, "🚬💉 HISTORY: Added ${type.name} to history with customRatioName=${historyActivity.customRatioName}, contribution=${historyActivity.cigaretteFractionContribution}")
        
        // Handle cigarette tracking if using custom ratio (joints only, not bowls)
        if (customRatio != null && type == ActivityType.JOINT) {
            handleCigaretteTracking(customRatio, type, adjustedNow, capturedSmoker, payerStashOwnerId)
            // Add a small delay to ensure database write completes
            delay(100)
            Log.d(TAG, "🚬 CIGARETTE_STATS: Cigarette tracking completed for joint")
        } else if (type == ActivityType.CONE) {
            // For cones, check if we should add cigarettes based on bowl conversion
            handleConeToBasedOnBowlRatio(adjustedNow, capturedSmoker, payerStashOwnerId)
            delay(100)
            Log.d(TAG, "🚬 CIGARETTE_STATS: Cigarette tracking completed for cone")
        }
        
        // Update active session summary
        updateActiveSessionSummary()

        // THEN handle cloud sync if in a cloud session
        if (currentShareCode != null) {
            val smokerActivityUid = if (capturedSmoker.isCloudSmoker) {
                capturedSmoker.cloudUserId!!
            } else {
                "local_${capturedSmoker.uid}"
            }
            val deviceId = getAndroidDeviceId()

            sessionSyncService.addActivityToRoom(
                shareCode = currentShareCode!!,
                smokerUid = smokerActivityUid,
                smokerName = capturedSmoker.name,
                activityType = type,
                timestamp = adjustedNow,
                deviceId = deviceId,
                cigaretteFractionContribution = cigaretteFractionContribution,
                cigaretteFractionBefore = fractionBefore,
                customRatioId = if (type == ActivityType.CONE) coneCustomRatioId else customRatio?.id,
                customRatioName = if (type == ActivityType.CONE) coneCustomRatioName else customRatio?.name
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
                    val handled = handleCloudSyncFailure(
                        error = error,
                        shareCode = currentShareCode!!,
                        smokerUid = smokerActivityUid,
                        smokerName = capturedSmoker.name,
                        activityType = type,
                        timestamp = adjustedNow,
                        deviceId = deviceId,
                        localActivityId = insertedId.toString(),
                        cigaretteFractionContribution = cigaretteFractionContribution,
                        cigaretteFractionBefore = fractionBefore,
                        customRatioId = if (type == ActivityType.CONE) coneCustomRatioId else customRatio?.id,
                        customRatioName = if (type == ActivityType.CONE) coneCustomRatioName else customRatio?.name
                    )
                    if (!handled) {
                        Log.w(TAG, "🎯 Cloud sync failure not queued (non-quota issue)")
                    }
                }
            )
        } else {
            // Local session - skip immediate refresh if processing queue
            // Stats will be refreshed after all queued activities are processed
            if (!isProcessingQueue) {
                refreshLocalSessionStatsIfNeeded()
            }
        }

        // Get the current spinner position BEFORE any changes
        val currentSpinnerPosition = binding.spinnerSmoker.selectedItemPosition

        // Handle post-hit actions with the CAPTURED smoker and position
        handlePostHitActionsWithPayerAndSmoker(
            capturedSmoker,
            currentSpinnerPosition,
            type,
            adjustedNow,
            payerStashOwnerId,
            historyActivity
        )

        Log.d(TAG, "🎯 === logHitWithPayerAndSmoker END ===")
    }

    // ADD this modified version that uses the captured smoker
    private suspend fun handlePostHitActionsWithPayerAndSmoker(
        capturedSmoker: Smoker,
        capturedPosition: Int,
        type: ActivityType,
        now: Long,
        payerStashOwnerId: String?,
        historyActivityOverride: ActivityLog? = null
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
                ActivityType.CUSTOM -> {
                    // Track custom activity timestamps using the last selected custom activity ID
                    lastSelectedCustomActivityId?.let { customId ->
                        lastCustomActivityTimestamps[customId] = now
                        Log.d(TAG, "⏰ CUSTOM_TIMER: Updated timestamp for custom activity (ID: $customId) to $now")
                    }
                }
                ActivityType.CIGARETTE -> { /* Cigarettes don't update core timestamps */ }
                ActivityType.SESSION_SUMMARY -> { /* Session summaries don't update timestamps */ }
            }

            val activityLog = historyActivityOverride ?: run {
                val cigaretteFractionContribution = when {
                    type == ActivityType.CIGARETTE -> {
                        -1.0
                    }
                    type == ActivityType.CONE -> {
                        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val lastSelectedBowlRatioId = prefs.getString("last_bowl_ratio_id", null)
                        if (lastSelectedBowlRatioId != null) {
                            val bowlRatios = ratioManager.getRatiosForType(SmokeRatio.RatioType.BOWL)
                            val bowlRatio = bowlRatios.find { it.id == lastSelectedBowlRatioId }
                            if (bowlRatio != null) {
                                val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
                                val ratios = stashViewModel.ratios.value
                                val conesPerBowl = if (ratios != null && ratios.coneGrams > 0) {
                                    ratios.bowlGrams / ratios.coneGrams
                                } else {
                                    4.0
                                }
                                bowlRatio.cigarettesPerSmoke / conesPerBowl
                            } else {
                                0.0
                            }
                        } else {
                            0.0
                        }
                    }
                    else -> 0.0
                }

                val fractionBefore = if (cigaretteFractionContribution != 0.0) {
                    ratioManager.getCigaretteFraction(capturedSmoker.smokerId)
                } else {
                    0.0
                }

                ActivityLog(
                    id = 0L,
                    smokerId = capturedSmoker.smokerId,
                    consumerId = capturedSmoker.smokerId,
                    payerStashOwnerId = payerStashOwnerId,
                    type = type,
                    timestamp = now,
                    sessionId = sessionStatsVM.currentSessionId.value,
                    sessionStartTime = if (sessionActive) sessionStart else null,
                    gramsAtLog = 0.0,
                    pricePerGramAtLog = 0.0,
                    cigaretteFractionContribution = cigaretteFractionContribution,
                    cigaretteFractionBefore = fractionBefore
                )
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

        // STASH TRACKING using CAPTURED smoker WITH CUSTOM ACTIVITY DEBUG
        val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
        Log.d("CUSTOM_STASH_DEBUG", "🟢 === STASH TRACKING CHECK (Location 3 - CAPTURED) ===")
        Log.d("CUSTOM_STASH_DEBUG", "🟢 Activity type: $type")
        Log.d("CUSTOM_STASH_DEBUG", "🟢 Is core activity: ${type in listOf(ActivityType.JOINT, ActivityType.CONE, ActivityType.BOWL)}")
        Log.d("CUSTOM_STASH_DEBUG", "🟢 Stash available: ${stashViewModel.currentStash.value != null}")
        
        if (stashViewModel.currentStash.value != null) {
            // IMPORTANT: Only process core activities for stash system
            if (type in listOf(ActivityType.JOINT, ActivityType.CONE, ActivityType.BOWL)) {
                Log.d("CUSTOM_STASH_DEBUG", "🟢 ✅ Processing CORE activity for stash")
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
            } else {
                Log.d("CUSTOM_STASH_DEBUG", "🟢 ❌ SKIPPING CUSTOM activity - not processing for stash")
            }
        } else {
            Log.d("CUSTOM_STASH_DEBUG", "🟢 ❌ No stash available - skipping stash tracking")
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
        
        // UNDO FIX: Set flag to prevent activity history rebuild during undo
        isPerformingUndo = true
        Log.d(TAG, "🔙 UNDO FIX: Setting isPerformingUndo = true")
        
        // Log detailed activity history
        Log.d(TAG, "🔙 UNDO FIX: Activity history contents:")
        activityHistory.forEachIndexed { index, activity ->
            Log.d(TAG, "🔙 UNDO FIX:   [$index] type=${activity.type}, customId=${activity.customActivityId}, customName=${activity.customActivityName}, timestamp=${activity.timestamp}")
        }

        // Check if we should undo bulk retroactive activities
        if (retroactiveActivities.isNotEmpty()) {
            Log.d(TAG, "🔙 UNDO FIX: Retroactive activities detected, calling undoBulkRetroactiveActivities")
            // Undo all retroactive activities from the last bulk add
            undoBulkRetroactiveActivities()
            return
        }

        if (activityHistory.isEmpty()) {
            Log.d(TAG, "🔙 UNDO FIX: Activity history is empty - nothing to undo")
            isPerformingUndo = false
            Toast.makeText(this, "No recent activity to undo", Toast.LENGTH_SHORT).show()
            return
        }

        val lastActivity = activityHistory.removeLastOrNull()
        if (lastActivity == null) {
            Log.d(TAG, "🔙 UNDO FIX: removeLastOrNull returned null")
            isPerformingUndo = false
            Toast.makeText(this, "No activity to undo", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "🔙 Undoing: ${lastActivity.type} for smoker ${lastActivity.smokerId} at ${lastActivity.timestamp}")
        Log.d(TAG, "🔙 UNDO FIX: Custom fields - ID: ${lastActivity.customActivityId}, Name: ${lastActivity.customActivityName}")
        Log.d(TAG, "🔙 PayerStashOwnerId: '${lastActivity.payerStashOwnerId}'")
        Log.d(TAG, "🔙 gramsAtLog: ${lastActivity.gramsAtLog}, pricePerGramAtLog: ${lastActivity.pricePerGramAtLog}")
        Log.d(TAG, "🔙 Activities remaining: ${activityHistory.size}")
        
        // UNDO FIX: Track this activity as recently undone
        val activityKey = if (lastActivity.type == ActivityType.CUSTOM && lastActivity.customActivityId != null) {
            "CUSTOM_${lastActivity.customActivityId}:${lastActivity.timestamp}"
        } else {
            "${lastActivity.type}:${lastActivity.timestamp}"
        }
        recentlyUndoneActivities.add(activityKey)
        Log.d(TAG, "🔙 UNDO FIX: Added to recently undone: $activityKey")

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
                        if (lastActivity.type == ActivityType.CUSTOM && !lastActivity.customActivityId.isNullOrEmpty()) {
                            goalService.reverseGoalProgressForSelectedActivity(
                                activityType = lastActivity.type,
                                customActivityId = lastActivity.customActivityId,
                                customActivityName = lastActivity.customActivityName,
                                sessionShareCode = sessionShareCode,
                                currentSmokerName = smoker.name
                            )
                        } else {
                            // Always call the selected-activity path first so single-activity goals roll back
                            goalService.reverseGoalProgressForSelectedActivity(
                                activityType = lastActivity.type,
                                customActivityId = lastActivity.customActivityId,
                                customActivityName = lastActivity.customActivityName,
                                sessionShareCode = sessionShareCode,
                                currentSmokerName = smoker.name
                            )

                            // Legacy multi-activity goals still use the aggregate reversal
                            goalService.reverseGoalProgressForActivity(
                                activityType = lastActivity.type,
                                sessionShareCode = sessionShareCode,
                                smokerName = smoker.name
                            )
                        }
                        Log.d(TAG, "🔙🎯 Goal progress reversed successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "🔙🎯 Error reversing goal progress: ${e.message}", e)
                    }
                } else {
                    Log.w(TAG, "🔙🎯 GoalService not initialized, skipping goal reversal")
                }
                // === END GOAL PROGRESS REVERSAL ===

                // === CIGARETTE FRACTION REVERSAL ===
                // Fetch the actual activity from database to get fraction fields
                // (in-memory activityHistory may have old objects without these fields)
                val activityWithFractions = if (lastActivity.id > 0) {
                    repo.getActivityById(lastActivity.id) ?: lastActivity
                } else {
                    lastActivity
                }
                
                // Debug: Log the actual values
                Log.d(TAG, "🔙🚬 DEBUG: From memory - contribution=${lastActivity.cigaretteFractionContribution}, before=${lastActivity.cigaretteFractionBefore}, customRatioName=${lastActivity.customRatioName}")
                Log.d(TAG, "🔙🚬 DEBUG: From DB - contribution=${activityWithFractions.cigaretteFractionContribution}, before=${activityWithFractions.cigaretteFractionBefore}, customRatioName=${activityWithFractions.customRatioName}")
                
                // Reverse cigarette fraction tracking for activities with custom ratios
                if (activityWithFractions.cigaretteFractionContribution != 0.0) {
                    Log.d(TAG, "🔙🚬 Reversing cigarette fraction for ${smoker.name}")
                    Log.d(TAG, "🔙🚬 Activity contribution: ${activityWithFractions.cigaretteFractionContribution}")
                    Log.d(TAG, "🔙🚬 Fraction before activity: ${activityWithFractions.cigaretteFractionBefore}")

                    if (
                        activityWithFractions.type == ActivityType.CIGARETTE &&
                        activityWithFractions.customRatioName != null &&
                        activityWithFractions.customRatioName.startsWith("From ")
                    ) {
                        val currentFraction = ratioManager.getCigaretteFraction(activityWithFractions.smokerId)
                        val newFraction = currentFraction + 1.0
                        ratioManager.saveCigaretteFraction(newFraction, activityWithFractions.smokerId)
                        Log.d(TAG, "🔙🚬 Restored +1.0 for auto-created cigarette, new fraction: $newFraction")
                    } else if (activityWithFractions.cigaretteFractionContribution > 0) {
                        val previousFraction = activityWithFractions.cigaretteFractionBefore
                        ratioManager.saveCigaretteFraction(previousFraction, activityWithFractions.smokerId)
                        Log.d(TAG, "🔙🚬 Restored fraction to $previousFraction for ${smoker.name}")

                        val totalBefore = activityWithFractions.cigaretteFractionBefore
                        val totalAfter = totalBefore + activityWithFractions.cigaretteFractionContribution
                        val generatedCigarettes = kotlin.math.max(
                            0,
                            (floor(totalAfter) - floor(totalBefore)).toInt()
                        )

                        if (generatedCigarettes > 0) {
                            Log.d(TAG, "🔙🚬 Removing $generatedCigarettes auto-generated cigarette(s)")
                            val logsAtTimestamp = repo.getLogsInTimeRange(
                                activityWithFractions.timestamp,
                                activityWithFractions.timestamp + 1
                            )
                            val candidateCigarettes = logsAtTimestamp
                                .filter { candidate ->
                                    candidate.type == ActivityType.CIGARETTE &&
                                        candidate.smokerId == activityWithFractions.smokerId &&
                                        candidate.cigaretteFractionContribution == -1.0
                                }
                                .sortedByDescending { it.id }

                            val sessionShareCode = if (sessionActive) currentShareCode else null

                            val cigarettesToRemove = candidateCigarettes.take(generatedCigarettes)

                            if (cigarettesToRemove.size < generatedCigarettes) {
                                Log.w(
                                    TAG,
                                    "🔙🚬 Expected $generatedCigarettes auto cigarette(s) but found ${cigarettesToRemove.size}"
                                )
                            }

                            cigarettesToRemove.forEach { autoCigarette ->
                                repo.delete(autoCigarette)
                                Log.d(TAG, "🔙🚬 Deleted auto-generated cigarette ID ${autoCigarette.id}")

                                if (::goalService.isInitialized) {
                                    try {
                                        goalService.reverseGoalProgressForSelectedActivity(
                                            activityType = ActivityType.CIGARETTE,
                                            customActivityId = null,
                                            customActivityName = null,
                                            sessionShareCode = sessionShareCode,
                                            currentSmokerName = smoker.name
                                        )
                                        goalService.reverseGoalProgressForActivity(
                                            activityType = ActivityType.CIGARETTE,
                                            sessionShareCode = sessionShareCode,
                                            smokerName = smoker.name
                                        )
                                    } catch (e: Exception) {
                                        Log.e(TAG, "🔙🚬 Error reversing goal progress for auto cigarette: ${e.message}", e)
                                    }
                                }
                            }
                        }
                    }
                }
                // === END CIGARETTE FRACTION REVERSAL ===

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

                // UNDO FIX: Delete from local database by finding the actual record with ID
                // The lastActivity from activityHistory has id=0, so we need to find the real one
                val allActivities = repo.getLogsInTimeRange(lastActivity.timestamp, lastActivity.timestamp + 1)
                val actualActivity = allActivities.find { 
                    it.smokerId == lastActivity.smokerId && 
                    it.type == lastActivity.type &&
                    it.timestamp == lastActivity.timestamp
                }
                if (actualActivity != null) {
                    repo.delete(actualActivity)
                    Log.d(TAG, "🔙 UNDO FIX: Deleted from local database (ID: ${actualActivity.id})")
                } else {
                    // Fallback: try to delete using the activity as-is (might work if Room matches by unique constraint)
                    repo.delete(lastActivity)
                    Log.w(TAG, "🔙 UNDO FIX: WARNING - Using fallback delete without ID")
                }

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
                                    // Apply full stats (including custom activities) immediately to avoid UI flicker
                                    applyRoomStatsWithCustom(roomData)
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
                        sessionStatsVM.decrementActivityCount(
                            smokerName = smoker.name,
                            activityType = lastActivity.type,
                            customActivityId = lastActivity.customActivityId
                        )
                    }
                }

                // Count remaining activities in session
                val remainingActivities = withContext(Dispatchers.IO) {
                    repo.getLogsInTimeRange(sessionStart, System.currentTimeMillis())
                }
                Log.d(TAG, "🔙 UNDO FIX: Remaining activities in session: ${remainingActivities.size}")
                Log.d(TAG, "🔙 UNDO FIX: Activity history size: ${activityHistory.size}")
                
                // Log details of remaining activities for debugging
                remainingActivities.forEach { activity ->
                    Log.d(TAG, "🔙 UNDO FIX: Remaining in DB - ID:${activity.id}, Type:${activity.type}, Time:${activity.timestamp}, CustomId:${activity.customActivityId}")
                }

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
                    
                    // UNDO FIX: Reset the flag after undo completes
                    isPerformingUndo = false
                    Log.d(TAG, "🔙 UNDO FIX: Setting isPerformingUndo = false")
                    
                    // UNDO FIX: Clean up old entries from recentlyUndoneActivities (keep only last 50)
                    if (recentlyUndoneActivities.size > 50) {
                        val toKeep = recentlyUndoneActivities.toList().takeLast(50)
                        recentlyUndoneActivities.clear()
                        recentlyUndoneActivities.addAll(toKeep)
                        Log.d(TAG, "🔙 UNDO FIX: Cleaned up recently undone activities, kept ${toKeep.size}")
                    }
                    
                    Log.d(TAG, "🔙 === UNDO COMPLETE ===")

                    Toast.makeText(this@MainActivity, "Activity removed, stash restored, and goals updated", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "🔙 Error undoing activity", e)
                withContext(Dispatchers.Main) {
                    // UNDO FIX: Reset flag on error too
                    isPerformingUndo = false
                    Log.d(TAG, "🔙 UNDO FIX: Setting isPerformingUndo = false (error case)")
                    Toast.makeText(this@MainActivity, "Error undoing activity", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Apply room stats to ViewModel including custom-activity per-smoker/group stats
    private fun applyRoomStatsWithCustom(updatedRoom: RoomData) {
        // Calculate gaps from activities
        val roomActivitiesRaw = updatedRoom.safeActivities()
        
        // Filter out blocked activities AND activities from previous sessions
        val blockedPrefs = getSharedPreferences("blocked_activities", Context.MODE_PRIVATE)
        val currentSessionId = sessionStatsVM.currentSessionId.value
        val roomActivities = roomActivitiesRaw.filter { activity ->
            // Check if blocked
            val activityKey = "${activity.smokerId}_${activity.type}_${activity.timestamp}"
            val isBlocked = blockedPrefs.getBoolean(activityKey, false)
            if (isBlocked) {
                Log.d(TAG, "🚫 Filtering blocked activity from cloud room stats: $activityKey")
                return@filter false
            }
            
            // Check if from current session (activity timestamp should be >= session start)
            if (currentSessionId != null && activity.timestamp < currentSessionId) {
                Log.d(TAG, "🚫 Filtering old session activity from cloud room stats: ${activity.type} at ${activity.timestamp} (before session ${currentSessionId})")
                return@filter false
            }
            
            true
        }
        
        val sortedActivities = roomActivities.sortedBy { it.timestamp }

        var lastGapMs: Long? = null
        var previousGapMs: Long? = null

        if (sortedActivities.size >= 2) {
            val lastActivity = sortedActivities[sortedActivities.size - 1]
            val secondLastActivity = sortedActivities[sortedActivities.size - 2]
            lastGapMs = lastActivity.timestamp - secondLastActivity.timestamp

            if (sortedActivities.size >= 3) {
                val thirdLastActivity = sortedActivities[sortedActivities.size - 3]
                previousGapMs = secondLastActivity.timestamp - thirdLastActivity.timestamp
            }
        }

        val roomStats = updatedRoom.safeCurrentStats()

        // Build custom activity group stats, filtering recently undone
        val customActivityGroupStats = mutableMapOf<String, CustomActivityGroupStat>()
        val currentTime = System.currentTimeMillis()
        val filteredCustomActivities = roomActivities.filter { it.type.startsWith("CUSTOM_") }.filter { activity ->
            val customId = activity.type.removePrefix("CUSTOM_")
            val activityKey = "CUSTOM_${customId}:${activity.timestamp}"
            !recentlyUndoneActivities.contains(activityKey)
        }
        val customByType = filteredCustomActivities.groupBy { it.type.removePrefix("CUSTOM_") }
        customByType.forEach { (customId, activities) ->
            if (activities.isNotEmpty()) {
                val lastActivity = activities.maxByOrNull { it.timestamp }!!
                val activityName = lastActivity.customActivityName ?: "Custom"
                customActivityGroupStats[customId] = CustomActivityGroupStat(
                    activityName = activityName,
                    total = activities.size,
                    lastSmokerName = lastActivity.smokerName,
                    sinceLastMs = currentTime - lastActivity.timestamp
                )
                // Track the timestamp for timer updates
                lastCustomActivityTimestamps[customId] = lastActivity.timestamp
                Log.d(TAG, "⏰ CUSTOM_TIMER: Tracked room timestamp for $activityName (ID: $customId): ${lastActivity.timestamp}")
            }
        }

        // Count totals from filtered activities
        val totalCones = roomActivities.count { it.type == "CONE" }
        val totalJoints = roomActivities.count { it.type == "JOINT" }
        val totalBowls = roomActivities.count { it.type == "BOWL" }
        val totalCigarettes = roomActivities.count { it.type == "CIGARETTE" }
        
        // Create GroupStats mirroring onChange path
        val groupStats = GroupStats(
            totalCones = totalCones,  // Use filtered count
            totalJoints = totalJoints,  // Use filtered count
            totalBowls = totalBowls,  // Use filtered count
            totalCigarettes = totalCigarettes,  // Use filtered count
            longestGapMs = roomStats.longestGapMs,
            shortestGapMs = roomStats.shortestGapMs,
            sinceLastGapMs = roomStats.sinceLastConeMs,
            sinceLastJointMs = roomStats.sinceLastJointMs,
            sinceLastBowlMs = roomStats.sinceLastBowlMs,
            totalRounds = if (isAutoMode) roomStats.totalRounds else (sessionStatsVM.groupStats.value?.totalRounds ?: 0),
            hitsInCurrentRound = if (isAutoMode) roomStats.hitsInCurrentRound else (sessionStatsVM.groupStats.value?.hitsInCurrentRound ?: 0),
            participantCount = roomStats.participantCount,
            lastConeSmokerName = roomStats.lastConeSmokerName,
            lastJointSmokerName = roomStats.lastJointSmokerName,
            lastBowlSmokerName = roomStats.lastBowlSmokerName,
            conesSinceLastBowl = roomStats.conesSinceLastBowl,
            lastGapMs = lastGapMs,
            previousGapMs = previousGapMs,
            customActivityGroupStats = customActivityGroupStats
        )

        // Build per-smoker stats with custom activity breakdowns
        val smokerDisplayOrder = smokers.associate { it.name to it.displayOrder }
        val perSmokerStatsWithGaps = roomStats.perSmokerStats.values.map { serverData ->
            val smokerActivities = roomActivities.filter { it.smokerName == serverData.smokerName }.sortedBy { it.timestamp }

            val coneActivities = smokerActivities.filter { it.type == "CONE" }
            val jointActivities = smokerActivities.filter { it.type == "JOINT" }
            val bowlActivities = smokerActivities.filter { it.type == "BOWL" }
            val cigaretteActivities = smokerActivities.filter { it.type == "CIGARETTE" }

            // Per-smoker custom activities (filter recently undone)
            val customActivityStats = mutableMapOf<String, CustomActivityStat>()
            val smokerCustom = smokerActivities.filter { it.type.startsWith("CUSTOM_") }.filter { activity ->
                val customId = activity.type.removePrefix("CUSTOM_")
                val activityKey = "CUSTOM_${customId}:${activity.timestamp}"
                !recentlyUndoneActivities.contains(activityKey)
            }
            val byCustom = smokerCustom.groupBy { it.type.removePrefix("CUSTOM_") }
            byCustom.forEach { (customId, activities) ->
                if (activities.isNotEmpty()) {
                    val sorted = activities.sortedBy { it.timestamp }
                    val gaps = if (sorted.size >= 2) sorted.zipWithNext { a, b -> b.timestamp - a.timestamp } else emptyList()
                    val lastTime = sorted.last().timestamp
                    val activityName = sorted.last().customActivityName ?: "Custom"
                    customActivityStats[customId] = CustomActivityStat(
                        activityName = activityName,
                        total = activities.size,
                        avgGapMs = if (gaps.isNotEmpty()) gaps.average().toLong() else 0L,
                        longestGapMs = gaps.maxOrNull() ?: 0L,
                        shortestGapMs = gaps.minOrNull() ?: 0L,
                        lastGapMs = if (gaps.isNotEmpty()) gaps.last() else 0L,
                        lastActivityTime = lastTime
                    )
                }
            }

            val lastConeGap = if (coneActivities.size >= 2) {
                val last = coneActivities.takeLast(2)
                last[1].timestamp - last[0].timestamp
            } else 0L
            val lastJointGap = if (jointActivities.size >= 2) {
                val last = jointActivities.takeLast(2)
                last[1].timestamp - last[0].timestamp
            } else 0L
            val lastBowlGap = if (bowlActivities.size >= 2) {
                val last = bowlActivities.takeLast(2)
                last[1].timestamp - last[0].timestamp
            } else 0L

            val lastConeTime = coneActivities.lastOrNull()?.timestamp ?: 0L
            val lastJointTime = jointActivities.lastOrNull()?.timestamp ?: 0L
            val lastBowlTime = bowlActivities.lastOrNull()?.timestamp ?: 0L
            val lastActivityTime = smokerActivities.lastOrNull()?.timestamp ?: 0L

            PerSmokerStats(
                smokerName = serverData.smokerName,
                totalCones = coneActivities.size,  // Use filtered count
                totalJoints = jointActivities.size,  // Use filtered count
                totalBowls = bowlActivities.size,  // Use filtered count
                totalCigarettes = cigaretteActivities.size,  // Use filtered count
                avgGapMs = serverData.avgGapMs,
                longestGapMs = serverData.longestGapMs,
                shortestGapMs = serverData.shortestGapMs,
                lastGapMs = lastConeGap,
                lastConeTime = lastConeTime,
                avgJointGapMs = serverData.avgJointGapMs,
                longestJointGapMs = serverData.longestJointGapMs,
                shortestJointGapMs = serverData.shortestJointGapMs,
                lastJointGapMs = lastJointGap,
                lastJointTime = lastJointTime,
                avgBowlGapMs = serverData.avgBowlGapMs,
                longestBowlGapMs = serverData.longestBowlGapMs,
                shortestBowlGapMs = serverData.shortestBowlGapMs,
                lastBowlGapMs = lastBowlGap,
                lastBowlTime = lastBowlTime,
                lastActivityTime = lastActivityTime,
                customActivityStats = customActivityStats
            )
        }.let { list ->
            list.sortedBy { smokerDisplayOrder[it.smokerName] ?: Int.MAX_VALUE }
        }

        sessionStatsVM.applyLocalStats(
            perSmokerStatsWithGaps,
            groupStats,
            updatedRoom.startTime,
            roomStats.lastConeSmokerName,
            roomStats.conesSinceLastBowl,
            smokerDisplayOrder
        )
    }

    // Undo all retroactive activities from the last bulk add
    private fun undoBulkRetroactiveActivities() {
        Log.d(TAG, "🔙 === BULK UNDO START ===")
        Log.d(TAG, "🔙 Undoing ${retroactiveActivities.size} retroactive activities")
        
        if (retroactiveActivities.isEmpty()) {
            // UNDO FIX: Reset flag if nothing to undo
            isPerformingUndo = false
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
                    
                    // UNDO FIX: Reset flag after bulk undo completes
                    isPerformingUndo = false
                    Log.d(TAG, "🔙 UNDO FIX: Setting isPerformingUndo = false (bulk undo complete)")
                    Log.d(TAG, "🔙 === BULK UNDO COMPLETE ===")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "🔙 Error undoing bulk activities", e)
                withContext(Dispatchers.Main) {
                    // UNDO FIX: Reset flag on error
                    isPerformingUndo = false
                    Log.d(TAG, "🔙 UNDO FIX: Setting isPerformingUndo = false (bulk undo error)")
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
        val shortest: Long = 0L,
        val last: Long = 0L
    )

    // Helper function to calculate gaps for a specific activity type
    private fun calculateGapsForType(logs: List<ActivityLog>, type: ActivityType): GapStats {
        val typeLogs = logs.filter { it.type == type }.sortedBy { it.timestamp }

        // Debug logging for cigarette stats
        if (type == ActivityType.CIGARETTE) {
            Log.d(TAG, "🚬📊 STATS: Calculating cigarette gaps - found ${typeLogs.size} cigarettes")
            typeLogs.take(5).forEachIndexed { index, log ->
                Log.d(TAG, "🚬📊 STATS:   Cigarette #${index + 1} at timestamp ${log.timestamp}")
            }
        }

        if (typeLogs.size < 2) {
            if (type == ActivityType.CIGARETTE) {
                Log.d(TAG, "🚬📊 STATS: Not enough cigarettes for gaps (need 2+, have ${typeLogs.size})")
            }
            return GapStats()
        }

        val gaps = mutableListOf<Long>()
        for (i in 1 until typeLogs.size) {
            val gap = typeLogs[i].timestamp - typeLogs[i - 1].timestamp
            gaps.add(gap)
            if (type == ActivityType.CIGARETTE && i <= 3) {  // Log first 3 gaps
                Log.d(TAG, "🚬📊 STATS:   Gap #$i: ${gap}ms (${gap/1000}s)")
            }
        }

        return if (gaps.isNotEmpty()) {
            val stats = GapStats(
                avg = gaps.average().toLong(),
                longest = gaps.maxOrNull() ?: 0L,
                shortest = gaps.minOrNull() ?: 0L,
                last = gaps.lastOrNull() ?: 0L
            )
            if (type == ActivityType.CIGARETTE) {
                Log.d(TAG, "🚬📊 STATS: Cigarette gap results - avg: ${stats.avg/1000}s, longest: ${stats.longest/1000}s, shortest: ${stats.shortest/1000}s, last: ${stats.last/1000}s")
            }
            stats
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
            showConfirmUndoDialog { undoLastActivity() }
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

        // Setup mode toggle button
        setupModeToggleButton()
    }

    private fun showConfirmUndoDialog(onConfirm: () -> Unit) {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        currentDialog = dialog

        // Root container with fade-in
        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Main card styled similar to offline dialog
        val mainCard = androidx.cardview.widget.CardView(this).apply {
            radius = 16.dpToPx(this@MainActivity).toFloat()
            cardElevation = 8.dpToPx(this@MainActivity).toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                setMargins(32.dpToPx(this@MainActivity), 0, 32.dpToPx(this@MainActivity), 0)
            }
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(this@MainActivity), 20.dpToPx(this@MainActivity), 20.dpToPx(this@MainActivity), 20.dpToPx(this@MainActivity))
            layoutParams = ViewGroup.LayoutParams(280.dpToPx(this@MainActivity), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleText = TextView(this).apply {
            text = "CONFIRM UNDO"
            textSize = 18f
            setTextColor(Color.parseColor("#98FB98"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(titleText)

        val messageText = TextView(this).apply {
            text = "Do you want to remove the last activity?"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(messageText)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2.dpToPx(this@MainActivity)).apply {
                topMargin = 4.dpToPx(this@MainActivity)
                bottomMargin = 16.dpToPx(this@MainActivity)
            }
            setBackgroundColor(Color.parseColor("#3398FB98"))
        }
        contentLayout.addView(divider)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val noButton = createThemedDialogButton("No", false, Color.WHITE) {
            animateCardSelection(dialog) {}
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, 44.dpToPx(this@MainActivity), 1f).apply {
                marginEnd = 8.dpToPx(this@MainActivity)
            }
        }
        val yesButton = createThemedDialogButton("Yes", true, Color.parseColor("#98FB98")) {
            animateCardSelection(dialog) { onConfirm() }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, 44.dpToPx(this@MainActivity), 1f).apply {
                marginStart = 8.dpToPx(this@MainActivity)
            }
        }

        buttonRow.addView(noButton)
        buttonRow.addView(yesButton)
        contentLayout.addView(buttonRow)
        mainCard.addView(contentLayout)
        rootContainer.addView(mainCard)

        // Dismiss when tapping outside
        rootContainer.setOnClickListener { v ->
            if (v == rootContainer) animateCardSelection(dialog) {}
        }

        dialog.setContentView(rootContainer)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
            setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }
        dialog.setOnDismissListener { currentDialog = null }

        rootContainer.alpha = 0f
        dialog.show()
        performManualFadeIn(rootContainer, 250L)
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
    
    private fun setupSkipButton() {
        binding.btnSkip.setOnClickListener {
            Log.d(TAG, "⏭️ Skip button clicked")
            
            // Skip to the next smoker
            moveToNextActiveSmoker()
            
            // Show feedback
            val currentSmoker = binding.spinnerSmoker.selectedItem as? Smoker
            currentSmoker?.let {
                Toast.makeText(this, "Skipped to ${it.name}", Toast.LENGTH_SHORT).show()
            }
            
            // ADD CONFETTI HERE
            confettiHelper.showMiniConfettiFromButton(binding.btnSkip)
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
        performManualFadeIn(dialogView, 1000L)  // Reduced by 50% from 2000L
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
                        
                        // Set user ID in repository for cloud sync
                        repo.setCurrentUserId(userId)
                        
                        // Sync history from cloud (runs in background)
                        Log.d(TAG, "🌐 Starting history sync from cloud...")
                        lifecycleScope.launch {
                            repo.syncHistoryFromCloud(lifecycleScope)
                            Log.d(TAG, "🌐 History sync initiated")
                            
                            // After downloading from cloud, upload any local-only data
                            delay(5000) // Wait for download to complete
                            Log.d(TAG, "🌐 Starting background upload of local data...")
                            repo.syncLocalToCloud()
                        }

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
        Log.d("MainActivity", "🔄 nextSmoker() called - smokers.size=${smokers.size}, currentIndex=$currentSmokerIndex")
        Log.d("MainActivity", "🔄 Current smokers list: ${smokers.map { "${it.name}(id:${it.smokerId},deleted:${it.isDeleted})" }}")
        
        if (smokers.size > 1) {
            val previousSmoker = smokers[currentSmokerIndex].name
            currentSmokerIndex = (currentSmokerIndex + 1) % smokers.size
            val newSmoker = smokers[currentSmokerIndex]

            Log.d("MainActivity", "🔄 Switching from $previousSmoker to ${newSmoker.name} (index: $currentSmokerIndex)")
            Log.d("MainActivity", "🔄 New smoker details: id=${newSmoker.smokerId}, isDeleted=${newSmoker.isDeleted}")

            updateSmokerDisplay()
            saveCurrentSmokerIndex()
        } else {
            Log.d("MainActivity", "🔄 Not enough smokers to rotate (count: ${smokers.size})")
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
            sessionId = sessionStatsVM.currentSessionId.value,
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
            Log.d(TAG, "🎯 INSERTED activity ID $id with sessionId: ${activityLog.sessionId}")

            // Verify it was stored correctly
            val verifyActivity = repo.getActivityById(id)
            Log.d(TAG, "🎯 VERIFICATION - stored sessionId: ${verifyActivity?.sessionId}")
            id
        }
        
        // Update active session summary
        updateActiveSessionSummary()

        // THEN handle cloud sync if in a cloud session
        if (currentShareCode != null) {
            val smokerActivityUid = if (selectedSmoker.isCloudSmoker) {
                selectedSmoker.cloudUserId!!
            } else {
                "local_${selectedSmoker.uid}"
            }

            val deviceId = getAndroidDeviceId()
            sessionSyncService.addActivityToRoom(
                shareCode = currentShareCode!!,
                smokerUid = smokerActivityUid,
                smokerName = selectedSmoker.name,
                activityType = type,
                timestamp = adjustedNow,
                deviceId = deviceId
            ).fold(
                onSuccess = {
                    Log.d(TAG, "🎯 Activity also synced to cloud room")
                },
                onFailure = { error ->
                    Log.e(TAG, "🎯 Failed to sync to room: ${error.message}")
                    val handled = handleCloudSyncFailure(
                        error = error,
                        shareCode = currentShareCode!!,
                        smokerUid = smokerActivityUid,
                        smokerName = selectedSmoker.name,
                        activityType = type,
                        timestamp = adjustedNow,
                        deviceId = deviceId,
                        localActivityId = insertedId.toString()
                    )
                    if (!handled) {
                        Log.w(TAG, "🎯 Cloud sync failure not queued (non-quota issue)")
                    }
                }
            )
        } else {
            // Local session - skip immediate refresh if processing queue
            // Stats will be refreshed after all queued activities are processed
            if (!isProcessingQueue) {
                refreshLocalSessionStatsIfNeeded()
            }
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

        // Update stash tracking WITH CUSTOM ACTIVITY DEBUG
        val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
        Log.d("CUSTOM_STASH_DEBUG", "🔴 === STASH TRACKING CHECK (Location 4) ===")
        Log.d("CUSTOM_STASH_DEBUG", "🔴 Activity type: $type")
        Log.d("CUSTOM_STASH_DEBUG", "🔴 Is core activity: ${type in listOf(ActivityType.JOINT, ActivityType.CONE, ActivityType.BOWL)}")
        Log.d("CUSTOM_STASH_DEBUG", "🔴 Stash available: ${stashViewModel.currentStash.value != null}")
        
        if (stashViewModel.currentStash.value != null) {
            // IMPORTANT: Only process core activities for stash system
            if (type in listOf(ActivityType.JOINT, ActivityType.CONE, ActivityType.BOWL)) {
                Log.d("CUSTOM_STASH_DEBUG", "🔴 ✅ Processing CORE activity for stash")
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
            } else {
                Log.d("CUSTOM_STASH_DEBUG", "🔴 ❌ SKIPPING CUSTOM activity - not processing for stash")
            }
        } else {
            Log.d("CUSTOM_STASH_DEBUG", "🔴 ❌ No stash available - skipping stash tracking")
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

        // Combine all active buttons (core + custom)
        val allActiveButtons = coreActivityButtons + customActivityButtons

        if (timersVisible) {
            binding.btnToggleTimers.text = "See Less"
            binding.timerContainer.visibility = View.VISIBLE
            binding.roundsContainer.visibility = View.VISIBLE

            // Make auto-controls visible
            binding.layoutConeAutoControls.visibility = View.VISIBLE
            binding.layoutJointAutoControls.visibility = View.VISIBLE
            binding.layoutBowlAutoControls.visibility = View.VISIBLE

            // FIX: Set height for all dynamically created buttons
            allActiveButtons.forEach { it.layoutParams.height = 96.dpToPx(this) }
            params.topMargin = -5.dpToPx(this) // Use consistent margin for proper spacing

            binding.sectionBackgroundImage.setImageResource(R.drawable.section_background_expanded)
            Log.d(TAG, "🔘 Showing all timer controls with doubled button heights")

        } else {
            binding.btnToggleTimers.text = "Advanced"
            binding.timerContainer.visibility = View.GONE
            binding.roundsContainer.visibility = View.GONE

            // Hide auto-controls
            binding.layoutConeAutoControls.visibility = View.GONE
            binding.layoutJointAutoControls.visibility = View.GONE
            binding.layoutBowlAutoControls.visibility = View.GONE

            // FIX: Set height for all dynamically created buttons
            allActiveButtons.forEach { it.layoutParams.height = 48.dpToPx(this) }
            params.topMargin = -5.dpToPx(this) // Use the smaller negative margin for collapsed state

            binding.sectionBackgroundImage.setImageResource(R.drawable.section_background_collapsed)
            Log.d(TAG, "🔘 Hiding all timer controls with normal button heights")
        }

        // Request layout update for all buttons after changing their height
        allActiveButtons.forEach { it.requestLayout() }

        buttonContainer.layoutParams = params

        val isLayoutAtBottom = prefs.getBoolean("layout_at_bottom", false)
        if (isLayoutAtBottom) {
            val chatFragment = supportFragmentManager.fragments.find { it is ChatFragment } as? ChatFragment
            chatFragment?.onExpansionStateChanged(timersVisible)
        }

        Log.d(TAG, "🔘 === TOGGLE TIMERS COMPLETE ===")
    }

    // Public method to check if button section is expanded
    fun isButtonSectionExpanded(): Boolean {
        return timersVisible
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

        val currentSessionId = sessionStatsVM.currentSessionId.value

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
        
        // Update active session summary
        updateActiveSessionSummary()

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
            pendingCustomActivity = null  // Clear pending custom activity
            Log.d(TAG, "🎯 No active session dialog dismissed for type: $type")
        }

        dialogView.alpha = 0f
        dialog.show()
        performManualFadeIn(dialogView, 1000L)
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
            // Store pending activity data before dialog dismissal clears it
            val savedPendingType = pendingActivityType
            val savedPendingCustom = pendingCustomActivity
            
            animateCardSelection(dialog) {
                // Restore pending activity data after dialog dismissal
                pendingActivityType = savedPendingType
                pendingCustomActivity = savedPendingCustom
                showCloudSessionOptions()
            }
        }
        buttonContainer.addView(cloudSessionButton)

        val localSessionButton = createImagePressButton("Local Session", false) {
            // Store pending activity data before dialog dismissal clears it
            val savedPendingType = pendingActivityType
            val savedPendingCustom = pendingCustomActivity
            
            animateCardSelection(dialog) {
                // Restore pending activity data after dialog dismissal
                pendingActivityType = savedPendingType
                pendingCustomActivity = savedPendingCustom
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

    private fun createImagePressButtonWithLongPress(
        text: String, 
        isPrimary: Boolean, 
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null
    ): View {
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

        // Variables for long press detection
        var longPressHandler: Handler? = null
        var longPressRunnable: Runnable? = null
        val longPressDelay = 1000L // 1 second for long press

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
                    
                    // Set up long press detection if handler provided
                    if (onLongClick != null) {
                        longPressHandler = Handler(Looper.getMainLooper())
                        longPressRunnable = Runnable {
                            // Long press detected
                            onLongClick()
                            // Reset visual state
                            imageView.visibility = View.GONE
                            buttonContainer.setCardBackgroundColor(originalBackgroundColor)
                            buttonText.setTextColor(originalTextColor)
                            buttonText.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                        }
                        longPressHandler?.postDelayed(longPressRunnable!!, longPressDelay)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    // Cancel long press if it hasn't fired
                    longPressRunnable?.let {
                        longPressHandler?.removeCallbacks(it)
                    }
                    
                    // Hide image, restore solid color
                    imageView.visibility = View.GONE
                    buttonContainer.setCardBackgroundColor(originalBackgroundColor)

                    // Restore original text color
                    buttonText.setTextColor(originalTextColor)
                    buttonText.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)

                    // Only trigger click if long press hasn't been triggered
                    if (longPressRunnable != null) {
                        v.performClick()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // Cancel long press
                    longPressRunnable?.let {
                        longPressHandler?.removeCallbacks(it)
                    }
                    
                    // Hide image, restore solid color
                    imageView.visibility = View.GONE
                    buttonContainer.setCardBackgroundColor(originalBackgroundColor)

                    // Restore original text color
                    buttonText.setTextColor(originalTextColor)
                    buttonText.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
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

    private fun createImagePressButtonYellow(text: String, onClick: () -> Unit): View {
        val buttonContainer = androidx.cardview.widget.CardView(this).apply {
            radius = 20.dpToPx(context).toFloat()
            cardElevation = 2.dpToPx(context).toFloat()
            setCardBackgroundColor(Color.parseColor("#FFFF66")) // Neon yellow

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
            setTextColor(Color.parseColor("#424242")) // Dark text on yellow
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
        val originalBackgroundColor = Color.parseColor("#FFFF66")
        val originalTextColor = Color.parseColor("#424242")

        // Handle touch events
        buttonContainer.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Show image, hide solid color
                    buttonContainer.setCardBackgroundColor(Color.TRANSPARENT)
                    imageView.visibility = View.VISIBLE

                    // Change text color to white when pressed
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
        isInFirstConeDialog = true
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

        // Add with new bowl button (primary - green) with long-press support
        val addWithBowlButton = createImagePressButtonWithLongPress(
            text = "Add with new bowl",
            isPrimary = true,
            onClick = {
                // Regular click - add 1 bowl and cone
                animateCardSelection(dialog) {
                    confettiHelper.showSuccessConfetti()
                    lifecycleScope.launch {
                        val originalAutoMode = isAutoMode
                        isAutoMode = false

                        Log.d(TAG, "🎯 🍶 Adding bowl for ${capturedSmoker.name} (auto disabled temporarily)")
                        val bowlTimestamp = now - 100
                        proceedWithLogHitWithSourceAndSmoker(ActivityType.BOWL, bowlTimestamp, finalStashSource, capturedSmoker)

                        delay(200)

                        Log.d(TAG, "🎯 🌿 Adding cone for ${capturedSmoker.name} (auto still disabled)")
                        proceedWithLogHitWithSourceAndSmoker(ActivityType.CONE, now, finalStashSource, capturedSmoker)
                        
                        // Restore auto mode
                        isAutoMode = originalAutoMode
                        Log.d(TAG, "🎯 ↻ Restored auto mode to: $originalAutoMode")

                        withContext(Dispatchers.Main) {
                            if (currentShareCode == null) {
                                refreshLocalSessionStatsIfNeeded()
                            }
                            sessionStatsVM.refreshTimer()
                            stashViewModel.onActivityLogged(ActivityType.CONE)
                            
                            // CRITICAL FIX: Manually trigger auto-advance after bowl+cone combo
                            if (originalAutoMode && smokers.isNotEmpty()) {
                                Log.d(TAG, "🎯 ➡️ Manually advancing smoker after bowl+cone combo")
                                handler.postDelayed({
                                    moveToNextActiveSmoker()
                                    Log.d(TAG, "🎯 ✅ Auto-advance completed after bowl+cone combo")
                                }, 300) // Small delay to ensure all operations complete
                            } else {
                                Log.d(TAG, "🎯 ❌ Not advancing: originalAutoMode=$originalAutoMode, smokersCount=${smokers.size}")
                            }
                        }
                    }
                }
            },
            onLongClick = {
                // Long press - show bowl quantity dialog
                vibrateFeedback(50) // Short vibration feedback
                dialog.dismiss() // Dismiss cone confirmation dialog
                showBowlQuantityDialogForCone(capturedSmoker, finalStashSource, now)
            }
        )
        buttonContainer.addView(addWithBowlButton)

        // Continue with last bowl button (neon yellow)
        val continueWithLastBowlButton = createImagePressButtonYellow("Continue with last bowl") {
            animateCardSelection(dialog) {
                confettiHelper.showSuccessConfetti()
                lifecycleScope.launch {
                    val originalAutoMode = isAutoMode
                    isAutoMode = false

                    Log.d(TAG, "🔄 CONTINUE_BOWL: Starting continue with last bowl for ${capturedSmoker.name}")
                    Log.d(TAG, "🔄 CONTINUE_BOWL: Current session active: ${sessionActive}")
                    
                    try {
                        // Get all bowls from the database to find the most recent ones
                        val allBowls = repo.getLogsInTimeRange(0L, System.currentTimeMillis())
                            .filter { it.type == ActivityType.BOWL }
                            .sortedByDescending { it.timestamp }
                        
                        Log.d(TAG, "🔄 CONTINUE_BOWL: Found ${allBowls.size} total bowls in database")
                        
                        // Find the last "batch" of bowls (bowls added close together)
                        val recentBowls = mutableListOf<ActivityLog>()
                        if (allBowls.isNotEmpty()) {
                            val latestBowl = allBowls.first()
                            recentBowls.add(latestBowl)
                            
                            // Find other bowls within 5 seconds of the latest bowl (likely added together)
                            for (bowl in allBowls.drop(1)) {
                                if (latestBowl.timestamp - bowl.timestamp <= 5000) {
                                    recentBowls.add(bowl)
                                } else {
                                    break // Stop when we find a gap larger than 5 seconds
                                }
                            }
                        }
                        
                        val allBowlsSinceLastSession = recentBowls.reversed() // Put in chronological order
                        
                        Log.d(TAG, "🔄 CONTINUE_BOWL: Found ${allBowlsSinceLastSession.size} recent bowls to continue from")
                        
                        if (allBowlsSinceLastSession.isNotEmpty()) {
                            // Get the earliest bowl to count from
                            val earliestBowl = allBowlsSinceLastSession.first()
                            val latestBowl = allBowlsSinceLastSession.last()
                            
                            Log.d(TAG, "🔄 CONTINUE_BOWL: Earliest bowl timestamp: ${earliestBowl.timestamp}")
                            Log.d(TAG, "🔄 CONTINUE_BOWL: Latest bowl timestamp: ${latestBowl.timestamp}")
                            Log.d(TAG, "🔄 CONTINUE_BOWL: Total bowls to continue: ${allBowlsSinceLastSession.size}")
                            
                            // Count cones since the latest bowl (most recent bowl)
                            val conesSinceLastBowl = repo.countConesBetweenTimestamps(
                                latestBowl.timestamp, 
                                System.currentTimeMillis()
                            )
                            Log.d(TAG, "🔄 CONTINUE_BOWL: Cones since last bowl: $conesSinceLastBowl")
                            
                            // Get all activities from the earliest bowl to now
                            val activitiesSinceBowls = repo.getLogsInTimeRange(
                                earliestBowl.timestamp,
                                System.currentTimeMillis()
                            )
                            Log.d(TAG, "🔄 CONTINUE_BOWL: Activities since bowls: ${activitiesSinceBowls.size}")
                            
                            // Calculate rounds from activities after the earliest bowl
                            val roundsFromBowls = if (activitiesSinceBowls.isNotEmpty()) {
                                calculateRoundsFromActivities(activitiesSinceBowls)
                            } else {
                                0
                            }
                            Log.d(TAG, "🔄 CONTINUE_BOWL: Rounds from bowls: $roundsFromBowls")
                            
                            // Get who had the last bowl and last cone
                            val lastBowlSmoker = repo.getSmokerById(latestBowl.smokerId)
                            val lastBowlSmokerName = lastBowlSmoker?.name ?: "Unknown"
                            
                            val lastCone = repo.getLastLogByType(ActivityType.CONE)
                            val lastConeSmokerName = if (lastCone != null) {
                                repo.getSmokerById(lastCone.smokerId)?.name ?: "Unknown"
                            } else {
                                "None"
                            }
                            
                            Log.d(TAG, "🔄 CONTINUE_BOWL: Last bowl by: $lastBowlSmokerName")
                            Log.d(TAG, "🔄 CONTINUE_BOWL: Last cone by: $lastConeSmokerName")
                            
                            // Store stats to preserve them
                            val bowlsCount = allBowlsSinceLastSession.size
                            
                            // Store the carried-over values in the ViewModel
                            // These will be used by refreshLocalSessionStatsIfNeeded to adjust the displayed stats
                            Log.d(TAG, "🔄 CONTINUE_BOWL: Setting carried-over stats - Cones: $conesSinceLastBowl, Rounds: $roundsFromBowls, Bowls: $bowlsCount")
                            sessionStatsVM.setCarriedOverStats(conesSinceLastBowl, roundsFromBowls, bowlsCount)
                            
                            // Store which smoker should get the carried-over bowls
                            Log.d(TAG, "🔄 CONTINUE_BOWL: Setting continue smoker ID: ${capturedSmoker.smokerId} (${capturedSmoker.name})")
                            sessionStatsVM.setContinueBowlSmoker(capturedSmoker.smokerId)
                            
                            // Now call refresh to display the stats with continue mode active
                            withContext(Dispatchers.Main) {
                                Log.d(TAG, "🔄 CONTINUE_BOWL: Calling refresh with continue mode active")
                                refreshLocalSessionStatsIfNeeded()
                                Log.d(TAG, "🔄 CONTINUE_BOWL: Initial stats displayed")
                            }
                            
                            Log.d(TAG, "🔄 CONTINUE_BOWL: Continue mode activated, stats will be adjusted during refresh")
                            
                            delay(200)
                            Log.d(TAG, "🔄 CONTINUE_BOWL: Ready to add cone with continue mode active")
                        } else {
                            Log.d(TAG, "🔄 CONTINUE_BOWL: No previous bowls found, adding a fresh bowl")
                            // If no previous bowls found, clear continue mode and add a new bowl normally
                            sessionStatsVM.clearCarriedOverStats()
                            
                            val bowlTimestamp = now - 100
                            proceedWithLogHitWithSourceAndSmoker(ActivityType.BOWL, bowlTimestamp, finalStashSource, capturedSmoker)
                            delay(200)
                        }
                        
                        // Add the cone
                        Log.d(TAG, "🔄 CONTINUE_BOWL: Adding cone at timestamp: $now")
                        proceedWithLogHitWithSourceAndSmoker(ActivityType.CONE, now, finalStashSource, capturedSmoker)
                        Log.d(TAG, "🔄 CONTINUE_BOWL: Cone added successfully")
                        
                        // Don't manually update stats - let the normal flow handle it
                        // The refresh will pick up the cone and add it to the carried-over stats
                        
                        // Restore auto mode
                        isAutoMode = originalAutoMode
                        Log.d(TAG, "🔄 CONTINUE_BOWL: Restored auto mode to: $originalAutoMode")

                        withContext(Dispatchers.Main) {
                            // The continue mode is set, so refresh will respect it
                            sessionStatsVM.refreshTimer()
                            stashViewModel.onActivityLogged(ActivityType.CONE)
                            
                            // Don't manually refresh here - proceedWithLogHitWithSourceAndSmoker already calls refresh internally
                            Log.d(TAG, "🔄 CONTINUE_BOWL: Stats will be refreshed by logHit function with continue mode active")
                            
                            // CRITICAL FIX: Manually trigger auto-advance after bowl+cone combo
                            if (originalAutoMode && smokers.isNotEmpty()) {
                                Log.d(TAG, "🔄 CONTINUE_BOWL: Manually advancing smoker after bowl+cone combo")
                                handler.postDelayed({
                                    moveToNextActiveSmoker()
                                    Log.d(TAG, "🔄 CONTINUE_BOWL: Auto-advance completed")
                                }, 300) // Small delay to ensure all operations complete
                            } else {
                                Log.d(TAG, "🔄 CONTINUE_BOWL: Not advancing - autoMode=$originalAutoMode, smokersCount=${smokers.size}")
                            }
                        }
                        
                        Log.d(TAG, "🔄 CONTINUE_BOWL: Process completed successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "🔄 CONTINUE_BOWL: Error during continue with last bowl", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Error continuing with last bowl", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        buttonContainer.addView(continueWithLastBowlButton)

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

        dialog.setOnDismissListener {
            isInFirstConeDialog = false
        }

        // Set initial alpha to 0 for fade-in
        rootContainer.alpha = 0f

        dialog.show()

        // Apply fade-in animation
        performManualFadeIn(rootContainer, 1000L)
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
        val cigaretteFractionContribution: Double = 0.0,
        val cigaretteFractionBefore: Double = 0.0,
        val customRatioId: String? = null,
        val customRatioName: String? = null,
        val retryCount: Int = 0,
        val maxRetries: Int = 10,
        val nextAttemptAt: Long = 0L,
        val backoffMs: Long = 0L
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

    private fun setupConfettiToggle() {
        // Load confetti preference
        confettiEnabled = prefs.getBoolean("confetti_enabled", true)
        updateConfettiButtonState()
        // Update ConfettiHelper with the initial state
        confettiHelper.setEnabled(confettiEnabled)

        binding.btnConfettiToggle.setOnClickListener {
            toggleConfetti()
        }
    }

    private fun toggleConfetti() {
        confettiEnabled = !confettiEnabled
        prefs.edit().putBoolean("confetti_enabled", confettiEnabled).apply()
        updateConfettiButtonState()
        // Update ConfettiHelper with the new state
        confettiHelper.setEnabled(confettiEnabled)
        
        // Show animation feedback (similar to vibration toggle)
        animateConfettiToggle()
        
        // Optional: Show a toast to confirm the change
        val message = if (confettiEnabled) "Confetti animations enabled" else "Confetti animations disabled"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateConfettiButtonState() {
        val iconRes = if (confettiEnabled) {
            R.drawable.ic_confetti_on
        } else {
            R.drawable.ic_confetti_off
        }
        binding.btnConfettiToggle.setImageResource(iconRes)
    }

    private fun animateConfettiToggle() {
        val originalTint = ContextCompat.getColor(this, android.R.color.darker_gray)
        val neonPurple = Color.parseColor("#BF7EFF")  // Using neon purple for confetti
        
        // Create color animation from neon purple to grey
        val colorAnimation = ValueAnimator.ofArgb(neonPurple, originalTint).apply {
            duration = 2000 // 2 seconds
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                binding.btnConfettiToggle.setColorFilter(color)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Ensure final state is grey
                    binding.btnConfettiToggle.setColorFilter(originalTint)
                }
            })
        }
        
        colorAnimation.start()
    }

    private fun setupModeToggleButton() {
        // Initialize button text based on current mode
        updateModeButtonText()
        
        binding.btnModeToggle.setOnClickListener { button ->
            // Toggle the mode
            isAutoMode = !isAutoMode
            val newAutoMode = isAutoMode  // Capture the value for the async operation
            lastModeToggleTime = System.currentTimeMillis()  // Track when we toggled
            lastLocalAutoModeValue = newAutoMode  // Remember what we set locally
            
            // Update button text
            updateModeButtonText()
            
            // Update session stats
            sessionStatsVM.setAutoMode(isAutoMode)

            if (!isApplyingRemoteAutoMode) {
                val shareCode = currentShareCode
                if (!shareCode.isNullOrEmpty()) {
                    // Set flag to prevent room listener from overriding during update
                    isUpdatingAutoModeToFirestore = true
                    lifecycleScope.launch {
                        try {
                            sessionSyncService.updateAutoModeInRoom(shareCode, newAutoMode).fold(
                                onSuccess = {
                                    Log.d(TAG, "🔘📡 Synced auto mode=$newAutoMode to room $shareCode")
                                },
                                onFailure = { error ->
                                    Log.e(TAG, "🔘📡 Failed to sync auto mode: ${error.message}")
                                }
                            )
                        } finally {
                            // Clear flag after update completes
                            isUpdatingAutoModeToFirestore = false
                        }
                    }
                }
            }

            // Show confetti animation
            confettiHelper.showMiniConfettiFromButton(button)
            
            // Animate button color
            animateModeToggle()
            
            // Log the change
            Log.d(TAG, "🔘 Mode toggled to: ${if (isAutoMode) "AUTO" else "STICKY"}")
        }
    }
    
    private fun updateModeButtonText() {
        binding.btnModeToggle.text = if (isAutoMode) "AUTO" else "STICKY"
    }
    
    private fun animateModeToggle() {
        // Create a simple text color animation for the mode toggle button
        val originalColor = ContextCompat.getColor(this, android.R.color.darker_gray)
        val neonColor = if (isAutoMode) {
            Color.parseColor("#66B2FF")  // Neon blue for auto
        } else {
            Color.parseColor("#FFA366")  // Neon orange for sticky
        }
        
        // Animate text color
        val colorAnimation = ValueAnimator.ofArgb(neonColor, originalColor).apply {
            duration = 1500
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                binding.btnModeToggle.setTextColor(color)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Restore original color
                    binding.btnModeToggle.setTextColor(originalColor)
                }
            })
        }
        colorAnimation.start()
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
                    // Save last activity type for turn notifications
                    turnNotificationManager.saveLastActivityType(type)
                },
                onFailure = { error ->
                    Log.e(TAG, "🎯 ❌ Failed to add to room: ${error.message}")
                    val handled = handleCloudSyncFailure(
                        error = error,
                        shareCode = currentShareCode!!,
                        smokerUid = smokerActivityUid,
                        smokerName = smoker.name,
                        activityType = type,
                        timestamp = adjustedTimestamp,
                        deviceId = deviceId,
                        localActivityId = "manual_${smokerActivityUid}_${adjustedTimestamp}_${type.name}"
                    )
                    if (!handled) {
                        Log.w(TAG, "🎯 Manual cloud sync failure not queued (non-quota issue)")
                    }
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

            val currentSessionId = sessionStatsVM.currentSessionId.value

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
            
            // Update active session summary
            updateActiveSessionSummary()
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
                    sessionId = sessionStatsVM.currentSessionId.value,
                    sessionStartTime = if (sessionActive) sessionStart else null,
                    gramsAtLog = 0.0,
                    pricePerGramAtLog = 0.0
                )

                activityHistory.add(activityLog)
                if (activityHistory.size > 10) {
                    activityHistory.removeAt(0)
                }
            }

            // Update stash tracking WITH CUSTOM ACTIVITY DEBUG
            val stashViewModel = ViewModelProvider(this@MainActivity).get(StashViewModel::class.java)
            Log.d("CUSTOM_STASH_DEBUG", "🟣 === STASH TRACKING CHECK (Location 5 - Specific Smoker) ===")
            Log.d("CUSTOM_STASH_DEBUG", "🟣 Activity type: $type")
            Log.d("CUSTOM_STASH_DEBUG", "🟣 Is core activity: ${type in listOf(ActivityType.JOINT, ActivityType.CONE, ActivityType.BOWL)}")
            Log.d("CUSTOM_STASH_DEBUG", "🟣 Stash available: ${stashViewModel.currentStash.value != null}")
            
            if (stashViewModel.currentStash.value != null) {
                // IMPORTANT: Only process core activities for stash system
                if (type in listOf(ActivityType.JOINT, ActivityType.CONE, ActivityType.BOWL)) {
                    Log.d("CUSTOM_STASH_DEBUG", "🟣 ✅ Processing CORE activity for stash")
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
                } else {
                    Log.d("CUSTOM_STASH_DEBUG", "🟣 ❌ SKIPPING CUSTOM activity - not processing for stash")
                }
            } else {
                Log.d("CUSTOM_STASH_DEBUG", "🟣 ❌ No stash available - skipping stash tracking")
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

    private fun resetButtonColors() {
        // Reset core activity buttons
        coreActivityButtons.forEach { button ->
            setActivityButtonSelected(button, false)
        }
        
        // Reset custom activity buttons
        customActivityButtons.forEach { button ->
            setActivityButtonSelected(button, false)
        }
    }
    
    private fun setActivityButtonSelected(button: Button, isSelected: Boolean) {
        if (isSelected) {
            // Filled state - green background, black text
            button.setBackgroundColor(ContextCompat.getColor(this, R.color.my_light_primary))
            button.setTextColor(Color.BLACK)
            // Remove stroke for filled state
            (button as? com.google.android.material.button.MaterialButton)?.apply {
                strokeWidth = 0
                // If this is an icon-only custom button, invert icon to black on green background
                iconTint = ColorStateList.valueOf(Color.BLACK)
            }
        } else {
            // Outlined state - transparent background, green text and border
            button.setBackgroundColor(Color.TRANSPARENT)
            button.setTextColor(ContextCompat.getColor(this, R.color.my_light_primary))
            // Add stroke back for outlined state
            (button as? com.google.android.material.button.MaterialButton)?.apply {
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.my_light_primary))
                strokeWidth = 4
                // Restore icon tint to green when not selected
                iconTint = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.my_light_primary))
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
                        // Update local paused list
                        pausedSmokerIds.remove(smokerId)
                        smokerManager.pausedSmokerIds.remove(smokerId)
                        
                        // Refresh the adapter to update icons
                        smokerAdapterNew.refreshOrganizedList(smokers, currentShareCode, pausedSmokerIds, awaySmokers)
                        
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
                        // Update local paused list
                        pausedSmokerIds.add(smokerId)
                        smokerManager.pausedSmokerIds.add(smokerId)
                        
                        // Refresh the adapter to update icons
                        smokerAdapterNew.refreshOrganizedList(smokers, currentShareCode, pausedSmokerIds, awaySmokers)
                        
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
                append("Are you sure you want to delete ALL smokers, their activities, and session history?")
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
        performManualFadeIn(dialogView, 1000L)
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
    
    private fun showNotSignedInPopup() {
        Log.d(TAG, "🏠 Showing not signed in popup for cloud features")
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        currentDialog = dialog
        val dialogView = createNotSignedInDialog(dialog)
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
            Log.d(TAG, "🏠 Not signed in dialog dismissed")
        }
        
        dialogView.alpha = 0f
        dialog.show()
        performManualFadeIn(dialogView, 500L)
    }
    
    private fun createNotSignedInDialog(dialog: Dialog): View {
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
                320.dpToPx(this@MainActivity),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
        }
        
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(this@MainActivity), 24.dpToPx(this@MainActivity),
                24.dpToPx(this@MainActivity), 24.dpToPx(this@MainActivity))
        }
        
        // Cloud icon
        val cloudIcon = TextView(this).apply {
            text = "☁️"
            textSize = 36f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(cloudIcon)
        
        // Title
        val titleText = TextView(this).apply {
            text = "NOT SIGNED IN"
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
        
        // Message with exact text requested
        val messageText = TextView(this).apply {
            text = "You're not signed in via Google.\n\nClick the add smoker button and login as Cloud Smoker to access cloud features"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20.dpToPx(this@MainActivity)
            }
        }
        contentLayout.addView(messageText)
        
        // OK button
        val okButton = createThemedDialogButton("OK", false, Color.WHITE) {
            dialog.dismiss()
            // Return to cloud session options after dismiss
            Handler(Looper.getMainLooper()).postDelayed({
                showCloudSessionOptions()
            }, 300)
        }
        okButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            44.dpToPx(this@MainActivity)
        )
        contentLayout.addView(okButton)
        
        mainCard.addView(contentLayout)
        rootContainer.addView(mainCard)
        
        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                dialog.dismiss()
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
            pendingActivityType = null
            pendingCustomActivity = null  // Clear pending custom activity
            Log.d(TAG, "🏠 No cloud user dialog dismissed")
        }

        // Set initial alpha to 0 for fade-in
        dialogView.alpha = 0f

        dialog.show()

        // Apply fade-in animation with 2-second duration
        performManualFadeIn(dialogView, 1000L)
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
            // Store pending activity data before dialog dismissal clears it
            val savedPendingType = pendingActivityType
            val savedPendingCustom = pendingCustomActivity
            
            animateCardSelection(dialog) {
                // Restore pending activity data after dialog dismissal
                pendingActivityType = savedPendingType
                pendingCustomActivity = savedPendingCustom
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
        val sessionDao = AppDatabase.getDatabase(this@MainActivity).sessionSummaryDao()
        val smokerDao = AppDatabase.getDatabase(this@MainActivity).smokerDao()

        lifecycleScope.launch(Dispatchers.IO) {
            var totalLogsDeleted = 0
            var totalLogsKept = 0
            var totalSessionsDeleted = 0
            var totalSessionsUpdated = 0
            var softDeletedCount = 0
            var hardDeletedCount = 0

            if (!keepData) {
                // Handle session summaries first
                val sessionDao = AppDatabase.getDatabase(this@MainActivity).sessionSummaryDao()
                val allSummaries = sessionDao.getAllSummariesSync()
                val smokerNamesToDelete = allSmokersToDelete.map { smoker -> smoker.name }.toSet()
                
                Log.d(TAG, "🗑️🔴 Processing ${allSummaries.size} session summaries")
                
                for (summary in allSummaries) {
                    val remainingSmokers = summary.smokerNames.filter { name -> name !in smokerNamesToDelete }
                    
                    if (remainingSmokers.isEmpty()) {
                        // All smokers in this session are being deleted
                        sessionDao.delete(summary)
                        totalSessionsDeleted++
                        Log.d(TAG, "🗑️🔴 Deleted session ${summary.id} (all smokers deleted)")
                    } else if (remainingSmokers.size < summary.smokerNames.size) {
                        // Some smokers remain - update the session
                        val indicesToKeep = summary.smokerNames.mapIndexedNotNull { index, name ->
                            if (name !in smokerNamesToDelete) index else null
                        }
                        
                        val updatedConesPerSmoker = indicesToKeep.mapNotNull { index ->
                            summary.conesPerSmoker.getOrNull(index)
                        }
                        
                        val updatedSummary = summary.copy(
                            smokerNames = remainingSmokers,
                            conesPerSmoker = updatedConesPerSmoker,
                            totalCones = updatedConesPerSmoker.sum()
                        )
                        
                        sessionDao.update(updatedSummary)
                        totalSessionsUpdated++
                        Log.d(TAG, "🗑️🔴 Updated session ${summary.id} (removed deleted smokers)")
                    }
                }
                
                Log.d(TAG, "🗑️🔴 Sessions: $totalSessionsDeleted deleted, $totalSessionsUpdated updated")
            }

            // Process each smoker
            allSmokersToDelete.forEach { smoker ->
                Log.d(TAG, "🗑️🔴 Processing ${smoker.name}, isCloud: ${smoker.isCloudSmoker}")
                
                // Check if this smoker has participated in cloud sessions
                val hasCloudParticipation = sessionDao.hasSmokerParticipatedInCloudSessions(smoker.name)
                Log.d(TAG, "🗑️🔴 ${smoker.name} has cloud participation: $hasCloudParticipation")
                
                // Use soft delete for local smokers with cloud participation OR when keeping data
                if ((hasCloudParticipation && !smoker.isCloudSmoker) || (keepData && !smoker.isCloudSmoker)) {
                    // Soft delete
                    Log.d(TAG, "🗑️🔴 SOFT deleting ${smoker.name}")
                    smokerDao.softDeleteSmoker(smoker.smokerId)
                    softDeletedCount++
                    
                    val logCount = repo.getLogsForSmoker(smoker.smokerId).size
                    totalLogsKept += logCount
                } else {
                    // Hard delete
                    Log.d(TAG, "🗑️🔴 HARD deleting ${smoker.name}")
                    
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
                    
                    // Hard delete the smoker entity
                    repo.deleteSmoker(smoker)
                    hardDeletedCount++
                }
                Log.d(TAG, "🗑️🔴 ✅ Processed smoker: ${smoker.name}")
            }

            withContext(Dispatchers.Main) {
                // Sign out if needed
                val hasCloudUser = allSmokersToDelete.any { it.cloudUserId == authManager.getCurrentUserId() }
                if (hasCloudUser) {
                    Log.d(TAG, "🗑️🔴 Signing out user")
                    authManager.signOut()
                }

                val message = when {
                    softDeletedCount > 0 && hardDeletedCount > 0 -> {
                        "Removed $softDeletedCount smokers (data kept), deleted $hardDeletedCount smokers"
                    }
                    softDeletedCount > 0 -> {
                        "Removed ${allSmokersToDelete.size} smokers (data kept)"
                    }
                    keepData -> {
                        "Deleted ${allSmokersToDelete.size} smokers (kept data)"
                    }
                    else -> {
                        "Deleted ${allSmokersToDelete.size} smokers, $totalLogsDeleted activities, and $totalSessionsDeleted sessions"
                    }
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
        if (drawable is GradientDrawable) {
            drawable.setColor(getSyncStatusColor(status))
        } else {
            dotView.setBackgroundColor(getSyncStatusColor(status))
        }
    }

    private fun updateQueueStatusDot(dotView: View?, smoker: Smoker) {
        if (dotView == null) return

        val smokerUid = if (smoker.isCloudSmoker && !smoker.cloudUserId.isNullOrEmpty()) {
            smoker.cloudUserId!!
        } else {
            "local_${smoker.uid}"
        }

        val currentCode = currentShareCode
        val hasPending = offlineActivityQueue.any { activity ->
            activity.smokerUid == smokerUid && (currentCode == null || activity.shareCode == currentCode)
        }

        if (hasPending) {
            dotView.visibility = View.VISIBLE
            val color = ContextCompat.getColor(this, R.color.neon_light_blue)
            val drawable = dotView.background
            if (drawable is GradientDrawable) {
                drawable.setColor(color)
            } else {
                dotView.setBackgroundColor(color)
            }
        } else {
            dotView.visibility = View.GONE
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

    private fun refreshQueueIndicators() {
        if (!::smokerAdapterNew.isInitialized) return
        runOnUiThread {
            smokerAdapterNew.notifyDataSetChanged()
        }
    }

    private fun loadOfflineQueue() {
        val json = prefs.getString("offline_activity_queue", null) ?: return
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<OfflineActivity>>() {}.type
        try {
            val now = System.currentTimeMillis()
            val loaded = gson.fromJson<List<OfflineActivity>>(json, type)
            offlineActivityQueue.clear()
            val normalized = loaded.map { item ->
                val backoff = if (item.backoffMs <= 0) INITIAL_BACKOFF_MS else item.backoffMs
                val nextAt = if (item.nextAttemptAt <= now) now + backoff else item.nextAttemptAt
                item.copy(backoffMs = backoff, nextAttemptAt = nextAt)
            }
            offlineActivityQueue.addAll(normalized)
            Log.d(TAG, "💾 OFFLINE_QUEUE: Loaded ${offlineActivityQueue.size} activities from prefs")
            refreshQueueIndicators()
            if (offlineActivityQueue.isNotEmpty()) {
                val nextAt = offlineActivityQueue.minOf { it.nextAttemptAt }
                scheduleNextQueueProcess(nextAt)
            }
        } catch (e: Exception) {
            Log.e(TAG, "💾 OFFLINE_QUEUE: Error loading queue", e)
        }
    }

    private fun addToOfflineQueue(activity: OfflineActivity): Boolean {
        val existing = offlineActivityQueue.find { it.activityId == activity.activityId }
        if (existing != null) {
            Log.d(TAG, "📴 OFFLINE_QUEUE: Activity already queued (${activity.activityType} for ${activity.smokerName}), skipping")
            return false
        }

        val now = System.currentTimeMillis()
        val queued = activity.copy(
            retryCount = 0,
            nextAttemptAt = now + INITIAL_BACKOFF_MS,
            backoffMs = INITIAL_BACKOFF_MS
        )

        offlineActivityQueue.add(queued)
        saveOfflineQueue()
        Log.d(TAG, "📴 OFFLINE_QUEUE: Added activity - ${queued.activityType} for ${queued.smokerName}")
        Log.d(TAG, "📴 OFFLINE_QUEUE: Queue size now: ${offlineActivityQueue.size}")

        refreshQueueIndicators()
        val nextAt = offlineActivityQueue.minOf { it.nextAttemptAt }
        scheduleNextQueueProcess(nextAt)

        runOnUiThread {
            Toast.makeText(this, "Activity saved offline, will sync when online", Toast.LENGTH_LONG).show()
        }
        return true
    }

    private fun scheduleNextQueueProcess(targetTimeMs: Long) {
        val handler = syncCheckHandler ?: Handler(Looper.getMainLooper()).also { syncCheckHandler = it }
        val runnable = syncCheckRunnable ?: Runnable { processOfflineQueue() }.also { syncCheckRunnable = it }

        handler.removeCallbacks(runnable)
        val delay = (targetTimeMs - System.currentTimeMillis()).coerceAtLeast(MIN_QUEUE_POLL_MS)
        handler.postDelayed(runnable, delay)
        Log.d(TAG, "🔄 SYNC_QUEUE: Scheduled next attempt in ${delay}ms")
    }

    private fun stopOfflineSyncChecker() {
        syncCheckRunnable?.let { runnable ->
            syncCheckHandler?.removeCallbacks(runnable)
        }
        syncCheckRunnable = null
        syncCheckHandler = null
        Log.d(TAG, "🔄 SYNC_QUEUE: Stopped scheduled processing")
    }

    private fun handleCloudSyncFailure(
        error: Throwable,
        shareCode: String,
        smokerUid: String,
        smokerName: String,
        activityType: ActivityType,
        timestamp: Long,
        deviceId: String,
        localActivityId: String,
        cigaretteFractionContribution: Double = 0.0,
        cigaretteFractionBefore: Double = 0.0,
        customRatioId: String? = null,
        customRatioName: String? = null
    ): Boolean {
        if (error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED
        ) {
            val activityId = if (localActivityId.isNotEmpty()) {
                localActivityId
            } else {
                "${smokerUid}_${timestamp}_${activityType.name}"
            }

            val offlineActivity = OfflineActivity(
                activityId = activityId,
                shareCode = shareCode,
                smokerUid = smokerUid,
                smokerName = smokerName,
                activityType = activityType,
                timestamp = timestamp,
                deviceId = deviceId,
                cigaretteFractionContribution = cigaretteFractionContribution,
                cigaretteFractionBefore = cigaretteFractionBefore,
                customRatioId = customRatioId,
                customRatioName = customRatioName
            )

            val added = addToOfflineQueue(offlineActivity)
            if (!added) {
                Log.d(TAG, "📴 OFFLINE_QUEUE: Failure handling detected duplicate for ${activityType} (${smokerName})")
                refreshQueueIndicators()
            }
            return added
        }
        return false
    }

    private fun processOfflineQueue() {
        if (offlineActivityQueue.isEmpty()) return

        val now = System.currentTimeMillis()
        if (!isNetworkAvailable) {
            Log.d(TAG, "🔄 SYNC_QUEUE: Network unavailable, deferring queue processing")
            scheduleNextQueueProcess(now + NETWORK_RETRY_MS)
            return
        }

        val dueActivities = offlineActivityQueue.filter { it.nextAttemptAt <= now }
        if (dueActivities.isEmpty()) {
            val nextAt = offlineActivityQueue.minOf { it.nextAttemptAt }
            scheduleNextQueueProcess(nextAt)
            return
        }

        Log.d(TAG, "🔄 SYNC_QUEUE: Processing ${dueActivities.size} offline activities")

        lifecycleScope.launch {
            for (activity in dueActivities) {
                Log.d(TAG, "🔄 SYNC_ITEM: Syncing ${activity.activityType} for ${activity.smokerName}")

                sessionSyncService.addActivityToRoom(
                    shareCode = activity.shareCode,
                    smokerUid = activity.smokerUid,
                    smokerName = activity.smokerName,
                    activityType = activity.activityType,
                    timestamp = activity.timestamp,
                    deviceId = activity.deviceId,
                    cigaretteFractionContribution = activity.cigaretteFractionContribution,
                    cigaretteFractionBefore = activity.cigaretteFractionBefore,
                    customRatioId = activity.customRatioId,
                    customRatioName = activity.customRatioName
                ).fold(
                    onSuccess = {
                        Log.d(TAG, "✅ SYNC_SUCCESS: ${activity.activityType} for ${activity.smokerName}")
                        offlineActivityQueue.remove(activity)
                        saveOfflineQueue()
                        refreshQueueIndicators()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "❌ SYNC_FAIL: ${activity.activityType} - ${error.message}")
                        val index = offlineActivityQueue.indexOfFirst { it.activityId == activity.activityId }
                        if (index >= 0) {
                            val current = offlineActivityQueue[index]
                            val nextBackoff = (current.backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                            val jitter = (nextBackoff * 0.1 * Random.nextDouble()).toLong()
                            val nextAttempt = System.currentTimeMillis() + nextBackoff + jitter

                            val updated = current.copy(
                                retryCount = current.retryCount + 1,
                                backoffMs = nextBackoff,
                                nextAttemptAt = nextAttempt
                            )

                            if (updated.retryCount >= updated.maxRetries) {
                                Log.e(TAG, "❌ SYNC_FAIL: Max retries reached for ${activity.activityType}; removing from queue")
                                offlineActivityQueue.removeAt(index)
                            } else {
                                offlineActivityQueue[index] = updated
                                Log.d(TAG, "🔁 SYNC_RETRY: Next attempt for ${activity.activityType} at $nextAttempt (${updated.retryCount}/${updated.maxRetries})")
                            }
                            saveOfflineQueue()
                            refreshQueueIndicators()
                        }
                    }
                )

                delay(SYNC_RETRY_SPACING_MS)
            }

            if (offlineActivityQueue.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "All offline activities synced!", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "✅ SYNC_COMPLETE: All offline activities synced")
                refreshQueueIndicators()
                stopOfflineSyncChecker()
            } else {
                Log.d(TAG, "⚠️ SYNC_PARTIAL: ${offlineActivityQueue.size} activities still pending")
                val nextAt = offlineActivityQueue.minOf { it.nextAttemptAt }
                scheduleNextQueueProcess(nextAt)
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
            Log.d(TAG, "📱      NextAttemptAt: ${Date(activity.nextAttemptAt)} (backoff=${activity.backoffMs}ms)")
        }
        Log.d(TAG, "📱 === END DEBUG ===")
    }

}

// Extension function for dp to px conversion
fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}
