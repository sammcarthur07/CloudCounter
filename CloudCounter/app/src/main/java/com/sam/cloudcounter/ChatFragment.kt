package com.sam.cloudcounter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.sam.cloudcounter.databinding.FragmentChatBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.*
import kotlinx.coroutines.withContext
import android.content.Context
import android.widget.ImageButton
import kotlinx.coroutines.isActive
import android.widget.ImageView
import android.animation.*
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.animation.DecelerateInterpolator
import androidx.cardview.widget.CardView
import kotlin.math.min
import java.util.UUID
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.graphics.Rect
import android.view.ViewTreeObserver


class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var authManager: FirebaseAuthManager
    private lateinit var messageAdapter: ChatMessageAdapter
    private lateinit var reportHelper: ReportHelper
    private lateinit var chatDao: ChatDao

    //  a member variable to hold the current dialog instance
    private var currentDialog: Dialog? = null

    // Add new navigation states
    private enum class NavigationState {
        MAIN_MENU, CHAT_TYPE_MENU, TEXT_CHAT, VIDEO_CHAT,
        SESH_LOGS, PUBLIC_LOGS  // NEW states
    }

    // Add new properties for logs
    private var currentLogType: LogType = LogType.NONE
    private var logMessageAdapter: ChatMessageAdapter? = null

    private enum class LogType {
        NONE, SESH_LOGS, PUBLIC_LOGS
    }

    // Add animation handlers
    private val fadeHandler = Handler(Looper.getMainLooper())
    private val throbHandlers = mutableListOf<Handler>()

    private var editingMessage: ChatMessage? = null
    private var isEditMode = false

    private var presenceRefreshJob: Job? = null

    private var notificationsEnabled = false
    private lateinit var notificationHelper: NotificationHelper
    private val PREF_NOTIFICATIONS = "chat_notifications"

    private var currentChatRoom: String? = null
    private var currentChatType: ChatType = ChatType.NONE
    private val likedMessageIds = mutableSetOf<String>()

    private var shouldRestoreChat = false

    private var confettiHelper: ConfettiHelper? = null

    fun setConfettiHelper(helper: ConfettiHelper) {
        this.confettiHelper = helper
    }

    // Video chat properties
    private var webRTCManager: WebRTCManager? = null
    private var signalingService: VideoSignalingService? = null
    private val videoViews = mutableMapOf<String, SurfaceViewRenderer>()
    private var videoCallJob: Job? = null
    private val participantNames = mutableMapOf<String, String>()
    private var isVideoPaused = false

    private val locallyDeletedMessageIds = mutableSetOf<String>()

    private var keyboardListenerRegistered = false
    private var keyboardLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var lastKeyboardHeight = -1 // Track last keyboard height to prevent excessive recalculations
    private var lastButtonSectionY = -1f // Track last button section position

    private var currentNavState = NavigationState.MAIN_MENU

    enum class ChatType {
        NONE, SESH_CHAT, PUBLIC_CHAT
    }

    companion object {
        private const val TAG = "ChatFragment"
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)

        // Soft input mode will be set in adjustInputPaddingForLayoutPosition()
        // based on whether section is at top or bottom

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authManager = (requireActivity().application as CloudCounterApplication).authManager
        chatViewModel = ViewModelProvider(this).get(ChatViewModel::class.java)
        reportHelper = ReportHelper(requireContext())
        chatDao = AppDatabase.getDatabase(requireContext()).chatDao()
        notificationHelper = NotificationHelper(requireContext()) // Add this line

        setupUI()
        observeViewModel()
        checkAuthStatus()
        loadUserLikes()
        createStyledMainMenuButtons()
        setupLogInput()
        
        // Adjust input padding based on layout position
        adjustInputPaddingForLayoutPosition()
        
        // Setup keyboard visibility detection
        setupKeyboardVisibilityDetection()
    }



    private fun createStyledMainMenuButtons() {
        Log.d(TAG, "🔨 DEBUG: createStyledMainMenuButtons() called")

        // Create Sesh Chat button
        val seshChatButton = createChatButton(
            emoji = "💬",
            title = "Sesh Chat",
            subtitle = "Join session chat",
            isPrimary = true,
            enableThrob = true
        ) {
            Log.d(TAG, "🔨 DEBUG: Sesh Chat button onClick triggered")

            // Check for active session BEFORE animation
            val mainActivity = requireActivity() as? MainActivity
            val shareCode = mainActivity?.getCurrentShareCode()

            if (shareCode == null) {
                Log.d(TAG, "🔨 DEBUG: No active session - showing dialog, NOT animating")
                showNoActiveSeshDialog()
                return@createChatButton  // Don't animate
            }

            // Only animate if we have a valid session
            Log.d(TAG, "🔨 DEBUG: Active session found - proceeding with animation")
            performButtonAnimation {
                Log.d(TAG, "🔨 DEBUG: Sesh Chat animation complete callback")
                enterSeshChat()
            }
        }
        binding.btnSeshChatContainer.addView(seshChatButton)

        val seshLogsButton = createChatButton(
            emoji = "📋",
            title = "Logs",
            subtitle = "View history",
            isPrimary = false,
            enableThrob = false
        ) {
            Log.d(TAG, "🔨 DEBUG: Sesh Logs button onClick triggered")
            performButtonAnimation {
                Log.d(TAG, "🔨 DEBUG: Sesh Logs animation complete callback")
                showSeshLogs()
            }
        }
        binding.btnSeshLogsContainer.addView(seshLogsButton)

        // Create Public Chat button - FIXED VERSION
        val publicChatButton = createChatButton(
            emoji = "🌐",
            title = "Public Chat",
            subtitle = "Join public room",
            isPrimary = true,
            enableThrob = true
        ) {
            Log.d(TAG, "🔨 DEBUG: Public Chat button onClick triggered")

            // CHECK INTERNET BEFORE ANIMATION!
            val hasInternet = authManager.isNetworkAvailable()
            Log.d(TAG, "🔨 DEBUG: Internet check result: $hasInternet")

            if (!hasInternet) {
                Log.d(TAG, "🔨 DEBUG: No internet - showing dialog, NOT animating")
                showNoInternetPublicChatDialog()
                return@createChatButton  // Don't animate, just show dialog
            }

            // Only animate if we have internet
            Log.d(TAG, "🔨 DEBUG: Internet available - proceeding with animation")
            performButtonAnimation {
                Log.d(TAG, "🔨 DEBUG: Public Chat animation complete callback")
                enterPublicChat()
            }
        }
        binding.btnPublicChatContainer.addView(publicChatButton)

        // Create Public Logs button
        val publicLogsButton = createChatButton(
            emoji = "📋",
            title = "Logs",
            subtitle = "View history",
            isPrimary = false,
            enableThrob = false
        ) {
            Log.d(TAG, "🔨 DEBUG: Public Logs button onClick triggered")
            performButtonAnimation {
                Log.d(TAG, "🔨 DEBUG: Public Logs animation complete callback")
                showPublicLogs()
            }
        }
        binding.btnPublicLogsContainer.addView(publicLogsButton)

        Log.d(TAG, "🔨 DEBUG: All main menu buttons created")
    }

    private fun createChatButton(
        emoji: String,
        title: String,
        subtitle: String,
        isPrimary: Boolean,
        enableThrob: Boolean,
        onClick: () -> Unit
    ): CardView {
        val cardView = CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#33FFFFFF"))

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            isClickable = true
            isFocusable = true
        }

        val frameLayout = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Hidden image for press state
        val imageView = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            // Use your existing button pressed background
            // setImageResource(R.drawable.button_pressed_background)
            visibility = View.GONE
        }

        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (!isPrimary && title == "Logs") {
                android.view.Gravity.CENTER  // Center emoji for log buttons
            } else {
                android.view.Gravity.CENTER_VERTICAL
            }
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
        }

        // Emoji icon with background
        val iconBackground = TextView(requireContext()).apply {
            text = emoji
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                48.dpToPx(),
                48.dpToPx()
            )
            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12.dpToPx().toFloat()
                setColor(Color.parseColor("#3398FB98"))
            }
            background = bgDrawable
        }

        // Text container
        val textContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 16.dpToPx()
            }
            // Hide entire text container for log buttons
            visibility = if (!isPrimary && title == "Logs") View.GONE else View.VISIBLE
        }

        val titleText = TextView(requireContext()).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            // Hide title text for log buttons
            visibility = if (!isPrimary && title == "Logs") View.GONE else View.VISIBLE
        }

        val subtitleText = TextView(requireContext()).apply {
            text = subtitle
            textSize = 12f
            setTextColor(Color.parseColor("#D3D3D3"))
        }

        textContainer.addView(titleText)
        textContainer.addView(subtitleText)

        // Pulsing indicator dot
        val indicatorDot = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                8.dpToPx(),
                8.dpToPx()
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

        frameLayout.addView(imageView)
        frameLayout.addView(contentLayout)
        cardView.addView(frameLayout)

        // Add throbbing if enabled
        if (enableThrob) {
            addThrobbingAnimation(cardView)
        }

        // Handle touch events for press effect
        cardView.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    cardView.setCardBackgroundColor(Color.TRANSPARENT)
                    imageView.visibility = View.VISIBLE
                    titleText.setShadowLayer(4f, 2f, 2f, Color.BLACK)
                    subtitleText.setShadowLayer(4f, 2f, 2f, Color.BLACK)
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    imageView.visibility = View.GONE
                    if (!enableThrob) {
                        cardView.setCardBackgroundColor(Color.parseColor("#33FFFFFF"))
                    }
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

        cardView.setOnClickListener {
            onClick()
        }

        return cardView
    }

    // Animation functions (adapted from MainActivity)
    private fun performButtonAnimation(onComplete: () -> Unit) {
        Log.d(TAG, "🎬 DEBUG: performButtonAnimation() called")
        Log.d(TAG, "🎬 DEBUG: Current layouts visibility:")
        Log.d(TAG, "🎬 DEBUG:   - layoutMainMenu: ${binding.layoutMainMenu.visibility}")
        Log.d(TAG, "🎬 DEBUG:   - layoutChatTypeMenu: ${binding.layoutChatTypeMenu.visibility}")

        Log.d(TAG, "🎬 DEBUG: Starting fade out of main menu")
        performManualFadeOut(binding.layoutMainMenu, 500L) {
            Log.d(TAG, "🎬 DEBUG: Fade out complete, calling onComplete callback")
            onComplete()

            // Ensure buttons are recreated when coming back
            if (binding.btnSeshChatContainer.childCount == 0) {
                Log.d(TAG, "🎬 DEBUG: Button container empty, recreating buttons")
                createStyledMainMenuButtons()
            }
        }
    }

    private fun performManualFadeIn(view: View, durationMs: Long) {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs
        val frameDelayMs = 16L

        view.alpha = 0f
        view.visibility = View.VISIBLE

        val fadeRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime
                val progress = min(elapsed.toFloat() / durationMs.toFloat(), 1f)

                // Apply easing
                val easedProgress = 1f - (1f - progress) * (1f - progress)
                view.alpha = easedProgress

                if (currentTime < endTime) {
                    fadeHandler.postDelayed(this, frameDelayMs)
                } else {
                    view.alpha = 1f
                }
            }
        }
        fadeHandler.post(fadeRunnable)
    }

    private fun performManualFadeOut(view: View, durationMs: Long, onComplete: () -> Unit) {
        Log.d(TAG, "🎭 DEBUG: performManualFadeOut() called for view: ${view.id}")
        Log.d(TAG, "🎭 DEBUG: View current visibility: ${view.visibility}, alpha: ${view.alpha}")

        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs
        val frameDelayMs = 16L

        val fadeRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime
                val progress = min(elapsed.toFloat() / durationMs.toFloat(), 1f)

                view.alpha = 1f - progress

                if (currentTime < endTime) {
                    fadeHandler.postDelayed(this, frameDelayMs)
                } else {
                    view.alpha = 0f
                    view.visibility = View.GONE
                    Log.d(TAG, "🎭 DEBUG: Fade out complete, view now GONE, calling onComplete")
                    onComplete()
                }
            }
        }
        fadeHandler.post(fadeRunnable)
    }

    private fun animateCardSelection(dialog: Dialog, onComplete: () -> Unit) {
        val contentView = dialog.window?.decorView?.findViewById<android.view.View>(android.R.id.content)
        val container = contentView as? android.view.ViewGroup
        val mainCard = container?.tag as? android.view.View ?: container?.getChildAt(0) ?: contentView

        Log.d(TAG, "📱 Starting card selection fade-out animation")

        val fadeOut = android.animation.ObjectAnimator.ofFloat(mainCard, "alpha", 1f, 0f)
        fadeOut.duration = 150L  // Faster than fade-in (reduced by 50%)
        fadeOut.interpolator = android.view.animation.AccelerateInterpolator()

        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                Log.d(TAG, "📱 Card selection fade-out completed")
                dialog.dismiss()

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    onComplete()
                }, 100)
            }
        })

        fadeOut.start()
    }

    private fun addThrobbingAnimation(view: View) {
        val cardView = view as? CardView ?: return

        // Ensure the card has at least minimal elevation for proper background rendering
        if (cardView.cardElevation == 0f) {
            cardView.cardElevation = 1f
        }

        val colors = intArrayOf(
            Color.parseColor("#33FFFFFF"),
            Color.parseColor("#3398FB98"),
            Color.parseColor("#33FFFFFF")
        )

        val handler = Handler(Looper.getMainLooper())
        throbHandlers.add(handler)
        var animationProgress = 0f
        var increasing = true

        val animationRunnable = object : Runnable {
            override fun run() {
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

                val color = if (animationProgress <= 0.5f) {
                    blendColors(colors[0], colors[1], animationProgress * 2)
                } else {
                    blendColors(colors[1], colors[2], (animationProgress - 0.5f) * 2)
                }

                cardView.setCardBackgroundColor(color)
                handler.postDelayed(this, 50)
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

    // New navigation functions for logs
    private fun showSeshLogs() {
        currentNavState = NavigationState.SESH_LOGS
        currentLogType = LogType.SESH_LOGS
        updateBackButtonVisibility()

        // Set up the logs view
        binding.layoutMainMenu.visibility = View.GONE
        binding.layoutChatTypeMenu.visibility = View.GONE
        binding.layoutTextChat.visibility = View.GONE
        binding.layoutVideoChat.visibility = View.GONE
        binding.layoutChatLogs.visibility = View.VISIBLE

        // Set header
        binding.textLogType.text = "Sesh Chat Logs"

        // Set the sesh logs overlay background
        binding.logsBackgroundImage?.apply {
            setImageResource(R.drawable.overlay_sesh_logs)
            visibility = View.VISIBLE
            alpha = 0.3f  // Adjust transparency as needed
        }

        // Load messages with 1-second fade in
        performManualFadeIn(binding.layoutChatLogs, 1000L)
        loadUserSeshMessages()
    }


    // Add this public method to be called from MainActivity
    fun onAuthStateChanged() {
        Log.d(TAG, "Auth state changed, rechecking...")
        checkAuthStatus()
    }

    private fun setupUI() {
        val currentUserId = authManager.getCurrentUserId() ?: ""

        Log.d(TAG, "🔍 DEBUG setupUI(): currentUserId for adapter = '$currentUserId'")
        Log.d(TAG, "🔍 DEBUG setupUI(): currentUser email = '${authManager.getCurrentUserEmail()}'")

        // Get smoker name for the adapter
        lifecycleScope.launch {
            val smokerName = getSmokerNameForUser(currentUserId)
            Log.d(TAG, "🔍 DEBUG setupUI(): smokerName = '$smokerName'")
            Log.d(TAG, "🔍 DEBUG setupUI(): Creating adapter with userId='$currentUserId', userName='$smokerName'")

            // Setup message RecyclerView with smoker name and edit callback
            messageAdapter = ChatMessageAdapter(
                currentUserId = currentUserId,
                currentUserName = smokerName,
                onMessageLongClick = { message ->
                    // This can be removed as we're handling it in the adapter now
                },
                onLikeClick = { message, isLiked ->
                    handleMessageLike(message, isLiked)
                },
                onReportClick = { message ->
                    handleMessageReport(message)
                },
                onDeleteClick = { message, isForEveryone ->
                    handleMessageDelete(message, isForEveryone)
                },
                onEditClick = { message ->  // Add the edit callback
                    handleMessageEdit(message)
                },
                userLikedMessages = likedMessageIds,
                userDeletedMessages = locallyDeletedMessageIds
            )

            // Set the developer delete callback
            messageAdapter.onDeveloperDelete = { message ->
                handleDeveloperDelete(message)
            }

            binding.recyclerMessages.apply {
                layoutManager = LinearLayoutManager(requireContext()).apply {
                    reverseLayout = false
                    stackFromEnd = true
                }
                adapter = messageAdapter
            }
        }

        // Setup button listeners
        setupButtonListeners()
    }

    private fun cancelEdit() {
        isEditMode = false
        editingMessage = null
        binding.editMessage.text.clear()
        binding.editMessage.hint = "Type a message..."
        binding.btnCancelEdit.visibility = View.GONE
        binding.btnSend.setImageResource(android.R.drawable.ic_menu_send)
    }


    private fun handleMessageEdit(message: ChatMessage) {
        val currentUserId = authManager.getCurrentUserId()
        if (message.senderId != currentUserId) {
            Toast.makeText(requireContext(), "You can only edit your own messages", Toast.LENGTH_SHORT).show()
            return
        }

        isEditMode = true
        editingMessage = message

        binding.btnCancelEdit.visibility = View.VISIBLE
        binding.btnSend.setImageResource(R.drawable.ic_check)
        binding.editMessage.setText(message.message)
        binding.editMessage.setSelection(message.message.length)
        binding.editMessage.requestFocus()

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.editMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)

        binding.editMessage.hint = "Edit message..."
    }


    private fun handleMessageDelete(message: ChatMessage, isForEveryone: Boolean) {
        lifecycleScope.launch {
            try {
                val userId = authManager.getCurrentUserId() ?: return@launch

                if (isForEveryone) {
                    // Delete for everyone (only if it's the user's own message)
                    // Mark as deleted in local database
                    chatDao.markMessageDeleted(message.id)

                    // Mark as deleted in Firebase
                    val firestore = FirebaseFirestore.getInstance()
                    firestore.collection("chat_messages")
                        .document(message.messageId)
                        .update("isDeleted", true)
                        .await()

                    Log.d(TAG, "Message deleted for everyone")
                } else {
                    // Delete only locally for this user
                    locallyDeletedMessageIds.add(message.messageId)

                    // Store in database for persistence
                    val deletion = UserDeletedMessage(
                        userId = userId,
                        messageId = message.messageId,
                        deletedAt = System.currentTimeMillis()
                    )
                    chatDao.insertUserDeletedMessage(deletion)

                    // Update adapter with locally deleted messages
                    messageAdapter.updateLocallyDeletedMessages(locallyDeletedMessageIds)

                    Log.d(TAG, "Message deleted locally for user")
                }

                // Reload messages
                loadLocalMessages()

            } catch (e: Exception) {
                Log.e(TAG, "Error deleting message", e)
                Toast.makeText(requireContext(), "Failed to delete message", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadLocalMessages() {
        currentChatRoom?.let { roomId ->
            lifecycleScope.launch {
                Log.d(TAG, "🔧 ====== LOAD LOCAL MESSAGES START ======")
                Log.d(TAG, "🔧 Loading messages for room: $roomId")

                // FIXED: Read directly from database to get fresh data including isDeveloperDeleted
                val messages = withContext(Dispatchers.IO) {
                    val allMessages = chatDao.getMessagesForRoomSync(roomId)
                    Log.d(TAG, "🔧 Total messages in DB: ${allMessages.size}")

                    allMessages.forEachIndexed { index, msg ->
                        Log.d(TAG, "🔧 Message [$index]: ID=${msg.messageId}, " +
                                "isDeveloperDeleted=${msg.isDeveloperDeleted}, " +
                                "isDeleted=${msg.isDeleted}, " +
                                "text='${msg.message.take(20)}...'")
                    }

                    // FIXED: Filter out ONLY developer deleted messages
                    val filteredMessages = allMessages
                        .filter { !it.isDeveloperDeleted } // Remove developer deleted
                        .sortedBy { it.timestamp }

                    Log.d(TAG, "🔧 After filtering: ${filteredMessages.size} messages")
                    Log.d(TAG, "🔧 Filtered out ${allMessages.size - filteredMessages.size} developer deleted messages")

                    filteredMessages.forEachIndexed { index, msg ->
                        Log.d(TAG, "🔧 Filtered [$index]: ID=${msg.messageId}, " +
                                "isDeveloperDeleted=${msg.isDeveloperDeleted}, " +
                                "isDeleted=${msg.isDeleted}, " +
                                "will show as: ${if (msg.isDeleted) "[Message deleted]" else "normal message"}")
                    }

                    filteredMessages
                }

                // Get locally deleted messages for this user
                val userId = authManager.getCurrentUserId() ?: return@launch
                val locallyDeleted = withContext(Dispatchers.IO) {
                    chatDao.getUserDeletedMessageIds(userId)
                }

                // Get user's likes for these messages
                val userLikes = mutableSetOf<String>()
                withContext(Dispatchers.IO) {
                    messages.forEach { message ->
                        if (chatDao.isMessageLikedByUser(message.messageId, userId)) {
                            userLikes.add(message.messageId)
                        }
                    }
                }

                // Update the liked messages set
                likedMessageIds.clear()
                likedMessageIds.addAll(userLikes)

                // Update locally deleted messages set
                locallyDeletedMessageIds.clear()
                locallyDeletedMessageIds.addAll(locallyDeleted)

                Log.d(TAG, "🔧 Final message count to display: ${messages.size}")
                Log.d(TAG, "🔧 User has liked ${likedMessageIds.size} messages")
                Log.d(TAG, "🔧 Locally deleted messages: ${locallyDeletedMessageIds.size}")

                // Update the adapter on the main thread
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "🔧 Updating adapter...")

                    // Update the adapter with the liked and deleted message IDs first
                    messageAdapter.updateLikedMessages(likedMessageIds)
                    messageAdapter.updateLocallyDeletedMessages(locallyDeletedMessageIds)

                    // Then submit all messages (this should NOT include developer deleted ones)
                    messageAdapter.submitList(messages.toList()) {
                        Log.d(TAG, "🔧 Adapter submitList completed - item count: ${messageAdapter.itemCount}")

                        // Log what types of messages we're displaying
                        messages.forEach { msg ->
                            val displayType = when {
                                msg.isDeveloperDeleted -> "INVISIBLE (ERROR - shouldn't be here!)"
                                msg.isDeleted -> "DELETED TEXT"
                                locallyDeletedMessageIds.contains(msg.messageId) -> "LOCALLY DELETED TEXT"
                                else -> "NORMAL"
                            }
                            Log.d(TAG, "🔧 Will display: ${msg.messageId} as $displayType")
                        }

                        // Maintain scroll position
                        val layoutManager = binding.recyclerMessages.layoutManager as? LinearLayoutManager
                        val lastVisiblePosition = layoutManager?.findLastCompletelyVisibleItemPosition() ?: -1
                        if (lastVisiblePosition >= messages.size - 2) {
                            binding.recyclerMessages.scrollToPosition(messages.size - 1)
                        }
                    }
                }
                Log.d(TAG, "🔧 ====== LOAD LOCAL MESSAGES END ======")
            }
        }
    }

    private fun setChatBackground() {
        val backgroundImage = binding.layoutTextChat.findViewById<ImageView>(R.id.chatBackgroundImage)

        // Set different background based on chat type
        when (currentChatType) {
            ChatType.SESH_CHAT -> {
                backgroundImage?.apply {
                    setImageResource(R.drawable.chat_bg_sesh)
                    visibility = View.VISIBLE
                    alpha = 1.0f // Adjust this value: 0.1 = very faint, 0.3 = more visible
                }
                Log.d(TAG, "Set sesh chat background")
            }
            ChatType.PUBLIC_CHAT -> {
                backgroundImage?.apply {
                    setImageResource(R.drawable.chat_bg_public)
                    visibility = View.VISIBLE
                    alpha = 1.0f // Adjust this value: 0.1 = very faint, 0.3 = more visible
                }
                Log.d(TAG, "Set public chat background")
            }
            else -> {
                // Hide background for other screens
                backgroundImage?.visibility = View.GONE
            }
        }
    }

    private fun showPublicLogs() {
        currentNavState = NavigationState.PUBLIC_LOGS
        currentLogType = LogType.PUBLIC_LOGS
        updateBackButtonVisibility()

        binding.layoutMainMenu.visibility = View.GONE
        binding.layoutChatTypeMenu.visibility = View.GONE
        binding.layoutTextChat.visibility = View.GONE
        binding.layoutVideoChat.visibility = View.GONE
        binding.layoutChatLogs.visibility = View.VISIBLE

        binding.textLogType.text = "Public Chat Logs"

        // Set the public logs overlay background
        binding.logsBackgroundImage?.apply {
            setImageResource(R.drawable.overlay_public_logs)
            visibility = View.VISIBLE
            alpha = 0.3f  // Adjust transparency as needed
        }

        performManualFadeIn(binding.layoutChatLogs, 1000L)
        loadUserPublicMessages()
    }

    private fun loadUserSeshMessages() {
        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId() ?: return@launch
            val messages = chatDao.getUserSeshMessages(userId)
            displayLogMessages(messages)
            binding.textLogCount.text = "${messages.size} messages"
        }
    }

    private fun loadUserPublicMessages() {
        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId() ?: return@launch
            val messages = chatDao.getUserPublicMessages(userId)
            displayLogMessages(messages)
            binding.textLogCount.text = "${messages.size} messages"
        }
    }

    private fun displayLogMessages(messages: List<ChatMessage>) {
        val currentUserId = authManager.getCurrentUserId() ?: return

        // Initialize log adapter with copy all functionality
        logMessageAdapter = ChatMessageAdapter(
            currentUserId = currentUserId,
            currentUserName = authManager.getCurrentUserName() ?: "You",
            onMessageLongClick = { /* handled in adapter */ },
            onLikeClick = { message, isLiked ->
                handleMessageLike(message, isLiked)
            },
            onReportClick = { message ->
                handleMessageReport(message)
            },
            onDeleteClick = { message, isForEveryone ->
                handleMessageDelete(message, isForEveryone)
            },
            onEditClick = { message ->
                handleMessageEdit(message)
            },
            userLikedMessages = likedMessageIds,
            userDeletedMessages = locallyDeletedMessageIds,
            onCopyTextClick = { message ->
                copyMessageToClipboard(message)
            },
            onCopyAllClick = {
                copyAllMessagesToClipboard(messages)
            },
            isLogView = true
        )

        binding.recyclerLogMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                reverseLayout = false  // Add this
                stackFromEnd = true    // Add this - makes messages start from bottom
            }
            adapter = logMessageAdapter
        }

        logMessageAdapter?.submitList(messages) {
            // Scroll to bottom after submitting list
            if (messages.isNotEmpty()) {
                binding.recyclerLogMessages.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun copyMessageToClipboard(message: ChatMessage) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Message", message.message)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Message copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun copyAllMessagesToClipboard(messages: List<ChatMessage>) {
        val formattedMessages = messages.joinToString("\n-\n") { it.message }

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("All Messages", formattedMessages)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "All messages copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun handleMessageLike(message: ChatMessage, shouldLike: Boolean) {
        Log.d(TAG, "💗 handleMessageLike: messageId=${message.messageId}, shouldLike=$shouldLike, currentCount=${message.likeCount}")

        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId() ?: return@launch

            try {
                val wasLiked = likedMessageIds.contains(message.messageId)

                if (shouldLike && !wasLiked) {
                    likedMessageIds.add(message.messageId)
                    val like = MessageLike(
                        messageId = message.messageId,
                        userId = userId,
                        timestamp = System.currentTimeMillis()
                    )
                    chatDao.insertLike(like)
                    val newCount = message.likeCount + 1
                    chatDao.updateMessageLikeCount(message.messageId, newCount)

                    // Only sync to Firebase if not in logs view
                    if (currentNavState != NavigationState.SESH_LOGS && currentNavState != NavigationState.PUBLIC_LOGS) {
                        syncLikeToFirebase(message.messageId, newCount)
                    }

                } else if (!shouldLike && wasLiked) {
                    likedMessageIds.remove(message.messageId)
                    val like = chatDao.getLike(message.messageId, userId)
                    like?.let {
                        chatDao.deleteLike(it)
                    }
                    val newCount = maxOf(0, message.likeCount - 1)
                    chatDao.updateMessageLikeCount(message.messageId, newCount)

                    // Only sync to Firebase if not in logs view
                    if (currentNavState != NavigationState.SESH_LOGS && currentNavState != NavigationState.PUBLIC_LOGS) {
                        syncLikeToFirebase(message.messageId, newCount)
                    }
                }

                // Update UI immediately
                withContext(Dispatchers.Main) {
                    messageAdapter.updateLikedMessages(likedMessageIds)
                    logMessageAdapter?.updateLikedMessages(likedMessageIds)
                    delay(50)

                    // Reload appropriate messages
                    when (currentNavState) {
                        NavigationState.SESH_LOGS -> loadUserSeshMessages()
                        NavigationState.PUBLIC_LOGS -> loadUserPublicMessages()
                        else -> loadLocalMessages()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "💗 Error handling like", e)
                // Revert on error
                if (shouldLike) {
                    likedMessageIds.remove(message.messageId)
                } else {
                    likedMessageIds.add(message.messageId)
                }
                withContext(Dispatchers.Main) {
                    messageAdapter.updateLikedMessages(likedMessageIds)
                    logMessageAdapter?.updateLikedMessages(likedMessageIds)
                    // Don't show Firebase sync error for log views
                    if (currentNavState != NavigationState.SESH_LOGS && currentNavState != NavigationState.PUBLIC_LOGS) {
                        Toast.makeText(requireContext(), "Failed to update like", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun adjustInputPaddingForLayoutPosition() {
        Log.d("KEYBOARD_FIX", "📏 === ADJUST INPUT PADDING START ===")
        Log.d("KEYBOARD_FIX", "📏 Method called from: ${Thread.currentThread().stackTrace[3]}")

        val prefs = requireContext().getSharedPreferences("sesh", Context.MODE_PRIVATE)
        val isLayoutAtBottom = prefs.getBoolean("layout_at_bottom", false)

        val allPrefs = prefs.all
        Log.d("KEYBOARD_FIX", "📏 All preferences in 'sesh': $allPrefs")
        Log.d("KEYBOARD_FIX", "📏 layout_at_bottom value: $isLayoutAtBottom")

        val mainActivity = requireActivity() as? MainActivity
        val buttonSection = mainActivity?.findViewById<View>(R.id.topSectionContainer)
        buttonSection?.let {
            val location = IntArray(2)
            it.getLocationOnScreen(location)
            val screenHeight = resources.displayMetrics.heightPixels
            val isVisuallyAtBottom = location[1] > screenHeight / 2
            Log.d("KEYBOARD_FIX", "📏 Button section Y position: ${location[1]} (screen height: $screenHeight)")
            Log.d("KEYBOARD_FIX", "📏 Is visually at bottom: $isVisuallyAtBottom")
        }

        if (isLayoutAtBottom) {
            requireActivity().window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            )
            Log.d("KEYBOARD_FIX", "📏 Set soft input mode to ADJUST_NOTHING")
        } else {
            requireActivity().window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            )
            Log.d("KEYBOARD_FIX", "📏 Set soft input mode to ADJUST_RESIZE")
        }

        binding.layoutMessageInput?.let { inputLayout ->
            // Reset translation first
            inputLayout.translationY = 0f

            val paddingLeft = inputLayout.paddingLeft
            val paddingRight = inputLayout.paddingRight
            val paddingTop = inputLayout.paddingTop
            val oldPaddingBottom = inputLayout.paddingBottom

            if (isLayoutAtBottom) {
                Log.d("KEYBOARD_FIX", "📏 Setting up message input for BOTTOM layout")
                val newPaddingBottom = 2.dpToPx()
                inputLayout.setPadding(paddingLeft, paddingTop, paddingRight, newPaddingBottom)
                inputLayout.fitsSystemWindows = false
                inputLayout.visibility = View.VISIBLE

                Log.d("KEYBOARD_FIX", "📏 Message input set for bottom layout, padding: $oldPaddingBottom -> $newPaddingBottom")
            } else {
                val newPaddingBottom = 48.dpToPx()
                inputLayout.setPadding(paddingLeft, paddingTop, paddingRight, newPaddingBottom)
                inputLayout.fitsSystemWindows = true
                inputLayout.visibility = View.VISIBLE
                Log.d("KEYBOARD_FIX", "📏 Message input reset for top layout, padding: $oldPaddingBottom -> $newPaddingBottom")
            }
        }

        Log.d("KEYBOARD_FIX", "📏 MESSAGE INPUT SECTION COMPLETED")

        // Position input when button section is at bottom
        if (isLayoutAtBottom) {
            Log.d("KEYBOARD_FIX", "📏 POSITIONING INPUT FOR BOTTOM LAYOUT")

            binding.layoutSignedIn?.visibility = View.VISIBLE
            binding.layoutTextChat?.visibility = View.VISIBLE

            // Post to ensure layout is measured
            binding.layoutMessageInput?.post {
                val mainActivity = requireActivity() as? MainActivity
                val buttonSection = mainActivity?.findViewById<View>(R.id.topSectionContainer)

                buttonSection?.let { section ->
                    binding.layoutMessageInput?.let { inputLayout ->
                        // Get button section position
                        val location = IntArray(2)
                        section.getLocationOnScreen(location)
                        val buttonSectionY = location[1].toFloat()

                        // Get input layout position WITHOUT translation
                        val inputLocation = IntArray(2)
                        inputLayout.getLocationOnScreen(inputLocation)
                        // Subtract current translation to get original position
                        val inputOriginalY = inputLocation[1].toFloat() - inputLayout.translationY

                        // Calculate target position: 10dp above button section
                        val targetY = buttonSectionY - inputLayout.height - 10.dpToPx()
                        val neededTranslation = targetY - inputOriginalY

                        Log.d("KEYBOARD_FIX", "📏 POSITIONING CALCULATION:")
                        Log.d("KEYBOARD_FIX", "📏   Button section Y: $buttonSectionY px")
                        Log.d("KEYBOARD_FIX", "📏   Input current Y: ${inputLocation[1].toFloat()} px")
                        Log.d("KEYBOARD_FIX", "📏   Input height: ${inputLayout.height} px")
                        Log.d("KEYBOARD_FIX", "📏   Target Y: $targetY px")
                        Log.d("KEYBOARD_FIX", "📏   Translation needed: $neededTranslation px")

                        inputLayout.translationY = neededTranslation

                        val finalY = inputOriginalY + neededTranslation
                        Log.d("KEYBOARD_FIX", "📏 ✅ INPUT POSITIONED AT: $finalY px")
                    }
                }
            }
        }

        // Also adjust log input
        binding.layoutLogInput?.let { logInputLayout ->
            logInputLayout.translationY = 0f
            val paddingLeft = logInputLayout.paddingLeft
            val paddingRight = logInputLayout.paddingRight
            val paddingTop = logInputLayout.paddingTop

            if (isLayoutAtBottom) {
                val newPaddingBottom = 2.dpToPx()
                logInputLayout.setPadding(paddingLeft, paddingTop, paddingRight, newPaddingBottom)
                logInputLayout.fitsSystemWindows = false
            } else {
                val newPaddingBottom = 16.dpToPx()
                logInputLayout.setPadding(paddingLeft, paddingTop, paddingRight, newPaddingBottom)
                logInputLayout.fitsSystemWindows = true
            }
        }
    }

    private fun getNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    private fun setupKeyboardVisibilityDetection() {
        Log.d("KEYBOARD_FIX", "🎹 === KEYBOARD DETECTION SETUP START ===")
        Log.d("KEYBOARD_FIX", "🎹 Called from: ${Thread.currentThread().stackTrace[3]}")

        val prefs = requireContext().getSharedPreferences("sesh", Context.MODE_PRIVATE)
        val isLayoutAtBottom = prefs.getBoolean("layout_at_bottom", false)
        
        // Log all preferences for debugging
        val allPrefs = prefs.all
        Log.d("KEYBOARD_FIX", "🎹 All preferences: $allPrefs")
        Log.d("KEYBOARD_FIX", "🎹 layout_at_bottom: $isLayoutAtBottom")
        Log.d("KEYBOARD_FIX", "🎹 Layout position: ${if (isLayoutAtBottom) "BOTTOM" else "TOP"}")

        // Always clean up existing listener first
        keyboardLayoutListener?.let { listener ->
            view?.rootView?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
            Log.d("ChatFragment", "🎹 Cleaned up existing listener")
        }
        keyboardListenerRegistered = false

        // Only setup keyboard detection when section is at bottom
        if (!isLayoutAtBottom) {
            Log.d("ChatFragment", "🎹 Section at TOP - using system keyboard handling")
            return
        }

        Log.d("ChatFragment", "🎹 Section at BOTTOM - setting up manual keyboard handling")

        val rootView = view?.rootView ?: run {
            Log.d("ChatFragment", "🎹 ERROR: rootView is null!")
            return
        }

        keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.height
            val keypadHeight = screenHeight - rect.bottom

            Log.d("ChatFragment", "🎹 === KEYBOARD STATE CHECK ===")
            Log.d("ChatFragment", "🎹 Screen height: $screenHeight px")
            Log.d("ChatFragment", "🎹 Rect bottom: ${rect.bottom} px")
            Log.d("ChatFragment", "🎹 Rect top: ${rect.top} px")
            Log.d("ChatFragment", "🎹 Keypad height: $keypadHeight px")
            Log.d("ChatFragment", "🎹 Keypad height in dp: ${keypadHeight / resources.displayMetrics.density} dp")

            // Get the height of the bottom section
            val mainActivity = requireActivity() as? MainActivity
            val topSection = mainActivity?.findViewById<View>(R.id.topSectionContainer)
            val sectionHeight = topSection?.height ?: 0
            val sectionY = topSection?.y ?: 0f

            Log.d("ChatFragment", "🎹 Button section height: $sectionHeight px")
            Log.d("ChatFragment", "🎹 Button section Y position: $sectionY px")

            // Check current input position
            binding.layoutMessageInput?.let { input ->
                Log.d("ChatFragment", "🎹 Input current Y: ${input.y} px")
                Log.d("ChatFragment", "🎹 Input current translationY: ${input.translationY} px")
                Log.d("ChatFragment", "🎹 Input height: ${input.height} px")
            }

            // Add throttling: only recalculate if keyboard height or button section position changed significantly
            val keyboardHeightChanged = Math.abs(keypadHeight - lastKeyboardHeight) > 10 // 10px threshold
            val buttonSectionMoved = Math.abs(sectionY - lastButtonSectionY) > 20f // 20px threshold
            
            if (!keyboardHeightChanged && !buttonSectionMoved) {
                // No significant change, skip recalculation
                return@OnGlobalLayoutListener
            }
            
            // Update last known values
            lastKeyboardHeight = keypadHeight
            lastButtonSectionY = sectionY
            
            // If keyboard is showing (height > 30dp - ultra sensitive for small keyboards)
            val keyboardThreshold = 30.dpToPx() // Very low threshold - 30dp (~105px)
            
            Log.d("KEYBOARD_FIX", "🎹 THRESHOLD CALCULATION:")
            Log.d("KEYBOARD_FIX", "🎹   30dp = $keyboardThreshold px")
            Log.d("KEYBOARD_FIX", "🎹   Density = ${resources.displayMetrics.density}")
            Log.d("KEYBOARD_FIX", "🎹   Keypad height = $keypadHeight px")
            Log.d("KEYBOARD_FIX", "🎹   Comparison: $keypadHeight > $keyboardThreshold = ${keypadHeight > keyboardThreshold}")
            Log.d("KEYBOARD_FIX", "🎹   Height changed: $keyboardHeightChanged, Button moved: $buttonSectionMoved")
            
            if (keypadHeight > keyboardThreshold) {
                Log.d("KEYBOARD_FIX", "🎹 ✅ KEYBOARD VISIBLE (height: $keypadHeight > threshold: $keyboardThreshold)")
                positionInputAboveKeyboard(keypadHeight, sectionHeight)
            } else {
                Log.d("KEYBOARD_FIX", "🎹 ❌ KEYBOARD HIDDEN (height: $keypadHeight <= threshold: $keyboardThreshold)")
                
                // FORCE keyboard positioning if height is close to our observed 168px
                if (keypadHeight >= 160 && keypadHeight <= 180) {
                    Log.d("KEYBOARD_FIX", "🎹 🚨 FORCE POSITIONING - detected likely keyboard at $keypadHeight px")
                    positionInputAboveKeyboard(keypadHeight, sectionHeight)
                } else {
                    restoreInputPosition()
                }
            }
            Log.d("KEYBOARD_FIX", "🎹 === END KEYBOARD CHECK ===")
        }

        rootView.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)
        keyboardListenerRegistered = true
        Log.d("ChatFragment", "🎹 Keyboard listener registered successfully for BOTTOM layout")
    }

    fun onLayoutPositionChanged() {
        Log.d("KEYBOARD_FIX", "📍 === LAYOUT POSITION CHANGED NOTIFICATION ===")
        
        // Force re-read the preference immediately
        val prefs = requireContext().getSharedPreferences("sesh", Context.MODE_PRIVATE)
        val isLayoutAtBottom = prefs.getBoolean("layout_at_bottom", false)
        Log.d("KEYBOARD_FIX", "📍 New layout position from prefs: ${if (isLayoutAtBottom) "BOTTOM" else "TOP"}")
        
        // Clear any existing keyboard listeners
        keyboardLayoutListener?.let { listener ->
            view?.rootView?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
            Log.d("KEYBOARD_FIX", "📍 Cleared existing keyboard listener")
        }
        keyboardLayoutListener = null
        keyboardListenerRegistered = false

        // Re-adjust padding immediately
        adjustInputPaddingForLayoutPosition()

        // Re-setup keyboard detection immediately (no delay needed since we used commit())
        setupKeyboardVisibilityDetection()
        
        // Force a layout pass
        view?.requestLayout()
        Log.d("KEYBOARD_FIX", "📍 Layout position change handling complete")
    }
    
    fun onExpansionStateChanged(isExpanded: Boolean) {
        Log.d("KEYBOARD_FIX", "🔄 === EXPANSION STATE CHANGED ===")
        Log.d("KEYBOARD_FIX", "🔄 Button section is now: ${if (isExpanded) "EXPANDED" else "COLLAPSED"}")
        
        // Reset tracking variables to force recalculation on state change
        lastButtonSectionY = -1f
        
        // Check if keyboard is currently visible
        val rootView = view?.rootView ?: return
        val rect = Rect()
        rootView.getWindowVisibleDisplayFrame(rect)
        val screenHeight = rootView.height
        val keypadHeight = screenHeight - rect.bottom
        val keyboardThreshold = 30.dpToPx() // Very low threshold - 30dp (~105px)
        
        val isKeyboardVisible = keypadHeight > keyboardThreshold
        Log.d("KEYBOARD_FIX", "🔄 Keyboard currently visible: $isKeyboardVisible (height: $keypadHeight)")
        
        // If keyboard is visible, recalculate the position with a delay to allow layout to settle
        if (isKeyboardVisible) {
            Log.d("KEYBOARD_FIX", "🔄 Recalculating input position for new expansion state (with delay)")
            
            // Add a small delay to allow the button section layout to complete
            view?.post {
                view?.postDelayed({
                    Log.d("KEYBOARD_FIX", "🔄 Delayed position recalculation starting")
                    val sectionHeight = getNavigationBarHeight()
                    positionInputAboveKeyboard(keypadHeight, sectionHeight)
                }, 50) // 50ms delay to allow layout to complete
            }
        }
    }

    private fun positionInputAboveKeyboard(keyboardHeight: Int, sectionHeight: Int) {
        Log.d("KEYBOARD_FIX", "🔧 === POSITION INPUT ABOVE KEYBOARD ===")
        Log.d("KEYBOARD_FIX", "🔧 keyboardHeight: $keyboardHeight px (${keyboardHeight / resources.displayMetrics.density} dp)")

        val prefs = requireContext().getSharedPreferences("sesh", Context.MODE_PRIVATE)
        val isLayoutAtBottom = prefs.getBoolean("layout_at_bottom", false)

        Log.d("KEYBOARD_FIX", "🔧 layout_at_bottom from prefs: $isLayoutAtBottom")

        if (!isLayoutAtBottom) {
            Log.d("KEYBOARD_FIX", "🔧 TOP MODE - no translation needed")
            return
        }

        // Simple approach: Position input field 60dp above button section
        val mainActivity = requireActivity() as? MainActivity
        val buttonSection = mainActivity?.findViewById<View>(R.id.topSectionContainer)

        binding.layoutMessageInput?.let { inputLayout ->
            if (buttonSection != null) {
                // Get button section's current Y position
                val location = IntArray(2)
                buttonSection.getLocationOnScreen(location)
                val buttonSectionY = location[1].toFloat()

                // Get input's original position (without translation)
                val inputLocation = IntArray(2)
                inputLayout.getLocationOnScreen(inputLocation)
                val inputCurrentY = inputLocation[1].toFloat()
                val inputOriginalY = inputCurrentY - inputLayout.translationY

                // Calculate target: 60dp above button section
                val marginAboveButton = 60.dpToPx()
                val targetY = buttonSectionY - marginAboveButton

                // Calculate translation needed
                val neededTranslation = targetY - inputOriginalY

                Log.d("KEYBOARD_FIX", "🔧 SIMPLE POSITIONING:")
                Log.d("KEYBOARD_FIX", "🔧   Button section Y: $buttonSectionY px")
                Log.d("KEYBOARD_FIX", "🔧   Input original Y: $inputOriginalY px")
                Log.d("KEYBOARD_FIX", "🔧   Target Y (60dp above button): $targetY px")
                Log.d("KEYBOARD_FIX", "🔧   Translation needed: $neededTranslation px")

                // Apply translation
                inputLayout.translationY = neededTranslation
                inputLayout.visibility = View.VISIBLE

                val finalY = inputOriginalY + neededTranslation
                Log.d("KEYBOARD_FIX", "🔧 ✅ INPUT POSITIONED AT: $finalY px")
            }
        }

        // Apply same translation to other elements
        val inputTranslation = binding.layoutMessageInput?.translationY ?: 0f

        binding.layoutLogInput?.let { it.translationY = inputTranslation }
        binding.recyclerMessages?.let { it.translationY = inputTranslation }
        binding.recyclerLogMessages?.let { it.translationY = inputTranslation }
    }

    // Add method to force positioning when layout changes
    private fun forceInputPositioningForLayoutChange() {
        Log.d("ChatFragment", "🔧 === FORCE INPUT POSITIONING FOR LAYOUT CHANGE ===")
        
        val prefs = requireContext().getSharedPreferences("sesh", Context.MODE_PRIVATE)
        val isLayoutAtBottom = prefs.getBoolean("layout_at_bottom", false)
        
        if (isLayoutAtBottom) {
            Log.d("ChatFragment", "🔧 Layout at bottom - forcing immediate positioning")
            
            // Ensure parent layouts are visible immediately
            binding.layoutSignedIn?.visibility = View.VISIBLE
            binding.layoutTextChat?.visibility = View.VISIBLE
            
            // Force a layout pass to ensure views are measured
            view?.post {
                Log.d("ChatFragment", "🔧 Layout pass completed - triggering keyboard positioning")
                
                // Get button section info for positioning
                val mainActivity = requireActivity() as? MainActivity
                val buttonSection = mainActivity?.findViewById<View>(R.id.topSectionContainer)
                val buttonSectionY = buttonSection?.y ?: 0f
                
                if (buttonSectionY > 0) {
                    // Trigger positioning with fake keyboard height (we just need positioning)
                    positionInputAboveKeyboard(200, 100)
                } else {
                    // If button section not ready, try again after a delay
                    view?.postDelayed({
                        Log.d("ChatFragment", "🔧 Retry positioning after button section ready")
                        val retryButtonSectionY = buttonSection?.y ?: 0f
                        if (retryButtonSectionY > 0) {
                            positionInputAboveKeyboard(200, 100)
                        }
                    }, 100)
                }
            }
        } else {
            Log.d("ChatFragment", "🔧 Layout at top - resetting position")
            restoreInputPosition()
        }
    }

    private fun restoreInputPosition() {
        Log.d("KEYBOARD_FIX", "🔄 Restoring input position - resetting all translations to 0")

        binding.layoutMessageInput?.let { inputLayout ->
            inputLayout.visibility = View.VISIBLE

            // When button section is at bottom, restore to position above button section
            val prefs = requireContext().getSharedPreferences("sesh", Context.MODE_PRIVATE)
            val isLayoutAtBottom = prefs.getBoolean("layout_at_bottom", false)

            if (isLayoutAtBottom) {
                // Re-position above button section
                val mainActivity = requireActivity() as? MainActivity
                val buttonSection = mainActivity?.findViewById<View>(R.id.topSectionContainer)

                buttonSection?.let { section ->
                    val location = IntArray(2)
                    section.getLocationOnScreen(location)
                    val buttonSectionY = location[1].toFloat()

                    // Get original position
                    val originalY = (inputLayout.tag as? Float) ?: run {
                        val inputLocation = IntArray(2)
                        inputLayout.getLocationOnScreen(inputLocation)
                        inputLocation[1].toFloat() - inputLayout.translationY
                    }

                    // Position above button section
                    val targetY = buttonSectionY - inputLayout.height - 10.dpToPx()
                    val neededTranslation = targetY - originalY

                    inputLayout.translationY = neededTranslation

                    Log.d("KEYBOARD_FIX", "🔄 Restored to above button section, translation: $neededTranslation")
                }
            } else {
                // Top mode - no translation needed
                inputLayout.translationY = 0f
            }
        }

        binding.layoutLogInput?.translationY = binding.layoutMessageInput?.translationY ?: 0f
        binding.recyclerMessages?.translationY = binding.layoutMessageInput?.translationY ?: 0f
        binding.recyclerLogMessages?.translationY = binding.layoutMessageInput?.translationY ?: 0f
    }


    private fun loadUserLikes() {
        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId() ?: return@launch
            currentChatRoom?.let { roomId ->
                try {
                    // Load user's likes for this room
                    val messages = chatDao.getMessagesForRoomSync(roomId)
                    messages.forEach { message ->
                        if (chatDao.isMessageLikedByUser(message.messageId, userId)) {
                            likedMessageIds.add(message.messageId)
                        }
                    }

                    // Load locally deleted messages for this user
                    val deletedIds = chatDao.getUserDeletedMessageIds(userId)
                    locallyDeletedMessageIds.clear()
                    locallyDeletedMessageIds.addAll(deletedIds)

                    // Update adapter with both likes and deletions
                    messageAdapter.updateLikedMessages(likedMessageIds)
                    messageAdapter.updateLocallyDeletedMessages(locallyDeletedMessageIds)

                } catch (e: Exception) {
                    Log.e(TAG, "Error loading user likes and deletions", e)
                }
            }
        }
    }

    private fun retrySyncLikesToFirebase() {
        lifecycleScope.launch {
            try {
                val firestore = FirebaseFirestore.getInstance()
                currentChatRoom?.let { roomId ->
                    // Get all messages with their like counts from local DB
                    val messages = chatDao.getMessagesForRoomSync(roomId)

                    messages.forEach { message ->
                        val localLikeCount = chatDao.getLikeCount(message.messageId)
                        if (localLikeCount > 0) {
                            // Try to sync any messages with likes
                            firestore.collection("chat_messages")
                                .document(message.messageId)
                                .update("likeCount", localLikeCount)
                                .addOnSuccessListener {
                                    Log.d(TAG, "💗 Retry sync successful for ${message.messageId}: count=$localLikeCount")
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "💗 Retry sync failed for ${message.messageId}", e)
                                }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "💗 Error in retry sync", e)
            }
        }
    }

    private fun syncLikeNotificationToFirebase(message: ChatMessage, likerName: String) {
        // This will trigger the notification on the message sender's other devices
        lifecycleScope.launch {
            try {
                val firestore = FirebaseFirestore.getInstance()
                currentChatRoom?.let { roomId ->
                    val notificationData = hashMapOf(
                        "type" to "like_notification",
                        "messageId" to message.messageId,
                        "messageSenderId" to message.senderId,
                        "likerName" to likerName,
                        "messagePreview" to message.message,
                        "timestamp" to System.currentTimeMillis()
                    )

                    firestore.collection("chat_notifications")
                        .document("${message.senderId}_${System.currentTimeMillis()}")
                        .set(notificationData)
                        .await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing like notification to Firebase", e)
            }
        }
    }

    private fun syncLikeToFirebase(messageId: String, likeCount: Int) {
        lifecycleScope.launch {
            try {
                val firestore = FirebaseFirestore.getInstance()

                // Update the like count in Firebase
                firestore.collection("chat_messages")
                    .document(messageId)
                    .update("likeCount", likeCount)
                    .addOnSuccessListener {
                        Log.d(TAG, "💗 Successfully synced to Firebase: messageId=$messageId, count=$likeCount")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "💗 Error syncing like to Firebase: ${e.message}", e)

                        // If it's a permission error, show a toast
                        if (e.message?.contains("PERMISSION_DENIED") == true) {
                            // Use lifecycleScope.launch instead of withContext
                            lifecycleScope.launch(Dispatchers.Main) {
                                Toast.makeText(
                                    requireContext(),
                                    "Unable to sync like. Please check your connection.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

            } catch (e: Exception) {
                Log.e(TAG, "💗 Error syncing like to Firebase: ${e.message}", e)
            }
        }
    }

    private fun handleMessageReport(message: ChatMessage) {
        val currentUserId = authManager.getCurrentUserId() ?: return
        val currentUserName = authManager.getCurrentUserName() ?: "Unknown"

        reportHelper.showReportDialog(
            context = requireContext(),
            isVideo = false,
            reportedUserId = message.senderId,
            reportedUserName = message.senderName,
            reporterUserId = currentUserId,
            reporterUserName = currentUserName,
            roomId = currentChatRoom ?: "",
            messageId = message.messageId,
            messageContent = message.message,
            onReportSubmitted = { reason, customReason ->
                submitMessageReport(message, reason, customReason)
            }
        )
    }

    private suspend fun submitMessageReport(message: ChatMessage, reason: String, customReason: String?) {
        val currentUserId = authManager.getCurrentUserId() ?: return
        val currentUserName = authManager.getCurrentUserName() ?: "Unknown"

        try {
            val report = MessageReport(
                messageId = message.messageId,
                reportedUserId = message.senderId,
                reportedUserName = message.senderName,
                reporterUserId = currentUserId,
                reporterUserName = currentUserName,
                reason = reason,
                messageContent = message.message,
                timestamp = System.currentTimeMillis()
            )

            // Save report to database
            val reportId = chatDao.insertMessageReport(report)

            // Increment report count
            chatDao.incrementMessageReportCount(message.messageId)

            // Send email report
            val success = reportHelper.sendMessageReport(report, customReason)

            if (success) {
                chatDao.markMessageReportSent(reportId)
                Toast.makeText(requireContext(), "Report submitted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Report saved but email failed", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error submitting report", e)
            Toast.makeText(requireContext(), "Failed to submit report", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleVideoReport(reportedUserId: String, reportedUserName: String) {
        val currentUserId = authManager.getCurrentUserId() ?: return
        val currentUserName = authManager.getCurrentUserName() ?: "Unknown"

        reportHelper.showReportDialog(
            context = requireContext(),
            isVideo = true,
            reportedUserId = reportedUserId,
            reportedUserName = reportedUserName,
            reporterUserId = currentUserId,
            reporterUserName = currentUserName,
            roomId = currentChatRoom ?: "",
            messageId = null,
            messageContent = null,
            onReportSubmitted = { reason, customReason ->
                submitVideoReport(reportedUserId, reportedUserName, reason, customReason)
            }
        )
    }

    private suspend fun submitVideoReport(
        reportedUserId: String,
        reportedUserName: String,
        reason: String,
        customReason: String?
    ) {
        val currentUserId = authManager.getCurrentUserId() ?: return
        val currentUserName = authManager.getCurrentUserName() ?: "Unknown"

        try {
            val report = VideoReport(
                reportedUserId = reportedUserId,
                reportedUserName = reportedUserName,
                reporterUserId = currentUserId,
                reporterUserName = currentUserName,
                roomId = currentChatRoom ?: "",
                reason = reason,
                timestamp = System.currentTimeMillis()
            )

            // Save report to database
            val reportId = chatDao.insertVideoReport(report)

            // Send email report
            val success = reportHelper.sendVideoReport(report, customReason)

            if (success) {
                chatDao.markVideoReportSent(reportId)
                Toast.makeText(requireContext(), "Report submitted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Report saved but email failed", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error submitting video report", e)
            Toast.makeText(requireContext(), "Failed to submit report", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtonListeners() {
        // Header back button
        binding.btnHeaderBack.setOnClickListener { view ->
            confettiHelper?.showMiniConfettiFromButton(view)
            handleBackNavigation()
        }

        // Sign in button (on signed out screen)
        binding.btnSignIn.setOnClickListener { view ->
            confettiHelper?.showMiniConfettiFromButton(view)
            signIn()
        }

        // Sign out button
        binding.btnSignOut.setOnClickListener { view ->
            if (authManager.isSignedIn) {
                confettiHelper?.showMiniConfettiFromButton(view)
                signOut()
            }
        }


// Send message button
        binding.btnSend.setOnClickListener { view ->
            confettiHelper?.showMiniConfettiFromButton(view)
            sendMessage()
        }

        // Cancel edit button
        binding.btnCancelEdit.setOnClickListener { view ->
            confettiHelper?.showMiniConfettiFromButton(view)
            cancelEdit()
        }

        // Handle enter key in message input
        binding.editMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
    }

    // Helper extension
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }


        private fun handleBackNavigation() {
            // Stop presence refresh when leaving chat
            presenceRefreshJob?.cancel()
            presenceRefreshJob = null

            when (currentNavState) {
                NavigationState.CHAT_TYPE_MENU -> {
                    // Going back from chat type menu to main menu
                    binding.layoutChatTypeMenu.visibility = View.GONE
                    showMainMenu()
                    // Ensure main menu buttons are visible
                    if (binding.btnSeshChatContainer.childCount == 0) {
                        createStyledMainMenuButtons()
                    }
                    performManualFadeIn(binding.layoutMainMenu, 500L)
                }
                NavigationState.TEXT_CHAT -> {
                    // Leave room and go back to chat type menu
                    currentChatRoom?.let {
                        chatViewModel.leaveRoom(it)
                    }
                    binding.layoutTextChat.visibility = View.GONE
                    showChatTypeMenu()
                    performManualFadeIn(binding.layoutChatTypeMenu, 500L)
                }
                NavigationState.VIDEO_CHAT -> {
                    endVideoCall()
                    // After ending video call, show chat type menu
                    binding.layoutVideoChat.visibility = View.GONE
                    showChatTypeMenu()
                    performManualFadeIn(binding.layoutChatTypeMenu, 500L)
                }
                NavigationState.SESH_LOGS,
                NavigationState.PUBLIC_LOGS -> {
                    // Fade out logs and return to main menu
                    performManualFadeOut(binding.layoutChatLogs, 500L) {
                        showMainMenu()
                        performManualFadeIn(binding.layoutMainMenu, 500L)
                    }
                }
                NavigationState.MAIN_MENU -> {
                    // Already at main menu
                }
            }
        }

    private fun updateBackButtonVisibility() {
        val shouldShow = when (currentNavState) {
            NavigationState.MAIN_MENU -> false
            else -> true
        }

        binding.btnHeaderBack.visibility = if (shouldShow) View.VISIBLE else View.GONE

        Log.d(TAG, "Back button visibility updated: state=$currentNavState, visible=$shouldShow")
    }



    private fun observeViewModel() {
        Log.d(TAG, "📔 Setting up observeViewModel")

        // Set up the new message callback
        chatViewModel.onNewMessageReceived = { message ->
            Log.d(TAG, "📔 New message received callback triggered")
            if (shouldShowNotification(message)) {
                val roomName = when (currentChatType) {
                    ChatType.SESH_CHAT -> {
                        val shareCode = currentChatRoom?.removePrefix("sesh_") ?: ""
                        "Session $shareCode"
                    }
                    ChatType.PUBLIC_CHAT -> "Public Chat"
                    else -> "Chat"
                }

                notificationHelper.showChatMessageNotification(
                    roomId = currentChatRoom ?: "",
                    roomName = roomName,
                    senderName = message.senderName,
                    message = message.message,
                    isSeshChat = currentChatType == ChatType.SESH_CHAT
                )
            }
        }

        // Observe messages for current room
        chatViewModel.currentRoomMessages.observe(viewLifecycleOwner) { messages ->
            Log.d(TAG, "Received ${messages.size} messages for display")

            // Check if we should auto-scroll
            val layoutManager = binding.recyclerMessages.layoutManager as? LinearLayoutManager
            val shouldScrollToBottom = layoutManager?.let {
                val lastVisiblePosition = it.findLastCompletelyVisibleItemPosition()
                lastVisiblePosition == -1 || lastVisiblePosition >= (messageAdapter.itemCount - 2)
            } ?: true

            // Submit the list without calling notifyDataSetChanged
            messageAdapter.submitList(messages.toList()) {
                // Scroll to bottom if we should
                if (messages.isNotEmpty() && shouldScrollToBottom) {
                    binding.recyclerMessages.post {
                        binding.recyclerMessages.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        // Observe online users
        chatViewModel.onlineUsers.observe(viewLifecycleOwner) { users ->
            Log.d(TAG, "Online users updated: ${users.size}")
            updateOnlineUsersList(users)
        }

        // Observe typing indicators
        chatViewModel.typingUsers.observe(viewLifecycleOwner) { typingUsers ->
            updateTypingIndicator(typingUsers)
        }
    }

    private fun checkAuthStatus() {
        val currentUser = authManager.getCurrentUser()

        if (currentUser != null) {
            Log.d(TAG, "User is signed in: ${currentUser.displayName}")

            // Update UI to show signed in state
            binding.layoutSignedOut.visibility = View.GONE
            binding.layoutSignedIn.visibility = View.VISIBLE

            // Use consistent name from FirebaseAuthManager
            val displayName = authManager.getCurrentUserName() ?: "User"
            binding.textCurrentUser.text = displayName
            binding.btnSignOut.visibility = View.VISIBLE

            // Initialize chat service with consistent name
            chatViewModel.initializeChat(currentUser.uid, displayName)

            // FIXED: Better state restoration logic
            if (shouldRestoreChat && currentChatRoom != null) {
                Log.d(TAG, "Restoring previous chat session to room: $currentChatRoom")
                shouldRestoreChat = false

                // Restore the UI to the correct state
                when (currentNavState) {
                    NavigationState.TEXT_CHAT -> {
                        restoreTextChatState()
                    }
                    NavigationState.CHAT_TYPE_MENU -> {
                        showChatTypeMenu()
                    }
                    else -> {
                        showMainMenu()
                    }
                }
            } else {
                // FIXED: Always update back button visibility when not restoring
                updateBackButtonVisibility()

                if (currentNavState == NavigationState.MAIN_MENU) {
                    showMainMenu()
                }
            }

        } else {
            Log.d(TAG, "User is not signed in")

            // Update UI to show signed out state
            binding.layoutSignedOut.visibility = View.VISIBLE
            binding.layoutSignedIn.visibility = View.GONE
            binding.btnSignOut.visibility = View.GONE
            binding.btnHeaderBack.visibility = View.GONE

            // Clear any persisted state since user is signed out
            clearPersistedState()
        }
    }

    private fun restoreTextChatState() {
        // Directly show text chat without animations
        binding.layoutMainMenu.visibility = View.GONE
        binding.layoutChatTypeMenu.visibility = View.GONE
        binding.layoutTextChat.visibility = View.VISIBLE
        binding.layoutVideoChat.visibility = View.GONE

        // Set the appropriate background
        setChatBackground()

        // FIXED: Update back button BEFORE joining room
        updateBackButtonVisibility()

        // Rejoin the room
        lifecycleScope.launch {
            delay(100) // Small delay to ensure UI is ready
            currentChatRoom?.let { roomId ->
                chatViewModel.joinRoom(roomId, currentChatType == ChatType.SESH_CHAT)
                loadUserLikes()
                loadLocalMessages()
                setupNotificationToggle()
            }
        }
    }

    private fun signIn() {
        // Check for internet connection first
        if (!authManager.isNetworkAvailable()) {
            // Show the same no-internet popup used for public chat
            showNoInternetSignInDialog()
            return
        }

        // Only proceed with sign-in if we have internet
        val mainActivity = requireActivity() as? MainActivity
        mainActivity?.triggerGoogleSignIn()
    }

    private fun signOut() {
        lifecycleScope.launch {
            try {
                // End video call if active
                if (currentNavState == NavigationState.VIDEO_CHAT) {
                    endVideoCall()
                }

                // Leave current room if in one
                currentChatRoom?.let {
                    chatViewModel.leaveRoom(it)
                }

                authManager.signOut()
                checkAuthStatus()
                Toast.makeText(requireContext(), "Signed out from chat", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error signing out", e)
                Toast.makeText(requireContext(), "Error signing out", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showMainMenu() {
        Log.d(TAG, "Showing main menu")
        currentNavState = NavigationState.MAIN_MENU
        updateBackButtonVisibility()

        // Clear persisted state when explicitly returning to main menu
        clearPersistedState()

        // Stop the listener service when leaving chat
        ChatListenerService.stopService(requireContext())

        binding.layoutMainMenu.visibility = View.VISIBLE
        binding.layoutChatTypeMenu.visibility = View.GONE
        binding.layoutTextChat.visibility = View.GONE
        binding.layoutVideoChat.visibility = View.GONE
        binding.layoutChatLogs.visibility = View.GONE

        // Show main menu overlay background
        binding.mainMenuOverlayBackground?.visibility = View.VISIBLE

        // Re-create the buttons if they're missing
        if (binding.btnSeshChatContainer.childCount == 0) {
            createStyledMainMenuButtons()
        }

        currentChatType = ChatType.NONE
        currentChatRoom = null
    }
    private fun enterSeshChat() {
        Log.d(TAG, "💬 DEBUG: enterSeshChat() called")
        Log.d(TAG, "💬 DEBUG: Current navState BEFORE: $currentNavState")
        Log.d(TAG, "💬 DEBUG: Current chatType BEFORE: $currentChatType")
        Log.d(TAG, "💬 DEBUG: Current room BEFORE: $currentChatRoom")

        // Get current session share code from MainActivity
        val mainActivity = requireActivity() as? MainActivity
        val shareCode = mainActivity?.getCurrentShareCode()

        Log.d(TAG, "💬 DEBUG: Current share code: $shareCode")

        if (shareCode == null) {
            Log.e(TAG, "💬 DEBUG: ERROR - No share code in enterSeshChat (shouldn't happen)")
            // Try to restore the main menu since we're in a bad state
            binding.layoutMainMenu.visibility = View.VISIBLE
            binding.layoutMainMenu.alpha = 1f
            return
        }

        Log.d(TAG, "💬 DEBUG: Setting up sesh chat")
        currentChatType = ChatType.SESH_CHAT
        currentChatRoom = "sesh_$shareCode"
        Log.d(TAG, "💬 DEBUG: Set room to: $currentChatRoom")
        Log.d(TAG, "💬 DEBUG: Calling showChatTypeMenu()")
        showChatTypeMenu()
        Log.d(TAG, "💬 DEBUG: enterSeshChat() completed successfully")
    }

    private fun enterPublicChat() {
        Log.d(TAG, "🌐 DEBUG: enterPublicChat() called")
        Log.d(TAG, "🌐 DEBUG: Current navState BEFORE: $currentNavState")
        Log.d(TAG, "🌐 DEBUG: Current chatType BEFORE: $currentChatType")
        Log.d(TAG, "🌐 DEBUG: Current room BEFORE: $currentChatRoom")

        // We can skip the internet check here since it was done before animation
        // But let's keep it as a safety check
        if (!authManager.isNetworkAvailable()) {
            Log.e(TAG, "🌐 DEBUG: ERROR - No internet in enterPublicChat (shouldn't happen)")
            // Try to restore the main menu since we're in a bad state
            binding.layoutMainMenu.visibility = View.VISIBLE
            binding.layoutMainMenu.alpha = 1f
            return
        }

        // Proceed with navigation
        Log.d(TAG, "🌐 DEBUG: Setting up public chat")
        currentChatType = ChatType.PUBLIC_CHAT
        currentChatRoom = "public"
        Log.d(TAG, "🌐 DEBUG: Set room to: $currentChatRoom")
        Log.d(TAG, "🌐 DEBUG: Calling showChatTypeMenu()")
        showChatTypeMenu()
        Log.d(TAG, "🌐 DEBUG: enterPublicChat() completed successfully")
    }

// Replace these complete functions in your ChatFragment.kt


    private fun showChatTypeMenu() {
        Log.d(TAG, "📋 DEBUG: showChatTypeMenu() called")
        Log.d(TAG, "📋 DEBUG: currentChatType: $currentChatType")
        Log.d(TAG, "📋 DEBUG: currentChatRoom: $currentChatRoom")

        // ADD THIS CHECK to prevent showing menu if no room is set
        if (currentChatRoom == null) {
            Log.e(TAG, "📋 DEBUG: ERROR - currentChatRoom is null, cannot show chat type menu!")
            Log.d(TAG, "📋 DEBUG: Showing main menu instead")
            showMainMenu()
            return
        }

        Log.d(TAG, "📋 DEBUG: Changing navState from $currentNavState to CHAT_TYPE_MENU")

        currentNavState = NavigationState.CHAT_TYPE_MENU
        updateBackButtonVisibility()

        Log.d(TAG, "📋 DEBUG: Hiding other layouts, showing chat type menu")
        binding.layoutMainMenu.visibility = View.GONE
        binding.layoutChatTypeMenu.visibility = View.VISIBLE
        binding.layoutTextChat.visibility = View.GONE
        binding.layoutVideoChat.visibility = View.GONE

        // Set the correct overlay based on chat type
        val overlayImage = binding.chatTypeMenuOverlayBackground
        when (currentChatType) {
            ChatType.SESH_CHAT -> {
                overlayImage?.setImageResource(R.drawable.overlay_sesh_type_menu)
                overlayImage?.visibility = View.VISIBLE
            }
            ChatType.PUBLIC_CHAT -> {
                overlayImage?.setImageResource(R.drawable.overlay_public_type_menu)
                overlayImage?.visibility = View.VISIBLE
            }
            else -> {
                overlayImage?.visibility = View.GONE
            }
        }

        // Update title based on chat type
        val title = when (currentChatType) {
            ChatType.SESH_CHAT -> "Session Chat"
            ChatType.PUBLIC_CHAT -> "Public Chat"
            else -> "Chat"
        }
        Log.d(TAG, "📋 DEBUG: Setting title to: $title")
        binding.textChatTitle.text = title

        // Ensure Text/Video chat buttons exist
        if (binding.btnTextChatContainer.childCount == 0) {
            Log.d(TAG, "📋 DEBUG: Creating chat type menu buttons")
            createChatTypeMenuButtons()
        }

        Log.d(TAG, "📋 DEBUG: showChatTypeMenu() completed")
        Log.d(TAG, "📋 DEBUG: Final state - navState: $currentNavState, chatType: $currentChatType, room: $currentChatRoom")
    }

    private fun createChatTypeMenuButtons() {
        // Clear any existing buttons first
        binding.btnTextChatContainer.removeAllViews()
        binding.btnVideoChatContainer.removeAllViews()

        // Create Text Chat button
        val textChatButton = createChatButton(
            emoji = "💬",
            title = "Text Chat",
            subtitle = "Message chat",
            isPrimary = true,
            enableThrob = true
        ) {
            // Don't use performButtonAnimation here since we want custom transition
            showTextChat()
        }
        binding.btnTextChatContainer.addView(textChatButton)

        // Create Video Chat button
        val videoChatButton = createChatButton(
            emoji = "📹",
            title = "Video Chat",
            subtitle = "Video call",
            isPrimary = true,
            enableThrob = true
        ) {
            // Don't use performButtonAnimation here since we want custom transition
            showVideoChat()
        }
        binding.btnVideoChatContainer.addView(videoChatButton)
    }

    private fun showTextChat() {
        Log.d(TAG, "Showing text chat for room: $currentChatRoom")
        currentNavState = NavigationState.TEXT_CHAT
        updateBackButtonVisibility()

        // Hide any overlay backgrounds when entering actual chat
        binding.chatTypeMenuOverlayBackground?.visibility = View.GONE

        // Fade out chat type menu first
        performManualFadeOut(binding.layoutChatTypeMenu, 500L) {
            binding.layoutMainMenu.visibility = View.GONE
            binding.layoutChatTypeMenu.visibility = View.GONE
            binding.layoutTextChat.visibility = View.VISIBLE
            binding.layoutVideoChat.visibility = View.GONE

            // Fade in text chat
            performManualFadeIn(binding.layoutTextChat, 500L)

            // Set the appropriate background
            setChatBackground()

            // Persist the state when entering text chat
            persistCurrentState()

            // Clear old messages
            messageAdapter.submitList(emptyList())
            likedMessageIds.clear()

            // Setup notification toggle
            setupNotificationToggle()

            // Clear any existing notifications for this room
            currentChatRoom?.let { roomId ->
                notificationHelper.clearChatNotifications(roomId)

                // Start the background listener service for notifications
                val roomName = when (currentChatType) {
                    ChatType.SESH_CHAT -> {
                        val shareCode = roomId.removePrefix("sesh_")
                        "Session $shareCode"
                    }
                    ChatType.PUBLIC_CHAT -> "Public Chat"
                    else -> "Chat"
                }

                ChatListenerService.startService(
                    requireContext(),
                    roomId,
                    roomName,
                    currentChatType == ChatType.SESH_CHAT
                )
            }

            // Join the chat room
            currentChatRoom?.let { roomId ->
                Log.d(TAG, "Joining room: $roomId")
                chatViewModel.joinRoom(roomId, currentChatType == ChatType.SESH_CHAT)
                loadUserLikes()

                // Start presence refresh when entering chat
                startPresenceRefresh()

                // Retry syncing any unsynced likes
                retrySyncLikesToFirebase()
            }
        }
    }
    private fun showVideoChat() {
        currentNavState = NavigationState.VIDEO_CHAT
        updateBackButtonVisibility()

        // Hide any overlay backgrounds when entering video chat
        binding.chatTypeMenuOverlayBackground?.visibility = View.GONE

        // Fade out chat type menu first
        performManualFadeOut(binding.layoutChatTypeMenu, 500L) {
            binding.layoutMainMenu.visibility = View.GONE
            binding.layoutChatTypeMenu.visibility = View.GONE
            binding.layoutTextChat.visibility = View.GONE
            binding.layoutVideoChat.visibility = View.VISIBLE

            // Show video overlay background
            binding.videoChatOverlayBackground?.visibility = View.VISIBLE

            // Fade in video chat
            performManualFadeIn(binding.layoutVideoChat, 500L)

            // Check camera permission first
            if (!hasCameraPermission()) {
                requestCameraPermission()
                return@performManualFadeOut
            }

            // Start video call
            startVideoCall()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun cleanupStaleParticipants() {
        lifecycleScope.launch {
            try {
                val roomId = currentChatRoom ?: return@launch
                val firestore = FirebaseFirestore.getInstance()

                // Get all participants
                val participants = firestore.collection("video_rooms")
                    .document(roomId)
                    .collection("participants")
                    .get()
                    .await()

                val now = System.currentTimeMillis()
                val staleTimeout = 5 * 60 * 1000L // 5 minutes

                participants.documents.forEach { doc ->
                    val data = doc.data
                    val lastHeartbeat = data?.get("lastHeartbeat") as? Long ?: 0L
                    val isActive = data?.get("isActive") as? Boolean ?: false

                    // If participant hasn't updated heartbeat in 5 minutes and still marked active
                    if (isActive && (now - lastHeartbeat) > staleTimeout) {
                        Log.d(TAG, "Cleaning up stale participant: ${doc.id}")
                        doc.reference.update("isActive", false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning stale participants", e)
            }
        }
    }

    private fun startVideoCall() {
        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId()
            val roomId = currentChatRoom

            if (userId == null) {
                Log.e(TAG, "Cannot start video call - no user ID")
                Toast.makeText(requireContext(), "Please sign in first", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Get the smoker name from database, not from Google account
            val userName = getSmokerNameForUser(userId)

            Log.d(TAG, "Starting video call - userId: $userId, userName: $userName, roomId: $roomId")

            if (roomId == null) {
                Log.e(TAG, "Cannot start video call - no room ID")
                Toast.makeText(requireContext(), "No room selected", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Start the foreground service
            val serviceIntent = Intent(requireContext(), VideoCallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(serviceIntent)
            } else {
                requireContext().startService(serviceIntent)
            }

            // Clean up stale participants first
            cleanupStaleParticipants()

            // Initialize participant tracking
            participantNames.clear()
            videoViews.forEach { (id, view) ->
                if (id != "local") {
                    view.release()
                }
            }
            videoViews.clear()

            updateVideoTopBar()

            // Initialize WebRTC with smoker name
            webRTCManager = WebRTCManager(requireContext(), roomId, userId, userName)
            webRTCManager?.initialize()

            // Initialize signaling
            signalingService = VideoSignalingService()

            // Start local video
            val localVideoTrack = webRTCManager?.startLocalVideo()

            if (localVideoTrack == null) {
                Log.e(TAG, "Failed to start local video track")
                Toast.makeText(requireContext(), "Failed to start camera", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Setup local video
            Handler(Looper.getMainLooper()).postDelayed({
                setupLocalVideo()
            }, 100)

            // Start video call coroutine
            videoCallJob = lifecycleScope.launch {
                try {
                    // Join video room with smoker name
                    signalingService?.joinVideoRoom(roomId, userId, userName)

                    // Observe participants
                    launch {
                        signalingService?.observeParticipants(roomId)?.collect { participants ->
                            Log.d(TAG, "All participants in room: ${participants.size}")
                            participants.forEach { p ->
                                Log.d(TAG, "  - ${p.userId}: ${p.userName} (active: ${p.isActive})")
                            }

                            val otherParticipants = participants.filter {
                                it.userId != userId
                            }

                            Log.d(TAG, "Other participants (excluding self): ${otherParticipants.size}")
                            handleParticipants(otherParticipants)
                        }
                    }

                    // Observe signals
                    launch {
                        signalingService?.observeSignals(roomId, userId)?.collect { signal ->
                            Log.d(TAG, "Received signal: ${signal.type} from ${signal.senderId}")
                            handleSignal(signal)
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error in video call", e)
                    Toast.makeText(requireContext(), "Video call error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // Setup control buttons
            setupVideoControls()
        }
    }

    // Send log entry
    private fun setupLogInput() {
        binding.btnSendLog.setOnClickListener {
            sendLogEntry()
        }
    }

    private fun sendLogEntry() {
        val text = binding.editLogMessage.text.toString().trim()
        if (text.isEmpty()) return

        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId() ?: return@launch

            // Determine the correct room ID based on current log type
            val roomId = when (currentLogType) {
                LogType.SESH_LOGS -> {
                    // For sesh logs, use the current sesh room if available
                    val mainActivity = requireActivity() as? MainActivity
                    val shareCode = mainActivity?.getCurrentShareCode()
                    if (shareCode != null) {
                        "sesh_$shareCode"
                    } else {
                        "sesh_log_${userId}"
                    }
                }
                LogType.PUBLIC_LOGS -> "public"  // Use the actual public room
                else -> return@launch
            }

            val logMessage = ChatMessage(
                messageId = UUID.randomUUID().toString(),
                roomId = roomId,
                senderId = userId,
                senderName = authManager.getCurrentUserName() ?: "You",
                message = text,
                timestamp = System.currentTimeMillis(),
                isSynced = false,
                isDeleted = false
            )

            chatDao.insertMessage(logMessage)
            binding.editLogMessage.text.clear()

            // Reload messages
            when (currentLogType) {
                LogType.SESH_LOGS -> loadUserSeshMessages()
                LogType.PUBLIC_LOGS -> loadUserPublicMessages()
                else -> {}
            }
        }
    }

    private fun setupLocalVideo() {
        Log.d(TAG, "Setting up local video view")

        // Get the video container
        val videoContainer = binding.layoutVideoChat.findViewById<LinearLayout>(R.id.videoContainer)

        // Clear any existing views
        videoContainer.removeAllViews()
        videoViews.clear()

        // Get current user info
        val userId = authManager.getCurrentUserId() ?: return

        // Launch coroutine to get smoker name
        lifecycleScope.launch {
            val smokerName = getSmokerNameForUser(userId)
            Log.d(TAG, "🎥 Local user smoker name: $smokerName")

            // Create VideoStreamView for local video
            val videoStreamView = VideoStreamView(requireContext())

            // Initialize the surface view
            videoStreamView.initializeSurfaceView(webRTCManager?.eglBase!!)

            // Check if smoker name already contains "(You)"
            val displayName = if (smokerName.contains("(You)", ignoreCase = true)) {
                smokerName
            } else {
                "$smokerName (You)"
            }

            // Set user info (true for local user - report button will be hidden)
            videoStreamView.setUserInfo(userId, displayName, true)
            Log.d(TAG, "🎥 Set local video name: $displayName")

            // Set up self control callbacks
            videoStreamView.onSelfMuteAudioClick = { enabled ->
                Log.d(TAG, "🎥 Self mute audio: enabled=$enabled")
                webRTCManager?.toggleAudio(enabled)
                // Update the bottom control button too
                updateMicButton(enabled)
            }

            videoStreamView.onSelfHideVideoClick = { enabled ->
                Log.d(TAG, "🎥 Self hide video: enabled=$enabled")
                webRTCManager?.toggleVideo(enabled)
                // You can add a video button update here if you have one
            }

            // Set layout parameters
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val videoWidth = screenWidth / 3
            val videoHeight = (videoWidth * 16 / 9)
            val maxHeight = ((screenHeight - 200) * 0.7).toInt()
            val finalHeight = minOf(videoHeight, maxHeight)
            val finalWidth = if (videoHeight > maxHeight) {
                (finalHeight * 9 / 16)
            } else {
                videoWidth
            }

            val params = LinearLayout.LayoutParams(finalWidth, finalHeight).apply {
                setMargins(8, 0, 8, 0)
            }
            videoStreamView.layoutParams = params

            // Add to container
            videoContainer.addView(videoStreamView)

            // Get the actual SurfaceViewRenderer from inside VideoStreamView
            val localView = videoStreamView.findViewById<SurfaceViewRenderer>(R.id.videoRenderer)
            localView.setMirror(true) // Mirror for front camera
            videoViews["local"] = localView

            // Attach video track
            webRTCManager?.localVideoTrack?.let { track ->
                videoStreamView.setVideoTrack(track)
                Log.d(TAG, "Local video track attached to view")
            }
        }
    }



    private fun handleParticipants(participants: List<VideoSignalingService.ParticipantData>) {
        val userId = authManager.getCurrentUserId() ?: return

        Log.d(TAG, "=== HANDLE PARTICIPANTS START ===")
        Log.d(TAG, "My userId: $userId")
        Log.d(TAG, "Received ${participants.size} participants:")

        participants.forEach { p ->
            Log.d(TAG, "  Participant: ${p.userId} = ${p.userName} (active: ${p.isActive})")
        }

        // Clear participant names first
        participantNames.clear()

        // Process only OTHER active participants
        participants.forEach { participant ->
            if (participant.userId == userId) {
                Log.d(TAG, "SKIPPING SELF: ${participant.userId} == $userId")
                return@forEach
            }

            if (participant.isActive) {
                Log.d(TAG, "🎥 ====== REMOTE USER JOINED ======")
                Log.d(TAG, "🎥 Remote userId: ${participant.userId}")
                Log.d(TAG, "🎥 Remote userName from Firebase: ${participant.userName}")

                // Get the smoker name for this user
                lifecycleScope.launch {
                    val smokerName = getSmokerNameForUser(participant.userId)
                    Log.d(TAG, "🎥 Remote user smoker name: $smokerName")
                    participantNames[participant.userId] = smokerName

                    if (!videoViews.containsKey(participant.userId)) {
                        Log.d(TAG, "🎥 Creating video for remote user: $smokerName")
                        createPeerConnection(participant.userId, true)
                        // Setup remote video with logging
                        setupRemoteVideo(participant.userId)
                    }

                    updateVideoTopBar()
                }
            }
        }

        Log.d(TAG, "Final participant names: $participantNames")
        Log.d(TAG, "=== HANDLE PARTICIPANTS END ===")
    }

    private fun updateVideoTopBar() {
        lifecycleScope.launch {
            val textParticipants = binding.layoutVideoChat.findViewById<TextView>(R.id.textVideoParticipants)
            val textRoomName = binding.layoutVideoChat.findViewById<TextView>(R.id.textVideoRoomName)

            // Build participant list using smoker names
            val allParticipants = mutableListOf<String>()

            // Add self with smoker name
            val myUserId = authManager.getCurrentUserId()
            if (myUserId != null) {
                val myName = getSmokerNameForUser(myUserId)
                allParticipants.add("$myName (You)")
            }

            // Add other participants with their smoker names
            for ((userId, _) in participantNames) {
                val smokerName = getSmokerNameForUser(userId)
                allParticipants.add(smokerName)
            }

            textParticipants?.text = "In call: ${allParticipants.joinToString(", ")}"

            // Update room name
            val roomName = when (currentChatType) {
                ChatType.SESH_CHAT -> "Session Video Chat"
                ChatType.PUBLIC_CHAT -> "Public Video Chat"
                else -> "Video Chat"
            }
            textRoomName?.text = roomName
        }
    }

    private fun createPeerConnection(remoteUserId: String, isOffer: Boolean) {
        val userId = authManager.getCurrentUserId() ?: return
        val userName = authManager.getCurrentUserName() ?: "Unknown"
        val roomId = currentChatRoom ?: return

        webRTCManager?.createPeerConnection(
            remoteUserId,
            isOffer,
            onIceCandidate = { candidate ->
                lifecycleScope.launch {
                    // Convert IceCandidate to String (just the sdp part)
                    signalingService?.sendIceCandidate(roomId, userId, remoteUserId, candidate.sdp)
                }
            },
            onNegotiationNeeded = {
                if (isOffer) {
                    webRTCManager?.createOffer(remoteUserId) { sdp ->
                        lifecycleScope.launch {
                            // SessionDescription.description is the actual SDP string
                            signalingService?.sendOffer(roomId, userId, userName, remoteUserId, sdp.description)
                        }
                    }
                }
            }
        )

        // Setup remote video view
        setupRemoteVideo(remoteUserId)

        // Create offer if needed
        if (isOffer) {
            webRTCManager?.createOffer(remoteUserId) { sdp ->
                lifecycleScope.launch {
                    // SessionDescription.description is the actual SDP string
                    signalingService?.sendOffer(roomId, userId, userName, remoteUserId, sdp.description)
                }
            }
        }
    }

// Add this debug version of setupRemoteVideo to ChatFragment to see what's happening:

    private fun setupRemoteVideo(remoteUserId: String) {
        Log.d(TAG, "🎥 Setting up remote video for user: $remoteUserId")

        val videoContainer = binding.layoutVideoChat.findViewById<LinearLayout>(R.id.videoContainer)

        // Launch coroutine to get the correct smoker name
        lifecycleScope.launch {
            val smokerName = getSmokerNameForUser(remoteUserId)
            Log.d(TAG, "🎥 Remote user smoker name: $smokerName")

            // Update participant names with correct smoker name
            participantNames[remoteUserId] = smokerName

            // Create VideoStreamView
            val videoStreamView = VideoStreamView(requireContext())

            // Initialize the surface view
            videoStreamView.initializeSurfaceView(webRTCManager?.eglBase!!)

            // Set user info (false for remote user - all buttons will show)
            videoStreamView.setUserInfo(remoteUserId, smokerName, false)
            Log.d(TAG, "🎥 Set user info with smoker name: $smokerName, isLocal: false")

            // Set up control callbacks for LOCAL-ONLY muting of remote users
            videoStreamView.onMuteAudioClick = { userId, enabled ->
                Log.d(TAG, "🎥 Locally muting audio for $userId: enabled=$enabled")
                webRTCManager?.toggleRemoteAudio(userId, enabled)
            }

            videoStreamView.onHideVideoClick = { userId, enabled ->
                Log.d(TAG, "🎥 Locally hiding video for $userId: enabled=$enabled")
                webRTCManager?.toggleRemoteVideo(userId, enabled)
            }

            videoStreamView.onReportClick = { userId, name ->
                Log.d(TAG, "🎥 Report clicked for $userId ($name)")
                handleVideoReport(userId, name)
            }

            // Set layout parameters
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val videoWidth = screenWidth / 3
            val videoHeight = (videoWidth * 16 / 9)
            val maxHeight = ((screenHeight - 200) * 0.7).toInt()
            val finalHeight = minOf(videoHeight, maxHeight)
            val finalWidth = if (videoHeight > maxHeight) {
                (finalHeight * 9 / 16)
            } else {
                videoWidth
            }

            val params = LinearLayout.LayoutParams(finalWidth, finalHeight).apply {
                setMargins(8, 0, 8, 0)
            }
            videoStreamView.layoutParams = params

            // Add to container
            videoContainer.addView(videoStreamView)

            // Get the SurfaceViewRenderer from inside VideoStreamView
            val remoteView = videoStreamView.findViewById<SurfaceViewRenderer>(R.id.videoRenderer)
            videoViews[remoteUserId] = remoteView

            // Set up the callback for when the remote video track is received
            webRTCManager?.setOnRemoteVideoTrack { userId, track ->
                if (userId == remoteUserId) {
                    videoStreamView.setVideoTrack(track)
                    Log.d(TAG, "🎥 Remote video track attached for $remoteUserId")
                }
            }

            // Update the top bar with correct names
            updateVideoTopBar()
        }
    }


    private fun handleSignal(signal: VideoSignalingService.SignalData) {
        val userId = authManager.getCurrentUserId() ?: return
        val userName = authManager.getCurrentUserName() ?: "Unknown"
        val roomId = currentChatRoom ?: return

        when (signal.type) {
            "offer" -> {
                Log.d(TAG, "Received offer from ${signal.senderId}")

                if (!videoViews.containsKey(signal.senderId)) {
                    createPeerConnection(signal.senderId, false)
                }

                val sdp = SessionDescription(SessionDescription.Type.OFFER, signal.sdp)
                webRTCManager?.setRemoteDescription(signal.senderId, sdp)

                webRTCManager?.createAnswer(signal.senderId) { answer ->
                    lifecycleScope.launch {
                        // Use answer.description to get the SDP string
                        signalingService?.sendAnswer(roomId, userId, userName, signal.senderId, answer.description)
                    }
                }
            }

            "answer" -> {
                Log.d(TAG, "Received answer from ${signal.senderId}")
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, signal.sdp)
                webRTCManager?.setRemoteDescription(signal.senderId, sdp)
            }

            "ice_candidate" -> {
                Log.d(TAG, "Received ICE candidate from ${signal.senderId}")
                signal.candidate?.let { candidateSdp ->
                    val candidate = IceCandidate(
                        signal.sdpMid ?: "",
                        signal.sdpMLineIndex ?: 0,
                        candidateSdp
                    )
                    webRTCManager?.addIceCandidate(signal.senderId, candidate)
                }
            }
        }
    }

    private fun removeVideoView(userId: String) {
        Log.d(TAG, "Removing video view for: $userId")

        val videoContainer = binding.layoutVideoChat.findViewById<LinearLayout>(R.id.videoContainer)

        // Find and remove the VideoStreamView with matching tag
        for (i in 0 until videoContainer.childCount) {
            val child = videoContainer.getChildAt(i)
            if (child is VideoStreamView && child.tag == userId) {
                child.release()
                videoContainer.removeView(child)
                break
            }
        }

        videoViews.remove(userId)
        webRTCManager?.removePeerConnection(userId)
    }

    private fun loadMessagesWithLikes() {
        lifecycleScope.launch {
            currentChatRoom?.let { roomId ->
                try {
                    val firestore = FirebaseFirestore.getInstance()

                    // Listen to messages collection for real-time updates
                    firestore.collection("chat_rooms")
                        .document(roomId)
                        .collection("messages")
                        .orderBy("timestamp")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.e(TAG, "Error listening to messages", error)
                                return@addSnapshotListener
                            }

                            snapshot?.documentChanges?.forEach { change ->
                                val doc = change.document
                                val likeCount = doc.getLong("likeCount")?.toInt() ?: 0
                                val messageId = doc.getString("messageId") ?: return@forEach

                                // Update the like count in local database
                                lifecycleScope.launch {
                                    chatDao.updateMessageLikeCount(messageId, likeCount)
                                }
                            }
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading messages with likes", e)
                }
            }
        }
    }

    private fun setupVideoControls() {
        binding.btnToggleMic.setOnClickListener { view ->
            confettiHelper?.showMiniConfettiFromButton(view)
            val isEnabled = webRTCManager?.isAudioEnabled() ?: false
            webRTCManager?.toggleAudio(!isEnabled)
            updateMicButton(!isEnabled)
        }

        binding.btnEndCall.setOnClickListener { view ->
            confettiHelper?.showMiniConfettiFromButton(view)
            endVideoCall()
        }
    }

    private fun updateParticipantNames() {
        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId() ?: return@launch
            val roomId = currentChatRoom ?: return@launch

            // Get updated name from database
            val updatedName = getSmokerNameForUser(userId)

            // Update UI
            updateVideoTopBar()

            Toast.makeText(requireContext(), "Name updated to: $updatedName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateMicButton(enabled: Boolean) {
        binding.btnToggleMic.setImageResource(
            if (enabled) android.R.drawable.ic_btn_speak_now
            else android.R.drawable.ic_lock_silent_mode
        )
    }

        private fun endVideoCall() {
            Log.d(TAG, "Ending video call")

            // Cancel job first
            videoCallJob?.cancel()
            videoCallJob = null

            // Stop the foreground service
            val serviceIntent = Intent(requireContext(), VideoCallService::class.java)
            requireContext().stopService(serviceIntent)

            // Leave video room in Firebase
            lifecycleScope.launch {
                try {
                    val userId = authManager.getCurrentUserId()
                    val roomId = currentChatRoom
                    if (userId != null && roomId != null) {
                        signalingService?.leaveVideoRoom(roomId, userId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error leaving video room", e)
                }
            }

            // Clean up video views BEFORE disposing WebRTC
            videoViews.values.forEach { view ->
                try {
                    view.clearImage()
                    view.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing video view", e)
                }
            }
            videoViews.clear()

            val videoContainer = binding.layoutVideoChat.findViewById<LinearLayout>(R.id.videoContainer)
            videoContainer?.removeAllViews()

            // Dispose WebRTC last
            try {
                webRTCManager?.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing WebRTC", e)
            }
            webRTCManager = null
            signalingService = null

            // Clear participants
            participantNames.clear()

            // Reset video paused state
            isVideoPaused = false

            // Go back to chat type menu (don't call showChatTypeMenu here, let handleBackNavigation do it)
        }

    private fun reconnectToVideoCall() {
        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId() ?: return@launch
            val userName = getSmokerNameForUser(userId)
            val roomId = currentChatRoom ?: return@launch

            Log.d(TAG, "Attempting to reconnect to video call in room: $roomId")

            if (webRTCManager == null) {
                Log.e(TAG, "WebRTC Manager is null - need to restart video call")
                startVideoCall()
                return@launch
            }

            // Mark as active again with smoker name
            try {
                signalingService?.returnToVideoRoom(roomId, userId, userName)
                Log.d(TAG, "Marked as active in Firebase")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark as active in Firebase", e)
            }

            // Check if we need to recreate peer connections
            delay(1000) // Give Firebase time to update

            // Re-fetch participants and recreate connections if needed
            try {
                val firestore = FirebaseFirestore.getInstance()
                val participants = firestore.collection("video_rooms")
                    .document(roomId)
                    .collection("participants")
                    .whereEqualTo("isActive", true)
                    .get()
                    .await()

                Log.d(TAG, "Found ${participants.size()} active participants")

                participants.documents.forEach { doc ->
                    val participantId = doc.id
                    if (participantId != userId && !videoViews.containsKey(participantId)) {
                        Log.d(TAG, "Recreating connection for $participantId")
                        createPeerConnection(participantId, true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reconnecting to video call", e)
            }
        }
    }

    private fun sendMessage() {
        val messageText = binding.editMessage.text.toString().trim()
        Log.d(TAG, "🔍 DEBUG sendMessage(): messageText='$messageText'")

        if (messageText.isEmpty()) {
            Log.d(TAG, "🔍 DEBUG sendMessage(): Empty message, returning")
            return
        }

        val currentUserId = authManager.getCurrentUserId()
        Log.d(TAG, "🔍 DEBUG sendMessage(): currentUserId from authManager = '$currentUserId'")
        Log.d(TAG, "🔍 DEBUG sendMessage(): currentUserEmail = '${authManager.getCurrentUserEmail()}'")
        Log.d(TAG, "🔍 DEBUG sendMessage(): currentUserName = '${authManager.getCurrentUserName()}'")
        Log.d(TAG, "🔍 DEBUG sendMessage(): isSignedIn = ${authManager.isSignedIn}")

        if (isEditMode && editingMessage != null) {
            Log.d(TAG, "🔍 DEBUG sendMessage(): In EDIT mode for message: ${editingMessage?.messageId}")
            Log.d(TAG, "Editing message: ${editingMessage?.messageId}")
            currentChatRoom?.let { roomId ->
                editingMessage?.let { message ->
                    chatViewModel.editMessage(message.messageId, messageText)
                    cancelEdit()
                }
            }
        } else {
            Log.d(TAG, "🔍 DEBUG sendMessage(): In SEND mode")
            Log.d(TAG, "🔍 DEBUG sendMessage(): currentChatRoom = '$currentChatRoom'")
            Log.d(TAG, "Sending message: $messageText to room: $currentChatRoom")
            currentChatRoom?.let { roomId ->
                chatViewModel.sendMessage(roomId, messageText)
                binding.editMessage.text.clear()
            }
        }
    }

    private fun updateOnlineUsersList(users: List<ChatUser>) {
        val currentUserId = authManager.getCurrentUserId()
        val currentUserName = authManager.getCurrentUserName() ?: "You"

        val allUsers = mutableListOf<String>()

        // Add current user
        allUsers.add("$currentUserName (You)")

        // Add other online users
        users.filter { it.userId != currentUserId }.forEach {
            allUsers.add(it.userName)
        }

        val onlineText = "Online: ${allUsers.joinToString(", ")}"
        binding.textOnlineUsers.text = onlineText
    }

    private fun updateTypingIndicator(typingUsers: List<String>) {
        if (typingUsers.isEmpty()) {
            binding.textTypingIndicator.visibility = View.GONE
        } else {
            binding.textTypingIndicator.visibility = View.VISIBLE
            binding.textTypingIndicator.text = when {
                typingUsers.size == 1 -> "${typingUsers[0]} is typing..."
                typingUsers.size == 2 -> "${typingUsers[0]} and ${typingUsers[1]} are typing..."
                else -> "Several people are typing..."
            }
        }
    }

    private fun isUserInChat(): Boolean {
        // Check if we're in text chat navigation state
        if (currentNavState != NavigationState.TEXT_CHAT) {
            Log.d(TAG, "📔 isUserInChat: Not in TEXT_CHAT state (state=$currentNavState), returning false")
            return false
        }

        // Check if fragment is actually visible and resumed
        val isFragmentVisible = isVisible && isResumed && !isHidden

        // Check if activity is in foreground
        val isInForeground = requireActivity().lifecycle.currentState.isAtLeast(
            androidx.lifecycle.Lifecycle.State.RESUMED
        )

        // The key issue: When switching tabs, the fragment gets destroyed
        // So we need to check if we're actually the active fragment
        val mainActivity = activity as? MainActivity
        val viewPager = mainActivity?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
        val currentTab = viewPager?.currentItem ?: -1
        val isChatTabSelected = (currentTab == 5)

        val result = isFragmentVisible && isChatTabSelected && isInForeground && !isVideoPaused

        Log.d(TAG, "📔 isUserInChat: navState=${currentNavState}, " +
                "isFragmentVisible=$isFragmentVisible, isChatTabSelected=$isChatTabSelected (tab=$currentTab), " +
                "isInForeground=$isInForeground, videoPaused=$isVideoPaused, " +
                "result=$result")

        return result
    }

    private fun shouldShowNotification(message: ChatMessage): Boolean {
        Log.d(TAG, "📔 shouldShowNotification check:")

        // Don't show notification for own messages
        val currentUserId = authManager.getCurrentUserId()
        Log.d(TAG, "📔   Current user ID: $currentUserId")
        Log.d(TAG, "📔   Message sender ID: ${message.senderId}")

        if (message.senderId == currentUserId) {
            Log.d(TAG, "📔   ❌ Own message - no notification")
            return false
        }

        // Check if user is currently in the chat
        val isInChat = isUserInChat()
        Log.d(TAG, "📔   Is user in chat: $isInChat")

        // Check notification preferences
        val prefs = requireContext().getSharedPreferences("chat_notifications", Context.MODE_PRIVATE)
        val roomKey = "notifications_${currentChatRoom}"
        val defaultEnabled = currentChatType == ChatType.SESH_CHAT
        val notificationsEnabled = prefs.getBoolean(roomKey, defaultEnabled)
        Log.d(TAG, "📔   Notifications enabled: $notificationsEnabled")

        // Only show notification if:
        // 1. Not from current user
        // 2. User is not actively in the chat
        // 3. Notifications are enabled for this room
        val shouldShow = !isInChat && notificationsEnabled

        Log.d(TAG, "📔   Final decision: $shouldShow")
        return shouldShow
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(TAG, "Saving instance state - navState: $currentNavState, room: $currentChatRoom")

        // Save current navigation state
        outState.putString("nav_state", currentNavState.name)
        outState.putString("chat_type", currentChatType.name)
        outState.putString("current_room", currentChatRoom)

        // Save if we were in text chat
        if (currentNavState == NavigationState.TEXT_CHAT) {
            outState.putBoolean("was_in_text_chat", true)
            outState.putBoolean("notifications_enabled", notificationsEnabled)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore saved state if available
        savedInstanceState?.let { state ->
            val savedNavState = state.getString("nav_state")
            val savedChatType = state.getString("chat_type")
            val savedRoom = state.getString("current_room")

            Log.d(TAG, "Restoring saved state - navState: $savedNavState, room: $savedRoom")

            // Restore the navigation state
            savedNavState?.let {
                try {
                    currentNavState = NavigationState.valueOf(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring nav state", e)
                }
            }

            savedChatType?.let {
                try {
                    currentChatType = ChatType.valueOf(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring chat type", e)
                }
            }

            currentChatRoom = savedRoom

            // Restore notification preference
            if (state.getBoolean("was_in_text_chat", false)) {
                notificationsEnabled = state.getBoolean("notifications_enabled", false)
            }
        }

        // Also check shared preferences for persistent state
        restorePersistedState()
    }


    private fun restorePersistedState() {
        val prefs = requireContext().getSharedPreferences("chat_state", Context.MODE_PRIVATE)

        // Check if we have a persisted chat session
        val persistedRoom = prefs.getString("persisted_room", null)
        val persistedChatType = prefs.getString("persisted_chat_type", null)
        val persistedNavState = prefs.getString("persisted_nav_state", null)
        val sessionTimestamp = prefs.getLong("session_timestamp", 0)

        // Only restore if session is less than 30 minutes old
        val thirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000)

        if (persistedRoom != null && persistedChatType != null && persistedNavState != null
            && sessionTimestamp > thirtyMinutesAgo) {

            Log.d(TAG, "Restoring persisted chat state - room: $persistedRoom, state: $persistedNavState")

            try {
                currentChatRoom = persistedRoom
                currentChatType = ChatType.valueOf(persistedChatType)
                currentNavState = NavigationState.valueOf(persistedNavState)

                // Mark that we should restore the chat
                shouldRestoreChat = true

                Log.d(TAG, "State restoration prepared successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring persisted state", e)
                // Clear corrupted state
                clearPersistedState()
            }
        } else {
            Log.d(TAG, "No valid persisted state found or session too old")
        }
    }


    private fun persistCurrentState() {
        if (currentNavState == NavigationState.TEXT_CHAT && currentChatRoom != null) {
            val prefs = requireContext().getSharedPreferences("chat_state", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("persisted_room", currentChatRoom)
                putString("persisted_chat_type", currentChatType.name)
                putString("persisted_nav_state", currentNavState.name)
                putLong("session_timestamp", System.currentTimeMillis())
                apply()
            }

            Log.d(TAG, "Persisted chat state - room: $currentChatRoom")
        }
    }

    private fun clearPersistedState() {
        val prefs = requireContext().getSharedPreferences("chat_state", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "Cleared persisted chat state")
    }


    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called - fragment pausing")
        Log.d(TAG, "Current navigation state when pausing: $currentNavState")

        // Stop presence refresh
        presenceRefreshJob?.cancel()
        presenceRefreshJob = null

        // Persist the current state when pausing
        persistCurrentState()

        // Fragment is no longer visible to user
        if (currentNavState == NavigationState.TEXT_CHAT && currentChatRoom != null) {
            Log.d(TAG, "User leaving text chat view (app backgrounded or tab switched)")
            // Update timestamp but stay online (for app minimize case)
            chatViewModel.refreshPresence()
        }

        // If in video call, just pause the video but don't end the call
        if (currentNavState == NavigationState.VIDEO_CHAT) {
            isVideoPaused = true
            // Optionally disable video to save resources (but keep audio)
            webRTCManager?.toggleVideo(false)
            Log.d(TAG, "Video paused but call maintained")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("ChatFragment", "onResume called - fragment resuming")
        Log.d("ChatFragment", "Current navigation state: $currentNavState")
        Log.d("ChatFragment", "Fragment visibility: isVisible=$isVisible, isResumed=$isResumed")

        // Re-adjust input padding in case layout position changed while in another tab
        adjustInputPaddingForLayoutPosition()

        // Re-setup keyboard detection in case it changed
        Handler(Looper.getMainLooper()).postDelayed({
            setupKeyboardVisibilityDetection()
        }, 200)

        // Check if we're the currently selected tab
        val mainActivity = activity as? MainActivity
        val viewPager = mainActivity?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
        val currentTab = viewPager?.currentItem ?: -1
        Log.d(TAG, "Currently selected tab: $currentTab (Chat is tab 5)")

        // Always check auth status when resuming
        checkAuthStatus()

        // If we're in text chat AND we're the selected tab, handle resume properly
        if (currentNavState == NavigationState.TEXT_CHAT && currentChatRoom != null && currentTab == 5) {
            Log.d(TAG, "Resuming text chat in room: $currentChatRoom - clearing notifications")
            // Clear any existing notifications for this room since user is back
            notificationHelper.clearChatNotifications(currentChatRoom!!)

            // Refresh presence immediately to mark user as online with fresh timestamp
            chatViewModel.refreshPresence()

            // Start periodic presence refresh (lighter than heartbeat)
            startPresenceRefresh()

            // Re-join the room to update presence if needed
            lifecycleScope.launch {
                // Reload messages to ensure we have the latest
                loadLocalMessages()
            }
        }

        // If we were in a video call, restore it
        if (currentNavState == NavigationState.VIDEO_CHAT && isVideoPaused) {
            Log.d(TAG, "Resuming video call - was paused: $isVideoPaused")
            isVideoPaused = false

            // Re-enable video
            webRTCManager?.let { manager ->
                manager.toggleVideo(true)
                Log.d(TAG, "Video resumed - enabled: ${manager.isVideoEnabled()}")
            } ?: Log.e(TAG, "WebRTC Manager is null - cannot resume video")

            // Reconnect if needed
            reconnectToVideoCall()

            // Refresh the participant list
            Handler(Looper.getMainLooper()).postDelayed({
                debugVideoConnection()
            }, 500)
        }
    }

    private fun startPresenceRefresh() {
        presenceRefreshJob?.cancel()
        presenceRefreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(120_000L) // Update every 2 minutes (much less frequent than heartbeat)
                if (currentNavState == NavigationState.TEXT_CHAT && currentChatRoom != null) {
                    Log.d(TAG, "Refreshing presence timestamp")
                    chatViewModel.refreshPresence()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop called - fragment no longer visible")
        Log.d(TAG, "Current state: $currentNavState")

        // Mark user as offline when app goes to background
        if (currentNavState == NavigationState.TEXT_CHAT && currentChatRoom != null) {
            lifecycleScope.launch {
                Log.d(TAG, "Marking user as offline in onStop")
                chatViewModel.updateUserPresence(false)
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        Log.d(TAG, "onHiddenChanged: hidden=$hidden, currentNavState=$currentNavState")

        if (!hidden && currentNavState == NavigationState.TEXT_CHAT && currentChatRoom != null) {
            // Check if we're actually the selected tab
            val mainActivity = activity as? MainActivity
            val viewPager = mainActivity?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
            val currentTab = viewPager?.currentItem ?: -1

            if (currentTab == 5) { // Only clear if Chat tab is actually selected
                Log.d(TAG, "User returned to chat tab, clearing notifications")
                notificationHelper.clearChatNotifications(currentChatRoom!!)
            }
        }
    }

    fun onTabSelected() {
        Log.d(TAG, "📔 Chat tab selected")
        if (currentNavState == NavigationState.TEXT_CHAT && currentChatRoom != null) {
            Log.d(TAG, "📔 Clearing notifications for room: $currentChatRoom")
            notificationHelper.clearChatNotifications(currentChatRoom!!)
        }
    }

    fun onTabUnselected() {
        Log.d(TAG, "📔 Chat tab unselected")
        // When tab is unselected, the user is no longer viewing the chat
        // isUserInChat() will now return false, allowing notifications
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "Camera and audio permissions granted")
                // Permissions granted, start video call
                Handler(Looper.getMainLooper()).postDelayed({
                    startVideoCall()
                }, 500)
            } else {
                Toast.makeText(
                    requireContext(),
                    "Camera and microphone permissions are required for video chat",
                    Toast.LENGTH_LONG
                ).show()
                showChatTypeMenu()
            }
        }
    }

    private suspend fun getSmokerNameForUser(userId: String): String {
        return try {
            val smokerDao = AppDatabase.getDatabase(requireContext()).smokerDao()
            val smoker = smokerDao.getSmokerByCloudUserId(userId)
            smoker?.name ?: authManager.getCurrentUserName() ?: "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting smoker name", e)
            authManager.getCurrentUserName() ?: "Unknown"
        }
    }

    private fun debugVideoConnection() {
        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId() ?: "unknown"
            val roomId = currentChatRoom ?: "unknown"

            Log.d(TAG, "=== VIDEO CONNECTION DEBUG ===")
            Log.d(TAG, "My User ID: $userId")
            Log.d(TAG, "Room ID: $roomId")
            Log.d(TAG, "Room Type: $currentChatType")

            // Check Firebase to see who's in the room
            try {
                val firestore = FirebaseFirestore.getInstance()

                // First check if the room exists
                val roomDoc = firestore.collection("video_rooms")
                    .document(roomId)
                    .get()
                    .await()

                Log.d(TAG, "Room exists: ${roomDoc.exists()}")

                // Now check participants
                val participants = firestore.collection("video_rooms")
                    .document(roomId)
                    .collection("participants")
                    .get()
                    .await()

                Log.d(TAG, "Total participants in Firebase: ${participants.size()}")
                participants.documents.forEach { doc ->
                    val data = doc.data
                    Log.d(TAG, "  - ${doc.id}: ${data?.get("userName")} (active: ${data?.get("isActive")}, joinedAt: ${data?.get("joinedAt")})")
                }

                // Check only active participants
                val activeParticipants = firestore.collection("video_rooms")
                    .document(roomId)
                    .collection("participants")
                    .whereEqualTo("isActive", true)
                    .get()
                    .await()

                Log.d(TAG, "Active participants: ${activeParticipants.size()}")
                activeParticipants.documents.forEach { doc ->
                    val data = doc.data
                    Log.d(TAG, "  - Active: ${doc.id}: ${data?.get("userName")}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error checking Firebase: ${e.message}", e)
            }

            Log.d(TAG, "Local video views: ${videoViews.keys}")
            Log.d(TAG, "WebRTC Manager initialized: ${webRTCManager != null}")
            Log.d(TAG, "Signaling Service initialized: ${signalingService != null}")
            Log.d(TAG, "=== END DEBUG ===")
        }
    }

    private fun handleDeveloperDelete(message: ChatMessage) {
        Log.d(TAG, "🔧 ====== DEVELOPER DELETE START ======")
        Log.d(TAG, "🔧 Message ID: ${message.messageId}")
        Log.d(TAG, "🔧 Message text: ${message.message}")
        Log.d(TAG, "🔧 Current isDeveloperDeleted: ${message.isDeveloperDeleted}")
        Log.d(TAG, "🔧 Current isDeleted: ${message.isDeleted}")

        lifecycleScope.launch {
            try {
                // STEP 1: Mark as developer deleted in local database
                Log.d(TAG, "🔧 Step 1: Marking as developer deleted in database...")
                withContext(Dispatchers.IO) {
                    chatDao.markMessageDeveloperDeleted(message.messageId)
                    Log.d(TAG, "🔧 Database update completed")

                    // Wait a moment for database to commit
                    kotlinx.coroutines.delay(100)

                    // Verify the update worked by reading fresh from database
                    val updatedMessage = chatDao.getMessageByMessageId(message.messageId)
                    Log.d(TAG, "🔧 Verification - Updated message in DB:")
                    Log.d(TAG, "🔧   - ID: ${updatedMessage?.id}")
                    Log.d(TAG, "🔧   - MessageId: ${updatedMessage?.messageId}")
                    Log.d(TAG, "🔧   - isDeveloperDeleted: ${updatedMessage?.isDeveloperDeleted}")
                    Log.d(TAG, "🔧   - isDeleted: ${updatedMessage?.isDeleted}")
                    Log.d(TAG, "🔧   - message text: ${updatedMessage?.message}")

                    if (updatedMessage?.isDeveloperDeleted != true) {
                        Log.e(TAG, "🔧 ❌ ERROR: Database update failed! isDeveloperDeleted is still false!")
                        return@withContext
                    } else {
                        Log.d(TAG, "🔧 ✅ Database update successful! isDeveloperDeleted = true")
                    }
                }

                // STEP 2: Delete from Firebase
                Log.d(TAG, "🔧 Step 2: Deleting from Firebase...")
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("chat_messages")
                    .document(message.messageId)
                    .delete()
                    .await()

                Log.d(TAG, "🔧 Firebase deletion completed")

                // STEP 3: Force immediate UI update
                Log.d(TAG, "🔧 Step 3: Forcing UI update...")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Message developer deleted - should disappear", Toast.LENGTH_LONG).show()

                    // Log current adapter state BEFORE reload
                    Log.d(TAG, "🔧 Before reload - adapter item count: ${messageAdapter.itemCount}")

                    // Force reload messages from database (should exclude developer deleted)
                    loadLocalMessages()

                    // Wait a bit then check what was loaded
                    delay(500)

                    Log.d(TAG, "🔧 After loadLocalMessages - adapter item count: ${messageAdapter.itemCount}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "🔧 ❌ Error in developer delete", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to hide message: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        Log.d(TAG, "🔧 ====== DEVELOPER DELETE END ======")
    }



    private fun setupNotificationToggle() {
        Log.d(TAG, "📔 Setting up notification toggle")

        val btnNotificationToggle = binding.layoutTextChat.findViewById<ImageButton>(R.id.btnNotificationToggle)

        if (btnNotificationToggle == null) {
            Log.e(TAG, "📔 ❌ btnNotificationToggle is NULL!")
            return
        }

        // Load saved preference
        val prefs = requireContext().getSharedPreferences(PREF_NOTIFICATIONS, Context.MODE_PRIVATE)
        val roomKey = "notifications_${currentChatRoom}"

        // Default: Sesh chat ON, Public chat OFF
        val defaultEnabled = currentChatType == ChatType.SESH_CHAT
        notificationsEnabled = prefs.getBoolean(roomKey, defaultEnabled)

        Log.d(TAG, "📔 Room: $currentChatRoom")
        Log.d(TAG, "📔 Room key: $roomKey")
        Log.d(TAG, "📔 Default enabled: $defaultEnabled")
        Log.d(TAG, "📔 Loaded preference: $notificationsEnabled")

        // Update icon
        updateNotificationIcon(btnNotificationToggle)

        // Set click listener
        btnNotificationToggle.setOnClickListener { view ->
            confettiHelper?.showMiniConfettiFromButton(view)
            notificationsEnabled = !notificationsEnabled

            Log.d(TAG, "📔 Notification toggle clicked: now $notificationsEnabled")

            // Save preference
            prefs.edit().putBoolean(roomKey, notificationsEnabled).apply()

            // Update icon
            updateNotificationIcon(btnNotificationToggle)

            // Show toast
            val message = if (notificationsEnabled) {
                "Chat notifications enabled"
            } else {
                "Chat notifications disabled"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateNotificationIcon(button: ImageButton?) {
        button?.setImageResource(
            if (notificationsEnabled) {
                R.drawable.ic_notification_on
            } else {
                R.drawable.ic_notification_off
            }
        )

        // Always use white for visibility - the strikethrough shows it's disabled
        button?.setColorFilter(
            ContextCompat.getColor(requireContext(), android.R.color.white)
        )

        Log.d(TAG, "📔 Notification icon updated: enabled=$notificationsEnabled")
    }

    // Add this function to show the "no active sesh" popup
    private fun showNoActiveSeshDialog() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        currentDialog = dialog

        val dialogView = createThemedNoActiveSeshDialog(dialog)
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
            Log.d(TAG, "🏠 No active sesh dialog dismissed")
        }

        // Set initial alpha to 0 for fade-in
        dialogView.alpha = 0f

        dialog.show()

        // Apply fade-in animation with 2-second duration
        performManualFadeIn(dialogView, 2000L)
    }

    private fun createThemedNoActiveSeshDialog(dialog: Dialog): View {
        // Root container - full screen with center gravity
        val rootContainer = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Main card - CENTERED, not at bottom
        val mainCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 16.dpToPx().toFloat()
            cardElevation = 8.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER  // CENTER the card
                // Smaller margins for a more compact look
                setMargins(32.dpToPx(), 0, 32.dpToPx(), 0)
            }
        }

        //   line to tag the main card for the animation
        rootContainer.tag = mainCard

        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(), 20.dpToPx(), 20.dpToPx(), 20.dpToPx())
            // Set a fixed width for consistency
            layoutParams = ViewGroup.LayoutParams(
                280.dpToPx(),  // Fixed width for smaller dialog
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Warning Icon - using confused emoji
        val warningIcon = TextView(requireContext()).apply {
            text = "🤔"
            textSize = 36f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx()
            }
        }
        contentLayout.addView(warningIcon)

        // Title - neon green
        val titleText = TextView(requireContext()).apply {
            text = "NO ACTIVE SESSION"
            textSize = 18f
            setTextColor(Color.parseColor("#98FB98"))  // Neon green
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        // Message
        val messageText = TextView(requireContext()).apply {
            text = "Are you trying to join a sesh chat when not in a sesh?"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }
        contentLayout.addView(messageText)

        // Green divider line
        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2.dpToPx()
            ).apply {
                topMargin = 4.dpToPx()
                bottomMargin = 16.dpToPx()
            }
            setBackgroundColor(Color.parseColor("#3398FB98"))  // Green divider
        }
        contentLayout.addView(divider)

        // Woops! button (centered)
        val woopsButton = createThemedDialogButton("Woops!", true, Color.parseColor("#98FB98")) {
            animateCardSelection(dialog) {
                // Just dismiss
            }
        }
        woopsButton.layoutParams = LinearLayout.LayoutParams(
            120.dpToPx(),
            44.dpToPx()
        ).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        contentLayout.addView(woopsButton)

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

    // Add this function to show the "no internet for public chat" popup
    private fun showNoInternetPublicChatDialog() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        currentDialog = dialog

        val dialogView = createThemedNoInternetPublicChatDialog(dialog)
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
            Log.d(TAG, "🌐 No internet public chat dialog dismissed")
        }

        // Set initial alpha to 0 for fade-in
        dialogView.alpha = 0f

        dialog.show()

        // Apply fade-in animation with 2-second duration
        performManualFadeIn(dialogView, 2000L)
    }


    // Add this function after showNoInternetPublicChatDialog() - around line 3050
    private fun showNoInternetSignInDialog() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        currentDialog = dialog

        val dialogView = createThemedNoInternetSignInDialog(dialog)
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
            Log.d(TAG, "🌐 No internet sign-in dialog dismissed")
        }

        // Set initial alpha to 0 for fade-in
        dialogView.alpha = 0f

        dialog.show()

        // Apply fade-in animation with 2-second duration
        performManualFadeIn(dialogView, 2000L)
    }

    private fun createThemedNoInternetSignInDialog(dialog: Dialog): View {
        // Root container - full screen with center gravity
        val rootContainer = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Main card - CENTERED, not at bottom
        val mainCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 16.dpToPx().toFloat()
            cardElevation = 8.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER  // CENTER the card
                // Smaller margins for a more compact look
                setMargins(32.dpToPx(), 0, 32.dpToPx(), 0)
            }
        }

        // Tag the main card for animation
        rootContainer.tag = mainCard

        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(), 20.dpToPx(), 20.dpToPx(), 20.dpToPx())
            // Set a fixed width for consistency
            layoutParams = ViewGroup.LayoutParams(
                280.dpToPx(),  // Fixed width for smaller dialog
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Warning Icon - using WiFi off emoji
        val warningIcon = TextView(requireContext()).apply {
            text = "📵"
            textSize = 36f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx()
            }
        }
        contentLayout.addView(warningIcon)

        // Title - neon green
        val titleText = TextView(requireContext()).apply {
            text = "NO INTERNET CONNECTION"
            textSize = 18f
            setTextColor(Color.parseColor("#98FB98"))  // Neon green
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        // Message
        val messageText = TextView(requireContext()).apply {
            text = "Google Sign-In requires an internet connection.\n\nPlease check your connection and try again."
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }
        contentLayout.addView(messageText)

        // Green divider line
        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2.dpToPx()
            ).apply {
                topMargin = 4.dpToPx()
                bottomMargin = 16.dpToPx()
            }
            setBackgroundColor(Color.parseColor("#3398FB98"))  // Green divider
        }
        contentLayout.addView(divider)

        // OK button (centered)
        val okButton = createThemedDialogButton("OK", true, Color.parseColor("#98FB98")) {
            animateCardSelection(dialog) {
                // Just dismiss
            }
        }
        okButton.layoutParams = LinearLayout.LayoutParams(
            120.dpToPx(),
            44.dpToPx()
        ).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        contentLayout.addView(okButton)

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


    private fun createThemedNoInternetPublicChatDialog(dialog: Dialog): View {
        // Root container - full screen with center gravity
        val rootContainer = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Main card - CENTERED, not at bottom
        val mainCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 16.dpToPx().toFloat()
            cardElevation = 8.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#E64A4A4A"))

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER  // CENTER the card
                // Smaller margins for a more compact look
                setMargins(32.dpToPx(), 0, 32.dpToPx(), 0)
            }
        }

        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(), 20.dpToPx(), 20.dpToPx(), 20.dpToPx())
            // Set a fixed width for consistency
            layoutParams = ViewGroup.LayoutParams(
                280.dpToPx(),  // Fixed width for smaller dialog
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Warning Icon - using WiFi off emoji
        val warningIcon = TextView(requireContext()).apply {
            text = "📵"
            textSize = 36f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx()
            }
        }
        contentLayout.addView(warningIcon)

        // Title - neon green
        val titleText = TextView(requireContext()).apply {
            text = "NO INTERNET CONNECTION"
            textSize = 18f
            setTextColor(Color.parseColor("#98FB98"))  // Neon green
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        // Message
        val messageText = TextView(requireContext()).apply {
            text = "Sorry! You need internet to join public chat."
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }
        contentLayout.addView(messageText)

        // Green divider line
        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2.dpToPx()
            ).apply {
                topMargin = 4.dpToPx()
                bottomMargin = 16.dpToPx()
            }
            setBackgroundColor(Color.parseColor("#3398FB98"))  // Green divider
        }
        contentLayout.addView(divider)

        // OK button (centered)
        val okButton = createThemedDialogButton("OK", true, Color.parseColor("#98FB98")) {
            animateCardSelection(dialog) {
                // Just dismiss
            }
        }
        okButton.layoutParams = LinearLayout.LayoutParams(
            120.dpToPx(),
            44.dpToPx()
        ).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        contentLayout.addView(okButton)

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

    // Add this helper function for creating themed buttons (if not already present)
    private fun createThemedDialogButton(text: String, isPrimary: Boolean, color: Int, onClick: () -> Unit): View {
        val buttonContainer = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 20.dpToPx().toFloat()
            cardElevation = if (isPrimary) 4.dpToPx().toFloat() else 0f
            setCardBackgroundColor(
                if (isPrimary) color
                else Color.parseColor("#33FFFFFF")
            )

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            ).apply {
                bottomMargin = 12.dpToPx()
            }

            isClickable = true
            isFocusable = true
        }

        // Create a FrameLayout to hold background image and text
        val contentFrame = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Image view for pressed state (initially hidden)
        val imageView = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.button_pressed_background)
            visibility = View.GONE
        }

        // Text on top
        val buttonText = TextView(requireContext()).apply {
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



    // Update onDestroyView to only clean up when fragment is actually destroyed
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called - fragment being destroyed")
        
        // Clean up keyboard listener
        keyboardLayoutListener?.let { listener ->
            view?.rootView?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
        }
        keyboardListenerRegistered = false
        keyboardLayoutListener = null

        // Clean up animation handlers
        throbHandlers.forEach { it.removeCallbacksAndMessages(null) }
        throbHandlers.clear()
        fadeHandler.removeCallbacksAndMessages(null)

        // Stop presence refresh
        presenceRefreshJob?.cancel()
        presenceRefreshJob = null

        // Stop the chat listener service when view is destroyed
        ChatListenerService.stopService(requireContext())

        // Only clean up video call if we're actually destroying the view
        if (currentNavState == NavigationState.VIDEO_CHAT && !requireActivity().isChangingConfigurations) {
            Log.d(TAG, "Fragment destroyed - ending video call")
            endVideoCall()
        }

        // Leave room if in text chat
        currentChatRoom?.let {
            if (currentNavState == NavigationState.TEXT_CHAT) {
                chatViewModel.leaveRoom(it)
            }
        }

        _binding = null
    }
}