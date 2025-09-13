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
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textSubtitle: TextView = itemView.findViewById(R.id.textSubtitle)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        private val pulseDot: View = itemView.findViewById(R.id.pulseDot)
        private val cardContainer: CardView = itemView.findViewById(R.id.cardContainer)
        private val timestampFormatter = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())

        fun bind(log: ActivityLog) {
            // Check if this is a custom activity
            val isCustomActivity = !log.customActivityId.isNullOrEmpty()
            
            // Set icon based on activity type or custom activity
            iconEmoji.text = when {
                isCustomActivity -> "🌟"  // Special icon for custom activities
                log.type == ActivityType.CONE -> "🍦"
                log.type == ActivityType.JOINT -> "🚀"
                log.type == ActivityType.BOWL -> "🥣"
                else -> "🌿"
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

        private fun formatInterval(sec: Long): String {
            val m = sec / 60
            val s = sec % 60
            return if (m > 0) "${m}m ${s.toString().padStart(2,'0')}s" else "${s}s"
        }

        fun bind(summary: SessionSummary) {
            iconEmoji.text = "📊"

            // Use room name if available, otherwise "Local Session"
            val sessionTitle = if (!summary.roomName.isNullOrEmpty()) {
                summary.roomName
            } else {
                "Local Session"
            }
            textTitle.text = "$sessionTitle - ${summary.totalCones} cones"

            // Format subtitle with duration and timestamp
            val durText = formatInterval(summary.sessionLength / 1000)
            val timestampText = fmt.format(Date(summary.timestamp))
            textSubtitle.text = if (!summary.shareCode.isNullOrEmpty()) {
                "$durText • Code: ${summary.shareCode}"
            } else {
                "$durText • $timestampText"
            }

            // Add pulsing animation to dot
            startPulsingAnimation(pulseDot)

            btnDelete.setOnClickListener { view ->
                // Use mini confetti like the original
                confettiHelper?.showMiniConfettiFromButton(view)
                onDeleteSummary(summary)
            }

            cardContainer.setOnClickListener { view ->
                animateCardPress(cardContainer)
                // Use mini confetti for consistency
                confettiHelper?.showMiniConfettiFromButton(view)
                onResumeSummary(summary)
            }
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