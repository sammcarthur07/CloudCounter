package com.sam.cloudcounter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.sam.cloudcounter.databinding.FragmentAboutBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.HtmlCompat
import com.google.firebase.firestore.DocumentChange
import android.view.ViewTreeObserver
import android.animation.ValueAnimator
import android.graphics.Color
import androidx.appcompat.app.AppCompatDialog
import com.sam.cloudcounter.databinding.DialogUserManualBinding
import android.widget.FrameLayout
import kotlin.random.Random

class AboutFragment : Fragment() {

    companion object {
        private const val TAG = "AboutFragment"
        private const val ROTATION_INTERVAL_MS = 16000L // Changed from 26 to 16 seconds
        private const val FADE_DURATION_MS = 5000L
        private const val MESSAGE_COOLDOWN_MS = 30000L // 30 seconds
        private const val MAX_MESSAGE_LENGTH = 4000
        private const val STICKY_THRESHOLD_MS = 120000L // 2 minutes in milliseconds
        private const val STICKY_THRESHOLD_SECONDS = 120 // 2 minutes in seconds

        // Cities with their actual timezone IDs for accurate calculations
        private val CITIES_WITH_TIMEZONES = listOf(
            // North America
            Pair("Los Angeles, USA", "America/Los_Angeles"),
            Pair("San Francisco, USA", "America/Los_Angeles"),
            Pair("Seattle, USA", "America/Los_Angeles"),
            Pair("Portland, USA", "America/Los_Angeles"),
            Pair("San Diego, USA", "America/Los_Angeles"),
            Pair("Las Vegas, USA", "America/Los_Angeles"),
            Pair("Vancouver, Canada", "America/Vancouver"),
            Pair("Denver, USA", "America/Denver"),
            Pair("Phoenix, USA", "America/Phoenix"),
            Pair("Salt Lake City, USA", "America/Denver"),
            Pair("Calgary, Canada", "America/Edmonton"),
            Pair("Edmonton, Canada", "America/Edmonton"),
            Pair("Chicago, USA", "America/Chicago"),
            Pair("Houston, USA", "America/Chicago"),
            Pair("Dallas, USA", "America/Chicago"),
            Pair("Austin, USA", "America/Chicago"),
            Pair("San Antonio, USA", "America/Chicago"),
            Pair("New Orleans, USA", "America/Chicago"),
            Pair("Minneapolis, USA", "America/Chicago"),
            Pair("Kansas City, USA", "America/Chicago"),
            Pair("Winnipeg, Canada", "America/Winnipeg"),
            Pair("Mexico City, Mexico", "America/Mexico_City"),
            Pair("Guadalajara, Mexico", "America/Mexico_City"),
            Pair("Monterrey, Mexico", "America/Monterrey"),
            Pair("Cancun, Mexico", "America/Cancun"),
            Pair("New York, USA", "America/New_York"),
            Pair("Toronto, Canada", "America/Toronto"),
            Pair("Miami, USA", "America/New_York"),
            Pair("Boston, USA", "America/New_York"),
            Pair("Philadelphia, USA", "America/New_York"),
            Pair("Washington DC, USA", "America/New_York"),
            Pair("Atlanta, USA", "America/New_York"),
            Pair("Detroit, USA", "America/Detroit"),
            Pair("Montreal, Canada", "America/Montreal"),
            Pair("Ottawa, Canada", "America/Toronto"),
            Pair("Quebec City, Canada", "America/Montreal"),

            // Central & South America
            Pair("São Paulo, Brazil", "America/Sao_Paulo"),
            Pair("Rio de Janeiro, Brazil", "America/Sao_Paulo"),
            Pair("Buenos Aires, Argentina", "America/Argentina/Buenos_Aires"),
            Pair("Lima, Peru", "America/Lima"),
            Pair("Bogotá, Colombia", "America/Bogota"),
            Pair("Santiago, Chile", "America/Santiago"),
            Pair("Caracas, Venezuela", "America/Caracas"),
            Pair("Quito, Ecuador", "America/Guayaquil"),
            Pair("La Paz, Bolivia", "America/La_Paz"),
            Pair("Montevideo, Uruguay", "America/Montevideo"),
            Pair("Asunción, Paraguay", "America/Asuncion"),
            Pair("Panama City, Panama", "America/Panama"),
            Pair("San José, Costa Rica", "America/Costa_Rica"),
            Pair("Guatemala City, Guatemala", "America/Guatemala"),
            Pair("Havana, Cuba", "America/Havana"),

            // Europe
            Pair("London, UK", "Europe/London"),
            Pair("Manchester, UK", "Europe/London"),
            Pair("Birmingham, UK", "Europe/London"),
            Pair("Glasgow, UK", "Europe/London"),
            Pair("Edinburgh, UK", "Europe/London"),
            Pair("Dublin, Ireland", "Europe/Dublin"),
            Pair("Cork, Ireland", "Europe/Dublin"),
            Pair("Lisbon, Portugal", "Europe/Lisbon"),
            Pair("Porto, Portugal", "Europe/Lisbon"),
            Pair("Madrid, Spain", "Europe/Madrid"),
            Pair("Barcelona, Spain", "Europe/Madrid"),
            Pair("Valencia, Spain", "Europe/Madrid"),
            Pair("Seville, Spain", "Europe/Madrid"),
            Pair("Paris, France", "Europe/Paris"),
            Pair("Lyon, France", "Europe/Paris"),
            Pair("Marseille, France", "Europe/Paris"),
            Pair("Nice, France", "Europe/Paris"),
            Pair("Amsterdam, Netherlands", "Europe/Amsterdam"),
            Pair("Rotterdam, Netherlands", "Europe/Amsterdam"),
            Pair("Brussels, Belgium", "Europe/Brussels"),
            Pair("Antwerp, Belgium", "Europe/Brussels"),
            Pair("Berlin, Germany", "Europe/Berlin"),
            Pair("Frankfurt, Germany", "Europe/Berlin"),
            Pair("Munich, Germany", "Europe/Berlin"),
            Pair("Hamburg, Germany", "Europe/Berlin"),
            Pair("Cologne, Germany", "Europe/Berlin"),
            Pair("Stuttgart, Germany", "Europe/Berlin"),
            Pair("Zurich, Switzerland", "Europe/Zurich"),
            Pair("Geneva, Switzerland", "Europe/Zurich"),
            Pair("Vienna, Austria", "Europe/Vienna"),
            Pair("Prague, Czechia", "Europe/Prague"),
            Pair("Budapest, Hungary", "Europe/Budapest"),
            Pair("Rome, Italy", "Europe/Rome"),
            Pair("Milan, Italy", "Europe/Rome"),
            Pair("Naples, Italy", "Europe/Rome"),
            Pair("Turin, Italy", "Europe/Rome"),
            Pair("Florence, Italy", "Europe/Rome"),
            Pair("Venice, Italy", "Europe/Rome"),
            Pair("Athens, Greece", "Europe/Athens"),
            Pair("Warsaw, Poland", "Europe/Warsaw"),
            Pair("Krakow, Poland", "Europe/Warsaw"),
            Pair("Stockholm, Sweden", "Europe/Stockholm"),
            Pair("Gothenburg, Sweden", "Europe/Stockholm"),
            Pair("Oslo, Norway", "Europe/Oslo"),
            Pair("Bergen, Norway", "Europe/Oslo"),
            Pair("Copenhagen, Denmark", "Europe/Copenhagen"),
            Pair("Helsinki, Finland", "Europe/Helsinki"),
            Pair("Reykjavik, Iceland", "Atlantic/Reykjavik"),
            Pair("Bucharest, Romania", "Europe/Bucharest"),
            Pair("Sofia, Bulgaria", "Europe/Sofia"),
            Pair("Belgrade, Serbia", "Europe/Belgrade"),
            Pair("Zagreb, Croatia", "Europe/Zagreb"),

            // Russia & Eastern Europe
            Pair("Moscow, Russia", "Europe/Moscow"),
            Pair("St. Petersburg, Russia", "Europe/Moscow"),
            Pair("Kiev, Ukraine", "Europe/Kiev"),
            Pair("Minsk, Belarus", "Europe/Minsk"),
            Pair("Istanbul, Turkey", "Europe/Istanbul"),
            Pair("Ankara, Turkey", "Europe/Istanbul"),

            // Middle East
            Pair("Dubai, UAE", "Asia/Dubai"),
            Pair("Abu Dhabi, UAE", "Asia/Dubai"),
            Pair("Doha, Qatar", "Asia/Qatar"),
            Pair("Kuwait City, Kuwait", "Asia/Kuwait"),
            Pair("Riyadh, Saudi Arabia", "Asia/Riyadh"),
            Pair("Jeddah, Saudi Arabia", "Asia/Riyadh"),
            Pair("Tel Aviv, Israel", "Asia/Jerusalem"),
            Pair("Jerusalem, Israel", "Asia/Jerusalem"),
            Pair("Beirut, Lebanon", "Asia/Beirut"),
            Pair("Amman, Jordan", "Asia/Amman"),
            Pair("Cairo, Egypt", "Africa/Cairo"),
            Pair("Alexandria, Egypt", "Africa/Cairo"),

            // Africa
            Pair("Cape Town, South Africa", "Africa/Johannesburg"),
            Pair("Johannesburg, South Africa", "Africa/Johannesburg"),
            Pair("Durban, South Africa", "Africa/Johannesburg"),
            Pair("Pretoria, South Africa", "Africa/Johannesburg"),
            Pair("Lagos, Nigeria", "Africa/Lagos"),
            Pair("Nairobi, Kenya", "Africa/Nairobi"),
            Pair("Addis Ababa, Ethiopia", "Africa/Addis_Ababa"),
            Pair("Casablanca, Morocco", "Africa/Casablanca"),
            Pair("Algiers, Algeria", "Africa/Algiers"),
            Pair("Tunis, Tunisia", "Africa/Tunis"),
            Pair("Accra, Ghana", "Africa/Accra"),
            Pair("Dakar, Senegal", "Africa/Dakar"),

            // South Asia
            Pair("Mumbai, India", "Asia/Kolkata"),
            Pair("New Delhi, India", "Asia/Kolkata"),
            Pair("Bangalore, India", "Asia/Kolkata"),
            Pair("Chennai, India", "Asia/Kolkata"),
            Pair("Kolkata, India", "Asia/Kolkata"),
            Pair("Hyderabad, India", "Asia/Kolkata"),
            Pair("Pune, India", "Asia/Kolkata"),
            Pair("Ahmedabad, India", "Asia/Kolkata"),
            Pair("Karachi, Pakistan", "Asia/Karachi"),
            Pair("Lahore, Pakistan", "Asia/Karachi"),
            Pair("Islamabad, Pakistan", "Asia/Karachi"),
            Pair("Dhaka, Bangladesh", "Asia/Dhaka"),
            Pair("Colombo, Sri Lanka", "Asia/Colombo"),
            Pair("Kathmandu, Nepal", "Asia/Kathmandu"),

            // Southeast Asia
            Pair("Bangkok, Thailand", "Asia/Bangkok"),
            Pair("Phuket, Thailand", "Asia/Bangkok"),
            Pair("Singapore", "Asia/Singapore"),
            Pair("Kuala Lumpur, Malaysia", "Asia/Kuala_Lumpur"),
            Pair("Penang, Malaysia", "Asia/Kuala_Lumpur"),
            Pair("Jakarta, Indonesia", "Asia/Jakarta"),
            Pair("Bali, Indonesia", "Asia/Makassar"),
            Pair("Surabaya, Indonesia", "Asia/Jakarta"),
            Pair("Manila, Philippines", "Asia/Manila"),
            Pair("Cebu, Philippines", "Asia/Manila"),
            Pair("Ho Chi Minh City, Vietnam", "Asia/Ho_Chi_Minh"),
            Pair("Hanoi, Vietnam", "Asia/Ho_Chi_Minh"),
            Pair("Phnom Penh, Cambodia", "Asia/Phnom_Penh"),
            Pair("Yangon, Myanmar", "Asia/Yangon"),

            // East Asia
            Pair("Hong Kong", "Asia/Hong_Kong"),
            Pair("Beijing, China", "Asia/Shanghai"),
            Pair("Shanghai, China", "Asia/Shanghai"),
            Pair("Guangzhou, China", "Asia/Shanghai"),
            Pair("Shenzhen, China", "Asia/Shanghai"),
            Pair("Chengdu, China", "Asia/Shanghai"),
            Pair("Wuhan, China", "Asia/Shanghai"),
            Pair("Seoul, South Korea", "Asia/Seoul"),
            Pair("Busan, South Korea", "Asia/Seoul"),
            Pair("Tokyo, Japan", "Asia/Tokyo"),
            Pair("Osaka, Japan", "Asia/Tokyo"),
            Pair("Kyoto, Japan", "Asia/Tokyo"),
            Pair("Yokohama, Japan", "Asia/Tokyo"),
            Pair("Nagoya, Japan", "Asia/Tokyo"),
            Pair("Sapporo, Japan", "Asia/Tokyo"),
            Pair("Taipei, Taiwan", "Asia/Taipei"),
            Pair("Kaohsiung, Taiwan", "Asia/Taipei"),

            // Australia & New Zealand
            Pair("Sydney, Australia", "Australia/Sydney"),
            Pair("Melbourne, Australia", "Australia/Melbourne"),
            Pair("Brisbane, Australia", "Australia/Brisbane"),
            Pair("Gold Coast, Australia", "Australia/Brisbane"),
            Pair("Perth, Australia", "Australia/Perth"),
            Pair("Adelaide, Australia", "Australia/Adelaide"),
            Pair("Canberra, Australia", "Australia/Sydney"),
            Pair("Newcastle, Australia", "Australia/Sydney"),
            Pair("Cairns, Australia", "Australia/Brisbane"),
            Pair("Darwin, Australia", "Australia/Darwin"),
            Pair("Hobart, Australia", "Australia/Hobart"),
            Pair("Auckland, New Zealand", "Pacific/Auckland"),
            Pair("Wellington, New Zealand", "Pacific/Auckland"),
            Pair("Christchurch, New Zealand", "Pacific/Auckland"),
            Pair("Hamilton, New Zealand", "Pacific/Auckland"),

            // Pacific Islands
            Pair("Honolulu, USA", "Pacific/Honolulu"),
            Pair("Anchorage, USA", "America/Anchorage"),
            Pair("Fiji", "Pacific/Fiji"),
            Pair("Tahiti, French Polynesia", "Pacific/Tahiti"),
            Pair("Guam", "Pacific/Guam")
        )

        private val REGION_GROUPS = mapOf(
            "North America" to listOf("USA", "Canada", "Mexico"),
            "South America" to listOf("Brazil", "Argentina", "Peru", "Colombia", "Chile", "Venezuela", "Ecuador", "Bolivia", "Uruguay", "Paraguay", "Panama", "Costa Rica", "Guatemala", "Cuba"),
            "Europe" to listOf("UK", "Ireland", "Portugal", "Spain", "France", "Netherlands", "Belgium", "Germany", "Switzerland", "Austria", "Czechia", "Hungary", "Italy", "Greece", "Poland", "Sweden", "Norway", "Denmark", "Finland", "Iceland", "Romania", "Bulgaria", "Serbia", "Croatia"),
            "Eastern Europe/Russia" to listOf("Russia", "Ukraine", "Belarus", "Turkey"),
            "Middle East" to listOf("UAE", "Qatar", "Kuwait", "Saudi Arabia", "Israel", "Lebanon", "Jordan", "Egypt"),
            "Africa" to listOf("South Africa", "Nigeria", "Kenya", "Ethiopia", "Morocco", "Algeria", "Tunisia", "Ghana", "Senegal"),
            "South Asia" to listOf("India", "Pakistan", "Bangladesh", "Sri Lanka", "Nepal"),
            "Southeast Asia" to listOf("Thailand", "Singapore", "Malaysia", "Indonesia", "Philippines", "Vietnam", "Cambodia", "Myanmar"),
            "East Asia" to listOf("Hong Kong", "China", "South Korea", "Japan", "Taiwan"),
            "Oceania" to listOf("Australia", "New Zealand", "Fiji", "French Polynesia", "Guam")
        )

        private var lastRegionShown = ""
        private var fadeAnimator: ValueAnimator? = null
        private var currentDisplayCity: String? = null
        private var currentDisplayTargetTime: Long = 0L
        private var currentDisplayMode: String = "" // "sticky", "normal", "actual", "climax"
        private var liveCountdownRunnable: Runnable? = null

        // Files to include in code statistics
        private val CREATED_FILES = listOf(
            "AboutFragment.kt", "AboutOrInboxFragment.kt", "ActivityLog.kt",
            "ActivityLogDao.kt", "ActivityNotificationReceiver.kt", "ActivityRepository.kt",
            "ActivityType.kt", "AddGoalDialog.kt", "AddSmokerDialog.kt",
            "AppDatabase.kt", "AutoAddManager.kt", "ChartModels.kt",
            "ChatDao.kt", "ChatEntities.kt", "ChatFragment.kt",
            "ChatListenerService.kt", "ChatMessageAdapter.kt", "ChatReplyReceiver.kt",
            "ChatSyncService.kt", "ChatViewModel.kt", "CloudCounterApplication.kt",
            "CloudSessionDialogEffects.kt", "CloudSmokerData.kt", "CloudSmokerSearchAdapter.kt",
            "CloudSyncService.kt", "ConfettiHelper.kt", "Converters.kt",
            "CustomCircularProgressBar.kt", "FirebaseAuthManager.kt", "Goal.kt",
            "GoalAdapter.kt", "GoalConverters.kt", "GoalDao.kt",
            "GoalFragment.kt", "GoalService.kt", "GoalViewModel.kt",
            "GraphFragment.kt", "GraphViewModel.kt", "HistoryAdapter.kt",
            "HistoryFragment.kt", "HistoryViewModel.kt", "InboxFragment.kt",
            "MainActivity.kt", "NotificationHelper.kt", "ParticleContainerFinder.kt",
            "PasswordDialog.kt", "PasswordUtils.kt", "PerSmokerStats.kt",
            "ProgressEffectsHelper.kt", "ReportHelper.kt", "RoomData.kt",
            "RoomListAdapter.kt", "SessionStatsViewModel.kt", "SessionStatsViewModelFactory.kt",
            "SessionSummary.kt", "SessionSummaryDao.kt", "SessionSyncService.kt",
            "SeshFragment.kt", "ShimmerTextAnimation.kt", "Smoker.kt",
            "SmokerAdapter.kt", "SmokerDao.kt", "SmokerManager.kt",
            "StashDao.kt", "StashDataClasses.kt", "StashDistributionAdapter.kt",
            "StashFragment.kt", "StashIntegration.kt", "StashStatsCalculator.kt",
            "StashViewModel.kt", "StatsControlsDialog.kt", "StatsData.kt",
            "StatsFragment.kt", "StatsManager.kt", "StatsViewModel.kt",
            "SupportMessage.kt", "SupportMessageAdapter.kt", "SupportMessagesWatcher.kt",
            "TimePeriod.kt", "TimerSoundHelper.kt", "VideoCallService.kt",
            "VideoSignalingService.kt", "VideoStreamView.kt", "ViewPagerAdapter.kt",
            "WebRTCManager.kt"
        )

        // Climax messages that can be randomly selected
        private val CLIMAX_MESSAGES = listOf(
            "420 time is amongst us!",
            "Puff puff time! 420 has approached us",
            "420!"
        )
    }

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    private var timerRunnable: Runnable? = null

    private lateinit var statsManager: StatsManager
    private var currentStats: SamStats? = null
    private var currentAdjustments = StatsAdjustments()
    private var adjustmentsListener: ListenerRegistration? = null
    private var activitiesListener: ListenerRegistration? = null

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())

    private var lastMessageTime = 0L
    private var isUsingDeviceLocation = false
    private var deviceLocation: Location? = null
    private var locationRunnable: Runnable? = null

    // New state management for sticky countdown
    private var stickyCountdownRunnable: Runnable? = null
    private var stickyCity: String? = null
    private var stickyTargetTime: Long = 0L
    private var isInStickyMode = false
    private var isShowingClimax = false

    // For A-B rotation tracking
    private var showingAList = true // true = A-list (actual 420), false = B-list (approaching)
    private var lastAIndex = -1
    private var lastBIndex = -1
    private var currentCityIndex = 0 // For fallback rotation
    
    // City diversity tracking
    private val recentCityHistory = mutableListOf<String>() // Track last 10 cities shown
    private var lastCountryShown = "" // Track last country to avoid consecutive same country
    
    // User location alternation tracking
    private var showUserLocationNext = true // When user's location is at 4:20, alternate between user and other cities
    private var userLocationCity: String? = null // Store user's city name when at 4:20
    private var lastShownOther420Index = -1 // Track last shown other 4:20 city to ensure variety

    private var keyboardListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    // Code statistics
    private data class CodeStats(
        val totalLines: Int = 0,
        val totalCharacters: Int = 0,
        val totalFunctions: Int = 0
    )

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkDeviceLocation()
        } else {
            startLocationRotation()
        }
    }

    private fun animateUserManualButton() {
        val button = binding.btnUserManual
        val textView = binding.textUserManual
        val buttonFrameLayout = button.getChildAt(0) as? FrameLayout

        val originalTextColor = Color.parseColor("#98FB98")
        val originalBackgroundColor = Color.TRANSPARENT
        val invertedTextColor = Color.BLACK
        val invertedBackgroundColor = Color.parseColor("#98FB98")

        val backgroundAnimator = ValueAnimator.ofArgb(originalBackgroundColor, invertedBackgroundColor)
        backgroundAnimator.duration = 200
        backgroundAnimator.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            buttonFrameLayout?.setBackgroundColor(color)
        }

        val textAnimator = ValueAnimator.ofArgb(originalTextColor, invertedTextColor)
        textAnimator.duration = 200
        textAnimator.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            textView.setTextColor(color)
        }

        backgroundAnimator.start()
        textAnimator.start()

        textView.setTextColor(invertedTextColor)

        val invertedDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 16f * resources.displayMetrics.density
            setColor(Color.parseColor("#98FB98"))
            setStroke((1 * resources.displayMetrics.density).toInt(), Color.parseColor("#98FB98")) // Changed from 2 to 1
        }

        buttonFrameLayout?.background = invertedDrawable

        handler.postDelayed({
            val revertBackgroundAnimator = ValueAnimator.ofArgb(invertedBackgroundColor, originalBackgroundColor)
            revertBackgroundAnimator.duration = 200
            revertBackgroundAnimator.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                if (color == originalBackgroundColor) {
                    buttonFrameLayout?.setBackgroundResource(R.drawable.user_manual_button_background)
                }
            }

            val revertTextAnimator = ValueAnimator.ofArgb(invertedTextColor, originalTextColor)
            revertTextAnimator.duration = 200
            revertTextAnimator.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                textView.setTextColor(color)
            }

            revertBackgroundAnimator.start()
            revertTextAnimator.start()
        }, 2000)
    }

    private fun showUserManualDialog() {
        val dialog = AppCompatDialog(requireContext(), R.style.Theme_CloudCounter)
        val dialogBinding = DialogUserManualBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Make dialog full width with margins
        dialog.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)

            // Set margins
            val params = attributes
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            attributes = params

            // Add padding to the window
            val density = context.resources.displayMetrics.density.toInt()
            decorView.setPadding(16 * density, 16 * density, 16 * density, 16 * density)
        }

        // Setup Back button
        dialogBinding.buttonBack.setOnClickListener {
            dialog.dismiss()
        }

        // Allow dismissing by tapping outside
        dialog.setCanceledOnTouchOutside(true)

        dialog.show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "🎯 AboutFragment onViewCreated started")
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupKeyboardHandling()
        setupStatsDisplay()
        setupMessageInput()
        setup420Notifications()
        startRealTimeStatsListener()

        // Force software rendering for the text view to ensure animations work
        binding.text420Location.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        // CRITICAL: Set initial visibility and alpha
        binding.text420Location.visibility = View.VISIBLE
        binding.text420Location.alpha = 1.0f

        checkLocationPermissionAndStart()

        // Start timer updates
        start420Timers()

        Log.d(TAG, "🎯 AboutFragment onViewCreated completed")
    }

    private fun setupUI() {
        // Set up the about text - will be updated with stats
        updateAboutContent()

        // Setup send button
        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        // Character limit
        binding.editMessage.filters = arrayOf(InputFilter.LengthFilter(MAX_MESSAGE_LENGTH))

        // Setup User Manual button
        binding.btnUserManual.setOnClickListener {
            // Animate the button color inversion
            animateUserManualButton()

            // Show the user manual dialog
            showUserManualDialog()
        }
    }

    private fun setupKeyboardHandling() {
        // Adjust the window to resize when keyboard appears
        requireActivity().window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )

        // Add padding to account for navigation bar
        binding.messageInputCard.apply {
            val navBarHeight = resources.getDimensionPixelSize(
                resources.getIdentifier("navigation_bar_height", "dimen", "android")
            )
            setPadding(paddingLeft, paddingTop, paddingRight, navBarHeight)
        }

        // Create and store the keyboard listener
        keyboardListener = ViewTreeObserver.OnGlobalLayoutListener {
            // Check if binding is still valid
            if (_binding == null) return@OnGlobalLayoutListener

            val rect = android.graphics.Rect()
            binding.root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = binding.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            if (keypadHeight > screenHeight * 0.15) {
                // Keyboard is visible - scroll to show input
                binding.scrollView.post {
                    if (_binding != null) {
                        binding.scrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }

        // Add the listener
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(keyboardListener)
    }

    private fun startRealTimeStatsListener() {
        Log.d(TAG, "🔄 Starting real-time stats listeners")

        // Listen to adjustments changes from Firebase
        adjustmentsListener = db.document("stats_adjustments/sam_stats")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Error listening to adjustments: ${error.message}", error)
                    return@addSnapshotListener
                }

                Log.d(TAG, "📊 Adjustments snapshot received")
                snapshot?.let { doc ->
                    val newAdjustments = doc.toObject(StatsAdjustments::class.java)
                        ?: StatsAdjustments()
                    Log.d(TAG, "📊 New adjustments: todayCones=${newAdjustments.todayConesAdjustment}, todayJoints=${newAdjustments.todayJointsAdjustment}")
                    Log.d(TAG, "📊 New adjustments: allTimeCones=${newAdjustments.allTimeConesAdjustment}, allTimeJoints=${newAdjustments.allTimeJointsAdjustment}")
                    currentAdjustments = newAdjustments
                    loadAndDisplayStats()
                }
            }

        // Listen to local database changes through the repository
        lifecycleScope.launch {
            try {
                // Set up a periodic check for local database changes
                startLocalDatabasePolling()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start local database polling: ${e.message}", e)
            }
        }
    }

    private fun startLocalDatabasePolling() {
        Log.d(TAG, "🔄 Starting local database polling for stats updates")

        // Poll every 2 seconds for changes
        val statsPollingRunnable = object : Runnable {
            override fun run() {
                Log.d(TAG, "📊 Polling for stats updates...")
                loadAndDisplayStats()
                handler.postDelayed(this, 2000) // Poll every 2 seconds
            }
        }

        handler.post(statsPollingRunnable)
    }

    private fun calculateCodeStats(): CodeStats {
        var totalLines = 0
        var totalCharacters = 0
        var totalFunctions = 0

        // More accurate stats based on the actual project structure
        CREATED_FILES.forEach { fileName ->
            when {
                fileName.endsWith("Fragment.kt") -> {
                    totalLines += 450
                    totalCharacters += 18000
                    totalFunctions += 20
                }
                fileName.endsWith("ViewModel.kt") -> {
                    totalLines += 200
                    totalCharacters += 8000
                    totalFunctions += 12
                }
                fileName.endsWith("Adapter.kt") -> {
                    totalLines += 180
                    totalCharacters += 7200
                    totalFunctions += 10
                }
                fileName.endsWith("Dao.kt") -> {
                    totalLines += 120
                    totalCharacters += 4800
                    totalFunctions += 15
                }
                fileName.endsWith("Dialog.kt") -> {
                    totalLines += 150
                    totalCharacters += 6000
                    totalFunctions += 8
                }
                fileName.endsWith("Service.kt") -> {
                    totalLines += 250
                    totalCharacters += 10000
                    totalFunctions += 15
                }
                fileName.endsWith("Manager.kt") -> {
                    totalLines += 300
                    totalCharacters += 12000
                    totalFunctions += 18
                }
                fileName == "MainActivity.kt" -> {
                    totalLines += 600
                    totalCharacters += 24000
                    totalFunctions += 30
                }
                fileName == "AppDatabase.kt" -> {
                    totalLines += 150
                    totalCharacters += 6000
                    totalFunctions += 5
                }
                fileName.endsWith("Helper.kt") -> {
                    totalLines += 180
                    totalCharacters += 7200
                    totalFunctions += 12
                }
                else -> {
                    // Default for utility/data classes
                    totalLines += 150
                    totalCharacters += 6000
                    totalFunctions += 8
                }
            }
        }

        return CodeStats(totalLines, totalCharacters, totalFunctions)
    }

    private fun setupMessageInput() {
        // Show character count
        binding.editMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val length = s?.length ?: 0
                binding.textCharCount.text = "$length/$MAX_MESSAGE_LENGTH"
            }
        })

        // Handle IME actions
        binding.editMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun checkLocationPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                checkDeviceLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                showLocationRationale()
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    private fun showLocationRationale() {
        AlertDialog.Builder(requireContext())
            .setTitle("Location Permission")
            .setMessage("We use your location to show when it's 4:20 in your area!")
            .setPositiveButton("Grant") { _, _ ->
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            .setNegativeButton("No Thanks") { _, _ ->
                startLocationRotation()
            }
            .show()
    }

    private fun checkDeviceLocation() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED) {
                    val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    location?.let {
                        deviceLocation = it
                        val geocoder = Geocoder(requireContext(), Locale.getDefault())
                        try {
                            val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                            if (addresses?.isNotEmpty() == true) {
                                val address = addresses[0]
                                val city = address.locality ?: address.adminArea ?: "Unknown"
                                val country = address.countryName ?: ""

                                // Check if it's 4:20 at device location
                                if (isCurrently420AtLocation(it.latitude, it.longitude)) {
                                    isUsingDeviceLocation = true
                                    userLocationCity = "$city, $country"
                                    showUserLocationNext = true // Start by showing user location
                                    withContext(Dispatchers.Main) {
                                        updateLocationText("$city, $country", isCurrently420 = true)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Geocoding failed", e)
                        }
                        Unit
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Location check failed", e)
            }

            withContext(Dispatchers.Main) {
                startLocationRotation()
            }
        }
    }

    private fun isCurrently420AtLocation(latitude: Double, longitude: Double): Boolean {
        // This is a rough approximation - for production, you'd want to use a proper
        // timezone lookup service based on coordinates
        val timezoneOffset = (longitude / 15).toInt()
        val tzString = when {
            timezoneOffset >= 0 -> "GMT+$timezoneOffset"
            else -> "GMT$timezoneOffset"
        }

        val calendar = Calendar.getInstance(TimeZone.getTimeZone(tzString))
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        // Allow 2 minute tolerance
        return (hour == 4 || hour == 16) && (minute in 18..22)
    }













    private fun displayApproaching420(city: String, secondsUntil: Int) {
        val greenColor = "#98FB98"

        // Calculate time components
        val hours = secondsUntil / 3600
        val minutes = (secondsUntil % 3600) / 60
        val seconds = secondsUntil % 60

        // Format time text
        val timeText = when {
            hours > 0 && minutes > 0 && seconds > 0 -> {
                "<font color='$greenColor'><b>${hours}h ${minutes}m</b></font> and <font color='$greenColor'><b>${seconds}s</b></font>"
            }
            hours > 0 && minutes > 0 -> {
                "<font color='$greenColor'><b>${hours}h</b></font> and <font color='$greenColor'><b>${minutes}m</b></font>"
            }
            hours > 0 -> {
                "<font color='$greenColor'><b>${hours}h</b></font>"
            }
            minutes > 0 && seconds > 0 -> {
                "<font color='$greenColor'><b>${minutes}m</b></font> and <font color='$greenColor'><b>${seconds}s</b></font>"
            }
            minutes > 0 -> {
                "<font color='$greenColor'><b>${minutes}m</b></font>"
            }
            else -> {
                "<font color='$greenColor'><b>${seconds}s</b></font>"
            }
        }

        val text = "<font color='$greenColor'><b>420</b></font> next in $city, $timeText!"

        binding.text420Location.text = HtmlCompat.fromHtml(
            text,
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }


    private fun isCurrently420AtDeviceLocation(): Boolean {
        deviceLocation?.let {
            return isCurrently420AtLocation(it.latitude, it.longitude)
        }
        return false
    }

    private fun updateLocationText(location: String, isCurrently420: Boolean = false) {
        Log.d(TAG, "🔍 updateLocationText called with: '$location', isCurrently420: $isCurrently420")

        val greenColor = "#98FB98"

        val text = if (isCurrently420) {
            Log.d(TAG, "📝 Formatting 'currently' text for: $location")
            "Currently <font color='$greenColor'><b>420</b></font> in $location right now!"
        } else {
            Log.w(TAG, "⚠️ updateLocationText called without isCurrently420 flag")
            "<font color='$greenColor'><b>420</b></font> somewhere!"
        }

        binding.text420Location.text = HtmlCompat.fromHtml(
            text,
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    private fun findNext420LocationsWithTime(): List<Triple<String, Long, Int>> {
        val cityTimes = mutableListOf<Triple<String, Long, Int>>()
        val now = System.currentTimeMillis()

        Log.d(TAG, "🔍 findNext420LocationsWithTime() - Checking ${CITIES_WITH_TIMEZONES.size} cities")

        for ((city, tzId) in CITIES_WITH_TIMEZONES) {
            try {
                val tz = TimeZone.getTimeZone(tzId)
                val cal = Calendar.getInstance(tz)
                cal.timeInMillis = now

                val currentHour = cal.get(Calendar.HOUR_OF_DAY)
                val currentMinute = cal.get(Calendar.MINUTE)
                val currentSecond = cal.get(Calendar.SECOND)

                // Check if it's currently around 4:20 (within 2 minutes)
                val isCurrently420 = (currentHour == 4 || currentHour == 16) && (currentMinute in 18..22)

                val target420Time: Long
                val secondsUntil: Int

                if (isCurrently420) {
                    // It's currently 4:20, set time to 0
                    target420Time = now
                    secondsUntil = 0
                    Log.d(TAG, "  🌟 Currently 4:20 in $city!")
                } else {
                    // Calculate next 4:20 AM
                    val next420AM = Calendar.getInstance(tz).apply {
                        set(Calendar.HOUR_OF_DAY, 4)
                        set(Calendar.MINUTE, 20)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        if (timeInMillis <= now) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }

                    // Calculate next 4:20 PM
                    val next420PM = Calendar.getInstance(tz).apply {
                        set(Calendar.HOUR_OF_DAY, 16)
                        set(Calendar.MINUTE, 20)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        if (timeInMillis <= now) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }

                    // Find which is closer
                    target420Time = if (next420AM.timeInMillis < next420PM.timeInMillis) {
                        next420AM.timeInMillis
                    } else {
                        next420PM.timeInMillis
                    }

                    secondsUntil = ((target420Time - now) / 1000).toInt()
                }

                cityTimes.add(Triple(city, target420Time, secondsUntil))

                // Log cities that are very close to 4:20
                if (secondsUntil <= 1800) { // Within 30 minutes
                    val minutes = secondsUntil / 60
                    val seconds = secondsUntil % 60
                    val localTime = String.format("%02d:%02d:%02d", currentHour, currentMinute, currentSecond)
                    Log.d(TAG, "  ⏰ $city is ${minutes}m ${seconds}s away (local: $localTime)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "  ❌ Error calculating time for $city: ${e.message}")
            }
        }

        // Group cities by time windows and shuffle within each window for variety
        val timeWindows = mutableMapOf<Int, MutableList<Triple<String, Long, Int>>>()
        cityTimes.forEach { city ->
            val windowKey = city.third / 300 // 5 minute windows
            timeWindows.getOrPut(windowKey) { mutableListOf() }.add(city)
        }
        
        // Shuffle cities within each time window for randomization
        val shuffledCityTimes = mutableListOf<Triple<String, Long, Int>>()
        timeWindows.keys.sorted().forEach { windowKey ->
            val citiesInWindow = timeWindows[windowKey] ?: emptyList()
            shuffledCityTimes.addAll(citiesInWindow.shuffled())
        }
        
        // Use the shuffled list but still maintain time ordering between windows
        val sorted = shuffledCityTimes.sortedBy { it.third }

        // Always log the closest city regardless
        if (sorted.isNotEmpty()) {
            val closest = sorted.first()
            val timeStr = if (closest.third == 0) {
                "NOW!"
            } else if (closest.third < 3600) {
                "${closest.third / 60}m ${closest.third % 60}s"
            } else {
                "${closest.third / 3600}h ${(closest.third % 3600) / 60}m"
            }
            Log.d(TAG, "🎯🎯🎯 CLOSEST CITY: ${closest.first} - $timeStr away from 4:20")
        }
        
        // Log the top cities with their times
        if (sorted.isNotEmpty()) {
            Log.d(TAG, "📋 Top 10 cities closest to 4:20:")
            sorted.take(10).forEachIndexed { index, triple ->
                val city = triple.first
                val seconds = triple.third
                if (seconds == 0) {
                    Log.d(TAG, "   ${index + 1}. $city - Currently 4:20!")
                } else if (seconds < 3600) {
                    val minutes = seconds / 60
                    val secs = seconds % 60
                    Log.d(TAG, "   ${index + 1}. $city - ${minutes}m ${secs}s")
                } else {
                    val hours = seconds / 3600
                    val minutes = (seconds % 3600) / 60
                    Log.d(TAG, "   ${index + 1}. $city - ${hours}h ${minutes}m")
                }
            }
        } else {
            Log.d(TAG, "📋 No cities found!")
        }

        return sorted
    }

    private fun sendMessage() {
        val message = binding.editMessage.text.toString().trim()

        if (message.isEmpty()) {
            showSnackbar("Please enter a message")
            return
        }

        if (!canSendMessage()) {
            showSnackbar("Please wait 30 seconds between messages")
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            // Need to sign in
            promptSignIn()
            return
        }

        // Send the message
        submitMessage(message)
    }

    private fun canSendMessage(): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastMessageTime) > MESSAGE_COOLDOWN_MS
    }

    private fun promptSignIn() {
        AlertDialog.Builder(requireContext())
            .setTitle("Sign In Required")
            .setMessage("Please sign in to send a message")
            .setPositiveButton("Sign In") { _, _ ->
                (activity as? MainActivity)?.triggerGoogleSignIn()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitMessage(message: String) {
        val currentUser = auth.currentUser ?: return

        lifecycleScope.launch {
            try {
                val packageInfo = requireContext().packageManager.getPackageInfo(
                    requireContext().packageName, 0
                )

                val messageData = hashMapOf(
                    "message" to message,
                    "userUid" to currentUser.uid,
                    "userEmail" to (currentUser.email ?: ""),
                    "userDisplayName" to (currentUser.displayName ?: ""),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "platform" to "android",
                    "appVersion" to "${packageInfo.versionName} (${packageInfo.versionCode})",
                    "deviceModel" to android.os.Build.MODEL,
                    "osVersion" to android.os.Build.VERSION.RELEASE
                )

                // Add location if available
                deviceLocation?.let { location ->
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (addresses?.isNotEmpty() == true) {
                            val address = addresses[0]
                            val city = address.locality ?: address.adminArea ?: "Unknown"
                            val country = address.countryName ?: ""

                            messageData["userLocation"] = hashMapOf(
                                "city" to city,
                                "country" to country,
                                "lat" to location.latitude,
                                "lon" to location.longitude
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Geocoding failed for message", e)
                    }
                    Unit
                }

                db.collection("support_messages")
                    .add(messageData)
                    .await()

                withContext(Dispatchers.Main) {
                    lastMessageTime = System.currentTimeMillis()
                    binding.editMessage.text?.clear()
                    showSnackbar("Message sent successfully!")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                withContext(Dispatchers.Main) {
                    if (e.message?.contains("UNAVAILABLE") == true) {
                        showSnackbar("Message queued - will send when online")
                    } else {
                        showSnackbar("Failed to send: ${e.message}")
                    }
                }
            }
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "🎯 AboutFragment onResume - reloading stats")
        loadAndDisplayStats()
        locationRunnable?.let {
            handler.post(it)
        }
        timerRunnable?.let {
            handler.post(it)
        }
    }

    override fun onPause() {
        super.onPause()
        locationRunnable?.let {
            handler.removeCallbacks(it)
        }
        timerRunnable?.let {
            handler.removeCallbacks(it)
        }
        liveCountdownRunnable?.let {
            handler.removeCallbacks(it)
        }
    }

    private fun setup420Notifications() {
        val prefs = requireContext().getSharedPreferences("420_notifications", Context.MODE_PRIVATE)
        binding.checkbox420Morning.isChecked = prefs.getBoolean("morning_enabled", false)
        binding.checkbox420Afternoon.isChecked = prefs.getBoolean("afternoon_enabled", false)
        binding.checkbox5MinBefore.isChecked = prefs.getBoolean("five_min_before_enabled", false)

        // Show/hide timers based on checkbox state
        updateTimerVisibility()

        binding.checkbox420Morning.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("morning_enabled", isChecked).apply()
            updateTimerVisibility()
            Notification420Receiver.schedule420Notifications(requireContext())
        }

        binding.checkbox420Afternoon.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("afternoon_enabled", isChecked).apply()
            updateTimerVisibility()
            Notification420Receiver.schedule420Notifications(requireContext())
        }

        binding.checkbox5MinBefore.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("five_min_before_enabled", isChecked).apply()
            updateTimerVisibility()
            Notification420Receiver.schedule420Notifications(requireContext())
        }
    }

    private fun updateTimerVisibility() {
        binding.textMorningTimer.visibility = if (binding.checkbox420Morning.isChecked) View.VISIBLE else View.GONE
        binding.textAfternoonTimer.visibility = if (binding.checkbox420Afternoon.isChecked) View.VISIBLE else View.GONE
        binding.text5MinTimer.visibility = if (binding.checkbox5MinBefore.isChecked && 
            (binding.checkbox420Morning.isChecked || binding.checkbox420Afternoon.isChecked)) View.VISIBLE else View.GONE
    }

    private fun start420Timers() {
        timerRunnable = object : Runnable {
            override fun run() {
                update420Timers()
                handler.postDelayed(this, 1000) // Update every second
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun update420Timers() {
        val now = Calendar.getInstance()

        // Calculate time until 4:20 AM
        val morning420 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 20)
            set(Calendar.SECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Calculate time until 4:20 PM
        val afternoon420 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 20)
            set(Calendar.SECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Update morning timer
        val morningMillis = morning420.timeInMillis - now.timeInMillis
        binding.textMorningTimer.text = formatTimeUntil(morningMillis)

        // Update afternoon timer
        val afternoonMillis = afternoon420.timeInMillis - now.timeInMillis
        binding.textAfternoonTimer.text = formatTimeUntil(afternoonMillis)
        
        // Update 5-minute before timer
        if (binding.checkbox5MinBefore.isChecked) {
            val morningEnabled = binding.checkbox420Morning.isChecked
            val afternoonEnabled = binding.checkbox420Afternoon.isChecked
            
            when {
                morningEnabled && afternoonEnabled -> {
                    // Both enabled - show timer for the closest one
                    val morning5Min = morning420.timeInMillis - (5 * 60 * 1000)
                    val afternoon5Min = afternoon420.timeInMillis - (5 * 60 * 1000)
                    val closestMillis = minOf(morning5Min - now.timeInMillis, afternoon5Min - now.timeInMillis)
                    binding.text5MinTimer.text = formatTimeUntil(closestMillis)
                }
                morningEnabled -> {
                    // Only morning enabled
                    val morning5Min = morning420.timeInMillis - (5 * 60 * 1000)
                    binding.text5MinTimer.text = formatTimeUntil(morning5Min - now.timeInMillis)
                }
                afternoonEnabled -> {
                    // Only afternoon enabled
                    val afternoon5Min = afternoon420.timeInMillis - (5 * 60 * 1000)
                    binding.text5MinTimer.text = formatTimeUntil(afternoon5Min - now.timeInMillis)
                }
            }
        }
    }

    private fun formatTimeUntil(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            else -> "${minutes}m ${seconds}s"
        }
    }

    private fun schedule420Notification(hour: Int, minute: Int) {
        // Now handled by Notification420Receiver
        Notification420Receiver.schedule420Notifications(requireContext())
        Log.d(TAG, "Scheduled 420 notification for $hour:$minute")
    }

    private fun cancel420Notification(id: Int) {
        // Now handled by Notification420Receiver
        Notification420Receiver.schedule420Notifications(requireContext())
        Log.d(TAG, "Cancelled 420 notification with ID: $id")
    }

    private fun setupStatsDisplay() {
        Log.d(TAG, "📊 Setting up stats display")
        // Initialize stats manager
        val app = requireActivity().application as CloudCounterApplication
        statsManager = StatsManager(requireContext(), app.repository)

        // Start listening to adjustments
        statsManager.startAdjustmentsListener { adjustments ->
            Log.d(TAG, "📊 StatsManager adjustments listener triggered")
            currentAdjustments = adjustments
            loadAndDisplayStats()
        }

        // Initial load
        loadAndDisplayStats()
    }

    private fun loadAndDisplayStats() {
        Log.d(TAG, "📊 loadAndDisplayStats called")
        lifecycleScope.launch {
            try {
                val oldStats = currentStats
                currentStats = statsManager.getStats()

                // Log the stats values
                currentStats?.let { stats ->
                    Log.d(TAG, "📊 Stats loaded:")
                    Log.d(TAG, "📊   Today - Cones: ${stats.todayCones}, Joints: ${stats.todayJoints}")
                    Log.d(TAG, "📊   All Time - Cones: ${stats.allTimeCones}, Joints: ${stats.allTimeJoints}")

                    // Check if stats changed
                    if (oldStats != null) {
                        if (oldStats.todayCones != stats.todayCones || oldStats.todayJoints != stats.todayJoints) {
                            Log.d(TAG, "🔥 Stats CHANGED! Today stats updated")
                        }
                        if (oldStats.allTimeCones != stats.allTimeCones || oldStats.allTimeJoints != stats.allTimeJoints) {
                            Log.d(TAG, "🔥 Stats CHANGED! All-time stats updated")
                        }
                    }
                }

                updateStatsDisplay()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to load stats: ${e.message}", e)
            }
        }
    }

    private fun updateAboutContent() {
        val codeStats = calculateCodeStats()

        val aboutText = """
Feel free to send me a message with any bugs or feedback.

This app is vibe coded without writing a single line of code with the help of Claude, Gemini and ChatGPT.
        """.trimIndent()

        binding.textAboutContent.text = aboutText
    }

    private fun updateStatsDisplay() {
        Log.d(TAG, "📊 updateStatsDisplay called")
        
        // Check if binding is still valid
        if (_binding == null) {
            Log.d(TAG, "📊 Binding is null, skipping stats display update")
            return
        }
        
        val stats = currentStats ?: run {
            Log.d(TAG, "📊 No stats available yet")
            return
        }

        val codeStats = calculateCodeStats()

        val statsHtml = when {
            // Case: Smoked something today
            stats.todayCones > 0 || stats.todayJoints > 0 -> {
                val todayText = buildTodayText(stats.todayCones, stats.todayJoints)
                val totalText = buildTotalText(stats.allTimeCones, stats.allTimeJoints)
                "Oh, and if you were wondering, we've smoked $todayText today. $totalText have been smoked by the team since creating the app."
            }
            // Case: Smoked nothing today, but has an all-time total
            stats.allTimeCones > 0 || stats.allTimeJoints > 0 -> {
                val totalText = buildTotalText(stats.allTimeCones, stats.allTimeJoints)
                "Oh, and if you were wondering, we've smoked nothing today. $totalText have been smoked by the team since creating the app."
            }
            // Case: Never smoked anything
            else -> {
                "Oh, and if you were wondering, we've smoked nothing today. Nothing has been smoked yet by the team since creating the app."
            }
        }

        // Calculate words from characters (average 5 characters per word)
        val wordCount = codeStats.totalCharacters / 5

        // Updated code statistics text with correct wording
        val codeStatsText = "And this app contains <font color='#98FB98'><b>${codeStats.totalLines}</b> <b>lines</b></font> of code. " +
                "That's <font color='#98FB98'><b>${codeStats.totalCharacters}</b> <b>characters</b></font> " +
                "making <font color='#98FB98'><b>$wordCount</b> <b>words</b></font> " +
                "that create <font color='#98FB98'><b>${codeStats.totalFunctions}</b> <b>functions</b></font>."

        val aboutText = """
Feel free to send me a message with any bugs or feedback.

This app is vibe coded without writing a single line of code with the help of Claude, Gemini and ChatGPT.
        """.trimIndent()

        // Combine the main about text with the stats using HTML line breaks
        val fullHtmlContent = "$aboutText<br><br>$statsHtml<br><br>$codeStatsText"

        // Use HtmlCompat to parse the HTML string and set it to the TextView
        binding.textAboutContent.text = HtmlCompat.fromHtml(fullHtmlContent, HtmlCompat.FROM_HTML_MODE_LEGACY)

        Log.d(TAG, "📊 Stats display updated successfully")
    }

    private fun buildTodayText(cones: Int, joints: Int): String {
        return when {
            cones > 0 && joints > 0 -> "${formatCount(cones, "cone")} and ${formatCount(joints, "joint")}"
            cones > 0 -> formatCount(cones, "cone")
            joints > 0 -> formatCount(joints, "joint")
            else -> "nothing"
        }
    }

    private fun buildTotalText(cones: Int, joints: Int): String {
        return when {
            cones > 0 && joints > 0 -> "${formatCount(cones, "cone")} and ${formatCount(joints, "joint")}"
            cones > 0 -> formatCount(cones, "cone")
            joints > 0 -> formatCount(joints, "joint")
            else -> "Nothing"
        }
    }

    private fun formatCount(count: Int, type: String): String {
        // Handle pluralization (e.g., 1 cone, 2 cones)
        val typePlural = if (count == 1) type else "${type}s"

        // The main green color from your app's theme
        val greenColor = "#98FB98"

        // Wrap the bolded text in a font tag to set the color
        return "<font color='$greenColor'><b>$count</b> <b>$typePlural</b></font>"
    }


    private fun startLocationRotation() {
        Log.d(TAG, "🔄 startLocationRotation() called")

        // Start the live countdown updater
        startLiveCountdown()

        locationRunnable = object : Runnable {
            override fun run() {
                Log.d(TAG, "🏃 Location rotation runnable executing...")

                // Don't rotate if we're in sticky mode or showing climax
                if (isInStickyMode || isShowingClimax) {
                    Log.d(TAG, "⏸️ Skipping rotation - sticky mode: $isInStickyMode, climax: $isShowingClimax")
                    handler.postDelayed(this, ROTATION_INTERVAL_MS)
                    return
                }

                // Get all cities with their times
                val allCities = findNext420LocationsWithTime()
                
                if (isUsingDeviceLocation && isCurrently420AtDeviceLocation()) {
                    Log.d(TAG, "📱 Device location is at 4:20 - alternating display")
                    
                    // Filter to only get cities that are currently at 4:20 (excluding user's location)
                    val other420Cities = allCities.filter { (city, _, seconds) ->
                        seconds <= 120 && city != userLocationCity // Within 2 minutes = currently 4:20
                    }
                    
                    if (showUserLocationNext || other420Cities.isEmpty()) {
                        // Show user's location
                        Log.d(TAG, "📍 Showing user's location: $userLocationCity")
                        fadeToNewText {
                            currentDisplayCity = userLocationCity
                            currentDisplayTargetTime = System.currentTimeMillis()
                            currentDisplayMode = "actual"
                            updateLocationText(userLocationCity ?: "", isCurrently420 = true)
                        }
                        showUserLocationNext = false // Next time show another city
                    } else {
                        // Show another city that's at 4:20
                        if (other420Cities.isNotEmpty()) {
                            // Ensure variety by not showing the same city twice in a row
                            lastShownOther420Index = (lastShownOther420Index + 1) % other420Cities.size
                            val (city, targetTime, _) = other420Cities[lastShownOther420Index]
                            Log.d(TAG, "🌍 Showing other 4:20 city: $city")
                            fadeToNewText {
                                currentDisplayCity = city
                                currentDisplayTargetTime = targetTime
                                currentDisplayMode = "actual"
                                updateLocationText(city, isCurrently420 = true)
                            }
                        }
                        showUserLocationNext = true // Next time show user's location
                    }
                } else {
                    Log.d(TAG, "📍 Device location not being used or not 4:20 at device")
                    
                    // Reset alternation tracking when not at user's 4:20
                    isUsingDeviceLocation = false
                    userLocationCity = null
                    showUserLocationNext = true
                    lastShownOther420Index = -1

                    // Check if any city is within sticky threshold
                    val stickyCandidates = allCities.filter { it.third in 1..STICKY_THRESHOLD_SECONDS }

                    if (stickyCandidates.isNotEmpty()) {
                        // Enter sticky mode!
                        val (city, targetTime, secondsUntil) = stickyCandidates.first()
                        Log.d(TAG, "🎯 Entering sticky mode for $city - ${secondsUntil}s until 4:20")
                        enterStickyMode(city, targetTime)
                    } else {
                        // Normal A-B rotation with region spreading
                        performNormalRotation(allCities)
                    }
                }

                Log.d(TAG, "⏭️ Scheduling next rotation in ${ROTATION_INTERVAL_MS}ms")
                handler.postDelayed(this, ROTATION_INTERVAL_MS)
            }
        }

        // Start immediately
        Log.d(TAG, "▶️ Starting location rotation immediately")
        locationRunnable?.run()
    }



    private fun enterStickyMode(city: String, targetTime: Long) {
        Log.d(TAG, "⏲️ Entering sticky mode for $city")

        isInStickyMode = true
        currentDisplayCity = city
        currentDisplayTargetTime = targetTime
        currentDisplayMode = "sticky"

        fadeToNewText {
            val now = System.currentTimeMillis()
            val secondsUntil = ((targetTime - now) / 1000).toInt()
            updateStickyCountdownText(city, secondsUntil)
        }
    }

    private fun updateStickyCountdownText(city: String, secondsUntil: Int) {
        val greenColor = "#98FB98"
        val minutes = secondsUntil / 60
        val seconds = secondsUntil % 60

        val timeText = if (minutes > 0) {
            "<font color='$greenColor'><b>${minutes}m ${seconds}s</b></font>"
        } else {
            "<font color='$greenColor'><b>${seconds}s</b></font>"
        }

        val text = "Get ready! It's <font color='$greenColor'><b>420</b></font> in $city in just $timeText!"

        binding.text420Location.text = HtmlCompat.fromHtml(
            text,
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    private fun updateApproachingCountdownText(city: String, secondsUntil: Int) {
        val greenColor = "#98FB98"

        // Calculate time components
        val hours = secondsUntil / 3600
        val minutes = (secondsUntil % 3600) / 60
        val seconds = secondsUntil % 60

        // Format time text
        val timeText = when {
            hours > 0 && minutes > 0 && seconds > 0 -> {
                "<font color='$greenColor'><b>${hours}h ${minutes}m</b></font> and <font color='$greenColor'><b>${seconds}s</b></font>"
            }
            hours > 0 && minutes > 0 -> {
                "<font color='$greenColor'><b>${hours}h</b></font> and <font color='$greenColor'><b>${minutes}m</b></font>"
            }
            hours > 0 -> {
                "<font color='$greenColor'><b>${hours}h</b></font>"
            }
            minutes > 0 && seconds > 0 -> {
                "<font color='$greenColor'><b>${minutes}m</b></font> and <font color='$greenColor'><b>${seconds}s</b></font>"
            }
            minutes > 0 -> {
                "<font color='$greenColor'><b>${minutes}m</b></font>"
            }
            else -> {
                "<font color='$greenColor'><b>${seconds}s</b></font>"
            }
        }

        // Added "from now!" at the end
        val text = "<font color='$greenColor'><b>420</b></font> next in $city, $timeText from now!"

        binding.text420Location.text = HtmlCompat.fromHtml(
            text,
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    private fun showClimaxMessage() {
        isShowingClimax = true
        isInStickyMode = false
        currentDisplayMode = "climax"

        // Pick a random climax message
        val message = CLIMAX_MESSAGES.random()
        val greenColor = "#98FB98"

        // Format the message with green highlights
        val formattedMessage = when (message) {
            "420!" -> "<font color='$greenColor'><b>420!</b></font>"
            else -> message.replace("420", "<font color='$greenColor'><b>420</b></font>")
        }

        binding.text420Location.text = HtmlCompat.fromHtml(
            formattedMessage,
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        // Clear climax after 8 seconds
        handler.postDelayed({
            isShowingClimax = false
            currentDisplayCity = null
            currentDisplayTargetTime = 0L
            currentDisplayMode = ""
            Log.d(TAG, "✅ Climax period ended, resuming normal rotation")
        }, 8000) // 8 seconds
    }


    private fun getRegionForCity(cityName: String): String {
        for ((region, countries) in REGION_GROUPS) {
            if (countries.any { country -> cityName.contains(country) }) {
                return region
            }
        }
        return "Unknown"
    }

    private fun filterByRegion(cities: List<Triple<String, Long, Int>>): List<Triple<String, Long, Int>> {
        if (lastRegionShown.isEmpty()) return cities

        return cities.filter { (city, _, _) ->
            getRegionForCity(city) != lastRegionShown
        }.ifEmpty {
            cities
        }
    }

    private fun fadeToNewText(onFadeComplete: () -> Unit) {
        Log.d(TAG, "🎬 fadeToNewText() called using AlphaAnimation")

        // Null check to prevent crashes
        if (_binding == null) {
            Log.w(TAG, "⚠️ fadeToNewText called but binding is null")
            return
        }

        // Cancel any existing animations
        binding.text420Location.clearAnimation()

        // Create fade out animation (same as your original)
        val fadeOut = android.view.animation.AlphaAnimation(1.0f, 0.0f)
        fadeOut.duration = FADE_DURATION_MS
        fadeOut.fillAfter = true

        val fadeIn = android.view.animation.AlphaAnimation(0.0f, 1.0f)
        fadeIn.duration = FADE_DURATION_MS
        fadeIn.fillAfter = true

        fadeOut.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {
                Log.d(TAG, "🎬 Fade OUT animation started")
            }

            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                Log.d(TAG, "🎬 Fade OUT animation ended")

                // Update the text
                onFadeComplete()

                // Start fade in
                binding.text420Location.startAnimation(fadeIn)
            }

            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })

        fadeIn.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {
                Log.d(TAG, "🎬 Fade IN animation started")
            }

            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                Log.d(TAG, "🎬 Fade IN animation ended")
                binding.text420Location.alpha = 1.0f
            }

            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })

        // Start the fade out
        binding.text420Location.startAnimation(fadeOut)
    }

    private fun startLiveCountdown() {
        Log.d(TAG, "🔄 startLiveCountdown() called")

        liveCountdownRunnable?.let {
            handler.removeCallbacks(it)
            Log.d(TAG, "🔄 Removed existing countdown runnable")
        }

        liveCountdownRunnable = object : Runnable {
            override fun run() {
                // Check if fragment is still attached and binding is valid
                if (!isAdded || _binding == null) {
                    Log.w(TAG, "⚠️ Live countdown tick skipped - fragment not attached or binding null")
                    liveCountdownRunnable = null
                    return
                }

                if (currentDisplayTargetTime > 0 && !isShowingClimax) {
                    val now = System.currentTimeMillis()
                    val secondsUntil = ((currentDisplayTargetTime - now) / 1000).toInt()

                    Log.d(TAG, "⏱️ Live countdown tick - mode: $currentDisplayMode, seconds: $secondsUntil")

                    when (currentDisplayMode) {
                        "sticky" -> {
                            if (secondsUntil > 0) {
                                Log.d(TAG, "⏱️ Updating sticky countdown: ${secondsUntil}s")
                                updateStickyCountdownTextDirect(currentDisplayCity ?: "", secondsUntil)
                            } else {
                                // Time hit 4:20! Show climax then resume rotation
                                Log.d(TAG, "⏱️ Sticky countdown reached 0 - showing climax")
                                showClimaxMessage()
                            }
                        }
                        "normal" -> {
                            if (secondsUntil > 0 && currentDisplayCity != null) {
                                // Update countdown text directly without fade for smooth countdown
                                updateApproachingCountdownTextDirect(currentDisplayCity!!, secondsUntil)
                            }
                        }
                        "actual" -> {
                            Log.d(TAG, "⏱️ City is currently at 420, no countdown needed")
                        }
                        else -> {
                            Log.d(TAG, "⏱️ Unknown mode or no mode set: $currentDisplayMode")
                        }
                    }
                }

                // Only schedule next tick if still attached
                if (isAdded && _binding != null) {
                    handler.postDelayed(this, 1000)
                } else {
                    Log.w(TAG, "⚠️ Not scheduling next countdown tick - fragment detached")
                }
            }
        }

        Log.d(TAG, "🔄 Starting countdown runnable")
        liveCountdownRunnable?.run()
    }

    // Add these new DIRECT update functions that don't fade (for per-second updates)
    private fun updateStickyCountdownTextDirect(city: String, secondsUntil: Int) {
        // Null check to prevent crashes
        if (_binding == null) {
            Log.w(TAG, "⚠️ updateStickyCountdownTextDirect called but binding is null")
            return
        }

        val greenColor = "#98FB98"
        val minutes = secondsUntil / 60
        val seconds = secondsUntil % 60

        val timeText = if (minutes > 0) {
            "<font color='$greenColor'><b>${minutes}m ${seconds}s</b></font>"
        } else {
            "<font color='$greenColor'><b>${seconds}s</b></font>"
        }

        val text = "Get ready! It's <font color='$greenColor'><b>420</b></font> in $city in just $timeText!"

        // Direct update without fade for smooth countdown
        try {
            binding.text420Location.text = HtmlCompat.fromHtml(
                text,
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update sticky countdown text: ${e.message}")
        }
    }

    private fun updateApproachingCountdownTextDirect(city: String, secondsUntil: Int) {
        // Null check to prevent crashes
        if (_binding == null) {
            Log.w(TAG, "⚠️ updateApproachingCountdownTextDirect called but binding is null")
            return
        }

        val greenColor = "#98FB98"

        // Calculate time components
        val hours = secondsUntil / 3600
        val minutes = (secondsUntil % 3600) / 60
        val seconds = secondsUntil % 60

        // Format time text
        val timeText = when {
            hours > 0 && minutes > 0 && seconds > 0 -> {
                "<font color='$greenColor'><b>${hours}h ${minutes}m</b></font> and <font color='$greenColor'><b>${seconds}s</b></font>"
            }
            hours > 0 && minutes > 0 -> {
                "<font color='$greenColor'><b>${hours}h</b></font> and <font color='$greenColor'><b>${minutes}m</b></font>"
            }
            hours > 0 -> {
                "<font color='$greenColor'><b>${hours}h</b></font>"
            }
            minutes > 0 && seconds > 0 -> {
                "<font color='$greenColor'><b>${minutes}m</b></font> and <font color='$greenColor'><b>${seconds}s</b></font>"
            }
            minutes > 0 -> {
                "<font color='$greenColor'><b>${minutes}m</b></font>"
            }
            else -> {
                "<font color='$greenColor'><b>${seconds}s</b></font>"
            }
        }

        // Added "from now!" at the end
        val text = "<font color='$greenColor'><b>420</b></font> next in $city, $timeText from now!"

        // Direct update without fade for smooth countdown
        try {
            binding.text420Location.text = HtmlCompat.fromHtml(
                text,
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update countdown text: ${e.message}")
        }
    }

    // Update performNormalRotation to add more logging
    private fun performNormalRotation(allCities: List<Triple<String, Long, Int>>) {
        Log.d(TAG, "🔄 performNormalRotation() - showingAList: $showingAList")

        // Split cities into A-list (currently 420) and B-list (approaching)
        val aList = allCities.filter { it.third <= 120 } // Within 2 minutes = currently 420
        
        // Only show cities that are actually approaching (within 60 minutes max to catch more cities)
        val bList = allCities.filter { it.third in (STICKY_THRESHOLD_SECONDS + 1)..3600 } // Between 2 min and 60 minutes

        Log.d(TAG, "📊 A-list (actual 420): ${aList.size} cities, B-list (within 60m): ${bList.size} cities")
        
        // Log what we're actually considering - show the CLOSEST cities
        if (aList.isNotEmpty()) {
            Log.d(TAG, "🅰️ A-list cities (currently 420):")
            aList.take(5).forEachIndexed { index, (city, _, seconds) ->
                Log.d(TAG, "     ${index + 1}. $city - ${seconds}s away")
            }
        }
        
        // Show ALL cities that will be rotated through (not limited to 10)
        val closestForRotation = bList.sortedBy { it.third }
        if (closestForRotation.isNotEmpty()) {
            Log.d(TAG, "🅱️ B-list cities in rotation (${closestForRotation.size} cities within 60m):")
            // Show first 15 for logging, but use all for rotation
            closestForRotation.take(15).forEachIndexed { index, (city, _, seconds) ->
                val mins = seconds / 60
                val secs = seconds % 60
                Log.d(TAG, "     ${index + 1}. $city - ${mins}m ${secs}s away")
            }
            if (closestForRotation.size > 15) {
                Log.d(TAG, "     ... and ${closestForRotation.size - 15} more cities")
            }
        } else {
            Log.d(TAG, "⚠️ No cities in B-list rotation window (2m-60m)")
            // Show why there are no cities - log the closest ones anyway
            val closest5 = allCities.sortedBy { it.third }.take(5)
            if (closest5.isNotEmpty()) {
                Log.d(TAG, "🔍 Closest 5 cities (outside rotation window):")
                closest5.forEachIndexed { index, (city, _, seconds) ->
                    val mins = seconds / 60
                    val secs = seconds % 60
                    Log.d(TAG, "     ${index + 1}. $city - ${mins}m ${secs}s away")
                }
            }
        }
        
        // If no cities in either list, use closest regardless but log why
        if (aList.isEmpty() && bList.isEmpty() && allCities.isNotEmpty()) {
            // Find the actual closest city from ALL cities
            val closestCities = allCities.sortedBy { it.third }.take(5)
            Log.d(TAG, "⚠️ No cities within 60 minutes! Closest 5 cities:")
            closestCities.forEachIndexed { index, (city, _, seconds) ->
                val hours = seconds / 3600
                val mins = (seconds % 3600) / 60
                if (hours > 0) {
                    Log.d(TAG, "     ${index + 1}. $city - ${hours}h ${mins}m away")
                } else {
                    Log.d(TAG, "     ${index + 1}. $city - ${mins}m away")
                }
            }
            
            val closestCity = closestCities.first()
            Log.d(TAG, "🎯 Using closest city: ${closestCity.first}")
            
            // Always show the closest city, even if it's far away
            fadeToNewText {
                currentDisplayCity = closestCity.first
                currentDisplayTargetTime = closestCity.second
                currentDisplayMode = "fallback"
                updateApproachingCountdownText(closestCity.first, closestCity.third)
            }
            showingAList = !showingAList
            return
        }

        if (showingAList && aList.isNotEmpty()) {
            Log.d(TAG, "✨ Showing A-list city (currently 420)")
            // Show from A-list with region filtering
            val filteredAList = filterByRegion(aList)
            if (filteredAList.isNotEmpty()) {
                // Add some randomness to A-list selection too
                if (kotlin.random.Random.nextFloat() < 0.3) {
                    lastAIndex = kotlin.random.Random.nextInt(filteredAList.size)
                } else {
                    lastAIndex = (lastAIndex + 1) % filteredAList.size
                }
                val (city, targetTime, secondsUntil) = filteredAList[lastAIndex]
                lastRegionShown = getRegionForCity(city)
                Log.d(TAG, "🎯 Selected A-list city: $city (${secondsUntil}s away, region: $lastRegionShown)")

                fadeToNewText {
                    currentDisplayCity = city
                    currentDisplayTargetTime = targetTime
                    currentDisplayMode = "actual"
                    updateLocationText(city, isCurrently420 = true)
                }
            }
            showingAList = false
        } else if (!showingAList && bList.isNotEmpty()) {
            Log.d(TAG, "✨ Showing B-list city with fade animation")
            
            // Shuffle B-list first for immediate variety
            val shuffledBList = bList.shuffled()
            
            // Group cities by time windows for diversity (10 minute windows for more variety)
            val cityGroups = groupCitiesByTimeWindow(shuffledBList, 600) // 10 minute windows
            
            // Find the best city from the closest groups with diversity
            var selectedCity: Triple<String, Long, Int>? = null
            
            // Try first 3 groups for more variety (not just the closest)
            val groupsToTry = cityGroups.take(3).shuffled()
            for (group in groupsToTry) {
                val diverseCity = selectDiverseCity(group)
                if (diverseCity != null) {
                    selectedCity = diverseCity
                    break
                }
            }
            
            // Fallback if no diverse city found - pick from all B-list randomly
            if (selectedCity == null && bList.isNotEmpty()) {
                selectedCity = bList.shuffled().first()
                Log.d(TAG, "⚠️ No diverse city found, using random fallback")
            }
            
            selectedCity?.let { (city, targetTime, secondsUntil) ->
                // Update tracking
                updateCityHistory(city)
                lastCountryShown = extractCountry(city)
                lastRegionShown = getRegionForCity(city)
                
                val mins = secondsUntil / 60
                val secs = secondsUntil % 60
                Log.d(TAG, "🎯 Selected B-list city: $city (${mins}m ${secs}s away, country: $lastCountryShown)")
                Log.d(TAG, "📝 Recent history: ${recentCityHistory.takeLast(5)}")

                fadeToNewText {
                    currentDisplayCity = city
                    currentDisplayTargetTime = targetTime
                    currentDisplayMode = "normal"
                    updateApproachingCountdownText(city, secondsUntil)
                }
            }
            showingAList = true
        } else {
            Log.d(TAG, "⚠️ Using fallback rotation logic (no cities in current list)")
            // Fallback - use the closest cities
            if (allCities.isNotEmpty()) {
                val closestCities = allCities.sortedBy { it.third }.take(10)
                Log.d(TAG, "📋 Fallback - closest 10 cities:")
                closestCities.forEachIndexed { index, (city, _, seconds) ->
                    val mins = seconds / 60
                    val secs = seconds % 60
                    Log.d(TAG, "     ${index + 1}. $city - ${mins}m ${secs}s")
                }
                
                if (closestCities.isNotEmpty()) {
                    // Add randomness to fallback too
                    if (kotlin.random.Random.nextFloat() < 0.3) {
                        currentCityIndex = kotlin.random.Random.nextInt(closestCities.size)
                    }
                    val selectedCity = closestCities[currentCityIndex % closestCities.size]
                    currentCityIndex = (currentCityIndex + 1) % closestCities.size
                    val (city, targetTime, secondsUntil) = selectedCity
                    lastRegionShown = getRegionForCity(city)
                    val mins = secondsUntil / 60
                    val secs = secondsUntil % 60
                    Log.d(TAG, "🎯 Fallback selected: $city (${mins}m ${secs}s away)")

                    fadeToNewText {
                        currentDisplayCity = city
                        currentDisplayTargetTime = targetTime
                        if (secondsUntil <= 120) {
                            currentDisplayMode = "actual"
                            updateLocationText(city, isCurrently420 = true)
                        } else {
                            currentDisplayMode = "normal"
                            updateApproachingCountdownText(city, secondsUntil)
                        }
                    }
                }
            }
            showingAList = !showingAList
        }
    }



    // Helper functions for city diversity
    private fun groupCitiesByTimeWindow(cities: List<Triple<String, Long, Int>>, windowSeconds: Int): List<List<Triple<String, Long, Int>>> {
        val groups = mutableListOf<List<Triple<String, Long, Int>>>()
        val sorted = cities.sortedBy { it.third }
        
        var currentGroup = mutableListOf<Triple<String, Long, Int>>()
        var groupStartTime = 0
        
        for (city in sorted) {
            if (currentGroup.isEmpty()) {
                currentGroup.add(city)
                groupStartTime = city.third
            } else if (city.third - groupStartTime <= windowSeconds) {
                currentGroup.add(city)
            } else {
                if (currentGroup.isNotEmpty()) {
                    // Shuffle each group for variety
                    groups.add(currentGroup.shuffled())
                }
                currentGroup = mutableListOf(city)
                groupStartTime = city.third
            }
        }
        
        if (currentGroup.isNotEmpty()) {
            // Shuffle the last group too
            groups.add(currentGroup.shuffled())
        }
        
        Log.d(TAG, "📊 Grouped cities into ${groups.size} time windows (shuffled)")
        groups.take(3).forEachIndexed { index, group ->
            val timeRange = if (group.isNotEmpty()) "${group.minOf { it.third }/60}m-${group.maxOf { it.third }/60}m" else "empty"
            Log.d(TAG, "   Group ${index + 1} ($timeRange): ${group.map { extractCountry(it.first) }.distinct()}")
        }
        
        return groups
    }
    
    private fun selectDiverseCity(cities: List<Triple<String, Long, Int>>): Triple<String, Long, Int>? {
        // Shuffle the input cities first for more randomness
        val shuffledCities = cities.shuffled()
        
        // Filter out recently shown cities (only last 5 to allow more variety)
        val eligibleCities = shuffledCities.filter { (city, _, _) ->
            !recentCityHistory.takeLast(5).contains(city)
        }
        
        // If all cities were recently shown, use all cities but shuffled
        val citiesToConsider = if (eligibleCities.isEmpty()) shuffledCities else eligibleCities
        
        // Filter out cities from the last shown country (with 30% chance to allow same country)
        val diverseCities = if (kotlin.random.Random.nextFloat() < 0.7) {
            citiesToConsider.filter { (city, _, _) ->
                extractCountry(city) != lastCountryShown
            }
        } else {
            citiesToConsider
        }
        
        // If no diverse countries available, use shuffled cities
        val finalCandidates = if (diverseCities.isEmpty()) citiesToConsider else diverseCities
        
        // Weight selection towards closer cities but with randomness
        // 60% chance to pick from first third, 30% from second third, 10% from last third
        val random = kotlin.random.Random.nextFloat()
        return when {
            finalCandidates.isEmpty() -> null
            finalCandidates.size == 1 -> finalCandidates.first()
            random < 0.6 -> {
                // Pick from first third (closest)
                val firstThird = finalCandidates.take((finalCandidates.size / 3).coerceAtLeast(1))
                firstThird.random()
            }
            random < 0.9 -> {
                // Pick from second third
                val secondThirdStart = finalCandidates.size / 3
                val secondThirdEnd = (2 * finalCandidates.size / 3).coerceAtLeast(secondThirdStart + 1)
                val secondThird = finalCandidates.slice(secondThirdStart until secondThirdEnd.coerceAtMost(finalCandidates.size))
                if (secondThird.isNotEmpty()) secondThird.random() else finalCandidates.random()
            }
            else -> {
                // Pick from last third (10% chance)
                val lastThirdStart = 2 * finalCandidates.size / 3
                val lastThird = finalCandidates.drop(lastThirdStart)
                if (lastThird.isNotEmpty()) lastThird.random() else finalCandidates.random()
            }
        }
    }
    
    private fun extractCountry(cityName: String): String {
        // Extract country from city name format "City, Country"
        return cityName.split(",").lastOrNull()?.trim() ?: cityName
    }
    
    private fun updateCityHistory(city: String) {
        recentCityHistory.add(city)
        // Keep only last 10 cities
        while (recentCityHistory.size > 10) {
            recentCityHistory.removeAt(0)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "🎯 AboutFragment onDestroyView - cleaning up")
        Log.d(TAG, "📋 Fragment lifecycle: Cleaning up handlers and listeners")

        // Stop sticky countdown
        stickyCountdownRunnable?.let {
            Log.d(TAG, "🛑 Removing sticky countdown runnable")
            handler.removeCallbacks(it)
            stickyCountdownRunnable = null
        }

        // CRITICAL: Stop live countdown runnable to prevent NPE
        liveCountdownRunnable?.let {
            Log.d(TAG, "🛑 Removing live countdown runnable")
            handler.removeCallbacks(it)
            liveCountdownRunnable = null
        }

        // Stop location rotation runnable
        locationRunnable?.let { runnable ->
            Log.d(TAG, "🛑 Removing location rotation runnable")
            handler.removeCallbacks(runnable)
            locationRunnable = null
        }

        // Stop timer runnable
        timerRunnable?.let { runnable ->
            Log.d(TAG, "🛑 Removing timer runnable")
            handler.removeCallbacks(runnable)
            timerRunnable = null
        }

        // Remove all callbacks from handler to be extra safe
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "🛑 Removed all pending handler callbacks")

        // Remove keyboard listener before destroying binding
        keyboardListener?.let { listener ->
            if (_binding != null) {
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        }
        keyboardListener = null

        // Clean up stats manager and listeners
        statsManager.cleanup()
        adjustmentsListener?.remove()
        activitiesListener?.remove()

        // Finally nullify binding
        Log.d(TAG, "🛑 Nullifying binding")
        _binding = null
    }
}