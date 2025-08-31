package com.sam.cloudcounter

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sam.cloudcounter.databinding.FragmentAboutOrInboxBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.content.Context

class AboutOrInboxFragment : Fragment() {

    companion object {
        private const val TAG = "AboutOrInboxFragment"
        private const val ADMIN_UID = "diY4ATkGQYhYndv2lQY4rZAUKGl2"
        private const val PREF_LAST_VIEW_STATE = "last_about_inbox_view_state"
        private const val VIEW_STATE_ABOUT = "about"
        private const val VIEW_STATE_INBOX = "inbox"
    }

    private var _binding: FragmentAboutOrInboxBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private var currentViewState = VIEW_STATE_ABOUT // Default to About

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutOrInboxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load the last view state from SharedPreferences
        loadLastViewState()

        setupToggleButton()
        setupStatsControlButton()
        showAppropriateFragment()
    }

    private fun loadLastViewState() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        currentViewState = prefs.getString(PREF_LAST_VIEW_STATE, VIEW_STATE_ABOUT) ?: VIEW_STATE_ABOUT

        // For non-admins, always show About
        val currentUser = auth.currentUser
        if (currentUser?.uid != ADMIN_UID) {
            currentViewState = VIEW_STATE_ABOUT
        }
    }

    private fun saveViewState(state: String) {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_LAST_VIEW_STATE, state).apply()
    }

    private fun setupToggleButton() {
        // Only show toggle button for admin
        val currentUser = auth.currentUser
        if (currentUser?.uid == ADMIN_UID) {
            binding.btnToggleView.visibility = View.VISIBLE
            binding.btnToggleView.setOnClickListener {
                toggleView()
            }
        } else {
            binding.btnToggleView.visibility = View.GONE
        }
    }

    private fun setupStatsControlButton() {
        // Only show stats control button for admin when in admin view
        val currentUser = auth.currentUser
        if (currentUser?.uid == ADMIN_UID) {
            binding.btnStatsControls.setOnClickListener {
                showStatsControlsDialog()
            }
            // Visibility will be controlled by showAppropriateFragment()
        } else {
            binding.btnStatsControls.visibility = View.GONE
        }
    }

    private fun showAppropriateFragment() {
        val currentUser = auth.currentUser
        val isAdmin = currentUser?.uid == ADMIN_UID

        val fragment = when {
            !isAdmin -> {
                // Non-admin users always see About
                binding.btnToggleView.visibility = View.GONE
                binding.btnStatsControls.visibility = View.GONE
                AboutFragment()
            }
            currentViewState == VIEW_STATE_INBOX -> {
                // Admin viewing inbox
                binding.btnToggleView.text = "View About"
                binding.btnStatsControls.visibility = View.VISIBLE
                InboxFragment()
            }
            else -> {
                // Admin viewing About (default)
                binding.btnToggleView.text = "View Inbox"
                binding.btnStatsControls.visibility = View.GONE
                AboutFragment()
            }
        }

        childFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun toggleView() {
        // Toggle between states
        currentViewState = if (currentViewState == VIEW_STATE_ABOUT) {
            VIEW_STATE_INBOX
        } else {
            VIEW_STATE_ABOUT
        }

        // Save the new state
        saveViewState(currentViewState)

        // Show the appropriate fragment
        showAppropriateFragment()
    }

    private fun showStatsControlsDialog() {
        lifecycleScope.launch {
            try {
                val app = requireActivity().application as CloudCounterApplication
                val statsManager = StatsManager(requireContext(), app.repository)
                val stats = statsManager.getStats()

                // Get current adjustments from Firebase
                val adjustmentsDoc = FirebaseFirestore.getInstance()
                    .document("stats_adjustments/sam_stats")
                    .get()
                    .await()

                val adjustments = adjustmentsDoc.toObject(StatsAdjustments::class.java)
                    ?: StatsAdjustments()

                val dialog = StatsControlsDialog.newInstance(stats, adjustments)
                dialog.show(childFragmentManager, "stats_controls")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show stats controls", e)
                Toast.makeText(context, "Failed to load stats", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}