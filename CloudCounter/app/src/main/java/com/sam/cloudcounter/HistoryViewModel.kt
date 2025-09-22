// HistoryViewModel.kt
package com.sam.cloudcounter

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class HistoryItem {
    abstract val timestamp: Long

    data class ActivityItem(val log: ActivityLog) : HistoryItem() {
        override val timestamp: Long = log.timestamp
    }

    data class SummaryItem(val summary: SessionSummary) : HistoryItem() {
        override val timestamp: Long = summary.timestamp
    }
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as CloudCounterApplication).repository

    private val logsSource: LiveData<List<ActivityLog>> =
        repository.allLogs

    private val summariesSource: LiveData<List<SessionSummary>> =
        repository.allSummaries

    private val _allItems = MediatorLiveData<List<HistoryItem>>()
    val allItems: LiveData<List<HistoryItem>> = _allItems

    init {
        _allItems.addSource(logsSource) { combineItems() }
        _allItems.addSource(summariesSource) { combineItems() }
    }

    private fun combineItems() {
        val logs = logsSource.value.orEmpty().map { HistoryItem.ActivityItem(it) }
        val summaries = summariesSource.value.orEmpty().map { HistoryItem.SummaryItem(it) }
        
        // Separate active and inactive sessions
        val activeSessions = summaries.filter { it is HistoryItem.SummaryItem && it.summary.isActive }
        val inactiveSessions = summaries.filter { it is HistoryItem.SummaryItem && !it.summary.isActive }
        
        // Sort each section by timestamp
        // Active sessions should always be at the top, sorted by timestamp (newest first)
        val sortedActive = activeSessions.sortedByDescending { it.timestamp }
        // Inactive sessions and logs sorted together by timestamp (newest first)
        val sortedRest = (inactiveSessions + logs).sortedByDescending { it.timestamp }
        
        // Combine with active sessions always at the top
        _allItems.value = sortedActive + sortedRest
    }

    fun deleteLog(log: ActivityLog) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(log)
        }
    }

    fun insertLog(log: ActivityLog) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(log)
        }
    }

    /** NEW: delete a session summary */
    fun deleteSummary(summary: SessionSummary) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSummary(summary)
        }
    }
}
