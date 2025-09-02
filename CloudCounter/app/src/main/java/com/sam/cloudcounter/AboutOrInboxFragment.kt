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
    
    override fun onResume() {
        super.onResume()
        // Re-check auth state when fragment becomes visible again
        // This ensures the inbox button visibility is updated after adding smokers
        Log.d(TAG, "onResume - Refreshing auth state and UI")
        refreshAuthStateAndUI()
    }
    
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser && isResumed) {
            Log.d(TAG, "Fragment became visible to user - refreshing auth state")
            refreshAuthStateAndUI()
        }
    }
    
    fun refreshAuthStateAndUI() {
        // Force a refresh of the auth state
        val currentUser = auth.currentUser
        if (currentUser != null) {
            currentUser.reload().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Auth state reloaded successfully - UID: ${auth.currentUser?.uid}")
                } else {
                    Log.d(TAG, "Failed to reload auth state: ${task.exception?.message}")
                }
                // Update UI regardless
                if (_binding != null) {
                    setupToggleButton()
                    setupStatsControlButton()
                    showAppropriateFragment()
                }
            }
        } else {
            // No user logged in
            Log.d(TAG, "No user logged in during refresh")
            if (_binding != null) {
                setupToggleButton()
                setupStatsControlButton()
                showAppropriateFragment()
            }
        }
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
        Log.d(TAG, "setupToggleButton - Current user UID: ${currentUser?.uid}, Admin UID: $ADMIN_UID")
        Log.d(TAG, "setupToggleButton - User email: ${currentUser?.email}")
        Log.d(TAG, "setupToggleButton - Is admin: ${currentUser?.uid == ADMIN_UID}")
        
        if (currentUser?.uid == ADMIN_UID) {
            Log.d(TAG, "Admin detected - showing inbox button")
            binding.btnToggleView.visibility = View.VISIBLE
            binding.btnToggleView.setOnClickListener {
                toggleView()
            }
        } else {
            Log.d(TAG, "Not admin - hiding inbox button")
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
        
        Log.d(TAG, "showAppropriateFragment - Current user: ${currentUser?.uid}")
        Log.d(TAG, "showAppropriateFragment - Is admin: $isAdmin")
        Log.d(TAG, "showAppropriateFragment - Current view state: $currentViewState")

        val fragment = when {
            !isAdmin -> {
                // Non-admin users always see About
                Log.d(TAG, "Showing About fragment for non-admin user")
                binding.btnToggleView.visibility = View.GONE
                binding.btnStatsControls.visibility = View.GONE
                AboutFragment()
            }
            currentViewState == VIEW_STATE_INBOX -> {
                // Admin viewing inbox
                Log.d(TAG, "Showing Inbox fragment for admin user")
                binding.btnToggleView.text = "View About"
                binding.btnStatsControls.visibility = View.VISIBLE
                InboxFragment()
            }
            else -> {
                // Admin viewing About (default)
                Log.d(TAG, "Showing About fragment for admin user")
                binding.btnToggleView.text = "View Inbox"
                binding.btnStatsControls.visibility = View.GONE
                AboutFragment()
            }
        }

        // Only replace fragment if the binding is still valid
        if (_binding != null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        }
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