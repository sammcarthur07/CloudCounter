package com.sam.cloudcounter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit

/**
 * Modern card-based room list adapter with animations and effects
 */
class RoomListAdapter(
    private val rooms: List<RoomData>,
    private val onRoomTapped: (RoomData) -> Unit
) : RecyclerView.Adapter<RoomListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_modern, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int = rooms.size

    override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
        val room = rooms[pos]
        val context = holder.itemView.context

        // Set room name
        holder.roomName.text = room.name

        // Show lock icon if password protected (check if room has password field in your data)
        // TODO: Add password check if your RoomData has a password field
        holder.iconLock.visibility = View.GONE

        // Set user count badge
        val activeCount = room.activeParticipants.size
        val totalCount = room.participants.size
        holder.textUserCount.text = "$activeCount/$totalCount"

        // Set creation date
        holder.createdAt.text = "Created: ${formatDate(room.createdAt)}"

        // Calculate and show last active time
        val activities = room.safeActivities()
        val lastActivityTime = activities.maxByOrNull { it.timestamp }?.timestamp ?: room.createdAt
        holder.textLastActive.text = getLastActiveText(lastActivityTime)

        // Style based on activity status
        val isRoomActive = System.currentTimeMillis() - lastActivityTime < TimeUnit.HOURS.toMillis(1)
        styleCardForActivity(holder, isRoomActive)

        // Clear and populate participants
        holder.containerParticipants.removeAllViews()
        populateParticipants(holder.containerParticipants, room)

        // Set room totals
        holder.textRoomTotals.text = getRoomTotalsText(room)

        // Set click listener with morph animation
        holder.cardRoom.setOnClickListener { view ->
            // Trigger morph animation
            val morphAnim = AnimationUtils.loadAnimation(context, R.anim.morph_transition)
            view.startAnimation(morphAnim)

            // Add glow effect
            addGlowEffect(view as CardView)

            // Delay navigation slightly for animation to show
            view.postDelayed({
                onRoomTapped(room)
            }, 300)
        }
    }

    private fun styleCardForActivity(holder: ViewHolder, isActive: Boolean) {
        if (isActive) {
            // Show neon border for active rooms
            holder.neonBorder.visibility = View.VISIBLE

            // Animate the neon border with rotation
            startNeonAnimation(holder.neonBorder)

            // Increase elevation for "popping out" effect
            holder.cardRoom.cardElevation = 8.dpToPx(holder.itemView.context).toFloat()

            // Start shimmer animation
            startShimmerAnimation(holder.shimmerOverlay)

            // Make last active text green
            holder.textLastActive.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.my_light_primary)
            )
        } else {
            holder.neonBorder.visibility = View.GONE
            holder.neonBorder.clearAnimation()
            holder.cardRoom.cardElevation = 4.dpToPx(holder.itemView.context).toFloat()
            holder.shimmerOverlay.visibility = View.GONE
            holder.textLastActive.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.tab_unselected_text_color_on_grey)
            )
        }
    }

    private fun startNeonAnimation(view: View) {
        val rotate = ObjectAnimator.ofFloat(view, "rotation", 0f, 360f)
        rotate.duration = 3000
        rotate.repeatCount = ObjectAnimator.INFINITE
        rotate.interpolator = android.view.animation.LinearInterpolator()
        rotate.start()
    }

    private fun startShimmerAnimation(shimmerView: View) {
        shimmerView.visibility = View.VISIBLE

        val shimmer = ObjectAnimator.ofFloat(shimmerView, "translationX",
            -shimmerView.width.toFloat(), shimmerView.width.toFloat())
        shimmer.duration = 3000
        shimmer.repeatCount = ObjectAnimator.INFINITE
        shimmer.start()
    }

    private fun addGlowEffect(card: CardView) {
        val glow = ObjectAnimator.ofFloat(card, "cardElevation", 8f, 20f, 8f)
        glow.duration = 400
        glow.start()
    }

    private fun populateParticipants(container: LinearLayout, room: RoomData) {
        val context = container.context
        val stats = room.safeCurrentStats()
        val perSmokerStats = stats.perSmokerStats
        val activities = room.safeActivities()
        val currentTime = System.currentTimeMillis()
        val oneHourAgo = currentTime - TimeUnit.HOURS.toMillis(1)

        // Process all participants
        val allParticipants = mutableMapOf<String, String>()

        // Add regular participants
        room.participants.forEach { uid ->
            allParticipants[uid] = uid
        }

        // Add shared smokers
        room.safeSharedSmokers().forEach { (smokerUid, smokerData) ->
            val name = smokerData["name"] as? String ?: "Unknown"
            allParticipants[smokerUid] = name
        }

        allParticipants.forEach { (uid, defaultName) ->
            val participantView = LayoutInflater.from(context)
                .inflate(R.layout.item_participant_modern, container, false)

            val statusIcon = participantView.findViewById<TextView>(R.id.textStatusIcon)
            val nameText = participantView.findViewById<TextView>(R.id.textParticipantName)
            val statsText = participantView.findViewById<TextView>(R.id.textParticipantStats)

            // Get smoker info
            val smokerStats = perSmokerStats[uid]
            val smokerName = smokerStats?.smokerName ?: defaultName
            val displayName = if (smokerName == uid) {
                when {
                    uid.startsWith("local_") -> "Local Smoker"
                    else -> "Participant"
                }
            } else {
                smokerName
            }

            // Check if active
            val lastActivity = activities
                .filter { it.smokerId == uid }
                .maxByOrNull { it.timestamp }

            val isActiveRecently = lastActivity?.let {
                it.timestamp > oneHourAgo
            } ?: false

            // Set status icon
            statusIcon.text = if (isActiveRecently) "🟢" else "💤"

            // Set name
            nameText.text = displayName

            // Set stats (hiding zeros)
            val cones = smokerStats?.totalCones ?: 0
            val joints = smokerStats?.totalJoints ?: 0
            val bowls = smokerStats?.totalBowls ?: 0

            val statsParts = mutableListOf<String>()
            if (cones > 0) statsParts.add("$cones cones")
            if (joints > 0) statsParts.add("$joints joints")
            if (bowls > 0) statsParts.add("$bowls bowls")

            statsText.text = if (statsParts.isNotEmpty()) {
                statsParts.joinToString(" • ")
            } else {
                "No activities"
            }

            // Adjust text colors based on active status
            if (isActiveRecently) {
                nameText.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                nameText.setTextColor(ContextCompat.getColor(context, R.color.tab_unselected_text_color_on_grey))
            }

            container.addView(participantView)
        }
    }

    private fun getRoomTotalsText(room: RoomData): String {
        // Using the same approach as original adapter
        val stats = room.safeCurrentStats()
        val totalCones = stats.totalCones
        val totalJoints = stats.totalJoints
        val totalBowls = stats.totalBowls
        val totalActivities = totalCones + totalJoints + totalBowls

        return buildString {
            append("Room Total: ")
            if (totalActivities > 0) {
                val parts = mutableListOf<String>()
                if (totalCones > 0) parts.add("$totalCones cones")
                if (totalJoints > 0) parts.add("$totalJoints joints")
                if (totalBowls > 0) parts.add("$totalBowls bowls")
                append(parts.joinToString(" • "))
            } else {
                append("No activities yet")
            }
        }
    }

    private fun getLastActiveText(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Active now"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                "$minutes min ago"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "$hours hours ago"
            }
            else -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                "$days days ago"
            }
        }
    }

    private fun formatDate(ts: Long): String =
        DateFormat.format("MM/dd HH:mm", ts).toString()

    private fun Int.dpToPx(context: android.content.Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val cardRoom: CardView = v.findViewById(R.id.cardRoom)
        val neonBorder: View = v.findViewById(R.id.neonBorder)
        val shimmerOverlay: View = v.findViewById(R.id.shimmerOverlay)
        val roomName: TextView = v.findViewById(R.id.textRoomName)
        val iconLock: ImageView = v.findViewById(R.id.iconLock)
        val textUserCount: TextView = v.findViewById(R.id.textUserCount)
        val createdAt: TextView = v.findViewById(R.id.textCreatedAt)
        val textLastActive: TextView = v.findViewById(R.id.textLastActive)
        val containerParticipants: LinearLayout = v.findViewById(R.id.containerParticipants)
        val textRoomTotals: TextView = v.findViewById(R.id.textRoomTotals)
    }
}