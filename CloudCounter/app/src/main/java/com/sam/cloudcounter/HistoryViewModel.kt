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
    private val authManager = (application as CloudCounterApplication).authManager
    private val historyCloudSync = HistoryCloudSync()

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
        
        // DEBUG: Log session details
        summaries.forEach { item ->
            if (item is HistoryItem.SummaryItem) {
                val s = item.summary
                val effectiveTimestamp = if (s.isActive) s.timestamp else (s.timestamp + s.sessionLength)
                android.util.Log.d("HistoryViewModel", "📋 Session ${s.id}: timestamp=${s.timestamp}, effective=${effectiveTimestamp}, isActive=${s.isActive}, length=${s.sessionLength}ms")
            }
        }
        
        // Separate active and inactive sessions
        val activeSessions = summaries.filter { it is HistoryItem.SummaryItem && it.summary.isActive }
        val inactiveSessions = summaries.filter { it is HistoryItem.SummaryItem && !it.summary.isActive }
        
        android.util.Log.d("HistoryViewModel", "📋 Active sessions: ${activeSessions.size}, Inactive: ${inactiveSessions.size}")
        
        // Sort active sessions by their start timestamp (newest first)
        val sortedActive = activeSessions.sortedByDescending { it.timestamp }
        
        // Combine inactive sessions and logs, sorting with custom timestamp logic
        val sortedRest = (inactiveSessions + logs).sortedByDescending { item ->
            when (item) {
                is HistoryItem.SummaryItem -> {
                    // For inactive sessions, use end time (start + length) for sorting
                    item.summary.timestamp + item.summary.sessionLength
                }
                is HistoryItem.ActivityItem -> {
                    // For activities, use their actual timestamp
                    item.timestamp
                }
            }
        }
        
        android.util.Log.d("HistoryViewModel", "📋 Sorted items - Active: ${sortedActive.size}, Rest: ${sortedRest.size}")
        
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

    /** Delete a session summary along with all its activities */
    fun deleteSessionWithActivities(summary: SessionSummary) {
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("HistoryViewModel", "🗑️ ========== DELETION START ==========")
            android.util.Log.d("HistoryViewModel", "🗑️ Deleting session ${summary.id}")
            android.util.Log.d("HistoryViewModel", "🗑️ Session timestamp (sessionId): ${summary.timestamp}")
            android.util.Log.d("HistoryViewModel", "🗑️ Session length: ${summary.sessionLength}ms (${summary.sessionLength/1000/60} minutes)")
            
            // Calculate the session time range
            val sessionStart = summary.timestamp
            val sessionEnd = summary.timestamp + summary.sessionLength
            
            android.util.Log.d("HistoryViewModel", "🗑️ Time range: $sessionStart to $sessionEnd")
            
            // Get activities by BOTH time range AND sessionId for comprehensive deletion
            android.util.Log.d("HistoryViewModel", "🗑️ Searching for activities by time range ($sessionStart, $sessionEnd)...")
            val activitiesByTime = repository.getLogsBetweenTimestamps(sessionStart, sessionEnd)
            android.util.Log.d("HistoryViewModel", "🗑️ Found ${activitiesByTime.size} activities by time range")
            activitiesByTime.forEach { activity ->
                android.util.Log.d("HistoryViewModel", "🗑️   - Time: ${activity.type} at ${activity.timestamp}, sessionId=${activity.sessionId}, id=${activity.id}")
            }
            
            android.util.Log.d("HistoryViewModel", "🗑️ Searching for activities by sessionId (${summary.timestamp})...")
            val activitiesBySessionId = repository.getActivitiesBySessionId(summary.timestamp)
            android.util.Log.d("HistoryViewModel", "🗑️ Found ${activitiesBySessionId.size} activities by sessionId")
            activitiesBySessionId.forEach { activity ->
                android.util.Log.d("HistoryViewModel", "🗑️   - SessionId: ${activity.type} at ${activity.timestamp}, sessionId=${activity.sessionId}, id=${activity.id}")
            }
            
            // Combine both lists and remove duplicates by activity ID
            val sessionActivities = (activitiesByTime + activitiesBySessionId).distinctBy { it.id }
            
            android.util.Log.d("HistoryViewModel", "🗑️ Total unique activities to delete: ${sessionActivities.size}")
            
            val userId = authManager.getCurrentUserId()
            android.util.Log.d("HistoryViewModel", "🗑️ Current user ID: ${userId ?: "NOT SIGNED IN"}")
            
            // Delete each activity locally AND from cloud
            sessionActivities.forEach { activity ->
                android.util.Log.d("HistoryViewModel", "🗑️ Deleting activity ${activity.id}: ${activity.type} at ${activity.timestamp}")
                
                // Delete locally first
                repository.delete(activity)
                android.util.Log.d("HistoryViewModel", "🗑️   ✅ Deleted locally: activity ${activity.id}")
                
                // Also delete from cloud if user is signed in
                if (userId != null) {
                    try {
                        val deleted = historyCloudSync.deleteActivity(userId, activity.id)
                        if (deleted) {
                            android.util.Log.d("HistoryViewModel", "🗑️   ✅ Deleted from cloud: activity ${activity.id}")
                        } else {
                            android.util.Log.w("HistoryViewModel", "🗑️   ⚠️ Cloud deletion returned false for activity ${activity.id}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HistoryViewModel", "🗑️   ❌ Failed to delete activity ${activity.id} from cloud", e)
                    }
                } else {
                    android.util.Log.d("HistoryViewModel", "🗑️   ⏭️ Skipping cloud deletion (not signed in)")
                }
            }
            
            // Delete the summary itself
            android.util.Log.d("HistoryViewModel", "🗑️ Deleting session summary ${summary.id}")
            
            // Delete from cloud first if signed in
            if (userId != null) {
                try {
                    val deleted = historyCloudSync.deleteSessionSummary(userId, summary.id)
                    if (deleted) {
                        android.util.Log.d("HistoryViewModel", "🗑️   ✅ Deleted session from cloud: ${summary.id}")
                    } else {
                        android.util.Log.w("HistoryViewModel", "🗑️   ⚠️ Cloud deletion returned false for session ${summary.id}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HistoryViewModel", "🗑️   ❌ Failed to delete session ${summary.id} from cloud", e)
                }
            }
            
            // Delete locally
            repository.deleteSummary(summary)
            android.util.Log.d("HistoryViewModel", "🗑️   ✅ Deleted session locally: ${summary.id}")
            
            android.util.Log.d("HistoryViewModel", "🗑️ ========== DELETION COMPLETE ==========")
            
            // The LiveData/Flow will automatically notify observers of the change
        }
    }
}
