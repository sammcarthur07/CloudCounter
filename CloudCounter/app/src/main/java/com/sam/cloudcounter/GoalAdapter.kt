package com.sam.cloudcounter

import android.animation.ValueAnimator
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.sam.cloudcounter.databinding.ItemGoalBinding
import kotlin.math.min
import kotlin.math.absoluteValue
import kotlin.math.abs

// Data class OUTSIDE of the adapter
data class SmokerProgress(
    val joints: Int = 0,
    val cones: Int = 0,
    val bowls: Int = 0
)

class GoalAdapter(
    private val onGoalClick: (Goal) -> Unit,
    private val onDeleteClick: (Goal) -> Unit,
    private val onPauseClick: (Goal) -> Unit,
    private val onCompleteClick: (Goal) -> Unit,
    private val onNotificationClick: (Goal) -> Unit,
    private val confettiHelper: ConfettiHelper? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items = listOf<Any>()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_GOAL = 1
        private const val TAG = "GoalAdapter"
    }

    fun submitList(newItems: List<Any>) {
        // Use DiffUtil for smarter updates that preserve animations
        val diffCallback = GoalDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is String -> TYPE_HEADER
            is Goal -> TYPE_GOAL
            else -> TYPE_GOAL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(android.R.layout.simple_list_item_1, parent, false)
                SectionViewHolder(view)
            }
            else -> {
                val binding = ItemGoalBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                GoalViewHolder(binding, onGoalClick, onDeleteClick, onPauseClick, onCompleteClick, onNotificationClick, confettiHelper)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SectionViewHolder -> {
                holder.bind(items[position] as String)
            }
            is GoalViewHolder -> {
                holder.bind(items[position] as Goal)
            }
        }
    }

    fun getItemAt(position: Int): Any? {
        return if (position < items.size) items[position] else null
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Handle partial updates for progress changes
            when (holder) {
                is GoalViewHolder -> {
                    val goal = items[position] as Goal
                    // For progress updates, do a partial update
                    // For other changes, do a full bind
                    if (payloads.contains("PROGRESS_UPDATE")) {
                        holder.updateProgress(goal)
                    } else {
                        holder.bind(goal)
                    }
                }
                else -> super.onBindViewHolder(holder, position, payloads)
            }
        }
    }

    override fun getItemCount() = items.size

    // DiffUtil callback for smart updates
    class GoalDiffCallback(
        private val oldList: List<Any>,
        private val newList: List<Any>
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]

            return when {
                oldItem is String && newItem is String -> oldItem == newItem
                oldItem is Goal && newItem is Goal -> oldItem.goalId == newItem.goalId
                else -> false
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]

            return when {
                oldItem is String && newItem is String -> oldItem == newItem
                oldItem is Goal && newItem is Goal -> {
                    // Check ALL fields including all notification fields
                    oldItem.goalName == newItem.goalName &&
                            oldItem.targetJoints == newItem.targetJoints &&
                            oldItem.targetCones == newItem.targetCones &&
                            oldItem.targetBowls == newItem.targetBowls &&
                            oldItem.currentJoints == newItem.currentJoints &&
                            oldItem.currentCones == newItem.currentCones &&
                            oldItem.currentBowls == newItem.currentBowls &&
                            oldItem.isPaused == newItem.isPaused &&
                            oldItem.isActive == newItem.isActive &&
                            oldItem.notificationsEnabled == newItem.notificationsEnabled &&  // Main field
                            oldItem.progressNotificationsEnabled == newItem.progressNotificationsEnabled &&  // Check this too
                            oldItem.completionNotificationsEnabled == newItem.completionNotificationsEnabled &&  // And this
                            oldItem.completedRounds == newItem.completedRounds
                }
                else -> false
            }
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]

            if (oldItem is Goal && newItem is Goal) {
                // Check if only progress changed
                if (oldItem.currentJoints != newItem.currentJoints ||
                    oldItem.currentCones != newItem.currentCones ||
                    oldItem.currentBowls != newItem.currentBowls) {
                    return "PROGRESS_UPDATE"
                }
            }
            return null
        }
    }

    // Section header ViewHolder
    class SectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(title: String) {
            textView.text = title
            textView.textSize = 16f
            textView.setTextColor(Color.parseColor("#666666"))
            textView.setPadding(16, 24, 16, 8)
            textView.setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    // GoalViewHolder class
    class GoalViewHolder(
        private val binding: ItemGoalBinding,
        private val onGoalClick: (Goal) -> Unit,
        private val onDeleteClick: (Goal) -> Unit,
        private val onPauseClick: (Goal) -> Unit,
        private val onCompleteClick: (Goal) -> Unit,
        private val onNotificationClick: (Goal) -> Unit,
        private val confettiHelper: ConfettiHelper? = null
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentGoal: Goal? = null
        private var currentGoalId: Long = -1
        private var lastKnownProgress = -1

        fun bind(goal: Goal) {
            // IMPORTANT: Tell the progress bar which goal it's bound to
            binding.circularProgressBar.setBoundGoalId(goal.goalId)


            currentGoalId = goal.goalId
            currentGoal = goal

            // Setup touch confetti on the progress bar
            binding.circularProgressBar.setOnTouchConfetti { tipX, tipY ->
                // Get the progress bar's location on screen
                val location = IntArray(2)
                binding.circularProgressBar.getLocationOnScreen(location)

                // Emit confetti from the tip position
                confettiHelper?.showProgressTipConfetti(
                    (location[0] + tipX).toFloat(),
                    (location[1] + tipY).toFloat(),
                    binding.circularProgressBar
                )
            }

            // Set goal name/description
            binding.textGoalName.text = getGoalDescription(goal)

            // Set goal type badge
            binding.textGoalType.text = when(goal.goalType) {
                GoalType.CURRENT_SESSION -> "Current Session"
                GoalType.ALL_SESSIONS -> "All Sessions"
                GoalType.TIME_BASED -> getTimeBasedDescription(goal)
            }

            // Show rounds for recurring goals
            if (goal.isRecurring && goal.completedRounds > 0) {
                binding.textRounds.text = "Rounds: ${goal.completedRounds}"
                binding.textRounds.visibility = View.VISIBLE
            } else {
                binding.textRounds.visibility = View.GONE
            }

            // Update progress
            updateProgressInternal(goal, animate = false)

            // Parse and show selected smokers with their individual progress
            val smokersText = when (goal.selectedSmokers) {
                "ALL", null, "" -> {
                    "All smokers"
                }
                else -> {
                    try {
                        val smokerNames = goal.selectedSmokers.split(",")
                        val progressMap = parseSmokerProgress(goal.smokerProgress)

                        if (smokerNames.size == 1) {
                            val name = smokerNames[0]
                            val progress = progressMap[name]
                            if (progress != null) {
                                val details = mutableListOf<String>()
                                if (progress.joints > 0) details.add("Joints ${progress.joints}")
                                if (progress.cones > 0) details.add("Cones ${progress.cones}")
                                if (progress.bowls > 0) details.add("Bowls ${progress.bowls}")

                                if (details.isNotEmpty()) {
                                    "For: $name (${details.joinToString(", ")})"
                                } else {
                                    "For: $name (0)"
                                }
                            } else {
                                "For: $name (0)"
                            }
                        } else {
                            "For: " + smokerNames.joinToString(", ") { name ->
                                val progress = progressMap[name]
                                if (progress != null) {
                                    val details = mutableListOf<String>()
                                    if (progress.joints > 0) details.add("Joints ${progress.joints}")
                                    if (progress.cones > 0) details.add("Cones ${progress.cones}")
                                    if (progress.bowls > 0) details.add("Bowls ${progress.bowls}")

                                    if (details.isNotEmpty()) {
                                        "$name (${details.joinToString(", ")})"
                                    } else {
                                        "$name (0)"
                                    }
                                } else {
                                    "$name (0)"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        val smokerNames = goal.selectedSmokers.split(",")
                        if (smokerNames.size == 1) {
                            "For: ${smokerNames[0]}"
                        } else {
                            "For: " + smokerNames.joinToString(", ")
                        }
                    }
                }
            }
            binding.textSelectedSmokers.text = smokersText
            binding.textSelectedSmokers.visibility = View.VISIBLE

            // Show status badges with dark text
            binding.chipRecurring.visibility = if (goal.isRecurring) View.VISIBLE else View.GONE
            binding.chipRecurring.setTextColor(Color.parseColor("#424242"))

            binding.chipPaused.visibility = if (goal.isPaused) View.VISIBLE else View.GONE
            binding.chipPaused.setTextColor(Color.parseColor("#424242"))

            // Show Active or Ended chip
            when {
                goal.isPaused -> {
                    binding.chipActive.visibility = View.GONE
                }
                !goal.isActive -> {
                    binding.chipActive.text = "Ended"
                    binding.chipActive.visibility = View.VISIBLE
                    binding.chipActive.chipBackgroundColor =
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#9E9E9E"))
                    binding.chipActive.setTextColor(Color.WHITE)
                }
                else -> {
                    binding.chipActive.text = "Active"
                    binding.chipActive.visibility = View.VISIBLE
                    binding.chipActive.chipBackgroundColor =
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#98FB98"))
                    binding.chipActive.setTextColor(Color.parseColor("#424242"))
                }
            }

            // Setup pause/play button
            if (goal.isPaused) {
                binding.buttonPause.setImageResource(android.R.drawable.ic_media_play)
                binding.buttonPause.contentDescription = "Resume Goal"
            } else {
                binding.buttonPause.setImageResource(android.R.drawable.ic_media_pause)
                binding.buttonPause.contentDescription = "Pause Goal"
            }

            // Setup complete/resume button
            if (!goal.isActive) {
                binding.buttonComplete.setImageResource(R.drawable.ic_refresh)
                binding.buttonComplete.contentDescription = "Resume Goal"
            } else {
                binding.buttonComplete.setImageResource(R.drawable.ic_check)
                binding.buttonComplete.contentDescription = "Complete Goal"
            }

            // Set the check button to match the delete button grey color
            binding.buttonComplete.setColorFilter(Color.parseColor("#BDBDBD"))


            // Setup notification button - check notificationsEnabled field
            Log.d("GoalAdapter", "🔔 Setting up notification button for goal ${goal.goalId}")
            Log.d("GoalAdapter", "🔔 notificationsEnabled = ${goal.notificationsEnabled}")

            if (goal.notificationsEnabled) {
                binding.buttonNotification.setImageResource(R.drawable.ic_notification)
                binding.buttonNotification.setColorFilter(Color.parseColor("#98FB98"))  // Green when ON
                binding.buttonNotification.contentDescription = "Notifications Enabled"
                Log.d("GoalAdapter", "🔔 Goal ${goal.goalId}: Notifications ON (green)")
            } else {
                binding.buttonNotification.setImageResource(R.drawable.ic_goal_notification_off)
                binding.buttonNotification.setColorFilter(Color.parseColor("#BDBDBD"))  // Grey when OFF
                binding.buttonNotification.contentDescription = "Notifications Disabled"
                Log.d("GoalAdapter", "🔔 Goal ${goal.goalId}: Notifications OFF (grey)")
            }

            // Set click listeners
            binding.root.setOnClickListener { onGoalClick(goal) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(goal) }
            binding.buttonPause.setOnClickListener { onPauseClick(goal) }
            binding.buttonComplete.setOnClickListener { onCompleteClick(goal) }
            binding.buttonNotification.setOnClickListener { onNotificationClick(goal) }

            // Show breakdown if multiple types
            val breakdown = mutableListOf<String>()
            if (goal.targetJoints > 0) breakdown.add("${goal.currentJoints}/${goal.targetJoints} joints")
            if (goal.targetCones > 0) breakdown.add("${goal.currentCones}/${goal.targetCones} cones")
            if (goal.targetBowls > 0) breakdown.add("${goal.currentBowls}/${goal.targetBowls} bowls")

            binding.textBreakdown.text = breakdown.joinToString(" • ")
            binding.textBreakdown.visibility = if (breakdown.size > 1) View.VISIBLE else View.GONE

            // Show time remaining for time-based goals
            if (goal.goalType == GoalType.TIME_BASED) {
                val timeRemaining = calculateTimeRemaining(goal)
                binding.textTimeRemaining.text = timeRemaining
                binding.textTimeRemaining.visibility = View.VISIBLE
            } else {
                binding.textTimeRemaining.visibility = View.GONE
            }
        }

        // Separate method for updating just the progress (for partial updates)
        fun updateProgress(goal: Goal, isUndo: Boolean = false) {
            currentGoal = goal
            binding.circularProgressBar.setBoundGoalId(goal.goalId)

            // Update progress with animation, passing the isUndo flag
            updateProgressInternal(goal, animate = true, isUndo = isUndo)

            // Also update the breakdown text as it shows current progress
            val breakdown = mutableListOf<String>()
            if (goal.targetJoints > 0) breakdown.add("${goal.currentJoints}/${goal.targetJoints} joints")
            if (goal.targetCones > 0) breakdown.add("${goal.currentCones}/${goal.targetCones} cones")
            if (goal.targetBowls > 0) breakdown.add("${goal.currentBowls}/${goal.targetBowls} bowls")

            binding.textBreakdown.text = breakdown.joinToString(" • ")
            binding.textBreakdown.visibility = if (breakdown.size > 1) View.VISIBLE else View.GONE

            // Update rounds if changed
            if (goal.isRecurring && goal.completedRounds > 0) {
                binding.textRounds.text = "Rounds: ${goal.completedRounds}"
                binding.textRounds.visibility = View.VISIBLE
            } else {
                binding.textRounds.visibility = View.GONE
            }
        }

        private fun updateProgressInternal(goal: Goal, animate: Boolean, isUndo: Boolean = false) {
            // Calculate progress
            val totalTarget = goal.targetJoints + goal.targetCones + goal.targetBowls
            val totalCurrent = goal.currentJoints + goal.currentCones + goal.currentBowls

            val progressPercentage = if (totalTarget > 0) {
                if (goal.allowOverflow) {
                    (totalCurrent * 100f / totalTarget).toInt()
                } else {
                    min((totalCurrent * 100f / totalTarget).toInt(), 100)
                }
            } else 0

            // Update progress text
            binding.textProgress.text = "$totalCurrent / $totalTarget"
            binding.textProgressPercentage.text = "$progressPercentage%"

            // Set progress percentage text color to main green
            binding.textProgressPercentage.setTextColor(Color.parseColor("#98FB98"))

            // Detect if this is a decrease (undo)
            val isProgressDecrease = lastKnownProgress != -1 && progressPercentage < lastKnownProgress

            // Update the progress bar with undo flag
            if (animate && lastKnownProgress != -1 && lastKnownProgress != progressPercentage) {
                if (isProgressDecrease || isUndo) {
                    // This is an undo - animate backwards
                    binding.circularProgressBar.setProgress(progressPercentage, goal.allowOverflow, animate = true, isUndo = true)
                } else {
                    // Normal forward animation
                    animateProgressWithEffects(
                        binding.circularProgressBar,
                        lastKnownProgress,
                        progressPercentage,
                        goal.allowOverflow
                    )
                }
            } else {
                // Just set it directly
                binding.circularProgressBar.setProgress(progressPercentage, goal.allowOverflow, animate = false)
            }

            lastKnownProgress = progressPercentage
        }

        // Helper function to parse smoker progress JSON
        private fun parseSmokerProgress(jsonString: String): Map<String, SmokerProgress> {
            val map = mutableMapOf<String, SmokerProgress>()
            try {
                val json = org.json.JSONObject(jsonString)
                json.keys().forEach { key ->
                    val smokerData = json.getJSONObject(key)
                    map[key] = SmokerProgress(
                        joints = smokerData.optInt("j", 0),
                        cones = smokerData.optInt("c", 0),
                        bowls = smokerData.optInt("b", 0)
                    )
                }
            } catch (e: Exception) {
                // Return empty map if parsing fails
            }
            return map
        }

        private fun getGoalDescription(goal: Goal): String {
            return if (!goal.goalName.isNullOrEmpty()) {
                goal.goalName
            } else {
                val parts = mutableListOf<String>()
                if (goal.targetJoints > 0) parts.add("${goal.targetJoints} Joints")
                if (goal.targetCones > 0) parts.add("${goal.targetCones} Cones")
                if (goal.targetBowls > 0) parts.add("${goal.targetBowls} Bowls")
                parts.joinToString(", ")
            }
        }

        private fun getTimeBasedDescription(goal: Goal): String {
            return when(goal.timeUnit) {
                TimeUnit.MINUTE -> "Every ${goal.timeDuration ?: 1} minute${if (goal.timeDuration != 1) "s" else ""}"
                TimeUnit.HOUR -> "Every ${goal.timeDuration ?: 1} hour${if (goal.timeDuration != 1) "s" else ""}"
                TimeUnit.DAY -> if (goal.timeDuration == 1) "Daily" else "Every ${goal.timeDuration} days"
                TimeUnit.WEEK -> if (goal.timeDuration == 1) "Weekly" else "Every ${goal.timeDuration} weeks"
                TimeUnit.FORTNIGHT -> "Fortnightly"
                TimeUnit.MONTH -> if (goal.timeDuration == 1) "Monthly" else "Every ${goal.timeDuration} months"
                TimeUnit.YEAR -> if (goal.timeDuration == 1) "Yearly" else "Every ${goal.timeDuration} years"
                else -> "Time Based"
            }
        }

        private fun calculateTimeRemaining(goal: Goal): String {
            if (goal.goalType != GoalType.TIME_BASED) return ""

            val now = System.currentTimeMillis()
            val elapsed = now - goal.lastResetAt
            val duration = getGoalDurationMillis(goal)
            val remaining = duration - elapsed

            if (remaining <= 0) return "Time's up!"

            val hours = remaining / (1000 * 60 * 60)
            val minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60)

            return when {
                hours > 24 -> "${hours / 24} days left"
                hours > 0 -> "$hours hours left"
                else -> "$minutes minutes left"
            }
        }

        private fun getGoalDurationMillis(goal: Goal): Long {
            val multiplier = goal.timeDuration?.toLong() ?: 1L
            return when(goal.timeUnit) {
                TimeUnit.MINUTE -> multiplier * 60 * 1000
                TimeUnit.HOUR -> multiplier * 60 * 60 * 1000
                TimeUnit.DAY -> multiplier * 24 * 60 * 60 * 1000
                TimeUnit.WEEK -> multiplier * 7 * 24 * 60 * 60 * 1000
                TimeUnit.FORTNIGHT -> multiplier * 14 * 24 * 60 * 60 * 1000
                TimeUnit.MONTH -> multiplier * 30 * 24 * 60 * 60 * 1000
                TimeUnit.YEAR -> multiplier * 365 * 24 * 60 * 60 * 1000
                else -> 0L
            }
        }

        // CLEAN ANIMATION METHOD WITH CONFETTI ONLY
        private fun animateProgressWithEffects(
            progressBar: CustomCircularProgressBar,
            fromProgress: Int,
            toProgress: Int,
            allowOverflow: Boolean
        ) {
            Log.d(TAG, "animateProgressWithEffects: from $fromProgress to $toProgress, allowOverflow: $allowOverflow")

            if (fromProgress == toProgress) {
                Log.d(TAG, "No change in progress, skipping animation")
                return
            }

            // Create a handler for continuous confetti emission
            val confettiHandler = android.os.Handler(android.os.Looper.getMainLooper())

            class ConfettiRunner {
                var runnable: Runnable? = null
                var isEmitting = false
                var animationStartTime = 0L
                val ANIMATION_DURATION = 5000L // 5 seconds
            }

            val confettiRunner = ConfettiRunner()

            progressBar.onAnimationStart = {
                Log.d(TAG, "Animation started - starting confetti emission for 5 seconds")
                confettiRunner.isEmitting = true
                confettiRunner.animationStartTime = System.currentTimeMillis()

                confettiRunner.runnable = object : Runnable {
                    override fun run() {
                        val elapsedTime = System.currentTimeMillis() - confettiRunner.animationStartTime

                        // Stop after 5 seconds even if animation continues (for overflow)
                        if (!confettiRunner.isEmitting || elapsedTime >= confettiRunner.ANIMATION_DURATION) {
                            Log.d(TAG, "Stopping confetti after ${elapsedTime}ms")
                            confettiRunner.isEmitting = false
                            return
                        }

                        // Get current tip position
                        val (tipX, tipY) = progressBar.getProgressTipPosition()
                        val location = IntArray(2)
                        progressBar.getLocationOnScreen(location)

                        // Emit confetti from tip
                        confettiHelper?.showProgressTipConfetti(
                            (location[0] + tipX).toFloat(),
                            (location[1] + tipY).toFloat(),
                            progressBar
                        )

                        // Continue emitting every 150ms
                        confettiRunner.runnable?.let {
                            confettiHandler.postDelayed(it, 150)
                        }
                    }
                }

                confettiRunner.runnable?.let {
                    confettiHandler.post(it)
                }

                // Also set a hard stop after 5 seconds as backup
                confettiHandler.postDelayed({
                    confettiRunner.isEmitting = false
                    confettiRunner.runnable?.let {
                        confettiHandler.removeCallbacks(it)
                    }
                    Log.d(TAG, "Force stopped confetti after 5 seconds")
                }, 5000)
            }

            progressBar.onAnimationEnd = {
                Log.d(TAG, "Animation ended - ensuring confetti is stopped")
                confettiRunner.isEmitting = false
                confettiRunner.runnable?.let {
                    confettiHandler.removeCallbacks(it)
                }

                // Celebration burst at 100%
                if (toProgress >= 100 && fromProgress < 100) {
                    Log.d(TAG, "100% celebration!")
                    confettiHelper?.showRainConfetti()
                }
            }

            // Start the progress animation
            progressBar.setProgress(toProgress, allowOverflow, true)
        }
    }
}