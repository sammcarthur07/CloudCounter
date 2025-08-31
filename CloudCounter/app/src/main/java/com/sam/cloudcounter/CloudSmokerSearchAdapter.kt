package com.sam.cloudcounter

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CloudSmokerSearchAdapter(
    private val smokers: List<CloudSmokerSearchResult>,
    private val repository: ActivityRepository,
    private val onSmokerSelected: (CloudSmokerSearchResult) -> Unit
) : RecyclerView.Adapter<CloudSmokerSearchAdapter.ViewHolder>() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cloud_smoker_modern, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val smoker = smokers[position]
        holder.bind(smoker)
    }

    override fun getItemCount() = smokers.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardSmoker: CardView = itemView.findViewById(R.id.cardSmoker)
        private val neonBorder: View = itemView.findViewById(R.id.neonBorder)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
        private val textName: TextView = itemView.findViewById(R.id.textSmokerName)
        private val iconLock: ImageView = itemView.findViewById(R.id.iconLock)
        private val textShareCode: TextView = itemView.findViewById(R.id.textShareCode)
        private val textActivityStats: TextView = itemView.findViewById(R.id.textActivityStats)
        private val textTotalActivities: TextView = itemView.findViewById(R.id.textTotalActivities)
        private val textLastActivity: TextView = itemView.findViewById(R.id.textLastActivity)

        fun bind(smoker: CloudSmokerSearchResult) {
            val context = itemView.context

            // Set smoker name
            textName.text = smoker.name

            // Show lock icon if password protected
            iconLock.visibility = if (smoker.hasPassword) View.VISIBLE else View.GONE

            // Update status indicator and apply effects for online smokers
            if (smoker.isOnline) {
                statusIndicator.setBackgroundResource(R.drawable.status_indicator_online)
                // Show neon border for online smokers
                neonBorder.visibility = View.VISIBLE

                // Animate the neon border
                val rotate = ObjectAnimator.ofFloat(neonBorder, "rotation", 0f, 360f)
                rotate.duration = 3000
                rotate.repeatCount = ObjectAnimator.INFINITE
                rotate.interpolator = android.view.animation.LinearInterpolator()
                rotate.start()

                // Increase elevation for "popping out" effect
                cardSmoker.cardElevation = 8.dpToPx(context).toFloat()
            } else {
                statusIndicator.setBackgroundResource(R.drawable.status_indicator_offline)
                neonBorder.visibility = View.GONE
                neonBorder.clearAnimation()
                cardSmoker.cardElevation = 4.dpToPx(context).toFloat()
            }

            // Style share code badge
            textShareCode.text = if (smoker.shareCode == "LOCAL") {
                "Local Smoker"
            } else {
                "Code: ${smoker.shareCode}"
            }

            // Handle activity stats display
            if (smoker.shareCode == "LOCAL") {
                // Fetch local smoker stats
                coroutineScope.launch {
                    val localSmoker = withContext(Dispatchers.IO) {
                        repository.getAllSmokersList()
                            .find { it.name == smoker.name && !it.isCloudSmoker }
                    }

                    if (localSmoker != null) {
                        val counts = withContext(Dispatchers.IO) {
                            repository.getActivityCounts(localSmoker.smokerId)
                        }

                        withContext(Dispatchers.Main) {
                            // Build stats text, hiding zeros
                            val statsParts = mutableListOf<String>()
                            if (counts.bowls > 0) statsParts.add("Bowls: ${counts.bowls}")
                            if (counts.joints > 0) statsParts.add("Joints: ${counts.joints}")
                            if (counts.cones > 0) statsParts.add("Cones: ${counts.cones}")

                            textActivityStats.text = if (statsParts.isNotEmpty()) {
                                statsParts.joinToString(" • ")
                            } else {
                                "No activities yet"
                            }

                            val total = counts.bowls + counts.joints + counts.cones
                            textTotalActivities.text = "Total activities: $total"
                        }
                    } else {
                        textActivityStats.text = "No activities yet"
                        textTotalActivities.text = "Total activities: 0"
                    }
                }
            } else {
                // For cloud smokers, show total activities
                // Note: Individual counts would need to be added to CloudSmokerSearchResult
                textActivityStats.text = "View profile for detailed stats"
                textTotalActivities.text = "Total activities: ${smoker.totalActivities}"
            }

            // Format last activity time with better styling
            textLastActivity.text = getLastActiveText(smoker.lastActivity)

            // Color code last activity based on recency
            val now = System.currentTimeMillis()
            val hourAgo = now - TimeUnit.HOURS.toMillis(1)

            if (smoker.lastActivity > hourAgo) {
                textLastActivity.setTextColor(
                    ContextCompat.getColor(context, R.color.my_light_primary)
                )
            } else {
                textLastActivity.setTextColor(
                    ContextCompat.getColor(context, R.color.tab_unselected_text_color_on_grey)
                )
            }

            // Set click listener with morph animation
            cardSmoker.setOnClickListener { view ->
                // Trigger morph animation
                val morphAnim = AnimationUtils.loadAnimation(context, R.anim.morph_transition)
                view.startAnimation(morphAnim)

                // Add glow effect
                addGlowEffect(view as CardView)

                // Delay selection slightly for animation to show
                view.postDelayed({
                    onSmokerSelected(smoker)
                }, 300)
            }
        }

        private fun getLastActiveText(timestamp: Long): String {
            if (timestamp <= 0) return "Never active"

            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "Last active: Just now"
                diff < TimeUnit.HOURS.toMillis(1) -> {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                    "Last active: $minutes min ago"
                }
                diff < TimeUnit.DAYS.toMillis(1) -> {
                    val hours = TimeUnit.MILLISECONDS.toHours(diff)
                    "Last active: $hours hours ago"
                }
                diff < TimeUnit.DAYS.toMillis(7) -> {
                    val days = TimeUnit.MILLISECONDS.toDays(diff)
                    "Last active: $days days ago"
                }
                else -> {
                    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    "Last active: ${formatter.format(Date(timestamp))}"
                }
            }
        }

        private fun addGlowEffect(card: CardView) {
            val glow = ObjectAnimator.ofFloat(card, "cardElevation", 8f, 20f, 8f)
            glow.duration = 400
            glow.start()
        }

        private fun Int.dpToPx(context: android.content.Context): Int {
            return (this * context.resources.displayMetrics.density).toInt()
        }
    }
}