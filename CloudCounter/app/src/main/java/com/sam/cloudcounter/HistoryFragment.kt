package com.sam.cloudcounter

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sam.cloudcounter.databinding.FragmentHistoryBinding
import kotlinx.coroutines.launch

class HistoryFragment : Fragment(R.layout.fragment_history) {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    // Get the HistoryViewModel
    private val viewModel by lazy {
        ViewModelProvider(this).get(HistoryViewModel::class.java)
    }

    // callbacks—MainActivity will set these if it wants to.
    var onDeleteLog:      ((ActivityLog)     -> Unit)? = null
    var onDeleteSummary:  ((SessionSummary)  -> Unit)? = null
    var onDeleteSummaryWithActivities: ((SessionSummary) -> Unit)? = null
    var onResumeSummary:  ((SessionSummary)  -> Unit)? = null

    // ADD: Confetti helper
    private var confettiHelper: ConfettiHelper? = null

    private lateinit var adapter: HistoryAdapter
    private val repo by lazy { (requireActivity().application as CloudCounterApplication).repository }

    // Track the previous list size to detect new additions
    private var previousItemCount = 0

    // ADD: Method to receive confetti helper from MainActivity
    fun setConfettiHelper(helper: ConfettiHelper) {
        this.confettiHelper = helper
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHistoryBinding.bind(view)

        // Setup the adapter with ViewModel-based deletion functions
        adapter = HistoryAdapter(
            repo,
            onDeleteLog = { log -> viewModel.deleteLog(log) },
            onDeleteSummary = { summary -> viewModel.deleteSummary(summary) },
            onDeleteSummaryWithActivities = { summary -> viewModel.deleteSessionWithActivities(summary) },
            onResumeSummary = onResumeSummary ?: { },
            confettiHelper
        )
        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewHistory.adapter = adapter

        // Observe the ViewModel's combined items LiveData
        viewModel.allItems.observe(viewLifecycleOwner) { items ->
            // Check if this is a new item being added (not initial load or deletion)
            val currentItemCount = items.size
            val shouldScrollToTop = currentItemCount > previousItemCount && previousItemCount > 0

            adapter.submitList(items) {
                // This callback runs after the list is updated
                // CRITICAL FIX: Check if binding is still valid before accessing it
                if (_binding != null && shouldScrollToTop) {
                    // Smooth scroll to the top to show the new entry
                    _binding?.recyclerViewHistory?.smoothScrollToPosition(0)
                }
            }

            previousItemCount = currentItemCount

            // CRITICAL FIX: Check if binding is still valid
            _binding?.textViewEmptyHistory?.visibility =
                if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        }
    }

    // Optional: Add a public method to scroll to top programmatically
    fun scrollToTop() {
        _binding?.recyclerViewHistory?.smoothScrollToPosition(0)
    }

    fun refreshHistory() {
        // This will trigger the ViewModel to reload data
        // The LiveData observers will automatically update the UI
        Log.d("HistoryFragment", "Refreshing history data after undo")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
