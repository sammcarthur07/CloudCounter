package com.sam.cloudcounter

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.sam.cloudcounter.databinding.FragmentSeshBinding

class SeshFragment : Fragment() {

    private var _binding: FragmentSeshBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionStatsVM: SessionStatsViewModel
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateTimerRunnable: Runnable
    private var currentPerSmokerStats: List<PerSmokerStats> = emptyList()

    // Add callback for resume button
    var onResumeSesh: (() -> Unit)? = null

    // ADD: Confetti helper
    private var confettiHelper: ConfettiHelper? = null

    // Track session state
    private var hasLoadedSummary = false
    private var isSessionActive = false
    private var sessionExplicitlyEnded = false

    // ADD: Method to receive confetti helper from MainActivity
    fun setConfettiHelper(helper: ConfettiHelper) {
        this.confettiHelper = helper
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSeshBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Fixed: Use simplified factory
        val factory = SessionStatsViewModelFactory()

        // Create the ViewModel using the factory
        sessionStatsVM = ViewModelProvider(requireActivity(), factory)
            .get(SessionStatsViewModel::class.java)

        // Set up resume button click listener WITH CONFETTI
        binding.fabResumeSesh.setOnClickListener {
            confettiHelper?.showCelebrationBurst(binding.fabResumeSesh)
            onResumeSesh?.invoke()
        }

        // ADD: Observe room info
        sessionStatsVM.roomInfo.observe(viewLifecycleOwner) { roomInfo ->
            if (roomInfo != null) {
                binding.textRoomName.text = roomInfo.roomName
                binding.textRoomCode.text = "Code: ${roomInfo.shareCode}"
                binding.textRoomName.visibility = View.VISIBLE
                binding.textRoomCode.visibility = View.VISIBLE
                Log.d("SeshFragment", "🏠 Room info displayed: ${roomInfo.roomName} (${roomInfo.shareCode})")
            } else {
                binding.textRoomName.text = "Local Session"
                binding.textRoomName.visibility = View.VISIBLE
                binding.textRoomCode.visibility = View.GONE
                Log.d("SeshFragment", "🏠 Local session - no room info")
            }
        }

        // Observe elapsed session time
        sessionStatsVM.elapsedTimeSec.observe(viewLifecycleOwner) { seconds: Long ->
            binding.textThisSesh.text = formatTime(seconds * 1000)
            Log.d("SeshFragment", "Updated elapsed time: ${formatTime(seconds * 1000)}")

            // FIXED: Only update session state from timer if not explicitly ended
            if (!sessionExplicitlyEnded) {
                val wasActive = isSessionActive
                val newActiveState = seconds > 0

                if (wasActive != newActiveState) {
                    Log.d("SeshFragment", "Timer detected session state change: $wasActive -> $newActiveState")
                    isSessionActive = newActiveState
                    updateResumeButtonVisibility()
                }
            } else {
                Log.d("SeshFragment", "Ignoring timer update - session was explicitly ended")
            }
        }

        // Observe group stats WITH rounds
        sessionStatsVM.groupStats.observe(viewLifecycleOwner) { gs ->
            Log.d("SeshFragment", "🔍 === GROUP STATS UPDATE ===")
            Log.d("SeshFragment", "🔍 Total cones: ${gs.totalCones}")
            Log.d("SeshFragment", "🔍 Total joints: ${gs.totalJoints}")
            Log.d("SeshFragment", "🔍 Total bowls: ${gs.totalBowls}")
            Log.d("SeshFragment", "🔍 Last gap ms: ${gs.lastGapMs}")
            Log.d("SeshFragment", "🔍 Previous gap ms: ${gs.previousGapMs}")
            Log.d("SeshFragment", "🔍 Since last gap ms: ${gs.sinceLastGapMs}")

            // Build the stats with line breaks
            val spannableStats = SpannableStringBuilder()

            // First line: Total cones | Rounds | Last cone (with bold counters)
            spannableStats.append("Total cones: ")
            val conesStart = spannableStats.length
            spannableStats.append(gs.totalCones.toString())
            val conesEnd = spannableStats.length
            spannableStats.setSpan(
                StyleSpan(Typeface.BOLD),
                conesStart,
                conesEnd,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            spannableStats.append("  |  Rounds: ")
            val roundsStart = spannableStats.length
            spannableStats.append(gs.totalRounds.toString())
            val roundsEnd = spannableStats.length
            spannableStats.setSpan(
                StyleSpan(Typeface.BOLD),
                roundsStart,
                roundsEnd,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Add Last cone on same line
            spannableStats.append("  |  Last cone: ")
            val lastConeStart = spannableStats.length
            spannableStats.append(formatTime(gs.sinceLastGapMs))
            val lastConeEnd = spannableStats.length
            spannableStats.setSpan(
                StyleSpan(Typeface.BOLD),
                lastConeStart,
                lastConeEnd,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableStats.append(" ago")

            // Add two line breaks
            spannableStats.append("\n\n")

            // Second line: Last gap | Longest | Shortest (with bold timers)
            spannableStats.append("Last gap: ")

            // DEBUG: Log before adding last gap
            Log.d("SeshFragment", "🔍 About to add last gap - value: ${gs.lastGapMs}, check: ${gs.lastGapMs != null && gs.lastGapMs > 0}")

            // Add last gap value (only if we have a last gap)
            if (gs.lastGapMs != null && gs.lastGapMs > 0) {
                val lastGapStart = spannableStats.length
                val formattedGap = formatTime(gs.lastGapMs)
                spannableStats.append(formattedGap)
                val lastGapEnd = spannableStats.length
                spannableStats.setSpan(
                    StyleSpan(Typeface.BOLD),
                    lastGapStart,
                    lastGapEnd,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                Log.d("SeshFragment", "🔍 Added last gap text: '$formattedGap'")

                // Add comparison with previous gap if available
                if (gs.previousGapMs != null && gs.previousGapMs > 0) {
                    val difference = gs.lastGapMs - gs.previousGapMs
                    val comparison = if (difference > 0) {
                        " (${formatTime(kotlin.math.abs(difference))} longer)"
                    } else if (difference < 0) {
                        " (${formatTime(kotlin.math.abs(difference))} shorter)"
                    } else {
                        " (same)"
                    }
                    spannableStats.append(comparison)
                    Log.d("SeshFragment", "🔍 Added comparison: '$comparison'")
                } else {
                    Log.d("SeshFragment", "🔍 No previous gap for comparison")
                }
            } else {
                Log.d("SeshFragment", "🔍 NO LAST GAP DATA - lastGapMs is null or 0")
            }

            spannableStats.append("  |  Longest: ")
            val longestStart = spannableStats.length
            spannableStats.append(formatTime(gs.longestGapMs))
            val longestEnd = spannableStats.length
            spannableStats.setSpan(
                StyleSpan(Typeface.BOLD),
                longestStart,
                longestEnd,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            spannableStats.append("  |  Shortest: ")
            val shortestStart = spannableStats.length
            spannableStats.append(formatTime(gs.shortestGapMs))
            val shortestEnd = spannableStats.length
            spannableStats.setSpan(
                StyleSpan(Typeface.BOLD),
                shortestStart,
                shortestEnd,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // DEBUG: Log final text
            Log.d("SeshFragment", "🔍 Final stats text: '${spannableStats.toString()}'")

            binding.textGroupTotalCones.text = spannableStats

            // Build text for who had last activities
            val lastActivitiesText = StringBuilder()
            
            // Add last cone info
            if (gs.lastConeSmokerName != null && gs.sinceLastGapMs > 0) {
                lastActivitiesText.append("${gs.lastConeSmokerName} had the last cone ${formatTime(gs.sinceLastGapMs)} ago")
            }
            
            // Add last joint info
            if (gs.lastJointSmokerName != null && gs.sinceLastJointMs > 0) {
                if (lastActivitiesText.isNotEmpty()) lastActivitiesText.append("\n")
                lastActivitiesText.append("${gs.lastJointSmokerName} had the last joint ${formatTime(gs.sinceLastJointMs)} ago")
            }
            
            // Add last bowl info
            if (gs.lastBowlSmokerName != null && gs.sinceLastBowlMs > 0) {
                if (lastActivitiesText.isNotEmpty()) lastActivitiesText.append("\n")
                lastActivitiesText.append("${gs.lastBowlSmokerName} had the last bowl ${formatTime(gs.sinceLastBowlMs)} ago")
            }
            
            // Update the text view with all last activity info
            if (lastActivitiesText.isNotEmpty()) {
                binding.textLastConeInfo.text = lastActivitiesText.toString()
                binding.textLastConeInfo.visibility = View.VISIBLE
            } else {
                binding.textLastConeInfo.visibility = View.GONE
            }

            // Show cones since last bowl only if there are bowls
            if (gs.totalBowls > 0 && gs.conesSinceLastBowl >= 0) {
                binding.textConesSinceLastBowl.text = "Cones since last bowl: ${gs.conesSinceLastBowl}"
                binding.textConesSinceLastBowl.visibility = View.VISIBLE
            } else {
                binding.textConesSinceLastBowl.visibility = View.GONE
            }

            // Hide the rounds and gaps TextView since we're combining everything
            binding.textRoundsAndGaps.visibility = View.GONE

            Log.d("SeshFragment", "🔍 === END GROUP STATS UPDATE ===")

            // Only set hasLoadedSummary to true when we have meaningful data
            if (gs.totalCones > 0 || gs.totalJoints > 0 || gs.totalBowls > 0 || gs.totalRounds > 0) {
                hasLoadedSummary = true
                Log.d("SeshFragment", "Set hasLoadedSummary = true due to stats data")
                updateResumeButtonVisibility()
            }
        }

        // Update table headers to show full words
        updateTableHeaders()

        // Observe per-smoker stats and create table layout
        sessionStatsVM.perSmokerStats.observe(viewLifecycleOwner) { statsList ->
            Log.d("SeshFragment", "Received per-smoker stats update: ${statsList.size} smokers")
            
            // Store the current stats for timer updates
            currentPerSmokerStats = statsList

            binding.perSmokerStatsContainer.removeAllViews()

            if (statsList.isEmpty()) {
                val tv = TextView(requireContext()).apply {
                    text = "No smoker stats available"
                    textSize = 14f
                    setPadding(0, 8, 0, 8)
                }
                binding.perSmokerStatsContainer.addView(tv)
                Log.d("SeshFragment", "No stats available, showing placeholder")
            } else {
                // Create table rows for each smoker - FIXED to show name on same row as first activity
                updatePerSmokerTable(statsList)
            }
        }
    }
    
    private fun updatePerSmokerTable(statsList: List<PerSmokerStats>) {
        binding.perSmokerStatsContainer.removeAllViews()
        statsList.forEach { stat ->
            createSmokerStatsRowsFixed(stat)
        }

        // Add a debug button (you can remove this later)
        binding.root.setOnLongClickListener {
            sessionStatsVM.debugCurrentState()
            true
        }
    }

    private fun updateTableHeaders() {
        // Clear existing header container
        binding.statsTableHeader.removeAllViews()

        // Create headers with proper labels - Added "Time" column
        val headers = listOf("Smoker", "Activity", "Time", "Last", "Avg", "Shortest", "Longest")
        val weights = floatArrayOf(1.8f, 1.3f, 1.0f, 1.0f, 1.0f, 1.2f, 1.2f)

        headers.forEachIndexed { index, header ->
            val headerText = TextView(requireContext()).apply {
                text = header
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weights[index])
            }
            binding.statsTableHeader.addView(headerText)
        }
    }

    private fun createSmokerStatsRowsFixed(stat: PerSmokerStats) {
        val context = requireContext()

        // Collect all activities for this smoker
        val activities = mutableListOf<Triple<String, String, List<String>>>()
        
        // Track if we've added the smoker name yet
        var nameAdded = false

        // Calculate time since last activity (for any type)
        val timeSinceLastActivity = if (stat.lastActivityTime > 0) {
            val currentTime = System.currentTimeMillis()
            val elapsed = (currentTime - stat.lastActivityTime) / 1000  // Convert to seconds
            formatTime(elapsed * 1000)  // formatTime expects milliseconds
        } else {
            "-"
        }
        
        Log.d("SeshFragment", "📊 LAST GAP DEBUG: ${stat.smokerName}")
        Log.d("SeshFragment", "📊   Cones: lastGap=${stat.lastGapMs}, avg=${stat.avgGapMs}")
        Log.d("SeshFragment", "📊   Joints: lastGap=${stat.lastJointGapMs}, avg=${stat.avgJointGapMs}")
        Log.d("SeshFragment", "📊   Bowls: lastGap=${stat.lastBowlGapMs}, avg=${stat.avgBowlGapMs}")
        Log.d("SeshFragment", "📊   Time since last: $timeSinceLastActivity")

        if (stat.totalCones > 0) {
            activities.add(Triple(
                stat.smokerName,  // Smoker name
                "${stat.totalCones} Cones",  // Activity
                listOf(
                    timeSinceLastActivity,  // Time since last activity
                    if (stat.totalCones > 1 && stat.lastGapMs > 0) formatTime(stat.lastGapMs) else "-",  // Last gap
                    if (stat.totalCones > 1) formatTime(stat.avgGapMs) else "-",  // Avg
                    if (stat.totalCones > 1) formatTime(stat.shortestGapMs) else "-",  // Shortest
                    if (stat.totalCones > 1) formatTime(stat.longestGapMs) else "-"  // Longest
                )
            ))
            nameAdded = true
        }

        if (stat.totalJoints > 0) {
            activities.add(Triple(
                if (!nameAdded) stat.smokerName else "",  // Add name if not added yet
                "${stat.totalJoints} Joints",
                listOf(
                    if (!nameAdded) timeSinceLastActivity else "",  // Show time only on first row
                    if (stat.totalJoints > 1 && stat.lastJointGapMs > 0) formatTime(stat.lastJointGapMs) else "-",  // Last gap
                    if (stat.totalJoints > 1) formatTime(stat.avgJointGapMs) else "-",
                    if (stat.totalJoints > 1) formatTime(stat.shortestJointGapMs) else "-",
                    if (stat.totalJoints > 1) formatTime(stat.longestJointGapMs) else "-"
                )
            ))
            if (!nameAdded) nameAdded = true
        }

        if (stat.totalBowls > 0) {
            activities.add(Triple(
                if (!nameAdded) stat.smokerName else "",  // Add name if not added yet
                "${stat.totalBowls} Bowls",
                listOf(
                    if (!nameAdded) timeSinceLastActivity else "",  // Show time only on first row
                    if (stat.totalBowls > 1 && stat.lastBowlGapMs > 0) formatTime(stat.lastBowlGapMs) else "-",  // Last gap
                    if (stat.totalBowls > 1) formatTime(stat.avgBowlGapMs) else "-",
                    if (stat.totalBowls > 1) formatTime(stat.shortestBowlGapMs) else "-",
                    if (stat.totalBowls > 1) formatTime(stat.longestBowlGapMs) else "-"
                )
            ))
            if (!nameAdded) nameAdded = true
        }

        // Create rows - name appears on SAME ROW as first activity
        activities.forEachIndexed { index, (name, activity, gaps) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (index == 0) 12 else 2
                }
            }

            // Smoker name column (show name on first row for this smoker)
            val smokerText = TextView(context).apply {
                text = name
                textSize = 12f
                if (name.isNotEmpty()) setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f)
            }
            row.addView(smokerText)

            // Activity column
            val activityText = TextView(context).apply {
                text = activity
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
            }
            row.addView(activityText)

            // Gap columns with matching weights to headers
            val gapWeights = floatArrayOf(1.0f, 1.0f, 1.3f, 1.3f)
            gaps.forEachIndexed { gapIndex, gap ->
                val gapText = TextView(context).apply {
                    text = gap
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, gapWeights[gapIndex])
                }
                row.addView(gapText)
            }

            binding.perSmokerStatsContainer.addView(row)
        }
    }

    /**
     * Show/hide resume button based on session state
     */
    private fun updateResumeButtonVisibility() {
        val shouldShow = hasLoadedSummary && !isSessionActive
        binding.fabResumeSesh.visibility = if (shouldShow) View.VISIBLE else View.GONE

        Log.d("SeshFragment", "=== RESUME BUTTON DEBUG ===")
        Log.d("SeshFragment", "hasLoadedSummary: $hasLoadedSummary")
        Log.d("SeshFragment", "isSessionActive: $isSessionActive")
        Log.d("SeshFragment", "sessionExplicitlyEnded: $sessionExplicitlyEnded")
        Log.d("SeshFragment", "shouldShow: $shouldShow")
        Log.d("SeshFragment", "Button visibility: ${if (shouldShow) "VISIBLE" else "GONE"}")
        Log.d("SeshFragment", "===============================")
    }

    /**
     * Call this when session becomes active to hide button
     */
    fun onSessionStarted() {
        Log.d("SeshFragment", "🟢 onSessionStarted() called")
        isSessionActive = true
        hasLoadedSummary = false
        sessionExplicitlyEnded = false  // Reset the flag
        updateResumeButtonVisibility()
    }

    /**
     * Call this when session summary is loaded to potentially show button
     */
    fun onSummaryLoaded() {
        Log.d("SeshFragment", "🟡 onSummaryLoaded() called")
        hasLoadedSummary = true
        sessionExplicitlyEnded = false  // Reset the flag when loading new summary
        updateResumeButtonVisibility()
    }

    /**
     * Call this when session ends to ensure button shows
     */
    fun onSessionEnded() {
        Log.d("SeshFragment", "🔴 onSessionEnded() called")
        isSessionActive = false
        sessionExplicitlyEnded = true  // NEW: Set the flag to prevent timer interference
        updateResumeButtonVisibility()

        Log.d("SeshFragment", "🔴 Session explicitly ended - timer updates will be ignored")
    }



    override fun onResume() {
        super.onResume()
        startTimeUpdateTimer()
    }
    
    override fun onPause() {
        super.onPause()
        stopTimeUpdateTimer()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        stopTimeUpdateTimer()
        _binding = null
    }
    
    private fun startTimeUpdateTimer() {
        updateTimerRunnable = object : Runnable {
            override fun run() {
                // Update the time column for all per-smoker stats
                if (currentPerSmokerStats.isNotEmpty() && _binding != null) {
                    updatePerSmokerTable(currentPerSmokerStats)
                }
                // Schedule next update in 1 second
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateTimerRunnable)
    }
    
    private fun stopTimeUpdateTimer() {
        if (::updateTimerRunnable.isInitialized) {
            handler.removeCallbacks(updateTimerRunnable)
        }
    }



    // Updated formatTime to show hours properly like MainActivity
    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes.toString().padStart(2,'0')}m ${secs.toString().padStart(2,'0')}s"
            minutes > 0 -> "${minutes}m ${secs.toString().padStart(2,'0')}s"
            else -> "${secs}s"
        }
    }
}