package com.sam.cloudcounter

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.sam.cloudcounter.databinding.FragmentGoalsBinding
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import androidx.appcompat.widget.SwitchCompat
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat

class GoalFragment : Fragment() {
    private var _binding: FragmentGoalsBinding? = null
    private val binding get() = _binding!!

    private lateinit var goalViewModel: GoalViewModel
    private lateinit var goalAdapter: GoalAdapter
    private var refreshTimer: Timer? = null
    private var lastRefreshTime = 0L

    companion object {
        private const val TAG = "GoalFragment"
    }

    private lateinit var goalService: GoalService

    private var confettiHelper: ConfettiHelper? = null

    fun setConfettiHelper(helper: ConfettiHelper) {
        this.confettiHelper = helper
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGoalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        goalViewModel = ViewModelProvider(this)[GoalViewModel::class.java]
        goalService = GoalService(requireActivity().application)

        setupRecyclerView()
        observeGoals()
        startRefreshTimer()

        binding.fabAddGoal.setOnClickListener {
            val dialog = AddGoalDialog()

            dialog.setOnGoalCreatedListener { newGoal ->
                lifecycleScope.launch {
                    goalViewModel.insertGoal(newGoal)
                    Toast.makeText(requireContext(), "Goal created!", Toast.LENGTH_SHORT).show()
                    confettiHelper?.showRainConfetti()
                    Log.d("GoalFragment", "🎯 Created new goal: ${newGoal.goalName} for ${newGoal.selectedSmokers}")
                }
            }

            dialog.show(childFragmentManager, "AddGoalDialog")
        }
    }

    private fun setupRecyclerView() {
        goalAdapter = GoalAdapter(
            onGoalClick = { goal ->
                // Show goal details
                showGoalDetailsDialog(goal)
            },
            onDeleteClick = { goal ->
                showDeleteConfirmationDialog(goal)
            },
            onPauseClick = { goal ->
                // FIX: Connect to the toggle function
                toggleGoalPause(goal)
            },
            onCompleteClick = { goal ->
                // FIX: Connect to the toggle function
                toggleGoalComplete(goal)
            },
            onNotificationClick = { goal ->
                handleNotificationToggle(goal)
            },
            confettiHelper = confettiHelper
        )

        binding.recyclerViewGoals.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = goalAdapter
        }
    }

    private fun handleNotificationToggle(goal: Goal) {
        Log.d(TAG, "🔔 Toggling notification for goal: ${goal.goalName} (ID: ${goal.goalId})")
        Log.d(TAG, "🔔 Current state: ${goal.notificationsEnabled}")

        lifecycleScope.launch {
            try {
                // Create updated goal with toggled notification state
                val updatedGoal = goal.copy(
                    notificationsEnabled = !goal.notificationsEnabled,
                    // Also update the other notification fields to keep them in sync
                    progressNotificationsEnabled = !goal.notificationsEnabled,
                    completionNotificationsEnabled = !goal.notificationsEnabled
                )

                // Update the goal in the database through ViewModel
                // This will trigger the LiveData observer which will refresh the UI
                goalViewModel.updateGoal(updatedGoal)

                // Show toast feedback
                val message = if (updatedGoal.notificationsEnabled) {
                    "Notifications enabled"
                } else {
                    "Notifications disabled"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

                Log.d(TAG, "🔔 Successfully toggled to: ${updatedGoal.notificationsEnabled}")

            } catch (e: Exception) {
                Log.e(TAG, "🔔 Error toggling notification", e)
                Toast.makeText(requireContext(), "Failed to update notification setting", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun toggleGoalPause(goal: Goal) {
        Log.d(TAG, "⏸️ Toggling pause for goal: ${goal.goalName}")
        lifecycleScope.launch {
            val updatedGoal = goal.copy(isPaused = !goal.isPaused)
            goalViewModel.updateGoal(updatedGoal)

            val message = if (updatedGoal.isPaused) {
                "Goal paused"
            } else {
                "Goal resumed"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleGoalComplete(goal: Goal) {
        Log.d(TAG, "✅ Toggling complete for goal: ${goal.goalName}")
        lifecycleScope.launch {
            val updatedGoal = if (goal.isActive) {
                // Mark as completed
                goal.copy(
                    isActive = false,
                    completedAt = System.currentTimeMillis()
                )
            } else {
                // Resume/reactivate
                goal.copy(
                    isActive = true,
                    completedAt = null
                )
            }

            goalViewModel.updateGoal(updatedGoal)

            // CORRECTED BLOCK
            val message = if (updatedGoal.isActive) {
                "Goal resumed"
            } else {
                confettiHelper?.showRainConfetti() // Move this line up
                "Goal completed! 🎉"               // Now the String is the last line
            }
            // Use requireContext() for safety
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRefreshTimer() {
        refreshTimer?.cancel()
        refreshTimer = Timer()
        refreshTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                activity?.runOnUiThread {
                    val now = System.currentTimeMillis()

                    if (now - lastRefreshTime > 5000) {
                        lastRefreshTime = now
                        goalViewModel.checkTimeBasedGoals()
                    }

                    updateTimeRemainingOnly()
                }
            }
        }, 1000, 1000)
    }

    private fun updateTimeRemainingOnly() {
        val layoutManager = binding.recyclerViewGoals.layoutManager
        val childCount = binding.recyclerViewGoals.childCount

        for (i in 0 until childCount) {
            val view = binding.recyclerViewGoals.getChildAt(i)
            val viewHolder = binding.recyclerViewGoals.getChildViewHolder(view)

            if (viewHolder is GoalAdapter.GoalViewHolder) {
                // Update time display if needed
            }
        }
    }

    override fun onResume() {
        super.onResume()
        goalViewModel.checkTimeBasedGoals()
        startRefreshTimer()
    }

    private fun observeGoals() {
        var previousInProgressCount = 0
        var previousPausedCount = 0
        var previousEndedCount = 0

        goalViewModel.allGoalsSorted.observe(viewLifecycleOwner) { goals ->
            Log.d(TAG, "📋 === GOALS OBSERVER TRIGGERED ===")
            Log.d(TAG, "📋 Total goals: ${goals.size}")

            // Log notification states for debugging
            goals.forEach { goal ->
                Log.d(TAG, "📋 Goal ${goal.goalId}: progressNotificationsEnabled = ${goal.notificationsEnabled}")
            }

            // Group goals into sections
            val inProgressGoals = mutableListOf<Any>()
            val endedGoals = mutableListOf<Any>()
            val pausedGoals = mutableListOf<Any>()

            goals.forEach { goal ->
                when {
                    goal.isPaused -> pausedGoals.add(goal)
                    goal.isActive -> inProgressGoals.add(goal)
                    else -> endedGoals.add(goal)
                }
            }

            // Check if goals moved between sections
            val movedSections = (inProgressGoals.size != previousInProgressCount && previousInProgressCount > 0) ||
                    (pausedGoals.size != previousPausedCount && previousPausedCount > 0) ||
                    (endedGoals.size != previousEndedCount && previousEndedCount > 0)

            if (movedSections) {
                confettiHelper?.showRainConfetti()
            }

            previousInProgressCount = inProgressGoals.size
            previousPausedCount = pausedGoals.size
            previousEndedCount = endedGoals.size

            // Build the final list with section headers
            val finalList = mutableListOf<Any>()

            // Add In Progress section
            if (inProgressGoals.isNotEmpty()) {
                finalList.add("In Progress")
                finalList.addAll(inProgressGoals)
            }

            // Add Paused section
            if (pausedGoals.isNotEmpty()) {
                finalList.add("Paused")
                finalList.addAll(pausedGoals)
            }

            // Add Ended section
            if (endedGoals.isNotEmpty()) {
                finalList.add("Ended")
                finalList.addAll(endedGoals)
            }

            goalAdapter.submitList(finalList)

            // Show/hide empty state
            if (finalList.isEmpty()) {
                binding.textViewEmptyGoals.visibility = View.VISIBLE
                binding.recyclerViewGoals.visibility = View.GONE
            } else {
                binding.textViewEmptyGoals.visibility = View.GONE
                binding.recyclerViewGoals.visibility = View.VISIBLE
            }
        }
    }

    fun notifyGoalProgressReversed(goalId: Long) {
        Log.d("GoalFragment", "🎯↩️ Goal $goalId progress was reversed")
    }

    private fun getCurrentSessionCode(): String? {
        val sharedPref = requireContext().getSharedPreferences("CloudCounterPrefs", Context.MODE_PRIVATE)
        return sharedPref.getString("current_session_code", null)
    }

    private fun showGoalDetailsDialog(goal: Goal) {
        val progress = calculateProgress(goal)
        val message = buildString {
            if (goal.goalName.isNotEmpty()) {
                appendLine("Name: ${goal.goalName}")
            }
            appendLine("Type: ${goal.goalType.name.replace("_", " ")}")
            appendLine("Progress: ${progress.first} / ${progress.second} (${progress.third}%)")

            if (goal.targetJoints > 0) {
                appendLine("Joints: ${goal.currentJoints} / ${goal.targetJoints}")
            }
            if (goal.targetCones > 0) {
                appendLine("Cones: ${goal.currentCones} / ${goal.targetCones}")
            }
            if (goal.targetBowls > 0) {
                appendLine("Bowls: ${goal.currentBowls} / ${goal.targetBowls}")
            }

            if (goal.isRecurring) {
                appendLine("Recurring: Yes")
                if (goal.completedRounds > 0) {
                    appendLine("Completed Rounds: ${goal.completedRounds}")
                }
            }

            appendLine("Allow Overflow: ${if (goal.allowOverflow) "Yes" else "No (Auto-complete at 100%)"}")
            appendLine("Notifications: ${if (goal.notificationsEnabled) "Enabled" else "Disabled"}")

            if (goal.isPaused) {
                appendLine("\n⏸ Goal is currently paused")
            }

            if (!goal.isActive) {
                appendLine("\n✅ Goal is completed")
            }

            if (goal.goalType == GoalType.TIME_BASED) {
                appendLine("Duration: ${goal.timeDuration} ${goal.timeUnit?.name?.lowercase()}")
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Goal Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(goal: Goal) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Goal")
            .setMessage("Are you sure you want to delete this goal?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        goalService.deleteGoal(goal.goalId)
                    }
                    Toast.makeText(context, "Goal deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun calculateProgress(goal: Goal): Triple<Int, Int, Int> {
        val totalTarget = goal.targetJoints + goal.targetCones + goal.targetBowls
        val totalCurrent = goal.currentJoints + goal.currentCones + goal.currentBowls
        val percentage = if (totalTarget > 0) {
            if (goal.allowOverflow) {
                (totalCurrent * 100 / totalTarget)
            } else {
                (totalCurrent * 100 / totalTarget).coerceAtMost(100)
            }
        } else 0

        return Triple(totalCurrent, totalTarget, percentage)
    }

    override fun onPause() {
        super.onPause()
        refreshTimer?.cancel()
        refreshTimer = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        goalService.cleanup()
        refreshTimer?.cancel()
        refreshTimer = null
        _binding = null
    }
}