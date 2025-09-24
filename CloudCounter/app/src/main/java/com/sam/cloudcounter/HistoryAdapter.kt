package com.sam.cloudcounter

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

private const val TYPE_ACTIVITY = 0
private const val TYPE_SUMMARY  = 1

class HistoryAdapter(
    private val repository: ActivityRepository,
    private val onDeleteLog: (ActivityLog) -> Unit,
    private val onDeleteSummary: (SessionSummary) -> Unit,
    private val onDeleteSummaryWithActivities: ((SessionSummary) -> Unit)? = null,
    private val onResumeSummary: (SessionSummary) -> Unit,
    private val confettiHelper: ConfettiHelper? = null
) : ListAdapter<HistoryItem, RecyclerView.ViewHolder>(HistoryItemDiffCallback()) {

    private val shimmerHandlers = mutableMapOf<RecyclerView.ViewHolder, Handler>()
    private val shimmerRunnables = mutableMapOf<RecyclerView.ViewHolder, Runnable>()

    override fun getItemViewType(position: Int) =
        when (getItem(position)) {
            is HistoryItem.ActivityItem -> TYPE_ACTIVITY
            is HistoryItem.SummaryItem  -> TYPE_SUMMARY
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_modern, parent, false)

        return if (viewType == TYPE_ACTIVITY) {
            ActivityLogViewHolder(view)
        } else {
            SummaryViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryItem.ActivityItem -> (holder as ActivityLogViewHolder).bind(item.log)
            is HistoryItem.SummaryItem  -> (holder as SummaryViewHolder).bind(item.summary)
        }
        startShimmerAnimation(holder)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        stopShimmerAnimation(holder)
        // Stop timer if this is a SummaryViewHolder
        if (holder is SummaryViewHolder) {
            holder.stopTimer()
        }
    }

    private fun startShimmerAnimation(holder: RecyclerView.ViewHolder) {
        val shimmerView = holder.itemView.findViewById<View>(R.id.shimmerOverlay) ?: return

        // Stop any existing animation
        stopShimmerAnimation(holder)

        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                // Create shimmer animation
                val animator = ObjectAnimator.ofFloat(shimmerView, "translationX",
                    -shimmerView.width.toFloat(), shimmerView.width.toFloat())
                animator.duration = 2000
                animator.addUpdateListener {
                    shimmerView.alpha = 0.3f * (1f - Math.abs(it.animatedFraction - 0.5f) * 2f)
                }
                animator.start()

                // Schedule next animation with random delay
                handler.postDelayed(this, 3000L + (Math.random() * 2000).toLong())
            }
        }

        // Start with random delay to desynchronize animations
        handler.postDelayed(runnable, (Math.random() * 3000).toLong())

        shimmerHandlers[holder] = handler
        shimmerRunnables[holder] = runnable
    }

    private fun stopShimmerAnimation(holder: RecyclerView.ViewHolder) {
        shimmerRunnables[holder]?.let { runnable ->
            shimmerHandlers[holder]?.removeCallbacks(runnable)
        }
        shimmerHandlers.remove(holder)
        shimmerRunnables.remove(holder)
    }

    private fun startPulsingAnimation(view: View) {
        val pulseAnimator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.3f, 1f)
        pulseAnimator.duration = 1500
        pulseAnimator.repeatCount = ValueAnimator.INFINITE
        pulseAnimator.start()
    }

    private fun animateCardPress(card: CardView) {
        val scaleX = ObjectAnimator.ofFloat(card, "scaleX", 1f, 0.95f, 1f)
        val scaleY = ObjectAnimator.ofFloat(card, "scaleY", 1f, 0.95f, 1f)
        scaleX.duration = 200
        scaleY.duration = 200
        scaleX.start()
        scaleY.start()
    }

    inner class ActivityLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconEmoji: TextView = itemView.findViewById(R.id.iconEmoji)
        private val iconDrawable: android.widget.ImageView = itemView.findViewById(R.id.iconDrawable)
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textSubtitle: TextView = itemView.findViewById(R.id.textSubtitle)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        private val pulseDot: View = itemView.findViewById(R.id.pulseDot)
        private val cardContainer: CardView = itemView.findViewById(R.id.cardContainer)
        private val timestampFormatter = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())

        fun bind(log: ActivityLog) {
            // Check if this is a custom activity
            val isCustomActivity = !log.customActivityId.isNullOrEmpty()
            
            // Reset visibility first
            iconEmoji.visibility = View.VISIBLE
            iconDrawable.visibility = View.GONE
            
            // Set icon based on activity type or custom activity
            when {
                isCustomActivity -> {
                    // Try to get the custom activity from the manager to retrieve its icon
                    val context = itemView.context
                    val customActivityManager = (context as? MainActivity)?.customActivityManager
                    val customActivity = customActivityManager?.getCustomActivities()
                        ?.find { it.id == log.customActivityId }
                    
                    // Special handling: Their Stash ledger entries should use the About tab icon
                    if (
                        log.customActivityId == THEIR_STASH_LEDGER_ID ||
                        log.customActivityId == MY_STASH_LEDGER_ID ||
                        log.payerStashOwnerId == "their_stash"
                    ) {
                        iconEmoji.visibility = View.GONE
                        iconDrawable.visibility = View.VISIBLE
                        // Use the same icon as About tab title
                        iconDrawable.setImageResource(R.drawable.ic_about_colored)
                    } else {
                    
                    // Handle icons - use drawables for bong, cough, stretch
                    when (customActivity?.iconResId) {
                        R.drawable.ic_bong, 
                        R.drawable.ic_cough, 
                        R.drawable.ic_stretch -> {
                            // Use drawable for these three
                            iconEmoji.visibility = View.GONE
                            iconDrawable.visibility = View.VISIBLE
                            iconDrawable.setImageResource(customActivity.iconResId)
                        }
                        R.drawable.ic_pills -> {
                            iconEmoji.text = "💊"
                        }
                        R.drawable.ic_cigarette -> {
                            iconEmoji.text = "🚬"
                        }
                        R.drawable.ic_water_glass -> {
                            iconEmoji.text = "💧"
                        }
                        else -> {
                            iconEmoji.text = "🌟" // Default star if no icon or not found
                        }
                    }
                    }
                }
                log.type == ActivityType.CONE -> {
                    iconEmoji.text = "🍦"
                }
                log.type == ActivityType.JOINT -> {
                    iconEmoji.text = "🚀"
                }
                log.type == ActivityType.BOWL -> {
                    iconEmoji.text = "🥣"
                }
                log.type == ActivityType.CIGARETTE -> {
                    iconEmoji.text = "🚬"
                }
                else -> {
                    iconEmoji.text = "🌿"
                }
            }

            // Get smoker name asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                val smoker = repository.getSmokerById(log.smokerId)
                withContext(Dispatchers.Main) {
                    val smokerName = smoker?.name ?: "Unknown"

                    // Display activity text based on type
                    val activityText = when {
                        isCustomActivity && !log.customActivityName.isNullOrEmpty() ->
                            "$smokerName - ${log.customActivityName}"
                        log.type == ActivityType.BOWL && log.bowlQuantity > 1 ->
                            "$smokerName - ${log.bowlQuantity} Bowls"
                        log.type == ActivityType.CIGARETTE ->
                            "$smokerName - Cigarette"
                        else ->
                            "$smokerName - ${log.type.name.lowercase().capitalize()}"
                    }

                    textTitle.text = activityText
                    textSubtitle.text = timestampFormatter.format(Date(log.timestamp))
                }
            }

            // Add pulsing animation to dot
            startPulsingAnimation(pulseDot)

            btnDelete.setOnClickListener { view ->
                // Use mini confetti like the original
                confettiHelper?.showMiniConfettiFromButton(view)

                // Launch coroutine to get smoker info before showing dialog
                CoroutineScope(Dispatchers.IO).launch {
                    val smoker = repository.getSmokerById(log.smokerId)

                    withContext(Dispatchers.Main) {
                        // Determine activity name for dialog
                        val activityName = if (!log.customActivityName.isNullOrEmpty()) {
                            log.customActivityName
                        } else {
                            log.type.name.lowercase()
                        }
                        
                        AlertDialog.Builder(itemView.context)
                            .setTitle("Delete Activity")
                            .setMessage("Delete this $activityName for ${smoker?.name ?: "Unknown"}?")
                            .setPositiveButton("Delete") { _, _ ->
                                handleActivityDeletion(itemView.context, log, smoker)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }

            // Card click effect
            cardContainer.setOnClickListener {
                animateCardPress(cardContainer)
            }
        }

        private fun handleActivityDeletion(context: Context, log: ActivityLog, smoker: Smoker?) {
            // Check if we're in a cloud session
            val prefs = context.getSharedPreferences("sesh", Context.MODE_PRIVATE)
            val currentShareCode = prefs.getString("currentShareCode", null)

            if (!currentShareCode.isNullOrEmpty() && smoker != null) {
                // Cloud session - remove from room
                CoroutineScope(Dispatchers.IO).launch {
                    val smokerUid = if (smoker.isCloudSmoker && !smoker.cloudUserId.isNullOrEmpty()) {
                        smoker.cloudUserId
                    } else {
                        "local_${smoker.uid}"
                    }

                    val sessionSyncService = SessionSyncService(repository = repository)
                    sessionSyncService.removeActivityFromRoom(
                        shareCode = currentShareCode,
                        smokerUid = smokerUid,
                        activityType = log.type,
                        timestamp = log.timestamp
                    ).fold(
                        onSuccess = {
                            Log.d("HistoryAdapter", "Successfully removed activity from cloud room")

                            // Force room refresh
                            CoroutineScope(Dispatchers.IO).launch {
                                sessionSyncService.forceRefreshRoom(currentShareCode)
                            }

                            // Delete locally
                            onDeleteLog(log)

                            // Send broadcast to update UI
                            sendActivityDeletedBroadcast(context, log, smoker)
                        },
                        onFailure = { error ->
                            Log.e("HistoryAdapter", "Failed to remove from cloud: ${error.message}")
                            // Still delete locally
                            onDeleteLog(log)
                            sendActivityDeletedBroadcast(context, log, smoker ?: Smoker(name = "Unknown"))
                        }
                    )
                }
            } else {
                // Local session - just delete
                onDeleteLog(log)
                sendActivityDeletedBroadcast(context, log, smoker ?: Smoker(name = "Unknown"))
            }
        }

        private fun sendActivityDeletedBroadcast(context: Context, log: ActivityLog, smoker: Smoker) {
            val intent = Intent("com.sam.cloudcounter.ACTIVITY_DELETED").apply {
                putExtra("activityType", log.type.name)
                putExtra("smokerId", log.smokerId)
                putExtra("smokerName", smoker.name)
                putExtra("timestamp", log.timestamp)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }

    inner class SummaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconEmoji: TextView = itemView.findViewById(R.id.iconEmoji)
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textSubtitle: TextView = itemView.findViewById(R.id.textSubtitle)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        private val pulseDot: View = itemView.findViewById(R.id.pulseDot)
        private val cardContainer: CardView = itemView.findViewById(R.id.cardContainer)
        private val fmt = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
        
        private var timerHandler: Handler? = null
        private var timerRunnable: Runnable? = null

        fun stopTimer() {
            timerRunnable?.let { runnable ->
                timerHandler?.removeCallbacks(runnable)
            }
            timerHandler = null
            timerRunnable = null
        }
        
        private fun startLiveUpdates(summary: SessionSummary, sessionTitle: String) {
            // Update UI immediately
            updateActiveSessionUI(summary, sessionTitle)
            
            // Set up timer for live duration updates
            timerHandler = Handler(Looper.getMainLooper())
            timerRunnable = object : Runnable {
                override fun run() {
                    updateActiveSessionUI(summary, sessionTitle)
                    // Update approximately every second
                    timerHandler?.postDelayed(this, 1000)
                }
            }
            // Start timer after 1 second
            timerHandler?.postDelayed(timerRunnable!!, 1000)
        }
        
        private fun updateActiveSessionUI(summary: SessionSummary, sessionTitle: String) {
            // Calculate live duration
            val currentTime = System.currentTimeMillis()
            val sessionStart = summary.timestamp - summary.sessionLength  // Original start time
            val liveDuration = currentTime - sessionStart
            
            // Parse activity breakdown to get total count
            val totalActivities = if (!summary.activityBreakdown.isNullOrEmpty()) {
                try {
                    val breakdown = org.json.JSONObject(summary.activityBreakdown)
                    var total = 0
                    breakdown.keys().forEach { key ->
                        total += breakdown.getInt(key)
                    }
                    total
                } catch (e: Exception) {
                    0
                }
            } else {
                0
            }
            
            textTitle.text = "$sessionTitle - $totalActivities activities"
            
            // Build subtitle with live duration and activity breakdown
            val subtitleText = StringBuilder()
            
            if (!summary.activityBreakdown.isNullOrEmpty()) {
                try {
                    val breakdown = org.json.JSONObject(summary.activityBreakdown)
                    val activities = mutableListOf<String>()
                    
                    breakdown.keys().forEach { key ->
                        val count = breakdown.getInt(key)
                        if (count > 0) {
                            val displayName = when {
                                key.contains("Stash +") && key.contains("($") -> {
                                    key.substringBefore(" ($")
                                }
                                else -> key
                            }
                            val pluralName = when {
                                count > 1 && displayName == "Joint" -> "${count} Joints"
                                count > 1 && displayName == "Cone" -> "${count} Cones"
                                count > 1 && displayName == "Bowl" -> "${count} Bowls"
                                count > 1 && displayName == "Cigarette" -> "${count} Cigarettes"
                                count == 1 && (displayName == "Joint" || displayName == "Cone" || displayName == "Bowl" || displayName == "Cigarette") -> "${count} $displayName"
                                else -> "${count} $displayName"
                            }
                            activities.add(pluralName)
                        }
                    }
                    
                    if (activities.isNotEmpty()) {
                        subtitleText.append(activities.joinToString("\n"))
                        subtitleText.append("\n")
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }
            
            // Add live duration
            val durText = formatInterval(liveDuration / 1000)
            subtitleText.append("Duration: $durText (ACTIVE)")
            
            textSubtitle.text = subtitleText.toString()
        }
        
        private fun formatInterval(sec: Long): String {
            if (sec <= 0) return "0s"
            
            val years = sec / 31536000 // 365 days
            val months = (sec % 31536000) / 2592000 // 30 days
            val weeks = (sec % 2592000) / 604800 // 7 days
            val days = (sec % 604800) / 86400 // 24 hours
            val hours = (sec % 86400) / 3600
            val minutes = (sec % 3600) / 60
            val seconds = sec % 60
            
            val parts = mutableListOf<String>()
            
            if (years > 0) parts.add("${years}y")
            if (months > 0) parts.add("${months}mo")
            if (weeks > 0) parts.add("${weeks}w")
            if (days > 0) parts.add("${days}d")
            if (hours > 0) parts.add("${hours}h")
            if (minutes > 0) parts.add("${minutes}m")
            if (seconds > 0) parts.add("${seconds}s")
            
            // If no parts (shouldn't happen with the check above), return 0s
            if (parts.isEmpty()) return "0s"
            
            // Join all parts with spaces
            return parts.joinToString(" ")
        }

        fun bind(summary: SessionSummary) {
            // Stop any existing timer
            stopTimer()
            
            iconEmoji.text = "📊"

            // Use room name if available, otherwise "Local Session"
            val sessionTitle = if (!summary.roomName.isNullOrEmpty()) {
                summary.roomName
            } else {
                "Local Session"
            }
            
            // If this is an active session, start live updates
            android.util.Log.d(
                "SeshFlow",
                "HistoryAdapter.SummaryViewHolder.bind: summaryId=${summary.id}, isActive=${summary.isActive}, room=${summary.roomName}, code=${summary.shareCode}"
            )
            if (summary.isActive) {
                android.util.Log.d(
                    "SeshFlow",
                    "HistoryAdapter: Active summary - attaching click listener and starting live updates (id=${summary.id})"
                )
                // Attach click listener even for active summaries so tapping navigates to live Sesh
                cardContainer.setOnClickListener { view ->
                    android.util.Log.d("SeshFlow", "HistoryAdapter: ACTIVE Summary clicked (id=${summary.id})")
                    animateCardPress(cardContainer)
                    confettiHelper?.showMiniConfettiFromButton(view)
                    onResumeSummary(summary)
                }
                startLiveUpdates(summary, sessionTitle)
                return
            }
            
            // Parse activity breakdown to get total count
            val totalActivities = if (!summary.activityBreakdown.isNullOrEmpty()) {
                try {
                    val breakdown = org.json.JSONObject(summary.activityBreakdown)
                    var total = 0
                    breakdown.keys().forEach { key ->
                        total += breakdown.getInt(key)
                    }
                    total
                } catch (e: Exception) {
                    summary.totalCones // Fallback to cones count
                }
            } else {
                summary.totalCones // Fallback for old sessions
            }
            
            textTitle.text = "$sessionTitle - $totalActivities activities"

            // Build subtitle with activity breakdown or fallback to duration/timestamp
            val subtitleText = StringBuilder()
            
            if (!summary.activityBreakdown.isNullOrEmpty()) {
                try {
                    val breakdown = org.json.JSONObject(summary.activityBreakdown)
                    val activities = mutableListOf<String>()
                    
                    breakdown.keys().forEach { key ->
                        val count = breakdown.getInt(key)
                        if (count > 0) {
                            // Format the activity name for display
                            val displayName = when {
                                key.contains("Stash +") && key.contains("($") -> {
                                    // Strip cost from stash activities
                                    key.substringBefore(" ($")
                                }
                                else -> key
                            }
                            // Pluralize standard activities
                            val pluralName = when {
                                count > 1 && displayName == "Joint" -> "${count} Joints"
                                count > 1 && displayName == "Cone" -> "${count} Cones"
                                count > 1 && displayName == "Bowl" -> "${count} Bowls"
                                count > 1 && displayName == "Cigarette" -> "${count} Cigarettes"
                                count == 1 && (displayName == "Joint" || displayName == "Cone" || displayName == "Bowl" || displayName == "Cigarette") -> "${count} $displayName"
                                else -> "${count} $displayName"
                            }
                            activities.add(pluralName)
                        }
                    }
                    
                    if (activities.isNotEmpty()) {
                        subtitleText.append(activities.joinToString("\n"))
                    }
                } catch (e: Exception) {
                    // Fallback to old format if parsing fails
                    subtitleText.append("${summary.totalCones} cones")
                }
            } else {
                // Old sessions without breakdown - show cones only
                subtitleText.append("${summary.totalCones} cones")
            }
            
            // Add duration and share code info on a new line
            subtitleText.append("\n")
            val durText = formatInterval(summary.sessionLength / 1000)
            if (!summary.shareCode.isNullOrEmpty()) {
                subtitleText.append("$durText • Code: ${summary.shareCode}")
            } else {
                val timestampText = fmt.format(Date(summary.timestamp))
                subtitleText.append("$durText • $timestampText")
            }
            
            textSubtitle.text = subtitleText.toString()

            // Add pulsing animation to dot
            startPulsingAnimation(pulseDot)

            btnDelete.setOnClickListener { view ->
                // Use mini confetti like the original
                confettiHelper?.showMiniConfettiFromButton(view)
                
                // Show deletion dialog with 3 options
                showSessionDeletionDialog(summary)
            }

            android.util.Log.d(
                "SeshFlow",
                "HistoryAdapter: Summary click listener ATTACHED (id=${summary.id}, isActive=${summary.isActive})"
            )
            cardContainer.setOnClickListener { view ->
                android.util.Log.d("SeshFlow", "HistoryAdapter: Summary clicked (id=${summary.id})")
                animateCardPress(cardContainer)
                // Use mini confetti for consistency
                confettiHelper?.showMiniConfettiFromButton(view)
                onResumeSummary(summary)
            }
        }
        
        private fun showSessionDeletionDialog(summary: SessionSummary) {
            val context = itemView.context
            
            // Create a themed dialog similar to smoker deletion
            val dialog = android.app.AlertDialog.Builder(context)
                .setTitle("Delete Session?")
                .setMessage("Session from ${formatTimestamp(summary.timestamp)}\n\nChoose deletion option:")
                .setPositiveButton("Delete Just Stats") { _, _ ->
                    // Delete only the session summary
                    onDeleteSummary(summary)
                }
                .setNeutralButton("Delete with Activities") { _, _ ->
                    // Delete session summary and all related activities
                    if (onDeleteSummaryWithActivities != null) {
                        onDeleteSummaryWithActivities.invoke(summary)
                    } else {
                        // Fallback to just deleting summary if the handler is not set
                        onDeleteSummary(summary)
                    }
                }
                .setNegativeButton("Cancel", null)
                .create()
                
            // Style the dialog buttons after showing
            dialog.show()
            
            // Style the buttons with appropriate colors
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(context.getColor(android.R.color.holo_orange_dark))
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.apply {
                setTextColor(context.getColor(android.R.color.holo_red_dark))
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(context.getColor(android.R.color.darker_gray))
            }
        }
        
        private fun formatTimestamp(timestamp: Long): String {
            val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(timestamp))
        }
    }
}

private class HistoryItemDiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
    override fun areItemsTheSame(old: HistoryItem, new: HistoryItem): Boolean =
        when {
            old is HistoryItem.ActivityItem && new is HistoryItem.ActivityItem ->
                old.log.id == new.log.id
            old is HistoryItem.SummaryItem  && new is HistoryItem.SummaryItem ->
                old.summary.id == new.summary.id
            else -> false
        }

    override fun areContentsTheSame(old: HistoryItem, new: HistoryItem): Boolean =
        old == new
}
