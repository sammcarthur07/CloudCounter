package com.vibecode.cloudcounter

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class UserStatsDialog : DialogFragment() {
    
    companion object {
        private const val TAG = "UserStatsDialog"
        private const val THROB_DURATION = 2000L
        private const val INACTIVE_THRESHOLD = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
    }
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserStatsAdapter
    private lateinit var closeButton: MaterialButton
    
    private val db = FirebaseFirestore.getInstance()
    private var roomsListener: ListenerRegistration? = null
    private val userStatsMap = mutableMapOf<String, UserStats>()
    private val throbAnimators = mutableMapOf<String, ObjectAnimator>()
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_user_stats, container, false)
        
        recyclerView = view.findViewById(R.id.recyclerUserStats)
        closeButton = view.findViewById(R.id.btnClose)
        
        setupRecyclerView()
        setupCloseButton()
        startRealtimeListener()
        
        return view
    }
    
    private fun setupRecyclerView() {
        adapter = UserStatsAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }
    
    private fun setupCloseButton() {
        closeButton.setOnClickListener {
            dismiss()
        }
    }
    
    private fun startRealtimeListener() {
        Log.d(TAG, "🔥 Starting real-time listener for user stats")
        
        // First, let's also try to get the current user's rooms specifically
        testFetchRooms()
        
        // Listen to all rooms for real-time updates
        roomsListener = db.collection("rooms")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Listen failed", error)
                    return@addSnapshotListener
                }
                
                Log.d(TAG, "📊 Snapshot received with ${snapshot?.documents?.size ?: 0} rooms")
                
                // Process all rooms and build user stats
                userStatsMap.clear()
                
                snapshot?.documents?.forEach { doc ->
                    Log.d(TAG, "📁 Processing room document: ${doc.id}")
                    val room = doc.toObject(RoomData::class.java)
                    if (room == null) {
                        Log.w(TAG, "⚠️ Failed to parse room: ${doc.id}")
                        return@forEach
                    }
                    Log.d(TAG, "✅ Room parsed: ${room.name}, activities: ${room.activities?.size ?: 0}")
                    processRoomActivities(room)
                }
                
                // Update the adapter with sorted stats
                val sortedStats = userStatsMap.values
                    .sortedByDescending { it.lastActivityTime }
                    .toList()
                
                Log.d(TAG, "📈 Total user stats: ${sortedStats.size}")
                sortedStats.forEach { stats ->
                    Log.d(TAG, "👤 User: ${stats.userName} (${stats.userId}), Activities: ${stats.totalActivities}, Last: ${stats.lastActivityTime}")
                }
                
                adapter.updateStats(sortedStats)
                
                // Setup throb animations for active users
                sortedStats.forEach { userStats ->
                    if (isActiveUser(userStats)) {
                        startThrobAnimation(userStats.userId)
                    } else {
                        stopThrobAnimation(userStats.userId)
                    }
                }
            }
    }
    
    private fun processRoomActivities(room: RoomData) {
        val activities = room.safeActivities()
        Log.d(TAG, "🎯 Processing ${activities.size} activities from room: ${room.name}")
        
        // Group activities by user
        val userActivities = activities.groupBy { it.smokerId }
        Log.d(TAG, "👥 Found ${userActivities.size} unique users in room")
        
        userActivities.forEach { (userId, userActivityList) ->
            Log.d(TAG, "🔍 Processing user: $userId with ${userActivityList.size} activities")
            
            // Get or create user stats
            val userName = getUserName(userId, room)
            Log.d(TAG, "📝 User name resolved: $userName for ID: $userId")
            
            val stats = userStatsMap.getOrPut(userId) {
                UserStats(
                    userId = userId,
                    userName = userName,
                    totalActivities = 0,
                    activityBreakdown = mutableMapOf(),
                    firstActivityTime = Long.MAX_VALUE,
                    lastActivityTime = 0L
                )
            }
            
            // Update stats
            stats.totalActivities += userActivityList.size
            
            // Count activities by type
            userActivityList.forEach { activity ->
                val activityType = when {
                    activity.type.startsWith("CUSTOM_") -> activity.customActivityName ?: activity.type
                    else -> activity.type
                }
                Log.d(TAG, "📊 Activity type: $activityType, timestamp: ${activity.timestamp}")
                stats.activityBreakdown[activityType] = 
                    (stats.activityBreakdown[activityType] ?: 0) + 1
                
                // Update timestamps
                if (activity.timestamp < stats.firstActivityTime) {
                    stats.firstActivityTime = activity.timestamp
                }
                if (activity.timestamp > stats.lastActivityTime) {
                    stats.lastActivityTime = activity.timestamp
                }
            }
        }
    }
    
    private fun getUserName(userId: String, room: RoomData): String {
        Log.d(TAG, "🔎 Looking up name for userId: $userId")
        
        // Try to find user name from shared smokers
        room.sharedSmokers?.forEach { (key, smokerData) ->
            val smokerId = smokerData["smokerId"] as? String
            val cloudUserId = smokerData["cloudUserId"] as? String
            val name = smokerData["name"] as? String
            
            Log.d(TAG, "   Checking smoker: $key, smokerId=$smokerId, cloudUserId=$cloudUserId, name=$name")
            
            if (smokerId == userId || cloudUserId == userId) {
                val resolvedName = name ?: "Unknown User"
                Log.d(TAG, "✅ Found name: $resolvedName for userId: $userId")
                return resolvedName
            }
        }
        
        // Try to find from room participants info if available
        val fallbackName = when {
            userId.startsWith("local_") -> "Local User ${userId.takeLast(4)}"
            else -> "User ${userId.take(6)}"
        }
        Log.d(TAG, "⚠️ Using fallback name: $fallbackName for userId: $userId")
        return fallbackName
    }
    
    private fun isActiveUser(stats: UserStats): Boolean {
        val now = System.currentTimeMillis()
        return (now - stats.lastActivityTime) < INACTIVE_THRESHOLD
    }
    
    private fun startThrobAnimation(userId: String) {
        // Don't start if already animating
        if (throbAnimators.containsKey(userId)) return
        
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(
            adapter.getPositionForUserId(userId)
        ) as? UserStatsAdapter.ViewHolder
        
        viewHolder?.let { holder ->
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.02f, 1.0f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.02f, 1.0f)
            
            val animator = ObjectAnimator.ofPropertyValuesHolder(
                holder.cardView,
                scaleX,
                scaleY
            ).apply {
                duration = THROB_DURATION
                repeatCount = ObjectAnimator.INFINITE
            }
            
            animator.start()
            throbAnimators[userId] = animator
        }
    }
    
    private fun stopThrobAnimation(userId: String) {
        throbAnimators[userId]?.cancel()
        throbAnimators.remove(userId)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        roomsListener?.remove()
        throbAnimators.values.forEach { it.cancel() }
        throbAnimators.clear()
    }
    
    private fun testFetchRooms() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        Log.d(TAG, "🔍 Test fetch - Current user: ${currentUser?.uid} (${currentUser?.email})")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Try to fetch all rooms
                val allRooms = db.collection("rooms")
                    .get()
                    .await()
                
                Log.d(TAG, "📊 Found ${allRooms.documents.size} total rooms in Firestore")
                
                allRooms.documents.forEach { doc ->
                    Log.d(TAG, "📁 Room ID: ${doc.id}")
                    val room = doc.toObject(RoomData::class.java)
                    if (room != null) {
                        Log.d(TAG, "   Name: ${room.name}")
                        Log.d(TAG, "   Activities: ${room.activities?.size ?: 0}")
                        Log.d(TAG, "   Participants: ${room.participants?.joinToString() ?: "none"}")
                        Log.d(TAG, "   Shared Smokers: ${room.sharedSmokers?.size ?: 0}")
                        
                        // Log first few activities
                        room.activities?.take(3)?.forEach { activity ->
                            Log.d(TAG, "   Sample Activity: ${activity.type} by ${activity.smokerName} at ${activity.timestamp}")
                        }
                    }
                }
                
                // Also try to fetch rooms where the current user is a participant
                if (currentUser != null) {
                    val userRooms = db.collection("rooms")
                        .whereArrayContains("participants", currentUser.uid)
                        .get()
                        .await()
                    
                    Log.d(TAG, "👤 User ${currentUser.uid} is participant in ${userRooms.documents.size} rooms")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Test fetch failed", e)
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
    
    // Data classes
    data class UserStats(
        val userId: String,
        val userName: String,
        var totalActivities: Int,
        val activityBreakdown: MutableMap<String, Int>,
        var firstActivityTime: Long,
        var lastActivityTime: Long
    )
    
    // Adapter for RecyclerView
    inner class UserStatsAdapter : RecyclerView.Adapter<UserStatsAdapter.ViewHolder>() {
        
        private var statsList = listOf<UserStats>()
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        
        fun updateStats(newStats: List<UserStats>) {
            Log.d(TAG, "🔄 Adapter updating with ${newStats.size} user stats")
            statsList = newStats
            notifyDataSetChanged()
        }
        
        fun getPositionForUserId(userId: String): Int {
            return statsList.indexOfFirst { it.userId == userId }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_user_stats, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val stats = statsList[position]
            holder.bind(stats)
            
            // Start or stop throb animation based on activity
            if (isActiveUser(stats)) {
                Log.d(TAG, "🔥 Starting throb for active user: ${stats.userName}")
                // Stop any existing animation first
                stopThrobAnimation(stats.userId)
                // Start new animation after a brief delay
                holder.cardView.postDelayed({
                    startThrobAnimationForView(holder.cardView, stats.userId)
                }, 100)
            } else {
                Log.d(TAG, "⭕ No throb for inactive user: ${stats.userName}")
                stopThrobAnimation(stats.userId)
            }
        }
        
        override fun getItemCount(): Int = statsList.size
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: CardView = itemView.findViewById(R.id.cardView)
            private val borderOverlay: View = itemView.findViewById(R.id.borderOverlay)
            private val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
            private val tvTotalActivities: TextView = itemView.findViewById(R.id.tvTotalActivities)
            private val tvActivityBreakdown: TextView = itemView.findViewById(R.id.tvActivityBreakdown)
            private val tvFirstActivity: TextView = itemView.findViewById(R.id.tvFirstActivity)
            private val tvLastActivity: TextView = itemView.findViewById(R.id.tvLastActivity)
            private val tvActivityDuration: TextView = itemView.findViewById(R.id.tvActivityDuration)
            private val tvActiveStatus: TextView = itemView.findViewById(R.id.tvActiveStatus)
            
            fun bind(stats: UserStats) {
                Log.d(TAG, "🎨 Binding stats for: ${stats.userName}, total: ${stats.totalActivities}")
                tvUserName.text = stats.userName
                tvTotalActivities.text = "Total: ${stats.totalActivities} activities"
                
                // Format activity breakdown
                val breakdown = stats.activityBreakdown.entries
                    .sortedByDescending { it.value }
                    .joinToString("\n") { "${it.key}: ${it.value}" }
                tvActivityBreakdown.text = breakdown.ifEmpty { "No activities" }
                
                // Format timestamps
                if (stats.firstActivityTime != Long.MAX_VALUE) {
                    tvFirstActivity.text = "First: ${dateFormat.format(Date(stats.firstActivityTime))}"
                } else {
                    tvFirstActivity.text = "First: Never"
                }
                
                if (stats.lastActivityTime != 0L) {
                    tvLastActivity.text = "Last: ${dateFormat.format(Date(stats.lastActivityTime))}"
                } else {
                    tvLastActivity.text = "Last: Never"
                }
                
                // Calculate and display duration between first and last activity
                if (stats.firstActivityTime != Long.MAX_VALUE && stats.lastActivityTime != 0L) {
                    val durationMs = stats.lastActivityTime - stats.firstActivityTime
                    val durationText = formatDuration(durationMs)
                    tvActivityDuration.text = "Duration: $durationText"
                } else {
                    tvActivityDuration.text = "Duration: -"
                }
                
                // Show active status
                if (isActiveUser(stats)) {
                    tvActiveStatus.text = "ACTIVE"
                    tvActiveStatus.setTextColor(Color.parseColor("#00FF00"))
                    // Active cards get thicker border and more elevation
                    borderOverlay.background = ContextCompat.getDrawable(itemView.context, R.drawable.user_stats_card_border_active)
                    cardView.cardElevation = 8f
                } else {
                    tvActiveStatus.text = "INACTIVE"
                    tvActiveStatus.setTextColor(Color.GRAY)
                    // Inactive cards have thinner border and less elevation
                    borderOverlay.background = ContextCompat.getDrawable(itemView.context, R.drawable.user_stats_card_border)
                    cardView.cardElevation = 4f
                }
            }
        }
        
        private fun startThrobAnimationForView(view: View, userId: String) {
            Log.d(TAG, "🎬 Actually starting throb animation for view: $userId")
            // Cancel any existing animation
            throbAnimators[userId]?.cancel()
            
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.03f, 1.0f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.03f, 1.0f)
            
            val animator = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY).apply {
                duration = THROB_DURATION
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
            }
            
            animator.start()
            throbAnimators[userId] = animator
            Log.d(TAG, "✨ Throb animation started for $userId")
        }
        
        private fun formatDuration(durationMs: Long): String {
            if (durationMs <= 0) return "0s"
            
            val seconds = durationMs / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            val weeks = days / 7
            val months = days / 30 // Approximate
            val years = days / 365 // Approximate
            
            val result = mutableListOf<String>()
            
            var remainingSeconds = seconds
            
            if (years > 0) {
                result.add("${years}y")
                remainingSeconds -= years * 365 * 24 * 60 * 60
            }
            
            val remainingMonths = (remainingSeconds / (30 * 24 * 60 * 60)).toInt()
            if (remainingMonths > 0) {
                result.add("${remainingMonths}m")
                remainingSeconds -= remainingMonths * 30 * 24 * 60 * 60
            }
            
            val remainingWeeks = (remainingSeconds / (7 * 24 * 60 * 60)).toInt()
            if (remainingWeeks > 0) {
                result.add("${remainingWeeks}w")
                remainingSeconds -= remainingWeeks * 7 * 24 * 60 * 60
            }
            
            val remainingDays = (remainingSeconds / (24 * 60 * 60)).toInt()
            if (remainingDays > 0) {
                result.add("${remainingDays}d")
                remainingSeconds -= remainingDays * 24 * 60 * 60
            }
            
            val remainingHours = (remainingSeconds / (60 * 60)).toInt()
            if (remainingHours > 0) {
                result.add("${remainingHours}h")
                remainingSeconds -= remainingHours * 60 * 60
            }
            
            val remainingMinutes = (remainingSeconds / 60).toInt()
            if (remainingMinutes > 0) {
                result.add("${remainingMinutes}m")
                remainingSeconds -= remainingMinutes * 60
            }
            
            if (remainingSeconds > 0 || result.isEmpty()) {
                result.add("${remainingSeconds}s")
            }
            
            return result.joinToString(" ")
        }
    }
}