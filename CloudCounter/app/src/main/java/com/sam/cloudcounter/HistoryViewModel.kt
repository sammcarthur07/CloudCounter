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
        val combined = mutableListOf<HistoryItem>().apply {
            addAll(summaries)
            addAll(logs)
        }
        combined.sortByDescending { it.timestamp }
        _allItems.value = combined
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
